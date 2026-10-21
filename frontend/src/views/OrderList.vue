<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { getOrders } from '../api/orders'
import { formatMoney } from '../utils/money'
import { orderStatus, orderTime } from '../utils/orderPresentation'
const filters = reactive({ orderNo: '', status: '' })
const applied = ref({ orderNo: '', status: '' })
const page = ref(1)
const result = ref({ records: [], total: 0 })
const loading = ref(false)
const error = ref('')
let controller
async function load(next = 1, apply = false) {
  if (apply) applied.value = { ...filters }
  controller?.abort()
  const active = new AbortController()
  controller = active
  loading.value = true; error.value = ''
  try {
    const data = await getOrders({ ...applied.value, page: next, size: 10 }, active.signal)
    if (controller !== active) return
    result.value = data; page.value = next
  } catch (cause) { if (cause.name !== 'AbortError' && controller === active) error.value = cause.message }
  finally { if (controller === active) loading.value = false }
}
onMounted(() => load())
onBeforeUnmount(() => controller?.abort())
</script>
<template>
  <section class="trade-page">
    <div class="page-heading"><h1>我的订单</h1><button class="button secondary" :disabled="loading" @click="load(page)">刷新</button></div>
    <form class="trade-filters" @submit.prevent="load(1, true)">
      <label>订单号<input v-model="filters.orderNo" maxlength="32" /></label>
      <label>状态<select v-model="filters.status"><option value="">全部</option><option value="0">待支付</option><option value="1">已支付</option><option value="2">已取消</option></select></label>
      <button class="button primary" :disabled="loading">查询</button>
    </form>
    <p v-if="error" class="trade-error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">正在加载订单…</p>
    <div v-else-if="result.records.length" class="trade-panel">
      <a v-for="order in result.records" :key="order.orderNo" class="trade-order-row" :href="'#orders/' + encodeURIComponent(order.orderNo)">
        <div><strong class="trade-order-no">{{ order.orderNo }}</strong><span>{{ orderTime(order.createdAt) }}</span></div>
        <span>{{ orderStatus(order.status) }}</span><strong>¥{{ formatMoney(order.totalAmount) }}</strong><span>查看订单</span>
      </a>
    </div>
    <p v-else-if="!error" class="trade-empty">暂无订单</p>
    <div class="trade-pagination">
      <span>共 {{ result.total }} 笔 · 第 {{ page }} 页</span>
      <button class="button secondary" :disabled="loading || page <= 1" @click="load(page - 1)">上一页</button>
      <button class="button secondary" :disabled="loading || page * 10 >= result.total" @click="load(page + 1)">下一页</button>
    </div>
  </section>
</template>
