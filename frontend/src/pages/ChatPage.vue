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
            <h2 class="chat-header-title">MoodCopilot</h2>
            <p class="chat-subtitle">可以聊聊最近的心情，分享你的故事和想法</p>
            
            <div v-if="quickStartersLoading" class="chat-quick-starters skeleton-starters">
              <div v-for="i in 4" :key="i" class="quick-starter-card skeleton-card">
                <div class="skeleton-icon"></div>
                <div class="skeleton-text"></div>
              </div>
            </div>
            <div v-else class="chat-quick-starters">
              <button 
                v-for="(item, idx) in quickStarters" 
                :key="idx" 
                class="quick-starter-card"
                type="button"
                @click="useQuickStarter(item.text)"
              >
                <span class="starter-icon">{{ item.icon }}</span>
                <span class="starter-text">{{ item.text }}</span>
              </button>
            </div>
          </div>

          <template v-for="msg in messages" :key="msg.id">
            <div :class="['msg-item', msg.role]">
              <!-- AI Avatar -->
              <div v-if="msg.role === 'ai'" class="msg-avatar ai-avatar">
                <img class="ai-avatar-icon" src="/logo.svg" alt="MoodCopilot" />
              </div>

              <div class="msg-wrapper">
                <div v-if="msg.role === 'ai' && msg.ragReferences?.length" class="rag-refs-panel rag-refs-above rag-references-fixed">
                  <button class="rag-refs-toggle" @click="toggleMsgRefs(msg.id)">
                    <span class="rag-refs-icon">🔍</span>
                    <span>已检索 {{ countDiaryRefs(msg.ragReferences) }} 条记录</span>
                    <span v-if="countProfileRefs(msg.ragReferences)"> · {{ countProfileRefs(msg.ragReferences) }} 条画像</span>
                    <span v-if="countGraphRefs(msg.ragReferences)"> · {{ countGraphRefs(msg.ragReferences) }} 条图谱</span>
                    <span class="rag-refs-arrow">{{ expandedRefs.has(msg.id) ? '▾' : '▸' }}</span>
                  </button>
                  <div v-if="expandedRefs.has(msg.id)" class="rag-refs-list">
                    <template v-if="getDiaryRefs(msg.ragReferences).length">
                      <div class="rag-refs-section-label">📝 日记记忆</div>
                      <div
                        v-for="(ref, i) in getDiaryRefs(msg.ragReferences)"
                        :key="'d'+i"
                        class="rag-ref-item rag-ref-clickable"
                        @click="ref.diaryId && goToDiary(ref.diaryId)"
                      >
                        <div class="rag-ref-meta">
                          <span class="rag-ref-date">{{ formatRefDate(ref.date) }}</span>
                          <span v-if="ref.toolName" class="rag-ref-tool-badge">{{ toolLabel(ref.toolName) }}</span>
                        </div>
                        <span class="rag-ref-snippet" :title="ref.snippet">{{ ref.snippet }}</span>
                        <span v-if="ref.diaryId" class="rag-ref-go">→</span>
                      </div>
                    </template>
                    <template v-if="getProfileRefs(msg.ragReferences).length">
                      <div class="rag-refs-section-label">🧠 个人画像</div>
                      <div v-for="(ref, i) in getProfileRefs(msg.ragReferences)" :key="'p'+i"
                           class="rag-ref-item-profile"
                           @click="toggleProfileSnippet(msg.id, i)">
                        <span :class="['rag-ref-snippet', { 'expanded': isProfileSnippetExpanded(msg.id, i) }]" :title="ref.snippet || ref.value">
                          <span v-if="ref.key" class="rag-ref-key">【{{ ref.key }}】</span>{{ ref.snippet || ref.value }}
                        </span>
                      </div>
                    </template>
                    <template v-if="getGraphRefs(msg.ragReferences).length">
                      <div class="rag-refs-section-label">🕸️ 关系图谱</div>
                      <div
                        v-for="(ref, i) in getGraphRefs(msg.ragReferences)"
                        :key="'g'+i"
                        :class="['rag-ref-item', { 'rag-ref-clickable': ref.diaryId }]"
                        @click="ref.diaryId && goToDiary(ref.diaryId)"
                      >
                        <div class="rag-ref-meta">
                          <span class="rag-ref-date">{{ formatRefDate(ref.date) }}</span>
                          <span v-if="ref.toolName" class="rag-ref-tool-badge">{{ toolLabel(ref.toolName) }}</span>
                        </div>
                        <span class="rag-ref-snippet" :title="ref.snippet">{{ ref.snippet }}</span>
                        <span v-if="ref.diaryId" class="rag-ref-go">→</span>
                      </div>
                    </template>
                  </div>
                </div>

                <div
                  :class="['chat-bubble', msg.role === 'user' ? 'chat-user' : 'chat-ai']"
                >
                  <template v-if="msg.role === 'ai'">
                    <!-- think 块内容不对用户展示，只显示正文 -->
                    <div v-if="parseThink(msg.content).text" class="md-content" v-html="renderMd(parseThink(msg.content).text)" />
                    <!-- 如果只有 think 没有正文（消息异常时的兜底） -->
                    <span v-else class="ai-think-placeholder">...</span>
                  </template>
                  <template v-else>
                    <p>{{ msg.content }}</p>
                    <ul v-if="msg.references?.length" class="chat-user-refs">
                      <li v-for="(refText, refIndex) in msg.references" :key="`${msg.id}-ref-${refIndex}`">
                        引用：{{ refText }}
                      </li>
                    </ul>
                  </template>
                </div>
              </div>

              <!-- User Avatar -->
              <div v-if="msg.role === 'user'" class="msg-avatar user-avatar">
                <img v-if="authStore.avatar" :src="authStore.avatar" :alt="authStore.displayName || 'Me'" />
                <span v-else>{{ userInitial }}</span>
              </div>
            </div>
          </template>

          <div v-if="isThinking" class="msg-item ai animate-fade-in">
            <div class="msg-avatar ai-avatar">
              <img class="ai-avatar-icon" src="/logo.svg" alt="MoodCopilot" />
            </div>
            <div class="msg-wrapper">
              <div class="chat-bubble chat-ai thinking-bubble">
                <div class="thinking-header">
                  <span class="sparkle-icon">✨</span>
                  <span class="thinking-text">MoodCopilot 正在沉思</span>
                </div>
                <div class="thinking-dots-loader">
                  <span class="dot animate-bounce" style="animation-delay: 0ms"></span>
                  <span class="dot animate-bounce" style="animation-delay: 150ms"></span>
                  <span class="dot animate-bounce" style="animation-delay: 300ms"></span>
                </div>
              </div>
            </div>
          </div>

          <!-- 流式回复（引用先行，文本追加） -->
          <div v-if="streaming && (streamingText || streamingRefs.length)" class="msg-item ai">
            <div class="msg-avatar ai-avatar">
              <img class="ai-avatar-icon" src="/logo.svg" alt="MoodCopilot" />
            </div>
            <div class="msg-wrapper">
              <div v-if="streaming && streamingRefs.length" class="rag-refs-panel rag-refs-above rag-references-fixed">
                <button class="rag-refs-toggle" @click="showStreamingRefs = !showStreamingRefs">
                  <span class="rag-refs-icon">🔍</span>
                  <span>已检索 {{ streamingDiaryRefs.length }} 条记录</span>
                  <span v-if="streamingProfileRefs.length"> · {{ streamingProfileRefs.length }} 条画像</span>
                  <span v-if="streamingGraphRefs.length"> · {{ streamingGraphRefs.length }} 条图谱</span>
                  <span class="rag-refs-arrow">{{ showStreamingRefs ? '▾' : '▸' }}</span>
                </button>
                <div v-if="showStreamingRefs" class="rag-refs-list">
                  <template v-if="streamingDiaryRefs.length">
                    <div class="rag-refs-section-label">📝 日记记忆</div>
                    <div
                      v-for="(ref, i) in streamingDiaryRefs"
                      :key="'sd'+i"
                      class="rag-ref-item rag-ref-clickable"
                      @click="ref.diaryId && goToDiary(ref.diaryId)"
                    >
                      <div class="rag-ref-meta">
                        <span class="rag-ref-date">{{ formatRefDate(ref.date) }}</span>
                        <span v-if="ref.toolName" class="rag-ref-tool-badge">{{ toolLabel(ref.toolName) }}</span>
                      </div>
                      <span class="rag-ref-snippet" :title="ref.snippet">{{ ref.snippet }}</span>
                      <span v-if="ref.diaryId" class="rag-ref-go">→</span>
                    </div>
                  </template>
                  <template v-if="streamingProfileRefs.length">
                    <div class="rag-refs-section-label">🧠 个人画像</div>
                    <div v-for="(ref, i) in streamingProfileRefs" :key="'sp'+i" class="rag-ref-item">
                      <span class="rag-ref-snippet" :title="ref.snippet || ref.value">
                        <span v-if="ref.key" class="rag-ref-key">【{{ ref.key }}】</span>{{ ref.snippet || ref.value }}
                      </span>
                    </div>
                  </template>
                  <template v-if="streamingGraphRefs.length">
                    <div class="rag-refs-section-label">🕸️ 关系图谱</div>
                    <div
                      v-for="(ref, i) in streamingGraphRefs"
                      :key="'sg'+i"
                      :class="['rag-ref-item', { 'rag-ref-clickable': ref.diaryId }]"
                      @click="ref.diaryId && goToDiary(ref.diaryId)"
                    >
                      <div class="rag-ref-meta">
                        <span v-if="ref.date" class="rag-ref-date">{{ formatRefDate(ref.date) }}</span>
                        <span v-if="ref.toolName" class="rag-ref-tool-badge">{{ toolLabel(ref.toolName) }}</span>
                      </div>
                      <span class="rag-ref-snippet" :title="ref.snippet">{{ ref.snippet }}</span>
                      <span v-if="ref.diaryId" class="rag-ref-go">→</span>
                    </div>
                  </template>
                </div>
              </div>

              <div class="chat-bubble chat-ai">
                <!-- 深度思考中：有 think 内容但正文还未出现时，只展示动效，不渲染 think 内容 -->
                <div v-if="parseThink(streamingText).think && !parseThink(streamingText).text" class="thinking-status">
                  <span class="sparkle-icon">✨</span>
                  <span class="thinking-text">深度思考中</span>
                  <span class="thinking-dots-inline">
                    <span class="dot animate-bounce" style="animation-delay: 0ms"></span>
                    <span class="dot animate-bounce" style="animation-delay: 150ms"></span>
                    <span class="dot animate-bounce" style="animation-delay: 300ms"></span>
                  </span>
                </div>

                <!-- 无任何内容时（还没收到 think 或 text） -->
                <div v-if="!parseThink(streamingText).text && !parseThink(streamingText).think" class="thinking-status">
                  <span class="sparkle-icon">✨</span>
                  <span class="thinking-text">MoodCopilot 正在思考</span>
                  <span class="typing-dots"></span>
                </div>

                <!-- 正文流式输出 -->
                <div v-if="parseThink(streamingText).text" class="md-content streaming-md" v-html="renderStreamingMd(parseThink(streamingText).text, true)" />
              </div>
            </div>
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
              size="large"
              placeholder="聊聊你今天的心情..."
              :disabled="streaming || creatingConversation"
              :maxlength="500"
              clearable
              @focus="handleDraftFocus"
              @keydown.enter.prevent="handleDraftEnter"
            />
            <n-button type="primary" :disabled="!draft.trim() || streaming || creatingConversation" @click="send">
              {{ streaming ? '发送中' : '发送' }}
            </n-button>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NInput } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import ReferenceBar from '../components/ReferenceBar.vue'
import { chatApi, diaryApi } from '../api'
import { renderSafeMarkdown } from '../utils/markdown'
import { tryExpToast } from '../utils/toast'
import { useAuthStore } from '../stores/auth'
import { logWarn } from '../utils/logger'

const authStore = useAuthStore()
const userInitial = computed(() => {
  return authStore.displayName ? authStore.displayName.trim().charAt(0).toUpperCase() : '我'
})

interface RagRef {
  type: string
  diaryId?: string
  date?: string
  snippet?: string
  toolName?: string
  value?: string
  key?: string
}

interface Message {
  id: string
  role: 'user' | 'ai'
  content: string
  references?: string[]
  ragReferences?: RagRef[]
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

function parseThink(content: string) {
  if (!content) return { think: '', text: '' }

  let think = ''

  // 1. 提取并移除所有已闭合的 <think>...</think> 块
  let text = content.replace(/<think>([\s\S]*?)<\/think>/g, (match, innerThink) => {
    think += (think ? '\n\n' : '') + innerThink.trim()
    return '' // 将原文本中的 think 块抹除
  })

  // 2. 处理流式输出时，最后一段可能还没闭合的 <think> 标签
  const unclosedMatch = text.match(/<think>([\s\S]*)$/)
  if (unclosedMatch) {
    think += (think ? '\n\n' : '') + unclosedMatch[1].trim()
    text = text.substring(0, unclosedMatch.index) // 将未闭合的 think 部分从正文中剪裁掉
  }

  return {
    think: think.trim(),
    text: text.trimStart() // 避免因为移除 think 导致正文开头有多余空行
  }
}

const isThinkExpanded = ref(false)
const expandedProfileSnippets = ref<Set<string>>(new Set())

function toggleProfileSnippet(msgId: string, idx: number) {
  const key = `${msgId}-${idx}`
  if (expandedProfileSnippets.value.has(key)) {
    expandedProfileSnippets.value.delete(key)
  } else {
    expandedProfileSnippets.value.add(key)
  }
}

function isProfileSnippetExpanded(msgId: string, idx: number) {
  return expandedProfileSnippets.value.has(`${msgId}-${idx}`)
}

function renderStreamingMd(text: string, showCursor: boolean) {
  const processed = showCursor ? text + '<span class="streaming-cursor">▋</span>' : text
  return renderMd(processed)
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

const quickStarters = ref<{icon: string; text: string}[]>([])
const quickStartersLoading = ref(true)

async function loadWelcomeTopics() {
  quickStartersLoading.value = true
  try {
    const res = await chatApi.getWelcomeTopics()
    if (res.data.data && Array.isArray(res.data.data) && res.data.data.length > 0) {
      quickStarters.value = res.data.data
    } else {
      throw new Error('No dynamic topics')
    }
  } catch (err) {
    quickStarters.value = [
      { icon: '🌟', text: '分析我最近三天的情绪波动' },
      { icon: '💡', text: '帮我回顾我最近开心的事情' },
      { icon: '🌿', text: '推荐一些适合我解压的音乐与方法' },
      { icon: '💬', text: '今天有点累，陪我聊一下' }
    ]
  } finally {
    quickStartersLoading.value = false
  }
}

function useQuickStarter(text: string) {
  draft.value = text
  send()
}
const recentDiariesLoading = ref(false)
const recentDiariesError = ref<string | null>(null)
const lastReplyError = ref<string | null>(null)
const lastReplyRequest = ref<{ convId: number; content: string; refContents: string[] } | null>(null)
const router = useRouter()
const streamingRefs = ref<RagRef[]>([])
const showStreamingRefs = ref(false)
const expandedRefs = reactive(new Set<string>())

function countDiaryRefs(refs: RagRef[]): number {
  const seen = new Set<string>()
  return refs.filter(r => {
    if (r.type === 'profile_memory' || r.type === 'graph_memory' || !r.diaryId) return false
    if (seen.has(r.diaryId)) return false
    seen.add(r.diaryId)
    return true
  }).length
}

function countProfileRefs(refs: RagRef[]): number {
  return refs.filter(r => r.type === 'profile_memory').length
}

function countGraphRefs(refs: RagRef[]): number {
  return getGraphRefs(refs).length
}

function toolLabel(name?: string): string {
  if (!name) return ''
  const map: Record<string, string> = {
    diarySearch: '日记',
    userStats: '统计',
    reportSnapshot: '报告',
    memoryQuery: '画像',
    graphSearch: '图谱',
  }
  return map[name] || name
}

function formatRefDate(dateStr?: string): string {
  if (!dateStr) return ''
  const tIndex = dateStr.indexOf('T')
  if (tIndex !== -1) {
    const datePart = dateStr.substring(0, tIndex)
    const timePart = dateStr.substring(tIndex + 1, tIndex + 6) // "HH:mm"
    return `${datePart} ${timePart}`
  }
  const spaceIndex = dateStr.indexOf(' ')
  if (spaceIndex !== -1 && dateStr.length > spaceIndex + 6) {
    return dateStr.substring(0, spaceIndex + 6) // up to "HH:mm"
  }
  return dateStr
}

function getDiaryRefs(refs: RagRef[]): RagRef[] {
  const seen = new Set<string>()
  return refs.filter(r => {
    if (r.type === 'profile_memory' || r.type === 'graph_memory' || !r.diaryId) return false
    if (seen.has(r.diaryId)) return false
    seen.add(r.diaryId)
    return true
  })
}

function getProfileRefs(refs: RagRef[]): RagRef[] {
  return refs.filter(r => r.type === 'profile_memory')
}

function getGraphRefs(refs: RagRef[]): RagRef[] {
  const seen = new Set<string>()
  return refs.filter(r => {
    if (r.type !== 'graph_memory') return false
    if (!r.snippet) return false
    if (seen.has(r.snippet)) return false
    seen.add(r.snippet)
    return true
  })
}

/** 流式面板用的计算属性 */
const streamingDiaryRefs = computed(() => getDiaryRefs(streamingRefs.value))
const streamingProfileRefs = computed(() => getProfileRefs(streamingRefs.value))
const streamingGraphRefs = computed(() => getGraphRefs(streamingRefs.value))

function toggleMsgRefs(msgId: string) {
  if (expandedRefs.has(msgId)) {
    expandedRefs.delete(msgId)
  } else {
    expandedRefs.add(msgId)
  }
}

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
  if (authStore.isAuthenticated && !authStore.userId) {
    authStore.fetchProfile()
  }
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
  await loadWelcomeTopics()

  const isNewSession = !sessionStorage.getItem('chatSessionInitialized')
  const storedConvId = sessionStorage.getItem('currentChatId')

  if (isNewSession) {
    sessionStorage.setItem('chatSessionInitialized', 'true')
    sessionStorage.removeItem('currentChatId')
    await createConversation()
  } else if (storedConvId) {
    const id = Number(storedConvId)
    if (conversations.value.some(c => c.id === id)) {
      await selectConversation(id)
    } else {
      await createConversation()
    }
  } else {
    // Session is initialized, but no conversation was started yet in this session.
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
  } catch (e) { logWarn('chat', '加载会话列表失败', e); conversations.value = [] }
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
  sessionStorage.setItem('currentChatId', String(id))
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
  } catch (e) {
    logWarn('chat', '同步消息失败', e)
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
  try {
    // 避免用户在会话创建尚未完成时把第一条消息发到旧会话里。
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
        ragReferences: Array.isArray(item.ragReferences) ? item.ragReferences : undefined,
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
  if (!content || streaming.value || creatingConversation.value) return

  if (!activeConvId.value) {
    creatingConversation.value = true
    try {
      await doCreateConversationOnServer()
    } catch (e) {
      logWarn('chat', '创建会话请求失败', e)
      creatingConversation.value = false
      return
    }
    creatingConversation.value = false
  }

  const convId = activeConvId.value
  if (!convId) return

  lastReplyError.value = null
  lastReplyRequest.value = null
  isThinkExpanded.value = false

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

  streamingRefs.value = []
  showStreamingRefs.value = false
  let fullReply = ''
  let currentRefs: RagRef[] = []

  try {
    await chatApi.replyStream(convId, content, refContents, (chunk) => {
      fullReply += chunk
      pendingStreamText = fullReply
      if (isThinking.value) {
        isThinking.value = false
      }
      if (streamRafId === null) {
        streamRafId = requestAnimationFrame(() => {
          const keepScroll = isNearBottom(msgBox.value)
          streamingText.value = pendingStreamText
          streamRafId = null
          if (keepScroll) {
            scrollBottom()
          }
        })
      }
    }, ctrl, (items: any) => {
      currentRefs = items
      streamingRefs.value = items
    }, (toolItems: any) => {
      currentRefs = [...currentRefs, ...toolItems]
      streamingRefs.value = currentRefs
    })

    // 流正常结束
    if (activeConvId.value !== convId) return
    lastReplyError.value = null
    lastReplyRequest.value = null
    messages.value.push({
      id: nextMsgId(),
      role: 'ai',
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
  streamingRefs.value = []
  isThinking.value = false
  references.value = []
  scrollBottom()
  try {
    await saveToBackend(convId)
  } catch (e) {
    logWarn('chat', '发送后保存历史失败', e)
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
  } catch (e) {
    recentDiaryOptions.value = []
    recentDiariesError.value = '加载最近日记失败'
    logWarn('chat', '加载最近日记选项失败', e)
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

/* 流式输出光标 */
:deep(.streaming-cursor) {
  animation: cursor-pulse 1.2s ease-in-out infinite;
  color: var(--color-primary);
  margin-left: 2px;
  vertical-align: baseline;
  display: inline-block;
}
@keyframes cursor-pulse {
  0%, 100% { opacity: 0.3; transform: scaleY(0.9); }
  50% { opacity: 1; transform: scaleY(1.1); }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ── RAG 引用折叠面板 ── */
.rag-refs-panel {
  margin: 6px 0;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-surface-soft, color-mix(in srgb, var(--color-primary) 3%, transparent));
  overflow: hidden;
  animation: refsIn 0.2s var(--ease-out);
}

/* 引用面板放在气泡上方 */
.rag-refs-above {
  margin: 0 0 8px 0;
  max-width: 85%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* 固定引用面板：脱离正文流式溢出，始终保持置顶 */
.rag-references-fixed {
  margin-bottom: 8px;
  padding: 8px 10px;
  background: var(--color-surface-soft, color-mix(in srgb, var(--color-primary) 3%, transparent));
  border-radius: 8px;
  border: none;
  width: fit-content;
  max-width: 100%;
}

/* 引用面板内的子项增加呼吸感 */
.rag-refs-list {
  gap: 2px;
}

.rag-ref-item {
  padding: 6px 12px;
}

.rag-ref-snippet {
  margin-right: 8px;
  max-width: 260px;
}

/* 展开面板中的画像项用 chip 风格横向排列 */
.rag-refs-list .rag-ref-item {
  max-width: 100%;
}

.rag-refs-list .rag-ref-snippet {
  max-width: 260px;
}

/* 消息内容容器：垂直堆叠，引用在上、气泡在下，shrink-wrap 到内容宽度 */
.msg-wrapper {
  display: flex;
  flex-direction: column;
  max-width: 88%;
}

@keyframes refsIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.rag-refs-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 4px 8px;
  border: none;
  background: none;
  color: var(--color-text-muted);
  font-size: 10px;
  font-weight: 500;
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
  display: flex;
  flex-direction: column;
  gap: 0;
  border-top: 1px solid color-mix(in srgb, var(--color-border) 30%, transparent 70%);
  padding: 2px 0;
}

.rag-refs-section-label {
  padding: 2px 10px 0;
  font-size: 9px;
  font-weight: 600;
  color: var(--color-text-muted);
  letter-spacing: 0.03em;
}

/* 日记引用行：日期标签 | 摘要 | 跳转箭头 */
.rag-ref-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  font-size: 11px;
}

.rag-ref-item:not(:last-child) {
  border-bottom: 1px solid color-mix(in srgb, var(--color-border) 25%, transparent 75%);
}

.rag-ref-clickable {
  cursor: pointer;
  transition: background 0.12s;
}

.rag-ref-clickable:hover {
  background: var(--color-surface-hover);
}

/* 画像条目：简单横排，无额外列 */
.rag-ref-item-profile {
  display: flex;
  align-items: center;
  padding: 5px 10px;
  font-size: 11px;
  cursor: pointer;
  transition: background 0.12s;
  position: relative;
}

.rag-ref-item-profile:not(:last-child) {
  border-bottom: 1px solid color-mix(in srgb, var(--color-border) 25%, transparent 75%);
}

.rag-ref-item-profile:hover {
  background: var(--color-surface-hover);
}

.rag-ref-meta {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  min-width: 56px;
}

.rag-ref-date {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  font-size: 10px;
}

.rag-ref-tool-badge {
  background: color-mix(in srgb, var(--color-primary) 10%, transparent);
  color: var(--color-primary);
  border-radius: 3px;
  padding: 0 3px;
  font-size: 8px;
  font-weight: 600;
  white-space: nowrap;
}

:deep(.think-block) {
  margin: 10px 0;
  padding: 8px 10px;
  background-color: transparent;
  border-radius: 6px;
  border-left: 2px solid color-mix(in srgb, var(--color-text-muted) 30%, transparent);
  font-size: var(--text-xs);
  color: color-mix(in srgb, var(--color-text-secondary) 80%, transparent);
}

:deep(.think-block summary) {
  cursor: pointer;
  font-weight: 500;
  font-size: var(--text-xs);
  color: color-mix(in srgb, var(--color-text-muted) 80%, transparent);
  user-select: none;
  outline: none;
}

.streaming-md :deep(.think-block:last-of-type summary::after) {
  content: '';
  animation: typing-dots 1.5s infinite;
  display: inline-block;
  width: 1em;
  text-align: left;
}

:deep(.think-content) {
  margin-top: 6px;
  padding-left: 2px;
  border-top: 1px dashed color-mix(in srgb, var(--color-border) 40%, transparent);
  padding-top: 6px;
}

:deep(.think-content p) {
  font-size: var(--text-xs);
  color: color-mix(in srgb, var(--color-text-secondary) 80%, transparent);
  margin-bottom: 0.5em;
  line-height: 1.6;
}

.rag-ref-snippet {
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  transition: all 0.2s ease;
}

.rag-ref-key {
  font-weight: 600;
  margin-right: 2px;
}

.rag-ref-snippet.expanded {
  white-space: normal;
  display: block;
  max-width: 360px;
  background: var(--color-surface);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  z-index: 10;
  position: relative;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--color-border);
}

/* ── 消息对齐：AI 靠左，用户靠右 ── */
.msg-item {
  display: flex;
  width: 100%;
  margin-bottom: 20px !important;
  gap: 12px;
}

.msg-item.ai {
  justify-content: flex-start !important;
}

.msg-item.user {
  justify-content: flex-end !important;
}

.msg-wrapper {
  display: flex;
  flex-direction: column;
  max-width: 85% !important;
}

.msg-item.ai .msg-wrapper {
  max-width: 92% !important;
}

.msg-item.ai .msg-wrapper {
  align-items: flex-start !important;
}

.msg-item.user .msg-wrapper {
  align-items: flex-end !important;
}

/* ── 引用卡片悬浮展开 (chip 风格) ── */
.rag-ref-chip {
  cursor: pointer;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
  transition: all 0.2s ease;
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rag-ref-chip.expanded {
  max-width: 400px;
  white-space: normal;
  background: var(--color-surface);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  z-index: 10;
  position: relative;
}

.rag-ref-go {
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.thinking-status {
  display: flex;
  align-items: center;
  gap: 6px;
  color: color-mix(in srgb, var(--color-text-secondary) 80%, transparent);
  font-size: var(--text-sm);
  padding: 8px 12px;
  background-color: transparent;
  border-radius: 8px;
  margin-bottom: 8px;
}

/* 深度思考动效中的行内跳点容器 */
.thinking-dots-inline {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: 2px;
}

/* AI 消息只有 think 无正文时的兜底占位 */
.ai-think-placeholder {
  color: var(--color-text-muted);
  font-size: 13px;
  opacity: 0.5;
  letter-spacing: 0.1em;
}

.sparkle-icon {
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% { opacity: 0.4; }
  50% { opacity: 1; }
  100% { opacity: 0.4; }
}

/* 沉思加载动画样式 */
.thinking-bubble {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  color: var(--color-primary, #4a7c62);
  font-weight: 500;
}

.thinking-dots-loader {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 8px;
  padding-left: 2px;
}

.thinking-dots-loader .dot {
  width: 6px;
  height: 6px;
  background-color: var(--color-primary, #4a7c62);
  border-radius: 50%;
}

/* 快捷对话建议卡片 */
.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  flex: 1;
  text-align: center;
  align-self: center;
  width: 100%;
  max-width: 560px;
  box-sizing: border-box;
}

.chat-header-title {
  font-size: 28px;
  color: var(--color-primary, #4a7c62);
  margin: 0;
  font-weight: 700;
}

.chat-subtitle {
  font-size: 14px;
  color: var(--color-text-muted, #8a7a6a);
  margin: 8px 0 24px;
}

.chat-quick-starters {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  max-width: 560px;
  width: 100%;
  margin-top: 10px;
}

.quick-starter-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
  padding: 16px;
  background: rgba(255, 255, 255, 0.45);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(180, 150, 120, 0.15);
  border-radius: var(--radius-lg, 16px);
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  font-family: inherit;
}

.quick-starter-card:hover {
  transform: translateY(-2px);
  border-color: var(--color-primary, #4a7c62);
  background: rgba(255, 255, 255, 0.7);
  box-shadow: 0 6px 20px rgba(74, 124, 98, 0.06);
}

.quick-starter-card:active {
  transform: translateY(0);
}

.starter-icon {
  font-size: 20px;
  margin-bottom: 8px;
}

.starter-text {
  font-size: 13.5px;
  color: #4a5a4e;
  line-height: 1.4;
  font-weight: 500;
}

/* Skeleton Loading Styles */
.skeleton-card {
  cursor: default;
}
.skeleton-card:hover {
  transform: none;
  box-shadow: none;
  background: rgba(255, 255, 255, 0.45);
  border-color: rgba(180, 150, 120, 0.15);
}

.skeleton-icon {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  margin-bottom: 8px;
  background: linear-gradient(90deg, #f0f0f0 25%, #fafafa 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite linear;
}

.skeleton-text {
  width: 80%;
  height: 14px;
  border-radius: 4px;
  margin-top: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #fafafa 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite linear;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@media (max-width: 600px) {
  .chat-quick-starters {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .quick-starter-card {
    padding: 12px 14px;
    flex-direction: row;
    align-items: center;
    gap: 12px;
  }
  .starter-icon {
    margin-bottom: 0;
  }
}

/* --- Premium Custom Styles --- */

.chat-messages {
  gap: 20px !important;
  padding: 24px !important;
  background: #fcfbfa !important;
  border: 1px solid rgba(180, 150, 120, 0.15) !important;
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.01);
}

.chat-messages::after {
  content: '';
  display: block;
  min-height: 24px;
  flex-shrink: 0;
}

.chat-input-area {
  background: rgba(255, 255, 255, 0.6) !important;
  backdrop-filter: blur(12px) !important;
  -webkit-backdrop-filter: blur(12px) !important;
  border: 1px solid rgba(180, 150, 120, 0.15) !important;
  border-radius: 16px !important;
  padding: 12px 16px !important;
  box-shadow: 0 8px 30px rgba(74, 124, 98, 0.05) !important;
  transition: all 0.3s ease;
}

.chat-input-area:focus-within {
  border-color: var(--color-primary, #4a7c62) !important;
  box-shadow: 0 8px 30px rgba(74, 124, 98, 0.08), 0 0 0 2px rgba(74, 124, 98, 0.1) !important;
}

.chat-input-row {
  gap: 12px !important;
}

.chat-input-row :deep(.n-input) {
  --n-border-radius: 12px !important;
  --n-border: 1px solid rgba(180, 150, 120, 0.2) !important;
  --n-border-hover: 1px solid var(--color-primary, #4a7c62) !important;
  --n-border-focus: 1px solid var(--color-primary, #4a7c62) !important;
  --n-box-shadow-focus: 0 0 0 2px rgba(74, 124, 98, 0.1) !important;
  background: #ffffff !important;
}

.chat-input-row :deep(.n-button) {
  --n-border-radius: 12px !important;
  height: 40px !important;
  padding: 0 20px !important;
  font-weight: 600 !important;
  transition: all 0.2s ease !important;
}

.chat-input-row :deep(.n-button:not([disabled]):hover) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(74, 124, 98, 0.2);
}

.rag-references-fixed {
  background: rgba(244, 247, 245, 0.8) !important;
  backdrop-filter: blur(4px);
  border: 1px solid rgba(74, 124, 98, 0.08) !important;
  border-radius: 12px !important;
  padding: 6px 10px !important;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
  font-family: inherit;
  box-sizing: border-box;
}

.msg-avatar.user-avatar {
  background: linear-gradient(135deg, #8ba897, #5f836f);
  color: #ffffff;
  font-weight: 600;
  font-size: 13px;
  box-shadow: 0 4px 10px rgba(95, 131, 111, 0.15);
  border: 1.5px solid #ffffff;
  overflow: hidden;
}

.msg-avatar.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.msg-avatar.ai-avatar {
  background: #ffffff;
  border: 1.5px solid rgba(74, 124, 98, 0.15);
  box-shadow: 0 4px 10px rgba(74, 124, 98, 0.08);
  padding: 4px;
  overflow: hidden;
}

.msg-avatar.ai-avatar .ai-avatar-icon {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.chat-bubble {
  max-width: 100% !important;
  padding: 12px 18px !important;
  font-size: 14.5px !important;
  line-height: 1.6 !important;
  letter-spacing: 0.01em;
  box-sizing: border-box;
}

.chat-bubble.chat-user {
  background: linear-gradient(135deg, var(--color-primary, #4a7c62), #3a6851) !important;
  color: #ffffff !important;
  border: none !important;
  border-radius: 18px 18px 4px 18px !important;
  box-shadow: 0 4px 14px rgba(74, 124, 98, 0.18) !important;
}

.chat-bubble.chat-user p {
  margin: 0;
  color: #ffffff !important;
}

.chat-bubble.chat-ai {
  background: #ffffff !important;
  color: #2c3e35 !important;
  border: 1px solid rgba(74, 124, 98, 0.08) !important;
  border-radius: 18px 18px 18px 4px !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04) !important;
}

.chat-user-refs {
  margin: 8px 0 0;
  padding-left: 12px;
  font-size: 11.5px;
  line-height: 1.5;
  border-left: 2px solid rgba(255, 255, 255, 0.4);
  color: rgba(255, 255, 255, 0.85);
  list-style-type: none;
}

.chat-user-refs li {
  margin-bottom: 2px;
}

:deep(.think-block) {
  margin: 4px 0 12px 0;
  padding: 10px 12px;
  background-color: rgba(74, 124, 98, 0.03);
  border-radius: 8px;
  border-left: 3px solid rgba(74, 124, 98, 0.25);
  font-family: Consolas, Monaco, "Andale Mono", monospace;
  font-size: 12.5px;
  color: #55685f;
}

:deep(.think-block summary) {
  cursor: pointer;
  font-weight: 600;
  font-size: 12px;
  color: #4a7c62;
  user-select: none;
  outline: none;
  margin-bottom: 4px;
}

:deep(.think-content) {
  margin-top: 8px;
  border-top: 1px dashed rgba(74, 124, 98, 0.15);
  padding-top: 8px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.md-content :deep(p) {
  margin-bottom: 8px !important;
}

.md-content :deep(p:last-child) {
  margin-bottom: 0 !important;
}

.md-content :deep(pre) {
  background: #f7f6f2 !important;
  border: 1px solid rgba(180, 150, 120, 0.12) !important;
}
</style>
