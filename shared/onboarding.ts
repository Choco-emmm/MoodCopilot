export const ONBOARDING_GUIDE_VERSION = 1

export const GUIDE_SCOPES = ['GLOBAL', 'WEB', 'MINI_PROGRAM'] as const
export type GuideScope = (typeof GUIDE_SCOPES)[number]
export type PlatformGuideScope = 'WEB' | 'MINI_PROGRAM'

export const GUIDE_STATUSES = ['NOT_STARTED', 'IN_PROGRESS', 'SKIPPED', 'COMPLETED'] as const
export type GuideStatus = (typeof GUIDE_STATUSES)[number]

export const GUIDE_STATUS_RANK: Record<GuideStatus, number> = {
  NOT_STARTED: 0,
  IN_PROGRESS: 1,
  SKIPPED: 2,
  COMPLETED: 3,
}

export const CORE_TASK_KEYS = [
  'FIRST_DIARY',
  'FIRST_ANALYSIS_VIEWED',
  'FIRST_CHAT_REPLY',
  'MEMORY_CENTER_VISITED',
] as const
export type CoreTaskKey = (typeof CORE_TASK_KEYS)[number]

export const OPTIONAL_TASK_KEYS = ['THEME_EXPLORED'] as const
export type OptionalTaskKey = (typeof OPTIONAL_TASK_KEYS)[number]

export const ALL_TASK_KEYS = [
  'FIRST_DIARY',
  'FIRST_ANALYSIS_VIEWED',
  'FIRST_CHAT_REPLY',
  'MEMORY_CENTER_VISITED',
  'THEME_EXPLORED',
] as const
export type TaskKey = (typeof ALL_TASK_KEYS)[number]

export const ALL_HINT_KEYS = [
  'ANALYSIS_READY',
  'CHAT_CITATION',
  'MEMORY_CANDIDATE',
  'RELATION_GRAPH',
  'REPORT_AVAILABLE',
  'LIFE_EVENT',
  'LIFE_CHAPTER',
  'MODEL_PRO',
  'PUBLIC_DIARY_WEB',
  'TOOL_APPROVAL_WEB',
] as const
export type HintKey = (typeof ALL_HINT_KEYS)[number]

export const ALL_GUIDE_KEYS = [
  'WELCOME_V1',
  'STARTER_CHECKLIST_V1',
  'FIRST_DIARY',
  'FIRST_ANALYSIS_VIEWED',
  'FIRST_CHAT_REPLY',
  'MEMORY_CENTER_VISITED',
  'THEME_EXPLORED',
  'ANALYSIS_READY',
  'CHAT_CITATION',
  'MEMORY_CANDIDATE',
  'RELATION_GRAPH',
  'REPORT_AVAILABLE',
  'LIFE_EVENT',
  'LIFE_CHAPTER',
  'MODEL_PRO',
  'PUBLIC_DIARY_WEB',
  'TOOL_APPROVAL_WEB',
] as const
export type GuideKey = (typeof ALL_GUIDE_KEYS)[number]

export const ONBOARDING_EVENT_TYPES = [
  'WELCOME_STARTED',
  'WELCOME_COMPLETED',
  'ANALYSIS_VIEWED',
  'MEMORY_CENTER_VISITED',
  'THEME_EXPLORED',
  'HINT_SEEN',
] as const
export type OnboardingEventType = (typeof ONBOARDING_EVENT_TYPES)[number]

export const ONBOARDING_DISMISS_TARGETS = [
  'WELCOME',
  'CHECKLIST',
  'TASK',
  'HINT',
] as const
export type OnboardingDismissTarget = (typeof ONBOARDING_DISMISS_TARGETS)[number]

export interface OnboardingState {
  enrolledVersion: number
  guideVersion: number
  welcomeStatus: GuideStatus
  checklistStatus: GuideStatus
  tasks: Record<TaskKey, GuideStatus>
  hints: Partial<Record<HintKey, GuideStatus>>
}

export interface OnboardingEventRequest {
  eventType: OnboardingEventType
  diaryId?: number
  hintKey?: HintKey
  scope?: GuideScope
  guideVersion?: number
}

export interface OnboardingDismissRequest {
  target: OnboardingDismissTarget
  key?: TaskKey | HintKey
  scope?: GuideScope
  guideVersion?: number
}

export interface WelcomeSlide {
  id: 'record' | 'meaning' | 'control'
  stepNumber: string
  eyebrow: string
  title: string
  description: string
  highlights: readonly string[]
}

export type WelcomeActionKey = 'WRITE_TODAY' | 'CHAT_WITH_AI' | 'EXPLORE_LATER'

export interface WelcomeActionItem {
  key: WelcomeActionKey
  label: string
  description: string
  emphasis: 'primary' | 'secondary' | 'ghost'
}

export interface OnboardingTaskMeta {
  key: TaskKey
  title: string
  description: string
  actionLabel: string
  core: boolean
  skippable: boolean
}

export interface ChatStarterPrompt {
  id: string
  label: string
  text: string
  requiresDiary: boolean
}

export interface CoreTaskProgress {
  completed: number
  total: number
  percentage: number
  allCompleted: boolean
  remainingKeys: CoreTaskKey[]
}

export const WELCOME_SLIDES: readonly WelcomeSlide[] = [
  {
    id: 'record',
    stepNumber: '01',
    eyebrow: '记录当下',
    title: '写下来，然后被理解',
    description: '文字、照片、音乐都可以成为记录。',
    highlights: [
      '不必写成长篇，几句话就能留住此刻的心情与片段',
      '可搭配照片与正在听的音乐，让回忆更有画面感',
      '支持随时编辑与整理，按自己的节奏慢慢记录',
    ],
  },
  {
    id: 'meaning',
    stepNumber: '02',
    eyebrow: '沉淀脉络',
    title: '让记录积累意义',
    description: 'AI 可以分析日记、整理记忆，并在对话中引用你的记录。',
    highlights: [
      '自动梳理日记中的情绪线索与回应，帮助你看见变化',
      '从日常片段中提炼可追溯的长期记忆与关系脉络',
      '对话时既能结合你的记录上下文，也能处理学习、写作与工作问题',
    ],
  },
  {
    id: 'control',
    stepNumber: '03',
    eyebrow: '边界清晰',
    title: '你的记忆，由你决定',
    description: '日记默认私密；可查证据、修改、删除，待确认的记忆变更由你决定。',
    highlights: [
      '新日记默认仅自己可见，公开或私密始终由你掌控',
      '每条记忆都保留原文依据，可随时核对、编辑或删除',
      '涉及改写或失效的记忆变更会先交给你确认，不会擅自覆盖',
    ],
  },
]

export const WELCOME_ACTIONS: readonly WelcomeActionItem[] = [
  {
    key: 'WRITE_TODAY',
    label: '记录今天',
    description: '写下今天的一段文字、照片或音乐',
    emphasis: 'primary',
  },
  {
    key: 'CHAT_WITH_AI',
    label: '找 AI 聊聊',
    description: '开启一段对话，聊日常、学习或任何想法',
    emphasis: 'secondary',
  },
  {
    key: 'EXPLORE_LATER',
    label: '稍后探索',
    description: '先自由逛逛，之后随时可在个人页重看指南',
    emphasis: 'ghost',
  },
]

export const ONBOARDING_TASKS: readonly OnboardingTaskMeta[] = [
  {
    key: 'FIRST_DIARY',
    title: '写下第一篇日记',
    description: '文字、照片或音乐都可以，默认仅自己可见。',
    actionLabel: '去记录',
    core: true,
    skippable: true,
  },
  {
    key: 'FIRST_ANALYSIS_VIEWED',
    title: '查看一次 AI 分析',
    description: '打开日记详情，查看情绪线索与温柔回应。',
    actionLabel: '去看看',
    core: true,
    skippable: true,
  },
  {
    key: 'FIRST_CHAT_REPLY',
    title: '和 AI 完成一次对话',
    description: '聊近况、解惑，或探讨学习与工作问题。',
    actionLabel: '去聊聊',
    core: true,
    skippable: true,
  },
  {
    key: 'MEMORY_CENTER_VISITED',
    title: '认识你的记忆中心',
    description: '了解记忆如何附带依据，并随时由你编辑或删除。',
    actionLabel: '去了解',
    core: true,
    skippable: true,
  },
  {
    key: 'THEME_EXPLORED',
    title: '挑一套喜欢的纸张主题',
    description: '可选探索：切换日间或夜间主题配色。',
    actionLabel: '换主题',
    core: false,
    skippable: true,
  },
]

export const CORE_ONBOARDING_TASKS: readonly OnboardingTaskMeta[] = ONBOARDING_TASKS.filter(
  (task) => task.core,
)

export const OPTIONAL_ONBOARDING_TASKS: readonly OnboardingTaskMeta[] = ONBOARDING_TASKS.filter(
  (task) => !task.core,
)

export const CHAT_STARTER_PROMPTS_WITH_DIARY: readonly ChatStarterPrompt[] = [
  {
    id: 'diary-reflect',
    label: '聊聊最近这篇日记',
    text: '结合我最近写下的日记，帮我梳理一下这阵子让我反复在意的事情。',
    requiresDiary: true,
  },
  {
    id: 'diary-pattern',
    label: '看看情绪与节奏变化',
    text: '从我最近的记录里，你觉得有哪些值得留意的节奏变化或小亮点？',
    requiresDiary: true,
  },
  {
    id: 'general-plan-with-diary',
    label: '规划接下来的待办',
    text: '帮我把接下来一周最重要的三件事拆成可执行的步骤。',
    requiresDiary: false,
  },
]

export const CHAT_STARTER_PROMPTS_WITHOUT_DIARY: readonly ChatStarterPrompt[] = [
  {
    id: 'general-start',
    label: '帮我理清手头的事',
    text: '我最近有几件事混在一起，可以一步步帮我理清优先级吗？',
    requiresDiary: false,
  },
  {
    id: 'general-study',
    label: '一起学习或拆解问题',
    text: '我想系统学习一个新主题，请先问我几个问题帮我制定学习路径。',
    requiresDiary: false,
  },
  {
    id: 'general-writing',
    label: '润色文字或整理思路',
    text: '我有一段想法想写清楚，你可以帮我提炼结构并给出一版简洁表达吗？',
    requiresDiary: false,
  },
]

export const ONBOARDING_COPY = {
  welcomeBadge: '初次相遇',
  welcomeReplayBadge: '使用指南',
  welcomeReplayNote: '当前为重播模式，不会重置你已完成的进度。',
  welcomeSkipLabel: '跳过',
  welcomePrevLabel: '上一页',
  welcomeNextLabel: '下一页',
  welcomeToActionsLabel: '下一步',
  welcomeReplayCloseLabel: '关闭指南',
  welcomeActionsHeading: '接下来，想从哪里开始？',
  welcomeActionsSubheading: '一切都可以按你的节奏慢慢来，随时可以从个人页重看这份指南。',
  guideEntryLabel: '使用指南',
  guideEntryDescription: '重新查看初次相遇介绍',
  checklistTitle: '新手上手清单',
  checklistOptionalHeading: '可选探索',
  checklistDismissLabel: '收起清单',
  memoryCenterSummary: '这里汇集系统整理与由你确认的长期记忆，每条都可查看原文依据、随时修改或删除。',
} as const

function hasOwn(obj: object, key: string): boolean {
  return Object.prototype.hasOwnProperty.call(obj, key)
}

export function isGuideStatus(value: unknown): value is GuideStatus {
  return (
    value === 'NOT_STARTED' ||
    value === 'IN_PROGRESS' ||
    value === 'SKIPPED' ||
    value === 'COMPLETED'
  )
}

export function isTaskKey(value: unknown): value is TaskKey {
  for (let i = 0; i < ALL_TASK_KEYS.length; i += 1) {
    if (ALL_TASK_KEYS[i] === value) return true
  }
  return false
}

export function isHintKey(value: unknown): value is HintKey {
  for (let i = 0; i < ALL_HINT_KEYS.length; i += 1) {
    if (ALL_HINT_KEYS[i] === value) return true
  }
  return false
}

export function maxGuideStatus(a?: GuideStatus | null, b?: GuideStatus | null): GuideStatus {
  const left: GuideStatus = a && isGuideStatus(a) ? a : 'NOT_STARTED'
  const right: GuideStatus = b && isGuideStatus(b) ? b : 'NOT_STARTED'
  return GUIDE_STATUS_RANK[left] >= GUIDE_STATUS_RANK[right] ? left : right
}

export function createDefaultTaskMap(): Record<TaskKey, GuideStatus> {
  return {
    FIRST_DIARY: 'NOT_STARTED',
    FIRST_ANALYSIS_VIEWED: 'NOT_STARTED',
    FIRST_CHAT_REPLY: 'NOT_STARTED',
    MEMORY_CENTER_VISITED: 'NOT_STARTED',
    THEME_EXPLORED: 'NOT_STARTED',
  }
}

export function createDefaultOnboardingState(
  overrides?: Partial<OnboardingState>,
): OnboardingState {
  const baseTasks = createDefaultTaskMap()
  if (overrides && overrides.tasks) {
    for (let i = 0; i < ALL_TASK_KEYS.length; i += 1) {
      const key = ALL_TASK_KEYS[i]
      const status = overrides.tasks[key]
      if (isGuideStatus(status)) {
        baseTasks[key] = status
      }
    }
  }

  const baseHints: Partial<Record<HintKey, GuideStatus>> = {}
  if (overrides && overrides.hints) {
    for (let i = 0; i < ALL_HINT_KEYS.length; i += 1) {
      const key = ALL_HINT_KEYS[i]
      const status = overrides.hints[key]
      if (isGuideStatus(status) && status !== 'NOT_STARTED') {
        baseHints[key] = status
      }
    }
  }

  return {
    enrolledVersion:
      overrides && typeof overrides.enrolledVersion === 'number' && Number.isFinite(overrides.enrolledVersion)
        ? Math.max(0, Math.floor(overrides.enrolledVersion))
        : 0,
    guideVersion:
      overrides && typeof overrides.guideVersion === 'number' && Number.isFinite(overrides.guideVersion) && overrides.guideVersion > 0
        ? Math.floor(overrides.guideVersion)
        : ONBOARDING_GUIDE_VERSION,
    welcomeStatus:
      overrides && isGuideStatus(overrides.welcomeStatus)
        ? overrides.welcomeStatus
        : 'NOT_STARTED',
    checklistStatus:
      overrides && isGuideStatus(overrides.checklistStatus)
        ? overrides.checklistStatus
        : 'NOT_STARTED',
    tasks: baseTasks,
    hints: baseHints,
  }
}

export function normalizeOnboardingState(raw: unknown): OnboardingState {
  if (!raw || typeof raw !== 'object') {
    return createDefaultOnboardingState()
  }
  const source = raw as Record<string, unknown>
  const tasks = createDefaultTaskMap()
  const rawTasks = source.tasks
  if (rawTasks && typeof rawTasks === 'object') {
    const taskRecord = rawTasks as Record<string, unknown>
    for (let i = 0; i < ALL_TASK_KEYS.length; i += 1) {
      const key = ALL_TASK_KEYS[i]
      if (hasOwn(taskRecord, key) && isGuideStatus(taskRecord[key])) {
        tasks[key] = taskRecord[key] as GuideStatus
      }
    }
  }

  const hints: Partial<Record<HintKey, GuideStatus>> = {}
  const rawHints = source.hints
  if (rawHints && typeof rawHints === 'object') {
    const hintRecord = rawHints as Record<string, unknown>
    for (let i = 0; i < ALL_HINT_KEYS.length; i += 1) {
      const key = ALL_HINT_KEYS[i]
      if (hasOwn(hintRecord, key) && isGuideStatus(hintRecord[key])) {
        const status = hintRecord[key] as GuideStatus
        if (status !== 'NOT_STARTED') {
          hints[key] = status
        }
      }
    }
  }

  const enrolledVersion =
    typeof source.enrolledVersion === 'number' && Number.isFinite(source.enrolledVersion)
      ? Math.max(0, Math.floor(source.enrolledVersion))
      : 0
  const guideVersion =
    typeof source.guideVersion === 'number' && Number.isFinite(source.guideVersion) && source.guideVersion > 0
      ? Math.floor(source.guideVersion)
      : ONBOARDING_GUIDE_VERSION
  const welcomeStatus = isGuideStatus(source.welcomeStatus) ? source.welcomeStatus : 'NOT_STARTED'
  const rawChecklistStatus = isGuideStatus(source.checklistStatus)
    ? source.checklistStatus
    : 'NOT_STARTED'

  const allCoreCompleted = CORE_TASK_KEYS.every((key) => tasks[key] === 'COMPLETED')
  const checklistStatus: GuideStatus = allCoreCompleted ? 'COMPLETED' : rawChecklistStatus

  return {
    enrolledVersion,
    guideVersion,
    welcomeStatus,
    checklistStatus,
    tasks,
    hints,
  }
}

export function isOnboardingEnrolled(state: OnboardingState): boolean {
  return state.guideVersion > 0 && state.enrolledVersion >= state.guideVersion
}

export function computeCoreTaskProgress(state: OnboardingState): CoreTaskProgress {
  let completed = 0
  const remainingKeys: CoreTaskKey[] = []
  for (let i = 0; i < CORE_TASK_KEYS.length; i += 1) {
    const key = CORE_TASK_KEYS[i]
    if (state.tasks[key] === 'COMPLETED') {
      completed += 1
    } else {
      remainingKeys.push(key)
    }
  }
  const total = CORE_TASK_KEYS.length
  const percentage = total > 0 ? Math.round((completed / total) * 100) : 0
  return {
    completed,
    total,
    percentage,
    allCompleted: completed >= total,
    remainingKeys,
  }
}

export function shouldAutoShowWelcome(state: OnboardingState): boolean {
  return isOnboardingEnrolled(state) && state.welcomeStatus === 'NOT_STARTED'
}

export function isChecklistActive(state: OnboardingState): boolean {
  if (!isOnboardingEnrolled(state)) {
    return false
  }
  if (state.checklistStatus !== 'NOT_STARTED' && state.checklistStatus !== 'IN_PROGRESS') {
    return false
  }
  return !computeCoreTaskProgress(state).allCompleted
}

export function mergeOnboardingStateMonotonic(
  local: OnboardingState,
  remote: OnboardingState,
): OnboardingState {
  const safeLocal = normalizeOnboardingState(local)
  const safeRemote = normalizeOnboardingState(remote)

  if (safeLocal.guideVersion !== safeRemote.guideVersion) {
    return safeRemote
  }

  const mergedTasks = createDefaultTaskMap()
  for (let i = 0; i < ALL_TASK_KEYS.length; i += 1) {
    const key = ALL_TASK_KEYS[i]
    mergedTasks[key] = maxGuideStatus(safeLocal.tasks[key], safeRemote.tasks[key])
  }

  const mergedHints: Partial<Record<HintKey, GuideStatus>> = {}
  for (let i = 0; i < ALL_HINT_KEYS.length; i += 1) {
    const key = ALL_HINT_KEYS[i]
    const mergedStatus = maxGuideStatus(safeLocal.hints[key], safeRemote.hints[key])
    if (mergedStatus !== 'NOT_STARTED') {
      mergedHints[key] = mergedStatus
    }
  }

  const allCoreCompleted = CORE_TASK_KEYS.every((key) => mergedTasks[key] === 'COMPLETED')
  const mergedChecklistStatus: GuideStatus = allCoreCompleted
    ? 'COMPLETED'
    : maxGuideStatus(safeLocal.checklistStatus, safeRemote.checklistStatus)

  return {
    enrolledVersion: safeRemote.enrolledVersion,
    guideVersion: safeRemote.guideVersion,
    welcomeStatus: maxGuideStatus(safeLocal.welcomeStatus, safeRemote.welcomeStatus),
    checklistStatus: mergedChecklistStatus,
    tasks: mergedTasks,
    hints: mergedHints,
  }
}

export function buildOnboardingCacheKey(
  userId: number | string,
  version: number = ONBOARDING_GUIDE_VERSION,
  scope: GuideScope = 'WEB',
): string {
  return `moodcopilot:onboarding:v${version}:${scope}:${String(userId)}`
}
