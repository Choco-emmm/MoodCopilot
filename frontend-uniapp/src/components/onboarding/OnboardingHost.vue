<template>
  <WelcomeSheet
    v-if="canRenderWelcome"
    :visible="canRenderWelcome"
    :replay-mode="welcomeReplayMode"
    @skip="handleSkip"
    @select-action="handleSelectAction"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { activeAnnouncement, announcementVisible } from '@/stores/announcement';
import { currentModal, popups } from '@/stores/globalUI';
import { loginSheetVisible } from '@/stores/login';
import {
  activeOnboardingRoute,
  allocateOnboardingHostId,
  claimOnboardingHost,
  closeWelcomeReplay,
  completeWelcome,
  isOnboardingHostActive,
  isRouteSafeForAutoWelcome,
  markWelcomeDisplayed,
  releaseOnboardingHost,
  skipWelcome,
  welcomeOpen,
  welcomeReplayMode,
} from '@/stores/onboarding';
import type { WelcomeActionKey } from '@/constants/onboarding';
import WelcomeSheet from './WelcomeSheet.vue';

const hostId = allocateOnboardingHostId();

const hasHigherPriorityOverlay = computed(() => {
  const announcementOpen = announcementVisible.value && Boolean(activeAnnouncement.value);
  return (
    announcementOpen ||
    loginSheetVisible.value ||
    Boolean(currentModal.value) ||
    popups.value.length > 0
  );
});

const isActiveHost = computed(() => isOnboardingHostActive(hostId));

const canRenderWelcome = computed(() => {
  if (!welcomeOpen.value || !isActiveHost.value) {
    return false;
  }
  if (hasHigherPriorityOverlay.value) {
    return false;
  }
  if (!welcomeReplayMode.value && !isRouteSafeForAutoWelcome(activeOnboardingRoute.value)) {
    return false;
  }
  return true;
});

watch(
  canRenderWelcome,
  (visible) => {
    if (visible && !welcomeReplayMode.value) {
      markWelcomeDisplayed();
    }
  },
  { immediate: true },
);

onMounted(() => {
  claimOnboardingHost(hostId);
});

onShow(() => {
  claimOnboardingHost(hostId);
});

onUnmounted(() => {
  releaseOnboardingHost(hostId);
});

const handleSkip = () => {
  if (welcomeReplayMode.value) {
    closeWelcomeReplay();
    return;
  }
  void skipWelcome();
};

const handleSelectAction = (actionKey: WelcomeActionKey) => {
  void completeWelcome(actionKey);
};
</script>
