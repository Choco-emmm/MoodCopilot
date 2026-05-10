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
  mine: (page = 1, size = 20) => api.get('/diaries/mine', { params: { page, size } }),
  public: (page = 1, size = 20) => api.get('/diaries/public', { params: { page, size } }),
  get: (id: number) => api.get(`/diaries/${id}`),
  similar: (id: number, limit = 3) => api.get(`/diaries/${id}/similar`, { params: { limit } }),
  addComment: (id: number, content: string, parentCommentId?: number) =>
    api.post(`/diaries/${id}/comments`, { content, parentCommentId: parentCommentId ?? null }),
  resonate: (id: number) => api.post(`/diaries/${id}/resonance`),
  todayStatus: () => api.get('/diaries/today-status'),
  todayMatch: () => api.get('/diaries/today-match'),
  coaching: () => api.get('/diaries/coaching'),
  communityMood: () => api.get('/diaries/community-mood'),
  encourageCandidates: (id: number) => api.get(`/diaries/${id}/encourage-candidates`),
  sendEncouragement: (id: number, message: string) => api.post(`/diaries/${id}/resonance`, { message }),
  weeklyReport: (weekOffset = 0) => api.get('/diaries/weekly-report', { params: { weekOffset } }),
  monthlyReport: (monthOffset = 0) => api.get('/diaries/monthly-report', { params: { monthOffset } }),
  following: (page = 1, size = 20) => api.get('/diaries/following', { params: { page, size } }),
  delete: (id: number) => api.delete(`/diaries/${id}`),
  deleteComment: (diaryId: number, commentId: number) => api.delete(`/diaries/${diaryId}/comments/${commentId}`),
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
  updateProfile: (data: { displayName?: string; avatar?: string }) =>
    api.post('/auth/update-profile', data),
  uploadAvatar: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return api.post('/auth/avatar', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  updateSettings: (dailyNotifyEnabled: boolean) =>
    api.put('/auth/settings', { dailyNotifyEnabled }),
  getQuota: () => api.get('/auth/quota'),
}

export const followApi = {
  follow: (userId: number) => api.post(`/follows/${userId}`),
  unfollow: (userId: number) => api.delete(`/follows/${userId}`),
  status: (userId: number) => api.get(`/follows/${userId}/status`),
}

export const summaryApi = {
  create: (data: { startDate: string; endDate: string }) => api.post('/summaries', data),
  list: () => api.get('/summaries'),
  delete: (id: number) => api.delete(`/summaries/${id}`),
}

export const chatApi = {
  listConversations: () => api.get('/chat/conversations'),
  createConversation: (title?: string) => api.post('/chat/conversations', { title: title || '' }),
  deleteConversation: (id: number) => api.delete(`/chat/conversations/${id}`),
  getHistory: (id: number) => api.get(`/chat/conversations/${id}/history`),
  saveHistory: (id: number, messages: any[]) => api.put(`/chat/conversations/${id}/history`, { messages }),
}
