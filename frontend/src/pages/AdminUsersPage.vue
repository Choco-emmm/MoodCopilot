<template>
  <main class="app-shell">
    <AppHeader />

    <div class="admin-page">
      <div class="admin-page-head">
        <div>
          <p class="eyebrow">ADMIN</p>
          <h2>用户管理</h2>
        </div>
        <div class="admin-actions">
          <n-input
            v-model:value="searchKeyword"
            placeholder="搜索用户名或邮箱..."
            clearable
            @keydown.enter="onSearch"
          >
            <template #suffix>
              <n-button text type="primary" @click="onSearch">搜索</n-button>
            </template>
          </n-input>
        </div>
      </div>

      <div v-if="!auth.isAdmin" class="empty-state">
        <p>当前账号没有管理权限。</p>
      </div>

      <div v-else>
        <div class="admin-filter-bar">
          <div class="sort-options">
            <span class="filter-label">排序方式：</span>
            <button
              :class="['sort-btn', { active: sortBy === 'lastActiveTime' }]"
              @click="changeSort('lastActiveTime')"
            >
              最后活跃
            </button>
            <button
              :class="['sort-btn', { active: sortBy === 'createdAt' }]"
              @click="changeSort('createdAt')"
            >
              注册时间
            </button>
          </div>
        </div>

        <div class="admin-users-list">
        <!-- Desktop Table View -->
        <div class="desktop-only">
          <n-data-table
            remote
            :columns="columns"
            :data="users"
            :loading="loading"
            :pagination="pagination"
            @update:page="handlePageChange"
            :scroll-x="1100"
          />
        </div>

        <!-- Mobile Card View -->
        <div class="mobile-only">
          <div v-if="loading && users.length === 0" class="mobile-loading">
            加载中...
          </div>
          <div v-else-if="users.length === 0" class="empty-state">
            <p>暂无数据</p>
          </div>
          <div v-else>
            <TransitionGroup name="user-list" tag="div" class="mobile-cards">
              <div v-for="user in users" :key="user.id" class="user-card-premium">
                <!-- Card Header: Avatar & Name & Status -->
                <div class="uc-header">
                  <div class="uc-avatar" :style="user.avatar ? {} : getAvatarStyle(user.displayName || '')">
                    <img v-if="user.avatar" :src="user.avatar" class="avatar-img" loading="lazy" decoding="async" />
                    <template v-else>
                      {{ (user.displayName || '')?.charAt(0).toUpperCase() }}
                    </template>
                  </div>
                  <div class="uc-info">
                    <div class="uc-name-row">
                      <span class="uc-name">{{ user.displayName }}</span>
                      <span class="uc-status-badge" :class="user.status === 1 ? 'status-active' : 'status-banned'">
                        {{ user.status === 1 ? '正常' : '已封禁' }}
                      </span>
                    </div>
                    <span class="uc-email">{{ user.email }}</span>
                  </div>
                  <span class="uc-id-badge">#{{ user.id }}</span>
                </div>

                <!-- Card Body: Metadata flex rows with custom SVGs -->
                <div class="uc-body">
                  <div class="uc-meta-row">
                    <span class="uc-meta-label">
                      <svg class="meta-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="10"></circle>
                        <polyline points="12 6 12 12 16 14"></polyline>
                      </svg>
                      最后活跃
                    </span>
                    <span class="uc-meta-value">{{ formatRelativeTime(user.lastActiveTime) }}</span>
                  </div>
                  <div class="uc-meta-row">
                    <span class="uc-meta-label">
                      <svg class="meta-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                        <line x1="16" y1="2" x2="16" y2="6"></line>
                        <line x1="8" y1="2" x2="8" y2="6"></line>
                        <line x1="3" y1="10" x2="21" y2="10"></line>
                      </svg>
                      注册时间
                    </span>
                    <span class="uc-meta-value">{{ formatTime(user.createdAt) }}</span>
                  </div>
                </div>

                <!-- Card Actions -->
                <div class="uc-actions">
                  <n-button 
                    size="medium" 
                    round 
                    secondary 
                    type="primary" 
                    class="action-btn-pill" 
                    @click="goToProfile(user.id)"
                  >
                    查看主页
                  </n-button>
                  <n-button
                    v-if="user.id !== auth.userId && user.role !== 'ADMIN'"
                    size="medium"
                    round
                    :type="user.status === 1 ? 'error' : 'success'"
                    ghost
                    class="action-btn-pill"
                    @click="toggleStatus(user)"
                  >
                    {{ user.status === 1 ? '封禁' : '解禁' }}
                  </n-button>
                </div>
              </div>
            </TransitionGroup>
            
            <div class="mobile-pagination-premium">
              <n-pagination
                v-model:page="pageNum"
                :page-size="pageSize"
                :item-count="total"
                @update:page="handlePageChange"
                simple
              />
            </div>
          </div>
        </div>
      </div>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NDataTable, NInput, NPagination, NTag, useMessage } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { adminApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { normalizeResourceUrl } from '../utils/resource'

const router = useRouter()
const message = useMessage()
const auth = useAuthStore()

type UserInfo = {
  id: number
  displayName: string
  email: string
  status: number
  role: string
  lastActiveTime: string | null
  createdAt: string
  avatar: string | null
}

const users = ref<UserInfo[]>([])
const loading = ref(false)
const searchKeyword = ref('')
const sortBy = ref('lastActiveTime')
const total = ref(0)
const pageNum = ref(1)
const pageSize = 20

const pagination = computed(() => ({
  page: pageNum.value,
  pageSize: pageSize,
  itemCount: total.value,
  onChange: (page: number) => handlePageChange(page)
}))

const columns = [
  {
    title: 'ID',
    key: 'id',
    width: 80
  },
  {
    title: '用户名',
    key: 'displayName',
    width: 200,
    render(row: UserInfo) {
      const hasAvatar = !!row.avatar
      const avatarStyle = hasAvatar ? {} : getAvatarStyle(row.displayName || '')
      const avatarElement = hasAvatar
        ? h('img', { src: row.avatar!, class: 'table-avatar-img' })
        : h('div', { class: 'table-avatar-placeholder', style: avatarStyle }, { default: () => (row.displayName || '')?.charAt(0).toUpperCase() })
      const textElement = h('span', { class: 'table-username-text' }, { default: () => row.displayName })
      return h('div', { class: 'table-user-cell' }, [avatarElement, textElement])
    }
  },
  {
    title: '邮箱',
    key: 'email',
    width: 200
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render(row: UserInfo) {
      if (row.status === 1) {
        return h(NTag, { type: 'success', size: 'small' }, { default: () => '正常' })
      } else {
        return h(NTag, { type: 'error', size: 'small' }, { default: () => '已封禁' })
      }
    }
  },
  {
    title: '最后活跃',
    key: 'lastActiveTime',
    width: 160,
    render(row: UserInfo) {
      return formatRelativeTime(row.lastActiveTime)
    }
  },
  {
    title: '注册时间',
    key: 'createdAt',
    width: 160,
    render(row: UserInfo) {
      return formatTime(row.createdAt)
    }
  },
  {
    title: '操作',
    key: 'actions',
    width: 250,
    render(row: UserInfo) {
      const btnView = h(NButton, {
        size: 'small',
        tertiary: true,
        onClick: () => goToProfile(row.id)
      }, { default: () => '查看主页' })

      const isSelfOrAdmin = row.id === auth.userId || row.role === 'ADMIN'
      if (isSelfOrAdmin) {
        return [btnView]
      }

      const btnBan = h(NButton, {
        size: 'small',
        type: row.status === 1 ? 'error' : 'success',
        ghost: true,
        style: { marginLeft: '8px' },
        onClick: () => toggleStatus(row)
      }, { default: () => (row.status === 1 ? '封禁' : '解禁') })

      return [btnView, btnBan]
    }
  }
]

onMounted(async () => {
  await auth.fetchProfile()
  if (auth.isAdmin) {
    await loadUsers()
  }
})

async function loadUsers() {
  if (!auth.isAdmin) return
  loading.value = true
  try {
    const res = await adminApi.users(searchKeyword.value, sortBy.value, pageNum.value, pageSize)
    const data = res.data.data
    users.value = (data.items ?? [])
      .filter((u: any) => u.role !== 'ADMIN')
      .map((u: any) => ({
        ...u,
        avatar: normalizeResourceUrl(u.avatar)
      }))
    total.value = data.total ?? 0
  } catch (e: any) {
    message.error('加载用户失败')
  } finally {
    loading.value = false
  }
}

function changeSort(field: string) {
  sortBy.value = field
  pageNum.value = 1
  loadUsers()
}

function onSearch() {
  pageNum.value = 1
  loadUsers()
}

function handlePageChange(page: number) {
  pageNum.value = page
  loadUsers()
}

function goToProfile(userId: number) {
  router.push(`/profile/${userId}`)
}

async function toggleStatus(user: UserInfo) {
  const newStatus = user.status === 1 ? 0 : 1
  const actionName = newStatus === 1 ? '解禁' : '封禁'
  if (!window.confirm(`确定要${actionName}该用户吗？`)) {
    return
  }

  try {
    await adminApi.updateUserStatus(user.id, newStatus)
    message.success(`${actionName}成功`)
    await loadUsers()
  } catch (e: any) {
    message.error(`${actionName}失败`)
  }
}

function formatTime(value?: string | null) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function formatRelativeTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffSec = Math.floor(diffMs / 1000)
  const diffMin = Math.floor(diffSec / 60)
  const diffHour = Math.floor(diffMin / 60)
  const diffDay = Math.floor(diffHour / 24)

  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  if (diffHour < 24) return `${diffHour}小时前`
  if (diffDay === 1) return '昨天'
  if (diffDay < 30) return `${diffDay}天前`
  if (diffDay < 365) return `${Math.floor(diffDay / 30)}个月前`
  return `${Math.floor(diffDay / 365)}年前`
}

function getAvatarStyle(name: string) {
  const colors = [
    ['#a3b899', '#7f9c73'], // Soft Sage/Jade
    ['#dfa29c', '#c17a72'], // Dusty Rose/Terracotta
    ['#e6cb9d', '#caa368'], // Soft Amber/Sand
    ['#b2afc2', '#8985a0'], // Dusty Lavender/Slate
    ['#9cbfb8', '#739e95'], // Cozy Teal/Ocean
    ['#cab8a6', '#9c8874'], // Warm Earth/Oatmeal
  ]
  let hash = 0
  if (name) {
    for (let i = 0; i < name.length; i++) {
      hash = name.charCodeAt(i) + ((hash << 5) - hash)
    }
  }
  const index = Math.abs(hash) % colors.length
  const [start, end] = colors[index]
  return {
    background: `linear-gradient(135deg, ${start} 0%, ${end} 100%)`,
    color: '#ffffff',
    textShadow: '0 1px 2px rgba(0,0,0,0.15)'
  }
}
</script>

<style scoped>
.admin-page {
  padding: var(--pad, 16px);
  max-width: 1000px;
  margin: 0 auto;
  padding-bottom: 90px;
}

.admin-page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  gap: 16px;
}

.admin-actions {
  width: 250px;
}

/* Custom styling for Naive UI input to match jade/warm theme */
:deep(.admin-actions .n-input) {
  border-radius: 20px !important;
  background-color: var(--color-surface) !important;
  box-shadow: var(--shadow-sm);
  transition: all var(--duration-normal) var(--ease-out) !important;
}

:deep(.admin-actions .n-input .n-input__border) {
  border-color: var(--color-border) !important;
}

:deep(.admin-actions .n-input:hover .n-input__border) {
  border-color: var(--color-primary-light) !important;
}

:deep(.admin-actions .n-input--focus .n-input__border) {
  border-color: var(--color-primary) !important;
  box-shadow: 0 0 0 2px rgba(74, 124, 98, 0.12) !important;
}

.admin-users-list {
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  padding: 24px;
  box-shadow: var(--shadow-md);
  border: 1px solid var(--color-border);
  max-width: 100%;
  overflow-x: auto;
}

.desktop-only {
  display: block;
}

.mobile-only {
  display: none;
}

@media (max-width: 768px) {
  .admin-page {
    display: block;
    padding: 16px 0 90px;
  }

  .admin-page-head {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
    margin-bottom: 20px;
  }
  .admin-actions {
    width: 100%;
  }
  
  .desktop-only {
    display: none;
  }
  
  .mobile-only {
    display: block;
  }
  
  .admin-users-list {
    background: transparent !important;
    border: none !important;
    padding: 0 !important;
    box-shadow: none !important;
  }
}

/* Mobile cards styling */
.mobile-cards {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.user-card-premium {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  padding: 16px;
  box-shadow: 0 4px 16px rgba(74, 124, 98, 0.03), 0 1px 3px rgba(74, 124, 98, 0.01);
  border: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 14px;
  transition: transform var(--duration-normal) var(--ease-out), 
              box-shadow var(--duration-normal) var(--ease-out), 
              border-color var(--duration-normal) var(--ease-out);
}

.user-card-premium:hover, .user-card-premium:active {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(74, 124, 98, 0.07), 0 2px 6px rgba(74, 124, 98, 0.02);
  border-color: var(--color-primary-light);
}

/* User Card Header */
.uc-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.uc-avatar {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 18px;
  flex-shrink: 0;
  border: 2px solid var(--color-surface);
  box-shadow: 0 2px 8px rgba(74, 124, 98, 0.1);
  overflow: hidden;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.table-user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-avatar-img {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.table-avatar-placeholder {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}

.table-username-text {
  font-weight: 500;
  color: var(--color-text);
}

.uc-info {
  display: flex;
  flex-grow: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.uc-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.uc-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 140px;
}

.uc-status-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  line-height: 1.4;
}

.status-active {
  background: rgba(74, 124, 98, 0.1);
  color: var(--color-primary);
}

.status-banned {
  background: rgba(169, 75, 69, 0.1);
  color: var(--color-accent);
}

.uc-email {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.uc-id-badge {
  font-size: 11px;
  font-family: monospace;
  color: var(--color-text-muted);
  background: var(--color-surface-soft);
  padding: 2px 6px;
  border-radius: 4px;
  align-self: flex-start;
  margin-top: 2px;
  flex-shrink: 0;
}

/* User Card Body */
.uc-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--color-surface-soft);
  padding: 10px 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
}

.uc-meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--text-sm);
}

.uc-meta-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
  font-weight: 500;
}

.meta-icon-svg {
  width: 14px;
  height: 14px;
  color: var(--color-primary);
  opacity: 0.8;
  flex-shrink: 0;
}

.uc-meta-value {
  color: var(--color-text);
  font-weight: 600;
  font-family: var(--font-body);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 60%;
  text-align: right;
}

/* Card Actions */
.uc-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 4px;
}

.action-btn-pill {
  flex: 1;
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(74, 124, 98, 0.02);
  transition: color var(--duration-fast) var(--ease-out), background-color var(--duration-fast) var(--ease-out), border-color var(--duration-fast) var(--ease-out), opacity var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out);
}

.action-btn-pill:active {
  transform: scale(0.96);
}

/* Mobile pagination */
.mobile-pagination-premium {
  display: flex;
  justify-content: center;
  margin-top: 20px;
  padding: 14px;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border);
}

.mobile-loading {
  text-align: center;
  padding: 30px;
  color: var(--color-text-secondary);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}

/* User list transition animations */
.user-list-enter-active,
.user-list-leave-active {
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}
.user-list-enter-from {
  opacity: 0;
  transform: translateY(24px) scale(0.97);
}
.user-list-leave-to {
  opacity: 0;
  transform: translateY(-24px) scale(0.97);
}
.user-list-move {
  transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.admin-filter-bar {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  margin-bottom: 16px;
}

.sort-options {
  display: flex;
  align-items: center;
  background: var(--color-surface-soft);
  padding: 4px;
  border-radius: 20px;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}

.filter-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-left: 8px;
  margin-right: 4px;
  font-weight: 500;
}

.sort-btn {
  border: none;
  background: transparent;
  padding: 6px 14px;
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
  border-radius: 16px;
  font-weight: 500;
  transition: color var(--duration-fast) var(--ease-out), background-color var(--duration-fast) var(--ease-out), border-color var(--duration-fast) var(--ease-out), opacity var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out);
}

.sort-btn:hover {
  color: var(--color-text);
}

.sort-btn.active {
  background: var(--color-surface);
  color: var(--color-primary);
  box-shadow: var(--shadow-sm);
  font-weight: 600;
}

@media (max-width: 768px) {
  .admin-filter-bar {
    justify-content: center;
    margin-bottom: 20px;
  }
}
</style>

