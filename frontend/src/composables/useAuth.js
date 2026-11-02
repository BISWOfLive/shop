import { computed, ref } from 'vue'
import { getMe, login as loginRequest, logout as logoutRequest } from '../api/auth.js'
import { onUnauthorized } from '../api/client.js'
import { usePurchaseList } from './usePurchaseList.js'

const user = ref(null)
const ready = ref(false)
// 身份代次：账号登录、退出或会话失效都会推进，页面据此丢弃切换前未完成的请求结果。
const identityEpoch = ref(0)
const purchaseList = usePurchaseList()
// 最近一次已知账号 ID：会话到期后仍保留，只有重新登录同一账号才恢复原未知下单意图。
let lastUserId = null
let listenerBound = false

function currentId(value) {
  return value?.id == null ? null : String(value.id)
}

function bindUnauthorizedListener() {
  if (listenerBound) return
  listenerBound = true
  onUnauthorized(() => {
    // 会话失效不等于退出：保留 lastUserId，供同一账号重新登录后恢复原意图。
    if (user.value !== null) {
      user.value = null
      identityEpoch.value += 1
    }
  })
}
bindUnauthorizedListener()

function setUser(next) {
  const nextId = currentId(next)
  const previous = lastUserId
  const changed = previous !== nextId
  if (changed) identityEpoch.value += 1
  lastUserId = nextId
  user.value = next
  if (changed) purchaseList.onIdentityChanged(previous, nextId)
}

async function loadMe() {
  try {
    const me = await getMe()
    if (me) setUser(me)
    else if (user.value !== null) { user.value = null; identityEpoch.value += 1 }
  } catch (error) {
    if (error.status === 401) {
      if (user.value !== null) { user.value = null; identityEpoch.value += 1 }
    } else if (user.value !== null) {
      user.value = null
      identityEpoch.value += 1
    }
  } finally {
    ready.value = true
  }
}

async function login(payload) {
  const loggedIn = await loginRequest(payload)
  setUser(loggedIn)
  ready.value = true
  return loggedIn
}

async function logout() {
  try {
    await logoutRequest()
  } finally {
    const previous = lastUserId
    user.value = null
    lastUserId = null
    identityEpoch.value += 1
    purchaseList.onIdentityChanged(previous, null)
  }
}

const isAdmin = computed(() => (user.value?.roles || []).some(role => role.code === 'ADMIN'))
const isLoggedIn = computed(() => user.value !== null)
// 后端能力提示只控制入口展示；每次通知请求仍由服务端按当前身份授权。
const notificationsEnabled = computed(() => user.value?.features?.notifications === true)

export function useAuth() {
  return {
    user, ready, identityEpoch, isAdmin, isLoggedIn, notificationsEnabled,
    loadMe, login, logout,
  }
}
