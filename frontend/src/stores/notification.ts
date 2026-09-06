import { defineStore } from 'pinia'
import { ref, h } from 'vue'
import { notificationApi } from '../api'
import router from '../router'
import { logWarn } from '../utils/logger'

export interface Notification {
  id: number
  recipientUserId: number
  actorUserId: number | null
  diaryId: number | null
  lifeEventId: number | null
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
  const error = ref('')
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

  function isInsightUpdateType(type: string | null | undefined) {
    return type === 'PROFILE_UPDATED' || type === 'MEMORY_UPDATED' || type === 'GRAPH_UPDATED'
      || type === 'MEMORY_CONSOLIDATION_COMPLETED' || type === 'GRAPH_CONSOLIDATION_COMPLETED'
      || type === 'CHAPTER_UPDATED'
  }

  function createInsightUpdateToast(type: string, message?: string, diff?: any) {
    if (!window.$notification) return

    const title = type === 'PROFILE_UPDATED'
      ? '👤 画像资料更新'
        : type === 'MEMORY_UPDATED'
          ? '🧠 画像更新'
        : type === 'GRAPH_UPDATED'
          ? '🕸️ 图谱更新'
          : type === 'CHAPTER_UPDATED'
            ? '📖 时光画卷更新'
            : '✨ AI 整理完成'
    const fallbackMessage = type === 'PROFILE_UPDATED'
      ? '✨ 你的个人资料已更新'
        : type === 'MEMORY_UPDATED'
          ? '✨ AI 已更新了关于你的长期记忆'
        : type === 'GRAPH_UPDATED'
          ? '🕸️ AI 已提取了新的事件因果关系'
          : type === 'CHAPTER_UPDATED'
            ? '📖 时光画卷整理已完成'
            : message || '✨ AI 整理已完成'

    const nodes: any[] = []
    const lineStyle = (color: string) => ({
      style: `color: ${color}; margin-top: 4px; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;`
    })

    if (diff) {
      if (type === 'MEMORY_UPDATED') {
        diff.added?.forEach((item: any) => nodes.push(h('div', lineStyle('var(--color-success)'), `+ [${item.key}] ${item.value}`)))
        diff.updated?.forEach((item: any) => nodes.push(h('div', lineStyle('var(--color-warning)'), `~ [${item.key}] ${item.oldValue} ➔ ${item.newValue}`)))
        diff.deleted?.forEach((item: any) => nodes.push(h('div', { ...lineStyle('var(--color-error)'), style: lineStyle('var(--color-error)').style + ' text-decoration: line-through;' }, `- [${item.key}] ${item.value}`)))
      } else if (type === 'GRAPH_UPDATED') {
        diff.added?.forEach((item: any) => nodes.push(h('div', lineStyle('var(--color-success)'), `+ ${item.head} —[${item.relation}]→ ${item.tail}`)))
        diff.deleted?.forEach((item: any) => nodes.push(h('div', { ...lineStyle('var(--color-error)'), style: lineStyle('var(--color-error)').style + ' text-decoration: line-through;' }, `- ${item.head} —[${item.relation}]→ ${item.tail}`)))
      }
    }

    const MAX_DISPLAY_NODES = 3
    if (nodes.length > MAX_DISPLAY_NODES) {
      const hiddenCount = nodes.length - MAX_DISPLAY_NODES
      nodes.splice(MAX_DISPLAY_NODES)
      nodes.push(h('div', { style: 'color: var(--color-text-muted); margin-top: 8px; font-size: 12px; font-style: italic;' }, `...以及其他 ${hiddenCount} 项变更`))
    }

    window.$notification.create({
      title,
      content: () => h('div', null, [
        h('div', { style: 'font-weight: bold; margin-bottom: 8px;' }, message || fallbackMessage),
        ...nodes
      ]),
      meta: new Date().toLocaleTimeString(),
      duration: 12000,
      keepAliveOnHover: true
    })
  }

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
      } catch (e) {
        logWarn('ws', '心跳发送失败', e)
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

  const isConnecting = ref(false)

  async function connectRealtime() {
    const token = localStorage.getItem('token')
    if (!token) return
    if (socket.value && (socket.value.readyState === WebSocket.OPEN || socket.value.readyState === WebSocket.CONNECTING)) {
      return
    }
    if (isConnecting.value) return

    isConnecting.value = true
    try {
      manualClose.value = false
      clearReconnectTimer()
      clearFallbackPollTimer()

      // 先请求短期 ticket，再建立 WS 连接（避免 JWT 出现在 URL 日志中）
      let ticket: string
      try {
        const res = await notificationApi.wsTicket()
        ticket = res.data.data.ticket
      } catch (e) {
        logWarn('ws', '获取 WS ticket 失败', e)
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
            const notification = payload.data as Notification
            mergeIncomingNotification(notification)
            if (isInsightUpdateType(notification?.type) && notification?.isRead === false) {
              createInsightUpdateToast(notification.type, notification.message)
            }
          } else if (isInsightUpdateType(payload?.type)) {
            createInsightUpdateToast(payload.type, payload.data?.message, payload.data?.diff)
          }
        } catch (e) {
          logWarn('ws', '收到无法解析的 WS 消息', event.data, e)
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
    } finally {
      isConnecting.value = false
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
    } catch (e) { logWarn('notif', '获取未读数失败', e) }
    finally {
      unreadLoading.value = false
    }
  }

  async function fetchNotifications(page = 1, size = 20, append = false) {
    loading.value = true
    error.value = ''
    try {
      const res = await notificationApi.list(page, size)
      const nextItems = res.data.data
        .filter((n: Notification) => n.createdAt !== 'null')
        .map((n: Notification) => ({ ...n, isRead: n.isRead ?? false }))
      items.value = append
        ? [...items.value, ...nextItems.filter((item: Notification) => !items.value.some((existing) => existing.id === item.id))]
        : nextItems
      return nextItems.length
    } catch (e: any) {
      error.value = e?.response?.data?.message || '通知加载失败，请稍后重试。'
      return null
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
    if (unreadCount.value === 0) return

    try {
      await notificationApi.markAllRead()
      items.value = items.value.map((item) => ({
        ...item,
        isRead: true,
      }))
      unreadCount.value = 0
    } catch (e) { logWarn('notif', '标记全部已读失败', e) }
  }

  return {
    items,
    unreadCount,
    loading,
    error,
    fetchUnreadCount,
    fetchNotifications,
    markRead,
    markAllRead,
    connectRealtime,
    disconnectRealtime,
  }
})
