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

/** 合并类工具的源记忆；`value` 取不到时为 null（键名写错或已经删掉了）。 */
export interface PendingApprovalSource {
  attributeKey?: string
  value?: string | null
}

/**
 * 一条「执行前要用户点头」的工具调用。
 *
 * `oldValue` 为空表示新增，否则是改写 —— 弹框据此决定要不要展示旧值。
 * `kind` 与 `oldValue` 可能同时缺席（工具没给预览），这时只显示要写入的值。
 * `sources` 只有合并类工具会给，且可能不止一条 —— 它塞不进单个 `oldValue`。
 */
export interface PendingApprovalItem {
  toolCallId: string
  toolName: string
  attributeKey?: string
  oldValue?: string
  newValue?: string
  kind?: string
  sources?: PendingApprovalSource[]
}

/** 对某一次工具调用的表态。 */
export interface ApprovalDecision {
  toolCallId: string
  approved: boolean
  reason?: string
}

export const chatApi = {
  listConversations: () => api.get('/chat/conversations'),
  createConversation: (title?: string) => api.post('/chat/conversations', { title: title || '' }),
  updateConversationTitle: (id: number, title: string) => api.put(`/chat/conversations/${id}/title`, { title }),
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
    api.post(`/chat/conversations/${id}/reply`, {
      message, references, useReasoning,
      ...(eventId ? { eventId } : {}), ...(referencePurpose ? { referencePurpose } : {}),
      ...(referenceItems?.length ? { referenceItems } : {})
    }),
  compressConversation: (id: number) =>
    api.post<{ compressed: boolean; message: string; summary?: string }>(`/chat/conversations/${id}/compress`),
  getRunStatus: (conversationId: number, runId: string) =>
    api.get(`/chat/conversations/${conversationId}/runs/${encodeURIComponent(runId)}`),
  /**
   * 对「执行前要点头」的工具调用表态，**逐条**。
   *
   * 一批里可能有好几组待批准（例如一次合并好几组记忆），用户会分页逐条选。没提交上来的那些
   * 后端一律按拒绝处理，所以这里只发用户真表过态的 —— 漏发不会变成误放行。
   *
   * 后端从检查点续跑，续写的分片仍进同一条 run 的事件流，调用方不要另开一轮 —— 原本那条 SSE 会自己接上。
   */
  approveRun: (conversationId: number, runId: string, decisions: ApprovalDecision[]) =>
    api.post(`/chat/conversations/${conversationId}/runs/${encodeURIComponent(runId)}/approve`, {
      decisions,
    }),
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
    imageUrls: string[],
    onChunk: (text: string) => void,
    ctrl: AbortController,
    onReferences?: (items: Array<{ type: string; diaryId: string; date: string; snippet: string }>) => void,
    onToolReferences?: (items: Array<{ type: string; diaryId?: string; date: string; snippet: string; toolName: string }>) => void,
    onStatus?: (status: { stage: string; message: string }) => void,
    referencePurpose?: string,
    referenceItems?: Array<{ sourceType: string; sourceId: number; referencePurpose?: string }>,
    onApproval?: (items: PendingApprovalItem[], runId: string) => void,
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
      ...(imageUrls?.length ? { imageUrls } : {}),
      // 这个客户端能弹审批框，也就能按 runId 回批
      approvalsInteractive: true,
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
      onApproval,
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
    onApproval?: (items: PendingApprovalItem[], runId: string) => void,
  ): Promise<void> => {
    const storedRunId = sessionStorage.getItem(chatRunStorageKey(id))
    if (storedRunId !== runId) return
    await consumeChatRunStream(id, runId, 0, {
      onChunk,
      onReferences,
      onToolReferences,
      onStatus,
      onApproval,
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
  /** 带上 runId：批准这条请求要用它，而调用方（发起本轮的那处）并不知道自己拿到的是哪个 run。 */
  onApproval?: (items: PendingApprovalItem[], runId: string) => void
  ctrl: AbortController
}

async function consumeChatRunStream(
  id: number,
  runId: string,
  initialSequence: number,
  callbacks: ChatRunCallbacks,
): Promise<void> {
  const { onChunk, onReferences, onToolReferences, onStatus, onApproval, ctrl } = callbacks
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
            } else if (msg.type === 'approval_required') {
              // 不是终态：后端只是停在工具执行前等用户点头，连接会一直挂着，
              // 用户决定之后的分片仍从这条流下来。所以这里既不能结束消费、也不能清 run 键。
              onApproval?.(Array.isArray(msg.items) ? msg.items : [], runId)
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
  if (ctrl.signal.aborted) return
  if (doneReceived) clearStoredRun(id, runId)
}

function clearStoredRun(conversationId: number, runId: string) {
  const key = chatRunStorageKey(conversationId)
  if (sessionStorage.getItem(key) === runId) sessionStorage.removeItem(key)
}
