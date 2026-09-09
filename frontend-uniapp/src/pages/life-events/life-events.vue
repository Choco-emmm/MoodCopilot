<template>
  <view class="life-page" :style="globalThemeStyle">
    <GlobalUI />
    <view class="page-header">
      <view><text class="eyebrow">PENDING THREADS</text><view class="title-with-info"><text class="page-title">重要事件</text><text class="info-button" aria-label="了解重要事件回访规则" @click="eventInfoOpen = true">i</text></view><text class="page-desc">那些还在心里占着位置的事，值得被记住，也值得回来问一句。</text></view>
      <view class="add-button" @click="openCreate">＋ 添加事件</view>
    </view>
    <view v-if="loading" class="state">正在整理你的事件线索...</view>
    <view v-else-if="events.length === 0" class="state">暂时没有重要事件。你也可以手动记下一件想回来关注的事。</view>
    <view v-else class="event-list">
      <view v-for="event in events" :key="event.id" class="event-item">
        <view class="event-date"><text class="date-main">{{ formatSchedule(event) }}</text><text>{{ statusLabel(event) }}</text><text v-if="phaseLabel(event.temporalPhase)">{{ phaseLabel(event.temporalPhase) }}</text></view>
        <view class="event-main">
          <text class="event-title">{{ event.title }}</text>
          <text v-if="event.description" class="event-desc">{{ event.description }}</text>
          <text v-if="event.followUpReason && event.status === 'PENDING'" class="event-meta">适合之后再聊：{{ event.followUpReason }}</text>
          <text class="event-meta">{{ event.diaryIds?.length ? `关联 ${event.diaryCount || event.diaryIds.length} 篇日记` : '暂无关联日记' }}</text>
          <view class="event-actions">
            <view class="event-action" @click="openEdit(event)">编辑</view>
            <view class="event-chat" @click="chatAbout(event)">聊聊这件事</view>
            <view v-if="event.status === 'PENDING'" class="event-action" @click="markDone(event)">标记已跟进</view>
            <view v-else-if="event.status === 'FOLLOWED_UP'" class="event-action" @click="updateStatus(event, 'PENDING')">恢复待跟进</view>
            <view class="event-action danger-action" @click="removeEvent(event)">删除</view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="undoEvent" class="undo-toast" @click="undoFollowUp"><text>已标记为已跟进 · </text><text class="undo-action">撤销</text></view>

    <view v-if="eventInfoOpen" class="modal-overlay" @click="eventInfoOpen = false">
      <view class="info-sheet" @click.stop>
        <view class="sheet-header"><view><text class="sheet-kicker">FOLLOW-UP</text><text class="sheet-title">事件回访怎么安排</text></view><text class="sheet-close" aria-label="关闭" @click="eventInfoOpen = false">×</text></view>
        <view class="info-content"><text>MoodCopilot 会根据事件填写的日期和时间，在合适的时候回来问问你。</text><text>只有到达设定时间的事件才会进入待回访；没有具体时间的事件，会按日期判断。</text><text>标记为“已跟进”后，这件事会保留在记录中，但不会再次自动提醒。你也可以随时恢复为“待跟进”。</text></view>
        <view class="info-actions" @click="eventInfoOpen = false">知道了</view>
      </view>
    </view>

    <view v-if="editorOpen" class="modal-overlay" @click="closeEditor">
      <view class="editor-sheet" @click.stop>
        <view class="sheet-header"><view><text class="sheet-kicker">EVENT NOTE</text><text class="sheet-title">{{ editing ? '编辑重要事件' : '添加重要事件' }}</text></view><text class="sheet-close" @click="closeEditor">×</text></view>
        <scroll-view scroll-y class="editor-scroll" :show-scrollbar="false" @scrolltolower="loadMoreDiaries" lower-threshold="80">
          <view class="form-field"><text>事件名称</text><input v-model="form.title" maxlength="128" placeholder="例如：期末考试" /></view>
          <view class="form-field"><text>描述</text><textarea v-model="form.description" maxlength="1000" auto-height placeholder="可以补充一点背景" /></view>
          <view class="field-row">
            <view class="form-field"><text>开始日期</text><picker mode="date" :value="form.targetDate" start="2000-01-01" end="2100-12-31" @change="form.targetDate = $event.detail.value"><view class="picker-value">{{ form.targetDate || '请选择' }}</view></picker></view>
            <view class="form-field"><text>结束日期</text><picker mode="date" :value="form.endDate || form.targetDate" start="2000-01-01" end="2100-12-31" @change="form.endDate = $event.detail.value"><view class="picker-value">{{ form.endDate || '可选' }}</view></picker><text v-if="form.endDate" class="clear-link" @click="form.endDate = ''">清除</text></view>
          </view>
          <view class="field-row">
            <view class="form-field"><text>开始时间</text><picker mode="time" :value="form.startTime" @change="form.startTime = $event.detail.value"><view class="picker-value">{{ form.startTime || '可选' }}</view></picker></view>
            <view class="form-field"><text>结束时间</text><picker mode="time" :value="form.endTime" @change="form.endTime = $event.detail.value"><view class="picker-value">{{ form.endTime || '可选' }}</view></picker><text v-if="form.endTime" class="clear-link" @click="form.endTime = ''">清除</text></view>
          </view>
           <view class="diary-picker"><view class="picker-heading"><text>关联日记</text><text>已选 {{ selectedDiaryIds.length }} 篇</text></view><text class="picker-hint">按关键词或日期筛选，关联后可在事件聊天中回看这些日记。</text><view class="diary-filters"><input v-model="diaryKeyword" placeholder="搜索日记内容" confirm-type="search" @confirm="applyDiaryFilters" /><input v-model="diaryStartDate" type="date" /><input v-model="diaryEndDate" type="date" /><view class="filter-button" @click="applyDiaryFilters">筛选</view></view><text v-if="diariesLoading && diaries.length === 0" class="picker-empty">正在加载日记...</text><text v-else-if="diaries.length === 0" class="picker-empty">暂时没有可关联的日记</text><checkbox-group v-else @change="onDiaryChange"><label v-for="diary in diaries" :key="diary.id" class="diary-option"><checkbox :value="String(diary.id)" :checked="selectedDiaryIds.includes(diary.id)" :color="currentTheme.primary" /><view><text class="diary-date">{{ formatDate(diary.date) }}</text><text class="diary-excerpt">{{ diary.excerpt || '这篇日记没有文字内容' }}</text></view></label></checkbox-group><view v-if="diariesHasMore" class="load-more-diaries" @click="loadMoreDiaries">{{ diariesLoading ? '正在加载...' : '加载更多日记' }}</view></view>
          <text v-if="editorError" class="editor-error">{{ editorError }}</text>
        </scroll-view>
        <view class="editor-actions"><view class="cancel-button" @click="closeEditor">取消</view><view class="save-button" :class="{ disabled: saving }" @click="saveEvent">{{ saving ? '保存中...' : '保存事件' }}</view></view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import GlobalUI from '@/components/GlobalUI.vue'
import { del, get, post, put } from '@/utils/request'
import { hasLoginToken, requireLogin } from '@/stores/login'
import { currentTheme } from '@/stores/theme'

interface LifeDiaryOption { id: number; date: string; excerpt: string; summary?: string }
interface LifeEvent { id: number; title: string; description?: string; targetDate: string; endDate?: string; startTime?: string; endTime?: string; status: string; diaryIds?: number[]; diaryCount?: number; lastDiaryId?: number; temporalPhase?: string; nextFollowUpAt?: string; followUpReason?: string; followUpCompleted?: boolean; followUpCount?: number }
interface EventForm { title: string; description: string; targetDate: string; endDate: string; startTime: string; endTime: string }
const events = ref<LifeEvent[]>([])
const diaries = ref<LifeDiaryOption[]>([])
const loading = ref(true)
const diariesLoading = ref(false)
const diaryKeyword = ref('')
const diaryStartDate = ref('')
const diaryEndDate = ref('')
const diaryPage = ref(1)
const diariesHasMore = ref(false)
const editorOpen = ref(false)
const editing = ref<LifeEvent | null>(null)
const saving = ref(false)
const editorError = ref('')
const selectedDiaryIds = ref<number[]>([])
const form = ref<EventForm>({ title: '', description: '', targetDate: '', endDate: '', startTime: '', endTime: '' })
const undoEvent = ref<LifeEvent | null>(null)
const eventInfoOpen = ref(false)
let undoTimer: ReturnType<typeof setTimeout> | undefined

onMounted(() => { if (!hasLoginToken()) { requireLogin(); loading.value = false; return } void loadEvents() })
async function loadEvents() { try { const res = await get<LifeEvent[]>('/api/life-events'); if (res.code === 200) events.value = res.data || [] } finally { loading.value = false } }
async function loadDiaries(reset = true) { diariesLoading.value = true; if (reset) diaryPage.value = 1; try { const res = await get<any>('/api/life-events/diaries', { keyword: diaryKeyword.value || undefined, startDate: diaryStartDate.value || undefined, endDate: diaryEndDate.value || undefined, page: diaryPage.value, size: 20 }); const result = res.code === 200 ? res.data : null; const items = result?.items || []; diaries.value = reset ? items : [...diaries.value, ...items]; diariesHasMore.value = Boolean(result?.hasMore) } finally { diariesLoading.value = false } }
function applyDiaryFilters() {
  if (diaryStartDate.value && diaryEndDate.value && diaryStartDate.value > diaryEndDate.value) {
    editorError.value = '日记筛选的结束日期不能早于开始日期'
    return
  }
  editorError.value = ''
  void loadDiaries()
}
async function loadMoreDiaries() { if (!diariesHasMore.value || diariesLoading.value) return; diaryPage.value += 1; await loadDiaries(false) }
function resetDiaryFilters() { diaryKeyword.value = ''; diaryStartDate.value = ''; diaryEndDate.value = '' }
function openCreate() { editing.value = null; editorError.value = ''; selectedDiaryIds.value = []; resetDiaryFilters(); form.value = { title: '', description: '', targetDate: localDate(), endDate: '', startTime: '', endTime: '' }; editorOpen.value = true; void loadDiaries() }
function openEdit(event: LifeEvent) { editing.value = event; editorError.value = ''; selectedDiaryIds.value = [...(event.diaryIds || [])]; resetDiaryFilters(); form.value = { title: event.title, description: event.description || '', targetDate: event.targetDate || '', endDate: event.endDate || '', startTime: event.startTime || '', endTime: event.endTime || '' }; editorOpen.value = true; void loadDiaries() }
function closeEditor() { if (!saving.value) editorOpen.value = false }
function onDiaryChange(event: any) {
  const visibleIds = new Set(diaries.value.map(diary => diary.id))
  const checkedIds = (event.detail.value || []).map((id: string) => Number(id))
  selectedDiaryIds.value = [...new Set([
    ...selectedDiaryIds.value.filter(id => !visibleIds.has(id)),
    ...checkedIds,
  ])]
}
async function saveEvent() {
  editorError.value = ''
  if (!form.value.title.trim()) { editorError.value = '请填写事件名称'; return }
  if (!form.value.targetDate) { editorError.value = '请选择开始日期'; return }
  if (form.value.endDate && form.value.endDate < form.value.targetDate) { editorError.value = '结束日期不能早于开始日期'; return }
  if (form.value.endTime && !form.value.startTime) { editorError.value = '结束时间不能单独填写'; return }
  if (form.value.targetDate === form.value.endDate && form.value.startTime && form.value.endTime && form.value.endTime < form.value.startTime) { editorError.value = '同一天的结束时间不能早于开始时间'; return }
  saving.value = true
  try {
    const payload = { ...form.value, diaryIds: selectedDiaryIds.value }
    const res = editing.value ? await put<LifeEvent>(`/api/life-events/${editing.value.id}`, payload) : await post<LifeEvent>('/api/life-events', payload)
    if (res.code !== 200 || !res.data) { editorError.value = res.message || '保存失败'; return }
    if (editing.value) Object.assign(editing.value, res.data)
    else events.value.unshift(res.data)
    editorOpen.value = false
  } catch (error: any) { editorError.value = error?.message || '保存失败，请稍后再试' } finally { saving.value = false }
}
function formatDate(value: string) { if (!value) return '未定日期'; const p = value.split('-'); return p.length === 3 ? `${Number(p[1])}月${Number(p[2])}日` : value }
function localDate() { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}` }
function formatSchedule(event: LifeEvent) { let text = formatDate(event.targetDate); if (event.endDate) text += ` - ${formatDate(event.endDate)}`; if (event.startTime) { text += ` ${event.startTime}`; if (event.endTime) text += ` - ${event.endTime}` } return text }
function statusLabel(event: LifeEvent) { return event.status === 'PENDING' ? (event.followUpCompleted ? '本轮已完成' : '待跟进') : '暂不再提醒' }
function phaseLabel(phase?: string) { return phase === 'UPCOMING' ? '即将发生' : phase === 'ONGOING' ? '正在经历' : phase === 'PAST' ? '已经发生' : '' }
function chatAbout(event: LifeEvent) { uni.setStorageSync('pendingLifeEventId', event.id); uni.switchTab({ url: '/pages/chat/chat' }) }
async function updateStatus(event: LifeEvent, status: string) { const res = await put<LifeEvent>(`/api/life-events/${event.id}/status`, { status }); if (res.code === 200 && res.data) Object.assign(event, res.data); return res.code === 200 }
async function markDone(event: LifeEvent) { if (!await updateStatus(event, 'FOLLOWED_UP')) return; undoEvent.value = event; if (undoTimer) clearTimeout(undoTimer); undoTimer = setTimeout(() => { undoEvent.value = null }, 5000) }
async function undoFollowUp() { const event = undoEvent.value; if (!event) return; if (undoTimer) clearTimeout(undoTimer); if (await updateStatus(event, 'PENDING')) undoEvent.value = null }
function removeEvent(event: LifeEvent) {
  uni.showModal({
    title: '删除重要事件',
    content: '只会删除事件，不会删除关联日记。确定继续吗？',
    confirmText: '删除',
    success: async ({ confirm }) => {
      if (!confirm) return
      const res = await del<any>(`/api/life-events/${event.id}`)
      if (res.code === 200) events.value = events.value.filter(item => item.id !== event.id)
    },
  })
}
</script>

<style scoped>
.life-page { min-height: 100vh; padding: 28rpx var(--theme-page-padding) calc(112rpx + env(safe-area-inset-bottom)); }
.page-header { margin: 20rpx 0 34rpx; padding-bottom: 24rpx; border-bottom: 1rpx solid var(--theme-border); }

.eyebrow { display: block; font-size: 22rpx; font-weight: bold; color: var(--theme-primary); margin-bottom: 8rpx; letter-spacing: 0.05em; text-transform: uppercase; }
.title-with-info { display: flex; align-items: center; gap: 12rpx; margin-bottom: 16rpx; }
.page-title { font-family: "Noto Serif SC", "Songti SC", "STSong", serif; font-size: 52rpx; font-weight: bold; color: var(--theme-text-primary); }
.info-button { display: inline-flex; justify-content: center; align-items: center; width: 36rpx; height: 36rpx; border: 1rpx solid var(--theme-text-secondary); border-radius: 50%; color: var(--theme-text-secondary); font-size: 22rpx; }
.page-desc { display: block; font-size: 28rpx; color: var(--theme-text-secondary); line-height: 1.6; margin-bottom: 24rpx; }

.add-button { display: inline-flex; align-items: center; justify-content: center; padding: 16rpx 32rpx; border-radius: var(--theme-radius-sm); background: var(--theme-primary); color: var(--theme-bg); font-size: 28rpx; font-weight: 500; }

.state { margin-top: 60rpx; font-size: 28rpx; color: var(--theme-text-secondary); text-align: center; }

/* Event Items */
.event-list { display: flex; flex-direction: column; }
.event-item { display: flex; flex-direction: column; gap: 24rpx; padding: 32rpx; margin-bottom: 24rpx; border: 1rpx solid var(--theme-border); border-radius: var(--theme-radius-lg); background: var(--theme-surface); box-shadow: var(--theme-shadow-panel); }


.event-date { display: flex; flex-direction: column; gap: 8rpx; }
.date-main { font-family: "Noto Serif SC", "Songti SC", "STSong", serif; font-size: 34rpx; font-weight: 700; color: var(--theme-text-primary); }
.event-date > text:not(.date-main) { font-size: 24rpx; color: var(--theme-text-secondary); }

.event-main { display: flex; flex-direction: column; gap: 16rpx; }
.event-title { font-family: "Noto Serif SC", "Songti SC", "STSong", serif; font-size: 36rpx; font-weight: bold; color: var(--theme-text-primary); line-height: 1.4; }
.event-desc { font-size: 28rpx; color: var(--theme-text-secondary); line-height: 1.6; }
.event-meta { font-size: 26rpx; color: var(--theme-text-muted); line-height: 1.5; }

.event-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 32rpx; margin-top: 16rpx; }
.event-action, .event-chat { font-size: 26rpx; cursor: pointer; padding: 8rpx 0; }
.event-action { color: var(--theme-text-secondary); }
.event-chat { color: var(--theme-primary); font-weight: bold; }
.danger-action { color: var(--theme-error); }

/* Overlays & Modals */
.undo-toast { position: fixed; bottom: 140rpx; left: 50%; transform: translateX(-50%); display: flex; align-items: center; gap: 16rpx; padding: 20rpx 40rpx; border-radius: 999rpx; background: var(--theme-surface); box-shadow: var(--theme-shadow-dialog); font-size: 28rpx; z-index: 100; border: 1rpx solid var(--theme-border); }
.undo-action { color: var(--theme-primary); font-weight: bold; }

.modal-overlay { position: fixed; inset: 0; z-index: 40; display: flex; align-items: flex-end; background: var(--theme-overlay); }
.editor-sheet, .info-sheet { width: 100%; border-radius: var(--theme-radius-lg) var(--theme-radius-lg) 0 0; background: var(--theme-surface); box-shadow: var(--theme-shadow-dialog); padding-bottom: env(safe-area-inset-bottom); }

.sheet-header { display: flex; justify-content: space-between; align-items: center; padding: 32rpx 40rpx; border-bottom: 1rpx solid var(--theme-border); }
.sheet-kicker { display: block; font-size: 20rpx; color: var(--theme-primary); font-weight: bold; letter-spacing: 0.05em; text-transform: uppercase; margin-bottom: 4rpx; }
.sheet-title { font-family: "Noto Serif SC", "Songti SC", "STSong", serif; font-size: 36rpx; font-weight: bold; color: var(--theme-text-primary); }
.sheet-close { font-size: 48rpx; color: var(--theme-text-secondary); line-height: 1; padding: 0 10rpx; }

.info-content { padding: 40rpx; display: flex; flex-direction: column; gap: 24rpx; font-size: 28rpx; color: var(--theme-text-secondary); line-height: 1.6; }
.info-actions { margin: 0 40rpx 40rpx; padding: 24rpx 0; text-align: center; background: var(--theme-primary); color: var(--theme-bg); border-radius: var(--theme-radius-sm); font-size: 30rpx; font-weight: bold; }

.editor-scroll { max-height: 70vh; padding: 40rpx; box-sizing: border-box; }
.form-field { display: flex; flex-direction: column; gap: 16rpx; margin-bottom: 32rpx; flex: 1; }
.form-field text:first-child { font-size: 26rpx; color: var(--theme-text-secondary); font-weight: bold; }
.form-field input, .form-field textarea { width: 100%; box-sizing: border-box; border: 1rpx solid var(--theme-border); border-radius: 8rpx; padding: 20rpx; background: var(--theme-bg); color: var(--theme-text-primary); font-size: 28rpx; }
.picker-value { border: 1rpx solid var(--theme-border); border-radius: 8rpx; padding: 20rpx; background: var(--theme-bg); color: var(--theme-text-primary); font-size: 28rpx; }
.field-row { display: flex; gap: 24rpx; }
.clear-link { font-size: 24rpx; color: var(--theme-primary); align-self: flex-end; margin-top: -12rpx; margin-bottom: 12rpx; }

.diary-picker { padding-top: 32rpx; border-top: 1rpx solid var(--theme-border); }
.picker-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.picker-heading text:first-child { font-size: 28rpx; font-weight: bold; color: var(--theme-text-primary); }
.picker-heading text:last-child { font-size: 24rpx; color: var(--theme-text-secondary); }
.picker-hint { display: block; font-size: 24rpx; color: var(--theme-text-muted); margin-bottom: 24rpx; }
.picker-empty { display: block; font-size: 26rpx; color: var(--theme-text-muted); text-align: center; padding: 40rpx 0; }

.diary-filters { display: flex; flex-wrap: wrap; gap: 16rpx; margin-bottom: 24rpx; }
.diary-filters input { min-width: 0; flex: 1 1 180rpx; box-sizing: border-box; padding: 16rpx; border: 1rpx solid var(--theme-border); border-radius: 8rpx; background: var(--theme-bg); color: var(--theme-text-primary); font-size: 26rpx; }
.diary-filters .filter-button { flex: 0 0 auto; padding: 16rpx 24rpx; border: 1rpx solid var(--theme-border); border-radius: 8rpx; color: var(--theme-primary); font-size: 26rpx; font-weight: bold; }

.diary-option { display: flex; align-items: flex-start; gap: 20rpx; padding: 24rpx 0; border-bottom: 1rpx solid var(--theme-border); }
.diary-option:last-child { border-bottom: none; }
.diary-date { display: block; font-size: 26rpx; font-weight: bold; color: var(--theme-text-primary); margin-bottom: 8rpx; }
.diary-excerpt { display: block; font-size: 26rpx; color: var(--theme-text-secondary); line-height: 1.5; }

.load-more-diaries { padding: 32rpx 0; color: var(--theme-primary); font-size: 26rpx; text-align: center; font-weight: bold; }
.editor-error { display: block; font-size: 26rpx; color: var(--theme-error); margin-top: 16rpx; }

.editor-actions { display: flex; gap: 24rpx; padding: 32rpx 40rpx; border-top: 1rpx solid var(--theme-border); background: var(--theme-surface); }
.cancel-button { flex: 1; padding: 24rpx 0; text-align: center; font-size: 30rpx; color: var(--theme-text-secondary); border: 1rpx solid var(--theme-border); border-radius: var(--theme-radius-sm); }
.save-button { flex: 2; padding: 24rpx 0; text-align: center; font-size: 30rpx; font-weight: bold; color: var(--theme-bg); background: var(--theme-primary); border-radius: var(--theme-radius-sm); }
.save-button.disabled { opacity: 0.6; pointer-events: none; }
</style>
