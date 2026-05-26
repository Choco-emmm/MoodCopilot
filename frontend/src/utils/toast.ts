import { reactive } from 'vue'
import { growthApi } from '../api'

declare global {
  interface Window { $message?: any }
}

interface DailyCap {
  current: number
  max: number
}

const caps = reactive<Record<string, DailyCap>>({})
let capsLoaded = false

async function ensureCapsLoaded() {
  if (capsLoaded) return
  try {
    const res = await growthApi.progress()
    const bars = res.data.data as { field: string; current: number; max: number }[] | undefined
    if (bars) {
      for (const b of bars) {
        caps[b.field] = { current: b.current, max: b.max }
      }
    }
  } catch { /* ignore */ }
  capsLoaded = true
}

/** 经验值弹窗已关闭，改为任务中心统一领取。保留接口兼容，暂不弹窗。 */
export async function tryExpToast(_field: string, _msg: string): Promise<boolean> {
  return false
}

export function expToast(_msg: string) {
  // no-op: 经验值统一在任务中心领取
}
