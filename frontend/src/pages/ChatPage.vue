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
            <h2 class="chat-header-title">小情绪</h2>
            <p class="chat-subtitle">你的 AI 情绪伙伴，可以聊聊最近的心情</p>
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
import { NButton, NInput, NPopover } from 'naive-ui'
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

// 本地用直连绕过 Vite 代理避免 SSE 缓冲；远程走同源 /api（Cloudflare 隧道路由到后端）
const API = window.location.hostname === 'localhost'
  ? 'http://localhost:18080/api'
  : window.location.origin + '/api'

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

onMounted(async () => {
  // 读取广场陪跑传递的引用
  const state = history.state as any
  if (state?.references?.length) {
    references.value = state.references.map((r: string) => ({
      label: '陪跑建议',
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
  fetch(API + `/chat/conversations/${convId}/history`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('token')}` },
    body: JSON.stringify({ messages: messages.value }),
  }).catch(() => {})
}

async function loadFromBackend(convId: number): Promise<Message[]> {
  try {
    const res = await fetch(API + `/chat/conversations/${convId}/history`, {
      headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
    })
    const data = await res.json()
    return data.data ?? []
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

  const xhr = new XMLHttpRequest()
  xhr.open('POST', API + `/chat/conversations/${convId}`)
  xhr.setRequestHeader('Content-Type', 'application/json')
  xhr.setRequestHeader('Authorization', `Bearer ${token}`)

  let lastIndex = 0
  let displayText = ''

  function processSSE() {
    const newText = xhr.responseText.substring(lastIndex)
    lastIndex = xhr.responseText.length
    const lines = newText.split('\n')
    for (const line of lines) {
      const trimmed = line.trim()
      if (trimmed.startsWith('data:')) {
        displayText += trimmed.slice(5).trimStart()
      }
    }
  }

  xhr.onprogress = () => {
    processSSE()
    streamingText.value = displayText
    scrollBottom()
  }

  xhr.onloadend = () => {
    // 处理 onloadend 可能在最后一次 onprogress 之前触发的残余数据
    processSSE()
    streamingText.value = displayText
    if (displayText) {
      messages.value.push({ role: 'ai', content: displayText })
    } else {
      messages.value.push({ role: 'ai', content: '抱歉，我暂时无法回复，请稍后再试。' })
    }
    finishSend(convId)
  }

  xhr.onerror = () => { /* handled in onloadend */ }

  const refContents = references.value.map(r => r.content)
  xhr.send(JSON.stringify({ message: content, references: refContents }))
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
    const diaries = (res.data.data || []) as any[]
    recentDiaryOptions.value = diaries.slice(0, 7).map((d: any) => ({
      id: d.id,
      date: d.createdAt?.split('T')[0] ?? '',
      snippet: d.content?.length > 30 ? d.content.slice(0, 30) : d.content ?? ''
    }))
  } catch { recentDiaryOptions.value = [] }
}
</script>
