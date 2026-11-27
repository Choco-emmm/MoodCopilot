import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  MINI_PROGRAM_TAB_BAR_PAGES,
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
} from '../onboardingRouter';

describe('mini-program onboarding route adapter', () => {
  const switchTab = vi.fn();
  const navigateTo = vi.fn();
  const redirectTo = vi.fn();
  const navigateBack = vi.fn();
  const setStorageSync = vi.fn();

  beforeEach(() => {
    switchTab.mockReset();
    navigateTo.mockReset();
    redirectTo.mockReset();
    navigateBack.mockReset();
    setStorageSync.mockReset();
    vi.stubGlobal('uni', {
      switchTab,
      navigateTo,
      redirectTo,
      navigateBack,
      setStorageSync,
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
    executeWelcomeAction('TRY_DEMO');
    expect(navigateTo).toHaveBeenCalledWith({ url: '/pages/onboarding-demo/onboarding-demo' });
    expect(switchTab).not.toHaveBeenCalled();

    navigateTo.mockReset();
    executeWelcomeAction('EXPLORE_LATER');
    expect(switchTab).not.toHaveBeenCalled();
    expect(navigateTo).not.toHaveBeenCalled();
  });

  it('routes demo entry/exit and real-write transition cleanly without prefill or storage writes', () => {
    openOnboardingDemoPage();
    expect(navigateTo).toHaveBeenCalledWith({ url: '/pages/onboarding-demo/onboarding-demo' });
    expect(isRouteSafeForAutoWelcome('/pages/onboarding-demo/onboarding-demo')).toBe(false);
    expect(isRouteSafeForAutoWelcome('pages/onboarding-demo/onboarding-demo')).toBe(false);

    startRealWritingFromDemo();
    expect(redirectTo).toHaveBeenCalledTimes(1);
    expect(redirectTo.mock.calls[0][0].url).toBe('/pages/write/write');
    expect(setStorageSync).not.toHaveBeenCalled();

    // Exit uses navigateBack and falls back to index tabBar on failure
    navigateBack.mockImplementationOnce((opts?: { fail?: () => void }) => {
      opts?.fail?.();
    });
    exitOnboardingDemoPage();
    expect(navigateBack).toHaveBeenCalledTimes(1);
    expect(switchTab).toHaveBeenCalledWith({ url: '/pages/index/index' });
  });

  it('restricts auto-welcome to safe tabBar pages and blocks editing subpages', () => {
    expect(isRouteSafeForAutoWelcome('/pages/index/index')).toBe(true);
    expect(isRouteSafeForAutoWelcome('pages/profile/profile')).toBe(true);
    expect(isRouteSafeForAutoWelcome('/pages/write/write')).toBe(false);
    expect(isRouteSafeForAutoWelcome('pages/detail/detail')).toBe(false);
    expect(normalizeMiniProgramRoute('pages/index/index?foo=1#top')).toBe('/pages/index/index');
  });

  it('routes all checklist tasks via switchTab for tabBar pages and navigateTo for subpages', () => {
    executeChecklistTaskAction('FIRST_DIARY');
    expect(navigateTo).toHaveBeenCalledWith({ url: '/pages/write/write' });

    navigateTo.mockReset();
    executeChecklistTaskAction('FIRST_ANALYSIS_VIEWED', { latestDiaryId: 88 });
    expect(navigateTo).toHaveBeenCalledWith({ url: '/pages/detail/detail?id=88' });

    navigateTo.mockReset();
    executeChecklistTaskAction('FIRST_ANALYSIS_VIEWED', { latestDiaryId: null });
    expect(navigateTo).toHaveBeenCalledWith({ url: '/pages/write/write' });

    navigateTo.mockReset();
    executeChecklistTaskAction('FIRST_CHAT_REPLY');
    executeChecklistTaskAction('MEMORY_CENTER_VISITED');
    executeChecklistTaskAction('THEME_EXPLORED');
    expect(navigateTo).not.toHaveBeenCalled();
    expect(switchTab).toHaveBeenNthCalledWith(1, { url: '/pages/chat/chat' });
    expect(switchTab).toHaveBeenNthCalledWith(2, { url: '/pages/analysis/analysis' });
    expect(switchTab).toHaveBeenNthCalledWith(3, { url: '/pages/profile/profile' });
  });

  it('applies starter prompts seamlessly between templates while requiring confirmation for hand-typed drafts', () => {
    const pool = ['帮我理清优先级', '回顾最近的心情变化'];
    expect(applyStarterPromptToDraft('', '帮我理清优先级', pool)).toEqual({
      nextDraft: '帮我理清优先级',
      applied: true,
      status: 'applied',
    });
    expect(applyStarterPromptToDraft('   \n ', '今天的一件小事：', pool)).toEqual({
      nextDraft: '今天的一件小事：',
      applied: true,
      status: 'applied',
    });
    // Switching directly between starter templates replaces seamlessly
    expect(
      applyStarterPromptToDraft('帮我理清优先级', '回顾最近的心情变化', pool),
    ).toEqual({
      nextDraft: '回顾最近的心情变化',
      applied: true,
      status: 'replaced_template',
    });
    // Hand-typed user draft requires explicit confirmation
    expect(applyStarterPromptToDraft('我已经写了半篇草稿', '不要覆盖我', pool)).toEqual({
      nextDraft: '我已经写了半篇草稿',
      applied: false,
      status: 'needs_confirm',
    });
    // Confirmed replacement succeeds
    expect(
      applyStarterPromptToDraft('我已经写了半篇草稿', '帮我理清优先级', pool, {
        forceReplace: true,
      }),
    ).toEqual({
      nextDraft: '帮我理清优先级',
      applied: true,
      status: 'replaced_template',
    });
  });

  it('detects renderable owned analysis strictly and rejects pending/non-owned/failed states', () => {
    const validDiary = {
      id: 12,
      authorUserId: 7,
      analysisStatus: 'complete',
      analysis: { summary: '今天节奏很稳' },
    };
    expect(hasRenderableOwnedAnalysis(validDiary, 7)).toBe(true);
    expect(hasRenderableOwnedAnalysis(validDiary, 8)).toBe(false);
    expect(hasRenderableOwnedAnalysis({ ...validDiary, analysisStatus: 'analyzing' }, 7)).toBe(false);
    expect(hasRenderableOwnedAnalysis({ ...validDiary, analysisStatus: 'skipped_user' }, 7)).toBe(false);
    expect(hasRenderableOwnedAnalysis({ ...validDiary, analysisStatus: 'skipped_quota' }, 7)).toBe(false);
    expect(hasRenderableOwnedAnalysis({ ...validDiary, analysisStatus: 'failed_limit' }, 7)).toBe(false);
    expect(hasRenderableOwnedAnalysis({ ...validDiary, analysisStatus: 'failed' }, 7)).toBe(false);
    expect(hasRenderableOwnedAnalysis({ ...validDiary, analysis: { summary: '' } }, 7)).toBe(false);
  });

  it('describes post-save next step accurately for every analysisStatus', () => {
    expect(describeWriteSaveNextStep('analyzing', true).description).toContain('后台分析');
    expect(describeWriteSaveNextStep('complete', true).title).toContain('分析已就绪');
    expect(describeWriteSaveNextStep('skipped_user', true).description).toContain('跳过');
    expect(describeWriteSaveNextStep(undefined, false).description).toContain('跳过');
    expect(describeWriteSaveNextStep('skipped_quota', true).description).toContain('次数已用完');
    expect(describeWriteSaveNextStep('failed_limit', true).description).toContain('深度思考额度');
    expect(describeWriteSaveNextStep('failed', true).description).toContain('未能完成');
  });
});
