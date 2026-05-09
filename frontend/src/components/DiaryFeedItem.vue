<template>
  <article class="feed-item">
    <div class="feed-head">
      <div class="feed-head-left">
        <span class="avatar">{{ diary.authorName.charAt(0) }}</span>
        <div>
          <div class="author-row">
            <strong>{{ diary.authorName }}</strong>
            <n-button
              v-if="diary.authorUserId !== auth.userId"
              size="tiny"
              :type="followStore.isFollowing(diary.authorUserId) ? 'default' : 'primary'"
              :secondary="followStore.isFollowing(diary.authorUserId)"
              @mouseenter="hoveringId = diary.authorUserId"
              @mouseleave="hoveringId = null"
              @click.stop="toggleFollow(diary.authorUserId)"
            >
              {{ followBtnLabel(diary.authorUserId) }}
            </n-button>
          </div>
          <span>{{ formatTime(diary.createdAt) }}</span>
        </div>
      </div>
      <n-tag v-if="diary.analysis?.moodLabel" round>{{ diary.analysis.moodLabel }}</n-tag>
    </div>

    <p class="feed-content">{{ diary.content }}</p>

    <div class="tag-row">
      <n-tag
        v-for="topic in (diary.analysis?.topicLabels ?? [])"
        :key="`${diary.id}-${topic}`"
        size="small"
        type="info"
        round
      >
        {{ topic }}
      </n-tag>
    </div>

    <div class="feed-actions">
      <n-button size="small" tertiary @click="$emit('resonate', diary)">
        共鸣 {{ diary.resonanceCount }}
      </n-button>
      <n-button size="small" text @click="$emit('select', diary)">看分析</n-button>
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
            <n-button size="tiny" text @click="replyTo = replyTo === comment.id ? null : comment.id">回复</n-button>
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
import { ref, onMounted } from 'vue'
import type { Diary } from '../stores/diary'
import { useFollowStore } from '../stores/follow'
import { useAuthStore } from '../stores/auth'

const props = defineProps<{ diary: Diary }>()
const emit = defineEmits<{
  select: [diary: Diary]
  resonate: [diary: Diary]
  comment: [diary: Diary, content: string, parentCommentId?: number]
}>()

const followStore = useFollowStore()
const auth = useAuthStore()
const hoveringId = ref<number | null>(null)

const draft = ref('')
const replyDraft = ref('')
const replyTo = ref<number | null>(null)

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
