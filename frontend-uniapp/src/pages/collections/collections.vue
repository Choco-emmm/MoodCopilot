<template>
  <view class="collections-page" :style="themeStyle">
    <GlobalUI />
    <view class="header">
      <text class="page-title">我的合集</text>
      <button class="create-btn" @click="showCreateModal = true">+ 新建</button>
    </view>

    <scroll-view scroll-y class="collections-list" @scrolltolower="loadMore">
      <view v-if="loading && collections.length === 0" class="loading-state">
        <text>加载中...</text>
      </view>
      <view v-else-if="collections.length === 0" class="empty-state">
        <text>你还没有创建任何合集</text>
      </view>
      <view v-else>
        <view 
          v-for="col in collections" 
          :key="col.id" 
          class="collection-card"
          @click="openCollection(col.id)"
        >
          <view class="col-info">
            <text class="col-name">{{ col.name }}</text>
            <text class="col-desc" v-if="col.description">{{ col.description }}</text>
          </view>
          <text class="col-count">{{ col.diaryCount || 0 }} 篇</text>
        </view>
      </view>
    </scroll-view>

    <!-- Create Modal -->
    <view class="modal-overlay" v-if="showCreateModal" @click="showCreateModal = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">新建合集</text>
        <input class="modal-input" v-model="newColName" placeholder="合集名称" maxlength="20" />
        <textarea class="modal-textarea" v-model="newColDesc" placeholder="描述（可选）" maxlength="100" />
        <view class="modal-actions">
          <button class="cancel-btn" @click="showCreateModal = false">取消</button>
          <button class="confirm-btn" :disabled="!newColName.trim()" @click="createCollection">创建</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { get, post } from '@/utils/request';
import { onLoad, onShow } from '@dcloudio/uni-app';
import GlobalUI from '@/components/GlobalUI.vue';
import { themeStyle, syncNavigationBarColor } from '@/stores/theme';

const collections = ref<any[]>([]);
const loading = ref(false);
const page = ref(1);
const hasMore = ref(true);

const showCreateModal = ref(false);
const newColName = ref('');
const newColDesc = ref('');

onLoad(() => {
  fetchCollections();
});

const fetchCollections = async (isLoadMore = false) => {
  if (loading.value || (!hasMore.value && isLoadMore)) return;
  loading.value = true;
  if (!isLoadMore) {
    page.value = 1;
    hasMore.value = true;
  }
  
  try {
    const res = await get(`/api/collections/mine?page=${page.value}&size=10`);
    if (res.code === 200) {
      const items = res.data.records || res.data.items || res.data.content || [];
      if (isLoadMore) {
        collections.value = [...collections.value, ...items];
      } else {
        collections.value = items;
      }
      hasMore.value = items.length === 10;
      if (hasMore.value) page.value++;
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const loadMore = () => fetchCollections(true);

const createCollection = async () => {
  if (!newColName.value.trim()) return;
  try {
    const res = await post('/api/collections', {
      name: newColName.value.trim(),
      description: newColDesc.value.trim()
    });
    if (res.code === 200) {
      uni.showToast({ title: '创建成功' });
      showCreateModal.value = false;
      newColName.value = '';
      newColDesc.value = '';
      fetchCollections();
    }
  } catch (e) {
    uni.showToast({ title: '创建失败', icon: 'none' });
  }
};

const openCollection = (id: number) => {
  uni.showToast({ title: '即将支持查看合集内容', icon: 'none' });
};

onShow(() => {
  syncNavigationBarColor();
});
</script>

<style scoped>
.collections-page {
  min-height: 100vh;
  background-color: var(--theme-bg);
  padding: 32rpx;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32rpx;
}

.page-title {
  font-size: 40rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
}

.create-btn {
  margin: 0;
  font-size: 28rpx;
  background-color: var(--theme-primary);
  color: var(--theme-surface);
  border-radius: 999rpx;
  padding: 0 32rpx;
}

.collection-card {
  background-color: var(--theme-surface);
  border-radius: 16rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.03);
}

.col-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.col-name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: 8rpx;
}

.col-desc {
  font-size: 24rpx;
  color: #7d7870;
}

.col-count {
  font-size: 28rpx;
  color: var(--theme-primary);
  font-weight: bold;
  background-color: rgba(var(--theme-primary-rgb), 0.1);
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
  margin-left: 16rpx;
}

.empty-state, .loading-state {
  text-align: center;
  padding: 100rpx;
  color: #7d7870;
}

.modal-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background-color: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
}

.modal-content {
  background-color: var(--theme-surface);
  width: 80%;
  border-radius: 4rpx;
  padding: 40rpx;
}

.modal-title {
  font-size: 36rpx;
  font-weight: bold;
  margin-bottom: 32rpx;
  display: block;
  text-align: center;
}

.modal-input {
  border: 1px solid #e0e0e0;
  padding: 16rpx 24rpx;
  border-radius: 4rpx;
  margin-bottom: 24rpx;
}

.modal-textarea {
  border: 1px solid #e0e0e0;
  padding: 16rpx 24rpx;
  border-radius: 4rpx;
  width: 100%;
  box-sizing: border-box;
  height: 160rpx;
  margin-bottom: 32rpx;
}

.modal-actions {
  display: flex;
  gap: 16rpx;
}

.cancel-btn, .confirm-btn {
  flex: 1;
  border-radius: 4rpx;
  font-size: 28rpx;
}

.cancel-btn {
  background-color: #f0f0f0;
  color: var(--theme-text-primary);
}

.confirm-btn {
  background-color: var(--theme-primary);
  color: var(--theme-surface);
}
</style>
