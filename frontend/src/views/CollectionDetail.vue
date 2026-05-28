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
      <button
        v-if="isOwner"
        class="collection-delete-btn"
        :disabled="deleting"
        @click="handleDelete"
      >
        {{ deleting ? '删除中...' : '删除合集' }}
      </button>
    </div>

    <div class="panel p-4 md:p-6 max-w-3xl mx-auto w-full">

      <div v-if="loading" class="text-center py-12">
        <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--color-primary)]"></div>
      </div>

      <div v-else-if="diaries?.length === 0" class="empty-state">
        <p>这个合集中还没有日记</p>
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
import { collectionApi } from '../api'
import { useAuthStore } from '../stores/auth'
import draggable from 'vuedraggable'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collectionId = Number(route.params.id)

const collection = ref<any>(null)
const diaries = ref<any[]>([])
const loading = ref(true)
const deleting = ref(false)

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
    router.push('/')
  } catch (e: any) {
    const msg = e?.response?.data?.message || '删除失败'
    window.$message?.error(msg)
  } finally {
    deleting.value = false
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

.collection-delete-btn {
  display: inline-block;
  margin-top: 12px;
  padding: 6px 18px;
  border: 1px solid color-mix(in oklab, var(--color-error, #c44) 40%, transparent);
  border-radius: 8px;
  background: transparent;
  color: var(--color-error, #c44);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;
}

.collection-delete-btn:hover:not(:disabled) {
  background: color-mix(in oklab, var(--color-error, #c44) 8%, transparent);
}

.collection-delete-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>