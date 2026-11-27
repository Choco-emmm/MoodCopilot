export type DemoStepId = 'diary' | 'analysis' | 'memory' | 'chat'

export type DemoMemoryKind = 'formal' | 'candidate'

export type DemoCandidateDecision = 'pending' | 'approved' | 'rejected'

export interface DemoStepMeta {
  id: DemoStepId
  stepNumber: string
  title: string
  subtitle: string
}

export interface DemoDiaryEntry {
  id: string
  title: string
  dateLabel: string
  visibilityLabel: string
  moodLabel: string
  content: string
  photoCaption: string
  musicCaption: string
}

export interface DemoMoodAnalysis {
  demoTag: string
  primaryMood: string
  moodIntensity: number
  emotionTags: readonly string[]
  summary: string
  insightPoints: readonly string[]
  gentlePrompt: string
}

export interface DemoMemoryItem {
  id: string
  kind: DemoMemoryKind
  attributeKey: string
  value: string
  memoryType: 'preference' | 'habit' | 'goal'
  statusLabel: string
  sourceLabel: string
  /** 必须为 diary.content 的连续原文子串 */
  evidence: string
  candidateNote?: string
}

export interface DemoChatCitation {
  id: string
  sourceType: 'diary' | 'memory'
  label: string
  excerpt: string
}

export interface DemoChatPreset {
  id: string
  userPrompt: string
  reply: string
  citations: readonly DemoChatCitation[]
}

export interface OnboardingDemoStory {
  storyId: string
  storyTitle: string
  badge: string
  bannerText: string
  disclaimer: string
  memoryDisclaimer: string
  chatDisclaimer: string
  diary: DemoDiaryEntry
  analysis: DemoMoodAnalysis
  memories: readonly DemoMemoryItem[]
  chatPresets: readonly DemoChatPreset[]
}

export interface DemoSessionState {
  stepIndex: number
  expandedEvidenceIds: string[]
  candidateDecision: DemoCandidateDecision
  selectedChatPresetIds: string[]
  activeCitationId: string | null
}

export const ONBOARDING_DEMO_STEPS: readonly DemoStepMeta[] = [
  {
    id: 'diary',
    stepNumber: '01',
    title: '示例日记',
    subtitle: '看看一篇私密记录如何写下当下的片段',
  },
  {
    id: 'analysis',
    stepNumber: '02',
    title: '情绪分析',
    subtitle: 'AI 梳理情绪脉络，帮助你回头看见变化',
  },
  {
    id: 'memory',
    stepNumber: '03',
    title: '记忆与依据',
    subtitle: '每条记忆可核对原文依据，变更由你决定',
  },
  {
    id: 'chat',
    stepNumber: '04',
    title: '上下文对话',
    subtitle: '预设演示回复与引用卡，体验带记忆的交流',
  },
]

export const ONBOARDING_DEMO_STORY: OnboardingDemoStory = {
  storyId: 'photography-contest-v1',
  storyTitle: '摄影比赛',
  badge: '演示示例',
  bannerText: '虚拟示例 · 不会写入你的账号，也不会消耗 AI 额度',
  disclaimer:
    '当前展示的是虚拟故事「摄影比赛」，仅用于提前体验记录、分析、记忆与对话流程，不会保存任何真实日记、记忆或聊天记录。',
  memoryDisclaimer: '仅演示交互效果，不会写入你的真实记忆或画像。',
  chatDisclaimer: '预设演示回复 · 非实时 AI，不会写入你的真实记录或消耗额度',
  diary: {
    id: 'demo-diary-photo-contest',
    title: '交出摄影比赛参赛稿的傍晚',
    dateLabel: '示例记录 · 周六傍晚',
    visibilityLabel: '仅自己可见（默认私密）',
    moodLabel: '期待与忐忑交织',
    content:
      '下午五点终于把这次校园摄影比赛的参赛作品交上去了。选来选去，最后投的是上周雨后在老图书馆台阶拍的那张倒影，画面很安静，光影刚好落在石阶边上。\n\n交稿前其实反复纠结了很久，总觉得构图还能再克制一点，但看到提交成功的提示时还是松了一口气。今年很想在摄影比赛里拿到一个名次，哪怕只是入围，也算是给自己坚持拍了一整年的小交代。\n\n回宿舍的路上风有点凉，我戴着耳机沿着湖边慢慢走了一圈，听完那首常听的钢琴曲，紧绷了一周的肩膀终于放松下来。',
    photoCaption: '示例附图：雨后老图书馆石阶倒影（纯文字示意，不加载外部图片）',
    musicCaption: '示例配乐：傍晚湖边散步的轻缓钢琴曲',
  },
  analysis: {
    demoTag: '示例情绪分析',
    primaryMood: '期待 · 略带紧张',
    moodIntensity: 3,
    emotionTags: ['期待', '松一口气', '自我要求高'],
    summary:
      '完成摄影比赛投稿后，你迎来了一次明显的放松：交稿前反复琢磨构图带来短暂紧绷，但提交后的踏实与期待逐渐占了上风。',
    insightPoints: [
      '从反复权衡细节到按时投出作品，说明你既在意表达质量，也能在合适时机做出决定。',
      '雨后倒影与湖边听钢琴曲的片段，显示安静的独处节奏能很快帮你卸下压力。',
    ],
    gentlePrompt: '作品已经稳稳交出去了，今晚不必再反复挑剔细节，把时间留给轻松的休息吧。',
  },
  memories: [
    {
      id: 'demo-mem-hobby',
      kind: 'formal',
      attributeKey: '兴趣爱好',
      value: '长期坚持摄影，偏爱安静克制的光影与构图',
      memoryType: 'preference',
      statusLabel: '正式记忆示例',
      sourceLabel: '来自示例日记',
      evidence: '算是给自己坚持拍了一整年的小交代',
    },
    {
      id: 'demo-mem-relax',
      kind: 'formal',
      attributeKey: '常用解压方式',
      value: '戴耳机在湖边听钢琴曲散步，帮助自己放松下来',
      memoryType: 'habit',
      statusLabel: '正式记忆示例',
      sourceLabel: '来自示例日记',
      evidence: '戴着耳机沿着湖边慢慢走了一圈，听完那首常听的钢琴曲',
    },
    {
      id: 'demo-mem-goal',
      kind: 'candidate',
      attributeKey: '近期目标',
      value: '希望在今年的校园摄影比赛中入围或拿到名次',
      memoryType: 'goal',
      statusLabel: '待确认候选示例',
      sourceLabel: '来自示例日记',
      evidence: '今年很想在摄影比赛里拿到一个名次，哪怕只是入围',
      candidateNote: '涉及需要你把关的记忆变动时，可以先展开原文依据，再决定确认或暂不记下。',
    },
  ],
  chatPresets: [
    {
      id: 'contest-nerves',
      userPrompt: '交完摄影比赛稿件还是有点忐忑，怎么缓解等待结果的焦虑？',
      reply:
        '你已经为这次投稿积累了一整年，也选出了最贴近自己审美的雨后倒影。等待结果时，可以把注意力放回可控的小事上，比如继续去湖边散步，或者不带比赛压力地随手拍几张日常光影。',
      citations: [
        {
          id: 'cite-diary-1',
          sourceType: 'diary',
          label: '示例日记 · 周六傍晚',
          excerpt: '最后投的是上周雨后在老图书馆台阶拍的那张倒影，画面很安静',
        },
        {
          id: 'cite-mem-1',
          sourceType: 'memory',
          label: '示例记忆 · 常用解压方式',
          excerpt: '戴着耳机沿着湖边慢慢走了一圈，听完那首常听的钢琴曲',
        },
      ],
    },
    {
      id: 'why-this-photo',
      userPrompt: '回顾这篇记录，我为什么最后会选老图书馆台阶那张照片？',
      reply:
        '从这篇记录能看出，你追求的不是刻意抢眼，而是克制、安静的光影层次。老图书馆雨后石阶的倒影刚好呼应了你一整年的拍摄习惯，所以提交之后反而觉得心里踏实。',
      citations: [
        {
          id: 'cite-diary-2',
          sourceType: 'diary',
          label: '示例日记 · 周六傍晚',
          excerpt: '总觉得构图还能再克制一点，但看到提交成功的提示时还是松了一口气',
        },
        {
          id: 'cite-mem-2',
          sourceType: 'memory',
          label: '示例记忆 · 兴趣爱好',
          excerpt: '算是给自己坚持拍了一整年的小交代',
        },
      ],
    },
    {
      id: 'general-plan',
      userPrompt: '除了聊心情，平时也可以找你一起规划作品集整理吗？',
      reply:
        '完全可以。除了梳理日记和情绪，MoodCopilot 也是你的通用个人助手，随时可以一起整理作品集目录、拆解学习计划，或讨论写作与工作问题。',
      citations: [
        {
          id: 'cite-mem-3',
          sourceType: 'memory',
          label: '示例记忆 · 近期目标',
          excerpt: '今年很想在摄影比赛里拿到一个名次，哪怕只是入围',
        },
      ],
    },
  ],
}

export const ONBOARDING_DEMO_COPY = {
  replayLabel: '重新体验',
  exitLabel: '退出体验',
  startRealWriteLabel: '开始真实记录',
  prevStepLabel: '上一步',
  nextStepLabel: '下一步',
  showEvidenceLabel: '查看原文依据',
  hideEvidenceLabel: '收起依据',
  approveCandidateLabel: '演示确认',
  rejectCandidateLabel: '演示拒绝',
  candidateApprovedLabel: '已演示确认（仅本地）',
  candidateRejectedLabel: '已演示拒绝（仅本地）',
  chatPresetHeading: '点一个示例问题，看看带上下文的演示回复',
  citationToggleLabel: '查看演示引用',
} as const

export function createInitialDemoSessionState(): DemoSessionState {
  return {
    stepIndex: 0,
    expandedEvidenceIds: [],
    candidateDecision: 'pending',
    selectedChatPresetIds: [],
    activeCitationId: null,
  }
}
