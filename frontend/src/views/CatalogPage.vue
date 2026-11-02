<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getCategories } from '../api/products'
import { searchHref } from '../utils/shopRoutes'
import ShopHeader from '../components/ShopHeader.vue'
import ProductStream from '../components/ProductStream.vue'
import ProductDetailDialog from '../components/ProductDetailDialog.vue'
import ShoppingDrawer from '../components/ShoppingDrawer.vue'
import AppIcon from '../components/AppIcon.vue'

const props = defineProps({ route: { type: Object, required: true } })
const keyword = ref(props.route.keyword || '')
const categories = ref([])
const categoryLoading = ref(true)
const categoryError = ref('')
const header = ref(null)
const detail = ref(null)
const drawer = ref(null)
let controller
let disposed = false

const activeCategory = computed(() => categories.value.find(category => category.id === props.route.categoryId))
const available = computed(() => !props.route.invalidCategory && (!props.route.categoryId || (!categoryLoading.value && !categoryError.value && Boolean(activeCategory.value))))
const scopeName = computed(() => activeCategory.value?.name || (props.route.categoryId || props.route.invalidCategory ? '商品分类' : '全部商品'))

async function loadCategories() {
  controller?.abort()
  const currentController = new AbortController()
  controller = currentController
  categoryLoading.value = true
  categoryError.value = ''
  try {
    const result = await getCategories(currentController.signal)
    if (disposed || controller !== currentController) return
    categories.value = result.map(category => ({ ...category, id: String(category.id) }))
  } catch (error) {
    if (error.name === 'AbortError' || disposed || controller !== currentController) return
    categoryError.value = error.message
  } finally {
    if (!disposed && controller === currentController) categoryLoading.value = false
  }
}

function search() {
  window.location.hash = searchHref(keyword.value, props.route.categoryId)
}
function openProduct(product) { detail.value.open(product) }
function scrollTop() { window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' }) }

watch(() => props.route.keyword, value => { keyword.value = value || '' })
watch(() => [props.route.categoryId, props.route.keyword], () => { drawer.value?.close() })
onMounted(loadCategories)
onBeforeUnmount(() => { disposed = true; controller?.abort() })
</script>

<template>
  <div class="storefront storefront-catalog" data-ui>
    <ShopHeader ref="header" v-model="keyword" :scope-name="scopeName" :disabled="!available" @search="search" />
    <main class="shop-container shop-main shop-category-main">
      <ProductStream v-if="available" :category-id="route.categoryId" :keyword="route.keyword" @open-product="openProduct" />
      <section v-else class="shop-catalog shop-empty-state" :aria-busy="categoryLoading">
        <template v-if="categoryLoading && !route.invalidCategory"><p role="status">正在加载分类…</p></template>
        <template v-else-if="categoryError && !route.invalidCategory">
          <AppIcon name="alert" /><h1>分类暂时无法加载</h1><p role="alert">{{ categoryError }}</p>
          <button type="button" class="shop-solid-button" @click="loadCategories">重新加载</button>
        </template>
        <template v-else><AppIcon name="box" /><h1>分类不存在</h1><a href="#home" class="shop-solid-button">返回商城首页</a></template>
      </section>
    </main>
    <footer class="shop-footer">
      <div class="shop-container shop-footer-inner">
        <a href="#home"><strong>精简商城</strong></a>
        <nav aria-label="页尾导航">
          <a href="#home">商城首页</a>
          <button type="button" @click="drawer.show('latest')">最近新增</button>
          <button type="button" @click="drawer.show('price')">低价优选</button>
          <button type="button" @click="scrollTop">回到顶部 <AppIcon name="up" /></button>
        </nav>
      </div>
    </footer>
    <nav class="shop-floating-tools" aria-label="页面快捷工具">
      <a href="#home"><AppIcon name="home" /><span>首页</span></a>
      <button type="button" @click="header.focusSearch()"><AppIcon name="search" /><span>搜索</span></button>
      <button type="button" @click="drawer.show('latest')"><AppIcon name="clock" /><span>最近新增</span></button>
      <button type="button" @click="drawer.show('price')"><AppIcon name="bag" /><span>低价优选</span></button>
    </nav>
    <ShoppingDrawer ref="drawer" :category-id="route.categoryId" :enabled="available" @open-product="openProduct" />
    <ProductDetailDialog ref="detail" :categories="categories" />
  </div>
</template>
