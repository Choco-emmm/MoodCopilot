<template>
  <div class="app-shell min-h-screen flex flex-col">

    <div class="mb-6 mt-4">
      <button @click="$router.back()" class="nav-icon-btn">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
        </svg>
      </button>
    </div>

    <div class="text-center mb-10 px-4">
      <h1 class="font-serif tracking-wide mb-3 text-[var(--color-text)]">
        {{ collection?.name || '合集详情' }}
      </h1>
      <p v-if="collection?.description" class="subtitle mx-auto">
        {{ collection.description }}
      </p>
      <div v-if="isOwner" class="collection-actions-row">
        <button class="collection-action-btn primary" @click="router.push(`/write?collectionId=${collectionId}`)">
          写日记
        </button>
        <button class="collection-action-btn" @click="openAddDiaryModal">
          添加已有日记
        </button>
        <button class="collection-action-btn" @click="showEditModal = true">
          编辑合集
        </button>
        <button
          class="collection-action-btn danger"
          :disabled="deleting"
          @click="handleDelete"
        >
          {{ deleting ? '删除中...' : '删除合集' }}
        </button>
      </div>
    </div>

    <CollectionModal
      v-model:show="showEditModal"
      :edit-data="collection"
      @success="handleEditSuccess"
    />

    <!-- 添加已有日记弹窗 -->
    <div v-if="showAddDiaryModal" class="add-diary-overlay" @click.self="showAddDiaryModal = false">
      <div class="add-diary-dialog" @click.stop>
        <div class="add-diary-head">
          <h3 class="add-diary-title">添加已有日记</h3>
          <button class="add-diary-close" @click="showAddDiaryModal = false">&times;</button>
        </div>

        <div v-if="loadingMyDiaries" class="add-diary-loading">
          <div class="inline-block animate-spin rounded-full h-6 w-6 border-b-2 border-[var(--color-primary)]"></div>
        </div>

        <div v-else-if="filteredMyDiaries.length === 0" class="add-diary-empty">
          {{ isCollectionPublic ? '暂无公开日记可添加（私密日记不能添加到公开合集）' : '暂无可添加的日记' }}
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
          <button class="collection-action-btn" :disabled="loadingMoreMyDiaries" @click="loadMoreMyDiaries_">
            {{ loadingMoreMyDiaries ? '加载中...' : '加载更多' }}
          </button>
        </div>

        <div class="add-diary-foot">
          <span class="add-diary-count">已选 {{ selectedDiaryIds.length }} 篇</span>
          <div class="add-diary-foot-actions">
            <button class="collection-action-btn" @click="showAddDiaryModal = false">取消</button>
            <button
              class="collection-action-btn primary"
              :disabled="selectedDiaryIds.length === 0 || addingDiaries"
              @click="confirmAddDiaries"
            >
              {{ addingDiaries ? '添加中...' : '确认添加' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="panel p-4 md:p-6 max-w-3xl mx-auto w-full">

      <div v-if="loading" class="text-center py-12">
        <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--color-primary)]"></div>
      </div>

      <div v-else-if="diaries?.length === 0" class="empty-state">
        <p>这个合集中还没有日记</p>
        <button v-if="isOwner" class="mt-4 px-6 py-2 bg-[var(--color-primary)] text-[var(--color-on-primary)] rounded-full text-sm font-medium hover:opacity-90 transition-opacity" @click="router.push(`/write?collectionId=${collectionId}`)">
          第一篇写点什么
        </button>
      </div>

      <div v-else class="diary-list">
        <draggable
          v-model="diaries"
          item-key="id"
          handle=".drag-handle"
          ghost-class="opacity-40"
          animation="250"
          @end="handleDragEnd"
        >
          <template #item="{ element: diary }">
            <div class="my-diary group flex items-start !grid-cols-[28px_minmax(0,1fr)] md:!grid-cols-[36px_minmax(0,1fr)_auto] mb-3 relative cursor-pointer" @click="goToDiary(diary.id)">

              <div class="drag-handle mt-1 text-[var(--color-text-muted)] cursor-grab active:cursor-grabbing hover:text-[var(--color-primary)] transition-colors">
                <svg class="w-5 h-5 md:w-6 md:h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 8h16M4 16h16"></path>
                </svg>
              </div>

              <div class="my-diary-info flex-1 w-full min-w-0">
                <div class="my-diary-content text-[var(--color-text)]">
                  {{ stripHtml(diary.content) }}
                </div>

                <div class="my-diary-meta mt-2 flex items-center justify-between">
                  <span class="my-diary-time font-mono text-xs">
                    {{ formatDate(diary.createdAt) }}
                  </span>
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

  // 获取前一个和后一个元素的排序值
  let prevSortOrder = null
  let nextSortOrder = null

  if (newIndex > 0) {
    prevSortOrder = diaries.value[newIndex - 1].sortOrder
  }

  if (newIndex < diaries.value.length - 1) {
    nextSortOrder = diaries.value[newIndex + 1].sortOrder
  }

  // 获取被拖拽元素的ID
  const diaryId = diaries.value[newIndex].id

  try {
    // 调用后端API更新排序
    await collectionApi.updateDiarySortOrder(collectionId, diaryId, prevSortOrder, nextSortOrder)

    // 更新本地排序值（可选，根据后端返回更新）
    const newSortOrder = prevSortOrder ?
      (prevSortOrder + nextSortOrder) / 2.0 :
      (nextSortOrder + 100000.0)

    diaries.value[newIndex].sortOrder = newSortOrder

    console.log('排序更新成功')
  } catch (error) {
    console.error('排序更新失败:', error)
    // 可选：回滚拖拽操作
    event.revert()
  }
}

// 点击跳转到日记详情
const goToDiary = (diaryId: number) => {
  router.push(`/diary/${diaryId}`)
}

// 删除合集
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

// 编辑成功回调
const handleEditSuccess = (updatedCollection: any) => {
  collection.value = updatedCollection
}

// 打开添加已有日记弹窗
async function openAddDiaryModal() {
  showAddDiaryModal.value = true
  selectedDiaryIds.value = []
  myDiariesPage.value = 1
  loadingMyDiaries.value = true
  try {
    // 获取当前合集内的日记 ID
    existingDiaryIds.value = new Set(diaries.value.map((d: any) => d.id))
    // 加载用户日记
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
  // Already in collection, skip
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
    // Refresh diary list
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
/* 拖拽时的样式增强 */
.drag-handle {
  touch-action: none; /* 防止移动端拖拽时触发页面滚动 */
}

/* 拖拽被抓取时的手部状态 */
.sortable-ghost {
  background-color: var(--color-surface-hover);
  border: 1px dashed var(--color-primary);
  border-radius: var(--radius-md);
}

.sortable-drag {
  box-shadow: var(--shadow-lg);
  background-color: var(--color-surface);
}

.collection-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;
}

.collection-action-btn:hover:not(:disabled) {
  background: color-mix(in oklab, var(--color-surface-hover) 80%, transparent);
}

.collection-action-btn.primary {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 5%, transparent);
}

.collection-action-btn.primary:hover:not(:disabled) {
  background: color-mix(in oklab, var(--color-primary) 10%, transparent);
}

.collection-action-btn.danger {
  border-color: color-mix(in oklab, var(--color-error, #c44) 40%, transparent);
  color: var(--color-error, #c44);
}

.collection-action-btn.danger:hover:not(:disabled) {
  background: color-mix(in oklab, var(--color-error, #c44) 8%, transparent);
}

.collection-action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.collection-actions-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
  flex-wrap: wrap;
}

/* ── 添加已有日记弹窗 ── */
.add-diary-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  backdrop-filter: blur(3px);
}

.add-diary-dialog {
  background: var(--color-surface);
  border-radius: 14px;
  width: 92%;
  max-width: 420px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 40px rgba(0,0,0,0.10);
  border: 1px solid var(--color-border);
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

.add-diary-loading,
.add-diary-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: var(--color-text-muted);
  font-size: 13px;
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
  border-radius: 10px;
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
  opacity: 0.5;
  cursor: default;
}

.add-diary-checkbox {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 1.5px solid var(--color-border);
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
  background: color-mix(in oklab, var(--color-surface-soft) 80%, transparent);
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
  border-top: 1px solid var(--color-border);
}

.add-diary-count {
  font-size: 12px;
  color: var(--color-text-muted);
}

.add-diary-foot-actions {
  display: flex;
  gap: 8px;
}
</style>