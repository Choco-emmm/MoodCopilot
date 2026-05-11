import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const userId = ref<number | null>(null)
  const displayName = ref<string | null>(null)
  const avatar = ref<string | null>(null)
  const dailyNotifyEnabled = ref<boolean>(true)

  const isAuthenticated = computed(() => !!token.value)

  async function fetchProfile() {
    try {
      const res = await authApi.me()
      const data = res.data.data
      userId.value = data.userId
      displayName.value = data.displayName
      avatar.value = data.avatar
      dailyNotifyEnabled.value = data.dailyNotifyEnabled !== false
    } catch { /* ignore */ }
  }

  async function updateProfile(name?: string, avatarUrl?: string) {
    const res = await authApi.updateProfile({ displayName: name, avatar: avatarUrl })
    const data = res.data.data
    displayName.value = data.displayName
    avatar.value = data.avatar
  }

  async function uploadAvatar(file: File) {
    const res = await authApi.uploadAvatar(file)
    avatar.value = res.data.data.avatar
  }

  async function updateSettings(enabled: boolean) {
    await authApi.updateSettings(enabled)
    dailyNotifyEnabled.value = enabled
  }

  async function login(email: string, password: string) {
    const res = await authApi.login({ email, password })
    const data = res.data.data
    token.value = data.token
    userId.value = data.userId
    displayName.value = data.displayName
    avatar.value = data.avatar
    dailyNotifyEnabled.value = data.dailyNotifyEnabled !== false
    localStorage.setItem('token', data.token)
  }

  async function register(name: string, email: string, password: string) {
    const res = await authApi.register({ displayName: name, email, password })
    const data = res.data.data
    token.value = data.token
    userId.value = data.userId
    displayName.value = data.displayName
    avatar.value = data.avatar
    dailyNotifyEnabled.value = data.dailyNotifyEnabled !== false
    localStorage.setItem('token', data.token)
  }

  function logout() {
    token.value = null
    userId.value = null
    displayName.value = null
    avatar.value = null
    dailyNotifyEnabled.value = true
    localStorage.removeItem('token')
  }

  return { token, userId, displayName, avatar, dailyNotifyEnabled, isAuthenticated,
    fetchProfile, updateProfile, uploadAvatar, updateSettings, login, register, logout }
})
