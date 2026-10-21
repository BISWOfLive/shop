<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import AppIcon from './components/AppIcon.vue'
import ProductManagement from './views/ProductManagement.vue'
import Storefront from './views/Storefront.vue'
import CatalogPage from './views/CatalogPage.vue'
import InventoryManagement from './views/InventoryManagement.vue'
import OrderCheckout from './views/OrderCheckout.vue'
import OrderList from './views/OrderList.vue'
import OrderDetail from './views/OrderDetail.vue'
import PurchaseListDialog from './components/PurchaseListDialog.vue'
import { usePurchaseList } from './composables/usePurchaseList'
import { readShopRoute } from './utils/shopRoutes'
import './trade.css'
const { openList, count } = usePurchaseList()
const route = ref(readShopRoute())
const title = computed(() => ({ manage: '商品管理', inventory: '库存管理', checkout: '结算', orders: '我的订单', 'order-detail': '订单详情' })[route.value.kind] || '商城')
function syncPage() {
  route.value = readShopRoute()
  window.scrollTo({ top: 0 })
}
window.addEventListener('hashchange', syncPage)
onBeforeUnmount(() => window.removeEventListener('hashchange', syncPage))
</script>
<template>
  <Storefront v-if="route.kind === 'home'" />
  <CatalogPage v-else-if="route.kind === 'catalog'" :route="route" />
  <div v-else class="workspace">
    <aside class="sidebar">
      <a class="brand" href="#home" aria-label="返回精简商城首页">
        <span class="brand-mark"><AppIcon name="bag" /></span><span>精简商城</span>
      </a>
      <nav aria-label="主导航">
        <a class="nav-item" :class="{ active: route.kind === 'manage' }" href="#manage" :aria-current="route.kind === 'manage' ? 'page' : undefined"><AppIcon name="grid" />商品管理</a>
        <a class="nav-item" :class="{ active: route.kind === 'inventory' }" href="#inventory" :aria-current="route.kind === 'inventory' ? 'page' : undefined"><AppIcon name="box" />库存管理</a>
        <a class="nav-item" :class="{ active: ['orders', 'order-detail'].includes(route.kind) }" href="#orders" :aria-current="route.kind === 'orders' ? 'page' : undefined"><AppIcon name="bag" />我的订单</a>
        <a class="nav-item" href="#checkout"><AppIcon name="bag" />结算</a>
        <button class="nav-item trade-nav-button" @click="openList = true">采购清单（{{ count }}）</button>
        <a class="nav-item" href="#home"><AppIcon name="home" />返回商城</a>
      </nav>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <div class="breadcrumbs"><span>精简商城</span><AppIcon name="right" /><strong>{{ title }}</strong></div>
        <a class="button secondary" href="#home"><AppIcon name="home" />浏览商城首页</a>
      </header>
      <main>
        <ProductManagement v-if="route.kind === 'manage'" />
        <InventoryManagement v-else-if="route.kind === 'inventory'" />
        <OrderCheckout v-else-if="route.kind === 'checkout'" />
        <OrderList v-else-if="route.kind === 'orders'" />
        <p v-else-if="route.invalidOrder" role="alert">订单号格式不正确</p>
        <OrderDetail v-else-if="route.kind === 'order-detail'" :order-no="route.orderNo" />
      </main>
    </div>
  </div>
  <PurchaseListDialog />
</template>
