import { defineStore } from 'pinia'
import { ref } from 'vue'
import { diaryApi } from '../api'

export interface Diary {
  id: number
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

export interface DiaryComment {
  id: number
  authorName: string
  content: string
  createdAt: string
}

export const useDiaryStore = defineStore('diary', () => {
  const myDiaries = ref<Diary[]>([])
  const publicDiaries = ref<Diary[]>([])
  const activeDiary = ref<Diary | null>(null)
  const similarDiaries = ref<Diary[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const errorMessage = ref<string | null>(null)

  async function fetchDiaries() {
    loading.value = true
    try {
      const [mineRes, publicRes] = await Promise.all([diaryApi.mine(), diaryApi.public()])
      myDiaries.value = mineRes.data.data.map(normalize)
      publicDiaries.value = publicRes.data.data.map(normalize)
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

  async function addComment(diaryId: number, content: string) {
    const res = await diaryApi.addComment(diaryId, content)
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

  function mergeDiary(updated: Diary) {
    replaceIn(myDiaries, updated)
    replaceIn(publicDiaries, updated)
  }

  function normalize(d: any): Diary {
    return { ...d, comments: d.comments || [] }
  }

  return {
    myDiaries, publicDiaries, activeDiary, similarDiaries, loading, saving, errorMessage,
    fetchDiaries, createDiary, loadSimilar, addComment, resonate, normalize,
  }
})

function replaceIn(list: Ref<Diary[]>, updated: Diary) {
  const idx = list.value.findIndex((d) => d.id === updated.id)
  if (idx !== -1) list.value[idx] = updated
}

import type { Ref } from 'vue'
