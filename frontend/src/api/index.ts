import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

function isUsableToken(token: string | null) {
  if (!token) return false
  const normalized = token.trim().toLowerCase()
  return normalized !== '' && normalized !== 'null' && normalized !== 'undefined'
}

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
    const isQuotaRequest = requestUrl.includes('/user/quota')
    if (status === 401 && !isQuotaRequest) {
      localStorage.removeItem('token')
      localStorage.removeItem('role')
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
  update: (id: number, data: { content: string; visibility: string }) => api.put(`/diaries/${id}`, data),
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
  generateWeeklyReport: (weekOffset = 0) => api.post('/diaries/weekly-report/generate', null, { params: { weekOffset } }),
  monthlyReport: (monthOffset = 0) => api.get('/diaries/monthly-report', { params: { monthOffset } }),
  generateMonthlyReport: (monthOffset = 0) => api.post('/diaries/monthly-report/generate', null, { params: { monthOffset } }),
  following: (page = 1, size = 20) => api.get('/diaries/following', { params: { page, size } }),
  delete: (id: number) => api.delete(`/diaries/${id}`),
  deleteComment: (diaryId: number, commentId: number) => api.delete(`/diaries/${diaryId}/comments/${commentId}`),
}

export const reportApi = {
  create: (data: { targetType: 'DIARY' | 'COMMENT'; targetId: number; reason: string }) =>
    api.post('/reports', data),
}

export const adminApi = {
  reports: (status = 'PENDING', page = 1, size = 20) =>
    api.get('/admin/reports', { params: { status, page, size } }),
  resolveReport: (id: number, note?: string) =>
    api.post(`/admin/reports/${id}/resolve`, { note: note ?? '' }),
  rejectReport: (id: number, note?: string) =>
    api.post(`/admin/reports/${id}/reject`, { note: note ?? '' }),
  hideTarget: (id: number, note?: string) =>
    api.post(`/admin/reports/${id}/hide-target`, { note: note ?? '' }),
}

export const notificationApi = {
  list: (page = 1, size = 20) => api.get('/notifications', { params: { page, size } }),
  unreadCount: () => api.get('/notifications/unread-count'),
  markRead: (id: number) => api.put(`/notifications/${id}/read`),
  wsUrl: (token: string) => {
    const env = import.meta.env as Record<string, string | undefined>
    const override = env.VITE_WS_BASE_URL?.trim()
    if (override) {
      return `${override.replace(/\/$/, '')}/ws/notifications?token=${encodeURIComponent(token)}`
    }

    const isLocalHost = /^(localhost|127\.0\.0\.1)$/i.test(window.location.hostname)
    const proxyTarget = env.VITE_API_PROXY_TARGET?.trim()
    if (isLocalHost && proxyTarget) {
      try {
        const target = new URL(proxyTarget)
        const wsProtocol = target.protocol === 'https:' ? 'wss:' : 'ws:'
        return `${wsProtocol}//${target.host}/ws/notifications?token=${encodeURIComponent(token)}`
      } catch {
        // fallback to same-origin when env is malformed
      }
    }

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    return `${protocol}://${window.location.host}/ws/notifications?token=${encodeURIComponent(token)}`
  },
}

export const authApi = {
  sendCode: (email: string) => api.post('/auth/send-code', { email }),
  register: (data: { displayName: string; email: string; password: string; inviteCode: string; verificationCode: string }) =>
    api.post('/auth/register', data),
  login: (data: { email: string; password: string }) => api.post('/auth/login', data),
  me: () => api.get('/auth/me'),
  updateProfile: (data: { displayName?: string; avatar?: string }) =>
    api.post('/auth/update-profile', data),
  uploadAvatar: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return api.post('/auth/avatar', fd)
  },
  updateSettings: (dailyNotifyEnabled: boolean) =>
    api.put('/auth/settings', { dailyNotifyEnabled }),
  getQuota: () => api.get('/user/quota'),
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

export const memoryApi = {
  getAll: () => api.get('/memory'),
  forget: (id: number) => api.delete(`/memory/${id}`),
}

export const chatApi = {
  listConversations: () => api.get('/chat/conversations'),
  createConversation: (title?: string) => api.post('/chat/conversations', { title: title || '' }),
  deleteConversation: (id: number) => api.delete(`/chat/conversations/${id}`),
  getHistory: (id: number) => api.get(`/chat/conversations/${id}/history`),
  saveHistory: (id: number, messages: any[]) => api.put(`/chat/conversations/${id}/history`, { messages }),
  reply: (id: number, message: string, references: string[] = []) =>
    api.post(`/chat/conversations/${id}/reply`, { message, references }),
}
