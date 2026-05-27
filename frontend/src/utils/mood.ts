/**
 * 情绪分类学：18 种情绪 × 莫兰迪色系
 * 积极象限偏暖，消极象限偏冷，温度即信号
 */
export const MOOD_COLORS: Record<string, string> = {
  // 积极 / 高能量 — 轻盈晨光系（提亮、透气、发光感）
  '喜悦': '#f2d280',
  '期待': '#f2b17e',
  '兴奋': '#eb978d',
  '自豪': '#e3a87c',
  // 积极 / 低能量 — 草木系（绿/青调：自然与呼吸）
  '轻松': '#86bba2',
  '平静': '#5c9a86',
  '感恩': '#78a8a1',
  '满足': '#94b19c',
  // 消极 / 高能量 — 沉重焦土系（压暗、沉重、收敛感）
  '烦躁': '#a86a42',
  '愤怒': '#9e4b43',
  '焦虑': '#a65d53',
  '害怕': '#825057',
  // 消极 / 低能量 — 暮色系（蓝/紫/冷灰调：水、阴天与情绪收缩）
  '疲惫': '#9b9eaa',
  '委屈': '#968f9e',
  '难过': '#7b8ba3',
  '迷茫': '#8b94a0',
  '孤独': '#6d768a',
  '内疚': '#8a858a',
}

export function moodColor(label?: string, valence?: number | null, arousal?: number | null): string {
  if (valence != null && arousal != null) {
    // Both -100 to 100
    if (valence > 0 && arousal > 0) return '#e69a63'; // 暖橙 (积极高能)
    if (valence > 0 && arousal <= 0) return '#72a192'; // 灰绿 (积极低能)
    if (valence <= 0 && arousal > 0) return '#a15c54'; // 焦红 (消极高能)
    if (valence <= 0 && arousal <= 0) return '#758296'; // 蓝灰 (消极低能)
  }
  return label && MOOD_COLORS[label] ? MOOD_COLORS[label] : '#9cb4a8';
}

/** 所有有效情绪标签 */
export const MOOD_LABELS = Object.keys(MOOD_COLORS)
