<template>
  <div class="auth-page fusion-auth-page">
    <div class="auth-container">
      <div class="auth-bg-card"></div>
      <div class="auth-card">
        <div class="auth-header">
          <h1 class="auth-title">MoodCopilot</h1>
          <p class="auth-subtitle">写下今天，慢慢理解自己。</p>
          <p class="auth-subtitle-small">先帮你看见情绪，再把你温和地连接给相似心情的人</p>
        </div>

        <n-alert v-if="errorMsg" type="error" :title="errorMsg" style="margin-bottom: 24px" />

        <n-form ref="formRef" :model="form" :rules="rules" size="large" class="auth-form">
          <n-form-item path="email" label="邮箱" class="fusion-form-item">
            <n-input v-model:value="form.email" placeholder="输入邮箱..." autocomplete="username" class="fusion-input" />
          </n-form-item>
          
          <n-form-item path="password" label="密码" class="fusion-form-item">
            <n-input v-model:value="form.password" type="password" placeholder="······" autocomplete="current-password" show-password-on="click" class="fusion-input" />
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
          
          <n-button type="primary" block :loading="loading" @click="handleLoginClick" class="auth-btn">登录</n-button>
        </n-form>

        <div class="auth-footer">
          还没有账号？
          <router-link to="/register" class="auth-link">点此注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { NForm, NFormItem, NInput, NButton, NCheckbox, NAlert, type FormInst } from 'naive-ui'
import { useAuthStore } from '../stores/auth'

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

async function handleLoginClick() {
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
        doLogin(token);
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
      doLogin('');
    }
  } else {
    doLogin('');
  }
}

async function doLogin(captchaToken: string) {
  loading.value = true
  try {
    await auth.login(form.email, form.password, captchaToken || undefined)
    router.push('/')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '登录失败，请检查邮箱和密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
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
  background: var(--color-primary-light)