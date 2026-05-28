<template>
  <!-- Memory Preview Modal -->
  <n-modal v-model:show="store.showMemoryPreviewModal" preset="card" title="👀 长期画像整理预览" class="consolidation-preview-modal">
    <p class="preview-desc">MoodCopilot 已将你的碎片记忆重新梳理为以下核心画像。确认替换后，这些内容将覆盖旧的数据：</p>
    
    <div v-if="deletedMemories.length > 0" class="deleted-items-panel">
      <div style="margin-bottom: 4px;">以下旧画像将被合并或移除：</div>
      <div v-for="m in deletedMemories" :key="m.id">
        <del>{{ m.attributeKey }}: {{ m.attributeValue.length > 30 ? m.attributeValue.substring(0, 30) + '...' : m.attributeValue }}</del>
      </div>
    </div>

    <div class="preview-list preview-list-panel">
      <div v-for="(item, index) in store.previewMemories" :key="index" class="preview-item">
        <div class="preview-item-key">
          {{ item.attributeKey }}
          <n-tag v-if="item.isCore" size="small" type="warning" style="margin-left: 8px;">核心</n-tag>
          <n-tag v-if="isMemoryNew(item)" size="small" type="success" style="margin-left: 8px;">✨ 已变动</n-tag>
          <n-tag v-else size="small" style="margin-left: 8px;">无变化</n-tag>
        </div>
        <div class="preview-item-value">{{ item.attributeValue }}</div>
      </div>
    </div>
    <template #action>
      <div class="flex-end-gap-12">
        <n-button @click="store.showMemoryPreviewModal = false">取消</n-button>
        <n-button type="primary" :loading="store.applyingMemory" @click="store.applyMemoryConsolidation()">确认替换</n-button>
      </div>
    </template>
  </n-modal>

  <!-- Graph Preview Modal -->
  <n-modal v-model:show="store.showGraphPreviewModal" preset="card" title="🕸️ 知识图谱整理预览" class="consolidation-preview-modal">
    <p class="preview-desc">MoodCopilot 已将你的碎片关联梳理为以下高度提纯的三元组。确认替换后，图谱将被重构：</p>
    
    <div v-if="deletedTriples.length > 0" class="deleted-items-panel">
      <div style="margin-bottom: 4px;">以下旧关联将被合并或移除：</div>
      <div v-for="t in deletedTriples" :key="t.id">
        <del>{{ t.headEntity }} --({{ t.relation }})--> {{ t.tailEntity }}</del>
      </div>
    </div>

    <div class="preview-list preview-list-panel">
      <div v-for="(item, index) in store.previewTriples" :key="index" class="preview-item" style="border-bottom: 1px dashed var(--color-border); padding-bottom: 8px;">
        <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
          <span style="font-weight: bold; color: var(--color-text);">{{ item.headEntity }}</span>
          <span style="color: var(--color-text-light); font-size: 12px;">--({{ item.relation }})--></span>
          <span style="font-weight: bold;" :style="{ color: item.tailPolarity > 0 ? 'var(--color-success)' : item.tailPolarity < 0 ? 'var(--color-error)' : 'var(--color-info)' }">
            {{ item.tailEntity }}
          </span>
          <n-tag v-if="isTripleNew(item)" size="small" type="success" style="margin-left: 8px;">✨ 新合并</n-tag>
          <n-tag v-else size="small" style="margin-left: 8px;">无变化</n-tag>
        </div>
      </div>
    </div>
    <template #action>
      <div class="flex-end-gap-12">
        <n-button @click="store.showGraphPreviewModal = false">取消</n-button>
        <n-button type="primary" :loading="store.applyingGraph" @click="store.applyGraphConsolidation()">确认替换</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { NModal, NButton, NTag } from 'naive-ui'
import { useConsolidationStore } from '../stores/consolidation'

const store = useConsolidationStore()

const deletedMemories = computed(() => {
  return store.memories.filter(old => !store.previewMemories.some(newM => old.attributeKey === newM.attributeKey && old.attributeValue === newM.attributeValue))
})

const deletedTriples = computed(() => {
  return store.triples.filter(old => !store.previewTriples.some(newT => old.headEntity === newT.headEntity && old.relation === newT.relation && old.tailEntity === newT.tailEntity))
})

function isMemoryNew(item: any) {
  return !store.memories.some(old => old.attributeKey === item.attributeKey && old.attributeValue === item.attributeValue)
}

function isTripleNew(item: any) {
  return !store.triples.some(old => old.headEntity === item.headEntity && old.relation === item.relation && old.tailEntity === item.tailEntity)
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
.preview-item-value { color: var(--color-text-secondary); line-height: 1.5; white-space: pre-wrap; }
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
