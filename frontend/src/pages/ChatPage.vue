<template>
  <main class="app-shell chat-shell">
    <AppHeader />

    <div class="chat-window">
      <div class="chat-top">
        <div>
          <h2>小情绪</h2>
          <p class="chat-subtitle">你的 AI 情绪伙伴，可以聊聊最近的心情</p>
        </div>
        <n-button size="small" text @click="clearChat">清空对话</n-button>
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

      <div class="chat-input-row">
        <n-input
          v-model:value="draft"
          placeholder="聊聊你今天的心情..."
          :disabled="streaming"
          clearable
          @keyup.enter="send"
        />
        <n-button type="primary" :disabled="!draft.trim() || streaming" @click="send">
          发送
        </n-button>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NButton, NInput } from 'naive-ui'
import { marked } from 'marked'
import AppHeader from '../components/AppHeader.vue'

interface Message {
  role: 'user' | 'ai'
  content: string
}

function renderMd(text: string) {
  return marked.parse(text, { async: false }) as string
}

const API = 'http://localhost:18080/api'

function saveToBackend() {
  fetch(API + '/chat/history', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('token')}` },
    body: JSON.stringify({ messages: messages.value }),
  }).catch(() => {})
}

async function loadFromBackend(): Promise<Message[]> {
  try {
    const res = await fetch(API + '/chat/history', {
      headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
    })
    const data = await res.json()
    return data.data ?? []
  } catch { return [] }
}

const messages = ref<Message[]>([])
const draft = ref('')
const streaming = ref(false)
const streamingText = ref('')
const msgBox = ref<HTMLElement | null>(null)

onMounted(async () => {
  messages.value = await loadFromBackend()
  if (messages.value.length === 0) {
    messages.value.push({
      role: 'ai',
      content: '嗨，我是小情绪。今天过得怎么样？有什么想聊的吗？',
    })
    saveToBackend()
  }
  scrollBottom()
})

async function send() {
  const content = draft.value.trim()
  if (!content || streaming.value) return

  messages.value.push({ role: 'user', content })
  saveToBackend()
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
  xhr.open('POST', API + '/chat')
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
      messages.value.push({ role: 'ai', content: '抱歉，我暂时无法回复，请稍后再试。' })
    }
    saveToBackend()
    streaming.value = false
    streamingText.value = ''
    scrollBottom()
  }

  xhr.onerror = () => {
    messages.value.push({ role: 'ai', content: '抱歉，网络错误，请稍后再试。' })
    saveToBackend()
    streaming.value = false
    streamingText.value = ''
  }

  xhr.send(JSON.stringify({ message: content }))
}

async function clearChat() {
  messages.value = []
  try {
    await fetch(API + '/chat/memory', { method: 'DELETE', headers: { Authorization: `Bearer ${localStorage.getItem('token')}` } })
  } catch { /* ignore */ }
}

function scrollBottom() {
  if (msgBox.value) {
    msgBox.value.scrollTop = msgBox.value.scrollHeight
  }
}
</script>
