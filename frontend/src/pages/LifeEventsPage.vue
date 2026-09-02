<template>
  <main class="app-shell life-page">
    <AppHeader />
    <section class="life-intro">
      <p class="eyebrow">PENDING THREADS</p>
      <h2>重要事件</h2>
      <p>那些还在心里占着位置的事，值得被记住，也值得有人回来问一句。</p>
    </section>

    <section class="event-list" aria-live="polite">
      <div v-if="loading" class="state">正在整理你的事件线索...</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <div v-else-if="events.length === 0" class="state empty">暂时没有待跟进的事件。写下日记，未来的你会有更多线索。</div>
      <article v-for="event in events" :key="event.id" class="event-entry">
        <div class="event-date">
          <strong>{{ formatDate(event.targetDate) }}</strong>
          <span>{{ statusLabel(event.status) }}</span>
        </div>
        <div class="event-body">
          <h3>{{ event.title }}</h3>
          <p v-if="event.description">{{ event.description }}</p>
          <button
            v-if="event.diaryIds?.length"
            class="event-meta event-meta-link"
            type="button"
            title="查看关联日记"
            @click="openLinkedDiary(event)"
          >
            关联 {{ event.diaryIds.length }} 篇日记 <span aria-hidden="true">↗</span>
          </button>
          <span v-else class="event-meta">暂无关联日记</span>
        </div>
        <div class="event-actions">
          <button class="text-button primary" type="button" @click="chatAbout(event)">聊聊这件事</button>
          <button v-if="event.status === 'PENDING'" class="text-button" type="button" @click="markDone(event)">标记已跟进</button>
          <button v-else-if="event.status === 'FOLLOWED_UP'" class="text-button" type="button" @click="archive(event)">收进档案</button>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import { lifeEventApi, type LifeEvent } from '../api/life'

const router = useRouter()
const events = ref<LifeEvent[]>([])
const loading = ref(true)
const error = ref('')

onMounted(loadEvents)

async function loadEvents() {
  loading.value = true
  error.value = ''
  try {
    events.value = (await lifeEventApi.list()).data.data || []
  } catch {
    error.value = '事件暂时加载失败，请稍后再试。'
  } finally {
    loading.value = false
  }
}

function formatDate(value: string) {
  if (!value) return '未定日期'
  const date = new Date(`${value}T00:00:00`)
  return Number.isNaN(date.getTime()) ? value : `${date.getMonth() + 1}月${date.getDate()}日`
}

function statusLabel(status: string) {
  return status === 'PENDING' ? '待回访' : status === 'FOLLOWED_UP' ? '已回访' : '已归档'
}

function chatAbout(event: LifeEvent) {
  router.push({ name: 'chat', state: { eventId: event.id } })
}

function openLinkedDiary(event: LifeEvent) {
  const diaryId = event.lastDiaryId ?? event.diaryIds?.[0]
  if (!diaryId) return
  router.push({ name: 'diary-detail', params: { id: diaryId } })
}

async function updateStatus(event: LifeEvent, status: string) {
  try {
    const response = await lifeEventApi.updateStatus(event.id, status)
    const updated = response.data.data
    if (updated) Object.assign(event, updated)
  } catch {
    error.value = '状态更新失败，请稍后再试。'
  }
}

function markDone(event: LifeEvent) { void updateStatus(event, 'FOLLOWED_UP') }
function archive(event: LifeEvent) { void updateStatus(event, 'ARCHIVED') }
</script>

<style scoped>
.life-page { min-height: 100vh; }
.life-intro { max-width: 860px; margin: 42px auto 28px; padding: 0 24px; }
.eyebrow { margin: 0 0 10px; color: var(--color-primary); font-size: 11px; font-weight: 700; letter-spacing: .14em; }
.life-intro h2 { margin: 0 0 8px; color: var(--color-text); font-family: var(--font-display); font-size: 2.3rem; }
.life-intro p:last-child { max-width: 560px; margin: 0; color: var(--color-text-secondary); line-height: 1.7; }
.event-list { max-width: 860px; margin: 0 auto 70px; padding: 0 24px; }
.event-entry { display: grid; grid-template-columns: 116px minmax(0, 1fr) auto; gap: 22px; align-items: start; padding: 24px 0; border-top: 1px solid var(--color-border); }
.event-date { display: flex; flex-direction: column; gap: 6px; color: var(--color-text-muted); font-size: 12px; }
.event-date strong { color: var(--color-text); font-family: var(--font-display); font-size: 1.05rem; }
.event-body h3 { margin: 0 0 7px; color: var(--color-text); font-family: var(--font-display); font-size: 1.28rem; }
.event-body p { margin: 0 0 10px; color: var(--color-text-secondary); line-height: 1.65; }
.event-meta { color: var(--color-text-muted); font-size: 12px; }
.event-meta-link { display: inline-flex; align-items: center; gap: 4px; margin: 0; padding: 0; border: 0; background: transparent; color: var(--color-primary); cursor: pointer; font: inherit; font-size: 12px; text-align: left; transition: opacity .2s ease; }
.event-meta-link:hover { opacity: .72; }
.event-meta-link:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 3px; border-radius: 2px; }
.event-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 7px; }
.text-button { border: 0; padding: 7px 0 7px 12px; background: transparent; color: var(--color-text-muted); cursor: pointer; font: inherit; font-size: 12px; white-space: nowrap; }
.text-button.primary { color: var(--color-primary); font-weight: 650; }
.state { padding: 42px 0; color: var(--color-text-muted); text-align: center; }
.state.error { color: var(--color-error); }
@media (max-width: 640px) { .event-entry { grid-template-columns: 1fr; gap: 10px; } .event-actions { justify-content: flex-start; } .text-button { padding-left: 0; margin-right: 14px; } }
</style>
