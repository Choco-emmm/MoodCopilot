<template>
  <main class="app-shell">
    <AppHeader />

    <section class="panel feedback-page-panel">
      <header class="feedback-page-header">
        <div>
          <p class="feedback-eyebrow">FEEDBACK</p>
          <h2 class="feedback-page-title">意见与反馈</h2>
          <p class="feedback-page-subtitle">
            你的每一条反馈与对话都仅对你和管理员可见，支持持续双向回复沟通。
          </p>
        </div>
        <div v-if="auth.isAdmin" class="feedback-tab-switcher" role="tablist" aria-label="反馈视图">
          <button
            type="button"
            role="tab"
            :aria-selected="activeTab === 'mine'"
            :class="['feedback-tab-btn', { active: activeTab === 'mine' }]"
            @click="switchTab('mine')"
          >
            我的反馈
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="activeTab === 'admin'"
            :class="['feedback-tab-btn', { active: activeTab === 'admin' }]"
            @click="switchTab('admin')"
          >
            全部反馈 (管理员)
          </button>
        </div>
      </header>

      <!-- 提交新反馈（我的反馈视图展示） -->
      <section v-if="activeTab === 'mine'" class="feedback-composer-section">
        <div class="feedback-composer-head">
          <h3 class="feedback-section-heading">提交新反馈</h3>
          <span class="feedback-char-count">{{ draftContent.length }}/1000</span>
        </div>
        <n-input
          v-model:value="draftContent"
          type="textarea"
          :maxlength="1000"
          :autosize="{ minRows: 3, maxRows: 7 }"
          placeholder="遇到了什么问题？或者有什么想要的新功能与改进建议？写在这里，管理员回复后会第一时间通知你..."
          :disabled="submitting"
        />
        <div class="feedback-composer-foot">
          <span class="feedback-composer-note">提交及后续回复都会邮件提醒管理员及时处理</span>
          <n-button
            type="primary"
            size="small"
            :loading="submitting"
            :disabled="!draftContent.trim() || submitting"
            @click="submitNewFeedback"
          >
            提交反馈
          </n-button>
        </div>
      </section>

      <!-- 管理员状态筛选 -->
      <section v-if="activeTab === 'admin' && auth.isAdmin" class="feedback-filter-bar">
        <button
          v-for="opt in statusFilters"
          :key="opt.value"
          type="button"
          :class="['feedback-filter-pill', { active: adminStatusFilter === opt.value }]"
          @click="selectStatusFilter(opt.value)"
        >
          {{ opt.label }}
        </button>
        <span class="feedback-total-count">共 {{ total }} 条</span>
      </section>

      <div class="feedback-list-header">
        <h3 class="feedback-section-heading">
          {{ activeTab === 'admin' ? '全部用户反馈' : '我提交的反馈' }}
          <span class="feedback-count-badge">({{ total }})</span>
        </h3>
        <n-button size="tiny" quaternary :loading="loading" @click="loadList(true)">刷新</n-button>
      </div>

      <div v-if="loading && !items.length" class="feedback-state">
        <n-spin size="small" />
      </div>

      <div v-else-if="loadError && !items.length" class="feedback-state">
        <p class="feedback-error-text">{{ loadError }}</p>
        <n-button size="small" secondary @click="loadList(true)">重新加载</n-button>
      </div>

      <n-empty
        v-else-if="!items.length"
        :description="activeTab === 'admin' ? '暂无匹配的用户反馈' : '还没有提交过反馈'"
        class="feedback-empty"
      />

      <template v-else>
        <FeedbackThreadList
          :items="items"
          :is-admin-mode="activeTab === 'admin' && auth.isAdmin"
          :highlight-id="highlightId"
          @updated="replaceItem"
        />

        <div v-if="hasMore" class="feedback-load-more">
          <n-button secondary size="small" :loading="loadingMore" @click="loadMore">
            加载更多
          </n-button>
        </div>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NEmpty, NInput, NSpin } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import FeedbackThreadList from '../components/feedback/FeedbackThreadList.vue'
import { suggestionApi, type SuggestionItem } from '../api'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeTab = ref<'mine' | 'admin'>('mine')
const adminStatusFilter = ref<string>('ALL')
const statusFilters = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待回复' },
  { value: 'READ', label: '跟进中' },
  { value: 'RESOLVED', label: '已解决' },
]

const draftContent = ref('')
const submitting = ref(false)
const items = ref<SuggestionItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)
const loadingMore = ref(false)
const loadError = ref('')

const highlightId = computed(() => {
  const raw = Number(route.query.id)
  return Number.isFinite(raw) && raw > 0 ? raw : null
})

const hasMore = computed(() => items.value.length < total.value)

async function initPage() {
  if (!auth.userId) {
    await auth.fetchProfile()
  }

  const requestedTab = route.query.tab === 'admin' && auth.isAdmin ? 'admin' : 'mine'
  activeTab.value = requestedTab

  if (highlightId.value) {
    try {
      const targetRes = await suggestionApi.get(highlightId.value)
      const target: SuggestionItem | null = targetRes.data?.data ?? null
      if (target && auth.isAdmin && auth.userId != null && target.userId !== auth.userId) {
        activeTab.value = 'admin'
      }
    } catch {
      // Fall back to regular list load
    }
  }

  await loadList(true)
  if (highlightId.value) {
    await ensureHighlightedItemPresent(highlightId.value)
    await scrollToHighlighted(highlightId.value)
  }
}

async function ensureHighlightedItemPresent(targetId: number) {
  if (items.value.some((i) => i.id === targetId)) return
  try {
    const res = await suggestionApi.get(targetId)
    const found: SuggestionItem | null = res.data?.data ?? null
    if (found) {
      items.value = [found, ...items.value.filter((i) => i.id !== found.id)]
    }
  } catch {
    // Ignore if inaccessible
  }
}

async function scrollToHighlighted(targetId: number) {
  await nextTick()
  const el = document.getElementById(`feedback-item-${targetId}`)
  el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

async function loadList(reset = true) {
  if (reset) {
    loading.value = true
    page.value = 1
  } else {
    if (loadingMore.value || !hasMore.value) return
    loadingMore.value = true
  }
  loadError.value = ''

  try {
    const nextPage = reset ? 1 : page.value + 1
    const res = activeTab.value === 'admin' && auth.isAdmin
      ? await suggestionApi.adminList(nextPage, pageSize, adminStatusFilter.value)
      : await suggestionApi.myList(nextPage, pageSize)

    const payload = res.data?.data || {}
    const fetched: SuggestionItem[] = payload.items || []
    total.value = Number(payload.total || 0)

    if (reset) {
      items.value = fetched
      page.value = 1
    } else {
      const existingIds = new Set(items.value.map((item) => item.id))
      items.value.push(...fetched.filter((item) => !existingIds.has(item.id)))
      page.value = nextPage
    }
  } catch (err: any) {
    loadError.value = err?.response?.data?.message || '加载反馈失败，请稍后重试'
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function loadMore() {
  void loadList(false)
}

function switchTab(tab: 'mine' | 'admin') {
  if (activeTab.value === tab) return
  activeTab.value = tab
  void router.replace({
    path: '/feedback',
    query: tab === 'admin' ? { tab: 'admin' } : {},
  })
  void loadList(true)
}

function selectStatusFilter(status: string) {
  if (adminStatusFilter.value === status) return
  adminStatusFilter.value = status
  void loadList(true)
}

async function submitNewFeedback() {
  const content = draftContent.value.trim()
  if (!content || submitting.value) return

  submitting.value = true
  try {
    const res = await suggestionApi.submit(content)
    const created: SuggestionItem | null = res.data?.data ?? null
    draftContent.value = ''
    if (created) {
      items.value = [created, ...items.value.filter((i) => i.id !== created.id)]
      total.value += 1
    } else {
      await loadList(true)
    }
    window.$message?.success('反馈已提交，感谢你的建议！')
  } catch (err: any) {
    window.$message?.error(err?.response?.data?.message || '提交反馈失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function replaceItem(updated: SuggestionItem) {
  const idx = items.value.findIndex((i) => i.id === updated.id)
  if (idx >= 0) {
    items.value.splice(idx, 1, updated)
  }
}

onMounted(() => {
  void initPage()
})

watch(() => [route.query.id, route.query.tab], () => {
  if (route.path === '/feedback') {
    void initPage()
  }
})
</script>

<style scoped>
.feedback-page-panel {
  max-width: 820px;
  margin: 0 auto;
  padding: 24px 28px;
}

.feedback-page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--color-border);
}

.feedback-eyebrow {
  margin: 0 0 4px;
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--color-text-muted);
}

.feedback-page-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text);
}

.feedback-page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.feedback-tab-switcher {
  display: inline-flex;
  gap: 6px;
  padding: 3px;
  border-radius: 8px;
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
}

.feedback-tab-btn {
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.feedback-tab-btn.active {
  background: var(--color-surface);
  color: var(--color-text);
  font-weight: 600;
}

.feedback-composer-section {
  padding: 20px 0;
  border-bottom: 1px solid var(--color-border);
}

.feedback-composer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.feedback-section-heading {
  margin: 0;
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.feedback-count-badge {
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 400;
  color: var(--color-text-muted);
}

.feedback-char-count {
  font-size: 12px;
  color: var(--color-text-light);
}

.feedback-composer-foot {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.feedback-composer-note {
  font-size: 12px;
  color: var(--color-text-muted);
}

.feedback-filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 16px 0 6px;
}

.feedback-filter-pill {
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease, color 0.18s ease;
}

.feedback-filter-pill.active {
  border-color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 12%, transparent);
  color: var(--color-primary);
  font-weight: 600;
}

.feedback-total-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-muted);
}

.feedback-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 18px;
  padding-bottom: 4px;
}

.feedback-state {
  padding: 36px 0;
  text-align: center;
}

.feedback-error-text {
  margin: 0 0 12px;
  color: var(--color-error);
  font-size: 13px;
}

.feedback-empty {
  padding: 40px 0;
}

.feedback-load-more {
  margin-top: 16px;
  text-align: center;
}

@media (max-width: 640px) {
  .feedback-page-panel {
    padding: 18px 16px;
  }
}
</style>
