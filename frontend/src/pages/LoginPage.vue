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
        <n-form-item path="agreed">
          <n-checkbox v-model:checked="form.agreed">
            我已知晓 MoodCopilot 提供的 AI 对话与情绪分析仅供参考与心理疏导，不构成任何专业医疗诊断。开发者不对 AI 生成的内容承担法律责任。
          </n-checkbox>
        </n-form-item>
        <div ref="turnstileRef" class="turnstile-widget"></div>
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
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { NForm, NFormItem, NInput, NButton, NCheckbox, NAlert, type FormInst } from 'naive-ui'
import { useAuthStore } from '../stores/auth'

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

const form = reactive({ email: '', password: '', agreed: false })
const rules = {
  email: [{ required: true, message: '请输入邮箱' }],
  password: [{ required: true, message: '请输入密码' }],
  agreed: [
    {
      validator: (_rule: any, value: boolean) => value === true,
      message: '请先阅读并同意免责声明',
      trigger: ['change', 'blur'],
    },
  ],
}

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

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  errorMsg.value = null
  try {
    await auth.login(form.email, form.password, turnstileToken.value || undefined)
    router.push('/')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '登录失败，请检查邮箱和密码'
    turnstileToken.value = ''
    if (turnstileWidgetId) window.turnstile?.reset(turnstileWidgetId)
  } finally {
    loading.value = false
  }
}
</script>
