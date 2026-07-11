<template>
  <div class="auth-page fusion-auth-page">
    <div class="auth-container">
      <div class="auth-bg-card"></div>
      <div class="auth-card">
        <div class="auth-header">
          <h1 class="auth-title">找回密码</h1>
          <p class="auth-subtitle">验证邮箱后重设密码</p>
        </div>

        <n-alert v-if="errorMsg" type="error" :title="errorMsg" style="margin-bottom: 24px" />
        <n-alert v-if="successMsg" type="success" :title="successMsg" style="margin-bottom: 24px" />

        <n-form ref="formRef" :model="form" :rules="rules" size="large" class="auth-form">
          <n-form-item path="email" label="邮箱" class="fusion-form-item">
            <n-input v-model:value="form.email" placeholder="输入注册邮箱..." class="fusion-input" />
          </n-form-item>
          
          <n-form-item path="verificationCode" label="邮箱验证码" class="fusion-form-item">
            <div class="verify-row">
              <n-input v-model:value="form.verificationCode" placeholder="6位验证码..." class="fusion-input verify-input" />
              <n-button attr-type="button" :disabled="countdown > 0" :loading="sendingCode" @click="handleSendCode" class="verify-btn">
                {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
              </n-button>
            </div>
          </n-form-item>

          <n-form-item path="newPassword" label="新密码" class="fusion-form-item">
            <n-input v-model:value="form.newPassword" type="password" placeholder="······" class="fusion-input" />
          </n-form-item>

          <n-form-item path="confirmNewPassword" label="确认新密码" class="fusion-form-item">
            <n-input v-model:value="form.confirmNewPassword" type="password" placeholder="······" class="fusion-input" />
          </n-form-item>
          
          <n-button type="primary" block :loading="loading" @click="handleResetClick" class="auth-btn">重置密码</n-button>
        </n-form>

        <div class="auth-footer">
          想起密码了？
          <router-link to="/login" class="auth-link">点此登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onBeforeUnmount, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NForm, NFormItem, NInput, NButton, NAlert } from 'naive-ui'
import type { FormInst, FormRules } from 'naive-ui'
import { authApi } from '../api'

import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const errorMsg = ref<string | null>(null)
const successMsg = ref<string | null>(null)
const formRef = ref<FormInst | null>(null)

const form = reactive({ email: '', verificationCode: '', newPassword: '', confirmNewPassword: '' })

onMounted(() => {
  if (route.query.email) {
    form.email = route.query.email as string
  }
})

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: ['input', 'blur'] }
  ],
  verificationCode: [{ required: true, message: '请输入邮箱验证码' }],
  newPassword: [{ required: true, message: '请输入新密码', min: 6 }],
  confirmNewPassword: [
    { required: true, message: '请确认新密码' },
    {
      validator: (_rule: any, value: string) => value === form.newPassword,
      message: '两次输入的密码不一致',
      trigger: ['input', 'blur']
    }
  ]
}

const sendingCode = ref(false)
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
})

async function handleSendCode() {
  if (!form.email) {
    errorMsg.value = '请先输入邮箱'
    return
  }
  sendingCode.value = true
  errorMsg.value = null
  try {
    await authApi.sendResetPasswordCode(form.email)
    countdown.value = 60
    countdownTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(countdownTimer!)
        countdownTimer = null
      }
    }, 1000)
    successMsg.value = '验证码已发送至您的邮箱，请注意查收'
    setTimeout(() => { successMsg.value = null }, 3000)
  } catch (e: any) {
    console.error('发送验证码失败', e)
    errorMsg.value = e.response?.data?.message || '发送验证码失败'
  } finally {
    sendingCode.value = false
  }
}

async function handleResetClick() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  errorMsg.value = null
  loading.value = true
  try {
    await auth.resetPassword(
      form.email,
      form.verificationCode,
      form.newPassword,
      form.confirmNewPassword
    )
    successMsg.value = '密码重置成功，即将进入系统...'
    setTimeout(() => {
      router.push('/')
    }, 1500)
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '重置密码失败，请检查验证码是否正确'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.verify-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.verify-input {
  flex: 1;
  min-width: 0;
}

.verify-btn {
  flex: 0 0 auto;
  white-space: nowrap;
}

@media (max-width: 420px) {
  .verify-row {
    gap: 6px;
  }

  .verify-btn {
    min-width: 84px;
    font-size: 12px;
    padding-left: 10px;
    padding-right: 10px;
  }
}

/* ── Fusion Scrapbook/Magazine Styles ── */
.fusion-auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  min-height: 100vh;
  background-image: 
    radial-gradient(circle at 10% 20%, color-mix(in oklab, var(--color-primary) 3%, transparent) 0%, transparent 20%),
    radial-gradient(circle at 90% 80%, color-mix(in oklab, var(--color-accent) 2%, transparent) 0%, transparent 20%);
}

.auth-container {
  width: 100%;
  max-width: 420px;
  position: relative;
  margin: 20px 0;
}

.auth-bg-card {
  position: absolute;
  inset: 0;
  background: color-mix(in oklab, var(--color-surface) 95%, #e8dcc5);
  border-radius: 8px;
  transform: rotate(1deg) translateY(4px);
  box-shadow: 2px 8px 24px rgba(0,0,0,0.04);
  z-index: 0;
  border: 1px solid color-mix(in oklab, var(--color-border) 30%, transparent);
}

.auth-card {
  position: relative;
  background: var(--color-surface);
  border-radius: 8px;
  padding: 40px 30px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.03);
  z-index: 1;
  border: 1px solid var(--color-border);
  background-image: linear-gradient(135deg, transparent 80%, color-mix(in oklab, var(--color-primary) 2%, transparent));
}

@media (min-width: 480px) {
  .auth-card {
    padding: 45px 35px 35px;
  }
}

.auth-header {
  text-align: center;
  margin-bottom: 30px;
}

.auth-title {
  font-family: var(--font-display);
  font-size: 2.2rem;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 10px 0;
  letter-spacing: 0.02em;
}

.auth-subtitle {
  font-size: 0.95rem;
  color: var(--color-primary);
  margin: 0;
  font-style: italic;
  font-weight: 500;
}

.auth-form {
  display: flex;
  flex-direction: column;
}

:deep(.fusion-form-item .n-form-item-label) {
  font-size: 13px !important;
  font-weight: 700 !important;
  color: var(--color-primary) !important;
  letter-spacing: 0.08em;
  padding-bottom: 2px !important;
}

:deep(.fusion-input) {
  --n-border: none !important;
  --n-border-hover: none !important;
  --n-border-focus: none !important;
  --n-box-shadow-focus: none !important;
  border-radius: 0 !important;
  border-bottom: 1.5px solid var(--color-border) !important;
  background: transparent !important;
  padding: 4px 0 !important;
}

:deep(.fusion-input:focus-within) {
  border-bottom-color: var(--color-primary) !important;
}

:deep(.fusion-input .n-input__input-el) {
  font-size: 16px;
  color: var(--color-text);
  font-family: var(--font-body);
}

:deep(.fusion-input .n-input__placeholder) {
  color: color-mix(in oklab, var(--color-text-muted) 60%, transparent) !important;
  font-style: italic;
  font-family: var(--font-display);
}

:deep(.verify-btn) {
  --n-border: none !important;
  --n-border-hover: none !important;
  --n-border-focus: none !important;
  background: color-mix(in oklab, var(--color-primary) 10%, transparent) !important;
  color: var(--color-primary) !important;
  border-radius: 8px !important;
  font-weight: 600 !important;
  height: 38px !important;
  font-size: 14px !important;
  transition: all 0.2s !important;
}
:deep(.verify-btn:not([disabled]):hover) {
  background: color-mix(in oklab, var(--color-primary) 18%, transparent) !important;
}

:deep(.auth-btn) {
  margin-top: 10px;
  --n-border: none !important;
  --n-border-hover: none !important;
  --n-border-focus: none !important;
  background: var(--color-primary) !important;
  color: #fff !important;
  border-radius: 12px !important;
  font-weight: 600 !important;
  letter-spacing: 0.05em;
  box-shadow: 0 4px 12px color-mix(in oklab, var(--color-primary) 20%, transparent) !important;
  height: 48px !important;
  font-size: 16px !important;
  transition: all 0.2s !important;
}

:deep(.auth-btn:not([disabled]):hover) {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px color-mix(in oklab, var(--color-primary) 30%, transparent) !important;
}

.auth-footer {
  margin-top: 25px;
  text-align: center;
  font-size: 13.5px;
  color: var(--color-text-secondary);
}

.auth-link {
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 600;
  transition: text-decoration 0.2s;
}

.auth-link:hover {
  text-decoration: underline;
}
</style>
