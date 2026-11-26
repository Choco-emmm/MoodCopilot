import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  fetchOnboardingState,
  postOnboardingDismiss,
  postOnboardingEvent,
} from '../api/onboarding'
import {
  ONBOARDING_GUIDE_VERSION,
  ONBOARDING_EVENT_TYPES,
  buildOnboardingCacheKey,
  computeCoreTaskProgress,
  createDefaultOnboardingState,
  isChecklistActive,
  maxGuideStatus,
  mergeOnboardingStateMonotonic,
  normalizeOnboardingState,
  shouldAutoShowWelcome,
  type HintKey,
  type OnboardingDismissRequest,
  type OnboardingEventRequest,
  type OnboardingEventType,
  type OnboardingState,
  type TaskKey,
} from '../constants/onboarding'
import { logWarn } from '../utils/logger'

interface PendingRetryEvent {
  dedupeKey: string
  userId: number
  payload: OnboardingEventRequest
  attempts: number
}

const MAX_RETRY_ATTEMPTS = 3
const MIN_REFRESH_INTERVAL_MS = 15000
const RETRYABLE_EVENTS: ReadonlySet<OnboardingEventType> = new Set(ONBOARDING_EVENT_TYPES)

function isRetryableError(err: unknown): boolean {
  const status = (err as { response?: { status?: number } })?.response?.status
  if (typeof status === 'number') {
    return status >= 500 || status === 408 || status === 429
  }
  return true
}

function readCachedState(userId: number): OnboardingState | null {
  try {
    const key = buildOnboardingCacheKey(userId, ONBOARDING_GUIDE_VERSION, 'WEB')
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return normalizeOnboardingState(parsed)
  } catch {
    return null
  }
}

function writeCachedState(userId: number, state: OnboardingState): void {
  try {
    const key = buildOnboardingCacheKey(userId, ONBOARDING_GUIDE_VERSION, 'WEB')
    localStorage.setItem(key, JSON.stringify(state))
  } catch {
    // 静默忽略存储配额或隐私模式异常
  }
}

function buildEventDedupeKey(payload: OnboardingEventRequest): string {
  return [
    payload.eventType,
    payload.diaryId ?? '',
    payload.hintKey ?? '',
    payload.scope ?? 'WEB',
    payload.guideVersion ?? ONBOARDING_GUIDE_VERSION,
  ].join(':')
}

export const useOnboardingStore = defineStore('onboarding', () => {
  const boundUserId = ref<number | null>(null)
  const state = ref<OnboardingState>(createDefaultOnboardingState())
  const loading = ref(false)
  const loaded = ref(false)
  const degraded = ref(false)
  const replayMode = ref(false)
  const autoWelcomeActive = ref(false)
  const externalModalBlocking = ref(false)

  let sessionGen = 0
  let fetchSeq = 0
  let requestSeq = 0
  let lastFetchAt = 0
  let welcomeStartedSentForUser: number | null = null
  let autoWelcomeHandledForUser: number | null = null
  let analysisViewedReportedDiaries = new Set<number>()
  let memoryCenterReportedForUser: number | null = null
  let themeExploredReportedForUser: number | null = null
  let retryQueue: PendingRetryEvent[] = []
  let retryFlushing = false

  const coreProgress = computed(() => computeCoreTaskProgress(state.value))
  const canAutoShowWelcome = computed(() => {
    if (boundUserId.value == null || !loaded.value) return false
    if (autoWelcomeHandledForUser === boundUserId.value) return false
    return shouldAutoShowWelcome(state.value)
  })
  const checklistActive = computed(() => {
    if (boundUserId.value == null) return false
    return isChecklistActive(state.value)
  })
  const isWelcomeOpen = computed(() => replayMode.value || autoWelcomeActive.value)

  function isCurrentSession(gen: number, targetUserId: number): boolean {
    return gen === sessionGen && boundUserId.value === targetUserId
  }

  function isCurrentFetch(seq: number, gen: number, targetUserId: number): boolean {
    return seq === fetchSeq && isCurrentSession(gen, targetUserId)
  }

  function isCurrentMutation(seq: number, gen: number, targetUserId: number): boolean {
    return seq === requestSeq && isCurrentSession(gen, targetUserId)
  }

  function applyRemoteState(targetUserId: number, remote: OnboardingState): void {
    const merged = mergeOnboardingStateMonotonic(state.value, remote)
    state.value = merged
    loaded.value = true
    degraded.value = false
    writeCachedState(targetUserId, merged)
  }

  function reset(): void {
    sessionGen += 1
    fetchSeq += 1
    requestSeq += 1
    boundUserId.value = null
    state.value = createDefaultOnboardingState()
    loading.value = false
    loaded.value = false
    degraded.value = false
    replayMode.value = false
    autoWelcomeActive.value = false
    externalModalBlocking.value = false
    lastFetchAt = 0
    welcomeStartedSentForUser = null
    autoWelcomeHandledForUser = null
    analysisViewedReportedDiaries = new Set<number>()
    memoryCenterReportedForUser = null
    themeExploredReportedForUser = null
    retryQueue = []
    retryFlushing = false
  }

  function bindUser(userId: number): void {
    if (boundUserId.value === userId) return
    reset()
    boundUserId.value = userId
    const cached = readCachedState(userId)
    if (cached) {
      state.value = cached
    }
  }

  async function loadForUser(
    userId: number,
    options?: { force?: boolean },
  ): Promise<OnboardingState | null> {
    if (!Number.isFinite(userId) || userId <= 0) {
      reset()
      return null
    }

    const userChanged = boundUserId.value !== userId
    bindUser(userId)

    const now = Date.now()
    if (!options?.force && !userChanged && loaded.value && now - lastFetchAt < MIN_REFRESH_INTERVAL_MS) {
      return state.value
    }

    const gen = sessionGen
    const seq = ++fetchSeq
    loading.value = true
    try {
      const remote = await fetchOnboardingState()
      if (!isCurrentFetch(seq, gen, userId)) {
        return null
      }
      lastFetchAt = Date.now()
      applyRemoteState(userId, remote)
      void flushRetryQueue(userId)
      return state.value
    } catch (err) {
      if (isCurrentFetch(seq, gen, userId)) {
        degraded.value = true
        logWarn('onboarding', '加载引导状态失败，已静默降级', err)
      }
      return null
    } finally {
      if (isCurrentFetch(seq, gen, userId)) {
        loading.value = false
      }
    }
  }

  async function refreshIfActive(options?: { force?: boolean }): Promise<OnboardingState | null> {
    const userId = boundUserId.value
    if (userId == null) return null
    return loadForUser(userId, options)
  }

  async function refreshAfterBusinessAction(): Promise<OnboardingState | null> {
    const userId = boundUserId.value
    if (userId == null) return null
    return loadForUser(userId, { force: true })
  }

  function enqueueRetryEvent(userId: number, payload: OnboardingEventRequest): void {
    if (boundUserId.value !== userId) return
    if (!RETRYABLE_EVENTS.has(payload.eventType)) return
    const dedupeKey = buildEventDedupeKey(payload)
    if (retryQueue.some((item) => item.dedupeKey === dedupeKey && item.userId === userId)) {
      return
    }
    retryQueue.push({
      dedupeKey,
      userId,
      payload: { ...payload },
      attempts: 0,
    })
  }

  async function flushRetryQueue(targetUserId: number): Promise<void> {
    if (retryFlushing || boundUserId.value !== targetUserId || retryQueue.length === 0) {
      return
    }
    const gen = sessionGen
    retryFlushing = true
    try {
      while (retryQueue.length > 0 && isCurrentSession(gen, targetUserId)) {
        const item = retryQueue[0]
        if (item.userId !== targetUserId) {
          retryQueue.shift()
          continue
        }
        const seq = ++requestSeq
        try {
          const remote = await postOnboardingEvent(item.payload)
          if (!isCurrentMutation(seq, gen, targetUserId)) {
            return
          }
          retryQueue.shift()
          applyRemoteState(targetUserId, remote)
        } catch (err) {
          if (!isCurrentSession(gen, targetUserId)) {
            return
          }
          item.attempts += 1
          if (!isRetryableError(err) || item.attempts >= MAX_RETRY_ATTEMPTS) {
            retryQueue.shift()
          } else {
            break
          }
        }
      }
    } finally {
      retryFlushing = false
    }
  }

  async function recordEvent(payload: OnboardingEventRequest): Promise<OnboardingState | null> {
    const userId = boundUserId.value
    if (userId == null) return null

    // 乐观单调更新本地状态（仅白名单 UI 事件）
    applyOptimisticEvent(userId, payload)

    const gen = sessionGen
    const seq = ++requestSeq
    try {
      const remote = await postOnboardingEvent(payload)
      if (!isCurrentMutation(seq, gen, userId)) {
        return null
      }
      applyRemoteState(userId, remote)
      return state.value
    } catch (err) {
      if (isCurrentSession(gen, userId) && isRetryableError(err)) {
        enqueueRetryEvent(userId, payload)
      }
      logWarn('onboarding', `上报引导事件 ${payload.eventType} 失败，已静默降级`, err)
      return null
    }
  }

  function applyOptimisticEvent(userId: number, payload: OnboardingEventRequest): void {
    const next = normalizeOnboardingState(state.value)
    switch (payload.eventType) {
      case 'WELCOME_STARTED':
        next.welcomeStatus = maxGuideStatus(next.welcomeStatus, 'IN_PROGRESS')
        break
      case 'WELCOME_COMPLETED':
        next.welcomeStatus = maxGuideStatus(next.welcomeStatus, 'COMPLETED')
        break
      case 'ANALYSIS_VIEWED':
        // 真实分析查看由服务端校验 diaryId 后为准，不在此伪造完成
        return
      case 'MEMORY_CENTER_VISITED':
        next.tasks.MEMORY_CENTER_VISITED = maxGuideStatus(
          next.tasks.MEMORY_CENTER_VISITED,
          'COMPLETED',
        )
        break
      case 'THEME_EXPLORED':
        next.tasks.THEME_EXPLORED = maxGuideStatus(next.tasks.THEME_EXPLORED, 'COMPLETED')
        break
      case 'HINT_SEEN':
        if (payload.hintKey) {
          next.hints[payload.hintKey] = maxGuideStatus(next.hints[payload.hintKey], 'COMPLETED')
        }
        break
    }
    state.value = normalizeOnboardingState(next)
    writeCachedState(userId, state.value)
  }

  async function dismiss(payload: OnboardingDismissRequest): Promise<OnboardingState | null> {
    const userId = boundUserId.value
    if (userId == null) return null

    applyOptimisticDismiss(userId, payload)

    const gen = sessionGen
    const seq = ++requestSeq
    try {
      const remote = await postOnboardingDismiss(payload)
      if (!isCurrentMutation(seq, gen, userId)) {
        return null
      }
      applyRemoteState(userId, remote)
      return state.value
    } catch (err) {
      logWarn('onboarding', `关闭引导项 ${payload.target} 失败，已静默降级`, err)
      return null
    }
  }

  function applyOptimisticDismiss(userId: number, payload: OnboardingDismissRequest): void {
    const next = normalizeOnboardingState(state.value)
    switch (payload.target) {
      case 'WELCOME':
        next.welcomeStatus = maxGuideStatus(next.welcomeStatus, 'SKIPPED')
        break
      case 'CHECKLIST':
        next.checklistStatus = maxGuideStatus(next.checklistStatus, 'SKIPPED')
        break
      case 'TASK':
        if (payload.key && payload.key in next.tasks) {
          const taskKey = payload.key as TaskKey
          next.tasks[taskKey] = maxGuideStatus(next.tasks[taskKey], 'SKIPPED')
        }
        break
      case 'HINT':
        if (payload.key) {
          const hintKey = payload.key as HintKey
          next.hints[hintKey] = maxGuideStatus(next.hints[hintKey], 'SKIPPED')
        }
        break
    }
    state.value = normalizeOnboardingState(next)
    writeCachedState(userId, state.value)
  }

  async function recordAnalysisViewed(diaryId: number): Promise<OnboardingState | null> {
    const userId = boundUserId.value
    if (userId == null || !Number.isFinite(diaryId) || diaryId <= 0) return null
    if (state.value.tasks.FIRST_ANALYSIS_VIEWED === 'COMPLETED') return state.value
    if (analysisViewedReportedDiaries.has(diaryId)) return state.value
    analysisViewedReportedDiaries.add(diaryId)
    return recordEvent({ eventType: 'ANALYSIS_VIEWED', diaryId })
  }

  async function recordMemoryCenterVisited(): Promise<OnboardingState | null> {
    const userId = boundUserId.value
    if (userId == null) return null
    if (state.value.tasks.MEMORY_CENTER_VISITED === 'COMPLETED') return state.value
    if (memoryCenterReportedForUser === userId) return state.value
    memoryCenterReportedForUser = userId
    return recordEvent({ eventType: 'MEMORY_CENTER_VISITED' })
  }

  async function recordThemeExplored(): Promise<OnboardingState | null> {
    const userId = boundUserId.value
    if (userId == null) return null
    if (state.value.tasks.THEME_EXPLORED === 'COMPLETED') return state.value
    if (themeExploredReportedForUser === userId) return state.value
    themeExploredReportedForUser = userId
    return recordEvent({ eventType: 'THEME_EXPLORED' })
  }

  async function dismissChecklist(): Promise<OnboardingState | null> {
    return dismiss({ target: 'CHECKLIST' })
  }

  async function dismissTask(taskKey: TaskKey): Promise<OnboardingState | null> {
    if (state.value.tasks[taskKey] === 'COMPLETED' || state.value.tasks[taskKey] === 'SKIPPED') {
      return state.value
    }
    return dismiss({ target: 'TASK', key: taskKey })
  }

  function openAutoWelcome(): boolean {
    const userId = boundUserId.value
    if (userId == null || !canAutoShowWelcome.value) {
      return false
    }
    autoWelcomeHandledForUser = userId
    autoWelcomeActive.value = true
    if (welcomeStartedSentForUser !== userId) {
      welcomeStartedSentForUser = userId
      void recordEvent({ eventType: 'WELCOME_STARTED' })
    }
    return true
  }

  async function completeWelcome(): Promise<void> {
    if (replayMode.value) {
      replayMode.value = false
      return
    }
    autoWelcomeActive.value = false
    if (boundUserId.value != null) {
      autoWelcomeHandledForUser = boundUserId.value
      await recordEvent({ eventType: 'WELCOME_COMPLETED' })
    }
  }

  async function skipWelcome(): Promise<void> {
    if (replayMode.value) {
      replayMode.value = false
      return
    }
    autoWelcomeActive.value = false
    if (boundUserId.value != null) {
      autoWelcomeHandledForUser = boundUserId.value
      await dismiss({ target: 'WELCOME' })
    }
  }

  function openWelcomeReplay(): void {
    replayMode.value = true
  }

  function closeWelcomeReplay(): void {
    replayMode.value = false
  }

  function setExternalModalBlocking(blocking: boolean): void {
    externalModalBlocking.value = blocking
  }

  function getPendingRetryCount(): number {
    return retryQueue.length
  }

  return {
    boundUserId,
    state,
    loading,
    loaded,
    degraded,
    replayMode,
    autoWelcomeActive,
    externalModalBlocking,
    coreProgress,
    canAutoShowWelcome,
    checklistActive,
    isWelcomeOpen,
    loadForUser,
    refreshIfActive,
    refreshAfterBusinessAction,
    recordEvent,
    recordAnalysisViewed,
    recordMemoryCenterVisited,
    recordThemeExplored,
    dismiss,
    dismissChecklist,
    dismissTask,
    openAutoWelcome,
    completeWelcome,
    skipWelcome,
    openWelcomeReplay,
    closeWelcomeReplay,
    setExternalModalBlocking,
    getPendingRetryCount,
    reset,
  }
})
