<template>
  <main class="app-shell chat-shell">
    <AppHeader />

    <div class="chat-layout">
      <!-- 会话列表侧边栏 -->
      <aside class="chat-sidebar">
        <div class="sidebar-head">
          <span class="sidebar-title">对话</span>
          <n-button size="tiny" text type="primary" @click="createConversation">+ 新建</n-button>
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
            <p class="chat-subtitle">可以聊聊最近的心情，也可以继续展开报告里的洞察</p>
          </div>
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
            <p v-else>{{ msg.content }}</p>
          </div>

          <div v-if="streaming" class="chat-bubble chat-ai">
            <div class="md-content" v-html="renderMd(streamingText)" />
            <span class="chat-cursor">|</span>
          </div>
        </div>

        <div class="chat-input-area">
          <ReferenceBar
            :items="references"
            :recent-diaries="recentDiaryOptions"
            @remove="removeRef"
            @add="addDiaryRef"
          />
          <div class="chat-input-row">
            <n-input
              v-model:value="draft"
              placeholder="聊聊你今天的心情..."
              :disabled="streaming || !activeConvId"
              clearable
              @keyup.enter="send"
            />
            <n-button type="primary" :disabled="!draft.trim() || streaming || !activeConvId" @click="send">
              发送
            </n-button>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NInput } from 'naive-ui'
import { marked } from 'marked'
import AppHeader from '../components/AppHeader.vue'
import ReferenceBar from '../components/ReferenceBar.vue'
import { chatApi, diaryApi } from '../api'

interface Message {
  role: 'user' | 'ai'
  content: string
}

interface Conversation {
  id: number
  title: string
}

function renderMd(text: string) {
  return marked.parse(text, { async: false }) as string
}

const STREAM_API = resolveStreamApiBase()

const router = useRouter()
const conversations = ref<Conversation[]>([])
const activeConvId = ref<number | null>(null)
const messages = ref<Message[]>([])
const draft = ref('')
const streaming = ref(false)
const streamingText = ref('')
const msgBox = ref<HTMLElement | null>(null)
const references = ref<{ label: string; content: string }[]>([])
const recentDiaryOptions = ref<{ id: number; date: string; snippet: string }[]>([])
const useStreamingChat = isLocalhost()

onMounted(async () => {
  // 读取广场、报告传递的引用
  const state = history.state as any
  if (state?.references?.length) {
    references.value = state.references.map((r: string) => ({
      label: 'MoodCopilot 引用',
      content: r
    }))
    // 清除 state 避免刷新后重复
    history.replaceState({ ...history.state, references: undefined }, '')
  }
  await loadConversations()
  await loadRecentDiaryOptions()
  if (conversations.value.length > 0) {
    await selectConversation(conversations.value[0].id)
  } else {
    await createConversation()
  }
})

async function loadConversations() {
  try {
    const res = await chatApi.listConversations()
    conversations.value = (res.data.data || []) as Conversation[]
  } catch { conversations.value = [] }
}

async function selectConversation(id: number) {
  if (id === activeConvId.value) return
  // 保存当前会话
  if (activeConvId.value && messages.value.length > 0) {
    saveToBackend(activeConvId.value)
  }
  activeConvId.value = id
  messages.value = await loadFromBackend(id)
  await nextTick()
  scrollBottom()
}

async function createConversation() {
  try {
    const res = await chatApi.createConversation()
    const conv = res.data.data as Conversation
    conversations.value.unshift(conv)
    // 保存当前会话消息
    if (activeConvId.value && messages.value.length > 0) {
      saveToBackend(activeConvId.value)
    }
    activeConvId.value = conv.id
    messages.value = []
  } catch { /* ignore */ }
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
  chatApi.saveHistory(convId, messages.value).catch(() => {})
}

async function loadFromBackend(convId: number): Promise<Message[]> {
  try {
    const res = await chatApi.getHistory(convId)
    return res.data.data ?? []
  } catch { return [] }
}

async function send() {
  const content = draft.value.trim()
  const convId = activeConvId.value
  if (!content || streaming.value || !convId) return

  messages.value.push({ role: 'user', content })
  saveToBackend(convId)
  draft.value = ''
  streaming.value = true
  streamingText.value = ''
  scrollBottom()
  const token = localStorage.getItem('token')
  if (!token) {
    messages.value.push({ role: 'ai', content: '请先登录' })
    streaming.value = false
    return
  }

  const refContents = references.value.map(r => r.content)
  if (!useStreamingChat) {
    await sendReply(convId, content, refContents)
    return
  }

  const xhr = new XMLHttpRequest()
  xhr.open('POST', STREAM_API + `/chat/conversations/${convId}`)
  xhr.setRequestHeader('Content-Type', 'application/json')
  xhr.setRequestHeader('Authorization', `Bearer ${token}`)

  let lastIndex = 0
  let displayText = ''

  xhr.onprogress = () => {
    const newText = xhr.responseText.substring(lastIndex)
    lastIndex = xhr.responseText.length
    const lines = newText.split('\n')
    for (const line of lines) {
      const cleaned = line.replace(/\r$/, '')
      if (cleaned.startsWith('data:')) {
        displayText += cleaned.slice(5).replace(/^\s+/, '')
      }
    }
    streamingText.value = displayText
    scrollBottom()
  }

  xhr.onloadend = () => {
    if (displayText) {
      messages.value.push({ role: 'ai', content: displayText })
    } else {
      messages.value.push({ role: 'ai', content: chatErrorMessage(xhr.status) })
    }
    finishSend(convId)
  }

  // onerror 只作标记，onloadend 统一处理（SSE 断连也会触发 onerror）
  xhr.onerror = () => { /* handled in onloadend */ }

  xhr.send(JSON.stringify({ message: content, references: refContents }))
}

async function sendReply(convId: number, content: string, refContents: string[]) {
  try {
    const res = await chatApi.reply(convId, content, refContents)
    const reply = res.data.data || '我刚才没有组织好语言，你可以再说一遍吗？'
    messages.value.push({ role: 'ai', content: reply })
  } catch (e: any) {
    messages.value.push({ role: 'ai', content: chatErrorMessage(e?.response?.status) })
  } finally {
    finishSend(convId)
  }
}

function finishSend(convId: number) {
  saveToBackend(convId)
  streaming.value = false
  streamingText.value = ''
  scrollBottom()
  loadConversations()
}

function scrollBottom() {
  if (msgBox.value) {
    msgBox.value.scrollTop = msgBox.value.scrollHeight
  }
}

function removeRef(index: number) {
  references.value.splice(index, 1)
}

function addDiaryRef(diaryId: string) {
  const d = recentDiaryOptions.value.find(o => String(o.id) === diaryId)
  if (d && !references.value.some(r => r.label === '日记 · ' + d.date)) {
    references.value.push({ label: '日记 · ' + d.date, content: d.snippet })
  }
}

async function loadRecentDiaryOptions() {
  try {
    const res = await diaryApi.mine()
    const data = res.data.data || []
    const diaries = (Array.isArray(data) ? data : data.items ?? []) as any[]
    recentDiaryOptions.value = diaries.slice(0, 7).map((d: any) => ({
      id: d.id,
      date: d.createdAt?.split('T')[0] ?? '',
      snippet: d.content?.length > 30 ? d.content.slice(0, 30) : d.content ?? ''
    }))
  } catch { recentDiaryOptions.value = [] }
}

function resolveStreamApiBase() {
  if (isLocalhost()) return 'http://localhost:18080/api'

  return '/api'
}

function isLocalhost() {
  const hostname = window.location.hostname
  return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1' || hostname === '[::1]'
}

function chatErrorMessage(status?: number) {
  if (status === 429) return '今天的 AI 聊天次数先用完了，明天再继续聊。'
  if (status === 401 || status === 403) return '登录状态过期了，请重新登录后再试。'
  return '抱歉，我暂时无法回复，请稍后再试。'
}
</script>
