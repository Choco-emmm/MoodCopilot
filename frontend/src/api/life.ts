import { api } from './core'
import type { ApiResponse } from './types'

export interface LifeEvent {
  id: number
  title: string
  description?: string
  targetDate: string
  endDate?: string
  startTime?: string
  endTime?: string
  status: 'PENDING' | 'FOLLOWED_UP' | string
  diaryIds: number[]
  diaryCount?: number
  lastDiaryId?: number
  followUpNote?: string
  createdAt?: string
  updatedAt?: string
  temporalPhase?: 'UPCOMING' | 'ONGOING' | 'PAST' | string
  nextFollowUpAt?: string
  lastFollowUpAt?: string
  followUpCount?: number
  followUpReason?: string
  followUpCompleted?: boolean
  importance?: number
}

export interface LifeChapter {
  id: number
  title: string
  themeSummary: string
  startDate: string
  endDate?: string
  dominantMoods: string[]
  growthReflection: string
  diaryCount: number
  createdAt?: string
  updatedAt?: string
  currentVersion?: number
  lifecycleStatus?: string
  generationStatus?: string
  lastGeneratedAt?: string
  lastGenerationError?: string
  eventCount?: number
  diarySources?: LifeChapterDiarySource[]
  eventSources?: LifeChapterEventSource[]
  segmentType?: 'DYNAMIC' | 'LEGACY_MONTH' | string
  isOpen?: boolean
  boundaryReason?: string
  boundaryConfidence?: number
  lastSourceAt?: string
  previousChapterId?: number
  nextChapterId?: number
}

export interface LifeChapterDiarySource { id: number; date: string; excerpt: string; summary?: string }
export interface LifeChapterEventSource { id: number; title: string; startDate: string; endDate?: string }
export interface LifeChapterVersion { version: number; title: string; themeSummary: string; dominantMoods: string[]; growthReflection?: string; sourceSnapshotHash?: string; createdAt?: string; diaryIds?: number[]; eventIds?: number[] }
export interface LifeTimelinePage { stages: LifeChapter[]; gaps: { startDate: string; endDate: string }[]; nextCursor?: string | null }
export interface LifeTimelineCandidate { id: number; leftChapterId: number; rightChapterId?: number; suggestedStartDate: string; suggestedEndDate?: string; reason: string; confidence: number; sourceDiaryIds: number[]; sourceEventIds: number[]; status: string; createdAt?: string; resolvedAt?: string }

export const lifeEventApi = {
  list: () => api.get<ApiResponse<LifeEvent[]>>('/life-events'),
  get: (id: number) => api.get<ApiResponse<LifeEvent>>(`/life-events/${id}`),
  diaries: (params: { keyword?: string; startDate?: string; endDate?: string; page?: number; size?: number } = {}) =>
    api.get<ApiResponse<LifeDiaryPage>>('/life-events/diaries', { params }),
  create: (data: LifeEventPayload) => api.post<ApiResponse<LifeEvent>>('/life-events', data),
  update: (id: number, data: LifeEventPayload) => api.put<ApiResponse<LifeEvent>>(`/life-events/${id}`, data),
  updateDiaries: (id: number, diaryIds: number[]) => api.put<ApiResponse<LifeEvent>>(`/life-events/${id}/diaries`, { diaryIds }),
  updateStatus: (id: number, status: 'PENDING' | 'FOLLOWED_UP', note?: string) =>
    api.put<ApiResponse<LifeEvent>>(`/life-events/${id}/status`, { status, note: note || '' }),
}

export interface LifeDiaryOption {
  id: number
  date: string
  excerpt: string
  summary?: string
}

export interface LifeDiaryPage {
  items: LifeDiaryOption[]
  total: number
  page: number
  size: number
  hasMore: boolean
}

export interface LifeEventPayload {
  title: string
  description?: string
  targetDate: string
  endDate?: string
  startTime?: string
  endTime?: string
  diaryIds?: number[]
}

export const lifeChapterApi = {
  list: () => api.get<ApiResponse<LifeChapter[]>>('/life-chapters'),
  get: (id: number) => api.get<ApiResponse<LifeChapter>>(`/life-chapters/${id}`),
  versions: (id: number) => api.get<ApiResponse<LifeChapterVersion[]>>(`/life-chapters/${id}/versions`),
  refresh: (id: number) => api.post<ApiResponse<void>>(`/life-chapters/${id}/refresh`),
  timeline: (params: { from?: string; to?: string; cursor?: string; size?: number; includeGaps?: boolean } = {}) =>
    api.get<ApiResponse<LifeTimelinePage>>('/life-timeline', { params }),
  timelineVersions: (id: number) => api.get<ApiResponse<LifeChapterVersion[]>>(`/life-timeline/${id}/versions`),
  candidates: (status = 'PENDING') => api.get<ApiResponse<LifeTimelineCandidate[]>>('/life-timeline/candidates', { params: { status } }),
  acceptCandidate: (id: number) => api.post<ApiResponse<void>>(`/life-timeline/candidates/${id}/accept`),
  rejectCandidate: (id: number) => api.post<ApiResponse<void>>(`/life-timeline/candidates/${id}/reject`),
}
