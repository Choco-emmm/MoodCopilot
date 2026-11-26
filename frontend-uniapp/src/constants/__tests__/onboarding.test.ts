import { describe, expect, it } from 'vitest';
import {
  ONBOARDING_GUIDE_VERSION,
  WELCOME_ACTIONS,
  WELCOME_SLIDES,
  buildOnboardingCacheKey,
  computeCoreTaskProgress,
  createDefaultOnboardingState,
  isChecklistActive,
  mergeOnboardingStateMonotonic,
  shouldAutoShowWelcome,
} from '../onboarding';

describe('shared onboarding pure helpers', () => {
  it('computes core task progress out of 4 core tasks and excludes optional theme task', () => {
    const initial = createDefaultOnboardingState({
      enrolledVersion: 1,
      tasks: {
        FIRST_DIARY: 'COMPLETED',
        FIRST_ANALYSIS_VIEWED: 'SKIPPED',
        FIRST_CHAT_REPLY: 'NOT_STARTED',
        MEMORY_CENTER_VISITED: 'IN_PROGRESS',
        THEME_EXPLORED: 'COMPLETED',
      },
    });

    const progress = computeCoreTaskProgress(initial);
    expect(progress.completed).toBe(1);
    expect(progress.total).toBe(4);
    expect(progress.percentage).toBe(25);
    expect(progress.allCompleted).toBe(false);
    expect(progress.remainingKeys).toEqual([
      'FIRST_ANALYSIS_VIEWED',
      'FIRST_CHAT_REPLY',
      'MEMORY_CENTER_VISITED',
    ]);

    const completed = createDefaultOnboardingState({
      enrolledVersion: 1,
      tasks: {
        FIRST_DIARY: 'COMPLETED',
        FIRST_ANALYSIS_VIEWED: 'COMPLETED',
        FIRST_CHAT_REPLY: 'COMPLETED',
        MEMORY_CENTER_VISITED: 'COMPLETED',
        THEME_EXPLORED: 'NOT_STARTED',
      },
    });
    expect(computeCoreTaskProgress(completed)).toEqual({
      completed: 4,
      total: 4,
      percentage: 100,
      allCompleted: true,
      remainingKeys: [],
    });
  });

  it('auto-shows welcome only for enrolled users whose welcome status is NOT_STARTED', () => {
    expect(
      shouldAutoShowWelcome(
        createDefaultOnboardingState({ enrolledVersion: 0, welcomeStatus: 'NOT_STARTED' }),
      ),
    ).toBe(false);

    expect(
      shouldAutoShowWelcome(
        createDefaultOnboardingState({ enrolledVersion: 1, welcomeStatus: 'NOT_STARTED' }),
      ),
    ).toBe(true);

    expect(
      shouldAutoShowWelcome(
        createDefaultOnboardingState({ enrolledVersion: 1, welcomeStatus: 'IN_PROGRESS' }),
      ),
    ).toBe(false);

    expect(
      shouldAutoShowWelcome(
        createDefaultOnboardingState({ enrolledVersion: 1, welcomeStatus: 'SKIPPED' }),
      ),
    ).toBe(false);

    expect(
      shouldAutoShowWelcome(
        createDefaultOnboardingState({ enrolledVersion: 1, welcomeStatus: 'COMPLETED' }),
      ),
    ).toBe(false);
  });

  it('determines checklist active state accurately across enrollment, dismissal, and completion', () => {
    expect(
      isChecklistActive(
        createDefaultOnboardingState({ enrolledVersion: 0, checklistStatus: 'NOT_STARTED' }),
      ),
    ).toBe(false);

    expect(
      isChecklistActive(
        createDefaultOnboardingState({ enrolledVersion: 1, checklistStatus: 'NOT_STARTED' }),
      ),
    ).toBe(true);

    expect(
      isChecklistActive(
        createDefaultOnboardingState({ enrolledVersion: 1, checklistStatus: 'IN_PROGRESS' }),
      ),
    ).toBe(true);

    expect(
      isChecklistActive(
        createDefaultOnboardingState({ enrolledVersion: 1, checklistStatus: 'SKIPPED' }),
      ),
    ).toBe(false);

    expect(
      isChecklistActive(
        createDefaultOnboardingState({
          enrolledVersion: 1,
          checklistStatus: 'IN_PROGRESS',
          tasks: {
            FIRST_DIARY: 'COMPLETED',
            FIRST_ANALYSIS_VIEWED: 'COMPLETED',
            FIRST_CHAT_REPLY: 'COMPLETED',
            MEMORY_CENTER_VISITED: 'COMPLETED',
            THEME_EXPLORED: 'NOT_STARTED',
          },
        }),
      ),
    ).toBe(false);
  });

  it('merges onboarding states monotonically without downgrading completed tasks', () => {
    const local = createDefaultOnboardingState({
      enrolledVersion: 0,
      welcomeStatus: 'SKIPPED',
      checklistStatus: 'IN_PROGRESS',
      tasks: {
        FIRST_DIARY: 'COMPLETED',
        FIRST_ANALYSIS_VIEWED: 'IN_PROGRESS',
        FIRST_CHAT_REPLY: 'NOT_STARTED',
        MEMORY_CENTER_VISITED: 'NOT_STARTED',
        THEME_EXPLORED: 'COMPLETED',
      },
      hints: { ANALYSIS_READY: 'COMPLETED' },
    });

    const remote = createDefaultOnboardingState({
      enrolledVersion: 1,
      welcomeStatus: 'IN_PROGRESS',
      checklistStatus: 'NOT_STARTED',
      tasks: {
        FIRST_DIARY: 'NOT_STARTED',
        FIRST_ANALYSIS_VIEWED: 'COMPLETED',
        FIRST_CHAT_REPLY: 'COMPLETED',
        MEMORY_CENTER_VISITED: 'COMPLETED',
        THEME_EXPLORED: 'NOT_STARTED',
      },
      hints: { ANALYSIS_READY: 'SKIPPED', CHAT_CITATION: 'COMPLETED' },
    });

    const merged = mergeOnboardingStateMonotonic(local, remote);
    expect(merged.enrolledVersion).toBe(1);
    expect(merged.welcomeStatus).toBe('SKIPPED');
    expect(merged.tasks.FIRST_DIARY).toBe('COMPLETED');
    expect(merged.tasks.FIRST_ANALYSIS_VIEWED).toBe('COMPLETED');
    expect(merged.tasks.FIRST_CHAT_REPLY).toBe('COMPLETED');
    expect(merged.tasks.MEMORY_CENTER_VISITED).toBe('COMPLETED');
    expect(merged.tasks.THEME_EXPLORED).toBe('COMPLETED');
    expect(merged.checklistStatus).toBe('COMPLETED');
    expect(merged.hints.ANALYSIS_READY).toBe('COMPLETED');
    expect(merged.hints.CHAT_CITATION).toBe('COMPLETED');
  });

  it('scopes cache keys by userId, version, and platform scope', () => {
    expect(buildOnboardingCacheKey(42, ONBOARDING_GUIDE_VERSION, 'MINI_PROGRAM')).toBe(
      `moodcopilot:onboarding:v${ONBOARDING_GUIDE_VERSION}:MINI_PROGRAM:42`,
    );
    expect(buildOnboardingCacheKey(42, 1, 'MINI_PROGRAM')).not.toBe(
      buildOnboardingCacheKey(42, 1, 'WEB'),
    );
    expect(buildOnboardingCacheKey(42, 1, 'MINI_PROGRAM')).not.toBe(
      buildOnboardingCacheKey(99, 1, 'MINI_PROGRAM'),
    );
  });

  it('provides 3 welcome slides and 3 non-social P0-B actions', () => {
    expect(WELCOME_SLIDES).toHaveLength(3);
    expect(WELCOME_ACTIONS.map((item) => item.key)).toEqual([
      'WRITE_TODAY',
      'CHAT_WITH_AI',
      'EXPLORE_LATER',
    ]);
  });
});
