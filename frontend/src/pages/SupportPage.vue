<template>
  <main class="app-shell">
    <AppHeader />

    <div class="support-page">
      <div class="support-hero">
        <span class="support-emoji">🧋</span>
        <h1>请开发者喝杯奶茶</h1>
        <p class="support-subtitle">
          MoodCopilot 是一个用爱发电的产品。<br />
          如果你喜欢它，一杯奶茶就是最好的鼓励。
        </p>
      </div>

      <div class="support-qr-section">
        <div class="qr-card" v-for="method in paymentMethods" :key="method.type">
          <img
            v-if="images[method.type]"
            :src="images[method.type]"
            :alt="method.name + '收款码'"
            class="qr-image"
          />
          <div v-else class="qr-placeholder">
            <span class="qr-icon">{{ method.icon }}</span>
            <span class="qr-label">{{ method.name }}</span>
            <span class="qr-hint">{{ method.hint }}</span>
          </div>
          <p class="qr-name">{{ method.name }}</p>

          <div v-if="auth.isAdmin" class="qr-admin-upload">
            <n-button size="tiny" quaternary @click="triggerUpload(method.type)">
              {{ images[method.type] ? '更换' : '上传' }}
            </n-button>
            <input
              :ref="(el: any) => fileInputs[method.type] = el"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              hidden
              @change="(e: Event) => onFileChange(method.type, e)"
            />
          </div>
        </div>
      </div>

      <div class="support-footer">
        <p>
          你的支持是我写代码的动力 ✨<br />
          谢谢你让这个小角落继续存在。
        </p>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { NButton } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { useAuthStore } from '../stores/auth'
import { supportApi } from '../api'

const auth = useAuthStore()
const images = reactive<Record<string, string>>({})
const fileInputs = reactive<Record<string, HTMLInputElement | null>>({})

const paymentMethods = [
  { type: 'wechat', name: '微信赞赏码', icon: '💚', hint: '保存图片扫码' },
]

onMounted(async () => {
  try {
    const res = await supportApi.images()
    Object.assign(images, res.data.data ?? {})
  } catch { /* ignore */ }
})

function triggerUpload(type: string) {
  fileInputs[type]?.click()
}

async function onFileChange(type: string, event: Event) {
  const input = event.target as HTMLInputElement
  const file = input?.files?.[0]
  if (!file) return
  try {
    await supportApi.uploadImage(type, file)
    const res = await supportApi.images()
    Object.assign(images, res.data.data ?? {})
  } catch { /* ignore */ }
  input.value = ''
}
</script>

<style scoped>
.support-page {
  max-width: 520px;
  margin: 0 auto;
  padding: 40px 20px 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.support-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 40px;
}

.support-emoji {
  font-size: 56px;
  display: block;
  margin-bottom: 16px;
}

.support-hero h1 {
  font-size: var(--text-xl);
  font-weight: 600;
  color: var(--color-text, #2f2a24);
  margin: 0 0 12px;
  text-align: center;
}

.support-subtitle {
  font-size: var(--text-base);
  color: var(--color-text-secondary, #67645d);
  line-height: 1.7;
  margin: 0;
  text-align: center;
}

.support-qr-section {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 20px;
  margin-bottom: 40px;
  justify-items: center;
}

.qr-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.qr-image {
  width: 180px;
  height: 180px;
  object-fit: contain;
  border-radius: var(--radius-lg, 12px);
  border: 1px solid var(--color-border, #e8e3da);
}

.qr-placeholder {
  width: 180px;
  height: 180px;
  background: var(--color-surface-soft, #f6f2ea);
  border: 2px dashed var(--color-border-strong, #d5cec0);
  border-radius: var(--radius-lg, 12px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: border-color 0.2s, background 0.2s;
}

.qr-placeholder:hover {
  border-color: var(--color-jade, #3f7a63);
  background: var(--color-primary-light, #e8f0eb);
}

.qr-icon {
  font-size: 32px;
}

.qr-label {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text, #2f2a24);
}

.qr-hint {
  font-size: var(--text-xs);
  color: var(--color-text-muted, #999);
}

.qr-name {
  font-size: var(--text-sm);
  color: var(--color-text-secondary, #67645d);
  margin: 0;
}

.qr-admin-upload {
  opacity: 0.5;
  transition: opacity 0.15s;
}
.qr-admin-upload:hover {
  opacity: 1;
}

.support-footer {
  padding-top: 24px;
  border-top: 1px solid var(--color-border, #e8e3da);
}

.support-footer p {
  font-size: var(--text-sm);
  color: var(--color-text-muted, #999);
  line-height: 1.8;
  margin: 0;
}

@media (max-width: 600px) {
  .support-page {
    padding: 24px 16px 100px;
  }

  .support-emoji {
    font-size: 44px;
  }

  .support-hero h1 {
    font-size: var(--text-lg);
  }

  .support-qr-section {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .qr-image,
  .qr-placeholder {
    width: 140px;
    height: 140px;
  }
}
</style>
