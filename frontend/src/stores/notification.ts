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
  isMarkdown?: boolean
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
  const HEARTBEAT_INTERVAL_MS = 25000
  const RECONNECT_BASE_DELAY_MS = 1000
  const RECONNECT_MAX_DELAY_MS = 15000
  const socket = ref<WebSocket | null>(null)
  const reconnectTimer = ref<number | null>(null)
  const heartbeatTimer = ref<number | null>(null)
  const reconnectAttempts = ref(0)
  const fallbackPollTimer = ref<number | null>(null)
  const manualClose = ref(false)

  function clearReconnectTimer() {
    if (reconnectTimer.value == null) return
    window.clearTimeout(reconnectTimer.value)
    reconnectTimer.value = null
  }

  function clearFallbackPollTimer() {
    if (fallbackPollTimer.value == null) return
    window.clearInterval(fallbackPollTimer.value)
    fallbackPollTimer.value = null
  }

  function clearHeartbeatTimer() {
    if (heartbeatTimer.value == null) return
    window.clearInterval(heartbeatTimer.value)
    heartbeatTimer.value = null
  }

  function startHeartbeat() {
    clearHeartbeatTimer()
    heartbeatTimer.value = window.setInterval(() => {
      if (!socket.value || socket.value.readyState !== WebSocket.OPEN) return
      try {
        socket.value.send('ping')
      } catch {
        // ignore send errors and let close handler reconnect
      }
    }, HEARTBEAT_INTERVAL_MS)
  }

  function scheduleReconnect() {
    if (manualClose.value) return
    clearReconnectTimer()

    reconnectAttempts.value += 1
    const expDelay = Math.min(RECONNECT_MAX_DELAY_MS,
      RECONNECT_BASE_DELAY_MS * Math.pow(2, reconnectAttempts.value - 1))
    const jitter = Math.floor(Math.random() * 400)
    reconnectTimer.value = window.setTimeout(() => connectRealtime(), expDelay + jitter)
  }

  function startFallbackPolling() {
    if (fallbackPollTimer.value != null) return
    fallbackPollTimer.value = window.setInterval(() => {
      void fetchUnreadCount(true)
    }, 15000)
  }

  async function connectRealtime() {
    const token = localStorage.getItem('token')
    if (!token) return
    if (socket.value && (socket.value.readyState === WebSocket.OPEN || socket.value.readyState === WebSocket.CONNECTING)) {
      return
    }

    manualClose.value = false
    clearReconnectTimer()
    clearFallbackPollTimer()

    // 先请求短期 ticket，再建立 WS 连接（避免 JWT 出现在 URL 日志中）
    let ticket: string
    try {
      const res = await notificationApi.wsTicket()
      ticket = res.data.data.ticket
    } catch {
      // 获取 ticket 失败，启动轮询兜底
      startFallbackPolling()
      scheduleReconnect()
      return
    }

    const ws = new WebSocket(notificationApi.wsUrl(ticket))
    socket.value = ws

    ws.onopen = () => {
      reconnectAttempts.value = 0
      startHeartbeat()
      clearFallbackPollTimer()
      void fetchUnreadCount(true)
    }

    ws.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data)
        if (payload?.type === 'NOTIFICATION') {
          mergeIncomingNotification(payload.data as Notification)
        }
      } catch {
        // ignore malformed payload
      }
    }

    ws.onclose = () => {
      socket.value = null
      clearHeartbeatTimer()
      startFallbackPolling()
      scheduleReconnect()
    }

    ws.onerror = () => {
      ws.close()
    }
  }

  function disconnectRealtime() {
    manualClose.value = true
    clearReconnectTimer()
    clearHeartbeatTimer()
    clearFallbackPollTimer()
    if (socket.value) {
      socket.value.close()
      socket.value = null
    }
  }

  function mergeIncomingNotification(notification: Notification | null | undefined) {
    if (!notification || typeof notification.id !== 'number') return

    const idx = items.value.findIndex((n) => n.id === notification.id)
    if (idx >= 0) {
      const old = items.value[idx]
      items.value[idx] = { ...old, ...notification }
      if ((old.isRead ?? false) && notification.isRead === false) {
        unreadCount.value += 1
      }
      return
    }

    items.value.unshift({ ...notification, isRead: notification.isRead ?? false })
    if (notification.isRead === false) {
      unreadCount.value += 1
    }
  }

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

  async function markAllRead() {
    const unreadIds = items.value
      .filter((item) => !item.isRead)
      .map((item) => item.id)

    if (unreadIds.length === 0) return

    await Promise.all(unreadIds.map((id) => notificationApi.markRead(id)))

    items.value = items.value.map((item) => ({
      ...item,
      isRead: true,
    }))
    unreadCount.value = 0
  }

  return {
    items,
    unreadCount,
    loading,
    fetchUnreadCount,
    fetchNotifications,
    markRead,
    markAllRead,
    connectRealtime,
    disconnectRealtime,
  }
})
