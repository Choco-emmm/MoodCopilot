<template>
  <view class="feedback-page" :style="themeStyle">
    <GlobalUI />
    <view class="header">
      <text class="page-title">意见反馈</text>
      <text class="page-desc">有什么想对 MoodCopilot 说的，或者有什么需要改进的地方，都在这里告诉我们吧！</text>
    </view>

    <view class="form-container">
      <textarea
        class="feedback-input"
        v-model="content"
        placeholder="写下你的想法、建议或是遇到的问题..."
        :maxlength="1000"
        cursor-spacing="20"
      ></textarea>
      <view class="word-count">{{ content.length }}/1000</view>
    </view>

    <button 
      class="submit-btn" 
      :class="{ disabled: !content.trim() || isSubmitting }"
      @click="submitFeedback"
    >
      {{ isSubmitting ? '提交中...' : '提交建议' }}
    </button>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { post } from '@/utils/request';
import GlobalUI from '@/components/GlobalUI.vue';
import { themeStyle } from '@/stores/theme';

const content = ref('');
const isSubmitting = ref(false);

const submitFeedback = async () => {
  if (!content.value.trim() || isSubmitting.value) return;
  isSubmitting.value = true;
  
  try {
    const res = await post('/api/suggestions', { content: content.value });
    if (res.code === 200) {
      uni.showToast({ title: '提交成功，感谢反馈！', icon: 'success' });
      setTimeout(() => {
        uni.navigateBack();
      }, 1500);
    } else {
      uni.showToast({ title: res.message || '提交失败', icon: 'none' });
    }
  } catch (e) {
    uni.showToast({ title: '提交失败，请重试', icon: 'none' });
  } finally {
    isSubmitting.value = false;
  }
};
</script>

<style scoped>
.feedback-page {
  min-height: 100vh;
  background-color: var(--theme-bg);
  padding: 40rpx;
  box-sizing: border-box;
}

.header {
  margin-top: 20rpx;
  margin-bottom: 60rpx;
}

.page-title {
  font-size: 48rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
  display: block;
  margin-bottom: 16rpx;
}

.page-desc {
  font-size: 28rpx;
  color: #7d7870;
  line-height: 1.5;
}

.form-container {
  background-color: var(--theme-surface);
  border-radius: 4rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.02);
  margin-bottom: 48rpx;
}

.feedback-input {
  width: 100%;
  height: 300rpx;
  font-size: 30rpx;
  line-height: 1.6;
  color: var(--theme-text-primary);
}

.word-count {
  text-align: right;
  font-size: 24rpx;
  color: #a09d98;
  margin-top: 16rpx;
}

.submit-btn {
  background-color: var(--theme-primary);
  color: var(--theme-surface);
  border-radius: 4rpx;
  font-size: 32rpx;
  font-weight: bold;
  padding: 24rpx 0;
  line-height: 1.5;
  transition: opacity 0.2s;
}

.submit-btn.disabled {
  opacity: 0.5;
}
</style>
