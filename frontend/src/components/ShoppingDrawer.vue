<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import ProductStream from './ProductStream.vue'

const props = defineProps({
  categoryId: { type: String, default: '' },
  enabled: { type: Boolean, default: true },
})
defineEmits(['open-product'])
const opened = ref(false)
const active = ref('latest')
const body = ref(null)
const toggle = ref(null)
const latestTab = ref(null)
const priceTab = ref(null)
const sortBy = computed(() => active.value === 'price' ? 'price' : 'createdAt')
const sortOrder = computed(() => active.value === 'price' ? 'asc' : 'desc')

async function show(section = 'latest') {
  active.value = section === 'price' ? 'price' : 'latest'
  opened.value = true
  await nextTick()
  const tab = active.value === 'price' ? priceTab.value : latestTab.value
  tab?.focus({ preventScroll: true })
}

async function close() {
  if (!opened.value) return
  opened.value = false
  await nextTick()
  toggle.value?.focus({ preventScroll: true })
}

function changeTab(section) {
  active.value = section
  body.value?.scrollTo({ top: 0 })
}

watch(() => props.categoryId, () => {
  if (opened.value) close()
})
defineExpose({ show, close })
</script>

<template>
  <aside class="shop-shopping-drawer" :class="{ 'is-open': opened }" aria-label="最近新增与低价优选"
         @keydown.esc.stop.prevent="close">
    <button ref="toggle" type="button" class="shop-drawer-toggle" :aria-expanded="opened"
            aria-controls="shop-shopping-panel" :aria-label="opened ? '收起商品栏' : '展开最近新增与低价优选'"
            @click="opened ? close() : show(active)"><AppIcon :name="opened ? 'left' : 'right'" /></button>
    <div v-if="opened" id="shop-shopping-panel" class="shop-drawer-panel">
      <div class="shop-drawer-heading">
        <div class="shop-drawer-tabs" role="tablist" aria-label="商品发现方式">
          <button id="shop-latest-tab" ref="latestTab" type="button" role="tab" :aria-selected="active === 'latest'"
                  :tabindex="active === 'latest' ? 0 : -1" aria-controls="shop-drawer-products"
                  @click="changeTab('latest')" @keydown.right.prevent="changeTab('price'); priceTab.focus()">最近新增</button>
          <button id="shop-price-tab" ref="priceTab" type="button" role="tab" :aria-selected="active === 'price'"
                  :tabindex="active === 'price' ? 0 : -1" aria-controls="shop-drawer-products"
                  @click="changeTab('price')" @keydown.left.prevent="changeTab('latest'); latestTab.focus()">低价优选</button>
        </div>
        <button type="button" class="shop-drawer-close" aria-label="关闭商品栏" @click="close"><AppIcon name="close" /></button>
      </div>
      <div id="shop-drawer-products" ref="body" class="shop-drawer-body" role="tabpanel"
           :aria-labelledby="active === 'price' ? 'shop-price-tab' : 'shop-latest-tab'">
        <ProductStream v-if="enabled" :category-id="categoryId" :sort-by="sortBy" :sort-order="sortOrder"
                       :scroll-root="body" @open-product="$emit('open-product', $event)" />
        <p v-else class="shop-drawer-unavailable" role="status">当前分类暂不可用</p>
      </div>
    </div>
  </aside>
</template>
