<template>
  <section class="memory-graph" aria-label="因果关联网络">
    <header class="section-head graph-header-controls">
      <div>
        <h3 class="graph-header-title">因果关联网络</h3>
        <p class="memory-desc">从日记中提取的事件与感受，留下可回看的关系线索。</p>
      </div>
      <div class="graph-actions">
        <button type="button" :disabled="navigationBusy" @click="isGraphListView = !isGraphListView">{{ isGraphListView ? '切换图谱视图' : '切换列表视图' }}</button>
        <button type="button" :disabled="graphLoading || navigationBusy || store.consolidatingGraph" @click="store.consolidateGraph()">
          {{ store.consolidatingGraph ? '正在整理图谱…' : '智能整理图谱' }}
        </button>
        <button type="button" :disabled="graphLoading || navigationBusy || store.consolidatingGraph" @click="loadTriples">{{ graphLoading ? '正在刷新…' : '刷新' }}</button>
      </div>
    </header>

    <div class="graph-filters">
      <label class="graph-search">搜索关系
        <input v-model="search" type="search" placeholder="搜索起点、关系或终点" :disabled="navigationBusy" />
      </label>
      <label>感受极性
        <select v-model="polarity" aria-label="感受极性" :disabled="navigationBusy">
          <option value="all">全部极性</option><option value="1">积极</option><option value="0">中性</option><option value="-1">消极</option>
        </select>
      </label>
    </div>
    <p class="graph-counts" role="status">关系总数 {{ store.triples.length }} · 筛选匹配 {{ filteredTriples.length }} · 当前显示 {{ visibleTriples.length }}</p>
    <div v-if="selectedNode" class="graph-selection">
      <span>节点邻域：{{ selectedNode }}</span>
      <button type="button" :disabled="navigationBusy" @click="selectedNode = null">清除节点</button>
    </div>
    <div v-if="loadError" class="graph-load-error" role="alert">
      <span>{{ loadError }}</span>
      <button type="button" :disabled="graphLoading || navigationBusy || store.consolidatingGraph" @click="loadTriples">重试加载</button>
    </div>
    <div v-if="graphLoading && !hasLoaded && !store.triples.length" class="graph-state" role="status">正在加载关系…</div>
    <template v-else-if="hasLoaded || store.triples.length">
      <p v-if="!store.triples.length" class="graph-empty">暂无关系。写下日记后，关联线索会逐渐出现。</p>
      <p v-else-if="!filteredTriples.length" class="graph-empty">没有匹配的关系，请调整搜索、极性或清除节点。</p>
      <template v-else>
        <div v-if="!isGraphListView" class="graph-visual">
          <div class="graph-chart-toolbar">
            <p id="graph-help" class="memory-desc">点击节点查看一跳邻域；拖动或缩放探索。键盘操作请切换列表视图。</p>
            <button type="button" @click="resetGraph">恢复视角</button>
          </div>
          <div class="chart-container" role="img" aria-label="当前显示关系的关联图" aria-describedby="graph-help">
            <VChart ref="chartRef" class="chart" :option="graphOptions" :update-options="{ notMerge: true }" autoresize @click="selectGraphNode" />
          </div>
        </div>
        <div v-else class="memory-list" aria-label="关系列表">
          <article v-for="t in visibleTriples" :key="t.id" class="memory-item">
            <div class="triple-summary">
              <p><strong>{{ t.headEntity }}</strong><span class="triple-relation"> {{ t.relation }} → </span><strong>{{ t.tailEntity }}</strong></p>
              <span class="triple-polarity">{{ polarityLabel(t.tailPolarity) }}</span>
              <button type="button" :aria-expanded="detailId === t.id" :aria-controls="`triple-detail-${t.id}`" :disabled="navigationBusy" @click="toggleDetails(t.id)">{{ detailId === t.id ? '收起详情' : '查看详情' }}</button>
            </div>
            <div v-if="detailId === t.id" :id="`triple-detail-${t.id}`" class="triple-details">
              <section class="triple-sources" aria-label="来源日记">
                <h4>来源日记</h4>
                <div v-if="sourceDiaryIds(t).length" class="graph-actions">
                  <RouterLink v-for="id in sourceDiaryIds(t)" :key="id" :to="{ name: 'diary-detail', params: { id } }">打开日记 {{ id }}</RouterLink>
                </div>
                <p v-else class="memory-desc">暂无可用的来源日记。</p>
              </section>
              <form v-if="editingTripleId === t.id" class="triple-editor" @submit.prevent="saveTriple(t.id)">
                <label>起点事件<input v-model="editingTriple.headEntity" :disabled="savingTripleId !== null" required /></label>
                <label>关系描述<input v-model="editingTriple.relation" :disabled="savingTripleId !== null" required /></label>
                <label>终点感受<input v-model="editingTriple.tailEntity" :disabled="savingTripleId !== null" required /></label>
                <label>编辑感受极性
                  <select v-model.number="editingTriple.tailPolarity" aria-label="编辑感受极性" :disabled="savingTripleId !== null">
                    <option :value="1">积极</option><option :value="0">中性</option><option :value="-1">消极</option>
                  </select>
                </label>
                <p v-if="editError" class="graph-local-error" role="alert">{{ editError }}</p>
                <div class="graph-actions">
                  <button type="submit" :disabled="savingTripleId !== null">{{ savingTripleId !== null ? '正在保存…' : '保存关系' }}</button>
                  <button type="button" :disabled="savingTripleId !== null" @click="cancelEditTriple">取消编辑</button>
                </div>
              </form>
              <div v-else class="graph-actions detail-actions">
                <button type="button" :disabled="navigationBusy || graphLoading || store.consolidatingGraph" @click="startEditTriple(t)">编辑关系</button>
                <button type="button" class="danger-action" :disabled="navigationBusy || graphLoading || store.consolidatingGraph" @click="confirmDeleteTriple(t)">删除此关系</button>
              </div>
            </div>
          </article>
        </div>
        <footer v-if="filteredTriples.length > PAGE_SIZE" class="graph-pagination">
          <span>{{ visibleTriples.length < filteredTriples.length ? '先看一小部分，再按需展开。' : '已显示全部匹配关系。' }}</span>
          <div class="graph-actions">
            <button v-if="visibleTriples.length < filteredTriples.length" type="button" :disabled="navigationBusy" @click="visibleLimit += PAGE_SIZE">显示更多</button>
            <button v-if="visibleTriples.length < filteredTriples.length" type="button" :disabled="navigationBusy" @click="visibleLimit = Infinity">展开全部</button>
            <button v-if="visibleLimit > PAGE_SIZE" type="button" :disabled="navigationBusy" @click="visibleLimit = PAGE_SIZE">恢复有限显示</button>
          </div>
        </footer>
      </template>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { useDialog } from 'naive-ui'
import { graphApi } from '../../api'
import { logWarn } from '../../utils/logger'
import { useConsolidationStore } from '../../stores/consolidation'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { GraphChart } from 'echarts/charts'
import { TooltipComponent, ToolboxComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, GraphChart, TooltipComponent, ToolboxComponent])

interface TripleItem {
  id: number
  headEntity: string
  relation: string
  tailEntity: string
  tailPolarity?: number
  diaryId?: unknown
  sourceDiaryIds?: unknown
}
const PAGE_SIZE = 20
const store = useConsolidationStore()
const dialog = useDialog()
const chartRef = ref<InstanceType<typeof VChart> | null>(null)
const isGraphListView = ref(false)
const graphLoading = ref(false)
const hasLoaded = ref(store.triples.length > 0)
const loadError = ref('')
const search = ref('')
const polarity = ref('all')
const selectedNode = ref<string | null>(null)
const visibleLimit = ref(PAGE_SIZE)
const detailId = ref<number | null>(null)
const editingTripleId = ref<number | null>(null)
const editingTriple = ref({ headEntity: '', relation: '', tailEntity: '', tailPolarity: 0 })
const savingTripleId = ref<number | null>(null)
const deletingTripleId = ref<number | null>(null)
const deleteDialogOpen = ref(false)
const editError = ref('')
const mutationBusy = computed(() => savingTripleId.value !== null || deletingTripleId.value !== null || deleteDialogOpen.value)
const navigationBusy = computed(() => mutationBusy.value || editingTripleId.value !== null)
const filteredTriples = computed<TripleItem[]>(() => {
  const query = search.value.trim().toLocaleLowerCase()
  return store.triples.filter((t: TripleItem) => (
    (!query || [t.headEntity, t.relation, t.tailEntity].some(text => text.toLocaleLowerCase().includes(query)))
    && (polarity.value === 'all' || (t.tailPolarity ?? 0) === Number(polarity.value))
    && (!selectedNode.value || t.headEntity === selectedNode.value || t.tailEntity === selectedNode.value)
  ))
})
const visibleTriples = computed(() => filteredTriples.value.slice(0, visibleLimit.value))
watch([search, polarity, selectedNode], () => { visibleLimit.value = PAGE_SIZE })
// Computed graph/list projections also react to consolidation's replacement or in-place edits.
watch(() => store.triples, (triples: TripleItem[]) => {
  if (selectedNode.value && !triples.some(t => t.headEntity === selectedNode.value || t.tailEntity === selectedNode.value)) selectedNode.value = null
  if (detailId.value !== null && !triples.some(t => t.id === detailId.value)) {
    detailId.value = null
    cancelEditTriple()
  }
}, { deep: true })

async function loadTriples() {
  if (graphLoading.value || navigationBusy.value || store.consolidatingGraph) return
  graphLoading.value = true
  loadError.value = ''
  try {
    // Do not use store.loadTriples: it swallows errors and clears the last successful result.
    const response = await graphApi.getTriples()
    const triples = response.data.data
    if (!Array.isArray(triples)) throw new Error('Invalid triples response')
    store.triples = triples
    hasLoaded.value = true
  } catch (error) {
    loadError.value = hasLoaded.value || store.triples.length
      ? '刷新失败，已保留上次加载的关系。请稍后重试。'
      : '关系加载失败，暂时无法获取数据。请重试。'
    logWarn('graph', '加载关系失败', error)
  } finally { graphLoading.value = false }
}
function polarityLabel(value?: number) { return value === 1 ? '积极' : value === -1 ? '消极' : '中性' }
function sourceDiaryIds(t: TripleItem): number[] {
  let sources = t.sourceDiaryIds
  if (typeof sources === 'string') {
    try { sources = JSON.parse(sources) } catch { sources = [] }
  }
  const ids = [t.diaryId, ...(Array.isArray(sources) ? sources : [])]
  return [...new Set(ids.flatMap(value => {
    if (typeof value !== 'number' && !(typeof value === 'string' && /^\d+$/.test(value))) return []
    const id = Number(value)
    return Number.isSafeInteger(id) && id > 0 ? [id] : []
  }))]
}
function toggleDetails(id: number) {
  if (navigationBusy.value) return
  detailId.value = detailId.value === id ? null : id
  cancelEditTriple()
}
function startEditTriple(t: TripleItem) {
  if (navigationBusy.value || graphLoading.value || store.consolidatingGraph) return
  editingTripleId.value = t.id
  editingTriple.value = { headEntity: t.headEntity, relation: t.relation, tailEntity: t.tailEntity, tailPolarity: t.tailPolarity ?? 0 }
  editError.value = ''
}
function cancelEditTriple() {
  if (savingTripleId.value !== null) return
  editingTripleId.value = null
  editError.value = ''
}
async function saveTriple(id: number) {
  if (mutationBusy.value || graphLoading.value || store.consolidatingGraph || editingTripleId.value !== id) return
  const draft = { ...editingTriple.value, headEntity: editingTriple.value.headEntity.trim(), relation: editingTriple.value.relation.trim(), tailEntity: editingTriple.value.tailEntity.trim() }
  if (!draft.headEntity || !draft.relation || !draft.tailEntity) { editError.value = '起点、关系与终点不能为空。'; return }
  savingTripleId.value = id
  editError.value = ''
  try {
    await graphApi.updateTriple(id, draft)
    const index = store.triples.findIndex((t: TripleItem) => t.id === id)
    if (index !== -1) store.triples[index] = { ...store.triples[index], ...draft }
    editingTripleId.value = null
  } catch (error) {
    editError.value = '保存失败，已保留你的修改，请重试。'
    logWarn('graph', '更新关系失败', error)
  } finally { savingTripleId.value = null }
}
function confirmDeleteTriple(t: TripleItem) {
  if (navigationBusy.value || graphLoading.value || store.consolidatingGraph) return
  deleteDialogOpen.value = true
  const error = ref('')
  const confirmation = dialog.warning({
    title: '确认删除关系',
    closable: false,
    contentStyle: { overflowWrap: 'anywhere' },
    content: () => h('div', [
      h('p', `${t.headEntity} — ${t.relation} → ${t.tailEntity}`),
      h('p', '仅删除此关系，不删除原日记。此操作无法撤销。'),
      error.value ? h('p', { role: 'alert', style: { color: 'var(--color-error)' } }, error.value) : null,
    ]),
    positiveText: '确认删除', negativeText: '取消',
    positiveButtonProps: { themeOverrides: { heightSmall: '44px', heightMedium: '44px', heightLarge: '44px' } },
    negativeButtonProps: { themeOverrides: { heightSmall: '44px', heightMedium: '44px', heightLarge: '44px' } },
    onClose: () => deletingTripleId.value === null,
    onMaskClick: () => deletingTripleId.value === null,
    onAfterLeave: () => { deleteDialogOpen.value = false },
    onPositiveClick: async () => {
      if (deletingTripleId.value !== null) return false
      // Do not delete a different relationship if consolidation changed this row meanwhile.
      const current = store.triples.find((item: TripleItem) => item.id === t.id)
      if (!current || current.headEntity !== t.headEntity || current.relation !== t.relation || current.tailEntity !== t.tailEntity) {
        error.value = '此关系已变化，请取消并重新查看。'
        return false
      }
      deletingTripleId.value = t.id
      confirmation.loading = true
      confirmation.closable = false
      confirmation.maskClosable = false
      confirmation.closeOnEsc = false
      confirmation.negativeButtonProps = { disabled: true, themeOverrides: { heightSmall: '44px', heightMedium: '44px', heightLarge: '44px' } }
      error.value = ''
      try {
        await graphApi.deleteTriple(t.id)
        store.triples = store.triples.filter((item: TripleItem) => item.id !== t.id)
        return true
      } catch (cause) {
        error.value = '删除失败，此关系仍保留，请重试。'
        logWarn('graph', '删除关系失败', cause)
        return false
      } finally {
        deletingTripleId.value = null
        confirmation.loading = false
        confirmation.closable = false
        confirmation.maskClosable = true
        confirmation.closeOnEsc = true
        confirmation.negativeButtonProps = { disabled: false, themeOverrides: { heightSmall: '44px', heightMedium: '44px', heightLarge: '44px' } }
      }
    },
  })
}

// ECharts canvas needs resolved colors. Refresh when the app changes its global theme.
const colors = ref<Record<string, string>>({})
function readColors() {
  const style = getComputedStyle(document.documentElement)
  colors.value = Object.fromEntries(['primary', 'text', 'text-secondary', 'border-strong', 'success', 'error', 'info', 'surface'].map(name => [name, style.getPropertyValue(`--color-${name}`).trim()]))
}
let themeObserver: MutationObserver | undefined
const darkMode = window.matchMedia('(prefers-color-scheme: dark)')
onMounted(() => {
  readColors()
  themeObserver = new MutationObserver(readColors)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['style', 'class', 'data-theme'] })
  darkMode.addEventListener('change', readColors)
  void loadTriples()
})
onUnmounted(() => { themeObserver?.disconnect(); darkMode.removeEventListener('change', readColors) })
function selectGraphNode(params: { dataType?: string; name?: string }) {
  if (!navigationBusy.value && params.dataType === 'node' && params.name) selectedNode.value = params.name
}
function resetGraph() { chartRef.value?.dispatchAction({ type: 'restore' }) }
const graphOptions = computed(() => {
  const nodes = new Map<string, { name: string; value: number; itemStyle: { color: string } }>()
  const c = colors.value
  for (const t of visibleTriples.value) {
    for (const [name, color] of [[t.headEntity, c.primary], [t.tailEntity, t.tailPolarity === 1 ? c.success : t.tailPolarity === -1 ? c.error : c.info]] as const) {
      const node = nodes.get(name)
      if (node) node.value++
      else nodes.set(name, { name, value: 1, itemStyle: { color } })
    }
  }
  return {
    animation: false,
    toolbox: { show: false, feature: { restore: {} } },
    tooltip: {
      trigger: 'item', renderMode: 'richText', confine: true,
      backgroundColor: c.surface, borderColor: c['border-strong'], textStyle: { color: c.text },
      formatter: (params: { dataType: string; data: { name: string; value: number; source: string; target: string; relation: string } }) => params.dataType === 'node'
        ? `节点：${params.data.name}\n当前显示关联数：${params.data.value}`
        : `${params.data.source}\n${params.data.relation}\n${params.data.target}`,
    },
    series: [{
      type: 'graph', layout: 'circular', roam: true, draggable: true,
      left: 56, right: 80, top: 56, bottom: 56, containLabel: true,
      scaleLimit: { min: 0.4, max: 3 },
      circular: { rotateLabel: false },
      label: { show: true, position: 'right', color: c.text, width: 100, overflow: 'truncate', formatter: '{b}' },
      labelLayout: { hideOverlap: true },
      edgeLabel: { show: false },
      emphasis: { focus: 'adjacency', label: { show: true } },
      lineStyle: { color: c['border-strong'], width: 1.5, curveness: 0.12 },
      data: [...nodes.values()].map(node => ({ ...node, symbolSize: Math.min(34, 14 + node.value * 3) })),
      links: visibleTriples.value.map(t => ({ source: t.headEntity, target: t.tailEntity, relation: t.relation })),
    }],
  }
})
</script>

<style scoped>
.memory-graph { color: var(--color-text); min-width: 0; }
.graph-header-controls, .graph-actions, .graph-chart-toolbar, .graph-pagination, .graph-selection, .graph-load-error { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.graph-header-controls { justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.graph-header-title { font: 600 20px/1.5 var(--font-display); margin: 0 0 6px; }
.memory-desc { color: var(--color-text-secondary); font-size: 13px; line-height: 1.7; margin: 0; }
.graph-filters { display: flex; flex-wrap: wrap; gap: 16px; }
.memory-graph label { display: flex; flex-direction: column; gap: 8px; font-size: 13px; color: var(--color-text-secondary); }
.graph-search { flex: 1 1 220px; }
.memory-graph input, .memory-graph select { box-sizing: border-box; width: 100%; min-width: 44px; min-height: 44px; padding: 10px 12px; border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); font: inherit; font-size: 16px; }
.memory-graph button, .triple-sources a { box-sizing: border-box; display: inline-flex; align-items: center; justify-content: center; min-width: 44px; min-height: 44px; padding: 10px 14px; border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); font: inherit; font-size: 13px; line-height: 1.5; cursor: pointer; text-decoration: none; transition: background-color 150ms, border-color 150ms; }
.memory-graph button:hover:not(:disabled), .triple-sources a:hover { background: var(--color-surface-hover); border-color: var(--color-border-strong); }
.memory-graph :is(button, input, select, a):focus-visible { outline: 2px solid var(--color-primary); outline-offset: 3px; }
.memory-graph :disabled { opacity: 0.55; cursor: not-allowed; }
.graph-counts, .graph-pagination { color: var(--color-text-secondary); font-size: 13px; line-height: 1.7; margin: 16px 0; }
.graph-selection { justify-content: space-between; background: var(--color-surface-soft); padding: 8px 12px; margin-bottom: 16px; border-radius: var(--radius-sm); overflow-wrap: anywhere; }
.graph-load-error { padding: 12px; border: 1px solid var(--color-error); border-radius: var(--radius-sm); margin: 12px 0; color: var(--color-error); }
.graph-local-error, .memory-graph .danger-action { color: var(--color-error); }
.graph-state, .graph-empty { padding: 48px 16px; text-align: center; color: var(--color-text-secondary); line-height: 1.7; }
.graph-chart-toolbar { justify-content: space-between; margin: 12px 0; }
.graph-chart-toolbar .memory-desc { flex: 1 1 220px; }
.chart-container { border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-surface-soft); overflow: hidden; }
.chart { width: 100%; height: 460px; }
.memory-list { border-top: 1px solid var(--color-border); }
.memory-item { border-bottom: 1px solid var(--color-border); padding: 20px 0; }
.triple-summary { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.triple-summary > p { flex: 1 1 240px; margin: 0; font-size: 15px; line-height: 1.8; overflow-wrap: anywhere; }
.triple-summary strong { font-weight: 500; }
.triple-relation, .triple-polarity { color: var(--color-text-secondary); font-size: 13px; }
.triple-details { padding: 20px; margin-top: 16px; background: var(--color-surface-soft); border-radius: var(--radius-sm); }
.triple-sources h4 { margin: 0 0 12px; font: 500 15px/1.5 var(--font-display); }
.triple-editor { display: grid; gap: 16px; margin-top: 24px; }
.detail-actions { margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--color-border); }
.graph-pagination { justify-content: space-between; gap: 16px; }
@media (max-width: 480px) {
  .graph-header-controls > .graph-actions { width: 100%; }
  .triple-details { padding: 16px; }
  .chart { height: 400px; }
}
@media (prefers-reduced-motion: reduce) { .memory-graph button, .triple-sources a { transition: none; } }
</style>
