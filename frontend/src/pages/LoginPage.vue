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
        <div class="privacy-disclaimer">
          <span class="privacy-icon">🔒</span>
          <span><strong>数据隐私承诺：</strong>我们承诺绝不窥探、使用您的任何个人数据。您的日记和对话仅在程序内部与大模型推理环节中严格流转。</span>
        </div>
        <div id="captcha-box"></div>
        <n-button type="primary" block :loading="loading" @click="handleLoginClick">登录</n-button>
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
  gap: 6px;
  margin-top: -12px;
  margin-bottom: 20px;
  padding: 10px 12px;
  background: rgba(74, 124, 98, 0.05);
  border-radius: 8px;
  border: 1px solid rgba(74, 124, 98, 0.15);
  font-size: 12px;
  color: #4a5a4e;
  line-height: 1.5;
}
.privacy-icon {
  font-size: 14px;
}
</style>
