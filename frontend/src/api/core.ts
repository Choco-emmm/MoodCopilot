import axios from 'axios'
import { logError } from '../utils/logger'
import { isUsableToken, clearAuthStorage } from '../utils/auth'

export const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (isUsableToken(token)) {
    config.headers.Authorization = `Bearer ${token}`
  } else {
    localStorage.removeItem('token')
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const requestUrl = String(error.config?.url ?? '')
    const method = String(error.config?.method ?? '').toUpperCase()
    const msg = error.response?.data?.message || error.message || '未知错误'
    logError('api', `${method} ${requestUrl} → ${status ?? 'NET_ERR'}: ${msg}`)
    const isQuotaRequest = requestUrl.includes('/user/quota')
    if (status === 401 && !isQuotaRequest) {
      clearAuthStorage()

      if (window.$message) {
        window.$message.error('登录状态已失效，请重新登录')
      }

      const path = window.location.pathname
      if (path !== '/login' && path !== '/register') {
        import('../stores/auth').then(({ useAuthStore }) => {
          useAuthStore().logout()
        }).catch(() => {})

        import('../router').then(({ default: router }) => {
          router.push('/login')
        }).catch(() => {
          window.location.replace('/login')
        })
      }
    }
    return Promise.reject(error)
  },
)
