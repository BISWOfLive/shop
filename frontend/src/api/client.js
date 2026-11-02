export class ApiError extends Error {
  constructor(message, status = 0, uncertain = false) {
    super(message)
    this.status = status
    this.uncertain = uncertain
  }
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE'])
// CSRF 令牌只保存在当前页面进程内，不写入 Cookie、localStorage 或日志。
let csrf = null
let csrfRequest = null
const unauthorizedListeners = new Set()

export function onUnauthorized(listener) {
  unauthorizedListeners.add(listener)
  return () => unauthorizedListeners.delete(listener)
}

function notifyUnauthorized() {
  for (const listener of unauthorizedListeners) {
    try { listener() } catch { /* 单个监听器失败不影响请求结果 */ }
  }
}

export function resetCsrf() { csrf = null }

async function fetchCsrf() {
  if (csrf) return csrf
  if (!csrfRequest) {
    csrfRequest = (async () => {
      let response
      try {
        response = await fetch('/api/auth/csrf', {
          credentials: 'same-origin',
          headers: { Accept: 'application/json' },
        })
      } catch {
        throw new ApiError('暂时无法连接服务，请稍后重试', 0, true)
      }
      let result
      try { result = await response.json() }
      catch { throw new ApiError('服务返回异常，请稍后重试', response.status, true) }
      if (!response.ok || result.code !== 0) {
        throw new ApiError(result.message || '无法获取请求令牌', response.status, false)
      }
      csrf = { headerName: result.data.headerName, token: result.data.token }
      return csrf
    })().finally(() => { csrfRequest = null })
  }
  return csrfRequest
}

export async function request(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase()
  let headers = {
    Accept: 'application/json',
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...options.headers,
  }
  if (!SAFE_METHODS.has(method)) {
    const token = await fetchCsrf()
    headers = { ...headers, [token.headerName]: token.token }
  }
  let response
  try {
    response = await fetch('/api' + path, { ...options, method, credentials: 'same-origin', headers })
  } catch (cause) {
    if (cause.name === 'AbortError') throw cause
    throw new ApiError('暂时无法连接服务，请稍后重试', 0, true)
  }
  let result
  try { result = await response.json() }
  catch (cause) {
    if (cause.name === 'AbortError') throw cause
    throw new ApiError('服务返回异常，请稍后重试', response.status, true)
  }
  if (!response.ok || result.code !== 0) {
    if (response.status === 401) {
      resetCsrf()
      notifyUnauthorized()
    }
    // 403 可能来自已轮换的 CSRF 令牌：只更新令牌与提示，不自动重放写请求。
    if (response.status === 403) resetCsrf()
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
