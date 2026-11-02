<script setup>
import { reactive, ref } from 'vue'
import { register } from '../api/auth.js'

const emit = defineEmits(['registered', 'go-login'])
const form = reactive({ username: '', password: '', nickname: '' })
const error = ref('')
const submitting = ref(false)

async function submit() {
  if (submitting.value) return
  error.value = ''
  submitting.value = true
  try {
    await register({ username: form.username, password: form.password, nickname: form.nickname })
    emit('registered')
  } catch (cause) {
    error.value = cause.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="trade-page auth-page">
    <div class="page-heading"><h1>注册</h1><a class="button secondary" href="#home">返回商城首页</a></div>
    <form class="trade-panel auth-form" @submit.prevent="submit">
      <label>用户名
        <input v-model="form.username" name="username" autocomplete="username" maxlength="64"
               pattern="[\x21-\x7E]+" required />
      </label>
      <label>密码
        <input v-model="form.password" name="password" type="password" autocomplete="new-password"
               minlength="8" maxlength="128" required />
      </label>
      <label>昵称
        <input v-model="form.nickname" name="nickname" maxlength="64" />
      </label>
      <p v-if="error" class="trade-error" role="alert">{{ error }}</p>
      <div class="trade-actions">
        <button class="button primary" type="submit" :disabled="submitting">{{ submitting ? '注册中…' : '注册' }}</button>
        <button class="button secondary" type="button" :disabled="submitting" @click="emit('go-login')">已有账号，去登录</button>
      </div>
    </form>
  </section>
</template>
