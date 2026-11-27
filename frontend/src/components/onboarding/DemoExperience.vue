<template>
  <Teleport to="body">
    <Transition name="demo-fade">
      <div
        v-if="open"
        class="demo-backdrop"
        data-testid="onboarding-demo-backdrop"
        @click.self="handleClose"
      >
        <div
          ref="dialogRef"
          class="demo-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="onboarding-demo-title"
          aria-describedby="onboarding-demo-disclaimer"
          tabindex="-1"
          data-testid="onboarding-demo-dialog"
        >
          <!-- 标题区 -->
          <header class="demo-header">
            <div class="demo-header-meta">
              <span class="demo-badge">{{ ONBOARDING_DEMO_STORY.badge }}</span>
              <span class="demo-story-name">「{{ ONBOARDING_DEMO_STORY.storyTitle }}」</span>
              <span class="demo-step-counter" aria-live="polite">
                {{ session.stepIndex + 1 }} / {{ ONBOARDING_DEMO_STEPS.length }}
              </span>
            </div>
            <div class="demo-header-actions">
              <button
                type="button"
                class="demo-ghost-btn"
                data-testid="demo-replay-btn"
                @click="handleReplay"
              >
                {{ ONBOARDING_DEMO_COPY.replayLabel }}
              </button>
              <button
                type="button"
                class="demo-ghost-btn"
                data-testid="demo-exit-btn"
                @click="handleClose"
              >
                {{ ONBOARDING_DEMO_COPY.exitLabel }}
              </button>
            </div>
          </header>

          <!-- 隔离声明条 -->
          <div id="onboarding-demo-disclaimer" class="demo-banner" role="note">
            {{ ONBOARDING_DEMO_STORY.bannerText }}
          </div>

          <!-- 步骤导览 -->
          <nav class="demo-step-tabs" role="tablist" aria-label="示例体验步骤">
            <button
              v-for="(step, idx) in ONBOARDING_DEMO_STEPS"
              :key="step.id"
              type="button"
              role="tab"
              :aria-selected="session.stepIndex === idx"
              :class="['demo-step-tab', { 'is-active': session.stepIndex === idx, 'is-passed': session.stepIndex > idx }]"
              :data-testid="`demo-tab-${step.id}`"
              @click="goToStep(idx)"
            >
              <span class="demo-step-tab-num">{{ step.stepNumber }}</span>
              <span class="demo-step-tab-title">{{ step.title }}</span>
            </button>
          </nav>

          <!-- 内容区 -->
          <div class="demo-body">
            <div class="demo-section-intro">
              <h2 id="onboarding-demo-title" class="demo-section-title">
                {{ currentStep.title }}
              </h2>
              <p class="demo-section-subtitle">{{ currentStep.subtitle }}</p>
            </div>

            <!-- Step 1: 示例日记 -->
            <article
              v-if="currentStep.id === 'diary'"
              class="demo-diary-article"
              data-testid="demo-step-diary"
            >
              <div class="demo-diary-meta">
                <span>{{ ONBOARDING_DEMO_STORY.diary.dateLabel }}</span>
                <span class="demo-pill">{{ ONBOARDING_DEMO_STORY.diary.visibilityLabel }}</span>
                <span class="demo-pill">{{ ONBOARDING_DEMO_STORY.diary.moodLabel }}</span>
              </div>
              <h3 class="demo-diary-title">{{ ONBOARDING_DEMO_STORY.diary.title }}</h3>
              <div class="demo-diary-content">
                <p v-for="(para, i) in diaryParagraphs" :key="i">{{ para }}</p>
              </div>
              <div class="demo-diary-attachments">
                <p class="demo-attachment-line">{{ ONBOARDING_DEMO_STORY.diary.photoCaption }}</p>
                <p class="demo-attachment-line">{{ ONBOARDING_DEMO_STORY.diary.musicCaption }}</p>
              </div>
            </article>

            <!-- Step 2: 示例情绪分析 -->
            <section
              v-else-if="currentStep.id === 'analysis'"
              class="demo-analysis-panel"
              data-testid="demo-step-analysis"
            >
              <div class="demo-analysis-head">
                <span class="demo-pill">{{ ONBOARDING_DEMO_STORY.analysis.demoTag }}</span>
                <span class="demo-analysis-intensity">
                  强度 {{ ONBOARDING_DEMO_STORY.analysis.moodIntensity }} / 5
                </span>
              </div>
              <h3 class="demo-analysis-mood">{{ ONBOARDING_DEMO_STORY.analysis.primaryMood }}</h3>
              <div class="demo-tag-row">
                <span
                  v-for="tag in ONBOARDING_DEMO_STORY.analysis.emotionTags"
                  :key="tag"
                  class="demo-tag"
                >
                  {{ tag }}
                </span>
              </div>
              <p class="demo-analysis-summary">{{ ONBOARDING_DEMO_STORY.analysis.summary }}</p>

              <ul class="demo-insight-list">
                <li
                  v-for="(point, idx) in ONBOARDING_DEMO_STORY.analysis.insightPoints"
                  :key="idx"
                  class="demo-insight-item"
                >
                  <span class="demo-dot" aria-hidden="true"></span>
                  <span>{{ point }}</span>
                </li>
              </ul>

              <blockquote class="demo-gentle-quote">
                {{ ONBOARDING_DEMO_STORY.analysis.gentlePrompt }}
              </blockquote>
            </section>

            <!-- Step 3: 示例记忆与依据 -->
            <section
              v-else-if="currentStep.id === 'memory'"
              class="demo-memory-section"
              data-testid="demo-step-memory"
            >
              <p class="demo-inline-disclaimer">{{ ONBOARDING_DEMO_STORY.memoryDisclaimer }}</p>

              <div class="demo-memory-list">
                <article
                  v-for="item in ONBOARDING_DEMO_STORY.memories"
                  :key="item.id"
                  :class="['demo-memory-item', { 'is-candidate': item.kind === 'candidate' }]"
                  :data-testid="`demo-memory-${item.id}`"
                >
                  <div class="demo-memory-top">
                    <span class="demo-memory-key">{{ item.attributeKey }}</span>
                    <span class="demo-pill">
                      {{ item.kind === 'candidate' ? candidateStatusText : item.statusLabel }}
                    </span>
                  </div>
                  <p class="demo-memory-value">{{ item.value }}</p>
                  <p v-if="item.candidateNote" class="demo-memory-note">{{ item.candidateNote }}</p>

                  <div class="demo-memory-controls">
                    <button
                      type="button"
                      class="demo-link-btn"
                      :aria-expanded="isEvidenceExpanded(item.id)"
                      :data-testid="`demo-evidence-toggle-${item.id}`"
                      @click="toggleEvidence(item.id)"
                    >
                      {{
                        isEvidenceExpanded(item.id)
                          ? ONBOARDING_DEMO_COPY.hideEvidenceLabel
                          : ONBOARDING_DEMO_COPY.showEvidenceLabel
                      }}
                    </button>

                    <div v-if="item.kind === 'candidate'" class="demo-candidate-actions">
                      <button
                        type="button"
                        :class="['demo-mini-btn', { 'is-selected': session.candidateDecision === 'approved' }]"
                        data-testid="demo-candidate-approve"
                        @click="setCandidateDecision('approved')"
                      >
                        {{ ONBOARDING_DEMO_COPY.approveCandidateLabel }}
                      </button>
                      <button
                        type="button"
                        :class="['demo-mini-btn', { 'is-selected': session.candidateDecision === 'rejected' }]"
                        data-testid="demo-candidate-reject"
                        @click="setCandidateDecision('rejected')"
                      >
                        {{ ONBOARDING_DEMO_COPY.rejectCandidateLabel }}
                      </button>
                    </div>
                  </div>

                  <div
                    v-if="isEvidenceExpanded(item.id)"
                    class="demo-evidence-box"
                    :data-testid="`demo-evidence-${item.id}`"
                  >
                    <span class="demo-evidence-source">{{ item.sourceLabel }} · 原文摘录</span>
                    <blockquote class="demo-evidence-quote">「{{ item.evidence }}」</blockquote>
                  </div>
                </article>
              </div>
            </section>

            <!-- Step 4: 预设聊天与引用卡 -->
            <section
              v-else-if="currentStep.id === 'chat'"
              class="demo-chat-section"
              data-testid="demo-step-chat"
            >
              <p class="demo-inline-disclaimer" data-testid="demo-chat-disclaimer">
                {{ ONBOARDING_DEMO_STORY.chatDisclaimer }}
              </p>

              <div class="demo-chat-presets">
                <span class="demo-preset-heading">{{ ONBOARDING_DEMO_COPY.chatPresetHeading }}</span>
                <div class="demo-preset-list">
                  <button
                    v-for="preset in ONBOARDING_DEMO_STORY.chatPresets"
                    :key="preset.id"
                    type="button"
                    :class="['demo-preset-chip', { 'is-used': session.selectedChatPresetIds.includes(preset.id) }]"
                    :data-testid="`demo-chat-preset-${preset.id}`"
                    @click="selectChatPreset(preset.id)"
                  >
                    {{ preset.userPrompt }}
                  </button>
                </div>
              </div>

              <div v-if="activeChatTurns.length" class="demo-chat-thread" data-testid="demo-chat-thread">
                <div
                  v-for="turn in activeChatTurns"
                  :key="turn.id"
                  class="demo-chat-turn"
                >
                  <div class="demo-chat-user">
                    <span class="demo-chat-role">示例提问</span>
                    <p>{{ turn.userPrompt }}</p>
                  </div>
                  <div class="demo-chat-ai">
                    <div class="demo-chat-ai-head">
                      <span class="demo-pill">预设演示回复 · 非实时 AI</span>
                    </div>
                    <p class="demo-chat-reply">{{ turn.reply }}</p>

                    <div class="demo-citations">
                      <button
                        v-for="cite in turn.citations"
                        :key="cite.id"
                        type="button"
                        :class="['demo-citation-chip', { 'is-active': session.activeCitationId === cite.id }]"
                        :data-testid="`demo-citation-${cite.id}`"
                        @click="toggleCitation(cite.id)"
                      >
                        {{ cite.label }}
                      </button>
                    </div>

                    <div
                      v-if="activeCitationInTurn(turn)"
                      class="demo-citation-preview"
                      data-testid="demo-citation-preview"
                    >
                      <span class="demo-evidence-source">
                        {{ activeCitationInTurn(turn)?.label }} · 演示引用依据
                      </span>
                      <blockquote class="demo-evidence-quote">
                        「{{ activeCitationInTurn(turn)?.excerpt }}」
                      </blockquote>
                    </div>
                  </div>
                </div>
              </div>
              <p v-else class="demo-chat-empty">
                点选上方任意一个示例问题，查看预设演示回复与可核对的引用卡片。
              </p>
            </section>
          </div>

          <!-- 操作区 -->
          <footer class="demo-footer">
            <div class="demo-footer-left">
              <button
                v-if="session.stepIndex > 0"
                type="button"
                class="demo-btn demo-btn-secondary"
                data-testid="demo-prev-btn"
                @click="goPrev"
              >
                {{ ONBOARDING_DEMO_COPY.prevStepLabel }}
              </button>
            </div>

            <div class="demo-footer-right">
              <button
                v-if="session.stepIndex < ONBOARDING_DEMO_STEPS.length - 1"
                type="button"
                class="demo-btn demo-btn-secondary"
                data-testid="demo-next-btn"
                @click="goNext"
              >
                {{ ONBOARDING_DEMO_COPY.nextStepLabel }}
              </button>
              <button
                type="button"
                class="demo-btn demo-btn-primary"
                data-testid="demo-start-real-write-btn"
                @click="handleStartWriting"
              >
                {{ ONBOARDING_DEMO_COPY.startRealWriteLabel }}
              </button>
            </div>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
  ONBOARDING_DEMO_COPY,
  ONBOARDING_DEMO_STEPS,
  ONBOARDING_DEMO_STORY,
  createInitialDemoSessionState,
  type DemoCandidateDecision,
  type DemoChatCitation,
  type DemoChatPreset,
  type DemoSessionState,
} from '../../constants/onboardingDemo'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'start-writing'): void
}>()

const dialogRef = ref<HTMLElement | null>(null)
const session = ref<DemoSessionState>(createInitialDemoSessionState())
let previouslyFocusedElement: HTMLElement | null = null

const currentStep = computed(
  () => ONBOARDING_DEMO_STEPS[session.value.stepIndex] || ONBOARDING_DEMO_STEPS[0],
)

const diaryParagraphs = computed(() =>
  ONBOARDING_DEMO_STORY.diary.content.split('\n\n').filter((p) => p.trim().length > 0),
)

const candidateStatusText = computed(() => {
  if (session.value.candidateDecision === 'approved') {
    return ONBOARDING_DEMO_COPY.candidateApprovedLabel
  }
  if (session.value.candidateDecision === 'rejected') {
    return ONBOARDING_DEMO_COPY.candidateRejectedLabel
  }
  return '待确认候选示例'
})

const activeChatTurns = computed<DemoChatPreset[]>(() => {
  const result: DemoChatPreset[] = []
  for (const id of session.value.selectedChatPresetIds) {
    const found = ONBOARDING_DEMO_STORY.chatPresets.find((preset) => preset.id === id)
    if (found) result.push(found)
  }
  return result
})

function isEvidenceExpanded(id: string): boolean {
  return session.value.expandedEvidenceIds.includes(id)
}

function toggleEvidence(id: string): void {
  const idx = session.value.expandedEvidenceIds.indexOf(id)
  if (idx >= 0) {
    session.value.expandedEvidenceIds.splice(idx, 1)
  } else {
    session.value.expandedEvidenceIds.push(id)
  }
}

function setCandidateDecision(decision: DemoCandidateDecision): void {
  session.value.candidateDecision = decision
}

function selectChatPreset(presetId: string): void {
  if (!session.value.selectedChatPresetIds.includes(presetId)) {
    session.value.selectedChatPresetIds.push(presetId)
  }
  const preset = ONBOARDING_DEMO_STORY.chatPresets.find((p) => p.id === presetId)
  if (preset && preset.citations.length > 0) {
    session.value.activeCitationId = preset.citations[0].id
  }
}

function toggleCitation(citationId: string): void {
  session.value.activeCitationId =
    session.value.activeCitationId === citationId ? null : citationId
}

function activeCitationInTurn(turn: DemoChatPreset): DemoChatCitation | null {
  if (!session.value.activeCitationId) return null
  return turn.citations.find((c) => c.id === session.value.activeCitationId) || null
}

function goToStep(index: number): void {
  session.value.stepIndex = Math.min(Math.max(0, index), ONBOARDING_DEMO_STEPS.length - 1)
}

function goPrev(): void {
  if (session.value.stepIndex > 0) {
    session.value.stepIndex -= 1
  }
}

function goNext(): void {
  if (session.value.stepIndex < ONBOARDING_DEMO_STEPS.length - 1) {
    session.value.stepIndex += 1
  }
}

function handleReplay(): void {
  session.value = createInitialDemoSessionState()
}

function handleClose(): void {
  emit('close')
}

function handleStartWriting(): void {
  emit('start-writing')
}

function getFocusableElements(): HTMLElement[] {
  const root = dialogRef.value
  if (!root) return []
  const selectors = [
    'button:not([disabled])',
    'a[href]',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
  ].join(',')
  return Array.from(root.querySelectorAll<HTMLElement>(selectors)).filter(
    (el) => el.offsetParent !== null || el === document.activeElement,
  )
}

function onKeydown(event: KeyboardEvent): void {
  if (!props.open) return
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    handleClose()
    return
  }
  if (event.key === 'Tab') {
    const focusable = getFocusableElements()
    if (focusable.length === 0) {
      event.preventDefault()
      dialogRef.value?.focus()
      return
    }
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    const current = document.activeElement as HTMLElement | null
    if (event.shiftKey) {
      if (!current || current === first || !dialogRef.value?.contains(current)) {
        event.preventDefault()
        last.focus()
      }
    } else if (!current || current === last || !dialogRef.value?.contains(current)) {
      event.preventDefault()
      first.focus()
    }
  }
}

function restoreFocus(): void {
  const target = previouslyFocusedElement
  previouslyFocusedElement = null
  if (target && typeof target.focus === 'function' && document.contains(target)) {
    target.focus()
  }
}

watch(
  () => props.open,
  async (isOpen) => {
    if (isOpen) {
      session.value = createInitialDemoSessionState()
      previouslyFocusedElement =
        document.activeElement instanceof HTMLElement ? document.activeElement : null
      document.addEventListener('keydown', onKeydown, true)
      await nextTick()
      dialogRef.value?.focus()
    } else {
      document.removeEventListener('keydown', onKeydown, true)
      await nextTick()
      restoreFocus()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown, true)
  restoreFocus()
})
</script>

<style scoped>
.demo-backdrop {
  position: fixed;
  inset: 0;
  z-index: 9550;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 16px calc(20px + env(safe-area-inset-bottom));
  background: var(--color-overlay);
}

.demo-dialog {
  width: min(640px, 100%);
  max-height: min(90vh, 760px);
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  color: var(--color-text);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  overflow: hidden;
  outline: none;
}

.demo-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 22px 12px;
  border-bottom: 1px solid var(--color-border);
}

.demo-header-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.demo-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-full);
  background: var(--color-surface-soft);
  color: var(--color-primary);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: 0.06em;
}

.demo-story-name {
  font-family: var(--font-display);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text);
}

.demo-step-counter {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.demo-header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.demo-ghost-btn {
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.demo-ghost-btn:hover {
  color: var(--color-text);
  border-color: var(--color-border);
  background: var(--color-surface-hover);
}

.demo-banner {
  padding: 8px 22px;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  background: var(--color-surface-soft);
  border-bottom: 1px solid var(--color-border);
}

.demo-step-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg);
}

.demo-step-tab {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 8px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.demo-step-tab.is-active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  background: var(--color-surface);
  font-weight: 600;
}

.demo-step-tab.is-passed {
  color: var(--color-text-secondary);
}

.demo-step-tab-num {
  font-family: var(--font-display);
  font-size: 11px;
}

.demo-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 22px 24px 20px;
}

.demo-section-intro {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
}

.demo-section-title {
  margin: 0 0 4px;
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--color-text);
}

.demo-section-subtitle {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.demo-pill {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: var(--color-surface-soft);
  color: var(--color-text-secondary);
  font-size: 11px;
}

.demo-diary-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.demo-diary-title {
  margin: 0 0 12px;
  font-family: var(--font-display);
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text);
}

.demo-diary-content p {
  margin: 0 0 10px;
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--color-text);
}

.demo-diary-content p:last-child {
  margin-bottom: 0;
}

.demo-diary-attachments {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.demo-attachment-line {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.demo-analysis-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.demo-analysis-intensity {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.demo-analysis-mood {
  margin: 0 0 10px;
  font-family: var(--font-display);
  font-size: var(--text-md);
  color: var(--color-primary);
}

.demo-tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}

.demo-tag {
  padding: 2px 9px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-soft);
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.demo-analysis-summary {
  margin: 0 0 14px;
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--color-text);
}

.demo-insight-list {
  list-style: none;
  margin: 0 0 14px;
  padding: 12px 0 0;
  border-top: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.demo-insight-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.demo-dot {
  width: 5px;
  height: 5px;
  margin-top: 8px;
  border-radius: var(--radius-full);
  background: var(--color-primary);
  flex-shrink: 0;
}

.demo-gentle-quote {
  margin: 0;
  padding: 10px 14px;
  border-left: 2px solid var(--color-primary);
  background: var(--color-surface-soft);
  font-size: var(--text-xs);
  line-height: 1.65;
  color: var(--color-text-secondary);
}

.demo-inline-disclaimer {
  margin: 0 0 12px;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.demo-memory-list {
  display: flex;
  flex-direction: column;
}

.demo-memory-item {
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border);
}

.demo-memory-item:first-child {
  padding-top: 0;
}

.demo-memory-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.demo-memory-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.demo-memory-key {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-primary);
}

.demo-memory-value {
  margin: 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-text);
}

.demo-memory-note {
  margin: 6px 0 0;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.demo-memory-controls {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.demo-link-btn {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
  text-decoration: underline;
  cursor: pointer;
}

.demo-link-btn:hover {
  color: var(--color-primary);
}

.demo-candidate-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.demo-mini-btn {
  padding: 4px 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
  cursor: pointer;
}

.demo-mini-btn.is-selected {
  border-color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 12%, transparent);
  color: var(--color-primary);
  font-weight: 600;
}

.demo-evidence-box {
  margin-top: 10px;
  padding: 10px 12px;
  border-left: 2px solid var(--color-border-strong);
  background: var(--color-surface-soft);
}

.demo-evidence-source {
  display: block;
  font-size: 11px;
  color: var(--color-text-muted);
  margin-bottom: 4px;
}

.demo-evidence-quote {
  margin: 0;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.demo-preset-heading {
  display: block;
  margin-bottom: 8px;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-secondary);
}

.demo-preset-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.demo-preset-chip {
  padding: 9px 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font-size: var(--text-xs);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.demo-preset-chip:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
}

.demo-preset-chip.is-used {
  border-color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 8%, transparent);
}

.demo-chat-thread {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.demo-chat-turn {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.demo-chat-user {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
}

.demo-chat-role {
  display: block;
  font-size: 11px;
  color: var(--color-text-muted);
  margin-bottom: 3px;
}

.demo-chat-user p,
.demo-chat-reply {
  margin: 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-text);
}

.demo-chat-ai {
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border-strong);
  background: var(--color-surface);
}

.demo-chat-ai-head {
  margin-bottom: 6px;
}

.demo-citations {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.demo-citation-chip {
  padding: 3px 9px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: var(--color-surface-soft);
  color: var(--color-text-secondary);
  font-size: 11px;
  cursor: pointer;
}

.demo-citation-chip.is-active {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.demo-citation-preview {
  margin-top: 10px;
  padding: 8px 10px;
  border-left: 2px solid var(--color-primary);
  background: var(--color-surface-soft);
}

.demo-chat-empty {
  margin: 16px 0 0;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.demo-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 22px 18px;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface-soft);
}

.demo-footer-left,
.demo-footer-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.demo-btn {
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.demo-btn-primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.demo-btn-primary:hover {
  background: color-mix(in oklab, var(--color-primary) 88%, var(--color-text));
}

.demo-btn-secondary {
  background: transparent;
  border: 1px solid var(--color-border-strong);
  color: var(--color-text-secondary);
}

.demo-btn-secondary:hover {
  color: var(--color-text);
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
}

.demo-fade-enter-active,
.demo-fade-leave-active {
  transition: opacity var(--duration-normal) var(--ease-out);
}

.demo-fade-enter-from,
.demo-fade-leave-to {
  opacity: 0;
}

@media (max-width: 560px) {
  .demo-header {
    padding: 12px 14px;
  }

  .demo-banner {
    padding: 8px 14px;
  }

  .demo-step-tab {
    flex-direction: column;
    gap: 2px;
    padding: 8px 4px;
    font-size: 11px;
  }

  .demo-body {
    padding: 16px 14px;
  }

  .demo-footer {
    padding: 12px 14px 14px;
    flex-wrap: wrap;
  }
}

@media (prefers-reduced-motion: reduce) {
  .demo-fade-enter-active,
  .demo-fade-leave-active,
  .demo-ghost-btn,
  .demo-step-tab,
  .demo-preset-chip,
  .demo-btn {
    transition: none;
  }
}
</style>
