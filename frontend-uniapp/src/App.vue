<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";
import { connectWebSocket, disconnectWebSocket } from "@/utils/socket";
import { currentTheme } from "@/stores/theme";
import { watch } from "vue";


onLaunch(() => {
  console.log("App Launch");
  connectWebSocket();
  setTimeout(() => {
    // Theme initialization if needed
  }, 100);
});

onShow(() => {
  console.log("App Show");
  connectWebSocket();

});

onHide(() => {
  console.log("App Hide");
});


</script>
<style>
/* App global styles */
page {
  background-color: var(--theme-bg);
  color: #20201d;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", STHeiti, "Microsoft Yahei", Tahoma, Simsun, sans-serif;
  font-size: 28rpx;
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}

/* 针对部分需要复用衬线体的地方 */
.font-serif {
  font-family: "Noto Serif SC", "Songti SC", "STSong", "KaiTi", serif;
}

/* -------------------------------------
   Premium Global UI Utilities
   ------------------------------------- */

/* 1. 微动效 Hover & Active Effects */
.hover-scale {
  transition: transform 0.2s cubic-bezier(0.25, 0.8, 0.25, 1), box-shadow 0.2s cubic-bezier(0.25, 0.8, 0.25, 1);
}
.hover-scale:active {
  transform: scale(0.97);
}

/* 2. 多层平滑阴影 Smooth Shadows */
.smooth-shadow {
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.02), 
              0 12rpx 24rpx rgba(0, 0, 0, 0.02),
              0 24rpx 48rpx rgba(0, 0, 0, 0.02);
}
.smooth-shadow-lg {
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.03), 
              0 24rpx 48rpx rgba(0, 0, 0, 0.03),
              0 48rpx 96rpx rgba(0, 0, 0, 0.03);
}

/* 3. 玻璃拟物化 Glassmorphism */
.glass-card {
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

/* 夜间模式的玻璃材质适配 */
@media (prefers-color-scheme: dark) {
  .glass-card {
    background: rgba(40, 40, 40, 0.6);
    border: 1px solid rgba(255, 255, 255, 0.08);
  }
}

/* 4. 尊贵渐变 Premium Gradient */
.premium-gradient-bg {
  background: linear-gradient(135deg, #F9F3EA 0%, #E8DFD1 100%);
}

/* 5. 淡入动画 Fade In */
.fade-in {
  animation: fadeIn 0.4s ease-out forwards;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10rpx); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
