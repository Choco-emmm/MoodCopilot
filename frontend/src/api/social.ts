import { api } from './core'
import { fetchEventSource } from '@microsoft/fetch-event-source'

export const growthApi = {
  checkIn: () => api.post('/growth/checkin'),
  status: () => api.get('/growth/status'),
  checkins: () => api.get('/growth/checkins'),
  progress: () => api.get('/growth/progress'),
  checkInStatus: () => api.get('/growth/checkin-status'),
}

export interface CheckInStatus {
  continuousDays: number
  currentMonthTotal: number
  todaySigned: boolean
  nextExpReward: number
}

export interface DailyTaskItem {
  label: string
  field: string
  current: number
  max: number
  expPerAction: number
  claimed: boolean
}

export const taskApi = {
  progress: () => api.get('/growth/progress'),
  checkIn: () => api.post('/growth/checkin'),
  checkInStatus: () => api.get('/growth/checkin-status'),
}

export const followApi = {
  follow: (userId: number) => api.post(`/follows/${userId}`),
  unfollow: (userId: number) => api.delete(`/follows/${userId}`),
  status: (userId: number) => api.get(`/follows/${userId}/status`),
}

export const summaryApi = {
  create: (data: { startDate: string; endDate: string }) => api.post('/summaries', data),
  list: (type?: string) => api.get('/summaries', { params: type ? { type } : undefined }),
  delete: (id: number) => api.delete(`/summaries/${id}`),
}

export const chatApi = {
  listConversations: () => api.get('/chat/conversations'),
  createConversation: (title?: string) => api.post('/chat/conversations', { title: title || '' }),
  deleteConversation: (id: number) => api.delete(`/chat/conversations/${id}`),
  getWelcomeTopics: () => api.get('/chat/welcome-topics'),
  getHistory: (id: number) => api.get(`/chat/conversations/${id}/history`),
  saveHistory: (id: number, messages: any[]) => api.put(`/chat/conversations/${id}/history`, { messages }),
  reply: (id: number, message: string, references: string[] = [], useReasoning = false, eventId?: number) =>
    api.post(`/chat/conversations/${id}/reply`, { message, references, useReasoning, ...(eventId ? { eventId } : {}) }),
  compressConversation: (id: number) =>
    api.post<{ compressed: boolean; message: string; summary?: string }>(`/chat/conversations/${id}/compress`),
  replyStream: async (
    id: number,
    message: string,
    references: string[],
    useReasoning: boolean,
    eventId: number | undefined,
    onChunk: (text: string) => void,
    ctrl: AbortController,
    onReferences?: (items: Array<{ type: string; diaryId: string; date: string; snippet: string }>) => void,
    onToolReferences?: (items: Array<{ type: string; diaryId?: string; date: string; snippet: string; toolName: string }>) => void,
    onStatus?: (status: { stage: string; message: string }) => void,
  ): Promise<void> => {
    const token = localStorage.getItem('token')
    let doneReceived = false
    try {
      await fetchEventSource(`/api/chat/conversations/${id}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ message, references, useReasoning, ...(eventId ? { eventId } : {}) }),
        signal: ctrl.signal,
        openWhenHidden: true,
        onmessage(event) {
          const raw = event.data
          try {
            const msg = JSON.parse(raw)
            if (msg.type === 'status') {
              onStatus?.({ stage: msg.stage, message: msg.message })
            } else if (msg.type === 'references') {
              onReferences?.(msg.items ?? [])
            } else if (msg.type === 'tool_references') {
              onToolReferences?.(msg.items ?? [])
            } else if (msg.type === 'chunk') {
              onChunk(msg.content ?? '')
            } else if (msg.type === 'done') {
              doneReceived = true
            }
          } catch {
            if (raw !== '[DONE]') onChunk(raw)
          }
        },
        onerror(err) {
          if (doneReceived) {
            ctrl.abort()
            throw new Error('__SSE_DONE__')
          }
          throw err
        },
      })
    } catch (e: any) {
      if (e?.message === '__SSE_DONE__') return
      throw e
    }
  },
}
