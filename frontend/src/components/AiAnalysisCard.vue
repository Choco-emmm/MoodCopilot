<template>
  <article v-if="!compact" class="panel analysis-panel">
    <div class="diary-content-section">
      <div class="diary-author-row">
        <span class="avatar">{{ diary.authorName.charAt(0) }}</span>
        <span class="author-name">{{ diary.authorName }}</span>
        <span class="diary-time">{{ formatTime(diary.createdAt) }}</span>
        <n-tag :type="diary.visibility === 'PUBLIC' ? 'success' : 'default'" round size="small">
          {{ diary.visibility === 'PUBLIC' ? '公开' : '私密' }}
        </n-tag>
      </div>
      <p class="diary-content">{{ diary.content }}</p>
    </div>
    <div class="section-divider" />
    <AnalysisBody :diary="diary" />
  </article>

  <AnalysisBody v-else :diary="diary" />
</template>

<script setup lang="ts">
import { NProgress } from 'naive-ui'
import type { Diary } from '../stores/diary'
import AnalysisBody from './AnalysisBody.vue'

defineProps<{ diary: Diary; compact?: boolean }>()

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>
