<template>
  <div class="ref-bar">
    <div v-for="(item, i) in items" :key="i" class="ref-chip">
      <span class="ref-chip-label">{{ item.displayContent || item.content }}</span>
      <button class="ref-chip-remove" @click="$emit('remove', i)">×</button>
    </div>
    
    <div class="ref-popover-wrapper">
      <button class="ref-add-btn" @click.stop="
        showDiaryPopover = !showDiaryPopover;
        showEventPopover = false;
        if (showDiaryPopover) $emit('open-diaries');
      ">+ 引用日记</button>

      <button class="ref-add-btn" @click.stop="
        showEventPopover = !showEventPopover;
        showDiaryPopover = false;
        if (showEventPopover) $emit('open-events');
      ">+ 引用事件</button>

      <div v-if="showDiaryPopover || showEventPopover" class="ref-popover-overlay" @click="closePopovers"></div>

      <div v-if="showDiaryPopover" class="ref-diary-popover custom-popover">
        <div v-if="loading" class="ref-diary-empty">正在加载最近日记...</div>
        <template v-else>
          <button
            v-for="d in recentDiaries"
            :key="d.id"
            class="ref-diary-option"
            @click="
              $emit('add', d.id + '');
              showDiaryPopover = false;
            "
          >
            <span class="ref-diary-date">{{ d.date }}</span>
            <span class="ref-diary-snippet">{{ d.snippet }}</span>
          </button>
          <div v-if="errorMessage" class="ref-diary-empty">
            {{ errorMessage }}
            <button class="ref-diary-retry" @click="$emit('retry-diaries')">重试</button>
          </div>
          <div v-else-if="recentDiaries.length === 0" class="ref-diary-empty">暂无最近日记</div>
        </template>
      </div>

      <div v-if="showEventPopover" class="ref-diary-popover custom-popover">
        <div v-if="eventsLoading" class="ref-diary-empty">正在加载重要事件...</div>
        <template v-else>
          <button
            v-for="event in recentEvents"
            :key="event.id"
            class="ref-diary-option"
            @click="
              $emit('add-event', event.id + '');
              showEventPopover = false;
            "
          >
            <span class="ref-diary-date">{{ event.targetDate || '时间未填写' }}</span>
            <span class="ref-diary-snippet">{{ event.title }}</span>
          </button>
          <div v-if="eventsErrorMessage" class="ref-diary-empty">
            {{ eventsErrorMessage }}
            <button class="ref-diary-retry" @click="$emit('retry-events')">重试</button>
          </div>
          <div v-else-if="recentEvents.length === 0" class="ref-diary-empty">暂无重要事件</div>
        </template>
      </div>
    </div>

    <button
      type="button"
      class="ref-persona-btn"
      aria-label="调整本会话风格"
      @click="$emit('open-persona')"
    >
      本会话风格
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

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

function closePopovers() {
  showDiaryPopover.value = false
  showEventPopover.value = false
}
</script>

<style scoped>
.ref-popover-wrapper {
  position: relative;
  display: inline-block;
}

.ref-persona-btn {
  margin-left: auto;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  padding: 5px 10px;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.ref-persona-btn:hover,
.ref-persona-btn:focus-visible {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.ref-popover-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 99;
}

.custom-popover {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 0;
  z-index: 100;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  padding: 4px 0;
  transform-origin: bottom left;
  animation: pop-in 0.15s ease-out;
}

@keyframes pop-in {
  from { opacity: 0; transform: scale(0.95) translateY(4px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}
</style>
