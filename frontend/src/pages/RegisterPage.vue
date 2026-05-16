<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="auth-title">MoodCopilot</h1>
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
        <n-form-item path="verificationCode" label="邮箱验证码">
          <div style="display: flex; gap: 8px; width: 100%">
            <n-input v-model:value="form.verificationCode" placeholder="6位验证码" style="flex: 1" />
            <n-button attr-type="button" :disabled="countdown > 0" :loading="sendingCode" @click="handleSendCode" style="white-space: nowrap">
              {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
            </n-button>
          </div>
        </n-form-item>
        <n-form-item path="inviteCode" label="内测邀请码">
          <n-input v-model:value="form.inviteCode" placeholder="请输入邀请码" :disabled="loading" />
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
import { authApi } from '../api'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const errorMsg = ref<string | null>(null)

const form = reactive({ displayName: '', email: '', password: '', verificationCode: '', inviteCode: '' })
const rules = {
  displayName: [{ required: true, message: '请输入用户名' }],
  email: [{ required: true, message: '请输入邮箱' }],
  password: [{ required: true, message: '请输入密码', min: 6 }],
  verificationCode: [{ required: true, message: '请输入邮箱验证码' }],
  inviteCode: [{ required: true, message: '请输入内测邀请码' }],
}

const sendingCode = ref(false)
const countdown = ref(0)

async function handleSendCode() {
  console.log('当前的 authApi 对象内容是:', authApi)
  sendingCode.value = true
  try {
    await authApi.sendCode(form.email)
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e: any) {
    console.error('发送验证码失败', e)
    errorMsg.value = e.response?.data?.message || '发送验证码失败'
  } finally {
    sendingCode.value = false
  }
}

async function handleRegister() {
  loading.value = true
  errorMsg.value = null
  try {
    await auth.register(form.displayName, form.email, form.password, form.inviteCode, form.verificationCode)
    router.push('/')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>
