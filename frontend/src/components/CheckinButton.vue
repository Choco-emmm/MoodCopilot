<template>
  <button
    class="nav-checkin-btn"
    :class="{ 'nav-checkin-btn--done': checkedInToday }"
    :disabled="checkingIn"
    @click="handleClick"
    :title="checkedInToday ? '已签到' : '点击签到，获取额外额度'"
  >
    <svg v-if="!checkedInToday" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="checkin-icon">
      <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line>
    </svg>
    <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" class="checkin-icon">
      <polyline points="20 6 9 17 4 12"></polyline>
    </svg>
    <span class="checkin-text-full">{{ checkingIn ? '...' : checkedInToday ? '已签到' : '签到 +' + exp }}</span>
    <span class="checkin-text-short">{{ checkingIn ? '...' : checkedInToday ? '已签' : '+' + exp }}</span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  checkedInToday: boolean
  checkingIn: boolean
  streak: number
}>()

const emit = defineEmits<{
  (e: 'checkin'): void
  (e: 'view-tasks'): void
}>()

const exp = computed(() => {
  const s = props.streak
  if (s >= 6) return 25
  return 10 + s * 2
})

function handleClick() {
  if (props.checkedInToday) {
    emit('view-tasks')
  } else {
    emit('checkin')
  }
}
</script>

<style scoped>
.nav-checkin-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  border-radius: 16px;
  background: color-mix(in oklab, var(--color-primary) 12%, transparent);
  color: var(--color-primary);
  border: none;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.nav-checkin-btn:hover:not(:disabled) {
  background: color-mix(in oklab, var(--color-primary) 20%, transparent);
}

.nav-checkin-btn--done {
  background: transparent;
  border: 1px solid color-mix(in oklab, var(--color-border-strong) 40%, transparent);
  color: var(--color-text-muted);
}

.nav-checkin-btn--done:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-secondary);
}

.nav-checkin-btn:disabled {
  cursor: default;
  opacity: 0.6;
}

.checkin-icon {
  flex-shrink: 0;
}

.checkin-text-short { display: none; }

@media (max-width: 600px) {
  .checkin-text-full { display: none; }
  .checkin-text-short { display: inline-block; white-space: nowrap; }
  .nav-checkin-btn { padding: 0 10px; font-size: 12px; height: 30px; gap: 4px; width: auto; min-width: max-content; }
}
</style>
