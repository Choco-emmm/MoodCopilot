<template>
  <main class="app-shell chat-shell">
    <AppHeader />

    <div class="chat-window">
      <h2>小情绪</h2>
      <p class="chat-subtitle">你的 AI 情绪伙伴，可以聊聊最近的心情</p>

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

        <div v-if="waiting" class="chat-bubble chat-ai">
          <p class="chat-typing">思考中...</p>
        </div>
      </div>

      <div class="chat-input-row">
        <n-input
          v-model:value="draft"
          placeholder="聊聊你今天的心情..."
          :disabled="waiting"
          clearable
          @keyup.enter="send"
        />
        <n-button type="primary" :disabled="!draft.trim() || waiting" @click="send">
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
import { chatApi } from '../api'

interface Message {
  role: 'user' | 'ai'
  content: string
}

const messages = ref<Message[]>([])
const draft = ref('')
const waiting = ref(false)
const msgBox = ref<HTMLElement | null>(null)

onMounted(() => {
  messages.value.push({
    role: 'ai',
    content: '嗨，我是你的情绪伙伴小情绪。今天过得怎么样？有什么想聊的吗？',
  })
})

async function send() {
  const content = draft.value.trim()
  if (!content || waiting.value) return

  messages.value.push({ role: 'user', content })
  draft.value = ''
  waiting.value = true
  await nextTick()
  scrollBottom()

  try {
    const res = await chatApi.send(content)
    messages.value.push({ role: 'ai', content: res.data.data.reply })
  } catch {
    messages.value.push({ role: 'ai', content: '抱歉，我暂时无法回复，请稍后再试。' })
  } finally {
    waiting.value = false
    await nextTick()
    scrollBottom()
  }
}

function scrollBottom() {
  if (msgBox.value) {
    msgBox.value.scrollTop = msgBox.value.scrollHeight
  }
}
</script>
