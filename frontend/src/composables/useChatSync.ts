import { ref, type Ref } from 'vue'
import { nextTick } from 'vue'
import { logWarn } from '../utils/logger'
import { useScrollManager } from './useScrollManager'
import { type Message } from './useChatConversation'

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

  function startAutoSync() {
    stopAutoSync()
    syncTimer = window.setInterval(() => {
      syncFromServer(false)
    }, 3500)
  }

  function stopAutoSync() {
    if (syncTimer != null) {
      window.clearInterval(syncTimer)
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
    if (!convId || streaming.value || creatingConversation.value || document.visibilityState !== 'visible') return

    try {
      const latest = await loadFromBackend(convId)
      const current = messages.value
      if (latest.length === 0) return
      if (current.length > latest.length) return

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
    } catch (e) {
      logWarn('chat', '同步消息失败', e)
    }
  }

  function isSameMessageList(a: Message[], b: Message[]): boolean {
    if (a.length !== b.length) return false
    for (let i = 0; i < a.length; i += 1) {
      if (a[i].id !== b[i].id) return false
    }
    return true
  }

  function cleanup() {
    stopAutoSync()
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    window.removeEventListener('focus', handleWindowFocus)
  }

  return {
    startAutoSync, stopAutoSync,
    handleVisibilityChange, handleWindowFocus,
    syncFromServer, cleanup,
  }
}
