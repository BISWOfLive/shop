export class ApiError extends Error {
  constructor(message, status = 0, uncertain = false) {
    super(message)
    this.status = status
    this.uncertain = uncertain
  }
}
export async function request(path, options = {}) {
  let response
  try {
    response = await fetch('/api' + path, {
      ...options,
      headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers },
    })
  } catch (cause) {
    if (cause.name === 'AbortError') throw cause
    throw new ApiError('暂时无法连接服务，请稍后重试', 0, true)
  }
  let result
  try { result = await response.json() }
  catch { throw new ApiError('服务返回异常，请稍后重试', response.status, true) }
  if (!response.ok || result.code !== 0) {
    throw new ApiError(result.message || '请求失败，请稍后重试', response.status, response.status >= 500)
  }
  return result.data
}
export function queryString(query) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== '' && value !== null && value !== undefined) params.set(key, String(value))
  }
  return params.toString()
}
