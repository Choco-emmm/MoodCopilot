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
        <div class="privacy-disclaimer">
          <div class="privacy-icon">🔒</div>
          <div class="privacy-content">
            <strong>数据隐私保护声明：</strong>
            <ul class="privacy-list">
              <li>默认情况下，您的私密数据仅在您与AI间严格流转，开发者绝不窥探或利用。</li>
              <li>仅当您主动将单篇日记设为「分享到社区」时，该文本才会在社区流通。</li>
              <li>仅当您主动开启共鸣匹配功能时，系统才会提取您的特征（绝不含私密明文）用于寻找共鸣之人。</li>
            </ul>
          </div>
        </div>
        <n-form-item path="agreed">
          <n-checkbox v-model:checked="form.agreed">
            我已阅读并同意上述隐私声明；且知晓 AI 分析仅供参考，不构成医疗诊断。
          </n-checkbox>
        </n-form-item>
        <div id="captcha-box"></div>
        <n-button type="primary" block :loading="loading" @click="handleRegisterClick">注册</n-button>
      </n-form>
      <p class="auth-switch">
        已有账号？
        <router-link to="/login">登录</router-link>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { NForm, NFormItem, NInput, NButton, NCheckbox, NAlert, type FormInst } from 'naive-ui'
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
const rules = {
  displayName: [{ required: true, message: '请输入用户名' }],
  email: [{ required: true, message: '请输入邮箱' }],
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
  gap: 8px;
  margin-top: 8px;
  margin-bottom: 16px;
  padding: 12px;
  background: rgba(74, 124, 98, 0.05);
  border-radius: 8px;
  border: 1px solid rgba(74, 124, 98, 0.15);
  font-size: 12px;
  color: #4a5a4e;
  line-height: 1.5;
}
.privacy-icon {
  font-size: 16px;
  margin-top: 2px;
}
.privacy-content {
  flex: 1;
}
.privacy-list {
  margin: 4px 0 0 0;
  padding-left: 18px;
}
.privacy-list li {
  margin-bottom: 2px;
}
</style>
