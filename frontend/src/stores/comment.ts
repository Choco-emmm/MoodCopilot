import { defineStore } from 'pinia'
import { diaryApi } from '../api'
import { tryExpToast } from '../utils/toast'

export interface DiaryComment {
  id: number
  parentCommentId: number | null
  replyToUserName: string | null
  authorName: string
  content: string
  createdAt: string
  replies: DiaryComment[]
}

export const useCommentStore = defineStore('comment', () => {

  async function addComment(diaryId: number, content: string, parentCommentId?: number) {
    const res = await diaryApi.addComment(diaryId, content, parentCommentId)
    tryExpToast('comment', '回复 +3 EXP')
    return res.data.data
  }

  async function deleteComment(diaryId: number, commentId: number) {
    const res = await diaryApi.deleteComment(diaryId, commentId)
    return res?.data?.data ?? null
  }

  function removeCommentFromTree(comments: DiaryComment[], commentId: number): DiaryComment[] {
    return comments
      .filter((comment) => comment.id !== commentId)
      .map((comment) => ({
        ...comment,
        replies: removeCommentFromTree(comment.replies || [], commentId),
      }))
  }

  return {
    addComment,
    deleteComment,
    removeCommentFromTree,
  }
})
