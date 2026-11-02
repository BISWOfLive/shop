<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { getProduct, saveProduct } from '../api/products'
import { normalizeProductPrice } from '../utils/money'
import AppIcon from './AppIcon.vue'
import ProductImagesEditor from './ProductImagesEditor.vue'

const props = defineProps({
  categories: { type: Array, default: () => [] },
})
const emit = defineEmits(['saved'])
const dialog = ref(null)
const editingId = ref(null)
const phase = ref('ready')
const saving = computed(() => phase.value === 'saving')
const errorMessage = ref('')
let controller
let requestId = 0
let disposed = false
const form = reactive({
  name: '',
  subtitle: '',
  categoryId: '',
  price: '',
  status: 0,
  imageUrl: '',
  description: '',
  images: [],
})
const title = computed(() => editingId.value === null ? '新增商品' : '编辑商品')

function cancelDetail() {
  requestId += 1
  controller?.abort()
  controller = null
}

function fillForm(product = null) {
  Object.assign(form, {
    name: product?.name ?? '',
    subtitle: product?.subtitle ?? '',
    categoryId: String(product?.categoryId ?? props.categories[0]?.id ?? ''),
    price: product ? String(product.price) : '',
    status: product?.status ?? 0,
    imageUrl: product?.imageUrl ?? '',
    description: product?.description ?? '',
    images: [
      ...(product?.carouselImages ?? []).map(image => ({ ...image, imageType: 1 })),
      ...(product?.detailImages ?? []).map(image => ({ ...image, imageType: 2 })),
    ].map(image => ({
      imageType: image.imageType,
      imageUrl: image.imageUrl,
      altText: image.altText ?? '',
    })),
  })
}

function open(productId = null) {
  if (saving.value || disposed) return
  cancelDetail()
  editingId.value = productId === null ? null : String(productId)
  fillForm()
  errorMessage.value = ''
  phase.value = editingId.value === null ? 'ready' : 'loading'
  if (!dialog.value.open) dialog.value.showModal()
  if (editingId.value !== null) loadDetail()
}

async function loadDetail() {
  cancelDetail()
  const currentId = requestId
  const productId = editingId.value
  controller = new AbortController()
  phase.value = 'loading'
  errorMessage.value = ''
  try {
    const product = await getProduct(productId, controller.signal, { fresh: true })
    if (disposed || currentId !== requestId || !dialog.value.open) return
    fillForm(product)
    phase.value = 'ready'
  } catch (error) {
    if (error.name === 'AbortError' || disposed || currentId !== requestId) return
    phase.value = 'error'
    errorMessage.value = error.message
  } finally {
    if (!disposed && currentId === requestId) controller = null
  }
}

function close() {
  if (!saving.value) {
    cancelDetail()
    dialog.value.close()
  }
}

function onClosed() {
  cancelDetail()
}

function onCancel(event) {
  if (saving.value) event.preventDefault()
}

function onBackdrop(event) {
  if (event.target === dialog.value) close()
}

async function submit() {
  if (phase.value !== 'ready' || !props.categories.length) return
  if (!form.name.trim()) {
    errorMessage.value = '商品名称不能为空'
    return
  }
  const currentId = requestId
  phase.value = 'saving'
  errorMessage.value = ''
  try {
    const isNew = editingId.value === null
    const product = await saveProduct(editingId.value, {
      name: form.name.trim(),
      subtitle: form.subtitle.trim(),
      categoryId: form.categoryId,
      price: normalizeProductPrice(form.price),
      status: form.status,
      imageUrl: form.imageUrl.trim(),
      description: form.description.trim(),
      images: collectImages(),
    })
    if (disposed || currentId !== requestId) return
    dialog.value.close()
    emit('saved', { product, isNew })
  } catch (error) {
    if (!disposed && currentId === requestId) errorMessage.value = error.message
  } finally {
    if (!disposed && currentId === requestId) phase.value = 'ready'
  }
}

function collectImages() {
  return [1, 2].flatMap(imageType => {
    const group = form.images.filter(image => image.imageType === imageType)
    if (group.length > (imageType === 1 ? 8 : 20)) throw new Error('轮播附图最多 8 张，详情图最多 20 张')
    const urls = new Set()
    return group.map((image, sortOrder) => {
      const imageUrl = image.imageUrl.trim()
      if (!/^https?:\/\/\S+$/.test(imageUrl)) throw new Error('图片地址须以 http:// 或 https:// 开头')
      if (urls.has(imageUrl)) throw new Error('同组图片地址不能重复')
      if (imageType === 1 && imageUrl === form.imageUrl.trim()) throw new Error('轮播附图不能重复主图')
      urls.add(imageUrl)
      return { imageType, imageUrl, altText: image.altText.trim(), sortOrder }
    })
  })
}

onBeforeUnmount(() => {
  disposed = true
  cancelDetail()
})

defineExpose({ open })
</script>

<template>
  <dialog ref="dialog" class="product-dialog" aria-labelledby="editor-title"
          @cancel="onCancel" @click="onBackdrop" @close="onClosed">
    <form @submit.prevent="submit">
      <div class="dialog-header">
        <div>
          <h2 id="editor-title">{{ title }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭表单" :disabled="saving" @click="close">
          <AppIcon name="close" />
        </button>
      </div>

      <p v-if="phase === 'loading'" class="editor-load-state" role="status">正在加载商品详情…</p>
      <div v-else-if="phase === 'error'" class="editor-load-state" role="alert">
        <p>{{ errorMessage }}</p>
        <button class="button secondary" type="button" @click="loadDetail">重新加载详情</button>
      </div>
      <fieldset v-else class="editor-fields" :disabled="saving">
        <label class="field">
          <span>商品名称 <b>*</b></span>
          <input v-model="form.name" name="name" required maxlength="120"
                 placeholder="例如：简约桌面收纳盒" autofocus />
        </label>
        <label class="field">
          <span>短卖点</span>
          <input v-model="form.subtitle" name="subtitle" maxlength="200" />
        </label>
        <div class="form-columns">
          <label class="field">
            <span>商品分类 <b>*</b></span>
            <select v-model="form.categoryId" name="categoryId" required>
              <option disabled value="">请选择分类</option>
              <option v-for="category in categories" :key="category.id" :value="String(category.id)">
                {{ category.name }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>销售价格（元） <b>*</b></span>
            <input v-model="form.price" name="price" type="text" inputmode="decimal"
                   pattern="[0-9]{1,10}([.][0-9]{1,2})?" maxlength="13" placeholder="0.00" required />
          </label>
        </div>
        <label class="field">
          <span>商品状态 <b>*</b></span>
          <select v-model.number="form.status" name="status" required>
            <option :value="1">上架</option>
            <option :value="0">下架</option>
          </select>
        </label>
        <label class="field">
          <span>主图地址 <b v-if="form.status === 1">*</b></span>
          <input v-model="form.imageUrl" name="imageUrl" type="url" maxlength="512"
                 :required="form.status === 1" placeholder="https://example.com/product.jpg" />
        </label>
        <label class="field">
          <span>商品描述</span>
          <textarea v-model="form.description" name="description" rows="4" maxlength="2000"
                    placeholder="介绍商品的材质、规格或使用方式……"></textarea>
        </label>
        <ProductImagesEditor v-model="form.images" :disabled="saving" />
        <p v-if="!categories.length" class="inline-error" role="alert">暂无可用分类，请先初始化商品分类。</p>
        <p v-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }}</p>
      </fieldset>

      <div class="dialog-footer">
        <button class="button secondary" type="button" :disabled="saving" @click="close">取消</button>
        <button class="button primary" type="submit" :disabled="phase !== 'ready' || !categories.length">
          <AppIcon v-if="!saving" name="check" />{{ saving ? '正在保存…' : '保存商品' }}
        </button>
      </div>
    </form>
  </dialog>
</template>
