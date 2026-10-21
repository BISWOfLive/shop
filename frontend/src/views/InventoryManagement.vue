<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { getInventory, getInventoryLogs, stockIn, adjustInventory } from '../api/inventory'
const filters = reactive({ productId: '', orderId: '', changeType: '', bizNo: '' })
const productId = ref('')
const inventory = ref(null)
const rows = ref({ records: [], total: 0 })
const page = ref(1)
const loading = ref(false)
const logLoading = ref(false)
const saving = ref(false)
const error = ref('')
const logError = ref('')
const saveError = ref('')
const saved = ref('')
const form = reactive({ mode: 'stock-in', quantity: '', bizNo: crypto.randomUUID(), remark: '' })
const applied = ref({ ...filters })
let stockSequence = 0
let logController
const names = { 1: '入库', 2: '下单扣减', 3: '取消归还', 4: '人工调整' }
async function queryStock() {
  const active = ++stockSequence
  inventory.value = null; error.value = ''; saved.value = ''
  if (!/^[1-9]\d{0,18}$/.test(productId.value)) { error.value = '请输入有效商品 ID'; return }
  loading.value = true
  try { const data = await getInventory(productId.value); if (active === stockSequence) inventory.value = data }
  catch (cause) { if (active === stockSequence) error.value = cause.message }
  finally { if (active === stockSequence) loading.value = false }
}
async function loadLogs(next = 1, apply = false) {
  if (apply) applied.value = { ...filters }
  logController?.abort()
  const active = new AbortController(); logController = active
  logLoading.value = true; logError.value = ''
  try {
    const data = await getInventoryLogs({ ...applied.value, page: next, size: 10 }, active.signal)
    if (logController === active) { rows.value = data; page.value = next }
  } catch (cause) { if (cause.name !== 'AbortError' && logController === active) logError.value = cause.message }
  finally { if (logController === active) logLoading.value = false }
}
async function save() {
  if (saving.value || !inventory.value) return
  const quantity = Number(form.quantity)
  if (!Number.isInteger(quantity) || quantity === 0 || quantity < -2147483648 || quantity > 2147483647 || (form.mode === 'stock-in' && quantity < 1)) {
    saveError.value = '请输入范围内的非零整数，入库必须为正数'; return
  }
  saving.value = true; saveError.value = ''; saved.value = ''
  const id = inventory.value.productId
  try {
    const payload = { productId: id, bizNo: form.bizNo, remark: form.remark }
    if (form.mode === 'stock-in') { payload.quantity = quantity; await stockIn(payload) }
    else { payload.changeQty = quantity; await adjustInventory(payload) }
    form.bizNo = crypto.randomUUID(); form.quantity = ''; form.remark = ''
    saved.value = '库存变更成功'
    const data = await getInventory(id)
    if (inventory.value?.productId === id) inventory.value = data
    filters.productId = id; await loadLogs(1, true)
  } catch (cause) { saveError.value = cause.message }
  finally { saving.value = false }
}
onBeforeUnmount(() => { stockSequence++; logController?.abort() })
</script>
<template>
  <section class="trade-page">
    <div class="page-heading"><h1>库存管理</h1></div>
    <form class="trade-filters" @submit.prevent="queryStock">
      <label>商品 ID<input v-model="productId" inputmode="numeric" :disabled="saving" required /></label>
      <button class="button primary" :disabled="loading || saving">{{ loading ? '查询中…' : '查询库存' }}</button>
    </form>
    <p v-if="error" class="trade-error" role="alert">{{ error }}</p>
    <div v-if="inventory" class="trade-panel">
      <div class="trade-heading"><h2>{{ inventory.productName }}</h2><strong>可售库存 {{ inventory.availableQty }}</strong></div>
      <form class="trade-filters" @submit.prevent="save">
        <label>操作<select v-model="form.mode" :disabled="saving"><option value="stock-in">入库</option><option value="adjust">人工调整</option></select></label>
        <label>{{ form.mode === 'stock-in' ? '入库数量' : '变更数量' }}<input v-model="form.quantity" type="number" step="1" :min="form.mode === 'stock-in' ? 1 : -2147483648" max="2147483647" :disabled="saving" required /></label>
        <label>业务号<input v-model="form.bizNo" maxlength="64" pattern="[!-~]+" :disabled="saving" required /></label>
        <label>备注<input v-model="form.remark" maxlength="255" :disabled="saving" /></label>
        <button class="button primary" :disabled="saving">{{ saving ? '保存中…' : '保存变更' }}</button>
      </form>
      <p v-if="saveError" class="trade-error" role="alert">{{ saveError }}</p><p v-if="saved" role="status">{{ saved }}</p>
    </div>
    <div class="trade-heading trade-section"><h2>库存流水</h2></div>
    <form class="trade-filters" @submit.prevent="loadLogs(1, true)">
      <label>商品 ID<input v-model="filters.productId" inputmode="numeric" /></label>
      <label>订单 ID<input v-model="filters.orderId" inputmode="numeric" /></label>
      <label>类型<select v-model="filters.changeType"><option value="">全部</option><option v-for="(name, code) in names" :key="code" :value="code">{{ name }}</option></select></label>
      <label>业务号<input v-model="filters.bizNo" maxlength="64" /></label>
      <button class="button secondary" :disabled="logLoading">查询流水</button>
    </form>
    <p v-if="logError" class="trade-error" role="alert">{{ logError }}</p>
    <p v-if="logLoading" role="status">正在加载流水…</p>
    <div v-else-if="rows.records.length" class="trade-table-wrap">
      <table class="trade-table"><thead><tr><th>商品</th><th>类型</th><th>变更前</th><th>变更量</th><th>变更后</th><th>业务号</th><th>备注</th></tr></thead>
        <tbody><tr v-for="log in rows.records" :key="log.id"><td>{{ log.productName || log.productId }}</td><td>{{ names[log.changeType] }}</td><td>{{ log.beforeQty }}</td><td>{{ log.changeQty }}</td><td>{{ log.afterQty }}</td><td>{{ log.bizNo }}</td><td>{{ log.remark }}</td></tr></tbody>
      </table>
    </div>
    <p v-else class="trade-empty">暂无流水</p>
    <div class="trade-pagination"><span>共 {{ rows.total }} 条 · 第 {{ page }} 页</span>
      <button class="button secondary" :disabled="logLoading || page <= 1" @click="loadLogs(page - 1)">上一页</button>
      <button class="button secondary" :disabled="logLoading || page * 10 >= rows.total" @click="loadLogs(page + 1)">下一页</button>
    </div>
  </section>
</template>
