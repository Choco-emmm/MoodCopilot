<template>
  <view
    v-if="visible"
    class="welcome-mask"
    :style="themeStyle"
    @click="handleSkip"
  >
    <view
      class="welcome-sheet"
      :style="sheetDynamicStyle"
      @click.stop
    >
      <view class="sheet-handle" />

      <view class="welcome-masthead">
        <view class="masthead-left">
          <text class="masthead-badge">
            {{ replayMode ? ONBOARDING_COPY.welcomeReplayBadge : ONBOARDING_COPY.welcomeBadge }}
          </text>
          <text class="masthead-step">{{ stepCounterLabel }}</text>
        </view>
        <view
          class="masthead-skip"
          role="button"
          @click="handleSkip"
        >
          <text class="masthead-skip-text">
            {{ replayMode ? ONBOARDING_COPY.welcomeReplayCloseLabel : ONBOARDING_COPY.welcomeSkipLabel }}
          </text>
        </view>
      </view>

      <view class="progress-rail">
        <view
          v-for="idx in totalSteps"
          :key="idx"
          class="progress-segment"
          :class="{ active: idx - 1 <= currentStep }"
        />
      </view>

      <view v-if="replayMode" class="replay-banner">
        <text class="replay-banner-text">{{ ONBOARDING_COPY.welcomeReplayNote }}</text>
      </view>

      <scroll-view scroll-y class="welcome-scroll">
        <view v-if="currentSlide" class="slide-body">
          <text class="slide-eyebrow">{{ currentSlide.stepNumber }} · {{ currentSlide.eyebrow }}</text>
          <text class="slide-title">{{ currentSlide.title }}</text>
          <text class="slide-description">{{ currentSlide.description }}</text>

          <view class="slide-divider" />

          <view class="highlight-list">
            <view
              v-for="(item, index) in currentSlide.highlights"
              :key="index"
              class="highlight-row"
            >
              <view class="highlight-mark" />
              <text class="highlight-text">{{ item }}</text>
            </view>
          </view>
        </view>

        <view v-else class="actions-body">
          <text class="slide-eyebrow">START · 开始探索</text>
          <text class="slide-title">{{ ONBOARDING_COPY.welcomeActionsHeading }}</text>
          <text class="slide-description">{{ ONBOARDING_COPY.welcomeActionsSubheading }}</text>

          <view class="action-list">
            <view
              v-for="action in WELCOME_ACTIONS"
              :key="action.key"
              class="action-item"
              :class="`action-${action.emphasis}`"
              role="button"
              @click="handleSelectAction(action.key)"
            >
              <view class="action-copy">
                <text class="action-label">{{ action.label }}</text>
                <text class="action-desc">{{ action.description }}</text>
              </view>
              <text class="action-arrow">›</text>
            </view>
          </view>
        </view>
      </scroll-view>

      <view class="welcome-footer">
        <template v-if="currentSlide">
          <button
            v-if="currentStep > 0"
            class="footer-btn footer-secondary"
            @click="goPrev"
          >
            {{ ONBOARDING_COPY.welcomePrevLabel }}
          </button>
          <button
            class="footer-btn footer-primary"
            @click="goNext"
          >
            {{ isLastSlide ? ONBOARDING_COPY.welcomeToActionsLabel : ONBOARDING_COPY.welcomeNextLabel }}
          </button>
        </template>
        <template v-else>
          <button
            class="footer-btn footer-secondary"
            @click="goPrev"
          >
            {{ ONBOARDING_COPY.welcomePrevLabel }}
          </button>
        </template>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import {
  ONBOARDING_COPY,
  WELCOME_ACTIONS,
  WELCOME_SLIDES,
} from '@/constants/onboarding';
import type { WelcomeActionKey } from '@/constants/onboarding';
import { themeStyle } from '@/stores/theme';

const props = defineProps<{
  visible: boolean;
  replayMode: boolean;
}>();

const emit = defineEmits<{
  (event: 'skip'): void;
  (event: 'select-action', actionKey: WelcomeActionKey): void;
}>();

const currentStep = ref(0);
const keyboardHeight = ref(0);

const totalSteps = WELCOME_SLIDES.length + 1;
const isLastSlide = computed(() => currentStep.value === WELCOME_SLIDES.length - 1);
const currentSlide = computed(() =>
  currentStep.value < WELCOME_SLIDES.length ? WELCOME_SLIDES[currentStep.value] : null,
);

const stepCounterLabel = computed(() => {
  if (currentStep.value < WELCOME_SLIDES.length) {
    return `${String(currentStep.value + 1).padStart(2, '0')} / ${String(WELCOME_SLIDES.length).padStart(2, '0')}`;
  }
  return '选择起点';
});

const sheetDynamicStyle = computed(() => {
  if (keyboardHeight.value <= 0) {
    return {};
  }
  return {
    paddingBottom: `${keyboardHeight.value + 20}px`,
  };
});

watch(
  () => props.visible,
  (nextVisible) => {
    if (nextVisible) {
      currentStep.value = 0;
    }
  },
  { immediate: true },
);

const handleKeyboardHeightChange = (res: { height?: number }) => {
  keyboardHeight.value = typeof res?.height === 'number' && res.height > 0 ? res.height : 0;
};

onMounted(() => {
  if (typeof uni !== 'undefined' && typeof uni.onKeyboardHeightChange === 'function') {
    uni.onKeyboardHeightChange(handleKeyboardHeightChange);
  }
});

onUnmounted(() => {
  if (typeof uni !== 'undefined' && typeof uni.offKeyboardHeightChange === 'function') {
    uni.offKeyboardHeightChange(handleKeyboardHeightChange);
  }
});

const goPrev = () => {
  if (currentStep.value > 0) {
    currentStep.value -= 1;
  }
};

const goNext = () => {
  if (currentStep.value < WELCOME_SLIDES.length) {
    currentStep.value += 1;
  }
};

const handleSkip = () => {
  emit('skip');
};

const handleSelectAction = (actionKey: WelcomeActionKey) => {
  emit('select-action', actionKey);
};
</script>

<style scoped>
.welcome-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 9500;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  background: var(--theme-overlay);
}

.welcome-sheet {
  width: 100%;
  max-height: 86vh;
  padding: 20rpx 40rpx calc(36rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  background: var(--theme-surface);
  border-top: 6rpx solid var(--theme-primary);
  border-radius: var(--theme-radius-lg) var(--theme-radius-lg) 0 0;
  box-shadow: var(--theme-shadow-dialog);
  display: flex;
  flex-direction: column;
}

.sheet-handle {
  width: 64rpx;
  height: 6rpx;
  margin: 0 auto 24rpx;
  border-radius: 6rpx;
  background: var(--theme-border);
}

.welcome-masthead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding-bottom: 18rpx;
}

.masthead-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.masthead-badge {
  padding: 4rpx 16rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: 999rpx;
  background: color-mix(in oklab, var(--theme-primary) 7%, var(--theme-surface));
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
  color: var(--theme-primary);
}

.masthead-step {
  font-size: 22rpx;
  color: var(--theme-text-placeholder);
  letter-spacing: 1rpx;
}

.masthead-skip {
  padding: 8rpx 4rpx 8rpx 20rpx;
}

.masthead-skip-text {
  font-size: 26rpx;
  color: var(--theme-text-secondary);
}

.progress-rail {
  display: flex;
  gap: 12rpx;
  margin-bottom: 24rpx;
}

.progress-segment {
  flex: 1;
  height: 4rpx;
  border-radius: 999rpx;
  background: var(--theme-border);
}

.progress-segment.active {
  background: var(--theme-primary);
}

.replay-banner {
  margin-bottom: 22rpx;
  padding: 16rpx 20rpx;
  background: color-mix(in oklab, var(--theme-primary) 6%, var(--theme-surface));
  border: 1rpx solid var(--theme-border);
  border-radius: 16rpx;
}

.replay-banner-text {
  display: block;
  font-size: 24rpx;
  line-height: 1.5;
  color: var(--theme-text-secondary);
}

.welcome-scroll {
  max-height: 54vh;
}

.slide-body,
.actions-body {
  padding: 6rpx 0 16rpx;
}

.slide-eyebrow {
  display: block;
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
  color: var(--theme-primary);
  margin-bottom: 14rpx;
}

.slide-title {
  display: block;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 42rpx;
  font-weight: 700;
  line-height: 1.35;
  color: var(--theme-text-primary);
  margin-bottom: 16rpx;
}

.slide-description {
  display: block;
  font-size: 28rpx;
  line-height: 1.7;
  color: var(--theme-text-secondary);
}

.slide-divider {
  height: 1rpx;
  margin: 28rpx 0 22rpx;
  background: var(--theme-border);
}

.highlight-list {
  display: flex;
  flex-direction: column;
}

.highlight-row {
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--theme-border);
}

.highlight-row:last-child {
  border-bottom: none;
}

.highlight-mark {
  width: 10rpx;
  height: 10rpx;
  margin-top: 15rpx;
  flex-shrink: 0;
  border-radius: 2rpx;
  background: var(--theme-primary);
}

.highlight-text {
  flex: 1;
  font-size: 26rpx;
  line-height: 1.65;
  color: var(--theme-text-primary);
}

.action-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  margin-top: 28rpx;
}

.action-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 24rpx 28rpx;
  border-radius: 20rpx;
  box-sizing: border-box;
}

.action-primary {
  background: var(--theme-primary);
  border: 1rpx solid var(--theme-primary);
}

.action-primary .action-label,
.action-primary .action-arrow {
  color: var(--theme-text-on-primary);
}

.action-primary .action-desc {
  color: color-mix(in oklab, var(--theme-text-on-primary) 82%, transparent);
}

.action-secondary {
  background: var(--theme-surface);
  border: 1rpx solid var(--theme-border);
}

.action-secondary .action-label,
.action-secondary .action-arrow {
  color: var(--theme-text-primary);
}

.action-secondary .action-desc {
  color: var(--theme-text-secondary);
}

.action-ghost {
  background: transparent;
  border: 1rpx solid transparent;
  border-top: 1rpx solid var(--theme-border);
  border-radius: 0;
  padding: 22rpx 8rpx 12rpx;
}

.action-ghost .action-label {
  color: var(--theme-text-secondary);
}

.action-ghost .action-desc,
.action-ghost .action-arrow {
  color: var(--theme-text-placeholder);
}

.action-copy {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.action-label {
  font-size: 30rpx;
  font-weight: 600;
  line-height: 1.35;
}

.action-desc {
  font-size: 24rpx;
  line-height: 1.5;
}

.action-arrow {
  font-size: 36rpx;
  line-height: 1;
}

.welcome-footer {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-top: 26rpx;
  padding-top: 22rpx;
  border-top: 1rpx solid var(--theme-border);
}

.footer-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  margin: 0;
  padding: 0 24rpx;
  border-radius: 16rpx;
  font-size: 26rpx;
  font-weight: 600;
}

.footer-btn::after {
  border: none;
}

.footer-primary {
  background: var(--theme-primary);
  color: var(--theme-text-on-primary);
}

.footer-secondary {
  background: transparent;
  color: var(--theme-text-primary);
  border: 1rpx solid var(--theme-border);
}
</style>
