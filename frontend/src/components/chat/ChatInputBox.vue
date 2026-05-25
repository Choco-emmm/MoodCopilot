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
      :loading="recentDiariesLoading"
      :error-message="recentDiariesError"
      @remove="$emit('remove-ref', $event)"
      @add="$emit('add-diary-ref', $event)"
      @retry="$emit('load-recent-diaries')"
    />
    <div class="chat-input-row">
      <n-input
        :value="draft"
        @update:value="$emit('update:draft', $event)"
        size="large"
        placeholder="聊聊你今天的心情..."
        :disabled="streaming || disabled"
        :maxlength="500"
        @focus="$emit('focus')"
        @keydown.enter.prevent="$emit('send-enter', $event)"
      />
      <n-button type="primary" :disabled="!draft.trim() || streaming || disabled" @click="$emit('send')">
        {{ streaming ? '发送中' : '发送' }}
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
  lastReplyError: string | null
  canRetry: boolean
  references: any[]
  recentDiaries: any[]
  recentDiariesLoading: boolean
  recentDiariesError: string | null
}>()

defineEmits<{
  (e: 'update:draft', val: string): void
  (e: 'send'): void
  (e: 'send-enter', event: KeyboardEvent): void
  (e: 'retry'): void
  (e: 'remove-ref', idx: number): void
  (e: 'add-diary-ref', item: any): void
  (e: 'load-recent-diaries'): void
  (e: 'focus'): void
}>()
</script>
