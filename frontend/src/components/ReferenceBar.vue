<template>
  <div class="ref-bar">
    <div v-for="(item, i) in items" :key="i" class="ref-chip">
      <span class="ref-chip-label">{{ item.displayContent || item.content }}</span>
      <button class="ref-chip-remove" @click="$emit('remove', i)">×</button>
    </div>
    
    <div class="ref-popover-wrapper">
      <button class="ref-add-btn" @click.stop="showPopover = !showPopover">+ 引用日记</button>
      
      <div v-if="showPopover" class="ref-popover-overlay" @click="showPopover = false"></div>
      
      <div v-if="showPopover" class="ref-diary-popover custom-popover">
        <div v-if="loading" class="ref-diary-empty">正在加载最近日记...</div>
        <template v-else>
          <button
            v-for="d in recentDiaries"
            :key="d.id"
            class="ref-diary-option"
            @click="
              $emit('add', d.id + '');
              showPopover = false;
            "
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  items: { label: string; content: string; displayContent?: string }[]
  recentDiaries: { id: number; date: string; snippet: string }[]
  loading?: boolean
  errorMessage?: string | null
}>()

defineEmits<{
  remove: [index: number]
  add: [diaryId: string]
  retry: []
}>()

const showPopover = ref(false)
</script>

<style scoped>
.ref-popover-wrapper {
  position: relative;
  display: inline-block;
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
