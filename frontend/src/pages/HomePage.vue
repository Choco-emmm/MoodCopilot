<template>
  <main class="app-shell">
    <AppHeader />

    <DiaryComposer />

    <section v-if="store.activeDiary" class="analysis-grid">
      <AiAnalysisCard :diary="store.activeDiary" />
      <SimilarDiariesPanel :diaries="store.similarDiaries" @select="selectDiary" />
    </section>

    <section class="content-grid">
      <MyDiaryList :diaries="store.myDiaries" @select="selectDiary" />
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
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import DiaryComposer from '../components/DiaryComposer.vue'
import AiAnalysisCard from '../components/AiAnalysisCard.vue'
import SimilarDiariesPanel from '../components/SimilarDiariesPanel.vue'
import MyDiaryList from '../components/MyDiaryList.vue'
import PublicFeed from '../components/PublicFeed.vue'
import { useDiaryStore, type Diary } from '../stores/diary'

const router = useRouter()
const store = useDiaryStore()

onMounted(() => store.fetchDiaries())

function selectDiary(diary: Diary) {
  store.activeDiary = diary
  store.loadSimilar(diary.id)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>
