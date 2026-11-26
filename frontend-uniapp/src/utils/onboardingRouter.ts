import type { WelcomeActionKey } from '../constants/onboarding';

export const MINI_PROGRAM_TAB_BAR_PAGES = [
  '/pages/index/index',
  '/pages/analysis/analysis',
  '/pages/chat/chat',
  '/pages/profile/profile',
] as const;

export const SAFE_AUTO_WELCOME_ROUTES: readonly string[] = MINI_PROGRAM_TAB_BAR_PAGES;

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

export function executeWelcomeAction(actionKey: WelcomeActionKey): void {
  if (actionKey === 'WRITE_TODAY') {
    navigateMiniProgramRoute('/pages/write/write');
    return;
  }
  if (actionKey === 'CHAT_WITH_AI') {
    navigateMiniProgramRoute('/pages/chat/chat');
    return;
  }
  // EXPLORE_LATER intentionally stays on the current page.
}
