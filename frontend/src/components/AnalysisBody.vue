<template>
  <!-- AI 分析 -->
  <template v-if="diary.analysis?.summary">
    <div class="section-title compact">
      <div>
        <p class="eyebrow">AI 分析</p>
        <h2>
          {{ diary.analysis.moodLabel }} · 强度 {{ diary.analysis.moodIntensity }}/5
          <span v-if="diary.analysis.secondaryMoods?.length" class="secondary-moods">
            <span
              v-for="m in diary.analysis.secondaryMoods"
              :key="m"
              class="secondary-mood-tag"
            >{{ m }}</span>
          </span>
        </h2>
      </div>
    </div>

    <div class="mood-meter" aria-label="情绪强度">
      <span v-for="step in 5" :key="step" :class="{ filled: step <= diary.analysis.moodIntensity }" />
    </div>

    <p class="summary">{{ diary.analysis.summary }}</p>
    <p class="feedback">{{ diary.analysis.feedback }}</p>
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
import { NProgress } from 'naive-ui'
import type { Diary } from '../stores/diary'

defineProps<{ diary: Diary }>()
</script>

<style scoped>
.secondary-moods {
  display: inline-flex;
  gap: 6px;
  margin-left: 8px;
  vertical-align: middle;
}
.secondary-mood-tag {
  font-size: 0.72rem;
  font-weight: 400;
  padding: 1px 8px;
  border-radius: 10px;
  background: var(--color-bg-secondary, #f0f0f0);
  color: var(--color-text-secondary, #888);
  border: 1px solid var(--color-border, #e0e0e0);
}
</style>
