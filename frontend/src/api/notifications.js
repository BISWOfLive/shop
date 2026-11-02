import { request, queryString } from './client.js'

// 本人通知接口：统一走集中 HTTP 客户端，写入自动携带同源 Session 与 CSRF，不另存 token。
export const getNotifications = (query, signal) =>
  request('/notifications?' + queryString(query), { signal })

export const getUnreadNotificationCount = signal =>
  request('/notifications/unread-count', { signal })

export const markNotificationRead = id =>
  request('/notifications/' + encodeURIComponent(String(id)) + '/read', { method: 'POST' })
