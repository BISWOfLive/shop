<script setup>
import { computed, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import catalogImage from '../assets/catalog-samples.png'

const props = defineProps({ product: { type: Object, required: true } })
const failed = ref(false)
watch(() => props.product.imageUrl, () => { failed.value = false })
const sampleStyle = computed(() => {
  const index = props.product.sampleImageIndex ?? 0
  return {
    backgroundImage: `url(${catalogImage})`,
    backgroundPosition: `${(index % 3) * 50}% ${Math.floor(index / 3) * 50}%`,
  }
})
</script>

<template>
  <div v-if="product.isSample" class="catalog-visual catalog-sample-image" :style="sampleStyle"
       role="img" :aria-label="product.name + '（示例图片）'"></div>
  <div v-else class="catalog-visual">
    <img v-if="product.imageUrl && !failed" :src="product.imageUrl" :alt="product.name"
         loading="lazy" decoding="async" @error="failed = true" />
    <span v-else class="catalog-image-empty"><AppIcon name="image" /><span>暂无商品图片</span></span>
  </div>
</template>
