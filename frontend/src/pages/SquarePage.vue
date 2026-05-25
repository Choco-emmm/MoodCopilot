<template>
  <main class="app-shell">
    <AppHeader />

    <div class="today-side">
      <router-link v-if="matchDiary" :to="'/diary/' + matchDiary.id" class="today-match-mini">
        <span class="today-side-label">今日同频</span>
        <span class="today-side-snippet">「{{ snippet(matchDiary) }}」</span>
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
    />

    <router-link to="/task-center" class="task-fab" title="任务中心">
      <span class="task-fab-icon">📋</span>
    </router-link>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import PublicFeed from '../components/PublicFeed.vue'
import { useDiaryStore, type Diary } from '../stores/diary'
import { diaryApi } from '../api'
import { renderSafeMarkdown, stripMarkdown } from '../utils/markdown'
import { logWarn } from '../utils/logger'

function snippet(diary: any): string {
  if (!diary?.content) return ''
  const text = stripMarkdown(diary.content)
  return text.length > 42 ? text.slice(0, 42) + '...' : text
}

const router = useRouter()
const store = useDiaryStore()
const matchDiary = ref<any>(null)

onMounted(async () => {
  store.fetchDiaries()
  try { const res = await diaryApi.todayMatch(); matchDiary.value = res.data.data } catch (e) { logWarn('square', '加载今日同频失败', e) }
})

</script>

<style scoped>
.task-fab {
  position: fixed;
  right: max(20px, calc((100vw - 1080px) / 2 + 20px));
  bottom: 112px;
  z-index: 8000;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--color-primary);
  color: var(--color-on-primary);
  box-shadow: 0 4px 20px color-mix(in srgb, var(--color-primary) 36%, transparent 64%);
  text-decoration: none;
  transition: transform 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out);
}

.task-fab:hover {
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 6px 28px color-mix(in srgb, var(--color-primary) 44%, transparent 56%);
}

.task-fab:active {
  transform: scale(0.95);
}

.task-fab-icon {
  font-size: 22px;
  line-height: 1;
}

@media (max-width: 780px) {
  .task-fab {
    right: 16px;
    bottom: 108px;
    width: 46px;
    height: 46px;
  }
}
</style>

