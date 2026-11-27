import test from 'node:test'
import assert from 'node:assert/strict'
import {
  ONBOARDING_GUIDE_VERSION,
  CORE_TASK_KEYS,
  ALL_TASK_KEYS,
  ALL_HINT_KEYS,
  ONBOARDING_HINTS,
  ONBOARDING_HINT_MAP,
  CORE_ONBOARDING_TASKS,
  OPTIONAL_ONBOARDING_TASKS,
  CHAT_STARTER_PROMPTS_WITH_DIARY,
  CHAT_STARTER_PROMPTS_WITHOUT_DIARY,
  WELCOME_SLIDES,
  WELCOME_ACTIONS,
  buildOnboardingCacheKey,
  computeCoreTaskProgress,
  createDefaultOnboardingState,
  getHintsForScope,
  isChecklistActive,
  isHintAllowedForScope,
  isHintPending,
  mergeOnboardingStateMonotonic,
  normalizeOnboardingState,
  pickActiveHint,
  shouldAutoShowWelcome,
} from '../../shared/onboarding.ts'

import {
  ONBOARDING_DEMO_STEPS,
  ONBOARDING_DEMO_STORY,
  createInitialDemoSessionState,
} from '../../shared/onboardingDemo.ts'

test('shared/onboarding metadata matches P0-A/P0-B/P2 contract', () => {
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
    ['记录今天', '找 AI 聊聊', '先体验一下', '稍后探索'],
  )
  assert.ok(WELCOME_ACTIONS.some((a) => a.key === 'TRY_DEMO' && a.label === '先体验一下'))
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

test('P1 hint registry covers all 10 hints with strict WEB vs MINI_PROGRAM scope filtering and priority picking', () => {
  assert.equal(ONBOARDING_HINTS.length, 10)
  for (const key of ALL_HINT_KEYS) {
    assert.ok(ONBOARDING_HINT_MAP[key], `missing hint metadata for ${key}`)
    assert.ok(ONBOARDING_HINT_MAP[key].anchorId.length > 0)
  }

  const webHints = getHintsForScope('WEB')
  const mpHints = getHintsForScope('MINI_PROGRAM')
  assert.equal(webHints.length, 10)
  assert.equal(mpHints.length, 8)
  assert.equal(isHintAllowedForScope('PUBLIC_DIARY_WEB', 'WEB'), true)
  assert.equal(isHintAllowedForScope('TOOL_APPROVAL_WEB', 'WEB'), true)
  assert.equal(isHintAllowedForScope('PUBLIC_DIARY_WEB', 'MINI_PROGRAM'), false)
  assert.equal(isHintAllowedForScope('TOOL_APPROVAL_WEB', 'MINI_PROGRAM'), false)

  const state = createDefaultOnboardingState({
    enrolledVersion: 1,
    hints: {
      ANALYSIS_READY: 'COMPLETED',
      LIFE_EVENT: 'SKIPPED',
    },
  })
  assert.equal(isHintPending(state, 'ANALYSIS_READY'), false)
  assert.equal(isHintPending(state, 'LIFE_EVENT'), false)
  assert.equal(isHintPending(state, 'CHAT_CITATION'), true)

  // Picks highest priority eligible hint for WEB
  const pickedWeb = pickActiveHint(state, ['CHAT_CITATION', 'MODEL_PRO', 'TOOL_APPROVAL_WEB'], 'WEB')
  assert.equal(pickedWeb?.key, 'TOOL_APPROVAL_WEB')

  // Filters out WEB-only hints on MINI_PROGRAM and picks next highest priority
  const pickedMp = pickActiveHint(state, ['CHAT_CITATION', 'MODEL_PRO', 'TOOL_APPROVAL_WEB'], 'MINI_PROGRAM')
  assert.equal(pickedMp?.key, 'MODEL_PRO')

  // Returns null when all eligible hints are already seen/dismissed
  assert.equal(pickActiveHint(state, ['ANALYSIS_READY', 'LIFE_EVENT'], 'WEB'), null)
})

test('P2 photography contest demo story has verbatim evidence, concise Chinese attributeKeys, and explicit preset disclaimer', () => {
  assert.equal(ONBOARDING_DEMO_STORY.storyTitle, '摄影比赛')
  assert.equal(ONBOARDING_DEMO_STEPS.length, 4)
  assert.ok(ONBOARDING_DEMO_STORY.chatDisclaimer.includes('非实时 AI'))
  assert.ok(ONBOARDING_DEMO_STORY.memories.length >= 3)
  assert.ok(ONBOARDING_DEMO_STORY.memories.some((m) => m.kind === 'candidate'))

  for (const memory of ONBOARDING_DEMO_STORY.memories) {
    assert.ok(
      ONBOARDING_DEMO_STORY.diary.content.includes(memory.evidence),
      `demo memory ${memory.id} evidence is not a verbatim substring of diary.content`,
    )
    assert.ok(
      /^[\u4e00-\u9fa5]{2,12}$/.test(memory.attributeKey),
      `demo memory ${memory.id} attributeKey "${memory.attributeKey}" must be concise Chinese`,
    )
  }

  assert.ok(ONBOARDING_DEMO_STORY.chatPresets.length >= 2)
  for (const preset of ONBOARDING_DEMO_STORY.chatPresets) {
    assert.ok(preset.citations.length >= 1)
    for (const cite of preset.citations) {
      assert.ok(
        ONBOARDING_DEMO_STORY.diary.content.includes(cite.excerpt),
        `demo citation ${cite.id} excerpt must come from demo diary`,
      )
    }
  }

  const initial = createInitialDemoSessionState()
  assert.equal(initial.stepIndex, 0)
  assert.equal(initial.candidateDecision, 'pending')
  assert.deepEqual(initial.selectedChatPresetIds, [])
})
