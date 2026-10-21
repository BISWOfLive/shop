<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import ProductVisual from './ProductVisual.vue'
import { formatMoney } from '../utils/money'
import { customerProductName } from '../utils/productPresentation'

const props = defineProps({
  products: { type: Array, required: true },
  virtualized: { type: Boolean, default: false },
  scrollRoot: { type: Object, default: null },
})
const emit = defineEmits(['open-product', 'warm-product'])
const grid = ref(null)
const columns = ref(6)
const rowHeight = ref(360)
const gap = ref(14)
const offset = ref(0)
const viewportHeight = ref(900)
const focusedIndex = ref(-1)
let observer
let frame = 0
let disposed = false
let scrollTarget
const stride = computed(() => rowHeight.value + gap.value)
const rows = computed(() => Math.ceil(props.products.length / columns.value))
const range = computed(() => {
  if (!props.virtualized) return { start: 0, end: rows.value }
  const visibleStart = Math.min(Math.max(0, rows.value - 1), Math.floor(offset.value / stride.value))
  let start = Math.max(0, visibleStart - 4)
  let end = Math.min(rows.value, visibleStart + Math.ceil(viewportHeight.value / stride.value) + 5)
  if (focusedIndex.value >= 0) {
    const focusedRow = Math.floor(focusedIndex.value / columns.value)
    if (focusedRow >= start - 1 && focusedRow <= end + 1) {
      start = Math.min(start, Math.max(0, focusedRow - 1))
      end = Math.max(end, Math.min(rows.value, focusedRow + 2))
    }
  }
  return { start, end }
})
const startIndex = computed(() => range.value.start * columns.value)
const visibleProducts = computed(() => props.products.slice(startIndex.value, range.value.end * columns.value))
const gridStyle = computed(() => props.virtualized ? {
  paddingTop: range.value.start * stride.value + 'px',
  paddingBottom: Math.max(0, rows.value - range.value.end) * stride.value + 'px',
  gridAutoRows: `minmax(${rowHeight.value}px, auto)`,
} : undefined)

function updateOffset() {
  if (!grid.value || disposed) return
  const top = props.scrollRoot?.getBoundingClientRect().top || 0
  offset.value = Math.max(0, top - grid.value.getBoundingClientRect().top)
  viewportHeight.value = props.scrollRoot?.clientHeight || window.innerHeight
}

function measure() {
  frame = 0
  if (!grid.value || disposed) return
  const style = getComputedStyle(grid.value)
  const nextColumns = style.gridTemplateColumns.split(' ').filter(Boolean).length || 1
  columns.value = nextColumns
  gap.value = parseFloat(style.rowGap) || 0
  const card = grid.value.querySelector('.shop-product-card')
  if (card) {
    const image = card.querySelector('.catalog-visual')
    const imageHeight = image?.getBoundingClientRect().height || 180
    rowHeight.value = imageHeight + 148
  }
  updateOffset()
}

function scheduleMeasure() {
  if (!frame && !disposed) frame = requestAnimationFrame(measure)
}

let scrollFrame = 0
function onScroll() {
  if (!scrollFrame) scrollFrame = requestAnimationFrame(() => {
    scrollFrame = 0
    updateOffset()
  })
}

function onFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) focusedIndex.value = -1
}

watch(() => props.products.length, () => nextTick(scheduleMeasure))
watch(() => props.scrollRoot, () => {
  scrollTarget?.removeEventListener('scroll', onScroll)
  scrollTarget = props.scrollRoot
  scrollTarget?.addEventListener('scroll', onScroll, { passive: true })
  scheduleMeasure()
}, { flush: 'post' })
onMounted(() => {
  measure()
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(scheduleMeasure)
    observer.observe(grid.value)
  }
  window.addEventListener('scroll', onScroll, { passive: true })
  scrollTarget = props.scrollRoot
  scrollTarget?.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', scheduleMeasure, { passive: true })
})
onBeforeUnmount(() => {
  disposed = true
  observer?.disconnect()
  cancelAnimationFrame(frame)
  cancelAnimationFrame(scrollFrame)
  window.removeEventListener('scroll', onScroll)
  scrollTarget?.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', scheduleMeasure)
})
</script>

<template>
  <div ref="grid" class="shop-product-grid" :class="{ 'shop-product-grid-virtual': virtualized }"
       :style="gridStyle" role="list" @focusout="onFocusOut">
    <article v-for="(product, index) in visibleProducts" :key="product.id" class="shop-product-card"
             role="listitem" :aria-posinset="startIndex + index + 1" :aria-setsize="virtualized ? -1 : products.length">
      <button type="button" class="shop-product-link" :aria-label="'查看商品详情：' + customerProductName(product)"
              @pointerenter="emit('warm-product', product)" @focus="focusedIndex = startIndex + index; emit('warm-product', product)"
              @click="emit('open-product', product)">
        <ProductVisual :product="product" :eager="startIndex + index < 6" />
        <span class="shop-product-name">{{ customerProductName(product) }}</span>
        <span class="shop-product-bottom">
          <span class="shop-product-price"><small>¥</small>{{ formatMoney(product.price) }}</span>
          <span class="shop-product-detail-link">查看详情 <AppIcon name="right" /></span>
        </span>
      </button>
    </article>
  </div>
</template>

<style scoped>
.shop-product-grid-virtual { overflow-anchor: none; }
</style>
