<template>
  <view class="profile-page" :style="localThemeStyle">
    <GlobalUI :tabIndex="3" />
    <view class="header clean-bg">
      <view class="user-info fade-in">
        <view class="avatar">
          <image :src="getFullUrl(userInfo?.avatar) || `data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23999999'%3E%3Cpath d='M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z'/%3E%3C/svg%3E`" class="avatar-icon" mode="aspectFill" />
        </view>
        <view class="user-detail">
          <view style="display: flex; align-items: center; gap: 12rpx;">
            <text class="nickname">{{ isLoggedIn ? (userInfo?.nickname || userInfo?.displayName || '微信用户') : '未登录' }}</text>
          </view>
          <text v-if="isLoggedIn && quotaInfo" class="level-text">Lv.{{ quotaInfo.level }} 用户</text>
        </view>
      </view>
      <view v-if="!isLoggedIn" class="login-btn hover-scale" @click="handleWechatLogin">
        登录以体验更多功能
      </view>
    </view>

    <view class="section fade-in" v-if="isLoggedIn">
      <text class="section-title">我的账户</text>
      
      <view class="card quota-card glass-card smooth-shadow" v-if="quotaInfo" @click="showQuotaModal = true">
        <view class="quota-header">
          <text class="quota-header-title">当前配额</text>
          <text class="quota-header-link">查看配额表 ></text>
        </view>
        <view class="quota-item">
          <text class="quota-label">当前经验值</text>
          <text class="quota-value">{{ quotaInfo.exp }}</text>
        </view>
        <view class="quota-item" v-if="quotaInfo.quotas">
          <text class="quota-label">AI 聊天剩余次数</text>
          <text class="quota-value">{{ quotaInfo.quotas.CHAT || 0 }} {{ quotaInfo.maxQuotas ? '/ ' + quotaInfo.maxQuotas.CHAT : '' }}</text>
        </view>
        <view class="quota-item" v-if="quotaInfo.quotas">
          <text class="quota-label">AI 报告剩余次数</text>
          <text class="quota-value">{{ quotaInfo.quotas.REPORT || 0 }} {{ quotaInfo.maxQuotas ? '/ ' + quotaInfo.maxQuotas.REPORT : '' }}</text>
        </view>
        <view class="quota-item" v-if="quotaInfo.quotas">
          <text class="quota-label">AI 分析剩余次数</text>
          <text class="quota-value">{{ quotaInfo.quotas.ANALYSIS || quotaInfo.quotas.AI_DIARY_ANALYSIS || 0 }} {{ quotaInfo.maxQuotas ? '/ ' + quotaInfo.maxQuotas.ANALYSIS : '' }}</text>
        </view>
        <view class="quota-item" v-if="quotaInfo.quotas">
          <text class="quota-label">图片上传剩余次数</text>
          <text class="quota-value">{{ quotaInfo.quotas.IMAGE_UPLOAD || 0 }} {{ quotaInfo.maxQuotas ? '/ ' + quotaInfo.maxQuotas.IMAGE_UPLOAD : '' }}</text>
        </view>
      </view>
      <view class="card quota-card" v-else>
        <text class="empty-text">加载中...</text>
      </view>
    </view>

    <view class="section fade-in" style="animation-delay: 0.1s;" v-if="isLoggedIn">
      <view class="card action-card checkin-card hover-scale smooth-shadow" @click="goToGrowth" style="margin-bottom: 24rpx;">
        <text class="checkin-text">🎁 每日签到 & 成长中心</text>
      </view>
      <view class="card action-card hover-scale smooth-shadow" @click="goToNotifications" style="margin-bottom: 24rpx;">
        <text class="action-text">🔔 系统通知</text>
      </view>
      <view class="card action-card hover-scale smooth-shadow" @click="goToSummaries" style="margin-bottom: 24rpx;">
        <text class="action-text">📊 情绪报告</text>
      </view>
      <view class="card action-card hover-scale smooth-shadow" @click="goToCollections" style="margin-bottom: 24rpx;">
        <text class="action-text">📁 我的合集</text>
      </view>
      <view class="card action-card hover-scale smooth-shadow" @click="goToFeedback" style="margin-bottom: 24rpx;">
        <text class="action-text">✉️ 意见反馈</text>
      </view>
      <view class="card action-card hover-scale smooth-shadow" @click="goToSettings">
        <text class="action-text">⚙️ 个人设置</text>
      </view>
    </view>

    <view class="section theme-section" v-if="isLoggedIn">
      <view class="section-title">界面外观</view>
      <view class="card theme-card" style="padding: 32rpx 24rpx;">
        <!-- Theme Mode Selector -->
        <view class="mode-selector">
          <view class="mode-item" :class="{ active: themeMode === 'light' }" @click="setThemeMode('light')">
            <text class="mode-icon">☀️</text>
            <text class="mode-text">日间</text>
          </view>
          <view class="mode-item" :class="{ active: themeMode === 'dark' }" @click="setThemeMode('dark')">
            <text class="mode-icon">🌙</text>
            <text class="mode-text">夜间</text>
          </view>
          <view class="mode-item" :class="{ active: themeMode === 'auto' }" @click="setThemeMode('auto')">
            <text class="mode-icon">⚙️</text>
            <text class="mode-text">跟随系统</text>
          </view>
        </view>

        <!-- Toggle Custom Themes -->
        <view class="custom-theme-toggle" @click="showCustomThemes = !showCustomThemes">
          <text class="toggle-text">🌈 自定义默认主题</text>
          <text class="toggle-icon">{{ showCustomThemes ? '▲' : '▼' }}</text>
        </view>

        <!-- Custom Themes Grid -->
        <view v-if="showCustomThemes" class="custom-theme-container">
          <view class="theme-section-label">☀️ 日间主题设定</view>
          <template v-for="group in lightThemeGroups" :key="group.name">
            <view class="theme-category-title">{{ group.name }}</view>
            <view class="theme-grid">
              <view 
                v-for="theme in group.themes" 
                :key="theme.value"
                class="theme-item"
                :class="{ active: defaultLightTheme === theme.value }"
                :style="{ '--t-primary': theme.primary, '--t-accent': theme.accent, '--t-bg': theme.bg, '--t-surface': theme.surface }"
                @click="setSpecificTheme(theme.value, false)"
              >
                <view class="theme-preview">
                  <view class="theme-preview-bg">
                    <view class="theme-preview-swatch theme-swatch-1"></view>
                    <view class="theme-preview-swatch theme-swatch-2"></view>
                    <view class="theme-preview-bar theme-bar-1"></view>
                    <view class="theme-preview-bar theme-bar-2"></view>
                  </view>
                </view>
                <text class="theme-name">{{ theme.label }}</text>
              </view>
            </view>
          </template>

          <view class="theme-section-label" style="margin-top: 24rpx;">🌙 夜间主题设定</view>
          <view class="theme-grid">
            <view 
              v-for="theme in darkThemeOptions" 
              :key="theme.value"
              class="theme-item"
              :class="{ active: defaultDarkTheme === theme.value }"
              :style="{ '--t-primary': theme.primary, '--t-accent': theme.accent, '--t-bg': theme.bg, '--t-surface': theme.surface }"
              @click="setSpecificTheme(theme.value, true)"
            >
              <view class="theme-preview">
                <view class="theme-preview-bg">
                  <view class="theme-preview-swatch theme-swatch-1"></view>
                  <view class="theme-preview-swatch theme-swatch-2"></view>
                  <view class="theme-preview-bar theme-bar-1"></view>
                  <view class="theme-preview-bar theme-bar-2"></view>
                </view>
              </view>
              <text class="theme-name">{{ theme.label }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="section fade-in" style="animation-delay: 0.2s;" v-if="isLoggedIn">
      <view class="card action-card logout-card hover-scale smooth-shadow" @click="handleLogout">
        <text class="logout-text">退出登录</text>
      </view>
    </view>

    <!-- Quota Modal -->
    <view v-if="showQuotaModal" class="quota-modal-overlay" @click="showQuotaModal = false">
      <view class="quota-modal-content" @click.stop>
        <view class="quota-modal-header">
          <text class="quota-modal-title">配额表</text>
          <text class="quota-modal-close" @click="showQuotaModal = false">×</text>
        </view>
        <view class="quota-modal-subtitle">当前：Lv.{{ quotaInfo?.level || 1 }}</view>
        
        <scroll-view scroll-x class="quota-table-container">
          <view class="quota-table">
            <view class="quota-tr quota-th">
              <view class="quota-td">身份 / 等级</view>
              <view class="quota-td">AI 聊天<text class="quota-unit">/天</text></view>
              <view class="quota-td">AI 分析<text class="quota-unit">/天</text></view>
              <view class="quota-td">深度思考<text class="quota-unit">/天</text></view>
              <view class="quota-td">共鸣检索<text class="quota-unit">/天</text></view>
              <view class="quota-td">图片上传<text class="quota-unit">/天</text></view>
              <view class="quota-td">图片分析<text class="quota-unit">/天</text></view>
            </view>
            <view class="quota-tr" :class="{ 'active-row': quotaInfo?.level === 1 }">
              <view class="quota-td">Lv.1</view>
              <view class="quota-td">15次</view><view class="quota-td">5次</view><view class="quota-td">2次</view>
              <view class="quota-td">—</view><view class="quota-td">3次</view><view class="quota-td">2次</view>
            </view>
            <view class="quota-tr" :class="{ 'active-row': quotaInfo?.level === 2 }">
              <view class="quota-td">Lv.2</view>
              <view class="quota-td">25次</view><view class="quota-td">8次</view><view class="quota-td">4次</view>
              <view class="quota-td">3次</view><view class="quota-td">5次</view><view class="quota-td">3次</view>
            </view>
            <view class="quota-tr" :class="{ 'active-row': quotaInfo?.level === 3 }">
              <view class="quota-td">Lv.3</view>
              <view class="quota-td">35次</view><view class="quota-td">12次</view><view class="quota-td">6次</view>
              <view class="quota-td">5次</view><view class="quota-td">8次</view><view class="quota-td">5次</view>
            </view>
            <view class="quota-tr" :class="{ 'active-row': quotaInfo?.level === 4 }">
              <view class="quota-td">Lv.4</view>
              <view class="quota-td">45次</view><view class="quota-td">16次</view><view class="quota-td">8次</view>
              <view class="quota-td">8次</view><view class="quota-td">12次</view><view class="quota-td">8次</view>
            </view>
            <view class="quota-tr" :class="{ 'active-row': quotaInfo?.level === 5 }">
              <view class="quota-td">Lv.5</view>
              <view class="quota-td">55次</view><view class="quota-td">20次</view><view class="quota-td">10次</view>
              <view class="quota-td">10次</view><view class="quota-td">16次</view><view class="quota-td">12次</view>
            </view>
            <view class="quota-tr" :class="{ 'active-row': quotaInfo?.level === 6 }">
              <view class="quota-td">Lv.6</view>
              <view class="quota-td">65次</view><view class="quota-td">25次</view><view class="quota-td">12次</view>
              <view class="quota-td">12次</view><view class="quota-td">20次</view><view class="quota-td">15次</view>
            </view>
          </view>
        </scroll-view>

        <view class="quota-modal-desc">
          <view class="desc-item"><text class="desc-icon">💡</text><text class="desc-text"><text class="desc-bold">AI 分析：</text>发布或修改日记时自动触发（含基础配图提炼）。</text></view>
          <view class="desc-item"><text class="desc-icon">💡</text><text class="desc-text"><text class="desc-bold">图片分析：</text>聊天时向 AI 追问图片内的具体文字、细节（基础提炼未涵盖的内容）时才触发。</text></view>
          <view class="desc-item"><text class="desc-icon">💡</text><text class="desc-text"><text class="desc-bold">深度思考：</text>当您的问题涉及复杂心理分析、建议或情绪梳理时，后台智能路由会自动为您开启长链路推演。</text></view>
          <view class="desc-item"><text class="desc-icon">💡</text><text class="desc-text"><text class="desc-bold">共鸣检索：</text>功能加紧开发中，敬请期待...</text></view>
        </view>
        
        <view class="quota-modal-footer">
          AI 聊天 / 分析 / 思考 / 检索 / 传图 每日 0 点重置 · 报告每月 1 日重置
        </view>
      </view>
    </view>



  </view>
</template>

<script setup lang="ts">
import GlobalUI from '@/components/GlobalUI.vue';
import { themeStyle, themeOptions, currentTheme, themeMode, defaultLightTheme, defaultDarkTheme, setThemeMode, setSpecificTheme } from '@/stores/theme';
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { get, post, upload, getFullUrl } from '@/utils/request';
import { connectWebSocket, disconnectWebSocket } from '@/utils/socket';

const localThemeStyle = ref(themeStyle.value);

onShow(() => {
  localThemeStyle.value = themeStyle.value;
});

uni.$on('themeChanged', () => {
  localThemeStyle.value = themeStyle.value;
});

const showQuotaModal = ref(false);

const showCustomThemes = ref(false);

const lightThemeGroups = computed(() => {
  const groups: { name: string, themes: typeof themeOptions }[] = [];
  const map = new Map<string, typeof themeOptions>();
  themeOptions.filter(t => !t.dark).forEach(t => {
    const cat = t.category || '其它';
    if (!map.has(cat)) map.set(cat, []);
    map.get(cat)!.push(t);
  });
  for (const [name, themes] of map.entries()) {
    groups.push({ name, themes });
  }
  return groups;
});

const darkThemeOptions = computed(() => themeOptions.filter(t => !!t.dark));

const isLoggedIn = ref(false);
const quotaInfo = ref<any>(null);
const userInfo = ref<any>(null);

const showEditProfileModal = ref(false);
const editForm = ref({
  displayName: '',
  nickname: '',
  avatar: ''
});

const openEditProfile = () => {
  editForm.value = {
    displayName: userInfo.value?.displayName || '',
    nickname: userInfo.value?.nickname || '',
    avatar: userInfo.value?.avatar || ''
  };
  showEditProfileModal.value = true;
};

const onChooseAvatar = async (e: any) => {
  const avatarUrl = e.detail.avatarUrl;
  uni.showLoading({ title: '上传中...' });
  try {
    const res = await upload('/api/auth/avatar', avatarUrl);
    if (res.code === 200 && res.data?.avatar) {
      editForm.value.avatar = res.data.avatar;
      uni.showToast({ title: '上传成功', icon: 'success' });
    } else {
      uni.showToast({ title: res.message || '上传失败', icon: 'none' });
    }
  } catch (err: any) {
    uni.showToast({ title: err.message || '上传请求失败', icon: 'none' });
  } finally {
    uni.hideLoading();
  }
};

const saveProfile = async () => {
  if (!editForm.value.displayName) {
    uni.showToast({ title: '账号ID不能为空', icon: 'none' });
    return;
  }
  if (!editForm.value.nickname) {
    uni.showToast({ title: '昵称不能为空', icon: 'none' });
    return;
  }
  uni.showLoading({ title: '保存中...' });
  try {
    const res = await post('/api/auth/update-profile', {
      displayName: editForm.value.displayName,
      nickname: editForm.value.nickname,
      avatar: editForm.value.avatar,
      signature: userInfo.value?.signature || ''
    });
    if (res.code === 200) {
      uni.showToast({ title: '保存成功', icon: 'success' });
      showEditProfileModal.value = false;
      fetchUserInfo();
      uni.$emit('profileUpdated');
    }
  } catch (e) {
    console.error(e);
  } finally {
    uni.hideLoading();
  }
};

const goToCollections = () => {
  uni.navigateTo({ url: '/pages/collections/collections' });
};

const goToSummaries = () => {
  uni.navigateTo({ url: '/pages/summaries/summaries' });
};

const goToGrowth = () => {
  if (checkLoginStatus()) uni.navigateTo({ url: '/pages/growth/growth' });
};

const goToNotifications = () => {
  if (checkLoginStatus()) uni.navigateTo({ url: '/pages/notifications/notifications' });
};

const goToFeedback = () => {
  uni.navigateTo({ url: '/pages/feedback/feedback' });
};

const goToSettings = () => {
  uni.navigateTo({ url: '/pages/settings/settings' });
};

onMounted(() => {
  checkLoginStatus();
  uni.$on('unauthorized', () => {
    handleLogout();
  });
  uni.$on('profileUpdated', () => {
    fetchUserInfo();
  });
});

const checkLoginStatus = () => {
  const token = uni.getStorageSync('token');
  if (token) {
    isLoggedIn.value = true;
    connectWebSocket();
    fetchQuotaInfo();
    fetchUserInfo();
  } else {
    isLoggedIn.value = false;
    quotaInfo.value = null;
  }
};

const fetchQuotaInfo = async () => {
  if (!isLoggedIn.value) return;
  try {
    const res = await get('/api/user/quota');
    if (res.code === 200) {
      quotaInfo.value = res.data;
    }
  } catch (e) {
    console.error('Failed to fetch quota info', e);
  }
};

const fetchUserInfo = async () => {
  if (!isLoggedIn.value) return;
  try {
    const res = await get('/api/auth/me');
    if (res.code === 200 && res.data) {
      userInfo.value = res.data.user;
    }
  } catch (e) {
    console.error('Failed to fetch user info', e);
  }
};

const handleWechatLogin = () => {
  uni.showLoading({ title: '登录中...' });
  uni.login({
    provider: 'weixin',
    success: (res) => {
      post('/api/auth/wx-login', { code: res.code })
        .then((result: any) => {
          uni.hideLoading();
          if (result.code === 200 && result.data && result.data.token) {
            uni.setStorageSync('token', result.data.token);
            isLoggedIn.value = true;
            uni.showToast({ title: '登录成功', icon: 'success' });
            connectWebSocket(); // Connect WS on login
            fetchQuotaInfo();
            fetchUserInfo();
            uni.$emit('refreshFeed'); // Refresh home feed
            
            // Auto-open profile edit if default user
            if (result.data.user && result.data.user.nickname && result.data.user.nickname.startsWith('微信用户')) {
              setTimeout(() => {
                openEditProfile();
              }, 600);
            }
          } else {
            uni.showToast({ title: '登录失败', icon: 'none' });
          }
        })
        .catch(() => uni.hideLoading());
    },
    fail: () => {
      uni.hideLoading();
      uni.showToast({ title: '授权失败', icon: 'none' });
    }
  });
};

const handleLogout = () => {
  uni.removeStorageSync('token');
  uni.removeStorageSync('userInfo');
  isLoggedIn.value = false;
  quotaInfo.value = null;
  disconnectWebSocket(); // Disconnect WS on logout
  uni.$emit('refreshFeed');
  uni.showToast({ title: '已退出', icon: 'none' });
};

const fetchQuota = async () => {
  try {
    const res = await get('/api/user/quota');
    if (res.code === 200) {
      quotaInfo.value = res.data;
    }
  } catch (e) {
    console.error('Failed to fetch quota', e);
  }
};
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background-color: var(--theme-bg);
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.header {
  padding-top: 100rpx;
  padding-bottom: 60rpx;
  padding-left: 48rpx;
  padding-right: 48rpx;
  padding: 80rpx 48rpx 60rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  overflow: hidden;
}

.clean-bg {
  background-color: var(--theme-bg);
  border-bottom: 1px solid rgba(0,0,0,0.02);
}

.user-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 1;
}

.avatar {
  margin-bottom: 24rpx;
  padding: 8rpx;
  background: var(--theme-surface);
  border-radius: 50%;
  box-shadow: 0 8rpx 24rpx rgba(0,0,0,0.06);
}

.avatar-icon {
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  background-color: var(--theme-border);
}

.user-detail {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.account-id {
  font-size: 26rpx;
  color: var(--theme-text-secondary);
  margin-top: 8rpx;
}

.nickname {
  font-size: 40rpx;
  font-weight: 800;
  color: var(--theme-text-primary);
  margin-bottom: 8rpx;
}

.level-text {
  font-size: 24rpx;
  color: var(--theme-text-secondary);
  background-color: rgba(0,0,0,0.05);
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
}

.login-btn {
  background-color: var(--theme-primary);
  color: white;
  text-align: center;
  padding: 24rpx 0;
  border-radius: 999rpx;
  font-size: 32rpx;
  font-weight: bold;
  backdrop-filter: blur(10px);
}

.section {
  margin-bottom: 48rpx;
  padding: 0 40rpx;
}

.section-title {
  font-family: "Noto Serif SC", "Songti SC", "STSong", "KaiTi", serif;
  font-size: 32rpx;
  color: var(--theme-text-primary);
  font-weight: 600;
  margin-bottom: 24rpx;
  display: block;
}

.card {
  background-color: var(--theme-surface);
  border-radius: 4rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0,0,0,0.05);
}

.quota-card {
  padding: 16rpx 32rpx;
}

.quota-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1px dashed rgba(var(--theme-primary-rgb), 0.2);
}

.quota-item:last-child {
  border-bottom: none;
}

.quota-label {
  font-size: 28rpx;
  color: var(--theme-text-primary);
}

.quota-value {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--theme-primary);
}

.action-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 36rpx;
  border-radius: 4rpx;
  transition: transform 0.2s ease;
}

.hover-scale:active {
  transform: scale(0.98);
}

.checkin-card {
  background: linear-gradient(135deg, rgba(var(--theme-primary-rgb), 0.1) 0%, rgba(var(--theme-primary-rgb), 0.05) 100%);
  border: 1px solid rgba(var(--theme-primary-rgb), 0.1);
}

.checkin-text {
  font-size: 32rpx;
  color: var(--theme-primary);
  font-weight: 600;
}

.logout-text {
  font-size: 32rpx;
  color: var(--theme-accent);
  font-weight: 600;
}

/* Quota Modal */
.quota-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32rpx;
}

.close-btn {
  background-color: var(--theme-primary);
  color: #fff;
  border-radius: 999rpx;
  font-size: 32rpx;
  font-weight: 500;
  padding: 24rpx 0;
  border: none;
}
.close-btn::after {
  display: none;
}

.edit-icon {
  font-size: 32rpx;
  color: var(--theme-primary);
  opacity: 0.8;
  padding: 4rpx;
}

/* Modal Styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.4);
  z-index: 1000;
  display: flex;
  justify-content: center;
  align-items: center;
}

.modal-content {
  width: 85%;
  background-color: var(--theme-surface);
  border-radius: 24rpx;
  padding: 40rpx;
  box-shadow: 0 16rpx 40rpx rgba(0, 0, 0, 0.1);
  box-sizing: border-box;
}

.modal-title {
  font-size: 34rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
  margin-bottom: 32rpx;
  text-align: center;
  display: block;
}

.edit-profile-form {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.edit-form-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.edit-label {
  font-size: 26rpx;
  color: var(--theme-text-secondary);
  margin-bottom: 16rpx;
  align-self: flex-start;
}

.avatar-wrapper {
  padding: 0;
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  background-color: transparent;
  border: none;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.1);
}
.avatar-wrapper::after {
  display: none;
}

.edit-avatar-img {
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  background-color: var(--theme-border);
}

.edit-input {
  width: 100%;
  background: #f8f8f7;
  border-radius: 12rpx;
  padding: 24rpx;
  font-size: 30rpx;
  border: 1px solid #e0ddd6;
  box-sizing: border-box;
}

.modal-actions {
  display: flex;
  justify-content: space-between;
  gap: 24rpx;
  margin-top: 48rpx;
}

.cancel-btn {
  flex: 1;
  background-color: #f0f0f0;
  color: var(--theme-text-secondary);
  font-size: 30rpx;
  border-radius: 40rpx;
  margin: 0;
}
.cancel-btn::after { border: none; }

.confirm-btn {
  flex: 1;
  background-color: var(--theme-primary);
  color: #fff;
  font-size: 30rpx;
  border-radius: 40rpx;
  margin: 0;
}
.confirm-btn::after { border: none; }

.quota-modal-content {
  background: var(--theme-surface);
  border-radius: 16rpx;
  width: 100%;
  max-height: 90vh;
  padding: 32rpx;
  display: flex;
  flex-direction: column;
}

.quota-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}

.quota-modal-title {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.quota-modal-close {
  font-size: 40rpx;
  color: var(--theme-text-placeholder);
  padding: 10rpx;
}

.quota-modal-subtitle {
  font-size: 28rpx;
  color: var(--theme-text-secondary);
  margin-bottom: 24rpx;
}

.quota-table-container {
  width: 100%;
  margin-bottom: 32rpx;
}

.quota-table {
  display: flex;
  flex-direction: column;
  min-width: 1200rpx;
}

.quota-tr {
  display: flex;
  flex-direction: row;
  border-bottom: 1px solid rgba(0,0,0,0.05);
}

.quota-tr.active-row {
  background-color: rgba(var(--theme-primary-rgb), 0.05);
}

.quota-th .quota-td {
  font-weight: 600;
  color: var(--theme-text-secondary);
  font-size: 24rpx;
  padding: 24rpx 16rpx;
}

.quota-td {
  flex: 1;
  text-align: center;
  padding: 24rpx 16rpx;
  font-size: 24rpx;
  color: var(--theme-text-primary);
}

.quota-unit {
  font-size: 20rpx;
  color: var(--theme-text-placeholder);
}

.quota-modal-desc {
  background: rgba(var(--theme-primary-rgb), 0.03);
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 24rpx;
}

.desc-item {
  display: flex;
  margin-bottom: 16rpx;
}
.desc-item:last-child {
  margin-bottom: 0;
}

.desc-icon {
  margin-right: 12rpx;
  font-size: 28rpx;
}

.desc-text {
  font-size: 24rpx;
  color: var(--theme-text-secondary);
  line-height: 1.6;
}

.desc-bold {
  font-weight: 600;
  color: var(--theme-primary);
}

.quota-modal-footer {
  text-align: center;
  font-size: 22rpx;
  color: var(--theme-text-placeholder);
}

.quota-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.quota-header-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--theme-text-primary);
}
.quota-header-link {
  font-size: 24rpx;
  color: var(--theme-primary);
}

.empty-text {
  color: #7d7870;
  font-size: 28rpx;
  display: block;
  text-align: center;
  padding: 24rpx;
}

/* Theme Switcher */
.theme-card {
  padding: 24rpx;
}

.mode-selector {
  display: flex;
  justify-content: space-between;
  margin-bottom: 32rpx;
  background: rgba(0, 0, 0, 0.03);
  padding: 8rpx;
  border-radius: 4rpx;
}

.mode-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16rpx 0;
  border-radius: 4rpx;
  transition: all 0.2s;
  color: var(--theme-text-secondary);
}

.mode-item.active {
  background: var(--theme-surface);
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.05);
  color: var(--theme-primary, var(--theme-text-primary));
  font-weight: bold;
}

.mode-icon {
  font-size: 32rpx;
  margin-right: 8rpx;
}

.mode-text {
  font-size: 28rpx;
}

.custom-theme-toggle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  background-color: rgba(var(--theme-primary-rgb), 0.05);
  border-radius: 4rpx;
  margin-bottom: 24rpx;
}

.toggle-text {
  font-size: 28rpx;
  color: var(--theme-primary);
  font-weight: 500;
}

.toggle-icon {
  font-size: 24rpx;
  color: var(--theme-primary);
}

.custom-theme-container {
  padding-top: 16rpx;
  border-top: 1px dashed rgba(var(--theme-primary-rgb), 0.1);
}

.theme-section-label {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary, var(--theme-text-primary));
  margin-bottom: 24rpx;
}
.theme-category-title {
  font-size: 24rpx;
  color: var(--color-text-secondary, var(--theme-text-secondary));
  margin-bottom: 16rpx;
}

.theme-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx 16rpx;
  margin-bottom: 32rpx;
}

.theme-item {
  width: calc(25% - 12rpx); /* 4 items per row */
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  opacity: 0.7;
  transition: all 0.2s ease;
}

.theme-item.active {
  opacity: 1;
}

.theme-preview {
  width: 90rpx;
  height: 90rpx;
  border-radius: 45rpx;
  padding: 6rpx;
  border: 4rpx solid transparent;
  transition: all 0.2s ease;
}

.theme-item.active .theme-preview {
  border-color: var(--t-primary);
  transform: scale(1.05);
}

.theme-preview-bg {
  width: 100%;
  height: 100%;
  border-radius: 45rpx;
  background: var(--t-surface);
  border: 2rpx solid rgba(0,0,0,0.05);
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.05);
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.theme-swatch-1 {
  position: absolute;
  top: 12rpx;
  left: 12rpx;
  width: 20rpx;
  height: 20rpx;
  border-radius: 10rpx;
  background: var(--t-primary);
}

.theme-swatch-2 {
  position: absolute;
  top: 12rpx;
  right: 12rpx;
  width: 14rpx;
  height: 14rpx;
  border-radius: 4rpx;
  background: var(--t-accent);
}

.theme-bar-1 {
  width: 40rpx;
  height: 6rpx;
  background: var(--t-primary);
  border-radius: 3rpx;
  opacity: 0.4;
  margin-top: 12rpx;
}

.theme-bar-2 {
  width: 28rpx;
  height: 6rpx;
  background: var(--t-primary);
  border-radius: 3rpx;
  opacity: 0.2;
  margin-top: 6rpx;
}

.theme-name {
  font-size: 22rpx;
  color: var(--color-text-secondary, var(--theme-text-secondary));
  white-space: nowrap;
}
</style>

