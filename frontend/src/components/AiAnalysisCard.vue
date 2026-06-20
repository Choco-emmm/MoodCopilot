<template>
  <article v-if="!compact" class="panel analysis-panel">
    <div class="diary-content-section">
      <div class="diary-author-row">
        <img v-if="diary.authorAvatar" :src="diary.authorAvatar" class="avatar avatar-img" loading="lazy" decoding="async" />
        <span v-else class="avatar">{{ diary.authorName.charAt(0) }}</span>
        <span class="author-name">{{ diary.authorName }}</span>
        <span class="diary-time">{{ formatTime(diary.createdAt) }}</span>
        <n-tag :type="diary.visibility === 'BANNED' ? 'error' : (diary.visibility === 'PUBLIC' ? 'success' : 'default')" round size="small">
          {{ diary.visibility === 'BANNED' ? '屏蔽中' : (diary.visibility === 'PUBLIC' ? '公开' : '私密') }}
        </n-tag>
      </div>
      <div class="diary-content md-content" v-html="renderMd(diary.content)"></div>
    </div>
    <div class="section-divider" />
    <AnalysisBody :diary="diary" />
  </article>

  <AnalysisBody v-else :diary="diary" />
</template>

<script setup lang="ts">
import { NProgress } from 'naive-ui'
import type { Diary } from '../stores/diary'
import { renderSafeMarkdown } from '../utils/markdown'
import AnalysisBody from './AnalysisBody.vue'

function renderMd(text: string) {
  return renderSafeMarkdown(text)
}

defineProps<{ diary: Diary; compact?: boolean }>()

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>
