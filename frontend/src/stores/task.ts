import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { growthApi, type CheckInStatus, type DailyTaskItem } from '../api'
import { logWarn } from '../utils/logger'

export const useTaskStore = defineStore('task', () => {
  const LEVEL_THRESHOLDS = [0, 150, 500, 1500, 4000, 10000]

  // ── 签到状态（目标契约） ──
  const checkInState = ref<CheckInStatus>({
    continuousDays: 0,
    currentMonthTotal: 0,
    todaySigned: false,
    nextExpReward: 10,
  })

  // ── 等级 / 经验 ──
  const userExp = ref(0)
  const userLevel = ref(1)
  const expToNextLevel = ref(150)

  /** 等级进度百分比 (0-100) */
  const levelProgress = computed(() => {
    const prev = LEVEL_THRESHOLDS[userLevel.value - 1] ?? 0
    const next = LEVEL_THRESHOLDS[userLevel.value] ?? prev + 1
    if (next <= prev) return 100
    return Math.min(Math.round(((userExp.value - prev) / (next - prev)) * 100), 100)
  })

  // ── 本月签到位图 ──
  const monthCheckinDays = ref<boolean[]>([])

  // ── 每日任务列表 ──
  const tasks = ref<DailyTaskItem[]>([])
  const tasksLoading = ref(false)

  // ── 签到按钮状态 ──
  const checkingIn = ref(false)
  const checkInMsg = ref('')

  // ── 计算属性 ──
  const tomorrowExp = computed(() => {
    const d = checkInState.value.continuousDays + 1
    return d >= 7 ? 25 : 10 + d * 2
  })

  const allTasksCompleted = computed(() =>
    tasks.value.length > 0 && tasks.value.every(t => t.current >= t.max),
  )
  // ── 方法 ──

  /** 加载签到状态（先用旧接口组装） */
  async function fetchCheckInStatus() {
    try {
      const [statusRes, checkinsRes] = await Promise.all([
        growthApi.status(),
        growthApi.checkins(),
      ])
      const status = statusRes.data.data
      const checkins = checkinsRes.data.data as boolean[] | undefined

      const monthTotal = checkins ? checkins.filter(Boolean).length : (status?.monthCheckins ?? 0)
      if (checkins) monthCheckinDays.value = checkins
      const streak = status?.streak ?? 0

      checkInState.value = {
        continuousDays: streak,
        currentMonthTotal: monthTotal,
        todaySigned: status?.checkedInToday ?? false,
        nextExpReward: streak >= 6 ? 25 : 10 + streak * 2,
      }
      if (status) {
        userExp.value = status.exp ?? 0
        userLevel.value = status.level ?? 1
        expToNextLevel.value = status.expToNextLevel ?? LEVEL_THRESHOLDS[1]
      }
    } catch (e) {
      logWarn('task', '加载签到状态失败', e)
    }
  }

  /** 加载今日任务进度 */
  async function fetchTasks() {
    tasksLoading.value = true
    try {
      const res = await growthApi.progress()
      tasks.value = (res.data.data ?? []) as DailyTaskItem[]
    } catch (e) {
      logWarn('task', '加载每日任务失败', e)
      tasks.value = []
    } finally {
      tasksLoading.value = false
    }
  }

  /** 签到 */
  async function doCheckIn(): Promise<boolean> {
    if (checkInState.value.todaySigned || checkingIn.value) return false
    checkingIn.value = true
    checkInMsg.value = ''
    try {
      const res = await growthApi.checkIn()
      const data = res.data.data
      if (data?.checkedIn) {
        checkInState.value = {
          ...checkInState.value,
          todaySigned: true,
          continuousDays: data.streak ?? checkInState.value.continuousDays + 1,
          nextExpReward: (data.streak ?? 0) >= 6 ? 25 : 10 + (data.streak ?? 0) * 2,
        }
        checkInMsg.value = `签到成功！+${data.exp} EXP`
        // 刷新任务列表以更新签到任务的 claimed 状态
        await fetchTasks()
        return true
      }
      checkInState.value = { ...checkInState.value, todaySigned: true }
      return false
    } catch (e: any) {
      checkInMsg.value = e?.response?.data?.message || '签到失败，请稍后再试'
      return false
    } finally {
      checkingIn.value = false
    }
  }
  /** 判断某任务是否已完成（新版自动发放经验，只要 current >= max 即完成） */
  function isClaimed(field: string): boolean {
    if (field === 'checkin' && checkInState.value.todaySigned) return true
    const task = tasks.value.find(t => t.field === field)
    return task ? task.current >= task.max : false
  }

  /** 获取任务按钮文字（自动发放，直接跳过"领取奖励"） */
  function taskButtonLabel(task: DailyTaskItem): string {
    if (task.current >= task.max) return '已完成'
    return '去完成'
  }

  /** 获取任务按钮状态 */
  function taskButtonState(task: DailyTaskItem): 'go' | 'done' {
    if (task.current >= task.max) return 'done'
    return 'go'
  }

  /** 重置每日状态（跨天时清除本地消息） */
  function resetDaily() {
    checkInMsg.value = ''
  }

  return {
    checkInState,
    tasks,
    tasksLoading,
    checkingIn,
    checkInMsg,
    userExp,
    userLevel,
    expToNextLevel,
    levelProgress,
    monthCheckinDays,
    tomorrowExp,
    allTasksCompleted,
    fetchCheckInStatus,
    fetchTasks,
    doCheckIn,
    isClaimed,
    taskButtonLabel,
    taskButtonState,
    resetDaily,
  }
})
