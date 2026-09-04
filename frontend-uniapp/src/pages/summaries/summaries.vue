<template>
  <view class="insight-page" :style="globalThemeStyle">
    <GlobalUI />

    <view class="insight-header">
      <text class="page-title">洞察</text>
      <text class="page-subtitle">从记录里看见一段时间的自己</text>
    </view>

    <view class="mode-tabs">
      <view :class="['mode-tab', { active: mode === 'week' }]" @click="setMode('week')">周报</view>
      <view :class="['mode-tab', { active: mode === 'month' }]" @click="setMode('month')">月报</view>
      <view :class="['mode-tab', { active: mode === 'custom' }]" @click="setMode('custom')">自定义</view>
    </view>

    <scroll-view scroll-y class="insight-scroll" :show-scrollbar="false">
      <view v-if="mode !== 'custom'" class="regular-report">
        <view class="period-nav">
          <view class="period-arrow" @click="movePeriod(-1)">‹</view>
          <view class="period-label-wrap">
            <text class="period-caption">{{ mode === 'week' ? '周度回顾' : '月度回顾' }}</text>
            <text class="period-label">{{ periodLabel }}</text>
          </view>
          <view :class="['period-arrow', { disabled: periodOffset === 0 }]" @click="movePeriod(1)">›</view>
        </view>

        <view v-if="loading" class="state-card">正在整理这段时间的记录...</view>
        <view v-else-if="loadError" class="state-card">
          <text>{{ loadError }}</text>
          <text class="retry-text" @click="loadRegularReport">重新加载</text>
        </view>
        <view v-else-if="!report || report.diaryCount === 0" class="state-card">
          <text>这段时间还没有可回顾的记录</text>
        </view>

        <template v-else>
          <view class="report-meta">
            <text>{{ report.diaryCount }} 篇日记</text>
            <text v-if="report.generatedAt">更新于 {{ formatGeneratedAt(report.generatedAt) }}</text>
          </view>

          <view class="stat-grid">
            <view class="stat-card stat-mood">
              <text class="stat-label">主导情绪</text>
              <text class="stat-value text-value">{{ report.moodDominantQuadrant || '未知' }}</text>
            </view>
            <view class="stat-card">
              <text class="stat-label">正向倾向</text>
              <text class="stat-value">{{ report.positiveRatioPercent ?? 0 }}<text class="stat-unit">%</text></text>
            </view>
            <view class="stat-card">
              <text class="stat-label">高能量占比</text>
              <text class="stat-value">{{ report.highEnergyRatioPercent ?? 0 }}<text class="stat-unit">%</text></text>
            </view>
          </view>

          <view v-if="report.needsRegenerate" class="regenerate-note">
            <text>有新日记尚未纳入本次回顾</text>
            <text class="regenerate-action" @click="generateReport">重新生成</text>
          </view>

          <view class="summary-card">
            <view class="section-head">
              <text>AI 总结</text>
              <text v-if="generating" class="section-status">生成中</text>
            </view>
            <rich-text v-if="report.aiSummary" class="summary-copy" :nodes="parseMarkdown(report.aiSummary)"></rich-text>
            <view v-else class="generate-empty">
              <text>{{ generating ? '正在生成，请稍候...' : '还没有生成这段时间的总结' }}</text>
              <view v-if="!generating" class="generate-button" @click="generateReport">生成总结</view>
            </view>
          </view>

          <view v-if="report.dailyMoods?.length" class="daily-card">
            <view class="section-head">
              <text>记录片段</text>
              <text class="day-count">{{ report.dailyMoods.length }} 天</text>
            </view>
            <view v-for="day in visibleDays" :key="`${day.date}-${day.diaryIds?.[0] || ''}`" class="day-row" @click="openDiary(day.diaryIds)">
              <text class="day-date">{{ formatDay(day.date) }}</text>
              <text class="day-mood">{{ day.moodLabel }}</text>
              <text class="day-snippet">{{ day.contentSnippet || '这一天留下了一段记录' }}</text>
              <text class="day-arrow">›</text>
            </view>
            <view v-if="report.dailyMoods.length > 5" class="more-days" @click="showAllDays = !showAllDays">
              {{ showAllDays ? '收起明细' : `查看其余 ${report.dailyMoods.length - 5} 天` }}
            </view>
          </view>

          <view v-if="hasGuidance(report)" class="guidance-card">
            <view v-if="report.insights?.length">
              <text class="guidance-title">MoodCopilot 看见了</text>
              <view v-for="item in report.insights" :key="item" class="guidance-item"><rich-text :nodes="parseMarkdown(item)"></rich-text></view>
            </view>
            <view v-if="report.suggestions?.length" class="suggestion-block">
              <text class="guidance-title">可以试试</text>
              <view v-for="item in report.suggestions" :key="item" class="guidance-item"><rich-text :nodes="parseMarkdown(item)"></rich-text></view>
            </view>
          </view>
        </template>
      </view>

      <view v-else class="custom-report">
        <view class="custom-form">
          <text class="custom-title">自定义一段时间</text>
          <view class="date-fields">
            <picker mode="date" :value="startDate" @change="onStartDateChange">
              <view class="date-field">
                <text>开始</text>
                <text class="date-value">{{ startDate || '选择日期' }}</text>
              </view>
            </picker>
            <text class="date-separator">至</text>
            <picker mode="date" :value="endDate" @change="onEndDateChange">
              <view class="date-field">
                <text>结束</text>
                <text class="date-value">{{ endDate || '选择日期' }}</text>
              </view>
            </picker>
          </view>
          <view :class="['create-button', { disabled: !canCreateCustom || creating }]" @click="createCustomReport">
            {{ creating ? '生成中...' : '生成总结' }}
          </view>
        </view>

        <text v-if="customSummaries.length" class="saved-heading">已保存的总结</text>
        <view v-if="customLoading" class="state-card">正在载入已保存的总结...</view>
        <view v-else-if="!customSummaries.length" class="state-card">选择时间范围，生成你的第一份总结</view>
        <view v-else class="saved-list">
          <view v-for="summary in customSummaries" :key="summary.id" class="saved-card" @click="selectedSummary = summary">
            <view>
              <text class="saved-title">{{ summary.title || `${summary.startDate} 至 ${summary.endDate}` }}</text>
              <text class="saved-meta">{{ summary.diaryCount || 0 }} 篇日记</text>
            </view>
            <text class="saved-arrow">›</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <view v-if="selectedSummary" class="detail-overlay" @click="selectedSummary = null">
      <view class="custom-detail" @click.stop>
        <view class="detail-head">
          <text>{{ selectedSummary.title || '自定义总结' }}</text>
          <text class="detail-close" @click="selectedSummary = null">×</text>
        </view>
        <scroll-view scroll-y class="custom-detail-scroll" :show-scrollbar="false">
          <rich-text class="summary-copy" :nodes="parseMarkdown(selectedSummary.aiSummary)"></rich-text>
          <view v-if="hasGuidance(selectedSummary)" class="guidance-card detail-guidance">
            <view v-if="selectedSummary.insights?.length">
              <text class="guidance-title">MoodCopilot 看见了</text>
              <view v-for="item in selectedSummary.insights" :key="item" class="guidance-item"><rich-text :nodes="parseMarkdown(item)"></rich-text></view>
            </view>
            <view v-if="selectedSummary.suggestions?.length" class="suggestion-block">
              <text class="guidance-title">可以试试</text>
              <view v-for="item in selectedSummary.suggestions" :key="item" class="guidance-item"><rich-text :nodes="parseMarkdown(item)"></rich-text></view>
            </view>
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
import { get, post } from '@/utils/request'
import { parseMarkdown } from '@/utils/markdown'

type ReportMode = 'week' | 'month' | 'custom'

const mode = ref<ReportMode>('week')
const periodOffset = ref(0)
const report = ref<any>(null)
const loading = ref(false)
const generating = ref(false)
const loadError = ref('')
const showAllDays = ref(false)

const startDate = ref('')
const endDate = ref('')
const creating = ref(false)
const customLoading = ref(false)
const customSummaries = ref<any[]>([])
const selectedSummary = ref<any>(null)

const periodLabel = computed(() => {
  if (report.value?.weekLabel) return report.value.weekLabel
  const target = new Date()
  if (mode.value === 'week') {
    target.setDate(target.getDate() + periodOffset.value * 7)
    const day = target.getDay() || 7
    const monday = new Date(target)
    monday.setDate(target.getDate() - day + 1)
    const sunday = new Date(monday)
    sunday.setDate(monday.getDate() + 6)
    return `${monday.getMonth() + 1}月${monday.getDate()}日 - ${sunday.getMonth() + 1}月${sunday.getDate()}日`
  }
  target.setMonth(target.getMonth() + periodOffset.value)
  return `${target.getFullYear()}年${target.getMonth() + 1}月`
})

const visibleDays = computed(() => {
  const days = report.value?.dailyMoods || []
  return showAllDays.value ? days : days.slice(0, 5)
})

const canCreateCustom = computed(() => Boolean(startDate.value && endDate.value && startDate.value <= endDate.value))

onLoad(() => {
  void loadRegularReport()
  void loadCustomSummaries()
})

function setMode(nextMode: ReportMode) {
  if (mode.value === nextMode) return
  mode.value = nextMode
  selectedSummary.value = null
  if (nextMode !== 'custom') {
    periodOffset.value = 0
    showAllDays.value = false
    void loadRegularReport()
  }
}

function movePeriod(direction: number) {
  if (direction > 0 && periodOffset.value === 0) return
  periodOffset.value += direction
  showAllDays.value = false
  void loadRegularReport()
}

async function loadRegularReport() {
  loading.value = true
  loadError.value = ''
  report.value = null
  try {
    const endpoint = mode.value === 'week' ? 'weekly-report' : 'monthly-report'
    const key = mode.value === 'week' ? 'weekOffset' : 'monthOffset'
    const response = await get(`/api/diaries/${endpoint}?${key}=${periodOffset.value}`)
    if (response.code !== 200) throw new Error(response.message || '报告加载失败')
    report.value = unwrapData(response.data)
  } catch (error: any) {
    loadError.value = error?.message || '报告暂时加载失败'
  } finally {
    loading.value = false
  }
}

async function generateReport() {
  if (generating.value) return
  generating.value = true
  try {
    const endpoint = mode.value === 'week' ? 'weekly-report' : 'monthly-report'
    const key = mode.value === 'week' ? 'weekOffset' : 'monthOffset'
    const response = await post(`/api/diaries/${endpoint}/generate?${key}=${periodOffset.value}`, {})
    if (response.code !== 200) throw new Error(response.message || '生成失败')
    report.value = unwrapData(response.data)
    uni.showToast({ title: '总结已生成', icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '生成失败，请稍后重试', icon: 'none' })
  } finally {
    generating.value = false
  }
}

async function loadCustomSummaries() {
  customLoading.value = true
  try {
    const response = await get('/api/summaries?type=CUSTOM')
    if (response.code === 200) customSummaries.value = unwrapData(response.data) || []
  } finally {
    customLoading.value = false
  }
}

async function createCustomReport() {
  if (!canCreateCustom.value || creating.value) return
  creating.value = true
  try {
    const response = await post('/api/summaries', { startDate: startDate.value, endDate: endDate.value })
    if (response.code !== 200) throw new Error(response.message || '生成失败')
    uni.showToast({ title: '总结已生成', icon: 'success' })
    startDate.value = ''
    endDate.value = ''
    await loadCustomSummaries()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '生成失败，请稍后重试', icon: 'none' })
  } finally {
    creating.value = false
  }
}

function unwrapData(data: any) {
  return data?.data ?? data
}

function onStartDateChange(event: any) {
  startDate.value = event.detail.value
}

function onEndDateChange(event: any) {
  endDate.value = event.detail.value
}

function formatDay(value: string) {
  const date = new Date(value)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function formatGeneratedAt(value: string) {
  const date = new Date(value)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function openDiary(ids?: number[]) {
  if (!ids?.length) return
  uni.navigateTo({ url: `/pages/detail/detail?id=${ids[0]}` })
}

function hasGuidance(value: any) {
  return Boolean(value?.insights?.length || value?.suggestions?.length)
}
</script>

<style scoped>
.insight-page { display: flex; height: 100vh; min-height: 100vh; flex-direction: column; overflow: hidden; background: var(--theme-bg); }
.insight-header { padding: 30rpx 36rpx 21rpx; }
.page-title { display: block; color: var(--theme-text-primary); font-size: 40rpx; font-weight: 700; line-height: 1.25; }
.page-subtitle { display: block; margin-top: 8rpx; color: var(--theme-text-secondary); font-size: 23rpx; }
.mode-tabs { display: flex; margin: 0 32rpx; padding: 6rpx; border: 1rpx solid var(--theme-border); border-radius: 8rpx; background: var(--theme-surface); }
.mode-tab { flex: 1; padding: 14rpx 0; border-radius: 5rpx; color: var(--theme-text-secondary); font-size: 25rpx; text-align: center; }
.mode-tab.active { background: var(--theme-primary); color: #fff; font-weight: 650; }
.insight-scroll { flex: 1; min-height: 0; }
.regular-report, .custom-report { padding: 28rpx 32rpx calc(130rpx + env(safe-area-inset-bottom)); }
.period-nav { display: flex; align-items: center; justify-content: space-between; margin-bottom: 25rpx; }
.period-arrow { display: flex; width: 60rpx; height: 60rpx; align-items: center; justify-content: center; border: 1rpx solid var(--theme-border); border-radius: 6rpx; color: var(--theme-primary); font-size: 45rpx; font-weight: 300; line-height: 1; }
.period-arrow.disabled { color: var(--theme-text-placeholder); opacity: .4; }
.period-label-wrap { text-align: center; }
.period-caption { display: block; color: var(--theme-text-placeholder); font-size: 20rpx; }
.period-label { display: block; margin-top: 5rpx; color: var(--theme-text-primary); font-size: 28rpx; font-weight: 650; }
.state-card { margin-top: 30rpx; padding: 72rpx 34rpx; border: 1rpx dashed var(--theme-border); border-radius: 8rpx; color: var(--theme-text-secondary); font-size: 25rpx; line-height: 1.6; text-align: center; }
.retry-text { display: block; margin-top: 15rpx; color: var(--theme-primary); }
.report-meta { display: flex; justify-content: space-between; margin-bottom: 16rpx; color: var(--theme-text-placeholder); font-size: 21rpx; }
.stat-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12rpx; }
.stat-card { display: flex; min-height: 126rpx; flex-direction: column; justify-content: space-between; padding: 22rpx; border: 1rpx solid var(--theme-border); border-radius: 7rpx; background: var(--theme-surface); }
.stat-mood { grid-column: span 2; min-height: 104rpx; background: rgba(var(--theme-primary-rgb), .035); }
.stat-label { color: var(--theme-text-secondary); font-size: 21rpx; }
.stat-value { color: var(--theme-text-primary); font-size: 37rpx; font-weight: 700; }
.text-value { color: var(--theme-primary); font-size: 32rpx; }
.stat-unit { margin-left: 3rpx; font-size: 21rpx; font-weight: 400; }
.regenerate-note { display: flex; align-items: center; justify-content: space-between; margin-top: 15rpx; padding: 16rpx 18rpx; border-left: 4rpx solid var(--theme-primary); background: rgba(var(--theme-primary-rgb), .06); color: var(--theme-text-secondary); font-size: 22rpx; }
.regenerate-action { color: var(--theme-primary); font-weight: 650; }
.summary-card, .daily-card, .guidance-card, .custom-form { margin-top: 20rpx; padding: 28rpx; border: 1rpx solid var(--theme-border); border-radius: 8rpx; background: var(--theme-surface); }
.section-head { display: flex; align-items: center; justify-content: space-between; color: var(--theme-text-primary); font-size: 27rpx; font-weight: 650; }
.section-status, .day-count { color: var(--theme-text-placeholder); font-size: 21rpx; font-weight: 400; }
.summary-copy { display: block; margin-top: 19rpx; color: var(--theme-text-primary); font-size: 27rpx; line-height: 1.85; white-space: pre-line; }
.generate-empty { padding: 42rpx 0 14rpx; color: var(--theme-text-secondary); font-size: 24rpx; text-align: center; }
.generate-button, .create-button { display: inline-flex; height: 64rpx; align-items: center; justify-content: center; margin-top: 24rpx; padding: 0 25rpx; border-radius: 6rpx; background: var(--theme-primary); color: #fff; font-size: 24rpx; font-weight: 650; }
.day-row { display: flex; align-items: center; gap: 11rpx; padding: 20rpx 0; border-bottom: 1rpx solid var(--theme-border); }
.day-date { width: 55rpx; color: var(--theme-text-placeholder); font-size: 21rpx; }
.day-mood { color: var(--theme-primary); font-size: 22rpx; font-weight: 650; }
.day-snippet { overflow: hidden; flex: 1; color: var(--theme-text-secondary); font-size: 22rpx; text-overflow: ellipsis; white-space: nowrap; }
.day-arrow, .saved-arrow { color: var(--theme-text-placeholder); font-size: 31rpx; font-weight: 300; }
.more-days { padding-top: 20rpx; color: var(--theme-primary); font-size: 22rpx; text-align: center; }
.guidance-title { display: block; color: var(--theme-primary); font-size: 24rpx; font-weight: 650; }
.guidance-item { margin-top: 14rpx; padding-left: 17rpx; border-left: 3rpx solid rgba(var(--theme-primary-rgb), .35); color: var(--theme-text-secondary); font-size: 24rpx; line-height: 1.7; }
.suggestion-block { margin-top: 28rpx; }
.custom-title { display: block; color: var(--theme-text-primary); font-size: 29rpx; font-weight: 650; }
.date-fields { display: flex; align-items: center; gap: 10rpx; margin-top: 24rpx; }
.date-fields picker { flex: 1; min-width: 0; }
.date-field { padding: 16rpx 13rpx; border: 1rpx solid var(--theme-border); border-radius: 6rpx; color: var(--theme-text-placeholder); font-size: 20rpx; }
.date-value { display: block; overflow: hidden; margin-top: 6rpx; color: var(--theme-text-primary); font-size: 23rpx; text-overflow: ellipsis; white-space: nowrap; }
.date-separator { color: var(--theme-text-placeholder); font-size: 22rpx; }
.create-button { width: 100%; margin-top: 22rpx; padding: 0; box-sizing: border-box; }
.create-button.disabled { background: var(--theme-border); color: var(--theme-text-placeholder); }
.saved-heading { display: block; margin: 31rpx 0 14rpx; color: var(--theme-text-secondary); font-size: 23rpx; }
.saved-list { display: flex; flex-direction: column; gap: 12rpx; }
.saved-card { display: flex; align-items: center; justify-content: space-between; padding: 25rpx 27rpx; border: 1rpx solid var(--theme-border); border-radius: 7rpx; background: var(--theme-surface); }
.saved-title { display: block; color: var(--theme-text-primary); font-size: 26rpx; font-weight: 600; }
.saved-meta { display: block; margin-top: 7rpx; color: var(--theme-text-placeholder); font-size: 21rpx; }
.detail-overlay { position: fixed; top: 0; right: 0; bottom: 0; left: 0; display: flex; align-items: flex-end; background: rgba(22, 27, 24, .42); z-index: 50; }
.custom-detail { display: flex; width: 100%; height: 76vh; flex-direction: column; padding: 28rpx 32rpx calc(28rpx + env(safe-area-inset-bottom)); border-radius: 12rpx 12rpx 0 0; background: var(--theme-surface); box-sizing: border-box; }
.detail-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 20rpx; border-bottom: 1rpx solid var(--theme-border); color: var(--theme-text-primary); font-size: 30rpx; font-weight: 650; }
.detail-close { color: var(--theme-text-secondary); font-size: 40rpx; font-weight: 300; }
.custom-detail-scroll { flex: 1; min-height: 0; }
.detail-guidance { margin-bottom: 28rpx; }
</style>
