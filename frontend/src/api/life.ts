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
}

export interface LifeChapter {
  id: number
  title: string
  themeSummary: string
  startDate: string
  endDate: string
  dominantMoods: string[]
  growthReflection: string
  diaryCount: number
  createdAt?: string
}

export const lifeEventApi = {
  list: () => api.get<ApiResponse<LifeEvent[]>>('/life-events'),
  get: (id: number) => api.get<ApiResponse<LifeEvent>>(`/life-events/${id}`),
  diaries: (keyword?: string) => api.get<ApiResponse<LifeDiaryOption[]>>('/life-events/diaries', { params: keyword ? { keyword } : {} }),
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
}
