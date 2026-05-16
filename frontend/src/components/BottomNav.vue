<template>
  <nav class="flex w-full items-center">
    <router-link
      v-for="tab in visibleTabs"
      :key="tab.path"
      :to="tab.path"
      class="bottom-tab"
      active-class="bottom-tab-active"
    >
      <span class="bottom-tab-icon">{{ tab.icon }}</span>
      <span class="bottom-tab-label">{{ tab.label }}</span>
    </router-link>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()

const baseTabs = [
  { path: '/', label: '广场', icon: '🏠' },
  { path: '/write', label: '写日记', icon: '✏️' },
  { path: '/chat', label: 'AI', icon: '💬' },
  { path: '/following', label: '关注', icon: '👥' },
  { path: '/report', label: '报告', icon: '📊' },
  { path: '/settings', label: '我的', icon: '👤' },
]

const visibleTabs = computed(() => {
  if (auth.isAdmin) {
    return [...baseTabs, { path: '/admin/reports', label: '审核', icon: '🛡️' }]
  }
  return baseTabs
})
</script>

<style scoped>
.bottom-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-width: 0;
  min-height: 48px;
  padding: 6px 2px;
  border-radius: 12px;
  color: var(--color-text-muted, #8b8680);
  font-size: 11px;
  font-weight: 600;
  line-height: 1.15;
  text-decoration: none;
  transition: color 0.2s, background 0.2s;
}

.bottom-tab-icon {
  display: grid;
  place-items: center;
  font-size: 20px;
  line-height: 1;
  margin-bottom: 2px;
}

.bottom-tab-active {
  color: var(--color-primary, #6366f1);
  background: rgba(99, 102, 241, 0.08);
}
</style>
