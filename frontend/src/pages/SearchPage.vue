<template>
  <main class="app-shell">
    <AppHeader />

    <section class="panel">
      <div class="section-title">
        <div>
          <p class="eyebrow">搜索日记</p>
          <h2>在已写过的日记中查找</h2>
        </div>
      </div>

      <div class="search-bar">
        <n-input
          v-model:value="keyword"
          placeholder="搜索日记内容、歌曲名或歌手..."
          clearable
          @keyup.enter="doSearch"
        />
        <n-button type="primary" @click="doSearch" :loading="loading">搜索</n-button>
      </div>

      <div class="search-filters">
        <div class="filter-item">
          <label class="filter-label">起始日期</label>
          <n-date-picker v-model:value="startDateVal" type="date" clearable placeholder="不限" :is-date-disabled="dateDisabled" />
        </div>
        <div class="filter-item">
          <label class="filter-label">结束日期</label>
          <n-date-picker v-model:value="endDateVal" type="date" clearable placeholder="不限" />
        </div>
        <div class="filter-item">
          <label class="filter-label">公开范围</label>
          <n-select
            v-model:value="visibilityFilter"
            :options="visibilityOpts"
            placeholder="不限"
            clearable
            style="min-width: 120px;"
          />
        </div>
        <n-button text @click="clearFilters">清除筛选</n-button>
      </div>

      <div v-if="results.length" class="search-results">
        <div class="search-result-count">共 {{ total }} 条结果</div>
        <div
          v-for="d in results"
          :key="d.id"
          class="search-result-card"
          role="button"
          tabindex="0"
          @click="router.push('/diary/' + d.id)"
          @keydown.enter.prevent="router.push('/diary/' + d.id)"
        >
          <div class="result-head">
            <span class="result-date">{{ formatDate(d.createdAt) }}</span>
            <span :class="['result-vis', d.visibility === 'PUBLIC' ? 'vis-public' : 'vis-private']">
              {{ d.visibility === 'PUBLIC' ? '公开' : '私密' }}
            </span>
          </div>
          <div class="result-body md-content" v-html="renderMd(snippet(d.content))"></div>
          <div v-if="d.musicMeta" class="result-music">
            🎵 {{ d.musicMeta.title }} — {{ d.musicMeta.artist }}
          </div>
          <div class="result-analysis" v-if="d.analysis?.moodLabel">
            <n-tag size="small" round>{{ d.analysis.moodLabel }}</n-tag>
          </div>
        </div>
        <div v-if="hasMore" class="search-more">
          <n-button :loading="loadingMore" @click="loadMore">加载更多</n-button>
        </div>
      </div>

      <div v-else-if="searched && !loading" class="search-empty">
        <n-empty description="未找到匹配的日记" />
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { diaryApi } from '../api'
import { renderSafeMarkdown, stripMarkdown } from '../utils/markdown'
import AppHeader from '../components/AppHeader.vue'

const router = useRouter()

const keyword = ref('')
const startDateVal = ref<number | null>(null)
const endDateVal = ref<number | null>(null)
const visibilityFilter = ref<string | null>(null)
const visibilityOpts = [
  { label: '仅自己看', value: 'PRIVATE' },
  { label: '公开', value: 'PUBLIC' },
]

const results = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const hasMore = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const searched = ref(false)
const pageSize = 20

function renderMd(text: string) {
  if (!text) return ''
  return renderSafeMarkdown(stripMarkdown(text))
}

function formatDate(d: string) {
  if (!d) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date(d))
}

function dateDisabled(ts: number) {
  return ts > Date.now()
}

function fmtDate(d: number | null): string | undefined {
  if (!d) return undefined
  const date = new Date(d)
  return date.getFullYear() + '-' +
    String(date.getMonth() + 1).padStart(2, '0') + '-' +
    String(date.getDate()).padStart(2, '0')
}

async function doSearch() {
  page.value = 1
  loading.value = true
  searched.value = true
  try {
    const res = await diaryApi.search({
      keyword: keyword.value || undefined,
      startDate: fmtDate(startDateVal.value),
      endDate: fmtDate(endDateVal.value),
      visibility: visibilityFilter.value || undefined,
      page: page.value,
      size: pageSize,
    })
    results.value = res.data.data.items ?? []
    total.value = res.data.data.total ?? 0
    hasMore.value = results.value.length < total.value
  } catch {
    results.value = []
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  page.value++
  try {
    const res = await diaryApi.search({
      keyword: keyword.value || undefined,
      startDate: fmtDate(startDateVal.value),
      endDate: fmtDate(endDateVal.value),
      visibility: visibilityFilter.value || undefined,
      page: page.value,
      size: pageSize,
    })
    const items = res.data.data.items ?? []
    results.value.push(...items)
    total.value = res.data.data.total ?? total.value
    hasMore.value = results.value.length < total.value
  } catch {
    page.value--
  } finally {
    loadingMore.value = false
  }
}

function clearFilters() {
  keyword.value = ''
  startDateVal.value = null
  endDateVal.value = null
  visibilityFilter.value = null
  results.value = []
  searched.value = false
}
</script>

<style scoped>
.search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.search-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  margin-bottom: 20px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border, #e0d8c8);
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.filter-label {
  font-size: 11px;
  color: var(--color-text-muted, #a09080);
}

.search-result-count {
  font-size: 13px;
  color: var(--color-text-secondary, #67645d);
  margin-bottom: 10px;
}

.search-results {
  display: grid;
  gap: 10px;
}

.search-result-card {
  padding: 14px 16px;
  border: 1px solid var(--color-border, #e0d8c8);
  border-radius: var(--radius-md, 10px);
  background: var(--color-surface, #fdfcf8);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.search-result-card:hover {
  border-color: var(--color-primary, #4a7c62);
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.06);
}

.result-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.result-date {
  font-size: 12px;
  color: var(--color-text-muted, #a09080);
}

.result-vis {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
}

.vis-public {
  background: #e7f0eb;
  color: #4a7c62;
}

.vis-private {
  background: #f5f0e8;
  color: #a09080;
}

.result-body {
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text, #20201d);
  margin-bottom: 6px;
}

.result-body :deep(p) {
  margin: 0 0 4px;
}

.result-music {
  font-size: 12px;
  color: var(--color-text-secondary, #67645d);
  margin-bottom: 6px;
}

.result-analysis {
  margin-top: 4px;
}

.search-more {
  text-align: center;
  padding: 12px 0;
}

.search-empty {
  padding: 40px 0;
}
</style>
