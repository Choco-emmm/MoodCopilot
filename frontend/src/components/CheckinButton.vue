<template>
  <button
    class="nav-checkin-btn"
    :class="{ 'nav-checkin-btn--done': checkedInToday }"
    :disabled="checkingIn"
    @click="handleClick"
  >
    <span class="checkin-text-full">{{ checkingIn ? '...' : checkedInToday ? '✓ 已签' : '签到 +' + exp }}</span>
    <span class="checkin-text-short">{{ checkingIn ? '...' : checkedInToday ? '✓' : '+' + exp }}</span>
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
  margin-right: 8px;
  padding: 2px 10px;
  border: 1px solid var(--color-primary);
  border-radius: 12px;
  background: transparent;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s, color 0.15s;
  flex-shrink: 0;
}

.nav-checkin-btn:hover:not(:disabled) {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.nav-checkin-btn--done {
  border-color: var(--color-border-strong);
  color: var(--color-text-muted);
  cursor: pointer;
}

.nav-checkin-btn--done:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-secondary);
}

.nav-checkin-btn:disabled {
  cursor: default;
  opacity: 0.6;
}

.checkin-text-short { display: none; }

@media (max-width: 600px) {
  .checkin-text-full { display: none; }
  .checkin-text-short { display: inline; }
  .nav-checkin-btn { margin-right: 5px; padding: 2px 7px; font-size: 11px; }
}
</style>
