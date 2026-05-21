<template>
  <header class="masthead">
    <div class="masthead-top">
      <router-link to="/" class="brand-mark">
        <svg class="brand-mark-icon" width="22" height="22" viewBox="0 0 64 64" fill="none" aria-hidden="true">
          <rect x="14" y="11" width="36" height="42" rx="8" stroke="currentColor" stroke-width="4"/>
          <path d="M24 11V53" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
          <path d="M36.25 24.5C34.14 21.86 29.9 22.06 28.02 25.03C26.14 28 27.12 31.93 30.55 34.63L32 35.76L33.45 34.63C36.88 31.93 37.86 28 35.98 25.03C35.89 24.9 35.81 24.76 35.72 24.64C35.89 24.58 36.07 24.53 36.25 24.5ZM36.25 24.5C38.31 21.62 42.78 21.62 44.84 24.5C46.96 27.47 45.98 31.46 42.45 34.23L37.8 37.88" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
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
          <button
            class="nav-checkin-btn"
            :class="{ 'nav-checkin-btn--done': growth.checkedInToday }"
            :disabled="checkingIn || growth.checkedInToday"
            @click="doCheckIn"
          >
            <span class="checkin-text-full">{{ checkingIn ? '...' : growth.checkedInToday ? '✓ 已签' : '签到 +' + checkinExp }}</span>
            <span class="checkin-text-short">{{ checkingIn ? '...' : growth.checkedInToday ? '✓' : '+' + checkinExp }}</span>
          </button>
          <n-popover :show="showQuotaPopover" trigger="click" placement="bottom-end" @update:show="onQuotaPopoverUpdate">
            <template #trigger>
              <n-button text size="small" class="nav-quota-btn">
                额度
              </n-button>
            </template>
            <div style="padding: 4px; min-width: 160px;">
              <p style="margin: 0 0 2px; font-weight: bold; font-size: 13px; color: #2f2a24;">Lv.{{ auth.level }} · {{ auth.exp }}/{{ levelExpCap }} EXP</p>
              <p style="margin: 0 0 8px; font-weight: bold; font-size: 13px; color: #2f2a24;">剩余额度</p>
              <ul style="margin: 0; padding-left: 18px; font-size: 13px; color: #555;">
                <li>AI 聊天：{{ formatQuota(quotas.CHAT, levelQuotaMax.chat) }}</li>
                <li>AI 分析：{{ formatQuota(quotas.ANALYSIS, levelQuotaMax.analysis) }}</li>
                <li>AI 深度思考：{{ formatQuota(quotas.REASONING, levelQuotaMax.reasoning) }}</li>
                <li>共鸣检索：{{ formatQuota(quotas.RESONANCE, levelQuotaMax.resonance) }}</li>
                <li>报告：{{ formatQuota(quotas.REPORT, levelQuotaMax.report) }}</li>
                <li>图片上传：{{ formatQuota(quotas.IMAGE_UPLOAD, levelQuotaMax.imageUpload) }}</li>
              </ul>
              <p style="margin: 8px 0 0; font-size: 11px; color: #888;">AI 聊天/分析/思考 及 共鸣检索/图片每日重置，报告每月重置</p>
              <p style="margin: 6px 0 0; font-size: 12px;">
                <a href="#" @click.prevent="openQuotaTable" style="color: var(--color-jade); font-weight: 600; text-decoration: none;">查看完整配额表 →</a>
              </p>
              <p v-if="quotaError" style="margin: 6px 0 0; font-size: 11px; color: #b15454;">{{ quotaError }}</p>
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
      <span class="mobile-nav-icon" aria-hidden="true">{{ item.icon }}</span>
      <span>{{ item.shortLabel }}</span>
    </router-link>
  </nav>

  <!-- 配额表弹窗 -->
  <Teleport to="body">
    <div v-if="showQuotaTable" class="quota-overlay" @click.self="showQuotaTable = false">
      <div class="quota-modal">
        <div class="quota-modal-header">
          <h3>配额表</h3>
          <button class="quota-modal-close" @click="showQuotaTable = false">&times;</button>
        </div>
        <p class="quota-modal-desc">
          当前：<strong>Lv.{{ auth.level }}</strong>
        </p>
        <div class="quota-table-wrap">
          <table class="quota-table">
            <thead>
              <tr>
                <th>身份 / 等级</th>
                <th>聊天 <span class="quota-unit">/天</span></th>
                <th>分析 <span class="quota-unit">/天</span></th>
                <th>深度思考 <span class="quota-unit">/天</span></th>
                <th>共鸣检索 <span class="quota-unit">/天</span></th>
                <th>报告 <span class="quota-unit">/月</span></th>
                <th>图片上传 <span class="quota-unit">/天</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in quotaTable" :key="row.label" :class="{ 'quota-row-active': row.isCurrent }">
                <td :class="{ 'quota-row-active': row.isCurrent }">{{ row.label }}</td>
                <td>{{ row.chat }}</td>
                <td>{{ row.analysis }}</td>
                <td>{{ row.reasoning }}</td>
                <td>{{ row.resonance }}</td>
                <td>{{ row.report }}</td>
                <td>{{ row.imageUpload }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="quota-modal-footer">聊天 / 分析 / 思考 / 检索每日 0 点重置 · 报告每月 1 日重置 · Lv.6 报告无上限</p>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NButton, NBadge, NPopover } from 'naive-ui'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notification'
import { authApi, growthApi } from '../api'
import { tryExpToast, expToast } from '../utils/toast'

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

const checkinExp = computed(() => {
  const s = growth.value.streak
  if (s >= 6) return 25
  return 10 + s * 2
})

const levelExpCap = computed(() => {
  const lv = auth.level || 1
  if (lv >= 6) return 'MAX'
  return LEVEL_EXP_CAPS[lv]
})

async function fetchGrowth() {
  try {
    const res = await growthApi.status()
    if (res.data.data) growth.value = res.data.data
  } catch { /* ignore */ }
}

async function doCheckIn() {
  checkingIn.value = true
  try {
    const res = await growthApi.checkIn()
    if (res.data.data?.checkedIn) {
      expToast(`签到 +${res.data.data.exp} EXP`)
      await fetchGrowth()
    }
  } catch { /* ignore */ }
  finally { checkingIn.value = false }
}
const profilePath = computed(() => (auth.userId != null ? `/profile/${auth.userId}` : '/login'))

// Lv.1..6
const LEVEL_LABELS = ['Lv.1', 'Lv.2', 'Lv.3', 'Lv.4', 'Lv.5', 'Lv.6']
const QUOTA_DATA = [
  { chat: 15,  analysis: 5,  reasoning: 2,  resonance: 0,  report: 0,  imageUpload: 3 },
  { chat: 25,  analysis: 8,  reasoning: 4,  resonance: 3,  report: 0,  imageUpload: 5 },
  { chat: 35,  analysis: 12, reasoning: 6,  resonance: 5,  report: 2,  imageUpload: 8 },
  { chat: 45,  analysis: 16, reasoning: 8,  resonance: 8,  report: 4,  imageUpload: 12 },
  { chat: 55,  analysis: 20, reasoning: 10, resonance: 10, report: 6,  imageUpload: 16 },
  { chat: 65,  analysis: 25, reasoning: 12, resonance: 12, report: 8,  imageUpload: 20 },
]

const quotaTable = computed(() => {
  return LEVEL_LABELS.map((label, i) => {
    const d = QUOTA_DATA[i]
    const isCurrent = label === `Lv.${auth.level}`
    return {
      label,
      chat: d.chat > 900 ? '不限' : d.chat + '次',
      analysis: d.analysis + '次',
      reasoning: d.reasoning + '次',
      resonance: d.resonance === 0 ? '—' : d.resonance + '次',
      report: d.report === 0 ? '—' : d.report > 900 ? '不限' : d.report + '次',
      imageUpload: d.imageUpload + '次',
      isCurrent,
    }
  })
})

const navItems = computed(() => {
  const items = [
    { label: '广场', shortLabel: '广场', icon: '⌂', path: '/' },
    { label: '写日记', shortLabel: '写', icon: '✎', path: '/write' },
    { label: 'MoodCopilot', shortLabel: 'AI', icon: '◌', path: '/chat', cls: 'nav-link-ai' },
    { label: '关注', shortLabel: '关注', icon: '◎', path: '/following' },
    { label: '报告', shortLabel: '报告', icon: '▥', path: '/report' },
  ]
  if (auth.isAdmin) {
    items.push({ label: '审核', shortLabel: '审核', icon: '!', path: '/admin/reports' })
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
.nav-checkin-btn {
  margin-right: 8px;
  padding: 2px 10px;
  border: 1px solid var(--color-jade);
  border-radius: 12px;
  background: transparent;
  color: var(--color-jade);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s, color 0.15s;
  flex-shrink: 0;
}

.nav-checkin-btn:hover:not(:disabled) {
  background: var(--color-jade);
  color: #fff;
}

.nav-checkin-btn--done {
  border-color: var(--color-border-strong);
  color: var(--color-text-muted);
  cursor: default;
}

.nav-checkin-btn--done:hover {
  background: transparent;
  color: var(--color-text-muted);
}

.nav-checkin-btn:disabled {
  cursor: default;
  opacity: 0.6;
}

.nav-quota-btn {
  margin-right: 8px;
  font-weight: bold;
  color: #496c58;
}

.checkin-text-short { display: none; }

@media (max-width: 600px) {
  .checkin-text-full { display: none; }
  .checkin-text-short { display: inline; }
  .user-level-badge { display: none; }
  .masthead-user-name { display: none; }
  .nav-checkin-btn { margin-right: 5px; padding: 2px 7px; font-size: 11px; }
  .nav-quota-btn { margin-right: 4px; padding: 0 4px; font-size: 12px; }
}

.user-level-badge {
  font-size: 10px;
  font-weight: 700;
  color: var(--color-jade);
  background: color-mix(in srgb, var(--color-jade) 12%, transparent);
  padding: 1px 6px;
  border-radius: 8px;
  flex-shrink: 0;
}

/* quota table modal */
.quota-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.35);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.quota-modal {
  background: #fff;
  border-radius: 14px;
  max-width: 640px;
  width: 100%;
  max-height: 85vh;
  overflow-y: auto;
  padding: 24px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.18);
}

.quota-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.quota-modal-header h3 {
  margin: 0;
  font-size: 18px;
  color: #2f2a24;
}

.quota-modal-close {
  background: none;
  border: none;
  font-size: 24px;
  color: #999;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.quota-modal-desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: #666;
}

.quota-table-wrap {
  overflow-x: auto;
}

.quota-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.quota-table th,
.quota-table td {
  padding: 8px 10px;
  text-align: center;
  border-bottom: 1px solid #eee;
  white-space: nowrap;
}

.quota-table th {
  font-weight: 600;
  color: #444;
  background: #f7f5f2;
  position: sticky;
  top: 0;
}

.quota-table th:first-child,
.quota-table td:first-child {
  text-align: left;
  font-weight: 600;
}

.quota-unit {
  font-weight: 400;
  font-size: 11px;
  color: #999;
}

.quota-row-active {
  background: color-mix(in srgb, var(--color-jade) 8%, transparent);
}

.quota-row-active td:first-child {
  color: var(--color-jade);
}

.quota-modal-footer {
  margin: 14px 0 0;
  font-size: 11px;
  color: #aaa;
  text-align: center;
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
