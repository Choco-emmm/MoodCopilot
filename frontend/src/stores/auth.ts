import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const userId = ref<number | null>(null)
  const displayName = ref<string | null>(null)

  const isAuthenticated = computed(() => !!token.value)

  async function login(email: string, password: string) {
    const res = await authApi.login({ email, password })
    const data = res.data.data
    token.value = data.token
    userId.value = data.userId
    displayName.value = data.displayName
    localStorage.setItem('token', data.token)
  }

  async function register(name: string, email: string, password: string) {
    const res = await authApi.register({ displayName: name, email, password })
    const data = res.data.data
    token.value = data.token
    userId.value = data.userId
    displayName.value = data.displayName
    localStorage.setItem('token', data.token)
  }

  function logout() {
    token.value = null
    userId.value = null
    displayName.value = null
    localStorage.removeItem('token')
  }

  return { token, userId, displayName, isAuthenticated, login, register, logout }
})
