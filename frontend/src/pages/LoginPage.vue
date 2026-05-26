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
  background: var(--color-primary-light);
  border-radius: 8px;
  border: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-secondary);
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

/* ── Fusion Scrapbook/Magazine Styles ── */
.fusion-auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background-image: 
    radial-gradient(circle at 10% 20%, color-mix(in oklab, var(--color-primary) 3%, transparent) 0%, transparent 20%),
    radial-gradient(circle at 90% 80%, color-mix(in oklab, var(--color-accent) 2%, transparent) 0%, transparent 20%);
}

.auth-container {
  width: 100%;
  max-width: 420px;
  position: relative;
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
  padding: 45px 35px 35px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.03);
  z-index: 1;
  border: 1px solid var(--color-border);
  background-image: linear-gradient(135deg, transparent 80%, color-mix(in oklab, var(--color-primary) 2%, transparent));
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

.auth-subtitle-small {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  margin: 6px 0 0;
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

:deep(.agreed-item .n-form-item-blank) {
  align-items: flex-start;
}
:deep(.n-checkbox) {
  align-items: flex-start;
}
</style>
