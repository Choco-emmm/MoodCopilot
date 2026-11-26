<template>
  <Teleport to="body">
    <Transition name="onboarding-fade">
      <div
        v-if="open"
        class="onboarding-welcome-backdrop"
        data-testid="onboarding-welcome-backdrop"
        @click.self="handleSkip"
      >
        <div
          ref="dialogRef"
          class="onboarding-welcome-dialog"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          :aria-describedby="descId"
          tabindex="-1"
          data-testid="onboarding-welcome-dialog"
        >
          <!-- 标题区 -->
          <header class="welcome-header">
            <div class="welcome-meta">
              <span class="welcome-badge">
                {{ replayMode ? ONBOARDING_COPY.welcomeReplayBadge : ONBOARDING_COPY.welcomeBadge }}
              </span>
              <span class="welcome-step-counter" aria-live="polite">
                {{ stepCounterText }}
              </span>
            </div>
            <button
              ref="skipButtonRef"
              type="button"
              class="welcome-skip-btn"
              data-testid="onboarding-skip-btn"
              @click="handleSkip"
            >
              {{ replayMode ? ONBOARDING_COPY.welcomeReplayCloseLabel : ONBOARDING_COPY.welcomeSkipLabel }}
            </button>
          </header>

          <p v-if="replayMode" class="welcome-replay-note">
            {{ ONBOARDING_COPY.welcomeReplayNote }}
          </p>

          <!-- 内容区 -->
          <div class="welcome-body">
            <template v-if="activeSlide">
              <div class="welcome-eyebrow-row">
                <span class="welcome-step-num">{{ activeSlide.stepNumber }}</span>
                <span class="welcome-eyebrow-divider" aria-hidden="true"></span>
                <span class="welcome-eyebrow-label">{{ activeSlide.eyebrow }}</span>
              </div>

              <h2 :id="titleId" class="welcome-title">{{ activeSlide.title }}</h2>
              <p :id="descId" class="welcome-lead">{{ activeSlide.description }}</p>

              <ul class="welcome-highlights">
                <li
                  v-for="(item, idx) in activeSlide.highlights"
                  :key="idx"
                  class="welcome-highlight-item"
                >
                  <span class="welcome-highlight-marker" aria-hidden="true"></span>
                  <span>{{ item }}</span>
                </li>
              </ul>
            </template>

            <template v-else>
              <div class="welcome-eyebrow-row">
                <span class="welcome-step-num">GO</span>
                <span class="welcome-eyebrow-divider" aria-hidden="true"></span>
                <span class="welcome-eyebrow-label">开始探索</span>
              </div>

              <h2 :id="titleId" class="welcome-title">{{ ONBOARDING_COPY.welcomeActionsHeading }}</h2>
              <p :id="descId" class="welcome-lead">{{ ONBOARDING_COPY.welcomeActionsSubheading }}</p>

              <div class="welcome-action-list">
                <button
                  v-for="action in WELCOME_ACTIONS"
                  :key="action.key"
                  type="button"
                  :class="['welcome-action-card', `is-${action.emphasis}`]"
                  :data-testid="`onboarding-action-${action.key}`"
                  @click="handleAction(action.key)"
                >
                  <span class="welcome-action-main">
                    <span class="welcome-action-label">{{ action.label }}</span>
                    <span class="welcome-action-desc">{{ action.description }}</span>
                  </span>
                  <span class="welcome-action-arrow" aria-hidden="true">→</span>
                </button>
              </div>
            </template>
          </div>

          <!-- 操作区 -->
          <footer class="welcome-footer">
            <div class="welcome-progress" role="tablist" aria-label="引导步骤">
              <button
                v-for="(slide, idx) in WELCOME_SLIDES"
                :key="slide.id"
                type="button"
                role="tab"
                :aria-selected="stepIndex === idx"
                :aria-label="`第 ${idx + 1} 页：${slide.title}`"
                :class="['welcome-progress-step', { 'is-active': stepIndex === idx, 'is-passed': stepIndex > idx }]"
                @click="goToStep(idx)"
              >
                <span class="welcome-progress-bar"></span>
                <span class="welcome-progress-num">{{ slide.stepNumber }}</span>
              </button>
              <button
                type="button"
                role="tab"
                :aria-selected="isActionStep"
                aria-label="选择下一步操作"
                :class="['welcome-progress-step', { 'is-active': isActionStep }]"
                @click="goToStep(WELCOME_SLIDES.length)"
              >
                <span class="welcome-progress-bar"></span>
                <span class="welcome-progress-num">开始</span>
              </button>
            </div>

            <div class="welcome-nav-actions">
              <button
                v-if="stepIndex > 0"
                type="button"
                class="welcome-btn welcome-btn-secondary"
                data-testid="onboarding-prev-btn"
                @click="goPrev"
              >
                {{ ONBOARDING_COPY.welcomePrevLabel }}
              </button>
              <button
                v-if="!isActionStep"
                type="button"
                class="welcome-btn welcome-btn-primary"
                data-testid="onboarding-next-btn"
                @click="goNext"
              >
                {{
                  stepIndex < WELCOME_SLIDES.length - 1
                    ? ONBOARDING_COPY.welcomeNextLabel
                    : ONBOARDING_COPY.welcomeToActionsLabel
                }}
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
  ONBOARDING_COPY,
  WELCOME_ACTIONS,
  WELCOME_SLIDES,
  type WelcomeActionKey,
} from '../../constants/onboarding'

const props = defineProps<{
  open: boolean
  replayMode?: boolean
}>()

const emit = defineEmits<{
  (e: 'skip'): void
  (e: 'choose-action', action: WelcomeActionKey): void
}>()

const titleId = 'onboarding-welcome-title'
const descId = 'onboarding-welcome-desc'

const stepIndex = ref(0)
const dialogRef = ref<HTMLElement | null>(null)
const skipButtonRef = ref<HTMLButtonElement | null>(null)
let previouslyFocusedElement: HTMLElement | null = null

const isActionStep = computed(() => stepIndex.value >= WELCOME_SLIDES.length)
const activeSlide = computed(() =>
  stepIndex.value < WELCOME_SLIDES.length ? WELCOME_SLIDES[stepIndex.value] : null,
)
const stepCounterText = computed(() =>
  isActionStep.value
    ? '选择起点'
    : `${stepIndex.value + 1} / ${WELCOME_SLIDES.length}`,
)

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
    handleSkip()
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
      stepIndex.value = 0
      previouslyFocusedElement =
        document.activeElement instanceof HTMLElement ? document.activeElement : null
      document.addEventListener('keydown', onKeydown, true)
      await nextTick()
      if (dialogRef.value) {
        dialogRef.value.focus()
      } else {
        skipButtonRef.value?.focus()
      }
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

function goToStep(index: number): void {
  const maxStep = WELCOME_SLIDES.length
  stepIndex.value = Math.min(Math.max(0, index), maxStep)
}

function goPrev(): void {
  if (stepIndex.value > 0) {
    stepIndex.value -= 1
  }
}

function goNext(): void {
  if (stepIndex.value < WELCOME_SLIDES.length) {
    stepIndex.value += 1
  }
}

function handleSkip(): void {
  emit('skip')
}

function handleAction(action: WelcomeActionKey): void {
  emit('choose-action', action)
}
</script>

<style scoped>
.onboarding-welcome-backdrop {
  position: fixed;
  inset: 0;
  z-index: 9500;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 16px calc(20px + env(safe-area-inset-bottom));
  background: var(--color-overlay);
}

.onboarding-welcome-dialog {
  width: min(540px, 100%);
  max-height: min(88vh, 660px);
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

.welcome-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 24px 14px;
  border-bottom: 1px solid var(--color-border);
}

.welcome-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.welcome-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-full);
  background: var(--color-surface-soft);
  color: var(--color-primary);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: 0.08em;
}

.welcome-step-counter {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  letter-spacing: 0.06em;
}

.welcome-skip-btn {
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.welcome-skip-btn:hover {
  color: var(--color-text);
  border-color: var(--color-border);
  background: var(--color-surface-hover);
}

.welcome-replay-note {
  margin: 0;
  padding: 8px 24px;
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  background: var(--color-surface-soft);
  border-bottom: 1px solid var(--color-border);
}

.welcome-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 28px 28px 24px;
}

.welcome-eyebrow-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.welcome-step-num {
  font-family: var(--font-display);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-primary);
  letter-spacing: 0.12em;
}

.welcome-eyebrow-divider {
  width: 24px;
  height: 1px;
  background: var(--color-border-strong);
}

.welcome-eyebrow-label {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  letter-spacing: 0.1em;
}

.welcome-title {
  margin: 0 0 12px;
  font-family: var(--font-display);
  font-size: clamp(1.35rem, 3vw, 1.65rem);
  font-weight: 600;
  line-height: var(--leading-snug);
  color: var(--color-text);
  letter-spacing: 0.02em;
}

.welcome-lead {
  margin: 0 0 22px;
  font-size: var(--text-base);
  line-height: var(--leading-relaxed);
  color: var(--color-text-secondary);
}

.welcome-highlights {
  list-style: none;
  margin: 0;
  padding: 16px 0 0;
  border-top: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.welcome-highlight-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
  color: var(--color-text-secondary);
}

.welcome-highlight-marker {
  width: 5px;
  height: 5px;
  margin-top: 9px;
  border-radius: var(--radius-full);
  background: var(--color-primary);
  flex-shrink: 0;
}

.welcome-action-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 4px;
}

.welcome-action-card {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  text-align: left;
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.welcome-action-card.is-primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.welcome-action-card.is-primary:hover {
  background: color-mix(in oklab, var(--color-primary) 90%, var(--color-text));
}

.welcome-action-card.is-primary .welcome-action-desc,
.welcome-action-card.is-primary .welcome-action-arrow {
  color: color-mix(in oklab, var(--color-on-primary) 85%, transparent);
}

.welcome-action-card.is-secondary {
  background: transparent;
  border: 1px solid var(--color-border-strong);
  color: var(--color-text);
}

.welcome-action-card.is-secondary:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
}

.welcome-action-card.is-ghost {
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}

.welcome-action-card.is-ghost:hover {
  color: var(--color-text);
  border-color: var(--color-border-strong);
  background: var(--color-surface-soft);
}

.welcome-action-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.welcome-action-label {
  font-size: var(--text-base);
  font-weight: 600;
  line-height: 1.35;
}

.welcome-action-desc {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  line-height: 1.5;
}

.welcome-action-arrow {
  font-size: var(--text-md);
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.welcome-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 24px 20px;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface-soft);
}

.welcome-progress {
  display: flex;
  align-items: center;
  gap: 10px;
}

.welcome-progress-step {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 2px 0;
  border: none;
  background: transparent;
  cursor: pointer;
}

.welcome-progress-bar {
  display: block;
  width: 22px;
  height: 2px;
  border-radius: var(--radius-full);
  background: var(--color-border-strong);
  transition: width var(--duration-normal) var(--ease-out),
    background-color var(--duration-normal) var(--ease-out);
}

.welcome-progress-step.is-passed .welcome-progress-bar {
  background: color-mix(in oklab, var(--color-primary) 55%, transparent);
}

.welcome-progress-step.is-active .welcome-progress-bar {
  width: 34px;
  background: var(--color-primary);
}

.welcome-progress-num {
  font-size: 11px;
  color: var(--color-text-light);
  line-height: 1;
}

.welcome-progress-step.is-active .welcome-progress-num {
  color: var(--color-primary);
  font-weight: 600;
}

.welcome-nav-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.welcome-btn {
  padding: 8px 18px;
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.welcome-btn-primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.welcome-btn-primary:hover {
  background: color-mix(in oklab, var(--color-primary) 88%, var(--color-text));
}

.welcome-btn-secondary {
  background: transparent;
  border: 1px solid var(--color-border-strong);
  color: var(--color-text-secondary);
}

.welcome-btn-secondary:hover {
  color: var(--color-text);
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
}

.onboarding-fade-enter-active,
.onboarding-fade-leave-active {
  transition: opacity var(--duration-normal) var(--ease-out);
}

.onboarding-fade-enter-from,
.onboarding-fade-leave-to {
  opacity: 0;
}

@media (max-width: 540px) {
  .welcome-header {
    padding: 14px 18px 12px;
  }

  .welcome-replay-note {
    padding: 8px 18px;
  }

  .welcome-body {
    padding: 22px 18px 18px;
  }

  .welcome-footer {
    flex-direction: column;
    align-items: stretch;
    gap: 14px;
    padding: 14px 18px 16px;
  }

  .welcome-nav-actions {
    justify-content: flex-end;
  }
}

@media (prefers-reduced-motion: reduce) {
  .onboarding-fade-enter-active,
  .onboarding-fade-leave-active,
  .welcome-skip-btn,
  .welcome-action-card,
  .welcome-progress-bar,
  .welcome-btn {
    transition: none;
  }
}
</style>
