async function request(path, options = {}) {
  let response
  try {
    response = await fetch('/api' + path, {
      ...options,
      headers: {
        Accept: 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers,
      },
    })
  } catch (error) {
    if (error.name === 'AbortError') throw error
    throw new Error('暂时无法连接服务，请稍后重试')
  }

  let result
  try {
    result = await response.json()
  } catch {
    throw new Error('服务返回异常，请稍后重试')
  }
  if (!response.ok || result.code !== 0) {
    throw new Error(result.message || '请求失败，请稍后重试')
  }
  return result.data
}

export function getCategories() {
  return request('/categories')
}

export function getProducts(query, signal) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== '' && value !== null && value !== undefined) {
      params.set(key, String(value))
    }
  }
  return request('/products?' + params.toString(), { signal })
}

export function saveProduct(id, product) {
  return request(id === null ? '/products' : '/products/' + id, {
    method: id === null ? 'POST' : 'PUT',
    body: JSON.stringify(product),
  })
}
