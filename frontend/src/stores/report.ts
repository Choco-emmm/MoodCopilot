import { defineStore } from 'pinia'
import { ref } from 'vue'
import { diaryApi } from '../api'

export interface DailyMood {
  date: string
  moodLabel: string
  moodIntensity: number
  valence?: number
  arousal?: number
  diaryIds?: number[]
  contentSnippet?: string
}

export interface WeeklyReport {
  weekLabel: string
  diaryCount: number
  dailyMoods: DailyMood[]
  topicCounts: Record<string, number>
  moodDistribution?: Record<string, number>
  moodDominantQuadrant?: string
  positiveRatioPercent?: number
  highEnergyRatioPercent?: number
  aiSummary: string
  insights?: string[]
  suggestions?: string[]
  followUpPrompt?: string
  generatedAt?: string | null
  needsRegenerate?: boolean
}

function formatReportError(e: any): string {
  return e?.response?.data?.message || '报告暂时加载失败，可以稍后重试。'
}

export const useReportStore = defineStore('report', () => {
  // ── Weekly ──
  const weeklyReport = ref<WeeklyReport | null>(null)
  const reportLoading = ref(false)
  const generatingWeekly = ref(false)
  const reportError = ref<string | null>(null)

  // ── Monthly ──
  const monthlyReport = ref<WeeklyReport | null>(null)
  const monthLoading = ref(false)
  const generatingMonthly = ref(false)
  const monthError = ref<string | null>(null)

  async function fetchWeeklyReport(weekOffset = 0) {
    reportLoading.value = true
    reportError.value = null
    try {
      const res = await diaryApi.weeklyReport(weekOffset)
      weeklyReport.value = res.data.data
    } catch (e: any) {
      weeklyReport.value = null
      reportError.value = formatReportError(e)
    } finally {
      reportLoading.value = false
    }
  }

  async function generateWeeklyAiSummary(weekOffset = 0) {
    generatingWeekly.value = true
    reportError.value = null
    try {
      const res = await diaryApi.generateWeeklyReport(weekOffset)
      weeklyReport.value = res.data.data
    } catch (e: any) {
      reportError.value = formatReportError(e)
    } finally {
      generatingWeekly.value = false
    }
  }

  async function fetchMonthlyReport(monthOffset = 0) {
    monthLoading.value = true
    monthError.value = null
    try {
      const res = await diaryApi.monthlyReport(monthOffset)
      monthlyReport.value = res.data.data
    } catch (e: any) {
      monthlyReport.value = null
      monthError.value = formatReportError(e)
    } finally {
      monthLoading.value = false
    }
  }

  async function generateMonthlyAiSummary(monthOffset = 0) {
    generatingMonthly.value = true
    monthError.value = null
    try {
      const res = await diaryApi.generateMonthlyReport(monthOffset)
      monthlyReport.value = res.data.data
    } catch (e: any) {
      monthError.value = formatReportError(e)
    } finally {
      generatingMonthly.value = false
    }
  }

  return {
    weeklyReport, reportLoading, generatingWeekly, reportError,
    monthlyReport, monthLoading, generatingMonthly, monthError,
    fetchWeeklyReport, fetchMonthlyReport, generateWeeklyAiSummary, generateMonthlyAiSummary,
  }
})
