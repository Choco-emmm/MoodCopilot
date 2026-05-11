<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="auth-title">MoodCopilot</h1>
      <p class="auth-sub">登录你的情绪日记</p>
      <n-alert v-if="errorMsg" type="error" :title="errorMsg" style="margin-bottom: 16px" />
      <n-form ref="formRef" :model="form" :rules="rules" size="large">
        <n-form-item path="email" label="邮箱">
          <n-input v-model:value="form.email" placeholder="输入邮箱" />
        </n-form-item>
        <n-form-item path="password" label="密码">
          <n-input v-model:value="form.password" type="password" placeholder="输入密码" />
        </n-form-item>
        <n-button type="primary" block :loading="loading" @click="handleLogin">登录</n-button>
      </n-form>
      <p class="auth-switch">
        还没有账号？
        <router-link to="/register">注册</router-link>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { NForm, NFormItem, NInput, NButton, NAlert } from 'naive-ui'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const errorMsg = ref<string | null>(null)

const form = reactive({ email: '', password: '' })
const rules = {
  email: [{ required: true, message: '请输入邮箱' }],
  password: [{ required: true, message: '请输入密码' }],
}

async function handleLogin() {
  loading.value = true
  errorMsg.value = null
  try {
    await auth.login(form.email, form.password)
    router.push('/')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '登录失败，请检查邮箱和密码'
  } finally {
    loading.value = false
  }
}
</script>
