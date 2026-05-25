import { ref, onMounted, onUnmounted, type Ref } from 'vue'

/**
 * Chat-style scroll manager: sticky bottom, auto-scroll on new content,
 * and input visibility handling.
 */
export function useScrollManager(containerRef: Ref<HTMLElement | null>) {
  const isNearBottomThreshold = 80

  function isNearBottom(): boolean {
    const el = containerRef.value
    if (!el) return true
    return el.scrollHeight - el.scrollTop - el.clientHeight < isNearBottomThreshold
  }

  function scrollBottom(behavior: ScrollBehavior = 'smooth') {
    const el = containerRef.value
    if (!el) return
    if (behavior === 'smooth') {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
    } else {
      el.scrollTop = el.scrollHeight
    }
  }

  /** Call before appending new content; returns whether to auto-scroll after. */
  function shouldStickToBottom(): boolean {
    return isNearBottom()
  }

  /** Call after appending new content if shouldStickToBottom() was true. */
  function stickIfRequested(wasNearBottom: boolean, behavior: ScrollBehavior = 'smooth') {
    if (wasNearBottom) scrollBottom(behavior)
  }

  return { isNearBottom, scrollBottom, shouldStickToBottom, stickIfRequested }
}

/**
 * Infinite scroll via IntersectionObserver.
 * Used by FollowingPage, UserProfilePage, PublicFeed.
 *
 * @example
 * const sentinel = ref<HTMLElement | null>(null)
 * useInfiniteScroll(sentinel, () => loadMore(), { enabled: hasMore })
 */
export function useInfiniteScroll(
  sentinelRef: Ref<HTMLElement | null>,
  onLoadMore: () => void | Promise<void>,
  options: { enabled?: Ref<boolean>; rootMargin?: string } = {},
) {
  const { enabled, rootMargin = '200px' } = options
  let observer: IntersectionObserver | null = null

  function disconnect() {
    observer?.disconnect()
    observer = null
  }

  function connect() {
    disconnect()
    if (typeof IntersectionObserver === 'undefined') return
    const el = sentinelRef.value
    if (!el) return

    observer = new IntersectionObserver(
      (entries) => {
        if (!entries[0]?.isIntersecting) return
        if (enabled && !enabled.value) return
        onLoadMore()
      },
      { rootMargin },
    )
    observer.observe(el)
  }

  onMounted(connect)
  onUnmounted(disconnect)

  return { connect, disconnect }
}
