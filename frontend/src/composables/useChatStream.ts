import { computed, ref, watch, type Ref } from 'vue'
import { chatApi, type ApprovalDecision, type PendingApprovalItem } from '../api'
import { tryExpToast } from '../utils/toast'
import { logWarn } from '../utils/logger'
import { getStoredToken } from '../utils/auth'
import { useScrollManager } from './useScrollManager'
import { type Message, type RagRef, nextMsgId } from './useChatConversation'

export interface ChatReference {
  label: string
  content: string
  fullContent: string
  diaryId?: number
  eventId?: number
  sourceType?: 'diary' | 'event' | 'image'
  url?: string
  type?: 'quote' | 'image'
  quoteAuthor?: string
  displayContent?: string
}

const REASONING_MARKER = '[[REASONING]]'

function normalizeReasoningChunk(chunk: string, carry: { value: string }): string {
  const combined = carry.value + chunk
  carry.value = ''
  for (let length = Math.min(REASONING_MARKER.length - 1, combined.length); length > 0; length -= 1) {
    if (combined.endsWith(REASONING_MARKER.slice(0, length))) {
      carry.value = combined.slice(-length)
      return combined.slice(0, -length)
    }
  }
  return combined
}

export function useChatStream(
  messages: Ref<Message[]>,
  activeConvId: Ref<number | null>,
  saveToBackend: (convId: number) => Promise<any>,
  loadConversations: () => Promise<void>,
  waitForConversationTitle: (convId: number) => Promise<void>,
  scrollManager: ReturnType<typeof useScrollManager>,
) {
  const draft = ref('')
  const streaming = ref(false)
  const sendGuard = ref(false)
  const streamingText = ref('')
  const streamingReasoning = ref('')
  const isThinking = ref(false)
  const isCompressing = ref(false)
  const compressingMessage = ref('正在优化对话上下文...')
  const compressingSubtip = ref('')
  const useReasoning = ref(false)
  const references = ref<ChatReference[]>([])
  const lastReplyError = ref<string | null>(null)
  const lastReplyRequest = ref<{ convId: number; content: string; refContents: string[]; referenceItems: Array<{ sourceType: string; sourceId: number }>; useReasoning: boolean; eventId?: number } | null>(null)
  const streamingRefs = ref<RagRef[]>([])
  const showStreamingRefs = ref(false)
  // 这一轮停在工具执行前，等用户点头。内容为空表示没有在等。
  const pendingApprovals = ref<PendingApprovalItem[]>([])
  const awaitingApproval = computed(() => pendingApprovals.value.length > 0)
  const approvalSubmitting = ref(false)
  const approvalError = ref<string | null>(null)
  let approvalRunId: string | null = null

  let pendingStreamText = ''
  let streamRafId: number | null = null
  let streamAbortCtrl: AbortController | null = null
  let syncCooldownUntil = 0
  let resumePromise: Promise<void> | null = null

  // ── Approval ──

  /** 后端停在工具执行前等用户点头；`runId` 是批准时要回传的那一个。 */
  function receiveApproval(items: PendingApprovalItem[], runId: string) {
    // 断线重连会重放同一帧，直接覆盖即可 —— 该显示的就是最新那批
    pendingApprovals.value = items
    approvalRunId = runId
    approvalError.value = null
  }

  /**
   * 提交用户的决定，**逐条**。
   * <p>
   * 这里不另起一轮：后端从检查点续跑，续写的分片仍从原来那条 SSE 回来，
   * 所以收掉弹框就够了。切页/断线的情况由 {@link resumeActiveRun} 重放恢复。
   * <p>
   * 调用方只传真表过态的那些；没表过态的后端按拒绝处理，所以漏传不会变成误放行。
   */
  async function resolveApproval(decisions: ApprovalDecision[]) {
    const convId = activeConvId.value
    const runId = approvalRunId
    if (!convId || !runId || approvalSubmitting.value) return
    approvalSubmitting.value = true
    approvalError.value = null
    try {
      await chatApi.approveRun(convId, runId, decisions.map((decision) => ({
        toolCallId: decision.toolCallId,
        approved: decision.approved,
        ...(decision.reason?.trim() ? { reason: decision.reason.trim() } : {}),
      })))
      pendingApprovals.value = []
      approvalRunId = null
    } catch (e: any) {
      // 409 表示这条 run 已经不在等确认了（多半是别处已经表过态）：静默收掉弹框，
      // 后面的分片会自己接上来，没必要让用户对着一个已经解决的确认框报错。
      if (e?.response?.status === 409) {
        pendingApprovals.value = []
        approvalRunId = null
        return
      }
      approvalError.value = e?.response?.data?.message || '没能提交这次确认，请检查网络后重试。'
    } finally {
      approvalSubmitting.value = false
    }
  }

  // ── Send ──

  async function send(creatingConversation: boolean, doCreate: () => Promise<void>, eventId?: number) {
    const content = draft.value.trim()
    if (!content || streaming.value || sendGuard.value || creatingConversation) return

    if (!activeConvId.value) {
      await doCreate()
    }

    const convId = activeConvId.value
    if (!convId) return
    const isFirstUserMessage = !messages.value.some(message => message.role === 'user')

    lastReplyError.value = null
    lastReplyRequest.value = null

    let finalContent = content
    let quoteRef: { content: string; author: string } | undefined
    const quoteRefItem = references.value.find(r => r.type === 'quote')
    if (quoteRefItem) {
      const author = quoteRefItem.quoteAuthor || 'AI'
      quoteRef = { content: quoteRefItem.content, author }
      finalContent = `[用户引用了之前的发言：\n"${quoteRefItem.content}"]\n\n用户的回复是：\n${content}`
    }

    const selectedReferences = references.value.filter(r => r.type !== 'quote' && r.type !== 'image').slice(0, 2)
    const refContents = selectedReferences.map(r => r.fullContent || r.content)
    const imageUrls = references.value.filter(r => r.type === 'image' && r.url).map(r => r.url as string)
    const referenceItems = selectedReferences
      .filter(r => (r.sourceType === 'diary' || r.sourceType === 'event') && Number.isFinite(r.diaryId || r.eventId))
      .map(r => ({ sourceType: r.sourceType as string, sourceId: Number(r.diaryId || r.eventId) }))
    messages.value.push({
      id: nextMsgId(), role: 'user', content,
      createdAt: new Date().toISOString(),
      references: refContents.length ? refContents : undefined,
      imageUrls: imageUrls.length ? imageUrls : undefined,
      quoteRef,
    })
    await saveToBackend(convId).catch(() => { })
    references.value = []
    draft.value = ''
    tryExpToast('chat', '聊天 +5 EXP')
    sendGuard.value = true
    streaming.value = true
    streamingText.value = ''
    streamingReasoning.value = ''
    isThinking.value = true
    scrollManager.scrollBottom()

    const token = getStoredToken()
    if (!token) {
      isThinking.value = false
      messages.value.push({ id: nextMsgId(), role: 'ai', content: '请先登录' })
      streaming.value = false
      return
    }

    await sendReply(convId, finalContent, refContents, referenceItems, useReasoning.value, false, eventId, isFirstUserMessage, imageUrls)
  }

  // ── Retry ──

  async function retryLastReply() {
    if (!lastReplyRequest.value || streaming.value) return
    const { convId, content, refContents, referenceItems, useReasoning: requestedUseReasoning, eventId } = lastReplyRequest.value
    if (activeConvId.value !== convId) {
      lastReplyError.value = '会话已切换，请在当前会话重新发送。'
      return
    }
    // The first failed attempt is represented by a temporary assistant error
    // message. Remove it before retrying so the new answer remains directly
    // attached to the original user message and its quote/reference card.
    const lastMessage = messages.value[messages.value.length - 1]
    if (lastMessage?.role === 'ai' && lastReplyError.value && lastMessage.content === lastReplyError.value) {
      messages.value.pop()
    }
    streaming.value = true
    streamingText.value = ''
    streamingReasoning.value = ''
    isThinking.value = true
    await sendReply(convId, content, refContents, referenceItems, requestedUseReasoning, true, eventId)
  }

  // ── Refresh recovery ──

  async function resumeActiveRun(convId: number): Promise<void> {
    if (!convId || streaming.value || resumePromise) return
    const runId = chatApi.getActiveRun(convId)
    if (!runId) return
    if (messages.value.some(message => message.id === `${runId}:assistant`)) {
      chatApi.clearActiveRun(convId, runId)
      return
    }
    // 事件是从 0 号重放的，「待批准」那一帧还在里面。只有当前状态确实停在等确认时，
    // 它才该弹出来 —— 否则用户批准后每刷新一次，都会看到一个早就解决了的确认框。
    let awaitingApproval = false
    try {
      const status = await chatApi.getRunStatus(convId, runId)
      awaitingApproval = (status.data?.data ?? status.data)?.status === 'AWAITING_APPROVAL'
    } catch (error: any) {
      if (error?.response?.status === 404 || error?.status === 404) {
        chatApi.clearActiveRun(convId, runId)
        return
      }
      // 状态查询的临时失败不应丢弃 runId，后续 SSE 仍可自行重试。
    }

    const ctrl = new AbortController()
    streamAbortCtrl = ctrl
    streaming.value = true
    streamingText.value = ''
    streamingReasoning.value = ''
    isThinking.value = true
    streamingRefs.value = []
    showStreamingRefs.value = false

    resumePromise = (async () => {
      let fullReply = ''; let fullReasoning = ''
      const reasoningCarry = { value: '' }
      let currentRefs: RagRef[] = []
      try {
        await chatApi.resumeReplyStream(
          convId,
          runId,
          (chunk: string) => {
            const normalizedChunk = normalizeReasoningChunk(chunk, reasoningCarry)
            if (normalizedChunk.startsWith(REASONING_MARKER)) {
              fullReasoning += normalizedChunk.substring(REASONING_MARKER.length)
            } else if (normalizedChunk.includes(REASONING_MARKER)) {
              fullReasoning += normalizedChunk.replace(/\[\[REASONING\]\]/g, '')
            } else {
              fullReply += normalizedChunk
            }
            pendingStreamText = fullReply
            if (isThinking.value && normalizedChunk && !normalizedChunk.startsWith(REASONING_MARKER)) isThinking.value = false
            if (streamRafId === null) {
              streamRafId = requestAnimationFrame(() => {
                const keepScroll = scrollManager.isNearBottom()
                streamingText.value = pendingStreamText
                streamingReasoning.value = fullReasoning
                streamRafId = null
                if (keepScroll) scrollManager.scrollBottom()
              })
            }
          },
          ctrl,
          (items: any) => {
            currentRefs = items
            streamingRefs.value = items
          },
          (toolItems: any) => {
            currentRefs = [...currentRefs, ...toolItems]
            streamingRefs.value = currentRefs
          },
          applyStatus,
          (items: PendingApprovalItem[], approvalRun: string) => {
            if (awaitingApproval) receiveApproval(items, approvalRun)
          },
        )

        if (activeConvId.value === convId && !messages.value.some(message => message.id === `${runId}:assistant`)) {
          messages.value.push({
            id: `${runId}:assistant`,
            role: 'ai',
            content: fullReply || (fullReasoning ? '' : '我刚才没有组织好语言，你可以再说一遍吗？'),
            reasoningContent: fullReasoning || undefined,
            createdAt: new Date().toISOString(),
            ragReferences: currentRefs.length ? currentRefs : undefined,
          })
        }
        lastReplyError.value = null
        lastReplyRequest.value = null
      } catch (e: any) {
        const isAbort = e?.name === 'AbortError' || /AbortError|聊天流已取消/.test(String(e?.message || ''))
        if (!isAbort && activeConvId.value === convId) {
          lastReplyError.value = e?.message || '恢复聊天生成失败，请稍后重试。'
        }
      } finally {
        if (streamAbortCtrl === ctrl) {
          isCompressing.value = false
          streamAbortCtrl = null
          await finishSend(convId)
        }
      }
    })()

    try {
      await resumePromise
    } finally {
      resumePromise = null
    }
  }

  // ── Stream Reply ──

  /**
   * 后台在干什么。stage 由后端 status 帧给出：
   * compressing 是上下文压缩，reading_images 是图片识别（OCR 一张带文字的图可能要几十秒）。
   * 副标题只在压缩阶段有意义，别的阶段留空，免得显示成「正在精炼长对话记忆」。
   */
  function applyStatus(status: { stage: string; message: string }) {
    if (status.stage === 'compressing') {
      isCompressing.value = true
      compressingMessage.value = status.message || '正在优化对话上下文...'
      compressingSubtip.value = '正在精炼长对话记忆，优化后将继续回复'
    } else if (status.stage === 'reading_images') {
      isCompressing.value = true
      compressingMessage.value = status.message || '正在识别图片内容…'
      compressingSubtip.value = ''
    } else if (status.stage === 'thinking') {
      isCompressing.value = false
      isThinking.value = true
    }
    scrollManager.scrollBottom()
  }

  async function sendReply(convId: number, content: string, refContents: string[], referenceItems: Array<{ sourceType: string; sourceId: number }>, requestedUseReasoning: boolean, isRetry: boolean, eventId?: number, refreshTitle = false, imageUrls: string[] = []) {
    if (streamAbortCtrl) {
      streamAbortCtrl.abort()
      streamAbortCtrl = null
    }
    const ctrl = new AbortController()
    streamAbortCtrl = ctrl

    streamingRefs.value = []
    showStreamingRefs.value = false
    let fullReply = ''; let fullReasoning = ''
    const reasoningCarry = { value: '' }
    let currentRefs: RagRef[] = []

    try {
      await chatApi.replyStream(
        convId,
        content,
        refContents,
        requestedUseReasoning,
        eventId,
        imageUrls,
        (chunk: string) => {
          const normalizedChunk = normalizeReasoningChunk(chunk, reasoningCarry)
          if (normalizedChunk.startsWith(REASONING_MARKER)) {
            fullReasoning += normalizedChunk.substring(REASONING_MARKER.length)
          } else if (normalizedChunk.includes(REASONING_MARKER)) {
            fullReasoning += normalizedChunk.replace(/\[\[REASONING\]\]/g, '')
          } else {
            fullReply += normalizedChunk
          }
          pendingStreamText = fullReply
          if (isCompressing.value) isCompressing.value = false
          if (isThinking.value && normalizedChunk && !normalizedChunk.startsWith(REASONING_MARKER)) isThinking.value = false
          if (streamRafId === null) {
            streamRafId = requestAnimationFrame(() => {
              const keepScroll = scrollManager.isNearBottom()
              streamingText.value = pendingStreamText
              streamingReasoning.value = fullReasoning
              streamRafId = null
              if (keepScroll) scrollManager.scrollBottom()
            })
          }
        },
        ctrl,
        (items: any) => {
          if (isCompressing.value) isCompressing.value = false
          currentRefs = items
          streamingRefs.value = items
        },
        (toolItems: any) => {
          currentRefs = [...currentRefs, ...toolItems]
          streamingRefs.value = currentRefs
        },
        applyStatus,
        undefined,
        referenceItems,
        receiveApproval,
      )

      if (activeConvId.value !== convId) return
      lastReplyError.value = null
      lastReplyRequest.value = null
      messages.value.push({
        id: nextMsgId(), role: 'ai',
        content: fullReply || (fullReasoning ? '' : '我刚才没有组织好语言，你可以再说一遍吗？'),
        reasoningContent: fullReasoning || undefined,
        createdAt: new Date().toISOString(),
        ragReferences: currentRefs.length ? currentRefs : undefined,
      })
    } catch (e: any) {
      const isAbort = e?.name === 'AbortError' || /AbortError|聊天流已取消/.test(String(e?.message || ''))
      isCompressing.value = false
      isThinking.value = false
      if (isAbort) {
        lastReplyError.value = null
        lastReplyRequest.value = null
        return
      }
      const bizMessage = e?.response?.data?.message || e?.message
      const errorText = chatErrorMessage(e?.status, bizMessage, requestedUseReasoning)
      if (activeConvId.value === convId) {
        lastReplyError.value = errorText
        lastReplyRequest.value = { convId, content, refContents, referenceItems, useReasoning: requestedUseReasoning, eventId }
        if (!isRetry) {
          messages.value.push({ id: nextMsgId(), role: 'ai', content: errorText })
        }
      }
    } finally {
      if (streamAbortCtrl === ctrl) {
        isCompressing.value = false
        streamAbortCtrl = null
        await finishSend(convId, refreshTitle)
      }
    }
  }

  // ── Finish ──

  async function finishSend(convId: number, refreshTitle = false) {
    if (streamRafId !== null) {
      cancelAnimationFrame(streamRafId)
      streamRafId = null
    }
    streaming.value = false
    sendGuard.value = false
    streamingText.value = ''
    streamingRefs.value = []
    isThinking.value = false
    references.value = []
    // 这一轮已经收尾（生成完、失败或取消），不再有悬着的确认
    pendingApprovals.value = []
    approvalRunId = null
    approvalError.value = null
    scrollManager.scrollBottom()
    if (activeConvId.value === convId) {
      try {
        await saveToBackend(convId)
      } catch (e) {
        logWarn('chat', '发送后保存历史失败', e)
        syncCooldownUntil = Date.now() + 5000
      }
    }
    loadConversations()
    if (refreshTitle) void waitForConversationTitle(convId)
  }

  function abortStream() {
    isCompressing.value = false
    lastReplyError.value = null
    lastReplyRequest.value = null
    sendGuard.value = false
    streaming.value = false
    isThinking.value = false
    streamingText.value = ''
    streamingReasoning.value = ''
    streamingRefs.value = []
    if (streamRafId !== null) {
      cancelAnimationFrame(streamRafId)
      streamRafId = null
    }
    if (streamAbortCtrl) {
      streamAbortCtrl.abort()
      streamAbortCtrl = null
    }
  }

  watch(activeConvId, (newId, oldId) => {
    if (newId !== oldId) {
      abortStream()
    }
  })

  // ── Error Message ──

  function chatErrorMessage(status?: number, bizMessage?: string, requestedUseReasoning = false): string {
    if (status === 429 && bizMessage) return bizMessage
    if (status === 429 && requestedUseReasoning) return '聊天 Pro 额度已用完，请改用聊天 Flash 或明日再试。'
    if (status === 429) return '聊天 Flash 额度已用完，请明日再试。'
    if (status === 503) return 'AI 服务暂时不可用，请稍后重试。'
    if (bizMessage) return `发送失败：${bizMessage}`
    return '消息发送失败，请检查网络后重试。'
  }

  // ── References ──


  function addImageRef(url: string) {
    references.value.push({
      label: '图片',
      displayContent: '已选图片附件',
      content: '【图片】',
      fullContent: url,
      sourceType: 'image',
      type: 'image',
      url: url,
    })
  }

  function removeRef(index: number) {
    references.value.splice(index, 1)
  }

  return {
    draft, streaming, streamingText, streamingReasoning, isThinking, isCompressing, compressingMessage, compressingSubtip, useReasoning, references,
    lastReplyError, lastReplyRequest, streamingRefs, showStreamingRefs,
    syncCooldownUntil,
    pendingApprovals, awaitingApproval, approvalSubmitting, approvalError, resolveApproval,
    send, retryLastReply, resumeActiveRun, abortStream, removeRef, addImageRef,
  }
}
