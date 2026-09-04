<template>
  <view class="growth-page" :style="globalThemeStyle">
    <GlobalUI />
    <view class="header">
      <view class="level-badge">Lv.{{ status?.level || 1 }}</view>
      <text class="page-title">成长中心</text>
    </view>

    <!-- EXP Card -->
    <view class="card exp-card">
      <view class="exp-info">
        <text class="current-exp">{{ status?.exp || 0 }}</text>
        <text class="exp-label">当前经验值</text>
      </view>
      <view class="progress-bar">
        <view class="progress-fill" :style="{ width: expPercentage + '%' }"></view>
      </view>
      <view class="exp-footer">
        <text>升级还需 {{ Math.max(0, (status?.expToNextLevel || 0) - (status?.exp || 0)) }} EXP</text>
        <text>{{ status?.expToNextLevel || 0 }} EXP</text>
      </view>
    </view>

    <!-- Daily Tasks -->
    <view class="section">
      <text class="section-title">每日任务</text>
      <view class="task-list">
        <view class="task-item" v-for="task in filteredTasks" :key="task.field">
          <view class="task-info">
            <text class="task-name">{{ task.label }}</text>
            <text class="task-desc">每次 +{{ task.expPerAction }} EXP，每日上限 {{ task.max }} 次</text>
          </view>
          <view class="task-action">
            <view class="task-progress">{{ task.current }} / {{ task.max }}</view>
            <button class="action-btn" :class="{ completed: task.current >= task.max }" @click="handleTaskAction(task)">
              {{ task.current >= task.max ? '已完成' : '去完成' }}
            </button>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { get, post } from '@/utils/request';
import { onLoad } from '@dcloudio/uni-app';
import GlobalUI from '@/components/GlobalUI.vue';


const status = ref<any>(null);
const tasks = ref<any[]>([]);

const filteredTasks = computed(() => {
  return tasks.value.filter(t => !['comment', 'like'].includes(t.field));
});

const expPercentage = computed(() => {
  if (!status.value) return 0;
  const { exp, expToNextLevel } = status.value;
  if (!expToNextLevel) return 100;
  return Math.min(100, Math.max(0, (exp / expToNextLevel) * 100));
});

onLoad(() => {
  fetchGrowthData();
});

const fetchGrowthData = async () => {
  try {
    const [statusRes, progressRes] = await Promise.all([
      get('/api/growth/status'),
      get('/api/growth/progress')
    ]);
    
    if (statusRes.code === 200) {
      status.value = statusRes.data;
    }
    if (progressRes.code === 200) {
      tasks.value = progressRes.data;
    }
  } catch (e) {
    console.error('Failed to fetch growth data', e);
  }
};

const handleTaskAction = async (task: any) => {
  if (task.current >= task.max) return;
  
  if (task.field === 'checkin') {
    try {
      const res = await post('/api/growth/checkin');
      if (res.code === 200 && res.data.exp > 0) {
        uni.showToast({ title: `签到成功，经验 +${res.data.exp}`, icon: 'none' });
        fetchGrowthData();
      } else {
        uni.showToast({ title: res.message || '今日已签到', icon: 'none' });
      }
    } catch (e) {
      uni.showToast({ title: '签到失败', icon: 'none' });
    }
  } else if (task.field === 'diary') {
    uni.switchTab({ url: '/pages/index/index' });
  } else if (task.field === 'chat') {
    uni.switchTab({ url: '/pages/chat/chat' });
  }
};

</script>

<style scoped>
.growth-page {
  min-height: 100vh;
  background-color: var(--theme-bg);
  padding: 32rpx;
}

.header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.level-badge {
  background: linear-gradient(135deg, #FFD700, #FDB931);
  color: #fff;
  font-size: 28rpx;
  font-weight: bold;
  padding: 4rpx 16rpx;
  border-radius: 4rpx;
}

.page-title {
  font-size: 40rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
}

.card {
  background-color: var(--theme-surface);
  border-radius: 4rpx;
  padding: 32rpx;
  margin-bottom: 32rpx;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.03);
}

.exp-info {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.current-exp {
  font-size: 64rpx;
  font-weight: bold;
  color: var(--theme-primary);
}

.exp-label {
  font-size: 28rpx;
  color: #7d7870;
}

.progress-bar {
  height: 16rpx;
  background-color: rgba(var(--theme-primary-rgb), 0.1);
  border-radius: 4rpx;
  overflow: hidden;
  margin-bottom: 16rpx;
}

.progress-fill {
  height: 100%;
  background-color: var(--theme-primary);
  transition: width 0.3s ease;
}

.exp-footer {
  display: flex;
  justify-content: space-between;
  font-size: 24rpx;
  color: #a09d98;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: 24rpx;
  display: block;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.task-item {
  background-color: var(--theme-surface);
  border-radius: 4rpx;
  padding: 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.02);
}

.task-info {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.task-name {
  font-size: 30rpx;
  font-weight: 500;
  color: var(--theme-text-primary);
}

.task-desc {
  font-size: 24rpx;
  color: #7d7870;
}

.task-action {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.task-progress {
  font-size: 26rpx;
  color: #a09d98;
}

.action-btn {
  margin: 0;
  font-size: 24rpx;
  background-color: var(--theme-primary);
  color: var(--theme-surface);
  border-radius: 4rpx;
  padding: 0 24rpx;
  height: 56rpx;
  line-height: 56rpx;
}

.action-btn.completed {
  background-color: #e0e0e0;
  color: var(--theme-text-placeholder);
}
</style>
