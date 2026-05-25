<template>
  <main class="app-shell">
    <AppHeader />

    <div class="report-page">
      <h2>情绪报告</h2>

      <!-- 单行切换：周报 / 月报 / 自定义总结 -->
      <div class="report-switch-row">
        <button
          :class="['report-switch-tab', { active: mainTab === 'regular' && mode === 'week' }]"
          @click="setRegularMode('week')"
        >周报</button>
        <button
          :class="['report-switch-tab', { active: mainTab === 'regular' && mode === 'month' }]"
          @click="setRegularMode('month')"
        >月报</button>
        <button
          :class="['report-switch-tab', { active: mainTab === 'custom' }]"
          @click="mainTab = 'custom'"
        >自定义总结</button>
      </div>

      <!-- ==================== 常规报告 ==================== -->
      <template v-if="mainTab === 'regular'">
      <!-- ==================== 周报 ==================== -->
      <section v-if="mode === 'week'" class="report-section">
        <div class="report-header">
          <h3>本周报告</h3>
          <div class="week-nav">
            <n-button text circle @click="prevWeek">&larr;</n-button>
            <span class="week-label">{{ computedWeekLabelStr }}</span>
            <n-button text circle :disabled="weekOffset === 0" @click="nextWeek">&rarr;</n-button>
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
            <h4 class="focus-title">本期重点</h4>
            <div class="insight-strip">
              <div class="insight-card insight-card-dominant">
                <p class="insight-label">主导象限</p>
                <p class="insight-value">{{ report.moodDominantQuadrant || '暂无' }}</p>
              </div>
              <div class="insight-card">
                <p class="insight-label">总日记量</p>
                <p class="insight-value">{{ report.diaryCount ?? 0 }} 篇</p>
              </div>
              <div class="insight-card">
                <p class="insight-label">正向占比</p>
                <p class="insight-value">{{ report.positiveRatioPercent ?? 0 }}%</p>
              </div>
              <div class="insight-card">
                <p class="insight-label">高能量占比</p>
                <p class="insight-value">{{ report.highEnergyRatioPercent ?? 0 }}%</p>
              </div>
            </div>

            <div v-if="report.moodDistribution" class="quadrant-list">
              <span v-for="(count, label) in report.moodDistribution" :key="label" class="quadrant-chip">{{ label }} {{ count }}</span>
            </div>

            <ReportCharts v-if="report.dailyMoods?.length" :moods="report.dailyMoods" />

            <p v-if="report.dailyMoods?.length" class="chart-footnote">仅展示已勾选 AI 分析的日记数据</p>

            <h4>AI 周总结</h4>
            <p class="report-auto-hint">系统会在每周一 00:00 自动生成上一周报告，也可以现在手动生成。</p>
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
              <div class="md-content ai-summary" v-html="renderMd(report.aiSummary)" />
            </div>

            <div v-if="hasGuidance(report)" class="report-guidance">
              <div v-if="report.insights?.length">
                <h4>MoodCopilot 看见了</h4>
                <ul class="guidance-list">
                  <li v-for="item in report.insights" :key="item" v-html="renderMd(item)" />
                </ul>
              </div>
              <div v-if="report.suggestions?.length">
                <h4>可以试试</h4>
                <ul class="guidance-list">
                  <li v-for="item in report.suggestions" :key="item" v-html="renderMd(item)" />
                </ul>
              </div>
            </div>
          </div>
        </template>
      </section>

      <!-- ==================== 月报 ==================== -->
      <section v-if="mode === 'month'" class="report-section">
        <div class="report-header">
          <h3>本月报告</h3>
          <div class="week-nav">
            <n-button text circle @click="prevMonth">&larr;</n-button>
            <span class="week-label">{{ computedMonthLabelStr }}</span>
            <n-button text circle :disabled="monthOffset === 0" @click="nextMonth">&rarr;</n-button>
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
            <h4 class="focus-title">本期重点</h4>
            <div class="insight-strip">
              <div class="insight-card insight-card-dominant">
                <p class="insight-label">主导象限</p>
                <p class="insight-value">{{ monthReport.moodDominantQuadrant || '暂无' }}</p>
              </div>
              <div class="insight-card">
                <p class="insight-label">总日记量</p>
                <p class="insight-value">{{ monthReport.diaryCount ?? 0 }} 篇</p>
              </div>
              <div class="insight-card">
                <p class="insight-label">正向占比</p>
                <p class="insight-value">{{ monthReport.positiveRatioPercent ?? 0 }}%</p>
              </div>
              <div class="insight-card">
                <p class="insight-label">高能量占比</p>
                <p class="insight-value">{{ monthReport.highEnergyRatioPercent ?? 0 }}%</p>
              </div>
            </div>

            <div v-if="monthReport.moodDistribution" class="quadrant-list">
              <span v-for="(count, label) in monthReport.moodDistribution" :key="label" class="quadrant-chip">{{ label }} {{ count }}</span>
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

            <h4>AI 月总结</h4>
            <p class="report-auto-hint">系统会在每月 1 日 00:00 自动生成上一月报告，也可以现在手动生成。</p>
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
              <div class="md-content ai-summary" v-html="renderMd(monthReport.aiSummary)" />
            </div>

            <div v-if="hasGuidance(monthReport)" class="report-guidance">
              <div v-if="monthReport.insights?.length">
                <h4>MoodCopilot 看见了</h4>
                <ul class="guidance-list">
                  <li v-for="item in monthReport.insights" :key="item" v-html="renderMd(item)" />
                </ul>
              </div>
              <div v-if="monthReport.suggestions?.length">
                <h4>可以试试</h4>
                <ul class="guidance-list">
                  <li v-for="item in monthReport.suggestions" :key="item" v-html="renderMd(item)" />
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
              <h4 class="focus-title mt-2">本期重点</h4>
              <div class="insight-strip">
                <div class="insight-card insight-card-dominant">
                  <p class="insight-label">主导象限</p>
                  <p class="insight-value">{{ s.moodDominantQuadrant || '暂无' }}</p>
                </div>
                <div class="insight-card">
                  <p class="insight-label">总日记量</p>
                  <p class="insight-value">{{ s.diaryCount }} 篇</p>
                </div>
                <div class="insight-card">
                  <p class="insight-label">正向占比</p>
                  <p class="insight-value">{{ s.positiveRatioPercent ?? 0 }}%</p>
                </div>
                <div class="insight-card">
                  <p class="insight-label">高能量占比</p>
                  <p class="insight-value">{{ s.highEnergyRatioPercent ?? 0 }}%</p>
                </div>
              </div>

              <div v-if="s.moodDistribution" class="quadrant-list">
                <span v-for="(count, label) in s.moodDistribution" :key="label" class="quadrant-chip">{{ label }} {{ count }}</span>
              </div>

              <ReportCharts v-if="s.dailyMoods?.length" :moods="s.dailyMoods" />
              <p v-if="s.dailyMoods?.length" class="chart-footnote">仅展示已勾选 AI 分析的日记数据</p>
            </div>
            <div class="md-content summary-body" v-html="renderMd(s.aiSummary)" />
            <div v-if="hasGuidance(s)" class="report-guidance">
              <div v-if="s.insights?.length">
                <h4>MoodCopilot 看见了</h4>
                <ul class="guidance-list">
                  <li v-for="item in s.insights" :key="item" v-html="renderMd(item)" />
                </ul>
              </div>
              <div v-if="s.suggestions?.length">
                <h4>可以试试</h4>
                <ul class="guidance-list">
                  <li v-for="item in s.suggestions" :key="item" v-html="renderMd(item)" />
                </ul>
              </div>
            </div>
          </article>
        </div>
      </section>
      </template>
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
/* Markdown 动态报告内容样式穿透 */
.md-content :deep(p) {
  margin: 0 0 0.8em 0;
  line-height: 1.7;
  color: var(--color-text);
}
.md-content :deep(p:last-child) {
  margin-bottom: 0;
}
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
.md-content :deep(blockquote) {
  border-left: 4px solid var(--color-border);
  padding-left: 1em;
  color: var(--color-text-secondary);
  font-style: italic;
  margin: 0.8em 0;
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

.focus-title {
  margin: 4px 0 10px;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.insight-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 10px 0 12px;
}

.insight-card {
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 10px 12px;
}

.insight-card-dominant {
  border-color: var(--color-border-strong);
  background: var(--color-surface-hover);
}

.insight-label {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.insight-value {
  margin: 6px 0 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text);
}

.insight-card-dominant .insight-value {
  color: var(--color-primary);
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

.report-switch-row {
  display: flex;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  overflow: hidden;
  background: var(--color-surface-soft);
}

.report-switch-tab {
  flex: 1;
  border: none;
  background: transparent;
  min-height: 42px;
  padding: 4px 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
}

.report-switch-tab + .report-switch-tab {
  border-left: 1px solid var(--color-border);
}

.report-switch-tab.active {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

@media (max-width: 480px) {
  .report-switch-tab {
    min-height: 40px;
    padding: 4px 8px;
    font-size: 12px;
  }

  .insight-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .insight-card {
    padding: 9px 10px;
  }


  .insight-label {
    font-size: 11px;
  }

  .insight-value {
    font-size: clamp(16px, 6vw, 24px);
    line-height: 1.2;
    white-space: nowrap;
    letter-spacing: -0.5px;
  }

  .report-meta-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .quadrant-list {
    margin-bottom: 10px;
  }

  .regenerate-banner {
    flex-direction: column;
    align-items: flex-start;
  }
  .regenerate-banner button {
    align-self: flex-end;
  }
}

.chart-footnote {
  font-size: 12px;
  color: #94a3b8;
  margin: 4px 0 0;
  text-align: right;
}
</style>
