<template>
  <main class="app-shell">
    <AppHeader />
    <div class="settings-page mood-surface">
      <section class="settings-hero">
        <div class="hero-avatar-wrap">
          <button type="button" class="hero-avatar" @click="triggerUpload" aria-label="更换头像">
            <img v-if="auth.avatar" :src="auth.avatar" class="avatar-img" decoding="async" />
            <span v-else class="avatar-placeholder">{{ auth.displayName?.charAt(0) }}</span>
          </button>
          <button type="button" class="avatar-change-btn" @click="triggerUpload">更换头像</button>
        </div>
        <div class="hero-meta">
          <h2 class="settings-title">个人中心</h2>
          <p class="hero-name">{{ auth.displayName || '未命名用户' }}</p>
          <p class="hero-subtitle">慢慢来，照顾好自己，每天都会好一点。</p>
        </div>
        <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp" hidden @change="onFileChange" />

        <n-button quaternary circle style="position: absolute; right: 16px; top: 16px; font-size: 22px; z-index: 10;" @click="showSettingsModal = true" title="设置">⚙️</n-button>
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
        <div class="crop-zoom-row">
          <n-button size="small" @click="zoomOut">−</n-button>
          <span class="crop-zoom-label">缩放</span>
          <n-button size="small" @click="zoomIn">＋</n-button>
        </div>
        <template #action>
          <div class="crop-action-row">
            <n-button @click="showCropModal = false">取消</n-button>
            <n-button type="primary" :loading="uploading" @click="handleCrop">确定</n-button>
          </div>
        </template>
      </n-modal>

      <!-- 我的日记列表 -->
      <section style="margin-top: 20px;">
        <MyDiaryList
          :diaries="diaryStore.myDiaries"
          @select="selectDiary"
          @edit="editDiary"
          @delete="handleDelete"
        />
      </section>

      <!-- Settings Modal -->
      <n-modal v-model:show="showSettingsModal" preset="card" title="设置" style="width: 95%; max-width: 650px; max-height: 85vh;">
        <div style="overflow-y: auto; max-height: calc(85vh - 100px); padding-right: 4px;">

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
              <label class="settings-label">个性签名</label>
              <span class="section-tag">Profile</span>
            </div>
            <div class="settings-row settings-row-signature">
              <n-input
                v-model:value="editingSignature"
                type="textarea"
                :maxlength="160"
                :autosize="{ minRows: 2, maxRows: 4 }"
                placeholder="写一句你希望别人看到的状态（最多160字）"
              />
              <n-button
                size="small"
                type="primary"
                :loading="savingSignature"
                :disabled="(editingSignature ?? '').trim() === (auth.signature ?? '')"
                @click="saveSignature"
                class="save-btn"
              >
                保存签名
              </n-button>
            </div>
            <p v-if="signatureMsg" class="settings-hint">{{ signatureMsg }}</p>
          </section>

          <section class="settings-section">
            <div class="section-head">
              <label class="settings-label">邮箱账号</label>
              <span class="section-tag">Account</span>
            </div>
            <div class="settings-inline-tip">{{ auth.email || '未获取到邮箱信息' }}</div>
            <p class="settings-desc">邮箱账号当前仅用于登录和安全验证，暂不支持直接修改。</p>
          </section>

          <section class="settings-section">
            <div class="section-head">
              <label class="settings-label">提醒与陪伴</label>
              <span class="section-tag">Routine</span>
            </div>
            <div class="settings-row notify-row">
              <div>
                <p class="notify-title">每日跟进通知</p>
                <p class="settings-desc">每天在偏好时段推送一条情绪陪跑通知，计入当日 AI 额度</p>
              </div>
              <n-switch :value="auth.dailyNotifyEnabled" @update:value="toggleNotify" :disabled="toggling" />
            </div>
          </section>

          <section class="settings-section">
            <div class="section-head">
              <p class="settings-label">内测邀请</p>
              <span class="section-tag">Invite</span>
            </div>
            <div class="invite-info">
              <div class="invite-code-box">
                <span class="invite-label">你的邀请码</span>
                <code class="invite-code-value">{{ auth.inviteCode || '暂无' }}</code>
              </div>
              <div class="invite-quota-box">
                <span class="invite-label">剩余邀请名额</span>
                <span class="invite-quota-value">{{ auth.inviteQuota }} 人</span>
              </div>
            </div>
            <p class="settings-desc invite-desc">把这串邀请码发给朋友，他们就能加入内测。每个新用户默认获得 3 个邀请名额。</p>
          </section>

          <section class="settings-section">
            <div class="section-head">
              <p class="settings-label">我的记忆</p>
              <span class="section-tag">Memory</span>
            </div>
            <p class="memory-desc">MoodCopilot 从你的日记和聊天中学习的长期画像，你可以编辑修正或删除不想要的部分。</p>
            <div v-if="memoriesLoading" class="memory-loading">加载中...</div>
            <div v-else-if="memories.length === 0" class="memory-empty">
              MoodCopilot 正在默默观察你，多写点日记或和 AI 聊天吧。
            </div>
            <div v-else class="memory-list">
              <div v-for="m in memories" :key="m.id" class="memory-item">
                <div class="memory-content">
                  <span class="memory-key">{{ m.attributeKey }}</span>
                  <template v-if="editingMemoryId === m.id">
                    <n-input
                      v-model:value="editingMemoryValue"
                      size="small"
                      class="memory-edit-input"
                      :maxlength="500"
                      @keyup.enter="saveMemory(m.id)"
                      @keyup.escape="cancelEditMemory"
                    />
                  </template>
                  <span v-else class="memory-value">{{ m.attributeValue }}</span>
                </div>
                <div class="memory-actions">
                  <template v-if="editingMemoryId === m.id">
                    <n-button size="tiny" text type="primary" :disabled="savingMemoryId === m.id" @click="saveMemory(m.id)">
                      {{ savingMemoryId === m.id ? '...' : '保存' }}
                    </n-button>
                    <n-button size="tiny" text @click="cancelEditMemory">取消</n-button>
                  </template>
                  <template v-else>
                    <n-button size="tiny" text type="info" @click="startEditMemory(m)">编辑</n-button>
                    <n-button size="tiny" text type="error" :disabled="deletingMemoryId === m.id" @click="forgetMemory(m.id)">
                      {{ deletingMemoryId === m.id ? '...' : '✕' }}
                    </n-button>
                  </template>
                </div>
              </div>
            </div>
          </section>

          <section class="settings-section danger-zone">
            <div class="section-head">
              <p class="settings-label">账户安全</p>
              <span class="section-tag">Security</span>
            </div>
            <p class="settings-desc">修改密码前会向当前账号邮箱发送验证码，验证通过后才会生效。</p>
            <div class="password-change-panel">
              <div class="password-row-inline">
                <n-input
                  v-model:value="oldPassword"
                  type="password"
                  show-password-on="click"
                  :maxlength="64"
                  placeholder="输入当前密码"
                />
              </div>
              <div class="password-row-inline">
                <n-input
                  v-model:value="newPassword"
                  type="password"
                  show-password-on="click"
                  :maxlength="64"
                  placeholder="输入新密码（至少6位）"
                />
              </div>
              <div class="password-row-inline">
                <n-input
                  v-model:value="confirmNewPassword"
                  type="password"
                  show-password-on="click"
                  :maxlength="64"
                  placeholder="再次输入新密码"
                />
              </div>
              <div class="password-row-inline password-code-row">
                <n-input
                  v-model:value="passwordVerificationCode"
                  :maxlength="6"
                  placeholder="输入邮箱验证码"
                />
                <n-button
                  :disabled="passwordCodeCountdown > 0"
                  :loading="sendingPasswordCode"
                  @click="sendPasswordCode"
                >
                  {{ passwordCodeCountdown > 0 ? `${passwordCodeCountdown}s` : '发送验证码' }}
                </n-button>
              </div>
              <n-button
                type="primary"
                :loading="changingPassword"
                :disabled="!oldPassword.trim() || !newPassword.trim() || !confirmNewPassword.trim() || !passwordVerificationCode.trim()"
                @click="submitPasswordChange"
              >
                确认修改密码
              </n-button>
              <p v-if="passwordMsg" class="settings-hint">{{ passwordMsg }}</p>
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
      </n-modal>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { NInput, NButton, NSwitch, NModal } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import MyDiaryList from '../components/MyDiaryList.vue'
import { useAuthStore } from '../stores/auth'
import { useDiaryStore, type Diary } from '../stores/diary'
import { memoryApi } from '../api'

const router = useRouter()
const auth = useAuthStore()
const diaryStore = useDiaryStore()

const showSettingsModal = ref(false)

// ---- diary list handlers ----
function selectDiary(diary: Diary) {
  router.push(`/diary/${diary.id}`)
}

function editDiary(diary: Diary) {
  router.push(`/diary/${diary.id}?edit=1`)
}

async function handleDelete(diary: Diary) {
  if (confirm('确定删除这篇日记吗？')) {
    await diaryStore.deleteDiary(diary.id)
  }
}
// ---- end diary list handlers ----

// ---- memory management ----
interface MemoryItem { id: number; attributeKey: string; attributeValue: string }
const memories = ref<MemoryItem[]>([])
const memoriesLoading = ref(false)
const deletingMemoryId = ref<number | null>(null)
const editingMemoryId = ref<number | null>(null)
const editingMemoryValue = ref('')
const savingMemoryId = ref<number | null>(null)

async function loadMemories() {
  memoriesLoading.value = true
  try {
    const res = await memoryApi.getAll()
    memories.value = (res.data.data ?? []) as MemoryItem[]
  } catch { memories.value = [] }
  finally { memoriesLoading.value = false }
}

async function forgetMemory(id: number) {
  deletingMemoryId.value = id
  try {
    await memoryApi.forget(id)
    memories.value = memories.value.filter(m => m.id !== id)
  } catch { /* ignore */ }
  finally { deletingMemoryId.value = null }
}

function startEditMemory(m: MemoryItem) {
  editingMemoryId.value = m.id
  editingMemoryValue.value = m.attributeValue
}

async function saveMemory(id: number) {
  if (!editingMemoryValue.value.trim()) return
  savingMemoryId.value = id
  try {
    await memoryApi.update(id, { attributeValue: editingMemoryValue.value.trim() })
    const idx = memories.value.findIndex(m => m.id === id)
    if (idx !== -1) {
      memories.value[idx] = { ...memories.value[idx], attributeValue: editingMemoryValue.value.trim() }
    }
    editingMemoryId.value = null
    editingMemoryValue.value = ''
  } catch { /* ignore */ }
  finally { savingMemoryId.value = null }
}

function cancelEditMemory() {
  editingMemoryId.value = null
  editingMemoryValue.value = ''
}
// ---- end memory management ----

const fileInput = ref<HTMLInputElement | null>(null)
const editingName = ref('')
const editingSignature = ref('')
const savingName = ref(false)
const savingSignature = ref(false)
const nameMsg = ref('')
const signatureMsg = ref('')
const uploadMsg = ref('')
const uploading = ref(false)
const toggling = ref(false)
const oldPassword = ref('')
const newPassword = ref('')
const confirmNewPassword = ref('')
const passwordVerificationCode = ref('')
const sendingPasswordCode = ref(false)
const changingPassword = ref(false)
const passwordCodeCountdown = ref(0)
const passwordMsg = ref('')
let passwordCodeTimer: number | null = null

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
let drawRafId: number | null = null

onMounted(async () => {
  await auth.fetchProfile()
  editingName.value = auth.displayName ?? ''
  editingSignature.value = auth.signature ?? ''
  loadMemories()
  diaryStore.fetchDiaries()
})

function triggerUpload() {
  fileInput.value?.click()
}

async function onFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    uploadMsg.value = '文件大小不能超过 10MB'
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

function scheduleDrawCrop() {
  if (drawRafId != null) return
  drawRafId = window.requestAnimationFrame(() => {
    drawRafId = null
    drawCrop()
  })
}

function zoomOut() {
  cropScale = Math.max(0.2, cropScale - 0.1)
  scheduleDrawCrop()
}

function zoomIn() {
  cropScale = Math.min(5, cropScale + 0.1)
  scheduleDrawCrop()
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
  scheduleDrawCrop()
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
  scheduleDrawCrop()
}

function onWheel(e: WheelEvent) {
  cropScale = Math.max(0.2, Math.min(5, cropScale + (e.deltaY > 0 ? -0.1 : 0.1)))
  scheduleDrawCrop()
}

onBeforeUnmount(() => {
  if (drawRafId != null) {
    window.cancelAnimationFrame(drawRafId)
    drawRafId = null
  }
  if (passwordCodeTimer != null) {
    window.clearInterval(passwordCodeTimer)
    passwordCodeTimer = null
  }
})

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
      uploadMsg.value = '头像已更新'
      showCropModal.value = false
    } catch {
      uploadMsg.value = '上传失败'
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

async function saveSignature() {
  savingSignature.value = true
  signatureMsg.value = ''
  try {
    await auth.updateProfile(undefined, undefined, editingSignature.value.trim())
    editingSignature.value = auth.signature ?? ''
    signatureMsg.value = '个性签名已更新'
  } catch {
    signatureMsg.value = '保存失败'
  }
  savingSignature.value = false
}

async function toggleNotify(val: boolean) {
  toggling.value = true
  try {
    await auth.updateSettings(val)
  } catch { /* ignore */ }
  toggling.value = false
}

async function sendPasswordCode() {
  if (passwordCodeCountdown.value > 0 || sendingPasswordCode.value) return
  passwordMsg.value = ''
  sendingPasswordCode.value = true
  try {
    await auth.sendPasswordChangeCode()
    passwordCodeCountdown.value = 60
    if (passwordCodeTimer != null) {
      window.clearInterval(passwordCodeTimer)
    }
    passwordCodeTimer = window.setInterval(() => {
      passwordCodeCountdown.value -= 1
      if (passwordCodeCountdown.value <= 0 && passwordCodeTimer != null) {
        window.clearInterval(passwordCodeTimer)
        passwordCodeTimer = null
      }
    }, 1000)
    passwordMsg.value = '验证码已发送到你的注册邮箱'
  } catch (e: any) {
    passwordMsg.value = e?.response?.data?.message || '验证码发送失败'
  } finally {
    sendingPasswordCode.value = false
  }
}

async function submitPasswordChange() {
  if (!oldPassword.value.trim()) {
    passwordMsg.value = '请输入当前密码'
    return
  }
  if (newPassword.value.trim().length < 6) {
    passwordMsg.value = '新密码至少 6 位'
    return
  }
  if (!confirmNewPassword.value.trim()) {
    passwordMsg.value = '请再次输入新密码'
    return
  }
  if (newPassword.value.trim() !== confirmNewPassword.value.trim()) {
    passwordMsg.value = '两次输入的新密码不一致'
    return
  }
  if (!passwordVerificationCode.value.trim()) {
    passwordMsg.value = '请输入验证码'
    return
  }

  changingPassword.value = true
  passwordMsg.value = ''
  try {
    await auth.changePassword(
      oldPassword.value.trim(),
      newPassword.value.trim(),
      confirmNewPassword.value.trim(),
      passwordVerificationCode.value.trim(),
    )
    oldPassword.value = ''
    newPassword.value = ''
    confirmNewPassword.value = ''
    passwordVerificationCode.value = ''
    auth.logout()
    await router.push('/login')
  } catch (e: any) {
    passwordMsg.value = e?.response?.data?.message || '密码修改失败'
  } finally {
    changingPassword.value = false
  }
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
    radial-gradient(120% 120% at 0% 0%, var(--color-accent-bg) 0%, transparent 44%),
    linear-gradient(160deg, var(--color-surface) 0%, var(--color-surface-soft) 46%, var(--color-bg) 100%);
  border: 1px solid color-mix(in srgb, var(--color-border-strong) 24%, transparent 76%);
}

.settings-hero {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 14px 14px 18px;
  border-radius: 20px;
  background: color-mix(in srgb, var(--color-surface) 72%, transparent 28%);
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--color-surface) 70%, transparent 30%);
}

.hero-avatar-wrap {
  display: grid;
  justify-items: center;
  gap: 8px;
}

.hero-avatar {
  position: relative;
  width: 84px;
  height: 84px;
  padding: 0;
  border-radius: 999px;
  border: 2px solid color-mix(in srgb, var(--color-primary) 28%, transparent 72%);
  background: linear-gradient(135deg, var(--color-primary), color-mix(in srgb, var(--color-primary) 70%, white 30%));
  cursor: pointer;
  display: grid;
  place-items: center;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.hero-avatar:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 20px color-mix(in srgb, var(--color-primary) 18%, transparent 82%);
}

.hero-avatar:focus-visible,
.avatar-change-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  font-size: 34px;
  font-weight: 700;
  color: #fff;
}

.avatar-change-btn {
  border: 1px solid color-mix(in srgb, var(--color-primary) 35%, transparent 65%);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.avatar-change-btn:hover {
  background: var(--color-primary-light);
  border-color: color-mix(in srgb, var(--color-primary) 50%, transparent 50%);
  color: var(--color-primary-hover);
}

.hero-meta {
  flex: 1;
  min-width: 0;
}

.settings-title {
  margin: 0;
  font-size: 22px;
  line-height: 1.2;
  color: var(--color-text);
}

.hero-name {
  margin: 6px 0 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.hero-subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.settings-section {
  margin-top: 14px;
  padding: 16px;
  border-radius: 18px;
  background: var(--color-surface);
  border: 1px solid color-mix(in srgb, var(--color-border-strong) 20%, transparent 80%);
  box-shadow: 0 10px 24px color-mix(in srgb, var(--color-text) 8%, transparent 92%);
  content-visibility: auto;
  contain-intrinsic-size: 180px;
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
  color: var(--color-text);
}

.section-tag {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-primary);
  background: var(--color-primary-light);
  border: 1px solid color-mix(in srgb, var(--color-primary) 18%, transparent 82%);
  border-radius: 999px;
  padding: 2px 9px;
}

.settings-inline-tip {
  margin-top: 10px;
  font-size: 13px;
  color: var(--color-text-secondary);
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
  color: var(--color-text);
}

.settings-desc {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.settings-hint {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--color-primary);
  background: var(--color-primary-light);
  border-radius: 10px;
  padding: 7px 10px;
}

.invite-info {
  margin-top: 10px;
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}

.invite-code-box,
.invite-quota-box {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--color-primary-light);
  border: 1px solid color-mix(in srgb, var(--color-primary) 16%, transparent 84%);
  flex: 1;
  min-width: 140px;
}

.invite-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.invite-code-value {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--color-text);
  background: none;
  padding: 0;
}

.invite-quota-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text);
}

.invite-desc {
  margin-top: 10px;
}

.memory-desc {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.memory-loading,
.memory-empty {
  margin-top: 10px;
  font-size: 13px;
  color: var(--color-text-muted);
}

.memory-list {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

.memory-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--color-surface-soft);
  border: 1px solid color-mix(in srgb, var(--color-border-strong) 14%, transparent 86%);
}

.memory-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
}

.memory-key {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-primary);
  background: var(--color-primary-light);
  border-radius: 6px;
  padding: 2px 8px;
  white-space: nowrap;
}

.memory-value {
  font-size: 13px;
  color: var(--color-text);
  line-height: 1.5;
  word-break: break-word;
}

.memory-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.memory-edit-input {
  flex: 1;
  min-width: 0;
}

.password-change-panel {
  margin-top: 10px;
  display: grid;
  gap: 10px;
}

.password-row-inline {
  display: flex;
  align-items: center;
  gap: 10px;
}

.password-row-inline :deep(.n-input) {
  flex: 1;
}

.password-code-row .n-button {
  min-width: 108px;
}

.danger-zone {
  background: linear-gradient(180deg, var(--color-surface) 0%, var(--color-accent-bg) 100%);
  border-color: color-mix(in srgb, var(--color-accent) 20%, transparent 80%);
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

  .avatar-change-btn {
    font-size: 11px;
    padding: 2px 9px;
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

  .password-row-inline {
    flex-direction: column;
    align-items: stretch;
  }

  .password-code-row .n-button {
    width: 100%;
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

  .memory-item {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .memory-content {
    flex-direction: column;
    gap: 4px;
  }

  .memory-key {
    align-self: flex-start;
  }

  .memory-edit-input {
    width: 100%;
  }

  .memory-actions {
    justify-content: flex-end;
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

.crop-zoom-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 10px;
}

.crop-zoom-label {
  font-size: 13px;
  color: var(--color-text-muted);
}

.crop-action-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
