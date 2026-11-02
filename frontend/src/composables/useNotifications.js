import { computed, ref, watch } from 'vue'
import {
  getNotifications,
  getUnreadNotificationCount,
  markNotificationRead,
} from '../api/notifications.js'
import { useAuth } from './useAuth.js'

// 通知列表、筛选、分页与未读数都是当前登录账号的派生状态，不写入 localStorage。
const notifications = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
// null：全部，true：未读，false：已读。
const unread = ref(null)
// 未读数未知时为 null，界面不把“服务不可用”显示成 0 条通知。
const unreadCount = ref(null)
const loading = ref(false)
const error = ref('')

let listSequence = 0
let countSequence = 0
const { identityEpoch } = useAuth()

watch(identityEpoch, () => reset())

function clearData() {
  listSequence += 1
  countSequence += 1
  notifications.value = []
  total.value = 0
  page.value = 1
  unread.value = null
  unreadCount.value = null
  loading.value = false
  error.value = ''
}

async function refresh(targetPage = page.value) {
  const epoch = identityEpoch.value
  const sequence = ++listSequence
  loading.value = true
  error.value = ''
  try {
    const query = { page: targetPage, size: size.value }
    if (unread.value !== null) query.unread = unread.value
    const result = await getNotifications(query)
    if (sequence !== listSequence || epoch !== identityEpoch.value) return
    notifications.value = Array.isArray(result?.records) ? result.records : []
    const nextTotal = Number(result?.total)
    total.value = Number.isFinite(nextTotal) && nextTotal >= 0 ? nextTotal : 0
    const nextPage = Number(result?.page)
    page.value = Number.isFinite(nextPage) && nextPage >= 1 ? nextPage : targetPage
  } catch (failure) {
    if (sequence !== listSequence || epoch !== identityEpoch.value) return
    notifications.value = []
    total.value = 0
    // 本人查询失败不能改成“没有通知”。
    error.value = failure?.message || '通知加载失败，请稍后重试'
  } finally {
    if (sequence === listSequence) loading.value = false
  }
}

async function refreshUnreadCount() {
  const epoch = identityEpoch.value
  const sequence = ++countSequence
  try {
    const result = await getUnreadNotificationCount()
    if (sequence !== countSequence || epoch !== identityEpoch.value) return
    const value = Number(result?.count)
    if (Number.isInteger(value) && value >= 0) unreadCount.value = value
  } catch (failure) {
    // 未读数失败不伪装成 0，也不覆盖列表已经展示的错误。
    if (epoch !== identityEpoch.value) return
  }
}

async function markRead(id) {
  const epoch = identityEpoch.value
  error.value = ''
  try {
    const updated = await markNotificationRead(id)
    if (epoch !== identityEpoch.value) return
    const index = notifications.value.findIndex(item => String(item.id) === String(id))
    if (index < 0) return
    const previous = notifications.value[index]
    notifications.value[index] = { ...previous, ...(updated || {}) }
    // 只有真正从“未读”变为“已读”才减少一次计数；失败时不先减计数。
    if (!previous.readAt && unreadCount.value !== null && unreadCount.value > 0) {
      unreadCount.value -= 1
    }
  } catch (failure) {
    if (epoch !== identityEpoch.value) return
    error.value = failure?.message || '标记已读失败，请重试'
  }
}

async function setUnreadFilter(value) {
  unread.value = value
  page.value = 1
  await refresh(1)
}

async function goPage(targetPage) {
  if (targetPage < 1 || targetPage > pages.value) return
  await refresh(targetPage)
}

async function refreshAll() {
  await Promise.all([refresh(page.value), refreshUnreadCount()])
}

function reset() {
  clearData()
}

const pages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

export function useNotifications() {
  return {
    notifications,
    total,
    page,
    size,
    pages,
    unread,
    unreadCount,
    loading,
    error,
    refresh,
    refreshAll,
    refreshUnreadCount,
    markRead,
    setUnreadFilter,
    goPage,
    reset,
  }
}
