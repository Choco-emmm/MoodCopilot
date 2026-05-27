import { ref, nextTick } from 'vue'
import { chatApi } from '../api'
import { logWarn } from '../utils/logger'
import { useScrollManager } from './useScrollManager'

export interface Message {
  id: string
  role: 'user' | 'ai'
  content: string
  references?: string[]
  ragReferences?: RagRef[]
  quoteRef?: { content: string; author: string }
}

export interface RagRef {
  type: string
  diaryId?: string
  date?: string
  snippet?: string
  toolName?: string
  value?: string
  key?: string
}

export interface Conversation {
  id: number
  title: string
}

let msgIdCounter = 0

export function nextMsgId(): string {
  return `${Date.now()}-${++msgIdCounter}-${Math.random().toString(36).slice(2, 6)}`
}

export function useChatConversation(scrollContainerRef: ReturnType<typeof useScrollManager>) {
  const conversations = ref<Conversation[]>([])
  const activeConvId = ref<number | null>(null)
  const messages = ref<Message[]>([])
  const creatingConversation = ref(false)

  // ── Load / Select ──

  async function loadConversations() {
    try {
      const res = await chatApi.listConversations()
      conversations.value = (res.data.data || []) as Conversation[]
      const currentId = activeConvId.value
      if (currentId && !conversations.value.some(conv => conv.id === currentId)) {
        if (conversations.value.length > 0) {
          await selectConversation(conversations.value[0].id)
        } else {
          activeConvId.value = null
        }
      }
    } catch (e) { logWarn('chat', '加载会话列表失败', e); conversations.value = [] }
  }

  async function selectConversation(id: number) {
    if (id === activeConvId.value) return
    if (activeConvId.value && messages.value.length > 0) {
      await saveToBackend(activeConvId.value).catch(() => {})
    }
    activeConvId.value = id
    sessionStorage.setItem('currentChatId', String(id))
    messages.value = await loadFromBackend(id)
    await nextTick()
    scrollContainerRef.scrollBottom()
  }

  // ── Create / Delete ──

  async function createConversation() {
    if (creatingConversation.value) return
    try {
      if (activeConvId.value && messages.value.length > 0) {
        await saveToBackend(activeConvId.value).catch(() => {})
      }
      activeConvId.value = null
      sessionStorage.removeItem('currentChatId')
      messages.value = []
    } catch (e) { logWarn('chat', '创建会话失败', e) }
  }

  async function doCreateConversationOnServer() {
    const res = await chatApi.createConversation()
    const conv = res.data.data as Conversation
    conversations.value.unshift(conv)
    activeConvId.value = conv.id
    sessionStorage.setItem('currentChatId', String(conv.id))
  }

  async function deleteConversation(id: number) {
    try {
      await chatApi.deleteConversation(id)
    } catch (e) { logWarn('chat', '删除会话失败', id, e) }
    conversations.value = conversations.value.filter(c => c.id !== id)
    if (id === activeConvId.value) {
      activeConvId.value = null
      messages.value = []
      if (conversations.value.length > 0) {
        await selectConversation(conversations.value[0].id)
      } else {
        await createConversation()
      }
    }
  }

  // ── Persistence ──

  function saveToBackend(convId: number) {
    return chatApi.saveHistory(convId, messages.value).catch((error) => {
      console.warn('[chat] 保存历史失败', { convId, error })
      throw error
    })
  }

  async function loadFromBackend(convId: number): Promise<Message[]> {
    try {
      const res = await chatApi.getHistory(convId)
      return normalizeHistoryMessages(res.data.data)
    } catch (error) {
      console.warn('[chat] 读取历史失败', { convId, error })
      return []
    }
  }

  // ── Normalization ──

  function normalizeHistoryMessages(raw: any): Message[] {
    if (!Array.isArray(raw)) return []
    return raw
      .map((item: any): Message | null => {
        if (!item) return null
        if (typeof item === 'string') {
          return { id: nextMsgId(), role: 'ai', content: item }
        }
        const content = String(item.content ?? item.message ?? item.text ?? '').trim()
        if (!content) return null
        const refsRaw = Array.isArray(item.references) ? item.references
          : Array.isArray(item.refs) ? item.refs : []
        const references = refsRaw.map((v: any) => String(v ?? '').trim()).filter(Boolean).slice(0, 2)
        const quoteRef = item.quoteRef && item.quoteRef.content
          ? { content: String(item.quoteRef.content), author: String(item.quoteRef.author || 'AI') }
          : undefined
        return {
          id: item.id || nextMsgId(),
          role: normalizeMessageRole(item.role),
          content,
          references: references.length ? references : undefined,
          ragReferences: Array.isArray(item.ragReferences) ? item.ragReferences : undefined,
          quoteRef,
        }
      })
      .filter((msg): msg is Message => msg != null)
  }

  function normalizeMessageRole(rawRole: any): 'user' | 'ai' {
    const normalized = String(rawRole ?? '').trim().toLowerCase()
    if (normalized === 'user' || normalized === 'human') return 'user'
    return 'ai'
  }

  return {
    conversations, activeConvId, messages, creatingConversation,
    loadConversations, selectConversation, createConversation,
    doCreateConversationOnServer, deleteConversation,
    saveToBackend, loadFromBackend,
  }
}
