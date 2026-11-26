import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createDefaultOnboardingState } from '../../constants/onboarding';

vi.mock('@/utils/socket', () => ({
  connectWebSocket: vi.fn(),
  disconnectWebSocket: vi.fn(),
}));

describe('login continuation priority and onboarding lifecycle compatibility', () => {
  let storage: Map<string, any>;
  let currentPages: Array<{ route: string }>;
  let onboardingResponse: any;
  let wxLoginResponse: any;

  beforeEach(() => {
    vi.resetModules();
    vi.stubGlobal('wx', undefined);
    Reflect.deleteProperty(globalThis, 'wx');

    storage = new Map();
    currentPages = [{ route: 'pages/index/index' }];
    onboardingResponse = {
      statusCode: 200,
      data: {
        code: 200,
        data: createDefaultOnboardingState({ enrolledVersion: 1, welcomeStatus: 'NOT_STARTED' }),
      },
    };
    wxLoginResponse = {
      statusCode: 200,
      data: {
        code: 200,
        data: { token: 'wx-token-new', userId: 301 },
      },
    };

    vi.stubGlobal('getCurrentPages', () => currentPages);
    vi.stubGlobal('uni', {
      getStorageSync: (k: string) => storage.get(k),
      setStorageSync: (k: string, v: any) => storage.set(k, v),
      removeStorageSync: (k: string) => storage.delete(k),
      showLoading: vi.fn(),
      hideLoading: vi.fn(),
      showToast: vi.fn(),
      $emit: vi.fn(),
      switchTab: vi.fn(),
      navigateTo: vi.fn(),
      login: (options: any) => options.success({ code: 'mock-wx-code' }),
      request: (options: any) => {
        if (options.url.endsWith('/api/auth/wx-login')) {
          options.success(wxLoginResponse);
          return;
        }
        if (options.url.includes('/api/onboarding')) {
          options.success(onboardingResponse);
          return;
        }
        options.success({ statusCode: 200, data: { code: 200, data: null } });
      },
    });
  });

  it('runs login continuation first and suppresses auto-welcome during continuation navigation', async () => {
    const events: string[] = [];
    const login = await import('../login');
    const onboarding = await import('../onboarding');

    const allowed = login.requireLogin(() => {
      events.push('continuation:navigate-write');
      currentPages = [{ route: 'pages/index/index' }, { route: 'pages/write/write' }];
    });
    expect(allowed).toBe(false);
    expect(login.loginSheetVisible.value).toBe(true);

    login.loginWithWechat();
    await new Promise((resolve) => setTimeout(resolve, 20));

    expect(events).toEqual(['continuation:navigate-write']);
    expect(onboarding.onboardingBoundUserId.value).toBe(301);
    expect(onboarding.onboardingState.value.enrolledVersion).toBe(1);
    expect(onboarding.welcomeOpen.value).toBe(false);
  });

  it('opens auto-welcome for enrolled new users logging in without continuation', async () => {
    const login = await import('../login');
    const onboarding = await import('../onboarding');

    login.showLoginWithoutContinuation();
    login.loginWithWechat();
    await new Promise((resolve) => setTimeout(resolve, 20));

    expect(onboarding.onboardingBoundUserId.value).toBe(301);
    expect(onboarding.welcomeOpen.value).toBe(true);
  });

  it('does not trigger welcome for existing users (enrolledVersion === 0) or failed login', async () => {
    const login = await import('../login');
    const onboarding = await import('../onboarding');

    // Existing user
    onboardingResponse = {
      statusCode: 200,
      data: {
        code: 200,
        data: createDefaultOnboardingState({ enrolledVersion: 0, welcomeStatus: 'NOT_STARTED' }),
      },
    };
    login.showLoginWithoutContinuation();
    login.loginWithWechat();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(onboarding.onboardingBoundUserId.value).toBe(301);
    expect(onboarding.welcomeOpen.value).toBe(false);

    // Reset and test failed login
    onboarding.resetOnboardingState();
    wxLoginResponse = {
      statusCode: 200,
      data: { code: 400, message: '登录失败', data: null },
    };
    login.showLoginWithoutContinuation();
    login.loginWithWechat();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(onboarding.onboardingBoundUserId.value).toBeNull();
    expect(onboarding.welcomeOpen.value).toBe(false);
  });

  it('clears onboarding state cleanly on logout', async () => {
    const login = await import('../login');
    const onboarding = await import('../onboarding');
    const user = await import('../user');

    login.showLoginWithoutContinuation();
    login.loginWithWechat();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(onboarding.welcomeOpen.value).toBe(true);

    await user.logout();
    expect(onboarding.onboardingBoundUserId.value).toBeNull();
    expect(onboarding.welcomeOpen.value).toBe(false);
    expect(onboarding.onboardingState.value.enrolledVersion).toBe(0);
    expect(onboarding.getPendingOnboardingRetryCount()).toBe(0);
  });
});
