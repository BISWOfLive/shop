import { request, queryString } from './client.js'
export const getInventory = (id, signal) => request('/inventories/' + encodeURIComponent(id), { signal })
export const getInventoryLogs = (query, signal) => request('/inventory-logs?' + queryString(query), { signal })
export const stockIn = body => request('/inventories/stock-in', { method: 'POST', body: JSON.stringify(body) })
export const adjustInventory = body => request('/inventories/adjust', { method: 'POST', body: JSON.stringify(body) })
