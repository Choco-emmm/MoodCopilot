<template>
  <header class="masthead">
    <div class="masthead-top">
      <router-link to="/" class="brand-mark">
        <svg class="brand-mark-icon" width="28" height="28" viewBox="0 0 64 64" fill="none" aria-hidden="true">
          <rect x="14" y="11" width="36" height="42" rx="8" stroke="currentColor" stroke-width="4"/>
          <path d="M24 11V53" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
          <path d="M32 38C26.6 33.8 24 31.1 24 27.5C24 24.95 26 23 28.6 23C30.1 23 31.55 23.68 32.5 24.76C33.45 23.68 34.9 23 36.4 23C39 23 41 24.95 41 27.5C41 31.1 38.4 33.8 33 38L32.5 38.4L32 38Z" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span class="desktop-only" style="margin-left: 8px;">MoodCopilot</span>
      </router-link>
      <nav class="masthead-nav">
        <template v-if="auth.isAuthenticated">
          <div class="nav-links">
            <router-link
              v-for="item in navItems"
              :key="item.path"
              :to="item.path"
              :class="['nav-link', item.cls, { active: route.path === item.path }]"
            >
              <n-badge v-if="item.id === 'notif'" :value="notif.unreadCount" :max="99" :show="notif.unreadCount > 0" class="nav-bell-badge" dot>
                <span class="nav-link-icon" aria-hidden="true" v-html="item.icon"></span>
              </n-badge>
              <span v-else class="nav-link-icon" aria-hidden="true" v-html="item.icon"></span>
              <span class="nav-link-label">{{ item.shortLabel }}</span>
            </router-link>
          </div>
          <router-link to="/notifications" class="nav-notification-link" aria-label="通知">
            <span class="nav-notification-link-inner">
              <n-badge :value="notif.unreadCount" :max="99" :show="notif.unreadCount > 0" class="nav-bell-badge">
                <button class="nav-icon-btn nav-bell" aria-label="通知">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                    <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
                  </svg>
                </button>
              </n-badge>
            </span>
          </router-link>
          <router-link :to="profilePath" class="masthead-user-link">
            <img v-if="auth.avatar" :src="auth.avatar" class="avatar-sm-nav avatar-sm-nav-img" decoding="async" />
            <span v-else class="avatar-sm-nav">{{ auth.displayName?.charAt(0) }}</span>
            <span class="masthead-user-name">{{ auth.displayName }}</span>
            <span class="user-level-badge desktop-only">Lv.{{ auth.level }}</span>
            <span class="user-link-arrow">›</span>
          </router-link>
          <n-button text size="small" class="nav-logout desktop-only" @click="handleLogout">退出</n-button>
        </template>
        <template v-else>
          <n-button text type="primary" @click="router.push('/login')">登录</n-button>
          <n-button text type="primary" @click="router.push('/register')">注册</n-button>
        </template>
      </nav>
    </div>
    <h1 class="desktop-only">写下今天，慢慢理解自己。</h1>
    <p class="subtitle desktop-only">MoodCopilot 先帮你看见情绪；当你愿意时，再把你温和地连接给相似心情的人。</p>
  </header>
</template>

<script lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
</script>

<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { NButton, NBadge } from 'naive-ui'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notification'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const notif = useNotificationStore()

const profilePath = computed(() => (auth.userId != null ? `/profile/${auth.userId}` : '/login'))

// Quota popover data
const QUOTA_DATA = [
  { chat: 15,  analysis: 5,  reasoning: 2,  resonance: 0,  report: 0,  imageUpload: 3,  imageAnalysis: 2 },
  { chat: 25,  analysis: 8,  reasoning: 4,  resonance: 3,  report: 2,  imageUpload: 5,  imageAnalysis: 3 },
  { chat: 35,  analysis: 12, reasoning: 6,  resonance: 5,  report: 4,  imageUpload: 8,  imageAnalysis: 5 },
  { chat: 45,  analysis: 16, reasoning: 8,  resonance: 8,  report: 7,  imageUpload: 12, imageAnalysis: 8 },
  { chat: 55,  analysis: 20, reasoning: 10, resonance: 10, report: 11, imageUpload: 16, imageAnalysis: 12 },
  { chat: 65,  analysis: 25, reasoning: 12, resonance: 12, report: 16, imageUpload: 20, imageAnalysis: 15 },
]

const navItems = computed(() => {
  const homeIcon = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`
  const writeIcon = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>`
  const aiIcon = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/></svg>`
  const followIcon = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/></svg>`
  const reportIcon = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/></svg>`
  const memoryIcon = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5v14c0 1.66 4.03 3 9 3s9-1.34 9-3V5"/><path d="M3 12c0 1.66 4.03 3 9 3s9-1.34 9-3"/></svg>`
  const adminIcon = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>`
  const usersIcon = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>`
  const profileIcon = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`

  const bellIcon = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>`

  const items = [
    { label: '广场', shortLabel: '广场', icon: homeIcon, path: '/', cls: 'nav-link-home' },
    { label: 'MoodCopilot', shortLabel: 'AI', icon: aiIcon, path: '/chat', cls: 'nav-link-ai' },
    { label: '写日记', shortLabel: '写日记', icon: writeIcon, path: '/write', cls: 'nav-link-write' },
    { id: 'notif', label: '通知', shortLabel: '消息', icon: bellIcon, path: '/notifications', cls: 'nav-link-notif mobile-only' },
    { id: 'mine', label: '我的', shortLabel: '我的', icon: profileIcon, path: profilePath.value, cls: 'nav-link-mine mobile-only' },
  ]
  if (auth.isAdmin) {
    items.push(
      { label: '审核', shortLabel: '审核', icon: adminIcon, path: '/admin/reports' },
      { label: '用户', shortLabel: '用户', icon: usersIcon, path: '/admin/users' },
    )
  }
  return items
})

onMounted(() => {
  notif.connectRealtime()
  void notif.fetchUnreadCount()
})

onUnmounted(() => {
  notif.disconnectRealtime()
})

function handleLogout() {
  notif.disconnectRealtime()
  auth.logout()
  router.push('/login')
}

</script>

<style scoped>
/* ── Masthead hero text — 纸张温度的克制版杂志风 ── */
.masthead :deep(h1) {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  line-height: var(--leading-tight);
  font-weight: 700;
  max-width: 620px;
  margin: 0 0 0 0;
  text-align: left;
  color: var(--color-text);
  letter-spacing: 0.02em;
  /* 确保无论父级是 grid 还是 block，文字起始位置一致 */
  padding-top: 0;
}

.masthead :deep(.subtitle) {
  font-family: var(--font-body);
  font-size: var(--text-base);
  line-height: var(--leading-relaxed);
  max-width: 540px;
  margin: 0;
  text-align: left;
  color: var(--color-text-secondary);
}

@media (max-width: 768px) {
  .masthead :deep(h1) {
    font-size: 1.25rem;
    max-width: 100%;
  }

  .masthead :deep(.subtitle) {
    max-width: 100%;
    font-size: 13px;
  }
}
</style>
