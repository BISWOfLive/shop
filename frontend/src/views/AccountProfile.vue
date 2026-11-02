<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { updateProfile } from '../api/users.js'
import { useAuth } from '../composables/useAuth'

const { user, isAdmin, loadMe, logout } = useAuth()
const form = reactive({ nickname: '' })
const error = ref('')
const notice = ref('')
const saving = ref(false)
const roles = computed(() => (user.value?.roles || []).map(role => role.name || role.code).join('、'))

watch(user, value => {
  if (value) form.nickname = value.nickname || ''
}, { immediate: true })

async function save() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    await updateProfile({ nickname: form.nickname })
    await loadMe()
    notice.value = '昵称已保存'
  } catch (cause) {
    error.value = cause.message
  } finally {
    saving.value = false
  }
}

async function handleLogout() {
  try { await logout() } finally { window.location.hash = '#home' }
}

function goUsers() {
  window.location.hash = '#users'
}
</script>

<template>
  <section class="trade-page account-page">
    <div class="page-heading"><h1>账户</h1><a class="button secondary" href="#home">返回商城首页</a></div>
    <div v-if="user" class="trade-panel">
      <dl class="trade-facts">
        <div><dt>用户名</dt><dd>{{ user.username }}</dd></div>
        <div><dt>角色</dt><dd>{{ roles || '未分配角色' }}</dd></div>
      </dl>
      <form class="auth-form" @submit.prevent="save">
        <label>昵称
          <input v-model="form.nickname" name="nickname" maxlength="64" />
        </label>
        <p v-if="error" class="trade-error" role="alert">{{ error }}</p>
        <p v-if="notice" role="status">{{ notice }}</p>
        <div class="trade-actions">
          <button class="button primary" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存昵称' }}</button>
          <button v-if="isAdmin" class="button secondary" type="button" @click="goUsers">用户管理</button>
          <button class="button secondary" type="button" :disabled="saving" @click="handleLogout">退出登录</button>
        </div>
      </form>
    </div>
  </section>
</template>
