<template>
  <view class="detail-page" :style="themeStyle">
    <GlobalUI />
    <view v-if="loading" class="loading-state">
      <text>加载中...</text>
    </view>
    <view v-else-if="diary" class="detail-container fade-in">
      <!-- Diary Header & Content -->
      <view class="diary-paper">
        <view class="diary-header">
          <view class="header-left">
            <text class="diary-date">{{ formatDate(diary.createdAt) }}</text>
            <view v-if="diary.analysis && diary.analysis.moodLabel" class="diary-mood">
              <text class="diary-mood-text">{{ diary.analysis.moodLabel }}</text>
            </view>
          </view>
          <view class="diary-actions" v-if="currentUser && currentUser.id === diary.authorUserId">
            <view class="action-btn edit-btn hover-scale" @click="editDiary">
              <text>编辑</text>
            </view>
            <view class="action-btn delete-btn hover-scale" @click="deleteDiary">
              <text>删除</text>
            </view>
          </view>
        </view>
        <text class="diary-text">{{ diary.content }}</text>
        
        <view v-if="diary.images && diary.images.length > 0" class="diary-images">
          <image 
            v-for="(img, idx) in diary.images" 
            :key="idx" 
            :src="img" 
            mode="widthFix" 
            class="diary-img-full preview-img"
            @click.stop="previewImage(img, diary.images)"
          />
        </view>

        <!-- Tags and Collections -->
        <view class="diary-footer-tags" v-if="parentCollections.length > 0">
          <text class="tag-label">收录于:</text>
          <text 
            v-for="col in parentCollections" 
            :key="col.id" 
            class="collection-tag hover-scale" 
            @click="goToCollection(col.id)"
          >
            📁 {{ col.name }}
          </text>
        </view>
      </view>

      <!-- AI Analysis Section -->
      <view class="ai-section" v-if="diary.analysisStatus === 'analyzing'">
        <view class="ai-card analyzing">
          <text class="ai-title">🤖 AI 正在深入分析你的情绪...</text>
          <text class="ai-desc">通常需?10-30 秒，稍后回来查看吧。</text>
        </view>
      </view>
      <view class="ai-section" v-else-if="diary.analysis && diary.analysis.summary">
        <view class="ai-card letter-card">
          <view class="letter-header">
            <text class="letter-title">来自 MoodCopilot 的信</text>
          </view>
          
          <view class="tags-container" v-if="topicLabels.length > 0">
            <text v-for="tag in topicLabels" :key="tag" class="topic-tag"># {{ tag }}</text>
          </view>

          <view class="intensity-bar" v-if="diary.analysis.moodIntensity > 0">
            <text class="intensity-label">情绪强度</text>
            <view class="bar-bg">
              <view class="bar-fill" :style="{ width: (diary.analysis.moodIntensity * 10) + '%' }"></view>
            </view>
            <text class="intensity-val">{{ diary.analysis.moodIntensity }}/10</text>
          </view>

          <view class="letter-body">
            <text class="letter-text">{{ diary.analysis.feedback || diary.analysis.summary }}</text>
          </view>
        </view>
      </view>
      <view class="ai-section" v-else-if="diary.analysisStatus === 'skipped_user' || diary.analysisStatus === 'skipped_quota'">
        <view class="ai-card skipped">
          <text class="ai-desc">本篇日记未生成✨ AI 深度分析。</text>
        </view>
      </view>
    </view>
    <!-- Bottom Action Bar -->
    <view v-if="diary" class="bottom-action-bar fade-in">
      <view class="action-item hover-scale" @click="quoteToChat">
        <text class="action-icon">🔮</text>
        <text class="action-text">跟 AI 聊这篇</text>
      </view>
      <view class="action-item hover-scale" @click="showCollectionModal = true">
        <text class="action-icon">📁</text>
        <text class="action-text">加入合集</text>
      </view>
    </view>

    <!-- Collection Modal -->
    <view class="modal-overlay" v-if="showCollectionModal" @click="showCollectionModal = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">加入合集</text>
        
        <scroll-view scroll-y class="collection-list">
          <view v-if="myCollections.length === 0" class="empty-state">
            暂无合集，请先在“我的”页面创建
          </view>
          <view 
            v-for="col in myCollections" 
            :key="col.id" 
            class="collection-item"
            @click="addToCollection(col.id)"
          >
            <text class="col-name">{{ col.name }}</text>
            <text class="add-btn">加入</text>
          </view>
        </scroll-view>
        
        <view class="modal-actions">
          <button class="cancel-btn" @click="showCollectionModal = false">取消</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import GlobalUI from '@/components/GlobalUI.vue';
import { themeStyle } from '@/stores/theme';
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { get, post, request } from '@/utils/request';
import { currentUser, fetchCurrentUser } from '@/stores/user';

const loading = ref(true);
const diary = ref<any>(null);

const showCollectionModal = ref(false);
const myCollections = ref<any[]>([]);
const parentCollections = ref<any[]>([]);

onLoad(async (options: any) => {
  if (!currentUser.value) {
    await fetchCurrentUser();
  }
  if (options.id) {
    fetchDiary(options.id);
    fetchParentCollections(options.id);
  }
  fetchMyCollections();
});

const fetchParentCollections = async (diaryId: number) => {
  try {
    const res = await get(`/api/collections/by-diary/${diaryId}`);
    if (res.code === 200) {
      parentCollections.value = res.data || [];
    }
  } catch (e) {
    console.error('Failed to fetch parent collections', e);
  }
};

const fetchMyCollections = async () => {
  try {
    const res = await get('/api/collections/mine?page=1&size=50');
    if (res.code === 200) {
      myCollections.value = res.data.records || res.data.items || res.data.content || [];
    }
  } catch (e) {
    console.error('Failed to fetch collections', e);
  }
};

const addToCollection = async (collectionId: number) => {
  try {
    const res = await post(`/api/collections/${collectionId}/diaries`, {
      diaryIds: [diary.value.id]
    });
    if (res.code === 200) {
      uni.showToast({ title: '已加入合集', icon: 'success' });
      showCollectionModal.value = false;
      fetchParentCollections(diary.value.id);
    } else {
      uni.showToast({ title: res.message || '操作失败', icon: 'none' });
    }
  } catch (e) {
    uni.showToast({ title: '操作失败', icon: 'none' });
  }
};

const fetchDiary = async (id: string | number) => {
  loading.value = true;
  try {
    const res = await get(`/api/diaries/${id}`);
    if (res.code === 200) {
      diary.value = res.data;
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const editDiary = () => {
  // Navigate to write page in edit mode
  uni.navigateTo({ url: `/pages/write/write?id=${diary.value.id}&mode=edit` });
};

const deleteDiary = () => {
  uni.showModal({
    title: '删除日记',
    content: '确定要删除这篇日记吗？此操作不可恢复。',
    success: async (res) => {
      if (res.confirm) {
        try {
          const result = await request('DELETE', `/api/diaries/${diary.value.id}`);
          if (result.code === 200) {
            uni.showToast({ title: '已删除', icon: 'success' });
            setTimeout(() => {
              uni.switchTab({ url: '/pages/index/index' });
            }, 1000);
          }
        } catch (e) {
          uni.showToast({ title: '删除失败', icon: 'none' });
        }
      }
    }
  });
};

const topicLabels = computed(() => {
  if (!diary.value || !diary.value.analysis || !diary.value.analysis.topicLabelsJson) return [];
  try {
    return JSON.parse(diary.value.analysis.topicLabelsJson);
  } catch (e) {
    return [];
  }
});

const formatDate = (isoString: string) => {
  if (!isoString) return '';
  const date = new Date(isoString);
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
};

const quoteToChat = () => {
  if (diary.value && diary.value.content) {
    const textToQuote = `关于我的这篇日记（${formatDate(diary.value.createdAt)}）：\n${diary.value.content}`;
    uni.setStorageSync('pendingQuote', textToQuote);
    uni.switchTab({ url: '/pages/chat/chat' });
  }
};

const previewImage = (current: string, urls: string[]) => {
  uni.previewImage({ current, urls });
};

const goToCollection = (colId: number) => {
  uni.navigateTo({ url: `/pages/collections/collections?id=${colId}` });
};
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  background-color: var(--theme-bg);
}

.detail-container {
  padding: 16rpx;
  padding-bottom: 120rpx;
}

.loading-state {
  text-align: center;
  padding: 100rpx;
  color: #7d7870;
}

.diary-paper {
  background-color: var(--theme-surface);
  border-radius: 4rpx;
  padding: 40rpx 32rpx;
  min-height: 50vh;
  box-shadow: 0 12rpx 36rpx rgba(0, 0, 0, 0.03);
  position: relative;
  border: 1px solid rgba(0,0,0,0.02);
}

.diary-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32rpx;
  border-bottom: 1px dashed rgba(var(--theme-primary-rgb), 0.2);
  padding-bottom: 24rpx;
}

.diary-date {
  font-size: 28rpx;
  color: #7d7870;
}

.diary-mood {
  background-color: rgba(var(--theme-primary-rgb), 0.1);
  padding: 8rpx 24rpx;
  border-radius: 9999rpx;
}

.diary-mood-text {
  font-size: 26rpx;
  color: var(--theme-primary);
  font-weight: 500;
}

.diary-text {
  font-size: 32rpx;
  color: var(--theme-text-primary);
  line-height: 1.8;
  margin-bottom: 32rpx;
  display: block;
}

.diary-images {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.diary-footer-tags {
  margin-top: 32rpx;
  padding-top: 32rpx;
  border-top: 1px dashed rgba(0,0,0,0.05);
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  align-items: center;
}

.tag-label {
  font-size: 24rpx;
  color: var(--theme-text-placeholder);
}

.collection-tag {
  background-color: rgba(var(--theme-primary-rgb), 0.05);
  color: var(--theme-primary);
  padding: 6rpx 20rpx;
  border-radius: 4rpx;
  font-size: 24rpx;
  border: 1px solid rgba(var(--theme-primary-rgb), 0.1);
}

.preview-img {
  width: 100%;
  border-radius: 4rpx;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.04);
}

.ai-section {
  margin-top: 48rpx;
}

.ai-card {
  border-radius: 4rpx;
  padding: 40rpx 32rpx;
}

.analyzing, .skipped {
  background-color: rgba(var(--theme-primary-rgb), 0.05);
  border: 1px dashed rgba(var(--theme-primary-rgb), 0.2);
  text-align: center;
}

.ai-title {
  font-size: 32rpx;
  color: var(--theme-primary);
  font-weight: 600;
  display: block;
  margin-bottom: 16rpx;
}

.ai-desc {
  font-size: 28rpx;
  color: #7d7870;
}

.letter-card {
  background-color: #f0ebd8; /* Envelope color */
  border: 1px solid rgba(32, 32, 29, 0.1);
  box-shadow: inset 0 0 40rpx rgba(255, 255, 255, 0.5), 0 8rpx 24rpx rgba(32, 32, 29, 0.05);
  position: relative;
}

.letter-header {
  border-bottom: 2px solid rgba(var(--theme-primary-rgb), 0.2);
  padding-bottom: 24rpx;
  margin-bottom: 32rpx;
  text-align: center;
}

.letter-title {
  font-family: "Noto Serif SC", "Songti SC", "STSong", "KaiTi", serif;
  font-size: 36rpx;
  color: var(--theme-text-primary);
  font-weight: 700;
  letter-spacing: 4rpx;
}

.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.topic-tag {
  font-size: 24rpx;
  color: var(--theme-surface);
  background-color: var(--theme-primary);
  padding: 6rpx 20rpx;
  border-radius: 9999rpx;
}

.intensity-bar {
  display: flex;
  align-items: center;
  margin-bottom: 32rpx;
}

.intensity-label {
  font-size: 26rpx;
  color: #7d7870;
  margin-right: 16rpx;
}

.bar-bg {
  flex: 1;
  height: 12rpx;
  background-color: rgba(32, 32, 29, 0.1);
  border-radius: 6rpx;
  margin-right: 16rpx;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background-color: var(--theme-primary);
  border-radius: 6rpx;
}

.intensity-val {
  font-size: 26rpx;
  color: var(--theme-primary);
  font-weight: 600;
}

.letter-body {
  font-family: "Noto Serif SC", "Songti SC", "STSong", "KaiTi", serif;
  font-size: 32rpx;
  color: var(--theme-text-primary);
  line-height: 2;
}

.letter-text {
  display: block;
}
.header-left {
  display: flex;
  align-items: center;
}

.diary-actions {
  display: flex;
  gap: 16rpx;
}

.action-btn {
  padding: 16rpx 40rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 600;
  border: 2rpx solid transparent;
  transition: all 0.2s ease;
}

.edit-btn {
  background-color: rgba(var(--theme-primary-rgb), 0.08);
  color: var(--theme-primary);
  border-color: rgba(var(--theme-primary-rgb), 0.1);
}

.delete-btn {
  background-color: rgba(229, 83, 83, 0.08);
  color: #e55353;
  border-color: rgba(229, 83, 83, 0.1);
}

.bottom-action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 100rpx;
  background-color: var(--theme-surface);
  box-shadow: 0 -4rpx 16rpx rgba(32, 32, 29, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  padding-bottom: env(safe-area-inset-bottom);
}

.action-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 16rpx 32rpx;
  border-radius: 999rpx;
  background-color: rgba(var(--theme-primary-rgb), 0.05);
}

.action-icon {
  font-size: 36rpx;
}

.action-text {
  font-size: 28rpx;
  color: var(--theme-primary);
  font-weight: 500;
}

/* Modal Styles */
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
  border-radius: 24rpx;
  padding: 40rpx;
}

.modal-title {
  font-size: 36rpx;
  font-weight: bold;
  margin-bottom: 32rpx;
  display: block;
  text-align: center;
}

.collection-list {
  max-height: 400rpx;
  margin-bottom: 32rpx;
}

.collection-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1px solid rgba(0,0,0,0.05);
}

.col-name {
  font-size: 32rpx;
  color: var(--theme-text-primary);
}

.add-btn {
  font-size: 24rpx;
  color: var(--theme-primary);
  background-color: rgba(var(--theme-primary-rgb), 0.1);
  padding: 8rpx 24rpx;
  border-radius: 8rpx;
}

.modal-actions {
  display: flex;
  justify-content: center;
}

.cancel-btn {
  width: 100%;
  background-color: #f0f0f0;
  color: var(--theme-text-primary);
  border-radius: 999rpx;
  font-size: 28rpx;
}
</style>
