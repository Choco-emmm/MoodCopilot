import test from 'node:test'
import assert from 'node:assert/strict'
import {
  ONBOARDING_GUIDE_VERSION,
  CORE_TASK_KEYS,
  ALL_TASK_KEYS,
  ALL_HINT_KEYS,
  CORE_ONBOARDING_TASKS,
  OPTIONAL_ONBOARDING_TASKS,
  CHAT_STARTER_PROMPTS_WITH_DIARY,
  CHAT_STARTER_PROMPTS_WITHOUT_DIARY,
  WELCOME_SLIDES,
  WELCOME_ACTIONS,
  buildOnboardingCacheKey,
  computeCoreTaskProgress,
  createDefaultOnboardingState,
  isChecklistActive,
  mergeOnboardingStateMonotonic,
  normalizeOnboardingState,
  shouldAutoShowWelcome,
} from '../../shared/onboarding.ts'

test('shared/onboarding metadata matches P0-A/P0-B contract', () => {
  assert.equal(ONBOARDING_GUIDE_VERSION, 1)
  assert.deepEqual([...CORE_TASK_KEYS], [
    'FIRST_DIARY',
    'FIRST_ANALYSIS_VIEWED',
    'FIRST_CHAT_REPLY',
    'MEMORY_CENTER_VISITED',
  ])
  assert.equal(ALL_TASK_KEYS.length, 5)
  assert.equal(ALL_HINT_KEYS.length, 10)
  assert.equal(WELCOME_SLIDES.length, 3)
  assert.equal(WELCOME_SLIDES[0].title, '写下来，然后被理解')
  assert.equal(WELCOME_SLIDES[1].title, '让记录积累意义')
  assert.equal(WELCOME_SLIDES[2].title, '你的记忆，由你决定')
  assert.deepEqual(
    WELCOME_ACTIONS.map((a) => a.label),
    ['记录今天', '找 AI 聊聊', '稍后探索'],
  )
})

test('shouldAutoShowWelcome and isChecklistActive gate enrollment and status accurately', () => {
  const existingUser = createDefaultOnboardingState()
  assert.equal(existingUser.enrolledVersion, 0)
  assert.equal(shouldAutoShowWelcome(existingUser), false)
  assert.equal(isChecklistActive(existingUser), false)

  const newUser = createDefaultOnboardingState({ enrolledVersion: 1 })
  assert.equal(shouldAutoShowWelcome(newUser), true)
  assert.equal(isChecklistActive(newUser), true)

  const skippedWelcome = createDefaultOnboardingState({
    enrolledVersion: 1,
    welcomeStatus: 'SKIPPED',
    checklistStatus: 'NOT_STARTED',
  })
  assert.equal(shouldAutoShowWelcome(skippedWelcome), false)
  assert.equal(isChecklistActive(skippedWelcome), true)

  const inProgressChecklist = createDefaultOnboardingState({
    enrolledVersion: 1,
    welcomeStatus: 'COMPLETED',
    checklistStatus: 'IN_PROGRESS',
  })
  assert.equal(isChecklistActive(inProgressChecklist), true)

  const dismissedChecklist = createDefaultOnboardingState({
    enrolledVersion: 1,
    welcomeStatus: 'COMPLETED',
    checklistStatus: 'SKIPPED',
  })
  assert.equal(isChecklistActive(dismissedChecklist), false)

  const allCoreDone = createDefaultOnboardingState({
    enrolledVersion: 1,
    welcomeStatus: 'COMPLETED',
    checklistStatus: 'IN_PROGRESS',
    tasks: {
      FIRST_DIARY: 'COMPLETED',
      FIRST_ANALYSIS_VIEWED: 'COMPLETED',
      FIRST_CHAT_REPLY: 'COMPLETED',
      MEMORY_CENTER_VISITED: 'COMPLETED',
      THEME_EXPLORED: 'NOT_STARTED',
    },
  })
  assert.equal(isChecklistActive(allCoreDone), false)
  assert.deepEqual(computeCoreTaskProgress(allCoreDone), {
    completed: 4,
    total: 4,
    percentage: 100,
    allCompleted: true,
    remainingKeys: [],
  })
})

test('computeCoreTaskProgress excludes SKIPPED and optional THEME_EXPLORED from core completion', () => {
  const state = createDefaultOnboardingState({
    enrolledVersion: 1,
    tasks: {
      FIRST_DIARY: 'COMPLETED',
      FIRST_ANALYSIS_VIEWED: 'SKIPPED',
      FIRST_CHAT_REPLY: 'IN_PROGRESS',
      MEMORY_CENTER_VISITED: 'NOT_STARTED',
      THEME_EXPLORED: 'COMPLETED',
    },
  })
  const progress = computeCoreTaskProgress(state)
  assert.equal(progress.completed, 1)
  assert.equal(progress.total, 4)
  assert.equal(progress.percentage, 25)
  assert.equal(progress.allCompleted, false)
  assert.deepEqual(progress.remainingKeys, [
    'FIRST_ANALYSIS_VIEWED',
    'FIRST_CHAT_REPLY',
    'MEMORY_CENTER_VISITED',
  ])
})

test('mergeOnboardingStateMonotonic never downgrades completed tasks and trusts server enrollment', () => {
  const local = createDefaultOnboardingState({
    enrolledVersion: 1,
    welcomeStatus: 'COMPLETED',
    checklistStatus: 'IN_PROGRESS',
    tasks: {
      FIRST_DIARY: 'COMPLETED',
      FIRST_ANALYSIS_VIEWED: 'SKIPPED',
      FIRST_CHAT_REPLY: 'NOT_STARTED',
      MEMORY_CENTER_VISITED: 'NOT_STARTED',
      THEME_EXPLORED: 'NOT_STARTED',
    },
    hints: {
      ANALYSIS_READY: 'COMPLETED',
    },
  })
  const staleRemote = createDefaultOnboardingState({
    enrolledVersion: 1,
    welcomeStatus: 'IN_PROGRESS',
    checklistStatus: 'NOT_STARTED',
    tasks: {
      FIRST_DIARY: 'NOT_STARTED',
      FIRST_ANALYSIS_VIEWED: 'NOT_STARTED',
      FIRST_CHAT_REPLY: 'COMPLETED',
      MEMORY_CENTER_VISITED: 'NOT_STARTED',
      THEME_EXPLORED: 'COMPLETED',
    },
    hints: {
      CHAT_CITATION: 'SKIPPED',
    },
  })

  const merged = mergeOnboardingStateMonotonic(local, staleRemote)
  assert.equal(merged.welcomeStatus, 'COMPLETED')
  assert.equal(merged.checklistStatus, 'IN_PROGRESS')
  assert.equal(merged.tasks.FIRST_DIARY, 'COMPLETED')
  assert.equal(merged.tasks.FIRST_ANALYSIS_VIEWED, 'SKIPPED')
  assert.equal(merged.tasks.FIRST_CHAT_REPLY, 'COMPLETED')
  assert.equal(merged.tasks.THEME_EXPLORED, 'COMPLETED')
  assert.equal(merged.hints.ANALYSIS_READY, 'COMPLETED')
  assert.equal(merged.hints.CHAT_CITATION, 'SKIPPED')
})

test('normalizeOnboardingState sanitizes malformed payloads and buildOnboardingCacheKey is scoped', () => {
  const normalized = normalizeOnboardingState({
    enrolledVersion: -5,
    guideVersion: 1,
    welcomeStatus: 'INVALID',
    tasks: { FIRST_DIARY: 'COMPLETED', UNKNOWN: 'COMPLETED' },
    hints: { ANALYSIS_READY: 'NOT_STARTED', MODEL_PRO: 'COMPLETED' },
  })
  assert.equal(normalized.enrolledVersion, 0)
  assert.equal(normalized.welcomeStatus, 'NOT_STARTED')
  assert.equal(normalized.tasks.FIRST_DIARY, 'COMPLETED')
  assert.equal(normalized.hints.ANALYSIS_READY, undefined)
  assert.equal(normalized.hints.MODEL_PRO, 'COMPLETED')

  assert.equal(buildOnboardingCacheKey(42, 1, 'WEB'), 'moodcopilot:onboarding:v1:WEB:42')
  assert.notEqual(
    buildOnboardingCacheKey(42, 1, 'WEB'),
    buildOnboardingCacheKey(42, 1, 'MINI_PROGRAM'),
  )
  assert.notEqual(
    buildOnboardingCacheKey(42, 1, 'WEB'),
    buildOnboardingCacheKey(43, 1, 'WEB'),
  )
})

test('P0-C task metadata separates 4 core tasks from optional theme task and provides with/without-diary chat starters', () => {
  assert.equal(CORE_ONBOARDING_TASKS.length, 4)
  assert.equal(OPTIONAL_ONBOARDING_TASKS.length, 1)
  assert.equal(OPTIONAL_ONBOARDING_TASKS[0].key, 'THEME_EXPLORED')

  assert.ok(CHAT_STARTER_PROMPTS_WITH_DIARY.length >= 2)
  assert.ok(CHAT_STARTER_PROMPTS_WITH_DIARY.some((p) => p.requiresDiary))
  assert.ok(CHAT_STARTER_PROMPTS_WITHOUT_DIARY.length >= 2)
  assert.ok(CHAT_STARTER_PROMPTS_WITHOUT_DIARY.every((p) => !p.requiresDiary))

  const onlyOptionalDone = createDefaultOnboardingState({
    enrolledVersion: 1,
    tasks: {
      FIRST_DIARY: 'NOT_STARTED',
      FIRST_ANALYSIS_VIEWED: 'NOT_STARTED',
      FIRST_CHAT_REPLY: 'NOT_STARTED',
      MEMORY_CENTER_VISITED: 'NOT_STARTED',
      THEME_EXPLORED: 'COMPLETED',
    },
  })
  assert.equal(computeCoreTaskProgress(onlyOptionalDone).completed, 0)
  assert.equal(computeCoreTaskProgress(onlyOptionalDone).total, 4)
  assert.equal(isChecklistActive(onlyOptionalDone), true)
})
