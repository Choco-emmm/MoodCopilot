<template>
  <main class="app-shell life-page">
    <AppHeader />
    <section class="life-intro">
      <div>
        <p class="eyebrow">PENDING THREADS</p>
        <div class="title-with-info"><h2>重要事件</h2><button class="info-button" type="button" aria-label="了解重要事件回访规则" title="了解重要事件回访规则" @click="eventInfoOpen = true">i</button></div>
        <p>那些还在心里占着位置的事，值得被记住，也值得有人回来问一句。</p>
      </div>
      <button class="add-button" type="button" @click="openCreate">＋ 添加事件</button>
    </section>

    <section class="event-list" aria-live="polite">
      <div v-if="loading" class="state">正在整理你的事件线索...</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <div v-else-if="events.length === 0" class="state empty">暂时没有重要事件。你也可以手动记下一件想回来关注的事。</div>
      <article v-for="event in events" :key="event.id" class="event-entry">
        <div class="event-date">
          <strong>{{ formatSchedule(event) }}</strong>
          <span>{{ statusLabel(event) }}</span>
          <span v-if="phaseLabel(event.temporalPhase)">{{ phaseLabel(event.temporalPhase) }}</span>
        </div>
        <div class="event-body">
          <h3>{{ event.title }}</h3>
          <p v-if="event.description">{{ event.description }}</p>
          <p v-if="event.followUpReason && event.status === 'PENDING'" class="event-meta">适合之后再聊：{{ event.followUpReason }}</p>
          <button v-if="event.diaryIds?.length" class="event-meta event-meta-link" type="button" title="查看关联日记" @click="openLinkedDiary(event)">
            关联 {{ event.diaryCount ?? event.diaryIds.length }} 篇日记 <span aria-hidden="true">↗</span>
          </button>
          <span v-else class="event-meta">暂无关联日记</span>
        </div>
        <div class="event-actions">
          <button class="text-button" type="button" title="编辑事件" @click="openEdit(event)">编辑</button>
          <button class="text-button primary" type="button" @click="chatAbout(event)">聊聊这件事</button>
          <button v-if="event.status === 'PENDING'" class="text-button" type="button" @click="markDone(event)">标记已跟进</button>
          <button v-else-if="event.status === 'FOLLOWED_UP'" class="text-button" type="button" @click="restore(event)">恢复待跟进</button>
          <button class="text-button danger" type="button" @click="removeEvent(event)">删除</button>
        </div>
      </article>
    </section>

    <button v-if="undoEvent" class="undo-toast" type="button" @click="undoFollowUp">已标记为已跟进 · <span>撤销</span></button>

    <div v-if="eventInfoOpen" class="modal-backdrop" @click.self="eventInfoOpen = false">
      <section class="info-dialog" role="dialog" aria-modal="true" aria-labelledby="event-info-title">
        <div class="editor-header"><div><p class="eyebrow">FOLLOW-UP</p><h3 id="event-info-title">事件回访怎么安排</h3></div><button class="close-button" type="button" aria-label="关闭" @click="eventInfoOpen = false">×</button></div>
        <div class="info-content">
          <p>MoodCopilot 会根据事件填写的日期和时间，在合适的时候回来问问你。</p>
          <p>只有到达设定时间的事件才会进入待回访；没有具体时间的事件，会按日期判断。</p>
          <p>标记为“已跟进”后，这件事会保留在记录中，但不会再次自动提醒。你也可以随时恢复为“待跟进”。</p>
        </div>
        <div class="info-actions"><button class="save-button" type="button" @click="eventInfoOpen = false">知道了</button></div>
      </section>
    </div>

    <div v-if="editorOpen" class="modal-backdrop" @click.self="closeEditor">
      <section class="event-editor" role="dialog" aria-modal="true" aria-labelledby="event-editor-title">
        <div class="editor-header">
          <div>
            <p class="eyebrow">EVENT NOTE</p>
            <h3 id="event-editor-title">{{ editing ? '编辑重要事件' : '添加重要事件' }}</h3>
          </div>
          <button class="close-button" type="button" aria-label="关闭" @click="closeEditor">×</button>
        </div>
        <div class="editor-form">
          <label>事件名称<input v-model.trim="form.title" maxlength="128" placeholder="例如：期末考试" /></label>
          <label>描述<textarea v-model.trim="form.description" maxlength="1000" rows="3" placeholder="可以补充一点背景"></textarea></label>
          <div class="field-grid">
            <label>开始日期<input v-model="form.targetDate" type="date" /></label>
            <label>结束日期（可选）<input v-model="form.endDate" type="date" /></label>
            <label>开始时间（可选）<input v-model="form.startTime" type="time" /></label>
            <label>结束时间（可选）<input v-model="form.endTime" type="time" /></label>
          </div>
          <div class="diary-picker">
            <div class="picker-heading"><strong>关联日记</strong><span>已选 {{ selectedDiaryIds.length }} 篇</span></div>
             <p class="picker-hint">按关键词或日期筛选，关联后可在事件聊天中回看这些日记。</p>
            <div class="diary-filters">
              <input v-model.trim="diaryKeyword" type="search" placeholder="搜索日记内容" @keyup.enter="applyDiaryFilters" />
              <input v-model="diaryStartDate" type="date" aria-label="日记开始日期" />
              <input v-model="diaryEndDate" type="date" aria-label="日记结束日期" />
              <button type="button" @click="applyDiaryFilters">筛选</button>
            </div>
            <div v-if="diariesLoading" class="picker-empty">正在加载日记...</div>
            <div v-else-if="diaries.length === 0" class="picker-empty">暂时没有可关联的日记</div>
            <label v-for="diary in diaries" v-else :key="diary.id" class="diary-option">
              <input v-model="selectedDiaryIds" type="checkbox" :value="diary.id" />
               <span><strong>{{ formatDate(diary.date) }}</strong><small>{{ diary.excerpt || '这篇日记没有文字内容' }}</small></span>
            </label>
            <button v-if="diariesHasMore" class="load-more-diaries" type="button" :disabled="diariesLoading" @click="loadMoreDiaries">加载更多日记</button>
          </div>
        </div>
        <p v-if="editorError" class="editor-error">{{ editorError }}</p>
        <div class="editor-actions"><button class="secondary-button" type="button" @click="closeEditor">取消</button><button class="save-button" type="button" :disabled="saving" @click="saveEvent">{{ saving ? '保存中...' : '保存事件' }}</button></div>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import { lifeEventApi, type LifeDiaryOption, type LifeEvent, type LifeEventPayload } from '../api/life'

const router = useRouter()
const events = ref<LifeEvent[]>([])
const diaries = ref<LifeDiaryOption[]>([])
const diaryKeyword = ref('')
const diaryStartDate = ref('')
const diaryEndDate = ref('')
const diaryPage = ref(1)
const diariesHasMore = ref(false)
const loading = ref(true)
const diariesLoading = ref(false)
const error = ref('')
const editorError = ref('')
const editorOpen = ref(false)
const editing = ref<LifeEvent | null>(null)
const saving = ref(false)
const selectedDiaryIds = ref<number[]>([])
const form = ref<LifeEventPayload>({ title: '', description: '', targetDate: '', endDate: '', startTime: '', endTime: '', diaryIds: [] })
const undoEvent = ref<LifeEvent | null>(null)
const eventInfoOpen = ref(false)
let undoTimer: ReturnType<typeof setTimeout> | undefined

onMounted(loadEvents)

async function loadEvents() {
  loading.value = true; error.value = ''
  try { events.value = (await lifeEventApi.list()).data.data || [] } catch { error.value = '事件暂时加载失败，请稍后再试。' } finally { loading.value = false }
}

async function loadDiaries(reset = true) {
  diariesLoading.value = true
  if (reset) diaryPage.value = 1
  try {
    const response = await lifeEventApi.diaries({ keyword: diaryKeyword.value || undefined, startDate: diaryStartDate.value || undefined, endDate: diaryEndDate.value || undefined, page: diaryPage.value, size: 20 })
    const result = response.data.data
    const items = result?.items || []
    diaries.value = reset ? items : [...diaries.value, ...items]
    diariesHasMore.value = Boolean(result?.hasMore)
  } catch { if (reset) diaries.value = []; diariesHasMore.value = false } finally { diariesLoading.value = false }
}
function applyDiaryFilters() {
  if (diaryStartDate.value && diaryEndDate.value && diaryStartDate.value > diaryEndDate.value) {
    editorError.value = '日记筛选的结束日期不能早于开始日期'
    return
  }
  editorError.value = ''
  void loadDiaries()
}
async function loadMoreDiaries() { if (!diariesHasMore.value || diariesLoading.value) return; diaryPage.value += 1; await loadDiaries(false) }

function openCreate() {
  editing.value = null; editorError.value = ''; selectedDiaryIds.value = []
  form.value = { title: '', description: '', targetDate: localDate(), endDate: '', startTime: '', endTime: '', diaryIds: [] }
  editorOpen.value = true; diaryKeyword.value = ''; diaryStartDate.value = ''; diaryEndDate.value = ''; void loadDiaries()
}
function openEdit(event: LifeEvent) {
  editing.value = event; editorError.value = ''; selectedDiaryIds.value = [...(event.diaryIds || [])]
  form.value = { title: event.title, description: event.description || '', targetDate: event.targetDate || '', endDate: event.endDate || '', startTime: event.startTime || '', endTime: event.endTime || '', diaryIds: selectedDiaryIds.value }
  editorOpen.value = true; diaryKeyword.value = ''; diaryStartDate.value = ''; diaryEndDate.value = ''; void loadDiaries()
}
function closeEditor() { if (!saving.value) editorOpen.value = false }

async function saveEvent() {
  editorError.value = ''
  if (!form.value.title?.trim()) { editorError.value = '请填写事件名称'; return }
  if (!form.value.targetDate) { editorError.value = '请选择开始日期'; return }
  if (form.value.endDate && form.value.endDate < form.value.targetDate) { editorError.value = '结束日期不能早于开始日期'; return }
  if (form.value.endTime && !form.value.startTime) { editorError.value = '结束时间不能单独填写'; return }
  if (form.value.targetDate === form.value.endDate && form.value.startTime && form.value.endTime && form.value.endTime < form.value.startTime) { editorError.value = '同一天的结束时间不能早于开始时间'; return }
  saving.value = true
  const payload = { ...form.value, diaryIds: selectedDiaryIds.value }
  try {
    const response = editing.value ? await lifeEventApi.update(editing.value.id, payload) : await lifeEventApi.create(payload)
    const saved = response.data.data
    if (saved && !editing.value) events.value.unshift(saved)
    if (saved && editing.value) Object.assign(editing.value, saved)
    editorOpen.value = false
  } catch (e: any) { editorError.value = e?.response?.data?.message || '保存失败，请稍后再试' } finally { saving.value = false }
}

function formatDate(value: string) { if (!value) return '未定日期'; const parts = value.split('-'); return parts.length === 3 ? `${Number(parts[1])}月${Number(parts[2])}日` : value }
function localDate() { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}` }
function formatSchedule(event: LifeEvent) { let text = formatDate(event.targetDate); if (event.endDate) text += ` - ${formatDate(event.endDate)}`; if (event.startTime) { text += ` ${event.startTime}`; if (event.endTime) text += ` - ${event.endTime}` } return text }
function statusLabel(event: LifeEvent) { return event.status === 'PENDING' ? (event.followUpCompleted ? '本轮已完成' : '待跟进') : '暂不再提醒' }
function phaseLabel(phase?: string) { return phase === 'UPCOMING' ? '即将发生' : phase === 'ONGOING' ? '正在经历' : phase === 'PAST' ? '已经发生' : '' }
function chatAbout(event: LifeEvent) { router.push({ name: 'chat', state: { eventId: event.id } }) }
function openLinkedDiary(event: LifeEvent) { const id = event.lastDiaryId ?? event.diaryIds?.[0]; if (id) router.push({ name: 'diary-detail', params: { id } }) }
async function updateStatus(event: LifeEvent, status: 'PENDING' | 'FOLLOWED_UP') { try { const response = await lifeEventApi.updateStatus(event.id, status); if (response.data.data) Object.assign(event, response.data.data); return true } catch { error.value = '状态更新失败，请稍后再试。'; return false } }
async function markDone(event: LifeEvent) { if (!await updateStatus(event, 'FOLLOWED_UP')) return; undoEvent.value = event; if (undoTimer) clearTimeout(undoTimer); undoTimer = setTimeout(() => { undoEvent.value = null }, 5000) }
async function undoFollowUp() { const event = undoEvent.value; if (!event) return; if (undoTimer) clearTimeout(undoTimer); if (await updateStatus(event, 'PENDING')) undoEvent.value = null }
function restore(event: LifeEvent) { void updateStatus(event, 'PENDING') }
async function removeEvent(event: LifeEvent) {
  if (!window.confirm(`确定删除“${event.title}”吗？只会删除事件，不会删除关联日记。`)) return
  try {
    await lifeEventApi.remove(event.id)
    events.value = events.value.filter(item => item.id !== event.id)
  } catch { error.value = '事件删除失败，请稍后再试。' }
}
</script>

<style scoped>
.life-page { min-height: 100vh; }
.life-intro { max-width: 860px; margin: 42px auto 28px; padding: 0 24px; display: flex; align-items: end; justify-content: space-between; gap: 20px; }
.eyebrow { margin: 0 0 10px; color: var(--color-primary); font-size: 11px; font-weight: 700; letter-spacing: .14em; }
.title-with-info { display: flex; align-items: center; gap: 10px; }.life-intro h2 { margin: 0 0 8px; color: var(--color-text); font-family: var(--font-display); font-size: 2.3rem; }.info-button { display: inline-grid; width: 22px; height: 22px; place-items: center; margin-bottom: 5px; border: 1px solid var(--color-primary); border-radius: 50%; background: transparent; color: var(--color-primary); cursor: pointer; font: inherit; font-size: 13px; font-weight: 700; line-height: 1; }.info-button:hover, .info-button:focus-visible { background: var(--color-primary); color: var(--color-on-primary); }
.life-intro p:last-child { max-width: 560px; margin: 0; color: var(--color-text-secondary); line-height: 1.7; }
.add-button, .save-button, .secondary-button { border: 1px solid var(--color-primary); border-radius: 5px; padding: 9px 13px; background: var(--color-primary); color: var(--color-on-primary); cursor: pointer; font: inherit; font-size: 13px; white-space: nowrap; }
.event-list { max-width: 860px; margin: 0 auto 70px; padding: 0 24px; }
.event-entry { display: grid; grid-template-columns: 160px minmax(0, 1fr) auto; gap: 22px; align-items: start; padding: 24px 0; border-top: 1px solid var(--color-border); }
.event-date { display: flex; flex-direction: column; gap: 6px; color: var(--color-text-muted); font-size: 12px; }
.event-date strong { color: var(--color-text); font-family: var(--font-display); font-size: 1.05rem; line-height: 1.45; }
.event-body h3 { margin: 0 0 7px; color: var(--color-text); font-family: var(--font-display); font-size: 1.28rem; }
.event-body p { margin: 0 0 10px; color: var(--color-text-secondary); line-height: 1.65; }
.event-meta { color: var(--color-text-muted); font-size: 12px; }.event-meta-link { display: inline-flex; gap: 4px; padding: 0; border: 0; background: transparent; color: var(--color-primary); cursor: pointer; font: inherit; font-size: 12px; }
.event-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 7px; }.text-button { border: 0; padding: 7px 0 7px 12px; background: transparent; color: var(--color-text-muted); cursor: pointer; font: inherit; font-size: 12px; white-space: nowrap; }.text-button.primary { color: var(--color-primary); font-weight: 650; }
.text-button.danger { color: var(--color-error); }
.state { padding: 42px 0; color: var(--color-text-muted); text-align: center; }.state.error, .editor-error { color: var(--color-error); }
.undo-toast { position: fixed; right: 24px; bottom: 24px; z-index: 20; border: 1px solid var(--color-border); border-radius: 6px; padding: 11px 14px; background: var(--color-surface); color: var(--color-text); box-shadow: var(--shadow-lg); cursor: pointer; font: inherit; font-size: 13px; }.undo-toast span { color: var(--color-primary); font-weight: 700; }
.modal-backdrop { position: fixed; inset: 0; z-index: 40; display: grid; place-items: center; padding: 24px; background: var(--color-overlay); }.event-editor { width: min(660px, 100%); max-height: min(760px, 92vh); overflow: auto; border: 1px solid var(--color-border); border-radius: 8px; padding: 26px; background: var(--color-surface); box-shadow: var(--shadow-xl); }.editor-header, .picker-heading, .editor-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; }.editor-header h3 { margin: 0; color: var(--color-text); font-family: var(--font-display); font-size: 1.5rem; }.close-button { border: 0; background: transparent; color: var(--color-text-muted); cursor: pointer; font-size: 26px; }.editor-form { display: grid; gap: 16px; margin-top: 24px; }.editor-form label { display: grid; gap: 7px; color: var(--color-text-secondary); font-size: 12px; }.editor-form input, .editor-form textarea { width: 100%; box-sizing: border-box; border: 1px solid var(--color-border); border-radius: 4px; padding: 10px; background: var(--color-bg); color: var(--color-text); font: inherit; font-size: 14px; }.field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }.diary-picker { border-top: 1px solid var(--color-border); padding-top: 16px; }.picker-heading strong { color: var(--color-text); font-size: 13px; }.picker-heading span, .picker-hint, .picker-empty { color: var(--color-text-muted); font-size: 12px; }.picker-hint { margin: 7px 0 12px; }.diary-option { display: flex !important; grid-template-columns: none !important; grid-template-rows: none !important; grid-auto-flow: column; align-items: start; gap: 10px !important; padding: 10px 0; border-top: 1px solid var(--color-border); }.diary-option input { width: auto; margin-top: 3px; }.diary-option span { display: grid; gap: 3px; }.diary-option strong { color: var(--color-text); font-size: 12px; }.diary-option small { color: var(--color-text-secondary); line-height: 1.5; }.editor-error { margin: 14px 0 0; font-size: 12px; }.editor-actions { justify-content: flex-end; margin-top: 24px; }.secondary-button { border-color: var(--color-border); background: transparent; color: var(--color-text-secondary); }.save-button:disabled { opacity: .55; cursor: wait; }
.info-dialog { width: min(480px, 100%); border: 1px solid var(--color-border); border-radius: 8px; padding: 26px; background: var(--color-surface); box-shadow: var(--shadow-xl); }.info-content { margin-top: 22px; color: var(--color-text-secondary); line-height: 1.75; }.info-content p { margin: 0 0 12px; }.info-content p:last-child { margin-bottom: 0; }.info-actions { display: flex; justify-content: flex-end; margin-top: 24px; }
.diary-filters { display: grid; grid-template-columns: minmax(0, 1.4fr) repeat(2, minmax(0, 1fr)) auto; gap: 7px; margin-bottom: 10px; }
.diary-filters input { min-width: 0; border: 1px solid var(--color-border); border-radius: 4px; padding: 8px; background: var(--color-bg); color: var(--color-text); font: inherit; font-size: 12px; }
.diary-filters button, .load-more-diaries { border: 1px solid var(--color-border); border-radius: 4px; padding: 8px 10px; background: transparent; color: var(--color-primary); cursor: pointer; font: inherit; font-size: 12px; white-space: nowrap; }
.load-more-diaries { display: block; width: 100%; margin-top: 10px; }
@media (max-width: 640px) { .life-intro { align-items: start; flex-direction: column; }.event-entry { grid-template-columns: 1fr; gap: 10px; }.event-actions { justify-content: flex-start; }.text-button { padding-left: 0; margin-right: 14px; }.modal-backdrop { padding: 12px; }.event-editor { padding: 20px; }.field-grid { grid-template-columns: 1fr; }.diary-filters { grid-template-columns: 1fr 1fr; }.diary-filters input:first-child, .diary-filters button { grid-column: span 2; } }
</style>
