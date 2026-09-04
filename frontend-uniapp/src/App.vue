<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";
import { connectWebSocket, disconnectWebSocket } from "@/utils/socket";
import { currentTheme, syncNavigationBarColor } from "@/stores/theme";
import { watch } from "vue";
import { get } from '@/utils/request';
import { loadActiveAnnouncement, setAnnouncementUserId } from '@/stores/announcement';
import { restoreLoggedInUser, showLoginWithoutContinuation } from '@/stores/login';


onLaunch(() => {
  console.log("App Launch");
  restoreLoggedInUser();
  syncNavigationBarColor();
  void loadActiveAnnouncement();
  void restoreAnnouncementUser();
  connectWebSocket();
  setTimeout(() => {
    // Theme initialization if needed
  }, 100);
});

onShow(() => {
  console.log("App Show");
  syncNavigationBarColor();
  connectWebSocket();

});

onHide(() => {
  console.log("App Hide");
  disconnectWebSocket();
});

uni.$on('unauthorized', () => {
  showLoginWithoutContinuation();
});

async function restoreAnnouncementUser() {
  if (!uni.getStorageSync('token')) return;
  try {
    const response = await get<{ userId?: number }>('/api/auth/me');
    const userId = response.data?.userId;
    if (response.code === 200 && userId) {
      uni.setStorageSync('loginUserId', userId);
      setAnnouncementUserId(userId);
    }
  } catch (error) {
    console.warn('恢复公告用户标识失败', error);
  }
}


</script>
<style>
/* App global styles */
page {
  background-color: var(--theme-bg);
  color: var(--theme-text-primary);
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
  box-shadow: 0 4rpx 12rpx color-mix(in oklab, var(--theme-primary) 3%, transparent),
              0 12rpx 24rpx color-mix(in oklab, var(--theme-primary) 3%, transparent),
              0 24rpx 48rpx color-mix(in oklab, var(--theme-primary) 3%, transparent);
}
.smooth-shadow-lg {
  box-shadow: 0 8rpx 24rpx color-mix(in oklab, var(--theme-primary) 4%, transparent),
              0 24rpx 48rpx color-mix(in oklab, var(--theme-primary) 4%, transparent),
              0 48rpx 96rpx color-mix(in oklab, var(--theme-primary) 4%, transparent);
}

/* 3. 玻璃拟物化 Glassmorphism */
.glass-card {
  background: color-mix(in oklab, var(--theme-surface) 60%, transparent);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid color-mix(in oklab, var(--theme-surface) 30%, transparent);
}

/* 夜间模式的玻璃材质适配 */
@media (prefers-color-scheme: dark) {
  .glass-card {
    background: color-mix(in oklab, var(--theme-surface) 60%, transparent);
    border: 1px solid color-mix(in oklab, var(--theme-surface) 8%, transparent);
  }
}

/* 4. 尊贵渐变 Premium Gradient */
.premium-gradient-bg {
  background: linear-gradient(135deg, var(--theme-surface), var(--theme-bg));
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
