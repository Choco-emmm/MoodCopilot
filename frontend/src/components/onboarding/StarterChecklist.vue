<template>
  <section
    v-if="onboarding.checklistActive"
    class="starter-checklist"
    aria-labelledby="starter-checklist-title"
    data-testid="starter-checklist"
  >
    <header class="checklist-header">
      <div class="checklist-heading">
        <span class="checklist-eyebrow">STARTER GUIDE</span>
        <div class="checklist-title-row">
          <h2 id="starter-checklist-title" class="checklist-title">
            {{ ONBOARDING_COPY.checklistTitle }}
          </h2>
          <span class="checklist-progress-label" data-testid="starter-checklist-progress">
            已完成 {{ onboarding.coreProgress.completed }} / {{ onboarding.coreProgress.total }}
          </span>
        </div>
      </div>

      <div class="checklist-header-actions">
        <button
          type="button"
          class="checklist-control-btn"
          :aria-expanded="!collapsed"
          aria-controls="starter-checklist-body"
          data-testid="starter-checklist-toggle"
          @click="collapsed = !collapsed"
        >
          {{ collapsed ? '展开' : '折叠' }}
        </button>
        <button
          type="button"
          class="checklist-control-btn checklist-dismiss-btn"
          data-testid="starter-checklist-dismiss"
          @click="handleDismissChecklist"
        >
          {{ ONBOARDING_COPY.checklistDismissLabel }}
        </button>
      </div>
    </header>

    <div
      class="checklist-progress-track"
      role="progressbar"
      :aria-valuenow="onboarding.coreProgress.completed"
      aria-valuemin="0"
      :aria-valuemax="onboarding.coreProgress.total"
      :aria-label="`核心上手进度 ${onboarding.coreProgress.completed} / ${onboarding.coreProgress.total}`"
    >
      <div
        class="checklist-progress-fill"
        :style="{ width: `${onboarding.coreProgress.percentage}%` }"
      />
    </div>

    <div v-show="!collapsed" id="starter-checklist-body" class="checklist-body">
      <ul class="checklist-list">
        <li
          v-for="(task, index) in CORE_ONBOARDING_TASKS"
          :key="task.key"
          :class="['checklist-item', `is-${taskStatus(task.key).toLowerCase()}`]"
          :data-testid="`starter-task-${task.key}`"
        >
          <span class="checklist-index" aria-hidden="true">
            {{ taskStatus(task.key) === 'COMPLETED' ? '✓' : taskStatus(task.key) === 'SKIPPED' ? '—' : `0${index + 1}` }}
          </span>

          <div class="checklist-item-main">
            <div class="checklist-item-title-row">
              <span class="checklist-item-title">{{ task.title }}</span>
              <span class="checklist-status-tag">{{ statusLabel(taskStatus(task.key)) }}</span>
            </div>
            <p class="checklist-item-desc">{{ task.description }}</p>
          </div>

          <div v-if="taskStatus(task.key) !== 'COMPLETED'" class="checklist-item-actions">
            <button
              type="button"
              class="checklist-action-btn"
              :data-testid="`starter-action-${task.key}`"
              @click="goToTask(task.key)"
            >
              {{ task.actionLabel }}
            </button>
            <button
              v-if="task.skippable && taskStatus(task.key) !== 'SKIPPED'"
              type="button"
              class="checklist-skip-btn"
              :data-testid="`starter-skip-${task.key}`"
              @click="handleSkipTask(task.key)"
            >
              跳过
            </button>
          </div>
        </li>
      </ul>

      <div v-if="OPTIONAL_ONBOARDING_TASKS.length" class="checklist-optional">
        <div class="checklist-optional-header">
          <span class="checklist-optional-title">{{ ONBOARDING_COPY.checklistOptionalHeading }}</span>
          <span class="checklist-optional-note">不计入核心完成进度</span>
        </div>

        <ul class="checklist-list checklist-optional-list">
          <li
            v-for="task in OPTIONAL_ONBOARDING_TASKS"
            :key="task.key"
            :class="['checklist-item', `is-${taskStatus(task.key).toLowerCase()}`]"
            :data-testid="`starter-task-${task.key}`"
          >
            <span class="checklist-index" aria-hidden="true">
              {{ taskStatus(task.key) === 'COMPLETED' ? '✓' : taskStatus(task.key) === 'SKIPPED' ? '—' : '·' }}
            </span>

            <div class="checklist-item-main">
              <div class="checklist-item-title-row">
                <span class="checklist-item-title">{{ task.title }}</span>
                <span class="checklist-status-tag">{{ statusLabel(taskStatus(task.key)) }}</span>
              </div>
              <p class="checklist-item-desc">{{ task.description }}</p>
            </div>

            <div v-if="taskStatus(task.key) !== 'COMPLETED'" class="checklist-item-actions">
              <button
                type="button"
                class="checklist-action-btn"
                :data-testid="`starter-action-${task.key}`"
                @click="goToTask(task.key)"
              >
                {{ task.actionLabel }}
              </button>
              <button
                v-if="task.skippable && taskStatus(task.key) !== 'SKIPPED'"
                type="button"
                class="checklist-skip-btn"
                :data-testid="`starter-skip-${task.key}`"
                @click="handleSkipTask(task.key)"
              >
                跳过
              </button>
            </div>
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useOnboardingStore } from '../../stores/onboarding'
import {
  CORE_ONBOARDING_TASKS,
  ONBOARDING_COPY,
  OPTIONAL_ONBOARDING_TASKS,
  type GuideStatus,
  type TaskKey,
} from '../../constants/onboarding'

const router = useRouter()
const auth = useAuthStore()
const onboarding = useOnboardingStore()

const collapsed = ref(false)

function taskStatus(key: TaskKey): GuideStatus {
  return onboarding.state.tasks[key] || 'NOT_STARTED'
}

function statusLabel(status: GuideStatus): string {
  switch (status) {
    case 'COMPLETED':
      return '已完成'
    case 'SKIPPED':
      return '已跳过'
    case 'IN_PROGRESS':
      return '进行中'
    default:
      return '待完成'
  }
}

function goToTask(key: TaskKey): void {
  switch (key) {
    case 'FIRST_DIARY':
      void router.push('/write')
      return
    case 'FIRST_ANALYSIS_VIEWED':
      if (onboarding.state.tasks.FIRST_DIARY === 'COMPLETED' && auth.userId != null) {
        void router.push(`/profile/${auth.userId}`)
      } else {
        void router.push('/write')
      }
      return
    case 'FIRST_CHAT_REPLY':
      void router.push('/chat')
      return
    case 'MEMORY_CENTER_VISITED':
      void router.push('/ai-memory')
      return
    case 'THEME_EXPLORED':
      if (auth.userId != null) {
        void router.push({ path: `/profile/${auth.userId}`, query: { openSettings: 'theme' } })
      }
      return
  }
}

async function handleSkipTask(key: TaskKey): Promise<void> {
  await onboarding.dismissTask(key)
}

async function handleDismissChecklist(): Promise<void> {
  await onboarding.dismissChecklist()
}
</script>

<style scoped>
.starter-checklist {
  margin: 0 0 18px;
  padding: 18px 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
}

.checklist-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.checklist-heading {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.checklist-eyebrow {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--color-accent);
}

.checklist-title-row {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 10px;
}

.checklist-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--text-md);
  font-weight: 600;
  line-height: var(--leading-snug);
  color: var(--color-text);
}

.checklist-progress-label {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
}

.checklist-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.checklist-control-btn {
  padding: 4px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: var(--text-xs);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.checklist-control-btn:hover {
  color: var(--color-text);
  border-color: var(--color-border-strong);
  background: var(--color-surface-hover);
}

.checklist-dismiss-btn {
  color: var(--color-text-muted);
}

.checklist-progress-track {
  margin-top: 12px;
  width: 100%;
  height: 3px;
  border-radius: var(--radius-full);
  background: var(--color-surface-soft);
  overflow: hidden;
}

.checklist-progress-fill {
  height: 100%;
  border-radius: var(--radius-full);
  background: var(--color-primary);
  transition: width var(--duration-normal) var(--ease-out);
}

.checklist-body {
  margin-top: 14px;
}

.checklist-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border-top: 1px solid var(--color-border);
}

.checklist-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
}

.checklist-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.checklist-index {
  width: 24px;
  height: 24px;
  margin-top: 1px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-full);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-size: 11px;
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-surface-soft);
  flex-shrink: 0;
}

.checklist-item.is-completed .checklist-index {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.checklist-item.is-skipped .checklist-index {
  color: var(--color-text-muted);
  border-color: var(--color-border);
}

.checklist-item-main {
  flex: 1;
  min-width: 0;
}

.checklist-item-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.checklist-item-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text);
  line-height: 1.4;
}

.checklist-item.is-completed .checklist-item-title,
.checklist-item.is-skipped .checklist-item-title {
  color: var(--color-text-muted);
}

.checklist-status-tag {
  padding: 1px 7px;
  border-radius: var(--radius-full);
  border: 1px solid var(--color-border);
  background: var(--color-surface-soft);
  font-size: 11px;
  color: var(--color-text-secondary);
}

.checklist-item.is-completed .checklist-status-tag {
  border-color: color-mix(in oklab, var(--color-primary) 35%, transparent);
  background: color-mix(in oklab, var(--color-primary) 10%, transparent);
  color: var(--color-primary);
}

.checklist-item.is-skipped .checklist-status-tag {
  color: var(--color-text-muted);
}

.checklist-item-desc {
  margin: 4px 0 0;
  font-size: var(--text-xs);
  line-height: 1.55;
  color: var(--color-text-muted);
}

.checklist-item-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.checklist-action-btn {
  padding: 5px 12px;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-primary);
  font-size: var(--text-xs);
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.checklist-action-btn:hover {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.checklist-skip-btn {
  padding: 5px 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  cursor: pointer;
  white-space: nowrap;
  transition: color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.checklist-skip-btn:hover {
  color: var(--color-text);
  border-color: var(--color-border);
}

.checklist-optional {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border-strong);
}

.checklist-optional-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.checklist-optional-title {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-secondary);
  letter-spacing: 0.04em;
}

.checklist-optional-note {
  font-size: 11px;
  color: var(--color-text-light);
}

.checklist-optional-list {
  border-top: none;
}

@media (max-width: 600px) {
  .starter-checklist {
    padding: 14px;
  }

  .checklist-header {
    flex-direction: column;
    align-items: stretch;
  }

  .checklist-header-actions {
    justify-content: flex-end;
  }

  .checklist-item {
    flex-wrap: wrap;
  }

  .checklist-item-actions {
    width: 100%;
    padding-left: 36px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .checklist-progress-fill,
  .checklist-control-btn,
  .checklist-action-btn,
  .checklist-skip-btn {
    transition: none;
  }
}
</style>
