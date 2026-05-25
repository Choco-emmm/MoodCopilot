<template>
  <main class="app-shell">
    <AppHeader />

    <section class="panel profile-head">
      <n-button
        v-if="isOwner"
        quaternary
        circle
        class="profile-settings-trigger"
        title="璁剧疆"
        @click="openSettingsModal"
      >
        鈿欙笍
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
            <h2 class="profile-title">{{ profileLoading ? '鍔犺浇涓?..' : (isOwner ? (auth.displayName || '鎴戠殑鏃ヨ') : profileName) }}</h2>
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
          <p class="profile-signature">{{ profileSignature || (isOwner ? '杩樻病鏈夊啓绛惧悕锛屽幓涓汉涓績琛ヤ竴鍙ュ惂銆? : '杩欎釜浜哄緢浣庤皟锛岃繕娌＄暀涓嬬鍚嶃€?) }}</p>
        </div>
      </div>
    </section>

    <section v-if="isOwner" class="panel tasks-panel">
      <router-link to="/task-center" class="task-center-link">
        <span>馃搵 浠诲姟涓績</span>
        <span class="task-center-arrow">鈫?/span>
      </router-link>
    </section>

    <section class="panel profile-list-panel">
      <div class="profile-list-head">
        <h3>{{ isSearching ? '鎼滅储缁撴灉' : '鏃ヨ鍒楄〃' }}</h3>
        <div class="profile-list-actions" style="display: flex; align-items: center; gap: 8px;">
          <n-button v-if="isSearching" text type="warning" size="small" @click="clearSearch" style="margin-right: 4px;">娓呴櫎鎼滅储</n-button>
          <n-button
            v-if="isOwner"
            quaternary
            circle
            size="small"
            :type="showSearchPanel ? 'primary' : 'default'"
            @click="showSearchPanel = !showSearchPanel"
            title="鎼滅储鏃ヨ"
            style="font-size: 14px;"
          >
            馃攳
          </n-button>
          <n-button quaternary size="small" :loading="loading" @click="reload">鍒锋柊</n-button>
        </div>
      </div>

      <!-- 鎼滅储鍗＄墖闈㈡澘 -->
      <transition name="fade-slide">
        <div v-if="isOwner && showSearchPanel" class="search-panel-card">
          <div class="search-grid">
            <div class="search-field keyword-field">
              <label class="field-label">鍏抽敭璇?/label>
              <n-input
                v-model:value="keyword"
                placeholder="鎼滅储鏃ヨ鍐呭鈥?
                clearable
                @keyup.enter="triggerSearch"
              />
            </div>
            <div class="search-field">
              <label class="field-label">璧峰鏃ユ湡</label>
              <n-date-picker
                v-model:value="startDateVal"
                type="date"
                clearable
                placeholder="涓嶉檺"
                :is-date-disabled="dateDisabled"
                style="width: 100%;"
              />
            </div>
            <div class="search-field">
              <label class="field-label">缁撴潫鏃ユ湡</label>
              <n-date-picker
                v-model:value="endDateVal"
                type="date"
                clearable
                placeholder="涓嶉檺"
                style="width: 100%;"
              />
            </div>
            <div class="search-field">
              <label class="field-label">鍏紑鑼冨洿</label>
              <n-select
                v-model:value="visibilityFilter"
                :options="visibilityOpts"
                placeholder="涓嶉檺"
                clearable
                style="width: 100%;"
              />
            </div>
          </div>
          <div class="search-actions">
            <n-button text class="clear-filters-btn" @click="clearFilters">娓呴櫎绛涢€?/n-button>
            <div class="search-buttons-group">
              <n-button type="primary" :loading="loading" @click="triggerSearch">鎼滅储</n-button>
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
          <n-button v-else secondary block @click="loadMore">鍔犺浇鏇村</n-button>
        </div>
      </div>

      <div v-else-if="!loading" class="profile-empty-wrap">
        <n-empty :description="isSearching ? '鏈壘鍒板尮閰嶇殑鏃ヨ' : (isOwner ? '浣犺繕娌℃湁鍐欐棩璁? : '鏆傛棤鍏紑鏃ヨ')" />
        <p class="profile-empty-tip">{{ isSearching ? '灏濊瘯缂╃煭鎴栦慨鏀规悳绱㈠叧閿瘝銆佹墿澶ф椂闂磋寖鍥? : (isOwner ? '浠庝竴鏉＄畝鍗曡褰曞紑濮嬶紝鎸佺画姣斿畬缇庢洿閲嶈銆? : '鏅氱偣鍐嶆潵鐪嬬湅锛屾垨鍏堝幓骞垮満鐪嬬湅澶у鐨勫垎浜€?) }}</p>
      </div>
      <n-spin v-else size="small" />
    </section>

    <n-modal
      v-model:show="showSettingsModal"
      preset="card"
      title="璁剧疆"
      class="settings-modal"
      style="width: 95%; max-width: 650px; max-height: 85vh;"
    >
      <div class="settings-modal-scroll">
        <section class="settings-section">
          <div class="section-head">
            <label class="settings-label">澶村儚</label>
            <span class="section-tag">Avatar</span>
          </div>
          <div class="settings-avatar-row">
            <div class="settings-avatar-preview-wrap">
              <img v-if="auth.avatar" :src="auth.avatar" class="settings-avatar-preview" decoding="async" />
              <span v-else class="settings-avatar-placeholder">{{ auth.displayName?.charAt(0) || '鎴? }}</span>
            </div>
            <n-button size="small" @click="triggerUpload">鏇存崲澶村儚</n-button>
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
            <label class="settings-label">鐢ㄦ埛鍚?/label>
            <span class="section-tag">Identity</span>
          </div>
          <div class="settings-row">
            <n-input
              v-model:value="editingName"
              :disabled="savingName || auth.remainingNameChanges <= 0"
              :maxlength="64"
              placeholder="杈撳叆鏂扮敤鎴峰悕"
              @keyup.enter="saveName"
              @blur="checkEditingName"
            />
            <n-button
              size="small"
              type="primary"
              :disabled="!editingName.trim() || editingName === auth.displayName || savingName || auth.remainingNameChanges <= 0"
              @click="saveName"
              class="save-btn"
            >
              淇濆瓨
            </n-button>
          </div>
          <p class="settings-hint">鏈懆鍓╀綑淇敼娆℃暟锛歿{ auth.remainingNameChanges }}</p>
          <p v-if="nameMsg" class="settings-hint">{{ nameMsg }}</p>
        </section>

        <section class="settings-section">
          <div class="section-head">
            <label class="settings-label">涓€х鍚?/label>
            <span class="section-tag">Profile</span>
          </div>
          <div class="settings-row settings-row-signature" style="flex-direction: column; align-items: stretch; gap: 8px;">
            <n-input
              v-model:value="editingSignature"
              type="textarea"
              :maxlength="160"
              :autosize="{ minRows: 2, maxRows: 4 }"
              placeholder="鍐欎竴鍙ヤ綘甯屾湜鍒汉鐪嬪埌鐨勭姸鎬侊紙鏈€澶?60瀛楋級"
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
                淇濆瓨绛惧悕
              </n-button>
            </div>
          </div>
          <p v-if="signatureMsg" class="settings-hint">{{ signatureMsg }}</p>
        </section>

        <section class="settings-section">
          <div class="section-head">
            <label class="settings-label">閭璐﹀彿</label>
            <span class="section-tag">Account</span>
          </div>
          <div class="settings-inline-tip">{{ auth.email || '鏈幏鍙栧埌閭淇℃伅' }}</div>
          <p class="settings-desc">閭璐﹀彿褰撳墠浠呯敤浜庣櫥褰曞拰瀹夊叏楠岃瘉锛屾殏涓嶆敮鎸佺洿鎺ヤ慨鏀广€?/p>
        </section>

        <section class="settings-section">
          <div class="section-head">
            <label class="settings-label">鎻愰啋涓庨櫔浼?/label>
            <span class="section-tag">Routine</span>
          </div>
          <div class="settings-row notify-row" style="align-items: center;">
            <div style="flex: 1; padding-right: 12px;">
              <p class="notify-title" style="font-size: 14px;">姣忔棩璺熻繘閫氱煡</p>
              <p class="settings-desc" style="margin-top: 4px; font-size: 12px;">姣忓ぉ鍦ㄥ亸濂芥椂娈垫帹閫佷竴鏉℃儏缁櫔璺戦€氱煡</p>
            </div>
            <n-switch :value="auth.dailyNotifyEnabled" :disabled="toggling" @update:value="toggleNotify" />
          </div>
          <div class="settings-row notify-row" style="align-items: center;">
            <div style="flex: 1; padding-right: 12px;">
              <p class="notify-title" style="font-size: 14px;">鐢诲儚/鍥捐氨鏇存柊閫氱煡</p>
              <p class="settings-desc" style="margin-top: 4px; font-size: 12px;">鏃ヨ鍒嗘瀽鍚庢湁鐢诲儚鎴栧浘璋卞彉鏇存椂寮圭獥鎻愰啋</p>
            </div>
            <n-switch :value="auth.profileNotifyEnabled" :disabled="toggling" @update:value="toggleProfileNotify" />
          </div>
        </section>



        <section class="settings-section danger-zone">
          <div class="section-head">
            <p class="settings-label">璐︽埛瀹夊叏</p>
            <span class="section-tag">Security</span>
          </div>
          <p class="settings-desc">淇敼瀵嗙爜鍓嶄細鍚戝綋鍓嶈处鍙烽偖绠卞彂閫侀獙璇佺爜锛岄獙璇侀€氳繃鍚庢墠浼氱敓鏁堛€?/p>
          <n-button v-if="!showPasswordChange" secondary size="small" @click="showPasswordChange = true" class="change-password-btn">
            <template #icon>
              <span style="font-size: 14px">馃敀</span>
            </template>
            淇敼瀵嗙爜
          </n-button>
          <div v-if="showPasswordChange" class="password-change-panel">
            <div class="password-row-inline">
              <n-input
                v-model:value="oldPassword"
                type="password"
                show-password-on="click"
                :maxlength="64"
                placeholder="杈撳叆褰撳墠瀵嗙爜"
              />
            </div>
            <div class="password-row-inline">
              <n-input
                v-model:value="newPassword"
                type="password"
                show-password-on="click"
                :maxlength="64"
                placeholder="杈撳叆鏂板瘑鐮侊紙鑷冲皯6浣嶏級"
              />
            </div>
            <div class="password-row-inline">
              <n-input
                v-model:value="confirmNewPassword"
                type="password"
                show-password-on="click"
                :maxlength="64"
                placeholder="鍐嶆杈撳叆鏂板瘑鐮?
              />
            </div>
            <div class="password-row-inline password-code-row">
              <n-input
                v-model:value="passwordVerificationCode"
                :maxlength="6"
                placeholder="杈撳叆閭楠岃瘉鐮?
              />
              <n-button
                :disabled="passwordCodeCountdown > 0"
                :loading="sendingPasswordCode"
                @click="sendPasswordCode"
              >
                {{ passwordCodeCountdown > 0 ? `${passwordCodeCountdown}s` : '鍙戦€侀獙璇佺爜' }}
              </n-button>
            </div>
            <n-button
              type="primary"
              :loading="changingPassword"
              :disabled="!oldPassword.trim() || !newPassword.trim() || !confirmNewPassword.trim() || !passwordVerificationCode.trim()"
              @click="submitPasswordChange"
            >
              纭淇敼瀵嗙爜
            </n-button>
            <p v-if="passwordMsg" class="settings-hint">{{ passwordMsg }}</p>
          </div>
        </section>

        <section class="settings-section support-donate-section">
          <div class="section-head">
            <p class="settings-label">璇峰紑鍙戣€呭枬鏉ザ鑼?/p>
            <span class="section-tag">馃</span>
          </div>
          <p class="settings-desc">濡傛灉 MoodCopilot 甯埌浜嗕綘锛屾杩庤寮€鍙戣€呭枬鏉ザ鑼讹綖</p>
          <n-button quaternary type="primary" @click="showSettingsModal = false; router.push('/support')">
            鍘荤湅鐪?鈫?          </n-button>
        </section>

        <section class="settings-section">
          <div class="section-head">
            <p class="settings-label">寤鸿涓庡弽棣?/p>
            <span class="section-tag">Feedback</span>
          </div>
          <div class="settings-row" style="flex-direction: column; align-items: stretch; gap: 8px;">
            <n-input
              v-model:value="suggestionContent"
              type="textarea"
              :maxlength="1000"
              :autosize="{ minRows: 2, maxRows: 4 }"
              placeholder="閬囧埌Bug锛熸垨鑰呮湁濂界殑鍔熻兘寤鸿锛熻鍛婅瘔鎴戜滑鍚э紒"
            />
            <div style="display: flex; justify-content: flex-end; align-items: center; gap: 8px;">
              <n-button
                v-if="auth.isAdmin"
                size="small"
                secondary
                @click="viewAdminSuggestions"
              >
                鏌ョ湅鐢ㄦ埛寤鸿
              </n-button>
              <n-button
                size="small"
                type="primary"
                :loading="submittingSuggestion"
                :disabled="!suggestionContent.trim()"
                @click="submitSuggestion"
              >
                鎻愪氦鍙嶉
              </n-button>
            </div>
          </div>
        </section>

        <section class="settings-section danger-zone">
          <div class="section-head">
            <p class="settings-label">璐︽埛鎿嶄綔</p>
            <span class="section-tag">Security</span>
          </div>
          <n-button type="error" block @click="handleLogout">閫€鍑虹櫥褰?/n-button>
        </section>
      </div>
    </n-modal>

    <n-modal
      v-model:show="showAdminSuggestions"
      preset="card"
      title="鐢ㄦ埛寤鸿鍒楄〃"
      style="width: 90%; max-width: 600px; max-height: 80vh; overflow-y: auto;"
    >
      <div v-if="adminSuggestionsLoading" style="text-align: center; padding: 20px;">
        <n-spin size="small" />
      </div>
      <div v-else-if="!adminSuggestions.length" style="text-align: center; padding: 20px; color: #999;">
        鏆傛棤鐢ㄦ埛寤鸿
      </div>
      <div v-else class="admin-suggestions-list">
        <div v-for="s in adminSuggestions" :key="s.id" class="suggestion-item" style="border: 1px solid var(--color-border); border-radius: 8px; padding: 12px; margin-bottom: 12px;">
          <div style="display: flex; justify-content: space-between; margin-bottom: 8px; align-items: center;">
            <div style="display: flex; align-items: center; gap: 8px;">
              <img v-if="s.userAvatar" :src="s.userAvatar" style="width: 24px; height: 24px; border-radius: 50%;" />
              <span v-else style="width: 24px; height: 24px; border-radius: 50%; background: var(--color-border); display: inline-flex; align-items: center; justify-content: center; font-size: 12px;">{{ s.userName.charAt(0) }}</span>
              <strong>{{ s.userName }}</strong>
            </div>
            <span style="font-size: 12px; color: #999;">{{ new Date(s.createdAt).toLocaleString() }}</span>
          </div>
          <p style="white-space: pre-wrap; font-size: 14px; margin: 0;">{{ s.content }}</p>
        </div>
        <div v-if="hasMoreAdminSuggestions" style="text-align: center; margin-top: 12px;">
          <n-button size="small" :loading="adminSuggestionsLoadingMore" @click="loadMoreAdminSuggestions">鍔犺浇鏇村</n-button>
        </div>
      </div>
    </n-modal>

    <n-modal
      v-model:show="showCropModal"
      preset="card"
      title="瑁佸垏澶村儚"
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
        <n-button size="small" @click="zoomOut">鈭?/n-button>
        <span class="crop-zoom-label">缂╂斁</span>
        <n-button size="small" @click="zoomIn">锛?/n-button>
      </div>
      <template #action>
        <div class="crop-action-row">
          <n-button @click="showCropModal = false">鍙栨秷</n-button>
          <n-button type="primary" :loading="uploading" @click="handleCrop">纭畾</n-button>
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
import { authApi, diaryApi, memoryApi, suggestionApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { useDiaryStore, type Diary } from '../stores/diary'
import { useFollowStore } from '../stores/follow'
import { logWarn } from '../utils/logger'

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
  { label: '浠呰嚜宸辩湅', value: 'PRIVATE' },
  { label: '鍏紑', value: 'PUBLIC' },
]



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
const showAdminSuggestions = ref(false)
const adminSuggestions = ref<any[]>([])
const adminSuggestionsPage = ref(1)
const hasMoreAdminSuggestions = ref(false)
const adminSuggestionsLoading = ref(false)
const adminSuggestionsLoadingMore = ref(false)

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
    return '澶勭悊涓?..'
  }
  if (followStore.isFollowing(profileUserId.value)) {
    return profileFollowHover.value ? '鍙栨秷鍏虫敞' : '宸插叧娉?
  }
  return '+ 鍏虫敞'
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
const profileInitial = computed(() => (profileName.value || '鐢?).charAt(0))

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
    profileName.value = profile?.displayName || (isOwner.value ? auth.displayName || '鎴? : '鐢ㄦ埛')
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
  suggestionContent.value = ''
}

async function submitSuggestion() {
  if (!suggestionContent.value.trim()) return
  submittingSuggestion.value = true
  try {
    await suggestionApi.submit(suggestionContent.value.trim())
    window.$message?.success('鍙嶉鎻愪氦鎴愬姛锛屾劅璋綘鐨勫缓璁紒')
    suggestionContent.value = ''
  } catch (err: any) {
    window.$message?.error('鎻愪氦澶辫触锛? + (err.response?.data?.message || err.message))
  } finally {
    submittingSuggestion.value = false
  }
}

async function viewAdminSuggestions() {
  showAdminSuggestions.value = true
  adminSuggestionsLoading.value = true
  adminSuggestionsPage.value = 1
  try {
    const res = await suggestionApi.adminList(1, 20)
    adminSuggestions.value = res.data.data.items || []
    hasMoreAdminSuggestions.value = adminSuggestions.value.length < (res.data.data.total || 0)
  } catch (err: any) {
    window.$message?.error('鑾峰彇澶辫触锛? + err.message)
  } finally {
    adminSuggestionsLoading.value = false
  }
}

async function loadMoreAdminSuggestions() {
  if (adminSuggestionsLoadingMore.value || !hasMoreAdminSuggestions.value) return
  adminSuggestionsLoadingMore.value = true
  try {
    const nextPage = adminSuggestionsPage.value + 1
    const res = await suggestionApi.adminList(nextPage, 20)
    const items = res.data.data.items || []
    adminSuggestions.value.push(...items)
    adminSuggestionsPage.value = nextPage
    hasMoreAdminSuggestions.value = adminSuggestions.value.length < (res.data.data.total || 0)
  } catch (err: any) {
    window.$message?.error('鑾峰彇澶辫触锛? + err.message)
  } finally {
    adminSuggestionsLoadingMore.value = false
  }
}



function triggerUpload() {
  fileInput.value?.click()
}

async function onFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return

  if (file.size > 10 * 1024 * 1024) {
    uploadMsg.value = '鏂囦欢澶у皬涓嶈兘瓒呰繃 10MB'
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
      uploadMsg.value = '澶村儚宸叉洿鏂?
      showCropModal.value = false
    } catch (e) {
      uploadMsg.value = '涓婁紶澶辫触'
      logWarn('profile', '澶村儚涓婁紶澶辫触', e)
    } finally {
      uploading.value = false
    }
  }, 'image/jpeg', 0.92)
}

async function checkEditingName() {
  const name = editingName.value.trim()
  if (!name || name === auth.displayName) {
    if (nameMsg.value === '璇ョ敤鎴峰悕宸茶鍗犵敤') nameMsg.value = ''
    return
  }
  try {
    const res = await authApi.checkUsername(name)
    if (!res.data.data.available) {
      nameMsg.value = '璇ョ敤鎴峰悕宸茶鍗犵敤'
    } else {
      if (nameMsg.value === '璇ョ敤鎴峰悕宸茶鍗犵敤') nameMsg.value = ''
    }
  } catch (e) {
    logWarn('profile', '妫€鏌ョ敤鎴峰悕鍙敤鎬уけ璐?, name, e)
  }
}

async function saveName() {
  const name = editingName.value.trim()
  if (!name || name === auth.displayName) return

  savingName.value = true
  nameMsg.value = ''
  try {
    await auth.updateProfile(name, undefined)
    if (isOwner.value) {
      profileName.value = auth.displayName || '鎴?
    }
    nameMsg.value = '鐢ㄦ埛鍚嶅凡鏇存柊'
    editingName.value = auth.displayName ?? ''
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || ''
    if (msg && msg !== '璇ョ敤鎴峰悕宸茶鍗犵敤') nameMsg.value = msg
    else if (!msg) nameMsg.value = '淇濆瓨澶辫触'
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
    signatureMsg.value = '涓€х鍚嶅凡鏇存柊'
  } catch (e) {
    logWarn('profile', '淇濆瓨绛惧悕澶辫触', e)
    signatureMsg.value = '淇濆瓨澶辫触'
  } finally {
    savingSignature.value = false
  }
}

async function toggleNotify(val: boolean) {
  toggling.value = true
  try {
    await auth.updateSettings(val)
  } catch (e) {
    logWarn('profile', '鏇存柊閫氱煡璁剧疆澶辫触', e)
  } finally {
    toggling.value = false
  }
}

async function toggleProfileNotify(val: boolean) {
  toggling.value = true
  try {
    await auth.updateSettings(auth.dailyNotifyEnabled, val)
  } catch (e) {
    logWarn('profile', '鏇存柊鐢诲儚閫氱煡璁剧疆澶辫触', e)
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
    passwordMsg.value = '楠岃瘉鐮佸凡鍙戦€佸埌浣犵殑娉ㄥ唽閭'
  } catch (e: any) {
    passwordMsg.value = e?.response?.data?.message || '楠岃瘉鐮佸彂閫佸け璐?
  } finally {
    sendingPasswordCode.value = false
  }
}

async function submitPasswordChange() {
  if (!oldPassword.value.trim()) {
    passwordMsg.value = '璇疯緭鍏ュ綋鍓嶅瘑鐮?
    return
  }
  if (newPassword.value.trim().length < 6) {
    passwordMsg.value = '鏂板瘑鐮佽嚦灏?6 浣?
    return
  }
  if (!confirmNewPassword.value.trim()) {
    passwordMsg.value = '璇峰啀娆¤緭鍏ユ柊瀵嗙爜'
    return
  }
  if (newPassword.value.trim() !== confirmNewPassword.value.trim()) {
    passwordMsg.value = '涓ゆ杈撳叆鐨勬柊瀵嗙爜涓嶄竴鑷?
    return
  }
  if (!passwordVerificationCode.value.trim()) {
    passwordMsg.value = '璇疯緭鍏ラ獙璇佺爜'
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
    passwordMsg.value = e?.response?.data?.message || '瀵嗙爜淇敼澶辫触'
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
