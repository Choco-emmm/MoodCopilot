<template>
  <main class="app-shell chat-shell">
    <AppHeader />

    <div class="chat-layout">
      <!-- 会话列表侧边栏 -->
      <aside class="chat-sidebar">
        <div class="sidebar-head">
          <span class="sidebar-title">对话</span>
          <n-button size="tiny" text type="primary" :disabled="creatingConversation" @click="createConversation">+ 新建</n-button>
        </div>
        <div class="conv-list">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            :class="['conv-item', { active: conv.id === activeConvId }]"
            @click="selectConversation(conv.id)"
          >
            <span class="conv-title">{{ conv.title }}</span>
            <n-button
              size="tiny"
              text
              class="conv-delete"
              @click.stop="deleteConversation(conv.id)"
            >&times;</n-button>
          </div>
          <div v-if="conversations.length === 0" class="conv-empty">暂无对话</div>
        </div>
      </aside>

      <!-- 聊天区域 -->
      <div class="chat-window">
        <div class="chat-top">
          <div>
            <h2 class="chat-header-title">MoodCopilot</h2>
            <p class="chat-subtitle">可以聊聊最近的心情，分享你的故事和想法</p>
          </div>
        </div>

        <div class="chat-mobile-conv">
          <select
            class="chat-mobile-conv-select"
            :value="activeConvId ?? ''"
            @change="handleMobileConversationChange"
          >
            <option
              v-for="conv in conversations"
              :key="conv.id"
              :value="conv.id"
            >
              {{ conv.title || `对话 ${conv.id}` }}
            </option>
          </select>
          <n-button
            size="small"
            tertiary
            type="error"
            :disabled="!activeConvId"
            @click="deleteActiveConversation"
          >删除</n-button>
          <n-button size="small" type="primary" :disabled="creatingConversation" @click="createConversation">新建</n-button>
        </div>

        <div class="chat-messages" ref="msgBox">
          <div v-if="messages.length === 0" class="chat-empty">
            跟我说说今天怎么样吧～
          </div>

          <div
            v-for="msg in messages"
            :key="msg.id"
            :class="['chat-bubble', msg.role === 'user' ? 'chat-user' : 'chat-ai']"
          >
            <div v-if="msg.role === 'ai'" class="md-content" v-html="renderMd(msg.content)" />
            <template v-else>
              <p>{{ msg.content }}</p>
              <ul v-if="msg.references?.length" class="chat-user-refs">
                <li v-for="(refText, refIndex) in msg.references" :key="`${msg.id}-ref-${refIndex}`">
                  引用：{{ refText }}
                </li>
              </ul>
            </template>
          </div>

          <div v-if="isThinking" class="flex items-start gap-3 my-2 animate-fade-in">
            <div class="w-8 h-8 min-w-[2rem] min-h-[2rem] rounded-full bg-indigo-50 flex items-center justify-center flex-shrink-0 shadow-sm border border-indigo-100 text-sm animate-pulse">
              ✨
            </div>

            <div class="bg-gray-100 text-gray-600 rounded-2xl rounded-tl-none p-4 max-w-[75%] shadow-sm flex flex-col gap-2">
              <div class="text-sm font-medium text-indigo-500 flex items-center gap-1.5">
                <span class="animate-pulse">MoodCopilot 正在沉思<span class="typing-dots"></span></span>
              </div>

              <div class="flex items-center gap-1 h-3 pl-1">
                <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 0ms"></span>
                <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 150ms"></span>
                <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 300ms"></span>
              </div>
            </div>
          </div>

          <!-- RAG 引用折叠面板 -->
          <div v-if="ragReferences.length" class="rag-refs-panel">
            <button class="rag-refs-toggle" @click="showReferences = !showReferences">
              <span class="rag-refs-icon">🔍</span>
              <span>AI 引用了你的 {{ diaryRefs.length }} 条日记</span>
              <span v-if="profileRefs.length">和 {{ profileRefs.length }} 条个人画像</span>
              <span class="rag-refs-arrow">{{ showReferences ? '▾' : '▸' }}</span>
            </button>
            <div v-if="showReferences" class="rag-refs-list">
              <template v-if="diaryRefs.length">
                <div class="rag-refs-section-label">📝 日记记忆</div>
                <div
                  v-for="(ref, i) in diaryRefs"
                  :key="'d'+i"
                  class="rag-ref-item rag-ref-clickable"
                  @click="goToDiary(ref.diaryId)"
                >
                  <span class="rag-ref-date">{{ ref.date }}</span>
                  <span class="rag-ref-snippet">{{ ref.snippet }}</span>
                  <span class="rag-ref-go">→</span>
                </div>
              </template>
              <template v-if="profileRefs.length">
                <div class="rag-refs-section-label">🧠 个人画像</div>
                <div v-for="(ref, i) in profileRefs" :key="'p'+i" class="rag-ref-item">
                  <span class="rag-ref-snippet">{{ ref.snippet }}</span>
                </div>
              </template>
            </div>
          </div>

          <div v-if="streaming && streamingText" class="chat-bubble chat-ai">
            <div class="md-content" v-html="renderMd(streamingText)" />
            <span class="chat-cursor">|</span>
          </div>
        </div>

        <div ref="chatInputArea" class="chat-input-area">
          <div v-if="lastReplyError" class="chat-reply-error-bar">
            <span>{{ lastReplyError }}</span>
            <n-button size="tiny" text type="primary" :disabled="streaming || !lastReplyRequest" @click="retryLastReply">
              重试回复
            </n-button>
          </div>
          <ReferenceBar
            :items="references"
            :recent-diaries="recentDiaryOptions"
            :loading="recentDiariesLoading"
            :error-message="recentDiariesError"
            @remove="removeRef"
            @add="addDiaryRef"
            @retry="loadRecentDiaryOptions"
          />
          <div class="chat-input-row">
            <n-input
              v-model:value="draft"
              placeholder="聊聊你今天的心情..."
              :disabled="streaming || creatingConversation || !activeConvId"
              :maxlength="500"
              clearable
              @focus="handleDraftFocus"
              @keydown.enter.prevent="handleDraftEnter"
            />
            <n-button type="primary" :disabled="!draft.trim() || streaming || creatingConversation || !activeConvId" @click="send">
              {{ streaming ? '发送中' : '发送' }}
            </n-button>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NInput } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import ReferenceBar from '../components/ReferenceBar.vue'
import { chatApi, diaryApi } from '../api'
import { renderSafeMarkdown } from '../utils/markdown'
import { tryExpToast } from '../utils/toast'

interface Message {
  id: string
  role: 'user' | 'ai'
  content: string
  references?: string[]
}

function nextMsgId(): string {
  return `${Date.now()}-${++msgIdCounter}-${Math.random().toString(36).slice(2, 6)}`
}

interface Conversation {
  id: number
  title: string
}

interface ChatReference {
  label: string
  content: string
  fullContent: string
  diaryId?: number
}

const mdCache = new Map<string, string>()
function renderMd(text: string) {
  let cached = mdCache.get(text)
  if (cached) return cached
  cached = renderSafeMarkdown(text)
  mdCache.set(text, cached)
  return cached
}

const conversations = ref<Conversation[]>([])
const activeConvId = ref<number | null>(null)
const messages = ref<Message[]>([])
const draft = ref('')
const streaming = ref(false)
const streamingText = ref('')
let pendingStreamText = ''
let streamRafId: number | null = null
const isThinking = ref(false)
const msgBox = ref<HTMLElement | null>(null)
const chatInputArea = ref<HTMLElement | null>(null)
const references = ref<ChatReference[]>([])
const recentDiaryOptions = ref<{ id: number; date: string; snippet: string; fullContent: string }[]>([])
const recentDiariesLoading = ref(false)
const recentDiariesError = ref<string | null>(null)
const lastReplyError = ref<string | null>(null)
const lastReplyRequest = ref<{ convId: number; content: string; refContents: string[] } | null>(null)
const router = useRouter()
const ragReferences = ref<Array<{ type: string; diaryId: string; date: string; snippet: string }>>([])
const showReferences = ref(true)

/** 去重后的日记引用（同 diaryId 合并） */
const diaryRefs = computed(() => {
  const seen = new Set<string>()
  return ragReferences.value.filter(r => {
    if (r.type === 'profile_memory' || !r.diaryId) return false
    if (seen.has(r.diaryId)) return false
    seen.add(r.diaryId)
    return true
  })
})

/** 个人画像引用 */
const profileRefs = computed(() =>
  ragReferences.value.filter(r => r.type === 'profile_memory'),
)

function goToDiary(diaryId: string) {
  if (!diaryId) return
  router.push(`/diary/${diaryId}`)
}
const viewportBaseHeight = ref(0)
const creatingConversation = ref(false)
const syncCooldownUntil = ref(0)
let syncTimer: number | null = null
// AI 状态速览相关
let convListSyncTick = 0
let msgIdCounter = 0
let streamAbortCtrl: AbortController | null = null

onMounted(async () => {
  const state = history.state as any
  let shouldAutoSend = false
  if (state?.references?.length) {
    references.value = state.references.slice(0, 2).map((r: string) => ({
      label: 'MoodCopilot 引用',
      content: String(r).length > 30 ? String(r).slice(0, 30) + '...' : String(r),
      fullContent: String(r)
    }))
    shouldAutoSend = !!state.autoSend
    if (shouldAutoSend) {
      draft.value = '来看看我最近的报告吧，我们继续聊聊'
    }
    history.replaceState({ ...history.state, references: undefined, autoSend: undefined }, '')
  }

  await loadConversations()
  await loadRecentDiaryOptions()
  if (conversations.value.length > 0) {
    await selectConversation(conversations.value[0].id)
  } else {
    await createConversation()
  }

  if (shouldAutoSend) {
    await nextTick()
    send()
  }

  if (window.visualViewport) {
    viewportBaseHeight.value = Math.max(window.visualViewport.height, window.innerHeight)
    updateMobileKeyboardState()
    window.visualViewport.addEventListener('resize', handleViewportResize)
  }

  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('focus', handleWindowFocus)
  startAutoSync()
})

onBeforeUnmount(() => {
  if (streamRafId !== null) {
    cancelAnimationFrame(streamRafId)
    streamRafId = null
  }
  if (streamAbortCtrl) {
    streamAbortCtrl.abort()
    streamAbortCtrl = null
  }
  if (window.visualViewport) {
    window.visualViewport.removeEventListener('resize', handleViewportResize)
  }
  stopAutoSync()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('focus', handleWindowFocus)
  document.body.classList.remove('chat-keyboard-open')
})

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
        messages.value = []
      }
    }
  } catch { conversations.value = [] }
}

async function selectConversation(id: number) {
  if (id === activeConvId.value) return
  // 中止当前流
  if (streamAbortCtrl) {
    streamAbortCtrl.abort()
    streamAbortCtrl = null
  }
  // 保存当前会话
  if (activeConvId.value && messages.value.length > 0) {
    await saveToBackend(activeConvId.value).catch(() => {})
  }
  activeConvId.value = id
  messages.value = await loadFromBackend(id)
  await nextTick()
  scrollBottom()
}

function startAutoSync() {
  stopAutoSync()
  syncTimer = window.setInterval(() => {
    syncFromServer(false)
  }, 3500)
}

function stopAutoSync() {
  if (syncTimer != null) {
    window.clearInterval(syncTimer)
    syncTimer = null
  }
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    startAutoSync()
    syncFromServer(true)
    return
  }
  stopAutoSync()
}

function handleWindowFocus() {
  syncFromServer(true)
}

async function syncFromServer(forceScroll: boolean) {
  const convId = activeConvId.value
  if (!convId || streaming.value || creatingConversation.value || document.visibilityState !== 'visible') return
  if (Date.now() < syncCooldownUntil.value) return

  try {
    const latest = await loadFromBackend(convId)
    const current = messages.value
    // 远端无数据或本地消息更多（本地有未持久化的新消息）时，不覆盖本地
    if (latest.length === 0) return
    if (current.length > latest.length) return
    const changed = !isSameMessageList(current, latest)
    const keepStickBottom = isNearBottom(msgBox.value)
    if (changed) {
      messages.value = latest
      await nextTick()
      if (forceScroll || keepStickBottom) {
        scrollBottom()
      }
    }

    convListSyncTick += 1
    if (convListSyncTick % 3 === 0) {
      await loadConversations()
    }
  } catch {
    // ignore sync failures to avoid interrupting user input
  }
}

function isSameMessageList(a: Message[], b: Message[]) {
  if (a.length !== b.length) return false
  for (let i = 0; i < a.length; i += 1) {
    if (a[i].id !== b[i].id) return false
  }
  return true
}

function isNearBottom(el: HTMLElement | null) {
  if (!el) return true
  const distance = el.scrollHeight - el.scrollTop - el.clientHeight
  return distance < 64
}

async function createConversation() {
  if (creatingConversation.value) return
  creatingConversation.value = true
  try {
    // 避免用户在会话创建尚未完成时把第一条消息发到旧会话里。
    if (activeConvId.value && messages.value.length > 0) {
      await saveToBackend(activeConvId.value).catch(() => {})
    }
    activeConvId.value = null
    messages.value = []

    const res = await chatApi.createConversation()
    const conv = res.data.data as Conversation
    conversations.value.unshift(conv)
    activeConvId.value = conv.id
    messages.value = []
  } catch { /* ignore */ }
  finally {
    creatingConversation.value = false
  }
}

async function deleteConversation(id: number) {
  try {
    await chatApi.deleteConversation(id)
  } catch { /* ignore */ }
  conversations.value = conversations.value.filter(c => c.id !== id)
  if (id === activeConvId.value) {
    activeConvId.value = null
    messages.value = []
    // 自动选第一个
    if (conversations.value.length > 0) {
      await selectConversation(conversations.value[0].id)
    } else {
      await createConversation()
    }
  }
}

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

      const refsRaw = Array.isArray(item.references)
        ? item.references
        : Array.isArray(item.refs)
          ? item.refs
          : []

      const references = refsRaw
        .map((v: any) => String(v ?? '').trim())
        .filter(Boolean)
        .slice(0, 2)

      return {
        id: item.id || nextMsgId(),
        role: normalizeMessageRole(item.role),
        content,
        references: references.length ? references : undefined,
      }
    })
    .filter((msg): msg is Message => msg != null)
}

function normalizeMessageRole(rawRole: any): 'user' | 'ai' {
  const normalized = String(rawRole ?? '').trim().toLowerCase()
  if (normalized === 'user' || normalized === 'human') return 'user'
  if (normalized === 'assistant' || normalized === 'ai' || normalized === 'bot' || normalized === 'system') return 'ai'
  return 'ai'
}

async function send() {
  const content = draft.value.trim()
  const convId = activeConvId.value
  if (!content || streaming.value || creatingConversation.value || !convId) return

  lastReplyError.value = null
  lastReplyRequest.value = null
  const refContents = references.value.slice(0, 2).map(r => r.fullContent || r.content)
  messages.value.push({ id: nextMsgId(), role: 'user', content, references: refContents.length ? refContents : undefined })
  saveToBackend(convId).catch(() => {})
  references.value = []
  draft.value = ''
  tryExpToast('chat', '聊天 +5 EXP')
  streaming.value = true
  streamingText.value = ''
  isThinking.value = true
  scrollBottom()
  const token = localStorage.getItem('token')
  if (!token) {
    isThinking.value = false
    messages.value.push({ id: nextMsgId(), role: 'ai', content: '请先登录' })
    streaming.value = false
    return
  }

  await sendReply(convId, content, refContents, false)
}

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

async function sendReply(convId: number, content: string, refContents: string[], isRetry: boolean) {
  // 中止上一次未完成的流
  if (streamAbortCtrl) {
    streamAbortCtrl.abort()
    streamAbortCtrl = null
  }
  const ctrl = new AbortController()
  streamAbortCtrl = ctrl

  ragReferences.value = []
  showReferences.value = true
  let fullReply = ''

  try {
    await chatApi.replyStream(convId, content, refContents, (chunk) => {
      fullReply += chunk
      pendingStreamText = fullReply
      if (isThinking.value) {
        isThinking.value = false
      }
      if (streamRafId === null) {
        streamRafId = requestAnimationFrame(() => {
          streamingText.value = pendingStreamText
          streamRafId = null
          scrollBottom()
        })
      }
    }, ctrl, (items) => {
      ragReferences.value = items
    })

    // 流正常结束
    if (activeConvId.value !== convId) return
    lastReplyError.value = null
    lastReplyRequest.value = null
    messages.value.push({
      id: nextMsgId(),
      role: 'ai',
      content: fullReply || '我刚才没有组织好语言，你可以再说一遍吗？',
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
    finishSend(convId)
  }
}

async function finishSend(convId: number) {
  if (streamRafId !== null) {
    cancelAnimationFrame(streamRafId)
    streamRafId = null
  }
  streaming.value = false
  streamingText.value = ''
  isThinking.value = false
  references.value = []
  scrollBottom()
  try {
    await saveToBackend(convId)
  } catch {
    syncCooldownUntil.value = Date.now() + 5000
  }
  loadConversations()
}

function scrollBottom() {
  if (msgBox.value) {
    msgBox.value.scrollTop = msgBox.value.scrollHeight
  }
}

function ensureInputVisible(behavior: ScrollBehavior = 'smooth') {
  window.requestAnimationFrame(() => {
    chatInputArea.value?.scrollIntoView({ behavior, block: 'end' })
    scrollBottom()
  })
}

function handleDraftFocus() {
  ensureInputVisible('auto')
}

function handleViewportResize() {
  updateMobileKeyboardState()
  if (document.activeElement?.tagName === 'INPUT' || document.activeElement?.tagName === 'TEXTAREA') {
    ensureInputVisible('auto')
  }
}

function handleMobileConversationChange(event: Event) {
  const target = event.target as HTMLSelectElement
  const nextId = Number(target.value)
  if (Number.isFinite(nextId) && nextId > 0) {
    selectConversation(nextId)
  }
}

function deleteActiveConversation() {
  const convId = activeConvId.value
  if (!convId) return
  const ok = window.confirm('确认删除当前对话吗？删除后不可恢复。')
  if (!ok) return
  deleteConversation(convId)
}

function updateMobileKeyboardState() {
  const vv = window.visualViewport
  if (!vv) return
  const baseHeight = viewportBaseHeight.value || window.innerHeight
  const keyboardLikelyOpen = baseHeight - vv.height > 120
  document.body.classList.toggle('chat-keyboard-open', keyboardLikelyOpen)
}

function handleDraftEnter(event: KeyboardEvent) {
  if ((event as any).isComposing) return
  send()
}

function removeRef(index: number) {
  references.value.splice(index, 1)
}

function addDiaryRef(diaryId: string) {
  const d = recentDiaryOptions.value.find(o => String(o.id) === diaryId)
  if (d && !references.value.some(r => r.diaryId === d.id)) {
    references.value.push({ label: '日记 · ' + d.date, content: d.snippet, fullContent: d.fullContent, diaryId: d.id })
  }
}

async function loadRecentDiaryOptions() {
  recentDiariesLoading.value = true
  recentDiariesError.value = null
  try {
    const options: { id: number; date: string; snippet: string; fullContent: string }[] = []

    // 加载最近的日记
    try {
      const res = await diaryApi.mine(1, 20)
      const data = res.data.data || []
      const diaries = (Array.isArray(data) ? data : data.items ?? []) as any[]
      diaries.slice(0, 20).forEach((d: any) => {
        options.push({
          id: d.id,
          date: d.createdAt?.split('T')[0] ?? '',
          snippet: d.content?.length > 30 ? d.content.slice(0, 30) + '...' : d.content ?? '',
          fullContent: d.content ?? ''
        })
      })
    } catch (e) {
      recentDiariesError.value = '加载最近日记失败'
      console.warn('[chat] 加载引用日记失败', e)
    }

    recentDiaryOptions.value = options
  } catch {
    recentDiaryOptions.value = []
    recentDiariesError.value = '加载最近日记失败'
  } finally {
    recentDiariesLoading.value = false
  }
}

function chatErrorMessage(status?: number, bizMessage?: string) {
  if (bizMessage && bizMessage !== 'Request failed with status code 429') return bizMessage
  if (status === 401 || status === 403) return '登录状态过期了，请重新登录后再试。'
  return '抱歉，我暂时无法回复，请稍后再试。'
}
</script>

<style scoped>
@keyframes bounce-subtle {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-4px); }
}

.animate-bounce {
  animation: bounce-subtle 1.2s infinite ease-in-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-fade-in {
  animation: fadeIn 0.25s ease-out forwards;
}

/* 动态省略号打字效果 */
@keyframes typing-dots {
  0% { content: ''; }
  25% { content: '.'; }
  50% { content: '..'; }
  75% { content: '...'; }
  100% { content: ''; }
}

.typing-dots::after {
  content: '';
  animation: typing-dots 1.5s infinite;
  display: inline-block;
  width: 1em;
  text-align: left;
}

.chat-reply-error-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  margin-bottom: 8px;
  border-radius: 10px;
  background: #fff4f4;
  border: 1px solid #ffd7d7;
  color: #9a3030;
  font-size: 13px;
}

.chat-user-refs {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.5;
  opacity: 0.9;
}

/* Markdown 动态内容样式穿透 */
.md-content :deep(p) {
  margin: 0 0 0.5em 0;
  line-height: 1.6;
}
.md-content :deep(p:last-child) {
  margin-bottom: 0;
}
.md-content :deep(strong), .md-content :deep(b) {
  font-weight: 600;
  color: inherit;
}
.md-content :deep(ul) {
  list-style-type: disc;
  padding-left: 1.5em;
  margin: 0.5em 0;
}
.md-content :deep(ol) {
  list-style-type: decimal;
  padding-left: 1.5em;
  margin: 0.5em 0;
}
.md-content :deep(li) {
  margin-bottom: 0.25em;
}
.md-content :deep(blockquote) {
  border-left: 3px solid #cbd5e1;
  padding-left: 0.75em;
  color: #64748b;
  margin: 0.5em 0;
}
.md-content :deep(code) {
  background-color: rgba(0, 0, 0, 0.05);
  padding: 0.2em 0.4em;
  border-radius: 4px;
  font-family: monospace;
  font-size: 0.9em;
}

/* ── RAG 引用折叠面板 ── */
.rag-refs-panel {
  margin: 6px 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  overflow: hidden;
  animation: refsIn 0.2s var(--ease-out);
}

@keyframes refsIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.rag-refs-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 12px;
  border: none;
  background: none;
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.rag-refs-toggle:hover {
  background: var(--color-surface-hover);
}

.rag-refs-icon {
  font-size: 13px;
}

.rag-refs-arrow {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-text-muted);
}

.rag-refs-list {
  display: grid;
  gap: 0;
  border-top: 1px solid var(--color-border);
  padding: 2px 0;
}

.rag-refs-section-label {
  padding: 4px 12px 2px;
  font-size: 10px;
  font-weight: 700;
  color: var(--color-text-muted);
  letter-spacing: 0.04em;
}

.rag-ref-item {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
  font-size: var(--text-xs);
}

.rag-ref-item:not(:last-child) {
  border-bottom: 1px solid color-mix(in srgb, var(--color-border) 40%, transparent 60%);
}

.rag-ref-clickable {
  cursor: pointer;
  transition: background 0.12s;
  grid-template-columns: 52px 1fr auto;
}

.rag-ref-clickable:hover {
  background: var(--color-primary-light);
}

.rag-ref-date {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.rag-ref-snippet {
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.rag-ref-go {
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
</style>
