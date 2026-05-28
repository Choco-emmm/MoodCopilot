import { api } from './core'

export const collectionApi = {
  /**
   * 创建合集
   */
  create: (data: { name: string; description?: string; coverUrl?: string; visibility?: string }) =>
    api.post('/collections', data),

  /**
   * 获取单个合集详情
   */
  get: (collectionId: number) => api.get(`/collections/${collectionId}`),

  /**
   * 获取当前用户的合集列表
   */
  mine: (page = 1, size = 10) => api.get('/collections/mine', { params: { page, size } }),

  /**
   * 获取指定用户的公开合集列表
   */
  byUser: (userId: number, page = 1, size = 10) =>
    api.get(`/collections/user/${userId}`, { params: { page, size } }),

  /**
   * 更新合集
   */
  update: (collectionId: number, data: { name: string; description?: string; coverUrl?: string; visibility?: string }) =>
    api.put(`/collections/${collectionId}`, data),

  /**
   * 删除合集
   */
  delete: (collectionId: number) => api.delete(`/collections/${collectionId}`),

  /**
   * 添加日记到合集
   */
  addDiaries: (collectionId: number, diaryIds: number[]) =>
    api.post(`/collections/${collectionId}/diaries`, { diaryIds }),

  /**
   * 从合集移除日记
   */
  removeDiaries: (collectionId: number, diaryIds: number[]) =>
    api.delete(`/collections/${collectionId}/diaries`, { params: { diaryIds } }),

  /**
   * 获取合集内的日记列表
   */
  diaries: (collectionId: number, page = 1, size = 10, sortBy: 'ADDED_TIME_DESC' | 'DIARY_CREATE_TIME_ASC' | 'DIARY_CREATE_TIME_DESC' = 'ADDED_TIME_DESC') =>
    api.get(`/collections/${collectionId}/diaries`, { params: { page, size, sortBy } }),

  /**
   * 检查日记是否在指定合集中
   */
  checkDiaryExists: (collectionId: number, diaryId: number) =>
    api.get(`/collections/${collectionId}/diaries/${diaryId}/exists`),

  /**
   * 更新日记在合集中的排序顺序
   */
  updateDiarySortOrder: (collectionId: number, diaryId: number, prevSortOrder: number | null, nextSortOrder: number | null) =>
    api.put(`/collections/${collectionId}/diaries/${diaryId}/sort`, {
      prevSortOrder,
      nextSortOrder
    }),

  /**
   * 获取日记所属的合集列表
   */
  byDiary: (diaryId: number) => api.get(`/collections/by-diary/${diaryId}`),
}
