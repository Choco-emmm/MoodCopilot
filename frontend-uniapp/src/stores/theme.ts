import { ref, computed } from 'vue';
import { themeOptions } from '../constants/theme';
import type { ThemeOption } from '../constants/theme';
export { themeOptions };

const hexToRgb = (hex: string) => {
  hex = hex.replace('#', '');
  if (hex.length === 3) {
    hex = hex.split('').map(char => char + char).join('');
  }
  const r = parseInt(hex.substring(0, 2), 16);
  const g = parseInt(hex.substring(2, 4), 16);
  const b = parseInt(hex.substring(4, 6), 16);
  return `${r}, ${g}, ${b}`;
};

export const themeMode = ref<'light' | 'dark' | 'auto'>(uni.getStorageSync('themeMode') || 'auto');
export const defaultLightTheme = ref<string>(uni.getStorageSync('defaultLightTheme') || 'green');
export const defaultDarkTheme = ref<string>(uni.getStorageSync('defaultDarkTheme') || 'minimal-dark');
export const systemTheme = ref<string>(uni.getSystemInfoSync().theme || 'light');

uni.onThemeChange((res) => {
  systemTheme.value = res.theme || 'light';
});

export const currentTheme = computed<ThemeOption>(() => {
  let isDark = false;
  if (themeMode.value === 'auto') {
    isDark = systemTheme.value === 'dark';
  } else {
    isDark = themeMode.value === 'dark';
  }

  const targetValue = isDark ? defaultDarkTheme.value : defaultLightTheme.value;
  return themeOptions.find(t => t.value === targetValue) || themeOptions[0];
});

import { watch } from 'vue';

export const themeStyle = ref('');

const updateThemeStyle = () => {
  const theme = currentTheme.value;
  const isDark = theme.dark || false;
  
  const textPrimary = isDark
    ? 'color-mix(in oklab, var(--theme-surface) 88%, var(--theme-primary))'
    : 'color-mix(in oklab, var(--theme-primary) 82%, var(--theme-bg))';
  const textSecondary = isDark
    ? 'color-mix(in oklab, var(--theme-surface) 58%, var(--theme-primary))'
    : 'color-mix(in oklab, var(--theme-primary) 55%, var(--theme-bg))';
  const textPlaceholder = isDark
    ? 'color-mix(in oklab, var(--theme-surface) 34%, var(--theme-primary))'
    : 'color-mix(in oklab, var(--theme-primary) 34%, var(--theme-bg))';
  const borderCol = 'color-mix(in oklab, var(--theme-primary) 16%, transparent)';
  const onPrimary = isDark ? 'var(--theme-bg)' : 'var(--theme-surface)';

  // Keep the divider as a concrete theme color because native mini-program WebViews may skip color-mix() in border declarations.
  themeStyle.value = `--theme-primary: ${theme.primary}; --theme-primary-rgb: ${hexToRgb(theme.primary)}; --theme-accent: ${theme.accent}; --theme-bg: ${theme.bg}; --theme-surface: ${theme.surface}; --theme-surface-rgb: ${hexToRgb(theme.surface)}; --theme-surface-hover: color-mix(in oklab, var(--theme-primary) 6%, var(--theme-surface)); --theme-text-primary: ${textPrimary}; --theme-text-secondary: ${textSecondary}; --theme-text-placeholder: ${textPlaceholder}; --theme-border: ${borderCol}; --theme-divider: ${theme.primary}; --theme-text-on-primary: ${onPrimary}; --theme-overlay: color-mix(in oklab, var(--theme-primary) 45%, transparent); --theme-page-padding: 40rpx; --theme-section-gap: 56rpx; --theme-content-gap: 16rpx; --theme-radius-sm: 6rpx; --theme-radius-md: 8rpx; --theme-radius-lg: 12rpx; --theme-shadow-panel: 0 1rpx 8rpx color-mix(in oklab, var(--theme-primary) 5%, transparent); --theme-shadow-dialog: 0 16rpx 48rpx color-mix(in oklab, var(--theme-primary) 16%, transparent);`;
};

watch(currentTheme, () => {
  updateThemeStyle();
}, { immediate: true, flush: 'sync' });

export const setThemeMode = (mode: 'light' | 'dark' | 'auto') => {
  themeMode.value = mode;
  uni.setStorageSync('themeMode', mode);
  uni.$emit('themeChanged');
};

export const syncNavigationBarColor = () => {
  try {
    const backgroundColor = currentTheme.value.bg;
    uni.setBackgroundColor({
      backgroundColor,
      backgroundColorTop: backgroundColor,
      backgroundColorBottom: backgroundColor
    });
    uni.setNavigationBarColor({
      // WeChat's native API accepts only black/white navigation text. This is
      // the platform adapter boundary; page styles still consume theme tokens.
      frontColor: currentTheme.value.dark ? '#ffffff' : '#000000',
      backgroundColor,
      animation: { duration: 0, timingFunc: 'linear' }
    });
  } catch (e) {
    // Ignore
  }
};

export const setSpecificTheme = (themeValue: string, isDarkTheme: boolean) => {
  if (isDarkTheme) {
    defaultDarkTheme.value = themeValue;
    uni.setStorageSync('defaultDarkTheme', themeValue);
  } else {
    defaultLightTheme.value = themeValue;
    uni.setStorageSync('defaultLightTheme', themeValue);
  }
  uni.$emit('themeChanged');
};

