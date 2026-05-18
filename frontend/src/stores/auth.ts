import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api'
import { normalizeResourceUrl } from '../utils/resource'

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
  const email = ref<string | null>(null)
  const avatar = ref<string | null>(null)
  const signature = ref<string | null>(null)
  const dailyNotifyEnabled = ref<boolean>(true)
  const role = ref<string>(localStorage.getItem('role') || 'USER')
  const inviteCode = ref<string | null>(null)
  const inviteQuota = ref<number>(0)

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')

  async function fetchProfile() {
    try {
      const res = await authApi.me()
      const data = res.data.data
      userId.value = data.userId
      displayName.value = data.displayName
      email.value = data.email ?? null
      avatar.value = normalizeResourceUrl(data.avatar)
      signature.value = data.signature ?? null
      dailyNotifyEnabled.value = data.dailyNotifyEnabled !== false
      inviteCode.value = data.inviteCode ?? null
      inviteQuota.value = data.inviteQuota ?? 0
      saveRole(data.role)
    } catch { /* ignore */ }
  }

  async function updateProfile(name?: string, avatarUrl?: string, signatureText?: string) {
    const res = await authApi.updateProfile({ displayName: name, avatar: avatarUrl, signature: signatureText })
    const data = res.data.data
    displayName.value = data.displayName
    avatar.value = normalizeResourceUrl(data.avatar)
    signature.value = data.signature ?? null
    saveRole(data.role)
  }

  async function uploadAvatar(file: File) {
    const res = await authApi.uploadAvatar(file)
    avatar.value = normalizeResourceUrl(res.data.data.avatar)
  }

  async function updateSettings(enabled: boolean) {
    await authApi.updateSettings(enabled)
    dailyNotifyEnabled.value = enabled
  }

  function applyAuthData(data: any) {
    token.value = data.token
    userId.value = data.userId
    displayName.value = data.displayName
    email.value = data.email ?? null
    avatar.value = normalizeResourceUrl(data.avatar)
    signature.value = data.signature ?? null
    dailyNotifyEnabled.value = data.dailyNotifyEnabled !== false
    inviteCode.value = data.inviteCode ?? null
    inviteQuota.value = data.inviteQuota ?? 0
    saveRole(data.role)
    if (data.token) localStorage.setItem('token', data.token)
  }

  async function login(email: string, password: string) {
    const res = await authApi.login({ email, password })
    applyAuthData(res.data.data)
  }

  async function sendCode(email: string) {
    await authApi.sendCode(email)
  }

  async function sendPasswordChangeCode() {
    await authApi.sendPasswordChangeCode()
  }

  async function register(name: string, email: string, password: string, inviteCodeParam: string, verificationCode: string) {
    const res = await authApi.register({ displayName: name, email, password, inviteCode: inviteCodeParam, verificationCode })
    applyAuthData(res.data.data)
  }

  async function changePassword(oldPassword: string, newPassword: string, confirmNewPassword: string, verificationCode: string) {
    await authApi.changePassword({ oldPassword, newPassword, confirmNewPassword, verificationCode })
  }

  function logout() {
    token.value = null
    userId.value = null
    displayName.value = null
    email.value = null
    avatar.value = null
    signature.value = null
    dailyNotifyEnabled.value = true
    role.value = 'USER'
    localStorage.removeItem('token')
    localStorage.removeItem('role')
  }

  function saveRole(value?: string) {
    role.value = value || 'USER'
    localStorage.setItem('role', role.value)
  }

  return {
    token, userId, displayName, email, avatar, signature, dailyNotifyEnabled, role, inviteCode, inviteQuota, isAuthenticated, isAdmin,
    fetchProfile, updateProfile, uploadAvatar, updateSettings, login, register, logout, sendCode, sendPasswordChangeCode, changePassword
  }
})
