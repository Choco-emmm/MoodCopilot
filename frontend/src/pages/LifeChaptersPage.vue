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
          <div class="chapter-meta">
            <span>第 {{ chapter.currentVersion || 1 }} 版</span>
            <span v-if="chapter.lastGeneratedAt">最近更新 {{ chapter.lastGeneratedAt }}</span>
            <span v-else-if="chapter.updatedAt">最近更新 {{ chapter.updatedAt }}</span>
            <span v-if="chapter.generationStatus === 'DIRTY' || chapter.generationStatus === 'GENERATING'" class="status updating">正在更新</span>
            <span v-else-if="chapter.generationStatus === 'FAILED'" class="status failed">更新失败，已保留上一版</span>
          </div>
          <p class="chapter-summary">{{ chapter.themeSummary }}</p>
          <p v-if="chapter.growthReflection" class="chapter-reflection">{{ chapter.growthReflection }}</p>
          <div v-if="chapter.dominantMoods?.length" class="mood-row">
            <span v-for="mood in chapter.dominantMoods" :key="mood">{{ mood }}</span>
          </div>
          <p v-if="chapter.generationStatus === 'FAILED' && chapter.lastGenerationError" class="chapter-error">{{ chapter.lastGenerationError }}</p>
          <div class="chapter-actions">
            <button type="button" class="text-button" @click="toggleSources(chapter.id)">
              {{ expandedId === chapter.id ? '收起来源' : `查看来源（${(chapter.diarySources?.length || 0) + (chapter.eventSources?.length || 0)}）` }}
            </button>
            <button type="button" class="text-button" :disabled="refreshingId === chapter.id" @click="refreshChapter(chapter)">
              {{ refreshingId === chapter.id ? '已提交更新' : '更新这一章' }}
            </button>
            <button v-if="(chapter.currentVersion || 0) > 1" type="button" class="text-button" @click="toggleVersions(chapter.id)">
              {{ versionsId === chapter.id ? '收起历史' : '查看历史版本' }}
            </button>
          </div>
          <div v-if="expandedId === chapter.id" class="source-list">
            <div v-for="source in chapter.diarySources" :key="source.id" class="source-item">
              <div><span class="source-date">{{ source.date }}</span><span class="source-excerpt">{{ source.excerpt || source.summary || '这篇日记暂无摘要' }}</span></div>
              <button type="button" class="source-link" @click="router.push(`/diary/${source.id}`)">查看日记 →</button>
            </div>
            <div v-for="source in chapter.eventSources" :key="`event-${source.id}`" class="source-item">
              <div><span class="source-date">{{ source.startDate }}</span><span class="source-excerpt">重要事件：{{ source.title }}</span></div>
              <button type="button" class="source-link" @click="router.push('/life-events')">查看事件 →</button>
            </div>
            <span v-if="!chapter.diarySources?.length && !chapter.eventSources?.length" class="empty-source">暂无来源</span>
          </div>
          <div v-if="versionsId === chapter.id" class="version-list">
            <div v-for="version in versions[chapter.id] || []" :key="version.version" class="version-item">
              <strong>第 {{ version.version }} 版 · {{ version.createdAt }}</strong>
              <span>{{ version.title }}</span>
            </div>
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
import { useRouter } from 'vue-router'

const chapters = ref<LifeChapter[]>([])
const loading = ref(true)
const error = ref('')
const expandedId = ref<number | null>(null)
const versionsId = ref<number | null>(null)
const versions = ref<Record<number, Awaited<ReturnType<typeof lifeChapterApi.versions>>['data']['data']>>({})
const refreshingId = ref<number | null>(null)
const router = useRouter()

async function loadChapters() {
  chapters.value = (await lifeChapterApi.list()).data.data || []
}

function toggleSources(id: number) { expandedId.value = expandedId.value === id ? null : id }

async function toggleVersions(id: number) {
  if (versionsId.value === id) { versionsId.value = null; return }
  if (!versions.value[id]) versions.value[id] = (await lifeChapterApi.versions(id)).data.data || []
  versionsId.value = id
}

async function refreshChapter(chapter: LifeChapter) {
  refreshingId.value = chapter.id
  try { await lifeChapterApi.refresh(chapter.id); await loadChapters() } catch { /* interceptor displays the error */ }
  finally { window.setTimeout(() => { if (refreshingId.value === chapter.id) refreshingId.value = null }, 1200) }
}

onMounted(async () => {
  try {
    await loadChapters()
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
.chapter-meta { display: flex; flex-wrap: wrap; gap: 10px; color: var(--color-text-muted); font-size: 11px; }
.status.updating { color: var(--color-primary); }.status.failed, .chapter-error { color: var(--color-error); }
.chapter-error { margin: 8px 0; font-size: 12px; }
.chapter-body h3 { margin: 9px 0 10px; color: var(--color-text); font-family: var(--font-display); font-size: 1.55rem; }
.chapter-summary, .chapter-reflection { max-width: 650px; margin: 0 0 10px; color: var(--color-text-secondary); line-height: 1.75; }
.chapter-reflection { padding-left: 14px; border-left: 2px solid var(--color-primary); }
.mood-row { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 15px; }
.mood-row span { padding: 4px 9px; border: 1px solid var(--color-border); color: var(--color-text-muted); font-size: 11px; }
.state { padding: 42px 0; color: var(--color-text-muted); text-align: center; }
.state.error { color: var(--color-error); }
.chapter-actions { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 16px; }
.text-button, .source-link { padding: 0; border: 0; background: transparent; color: var(--color-primary); cursor: pointer; font: inherit; font-size: 12px; }
.text-button:disabled { cursor: wait; opacity: .55; }
.source-list, .version-list { margin-top: 14px; border-left: 2px solid var(--color-border); padding-left: 14px; }
.source-item, .version-item { display: flex; align-items: baseline; justify-content: space-between; gap: 14px; padding: 9px 0; border-bottom: 1px solid var(--color-border); font-size: 12px; }
.source-date { display: inline-block; min-width: 90px; color: var(--color-text-muted); }
.source-excerpt, .version-item span { color: var(--color-text-secondary); }
.empty-source { color: var(--color-text-muted); font-size: 12px; }
@media (max-width: 620px) { .source-item { align-items: flex-start; flex-direction: column; gap: 5px; } .source-date { min-width: auto; margin-right: 8px; } }
</style>
