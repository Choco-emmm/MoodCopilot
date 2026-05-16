/**
 * 情绪分类学：18 种情绪 × 莫兰迪色系
 * 积极象限偏暖，消极象限偏冷，温度即信号
 */
export const MOOD_COLORS: Record<string, string> = {
  // 积极 / 高能量 — 暖调
  '喜悦': '#c4a878',
  '期待': '#d4b896',
  '兴奋': '#cc9a94',
  '自豪': '#b8a4c4',
  // 积极 / 低能量 — 冷调治愈绿系
  '轻松': '#7db89a',
  '平静': '#4f8f7c',
  '感恩': '#a4c0b8',
  '满足': '#acbea8',
  // 消极 / 高能量 — 暖调但不刺眼
  '烦躁': '#e09f5c',
  '愤怒': '#cc9688',
  '焦虑': '#e08d72',
  '害怕': '#c4a8b4',
  // 消极 / 低能量 — 冷调灰蓝灰褐
  '疲惫': '#9cb4a8',
  '委屈': '#d4a373',
  '难过': '#a8b0c0',
  '孤独': '#aeaeb8',
  '迷茫': '#c0bab0',
  '内疚': '#c0aca0',
}

export function moodColor(label: string): string {
  return MOOD_COLORS[label] || '#9cb4a8'
}

/** 所有有效情绪标签 */
export const MOOD_LABELS = Object.keys(MOOD_COLORS)
