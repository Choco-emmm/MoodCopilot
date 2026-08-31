import { ref } from 'vue';
import { get, post } from '../utils/request';
import { disconnectWebSocket } from '../utils/socket';
import { setAnnouncementUserId } from '../stores/announcement';

export const currentUser = ref<any>(null);
export const isLoggedIn = ref<boolean>(!!uni.getStorageSync('token'));

export const fetchCurrentUser = async () => {
  const token = uni.getStorageSync('token');
  if (!token) {
    isLoggedIn.value = false;
    currentUser.value = null;
    return null;
  }

  try {
    const res = await get('/api/auth/me');
    if (res.code === 200 && res.data) {
      isLoggedIn.value = true;
      currentUser.value = res.data.user || res.data;
      return currentUser.value;
    }
  } catch (e) {
    console.error('获取用户信息失败', e);
  }
  return null;
};

export const logout = async () => {
  // 1. Notify backend to invalidate the token (best-effort, don't block on failure)
  try {
    await post('/api/auth/logout');
  } catch (e) {
    // Even if backend logout fails, proceed with local cleanup
  }

  // 2. Disconnect WebSocket
  disconnectWebSocket();

  // 3. Clear local storage
  uni.removeStorageSync('token');
  uni.removeStorageSync('loginUserId');
  uni.removeStorageSync('userInfo');

  // 4. Reset reactive state
  isLoggedIn.value = false;
  currentUser.value = null;

  // 5. Reset announcement user context
  setAnnouncementUserId(null);
};
