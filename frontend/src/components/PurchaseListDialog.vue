<script setup>
import { nextTick, ref, watch } from 'vue'
import { usePurchaseList } from '../composables/usePurchaseList'
import { formatMoney } from '../utils/money'
const { items, openList, locked, setQuantity, remove, beginCheckout } = usePurchaseList()
const dialog = ref(null)
const error = ref('')
watch(openList, async open => {
  await nextTick()
  if (open && !dialog.value.open) dialog.value.showModal()
  else if (!open && dialog.value.open) dialog.value.close()
})
function edit(item, event) {
  try { setQuantity(item.productId, event.target.value); error.value = '' }
  catch (cause) { error.value = cause.message; event.target.value = item.quantity }
}
function checkout() {
  if (!locked.value) beginCheckout()
  openList.value = false
  window.location.hash = '#checkout'
}
</script>
<template>
  <dialog ref="dialog" class="purchase-dialog" aria-labelledby="purchase-title" @close="openList = false"
          @click="event => { if (event.target === event.currentTarget) openList = false }">
    <div class="trade-heading"><h2 id="purchase-title">采购清单</h2><button class="button secondary" @click="openList = false">关闭</button></div>
    <p v-if="error" role="alert" class="trade-error">{{ error }}</p>
    <p v-if="!items.length" class="trade-empty">采购清单为空</p>
    <div v-for="item in items" :key="item.productId" class="trade-line">
      <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.name" />
      <div class="trade-line-name"><strong>{{ item.name }}</strong><span>¥{{ formatMoney(item.price) }}</span></div>
      <label>数量<input type="number" min="1" max="9999" step="1" :value="item.quantity" :disabled="locked" @change="edit(item, $event)" /></label>
      <button class="button secondary" :disabled="locked" @click="remove(item.productId)">移除</button>
    </div>
    <div class="trade-actions"><button class="button primary" :disabled="!items.length" @click="checkout">{{ locked ? '返回本次结算' : '结算' }}</button></div>
  </dialog>
</template>
