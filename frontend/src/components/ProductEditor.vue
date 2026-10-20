<script setup>
import { computed, reactive, ref } from 'vue'
import { saveProduct } from '../api/products'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  categories: { type: Array, default: () => [] },
})
const emit = defineEmits(['saved'])
const dialog = ref(null)
const editingId = ref(null)
const saving = ref(false)
const errorMessage = ref('')
const form = reactive({
  name: '',
  categoryId: '',
  price: '',
  status: 0,
  imageUrl: '',
  description: '',
})
const title = computed(() => editingId.value === null ? '新增商品' : '编辑商品')

function open(product = null) {
  editingId.value = product?.id ?? null
  Object.assign(form, {
    name: product?.name ?? '',
    categoryId: product?.categoryId ?? props.categories[0]?.id ?? '',
    price: product ? String(product.price) : '',
    status: product?.status ?? 0,
    imageUrl: product?.imageUrl ?? '',
    description: product?.description ?? '',
  })
  errorMessage.value = ''
  dialog.value.showModal()
}

function close() {
  if (!saving.value) dialog.value.close()
}

function onCancel(event) {
  if (saving.value) event.preventDefault()
}

function onBackdrop(event) {
  if (event.target === dialog.value) close()
}

async function submit() {
  if (saving.value) return
  if (!form.name.trim()) {
    errorMessage.value = '商品名称不能为空'
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    const isNew = editingId.value === null
    const product = await saveProduct(editingId.value, {
      name: form.name.trim(),
      categoryId: Number(form.categoryId),
      price: Number(form.price),
      status: Number(form.status),
      imageUrl: form.imageUrl.trim(),
      description: form.description.trim(),
    })
    dialog.value.close()
    emit('saved', { product, isNew })
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <dialog ref="dialog" class="product-dialog" aria-labelledby="editor-title"
          @cancel="onCancel" @click="onBackdrop">
    <form @submit.prevent="submit">
      <div class="dialog-header">
        <div>
          <p class="eyebrow">商品信息</p>
          <h2 id="editor-title">{{ title }}</h2>
          <p class="muted">填写商品的基本信息，带 * 的项目为必填项。</p>
        </div>
        <button class="icon-button" type="button" aria-label="关闭表单" :disabled="saving" @click="close">
          <AppIcon name="close" />
        </button>
      </div>

      <fieldset class="editor-fields" :disabled="saving">
        <label class="field">
          <span>商品名称 <b>*</b></span>
          <input v-model="form.name" name="name" required maxlength="120"
                 placeholder="例如：简约桌面收纳盒" autofocus />
        </label>
        <div class="form-columns">
          <label class="field">
            <span>商品分类 <b>*</b></span>
            <select v-model="form.categoryId" name="categoryId" required>
              <option disabled value="">请选择分类</option>
              <option v-for="category in categories" :key="category.id" :value="category.id">
                {{ category.name }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>销售价格（元） <b>*</b></span>
            <input v-model="form.price" name="price" type="number" inputmode="decimal"
                   min="0.01" max="9999999999.99" step="0.01" placeholder="0.00" required />
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
          <span>主图地址 <b v-if="form.status === 1">*</b><small>{{ form.status === 1 ? '上架必填' : '下架时可暂不填' }}</small></span>
          <input v-model="form.imageUrl" name="imageUrl" type="url" maxlength="512"
                 :required="form.status === 1" placeholder="https://example.com/product.jpg" />
          <small>使用以 http:// 或 https:// 开头的稳定图片地址；可先下架保存，补齐主图后再上架。</small>
        </label>
        <label class="field">
          <span class="label-row">商品描述 <small>{{ form.description.length }}/2000</small></span>
          <textarea v-model="form.description" name="description" rows="4" maxlength="2000"
                    placeholder="介绍商品的材质、规格或使用方式……"></textarea>
        </label>
        <p v-if="!categories.length" class="inline-error" role="alert">暂无可用分类，请先初始化商品分类。</p>
        <p v-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }}</p>
      </fieldset>

      <div class="dialog-footer">
        <button class="button secondary" type="button" :disabled="saving" @click="close">取消</button>
        <button class="button primary" type="submit" :disabled="saving || !categories.length">
          <AppIcon v-if="!saving" name="check" />{{ saving ? '正在保存…' : '保存商品' }}
        </button>
      </div>
    </form>
  </dialog>
</template>
