import { describe, expect, it } from 'vitest';
import {
  MINI_PROGRAM_WELCOME_TOUR_STEPS,
  ONBOARDING_GUIDE_VERSION,
  ONBOARDING_HINTS,
  WEB_WELCOME_TOUR_STEPS,
  WELCOME_ACTIONS,
  WELCOME_SLIDES,
  buildOnboardingCacheKey,
  computeCoreTaskProgress,
  createDefaultOnboardingState,
  getHintsForScope,
  isChecklistActive,
  isHintAllowedForScope,
  isHintPending,
  mergeOnboardingStateMonotonic,
  pickActiveHint,
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

  it('provides 4 real-UI spotlight tour steps per platform with plain non-social mini-program wording', () => {
    expect(WEB_WELCOME_TOUR_STEPS).toHaveLength(4);
    expect(MINI_PROGRAM_WELCOME_TOUR_STEPS).toHaveLength(4);
    expect(MINI_PROGRAM_WELCOME_TOUR_STEPS.map((s) => s.anchorId)).toEqual([
      'mp-nav-write',
      'mp-nav-analysis',
      'mp-nav-chat',
      'mp-nav-profile',
    ]);
    expect(WELCOME_ACTIONS.map((item) => item.key)).toEqual([
      'WRITE_TODAY',
      'CHAT_WITH_AI',
      'EXPLORE_LATER',
    ]);
    for (const step of MINI_PROGRAM_WELCOME_TOUR_STEPS) {
      expect(step.title).not.toContain('上下文');
      expect(step.description).not.toContain('上下文');
      expect(step.description).not.toMatch(/广场|社区|关注|举报/);
    }
    for (const hint of ONBOARDING_HINTS) {
      expect(hint.title).not.toContain('上下文');
      expect(hint.description).not.toContain('上下文');
    }
  });

  it('filters P1 hint registry by scope so Web-only hints never appear on Mini-Program', () => {
    expect(ONBOARDING_HINTS).toHaveLength(10);

    const miniProgramHints = getHintsForScope('MINI_PROGRAM');
    expect(miniProgramHints).toHaveLength(8);
    expect(miniProgramHints.map((h) => h.key)).not.toContain('PUBLIC_DIARY_WEB');
    expect(miniProgramHints.map((h) => h.key)).not.toContain('TOOL_APPROVAL_WEB');
    expect(isHintAllowedForScope('PUBLIC_DIARY_WEB', 'MINI_PROGRAM')).toBe(false);
    expect(isHintAllowedForScope('TOOL_APPROVAL_WEB', 'MINI_PROGRAM')).toBe(false);
    expect(isHintAllowedForScope('ANALYSIS_READY', 'MINI_PROGRAM')).toBe(true);
    expect(isHintAllowedForScope('PUBLIC_DIARY_WEB', 'WEB')).toBe(true);
  });

  it('picks at most one highest-priority pending hint for the given scope', () => {
    const state = createDefaultOnboardingState({
      enrolledVersion: 1,
      hints: {
        MODEL_PRO: 'COMPLETED',
        MEMORY_CANDIDATE: 'SKIPPED',
      },
    });

    expect(isHintPending(state, 'MODEL_PRO')).toBe(false);
    expect(isHintPending(state, 'MEMORY_CANDIDATE')).toBe(false);
    expect(isHintPending(state, 'CHAT_CITATION')).toBe(true);

    // Web-only hints are ignored on MINI_PROGRAM, completed MODEL_PRO is skipped -> CHAT_CITATION wins
    expect(
      pickActiveHint(
        state,
        ['TOOL_APPROVAL_WEB', 'MODEL_PRO', 'RELATION_GRAPH', 'CHAT_CITATION'],
        'MINI_PROGRAM',
      )?.key,
    ).toBe('CHAT_CITATION');

    // Once MEMORY_CANDIDATE is skipped, RELATION_GRAPH becomes active
    expect(
      pickActiveHint(state, ['MEMORY_CANDIDATE', 'RELATION_GRAPH'], 'MINI_PROGRAM')?.key,
    ).toBe('RELATION_GRAPH');

    // Returns null when no eligible pending hint matches scope
    expect(
      pickActiveHint(state, ['PUBLIC_DIARY_WEB', 'MODEL_PRO'], 'MINI_PROGRAM'),
    ).toBeNull();
  });
});
