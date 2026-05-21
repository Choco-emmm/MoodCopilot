<template>
  <main class="app-shell">
    <AppHeader />

    <!-- ── 签到状态卡片 ── -->
    <section class="panel checkin-stats">
      <div class="stats-grid">
        <div class="stat-card">
          <span class="stat-number">{{ taskStore.checkInState.continuousDays }}</span>
          <span class="stat-label">连签天数</span>
        </div>
        <div class="stat-card">
          <span class="stat-number">{{ taskStore.checkInState.currentMonthTotal }}</span>
          <span class="stat-label">本月签到</span>
        </div>
        <div class="stat-card">
          <span class="stat-number">+{{ tomorrowExp }}</span>
          <span class="stat-label">明日经验</span>
        </div>
      </div>

      <!-- 签到按钮 -->
      <button
        class="checkin-btn-main"
        :class="{ 'checkin-btn-done': taskStore.checkInState.todaySigned }"
        :disabled="taskStore.checkInState.todaySigned || taskStore.checkingIn"
        @click="handleCheckIn"
      >
        <span v-if="taskStore.checkingIn" class="checkin-btn-icon">
          <n-spin :size="18" />
        </span>
        <span v-else class="checkin-btn-icon">{{ taskStore.checkInState.todaySigned ? '✓' : '☀' }}</span>
        <span>{{ taskStore.checkInState.todaySigned ? '已签到' : '立即签到' }}</span>
      </button>
      <p v-if="taskStore.checkInMsg" class="checkin-msg">{{ taskStore.checkInMsg }}</p>
    </section>

    <!-- ── 每日任务 ── -->
    <section class="panel tasks-section">
      <div class="section-head">
        <h3>每日任务</h3>
        <span class="section-tag">每日刷新</span>
      </div>

      <div v-if="taskStore.tasksLoading" class="tasks-loading">
        <n-spin size="small" />
      </div>

      <div v-else-if="taskStore.tasks.length === 0" class="tasks-empty">
        <p>暂时没有任务数据</p>
      </div>

      <div v-else class="tasks-list-full">
        <div
          v-for="task in taskStore.tasks"
          :key="task.field"
          class="task-card"
          :class="{ 'task-card-done': taskStore.taskButtonState(task) === 'done' }"
        >
          <div class="task-card-info">
            <div class="task-card-header">
              <span class="task-card-label">{{ task.label }}</span>
              <n-tag
                :type="task.current >= task.max ? 'success' : 'default'"
                size="small"
                :bordered="false"
              >
                {{ task.current }}/{{ task.max }}
              </n-tag>
            </div>
            <div class="task-bar-wrap">
              <div
                class="task-bar-fill"
                :class="{ 'task-bar-full': task.current >= task.max }"
                :style="{ width: (task.max > 0 ? Math.min(task.current / task.max, 1) * 100 : 0) + '%' }"
              />
            </div>
            <span class="task-exp-badge">+{{ task.expPerAction }} EXP/次</span>
          </div>

          <div class="task-card-action">
            <n-button
              size="small"
              :type="taskStore.taskButtonState(task) === 'claim' ? 'warning' : taskStore.taskButtonState(task) === 'done' ? 'default' : 'primary'"
              :disabled="taskStore.taskButtonState(task) === 'done'"
              :secondary="taskStore.taskButtonState(task) === 'claim'"
              :loading="taskStore.claimingField === task.field"
              @click="handleTaskClick(task)"
            >
              {{ taskStore.claimingField === task.field ? '' : taskStore.taskButtonLabel(task) }}
            </n-button>
          </div>
        </div>
      </div>

      <div v-if="taskStore.allRewardsClaimed && taskStore.checkInState.todaySigned" class="all-done-banner">
        <span class="all-done-icon">🎉</span>
        <span>今日任务全部完成，明天继续加油～</span>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NSpin, NTag } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { useTaskStore } from '../stores/task'
import type { DailyTaskItem } from '../api'

const router = useRouter()
const taskStore = useTaskStore()

const tomorrowExp = taskStore.tomorrowExp

onMounted(async () => {
  await Promise.all([
    taskStore.fetchCheckInStatus(),
    taskStore.fetchTasks(),
  ])
})

async function handleCheckIn() {
  await taskStore.doCheckIn()
  await taskStore.fetchTasks()
}

async function handleTaskClick(task: DailyTaskItem) {
  const state = taskStore.taskButtonState(task)
  if (state === 'claim') {
    await taskStore.claimReward(task.field)
    if (taskStore.claimError) {
      window.alert(taskStore.claimError)
    }
    return
  }
  if (state === 'done') return
  // go
  navigateToTask(task.field)
}

function navigateToTask(field: string) {
  switch (field) {
    case 'checkin':
      handleCheckIn()
      break
    case 'diary':
      router.push('/write')
      break
    case 'chat':
      router.push('/chat')
      break
    case 'like':
    case 'comment':
      router.push('/')
      break
    default:
      break
  }
}
</script>

<style scoped>
/* ── 签到状态卡片 ── */
.checkin-stats {
  margin-bottom: 14px;
  padding: 20px 22px;
  display: grid;
  gap: 18px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.stat-card {
  display: grid;
  gap: 4px;
  padding: 14px 10px;
  border-radius: var(--radius-md);
  background: var(--color-surface-soft);
  text-align: center;
  border: 1px solid color-mix(in srgb, var(--color-border) 70%, transparent 30%);
}

.stat-number {
  font-family: var(--font-body);
  font-size: var(--text-2xl);
  font-weight: 800;
  color: var(--color-primary);
  line-height: 1;
}

.stat-label {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-muted);
  letter-spacing: 0.04em;
}

/* ── 签到主按钮 ── */
.checkin-btn-main {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  padding: 16px 24px;
  border: none;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--color-primary), #5a9470);
  color: #fff;
  font-family: var(--font-body);
  font-size: var(--text-md);
  font-weight: 700;
  cursor: pointer;
  transition: background var(--duration-normal) var(--ease-out),
              transform var(--duration-fast) var(--ease-out),
              box-shadow var(--duration-normal) var(--ease-out);
  box-shadow: 0 4px 18px color-mix(in srgb, var(--color-primary) 28%, transparent 72%);
}

.checkin-btn-main:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--color-primary-hover), #4a8460);
  transform: translateY(-1px);
  box-shadow: 0 6px 24px color-mix(in srgb, var(--color-primary) 36%, transparent 64%);
}

.checkin-btn-main:active:not(:disabled) {
  transform: translateY(0);
}

.checkin-btn-main:disabled {
  cursor: default;
}

.checkin-btn-done {
  background: var(--color-surface-soft);
  color: var(--color-text-muted);
  box-shadow: none;
}

.checkin-btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  font-size: 18px;
}

.checkin-msg {
  margin: 0;
  text-align: center;
  font-size: var(--text-sm);
  color: var(--color-primary);
  background: var(--color-primary-light);
  border-radius: var(--radius-sm);
  padding: 8px 12px;
}

/* ── 每日任务 ── */
.tasks-section {
  margin-bottom: 14px;
  padding: 20px 22px;
}

.tasks-section .section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.tasks-section h3 {
  margin: 0;
}

.section-tag {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--color-primary);
  background: var(--color-primary-light);
  border: 1px solid color-mix(in srgb, var(--color-primary) 18%, transparent 82%);
  border-radius: 999px;
  padding: 2px 9px;
}

.tasks-loading {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}

.tasks-empty {
  text-align: center;
  padding: 40px 0;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.tasks-list-full {
  display: grid;
  gap: 10px;
}

.task-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: var(--color-surface-soft);
  border: 1px solid color-mix(in srgb, var(--color-border-strong) 14%, transparent 86%);
  transition: border-color var(--duration-fast) var(--ease-out);
}

.task-card:hover {
  border-color: color-mix(in srgb, var(--color-primary) 30%, var(--color-border) 70%);
}

.task-card-done {
  opacity: 0.62;
  border-color: transparent;
}

.task-card-info {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.task-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.task-card-label {
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--color-text);
}

.task-bar-wrap {
  height: 5px;
  border-radius: 3px;
  background: color-mix(in srgb, var(--color-primary) 10%, transparent 90%);
  overflow: hidden;
}

.task-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--color-primary);
  transition: width 0.4s var(--ease-out);
  min-width: 0;
}

.task-bar-full {
  background: color-mix(in srgb, var(--color-primary) 50%, var(--color-accent-light) 50%);
}

.task-exp-badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-primary-light);
  border-radius: 6px;
  padding: 2px 8px;
  width: fit-content;
}

.task-card-action {
  flex-shrink: 0;
}

/* ── 全部完成横幅 ── */
.all-done-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-primary-light), color-mix(in srgb, var(--color-primary-light) 70%, var(--color-surface) 30%));
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-primary);
  border: 1px dashed color-mix(in srgb, var(--color-primary) 24%, transparent 76%);
}

.all-done-icon {
  font-size: 18px;
}

/* ── 响应式 ── */
@media (max-width: 780px) {
  .checkin-stats {
    padding: 16px;
  }

  .stats-grid {
    gap: 8px;
  }

  .stat-card {
    padding: 12px 6px;
  }

  .stat-number {
    font-size: var(--text-xl);
  }

  .tasks-section {
    padding: 16px;
  }

  .task-card {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .task-card-action {
    justify-self: stretch;
  }

  .task-card-action .n-button {
    width: 100%;
  }
}
</style>
