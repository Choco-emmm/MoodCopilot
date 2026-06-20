import axios from 'axios'
import { logError } from '../utils/logger'
import { isUsableToken, clearAuthStorage } from '../utils/auth'

export const api = axios.create({ baseURL: '/api' })

let isRedirectingToLogin = false;

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

      const path = window.location.pathname
      if (path !== '/login' && path !== '/register') {
        if (!isRedirectingToLogin) {
          isRedirectingToLogin = true
          
          if (window.$message) {
            window.$message.error('登录状态已失效，请重新登录')
          }
          
          setTimeout(() => {
            window.location.href = '/login'
          }, 1000)
        }
      }
    }
    return Promise.reject(error)
  },
)
