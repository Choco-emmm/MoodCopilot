<template>
  <div class="tab-content">
    <div class="section-head" style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 16px;">
      <div style="display: flex; align-items: center; gap: 8px; flex-shrink: 0;">
        <p class="settings-label" style="margin: 0; font-weight: bold; white-space: nowrap;">我的记忆</p>
      </div>
      <n-button size="small" secondary type="primary" :loading="store.consolidatingMemory" @click="store.consolidateMemories()">
        ✨ 智能整理记忆
      </n-button>
      <n-button size="small" secondary :loading="memoriesLoading" @click="loadMemories">
         刷新
      </n-button>
    </div>
    <p class="memory-desc">MoodCopilot 从你的日记和聊天中学习的长期画像，你可以编辑修正或删除不想要的部分。如果碎片太多，可以尝试智能整理归并。</p>
    <div v-if="memoriesLoading" class="memory-loading" style="text-align: center; padding: 40px 0;">
      <n-spin size="small" />
    </div>
    <div v-else-if="store.memories.length === 0" class="memory-empty" style="text-align: center; padding: 40px 0; color: var(--color-text-light);">
      MoodCopilot 正在默默观察你，多写点日记或和 MoodCopilot 聊天吧。
    </div>
    <div v-else class="memory-list">
      <div
        v-for="(m, index) in store.memories"
        :key="m.id"
        class="memory-item"
        v-motion
        :initial="{ opacity: 0, y: 30 }"
        :enter="{ opacity: 1, y: 0, transition: { type: 'spring', stiffness: 250, damping: 25, delay: index * 50 } }"
      >
        <div class="memory-content">
          <span class="memory-key">{{ m.attributeKey }}</span>
          <n-tag v-if="isRecentlyUpdated(m)" size="small" type="success" style="margin-left: 8px; vertical-align: text-bottom;">✨ 近期变动</n-tag>
          <template v-if="editingMemoryId === m.id">
            <div style="display: flex; flex-direction: column; gap: 8px;">
              <n-input
                v-model:value="editingMemoryValue"
                size="small"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 6 }"
                class="memory-edit-input"
                :maxlength="500"
              />
              <n-checkbox v-model:checked="editingMemoryIsCore">设为核心属性</n-checkbox>
            </div>
          </template>
          <div v-else class="memory-value">
            <n-tag v-if="m.isCore" size="small" type="warning" style="margin-right: 6px; vertical-align: top;">
              核心
              <n-popover trigger="hover" placement="top" style="max-width: 280px; font-size: 13px;">
                <template #trigger>
                  <span style="display: inline-block; width: 14px; text-align: center; cursor: pointer; color: var(--color-text-secondary); font-weight: bold; margin-left: 2px;">ⓘ</span>
                </template>
                <strong>核心属性</strong><br/>决定了 MoodCopilot 了解你底层性格、长期偏好和沟通基调的关键。这些信息会被常驻注入到与你的每一次对话中，让 MoodCopilot 能够始终保持对你最深刻的理解。而“非核心属性”则是辅助背景，在提到相关话题时才会被回忆起来。
              </n-popover>
            </n-tag>
            {{ m.attributeValue }}
          </div>
        </div>
        <div class="memory-actions">
          <template v-if="editingMemoryId === m.id">
            <n-button size="small" secondary type="primary" :disabled="savingMemoryId === m.id" @click="saveMemory(m.id)">
              {{ savingMemoryId === m.id ? '...' : '保存' }}
            </n-button>
            <n-button size="small" secondary @click="cancelEditMemory">取消</n-button>
          </template>
          <template v-else>
            <n-button size="small" secondary @click="startEditMemory(m)">编辑</n-button>
            <n-button size="small" secondary type="error" :disabled="deletingMemoryId === m.id" @click="forgetMemory(m.id)">
              {{ deletingMemoryId === m.id ? '...' : '删除' }}
            </n-button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NButton, NSpin, NInput, NTag, NCheckbox, NPopover } from 'naive-ui'
import { memoryApi } from '../../api'
import { logWarn } from '../../utils/logger'
import { useConsolidationStore } from '../../stores/consolidation'

interface MemoryItem {
  id: number
  attributeKey: string
  attributeValue: string
  isCore?: boolean
}

const store = useConsolidationStore()
const memoriesLoading = ref(false)
const deletingMemoryId = ref<number | null>(null)
const editingMemoryId = ref<number | null>(null)
const editingMemoryValue = ref('')
const editingMemoryIsCore = ref(false)
const savingMemoryId = ref<number | null>(null)

onMounted(() => {
  loadMemories()
})

async function loadMemories() {
  memoriesLoading.value = true
  await store.loadMemories()
  memoriesLoading.value = false
}

function isRecentlyUpdated(item: any) {
  if (!item.updateTime) return false
  const ut = new Date(item.updateTime).getTime()
  // within 5 minutes
  return Date.now() - ut < 5 * 60 * 1000
}

async function forgetMemory(id: number) {
  deletingMemoryId.value = id
  try {
    await memoryApi.forget(id)
    store.memories = store.memories.filter((m: any) => m.id !== id)
  } catch (e) {
    logWarn('memory', '删除记忆失败', id, e)
  } finally {
    deletingMemoryId.value = null
  }
}

function startEditMemory(m: MemoryItem) {
  editingMemoryId.value = m.id
  editingMemoryValue.value = m.attributeValue
  editingMemoryIsCore.value = !!m.isCore
}

async function saveMemory(id: number) {
  const value = editingMemoryValue.value.trim()
  if (!value) return

  savingMemoryId.value = id
  try {
    await memoryApi.update(id, { 
      attributeValue: value,
      isCore: editingMemoryIsCore.value
    })
    const idx = store.memories.findIndex((m: any) => m.id === id)
    if (idx !== -1) {
      store.memories[idx] = { 
        ...store.memories[idx], 
        attributeValue: value,
        isCore: editingMemoryIsCore.value
      }
    }
    editingMemoryId.value = null
    editingMemoryValue.value = ''
  } catch (e) {
    logWarn('memory', '保存记忆失败', id, e)
  } finally {
    savingMemoryId.value = null
  }
}

function cancelEditMemory() {
  editingMemoryId.value = null
  editingMemoryValue.value = ''
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
  transition: all var(--duration-normal) var(--ease-out);
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
.memory-key {
  font-family: var(--font-serif);
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--color-primary);
}
.memory-value {
  font-size: 0.95rem;
  color: var(--color-text);
  line-height: 1.6;
  white-space: pre-wrap;
}
.memory-edit-input {
  margin-top: 8px;
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
.memory-actions .n-button {
  font-size: 12px;
  padding: 0 12px;
  border-radius: var(--radius-full);
}

.profile-header-controls { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.profile-header-title { margin: 0; font-weight: bold; }
.mb-12 { margin-bottom: 12px; }
.profile-filter-row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.w-140 { width: 140px; }
.profile-tab-content { min-height: 400px; display: flex; flex-direction: column; }
.flex-center-full { flex: 1; display: flex; justify-content: center; align-items: center; }
.flex-wrap-gap-8 { display: flex; flex-wrap: wrap; gap: 8px; }
.ml-6 { margin-left: 6px; }
.empty-profile-state { text-align: center; color: var(--color-text-light); padding: 40px 20px; }
.profile-details-section { margin-top: 24px; padding-top: 16px; border-top: 1px dashed var(--color-border); }
.details-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.details-title { font-size: 16px; font-weight: bold; margin: 0; color: var(--color-text); }
.profile-detail-card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: 8px; padding: 12px; }
.details-content-text { font-size: 14px; color: var(--color-text-secondary); line-height: 1.6; white-space: pre-wrap; }
.details-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 12px; font-size: 12px; color: var(--color-text-light); }
.graph-preview-modal { width: 600px; max-width: 90vw; }
.preview-desc { margin-top: 0; color: var(--color-text-secondary); font-size: 13px; }
.preview-list-panel { max-height: 35vh; overflow-y: auto; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 6px; padding: 12px; margin-bottom: 16px; }
.preview-item { margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--color-border); font-size: 13px; color: var(--color-text); }
.preview-item-key { font-weight: bold; color: var(--color-primary); margin-bottom: 4px; }
.preview-item-value { color: var(--color-text-secondary); line-height: 1.5; }
.flex-end-gap-12 { display: flex; gap: 12px; justify-content: flex-end; }

</style>
