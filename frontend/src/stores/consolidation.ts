import { defineStore } from 'pinia'
import { ref } from 'vue'
import { memoryApi, graphApi } from '../api'
import { logWarn } from '../utils/logger'
import { useAiPreviewStore } from './aiPreview'

export const useConsolidationStore = defineStore('consolidation', () => {
  const aiPreviewStore = useAiPreviewStore()
  // Data State
  const memories = ref<any[]>([])
  const triples = ref<any[]>([])

  // Memory Consolidation State
  const consolidatingMemory = ref(false)
  const showMemoryPreviewModal = ref(false)
  const previewMemories = ref<any[]>([])
  const applyingMemory = ref(false)

  // Graph Consolidation State
  const consolidatingGraph = ref(false)
  const showGraphPreviewModal = ref(false)
  const previewTriples = ref<any[]>([])
  const applyingGraph = ref(false)

  // Loading messages references
  let memoryLoadingMsg: any = null
  let graphLoadingMsg: any = null

  async function loadMemories() {
    try {
      const res = await memoryApi.getAll()
      memories.value = (res.data.data ?? []) as any[]
    } catch (e) {
      logWarn('memory', '加载记忆失败', e)
      memories.value = []
    }
  }

  async function loadTriples() {
    try {
      const res = await graphApi.getTriples()
      triples.value = res.data.data || []
    } catch (err) {
      logWarn('graph', '加载图谱数据失败', err)
      triples.value = []
    }
  }

  async function consolidateMemories() {
    if (consolidatingMemory.value) return
    consolidatingMemory.value = true
    await loadMemories() // Ensure old data is loaded for diffing
    memoryLoadingMsg = window.$message?.info('已开始整理个人画像，完成后会通知你。', { duration: 3500 })
    try {
      const res = await memoryApi.previewConsolidate()
      const taskId = res.data.data?.taskId
      if (!taskId) throw new Error('未能创建整理任务')
      const result = await waitForMemoryTask(taskId)
      previewMemories.value = result
      if (previewMemories.value.length === 0) {
        window.$message?.info('当前没有可以安全整理的重复记忆')
        if (memoryLoadingMsg) memoryLoadingMsg.destroy()
        return
      }
      if (memoryLoadingMsg) memoryLoadingMsg.destroy()
      aiPreviewStore.enqueue({ kind: 'MEMORY_CONSOLIDATION', items: previewMemories.value })
    } catch (err: any) {
      if (memoryLoadingMsg) memoryLoadingMsg.destroy()
      if (err.response?.status === 429 || (err.response?.data?.message && err.response.data.message.includes('每天最多只能进行20次个人画像整理'))) {
        alert('每天最多只能进行20次个人画像整理，请明天再试吧')
      } else {
        logWarn('memory', '记忆预览失败', err)
        alert('记忆整理失败：' + (err.response?.data?.message || err.message))
      }
    } finally {
      consolidatingMemory.value = false
    }
  }

  async function applyMemoryConsolidation(items = previewMemories.value, onSuccess?: () => void): Promise<boolean> {
    applyingMemory.value = true
    try {
      await memoryApi.applyConsolidate(items)
      window.$message?.success('长久记忆画像已重构成功！')
      await loadMemories()
      if (onSuccess) onSuccess()
      return true
    } catch (err: any) {
      logWarn('memory', '应用整合失败', err)
      window.$message?.error('应用失败：' + (err.response?.data?.message || err.message))
      return false
    } finally {
      applyingMemory.value = false
    }
  }

  async function consolidateGraph() {
    if (consolidatingGraph.value) return
    consolidatingGraph.value = true
    await loadTriples() // Ensure old data is loaded for diffing
    graphLoadingMsg = window.$message?.info('已开始整理知识图谱，完成后会通知你。', { duration: 3500 })
    try {
      const res = await graphApi.previewConsolidate()
      const taskId = res.data.data?.taskId
      if (!taskId) throw new Error('未能创建整理任务')
      const result = await waitForGraphTask(taskId)
      previewTriples.value = result
      if (previewTriples.value.length === 0) {
        window.$message?.info('当前没有可以安全整理的重复关系')
        if (graphLoadingMsg) graphLoadingMsg.destroy()
        return
      }
      if (graphLoadingMsg) graphLoadingMsg.destroy()
      aiPreviewStore.enqueue({ kind: 'GRAPH_CONSOLIDATION', triples: previewTriples.value })
    } catch (err: any) {
      if (graphLoadingMsg) graphLoadingMsg.destroy()
      if (err.response?.status === 429 || (err.response?.data?.message && err.response.data.message.includes('每天最多只能进行2次关系图谱整理'))) {
        alert('每天最多只能进行2次关系图谱整理，请明天再试吧')
      } else {
        logWarn('graph', '图谱整理失败', err)
        alert('图谱整理失败：' + (err.response?.data?.message || err.message))
      }
    } finally {
      consolidatingGraph.value = false
    }
  }

  async function waitForMemoryTask(taskId: string): Promise<any[]> {
    for (let attempt = 0; attempt < 90; attempt += 1) {
      const res = await memoryApi.consolidationTask(taskId)
      const data = res.data.data || {}
      if (data.status === 'SUCCEEDED') return data.items || []
      if (data.status === 'DEAD_LETTER' || data.status === 'CANCELLED') {
        throw new Error(data.error || '记忆整理失败，请稍后重试')
      }
      await new Promise(resolve => window.setTimeout(resolve, 2000))
    }
    throw new Error('整理时间较长，请稍后在记忆页面重新查看')
  }

  async function waitForGraphTask(taskId: string): Promise<any[]> {
    for (let attempt = 0; attempt < 90; attempt += 1) {
      const res = await graphApi.consolidationTask(taskId)
      const data = res.data.data || {}
      if (data.status === 'SUCCEEDED') return data.triples || []
      if (data.status === 'DEAD_LETTER' || data.status === 'CANCELLED') {
        throw new Error(data.error || '知识图谱整理失败，请稍后重试')
      }
      await new Promise(resolve => window.setTimeout(resolve, 2000))
    }
    throw new Error('整理时间较长，请稍后在图谱页面重新查看')
  }

  async function applyGraphConsolidation(triplesToApply = previewTriples.value, onSuccess?: () => void): Promise<boolean> {
    applyingGraph.value = true
    try {
      await graphApi.applyConsolidate(triplesToApply)
      window.$message?.success('图谱关系已更新！')
      await loadTriples()
      if (onSuccess) onSuccess()
      return true
    } catch (err: any) {
      logWarn('graph', '应用图谱整合失败', err)
      window.$message?.error('图谱更新失败：' + (err.response?.data?.message || err.message))
      return false
    } finally {
      applyingGraph.value = false
    }
  }

  return {
    memories,
    triples,
    loadMemories,
    loadTriples,

    consolidatingMemory,
    showMemoryPreviewModal,
    previewMemories,
    applyingMemory,
    consolidateMemories,
    applyMemoryConsolidation,

    consolidatingGraph,
    showGraphPreviewModal,
    previewTriples,
    applyingGraph,
    consolidateGraph,
    applyGraphConsolidation
  }
})
