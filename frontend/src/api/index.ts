import axios from 'axios'
import { fetchEventSource } from '@microsoft/fetch-event-source'

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
  create: (data: { content: string; visibility: string; musicMeta?: any; images?: string[]; analyze?: boolean }) => api.post('/diaries', data),
  update: (id: number, data: { content: string; visibility: string; isPinned?: boolean; musicMeta?: any; images?: string[] }) => api.put(`/diaries/${id}`, data),
  mine: (page = 1, size = 20) => api.get('/diaries/mine', { params: { page, size } }),
  byUser: (userId: number, page = 1, size = 20) => api.get(`/diaries/user/${userId}`, { params: { page, size } }),
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
  banUserReport: (id: number, note?: string) =>
    api.post(`/admin/reports/${id}/ban-user`, { note: note ?? '' }),
}

export const notificationApi = {
  list: (page = 1, size = 20) => api.get('/notifications', { params: { page, size } }),
  unreadCount: () => api.get('/notifications/unread-count'),
  markRead: (id: number) => api.put(`/notifications/${id}/read`),
  wsTicket: () => api.post('/notifications/ws-ticket'),
  wsUrl: (ticket: string) => {
    const env = import.meta.env as Record<string, string | undefined>
    const override = env.VITE_WS_BASE_URL?.trim()
    if (override) {
      return `${override.replace(/\/$/, '')}/ws/notifications?ticket=${encodeURIComponent(ticket)}`
    }
    // 始终通过当前页面的 host 建立 WebSocket 连接：
    // - 开发环境：Vite 的 /ws 代理（ws: true）会转发到后端
    // - 生产环境：Nginx 的 /ws/ location 会代理到后端
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.host}/ws/notifications?ticket=${encodeURIComponent(ticket)}`
  },
}

export const authApi = {
  sendCode: (email: string) => api.post('/auth/send-code', { email }),
  sendPasswordChangeCode: () => api.post('/auth/change-password/send-code'),
  register: (data: { displayName: string; email: string; password: string; verificationCode: string; turnstileToken?: string }) =>
    api.post('/auth/register', data),
  login: (data: { email: string; password: string; turnstileToken?: string }) => api.post('/auth/login', data),
  changePassword: (data: { oldPassword: string; newPassword: string; confirmNewPassword: string; verificationCode: string }) =>
    api.post('/auth/change-password', data),
  me: () => api.get('/auth/me'),
  profile: (userId: number) => api.get(`/auth/profile/${userId}`),
  updateProfile: (data: { displayName?: string; avatar?: string; signature?: string }) =>
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

export const growthApi = {
  checkIn: () => api.post('/growth/checkin'),
  status: () => api.get('/growth/status'),
  checkins: () => api.get('/growth/checkins'),
  progress: () => api.get('/growth/progress'),
  /** 签到状态（扁平契约，后端高吞吐存储方案就绪后切换到此接口） */
  checkInStatus: () => api.get('/growth/checkin-status'),
}

/** 签到状态扁平契约，待后端对齐 */
export interface CheckInStatus {
  continuousDays: number
  currentMonthTotal: number
  todaySigned: boolean
  nextExpReward: number
}

/** 每日任务条目 */
export interface DailyTaskItem {
  label: string
  field: string
  current: number
  max: number
  expPerAction: number
}

export const taskApi = {
  /** 获取今日任务进度 */
  progress: () => api.get('/growth/progress'),
  /** 签到 */
  checkIn: () => api.post('/growth/checkin'),
  /** 签到状态（后续切换为新契约） */
  checkInStatus: () => api.get('/growth/checkin-status'),
}

export const followApi = {
  follow: (userId: number) => api.post(`/follows/${userId}`),
  unfollow: (userId: number) => api.delete(`/follows/${userId}`),
  status: (userId: number) => api.get(`/follows/${userId}/status`),
}

export const summaryApi = {
  create: (data: { startDate: string; endDate: string }) => api.post('/summaries', data),
  list: (type?: string) => api.get('/summaries', { params: type ? { type } : undefined }),
  delete: (id: number) => api.delete(`/summaries/${id}`),
}

export const supportApi = {
  images: () => api.get('/support-images'),
  uploadImage: (type: string, file: File) => {
    const form = new FormData()
    form.append('type', type)
    form.append('file', file)
    return api.post('/admin/support-images', form)
  },
}

export const memoryApi = {
  getAll: () => api.get('/memory'),
  forget: (id: number) => api.delete(`/memory/${id}`),
  update: (id: number, data: { attributeValue: string }) => api.put(`/memory/${id}`, data),
}

export const imageApi = {
  upload: (file: File, compress = true) => {
    const fd = new FormData()
    fd.append('file', file)
    return api.post('/images/upload', fd, {
      params: { compress },
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /** 获取 OSS 直传策略 */
  uploadPolicy: (ext: string) =>
    api.post('/images/upload-policy', null, { params: { ext } }),

  /** 浏览器直传 OSS，文件不经服务器 */
  uploadDirect: async (file: File): Promise<string> => {
    const ext = file.name.includes('.') ? file.name.substring(file.name.lastIndexOf('.')) : '.jpg'
    const policyRes = await imageApi.uploadPolicy(ext)
    const policy = policyRes.data?.data
    if (!policy) throw new Error('获取上传策略失败')

    const fd = new FormData()
    fd.append('OSSAccessKeyId', policy.accessId)
    fd.append('policy', policy.policy)
    fd.append('signature', policy.signature)
    fd.append('key', policy.key)
    fd.append('success_action_status', '200')
    fd.append('file', file)

    await fetch(policy.host, { method: 'POST', body: fd })
    return policy.url
  },
}

export interface OssPolicy {
  host: string
  accessId: string
  policy: string
  signature: string
  key: string
  url: string
  expireMs: number
}

export const musicApi = {
  parse: (url: string, text?: string) => api.post('/music/parse', { url, text: text || '' }),
  lyrics: (url: string, title: string, artist: string) => api.post('/music/lyrics', { url, title, artist }),
}

export const chatApi = {
  listConversations: () => api.get('/chat/conversations'),
  createConversation: (title?: string) => api.post('/chat/conversations', { title: title || '' }),
  deleteConversation: (id: number) => api.delete(`/chat/conversations/${id}`),
  getHistory: (id: number) => api.get(`/chat/conversations/${id}/history`),
  saveHistory: (id: number, messages: any[]) => api.put(`/chat/conversations/${id}/history`, { messages }),
  reply: (id: number, message: string, references: string[] = []) =>
    api.post(`/chat/conversations/${id}/reply`, { message, references }),
  /** SSE 流式请求（JSON Chunk 协议） */
  replyStream: (
    id: number,
    message: string,
    references: string[],
    onChunk: (text: string) => void,
    ctrl: AbortController,
    onReferences?: (items: Array<{ type: string; date: string; snippet: string }>) => void,
  ): Promise<void> => {
    const token = localStorage.getItem('token')
    return fetchEventSource(`/api/chat/conversations/${id}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ message, references }),
      signal: ctrl.signal,
      openWhenHidden: true,
      onmessage(event) {
        const raw = event.data
        try {
          const msg = JSON.parse(raw)
          if (msg.type === 'references') {
            onReferences?.(msg.items ?? [])
          } else if (msg.type === 'chunk') {
            onChunk(msg.content ?? '')
          }
          // 'done' 类型无需处理
        } catch {
          // 兼容旧格式：纯文本 chunk
          if (raw !== '[DONE]') onChunk(raw)
        }
      },
      onerror(err) {
        throw err
      },
    })
  },
}
