import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type AiPreviewEntry =
  | { id: string; kind: 'DIARY_ANALYSIS'; diary: any }
  | { id: string; kind: 'MEMORY_CONSOLIDATION'; items: any[] }
  | { id: string; kind: 'GRAPH_CONSOLIDATION'; triples: any[] }

export type AiPreviewEntryInput =
  | { kind: 'DIARY_ANALYSIS'; diary: any }
  | { kind: 'MEMORY_CONSOLIDATION'; items: any[] }
  | { kind: 'GRAPH_CONSOLIDATION'; triples: any[] }

export const useAiPreviewStore = defineStore('aiPreview', () => {
  const stack = ref<AiPreviewEntry[]>([])
  const current = computed(() => stack.value[stack.value.length - 1] || null)

  function enqueue(entry: AiPreviewEntryInput) {
    stack.value.push({ ...entry, id: `${entry.kind}-${Date.now()}-${Math.random().toString(36).slice(2)}` } as AiPreviewEntry)
  }

  function pop() {
    stack.value.pop()
  }

  return { stack, current, enqueue, pop }
})
