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

export const themeStyle = computed(() => {
  const theme = currentTheme.value;
  return `--theme-primary: ${theme.primary}; --theme-primary-rgb: ${hexToRgb(theme.primary)}; --theme-accent: ${theme.accent}; --theme-bg: ${theme.bg}; --theme-surface: ${theme.surface};`;
});

export const setThemeMode = (mode: 'light' | 'dark' | 'auto') => {
  themeMode.value = mode;
  uni.setStorageSync('themeMode', mode);
  uni.$emit('themeChanged');
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
