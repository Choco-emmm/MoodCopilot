import { defineStore } from 'pinia'
import { ref } from 'vue'
import { diaryApi } from '../api'
import { normalizeResourceUrl } from '../utils/resource'

export interface Diary {
  id: number
  authorUserId: number
  authorName: string
  authorAvatar?: string | null
  content: string
  visibility: string
  analysis: DiaryAnalysis | null
  createdAt: string
  resonanceCount: number
  comments: DiaryComment[]
}

export interface DiaryAnalysis {
  moodLabel: string
  moodIntensity: number
  topicLabels: string[]
  summary: string
  feedback: string
}

export interface WeeklyReport {
  weekLabel: string
  diaryCount: number
  dailyMoods: DailyMood[]
  topicCounts: Record<string, number>
  aiSummary: string
  insights?: string[]
  suggestions?: string[]
  followUpPrompt?: string
  generatedAt?: string | null
  needsRegenerate?: boolean
}

export interface DailyMood {
  date: string
  moodLabel: string
  moodIntensity: number
  diaryIds?: number[]
  contentSnippet?: string
}

export interface DiaryComment {
  id: number
  parentCommentId: number | null
  replyToUserName: string | null
  authorName: string
  content: string
  createdAt: string
  replies: DiaryComment[]
}

export const useDiaryStore = defineStore('diary', () => {
  const myDiaries = ref<Diary[]>([])
  const publicDiaries = ref<Diary[]>([])
  const activeDiary = ref<Diary | null>(null)
  const similarDiaries = ref<Diary[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const errorMessage = ref<string | null>(null)
  const analysisStatus = ref<'idle' | 'saved' | 'analyzing' | 'complete' | 'failed'>('idle')
  const publicPage = ref(1)
  const publicTotal = ref(0)
  const hasMore = ref(true)
  const weeklyReport = ref<WeeklyReport | null>(null)
  const reportLoading = ref(false)
  const reportError = ref<string | null>(null)
  let analysisPollTimer: ReturnType<typeof setInterval> | null = null
  let analysisPollAttempts = 0
  const ANALYSIS_POLL_MAX_ATTEMPTS = 30

  async function fetchDiaries() {
    loading.value = true
    errorMessage.value = null
    try {
      publicPage.value = 1
      const [mineRes, publicRes] = await Promise.all([diaryApi.mine(1, 20), diaryApi.public(1, 20)])
      const mineData = normalizePageData(mineRes.data.data)
      myDiaries.value = mineData.items.map(normalize)
      const pdata = publicRes.data.data
      publicDiaries.value = (pdata.items ?? pdata).map(normalize)
      publicTotal.value = pdata.total ?? 0
      hasMore.value = hasNextPage(pdata, 1, 20)
    } catch (e: any) {
      myDiaries.value = []
      publicDiaries.value = []
      hasMore.value = false
      errorMessage.value = e?.response?.data?.message || '加载失败，请重新登录后重试'
    } finally {
      loading.value = false
    }
  }

  async function loadMorePublic() {
    if (!hasMore.value || loading.value) return
    loading.value = true
    try {
      publicPage.value++
      const res = await diaryApi.public(publicPage.value)
      const pdata = res.data.data
      const items = (pdata.items ?? pdata).map(normalize)
      const existing = new Set(publicDiaries.value.map(d => d.id))
      publicDiaries.value.push(...items.filter((item: Diary) => !existing.has(item.id)))
      hasMore.value = hasNextPage(pdata, publicPage.value, 20)
    } catch {
      publicPage.value = Math.max(1, publicPage.value - 1)
      hasMore.value = false
    } finally {
      loading.value = false
    }
  }

  async function createDiary(content: string, visibility: string) {
    saving.value = true
    errorMessage.value = null
    try {
      const res = await diaryApi.create({ content, visibility })
      const diary = normalize(res.data.data)
      activeDiary.value = diary
      analysisStatus.value = diary.analysis == null ? 'analyzing' : 'complete'
      await fetchDiaries()
      if (diary.analysis == null) {
        pollAnalysis(diary.id)
      }
      if (activeDiary.value) {
        await loadSimilar(activeDiary.value.id)
      }
    } catch (e: any) {
      errorMessage.value = e.response?.data?.message || '保存失败'
      throw e
    } finally {
      saving.value = false
    }
  }

  async function updateDiary(id: number, content: string, visibility: string) {
    saving.value = true
    errorMessage.value = null
    try {
      const res = await diaryApi.update(id, { content, visibility })
      const updated = normalize(res.data.data)
      mergeDiary(updated)
      if (activeDiary.value?.id === id) {
        activeDiary.value = updated
      }
      return updated
    } catch (e: any) {
      errorMessage.value = e?.response?.data?.message || '更新失败'
      throw e
    } finally {
      saving.value = false
    }
  }

  function pollAnalysis(diaryId: number) {
    clearAnalysisPollTimer()
    analysisPollAttempts = 0
    analysisPollTimer = setInterval(async () => {
      analysisPollAttempts += 1
      if (analysisPollAttempts > ANALYSIS_POLL_MAX_ATTEMPTS) {
        analysisStatus.value = 'failed'
        errorMessage.value = 'AI 分析耗时过长，请稍后点击重试获取'
        clearAnalysisPollTimer()
        return
      }
      try {
        const res = await diaryApi.get(diaryId)
        const updated = normalize(res.data.data)
        if (updated.analysis != null) {
          if (activeDiary.value?.id === diaryId) {
            activeDiary.value = updated
          }
          mergeDiary(updated)
          analysisStatus.value = 'complete'
          clearAnalysisPollTimer()
        }
      } catch {
        analysisStatus.value = 'failed'
        clearAnalysisPollTimer()
      }
    }, 2000)
  }

  function clearAnalysisPollTimer() {
    if (!analysisPollTimer) return
    clearInterval(analysisPollTimer)
    analysisPollTimer = null
    analysisPollAttempts = 0
  }

  async function refreshAnalysis(diaryId: number) {
    analysisStatus.value = 'analyzing'
    try {
      const res = await diaryApi.get(diaryId)
      const updated = normalize(res.data.data)
      activeDiary.value = updated
      mergeDiary(updated)
      analysisStatus.value = updated.analysis ? 'complete' : 'failed'
    } catch {
      analysisStatus.value = 'failed'
    }
  }

  async function loadSimilar(diaryId: number) {
    const res = await diaryApi.similar(diaryId, 3)
    similarDiaries.value = res.data.data.map(normalize)
  }

  async function addComment(diaryId: number, content: string, parentCommentId?: number) {
    const res = await diaryApi.addComment(diaryId, content, parentCommentId)
    const updated = normalize(res.data.data)
    mergeDiary(updated)
    if (activeDiary.value?.id === diaryId) {
      activeDiary.value = updated
    }
  }

  async function deleteComment(diaryId: number, commentId: number) {
    const res = await diaryApi.deleteComment(diaryId, commentId)
    const updated = res?.data?.data ? normalize(res.data.data) : null

    if (updated) {
      mergeDiary(updated)
      if (activeDiary.value?.id === diaryId) {
        activeDiary.value = updated
      }
      return
    }

    removeCommentIn(myDiaries, diaryId, commentId)
    removeCommentIn(publicDiaries, diaryId, commentId)
    if (activeDiary.value?.id === diaryId) {
      activeDiary.value = {
        ...activeDiary.value,
        comments: removeCommentFromTree(activeDiary.value.comments || [], commentId),
      }
    }
  }

  async function resonate(diaryId: number) {
    const res = await diaryApi.resonate(diaryId)
    const updated = normalize(res.data.data)
    mergeDiary(updated)
    if (activeDiary.value?.id === diaryId) {
      activeDiary.value = updated
    }
  }

  async function sendEncouragement(diaryId: number, message: string) {
    const res = await diaryApi.sendEncouragement(diaryId, message)
    const updated = normalize(res.data.data)
    mergeDiary(updated)
    if (activeDiary.value?.id === diaryId) {
      activeDiary.value = updated
    }
  }

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
    reportLoading.value = true
    reportError.value = null
    try {
      const res = await diaryApi.generateWeeklyReport(weekOffset)
      weeklyReport.value = res.data.data
    } catch (e: any) {
      reportError.value = formatReportError(e)
    } finally {
      reportLoading.value = false
    }
  }

  const monthlyReport = ref<WeeklyReport | null>(null)
  const monthLoading = ref(false)
  const monthError = ref<string | null>(null)

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
    monthLoading.value = true
    monthError.value = null
    try {
      const res = await diaryApi.generateMonthlyReport(monthOffset)
      monthlyReport.value = res.data.data
    } catch (e: any) {
      monthError.value = formatReportError(e)
    } finally {
      monthLoading.value = false
    }
  }

  function mergeDiary(updated: Diary) {
    replaceIn(myDiaries, updated)
    replaceIn(publicDiaries, updated)
  }

  function normalize(d: any): Diary {
    return {
      ...d,
      authorAvatar: normalizeResourceUrl(d.authorAvatar),
      comments: d.comments || [],
    }
  }

  async function deleteDiary(id: number) {
    await diaryApi.delete(id)
    myDiaries.value = myDiaries.value.filter(d => d.id !== id)
    if (activeDiary.value?.id === id) activeDiary.value = null
  }

  return {
    myDiaries, publicDiaries, activeDiary, similarDiaries, loading, saving, errorMessage,
    analysisStatus, hasMore, weeklyReport, reportLoading, reportError, monthlyReport, monthLoading, monthError,
    fetchDiaries, loadMorePublic, createDiary, updateDiary, loadSimilar, addComment, resonate, sendEncouragement, deleteDiary,
    refreshAnalysis, deleteComment,
    fetchWeeklyReport, fetchMonthlyReport, generateWeeklyAiSummary, generateMonthlyAiSummary, normalize,
  }
})

function replaceIn(list: Ref<Diary[]>, updated: Diary) {
  const idx = list.value.findIndex((d) => d.id === updated.id)
  if (idx !== -1) list.value[idx] = updated
}

function normalizePageData(data: any) {
  if (Array.isArray(data)) return { items: data, total: data.length, page: 1, size: data.length || 20 }
  return {
    items: data?.items ?? [],
    total: data?.total ?? 0,
    page: data?.page ?? 1,
    size: data?.size ?? 20,
  }
}

function hasNextPage(data: any, page: number, size: number) {
  const items = data.items ?? data
  if (typeof data.total === 'number') return page * size < data.total
  return Array.isArray(items) && items.length >= size
}

function formatReportError(e: any) {
  if (e?.response?.status === 429) {
    return '报告生成太频繁了，稍等一会儿再试。'
  }
  return e?.response?.data?.message || '报告暂时加载失败，可以稍后重试。'
}

function removeCommentIn(list: Ref<Diary[]>, diaryId: number, commentId: number) {
  const target = list.value.find((d) => d.id === diaryId)
  if (!target) return
  target.comments = removeCommentFromTree(target.comments || [], commentId)
}

function removeCommentFromTree(comments: DiaryComment[], commentId: number): DiaryComment[] {
  return comments
    .filter((comment) => comment.id !== commentId)
    .map((comment) => ({
      ...comment,
      replies: removeCommentFromTree(comment.replies || [], commentId),
    }))
}

import type { Ref } from 'vue'
