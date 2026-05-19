import { reactive } from 'vue'
import { growthApi } from '../api'

declare global {
  interface Window { $message?: { success: (msg: string, opts?: { duration?: number }) => void } }
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

/** 仅当未达每日上限时弹出 EXP toast。返回 true 表示已弹。 */
export async function tryExpToast(field: string, msg: string): Promise<boolean> {
  await ensureCapsLoaded()
  const cap = caps[field]
  if (!cap) {
    // 未知字段，安全放行
    window.$message?.success(msg, { duration: 1800 })
    return true
  }
  if (cap.current >= cap.max) return false
  cap.current++
  window.$message?.success(msg, { duration: 1800 })
  return true
}

/** 简易同步版（无需等待 API），用于已经有数据的场景 */
export function expToast(msg: string) {
  window.$message?.success(msg, { duration: 1800 })
}
