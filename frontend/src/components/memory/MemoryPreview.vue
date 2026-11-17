<template>
  <div class="memory-preview">
    <div class="memory-preview-heading">
      <strong class="memory-main-phrase">{{ phrase }}</strong>
      <span v-if="status" class="memory-status-tag" :class="{ 'memory-status-recent': status === '近期状态' }">{{ status }}</span>
    </div>
    <p v-if="remainder" class="memory-preview-summary">{{ remainder }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ value: string; status?: string }>()
// Presentation only: both portions are consecutive slices of the original text.
// No generated title, factual rewrite or persisted change.

// 短文本不切：一句「番茄（闻到味道就反胃，很排斥）」被拆成粗体头 + 灰色尾巴，
// 读起来像断掉的两截。只有足够长的记忆才值得分出主句和补充。
const MIN_SPLIT_LENGTH = 36
const MIN_REMAINDER = 8
// 优先在句末断开；逗号次之，且不能切在太靠后的位置，否则头重脚轻。
const STRONG_BOUNDARY = /[。；！？\n]/u
const WEAK_BOUNDARY = /[，、]/u

function cutAt(text: string, pattern: RegExp, maxIndex: number): number {
  const index = text.search(pattern)
  return index > 0 && index <= maxIndex ? index + 1 : -1
}

const parts = computed(() => {
  const text = props.value || ''
  if (Array.from(text).length < MIN_SPLIT_LENGTH) return [text, '']

  let cut = cutAt(text, STRONG_BOUNDARY, 48)
  if (cut < 0) cut = cutAt(text, WEAK_BOUNDARY, 32)
  if (cut < 0 || cut >= text.length) return [text, '']

  const head = text.slice(0, cut)
  const rest = text.slice(cut)
  if (Array.from(rest).length < MIN_REMAINDER) return [text, '']
  return [head, rest]
})
const phrase = computed(() => parts.value[0])
const remainder = computed(() => parts.value[1])
</script>

<style scoped>
.memory-preview { min-width: 0; }
.memory-preview-heading { display: flex; align-items: flex-start; gap: var(--space-3); }
.memory-main-phrase { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; min-width: 0; flex: 1; color: var(--color-text); font: 600 16px/1.7 var(--font-body); overflow-wrap: anywhere; }
.memory-preview-summary { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; margin: 2px 0 0; color: var(--color-text-secondary); font: 400 15px/1.7 var(--font-body); white-space: pre-wrap; overflow-wrap: anywhere; }
.memory-status-tag { flex-shrink: 0; padding: 2px 7px; border: 1px solid color-mix(in oklab, var(--color-accent) 35%, var(--color-border)); border-radius: var(--radius-sm); color: color-mix(in oklab, var(--color-accent) 55%, var(--color-text)); background: var(--color-accent-bg); font: 500 12px/1.6 var(--font-body); }
.memory-status-recent { color: var(--color-text); background: color-mix(in oklab, var(--color-warning) 12%, var(--color-surface)); border-color: color-mix(in oklab, var(--color-warning) 45%, var(--color-border)); }
</style>
