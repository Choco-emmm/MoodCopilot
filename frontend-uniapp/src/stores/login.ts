import { ref } from 'vue'
import { post } from '@/utils/request'
import { connectWebSocket } from '@/utils/socket'
import { acknowledgeGuestAnnouncementForUser, setAnnouncementUserId } from '@/stores/announcement'

type Continuation = (() => void) | null

export const loginSheetVisible = ref(false)
export const loginSubmitting = ref(false)
let continuation: Continuation = null

export function hasLoginToken() {
  return Boolean(uni.getStorageSync('token'))
}

export function requireLogin(next?: () => void) {
  if (hasLoginToken()) {
    next?.()
    return true
  }
  continuation = next ?? continuation
  loginSheetVisible.value = true
  return false
}

export function showLoginWithoutContinuation() {
  continuation = null
  loginSheetVisible.value = true
}

export function dismissLogin() {
  loginSheetVisible.value = false
  continuation = null
}

export function restoreLoggedInUser() {
  const storedUserId = uni.getStorageSync('loginUserId')
  if (storedUserId) setAnnouncementUserId(Number(storedUserId))
}

export function loginWithWechat() {
  if (loginSubmitting.value) return
  loginSubmitting.value = true
  uni.showLoading({ title: '登录中...' })
  uni.login({
    provider: 'weixin',
    success: async (loginResult) => {
      try {
        const response = await post<{ token: string; userId: number }>('/api/auth/wx-login', { code: loginResult.code })
        if (response.code !== 200 || !response.data?.token) {
          uni.showToast({ title: response.message || '登录失败', icon: 'none' })
          return
        }

        uni.setStorageSync('token', response.data.token)
        uni.setStorageSync('loginUserId', response.data.userId)
        setAnnouncementUserId(response.data.userId)
        acknowledgeGuestAnnouncementForUser(response.data.userId)
        connectWebSocket()
        loginSheetVisible.value = false
        uni.$emit('login-success')
        uni.showToast({ title: '登录成功', icon: 'success' })

        const next = continuation
        continuation = null
        next?.()
      } catch (error) {
        console.error('微信登录失败', error)
      } finally {
        uni.hideLoading()
        loginSubmitting.value = false
      }
    },
    fail: () => {
      uni.hideLoading()
      loginSubmitting.value = false
      uni.showToast({ title: '授权失败', icon: 'none' })
    },
  })
}
