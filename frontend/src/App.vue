<script setup>
import { onBeforeUnmount, ref } from 'vue'
import AppIcon from './components/AppIcon.vue'
import ProductManagement from './views/ProductManagement.vue'
import Storefront from './views/Storefront.vue'

const managing = ref(window.location.hash === '#manage')
function syncPage() {
  managing.value = window.location.hash === '#manage'
  window.scrollTo({ top: 0 })
}
window.addEventListener('hashchange', syncPage)
onBeforeUnmount(() => window.removeEventListener('hashchange', syncPage))
</script>

<template>
  <Storefront v-if="!managing" />
  <div v-else class="workspace">
    <aside class="sidebar">
      <a class="brand" href="#home" aria-label="返回精简商城首页">
        <span class="brand-mark"><AppIcon name="bag" /></span>
        <span>精简商城<small>商品管理中心</small></span>
      </a>
      <nav aria-label="主导航">
        <p class="nav-label">商品运营</p>
        <a class="nav-item active" href="#manage" aria-current="page">
          <AppIcon name="grid" />商品管理
        </a>
        <a class="nav-item" href="#home"><AppIcon name="home" />返回商城</a>
      </nav>
      <div class="sidebar-footer">
        <AppIcon name="bag" />
        <p>打理好每一件商品<small>让日常经营更简单</small></p>
      </div>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <div class="breadcrumbs"><span>工作台</span><AppIcon name="right" /><strong>商品管理</strong></div>
        <a class="button secondary" href="#home"><AppIcon name="home" />浏览商城首页</a>
      </header>
      <main id="products"><ProductManagement /></main>
    </div>
  </div>
</template>
