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

const profilePath = computed(() => (auth.userId != null ? `/profile/${auth.userId}` : '/login'))

const baseTabs = computed(() => [
  { path: '/', label: '广场', icon: '览' },
  { path: '/write', label: '写日记', icon: '写' },
  { path: '/chat', label: 'AI', icon: '聊' },
  { path: '/following', label: '关注', icon: '关' },
  { path: '/report', label: '报告', icon: '报' },
  { path: profilePath.value, label: '我的', icon: '己' },
])

const visibleTabs = computed(() => {
  if (auth.isAdmin) {
    return [...baseTabs.value, { path: '/admin/reports', label: '审核', icon: '审' }]
  }
  return baseTabs.value
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
  font-size: 22px;
  font-weight: 800;
  line-height: 1;
  margin-bottom: 1px;
}

.bottom-tab-active {
  color: var(--color-primary);
  background: var(--color-primary-light);
}
</style>
