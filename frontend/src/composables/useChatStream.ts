import { ref, type Ref } from 'vue'
import { chatApi } from '../api'
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
  sourceType?: 'diary' | 'event'
  type?: 'quote'
  quoteAuthor?: string
  displayContent?: string
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
  const streamingText = ref('')
  const isThinking = ref(false)
  const isCompressing = ref(false)
  const compressingMessage = ref('正在优化对话上下文...')
  const useReasoning = ref(false)
  const references = ref<ChatReference[]>([])
  const lastReplyError = ref<string | null>(null)
  const lastReplyRequest = ref<{ convId: number; content: string; refContents: string[]; useReasoning: boolean; eventId?: number } | null>(null)
  const streamingRefs = ref<RagRef[]>([])
  const showStreamingRefs = ref(false)

  let pendingStreamText = ''
  let streamRafId: number | null = null
  let streamAbortCtrl: AbortController | null = null
  let syncCooldownUntil = 0

  // ── Send ──

  async function send(creatingConversation: boolean, doCreate: () => Promise<void>, eventId?: number) {
    const content = draft.value.trim()
    if (!content || streaming.value || creatingConversation) return

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

    const refContents = references.value.filter(r => r.type !== 'quote').slice(0, 2).map(r => r.fullContent || r.content)
    messages.value.push({
      id: nextMsgId(), role: 'user', content,
      createdAt: new Date().toISOString(),
      references: refContents.length ? refContents : undefined,
      quoteRef,
    })
    saveToBackend(convId).catch(() => {})
    references.value = []
    draft.value = ''
    tryExpToast('chat', '聊天 +5 EXP')
    streaming.value = true
    streamingText.value = ''
    isThinking.value = true
    scrollManager.scrollBottom()

    const token = getStoredToken()
    if (!token) {
      isThinking.value = false
      messages.value.push({ id: nextMsgId(), role: 'ai', content: '请先登录' })
      streaming.value = false
      return
    }

    await sendReply(convId, finalContent, refContents, useReasoning.value, false, eventId, isFirstUserMessage)
  }

  // ── Retry ──

  async function retryLastReply() {
    if (!lastReplyRequest.value || streaming.value) return
    const { convId, content, refContents, useReasoning: requestedUseReasoning, eventId } = lastReplyRequest.value
    if (activeConvId.value !== convId) {
      lastReplyError.value = '会话已切换，请在当前会话重新发送。'
      return
    }
    streaming.value = true
    streamingText.value = ''
    isThinking.value = true
    await sendReply(convId, content, refContents, requestedUseReasoning, true, eventId)
  }

  // ── Stream Reply ──

  async function sendReply(convId: number, content: string, refContents: string[], requestedUseReasoning: boolean, isRetry: boolean, eventId?: number, refreshTitle = false) {
    if (streamAbortCtrl) {
      streamAbortCtrl.abort()
      streamAbortCtrl = null
    }
    const ctrl = new AbortController()
    streamAbortCtrl = ctrl

    streamingRefs.value = []
    showStreamingRefs.value = false
    let fullReply = ''
    let currentRefs: RagRef[] = []

    try {
      await chatApi.replyStream(
        convId,
        content,
        refContents,
        requestedUseReasoning,
        eventId,
        (chunk: string) => {
          fullReply += chunk
          pendingStreamText = fullReply
          if (isCompressing.value) isCompressing.value = false
          if (isThinking.value) isThinking.value = false
          if (streamRafId === null) {
            streamRafId = requestAnimationFrame(() => {
              const keepScroll = scrollManager.isNearBottom()
              streamingText.value = pendingStreamText
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
        (status: { stage: string; message: string }) => {
          if (status.stage === 'compressing') {
            isCompressing.value = true
            compressingMessage.value = status.message || '正在优化对话上下文...'
          } else if (status.stage === 'thinking') {
            isCompressing.value = false
            isThinking.value = true
          }
          scrollManager.scrollBottom()
        },
      )

      if (activeConvId.value !== convId) return
      lastReplyError.value = null
      lastReplyRequest.value = null
      messages.value.push({
        id: nextMsgId(), role: 'ai',
        content: fullReply || '我刚才没有组织好语言，你可以再说一遍吗？',
        createdAt: new Date().toISOString(),
        ragReferences: currentRefs.length ? currentRefs : undefined,
      })
    } catch (e: any) {
      isCompressing.value = false
      isThinking.value = false
      const bizMessage = e?.response?.data?.message || e?.message
      const errorText = chatErrorMessage(e?.status, bizMessage, requestedUseReasoning)
      if (activeConvId.value === convId) {
        lastReplyError.value = errorText
        lastReplyRequest.value = { convId, content, refContents, useReasoning: requestedUseReasoning, eventId }
        if (!isRetry) {
          messages.value.push({ id: nextMsgId(), role: 'ai', content: errorText })
        }
      }
    } finally {
      isCompressing.value = false
      streamAbortCtrl = null
      await finishSend(convId, refreshTitle)
    }
  }

  // ── Finish ──

  async function finishSend(convId: number, refreshTitle = false) {
    if (streamRafId !== null) {
      cancelAnimationFrame(streamRafId)
      streamRafId = null
    }
    streaming.value = false
    streamingText.value = ''
    streamingRefs.value = []
    isThinking.value = false
    references.value = []
    scrollManager.scrollBottom()
    try {
      await saveToBackend(convId)
    } catch (e) {
      logWarn('chat', '发送后保存历史失败', e)
      syncCooldownUntil = Date.now() + 5000
    }
    loadConversations()
    if (refreshTitle) void waitForConversationTitle(convId)
  }

  function abortStream() {
    isCompressing.value = false
    if (streamRafId !== null) {
      cancelAnimationFrame(streamRafId)
      streamRafId = null
    }
    if (streamAbortCtrl) {
      streamAbortCtrl.abort()
      streamAbortCtrl = null
    }
  }

  // ── Error Message ──

  function chatErrorMessage(status?: number, bizMessage?: string, requestedUseReasoning = false): string {
    if (status === 429 && requestedUseReasoning) return '深度思考额度已用完，请改用普通对话或明日再试。'
    if (status === 429) return '普通对话额度已用完，请明日再试。'
    if (status === 503) return 'AI 服务暂时不可用，请稍后重试。'
    if (bizMessage) return `发送失败：${bizMessage}`
    return '消息发送失败，请检查网络后重试。'
  }

  // ── References ──

  function removeRef(index: number) {
    references.value.splice(index, 1)
  }

  return {
    draft, streaming, streamingText, isThinking, isCompressing, compressingMessage, useReasoning, references,
    lastReplyError, lastReplyRequest, streamingRefs, showStreamingRefs,
    syncCooldownUntil,
    send, retryLastReply, abortStream, removeRef,
  }
}
