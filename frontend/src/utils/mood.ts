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
    // Both -100 to 100, normalize to 0..1
    const v = Math.min(100, Math.max(-100, valence));
    const a = Math.min(100, Math.max(-100, arousal));
    const tx = (v + 100) / 200;
    const ty = (a + 100) / 200;

    // Corner RGB values (Morandi palette)
    const q1 = [230, 154, 99];  // +V, +A (暖橙)
    const q2 = [161, 92, 84];   // -V, +A (焦红)
    const q3 = [117, 130, 150]; // -V, -A (蓝灰)
    const q4 = [114, 161, 146]; // +V, -A (灰绿)

    // Bilinear interpolation
    const bottomR = (1 - tx) * q3[0] + tx * q4[0];
    const bottomG = (1 - tx) * q3[1] + tx * q4[1];
    const bottomB = (1 - tx) * q3[2] + tx * q4[2];

    const topR = (1 - tx) * q2[0] + tx * q1[0];
    const topG = (1 - tx) * q2[1] + tx * q1[1];
    const topB = (1 - tx) * q2[2] + tx * q1[2];

    const finalR = Math.round((1 - ty) * bottomR + ty * topR);
    const finalG = Math.round((1 - ty) * bottomG + ty * topG);
    const finalB = Math.round((1 - ty) * bottomB + ty * topB);

    return `rgb(${finalR}, ${finalG}, ${finalB})`;
  }
  return label && MOOD_COLORS[label] ? MOOD_COLORS[label] : '#9cb4a8';
}

/** 所有有效情绪标签 */
export const MOOD_LABELS = Object.keys(MOOD_COLORS)
