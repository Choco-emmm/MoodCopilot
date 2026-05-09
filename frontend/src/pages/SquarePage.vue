<template>
  <main class="app-shell">
    <AppHeader />

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
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import PublicFeed from '../components/PublicFeed.vue'
import { useDiaryStore, type Diary } from '../stores/diary'

const router = useRouter()
const store = useDiaryStore()

onMounted(() => store.fetchDiaries())

function selectDiary(diary: Diary) {
  router.push(`/diary/${diary.id}`)
}
</script>
