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
            <span class="week-label">{{ report?.weekLabel ?? '' }}</span>
            <n-button text circle :disabled="weekOffset === 0" @click="nextWeek">&rarr;</n-button>
          </div>
        </div>

        <div v-if="report && report.diaryCount === 0" class="empty-state">
          <p>本周还没有记录，去写一篇吧～</p>
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

            <h4>本周话题</h4>
            <div class="topic-cloud">
              <n-tag v-for="(count, topic) in report.topicCounts" :key="topic" type="info" round>{{ topic }} × {{ count }}</n-tag>
            </div>

            <h4>AI 周总结</h4>
            <p class="ai-summary">{{ report.aiSummary }}</p>
          </div>
        </template>
      </section>

      <!-- ==================== 月报 ==================== -->
      <section v-if="mode === 'month'" class="report-section">
        <div class="report-header">
          <h3>本月报告</h3>
          <div class="week-nav">
            <n-button text circle @click="prevMonth">&larr;</n-button>
            <span class="week-label">{{ monthReport?.weekLabel ?? '' }}</span>
            <n-button text circle :disabled="monthOffset === 0" @click="nextMonth">&rarr;</n-button>
          </div>
        </div>

        <div v-if="monthReport && monthReport.diaryCount === 0" class="empty-state">
          <p>本月还没有记录，去写一篇吧～</p>
        </div>

        <template v-if="monthReport && monthReport.diaryCount > 0">
          <div class="report-detail">
            <h4>情绪走向</h4>
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
            </svg>
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
                <span v-if="day.contentSnippet" class="mood-snippet-text">「{{ day.contentSnippet }}{{ day.contentSnippet.length >= 30 ? '...' : '' }}」</span>
              </div>
            </div>

            <h4>本月话题</h4>
            <div class="topic-cloud">
              <n-tag v-for="(count, topic) in monthReport.topicCounts" :key="topic" type="info" round>{{ topic }} × {{ count }}</n-tag>
            </div>

            <h4>AI 月总结</h4>
            <p class="ai-summary">{{ monthReport.aiSummary }}</p>
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
              <div class="topic-cloud">
                <n-tag v-for="(count, topic) in s.topicCounts" :key="topic" type="info" round size="small">{{ topic }} × {{ count }}</n-tag>
              </div>
            </div>
            <p class="summary-body">{{ s.aiSummary }}</p>
            <div v-if="s.dailyMoods?.length" class="summary-diary-links">
              <span class="summary-diary-label">相关日记：</span>
              <button
                v-for="day in s.dailyMoods"
                :key="day.diaryIds?.[0]"
                class="diary-link-btn"
                :title="day.contentSnippet"
                @click="router.push('/diary/' + day.diaryIds?.[0])"
              >「{{ day.contentSnippet || '...' }}{{ (day.contentSnippet?.length || 0) >= 30 ? '...' : '' }}」</button>
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
import { NButton, NTag, NDatePicker } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { useDiaryStore } from '../stores/diary'
import { summaryApi } from '../api'

const router = useRouter()
const store = useDiaryStore()

const mode = ref<'week' | 'month'>('week')
const weekOffset = ref(0)
const monthOffset = ref(0)
const creating = ref(false)
const startDate = ref<number | null>(null)
const endDate = ref<number | null>(null)
const summaries = ref<any[]>([])

const report = computed(() => store.weeklyReport)
const monthReport = computed(() => store.monthlyReport)

onMounted(() => {
  store.fetchWeeklyReport(weekOffset.value)
  store.fetchMonthlyReport(monthOffset.value)
  loadSummaries()
})

watch(weekOffset, (val) => { store.fetchWeeklyReport(val) })
watch(monthOffset, (val) => { store.fetchMonthlyReport(val) })

function switchMode(m: 'week' | 'month') { mode.value = m }

function prevWeek() { weekOffset.value-- }
function nextWeek() { weekOffset.value++ }
function prevMonth() { monthOffset.value-- }
function nextMonth() { monthOffset.value++ }

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
  } catch { /* ignore */ }
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

function formatDay(dateStr: string) {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}
</script>
