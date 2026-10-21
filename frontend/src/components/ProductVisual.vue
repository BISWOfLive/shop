<script setup>
import { ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import { customerProductName } from '../utils/productPresentation'

const props = defineProps({
  product: { type: Object, required: true },
  eager: { type: Boolean, default: false },
})
const failed = ref(false)
watch(() => [props.product.id, props.product.imageUrl], () => { failed.value = false })
</script>

<template>
  <div class="catalog-visual">
    <img v-if="product.imageUrl && !failed" :src="product.imageUrl" :alt="customerProductName(product)"
         :loading="eager ? 'eager' : 'lazy'" :fetchpriority="eager ? 'high' : 'auto'"
         decoding="async" @error="failed = true" />
    <span v-else class="catalog-image-empty"><AppIcon name="image" /><span>暂无商品图片</span></span>
  </div>
</template>
