<template>
  <div class="ref-bar">
    <div v-for="(item, i) in items" :key="i" class="ref-chip">
      <span class="ref-chip-label">{{ item.content }}</span>
      <button class="ref-chip-remove" @click="$emit('remove', i)">×</button>
    </div>
    <n-popover trigger="click" placement="top-start">
      <template #trigger>
        <button class="ref-add-btn">+ 引用日记</button>
      </template>
      <div class="ref-diary-popover">
        <div v-if="loading" class="ref-diary-empty">正在加载最近日记...</div>
        <template v-else>
          <button
            v-for="d in recentDiaries"
            :key="d.id"
            class="ref-diary-option"
            @click="$emit('add', d.id + '')
                    ; /* close handled by popover */"
          >
            <span class="ref-diary-date">{{ d.date }}</span>
            <span class="ref-diary-snippet">{{ d.snippet }}</span>
          </button>
          <div v-if="errorMessage" class="ref-diary-empty">
            {{ errorMessage }}
            <button class="ref-diary-retry" @click="$emit('retry')">重试</button>
          </div>
          <div v-else-if="recentDiaries.length === 0" class="ref-diary-empty">暂无最近日记</div>
        </template>
      </div>
    </n-popover>
  </div>
</template>

<script setup lang="ts">
import { NPopover } from 'naive-ui'

defineProps<{
  items: { label: string; content: string }[]
  recentDiaries: { id: number; date: string; snippet: string }[]
  loading?: boolean
  errorMessage?: string | null
}>()

defineEmits<{
  remove: [index: number]
  add: [diaryId: string]
  retry: []
}>()
</script>
