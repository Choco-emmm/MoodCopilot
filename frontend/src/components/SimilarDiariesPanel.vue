<template>
  <article class="panel">
    <div class="section-title compact">
      <div>
        <p class="eyebrow">同频推荐</p>
        <h2>相似心情的人</h2>
      </div>
    </div>

    <div v-if="diaries.length" class="mini-feed similar-feed">
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

<style scoped>
article.panel {
  padding: 24px;
  display: grid;
  gap: 20px;
}
.section-title {
  margin-bottom: 0;
}
.similar-feed {
  gap: 16px;
}
.similar-item {
  display: grid;
  gap: 8px;
  padding: 16px 18px;
  border-radius: 10px;
}
.similar-item span {
  font-size: 12px;
  color: var(--color-text-muted);
}
.similar-item strong {
  font-size: 14px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
