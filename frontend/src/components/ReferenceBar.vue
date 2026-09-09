<template>
  <div class="ref-bar-container">
    <!-- 只有当有引用项时才显示引用栏 -->
    <div v-show="items.length > 0" class="ref-bar">
      <div v-for="(item, i) in items" :key="i" class="ref-chip">
        <span class="ref-chip-label">{{ item.displayContent || item.content }}</span>
        <button class="ref-chip-remove" @click="$emit('remove', i)">×</button>
      </div>
    </div>

    <!-- 选择日记弹窗 -->
    <n-modal v-model:show="showDiaryPopover" preset="card" title="引用最近日记" style="width: 400px; max-width: 90vw;">
      <div v-if="loading" class="ref-modal-empty">正在加载最近日记...</div>
      <div v-else-if="errorMessage" class="ref-modal-empty">
        {{ errorMessage }}
        <n-button size="small" @click="$emit('retry-diaries')" style="margin-top: 8px;">重试</n-button>
      </div>
      <div v-else-if="recentDiaries.length === 0" class="ref-modal-empty">暂无最近日记</div>
      <div v-else class="ref-modal-list">
        <button
          v-for="d in recentDiaries"
          :key="d.id"
          class="ref-modal-option"
          @click="$emit('add', d.id + ''); showDiaryPopover = false;"
        >
          <span class="ref-modal-date">{{ d.date }}</span>
          <span class="ref-modal-snippet">{{ d.snippet }}</span>
        </button>
      </div>
    </n-modal>

    <!-- 选择事件弹窗 -->
    <n-modal v-model:show="showEventPopover" preset="card" title="引用重要事件" style="width: 400px; max-width: 90vw;">
      <div v-if="eventsLoading" class="ref-modal-empty">正在加载重要事件...</div>
      <div v-else-if="eventsErrorMessage" class="ref-modal-empty">
        {{ eventsErrorMessage }}
        <n-button size="small" @click="$emit('retry-events')" style="margin-top: 8px;">重试</n-button>
      </div>
      <div v-else-if="recentEvents.length === 0" class="ref-modal-empty">暂无重要事件</div>
      <div v-else class="ref-modal-list">
        <button
          v-for="event in recentEvents"
          :key="event.id"
          class="ref-modal-option"
          @click="$emit('add-event', event.id + ''); showEventPopover = false;"
        >
          <span class="ref-modal-date">{{ event.targetDate || '时间未填写' }}</span>
          <span class="ref-modal-snippet">{{ event.title }}</span>
        </button>
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { NModal, NButton } from 'naive-ui'

defineProps<{
  items: { label: string; content: string; displayContent?: string }[]
  recentDiaries: { id: number; date: string; snippet: string }[]
  recentEvents: { id: number; title: string; targetDate?: string }[]
  loading?: boolean
  eventsLoading?: boolean
  errorMessage?: string | null
  eventsErrorMessage?: string | null
}>()

defineEmits<{
  remove: [index: number]
  add: [diaryId: string]
  'retry-diaries': []
  'open-diaries': []
  'add-event': [eventId: string]
  'retry-events': []
  'open-events': []
  'open-persona': []
}>()

const showDiaryPopover = ref(false)
const showEventPopover = ref(false)

defineExpose({
  openDiaryPopover: () => {
    showDiaryPopover.value = true;
    showEventPopover.value = false;
  },
  openEventPopover: () => {
    showEventPopover.value = true;
    showDiaryPopover.value = false;
  }
})
</script>

<style scoped>
.ref-bar-container {
  display: flex;
  flex-direction: column;
}

.ref-modal-empty {
  padding: 24px;
  text-align: center;
  color: var(--color-text-muted);
}

.ref-modal-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 50vh;
  overflow-y: auto;
}

.ref-modal-option {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  cursor: pointer;
  text-align: left;
  transition: background 0.2s;
}

.ref-modal-option:hover {
  background: var(--color-surface-hover);
}

.ref-modal-date {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 4px;
}

.ref-modal-snippet {
  font-size: 14px;
  color: var(--color-text);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
