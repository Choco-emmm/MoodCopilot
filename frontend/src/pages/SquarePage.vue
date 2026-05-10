<template>
  <main class="app-shell">
    <AppHeader />

    <!-- 今日概览面板 -->
    <section class="today-panel">
      <!-- 左侧：状态 + 陪跑 -->
      <div class="today-main">
        <div v-if="status" class="today-status-row">
          <span class="today-status-dot" :class="{ filled: status.todayHasDiary }" />
          <span class="today-status-label">
            <template v-if="status.todayHasDiary">
              连续记录 <strong>{{ status.streak }}</strong> 天{{ status.yesterdayMood ? ' · 昨天「' + status.yesterdayMood + '」' : '' }}
            </template>
            <template v-else>
              新的一天 — <router-link to="/write" class="today-write-link">写下心情</router-link>
            </template>
          </span>
        </div>

      </div>

      <!-- 右侧：同频 + 社区 -->
      <div class="today-side">
        <router-link v-if="matchDiary" :to="'/diary/' + matchDiary.id" class="today-match-mini">
          <span class="today-side-label">有人和你感同身受</span>
          <div class="today-match-author">
            <span class="avatar-xs">{{ matchDiary.authorName?.charAt(0) }}</span>
            <span class="author-name-xs">{{ matchDiary.authorName }}</span>
          </div>
          <span class="today-side-snippet">「{{ matchDiary.content?.length > 30 ? matchDiary.content.slice(0, 30) + '...' : matchDiary.content }}」</span>
        </router-link>

        <div v-if="moods && Object.keys(moods).length" class="today-mood-cloud">
          <span class="today-side-label">社区共鸣</span>
          <span class="today-side-text">
            <template v-for="(count, mood) in topMoods" :key="mood">
              <span class="mood-chip">{{ mood }} <em>{{ count }}</em></span>
            </template>
          </span>
        </div>
      </div>
    </section>

    <div class="chat-tease">
      <router-link to="/chat" class="chat-tease-link">
        <span class="chat-tease-icon">&#128302;</span>
        <span class="chat-tease-text">和小情绪聊聊今天的心情</span>
        <span class="chat-tease-arrow">&rarr;</span>
      </router-link>
    </div>

    <PublicFeed
      :diaries="store.publicDiaries"
      :loading="store.loading"
      :has-more="store.hasMore"
      @refresh="store.fetchDiaries()"
      @load-more="store.loadMorePublic()"
      @select="selectDiary"
      @resonate="(d: Diary) => store.resonate(d.id)"
      @comment="(d: Diary, c: string, pid?: number) => store.addComment(d.id, c, pid)"
    />
  </main>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import PublicFeed from '../components/PublicFeed.vue'
import { useDiaryStore, type Diary } from '../stores/diary'
import { diaryApi } from '../api'

const router = useRouter()
const store = useDiaryStore()
const status = ref<{ todayHasDiary: boolean; streak: number; yesterdayMood: string } | null>(null)
const matchDiary = ref<any>(null)
const moods = ref<Record<string, number> | null>(null)
const topMoods = computed(() => {
  if (!moods.value) return []
  return Object.entries(moods.value)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
})

onMounted(async () => {
  store.fetchDiaries()
  try { const res = await diaryApi.todayStatus(); status.value = res.data.data } catch { /* ignore */ }
  try { const res = await diaryApi.todayMatch(); matchDiary.value = res.data.data } catch { /* ignore */ }
  try { const res = await diaryApi.communityMood(); moods.value = res.data.data } catch { /* ignore */ }
})

function selectDiary(diary: Diary) {
  router.push(`/diary/${diary.id}`)
}
</script>
