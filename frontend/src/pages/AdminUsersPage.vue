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
            :scroll-x="800"
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
          <div v-else class="mobile-cards">
            <div v-for="user in users" :key="user.id" class="user-card">
              <div class="user-card-header">
                <span class="user-id">ID: {{ user.id }}</span>
                <n-tag :type="user.status === 1 ? 'success' : 'error'" size="small">
                  {{ user.status === 1 ? '正常' : '已封禁' }}
                </n-tag>
              </div>
              <div class="user-card-body">
                <div class="info-row">
                  <span class="info-label">用户名:</span>
                  <span class="info-value font-medium">{{ user.displayName }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">邮箱:</span>
                  <span class="info-value">{{ user.email }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">最后活跃:</span>
                  <span class="info-value">{{ formatTime(user.lastActiveTime) }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">注册时间:</span>
                  <span class="info-value">{{ formatTime(user.createdAt) }}</span>
                </div>
              </div>
              <div class="user-card-actions">
                <n-button size="small" tertiary @click="goToProfile(user.id)">
                  查看主页
                </n-button>
                <n-button
                  size="small"
                  :type="user.status === 1 ? 'error' : 'success'"
                  ghost
                  @click="toggleStatus(user)"
                >
                  {{ user.status === 1 ? '封禁' : '解禁' }}
                </n-button>
              </div>
            </div>
            
            <div class="mobile-pagination">
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
    width: 150
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
      return formatTime(row.lastActiveTime)
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
    users.value = data.items ?? []
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
  margin-bottom: 20px;
  gap: 12px;
}

.admin-actions {
  width: 250px;
}

.admin-users-list {
  background: #ffffff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.04);
  border: 1px solid #edf2f7;
}

.desktop-only {
  display: block;
}

.mobile-only {
  display: none;
}

@media (max-width: 768px) {
  .admin-page-head {
    flex-direction: column;
    align-items: flex-start;
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
  gap: 12px;
}

.user-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
  border: 1px solid #edf2f7;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.user-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f7fafc;
  padding-bottom: 8px;
}

.user-id {
  font-size: 13px;
  color: #718096;
  font-family: monospace;
}

.user-card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
}

.info-label {
  color: #718096;
}

.info-value {
  color: #2d3748;
  word-break: break-all;
  text-align: right;
  max-width: 70%;
}

.font-medium {
  font-weight: 500;
}

.user-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid #f7fafc;
  padding-top: 12px;
  margin-top: 4px;
}

.mobile-pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
  padding: 12px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
  border: 1px solid #edf2f7;
}

.mobile-loading {
  text-align: center;
  padding: 30px;
  color: #718096;
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #edf2f7;
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
  background: #f7fafc;
  padding: 4px;
  border-radius: 8px;
  border: 1px solid #edf2f7;
}

.filter-label {
  font-size: 13px;
  color: #718096;
  margin-left: 8px;
  margin-right: 4px;
}

.sort-btn {
  border: none;
  background: transparent;
  padding: 6px 12px;
  font-size: 13px;
  color: #4a5568;
  cursor: pointer;
  border-radius: 6px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.sort-btn:hover {
  color: #2d3748;
}

.sort-btn.active {
  background: #ffffff;
  color: var(--color-jade, #10b981);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

@media (max-width: 768px) {
  .admin-filter-bar {
    justify-content: center;
  }
}
</style>
