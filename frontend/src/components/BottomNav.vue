<template>
  <nav class="bottom-nav">
    <router-link
      v-for="tab in tabs"
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
const tabs = [
  { path: '/', label: '广场', icon: '🏠' },
  { path: '/write', label: '写日记', icon: '✏️' },
  { path: '/chat', label: 'AI', icon: '💬' },
  { path: '/following', label: '关注', icon: '👥' },
  { path: '/report', label: '报告', icon: '📊' },
  { path: '/settings', label: '我的', icon: '👤' },
]
</script>
