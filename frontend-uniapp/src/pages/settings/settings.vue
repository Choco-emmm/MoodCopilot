<template>
  <view class="settings-page" :style="themeStyle">
    <GlobalUI />
    <view class="header">
      <text class="page-title">个人设置</text>
    </view>

    <view class="form-container" v-if="!loading">
      <view class="form-item email-item" @click="showEmailModal = true">
        <text class="label">绑定邮箱</text>
        <view class="email-right">
          <text class="email-text" :class="{ 'is-bound': originalEmail }">{{ originalEmail || '未绑定' }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
    </view>

    <!-- Email Modal -->
    <view class="modal-overlay" v-if="showEmailModal" @click="showEmailModal = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">{{ originalEmail ? '更换绑定邮箱' : '绑定邮箱' }}</text>
        <view class="email-form">
          <input class="modal-input" v-model="bindForm.email" placeholder="输入真实邮箱" />
          <view class="code-row">
            <input class="modal-input code-input" v-model="bindForm.code" placeholder="验证码" />
            <button class="code-btn" :disabled="countdown > 0" @click="sendBindCode">{{ countdown > 0 ? `${countdown}s` : '获取验证码' }}</button>
          </view>
        </view>
        <view class="modal-actions">
          <button class="cancel-btn" @click="showEmailModal = false">取消</button>
          <button class="confirm-btn" @click="submitBindEmail">确认绑定</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { get, post, upload, getFullUrl } from '@/utils/request';
import GlobalUI from '@/components/GlobalUI.vue';
import { themeStyle, themeOptions, defaultLightTheme, defaultDarkTheme } from '@/stores/theme';

const loading = ref(true);
const isSubmitting = ref(false);

const form = ref({
  avatar: '',
  displayName: '',
  nickname: '',
  signature: ''
});

const originalEmail = ref('');
const countdown = ref(0);
const showEmailModal = ref(false);

const bindForm = ref({
  email: '',
  code: ''
});



onMounted(() => {
  fetchProfile();
});

const fetchProfile = async () => {
  try {
    const res = await get('/api/auth/me');
    if (res.code === 200 && res.data) {
      form.value.avatar = res.data.user.avatar || '';
      form.value.displayName = res.data.user.displayName || '';
      form.value.nickname = res.data.user.nickname || '';
      form.value.signature = res.data.user.signature || '';
      let email = res.data.user.email || '';
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

const changeAvatar = () => {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const tempFilePath = res.tempFilePaths[0];
      uni.showLoading({ title: '上传中...' });
      try {
        const uploadRes: any = await upload('/api/auth/avatar', tempFilePath);
        if (uploadRes.code === 200 && uploadRes.data && uploadRes.data.avatar) {
          form.value.avatar = uploadRes.data.avatar;
          uni.showToast({ title: '上传成功', icon: 'success' });
        } else {
          uni.showToast({ title: uploadRes.message || '上传失败', icon: 'none' });
        }
      } catch (e) {
        console.error(e);
        uni.showToast({ title: '上传失败', icon: 'none' });
      } finally {
        uni.hideLoading();
      }
    }
  });
};

const sendBindCode = async () => {
  if (!bindForm.value.email.trim() || countdown.value > 0) return;
  uni.showLoading({ title: '发送中' });
  try {
    const res = await post('/api/auth/bind-email/send-code', { email: bindForm.value.email.trim() });
    if (res.code === 200) {
      uni.showToast({ title: '已发送', icon: 'success' });
      countdown.value = 60;
      const timer = setInterval(() => {
        countdown.value--;
        if (countdown.value <= 0) clearInterval(timer);
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

const saveSettings = async () => {
  if (isSubmitting.value) return;
  if (!form.value.displayName.trim()) {
    uni.showToast({ title: '账号ID不能为空', icon: 'none' });
    return;
  }
  if (!form.value.nickname.trim()) {
    uni.showToast({ title: '昵称不能为空', icon: 'none' });
    return;
  }
  
  isSubmitting.value = true;
  uni.showLoading({ title: '保存中...' });
  try {
    const res = await post('/api/auth/update-profile', {
      avatar: form.value.avatar,
      displayName: form.value.displayName.trim(),
      nickname: form.value.nickname.trim(),
      signature: form.value.signature.trim()
    });
    
    if (res.code === 200) {
      uni.showToast({ title: '保存成功', icon: 'success' });
      uni.$emit('profileUpdated');
      setTimeout(() => {
        uni.navigateBack();
      }, 1000);
    } else {
      uni.showToast({ title: res.message || '保存失败', icon: 'none' });
    }
  } catch (e) {
    uni.showToast({ title: '保存失败', icon: 'none' });
  } finally {
    isSubmitting.value = false;
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

.header {
  margin-top: 20rpx;
  margin-bottom: 60rpx;
}

.page-title {
  font-size: 48rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
}

.form-container {
  background-color: var(--theme-surface);
  border-radius: 24rpx;
  padding: 0 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.02);
  margin-bottom: 60rpx;
}

.form-item {
  display: flex;
  align-items: center;
  padding: 32rpx 0;
  border-bottom: 1px solid rgba(0,0,0,0.05);
}

.form-item:last-child {
  border-bottom: none;
}

.avatar-item, .email-item {
  justify-content: space-between;
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

.label {
  width: 160rpx;
  font-size: 32rpx;
  color: var(--theme-text-primary);
  font-weight: 500;
}

.input-field, .textarea-field {
  flex: 1;
  font-size: 30rpx;
  color: var(--theme-text-primary);
  text-align: right;
}

.textarea-field {
  min-height: 40rpx;
  padding: 10rpx 0;
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

.arrow {
  font-size: 40rpx;
  color: #a09d98;
}

.submit-btn {
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
