<template>
  <article
    class="feed-item"
    v-motion
    :initial="{ opacity: 0, y: 24 }"
    :enter="{ opacity: 1, y: 0, transition: { type: 'spring', stiffness: 170, damping: 26 } }"
    :class="{ 'feed-item-compact': compact }"
    @click="handleCardClick"
  >
    <!-- 左侧：日期 -->
    <div class="feed-left">
      <div class="feed-date">
        <span class="feed-date-day">{{ formatDay(diary.createdAt) }}</span>
        <span class="feed-date-month">{{ formatMonth(diary.createdAt) }}</span>
        <span class="feed-date-time">{{ formatTimeOnly(diary.createdAt) }}</span>
      </div>
      <span v-if="diary.isPinned" class="feed-pin-tag">📌 置顶</span>
    </div>

    <!-- 右侧：内容 -->
    <div class="feed-right">
      <!-- 作者行 -->
      <div class="feed-author">
        <img
          v-if="diary.authorAvatar"
          :src="diary.authorAvatar"
          class="feed-author-avatar feed-author-avatar-img"
          loading="lazy"
          decoding="async"
        />
        <span
          v-else
          class="feed-author-avatar"
          :style="getAvatarStyle(diary.authorName)"
        >{{ diary.authorName?.charAt(0).toUpperCase() }}</span>
        <button type="button" class="feed-author-name" @click.stop="openAuthorProfile(diary.authorUserId)">
          {{ diary.authorName }}
        </button>
        <span v-if="diary.authorRole === 'ADMIN'" class="feed-badge feed-badge-admin">管理员</span>
        <span v-if="diary.authorLevel" class="feed-badge feed-badge-level">Lv.{{ diary.authorLevel }}</span>
        <button
          v-if="diary.authorUserId !== auth.userId && !hideFollowBtn"
          :class="['feed-follow', { following: followStore.isFollowing(diary.authorUserId) }]"
          :disabled="followStore.isPending(diary.authorUserId)"
          @mouseenter="hoveringId = diary.authorUserId"
          @mouseleave="hoveringId = null"
          @click.stop="toggleFollow(diary.authorUserId)"
        >
          {{ followBtnLabel(diary.authorUserId) }}
        </button>
      </div>

      <!-- 正文 -->
      <div
        class="feed-body md-content"
        role="button"
        tabindex="0"
        @click.stop="$emit('open-detail', diary)"
        @keydown.enter.prevent="$emit('open-detail', diary)"
        @keydown.space.prevent="$emit('open-detail', diary)"
        v-html="renderMd(visibleContent)"
      ></div>

      <button
        v-if="isLongContent && showExpandToggle"
        class="feed-expand"
        type="button"
        @click="expanded = !expanded"
      >
        {{ expanded ? '收起 ▲' : '阅读全文 ▼' }}
      </button>

      <!-- 多媒体 -->
      <MusicCard v-if="diary.musicMeta" :music-meta="diary.musicMeta" />
      <ImageGallery v-if="diary.images?.length" :images="diary.images" :thumbnail="true" />

      <!-- ★ 底部操作栏：纯图标 + 数字，杂志风 -->
      <div class="feed-foot">
        <button
          :class="['feed-stat', { liked: diary.likedByMe, 'just-liked': justLiked }]"
          @click.stop="handleResonate(diary)"
        >
          <span class="feed-stat-icon" v-html="diary.likedByMe ? heartFilled : heartOutline"></span>
          <span class="feed-stat-num">{{ diary.resonanceCount ?? 0 }}</span>
        </button>
        <span class="feed-stat">
          <span class="feed-stat-icon" v-html="commentIcon"></span>
          <span class="feed-stat-num">{{ totalCommentCount }}</span>
        </span>
        <button
          v-if="diary.authorUserId !== auth.userId"
          class="feed-report"
          @click.stop="reportDiary"
        >举报</button>
      </div>

      <!-- 管理员操作 -->
      <div v-if="auth.isAdmin" class="feed-admin">
        <button class="feed-admin-delete" @click.stop="handleAdminDeleteCard">🗑️ 强制删除</button>
      </div>

      <!-- 评论区 -->
      <div v-if="enableComments && (diary.comments ?? []).length" class="feed-comments">
        <div v-for="comment in diary.comments" :key="comment.id" class="comment-thread">
          <div class="comment-main">
            <p class="comment-head">
              <strong>{{ comment.authorName }}</strong>
              <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
            </p>
            <div class="comment-body md-content" v-html="renderMd(comment.content)"></div>
            <div class="comment-foot">
              <button v-if="canDeleteComment(comment)" class="comment-act comment-act-danger" @click="deleteComment(comment.id)">删除</button>
              <button class="comment-act" @click="replyTo = replyTo === comment.id ? null : comment.id">回复</button>
              <button class="comment-act" @click="reportComment(comment.id)">举报</button>
            </div>
          </div>
          <div v-if="replyTo === comment.id" class="comment-reply-box">
            <input
              v-model="replyDraft"
              class="comment-reply-input"
              :placeholder="`回复 ${comment.authorName}...`"
              @keyup.enter="submitReply(comment.id)"
            />
            <button class="comment-reply-submit" :disabled="!replyDraft.trim()" @click="submitReply(comment.id)">发送</button>
          </div>
          <div v-if="(comment.replies ?? []).length" class="comment-replies">
            <div v-for="reply in comment.replies" :key="reply.id" class="reply-item">
              <p class="comment-head">
                <strong>{{ reply.authorName }}</strong>
                <span v-if="reply.replyToUserName" class="reply-to"> 回复 @{{ reply.replyToUserName }} </span>
                <span class="comment-time">{{ formatTime(reply.createdAt) }}</span>
              </p>
              <div class="comment-body md-content" v-html="renderMd(reply.content)"></div>
              <div class="comment-foot">
                <button v-if="canDeleteComment(reply)" class="comment-act comment-act-danger" @click="deleteComment(reply.id)">删除</button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 评论输入 -->
      <div v-if="enableComments && replyTo === null" class="comment-input-row">
        <input
          v-model="draft"
          class="comment-reply-input"
          placeholder="写一句温柔的回应..."
          @keyup.enter="submit"
        />
        <button class="comment-reply-submit" :disabled="!draft.trim()" @click="submit">留言</button>
      </div>
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

const heartOutline = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>`
const heartFilled = `<svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>`
const commentIcon = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>`

const enableComments = computed(() => props.enableComments)
const compact = computed(() => props.compact)
const showExpandToggle = computed(() => props.showExpandToggle)

/** 评论数：优先使用后端预计算的 commentCount（广场列表），否则递归统计已加载的评论树（详情页） */
const totalCommentCount = computed(() => {
  if (typeof props.diary.commentCount === 'number') {
    return props.diary.commentCount
  }
  const comments = props.diary.comments ?? []
  let count = 0
  function countRecursive(list: any[]) {
    for (const c of list) {
      count++
      if (c.replies?.length) countRecursive(c.replies)
    }
  }
  countRecursive(comments)
  return count
})

function renderMd(text: string) { return renderSafeMarkdown(text) }

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
  if (followStore.isFollowing(userId)) { await followStore.unfollow(userId) }
  else { await followStore.follow(userId) }
}

function handleResonate(diary: Diary) {
  if (justLikedTimer) { clearTimeout(justLikedTimer) }
  justLiked.value = true
  emit('resonate', diary)
  justLikedTimer = window.setTimeout(() => { justLiked.value = false }, 360)
}

function followBtnLabel(userId: number) {
  if (followStore.isPending(userId)) return '处理中...'
  if (followStore.isFollowing(userId)) return hoveringId.value === userId ? '取消关注' : '已关注'
  return '+ 关注'
}

async function deleteComment(commentId: number) { try { emit('delete-comment', props.diary, commentId) } catch { /* ignore */ } }
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
    try { await store.deleteDiary(props.diary.id); message.success('该日记已被强制抹除') }
    catch { message.error('删除失败，请稍后重试') }
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
  if (Number.isFinite(authorUserId) && auth.userId != null) return authorUserId === auth.userId
  return Boolean(auth.displayName && comment?.authorName === auth.displayName)
}
function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}
function formatDay(value: string) { return new Date(value).getDate().toString().padStart(2, '0') }
function formatMonth(value: string) { return (new Date(value).getMonth() + 1) + '月' }
function formatTimeOnly(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}
function handleCardClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (target.closest('button, a, input, textarea')) return
  router.push(`/diary/${props.diary.id}`)
}
function openAuthorProfile(userId: number) {
  if (!Number.isFinite(userId)) return
  router.push(`/profile/${userId}`)
}
function getAvatarStyle(name: string) {
  const colors = [['#a3b899','#7f9c73'],['#dfa29c','#c17a72'],['#e6cb9d','#caa368'],['#b2afc2','#8985a0'],['#9cbfb8','#739e95'],['#cab8a6','#9c8874']]
  let hash = 0
  if (name) { for (let i = 0; i < name.length; i++) { hash = name.charCodeAt(i) + ((hash << 5) - hash) } }
  const index = Math.abs(hash) % colors.length
  const [start, end] = colors[index]
  return { background: `linear-gradient(135deg, ${start} 0%, ${end} 100%)`, color: '#ffffff', textShadow: '0 1px 2px rgba(0,0,0,0.15)' }
}
</script>

<style scoped>
/* ═══════════════════════════════════════════
   杂志风格 Feed Item · 去框架设计
   无卡片边框 · 无底色块 · 仅呼吸线分隔
   ═══════════════════════════════════════════ */

.feed-item {
  display: grid;
  grid-template-columns: 130px 1fr;
  gap: 28px;
  padding: 32px 0;
  border: none;
  background: transparent;
  border-bottom: 1px solid color-mix(in oklab, var(--color-primary) 8%, transparent);
  content-visibility: auto;
  contain-intrinsic-size: 320px;
  transition: opacity 0.25s;
}

.feed-item:last-child {
  border-bottom: none;
}

.feed-item:hover {
  opacity: 0.96;
}

/* ── 左侧：日期 ── */
.feed-left {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  padding-top: 4px;
}

.feed-date {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  font-family: var(--font-display);
  color: var(--color-primary);
  line-height: 1;
}

.feed-date-day {
  font-size: 2.4rem;
  font-weight: 500;
}

.feed-date-month {
  font-size: 0.9rem;
  opacity: 0.75;
  margin-top: 3px;
}

.feed-date-time {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  font-family: var(--font-body);
  margin-top: 6px;
}

.feed-pin-tag {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 100px;
  background: color-mix(in oklab, var(--color-warning) 16%, transparent);
  color: var(--color-warning);
  font-size: 0.72rem;
  font-weight: 700;
}

/* ── 右侧：内容区 ── */
.feed-right {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

/* 作者行 */
.feed-author {
  display: flex;
  align-items: center;
  gap: 10px;
}

.feed-author-avatar {
  width: 30px; height: 30px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  flex-shrink: 0; font-size: 13px; font-weight: 700;
}

.feed-author-avatar-img {
  object-fit: cover;
  border: 1.5px solid color-mix(in oklab, var(--color-primary) 30%, transparent);
}

.feed-author-name {
  border: none; background: transparent; padding: 0;
  font-size: 0.92rem; font-weight: 700; color: var(--color-text);
  max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  cursor: pointer;
  transition: color 0.15s;
}

.feed-author-name:hover { color: var(--color-primary); }

.feed-badge {
  font-size: 10px; font-weight: 700; padding: 2px 8px; border-radius: 8px; flex-shrink: 0;
}

.feed-badge-admin { color: var(--color-error); background: color-mix(in oklab, var(--color-error) 12%, transparent); }
.feed-badge-level { color: var(--color-primary); background: color-mix(in oklab, var(--color-primary) 12%, transparent); }

/* 关注按钮 */
.feed-follow {
  margin-left: auto; flex-shrink: 0;
  padding: 5px 14px; border: 1.5px solid var(--color-primary);
  border-radius: 100px; background: var(--color-primary);
  color: var(--color-on-primary); font-size: 0.75rem; font-weight: 700;
  cursor: pointer; transition: all 0.2s var(--ease-out);
}

.feed-follow:hover { background: var(--color-primary-hover); border-color: var(--color-primary-hover); }

.feed-follow.following { background: transparent; color: var(--color-primary); }
.feed-follow.following:hover { color: var(--color-accent); border-color: var(--color-accent); background: color-mix(in oklab, var(--color-accent) 8%, transparent); }

.feed-follow:disabled { opacity: 0.5; cursor: not-allowed; }

/* 正文 */
.feed-body {
  font-size: 1rem;
  line-height: 1.9;
  color: var(--color-text);
  word-break: break-word;
  cursor: pointer;
  transition: opacity 0.2s;
}

.feed-body:hover { opacity: 0.8; }

.feed-body:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 4px;
  border-radius: 4px;
}

.feed-expand {
  justify-self: start;
  padding: 4px 0; border: none; background: transparent;
  color: var(--color-primary); font-size: 0.82rem; font-weight: 700;
  cursor: pointer; transition: color 0.15s; letter-spacing: 0.03em;
}

.feed-expand:hover { color: var(--color-primary-hover); }

/* ★ 底部操作栏 — 杂志极简风 */
.feed-foot {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-top: 2px;
}

.feed-stat {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 0;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 0.82rem;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.2s;
}

.feed-stat:hover { color: var(--color-text); }

.feed-stat-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 15px; height: 15px;
  transition: transform 0.2s var(--ease-out);
}

.feed-stat:hover .feed-stat-icon { transform: scale(1.12); }

.feed-stat-num { font-weight: 600; }

.feed-stat.liked { color: var(--color-primary); }

@keyframes like-pop {
  0% { transform: scale(1); }
  40% { transform: scale(1.3); }
  100% { transform: scale(1); }
}

.feed-stat.just-liked .feed-stat-icon { animation: like-pop 0.35s ease; }

.feed-report {
  padding: 2px 6px; border: none; background: transparent;
  color: var(--color-text-light); font-size: 0.7rem;
  cursor: pointer; transition: color 0.15s;
}

.feed-report:hover { color: var(--color-accent); }

/* 管理员 */
.feed-admin {
  margin-top: 4px; padding-top: 8px;
  border-top: 1px dashed color-mix(in oklab, var(--color-border) 70%, transparent);
}

.feed-admin-delete {
  padding: 2px 8px; border: none; border-radius: 6px;
  background: color-mix(in oklab, var(--color-error) 10%, transparent);
  color: var(--color-error); font-size: 0.78rem; font-weight: 700;
  cursor: pointer; transition: background 0.15s;
}

.feed-admin-delete:hover { background: color-mix(in oklab, var(--color-error) 18%, transparent); }

/* 评论区 */
.feed-comments {
  display: grid; gap: 6px; margin-top: 8px;
}

.comment-main {
  padding: 12px 14px; border-radius: 10px;
  background: color-mix(in oklab, var(--color-primary) 4%, var(--color-surface));
  border: 1px solid color-mix(in oklab, var(--color-border) 60%, transparent);
}

.comment-head {
  display: flex; align-items: center; gap: 8px;
  margin: 0 0 4px; font-size: 0.82rem;
}

.comment-head strong { color: var(--color-text); font-size: 0.85rem; }
.comment-time { color: var(--color-text-muted); font-size: 0.7rem; }

.comment-body {
  margin: 0; color: var(--color-text-secondary);
  font-size: 0.85rem; line-height: 1.6;
}

.comment-foot {
  display: flex; justify-content: flex-end; gap: 4px; margin-top: 6px;
}

.comment-act {
  padding: 2px 10px; border: none; border-radius: 6px;
  background: transparent; color: var(--color-text-muted);
  font-size: 0.72rem; cursor: pointer; transition: all 0.15s;
}

.comment-act:hover { background: color-mix(in oklab, var(--color-primary) 8%, transparent); color: var(--color-text); }
.comment-act-danger { color: var(--color-error); }
.comment-act-danger:hover { background: color-mix(in oklab, var(--color-error) 10%, transparent); }

.comment-replies {
  margin-left: 20px; padding-left: 14px;
  border-left: 2px solid color-mix(in oklab, var(--color-primary) 20%, transparent);
  display: grid; gap: 6px;
}

.reply-item {
  padding: 10px 12px; border-radius: 8px;
  background: color-mix(in oklab, var(--color-primary) 3%, var(--color-surface));
  border: 1px solid color-mix(in oklab, var(--color-border) 40%, transparent);
}

.reply-to { color: var(--color-primary); font-weight: 600; font-size: 0.8rem; }

/* 评论输入 */
.comment-input-row, .comment-reply-box {
  display: flex; gap: 8px; margin-top: 10px;
}

.comment-reply-input {
  flex: 1; padding: 9px 14px; border: 1.5px solid var(--color-border);
  border-radius: 12px; background: var(--color-surface);
  color: var(--color-text); font-size: 0.85rem;
  outline: none; transition: border-color 0.2s;
}

.comment-reply-input:focus { border-color: var(--color-primary); }
.comment-reply-input::placeholder { color: var(--color-text-light); }

.comment-reply-submit {
  padding: 9px 18px; border: none; border-radius: 12px;
  background: var(--color-primary); color: var(--color-on-primary);
  font-size: 0.82rem; font-weight: 700; cursor: pointer;
  transition: background 0.2s, opacity 0.2s;
}

.comment-reply-submit:hover:not(:disabled) { background: var(--color-primary-hover); }
.comment-reply-submit:disabled { opacity: 0.4; cursor: not-allowed; }

/* ═══ Mobile ═══ */
@media (max-width: 768px) {
  .feed-item {
    grid-template-columns: 1fr;
    gap: 0;
    padding: 22px 0;
  }

  .feed-left {
    flex-direction: row;
    align-items: center;
    padding-top: 0;
    margin-bottom: 10px;
    gap: 10px;
  }

  .feed-date {
    flex-direction: row;
    align-items: baseline;
    gap: 4px;
  }

  .feed-date-day { font-size: 1.6rem; }
  .feed-date-month { font-size: 1rem; font-weight: 500; margin-top: 0; }
  .feed-date-time { font-size: 0.8rem; margin-top: 0; margin-left: 4px; }

  .feed-pin-tag { margin-left: auto; }

  .feed-body { font-size: 0.93rem; line-height: 1.75; }
  .feed-foot { gap: 14px; }

  .feed-author-name { max-width: 110px; font-size: 0.88rem; }
}

/* ═══ Extra small screens ═══ */
@media (max-width: 420px) {
  .feed-item { padding: 18px 0; }

  .feed-date-day { font-size: 1.4rem; }
  .feed-date-month { font-size: 0.9rem; }
  .feed-date-time { font-size: 0.72rem; }

  .feed-body { font-size: 0.88rem; line-height: 1.7; }

  .feed-stat { font-size: 0.78rem; }
  .feed-author-name { max-width: 90px; }
}

/* ═══ Dark theme overrides ═══ */
@media (prefers-color-scheme: dark) {
  .feed-item {
    border-bottom-color: color-mix(in oklab, var(--color-primary) 10%, transparent);
  }
}

[data-theme="black-rice"] .feed-item,
[data-theme="minimal-dark"] .feed-item {
  border-bottom-color: color-mix(in oklab, var(--color-primary) 12%, rgba(255,255,255,0.06));
}
</style>
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               