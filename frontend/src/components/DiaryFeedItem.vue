<template>
  <article class="feed-item" :class="{ 'feed-item-compact': compact }" @click="handleCardClick">
    <div class="feed-head">
      <div class="feed-head-left">
        <img v-if="diary.authorAvatar" :src="diary.authorAvatar" class="avatar avatar-img" loading="lazy" decoding="async" />
        <span v-else class="avatar" :style="getAvatarStyle(diary.authorName)">{{ diary.authorName?.charAt(0).toUpperCase() }}</span>
        <div>
          <div class="author-row">
            <button type="button" class="author-name-link" @click.stop="openAuthorProfile(diary.authorUserId)">
              {{ diary.authorName }}
            </button>
            <span v-if="diary.authorRole === 'ADMIN'" class="diary-author-admin">管理员</span>
            <span v-if="diary.authorLevel" class="diary-author-level">Lv.{{ diary.authorLevel }}</span>
          </div>
          <span class="feed-time">
            {{ formatTime(diary.createdAt) }}
            <span :class="['vis-tag', diary.visibility === 'PUBLIC' ? 'vis-tag-public' : 'vis-tag-private']">
              {{ diary.visibility === 'PUBLIC' ? '公开' : '私密' }}
            </span>
            <n-tag v-if="diary.isPinned" type="warning" size="small" round style="margin-left: 6px;">
              📌 置顶公告
            </n-tag>
          </span>
        </div>
      </div>
      <div class="feed-head-right">
        <button
          v-if="diary.authorUserId !== auth.userId && !hideFollowBtn"
          :class="['follow-btn', 'feed-follow-btn', { following: followStore.isFollowing(diary.authorUserId) }]"
          :disabled="followStore.isPending(diary.authorUserId)"
          @mouseenter="hoveringId = diary.authorUserId"
          @mouseleave="hoveringId = null"
          @click.stop="toggleFollow(diary.authorUserId)"
        >
          {{ followBtnLabel(diary.authorUserId) }}
        </button>
      </div>
    </div>

    <div
      class="feed-content feed-content-clickable md-content"
      role="button"
      tabindex="0"
      @click.stop="$emit('open-detail', diary)"
      @keydown.enter.prevent="$emit('open-detail', diary)"
      @keydown.space.prevent="$emit('open-detail', diary)"
      v-html="renderMd(visibleContent)"
    ></div>
    <button v-if="isLongContent && showExpandToggle" class="feed-expand" type="button" @click="expanded = !expanded">
      {{ expanded ? '收起' : '展开' }}
    </button>

    <MusicCard v-if="diary.musicMeta" :music-meta="diary.musicMeta" />
    <ImageGallery v-if="diary.images?.length" :images="diary.images" :thumbnail="true" />

    <div class="feed-actions">
      <n-button size="small" tertiary :class="['like-btn', { liked: diary.likedByMe, 'just-liked': justLiked }]" @click="handleResonate(diary)">
        <span class="like-btn-icon" v-html="diary.likedByMe ? thumbsUpFilled : thumbsUpOutline" />
        <span class="like-btn-count">{{ diary.resonanceCount ?? 0 }}</span>
      </n-button>
      <n-button v-if="diary.authorUserId !== auth.userId" size="small" text class="feed-report-btn" @click="reportDiary">举报</n-button>
    </div>

    <div v-if="auth.isAdmin" class="feed-admin-row">
      <n-button size="small" text class="feed-admin-delete" @click="handleAdminDeleteCard">
        🗑️ 强制删除
      </n-button>
    </div>

    <div v-if="enableComments && (diary.comments ?? []).length" class="comments">
      <div v-for="comment in diary.comments" :key="comment.id" class="comment-thread">
        <div class="comment-main">
          <p class="comment-text">
            <strong>{{ comment.authorName }}</strong>
            <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
          </p>
          <div class="comment-body md-content" v-html="renderMd(comment.content)"></div>
          <div class="comment-foot">
            <n-button
              v-if="canDeleteComment(comment)"
              size="tiny"
              text
              type="error"
              @click="deleteComment(comment.id)"
            >删除</n-button>
            <n-button size="tiny" text @click="replyTo = replyTo === comment.id ? null : comment.id">回复</n-button>
            <n-button size="tiny" text @click="reportComment(comment.id)">举报</n-button>
          </div>
        </div>
        <div v-if="replyTo === comment.id" class="comment-box comment-box-reply">
          <n-input
            v-model:value="replyDraft"
            size="small"
            :placeholder="`回复 ${comment.authorName}...`"
            @keyup.enter="submitReply(comment.id)"
          />
          <n-button size="small" type="primary" :disabled="!replyDraft.trim()" @click="submitReply(comment.id)">
            发送
          </n-button>
        </div>
        <div v-if="(comment.replies ?? []).length" class="comment-replies">
          <div v-for="reply in comment.replies" :key="reply.id" class="reply-item">
            <p class="comment-text">
              <strong>{{ reply.authorName }}</strong>
              <span v-if="reply.replyToUserName" class="reply-to"> 回复 @{{ reply.replyToUserName }} </span>
              <span class="comment-time">{{ formatTime(reply.createdAt) }}</span>
            </p>
            <div class="comment-body md-content" v-html="renderMd(reply.content)"></div>
            <div class="comment-foot">
              <n-button v-if="canDeleteComment(reply)" size="tiny" text type="error" @click="deleteComment(reply.id)">删除</n-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="enableComments && replyTo === null" class="comment-box comment-box-main">
      <n-input
        v-model:value="draft"
        size="small"
        placeholder="留一句温柔回应"
        @keyup.enter="submit"
      />
      <n-button size="small" type="primary" :disabled="!draft.trim()" @click="submit">
        留言
      </n-button>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { Diary } from '../stores/diary'
import { useDiaryStore } from '../stores/diary'
import { useFollowStore } from '../stores/follow'
import { useAuthStore } from '../stores/auth'
import { reportApi } from '../api'
import { renderSafeMarkdown, stripMarkdown } from '../utils/markdown'
import MusicCard from './MusicCard.vue'
import ImageGallery from './ImageGallery.vue'

const store = useDiaryStore()
const message = useMessage()
const router = useRouter()

const props = withDefaults(defineProps<{
  diary: Diary
  enableComments?: boolean
  compact?: boolean
  previewLimit?: number
  showExpandToggle?: boolean
  hideFollowBtn?: boolean
}>(), {
  enableComments: true,
  compact: false,
  previewLimit: 180,
  showExpandToggle: true,
  hideFollowBtn: false,
})
const emit = defineEmits<{
  resonate: [diary: Diary]
  comment: [diary: Diary, content: string, parentCommentId?: number]
  'delete-comment': [diary: Diary, commentId: number]
  'open-detail': [diary: Diary]
}>()

const followStore = useFollowStore()
const auth = useAuthStore()
const hoveringId = ref<number | null>(null)
const hideFollowBtn = computed(() => props.hideFollowBtn)

const draft = ref('')
const replyDraft = ref('')
const replyTo = ref<number | null>(null)
const expanded = ref(false)
const justLiked = ref(false)
let justLikedTimer: ReturnType<typeof setTimeout> | null = null

const thumbsUpOutline = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3m7-2V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14Z"/></svg>`
const thumbsUpFilled = `<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3m7-2V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14Z"/></svg>`

const enableComments = computed(() => props.enableComments)
const compact = computed(() => props.compact)
const showExpandToggle = computed(() => props.showExpandToggle)

function renderMd(text: string) {
  return renderSafeMarkdown(text)
}

const isLongContent = computed(() => (props.diary.content ?? '').length > props.previewLimit)
const visibleContent = computed(() => {
  const content = props.diary.content ?? ''
  if (expanded.value || !isLongContent.value) return content
  return stripMarkdown(content).slice(0, props.previewLimit) + '...'
})

onMounted(() => {
  if (props.diary.authorUserId !== auth.userId) {
    followStore.checkStatus(props.diary.authorUserId)
  }
})

async function toggleFollow(userId: number) {
  if (followStore.isPending(userId)) return
  if (followStore.isFollowing(userId)) {
    await followStore.unfollow(userId)
  } else {
    await followStore.follow(userId)
  }
}

function handleResonate(diary: Diary) {
  if (justLikedTimer) { clearTimeout(justLikedTimer) }
  justLiked.value = true
  emit('resonate', diary)
  justLikedTimer = window.setTimeout(() => { justLiked.value = false }, 360)
}

function followBtnLabel(userId: number) {
  if (followStore.isPending(userId)) {
    return '处理中...'
  }
  if (followStore.isFollowing(userId)) {
    return hoveringId.value === userId ? '取消关注' : '已关注'
  }
  return '+ 关注'
}

async function deleteComment(commentId: number) {
  try {
    emit('delete-comment', props.diary, commentId)
  } catch { /* ignore */ }
}

async function reportDiary() {
  const reason = window.prompt('请简单说明举报原因')
  if (!reason?.trim()) return
  await reportApi.create({ targetType: 'DIARY', targetId: props.diary.id, reason: reason.trim() })
}

async function reportComment(commentId: number) {
  const reason = window.prompt('请简单说明举报原因')
  if (!reason?.trim()) return
  await reportApi.create({ targetType: 'COMMENT', targetId: commentId, reason: reason.trim() })
}

async function handleAdminDeleteCard() {
  if (window.confirm(`您确定要以管理员身份直接从公共广场删除 [${props.diary.authorName}] 的这篇日记吗？`)) {
    try {
      await store.deleteDiary(props.diary.id)
      message.success('该日记已被强制抹除')
    } catch {
      message.error('删除失败，请稍后重试')
    }
  }
}

function submit() {
  const content = draft.value.trim()
  if (!content) return
  emit('comment', props.diary, content)
  draft.value = ''
}

function submitReply(commentId: number) {
  const content = replyDraft.value.trim()
  if (!content) return
  emit('comment', props.diary, content, commentId)
  replyDraft.value = ''
  replyTo.value = null
}

function canDeleteComment(comment: any) {
  if (auth.isAdmin) return true
  const authorUserId = Number(comment?.authorUserId)
  if (Number.isFinite(authorUserId) && auth.userId != null) {
    return authorUserId === auth.userId
  }
  return Boolean(auth.displayName && comment?.authorName === auth.displayName)
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function handleCardClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (target.closest('button, a, input, textarea, .n-button')) return
  router.push(`/diary/${props.diary.id}`)
}

function openAuthorProfile(userId: number) {
  if (!Number.isFinite(userId)) return
  router.push(`/profile/${userId}`)
}

function getAvatarStyle(name: string) {
  const colors = [
    ['#3b82f6', '#1d4ed8'], // Blue
    ['#10b981', '#047857'], // Emerald/Jade
    ['#8b5cf6', '#6d28d9'], // Violet
    ['#f59e0b', '#b45309'], // Amber
    ['#ec4899', '#be185d'], // Pink
    ['#06b6d4', '#0891b2'], // Cyan
  ]
  let hash = 0
  if (name) {
    for (let i = 0; i < name.length; i++) {
      hash = name.charCodeAt(i) + ((hash << 5) - hash)
    }
  }
  const index = Math.abs(hash) % colors.length
  const [start, end] = colors[index]
  return {
    background: `linear-gradient(135deg, ${start} 0%, ${end} 100%)`,
    color: '#ffffff',
    textShadow: '0 1px 2px rgba(0,0,0,0.15)'
  }
}
</script>
