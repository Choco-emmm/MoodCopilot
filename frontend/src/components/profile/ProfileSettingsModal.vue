<template>
  <n-modal :show="show"
    @update:show="emit('update:show', $event)"
    preset="card"
    class="settings-modal"
    style="width: 520px; max-width: 90vw; border-radius: 20px;"
    :bordered="false"
    :auto-focus="false"
  >
    <template #header>
      <div style="text-align: center; font-weight: bold; font-size: 18px;">系统设置</div>
    </template>
    <div class="settings-modal-scroll">
      <div class="settings-avatar-wrap">
        <div style="display: flex; align-items: center; gap: 16px;">
          <div class="settings-avatar-preview-wrap">
            <img v-if="auth.avatar" :src="auth.avatar" class="settings-avatar-preview" decoding="async" />
            <span v-else class="settings-avatar-placeholder">{{ auth.displayName?.charAt(0) || '我' }}</span>
          </div>
          <div style="font-size: 14px; color: var(--color-text); font-weight: 500;">{{ auth.displayName }}</div>
        </div>
        <button class="upload-btn" @click="triggerUpload">更换头像</button>
        <input type="file" ref="fileInput" accept="image/jpeg,image/png,image/webp" style="display: none" @change="onFileChange" />
      </div>

      <SettingSection title="用户名" tag="Identity">
        <div class="settings-row" style="flex-direction: column; align-items: stretch; gap: 8px;">
          <n-input
            v-model:value="editingName"
            placeholder="设置你的用户名"
            :maxlength="20"
            @blur="checkEditingName"
          />
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <span style="font-size: 12px; color: var(--color-text-light);">
              当前可用改名次数：{{ auth.remainingNameChanges ?? 0 }}
            </span>
            <n-button
              size="small"
              type="primary"
              secondary
              :loading="savingName"
              :disabled="!editingName.trim() || editingName === auth.displayName || savingName || auth.remainingNameChanges <= 0"
              @click="saveName"
              class="save-btn"
            >
              保存修改
            </n-button>
          </div>
        </div>
        <p v-if="nameMsg" class="settings-hint" :style="{ color: nameMsg === '用户名已更新' ? 'var(--color-success)' : 'var(--color-error)' }">
          {{ nameMsg }}
        </p>
      </SettingSection>

      <SettingSection title="个性签名" tag="Profile">
        <div class="settings-row settings-row-signature" style="flex-direction: column; align-items: stretch; gap: 8px;">
          <n-input
            v-model:value="editingSignature"
            type="textarea"
            :maxlength="160"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="写一句你希望别人看到的状态（最多160字）"
          />
          <div style="display: flex; justify-content: flex-end;">
            <n-button
              size="small"
              type="primary"
              secondary
              :loading="savingSignature"
              :disabled="(editingSignature ?? '').trim() === (auth.signature ?? '')"
              @click="saveSignature"
              class="save-btn"
            >
              保存签名
            </n-button>
          </div>
        </div>
        <p v-if="signatureMsg" class="settings-hint">{{ signatureMsg }}</p>
      </SettingSection>

      <SettingSection title="主题外观" tag="Theme">
        <!-- 模式切换 -->
        <div class="theme-mode-row">
          <button
            v-for="mode in themeModeOptions"
            :key="mode.value"
            type="button"
            :class="['theme-mode-btn', { active: auth.themeMode === mode.value }]"
            @click="selectThemeMode(mode.value)"
          >
            {{ mode.label }}
          </button>
        </div>

        <button type="button" class="theme-toggle-btn" @click="themeExpanded = !themeExpanded">
          主题配色 {{ themeExpanded ? '▾' : '▸' }}
        </button>

        <template v-if="themeExpanded">
          <!-- 日间主题 -->
          <p class="theme-section-label">☀️ 日间主题</p>
        <div class="theme-grid">
          <div
            v-for="t in lightThemeOptions"
            :key="t.value"
            class="theme-item"
            :class="{ active: auth.lightTheme === t.value || (!auth.lightTheme && t.value === 'green') }"
            :style="{ '--t-primary': t.primary, '--t-accent': t.accent, '--t-bg': t.bg, '--t-surface': t.surface }"
            @click="selectLightTheme(t.value)"
          >
            <div class="theme-preview">
              <div class="theme-preview-bg">
                <div class="theme-preview-swatch" style="left: 6px; top: 6px; width: 18px; height: 18px; border-radius: 50%; background: var(--t-primary);"></div>
                <div class="theme-preview-swatch" style="right: 6px; top: 8px; width: 10px; height: 10px; border-radius: 3px; background: var(--t-accent); opacity: 0.8;"></div>
                <div class="theme-preview-bar" style="left: 6px; bottom: 8px; width: 24px; height: 3px; border-radius: 2px; background: var(--t-primary); opacity: 0.3;"></div>
                <div class="theme-preview-bar" style="left: 6px; bottom: 14px; width: 16px; height: 2px; border-radius: 1px; background: var(--t-primary); opacity: 0.15;"></div>
              </div>
            </div>
            <span class="theme-label">{{ t.label }}</span>
            <span v-if="auth.lightTheme === t.value || (!auth.lightTheme && t.value === 'green')" class="theme-check">✓</span>
          </div>
        </div>

        <!-- 夜间主题 -->
        <p class="theme-section-label">🌙 夜间主题</p>
        <div class="theme-grid">
          <div
            v-for="t in darkThemeOptions"
            :key="t.value"
            class="theme-item"
            :class="{ active: auth.darkTheme === t.value || (!auth.darkTheme && t.value === 'minimal-dark') }"
            :style="{ '--t-primary': t.primary, '--t-accent': t.accent, '--t-bg': t.bg, '--t-surface': t.surface }"
            @click="selectDarkTheme(t.value)"
          >
            <div class="theme-preview">
              <div class="theme-preview-bg">
                <div class="theme-preview-swatch" style="left: 6px; top: 6px; width: 18px; height: 18px; border-radius: 50%; background: var(--t-primary);"></div>
                <div class="theme-preview-swatch" style="right: 6px; top: 8px; width: 10px; height: 10px; border-radius: 3px; background: var(--t-accent); opacity: 0.8;"></div>
                <div class="theme-preview-bar" style="left: 6px; bottom: 8px; width: 24px; height: 3px; border-radius: 2px; background: var(--t-primary); opacity: 0.3;"></div>
                <div class="theme-preview-bar" style="left: 6px; bottom: 14px; width: 16px; height: 2px; border-radius: 1px; background: var(--t-primary); opacity: 0.15;"></div>
              </div>
            </div>
            <span class="theme-label">{{ t.label }}</span>
            <span v-if="auth.darkTheme === t.value || (!auth.darkTheme && t.value === 'minimal-dark')" class="theme-check">✓</span>
          </div>
        </div>
        </template>
      </SettingSection>

      <SettingSection title="邮箱账号" tag="Account">
        <div class="settings-inline-tip">{{ auth.email || '未获取到邮箱信息' }}</div>
        <p class="settings-desc">邮箱账号当前仅用于登录和安全验证，暂不支持直接修改。</p>
      </SettingSection>

      <SettingSection title="提醒与陪伴" tag="Routine">
        <div class="settings-row" style="justify-content: space-between;">
          <span style="font-size: 14px; color: var(--color-text);">每日心情日记提醒</span>
          <n-switch
            :value="auth.dailyNotifyEnabled"
            :loading="toggling"
            @update:value="toggleNotify"
          />
        </div>
        <div class="settings-row" style="justify-content: space-between; margin-top: 12px;">
          <span style="font-size: 14px; color: var(--color-text);">长期记忆画像生成通知</span>
          <n-switch
            :value="auth.profileNotifyEnabled"
            :loading="toggling"
            @update:value="toggleProfileNotify"
          />
        </div>
      </SettingSection>

      <SettingSection title="安全设置" tag="Security">
        <div class="settings-row" style="flex-direction: column; align-items: flex-start; gap: 8px;">
          <button class="upload-btn" @click="showPasswordChange = !showPasswordChange" style="margin-top: 8px;">
            {{ showPasswordChange ? '取消修改密码' : '修改登录密码' }}
          </button>
          
          <div v-if="showPasswordChange" style="width: 100%; display: flex; flex-direction: column; gap: 12px; margin-top: 8px; padding: 12px; background: var(--color-surface-hover); border-radius: 8px;">
            <p class="settings-desc">修改密码前会向当前账号邮箱发送验证码，验证通过后才会生效。</p>
            <n-input
              v-model:value="oldPassword"
              type="password"
              placeholder="输入当前密码"
              show-password-on="click"
            />
            <n-input
              v-model:value="newPassword"
              type="password"
              placeholder="输入新密码（至少6位）"
              show-password-on="click"
            />
            <n-input
              v-model:value="confirmNewPassword"
              type="password"
              placeholder="再次输入新密码"
              show-password-on="click"
            />
            <div style="display: flex; gap: 8px;">
              <n-input
                v-model:value="passwordVerificationCode"
                placeholder="输入邮箱验证码"
                style="flex: 1;"
              />
              <n-button
                :disabled="passwordCodeCountdown > 0 || sendingPasswordCode"
                :loading="sendingPasswordCode"
                @click="sendPasswordCode"
              >
                {{ passwordCodeCountdown > 0 ? `${passwordCodeCountdown}s` : '发送验证码' }}
              </n-button>
            </div>
            
            <n-button
              type="primary"
              block
              :loading="changingPassword"
              :disabled="!oldPassword || newPassword.length < 6 || newPassword !== confirmNewPassword || !passwordVerificationCode"
              @click="submitPasswordChange"
            >
              确认修改密码
            </n-button>
            <p v-if="passwordMsg" class="settings-hint" :style="{ color: 'var(--color-error)' }">{{ passwordMsg }}</p>
          </div>
        </div>
      </SettingSection>

      <SettingSection title="请开发者喝杯奶茶" tag="🧋" extraClass="support-donate-section">
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span style="font-size: 13px; color: var(--color-text-light);">觉得 MoodCopilot 不错？支持一下独立开发者吧！</span>
          <n-button size="small" type="primary" secondary @click="router.push('/support')">
            去看看 →
          </n-button>
        </div>
      </SettingSection>

      <SettingSection title="建议与反馈" tag="Feedback">
        <div style="display: flex; flex-direction: column; gap: 12px;">
          <n-input
            v-model:value="suggestionContent"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            placeholder="你在使用中遇到了什么问题？或者有什么想要的新功能？请告诉我们..."
          />
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <n-button v-if="isOwner && auth.isAdmin" size="small" type="warning" secondary @click="emit('open-admin-suggestions')">查看所有用户反馈 (Admin)</n-button>
            <div v-else></div>
            <n-button
              size="small"
              type="primary"
              :loading="submittingSuggestion"
              :disabled="!suggestionContent.trim()"
              @click="submitSuggestion"
            >
              提交反馈
            </n-button>
          </div>
        </div>
      </SettingSection>

      <div style="margin-top: 40px; margin-bottom: 24px;">
        <button class="danger-btn" @click="handleLogout">退出登录</button>
      </div>
    </div>
  </n-modal>

  <!-- 头像裁剪弹窗 -->
  <n-modal v-model:show="showCropModal" preset="card" style="width: 400px; max-width: 90vw;" title="调整头像">
    <div class="crop-area-container">
      <div 
        class="crop-area" 
        ref="cropAreaRef"
        @mousedown="onDragStart"
        @mousemove="onDragMove"
        @mouseup="onDragEnd"
        @mouseleave="onDragEnd"
        @touchstart.prevent="onTouchStart"
        @touchmove.prevent="onTouchMove"
        @touchend="onDragEnd"
        @wheel.prevent="onWheel"
      >
        <canvas ref="cropCanvas" class="crop-canvas"></canvas>
      </div>
      <div class="crop-controls">
        <n-button size="small" @click="zoomOut">−</n-button>
        <n-button size="small" @click="zoomIn">＋</n-button>
      </div>
      <p style="font-size: 12px; color: var(--color-text-light); text-align: center; margin-top: -8px;">
        可拖动调整位置，滑轮缩放
      </p>
      <div style="display: flex; gap: 12px; width: 100%; margin-top: 8px;">
        <n-button block @click="showCropModal = false" style="flex: 1;">取消</n-button>
        <n-button type="primary" block :loading="uploading" @click="handleCrop" style="flex: 1;">确定</n-button>
      </div>
    </div>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NModal, NButton, NInput, NSwitch } from 'naive-ui'
import SettingSection from '../SettingSection.vue'
import { authApi, suggestionApi } from '../../api'
import { useAuthStore } from '../../stores/auth'
import { logWarn } from '../../utils/logger'
import { themeOptions } from '../../constants/theme'

const props = defineProps<{
  show: boolean
  isOwner: boolean
}>()

const emit = defineEmits<{
  (e: 'update:show', val: boolean): void
  (e: 'profile-updated'): void
  (e: 'open-admin-suggestions'): void
}>()

const router = useRouter()
const auth = useAuthStore()
const themeModeOptions = [
  { value: 'auto', label: '跟随系统' },
  { value: 'light', label: '保持白天' },
  { value: 'dark', label: '保持夜间' },
]

const lightThemeOptions = computed(() =>
  themeOptions.filter(t => t.value !== 'black-rice' && t.value !== 'minimal-dark')
)

const darkThemeOptions = computed(() =>
  themeOptions.filter(t => t.value === 'black-rice' || t.value === 'minimal-dark')
)

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
const showPasswordChange = ref(false)
const themeExpanded = ref(false)
let passwordCodeTimer: number | null = null

const suggestionContent = ref('')
const submittingSuggestion = ref(false)

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

watch(() => props.show, (val) => {
  if (!val) return
  hydrateSettingsData()
})

watch(showCropModal, (val) => {
  if (val && cropImg) {
    setTimeout(() => drawCrop(), 150)
  }
})

async function hydrateSettingsData() {
  editingName.value = auth.displayName ?? ''
  editingSignature.value = auth.signature ?? ''
  suggestionContent.value = ''
}

async function submitSuggestion() {
  if (!suggestionContent.value.trim()) return
  submittingSuggestion.value = true
  try {
    await suggestionApi.submit(suggestionContent.value.trim())
    window.$message?.success('反馈提交成功，感谢你的建议！')
    suggestionContent.value = ''
  } catch (err: any) {
    window.$message?.error('提交失败：' + (err.response?.data?.message || err.message))
  } finally {
    submittingSuggestion.value = false
  }
}

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
    cropImageSrc.value = String(ev.target?.result ?? '')
    showCropModal.value = true
    nextTick(() => loadCropImage())
  }
  reader.readAsDataURL(file)

  if (fileInput.value) {
    fileInput.value.value = ''
  }
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

function drawCrop() {
  const canvas = cropCanvas.value
  const area = cropAreaRef.value
  if (!canvas || !area || !cropImg) return

  const size = Math.min(area.clientWidth || 320, 320)
  canvas.width = size
  canvas.height = size
  canvas.style.width = `${size}px`
  canvas.style.height = `${size}px`

  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.clearRect(0, 0, size, size)

  const cssVars = getComputedStyle(document.documentElement)
  const surfaceSoft = cssVars.getPropertyValue('--color-surface-soft').trim() || 'rgba(0, 0, 0, 0.06)'
  const primary = cssVars.getPropertyValue('--color-primary').trim() || 'rgba(0, 0, 0, 0.4)'
  ctx.fillStyle = surfaceSoft
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
  ctx.strokeStyle = primary
  ctx.globalAlpha = 0.35
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

function onDragEnd() {
  dragging = false
}

function onTouchStart(e: TouchEvent) {
  if (e.touches.length !== 1) return
  dragging = true
  dragStartX = e.touches[0].clientX
  dragStartY = e.touches[0].clientY
  lastOffsetX = cropOffsetX
  lastOffsetY = cropOffsetY
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

function handleCrop() {
  if (!cropImg) return

  uploading.value = true
  const outSize = 400
  const offscreen = document.createElement('canvas')
  offscreen.width = outSize
  offscreen.height = outSize
  const ctx = offscreen.getContext('2d')
  if (!ctx) {
    uploading.value = false
    return
  }

  const canvas = cropCanvas.value
  if (!canvas) {
    uploading.value = false
    return
  }

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
    if (!blob) {
      uploading.value = false
      return
    }
    const file = new File([blob], 'avatar.jpg', { type: 'image/jpeg' })
    try {
      await auth.uploadAvatar(file)
      uploadMsg.value = '头像已更新'
      showCropModal.value = false
      emit('profile-updated')
    } catch (e) {
      uploadMsg.value = '上传失败'
      logWarn('profile', '头像上传失败', e)
    } finally {
      uploading.value = false
    }
  }, 'image/jpeg', 0.92)
}

async function checkEditingName() {
  const name = editingName.value.trim()
  if (!name || name === auth.displayName) {
    if (nameMsg.value === '该用户名已被占用' || nameMsg.value === '需为 2-20 位中英文、数字、下划线或横线') nameMsg.value = ''
    return
  }
  if (!/^[a-zA-Z0-9\u4e00-\u9fa5_-]{2,20}$/.test(name)) {
    nameMsg.value = '需为 2-20 位中英文、数字、下划线或横线'
    return
  }
  try {
    const res = await authApi.checkUsername(name)
    if (!res.data.data.available) {
      nameMsg.value = '该用户名已被占用'
    } else {
      if (nameMsg.value === '该用户名已被占用' || nameMsg.value === '需为 2-20 位中英文、数字、下划线或横线') nameMsg.value = ''
    }
  } catch (e) {
    logWarn('profile', '检查用户名可用性失败', name, e)
    nameMsg.value = '需为 2-20 位中英文、数字、下划线或横线'
  }
}

async function saveName() {
  const name = editingName.value.trim()
  if (!name || name === auth.displayName) return
  if (!/^[a-zA-Z0-9\u4e00-\u9fa5_-]{2,20}$/.test(name)) {
    nameMsg.value = '需为 2-20 位中英文、数字、下划线或横线'
    return
  }

  savingName.value = true
  nameMsg.value = ''
  try {
    await auth.updateProfile(name, undefined)
    nameMsg.value = '用户名已更新'
    editingName.value = auth.displayName ?? ''
    emit('profile-updated')
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || ''
    if (msg && msg !== '该用户名已被占用') nameMsg.value = msg
    else if (!msg) nameMsg.value = '保存失败'
  } finally {
    savingName.value = false
  }
}

async function saveSignature() {
  savingSignature.value = true
  signatureMsg.value = ''
  try {
    await auth.updateProfile(undefined, undefined, editingSignature.value.trim())
    editingSignature.value = auth.signature ?? ''
    signatureMsg.value = '个性签名已更新'
    emit('profile-updated')
  } catch (e) {
    logWarn('profile', '保存签名失败', e)
    signatureMsg.value = '保存失败'
  } finally {
    savingSignature.value = false
  }
}

async function toggleNotify(val: boolean) {
  toggling.value = true
  try {
    await auth.updateSettings(val)
  } catch (e) {
    logWarn('profile', '更新通知设置失败', e)
  } finally {
    toggling.value = false
  }
}

async function toggleProfileNotify(val: boolean) {
  toggling.value = true
  try {
    await auth.updateSettings(auth.dailyNotifyEnabled, val, auth.theme)
  } catch (e) {
    logWarn('profile', '更新画像通知设置失败', e)
  } finally {
    toggling.value = false
  }
}

async function selectThemeMode(mode: string) {
  if (auth.themeMode === mode) return
  try {
    await auth.updateSettings(auth.dailyNotifyEnabled, auth.profileNotifyEnabled,
      undefined, mode, undefined, undefined)
  } catch (e) {
    logWarn('profile', '更新主题模式失败', e)
  }
}

async function selectLightTheme(themeValue: string) {
  if (auth.lightTheme === themeValue) return
  try {
    await auth.updateSettings(auth.dailyNotifyEnabled, auth.profileNotifyEnabled,
      undefined, undefined, themeValue, undefined)
  } catch (e) {
    logWarn('profile', '更新日间主题失败', e)
  }
}

async function selectDarkTheme(themeValue: string) {
  if (auth.darkTheme === themeValue) return
  try {
    await auth.updateSettings(auth.dailyNotifyEnabled, auth.profileNotifyEnabled,
      undefined, undefined, undefined, themeValue)
  } catch (e) {
    logWarn('profile', '更新夜间主题失败', e)
  }
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
    emit('update:show', false)
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
  emit('update:show', false)
  auth.logout()
  router.push('/login')
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
</script>

<style scoped>
.settings-modal-scroll {
  overflow-y: auto;
  max-height: 70vh;
  padding-right: 12px;
  margin-right: -12px;
}

.settings-modal-scroll::-webkit-scrollbar {
  width: 6px;
}
.settings-modal-scroll::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 4px;
}
.settings-modal-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.settings-avatar-wrap {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px dashed color-mix(in oklab, var(--color-border) 40%, transparent);
}
.settings-avatar-preview-wrap {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid color-mix(in oklab, var(--color-primary) 20%, transparent);
}

.upload-btn {
  font-size: 13px;
  padding: 6px 14px;
  border-radius: 20px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  font-family: var(--font-body);
}

.upload-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 4%, transparent);
}
.settings-avatar-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.settings-avatar-placeholder {
  font-size: 20px;
  font-family: var(--font-display);
  color: #fff;
}

.settings-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.settings-hint {
  font-size: 12px;
  color: var(--color-text-light);
  margin-top: 6px;
}
.settings-desc {
  font-size: 12px;
  color: var(--color-text-light);
  margin-top: 4px;
  line-height: 1.5;
}
.settings-inline-tip {
  font-size: 14px;
  color: var(--color-text);
  background: var(--color-surface-hover);
  padding: 8px 12px;
  border-radius: 8px;
}
.danger-btn {
  width: 100%;
  background: transparent;
  color: var(--color-accent);
  border: 1px solid color-mix(in oklab, var(--color-accent) 30%, transparent);
  padding: 12px;
  border-radius: 8px;
  font-size: 14.5px;
  cursor: pointer;
  transition: all 0.2s;
}

.danger-btn:hover {
  background: color-mix(in oklab, var(--color-accent) 5%, transparent);
  border-color: var(--color-accent);
}

.crop-area-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}
.crop-area {
  position: relative;
  width: 100%;
  max-width: 320px;
  aspect-ratio: 1;
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  touch-action: none;
  cursor: grab;
}
.crop-area:active {
  cursor: grabbing;
}
.crop-canvas {
  display: block;
  margin: 0 auto;
}
.crop-controls {
  display: flex;
  gap: 12px;
}

.support-donate-section :deep(.n-card) {
  border-color: var(--color-primary-light);
  background: var(--color-surface);
}

.theme-mode-row {
  display: flex;
  gap: 6px;
  margin-bottom: 14px;
}
.theme-mode-btn {
  flex: 1;
  padding: 7px 0;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;
}
.theme-mode-btn:hover {
  border-color: var(--color-border-strong);
  background: var(--color-surface-hover);
}
.theme-mode-btn.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-weight: 600;
}
.theme-toggle-btn {
  display: block;
  width: 100%;
  padding: 6px 0;
  border: none;
  background: none;
  color: var(--color-text-muted);
  font-size: 12px;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: color 0.15s;
}
.theme-toggle-btn:hover {
  color: var(--color-text-secondary);
}
.theme-section-label {
  margin: 16px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.theme-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 10px;
}
.theme-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 10px 6px 8px;
  border-radius: 14px;
  border: 2px solid transparent;
  transition: all 0.25s var(--ease-out, ease);
  position: relative;
}
.theme-item:hover {
  background: var(--color-surface-hover);
  transform: translateY(-1px);
}
.theme-item.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}
.theme-preview {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  padding: 3px;
  background: var(--t-bg);
  box-shadow: 0 2px 10px rgba(0,0,0,0.07), inset 0 0 0 1px rgba(0,0,0,0.04);
  transition: box-shadow 0.25s var(--ease-out, ease), transform 0.25s var(--ease-out, ease);
}
.theme-item:hover .theme-preview {
  box-shadow: 0 4px 14px rgba(0,0,0,0.1), inset 0 0 0 1px rgba(0,0,0,0.06);
  transform: scale(1.05);
}
.theme-item.active .theme-preview {
  box-shadow: 0 2px 12px color-mix(in oklab, var(--t-primary) 30%, transparent), inset 0 0 0 1px color-mix(in oklab, var(--t-primary) 20%, transparent);
}
.theme-preview-bg {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--t-bg);
  position: relative;
  overflow: hidden;
}
.theme-preview-swatch,
.theme-preview-bar {
  position: absolute;
}
.theme-label {
  font-size: 12px;
  color: var(--color-text);
  text-align: center;
  font-weight: 500;
}
.theme-check {
  position: absolute;
  top: 4px;
  right: 8px;
  font-size: 11px;
  color: var(--color-primary);
  font-weight: 700;
}
@media (min-width: 500px) {
  .theme-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
</style>

<style>
/* Override naive-ui modal default white background */
.settings-modal.n-card {
  background: var(--color-surface) !important;
  border: 1px solid color-mix(in oklab, var(--color-border) 40%, transparent) !important;
  border-radius: 12px !important;
  box-shadow: 0 10px 40px rgba(0,0,0,0.08) !important;
}
.settings-modal.n-card > .n-card-header {
  background: transparent !important;
  padding: 24px 32px 16px !important;
  border-bottom: 1px solid color-mix(in oklab, var(--color-border) 30%, transparent) !important;
}
.settings-modal.n-card > .n-card-header .n-card-header__main {
  font-family: var(--font-display);
  font-size: 1.3rem !important;
  font-weight: 600 !important;
  letter-spacing: 0.02em;
}

/* Fix Naive UI inputs inside settings modal to follow theme colors */
.settings-modal .n-input {
  --n-color: var(--color-surface-hover) !important;
  --n-color-focus: var(--color-surface) !important;
  --n-border: 1px solid var(--color-border) !important;
  --n-border-hover: 1px solid var(--color-primary) !important;
  --n-border-focus: 1px solid var(--color-primary) !important;
  --n-caret-color: var(--color-primary) !important;
  --n-text-color: var(--color-text) !important;
  --n-placeholder-color: var(--color-text-muted) !important;
}
</style>

