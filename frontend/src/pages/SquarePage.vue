<template>
  <main class="app-shell">
    <AppHeader />

    <div class="today-side">
      <router-link v-if="matchDiary" :to="'/diary/' + matchDiary.id" class="today-match-mini">
        <span class="today-side-label">今日同频</span>
        <span class="today-side-snippet">「{{ matchDiary.content?.length > 42 ? matchDiary.content.slice(0, 42) + '...' : matchDiary.content }}」</span>
      </router-link>

      <router-link v-else to="/write" class="today-match-mini">
        <span class="today-side-label">今日同频</span>
        <span class="today-side-snippet">写下今天后，MoodCopilot 会帮你找相似处境的人。</span>
      </router-link>
    </div>


    <div class="chat-tease">
      <router-link to="/chat" class="chat-tease-link">
        <span class="chat-tease-icon">&#128302;</span>
        <span class="chat-tease-text">和 MoodCopilot 聊聊今天的心情</span>
        <span class="chat-tease-arrow">&rarr;</span>
      </router-link>
    </div>

    <PublicFeed
      :diaries="store.publicDiaries"
      :loading="store.loading"
      :has-more="store.hasMore"
      @refresh="store.fetchDiaries()"
      @load-more="store.loadMorePublic()"
      @resonate="(d: Diary) => store.resonate(d.id)"
      @open-detail="(d: Diary) => router.push(`/diary/${d.id}`)"
      @comment="(d: Diary, c: string, pid?: number) => store.addComment(d.id, c, pid)"
      @delete-comment="(d: Diary, commentId: number) => store.deleteComment(d.id, commentId)"
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
const matchDiary = ref<any>(null)

onMounted(async () => {
  store.fetchDiaries()
  try { const res = await diaryApi.todayMatch(); matchDiary.value = res.data.data } catch { /* ignore */ }
})

</script>
