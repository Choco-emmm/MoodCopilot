<template>
  <Teleport to="body">
    <div
      v-if="shouldRender && hint"
      class="spotlight-guide-layer"
      :class="{ 'is-non-blocking': nonBlocking, 'is-fallback': fallbackMode }"
      data-testid="spotlight-guide"
      :data-hint-key="hint.key"
      :data-fallback="fallbackMode ? 'true' : 'false'"
    >
      <div
        v-if="!fallbackMode && spotlightRect"
        class="spotlight-ring"
        aria-hidden="true"
        :style="ringStyle"
      />

      <aside
        ref="cardRef"
        class="spotlight-card"
        :class="{ 'is-fallback-card': fallbackMode }"
        :style="cardStyle"
        :role="nonBlocking ? 'status' : 'region'"
        :aria-label="`${ONBOARDING_COPY.hintBadge}：${hint.title}`"
        data-testid="spotlight-guide-card"
      >
        <div class="spotlight-head">
          <span class="spotlight-badge">{{ ONBOARDING_COPY.hintBadge }}</span>
          <button
            type="button"
            class="spotlight-close-btn"
            :aria-label="ONBOARDING_COPY.hintDismissLabel"
            data-testid="spotlight-dismiss-btn"
            @click="handleDismiss"
          >
            {{ ONBOARDING_COPY.hintDismissLabel }}
          </button>
        </div>

        <h3 class="spotlight-title">{{ hint.title }}</h3>
        <p class="spotlight-desc">{{ hint.description }}</p>

        <div class="spotlight-actions">
          <button
            type="button"
            class="spotlight-primary-btn"
            data-testid="spotlight-seen-btn"
            @click="handleSeen"
          >
            {{ hint.actionLabel || ONBOARDING_COPY.hintGotItLabel }}
          </button>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAiPreviewStore } from '../../stores/aiPreview'
import { useOnboardingStore } from '../../stores/onboarding'
import {
  ONBOARDING_COPY,
  type HintKey,
  type OnboardingHintDefinition,
} from '../../constants/onboarding'

interface AnchorBox {
  top: number
  left: number
  width: number
  height: number
}

const VIEWPORT_MARGIN = 12
const SPOTLIGHT_PADDING = 6
const CARD_GAP = 12
const MAX_CARD_WIDTH = 332

const props = withDefaults(
  defineProps<{
    hint: OnboardingHintDefinition | null
    visible?: boolean
    nonBlocking?: boolean
  }>(),
  {
    visible: true,
    nonBlocking: false,
  },
)

const emit = defineEmits<{
  (e: 'seen', key: HintKey): void
  (e: 'dismiss', key: HintKey): void
}>()

const route = useRoute()
const aiPreview = useAiPreviewStore()
const onboarding = useOnboardingStore()

const cardRef = ref<HTMLElement | null>(null)
const spotlightRect = ref<AnchorBox | null>(null)
const cardPosition = ref<{ top: number; left: number; width: number } | null>(null)
const fallbackMode = ref(false)
const isActivated = ref(true)
const ownerPath = ref(route.path)
const routeDismissed = ref(false)

let activeListeners = false
let rafId: number | null = null
let observedAnchor: HTMLElement | null = null
let resizeObserver: ResizeObserver | null = null

const shouldRender = computed(() => {
  if (!isActivated.value || !props.hint || !props.visible || routeDismissed.value) return false
  if (onboarding.isWelcomeOpen || onboarding.demoOpen) return false
  if (!props.nonBlocking && Boolean(aiPreview.current)) return false
  return true
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
  const el = document.querySelector(`[data-guide-id="${anchorId}"]`) as HTMLElement | null
  if (!el) return null
  const rect = el.getBoundingClientRect()
  if (rect.width <= 0 || rect.height <= 0) return null
  const style = window.getComputedStyle(el)
  if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0) {
    return null
  }
  const vh = window.innerHeight || 0
  const vw = window.innerWidth || 0
  if (rect.bottom < 0 || rect.top > vh || rect.right < 0 || rect.left > vw) {
    return null
  }
  return el
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

function measureAndPosition(): void {
  if (!shouldRender.value || !props.hint) {
    spotlightRect.value = null
    cardPosition.value = null
    fallbackMode.value = false
    updateResizeObserver(null)
    return
  }

  const anchor = findVisibleAnchor(props.hint.anchorId)
  updateResizeObserver(anchor)

  if (!anchor) {
    spotlightRect.value = null
    cardPosition.value = null
    fallbackMode.value = true
    return
  }

  const rect = anchor.getBoundingClientRect()
  const vw = window.innerWidth || 390
  const vh = window.innerHeight || 800

  spotlightRect.value = {
    top: Math.max(0, rect.top - SPOTLIGHT_PADDING),
    left: Math.max(0, rect.left - SPOTLIGHT_PADDING),
    width: Math.min(vw, rect.width + SPOTLIGHT_PADDING * 2),
    height: Math.min(vh, rect.height + SPOTLIGHT_PADDING * 2),
  }

  const cardWidth = Math.min(MAX_CARD_WIDTH, Math.max(220, vw - VIEWPORT_MARGIN * 2))
  const measuredCardHeight = cardRef.value?.getBoundingClientRect().height || 168

  // nonBlocking 模式优先放在锚点上方，避免遮挡底部操作按钮
  let top: number
  if (props.nonBlocking) {
    const aboveTop = rect.top - measuredCardHeight - CARD_GAP - SPOTLIGHT_PADDING
    if (aboveTop >= VIEWPORT_MARGIN) {
      top = aboveTop
    } else {
      fallbackMode.value = true
      cardPosition.value = null
      return
    }
  } else {
    const belowTop = rect.bottom + CARD_GAP + SPOTLIGHT_PADDING
    const aboveTop = rect.top - measuredCardHeight - CARD_GAP - SPOTLIGHT_PADDING
    if (belowTop + measuredCardHeight <= vh - VIEWPORT_MARGIN) {
      top = belowTop
    } else if (aboveTop >= VIEWPORT_MARGIN) {
      top = aboveTop
    } else {
      top = Math.max(VIEWPORT_MARGIN, Math.min(vh - measuredCardHeight - VIEWPORT_MARGIN, belowTop))
    }
  }

  const idealLeft = rect.left
  const left = Math.max(
    VIEWPORT_MARGIN,
    Math.min(vw - cardWidth - VIEWPORT_MARGIN, idealLeft),
  )

  fallbackMode.value = false
  cardPosition.value = { top, left, width: cardWidth }
}

function scheduleMeasure(): void {
  if (rafId !== null) return
  rafId = window.requestAnimationFrame(() => {
    rafId = null
    measureAndPosition()
  })
}

function onKeydown(event: KeyboardEvent): void {
  if (!shouldRender.value || !props.hint) return
  if (event.key === 'Escape') {
    event.preventDefault()
    void handleDismiss()
  }
}

function attachListeners(): void {
  if (activeListeners) return
  activeListeners = true
  window.addEventListener('resize', scheduleMeasure)
  window.addEventListener('scroll', scheduleMeasure, true)
  document.addEventListener('keydown', onKeydown, true)
}

function detachListeners(): void {
  if (rafId !== null) {
    window.cancelAnimationFrame(rafId)
    rafId = null
  }
  updateResizeObserver(null)
  if (!activeListeners) return
  activeListeners = false
  window.removeEventListener('resize', scheduleMeasure)
  window.removeEventListener('scroll', scheduleMeasure, true)
  document.removeEventListener('keydown', onKeydown, true)
}

watch(
  () => [shouldRender.value, props.hint?.key, props.hint?.anchorId, props.nonBlocking],
  async ([active]) => {
    if (active) {
      attachListeners()
      await nextTick()
      measureAndPosition()
    } else {
      detachListeners()
      spotlightRect.value = null
      cardPosition.value = null
      fallbackMode.value = false
    }
  },
  { immediate: true },
)

watch(
  () => props.hint?.key,
  () => {
    if (isActivated.value && route.path === ownerPath.value) {
      routeDismissed.value = false
    }
  },
)

watch(
  () => route.path,
  (nextPath) => {
    if (nextPath !== ownerPath.value) {
      routeDismissed.value = true
      detachListeners()
    }
  },
)

onActivated(() => {
  ownerPath.value = route.path
  isActivated.value = true
  routeDismissed.value = false
})

onDeactivated(() => {
  isActivated.value = false
  routeDismissed.value = true
  detachListeners()
})

onBeforeUnmount(() => {
  isActivated.value = false
  detachListeners()
})

async function handleSeen(): Promise<void> {
  if (!props.hint) return
  const key = props.hint.key
  detachListeners()
  await onboarding.recordHintSeen(key)
  emit('seen', key)
}

async function handleDismiss(): Promise<void> {
  if (!props.hint) return
  const key = props.hint.key
  detachListeners()
  await onboarding.dismissHint(key)
  emit('dismiss', key)
}
</script>

<style scoped>
.spotlight-guide-layer {
  position: fixed;
  inset: 0;
  z-index: 9100;
  pointer-events: none;
}

.spotlight-ring {
  position: fixed;
  border: 2px solid var(--color-primary);
  border-radius: var(--radius-md);
  background: color-mix(in oklab, var(--color-primary) 7%, transparent);
  box-shadow: 0 0 0 4px color-mix(in oklab, var(--color-primary) 16%, transparent);
  pointer-events: none;
  transition: top var(--duration-fast) var(--ease-out),
    left var(--duration-fast) var(--ease-out),
    width var(--duration-fast) var(--ease-out),
    height var(--duration-fast) var(--ease-out);
}

.spotlight-card {
  position: fixed;
  max-width: calc(100vw - 24px);
  padding: 14px 16px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text);
  box-shadow: var(--shadow-lg);
  pointer-events: auto;
  transition: top var(--duration-fast) var(--ease-out),
    left var(--duration-fast) var(--ease-out);
}

.spotlight-card.is-fallback-card {
  right: 16px;
  bottom: calc(20px + env(safe-area-inset-bottom));
  width: min(332px, calc(100vw - 32px));
}

.spotlight-guide-layer.is-non-blocking .spotlight-card.is-fallback-card {
  top: 84px;
  right: 16px;
  bottom: auto;
}

.spotlight-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.spotlight-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-full);
  background: var(--color-surface-soft);
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.spotlight-close-btn {
  padding: 2px 6px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.spotlight-close-btn:hover {
  color: var(--color-text);
  border-color: var(--color-border);
}

.spotlight-title {
  margin: 0 0 6px;
  font-family: var(--font-display);
  font-size: var(--text-base);
  font-weight: 600;
  line-height: var(--leading-snug);
  color: var(--color-text);
}

.spotlight-desc {
  margin: 0;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.spotlight-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.spotlight-primary-btn {
  padding: 5px 14px;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: var(--text-xs);
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.spotlight-primary-btn:hover {
  background: color-mix(in oklab, var(--color-primary) 88%, var(--color-text));
}

@media (prefers-reduced-motion: reduce) {
  .spotlight-ring,
  .spotlight-card,
  .spotlight-close-btn,
  .spotlight-primary-btn {
    transition: none;
  }
}
</style>
