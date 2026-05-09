import { defineStore } from 'pinia'
import { ref } from 'vue'
import { followApi } from '../api'

export const useFollowStore = defineStore('follow', () => {
  const followingMap = ref<Record<number, boolean>>({})
  const loading = ref(false)

  async function checkStatus(userId: number) {
    try {
      const res = await followApi.status(userId)
      followingMap.value[userId] = res.data.data.following
    } catch {
      // ignore
    }
  }

  async function follow(userId: number) {
    loading.value = true
    try {
      await followApi.follow(userId)
      followingMap.value[userId] = true
    } finally {
      loading.value = false
    }
  }

  async function unfollow(userId: number) {
    loading.value = true
    try {
      await followApi.unfollow(userId)
      followingMap.value[userId] = false
    } finally {
      loading.value = false
    }
  }

  function isFollowing(userId: number) {
    return followingMap.value[userId] ?? false
  }

  return { followingMap, loading, checkStatus, follow, unfollow, isFollowing }
})
