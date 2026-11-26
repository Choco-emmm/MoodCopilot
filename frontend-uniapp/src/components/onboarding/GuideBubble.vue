<template>
  <view
    v-if="visible && hintDefinition"
    class="guide-bubble"
    :class="{ compact }"
    role="region"
  >
    <view class="guide-bubble-head">
      <text class="guide-bubble-badge">{{ ONBOARDING_COPY.hintBadge }}</text>
      <text class="guide-bubble-close" role="button" @click="handleDismiss">×</text>
    </view>

    <text class="guide-bubble-title">{{ hintDefinition.title }}</text>
    <text class="guide-bubble-desc">{{ hintDefinition.description }}</text>

    <view class="guide-bubble-actions">
      <view
        class="guide-btn-primary"
        role="button"
        @click="handleAcknowledge"
      >
        <text class="guide-btn-primary-text">
          {{ hintDefinition.actionLabel || ONBOARDING_COPY.hintGotItLabel }}
        </text>
      </view>
      <text
        class="guide-btn-dismiss"
        role="button"
        @click="handleDismiss"
      >
        {{ ONBOARDING_COPY.hintDismissLabel }}
      </text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { ONBOARDING_COPY, ONBOARDING_HINT_MAP } from '@/constants/onboarding';
import type { HintKey } from '@/constants/onboarding';
import { activeAnnouncement, announcementVisible } from '@/stores/announcement';
import { currentModal, popups } from '@/stores/globalUI';
import { loginSheetVisible } from '@/stores/login';
import { canShowHint, dismissHint, recordHintSeen } from '@/stores/onboarding';

const props = withDefaults(
  defineProps<{
    hintKey: HintKey | null;
    eligible?: boolean;
    compact?: boolean;
  }>(),
  {
    eligible: true,
    compact: false,
  },
);

const hasHigherPriorityOverlay = computed(() => {
  const announcementOpen = announcementVisible.value && Boolean(activeAnnouncement.value);
  return (
    announcementOpen ||
    loginSheetVisible.value ||
    Boolean(currentModal.value) ||
    popups.value.length > 0
  );
});

const hintDefinition = computed(() => {
  if (!props.hintKey) return null;
  return ONBOARDING_HINT_MAP[props.hintKey] || null;
});

const visible = computed(() => {
  if (!props.eligible || !props.hintKey) return false;
  return canShowHint(props.hintKey, { overlayBlocked: hasHigherPriorityOverlay.value });
});

function handleAcknowledge() {
  if (!props.hintKey) return;
  void recordHintSeen(props.hintKey);
}

function handleDismiss() {
  if (!props.hintKey) return;
  void dismissHint(props.hintKey);
}
</script>

<style scoped>
.guide-bubble {
  margin: 18rpx 0;
  padding: 22rpx 24rpx 20rpx;
  background: color-mix(in oklab, var(--theme-primary) 5%, var(--theme-surface));
  border: 1rpx solid var(--theme-border);
  border-left: 5rpx solid var(--theme-primary);
  border-radius: var(--theme-radius-md);
  box-shadow: var(--theme-shadow-panel);
  box-sizing: border-box;
}

.guide-bubble.compact {
  margin: 12rpx 0;
  padding: 18rpx 20rpx 16rpx;
}

.guide-bubble-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-bottom: 10rpx;
}

.guide-bubble-badge {
  font-size: 20rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
  color: var(--theme-primary);
}

.guide-bubble-close {
  padding: 2rpx 8rpx;
  font-size: 32rpx;
  line-height: 1;
  color: var(--theme-text-placeholder);
}

.guide-bubble-title {
  display: block;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 29rpx;
  font-weight: 700;
  line-height: 1.4;
  color: var(--theme-text-primary);
  margin-bottom: 8rpx;
}

.guide-bubble-desc {
  display: block;
  font-size: 24rpx;
  line-height: 1.65;
  color: var(--theme-text-secondary);
}

.guide-bubble-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 18rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid var(--theme-border);
}

.guide-btn-primary {
  padding: 10rpx 22rpx;
  border-radius: var(--theme-radius-sm);
  background: var(--theme-primary);
}

.guide-btn-primary-text {
  font-size: 23rpx;
  font-weight: 600;
  color: var(--theme-text-on-primary);
}

.guide-btn-dismiss {
  padding: 8rpx 6rpx;
  font-size: 22rpx;
  color: var(--theme-text-secondary);
}
</style>
