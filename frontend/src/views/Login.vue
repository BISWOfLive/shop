<script setup>
import { reactive, ref } from 'vue'
import { useAuth } from '../composables/useAuth'

defineProps({
  notice: { type: String, default: '' },
})
const emit = defineEmits(['logged-in', 'go-register'])
const { login } = useAuth()
const form = reactive({ username: '', password: '' })
const error = ref('')
const submitting = ref(false)

async function submit() {
  if (submitting.value) return
  error.value = ''
  submitting.value = true
  try {
    await login({ username: form.username, password: form.password })
    emit('logged-in')
  } catch (cause) {
    error.value = cause.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="trade-page auth-page">
    <div class="page-heading"><h1>登录</h1><a class="button secondary" href="#home">返回商城首页</a></div>
    <p v-if="notice" class="success-banner" role="status"><span>{{ notice }}</span></p>
    <form class="trade-panel auth-form" @submit.prevent="submit">
      <label>用户名
        <input v-model="form.username" name="username" autocomplete="username" maxlength="64" required />
      </label>
      <label>密码
        <input v-model="form.password" name="password" type="password" autocomplete="current-password"
               maxlength="128" required />
      </label>
      <p v-if="error" class="trade-error" role="alert">{{ error }}</p>
      <div class="trade-actions">
        <button class="button primary" type="submit" :disabled="submitting">{{ submitting ? '登录中…' : '登录' }}</button>
        <button class="button secondary" type="button" :disabled="submitting" @click="emit('go-register')">注册账号</button>
      </div>
    </form>
  </section>
</template>
