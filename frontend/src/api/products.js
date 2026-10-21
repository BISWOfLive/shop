import { request, queryString } from './client.js'

export function getCategories(signal) {
  return request('/categories', { signal })
}

// 小容量、短有效期详情缓存；悬停预取和点击共享同一次请求。
const productDetails = new Map()

function detailRequest(id) {
  const key = String(id)
  const cached = productDetails.get(key)
  if (cached && cached.expiresAt > Date.now()) return cached.promise
  const entry = { expiresAt: Infinity }
  entry.promise = request('/products/' + encodeURIComponent(key)).then(product => {
    entry.expiresAt = Date.now() + 15000
    return product
  }).catch(error => {
    if (productDetails.get(key) === entry) productDetails.delete(key)
    throw error
  })
  productDetails.delete(key)
  productDetails.set(key, entry)
  if (productDetails.size > 24) productDetails.delete(productDetails.keys().next().value)
  return entry.promise
}

export function getProduct(id, signal) {
  if (signal?.aborted) return Promise.reject(new DOMException('请求已取消', 'AbortError'))
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

export function getProductFeed(query, signal) {
  return request('/products/feed?' + queryString(query), { signal })
}

export async function saveProduct(id, product) {
  const saved = await request(id === null ? '/products' : '/products/' + id, {
    method: id === null ? 'POST' : 'PUT',
    body: JSON.stringify(product),
  })
  if (id !== null) productDetails.delete(String(id))
  return saved
}
