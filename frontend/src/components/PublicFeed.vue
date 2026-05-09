<template>
  <article class="panel">
    <div class="section-title compact">
      <div>
        <p class="eyebrow">公开日记流</p>
        <h2>温和回应</h2>
      </div>
      <n-button quaternary size="small" :loading="loading" @click="$emit('refresh')">刷新</n-button>
    </div>

    <div v-if="diaries.length" class="feed">
      <DiaryFeedItem
        v-for="diary in diaries"
        :key="diary.id"
        :diary="diary"
        @select="$emit('select', $event)"
        @resonate="$emit('resonate', $event)"
        @comment="(d, c) => $emit('comment', d, c)"
      />
    </div>
    <n-empty v-else description="暂无公开日记" />
  </article>
</template>

<script setup lang="ts">
import DiaryFeedItem from './DiaryFeedItem.vue'
import type { Diary } from '../stores/diary'

defineProps<{ diaries: Diary[]; loading: boolean }>()
defineEmits<{
  refresh: []
  select: [diary: Diary]
  resonate: [diary: Diary]
  comment: [diary: Diary, content: string]
}>()
</script>
