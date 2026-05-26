import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api'
import { normalizeResourceUrl } from '../utils/resource'
import { logWarn } from '../utils/logger'
import { getStoredToken, clearAuthStorage } from '../utils/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getStoredToken())
  const userId = ref<number | null>(null)
  const displayName = ref<string | null>(null)
  const email = ref<string | null>(null)
  const avatar = ref<string | null>(null)
  const signature = ref<string | null>(null)
  const theme = ref<string>('green')
  const lightTheme = ref<string>('green')
  const darkTheme = ref<string>('minimal-dark')
  const themeMode = ref<string>('auto')
  const dailyNotifyEnabled = ref<boolean>(true)
  const profileNotifyEnabled = ref<boolean>(true)
  const role = ref<string>(localStorage.getItem('role') || 'USER')
  const exp = ref<number>(0)
  const level = ref<number>(1)
  const proExpireTime = ref<string | null>(null)
  const nameChangeCount = ref<number>(0)
  const nameChangeWeek = ref<number>(0)
  const maxWeeklyNameChanges = 3

  const remainingNameChanges = computed(() => {
    const now = new Date()
    const year = now.getFullYear()
    // ISO week: Monday=1, find the week that contains Thursday
    const jan4 = new Date(year, 0, 4)
    const jan4Day = jan4.getDay() || 7
    const firstMonday = new Date(jan4)
    firstMonday.setDate(jan4.getDate() - (jan4Day - 1))
    const daysSinceFirstMonday = Math.floor((now.getTime() - firstMonday.getTime()) / 86400000)
    const currentWeek = Math.floor(daysSinceFirstMonday / 7) + 1
    const currentWeekKey = year * 100 + currentWeek
    if (nameChangeWeek.value !== currentWeekKey) return maxWeeklyNameChanges
    return Math.max(0, maxWeeklyNameChanges - nameChangeCount.value)
  })

  const isPro = computed(() => {
    if (!proExpireTime.value) return false
    return new Date(proExpireTime.value) > new Date()
  })
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
      // 主题字段仅在 API 明确返回时才覆盖，避免 fetchProfile 把用户设置冲掉
      if (data.lightTheme != null) lightTheme.value = data.lightTheme
      if (data.darkTheme != null) darkTheme.value = data.darkTheme
      if (data.themeMode != null) themeMode.value = data.themeMode
      dailyNotifyEnabled.value = data.dailyNotifyEnabled !== false
      profileNotifyEnabled.value = data.profileNotifyEnabled !== false
      exp.value = data.exp ?? 0
      level.value = data.level ?? 1
      proExpireTime.value = data.proExpireTime ?? null
      nameChangeCount.value = data.nameChangeCount ?? 0
      nameChangeWeek.value = data.nameChangeWeek ?? 0
      saveRole(data.role)
    } catch (e) { logWarn('auth', 'fetchProfile 失败', e) }
  }

  async function updateProfile(name?: string, avatarUrl?: string, signatureText?: string) {
    const res = await authApi.updateProfile({ displayName: name, avatar: avatarUrl, signature: signatureText })
    const data = res.data.data
    displayName.value = data.displayName
    avatar.value = normalizeResourceUrl(data.avatar)
    signature.value = data.signature ?? null
    nameChangeCount.value = data.nameChangeCount ?? 0
    nameChangeWeek.value = data.nameChangeWeek ?? 0
    saveRole(data.role)
  }

  async function uploadAvatar(file: File) {
    const res = await authApi.uploadAvatar(file)
    avatar.value = normalizeResourceUrl(res.data.data.avatar)
  }

  async function updateSettings(
    daily: boolean, profile?: boolean,
    newTheme?: string, newThemeMode?: string, newLightTheme?: string, newDarkTheme?: string
  ) {
    await authApi.updateSettings(daily, profile, newTheme, newThemeMode, newLightTheme, newDarkTheme)
    dailyNotifyEnabled.value = daily
    if (profile !== undefined) profileNotifyEnabled.value = profile
    if (newTheme !== undefined) theme.value = newTheme
    if (newThemeMode !== undefined) themeMode.value = newThemeMode
    if (newLightTheme !== undefined) lightTheme.value = newLightTheme
    if (newDarkTheme !== undefined) darkTheme.value = newDarkTheme
  }

  function applyAuthData(data: any) {
    token.value = data.token
    userId.value = data.userId
    displayName.value = data.displayName
    email.value = data.email ?? null
    avatar.value = normalizeResourceUrl(data.avatar)
    signature.value = data.signature ?? null
    dailyNotifyEnabled.value = data.dailyNotifyEnabled !== false
    profileNotifyEnabled.value = data.profileNotifyEnabled !== false
    lightTheme.value = data.lightTheme ?? 'green'
    darkTheme.value = data.darkTheme ?? 'minimal-dark'
    themeMode.value = data.themeMode ?? 'auto'
    exp.value = data.exp ?? 0
    level.value = data.level ?? 1
    proExpireTime.value = data.proExpireTime ?? null
    nameChangeCount.value = data.nameChangeCount ?? 0
    nameChangeWeek.value = data.nameChangeWeek ?? 0
    saveRole(data.role)
    if (data.token) localStorage.setItem('token', data.token)
  }

  async function login(email: string, password: string, captchaToken?: string) {
    const res = await authApi.login({ email, password, captchaToken })
    applyAuthData(res.data.data)
  }

  async function sendCode(email: string) {
    await authApi.sendCode(email)
  }

  async function sendPasswordChangeCode() {
    await authApi.sendPasswordChangeCode()
  }

  async function register(name: string, email: string, password: string, verificationCode: string, captchaToken?: string) {
    const res = await authApi.register({ displayName: name, email, password, verificationCode, captchaToken })
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
    profileNotifyEnabled.value = true
    role.value = 'USER'
    nameChangeCount.value = 0
    nameChangeWeek.value = 0
    clearAuthStorage()
  }

  function saveRole(value?: string) {
    role.value = value || 'USER'
    localStorage.setItem('role', role.value)
  }

  return {
    token, userId, displayName,    email,
    avatar,
    signature,
    theme, lightTheme, darkTheme, themeMode,
    dailyNotifyEnabled, profileNotifyEnabled, role, exp, level, proExpireTime, nameChangeCount, nameChangeWeek, maxWeeklyNameChanges, remainingNameChanges, isPro, isAuthenticated, isAdmin,
    fetchProfile, updateProfile, uploadAvatar, updateSettings, login, register, logout, sendCode, sendPasswordChangeCode, changePassword
  }
})
