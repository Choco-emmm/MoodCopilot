<template>
  <main class="app-shell">
    <AppHeader />

    <div class="report-page">
    <div class="report-header">
      <h2>周报</h2>
      <div class="week-nav">
        <n-button text circle @click="prevWeek">
          <span class="arrow">&larr;</span>
        </n-button>
        <span class="week-label">{{ report?.weekLabel ?? '' }}</span>
        <n-button text circle :disabled="weekOffset === 0" @click="nextWeek">
          <span class="arrow">&rarr;</span>
        </n-button>
      </div>
    </div>

    <div v-if="report && report.diaryCount === 0" class="empty-state">
      <p>本周还没有记录，去写一篇吧～</p>
      <n-button type="primary" @click="router.push('/')">去写日记</n-button>
    </div>

    <template v-if="report && report.diaryCount > 0">
      <section class="report-section">
        <h3>情绪趋势</h3>
        <div class="mood-chart">
          <div
            v-for="day in report.dailyMoods"
            :key="day.date"
            class="mood-bar-row"
          >
            <span class="mood-date">{{ formatDay(day.date) }}</span>
            <div class="mood-bar-track">
              <div
                class="mood-bar"
                :style="{
                  width: (day.moodIntensity / 5) * 100 + '%',
                  background: moodColor(day.moodLabel),
                }"
              />
            </div>
            <n-tag :color="{ color: moodColor(day.moodLabel), textColor: '#fff' }" size="small" round>
              {{ day.moodLabel }}
            </n-tag>
          </div>
        </div>
      </section>

      <section class="report-section">
        <h3>本周话题</h3>
        <div class="topic-cloud">
          <n-tag
            v-for="(count, topic) in report.topicCounts"
            :key="topic"
            type="info"
            round
            size="large"
          >
            {{ topic }} × {{ count }}
          </n-tag>
        </div>
      </section>

      <section class="report-section">
        <h3>AI 周总结</h3>
        <p class="ai-summary">{{ report.aiSummary }}</p>
        <div class="summary-actions">
          <n-button size="small" type="primary" :loading="saving" @click="saveToLibrary">
            保存到总结库
          </n-button>
        </div>
      </section>
    </template>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NTag } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { useDiaryStore } from '../stores/diary'
import { summaryApi } from '../api'

const router = useRouter()
const store = useDiaryStore()
const weekOffset = ref(0)
const saving = ref(false)

const report = computed(() => store.weeklyReport)

onMounted(() => {
  store.fetchWeeklyReport(weekOffset.value)
})

watch(weekOffset, (val) => {
  store.fetchWeeklyReport(val)
})

function prevWeek() { weekOffset.value-- }
function nextWeek() { weekOffset.value++ }

function moodColor(label: string) {
  const map: Record<string, string> = {
    '焦虑': '#e08d72',
    '委屈': '#d4a373',
    '烦躁': '#e09f5c',
    '疲惫': '#9cb4a8',
    '轻松': '#7db89a',
    '平静': '#4f8f7c',
  }
  return map[label] || '#9cb4a8'
}

async function saveToLibrary() {
  if (!report.value) return
  const parts = report.value.weekLabel.split(' - ')
  const startDate = new Date(new Date().getFullYear(), 0, 1)
  // Calculate start and end dates from the report label
  const now = new Date()
  const currentMonday = new Date(now)
  currentMonday.setDate(now.getDate() - ((now.getDay() + 6) % 7))
  currentMonday.setDate(currentMonday.getDate() + weekOffset.value * 7)
  const sunday = new Date(currentMonday)
  sunday.setDate(currentMonday.getDate() + 6)

  const iso = (d: Date) => d.toISOString().split('T')[0]
  saving.value = true
  try {
    await summaryApi.create({ startDate: iso(currentMonday), endDate: iso(sunday) })
  } finally {
    saving.value = false
  }
}

function formatDay(dateStr: string) {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}
</script>
