import { request, queryString } from './client.js'
export const createOrder = body => request('/orders', { method: 'POST', body: JSON.stringify(body) })
export const getOrders = (query, signal) => request('/orders?' + queryString(query), { signal })
export const getOrder = (no, signal) => request('/orders/' + encodeURIComponent(no), { signal })
export const getOrderByRequest = key => request('/orders/by-request?' + new URLSearchParams({ idempotencyKey: key }))
export const cancelOrder = no => request('/orders/' + encodeURIComponent(no) + '/cancel', { method: 'POST' })
export const mockPayOrder = no => request('/orders/' + encodeURIComponent(no) + '/mock-pay', { method: 'POST' })
