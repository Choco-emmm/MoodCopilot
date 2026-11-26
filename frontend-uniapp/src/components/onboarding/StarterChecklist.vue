<template>
  <view v-if="isChecklistVisible" class="starter-checklist">
    <view class="checklist-header" @click="toggleExpanded">
      <view class="checklist-heading">
        <view class="checklist-title-row">
          <text class="checklist-title">{{ ONBOARDING_COPY.checklistTitle }}</text>
          <text class="checklist-progress-label">
            已完成 {{ progress.completed }} / {{ progress.total }}
          </text>
        </view>
        <view class="progress-track">
          <view
            class="progress-fill"
            :style="{ width: `${progress.percentage}%` }"
          />
        </view>
      </view>
      <view class="checklist-toggle">
        <text class="checklist-toggle-text">{{ expanded ? '收起' : '展开' }}</text>
        <text class="checklist-toggle-arrow">{{ expanded ? '⌃' : '⌄' }}</text>
      </view>
    </view>

    <view v-if="expanded" class="checklist-body">
      <view class="task-list">
        <view
          v-for="task in CORE_ONBOARDING_TASKS"
          :key="task.key"
          class="task-row"
        >
          <view class="task-main">
            <view class="task-title-row">
              <text
                class="task-status-badge"
                :class="`status-${statusOf(task.key).toLowerCase()}`"
              >
                {{ statusLabelOf(task.key) }}
              </text>
              <text
                class="task-title"
                :class="{ 'task-title-done': statusOf(task.key) === 'COMPLETED' }"
              >
                {{ task.title }}
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
        <text class="optional-heading">{{ ONBOARDING_COPY.checklistOptionalHeading }}</text>
        <view
          v-for="task in OPTIONAL_ONBOARDING_TASKS"
          :key="task.key"
          class="task-row optional-row"
        >
          <view class="task-main">
            <view class="task-title-row">
              <text
                class="task-status-badge"
                :class="`status-${statusOf(task.key).toLowerCase()}`"
              >
                {{ statusLabelOf(task.key) }}
              </text>
              <text
                class="task-title"
                :class="{ 'task-title-done': statusOf(task.key) === 'COMPLETED' }"
              >
                {{ task.title }}
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
  margin: 18rpx 0 8rpx;
  padding: 24rpx 24rpx 20rpx;
  background: var(--theme-surface);
  border: 1rpx solid var(--theme-border);
  border-top: 4rpx solid var(--theme-primary);
  border-radius: var(--theme-radius-md);
  box-shadow: var(--theme-shadow-panel);
}

.checklist-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.checklist-heading {
  flex: 1;
  min-width: 0;
}

.checklist-title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14rpx;
  margin-bottom: 12rpx;
}

.checklist-title {
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--theme-text-primary);
}

.checklist-progress-label {
  font-size: 22rpx;
  color: var(--theme-primary);
  font-weight: 600;
  white-space: nowrap;
}

.progress-track {
  width: 100%;
  height: 6rpx;
  border-radius: 6rpx;
  background: var(--theme-border);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 6rpx;
  background: var(--theme-primary);
  transition: width 0.25s ease;
}

.checklist-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 8rpx 4rpx 8rpx 12rpx;
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
  margin-top: 20rpx;
  padding-top: 12rpx;
  border-top: 1rpx solid var(--theme-border);
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
  padding: 18rpx 0;
  border-bottom: 1rpx solid var(--theme-border);
}

.task-row:last-child {
  border-bottom: none;
}

.task-main {
  flex: 1;
  min-width: 0;
}

.task-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-bottom: 6rpx;
}

.task-status-badge {
  padding: 2rpx 10rpx;
  border-radius: var(--theme-radius-sm);
  font-size: 19rpx;
  line-height: 1.5;
  border: 1rpx solid var(--theme-border);
  color: var(--theme-text-secondary);
  background: transparent;
}

.task-status-badge.status-completed {
  border-color: var(--theme-primary);
  color: var(--theme-primary);
  background: color-mix(in oklab, var(--theme-primary) 8%, var(--theme-surface));
}

.task-status-badge.status-skipped {
  color: var(--theme-text-placeholder);
}

.task-title {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--theme-text-primary);
  line-height: 1.45;
}

.task-title-done {
  color: var(--theme-text-secondary);
}

.task-desc {
  display: block;
  font-size: 22rpx;
  line-height: 1.55;
  color: var(--theme-text-secondary);
}

.task-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8rpx;
  flex-shrink: 0;
}

.task-action-btn {
  padding: 8rpx 18rpx;
  border: 1rpx solid var(--theme-primary);
  border-radius: var(--theme-radius-sm);
  background: transparent;
}

.task-action-text {
  font-size: 22rpx;
  font-weight: 600;
  color: var(--theme-primary);
  line-height: 1.2;
}

.task-skip-link {
  font-size: 20rpx;
  color: var(--theme-text-placeholder);
  padding: 4rpx 6rpx;
}

.optional-section {
  margin-top: 12rpx;
  padding-top: 16rpx;
  border-top: 1rpx dashed var(--theme-border);
}

.optional-heading {
  display: block;
  font-size: 21rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  color: var(--theme-text-secondary);
  margin-bottom: 4rpx;
}

.optional-row {
  padding: 14rpx 0 8rpx;
}

.checklist-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 14rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--theme-border);
}

.checklist-footer-note {
  flex: 1;
  font-size: 20rpx;
  line-height: 1.45;
  color: var(--theme-text-placeholder);
}

.checklist-dismiss-link {
  font-size: 22rpx;
  color: var(--theme-text-secondary);
  padding: 6rpx 4rpx;
  flex-shrink: 0;
}
</style>
