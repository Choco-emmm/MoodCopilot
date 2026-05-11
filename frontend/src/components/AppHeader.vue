<template>
  <header class="masthead">
    <div class="masthead-top">
      <router-link to="/" class="brand-mark">MoodCopilot</router-link>
      <nav class="masthead-nav">
        <template v-if="auth.isAuthenticated">
          <div class="nav-links">
            <router-link
              v-for="item in navItems"
              :key="item.path"
              :to="item.path"
              :class="['nav-link', item.cls, { active: route.path === item.path }]"
            >{{ item.label }}</router-link>
          </div>
          <div class="nav-sep" />
          <n-popover trigger="click" placement="bottom-end" @update:show="onPopoverShow">
            <template #trigger>
              <n-badge :value="notif.unreadCount" :max="99" :show="notif.unreadCount > 0">
                <n-button text size="small" class="nav-bell">
                  <template #icon>
                    <span style="font-size: 16px">&#128276;</span>
                  </template>
                </n-button>
              </n-badge>
            </template>
            <div class="notif-popover">
              <div v-if="notif.items.length === 0" class="notif-empty">暂无通知</div>
              <div
                v-for="item in notif.items"
                :key="item.id"
                class="notif-item"
                :class="{ unread: !item.isRead }"
                @click="handleNotifClick(item)"
              >
                <p class="notif-msg">{{ item.message }}</p>
                <span class="notif-time">{{ formatTime(item.createdAt) }}</span>
              </div>
            </div>
          </n-popover>
          <span class="masthead-user">{{ auth.displayName }}</span>
          <n-button text size="small" class="nav-logout" @click="handleLogout">退出</n-button>
        </template>
        <template v-else>
          <n-button text type="primary" @click="router.push('/login')">登录</n-button>
          <n-button text type="primary" @click="router.push('/register')">注册</n-button>
        </template>
      </nav>
    </div>
    <h1>写下今天，慢慢理解自己。</h1>
    <p class="subtitle">MoodCopilot 先帮你看见情绪；当你愿意时，再把你温和地连接给相似心情的人。</p>
  </header>

  <nav v-if="auth.isAuthenticated" class="mobile-bottom-nav" aria-label="主要导航">
    <router-link
      v-for="item in navItems"
      :key="`mobile-${item.path}`"
      :to="item.path"
      :class="['mobile-nav-link', { active: route.path === item.path }]"
    >
      <span class="mobile-nav-icon" aria-hidden="true">{{ item.icon }}</span>
      <span>{{ item.shortLabel }}</span>
    </router-link>
  </nav>
</template>

<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { NButton, NBadge, NPopover } from 'naive-ui'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore, type Notification } from '../stores/notification'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const notif = useNotificationStore()

const navItems = [
  { label: '广场', shortLabel: '广场', icon: '⌂', path: '/' },
  { label: '写日记', shortLabel: '写', icon: '✎', path: '/write' },
  { label: 'MoodCopilot', shortLabel: 'AI', icon: '◌', path: '/chat', cls: 'nav-link-ai' },
  { label: '关注', shortLabel: '关注', icon: '◎', path: '/following' },
  { label: '报告', shortLabel: '报告', icon: '▥', path: '/report' },
]

notif.fetchUnreadCount()

function handleLogout() {
  auth.logout()
  router.push('/login')
}

function onPopoverShow(show: boolean) {
  if (show) notif.fetchNotifications()
}

function handleNotifClick(item: Notification) {
  if (!item.isRead) notif.markRead(item.id)
  if (item.diaryId) router.push(`/diary/${item.diaryId}`)
}

function formatTime(value: string) {
  if (!value || value === 'null') return ''
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>
