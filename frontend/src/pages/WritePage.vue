<template>
  <main class="app-shell">
    <AppHeader />

    <DiaryComposer />

    <section class="write-history-section">
      <MyDiaryList
        :diaries="store.myDiaries"
        :active-diary-id="store.activeDiary?.id ?? null"
        @select="selectDiary"
        @delete="handleDelete"
      />
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import DiaryComposer from '../components/DiaryComposer.vue'
import MyDiaryList from '../components/MyDiaryList.vue'
import { useDiaryStore, type Diary } from '../stores/diary'

const store = useDiaryStore()

onMounted(() => store.fetchDiaries())

function selectDiary(diary: Diary) {
  store.activeDiary = store.activeDiary?.id === diary.id ? null : diary
}

async function handleDelete(diary: Diary) {
  if (confirm('确定删除这篇日记吗？')) {
    await store.deleteDiary(diary.id)
  }
}
</script>
