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
export const defaultDarkTheme = ref<string>(uni.getStorageSync('defaultDarkTheme') || 'dark_green');
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
  
  const textPrimary = isDark ? 'rgba(255, 255, 255, 0.9)' : '#20201d';
  const textSecondary = isDark ? 'rgba(255, 255, 255, 0.6)' : '#666666';
  const textPlaceholder = isDark ? 'rgba(255, 255, 255, 0.3)' : '#999999';
  const borderCol = isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.05)';

  themeStyle.value = `--theme-primary: ${theme.primary}; --theme-primary-rgb: ${hexToRgb(theme.primary)}; --theme-accent: ${theme.accent}; --theme-bg: ${theme.bg}; --theme-surface: ${theme.surface}; --theme-surface-rgb: ${hexToRgb(theme.surface)}; --theme-text-primary: ${textPrimary}; --theme-text-secondary: ${textSecondary}; --theme-text-placeholder: ${textPlaceholder}; --theme-border: ${borderCol};`;
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
    uni.setNavigationBarColor({
      frontColor: currentTheme.value.dark ? '#ffffff' : '#000000',
      backgroundColor: currentTheme.value.bg,
      animation: { duration: 200, timingFunc: 'easeInOut' }
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
