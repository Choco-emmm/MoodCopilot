<template>
  <view class="life-page" :style="globalThemeStyle">
    <GlobalUI />
    <view class="page-header">
      <text class="eyebrow">LIFE CHAPTERS</text>
      <text class="page-title">时光画卷</text>
      <text class="page-desc">把一段段日子放远一点看，成长藏在那些当时没有察觉的转弯里。</text>
    </view>
    <view v-if="candidates.length" class="candidate-panel">
      <text class="section-label">需要你确认</text><text class="candidate-title">可能是新的阶段</text>
      <view v-for="candidate in candidates" :key="candidate.id" class="candidate-item">
        <view><text class="candidate-date">{{ candidate.suggestedStartDate }} 起</text><text class="candidate-reason">{{ candidate.reason }}</text><text class="candidate-count">涉及 {{ candidate.sourceDiaryIds.length + candidate.sourceEventIds.length }} 条记录</text></view>
        <view class="candidate-actions"><text class="candidate-action" @click="rejectCandidate(candidate.id)">暂不分开</text><text class="candidate-action accept" @click="acceptCandidate(candidate.id)">接受新阶段</text></view>
      </view>
    </view>
    <view v-if="loading" class="state">正在翻阅你的时光...</view>
    <view v-else-if="chapters.length === 0" class="state">还没有足够长的一段故事。继续记录，章节会慢慢长出来。</view>
    <view v-else class="chapter-list">
      <view v-for="(chapter, index) in chapters" :key="chapter.id" class="chapter-item">
        <view class="chapter-marker">{{ String(index + 1).padStart(2, '0') }}</view>
        <view class="chapter-main">
          <text class="chapter-period">{{ chapter.startDate }}{{ chapter.endDate ? ` - ${chapter.endDate}` : ' - 至今' }} · {{ sourceCount(chapter) }} 条记录</text>
          <text class="chapter-title">{{ chapter.title }}</text>
          <text class="chapter-meta">{{ chapter.segmentType === 'LEGACY_MONTH' ? '历史月度章节' : '动态阶段' }} · {{ chapter.currentVersion ? `第 ${chapter.currentVersion} 版 · ` : '' }}{{ chapter.lastGeneratedAt || chapter.updatedAt || '等待首次生成' }}</text>
          <text v-if="chapter.isOpen || chapter.generationStatus === 'COLLECTING'" class="chapter-status">正在积累</text>
          <text v-else-if="chapter.generationStatus === 'GENERATING'" class="chapter-status updating">正在整理</text>
          <text v-else-if="chapter.generationStatus === 'DIRTY'" class="chapter-status updating">待整理</text>
          <text v-else-if="chapter.generationStatus === 'FAILED'" class="chapter-status failed">更新失败，已保留上一版</text>
          <text v-if="chapter.isOpen || chapter.generationStatus === 'COLLECTING'" class="chapter-summary">这一阶段的记录还在积累，内容更完整后会生成总结。</text><text v-else class="chapter-summary">{{ chapter.themeSummary }}</text>
          <text v-if="chapter.growthReflection && !chapter.isOpen && chapter.generationStatus !== 'COLLECTING'" class="chapter-reflection">{{ chapter.growthReflection }}</text>
          <view v-if="chapter.dominantMoods?.length" class="mood-row">
            <text v-for="mood in chapter.dominantMoods" :key="mood">{{ mood }}</text>
          </view>
          <text v-if="chapter.generationStatus === 'FAILED' && chapter.lastGenerationError" class="chapter-error">{{ chapter.lastGenerationError }}</text>
          <view class="chapter-actions">
            <text class="chapter-action" @click="toggleSources(chapter.id)">{{ expandedId === chapter.id ? '收起来源' : `查看来源（${(chapter.diarySources?.length || 0) + (chapter.eventSources?.length || 0)}）` }}</text>
            <text v-if="!chapter.isOpen && chapter.generationStatus !== 'COLLECTING'" class="chapter-action" @click="refreshChapter(chapter)">{{ refreshingId === chapter.id ? '已提交整理' : '重新整理这一章' }}</text>
            <text v-if="(chapter.currentVersion || 0) > 1" class="chapter-action" @click="toggleVersions(chapter.id)">{{ versionsId === chapter.id ? '收起历史' : '查看历史版本' }}</text>
          </view>
          <view v-if="expandedId === chapter.id" class="source-list">
            <view v-for="source in chapter.diarySources" :key="source.id" class="source-item" @click="openDiary(source.id)">
              <text class="source-date">{{ source.date }}</text><text class="source-excerpt">{{ source.excerpt || source.summary || '这篇日记暂无摘要' }}</text><text class="source-link">查看日记 →</text>
            </view>
            <view v-for="source in chapter.eventSources" :key="`event-${source.id}`" class="source-item" @click="openEvents()">
              <text class="source-date">{{ source.startDate }}</text><text class="source-excerpt">重要事件：{{ source.title }}</text><text class="source-link">查看事件 →</text>
            </view>
            <text v-if="!chapter.diarySources?.length && !chapter.eventSources?.length" class="empty-source">暂无来源</text>
          </view>
          <view v-if="versionsId === chapter.id" class="version-list">
            <view v-for="version in versions[chapter.id] || []" :key="version.version" class="version-item"><text>第 {{ version.version }} 版 · {{ version.createdAt }}</text><text>{{ version.title }}</text></view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import GlobalUI from '@/components/GlobalUI.vue'
import { get, request } from '@/utils/request'
import { hasLoginToken, requireLogin } from '@/stores/login'

interface DiarySource { id: number; date: string; excerpt: string; summary?: string }
interface EventSource { id: number; title: string; startDate: string; endDate?: string }
interface ChapterVersion { version: number; title: string; themeSummary: string; createdAt?: string }
interface LifeChapter { id: number; title: string; themeSummary: string; startDate: string; endDate?: string; dominantMoods?: string[]; growthReflection?: string; diaryCount: number; updatedAt?: string; currentVersion?: number; generationStatus?: string; lastGeneratedAt?: string; lastGenerationError?: string; diarySources?: DiarySource[]; eventSources?: EventSource[]; segmentType?: string; isOpen?: boolean }
interface TimelineCandidate { id: number; suggestedStartDate: string; reason: string; sourceDiaryIds: number[]; sourceEventIds: number[]; status: string }
const chapters = ref<LifeChapter[]>([])
const loading = ref(true)
const expandedId = ref<number | null>(null)
const versionsId = ref<number | null>(null)
const versions = ref<Record<number, ChapterVersion[]>>({})
const refreshingId = ref<number | null>(null)
const candidates = ref<TimelineCandidate[]>([])

onMounted(() => {
  if (!hasLoginToken()) {
    requireLogin()
    loading.value = false
    return
  }
  void loadChapters()
})

async function loadChapters() {
  try {
    const res = await get<{ stages: LifeChapter[]; gaps: { startDate: string; endDate: string }[] }>('/api/life-timeline?includeGaps=true&size=50')
    if (res.code === 200) chapters.value = res.data?.stages || []
    const candidateRes = await get<TimelineCandidate[]>('/api/life-timeline/candidates')
    if (candidateRes.code === 200) candidates.value = candidateRes.data || []
  } finally {
    loading.value = false
  }
}

function toggleSources(id: number) { expandedId.value = expandedId.value === id ? null : id }
async function toggleVersions(id: number) {
  if (versionsId.value === id) { versionsId.value = null; return }
  if (!versions.value[id]) {
    const res = await get<ChapterVersion[]>(`/api/life-timeline/${id}/versions`)
    if (res.code === 200) versions.value[id] = res.data || []
  }
  versionsId.value = id
}
async function acceptCandidate(id: number) { await request(`/api/life-timeline/candidates/${id}/accept`, 'POST'); await loadChapters() }
async function rejectCandidate(id: number) { await request(`/api/life-timeline/candidates/${id}/reject`, 'POST'); await loadChapters() }
function sourceCount(chapter: LifeChapter) { return (chapter.diarySources?.length || 0) + (chapter.eventSources?.length || 0) }
async function refreshChapter(chapter: LifeChapter) {
  if (refreshingId.value === chapter.id) return
  refreshingId.value = chapter.id
  try { await request(`/api/life-chapters/${chapter.id}/refresh`, 'POST'); await loadChapters() } finally {
    setTimeout(() => { if (refreshingId.value === chapter.id) refreshingId.value = null }, 1200)
  }
}
function openDiary(id: number) { uni.navigateTo({ url: `/pages/detail/detail?id=${id}` }) }
function openEvents() { uni.navigateTo({ url: '/pages/life-events/life-events' }) }
</script>

<style scoped>
.life-page { min-height: 100vh; padding: 42rpx 36rpx 140rpx; box-sizing: border-box; background: var(--theme-bg); }
.page-header { margin: 28rpx 0 54rpx; }
.eyebrow { display: block; margin-bottom: 12rpx; color: var(--theme-primary); font-size: 20rpx; font-weight: 700; letter-spacing: 4rpx; }
.page-title { display: block; color: var(--theme-text-primary); font-family: Georgia, serif; font-size: 58rpx; font-weight: 700; }
.page-desc { display: block; margin-top: 18rpx; color: var(--theme-text-secondary); font-size: 25rpx; line-height: 1.7; }
.candidate-panel { margin-bottom: 42rpx; padding: 24rpx; border: 1rpx solid var(--theme-border); background: var(--theme-surface); }.section-label, .candidate-title, .candidate-reason, .candidate-count { display: block; }.section-label { color: var(--theme-primary); font-size: 20rpx; font-weight: 700; letter-spacing: 3rpx; }.candidate-title { margin-top: 8rpx; color: var(--theme-text-primary); font-size: 31rpx; font-weight: 650; }.candidate-item { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin-top: 20rpx; padding-top: 20rpx; border-top: 1rpx solid var(--theme-border); }.candidate-date { display: block; color: var(--theme-text-primary); font-size: 24rpx; font-weight: 650; }.candidate-reason { margin-top: 6rpx; color: var(--theme-text-secondary); font-size: 23rpx; line-height: 1.5; }.candidate-count { margin-top: 5rpx; color: var(--theme-text-placeholder); font-size: 20rpx; }.candidate-actions { display: flex; flex-shrink: 0; align-items: center; gap: 18rpx; }.candidate-action { color: var(--theme-text-secondary); font-size: 21rpx; }.candidate-action.accept { padding: 12rpx 16rpx; background: var(--theme-primary); color: var(--theme-surface); }
.chapter-list { border-top: 1rpx solid var(--theme-border); }
.chapter-item { display: flex; gap: 24rpx; padding: 32rpx 0 40rpx; border-bottom: 1rpx solid var(--theme-border); }
.chapter-marker { display: flex; width: 48rpx; height: 48rpx; flex-shrink: 0; align-items: center; justify-content: center; border: 1rpx solid var(--theme-primary); border-radius: 50%; color: var(--theme-primary); font-size: 19rpx; }
.chapter-main { min-width: 0; flex: 1; }
.chapter-period, .chapter-title, .chapter-summary, .chapter-reflection { display: block; }
.chapter-period { color: var(--theme-text-placeholder); font-size: 20rpx; }
.chapter-meta, .chapter-status, .chapter-error { display: block; margin-top: 8rpx; color: var(--theme-text-placeholder); font-size: 20rpx; }
.chapter-status.updating { color: var(--theme-primary); }.chapter-status.failed, .chapter-error { color: var(--theme-accent); }
.chapter-title { margin-top: 12rpx; color: var(--theme-text-primary); font-family: Georgia, serif; font-size: 36rpx; font-weight: 700; }
.chapter-summary { margin-top: 12rpx; color: var(--theme-text-secondary); font-size: 25rpx; line-height: 1.75; }
.chapter-reflection { margin-top: 16rpx; padding-left: 16rpx; border-left: 3rpx solid var(--theme-primary); color: var(--theme-text-secondary); font-size: 24rpx; line-height: 1.7; }
.mood-row { display: flex; flex-wrap: wrap; gap: 10rpx; margin-top: 18rpx; }
.mood-row text { padding: 5rpx 12rpx; border: 1rpx solid var(--theme-border); color: var(--theme-text-placeholder); font-size: 20rpx; }
.state { padding: 80rpx 20rpx; color: var(--theme-text-secondary); font-size: 26rpx; line-height: 1.7; text-align: center; }
.chapter-actions { display: flex; flex-wrap: wrap; gap: 24rpx; margin-top: 22rpx; }
.chapter-action, .source-link { color: var(--theme-primary); font-size: 22rpx; }
.source-list, .version-list { margin-top: 18rpx; padding-left: 18rpx; border-left: 3rpx solid var(--theme-border); }
.source-item, .version-item { display: flex; flex-wrap: wrap; gap: 10rpx; padding: 14rpx 0; border-bottom: 1rpx solid var(--theme-border); }
.source-date { color: var(--theme-text-placeholder); font-size: 20rpx; }
.source-excerpt, .version-item text:last-child { flex: 1; min-width: 220rpx; color: var(--theme-text-secondary); font-size: 22rpx; line-height: 1.5; }
.empty-source { color: var(--theme-text-placeholder); font-size: 22rpx; }
@media (max-width: 520px) { .candidate-item { align-items: flex-start; flex-direction: column; }.candidate-actions { align-self: flex-end; } }
</style>
