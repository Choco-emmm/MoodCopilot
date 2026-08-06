<template>
  <view class="detail-page" :style="globalThemeStyle">
    <GlobalUI />

    <view v-if="loading" class="loading-state">正在打开日记...</view>

    <scroll-view v-else-if="diary" scroll-y class="detail-scroll" :show-scrollbar="false">
      <view class="detail-content">
        <view class="diary-entry">
          <view class="entry-meta">
            <view>
              <text class="entry-date">{{ formatDate(diary.createdAt) }}</text>
              <text class="entry-time">{{ timeOf(diary.createdAt) }}</text>
            </view>
            <view class="entry-meta-right">
              <text v-if="moodLabel" class="mood-tag">{{ moodLabel }}</text>
              <view v-if="isOwner" class="manage-button" @click="openOwnerActions">
                <text class="manage-icon">•••</text>
                <text>管理</text>
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
          <view class="analysis-title-row">
            <text class="analysis-kicker">这一天的回声</text>
            <text v-if="diary.analysis.moodIntensity" class="analysis-score">{{ diary.analysis.moodIntensity }}/10</text>
          </view>
          <view v-if="topicLabels.length" class="topic-list">
            <text v-for="tag in topicLabels" :key="tag" class="topic-tag">{{ tag }}</text>
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

const loading = ref(true)
const diary = ref<any>(null)
const showCollectionModal = ref(false)
const myCollections = ref<any[]>([])
const parentCollections = ref<any[]>([])

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

function formatDate(value: string) {
  const date = new Date(value)
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

function timeOf(value: string) {
  const date = new Date(value)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function quoteToChat() {
  const text = `关于我的这篇日记（${formatDate(diary.value.createdAt)}）：\n${diary.value.content}`
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
.detail-page { min-height: 100vh; background: var(--theme-bg); }
.detail-scroll { height: 100vh; }
.detail-content { padding: 32rpx 32rpx calc(54rpx + env(safe-area-inset-bottom)); }
.loading-state, .missing-state { padding-top: 240rpx; color: var(--theme-text-placeholder); font-size: 27rpx; text-align: center; }
.diary-entry, .analysis-card { border: 1rpx solid var(--theme-border); border-radius: 8rpx; background: var(--theme-surface); box-shadow: 0 6rpx 18rpx rgba(29, 38, 32, .035); }
.diary-entry { padding: 34rpx 30rpx 30rpx; }
.entry-meta { display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; padding-bottom: 24rpx; border-bottom: 1rpx solid var(--theme-border); }
.entry-date { display: block; color: var(--theme-text-primary); font-size: 30rpx; font-weight: 650; line-height: 1.3; }
.entry-time { display: block; margin-top: 7rpx; color: var(--theme-text-placeholder); font-size: 22rpx; }
.entry-meta-right { display: flex; align-items: center; gap: 12rpx; }
.mood-tag { padding: 7rpx 14rpx; border-radius: 999rpx; background: rgba(var(--theme-primary-rgb), .09); color: var(--theme-primary); font-size: 22rpx; }
.manage-button { display: flex; align-items: center; gap: 5rpx; padding: 7rpx 0 7rpx 9rpx; color: var(--theme-text-secondary); font-size: 22rpx; }
.manage-icon { color: var(--theme-primary); font-size: 26rpx; font-weight: 700; letter-spacing: 1rpx; line-height: .8; }
.diary-text { display: block; margin-top: 30rpx; color: var(--theme-text-primary); font-size: 31rpx; line-height: 1.86; word-break: break-word; }
.diary-images { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10rpx; margin-top: 28rpx; }
.diary-image { width: 100%; height: 294rpx; border-radius: 6rpx; background: rgba(var(--theme-primary-rgb), .07); }
.diary-images .diary-image:only-child { grid-column: span 2; height: 410rpx; }
.entry-music { margin-top: 28rpx; }
.collection-row { display: flex; flex-wrap: wrap; align-items: center; gap: 10rpx; margin-top: 26rpx; padding-top: 24rpx; border-top: 1rpx solid var(--theme-border); }
.collection-label { margin-right: 2rpx; color: var(--theme-text-placeholder); font-size: 22rpx; }
.collection-chip { display: inline-flex; align-items: center; gap: 5rpx; padding: 8rpx 11rpx; border-radius: 5rpx; background: rgba(var(--theme-primary-rgb), .06); color: var(--theme-primary); font-size: 22rpx; }
.chip-arrow, .chat-action-arrow { font-size: 30rpx; font-weight: 300; line-height: .7; }
.analysis-card { margin-top: 20rpx; padding: 28rpx 30rpx; }
.analysis-pending { border-style: dashed; background: rgba(var(--theme-primary-rgb), .025); }
.analysis-title-row { display: flex; align-items: center; justify-content: space-between; }
.analysis-kicker { display: block; color: var(--theme-primary); font-size: 24rpx; font-weight: 650; }
.analysis-score { color: var(--theme-text-placeholder); font-size: 22rpx; }
.topic-list { display: flex; flex-wrap: wrap; gap: 8rpx; margin-top: 17rpx; }
.topic-tag { padding: 5rpx 10rpx; border-radius: 4rpx; background: rgba(var(--theme-primary-rgb), .08); color: var(--theme-primary); font-size: 20rpx; }
.analysis-copy { display: block; margin-top: 15rpx; color: var(--theme-text-secondary); font-size: 25rpx; line-height: 1.75; white-space: pre-line; }
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
