<template>
  <article class="panel">
    <div class="section-title compact">
      <div>
        <p class="eyebrow">同频推荐</p>
        <h2>相似心情的人</h2>
      </div>
    </div>

    <div v-if="diaries.length" class="mini-feed">
      <button
        v-for="diary in diaries"
        :key="diary.id"
        class="similar-item"
        type="button"
        @click="$emit('select', diary)"
      >
        <span>{{ diary.authorName }} · {{ formatTime(diary.createdAt) }}</span>
        <strong>{{ diary.content }}</strong>
      </button>
    </div>
    <n-empty v-else description="暂时没有更多相似日记" />
  </article>
</template>

<script setup lang="ts">
import type { Diary } from '../stores/diary'

defineProps<{ diaries: Diary[] }>()
defineEmits<{ select: [diary: Diary] }>()

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>
