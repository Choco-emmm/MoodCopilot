<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="auth-title">MoodCopilot</h1>
      <div class="auth-logo">
        <svg width="28" height="22" viewBox="0 0 20 16" fill="none">
          <path d="M2 14 L6 2 L10 10 L14 2 L18 14" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <p class="auth-sub">开始记录你的情绪</p>
      <n-alert v-if="errorMsg" type="error" :title="errorMsg" style="margin-bottom: 16px" />
      <n-form ref="formRef" :model="form" :rules="rules" size="large">
        <n-form-item path="displayName" label="用户名">
          <n-input v-model:value="form.displayName" placeholder="给自己起个名字" />
        </n-form-item>
        <n-form-item path="email" label="邮箱">
          <n-input v-model:value="form.email" placeholder="输入邮箱" />
        </n-form-item>
        <n-form-item path="password" label="密码">
          <n-input v-model:value="form.password" type="password" placeholder="至少6位密码" />
        </n-form-item>
        <n-button type="primary" block :loading="loading" @click="handleRegister">注册</n-button>
      </n-form>
      <p class="auth-switch">
        已有账号？
        <router-link to="/login">登录</router-link>
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

const form = reactive({ displayName: '', email: '', password: '' })
const rules = {
  displayName: [{ required: true, message: '请输入用户名' }],
  email: [{ required: true, message: '请输入邮箱' }],
  password: [{ required: true, message: '请输入密码', min: 6 }],
}

async function handleRegister() {
  loading.value = true
  errorMsg.value = null
  try {
    await auth.register(form.displayName, form.email, form.password)
    router.push('/')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>
