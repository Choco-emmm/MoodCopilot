<template>
  <!-- Memory Preview Modal -->
  <n-modal v-model:show="store.showMemoryPreviewModal" preset="card" title="👀 长期画像整理预览" class="consolidation-preview-modal">
    <p class="preview-desc">以下仅是有明确来源的同值记忆去重建议。原始记录、证据和版本历史都会保留，冲突事实不会被合并。</p>
    
    <div class="preview-list preview-list-panel">
      <div v-for="(item, index) in store.previewMemories" :key="index" class="preview-item">
        <div class="preview-item-key">
          {{ item.attributeKey }}
          <n-tag v-if="item.isCore" size="small" type="warning" style="margin-left: 8px;">核心</n-tag>
          <n-tag size="small" type="success" style="margin-left: 8px;">{{ item.operation || '去重' }}</n-tag>
        </div>
        <div class="preview-item-value">{{ item.attributeValue }}</div>
        <div class="preview-item-source">来源记忆 {{ (item.sourceMemoryIds || []).join('、') }} · 证据 {{ (item.evidenceIds || []).length }} 条</div>
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
    <p class="preview-desc">以下仅是有明确来源的重复关系去重建议。原始三元组和来源日记会保留，冲突极性不会被删除。</p>
    
    <div class="preview-list preview-list-panel">
      <div v-for="(item, index) in store.previewTriples" :key="index" class="preview-item" style="border-bottom: 1px dashed var(--color-border); padding-bottom: 8px;">
        <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
          <span style="font-weight: bold; color: var(--color-text);">{{ item.headEntity }}</span>
          <span style="color: var(--color-text-light); font-size: 12px;">--({{ item.relation }})--></span>
          <span style="font-weight: bold;" :style="{ color: item.tailPolarity > 0 ? 'var(--color-success)' : item.tailPolarity < 0 ? 'var(--color-error)' : 'var(--color-info)' }">
            {{ item.tailEntity }}
          </span>
          <n-tag size="small" type="success" style="margin-left: 8px;">{{ item.operation || '去重' }}</n-tag>
        </div>
        <div class="preview-item-source">来源三元组 {{ (item.sourceTripleIds || []).join('、') }} · 来源日记 {{ (item.sourceDiaryIds || []).join('、') }}</div>
      </div>
    </div>
    <template #action>
      <div class="flex-end-gap-12">
        <n-button @click="store.showGraphPreviewModal = false">取消</n-button>
        <n-button type="primary" :loading="store.applyingGraph" @click="store.applyGraphConsolidation()">确认去重</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { NModal, NButton, NTag } from 'naive-ui'
import { useConsolidationStore } from '../stores/consolidation'

const store = useConsolidationStore()

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
.preview-item-value { color: var(--color-text-secondary); line-height: 1.5; white-space: pre-wrap; }
.preview-item-source { margin-top: 6px; color: var(--color-text-tertiary, #929a95); font-size: 11px; }
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
