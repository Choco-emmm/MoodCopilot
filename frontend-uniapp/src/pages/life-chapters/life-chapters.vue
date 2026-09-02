<template>
  <view class="life-page" :style="globalThemeStyle">
    <GlobalUI />
    <view class="page-header">
      <text class="eyebrow">LIFE CHAPTERS</text>
      <text class="page-title">时光画卷</text>
      <text class="page-desc">把一段段日子放远一点看，成长藏在那些当时没有察觉的转弯里。</text>
    </view>
    <view v-if="loading" class="state">正在翻阅你的时光...</view>
    <view v-else-if="chapters.length === 0" class="state">还没有足够长的一段故事。继续记录，章节会慢慢长出来。</view>
    <view v-else class="chapter-list">
      <view v-for="(chapter, index) in chapters" :key="chapter.id" class="chapter-item">
        <view class="chapter-marker">{{ String(index + 1).padStart(2, '0') }}</view>
        <view class="chapter-main">
          <text class="chapter-period">{{ chapter.startDate }} — {{ chapter.endDate }} · {{ chapter.diaryCount }} 篇日记</text>
          <text class="chapter-title">{{ chapter.title }}</text>
          <text class="chapter-summary">{{ chapter.themeSummary }}</text>
          <text v-if="chapter.growthReflection" class="chapter-reflection">{{ chapter.growthReflection }}</text>
          <view v-if="chapter.dominantMoods?.length" class="mood-row">
            <text v-for="mood in chapter.dominantMoods" :key="mood">{{ mood }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import GlobalUI from '@/components/GlobalUI.vue'
import { get } from '@/utils/request'
import { hasLoginToken, requireLogin } from '@/stores/login'

interface LifeChapter { id: number; title: string; themeSummary: string; startDate: string; endDate: string; dominantMoods?: string[]; growthReflection?: string; diaryCount: number }
const chapters = ref<LifeChapter[]>([])
const loading = ref(true)

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
    const res = await get<LifeChapter[]>('/api/life-chapters')
    if (res.code === 200) chapters.value = res.data || []
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.life-page { min-height: 100vh; padding: 42rpx 36rpx 140rpx; box-sizing: border-box; background: var(--theme-bg); }
.page-header { margin: 28rpx 0 54rpx; }
.eyebrow { display: block; margin-bottom: 12rpx; color: var(--theme-primary); font-size: 20rpx; font-weight: 700; letter-spacing: 4rpx; }
.page-title { display: block; color: var(--theme-text-primary); font-family: Georgia, serif; font-size: 58rpx; font-weight: 700; }
.page-desc { display: block; margin-top: 18rpx; color: var(--theme-text-secondary); font-size: 25rpx; line-height: 1.7; }
.chapter-list { border-top: 1rpx solid var(--theme-border); }
.chapter-item { display: flex; gap: 24rpx; padding: 32rpx 0 40rpx; border-bottom: 1rpx solid var(--theme-border); }
.chapter-marker { display: flex; width: 48rpx; height: 48rpx; flex-shrink: 0; align-items: center; justify-content: center; border: 1rpx solid var(--theme-primary); border-radius: 50%; color: var(--theme-primary); font-size: 19rpx; }
.chapter-main { min-width: 0; flex: 1; }
.chapter-period, .chapter-title, .chapter-summary, .chapter-reflection { display: block; }
.chapter-period { color: var(--theme-text-placeholder); font-size: 20rpx; }
.chapter-title { margin-top: 12rpx; color: var(--theme-text-primary); font-family: Georgia, serif; font-size: 36rpx; font-weight: 700; }
.chapter-summary { margin-top: 12rpx; color: var(--theme-text-secondary); font-size: 25rpx; line-height: 1.75; }
.chapter-reflection { margin-top: 16rpx; padding-left: 16rpx; border-left: 3rpx solid var(--theme-primary); color: var(--theme-text-secondary); font-size: 24rpx; line-height: 1.7; }
.mood-row { display: flex; flex-wrap: wrap; gap: 10rpx; margin-top: 18rpx; }
.mood-row text { padding: 5rpx 12rpx; border: 1rpx solid var(--theme-border); color: var(--theme-text-placeholder); font-size: 20rpx; }
.state { padding: 80rpx 20rpx; color: var(--theme-text-secondary); font-size: 26rpx; line-height: 1.7; text-align: center; }
</style>
