import { defineStore } from 'pinia'
import { ref } from 'vue'
import { followApi } from '../api'

export const useFollowStore = defineStore('follow', () => {
  const followingMap = ref<Record<number, boolean>>({})
  const pendingMap = ref<Record<number, boolean>>({})

  async function checkStatus(userId: number) {
    try {
      const res = await followApi.status(userId)
      followingMap.value[userId] = res.data.data.following
    } catch {
      // ignore
    }
  }

  async function follow(userId: number) {
    if (pendingMap.value[userId]) return
    pendingMap.value[userId] = true
    try {
      await followApi.follow(userId)
      followingMap.value[userId] = true
    } finally {
      pendingMap.value[userId] = false
    }
  }

  async function unfollow(userId: number) {
    if (pendingMap.value[userId]) return
    pendingMap.value[userId] = true
    try {
      await followApi.unfollow(userId)
      followingMap.value[userId] = false
    } finally {
      pendingMap.value[userId] = false
    }
  }

  function isFollowing(userId: number) {
    return followingMap.value[userId] ?? false
  }

  function isPending(userId: number) {
    return pendingMap.value[userId] ?? false
  }

  return { followingMap, pendingMap, checkStatus, follow, unfollow, isFollowing, isPending }
})
