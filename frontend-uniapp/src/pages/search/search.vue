<template>
  <view class="search-page" :style="themeStyle">
    <GlobalUI />
    
    <view class="search-header">
      <view class="search-bar">
        <text class="search-icon">🔍</text>
        <input 
          class="search-input" 
          v-model="keyword" 
          placeholder="搜索过去的日记内容..." 
          :focus="true"
          confirm-type="search"
          @confirm="onSearch"
        />
        <text v-if="keyword" class="clear-btn" @click="clearSearch">×</text>
      </view>
    </view>

    <scroll-view class="search-results" scroll-y @scrolltolower="loadMore">
      <view v-if="loading" class="loading-state">搜索中...</view>
      
      <view v-else-if="results.length > 0" class="diary-list">
        <view 
          class="diary-card smooth-shadow hover-scale fade-in" 
          v-for="diary in results" 
          :key="diary.id"
          @click="goToDetail(diary.id)"
        >
          <view class="diary-author-info">
            <image :src="getFullUrl(diary.authorAvatar) || `data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23999999'%3E%3Cpath d='M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z'/%3E%3C/svg%3E`" class="author-avatar" mode="aspectFill" />
            <view class="author-details">
              <text class="author-name">{{ diary.authorName || '微信用户' }}</text>
              <text class="author-level">Lv.{{ diary.authorLevel || 1 }}</text>
            </view>
          </view>
          <view class="diary-meta">
            <text class="diary-time">{{ formatDate(diary.createdAt) }}</text>
            <view v-if="diary.musicMeta" class="music-tag">🎵 {{ diary.musicMeta.title }}</view>
          </view>
          <text class="diary-content" :class="{ 'has-images': diary.images && diary.images.length > 0 }">
            {{ diary.content }}
          </text>
          <view class="diary-images" v-if="diary.images && diary.images.length > 0">
            <image 
              v-for="(img, idx) in diary.images.slice(0, 3)" 
              :key="idx" 
              :src="img" 
              mode="aspectFill" 
              class="preview-img"
            />
            <view v-if="diary.images.length > 3" class="more-img-mask">+{{ diary.images.length - 3 }}</view>
          </view>
        </view>
        <view v-if="!hasMore && results.length > 0" class="no-more">没有更多记录了</view>
      </view>
      
      <view v-else-if="searched" class="empty-state">
        <text>没有找到相关日记</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { get, getFullUrl } from '@/utils/request';
import GlobalUI from '@/components/GlobalUI.vue';
import { themeStyle } from '@/stores/theme';

const keyword = ref('');
const results = ref<any[]>([]);
const loading = ref(false);
const searched = ref(false);

const userInfo = ref<any>(null);

onMounted(() => {
  fetchUserInfo();
});

const fetchUserInfo = async () => {
  try {
    const res = await get('/api/auth/me');
    if (res.code === 200 && res.data) {
      userInfo.value = res.data.user;
      const quotaRes = await get('/api/user/quota');
      if (quotaRes.code === 200) {
        userInfo.value.level = quotaRes.data.level;
      }
    }
  } catch (e) {
    console.error('Failed to fetch user info', e);
  }
};

const page = ref(1);
const size = 20;
const hasMore = ref(true);

const onSearch = () => {
  if (!keyword.value.trim()) return;
  page.value = 1;
  results.value = [];
  hasMore.value = true;
  searched.value = true;
  fetchResults();
};

const clearSearch = () => {
  keyword.value = '';
  results.value = [];
  searched.value = false;
};

const fetchResults = async () => {
  if (!hasMore.value || loading.value) return;
  loading.value = true;
  try {
    const res = await get('/api/diaries/search', {
      keyword: keyword.value.trim(),
      page: page.value,
      size
    });
    if (res.code === 200 && res.data) {
      const items = res.data.items || [];
      results.value.push(...items);
      hasMore.value = items.length >= size;
      page.value++;
    }
  } catch (e) {
    uni.showToast({ title: '搜索失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
};

const loadMore = () => {
  if (hasMore.value && !loading.value) {
    fetchResults();
  }
};

const goToDetail = (id: number) => {
  uni.navigateTo({ url: `/pages/detail/detail?id=${id}` });
};

const formatDate = (isoStr: string) => {
  if (!isoStr) return '';
  const date = new Date(isoStr);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
};
</script>

<style scoped>
.search-page {
  min-height: 100vh;
  background-color: var(--theme-bg);
  display: flex;
  flex-direction: column;
}

.search-header {
  padding: 20rpx 40rpx;
  background-color: var(--theme-bg);
  position: sticky;
  top: 0;
  z-index: 10;
}

.search-bar {
  display: flex;
  align-items: center;
  background-color: var(--theme-surface);
  border-radius: 4rpx;
  padding: 16rpx 32rpx;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.03);
}

.search-icon {
  font-size: 32rpx;
  color: #a09d98;
  margin-right: 16rpx;
}

.search-input {
  flex: 1;
  font-size: 28rpx;
  color: var(--theme-text-primary);
}

.clear-btn {
  font-size: 36rpx;
  color: #a09d98;
  padding: 0 16rpx;
}

.search-results {
  flex: 1;
  padding: 20rpx 40rpx;
  box-sizing: border-box;
}

.diary-list {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
  padding-bottom: 64rpx;
}

.diary-card {
  background-color: var(--theme-surface);
  border-radius: 4rpx;
  padding: 32rpx;
  border: 1px solid rgba(0,0,0,0.05);
  transition: all 0.25s ease;
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

.visibility-tag {
  font-size: 20rpx;
  color: #4a7c62;
  background-color: rgba(74, 124, 98, 0.1);
  padding: 4rpx 12rpx;
  border-radius: 4rpx;
  font-weight: bold;
}

.diary-time {
  font-size: 24rpx;
  color: #a09d98;
}

.music-tag {
  font-size: 20rpx;
  color: var(--theme-primary);
  background-color: rgba(var(--theme-primary-rgb), 0.1);
  padding: 4rpx 12rpx;
  border-radius: 4rpx;
}

.diary-content {
  font-size: 28rpx;
  line-height: 1.6;
  color: var(--theme-text-primary);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  overflow: hidden;
}

.diary-content.has-images {
  -webkit-line-clamp: 2;
  margin-bottom: 16rpx;
}

.diary-images {
  display: flex;
  gap: 16rpx;
  position: relative;
}

.preview-img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.05);
}

.more-img-mask {
  position: absolute;
  right: 0;
  top: 0;
  width: 160rpx;
  height: 160rpx;
  background-color: rgba(0,0,0,0.5);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  font-weight: bold;
  border-radius: 12rpx;
}

.loading-state, .empty-state, .no-more {
  text-align: center;
  color: #a09d98;
  font-size: 24rpx;
  padding: 40rpx 0;
}
</style>
