import { resolveStarterPromptDraft } from '../constants/onboarding';
import type {
  StarterPromptApplyStatus,
  TaskKey,
  WelcomeActionKey,
} from '../constants/onboarding';

export const MINI_PROGRAM_TAB_BAR_PAGES = [
  '/pages/index/index',
  '/pages/analysis/analysis',
  '/pages/chat/chat',
  '/pages/profile/profile',
] as const;

export const SAFE_AUTO_WELCOME_ROUTES: readonly string[] = MINI_PROGRAM_TAB_BAR_PAGES;

const NON_COMPLETE_ANALYSIS_STATUSES = new Set([
  'analyzing',
  'skipped_user',
  'skipped_quota',
  'failed',
  'failed_limit',
]);

export function normalizeMiniProgramRoute(url: string): string {
  const trimmed = (url || '').trim();
  if (!trimmed) return '';
  const withoutHash = trimmed.split('#')[0] || '';
  const basePath = withoutHash.split('?')[0] || '';
  if (!basePath) return '';
  return basePath.startsWith('/') ? basePath : `/${basePath}`;
}

export function normalizeMiniProgramUrl(url: string): string {
  const trimmed = (url || '').trim();
  if (!trimmed) return '';
  return trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
}

export function isMiniProgramTabBarPage(url: string): boolean {
  const normalizedRoute = normalizeMiniProgramRoute(url);
  return (MINI_PROGRAM_TAB_BAR_PAGES as readonly string[]).includes(normalizedRoute);
}

export function isRouteSafeForAutoWelcome(route: string): boolean {
  const normalizedRoute = normalizeMiniProgramRoute(route);
  if (!normalizedRoute) return true;
  return SAFE_AUTO_WELCOME_ROUTES.includes(normalizedRoute);
}

export function navigateMiniProgramRoute(url: string): void {
  const normalizedUrl = normalizeMiniProgramUrl(url);
  if (!normalizedUrl) return;

  if (isMiniProgramTabBarPage(normalizedUrl)) {
    const tabPath = normalizeMiniProgramRoute(normalizedUrl);
    uni.switchTab({ url: tabPath });
    return;
  }

  uni.navigateTo({ url: normalizedUrl });
}

export function openOnboardingDemoPage(): void {
  navigateMiniProgramRoute('/pages/onboarding-demo/onboarding-demo');
}

export function exitOnboardingDemoPage(): void {
  if (typeof uni.navigateBack === 'function') {
    uni.navigateBack({
      fail: () => {
        uni.switchTab({ url: '/pages/index/index' });
      },
    });
    return;
  }
  uni.switchTab({ url: '/pages/index/index' });
}

export function startRealWritingFromDemo(): void {
  const writeUrl = '/pages/write/write';
  if (typeof uni.redirectTo === 'function') {
    uni.redirectTo({
      url: writeUrl,
      fail: () => {
        uni.navigateTo({ url: writeUrl });
      },
    });
    return;
  }
  uni.navigateTo({ url: writeUrl });
}

export function executeWelcomeAction(actionKey: WelcomeActionKey): void {
  if (actionKey === 'WRITE_TODAY') {
    navigateMiniProgramRoute('/pages/write/write');
    return;
  }
  if (actionKey === 'CHAT_WITH_AI') {
    navigateMiniProgramRoute('/pages/chat/chat');
    return;
  }
  if (actionKey === 'TRY_DEMO') {
    openOnboardingDemoPage();
    return;
  }
  // EXPLORE_LATER intentionally stays on the current page.
}

export function executeChecklistTaskAction(
  taskKey: TaskKey,
  options?: { latestDiaryId?: number | null },
): void {
  switch (taskKey) {
    case 'FIRST_DIARY':
      navigateMiniProgramRoute('/pages/write/write');
      return;
    case 'FIRST_ANALYSIS_VIEWED': {
      const diaryId = Number(options?.latestDiaryId);
      if (Number.isFinite(diaryId) && diaryId > 0) {
        navigateMiniProgramRoute(`/pages/detail/detail?id=${Math.floor(diaryId)}`);
      } else {
        navigateMiniProgramRoute('/pages/write/write');
      }
      return;
    }
    case 'FIRST_CHAT_REPLY':
      navigateMiniProgramRoute('/pages/chat/chat');
      return;
    case 'MEMORY_CENTER_VISITED':
      navigateMiniProgramRoute('/pages/analysis/analysis');
      return;
    case 'THEME_EXPLORED':
      navigateMiniProgramRoute('/pages/profile/profile');
      return;
  }
}

export function applyStarterPromptToDraft(
  currentDraft: string,
  promptText: string,
  templatePool: readonly string[] = [],
  options?: {
    forceReplace?: boolean;
    lastAppliedPrompt?: string | null;
  },
): { nextDraft: string; applied: boolean; status: StarterPromptApplyStatus } {
  const result = resolveStarterPromptDraft(currentDraft, promptText, templatePool, options);
  return {
    nextDraft: result.nextDraft,
    applied: result.status === 'applied' || result.status === 'replaced_template',
    status: result.status,
  };
}

export function hasRenderableOwnedAnalysis(
  diary: {
    id?: number | string | null;
    authorUserId?: number | string | null;
    analysisStatus?: string | null;
    analysis?: { summary?: string | null; feedback?: string | null } | null;
  } | null | undefined,
  currentUserId: number | string | null | undefined,
): boolean {
  if (!diary) return false;
  const diaryId = Number(diary.id);
  const ownerId = Number(diary.authorUserId);
  const viewerId = Number(currentUserId);
  if (!Number.isFinite(diaryId) || diaryId <= 0) return false;
  if (!Number.isFinite(ownerId) || !Number.isFinite(viewerId) || ownerId !== viewerId) {
    return false;
  }
  const status = typeof diary.analysisStatus === 'string' ? diary.analysisStatus.trim() : '';
  if (status && NON_COMPLETE_ANALYSIS_STATUSES.has(status)) {
    return false;
  }
  const summary = typeof diary.analysis?.summary === 'string' ? diary.analysis.summary.trim() : '';
  const feedback = typeof diary.analysis?.feedback === 'string' ? diary.analysis.feedback.trim() : '';
  return Boolean(summary || feedback);
}

export interface WriteSaveNextStep {
  title: string;
  description: string;
}

export function describeWriteSaveNextStep(
  analysisStatus: string | null | undefined,
  analyzeEnabled: boolean,
): WriteSaveNextStep {
  const normalized = typeof analysisStatus === 'string' ? analysisStatus.trim() : '';
  if (!analyzeEnabled || normalized === 'skipped_user') {
    return {
      title: '日记已保存',
      description: '已按你的选择跳过本次 AI 分析，你随时可以查看、编辑或和 AI 聊聊。',
    };
  }
  if (normalized === 'analyzing') {
    return {
      title: '日记已保存',
      description: 'AI 正在后台分析这篇记录，完成后会通过系统消息提醒你。',
    };
  }
  if (normalized === 'complete' || normalized === 'completed') {
    return {
      title: '日记已保存，分析已就绪',
      description: '现在就可以打开这篇日记查看情绪线索与回应。',
    };
  }
  if (normalized === 'skipped_quota') {
    return {
      title: '日记已保存',
      description: '今日 AI 分析次数已用完，不影响查看、编辑日记或继续聊天。',
    };
  }
  if (normalized === 'failed_limit') {
    return {
      title: '日记已保存',
      description: '深度思考额度已用完，可在日记详情改用普通分析或稍后重试。',
    };
  }
  if (normalized === 'failed') {
    return {
      title: '日记已保存',
      description: '本次 AI 分析未能完成，你可以在日记详情页稍后重试。',
    };
  }
  return {
    title: '日记已保存',
    description: '记录已安全存好，你可以查看详情、找 AI 聊聊，或返回日记本。',
  };
}
