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
}

export function useChatStream(
  messages: Ref<Message[]>,
  activeConvId: Ref<number | null>,
  saveToBackend: (convId: number) => Promise<any>,
  loadConversations: () => Promise<void>,
  scrollManager: ReturnType<typeof useScrollManager>,
) {
  const draft = ref('')
  const streaming = ref(false)
  const streamingText = ref('')
  const isThinking = ref(false)
  const references = ref<ChatReference[]>([])
  const lastReplyError = ref<string | null>(null)
  const lastReplyRequest = ref<{ convId: number; content: string; refContents: string[] } | null>(null)
  const streamingRefs = ref<RagRef[]>([])
  const showStreamingRefs = ref(false)

  let pendingStreamText = ''
  let streamRafId: number | null = null
  let streamAbortCtrl: AbortController | null = null
  let syncCooldownUntil = 0

  // ── Send ──

  async function send(creatingConversation: boolean, doCreate: () => Promise<void>) {
    const content = draft.value.trim()
    if (!content || streaming.value || creatingConversation) return

    if (!activeConvId.value) {
      await doCreate()
    }

    const convId = activeConvId.value
    if (!convId) return

    lastReplyError.value = null
    lastReplyRequest.value = null

    const refContents = references.value.slice(0, 2).map(r => r.fullContent || r.content)
    messages.value.push({
      id: nextMsgId(), role: 'user', content,
      references: refContents.length ? refContents : undefined,
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

    await sendReply(convId, content, refContents, false)
  }

  // ── Retry ──

  async function retryLastReply() {
    if (!lastReplyRequest.value || streaming.value) return
    const { convId, content, refContents } = lastReplyRequest.value
    if (activeConvId.value !== convId) {
      lastReplyError.value = '会话已切换，请在当前会话重新发送。'
      return
    }
    streaming.value = true
    streamingText.value = ''
    isThinking.value = true
    await sendReply(convId, content, refContents, true)
  }

  // ── Stream Reply ──

  async function sendReply(convId: number, content: string, refContents: string[], isRetry: boolean) {
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
      await chatApi.replyStream(convId, content, refContents, (chunk: string) => {
        fullReply += chunk
        pendingStreamText = fullReply
        if (isThinking.value) isThinking.value = false
        if (streamRafId === null) {
          streamRafId = requestAnimationFrame(() => {
            const keepScroll = scrollManager.isNearBottom()
            streamingText.value = pendingStreamText
            streamRafId = null
            if (keepScroll) scrollManager.scrollBottom()
          })
        }
      }, ctrl, (items: any) => {
        currentRefs = items
        streamingRefs.value = items
      }, (toolItems: any) => {
        currentRefs = [...currentRefs, ...toolItems]
        streamingRefs.value = currentRefs
      })

      if (activeConvId.value !== convId) return
      lastReplyError.value = null
      lastReplyRequest.value = null
      messages.value.push({
        id: nextMsgId(), role: 'ai',
        content: fullReply || '我刚才没有组织好语言，你可以再说一遍吗？',
        ragReferences: currentRefs.length ? currentRefs : undefined,
      })
    } catch (e: any) {
      isThinking.value = false
      const bizMessage = e?.response?.data?.message || e?.message
      const errorText = chatErrorMessage(e?.status, bizMessage)
      if (activeConvId.value === convId) {
        lastReplyError.value = errorText
        lastReplyRequest.value = { convId, content, refContents }
        if (!isRetry) {
          messages.value.push({ id: nextMsgId(), role: 'ai', content: errorText })
        }
      }
    } finally {
      streamAbortCtrl = null
      await finishSend(convId)
    }
  }

  // ── Finish ──

  async function finishSend(convId: number) {
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
  }

  function abortStream() {
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

  function chatErrorMessage(status?: number, bizMessage?: string): string {
    if (status === 429) return '请求太频繁，请稍后再试。'
    if (status === 503) return 'AI 服务暂时不可用，请稍后重试。'
    if (bizMessage) return `发送失败：${bizMessage}`
    return '消息发送失败，请检查网络后重试。'
  }

  // ── References ──

  function removeRef(index: number) {
    references.value.splice(index, 1)
  }

  return {
    draft, streaming, streamingText, isThinking, references,
    lastReplyError, lastReplyRequest, streamingRefs, showStreamingRefs,
    syncCooldownUntil,
    send, retryLastReply, abortStream, removeRef,
  }
}
