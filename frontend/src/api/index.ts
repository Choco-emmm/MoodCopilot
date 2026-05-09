import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 || error.response?.status === 403) {
      localStorage.removeItem('token')
      const path = window.location.pathname
      if (path !== '/login' && path !== '/register') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)

export const diaryApi = {
  create: (data: { content: string; visibility: string }) => api.post('/diaries', data),
  mine: () => api.get('/diaries/mine'),
  public: (page = 1, size = 20) => api.get('/diaries/public', { params: { page, size } }),
  get: (id: number) => api.get(`/diaries/${id}`),
  similar: (id: number, limit = 3) => api.get(`/diaries/${id}/similar`, { params: { limit } }),
  addComment: (id: number, content: string, parentCommentId?: number) =>
    api.post(`/diaries/${id}/comments`, { content, parentCommentId: parentCommentId ?? null }),
  resonate: (id: number) => api.post(`/diaries/${id}/resonance`),
  weeklyReport: (weekOffset = 0) => api.get('/diaries/weekly-report', { params: { weekOffset } }),
}

export const notificationApi = {
  list: (page = 1, size = 20) => api.get('/notifications', { params: { page, size } }),
  unreadCount: () => api.get('/notifications/unread-count'),
  markRead: (id: number) => api.put(`/notifications/${id}/read`),
}

export const authApi = {
  register: (data: { displayName: string; email: string; password: string }) =>
    api.post('/auth/register', data),
  login: (data: { email: string; password: string }) => api.post('/auth/login', data),
  me: () => api.get('/auth/me'),
}
