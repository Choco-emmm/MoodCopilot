import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ONBOARDING_GUIDE_VERSION,
  buildOnboardingCacheKey,
  createDefaultOnboardingState,
} from '../../constants/onboarding';

interface MockUniRequest {
  url: string;
  method?: string;
  data?: any;
  success: (res: any) => void;
  fail: (err: any) => void;
}

describe('mini-program onboarding store', () => {
  let storage: Map<string, any>;
  let currentPages: Array<{ route: string }>;
  let showToast: ReturnType<typeof vi.fn>;
  let emit: ReturnType<typeof vi.fn>;
  let switchTab: ReturnType<typeof vi.fn>;
  let navigateTo: ReturnType<typeof vi.fn>;
  let requestHandler: (options: MockUniRequest) => void;

  beforeEach(() => {
    vi.resetModules();
    vi.stubGlobal('wx', undefined);
    Reflect.deleteProperty(globalThis, 'wx');

    storage = new Map([
      ['token', 'token-a'],
      ['loginUserId', 101],
    ]);
    currentPages = [{ route: 'pages/index/index' }];
    showToast = vi.fn();
    emit = vi.fn();
    switchTab = vi.fn();
    navigateTo = vi.fn();
    requestHandler = (options) => {
      options.success({
        statusCode: 200,
        data: {
          code: 200,
          data: createDefaultOnboardingState({ enrolledVersion: 1, welcomeStatus: 'NOT_STARTED' }),
        },
      });
    };

    vi.stubGlobal('getCurrentPages', () => currentPages);
    vi.stubGlobal('uni', {
      getStorageSync: (k: string) => storage.get(k),
      setStorageSync: (k: string, v: any) => storage.set(k, v),
      removeStorageSync: (k: string) => storage.delete(k),
      showToast,
      $emit: emit,
      switchTab,
      navigateTo,
      request: (options: MockUniRequest) => requestHandler(options),
    });
  });

  it('degrades silently on 404, 500, 401, and network failure without toasts or false welcome popup', async () => {
    const store = await import('../onboarding');

    for (const mode of ['404', '500', '401', 'network'] as const) {
      store.resetOnboardingState();
      requestHandler = (options) => {
        if (mode === 'network') {
          options.fail({ errMsg: 'request:fail timeout' });
          return;
        }
        options.success({
          statusCode: Number(mode),
          data: { message: `error-${mode}` },
        });
      };

      const result = await store.initializeOnboardingForUser(101);
      expect(result).toBeNull();
      expect(store.welcomeOpen.value).toBe(false);
      expect(store.onboardingState.value.enrolledVersion).toBe(0);
      expect(showToast).not.toHaveBeenCalled();
      expect(emit).not.toHaveBeenCalledWith('unauthorized');
    }
  });

  it('discards stale responses after account switch or logout (generation race guard)', async () => {
    const pendingByUser: Record<string, (res: any) => void> = {};
    requestHandler = (options) => {
      const token = (options as any).header?.Authorization;
      pendingByUser[token] = options.success;
    };

    const store = await import('../onboarding');

    // Start slow request for user 101
    storage.set('token', 'token-101');
    const req101 = store.initializeOnboardingForUser(101);

    // Switch immediately to user 202 (existing user, enrolledVersion: 0)
    storage.set('token', 'token-202');
    storage.set('loginUserId', 202);
    const req202 = store.initializeOnboardingForUser(202);

    pendingByUser['Bearer token-202']({
      statusCode: 200,
      data: {
        code: 200,
        data: createDefaultOnboardingState({ enrolledVersion: 0, welcomeStatus: 'NOT_STARTED' }),
      },
    });
    await req202;

    // Now user 101's stale response arrives with enrolledVersion: 1
    pendingByUser['Bearer token-101']({
      statusCode: 200,
      data: {
        code: 200,
        data: createDefaultOnboardingState({ enrolledVersion: 1, welcomeStatus: 'NOT_STARTED' }),
      },
    });
    const staleResult = await req101;

    expect(staleResult).toBeNull();
    expect(store.onboardingBoundUserId.value).toBe(202);
    expect(store.onboardingState.value.enrolledVersion).toBe(0);
    expect(store.welcomeOpen.value).toBe(false);
    expect(storage.has(buildOnboardingCacheKey(101, ONBOARDING_GUIDE_VERSION, 'MINI_PROGRAM'))).toBe(false);
  });

  it('replays welcome locally without resetting progress or calling remote endpoints', async () => {
    const calledUrls: string[] = [];
    requestHandler = (options) => {
      calledUrls.push(`${options.method}:${options.url}`);
      options.success({
        statusCode: 200,
        data: {
          code: 200,
          data: createDefaultOnboardingState({
            enrolledVersion: 1,
            welcomeStatus: 'COMPLETED',
            tasks: {
              FIRST_DIARY: 'COMPLETED',
              FIRST_ANALYSIS_VIEWED: 'NOT_STARTED',
              FIRST_CHAT_REPLY: 'NOT_STARTED',
              MEMORY_CENTER_VISITED: 'NOT_STARTED',
              THEME_EXPLORED: 'NOT_STARTED',
            },
          }),
        },
      });
    };

    const store = await import('../onboarding');
    await store.initializeOnboardingForUser(101);
    expect(store.welcomeOpen.value).toBe(false);
    calledUrls.length = 0;

    store.openWelcomeReplay();
    expect(store.welcomeOpen.value).toBe(true);
    expect(store.welcomeReplayMode.value).toBe(true);

    store.markWelcomeDisplayed();
    await store.skipWelcome();
    expect(store.welcomeOpen.value).toBe(false);
    expect(store.welcomeReplayMode.value).toBe(false);
    expect(calledUrls).toEqual([]);
    expect(store.onboardingState.value.welcomeStatus).toBe('COMPLETED');
    expect(store.onboardingState.value.tasks.FIRST_DIARY).toBe('COMPLETED');

    // Replay + select action navigates without sending WELCOME_COMPLETED
    store.openWelcomeReplay();
    await store.completeWelcome('CHAT_WITH_AI');
    expect(calledUrls).toEqual([]);
    expect(switchTab).toHaveBeenCalledWith({ url: '/pages/chat/chat' });
  });

  it('queues failed idempotent UI events and clears them strictly on account switch or logout', async () => {
    let failPosts = true;
    const postedPayloads: Array<{ url: string; data: any; token: string }> = [];

    requestHandler = (options: any) => {
      if (options.method === 'GET') {
        options.success({
          statusCode: 200,
          data: {
            code: 200,
            data: createDefaultOnboardingState({ enrolledVersion: 1, welcomeStatus: 'NOT_STARTED' }),
          },
        });
        return;
      }
      postedPayloads.push({
        url: options.url,
        data: options.data,
        token: options.header?.Authorization || '',
      });
      if (failPosts) {
        options.fail({ errMsg: 'request:fail offline' });
      } else {
        options.success({
          statusCode: 200,
          data: {
            code: 200,
            data: createDefaultOnboardingState({ enrolledVersion: 1, welcomeStatus: 'SKIPPED' }),
          },
        });
      }
    };

    const store = await import('../onboarding');
    await store.initializeOnboardingForUser(101);
    expect(store.welcomeOpen.value).toBe(true);

    await store.skipWelcome();
    expect(store.welcomeOpen.value).toBe(false);
    expect(store.getPendingOnboardingRetryCount()).toBe(1);

    // Account switch must discard user 101's pending queue so it is never sent under user 202
    storage.set('token', 'token-202');
    storage.set('loginUserId', 202);
    failPosts = false;
    postedPayloads.length = 0;

    await store.initializeOnboardingForUser(202);
    expect(store.getPendingOnboardingRetryCount()).toBe(0);
    expect(postedPayloads).toHaveLength(0);
  });

  it('enforces single active-page ownership across mounted GlobalUI hosts', async () => {
    const store = await import('../onboarding');
    store.resetOnboardingState({ resetHosts: true });

    const indexHost = store.allocateOnboardingHostId();
    const profileHost = store.allocateOnboardingHostId();
    const settingsHost = store.allocateOnboardingHostId();

    currentPages = [{ route: 'pages/index/index' }];
    store.claimOnboardingHost(indexHost, '/pages/index/index');
    expect(store.isOnboardingHostActive(indexHost)).toBe(true);
    expect(store.isOnboardingHostActive(profileHost)).toBe(false);

    // Switch tab to profile (index page remains cached in Mini-Program)
    currentPages = [{ route: 'pages/profile/profile' }];
    store.claimOnboardingHost(profileHost, '/pages/profile/profile');
    expect(store.isOnboardingHostActive(indexHost)).toBe(false);
    expect(store.isOnboardingHostActive(profileHost)).toBe(true);

    // Navigate to settings
    currentPages = [{ route: 'pages/profile/profile' }, { route: 'pages/settings/settings' }];
    store.claimOnboardingHost(settingsHost, '/pages/settings/settings');
    expect(store.isOnboardingHostActive(settingsHost)).toBe(true);
    expect(store.isOnboardingHostActive(profileHost)).toBe(false);

    // Navigate back from settings -> releases settingsHost and restores profileHost
    currentPages = [{ route: 'pages/profile/profile' }];
    store.releaseOnboardingHost(settingsHost);
    expect(store.isOnboardingHostActive(profileHost)).toBe(true);
    expect(store.isOnboardingHostActive(indexHost)).toBe(false);

    // Switching back to cached tab via page-stack sync hands ownership back to indexHost
    currentPages = [{ route: 'pages/index/index' }];
    store.syncActiveOnboardingHostFromPages();
    expect(store.isOnboardingHostActive(indexHost)).toBe(true);
    expect(store.isOnboardingHostActive(profileHost)).toBe(false);
  });

  it('handles P0-C task events, per-task skip, checklist dismiss without forging completion, and auto-collapse', async () => {
    const posted: Array<{ url: string; data: any }> = [];
    let remoteState = createDefaultOnboardingState({
      enrolledVersion: 1,
      welcomeStatus: 'COMPLETED',
      checklistStatus: 'IN_PROGRESS',
    });

    requestHandler = (options) => {
      if (options.method === 'GET') {
        options.success({ statusCode: 200, data: { code: 200, data: remoteState } });
        return;
      }
      posted.push({ url: options.url, data: options.data });
      if (options.url.endsWith('/api/onboarding/events')) {
        if (options.data.eventType === 'ANALYSIS_VIEWED') {
          remoteState = {
            ...remoteState,
            tasks: { ...remoteState.tasks, FIRST_ANALYSIS_VIEWED: 'COMPLETED' },
          };
        } else if (options.data.eventType === 'MEMORY_CENTER_VISITED') {
          remoteState = {
            ...remoteState,
            tasks: { ...remoteState.tasks, MEMORY_CENTER_VISITED: 'COMPLETED' },
          };
        } else if (options.data.eventType === 'THEME_EXPLORED') {
          remoteState = {
            ...remoteState,
            tasks: { ...remoteState.tasks, THEME_EXPLORED: 'COMPLETED' },
          };
        }
      } else if (options.url.endsWith('/api/onboarding/dismiss')) {
        if (options.data.target === 'TASK' && options.data.key) {
          remoteState = {
            ...remoteState,
            tasks: { ...remoteState.tasks, [options.data.key]: 'SKIPPED' },
          };
        } else if (options.data.target === 'CHECKLIST') {
          remoteState = { ...remoteState, checklistStatus: 'SKIPPED' };
        }
      }
      options.success({ statusCode: 200, data: { code: 200, data: remoteState } });
    };

    const store = await import('../onboarding');
    await store.initializeOnboardingForUser(101);
    expect(store.isOnboardingChecklistActive.value).toBe(true);

    // Record ANALYSIS_VIEWED once & dedupe repeat call
    await store.recordAnalysisViewed(55);
    await store.recordAnalysisViewed(55);
    expect(store.onboardingState.value.tasks.FIRST_ANALYSIS_VIEWED).toBe('COMPLETED');
    expect(posted.filter((p) => p.data.eventType === 'ANALYSIS_VIEWED')).toHaveLength(1);
    expect(posted[0].data).toMatchObject({ eventType: 'ANALYSIS_VIEWED', diaryId: 55, scope: 'MINI_PROGRAM' });

    // Record MEMORY_CENTER_VISITED & THEME_EXPLORED once each
    await store.recordMemoryCenterVisited();
    await store.recordMemoryCenterVisited();
    await store.recordThemeExplored();
    await store.recordThemeExplored();
    expect(store.onboardingState.value.tasks.MEMORY_CENTER_VISITED).toBe('COMPLETED');
    expect(store.onboardingState.value.tasks.THEME_EXPLORED).toBe('COMPLETED');
    expect(store.onboardingProgress.value.completed).toBe(2);
    expect(store.onboardingProgress.value.total).toBe(4);

    // Skip single task marks SKIPPED, never COMPLETED
    await store.dismissTask('FIRST_CHAT_REPLY');
    expect(store.onboardingState.value.tasks.FIRST_CHAT_REPLY).toBe('SKIPPED');
    expect(store.onboardingProgress.value.completed).toBe(2);

    // Dismissing checklist hides checklist without forging task completions
    await store.dismissChecklist();
    expect(store.onboardingState.value.checklistStatus).toBe('SKIPPED');
    expect(store.isOnboardingChecklistActive.value).toBe(false);
    expect(store.onboardingState.value.tasks.FIRST_DIARY).toBe('NOT_STARTED');

    // Refresh after business action merges real server completion monotonically
    remoteState = {
      ...remoteState,
      checklistStatus: 'IN_PROGRESS',
      tasks: {
        FIRST_DIARY: 'COMPLETED',
        FIRST_ANALYSIS_VIEWED: 'COMPLETED',
        FIRST_CHAT_REPLY: 'COMPLETED',
        MEMORY_CENTER_VISITED: 'COMPLETED',
        THEME_EXPLORED: 'COMPLETED',
      },
    };
    await store.refreshAfterBusinessAction();
    expect(store.onboardingProgress.value.allCompleted).toBe(true);
    expect(store.onboardingState.value.checklistStatus).toBe('COMPLETED');
    expect(store.isOnboardingChecklistActive.value).toBe(false);
  });
});
