const fs = require('fs');
const oldCode = fs.readFileSync('d:/Code/MoodCopilot/frontend/userprofile_master.vue', 'utf8');

const templateStart = oldCode.indexOf('<n-modal v-model:show="showSettingsModal"');
const templateEnd = oldCode.indexOf('</n-modal>', templateStart) + 10;
let templateContent = oldCode.substring(templateStart, templateEnd);
templateContent = templateContent.replace('v-model:show="showSettingsModal"', ':show="show"\n    @update:show="emit(\'update:show\', )"');

// Now add the theme UI
const themeHtml =       <SettingSection title="个性签名" tag="Profile">
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
        <div class="theme-grid">
          <div 
            v-for="t in themeOptions" 
            :key="t.value" 
            class="theme-item" 
            :class="{ active: auth.theme === t.value }"
            :style="{ '--t-primary': t.primary, '--t-bg': t.bg }"
            @click="selectTheme(t.value)"
          >
            <div class="theme-color-box"></div>
            <span class="theme-label">{{ t.label }}</span>
          </div>
        </div>
      </SettingSection>;
templateContent = templateContent.replace(/<SettingSection title="个性签名".*?<\/SettingSection>/s, themeHtml);


let newCode = <template>\n  \n</template>\n\n<script setup lang="ts">\n;

const scriptContent = import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { NModal, NButton, NInput, NSwitch } from 'naive-ui'
import SettingSection from '../SettingSection.vue'
import { authApi, suggestionApi } from '../../api'
import { useAuthStore } from '../../stores/auth'
import { logWarn } from '../../utils/logger'

const themeOptions = [
  { value: 'green', label: '绿意轻盈', primary: '#4a7c62', bg: '#f8f6f1' },
  { value: 'salt-lemon', label: '海盐柠泡', primary: '#67bac6', bg: '#f2fcfe' },
  { value: 'morning-dew', label: '露露晨屿', primary: '#ffb6b9', bg: '#fff5f6' },
  { value: 'cherry-blossom', label: '樱落桃酥', primary: '#ff78ae', bg: '#fff0f7' },
  { value: 'cloud-sky', label: '云屿蓝天', primary: '#4dd0e1', bg: '#f4fdff' },
  { value: 'mint-star', label: '薄荷星梦', primary: '#d8c2f9', bg: '#faf7ff' },
  { value: 'forest-dream', label: '森梦蝶屿', primary: '#346357', bg: '#f4fcf9' },
  { value: 'night-apple', label: '晚风苹果', primary: '#5a7d9a', bg: '#f4f8fb' },
  { value: 'black-rice', label: '黑米潮糕', primary: '#ffb400', bg: '#353533' },
  { value: 'starlight-blue', label: '星光蓝', primary: '#41BBC8', bg: '#FCF9E8' },
  { value: 'bamboo-moon', label: '竹月色', primary: '#6090B8', bg: '#F3D8C3' },
  { value: 'pine-cone', label: '松果褐', primary: '#664B3A', bg: '#DBC6B4' }
]

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
  await auth.fetchProfile()
  editingName.value = auth.displayName ?? ''
  suggestionContent.value = ''
}

async function submitSuggestion() {
  if (!suggestionContent.value.trim()) return
  submittingSuggestion.value = true
  try {
    await suggestionApi.submit(suggestionContent.value.trim())
    window..success('反馈提交成功，感谢你的建议！')
    suggestionContent.value = ''
  } catch (err: any) {
    window..error('提交失败：' + (err.response?.data?.message || err.message))
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
  canvas.style.width = \\px\
  canvas.style.height = \\px\

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

async function selectTheme(themeValue: string) {
  if (auth.theme === themeValue) return
  try {
    await auth.updateSettings(auth.dailyNotifyEnabled, auth.profileNotifyEnabled, themeValue)
  } catch (e) {
    logWarn('profile', '更新主题设置失败', e)
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
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 16px 0;
}
.settings-avatar-preview-wrap {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--color-surface-hover);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border);
}
.settings-avatar-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.settings-avatar-placeholder {
  font-size: 32px;
  color: var(--color-text-light);
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

.theme-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 10px;
}
.theme-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 8px;
  border-radius: 12px;
  border: 2px solid transparent;
  transition: all 0.2s ease;
}
.theme-item:hover {
  background: var(--color-surface-hover);
}
.theme-item.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}
.theme-color-box {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--t-primary) 50%, var(--t-bg) 50%);
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}
.theme-label {
  font-size: 12px;
  color: var(--color-text);
  text-align: center;
}
@media (min-width: 500px) {
  .theme-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
</style>
;

newCode += scriptContent;
fs.writeFileSync('d:/Code/MoodCopilot/frontend/src/components/profile/ProfileSettingsModal.vue', newCode, 'utf8');
