<template>
  <main class="app-shell">
    <AppHeader />

    <div v-if="matchDiary" class="today-match">
      <router-link :to="'/diary/' + matchDiary.id" class="today-match-link">
        <span class="today-match-label">今日同频</span>
        <span class="today-match-mood">{{ matchDiary.analysis?.moodLabel }}</span>
        <span class="today-match-content">{{ matchDiary.content.slice(0, 40) }}{{ matchDiary.content.length > 40 ? '...' : '' }}</span>
        <span class="today-match-arrow">&rarr;</span>
      </router-link>
    </div>

    <div v-if="status" class="daily-status" :class="{ done: status.todayHasDiary }">
      <span class="daily-status-icon">{{ status.todayHasDiary ? '&#9745;' : '&#9744;' }}</span>
      <span class="daily-status-text">
        <template v-if="status.todayHasDiary">
          已记录{{ status.streak }}天连更{{ status.yesterdayMood ? '，昨天心情「' + status.yesterdayMood + '」' : '' }}
        </template>
        <template v-else>
          新的一天，写下今天的心情吧
        </template>
      </span>
      <router-link v-if="!status.todayHasDiary" to="/write" class="daily-status-write">写日记</router-link>
    </div>

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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import PublicFeed from '../components/PublicFeed.vue'
import { useDiaryStore, type Diary } from '../stores/diary'
import { diaryApi } from '../api'

const router = useRouter()
const store = useDiaryStore()
const status = ref<{ todayHasDiary: boolean; streak: number; yesterdayMood: string } | null>(null)
const matchDiary = ref<any>(null)

onMounted(async () => {
  store.fetchDiaries()
  try { const res = await diaryApi.todayStatus(); status.value = res.data.data } catch { /* ignore */ }
  try { const res = await diaryApi.todayMatch(); matchDiary.value = res.data.data } catch { /* ignore */ }
})

function selectDiary(diary: Diary) {
  router.push(`/diary/${diary.id}`)
}
</script>
