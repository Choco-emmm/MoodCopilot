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
            v-for="(msg, i) in messages"
            :key="i"
            :class="['chat-bubble', msg.role === 'user' ? 'chat-user' : 'chat-ai']"
          >
            <div v-if="msg.role === 'ai'" class="md-content" v-html="renderMd(msg.content)" />
            <template v-else>
              <p>{{ msg.content }}</p>
              <ul v-if="msg.references?.length" class="chat-user-refs">
                <li v-for="(refText, refIndex) in msg.references" :key="`${i}-ref-${refIndex}`">
                  引用：{{ refText }}
                </li>
              </ul>
            </template>
          </div>

          <div v-if="isThinking" class="flex items-start gap-3 my-2 animate-fade-in">
            <div class="w-8 h-8 rounded-full bg-indigo-100 flex items-center justify-center flex-shrink-0 shadow-sm text-sm">
              🧠
            </div>

            <div class="bg-gray-100 text-gray-600 rounded-2xl rounded-tl-none p-4 max-w-[75%] shadow-sm flex flex-col gap-2">
              <div class="text-sm font-medium text-indigo-500 flex items-center gap-1.5">
                <span class="animate-pulse">MoodCopilot 正在沉思...</span>
              </div>

              <div class="flex items-center gap-1 h-3 pl-1">
                <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 0ms"></span>
                <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 150ms"></span>
                <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 300ms"></span>
              </div>
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
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { NButton, NInput } from 'naive-ui'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import AppHeader from '../components/AppHeader.vue'
import ReferenceBar from '../components/ReferenceBar.vue'
import { chatApi, diaryApi } from '../api'

interface Message {
  role: 'user' | 'ai'
  content: string
  references?: string[]
}

interface Conversation {
  id: number
  title: string
}

interface ChatReference {
  label: string
  content: string
  diaryId?: number
}

function renderMd(text: string) {
  const html = marked.parse(text, { async: false }) as string
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    ALLOWED_TAGS: [
      'p', 'br', 'strong', 'em', 'b', 'i', 'u',
      'ul', 'ol', 'li', 'blockquote', 'code', 'pre',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'a',
    ],
    ALLOWED_ATTR: ['href', 'target', 'rel'],
  })
}

const conversations = ref<Conversation[]>([])
const activeConvId = ref<number | null>(null)
const messages = ref<Message[]>([])
const draft = ref('')
const streaming = ref(false)
const streamingText = ref('')
const isThinking = ref(false)
const msgBox = ref<HTMLElement | null>(null)
const chatInputArea = ref<HTMLElement | null>(null)
const references = ref<ChatReference[]>([])
const recentDiaryOptions = ref<{ id: number; date: string; snippet: string }[]>([])
const recentDiariesLoading = ref(false)
const recentDiariesError = ref<string | null>(null)
const lastReplyError = ref<string | null>(null)
const lastReplyRequest = ref<{ convId: number; content: string; refContents: string[] } | null>(null)
const viewportBaseHeight = ref(0)
const creatingConversation = ref(false)
let syncTimer: number | null = null
let convListSyncTick = 0

onMounted(async () => {
  const state = history.state as any
  let shouldAutoSend = false
  if (state?.references?.length) {
    references.value = state.references.slice(0, 2).map((r: string) => ({
      label: 'MoodCopilot 引用',
      content: String(r).slice(0, 120)
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
  // 保存当前会话
  if (activeConvId.value && messages.value.length > 0) {
    await saveToBackend(activeConvId.value)
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

  try {
    const latest = await loadFromBackend(convId)
    const current = messages.value
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
    const left = a[i]
    const right = b[i]
    if (left.role !== right.role) return false
    if (left.content !== right.content) return false
    const leftRefs = left.references ?? []
    const rightRefs = right.references ?? []
    if (leftRefs.length !== rightRefs.length) return false
    for (let j = 0; j < leftRefs.length; j += 1) {
      if (leftRefs[j] !== rightRefs[j]) return false
    }
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
      await saveToBackend(activeConvId.value)
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
        return { role: 'ai', content: item }
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
  const refContents = references.value.slice(0, 2).map(r => r.content.slice(0, 120))
  messages.value.push({ role: 'user', content, references: refContents.length ? refContents : undefined })
  saveToBackend(convId)
  draft.value = ''
  streaming.value = true
  streamingText.value = ''
  isThinking.value = true
  scrollBottom()
  const token = localStorage.getItem('token')
  if (!token) {
    isThinking.value = false
    messages.value.push({ role: 'ai', content: '请先登录' })
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
  try {
    const res = await chatApi.reply(convId, content, refContents)
    if (res.data?.code !== 0) {
      throw new Error(res.data?.message || '请求失败')
    }
    const reply = String(res.data?.data ?? '').trim() || '我刚才没有组织好语言，你可以再说一遍吗？'
    isThinking.value = false
    // 会话切换后，旧请求返回不应再写入当前会话消息。
    if (activeConvId.value !== convId) {
      return
    }
    lastReplyError.value = null
    lastReplyRequest.value = null
    messages.value.push({ role: 'ai', content: reply })
  } catch (e: any) {
    isThinking.value = false
    const bizMessage = e?.response?.data?.message || e?.message
    const errorText = chatErrorMessage(e?.response?.status, bizMessage)
    if (activeConvId.value === convId) {
      lastReplyError.value = errorText
      lastReplyRequest.value = { convId, content, refContents }
      if (!isRetry) {
        messages.value.push({ role: 'ai', content: errorText })
      }
    }
  } finally {
    finishSend(convId)
  }
}

async function finishSend(convId: number) {
  await saveToBackend(convId)
  streaming.value = false
  streamingText.value = ''
  isThinking.value = false
  references.value = []
  scrollBottom()
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
    references.value.push({ label: '日记 · ' + d.date, content: d.snippet.slice(0, 120), diaryId: d.id })
  }
}

async function loadRecentDiaryOptions() {
  recentDiariesLoading.value = true
  recentDiariesError.value = null
  try {
    const options: { id: number; date: string; snippet: string }[] = []

    // 加载最近的日记
    try {
      const res = await diaryApi.mine(1, 20)
      const data = res.data.data || []
      const diaries = (Array.isArray(data) ? data : data.items ?? []) as any[]
      diaries.slice(0, 20).forEach((d: any) => {
        options.push({
          id: d.id,
          date: d.createdAt?.split('T')[0] ?? '',
          snippet: d.content?.length > 30 ? d.content.slice(0, 30) : d.content ?? ''
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
  if (bizMessage) return bizMessage
  if (status === 429) return '今天的 AI 聊天次数先用完了，明天再继续聊。'
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
</style>
