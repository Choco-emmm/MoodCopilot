<template>
  <view class="detail-page" :style="globalThemeStyle">
    <GlobalUI />

    <view v-if="loading" class="loading-state">正在打开日记...</view>

    <scroll-view v-else-if="diary" scroll-y class="detail-scroll" :show-scrollbar="false">
      <view class="detail-content">
        <view class="diary-entry">
          <view class="mood-glow" :style="diaryMoodColor !== 'transparent' ? { background: `radial-gradient(circle 1000rpx at 100% 0%, ${diaryMoodColor}, transparent 80%)` } : { display: 'none' }" />

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

        <view v-if="diary.analysisStatus === 'skipped_quota'" class="analysis-card analysis-pending">
          <text class="analysis-kicker">今日分析次数已用完</text>
          <text class="analysis-copy">日记已保存，额度恢复后可以重新分析。</text>
          <button class="analysis-retry" :disabled="retrying" @click="chooseRetryModel">重新分析</button>
        </view>
        <view v-else-if="diary.analysisStatus === 'failed_limit'" class="analysis-card analysis-pending">
          <text class="analysis-kicker">深度思考额度已用完</text>
          <text class="analysis-copy">日记已保存，可稍后重试或改用普通分析。</text>
          <button class="analysis-retry" :disabled="retrying" @click="chooseRetryModel">{{ retrying ? '正在提交...' : '重新分析' }}</button>
        </view>
        <view v-else-if="diary.analysisStatus === 'analyzing'" class="analysis-card analysis-pending">
          <text class="analysis-kicker">AI 正在阅读这篇记录</text>
          <text class="analysis-copy">分析完成后会出现在这里。</text>
        </view>
        <view v-else-if="diary.analysisStatus === 'failed'" class="analysis-card analysis-pending">
          <text class="analysis-kicker">分析未完成</text>
          <text class="analysis-copy">{{ diary.analysisError || '日记已保存，本次分析没有完成。' }}</text>
          <button class="analysis-retry" :disabled="retrying" @click="chooseRetryModel">{{ retrying ? '正在提交...' : '重新分析' }}</button>
        </view>
        <view v-else-if="diary.analysis?.summary" class="analysis-card">
          <text class="analysis-kicker">AI 分析</text>
          
          <view class="analysis-mood-header">
            <text class="analysis-mood-label" :style="diaryMoodColor !== 'transparent' ? { color: diaryMoodColor } : {}">{{ moodLabel || '心情' }}</text>
            <text v-if="diary.analysis.moodIntensity" class="analysis-mood-intensity"> · 强度 {{ diary.analysis.moodIntensity }}/5</text>
            <text class="mood-guide-icon" @click="showGuide">ⓘ</text>
            
            <view v-if="secondaryMoods.length" class="secondary-moods">
              <text v-for="m in secondaryMoods" :key="m" class="secondary-mood-tag">{{ m }}</text>
            </view>
            <view v-else-if="topicLabels.length" class="secondary-moods">
              <text v-for="tag in topicLabels" :key="tag" class="secondary-mood-tag">{{ tag }}</text>
            </view>
          </view>

          <view v-if="diary.analysis.moodIntensity" class="mood-meter">
            <view v-for="step in 5" :key="step" class="meter-step" :class="{ filled: step <= diary.analysis.moodIntensity }" />
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
import { onLoad, onUnload } from '@dcloudio/uni-app'
import GlobalUI from '@/components/GlobalUI.vue'
import MusicCard from '@/components/MusicCard.vue'
import { get, post, request } from '@/utils/request'
import { formatDiaryContent, extractPlainText } from '@/utils/markdown'
import { currentUser, fetchCurrentUser } from '@/stores/user'
import { moodColor } from '@/utils/mood'
import { setQuote } from '@/stores/quote'
import { currentTheme } from '@/stores/theme'

const loading = ref(true)
const diary = ref<any>(null)
const showCollectionModal = ref(false)
const myCollections = ref<any[]>([])
const parentCollections = ref<any[]>([])
const retrying = ref(false)
let analysisPollTimer: ReturnType<typeof setTimeout> | null = null

const diaryMoodColor = computed(() => {
  if (diary.value?.analysis && isOwner.value) {
    return moodColor(diary.value.analysis.moodLabel, diary.value.analysis.valence, diary.value.analysis.arousal)
  }
  return 'transparent'
})

const isOwner = computed(() => Boolean(currentUser.value && diary.value && currentUser.value.userId === diary.value.authorUserId))

const moodLabel = computed(() => diary.value?.analysis?.moodLabel || '')
const secondaryMoods = computed(() => {
  const a = diary.value?.analysis
  if (!a) return []
  const raw = a.secondaryMoods || a.secondaryMoodsJson
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw)
      return Array.isArray(parsed) ? parsed : [raw]
    } catch {
      return [raw]
    }
  }
  return []
})

const topicLabels = computed(() => {
  const labels = diary.value?.analysis?.topicLabelsJson || diary.value?.analysis?.topicLabels
  if (!labels) return []
  if (Array.isArray(labels)) return labels
  try {
    const parsed = JSON.parse(labels)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
})

function showGuide() {
  uni.showModal({
    title: '情绪强度指南',
    content: '1: 极其轻微，一闪而过\n2: 背景情绪，细细感知才注意\n3: 清晰的情感，影响当前注意力\n4: 强烈情感，驱动身体或行为反应\n5: 几乎失控，难以独自承受',
    showCancel: false,
    confirmText: '我知道了'
  })
}

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
    if (response.code === 200) {
      diary.value = response.data
      if (diary.value?.analysisStatus === 'analyzing') startAnalysisPolling()
      else stopAnalysisPolling()
    }
  } catch (error) {
    console.error('Failed to fetch diary', error)
  } finally {
    loading.value = false
  }
}

function chooseRetryModel() {
  uni.showActionSheet({
    itemList: ['普通分析', '深度思考'],
    success: ({ tapIndex }) => { void retryAnalysis(tapIndex === 1) },
  })
}

async function retryAnalysis(useReasoning: boolean) {
  if (!diary.value || retrying.value) return
  retrying.value = true
  try {
    const response = await request(`/api/diaries/${diary.value.id}/analysis/retry`, 'POST', { useReasoning })
    if (response.code === 200) {
      diary.value = response.data
      if (response.data?.analysisStatus === 'analyzing') {
        startAnalysisPolling()
        uni.showToast({ title: '已提交分析', icon: 'none' })
      } else {
        uni.showToast({ title: response.data?.analysisError || '暂时无法分析', icon: 'none' })
      }
    }
  } catch (error) {
    console.error('Failed to retry diary analysis', error)
  } finally {
    retrying.value = false
  }
}

function stopAnalysisPolling() {
  if (analysisPollTimer) {
    clearTimeout(analysisPollTimer)
    analysisPollTimer = null
  }
}

function startAnalysisPolling() {
  stopAnalysisPolling()
  const poll = async () => {
    if (!diary.value || diary.value.analysisStatus !== 'analyzing') return
    try {
      const response = await get(`/api/diaries/${diary.value.id}`)
      if (response.code === 200) {
        diary.value = response.data
        if (diary.value?.analysisStatus === 'analyzing') {
          analysisPollTimer = setTimeout(poll, 3000)
        } else {
          analysisPollTimer = null
        }
      }
    } catch {
      analysisPollTimer = setTimeout(poll, 5000)
    }
  }
  analysisPollTimer = setTimeout(poll, 2000)
}

onUnload(() => stopAnalysisPolling())

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
    itemColor: currentTheme.value.primary,
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
    confirmColor: currentTheme.value.accent,
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
  const plain = extractPlainText(diary.value?.content || '') || '一段没有文字的记录'
  const dateStr = diary.value?.createdAt ? formatDateTime(diary.value.createdAt) : ''
  const text = `关于我的这篇日记（${dateStr}）：\n${plain}`
  setQuote(text)
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
.diary-entry { position: relative; overflow: hidden; padding: 34rpx 30rpx 30rpx; }
.mood-glow { position: absolute; top: 0; left: 0; right: 0; bottom: 0; opacity: 0.22; pointer-events: none; z-index: 0; }
.entry-header, .diary-text, .diary-images, .entry-music, .collection-row { position: relative; z-index: 1; }
.entry-header { display: flex; flex-direction: column; gap: 20rpx; padding-bottom: 24rpx; border-bottom: 1rpx solid var(--theme-border); }
.author-info { display: flex; align-items: center; gap: 14rpx; flex-wrap: wrap; }
.entry-datetime { color: var(--theme-text-primary); font-size: 30rpx; font-weight: 650; }
.edit-action { margin-left: auto; color: var(--theme-text-secondary); font-size: 24rpx; padding: 10rpx; }
.entry-quick-actions { display: flex; gap: 24rpx; margin-top: 4rpx; }
.action-btn { display: flex; align-items: center; gap: 8rpx; font-size: 24rpx; font-weight: 600; padding: 8rpx 0; }
.action-icon { font-size: 28rpx; }
.delete-btn { color: var(--theme-accent); }
.delete-btn .action-icon { color: var(--theme-accent); }
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
.analysis-retry { align-self: flex-start; margin-top: 22rpx; padding: 0 24rpx; border: 1rpx solid var(--theme-primary); border-radius: 6rpx; background: transparent; color: var(--theme-primary); font-size: 25rpx; line-height: 68rpx; }
.analysis-pending { border-style: dashed; background: rgba(var(--theme-primary-rgb), .025); }
.analysis-kicker { display: block; color: var(--theme-accent); font-size: 22rpx; font-weight: 650; letter-spacing: 1rpx; margin-bottom: 16rpx; }
.analysis-mood-header { display: flex; align-items: center; flex-wrap: wrap; margin-bottom: 20rpx; }
.analysis-mood-label { font-size: 32rpx; font-weight: 650; }
.analysis-mood-intensity { color: var(--theme-text-secondary); font-size: 28rpx; font-weight: 500; margin-left: 6rpx; }
.mood-guide-icon { display: inline-flex; align-items: center; justify-content: center; width: 32rpx; height: 32rpx; margin-left: 10rpx; font-size: 20rpx; color: var(--theme-text-placeholder); border: 1rpx solid var(--theme-border); border-radius: 50%; line-height: 1; }
.secondary-moods { display: inline-flex; gap: 10rpx; margin-left: 14rpx; align-items: center; }
.secondary-mood-tag { font-size: 22rpx; padding: 2rpx 14rpx; border-radius: 999rpx; border: 1rpx solid var(--theme-border); color: var(--theme-text-secondary); background: transparent; }
.mood-meter { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10rpx; margin-bottom: 30rpx; }
.meter-step { height: 12rpx; border-radius: 999rpx; background: var(--theme-border); }
.meter-step.filled { background: linear-gradient(90deg, var(--theme-primary), var(--theme-accent)); }
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
