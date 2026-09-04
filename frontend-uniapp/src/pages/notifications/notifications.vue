<template>
  <view class="notifications-page" :style="globalThemeStyle">
    <GlobalUI />
    <view v-if="loading" class="loading-state">
      <text>加载中...</text>
    </view>
    <view v-else-if="notifications.length === 0" class="empty-state">
      <text class="empty-text">暂无新消息。</text>
    </view>
    <scroll-view v-else class="notification-scroll" scroll-y :show-scrollbar="false" @scrolltolower="loadMore">
      <view class="notification-list">
      <view v-for="item in notifications" :key="item.id" class="notification-card" :class="{ unread: !item.isRead }" @click="openNotification(item)">
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
      <text v-if="loadingMore" class="list-status">正在加载...</text>
      <text v-else-if="!hasMore" class="list-status">已经到底了</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">

import GlobalUI from '@/components/GlobalUI.vue';

import { ref, onMounted } from 'vue';
import { get, put } from '@/utils/request';
import { currentTheme } from '@/stores/theme';

const notifications = ref<any[]>([]);
const loading = ref(true);
const loadingMore = ref(false);
const page = ref(1);
const hasMore = ref(true);
const pageSize = 20;
const socialNotificationTypes = new Set(['RESONANCE', 'COMMENT', 'FOLLOW']);

onMounted(() => {
  void fetchNotifications();
});

const fetchNotifications = async (isLoadMore = false) => {
  if (loadingMore.value || (isLoadMore && !hasMore.value)) return;
  if (isLoadMore) loadingMore.value = true;
  else {
    loading.value = true;
    page.value = 1;
    hasMore.value = true;
  }
  try {
    const res = await get('/api/notifications', { page: page.value, size: pageSize });
    if (res.code === 200) {
      const items = (res.data || []).filter((item: any) => !socialNotificationTypes.has(item.type));
      notifications.value = isLoadMore ? [...notifications.value, ...items] : items;
      hasMore.value = (res.data || []).length === pageSize;
      if (hasMore.value) page.value += 1;
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
};

const markRead = async (notification: any) => {
  if (notification.isRead) return true;
  try {
    const res = await put(`/api/notifications/${notification.id}/read`);
    if (res.code === 200) {
      notification.isRead = true;
      uni.$emit('refreshUnreadCount');
      return true;
    }
  } catch (e) {
    console.error(e);
  }
  return false;
};

const openNotification = async (notification: any) => {
  const didMarkRead = await markRead(notification);
  const eventId = Number(notification.lifeEventId || notification.eventId);
  if (didMarkRead && Number.isFinite(eventId) && eventId > 0) {
    uni.setStorageSync('pendingLifeEventId', eventId);
    uni.switchTab({ url: '/pages/chat/chat' });
    return;
  }
  if (didMarkRead && notification.diaryId) {
    uni.navigateTo({ url: `/pages/detail/detail?id=${notification.diaryId}` });
  }
};

const loadMore = () => {
  void fetchNotifications(true);
};

const getNotifTitle = (type: string) => {
  switch (type) {
    case 'AI_ANALYSIS_COMPLETE': return '日记分析已完成';
    case 'MEMORY_UPDATED': return '记忆已更新';
    case 'GRAPH_UPDATED': return '图谱已更新';
    case 'PROFILE_UPDATED': return '个人资料已更新';
    default: return '系统通知';
  }
};

const getNotifIconUrl = (type: string) => {
  const encodeSvg = (svg: string) => `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
  const primary = currentTheme.value.primary.replace('#', '%23');
  
  switch (type) {
    case 'AI_ANALYSIS_COMPLETE': 
      return encodeSvg(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="${primary}"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>`);
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

</script>

<style scoped>
.notifications-page {
  display: flex;
  height: 100vh;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--theme-bg);
  padding: 32rpx;
  box-sizing: border-box;
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
  padding-bottom: 40rpx;
}

.notification-scroll { min-height: 0; flex: 1; }
.list-status { display: block; padding: 28rpx 0; color: var(--theme-text-placeholder); font-size: 22rpx; text-align: center; }

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

