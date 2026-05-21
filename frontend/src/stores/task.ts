import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { growthApi, type CheckInStatus, type DailyTaskItem } from '../api'

export const useTaskStore = defineStore('task', () => {
  // ── 签到状态（目标契约） ──
  const checkInState = ref<CheckInStatus>({
    continuousDays: 0,
    currentMonthTotal: 0,
    todaySigned: false,
    nextExpReward: 10,
  })

  // ── 每日任务列表 ──
  const tasks = ref<DailyTaskItem[]>([])
  const tasksLoading = ref(false)

  // ── 签到按钮状态 ──
  const checkingIn = ref(false)
  const checkInMsg = ref('')

  // ── 本地已领取标记（session 级，无后端持久化） ──
  const claimedRewards = ref<Set<string>>(new Set())

  // ── 计算属性 ──
  const tomorrowExp = computed(() => {
    const d = checkInState.value.continuousDays + 1
    return d >= 7 ? 25 : 10 + d * 2
  })

  const allTasksCompleted = computed(() =>
    tasks.value.length > 0 && tasks.value.every(t => t.current >= t.max),
  )

  const allRewardsClaimed = computed(() =>
    tasks.value.length > 0 && tasks.value.every(
      t => t.current >= t.max && claimedRewards.value.has(t.field),
    ),
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
      const streak = status?.streak ?? 0

      checkInState.value = {
        continuousDays: streak,
        currentMonthTotal: monthTotal,
        todaySigned: status?.checkedInToday ?? false,
        nextExpReward: streak >= 6 ? 25 : 10 + streak * 2,
      }
    } catch {
      // 静默降级
    }
  }

  /** 加载今日任务进度 */
  async function fetchTasks() {
    tasksLoading.value = true
    try {
      const res = await growthApi.progress()
      tasks.value = (res.data.data ?? []) as DailyTaskItem[]
    } catch {
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

  /** 领取任务奖励 */
  function claimReward(field: string) {
    claimedRewards.value = new Set([...claimedRewards.value, field])
  }

  /** 判断某任务是否已领取 */
  function isClaimed(field: string): boolean {
    return claimedRewards.value.has(field)
  }

  /** 获取任务按钮文字 */
  function taskButtonLabel(task: DailyTaskItem): string {
    if (task.current >= task.max && isClaimed(task.field)) return '已完成'
    if (task.current >= task.max) return '领取奖励'
    return '去完成'
  }

  /** 获取任务按钮状态 */
  function taskButtonState(task: DailyTaskItem): 'go' | 'claim' | 'done' {
    if (task.current >= task.max && isClaimed(task.field)) return 'done'
    if (task.current >= task.max) return 'claim'
    return 'go'
  }

  /** 重置每日状态（跨天时清除领取记录） */
  function resetDaily() {
    claimedRewards.value = new Set()
    checkInMsg.value = ''
  }

  return {
    checkInState,
    tasks,
    tasksLoading,
    checkingIn,
    checkInMsg,
    tomorrowExp,
    allTasksCompleted,
    allRewardsClaimed,
    fetchCheckInStatus,
    fetchTasks,
    doCheckIn,
    claimReward,
    isClaimed,
    taskButtonLabel,
    taskButtonState,
    resetDaily,
  }
})
