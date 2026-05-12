<template>
  <main class="app-shell">
    <AppHeader />
    <div class="settings-page mood-surface">
      <section class="settings-hero">
        <div class="hero-avatar" @click="triggerUpload">
          <img v-if="auth.avatar" :src="auth.avatar" class="avatar-img" />
          <span v-else class="avatar-placeholder">{{ auth.displayName?.charAt(0) }}</span>
          <span class="avatar-edit-tip">更换</span>
        </div>
        <div class="hero-meta">
          <h2 class="settings-title">个人中心</h2>
          <p class="hero-name">{{ auth.displayName || '未命名用户' }}</p>
          <p class="hero-subtitle">慢慢来，照顾好自己，每天都会好一点。</p>
        </div>
        <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp" hidden @change="onFileChange" />
      </section>

      <section class="settings-section">
        <div class="section-head">
          <p class="settings-label">头像设置</p>
          <span class="section-tag">Profile</span>
        </div>
        <div class="settings-inline-tip">点击上方头像即可更换，支持 JPG/PNG/WEBP，最大 512KB。</div>
        <p v-if="uploadMsg" class="settings-hint">{{ uploadMsg }}</p>
      </section>

      <section class="settings-section">
        <div class="section-head">
          <label class="settings-label">用户名</label>
          <span class="section-tag">Identity</span>
        </div>
        <div class="settings-row">
          <n-input
            v-model:value="editingName"
            :disabled="savingName"
            placeholder="输入新用户名"
            @keyup.enter="saveName"
          />
          <n-button size="small" type="primary" :disabled="!editingName.trim() || editingName === auth.displayName || savingName" @click="saveName" class="save-btn">
            保存
          </n-button>
        </div>
        <p v-if="nameMsg" class="settings-hint">{{ nameMsg }}</p>
      </section>

      <section class="settings-section">
        <div class="section-head">
          <label class="settings-label">提醒与陪伴</label>
          <span class="section-tag">Routine</span>
        </div>
        <div class="settings-row notify-row">
          <div>
            <p class="notify-title">每日跟进通知</p>
            <p class="settings-desc">每天早上 6:00 推送一条情绪陪跑通知，计入当日 AI 额度</p>
          </div>
          <n-switch :value="auth.dailyNotifyEnabled" @update:value="toggleNotify" :disabled="toggling" />
        </div>
      </section>

      <section class="settings-section danger-zone">
        <div class="section-head">
          <p class="settings-label">账户操作</p>
          <span class="section-tag">Security</span>
        </div>
        <n-button type="error" @click="handleLogout" block>退出登录</n-button>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NInput, NButton, NSwitch } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const fileInput = ref<HTMLInputElement | null>(null)
const editingName = ref('')
const savingName = ref(false)
const nameMsg = ref('')
const uploadMsg = ref('')
const toggling = ref(false)

onMounted(async () => {
  await auth.fetchProfile()
  editingName.value = auth.displayName ?? ''
})

function triggerUpload() {
  fileInput.value?.click()
}

async function onFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 512 * 1024) {
    uploadMsg.value = '文件大小不能超过 512KB'
    return
  }
  uploadMsg.value = ''
  try {
    const res = await auth.uploadAvatar(file)
    uploadMsg.value = '头像已更新'
  } catch {
    uploadMsg.value = '上传失败'
  }
}

async function saveName() {
  const name = editingName.value.trim()
  if (!name || name === auth.displayName) return
  savingName.value = true
  nameMsg.value = ''
  try {
    await auth.updateProfile(name, undefined)
    nameMsg.value = '用户名已更新'
  } catch {
    nameMsg.value = '保存失败'
  }
  savingName.value = false
}

async function toggleNotify(val: boolean) {
  toggling.value = true
  try {
    await auth.updateSettings(val)
  } catch { /* ignore */ }
  toggling.value = false
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.settings-page {
  position: relative;
  max-width: 760px;
  margin: 0 auto 96px;
  padding: 22px 18px 14px;
  border-radius: 28px;
  background:
    radial-gradient(120% 120% at 0% 0%, rgba(228, 205, 180, 0.26) 0%, rgba(228, 205, 180, 0) 44%),
    linear-gradient(160deg, #f6eee5 0%, #f5efe7 46%, #f2ebe2 100%);
  border: 1px solid rgba(150, 130, 110, 0.24);
}

.settings-hero {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 14px 14px 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.hero-avatar {
  position: relative;
  width: 84px;
  height: 84px;
  border-radius: 999px;
  border: 2px solid rgba(67, 102, 76, 0.28);
  background: #eff3ec;
  cursor: pointer;
  display: grid;
  place-items: center;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.hero-avatar:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 20px rgba(54, 74, 60, 0.18);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  font-size: 34px;
  font-weight: 700;
  color: #335444;
}

.avatar-edit-tip {
  position: absolute;
  right: 0;
  bottom: 0;
  transform: translate(8%, 14%);
  font-size: 12px;
  font-weight: 700;
  color: #f5f7f5;
  background: #496c58;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.64);
}

.hero-meta {
  flex: 1;
  min-width: 0;
}

.settings-title {
  margin: 0;
  font-size: 22px;
  line-height: 1.2;
  color: #2f2a24;
}

.hero-name {
  margin: 6px 0 0;
  font-size: 16px;
  font-weight: 700;
  color: #3d5247;
}

.hero-subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  color: #706558;
}

.settings-section {
  margin-top: 14px;
  padding: 16px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid rgba(162, 142, 123, 0.2);
  box-shadow: 0 10px 24px rgba(94, 70, 50, 0.08);
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.settings-label {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #463e35;
}

.section-tag {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #446454;
  background: #edf4ef;
  border: 1px solid rgba(68, 100, 84, 0.18);
  border-radius: 999px;
  padding: 2px 9px;
}

.settings-inline-tip {
  margin-top: 10px;
  font-size: 13px;
  color: #6f6155;
  line-height: 1.6;
}

.settings-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.settings-row :deep(.n-input) {
  flex: 1;
}

.save-btn {
  min-width: 74px;
}

.notify-row {
  align-items: flex-start;
  justify-content: space-between;
}

.notify-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #2f443a;
}

.settings-desc {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #6f6256;
}

.settings-hint {
  margin: 10px 0 0;
  font-size: 13px;
  color: #40624f;
  background: #edf6f0;
  border-radius: 10px;
  padding: 7px 10px;
}

.danger-zone {
  background: linear-gradient(180deg, #ffffff 0%, #fff8f8 100%);
  border-color: rgba(181, 90, 90, 0.2);
}

@media (max-width: 640px) {
  .settings-page {
    margin-bottom: 88px;
    padding: 16px 12px 10px;
    border-radius: 22px;
  }

  .settings-hero {
    padding: 12px;
    gap: 12px;
  }

  .hero-avatar {
    width: 70px;
    height: 70px;
  }

  .avatar-placeholder {
    font-size: 28px;
  }

  .settings-title {
    font-size: 20px;
  }

  .hero-name {
    font-size: 15px;
  }

  .settings-row {
    flex-direction: column;
    align-items: stretch;
  }

  .save-btn {
    width: 100%;
  }

  .notify-row {
    flex-direction: row;
    gap: 10px;
  }

  .settings-section {
    padding: 14px;
  }
}
</style>
