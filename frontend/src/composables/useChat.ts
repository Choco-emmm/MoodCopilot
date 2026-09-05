/**
 * useChat — orchestrator that composes all chat sub-composables
 * into a single API for ChatPage.vue to consume.
 */
import { ref, computed, onMounted, onBeforeUnmount, onActivated, onDeactivated, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { diaryApi, lifeEventApi, type LifeEvent } from '../api'
import { useAuthStore } from '../stores/auth'
import { logWarn } from '../utils/logger'
import { useScrollManager } from './useScrollManager'
import { useChatConversation, type Message, nextMsgId } from './useChatConversation'
import { useChatStream, type ChatReference } from './useChatStream'
import { useChatSync } from './useChatSync'
import { consumePendingChatEventContext } from '../utils/chatContext'

export function useChat() {
  const router = useRouter()
  const authStore = useAuthStore()
  const userInitial = computed(() =>
    authStore.displayName ? authStore.displayName.trim().charAt(0).toUpperCase() : '我',
  )

  // ── Scroll ──
  const msgBox = ref<HTMLElement | null>(null)
  const chatInputArea = ref<HTMLElement | null>(null)
  const scroll = useScrollManager(msgBox)

  // ── Conversation ──
  const conv = useChatConversation(scroll)

  // ── Stream ──
  const stream = useChatStream(
    conv.messages,
    conv.activeConvId,
    conv.saveToBackend,
    conv.loadConversations,
    conv.waitForConversationTitle,
    scroll,
  )

  // ── Sync ──
  const sync = useChatSync(
    conv.messages,
    conv.activeConvId,
    stream.streaming,
    conv.creatingConversation,
    conv.loadFromBackend,
    conv.loadConversations,
    scroll,
  )

  // ── Quick Starters ──
  const quickStarters = ref<{ icon: string; text: string; eventId?: number; greeting?: string }[]>([])
  const quickStartersLoading = ref(true)

  async function loadWelcomeTopics() {
    quickStartersLoading.value = true
    try {
      const { chatApi } = await import('../api')
      const res = await chatApi.getWelcomeTopics()
      if (res.data.data && Array.isArray(res.data.data) && res.data.data.length > 0) {
        quickStarters.value = res.data.data
      } else {
        throw new Error('No dynamic topics')
      }
    } catch {
      quickStarters.value = [
        { icon: '🌟', text: '分析我最近三天的情绪波动' },
        { icon: '💡', text: '帮我回顾我最近开心的事情' },
        { icon: '🌿', text: '推荐一些适合我解压的音乐与方法' },
        { icon: '💬', text: '今天有点累，陪我聊一下' },
      ]
    } finally {
      quickStartersLoading.value = false
    }
  }

  const pendingEventId = ref<number | undefined>(undefined)

  async function applyEventContext(eventId: number, supplied?: { title?: string; description?: string; targetDate?: string; endDate?: string; startTime?: string; endTime?: string }) {
    if (!Number.isFinite(eventId) || eventId <= 0) return
    let event = supplied
    if (!event?.title) {
      try {
        const response = await lifeEventApi.get(eventId)
        event = response.data.data
      } catch (e) {
        logWarn('chat', '加载事件引用失败', eventId, e)
      }
    }
    const title = event?.title || `事件 #${eventId}`
    const details = [
      event?.description,
      event?.targetDate ? `时间：${event.targetDate}${event.endDate ? ` 至 ${event.endDate}` : ''}` : '',
    ].filter(Boolean).join('\n')
    stream.references.value = stream.references.value.filter(reference => reference.eventId !== eventId)
    stream.references.value.unshift({
      label: '事件',
      displayContent: `事件 · ${title}`,
      content: details || `事件：${title}`,
      fullContent: `事件：${title}${details ? `\n${details}` : ''}`,
      eventId,
      sourceType: 'event',
    })
    pendingEventId.value = eventId
  }

  async function consumePendingEventContext() {
    const context = consumePendingChatEventContext()
    if (context) await applyEventContext(context.eventId, context)
  }

  function useQuickStarter(text: string, eventId?: number) {
    pendingEventId.value = eventId
    stream.draft.value = text
    handleSend()
  }

  // ── Diary References ──
  const recentDiaryOptions = ref<{ id: number; date: string; snippet: string; fullContent: string }[]>([])
  const recentDiariesLoading = ref(false)
  const recentDiariesError = ref<string | null>(null)

  const recentEventOptions = ref<LifeEvent[]>([])
  const recentEventsLoading = ref(false)
  const recentEventsError = ref<string | null>(null)

  async function loadRecentEventOptions() {
    recentEventsLoading.value = true
    recentEventsError.value = null
    try {
      const response = await lifeEventApi.list()
      recentEventOptions.value = (response.data.data || []).filter(event => event.status !== 'FOLLOWED_UP').slice(0, 20)
    } catch (e) {
      recentEventOptions.value = []
      recentEventsError.value = '加载重要事件失败'
      logWarn('chat', '加载引用事件失败', e)
    } finally {
      recentEventsLoading.value = false
    }
  }

  function addEventRef(eventId: string) {
    const event = recentEventOptions.value.find(item => String(item.id) === eventId)
    const id = Number(eventId)
    if (!Number.isFinite(id) || id <= 0) return
    void applyEventContext(id, event)
  }

  async function loadRecentDiaryOptions() {
    recentDiariesLoading.value = true
    recentDiariesError.value = null
    try {
      const options: typeof recentDiaryOptions.value = []
      try {
        const res = await diaryApi.mine(1, 20)
        const data = res.data.data || []
        const diaries = (Array.isArray(data) ? data : data.items ?? []) as any[]
        diaries.slice(0, 20).forEach((d: any) => {
          const plainText = (d.content ?? '').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim()
          options.push({
            id: d.id,
            date: d.createdAt?.split('T')[0] ?? '',
            snippet: plainText.length > 30 ? plainText.slice(0, 30) + '...' : plainText,
            fullContent: d.content ?? '',
          })
        })
      } catch (e) {
        recentDiariesError.value = '加载最近日记失败'
        console.warn('[chat] 加载引用日记失败', e)
      }
      recentDiaryOptions.value = options
    } catch (e) {
      recentDiaryOptions.value = []
      recentDiariesError.value = '加载最近日记失败'
      logWarn('chat', '加载最近日记选项失败', e)
    } finally {
      recentDiariesLoading.value = false
    }
  }

  async function addDiaryRef(diaryId: string) {
    const d = recentDiaryOptions.value.find(o => String(o.id) === diaryId)
    if (!d || stream.references.value.some(r => r.diaryId === d.id)) return
    let fullContent = d.fullContent
    try {
      const res = await diaryApi.get(d.id)
      fullContent = res.data.data?.content ?? fullContent
    } catch (e) {
      logWarn('chat', '获取完整日记内容失败，使用截断版本', d.id, e)
    }
    stream.references.value.push({
      label: '日记 · ' + d.date, content: d.snippet, fullContent, diaryId: d.id, sourceType: 'diary',
    })
  }

  // ── Navigation ──
  function goToDiary(diaryId: string | number | undefined) {
    if (!diaryId || String(diaryId) === '-1') return
    router.push(`/diary/${diaryId}`)
  }

  // ── Mobile ──
  const viewportBaseHeight = ref(0)

  function updateMobileKeyboardState() {
    const vv = window.visualViewport
    if (!vv) return
    const baseHeight = viewportBaseHeight.value || window.innerHeight
    document.body.classList.toggle('chat-keyboard-open', baseHeight - vv.height > 120)
  }

  function handleViewportResize() {
    updateMobileKeyboardState()
    if (document.activeElement?.tagName === 'INPUT' || document.activeElement?.tagName === 'TEXTAREA') {
      window.requestAnimationFrame(() => {
        chatInputArea.value?.scrollIntoView({ behavior: 'auto', block: 'end' })
        scroll.scrollBottom()
      })
    }
  }

  function handleDraftFocus() {
    window.requestAnimationFrame(() => {
      chatInputArea.value?.scrollIntoView({ behavior: 'auto', block: 'end' })
      scroll.scrollBottom()
    })
  }

  function handleDraftEnter(event: KeyboardEvent) {
    if ((event as any).isComposing) return
    handleSend()
  }

  function handleMobileConversationChange(event: Event) {
    const target = event.target as HTMLSelectElement
    const nextId = Number(target.value)
    if (Number.isFinite(nextId) && nextId > 0) conv.selectConversation(nextId)
  }

  function deleteActiveConversation() {
    const convId = conv.activeConvId.value
    if (!convId) return
    if (!window.confirm('确认删除当前对话吗？删除后不可恢复。')) return
    conv.deleteConversation(convId)
  }

  // ── Send wrapper (handles conversation creation) ──
  async function handleSend() {
    const eventId = pendingEventId.value
    await stream.send(conv.creatingConversation.value, async () => {
      await conv.ensureConversation()
    }, eventId)
    pendingEventId.value = undefined
  }

  // ── Lifecycle ──
  async function selectPendingConversation() {
    const pendingId = Number(sessionStorage.getItem('pendingChatConversationId'))
    if (!Number.isFinite(pendingId) || pendingId <= 0) return false
    sessionStorage.removeItem('pendingChatConversationId')
    if (conv.conversations.value.some(c => c.id === pendingId)) {
      await conv.selectConversation(pendingId)
      return true
    }
    return false
  }

  onMounted(async () => {
    if (authStore.isAuthenticated && !authStore.userId) authStore.fetchProfile()

    const state = history.state as any
    let shouldAutoSend = false
    if (state?.references?.length) {
      stream.references.value = state.references.slice(0, 2).map((r: string) => ({
        label: 'MoodCopilot 引用',
        content: String(r).length > 30 ? String(r).slice(0, 30) + '...' : String(r),
        fullContent: String(r),
      }))
      shouldAutoSend = !!state.autoSend
      if (shouldAutoSend) stream.draft.value = '来看看我最近的报告吧，我们继续聊聊'
      history.replaceState({ ...history.state, references: undefined, autoSend: undefined }, '')
    }
    if (state?.eventId) {
      void applyEventContext(Number(state.eventId))
      history.replaceState({ ...history.state, eventId: undefined }, '')
    }

    await consumePendingEventContext()

    await conv.loadConversations()
    await loadRecentDiaryOptions()
    await loadWelcomeTopics()

    const openedPendingConversation = await selectPendingConversation()
    const isNewSession = !openedPendingConversation && !sessionStorage.getItem('chatSessionInitialized')
    const storedConvId = sessionStorage.getItem('currentChatId')

    if (isNewSession) {
      sessionStorage.setItem('chatSessionInitialized', 'true')
      sessionStorage.removeItem('currentChatId')
      await conv.createConversation()
    } else if (!openedPendingConversation && storedConvId) {
      const id = Number(storedConvId)
      if (conv.conversations.value.some(c => c.id === id)) {
        await conv.selectConversation(id)
      } else {
        await conv.createConversation()
      }
    } else if (!openedPendingConversation) {
      await conv.createConversation()
    }

    if (conv.activeConvId.value) {
      await stream.resumeActiveRun(conv.activeConvId.value)
    }

    if (shouldAutoSend) { await nextTick(); handleSend() }

    if (window.visualViewport) {
      viewportBaseHeight.value = Math.max(window.visualViewport.height, window.innerHeight)
      updateMobileKeyboardState()
      window.visualViewport.addEventListener('resize', handleViewportResize)
    }

    document.addEventListener('visibilitychange', sync.handleVisibilityChange)
    window.addEventListener('focus', sync.handleWindowFocus)
    sync.startAutoSync()
  })

  onBeforeUnmount(() => {
    stream.abortStream()
    if (window.visualViewport) {
      window.visualViewport.removeEventListener('resize', handleViewportResize)
    }
    sync.cleanup()
    document.body.classList.remove('chat-keyboard-open')
  })

  // ── Keep-alive: pause/resume without killing the stream ──
  onDeactivated(() => {
    // Stop sync polling while hidden, but do NOT abort the SSE stream
    sync.stopAutoSync()
    if (window.visualViewport) {
      window.visualViewport.removeEventListener('resize', handleViewportResize)
    }
    document.body.classList.remove('chat-keyboard-open')
  })

  onActivated(async () => {
    // Resume sync polling and scroll to latest messages
    sync.startAutoSync()
    await consumePendingEventContext()
    const state = history.state as any
    if (state?.eventId) {
      void applyEventContext(Number(state.eventId))
      history.replaceState({ ...history.state, eventId: undefined }, '')
    }
    await selectPendingConversation()
    if (conv.activeConvId.value) {
      await stream.resumeActiveRun(conv.activeConvId.value)
    }
    loadRecentDiaryOptions()
    if (window.visualViewport) {
      viewportBaseHeight.value = Math.max(window.visualViewport.height, window.innerHeight)
      updateMobileKeyboardState()
      window.visualViewport.addEventListener('resize', handleViewportResize)
    }
    await nextTick()
    scroll.scrollBottom()
  })

  return {
    // auth
    authStore, userInitial,
    // conversation
    conversations: conv.conversations,
    activeConvId: conv.activeConvId,
    creatingConversation: conv.creatingConversation,
    createConversation: conv.createConversation,
    ensureConversation: conv.ensureConversation,
    selectConversation: conv.selectConversation,
    deleteConversation: conv.deleteConversation,
    handleMobileConversationChange,
    deleteActiveConversation,
    // messages
    messages: conv.messages,
    // stream
    draft: stream.draft,
    streaming: stream.streaming,
    streamingText: stream.streamingText,
    isThinking: stream.isThinking,
    isCompressing: stream.isCompressing,
    compressingMessage: stream.compressingMessage,
    useReasoning: stream.useReasoning,
    streamingRefs: stream.streamingRefs,
    lastReplyError: stream.lastReplyError,
    lastReplyRequest: stream.lastReplyRequest,
    references: stream.references,
    send: handleSend,
    retryLastReply: stream.retryLastReply,
    removeRef: stream.removeRef,
    // refs / diary
    recentDiaryOptions,
    recentDiariesLoading,
    recentDiariesError,
    addDiaryRef,
    loadRecentDiaryOptions,
    recentEventOptions,
    recentEventsLoading,
    recentEventsError,
    addEventRef,
    loadRecentEventOptions,
    // quick starters
    quickStarters,
    quickStartersLoading,
    useQuickStarter,
    // scroll
    msgBox,
    chatInputArea,
    // handlers
    handleDraftFocus,
    handleDraftEnter,
    goToDiary,
  }
}

