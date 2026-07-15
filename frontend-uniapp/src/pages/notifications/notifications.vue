<template>
  <view class="notifications-page" :style="themeStyle">
    <GlobalUI />
    <view v-if="loading" class="loading-state">
      <text>加载中...</text>
    </view>
    <view v-else-if="notifications.length === 0" class="empty-state">
      <text class="empty-text">暂无新消息。</text>
    </view>
    <view v-else class="notification-list">
      <view v-for="item in notifications" :key="item.id" class="notification-card" :class="{ unread: !item.isRead }">
        <view class="icon-container">
          <image :src="getNotifIconUrl(item.type)" mode="aspectFit" class="notification-icon-img" />
        </view>
        <view class="content-container">
          <view class="notif-header">
            <text class="notif-title">{{ getNotifTitle(item.type) }}</text>
            <text class="notif-time">{{ formatTime(item.createdAt) }}</text>
          </view>
          <text class="notif-content">{{ item.content || item.message }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import GlobalUI from '@/components/GlobalUI.vue';
import { themeStyle, syncNavigationBarColor } from '@/stores/theme';
import { ref, onMounted, onUnmounted } from 'vue';
import { get, put } from '@/utils/request';

const notifications = ref<any[]>([]);
const loading = ref(true);

onMounted(() => {
  fetchNotifications();
});

onUnmounted(() => {
  markAllRead();
});

const fetchNotifications = async () => {
  loading.value = true;
  try {
    const res = await get('/api/notifications', { page: 1, size: 50 });
    if (res.code === 200) {
      notifications.value = res.data || [];
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const markAllRead = async () => {
  try {
    await put('/api/notifications/read-all');
    uni.$emit('notificationsRead');
  } catch (e) {
    console.error(e);
  }
};

const getNotifTitle = (type: string) => {
  switch (type) {
    case 'AI_ANALYSIS_COMPLETE': return '日记分析已完成';
    case 'RESONANCE': return '收到新共鸣';
    case 'COMMENT': return '收到新评论';
    case 'FOLLOW': return '新关注';
    default: return '系统通知';
  }
};

const getNotifIconUrl = (type: string) => {
  const encodeSvg = (svg: string) => `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
  const primary = currentTheme.value.primary.replace('#', '%23');
  const accent = currentTheme.value.accent.replace('#', '%23');
  
  switch (type) {
    case 'AI_ANALYSIS_COMPLETE': 
      return encodeSvg(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="${primary}"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>`);
    case 'RESONANCE': 
      return encodeSvg(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="${accent}"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>`);
    case 'COMMENT': 
      return encodeSvg(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="${primary}"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v10z"/></svg>`);
    case 'FOLLOW': 
      return encodeSvg(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="${primary}"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>`);
    default: 
      return encodeSvg(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="#7d7870"><path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2zm-2 1H8v-6c0-2.48 1.51-4.5 4-4.5s4 2.02 4 4.5v6z"/></svg>`);
  }
};

const formatTime = (dateStr: string) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const now = new Date();
  const diff = Math.floor((now.getTime() - date.getTime()) / 1000);
  
  if (diff < 60) return '刚刚';
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`;
  return `${date.getMonth() + 1}月${date.getDate()}日`;
};

onShow(() => {
  syncNavigationBarColor();
});
</script>

<style scoped>
.notifications-page {
  min-height: 100vh;
  background-color: var(--theme-bg);
  padding: 32rpx;
}

.loading-state, .empty-state {
  text-align: center;
  padding: 100rpx;
}

.empty-text {
  color: #7d7870;
  font-size: 28rpx;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.notification-card {
  display: flex;
  background-color: var(--theme-surface);
  border-radius: 4rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(32, 32, 29, 0.04);
  border: 1px solid rgba(var(--theme-primary-rgb), 0.1);
  align-items: flex-start;
}

.notification-card.unread {
  border-left: 8rpx solid var(--theme-primary);
  background-color: #f4fbf7;
}

.icon-container {
  margin-right: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80rpx;
  height: 80rpx;
  background-color: rgba(var(--theme-primary-rgb), 0.08);
  border-radius: 50%;
  flex-shrink: 0;
}

.notification-icon-img {
  width: 44rpx;
  height: 44rpx;
}

.content-container {
  flex: 1;
}

.notif-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.notif-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.notif-time {
  font-size: 24rpx;
  color: #7d7870;
}

.notif-content {
  font-size: 28rpx;
  color: #4a4a46;
  line-height: 1.6;
  display: block;
}
</style>

