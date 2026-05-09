<template>
  <main class="app-shell">
    <AppHeader />

    <div class="report-page">
      <h2>情绪报告</h2>

      <!-- 本周报告：周选择器 -->
      <section class="report-section">
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
              <div v-for="day in report.dailyMoods" :key="day.date" class="mood-bar-row">
                <span class="mood-date">{{ formatDay(day.date) }}</span>
                <div class="mood-bar-track">
                  <div class="mood-bar" :style="{ width: (day.moodIntensity / 5) * 100 + '%', background: moodColor(day.moodLabel) }" />
                </div>
                <n-tag :color="{ color: moodColor(day.moodLabel), textColor: '#fff' }" size="small" round>{{ day.moodLabel }}</n-tag>
              </div>
            </div>

            <h4>本周话题</h4>
            <div class="topic-cloud">
              <n-tag v-for="(count, topic) in report.topicCounts" :key="topic" type="info" round>{{ topic }} × {{ count }}</n-tag>
            </div>

            <h4>AI 周总结</h4>
            <p class="ai-summary">{{ report.aiSummary }}</p>
            <div class="summary-actions">
              <n-button size="small" type="primary" :loading="saving" @click="saveToLibrary">保存到总结库</n-button>
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
              <div class="topic-cloud">
                <n-tag v-for="(count, topic) in s.topicCounts" :key="topic" type="info" round size="small">{{ topic }} × {{ count }}</n-tag>
              </div>
            </div>
            <p class="summary-body">{{ s.aiSummary }}</p>
          </article>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { NButton, NTag, NDatePicker } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { useDiaryStore } from '../stores/diary'
import { summaryApi } from '../api'

const store = useDiaryStore()
const weekOffset = ref(0)
const saving = ref(false)
const creating = ref(false)
const startDate = ref<number | null>(null)
const endDate = ref<number | null>(null)
const summaries = ref<any[]>([])

const report = computed(() => store.weeklyReport)

onMounted(() => {
  store.fetchWeeklyReport(weekOffset.value)
  loadSummaries()
})

watch(weekOffset, (val) => {
  store.fetchWeeklyReport(val)
})

function prevWeek() { weekOffset.value-- }
function nextWeek() { weekOffset.value++ }

async function saveToLibrary() {
  if (!report.value) return
  saving.value = true
  try {
    const now = new Date()
    const monday = new Date(now)
    monday.setDate(now.getDate() - ((now.getDay() + 6) % 7))
    monday.setDate(monday.getDate() + weekOffset.value * 7)
    const sunday = new Date(monday)
    sunday.setDate(monday.getDate() + 6)
    const iso = (d: Date) => d.toISOString().split('T')[0]
    await summaryApi.create({ startDate: iso(monday), endDate: iso(sunday) })
    await loadSummaries()
  } finally { saving.value = false }
}

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

function formatDay(dateStr: string) {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}
</script>
