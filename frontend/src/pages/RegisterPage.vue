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
          <div class="verify-row">
            <n-input v-model:value="form.verificationCode" placeholder="6位验证码" class="verify-input" />
            <n-button attr-type="button" :disabled="countdown > 0" :loading="sendingCode" @click="handleSendCode" class="verify-btn">
              {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
            </n-button>
          </div>
        </n-form-item>
        <n-form-item path="inviteCode" label="内测邀请码">
          <n-input v-model:value="form.inviteCode" placeholder="请输入邀请码" :disabled="loading" />
        </n-form-item>
        <n-form-item path="agreed">
          <n-checkbox v-model:checked="form.agreed">
            我已知晓 MoodCopilot 提供的 AI 对话与情绪分析仅供参考与心理疏导，不构成任何专业医疗诊断。开发者不对 AI 生成的内容承担法律责任。
          </n-checkbox>
        </n-form-item>
        <div ref="turnstileRef" class="turnstile-widget"></div>
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
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { NForm, NFormItem, NInput, NButton, NCheckbox, NAlert, type FormInst } from 'naive-ui'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api'

declare global {
  interface Window {
    turnstile?: {
      render: (el: HTMLElement, opts: Record<string, unknown>) => string
      reset: (id: string) => void
      remove: (id: string) => void
    }
  }
}

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const errorMsg = ref<string | null>(null)
const formRef = ref<FormInst | null>(null)
const turnstileRef = ref<HTMLElement | null>(null)
const turnstileToken = ref('')
let turnstileWidgetId: string | undefined

const form = reactive({ displayName: '', email: '', password: '', verificationCode: '', inviteCode: '', agreed: false })
const rules = {
  displayName: [{ required: true, message: '请输入用户名' }],
  email: [{ required: true, message: '请输入邮箱' }],
  password: [{ required: true, message: '请输入密码', min: 6 }],
  verificationCode: [{ required: true, message: '请输入邮箱验证码' }],
  inviteCode: [{ required: true, message: '请输入内测邀请码' }],
  agreed: [
    {
      validator: (_rule: any, value: boolean) => value === true,
      message: '请先阅读并同意免责声明',
      trigger: ['change', 'blur'],
    },
  ],
}

const sendingCode = ref(false)
const countdown = ref(0)

function renderTurnstile() {
  if (!turnstileRef.value) return
  const siteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY
  if (!siteKey) return
  if (!window.turnstile) {
    setTimeout(renderTurnstile, 300)
    return
  }
  turnstileWidgetId = window.turnstile.render(turnstileRef.value, {
    sitekey: siteKey,
    callback: (token: string) => { turnstileToken.value = token },
    'expired-callback': () => { turnstileToken.value = '' },
  })
}

onMounted(() => renderTurnstile())
onUnmounted(() => {
  if (turnstileWidgetId) window.turnstile?.remove(turnstileWidgetId)
})

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
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  errorMsg.value = null
  try {
    await auth.register(form.displayName, form.email, form.password, form.inviteCode, form.verificationCode, turnstileToken.value || undefined)
    router.push('/')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '注册失败，请稍后重试'
    turnstileToken.value = ''
    if (turnstileWidgetId) window.turnstile?.reset(turnstileWidgetId)
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
</style>
