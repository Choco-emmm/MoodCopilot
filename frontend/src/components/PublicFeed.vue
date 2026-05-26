<template>
  <section class="feed-section">
    <div class="feed-section-header">
      <div class="feed-section-title">
        <span class="feed-section-eyebrow">心情广场</span>
        <h2 class="feed-section-heading">最近的心情</h2>
      </div>
      <button class="feed-section-refresh" :disabled="loading" @click="$emit('refresh')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" :class="{ spinning: loading }">
          <polyline points="23 4 23 10 17 10"/>
          <polyline points="1 20 1 14 7 14"/>
          <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
        </svg>
        <span>{{ loading ? '刷新中...' : '刷新' }}</span>
      </button>
    </div>

    <div v-if="diaries.length" class="feed-list">
      <DynamicScroller
        :items="diaries"
        :min-item-size="200"
        key-field="id"
        page-mode
      >
        <template #default="{ item, index, active }">
          <DynamicScrollerItem
            :item="item"
            :active="active"
            :size-dependencies="[
              item.content,
              item.images?.length,
              item.comments?.length,
              item.musicMeta?.songUrl
            ]"
            :data-index="index"
          >
            <DiaryFeedItem
              :diary="item"
              :enable-comments="false"
              :compact="true"
              :preview-limit="120"
              :show-expand-toggle="false"
              @resonate="$emit('resonate', $event)"
              @open-detail="$emit('open-detail', $event)"
            />
            <!-- Interleaved slot -->
            <div v-if="index === 1" class="in-feed-slot">
              <slot name="in-feed" />
            </div>
          </DynamicScrollerItem>
        </template>
      </DynamicScroller>
      <div v-if="hasMore" ref="sentinel" class="scroll-sentinel" />
      <div v-if="loading && diaries.length" class="feed-list-loading">
        <span class="feed-list-loading-dot"></span>
        <span class="feed-list-loading-dot"></span>
        <span class="feed-list-loading-dot"></span>
      </div>
      <button v-if="hasMore && !loading" class="feed-list-more" @click="$emit('loadMore')">
        加载更多
      </button>
    </div>

    <div v-else-if="!loading" class="feed-list-empty">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="feed-empty-icon">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
        <polyline points="14 2 14 8 20 8"/>
        <line x1="16" y1="13" x2="8" y2="13"/>
        <line x1="16" y1="17" x2="8" y2="17"/>
      </svg>
      <p>还没有人写下公开日记</p>
      <p class="feed-empty-sub">成为第一个分享心情的人吧</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import DiaryFeedItem from './DiaryFeedItem.vue'
import type { Diary } from '../stores/diary'
import { useInfiniteScroll } from '../composables/useScrollManager'

const props = defineProps<{ diaries: Diary[]; loading: boolean; hasMore?: boolean }>()
const emit = defineEmits<{
  refresh: []
  resonate: [diary: Diary]
  'open-detail': [diary: Diary]
  loadMore: []
}>()

const sentinel = ref<HTMLElement | null>(null)
const hasMoreRef = computed(() => !!props.hasMore)

useInfiniteScroll(sentinel, () => {
  if (!props.loading) emit('loadMore')
}, { enabled: hasMoreRef, rootMargin: '200px' })
</script>

<style scoped>
.feed-section {
  display: grid;
  gap: 0;
}

/* ── Section Header ── */
.feed-section-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
  padding-bottom: 14px;
  border-bottom: 2px solid color-mix(in oklab, var(--color-primary) 12%, transparent);
}

.feed-section-title {
  display: grid;
  gap: 2px;
}

.feed-section-eyebrow {
  font-size: 10px;
  font-weight: 700;
  color: var(--color-accent);
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.feed-section-heading {
  margin: 0;
  font-family: var(--font-body);
  font-size: 1.3rem;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: 0.01em;
}

.feed-section-refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1.5px solid var(--color-border);
  border-radius: 100px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s var(--ease-out);
}

.feed-section-refresh:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.feed-section-refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.feed-section-refresh svg {
  width: 15px;
  height: 15px;
}

.feed-section-refresh svg.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ── Feed List ── */
.feed-list {
  display: grid;
  gap: 10px;
}

/* ── Loading Dots ── */
.feed-list-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 28px 0;
}

.feed-list-loading-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: color-mix(in oklab, var(--color-primary) 40%, transparent);
  animation: dot-bounce 1.2s ease-in-out infinite;
}

.feed-list-loading-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.feed-list-loading-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes dot-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* ── Load More ── */
.feed-list-more {
  justify-self: center;
  margin-top: 8px;
  padding: 10px 32px;
  border: 2px solid var(--color-border);
  border-radius: 100px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 0.85rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.25s var(--ease-out);
  letter-spacing: 0.03em;
}

.feed-list-more:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 4%, var(--color-surface));
  transform: translateY(-1px);
  box-shadow: 0 4px 12px color-mix(in oklab, var(--color-primary) 10%, transparent);
}

/* ── Empty State ── */
.feed-list-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 20px;
  text-align: center;
}

.feed-empty-icon {
  color: var(--color-text-light);
  margin-bottom: 16px;
  opacity: 0.5;
}

.feed-list-empty p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.95rem;
}

.feed-empty-sub {
  margin-top: 4px;
  font-size: 0.82rem !important;
  opacity: 0.7;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .feed-section-header {
    margin-bottom: 14px;
    padding-bottom: 10px;
  }

  .feed-section-heading {
    font-size: 1.15rem;
  }

  .feed-list {
    gap: 4px;
  }
}
</style>
