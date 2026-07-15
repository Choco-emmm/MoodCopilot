<template>
  <view class="global-ui">
    <!-- Top Right Popups (Memory / Graph) -->
    <view class="popups-container">
      <view 
        v-for="popup in popups" 
        :key="popup.id" 
        class="popup-card"
        @click="removePopup(popup.id)"
      >
        <image :src="popup.icon" mode="aspectFit" class="popup-icon" />
        <view class="popup-content">
          <text class="popup-title">{{ popup.title }}</text>
          <text v-if="popup.message" class="popup-message">{{ popup.message }}</text>
        </view>
        <view class="popup-close">×</view>
      </view>
    </view>

    <!-- Center Modal (Analysis Complete) -->
    <view v-if="currentModal" class="modal-overlay">
      <view class="modal-box">
        <view class="modal-icon-wrapper">
          <image :src="svgSparkleUrl" mode="aspectFit" class="modal-icon" />
        </view>
        <text class="modal-title">{{ currentModal.title }}</text>
        <text class="modal-message">{{ currentModal.message }}</text>
        
        <view class="modal-actions">
          <button class="btn-cancel" @click="closeModal">关闭</button>
          <button class="btn-primary" @click="viewDetails">查看详情</button>
        </view>
      </view>
    </view>

    <!-- Custom Tab Bar -->
    <CustomTabBar v-if="tabIndex !== undefined" :current="tabIndex" />
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import { popups, currentModal, removePopup, closeModal } from '@/stores/globalUI';
import { currentTheme } from '@/stores/theme';
import CustomTabBar from './CustomTabBar.vue';

const props = defineProps<{
  tabIndex?: number
}>();

const svgSparkleUrl = computed(() => {
  const primary = encodeURIComponent(currentTheme.value.primary);
  return `data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='${primary}'%3E%3Cpath d='M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z'/%3E%3C/svg%3E`;
});

const updateNav = () => {
  try {
    uni.setNavigationBarColor({
      frontColor: currentTheme.value.dark ? '#ffffff' : '#000000',
      backgroundColor: currentTheme.value.bg,
      animation: { duration: 200, timingFunc: 'easeInOut' }
    });
  } catch (e) {
    // Ignore on unsupported platforms
  }
};

onMounted(() => {
  updateNav();
  if (props.tabIndex !== undefined) {
    uni.hideTabBar({
      animation: false,
      fail: () => {}
    });
  }
});

watch(currentTheme, () => {
  updateNav();
});

const viewDetails = () => {
  if (currentModal.value?.diaryId) {
    uni.navigateTo({ url: `/pages/detail/detail?id=${currentModal.value.diaryId}` });
  } else {
    // If no diaryId, maybe navigate to analysis page
    uni.switchTab({ url: `/pages/analysis/analysis` });
  }
  closeModal();
};
</script>

<style scoped>
/* Top Right Popups */
.popups-container {
  position: fixed;
  top: 100rpx; /* Leave space for status bar / nav bar */
  right: 32rpx;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  pointer-events: none; /* Let clicks pass through empty space */
}

.popup-card {
  pointer-events: auto; /* Re-enable clicks for the card */
  background: rgba(255, 253, 248, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 16rpx;
  padding: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(32, 32, 29, 0.08);
  border: 1px solid rgba(var(--theme-primary-rgb), 0.15);
  display: flex;
  align-items: center;
  width: 460rpx;
  animation: slideInRight 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes slideInRight {
  from { transform: translateX(100%); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}

.popup-icon {
  width: 48rpx;
  height: 48rpx;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.popup-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.popup-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #20201d;
  margin-bottom: 4rpx;
}

.popup-message {
  font-size: 24rpx;
  color: #7d7870;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.popup-close {
  color: #a09d96;
  font-size: 32rpx;
  padding: 0 8rpx;
  margin-left: 12rpx;
}

/* Center Modal */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(32, 32, 29, 0.4);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-box {
  background: var(--theme-surface);
  border-radius: 24rpx;
  width: 600rpx;
  padding: 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  box-shadow: 0 16rpx 48rpx rgba(32, 32, 29, 0.1);
  animation: scaleUp 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes scaleUp {
  from { transform: scale(0.95); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

.modal-icon-wrapper {
  width: 96rpx;
  height: 96rpx;
  background: rgba(var(--theme-primary-rgb), 0.1);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
}

.modal-icon {
  width: 56rpx;
  height: 56rpx;
}

.modal-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #20201d;
  margin-bottom: 16rpx;
}

.modal-message {
  font-size: 28rpx;
  color: #7d7870;
  line-height: 1.6;
  margin-bottom: 48rpx;
}

.modal-actions {
  display: flex;
  width: 100%;
  gap: 24rpx;
}

.btn-cancel {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 40rpx;
  background: #f1efeb;
  color: #4a4a46;
  font-size: 28rpx;
  border: none;
}
.btn-cancel::after { display: none; }

.btn-primary {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 40rpx;
  background: var(--theme-primary);
  color: #ffffff;
  font-size: 28rpx;
  border: none;
}
.btn-primary::after { display: none; }
</style>

