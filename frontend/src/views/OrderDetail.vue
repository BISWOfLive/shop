<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { getOrder, cancelOrder, mockPayOrder } from '../api/orders'
import { formatMoney } from '../utils/money'
import { orderStatus, orderTime } from '../utils/orderPresentation'
const props = defineProps({ orderNo: { type: String, required: true } })
const order = ref(null)
const loading = ref(false)
const pending = ref('')
const error = ref('')
let sequence = 0
async function load(clearError = true) {
  const active = ++sequence
  const no = props.orderNo
  loading.value = true
  if (clearError) error.value = ''
  try { const result = await getOrder(no); if (active === sequence) order.value = result }
  catch (cause) { if (active === sequence) error.value = cause.message }
  finally { if (active === sequence) loading.value = false }
}
async function act(kind) {
  if (pending.value || loading.value) return
  pending.value = kind; error.value = ''
  const no = props.orderNo
  try {
    const result = await (kind === 'pay' ? mockPayOrder(no) : cancelOrder(no))
    if (props.orderNo === no) order.value = result
  } catch (cause) {
    if (props.orderNo === no) { error.value = cause.message; await load(false) }
  } finally { pending.value = '' }
}
watch(() => props.orderNo, () => { order.value = null; load() }, { immediate: true })
onBeforeUnmount(() => { sequence++ })
</script>
<template>
  <section class="trade-page">
    <div class="page-heading"><h1>订单详情</h1><a class="button secondary" href="#orders">我的订单</a></div>
    <p v-if="error" class="trade-error" role="alert">{{ error }}</p>
    <div class="trade-actions"><button class="button secondary" :disabled="loading || !!pending" @click="load()">刷新订单</button></div>
    <p v-if="loading" role="status">正在加载订单…</p>
    <template v-if="order">
      <div class="trade-panel">
        <div class="trade-heading"><strong class="trade-order-no">{{ order.orderNo }}</strong><strong class="trade-state">{{ orderStatus(order.status) }}</strong></div>
        <dl class="trade-facts">
          <div><dt>创建时间</dt><dd>{{ orderTime(order.createdAt) }}</dd></div>
          <div><dt>支付截止</dt><dd>{{ orderTime(order.expiresAt) }}</dd></div>
          <div v-if="order.paidAt"><dt>支付时间</dt><dd>{{ orderTime(order.paidAt) }}</dd></div>
          <div v-if="order.cancelledAt"><dt>取消时间</dt><dd>{{ orderTime(order.cancelledAt) }}</dd></div>
          <div v-if="order.cancelReason"><dt>取消原因</dt><dd>{{ order.cancelReason === 'USER' ? '主动取消' : order.cancelReason }}</dd></div>
        </dl>
        <div v-for="item in order.items" :key="item.id" class="trade-line">
          <img v-if="item.productImageUrl" :src="item.productImageUrl" :alt="item.productName" />
          <div class="trade-line-name"><strong>{{ item.productName }}</strong><span>¥{{ formatMoney(item.unitPrice) }} × {{ item.quantity }}</span></div>
          <strong>¥{{ formatMoney(item.lineAmount) }}</strong>
        </div>
        <div class="trade-total"><span>订单总额</span><strong>¥{{ formatMoney(order.totalAmount) }}</strong></div>
        <div v-if="order.status === 0" class="trade-actions">
          <button class="button secondary" :disabled="loading || !!pending" @click="act('cancel')">{{ pending === 'cancel' ? '取消中…' : '取消订单' }}</button>
          <button class="button primary" :disabled="loading || !!pending" @click="act('pay')">{{ pending === 'pay' ? '支付中…' : '模拟支付' }}</button>
        </div>
      </div>
    </template>
  </section>
</template>
