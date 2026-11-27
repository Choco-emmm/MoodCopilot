<template>
  <view v-if="isChecklistVisible" class="starter-checklist">
    <view class="checklist-header" @click="toggleExpanded">
      <view class="checklist-heading">
        <text class="checklist-eyebrow">STARTER GUIDE</text>
        <view class="checklist-title-row">
          <text class="checklist-title">{{ ONBOARDING_COPY.checklistTitle }}</text>
          <text class="checklist-progress-label">
            已完成 {{ progress.completed }} / {{ progress.total }}
          </text>
        </view>
      </view>
      <view class="checklist-toggle">
        <text class="checklist-toggle-text">{{ expanded ? '收起' : '展开' }}</text>
        <text class="checklist-toggle-arrow">{{ expanded ? '⌃' : '⌄' }}</text>
      </view>
    </view>

    <view class="progress-track">
      <view
        class="progress-fill"
        :style="{ width: `${progress.percentage}%` }"
      />
    </view>

    <view v-if="expanded" class="checklist-body">
      <view class="task-list">
        <view
          v-for="(task, index) in CORE_ONBOARDING_TASKS"
          :key="task.key"
          class="task-row"
          :class="`is-${statusOf(task.key).toLowerCase()}`"
        >
          <view class="task-index">
            <text class="task-index-text">
              {{ statusOf(task.key) === 'COMPLETED' ? '✓' : statusOf(task.key) === 'SKIPPED' ? '—' : `0${index + 1}` }}
            </text>
          </view>

          <view class="task-main">
            <view class="task-title-row">
              <text
                class="task-title"
                :class="{ 'task-title-done': statusOf(task.key) === 'COMPLETED' || statusOf(task.key) === 'SKIPPED' }"
              >
                {{ task.title }}
              </text>
              <text
                class="task-status-badge"
                :class="`status-${statusOf(task.key).toLowerCase()}`"
              >
                {{ statusLabelOf(task.key) }}
              </text>
            </view>
            <text class="task-desc">{{ task.description }}</text>
          </view>

          <view
            v-if="statusOf(task.key) !== 'COMPLETED' && statusOf(task.key) !== 'SKIPPED'"
            class="task-actions"
          >
            <view
              class="task-action-btn"
              role="button"
              @click="handleTaskAction(task.key)"
            >
              <text class="task-action-text">{{ task.actionLabel }}</text>
            </view>
            <text
              v-if="task.skippable"
              class="task-skip-link"
              @click="handleSkipTask(task.key)"
            >
              跳过
            </text>
          </view>
        </view>
      </view>

      <view v-if="OPTIONAL_ONBOARDING_TASKS.length" class="optional-section">
        <view class="optional-header">
          <text class="optional-heading">{{ ONBOARDING_COPY.checklistOptionalHeading }}</text>
          <text class="optional-note">不计入核心进度</text>
        </view>
        <view
          v-for="task in OPTIONAL_ONBOARDING_TASKS"
          :key="task.key"
          class="task-row optional-row"
          :class="`is-${statusOf(task.key).toLowerCase()}`"
        >
          <view class="task-index">
            <text class="task-index-text">
              {{ statusOf(task.key) === 'COMPLETED' ? '✓' : statusOf(task.key) === 'SKIPPED' ? '—' : '·' }}
            </text>
          </view>

          <view class="task-main">
            <view class="task-title-row">
              <text
                class="task-title"
                :class="{ 'task-title-done': statusOf(task.key) === 'COMPLETED' || statusOf(task.key) === 'SKIPPED' }"
              >
                {{ task.title }}
              </text>
              <text
                class="task-status-badge"
                :class="`status-${statusOf(task.key).toLowerCase()}`"
              >
                {{ statusLabelOf(task.key) }}
              </text>
            </view>
            <text class="task-desc">{{ task.description }}</text>
          </view>

          <view
            v-if="statusOf(task.key) !== 'COMPLETED' && statusOf(task.key) !== 'SKIPPED'"
            class="task-actions"
          >
            <view
              class="task-action-btn"
              role="button"
              @click="handleTaskAction(task.key)"
            >
              <text class="task-action-text">{{ task.actionLabel }}</text>
            </view>
            <text
              v-if="task.skippable"
              class="task-skip-link"
              @click="handleSkipTask(task.key)"
            >
              跳过
            </text>
          </view>
        </view>
      </view>

      <view class="checklist-footer">
        <text class="checklist-footer-note">所有功能均可自由使用，也可随时收起这份清单。</text>
        <text class="checklist-dismiss-link" @click="handleDismissChecklist">
          {{ ONBOARDING_COPY.checklistDismissLabel }}
        </text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  CORE_ONBOARDING_TASKS,
  ONBOARDING_COPY,
  OPTIONAL_ONBOARDING_TASKS,
} from '@/constants/onboarding';
import type { GuideStatus, TaskKey } from '@/constants/onboarding';
import {
  dismissChecklist,
  dismissTask,
  executeChecklistTaskAction,
  isOnboardingChecklistActive,
  onboardingLoaded,
  onboardingProgress,
  onboardingState,
} from '@/stores/onboarding';

const props = defineProps<{
  latestDiaryId?: number | null;
}>();

const expanded = ref(true);

const progress = computed(() => onboardingProgress.value);
const isChecklistVisible = computed(
  () => onboardingLoaded.value && isOnboardingChecklistActive.value,
);

watch(
  () => progress.value.allCompleted,
  (done) => {
    if (done) {
      expanded.value = false;
    }
  },
);

function statusOf(taskKey: TaskKey): GuideStatus {
  return onboardingState.value.tasks[taskKey] || 'NOT_STARTED';
}

function statusLabelOf(taskKey: TaskKey): string {
  const status = statusOf(taskKey);
  if (status === 'COMPLETED') return '已完成';
  if (status === 'SKIPPED') return '已跳过';
  if (status === 'IN_PROGRESS') return '进行中';
  return '待完成';
}

function toggleExpanded() {
  expanded.value = !expanded.value;
}

function handleTaskAction(taskKey: TaskKey) {
  executeChecklistTaskAction(taskKey, { latestDiaryId: props.latestDiaryId });
}

function handleSkipTask(taskKey: TaskKey) {
  void dismissTask(taskKey);
}

function handleDismissChecklist() {
  void dismissChecklist();
}
</script>

<style scoped>
.starter-checklist {
  margin: 20rpx 0 12rpx;
  padding: 28rpx 28rpx 24rpx;
  background: var(--theme-surface);
  border: 1rpx solid var(--theme-border);
  border-radius: 24rpx;
  box-shadow: var(--theme-shadow-panel);
}

.checklist-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
}

.checklist-heading {
  flex: 1;
  min-width: 0;
}

.checklist-eyebrow {
  display: block;
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 3rpx;
  line-height: 1.4;
  color: var(--theme-accent);
  margin-bottom: 6rpx;
}

.checklist-title-row {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 16rpx;
}

.checklist-title {
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 32rpx;
  font-weight: 600;
  line-height: 1.4;
  color: var(--theme-text-primary);
}

.checklist-progress-label {
  font-size: 22rpx;
  color: var(--theme-text-secondary);
  font-weight: 500;
  white-space: nowrap;
}

.progress-track {
  margin-top: 18rpx;
  width: 100%;
  height: 6rpx;
  border-radius: 999rpx;
  background: color-mix(in oklab, var(--theme-primary) 10%, var(--theme-bg));
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 999rpx;
  background: var(--theme-primary);
  transition: width 0.25s ease;
}

.checklist-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 6rpx 16rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: 12rpx;
  color: var(--theme-text-secondary);
  font-size: 22rpx;
  flex-shrink: 0;
}

.checklist-toggle-text {
  color: var(--theme-text-secondary);
}

.checklist-toggle-arrow {
  color: var(--theme-text-placeholder);
  font-size: 24rpx;
  line-height: 1;
}

.checklist-body {
  margin-top: 22rpx;
  padding-top: 8rpx;
  border-top: 1rpx solid var(--theme-divider);
}

.task-list {
  display: flex;
  flex-direction: column;
}

.task-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid var(--theme-divider);
}

.task-row:last-child {
  border-bottom: none;
}

.task-index {
  width: 44rpx;
  height: 44rpx;
  margin-top: 2rpx;
  border-radius: 999rpx;
  border: 1rpx solid var(--theme-border);
  background: color-mix(in oklab, var(--theme-primary) 6%, var(--theme-surface));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.task-index-text {
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 22rpx;
  font-weight: 600;
  color: var(--theme-primary);
  line-height: 1;
}

.task-row.is-completed .task-index {
  background: var(--theme-primary);
  border-color: var(--theme-primary);
}

.task-row.is-completed .task-index-text {
  color: var(--theme-text-on-primary);
}

.task-row.is-skipped .task-index {
  background: transparent;
}

.task-row.is-skipped .task-index-text {
  color: var(--theme-text-placeholder);
}

.task-main {
  flex: 1;
  min-width: 0;
}

.task-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 6rpx;
}

.task-status-badge {
  padding: 2rpx 14rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  line-height: 1.5;
  border: 1rpx solid var(--theme-border);
  color: var(--theme-text-secondary);
  background: color-mix(in oklab, var(--theme-bg) 70%, var(--theme-surface));
}

.task-status-badge.status-completed {
  border-color: color-mix(in oklab, var(--theme-primary) 35%, transparent);
  color: var(--theme-primary);
  background: color-mix(in oklab, var(--theme-primary) 9%, var(--theme-surface));
}

.task-status-badge.status-skipped {
  color: var(--theme-text-placeholder);
}

.task-title {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--theme-text-primary);
  line-height: 1.4;
}

.task-title-done {
  color: var(--theme-text-placeholder);
}

.task-desc {
  display: block;
  font-size: 24rpx;
  line-height: 1.6;
  color: var(--theme-text-secondary);
}

.task-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10rpx;
  flex-shrink: 0;
}

.task-action-btn {
  padding: 10rpx 22rpx;
  border: 1rpx solid var(--theme-primary);
  border-radius: 14rpx;
  background: transparent;
}

.task-action-btn:active {
  background: color-mix(in oklab, var(--theme-primary) 10%, var(--theme-surface));
}

.task-action-text {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--theme-primary);
  line-height: 1.3;
}

.task-skip-link {
  font-size: 22rpx;
  color: var(--theme-text-placeholder);
  padding: 4rpx 6rpx;
}

.optional-section {
  margin-top: 12rpx;
  padding-top: 20rpx;
  border-top: 1rpx dashed var(--theme-border);
}

.optional-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12rpx;
  margin-bottom: 6rpx;
}

.optional-heading {
  font-size: 24rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  color: var(--theme-text-secondary);
}

.optional-note {
  font-size: 22rpx;
  color: var(--theme-text-placeholder);
}

.optional-row {
  padding: 16rpx 0 8rpx;
}

.checklist-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 16rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid var(--theme-divider);
}

.checklist-footer-note {
  flex: 1;
  font-size: 22rpx;
  line-height: 1.5;
  color: var(--theme-text-placeholder);
}

.checklist-dismiss-link {
  font-size: 24rpx;
  color: var(--theme-text-secondary);
  padding: 6rpx 4rpx;
  flex-shrink: 0;
}
</style>
