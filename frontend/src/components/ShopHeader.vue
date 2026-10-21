<script setup>
import { ref } from 'vue'
import AppIcon from './AppIcon.vue'
import '../storefront.css'
import { usePurchaseList } from '../composables/usePurchaseList'
const { openList, count } = usePurchaseList()

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
defineExpose({ focusSearch })
</script>

<template>
  <div class="shop-utility">
    <div class="shop-container shop-utility-inner">
      <a href="#home"><AppIcon name="home" />商城首页</a>
      <nav aria-label="快捷导航">
        <a href="#orders">我的订单</a>
        <a href="#inventory">库存管理</a>
        <button v-if="showCategories" type="button" @click="emit('browse-categories')"><AppIcon name="grid" />全部分类</button>
        <a href="#manage">商品管理 <AppIcon name="right" /></a>
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
