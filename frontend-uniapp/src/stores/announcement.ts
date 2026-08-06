import { computed, ref } from 'vue'
import { get } from '@/utils/request'

export type Announcement = {
  id: number
  version: number
  title: string
  content: string
  publishedAt: string
}

export const activeAnnouncement = ref<Announcement | null>(null)
export const announcementVisible = ref(false)
const announcementUserId = ref<number | null>(null)

const guestKey = (version: number) => `announcement:guest:${version}`
const userKey = (userId: number, version: number) => `announcement:user:${userId}:${version}`

export const announcementAcknowledgementKey = computed(() => {
  if (!activeAnnouncement.value) return ''
  return announcementUserId.value == null
    ? guestKey(activeAnnouncement.value.version)
    : userKey(announcementUserId.value, activeAnnouncement.value.version)
})

export async function loadActiveAnnouncement() {
  try {
    const response = await get<Announcement | null>('/api/announcements/active')
    activeAnnouncement.value = response.code === 200 ? response.data : null
    showAnnouncementIfNeeded()
  } catch (error) {
    // The announcement is optional and must never block the entry screen.
    console.warn('获取公告失败', error)
  }
}

export function setAnnouncementUserId(userId: number | null) {
  announcementUserId.value = userId && userId > 0 ? userId : null
  showAnnouncementIfNeeded()
}

export function showAnnouncementIfNeeded() {
  const announcement = activeAnnouncement.value
  if (!announcement) return

  const currentKey = announcementAcknowledgementKey.value
  const guestAcknowledged = Boolean(uni.getStorageSync(guestKey(announcement.version)))
  const acknowledged = currentKey ? Boolean(uni.getStorageSync(currentKey)) : false
  announcementVisible.value = !acknowledged && !(announcementUserId.value != null && guestAcknowledged)
}

export function closeAnnouncement() {
  const key = announcementAcknowledgementKey.value
  if (key) uni.setStorageSync(key, true)
  announcementVisible.value = false
}

export function acknowledgeGuestAnnouncementForUser(userId: number) {
  const announcement = activeAnnouncement.value
  if (!announcement || !uni.getStorageSync(guestKey(announcement.version))) return
  uni.setStorageSync(userKey(userId, announcement.version), true)
  announcementVisible.value = false
}
