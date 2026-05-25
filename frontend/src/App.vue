<template>
  <n-config-provider :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN">
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
import { computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { defineComponent } from 'vue'
import { useMessage, useNotification, zhCN, dateZhCN } from 'naive-ui'
import { useDiaryStore } from './stores/diary'
import { useAuthStore } from './stores/auth'
import { useNotificationStore } from './stores/notification'
import type { GlobalThemeOverrides } from 'naive-ui'

const router = useRouter()
const store = useDiaryStore()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()

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

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: 'var(--color-primary)',
    primaryColorHover: '#3d6a52',
    primaryColorPressed: '#345b46',
    primaryColorSuppl: 'var(--color-primary)',
    infoColor: '#b5343a',
    infoColorHover: '#9e2d33',
    infoColorPressed: '#87262c',
    infoColorSuppl: '#b5343a',
    errorColor: '#b5343a',
    errorColorHover: '#9e2d33',
    errorColorPressed: '#87262c',
    successColor: 'var(--color-primary)',
    successColorHover: '#3d6a52',
    successColorPressed: '#345b46',
    warningColor: '#c8843c',
    warningColorHover: '#b87635',
    warningColorPressed: '#a0682d',
  },
  Button: {
    colorPrimary: 'var(--color-primary)',
    colorPrimaryHover: '#3d6a52',
    colorPrimaryPressed: '#345b46',
    textColorPrimary: '#fdfbf7',
    borderRadiusSmall: '4px',
    borderRadiusMedium: '6px',
    borderRadiusLarge: '10px',
  },
  Input: {
    borderFocus: 'var(--color-primary)',
    borderHover: '#d4cdbc',
    borderRadius: '6px',
  },
  Tag: {
    borderRadius: '4px',
  },
  Badge: {
    color: '#b5343a',
  },
}
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
  color: #555;
  line-height: 1.6;
}

.modal-feedback {
  margin: 0 0 20px;
  font-size: 13px;
  color: #777;
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
