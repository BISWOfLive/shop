<script setup>
import { ref } from 'vue'
import AppIcon from './AppIcon.vue'
import '../storefront.css'
import { usePurchaseList } from '../composables/usePurchaseList'
import { useAuth } from '../composables/useAuth'
import { useNotifications } from '../composables/useNotifications'
const { openList, count } = usePurchaseList()
const { user, isAdmin, notificationsEnabled, logout } = useAuth()
const { unreadCount } = useNotifications()

const props = defineProps({
  modelValue: { type: String, default: '' },
  scopeName: { type: String, default: '' },
  showCategories: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'search', 'browse-categories'])
const searchInput = ref(null)

function focusSearch() {
  window.scrollTo({ top: 0 })
  searchInput.value?.focus({ preventScroll: true })
}

async function handleLogout() {
  try { await logout() } finally { window.location.hash = '#home' }
}
defineExpose({ focusSearch })
</script>

<template>
  <div class="shop-utility">
    <div class="shop-container shop-utility-inner">
      <a href="#home"><AppIcon name="home" />商城首页</a>
      <nav aria-label="快捷导航">
        <a href="#orders">我的订单</a>
        <a v-if="notificationsEnabled" href="#notifications">通知<span
          v-if="unreadCount !== null && unreadCount > 0" class="shop-notification-count">{{ unreadCount }}</span></a>
        <a v-if="isAdmin" href="#inventory">库存管理</a>
        <a v-if="isAdmin" href="#manage">商品管理 <AppIcon name="right" /></a>
        <a v-if="isAdmin" href="#users">用户管理</a>
        <a v-if="user" href="#account">{{ user.nickname || user.username }}</a>
        <a v-else href="#login">登录</a>
        <a v-if="!user" href="#register">注册</a>
        <button v-if="user" type="button" class="shop-text-button" @click="handleLogout">退出</button>
        <button v-if="showCategories" type="button" @click="emit('browse-categories')"><AppIcon name="grid" />全部分类</button>
      </nav>
    </div>
  </div>
  <header class="shop-header">
    <div class="shop-container shop-masthead">
      <a class="shop-brand" href="#home" aria-label="精简商城首页">
        <span class="shop-brand-mark">简</span><strong>精简商城</strong>
      </a>
      <div class="shop-search-area">
        <form class="shop-search" role="search" @submit.prevent="emit('search')">
          <span v-if="scopeName" class="shop-search-scope" :title="scopeName">{{ scopeName }}</span>
          <AppIcon v-else name="search" />
          <label class="sr-only" for="shop-search">{{ scopeName ? '搜索' + scopeName + '商品' : '搜索商品名称' }}</label>
          <input id="shop-search" ref="searchInput" :value="props.modelValue" type="search" maxlength="120"
                 :disabled="disabled" :placeholder="scopeName ? '搜索当前分类商品' : '搜索商品名称'"
                 @input="emit('update:modelValue', $event.target.value)" />
          <button type="submit" :disabled="disabled">搜索</button>
        </form>
      </div>
      <button class="shop-manage-link" type="button" @click="openList = true"><AppIcon name="bag" /><span>采购清单（{{ count }}）</span></button>
    </div>
  </header>
</template>
