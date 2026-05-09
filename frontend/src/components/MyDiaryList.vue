<template>
  <article class="panel">
    <div class="section-title compact">
      <div>
        <p class="eyebrow">我的日记</p>
        <h2>近期记录</h2>
      </div>
    </div>

    <div v-if="diaries.length" class="diary-list">
      <button
        v-for="diary in diaries"
        :key="diary.id"
        class="my-diary"
        type="button"
        @click="$emit('select', diary)"
      >
        <span class="avatar avatar-sm">{{ diary.authorName.charAt(0) }}</span>
        <span class="my-diary-info">
          <strong class="my-diary-content">{{ diary.content }}</strong>
          <small>{{ formatTime(diary.createdAt) }} · {{ diary.analysis?.moodLabel || '分析中...' }}</small>
        </span>
        <button
          class="diary-delete-btn"
          title="删除日记"
          @click.stop="$emit('delete', diary)"
        >&times;</button>
      </button>
    </div>
    <n-empty v-else description="还没有写过日记" />
  </article>
</template>

<script setup lang="ts">
import type { Diary } from '../stores/diary'

defineProps<{ diaries: Diary[] }>()
defineEmits<{ select: [diary: Diary]; delete: [diary: Diary] }>()

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>
