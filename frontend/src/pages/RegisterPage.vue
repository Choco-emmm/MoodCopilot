<template>
  <div class="auth-page fusion-auth-page">
    <div class="auth-container">
      <div class="auth-bg-card"></div>
      <div class="auth-card">
        <div class="auth-header">
          <h1 class="auth-title">MoodCopilot</h1>
          <p class="auth-subtitle">写下今天，慢慢理解自己。</p>
          <p class="auth-subtitle-small">记录真实的自己，等待同频的灵魂</p>
        </div>

        <n-alert v-if="errorMsg" type="error" :title="errorMsg" style="margin-bottom: 24px" />

        <n-form ref="formRef" :model="form" :rules="rules" size="large" class="auth-form">
          <n-form-item path="displayName" label="用户名" class="fusion-form-item">
            <n-input v-model:value="form.displayName" placeholder="给自己起个名字..." class="fusion-input" />
          </n-form-item>
          
          <n-form-item path="email" label="邮箱" class="fusion-form-item">
            <n-input v-model:value="form.email" placeholder="输入邮箱..." class="fusion-input" />
          </n-form-item>
          
          <n-form-item path="password" label="密码" class="fusion-form-item">
            <n-input v-model:value="form.password" type="password" placeholder="······" class="fusion-input" />
          </n-form-item>
          
          <n-form-item path="verificationCode" label="邮箱验证码" class="fusion-form-item">
            <div class="verify-row">
              <n-input v-model:value="form.verificationCode" placeholder="6位验证码..." class="fusion-input verify-input" />
              <n-button attr-type="button" :disabled="countdown > 0" :loading="sendingCode" @click="handleSendCode" class="verify-btn">
                {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
              </n-button>
            </div>
          </n-form-item>
          
          <div class="privacy-disclaimer">
            <div class="privacy-icon">🔒</div>
            <div class="privacy-content">
              <strong>数据隐私保护声明：</strong>
              <ul class="privacy-list">
                <li>私密数据仅在您与AI间严格流转。</li>
                <li>仅主动分享时才会在社区流通。</li>
                <li>匹配功能仅提取无明文特征。</li>
              </ul>
            </div>
          </div>
          
          <n-form-item path="agreed" class="agreed-item">
            <n-checkbox v-model:checked="form.agreed">
              阅读并同意隐私声明，知晓 AI 仅供参考。
            </n-checkbox>
          </n-form-item>
          
          <div id="captcha-box"></div>
          
          <n-button type="primary" block :loading="loading" @click="handleRegisterClick" class="auth-btn">注册</n-button>
        </n-form>

        <div class="auth-footer">
          已有账号？
          <router-link to="/login" class="auth-link">点此登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { NForm, NFormItem, NInput, NButton, NCheckbox, NAlert } from 'naive-ui'
import type { FormInst, FormRules } from 'naive-ui'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api'

declare global {
  interface Window {
    initTAC?: (path: string, config: any, style?: any) => Promise<any>
  }
}

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const errorMsg = ref<string | null>(null)
const formRef = ref<FormInst | null>(null)

const form = reactive({ displayName: '', email: '', password: '', verificationCode: '', agreed: false })
const rules: FormRules = {
  displayName: [
    { required: true, message: '请输入用户名' },
    {
      validator: async (_rule: any, value: string) => {
        if (!value) return
        if (!/^[a-zA-Z0-9\u4e00-\u9fa5_-]{2,20}$/.test(value)) {
          throw new Error('需为 2-20 位中英文、数字、下划线或横线')
        }
        try {
          const res = await authApi.checkUsername(value)
          if (!res.data.data.available) {
            throw new Error('该用户名已被占用')
          }
        } catch (e: any) {
          if (e.message === '该用户名已被占用') throw e
        }
      },
      trigger: 'blur'
    }
  ],
  email: [
    { required: true, message: '请输入邮箱' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: ['input', 'blur'] }
  ],
  password: [{ required: true, message: '请输入密码', min: 6 }],
  verificationCode: [{ required: true, message: '请输入邮箱验证码' }],
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
let countdownTimer: ReturnType<typeof setInterval> | null = null

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
})

async function handleSendCode() {
  sendingCode.value = true
  try {
    await authApi.sendCode(form.email)
    countdown.value = 60
    countdownTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(countdownTimer!)
        countdownTimer = null
      }
    }, 1000)
  } catch (e: any) {
    console.error('发送验证码失败', e)
    errorMsg.value = e.response?.data?.message || '发送验证码失败'
  } finally {
    sendingCode.value = false
  }
}

async function handleRegisterClick() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  errorMsg.value = null

  if ((window as any).TAC) {
    const config = {
      requestCaptchaDataUrl: "/api/auth/captcha/gen",
      validCaptchaUrl: "/api/auth/captcha/check",
      bindEl: "#captcha-box",
      validSuccess: (res: any, c: any, tac: any) => {
        tac.destroyWindow();
        let token = res.data;
        if (typeof res.data === 'object' && res.data.token) {
          token = res.data.token;
        }
        doRegister(token);
      },
      validFail: (res: any, c: any, tac: any) => {
        tac.reloadCaptcha();
      },
      btnCloseFun: (el: any, tac: any) => {
        tac.destroyWindow();
      }
    };
    try {
      new (window as any).TAC(config).init();
    } catch (e) {
      console.error("初始化验证码失败", e);
      doRegister('');
    }
  } else {
    doRegister('');
  }
}

async function doRegister(captchaToken: string) {
  loading.value = true
  try {
    await auth.register(form.displayName, form.email, form.password, form.verificationCode, captchaToken || undefined)
    router.push('/')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '注册失败，请稍后重试'
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

#captcha-box {
  width: 100%;
  display: flex;
  justify-content: center;
}
@media (max-width: 380px) {
  #captcha-box {
    transform: scale(0.9);
    transform-origin: center top;
    margin-bottom: -30px;
  }
}
@media (max-width: 330px) {
  #captcha-box {
    transform: scale(0.8);
    transform-origin: center top;
    margin-bottom: -60px;
  }
}
.privacy-disclaimer {
  display: flex;
  align-items: flex-start;
  gap