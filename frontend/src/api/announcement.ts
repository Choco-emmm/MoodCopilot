import { api } from './core'

export type Announcement = {
  id: number
  version: number
  title: string
  content: string
  publishedAt: string
  publishedByUserId: number
  publishedByDisplayName?: string
}

export const announcementApi = {
  current: () => api.get<{ data: Announcement | null }>('/admin/announcements/current'),
  publish: (title: string, content: string) =>
    api.post<{ data: Announcement }>('/admin/announcements/publish', { title, content }),
}
