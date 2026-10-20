<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { getCategories, getProducts } from '../api/products'
import AppIcon from '../components/AppIcon.vue'
import ProductVisual from '../components/ProductVisual.vue'
import { categoryPresentation, sampleCategories, sampleProducts } from '../data/catalog'
import '../storefront.css'

const keyword = ref('')
const categories = ref([])
const products = ref([])
const featured = ref([])
const total = ref(0)
const mode = ref('loading')
const loading = ref(true)
const listError = ref('')
const sampleMessage = ref('')
const sortMode = ref('createdAt:desc')
const campaignIndex = ref(0)
const searchInput = ref(null)
const catalogSection = ref(null)
const detailDialog = ref(null)
const selectedProduct = ref(null)
const query = reactive({ keyword: '', categoryId: '', status: 1, page: 1, size: 15, sortBy: 'createdAt', sortOrder: 'desc' })
let controller
let requestId = 0
let disposed = false

const hotKeywords = ['无线耳机', '保温杯', '桌面收纳', '通勤', '键盘']
const campaigns = [
  { title: ['好物上新', '生活刚刚好'], description: '从一件喜欢的好物，开启日常新灵感。', action: '发现精选好物', category: '', image: sampleProducts[0], theme: 'coral' },
  { title: ['把日常', '过成喜欢的样子'], description: '一盏灯、一只杯，把小日子安放妥帖。', action: '逛逛家居好物', category: '家居日用', image: sampleProducts[2], theme: 'sage' },
  { title: ['轻松出门', '装下你的日常'], description: '简简单单的搭配，也能让心情轻盈起来。', action: '发现日常穿搭', category: '服饰箱包', image: sampleProducts[4], theme: 'sand' },
]
const activeCampaign = computed(() => campaigns[campaignIndex.value])
const displayCategories = computed(() => {
  if (mode.value !== 'live') return sampleCategories
  if (categories.value.length) return categories.value
  return Array.from(new Map(products.value.map(product => [product.categoryId, { id: product.categoryId, name: product.categoryName || '商品分类' }])).values())
})
const featuredProducts = computed(() => mode.value === 'demo' ? sampleProducts.slice(0, 4) : featured.value)
const activeCategory = computed(() => displayCategories.value.find(category => String(category.id) === String(query.categoryId)))
const hasFilters = computed(() => Boolean(query.keyword || query.categoryId !== ''))
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.size)))
const visiblePages = computed(() => {
  const start = Math.max(1, Math.min(query.page - 2, totalPages.value - 4))
  return Array.from({ length: Math.min(5, totalPages.value) }, (_, index) => start + index)
})
const priceFormatter = new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const price = value => priceFormatter.format(Number(value) || 0)

function showSamples() {
  let matching = sampleProducts.filter(product =>
    (!query.keyword || `${product.name} ${product.categoryName}`.includes(query.keyword)) &&
    (query.categoryId === '' || String(product.categoryId) === String(query.categoryId)),
  )
  if (query.sortBy === 'price') {
    matching.sort((a, b) => (a.price - b.price) * (query.sortOrder === 'asc' ? 1 : -1))
  } else if (query.sortBy === 'name') {
    matching.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN') * (query.sortOrder === 'asc' ? 1 : -1))
  }
  total.value = matching.length
  products.value = matching.slice((query.page - 1) * query.size, query.page * query.size)
  loading.value = false
}

async function loadProducts() {
  if (mode.value === 'demo') {
    showSamples()
    return
  }
  const currentId = ++requestId
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  listError.value = ''
  try {
    const result = await getProducts({ ...query }, controller.signal)
    if (currentId !== requestId || disposed) return
    if (mode.value !== 'live' && result.total === 0) {
      mode.value = 'demo'
      sampleMessage.value = '当前展示示例商品。添加在售商品后，首页将展示你的真实商品。'
      showSamples()
      return
    }
    mode.value = 'live'
    const maxPage = Math.max(1, Math.ceil(result.total / query.size))
    if (query.page > maxPage) {
      query.page = maxPage
      return await loadProducts()
    }
    products.value = result.records
    total.value = result.total
    if (!hasFilters.value && query.page === 1 && query.sortBy === 'createdAt') featured.value = result.records.slice(0, 4)
  } catch (error) {
    if (error.name === 'AbortError' || currentId !== requestId || disposed) return
    if (mode.value !== 'live') {
      mode.value = 'demo'
      sampleMessage.value = '商品服务暂时不可用，当前展示示例商品与示例价格。'
      showSamples()
    } else {
      products.value = []
      total.value = 0
      listError.value = error.message
    }
  } finally {
    if (currentId === requestId && !disposed) loading.value = false
  }
}

async function loadCategories() {
  try {
    const result = await getCategories()
    if (!disposed) categories.value = result
  } catch {
    // 商品仍可浏览；分类不可用时由已返回的真实商品生成分类入口。
  }
}

function scrollCatalog() {
  catalogSection.value?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth', block: 'start' })
}

function search(term) {
  if (typeof term === 'string') keyword.value = term
  query.keyword = keyword.value.trim()
  query.page = 1
  loadProducts()
  scrollCatalog()
}

function selectCategory(category, scroll = true) {
  query.categoryId = category?.id ?? ''
  query.keyword = ''
  keyword.value = ''
  query.page = 1
  loadProducts()
  if (scroll) scrollCatalog()
}

function browseCategory(name) {
  selectCategory(displayCategories.value.find(category => category.name === name))
}

function resetFilters() {
  Object.assign(query, { keyword: '', categoryId: '', page: 1, sortBy: 'createdAt', sortOrder: 'desc' })
  keyword.value = ''
  sortMode.value = 'createdAt:desc'
  loadProducts()
}

function setSort(value, scroll = false) {
  sortMode.value = value
  const [sortBy, sortOrder] = value.split(':')
  Object.assign(query, { sortBy, sortOrder, page: 1 })
  loadProducts()
  if (scroll) scrollCatalog()
}

function goToPage(page) {
  if (loading.value || page < 1 || page > totalPages.value || page === query.page) return
  query.page = page
  loadProducts()
  scrollCatalog()
}

function retryLiveCatalog() {
  mode.value = 'loading'
  resetFilters()
  loadCategories()
}

function changeCampaign(direction) {
  campaignIndex.value = (campaignIndex.value + direction + campaigns.length) % campaigns.length
}

async function openProduct(product) {
  selectedProduct.value = product
  await nextTick()
  detailDialog.value.showModal()
}

function browseRelated() {
  const product = selectedProduct.value
  detailDialog.value.close()
  selectCategory({ id: product.categoryId, name: product.categoryName })
}

function focusSearch() {
  window.scrollTo({ top: 0 })
  searchInput.value?.focus({ preventScroll: true })
}

function scrollTop() {
  window.scrollTo({ top: 0 })
}

onMounted(() => {
  loadCategories()
  loadProducts()
})
onBeforeUnmount(() => {
  disposed = true
  controller?.abort()
})
</script>

<template>
  <div class="storefront">
    <a class="shop-skip-link" href="#catalog-content">跳到商品列表</a>
    <div class="shop-utility">
      <div class="shop-container shop-utility-inner">
        <span>你好，欢迎来到精简商城！</span>
        <nav aria-label="快捷导航">
          <button type="button" @click="resetFilters(); scrollCatalog()">发现好物</button>
          <button type="button" @click="scrollCatalog">全部商品</button>
          <a href="#manage">商家管理 <AppIcon name="right" /></a>
        </nav>
      </div>
    </div>

    <header class="shop-header">
      <div class="shop-container shop-masthead">
        <a class="shop-brand" href="#home" aria-label="精简商城首页">
          <span class="shop-brand-mark">简</span>
          <span class="shop-brand-copy"><strong>精简商城</strong><span>好物相伴 · 日常刚好</span></span>
        </a>
        <div class="shop-search-area">
          <form class="shop-search" role="search" @submit.prevent="search()">
            <AppIcon name="search" />
            <label class="sr-only" for="shop-search">搜索商品</label>
            <input id="shop-search" ref="searchInput" v-model="keyword" type="search" maxlength="120"
                   placeholder="搜点喜欢的好物…" :disabled="loading" />
            <button type="submit" :disabled="loading">搜索</button>
          </form>
          <div class="shop-hot-searches" aria-label="热门搜索">
            <span>大家都在逛</span>
            <button v-for="(term, index) in hotKeywords" :key="term" type="button"
                    :class="{ 'shop-hot-word': index === 0 }" :disabled="loading" @click="search(term)">{{ term }}</button>
          </div>
        </div>
        <a class="shop-manage-link" href="#manage"><AppIcon name="bag" /><span>商品管理</span><AppIcon name="right" /></a>
      </div>
      <div class="shop-container shop-main-nav">
        <div class="shop-category-title"><AppIcon name="grid" />全部商品分类</div>
        <nav aria-label="商城导航">
          <button class="shop-nav-home" type="button" @click="resetFilters(); scrollCatalog()">商城首页</button>
          <button v-for="category in displayCategories.slice(0, 5)" :key="category.id" type="button"
                  :disabled="loading" @click="selectCategory(category)">{{ category.name }}</button>
          <button type="button" class="shop-nav-new" :disabled="loading" @click="setSort('createdAt:desc', true)">发现上新 <AppIcon name="spark" /></button>
        </nav>
        <span class="shop-nav-note">认真选好物，简单过日常</span>
      </div>
    </header>

    <main class="shop-container shop-main">
      <section class="shop-discovery" aria-label="发现商城好物">
        <aside class="shop-categories" aria-label="商品分类">
          <button v-for="category in displayCategories.slice(0, 6)" :key="category.id" class="shop-category"
                  type="button" :disabled="loading" @click="selectCategory(category)">
            <AppIcon :name="categoryPresentation(category.name).icon" />
            <span><strong>{{ category.name }}</strong><small>{{ categoryPresentation(category.name).detail }}</small></span>
            <AppIcon name="right" />
          </button>
          <div class="shop-category-bottom"><AppIcon name="spark" /><span>喜欢的好物<br /><strong>就在这里等你</strong></span></div>
        </aside>

        <div class="shop-campaigns">
          <div class="shop-hero" :class="'shop-hero-' + activeCampaign.theme" aria-roledescription="轮播图" aria-label="好物灵感">
            <div class="shop-hero-copy">
              <p class="shop-hero-intro">精简好物季</p>
              <h1 id="campaign-title" aria-live="polite">{{ activeCampaign.title[0] }}<br />{{ activeCampaign.title[1] }}</h1>
              <p class="shop-hero-description">{{ activeCampaign.description }}</p>
              <button class="shop-hero-action" type="button" :disabled="loading" @click="browseCategory(activeCampaign.category)">{{ activeCampaign.action }}<AppIcon name="arrow" /></button>
            </div>
            <div class="shop-hero-art"><ProductVisual :product="activeCampaign.image" /></div>
            <span class="shop-hero-caption">好物灵感 · 图片为商品示意</span>
            <div class="shop-carousel-controls">
              <button type="button" class="shop-carousel-arrow" aria-label="上一张好物灵感" @click="changeCampaign(-1)"><AppIcon name="left" /></button>
              <div class="shop-carousel-dots">
                <button v-for="(campaign, index) in campaigns" :key="campaign.theme" type="button"
                        :aria-label="'查看' + campaign.title.join('，')" :aria-pressed="campaignIndex === index"
                        :class="{ active: campaignIndex === index }" @click="campaignIndex = index"></button>
              </div>
              <button type="button" class="shop-carousel-arrow" aria-label="下一张好物灵感" @click="changeCampaign(1)"><AppIcon name="right" /></button>
            </div>
          </div>
          <div class="shop-scene-row">
            <button class="shop-scene shop-scene-digital" type="button" :disabled="loading" @click="browseCategory('数码配件')">
              <span class="shop-scene-copy"><strong>桌面新灵感</strong><span>让专注多一点乐趣</span><b>数码好物 <AppIcon name="right" /></b></span>
              <ProductVisual :product="sampleProducts[3]" />
            </button>
            <button class="shop-scene shop-scene-home" type="button" :disabled="loading" @click="browseCategory('家居日用')">
              <span class="shop-scene-copy"><strong>日常好搭档</strong><span>把小日子过得有声有色</span><b>家居日用 <AppIcon name="right" /></b></span>
              <ProductVisual :product="sampleProducts[1]" />
            </button>
          </div>
        </div>

        <aside class="shop-side-panels">
          <div class="shop-welcome">
            <span class="shop-avatar"><AppIcon name="user" /></span>
            <h2>Hi，欢迎来逛逛</h2>
            <p>发现一件好物<br />也发现日常的小美好</p>
            <button class="shop-solid-button" type="button" @click="scrollCatalog">开始挑好物 <AppIcon name="arrow" /></button>
            <a href="#manage">我是商家，管理商品 <AppIcon name="right" /></a>
          </div>
          <div class="shop-quick-panel">
            <div class="shop-panel-heading"><h2>逛点不一样的</h2><AppIcon name="spark" /></div>
            <button type="button" :disabled="loading" @click="setSort('createdAt:desc', true)"><span class="shop-quick-icon"><AppIcon name="clock" /></span><span><strong>新鲜上架</strong><small>遇见最近的好物</small></span><AppIcon name="right" /></button>
            <button type="button" :disabled="loading" @click="setSort('price:asc', true)"><span class="shop-quick-icon"><AppIcon name="bag" /></span><span><strong>价格优先</strong><small>按心意，慢慢挑</small></span><AppIcon name="right" /></button>
          </div>
        </aside>
      </section>

      <div v-if="mode === 'demo'" class="shop-demo-notice" role="status">
        <span><AppIcon name="image" />{{ sampleMessage }}</span>
        <button type="button" @click="retryLiveCatalog">重新加载商品 <AppIcon name="right" /></button>
      </div>

      <section v-if="featuredProducts.length" class="shop-featured" aria-labelledby="featured-title">
        <div class="shop-featured-intro">
          <span class="shop-featured-mark"><AppIcon name="spark" /></span>
          <h2 id="featured-title">好物<br />值得一逛</h2>
          <p>总有一件，刚好喜欢</p>
          <button type="button" :disabled="loading" @click="resetFilters(); scrollCatalog()">发现更多 <AppIcon name="arrow" /></button>
        </div>
        <button v-for="product in featuredProducts" :key="product.id" class="shop-featured-product" type="button" @click="openProduct(product)">
          <ProductVisual :product="product" />
          <span class="shop-featured-name">{{ product.name }}</span>
          <span class="shop-featured-price"><small>¥</small>{{ price(product.price) }}<span v-if="product.isSample">示例</span></span>
        </button>
      </section>

      <section id="catalog-content" ref="catalogSection" class="shop-catalog" aria-labelledby="catalog-title" tabindex="-1">
        <div class="shop-catalog-heading"><div><h2 id="catalog-title">为你推荐</h2><span>好物不必复杂，喜欢就是刚好</span></div><span class="shop-catalog-count" aria-live="polite">{{ loading ? '正在寻找好物…' : total + ' 件' + (mode === 'demo' ? '示例商品' : '商品') }}</span></div>
        <div class="shop-catalog-tabs" aria-label="按分类筛选">
          <button type="button" :class="{ selected: query.categoryId === '' }" :aria-pressed="query.categoryId === ''" :disabled="loading" @click="selectCategory(null, false)"><strong>精选推荐</strong><span>猜你喜欢</span></button>
          <button v-for="category in displayCategories" :key="category.id" type="button" :disabled="loading"
                  :class="{ selected: String(query.categoryId) === String(category.id) }"
                  :aria-pressed="String(query.categoryId) === String(category.id)" @click="selectCategory(category, false)"><strong>{{ category.name }}</strong><span>{{ categoryPresentation(category.name).note }}</span></button>
        </div>
        <div class="shop-catalog-toolbar">
          <div class="shop-sort-buttons" aria-label="商品排序">
            <button type="button" :class="{ selected: sortMode === 'createdAt:desc' }" :aria-pressed="sortMode === 'createdAt:desc'" :disabled="loading" @click="setSort('createdAt:desc')">综合推荐</button>
            <button type="button" :class="{ selected: sortMode === 'price:asc' }" :aria-pressed="sortMode === 'price:asc'" :disabled="loading" @click="setSort('price:asc')">价格从低到高</button>
            <button type="button" :class="{ selected: sortMode === 'price:desc' }" :aria-pressed="sortMode === 'price:desc'" :disabled="loading" @click="setSort('price:desc')">价格从高到低</button>
          </div>
          <label class="shop-sort-select"><span class="sr-only">商品排序方式</span><select v-model="sortMode" :disabled="loading" @change="setSort(sortMode)"><option value="createdAt:desc">综合推荐</option><option value="price:asc">价格从低到高</option><option value="price:desc">价格从高到低</option><option value="name:asc">商品名称排序</option></select></label>
          <span v-if="!hasFilters" class="shop-toolbar-note">慢慢逛，找到你的心头好</span>
          <button v-else type="button" class="shop-clear-filters" :disabled="loading" @click="resetFilters"><span>{{ query.keyword ? '“' + query.keyword + '”' : activeCategory?.name || '当前分类' }}</span><AppIcon name="close" />清除筛选</button>
        </div>

        <div v-if="loading" class="shop-product-grid" aria-busy="true" aria-label="商品加载中">
          <div v-for="index in 5" :key="index" class="shop-skeleton-card" aria-hidden="true"><span></span><i></i><i></i><b></b></div>
        </div>
        <div v-else-if="listError" class="shop-empty-state" role="alert"><AppIcon name="alert" /><h3>好物暂时没有加载出来</h3><p>{{ listError }}</p><button class="shop-solid-button" type="button" @click="loadProducts">重新加载</button></div>
        <div v-else-if="!products.length" class="shop-empty-state"><AppIcon name="search" /><h3>还没有找到这件好物</h3><p>换个关键词，或看看其他分类，也许就能遇见喜欢的。</p><button class="shop-solid-button" type="button" @click="resetFilters">看看全部商品</button></div>
        <div v-else class="shop-product-grid">
          <article v-for="product in products" :key="product.id" class="shop-product-card">
            <button type="button" class="shop-product-link" @click="openProduct(product)">
              <ProductVisual :product="product" />
              <span class="shop-product-category">{{ product.categoryName || '精选商品' }}<span v-if="product.isSample">示例商品</span></span>
              <span class="shop-product-name">{{ product.name }}</span>
              <span class="shop-product-bottom"><span class="shop-product-price"><small>¥</small>{{ price(product.price) }}</span><span class="shop-product-arrow"><AppIcon name="arrow" /></span></span>
            </button>
          </article>
        </div>

        <nav v-if="totalPages > 1" class="shop-pagination" aria-label="商城商品分页">
          <span>共 {{ total }} 件商品</span>
          <button type="button" :disabled="loading || query.page <= 1" @click="goToPage(query.page - 1)"><AppIcon name="left" />上一页</button>
          <button v-for="page in visiblePages" :key="page" class="shop-page-number" type="button" :disabled="loading" :class="{ selected: query.page === page }" :aria-current="query.page === page ? 'page' : undefined" :aria-label="'第 ' + page + ' 页'" @click="goToPage(page)">{{ page }}</button>
          <button type="button" :disabled="loading || query.page >= totalPages" @click="goToPage(query.page + 1)">下一页<AppIcon name="right" /></button>
        </nav>
        <p v-if="products.length && !loading" class="shop-catalog-end">每一件喜欢，都让日常更好一点</p>
      </section>
    </main>

    <footer class="shop-footer">
      <div class="shop-container">
        <div class="shop-values">
          <div><span>选</span><p><strong>好物有分类</strong><small>按需挑选，直达喜欢</small></p></div>
          <div><span>逛</span><p><strong>日常有灵感</strong><small>从一件好物开始发现</small></p></div>
          <div><span>比</span><p><strong>价格看得见</strong><small>轻松排序，从容选择</small></p></div>
          <div><span>简</span><p><strong>经营更简单</strong><small>商品管理，清晰有序</small></p></div>
        </div>
        <div class="shop-footer-main">
          <div class="shop-footer-brand"><strong>精简商城</strong><p>好物相伴，日常刚好。</p></div>
          <div class="shop-footer-links"><h3>逛逛商城</h3><button v-for="category in displayCategories.slice(0, 4)" :key="category.id" type="button" :disabled="loading" @click="selectCategory(category)">{{ category.name }}</button></div>
          <div class="shop-footer-links"><h3>发现好物</h3><button type="button" @click="resetFilters(); scrollCatalog()">全部商品</button><button type="button" :disabled="loading" @click="setSort('createdAt:desc', true)">最近上新</button><button type="button" :disabled="loading" @click="setSort('price:asc', true)">按价格挑选</button></div>
          <div class="shop-footer-links"><h3>商家入口</h3><a href="#manage">商品管理</a><a href="#manage">发布新商品</a><a href="#manage">编辑商品信息</a></div>
          <div class="shop-footer-message"><AppIcon name="bag" /><p>简单挑选<br /><strong>认真生活</strong></p></div>
        </div>
        <div class="shop-footer-bottom"><span>© 2026 精简商城</span><span>用心发现每一件日常好物</span><button type="button" @click="scrollTop">回到顶部 <AppIcon name="up" /></button></div>
      </div>
    </footer>

    <nav class="shop-floating-tools" aria-label="页面工具"><button type="button" @click="focusSearch"><AppIcon name="search" /><span>搜索</span></button><a href="#manage"><AppIcon name="bag" /><span>管理</span></a><button type="button" @click="scrollTop"><AppIcon name="up" /><span>顶部</span></button></nav>

    <dialog ref="detailDialog" class="shop-detail-dialog" aria-labelledby="shop-detail-title" @click="event => { if (event.target === event.currentTarget) detailDialog.close() }">
      <template v-if="selectedProduct">
        <button type="button" class="shop-dialog-close" aria-label="关闭商品详情" @click="detailDialog.close()"><AppIcon name="close" /></button>
        <div class="shop-detail-layout">
          <ProductVisual :product="selectedProduct" />
          <div class="shop-detail-copy"><span class="shop-detail-category">{{ selectedProduct.categoryName }}{{ selectedProduct.isSample ? ' · 示例商品' : '' }}</span><h2 id="shop-detail-title">{{ selectedProduct.name }}</h2><p class="shop-detail-price"><small>¥</small>{{ price(selectedProduct.price) }}</p><p class="shop-detail-description">{{ selectedProduct.description || '这件商品暂未填写详细介绍，可以继续浏览同类好物。' }}</p><p v-if="selectedProduct.isSample" class="shop-detail-sample-note">图片与价格用于展示商城设计，示例商品不支持购买。</p><button class="shop-solid-button" type="button" @click="browseRelated">浏览同类商品 <AppIcon name="arrow" /></button><button class="shop-detail-back" type="button" @click="detailDialog.close()">返回商品列表</button></div>
        </div>
      </template>
    </dialog>
  </div>
</template>
