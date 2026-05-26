<template>
  <main class="app-shell">
    <AppHeader />

    <!-- ── 等级进度卡片 ── -->
    <section class="panel level-card">
      <div class="level-card-top">
        <div class="level-badge">Lv.{{ taskStore.userLevel }}</div>
        <div class="level-exp-text">{{ taskStore.userExp }} / {{ taskStore.expToNextLevel > 0 ? taskStore.expToNextLevel : '—' }} EXP</div>
      </div>
      <div class="level-bar-track">
        <div
          class="level-bar-fill"
          :style="{ width: taskStore.levelProgress + '%' }"
        />
      </div>
      <div class="level-card-foot">
        <p class="level-card-hint" v-if="taskStore.expToNextLevel > 0">
          距 Lv.{{ taskStore.userLevel + 1 }} 还需 {{ Math.max(0, taskStore.expToNextLevel - taskStore.userExp) }} EXP
        </p>
        <p class="level-card-hint level-card-max" v-else>已达满级</p>
        <button class="quota-link-btn" @click="showQuotaTable = true">查看配额表 →</button>
      </div>
    </section>

    <!-- ── 签到区域 ── -->
    <section class="panel checkin-section">
      <!-- 周签可视化 -->
      <div class="week-strip">
        <div
          v-for="(day, idx) in weekDays"
          :key="idx"
          class="week-day"
          :class="{
            'week-day-done': day.done,
            'week-day-today': day.isToday,
          }"
        >
          <span class="day-number">{{ day.dayOfMonth }}</span>
          <span class="day-label">{{ day.label }}</span>
        </div>
      </div>

      <div class="checkin-stats-row">
        <div class="checkin-stat">
          <span class="checkin-stat-num">{{ taskStore.checkInState.continuousDays }}</span>
          <span class="checkin-stat-label">连签天数</span>
        </div>
        <div class="checkin-stat">
          <span class="checkin-stat-num">{{ taskStore.checkInState.currentMonthTotal }}</span>
          <span class="checkin-stat-label">本月签到</span>
        </div>
        <div class="checkin-stat">
          <span class="checkin-stat-num">+{{ taskStore.tomorrowExp }}</span>
          <span class="checkin-stat-label">明日经验</span>
        </div>
      </div>

      <button
        class="checkin-btn-main"
        :class="{ 'checkin-btn-done': taskStore.checkInState.todaySigned }"
        :disabled="taskStore.checkInState.todaySigned || taskStore.checkingIn"
        @click="handleCheckIn"
      >
        <span class="checkin-btn-icon">{{ taskStore.checkingIn ? '⏳' : taskStore.checkInState.todaySigned ? '✓' : '☀' }}</span>
        <span>{{ taskStore.checkingIn ? '签到中...' : taskStore.checkInState.todaySigned ? '今日已签到' : '立即签到' }}</span>
      </button>
      <p v-if="taskStore.checkInMsg" class="checkin-msg">{{ taskStore.checkInMsg }}</p>
    </section>

    <!-- ── 每日任务 ── -->
    <section class="panel tasks-section">
      <div class="panel-header">
        <h3 class="panel-title">每日任务</h3>
        <span class="panel-tag">每日刷新</span>
      </div>

      <div v-if="taskStore.tasksLoading" class="tasks-loading">
        <n-spin size="small" />
        <span>加载中...</span>
      </div>

      <div v-else-if="taskStore.tasks.length === 0" class="tasks-empty">
        <span class="tasks-empty-icon">📋</span>
        <p>暂无任务数据</p>
        <p class="tasks-empty-sub">去写日记或聊天来获取经验吧</p>
      </div>

      <div v-else class="tasks-list-full">
        <div
          v-for="task in taskStore.tasks"
          :key="task.field"
          class="task-card"
          :class="{
            'task-card-done': taskStore.taskButtonState(task) === 'done',
          }"
        >
          <div class="task-card-info">
            <div class="task-card-header">
              <span class="task-card-icon">{{ taskIcon(task.field) }}</span>
              <span class="task-card-label">{{ task.label }}</span>
              <span
                class="task-card-counter"
                :class="{ 'task-counter-full': task.current >= task.max }"
              >
                {{ task.current }}/{{ task.max }}
              </span>
            </div>
            <div class="task-card-foot">
              <span class="task-exp-badge">+{{ task.expPerAction }} EXP/次</span>
            </div>
          </div>

          <div class="task-card-action">
            <button
              v-if="taskStore.taskButtonState(task) === 'done'"
              class="btn-done"
              disabled
            >
              已完成 ✓
            </button>
            <button
              v-else
              class="btn-go"
              @click="handleTaskClick(task)"
            >
              去完成 →
            </button>
          </div>
        </div>
      </div>

      <!-- 操作反馈 -->
      <div v-if="claimResult.show" class="claim-toast" :class="claimResult.ok ? 'claim-toast-ok' : 'claim-toast-err'">
        {{ claimResult.msg }}
      </div>

      <div v-if="taskStore.allTasksCompleted && taskStore.checkInState.todaySigned" class="all-done-banner">
        <span class="all-done-icon">🎉</span>
        <span>今日任务全部完成，明天继续加油～</span>
      </div>
    </section>
  </main>

  <QuotaTableModal :show="showQuotaTable" :level="taskStore.userLevel" @close="showQuotaTable = false" />
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NSpin } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import QuotaTableModal from '../components/QuotaTableModal.vue'
import { useTaskStore } from '../stores/task'
import type { DailyTaskItem } from '../api'

const router = useRouter()
const taskStore = useTaskStore()

const showQuotaTable = ref(false)

const claimResult = reactive({ show: false, ok: false, msg: '', timer: 0 as number })

const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日']

const weekDays = reactive<Array<{ label: string; isToday: boolean; done: boolean; dayOfMonth: number; date: Date }>>([])

function buildWeekDays() {
  const today = new Date()
  const dayOfWeek = today.getDay()
  const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek
  weekDays.length = 0
  for (let i = 0; i < 7; i++) {
    const d = new Date(today)
    d.setDate(today.getDate() + mondayOffset + i)
    const label = WEEKDAY_LABELS[(d.getDay() + 6) % 7]
    const isToday = d.toDateString() === today.toDateString()
    weekDays.push({ label, isToday, done: false, dayOfMonth: d.getDate(), date: d })
  }
}

function syncWeekStrip() {
  const checkins = taskStore.monthCheckinDays
  const today = new Date()
  if (!checkins.length) return
  for (const day of weekDays) {
    const idx = day.date.getDate() - 1
    const isPastOrToday = day.date <= today
    const sameMonth = day.date.getMonth() === today.getMonth()
    day.done = isPastOrToday && sameMonth && idx < checkins.length && checkins[idx] === true
    day.isToday = day.date.toDateString() === today.toDateString()
  }
}

const taskIcons: Record<string, string> = {
  checkin: '☀',
  diary: '✏',
  chat: '💬',
  comment: '💭',
  like: '❤',
}

function taskIcon(field: string): string {
  return taskIcons[field] ?? '📌'
}

function totalTaskExp(task: DailyTaskItem): number {
  return task.current * task.expPerAction
}

function showClaimToast(ok: boolean, msg: string) {
  clearTimeout(claimResult.timer)
  claimResult.show = true
  claimResult.ok = ok
  claimResult.msg = msg
  claimResult.timer = window.setTimeout(() => {
    claimResult.show = false
  }, 2800)
}

onMounted(async () => {
  buildWeekDays()
  await Promise.all([
    taskStore.fetchCheckInStatus(),
    taskStore.fetchTasks(),
  ])
  syncWeekStrip()
})

async function handleCheckIn() {
  const ok = await taskStore.doCheckIn()
  await taskStore.fetchTasks()
  if (ok) {
    showClaimToast(true, `签到成功！+${taskStore.checkInState.nextExpReward} EXP`)
  } else if (taskStore.checkInMsg) {
    showClaimToast(false, taskStore.checkInMsg)
  }
}

async function handleTaskClick(task: DailyTaskItem) {
  const state = taskStore.taskButtonState(task)
  if (state === 'done') return
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
.panel {
  position: relative;
  background: var(--color-surface);
  border-radius: 8px;
  padding: 30px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.03);
  border: 1px solid color-mix(in oklab, var(--color-border) 40%, transparent);
  background-image: linear-gradient(135deg, transparent 80%, color-mix(in oklab, var(--color-primary) 1.5%, transparent));
  margin-bottom: 24px;
}

.level-card {
  padding: 30px;
}

.level-card-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.level-badge {
  font-size: var(--text-lg);
  font-weight: 800;
  color: var(--color-primary);
  letter-spacing: -0.01em;
}

.level-exp-text {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-muted);
}

.level-bar-track {
  height: 4px;
  background: color-mix(in oklab, var(--color-border) 60%, transparent);
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 12px;
}

.level-bar-fill {
  height: 100%;
  border-radius: 2px;
  background: var(--color-primary);
  transition: width 0.6s var(--ease-out);
}

.level-card-hint {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.level-card-max {
  color: var(--color-primary);
  font-weight: 700;
}

.level-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.quota-link-btn {
  background: transparent;
  border: none;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: color-mix(in oklab, var(--color-primary) 30%, transparent);
  text-underline-offset: 4px;
  padding: 0;
}

.quota-link-btn:hover {
  text-decoration-color: var(--color-primary);
}

/* ── 签到区域 ── */
.checkin-section {
  padding: 30px;
}

/* 周签可视化 */
.week-strip {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
  margin-bottom: 24px;
}

.week-day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 0;
  border: 1px solid color-mix(in oklab, var(--color-border) 40%, transparent);
  border-radius: 6px;
  background: var(--theme-surface);
  transition: border-color var(--duration-fast) var(--ease-out),
              background var(--duration-fast) var(--ease-out);
}

.week-day-done {
  background: color-mix(in oklab, var(--color-primary) 6%, transparent);
  border-color: color-mix(in oklab, var(--color-primary) 20%, transparent);
}

.week-day-today {
  border-color: var(--color-primary);
  border-width: 1.5px;
}

.day-number {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}

.week-day-done .day-number,
.week-day-today .day-number {
  color: var(--color-primary);
}

.day-label {
  font-size: 11px;
  color: var(--color-text-muted);
}

/* 签到统计行 */
.checkin-stats-row {
  display: flex;
  justify-content: space-around;
  padding: 20px 0;
  border-top: 1px dashed color-mix(in oklab, var(--color-border) 60%, transparent);
  border-bottom: 1px dashed color-mix(in oklab, var(--color-border) 60%, transparent);
  margin-bottom: 24px;
}

.checkin-stat {
  text-align: center;
}

.checkin-stat-num {
  font-size: var(--text-xl);
  font-weight: 800;
  color: var(--color-primary);
  line-height: 1;
}

.checkin-stat-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-muted);
  letter-spacing: 0.04em;
}

/* 签到按钮 */
.checkin-btn-main {
  width: 100%;
  padding: 14px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 15px;
  font-weight: 600;
  font-family: var(--font-body);
  cursor: pointer;
  box-shadow: 0 4px 12px color-mix(in oklab, var(--color-primary) 20%, transparent);
  transition: transform 0.2s, box-shadow 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.checkin-btn-main:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px color-mix(in oklab, var(--color-primary) 30%, transparent);
}

.checkin-btn-main:active:not(:disabled) {
  transform: translateY(0);
}

.checkin-btn-main:disabled {
  cursor: default;
}

.checkin-btn-done {
  background: color-mix(in oklab, var(--color-border) 80%, transparent);
  color: var(--color-text-muted);
  box-shadow: none;
}

.checkin-btn-icon {
  font-size: 18px;
}

.checkin-msg {
  margin: 0;
  text-align: center;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-primary-light);
  border-radius: var(--radius-sm);
  padding: 8px 12px;
}

/* ── 每日任务 ── */
.tasks-section {
  padding: 30px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  padding-bottom: 12px;
  border-bottom: 1px dashed color-mix(in oklab, var(--color-border) 60%, transparent);
}

.panel-title {
  font-family: var(--font-display);
  font-size: 1.4rem;
  margin: 0;
  color: var(--color-text);
  font-weight: 600;
}

.panel-tag {
  font-size: 11px;
  font-family: var(--font-body);
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 8%, transparent);
  padding: 4px 10px;
  border-radius: 4px;
}

.tasks-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 0;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.tasks-empty {
  text-align: center;
  padding: 32px 0;
}

.tasks-empty-icon {
  font-size: 32px;
}

.tasks-empty p {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.tasks-empty-sub {
  font-size: var(--text-xs) !important;
}

.tasks-list-full {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.task-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-radius: 6px;
  background: color-mix(in oklab, var(--color-surface) 50%, transparent);
  border: 1px solid var(--color-border);
  transition: border-color var(--duration-fast) var(--ease-out),
              background var(--duration-fast) var(--ease-out);
}

.task-card-done {
  opacity: 0.7;
}

.task-card-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.task-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-card-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.task-card-label {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--color-text);
}

.task-card-counter {
  font-size: 11px;
  color: var(--color-text-muted);
  background: color-mix(in oklab, var(--color-border) 50%, transparent);
  padding: 2px 6px;
  border-radius: 4px;
}

.task-counter-full {
  background: color-mix(in oklab, var(--color-primary) 15%, transparent);
  color: var(--color-primary);
}

.task-card-foot {
  display: flex;
  align-items: center;
}

.task-exp-badge {
  font-size: 12px;
  color: var(--color-primary);
  font-family: var(--font-body);
}


.task-card-action {
  flex-shrink: 0;
}

/* 操作按钮 */
.btn-done,
.btn-go {
  padding: 6px 16px;
  border-radius: 20px;
  border: none;
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
  white-space: nowrap;
}

.btn-done {
  background: transparent;
  color: var(--color-text-muted);
  cursor: default;
}

.btn-go {
  background: color-mix(in oklab, var(--color-primary) 8%, transparent);
  color: var(--color-primary);
}

.btn-go:hover {
  background: color-mix(in oklab, var(--color-primary) 15%, transparent);
}

/* ── Toast 反馈 ── */
.claim-toast {
  margin-top: 12px;
  padding: 10px 16px;
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  font-weight: 600;
  text-align: center;
  animation: toastIn 0.25s var(--ease-out);
}

.claim-toast-ok {
  background: var(--color-primary-light);
  color: var(--color-primary);
  border: 1px solid color-mix(in oklab, var(--color-primary) 20%, transparent 80%);
}

.claim-toast-err {
  background: var(--color-accent-bg);
  color: var(--color-accent);
  border: 1px solid color-mix(in oklab, var(--color-accent) 20%, transparent 80%);
}

@keyframes toastIn {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
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
  background: linear-gradient(135deg, var(--color-primary-light), color-mix(in oklab, var(--color-primary-light) 70%, var(--color-surface) 30%));
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-primary);
  border: 1px dashed color-mix(in oklab, var(--color-primary) 24%, transparent 76%);
}

.all-done-icon {
  font-size: 18px;
}

/* ── 响应式 ── */
@media (max-width: 640px) {
  .panel {
    padding: 20px;
  }
}
</style>
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    