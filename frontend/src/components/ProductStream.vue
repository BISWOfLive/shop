<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useInfiniteProducts } from '../composables/useInfiniteProducts'
import { prefetchProduct } from '../api/products'
import AppIcon from './AppIcon.vue'
import ProductCatalogGrid from './ProductCatalogGrid.vue'

const props = defineProps({
  title: { type: String, default: '' },
  categoryId: { type: String, default: '' },
  keyword: { type: String, default: '' },
  sortBy: { type: String, default: 'createdAt' },
  sortOrder: { type: String, default: 'desc' },
  enabled: { type: Boolean, default: true },
  scrollRoot: { type: Object, default: null },
})
defineEmits(['open-product'])
const sentinel = ref(null)
const { products, total, feedMode, loading, loadingMore, error, hasMore, loadNext, retry } = useInfiniteProducts(() => ({
  categoryId: props.categoryId,
  keyword: props.keyword,
  sortBy: props.sortBy,
  sortOrder: props.sortOrder,
  enabled: props.enabled,
}))
let observer
let scrollTarget
let frame
let disposed = false
let warmTimer

function warmProduct(product) {
  window.clearTimeout(warmTimer)
  warmTimer = window.setTimeout(() => { if (!disposed) prefetchProduct(product.id) }, 100)
}

function loadIfNearEnd() {
  if (disposed || !props.enabled || loading.value || loadingMore.value || error.value || !hasMore.value || !sentinel.value) return
  const bottom = props.scrollRoot ? props.scrollRoot.getBoundingClientRect().bottom : window.innerHeight
  if (sentinel.value.getBoundingClientRect().top <= bottom + 500) loadNext()
}

function queueLoad() {
  if (disposed) return
  window.cancelAnimationFrame(frame)
  window.clearTimeout(warmTimer)
  frame = window.requestAnimationFrame(loadIfNearEnd)
}

function connectObserver() {
  observer?.disconnect()
  scrollTarget?.removeEventListener('scroll', queueLoad)
  window.removeEventListener('resize', queueLoad)
  if (disposed || !sentinel.value) return
  if ('IntersectionObserver' in window) {
    observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) queueLoad()
    }, { root: props.scrollRoot || null, rootMargin: '0px 0px 500px 0px', threshold: 0 })
    observer.observe(sentinel.value)
  } else {
    scrollTarget = props.scrollRoot || window
    scrollTarget.addEventListener('scroll', queueLoad, { passive: true })
  }
  window.addEventListener('resize', queueLoad)
  queueLoad()
}

watch(() => props.scrollRoot, async () => {
  await nextTick()
  connectObserver()
}, { flush: 'post' })
watch([() => products.value.length, loading, loadingMore, error, hasMore, () => props.enabled], queueLoad, { flush: 'post' })
onMounted(connectObserver)
onBeforeUnmount(() => {
  disposed = true
  observer?.disconnect()
  scrollTarget?.removeEventListener('scroll', queueLoad)
  window.removeEventListener('resize', queueLoad)
  window.cancelAnimationFrame(frame)
  window.clearTimeout(warmTimer)
})
</script>

<template>
  <section class="shop-catalog shop-product-stream" :aria-label="title || '商品列表'" :aria-busy="loading || loadingMore">
    <div v-if="title" class="shop-catalog-heading">
      <h2><AppIcon name="bag" />{{ title }}</h2>
      <span class="shop-catalog-count" role="status">{{ loading ? '正在加载…' : error && !products.length ? '暂不可用' : feedMode ? '已加载 ' + products.length + ' 件' : total + ' 件商品' }}</span>
    </div>
    <div v-if="loading && !products.length" class="shop-product-grid" aria-label="商品加载中">
      <div v-for="index in 6" :key="index" class="shop-skeleton-card" aria-hidden="true"><span></span><i></i><i></i><b></b></div>
    </div>
    <div v-else-if="error && !products.length" class="shop-empty-state" role="alert">
      <AppIcon name="alert" /><h3>商品暂时无法加载</h3><p>{{ error }}</p>
      <button type="button" class="shop-solid-button" @click="retry">重新加载</button>
    </div>
    <div v-else-if="!products.length && enabled" class="shop-empty-state">
      <AppIcon name="box" /><h3>{{ keyword ? '没有找到相关商品' : '暂无在售商品' }}</h3>
    </div>
    <ProductCatalogGrid v-else :products="products" virtualized :scroll-root="scrollRoot"
                        @open-product="$emit('open-product', $event)" @warm-product="warmProduct" />
    <div v-if="products.length" class="shop-stream-state">
      <p v-if="loadingMore" role="status">正在加载商品…</p>
      <div v-else-if="error" role="alert"><p>{{ error }}</p><button type="button" class="shop-text-button" @click="retry">重试加载</button></div>
      <p v-else-if="!hasMore" role="status">已显示全部商品</p>
      <button v-else type="button" class="shop-text-button" @click="loadNext">加载更多</button>
    </div>
    <div ref="sentinel" class="shop-stream-sentinel" aria-hidden="true"></div>
  </section>
</template>
