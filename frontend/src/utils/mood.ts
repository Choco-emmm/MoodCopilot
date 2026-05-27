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

function hexToRgb(hex: string): [number, number, number] {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result ? [
    parseInt(result[1], 16),
    parseInt(result[2], 16),
    parseInt(result[3], 16)
  ] : [128, 128, 128];
}

export function moodColor(label?: string, valence?: number | null, arousal?: number | null): string {
  let r = 156, g = 180, b = 168; // fallback rgb for #9cb4a8

  if (valence != null && arousal != null) {
    // Both -100 to 100, normalize to 0..1
    const v = Math.min(100, Math.max(-100, valence));
    const a = Math.min(100, Math.max(-100, arousal));
    const tx = (v + 100) / 200;
    const ty = (a + 100) / 200;

    // Corner RGB values (Morandi palette)
    const q1 = [242, 177, 126]; // +V, +A (暖黄/橙色 #f2b17e)
    const q2 = [166, 93, 83];   // -V, +A (焦土红褐色 #a65d53)
    const q3 = [139, 148, 160]; // -V, -A (暮霭蓝灰色 #8b94a0)
    const q4 = [120, 168, 161]; // +V, -A (草木绿色 #78a8a1)

    // Bilinear interpolation
    const bottomR = (1 - tx) * q3[0] + tx * q4[0];
    const bottomG = (1 - tx) * q3[1] + tx * q4[1];
    const bottomB = (1 - tx) * q3[2] + tx * q4[2];

    const topR = (1 - tx) * q2[0] + tx * q1[0];
    const topG = (1 - tx) * q2[1] + tx * q1[1];
    const topB = (1 - tx) * q2[2] + tx * q1[2];

    r = Math.round((1 - ty) * bottomR + ty * topR);
    g = Math.round((1 - ty) * bottomG + ty * topG);
    b = Math.round((1 - ty) * bottomB + ty * topB);
  } else if (label && MOOD_COLORS[label]) {
    const rgb = hexToRgb(MOOD_COLORS[label]);
    r = rgb[0]; g = rgb[1]; b = rgb[2];
  }

  // 结合用户自选主题色：轻微混入主题色，使其在不同主题下保持微妙的和谐感
  if (typeof document !== 'undefined') {
    const themeHex = document.documentElement.style.getPropertyValue('--theme-primary').trim();
    if (themeHex) {
      const themeRgb = hexToRgb(themeHex);
      const blend = 0.15; // 15% 主题色晕染，保持克制
      r = Math.round(r * (1 - blend) + themeRgb[0] * blend);
      g = Math.round(g * (1 - blend) + themeRgb[1] * blend);
      b = Math.round(b * (1 - blend) + themeRgb[2] * blend);
    }
  }

  return `rgb(${r}, ${g}, ${b})`;
}

/** 所有有效情绪标签 */
export const MOOD_LABELS = Object.keys(MOOD_COLORS)
