export interface PendingChatEventContext {
  eventId: number
  title?: string
  description?: string
  targetDate?: string
  endDate?: string
  startTime?: string
  endTime?: string
}

export interface PendingChatDiaryContext {
  diaryId: number
  date?: string
  snippet?: string
  fullContent?: string
}

export const PENDING_CHAT_EVENT_KEY = 'pendingChatEventContext'
export const PENDING_CHAT_DIARY_KEY = 'pendingChatDiaryContext'

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

export function setPendingChatDiaryContext(context: PendingChatDiaryContext) {
  sessionStorage.setItem(PENDING_CHAT_DIARY_KEY, JSON.stringify(context))
}

export function consumePendingChatDiaryContext(): PendingChatDiaryContext | null {
  const raw = sessionStorage.getItem(PENDING_CHAT_DIARY_KEY)
  if (!raw) return null
  sessionStorage.removeItem(PENDING_CHAT_DIARY_KEY)
  try {
    const parsed = JSON.parse(raw) as PendingChatDiaryContext
    const diaryId = Number(parsed?.diaryId)
    return Number.isFinite(diaryId) && diaryId > 0 ? { ...parsed, diaryId } : null
  } catch {
    return null
  }
}
