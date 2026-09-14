import { BASE_URL, get, post } from './request'
import { createSseParser, createUtf8Decoder } from './streamCodec'

const REASONING_MARKER = '[[REASONING]]'
const TOOL_EVENT_PREFIX = '[[TOOL_EVENT]]'

// 默认 60s 会掐断长流；这里给足并允许上层取消
const STREAM_REQUEST_TIMEOUT_MS = 300_000
const POLL_INITIAL_MS = 1500
const POLL_SLOW_MS = 3000
const POLL_SLOW_AFTER_MS = 30_000
const POLL_MAX_MS = 300_000
const MAX_RECONNECTS = 3
const RECONNECT_BACKOFF_MS = [1000, 2000, 4000]

export interface ChatStreamParams {
  conversationId: number
  message: string
  references?: string[]
  referenceItems?: Array<{ sourceType: string; sourceId: number }>
  eventId?: number
  useReasoning?: boolean
}

export interface ChatStreamCallbacks {
  onChunk: (text: string) => void
  onReasoning?: (text: string) => void
  onReferences?: (items: unknown[]) => void
  onPhase?: (phase: 'streaming' | 'polling') => void
  onDone: () => void
  onError: (error: Error) => void
}

export interface ChatStreamHandle {
  readonly runId: string | null
  cancel: () => void
}

type StreamOutcome =
  | { kind: 'done' }
  | { kind: 'error'; message: string }
  | { kind: 'unsupported' }
  | { kind: 'dropped' }

interface ChunkTask {
  abort?: () => void
  onChunkReceived?: (listener: (res: { data: ArrayBuffer | Uint8Array }) => void) => void
}

interface RunState {
  cancelled: boolean
  settled: boolean
  runId: string | null
  lastSeq: number
  reconnects: number
  task: ChunkTask | null
  pollTimer: ReturnType<typeof setTimeout> | null
}

const sleep = (ms: number) => new Promise<void>(resolve => setTimeout(resolve, ms))

function asError(error: unknown): Error {
  if (error instanceof Error) return error
  const message = (error as { message?: string } | null)?.message
  return new Error(message || '网络似乎出了点问题')
}

function newClientRequestId(): string {
  const uuid = globalThis.crypto?.randomUUID
  if (typeof uuid === 'function') return uuid.call(globalThis.crypto)
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

/**
 * 打开一个分片请求。分片能力依赖运行时的 onChunkReceived（微信基础库 2.20.2+、
 * uni-h5 的 fetch 实现），app-plus 与低版本基础库拿不到，返回 null 让上层走轮询。
 */
function openChunkedRequest(
  path: string,
  onChunk: (bytes: ArrayBuffer | Uint8Array) => void,
  onClose: () => void,
  onFail: (error: unknown) => void,
): ChunkTask | null {
  const token = uni.getStorageSync('token')
  const task = uni.request({
    url: BASE_URL + path,
    method: 'GET',
    header: token ? { Authorization: `Bearer ${token}` } : {},
    enableChunked: true,
    timeout: STREAM_REQUEST_TIMEOUT_MS,
    dataType: 'text',
    success: () => onClose(),
    fail: error => onFail(error),
  }) as unknown as ChunkTask

  if (typeof task?.onChunkReceived !== 'function') {
    try {
      task?.abort?.()
    } catch {
      // 连接可能已经关闭，忽略
    }
    return null
  }

  task.onChunkReceived(res => onChunk(res.data))
  return task
}

async function createRun(params: ChatStreamParams): Promise<string> {
  const body: Record<string, unknown> = {
    clientRequestId: newClientRequestId(),
    message: params.message,
    references: params.references ?? [],
    useReasoning: params.useReasoning === true,
  }
  if (params.eventId) body.eventId = params.eventId
  if (params.referenceItems?.length) body.referenceItems = params.referenceItems

  const res = await post<{ runId: string }>(
    `/api/chat/conversations/${params.conversationId}/runs`,
    body,
  )
  const runId = res?.code === 200 ? res.data?.runId : null
  if (!runId) throw new Error(res?.message || '生成任务创建失败')
  return String(runId)
}

async function fetchSnapshot(conversationId: number, runId: string) {
  try {
    const res = await get<{ status: string; lastSequence: number }>(
      `/api/chat/conversations/${conversationId}/runs/${encodeURIComponent(runId)}`,
    )
    return res?.code === 200 ? res.data : null
  } catch {
    return null
  }
}

/** 从历史里取最后一条非用户消息；用倒序查找兼容历史上出现过的 ai/assistant 两种角色值 */
async function fetchLastReply(conversationId: number): Promise<string> {
  try {
    const res = await get<unknown>(`/api/chat/conversations/${conversationId}/history`)
    const data = res?.data as { messages?: unknown[] } | unknown[] | null
    let list: unknown[] = []
    if (Array.isArray(data)) {
      list = data
    } else if (data && Array.isArray(data.messages)) {
      list = data.messages
    }
    for (let i = list.length - 1; i >= 0; i -= 1) {
      const item = list[i] as { role?: string; content?: unknown } | null
      if (item && item.role !== 'user' && typeof item.content === 'string' && item.content) {
        return item.content
      }
    }
    return ''
  } catch {
    return ''
  }
}

export async function startChatStream(
  params: ChatStreamParams,
  callbacks: ChatStreamCallbacks,
): Promise<ChatStreamHandle> {
  const state: RunState = {
    cancelled: false,
    settled: false,
    runId: null,
    lastSeq: 0,
    reconnects: 0,
    task: null,
    pollTimer: null,
  }

  const abortTask = () => {
    const task = state.task
    state.task = null
    if (task?.abort) {
      try {
        task.abort()
      } catch {
        // 连接可能已经关闭，忽略
      }
    }
  }

  const stopPolling = () => {
    if (state.pollTimer !== null) {
      clearTimeout(state.pollTimer)
      state.pollTimer = null
    }
  }

  const settle = (error?: Error) => {
    if (state.settled) return
    state.settled = true
    stopPolling()
    abortTask()
    if (error) callbacks.onError(error)
    else callbacks.onDone()
  }

  const isOver = () => state.cancelled || state.settled

  function dispatchFrame(raw: string): StreamOutcome | null {
    if (!raw) return null

    if (raw.startsWith(TOOL_EVENT_PREFIX)) {
      try {
        const event = JSON.parse(raw.slice(TOOL_EVENT_PREFIX.length))
        if (event?.items) callbacks.onReferences?.(event.items)
      } catch {
        // 解析不了的工具事件直接丢
      }
      return null
    }

    // 非 JSON 载荷（将来若加心跳或 [DONE] 哨兵）一律忽略，绝不塞进气泡
    if (raw[0] !== '{') return null

    let message: { type?: string; content?: unknown; items?: unknown[]; message?: unknown; seq?: unknown }
    try {
      message = JSON.parse(raw)
    } catch {
      return null
    }

    const seq = Number(message?.seq)
    if (Number.isFinite(seq) && seq > state.lastSeq) state.lastSeq = seq

    switch (message?.type) {
      case 'chunk': {
        const content = typeof message.content === 'string' ? message.content : ''
        if (!content) return null
        if (content.startsWith(REASONING_MARKER)) {
          callbacks.onReasoning?.(content.slice(REASONING_MARKER.length))
        } else {
          callbacks.onChunk(content)
        }
        return null
      }
      case 'references':
      case 'tool_references':
        if (Array.isArray(message.items)) callbacks.onReferences?.(message.items)
        return null
      case 'done':
        return { kind: 'done' }
      case 'error':
        return {
          kind: 'error',
          message: typeof message.message === 'string' && message.message
            ? message.message
            : 'AI 服务暂时无法完成本次回答',
        }
      default:
        return null
    }
  }

  function consumeStream(after: number): Promise<StreamOutcome> {
    return new Promise<StreamOutcome>(resolve => {
      let finished = false
      const finish = (outcome: StreamOutcome) => {
        if (finished) return
        finished = true
        const task = state.task
        state.task = null
        // 收到终态事件后主动断开，不必等服务端关闭
        if (task?.abort) {
          try {
            task.abort()
          } catch {
            // 连接可能已经关闭，忽略
          }
        }
        resolve(outcome)
      }

      const decoder = createUtf8Decoder()
      const parser = createSseParser(raw => {
        const outcome = dispatchFrame(raw)
        if (outcome) finish(outcome)
      })

      const path = `/api/chat/conversations/${params.conversationId}/runs/${encodeURIComponent(state.runId!)}/stream?after=${after}`

      const task = openChunkedRequest(
        path,
        bytes => {
          if (finished) return
          const text = decoder.push(bytes)
          if (text) parser.push(text)
        },
        () => {
          // 连接关闭：先吐出解码器和分帧器里的尾巴，再判为断流
          if (finished) return
          const tail = decoder.flush()
          if (tail) parser.push(tail)
          parser.flush()
          finish({ kind: 'dropped' })
        },
        () => finish({ kind: 'dropped' }),
      )

      if (!task) {
        finish({ kind: 'unsupported' })
        return
      }
      state.task = task
    })
  }

  /** 轮询兜底：拿不到增量文本，只能等 run 结束后从历史里取整段回复 */
  async function pollUntilDone(): Promise<void> {
    callbacks.onPhase?.('polling')
    const startedAt = Date.now()

    while (!isOver()) {
      const elapsed = Date.now() - startedAt
      if (elapsed > POLL_MAX_MS) {
        settle(new Error('回复超时，请稍后重试'))
        return
      }

      await sleep(elapsed < POLL_SLOW_AFTER_MS ? POLL_INITIAL_MS : POLL_SLOW_MS)
      if (isOver()) return

      const snapshot = await fetchSnapshot(params.conversationId, state.runId!)
      if (isOver()) return
      if (!snapshot) continue // 单次查询失败不致命，下一轮再试

      switch (snapshot.status) {
        case 'SUCCEEDED':
        case 'FINALIZING': {
          const text = await fetchLastReply(params.conversationId)
          if (text) callbacks.onChunk(text)
          settle()
          return
        }
        case 'FAILED':
          settle(new Error('AI 服务暂时无法完成本次回答'))
          return
        case 'CANCELLED':
          settle()
          return
        default:
          // RUNNING / 未知状态，继续等
          break
      }
    }
  }

  async function drive(): Promise<void> {
    callbacks.onPhase?.('streaming')

    while (!isOver()) {
      const seqBefore = state.lastSeq
      const outcome = await consumeStream(state.lastSeq)
      if (isOver()) return
      if (state.lastSeq > seqBefore) state.reconnects = 0 // 有进展就重置退避次数

      if (outcome.kind === 'done') {
        settle()
        return
      }
      if (outcome.kind === 'error') {
        settle(new Error(outcome.message))
        return
      }
      if (outcome.kind === 'unsupported') break

      // 断流：先查 run 状态再决定重连还是转轮询。
      // 直接用终态 seq 重连会因为没有新事件可发而挂满 30 分钟超时，所以必须先查。
      const snapshot = await fetchSnapshot(params.conversationId, state.runId!)
      if (isOver()) return

      if (snapshot) {
        if (snapshot.status === 'SUCCEEDED' || snapshot.status === 'FINALIZING') {
          const text = await fetchLastReply(params.conversationId)
          if (text) callbacks.onChunk(text)
          settle()
          return
        }
        if (snapshot.status === 'FAILED') {
          settle(new Error('AI 服务暂时无法完成本次回复'))
          return
        }
        if (snapshot.status === 'CANCELLED') {
          settle()
          return
        }
        if (snapshot.lastSequence > state.lastSeq && state.reconnects < MAX_RECONNECTS) {
          await sleep(RECONNECT_BACKOFF_MS[state.reconnects])
          if (isOver()) return
          state.reconnects += 1
          continue
        }
      }
      break
    }

    if (isOver()) return
    await pollUntilDone()
  }

  const handle: ChatStreamHandle = {
    get runId() {
      return state.runId
    },
    cancel() {
      if (state.settled) return
      state.cancelled = true
      state.settled = true
      stopPolling()
      abortTask()
      if (state.runId) {
        void post(
          `/api/chat/conversations/${params.conversationId}/runs/${encodeURIComponent(state.runId)}/cancel`,
          {},
        ).catch(() => {
          // 取消失败不影响本地状态
        })
      }
    },
  }

  // 所有失败都走 onError，调用方只需要一个错误出口
  let runId: string
  try {
    runId = await createRun(params)
  } catch (error) {
    state.settled = true
    callbacks.onError(asError(error))
    return handle
  }

  state.runId = runId
  if (isOver()) return handle

  void drive().catch(error => settle(asError(error)))
  return handle
}
