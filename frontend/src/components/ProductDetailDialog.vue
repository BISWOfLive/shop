<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { getProduct } from '../api/products'
import { categoryHref } from '../utils/shopRoutes'
import { formatMoney } from '../utils/money'
import { customerProductName } from '../utils/productPresentation'
import AppIcon from './AppIcon.vue'
import ProductVisual from './ProductVisual.vue'
import { usePurchaseList } from '../composables/usePurchaseList'
const purchase = usePurchaseList()
const quantity = ref(1)
const purchaseMessage = ref('')
function choose(now) {
  purchaseMessage.value = ''
  try {
    if (now) {
      purchase.buyNow(product.value, quantity.value)
      dialog.value.close()
      window.location.hash = '#checkout'
    } else {
      purchase.add(product.value, quantity.value)
      purchaseMessage.value = '已加入采购清单'
    }
  } catch (cause) { purchaseMessage.value = cause.message }
}

const props = defineProps({ categories: { type: Array, default: () => [] } })
const dialog = ref(null)
const product = ref(null)
const loading = ref(false)
const error = ref('')
let controller
let requestId = 0
let disposed = false
const canBrowseRelated = computed(() => props.categories.some(category => String(category.id) === product.value?.categoryId))

async function loadDetail(productId) {
  const currentId = ++requestId
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const result = await getProduct(productId, controller.signal)
    if (disposed || currentId !== requestId) return
    if (result.status !== 1) throw new Error('这件商品已下架，请浏览其他商品。')
    product.value = {
      ...result,
      id: String(result.id),
      categoryId: result.categoryId == null ? '' : String(result.categoryId),
    }
  } catch (cause) {
    if (cause.name === 'AbortError' || disposed || currentId !== requestId) return
    error.value = cause.message
  } finally {
    if (!disposed && currentId === requestId) loading.value = false
  }
}

async function open(selected) {
  quantity.value = 1
  purchaseMessage.value = ''
  product.value = {
    ...selected, id: String(selected.id),
    categoryId: selected.categoryId == null ? '' : String(selected.categoryId),
  }
  loading.value = true
  error.value = ''
  await nextTick()
  if (disposed) return
  if (!dialog.value.open) dialog.value.showModal()
  loadDetail(selected.id)
}

function onClosed() {
  requestId += 1
  controller?.abort()
  product.value = null
  loading.value = false
  error.value = ''
}
onBeforeUnmount(() => { disposed = true; controller?.abort() })
defineExpose({ open })
</script>

<template>
  <dialog ref="dialog" class="shop-detail-dialog" aria-labelledby="shop-detail-title"
          @close="onClosed" @click="event => { if (event.target === event.currentTarget) dialog.close() }">
    <template v-if="product">
      <button type="button" class="shop-dialog-close" aria-label="关闭商品详情" @click="dialog.close()"><AppIcon name="close" /></button>
      <div class="shop-detail-layout">
        <ProductVisual :product="product" eager />
        <div class="shop-detail-copy">
          <span class="shop-detail-category">{{ product.categoryName }}</span>
            <h2 id="shop-detail-title">{{ customerProductName(product) }}</h2>
          <p class="shop-detail-price"><small>¥</small>{{ formatMoney(product.price) }}</p>
          <p v-if="loading" class="shop-detail-status" role="status">正在加载商品详情…</p>
          <div v-else-if="error" class="shop-detail-status" role="alert">
            <p>{{ error }}</p><button type="button" class="shop-text-button" @click="loadDetail(product.id)">重新加载详情</button>
          </div>
          <template v-else>
            <p v-if="product.description" class="shop-detail-description">{{ product.description }}</p>
            <div class="shop-purchase-controls">
              <label>数量<input v-model="quantity" type="number" min="1" max="9999" step="1" :disabled="purchase.locked.value" /></label>
              <button class="button primary" :disabled="purchase.locked.value" @click="choose(true)">立即购买</button>
              <button class="button secondary" :disabled="purchase.locked.value" @click="choose(false)">加入采购清单</button>
            </div>
            <p v-if="purchaseMessage" class="shop-detail-status" role="status">{{ purchaseMessage }}</p>
            <a v-if="canBrowseRelated" class="shop-solid-button" :href="categoryHref(product.categoryId)" @click="dialog.close()">浏览同类商品 <AppIcon name="arrow" /></a>
          </template>
          <button type="button" class="shop-detail-back" @click="dialog.close()">返回商品列表</button>
        </div>
      </div>
    </template>
  </dialog>
</template>
