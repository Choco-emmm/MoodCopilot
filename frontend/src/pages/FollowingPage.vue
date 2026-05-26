<template>
  <main class="app-shell">
    <AppHeader />

    <!-- Editorial Navigation -->
    <header class="editorial-header">
      <div class="editorial-nav">
        <router-link to="/" class="editorial-link">广场 <span class="en-sub">Square</span></router-link>
        <span class="editorial-separator">/</span>
        <router-link to="/following" class="editorial-link active">关注 <span class="en-sub">Following</span></router-link>
      </div>
    </header>

    <div class="following-page">

      <div v-if="errorMessage" class="empty-state">
        <p>{{ errorMessage }}</p>
        <n-button type="primary" @click="loadFirst">重试</n-button>
      </div>

      <div v-else-if="diaries.length === 0 && !loading" class="empty-state">
        <p>还没有关注任何人，去公开日记流发现有趣的人吧～</p>
        <n-button type="primary" @click="router.push('/')">去逛逛</n-button>
      </div>

      <div v-else class="feed">
        <DynamicScroller
          :items="diaries"
          :min-item-size="200"
          key-field="id"
          page-mode
        >
          <template #default="{ item, index, active }">
            <DynamicScrollerItem
              :item="item"
              :active="active"
              :size-dependencies="[
                item.content,
                item.images?.length,
                item.comments?.length,
                item.musicMeta?.songUrl
              ]"
              :data-index="index"
            >
              <DiaryFeedItem
                :diary="item"
                :enable-comments="false"
                :compact="true"
                :preview-limit="120"
                :show-expand-toggle="false"
                @resonate="(d: Diary) => store.resonate(d.id, d)"
                @open-detail="(d: Diary) => router.push(`/diary/${d.id}`)"
              />
            </DynamicScrollerItem>
          </template>
        </DynamicScroller>

        <div v-if="hasMore" ref="sentinel" class="loading-hint">
          <n-spin v-if="loading" size="small" />
          <n-button v-else block secondary @click="loadMore">加载更多</n-button>
        </div>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import DiaryFeedItem from '../components/DiaryFeedItem.vue'
import { useDiaryStore, type Diary } from '../stores/diary'
import { useInfiniteScroll } from '../composables/useScrollManager'
import { diaryApi } from '../api'

const router = useRouter()
const store = useDiaryStore()
const diaries = ref<Diary[]>([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const errorMessage = ref('')
const sentinel = ref<HTMLElement | null>(null)

useInfiniteScroll(sentinel, loadMore, {
  enabled: hasMore,
  rootMargin: '300px',
})

onMounted(async () => {
  await loadFirst()
})

async function loadFirst() {
  loading.value = true
  errorMessage.value = ''
  try {
    page.value = 1
    const res = await diaryApi.following(1, 20)
    const data = res.data?.data
    const items: Diary[] = Array.isArray(data?.items)
      ? data.items.map(store.normalize)
      : Array.isArray(data)
        ? data.map(store.normalize)
        : []
    diaries.value = items
      .sort((a: Diary, b: Diary) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    hasMore.value = items.length >= 20
  } catch (e: any) {
    diaries.value = []
    hasMore.value = false
    errorMessage.value = e?.response?.data?.message || '关注动态加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    page.value++
    const res = await diaryApi.following(page.value, 20)
    const data = res.data?.data
    const items: Diary[] = Array.isArray(data?.items)
      ? data.items.map(store.normalize)
      : Array.isArray(data)
        ? data.map(store.normalize)
        : []
    diaries.value.push(...items)
    hasMore.value = items.length >= 20
  } catch (e: any) {
    page.value = Math.max(1, page.value - 1)
    errorMessage.value = e?.response?.data?.message || '加载更多失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}
</script>
