import { computed, ref } from 'vue'
import { createOrder, getOrderByRequest } from '../api/orders.js'

export function createPurchaseList(api = {}) {
  const send = api.createOrder || createOrder
  const lookup = api.getOrderByRequest || getOrderByRequest
  const makeKey = api.makeKey || (() => globalThis.crypto.randomUUID())
  const items = ref([])
  const directItem = ref(null)
  const source = ref('list')
  const intent = ref(null)
  const pending = ref(false)
  const error = ref('')
  const openList = ref(false)
  const canRetry = ref(false)
  let revision = 0
  const checkoutItems = computed(() => source.value === 'direct' ? (directItem.value ? [directItem.value] : []) : items.value)
  const locked = computed(() => pending.value || intent.value?.status === 'unknown')
  const count = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))

  function editable() {
    if (locked.value) throw new Error('请先恢复本次下单结果')
  }
  function quantity(value) {
    const q = Number(value)
    if (!Number.isInteger(q) || q < 1 || q > 9999) throw new Error('购买数量必须为 1～9999 的整数')
    return q
  }
  function changed() { intent.value = null; error.value = ''; canRetry.value = false }
  function line(product, qty) {
    return { productId: String(product.id), name: product.name, imageUrl: product.imageUrl, price: String(product.price), quantity: quantity(qty), revision: ++revision }
  }
  function add(product, qty) {
    editable()
    const old = items.value.find(item => item.productId === String(product.id))
    const q = quantity(qty)
    if (old) { old.quantity = quantity(old.quantity + q); old.revision = ++revision }
    else {
      if (items.value.length >= 20) throw new Error('采购清单最多 20 种商品')
      items.value.push(line(product, q))
    }
    if (source.value === 'list') changed()
  }
  function setQuantity(id, qty) {
    editable()
    const item = items.value.find(item => item.productId === String(id))
    if (!item) return
    const next = quantity(qty)
    if (item.quantity === next) return
    item.quantity = next; item.revision = ++revision
    if (source.value === 'list') changed()
  }
  function remove(id) { editable(); items.value = items.value.filter(item => item.productId !== String(id)); if (source.value === 'list') changed() }
  function buyNow(product, qty) { editable(); directItem.value = line(product, qty); source.value = 'direct'; changed() }
  function beginCheckout() { editable(); source.value = 'list'; changed(); openList.value = false }
  function setDirectQuantity(qty) {
    editable()
    if (!directItem.value) return
    directItem.value.quantity = quantity(qty); directItem.value.revision = ++revision; changed()
  }
  function ensureIntent() {
    if (!checkoutItems.value.length) throw new Error('请先选择商品')
    if (!intent.value || intent.value.status === 'resolved') {
      const snapshot = checkoutItems.value.map(item => ({ ...item }))
      intent.value = {
        status: 'ready', source: source.value, snapshot,
        payload: { idempotencyKey: makeKey(), items: snapshot.map(item => ({ productId: item.productId, quantity: item.quantity })) },
      }
    }
    return intent.value
  }
  function complete(order, submitted) {
    if (submitted.source === 'list') {
      // 只移除本次提交时仍未被编辑的条目，保留后来加入或变更的内容。
      items.value = items.value.filter(item => !submitted.snapshot.some(old => old.productId === item.productId && old.revision === item.revision))
    } else if (directItem.value?.revision === submitted.snapshot[0]?.revision) directItem.value = null
    submitted.status = 'resolved'; error.value = ''; canRetry.value = false
    return order
  }
  async function submit() {
    if (pending.value) return null
    if (intent.value?.status === 'unknown' && !canRetry.value) { error.value = '下单结果未知，请先恢复订单'; return null }
    let submitted
    try { submitted = ensureIntent() } catch (cause) { error.value = cause.message; return null }
    pending.value = true; error.value = ''
    try { return complete(await send(submitted.payload), submitted) }
    catch (cause) {
      // 一旦出现未知结果，后续错误也不能解除原意图，必须继续按原请求号恢复。
      if (cause.uncertain || submitted.status === 'unknown') submitted.status = 'unknown'
      error.value = submitted.status === 'unknown' ? '下单结果未知，请恢复订单或使用原请求重试' : cause.message
      return null
    } finally { pending.value = false }
  }
  async function recover() {
    if (pending.value || !intent.value) return null
    pending.value = true; error.value = ''
    try { return complete(await lookup(intent.value.payload.idempotencyKey), intent.value) }
    catch (cause) {
      if (cause.status === 404) { canRetry.value = true; error.value = '暂未找到订单，可使用原请求号重试' }
      else error.value = cause.message
      return null
    } finally { pending.value = false }
  }
  return { items, source, checkoutItems, intent, pending, error, locked, openList, count, canRetry,
    add, setQuantity, remove, buyNow, beginCheckout, setDirectQuantity, submit, recover }
}
const purchaseList = createPurchaseList()
export function usePurchaseList() { return purchaseList }
