import { request, resetCsrf } from './client.js'

export function getCsrf() {
  return request('/auth/csrf')
}

export async function login(payload) {
  const user = await request('/auth/login', { method: 'POST', body: JSON.stringify(payload) })
  // 登录成功后服务端使旧 CSRF 令牌失效，下一次写请求重新获取。
  resetCsrf()
  return user
}

export function register(payload) {
  return request('/auth/register', { method: 'POST', body: JSON.stringify(payload) })
}

export async function logout() {
  try {
    return await request('/auth/logout', { method: 'POST' })
  } finally {
    resetCsrf()
  }
}

export function getMe(signal) {
  return request('/auth/me', { signal })
}
