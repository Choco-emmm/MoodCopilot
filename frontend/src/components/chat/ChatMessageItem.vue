<template>
  <div :class="['msg-item', msg.role]">
    <!-- AI Avatar -->
    <div v-if="msg.role === 'ai'" class="msg-avatar ai-avatar">
      <svg class="ai-avatar-icon" xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64" fill="none">
        <rect x="14" y="11" width="36" height="42" rx="8" stroke="currentColor" stroke-width="4"/>
        <path d="M24 11V53" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
        <path d="M32 38C26.6 33.8 24 31.1 24 27.5C24 24.95 26 23 28.6 23C30.1 23 31.55 23.68 32.5 24.76C33.45 23.68 34.9 23 36.4 23C39 23 41 24.95 41 27.5C41 31.1 38.4 33.8 33 38L32.5 38.4L32 38Z" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </div>

    <div class="msg-wrapper">
      <div v-if="msg.role === 'ai' && msg.ragReferences?.length" class="rag-refs-panel rag-refs-above rag-references-fixed">
        <button class="rag-refs-toggle" @click="toggleRefs">
          <span class="rag-refs-icon">🔍</span>
          <span>已检索 {{ diaryRefs.length }} 条记录</span>
          <span v-if="profileRefs.length"> · {{ profileRefs.length }} 条画像</span>
          <span v-if="graphRefs.length"> · {{ graphRefs.length }} 条图谱</span>
          <span class="rag-refs-arrow">{{ isRefsExpanded ? '▾' : '▸' }}</span>
        </button>
        <div v-if="isRefsExpanded" class="rag-refs-list">
          <template v-if="diaryRefs.length">
            <div class="rag-refs-section-label">📝 日记记忆</div>
            <div
              v-for="(ref, i) in diaryRefs"
              :key="'d'+i"
              :class="['rag-ref-item', { 'rag-ref-clickable': ref.diaryId && String(ref.diaryId) !== '-1' }]"
              @click="String(ref.diaryId) !== '-1' && ref.diaryId && $emit('go-diary', ref.diaryId)"
            >
              <div class="rag-ref-meta">
                <span class="rag-ref-date">{{ formatRefDate(ref.date) }}</span>
                <span v-if="ref.toolName" class="rag-ref-tool-badge">{{ toolLabel(ref.toolName) }}</span>
              </div>
              <span class="rag-ref-snippet" :title="ref.snippet">{{ ref.snippet }}</span>
              <span v-if="ref.diaryId && String(ref.diaryId) !== '-1'" class="rag-ref-go">→</span>
            </div>
          </template>
          <template v-if="profileRefs.length">
            <div class="rag-refs-section-label">🧠 个人画像</div>
            <div v-for="(ref, i) in profileRefs" :key="'p'+i"
                 class="rag-ref-item-profile"
                 @click="toggleSnippet(i)">
              <span :class="['rag-ref-snippet', { 'expanded': isSnippetExpanded(i) }]" :title="ref.snippet || ref.value">
                <span v-if="ref.key" class="rag-ref-key">【{{ ref.key }}】</span>{{ ref.snippet || ref.value }}
              </span>
            </div>
          </template>
          <template v-if="graphRefs.length">
            <div class="rag-refs-section-label">🕸️ 关系图谱</div>
            <div
              v-for="(ref, i) in graphRefs"
              :key="'g'+i"
              class="rag-ref-item-profile"
              @click="toggleSnippet(1000 + i)"
            >
              <span :class="['rag-ref-snippet', { 'expanded': isSnippetExpanded(1000 + i) }]" :title="ref.snippet">{{ ref.snippet }}</span>
            </div>
          </template>
        </div>
      </div>

      <div
        :class="['chat-bubble', msg.role === 'user' ? 'chat-user' : 'chat-ai']"
      >
        <template v-if="msg.role === 'ai'">
          <!-- think 块内容不对用户展示，只显示正文 -->
          <div v-if="parsedContent.text" class="md-content" v-html="renderMd(parsedContent.text)" />
          <!-- 如果只有 think 没有正文（消息异常时的兜底） -->
          <span v-else class="ai-think-placeholder">...</span>

          <!-- Quote Action -->
          <div v-if="parsedContent.text" class="msg-actions">
            <button class="msg-action-btn" title="引用此回复" @click="handleQuote">
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"></path><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"></path></svg>
              <span>引用</span>
            </button>
          </div>
        </template>
        <template v-else>
          <div class="md-content" v-html="renderMd(msg.content)" />
          
          <!-- Quote Action -->
          <div class="msg-actions msg-actions-user">
            <button class="msg-action-btn" title="引用此回复" @click="handleQuote">
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"></path><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"></path></svg>
              <span>引用</span>
            </button>
          </div>
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
      <img v-if="userAvatar" :src="userAvatar" :alt="userInitial" />
      <span v-else>{{ userInitial }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { renderSafeMarkdown } from '../../utils/markdown'

interface RagRef {
  type: string
  diaryId?: string
  date?: string
  snippet?: string
  toolName?: string
  value?: string
  key?: string
}

export interface Message {
  id: string
  role: 'user' | 'ai'
  content: string
  references?: string[]
  ragReferences?: RagRef[]
}

const props = defineProps<{
  msg: Message
  userAvatar?: string | null
  userInitial: string
}>()

const emit = defineEmits<{
  (e: 'go-diary', diaryId: string | number): void
  (e: 'quote', data: { text: string; role: 'user' | 'ai' }): void
}>()

const isRefsExpanded = ref(false)
const expandedSnippets = ref<Set<number>>(new Set())

function toggleRefs() {
  isRefsExpanded.value = !isRefsExpanded.value
}

function toggleSnippet(idx: number) {
  if (expandedSnippets.value.has(idx)) {
    expandedSnippets.value.delete(idx)
  } else {
    expandedSnippets.value.add(idx)
  }
}

function isSnippetExpanded(idx: number) {
  return expandedSnippets.value.has(idx)
}

const mdCache = new Map<string, string>()
function renderMd(text: string) {
  let cached = mdCache.get(text)
  if (cached) return cached
  cached = renderSafeMarkdown(text)
  mdCache.set(text, cached)
  return cached
}

function handleQuote() {
  const text = parsedContent.value.text || props.msg.content
  if (!text) return
  // 去除所有 markdown 标签（粗糙处理），将多行合并
  const plainText = text.replace(/[#*`_~>\[\]\(\)-]/g, '').replace(/\n+/g, ' ').trim()
  const snippet = plainText.length > 80 ? plainText.slice(0, 80) + '...' : plainText
  emit('quote', { text: snippet, role: props.msg.role })
}

const parsedContent = computed(() => {
  const content = props.msg.content
  if (!content) return { think: '', text: '' }

  let think = ''
  let text = content.replace(/<think>([\s\S]*?)<\/think>/g, (match, innerThink) => {
    think += (think ? '\n\n' : '') + innerThink.trim()
    return ''
  })

  const unclosedMatch = text.match(/<think>([\s\S]*)$/)
  if (unclosedMatch) {
    think += (think ? '\n\n' : '') + unclosedMatch[1].trim()
    text = text.substring(0, unclosedMatch.index)
  }

  return {
    think: think.trim(),
    text: text.trimStart()
  }
})

// Filters
const diaryRefs = computed(() => {
  const seen = new Set<string>()
  return (props.msg.ragReferences || []).filter(r => {
    if (r.type === 'profile_memory' || r.type === 'graph_memory' || !r.diaryId) return false
    if (seen.has(r.diaryId)) return false
    seen.add(r.diaryId)
    return true
  })
})

const profileRefs = computed(() => {
  return (props.msg.ragReferences || []).filter(r => r.type === 'profile_memory')
})

const graphRefs = computed(() => {
  const seen = new Set<string>()
  return (props.msg.ragReferences || []).filter(r => {
    if (r.type !== 'graph_memory') return false
    if (!r.snippet) return false
    if (seen.has(r.snippet)) return false
    seen.add(r.snippet)
    return true
  })
})

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
    const timePart = dateStr.substring(tIndex + 1, tIndex + 6)
    return `${datePart} ${timePart}`
  }
  const spaceIndex = dateStr.indexOf(' ')
  if (spaceIndex !== -1 && dateStr.length > spaceIndex + 6) {
    return dateStr.substring(0, spaceIndex + 6)
  }
  return dateStr
}
</script>
