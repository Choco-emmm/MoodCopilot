<template>
  <view class="summaries-page" :style="themeStyle">
    <GlobalUI />
    <view class="header">
      <text class="page-title">我的报告</text>
      <button class="create-btn" @click="showCreateModal = true">+ 生成新报告</button>
    </view>

    <scroll-view scroll-y class="summaries-list">
      <view v-if="loading && summaries.length === 0" class="loading-state">
        <text>加载中...</text>
      </view>
      <view v-else-if="summaries.length === 0" class="empty-state">
        <text>你还没有生成过任何报告</text>
      </view>
      <view v-else>
        <view 
          v-for="summary in summaries" 
          :key="summary.id" 
          class="summary-card"
          @click="openSummary(summary)"
        >
          <view class="col-info">
            <text class="col-name">{{ summary.title || `${summary.startDate} 至 ${summary.endDate} 报告` }}</text>
            <text class="col-desc">共 {{ summary.diaryCount }} 篇日记</text>
          </view>
          <text class="col-date">{{ formatDate(summary.createdAt) }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- Create Modal -->
    <view class="modal-overlay" v-if="showCreateModal" @click="showCreateModal = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">生成自定义报告</text>
        
        <view class="date-picker-row">
          <text class="label">开始日期：</text>
          <picker mode="date" :value="startDate" @change="e => startDate = e.detail.value">
            <view class="picker-value">{{ startDate || '请选择' }}</view>
          </picker>
        </view>
        
        <view class="date-picker-row">
          <text class="label">结束日期：</text>
          <picker mode="date" :value="endDate" @change="e => endDate = e.detail.value">
            <view class="picker-value">{{ endDate || '请选择' }}</view>
          </picker>
        </view>

        <view class="modal-actions">
          <button class="cancel-btn" @click="showCreateModal = false">取消</button>
          <button class="confirm-btn" :disabled="!startDate || !endDate" @click="createSummary">生成</button>
        </view>
      </view>
    </view>

    <!-- Summary Detail Modal -->
    <view class="modal-overlay summary-overlay" v-if="selectedSummary" @click="selectedSummary = null">
      <view class="summary-detail-content" @click.stop>
        <view class="modal-header">
          <text class="modal-title">{{ selectedSummary.title || '情绪报告' }}</text>
          <text class="close-btn" @click="selectedSummary = null">✕</text>
        </view>
        
        <scroll-view scroll-y class="summary-scroll">
          <view class="summary-section">
            <text class="section-title">📊 情绪概况</text>
            <text class="summary-text">主导情绪区：{{ selectedSummary.moodDominantQuadrant }}</text>
            <text class="summary-text">正向情绪占比：{{ selectedSummary.positiveRatioPercent }}%</text>
            <text class="summary-text">高能量情绪占比：{{ selectedSummary.highEnergyRatioPercent }}%</text>
          </view>
          
          <view class="summary-section" v-if="selectedSummary.aiSummary">
            <text class="section-title">✨ AI 总结</text>
            <text class="summary-text">{{ selectedSummary.aiSummary }}</text>
          </view>
          
          <view class="summary-section" v-if="selectedSummary.insights && selectedSummary.insights.length > 0">
            <text class="section-title">💡 洞察与发现</text>
            <view v-for="(insight, idx) in selectedSummary.insights" :key="idx" class="list-item">
              <text class="dot">•</text>
              <text class="summary-text">{{ insight }}</text>
            </view>
          </view>

          <view class="summary-section" v-if="selectedSummary.suggestions && selectedSummary.suggestions.length > 0">
            <text class="section-title">🌟 行动建议</text>
            <view v-for="(sug, idx) in selectedSummary.suggestions" :key="idx" class="list-item">
              <text class="dot">•</text>
              <text class="summary-text">{{ sug }}</text>
            </view>
          </view>
        </scroll-view>
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

const summaries = ref<any[]>([]);
const loading = ref(false);
const showCreateModal = ref(false);

const startDate = ref('');
const endDate = ref('');
const selectedSummary = ref<any>(null);

onLoad(() => {
  fetchSummaries();
  // Default to this week
  const today = new Date();
  const lastWeek = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000);
  endDate.value = today.toISOString().split('T')[0];
  startDate.value = lastWeek.toISOString().split('T')[0];
});

const formatDate = (isoString: string) => {
  if (!isoString) return '';
  return isoString.split('T')[0];
};

const fetchSummaries = async () => {
  loading.value = true;
  try {
    const res = await get('/api/summaries');
    if (res.code === 200) {
      summaries.value = res.data || [];
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const createSummary = async () => {
  if (!startDate.value || !endDate.value) return;
  uni.showLoading({ title: '正在生成报告...' });
  try {
    const res = await post('/api/summaries', {
      startDate: startDate.value,
      endDate: endDate.value
    });
    if (res.code === 200) {
      uni.showToast({ title: '生成成功' });
      showCreateModal.value = false;
      fetchSummaries();
    } else {
      uni.showToast({ title: res.message || '生成失败', icon: 'none' });
    }
  } catch (e) {
    uni.showToast({ title: '生成失败', icon: 'none' });
  } finally {
    uni.hideLoading();
  }
};

const openSummary = (summary: any) => {
  selectedSummary.value = summary;
};

onShow(() => {
  syncNavigationBarColor();
});
</script>

<style scoped>
.summaries-page {
  min-height: 100vh;
  background-color: var(--theme-bg);
  padding: 32rpx;
  padding-bottom: env(safe-area-inset-bottom);
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
  font-size: 26rpx;
  background-color: var(--theme-primary);
  color: #fff;
  border-radius: 4rpx;
  padding: 0 32rpx;
  height: 60rpx;
  line-height: 60rpx;
  margin: 0;
}
.create-btn::after {
  display: none;
}

.loading-state, .empty-state {
  text-align: center;
  padding: 100rpx 0;
  color: var(--theme-text-placeholder);
  font-size: 28rpx;
}

.summaries-list {
  flex: 1;
}

.summary-card {
  background-color: var(--theme-surface);
  border-radius: 4rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: 1px solid rgba(0,0,0,0.05);
}

.col-info {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.col-name {
  font-size: 32rpx;
  color: var(--theme-text-primary);
  font-weight: 500;
}

.col-desc {
  font-size: 24rpx;
  color: #7d7870;
}

.col-date {
  font-size: 24rpx;
  color: var(--theme-text-placeholder);
}

/* Modal styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  width: 600rpx;
  background-color: var(--theme-bg);
  border-radius: 4rpx;
  padding: 40rpx;
}

.modal-title {
  font-size: 36rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
  margin-bottom: 32rpx;
  display: block;
  text-align: center;
}

.date-picker-row {
  display: flex;
  align-items: center;
  margin-bottom: 24rpx;
}

.date-picker-row .label {
  width: 160rpx;
  font-size: 28rpx;
  color: var(--theme-text-secondary);
}

.picker-value {
  flex: 1;
  background: #f5f5f5;
  padding: 16rpx 24rpx;
  border-radius: 4rpx;
  font-size: 28rpx;
  color: var(--theme-text-primary);
}

.modal-actions {
  display: flex;
  justify-content: space-between;
  gap: 24rpx;
  margin-top: 48rpx;
}

.cancel-btn, .confirm-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 4rpx;
  font-size: 30rpx;
}

.cancel-btn {
  background-color: #f5f5f5;
  color: var(--theme-text-secondary);
}

.confirm-btn {
  background-color: var(--theme-primary);
  color: #fff;
}
.confirm-btn[disabled] {
  opacity: 0.5;
}

/* Summary Detail Styles */
.summary-detail-content {
  width: 85vw;
  height: 80vh;
  background-color: #fcfbf9; /* newspaper tint */
  border-radius: 4rpx;
  padding: 40rpx;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 2px solid var(--theme-text-primary);
  padding-bottom: 20rpx;
  margin-bottom: 24rpx;
}

.close-btn {
  font-size: 40rpx;
  color: var(--theme-text-placeholder);
  padding: 10rpx;
}

.summary-scroll {
  flex: 1;
  overflow-y: auto;
}

.summary-section {
  margin-bottom: 40rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
  margin-bottom: 16rpx;
  display: block;
  font-family: "Noto Serif SC", serif;
  border-bottom: 1px solid rgba(0,0,0,0.1);
  padding-bottom: 8rpx;
}

.summary-text {
  font-size: 28rpx;
  color: #444;
  line-height: 1.6;
  display: block;
}

.list-item {
  display: flex;
  margin-bottom: 12rpx;
  align-items: flex-start;
}

.dot {
  margin-right: 12rpx;
  color: var(--theme-primary);
  font-weight: bold;
}
</style>
