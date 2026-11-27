import { computed, ref } from 'vue';
import {
  ONBOARDING_GUIDE_VERSION,
  buildOnboardingCacheKey,
  computeCoreTaskProgress,
  createDefaultOnboardingState,
  isChecklistActive,
  isHintAllowedForScope,
  isHintPending,
  isOnboardingEnrolled,
  maxGuideStatus,
  mergeOnboardingStateMonotonic,
  normalizeOnboardingState,
  pickActiveHint,
  shouldAutoShowWelcome,
} from '../constants/onboarding';
import type {
  HintKey,
  OnboardingDismissRequest,
  OnboardingEventRequest,
  OnboardingHintDefinition,
  OnboardingState,
  PlatformGuideScope,
  TaskKey,
  WelcomeActionKey,
} from '../constants/onboarding';
import { get, post } from '../utils/request';
import {
  applyStarterPromptToDraft,
  describeWriteSaveNextStep,
  executeChecklistTaskAction,
  executeWelcomeAction,
  exitOnboardingDemoPage,
  hasRenderableOwnedAnalysis,
  isMiniProgramTabBarPage,
  isRouteSafeForAutoWelcome,
  navigateMiniProgramRoute,
  normalizeMiniProgramRoute,
  openOnboardingDemoPage,
  startRealWritingFromDemo,
} from '../utils/onboardingRouter';

export {
  applyStarterPromptToDraft,
  describeWriteSaveNextStep,
  executeChecklistTaskAction,
  executeWelcomeAction,
  exitOnboardingDemoPage,
  hasRenderableOwnedAnalysis,
  isMiniProgramTabBarPage,
  isRouteSafeForAutoWelcome,
  navigateMiniProgramRoute,
  normalizeMiniProgramRoute,
  openOnboardingDemoPage,
  startRealWritingFromDemo,
};

export const MINI_PROGRAM_GUIDE_SCOPE: PlatformGuideScope = 'MINI_PROGRAM';

interface QueuedOnboardingMutation {
  kind: 'event' | 'dismiss';
  userId: number;
  dedupeKey: string;
  payload: OnboardingEventRequest | OnboardingDismissRequest;
}

const MAX_RETRY_QUEUE_SIZE = 20;

export const onboardingBoundUserId = ref<number | null>(null);
export const onboardingState = ref<OnboardingState>(createDefaultOnboardingState());
export const onboardingLoaded = ref(false);
export const onboardingLoading = ref(false);
export const welcomeOpen = ref(false);
export const welcomeReplayMode = ref(false);
export const hintOverlayBlocked = ref(false);
export const activeOnboardingHostId = ref<string | null>(null);
export const activeOnboardingRoute = ref('');

export const onboardingProgress = computed(() => computeCoreTaskProgress(onboardingState.value));
export const isOnboardingEnrolledForCurrentUser = computed(() =>
  isOnboardingEnrolled(onboardingState.value),
);
export const isOnboardingChecklistActive = computed(() =>
  isChecklistActive(onboardingState.value),
);

let sessionGeneration = 0;
let fetchSeq = 0;
let hostCounter = 0;
let autoWelcomeSuppressed = false;
let welcomeStartedNotifiedUserId: number | null = null;
let memoryCenterReportedUserId: number | null = null;
let themeExploredReportedUserId: number | null = null;
const reportedAnalysisDiaryIds = new Set<number>();
const handledHintsInSession = new Set<HintKey>();
let flushingRetryQueue = false;
let retryQueue: QueuedOnboardingMutation[] = [];

const registeredHosts = new Map<string, string>();

function parseValidUserId(raw: unknown): number | null {
  const num = Number(raw);
  return Number.isFinite(num) && num > 0 ? Math.floor(num) : null;
}

function readTopPageRoute(): string {
  try {
    if (typeof getCurrentPages !== 'function') {
      return '';
    }
    const pages = getCurrentPages();
    if (!Array.isArray(pages) || pages.length === 0) {
      return '';
    }
    const topPage = pages[pages.length - 1] as { route?: string } | undefined;
    return normalizeMiniProgramRoute(topPage?.route || '');
  } catch {
    return '';
  }
}

function readCachedState(userId: number): OnboardingState | null {
  try {
    const key = buildOnboardingCacheKey(userId, ONBOARDING_GUIDE_VERSION, MINI_PROGRAM_GUIDE_SCOPE);
    const raw = uni.getStorageSync(key);
    if (!raw) return null;
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (!parsed || typeof parsed !== 'object') return null;
    return normalizeOnboardingState(parsed);
  } catch {
    return null;
  }
}

function writeCachedState(userId: number, state: OnboardingState): void {
  try {
    const key = buildOnboardingCacheKey(
      userId,
      state.guideVersion || ONBOARDING_GUIDE_VERSION,
      MINI_PROGRAM_GUIDE_SCOPE,
    );
    uni.setStorageSync(key, JSON.stringify(state));
  } catch {
    // Ignore storage quota or serialization errors.
  }
}

function isTransientFailure(error: unknown): boolean {
  const statusCode = (error as { statusCode?: number } | null)?.statusCode;
  if (typeof statusCode !== 'number') return true;
  if (statusCode === 0) return true;
  return statusCode >= 500;
}

function enqueueRetryMutation(item: QueuedOnboardingMutation): void {
  if (onboardingBoundUserId.value !== item.userId) return;
  const existingIndex = retryQueue.findIndex(
    (entry) => entry.userId === item.userId && entry.dedupeKey === item.dedupeKey,
  );
  if (existingIndex >= 0) {
    retryQueue[existingIndex] = item;
    return;
  }
  if (retryQueue.length >= MAX_RETRY_QUEUE_SIZE) {
    retryQueue.shift();
  }
  retryQueue.push(item);
}

function applyMergedState(userId: number, nextRemote: OnboardingState): OnboardingState {
  const merged = mergeOnboardingStateMonotonic(onboardingState.value, nextRemote);
  onboardingState.value = merged;
  onboardingLoaded.value = true;
  writeCachedState(userId, merged);

  if (
    !welcomeReplayMode.value &&
    welcomeOpen.value &&
    (merged.welcomeStatus === 'COMPLETED' || merged.welcomeStatus === 'SKIPPED')
  ) {
    welcomeOpen.value = false;
  }
  return merged;
}

function maybeTriggerAutoWelcome(state: OnboardingState): void {
  if (welcomeReplayMode.value) return;
  if (autoWelcomeSuppressed) return;
  if (!shouldAutoShowWelcome(state)) return;
  const currentRoute = activeOnboardingRoute.value || readTopPageRoute();
  if (!isRouteSafeForAutoWelcome(currentRoute)) return;
  welcomeOpen.value = true;
}

export function allocateOnboardingHostId(): string {
  hostCounter += 1;
  return `onboarding-host-${hostCounter}`;
}

export function claimOnboardingHost(hostId: string, pageRoute?: string): void {
  const resolvedRoute = normalizeMiniProgramRoute(pageRoute || readTopPageRoute());
  registeredHosts.set(hostId, resolvedRoute);
  activeOnboardingHostId.value = hostId;
  if (resolvedRoute) {
    activeOnboardingRoute.value = resolvedRoute;
  }
}

export function releaseOnboardingHost(hostId: string): void {
  registeredHosts.delete(hostId);
  if (activeOnboardingHostId.value !== hostId) return;

  const topRoute = readTopPageRoute();
  activeOnboardingRoute.value = topRoute;

  let fallbackHostId: string | null = null;
  if (topRoute) {
    for (const [candidateId, candidateRoute] of registeredHosts.entries()) {
      if (candidateRoute === topRoute) {
        fallbackHostId = candidateId;
      }
    }
  }
  activeOnboardingHostId.value = fallbackHostId;
}

export function syncActiveOnboardingHostFromPages(): void {
  const topRoute = readTopPageRoute();
  if (!topRoute) return;
  activeOnboardingRoute.value = topRoute;

  let matchedHostId: string | null = null;
  for (const [candidateId, candidateRoute] of registeredHosts.entries()) {
    if (candidateRoute === topRoute) {
      matchedHostId = candidateId;
    }
  }
  if (matchedHostId) {
    activeOnboardingHostId.value = matchedHostId;
  }
}

export function isOnboardingHostActive(hostId: string): boolean {
  if (activeOnboardingHostId.value !== hostId) {
    return false;
  }
  const topRoute = readTopPageRoute();
  const hostRoute = registeredHosts.get(hostId) || '';
  if (topRoute && hostRoute && topRoute !== hostRoute) {
    return false;
  }
  return true;
}

export function resetOnboardingState(options?: { resetHosts?: boolean }): void {
  sessionGeneration += 1;
  fetchSeq += 1;
  retryQueue = [];
  flushingRetryQueue = false;
  autoWelcomeSuppressed = false;
  welcomeStartedNotifiedUserId = null;
  memoryCenterReportedUserId = null;
  themeExploredReportedUserId = null;
  reportedAnalysisDiaryIds.clear();
  handledHintsInSession.clear();
  hintOverlayBlocked.value = false;

  onboardingBoundUserId.value = null;
  onboardingState.value = createDefaultOnboardingState();
  onboardingLoaded.value = false;
  onboardingLoading.value = false;
  welcomeOpen.value = false;
  welcomeReplayMode.value = false;

  if (options?.resetHosts) {
    registeredHosts.clear();
    activeOnboardingHostId.value = null;
    activeOnboardingRoute.value = '';
  }
}

export function suppressAutoWelcomeForContinuation(): void {
  autoWelcomeSuppressed = true;
  if (!welcomeReplayMode.value) {
    welcomeOpen.value = false;
  }
}

export async function initializeOnboardingForUser(
  rawUserId: number | string | null | undefined,
  options?: { suppressAutoWelcome?: boolean; forceRefresh?: boolean },
): Promise<OnboardingState | null> {
  const userId = parseValidUserId(rawUserId);
  if (!userId) {
    resetOnboardingState();
    return null;
  }

  const isAccountSwitch = onboardingBoundUserId.value !== userId;
  if (isAccountSwitch) {
    resetOnboardingState();
    onboardingBoundUserId.value = userId;
    const cached = readCachedState(userId);
    if (cached) {
      onboardingState.value = cached;
    }
  }

  if (options?.suppressAutoWelcome) {
    autoWelcomeSuppressed = true;
    if (!welcomeReplayMode.value) {
      welcomeOpen.value = false;
    }
  } else if (options?.suppressAutoWelcome === false) {
    autoWelcomeSuppressed = false;
  }

  if (!isAccountSwitch && onboardingLoaded.value && !options?.forceRefresh) {
    void flushOnboardingRetryQueue();
    maybeTriggerAutoWelcome(onboardingState.value);
    return onboardingState.value;
  }

  return fetchOnboardingState(userId);
}

export async function fetchOnboardingState(targetUserId?: number): Promise<OnboardingState | null> {
  const userId = targetUserId ?? onboardingBoundUserId.value;
  if (!userId || onboardingBoundUserId.value !== userId) {
    return null;
  }

  const capturedSessionGen = sessionGeneration;
  const capturedFetchSeq = ++fetchSeq;
  onboardingLoading.value = true;

  try {
    const res = await get<OnboardingState>(
      '/api/onboarding',
      { scope: MINI_PROGRAM_GUIDE_SCOPE },
      undefined,
      { silent: true },
    );
    if (
      capturedSessionGen !== sessionGeneration ||
      onboardingBoundUserId.value !== userId ||
      capturedFetchSeq !== fetchSeq
    ) {
      return null;
    }
    if (!res || res.code !== 200 || !res.data) {
      return null;
    }

    const remote = normalizeOnboardingState(res.data);
    const merged = applyMergedState(userId, remote);
    maybeTriggerAutoWelcome(merged);
    void flushOnboardingRetryQueue();
    return merged;
  } catch {
    // Silent degradation: never toast, never mark user as enrolled on API failure.
    return null;
  } finally {
    if (capturedSessionGen === sessionGeneration && capturedFetchSeq === fetchSeq) {
      onboardingLoading.value = false;
    }
  }
}

export async function refreshOnboardingOnForeground(): Promise<OnboardingState | null> {
  const token = uni.getStorageSync('token');
  const storedUserId = parseValidUserId(uni.getStorageSync('loginUserId'));
  const userId = onboardingBoundUserId.value ?? storedUserId;

  if (!token || !userId) {
    if (onboardingBoundUserId.value !== null) {
      resetOnboardingState();
    }
    return null;
  }

  return initializeOnboardingForUser(userId, { forceRefresh: true });
}

export async function recordOnboardingEvent(
  payload: Omit<OnboardingEventRequest, 'scope' | 'guideVersion'>,
): Promise<OnboardingState | null> {
  const userId = onboardingBoundUserId.value;
  if (!userId) return null;
  if (
    welcomeReplayMode.value &&
    (payload.eventType === 'WELCOME_STARTED' || payload.eventType === 'WELCOME_COMPLETED')
  ) {
    return null;
  }

  const capturedSessionGen = sessionGeneration;
  const requestBody: OnboardingEventRequest = {
    ...payload,
    scope: MINI_PROGRAM_GUIDE_SCOPE,
    guideVersion: onboardingState.value.guideVersion || ONBOARDING_GUIDE_VERSION,
  };
  const dedupeKey = `event:${payload.eventType}:${payload.diaryId ?? ''}:${payload.hintKey ?? ''}`;

  try {
    const res = await post<OnboardingState>(
      '/api/onboarding/events',
      requestBody,
      undefined,
      { silent: true },
    );
    if (capturedSessionGen !== sessionGeneration || onboardingBoundUserId.value !== userId) {
      return null;
    }
    if (res && res.code === 200 && res.data) {
      return applyMergedState(userId, normalizeOnboardingState(res.data));
    }
    return null;
  } catch (error) {
    if (
      capturedSessionGen === sessionGeneration &&
      onboardingBoundUserId.value === userId &&
      isTransientFailure(error)
    ) {
      enqueueRetryMutation({
        kind: 'event',
        userId,
        dedupeKey,
        payload: requestBody,
      });
    }
    return null;
  }
}

export async function dismissOnboarding(
  payload: Omit<OnboardingDismissRequest, 'scope' | 'guideVersion'>,
): Promise<OnboardingState | null> {
  const userId = onboardingBoundUserId.value;
  if (!userId) return null;

  const capturedSessionGen = sessionGeneration;
  const requestBody: OnboardingDismissRequest = {
    ...payload,
    scope: MINI_PROGRAM_GUIDE_SCOPE,
    guideVersion: onboardingState.value.guideVersion || ONBOARDING_GUIDE_VERSION,
  };
  const dedupeKey = `dismiss:${payload.target}:${payload.key ?? ''}`;

  try {
    const res = await post<OnboardingState>(
      '/api/onboarding/dismiss',
      requestBody,
      undefined,
      { silent: true },
    );
    if (capturedSessionGen !== sessionGeneration || onboardingBoundUserId.value !== userId) {
      return null;
    }
    if (res && res.code === 200 && res.data) {
      return applyMergedState(userId, normalizeOnboardingState(res.data));
    }
    return null;
  } catch (error) {
    if (
      capturedSessionGen === sessionGeneration &&
      onboardingBoundUserId.value === userId &&
      isTransientFailure(error)
    ) {
      enqueueRetryMutation({
        kind: 'dismiss',
        userId,
        dedupeKey,
        payload: requestBody,
      });
    }
    return null;
  }
}

export async function flushOnboardingRetryQueue(): Promise<void> {
  if (flushingRetryQueue || retryQueue.length === 0) return;
  const userId = onboardingBoundUserId.value;
  if (!userId) {
    retryQueue = [];
    return;
  }

  const capturedSessionGen = sessionGeneration;
  flushingRetryQueue = true;

  try {
    while (retryQueue.length > 0) {
      if (capturedSessionGen !== sessionGeneration || onboardingBoundUserId.value !== userId) {
        retryQueue = [];
        return;
      }

      const next = retryQueue[0];
      if (!next || next.userId !== userId) {
        retryQueue.shift();
        continue;
      }

      try {
        const endpoint = next.kind === 'event' ? '/api/onboarding/events' : '/api/onboarding/dismiss';
        const res = await post<OnboardingState>(endpoint, next.payload, undefined, { silent: true });
        if (capturedSessionGen !== sessionGeneration || onboardingBoundUserId.value !== userId) {
          retryQueue = [];
          return;
        }
        retryQueue.shift();
        if (res && res.code === 200 && res.data) {
          applyMergedState(userId, normalizeOnboardingState(res.data));
        }
      } catch (error) {
        if (!isTransientFailure(error)) {
          retryQueue.shift();
          continue;
        }
        break;
      }
    }
  } finally {
    flushingRetryQueue = false;
  }
}

export function markWelcomeDisplayed(): void {
  if (welcomeReplayMode.value) return;
  const userId = onboardingBoundUserId.value;
  if (!userId) return;
  if (welcomeStartedNotifiedUserId === userId) return;
  if (onboardingState.value.welcomeStatus !== 'NOT_STARTED') return;

  welcomeStartedNotifiedUserId = userId;
  onboardingState.value = {
    ...onboardingState.value,
    welcomeStatus: maxGuideStatus(onboardingState.value.welcomeStatus, 'IN_PROGRESS'),
  };
  writeCachedState(userId, onboardingState.value);
  void recordOnboardingEvent({ eventType: 'WELCOME_STARTED' });
}

export function openWelcomeReplay(): void {
  welcomeReplayMode.value = true;
  welcomeOpen.value = true;
}

export function closeWelcomeReplay(): void {
  welcomeOpen.value = false;
  welcomeReplayMode.value = false;
}

export async function skipWelcome(): Promise<OnboardingState | null> {
  const wasReplay = welcomeReplayMode.value;
  welcomeOpen.value = false;
  welcomeReplayMode.value = false;
  if (wasReplay) {
    return null;
  }

  const userId = onboardingBoundUserId.value;
  if (userId) {
    onboardingState.value = {
      ...onboardingState.value,
      welcomeStatus: maxGuideStatus(onboardingState.value.welcomeStatus, 'SKIPPED'),
    };
    writeCachedState(userId, onboardingState.value);
  }
  return dismissOnboarding({ target: 'WELCOME' });
}

export async function completeWelcome(actionKey: WelcomeActionKey): Promise<void> {
  const wasReplay = welcomeReplayMode.value;
  welcomeOpen.value = false;
  welcomeReplayMode.value = false;

  if (!wasReplay) {
    const userId = onboardingBoundUserId.value;
    if (userId) {
      onboardingState.value = {
        ...onboardingState.value,
        welcomeStatus: maxGuideStatus(onboardingState.value.welcomeStatus, 'COMPLETED'),
      };
      writeCachedState(userId, onboardingState.value);
    }
    void recordOnboardingEvent({ eventType: 'WELCOME_COMPLETED' });
  }

  executeWelcomeAction(actionKey);
}

export async function recordAnalysisViewed(diaryId: number): Promise<OnboardingState | null> {
  const userId = onboardingBoundUserId.value;
  const normalizedDiaryId = Number(diaryId);
  if (!userId || !Number.isFinite(normalizedDiaryId) || normalizedDiaryId <= 0) {
    return null;
  }
  const safeDiaryId = Math.floor(normalizedDiaryId);
  if (
    onboardingState.value.tasks.FIRST_ANALYSIS_VIEWED === 'COMPLETED' ||
    reportedAnalysisDiaryIds.has(safeDiaryId)
  ) {
    return onboardingState.value;
  }
  reportedAnalysisDiaryIds.add(safeDiaryId);
  const updated = await recordOnboardingEvent({
    eventType: 'ANALYSIS_VIEWED',
    diaryId: safeDiaryId,
  });
  if (!updated) {
    reportedAnalysisDiaryIds.delete(safeDiaryId);
  }
  return updated;
}

export async function recordMemoryCenterVisited(): Promise<OnboardingState | null> {
  const userId = onboardingBoundUserId.value;
  if (!userId) return null;
  if (
    onboardingState.value.tasks.MEMORY_CENTER_VISITED === 'COMPLETED' ||
    memoryCenterReportedUserId === userId
  ) {
    return onboardingState.value;
  }
  memoryCenterReportedUserId = userId;
  const updated = await recordOnboardingEvent({ eventType: 'MEMORY_CENTER_VISITED' });
  if (!updated && memoryCenterReportedUserId === userId) {
    memoryCenterReportedUserId = null;
  }
  return updated;
}

export async function recordThemeExplored(): Promise<OnboardingState | null> {
  const userId = onboardingBoundUserId.value;
  if (!userId) return null;
  if (
    onboardingState.value.tasks.THEME_EXPLORED === 'COMPLETED' ||
    themeExploredReportedUserId === userId
  ) {
    return onboardingState.value;
  }
  themeExploredReportedUserId = userId;
  const updated = await recordOnboardingEvent({ eventType: 'THEME_EXPLORED' });
  if (!updated && themeExploredReportedUserId === userId) {
    themeExploredReportedUserId = null;
  }
  return updated;
}

export async function dismissChecklist(): Promise<OnboardingState | null> {
  const userId = onboardingBoundUserId.value;
  if (!userId) return null;

  onboardingState.value = {
    ...onboardingState.value,
    checklistStatus: maxGuideStatus(onboardingState.value.checklistStatus, 'SKIPPED'),
  };
  writeCachedState(userId, onboardingState.value);
  return dismissOnboarding({ target: 'CHECKLIST' });
}

export async function dismissTask(taskKey: TaskKey): Promise<OnboardingState | null> {
  const userId = onboardingBoundUserId.value;
  if (!userId) return null;

  const currentStatus = onboardingState.value.tasks[taskKey];
  if (currentStatus === 'COMPLETED' || currentStatus === 'SKIPPED') {
    return onboardingState.value;
  }

  onboardingState.value = {
    ...onboardingState.value,
    tasks: {
      ...onboardingState.value.tasks,
      [taskKey]: maxGuideStatus(currentStatus, 'SKIPPED'),
    },
  };
  writeCachedState(userId, onboardingState.value);
  return dismissOnboarding({ target: 'TASK', key: taskKey });
}

export async function refreshAfterBusinessAction(): Promise<OnboardingState | null> {
  const token = uni.getStorageSync('token');
  const storedUserId = parseValidUserId(uni.getStorageSync('loginUserId'));
  const userId = onboardingBoundUserId.value ?? storedUserId;
  if (!token || !userId) {
    return null;
  }
  if (onboardingBoundUserId.value !== userId) {
    return initializeOnboardingForUser(userId, { suppressAutoWelcome: true, forceRefresh: true });
  }
  return fetchOnboardingState(userId);
}

export function setHintOverlayBlocked(blocked: boolean): void {
  hintOverlayBlocked.value = blocked;
}

export function canShowHint(
  hintKey: HintKey,
  options?: { overlayBlocked?: boolean },
): boolean {
  if (!onboardingBoundUserId.value || !onboardingLoaded.value) {
    return false;
  }
  if (welcomeOpen.value || welcomeReplayMode.value) {
    return false;
  }
  if (hintOverlayBlocked.value || options?.overlayBlocked) {
    return false;
  }
  if (!isHintAllowedForScope(hintKey, MINI_PROGRAM_GUIDE_SCOPE)) {
    return false;
  }
  if (handledHintsInSession.has(hintKey)) {
    return false;
  }
  return isHintPending(onboardingState.value, hintKey);
}

export function pickActiveMiniProgramHint(
  eligibleKeys: readonly HintKey[],
  options?: { overlayBlocked?: boolean },
): OnboardingHintDefinition | null {
  if (!onboardingBoundUserId.value || !onboardingLoaded.value) {
    return null;
  }
  if (welcomeOpen.value || welcomeReplayMode.value) {
    return null;
  }
  if (hintOverlayBlocked.value || options?.overlayBlocked) {
    return null;
  }
  const filteredKeys = eligibleKeys.filter((key) => !handledHintsInSession.has(key));
  return pickActiveHint(onboardingState.value, filteredKeys, MINI_PROGRAM_GUIDE_SCOPE);
}

export async function recordHintSeen(hintKey: HintKey): Promise<OnboardingState | null> {
  const userId = onboardingBoundUserId.value;
  if (!userId || !isHintAllowedForScope(hintKey, MINI_PROGRAM_GUIDE_SCOPE)) {
    return null;
  }
  if (!isHintPending(onboardingState.value, hintKey) || handledHintsInSession.has(hintKey)) {
    return onboardingState.value;
  }

  handledHintsInSession.add(hintKey);
  onboardingState.value = {
    ...onboardingState.value,
    hints: {
      ...onboardingState.value.hints,
      [hintKey]: maxGuideStatus(onboardingState.value.hints[hintKey], 'COMPLETED'),
    },
  };
  writeCachedState(userId, onboardingState.value);

  return recordOnboardingEvent({
    eventType: 'HINT_SEEN',
    hintKey,
  });
}

export async function dismissHint(hintKey: HintKey): Promise<OnboardingState | null> {
  const userId = onboardingBoundUserId.value;
  if (!userId || !isHintAllowedForScope(hintKey, MINI_PROGRAM_GUIDE_SCOPE)) {
    return null;
  }
  if (!isHintPending(onboardingState.value, hintKey) || handledHintsInSession.has(hintKey)) {
    return onboardingState.value;
  }

  handledHintsInSession.add(hintKey);
  onboardingState.value = {
    ...onboardingState.value,
    hints: {
      ...onboardingState.value.hints,
      [hintKey]: maxGuideStatus(onboardingState.value.hints[hintKey], 'SKIPPED'),
    },
  };
  writeCachedState(userId, onboardingState.value);

  return dismissOnboarding({
    target: 'HINT',
    key: hintKey,
  });
}

export function getPendingOnboardingRetryCount(): number {
  return retryQueue.length;
}
