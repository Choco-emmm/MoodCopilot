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

        <!-- 全局任务中心 FAB -->
        <router-link
          v-if="showTaskFab"
          to="/task-center"
          class="global-task-fab"
          :class="{ 'has-dot': !taskStore.checkInState.todaySigned }"
          title="任务中心"
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
            <line x1="3" y1="9" x2="21" y2="9"/>
            <line x1="9" y1="21" x2="9" y2="9"/>
          </svg>
        </router-link>

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
        <GlobalConsolidationModals />
      </n-message-provider>
    </n-notification-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { computed, watch, watchEffect, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { defineComponent } from 'vue'
import { useMessage, useNotification, zhCN, dateZhCN, useOsTheme, darkTheme } from 'naive-ui'
import { useDiaryStore } from './stores/diary'
import { useAuthStore } from './stores/auth'
import { useNotificationStore } from './stores/notification'
import { useTaskStore } from './stores/task'
import GlobalConsolidationModals from './components/GlobalConsolidationModals.vue'
import type { GlobalThemeOverrides } from 'naive-ui'
import { themeOptions } from './constants/theme'

const router = useRouter()
const route = useRoute()
const store = useDiaryStore()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()
const taskStore = useTaskStore()
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

onMounted(() => {
  if (authStore.isAuthenticated) {
    taskStore.fetchCheckInStatus()
  }
})

const isDark = computed(() => osTheme.value === 'dark')

/** 全局任务 FAB：在任务中心页自身不显示 */
const showTaskFab = computed(() => {
  const p = route.path
  return p !== '/task-center' && p !== '/login' && p !== '/register' && p !== '/chat'
})

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

  // Naive UI 的 seemly 库无法解析 CSS 变量，必须传入真实 hex 颜色
  // errorColor/warningColor 使用与主题 accent 颜色协调的固定值
  const errorColor = current.accent   // accent 通常是暖色，适合 error/warning
  const warningColor = isDarkTheme ? '#e6a817' : '#d97706'

  return {
    common: {
      primaryColor: current.primary,
      infoColor: current.accent,
      successColor: current.primary,
      errorColor,
      warningColor,
      ...(isDarkTheme ? {
        bodyColor: current.bg,
        cardColor: current.surface,
        modalColor: current.surface,
        popoverColor: current.surface,
        tableColor: current.surface,
        inputColor: current.surface,
        actionColor: current.surface,
        borderColor: '#333333',
        dividerColor: '#333333',
        hoverColor: '#333333',
        pressedColor: '#222222',
      } : {}),
    },
    Button: {
      borderRadiusSmall: '4px',
      borderRadiusMedium: '6px',
      borderRadiusLarge: '10px',
    },
    Input: {
      borderRadius: '6px',
      color: isDarkTheme ? '#333333' : '#fafafa',
      colorFocus: current.surface,
      border: '1px solid ' + (isDarkTheme ? '#333333' : '#e0e0e0'),
      borderFocus: '1px solid ' + current.primary,
      borderHover: '1px solid ' + current.primary,
    },
    Switch: {
      railColorActive: current.primary,
      buttonColor: current.surface,
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

.modal-actions :deep(.n-button:not(.n-button--primary-type)) {
  --n-text-color-hover: var(--color-primary) !important;
  --n-border-hover: 1px solid var(--color-primary) !important;
  --n-text-color-focus: var(--color-primary) !important;
  --n-border-focus: 1px solid var(--color-primary) !important;
  --n-text-color-pressed: var(--color-primary-hover) !important;
  --n-border-pressed: 1px solid var(--color-primary-hover) !important;
}

/* ── 全局任务中心 FAB ── */
.global-task-fab {
  position: fixed;
  right: max(20px, calc((100vw - 1080px) / 2 + 20px));
  bottom: 32px;
  z-index: 8000;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--color-primary);
  color: var(--color-on-primary);
  box-shadow: 0 4px 20px color-mix(in oklab, var(--color-primary) 36%, transparent);
  text-decoration: none;
  transition: transform 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out);
}

.global-task-fab::after {
  content: '';
  position: absolute;
  top: 4px;
  right: 4px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-accent);
  border: 2px solid var(--color-on-primary);
  opacity: 0;
  transform: scale(0);
  transition: opacity 0.2s, transform 0.2s var(--ease-out);
}

.global-task-fab.has-dot::after {
  opacity: 1;
  transform: scale(1);
  animation: fab-dot-pulse 2s ease-in-out infinite;
}

@keyframes fab-dot-pulse {
  0%, 100% { box-shadow: 0 0 0 0 color-mix(in oklab, var(--color-accent) 40%, transparent); }
  50% { box-shadow: 0 0 0 4px color-mix(in oklab, var(--color-accent) 0%, transparent); }
}

.global-task-fab:hover {
  transform: translateY(-2px) scale(1.06);
  box-shadow: 0 6px 28px color-mix(in oklab, var(--color-primary) 45%, transparent);
}

.global-task-fab:active {
  transform: scale(0.95);
}

@media (max-width: 780px) {
  .global-task-fab {
    right: 16px;
    bottom: calc(76px + env(safe-area-inset-bottom));
    width: 44px;
    height: 44px;
  }
}
</style>
