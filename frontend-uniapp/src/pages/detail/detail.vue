<template>
  <view class="detail-page" :style="globalThemeStyle">
    <GlobalUI />

    <view class="mood-glow" :style="diaryMoodColor !== 'transparent' ? { background: `radial-gradient(circle 600rpx at top right, ${diaryMoodColor}, transparent 80%)` } : { display: 'none' }" />

    <view v-if="loading" class="loading-state">正在打开日记...</view>

    <scroll-view v-else-if="diary" scroll-y class="detail-scroll" :show-scrollbar="false">
      <view class="detail-content">
        <view class="diary-entry">
          <view class="entry-header">
            <view class="author-info">
              <text class="entry-datetime">{{ formatDateTime(diary.createdAt) }}</text>
              <view v-if="isOwner" class="edit-action" @click="editDiary">
                <text>编辑</text>
              </view>
            </view>
            <view class="entry-quick-actions">
              <view v-if="isOwner" class="action-btn delete-btn" style="margin-left: auto;" @click="confirmDelete">
                <text class="action-icon">🗑</text>
                <text>删除日记</text>
              </view>
            </view>
          </view>

          <rich-text class="diary-text" :nodes="formatDiaryContent(diary.content)" />

          <view v-if="diary.images?.length" class="diary-images">
            <image
              v-for="(image, index) in diary.images"
              :key="`${diary.id}-${index}`"
              :src="image"
              mode="aspectFill"
              class="diary-image"
              @click="previewImage(image, diary.images)"
            />
          </view>

          <MusicCard v-if="diary.musicMeta" :music-meta="diary.musicMeta" label="这一刻在听" class="entry-music" />

          <view v-if="parentCollections.length" class="collection-row">
            <text class="collection-label">收录于</text>
            <view
              v-for="collection in parentCollections"
              :key="collection.id"
              class="collection-chip"
              @click="goToCollection(collection.id)"
            >
              <text>{{ collection.name }}</text>
              <text class="chip-arrow">›</text>
            </view>
          </view>
        </view>

        <view v-if="diary.analysisStatus === 'analyzing'" class="analysis-card analysis-pending">
          <text class="analysis-kicker">AI 正在阅读这篇记录</text>
          <text class="analysis-copy">分析完成后会出现在这里。</text>
        </view>
        <view v-else-if="diary.analysis?.summary" class="analysis-card">
          <text class="analysis-kicker">AI 分析</text>
          
          <view class="analysis-mood-header">
            <text class="analysis-mood-label" :style="diaryMoodColor !== 'transparent' ? { color: diaryMoodColor } : {}">{{ moodLabel || '心情' }}</text>
            <text v-if="diary.analysis.moodIntensity" class="analysis-mood-intensity"> · 强度 {{ diary.analysis.moodIntensity }}/5</text>
            <view v-if="topicLabels.length" class="analysis-topic-list">
              <text v-for="tag in topicLabels" :key="tag" class="topic-tag">{{ tag }}</text>
            </view>
          </view>

          <view v-if="diary.analysis.moodIntensity" class="mood-meter">
            <view v-for="step in 5" :key="step" class="meter-step" :class="{ filled: step <= diary.analysis.moodIntensity }" :style="step <= diary.analysis.moodIntensity && diaryMoodColor !== 'transparent' ? { background: diaryMoodColor } : {}"></view>
          </view>

          <text class="analysis-copy">{{ diary.analysis.feedback || diary.analysis.summary }}</text>
        </view>

        <view class="entry-actions">
          <view class="chat-action" @click="quoteToChat">
            <text class="chat-action-icon">✦</text>
            <text>和 AI 聊聊这篇日记</text>
            <text class="chat-action-arrow">›</text>
          </view>
          <view class="collection-action" @click="showCollectionModal = true">
            <text>加入合集</text>
            <text class="chat-action-arrow">›</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <view v-else class="missing-state">这篇日记暂时无法打开</view>

    <view v-if="showCollectionModal" class="modal-overlay" @click="showCollectionModal = false">
      <view class="collection-sheet" @click.stop>
        <view class="sheet-handle" />
        <view class="sheet-header">
          <text class="sheet-title">加入合集</text>
          <text class="sheet-close" @click="showCollectionModal = false">×</text>
        </view>
        <scroll-view scroll-y class="collection-list" :show-scrollbar="false">
          <text v-if="myCollections.length === 0" class="collection-empty">还没有创建合集</text>
          <view v-for="collection in myCollections" :key="collection.id" class="collection-item" @click="addToCollection(collection.id)">
            <text>{{ collection.name }}</text>
            <text class="collection-add">加入</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import GlobalUI from '@/components/GlobalUI.vue'
import MusicCard from '@/components/MusicCard.vue'
import { get, post, request } from '@/utils/request'
import { formatDiaryContent } from '@/utils/markdown'
import { currentUser, fetchCurrentUser } from '@/stores/user'
import { moodColor } from '@/utils/mood'

const loading = ref(true)
const diary = ref<any>(null)
const showCollectionModal = ref(false)
const myCollections = ref<any[]>([])
const parentCollections = ref<any[]>([])

const diaryMoodColor = computed(() => {
  if (diary.value?.analysis && isOwner.value) {
    return moodColor(diary.value.analysis.moodLabel, diary.value.analysis.valence, diary.value.analysis.arousal)
  }
  return 'transparent'
})

const isOwner = computed(() => Boolean(currentUser.value && diary.value && currentUser.value.userId === diary.value.authorUserId))

const moodLabel = computed(() => diary.value?.analysis?.moodLabel || '')
const topicLabels = computed(() => {
  const labels = diary.value?.analysis?.topicLabelsJson
  if (!labels) return []
  try {
    const parsed = JSON.parse(labels)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
})

onLoad(async (options: any) => {
  if (!currentUser.value) await fetchCurrentUser()
  if (options.id) {
    void fetchDiary(options.id)
    void fetchParentCollections(options.id)
  } else {
    loading.value = false
  }
  void fetchMyCollections()
})

async function fetchDiary(id: string | number) {
  loading.value = true
  try {
    const response = await get(`/api/diaries/${id}`)
    if (response.code === 200) diary.value = response.data
  } catch (error) {
    console.error('Failed to fetch diary', error)
  } finally {
    loading.value = false
  }
}

async function fetchParentCollections(diaryId: number) {
  try {
    const response = await get(`/api/collections/by-diary/${diaryId}`)
    if (response.code === 200) parentCollections.value = response.data || []
  } catch (error) {
    console.error('Failed to fetch diary collections', error)
  }
}

async function fetchMyCollections() {
  try {
    const response = await get('/api/collections/mine?page=1&size=50')
    if (response.code === 200) myCollections.value = response.data?.records || response.data?.items || response.data?.content || []
  } catch (error) {
    console.error('Failed to fetch collections', error)
  }
}

async function addToCollection(collectionId: number) {
  try {
    const response = await post(`/api/collections/${collectionId}/diaries`, { diaryIds: [diary.value.id] })
    if (response.code === 200) {
      uni.showToast({ title: '已加入合集', icon: 'success' })
      showCollectionModal.value = false
      void fetchParentCollections(diary.value.id)
      return
    }
    uni.showToast({ title: response.message || '操作失败', icon: 'none' })
  } catch {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

function openOwnerActions() {
  uni.showActionSheet({
    itemList: ['编辑日记', '删除日记'],
    itemColor: '#365f4c',
    success: ({ tapIndex }) => {
      if (tapIndex === 0) {
        uni.navigateTo({ url: `/pages/write/write?id=${diary.value.id}&mode=edit` })
        return
      }
      confirmDelete()
    },
  })
}

function confirmDelete() {
  uni.showModal({
    title: '删除日记',
    content: '删除后无法恢复，确定继续吗？',
    confirmColor: '#c74d4d',
    success: async ({ confirm }) => {
      if (!confirm) return
      try {
        const response = await request(`/api/diaries/${diary.value.id}`, 'DELETE')
        if (response.code === 200) {
          uni.showToast({ title: '已删除', icon: 'success' })
          setTimeout(() => uni.navigateBack(), 500)
        }
      } catch {
        uni.showToast({ title: '删除失败', icon: 'none' })
      }
    },
  })
}

function editDiary() {
  uni.navigateTo({ url: `/pages/write/write?id=${diary.value.id}&mode=edit` })
}

function formatDateTime(value: string) {
  const date = new Date(value)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${month}/${day} ${hours}:${minutes}`
}

function quoteToChat() {
  const text = `关于我的这篇日记（${formatDateTime(diary.value.createdAt)}）：\n${diary.value.content}`
  uni.setStorageSync('pendingQuote', text)
  uni.switchTab({ url: '/pages/chat/chat' })
}

function previewImage(current: string, urls: string[]) {
  uni.previewImage({ current, urls })
}

function goToCollection(collectionId: number) {
  uni.navigateTo({ url: `/pages/collections/detail?id=${collectionId}` })
}
</script>

<style scoped>
.detail-page { min-height: 100vh; background: var(--theme-bg); position: relative; }
.mood-glow { position: absolute; top: 0; left: 0; right: 0; height: 600rpx; opacity: 0.2; pointer-events: none; z-index: 0; }
.detail-scroll { height: 100vh; position: relative; z-index: 1; }
.detail-content { padding: 32rpx 32rpx calc(54rpx + env(safe-area-inset-bottom)); }
.loading-state, .missing-state { padding-top: 240rpx; color: var(--theme-text-placeholder); font-size: 27rpx; text-align: center; }
.diary-entry, .analysis-card { border: 1rpx solid var(--theme-border); border-radius: 8rpx; background: var(--theme-surface); box-shadow: 0 6rpx 18rpx rgba(29, 38, 32, .035); }
.diary-entry { padding: 34rpx 30rpx 30rpx; }
.entry-header { display: flex; flex-direction: column; gap: 20rpx; padding-bottom: 24rpx; border-bottom: 1rpx solid var(--theme-border); }
.author-info { display: flex; align-items: center; gap: 14rpx; flex-wrap: wrap; }
.entry-datetime { color: var(--theme-text-primary); font-size: 30rpx; font-weight: 650; }
.edit-action { margin-left: auto; color: var(--theme-text-secondary); font-size: 24rpx; padding: 10rpx; }
.entry-quick-actions { display: flex; gap: 24rpx; margin-top: 4rpx; }
.action-btn { display: flex; align-items: center; gap: 8rpx; font-size: 24rpx; font-weight: 600; padding: 8rpx 0; }
.action-icon { font-size: 28rpx; }
.delete-btn { color: #d65c5c; }
.delete-btn .action-icon { color: #d65c5c; }
.mood-tag { padding: 7rpx 14rpx; border-radius: 999rpx; background: rgba(var(--theme-primary-rgb), .09); color: var(--theme-primary); font-size: 22rpx; margin-bottom: 10rpx; display: inline-block; }
.diary-text { display: block; margin-top: 30rpx; color: var(--theme-text-primary); font-size: 31rpx; line-height: 1.86; word-break: break-word; }
.diary-images { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10rpx; margin-top: 28rpx; }
.diary-image { width: 100%; height: 294rpx; border-radius: 6rpx; background: rgba(var(--theme-primary-rgb), .07); }
.diary-images .diary-image:only-child { grid-column: span 2; height: 410rpx; }
.entry-music { margin-top: 28rpx; }
.collection-row { display: flex; flex-wrap: wrap; align-items: center; gap: 10rpx; margin-top: 26rpx; padding-top: 24rpx; border-top: 1rpx solid var(--theme-border); }
.collection-label { margin-right: 2rpx; color: var(--theme-text-placeholder); font-size: 22rpx; }
.collection-chip { display: inline-flex; align-items: center; gap: 5rpx; padding: 8rpx 11rpx; border-radius: 5rpx; background: rgba(var(--theme-primary-rgb), .06); color: var(--theme-primary); font-size: 22rpx; }
.chip-arrow, .chat-action-arrow { font-size: 30rpx; font-weight: 300; line-height: .7; }
.analysis-card { margin-top: 20rpx; padding: 32rpx; }
.analysis-pending { border-style: dashed; background: rgba(var(--theme-primary-rgb), .025); }
.analysis-kicker { display: block; color: #a86c6c; font-size: 22rpx; font-weight: 650; letter-spacing: 1rpx; margin-bottom: 16rpx; }
.analysis-mood-header { display: flex; align-items: center; flex-wrap: wrap; margin-bottom: 20rpx; }
.analysis-mood-label { font-size: 32rpx; font-weight: 650; }
.analysis-mood-intensity { color: var(--theme-text-secondary); font-size: 28rpx; font-weight: 500; margin-left: 6rpx; }
.analysis-topic-list { display: flex; flex-wrap: wrap; gap: 8rpx; margin-left: 16rpx; }
.topic-tag { padding: 4rpx 14rpx; border-radius: 99rpx; border: 1rpx solid rgba(var(--theme-text-primary-rgb), .08); color: var(--theme-text-secondary); font-size: 20rpx; }
.mood-meter { display: flex; gap: 10rpx; margin-bottom: 30rpx; }
.meter-step { flex: 1; height: 12rpx; border-radius: 6rpx; background: rgba(var(--theme-text-primary-rgb), .06); }
.meter-step.filled { background: var(--theme-primary); }
.analysis-copy { display: block; color: var(--theme-text-primary); font-size: 28rpx; line-height: 1.8; white-space: pre-line; }
.entry-actions { margin-top: 20rpx; border-top: 1rpx solid var(--theme-border); }
.chat-action, .collection-action { display: flex; align-items: center; padding: 25rpx 8rpx; color: var(--theme-text-primary); font-size: 26rpx; }
.chat-action { color: var(--theme-primary); font-weight: 600; }
.chat-action-icon { margin-right: 11rpx; font-size: 28rpx; }
.chat-action-arrow { margin-left: auto; color: var(--theme-text-placeholder); }
.collection-action { border-top: 1rpx solid var(--theme-border); }
.modal-overlay { position: fixed; top: 0; right: 0; bottom: 0; left: 0; display: flex; align-items: flex-end; background: rgba(21, 25, 22, .4); z-index: 50; }
.collection-sheet { width: 100%; max-height: 68vh; padding: 16rpx 32rpx calc(32rpx + env(safe-area-inset-bottom)); border-radius: 12rpx 12rpx 0 0; background: var(--theme-surface); box-sizing: border-box; }
.sheet-handle { width: 54rpx; height: 6rpx; margin: 0 auto 26rpx; border-radius: 99rpx; background: var(--theme-border); }
.sheet-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.sheet-title { color: var(--theme-text-primary); font-size: 32rpx; font-weight: 650; }
.sheet-close { width: 48rpx; color: var(--theme-text-secondary); font-size: 42rpx; font-weight: 300; text-align: right; line-height: 1; }
.collection-list { max-height: 730rpx; }
.collection-empty { display: block; padding: 54rpx 0; color: var(--theme-text-placeholder); font-size: 25rpx; text-align: center; }
.collection-item { display: flex; align-items: center; justify-content: space-between; padding: 26rpx 4rpx; border-bottom: 1rpx solid var(--theme-border); color: var(--theme-text-primary); font-size: 27rpx; }
.collection-add { color: var(--theme-primary); font-size: 23rpx; }
</style>
