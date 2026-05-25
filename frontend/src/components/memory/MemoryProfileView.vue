<template>
  <div class="tab-content">
    <div class="section-head" style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 16px;">
      <div style="display: flex; align-items: center; gap: 8px; flex-shrink: 0;">
        <p class="settings-label" style="margin: 0; font-weight: bold; white-space: nowrap;">我的记忆</p>
      </div>
      <n-button size="small" secondary type="primary" :loading="consolidatingMemory" @click="consolidateMemories">
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
    <div v-else-if="memories.length === 0" class="memory-empty" style="text-align: center; padding: 40px 0; color: var(--color-text-light);">
      MoodCopilot 正在默默观察你，多写点日记或和 MoodCopilot 聊天吧。
    </div>
    <div v-else class="memory-list">
      <div v-for="m in memories" :key="m.id" class="memory-item">
        <div class="memory-content">
          <span class="memory-key">{{ m.attributeKey }}</span>
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

    <!-- Memory Preview Modal -->
    <n-modal v-model:show="showMemoryPreviewModal" preset="card" title="👀 长期画像整理预览" style="width: 600px; max-width: 90vw;">
      <p style="margin-top: 0; color: var(--color-text-secondary); font-size: 13px;">MoodCopilot 已将你的碎片记忆重新梳理为以下核心画像。确认替换后，这些内容将覆盖旧的数据：</p>
      
      <div v-if="deletedMemories.length > 0" style="background: var(--color-surface-hover); border-left: 3px solid var(--color-border); padding: 8px 12px; margin-bottom: 16px; font-size: 12px; color: var(--color-text-secondary); border-radius: 4px; max-height: 15vh; overflow-y: auto;">
        <div style="margin-bottom: 4px;">以下旧画像将被合并或移除：</div>
        <div v-for="m in deletedMemories" :key="m.id">
          <del>{{ m.attributeKey }}: {{ m.attributeValue.length > 30 ? m.attributeValue.substring(0, 30) + '...' : m.attributeValue }}</del>
        </div>
      </div>

      <div class="preview-list" style="max-height: 35vh; overflow-y: auto; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 6px; padding: 12px; margin-bottom: 16px;">
        <div v-for="(item, index) in previewMemories" :key="index" style="margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--color-border);">
          <div style="font-weight: bold; color: var(--color-primary); margin-bottom: 4px;">
            {{ item.attributeKey }}
            <n-tag v-if="item.isCore" size="small" type="warning" style="margin-left: 8px;">核心</n-tag>
            <n-tag v-if="isMemoryNew(item)" size="small" type="success" style="margin-left: 8px;">✨ 已合并</n-tag>
            <n-tag v-else size="small" style="margin-left: 8px;">无变化</n-tag>
          </div>
          <div style="font-size: 13px; color: var(--color-text); white-space: pre-wrap;">{{ item.attributeValue }}</div>
        </div>
      </div>
      <template #action>
        <div style="display: flex; justify-content: flex-end; gap: 12px;">
          <n-button @click="showMemoryPreviewModal = false">取消</n-button>
          <n-button type="primary" :loading="applyingMemory" @click="applyMemoryConsolidation">确认替换</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { NButton, NSpin, NInput, NModal, NTag, NCheckbox, NPopover } from 'naive-ui'
import { memoryApi } from '../../api'
import { logWarn } from '../../utils/logger'

interface MemoryItem {
  id: number
  attributeKey: string
  attributeValue: string
  isCore?: boolean
}

const memories = ref<MemoryItem[]>([])
const memoriesLoading = ref(false)
const consolidatingMemory = ref(false)
const deletingMemoryId = ref<number | null>(null)
const editingMemoryId = ref<number | null>(null)
const editingMemoryValue = ref('')
const editingMemoryIsCore = ref(false)
const savingMemoryId = ref<number | null>(null)

const showMemoryPreviewModal = ref(false)
const previewMemories = ref<any[]>([])
const applyingMemory = ref(false)

const deletedMemories = computed(() => {
  return memories.value.filter(old => !previewMemories.value.some(newM => old.attributeKey === newM.attributeKey && old.attributeValue === newM.attributeValue))
})

onMounted(() => {
  loadMemories()
})

async function loadMemories() {
  memoriesLoading.value = true
  try {
    const res = await memoryApi.getAll()
    memories.value = (res.data.data ?? []) as MemoryItem[]
  } catch (e) {
    logWarn('memory', '加载记忆失败', e)
    memories.value = []
  } finally {
    memoriesLoading.value = false
  }
}

function isMemoryNew(item: any) {
  return !memories.value.some(old => old.attributeKey === item.attributeKey && old.attributeValue === item.attributeValue)
}

async function forgetMemory(id: number) {
  deletingMemoryId.value = id
  try {
    await memoryApi.forget(id)
    memories.value = memories.value.filter((m) => m.id !== id)
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
    const idx = memories.value.findIndex((m) => m.id === id)
    if (idx !== -1) {
      memories.value[idx] = { 
        ...memories.value[idx], 
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

async function consolidateMemories() {
  if (consolidatingMemory.value) return
  consolidatingMemory.value = true
  const loadingMsg = window.$message?.loading('MoodCopilot 正在努力整理中，由于数据量较大可能需要较长时间，你可以先去其他页面转转~', { duration: 0 })
  try {
    const res = await memoryApi.previewConsolidate()
    previewMemories.value = res.data.data || []
    if (previewMemories.value.length === 0) {
      window.$message?.warning('未能提取出有效的整合结果，请检查日记数量')
      if (loadingMsg) loadingMsg.destroy()
      return
    }
    if (loadingMsg) loadingMsg.destroy()
    showMemoryPreviewModal.value = true
  } catch (err: any) {
    if (loadingMsg) loadingMsg.destroy()
    if (err.response?.status === 429 || (err.response?.data?.message && err.response.data.message.includes('每天最多只能进行两次'))) {
      alert('每天最多只能进行两次智能整理，请明天再试吧')
    } else {
      logWarn('memory', '记忆预览失败', err)
      alert('记忆整理失败：' + (err.response?.data?.message || err.message))
    }
  } finally {
    consolidatingMemory.value = false
  }
}

async function applyMemoryConsolidation() {
  applyingMemory.value = true
  try {
    await memoryApi.applyConsolidate(previewMemories.value)
    showMemoryPreviewModal.value = false
    window.$message?.success('长久记忆已重构成功！')
    await loadMemories()
  } catch (err: any) {
    logWarn('memory', '应用整合失败', err)
    window.$message?.error('应用失败：' + (err.response?.data?.message || err.message))
  } finally {
    applyingMemory.value = false
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
.memory-key {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
}
.memory-value {
  font-size: 14px;
  color: var(--color-text-main);
  line-height: 1.5;
  white-space: pre-wrap;
}
.memory-edit-input {
  margin-top: 4px;
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
</style>
