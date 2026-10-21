<script setup>
import { usePurchaseList } from '../composables/usePurchaseList'
import { formatMoney } from '../utils/money'
const { checkoutItems, source, intent, pending, error, locked, canRetry,
  setQuantity, setDirectQuantity, submit, recover } = usePurchaseList()
function edit(item, event) {
  try {
    if (source.value === 'direct') setDirectQuantity(event.target.value)
    else setQuantity(item.productId, event.target.value)
  } catch (cause) { error.value = cause.message; event.target.value = item.quantity }
}
async function act(action) {
  const order = await action()
  if (order) window.location.hash = '#orders/' + encodeURIComponent(order.orderNo)
}
</script>
<template>
  <section class="trade-page">
    <div class="page-heading"><h1>结算</h1><a class="button secondary" href="#home">继续选购</a></div>
    <p v-if="error" class="trade-error" role="alert">{{ error }}</p>
    <div v-if="checkoutItems.length" class="trade-panel">
      <div v-for="item in checkoutItems" :key="item.productId" class="trade-line">
        <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.name" />
        <div class="trade-line-name"><strong>{{ item.name }}</strong><span>当前单价 ¥{{ formatMoney(item.price) }}</span></div>
        <label>数量<input type="number" min="1" max="9999" step="1" :value="item.quantity" :disabled="locked" @change="edit(item, $event)" /></label>
      </div>
      <div class="trade-actions">
        <template v-if="intent?.status === 'unknown'">
          <button class="button secondary" :disabled="pending" @click="act(recover)">恢复订单</button>
          <button class="button primary" :disabled="pending || !canRetry" @click="act(submit)">使用原请求重试</button>
        </template>
        <button v-else class="button primary" :disabled="pending" @click="act(submit)">{{ pending ? '提交中…' : '提交订单' }}</button>
      </div>
    </div>
    <p v-else class="trade-empty">没有待结算商品</p>
  </section>
</template>
