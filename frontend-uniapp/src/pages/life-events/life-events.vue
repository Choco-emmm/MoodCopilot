<template>
  <view class="life-page" :style="globalThemeStyle">
    <GlobalUI />
    <view class="page-header">
      <view><text class="eyebrow">PENDING THREADS</text><text class="page-title">重要事件</text><text class="page-desc">那些还在心里占着位置的事，值得被记住，也值得回来问一句。</text></view>
      <view class="add-button" @click="openCreate">＋ 添加事件</view>
    </view>
    <view v-if="loading" class="state">正在整理你的事件线索...</view>
    <view v-else-if="events.length === 0" class="state">暂时没有重要事件。你也可以手动记下一件想回来关注的事。</view>
    <view v-else class="event-list">
      <view v-for="event in events" :key="event.id" class="event-item">
        <view class="event-date"><text class="date-main">{{ formatSchedule(event) }}</text><text>{{ statusLabel(event.status) }}</text></view>
        <view class="event-main">
          <text class="event-title">{{ event.title }}</text>
          <text v-if="event.description" class="event-desc">{{ event.description }}</text>
          <text class="event-meta">{{ event.diaryIds?.length ? `关联 ${event.diaryCount || event.diaryIds.length} 篇日记` : '暂无关联日记' }}</text>
          <view class="event-actions">
            <view class="event-action" @click="openEdit(event)">编辑</view>
            <view class="event-chat" @click="chatAbout(event)">聊聊这件事</view>
            <view v-if="event.status === 'PENDING'" class="event-action" @click="markDone(event)">标记已跟进</view>
            <view v-else-if="event.status === 'FOLLOWED_UP'" class="event-action" @click="updateStatus(event, 'PENDING')">恢复待跟进</view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="undoEvent" class="undo-toast" @click="undoFollowUp"><text>已标记为已跟进 · </text><text class="undo-action">撤销</text></view>

    <view v-if="editorOpen" class="modal-overlay" @click="closeEditor">
      <view class="editor-sheet" @click.stop>
        <view class="sheet-header"><view><text class="sheet-kicker">EVENT NOTE</text><text class="sheet-title">{{ editing ? '编辑重要事件' : '添加重要事件' }}</text></view><text class="sheet-close" @click="closeEditor">×</text></view>
        <scroll-view scroll-y class="editor-scroll" :show-scrollbar="false">
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
          <view class="diary-picker"><view class="picker-heading"><text>关联日记</text><text>{{ selectedDiaryIds.length }} 篇</text></view><text class="picker-hint">按日期和内容选择，事件聊天会优先使用日记摘要。</text><text v-if="diariesLoading" class="picker-empty">正在加载日记...</text><text v-else-if="diaries.length === 0" class="picker-empty">暂时没有可关联的日记</text><checkbox-group v-else @change="onDiaryChange"><label v-for="diary in diaries" :key="diary.id" class="diary-option"><checkbox :value="String(diary.id)" :checked="selectedDiaryIds.includes(diary.id)" color="#4a7c62" /><view><text class="diary-date">{{ formatDate(diary.date) }}</text><text class="diary-excerpt">{{ diary.summary || diary.excerpt || '无文字摘要' }}</text></view></label></checkbox-group></view>
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
import { get, post, put } from '@/utils/request'
import { hasLoginToken, requireLogin } from '@/stores/login'

interface LifeDiaryOption { id: number; date: string; excerpt: string; summary?: string }
interface LifeEvent { id: number; title: string; description?: string; targetDate: string; endDate?: string; startTime?: string; endTime?: string; status: string; diaryIds?: number[]; diaryCount?: number; lastDiaryId?: number }
interface EventForm { title: string; description: string; targetDate: string; endDate: string; startTime: string; endTime: string }
const events = ref<LifeEvent[]>([])
const diaries = ref<LifeDiaryOption[]>([])
const loading = ref(true)
const diariesLoading = ref(false)
const editorOpen = ref(false)
const editing = ref<LifeEvent | null>(null)
const saving = ref(false)
const editorError = ref('')
const selectedDiaryIds = ref<number[]>([])
const form = ref<EventForm>({ title: '', description: '', targetDate: '', endDate: '', startTime: '', endTime: '' })
const undoEvent = ref<LifeEvent | null>(null)
let undoTimer: ReturnType<typeof setTimeout> | undefined

onMounted(() => { if (!hasLoginToken()) { requireLogin(); loading.value = false; return } void loadEvents() })
async function loadEvents() { try { const res = await get<LifeEvent[]>('/api/life-events'); if (res.code === 200) events.value = res.data || [] } finally { loading.value = false } }
async function loadDiaries() { diariesLoading.value = true; try { const res = await get<LifeDiaryOption[]>('/api/life-events/diaries'); diaries.value = res.code === 200 ? res.data || [] : [] } finally { diariesLoading.value = false } }
function openCreate() { editing.value = null; editorError.value = ''; selectedDiaryIds.value = []; form.value = { title: '', description: '', targetDate: localDate(), endDate: '', startTime: '', endTime: '' }; editorOpen.value = true; void loadDiaries() }
function openEdit(event: LifeEvent) { editing.value = event; editorError.value = ''; selectedDiaryIds.value = [...(event.diaryIds || [])]; form.value = { title: event.title, description: event.description || '', targetDate: event.targetDate || '', endDate: event.endDate || '', startTime: event.startTime || '', endTime: event.endTime || '' }; editorOpen.value = true; void loadDiaries() }
function closeEditor() { if (!saving.value) editorOpen.value = false }
function onDiaryChange(event: any) { selectedDiaryIds.value = (event.detail.value || []).map((id: string) => Number(id)) }
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
function statusLabel(status: string) { return status === 'PENDING' ? '待跟进' : '已跟进' }
function chatAbout(event: LifeEvent) { uni.setStorageSync('pendingLifeEventId', event.id); uni.switchTab({ url: '/pages/chat/chat' }) }
async function updateStatus(event: LifeEvent, status: string) { const res = await put<LifeEvent>(`/api/life-events/${event.id}/status`, { status }); if (res.code === 200 && res.data) Object.assign(event, res.data); return res.code === 200 }
async function markDone(event: LifeEvent) { if (!await updateStatus(event, 'FOLLOWED_UP')) return; undoEvent.value = event; if (undoTimer) clearTimeout(undoTimer); undoTimer = setTimeout(() => { undoEvent.value = null }, 5000) }
async function undoFollowUp() { const event = undoEvent.value; if (!event) return; if (undoTimer) clearTimeout(undoTimer); if (await updateStatus(event, 'PENDING')) undoEvent.value = null }
</script>

<style scoped>
.life-page { min-height: 100vh; padding: 42rpx 36rpx 140rpx; box-sizing: border-box; background: var(--theme-bg); }.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 20rpx; margin: 28rpx 0 54rpx; }.eyebrow, .sheet-kicker { display: block; margin-bottom: 12rpx; color: var(--theme-primary); font-size: 20rpx; font-weight: 700; letter-spacing: 4rpx; }.page-title { display: block; color: var(--theme-text-primary); font-family: Georgia, serif; font-size: 58rpx; font-weight: 700; }.page-desc { display: block; max-width: 560rpx; margin-top: 18rpx; color: var(--theme-text-secondary); font-size: 25rpx; line-height: 1.7; }.add-button { flex-shrink: 0; padding: 16rpx 20rpx; border-radius: 6rpx; background: var(--theme-primary); color: #fff; font-size: 23rpx; }.event-list { border-top: 1rpx solid var(--theme-border); }.event-item { display: flex; gap: 24rpx; padding: 30rpx 0; border-bottom: 1rpx solid var(--theme-border); }.event-date { width: 170rpx; flex-shrink: 0; color: var(--theme-text-placeholder); font-size: 21rpx; line-height: 1.6; }.date-main { display: block; color: var(--theme-text-primary); font-family: Georgia, serif; font-size: 27rpx; }.event-main { min-width: 0; flex: 1; }.event-title, .event-desc, .event-meta { display: block; }.event-title { color: var(--theme-text-primary); font-family: Georgia, serif; font-size: 32rpx; font-weight: 700; }.event-desc { margin-top: 9rpx; color: var(--theme-text-secondary); font-size: 24rpx; line-height: 1.6; }.event-meta { margin-top: 12rpx; color: var(--theme-text-placeholder); font-size: 21rpx; }.event-actions { display: flex; flex-wrap: wrap; gap: 24rpx; margin-top: 20rpx; font-size: 23rpx; }.event-chat { color: var(--theme-primary); font-weight: 650; }.event-action { color: var(--theme-text-secondary); }.state { padding: 80rpx 20rpx; color: var(--theme-text-secondary); font-size: 26rpx; line-height: 1.7; text-align: center; }.undo-toast { position: fixed; right: 30rpx; bottom: calc(30rpx + env(safe-area-inset-bottom)); z-index: 30; display: flex; padding: 22rpx 28rpx; border: 1rpx solid var(--theme-border); border-radius: 8rpx; background: var(--theme-surface); color: var(--theme-text-primary); box-shadow: 0 10rpx 30rpx rgba(0,0,0,.16); font-size: 24rpx; }.undo-action { color: var(--theme-primary); font-weight: 700; }.modal-overlay { position: fixed; inset: 0; z-index: 50; display: flex; align-items: flex-end; background: rgba(21,25,22,.4); }.editor-sheet { width: 100%; max-height: 90vh; padding: 24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom)); box-sizing: border-box; border-radius: 14rpx 14rpx 0 0; background: var(--theme-surface); }.sheet-header { display: flex; align-items: flex-start; justify-content: space-between; }.sheet-title { display: block; color: var(--theme-text-primary); font-size: 34rpx; font-weight: 650; }.sheet-close { color: var(--theme-text-secondary); font-size: 44rpx; line-height: 1; }.editor-scroll { max-height: 68vh; margin-top: 24rpx; }.form-field { margin-bottom: 24rpx; color: var(--theme-text-secondary); font-size: 23rpx; }.form-field > text:first-child { display: block; margin-bottom: 10rpx; }.form-field input, .form-field textarea, .picker-value { width: 100%; box-sizing: border-box; padding: 18rpx; border: 1rpx solid var(--theme-border); border-radius: 5rpx; background: var(--theme-bg); color: var(--theme-text-primary); font-size: 27rpx; }.field-row { display: flex; gap: 18rpx; }.field-row .form-field { min-width: 0; flex: 1; }.picker-value { min-height: 64rpx; }.clear-link { display: block; margin-top: 8rpx; color: var(--theme-primary); font-size: 21rpx; }.diary-picker { border-top: 1rpx solid var(--theme-border); padding-top: 22rpx; }.picker-heading { display: flex; justify-content: space-between; color: var(--theme-text-primary); font-size: 27rpx; font-weight: 650; }.picker-heading text:last-child, .picker-hint, .picker-empty { color: var(--theme-text-placeholder); font-size: 22rpx; font-weight: 400; }.picker-hint { display: block; margin: 10rpx 0 14rpx; }.picker-empty { display: block; padding: 24rpx 0; }.diary-option { display: flex; align-items: flex-start; gap: 10rpx; padding: 18rpx 0; border-top: 1rpx solid var(--theme-border); }.diary-option > view { min-width: 0; }.diary-date, .diary-excerpt { display: block; }.diary-date { color: var(--theme-text-primary); font-size: 23rpx; }.diary-excerpt { margin-top: 5rpx; color: var(--theme-text-secondary); font-size: 22rpx; line-height: 1.5; }.editor-error { display: block; margin-top: 14rpx; color: #c74d4d; font-size: 23rpx; }.editor-actions { display: flex; justify-content: flex-end; gap: 20rpx; margin-top: 20rpx; }.cancel-button, .save-button { padding: 18rpx 28rpx; border-radius: 5rpx; font-size: 25rpx; }.cancel-button { color: var(--theme-text-secondary); }.save-button { background: var(--theme-primary); color: #fff; }.save-button.disabled { opacity: .55; }
@media (max-width: 420px) { .page-header { align-items: flex-start; flex-direction: column; }.event-date { width: 148rpx; }.field-row { display: block; } }
</style>
