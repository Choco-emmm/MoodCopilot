import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api'

function getInitialToken() {
  const token = localStorage.getItem('token')
  if (!token) return null
  const normalized = token.trim().toLowerCase()
  if (normalized === '' || normalized === 'null' || normalized === 'undefined') {
    localStorage.removeItem('token')
    return null
  }
  return token
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getInitialToken())
  const userId = ref<number | null>(null)
  const displayName = ref<string | null>(null)
  const avatar = ref<string | null>(null)
  const dailyNotifyEnabled = ref<boolean>(true)
  const role = ref<string>(localStorage.getItem('role') || 'USER')

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')

  async function fetchProfile() {
    try {
      const res = await authApi.me()
      const data = res.data.data
      userId.value = data.userId
      displayName.value = data.displayName
      avatar.value = data.avatar
      dailyNotifyEnabled.value = data.dailyNotifyEnabled !== false
      saveRole(data.role)
    } catch { /* ignore */ }
  }

  async function updateProfile(name?: string, avatarUrl?: string) {
    const res = await authApi.updateProfile({ displayName: name, avatar: avatarUrl })
    const data = res.data.data
    displayName.value = data.displayName
    avatar.value = data.avatar
    saveRole(data.role)
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
    saveRole(data.role)
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
    saveRole(data.role)
    localStorage.setItem('token', data.token)
  }

  function logout() {
    token.value = null
    userId.value = null
    displayName.value = null
    avatar.value = null
    dailyNotifyEnabled.value = true
    role.value = 'USER'
    localStorage.removeItem('token')
    localStorage.removeItem('role')
  }

  function saveRole(value?: string) {
    role.value = value || 'USER'
    localStorage.setItem('role', role.value)
  }

  return { token, userId, displayName, avatar, dailyNotifyEnabled, role, isAuthenticated, isAdmin,
    fetchProfile, updateProfile, uploadAvatar, updateSettings, login, register, logout }
})
