/**
 * 情绪分类学：18 种情绪 × 莫兰迪色系
 * 积极象限偏暖，消极象限偏冷，温度即信号
 */
export const MOOD_COLORS: Record<string, string> = {
  // 积极 / 高能量 — 晨光系（黄/橙/粉调：阳光与活力）
  '喜悦': '#eac97d',
  '期待': '#eca77d',
  '兴奋': '#e39589',
  '自豪': '#d9a986',
  // 积极 / 低能量 — 草木系（绿/青调：自然与呼吸）
  '轻松': '#86bba2',
  '平静': '#5c9a86',
  '感恩': '#78a8a1',
  '满足': '#94b19c',
  // 消极 / 高能量 — 陶土系（砖红/赭石调：泥土包裹的燃烧感）
  '烦躁': '#d18d68',
  '焦虑': '#d28172',
  '愤怒': '#c57365',
  '害怕': '#ba7a83',
  // 消极 / 低能量 — 暮色系（蓝/紫/冷灰调：水、阴天与情绪收缩）
  '疲惫': '#9b9eaa',
  '委屈': '#968f9e',
  '难过': '#7b8ba3',
  '迷茫': '#8b94a0',
  '孤独': '#6d768a',
  '内疚': '#8a858a',
}

export function moodColor(label: string): string {
  return MOOD_COLORS[label] || '#9cb4a8'
}

/** 所有有效情绪标签 */
export const MOOD_LABELS = Object.keys(MOOD_COLORS)
