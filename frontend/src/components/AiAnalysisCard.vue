<template>
  <article class="panel analysis-panel">
    <!-- 日记原文 -->
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

    <!-- AI 分析 -->
    <template v-if="diary.analysis">
      <div class="section-title compact">
        <div>
          <p class="eyebrow">AI 分析</p>
          <h2>{{ diary.analysis.moodLabel }} · 强度 {{ diary.analysis.moodIntensity }}/5</h2>
        </div>
      </div>

      <div class="mood-meter" aria-label="情绪强度">
        <span v-for="step in 5" :key="step" :class="{ filled: step <= diary.analysis.moodIntensity }" />
      </div>

      <p class="summary">{{ diary.analysis.summary }}</p>
      <p class="feedback">{{ diary.analysis.feedback }}</p>

      <div class="tag-row">
        <n-tag v-for="topic in diary.analysis.topicLabels" :key="topic" type="info" round>
          {{ topic }}
        </n-tag>
      </div>
    </template>

    <template v-else>
      <div class="section-title compact">
        <div>
          <p class="eyebrow">AI 分析</p>
          <h2>分析中...</h2>
        </div>
      </div>
      <n-progress
        :percentage="0"
        :indicator-placement="'inside'"
        :processing="true"
        :border-radius="6"
        style="width: 100%"
      />
      <p class="feedback">AI 正在解读你的情绪，稍等片刻...</p>
    </template>
  </article>
</template>

<script setup lang="ts">
import { NProgress } from 'naive-ui'
import type { Diary } from '../stores/diary'

defineProps<{ diary: Diary }>()

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>
