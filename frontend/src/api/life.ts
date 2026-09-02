import { api } from './core'
import type { ApiResponse } from './types'

export interface LifeEvent {
  id: number
  title: string
  description?: string
  targetDate: string
  status: 'PENDING' | 'FOLLOWED_UP' | 'ARCHIVED' | string
  diaryIds: number[]
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
  updateStatus: (id: number, status: string, note?: string) =>
    api.put<ApiResponse<LifeEvent>>(`/life-events/${id}/status`, { status, note: note || '' }),
}

export const lifeChapterApi = {
  list: () => api.get<ApiResponse<LifeChapter[]>>('/life-chapters'),
}
