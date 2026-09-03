<template>
  <main class="app-shell life-page">
    <AppHeader />
    <section class="life-intro">
      <div>
        <p class="eyebrow">PENDING THREADS</p>
        <h2>重要事件</h2>
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
          <span>{{ statusLabel(event.status) }}</span>
        </div>
        <div class="event-body">
          <h3>{{ event.title }}</h3>
          <p v-if="event.description">{{ event.description }}</p>
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
        </div>
      </article>
    </section>

    <button v-if="undoEvent" class="undo-toast" type="button" @click="undoFollowUp">已标记为已跟进 · <span>撤销</span></button>

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
            <div class="picker-heading"><strong>关联日记</strong><span>{{ selectedDiaryIds.length }} 篇</span></div>
            <p class="picker-hint">按日期和内容选择，事件聊天会优先使用日记摘要。</p>
            <div v-if="diariesLoading" class="picker-empty">正在加载日记...</div>
            <div v-else-if="diaries.length === 0" class="picker-empty">暂时没有可关联的日记</div>
            <label v-for="diary in diaries" v-else :key="diary.id" class="diary-option">
              <input v-model="selectedDiaryIds" type="checkbox" :value="diary.id" />
              <span><strong>{{ formatDate(diary.date) }}</strong><small>{{ diary.summary || diary.excerpt || '无文字摘要' }}</small></span>
            </label>
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
let undoTimer: ReturnType<typeof setTimeout> | undefined

onMounted(loadEvents)

async function loadEvents() {
  loading.value = true; error.value = ''
  try { events.value = (await lifeEventApi.list()).data.data || [] } catch { error.value = '事件暂时加载失败，请稍后再试。' } finally { loading.value = false }
}

async function loadDiaries() {
  diariesLoading.value = true
  try { diaries.value = (await lifeEventApi.diaries()).data.data || [] } catch { diaries.value = [] } finally { diariesLoading.value = false }
}

function openCreate() {
  editing.value = null; editorError.value = ''; selectedDiaryIds.value = []
  form.value = { title: '', description: '', targetDate: localDate(), endDate: '', startTime: '', endTime: '', diaryIds: [] }
  editorOpen.value = true; void loadDiaries()
}
function openEdit(event: LifeEvent) {
  editing.value = event; editorError.value = ''; selectedDiaryIds.value = [...(event.diaryIds || [])]
  form.value = { title: event.title, description: event.description || '', targetDate: event.targetDate || '', endDate: event.endDate || '', startTime: event.startTime || '', endTime: event.endTime || '', diaryIds: selectedDiaryIds.value }
  editorOpen.value = true; void loadDiaries()
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
function statusLabel(status: string) { return status === 'PENDING' ? '待跟进' : '已跟进' }
function chatAbout(event: LifeEvent) { router.push({ name: 'chat', state: { eventId: event.id } }) }
function openLinkedDiary(event: LifeEvent) { const id = event.lastDiaryId ?? event.diaryIds?.[0]; if (id) router.push({ name: 'diary-detail', params: { id } }) }
async function updateStatus(event: LifeEvent, status: 'PENDING' | 'FOLLOWED_UP') { try { const response = await lifeEventApi.updateStatus(event.id, status); if (response.data.data) Object.assign(event, response.data.data); return true } catch { error.value = '状态更新失败，请稍后再试。'; return false } }
async function markDone(event: LifeEvent) { if (!await updateStatus(event, 'FOLLOWED_UP')) return; undoEvent.value = event; if (undoTimer) clearTimeout(undoTimer); undoTimer = setTimeout(() => { undoEvent.value = null }, 5000) }
async function undoFollowUp() { const event = undoEvent.value; if (!event) return; if (undoTimer) clearTimeout(undoTimer); if (await updateStatus(event, 'PENDING')) undoEvent.value = null }
function restore(event: LifeEvent) { void updateStatus(event, 'PENDING') }
</script>

<style scoped>
.life-page { min-height: 100vh; }
.life-intro { max-width: 860px; margin: 42px auto 28px; padding: 0 24px; display: flex; align-items: end; justify-content: space-between; gap: 20px; }
.eyebrow { margin: 0 0 10px; color: var(--color-primary); font-size: 11px; font-weight: 700; letter-spacing: .14em; }
.life-intro h2 { margin: 0 0 8px; color: var(--color-text); font-family: var(--font-display); font-size: 2.3rem; }
.life-intro p:last-child { max-width: 560px; margin: 0; color: var(--color-text-secondary); line-height: 1.7; }
.add-button, .save-button, .secondary-button { border: 1px solid var(--color-primary); border-radius: 5px; padding: 9px 13px; background: var(--color-primary); color: #fff; cursor: pointer; font: inherit; font-size: 13px; white-space: nowrap; }
.event-list { max-width: 860px; margin: 0 auto 70px; padding: 0 24px; }
.event-entry { display: grid; grid-template-columns: 160px minmax(0, 1fr) auto; gap: 22px; align-items: start; padding: 24px 0; border-top: 1px solid var(--color-border); }
.event-date { display: flex; flex-direction: column; gap: 6px; color: var(--color-text-muted); font-size: 12px; }
.event-date strong { color: var(--color-text); font-family: var(--font-display); font-size: 1.05rem; line-height: 1.45; }
.event-body h3 { margin: 0 0 7px; color: var(--color-text); font-family: var(--font-display); font-size: 1.28rem; }
.event-body p { margin: 0 0 10px; color: var(--color-text-secondary); line-height: 1.65; }
.event-meta { color: var(--color-text-muted); font-size: 12px; }.event-meta-link { display: inline-flex; gap: 4px; padding: 0; border: 0; background: transparent; color: var(--color-primary); cursor: pointer; font: inherit; font-size: 12px; }
.event-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 7px; }.text-button { border: 0; padding: 7px 0 7px 12px; background: transparent; color: var(--color-text-muted); cursor: pointer; font: inherit; font-size: 12px; white-space: nowrap; }.text-button.primary { color: var(--color-primary); font-weight: 650; }
.state { padding: 42px 0; color: var(--color-text-muted); text-align: center; }.state.error, .editor-error { color: var(--color-error); }
.undo-toast { position: fixed; right: 24px; bottom: 24px; z-index: 20; border: 1px solid var(--color-border); border-radius: 6px; padding: 11px 14px; background: var(--color-surface, #fff); color: var(--color-text); box-shadow: 0 8px 24px rgba(0,0,0,.12); cursor: pointer; font: inherit; font-size: 13px; }.undo-toast span { color: var(--color-primary); font-weight: 700; }
.modal-backdrop { position: fixed; inset: 0; z-index: 40; display: grid; place-items: center; padding: 24px; background: rgba(18, 25, 20, .42); }.event-editor { width: min(660px, 100%); max-height: min(760px, 92vh); overflow: auto; border: 1px solid var(--color-border); border-radius: 8px; padding: 26px; background: var(--color-surface); box-shadow: 0 18px 60px rgba(0,0,0,.2); }.editor-header, .picker-heading, .editor-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; }.editor-header h3 { margin: 0; color: var(--color-text); font-family: var(--font-display); font-size: 1.5rem; }.close-button { border: 0; background: transparent; color: var(--color-text-muted); cursor: pointer; font-size: 26px; }.editor-form { display: grid; gap: 16px; margin-top: 24px; }.editor-form label { display: grid; gap: 7px; color: var(--color-text-secondary); font-size: 12px; }.editor-form input, .editor-form textarea { width: 100%; box-sizing: border-box; border: 1px solid var(--color-border); border-radius: 4px; padding: 10px; background: var(--color-bg); color: var(--color-text); font: inherit; font-size: 14px; }.field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }.diary-picker { border-top: 1px solid var(--color-border); padding-top: 16px; }.picker-heading strong { color: var(--color-text); font-size: 13px; }.picker-heading span, .picker-hint, .picker-empty { color: var(--color-text-muted); font-size: 12px; }.picker-hint { margin: 7px 0 12px; }.diary-option { display: flex !important; grid-template-columns: none !important; grid-template-rows: none !important; grid-auto-flow: column; align-items: start; gap: 10px !important; padding: 10px 0; border-top: 1px solid var(--color-border); }.diary-option input { width: auto; margin-top: 3px; }.diary-option span { display: grid; gap: 3px; }.diary-option strong { color: var(--color-text); font-size: 12px; }.diary-option small { color: var(--color-text-secondary); line-height: 1.5; }.editor-error { margin: 14px 0 0; font-size: 12px; }.editor-actions { justify-content: flex-end; margin-top: 24px; }.secondary-button { border-color: var(--color-border); background: transparent; color: var(--color-text-secondary); }.save-button:disabled { opacity: .55; cursor: wait; }
@media (max-width: 640px) { .life-intro { align-items: start; flex-direction: column; }.event-entry { grid-template-columns: 1fr; gap: 10px; }.event-actions { justify-content: flex-start; }.text-button { padding-left: 0; margin-right: 14px; }.modal-backdrop { padding: 12px; }.event-editor { padding: 20px; }.field-grid { grid-template-columns: 1fr; } }
</style>
