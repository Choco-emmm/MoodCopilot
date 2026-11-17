<template>
  <nav v-if="total > size" class="memory-pagination" :aria-label="label">
    <button type="button" :disabled="disabled || current === 0" @click="$emit('update:page', current - 1)">上一页</button>
    <span role="status">{{ current * size + 1 }}–{{ Math.min((current + 1) * size, total) }} / 共 {{ total }} 条</span>
    <button type="button" :disabled="disabled || current >= pages - 1" @click="$emit('update:page', current + 1)">下一页</button>
  </nav>
</template>
<script setup lang="ts">
import { computed } from 'vue'
const props = withDefaults(defineProps<{ total: number; page: number; label: string; disabled?: boolean; size?: number }>(), { size: 3, disabled: false })
defineEmits<{ 'update:page': [page: number] }>()
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
const current = computed(() => Math.min(Math.max(0, props.page), pages.value - 1))
</script>
<style scoped>
.memory-pagination { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: var(--space-2); padding: var(--space-3) 0; color: var(--color-text-secondary); font: 12px/1.6 var(--font-body); font-variant-numeric: tabular-nums; }
button { min-height: 44px; min-width: 64px; padding: 0 var(--space-2); border: 1px solid var(--color-border-strong); border-radius: var(--radius-sm); background: transparent; color: var(--color-primary); font: inherit; cursor: pointer; }
button:hover:not(:disabled), button:active:not(:disabled) { background: var(--color-surface-hover); color: var(--color-primary-hover); }
button:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }
button:disabled { color: var(--color-text-muted); border-color: var(--color-border); cursor: not-allowed; }
</style>
