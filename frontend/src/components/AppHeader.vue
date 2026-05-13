<template>
  <header class="masthead">
    <div class="masthead-top">
      <router-link to="/" class="brand-mark">
        <svg class="brand-mark-icon" width="22" height="22" viewBox="0 0 64 64" fill="none" aria-hidden="true">
          <rect x="14" y="11" width="36" height="42" rx="8" stroke="currentColor" stroke-width="4"/>
          <path d="M24 11V53" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
          <path d="M36.25 24.5C34.14 21.86 29.9 22.06 28.02 25.03C26.14 28 27.12 31.93 30.55 34.63L32 35.76L33.45 34.63C36.88 31.93 37.86 28 35.98 25.03C35.89 24.9 35.81 24.76 35.72 24.64C35.89 24.58 36.07 24.53 36.25 24.5ZM36.25 24.5C38.31 21.62 42.78 21.62 44.84 24.5C46.96 27.47 45.98 31.46 42.45 34.23L37.8 37.88" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        MoodCopilot
      </router-link>
      <nav class="masthead-nav">
        <template v-if="auth.isAuthenticated">
          <div class="nav-links desktop-only">
            <router-link
              v-for="item in navItems"
              :key="item.path"
              :to="item.path"
              :class="['nav-link', item.cls, { active: route.path === item.path }]"
            >{{ item.label }}</router-link>
          </div>
          <div class="nav-sep desktop-only" />
          <n-popover trigger="click" placement="bottom-end" @update:show="onQuotaPopoverShow">
            <template #trigger>
              <n-button text size="small" class="nav-bell" style="margin-right: 8px; font-weight: bold; color: #496c58">
                额度
              </n-button>
            </template>
            <div style="padding: 4px; min-width: 140px;">
              <p style="margin: 0 0 8px; font-weight: bold; font-size: 13px; color: #2f2a24;">今日剩余 AI 额度</p>
              <ul style="margin: 0; padding-left: 18px; font-size: 13px; color: #555;">
                <li>聊天：{{ quotas.CHAT ?? '--' }} 次</li>
                <li>分析（含陪跑）：{{ quotas.ANALYSIS ?? '--' }} 次</li>
                <li>报告：{{ quotas.REPORT ?? '--' }} 次</li>
              </ul>
              <p style="margin: 8px 0 0; font-size: 11px; color: #888;">每日 0 点重置</p>
              <p v-if="quotaError" style="margin: 6px 0 0; font-size: 11px; color: #b15454;">{{ quotaError }}</p>
            </div>
          </n-popover>
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
          <router-link to="/settings" class="masthead-user-link">
            <img v-if="auth.avatar" :src="auth.avatar" class="avatar-sm-nav avatar-sm-nav-img" />
            <span v-else class="avatar-sm-nav">{{ auth.displayName?.charAt(0) }}</span>
            <span class="masthead-user-name">{{ auth.displayName }}</span>
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

  <nav v-if="auth.isAuthenticated" class="mobile-bottom-nav" aria-label="主要导航">
    <router-link
      v-for="item in mobileNavItems"
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
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NButton, NBadge, NPopover } from 'naive-ui'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore, type Notification } from '../stores/notification'
import { authApi } from '../api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const notif = useNotificationStore()

const quotas = ref<Record<string, number>>({})
const quotaError = ref('')

const navItems = computed(() => {
  const items = [
    { label: '广场', shortLabel: '广场', icon: '⌂', path: '/' },
    { label: '写日记', shortLabel: '写', icon: '✎', path: '/write' },
    { label: 'MoodCopilot', shortLabel: 'AI', icon: '◌', path: '/chat', cls: 'nav-link-ai' },
    { label: '关注', shortLabel: '关注', icon: '◎', path: '/following' },
    { label: '报告', shortLabel: '报告', icon: '▥', path: '/report' },
  ]
  if (auth.isAdmin) {
    items.push({ label: '审核', shortLabel: '审核', icon: '!', path: '/admin/reports' })
  }
  return items
})

const mobileNavItems = computed(() => {
  const items = [...navItems.value]
  items.push({ label: '我的', shortLabel: '我的', icon: '◍', path: '/settings' })
  return items
})

onMounted(() => {
  void notif.fetchUnreadCount()
})

function handleLogout() {
  auth.logout()
  router.push('/login')
}

function onPopoverShow(show: boolean) {
  if (show) notif.fetchNotifications()
}

async function onQuotaPopoverShow(show: boolean) {
  if (show) {
    quotaError.value = ''
    try {
      const res = await authApi.getQuota()
      quotas.value = res.data.data ?? {}
    } catch (e: any) {
      quotaError.value = e?.response?.data?.message || '额度加载失败'
    }
  }
}

function handleNotifClick(item: Notification) {
  if (!item.isRead) notif.markRead(item.id)
  if (item.type === 'SYSTEM' && !item.diaryId) {
    router.push('/')
  } else if (item.diaryId) {
    router.push(`/diary/${item.diaryId}`)
  }
}

function formatTime(value: string) {
  if (!value || value === 'null') return ''
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>
