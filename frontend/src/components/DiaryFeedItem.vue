<template>
  <article class="feed-item">
    <div class="feed-head">
      <div class="feed-head-left">
        <span class="avatar">{{ diary.authorName.charAt(0) }}</span>
        <div>
          <strong>{{ diary.authorName }}</strong>
          <span>{{ formatTime(diary.createdAt) }}</span>
        </div>
      </div>
      <n-tag v-if="diary.analysis?.moodLabel" round>{{ diary.analysis.moodLabel }}</n-tag>
    </div>

    <p class="feed-content">{{ diary.content }}</p>

    <div class="tag-row">
      <n-tag
        v-for="topic in (diary.analysis?.topicLabels ?? [])"
        :key="`${diary.id}-${topic}`"
        size="small"
        type="info"
        round
      >
        {{ topic }}
      </n-tag>
    </div>

    <div class="feed-actions">
      <n-button size="small" tertiary @click="$emit('resonate', diary)">
        共鸣 {{ diary.resonanceCount }}
      </n-button>
      <n-button size="small" text @click="$emit('select', diary)">看分析</n-button>
    </div>

    <div v-if="(diary.comments ?? []).length" class="comments">
      <p v-for="comment in diary.comments" :key="comment.id">
        <strong>{{ comment.authorName }}</strong>
        <span>{{ comment.content }}</span>
      </p>
    </div>

    <div class="comment-box">
      <n-input
        v-model:value="draft"
        size="small"
        placeholder="留一句温柔回应"
        @keyup.enter="submit"
      />
      <n-button size="small" type="primary" :disabled="!draft.trim()" @click="submit">
        留言
      </n-button>
    </div>
  </article>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { Diary } from '../stores/diary'

const props = defineProps<{ diary: Diary }>()
const emit = defineEmits<{
  select: [diary: Diary]
  resonate: [diary: Diary]
  comment: [diary: Diary, content: string]
}>()

const draft = ref('')

function submit() {
  const content = draft.value.trim()
  if (!content) return
  emit('comment', props.diary, content)
  draft.value = ''
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>
