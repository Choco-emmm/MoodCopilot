import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { Ref } from 'vue'
import { diaryApi } from '../api'
import type { PaginatedData } from '../api'
import type { DiaryImageMetaPayload } from '../api/diary'
import { normalizeResourceUrl } from '../utils/resource'
import { tryExpToast } from '../utils/toast'
import { logWarn } from '../utils/logger'
import { useCommentStore, type DiaryComment } from './comment'

export interface MusicMeta {
  title: string
  artist: string
  coverUrl: string
  userLyric?: string
  songUrl?: string
  moodTags?: string | null
  themeSummary?: string | null
}

export interface Diary {
  id: number
  authorUserId: number
  authorName: string
  authorAvatar?: string | null
  authorLevel?: number | null
  authorRole?: string | null
  content: string
  visibility: string
  analysis: DiaryAnalysis | null
  musicMeta?: MusicMeta | null
  images?: string[] | null
  imageMeta?: DiaryImageMetaPayload[] | null
  analysisStatus?: string | null // "analyzing" | "complete" | "failed" | "cancelled" | "skipped_quota" | "failed_limit" | "skipped_user"
  analysisError?: string | null
  createdAt: string
  resonanceCount: number
  likedByMe?: boolean
  isPinned?: boolean
  comments: DiaryComment[]
  commentCount?: number
}

export interface DiaryAnalysis {
  moodLabel: string
  moodIntensity: number
  topicLabels: string[]
  secondaryMoods?: string[]
  valence?: number
  arousal?: number
  summary: string
  feedback: string
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

  watch(analysisStatus, (val) => {
    if (val === 'complete' || val === 'failed') {
      setTimeout(() => {
        if (analysisStatus.value === val) {
          analysisStatus.value = 'idle'
        }
      }, 5000)
    }
  })

  const showGlobalAnalysisModal = ref(false)
  const globalAnalysisDiary = ref<Diary | null>(null)
  const publicPage = ref(1)
  const publicTotal = ref(0)
  const hasMore = ref(true)

  let analysisPollTimer: ReturnType<typeof setInterval> | null = null
  let analysisPollAttempts = 0
  const ANALYSIS_POLL_MAX_ATTEMPTS = 60

  // ── Helpers ──

  function mergeDiary(updated: Diary) {
    replaceIn(myDiaries, updated)
    replaceIn(publicDiaries, updated)
  }

  function normalize(d: any): Diary {
    return {
      ...d,
      likedByMe: Boolean(d.likedByMe),
      authorAvatar: normalizeResourceUrl(d.authorAvatar),
      comments: d.comments || [],
    }
  }

  // ── Fetch ──

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
    } catch (e) {
      logWarn('diary', '加载更多公开日记失败', e)
      publicPage.value = Math.max(1, publicPage.value - 1)
      hasMore.value = false
    } finally {
      loading.value = false
    }
  }

  // ── Create / Update / Delete ──

  async function createDiary(content: string, visibility: string, musicMeta?: MusicMeta, analyze = true, images?: string[], imageMeta?: DiaryImageMetaPayload[], useReasoning = false) {
    saving.value = true
    errorMessage.value = null
    try {
      const res = await diaryApi.create({ content, visibility, musicMeta, images, imageMeta, analyze, useReasoning })
      const diary = normalize(res.data.data)
      activeDiary.value = diary

      if (diary.analysisStatus === 'skipped_quota' || diary.analysisStatus === 'failed_limit') {
        analysisStatus.value = 'complete'
        errorMessage.value = diary.analysisStatus === 'failed_limit'
          ? '深度思考额度已用完，日记已保存，可稍后重新获取分析'
          : '今日 AI 分析次数已用完，日记已保存'
      } else if (diary.analysisStatus === 'skipped_user') {
        analysisStatus.value = 'complete'
      } else {
        analysisStatus.value = diary.analysis == null ? 'analyzing' : 'complete'
      }

      if (content.length >= 15) {
        const bonus = content.length > 100 ? '+30' : '+20'
        tryExpToast('diary', `写日记 ${bonus} EXP`)
      }
      await fetchDiaries()
      if (diary.analysisStatus === 'skipped_quota' || diary.analysisStatus === 'failed_limit' || diary.analysisStatus === 'skipped_user') {
        replaceIn(myDiaries, { ...diary, analysisStatus: diary.analysisStatus } as Diary)
      }
      if (diary.analysis == null && diary.analysisStatus !== 'skipped_quota' && diary.analysisStatus !== 'failed_limit' && diary.analysisStatus !== 'skipped_user') {
        window.$message?.success('已保存，MoodCopilot 正在分析中...', { duration: 5000 })
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

  async function updateDiary(id: number, content: string, visibility: string, musicMeta?: MusicMeta, images?: string[], analyze = true, imageMeta?: DiaryImageMetaPayload[], useReasoning = false) {
    saving.value = true
    errorMessage.value = null
    try {
      const res = await diaryApi.update(id, { content, visibility, musicMeta, images, imageMeta, analyze, useReasoning })
      const updated = normalize(res.data.data)

      if (updated.analysisStatus === 'skipped_quota' || updated.analysisStatus === 'failed_limit') {
        analysisStatus.value = 'complete'
        errorMessage.value = updated.analysisStatus === 'failed_limit'
          ? '深度思考额度已用完，日记修改已保存，可稍后重新获取分析'
          : '今日 AI 分析次数已用完，日记修改已保存'
      } else if (updated.analysisStatus === 'skipped_user') {
        analysisStatus.value = 'complete'
      } else {
        analysisStatus.value = updated.analysis == null ? 'analyzing' : 'complete'
      }

      mergeDiary(updated)
      if (activeDiary.value?.id === id) {
        activeDiary.value = updated
      }

      if (updated.analysis == null && updated.analysisStatus !== 'skipped_quota' && updated.analysisStatus !== 'failed_limit' && updated.analysisStatus !== 'skipped_user') {
        window.$message?.success('日记已修改，MoodCopilot 正在重新分析中...', { duration: 5000 })
        pollAnalysis(updated.id)
      }

      return updated
    } catch (e: any) {
      errorMessage.value = e?.response?.data?.message || '更新失败'
      throw e
    } finally {
      saving.value = false
    }
  }

  async function deleteDiary(id: number) {
    await diaryApi.delete(id)
    myDiaries.value = myDiaries.value.filter(d => d.id !== id)
    publicDiaries.value = publicDiaries.value.filter(d => d.id !== id)
    if (activeDiary.value?.id === id) activeDiary.value = null
  }

  // ── Analysis ──

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
          globalAnalysisDiary.value = updated
          showGlobalAnalysisModal.value = true
          clearAnalysisPollTimer()
        }
      } catch (e) {
        logWarn('diary', '轮询分析结果失败', diaryId, e)
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
      if (updated.analysis) {
        globalAnalysisDiary.value = updated
        showGlobalAnalysisModal.value = true
      }
    } catch (e) {
      logWarn('diary', '刷新分析失败', diaryId, e)
      analysisStatus.value = 'failed'
    }
  }

  function closeAnalysisModal() {
    showGlobalAnalysisModal.value = false
  }

  // ── Similar ──

  async function loadSimilar(diaryId: number) {
    const res = await diaryApi.similar(diaryId, 3)
    similarDiaries.value = res.data.data.map(normalize)
  }

  // ── Comments ──

  async function addComment(diaryId: number, content: string, parentCommentId?: number) {
    const commentStore = useCommentStore()
    const updated = await commentStore.addComment(diaryId, content, parentCommentId)
    const normalized = normalize(updated)
    mergeDiary(normalized)
    if (activeDiary.value?.id === diaryId) {
      activeDiary.value = normalized
    }
  }

  async function deleteComment(diaryId: number, commentId: number) {
    const commentStore = useCommentStore()
    const data = await commentStore.deleteComment(diaryId, commentId)
    const updated = data ? normalize(data) : null

    if (updated) {
      mergeDiary(updated)
      if (activeDiary.value?.id === diaryId) {
        activeDiary.value = updated
      }
      return
    }

    removeCommentIn(myDiaries, diaryId, commentId, commentStore)
    removeCommentIn(publicDiaries, diaryId, commentId, commentStore)
    if (activeDiary.value?.id === diaryId) {
      activeDiary.value = {
        ...activeDiary.value,
        comments: commentStore.removeCommentFromTree(activeDiary.value.comments || [], commentId),
      }
    }
  }

  // ── Social ──

  const resonatingKeys = new Set<number>()

  async function resonate(diaryId: number, localTarget?: Diary) {
    if (resonatingKeys.has(diaryId)) return
    resonatingKeys.add(diaryId)

    const target = localTarget || myDiaries.value.find(d => d.id === diaryId) ||
      publicDiaries.value.find(d => d.id === diaryId) ||
      (activeDiary.value?.id === diaryId ? activeDiary.value : null)

    let originalLikedByMe = false
    let originalCount = 0

    if (target) {
      originalLikedByMe = !!target.likedByMe
      originalCount = target.resonanceCount || 0

      const newLikedByMe = !originalLikedByMe
      const newCount = newLikedByMe ? originalCount + 1 : Math.max(0, originalCount - 1)

      // In-place mutation for reactive components that hold a reference to target
      target.likedByMe = newLikedByMe
      target.resonanceCount = newCount

      mergeDiary({ ...target })
      if (activeDiary.value?.id === diaryId) {
        activeDiary.value = { ...target }
      }
    }

    try {
      const res = await diaryApi.resonate(diaryId)
      const updated = normalize(res.data.data)
      if (updated.likedByMe && !originalLikedByMe) {
        tryExpToast('like', '点赞 +2 EXP')
      }

      if (target) {
        Object.assign(target, updated)
      }

      mergeDiary(updated)
      if (activeDiary.value?.id === diaryId) {
        activeDiary.value = updated
      }
    } catch (e) {
      if (target) {
        target.likedByMe = originalLikedByMe
        target.resonanceCount = originalCount

        mergeDiary({ ...target })
        if (activeDiary.value?.id === diaryId) {
          activeDiary.value = { ...target }
        }
      }
      window.$message?.error('点赞失败，请稍后重试')
    } finally {
      resonatingKeys.delete(diaryId)
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

  return {
    myDiaries, publicDiaries, activeDiary, similarDiaries, loading, saving, errorMessage,
    analysisStatus, showGlobalAnalysisModal, globalAnalysisDiary, closeAnalysisModal,
    hasMore, publicTotal,
    fetchDiaries, loadMorePublic, createDiary, updateDiary, deleteDiary,
    loadSimilar, addComment, deleteComment, resonate, sendEncouragement,
    refreshAnalysis, normalize,
  }
})

// ── Private helpers ──

function replaceIn(list: Ref<Diary[]>, updated: Diary) {
  const idx = list.value.findIndex((d) => d.id === updated.id)
  if (idx !== -1) list.value[idx] = updated
}

function normalizePageData(data: any): PaginatedData<Diary> {
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

function removeCommentIn(list: Ref<Diary[]>, diaryId: number, commentId: number, commentStore: ReturnType<typeof useCommentStore>) {
  const target = list.value.find((d) => d.id === diaryId)
  if (!target) return
  target.comments = commentStore.removeCommentFromTree(target.comments || [], commentId)
}
