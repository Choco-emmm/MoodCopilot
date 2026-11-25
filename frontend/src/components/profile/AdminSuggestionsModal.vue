<template>
  <n-modal
    :show="show"
    @update:show="$emit('update:show', $event)"
    preset="card"
    class="suggestions-modal"
    :bordered="false"
    :auto-focus="false"
  >
    <template #header>
      <div class="suggestions-modal-title">全部用户反馈</div>
    </template>
    <template #header-extra>
      <button type="button" class="open-full-page-link" @click="openFullPage">
        前往反馈页面 →
      </button>
    </template>

    <div class="suggestions-modal-scroll">
      <div class="admin-filter-row">
        <button
          v-for="opt in statusFilters"
          :key="opt.value"
          type="button"
          :class="['filter-pill', { active: statusFilter === opt.value }]"
          @click="selectFilter(opt.value)"
        >
          {{ opt.label }}
        </button>
        <span class="total-label">共 {{ total }} 条</span>
      </div>

      <div v-if="loading" class="center-p20">
        <n-spin size="small" />
      </div>
      <div v-else-if="!suggestions.length" class="center-p20 text-light">
        暂无用户反馈
      </div>
      <div v-else class="admin-suggestions-list">
        <FeedbackThreadList
          :items="suggestions"
          :is-admin-mode="true"
          @updated="replaceSuggestion"
        />
        <div v-if="hasMore" class="center-mt12">
          <n-button size="small" secondary :loading="loadingMore" @click="loadMore">加载更多</n-button>
        </div>
      </div>
    </div>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NModal, NSpin, NButton } from 'naive-ui'
import { suggestionApi, type SuggestionItem } from '../../api'
import FeedbackThreadList from '../feedback/FeedbackThreadList.vue'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  (e: 'update:show', val: boolean): void
}>()

const router = useRouter()

const statusFilters = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待回复' },
  { value: 'READ', label: '跟进中' },
  { value: 'RESOLVED', label: '已解决' },
]

const statusFilter = ref('ALL')
const suggestions = ref<SuggestionItem[]>([])
const total = ref(0)
const page = ref(1)
const hasMore = ref(false)
const loading = ref(false)
const loadingMore = ref(false)

async function loadData() {
  loading.value = true
  page.value = 1
  try {
    const res = await suggestionApi.adminList(1, 20, statusFilter.value)
    suggestions.value = res.data.data.items || []
    total.value = Number(res.data.data.total || 0)
    hasMore.value = suggestions.value.length < total.value
  } catch (e) {
    console.error('加载建议失败', e)
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const nextPage = page.value + 1
    const res = await suggestionApi.adminList(nextPage, 20, statusFilter.value)
    const items: SuggestionItem[] = res.data.data.items || []
    suggestions.value.push(...items)
    total.value = Number(res.data.data.total || total.value)
    page.value = nextPage
    hasMore.value = suggestions.value.length < total.value
  } catch (e) {
    console.error('加载更多建议失败', e)
  } finally {
    loadingMore.value = false
  }
}

function selectFilter(status: string) {
  if (statusFilter.value === status) return
  statusFilter.value = status
  void loadData()
}

function replaceSuggestion(updated: SuggestionItem) {
  const idx = suggestions.value.findIndex((item) => item.id === updated.id)
  if (idx >= 0) {
    suggestions.value.splice(idx, 1, updated)
  }
}

function openFullPage() {
  emit('update:show', false)
  void router.push({ path: '/feedback', query: { tab: 'admin' } })
}

watch(() => props.show, (val) => {
  if (val) {
    void loadData()
  }
})
</script>

<style scoped>
.suggestions-modal-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
}

.open-full-page-link {
  margin-right: 12px;
  padding: 4px 10px;
  border: 1px solid color-mix(in oklab, var(--color-primary) 30%, transparent);
  border-radius: 6px;
  background: transparent;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease;
}

.open-full-page-link:hover {
  background: color-mix(in oklab, var(--color-primary) 10%, transparent);
  border-color: var(--color-primary);
}

.suggestions-modal-scroll {
  max-height: calc(82vh - 96px);
  overflow-y: auto;
  padding-right: 4px;
}

.admin-filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
}

.filter-pill {
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease, color 0.18s ease;
}

.filter-pill.active {
  border-color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 12%, transparent);
  color: var(--color-primary);
  font-weight: 600;
}

.total-label {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-muted);
}

.center-p20 {
  text-align: center;
  padding: 24px;
}

.text-light {
  color: var(--color-text-light);
}

.center-mt12 {
  text-align: center;
  margin-top: 12px;
}
</style>

<style>
.suggestions-modal.n-card {
  width: min(760px, calc(100vw - 32px)) !important;
  max-width: calc(100vw - 32px) !important;
  max-height: calc(100vh - 48px) !important;
  background: var(--color-surface) !important;
  border: 1px solid color-mix(in oklab, var(--color-border) 50%, transparent) !important;
  border-radius: 12px !important;
  box-shadow: var(--shadow-lg) !important;
}

.suggestions-modal.n-card > .n-card-header {
  background: transparent !important;
  padding: 20px 24px 14px !important;
  border-bottom: 1px solid color-mix(in oklab, var(--color-border) 40%, transparent) !important;
}

.suggestions-modal.n-card > .n-card__content {
  padding: 16px 24px 20px !important;
}
</style>
