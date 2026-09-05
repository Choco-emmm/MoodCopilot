<template>
  <view class="diary-page" :style="globalThemeStyle">
    <GlobalUI :tabIndex="0" />

    <view class="page-header" :style="{ paddingTop: `${headerTop}px` }">
      <view class="page-heading">
        <text class="page-title">日记</text>
        <text class="page-date">{{ todayLabel }}</text>
      </view>
      <view class="write-header-button" @click="goToWrite">
        <text class="write-header-plus">+</text>
        <text>写日记</text>
      </view>
      <view class="diary-filter-trigger" @click="openFilterSheet">
        <view class="filter-trigger-copy">
          <text class="filter-trigger-title">搜索与筛选</text>
          <text class="filter-trigger-summary">{{ filterSummary }}</text>
        </view>
        <text class="filter-trigger-icon">⌕</text>
      </view>
    </view>

    <scroll-view
      scroll-y
      class="diary-scroll"
      :show-scrollbar="false"
      :refresher-triggered="isRefreshing"
      refresher-enabled
      @scrolltolower="loadMore"
      @refresherrefresh="onRefresh"
    >
      <view v-if="!isLoggedIn" class="guest-state">
        <text class="guest-title">留住今天</text>
        <text class="guest-copy">写下一句、拍下一张，慢慢积累属于你的日子。</text>
        <view class="guest-button" @click="goToWrite">开始写日记</view>
      </view>

      <template v-else>
        <view v-if="loading && diaries.length === 0" class="loading-state">正在载入日记...</view>

        <view v-else-if="diaries.length" class="diary-list">
          <view v-for="diary in diaries" :key="diary.id" class="diary-item" @click="goToDetail(diary.id)">
            <view class="diary-item-head">
              <text class="diary-date-num">{{ dateNumOf(diary.createdAt) }}</text>
              <text class="diary-date-month">{{ monthOf(diary.createdAt) }}</text>
              <text class="diary-time">{{ timeOf(diary.createdAt) }}</text>
            </view>

            <text class="diary-content">{{ extractPlainText(diary.content) || '一段没有文字的记录' }}</text>

            <view v-if="diary.images?.length" class="diary-images">
              <image
                v-for="(image, index) in diary.images.slice(0, 3)"
                :key="`${diary.id}-${index}`"
                :src="image"
                mode="aspectFill"
                class="diary-image"
                @click.stop="previewImage(image, diary.images)"
              />
              <view v-if="diary.images.length > 3" class="image-count">+{{ diary.images.length - 3 }}</view>
            </view>

            <MusicCard v-if="diary.musicMeta" :music-meta="diary.musicMeta" class="diary-music" />
          </view>
          <text class="list-footnote">{{ hasMore ? '继续下滑查看更早的记录' : '已经到底了' }}</text>
        </view>

        <view v-else class="empty-state">
          <text class="empty-title">还没有日记</text>
          <text class="empty-copy">不需要完整的故事，写下一句就好。</text>
          <view class="empty-button" @click="goToWrite">写下第一句</view>
        </view>
      </template>
    </scroll-view>

    <view v-if="showFilterSheet" class="filter-overlay" @click="showFilterSheet = false">
      <view class="filter-sheet" @click.stop>
        <view class="filter-handle" />
        <view class="filter-sheet-head">
          <view>
            <text class="filter-sheet-title">筛选日记</text>
            <text class="filter-sheet-subtitle">只搜索你的私人记录</text>
          </view>
          <text class="filter-close" @click="showFilterSheet = false">×</text>
        </view>

        <view class="filter-keyword-field">
          <text class="keyword-symbol">⌕</text>
          <input v-model="searchKeyword" class="filter-keyword-input" placeholder="搜索日记内容、歌曲或歌手" confirm-type="search" @confirm="applyFilters" />
          <text v-if="searchKeyword" class="keyword-clear" @click="searchKeyword = ''">×</text>
        </view>

        <text class="filter-label">时间范围</text>
        <view class="date-filter-row">
          <picker mode="date" :value="filterStartDate" @change="onStartDateChange">
            <view class="date-filter-field">
              <text class="date-field-label">开始</text>
              <text :class="['date-field-value', { empty: !filterStartDate }]">{{ filterStartDate || '不限' }}</text>
            </view>
          </picker>
          <text class="date-range-separator">至</text>
          <picker mode="date" :value="filterEndDate" @change="onEndDateChange">
            <view class="date-filter-field">
              <text class="date-field-label">结束</text>
              <text :class="['date-field-value', { empty: !filterEndDate }]">{{ filterEndDate || '不限' }}</text>
            </view>
          </picker>
        </view>
        <view class="quick-range-row">
          <view class="quick-range" @click="setQuickRange(7)">近 7 天</view>
          <view class="quick-range" @click="setQuickRange(30)">近 30 天</view>
          <view class="quick-range" @click="setCurrentMonth">本月</view>
        </view>

        <view class="filter-actions">
          <view class="filter-reset" @click="clearFilters">清除筛选</view>
          <view class="filter-apply" @click="applyFilters">查看日记</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import GlobalUI from '@/components/GlobalUI.vue'
import MusicCard from '@/components/MusicCard.vue'
import { extractPlainText } from '@/utils/markdown'
import { get } from '@/utils/request'
import { requireLogin } from '@/stores/login'

const isLoggedIn = ref(false)
const diaries = ref<any[]>([])
const page = ref(1)
const hasMore = ref(true)
const loading = ref(false)
const isRefreshing = ref(false)
const showFilterSheet = ref(false)
const searchKeyword = ref('')
const filterStartDate = ref('')
const filterEndDate = ref('')
const size = 12
const statusBarHeight = uni.getSystemInfoSync().statusBarHeight || 20
const headerTop = statusBarHeight + 20

const todayLabel = computed(() => {
  const date = new Date()
  return `${date.getMonth() + 1}月${date.getDate()}日 ${weekdayOf(date.toISOString())}`
})

const hasActiveFilters = computed(() => Boolean(searchKeyword.value.trim() || filterStartDate.value || filterEndDate.value))
const filterSummary = computed(() => {
  if (searchKeyword.value.trim()) return `关键词：${searchKeyword.value.trim()}`
  if (filterStartDate.value && filterEndDate.value) return `${filterStartDate.value} 至 ${filterEndDate.value}`
  if (filterStartDate.value) return `${filterStartDate.value} 之后`
  if (filterEndDate.value) return `${filterEndDate.value} 之前`
  return '按关键词或时间范围查找'
})

onMounted(() => {
  checkLoginStatus()
  uni.$on('refreshFeed', refreshAfterLogin)
  uni.$on('login-success', refreshAfterLogin)
})

onUnmounted(() => {
  uni.$off('refreshFeed', refreshAfterLogin)
  uni.$off('login-success', refreshAfterLogin)
})

onReachBottom(loadMore)
onPullDownRefresh(() => {
  onRefresh()
  setTimeout(() => uni.stopPullDownRefresh(), 800)
})

function refreshAfterLogin() {
  checkLoginStatus()
}

function checkLoginStatus() {
  isLoggedIn.value = Boolean(uni.getStorageSync('token'))
  if (!isLoggedIn.value) {
    diaries.value = []
    return
  }
  void fetchDiaries()
}

async function fetchDiaries(isLoadMore = false) {
  if (!isLoggedIn.value || loading.value || (isLoadMore && !hasMore.value)) return
  loading.value = true
  if (!isLoadMore) {
    page.value = 1
    hasMore.value = true
  }

  try {
    const response = hasActiveFilters.value
      ? await get('/api/diaries/search', {
          keyword: searchKeyword.value.trim() || undefined,
          startDate: filterStartDate.value || undefined,
          endDate: filterEndDate.value || undefined,
          page: page.value,
          size,
        })
      : await get(`/api/diaries/mine?page=${page.value}&size=${size}`)
    if (response.code !== 200) return
    const records = response.data?.items || response.data?.content || response.data || []
    diaries.value = isLoadMore ? [...diaries.value, ...records] : records
    hasMore.value = records.length === size
    if (hasMore.value) page.value += 1
  } catch (error) {
    console.error('获取日记失败', error)
  } finally {
    loading.value = false
    isRefreshing.value = false
  }
}

function onRefresh() {
  isRefreshing.value = true
  void fetchDiaries()
}

function loadMore() {
  void fetchDiaries(true)
}

function openFilterSheet() {
  requireLogin(() => {
    showFilterSheet.value = true
  })
}

function onStartDateChange(event: any) {
  filterStartDate.value = event.detail.value
}

function onEndDateChange(event: any) {
  filterEndDate.value = event.detail.value
}

function setQuickRange(days: number) {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - (days - 1))
  filterStartDate.value = dateToIso(start)
  filterEndDate.value = dateToIso(end)
}

function setCurrentMonth() {
  const today = new Date()
  filterStartDate.value = dateToIso(new Date(today.getFullYear(), today.getMonth(), 1))
  filterEndDate.value = dateToIso(today)
}

function dateToIso(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function clearFilters() {
  searchKeyword.value = ''
  filterStartDate.value = ''
  filterEndDate.value = ''
  showFilterSheet.value = false
  void fetchDiaries()
}

function applyFilters() {
  if (filterStartDate.value && filterEndDate.value && filterStartDate.value > filterEndDate.value) {
    uni.showToast({ title: '开始日期不能晚于结束日期', icon: 'none' })
    return
  }
  showFilterSheet.value = false
  void fetchDiaries()
}

function goToWrite() {
  requireLogin(() => uni.navigateTo({ url: '/pages/write/write' }))
}

function goToDetail(id: number | string) {
  requireLogin(() => uni.navigateTo({ url: `/pages/detail/detail?id=${id}` }))
}

function previewImage(current: string, urls: string[]) {
  uni.previewImage({ current, urls })
}

function dateFrom(value: string) {
  return new Date(value)
}

function weekdayOf(value: string) {
  return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][dateFrom(value).getDay()]
}

function dateNumOf(value: string) {
  return dateFrom(value).getDate()
}

function monthOf(value: string) {
  return `${dateFrom(value).getMonth() + 1}月`
}

function timeOf(value: string) {
  const date = dateFrom(value)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.diary-page { display: flex; min-height: 100vh; height: 100vh; flex-direction: column; overflow: hidden; box-sizing: border-box; background: var(--theme-bg); }
.page-header { padding-right: 40rpx; padding-bottom: 30rpx; padding-left: 40rpx; }
.page-heading { display: block; }
.page-title { display: block; color: var(--theme-text-primary); font-size: 42rpx; font-weight: 700; line-height: 1.2; }
.page-date { display: block; margin-top: 9rpx; color: var(--theme-text-secondary); font-size: 23rpx; }
.write-header-button { display: inline-flex; height: 62rpx; align-items: center; gap: 8rpx; margin-top: 26rpx; padding: 0 20rpx; border-radius: 8rpx; background: var(--theme-primary); color: var(--theme-text-on-primary); font-size: 24rpx; font-weight: 600; }
.write-header-plus { font-size: 31rpx; font-weight: 300; line-height: 1; }
.diary-filter-trigger { display: flex; align-items: center; justify-content: space-between; margin-top: 16rpx; padding: 17rpx 18rpx; border-radius: 7rpx; background: color-mix(in oklab, var(--theme-primary) 4%, var(--theme-surface)); }
.filter-trigger-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.filter-trigger-title { color: var(--theme-text-primary); font-size: 24rpx; font-weight: 650; }
.filter-trigger-summary { overflow: hidden; margin-top: 5rpx; color: var(--theme-text-placeholder); font-size: 20rpx; text-overflow: ellipsis; white-space: nowrap; }
.filter-trigger-icon { color: var(--theme-primary); font-size: 39rpx; line-height: .8; }
.diary-scroll { flex: 1; min-height: 0; padding: 0 32rpx calc(130rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.diary-list { display: flex; flex-direction: column; gap: 0; }
.diary-item { padding: 40rpx 0; border-bottom: 1rpx dashed var(--theme-border); }
.diary-item:last-child { border-bottom: none; }
.diary-item:active { opacity: .7; }
.diary-item-head { display: flex; align-items: baseline; margin-bottom: 24rpx; }
.diary-date-num { color: var(--theme-primary); font-size: 56rpx; font-weight: 300; font-family: "Noto Serif SC", "Songti SC", serif; margin-right: 8rpx; line-height: 1; }
.diary-date-month { color: var(--theme-primary); font-size: 28rpx; font-weight: 500; font-family: "Noto Serif SC", "Songti SC", serif; margin-right: 18rpx; }
.diary-time { color: var(--theme-text-placeholder); font-size: 24rpx; font-weight: 400; }
.diary-content { display: -webkit-box; overflow: hidden; color: var(--theme-text-primary); font-size: 28rpx; line-height: 1.68; word-break: break-word; -webkit-box-orient: vertical; -webkit-line-clamp: 4; }
.diary-images { position: relative; display: grid; grid-template-columns: repeat(3, 1fr); gap: 10rpx; margin-top: 22rpx; }
.diary-image { width: 100%; height: 176rpx; border-radius: 6rpx; background: color-mix(in oklab, var(--theme-primary) 8%, var(--theme-surface)); }
.image-count { position: absolute; right: 0; bottom: 0; display: flex; width: calc((100% - 20rpx) / 3); height: 176rpx; align-items: center; justify-content: center; border-radius: 6rpx; background: var(--theme-overlay); color: var(--theme-text-on-primary); font-size: 27rpx; }
.diary-music { margin-top: 22rpx; }
.list-footnote { padding: 18rpx 0 38rpx; color: var(--theme-text-placeholder); font-size: 22rpx; text-align: center; }
.guest-state, .empty-state { margin-top: 118rpx; text-align: center; }
.guest-title, .empty-title { display: block; color: var(--theme-text-primary); font-size: 38rpx; font-weight: 700; }
.guest-copy, .empty-copy { display: block; max-width: 480rpx; margin: 16rpx auto 0; color: var(--theme-text-secondary); font-size: 26rpx; line-height: 1.7; }
.guest-button, .empty-button { display: inline-flex; height: 74rpx; align-items: center; justify-content: center; margin-top: 38rpx; padding: 0 30rpx; border-radius: 8rpx; background: var(--theme-primary); color: var(--theme-text-on-primary); font-size: 26rpx; font-weight: 600; }
.loading-state { padding-top: 150rpx; color: var(--theme-text-placeholder); font-size: 25rpx; text-align: center; }
.filter-overlay { position: fixed; top: 0; right: 0; bottom: 0; left: 0; display: flex; align-items: flex-end; background: var(--theme-overlay); z-index: 50; }
.filter-sheet { width: 100%; padding: 16rpx 32rpx calc(32rpx + env(safe-area-inset-bottom)); border-radius: 12rpx 12rpx 0 0; background: var(--theme-surface); box-sizing: border-box; }
.filter-handle { width: 54rpx; height: 6rpx; margin: 0 auto 26rpx; border-radius: 99rpx; background: var(--theme-border); }
.filter-sheet-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 23rpx; }
.filter-sheet-title { display: block; color: var(--theme-text-primary); font-size: 32rpx; font-weight: 650; }
.filter-sheet-subtitle { display: block; margin-top: 5rpx; color: var(--theme-text-placeholder); font-size: 20rpx; }
.filter-close { width: 48rpx; color: var(--theme-text-secondary); font-size: 42rpx; font-weight: 300; text-align: right; line-height: 1; }
.filter-keyword-field { display: flex; height: 78rpx; align-items: center; padding: 0 18rpx; border: 1rpx solid var(--theme-border); border-radius: 7rpx; background: var(--theme-bg); }
.keyword-symbol { margin-right: 12rpx; color: var(--theme-primary); font-size: 34rpx; line-height: 1; }
.filter-keyword-input { min-width: 0; flex: 1; color: var(--theme-text-primary); font-size: 25rpx; }
.keyword-clear { padding: 6rpx; color: var(--theme-text-placeholder); font-size: 30rpx; }
.filter-label { display: block; margin: 25rpx 0 12rpx; color: var(--theme-text-secondary); font-size: 22rpx; }
.date-filter-row { display: flex; align-items: center; gap: 10rpx; }
.date-filter-row picker { flex: 1; min-width: 0; }
.date-filter-field { padding: 13rpx 14rpx; border: 1rpx solid var(--theme-border); border-radius: 6rpx; background: var(--theme-bg); }
.date-field-label { display: block; color: var(--theme-text-placeholder); font-size: 18rpx; }
.date-field-value { display: block; overflow: hidden; margin-top: 5rpx; color: var(--theme-text-primary); font-size: 22rpx; text-overflow: ellipsis; white-space: nowrap; }
.date-field-value.empty { color: var(--theme-text-placeholder); }
.date-range-separator { color: var(--theme-text-placeholder); font-size: 21rpx; }
.quick-range-row { display: flex; gap: 10rpx; margin-top: 13rpx; }
.quick-range { padding: 10rpx 14rpx; border-radius: 5rpx; background: color-mix(in oklab, var(--theme-primary) 7%, var(--theme-surface)); color: var(--theme-primary); font-size: 21rpx; }
.filter-actions { display: flex; align-items: center; gap: 16rpx; margin-top: 29rpx; }
.filter-reset { flex: 1; height: 76rpx; border: 1rpx solid var(--theme-border); border-radius: 7rpx; color: var(--theme-text-secondary); font-size: 25rpx; line-height: 76rpx; text-align: center; }
.filter-apply { flex: 1.5; height: 76rpx; border-radius: 7rpx; background: var(--theme-primary); color: var(--theme-text-on-primary); font-size: 25rpx; font-weight: 650; line-height: 76rpx; text-align: center; }
</style>

<style scoped>
.diary-page {
  background: var(--theme-bg);
}

.page-header {
  padding-right: var(--theme-page-padding);
  padding-bottom: 22rpx;
  padding-left: var(--theme-page-padding);
}

.page-title {
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 48rpx;
  font-weight: 700;
  letter-spacing: 0;
}

.page-date {
  margin-top: 8rpx;
  font-size: 21rpx;
  letter-spacing: 1rpx;
}

.write-header-button {
  height: 58rpx;
  margin-top: 24rpx;
  padding: 0 18rpx;
  border-radius: var(--theme-radius-sm);
  background: var(--theme-primary);
  color: var(--theme-text-on-primary);
  font-size: 23rpx;
}

.diary-filter-trigger {
  margin-top: 14rpx;
  padding: 16rpx 18rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
  background: var(--theme-surface);
}

.filter-trigger-title {
  font-size: 23rpx;
}

.filter-trigger-summary {
  font-size: 19rpx;
}

.diary-scroll {
  padding-right: 32rpx;
  padding-left: 32rpx;
}

.diary-item {
  padding: 30rpx 0;
  border-bottom-color: var(--theme-border);
}

.diary-item:active {
  background: var(--theme-surface-hover);
  opacity: 1;
}

.diary-item-head {
  margin-bottom: 18rpx;
}

.diary-date-num {
  font-size: 50rpx;
}

.diary-date-month {
  font-size: 25rpx;
}

.diary-time {
  font-size: 21rpx;
}

.diary-content {
  font-size: 27rpx;
  line-height: 1.75;
}

.diary-images {
  gap: 8rpx;
  margin-top: 18rpx;
}

.diary-image,
.image-count {
  border-radius: var(--theme-radius-sm);
}

.diary-music {
  margin-top: 18rpx;
}

.guest-state,
.empty-state {
  margin-top: 96rpx;
}

.guest-title,
.empty-title {
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 36rpx;
}

.guest-button,
.empty-button {
  height: 68rpx;
  margin-top: 32rpx;
  border-radius: var(--theme-radius-sm);
  background: var(--theme-primary);
  color: var(--theme-text-on-primary);
  font-size: 24rpx;
}

.filter-sheet {
  border-radius: var(--theme-radius-lg) var(--theme-radius-lg) 0 0;
  box-shadow: var(--theme-shadow-dialog);
}

.filter-actions {
  gap: 12rpx;
}

.filter-reset,
.filter-apply {
  border-radius: var(--theme-radius-sm);
}

.filter-apply {
  background: var(--theme-primary);
  color: var(--theme-text-on-primary);
}
</style>
