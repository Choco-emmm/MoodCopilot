<template>
  <main class="app-shell">
    <AppHeader />
    <div v-if="diary" class="analysis-grid">
      <AiAnalysisCard :diary="diary" />
      <SimilarDiariesPanel :diaries="store.similarDiaries" @select="selectDiary" />
    </div>
    <n-empty v-else description="日记不存在" />
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { diaryApi } from '../api'
import AppHeader from '../components/AppHeader.vue'
import AiAnalysisCard from '../components/AiAnalysisCard.vue'
import SimilarDiariesPanel from '../components/SimilarDiariesPanel.vue'
import { useDiaryStore, type Diary } from '../stores/diary'

const route = useRoute()
const store = useDiaryStore()
const diary = ref<Diary | null>(null)

onMounted(async () => {
  const id = Number(route.params.id)
  try {
    const res = await diaryApi.get(id)
    diary.value = store.normalize(res.data.data)
    await store.loadSimilar(id)
  } catch {
    diary.value = null
  }
})

function selectDiary(d: Diary) {
  diary.value = store.normalize(d)
  store.loadSimilar(d.id)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>
