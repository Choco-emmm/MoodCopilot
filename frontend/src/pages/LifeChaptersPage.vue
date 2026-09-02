<template>
  <main class="app-shell life-page">
    <AppHeader />
    <section class="life-intro">
      <p class="eyebrow">LIFE CHAPTERS</p>
      <h2>时光画卷</h2>
      <p>把一段段日子放远一点看，成长往往藏在那些当时没有察觉的转弯里。</p>
    </section>
    <section class="chapter-list" aria-live="polite">
      <div v-if="loading" class="state">正在翻阅你的时光...</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <div v-else-if="chapters.length === 0" class="state">还没有足够长的一段故事。继续记录，章节会慢慢长出来。</div>
      <article v-for="(chapter, index) in chapters" :key="chapter.id" class="chapter-entry">
        <div class="chapter-marker"><span>{{ String(index + 1).padStart(2, '0') }}</span></div>
        <div class="chapter-body">
          <div class="chapter-period">{{ chapter.startDate }} — {{ chapter.endDate }} · {{ chapter.diaryCount }} 篇日记</div>
          <h3>{{ chapter.title }}</h3>
          <p class="chapter-summary">{{ chapter.themeSummary }}</p>
          <p v-if="chapter.growthReflection" class="chapter-reflection">{{ chapter.growthReflection }}</p>
          <div v-if="chapter.dominantMoods?.length" class="mood-row">
            <span v-for="mood in chapter.dominantMoods" :key="mood">{{ mood }}</span>
          </div>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import { lifeChapterApi, type LifeChapter } from '../api/life'

const chapters = ref<LifeChapter[]>([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    chapters.value = (await lifeChapterApi.list()).data.data || []
  } catch {
    error.value = '时光画卷暂时打不开，请稍后再试。'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.life-page { min-height: 100vh; }
.life-intro { max-width: 860px; margin: 42px auto 32px; padding: 0 24px; }
.eyebrow { margin: 0 0 10px; color: var(--color-primary); font-size: 11px; font-weight: 700; letter-spacing: .14em; }
.life-intro h2 { margin: 0 0 8px; color: var(--color-text); font-family: var(--font-display); font-size: 2.3rem; }
.life-intro p:last-child { max-width: 560px; margin: 0; color: var(--color-text-secondary); line-height: 1.7; }
.chapter-list { max-width: 860px; margin: 0 auto 70px; padding: 0 24px; }
.chapter-entry { display: grid; grid-template-columns: 54px minmax(0, 1fr); gap: 22px; padding: 28px 0 34px; border-top: 1px solid var(--color-border); }
.chapter-marker { display: flex; justify-content: center; }
.chapter-marker span { display: grid; width: 38px; height: 38px; place-items: center; border: 1px solid var(--color-primary); border-radius: 50%; color: var(--color-primary); font-size: 11px; }
.chapter-period { color: var(--color-text-muted); font-size: 12px; letter-spacing: .03em; }
.chapter-body h3 { margin: 9px 0 10px; color: var(--color-text); font-family: var(--font-display); font-size: 1.55rem; }
.chapter-summary, .chapter-reflection { max-width: 650px; margin: 0 0 10px; color: var(--color-text-secondary); line-height: 1.75; }
.chapter-reflection { padding-left: 14px; border-left: 2px solid var(--color-primary); }
.mood-row { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 15px; }
.mood-row span { padding: 4px 9px; border: 1px solid var(--color-border); color: var(--color-text-muted); font-size: 11px; }
.state { padding: 42px 0; color: var(--color-text-muted); text-align: center; }
.state.error { color: var(--color-error); }
</style>
