<template>
  <!-- AI 分析 -->
  <template v-if="diary.analysis?.summary">
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
</template>

<script setup lang="ts">
import { NTag, NProgress } from 'naive-ui'
import type { Diary } from '../stores/diary'

defineProps<{ diary: Diary }>()
</script>
