<template>
  <main class="app-shell">
    <AppHeader />
    <div class="settings-page">
      <h2 class="settings-title">个人中心</h2>

      <!-- 头像 -->
      <section class="settings-section">
        <label class="settings-label">头像</label>
        <div class="avatar-upload" @click="triggerUpload">
          <img v-if="auth.avatar" :src="auth.avatar" class="avatar-img" />
          <span v-else class="avatar-placeholder">{{ auth.displayName?.charAt(0) }}</span>
          <span class="avatar-hint">点击更换</span>
          <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp" hidden @change="onFileChange" />
        </div>
        <p v-if="uploadMsg" class="settings-hint">{{ uploadMsg }}</p>
      </section>

      <!-- 用户名 -->
      <section class="settings-section">
        <label class="settings-label">用户名</label>
        <div class="settings-row">
          <n-input
            v-model:value="editingName"
            :disabled="savingName"
            placeholder="输入新用户名"
            @keyup.enter="saveName"
          />
          <n-button size="small" type="primary" :disabled="!editingName.trim() || editingName === auth.displayName || savingName" @click="saveName">
            保存
          </n-button>
        </div>
        <p v-if="nameMsg" class="settings-hint">{{ nameMsg }}</p>
      </section>

      <!-- 每日跟进通知 -->
      <section class="settings-section">
        <div class="settings-row">
          <div>
            <label class="settings-label">每日跟进通知</label>
            <p class="settings-desc">每天早上 6:00 推送一条情绪陪跑通知，计入当日 AI 额度</p>
          </div>
          <n-switch :value="auth.dailyNotifyEnabled" @update:value="toggleNotify" :disabled="toggling" />
        </div>
      </section>

      <!-- 退出 -->
      <section class="settings-section">
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
import { authApi } from '../api'

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
