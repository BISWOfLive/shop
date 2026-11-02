<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from './components/AppIcon.vue'
import ProductManagement from './views/ProductManagement.vue'
import Storefront from './views/Storefront.vue'
import CatalogPage from './views/CatalogPage.vue'
import InventoryManagement from './views/InventoryManagement.vue'
import OrderCheckout from './views/OrderCheckout.vue'
import OrderList from './views/OrderList.vue'
import OrderDetail from './views/OrderDetail.vue'
import Login from './views/Login.vue'
import Register from './views/Register.vue'
import AccountProfile from './views/AccountProfile.vue'
import UserManagement from './views/UserManagement.vue'
import NotificationList from './views/NotificationList.vue'
import PurchaseListDialog from './components/PurchaseListDialog.vue'
import { usePurchaseList } from './composables/usePurchaseList'
import { useAuth } from './composables/useAuth'
import { useNotifications } from './composables/useNotifications'
import { readShopRoute, safeInternalHash } from './utils/shopRoutes'
import './trade.css'

const { openList, count } = usePurchaseList()
const { user, ready, isAdmin, notificationsEnabled, loadMe, logout } = useAuth()
const { unreadCount, refreshUnreadCount } = useNotifications()
const route = ref(readShopRoute())
const loginNotice = ref('')
const intendedHash = ref('#home')

const ADMIN_ROUTES = new Set(['manage', 'inventory', 'users'])
const MEMBER_ROUTES = new Set(['orders', 'order-detail', 'checkout', 'account'])
const TITLES = {
  manage: '商品管理', inventory: '库存管理', users: '用户管理', checkout: '结算', orders: '我的订单',
  'order-detail': '订单详情', login: '登录', register: '注册', account: '账户', notifications: '通知',
}

const title = computed(() => TITLES[route.value.kind] || '商城')
const view = computed(() => {
  if (!ready.value) return 'loading'
  const kind = route.value.kind
  if (kind === 'login') return user.value ? 'home' : 'login'
  if (kind === 'register') return user.value ? 'home' : 'register'
  if (kind === 'notifications') {
    if (!user.value) return 'login'
    // 功能关闭时直接进入 hash 显示明确的模块不可用，不用空列表伪装正常。
    return notificationsEnabled.value ? 'notifications' : 'notifications-unavailable'
  }
  if (ADMIN_ROUTES.has(kind)) {
    if (!user.value) return 'login'
    return isAdmin.value ? kind : 'forbidden'
  }
  if (MEMBER_ROUTES.has(kind)) return user.value ? kind : 'login'
  return kind
})

watch([view, user, ready], () => {
  if (view.value === 'login') {
    const hash = window.location.hash
    if (hash && hash !== '#login' && hash !== '#register') intendedHash.value = hash
  }
  // 已登录访问登录/注册页时回到商城首页，避免停留在无意义的身份页面。
  if (ready.value && user.value && (route.value.kind === 'login' || route.value.kind === 'register')) {
    window.location.hash = '#home'
  }
})

function syncPage() {
  route.value = readShopRoute()
  window.scrollTo({ top: 0 })
}

// 登录后读取一次本人未读数；退出与账号切换由通知状态自身的身份代次清理。
watch([user, notificationsEnabled], ([nextUser, enabled]) => {
  if (nextUser && enabled) refreshUnreadCount()
})

function afterLogin() {
  loginNotice.value = ''
  const target = safeInternalHash(intendedHash.value)
  window.location.hash = target && !['#login', '#register'].includes(target) ? target : '#home'
}

function afterRegister() {
  loginNotice.value = '注册成功，请登录'
  window.location.hash = '#login'
}

function goRegister() { window.location.hash = '#register' }
function goLogin() { window.location.hash = '#login' }

async function handleLogout() {
  try {
    await logout()
  } finally {
    loginNotice.value = ''
    window.location.hash = '#home'
  }
}

onMounted(() => {
  window.addEventListener('hashchange', syncPage)
  loadMe()
})
onBeforeUnmount(() => window.removeEventListener('hashchange', syncPage))
</script>

<template>
  <Storefront v-if="view === 'home'" />
  <CatalogPage v-else-if="view === 'catalog'" :route="route" />
  <section v-else-if="view === 'loading'" class="trade-page" role="status">
    <p>正在确认登录状态…</p>
  </section>
  <div v-else class="workspace">
    <aside class="sidebar">
      <a class="brand" href="#home" aria-label="返回精简商城首页">
        <span class="brand-mark"><AppIcon name="bag" /></span><span>精简商城</span>
      </a>
      <nav aria-label="主导航">
        <a v-if="isAdmin" class="nav-item" :class="{ active: view === 'manage' }" href="#manage"
           :aria-current="view === 'manage' ? 'page' : undefined"><AppIcon name="grid" />商品管理</a>
        <a v-if="isAdmin" class="nav-item" :class="{ active: view === 'inventory' }" href="#inventory"
           :aria-current="view === 'inventory' ? 'page' : undefined"><AppIcon name="box" />库存管理</a>
        <a v-if="isAdmin" class="nav-item" :class="{ active: view === 'users' }" href="#users"
           :aria-current="view === 'users' ? 'page' : undefined"><AppIcon name="user" />用户管理</a>
        <a class="nav-item" :class="{ active: ['orders', 'order-detail'].includes(view) }" href="#orders"
           :aria-current="view === 'orders' ? 'page' : undefined"><AppIcon name="bag" />我的订单</a>
        <a v-if="notificationsEnabled" class="nav-item" :class="{ active: view === 'notifications' }"
           href="#notifications"
           :aria-current="view === 'notifications' ? 'page' : undefined"><AppIcon name="bell" />通知
          <span v-if="unreadCount !== null && unreadCount > 0" class="nav-badge">{{ unreadCount }}</span>
        </a>
        <a class="nav-item" :class="{ active: view === 'checkout' }" href="#checkout"><AppIcon name="bag" />结算</a>
        <button class="nav-item trade-nav-button" type="button" @click="openList = true">采购清单（{{ count }}）</button>
        <a class="nav-item" :class="{ active: view === 'account' }" :href="user ? '#account' : '#login'">
          <AppIcon name="user" />{{ user ? '账户' : '登录' }}
        </a>
        <a class="nav-item" href="#home"><AppIcon name="home" />返回商城</a>
        <button v-if="user" class="nav-item trade-nav-button" type="button" @click="handleLogout">退出登录</button>
      </nav>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <div class="breadcrumbs"><span>精简商城</span><AppIcon name="right" /><strong>{{ title }}</strong></div>
        <div class="topbar-actions">
          <template v-if="user">
            <a class="button secondary" href="#account">{{ user.nickname || user.username }}</a>
            <button class="button secondary" type="button" @click="handleLogout">退出</button>
          </template>
          <template v-else>
            <a class="button secondary" href="#login">登录</a>
            <a class="button secondary" href="#register">注册</a>
          </template>
          <a class="button secondary" href="#home"><AppIcon name="home" />浏览商城首页</a>
        </div>
      </header>
      <main>
        <Login v-if="view === 'login'" :notice="loginNotice" @logged-in="afterLogin" @go-register="goRegister" />
        <Register v-else-if="view === 'register'" @registered="afterRegister" @go-login="goLogin" />
        <AccountProfile v-else-if="view === 'account'" />
        <UserManagement v-else-if="view === 'users'" />
        <section v-else-if="view === 'forbidden'" class="trade-page">
          <div class="page-heading"><h1>没有权限</h1><a class="button secondary" href="#home">返回商城首页</a></div>
          <p class="trade-error" role="alert">当前账号无权访问该页面。</p>
        </section>
        <section v-else-if="view === 'notifications-unavailable'" class="trade-page">
          <div class="trade-heading"><h2>通知</h2><a class="button secondary" href="#home">返回商城首页</a></div>
          <p class="trade-error" role="alert">通知模块不可用。</p>
        </section>
        <ProductManagement v-else-if="view === 'manage'" />
        <InventoryManagement v-else-if="view === 'inventory'" />
        <OrderCheckout v-else-if="view === 'checkout'" />
        <NotificationList v-else-if="view === 'notifications'" />
        <OrderList v-else-if="view === 'orders'" />
        <p v-else-if="route.invalidOrder" role="alert">订单号格式不正确</p>
        <OrderDetail v-else-if="view === 'order-detail'" :order-no="route.orderNo" />
      </main>
    </div>
  </div>
  <PurchaseListDialog />
</template>
