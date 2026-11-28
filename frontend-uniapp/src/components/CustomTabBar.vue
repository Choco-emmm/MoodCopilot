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
          <!-- Use CSS mask with original active PNGs to apply theme color -->
          <view 
            v-else
            class="tab-icon active-icon mask-icon" 
            :class="item.maskClass"
            :style="{ backgroundColor: 'var(--theme-primary)' }"
          ></view>
        </view>
        <text 
          class="tab-text" 
          :style="{ color: current === index ? 'var(--theme-primary)' : 'var(--theme-text-placeholder)', fontWeight: current === index ? 'bold' : 'normal' }"
        >
          {{ item.text }}
        </text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { getCurrentInstance, nextTick, onMounted, watch } from 'vue';
import { themeStyle } from '@/stores/theme';
import { registerSpotlightAnchorRect, welcomeOpen } from '@/stores/onboarding';

const props = defineProps<{
  current: number
}>();

const instance = getCurrentInstance();

const list = [
  {
    pagePath: '/pages/index/index',
    text: '日记',
    iconPath: '/static/tabs/diary.png',
    maskClass: 'mask-diary',
    anchorId: 'mp-nav-diary',
  },
  {
    pagePath: '/pages/analysis/analysis',
    text: '洞察',
    iconPath: '/static/tabs/analysis.png',
    maskClass: 'mask-analysis',
    anchorId: 'mp-nav-analysis',
  },
  {
    pagePath: '/pages/chat/chat',
    text: '陪伴',
    iconPath: '/static/tabs/chat.png',
    maskClass: 'mask-chat',
    anchorId: 'mp-nav-chat',
  },
  {
    pagePath: '/pages/profile/profile',
    text: '我的',
    iconPath: '/static/tabs/profile.png',
    maskClass: 'mask-profile',
    anchorId: 'mp-nav-profile',
  }
];

const measureTabAnchors = () => {
  if (typeof uni === 'undefined' || typeof uni.createSelectorQuery !== 'function') return;
  const query = instance?.proxy ? uni.createSelectorQuery().in(instance.proxy) : uni.createSelectorQuery();
  query
    .selectAll('.tab-item')
    .boundingClientRect((rects: any) => {
      if (!Array.isArray(rects)) return;
      rects.forEach((rect, idx) => {
        const item = list[idx];
        if (item && rect && rect.width > 0 && rect.height > 0) {
          registerSpotlightAnchorRect(item.anchorId, {
            top: rect.top,
            left: rect.left,
            width: rect.width,
            height: rect.height,
          });
        }
      });
    })
    .exec();
};

onMounted(() => {
  nextTick(() => {
    measureTabAnchors();
  });
});

watch(welcomeOpen, (open) => {
  if (open) {
    nextTick(() => {
      measureTabAnchors();
      setTimeout(measureTabAnchors, 120);
    });
  }
});

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
  height: 96rpx;
  padding-bottom: env(safe-area-inset-bottom);
  box-sizing: content-box;
  background: var(--theme-bg);
  box-shadow: 0 -1rpx 0 var(--theme-border);
  display: flex;
  flex-direction: column;
  z-index: 90;
}

.tab-bar-border {
  height: 1px;
  background: var(--theme-border);
  width: 100%;
}

.tab-list {
  display: flex;
  flex-direction: row;
  justify-content: space-around;
  align-items: center;
  height: 96rpx;
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  height: 100%;
  transition: opacity 0.2s ease;
}

.tab-item:active { opacity: 0.62; }

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

.mask-icon {
  -webkit-mask-size: contain;
  mask-size: contain;
  -webkit-mask-repeat: no-repeat;
  mask-repeat: no-repeat;
  -webkit-mask-position: center;
  mask-position: center;
}

.mask-diary {
  -webkit-mask-image: url('/static/tabs/diary-active.png');
  mask-image: url('/static/tabs/diary-active.png');
}

.mask-analysis {
  -webkit-mask-image: url('/static/tabs/analysis-active.png');
  mask-image: url('/static/tabs/analysis-active.png');
}

.mask-chat {
  -webkit-mask-image: url('/static/tabs/chat-active.png');
  mask-image: url('/static/tabs/chat-active.png');
}

.mask-profile {
  -webkit-mask-image: url('/static/tabs/profile-active.png');
  mask-image: url('/static/tabs/profile-active.png');
}

.active-icon {
  animation: bounceIn 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.tab-text {
  font-size: 21rpx;
  line-height: 1;
  transition: color 0.2s;
}

@keyframes bounceIn {
  0% { transform: scale(0.8); }
  50% { transform: scale(1.1); }
  100% { transform: scale(1); }
}
</style>
