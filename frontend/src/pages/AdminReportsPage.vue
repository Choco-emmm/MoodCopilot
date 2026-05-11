<template>
  <main class="app-shell">
    <AppHeader />

    <div class="admin-page">
      <div class="admin-page-head">
        <div>
          <p class="eyebrow">ADMIN</p>
          <h2>举报审核</h2>
        </div>
        <n-select
          v-model:value="status"
          class="admin-status-select"
          :options="statusOptions"
          size="small"
          @update:value="loadReports"
        />
      </div>

      <div v-if="!auth.isAdmin" class="empty-state">
        <p>当前账号没有审核权限。</p>
      </div>

      <div v-else-if="reports.length === 0 && !loading" class="empty-state">
        <p>当前没有待处理举报。</p>
      </div>

      <div v-else class="admin-report-list">
        <article v-for="report in reports" :key="report.id" class="admin-report-card">
          <div class="admin-report-main">
            <div class="admin-report-title">
              <n-tag size="small" :type="tagType(report.status)">{{ report.status }}</n-tag>
              <strong>{{ report.targetType }} #{{ report.targetId }}</strong>
            </div>
            <p class="admin-report-reason">{{ report.reason }}</p>
            <p class="admin-report-meta">
              举报人 #{{ report.reporterUserId }} · {{ formatTime(report.createdAt) }}
            </p>
            <p v-if="report.handleNote" class="admin-report-note">处理备注：{{ report.handleNote }}</p>
          </div>

          <div class="admin-report-actions">
            <n-button size="small" tertiary :loading="actingId === report.id" @click="resolve(report)">
              标记已处理
            </n-button>
            <n-button size="small" tertiary type="warning" :loading="actingId === report.id" @click="reject(report)">
              驳回
            </n-button>
            <n-button size="small" type="error" :loading="actingId === report.id" @click="hideTarget(report)">
              隐藏并处理
            </n-button>
          </div>
        </article>

        <div v-if="loading" class="loading-hint">加载中...</div>
        <n-button v-else-if="hasMore" block secondary @click="loadMore">加载更多</n-button>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NSelect, NTag } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { adminApi } from '../api'
import { useAuthStore } from '../stores/auth'

type AdminReport = {
  id: number
  reporterUserId: number
  targetType: 'DIARY' | 'COMMENT'
  targetId: number
  reason: string
  status: 'PENDING' | 'RESOLVED' | 'REJECTED'
  handledByUserId?: number
  handledAt?: string
  handleNote?: string
  createdAt: string
}

const auth = useAuthStore()
const reports = ref<AdminReport[]>([])
const status = ref<'PENDING' | 'RESOLVED' | 'REJECTED'>('PENDING')
const page = ref(1)
const size = 20
const total = ref(0)
const loading = ref(false)
const actingId = ref<number | null>(null)

const statusOptions = [
  { label: '待处理', value: 'PENDING' },
  { label: '已处理', value: 'RESOLVED' },
  { label: '已驳回', value: 'REJECTED' },
]

const hasMore = computed(() => reports.value.length < total.value)

onMounted(async () => {
  await auth.fetchProfile()
  if (auth.isAdmin) await loadReports()
})

async function loadReports() {
  if (!auth.isAdmin) return
  loading.value = true
  try {
    page.value = 1
    const res = await adminApi.reports(status.value, page.value, size)
    const data = res.data.data
    reports.value = data.items ?? []
    total.value = data.total ?? reports.value.length
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    page.value++
    const res = await adminApi.reports(status.value, page.value, size)
    const data = res.data.data
    reports.value.push(...(data.items ?? []))
    total.value = data.total ?? reports.value.length
  } finally {
    loading.value = false
  }
}

async function resolve(report: AdminReport) {
  await act(report.id, (note) => adminApi.resolveReport(report.id, note), '已确认处理')
}

async function reject(report: AdminReport) {
  await act(report.id, (note) => adminApi.rejectReport(report.id, note), '不是违规内容')
}

async function hideTarget(report: AdminReport) {
  await act(report.id, (note) => adminApi.hideTarget(report.id, note), '内容违规，已隐藏')
}

async function act(id: number, action: (note: string) => Promise<unknown>, defaultNote: string) {
  const note = window.prompt('处理备注', defaultNote)
  if (note === null) return
  actingId.value = id
  try {
    await action(note)
    await loadReports()
  } finally {
    actingId.value = null
  }
}

function tagType(value: AdminReport['status']) {
  if (value === 'PENDING') return 'warning'
  if (value === 'RESOLVED') return 'success'
  return 'default'
}

function formatTime(value?: string) {
  if (!value) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>
