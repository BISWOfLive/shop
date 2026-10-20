<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { getCategories, getProducts } from '../api/products'
import AppIcon from '../components/AppIcon.vue'
import ProductEditor from '../components/ProductEditor.vue'

const categories = ref([])
const categoryLoading = ref(true)
const categoryError = ref('')
const products = ref([])
const total = ref(0)
const loading = ref(true)
const listError = ref('')
const notice = ref('')
const editor = ref(null)
const brokenImages = reactive({})
const sortMode = ref('createdAt:desc')
const filters = reactive({ keyword: '', categoryId: '', status: '' })
const query = reactive({
  keyword: '', categoryId: '', status: '',
  page: 1, size: 10, sortBy: 'createdAt', sortOrder: 'desc',
})
let requestId = 0
let controller
let noticeTimer

const money = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' })
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.size)))
const firstItem = computed(() => total.value ? (query.page - 1) * query.size + 1 : 0)
const lastItem = computed(() => Math.min(query.page * query.size, total.value))
const hasFilters = computed(() => query.keyword !== '' || query.categoryId !== '' || query.status !== '')
const visiblePages = computed(() => {
  const start = Math.max(1, Math.min(query.page - 2, totalPages.value - 4))
  return Array.from({ length: Math.min(5, totalPages.value) }, (_, index) => start + index)
})

async function loadCategories() {
  categoryLoading.value = true
  categoryError.value = ''
  try {
    categories.value = await getCategories()
    if (!categories.value.length) categoryError.value = '暂无商品分类，请先初始化分类数据。'
  } catch (error) {
    categoryError.value = '分类加载失败：' + error.message
  } finally {
    categoryLoading.value = false
  }
}

async function loadProducts() {
  const currentId = ++requestId
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  listError.value = ''
  try {
    const result = await getProducts({ ...query }, controller.signal)
    if (currentId !== requestId) return
    const maxPage = Math.max(1, Math.ceil(result.total / query.size))
    if (query.page > maxPage) {
      query.page = maxPage
      return await loadProducts()
    }
    products.value = result.records
    total.value = result.total
  } catch (error) {
    if (error.name === 'AbortError' || currentId !== requestId) return
    products.value = []
    total.value = 0
    listError.value = error.message
  } finally {
    if (currentId === requestId) loading.value = false
  }
}

function applyFilters() {
  Object.assign(query, {
    keyword: filters.keyword.trim(), categoryId: filters.categoryId,
    status: filters.status, page: 1,
  })
  loadProducts()
}

function resetFilters() {
  Object.assign(filters, { keyword: '', categoryId: '', status: '' })
  Object.assign(query, {
    keyword: '', categoryId: '', status: '', page: 1,
    sortBy: 'createdAt', sortOrder: 'desc',
  })
  sortMode.value = 'createdAt:desc'
  loadProducts()
}

function changeSort() {
  const [sortBy, sortOrder] = sortMode.value.split(':')
  Object.assign(query, { sortBy, sortOrder, page: 1 })
  loadProducts()
}

function changeSize() {
  query.page = 1
  loadProducts()
}

function goToPage(page) {
  if (loading.value || page < 1 || page > totalPages.value || page === query.page) return
  query.page = page
  loadProducts()
}

function openEditor(product = null) {
  editor.value.open(product)
}

function onSaved({ isNew }) {
  notice.value = isNew ? '商品已新增' : '商品信息已保存'
  clearTimeout(noticeTimer)
  noticeTimer = setTimeout(() => { notice.value = '' }, 4500)
  if (isNew) resetFilters()
  else loadProducts()
}

function formatTime(value) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

onMounted(() => {
  loadCategories()
  loadProducts()
})
onBeforeUnmount(() => {
  controller?.abort()
  clearTimeout(noticeTimer)
})
</script>

<template>
  <section class="product-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">商品中心 / PRODUCTS</p>
        <h1>商品管理</h1>
        <p class="page-description">管理每一件好商品，让商品信息井然有序。</p>
      </div>
      <button class="button primary" type="button" :disabled="!categories.length || categoryLoading"
              @click="openEditor()"><AppIcon name="plus" />新增商品</button>
    </div>

    <div v-if="categoryError" class="alert-banner" role="alert">
      <AppIcon name="alert" /><span>{{ categoryError }}</span>
      <button class="text-button" type="button" :disabled="categoryLoading" @click="loadCategories">重新加载</button>
    </div>
    <div v-if="notice" class="success-banner" role="status">
      <AppIcon name="check" /><span>{{ notice }}</span>
      <button class="icon-button" type="button" aria-label="关闭提示" @click="notice = ''">
        <AppIcon name="close" />
      </button>
    </div>

    <div class="content-card">
      <form class="filter-bar" aria-label="筛选商品" @submit.prevent="applyFilters">
        <label class="filter-field search-field">
          <span>商品搜索</span>
          <div class="search-input">
            <AppIcon name="search" />
            <input v-model="filters.keyword" name="keyword" type="search" maxlength="120"
                   placeholder="搜索商品名称" />
          </div>
        </label>
        <label class="filter-field">
          <span>商品分类</span>
          <select v-model="filters.categoryId" name="categoryId" :disabled="categoryLoading">
            <option value="">全部分类</option>
            <option v-for="category in categories" :key="category.id" :value="category.id">
              {{ category.name }}
            </option>
          </select>
        </label>
        <label class="filter-field">
          <span>商品状态</span>
          <select v-model="filters.status" name="status">
            <option value="">全部状态</option>
            <option value="1">上架</option>
            <option value="0">下架</option>
          </select>
        </label>
        <div class="filter-actions">
          <button class="button primary" type="submit">查询</button>
          <button class="button secondary" type="button" @click="resetFilters">重置</button>
        </div>
      </form>

      <div class="list-toolbar">
        <div class="list-title"><h2>商品列表</h2><span class="count-tag">{{ total }} 件商品</span></div>
        <label class="sort-control">
          <span>排序</span>
          <select v-model="sortMode" name="sort" @change="changeSort">
            <option value="createdAt:desc">最新创建</option>
            <option value="createdAt:asc">最早创建</option>
            <option value="price:asc">价格从低到高</option>
            <option value="price:desc">价格从高到低</option>
            <option value="name:asc">名称升序</option>
            <option value="name:desc">名称降序</option>
          </select>
        </label>
      </div>

      <div v-if="listError" class="list-state error-state" role="alert">
        <span class="state-icon"><AppIcon name="alert" /></span>
        <h3>商品暂时无法加载</h3><p>{{ listError }}</p>
        <button class="button secondary" type="button" @click="loadProducts">重新加载</button>
      </div>
      <div v-else class="table-scroll" :aria-busy="loading">
        <table>
          <caption class="sr-only">商品列表，可按分类、状态和名称筛选，支持排序及分页</caption>
          <thead>
            <tr>
              <th scope="col">商品信息</th><th scope="col">商品分类</th><th scope="col">销售价格</th>
              <th scope="col">状态</th><th scope="col">更新时间</th><th scope="col" class="action-column">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="6" class="loading-cell"><span class="loading-dot"></span>正在加载商品…</td></tr>
            <tr v-else-if="!products.length">
              <td colspan="6">
                <div class="list-state">
                  <span class="state-icon"><AppIcon name="box" /></span>
                  <h3>{{ hasFilters ? '没有找到符合条件的商品' : '从第一件商品开始' }}</h3>
                  <p>{{ hasFilters ? '尝试调整商品名称、分类或状态。' : '添加商品名称、价格和分类，开始整理你的商品库。' }}</p>
                  <button v-if="hasFilters" class="button secondary" type="button" @click="resetFilters">清除筛选</button>
                  <button v-else class="button primary" type="button" :disabled="!categories.length"
                          @click="openEditor()"><AppIcon name="plus" />新增商品</button>
                </div>
              </td>
            </tr>
            <tr v-for="product in loading ? [] : products" :key="product.id">
              <td>
                <div class="product-info">
                  <div class="product-thumbnail">
                    <img v-if="product.imageUrl && brokenImages[product.id] !== product.imageUrl"
                         :src="product.imageUrl" :alt="product.name" loading="lazy"
                         @error="brokenImages[product.id] = product.imageUrl" />
                    <AppIcon v-else name="image" />
                  </div>
                  <div class="product-copy">
                    <strong :title="product.name">{{ product.name }}</strong>
                    <small>ID {{ String(product.id).padStart(6, '0') }}</small>
                  </div>
                </div>
              </td>
              <td><span class="category-name">{{ product.categoryName }}</span></td>
              <td class="price-cell">{{ money.format(product.price) }}</td>
              <td><span class="status-badge" :class="{ 'is-up': product.status === 1 }">
                <i></i>{{ product.status === 1 ? '上架' : '下架' }}
              </span></td>
              <td class="time-cell">{{ formatTime(product.updatedAt) }}</td>
              <td class="action-column">
                <button class="edit-button" type="button" :disabled="!categories.length"
                        :aria-label="'编辑 ' + product.name" @click="openEditor(product)">
                  <AppIcon name="edit" />编辑
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="pagination-bar">
        <p>显示 {{ firstItem }}–{{ lastItem }} 项，共 {{ total }} 项</p>
        <div class="pagination-actions">
          <label class="page-size">
            <span class="sr-only">每页条数</span>
            <select v-model.number="query.size" :disabled="loading" @change="changeSize">
              <option :value="10">10 条 / 页</option><option :value="20">20 条 / 页</option>
              <option :value="50">50 条 / 页</option><option :value="100">100 条 / 页</option>
            </select>
          </label>
          <nav class="page-buttons" aria-label="商品分页">
            <button type="button" aria-label="上一页" :disabled="loading || query.page <= 1"
                    @click="goToPage(query.page - 1)"><AppIcon name="left" /></button>
            <button v-for="page in visiblePages" :key="page" type="button"
                    :class="{ selected: page === query.page }"
                    :aria-label="'第 ' + page + ' 页'" :aria-current="page === query.page ? 'page' : undefined"
                    :disabled="loading" @click="goToPage(page)">{{ page }}</button>
            <button type="button" aria-label="下一页" :disabled="loading || query.page >= totalPages"
                    @click="goToPage(query.page + 1)"><AppIcon name="right" /></button>
          </nav>
          <span class="page-total">共 {{ totalPages }} 页</span>
        </div>
      </footer>
    </div>
    <p class="page-footnote">每一份好生意，都从认真管理商品开始。</p>
    <ProductEditor ref="editor" :categories="categories" @saved="onSaved" />
  </section>
</template>
