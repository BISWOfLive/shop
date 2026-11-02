import { request, queryString } from './client.js'

export function getCategories(signal) {
  return request('/categories', { signal })
}

// 最多保留 24 个在途请求；悬停预取和点击共享请求，完成后不缓存结果。
const productDetails = new Map()

function detailRequest(id) {
  const key = String(id)
  const pending = productDetails.get(key)
  if (pending) return pending.promise
  const entry = {}
  entry.promise = request('/products/' + encodeURIComponent(key)).finally(() => {
    if (productDetails.get(key) === entry) productDetails.delete(key)
  })
  productDetails.delete(key)
  productDetails.set(key, entry)
  if (productDetails.size > 24) productDetails.delete(productDetails.keys().next().value)
  return entry.promise
}

export function getProduct(id, signal, { fresh = false } = {}) {
  if (signal?.aborted) return Promise.reject(new DOMException('请求已取消', 'AbortError'))
  if (fresh) return request('/products/' + encodeURIComponent(String(id)) + '/edit', { signal, cache: 'no-store' })
  const pending = detailRequest(id)
  if (!signal) return pending
  return new Promise((resolve, reject) => {
    const abort = () => reject(new DOMException('请求已取消', 'AbortError'))
    signal.addEventListener('abort', abort, { once: true })
    pending.then(resolve, reject).finally(() => signal.removeEventListener('abort', abort))
  })
}

export function prefetchProduct(id) {
  if (globalThis.navigator?.connection?.saveData) return
  detailRequest(id).catch(() => {})
}

export function getProducts(query, signal) {
  return request('/products?' + queryString(query), { signal })
}

// 管理端列表允许按上下架状态筛选；公开列表始终由服务端强制上架范围。
export function getAdminProducts(query, signal) {
  return request('/admin/products?' + queryString(query), { signal })
}

export function getProductFeed(query, signal) {
  return request('/products/feed?' + queryString(query), { signal })
}

export async function saveProduct(id, product) {
  const saved = await request(id === null ? '/products' : '/products/' + encodeURIComponent(String(id)), {
    method: id === null ? 'POST' : 'PUT',
    body: JSON.stringify(product),
  })
  productDetails.delete(String(saved.id ?? id))
  return saved
}
