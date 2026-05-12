<template>
  <article class="feed-item">
    <div class="feed-head">
      <div class="feed-head-left">
        <span class="avatar">{{ diary.authorName.charAt(0) }}</span>
        <div>
          <div class="author-row">
            <strong>{{ diary.authorName }}</strong>
            <button
              v-if="diary.authorUserId !== auth.userId"
              :class="['follow-btn', { following: followStore.isFollowing(diary.authorUserId) }]"
              @mouseenter="hoveringId = diary.authorUserId"
              @mouseleave="hoveringId = null"
              @click.stop="toggleFollow(diary.authorUserId)"
            >
              {{ followBtnLabel(diary.authorUserId) }}
            </button>
          </div>
          <span>{{ formatTime(diary.createdAt) }}</span>
        </div>
      </div>
    </div>

    <p class="feed-content">{{ visibleContent }}</p>
    <button v-if="isLongContent" class="feed-expand" type="button" @click="expanded = !expanded">
      {{ expanded ? '收起' : '展开' }}
    </button>

    <div class="feed-actions">
      <n-button size="small" tertiary @click="$emit('resonate', diary)">
        共鸣 {{ diary.resonanceCount }}
      </n-button>
      <div v-if="diary.authorUserId !== auth.userId" class="feed-safety-actions">
        <n-button size="small" text @click="reportDiary">举报</n-button>
        <n-button size="small" text @click="hideDiary">隐藏</n-button>
      </div>
    </div>

    <div v-if="(diary.comments ?? []).length" class="comments">
      <div v-for="comment in diary.comments" :key="comment.id" class="comment-thread">
        <div class="comment-main">
          <p class="comment-text">
            <strong>{{ comment.authorName }}</strong>
            <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
          </p>
          <p class="comment-body">{{ comment.content }}</p>
          <div class="comment-foot">
            <n-button
              v-if="comment.authorName === auth.displayName"
              size="tiny"
              text
              type="error"
              @click="deleteComment(comment.id)"
            >删除</n-button>
            <n-button size="tiny" text @click="replyTo = replyTo === comment.id ? null : comment.id">回复</n-button>
            <n-button size="tiny" text @click="reportComment(comment.id)">举报</n-button>
          </div>
        </div>
        <div v-if="replyTo === comment.id" class="comment-box">
          <n-input
            v-model:value="replyDraft"
            size="small"
            placeholder="回复 {{ comment.authorName }}..."
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
            <p class="comment-body">{{ reply.content }}</p>
          </div>
        </div>
      </div>
    </div>

    <div class="comment-box">
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
import type { Diary } from '../stores/diary'
import { useFollowStore } from '../stores/follow'
import { useAuthStore } from '../stores/auth'
import { useDiaryStore } from '../stores/diary'
import { diaryApi, reportApi } from '../api'

const props = defineProps<{ diary: Diary }>()
const emit = defineEmits<{
  resonate: [diary: Diary]
  comment: [diary: Diary, content: string, parentCommentId?: number]
}>()

const followStore = useFollowStore()
const auth = useAuthStore()
const diaryStore = useDiaryStore()
const hoveringId = ref<number | null>(null)

const draft = ref('')
const replyDraft = ref('')
const replyTo = ref<number | null>(null)
const expanded = ref(false)

const isLongContent = computed(() => (props.diary.content ?? '').length > 180)
const visibleContent = computed(() => {
  const content = props.diary.content ?? ''
  if (expanded.value || !isLongContent.value) return content
  return content.slice(0, 180) + '...'
})

onMounted(() => {
  if (props.diary.authorUserId !== auth.userId) {
    followStore.checkStatus(props.diary.authorUserId)
  }
})

function toggleFollow(userId: number) {
  if (followStore.isFollowing(userId)) {
    followStore.unfollow(userId)
  } else {
    followStore.follow(userId)
  }
}

function followBtnLabel(userId: number) {
  if (followStore.isFollowing(userId)) {
    return hoveringId.value === userId ? '取消关注' : '已关注'
  }
  return '+ 关注'
}

async function deleteComment(commentId: number) {
  try {
    await diaryApi.deleteComment(props.diary.id, commentId)
    emit('comment', props.diary, '') // 触发父组件刷新
  } catch { /* ignore */ }
}

async function hideDiary() {
  await diaryStore.hideDiary(props.diary.id)
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

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>
