<template>
  <main class="app-shell life-page">
    <AppHeader />
    <section class="life-intro">
      <p class="eyebrow">LIFE CHAPTERS</p>
      <h2>时光画卷</h2>
      <p>把一段段日子放远一点看，成长往往藏在那些当时没有察觉的转弯里。</p>
    </section>
    <section v-if="candidates.length" class="candidate-panel" aria-live="polite">
      <div class="section-heading"><span class="section-kicker">需要你确认</span><h3>可能是新的阶段</h3></div>
      <div v-for="candidate in candidates" :key="candidate.id" class="candidate-item">
        <div><strong>{{ candidate.suggestedStartDate }} 起</strong><p>{{ candidate.reason }}</p><span>涉及 {{ candidate.sourceDiaryIds.length + candidate.sourceEventIds.length }} 条记录</span></div>
        <div class="candidate-actions"><button type="button" class="text-button" @click="rejectCandidate(candidate.id)">暂不分开</button><button type="button" class="primary-button" @click="acceptCandidate(candidate.id)">接受新阶段</button></div>
      </div>
    </section>
    <section class="chapter-list" aria-live="polite">
      <div v-if="loading" class="state">正在翻阅你的时光...</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <div v-else-if="chapters.length === 0" class="state">还没有足够长的一段故事。继续记录，章节会慢慢长出来。</div>
      <div v-else-if="currentChapter" class="chapter-group current-group"><p class="group-label">当前阶段</p><article class="chapter-entry" :key="currentChapter.id">
        <div class="chapter-marker"><span>今</span></div>
        <div class="chapter-body"><LifeChapterContent :chapter="currentChapter" :expanded-id="expandedId" :versions-id="versionsId" :versions="versions" :refreshing-id="refreshingId" @toggle-sources="toggleSources" @toggle-versions="toggleVersions" @refresh="refreshChapter" @open-diary="openDiary" @open-events="openEvents" /></div>
      </article></div>
      <div v-if="historyChapters.length" class="chapter-group"><p class="group-label">更早阶段</p><article v-for="(chapter, index) in historyChapters" :key="chapter.id" class="chapter-entry">
        <div class="chapter-marker"><span>{{ String(index + 1).padStart(2, '0') }}</span></div>
        <div class="chapter-body"><LifeChapterContent :chapter="chapter" :expanded-id="expandedId" :versions-id="versionsId" :versions="versions" :refreshing-id="refreshingId" @toggle-sources="toggleSources" @toggle-versions="toggleVersions" @refresh="refreshChapter" @open-diary="openDiary" @open-events="openEvents" /></div>
      </article></div>
      <div v-if="gaps.length" class="gaps"><p class="group-label">记录较少的时间段</p><div v-for="gap in gaps" :key="`${gap.startDate}-${gap.endDate}`">{{ gap.startDate }} - {{ gap.endDate }}<span>这段时间暂时没有足够记录</span></div></div>
      <div v-if="nextCursor" class="timeline-load-more"><button type="button" class="text-button" :disabled="loadingMore" @click="loadMoreChapters">{{ loadingMore ? '正在加载…' : '继续查看更早阶段' }}</button></div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import LifeChapterContent from '../components/LifeChapterContent.vue'
import { lifeChapterApi, type LifeChapter, type LifeChapterVersion, type LifeTimelineCandidate } from '../api/life'
import { useRouter } from 'vue-router'

const chapters = ref<LifeChapter[]>([])
const loading = ref(true)
const error = ref('')
const expandedId = ref<number | null>(null)
const versionsId = ref<number | null>(null)
const versions = ref<Record<number, LifeChapterVersion[]>>({})
const refreshingId = ref<number | null>(null)
const candidates = ref<LifeTimelineCandidate[]>([])
const gaps = ref<{ startDate: string; endDate: string }[]>([])
const nextCursor = ref<string | null>(null)
const loadingMore = ref(false)
const router = useRouter()
const currentChapter = computed(() => chapters.value.find(chapter => chapter.isOpen))
const historyChapters = computed(() => chapters.value.filter(chapter => !chapter.isOpen))

async function loadChapters() {
  const timeline = (await lifeChapterApi.timeline({ includeGaps: true, size: 50 })).data.data
  chapters.value = timeline?.stages || []
  gaps.value = timeline?.gaps || []
  nextCursor.value = timeline?.nextCursor || null
  candidates.value = (await lifeChapterApi.candidates()).data.data || []
}

async function loadMoreChapters() {
  if (!nextCursor.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const timeline = (await lifeChapterApi.timeline({ cursor: nextCursor.value, includeGaps: true, size: 50 })).data.data
    chapters.value = [...chapters.value, ...(timeline?.stages || [])]
    gaps.value = [...gaps.value, ...(timeline?.gaps || [])]
    nextCursor.value = timeline?.nextCursor || null
  } finally {
    loadingMore.value = false
  }
}

function toggleSources(id: number) { expandedId.value = expandedId.value === id ? null : id }

async function toggleVersions(id: number) {
  if (versionsId.value === id) { versionsId.value = null; return }
  if (!versions.value[id]) versions.value[id] = (await lifeChapterApi.timelineVersions(id)).data.data || []
  versionsId.value = id
}

async function acceptCandidate(id: number) { await lifeChapterApi.acceptCandidate(id); await loadChapters() }
async function rejectCandidate(id: number) { await lifeChapterApi.rejectCandidate(id); await loadChapters() }
function openDiary(id: number) { router.push(`/diary/${id}`) }
function openEvents() { router.push('/life-events') }

async function refreshChapter(chapter: LifeChapter) {
  window.$message?.info('已开始整理这个阶段，完成后会通知你。', { duration: 3500 })
  refreshingId.value = chapter.id
  try {
    await lifeChapterApi.refresh(chapter.id)
    await loadChapters()
  } catch {
    window.$message?.error('阶段整理任务提交失败，请稍后重试', { duration: 5000 })
  }
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
.candidate-panel { max-width: 860px; margin: 0 auto 34px; padding: 18px 24px; border: 1px solid var(--color-border); background: var(--color-surface-soft); }
.section-heading { display: flex; align-items: baseline; gap: 12px; }.section-heading h3 { margin: 0; color: var(--color-text); font-family: var(--font-display); font-size: 1.3rem; }.section-kicker, .group-label { color: var(--color-primary); font-size: 11px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }.candidate-item { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 16px 0 4px; border-top: 1px solid var(--color-border); }.candidate-item:first-of-type { margin-top: 14px; }.candidate-item p { margin: 6px 0; color: var(--color-text-secondary); }.candidate-item span { color: var(--color-text-muted); font-size: 12px; }.candidate-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; }.primary-button { padding: 8px 14px; border: 0; background: var(--color-primary); color: var(--color-on-primary); cursor: pointer; font: inherit; font-size: 12px; }.group-label { margin: 0; padding: 0 0 8px; }.chapter-group { max-width: none; margin: 0; padding: 0; }.current-group { margin-bottom: 24px; }.gaps { max-width: none; margin: 30px 0; padding: 0; }.gaps > div { display: flex; justify-content: space-between; gap: 20px; padding: 12px 0; border-top: 1px solid var(--color-border); color: var(--color-text-muted); font-size: 12px; }.gaps span { color: var(--color-text-secondary); }
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
.timeline-load-more { padding: 24px 0; text-align: center; }
.state.error { color: var(--color-error); }
.chapter-actions { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 16px; }
.text-button, .source-link { padding: 0; border: 0; background: transparent; color: var(--color-primary); cursor: pointer; font: inherit; font-size: 12px; }
.text-button:disabled { cursor: wait; opacity: .55; }
.source-list, .version-list { margin-top: 14px; border-left: 2px solid var(--color-border); padding-left: 14px; }
.source-item, .version-item { display: flex; align-items: baseline; justify-content: space-between; gap: 14px; padding: 9px 0; border-bottom: 1px solid var(--color-border); font-size: 12px; }
.source-date { display: inline-block; min-width: 90px; color: var(--color-text-muted); }
.source-excerpt, .version-item span { color: var(--color-text-secondary); }
.empty-source { color: var(--color-text-muted); font-size: 12px; }
@media (max-width: 620px) {
  .chapter-list { padding: 0 16px; }
  .candidate-item, .gaps > div { align-items: flex-start; flex-direction: column; gap: 10px; }
  .chapter-entry { grid-template-columns: 34px minmax(0, 1fr); gap: 12px; padding: 22px 0 28px; }
  .chapter-marker span { width: 32px; height: 32px; font-size: 10px; }
  .chapter-body h3 { margin-top: 8px; font-size: 1.35rem; line-height: 1.35; }
  .chapter-period, .chapter-meta { line-height: 1.55; }
  .chapter-summary, .chapter-reflection, .collecting-note { max-width: none; }
  .chapter-actions { gap: 12px 16px; }
  .source-item { align-items: flex-start; flex-direction: column; gap: 5px; }
  .source-date { min-width: auto; margin-right: 8px; }
}
</style>
