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

      <!-- 裁切弹窗 -->
      <n-modal v-model:show="showCropModal" preset="card" title="裁切头像" style="width: 90%; max-width: 420px;" :mask-closable="false">
        <div class="crop-area" ref="cropAreaRef"
          @mousedown="onDragStart" @mousemove="onDragMove" @mouseup="onDragEnd" @mouseleave="onDragEnd"
          @touchstart.prevent="onTouchStart" @touchmove.prevent="onTouchMove" @touchend="onDragEnd"
          @wheel.prevent="onWheel"
        >
          <canvas ref="cropCanvas" class="crop-canvas"></canvas>
        </div>
        <div style="display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 10px;">
          <n-button size="small" @click="cropScale = Math.max(0.2, cropScale - 0.1); drawCrop()">−</n-button>
          <span style="font-size: 13px; color: #666;">缩放</span>
          <n-button size="small" @click="cropScale = Math.min(5, cropScale + 0.1); drawCrop()">＋</n-button>
        </div>
        <template #action>
          <div style="display: flex; justify-content: flex-end; gap: 12px;">
            <n-button @click="showCropModal = false">取消</n-button>
            <n-button type="primary" :loading="uploading" @click="handleCrop">确定</n-button>
          </div>
        </template>
      </n-modal>

      <section class="settings-shortcuts">
        <router-link to="/write" class="shortcut-card">
          <span class="shortcut-kicker">记录</span>
          <strong>继续写日记</strong>
          <span>把今天的状态轻轻放下。</span>
        </router-link>
        <router-link to="/chat" class="shortcut-card">
          <span class="shortcut-kicker">陪伴</span>
          <strong>继续和 AI 聊聊</strong>
          <span>围绕最近情绪继续展开。</span>
        </router-link>
        <router-link to="/report" class="shortcut-card">
          <span class="shortcut-kicker">回顾</span>
          <strong>查看本周报告</strong>
          <span>看看趋势和关键变化。</span>
        </router-link>
      </section>

      <section class="settings-section">
        <div class="section-head">
          <p class="settings-label">头像设置</p>
          <span class="section-tag">Profile</span>
        </div>
        <div class="settings-inline-tip">点击上方头像即可更换，支持 JPG/PNG/WEBP，最大 10MB。上传后可自定义裁切。</div>
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
import { ref, onMounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { NInput, NButton, NSwitch, NModal } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const fileInput = ref<HTMLInputElement | null>(null)
const editingName = ref('')
const savingName = ref(false)
const nameMsg = ref('')
const uploadMsg = ref('')
const uploading = ref(false)
const toggling = ref(false)

const showCropModal = ref(false)
const cropImageSrc = ref('')
const cropCanvas = ref<HTMLCanvasElement | null>(null)
const cropAreaRef = ref<HTMLElement | null>(null)

let cropImg: HTMLImageElement | null = null
let cropScale = 1
let cropOffsetX = 0
let cropOffsetY = 0
let dragging = false
let dragStartX = 0
let dragStartY = 0
let lastOffsetX = 0
let lastOffsetY = 0

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
  if (file.size > 10 * 1024 * 1024) {
    uploadMsg.value = '\u6587\u4ef6\u5927\u5c0f\u4e0d\u80fd\u8d85\u8fc7 10MB'
    return
  }
  uploadMsg.value = ''

  const reader = new FileReader()
  reader.onload = (ev) => {
    cropImageSrc.value = ev.target?.result as string
    showCropModal.value = true
    nextTick(() => loadCropImage())
  }
  reader.readAsDataURL(file)
  if (fileInput.value) fileInput.value.value = ''
}

function loadCropImage() {
  const img = new Image()
  img.onload = () => {
    cropImg = img
    cropScale = 1
    cropOffsetX = 0
    cropOffsetY = 0
    setTimeout(() => drawCrop(), 120)
  }
  img.src = cropImageSrc.value
}

watch(showCropModal, (val) => {
  if (val && cropImg) {
    setTimeout(() => drawCrop(), 150)
  }
})

function drawCrop() {
  const canvas = cropCanvas.value
  const area = cropAreaRef.value
  if (!canvas || !area || !cropImg) return

  const size = Math.min(area.clientWidth || 320, 320)
  canvas.width = size
  canvas.height = size
  canvas.style.width = size + 'px'
  canvas.style.height = size + 'px'

  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, size, size)

  ctx.fillStyle = '#e8e8e8'
  ctx.fillRect(0, 0, size, size)

  const imgW = cropImg.naturalWidth
  const imgH = cropImg.naturalHeight
  const fitScale = size / Math.min(imgW, imgH)
  const drawW = imgW * fitScale * cropScale
  const drawH = imgH * fitScale * cropScale
  const drawX = (size - drawW) / 2 + cropOffsetX
  const drawY = (size - drawH) / 2 + cropOffsetY

  ctx.drawImage(cropImg, drawX, drawY, drawW, drawH)

  ctx.save()
  ctx.strokeStyle = 'rgba(255,255,255,0.8)'
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.arc(size / 2, size / 2, size / 2 - 4, 0, Math.PI * 2)
  ctx.stroke()
  ctx.restore()
}

function onDragStart(e: MouseEvent) {
  dragging = true
  dragStartX = e.clientX
  dragStartY = e.clientY
  lastOffsetX = cropOffsetX
  lastOffsetY = cropOffsetY
}
function onDragMove(e: MouseEvent) {
  if (!dragging) return
  cropOffsetX = lastOffsetX + (e.clientX - dragStartX)
  cropOffsetY = lastOffsetY + (e.clientY - dragStartY)
  drawCrop()
}
function onDragEnd() { dragging = false }

function onTouchStart(e: TouchEvent) {
  if (e.touches.length === 1) {
    dragging = true
    dragStartX = e.touches[0].clientX
    dragStartY = e.touches[0].clientY
    lastOffsetX = cropOffsetX
    lastOffsetY = cropOffsetY
  }
}
function onTouchMove(e: TouchEvent) {
  if (!dragging || e.touches.length !== 1) return
  cropOffsetX = lastOffsetX + (e.touches[0].clientX - dragStartX)
  cropOffsetY = lastOffsetY + (e.touches[0].clientY - dragStartY)
  drawCrop()
}

function onWheel(e: WheelEvent) {
  cropScale = Math.max(0.2, Math.min(5, cropScale + (e.deltaY > 0 ? -0.1 : 0.1)))
  drawCrop()
}

function handleCrop() {
  if (!cropImg) return
  uploading.value = true

  const outSize = 400
  const offscreen = document.createElement('canvas')
  offscreen.width = outSize
  offscreen.height = outSize
  const ctx = offscreen.getContext('2d')!

  const canvas = cropCanvas.value!
  const displaySize = canvas.width
  const scale = outSize / displaySize

  const imgW = cropImg.naturalWidth
  const imgH = cropImg.naturalHeight
  const fitScale = displaySize / Math.min(imgW, imgH)
  const drawW = imgW * fitScale * cropScale * scale
  const drawH = imgH * fitScale * cropScale * scale
  const drawX = (outSize - drawW) / 2 + cropOffsetX * scale
  const drawY = (outSize - drawH) / 2 + cropOffsetY * scale

  ctx.drawImage(cropImg, drawX, drawY, drawW, drawH)

  offscreen.toBlob(async (blob) => {
    if (!blob) { uploading.value = false; return }
    const file = new File([blob], 'avatar.jpg', { type: 'image/jpeg' })
    try {
      await auth.uploadAvatar(file)
      uploadMsg.value = '\u5934\u50cf\u5df2\u66f4\u65b0'
      showCropModal.value = false
    } catch {
      uploadMsg.value = '\u4e0a\u4f20\u5931\u8d25'
    } finally {
      uploading.value = false
    }
  }, 'image/jpeg', 0.92)
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

.settings-shortcuts {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.shortcut-card {
  display: grid;
  gap: 6px;
  padding: 13px 12px;
  border-radius: 14px;
  text-decoration: none;
  color: #3d443d;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.86) 0%, rgba(246, 249, 245, 0.86) 100%);
  border: 1px solid rgba(122, 144, 128, 0.24);
  box-shadow: 0 8px 20px rgba(70, 88, 75, 0.08);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.shortcut-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 26px rgba(63, 92, 74, 0.14);
  border-color: rgba(72, 114, 89, 0.4);
}

.shortcut-kicker {
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #62806e;
  font-weight: 700;
}

.shortcut-card strong {
  font-size: 14px;
  color: #2d4034;
}

.shortcut-card span:last-child {
  font-size: 12px;
  line-height: 1.5;
  color: #6b726a;
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

  .settings-shortcuts {
    grid-template-columns: 1fr;
    gap: 8px;
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

.crop-area {
  display: flex;
  justify-content: center;
  align-items: center;
  background: #2a2a2a;
  border-radius: 12px;
  overflow: hidden;
  cursor: grab;
  touch-action: none;
  user-select: none;
}
.crop-area:active { cursor: grabbing; }
.crop-canvas {
  display: block;
  border-radius: 4px;
}
</style>
