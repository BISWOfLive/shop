function searchParams(keyword) {
  const text = String(keyword || '').trim().slice(0, 120)
  return text ? '?' + new URLSearchParams({ q: text }).toString() : ''
}

export function categoryHref(categoryId, keyword = '') {
  return '#category/' + encodeURIComponent(String(categoryId)) + searchParams(keyword)
}

export function searchHref(keyword, categoryId = '') {
  if (categoryId) return categoryHref(categoryId, keyword)
  const params = searchParams(keyword)
  return params ? '#search' + params : '#products'
}

export function readShopRoute(hash = window.location.hash) {
  const value = hash.replace(/^#/, '')
  const separator = value.indexOf('?')
  const path = separator < 0 ? value : value.slice(0, separator)
  const params = new URLSearchParams(separator < 0 ? '' : value.slice(separator + 1))
  const keyword = (params.get('q') || '').trim().slice(0, 120)

  if (path === 'manage') return { kind: 'manage' }
  if (path === 'inventory') return { kind: 'inventory' }
  if (path === 'checkout') return { kind: 'checkout' }
  if (path === 'orders') return { kind: 'orders' }
  if (path.startsWith('orders/')) {
    let orderNo = ''
    try { orderNo = decodeURIComponent(path.slice(7)) } catch { /* 返回明确的无效订单状态 */ }
    return { kind: 'order-detail', orderNo, invalidOrder: !/^[a-f0-9]{32}$/i.test(orderNo) }
  }
  if (path.startsWith('category/')) {
    let categoryId = ''
    try {
      categoryId = decodeURIComponent(path.slice('category/'.length))
    } catch {
      return { kind: 'catalog', categoryId: '', keyword, invalidCategory: true }
    }
    return {
      kind: 'catalog', categoryId, keyword,
      invalidCategory: !/^[1-9]\d{0,18}$/.test(categoryId),
    }
  }
  if (path === 'products' || path === 'search') {
    return { kind: 'catalog', categoryId: '', keyword, invalidCategory: false }
  }
  return { kind: 'home' }
}
