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
          @click="handleMarkAllRead"
        >
          全部标为已读
        </n-button>
      </div>

      <div v-if="notif.loading" class="empty-state compact">
        <n-spin size="medium">加载中...</n-spin>
      </div>

      <n-empty v-else-if="notif.items.length === 0" description="暂无通知" class="notification-page-empty" />

      <div v-else class="notification-page-list">
        <div
          v-for="item in notif.items"
          :key="item.id"
          class="notif-item notification-page-item"
          :class="{ unread: !item.isRead }"
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
const markingAll = ref(false)
const hasUnread = computed(() => notif.items.some((item) => !item.isRead))

onMounted(() => {
  notif.connectRealtime()
  expandedNotificationIds.value = []
  void notif.fetchUnreadCount(true)
  void notif.fetchNotifications()
})

async function handleNotifClick(item: Notification) {
  if (!item.isRead) {
    await notif.markRead(item.id)
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

async function handleMarkAllRead() {
  if (!hasUnread.value || markingAll.value) return
  markingAll.value = true
  try {
    await notif.markAllRead()
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
  if (item.isMarkdown === false) {
    return renderSafeMarkdown(item.message.replace(/\n/g, '  \n'))
  }
  return renderSafeMarkdown(item.message)
}

function shouldCollapseNotification(item: Notification) {
  if (!item?.message) return false
  return item.message.length > 88 || item.message.includes('\n') || item.message.includes('**')
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