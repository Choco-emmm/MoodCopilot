import { api } from './core'

export interface DiaryImageMetaPayload {
  url: string
  channel: 'normal' | 'text' | 'legacy'
  origWidth?: number
  origHeight?: number
  compressedWidth?: number
  compressedHeight?: number
  origSize?: number
  compressedSize?: number
  quality?: number
  mime?: string
}

export const diaryApi = {
  create: (data: { content: string; visibility: string; musicMeta?: any; images?: string[]; imageMeta?: DiaryImageMetaPayload[]; analyze?: boolean; useReasoning?: boolean }) => api.post('/diaries', data),
  update: (id: number, data: { content: string; visibility: string; isPinned?: boolean; musicMeta?: any; images?: string[]; imageMeta?: DiaryImageMetaPayload[]; analyze?: boolean; useReasoning?: boolean }) => api.put(`/diaries/${id}`, data),
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
  search: (params: { keyword?: string; startDate?: string; endDate?: string; visibility?: string; page?: number; size?: number }) =>
    api.get('/diaries/search', { params }),
}

export const memoryApi = {
  getAll: () => api.get('/memory'),
  getDetail: (id: number) => api.get(`/memory/${id}`),
  getCandidates: (status = 'PENDING', page = 1, size = 20, sort = 'updatedAt') => api.get('/memory/candidates', { params: { status, page, size, sort } }),
  approveCandidate: (id: number) => api.post(`/memory/candidates/${id}/approve`),
  rejectCandidate: (id: number) => api.post(`/memory/candidates/${id}/reject`),
  getHistory: (id: number) => api.get(`/memory/${id}/history`),
  getEvidence: (id: number) => api.get(`/memory/${id}/evidence`),
  forget: (id: number) => api.delete(`/memory/${id}`),
  update: (id: number, data: { attributeValue: string; isCore?: boolean }) => api.put(`/memory/${id}`, data),
  previewConsolidate: () => api.post('/memory/consolidate/preview'),
  applyConsolidate: (data: any) => api.post('/memory/consolidate/apply', data),
}

export const graphApi = {
  getUserGraph: () => api.get('/graph/user-graph'),
  getTriples: () => api.get('/graph/triples'),
  updateTriple: (id: number, data: { headEntity: string; relation: string; tailEntity: string }) => api.put(`/graph/triples/${id}`, data),
  deleteTriple: (id: number) => api.delete(`/graph/triples/${id}`),
  previewConsolidate: () => api.post('/graph/consolidate/preview'),
  applyConsolidate: (data: any) => api.post('/graph/consolidate/apply', data)
}
