import { ref } from 'vue';
import { get } from '../utils/request';

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

export const logout = () => {
  uni.removeStorageSync('token');
  isLoggedIn.value = false;
  currentUser.value = null;
};
