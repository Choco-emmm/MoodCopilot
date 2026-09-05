<template>
  <!-- Memory Preview Modal -->
  <n-modal v-model:show="store.showMemoryPreviewModal" preset="card" title="👀 长期画像整理预览" class="consolidation-preview-modal">
    <p class="preview-desc">这里只展示可以明确合并的重复记忆。确认后会保留一条记忆，并保留全部来源、证据和历史版本。</p>
    
    <div class="preview-list preview-list-panel">
      <div v-for="(item, index) in store.previewMemories" :key="index" class="preview-item">
        <div class="preview-item-key">
          {{ item.attributeKey }}
          <n-tag v-if="item.isCore" size="small" type="warning" class="preview-tag">核心记忆</n-tag>
          <n-tag size="small" type="success" class="preview-tag">{{ memoryOperationLabel(item.operation) }}</n-tag>
        </div>
        <div class="preview-item-value">{{ item.attributeValue }}</div>
        <div class="preview-item-source">合并 {{ (item.sourceMemoryIds || []).length }} 条相同记忆 · 保留 {{ (item.evidenceIds || []).length }} 条证据</div>
      </div>
    </div>
    <template #action>
      <div class="flex-end-gap-12">
        <n-button @click="store.showMemoryPreviewModal = false">取消</n-button>
        <n-button type="primary" :loading="store.applyingMemory" @click="store.applyMemoryConsolidation()">确认去重</n-button>
      </div>
    </template>
  </n-modal>

  <!-- Graph Preview Modal -->
  <n-modal v-model:show="store.showGraphPreviewModal" preset="card" title="🕸️ 知识图谱整理预览" class="consolidation-preview-modal">
    <p class="preview-desc">这里只展示可以明确合并的重复关系。确认后会保留关系内容，并保留全部来源日记；相互冲突的关系不会被删除。</p>
    
    <div class="preview-list preview-list-panel">
      <div v-for="(item, index) in store.previewTriples" :key="index" class="preview-item graph-preview-item">
        <div class="graph-preview-relation">
          <span class="graph-preview-entity">{{ item.headEntity }}</span>
          <span class="graph-preview-connector">{{ item.relation }}</span>
          <span :class="['graph-preview-entity', polarityClass(item.tailPolarity)]">{{ item.tailEntity }}</span>
          <n-tag size="small" type="success" class="preview-tag">
            {{ graphOperationLabel(item.operation) }}
          </n-tag>
        </div>
        <div class="preview-item-source">来自 {{ (item.sourceTripleIds || []).length }} 条关系 · 关联 {{ (item.sourceDiaryIds || []).length }} 篇日记</div>
      </div>
    </div>
    <template #action>
      <div class="flex-end-gap-12">
        <n-button @click="store.showGraphPreviewModal = false">取消</n-button>
        <n-button type="primary" :loading="store.applyingGraph" @click="store.applyGraphConsolidation()">确认整理</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { NModal, NButton, NTag } from 'naive-ui'
import { useConsolidationStore } from '../stores/consolidation'

const store = useConsolidationStore()

function memoryOperationLabel(operation?: string) {
  return ({ MERGE: '合并同义记忆', DEDUP: '去除重复', NORMALIZE: '统一表达', EXPIRE: '标记过期' } as Record<string, string>)[operation || ''] || '整理建议'
}

function graphOperationLabel(operation?: string) {
  return ({ MERGE: '合并重复关系', DEDUP: '去除重复', NORMALIZE: '统一表达' } as Record<string, string>)[operation || ''] || '整理建议'
}

function polarityClass(polarity?: number) {
  polarity = polarity ?? 0
  if (polarity > 0) return 'is-positive'
  if (polarity < 0) return 'is-negative'
  return 'is-neutral'
}

</script>

<style scoped>
.consolidation-preview-modal { width: 600px; max-width: 90vw; }
.preview-desc { margin-top: 0; color: var(--color-text-secondary); font-size: 13px; margin-bottom: 16px; }
.deleted-items-panel {
  background: var(--color-surface-hover); 
  border-left: 3px solid var(--color-border); 
  padding: 8px 12px; 
  margin-bottom: 16px; 
  font-size: 12px; 
  color: var(--color-text-secondary); 
  border-radius: 4px; 
  max-height: 15vh; 
  overflow-y: auto;
}
.preview-list-panel { 
  max-height: 35vh; 
  overflow-y: auto; 
  background: var(--color-bg); 
  border: 1px solid var(--color-border); 
  border-radius: 6px; 
  padding: 12px; 
  margin-bottom: 16px; 
}
.preview-item { margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--color-border); font-size: 13px; color: var(--color-text); }
.preview-item:last-child { margin-bottom: 0; border-bottom: none; padding-bottom: 0; }
.preview-item-key { font-weight: bold; color: var(--color-primary); margin-bottom: 4px; }
.preview-tag { margin-left: 8px; }
.preview-item-value { color: var(--color-text-secondary); line-height: 1.5; white-space: pre-wrap; }
.preview-item-source { margin-top: 6px; color: var(--color-text-muted); font-size: 11px; }
.graph-preview-item { border-bottom: 1px dashed var(--color-border); padding-bottom: 8px; }
.graph-preview-relation { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.graph-preview-entity { font-weight: 700; color: var(--color-text); }
.graph-preview-connector { color: var(--color-text-secondary); font-size: 12px; }
.graph-preview-connector::before { content: '→ '; color: var(--color-text-muted); }
.graph-preview-entity.is-positive { color: var(--color-success); }
.graph-preview-entity.is-negative { color: var(--color-error); }
.graph-preview-entity.is-neutral { color: var(--color-info); }
.flex-end-gap-12 { display: flex; gap: 12px; justify-content: flex-end; }

.flex-end-gap-12 :deep(.n-button:not(.n-button--primary-type)) {
  --n-text-color-hover: var(--color-primary) !important;
  --n-border-hover: 1px solid var(--color-primary) !important;
  --n-text-color-focus: var(--color-primary) !important;
  --n-border-focus: 1px solid var(--color-primary) !important;
  --n-text-color-pressed: var(--color-primary-hover) !important;
  --n-border-pressed: 1px solid var(--color-primary-hover) !important;
  color: var(--color-text);
}
</style>
