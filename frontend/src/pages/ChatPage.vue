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
          <p>{{ msg.content }}</p>
        </div>

        <div v-if="streaming" class="chat-bubble chat-ai">
          <p>{{ streamingText }}<span class="chat-cursor">|</span></p>
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
import { ref, nextTick, onMounted } from 'vue'
import { NButton, NInput } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'

interface Message {
  role: 'user' | 'ai'
  content: string
}

function saveToBackend() {
  fetch('/api/chat/history', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('token')}` },
    body: JSON.stringify({ messages: messages.value }),
  }).catch(() => {})
}

async function loadFromBackend(): Promise<Message[]> {
  try {
    const res = await fetch('/api/chat/history', {
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
  await nextTick()
  scrollBottom()

  try {
    const token = localStorage.getItem('token')
    if (!token) throw new Error('Not logged in')

    const response = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ message: content }),
    })
    if (!response.ok) throw new Error('Server error: ' + response.status)

    const reader = response.body?.getReader()
    if (!reader) throw new Error('No reader')

    const decoder = new TextDecoder()
    let displayText = ''
    let rawBuffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      rawBuffer += decoder.decode(value, { stream: true })
      const lines = rawBuffer.split('\n')
      rawBuffer = lines.pop() || ''

      for (const line of lines) {
        const cleaned = line.replace(/\r$/, '')
        if (cleaned.startsWith('data:')) {
          displayText += cleaned.slice(5).replace(/^\s+/, '')
        }
      }
      streamingText.value = displayText
      scrollBottom()
    }
    // Drain remaining buffer
    const last = rawBuffer.trim().replace(/\r$/, '')
    if (last.startsWith('data:')) {
      displayText += last.slice(5).replace(/^\s+/, '')
    }
    if (displayText) {
      messages.value.push({ role: 'ai', content: displayText })
    } else {
      messages.value.push({ role: 'ai', content: '（收到回应了，但内容为空）' })
    }
    saveToBackend()
  } catch (e) {
    messages.value.push({ role: 'ai', content: '抱歉，我暂时无法回复，请稍后再试。' })
    saveToBackend()
  } finally {
    streaming.value = false
    streamingText.value = ''
    scrollBottom()
  }
}

async function clearChat() {
  messages.value = []
  try {
    await fetch('/api/chat/memory', { method: 'DELETE', headers: { Authorization: `Bearer ${localStorage.getItem('token')}` } })
  } catch { /* ignore */ }
}

function scrollBottom() {
  if (msgBox.value) {
    msgBox.value.scrollTop = msgBox.value.scrollHeight
  }
}
</script>
