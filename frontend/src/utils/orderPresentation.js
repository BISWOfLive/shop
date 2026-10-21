export const orderStatus = status => ({ 0: '待支付', 1: '已支付', 2: '已取消' })[status] || '未知状态'
export function orderTime(value) {
  return value ? String(value).replace('T', ' ').replace(/(?:\.\d+)?\+08:00$/, '') : '—'
}
