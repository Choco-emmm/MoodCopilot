<template>
  <header class="masthead">
    <div class="masthead-top">
      <router-link to="/" class="brand-mark">
        <svg class="brand-mark-icon" width="22" height="22" viewBox="0 0 64 64" fill="none" aria-hidden="true">
          <rect x="14" y="11" width="36" height="42" rx="8" stroke="currentColor" stroke-width="4"/>
          <path d="M24 11V53" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
          <path d="M32 38C26.6 33.8 24 31.1 24 27.5C24 24.95 26 23 28.6 23C30.1 23 31.55 23.68 32.5 24.76C33.45 23.68 34.9 23 36.4 23C39 23 41 24.95 41 27.5C41 31.1 38.4 33.8 33 38L32.5 38.4L32 38Z" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        MoodCopilot
      </router-link>
      <nav class="masthead-nav">
        <template v-if="auth.isAuthenticated">
          <div class="nav-links desktop-only">
            <router-link
              v-for="item in navItems"
              :key="item.path"
              :to="item.path"
              :class="['nav-link', item.cls, { active: route.path === item.path }]"
            >{{ item.label }}</router-link>
          </div>
          <div class="nav-sep desktop-only" />
          <CheckinButton
            :checked-in-today="growth.checkedInToday"
            :checking-in="checkingIn"
            :streak="growth.streak"
            @checkin="doCheckIn"
            @view-tasks="router.push('/task-center')"
          />
          <n-popover :show="showQuotaPopover" trigger="click" placement="bottom-end" @update:show="onQuotaPopoverUpdate">
            <template #trigger>
              <n-button text size="small" class="nav-quota-btn">
                额度
              </n-button>
            </template>
            <div class="quota-popover">
              <p class="quota-popover-title">Lv.{{ auth.level }} · {{ auth.exp }}/{{ levelExpCap }} EXP</p>
              <p class="quota-popover-subtitle">剩余额度</p>
              <ul class="quota-popover-list">
                <li>AI 聊天：{{ formatQuota(quotas.CHAT, levelQuotaMax.chat) }}</li>
                <li>AI 分析：{{ formatQuota(quotas.ANALYSIS, levelQuotaMax.analysis) }}</li>
                <li>AI 深度思考：{{ formatQuota(quotas.REASONING, levelQuotaMax.reasoning) }}</li>
                <li>共鸣检索：{{ formatQuota(quotas.RESONANCE, levelQuotaMax.resonance) }}</li>
                <li>图片上传：{{ formatQuota(quotas.IMAGE_UPLOAD, levelQuotaMax.imageUpload) }}</li>
                <li>图片分析：{{ formatQuota(quotas.IMAGE_ANALYSIS, levelQuotaMax.imageAnalysis) }}</li>
                <li>报告：{{ formatQuota(quotas.REPORT, levelQuotaMax.report) }}</li>
              </ul>
              <p class="quota-popover-hint">AI 聊天 / AI 分析 / 思考 / 检索 / 传图 每日 0 点重置，报告每月重置</p>
              <p class="quota-popover-link-wrap">
                <a href="#" @click.prevent="openQuotaTable" class="quota-popover-link">查看完整配额表 →</a>
              </p>
              <p v-if="quotaError" class="quota-popover-error">{{ quotaError }}</p>
            </div>
          </n-popover>
          <router-link to="/notifications" class="nav-notification-link" aria-label="通知">
            <span class="nav-notification-link-inner">
              <n-badge :value="notif.unreadCount" :max="99" :show="notif.unreadCount > 0">
                <n-button text size="small" class="nav-bell">
                  <template #icon>
                    <span style="font-size: 16px">&#128276;</span>
                  </template>
                </n-button>
              </n-badge>
            </span>
          </router-link>
          <router-link :to="profilePath" class="masthead-user-link">
            <img v-if="auth.avatar" :src="auth.avatar" class="avatar-sm-nav avatar-sm-nav-img" decoding="async" />
            <span v-else class="avatar-sm-nav">{{ auth.displayName?.charAt(0) }}</span>
            <span class="masthead-user-name">{{ auth.displayName }}</span>
            <span class="user-level-badge desktop-only">Lv.{{ auth.level }}</span>
            <span class="user-link-arrow">›</span>
          </router-link>
          <n-button text size="small" class="nav-logout desktop-only" @click="handleLogout">退出</n-button>
        </template>
        <template v-else>
          <n-button text type="primary" @click="router.push('/login')">登录</n-button>
          <n-button text type="primary" @click="router.push('/register')">注册</n-button>
        </template>
      </nav>
    </div>
    <h1 class="desktop-only">写下今天，慢慢理解自己。</h1>
    <p class="subtitle desktop-only">MoodCopilot 先帮你看见情绪；当你愿意时，再把你温和地连接给相似心情的人。</p>
  </header>

  <nav v-if="auth.isAuthenticated" class="mobile-bottom-nav" aria-label="主要导航">
    <router-link
      v-for="item in mobileNavItems"
      :key="`mobile-${item.path}`"
      :to="item.path"
      :class="['mobile-nav-link', { active: route.path === item.path }]"
    >
      <span class="mobile-nav-icon" aria-hidden="true">
        <!-- 广场 Home -->
        <svg v-if="item.path === '/'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          <polyline points="9 22 9 12 15 12 15 22"/>
        </svg>
        <!-- 写日记 Edit -->
        <svg v-else-if="item.path === '/write'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 20h9"/>
          <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>
        </svg>
        <!-- AI Chat Sparkle -->
        <svg v-else-if="item.path === '/chat'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="sparkle-icon">
          <path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/>
        </svg>
        <!-- 关注 Heart -->
        <svg v-else-if="item.path === '/following'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/>
        </svg>
        <!-- 报告 Report Chart -->
        <svg v-else-if="item.path === '/report'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="16" y1="13" x2="8" y2="13"/>
          <line x1="16" y1="17" x2="8" y2="17"/>
          <line x1="10" y1="9" x2="8" y2="9"/>
        </svg>
        <!-- 审核 Admin Reports Shield -->
        <svg v-else-if="item.path === '/admin/reports'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          <line x1="12" y1="9" x2="12" y2="13"/>
          <line x1="12" y1="17" x2="12.01" y2="17"/>
        </svg>
        <!-- 记忆 AI Memory Database -->
        <svg v-else-if="item.path === '/ai-memory'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <ellipse cx="12" cy="5" rx="9" ry="3"/>
          <path d="M3 5v14c0 1.66 4.03 3 9 3s9-1.34 9-3V5"/>
          <path d="M3 12c0 1.66 4.03 3 9 3s9-1.34 9-3"/>
        </svg>
        <!-- 用户 Admin Users Group -->
        <svg v-else-if="item.path === '/admin/users'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
          <circle cx="9" cy="7" r="4"/>
          <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
          <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
        </svg>
        <!-- Default Fallback -->
        <span v-else>{{ item.icon }}</span>
      </span>
      <span class="mobile-nav-label">{{ item.shortLabel }}</span>
    </router-link>
  </nav>

  <!-- 配额表弹窗 -->
  <QuotaTableModal :show="showQuotaTable" :level="auth.level" @close="showQuotaTable = false" />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NButton, NBadge, NPopover } from 'naive-ui'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notification'
import CheckinButton from './CheckinButton.vue'
import QuotaTableModal from './QuotaTableModal.vue'
import { authApi, growthApi } from '../api'
import { tryExpToast, expToast } from '../utils/toast'
import { logWarn } from '../utils/logger'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const notif = useNotificationStore()

const quotas = ref<Record<string, number>>({})
const quotaError = ref('')
const showQuotaPopover = ref(false)
const showQuotaTable = ref(false)

// ── 签到 ──
const checkingIn = ref(false)
const growth = ref({ exp: 0, level: 1, expToNextLevel: 150, streak: 0, monthCheckins: 0, checkedInToday: false })

const LEVEL_EXP_CAPS = [0, 150, 500, 1500, 4000, 10000]

const levelExpCap = computed(() => {
  const lv = auth.level || 1
  if (lv >= 6) return 'MAX'
  return LEVEL_EXP_CAPS[lv]
})

async function fetchGrowth() {
  try {
    const res = await growthApi.status()
    if (res.data.data) {
      const d = res.data.data
      // 后端 streak 不含今天，已签到时 +1
      growth.value = { ...d, streak: d.checkedInToday ? d.streak + 1 : d.streak }
    }
  } catch (e) { logWarn('header', '加载成长数据失败', e) }
}

async function doCheckIn() {
  checkingIn.value = true
  try {
    const res = await growthApi.checkIn()
    if (res.data.data?.checkedIn) {
      expToast(`签到 +${res.data.data.exp} EXP`)
      await fetchGrowth()
    }
  } catch (e) { logWarn('header', '签到失败', e) }
  finally { checkingIn.value = false }
}

const profilePath = computed(() => (auth.userId != null ? `/profile/${auth.userId}` : '/login'))

// Quota popover data
const QUOTA_DATA = [
  { chat: 15,  analysis: 5,  reasoning: 2,  resonance: 0,  report: 0,  imageUpload: 3,  imageAnalysis: 2 },
  { chat: 25,  analysis: 8,  reasoning: 4,  resonance: 3,  report: 2,  imageUpload: 5,  imageAnalysis: 3 },
  { chat: 35,  analysis: 12, reasoning: 6,  resonance: 5,  report: 4,  imageUpload: 8,  imageAnalysis: 5 },
  { chat: 45,  analysis: 16, reasoning: 8,  resonance: 8,  report: 7,  imageUpload: 12, imageAnalysis: 8 },
  { chat: 55,  analysis: 20, reasoning: 10, resonance: 10, report: 11, imageUpload: 16, imageAnalysis: 12 },
  { chat: 65,  analysis: 25, reasoning: 12, resonance: 12, report: 16, imageUpload: 20, imageAnalysis: 15 },
]

const navItems = computed(() => {
  const items = [
    { label: '广场', shortLabel: '广场', icon: '⌂', path: '/' },
    { label: '写日记', shortLabel: '写', icon: '✎', path: '/write' },
    { label: 'MoodCopilot', shortLabel: 'AI', icon: '✦', path: '/chat', cls: 'nav-link-ai' },
    { label: '关注', shortLabel: '关注', icon: '♥', path: '/following' },
    { label: '我的', shortLabel: '我的', icon: '◉', path: profilePath.value },
    { label: '报告', shortLabel: '报告', icon: '▤', path: '/report' },
    { label: 'AI记忆', shortLabel: '记忆', icon: '🏛️', path: '/ai-memory' },
  ]
  if (auth.isAdmin) {
    items.push({ label: '审核', shortLabel: '审核', icon: '!', path: '/admin/reports' })
    items.push({ label: '用户', shortLabel: '用户', icon: '👥', path: '/admin/users' })
  }
  return items
})

const mobileNavItems = computed(() => {
  return [...navItems.value]
})

onMounted(() => {
  notif.connectRealtime()
  void notif.fetchUnreadCount()
  fetchGrowth()
})

function handleLogout() {
  notif.disconnectRealtime()
  auth.logout()
  router.push('/login')
}

function onQuotaPopoverUpdate(show: boolean) {
  showQuotaPopover.value = show
  if (show) {
    quotaError.value = ''
    auth.fetchProfile()
    authApi.getQuota().then(res => {
      quotas.value = res.data.data?.quotas ?? {}
    }).catch((e: any) => {
      quotaError.value = e?.response?.data?.message || '额度加载失败'
    })
  }
}

function openQuotaTable() {
  showQuotaPopover.value = false
  showQuotaTable.value = true
}

const levelQuotaMax = computed(() => {
  const idx = Math.max(0, Math.min(5, (auth.level || 1) - 1))
  return QUOTA_DATA[idx]
})

function formatQuota(val: number | undefined, max?: number): string {
  if (val == null) return '--'
  if (val < 0 || val >= 9999) return '不限'
  if (max === 0) return '—（未解锁）'
  if (max != null && max < 999) return `${val}/${max}`
  if (max != null && max >= 999) return val + ' 次'
  return val + ' 次'
}

</script>

<style scoped>
/* checkin button in nav */
.nav-quota-btn {
  margin-right: 8px;
  font-weight: bold;
  color: var(--color-primary);
}

@media (max-width: 600px) {
  .user-level-badge { display: none; }
  .masthead-user-name { display: none; }
  .nav-quota-btn { margin-right: 4px; padding: 0 4px; font-size: 12px; }
}

.user-level-badge {
  font-size: 10px;
  font-weight: 700;
  color: var(--color-jade);
  background: color-mix(in oklab, var(--color-jade) 12%, transparent);
  padding: 1px 6px;
  border-radius: 8px;
  flex-shrink: 0;
}

/* Quota popover */
.quota-popover {
  padding: 4px;
  min-width: 160px;
  max-width: 260px;
  white-space: normal;
  word-break: break-word;
}
.quota-popover-title {
  margin: 0 0 2px;
  font-weight: bold;
  font-size: 13px;
  color: var(--color-text);
}
.quota-popover-subtitle {
  margin: 0 0 8px;
  font-weight: bold;
  font-size: 13px;
  color: var(--color-text);
}
.quota-popover-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.quota-popover-hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--color-text-light);
}
.quota-popover-link-wrap {
  margin: 6px 0 0;
  font-size: 12px;
}
.quota-popover-link {
  color: var(--color-jade);
  font-weight: 600;
  text-decoration: none;
}
.quota-popover-link:hover {
  color: var(--color-jade-hover);
}
.quota-popover-error {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--color-error);
}

/* daily exp progress strip */
.exp-progress-strip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 18px;
  background: var(--color-surface-soft);
  border-bottom: 1px solid var(--color-border);
  overflow-x: auto;
}

.exp-progress-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.exp-progress-item {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.exp-progress-icon {
  font-size: 10px;
  width: 10px;
  color: var(--color-jade);
}

.exp-progress-name {
  font-size: 11px;
  color: var(--color-text-secondary);
  width: 32px;
  flex-shrink: 0;
}

.exp-progress-bar-bg {
  width: 40px;
  height: 5px;
  border-radius: 3px;
  background: var(--color-border);
  flex-shrink: 0;
  overflow: hidden;
}

.exp-progress-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--color-jade);
  transition: width 0.3s;
}

.exp-progress-num {
  font-size: 10px;
  color: var(--color-text-muted);
  width: 28px;
  flex-shrink: 0;
}
</style>
