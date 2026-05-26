<template>
  <main class="app-shell">
    <AppHeader />

    <div class="report-page">
      <h1 class="page-title">情绪报告</h1>

      <!-- 单行切换：周报 / 月报 / 自定义总结 -->
      <div class="tab-switch">
        <button
          :class="['tab-btn', { active: mainTab === 'regular' && mode === 'week' }]"
          @click="setRegularMode('week')"
        >周报</button>
        <button
          :class="['tab-btn', { active: mainTab === 'regular' && mode === 'month' }]"
          @click="setRegularMode('month')"
        >月报</button>
        <button
          :class="['tab-btn', { active: mainTab === 'custom' }]"
          @click="mainTab = 'custom'"
        >自定义总结</button>
      </div>

      <div class="report-panel">
      <!-- ==================== 常规报告 ==================== -->
      <template v-if="mainTab === 'regular'">
      <!-- ==================== 周报 ==================== -->
      <section v-if="mode === 'week'" class="report-section">
        <div class="date-nav">
          <h2 class="date-nav-title">本周报告</h2>
          <div class="date-nav-controls">
            <button class="nav-arrow" @click="prevWeek">&larr;</button>
            <span class="date-label">{{ computedWeekLabelStr }}</span>
            <button class="nav-arrow" :disabled="weekOffset === 0" @click="nextWeek" :style="weekOffset === 0 ? 'opacity:0.3; cursor:not-allowed;' : ''">&rarr;</button>
          </div>
        </div>

        <div v-if="store.reportLoading" class="empty-state">
          <n-spin size="medium">加载中...</n-spin>
        </div>

        <div v-else-if="report && report.diaryCount === 0" class="empty-state">
          <p>本周暂无记录</p>
        </div>

        <div v-else-if="store.reportError" class="empty-state compact">
          <p>{{ store.reportError }}</p>
          <n-button size="small" text type="primary" @click="store.fetchWeeklyReport(weekOffset)">重新加载</n-button>
        </div>

        <template v-if="report && report.diaryCount > 0">
          <div class="report-detail">
            <div class="report-meta-row">
              <span v-if="report.generatedAt" class="report-meta-text">上次生成：{{ formatGeneratedAt(report.generatedAt) }}</span>
              <span v-if="report.needsRegenerate" class="report-meta-warning">有新日记未纳入，建议重新生成</span>
            </div>
            <div class="stats-grid">
              <div class="stat-item">
                <span class="stat-label">主导情绪</span>
                <span class="stat-val text-val">{{ report.moodDominantQuadrant || '未知' }}</span>
                <span class="stat-desc">你在这段时间内的情绪底色。</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">总日记量</span>
                <span class="stat-val">{{ report.diaryCount ?? 0 }} <span class="stat-unit">篇</span></span>
                <span class="stat-desc">留下的文字，都是梳理的痕迹。</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">正向倾向</span>
                <span class="stat-val">{{ report.positiveRatioPercent ?? 0 }} <span class="stat-unit">%</span></span>
                <span class="stat-desc">情绪天平中偏向阳光的一面。</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">高能量占比</span>
                <span class="stat-val">{{ report.highEnergyRatioPercent ?? 0 }} <span class="stat-unit">%</span></span>
                <span class="stat-desc">充满波动与强度的情绪比例。</span>
              </div>
            </div>

            <ReportCharts v-if="report.dailyMoods?.length" :moods="report.dailyMoods" />

            <p v-if="report.dailyMoods?.length" class="chart-footnote">仅展示已勾选 AI 分析的日记数据</p>

            <div class="section-divider">
              <span>AI Weekly Summary</span>
            </div>
            <p class="report-auto-hint">系统会在每周一自动生成上一周报告，也可手动生成。</p>
            <div v-if="!report.aiSummary" class="empty-state compact">
              <p v-if="store.generatingWeekly">AI 正在生成总结，请稍候...</p>
              <p v-else>暂无总结，可使用 AI 限额提前生成</p>
              <n-button type="primary" :loading="store.generatingWeekly" :disabled="store.generatingWeekly" @click="store.generateWeeklyAiSummary(weekOffset)">
                {{ store.generatingWeekly ? 'AI 生成中...' : '生成 AI 总结' }}
              </n-button>
            </div>
            <div v-else class="ai-summary-wrapper">
              <div v-if="report.needsRegenerate" class="regenerate-banner">
                <p>检测到新日记未纳入本次报告，可按需重新生成，或等待下次自动生成</p>
                <n-button size="small" type="primary" :loading="store.generatingWeekly" :disabled="store.generatingWeekly" @click="store.generateWeeklyAiSummary(weekOffset)">
                  {{ store.generatingWeekly ? '生成中...' : '重新生成' }}
                </n-button>
              </div>
              <div class="md-content ai-summary" v-html="renderMd(report.aiSummary)"></div>
            </div>

            <div v-if="hasGuidance(report)" class="guidance-box">
              <div v-if="report.insights?.length">
                <h4 class="guidance-title">MoodCopilot 看见了</h4>
                <ul class="guidance-list">
                  <li v-for="item in report.insights" :key="item" v-html="renderMd(item)"></li>
                </ul>
              </div>
              <div v-if="report.suggestions?.length" style="margin-top: 24px;">
                <h4 class="guidance-title">可以试试</h4>
                <ul class="guidance-list">
                  <li v-for="item in report.suggestions" :key="item" v-html="renderMd(item)"></li>
                </ul>
              </div>
            </div>
          </div>
        </template>
      </section>

      <!-- ==================== 月报 ==================== -->
      <section v-if="mode === 'month'" class="report-section">
        <div class="date-nav">
          <h2 class="date-nav-title">本月报告</h2>
          <div class="date-nav-controls">
            <button class="nav-arrow" @click="prevMonth">&larr;</button>
            <span class="date-label">{{ computedMonthLabelStr }}</span>
            <button class="nav-arrow" :disabled="monthOffset === 0" @click="nextMonth" :style="monthOffset === 0 ? 'opacity:0.3; cursor:not-allowed;' : ''">&rarr;</button>
          </div>
        </div>

        <div v-if="store.monthLoading" class="empty-state">
          <n-spin size="medium">加载中...</n-spin>
        </div>

        <div v-else-if="monthReport && monthReport.diaryCount === 0" class="empty-state">
          <p>本月暂无记录</p>
        </div>

        <div v-else-if="store.monthError" class="empty-state compact">
          <p>{{ store.monthError }}</p>
          <n-button size="small" text type="primary" @click="store.fetchMonthlyReport(monthOffset)">重新加载</n-button>
        </div>

        <template v-if="monthReport && monthReport.diaryCount > 0">
          <div class="report-detail">
            <div class="report-meta-row">
              <span v-if="monthReport.generatedAt" class="report-meta-text">上次生成：{{ formatGeneratedAt(monthReport.generatedAt) }}</span>
              <span v-if="monthReport.needsRegenerate" class="report-meta-warning">有新日记未纳入，建议重新生成</span>
            </div>
            <div class="stats-grid">
              <div class="stat-item">
                <span class="stat-label">主导情绪</span>
                <span class="stat-val text-val">{{ monthReport.moodDominantQuadrant || '未知' }}</span>
                <span class="stat-desc">你在这段时间内的情绪底色。</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">总日记量</span>
                <span class="stat-val">{{ monthReport.diaryCount ?? 0 }} <span class="stat-unit">篇</span></span>
                <span class="stat-desc">留下的文字，都是梳理的痕迹。</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">正向倾向</span>
                <span class="stat-val">{{ monthReport.positiveRatioPercent ?? 0 }} <span class="stat-unit">%</span></span>
                <span class="stat-desc">情绪天平中偏向阳光的一面。</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">高能量占比</span>
                <span class="stat-val">{{ monthReport.highEnergyRatioPercent ?? 0 }} <span class="stat-unit">%</span></span>
                <span class="stat-desc">充满波动与强度的情绪比例。</span>
              </div>
            </div>

            <ReportCharts v-if="monthReport.dailyMoods?.length" :moods="monthReport.dailyMoods" />

            <p v-if="monthReport.dailyMoods?.length" class="chart-footnote">仅展示已勾选 AI 分析的日记数据</p>

            <div v-if="monthReport.dailyMoods?.length" class="list-switch-row">
              <n-button text size="small" @click="showAllMonthDetails = !showAllMonthDetails">
                {{ showAllMonthDetails ? '收起明细' : '展开全部明细' }}
              </n-button>
            </div>

            <div v-if="monthDisplayMoods.length" class="mood-snippet-list">
              <div
                v-for="day in monthDisplayMoods"
                :key="day.date + '-' + (day.diaryIds?.[0] ?? '')"
                class="mood-snippet-row"
                @click="goDiary(day.diaryIds)"
              >
                <span class="mood-snippet-date">{{ formatDay(day.date) }}</span>
                <span class="mood-snippet-tag" :style="{ color: moodColor(day.moodLabel) }">{{ day.moodLabel }}</span>
                <span class="mood-snippet-intensity">强度 {{ day.moodIntensity }}/5</span>
                <span v-if="day.contentSnippet" class="mood-snippet-text">「{{ day.contentSnippet.length > 30 ? day.contentSnippet.slice(0, 30) + '...' : day.contentSnippet }}」</span>
              </div>
            </div>

            <div class="section-divider">
              <span>AI Monthly Summary</span>
            </div>
            <p class="report-auto-hint">系统会在每月 1 日自动生成上一月报告，也可手动生成。</p>
            <div v-if="!monthReport.aiSummary" class="empty-state compact">
              <p v-if="store.generatingMonthly">AI 正在生成总结，请稍候...</p>
              <p v-else>暂无总结，可使用 AI 限额提前生成</p>
              <n-button type="primary" :loading="store.generatingMonthly" :disabled="store.generatingMonthly" @click="store.generateMonthlyAiSummary(monthOffset)">
                {{ store.generatingMonthly ? 'AI 生成中...' : '生成 AI 总结' }}
              </n-button>
            </div>
            <div v-else class="ai-summary-wrapper">
              <div v-if="monthReport.needsRegenerate" class="regenerate-banner">
                <p>检测到新日记未纳入本次报告，可按需重新生成，或等待下次自动生成</p>
                <n-button size="small" type="primary" :loading="store.generatingMonthly" :disabled="store.generatingMonthly" @click="store.generateMonthlyAiSummary(monthOffset)">
                  {{ store.generatingMonthly ? '生成中...' : '重新生成' }}
                </n-button>
              </div>
              <div class="md-content ai-summary" v-html="renderMd(monthReport.aiSummary)"></div>
            </div>

            <div v-if="hasGuidance(monthReport)" class="guidance-box">
              <div v-if="monthReport.insights?.length">
                <h4 class="guidance-title">MoodCopilot 看见了</h4>
                <ul class="guidance-list">
                  <li v-for="item in monthReport.insights" :key="item" v-html="renderMd(item)"></li>
                </ul>
              </div>
              <div v-if="monthReport.suggestions?.length" style="margin-top: 24px;">
                <h4 class="guidance-title">可以试试</h4>
                <ul class="guidance-list">
                  <li v-for="item in monthReport.suggestions" :key="item" v-html="renderMd(item)"></li>
                </ul>
              </div>
            </div>
          </div>
        </template>
      </section>
      </template>

      <!-- ==================== 自定义总结 ==================== -->
      <template v-if="mainTab === 'custom'">
        <section class="report-section">
        <h3>自定义总结</h3>
        <div class="create-row">
          <n-date-picker v-model:value="startDate" type="date" placeholder="开始日期" />
          <span class="create-sep">至</span>
          <n-date-picker v-model:value="endDate" type="date" placeholder="结束日期" />
          <n-button type="primary" :loading="creating" :disabled="!startDate || !endDate" @click="createCustom">生成</n-button>
        </div>
      </section>

      <!-- 已保存的总结 -->
      <section v-if="summaries.length > 0" class="report-section">
        <h3>保存的总结</h3>
        <div class="summary-list">
          <article v-for="s in summaries" :key="s.id" class="summary-card">
            <div class="summary-head">
              <h4>{{ s.title }}（{{ s.diaryCount }} 篇日记）</h4>
              <n-button size="tiny" text type="error" @click="remove(s.id)">删除</n-button>
            </div>
            <div v-if="s.diaryCount > 0" class="report-detail">
              <div class="stats-grid">
                <div class="stat-item">
                  <span class="stat-label">主导情绪</span>
                  <span class="stat-val text-val">{{ s.moodDominantQuadrant || '未知' }}</span>
                  <span class="stat-desc">你在这段时间内的情绪底色。</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">总日记量</span>
                  <span class="stat-val">{{ s.diaryCount }} <span class="stat-unit">篇</span></span>
                  <span class="stat-desc">留下的文字，都是梳理的痕迹。</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">正向倾向</span>
                  <span class="stat-val">{{ s.positiveRatioPercent ?? 0 }} <span class="stat-unit">%</span></span>
                  <span class="stat-desc">情绪天平中偏向阳光的一面。</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">高能量占比</span>
                  <span class="stat-val">{{ s.highEnergyRatioPercent ?? 0 }} <span class="stat-unit">%</span></span>
                  <span class="stat-desc">充满波动与强度的情绪比例。</span>
                </div>
              </div>

              <ReportCharts v-if="s.dailyMoods?.length" :moods="s.dailyMoods" />
              <p v-if="s.dailyMoods?.length" class="chart-footnote">仅展示已勾选 AI 分析的日记数据</p>
            </div>
            <div class="section-divider">
              <span>AI Summary</span>
            </div>
            <div class="md-content ai-summary" v-html="renderMd(s.aiSummary)"></div>
            <div v-if="hasGuidance(s)" class="guidance-box">
              <div v-if="s.insights?.length">
                <h4 class="guidance-title">MoodCopilot 看见了</h4>
                <ul class="guidance-list">
                  <li v-for="item in s.insights" :key="item" v-html="renderMd(item)"></li>
                </ul>
              </div>
              <div v-if="s.suggestions?.length" style="margin-top: 24px;">
                <h4 class="guidance-title">可以试试</h4>
                <ul class="guidance-list">
                  <li v-for="item in s.suggestions" :key="item" v-html="renderMd(item)"></li>
                </ul>
              </div>
            </div>
          </article>
        </div>
      </section>
      </template>
      </div> <!-- close report-panel -->
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NTag, NDatePicker, NSpin } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { useReportStore } from '../stores/report'
import { summaryApi } from '../api'
import { moodColor } from '../utils/mood'
import { renderSafeMarkdown } from '../utils/markdown'

function renderMd(text: string) {
  return renderSafeMarkdown(text)
}

const router = useRouter()
import ReportCharts from '../components/ReportCharts.vue'

const store = useReportStore()

const mainTab = ref<'regular' | 'custom'>('regular')
const mode = ref<'week' | 'month'>('week')
const weekOffset = ref(0)
const monthOffset = ref(0)
const creating = ref(false)
const startDate = ref<number | null>(null)
const endDate = ref<number | null>(null)
const summaries = ref<any[]>([])
const showAllMonthDetails = ref(false)

const report = computed(() => store.weeklyReport)
const monthReport = computed(() => store.monthlyReport)

onMounted(() => {
  store.fetchWeeklyReport(weekOffset.value)
  loadSummaries()
})

watch(weekOffset, (val) => { store.fetchWeeklyReport(val) })
watch(monthOffset, (val) => { store.fetchMonthlyReport(val) })

function setRegularMode(m: 'week' | 'month') {
  mainTab.value = 'regular'
  mode.value = m
  if (m === 'month' && !store.monthlyReport && !store.monthLoading && !store.monthError) {
    store.fetchMonthlyReport(monthOffset.value)
  }
}

function prevWeek() { weekOffset.value-- }
function nextWeek() { weekOffset.value++ }
function prevMonth() { monthOffset.value-- }
function nextMonth() { monthOffset.value++ }

const computedWeekLabelStr = computed(() => {
  if (report.value?.weekLabel) return report.value.weekLabel
  const today = new Date()
  const offsetMs = weekOffset.value * 7 * 24 * 60 * 60 * 1000
  const targetDate = new Date(today.getTime() + offsetMs)
  const d = targetDate.getDay() || 7
  const monday = new Date(targetDate.getTime() - (d - 1) * 24 * 60 * 60 * 1000)
  const sunday = new Date(monday.getTime() + 6 * 24 * 60 * 60 * 1000)
  return `${monday.getMonth()+1}/${monday.getDate()} - ${sunday.getMonth()+1}/${sunday.getDate()}`
})

const computedMonthLabelStr = computed(() => {
  if (monthReport.value?.weekLabel) return monthReport.value.weekLabel
  const targetDate = new Date()
  targetDate.setMonth(targetDate.getMonth() + monthOffset.value)
  return `${targetDate.getFullYear()}年${targetDate.getMonth()+1}月`
})


const monthDisplayMoods = computed(() => {
  const moods = monthReport.value?.dailyMoods ?? []
  if (showAllMonthDetails.value) return moods
  return moods.slice(0, 12)
})

async function createCustom() {
  if (!startDate.value || !endDate.value) return
  creating.value = true
  try {
    const iso = (ts: number) => new Date(ts).toISOString().split('T')[0]
    await summaryApi.create({ startDate: iso(startDate.value), endDate: iso(endDate.value) })
    startDate.value = null
    endDate.value = null
    await loadSummaries()
  } finally { creating.value = false }
}

async function loadSummaries() {
  try {
    const res = await summaryApi.list('CUSTOM')
    summaries.value = res.data.data ?? []
  } catch { /* ignore */ }
}

async function remove(id: number) {
  await summaryApi.delete(id)
  summaries.value = summaries.value.filter((s: any) => s.id !== id)
}

function goDiary(ids?: number[]) {
  if (!ids?.length) return
  if (ids.length === 1) {
    router.push('/diary/' + ids[0])
  } else {
    // 当天有多篇日记，跳转到第一篇，后续可优化为弹出选择
    router.push('/diary/' + ids[0])
  }
}

function hasGuidance(currentReport: any) {
  return Boolean(currentReport?.insights?.length || currentReport?.suggestions?.length || currentReport?.followUpPrompt)
}

function formatDay(dateStr: string) {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function formatGeneratedAt(value?: string | Date | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<style scoped>
.report-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 16px 40px;
}

.page-title {
  display: block;
  width: 100%;
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 700;
  text-align: center;
  margin: 0 0 16px;
}

/* ── Segmented Control (Tabs) ── */
.tab-switch {
  display: flex;
  margin: 0 auto 24px;
  max-width: fit-content;
  background: color-mix(in oklab, var(--color-border) 40%, transparent);
  border-radius: 999px;
  padding: 4px;
  gap: 4px;
}

.tab-btn {
  padding: 8px 24px;
  border: none;
  background: transparent;
  border-radius: 999px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn.active {
  background: var(--color-surface);
  color: var(--color-text);
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

/* ── Editorial Panel ── */
.report-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 40px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.02);
}

/* ── Date Navigator ── */
.date-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 16px;
  margin-bottom: 32px;
}

.date-nav-title {
  font-family: var(--font-display);
  font-size: 1.4rem;
  margin: 0;
  color: var(--color-text);
}

.date-nav-controls {
  display: flex;
  align-items: center;
  gap: 16px;
}

.date-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-secondary);
  font-family: var(--font-display);
  letter-spacing: 0.05em;
}

.nav-arrow {
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: all 0.2s;
}

.nav-arrow:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

/* ── Stats Grid (Magazine Style Typography) ── */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  margin-bottom: 40px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: color-mix(in oklab, var(--theme-bg) 50%, transparent);
  border-radius: 6px;
  border: 1px dashed color-mix(in oklab, var(--color-border) 60%, transparent);
}

.stat-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-muted);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.stat-val {
  font-family: var(--font-display);
  font-size: 2.5rem;
  color: var(--color-primary);
  line-height: 1;
  margin-bottom: 4px;
}

.stat-val.text-val {
  font-size: 1.6rem;
  color: var(--color-accent);
  margin-top: 4px;
}

.stat-unit {
  font-size: 14px;
  color: var(--color-text-secondary);
  font-family: var(--font-body);
}

.stat-desc {
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
}

/* ── Section Dividers ── */
.section-divider {
  text-align: center;
  margin: 40px 0;
  position: relative;
}

.section-divider::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  border-top: 1px solid var(--color-border);
  z-index: 0;
}

.section-divider span {
  background: var(--color-surface);
  padding: 0 16px;
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
  letter-spacing: 0.2em;
  text-transform: uppercase;
  position: relative;
  z-index: 1;
}

/* ── AI Summary (Editorial Layout) ── */
.ai-summary {
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.8;
  color: var(--color-text);
  columns: 2;
  column-gap: 40px;
  margin-bottom: 40px;
}

.ai-summary :deep(p) {
  margin-top: 0;
  margin-bottom: 1em;
  text-align: justify;
}

/* Drop cap for first paragraph */
.ai-summary > :deep(p:first-of-type::first-letter) {
  font-family: var(--font-display);
  font-size: 3.5em;
  float: left;
  line-height: 0.8;
  margin-right: 0.1em;
  color: var(--color-primary);
}

/* ── Guidance/Insights ── */
.guidance-box {
  margin-top: 40px;
  background: color-mix(in oklab, var(--color-primary) 4%, transparent);
  border-left: 3px solid var(--color-primary);
  padding: 24px;
}

.guidance-title {
  font-family: var(--font-display);
  font-size: 1.1rem;
  color: var(--color-primary);
  margin: 0 0 16px;
}

.guidance-list {
  margin: 0;
  padding-left: 20px;
  color: var(--color-text-secondary);
  font-size: 14.5px;
  line-height: 1.7;
}

.guidance-list li {
  margin-bottom: 12px;
}
.guidance-list li:last-child {
  margin-bottom: 0;
}

/* Markdown overrides */
.md-content :deep(strong), .md-content :deep(b) {
  font-weight: 600;
  color: var(--color-text);
}
.md-content :deep(ul) {
  list-style-type: disc;
  padding-left: 1.5em;
  margin: 0.5em 0;
}
.md-content :deep(ol) {
  list-style-type: decimal;
  padding-left: 1.5em;
  margin: 0.5em 0;
}
.md-content :deep(li) {
  margin-bottom: 0.4em;
  line-height: 1.6;
}

/* 日记列表高度约束 + 移动端滚动优化 */
.mood-chart,
.mood-snippet-list {
  max-height: 48vh;
  overflow-y: auto;
  padding-right: 6px;
  -webkit-overflow-scrolling: touch;
  touch-action: pan-y;
  overscroll-behavior: contain;
}

.mood-chart::-webkit-scrollbar,
.mood-snippet-list::-webkit-scrollbar {
  width: 5px;
}
.mood-chart::-webkit-scrollbar-thumb,
.mood-snippet-list::-webkit-scrollbar-thumb {
  background-color: #cbd5e1;
  border-radius: 4px;
}

/* 重新生成提示条 */
.regenerate-banner {
  background-color: var(--color-surface-hover);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.regenerate-banner p {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.report-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.report-meta-text {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.report-meta-warning {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  color: var(--color-accent);
  background: var(--color-accent-bg);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 2px 10px;
}

.quadrant-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.quadrant-chip {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
  background: var(--color-surface-soft);
}

.list-switch-row {
  display: flex;
  justify-content: flex-end;
  margin: 6px 0 4px;
}

.legend-more {
  color: var(--color-text-muted);
}



@media (max-width: 768px) {
  .report-page {
    padding: 0 0 40px;
  }

  .page-title {
    font-size: 1.5rem;
    margin: 0 0 12px;
  }

  .report-panel {
    padding: 24px 16px;
    border-radius: 0;
    border-left: none;
    border-right: none;
  }

  .date-nav {
    padding-bottom: 12px;
    margin-bottom: 20px;
  }

  .date-nav-title {
    font-size: 1.1rem;
  }

  .date-nav-controls {
    gap: 10px;
  }

  .date-label {
    font-size: 13px;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
    margin-bottom: 24px;
  }

  .stat-item {
    padding: 12px;
    gap: 4px;
  }

  .stat-val {
    font-size: 1.8rem;
  }

  .stat-val.text-val {
    font-size: 1.2rem;
  }

  .section-divider {
    margin: 24px 0;
  }

  .section-divider span {
    font-size: 11px;
    padding: 0 12px;
  }

  .ai-summary {
    columns: 1;
    font-size: 14px;
    line-height: 1.7;
    margin-bottom: 24px;
  }

  .ai-summary > :deep(p:first-of-type::first-letter) {
    font-size: 2.5em;
  }

  .guidance-box {
    margin-top: 24px;
    padding: 16px;
  }

  .guidance-title {
    font-size: 1rem;
    margin: 0 0 12px;
  }

  .guidance-list {
    font-size: 13.5px;
    padding-left: 16px;
  }

  .regenerate-banner {
    flex-direction: column;
    align-items: flex-start;
    padding: 10px 14px;
    gap: 8px;
  }

  .tab-btn {
    padding: 6px 14px;
    font-size: 12px;
  }

  .tab-switch {
    margin-bottom: 16px;
  }

  .create-row {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .create-sep {
    display: none;
  }

  .summary-card {
    padding: 16px;
  }

  .mood-snippet-list {
    max-height: 40vh;
  }

  .report-auto-hint {
    font-size: 12px;
  }
}

@media (max-width: 480px) {
  .page-title {
    font-size: 1.3rem;
  }

  .report-panel {
    padding: 14px 10px;
    overflow-x: hidden;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 6px;
    margin-bottom: 16px;
  }

  .stat-item {
    padding: 8px;
    gap: 2px;
  }

  .stat-val {
    font-size: 1.4rem;
  }

  .stat-val.text-val {
    font-size: 0.95rem;
  }

  .stat-label {
    font-size: 9px;
  }

  .stat-desc {
    font-size: 10px;
    line-height: 1.4;
  }

  .date-nav {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    padding-bottom: 10px;
    margin-bottom: 14px;
  }

  .date-nav-title {
    font-size: 0.95rem;
  }

  .date-nav-controls {
    width: 100%;
    justify-content: space-between;
  }

  .tab-switch {
    width: 100%;
    max-width: none;
    gap: 2px;
    padding: 3px;
  }

  .tab-btn {
    flex: 1;
    text-align: center;
    padding: 5px 4px;
    font-size: 10px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .guidance-box {
    margin-top: 16px;
    padding: 12px 10px;
    border-left-width: 2px;
  }

  .guidance-title {
    font-size: 0.9rem;
    margin: 0 0 8px;
  }

  .guidance-list {
    font-size: 12px;
    padding-left: 14px;
    line-height: 1.6;
  }

  .section-divider {
    margin: 16px 0;
  }

  .section-divider span {
    font-size: 9px;
    padding: 0 8px;
    letter-spacing: 0.15em;
  }

  .ai-summary {
    font-size: 13px;
    line-height: 1.65;
    margin-bottom: 16px;
  }

  .ai-summary > :deep(p:first-of-type::first-letter) {
    font-size: 1.8em;
  }

  .report-meta-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    margin-bottom: 4px;
  }

  .report-meta-warning {
    font-size: 11px;
    padding: 2px 8px;
  }

  .regenerate-banner {
    padding: 8px 10px;
    gap: 6px;
  }

  .regenerate-banner p {
    font-size: 11px;
  }

  .list-switch-row {
    justify-content: flex-start;
    margin: 2px 0;
  }

  .mood-snippet-row {
    grid-template-columns: 38px 44px 1fr;
    gap: 4px;
    padding: 2px 2px;
    font-size: 11px;
  }

  .mood-snippet-date {
    font-size: 10px;
  }

  .mood-snippet-tag {
    font-size: 10px;
  }

  .mood-snippet-intensity {
    font-size: 10px;
    justify-self: end;
  }

  .mood-snippet-text {
    grid-column: 1 / -1;
    font-size: 11px;
    white-space: normal;
    overflow: visible;
    text-overflow: unset;
    line-height: 1.5;
  }

  .report-auto-hint {
    font-size: 10px;
  }

  .chart-footnote {
    font-size: 10px;
  }
}

.chart-footnote {
  font-size: 12px;
  color: #94a3b8;
  margin: 4px 0 0;
  text-align: right;
}
</style>
