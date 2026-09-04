<template>
  <div>
    <div class="section-head graph-header-controls">
      <div class="flex-center-gap-8 shrink-0">
        <p class="settings-label graph-header-title">因果关联网络</p>
      </div>
      <div class="flex-gap-8-wrap">
        <n-button size="small" secondary @click="isGraphListView = !isGraphListView">
          {{ isGraphListView ? '🕸️ 切换图谱视图' : '📝 切换列表视图' }}
        </n-button>
        <n-button size="small" secondary type="primary" :loading="store.consolidatingGraph" @click="store.consolidateGraph()">
          ✨ 智能整理图谱
        </n-button>
        <n-button size="small" secondary :loading="graphLoading" @click="loadGraphAndTriples">
           刷新
        </n-button>
      </div>
    </div>
    
    <div class="tab-content graph-tab-content">
      <p class="memory-desc mb-12">从你的日记中提取的事件因果关系与概念网络。图谱上的“关联数”代表该事件与其他事件的联系程度，你可以在列表视图中手动调整不准确的关联。</p>
      
      <div v-if="graphLoading" class="flex-center-full">
        <n-spin size="small" />
      </div>
      <div v-else-if="!graphOptions && !isGraphListView" class="flex-center-full text-light">
        暂无图谱数据，写些有深度关联的日记吧。
      </div>
      
      <!-- Graph View -->
      <div v-else-if="!isGraphListView" class="chart-container">
        <VChart ref="chartRef" class="chart chart-canvas" :option="graphOptions" autoresize />
        <div class="chart-overlay-controls">
          <n-button size="small" secondary @click="resetGraph">
            🧭 恢复视角
          </n-button>
        </div>
      </div>

      <!-- List View -->
      <div v-else class="memory-list">
         <div
            v-for="(t, index) in store.triples"
            :key="t.id"
            class="memory-item"
            v-motion
            :initial="{ opacity: 0, y: 30 }"
            :enter="{ opacity: 1, y: 0, transition: { type: 'spring', stiffness: 250, damping: 25, delay: index * 50 } }"
          >
            <div class="memory-content flex-row-wrap-center">
              <template v-if="editingTripleId === t.id">
                <div class="flex-col-gap-8-full">
                  <div class="flex-gap-8-wrap">
                    <n-input v-model:value="editingTriple.headEntity" size="small" placeholder="起点" class="flex-1-min-90" />
                    <n-input v-model:value="editingTriple.relation" size="small" placeholder="关系" class="flex-1-min-60" />
                    <n-input v-model:value="editingTriple.tailEntity" size="small" placeholder="终点" class="flex-1-min-90" />
                  </div>
                  <n-radio-group v-model:value="editingTriple.tailPolarity" size="small" class="flex-wrap-gap-8-mt-4">
                    <n-radio :value="1">😃 积极</n-radio>
                    <n-radio :value="0">😐 中性</n-radio>
                    <n-radio :value="-1">😔 消极</n-radio>
                  </n-radio-group>
                </div>
              </template>
              <template v-else>
                <span class="triple-entity">{{ t.headEntity }}</span>
                <span class="triple-relation">--({{ t.relation }})--></span>
                <span class="triple-entity">{{ t.tailEntity }}</span>
                <n-tag v-if="isRecentlyUpdated(t)" size="small" type="success" class="ml-8">✨ 近期新增</n-tag>
              </template>
            </div>
            <div class="memory-actions">
              <template v-if="editingTripleId === t.id">
                <n-button class="memory-action-btn save-btn" size="small" :disabled="savingTripleId === t.id" @click="saveTriple(t.id)">保存</n-button>
                <n-button class="memory-action-btn cancel-btn" size="small" @click="cancelEditTriple">取消</n-button>
              </template>
              <template v-else>
                <n-button class="memory-action-btn edit-btn" size="small" @click="startEditTriple(t)">编辑</n-button>
                <n-button class="memory-action-btn delete-btn" size="small" :disabled="deletingTripleId === t.id" @click="deleteTriple(t.id)">删除</n-button>
              </template>
            </div>
         </div>
         <div v-if="store.triples.length === 0" class="empty-triples">
           暂无图谱数据
         </div>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NButton, NSpin, NInput, NTag, NRadioGroup, NRadio } from 'naive-ui'
import { graphApi } from '../../api'
import { logWarn } from '../../utils/logger'
import { useConsolidationStore } from '../../stores/consolidation'

// ECharts
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { GraphChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, ToolboxComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, GraphChart, TitleComponent, TooltipComponent, LegendComponent, ToolboxComponent])

// Graph state
const chartRef = ref<any>(null)
const graphLoading = ref(false)
const graphOptions = ref<any>(null)
const isGraphListView = ref(false)
const store = useConsolidationStore()

function resetGraph() {
  if (chartRef.value) {
    chartRef.value.dispatchAction({ type: 'restore' })
  }
}

interface TripleItem {
  id: number
  headEntity: string
  relation: string
  tailEntity: string
  tailPolarity: number
  diaryId: number
}
const triplesLoading = ref(false)
const deletingTripleId = ref<number | null>(null)
const editingTripleId = ref<number | null>(null)
const editingTriple = ref({ headEntity: '', relation: '', tailEntity: '', tailPolarity: 0 })
const savingTripleId = ref<number | null>(null)

onMounted(() => {
  loadGraphAndTriples()
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

    const style = getComputedStyle(document.documentElement)
    const primaryColor = style.getPropertyValue('--color-primary').trim() || '#e4509a'
    const textColor = style.getPropertyValue('--color-text').trim() || '#333'
    const textSecColor = style.getPropertyValue('--color-text-secondary').trim() || '#666'
    const lineColor = style.getPropertyValue('--color-border-strong').trim() || '#ccc'

    // Some themes set --color-success and --color-info to the primary color for a monochrome UI aesthetic.
    // However, for the knowledge graph, we MUST have distinguishable semantic colors.
    let successColor = style.getPropertyValue('--color-success').trim() || '#18a058'
    let errorColor = style.getPropertyValue('--color-error').trim() || '#d03050'
    let infoColor = style.getPropertyValue('--color-info').trim() || '#8a8e99'

    if (successColor === primaryColor) successColor = '#18a058'
    if (errorColor === primaryColor) errorColor = '#d03050'
    if (infoColor === primaryColor) infoColor = '#8a8e99'

    graphOptions.value = {
      toolbox: {
        show: false,
        feature: {
          restore: {} // required for dispatchAction({type: 'restore'})
        }
      },
      legend: {
        show: true,
        bottom: 10,
        textStyle: { color: textSecColor }
      },
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
          scaleLimit: { min: 0.2, max: 4 }, // Limit zoom on mobile
          draggable: true,
          label: {
            show: true,
            position: 'right',
            formatter: '{b}',
            color: textColor
          },
          force: {
            repulsion: 300,
            edgeLength: 100
          },
          lineStyle: {
            color: lineColor,
            curveness: 0.2,
            width: 2
          },
          edgeLabel: {
            show: true,
            fontSize: 10,
            formatter: (params: any) => params.data.label,
            color: textSecColor
          },
          categories: [
            { name: '触发源 (事件/环境)', itemStyle: { color: primaryColor } },
            { name: '正向感受', itemStyle: { color: successColor } },
            { name: '负向与压力', itemStyle: { color: errorColor } },
            { name: '中性/平和', itemStyle: { color: infoColor } }
          ],
          data: data.nodes.map((n: any) => {
            const edgeAsTarget = data.edges.find((e: any) => e.target === n.name);
            let categoryIndex = 0;
            if (edgeAsTarget) {
              const p = edgeAsTarget.tailPolarity;
              if (p === 1) categoryIndex = 1;
              else if (p === -1) categoryIndex = 2;
              else categoryIndex = 3;
            }
            return {
              name: n.name,
              value: n.value,
              category: categoryIndex,
              // 加大节点的基础大小和生长率，提升视觉显著度
              symbolSize: Math.max(15, Math.min(45, n.value * 8))
            };
          }),
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

async function loadGraphAndTriples() {
  await loadGraph()
  await store.loadTriples()
}


function isRecentlyUpdated(t: any) {
  if (!t.createdAt) return false
  const ut = new Date(t.createdAt).getTime()
  // within 5 minutes
  return Date.now() - ut < 5 * 60 * 1000
}

function startEditTriple(t: TripleItem) {
  editingTripleId.value = t.id
  editingTriple.value = { headEntity: t.headEntity, relation: t.relation, tailEntity: t.tailEntity, tailPolarity: t.tailPolarity ?? 0 }
}

function cancelEditTriple() {
  editingTripleId.value = null
  editingTriple.value = { headEntity: '', relation: '', tailEntity: '', tailPolarity: 0 }
}

async function saveTriple(id: number) {
  if (!editingTriple.value.headEntity || !editingTriple.value.tailEntity || !editingTriple.value.relation) {
    window.$message?.warning('起点、关系、终点不能为空')
    return
  }
  savingTripleId.value = id
  try {
    await graphApi.updateTriple(id, editingTriple.value)
    const idx = store.triples.findIndex((t: any) => t.id === id)
    if (idx !== -1) {
      store.triples[idx] = { ...store.triples[idx], ...editingTriple.value }
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
    store.triples = store.triples.filter((t: any) => t.id !== id)
    window.$message?.success('关系已删除')
    loadGraph() // 重新渲染图谱
  } catch (err) {
    logWarn('graph', '删除失败', err)
  } finally {
    deletingTripleId.value = null
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
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-4);
  padding: 10px 0;
}
.memory-item {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 16px;
  padding: 24px;
  background: var(--color-surface);
  border: none;
  border-radius: var(--radius-xl);
  box-shadow: 0 10px 30px -10px rgba(0,0,0,0.03);
  transition: color var(--duration-normal) var(--ease-out), background-color var(--duration-normal) var(--ease-out), border-color var(--duration-normal) var(--ease-out), opacity var(--duration-normal) var(--ease-out), transform var(--duration-normal) var(--ease-out);
}
.memory-item:hover {
  transform: translateY(-4px);
  box-shadow: 0 20px 40px -10px rgba(0,0,0,0.06);
}
@media (prefers-color-scheme: dark) {
  .memory-item:hover {
    box-shadow: 0 20px 40px -10px rgba(0,0,0,0.2);
  }
}
.memory-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.memory-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
  justify-content: flex-end;
  border-top: 1px dashed color-mix(in oklab, var(--color-border) 40%, transparent);
  padding-top: 12px;
}
.memory-action-btn {
  font-size: 12px !important;
  padding: 0 12px !important;
  background: transparent !important;
  border: none !important;
  transition: all 0.2s ease !important;
  border-radius: var(--radius-full) !important;
}
.memory-action-btn.edit-btn,
.memory-action-btn.save-btn {
  color: var(--color-primary) !important;
}
.memory-action-btn.edit-btn:hover,
.memory-action-btn.save-btn:hover {
  color: var(--color-primary-hover) !important;
}
.memory-action-btn.delete-btn {
  color: var(--color-error) !important;
}
.memory-action-btn.delete-btn:hover {
  color: #ff6666 !important;
}
.memory-action-btn.cancel-btn {
  color: var(--color-text-secondary) !important;
}
.memory-action-btn.cancel-btn:hover {
  color: var(--color-text) !important;
}
.chart {
  width: 100%;
  height: 100%;
}

.graph-header-controls { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.flex-center-gap-8 { display: flex; align-items: center; gap: 8px; }
.shrink-0 { flex-shrink: 0; }
.graph-header-title { margin: 0; font-weight: bold; white-space: nowrap; }
.flex-gap-8-wrap { display: flex; gap: 8px; flex-wrap: wrap; }
.graph-tab-content { position: relative; min-height: 500px; display: flex; flex-direction: column; }
.mb-12 { margin-bottom: 12px; }
.flex-center-full { flex: 1; display: flex; justify-content: center; align-items: center; }
.text-light { color: var(--color-text-light); }
.chart-container { flex: 1; min-height: 500px; border: 1px solid var(--color-border); border-radius: 8px; overflow: hidden; background: var(--color-surface-soft); position: relative; touch-action: none; }
.chart-canvas { height: 100%; min-height: 500px; }
.chart-overlay-controls { position: absolute; right: 16px; top: 16px; z-index: 10; }
.flex-row-wrap-center { flex-direction: row; align-items: center; flex-wrap: wrap; }
.flex-col-gap-8-full { display: flex; flex-direction: column; gap: 8px; flex: 1; width: 100%; }
.flex-1-min-90 { flex: 1; min-width: 90px; }
.flex-1-min-60 { flex: 1; min-width: 60px; }
.flex-wrap-gap-8-mt-4 { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 4px; }
.triple-entity { font-weight: bold; color: var(--color-primary); }
.triple-relation { color: var(--color-text-light); margin: 0 6px; }
.ml-8 { margin-left: 8px; }
.empty-triples { text-align: center; color: var(--color-text-light); padding: 20px; }
.graph-preview-modal { width: 600px; max-width: 90vw; }
.preview-desc { margin-top: 0; color: var(--color-text-secondary); font-size: 13px; }
.deleted-triples-panel { background: var(--color-surface-hover); border-left: 3px solid var(--color-border); padding: 8px 12px; margin-bottom: 16px; font-size: 12px; color: var(--color-text-secondary); border-radius: 4px; max-height: 15vh; overflow-y: auto; }
.mb-4 { margin-bottom: 4px; }
.preview-list-panel { max-height: 35vh; overflow-y: auto; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 6px; padding: 12px; margin-bottom: 16px; }
.preview-item { margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--color-border); font-size: 13px; color: var(--color-text); }
.flex-end-gap-12 { display: flex; gap: 12px; justify-content: flex-end; }

</style>

