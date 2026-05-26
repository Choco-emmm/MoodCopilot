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
  return p !== '/task-center' && p !== '/login' && p !== '/register'
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
  return {
    common: {
      primaryColor: current.primary,
      infoColor: current.accent,
      successColor: current.primary,
      errorColor: 'var(--color-error)',
      warningColor: 'var(--color-warning)',
      ...(isDarkTheme ? {
        bodyColor: 'var(--color-bg)',
        cardColor: 'var(--color-surface)',
        modalColor: 'var(--color-surface)',
        popoverColor: 'var(--color-surface)',
        tableColor: 'var(--color-surface)',
        inputColor: 'var(--color-surface-hover)',
        actionColor: 'var(--color-surface-hover)',
        borderColor: 'var(--color-border)',
        dividerColor: 'var(--color-border)',
        hoverColor: 'var(--color-surface-hover)',
        pressedColor: 'var(--color-surface-soft)',
      } : {}),
    },
    Button: {
      textColor: 'var(--color-text)',
      textColorPrimary: 'var(--color-on-primary)',
      textColorHover: 'var(--color-primary)',
      border: '1px solid var(--color-border)',
      borderHover: '1px solid var(--color-primary)',
      borderRadiusSmall: '4px',
      borderRadiusMedium: '6px',
      borderRadiusLarge: '10px',
    },
    Input: {
      borderRadius: '6px',
      color: 'var(--color-surface-hover)',
      colorFocus: 'var(--color-surface)',
      border: '1px solid var(--color-border)',
      borderFocus: '1px solid var(--color-primary)',
      borderHover: '1px solid var(--color-primary-hover)',
    },
    Switch: {
      railColorActive: 'var(--color-primary)',
      buttonColor: 'var(--color-surface)'