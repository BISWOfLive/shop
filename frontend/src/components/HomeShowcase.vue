<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import ProductVisual from './ProductVisual.vue'
import { customerProductName } from '../utils/productPresentation'

const props = defineProps({
  products: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
})
const emit = defineEmits(['open-product', 'warm-product', 'browse-category', 'browse-all', 'retry'])
const activeIndex = ref(0)
const paused = ref(false)
const themes = ['violet', 'blue', 'rose', 'mint']
let timer

const activeProduct = computed(() => props.products[activeIndex.value] || null)
const companionProducts = computed(() => props.products.filter(product => product.id !== activeProduct.value?.id).slice(0, 2))
const theme = computed(() => themes[activeIndex.value % themes.length])
const money = new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

watch(() => props.products.map(product => product.id).join(','), () => { activeIndex.value = 0 })

function changeSlide(direction) {
  if (props.products.length < 2) return
  activeIndex.value = (activeIndex.value + direction + props.products.length) % props.products.length
}

function onFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) paused.value = false
}

onMounted(() => {
  timer = window.setInterval(() => {
    if (!paused.value && !document.hidden && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      changeSlide(1)
    }
  }, 6000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <section class="shop-showcase" :class="'shop-showcase-' + theme"
           aria-roledescription="轮播图" aria-label="商品精选" @mouseenter="paused = true" @mouseleave="paused = false"
           @focusin="paused = true" @focusout="onFocusOut"
           @keydown.left.prevent="changeSlide(-1)" @keydown.right.prevent="changeSlide(1)">
    <Transition name="shop-slide" mode="out-in">
      <div v-if="activeProduct" :key="activeProduct.id" class="shop-showcase-slide">
        <div class="shop-showcase-copy">
          <button v-if="activeProduct.categoryId" type="button" class="shop-showcase-category"
                  @click="emit('browse-category', activeProduct.categoryId)">
            {{ activeProduct.categoryName || '浏览同类' }} <AppIcon name="right" />
          </button>
          <h1><button type="button" :title="customerProductName(activeProduct)" @pointerenter="emit('warm-product', activeProduct)" @focus="emit('warm-product', activeProduct)" @click="emit('open-product', activeProduct)">{{ customerProductName(activeProduct) }}</button></h1>
          <p class="shop-showcase-price"><span>¥</span>{{ money.format(activeProduct.price) }}</p>
          <button type="button" class="shop-showcase-action" @pointerenter="emit('warm-product', activeProduct)" @focus="emit('warm-product', activeProduct)" @click="emit('open-product', activeProduct)">查看商品 <AppIcon name="arrow" /></button>
        </div>
        <div class="shop-showcase-media">
          <button type="button" class="shop-showcase-main-product" :aria-label="'查看商品：' + customerProductName(activeProduct)"
                  @pointerenter="emit('warm-product', activeProduct)" @focus="emit('warm-product', activeProduct)" @click="emit('open-product', activeProduct)">
            <ProductVisual :product="activeProduct" eager />
          </button>
          <div v-if="companionProducts.length" class="shop-showcase-companions">
            <button v-for="product in companionProducts" :key="product.id" type="button" :aria-label="'查看商品：' + customerProductName(product)"
                    @pointerenter="emit('warm-product', product)" @focus="emit('warm-product', product)" @click="emit('open-product', product)"><ProductVisual :product="product" /></button>
          </div>
        </div>
      </div>
      <div v-else key="empty" class="shop-showcase-empty" :aria-busy="loading">
        <AppIcon :name="error ? 'alert' : 'bag'" />
        <h1>{{ loading ? '正在加载商品' : error ? '商品暂时无法加载' : '暂无在售商品' }}</h1>
        <button v-if="error" type="button" class="shop-showcase-action" @click="emit('retry')">重新加载 <AppIcon name="arrow" /></button>
        <button v-else type="button" class="shop-showcase-action" @click="emit('browse-all')">浏览全部商品 <AppIcon name="arrow" /></button>
      </div>
    </Transition>
    <div v-if="products.length > 1" class="shop-showcase-controls">
      <button type="button" class="shop-showcase-arrow" aria-label="上一件商品" @click="changeSlide(-1)"><AppIcon name="left" /></button>
      <div class="shop-showcase-dots">
        <button v-for="(product, index) in products" :key="product.id" type="button"
                :class="{ active: index === activeIndex }" :aria-pressed="index === activeIndex"
                :aria-label="'查看第 ' + (index + 1) + ' 件商品：' + customerProductName(product)" @click="activeIndex = index"><span></span></button>
      </div>
      <button type="button" class="shop-showcase-arrow" aria-label="下一件商品" @click="changeSlide(1)"><AppIcon name="right" /></button>
    </div>
  </section>
</template>
