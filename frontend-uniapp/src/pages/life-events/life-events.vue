<template>
  <view class="life-page" :style="globalThemeStyle">
    <GlobalUI />
    <view class="page-header">
      <text class="eyebrow">PENDING THREADS</text>
      <text class="page-title">重要事件</text>
      <text class="page-desc">那些还在心里占着位置的事，值得被记住，也值得回来问一句。</text>
    </view>
    <view v-if="loading" class="state">正在整理你的事件线索...</view>
    <view v-else-if="events.length === 0" class="state">暂时没有待跟进的事件。继续记录，故事会慢慢长出来。</view>
    <view v-else class="event-list">
      <view v-for="event in events" :key="event.id" class="event-item">
        <view class="event-date">
          <text class="date-main">{{ formatDate(event.targetDate) }}</text>
          <text>{{ statusLabel(event.status) }}</text>
        </view>
        <view class="event-main">
          <text class="event-title">{{ event.title }}</text>
          <text v-if="event.description" class="event-desc">{{ event.description }}</text>
          <text class="event-meta">关联 {{ event.diaryIds?.length || 0 }} 篇日记</text>
          <view class="event-actions">
            <view class="event-chat" @click="chatAbout(event)">聊聊这件事</view>
            <view v-if="event.status === 'PENDING'" class="event-action" @click="markDone(event)">标记已跟进</view>
            <view v-else-if="event.status === 'FOLLOWED_UP'" class="event-action" @click="updateStatus(event, 'PENDING')">恢复待跟进</view>
          </view>
        </view>
      </view>
    </view>
    <view v-if="undoEvent" class="undo-toast" @click="undoFollowUp">
      <text>已标记为已跟进 · </text><text class="undo-action">撤销</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import GlobalUI from '@/components/GlobalUI.vue'
import { get, put } from '@/utils/request'
import { hasLoginToken, requireLogin } from '@/stores/login'

interface LifeEvent { id: number; title: string; description?: string; targetDate: string; status: string; diaryIds?: number[] }
const events = ref<LifeEvent[]>([])
const loading = ref(true)

onMounted(() => {
  if (!hasLoginToken()) {
    requireLogin()
    loading.value = false
    return
  }
  void loadEvents()
})

async function loadEvents() {
  try {
    const res = await get<LifeEvent[]>('/api/life-events')
    if (res.code === 200) events.value = res.data || []
  } finally {
    loading.value = false
  }
}

function formatDate(value: string) {
  if (!value) return '未定日期'
  const parts = value.split('-')
  return parts.length === 3 ? `${Number(parts[1])}月${Number(parts[2])}日` : value
}

function statusLabel(status: string) { return status === 'PENDING' ? '待跟进' : '已跟进' }

function chatAbout(event: LifeEvent) {
  uni.setStorageSync('pendingLifeEventId', event.id)
  uni.switchTab({ url: '/pages/chat/chat' })
}

async function updateStatus(event: LifeEvent, status: string) {
  const res = await put(`/api/life-events/${event.id}/status`, { status })
  if (res.code === 200 && res.data) Object.assign(event, res.data)
  return res.code === 200
}

const undoEvent = ref<LifeEvent | null>(null)
let undoTimer: ReturnType<typeof setTimeout> | undefined

async function markDone(event: LifeEvent) {
  if (!await updateStatus(event, 'FOLLOWED_UP')) return
  undoEvent.value = event
  if (undoTimer) clearTimeout(undoTimer)
  undoTimer = setTimeout(() => { undoEvent.value = null }, 5000)
}

async function undoFollowUp() {
  const event = undoEvent.value
  if (!event) return
  if (undoTimer) clearTimeout(undoTimer)
  if (await updateStatus(event, 'PENDING')) undoEvent.value = null
}
</script>

<style scoped>
.life-page { min-height: 100vh; padding: 42rpx 36rpx 140rpx; box-sizing: border-box; background: var(--theme-bg); }
.page-header { margin: 28rpx 0 54rpx; }
.eyebrow { display: block; margin-bottom: 12rpx; color: var(--theme-primary); font-size: 20rpx; font-weight: 700; letter-spacing: 4rpx; }
.page-title { display: block; color: var(--theme-text-primary); font-family: Georgia, serif; font-size: 58rpx; font-weight: 700; }
.page-desc { display: block; max-width: 620rpx; margin-top: 18rpx; color: var(--theme-text-secondary); font-size: 25rpx; line-height: 1.7; }
.event-list { border-top: 1rpx solid var(--theme-border); }
.event-item { display: flex; gap: 24rpx; padding: 30rpx 0; border-bottom: 1rpx solid var(--theme-border); }
.event-date { width: 128rpx; flex-shrink: 0; color: var(--theme-text-placeholder); font-size: 21rpx; line-height: 1.6; }
.date-main { display: block; color: var(--theme-text-primary); font-family: Georgia, serif; font-size: 27rpx; }
.event-main { min-width: 0; flex: 1; }
.event-title, .event-desc, .event-meta { display: block; }
.event-title { color: var(--theme-text-primary); font-family: Georgia, serif; font-size: 32rpx; font-weight: 700; }
.event-desc { margin-top: 9rpx; color: var(--theme-text-secondary); font-size: 24rpx; line-height: 1.6; }
.event-meta { margin-top: 12rpx; color: var(--theme-text-placeholder); font-size: 21rpx; }
.event-actions { display: flex; flex-wrap: wrap; gap: 24rpx; margin-top: 20rpx; font-size: 23rpx; }
.event-chat { color: var(--theme-primary); font-weight: 650; }
.event-action { color: var(--theme-text-secondary); }
.undo-toast { position: fixed; right: 30rpx; bottom: calc(30rpx + env(safe-area-inset-bottom)); z-index: 30; display: flex; align-items: center; padding: 22rpx 28rpx; border: 1rpx solid var(--theme-border); border-radius: 8rpx; background: var(--theme-surface); color: var(--theme-text-primary); box-shadow: 0 10rpx 30rpx rgba(0,0,0,.16); font-size: 24rpx; }
.undo-action { color: var(--theme-primary); font-weight: 700; }
.state { padding: 80rpx 20rpx; color: var(--theme-text-secondary); font-size: 26rpx; line-height: 1.7; text-align: center; }
</style>
