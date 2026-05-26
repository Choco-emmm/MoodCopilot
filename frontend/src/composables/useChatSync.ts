import { ref, type Ref } from 'vue'
import { nextTick } from 'vue'
import { logWarn } from '../utils/logger'
import { useScrollManager } from './useScrollManager'
import { type Message } from './useChatConversation'

const BASE_SYNC_INTERVAL = 3500
const MAX_BACKOFF = 60000
const BACKOFF_MULTIPLIER = 2

export function useChatSync(
  messages: Ref<Message[]>,
  activeConvId: Ref<number | null>,
  streaming: Ref<boolean>,
  creatingConversation: Ref<boolean>,
  loadFromBackend: (convId: number) => Promise<Message[]>,
  loadConversations: () => Promise<void>,
  scrollManager: ReturnType<typeof useScrollManager>,
) {
  let syncTimer: number | null = null
  let convListSyncTick = 0
  let currentBackoff = BASE_SYNC_INTERVAL

  function scheduleNextSync() {
    stopAutoSync()
    syncTimer = window.setTimeout(() => {
      syncFromServer(false)
    }, currentBackoff)
  }

  function startAutoSync() {
    stopAutoSync()
    currentBackoff = BASE_SYNC_INTERVAL
    scheduleNextSync()
  }

  function stopAutoSync() {
    if (syncTimer != null) {
      window.clearTimeout(syncTimer)
      syncTimer = null
    }
  }

  function handleVisibilityChange() {
    if (document.visibilityState === 'visible') {
      startAutoSync()
      syncFromServer(true)
      return
    }
    stopAutoSync()
  }

  function handleWindowFocus() {
    syncFromServer(true)
  }

  async function syncFromServer(forceScroll: boolean) {
    const convId = activeConvId.value
    if (!convId || streaming.value || creatingConversation.value || document.visibilityState !== 'visible') {
      scheduleNextSync()
      return
    }

    try {
      const latest = await loadFromBackend(convId)
      // 成功则重置回退
      currentBackoff = BASE_SYNC_INTERVAL

      const current = messages.value
      if (latest.length === 0) {
        scheduleNextSync()
        return
      }
      if (current.length > latest.length) {
        scheduleNextSync()
        return
      }

      const changed = !isSameMessageList(current, latest)
      const keepStickBottom = scrollManager.isNearBottom()
      if (changed) {
        messages.value = latest
        await nextTick()
        if (forceScroll || keepStickBottom) {
          scrollManager.scrollBottom()
        }
      }

      convListSyncTick += 1
      if (convListSyncTick % 3 === 0) {
        await loadConversations()
      }
    } catch (e: any) {
      // 429 或被限流时指数回退
      const status = e?.response?.status ?? e?.status
      if (status === 429 || status