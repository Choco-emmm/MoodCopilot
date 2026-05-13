import { defineStore } from 'pinia'
import { ref } from 'vue'
import { notificationApi } from '../api'

export interface Notification {
  id: number
  recipientUserId: number
  actorUserId: number | null
  diaryId: number | null
  commentId: number | null
  type: string
  message: string
  isRead: boolean
  readAt: string | null
  createdAt: string
}

export const useNotificationStore = defineStore('notification', () => {
  const items = ref<Notification[]>([])
  const unreadCount = ref(0)
  const loading = ref(false)
  const unreadLoading = ref(false)
  const unreadFetchedAt = ref(0)
  const UNREAD_CACHE_MS = 30000

  async function fetchUnreadCount(force = false) {
    if (unreadLoading.value) return
    const now = Date.now()
    if (!force && unreadFetchedAt.value > 0 && now - unreadFetchedAt.value < UNREAD_CACHE_MS) {
      return
    }

    unreadLoading.value = true
    try {
      const res = await notificationApi.unreadCount()
      unreadCount.value = res.data.data.count
      unreadFetchedAt.value = Date.now()
    } catch { /* ignore */ }
    finally {
      unreadLoading.value = false
    }
  }

  async function fetchNotifications() {
    loading.value = true
    try {
      const res = await notificationApi.list()
      items.value = res.data.data
        .filter((n: Notification) => n.createdAt !== 'null')
        .map((n: Notification) => ({ ...n, isRead: n.isRead ?? false }))
    } catch {
      items.value = []
    } finally {
      loading.value = false
    }
  }

  async function markRead(id: number) {
    await notificationApi.markRead(id)
    const found = items.value.find((n) => n.id === id)
    if (found) {
      found.isRead = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
  }

  return { items, unreadCount, loading, fetchUnreadCount, fetchNotifications, markRead }
})
