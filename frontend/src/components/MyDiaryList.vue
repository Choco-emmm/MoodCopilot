<template>
  <article class="panel my-diary-panel">
    <div class="section-title compact">
      <div>
        <p class="eyebrow">我的日记</p>
        <h2>近期记录</h2>
      </div>
    </div>

    <div v-if="diaries.length" class="diary-list">
      <template v-for="diary in diaries" :key="diary.id">
        <div class="my-diary-entry">
          <div
            class="my-diary"
            role="button"
            tabindex="0"
            @click="$emit('select', diary)"
            @keydown.enter.prevent="$emit('select', diary)"
            @keydown.space.prevent="$emit('select', diary)"
          >
            <img v-if="diary.authorAvatar" :src="diary.authorAvatar" class="avatar avatar-sm avatar-img" loading="lazy" decoding="async" />
            <span v-else class="avatar avatar-sm">{{ diary.authorName.charAt(0) }}</span>
            <span class="my-diary-info">
              <strong class="my-diary-content">{{ diary.content }}</strong>
              <span class="my-diary-meta">
                <small>{{ formatTime(diary.createdAt) }}</small>
                <small
                class="mood-label"
                :style="{ color: diary.analysis?.moodLabel ? moodColor(diary.analysis.moodLabel) : undefined }"
              >{{ diary.analysis?.moodLabel || '分析中...' }}</small>
              </span>
            </span>
            <div class="diary-actions">
              <button
                class="diary-edit-btn"
                type="button"
                title="编辑日记"
                @click.stop="$emit('edit', diary)"
              >编辑</button>
              <button
                class="diary-delete-btn"
                type="button"
                title="删除日记"
                @click.stop="$emit('delete', diary)"
              >&times;</button>
            </div>
          </div>
        </div>
      </template>
    </div>
    <n-empty v-else description="还没有写过日记" />
  </article>
</template>

<script setup lang="ts">
import type { Diary } from '../stores/diary'
import { moodColor } from '../utils/mood'

defineProps<{ diaries: Diary[] }>()
defineEmits<{ select: [diary: Diary]; edit: [diary: Diary]; delete: [diary: Diary] }>()

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>

<style scoped>
.mood-label {
  font-weight: 500;
  padding: 1px 7px;
  border-radius: 8px;
  background: var(--color-bg-elevated, #f5f3f0);
}
.diary-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

.diary-edit-btn {
  border: 1px solid #d7d0c3;
  background: #fff;
  color: #5f584f;
  font-size: 12px;
  border-radius: 999px;
  padding: 2px 8px;
  cursor: pointer;
  white-space: nowrap;
}

.diary-edit-btn:hover {
  border-color: #9db7a8;
  color: #3f6d59;
}

@media (max-width: 780px) {
  .diary-actions {
    flex-direction: column;
    align-self: stretch;
  }

  .diary-edit-btn {
    font-size: 13px;
    padding: 5px 10px;
    min-height: 36px;
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}
</style>
