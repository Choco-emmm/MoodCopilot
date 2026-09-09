<template>
  <div class="chat-input-area">
    <div v-if="lastReplyError" class="chat-reply-error-bar">
      <span>{{ lastReplyError }}</span>
      <n-button size="tiny" text type="primary" :disabled="streaming || !canRetry" @click="$emit('retry')">
        重试回复
      </n-button>
    </div>
    
    <ReferenceBar
      ref="referenceBarRef"
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

    <div class="ds-input-container">
      <n-input
        type="textarea"
        class="ds-textarea"
        :autosize="{ minRows: 1, maxRows: 6 }"
        :value="draft"
        @update:value="$emit('update:draft', $event)"
        :placeholder="isCompressing ? '正在优化对话上下文，请稍候...' : '发消息或按住说话'"
        :disabled="streaming || isCompressing || disabled"
        :maxlength="1000"
        @focus="$emit('focus')"
        @keydown.enter.prevent="!isCompressing && !streaming && $emit('send-enter', $event)"
      />
      
      <div class="ds-action-row">
        <div class="ds-action-left">
          <button 
            class="ds-btn ds-toggle-btn" 
            :class="{ 'is-active': useReasoning }"
            @click="$emit('update:use-reasoning', !useReasoning)"
            :disabled="streaming || isCompressing || disabled"
          >
            <span class="ds-icon">⚛</span> 深度思考
          </button>
          
          <button 
            class="ds-btn ds-persona-btn"
            @click="$emit('open-persona')"
            :disabled="streaming || isCompressing || disabled"
          >
            <span class="ds-icon">🎭</span> 对话风格
          </button>
        </div>
        
        <div class="ds-action-right">
          <n-popover trigger="click" placement="top-end" :show-arrow="false" class="ds-plus-popover">
            <template #trigger>
              <button class="ds-icon-btn ds-plus-btn" :disabled="streaming || isCompressing || disabled">
                <span class="ds-plus-icon">+</span>
              </button>
            </template>
            <div class="ds-plus-menu">
              <div class="ds-plus-item" @click="$emit('load-recent-diaries'); referenceBarRef?.openDiaryPopover()">
                <span class="ds-item-icon">📔</span> 引用日记
              </div>
              <div class="ds-plus-item" @click="$emit('load-recent-events'); referenceBarRef?.openEventPopover()">
                <span class="ds-item-icon">📅</span> 引用事件
              </div>
              <div class="ds-plus-item" @click="handleImageUpload">
                <span class="ds-item-icon">🖼</span> 发送图片
              </div>
            </div>
          </n-popover>
          
          <button 
            class="ds-icon-btn ds-send-btn" 
            :class="{ 'can-send': draft.trim() && !streaming && !isCompressing }"
            :disabled="!draft.trim() || streaming || isCompressing || disabled"
            @click="$emit('send')"
          >
            <span class="ds-send-icon">↑</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NInput, NPopover, useMessage } from 'naive-ui'
import ReferenceBar from '../ReferenceBar.vue'

const message = useMessage()
const referenceBarRef = ref<InstanceType<typeof ReferenceBar> | null>(null)

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

function handleImageUpload() {
  message.info('发送图片功能将在后续版本接入', { duration: 3000 })
}
</script>

<style scoped>
.chat-input-area {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.chat-reply-error-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: color-mix(in oklab, var(--color-error) 10%, transparent);
  border: 1px solid color-mix(in oklab, var(--color-error) 20%, transparent);
  border-radius: 8px;
  color: var(--color-error);
  font-size: 13px;
}

.ds-input-container {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 24px;
  padding: 8px 12px 12px;
  transition: border-color 0.2s, box-shadow 0.2s;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ds-input-container:focus-within {
  border-color: var(--color-primary-light);
  box-shadow: 0 0 0 2px color-mix(in oklab, var(--color-primary) 15%, transparent);
}

.ds-textarea :deep(.n-input__textarea-el) {
  padding: 8px 4px !important;
  font-size: 15px;
  line-height: 1.6;
}

.ds-action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ds-action-left {
  display: flex;
  gap: 8px;
}

.ds-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 12px;
  border-radius: 16px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.ds-btn:hover:not(:disabled) {
  background: var(--color-surface-hover);
}

.ds-btn.is-active {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 10%, transparent);
}

.ds-icon {
  font-size: 14px;
}

.ds-action-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ds-icon-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.ds-plus-btn {
  background: var(--color-surface-hover);
  color: var(--color-text);
  font-size: 18px;
  font-weight: 300;
}

.ds-plus-btn:hover:not(:disabled) {
  background: color-mix(in oklab, var(--color-text) 15%, transparent);
}

.ds-send-btn {
  background: var(--color-surface-hover);
  color: var(--color-text-muted);
  font-size: 16px;
  font-weight: bold;
}

.ds-send-btn.can-send {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.ds-send-btn.can-send:hover {
  background: var(--color-primary-hover);
}

.ds-plus-menu {
  display: flex;
  flex-direction: column;
  min-width: 120px;
  padding: 4px;
}

.ds-plus-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 14px;
  color: var(--color-text);
  cursor: pointer;
  transition: background 0.2s;
}

.ds-plus-item:hover {
  background: var(--color-surface-hover);
}

.ds-item-icon {
  font-size: 16px;
}

@media (max-width: 640px) {
  .ds-input-container {
    border-radius: 20px;
    padding: 6px 10px 10px;
  }
  
  .ds-btn {
    padding: 0 10px;
    font-size: 12px;
  }
}
</style>
