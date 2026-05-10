<template>
  <div class="ref-bar">
    <div v-for="(item, i) in items" :key="i" class="ref-chip">
      <span class="ref-chip-label">{{ item.label }}</span>
      <button class="ref-chip-remove" @click="$emit('remove', i)">×</button>
    </div>
    <n-popover trigger="click" placement="top-start">
      <template #trigger>
        <button class="ref-add-btn">+ 引用日记</button>
      </template>
      <div class="ref-diary-popover">
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
        <div v-if="recentDiaries.length === 0" class="ref-diary-empty">暂无最近日记</div>
      </div>
    </n-popover>
  </div>
</template>

<script setup lang="ts">
import { NPopover } from 'naive-ui'

defineProps<{
  items: { label: string; content: string }[]
  recentDiaries: { id: number; date: string; snippet: string }[]
}>()

defineEmits<{
  remove: [index: number]
  add: [diaryId: string]
}>()
</script>
