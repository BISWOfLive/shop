<script setup>
import { computed, reactive } from 'vue'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])
const failedImages = reactive({})
const groups = computed(() => [
  { imageType: 1, title: '轮播附图', limit: 8 },
  { imageType: 2, title: '详情图片', limit: 20 },
].map(group => ({
  ...group,
  items: props.modelValue.map((image, index) => ({ image, index }))
    .filter(entry => entry.image.imageType === group.imageType),
})))

function add(group) {
  if (props.disabled || group.items.length >= group.limit) return
  emit('update:modelValue', [...props.modelValue, { imageType: group.imageType, imageUrl: '', altText: '' }])
}

function update(index, field, value) {
  if (props.disabled) return
  emit('update:modelValue', props.modelValue.map((image, position) => position === index ? { ...image, [field]: value } : image))
}

function remove(index) {
  if (!props.disabled) emit('update:modelValue', props.modelValue.filter((_, position) => position !== index))
}

function move(group, position, direction) {
  const target = position + direction
  if (props.disabled || target < 0 || target >= group.items.length) return
  const images = [...props.modelValue]
  const from = group.items[position].index
  const to = group.items[target].index
  ;[images[from], images[to]] = [images[to], images[from]]
  emit('update:modelValue', images)
}

function previewUrl(value) {
  const url = String(value ?? '').trim()
  return /^https?:\/\/\S+$/.test(url) ? url : ''
}
</script>

<template>
  <section v-for="group in groups" :key="group.imageType" class="image-editor-group">
    <div class="image-editor-header">
      <h3>{{ group.title }}</h3>
      <span>{{ group.items.length }} / {{ group.limit }}</span>
      <button class="button secondary" type="button" :disabled="disabled || group.items.length >= group.limit"
              @click="add(group)">添加图片</button>
    </div>
    <div v-for="(entry, position) in group.items" :key="entry.index" class="image-editor-item">
      <div class="image-editor-preview">
        <img v-if="previewUrl(entry.image.imageUrl) && failedImages[entry.index] !== entry.image.imageUrl"
             :src="previewUrl(entry.image.imageUrl)" :alt="entry.image.altText || group.title" loading="lazy"
             @error="failedImages[entry.index] = entry.image.imageUrl" />
        <AppIcon v-else name="image" />
      </div>
      <div class="image-editor-inputs">
        <label class="field">
          <span>图片地址 <b>*</b></span>
          <input :value="entry.image.imageUrl" type="url" pattern="https?://[^\s]+" maxlength="512" required
                 :disabled="disabled" @input="update(entry.index, 'imageUrl', $event.target.value)" />
        </label>
        <label class="field">
          <span>替代文字</span>
          <input :value="entry.image.altText" maxlength="120" :disabled="disabled"
                 @input="update(entry.index, 'altText', $event.target.value)" />
        </label>
      </div>
      <div class="image-editor-actions">
        <button class="button secondary" type="button" :disabled="disabled || position === 0"
                :aria-label="'上移第 ' + (position + 1) + ' 张' + group.title" @click="move(group, position, -1)">上移</button>
        <button class="button secondary" type="button" :disabled="disabled || position === group.items.length - 1"
                :aria-label="'下移第 ' + (position + 1) + ' 张' + group.title" @click="move(group, position, 1)">下移</button>
        <button class="button secondary" type="button" :disabled="disabled"
                :aria-label="'删除第 ' + (position + 1) + ' 张' + group.title" @click="remove(entry.index)">删除</button>
      </div>
    </div>
  </section>
</template>
