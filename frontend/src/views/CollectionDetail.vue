<template>
  <div class="app-shell min-h-screen flex flex-col">

    <!-- ── 杂志风返回导航 ── -->
    <div class="editorial-back">
      <button @click="$router.back()" class="editorial-back-link">
        <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24" style="flex-shrink: 0;">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 6l-6 6 6 6" />
        </svg>
        <span>返回</span>
      </button>
    </div>

    <!-- ── 杂志风头部 ── -->
    <header class="collection-header">
      <div class="collection-header-title-row">
        <h1 class="collection-header-title">
          {{ collection?.name || '合集详情' }}
        </h1>
        <span v-if="collection" class="collection-header-vis" :class="collection.visibility === 'PUBLIC' ? 'vis-public' : 'vis-private'">
          {{ collection.visibility === 'PUBLIC' ? '公开' : '私密' }}
        </span>
      </div>

      <p v-if="collection?.description" class="collection-header-desc">
        {{ collection.description }}
      </p>

      <p v-if="collection" class="collection-header-count">
        共 {{ diaries.length }} 篇日记
      </p>

      <div v-if="isOwner" class="collection-header-actions">
        <button class="editorial-pill primary" @click="router.push(`/write?collectionId=${collectionId}`)">
          写日记
        </button>
        <button class="editorial-pill ghost" @click="openAddDiaryModal">添加已有日记</button>
        <button class="editorial-pill ghost" @click="showEditModal = true">编辑合集</button>
        <button class="editorial-pill ghost danger" :disabled="deleting" @click="handleDelete">
          {{ deleting ? '删除中...' : '删除合集' }}
        </button>
      </div>
    </header>

    <CollectionModal
      v-model:show="showEditModal"
      :edit-data="collection"
      @success="handleEditSuccess"
    />

    <!-- ── 添加已有日记弹窗 ── -->
    <div v-if="showAddDiaryModal" class="add-diary-overlay" @click.self="showAddDiaryModal = false">
      <div class="add-diary-dialog" @click.stop>
        <div class="add-diary-head">
          <h3 class="add-diary-title">添加已有日记</h3>
          <button class="add-diary-close" @click="showAddDiaryModal = false">&times;</button>
        </div>

        <div v-if="loadingMyDiaries" class="add-diary-status">
          <span class="add-diary-status-text">加载中...</span>
        </div>

        <div v-else-if="filteredMyDiaries.length === 0" class="add-diary-status">
          <span class="add-diary-status-text">{{ isCollectionPublic ? '暂无公开日记可添加（私密日记不能添加到公开合集）' : '暂无可添加的日记' }}</span>
        </div>

        <div v-else class="add-diary-list">
          <div
            v-for="d in filteredMyDiaries"
            :key="d.id"
            :class="['add-diary-item', {
              selected: selectedDiaryIds.includes(d.id),
              existing: existingDiaryIds.has(d.id)
            }]"
            @click="toggleDiarySelection(d.id)"
          >
            <span class="add-diary-checkbox">
              <span v-if="selectedDiaryIds.includes(d.id) || existingDiaryIds.has(d.id)" class="add-diary-checked">✓</span>
            </span>
            <div class="add-diary-item-info">
              <div class="add-diary-item-content">{{ stripHtml(d.content) }}</div>
              <div class="add-diary-item-time">{{ formatDate(d.createdAt) }}</div>
            </div>
            <span v-if="existingDiaryIds.has(d.id)" class="add-diary-already-tag">已添加</span>
          </div>
        </div>

        <div v-if="hasMoreMyDiaries && !loadingMyDiaries" class="add-diary-load-more">
          <button class="editorial-pill" :disabled="loadingMoreMyDiaries" @click="loadMoreMyDiaries_">
            {{ loadingMoreMyDiaries ? '加载中...' : '加载更多' }}
          </button>
        </div>

        <div class="add-diary-foot">
          <span class="add-diary-count">已选 {{ selectedDiaryIds.length }} 篇</span>
          <div class="add-diary-foot-actions">
            <button class="editorial-pill" @click="showAddDiaryModal = false">取消</button>
            <button
              class="editorial-pill primary"
              :disabled="selectedDiaryIds.length === 0 || addingDiaries"
              @click="confirmAddDiaries"
            >
              {{ addingDiaries ? '添加中...' : '确认添加' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ── 日记列表面板（纸张温度） ── -->
    <div class="diary-panel">

      <div v-if="loading" class="diary-panel-status">
        <span class="diary-panel-status-text">加载中...</span>
      </div>

      <div v-else-if="diaries?.length === 0" class="diary-panel-empty">
        <p class="diary-panel-empty-text">这个合集中还没有日记</p>
        <button v-if="isOwner" class="editorial-pill primary" @click="router.push(`/write?collectionId=${collectionId}`)">
          第一篇写点什么 &rarr;
        </button>
      </div>

      <div v-else class="diary-panel-list">
        <draggable
          v-model="diaries"
          item-key="id"
          handle=".drag-handle"
          ghost-class="sortable-ghost"
          animation="250"
          @end="handleDragEnd"
        >
          <template #item="{ element: diary }">
            <div class="diary-row" @click="goToDiary(diary.id)">

              <div class="drag-handle">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 8h16M4 16h16" />
                </svg>
              </div>

              <div class="diary-row-body">
                <div class="diary-row-content">
                  {{ stripHtml(diary.content) }}
                </div>

                <div class="diary-row-meta">
                  <span class="diary-row-time">{{ formatDate(diary.createdAt) }}</span>
                </div>
              </div>
            </div>
          </template>
        </draggable>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { collectionApi, diaryApi } from '../api'
import { useAuthStore } from '../stores/auth'
import draggable from 'vuedraggable'
import CollectionModal from '../components/collection/CollectionModal.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collectionId = Number(route.params.id)

const collection = ref<any>(null)
const diaries = ref<any[]>([])
const loading = ref(true)
const deleting = ref(false)
const showEditModal = ref(false)

// 添加已有日记
const showAddDiaryModal = ref(false)
const myDiaries = ref<any[]>([])
const loadingMyDiaries = ref(false)
const loadingMoreMyDiaries = ref(false)
const addingDiaries = ref(false)
const selectedDiaryIds = ref<number[]>([])
const existingDiaryIds = ref<Set<number>>(new Set())
const myDiariesPage = ref(1)
const myDiariesTotal = ref(0)
const hasMoreMyDiaries = computed(() => myDiaries.value.length < myDiariesTotal.value)
const isCollectionPublic = computed(() => collection.value?.visibility === 'PUBLIC')
const filteredMyDiaries = computed(() => {
  if (!isCollectionPublic.value) return myDiaries.value
  return myDiaries.value.filter((d: any) => d.visibility === 'PUBLIC')
})

const isOwner = computed(() => auth.userId != null && collection.value?.userId === auth.userId)

// 去掉 HTML 标签，提取纯文本（保留换行）
const stripHtml = (html?: string): string => {
  if (!html) return ''
  return html
    .replace(/<\/(p|div|h[1-6]|li|blockquote)>/gi, '\n')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .trim()
}

// 格式化日期
const formatDate = (date: string) => {
  const d = new Date(date)
  return d.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  }).toUpperCase()
}

// 获取合集详情
const fetchCollection = async () => {
  try {
    const [collRes, diariesRes] = await Promise.all([
      collectionApi.get(collectionId),
      collectionApi.diaries(collectionId, 1, 100, 'ADDED_TIME_DESC'),
    ])
    collection.value = collRes.data.data
    diaries.value = diariesRes.data.data?.records ?? []
  } catch (error) {
    console.error('获取合集详情失败:', error)
  } finally {
    loading.value = false
  }
}

// 处理拖拽结束事件
const handleDragEnd = async (event: any) => {
  const { oldIndex, newIndex } = event.moved
  let prevSortOrder: number | null = null
  let nextSortOrder: number | null = null

  if (newIndex > 0) {
    prevSortOrder = diaries.value[newIndex - 1].sortOrder
  }
  if (newIndex < diaries.value.length - 1) {
    nextSortOrder = diaries.value[newIndex + 1].sortOrder
  }

  const diaryId = diaries.value[newIndex].id

  try {
    await collectionApi.updateDiarySortOrder(collectionId, diaryId, prevSortOrder, nextSortOrder)
    const newSortOrder = prevSortOrder != null
      ? (prevSortOrder + (nextSortOrder ?? prevSortOrder + 1)) / 2.0
      : ((nextSortOrder ?? 0) + 100000.0)
    diaries.value[newIndex].sortOrder = newSortOrder
  } catch (error) {
    console.error('排序更新失败:', error)
    event.revert()
  }
}

const goToDiary = (diaryId: number) => {
  router.push(`/diary/${diaryId}`)
}

const handleDelete = async () => {
  if (!confirm('确定要删除这个合集吗？日记不会被删除，仅移除合集关联。')) return
  deleting.value = true
  try {
    await collectionApi.delete(collectionId)
    if (auth.userId) {
      router.replace({ path: `/profile/${auth.userId}`, query: { tab: 'collections', refresh: 'true' } })
    } else {
      router.replace('/')
    }
  } catch (e: any) {
    const msg = e?.response?.data?.message || '删除失败'
    window.$message?.error(msg)
  } finally {
    deleting.value = false
  }
}

const handleEditSuccess = (updatedCollection: any) => {
  collection.value = updatedCollection
}

async function openAddDiaryModal() {
  showAddDiaryModal.value = true
  selectedDiaryIds.value = []
  myDiariesPage.value = 1
  loadingMyDiaries.value = true
  try {
    existingDiaryIds.value = new Set(diaries.value.map((d: any) => d.id))
    const res = await diaryApi.mine(1, 20)
    const data = res.data.data
    myDiaries.value = data.items ?? []
    myDiariesTotal.value = data.total ?? 0
  } catch (e) {
    console.error('加载日记列表失败', e)
  } finally {
    loadingMyDiaries.value = false
  }
}

async function loadMoreMyDiaries_() {
  if (loadingMoreMyDiaries.value) return
  loadingMoreMyDiaries.value = true
  try {
    const nextPage = myDiariesPage.value + 1
    const res = await diaryApi.mine(nextPage, 20)
    const data = res.data.data
    const items = data.items ?? []
    const existingIds = new Set(myDiaries.value.map((d: any) => d.id))
    myDiaries.value.push(...items.filter((d: any) => !existingIds.has(d.id)))
    myDiariesTotal.value = data.total ?? myDiaries.value.length
    myDiariesPage.value = nextPage
  } catch (e) {
    console.error('加载更多日记失败', e)
  } finally {
    loadingMoreMyDiaries.value = false
  }
}

function toggleDiarySelection(diaryId: number) {
  if (existingDiaryIds.value.has(diaryId)) return
  const idx = selectedDiaryIds.value.indexOf(diaryId)
  if (idx === -1) {
    selectedDiaryIds.value.push(diaryId)
  } else {
    selectedDiaryIds.value.splice(idx, 1)
  }
}

async function confirmAddDiaries() {
  if (selectedDiaryIds.value.length === 0) return
  addingDiaries.value = true
  try {
    await collectionApi.addDiaries(collectionId, selectedDiaryIds.value)
    window.$message?.success(`成功添加 ${selectedDiaryIds.value.length} 篇日记`)
    showAddDiaryModal.value = false
    loading.value = true
    await fetchCollection()
  } catch (e: any) {
    const msg = e?.response?.data?.message || '添加失败'
    window.$message?.error(msg)
  } finally {
    addingDiaries.value = false
  }
}

onMounted(() => {
  fetchCollection()
})
</script>

<style scoped>
/* ── 杂志风返回 ── */
.editorial-back {
  margin-bottom: 20px;
  padding: 0 4px;
}

.editorial-back-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  color: var(--color-text-muted);
  font-size: 13px;
  font-family: var(--font-body);
  cursor: pointer;
  padding: 0;
  transition: color 0.15s;
}

.editorial-back-link:hover {
  color: var(--color-text);
}

/* ── 杂志风头部 ── */
.collection-header {
  text-align: center;
  margin-bottom: 32px;
  padding: 0 16px;
}

.collection-header-title-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.collection-header-title {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 600;
  letter-spacing: var(--tracking-wide);
  color: var(--color-text);
  margin: 0;
}

.collection-header-vis {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 8px;
  flex-shrink: 0;
}

.collection-header-vis.vis-public {
  color: var(--color-success);
  background: color-mix(in oklab, var(--color-success) 12%, transparent);
}

.collection-header-vis.vis-private {
  color: var(--color-text-muted);
  background: color-mix(in oklab, var(--color-text-muted) 12%, transparent);
}

.collection-header-desc {
  max-width: 480px;
  margin: 12px auto 0;
  font-style: italic;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  line-height: var(--leading-relaxed);
  border-left: 3px solid var(--color-primary);
  padding-left: 16px;
  text-align: left;
}

.collection-header-count {
  margin-top: 12px;
  font-size: 12px;
  color: var(--color-text-muted);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.collection-header-actions {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 14px;
  margin-top: 20px;
  flex-wrap: wrap;
}

/* ── 杂志风操作按钮（editorial pill） ── */
.editorial-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 5px 16px;
  border: 1px solid color-mix(in oklab, var(--color-primary) 15%, transparent);
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  font-family: var(--font-body);
}

.editorial-pill:hover:not(:disabled) {
  background: color-mix(in oklab, var(--color-primary) 5%, transparent);
  color: var(--color-text);
  border-color: color-mix(in oklab, var(--color-primary) 25%, transparent);
}

.editorial-pill.primary {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 5%, transparent);
}

.editorial-pill.primary:hover:not(:disabled) {
  background: color-mix(in oklab, var(--color-primary) 12%, transparent);
}

.editorial-pill.danger {
  border-color: color-mix(in oklab, var(--color-error, #c44) 30%, transparent);
  color: var(--color-error, #c44);
}

.editorial-pill.danger:hover:not(:disabled) {
  background: color-mix(in oklab, var(--color-error, #c44) 8%, transparent);
}

.editorial-pill:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ── Ghost pill（次要操作 · 克制的边框仅 hover 时显现） ── */
.editorial-pill.ghost {
  border-color: transparent;
  color: var(--color-text-muted);
  background: transparent;
}

.editorial-pill.ghost:hover:not(:disabled) {
  color: var(--color-text);
  border-color: color-mix(in oklab, var(--color-primary) 18%, transparent);
  background: transparent;
}

.editorial-pill.ghost.danger {
  color: var(--color-text-muted);
}

.editorial-pill.ghost.danger:hover:not(:disabled) {
  color: var(--color-error, #c44);
  border-color: color-mix(in oklab, var(--color-error, #c44) 25%, transparent);
  background: transparent;
}

/* ── 日记面板（纸张温度） ── */
.diary-panel {
  max-width: 680px;
  margin: 0 auto;
  width: 100%;
  padding: 28px 24px;
  background-image: linear-gradient(135deg, transparent 80%, color-mix(in oklab, var(--color-primary) 1.5%, transparent));
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.diary-panel-status {
  text-align: center;
  padding: 40px 0;
}

.diary-panel-status-text {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  font-style: italic;
}

/* ── 空状态 ── */
.diary-panel-empty {
  text-align: center;
  padding: 48px 20px;
}

.diary-panel-empty-text {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  font-style: italic;
  border-left: 3px solid var(--color-primary);
  padding-left: 16px;
  display: inline-block;
  text-align: left;
  margin: 0 0 20px;
}

/* ── 呼吸线日记列表 ── */
.diary-panel-list {
  /* container for draggable */
}

.diary-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid color-mix(in oklab, var(--color-primary) 8%, transparent);
  cursor: pointer;
  transition: opacity 0.2s;
}

.diary-row:first-child {
  padding-top: 0;
}

.diary-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.diary-row:hover {
  opacity: 0.92;
}

.drag-handle {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--color-text-muted);
  cursor: grab;
  transition: color 0.15s;
  touch-action: none;
}

.drag-handle:active {
  cursor: grabbing;
}

.diary-row:hover .drag-handle {
  color: color-mix(in oklab, var(--color-primary) 40%, transparent);
}

.diary-row-body {
  flex: 1;
  min-width: 0;
}

.diary-row-content {
  font-size: var(--text-base);
  color: var(--color-text);
  line-height: var(--leading-normal);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.diary-row-meta {
  margin-top: 6px;
}

.diary-row-time {
  font-size: 11px;
  color: var(--color-text-muted);
  font-family: monospace;
}

/* 拖拽态 */
.sortable-ghost {
  opacity: 0.3;
  background: color-mix(in oklab, var(--color-primary) 5%, transparent);
  border-radius: var(--radius-md);
}

/* ── 添加已有日记弹窗（杂志风） ── */
.add-diary-overlay {
  position: fixed;
  inset: 0;
  background: rgba(32, 32, 29, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  backdrop-filter: blur(4px);
}

.add-diary-dialog {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  width: 92%;
  max-width: 420px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
  border: 1px solid color-mix(in oklab, var(--color-primary) 10%, transparent);
  overflow: hidden;
}

.add-diary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 12px;
  flex-shrink: 0;
}

.add-diary-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  font-family: var(--font-display);
}

.add-diary-close {
  background: none;
  border: none;
  font-size: 22px;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 0;
  line-height: 1;
  transition: color 0.15s;
}

.add-diary-close:hover {
  color: var(--color-text);
}

.add-diary-status {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.add-diary-status-text {
  font-size: 13px;
  color: var(--color-text-muted);
  font-style: italic;
}

.add-diary-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 50vh;
}

.add-diary-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid transparent;
}

.add-diary-item:hover {
  background: color-mix(in oklab, var(--color-primary) 3%, transparent);
}

.add-diary-item.selected {
  background: color-mix(in oklab, var(--color-primary) 6%, transparent);
  border-color: color-mix(in oklab, var(--color-primary) 15%, transparent);
}

.add-diary-item.existing {
  opacity: 0.45;
  cursor: default;
}

.add-diary-checkbox {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 1.5px solid color-mix(in oklab, var(--color-primary) 18%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 11px;
  color: var(--color-primary);
  transition: all 0.15s;
  margin-top: 2px;
}

.add-diary-item.selected .add-diary-checkbox {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.add-diary-item.existing .add-diary-checkbox {
  background: var(--color-text-muted);
  border-color: var(--color-text-muted);
  color: var(--color-surface);
}

.add-diary-item-info {
  flex: 1;
  min-width: 0;
}

.add-diary-item-content {
  font-size: 13px;
  color: var(--color-text);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.add-diary-item-time {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 4px;
  font-family: monospace;
}

.add-diary-already-tag {
  font-size: 10px;
  color: var(--color-text-muted);
  background: color-mix(in oklab, var(--color-text-muted) 10%, transparent);
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
  margin-top: 2px;
}

.add-diary-load-more {
  display: flex;
  justify-content: center;
  padding: 8px 12px;
}

.add-diary-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px 16px;
  flex-shrink: 0;
  border-top: 1px solid color-mix(in oklab, var(--color-primary) 8%, transparent);
}

.add-diary-count {
  font-size: 12px;
  color: var(--color-text-muted);
}

.add-diary-foot-actions {
  display: flex;
  gap: 8px;
}

/* ── 响应式 ── */
@media (max-width: 640px) {
  .collection-header-title {
    font-size: var(--text-xl);
  }

  .collection-header-actions {
    gap: 10px;
  }

  .diary-panel {
    padding: 20px 16px;
    border-radius: var(--radius-md);
  }

  .diary-row {
    gap: 8px;
    padding: 14px 0;
  }

  .diary-row-content {
    font-size: var(--text-sm);
  }
}
</style>
