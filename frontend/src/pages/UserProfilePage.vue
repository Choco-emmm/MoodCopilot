<template>
  <main class="app-shell">
    <AppHeader />

    <section class="panel profile-head">
      <n-button
        v-if="isOwner"
        quaternary
        circle
        class="profile-settings-trigger"
        title="设置"
        @click="openSettingsModal"
      >
        ⚙️
      </n-button>
      <div class="profile-hero">
        <div class="profile-avatar-wrap">
          <div v-if="profileLoading" class="profile-avatar">
            <n-spin size="small" />
          </div>
          <img v-else-if="profileAvatar" :src="profileAvatar" class="profile-avatar profile-avatar-img" decoding="async" />
          <span v-else class="profile-avatar">{{ profileInitial }}</span>
        </div>
        <div class="profile-main">
          <div class="profile-title-row">
            <h2 class="profile-title">{{ profileLoading ? '加载中...' : (isOwner ? (auth.displayName || '我的日记') : profileName) }}</h2>
            <button
              v-if="!isOwner && !profileLoading"
              :class="['follow-btn', 'profile-follow-btn', { following: followStore.isFollowing(profileUserId) }]"
              :disabled="followStore.isPending(profileUserId)"
              @mouseenter="profileFollowHover = true"
              @mouseleave="profileFollowHover = false"
              @click="toggleProfileFollow"
            >
              {{ profileFollowLabel }}
            </button>
          </div>
          <p class="profile-signature">{{ profileSignature || (isOwner ? '还没有写签名，去个人中心补一句吧。' : '这个人很低调，还没留下签名。') }}</p>
        </div>
      </div>
    </section>

    <section v-if="isOwner" class="panel tasks-panel">
      <router-link to="/task-center" class="task-center-link">
        <span>📋 任务中心</span>
        <span class="task-center-arrow">→</span>
      </router-link>
    </section>

    <section class="panel profile-list-panel">
      <div class="profile-list-head">
        <h3>{{ isSearching ? '搜索结果' : '日记列表' }}</h3>
        <div class="profile-list-actions" style="display: flex; align-items: center; gap: 8px;">
          <n-button v-if="isSearching" text type="warning" size="small" @click="clearSearch" style="margin-right: 4px;">清除搜索</n-button>
          <n-button
            v-if="isOwner"
            quaternary
            circle
            size="small"
            :type="showSearchPanel ? 'primary' : 'default'"
            @click="showSearchPanel = !showSearchPanel"
            title="搜索日记"
            style="font-size: 14px;"
          >
            🔍
          </n-button>
          <n-button quaternary size="small" :loading="loading" @click="reload">刷新</n-button>
        </div>
      </div>

      <!-- 搜索卡片面板 -->
      <transition name="fade-slide">
        <div v-if="isOwner && showSearchPanel" class="search-panel-card">
          <div class="search-grid">
            <div class="search-field keyword-field">
              <label class="field-label">关键词</label>
              <n-input
                v-model:value="keyword"
                placeholder="搜索日记内容…"
                clearable
                @keyup.enter="triggerSearch"
              />
            </div>
            <div class="search-field">
              <label class="field-label">起始日期</label>
              <n-date-picker
                v-model:value="startDateVal"
                type="date"
                clearable
                placeholder="不限"
                :is-date-disabled="dateDisabled"
                style="width: 100%;"
              />
            </div>
            <div class="search-field">
              <label class="field-label">结束日期</label>
              <n-date-picker
                v-model:value="endDateVal"
                type="date"
                clearable
                placeholder="不限"
                style="width: 100%;"
              />
            </div>
            <div class="search-field">
              <label class="field-label">公开范围</label>
              <n-select
                v-model:value="visibilityFilter"
                :options="visibilityOpts"
                placeholder="不限"
                clearable
                style="width: 100%;"
              />
            </div>
          </div>
          <div class="search-actions">
            <n-button text class="clear-filters-btn" @click="clearFilters">清除筛选</n-button>
            <div class="search-buttons-group">
              <n-button type="primary" :loading="loading" @click="triggerSearch">搜索</n-button>
            </div>
          </div>
        </div>
      </transition>

      <div v-if="diaries.length" class="feed">
        <DiaryFeedItem
          v-for="diary in diaries"
          :key="diary.id"
          :diary="diary"
          :enable-comments="false"
          :compact="true"
          :preview-limit="120"
          :show-expand-toggle="false"
          :hide-follow-btn="!isOwner"
          @resonate="(d: Diary) => store.resonate(d.id)"
          @open-detail="(d: Diary) => router.push(`/diary/${d.id}`)"
        />

        <div v-if="hasMore" ref="sentinel" class="profile-load-more">
          <n-spin v-if="loadingMore" size="small" />
          <n-button v-else secondary block @click="loadMore">加载更多</n-button>
        </div>
      </div>

      <div v-else-if="!loading" class="profile-empty-wrap">
        <n-empty :description="isSearching ? '未找到匹配的日记' : (isOwner ? '你还没有写日记' : '暂无公开日记')" />
        <p class="profile-empty-tip">{{ isSearching ? '尝试缩短或修改搜索关键词、扩大时间范围' : (isOwner ? '从一条简单记录开始，持续比完美更重要。' : '晚点再来看看，或先去广场看看大家的分享。') }}</p>
      </div>
      <n-spin v-else size="small" />
    </section>

    <n-modal
      v-model:show="showSettingsModal"
      preset="card"
      title="设置"
      class="settings-modal"
      style="width: 95%; max-width: 650px; max-height: 85vh;"
    >
      <div class="settings-modal-scroll">
        <section class="settings-section">
          <div class="section-head">
            <label class="settings-label">头像</label>
            <span class="section-tag">Avatar</span>
          </div>
          <div class="settings-avatar-row">
            <div class="settings-avatar-preview-wrap">
              <img v-if="auth.avatar" :src="auth.avatar" class="settings-avatar-preview" decoding="async" />
              <span v-else class="settings-avatar-placeholder">{{ auth.displayName?.charAt(0) || '我' }}</span>
            </div>
            <n-button size="small" @click="triggerUpload">更换头像</n-button>
          </div>
          <p v-if="uploadMsg" class="settings-hint">{{ uploadMsg }}</p>
          <input
            ref="fileInput"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            hidden
            @change="onFileChange"
          />
        </section>

        <section class="settings-section">
          <div class="section-head">
            <label class="settings-label">用户名</label>
            <span class="section-tag">Identity</span>
          </div>
          <div class="settings-row">
            <n-input
              v-model:value="editingName"
              :disabled="savingName || auth.remainingNameChanges <= 0"
              :maxlength="64"
              placeholder="输入新用户名"
              @keyup.enter="saveName"
            />
            <n-button
              size="small"
              type="primary"
              :disabled="!editingName.trim() || editingName === auth.displayName || savingName || auth.remainingNameChanges <= 0"
              @click="saveName"
              class="save-btn"
            >
              保存
            </n-button>
          </div>
          <p class="settings-hint">本周剩余修改次数：{{ auth.remainingNameChanges }}</p>
          <p v-if="nameMsg" class="settings-hint">{{ nameMsg }}</p>
        </section>

        <section class="settings-section">
          <div class="section-head">
            <label class="settings-label">个性签名</label>
            <span class="section-tag">Profile</span>
          </div>
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
          <div class="settings-row notify-row" style="align-items: center;">
            <div style="flex: 1; padding-right: 12px;">
              <p class="notify-title" style="font-size: 14px;">每日跟进通知</p>
              <p class="settings-desc" style="margin-top: 4px; font-size: 12px;">每天在偏好时段推送一条情绪陪跑通知</p>
            </div>
            <n-switch :value="auth.dailyNotifyEnabled" :disabled="toggling" @update:value="toggleNotify" />
          </div>
        </section>

        <section class="settings-section">
          <div class="section-head" style="display: flex; justify-content: space-between; align-items: center;">
            <div style="display: flex; align-items: center; gap: 8px;">
              <p class="settings-label">我的记忆</p>
              <span class="section-tag">Memory</span>
            </div>
            <n-button size="tiny" secondary type="primary" :loading="consolidatingMemory" @click="consolidateMemories">
              ✨ 智能整理记忆
            </n-button>
          </div>
          <p class="memory-desc">MoodCopilot 从你的日记和聊天中学习的长期画像，你可以编辑修正或删除不想要的部分。如果碎片太多，可以尝试智能整理归并。</p>
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
              <div class="memory-actions" style="display: flex; gap: 4px; align-items: center; margin-left: auto;">
                <template v-if="editingMemoryId === m.id">
                  <n-button size="small" secondary type="primary" :disabled="savingMemoryId === m.id" @click="saveMemory(m.id)" style="font-size: 12px; padding: 0 8px;">
                    {{ savingMemoryId === m.id ? '...' : '保存' }}
                  </n-button>
                  <n-button size="small" secondary @click="cancelEditMemory" style="font-size: 12px; padding: 0 8px;">取消</n-button>
                </template>
                <template v-else>
                  <n-button size="small" secondary @click="startEditMemory(m)" style="font-size: 12px; padding: 0 8px;">编辑</n-button>
                  <n-button size="small" secondary type="error" :disabled="deletingMemoryId === m.id" @click="forgetMemory(m.id)" style="font-size: 12px; padding: 0 8px;">
                    {{ deletingMemoryId === m.id ? '...' : '删除' }}
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
          <n-button v-if="!showPasswordChange" secondary size="small" @click="showPasswordChange = true" class="change-password-btn">
            <template #icon>
              <span style="font-size: 14px">🔒</span>
            </template>
            修改密码
          </n-button>
          <div v-if="showPasswordChange" class="password-change-panel">
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

        <section class="settings-section support-donate-section">
          <div class="section-head">
            <p class="settings-label">请开发者喝杯奶茶</p>
            <span class="section-tag">🧋</span>
          </div>
          <p class="settings-desc">如果 MoodCopilot 帮到了你，欢迎请开发者喝杯奶茶～</p>
          <n-button quaternary type="primary" @click="showSettingsModal = false; router.push('/support')">
            去看看 →
          </n-button>
        </section>

        <section class="settings-section danger-zone">
          <div class="section-head">
            <p class="settings-label">账户操作</p>
            <span class="section-tag">Security</span>
          </div>
          <n-button type="error" block @click="handleLogout">退出登录</n-button>
        </section>
      </div>
    </n-modal>

    <n-modal
      v-model:show="showCropModal"
      preset="card"
      title="裁切头像"
      style="width: 90%; max-width: 420px;"
      :mask-closable="false"
    >
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
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NEmpty, NInput, NModal, NSpin, NSwitch, NDatePicker, NSelect } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import DiaryFeedItem from '../components/DiaryFeedItem.vue'
import { authApi, diaryApi, memoryApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { useDiaryStore, type Diary } from '../stores/diary'
import { useFollowStore } from '../stores/follow'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const store = useDiaryStore()
const followStore = useFollowStore()

const loading = ref(false)
const loadingMore = ref(false)
const page = ref(1)
const total = ref(0)
const diaries = ref<Diary[]>([])
const profileName = ref('')
const profileAvatar = ref<string | null>(null)
const profileSignature = ref('')
const profileLoading = ref(true)
const showSettingsModal = ref(false)

const isSearching = ref(false)
const showSearchPanel = ref(false)
const keyword = ref('')
const startDateVal = ref<number | null>(null)
const endDateVal = ref<number | null>(null)
const visibilityFilter = ref<string | null>(null)
const visibilityOpts = [
  { label: '仅自己看', value: 'PRIVATE' },
  { label: '公开', value: 'PUBLIC' },
]

interface MemoryItem {
  id: number
  attributeKey: string
  attributeValue: string
}

const memories = ref<MemoryItem[]>([])
const memoriesLoading = ref(false)
const consolidatingMemory = ref(false)
const deletingMemoryId = ref<number | null>(null)
const editingMemoryId = ref<number | null>(null)
const editingMemoryValue = ref('')
const savingMemoryId = ref<number | null>(null)

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

const profileUserId = computed(() => Number(route.params.userId))
const isOwner = computed(() => auth.userId != null && auth.userId === profileUserId.value)
const profileFollowHover = ref(false)
const profileFollowLabel = computed(() => {
  if (followStore.isPending(profileUserId.value)) {
    return '处理中...'
  }
  if (followStore.isFollowing(profileUserId.value)) {
    return profileFollowHover.value ? '取消关注' : '已关注'
  }
  return '+ 关注'
})
async function toggleProfileFollow() {
  if (followStore.isPending(profileUserId.value)) return
  if (followStore.isFollowing(profileUserId.value)) {
    await followStore.unfollow(profileUserId.value)
  } else {
    await followStore.follow(profileUserId.value)
  }
}
const sentinel = ref<HTMLElement | null>(null)
const hasMore = computed(() => diaries.value.length < total.value)
const profileInitial = computed(() => (profileName.value || '用').charAt(0))

let io: IntersectionObserver | null = null

onMounted(() => {
  void reload()
  if (typeof IntersectionObserver !== 'undefined') {
    io = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && hasMore.value && !loadingMore.value) {
        loadMore()
      }
    }, { rootMargin: '300px' })
  }
})

onUnmounted(() => {
  io?.disconnect()
})

watch(sentinel, (el) => {
  io?.disconnect()
  if (el) io?.observe(el)
})

watch(() => route.params.userId, () => {
  void reload()
})

watch(showSettingsModal, (val) => {
  if (!val) return
  void hydrateSettingsData()
})

watch(showCropModal, (val) => {
  if (val && cropImg) {
    setTimeout(() => drawCrop(), 150)
  }
})

function dateDisabled(ts: number) {
  return ts > Date.now()
}

function fmtDate(d: number | null): string | undefined {
  if (!d) return undefined
  const date = new Date(d)
  return date.getFullYear() + '-' +
    String(date.getMonth() + 1).padStart(2, '0') + '-' +
    String(date.getDate()).padStart(2, '0')
}

function triggerSearch() {
  isSearching.value = true
  void reload()
}

function clearFilters() {
  keyword.value = ''
  startDateVal.value = null
  endDateVal.value = null
  visibilityFilter.value = null
}

function clearSearch() {
  clearFilters()
  isSearching.value = false
  void reload()
}

async function reload() {
  if (!Number.isFinite(profileUserId.value)) {
    diaries.value = []
    total.value = 0
    return
  }

  loading.value = true
  profileLoading.value = true
  page.value = 1
  try {
    const profileRes = await authApi.profile(profileUserId.value)
    const profile = profileRes.data.data
    profileName.value = profile?.displayName || (isOwner.value ? auth.displayName || '我' : '用户')
    profileAvatar.value = profile?.avatar || (isOwner.value ? auth.avatar : null)
    profileSignature.value = profile?.signature || ''

    if (!isOwner.value) {
      void followStore.checkStatus(profileUserId.value)
    }

    if (isSearching.value) {
      const res = await diaryApi.search({
        keyword: keyword.value || undefined,
        startDate: fmtDate(startDateVal.value),
        endDate: fmtDate(endDateVal.value),
        visibility: visibilityFilter.value || undefined,
        page: 1,
        size: 20,
      })
      const items = (res.data.data.items ?? []).map(store.normalize)
      diaries.value = items
      total.value = res.data.data.total ?? 0
    } else {
      const diaryRes = isOwner.value ? await diaryApi.mine(1, 20) : await diaryApi.byUser(profileUserId.value, 1, 20)
      const data = diaryRes.data.data
      const items = (data.items ?? []).map(store.normalize)
      diaries.value = items
      total.value = data.total ?? items.length
    }
  } finally {
    profileLoading.value = false
    loading.value = false
  }
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const nextPage = page.value + 1
    if (isSearching.value) {
      const res = await diaryApi.search({
        keyword: keyword.value || undefined,
        startDate: fmtDate(startDateVal.value),
        endDate: fmtDate(endDateVal.value),
        visibility: visibilityFilter.value || undefined,
        page: nextPage,
        size: 20,
      })
      const items = (res.data.data.items ?? []).map(store.normalize)
      const existing = new Set(diaries.value.map(d => d.id))
      diaries.value.push(...items.filter((item: Diary) => !existing.has(item.id)))
      total.value = res.data.data.total ?? diaries.value.length
      page.value = nextPage
    } else {
      const res = isOwner.value
        ? await diaryApi.mine(nextPage, 20)
        : await diaryApi.byUser(profileUserId.value, nextPage, 20)
      const data = res.data.data
      const items = (data.items ?? []).map(store.normalize)
      const existing = new Set(diaries.value.map(d => d.id))
      diaries.value.push(...items.filter((item: Diary) => !existing.has(item.id)))
      total.value = data.total ?? diaries.value.length
      page.value = nextPage
    }
  } finally {
    loadingMore.value = false
  }
}

function openSettingsModal() {
  if (!isOwner.value) return
  showSettingsModal.value = true
}

async function hydrateSettingsData() {
  await auth.fetchProfile()
  editingName.value = auth.displayName ?? ''
  editingSignature.value = auth.signature ?? ''
  await loadMemories()
}

async function loadMemories() {
  memoriesLoading.value = true
  try {
    const res = await memoryApi.getAll()
    memories.value = (res.data.data ?? []) as MemoryItem[]
  } catch {
    memories.value = []
  } finally {
    memoriesLoading.value = false
  }
}

async function forgetMemory(id: number) {
  deletingMemoryId.value = id
  try {
    await memoryApi.forget(id)
    memories.value = memories.value.filter((m) => m.id !== id)
  } catch {
    // ignore
  } finally {
    deletingMemoryId.value = null
  }
}

function startEditMemory(m: MemoryItem) {
  editingMemoryId.value = m.id
  editingMemoryValue.value = m.attributeValue
}

async function saveMemory(id: number) {
  const value = editingMemoryValue.value.trim()
  if (!value) return

  savingMemoryId.value = id
  try {
    await memoryApi.update(id, { attributeValue: value })
    const idx = memories.value.findIndex((m) => m.id === id)
    if (idx !== -1) {
      memories.value[idx] = { ...memories.value[idx], attributeValue: value }
    }
    editingMemoryId.value = null
    editingMemoryValue.value = ''
  } catch {
    // ignore
  } finally {
    savingMemoryId.value = null
  }
}

function cancelEditMemory() {
  editingMemoryId.value = null
  editingMemoryValue.value = ''
}

async function consolidateMemories() {
  if (consolidatingMemory.value) return
  consolidatingMemory.value = true
  try {
    await memoryApi.consolidate()
    await loadMemories()
    window.$message?.success('记忆碎片整理完成')
  } catch (err: any) {
    console.error('Failed to consolidate memories', err)
    if (err.response?.status === 429 || (err.response?.data?.message && err.response.data.message.includes('每天最多只能进行两次'))) {
      alert('每天最多只能进行两次智能整理，请明天再试吧')
    } else {
      alert('记忆碎片整理失败，请稍后重试')
    }
  } finally {
    consolidatingMemory.value = false
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
      if (isOwner.value) {
        profileAvatar.value = auth.avatar
      }
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
    if (isOwner.value) {
      profileName.value = auth.displayName || '我'
    }
    nameMsg.value = '用户名已更新'
    editingName.value = auth.displayName ?? ''
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || ''
    if (msg) nameMsg.value = msg
    else nameMsg.value = '保存失败'
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
    if (isOwner.value) {
      profileSignature.value = editingSignature.value
    }
    signatureMsg.value = '个性签名已更新'
  } catch {
    signatureMsg.value = '保存失败'
  } finally {
    savingSignature.value = false
  }
}

async function toggleNotify(val: boolean) {
  toggling.value = true
  try {
    await auth.updateSettings(val)
  } catch {
    // ignore
  } finally {
    toggling.value = false
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
    showSettingsModal.value = false
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
  showSettingsModal.value = false
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
.profile-head {
  position: relative;
  margin-bottom: 12px;
  padding: 16px;
}

.profile-settings-trigger {
  position: absolute;
  right: 16px;
  top: 16px;
  font-size: 20px;
}

.profile-hero {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.profile-avatar-wrap {
  flex-shrink: 0;
}

.profile-avatar {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  color: var(--color-surface);
  background: var(--color-primary);
  border: 1px solid color-mix(in srgb, var(--color-primary) 35%, transparent 65%);
}

.profile-avatar-img {
  object-fit: cover;
}

.profile-main {
  min-width: 0;
}

.profile-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.profile-title {
  margin: 0;
  font-family: var(--font-body);
  color: var(--color-text);
  font-size: clamp(24px, 4.8vw, 38px);
  line-height: 1.14;
  letter-spacing: -0.01em;
}

.profile-follow-btn {
  flex-shrink: 0;
  font-size: 13px;
  padding: 5px 14px;
  border-radius: 20px;
  border: 1.5px solid var(--color-accent);
  background: transparent;
  color: var(--color-accent);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  font-family: var(--font-body);
}

.profile-follow-btn.following {
  background: var(--color-accent);
  color: #fff;
}

.profile-follow-btn:hover {
  opacity: 0.82;
}

.profile-signature {
  margin: 10px 0 0;
  color: var(--color-text-secondary);
  font-size: 14px;
  line-height: 1.55;
}

.profile-list-panel {
  min-height: 180px;
  padding: 16px;
}

.profile-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.profile-list-head h3 {
  margin: 0;
  color: var(--color-text);
  font-size: 18px;
  line-height: 1.2;
}

.profile-empty-wrap {
  display: grid;
  gap: 8px;
  justify-items: center;
  padding: 8px 0 2px;
}

.profile-empty-tip {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.profile-load-more {
  margin-top: 10px;
}

.settings-modal-scroll {
  overflow-y: auto;
  max-height: calc(85vh - 100px);
  padding-right: 4px;
}

.support-donate-section {
  text-align: center;
}
.support-donate-section .section-head {
  justify-content: center;
}

.settings-section {
  margin-top: 12px;
  padding: 16px;
  border-radius: 16px;
  background: var(--color-surface);
  border: 1px solid color-mix(in srgb, var(--color-border) 40%, transparent);
  box-shadow: 0 2px 10px color-mix(in srgb, var(--color-text) 3%, transparent);
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

.settings-avatar-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.settings-avatar-preview-wrap,
.settings-avatar-placeholder,
.settings-avatar-preview {
  width: 62px;
  height: 62px;
  border-radius: 999px;
}

.settings-avatar-preview-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--color-primary) 24%, transparent 76%);
}

.settings-avatar-preview {
  object-fit: cover;
}

.settings-avatar-placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  color: var(--color-surface);
  background: var(--color-primary);
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
  max-height: 260px;
  overflow-y: auto;
}

.memory-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--color-surface-soft) 50%, transparent);
  border: 1px solid color-mix(in srgb, var(--color-border) 40%, transparent);
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

.change-password-btn {
  margin-top: 4px;
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
  border-color: color-mix(in srgb, var(--color-accent) 20%, transparent 80%);
}

.crop-area {
  display: flex;
  justify-content: center;
  align-items: center;
  background: var(--color-surface-soft);
  border: 1px solid color-mix(in srgb, var(--color-border-strong) 22%, transparent 78%);
  border-radius: 12px;
  overflow: hidden;
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.crop-area:active {
  cursor: grabbing;
}

.crop-canvas {
  display: block;
  border-radius: 4px;
}

.crop-zoom-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 12px;
}

.crop-zoom-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.crop-action-row {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.tasks-panel {
  margin-bottom: 12px;
  padding: 12px 16px;
}

.task-center-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-primary);
  text-decoration: none;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-light);
  transition: background 0.15s, color 0.15s;
}

.task-center-link:hover {
  background: color-mix(in srgb, var(--color-primary) 18%, transparent 82%);
  color: var(--color-primary-hover);
}

.task-center-arrow {
  font-size: 16px;
}

.search-panel-card {
  margin-bottom: 20px;
  padding: 16px;
  background: var(--color-surface-soft);
  border: 1px solid color-mix(in srgb, var(--color-border-strong) 15%, transparent 85%);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.search-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: 12px;
  margin-bottom: 14px;
}

.search-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.search-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed color-mix(in srgb, var(--color-border-strong) 25%, transparent 75%);
  padding-top: 12px;
}

.clear-filters-btn {
  font-size: 13px !important;
  color: var(--color-text-muted) !important;
}

.clear-filters-btn:hover {
  color: var(--color-accent) !important;
}

.search-buttons-group {
  display: flex;
  gap: 8px;
}

@media (max-width: 780px) {
  .profile-head,
  .profile-list-panel {
    padding: 14px;
  }

  .profile-avatar {
    width: 52px;
    height: 52px;
    font-size: 20px;
  }

  .profile-signature {
    font-size: 13px;
  }

  .profile-list-head h3 {
    font-size: 17px;
  }

  .settings-row {
    flex-direction: column;
    align-items: stretch;
  }

  .password-row-inline {
    flex-direction: column;
    align-items: stretch;
  }

  .password-code-row {
    flex-direction: row;
    align-items: center;
  }

  .password-code-row :deep(.n-input) {
    flex: 1;
    min-width: 0;
  }

  .password-code-row .n-button {
    width: auto;
    flex: 0 0 auto;
  }

  .save-btn {
    width: 100%;
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

  .search-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }
}


.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.25s var(--ease-out);
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
