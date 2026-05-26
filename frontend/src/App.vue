<template>
  <n-config-provider :theme="naiveTheme" :theme-overrides="dynamicThemeOverrides" :locale="zhCN" :date-locale="dateZhCN">
    <n-notification-provider>
      <n-message-provider>
        <MessageEnvironment />
        <router-view v-slot="{ Component, route }">
          <keep-alive>
            <component :is="Component" v-if="route.meta.keepAlive" :key="route.name" />
          </keep-alive>
          <component :is="Component" v-if="!route.meta.keepAlive" :key="route.fullPath" />
        </router-view>

        <n-modal :show="store.showGlobalAnalysisModal" :mask-closable="false" @update:show="onGlobalModalUpdate">
          <div class="analysis-modal">
            <div class="modal-header">
              <h3>分析完成</h3>
              <button class="modal-close" @click="store.closeAnalysisModal()">&times;</button>
            </div>
            <template v-if="store.globalAnalysisDiary?.analysis">
              <div class="modal-mood">
                <n-tag :type="moodTagType(store.globalAnalysisDiary.analysis.moodLabel)" size="medium">
                  {{ store.globalAnalysisDiary.analysis.moodLabel }}
                </n-tag>
                <span class="mood-intensity">强度 {{ '★'.repeat(store.globalAnalysisDiary.analysis.moodIntensity) }}{{ '☆'.repeat(5 - store.globalAnalysisDiary.analysis.moodIntensity) }}</span>
              </div>
              <template v-if="store.globalAnalysisDiary.analysis.secondaryMoods?.length">
                <div class="modal-secondary">
                  <n-tag v-for="m in store.globalAnalysisDiary.analysis.secondaryMoods" :key="m" size="small" :bordered="true">
                    {{ m }}
                  </n-tag>
                </div>
              </template>
              <p class="modal-summary">{{ store.globalAnalysisDiary.analysis.summary }}</p>
              <p class="modal-feedback">{{ truncatedFeedback }}</p>
            </template>
            <div class="modal-actions">
              <n-button @click="store.closeAnalysisModal()">关闭</n-button>
              <n-button type="primary" @click="goToDetail">查看完整分析</n-button>
            </div>
          </div>
        </n-modal>
      </n-message-provider>
    </n-notification-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { computed, watch, watchEffect } from 'vue'
import { useRouter } from 'vue-router'
import { defineComponent } from 'vue'
import { useMessage, useNotification, zhCN, dateZhCN, useOsTheme, darkTheme } from 'naive-ui'
import { useDiaryStore } from './stores/diary'
import { useAuthStore } from './stores/auth'
import { useNotificationStore } from './stores/notification'
import type { GlobalThemeOverrides } from 'naive-ui'
import { themeOptions } from './constants/theme'

const router = useRouter()
const store = useDiaryStore()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()
const osTheme = useOsTheme()

const MessageEnvironment = defineComponent({
  setup() {
    window.$message = useMessage()
    window.$notification = useNotification()
    return () => null
  },
})

function onGlobalModalUpdate(show: boolean) {
  if (!show) store.closeAnalysisModal()
}

function goToDetail() {
  if (store.globalAnalysisDiary) {
    router.push('/diary/' + store.globalAnalysisDiary.id)
  }
  store.closeAnalysisModal()
}

const truncatedFeedback = computed(() => {
  const fb = store.globalAnalysisDiary?.analysis?.feedback
  if (!fb) return ''
  return fb.length > 120 ? fb.slice(0, 120) + '...' : fb
})

function moodTagType(mood: string) {
  const positive = ['喜悦', '期待', '兴奋', '自豪', '轻松', '平静', '感恩', '满足']
  return positive.includes(mood) ? 'success' as const : 'warning' as const
}

watch(() => authStore.token, (newToken, oldToken) => {
  if (newToken) {
    if (newToken !== oldToken) {
      notificationStore.disconnectRealtime()
      notificationStore.fetchUnreadCount()
      notificationStore.connectRealtime()
    }
  } else {
    notificationStore.disconnectRealtime()
  }
})

const isDark = computed(() => osTheme.value === 'dark')

const activeThemeName = computed(() => {
  // themeMode: 'auto' 跟随系统, 'light' 强制白天, 'dark' 强制夜间
  if (authStore.themeMode === 'light') return authStore.lightTheme || 'green'
  if (authStore.themeMode === 'dark') return authStore.darkTheme || 'minimal-dark'
  // auto: 跟随系统
  return isDark.value
    ? (authStore.darkTheme || 'minimal-dark')
    : (authStore.lightTheme || 'green')
})

watchEffect(() => {
  document.documentElement.setAttribute('data-theme', activeThemeName.value)
  const currentTheme = themeOptions.find(t => t.value === activeThemeName.value) || themeOptions[0]
  
  // 注入我们 19 套主题的色彩变量，以驱动无边框排版
  const root = document.documentElement
  root.style.setProperty('--theme-primary', currentTheme.primary)
  root.style.setProperty('--theme-accent', currentTheme.accent)
  root.style.setProperty('--theme-bg', currentTheme.bg)
  root.style.setProperty('--theme-surface', currentTheme.surface)

  const metaThemeColor = document.querySelector('meta[name="theme-color"]')
  if (metaThemeColor) {
    metaThemeColor.setAttribute('content', currentTheme.bg)
  }
})

const naiveTheme = computed(() => {
  if (authStore.themeMode === 'dark') return darkTheme
  if (authStore.themeMode === 'light') return null
  return isDark.value ? darkTheme : null
})

const dynamicThemeOverrides = computed<GlobalThemeOverrides>(() => {
  const current = themeOptions.find(t => t.value === activeThemeName.value) || themeOptions[0]
  const isBlackRice = activeThemeName.value === 'black-rice'
  const isMinimalDark = activeThemeName.value === 'minimal-dark'
  const isDarkTheme = isBlackRice || isMinimalDark
  return {
    common: {
      primaryColor: current.primary,
      infoColor: current.accent,
      successColor: current.primary,
      errorColor: isDarkTheme ? '#e06060' : '#b23a3a',
      warningColor: isDarkTheme ? '#c8983e' : '#d49200',
      ...(isDarkTheme ? {
        bodyColor: isMinimalDark ? '#0e0e0e' : '#2b2b29',
        cardColor: isMinimalDark ? '#1a1a1a' : '#3a3a37',
        modalColor: isMinimalDark ? '#1a1a1a' : '#3a3a37',
        popoverColor: isMinimalDark ? '#1a1a1a' : '#3a3a37',
        tableColor: isMinimalDark ? '#1a1a1a' : '#3a3a37',
        inputColor: isMinimalDark ? '#1a1a1a' : '#3a3a37',
        actionColor: isMinimalDark ? '#1e1e1e' : '#474744',
        borderColor: isMinimalDark ? '#282828' : '#666663',
        dividerColor: isMinimalDark ? '#222222' : '#555552',
        hoverColor: isMinimalDark ? 'rgba(138, 142, 150, 0.08)' : 'rgba(255, 180, 0, 0.08)',
        pressedColor: isMinimalDark ? 'rgba(138, 142, 150, 0.12)' : 'rgba(255, 180, 0, 0.12)',
      } : {}),
    },
    Button: {
      textColorPrimary: isDarkTheme ? '#e6e6e6' : '#ffffff',
      borderRadiusSmall: '4px',
      borderRadiusMedium: '6px',
      borderRadiusLarge: '10px',
    },
    Input: {
      borderRadius: '6px',
      color: 'var(--color-surface-hover)',
      colorFocus: 'var(--color-surface)',
    },
    Tag: {
      borderRadius: '4px',
    },
  }
})
</script>

<style scoped>
.analysis-modal {
  background: var(--color-surface);
  border-radius: 12px;
  padding: 24px;
  max-width: 420px;
  margin: 0 auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--color-text);
}

.modal-close {
  background: none;
  border: none;
  font-size: 22px;
  color: var(--color-text-light);
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.modal-close:hover {
  color: var(--color-text);
}

.modal-mood {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.mood-intensity {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.modal-secondary {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.modal-summary {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.modal-feedback {
  margin: 0 0 20px;
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 1.6;
  padding: 10px 12px;
  background: var(--color-surface-hover);
  border-radius: 8px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
