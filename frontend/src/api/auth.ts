import { api } from './core'

export const authApi = {
  checkUsername: (username: string) => api.get('/auth/check-username', { params: { username } }),
  checkEmail: (email: string) => api.get('/auth/check-email', { params: { email } }),
  sendCode: (email: string) => api.post('/auth/send-code', { email }),
  sendPasswordChangeCode: () => api.post('/auth/change-password/send-code'),
  register: (data: { displayName: string; email: string; password: string; verificationCode: string; captchaToken?: string }) =>
    api.post('/auth/register', data),
  login: (data: { email: string; password: string; captchaToken?: string }) => api.post('/auth/login', data),
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
  updateSettings: (dailyNotifyEnabled: boolean, profileNotifyEnabled?: boolean, theme?: string, themeMode?: string, lightTheme?: string, darkTheme?: string) =>
    api.put('/auth/settings', { dailyNotifyEnabled, profileNotifyEnabled, theme, themeMode, lightTheme, darkTheme }),
  getQuota: () => api.get('/user/quota'),
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
  users: (keyword?: string, sortBy = 'lastActiveTime', page = 1, size = 20) =>
    api.get('/admin/users', { params: { keyword, sortBy, page, size } }),
  updateUserStatus: (id: number, status: number) =>
    api.post(`/admin/users/${id}/status`, { status }),
}

export const reportApi = {
  create: (data: { targetType: 'DIARY' | 'COMMENT'; targetId: number; reason: string }) =>
    api.post('/reports', data),
}

export const notificationApi = {
  list: (page = 1, size = 20) => api.get('/notifications', { params: { page, size } }),
  unreadCount: () => api.get('/notifications/unread-count'),
  markRead: (id: number) => api.put(`/notifications/${id}/read`),
  markAllRead: () => api.put('/notifications/read-all'),
  wsTicket: () => api.post('/notifications/ws-ticket'),
  wsUrl: (ticket: string) => {
    const env = import.meta.env as Record<string, string | undefined>
    const override = env.VITE_WS_BASE_URL?.trim()
    if (override) {
      return `${override.replace(/\/$/, '')}/ws/notifications?ticket=${encodeURIComponent(ticket)}`
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.host}/ws/notifications?ticket=${encodeURIComponent(ticket)}`
  },
}
