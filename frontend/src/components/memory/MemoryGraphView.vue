<template>
  <div>
    <div class="section-head" style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 16px;">
      <div style="display: flex; align-items: center; gap: 8px; flex-shrink: 0;">
        <p class="settings-label" style="margin: 0; font-weight: bold; white-space: nowrap;">因果关联网络</p>
      </div>
      <div style="display: flex; gap: 8px; flex-wrap: wrap;">
        <n-button size="small" secondary @click="isGraphListView = !isGraphListView">
          {{ isGraphListView ? '🕸️ 切换图谱视图' : '📝 切换列表视图' }}
        </n-button>
        <n-button size="small" secondary type="primary" :loading="consolidatingGraph" @click="consolidateGraph">
          ✨ 智能整理图谱
        </n-button>
        <n-button size="small" secondary :loading="graphLoading" @click="loadGraph">
           刷新
        </n-button>
      </div>
    </div>
    
    <div class="tab-content" style="position: relative; min-height: 500px; display: flex; flex-direction: column;">
      <p class="memory-desc" style="margin-bottom: 12px;">从你的日记中提取的事件因果关系与概念网络。图谱上的“关联数”代表该事件与其他事件的联系程度，你可以在列表视图中手动调整不准确的关联。</p>
      
      <div v-if="graphLoading" style="flex: 1; display: flex; justify-content: center; align-items: center;">
        <n-spin size="small" />
      </div>
      <div v-else-if="!graphOptions && !isGraphListView" style="flex: 1; display: flex; justify-content: center; align-items: center; color: var(--color-text-light);">
        暂无图谱数据，写些有深度关联的日记吧。
      </div>
      
      <!-- Graph View -->
      <div v-else-if="!isGraphListView" style="flex: 1; min-height: 500px; border: 1px solid var(--color-border); border-radius: 8px; overflow: hidden; background: var(--color-bg);">
        <VChart class="chart" :option="graphOptions" autoresize style="height: 100%; min-height: 500px;" />
      </div>

      <!-- List View -->
      <div v-else class="memory-list">
         <div v-for="t in triples" :key="t.id" class="memory-item">
            <div class="memory-content" style="flex-direction: row; align-items: center; flex-wrap: wrap;">
              <template v-if="editingTripleId === t.id">
                <div style="display: flex; gap: 8px; flex: 1;">
                  <n-input v-model:value="editingTriple.headEntity" size="small" placeholder="起点" />
                  <n-input v-model:value="editingTriple.relation" size="small" placeholder="关系" />
                  <n-input v-model:value="editingTriple.tailEntity" size="small" placeholder="终点" />
                </div>
              </template>
              <template v-else>
                <span style="font-weight: bold; color: var(--color-primary);">{{ t.headEntity }}</span>
                <span style="color: var(--color-text-light); margin: 0 6px;">--({{ t.relation }})--></span>
                <span style="font-weight: bold; color: var(--color-primary);">{{ t.tailEntity }}</span>
              </template>
            </div>
            <div class="memory-actions">
              <template v-if="editingTripleId === t.id">
                <n-button size="small" secondary type="primary" :disabled="savingTripleId === t.id" @click="saveTriple(t.id)">保存</n-button>
                <n-button size="small" secondary @click="cancelEditTriple">取消</n-button>
              </template>
              <template v-else>
                <n-button size="small" secondary @click="startEditTriple(t)">编辑</n-button>
                <n-button size="small" secondary type="error" :disabled="deletingTripleId === t.id" @click="deleteTriple(t.id)">删除</n-button>
              </template>
            </div>
         </div>
         <div v-if="triples.length === 0" style="text-align: center; color: var(--color-text-light); padding: 20px;">
           暂无图谱数据
         </div>
      </div>
    </div>

    <!-- Graph Preview Modal -->
    <n-modal v-model:show="showGraphPreviewModal" preset="card" title="🕸️ 知识图谱整理预览" style="width: 600px; max-width: 90vw;">
      <p style="margin-top: 0; color: var(--color-text-secondary); font-size: 13px;">MoodCopilot 已合并冗余关系。确认后将覆盖旧的图谱关系：</p>

      <div v-if="deletedTriples.length > 0" style="background: var(--color-surface-hover); border-left: 3px solid var(--color-border); padding: 8px 12px; margin-bottom: 16px; font-size: 12px; color: var(--color-text-secondary); border-radius: 4px; max-height: 15vh; overflow-y: auto;">
        <div style="margin-bottom: 4px;">以下旧关系将被合并或移除：</div>
        <div v-for="t in deletedTriples" :key="t.id">
          <del>{{ t.headEntity }} --({{ t.relation }})--> {{ t.tailEntity }}</del>
        </div>
      </div>

      <div class="preview-list" style="max-height: 35vh; overflow-y: auto; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 6px; padding: 12px; margin-bottom: 16px;">
        <div v-for="(t, index) in previewTriples" :key="index" style="margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--color-border); font-size: 13px; color: var(--color-text);">
          <span style="font-weight: bold; color: var(--color-primary);">{{ t.headEntity }}</span>
          <span style="color: var(--color-text-light); margin: 0 6px;">--({{ t.relation }})--></span>
          <span style="font-weight: bold; color: var(--color-primary);">{{ t.tailEntity }}</span>
          <n-tag v-if="isTripleNew(t)" size="small" type="success" style="margin-left: 8px;">✨ 已合并</n-tag>
          <n-tag v-else size="small" style="margin-left: 8px;">无变化</n-tag>
        </div>
      </div>
      <template #action>
        <div style="display: flex; justify-content: flex-end; gap: 12px;">
          <n-button @click="showGraphPreviewModal = false">取消</n-button>
          <n-button type="primary" :loading="applyingGraph" @click="applyGraphConsolidation">确认替换</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { NButton, NSpin, NInput, NModal, NTag } from 'naive-ui'
import { graphApi } from '../../api'
import { logWarn } from '../../utils/logger'

// ECharts
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { GraphChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, GraphChart, TitleComponent, TooltipComponent, LegendComponent])

// Graph state
const graphLoading = ref(false)
const graphOptions = ref<any>(null)
const isGraphListView = ref(false)
const consolidatingGraph = ref(false)

interface TripleItem {
  id: number
  headEntity: string
  relation: string
  tailEntity: string
  diaryId: number
}
const triples = ref<TripleItem[]>([])
const triplesLoading = ref(false)
const deletingTripleId = ref<number | null>(null)
const editingTripleId = ref<number | null>(null)
const editingTriple = ref({ headEntity: '', relation: '', tailEntity: '' })
const savingTripleId = ref<number | null>(null)

const showGraphPreviewModal = ref(false)
const previewTriples = ref<any[]>([])
const applyingGraph = ref(false)

const deletedTriples = computed(() => {
  return triples.value.filter(old => !previewTriples.value.some(newT => old.headEntity === newT.headEntity && old.relation === newT.relation && old.tailEntity === newT.tailEntity))
})

onMounted(() => {
  loadGraph()
  loadTriples()
})

async function loadGraph() {
  graphLoading.value = true
  try {
    const res = await graphApi.getUserGraph()
    const data = res.data.data
    if (!data || !data.nodes || data.nodes.length === 0) {
      graphOptions.value = null
      return
    }

    graphOptions.value = {
      tooltip: {
        trigger: 'item',
        formatter: (params: any) => {
          if (params.dataType === 'node') {
            return `节点：${params.data.name}<br/>关联数：${params.data.value}`
          } else {
            return `${params.data.source} <br/> --(${params.data.label})--&gt; <br/> ${params.data.target}`
          }
        }
      },
      series: [
        {
          type: 'graph',
          layout: 'force',
          roam: true,
          draggable: true,
          label: {
            show: true,
            position: 'right',
            formatter: '{b}',
            color: 'var(--color-text)'
          },
          force: {
            repulsion: 300,
            edgeLength: 100
          },
          lineStyle: {
            color: '#a3c2b1',
            curveness: 0.2,
            width: 2
          },
          itemStyle: {
            color: 'var(--color-primary)'
          },
          edgeLabel: {
            show: true,
            fontSize: 10,
            formatter: (params: any) => params.data.label,
            color: 'var(--color-text-secondary)'
          },
          data: data.nodes.map((n: any) => ({
            name: n.name,
            value: n.value,
            symbolSize: Math.max(10, Math.min(30, n.value * 5))
          })),
          links: data.edges.map((e: any) => ({
            source: e.source,
            target: e.target,
            label: e.label
          }))
        }
      ]
    }
  } catch (err) {
    logWarn('memory', '加载图谱失败', err)
    graphOptions.value = null
  } finally {
    graphLoading.value = false
  }
}

async function loadTriples() {
  triplesLoading.value = true
  try {
    const res = await graphApi.getTriples()
    triples.value = res.data.data || []
  } catch (err) {
    logWarn('graph', '加载三元组失败', err)
  } finally {
    triplesLoading.value = false
  }
}

function isTripleNew(t: any) {
  return !triples.value.some(old => old.headEntity === t.headEntity && old.relation === t.relation && old.tailEntity === t.tailEntity)
}

function startEditTriple(t: TripleItem) {
  editingTripleId.value = t.id
  editingTriple.value = { headEntity: t.headEntity, relation: t.relation, tailEntity: t.tailEntity }
}

function cancelEditTriple() {
  editingTripleId.value = null
  editingTriple.value = { headEntity: '', relation: '', tailEntity: '' }
}

async function saveTriple(id: number) {
  if (!editingTriple.value.headEntity || !editingTriple.value.tailEntity || !editingTriple.value.relation) {
    window.$message?.warning('起点、关系、终点不能为空')
    return
  }
  savingTripleId.value = id
  try {
    await graphApi.updateTriple(id, editingTriple.value)
    const idx = triples.value.findIndex(t => t.id === id)
    if (idx !== -1) {
      triples.value[idx] = { ...triples.value[idx], ...editingTriple.value }
    }
    editingTripleId.value = null
    window.$message?.success('关系已更新')
    loadGraph() // 重新渲染图谱
  } catch (err) {
    logWarn('graph', '更新失败', err)
  } finally {
    savingTripleId.value = null
  }
}

async function deleteTriple(id: number) {
  deletingTripleId.value = id
  try {
    await graphApi.deleteTriple(id)
    triples.value = triples.value.filter(t => t.id !== id)
    window.$message?.success('关系已删除')
    loadGraph() // 重新渲染图谱
  } catch (err) {
    logWarn('graph', '删除失败', err)
  } finally {
    deletingTripleId.value = null
  }
}

async function consolidateGraph() {
  if (consolidatingGraph.value) return
  consolidatingGraph.value = true
  const loadingMsg = window.$message?.loading('MoodCopilot 正在努力整理图谱中，可能需要一点时间，你可以先去其他页面转转~', { duration: 0 })
  try {
    const res = await graphApi.previewConsolidate()
    previewTriples.value = res.data.data || []
    if (previewTriples.value.length === 0) {
      window.$message?.warning('未能生成有效的合并结果')
      if (loadingMsg) loadingMsg.destroy()
      return
    }
    if (loadingMsg) loadingMsg.destroy()
    showGraphPreviewModal.value = true
  } catch (err: any) {
    if (loadingMsg) loadingMsg.destroy()
    logWarn('graph', '图谱整理失败', err)
    alert('图谱整理失败：' + (err.response?.data?.message || err.message))
  } finally {
    consolidatingGraph.value = false
  }
}

async function applyGraphConsolidation() {
  applyingGraph.value = true
  try {
    await graphApi.applyConsolidate(previewTriples.value)
    showGraphPreviewModal.value = false
    window.$message?.success('图谱关系已更新！')
    await loadGraph()
    await loadTriples()
  } catch (err: any) {
    logWarn('graph', '应用整合失败', err)
    window.$message?.error('应用失败：' + (err.response?.data?.message || err.message))
  } finally {
    applyingGraph.value = false
  }
}
</script>

<style scoped>
.tab-content {
  padding: 16px 0;
  min-height: 400px;
}
.memory-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0 0 16px 0;
  line-height: 1.5;
}
.memory-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.memory-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px;
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.memory-item:hover {
  border-color: var(--color-jade-light);
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.memory-content {
  flex: 1 1 200px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.memory-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}
.memory-actions .n-button {
  font-size: 12px;
  padding: 0 10px;
}
.chart {
  width: 100%;
  height: 100%;
}
</style>
