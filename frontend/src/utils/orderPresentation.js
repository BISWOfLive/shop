export const orderStatus = status => ({ 0: '待支付', 1: '已支付', 2: '已取消' })[status] || '未知状态'
export const orderCancelReason = reason => ({ USER: '主动取消', TIMEOUT: '到期取消' })[reason] || '未知原因'
export function orderTime(value) {
  return value ? String(value).replace('T', ' ').replace(/(?:\.\d+)?\+08:00$/, '') : '—'
}
