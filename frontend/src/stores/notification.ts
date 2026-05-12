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

  async function fetchUnreadCount() {
    try {
      const res = await notificationApi.unreadCount()
      unreadCount.value = res.data.data.count
    } catch { /* ignore */ }
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
