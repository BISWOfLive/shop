<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getCategories, prefetchProduct } from '../api/products'
import { categoryHref, searchHref } from '../utils/shopRoutes'
import { useHomeShowcase } from '../composables/useHomeShowcase'
import { categoryIcon } from '../data/catalog'
import AppIcon from '../components/AppIcon.vue'
import ProductVisual from '../components/ProductVisual.vue'
import HomeShowcase from '../components/HomeShowcase.vue'
import ShopHeader from '../components/ShopHeader.vue'
import ProductStream from '../components/ProductStream.vue'
import ProductDetailDialog from '../components/ProductDetailDialog.vue'
import ShoppingDrawer from '../components/ShoppingDrawer.vue'

const keyword = ref('')
const categories = ref([])
const categoryLoading = ref(true)
const categoryError = ref('')
const categoryPanel = ref(null)
const catalogSection = ref(null)
const header = ref(null)
const detail = ref(null)
const drawer = ref(null)
let categoryController
let disposed = false

const {
  categoryProducts, campaignProducts, homeLoading,
  latestError, affordableError, loadHomeShowcase, loadCategoryShowcase,
} = useHomeShowcase()
const campaignError = computed(() => campaignProducts.value.length ? '' : latestError.value)
const categorySpotlights = computed(() => categories.value.slice(0, 4).map((category, index) => ({
  ...category, product: categoryProducts.value[category.id] || null,
  tone: ['mint', 'lilac', 'blue', 'sand'][index],
})))

async function loadCategories() {
  categoryController?.abort()
  const currentController = new AbortController()
  categoryController = currentController
  categoryLoading.value = true
  categoryError.value = ''
  try {
    const result = await getCategories(currentController.signal)
    if (disposed || categoryController !== currentController) return
    categories.value = result.map(category => ({ ...category, id: String(category.id) }))
    loadCategoryShowcase(categories.value)
  } catch (error) {
    if (error.name === 'AbortError' || disposed || categoryController !== currentController) return
    categoryError.value = error.message
  } finally {
    if (!disposed && categoryController === currentController) categoryLoading.value = false
  }
}
function search() { window.location.hash = searchHref(keyword.value) }
function openCategory(categoryId) { window.location.hash = categoryHref(categoryId) }
function openProduct(product) { detail.value.open(product) }
function scrollCatalog() {
  catalogSection.value?.scrollIntoView({
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start',
  })
}
function scrollCategories() {
  categoryPanel.value?.scrollIntoView({
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'center',
  })
  categoryPanel.value?.focus({ preventScroll: true })
}
function scrollTop() {
  window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
}
onMounted(loadCategories)
onBeforeUnmount(() => { disposed = true; categoryController?.abort() })
</script>

<template>
  <div id="home" class="storefront" data-ui>
    <a class="shop-skip-link" href="#catalog-content" @click.prevent="scrollCatalog">跳到商品列表</a>
    <ShopHeader ref="header" v-model="keyword" show-categories @search="search" @browse-categories="scrollCategories" />

    <main class="shop-container shop-main">
      <section class="shop-discovery" aria-label="商城分类与精选商品">
        <aside ref="categoryPanel" class="shop-category-panel" aria-labelledby="category-title" tabindex="-1">
          <div class="shop-panel-heading">
            <h2 id="category-title"><AppIcon name="grid" />商品分类</h2>
            <button type="button" class="shop-text-button" :disabled="categoryLoading" @click="loadCategories">{{ categoryLoading ? '加载中' : '刷新' }}</button>
          </div>
          <div v-if="categoryError" class="shop-category-message" role="alert">
            <p>分类加载失败：{{ categoryError }}</p>
            <button type="button" class="shop-text-button" @click="loadCategories">重新加载</button>
          </div>
          <p v-else-if="categoryLoading && !categories.length" class="shop-category-message" role="status">正在加载分类…</p>
          <p v-else-if="!categoryLoading && !categories.length" class="shop-category-message">暂无商品分类</p>
          <nav class="shop-category-list" aria-label="商品分类页面">
            <a class="shop-category" href="#products"><AppIcon name="grid" /><span>全部商品</span><AppIcon name="right" /></a>
            <a v-for="category in categories" :key="category.id" class="shop-category" :href="categoryHref(category.id)">
              <AppIcon :name="categoryIcon(category.name)" /><span>{{ category.name }}</span><AppIcon name="right" />
            </a>
          </nav>
          <div class="shop-category-shortcuts">
            <button type="button" @click="drawer.show('latest')"><AppIcon name="clock" />最近新增<AppIcon name="right" /></button>
            <button type="button" @click="drawer.show('price')"><AppIcon name="bag" />低价优选<AppIcon name="right" /></button>
          </div>
        </aside>

        <HomeShowcase :products="campaignProducts" :loading="homeLoading" :error="campaignError"
                      @open-product="openProduct" @browse-category="openCategory"
                      @warm-product="product => prefetchProduct(product.id)" @browse-all="scrollCatalog" @retry="loadHomeShowcase" />

        <aside class="shop-category-spotlights" aria-label="分类专区">
          <a v-for="category in categorySpotlights" :key="category.id" class="shop-category-spotlight"
             :class="'shop-category-spotlight-' + category.tone" :href="categoryHref(category.id)">
            <span>{{ category.name }}<AppIcon name="arrow" /></span>
            <ProductVisual v-if="category.product" :product="category.product" />
            <AppIcon v-else class="shop-category-spotlight-icon" :name="categoryIcon(category.name)" />
          </a>
          <template v-if="!categorySpotlights.length">
            <button type="button" class="shop-category-spotlight shop-category-spotlight-mint" @click="drawer.show('latest')"><span>最近新增<AppIcon name="arrow" /></span><AppIcon class="shop-category-spotlight-icon" name="clock" /></button>
            <button type="button" class="shop-category-spotlight shop-category-spotlight-lilac" @click="drawer.show('price')"><span>低价优选<AppIcon name="arrow" /></span><AppIcon class="shop-category-spotlight-icon" name="bag" /></button>
            <button type="button" class="shop-category-spotlight shop-category-spotlight-blue" @click="header.focusSearch()"><span>搜索商品<AppIcon name="arrow" /></span><AppIcon class="shop-category-spotlight-icon" name="search" /></button>
            <a class="shop-category-spotlight shop-category-spotlight-sand" href="#manage"><span>商品管理<AppIcon name="arrow" /></span><AppIcon class="shop-category-spotlight-icon" name="grid" /></a>
          </template>
        </aside>
      </section>

      <div id="catalog-content" ref="catalogSection" class="shop-stream-anchor" tabindex="-1">
        <ProductStream title="商品推荐" @open-product="openProduct" />
      </div>
    </main>

    <footer class="shop-footer">
      <div class="shop-container shop-footer-inner">
        <strong>精简商城</strong>
        <nav aria-label="页尾导航">
          <button type="button" @click="scrollCategories">商品分类</button>
          <button type="button" @click="drawer.show('latest')">最近新增</button>
          <button type="button" @click="drawer.show('price')">低价优选</button>
          <button type="button" @click="scrollTop">回到顶部 <AppIcon name="up" /></button>
        </nav>
      </div>
    </footer>
    <nav class="shop-floating-tools" aria-label="页面快捷工具">
      <button type="button" @click="scrollCategories"><AppIcon name="grid" /><span>分类</span></button>
      <button type="button" @click="scrollCatalog"><AppIcon name="bag" /><span>商品</span></button>
      <button type="button" @click="header.focusSearch()"><AppIcon name="search" /><span>搜索</span></button>
      <button type="button" @click="scrollTop"><AppIcon name="up" /><span>顶部</span></button>
    </nav>
    <ShoppingDrawer ref="drawer" @open-product="openProduct" />
    <ProductDetailDialog ref="detail" :categories="categories" />
  </div>
</template>
