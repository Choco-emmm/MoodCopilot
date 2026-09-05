<template>
  <n-modal :show="show"
    @update:show="emit('update:show', $event)"
    preset="card"
    class="settings-modal modal-card"
    :bordered="false"
    :auto-focus="false"
  >
    <template #header>
      <div class="modal-title">系统设置</div>
    </template>
    <div class="settings-modal-scroll">
      <div class="settings-avatar-wrap">
        <div class="flex-center gap-16">
          <div class="settings-avatar-preview-wrap">
            <img v-if="auth.avatar" :src="auth.avatar" class="settings-avatar-preview" decoding="async" />
            <span v-else class="settings-avatar-placeholder">{{ auth.displayName?.charAt(0) || '我' }}</span>
          </div>
          <div class="user-name">{{ auth.displayName }}</div>
        </div>
        <button class="upload-btn" @click="triggerUpload">更换头像</button>
        <p v-if="uploadMsg" class="upload-msg" :style="{ color: uploadMsg === '头像已更新' ? 'var(--color-success)' : 'var(--color-error)' }">{{ uploadMsg }}</p>
        <input type="file" ref="fileInput" accept="image/jpeg,image/png,image/webp" class="hidden" @change="onFileChange" />
      </div>

      <SettingSection title="用户名" tag="Identity">
        <div class="settings-row flex-col-stretch gap-8">
          <n-input
            v-model:value="editingName"
            placeholder="设置你的用户名"
            :maxlength="20"
            @blur="checkEditingName"
          />
          <div class="flex-between-center">
            <span class="text-xs-light">
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
        <div class="settings-row settings-row-signature flex-col-stretch gap-8">
          <n-input
            v-model:value="editingSignature"
            type="textarea"
            :maxlength="160"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="写一句你希望别人看到的状态（最多160字）"
          />
          <div class="flex-end">
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

      <SettingSection title="自定义 MoodCopilot 全局个性" tag="Persona">
        <button type="button" class="persona-toggle" :aria-expanded="personaExpanded" @click="personaExpanded = !personaExpanded">
          <span>调整 MoodCopilot 的身份、语气和回答方式</span>
          <span aria-hidden="true">{{ personaExpanded ? '收起' : '展开' }}</span>
        </button>
        <div v-if="personaExpanded" class="persona-panel">
          <p class="settings-desc">这项设置会影响 MoodCopilot 各项 AI 功能的表达方式，包括聊天、日记分析、事件回顾和报告等。</p>
          <label class="persona-label" for="persona-role">互动身份</label>
          <select id="persona-role" v-model="persona.role" class="persona-select">
            <option v-for="option in personaRoleOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <label class="persona-label">语气</label>
          <div class="persona-options">
            <label v-for="option in personaToneOptions" :key="option.value" class="persona-option">
              <input v-model="persona.tone" type="checkbox" :value="option.value" />
              <span>{{ option.label }}</span>
            </label>
          </div>
          <label class="persona-label" for="persona-custom-tone">自定义语气</label>
          <n-input id="persona-custom-tone" v-model:value="persona.customTone" :maxlength="160"
            placeholder="例如：冷静务实，像可靠的前辈" />
          <p class="settings-desc">用一句话补充预设之外的表达感觉，只影响语气。</p>
          <label class="persona-label">回答方式</label>
          <div class="persona-options">
            <label v-for="option in personaBehaviorOptions" :key="option.value" class="persona-option">
              <input v-model="persona.behaviorFlags" type="checkbox" :value="option.value" />
              <span>{{ option.label }}</span>
            </label>
          </div>
          <label class="persona-label" for="persona-response-style">自定义回答方式</label>
          <n-input id="persona-response-style" v-model:value="persona.customResponseStyle" type="textarea" :maxlength="800"
            placeholder="例如：按“事实、判断、建议”分开说明，并明确标注不确定信息" />
          <p class="settings-desc">只影响回答组织方式。</p>
          <div class="persona-actions">
            <n-button type="primary" secondary :loading="savingPersona" @click="savePersona">保存 AI 个性</n-button>
            <n-button :disabled="savingPersona" @click="resetPersona">恢复默认</n-button>
          </div>
          <p v-if="personaMsg" class="settings-hint" :class="{ 'persona-error': personaError }">{{ personaMsg }}</p>
          <div class="persona-preview">
            <div class="persona-preview-head">
              <span class="persona-label">效果预览</span>
              <n-button size="small" secondary :loading="previewingPersona" :disabled="previewingPersona" @click="previewPersona">试试这个设置</n-button>
            </div>
              <div class="persona-preview-controls">
                <n-input v-model:value="personaSampleMessage" :maxlength="300" placeholder="输入一个示例问题" />
                <select v-model="personaPreviewModel" class="persona-preview-model" aria-label="预览模式">
                  <option value="0">普通对话</option>
                  <option value="1">深度思考</option>
                </select>
              </div>
            <div v-if="personaPreview" class="persona-preview-result md-content" v-html="personaPreviewHtml"></div>
            <p class="settings-desc">预览不会读取或写入你的日记、记忆、事件和聊天记录。</p>
          </div>
        </div>
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
          
          <template v-for="group in lightThemeGroups" :key="group.name">
            <div class="theme-category-title">{{ group.name }}</div>
            <div class="theme-grid">
              <div
                v-for="t in group.themes"
                :key="t.value"
                class="theme-item"
                :class="{ active: auth.lightTheme === t.value || (!auth.lightTheme && t.value === 'green') }"
                :style="{ '--t-primary': t.primary, '--t-accent': t.accent, '--t-bg': t.bg, '--t-surface': t.surface }"
                @click="selectLightTheme(t.value)"
              >
                <div class="theme-preview">
                  <div class="theme-preview-bg">
                    <div class="theme-preview-swatch theme-swatch-1"></div>
                    <div class="theme-preview-swatch theme-swatch-2"></div>
                    <div class="theme-preview-bar theme-bar-1"></div>
                    <div class="theme-preview-bar theme-bar-2"></div>
                  </div>
                </div>
                <span class="theme-label">{{ t.label }}</span>
                <span v-if="auth.lightTheme === t.value || (!auth.lightTheme && t.value === 'green')" class="theme-check">✓</span>
              </div>
            </div>
          </template>

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
                <div class="theme-preview-swatch theme-swatch-1"></div>
                <div class="theme-preview-swatch theme-swatch-2"></div>
                <div class="theme-preview-bar theme-bar-1"></div>
                <div class="theme-preview-bar theme-bar-2"></div>
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
        <div class="settings-row justify-between">
          <span class="text-sm">每日心情日记提醒</span>
          <n-switch
            :value="auth.dailyNotifyEnabled"
            :loading="toggling"
            @update:value="toggleNotify"
          />
        </div>
        <div class="settings-row justify-between mt-12">
          <span class="text-sm">长期记忆画像生成通知</span>
          <n-switch
            :value="auth.profileNotifyEnabled"
            :loading="toggling"
            @update:value="toggleProfileNotify"
          />
        </div>
      </SettingSection>

      <SettingSection title="安全设置" tag="Security">
        <div class="settings-row flex-col-start gap-8">
          <button class="upload-btn mt-8" @click="showPasswordChange = !showPasswordChange">
            {{ showPasswordChange ? '取消修改密码' : '修改登录密码' }}
          </button>
          
          <div v-if="showPasswordChange" class="password-panel">
            <p class="settings-desc">修改密码前会向当前账号邮箱发送验证码，验证通过后才会生效。</p>
            <n-input
              v-if="!forgotOldPasswordMode"
              v-model:value="oldPassword"
              type="password"
              placeholder="输入当前密码"
              show-password-on="click"
            />
            <div v-if="!forgotOldPasswordMode" style="text-align: right; margin-top: -6px; margin-bottom: 6px;">
              <a href="javascript:void(0)" @click="forgotOldPasswordMode = true" style="color: var(--color-primary); font-size: 13px; text-decoration: none;">忘记原密码？</a>
            </div>
            <div v-if="forgotOldPasswordMode" style="text-align: right; margin-bottom: 6px;">
              <span style="font-size: 13px; color: var(--color-text-secondary); margin-right: 8px;">将通过注册邮箱验证找回密码</span>
              <a href="javascript:void(0)" @click="forgotOldPasswordMode = false" style="color: var(--color-primary); font-size: 13px; text-decoration: none;">想起原密码了</a>
            </div>
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
            <div class="flex-gap-8">
              <n-input
                v-model:value="passwordVerificationCode"
                placeholder="输入邮箱验证码"
                class="flex-1"
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
              :disabled="(!forgotOldPasswordMode && !oldPassword) || newPassword.length < 6 || newPassword !== confirmNewPassword || !passwordVerificationCode"
              @click="submitPasswordChange"
            >
              确认修改密码
            </n-button>
            <p v-if="passwordMsg" class="settings-hint" :style="{ color: 'var(--color-error)' }">{{ passwordMsg }}</p>
          </div>
        </div>
      </SettingSection>

      <SettingSection title="请开发者喝杯奶茶" tag="🧋" extraClass="support-donate-section">
        <div class="flex-between-center">
          <span class="text-sm-light">觉得 MoodCopilot 不错？支持一下独立开发者吧！</span>
          <n-button size="small" type="primary" secondary @click="router.push('/support')">
            去看看 →
          </n-button>
        </div>
      </SettingSection>

      <SettingSection title="建议与反馈" tag="Feedback">
        <div class="flex-col-gap-12">
          <n-input
            v-model:value="suggestionContent"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            placeholder="你在使用中遇到了什么问题？或者有什么想要的新功能？请告诉我们..."
          />
          <div class="flex-between-center">
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

      <div class="my-40-24">
        <button class="danger-btn" @click="handleLogout">退出登录</button>
      </div>
    </div>
  </n-modal>

  <!-- 头像裁剪弹窗 -->
  <n-modal v-model:show="showCropModal" preset="card" class="crop-modal-card" title="调整头像">
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
      <p class="crop-hint">
        可拖动调整位置，滑轮缩放
      </p>
      <div class="crop-actions">
        <n-button block @click="showCropModal = false" class="flex-1">取消</n-button>
        <n-button type="primary" block :loading="uploading" @click="handleCrop" class="flex-1">确定</n-button>
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
import { renderSafeMarkdown } from '../../utils/markdown'
import { themeOptions, type ThemeOption } from '../../constants/theme'

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
  themeOptions.filter((t: ThemeOption) => !t.dark)
)

const lightThemeGroups = computed(() => {
  const groups: { name: string, themes: typeof themeOptions }[] = []
  const map = new Map<string, typeof themeOptions>()
  
  lightThemeOptions.value.forEach((t: ThemeOption) => {
    const cat = t.category || '其它主题'
    if (!map.has(cat)) {
      const arr: typeof themeOptions = []
      map.set(cat, arr)
      groups.push({ name: cat, themes: arr })
    }
    map.get(cat)!.push(t)
  })
  return groups
})

const darkThemeOptions = computed(() =>
  themeOptions.filter((t: ThemeOption) => !!t.dark)
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
const forgotOldPasswordMode = ref(false)
const sendingPasswordCode = ref(false)
const changingPassword = ref(false)
const passwordCodeCountdown = ref(0)
const passwordMsg = ref('')
const showPasswordChange = ref(false)
const themeExpanded = ref(false)
let passwordCodeTimer: number | null = null

const suggestionContent = ref('')
const submittingSuggestion = ref(false)

const personaRoleOptions = [
  { value: 'personal_assistant', label: '通用个人助手' },
  { value: 'study_partner', label: '学习伙伴' },
  { value: 'coding_partner', label: '编程协作伙伴' },
  { value: 'writing_partner', label: '写作伙伴' },
  { value: 'life_companion', label: '生活陪伴者' },
]
const personaToneOptions = [
  { value: 'natural', label: '自然' },
  { value: 'warm', label: '温和' },
  { value: 'direct', label: '直接' },
  { value: 'clear', label: '清晰' },
  { value: 'concise', label: '简洁' },
  { value: 'precise', label: '严谨' },
  { value: 'formal', label: '正式' }, { value: 'playful', label: '轻松' },
  { value: 'empathetic', label: '共情' }, { value: 'calm', label: '沉静' },
  { value: 'analytical', label: '分析型' }, { value: 'encouraging', label: '鼓励' },
  { value: 'humorous', label: '幽默' }, { value: 'critical', label: '批判思考' },
]
const personaBehaviorOptions = [
  { value: 'CONCLUSION_FIRST', label: '先说结论' },
  { value: 'ASK_WHEN_AMBIGUOUS', label: '不明确时先追问' },
  { value: 'CODE_FIRST', label: '代码优先' },
  { value: 'LESS_REASSURANCE', label: '少一些安慰' },
  { value: 'DIRECT_FEEDBACK', label: '直接反馈' },
  { value: 'STEP_BY_STEP', label: '分步骤说明' },
  { value: 'CONCISE', label: '控制篇幅' },
]
const persona = ref({ role: 'personal_assistant', tone: ['natural', 'clear'], behaviorFlags: ['CONCLUSION_FIRST', 'ASK_WHEN_AMBIGUOUS'], disabledBehaviorFlags: [] as string[], customTone: '', customResponseStyle: '' })
const savingPersona = ref(false)
const previewingPersona = ref(false)
const personaExpanded = ref(false)
const personaMsg = ref('')
const personaError = ref(false)
const personaSampleMessage = ref('帮我规划一个轻松的周末安排')
const personaPreview = ref('')
const personaPreviewModel = ref<'0' | '1'>('0')
const personaPreviewHtml = computed(() => renderSafeMarkdown(personaPreview.value))

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
  personaMsg.value = ''
  personaPreview.value = ''
  try {
    const response = await authApi.getAiPersona()
    const data = response.data?.data
    if (data) {
      persona.value = {
        role: data.role || 'personal_assistant',
        tone: Array.isArray(data.tone) && data.tone.length ? data.tone : ['natural', 'clear'],
        behaviorFlags: Array.isArray(data.behaviorFlags) ? data.behaviorFlags : ['CONCLUSION_FIRST', 'ASK_WHEN_AMBIGUOUS'],
        disabledBehaviorFlags: Array.isArray(data.disabledBehaviorFlags) ? data.disabledBehaviorFlags : [],
        customTone: data.customTone || '',
        customResponseStyle: data.customResponseStyle || '',
      }
    }
  } catch (error) {
    logWarn('profile', '读取 AI 个性失败', error)
  }
}

async function savePersona() {
  savingPersona.value = true
  personaError.value = false
  personaMsg.value = ''
  try {
    const response = await authApi.updateAiPersona({ ...persona.value, customTone: persona.value.customTone.trim(), customResponseStyle: persona.value.customResponseStyle.trim() })
    const data = response.data?.data
    if (data) {
      persona.value = { role: data.role, tone: data.tone || [], behaviorFlags: data.behaviorFlags || [], disabledBehaviorFlags: data.disabledBehaviorFlags || [], customTone: data.customTone || '', customResponseStyle: data.customResponseStyle || '' }
    }
    personaMsg.value = 'AI 个性已更新'
  } catch (error: any) {
    personaError.value = true
    personaMsg.value = error?.response?.data?.message || '保存 AI 个性失败'
  } finally {
    savingPersona.value = false
  }
}

function resetPersona() {
  persona.value = { role: 'personal_assistant', tone: ['natural', 'clear'], behaviorFlags: ['CONCLUSION_FIRST', 'ASK_WHEN_AMBIGUOUS'], disabledBehaviorFlags: [], customTone: '', customResponseStyle: '' }
  personaMsg.value = '已恢复默认，点击保存后生效'
  personaError.value = false
}

async function previewPersona() {
  if (!personaSampleMessage.value.trim()) return
  previewingPersona.value = true
  personaPreview.value = ''
  try {
    const response = await authApi.previewAiPersona({ persona: persona.value, sampleMessage: personaSampleMessage.value.trim(), useReasoning: personaPreviewModel.value === '1' })
    personaPreview.value = response.data?.data || '暂时没有生成预览'
  } catch (error: any) {
    personaPreview.value = error?.response?.data?.message || '预览失败，请稍后重试'
  } finally {
    previewingPersona.value = false
  }
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

  if (file.size > 20 * 1024 * 1024) {
    uploadMsg.value = '文件大小不能超过 20MB'
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
  const surfaceSoft = cssVars.getPropertyValue('--color-surface-soft').trim()
  const primary = cssVars.getPropertyValue('--color-primary').trim()
  if (surfaceSoft) {
    ctx.fillStyle = surfaceSoft
    ctx.fillRect(0, 0, size, size)
  }

  const imgW = cropImg.naturalWidth
  const imgH = cropImg.naturalHeight
  const fitScale = size / Math.min(imgW, imgH)
  const drawW = imgW * fitScale * cropScale
  const drawH = imgH * fitScale * cropScale
  const drawX = (size - drawW) / 2 + cropOffsetX
  const drawY = (size - drawH) / 2 + cropOffsetY

  ctx.drawImage(cropImg, drawX, drawY, drawW, drawH)

  if (primary) {
    ctx.save()
    ctx.strokeStyle = primary
    ctx.globalAlpha = 0.35
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.arc(size / 2, size / 2, size / 2 - 4, 0, Math.PI * 2)
    ctx.stroke()
    ctx.restore()
  }
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
    } catch (e: any) {
      uploadMsg.value = e?.response?.data?.message || '上传失败'
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
    if (forgotOldPasswordMode.value) {
      if (!auth.email) throw new Error('未获取到当前账号邮箱，请重试')
      await authApi.sendResetPasswordCode(auth.email)
    } else {
      await auth.sendPasswordChangeCode()
    }
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
  if (!forgotOldPasswordMode.value && !oldPassword.value.trim()) {
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
    if (forgotOldPasswordMode.value) {
      if (!auth.email) throw new Error('未获取到当前账号邮箱，请重试')
      await auth.resetPassword(
        auth.email,
        passwordVerificationCode.value.trim(),
        newPassword.value.trim(),
        confirmNewPassword.value.trim()
      )
    } else {
      await auth.changePassword(
        oldPassword.value.trim(),
        newPassword.value.trim(),
        confirmNewPassword.value.trim(),
        passwordVerificationCode.value.trim(),
      )
    }
    
    emit('update:show', false)
    oldPassword.value = ''
    newPassword.value = ''
    confirmNewPassword.value = ''
    passwordVerificationCode.value = ''
    forgotOldPasswordMode.value = false
    
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
  transition: color 0.2s, background-color 0.2s, border-color 0.2s, opacity 0.2s, transform 0.2s;
  font-family: var(--font-body);
}

.upload-msg {
  margin-top: 8px;
  font-size: 12px;
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
  color: var(--color-on-primary);
}

.settings-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.persona-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.persona-toggle {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-hover);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}
.persona-toggle:hover,
.persona-toggle:focus-visible {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.persona-label {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 600;
}
.persona-select {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  padding: 9px 10px;
  font: inherit;
}
.persona-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.persona-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 7px 9px;
  color: var(--color-text-secondary);
  font-size: 12px;
  cursor: pointer;
}
.persona-option:has(input:checked) {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
  color: var(--color-primary-hover);
}
.persona-option input {
  accent-color: var(--color-primary);
}
.persona-actions,
.persona-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
}
.persona-error {
  color: var(--color-error) !important;
}
.persona-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 6px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}
.persona-preview-controls {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}
.persona-preview-model {
  min-height: 34px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  padding: 0 9px;
  font: inherit;
}
.persona-preview-result {
  margin: 0;
  padding: 10px 12px;
  border-left: 2px solid var(--color-primary);
  background: var(--color-surface-hover);
  color: var(--color-text);
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
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
  transition: color 0.2s, background-color 0.2s, border-color 0.2s, opacity 0.2s, transform 0.2s;
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
  background: var(--color-backdrop);
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
  transition: color 0.15s, background-color 0.15s, border-color 0.15s, opacity 0.15s, transform 0.15s;
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
  margin-top: 6px;
}
.theme-category-title {
  font-size: 12.5px;
  color: var(--color-text-light);
  margin-top: 20px;
  margin-bottom: 2px;
  font-weight: 500;
  letter-spacing: 0.02em;
}
.theme-category-title:first-of-type {
  margin-top: 8px;
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
  transition: color 0.25s var(--ease-out, ease), background-color 0.25s var(--ease-out, ease), border-color 0.25s var(--ease-out, ease), opacity 0.25s var(--ease-out, ease), transform 0.25s var(--ease-out, ease);
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
  box-shadow: var(--shadow-sm);
  transition: box-shadow 0.25s var(--ease-out, ease), transform 0.25s var(--ease-out, ease);
}
.theme-item:hover .theme-preview {
  box-shadow: var(--shadow-md);
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

.modal-card { width: 520px; max-width: 90vw; border-radius: 20px; }
.modal-title { text-align: center; font-weight: bold; font-size: 18px; }
.flex-center { display: flex; align-items: center; }
.gap-16 { gap: 16px; }
.user-name { font-size: 14px; color: var(--color-text); font-weight: 500; }
.hidden { display: none; }
.flex-col-stretch { display: flex; flex-direction: column; align-items: stretch; }
.gap-8 { gap: 8px; }
.flex-between-center { display: flex; justify-content: space-between; align-items: center; }
.text-xs-light { font-size: 12px; color: var(--color-text-light); }
.flex-end { display: flex; justify-content: flex-end; }
.theme-swatch-1 { left: 6px; top: 6px; width: 18px; height: 18px; border-radius: 50%; background: var(--t-primary); }
.theme-swatch-2 { right: 6px; top: 8px; width: 10px; height: 10px; border-radius: 3px; background: var(--t-accent); opacity: 0.8; }
.theme-bar-1 { left: 6px; bottom: 8px; width: 24px; height: 3px; border-radius: 2px; background: var(--t-primary); opacity: 0.3; }
.theme-bar-2 { left: 6px; bottom: 14px; width: 16px; height: 2px; border-radius: 1px; background: var(--t-primary); opacity: 0.15; }
.justify-between { justify-content: space-between; }
.text-sm { font-size: 14px; color: var(--color-text); }
.mt-12 { margin-top: 12px; }
.flex-col-start { display: flex; flex-direction: column; align-items: flex-start; }
.mt-8 { margin-top: 8px; }
.password-panel { width: 100%; display: flex; flex-direction: column; gap: 12px; margin-top: 8px; padding: 12px; background: var(--color-surface-hover); border-radius: 8px; }
.flex-gap-8 { display: flex; gap: 8px; }
.flex-1 { flex: 1; }
.text-sm-light { font-size: 13px; color: var(--color-text-light); }
.flex-col-gap-12 { display: flex; flex-direction: column; gap: 12px; }
.my-40-24 { margin-top: 40px; margin-bottom: 24px; }
.crop-modal-card { width: 400px; max-width: 90vw; }
.crop-hint { font-size: 12px; color: var(--color-text-light); text-align: center; margin-top: -8px; }
.crop-actions { display: flex; gap: 12px; width: 100%; margin-top: 8px; }

</style>

<style>
/* Override naive-ui modal default white background */
.settings-modal.n-card {
  width: min(760px, calc(100vw - 32px)) !important;
  max-width: calc(100vw - 32px) !important;
  max-height: calc(100vh - 32px) !important;
  background: var(--color-surface) !important;
  border: 1px solid color-mix(in oklab, var(--color-border) 40%, transparent) !important;
  border-radius: 12px !important;
  box-shadow: var(--shadow-lg) !important;
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


