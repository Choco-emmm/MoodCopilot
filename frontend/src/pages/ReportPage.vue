<template>
  <main class="app-shell">
    <AppHeader />

    <div class="report-page">
      <h2>情绪报告</h2>

      <!-- 周/月切换 -->
      <div class="report-mode-tabs">
        <button
          :class="['mode-tab', { active: mode === 'week' }]"
          @click="switchMode('week')"
        >周报</button>
        <button
          :class="['mode-tab', { active: mode === 'month' }]"
          @click="switchMode('month')"
        >月报</button>
      </div>

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
            <h4>情绪趋势</h4>
            <div class="mood-chart">
              <div
                v-for="day in report.dailyMoods"
                :key="day.date + '-' + (day.diaryIds?.[0] ?? '')"
                class="mood-day-block"
              >
                <div
                  class="mood-bar-row mood-bar-row-clickable"
                  @click="goDiary(day.diaryIds)"
                >
                  <span class="mood-date">{{ formatDay(day.date) }}</span>
                  <div class="mood-bar-track">
                    <div class="mood-bar" :style="{ width: (day.moodIntensity / 5) * 100 + '%', background: moodColor(day.moodLabel) }" />
                  </div>
                  <n-tag :color="{ color: moodColor(day.moodLabel), textColor: '#fff' }" size="small" round>{{ day.moodLabel }}</n-tag>
                </div>
                <div v-if="day.contentSnippet" class="mood-snippet" @click="goDiary(day.diaryIds)">
                  「{{ day.contentSnippet }}{{ day.contentSnippet.length >= 30 ? '...' : '' }}」
                </div>
              </div>
            </div>

            <h4>AI 周总结</h4>
            <div v-if="!report.aiSummary" class="empty-state compact">
              <p>暂无总结，可使用 AI 限额提前生成</p>
              <n-button type="primary" @click="store.generateWeeklyAiSummary(weekOffset)">生成 AI 总结</n-button>
            </div>
            <p v-else class="ai-summary">{{ report.aiSummary }}</p>

            <div v-if="hasGuidance(report)" class="report-guidance">
              <div v-if="report.insights?.length">
                <h4>MoodCopilot 看见了</h4>
                <ul class="guidance-list">
                  <li v-for="item in report.insights" :key="item">{{ item }}</li>
                </ul>
              </div>
              <div v-if="report.suggestions?.length">
                <h4>可以试试</h4>
                <ul class="guidance-list">
                  <li v-for="item in report.suggestions" :key="item">{{ item }}</li>
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
            <h4>情绪走向（纵轴=强度 1-5，越高表示情绪更强）</h4>
            <svg class="sparkline" :viewBox="'0 0 ' + sparklineW + ' 60'" preserveAspectRatio="none">
              <polyline
                :points="sparklinePoints"
                fill="none"
                stroke="#4a7c62"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <polygon
                :points="sparklineArea"
                fill="url(#sparkGrad)"
              />
              <defs>
                <linearGradient id="sparkGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#4a7c62" stop-opacity="0.25" />
                  <stop offset="100%" stop-color="#4a7c62" stop-opacity="0.02" />
                </linearGradient>
              </defs>
              <g v-for="p in sparklinePointMeta" :key="'p-' + p.date + '-' + p.diaryId">
                <circle :cx="p.x" :cy="p.y" r="3.2" :fill="p.color" />
              </g>
            </svg>
            <div class="trend-legend">
              <span class="legend-item"><i class="legend-dot" style="background:#4f8f7c"></i>平静</span>
              <span class="legend-item"><i class="legend-dot" style="background:#7db89a"></i>轻松</span>
              <span class="legend-item"><i class="legend-dot" style="background:#e08d72"></i>焦虑</span>
              <span class="legend-item"><i class="legend-dot" style="background:#e09f5c"></i>烦躁</span>
            </div>
            <div class="sparkline-labels">
              <span>{{ sparklineFirst }}</span>
              <span>{{ sparklineLast }}</span>
            </div>

            <div v-if="monthReport.dailyMoods?.length" class="mood-snippet-list">
              <div
                v-for="day in monthReport.dailyMoods"
                :key="day.date + '-' + (day.diaryIds?.[0] ?? '')"
                class="mood-snippet-row"
                @click="goDiary(day.diaryIds)"
              >
                <span class="mood-snippet-date">{{ formatDay(day.date) }}</span>
                <span class="mood-snippet-tag" :style="{ color: moodColor(day.moodLabel) }">{{ day.moodLabel }}</span>
                <span class="mood-snippet-intensity">强度 {{ day.moodIntensity }}/5</span>
                <span v-if="day.contentSnippet" class="mood-snippet-text">「{{ day.contentSnippet }}{{ day.contentSnippet.length >= 30 ? '...' : '' }}」</span>
              </div>
            </div>

            <h4>AI 月总结</h4>
            <div v-if="!monthReport.aiSummary" class="empty-state compact">
              <p>暂无总结，可使用 AI 限额提前生成</p>
              <n-button type="primary" @click="store.generateMonthlyAiSummary(monthOffset)">生成 AI 总结</n-button>
            </div>
            <p v-else class="ai-summary">{{ monthReport.aiSummary }}</p>

            <div v-if="hasGuidance(monthReport)" class="report-guidance">
              <div v-if="monthReport.insights?.length">
                <h4>MoodCopilot 看见了</h4>
                <ul class="guidance-list">
                  <li v-for="item in monthReport.insights" :key="item">{{ item }}</li>
                </ul>
              </div>
              <div v-if="monthReport.suggestions?.length">
                <h4>可以试试</h4>
                <ul class="guidance-list">
                  <li v-for="item in monthReport.suggestions" :key="item">{{ item }}</li>
                </ul>
              </div>
            </div>
          </div>
        </template>
      </section>

      <!-- 自定义总结 -->
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
            <div v-if="s.dailyMoods?.length" class="summary-detail">
              <div class="mood-chart">
                <div v-for="day in s.dailyMoods" :key="day.date" class="mood-bar-row">
                  <span class="mood-date">{{ formatDay(day.date) }}</span>
                  <div class="mood-bar-track">
                    <div class="mood-bar" :style="{ width: (day.moodIntensity / 5) * 100 + '%', background: moodColor(day.moodLabel) }" />
                  </div>
                  <n-tag :color="{ color: moodColor(day.moodLabel), textColor: '#fff' }" size="small" round>{{ day.moodLabel }}</n-tag>
                </div>
              </div>
            </div>
            <p class="summary-body">{{ s.aiSummary }}</p>
            <div v-if="s.dailyMoods?.length" class="summary-diary-links">
              <span class="summary-diary-label">相关日记：</span>
              <button
                v-for="day in s.dailyMoods"
                :key="day.diaryIds?.[0]"
                class="diary-link-btn"
                :title="summaryDiarySnippet(day)"
                @click="router.push('/diary/' + day.diaryIds?.[0])"
              >「{{ summaryDiarySnippet(day) }}{{ summaryDiarySnippet(day).length >= 30 ? '...' : '' }}」</button>
            </div>
          </article>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NTag, NDatePicker, NSpin } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { useDiaryStore } from '../stores/diary'
import { diaryApi, summaryApi } from '../api'

const router = useRouter()
const store = useDiaryStore()

const mode = ref<'week' | 'month'>('week')
const weekOffset = ref(0)
const monthOffset = ref(0)
const creating = ref(false)
const startDate = ref<number | null>(null)
const endDate = ref<number | null>(null)
const summaries = ref<any[]>([])
const diarySnippetMap = ref<Record<number, string>>({})

const report = computed(() => store.weeklyReport)
const monthReport = computed(() => store.monthlyReport)

onMounted(() => {
  store.fetchWeeklyReport(weekOffset.value)
  loadSummaries()
})

watch(weekOffset, (val) => { store.fetchWeeklyReport(val) })
watch(monthOffset, (val) => { store.fetchMonthlyReport(val) })

function switchMode(m: 'week' | 'month') {
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

// ── SVG sparkline ──
const sparklineW = 300

const sparklinePoints = computed(() => {
  const moods = monthReport.value?.dailyMoods ?? []
  if (moods.length === 0) return ''
  const step = sparklineW / Math.max(moods.length - 1, 1)
  return moods.map((d, i) => {
    const x = Math.round(i * step)
    const y = Math.round(54 - (d.moodIntensity / 5) * 48)
    return `${x},${y}`
  }).join(' ')
})

const sparklinePointMeta = computed(() => {
  const moods = monthReport.value?.dailyMoods ?? []
  if (moods.length === 0) return []
  const step = sparklineW / Math.max(moods.length - 1, 1)
  return moods.map((d: any, i: number) => ({
    x: Math.round(i * step),
    y: Math.round(54 - (d.moodIntensity / 5) * 48),
    color: moodColor(d.moodLabel),
    date: d.date,
    diaryId: d.diaryIds?.[0] ?? i,
  }))
})

const sparklineArea = computed(() => {
  const pts = sparklinePoints.value
  if (!pts) return ''
  return `0,60 ${pts} ${sparklineW},60`
})

const sparklineFirst = computed(() => {
  const moods = monthReport.value?.dailyMoods ?? []
  return moods.length > 0 ? formatDay(moods[0].date) : ''
})
const sparklineLast = computed(() => {
  const moods = monthReport.value?.dailyMoods ?? []
  return moods.length > 0 ? formatDay(moods[moods.length - 1].date) : ''
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
    const res = await summaryApi.list()
    summaries.value = res.data.data ?? []
    await backfillSummarySnippets()
  } catch { /* ignore */ }
}

function summaryDiarySnippet(day: any) {
  const raw = day?.contentSnippet?.trim?.() || diarySnippetMap.value[day?.diaryIds?.[0]] || ''
  if (!raw) return formatDay(day?.date || '') + '日记'
  return raw.length > 30 ? raw.slice(0, 30) : raw
}

async function backfillSummarySnippets() {
  const ids = Array.from(new Set(
    summaries.value
      .flatMap((s: any) => s.dailyMoods ?? [])
      .filter((day: any) => !day?.contentSnippet && day?.diaryIds?.length)
      .map((day: any) => day.diaryIds[0])
      .filter((id: any) => Number.isFinite(id) && !diarySnippetMap.value[id])
  )) as number[]

  if (ids.length === 0) return

  const pairs = await Promise.all(ids.map(async (id) => {
    try {
      const res = await diaryApi.get(id)
      const content = res?.data?.data?.content ?? ''
      return [id, content.length > 30 ? content.slice(0, 30) : content] as const
    } catch {
      return [id, ''] as const
    }
  }))

  for (const [id, snippet] of pairs) {
    if (snippet) diarySnippetMap.value[id] = snippet
  }
}

async function remove(id: number) {
  await summaryApi.delete(id)
  summaries.value = summaries.value.filter((s: any) => s.id !== id)
}

function moodColor(label: string) {
  const map: Record<string, string> = {
    '焦虑': '#e08d72', '委屈': '#d4a373', '烦躁': '#e09f5c',
    '疲惫': '#9cb4a8', '轻松': '#7db89a', '平静': '#4f8f7c',
  }
  return map[label] || '#9cb4a8'
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
</script>
