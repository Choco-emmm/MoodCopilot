<template>
  <article class="panel">
    <div class="section-title compact">
      <div>
        <p class="eyebrow">公开日记流</p>
        <h2>温和回应</h2>
      </div>
      <n-button quaternary size="small" :loading="loading" @click="$emit('refresh')">刷新</n-button>
    </div>

    <div v-if="diaries.length" class="feed">
      <DiaryFeedItem
        v-for="diary in diaries"
        :key="diary.id"
        :diary="diary"
        @select="$emit('select', $event)"
        @resonate="$emit('resonate', $event)"
        @comment="(d, c) => $emit('comment', d, c)"
      />
      <div v-if="hasMore" ref="sentinel" class="scroll-sentinel" />
      <n-spin v-if="loading && diaries.length" size="small" />
    </div>
    <n-empty v-else-if="!loading" description="暂无公开日记" />
  </article>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import DiaryFeedItem from './DiaryFeedItem.vue'
import type { Diary } from '../stores/diary'

defineProps<{ diaries: Diary[]; loading: boolean; hasMore?: boolean }>()
const emit = defineEmits<{
  refresh: []
  select: [diary: Diary]
  resonate: [diary: Diary]
  comment: [diary: Diary, content: string, parentCommentId?: number]
  loadMore: []
}>()

const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

onMounted(() => {
  if (typeof IntersectionObserver === 'undefined') return
  observer = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting) {
      emit('loadMore')
    }
  }, { rootMargin: '200px' })
})

onUnmounted(() => {
  observer?.disconnect()
})

function setupSentinel(el: HTMLElement | null) {
  observer?.disconnect()
  if (el) observer?.observe(el)
}

watch(sentinel, (el) => setupSentinel(el))
</script>
