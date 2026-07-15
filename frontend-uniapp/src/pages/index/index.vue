<template>
  <view class="container" :style="themeStyle">
    <GlobalUI :tabIndex="0" />
    <!-- Header Area -->
    <view class="header" :style="{ paddingTop: (statusBarHeight + 10) + 'px' }">
      <view class="title-container">
        <text class="title web-title">MoodCopilot</text>
      </view>
    </view>

    <!-- Main Feed Area -->
    <scroll-view 
      scroll-y 
      class="feed-area" 
      :style="{ paddingTop: '16rpx' }"
      @scrolltolower="loadMore"
      refresher-enabled
      :refresher-triggered="isRefreshing"
      @refresherrefresh="onRefresh"
    >
      <view v-if="isLoggedIn" class="top-banners">
        <view class="chat-banner" @click="goToChat">
          <text class="chat-banner-icon">✨</text>
          <text class="chat-banner-text">和 MoodCopilot 聊聊</text>
          <text class="chat-banner-arrow">→</text>
        </view>
      </view>

      <view v-if="!isLoggedIn" class="empty-state">
        <view class="lock-icon">
          <text class="lock-emoji">🔒</text>
        </view>
        <text class="empty-text">请前往“我的”页面登录。</text>
      </view>

      <view v-else-if="diaries.length > 0" class="feed-container">
        <view class="feed-container-header">
          <view class="feed-title-col">
            <text class="feed-subtitle">最近的心情</text>
          </view>
          <view class="feed-actions">
            <text class="refresh-btn" @click="onRefresh">刷新</text>
          </view>
        </view>

        <view class="diary-list">
          <!-- Diary List Items -->
          <view v-for="diary in diaries" :key="diary.id" class="diary-card hover-scale fade-in" @click="goToDetail(diary.id)">
            <view class="diary-date-header">
              <text class="date-day">{{ new Date(diary.createdAt).getDate() }}</text>
              <text class="date-month">{{ new Date(diary.createdAt).getMonth() + 1 }}月</text>
              <text class="date-time">{{ String(new Date(diary.createdAt).getHours()).padStart(2, '0') }}:{{ String(new Date(diary.createdAt).getMinutes()).padStart(2, '0') }}</text>
            </view>
            <view class="diary-author-info">
              <image :src="getFullUrl(diary.authorAvatar) || `data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23999999'%3E%3Cpath d='M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z'/%3E%3C/svg%3E`" class="author-avatar" mode="aspectFill" />
              <view class="author-details">
                <text class="author-name">{{ diary.authorName || '微信用户' }}</text>
                <text class="author-badge badge-admin" v-if="diary.authorRole === 'ADMIN'">管理员</text>
                <text class="author-badge badge-level">Lv.{{ diary.authorLevel || 1 }}</text>
              </view>
            </view>
            <text class="diary-content">{{ diary.content }}</text>
          
          <view v-if="diary.images && diary.images.length > 0" class="diary-images">
            <image 
              v-for="(img, idx) in diary.images" 
              :key="idx" 
              :src="img" 
              mode="aspectFill" 
              class="diary-img"
              @click.stop="previewImage(img, diary.images)"
            />
          </view>
          </view>
          
          <view class="no-more">
            <text class="no-more-text">{{ loading ? '正在加载...' : (hasMore ? '上滑加载更多' : '没有更多日记啦~') }}</text>
          </view>
        </view>
      </view>
      
      <view v-else-if="isLoggedIn && !loading" class="empty-state">
        <text class="empty-text">你还没有写过日记，开始记录吧。</text>
      </view>
    </scroll-view>

    <!-- Write Diary Floating Button -->
    <view class="fab-btn fade-in" v-if="isLoggedIn" @click="goToWrite">
      <text class="fab-icon">📝</text>
    </view>

    <!-- Login Popup (Bottom Sheet) -->
    <view class="login-mask fade-in" v-if="showLoginPopup" @click="showLoginPopup = false">
      <view class="login-sheet" @click.stop>
        <view class="login-sheet-header">
          <text class="login-sheet-title">欢迎使用 MoodCopilot</text>
          <text class="login-sheet-desc">一键登录，记录你的专属心情日记</text>
        </view>
        <view class="login-sheet-actions">
          <button class="wx-login-btn hover-scale" @click="handleWxLogin">微信快捷登录</button>
          <view class="cancel-login" @click="showLoginPopup = false">暂不登录，随便看看</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { get, post, del, getFullUrl } from '@/utils/request';
import { onPullDownRefresh, onShow, onReachBottom } from '@dcloudio/uni-app';
import GlobalUI from '@/components/GlobalUI.vue';
import { themeStyle } from '@/stores/theme';

const isLoggedIn = ref(false);
const diaries = ref<any[]>([]);
const page = ref(1);
const size = 10;
const hasMore = ref(true);
const loading = ref(false);
const isRefreshing = ref(false);
const unreadCount = ref(0);
const statusBarHeight = ref(uni.getSystemInfoSync().statusBarHeight || 20);
const showLoginPopup = ref(false);

onMounted(() => {
  checkLoginStatus();
  uni.$on('refreshFeed', () => {
    if (isLoggedIn.value) onRefresh();
  });
  uni.$on('notificationsRead', () => {
    unreadCount.value = 0;
  });
});

onUnmounted(() => {
  uni.$off('refreshFeed');
  uni.$off('notificationsRead');
});

const checkLoginStatus = () => {
  const token = uni.getStorageSync('token');
  if (token) {
    isLoggedIn.value = true;
    showLoginPopup.value = false;
    fetchUserInfo();
    fetchUnreadCount();
    onRefresh();
  } else {
    isLoggedIn.value = false;
    diaries.value = [];
    userInfo.value = null;
    showLoginPopup.value = true;
  }
};

const handleWxLogin = () => {
  uni.showLoading({ title: '登录中...' });
  uni.login({
    provider: 'weixin',
    success: async (loginRes) => {
      try {
        const res = await post('/api/auth/wx-login', { code: loginRes.code });
        if (res.code === 200 && res.data.token) {
          uni.setStorageSync('token', res.data.token);
          uni.showToast({ title: '登录成功', icon: 'success' });
          checkLoginStatus();
        } else {
          uni.showToast({ title: '登录失败', icon: 'none' });
        }
      } catch (e) {
        uni.showToast({ title: '网络错误', icon: 'none' });
        console.error(e);
      } finally {
        uni.hideLoading();
      }
    },
    fail: () => {
      uni.hideLoading();
      uni.showToast({ title: '授权失败', icon: 'none' });
    }
  });
};

const userInfo = ref<any>(null);

const fetchUserInfo = async () => {
  try {
    const res = await get('/api/auth/me');
    if (res.code === 200 && res.data && res.data.user) {
      userInfo.value = res.data.user;
      try {
        const quotaRes = await get('/api/users/quota');
        if (quotaRes.code === 200 && quotaRes.data) {
          userInfo.value.level = quotaRes.data.level || 1;
        }
      } catch (e) {
        console.error('Failed to fetch quota', e);
      }
    }
  } catch (e) {
    console.error('Failed to fetch user info', e);
  }
};

const fetchDiaries = async (isLoadMore = false) => {
  if (!isLoggedIn.value) return;
  if (loading.value || (!hasMore.value && isLoadMore)) return;
  
  loading.value = true;
  if (!isLoadMore) {
    page.value = 1;
    hasMore.value = true;
  }
  
  try {
    const res = await get(`/api/diaries/mine?page=${page.value}&size=${size}`);
    
    if (res.code === 200) {
      const newDiaries = res.data.items || res.data.content || res.data;
      if (isLoadMore) {
        diaries.value = [...diaries.value, ...newDiaries];
      } else {
        diaries.value = newDiaries;
      }
      
      hasMore.value = newDiaries.length === size;
      if (hasMore.value) page.value++;
    }
  } catch (e) {
    console.error('获取日记失败', e);
  } finally {
    loading.value = false;
    isRefreshing.value = false;
  }
};

const fetchUnreadCount = async () => {
  try {
    const res = await get('/api/notifications/unread-count');
    if (res.code === 200) {
      unreadCount.value = res.data?.count || 0;
    }
  } catch (e) {
    console.error(e);
  }
};

const onRefresh = () => {
  isRefreshing.value = true;
  fetchDiaries(false);
};

const loadMore = () => {
  fetchDiaries(true);
};

const goToWrite = () => {
  uni.navigateTo({ url: '/pages/write/write' });
};

const goToDetail = (id: string | number) => {
  uni.navigateTo({ url: `/pages/detail/detail?id=${id}` });
};

const goToProfile = () => {
  uni.switchTab({ url: '/pages/profile/profile' });
};

const goToChat = () => {
  uni.switchTab({ url: '/pages/chat/chat' });
};

const goToSearch = () => {
  uni.navigateTo({ url: '/pages/search/search' });
};

const goToNotifications = () => {
  uni.navigateTo({ url: '/pages/notifications/notifications' });
};

const formatDate = (dateStr: string) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`;
};

const previewImage = (current: string, urls: string[]) => {
  uni.previewImage({ current, urls });
};
</script>

<style scoped>
/* Base Layout */
.container {
  min-height: 100vh;
  background-color: var(--theme-bg);
  display: flex;
  flex-direction: column;
  position: relative;
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
}

.header {
  padding-bottom: 24rpx;
  padding-left: 48rpx;
  padding-right: 48rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  z-index: 10;
  background-color: var(--theme-bg);
}

.title {
  font-family: "Noto Serif SC", "Songti SC", "STSong", "KaiTi", serif;
  font-size: 48rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
  color: var(--primary-color);
}

.header-actions {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 24rpx;
}

.action-text-btn {
  display: flex;
  align-items: center;
  font-size: 28rpx;
  color: var(--theme-primary);
  font-weight: 500;
  gap: 8rpx;
}

.check-icon {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  border: 1px solid var(--theme-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
  color: var(--theme-text-placeholder);
}

.action-icon {
  position: relative;
  font-size: 40rpx;
}

.header-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  border: 2px solid var(--theme-surface);
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.1);
}

.notification-icon {
  position: relative;
}

.unread-dot {
  position: absolute;
  top: 0;
  right: 0;
  width: 16rpx;
  height: 16rpx;
  background-color: #e55353;
  border-radius: 50%;
  border: 2rpx solid var(--theme-bg);
}

.bell-emoji {
  font-size: 40rpx;
}

.tabs {
  display: flex;
  flex-direction: row;
  padding: 0 48rpx 16rpx;
  gap: 40rpx;
  border-bottom: 1px solid rgba(var(--theme-primary-rgb), 0.1);
}

.tab-item {
  font-size: 32rpx;
  color: var(--theme-text-placeholder);
  font-weight: 500;
  position: relative;
  padding-bottom: 8rpx;
  transition: all 0.2s ease;
}

.tab-item.active {
  color: var(--theme-text-primary);
  font-size: 34rpx;
  font-weight: 700;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 40rpx;
  height: 6rpx;
  background-color: var(--theme-primary);
  border-radius: 4rpx;
}

.user-actions {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.btn-login {
  background-color: var(--theme-primary);
  padding: 16rpx 32rpx;
  border-radius: 9999rpx;
  transition: transform 0.15s ease;
}

.btn-login:active {
  transform: scale(0.95);
}

.btn-login-text {
  color: #F6F2EA;
  font-size: 28rpx;
  font-weight: 500;
}

.avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 9999rpx;
  background-color: var(--theme-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.9;
  border: 1px solid var(--theme-primary);
}

.avatar-text {
  color: #F6F2EA;
  font-weight: 700;
  font-size: 28rpx;
}

/* Feed Area */
.feed-area {
  flex: 1;
  padding: 32rpx;
  box-sizing: border-box;
}

/* Empty State */
.empty-state {
  margin-top: 160rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  opacity: 0.8;
}

.chat-banner {
  margin: 0 16rpx 32rpx;
  padding: 32rpx;
  background-color: rgba(255, 255, 255, 0.8);
  border-radius: 4rpx;
  display: flex;
  align-items: center;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.03);
  backdrop-filter: blur(10px);
}

.lock-icon.fab {
  width: 110rpx;
  height: 110rpx;
  background-color: var(--theme-primary);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(var(--theme-primary-rgb), 0.4);
}

.fab-icon {
  font-size: 48rpx;
  color: #fff;
}

/* Login Bottom Sheet */
.login-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.login-sheet {
  background-color: var(--theme-surface);
  border-top-left-radius: 48rpx;
  border-top-right-radius: 48rpx;
  padding: 64rpx 48rpx;
  padding-bottom: calc(64rpx + env(safe-area-inset-bottom));
}

.login-sheet-header {
  text-align: center;
  margin-bottom: 64rpx;
}

.login-sheet-title {
  display: block;
  font-size: 44rpx;
  font-weight: 800;
  color: var(--theme-text-primary);
  margin-bottom: 16rpx;
}

.login-sheet-desc {
  display: block;
  font-size: 28rpx;
  color: var(--theme-text-secondary);
}

.wx-login-btn {
  background-color: #07c160;
  color: #fff;
  border-radius: 999rpx;
  font-size: 32rpx;
  font-weight: 600;
  padding: 12rpx 0;
  margin-bottom: 32rpx;
}

.wx-login-btn::after {
  border: none;
}

.cancel-login {
  text-align: center;
  font-size: 28rpx;
  color: var(--theme-text-placeholder);
  padding: 16rpx;
}

.empty-text {
  color: var(--theme-primary);
  font-size: 28rpx;
  opacity: 0.8;
  font-weight: 500;
}

.top-banners {
  margin: 0 40rpx 48rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.chat-banner {
  background-color: var(--theme-surface);
  border-radius: 16rpx;
  padding: 32rpx;
  display: flex;
  align-items: center;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.03);
}

.banner-tag {
  color: var(--theme-accent);
  font-weight: bold;
  font-size: 26rpx;
  margin-right: 16rpx;
}

.chat-banner-icon {
  font-size: 40rpx;
  margin-right: 16rpx;
}

.chat-banner-text {
  flex: 1;
  font-size: 30rpx;
  color: var(--theme-text-secondary);
}

.chat-banner-arrow {
  color: var(--theme-text-placeholder);
}

.feed-container {
  background-color: transparent;
  margin: 0 40rpx;
  padding: 0;
}

.feed-container-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.feed-subtitle {
  display: none;
}

.refresh-btn {
  font-size: 28rpx;
  color: var(--theme-text-secondary);
}

/* Diary List */
.diary-list {
  display: flex;
  flex-direction: column;
}

.diary-card {
  padding: 0 0 48rpx 0;
  margin-bottom: 48rpx;
  border-bottom: 1px dashed rgba(0, 0, 0, 0.1);
}

.diary-date-header {
  display: flex;
  align-items: baseline;
  margin-bottom: 16rpx;
}

.date-day {
  font-size: 64rpx;
  color: var(--theme-primary);
  font-family: "Noto Serif SC", serif;
  margin-right: 8rpx;
}

.date-month {
  font-size: 32rpx;
  color: var(--theme-primary);
  font-family: "Noto Serif SC", serif;
}

.date-time {
  font-size: 26rpx;
  color: var(--theme-text-placeholder);
  margin-left: 24rpx;
}

.diary-card:last-child {
  border-bottom: none;
}

.diary-card:active {
  transform: scale(0.98);
}

.diary-author-info {
  display: flex;
  align-items: center;
  margin-bottom: 12rpx;
}

.author-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  margin-right: 16rpx;
}

.author-details {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.author-name {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--theme-text-primary);
}

.author-level {
  font-size: 20rpx;
  background-color: var(--theme-border);
  color: var(--theme-text-secondary);
  padding: 2rpx 12rpx;
  border-radius: 2rpx;
  font-weight: bold;
}

.diary-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.diary-date {
  font-size: 24rpx;
  color: var(--theme-text-placeholder);
}

.visibility-tag {
  font-size: 20rpx;
  color: var(--theme-primary);
  background-color: rgba(var(--theme-primary-rgb), 0.1);
  padding: 4rpx 12rpx;
  border-radius: 4rpx;
  font-weight: bold;
}

.diary-content {
  font-size: 30rpx;
  color: var(--theme-text-primary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  overflow: hidden;
  text-overflow: ellipsis;
}

.diary-images {
  display: flex;
  flex-direction: row;
  gap: 16rpx;
  margin-top: 24rpx;
}

.diary-img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 2rpx;
  box-shadow: 0 2rpx 6rpx rgba(0,0,0,0.05);
}

.no-more {
  padding-top: 48rpx;
  padding-bottom: 48rpx;
  display: flex;
  justify-content: center;
}

.no-more-text {
  font-size: 24rpx;
  color: var(--theme-primary);
  opacity: 0.6;
}

/* Floating Action Button */
.fab-btn {
  position: fixed;
  bottom: calc(130rpx + env(safe-area-inset-bottom));
  right: 48rpx;
  width: 112rpx;
  height: 112rpx;
  background-color: var(--theme-primary);
  border-radius: 9999rpx;
  box-shadow: 0 10px 15px -3px rgba(var(--theme-primary-rgb), 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;
  transition: transform 0.15s ease;
}

.fab-btn:active {
  transform: scale(0.9);
}

.fab-icon {
  color: #F6F2EA;
  font-size: 60rpx;
  font-weight: 300;
  margin-bottom: 8rpx;
}
</style>

