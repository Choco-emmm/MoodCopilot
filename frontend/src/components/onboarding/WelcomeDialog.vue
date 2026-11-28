<template>
  <Teleport to="body">
    <Transition name="onboarding-fade">
      <div
        v-if="open"
        class="tour-layer"
        :class="{ 'is-fallback': fallbackMode }"
        data-testid="onboarding-welcome-backdrop"
        :data-fallback="fallbackMode ? 'true' : 'false'"
      >
        <!-- 四向聚光灯暗色遮罩：在真实控件周围留出高亮透视窗 -->
        <template v-if="!fallbackMode && spotlightRect">
          <div class="tour-dim-panel" :style="dimTopStyle" aria-hidden="true" />
          <div class="tour-dim-panel" :style="dimBottomStyle" aria-hidden="true" />
          <div class="tour-dim-panel" :style="dimLeftStyle" aria-hidden="true" />
          <div class="tour-dim-panel" :style="dimRightStyle" aria-hidden="true" />
          <div
            class="tour-spotlight-ring"
            aria-hidden="true"
            data-testid="onboarding-spotlight-ring"
            :style="ringStyle"
          />
        </template>
        <div v-else class="tour-dim-full" aria-hidden="true" />

        <!-- 紧贴目标控件的编辑部气泡卡 -->
        <div
          ref="dialogRef"
          class="tour-card"
          :class="{
            'is-centered': fallbackMode,
            'is-below': !fallbackMode && cardPlacement === 'below',
            'is-above': !fallbackMode && cardPlacement === 'above',
          }"
          :style="cardStyle"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          :aria-describedby="descId"
          tabindex="-1"
          data-testid="onboarding-welcome-dialog"
          :data-tour-step="currentStep.id"
        >
          <span
            v-if="!fallbackMode && arrowLeftPx != null"
            class="tour-arrow"
            :style="{ left: `${arrowLeftPx}px` }"
            aria-hidden="true"
          />

          <header class="tour-header">
            <div class="tour-meta">
              <span class="tour-badge">
                {{ replayMode ? ONBOARDING_COPY.welcomeReplayBadge : ONBOARDING_COPY.welcomeBadge }}
              </span>
              <span class="tour-counter" aria-live="polite">
                {{ stepIndex + 1 }} / {{ steps.length }}
              </span>
            </div>
            <button
              ref="skipButtonRef"
              type="button"
              class="tour-skip-btn"
              data-testid="onboarding-skip-btn"
              @click="handleSkip"
            >
              {{ replayMode ? ONBOARDING_COPY.welcomeReplayCloseLabel : ONBOARDING_COPY.welcomeSkipLabel }}
            </button>
          </header>

          <div class="tour-progress" role="tablist" aria-label="引导步骤">
            <button
              v-for="(step, idx) in steps"
              :key="step.id"
              type="button"
              role="tab"
              :aria-selected="stepIndex === idx"
              :aria-label="`第 ${idx + 1} 步：${step.title}`"
              :class="['tour-progress-bar', { 'is-active': stepIndex === idx, 'is-passed': stepIndex > idx }]"
              @click="goToStep(idx)"
            />
          </div>

          <div class="tour-body">
            <h2 :id="titleId" class="tour-title">{{ currentStep.title }}</h2>
            <p :id="descId" class="tour-desc">{{ currentStep.description }}</p>
          </div>

          <footer class="tour-footer">
            <template v-if="!isLastStep">
              <div class="tour-footer-left">
                <button
                  v-if="stepIndex > 0"
                  type="button"
                  class="tour-btn tour-btn-secondary"
                  data-testid="onboarding-prev-btn"
                  @click="goPrev"
                >
                  {{ ONBOARDING_COPY.welcomePrevLabel }}
                </button>
              </div>
              <button
                type="button"
                class="tour-btn tour-btn-primary"
                data-testid="onboarding-next-btn"
                @click="goNext"
              >
                {{ ONBOARDING_COPY.welcomeNextLabel }}
              </button>
            </template>

            <template v-else>
              <div class="tour-final-actions">
                <button
                  v-for="action in orderedFinalActions"
                  :key="action.key"
                  type="button"
                  :class="['tour-btn', `tour-btn-${action.emphasis}`]"
                  :data-testid="`onboarding-action-${action.key}`"
                  @click="handleAction(action.key)"
                >
                  {{ action.label }}
                </button>
              </div>
              <button
                type="button"
                class="tour-back-link"
                data-testid="onboarding-prev-btn"
                @click="goPrev"
              >
                ← {{ ONBOARDING_COPY.welcomePrevLabel }}
              </button>
            </template>
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
  WEB_WELCOME_TOUR_STEPS,
  WELCOME_ACTIONS,
  type WelcomeActionKey,
} from '../../constants/onboarding'

interface AnchorBox {
  top: number
  left: number
  width: number
  height: number
}

const VIEWPORT_MARGIN = 12
const SPOTLIGHT_PADDING = 6
const CARD_GAP = 14
const MAX_CARD_WIDTH = 368

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
const steps = WEB_WELCOME_TOUR_STEPS

const stepIndex = ref(0)
const dialogRef = ref<HTMLElement | null>(null)
const skipButtonRef = ref<HTMLButtonElement | null>(null)
const spotlightRect = ref<AnchorBox | null>(null)
const cardPosition = ref<{ top: number; left: number; width: number } | null>(null)
const cardPlacement = ref<'below' | 'above'>('below')
const arrowLeftPx = ref<number | null>(null)
const fallbackMode = ref(false)

let previouslyFocusedElement: HTMLElement | null = null
let rafId: number | null = null
let activeListeners = false
let observedAnchor: HTMLElement | null = null
let resizeObserver: ResizeObserver | null = null

const currentStep = computed(() => steps[stepIndex.value] || steps[0])
const isLastStep = computed(() => stepIndex.value >= steps.length - 1)

const orderedFinalActions = computed(() => {
  const primary = WELCOME_ACTIONS.filter((a) => a.emphasis === 'primary')
  const secondary = WELCOME_ACTIONS.filter((a) => a.emphasis === 'secondary')
  const ghost = WELCOME_ACTIONS.filter((a) => a.emphasis === 'ghost')
  return [...primary, ...secondary, ...ghost]
})

const dimTopStyle = computed(() => {
  if (!spotlightRect.value) return {}
  return { top: '0px', left: '0px', right: '0px', height: `${Math.max(0, spotlightRect.value.top)}px` }
})

const dimBottomStyle = computed(() => {
  if (!spotlightRect.value) return {}
  const top = Math.max(0, spotlightRect.value.top + spotlightRect.value.height)
  return { top: `${top}px`, left: '0px', right: '0px', bottom: '0px' }
})

const dimLeftStyle = computed(() => {
  if (!spotlightRect.value) return {}
  return {
    top: `${Math.max(0, spotlightRect.value.top)}px`,
    left: '0px',
    width: `${Math.max(0, spotlightRect.value.left)}px`,
    height: `${Math.max(0, spotlightRect.value.height)}px`,
  }
})

const dimRightStyle = computed(() => {
  if (!spotlightRect.value) return {}
  return {
    top: `${Math.max(0, spotlightRect.value.top)}px`,
    left: `${Math.max(0, spotlightRect.value.left + spotlightRect.value.width)}px`,
    right: '0px',
    height: `${Math.max(0, spotlightRect.value.height)}px`,
  }
})

const ringStyle = computed(() => {
  if (!spotlightRect.value) return {}
  return {
    top: `${spotlightRect.value.top}px`,
    left: `${spotlightRect.value.left}px`,
    width: `${spotlightRect.value.width}px`,
    height: `${spotlightRect.value.height}px`,
  }
})

const cardStyle = computed(() => {
  if (fallbackMode.value || !cardPosition.value) {
    return {}
  }
  return {
    top: `${cardPosition.value.top}px`,
    left: `${cardPosition.value.left}px`,
    width: `${cardPosition.value.width}px`,
  }
})

function findVisibleAnchor(anchorId: string): HTMLElement | null {
  if (typeof document === 'undefined' || !anchorId) return null
  const nodes = document.querySelectorAll(`[data-guide-id="${anchorId}"]`)
  if (!nodes.length) return null
  const vw = window.innerWidth || 0
  const vh = window.innerHeight || 0

  for (let i = 0; i < nodes.length; i += 1) {
    const el = nodes[i] as HTMLElement
    const rect = el.getBoundingClientRect()
    if (rect.width <= 0 || rect.height <= 0) continue
    const style = window.getComputedStyle(el)
    if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0) {
      continue
    }
    if (rect.bottom <= 2 || rect.top >= vh - 2 || rect.right <= 2 || rect.left >= vw - 2) {
      continue
    }
    return el
  }
  return null
}

function updateResizeObserver(anchor: HTMLElement | null): void {
  if (observedAnchor === anchor) return
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  observedAnchor = anchor
  if (anchor && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      scheduleMeasure()
    })
    resizeObserver.observe(anchor)
  }
}

function measureSpotlight(): void {
  if (!props.open) {
    spotlightRect.value = null
    cardPosition.value = null
    arrowLeftPx.value = null
    fallbackMode.value = false
    updateResizeObserver(null)
    return
  }

  const anchor = findVisibleAnchor(currentStep.value.anchorId)
  updateResizeObserver(anchor)

  if (!anchor) {
    spotlightRect.value = null
    cardPosition.value = null
    arrowLeftPx.value = null
    fallbackMode.value = true
    return
  }

  const rect = anchor.getBoundingClientRect()
  const vw = window.innerWidth || 390
  const vh = window.innerHeight || 800

  const top = Math.max(2, rect.top - SPOTLIGHT_PADDING)
  const left = Math.max(2, rect.left - SPOTLIGHT_PADDING)
  const right = Math.min(vw - 2, rect.right + SPOTLIGHT_PADDING)
  const bottom = Math.min(vh - 2, rect.bottom + SPOTLIGHT_PADDING)

  spotlightRect.value = {
    top,
    left,
    width: Math.max(20, right - left),
    height: Math.max(20, bottom - top),
  }
  fallbackMode.value = false

  const cardWidth = Math.min(MAX_CARD_WIDTH, Math.max(260, vw - VIEWPORT_MARGIN * 2))
  const estimatedHeight = dialogRef.value?.offsetHeight || 228
  const anchorCenterX = left + (right - left) / 2

  let cardLeft = anchorCenterX - cardWidth / 2
  cardLeft = Math.max(VIEWPORT_MARGIN, Math.min(cardLeft, vw - cardWidth - VIEWPORT_MARGIN))

  const spaceBelow = vh - bottom - VIEWPORT_MARGIN
  const spaceAbove = top - VIEWPORT_MARGIN
  let cardTop: number

  if (spaceBelow >= estimatedHeight + CARD_GAP || spaceBelow >= spaceAbove) {
    cardPlacement.value = 'below'
    cardTop = Math.min(bottom + CARD_GAP, vh - estimatedHeight - VIEWPORT_MARGIN)
  } else {
    cardPlacement.value = 'above'
    cardTop = Math.max(VIEWPORT_MARGIN, top - estimatedHeight - CARD_GAP)
  }

  cardPosition.value = {
    top: Math.max(VIEWPORT_MARGIN, cardTop),
    left: cardLeft,
    width: cardWidth,
  }
  arrowLeftPx.value = Math.max(22, Math.min(cardWidth - 22, anchorCenterX - cardLeft))
}

function scheduleMeasure(): void {
  if (typeof window === 'undefined') return
  if (rafId != null) {
    window.cancelAnimationFrame(rafId)
  }
  rafId = window.requestAnimationFrame(() => {
    rafId = null
    measureSpotlight()
  })
}

function bindListeners(): void {
  if (activeListeners || typeof window === 'undefined') return
  activeListeners = true
  window.addEventListener('resize', scheduleMeasure)
  window.addEventListener('scroll', scheduleMeasure, true)
  document.addEventListener('keydown', onKeydown, true)
}

function unbindListeners(): void {
  if (!activeListeners || typeof window === 'undefined') return
  activeListeners = false
  window.removeEventListener('resize', scheduleMeasure)
  window.removeEventListener('scroll', scheduleMeasure, true)
  document.removeEventListener('keydown', onKeydown, true)
  if (rafId != null) {
    window.cancelAnimationFrame(rafId)
    rafId = null
  }
  updateResizeObserver(null)
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
      bindListeners()
      await nextTick()
      measureSpotlight()
      scheduleMeasure()
      if (dialogRef.value) {
        dialogRef.value.focus()
      } else {
        skipButtonRef.value?.focus()
      }
    } else {
      unbindListeners()
      await nextTick()
      restoreFocus()
    }
  },
  { immediate: true },
)

watch(stepIndex, async () => {
  if (!props.open) return
  await nextTick()
  measureSpotlight()
  scheduleMeasure()
})

onBeforeUnmount(() => {
  unbindListeners()
  restoreFocus()
})

function goToStep(index: number): void {
  stepIndex.value = Math.min(Math.max(0, index), steps.length - 1)
}

function goPrev(): void {
  if (stepIndex.value > 0) {
    stepIndex.value -= 1
  }
}

function goNext(): void {
  if (stepIndex.value < steps.length - 1) {
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
.tour-layer {
  position: fixed;
  inset: 0;
  z-index: 9500;
}

.tour-dim-panel,
.tour-dim-full {
  position: fixed;
  background: var(--color-overlay);
}

.tour-dim-full {
  inset: 0;
}

.tour-spotlight-ring {
  position: fixed;
  border-radius: var(--radius-md);
  border: 2px solid var(--color-primary);
  box-shadow: 0 0 0 4px color-mix(in oklab, var(--color-primary) 24%, transparent);
  pointer-events: none;
  transition: top var(--duration-normal) var(--ease-out),
    left var(--duration-normal) var(--ease-out),
    width var(--duration-normal) var(--ease-out),
    height var(--duration-normal) var(--ease-out);
}

.tour-card {
  position: fixed;
  background: var(--color-surface);
  color: var(--color-text);
  border: 1px solid var(--color-border-strong);
  border-top: 3px solid var(--color-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  padding: 16px 18px 16px;
  outline: none;
  box-sizing: border-box;
  transition: top var(--duration-normal) var(--ease-out),
    left var(--duration-normal) var(--ease-out);
}

.tour-card.is-centered {
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: min(400px, calc(100vw - 32px));
}

.tour-arrow {
  position: absolute;
  width: 10px;
  height: 10px;
  background: var(--color-surface);
  transform: translateX(-50%) rotate(45deg);
  pointer-events: none;
}

.tour-card.is-below .tour-arrow {
  top: -7px;
  border-left: 2px solid var(--color-primary);
  border-top: 2px solid var(--color-primary);
}

.tour-card.is-above .tour-arrow {
  bottom: -6px;
  border-right: 1px solid var(--color-border-strong);
  border-bottom: 1px solid var(--color-border-strong);
}

.tour-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.tour-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tour-badge {
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

.tour-counter {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.tour-skip-btn {
  padding: 2px 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  cursor: pointer;
}

.tour-skip-btn:hover {
  color: var(--color-text);
  border-color: var(--color-border);
  background: var(--color-surface-hover);
}

.tour-progress {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  margin-bottom: 14px;
}

.tour-progress-bar {
  height: 3px;
  padding: 0;
  border: none;
  border-radius: var(--radius-full);
  background: var(--color-border);
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.tour-progress-bar.is-passed {
  background: color-mix(in oklab, var(--color-primary) 48%, transparent);
}

.tour-progress-bar.is-active {
  background: var(--color-primary);
}

.tour-body {
  margin-bottom: 16px;
}

.tour-title {
  margin: 0 0 6px;
  font-family: var(--font-display);
  font-size: var(--text-md);
  font-weight: 600;
  line-height: var(--leading-snug);
  color: var(--color-text);
}

.tour-desc {
  margin: 0;
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--color-text-secondary);
}

.tour-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.tour-footer-left {
  display: flex;
  align-items: center;
}

.tour-final-actions {
  width: 100%;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.tour-btn {
  padding: 7px 14px;
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  font-weight: 600;
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.tour-btn-primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.tour-btn-primary:hover {
  background: color-mix(in oklab, var(--color-primary) 88%, var(--color-text));
}

.tour-btn-secondary {
  background: transparent;
  border: 1px solid var(--color-border-strong);
  color: var(--color-text);
}

.tour-btn-secondary:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
}

.tour-btn-ghost {
  background: transparent;
  border: 1px solid transparent;
  color: var(--color-text-muted);
}

.tour-btn-ghost:hover {
  color: var(--color-text);
  border-color: var(--color-border);
  background: var(--color-surface-hover);
}

.tour-back-link {
  padding: 2px 0;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  cursor: pointer;
}

.tour-back-link:hover {
  color: var(--color-text);
}

.onboarding-fade-enter-active,
.onboarding-fade-leave-active {
  transition: opacity var(--duration-normal) var(--ease-out);
}

.onboarding-fade-enter-from,
.onboarding-fade-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .onboarding-fade-enter-active,
  .onboarding-fade-leave-active,
  .tour-spotlight-ring,
  .tour-card,
  .tour-progress-bar,
  .tour-btn {
    transition: none;
  }
}
</style>
