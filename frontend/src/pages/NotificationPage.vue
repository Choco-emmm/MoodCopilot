<template>
  <main class="app-shell">
    <AppHeader />

    <section class="notification-page panel">
      <div class="section-title compact notification-page-header">
        <div>
          <h2>通知</h2>
          <p class="notification-page-subtitle">查看最新互动与系统提醒。</p>
        </div>
        <n-button
          v-if="hasUnread"
          size="small"
          secondary
          :loading="markingAll"
          :disabled="markingAll"
          @click="() => handleMarkAllRead()"
        >
          全部标为已读
        </n-button>
      </div>

      <div v-if="notif.loading && notif.items.length === 0" class="empty-state compact">
        <n-spin size="medium">加载中...</n-spin>
      </div>

      <div v-else-if="notif.error && notif.items.length === 0" class="empty-state compact">
        <p>{{ notif.error }}</p>
        <n-button type="primary" @click="() => loadNotifications()">重试</n-button>
      </div>

      <n-empty v-else-if="notif.items.length === 0" description="暂无通知" class="notification-page-empty" />

      <div v-else class="notification-page-list">
        <div v-if="notif.error" class="notification-page-error">
          <p>{{ notif.error }}</p>
          <n-button size="small" text type="primary" @click="loadNotifications(true)">重新加载</n-button>
        </div>
        <div
          v-for="item in notif.items"
          :key="item.id"
          class="notif-item notification-page-item"
          :class="{ unread: !item.isRead || initialUnreadIds.has(item.id) }"
          @click="handleNotifClick(item)"
        >
          <div
            :class="['notif-msg', 'md-content', { 'notif-msg-collapsed': shouldCollapseNotification(item) && !isNotificationExpanded(item.id) }]"
            v-html="renderNotification(item)"
          />
          <button
            v-if="shouldCollapseNotification(item)"
            type="button"
            class="notif-expand-btn"
            @click.stop="toggleNotificationExpand(item.id)"
          >
            {{ isNotificationExpanded(item.id) ? '收起' : '展开' }}
          </button>
          <span class="notif-time">{{ formatTime(item.createdAt) }}</span>
        </div>

        <div v-if="hasMore" class="notification-page-load-more">
          <n-button secondary block :loading="loadingMore" :disabled="loadingMore" @click="loadMore">
            加载更多
          </n-button>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NEmpty, NSpin } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { useNotificationStore, type Notification } from '../stores/notification'
import { renderSafeMarkdown } from '../utils/markdown'

const router = useRouter()
const notif = useNotificationStore()

const expandedNotificationIds = ref<number[]>([])
const initialUnreadIds = ref<Set<number>>(new Set())
const markingAll = ref(false)
const loadingMore = ref(false)
const page = ref(1)
const hasMore = ref(true)
const PAGE_SIZE = 20
const hasUnread = computed(() => notif.items.some((item) => !item.isRead) || initialUnreadIds.value.size > 0)

onMounted(() => {
  notif.connectRealtime()
  expandedNotificationIds.value = []
  initialUnreadIds.value = new Set()
  void notif.fetchUnreadCount(true)
  void loadNotifications(true)
})

function handleNotifClick(item: Notification) {
  if (!item.isRead) {
    void notif.markRead(item.id).catch(() => {})
  }
  if (item.type === 'SYSTEM' && !item.diaryId) {
    router.push('/')
    return
  }
  if (item.diaryId) {
    router.push(`/diary/${item.diaryId}`)
    return
  }
  router.push('/')
}

async function loadNotifications(reset = false) {
  if (reset) {
    page.value = 1
    const loaded = await notif.fetchNotifications(1, PAGE_SIZE)
    if (loaded == null) {
      hasMore.value = true
      return
    }
    
    notif.items.forEach(item => {
      if (!item.isRead) {
        initialUnreadIds.value.add(item.id)
      }
    })
    
    hasMore.value = loaded >= PAGE_SIZE
    if (notif.unreadCount > 0) {
      void notif.markAllRead()
    }
    return
  }

  const nextPage = page.value + 1
  const loaded = await notif.fetchNotifications(nextPage, PAGE_SIZE, true)
  if (loaded == null) {
    return
  }
  
  notif.items.forEach(item => {
    if (!item.isRead) {
      initialUnreadIds.value.add(item.id)
    }
  })
  
  page.value = nextPage
  hasMore.value = loaded >= PAGE_SIZE
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    await loadNotifications(false)
  } finally {
    loadingMore.value = false
  }
}

async function handleMarkAllRead() {
  if (markingAll.value) return
  markingAll.value = true
  try {
    await notif.markAllRead()
    initialUnreadIds.value.clear()
  } finally {
    markingAll.value = false
  }
}

function formatTime(value: string) {
  if (!value || value === 'null') return ''
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function renderNotification(item: Notification) {
  if (!item?.message) return ''
  let message = item.message
  
  if (item.isMarkdown === false) {
    return renderSafeMarkdown(message.replace(/\n/g, '  \n'))
  }
  return renderSafeMarkdown(message)
}

function shouldCollapseNotification(item: Notification) {
  if (!item?.message) return false
  const msg = item.type !== 'SYSTEM' ? item.message.replace(/<[^>]+>/g, '') : item.message
  return msg.length > 88 || msg.includes('\n') || msg.includes('**')
}

function isNotificationExpanded(id: number) {
  return expandedNotificationIds.value.includes(id)
}

function toggleNotificationExpand(id: number) {
  if (isNotificationExpanded(id)) {
    expandedNotificationIds.value = expandedNotificationIds.value.filter((itemId) => itemId !== id)
    return
  }
  expandedNotificationIds.value = [...expandedNotificationIds.value, id]
}
</script>