export interface PendingChatEventContext {
  eventId: number
  title?: string
  description?: string
  targetDate?: string
  endDate?: string
  startTime?: string
  endTime?: string
}

export const PENDING_CHAT_EVENT_KEY = 'pendingChatEventContext'

export function setPendingChatEventContext(context: PendingChatEventContext) {
  sessionStorage.setItem(PENDING_CHAT_EVENT_KEY, JSON.stringify(context))
}

export function consumePendingChatEventContext(): PendingChatEventContext | null {
  const raw = sessionStorage.getItem(PENDING_CHAT_EVENT_KEY)
  if (!raw) return null
  sessionStorage.removeItem(PENDING_CHAT_EVENT_KEY)
  try {
    const parsed = JSON.parse(raw) as PendingChatEventContext
    const eventId = Number(parsed?.eventId)
    return Number.isFinite(eventId) && eventId > 0 ? { ...parsed, eventId } : null
  } catch {
    return null
  }
}
