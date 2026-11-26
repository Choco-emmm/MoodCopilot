<template>
  <main class="app-shell">
    <AppHeader />

    <!-- Editorial Navigation -->
    <header class="editorial-header">
      <div class="editorial-nav">
        <router-link to="/" class="editorial-link active">广场 <span class="en-sub">Square</span></router-link>
        <span class="editorial-separator">/</span>
        <router-link to="/following" class="editorial-link">关注 <span class="en-sub">Following</span></router-link>
      </div>
    </header>

    <StarterChecklist v-if="authStore.isAuthenticated && onboardingStore.checklistActive" />

    <!-- Feed -->
    <PublicFeed
      :diaries="store.publicDiaries"
      :loading="store.loading"
      :has-more="store.hasMore"
      @refresh="store.fetchDiaries()"
      @load-more="store.loadMorePublic()"
      @resonate="(d: Diary) => store.resonate(d.id, d)"
      @open-detail="(d: Diary) => router.push(`/diary/${d.id}`)"
    >
      <template #in-feed>
        <!-- Hero bar: Today's resonance + Chat tease in a unified row -->
        <div class="square-hero">
          <router-link v-if="matchDiary" :to="'/diary/' + matchDiary.id" class="square-hero-card square-hero-resonance">
            <span class="square-hero-label">今日同频</span>
            <span class="square-hero-snippet">「{{ snippet(matchDiary) }}」</span>
            <span class="square-hero-arrow">→</span>
          </router-link>

          <router-link v-else to="/write" class="square-hero-card square-hero-resonance">
            <span class="square-hero-label">今日同频</span>
            <span class="square-hero-snippet" style="color: var(--color-text-muted); font-style: italic;">写下今天后，MoodCopilot 会帮你找相似处境的人。</span>
            <span class="square-hero-arrow">→</span>
          </router-link>

          <router-link to="/chat" class="square-hero-card square-hero-chat">
            <span class="square-hero-chat-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/>
              </svg>
            </span>
            <span class="square-hero-snippet">和 MoodCopilot 聊聊</span>
            <span class="square-hero-arrow">→</span>
          </router-link>
        </div>
      </template>
    </PublicFeed>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import PublicFeed from '../components/PublicFeed.vue'
import StarterChecklist from '../components/onboarding/StarterChecklist.vue'
import { useAuthStore } from '../stores/auth'
import { useDiaryStore, type Diary } from '../stores/diary'
import { useOnboardingStore } from '../stores/onboarding'
import { diaryApi } from '../api'
import { stripMarkdown } from '../utils/markdown'
import { logWarn } from '../utils/logger'

function snippet(diary: any): string {
  if (!diary?.content) return ''
  const text = stripMarkdown(diary.content)
  return text.length > 42 ? text.slice(0, 42) + '...' : text
}

const router = useRouter()
const authStore = useAuthStore()
const store = useDiaryStore()
const onboardingStore = useOnboardingStore()
const matchDiary = ref<any>(null)

onMounted(async () => {
  store.fetchDiaries()
  try { const res = await diaryApi.todayMatch(); matchDiary.value = res.data.data } catch (e) { logWarn('square', '加载今日同频失败', e) }
})
</script>

<style scoped>
/* ── Layout ── */
.app-shell {
  display: flex;
  flex-direction: column;
}

/* ── Hero Row ── */
.square-hero {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin: 16px 0;
}

@media (max-width: 780px) {
  .square-hero {
    grid-template-columns: 1fr;
    gap: 10px;
  }
}

.square-hero-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-radius: 6px;
  text-decoration: none;
  background: var(--color-surface);
  border: 1px solid color-mix(in oklab, var(--color-border) 40%, transparent);
  transition: all 0.2s var(--ease-out);
  position: relative;
  overflow: hidden;
}

.square-hero-resonance:hover,
.square-hero-chat:hover {
  transform: translateY(-1px);
  border-color: color-mix(in oklab, var(--color-border) 80%, transparent);
  box-shadow: 0 4px 12px rgba(0,0,0,0.03);
}

.square-hero-label {
  font-size: 10px;
  font-weight: 700;
  color: var(--color-accent);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  flex-shrink: 0;
  white-space: nowrap;
}

.square-hero-chat-icon {
  display: flex;
  align-items: center;
  color: var(--color-primary);
  flex-shrink: 0;
  animation: gentle-pulse 3s ease-in-out infinite;
}

@keyframes gentle-pulse {
  0%, 100% { opacity: 0.7; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.08); }
}

.square-hero-snippet {
  flex: 1;
  font-size: 0.88rem;
  color: var(--color-text-secondary);
  line-height: 1.5;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.square-hero-arrow {
  font-size: 0.9rem;
  color: var(--color-text-light);
  flex-shrink: 0;
  transition: transform 0.2s, color 0.2s;
}

.square-hero-card:hover .square-hero-arrow {
  transform: translateX(3px);
  color: var(--color-primary);
}
</style>
