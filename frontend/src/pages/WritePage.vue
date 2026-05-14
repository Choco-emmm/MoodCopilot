<template>
  <main class="app-shell">
    <AppHeader />

    <DiaryComposer />

    <section class="write-history-section">
      <MyDiaryList
        :diaries="store.myDiaries"
        @select="selectDiary"
        @edit="editDiary"
        @delete="handleDelete"
      />
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import DiaryComposer from '../components/DiaryComposer.vue'
import MyDiaryList from '../components/MyDiaryList.vue'
import { useDiaryStore, type Diary } from '../stores/diary'

const router = useRouter()
const store = useDiaryStore()

onMounted(() => store.fetchDiaries())

function selectDiary(diary: Diary) {
  router.push(`/diary/${diary.id}`)
}

function editDiary(diary: Diary) {
  router.push(`/diary/${diary.id}?edit=1`)
}

async function handleDelete(diary: Diary) {
  if (confirm('确定删除这篇日记吗？')) {
    await store.deleteDiary(diary.id)
  }
}
</script>
