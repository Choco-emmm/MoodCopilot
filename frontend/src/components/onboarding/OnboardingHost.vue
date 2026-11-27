<template>
  <WelcomeDialog
    :open="onboarding.isWelcomeOpen"
    :replay-mode="onboarding.replayMode"
    @skip="handleSkip"
    @choose-action="handleChooseAction"
  />
  <DemoExperience
    :open="onboarding.demoOpen"
    @close="onboarding.closeDemoExperience()"
    @start-writing="handleStartRealWriting"
  />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WelcomeDialog from './WelcomeDialog.vue'
import DemoExperience from './DemoExperience.vue'
import { useAuthStore } from '../../stores/auth'
import { useAiPreviewStore } from '../../stores/aiPreview'
import { useOnboardingStore } from '../../stores/onboarding'
import type { WelcomeActionKey } from '../../constants/onboarding'

const BLOCKED_ROUTES = new Set(['/login', '/register', '/reset-password'])
const AUTO_OPEN_DELAY_MS = 120

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const aiPreview = useAiPreviewStore()
const onboarding = useOnboardingStore()

let evalTimer: ReturnType<typeof setTimeout> | null = null

function clearEvalTimer(): void {
  if (evalTimer != null) {
    clearTimeout(evalTimer)
    evalTimer = null
  }
}

function isRouteBlocked(): boolean {
  const path = route.path || ''
  if (BLOCKED_ROUTES.has(path)) return true
  if (path.startsWith('/admin')) return true
  if (route.meta?.requiresAdmin) return true
  return false
}

function isWriteEditorActivelyFocused(): boolean {
  if (route.path !== '/write') return false
  const active = document.activeElement as HTMLElement | null
  if (!active || active === document.body) return false
  const tag = active.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || active.isContentEditable) {
    return true
  }
  return Boolean(active.closest('[contenteditable="true"], .w-e-text-container, .w-e-scroll'))
}

function hasBlockingOverlayInDom(): boolean {
  return Boolean(document.querySelector('.n-modal-mask, .n-dialog'))
}

function canAutoTriggerNow(): boolean {
  if (!auth.isAuthenticated || auth.userId == null) return false
  if (!onboarding.canAutoShowWelcome || onboarding.isWelcomeOpen || onboarding.demoOpen) return false
  if (isRouteBlocked()) return false
  if (Boolean(aiPreview.current)) return false
  if (onboarding.externalModalBlocking) return false
  if (isWriteEditorActivelyFocused()) return false
  if (hasBlockingOverlayInDom()) return false
  return true
}

function scheduleAutoEvaluate(): void {
  clearEvalTimer()
  if (!onboarding.canAutoShowWelcome || onboarding.isWelcomeOpen) return
  evalTimer = setTimeout(() => {
    evalTimer = null
    if (canAutoTriggerNow()) {
      onboarding.openAutoWelcome()
    }
  }, AUTO_OPEN_DELAY_MS)
}

function onFocusOut(): void {
  if (route.path === '/write' && onboarding.canAutoShowWelcome && !onboarding.isWelcomeOpen) {
    scheduleAutoEvaluate()
  }
}

watch(
  [
    () => auth.isAuthenticated,
    () => auth.userId,
    () => onboarding.canAutoShowWelcome,
    () => onboarding.externalModalBlocking,
    () => aiPreview.current,
    () => route.fullPath,
  ],
  () => {
    if (!auth.isAuthenticated || auth.userId == null) {
      clearEvalTimer()
      return
    }
    scheduleAutoEvaluate()
  },
  { immediate: true },
)

onMounted(() => {
  document.addEventListener('focusout', onFocusOut)
})

onBeforeUnmount(() => {
  clearEvalTimer()
  document.removeEventListener('focusout', onFocusOut)
})

async function handleSkip(): Promise<void> {
  await onboarding.skipWelcome()
}

async function handleChooseAction(action: WelcomeActionKey): Promise<void> {
  await onboarding.completeWelcome()
  if (action === 'TRY_DEMO') {
    onboarding.openDemoExperience()
    return
  }
  if (action === 'WRITE_TODAY') {
    if (route.path !== '/write') {
      void router.push('/write')
    }
    return
  }
  if (action === 'CHAT_WITH_AI') {
    if (route.path !== '/chat') {
      void router.push('/chat')
    }
  }
}

function handleStartRealWriting(): void {
  onboarding.closeDemoExperience()
  if (route.path !== '/write') {
    void router.push('/write')
  }
}
</script>
