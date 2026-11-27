<template>
  <view class="onboarding-demo-page" :style="globalThemeStyle">
    <GlobalUI />
    <scroll-view scroll-y class="demo-scroll" :show-scrollbar="false">
      <DemoExperience
        @exit="handleExit"
        @start-real-write="handleStartRealWrite"
      />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import GlobalUI from '@/components/GlobalUI.vue';
import DemoExperience from '@/components/onboarding/DemoExperience.vue';
import { requireLogin } from '@/stores/login';
import { exitOnboardingDemoPage, startRealWritingFromDemo } from '@/stores/onboarding';

function handleExit() {
  exitOnboardingDemoPage();
}

function handleStartRealWrite() {
  requireLogin(() => {
    startRealWritingFromDemo();
  });
}
</script>

<style scoped>
.onboarding-demo-page {
  min-height: 100vh;
  height: 100vh;
  background: var(--theme-bg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
}

.demo-scroll {
  flex: 1;
  min-height: 0;
}
</style>
