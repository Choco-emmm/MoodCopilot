import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  MINI_PROGRAM_TAB_BAR_PAGES,
  executeWelcomeAction,
  isMiniProgramTabBarPage,
  isRouteSafeForAutoWelcome,
  navigateMiniProgramRoute,
  normalizeMiniProgramRoute,
} from '../onboardingRouter';

describe('mini-program onboarding route adapter', () => {
  const switchTab = vi.fn();
  const navigateTo = vi.fn();

  beforeEach(() => {
    switchTab.mockReset();
    navigateTo.mockReset();
    vi.stubGlobal('uni', {
      switchTab,
      navigateTo,
    });
  });

  it('identifies all 4 tabBar pages regardless of leading slash or query string', () => {
    for (const tabPath of MINI_PROGRAM_TAB_BAR_PAGES) {
      expect(isMiniProgramTabBarPage(tabPath)).toBe(true);
      expect(isMiniProgramTabBarPage(tabPath.slice(1))).toBe(true);
      expect(isMiniProgramTabBarPage(`${tabPath}?tab=memory`)).toBe(true);
    }

    expect(isMiniProgramTabBarPage('/pages/write/write')).toBe(false);
    expect(isMiniProgramTabBarPage('pages/detail/detail?id=9')).toBe(false);
    expect(isMiniProgramTabBarPage('/pages/settings/settings')).toBe(false);
  });

  it('routes tabBar pages via uni.switchTab and strips query parameters', () => {
    navigateMiniProgramRoute('/pages/chat/chat');
    navigateMiniProgramRoute('pages/analysis/analysis?tab=memory');

    expect(switchTab).toHaveBeenCalledTimes(2);
    expect(switchTab).toHaveBeenNthCalledWith(1, { url: '/pages/chat/chat' });
    expect(switchTab).toHaveBeenNthCalledWith(2, { url: '/pages/analysis/analysis' });
    expect(navigateTo).not.toHaveBeenCalled();
  });

  it('routes non-tabBar pages via uni.navigateTo and preserves query parameters', () => {
    navigateMiniProgramRoute('/pages/write/write');
    navigateMiniProgramRoute('pages/detail/detail?id=123');

    expect(navigateTo).toHaveBeenCalledTimes(2);
    expect(navigateTo).toHaveBeenNthCalledWith(1, { url: '/pages/write/write' });
    expect(navigateTo).toHaveBeenNthCalledWith(2, { url: '/pages/detail/detail?id=123' });
    expect(switchTab).not.toHaveBeenCalled();
  });

  it('executes welcome actions with the right navigation method', () => {
    executeWelcomeAction('WRITE_TODAY');
    expect(navigateTo).toHaveBeenCalledWith({ url: '/pages/write/write' });
    expect(switchTab).not.toHaveBeenCalled();

    navigateTo.mockReset();
    executeWelcomeAction('CHAT_WITH_AI');
    expect(switchTab).toHaveBeenCalledWith({ url: '/pages/chat/chat' });
    expect(navigateTo).not.toHaveBeenCalled();

    switchTab.mockReset();
    executeWelcomeAction('EXPLORE_LATER');
    expect(switchTab).not.toHaveBeenCalled();
    expect(navigateTo).not.toHaveBeenCalled();
  });

  it('restricts auto-welcome to safe tabBar pages and blocks editing subpages', () => {
    expect(isRouteSafeForAutoWelcome('/pages/index/index')).toBe(true);
    expect(isRouteSafeForAutoWelcome('pages/profile/profile')).toBe(true);
    expect(isRouteSafeForAutoWelcome('/pages/write/write')).toBe(false);
    expect(isRouteSafeForAutoWelcome('pages/detail/detail')).toBe(false);
    expect(normalizeMiniProgramRoute('pages/index/index?foo=1#top')).toBe('/pages/index/index');
  });
});
