<template>
  <main class="app-shell">
    <AppHeader />

    <section class="fusion-panel">
      <div class="fusion-bg"></div>
      <div class="fusion-content profile-head">
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
        <div class="avatar-wrap">
          <div v-if="profileLoading" class="avatar-img" style="background: var(--color-primary); color: white; display: flex; align-items: center; justify-content: center;">
            <n-spin size="small" />
          </div>
          <img v-else-if="profileAvatar" :src="profileAvatar" class="avatar-img" decoding="async" />
          <span v-else class="avatar-img" style="background: var(--color-primary); color: white; display: flex; align-items: center; justify-content: center; font-size: 28px; font-family: var(--font-display);">{{ profileInitial }}</span>
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
      </div>
    </section>



    <section class="fusion-panel">
      <div class="fusion-content profile-list-panel">
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
      <ProfileSearchPanel
        v-if="isOwner"
        :show="showSearchPanel"
        v-model:keyword="keyword"
        v-model:startDate="startDateVal"
        v-model:endDate="endDateVal"
        v-model:visibility="visibilityFilter"
        :visibility-opts="visibilityOpts"
        :date-disabled="dateDisabled"
        :loading="loading"
        @search="triggerSearch"
        @clear="clearFilters"
      />

      <div v-if="diaries.length" class="feed">
        <DynamicScroller
          :items="diaries"
          :min-item-size="200"
          key-field="id"
          page-mode
        >
          <template #default="{ item, index, active }">
            <DynamicScrollerItem
              :item="item"
              :active="active"
              :size-dependencies="[
                item.content,
                item.images?.length,
                item.comments?.length,
                item.musicMeta?.songUrl
              ]"
              :data-index="index"
            >
              <DiaryFeedItem
                :diary="item"
                :enable-comments="false"
                :compact="true"
                :preview-limit="120"
                :show-expand-toggle="false"
                :hide-follow-btn="!isOwner"
                :show-visibility-badge="isOwner"
                @resonate="(d: Diary) => store.resonate(d.id, d)"
                @open-detail="(d: Diary) => router.push(`/diary/${d.id}`)"
              />
            </DynamicScrollerItem>
          </template>
        </DynamicScroller>

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
      </div>
    </section>

    <ProfileSettingsModal
      v-model:show="showSettingsModal"
      :is-owner="isOwner"
      @profile-updated="handleProfileUpdated"
      @open-admin-suggestions="showAdminSuggestions = true"
    />
    <AdminSuggestionsModal v-model:show="showAdminSuggestions" />
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NEmpty, NInput, NModal, NSpin, NSwitch, NDatePicker, NSelect, NTabs, NTabPane, NTag, NCheckbox, NPopover } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import ProfileSettingsModal from '../components/profile/ProfileSettingsModal.vue'
import ProfileSearchPanel from '../components/profile/ProfileSearchPanel.vue'
import AdminSuggestionsModal from '../components/profile/AdminSuggestionsModal.vue'
import DiaryFeedItem from '../components/DiaryFeedItem.vue'
import { authApi, diaryApi, memoryApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { useDiaryStore, type Diary } from '../stores/diary'
import { useFollowStore } from '../stores/follow'
import { useInfiniteScroll } from '../composables/useScrollManager'
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
  { label: '仅自己看', value: 'PRIVATE' },
  { label: '公开', value: 'PUBLIC' },
]



const showAdminSuggestions = ref(false)

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

useInfiniteScroll(sentinel, loadMore, { enabled: hasMore, rootMargin: '300px' })

onMounted(() => {
  void reload()
})

watch(() => route.params.userId, () => {
  void reload()
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

function handleProfileUpdated() {
  if (isOwner.value) {
    profileName.value = auth.displayName || '我'
    profileSignature.value = auth.signature || ''
    profileAvatar.value = auth.avatar || null
  }
}








</script>

<style scoped>
.fusion-panel {
  position: relative;
  margin-bottom: 24px;
}

.fusion-bg {
  position: absolute;
  inset: 0;
  background: color-mix(in oklab, var(--color-surface) 90%, #e8dcc5);
  border-radius: 8px;
  transform: rotate(0.4deg) translateY(2px);
  box-shadow: 2px 6px 16px rgba(0,0,0,0.03);
  z-index: 0;
  border: 1px solid color-mix(in oklab, var(--color-border) 40%, transparent);
}

.fusion-content {
  position: relative;
  background: var(--color-surface);
  border-radius: 8px;
  padding: 30px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.03);
  z-index: 1;
  border: 1px solid var(--color-border);
  background-image: linear-gradient(135deg, transparent 80%, color-mix(in oklab, var(--color-primary) 1.5%, transparent));
}

.profile-head {
  display: flex;
  flex-direction: column;
}

.profile-settings-trigger {
  position: absolute;
  right: 16px;
  top: 16px;
  font-size: 20px;
  z-index: 10;
}

.profile-hero {
  display: flex;
  align-items: center;
  gap: 24px;
  position: relative;
}

.avatar-wrap {
  width: 80px;
  height: 80px;
  border-radius: 4px;
  background: #fff;
  padding: 6px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  transform: rotate(-1.5deg);
  flex-shrink: 0;
  border: 1px solid color-mix(in oklab, var(--color-border) 40%, transparent);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 2px;
  background: var(--color-primary-light);
}

.profile-main {
  flex: 1;
  min-width: 0;
}

.profile-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.profile-title {
  margin: 0 0 8px 0;
  font-family: var(--font-display);
  color: var(--color-text);
  font-size: clamp(24px, 4vw, 32px);
  font-weight: 600;
  line-height: 1.2;
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
  color: var(--color-on-primary);
}

.profile-follow-btn:hover {
  opacity: 0.82;
}

.profile-signature {
  font-size: 14.5px;
  color: var(--color-text-secondary);
  font-style: italic;
  margin: 0;
  padding-left: 12px;
  border-left: 2px solid var(--color-primary);
  line-height: 1.6;
}

.profile-list-panel {
  min-height: 180px;
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
  font-size: 1.4rem;
  font-family: var(--font-display);
  font-weight: 600;
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
  border: 1px solid color-mix(in oklab, var(--color-primary) 24%, transparent 76%);
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
  background: color-mix(in oklab, var(--color-surface-soft) 50%, transparent);
  border: 1px solid color-mix(in oklab, var(--color-border) 40%, transparent);
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
  border-color: color-mix(in oklab, var(--color-accent) 20%, transparent 80%);
}

.crop-area {
  display: flex;
  justify-content: center;
  align-items: center;
  background: var(--color-surface-soft);
  border: 1px solid color-mix(in oklab, var(--color-border-strong) 22%, transparent 78%);
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



.search-panel-card {
  margin-bottom: 20px;
  padding: 16px;
  background: var(--color-surface-soft);
  border: 1px solid color-mix(in oklab, var(--color-border-strong) 15%, transparent 85%);
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
  border-top: 1px dashed color-mix(in oklab, var(--color-border-strong) 25%, transparent 75%);
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

@media (max-width: 640px) {
  .fusion-content {
    padding: 20px;
  }
  
  .profile-hero {
    flex-direction: column;
    text-align: center;
    gap: 16px;
  }
  
  .avatar-wrap {
    transform: rotate(0deg);
  }
  
  .profile-signature {
    border-left: none;
    padding-left: 0;
  }
  
  .profile-list-head {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .profile-list-head h3 {
    font-size: 1.2rem;
  }
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
