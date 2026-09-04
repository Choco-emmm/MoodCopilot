<template>
  <view class="settings-page" :style="globalThemeStyle">
    <GlobalUI />
    <view class="header">
      <text class="page-title">数据合并</text>
      <text class="page-subtitle">把已有账户的数据带回微信小程序</text>
    </view>

    <view v-if="loading" class="loading-state">正在检查账户状态...</view>
    <view v-else class="merge-content">
      <view class="merge-note"><text class="note-mark">M</text><view><text class="note-title">继续使用已有记录</text><text class="note-text">绑定邮箱后，可将该邮箱账户下的日记、记忆与成长数据合并到当前微信账户。</text></view></view>
      <view class="merge-sheet" @click="showEmailModal = true"><view><text class="row-label">合并邮箱</text><text class="row-hint">{{ originalEmail || '尚未绑定，可随时开始' }}</text></view><view class="merge-action"><text>{{ originalEmail ? '更换' : '去绑定' }}</text><text class="arrow">›</text></view></view>
      <text class="privacy-note">不会公开你的邮箱或日记内容。若该邮箱已有账户，验证完成后将合并为同一个账号。</text>
    </view>

    <!-- Email Modal -->
    <view class="modal-overlay" v-if="showEmailModal" @click="showEmailModal = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">{{ originalEmail ? '更换合并邮箱' : '验证并合并账户' }}</text>
        <view class="email-form">
          <input class="modal-input" v-model="bindForm.email" placeholder="输入已有账户的邮箱" />
          <view class="code-row">
            <input class="modal-input code-input" v-model="bindForm.code" placeholder="验证码" />
            <button class="code-btn" :disabled="countdown > 0" @click="sendBindCode">{{ countdown > 0 ? `${countdown}s` : '获取验证码' }}</button>
          </view>
        </view>
        <view class="modal-actions">
          <button class="cancel-btn" @click="showEmailModal = false">取消</button>
          <button class="confirm-btn" @click="submitBindEmail">确认合并</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">

import { ref, onMounted, onUnmounted } from 'vue';
import { get, post } from '@/utils/request';
import GlobalUI from '@/components/GlobalUI.vue';

const loading = ref(true);

const originalEmail = ref('');
const countdown = ref(0);
const showEmailModal = ref(false);

const bindForm = ref({
  email: '',
  code: ''
});

let countdownTimer: ReturnType<typeof setInterval> | null = null;

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
});



onMounted(() => {
  fetchProfile();
});

const fetchProfile = async () => {
  try {
    const res = await get('/api/auth/me');
    if (res.code === 200 && res.data) {
      const u = res.data.user || res.data;
      let email = u.email || '';
      if (email.includes('@wx.com')) {
        email = '';
      }
      originalEmail.value = email;
      bindForm.value.email = ''; // clear for new binding
    }
  } catch (e) {
    console.error('Failed to fetch profile', e);
  } finally {
    loading.value = false;
  }
};

const sendBindCode = async () => {
  if (!bindForm.value.email.trim() || countdown.value > 0) return;
  uni.showLoading({ title: '发送中' });
  try {
    const res = await post('/api/auth/bind-email/send-code', { email: bindForm.value.email.trim() });
    if (res.code === 200) {
      uni.showToast({ title: '已发送', icon: 'success' });
      countdown.value = 60;
      countdownTimer = setInterval(() => {
        countdown.value--;
        if (countdown.value <= 0) {
          if (countdownTimer) clearInterval(countdownTimer);
          countdownTimer = null;
        }
      }, 1000);
    } else {
      uni.showToast({ title: res.message || '发送失败', icon: 'none' });
    }
  } catch(e) {
    uni.showToast({ title: '发送失败', icon: 'none' });
  } finally {
    uni.hideLoading();
  }
};

const submitBindEmail = async () => {
  if (!bindForm.value.email.trim() || !bindForm.value.code.trim()) {
    uni.showToast({ title: '请填写完整', icon: 'none' });
    return;
  }
  uni.showLoading({ title: '绑定中...' });
  try {
    const bindRes = await post('/api/auth/bind-email', { email: bindForm.value.email.trim(), code: bindForm.value.code.trim() });
    if (bindRes.code === 200) {
      if (bindRes.data) {
        uni.setStorageSync('token', bindRes.data);
        uni.showToast({ title: '账号合并成功', icon: 'success' });
        // Emit event to refresh feed/profile
        uni.$emit('profileUpdated');
        uni.$emit('refreshFeed');
      } else {
        uni.showToast({ title: '绑定成功', icon: 'success' });
      }
      originalEmail.value = bindForm.value.email.trim();
      showEmailModal.value = false;
      bindForm.value.code = '';
    } else {
      uni.showToast({ title: bindRes.message || '绑定失败', icon: 'none' });
    }
  } catch (e) {
    uni.showToast({ title: '绑定失败', icon: 'none' });
  } finally {
    uni.hideLoading();
  }
};

</script>

<style scoped>
.settings-page {
  min-height: 100vh;
  background-color: var(--theme-bg);
  padding: 40rpx;
  box-sizing: border-box;
}

.loading-state { padding: 160rpx 0; text-align: center; color: var(--theme-text-secondary); font-size: 28rpx; }
.merge-content { padding-top: 8rpx; }
.merge-note { display: flex; gap: 20rpx; padding: 30rpx; background: rgba(var(--theme-primary-rgb), .07); border-left: 4rpx solid var(--theme-primary); }
.note-mark { display: flex; width: 56rpx; height: 56rpx; align-items: center; justify-content: center; background: var(--theme-primary); color: #fff; font-family: "Noto Serif SC", serif; font-size: 30rpx; flex-shrink: 0; }
.note-title { display: block; color: var(--theme-text-primary); font-family: "Noto Serif SC", serif; font-size: 32rpx; }
.note-text { display: block; margin-top: 8rpx; color: var(--theme-text-secondary); font-size: 25rpx; line-height: 1.65; }
.merge-sheet { display: flex; align-items: center; justify-content: space-between; margin-top: 36rpx; padding: 30rpx; background: var(--theme-surface); border: 1px solid var(--theme-border); border-radius: 8rpx; box-shadow: 0 10rpx 26rpx rgba(32, 32, 29, .035); }
.row-label { display: block; color: var(--theme-text-primary); font-size: 30rpx; font-weight: 600; }
.row-hint { display: block; margin-top: 6rpx; color: var(--theme-text-placeholder); font-size: 24rpx; }
.merge-action { display: flex; align-items: center; gap: 8rpx; color: var(--theme-primary); font-size: 26rpx; }
.privacy-note { display: block; margin: 26rpx 8rpx; color: var(--theme-text-placeholder); font-size: 23rpx; line-height: 1.65; }

.header {
  margin-top: 20rpx;
  margin-bottom: 60rpx;
}

.page-title {
  font-size: 48rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
}

.email-right {
  display: flex;
  align-items: center;
}

.email-text {
  font-size: 28rpx;
  color: var(--theme-text-placeholder);
  margin-right: 16rpx;
}

.email-text.is-bound {
  color: var(--theme-primary);
}


.code-btn {
  font-size: 26rpx;
  color: #fff;
  background-color: var(--theme-primary);
  padding: 0 32rpx;
  border-radius: 12rpx;
  height: 90rpx;
  line-height: 90rpx;
  margin: 0 0 0 16rpx;
  white-space: nowrap;
}

.code-btn::after {
  display: none;
}

.code-btn[disabled] {
  color: var(--theme-text-placeholder);
  background-color: #e0ddd6;
}

.avatar-right {
  display: flex;
  align-items: center;
}

.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  margin-right: 16rpx;
}
.page-subtitle { display: block; margin-top: 8rpx; color: var(--theme-text-secondary); font-size: 27rpx; }
.avatar-fallback { display: flex; align-items: center; justify-content: center; background: var(--theme-primary); color: #fff; font-size: 32rpx; font-weight: 600; }

.arrow {
  font-size: 40rpx;
  color: #a09d98;
}

.submit-btn {
  margin-top: 48rpx;
  background-color: var(--theme-primary);
  color: var(--theme-surface);
  border-radius: 999rpx;
  font-size: 32rpx;
  font-weight: bold;
  padding: 24rpx 0;
  line-height: 1.5;
  transition: opacity 0.2s;
}

.submit-btn.disabled {
  opacity: 0.5;
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
  width: 85%;
  background-color: var(--theme-surface);
  border-radius: 24rpx;
  padding: 40rpx;
  box-shadow: 0 16rpx 40rpx rgba(0, 0, 0, 0.1);
  box-sizing: border-box;
}

.modal-title {
  font-size: 36rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
  margin-bottom: 32rpx;
  display: block;
  text-align: center;
}

.email-form {
  margin-bottom: 32rpx;
}

.modal-input {
  width: 100%;
  background: #f8f8f7;
  border-radius: 12rpx;
  padding: 24rpx;
  font-size: 30rpx;
  border: 1px solid #e0ddd6;
  box-sizing: border-box;
  margin-bottom: 24rpx;
}

.code-row {
  display: flex;
  align-items: center;
}

.code-input {
  flex: 1;
  margin-bottom: 0;
}

.modal-actions {
  display: flex;
  justify-content: space-between;
  gap: 24rpx;
  margin-top: 16rpx;
}

.cancel-btn, .confirm-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 40rpx;
  font-size: 30rpx;
  margin: 0;
}

.cancel-btn::after, .confirm-btn::after {
  border: none;
}

.cancel-btn {
  background-color: #f0f0f0;
  color: var(--theme-text-secondary);
}

.confirm-btn {
  background-color: var(--theme-primary);
  color: #fff;
}
</style>
