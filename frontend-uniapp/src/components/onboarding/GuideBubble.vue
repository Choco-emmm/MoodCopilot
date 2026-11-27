<template>
  <view
    v-if="visible && hintDefinition"
    class="guide-bubble-wrap"
    :class="{
      'is-floating': floating,
      'has-tabbar': floating && hasTabBar,
      'is-compact': compact,
    }"
    role="region"
  >
    <view class="guide-bubble">
      <view class="guide-bubble-head">
        <view class="guide-bubble-badge">
          <text class="guide-bubble-badge-text">{{ ONBOARDING_COPY.hintBadge }}</text>
        </view>
        <text class="guide-bubble-dismiss" role="button" @click="handleDismiss">
          {{ ONBOARDING_COPY.hintDismissLabel }}
        </text>
      </view>

      <text class="guide-bubble-title">{{ hintDefinition.title }}</text>
      <text class="guide-bubble-desc">{{ hintDefinition.description }}</text>

      <view class="guide-bubble-actions">
        <view
          v-if="locateLabel"
          class="guide-btn-secondary"
          role="button"
          @click="handleLocate"
        >
          <text class="guide-btn-secondary-text">{{ locateLabel }}</text>
        </view>
        <view
          class="guide-btn-primary"
          role="button"
          @click="handleAcknowledge"
        >
          <text class="guide-btn-primary-text">
            {{ hintDefinition.actionLabel || ONBOARDING_COPY.hintGotItLabel }}
          </text>
        </view>
      </view>
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
    floating?: boolean;
    hasTabBar?: boolean;
    locateLabel?: string;
  }>(),
  {
    eligible: true,
    compact: false,
    floating: true,
    hasTabBar: false,
    locateLabel: '',
  },
);

const emit = defineEmits<{
  (event: 'locate', key: HintKey): void;
  (event: 'seen', key: HintKey): void;
  (event: 'dismiss', key: HintKey): void;
}>();

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

function handleLocate() {
  if (!props.hintKey) return;
  const key = props.hintKey;
  emit('locate', key);
  void recordHintSeen(key);
  emit('seen', key);
}

function handleAcknowledge() {
  if (!props.hintKey) return;
  const key = props.hintKey;
  void recordHintSeen(key);
  emit('seen', key);
}

function handleDismiss() {
  if (!props.hintKey) return;
  const key = props.hintKey;
  void dismissHint(key);
  emit('dismiss', key);
}
</script>

<style scoped>
.guide-bubble-wrap {
  box-sizing: border-box;
  margin: 16rpx 0;
}

.guide-bubble-wrap.is-floating {
  position: fixed;
  left: 28rpx;
  right: 28rpx;
  bottom: calc(28rpx + env(safe-area-inset-bottom));
  z-index: 9400;
  margin: 0;
  pointer-events: none;
}

.guide-bubble-wrap.is-floating.has-tabbar {
  bottom: calc(136rpx + env(safe-area-inset-bottom));
}

.guide-bubble {
  pointer-events: auto;
  padding: 24rpx 28rpx 22rpx;
  background: var(--theme-surface);
  border: 1rpx solid var(--theme-border);
  border-radius: 24rpx;
  box-shadow: 0 12rpx 36rpx color-mix(in oklab, var(--theme-primary) 12%, transparent);
  box-sizing: border-box;
}

.guide-bubble-wrap.is-compact .guide-bubble {
  padding: 20rpx 24rpx 18rpx;
}

.guide-bubble-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.guide-bubble-badge {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 16rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: 999rpx;
  background: color-mix(in oklab, var(--theme-primary) 7%, var(--theme-surface));
}

.guide-bubble-badge-text {
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: 2rpx;
  color: var(--theme-primary);
}

.guide-bubble-dismiss {
  padding: 6rpx 4rpx;
  font-size: 22rpx;
  line-height: 1.4;
  color: var(--theme-text-placeholder);
}

.guide-bubble-title {
  display: block;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 1.4;
  color: var(--theme-text-primary);
  margin-bottom: 8rpx;
}

.guide-bubble-desc {
  display: block;
  font-size: 24rpx;
  line-height: 1.6;
  color: var(--theme-text-secondary);
}

.guide-bubble-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16rpx;
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--theme-divider);
}

.guide-btn-secondary {
  padding: 12rpx 24rpx;
  border: 1rpx solid var(--theme-primary);
  border-radius: 16rpx;
  background: transparent;
}

.guide-btn-secondary:active {
  background: color-mix(in oklab, var(--theme-primary) 8%, var(--theme-surface));
}

.guide-btn-secondary-text {
  font-size: 24rpx;
  font-weight: 500;
  line-height: 1.4;
  color: var(--theme-primary);
}

.guide-btn-primary {
  padding: 12rpx 28rpx;
  border: 1rpx solid var(--theme-primary);
  border-radius: 16rpx;
  background: var(--theme-primary);
}

.guide-btn-primary:active {
  opacity: 0.88;
}

.guide-btn-primary-text {
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1.4;
  color: var(--theme-text-on-primary);
}
</style>
