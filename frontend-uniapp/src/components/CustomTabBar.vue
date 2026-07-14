<template>
  <view class="custom-tab-bar" :style="themeStyle">
    <view class="tab-bar-border"></view>
    <view class="tab-list">
      <view 
        class="tab-item" 
        v-for="(item, index) in list" 
        :key="index"
        @click="switchTab(item.pagePath, index)"
      >
        <view class="icon-container">
          <image 
            v-if="current !== index"
            class="tab-icon" 
            :src="item.iconPath" 
            mode="aspectFit"
          />
          <!-- Inline SVG for active state to use theme color -->
          <view v-else class="tab-icon active-icon" v-html="getActiveSvg(item.svg, currentTheme.primary)"></view>
        </view>
        <text 
          class="tab-text" 
          :style="{ color: current === index ? currentTheme.primary : '#999999', fontWeight: current === index ? 'bold' : 'normal' }"
        >
          {{ item.text }}
        </text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { currentTheme, themeStyle } from '@/stores/theme';

const props = defineProps<{
  current: number
}>();

const getActiveSvg = (svgRaw: string, color: string) => {
  return svgRaw.replace(/currentColor/g, color);
};

const list = [
  {
    pagePath: '/pages/index/index',
    text: '日记',
    iconPath: '/static/tabs/diary.png',
    // Book icon
    svg: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" width="100%" height="100%"><path d="M21 4H7a2 2 0 00-2 2v12a2 2 0 002 2h14a1 1 0 001-1V5a1 1 0 00-1-1zm-1 13H7a1 1 0 010-2h13v2zm0-4H7V6h13v7z"/><path d="M3 19V6a1 1 0 00-2 0v13a3 3 0 003 3h16a1 1 0 000-2H4a1 1 0 01-1-1z"/></svg>`
  },
  {
    pagePath: '/pages/analysis/analysis',
    text: '洞察',
    iconPath: '/static/tabs/analysis.png',
    // Bulb icon
    svg: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" width="100%" height="100%"><path d="M12 2C8.13 2 5 5.13 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.87-3.13-7-7-7zm2.5 11.7l-.5.35V16h-4v-1.95l-.5-.35C8.07 12.68 7 10.94 7 9c0-2.76 2.24-5 5-5s5 2.24 5 5c0 1.94-1.07 3.68-2.5 4.7z"/><path d="M9 19h6v2H9z"/></svg>`
  },
  {
    pagePath: '/pages/chat/chat',
    text: '陪伴',
    iconPath: '/static/tabs/chat.png',
    // Chat icon
    svg: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" width="100%" height="100%"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H5.17L4 17.17V4h16v12z"/><path d="M7 9h10v2H7zm0-3h10v2H7zm0 6h7v2H7z"/></svg>`
  },
  {
    pagePath: '/pages/profile/profile',
    text: '我的',
    iconPath: '/static/tabs/profile.png',
    // User icon
    svg: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" width="100%" height="100%"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>`
  }
];

const switchTab = (url: string, index: number) => {
  if (props.current === index) return;
  uni.switchTab({ url });
};
</script>

<style scoped>
.custom-tab-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 100rpx;
  padding-bottom: env(safe-area-inset-bottom);
  box-sizing: content-box;
  background: var(--theme-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  display: flex;
  flex-direction: column;
  z-index: 90;
}

.tab-bar-border {
  height: 1px;
  background: rgba(0, 0, 0, 0.05);
  width: 100%;
}

.tab-list {
  display: flex;
  flex-direction: row;
  justify-content: space-around;
  align-items: center;
  height: 100rpx;
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  height: 100%;
}

.icon-container {
  width: 48rpx;
  height: 48rpx;
  margin-bottom: 6rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tab-icon {
  width: 100%;
  height: 100%;
}

.active-icon {
  animation: bounceIn 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.tab-text {
  font-size: 20rpx;
  line-height: 1;
  transition: color 0.2s;
}

@keyframes bounceIn {
  0% { transform: scale(0.8); }
  50% { transform: scale(1.1); }
  100% { transform: scale(1); }
}
</style>
