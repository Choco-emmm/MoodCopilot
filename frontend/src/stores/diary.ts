import { defineStore } from 'pinia'
import { ref } from 'vue'
import { diaryApi } from '../api'

export interface Diary {
  id: number
  authorUserId: number
  authorName: string
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
  const publicPage = ref(1)
  const publicTotal = ref(0)
  const hasMore = ref(true)
  const weeklyReport = ref<WeeklyReport | null>(null)
  const reportLoading = ref(false)

  async function fetchDiaries() {
    loading.value = true
    try {
      publicPage.value = 1
      const [mineRes, publicRes] = await Promise.all([diaryApi.mine(), diaryApi.public(1)])
      myDiaries.value = mineRes.data.data.map(normalize)
      const pdata = publicRes.data.data
      publicDiaries.value = (pdata.items ?? pdata).map(normalize)
      publicTotal.value = pdata.total ?? 0
      hasMore.value = (pdata.items ?? pdata).length >= 20
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
      publicDiaries.value.push(...items)
      hasMore.value = items.length >= 20
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

  function pollAnalysis(diaryId: number) {
    const interval = setInterval(async () => {
      try {
        const res = await diaryApi.get(diaryId)
        const updated = normalize(res.data.data)
        if (updated.analysis != null) {
          if (activeDiary.value?.id === diaryId) {
            activeDiary.value = updated
          }
          mergeDiary(updated)
          clearInterval(interval)
        }
      } catch {
        clearInterval(interval)
      }
    }, 2000)
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

  async function resonate(diaryId: number) {
    const res = await diaryApi.resonate(diaryId)
    const updated = normalize(res.data.data)
    mergeDiary(updated)
    if (activeDiary.value?.id === diaryId) {
      activeDiary.value = updated
    }
  }

  async function fetchWeeklyReport(weekOffset = 0) {
    reportLoading.value = true
    try {
      const res = await diaryApi.weeklyReport(weekOffset)
      weeklyReport.value = res.data.data
    } finally {
      reportLoading.value = false
    }
  }

  function mergeDiary(updated: Diary) {
    replaceIn(myDiaries, updated)
    replaceIn(publicDiaries, updated)
  }

  function normalize(d: any): Diary {
    return { ...d, comments: d.comments || [] }
  }

  async function deleteDiary(id: number) {
    await diaryApi.delete(id)
    myDiaries.value = myDiaries.value.filter(d => d.id !== id)
    if (activeDiary.value?.id === id) activeDiary.value = null
  }

  return {
    myDiaries, publicDiaries, activeDiary, similarDiaries, loading, saving, errorMessage,
    hasMore, weeklyReport, reportLoading,
    fetchDiaries, loadMorePublic, createDiary, loadSimilar, addComment, resonate, deleteDiary,
    fetchWeeklyReport, normalize,
  }
})

function replaceIn(list: Ref<Diary[]>, updated: Diary) {
  const idx = list.value.findIndex((d) => d.id === updated.id)
  if (idx !== -1) list.value[idx] = updated
}

import type { Ref } from 'vue'
