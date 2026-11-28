<template>
  <view
    v-if="visible"
    class="tour-layer"
    :style="themeStyle"
    @touchmove.stop.prevent
  >
    <!-- 四向暗色遮罩，在真实按钮/导航栏项周围抠出明亮聚光灯窗 -->
    <template v-if="spotlightHole">
      <view class="tour-dim-panel" :style="dimTopStyle" />
      <view class="tour-dim-panel" :style="dimBottomStyle" />
      <view class="tour-dim-panel" :style="dimLeftStyle" />
      <view class="tour-dim-panel" :style="dimRightStyle" />
      <view class="tour-spotlight-ring" :style="ringStyle" />
    </template>
    <view v-else class="tour-dim-full" />

    <!-- 紧贴高亮控件的杂志风编辑部气泡卡 -->
    <view
      class="tour-card"
      :class="{
        'is-centered': !spotlightHole,
        'is-below': spotlightHole && cardPlacement === 'below',
        'is-above': spotlightHole && cardPlacement === 'above',
      }"
      :style="cardStyle"
      @click.stop
    >
      <view
        v-if="spotlightHole"
        class="tour-arrow"
        :style="{ left: `${arrowLeftPx}px` }"
      />

      <view class="tour-header">
        <view class="tour-meta">
          <text class="tour-badge">
            {{ replayMode ? ONBOARDING_COPY.welcomeReplayBadge : ONBOARDING_COPY.welcomeBadge }}
          </text>
          <text class="tour-counter">{{ stepCounterLabel }}</text>
        </view>
        <view class="tour-skip" role="button" @click="handleSkip">
          <text class="tour-skip-text">
            {{ replayMode ? ONBOARDING_COPY.welcomeReplayCloseLabel : ONBOARDING_COPY.welcomeSkipLabel }}
          </text>
        </view>
      </view>

      <view class="tour-progress">
        <view
          v-for="(step, idx) in steps"
          :key="step.id"
          class="tour-progress-bar"
          :class="{ active: idx === currentStepIndex, passed: idx < currentStepIndex }"
          role="button"
          @click="goToStep(idx)"
        />
      </view>

      <view class="tour-body">
        <text class="tour-title">{{ currentStep.title }}</text>
        <text class="tour-desc">{{ currentStep.description }}</text>
      </view>

      <view class="tour-footer">
        <template v-if="!isLastStep">
          <view class="footer-row">
            <button
              v-if="currentStepIndex > 0"
              class="footer-btn footer-secondary"
              @click="goPrev"
            >
              {{ ONBOARDING_COPY.welcomePrevLabel }}
            </button>
            <button
              class="footer-btn footer-primary"
              @click="goNext"
            >
              {{ ONBOARDING_COPY.welcomeNextLabel }}
            </button>
          </view>
        </template>

        <template v-else>
          <view class="footer-row">
            <button
              class="footer-btn footer-secondary"
              @click="handleSelectAction('CHAT_WITH_AI')"
            >
              找 AI 聊聊
            </button>
            <button
              class="footer-btn footer-primary"
              @click="handleSelectAction('WRITE_TODAY')"
            >
              记录今天
            </button>
          </view>
          <view class="footer-links">
            <text class="footer-link" role="button" @click="goPrev">
              ← {{ ONBOARDING_COPY.welcomePrevLabel }}
            </text>
            <text class="footer-link" role="button" @click="handleSelectAction('EXPLORE_LATER')">
              稍后探索
            </text>
          </view>
        </template>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  MINI_PROGRAM_WELCOME_TOUR_STEPS,
  ONBOARDING_COPY,
} from '@/constants/onboarding';
import type { WelcomeActionKey } from '@/constants/onboarding';
import { spotlightAnchorRects } from '@/stores/onboarding';
import type { SpotlightAnchorRect } from '@/stores/onboarding';
import { themeStyle } from '@/stores/theme';

const SPOTLIGHT_PADDING = 5;
const CARD_HORIZONTAL_MARGIN = 16;
const CARD_GAP = 14;

const props = defineProps<{
  visible: boolean;
  replayMode: boolean;
}>();

const emit = defineEmits<{
  (event: 'skip'): void;
  (event: 'select-action', actionKey: WelcomeActionKey): void;
}>();

const steps = MINI_PROGRAM_WELCOME_TOUR_STEPS;
const currentStepIndex = ref(0);

const currentStep = computed(() => steps[currentStepIndex.value] || steps[0]);
const isLastStep = computed(() => currentStepIndex.value >= steps.length - 1);

const stepCounterLabel = computed(
  () => `${String(currentStepIndex.value + 1).padStart(2, '0')} / ${String(steps.length).padStart(2, '0')}`,
);

function getViewportMetrics(): { width: number; height: number; safeBottom: number } {
  try {
    const info =
      typeof uni !== 'undefined' && typeof (uni as any).getWindowInfo === 'function'
        ? (uni as any).getWindowInfo()
        : typeof uni !== 'undefined' && typeof uni.getSystemInfoSync === 'function'
          ? uni.getSystemInfoSync()
          : null;
    const width = Number(info?.windowWidth) > 0 ? Number(info.windowWidth) : 375;
    const height = Number(info?.windowHeight) > 0 ? Number(info.windowHeight) : 667;
    const safeAreaBottom = Number(info?.safeArea?.bottom);
    const safeBottom =
      Number.isFinite(safeAreaBottom) && safeAreaBottom > 0 && safeAreaBottom <= height
        ? Math.max(0, height - safeAreaBottom)
        : 0;
    return { width, height, safeBottom };
  } catch {
    return { width: 375, height: 667, safeBottom: 0 };
  }
}

function getFallbackTabRect(anchorId: string): SpotlightAnchorRect | null {
  const tabIndexMap: Record<string, number> = {
    'mp-nav-diary': 0,
    'mp-nav-analysis': 1,
    'mp-nav-chat': 2,
    'mp-nav-profile': 3,
  };
  const tabIdx = tabIndexMap[anchorId];
  if (tabIdx === undefined) return null;
  const { width, height, safeBottom } = getViewportMetrics();
  const tabHeight = Math.round((96 * width) / 750);
  const itemWidth = width / 4;
  return {
    left: Math.round(tabIdx * itemWidth) + 6,
    top: Math.max(0, height - safeBottom - tabHeight + 2),
    width: Math.max(40, Math.round(itemWidth) - 12),
    height: Math.max(38, tabHeight - 4),
  };
}

const spotlightHole = computed<SpotlightAnchorRect | null>(() => {
  const anchorId = currentStep.value.anchorId;
  const measured = spotlightAnchorRects.value[anchorId] || getFallbackTabRect(anchorId);
  if (!measured) return null;
  const { width: vw, height: vh } = getViewportMetrics();
  const left = Math.max(2, measured.left - SPOTLIGHT_PADDING);
  const top = Math.max(2, measured.top - SPOTLIGHT_PADDING);
  const right = Math.min(vw - 2, measured.left + measured.width + SPOTLIGHT_PADDING);
  const bottom = Math.min(vh - 2, measured.top + measured.height + SPOTLIGHT_PADDING);
  return {
    left,
    top,
    width: Math.max(24, right - left),
    height: Math.max(24, bottom - top),
  };
});

const cardPlacement = computed<'below' | 'above'>(() => {
  if (!spotlightHole.value) return 'below';
  const { height: vh } = getViewportMetrics();
  const centerY = spotlightHole.value.top + spotlightHole.value.height / 2;
  return centerY < vh * 0.48 ? 'below' : 'above';
});

const arrowLeftPx = computed(() => {
  if (!spotlightHole.value) return 40;
  const { width: vw } = getViewportMetrics();
  const cardWidth = Math.max(240, vw - CARD_HORIZONTAL_MARGIN * 2);
  const anchorCenterX = spotlightHole.value.left + spotlightHole.value.width / 2;
  return Math.max(24, Math.min(cardWidth - 24, anchorCenterX - CARD_HORIZONTAL_MARGIN));
});

const dimTopStyle = computed(() => {
  if (!spotlightHole.value) return {};
  return {
    top: '0px',
    left: '0px',
    right: '0px',
    height: `${spotlightHole.value.top}px`,
  };
});

const dimBottomStyle = computed(() => {
  if (!spotlightHole.value) return {};
  const top = spotlightHole.value.top + spotlightHole.value.height;
  return {
    top: `${top}px`,
    left: '0px',
    right: '0px',
    bottom: '0px',
  };
});

const dimLeftStyle = computed(() => {
  if (!spotlightHole.value) return {};
  return {
    top: `${spotlightHole.value.top}px`,
    left: '0px',
    width: `${spotlightHole.value.left}px`,
    height: `${spotlightHole.value.height}px`,
  };
});

const dimRightStyle = computed(() => {
  if (!spotlightHole.value) return {};
  const left = spotlightHole.value.left + spotlightHole.value.width;
  return {
    top: `${spotlightHole.value.top}px`,
    left: `${left}px`,
    right: '0px',
    height: `${spotlightHole.value.height}px`,
  };
});

const ringStyle = computed(() => {
  if (!spotlightHole.value) return {};
  return {
    top: `${spotlightHole.value.top}px`,
    left: `${spotlightHole.value.left}px`,
    width: `${spotlightHole.value.width}px`,
    height: `${spotlightHole.value.height}px`,
  };
});

const cardStyle = computed(() => {
  if (!spotlightHole.value) return {};
  const { height: vh } = getViewportMetrics();
  if (cardPlacement.value === 'below') {
    const top = spotlightHole.value.top + spotlightHole.value.height + CARD_GAP;
    return {
      left: `${CARD_HORIZONTAL_MARGIN}px`,
      right: `${CARD_HORIZONTAL_MARGIN}px`,
      top: `${top}px`,
    };
  }
  const bottom = Math.max(16, vh - spotlightHole.value.top + CARD_GAP);
  return {
    left: `${CARD_HORIZONTAL_MARGIN}px`,
    right: `${CARD_HORIZONTAL_MARGIN}px`,
    bottom: `${bottom}px`,
  };
});

watch(
  () => props.visible,
  (nextVisible) => {
    if (nextVisible) {
      currentStepIndex.value = 0;
    }
  },
  { immediate: true },
);

const goToStep = (index: number) => {
  if (index < 0 || index >= steps.length) return;
  currentStepIndex.value = index;
};

const goPrev = () => {
  if (currentStepIndex.value > 0) {
    currentStepIndex.value -= 1;
  }
};

const goNext = () => {
  if (currentStepIndex.value < steps.length - 1) {
    currentStepIndex.value += 1;
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
.tour-layer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 9500;
}

.tour-dim-panel,
.tour-dim-full {
  position: fixed;
  background: var(--theme-overlay);
}

.tour-dim-full {
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}

.tour-spotlight-ring {
  position: fixed;
  border-radius: 20rpx;
  border: 4rpx solid var(--theme-primary);
  box-shadow: 0 0 0 8rpx color-mix(in oklab, var(--theme-primary) 26%, transparent);
  pointer-events: none;
  box-sizing: border-box;
}

.tour-card {
  position: fixed;
  padding: 28rpx 30rpx 26rpx;
  background: var(--theme-surface);
  border: 1rpx solid var(--theme-border);
  border-top: 6rpx solid var(--theme-primary);
  border-radius: 24rpx;
  box-shadow: var(--theme-shadow-dialog);
  box-sizing: border-box;
}

.tour-card.is-centered {
  top: 50%;
  left: 32rpx;
  right: 32rpx;
  transform: translateY(-50%);
}

.tour-arrow {
  position: absolute;
  width: 20rpx;
  height: 20rpx;
  background: var(--theme-surface);
  transform: translateX(-50%) rotate(45deg);
}

.tour-card.is-below .tour-arrow {
  top: -14rpx;
  border-left: 4rpx solid var(--theme-primary);
  border-top: 4rpx solid var(--theme-primary);
}

.tour-card.is-above .tour-arrow {
  bottom: -11rpx;
  border-right: 1rpx solid var(--theme-border);
  border-bottom: 1rpx solid var(--theme-border);
}

.tour-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 14rpx;
}

.tour-meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.tour-badge {
  padding: 4rpx 14rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: 999rpx;
  background: color-mix(in oklab, var(--theme-primary) 7%, var(--theme-surface));
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  color: var(--theme-primary);
}

.tour-counter {
  font-size: 22rpx;
  color: var(--theme-text-placeholder);
}

.tour-skip {
  padding: 6rpx 4rpx 6rpx 16rpx;
}

.tour-skip-text {
  font-size: 24rpx;
  color: var(--theme-text-secondary);
}

.tour-progress {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10rpx;
  margin-bottom: 20rpx;
}

.tour-progress-bar {
  height: 6rpx;
  border-radius: 999rpx;
  background: var(--theme-border);
}

.tour-progress-bar.passed {
  background: color-mix(in oklab, var(--theme-primary) 48%, transparent);
}

.tour-progress-bar.active {
  background: var(--theme-primary);
}

.tour-body {
  margin-bottom: 24rpx;
}

.tour-title {
  display: block;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 34rpx;
  font-weight: 700;
  line-height: 1.35;
  color: var(--theme-text-primary);
  margin-bottom: 10rpx;
}

.tour-desc {
  display: block;
  font-size: 26rpx;
  line-height: 1.7;
  color: var(--theme-text-secondary);
}

.tour-footer {
  padding-top: 20rpx;
  border-top: 1rpx solid var(--theme-border);
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.footer-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.footer-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  margin: 0;
  padding: 0 20rpx;
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

.footer-links {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 6rpx;
}

.footer-link {
  font-size: 24rpx;
  color: var(--theme-text-secondary);
}
</style>
