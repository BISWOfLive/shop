<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { getRoles, getUsers, updateUserRoles, updateUserStatus } from '../api/users.js'
import { useAuth } from '../composables/useAuth'
import AppIcon from '../components/AppIcon.vue'

const { identityEpoch } = useAuth()
const filters = reactive({ keyword: '', status: '' })
const query = reactive({ keyword: '', status: '', page: 1, size: 10 })
const result = ref({ records: [], total: 0 })
const roles = ref([])
const roleLoading = ref(true)
const roleError = ref('')
const loading = ref(false)
const listError = ref('')
const notice = ref('')
const selection = reactive({})
const savingId = ref('')
let controller
let sequence = 0
let noticeTimer

const totalPages = computed(() => Math.max(1, Math.ceil(result.value.total / query.size)))
const firstItem = computed(() => result.value.total ? (query.page - 1) * query.size + 1 : 0)
const lastItem = computed(() => Math.min(query.page * query.size, result.value.total))
const fixedRoles = computed(() => roles.value.filter(role => role.code === 'ADMIN' || role.code === 'USER'))
const roleName = code => code === 'ADMIN' ? '管理员' : code === 'USER' ? '普通用户' : code

function showNotice(message) {
  notice.value = message
  clearTimeout(noticeTimer)
  noticeTimer = setTimeout(() => { notice.value = '' }, 4500)
}

function syncSelection(records) {
  for (const key of Object.keys(selection)) delete selection[key]
  for (const record of records) {
    selection[record.id] = new Set((record.roles || []).map(role => role.id))
  }
}

async function loadRoles() {
  roleLoading.value = true
  roleError.value = ''
  try {
    roles.value = await getRoles()
  } catch (cause) {
    roleError.value = cause.message
  } finally {
    roleLoading.value = false
  }
}

async function load(next = 1, apply = false) {
  if (apply) {
    query.keyword = filters.keyword.trim()
    query.status = filters.status
    query.page = 1
  } else {
    query.page = next
  }
  controller?.abort()
  const active = new AbortController()
  controller = active
  const epoch = identityEpoch.value
  const current = ++sequence
  loading.value = true
  listError.value = ''
  try {
    const data = await getUsers({ ...query }, active.signal)
    if (epoch !== identityEpoch.value || current !== sequence) return
    result.value = data
    syncSelection(data.records)
  } catch (cause) {
    if (cause.name === 'AbortError' || epoch !== identityEpoch.value || current !== sequence) return
    listError.value = cause.message
  } finally {
    if (current === sequence && epoch === identityEpoch.value) loading.value = false
  }
}

function applyFilters() { load(1, true) }

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  query.keyword = ''
  query.status = ''
  load(1)
}

function goToPage(page) {
  if (loading.value || page < 1 || page > totalPages.value || page === query.page) return
  load(page)
}

function changeSize() {
  query.page = 1
  load(1)
}

function changeSelection(record, roleId, checked) {
  const selected = selection[record.id] || new Set()
  if (checked) selected.add(roleId)
  else selected.delete(roleId)
  selection[record.id] = selected
}

async function toggleStatus(record) {
  if (savingId.value) return
  savingId.value = record.id
  listError.value = ''
  try {
    const updated = await updateUserStatus(record.id, record.status === 1 ? 0 : 1)
    replaceRecord(updated)
    showNotice(updated.status === 1 ? '账号已启用' : '账号已禁用')
  } catch (cause) {
    listError.value = cause.message
  } finally {
    savingId.value = ''
  }
}

async function saveRoles(record) {
  if (savingId.value) return
  savingId.value = record.id
  listError.value = ''
  try {
    const roleIds = Array.from(selection[record.id] || [])
    const updated = await updateUserRoles(record.id, roleIds)
    replaceRecord(updated)
    showNotice('角色已保存')
  } catch (cause) {
    listError.value = cause.message
  } finally {
    savingId.value = ''
  }
}

function replaceRecord(updated) {
  const records = result.value.records.map(record => record.id === updated.id ? updated : record)
  result.value = { ...result.value, records }
  selection[updated.id] = new Set((updated.roles || []).map(role => role.id))
}

onMounted(() => {
  loadRoles()
  load(1)
})
onBeforeUnmount(() => {
  controller?.abort()
  clearTimeout(noticeTimer)
})
</script>

<template>
  <section class="product-page user-page">
    <div class="page-heading">
      <div><h1>用户管理</h1></div>
      <a class="button secondary" href="#home">浏览商城首页</a>
    </div>

    <div v-if="roleError" class="alert-banner" role="alert">
      <AppIcon name="alert" /><span>{{ roleError }}</span>
      <button class="text-button" type="button" :disabled="roleLoading" @click="loadRoles">重新加载</button>
    </div>
    <div v-if="notice" class="success-banner" role="status">
      <AppIcon name="check" /><span>{{ notice }}</span>
      <button class="icon-button" type="button" aria-label="关闭提示" @click="notice = ''"><AppIcon name="close" /></button>
    </div>

    <div class="content-card">
      <form class="filter-bar" aria-label="筛选用户" @submit.prevent="applyFilters">
        <label class="filter-field search-field">
          <span>用户名或昵称</span>
          <div class="search-input">
            <AppIcon name="search" />
            <input v-model="filters.keyword" name="keyword" type="search" maxlength="64" placeholder="搜索用户名或昵称" />
          </div>
        </label>
        <label class="filter-field">
          <span>账号状态</span>
          <select v-model="filters.status" name="status">
            <option value="">全部状态</option>
            <option value="1">启用</option>
            <option value="0">禁用</option>
          </select>
        </label>
        <div class="filter-actions">
          <button class="button primary" type="submit">查询</button>
          <button class="button secondary" type="button" @click="resetFilters">重置</button>
        </div>
      </form>

      <div class="list-toolbar">
        <div class="list-title"><h2>账号列表</h2><span class="count-tag">{{ result.total }} 个账号</span></div>
        <label class="sort-control">
          <span>每页条数</span>
          <select v-model.number="query.size" name="size" :disabled="loading" @change="changeSize">
            <option :value="10">10 条 / 页</option>
            <option :value="20">20 条 / 页</option>
            <option :value="50">50 条 / 页</option>
            <option :value="100">100 条 / 页</option>
          </select>
        </label>
      </div>

      <div v-if="listError" class="list-state error-state" role="alert">
        <span class="state-icon"><AppIcon name="alert" /></span>
        <h3>账号列表暂时无法加载</h3><p>{{ listError }}</p>
        <button class="button secondary" type="button" @click="load(query.page)">重新加载</button>
      </div>
      <div v-else class="table-scroll" :aria-busy="loading">
        <table>
          <caption class="sr-only">用户账号列表，可筛选、启用或禁用账号并维护固定角色</caption>
          <thead>
            <tr>
              <th scope="col">用户名</th><th scope="col">昵称</th><th scope="col">状态</th>
              <th scope="col">角色</th><th scope="col" class="action-column">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="5" class="loading-cell"><span class="loading-dot"></span>正在加载账号…</td></tr>
            <tr v-else-if="!result.records.length">
              <td colspan="5"><div class="list-state"><span class="state-icon"><AppIcon name="user" /></span><h3>没有符合条件的账号</h3></div></td>
            </tr>
            <tr v-for="record in loading ? [] : result.records" :key="record.id">
              <td><strong class="user-name">{{ record.username }}</strong></td>
              <td>{{ record.nickname || '—' }}</td>
              <td>
                <span class="status-badge" :class="{ 'is-up': record.status === 1 }">
                  <i></i>{{ record.status === 1 ? '启用' : '禁用' }}
                </span>
              </td>
              <td>
                <div class="role-options" role="group" :aria-label="'设置 ' + record.username + ' 的角色'">
                  <label v-for="role in fixedRoles" :key="role.id">
                    <input type="checkbox" :value="role.id" :disabled="!!savingId || roleLoading"
                           :checked="(selection[record.id] || new Set()).has(role.id)"
                           @change="changeSelection(record, role.id, $event.target.checked)" />
                    {{ role.name }}
                  </label>
                </div>
              </td>
              <td class="action-column">
                <div class="row-actions">
                  <button class="button secondary" type="button" :disabled="!!savingId" @click="toggleStatus(record)">
                    {{ record.status === 1 ? '禁用' : '启用' }}
                  </button>
                  <button class="button primary" type="button" :disabled="!!savingId || roleLoading" @click="saveRoles(record)">
                    {{ savingId === record.id ? '保存中…' : '保存角色' }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="pagination-bar">
        <p>显示 {{ firstItem }}–{{ lastItem }} 项，共 {{ result.total }} 项</p>
        <div class="pagination-actions">
          <nav class="page-buttons" aria-label="账号分页">
            <button type="button" aria-label="上一页" :disabled="loading || query.page <= 1" @click="goToPage(query.page - 1)"><AppIcon name="left" /></button>
            <button type="button" :disabled="loading" aria-current="page">{{ query.page }}</button>
            <button type="button" aria-label="下一页" :disabled="loading || query.page >= totalPages" @click="goToPage(query.page + 1)"><AppIcon name="right" /></button>
          </nav>
          <span class="page-total">共 {{ totalPages }} 页</span>
        </div>
      </footer>
    </div>
  </section>
</template>
