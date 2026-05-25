<template>
  <div v-if="streaming && (streamingText || streamingRefs.length)" class="msg-item ai">
    <div class="msg-avatar ai-avatar">
      <svg class="ai-avatar-icon" xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64" fill="none">
        <rect x="14" y="11" width="36" height="42" rx="8" stroke="currentColor" stroke-width="4"/>
        <path d="M24 11V53" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
        <path d="M32 38C26.6 33.8 24 31.1 24 27.5C24 24.95 26 23 28.6 23C30.1 23 31.55 23.68 32.5 24.76C33.45 23.68 34.9 23 36.4 23C39 23 41 24.95 41 27.5C41 31.1 38.4 33.8 33 38L32.5 38.4L32 38Z" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
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
          <template v-if="streamingProfileRefs.length">
            <div class="rag-refs-section-label">🧠 个人画像</div>
            <div v-for="(ref, i) in streamingProfileRefs" :key="'sp'+i"
                 class="rag-ref-item-profile"
                 @click="toggleSnippet(i)">
              <span :class="['rag-ref-snippet', { 'expanded': isSnippetExpanded(i) }]" :title="ref.snippet || ref.value">
                <span v-if="ref.key" class="rag-ref-key">【{{ ref.key }}】</span>{{ ref.snippet || ref.value }}
              </span>
            </div>
          </template>
          <template v-if="streamingGraphRefs.length">
            <div class="rag-refs-section-label">🕸️ 关系图谱</div>
            <div
              v-for="(ref, i) in streamingGraphRefs"
              :key="'sg'+i"
              class="rag-ref-item-profile"
              @click="toggleSnippet(1000 + i)"
            >
              <div class="rag-ref-meta">
                <span v-if="ref.date" class="rag-ref-date">{{ formatRefDate(ref.date) }}</span>
                <span v-if="ref.toolName" class="rag-ref-tool-badge">{{ toolLabel(ref.toolName) }}</span>
              </div>
              <span :class="['rag-ref-snippet', { 'expanded': isSnippetExpanded(1000 + i) }]" :title="ref.snippet">{{ ref.snippet }}</span>
            </div>
          </template>
        </div>
      </div>

      <div class="chat-bubble chat-ai">
        <div v-if="parsedStreaming.think && !parsedStreaming.text" class="thinking-status">
          <span class="sparkle-icon">✨</span>
          <span class="thinking-text">深度思考中</span>
          <span class="thinking-dots-inline">
            <span class="dot animate-bounce" style="animation-delay: 0ms"></span>
            <span class="dot animate-bounce" style="animation-delay: 150ms"></span>
            <span class="dot animate-bounce" style="animation-delay: 300ms"></span>
          </span>
        </div>

        <div v-if="!parsedStreaming.text && !parsedStreaming.think" class="thinking-status">
          <span class="sparkle-icon">✨</span>
          <span class="thinking-text">MoodCopilot 正在思考</span>
          <span class="typing-dots"></span>
        </div>

        <div v-if="parsedStreaming.text" class="md-content streaming-md" v-html="renderStreamingMd(parsedStreaming.text, true)" />
      </div>
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

const props = defineProps<{
  streaming: boolean
  streamingText: string
  streamingRefs: RagRef[]
}>()

defineEmits<{
  (e: 'go-diary', diaryId: string | number): void
}>()

const showStreamingRefs = ref(false)
const expandedSnippets = ref<Set<number>>(new Set())

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

function renderStreamingMd(text: string, showCursor: boolean) {
  const processed = showCursor ? text + '<span class="streaming-cursor">▋</span>' : text
  return renderMd(processed)
}

const parsedStreaming = computed(() => {
  const content = props.streamingText
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
const streamingDiaryRefs = computed(() => {
  const seen = new Set<string>()
  return (props.streamingRefs || []).filter(r => {
    if (r.type === 'profile_memory' || r.type === 'graph_memory' || !r.diaryId) return false
    if (seen.has(r.diaryId)) return false
    seen.add(r.diaryId)
    return true
  })
})

const streamingProfileRefs = computed(() => {
  return (props.streamingRefs || []).filter(r => r.type === 'profile_memory')
})

const streamingGraphRefs = computed(() => {
  const seen = new Set<string>()
  return (props.streamingRefs || []).filter(r => {
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
