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
  getPersona: (id: number) => api.get(`/chat/conversations/${id}/persona`),
  updatePersona: (id: number, data: { role?: string; tone?: string[]; behaviorFlags?: string[]; disabledBehaviorFlags?: string[]; customTone?: string; customResponseStyle?: string }) =>
    api.put(`/chat/conversations/${id}/persona`, data),
  resetPersona: (id: number) => api.delete(`/chat/conversations/${id}/persona`),
  saveHistory: (id: number, messages: any[]) => api.put(`/chat/conversations/${id}/history`, { messages }),
  reply: (id: number, message: string, references: string[] = [], useReasoning = false, eventId?: number, referencePurpose?: string,
    referenceItems?: Array<{ sourceType: string; sourceId: number; referencePurpose?: string }>) =>
    api.post(`/chat/conversations/${id}/reply`, { message, references, useReasoning,
      ...(eventId ? { eventId } : {}), ...(referencePurpose ? { referencePurpose } : {}),
      ...(referenceItems?.length ? { referenceItems } : {}) }),
  compressConversation: (id: number) =>
    api.post<{ compressed: boolean; message: string; summary?: string }>(`/chat/conversations/${id}/compress`),
  getRunStatus: (conversationId: number, runId: string) =>
    api.get(`/chat/conversations/${conversationId}/runs/${encodeURIComponent(runId)}`),
  clearActiveRun: (conversationId: number, runId?: string) => {
    const key = chatRunStorageKey(conversationId)
    if (!runId || sessionStorage.getItem(key) === runId) sessionStorage.removeItem(key)
  },
  getActiveRun: (conversationId: number) => sessionStorage.getItem(chatRunStorageKey(conversationId)),
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
    referencePurpose?: string,
    referenceItems?: Array<{ sourceType: string; sourceId: number; referencePurpose?: string }>,
  ): Promise<void> => {
    const clientRequestId = typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`
    const startResponse = await api.post(`/chat/conversations/${id}/runs`, {
      clientRequestId,
      message,
      references,
      useReasoning,
      ...(eventId ? { eventId } : {}),
      ...(referencePurpose ? { referencePurpose } : {}),
      ...(referenceItems?.length ? { referenceItems } : {}),
    })
    const startPayload = startResponse.data?.data ?? startResponse.data
    const runId = String(startPayload?.runId || '')
    if (!runId) throw new Error('生成任务创建失败')

    sessionStorage.setItem(chatRunStorageKey(id), runId)

    await consumeChatRunStream(id, runId, 0, {
      onChunk,
      onReferences,
      onToolReferences,
      onStatus,
      ctrl,
    })
  },
  resumeReplyStream: async (
    id: number,
    runId: string,
    onChunk: (text: string) => void,
    ctrl: AbortController,
    onReferences?: (items: Array<{ type: string; diaryId: string; date: string; snippet: string }>) => void,
    onToolReferences?: (items: Array<{ type: string; diaryId?: string; date: string; snippet: string; toolName: string }>) => void,
    onStatus?: (status: { stage: string; message: string }) => void,
  ): Promise<void> => {
    const storedRunId = sessionStorage.getItem(chatRunStorageKey(id))
    if (storedRunId !== runId) return
    await consumeChatRunStream(id, runId, 0, {
      onChunk,
      onReferences,
      onToolReferences,
      onStatus,
      ctrl,
    })
  },
}

const chatRunStorageKey = (conversationId: number) => `chat:active-run:${conversationId}`

type ChatRunCallbacks = {
  onChunk: (text: string) => void
  onReferences?: (items: Array<{ type: string; diaryId: string; date: string; snippet: string }>) => void
  onToolReferences?: (items: Array<{ type: string; diaryId?: string; date: string; snippet: string; toolName: string }>) => void
  onStatus?: (status: { stage: string; message: string }) => void
  ctrl: AbortController
}

async function consumeChatRunStream(
  id: number,
  runId: string,
  initialSequence: number,
  callbacks: ChatRunCallbacks,
): Promise<void> {
    const { onChunk, onReferences, onToolReferences, onStatus, ctrl } = callbacks
    const token = localStorage.getItem('token')
    let sequence = Math.max(0, initialSequence)
    let doneReceived = false
    let terminalError: Error | null = null
    let retryCount = 0
    const wait = (ms: number) => new Promise<void>(resolve => window.setTimeout(resolve, ms))

    while (!doneReceived && !ctrl.signal.aborted) {
      try {
        await fetchEventSource(`/api/chat/conversations/${id}/runs/${runId}/stream?after=${sequence}`, {
          method: 'GET',
          headers: {
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          openWhenHidden: true,
          signal: ctrl.signal,
          async onopen(response) {
            if (!response.ok) throw new Error(`SSE 连接失败（${response.status}）`)
            retryCount = 0
          },
          onmessage(event) {
            const raw = event.data
            try {
              const msg = JSON.parse(raw)
              const nextSequence = Number(msg.seq)
              if (Number.isFinite(nextSequence) && nextSequence > sequence) sequence = nextSequence
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
              } else if (msg.type === 'error') {
                terminalError = new Error(msg.message || 'AI 服务暂时无法完成本次回答')
                doneReceived = true
              }
            } catch {
              if (raw !== '[DONE]') onChunk(raw)
            }
          },
          onerror(error) {
            throw error
          },
        })
      } catch (error: any) {
        if (ctrl.signal.aborted) throw error
        if (terminalError) break
        retryCount += 1
        if (retryCount > 8) throw error
        await wait(Math.min(1000 * 2 ** (retryCount - 1), 15000))
      }

      if (!doneReceived && !ctrl.signal.aborted) {
        retryCount += 1
        if (retryCount > 8) throw new Error('SSE 连接多次中断，请稍后重试')
        await wait(Math.min(1000 * 2 ** (retryCount - 1), 15000))
      }
    }

    if (terminalError) {
      clearStoredRun(id, runId)
      throw terminalError
    }
    if (ctrl.signal.aborted) throw new DOMException('聊天流已取消', 'AbortError')
    if (doneReceived) clearStoredRun(id, runId)
}

function clearStoredRun(conversationId: number, runId: string) {
  const key = chatRunStorageKey(conversationId)
  if (sessionStorage.getItem(key) === runId) sessionStorage.removeItem(key)
}
