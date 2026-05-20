<template>
  <main class="app-shell">
    <AppHeader />
    <div v-if="diary" class="diary-detail-page">
      <!-- 日记正文 -->
      <article class="panel analysis-panel">
        <div class="diary-content-section">
          <div class="diary-author-row">
            <img v-if="diary.authorAvatar" :src="diary.authorAvatar" class="avatar avatar-img" decoding="async" />
            <span v-else class="avatar">{{ diary.authorName.charAt(0) }}</span>
            <n-button text class="author-name-link" @click="openAuthorProfile">{{ diary.authorName }}</n-button>
            <span class="diary-time">{{ formatTime(diary.createdAt) }}</span>
            <n-tag :type="diary.visibility === 'PUBLIC' ? 'success' : 'default'" round size="small">
              {{ diary.visibility === 'PUBLIC' ? '公开' : '私密' }}
            </n-tag>
            <n-button v-if="isOwner" size="tiny" text @click="router.push('/write?edit=' + diary.id)">编辑</n-button>
            <n-button v-if="!isOwner" size="tiny" text @click="reportDiary">举报</n-button>
            <n-button
              v-if="auth.isAdmin"
              size="tiny"
              type="warning"
              text
              :loading="pinning"
              @click="togglePin"
              style="margin-left: 8px; font-weight: bold;"
            >
              {{ diary.isPinned ? '📌 取消置顶' : '📌 置顶' }}
            </n-button>
            <n-button
              v-if="isOwner"
              size="tiny"
              type="error"
              text
              @click="handleDeleteDiary"
              style="margin-left: 8px; font-weight: bold;"
            >
              🗑️ 删除日记
            </n-button>
            <n-button
              v-if="auth.isAdmin && !isOwner"
              size="tiny"
              type="error"
              text
              @click="handleAdminForceDelete"
              style="margin-left: 8px; font-weight: bold;"
            >
              🗑️ 管理员删除
            </n-button>
            <n-button
              v-if="!isOwner"
              size="tiny"
              :type="followStore.isFollowing(diary.authorUserId) ? 'default' : 'primary'"
              :secondary="!followStore.isFollowing(diary.authorUserId)"
              :loading="followStore.isPending(diary.authorUserId)"
              :disabled="followStore.isPending(diary.authorUserId)"
              @click="handleFollow"
              style="margin-left: 8px;"
            >
              {{ followStore.isFollowing(diary.authorUserId) ? '已关注' : '关注' }}
            </n-button>
          </div>
          <p class="diary-content">{{ diary.content }}</p>

          <MusicCard
            v-if="diary.musicMeta"
            :music-meta="diary.musicMeta"
            :lyric="diary.musicMeta.userLyric"
            :song-url="diary.musicMeta.songUrl"
            expandable-lyric
          />
          <ImageGallery v-if="diary.images?.length" :images="diary.images" />

          <div class="detail-actions">
            <n-button
              size="small"
              tertiary
              :class="['like-btn', { liked: diary.likedByMe, 'just-liked': justLiked }]"
              :disabled="resonating"
              @click="resonateDiary"
            >
              <span class="like-btn-icon" v-html="diary.likedByMe ? thumbsUpFilled : thumbsUpOutline" />
              <span class="like-btn-count">{{ diary.resonanceCount ?? 0 }}</span>
            </n-button>
          </div>
        </div>

        <!-- 本人的 AI 分析 -->
        <template v-if="isOwner">
          <div class="section-divider" />
          <AiAnalysisCard :diary="diary" :compact="true" />
        </template>
      </article>

      <!-- 评论区域 -->
      <section class="panel analysis-panel">
        <h3 class="comment-section-title">评论 ({{ diary.comments?.length ?? 0 }})</h3>

        <!-- 写评论 -->
        <div v-if="!replyThreadId" class="comment-box">
          <n-input
            v-model:value="commentDraft"
            placeholder="写下你的回应..."
            :disabled="sending"
            clearable
            @focus="handleCommentFocus"
            @blur="handleCommentBlur"
            @keyup.enter="submitComment(null)"
          />
          <n-button type="primary" size="small" :disabled="!commentDraft.trim() || sending" @click="submitComment(null)">
            发送
          </n-button>
        </div>
        <div v-else class="reply-mode-hint">
          正在回复 {{ replyTargetName }}，请在对应评论下方输入。
          <n-button size="tiny" text @click="cancelReply">退出回复</n-button>
        </div>
        <div v-if="commentError" class="comment-error">{{ commentError }}</div>

        <!-- 评论列表 -->
        <div v-if="diary.comments?.length" class="comments">
          <div v-for="c in diary.comments" :key="c.id" class="comment-thread">
            <!-- 根评论 -->
            <div class="comment-main">
              <div class="comment-text">
                <strong>{{ c.authorName }}</strong>
                <span class="comment-time">{{ formatTime(c.createdAt) }}</span>
              </div>
              <p class="comment-body">{{ c.content }}</p>
              <div class="comment-foot">
                <n-button v-if="canDeleteComment(c)" size="tiny" text type="error" :disabled="deleting" @click="deleteComment(c.id)">删除</n-button>
                <n-button size="tiny" text @click="startReply(c.id, c.id, c.authorName)">回复</n-button>
                <n-button size="tiny" text @click="reportComment(c.id)">举报</n-button>
              </div>
            </div>

            <!-- 回复列表 -->
            <div v-if="c.replies?.length" class="comment-replies">
              <div v-for="r in c.replies" :key="r.id" class="reply-item">
                <div class="comment-text">
                  <strong>{{ r.authorName }}</strong>
                  <span v-if="r.replyToUserName" class="reply-to"> 回复 {{ r.replyToUserName }}</span>
                  <span class="comment-time">{{ formatTime(r.createdAt) }}</span>
                </div>
                <p class="comment-body">{{ r.content }}</p>
                <div class="comment-foot">
                  <n-button v-if="canDeleteComment(r)" size="tiny" text type="error" :disabled="deleting" @click="deleteComment(r.id)">删除</n-button>
                  <n-button size="tiny" text @click="startReply(r.id, c.id, r.authorName)">回复</n-button>
                  <n-button size="tiny" text @click="reportComment(r.id)">举报</n-button>
                </div>
              </div>
            </div>

            <!-- 回复输入框 -->
            <div v-if="replyThreadId === c.id" class="comment-reply-box">
              <div class="reply-target-tip">
                <span>正在回复 {{ replyTargetName }}</span>
                <n-button size="tiny" text @click="cancelReply">取消</n-button>
              </div>
              <n-input
                v-model:value="replyDraft"
                :placeholder="`回复 ${replyTargetName}...`"
                :disabled="sending"
                size="small"
                @focus="handleCommentFocus"
                @blur="handleCommentBlur"
                @keyup.enter="submitComment(replyParentId)"
              />
              <n-button size="tiny" type="primary" :disabled="!replyDraft.trim() || sending" @click="submitComment(replyParentId)">
                回复
              </n-button>
            </div>
          </div>
        </div>
      </section>

      <!-- 同频推荐（保留） -->
      <SimilarDiariesPanel v-if="!hideSimilarOnMobileInput" :diaries="store.similarDiaries" @select="selectDiary" />
    </div>
    <n-empty v-else description="日记不存在" />
  </main>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NInput, NTag, NEmpty, useMessage } from 'naive-ui'
import { diaryApi, reportApi } from '../api'
import { tryExpToast } from '../utils/toast'
import { useAuthStore } from '../stores/auth'
import AppHeader from '../components/AppHeader.vue'
import AiAnalysisCard from '../components/AiAnalysisCard.vue'
import MusicCard from '../components/MusicCard.vue'
import ImageGallery from '../components/ImageGallery.vue'
import SimilarDiariesPanel from '../components/SimilarDiariesPanel.vue'
import { useDiaryStore, type Diary } from '../stores/diary'
import { useFollowStore } from '../stores/follow'

const route = useRoute()
const router = useRouter()
const store = useDiaryStore()
const followStore = useFollowStore()
const auth = useAuthStore()
const message = useMessage()
const diary = ref<Diary | null>(null)
const commentDraft = ref('')
const replyDraft = ref('')
const commentError = ref('')
const replyParentId = ref<number | null>(null)
const replyThreadId = ref<number | null>(null)
const replyTargetName = ref('')
const sending = ref(false)
const deleting = ref(false)
const resonating = ref(false)
const justLiked = ref(false)
let justLikedTimer: ReturnType<typeof setTimeout> | null = null
const thumbsUpOutline = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3m7-2V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14Z"/></svg>`
const thumbsUpFilled = `<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3m7-2V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14Z"/></svg>`
const pinning = ref(false)
const hideSimilarOnMobileInput = ref(false)
let inputBlurTimer: ReturnType<typeof setTimeout> | null = null

const isOwner = computed(() => auth.userId != null && diary.value != null && auth.userId === diary.value.authorUserId)

async function loadDiaryByRoute() {
  const id = Number(route.params.id)
  if (!Number.isFinite(id)) {
    diary.value = null
    return
  }

  try {
    const res = await diaryApi.get(id)
    diary.value = store.normalize(res.data.data)
    if (!isOwner.value && diary.value?.authorUserId) {
      await followStore.checkStatus(diary.value.authorUserId)
    }
    await store.loadSimilar(id)
  } catch {
    diary.value = null
  }
}

onMounted(async () => {
  await loadDiaryByRoute()
})

watch(() => route.params.id, async () => {
  await loadDiaryByRoute()
})

async function submitComment(parentId: number | null) {
  const content = parentId ? replyDraft.value.trim() : commentDraft.value.trim()
  if (!content || !diary.value || sending.value) return
  sending.value = true
  commentError.value = ''
  try {
    const res = await diaryApi.addComment(diary.value.id, content, parentId ?? undefined)
    diary.value = store.normalize(res.data.data)
    tryExpToast('comment', '回复 +3 EXP')
    commentDraft.value = ''
    replyDraft.value = ''
    commentError.value = ''
    cancelReply()
  } catch (e: any) {
    commentError.value = e?.response?.data?.message || '评论发送失败，请稍后重试'
  }
  sending.value = false
}

async function deleteComment(commentId: number) {
  if (!diary.value || deleting.value) return
  deleting.value = true
  commentError.value = ''
  try {
    const res = await diaryApi.deleteComment(diary.value.id, commentId)
    diary.value = store.normalize(res.data.data)
    if (replyParentId.value === commentId || replyThreadId.value === commentId) {
      cancelReply()
    }
  } catch (e: any) {
    commentError.value = e?.response?.data?.message || '删除失败，请稍后重试'
  } finally {
    deleting.value = false
  }
}

function canDeleteComment(comment: any) {
  if (auth.isAdmin) return true
  const authorUserId = Number(comment?.authorUserId)
  if (Number.isFinite(authorUserId) && auth.userId != null) {
    return authorUserId === auth.userId
  }
  return Boolean(auth.displayName && comment?.authorName === auth.displayName)
}

function startReply(parentId: number, threadId: number, targetName: string) {
  if (replyParentId.value === parentId && replyThreadId.value === threadId) {
    cancelReply()
    return
  }
  replyParentId.value = parentId
  replyThreadId.value = threadId
  replyTargetName.value = targetName
  replyDraft.value = ''
  void nextTick(() => {
    ensureCommentInputVisible()
  })
}

function cancelReply() {
  replyParentId.value = null
  replyThreadId.value = null
  replyTargetName.value = ''
  replyDraft.value = ''
}

async function reportDiary() {
  if (!diary.value) return
  const reason = window.prompt('请简单说明举报原因')
  if (!reason?.trim()) return
  await reportApi.create({ targetType: 'DIARY', targetId: diary.value.id, reason: reason.trim() })
}

function openAuthorProfile() {
  if (!diary.value) return
  router.push(`/profile/${diary.value.authorUserId}`)
}

async function togglePin() {
  if (!diary.value || pinning.value) return
  pinning.value = true
  try {
    const res = await diaryApi.update(diary.value.id, {
      content: diary.value.content,
      visibility: diary.value.visibility,
      isPinned: !diary.value.isPinned,
    })
    diary.value = store.normalize(res.data.data)
    message.success(diary.value.isPinned ? '已置顶' : '已取消置顶')
  } catch (e: any) {
    message.error(e?.response?.data?.message || '操作失败')
  } finally {
    pinning.value = false
  }
}

async function handleDeleteDiary() {
  if (!diary.value) return
  if (window.confirm('确定要删除这篇日记吗？此操作不可撤销。')) {
    try {
      await store.deleteDiary(diary.value.id)
      message.success('日记已删除')
      router.push('/')
    } catch (e: any) {
      message.error(e?.response?.data?.message || '删除失败')
    }
  }
}

async function handleAdminForceDelete() {
  if (!diary.value) return
  if (window.confirm('您正在以管理员身份强制删除这篇日记，此操作不可逆，确定吗？')) {
    try {
      await store.deleteDiary(diary.value.id)
      message.success('日记已强制删除')
      router.push('/')
    } catch (e: any) {
      message.error(e?.response?.data?.message || '强制删除失败')
    }
  }
}

async function reportComment(commentId: number) {
  const reason = window.prompt('请简单说明举报原因')
  if (!reason?.trim()) return
  await reportApi.create({ targetType: 'COMMENT', targetId: commentId, reason: reason.trim() })
}

async function resonateDiary() {
  if (!diary.value || resonating.value) return
  resonating.value = true
  if (justLikedTimer) { clearTimeout(justLikedTimer) }
  justLiked.value = true
  try {
    const res = await diaryApi.resonate(diary.value.id)
    diary.value = store.normalize(res.data.data)
    if (diary.value.likedByMe) tryExpToast('like', '点赞 +2 EXP')
  } finally {
    resonating.value = false
  }
  justLikedTimer = window.setTimeout(() => { justLiked.value = false }, 360)
}

function selectDiary(d: Diary) {
  void router.push(`/diary/${d.id}`)
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

async function handleFollow() {
  if (!diary.value || followStore.isPending(diary.value.authorUserId)) return
  const authorId = diary.value.authorUserId
  if (followStore.isFollowing(authorId)) {
    await followStore.unfollow(authorId)
  } else {
    await followStore.follow(authorId)
  }
}

function handleCommentFocus() {
  if (isMobileViewport()) {
    hideSimilarOnMobileInput.value = true
  }
  ensureCommentInputVisible()
}

function handleCommentBlur() {
  if (inputBlurTimer) {
    clearTimeout(inputBlurTimer)
  }
  inputBlurTimer = setTimeout(() => {
    const active = document.activeElement as HTMLElement | null
    const isTypingElement = Boolean(active && (
      active.tagName === 'INPUT' ||
      active.tagName === 'TEXTAREA' ||
      active.getAttribute('contenteditable') === 'true'
    ))
    if (!isTypingElement) {
      hideSimilarOnMobileInput.value = false
    }
  }, 120)
}

function isMobileViewport() {
  return window.matchMedia('(max-width: 768px)').matches
}

function ensureCommentInputVisible() {
  window.requestAnimationFrame(() => {
    const active = document.activeElement as HTMLElement | null
    active?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}
</script>

<style scoped>
.reply-target-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #4f5f53;
  font-size: 12px;
  margin-bottom: 8px;
}

.reply-mode-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 36px;
  padding: 8px 12px;
  margin-bottom: 10px;
  border-radius: 10px;
  border: 1px solid #e5ddd1;
  background: linear-gradient(180deg, #f9f5ee 0%, #f5efe5 100%);
  color: #5f584f;
  font-size: 12px;
}

.comment-error {
  margin: 8px 0 10px;
  padding: 7px 10px;
  border-radius: 8px;
  border: 1px solid #f1c7c7;
  background: #fff5f5;
  color: #b23a3a;
  font-size: 12px;
}




@media (max-width: 600px) {
  .diary-edit-actions {
    flex-wrap: wrap;
  }

  .diary-edit-visibility {
    flex: 1 0 100%;
    order: -1;
  }
}
</style>
