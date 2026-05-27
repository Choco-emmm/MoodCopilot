import { defineStore } from 'pinia'
import { ref } from 'vue'
import { memoryApi, graphApi } from '../api'
import { logWarn } from '../utils/logger'

export const useConsolidationStore = defineStore('consolidation', () => {
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
    memoryLoadingMsg = window.$message?.loading('MoodCopilot 正在努力整理个人画像中，由于数据量较大可能需要较长时间，你可以先去其他页面转转~', { duration: 0 })
    try {
      const res = await memoryApi.previewConsolidate()
      previewMemories.value = res.data.data || []
      if (previewMemories.value.length === 0) {
        window.$message?.warning('未能提取出有效的整合结果，请检查日记数量')
        if (memoryLoadingMsg) memoryLoadingMsg.destroy()
        return
      }
      if (memoryLoadingMsg) memoryLoadingMsg.destroy()
      showMemoryPreviewModal.value = true
    } catch (err: any) {
      if (memoryLoadingMsg) memoryLoadingMsg.destroy()
      if (err.response?.status === 429 || (err.response?.data?.message && err.response.data.message.includes('每天最多只能进行2次个人画像整理'))) {
        alert('每天最多只能进行2次个人画像整理，请明天再试吧')
      } else {
        logWarn('memory', '记忆预览失败', err)
        alert('记忆整理失败：' + (err.response?.data?.message || err.message))
      }
    } finally {
      consolidatingMemory.value = false
    }
  }

  async function applyMemoryConsolidation(onSuccess?: () => void) {
    applyingMemory.value = true
    try {
      await memoryApi.applyConsolidate(previewMemories.value)
      showMemoryPreviewModal.value = false
      window.$message?.success('长久记忆画像已重构成功！')
      await loadMemories()
      if (onSuccess) onSuccess()
    } catch (err: any) {
      logWarn('memory', '应用整合失败', err)
      window.$message?.error('应用失败：' + (err.response?.data?.message || err.message))
    } finally {
      applyingMemory.value = false
    }
  }

  async function consolidateGraph() {
    if (consolidatingGraph.value) return
    consolidatingGraph.value = true
    graphLoadingMsg = window.$message?.loading('MoodCopilot 正在努力整理知识图谱中，可能需要一点时间，你可以先去其他页面转转~', { duration: 0 })
    try {
      const res = await graphApi.previewConsolidate()
      previewTriples.value = res.data.data || []
      if (previewTriples.value.length === 0) {
        window.$message?.warning('未能生成有效的图谱合并结果')
        if (graphLoadingMsg) graphLoadingMsg.destroy()
        return
      }
      if (graphLoadingMsg) graphLoadingMsg.destroy()
      showGraphPreviewModal.value = true
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

  async function applyGraphConsolidation(onSuccess?: () => void) {
    applyingGraph.value = true
    try {
      await graphApi.applyConsolidate(previewTriples.value)
      showGraphPreviewModal.value = false
      window.$message?.success('图谱关系已更新！')
      await loadTriples()
      if (onSuccess) onSuccess()
    } catch (err: any) {
      logWarn('graph', '应用图谱整合失败', err)
      window.$message?.error('图谱更新失败：' + (err.response?.data?.message || err.message))
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
