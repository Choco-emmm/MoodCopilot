<template>
  <main class="app-shell life-page">
    <AppHeader />
    <section class="life-intro">
      <button class="page-back-link" @click="$router.back()">←</button>
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
      <div v-else-if="currentChapter" class="chapter-group"><p class="group-label">当前阶段</p><article class="chapter-entry">
        <LifeChapterContent :chapter="currentChapter" :expanded-id="expandedId" :versions-id="versionsId" :versions="versions" :refreshing-id="refreshingId" @toggle-sources="toggleSources" @toggle-versions="toggleVersions" @refresh="refreshChapter" @open-diary="openDiary" @open-events="openEvents" />
      </article></div>
      <div v-if="historyChapters.length" class="chapter-group"><p class="group-label">更早阶段</p><article v-for="chapter in historyChapters" :key="chapter.id" class="chapter-entry">
        <LifeChapterContent :chapter="chapter" :expanded-id="expandedId" :versions-id="versionsId" :versions="versions" :refreshing-id="refreshingId" @toggle-sources="toggleSources" @toggle-versions="toggleVersions" @refresh="refreshChapter" @open-diary="openDiary" @open-events="openEvents" />
      </article></div>
      <div v-if="gaps.length" class="gaps"><p class="group-label">记录较少的时间段</p><div v-for="gap in gaps" :key="`${gap.startDate}-${gap.endDate}`">{{ gap.startDate }} — {{ gap.endDate }}<span>这段时间暂时没有足够记录</span></div></div>
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
  try {
    // 每次展开都重拉：整理完成会新增版本，缓存住的话用户永远看不到最新那一版。
    versions.value[id] = (await lifeChapterApi.timelineVersions(id)).data.data || []
    versionsId.value = id
  } catch {
    window.$message?.error('历史版本加载失败，请稍后重试', { duration: 4000 })
  }
}

async function acceptCandidate(id: number) { await lifeChapterApi.acceptCandidate(id); await loadChapters() }
async function rejectCandidate(id: number) { await lifeChapterApi.rejectCandidate(id); await loadChapters() }
function openDiary(id: number) { router.push(`/diary/${id}`) }
function openEvents() { router.push('/life-events') }

async function refreshChapter(chapter: LifeChapter) {
  refreshingId.value = chapter.id
  try {
    await lifeChapterApi.refresh(chapter.id)
    window.$message?.info('已开始整理这个阶段，完成后会通知你。', { duration: 3500 })
    await loadChapters()
  } catch (e: any) {
    window.$message?.error(e?.response?.data?.message || '阶段整理任务提交失败，请稍后重试', { duration: 5000 })
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
.life-intro { max-width: 860px; margin: 42px auto 40px; padding: 0 24px; }
.eyebrow { margin: 0 0 10px; color: var(--color-primary); font-size: 11px; font-weight: 700; letter-spacing: .14em; }
.life-intro h2 { margin: 0 0 10px; color: var(--color-text); font-family: var(--font-display); font-size: 2.3rem; line-height: 1.25; }
.life-intro p:last-child { max-width: 560px; margin: 0; color: var(--color-text-secondary); line-height: 1.75; }
.candidate-panel { max-width: 860px; margin: 0 auto 44px; padding: 18px 24px; border: 1px solid var(--color-border); background: var(--color-surface-soft); }
.section-heading { display: flex; flex-wrap: wrap; align-items: baseline; gap: 10px; }
.section-heading h3 { margin: 0; color: var(--color-text); font-family: var(--font-display); font-size: 1.25rem; }
.section-kicker { color: var(--color-text-muted); font-size: 12px; }
.candidate-item { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 16px 0 4px; border-top: 1px solid var(--color-border); }
.candidate-item:first-of-type { margin-top: 16px; }
.candidate-item p { margin: 6px 0; color: var(--color-text-secondary); }
.candidate-item span { color: var(--color-text-muted); font-size: 12px; }
.candidate-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; }
.primary-button { padding: 8px 14px; border: 0; background: var(--color-primary); color: var(--color-on-primary); cursor: pointer; font: inherit; font-size: 12px; }
.gaps { margin: 30px 0 0; padding: 0; }
.gaps > div { display: flex; justify-content: space-between; gap: 20px; padding: 12px 0; border-top: 1px solid var(--color-border); color: var(--color-text-muted); font-size: 12px; }
.gaps span { color: var(--color-text-secondary); }
/* 章节列表：去掉时间轴竖杆和编号圆点，每个阶段就是一章的开场，内容列与页面同一条左边线 */
.chapter-list { max-width: 860px; margin: 0 auto 90px; padding: 0 24px; }
.group-label { margin: 0; padding: 0 0 6px; color: var(--color-text-muted); font-size: 12px; letter-spacing: .02em; }
.chapter-group { margin: 0; padding: 0; }
.chapter-group + .chapter-group { margin-top: 34px; }
.chapter-entry { padding: 24px 0 30px; border-top: 1px solid var(--color-border); }
.state { padding: 42px 0; color: var(--color-text-muted); text-align: center; }
.state.error { color: var(--color-error); }
.timeline-load-more { padding: 26px 0; text-align: center; }
.text-button { padding: 0; border: 0; background: transparent; color: var(--color-primary); cursor: pointer; font: inherit; font-size: 12px; }
.text-button:disabled { cursor: wait; opacity: .55; }
@media (max-width: 620px) {
  .life-intro { margin: 30px auto 30px; padding: 0 16px; }
  .life-intro h2 { font-size: 1.8rem; }
  .candidate-panel { margin-left: 16px; margin-right: 16px; padding: 16px; }
  .candidate-item { align-items: flex-start; flex-direction: column; gap: 12px; }
  .gaps > div { align-items: flex-start; flex-direction: column; gap: 4px; }
  .chapter-list { padding: 0 16px; }
  .chapter-entry { padding: 22px 0 26px; }
}
</style>
