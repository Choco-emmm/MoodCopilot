<template>
  <div class="chat-input-area">
    <div v-if="lastReplyError" class="chat-reply-error-bar">
      <span>{{ lastReplyError }}</span>
      <n-button size="tiny" text type="primary" :disabled="streaming || !canRetry" @click="$emit('retry')">
        重试回复
      </n-button>
    </div>
    <ReferenceBar
      :items="references"
      :recent-diaries="recentDiaries"
      :recent-events="recentEvents"
      :loading="recentDiariesLoading"
      :events-loading="recentEventsLoading"
      :error-message="recentDiariesError"
      :events-error-message="recentEventsError"
      @remove="$emit('remove-ref', $event)"
      @add="$emit('add-diary-ref', $event)"
      @retry-diaries="$emit('load-recent-diaries')"
      @open-diaries="$emit('load-recent-diaries')"
      @add-event="$emit('add-event-ref', $event)"
      @retry-events="$emit('load-recent-events')"
      @open-events="$emit('load-recent-events')"
      @open-persona="$emit('open-persona')"
    />
    <div class="chat-input-row">
      <n-input
        :value="draft"
        @update:value="$emit('update:draft', $event)"
        size="large"
        :placeholder="isCompressing ? '正在优化对话上下文，请稍候...' : '聊聊你今天的心情...'"
        :disabled="streaming || isCompressing || disabled"
        :maxlength="500"
        @focus="$emit('focus')"
        @keydown.enter.prevent="!isCompressing && !streaming && $emit('send-enter', $event)"
      />
      <select
        class="chat-model-select"
        :value="useReasoning ? 'reasoning' : 'normal'"
        :disabled="streaming || isCompressing || disabled"
        aria-label="选择对话模型"
        @change="$emit('update:use-reasoning', ($event.target as HTMLSelectElement).value === 'reasoning')"
      >
        <option value="normal">普通对话</option>
        <option value="reasoning">深度思考</option>
      </select>
      <n-button type="primary" :disabled="!draft.trim() || streaming || isCompressing || disabled" @click="$emit('send')">
        {{ isCompressing ? '优化中...' : (streaming ? '发送中' : '发送') }}
      </n-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { NButton, NInput } from 'naive-ui'
import ReferenceBar from '../ReferenceBar.vue'

defineProps<{
  draft: string
  streaming: boolean
  disabled: boolean
  isCompressing?: boolean
  compressingMessage?: string
  useReasoning: boolean
  lastReplyError: string | null
  canRetry: boolean
  references: any[]
  recentDiaries: any[]
  recentEvents: any[]
  recentDiariesLoading: boolean
  recentEventsLoading: boolean
  recentDiariesError: string | null
  recentEventsError: string | null
}>()

defineEmits<{
  (e: 'update:draft', val: string): void
  (e: 'send'): void
  (e: 'send-enter', event: KeyboardEvent): void
  (e: 'update:use-reasoning', val: boolean): void
  (e: 'retry'): void
  (e: 'remove-ref', idx: number): void
  (e: 'add-diary-ref', item: any): void
  (e: 'add-event-ref', eventId: string): void
  (e: 'load-recent-diaries'): void
  (e: 'load-recent-events'): void
  (e: 'focus'): void
  (e: 'open-persona'): void
}>()
</script>

<style scoped>
.chat-model-select {
  min-width: 148px;
  height: 40px;
  padding: 0 28px 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font: inherit;
  font-size: 12px;
}

.chat-model-select:disabled {
  opacity: .6;
}

@media (max-width: 640px) {
  .chat-input-row {
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-rows: auto auto;
    align-items: stretch;
  }

  .chat-input-row :deep(.n-input) {
    grid-column: 1 / -1;
    grid-row: 1;
    min-width: 0;
    width: 100%;
  }

  .chat-model-select {
    grid-column: 1;
    grid-row: 2;
    width: 100%;
    min-width: 0;
    padding: 0 22px 0 10px;
    font-size: 11px;
  }

  .chat-input-row :deep(.n-button) {
    grid-column: 2;
    grid-row: 2;
    min-width: 72px;
    width: auto;
  }
}
</style>
