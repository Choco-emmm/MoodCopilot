<template>
  <div class="feedback-thread-list">
    <article
      v-for="item in items"
      :key="item.id"
      :id="`feedback-item-${item.id}`"
      class="feedback-entry"
      :class="{ highlighted: highlightId === item.id }"
    >
      <header class="feedback-entry-head">
        <div class="feedback-author">
          <img v-if="item.userAvatar" :src="item.userAvatar" class="feedback-avatar" decoding="async" />
          <span v-else class="feedback-avatar feedback-avatar-fallback">{{ (item.userName || '用').charAt(0) }}</span>
          <div class="feedback-author-meta">
            <div class="feedback-author-row">
              <strong class="feedback-author-name">{{ item.userName }}</strong>
              <span class="feedback-status-pill" :class="`status-${(item.status || 'PENDING').toLowerCase()}`">
                {{ statusLabel(item.status) }}
              </span>
              <span class="feedback-privacy-hint">仅双方可见</span>
            </div>
            <span class="feedback-time">{{ formatTime(item.createdAt) }}</span>
          </div>
        </div>

        <div v-if="isAdminMode" class="feedback-admin-actions">
          <button
            v-if="item.status !== 'READ'"
            type="button"
            class="feedback-act-btn"
            :disabled="statusLoadingId === item.id"
            @click="changeStatus(item, 'READ')"
          >
            标记跟进中
          </button>
          <button
            v-if="item.status !== 'RESOLVED'"
            type="button"
            class="feedback-act-btn feedback-act-primary"
            :disabled="statusLoadingId === item.id"
            @click="changeStatus(item, 'RESOLVED')"
          >
            标记已解决
          </button>
          <button
            v-if="item.status === 'RESOLVED'"
            type="button"
            class="feedback-act-btn"
            :disabled="statusLoadingId === item.id"
            @click="changeStatus(item, 'PENDING')"
          >
            重新打开
          </button>
        </div>
      </header>

      <div class="feedback-content">{{ item.content }}</div>

      <section class="feedback-replies-section">
        <div class="feedback-replies-bar">
          <span class="feedback-replies-title">回复 ({{ item.replyCount ?? countReplies(item.replies) }})</span>
          <button
            type="button"
            class="feedback-text-btn"
            @click="toggleComposer(item.id)"
          >
            {{ isComposerOpen(item.id) ? '收起输入框' : '写回复' }}
          </button>
        </div>

        <div v-if="errorBySuggestion[item.id]" class="feedback-error">
          {{ errorBySuggestion[item.id] }}
        </div>

        <!-- 评论区式两层回复列表（复用日记评论区排版） -->
        <div v-if="item.replies?.length" class="comments feedback-comments">
          <div v-for="reply in item.replies" :key="reply.id" class="comment-thread">
            <div class="comment-main">
              <div class="comment-text">
                <strong>{{ reply.authorName }}</strong>
                <span v-if="reply.authorRole === 'ADMIN'" class="role-badge role-badge-admin">管理员</span>
                <span v-else-if="reply.authorUserId === item.userId" class="role-badge role-badge-owner">反馈人</span>
                <span v-if="reply.replyToUserName" class="reply-to">回复 {{ reply.replyToUserName }}</span>
                <span class="comment-time">{{ formatTime(reply.createdAt) }}</span>
              </div>
              <div class="comment-body feedback-reply-body">{{ reply.content }}</div>
              <div class="comment-foot">
                <button
                  v-if="canDeleteReply(reply)"
                  type="button"
                  class="feedback-text-btn feedback-text-danger"
                  :disabled="deletingReplyId === reply.id"
                  @click="handleDeleteReply(item.id, reply.id)"
                >
                  删除
                </button>
                <button
                  type="button"
                  class="feedback-text-btn"
                  @click="startNestedReply(item.id, reply.id, reply.id, reply.authorName)"
                >
                  回复
                </button>
              </div>
            </div>

            <!-- 子回复列表 -->
            <div v-if="reply.replies?.length" class="comment-replies">
              <div v-for="child in reply.replies" :key="child.id" class="reply-item">
                <div class="comment-text">
                  <strong>{{ child.authorName }}</strong>
                  <span v-if="child.authorRole === 'ADMIN'" class="role-badge role-badge-admin">管理员</span>
                  <span v-else-if="child.authorUserId === item.userId" class="role-badge role-badge-owner">反馈人</span>
                  <span v-if="child.replyToUserName" class="reply-to">回复 {{ child.replyToUserName }}</span>
                  <span class="comment-time">{{ formatTime(child.createdAt) }}</span>
                </div>
                <div class="comment-body feedback-reply-body">{{ child.content }}</div>
                <div class="comment-foot">
                  <button
                    v-if="canDeleteReply(child)"
                    type="button"
                    class="feedback-text-btn feedback-text-danger"
                    :disabled="deletingReplyId === child.id"
                    @click="handleDeleteReply(item.id, child.id)"
                  >
                    删除
                  </button>
                  <button
                    type="button"
                    class="feedback-text-btn"
                    @click="startNestedReply(item.id, child.id, reply.id, child.authorName)"
                  >
                    回复
                  </button>
                </div>
              </div>
            </div>

            <!-- 针对某条回复的行内回复输入框 -->
            <div
              v-if="activeReplyTarget?.suggestionId === item.id && activeReplyTarget?.threadId === reply.id"
              class="comment-reply-box feedback-inline-reply-box"
            >
              <div class="reply-target-tip">
                <span>正在回复 {{ activeReplyTarget.targetName }}</span>
                <button type="button" class="feedback-text-btn" @click="cancelNestedReply">取消</button>
              </div>
              <div class="feedback-reply-input-row">
                <n-input
                  v-model:value="nestedDraft"
                  :placeholder="`回复 ${activeReplyTarget.targetName}...`"
                  :maxlength="1000"
                  :disabled="sendingSuggestionId === item.id"
                  size="small"
                  @keyup.enter="submitReply(item.id, activeReplyTarget.parentReplyId)"
                />
                <n-button
                  size="small"
                  type="primary"
                  :loading="sendingSuggestionId === item.id"
                  :disabled="!nestedDraft.trim() || sendingSuggestionId === item.id"
                  @click="submitReply(item.id, activeReplyTarget.parentReplyId)"
                >
                  回复
                </n-button>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="feedback-no-replies">
          {{ isAdminMode ? '暂未回复，点击下方输入框回应这条反馈。' : '暂未收到回复，你可以继续补充说明或等待管理员回应。' }}
        </div>

        <!-- 直接回复反馈的输入框 -->
        <div
          v-if="isComposerOpen(item.id) && activeReplyTarget?.suggestionId !== item.id"
          class="comment-box feedback-root-composer"
        >
          <n-input
            v-model:value="rootDrafts[item.id]"
            :placeholder="isAdminMode ? `回复 ${item.userName} 的反馈...` : '补充说明或回复...'"
            :maxlength="1000"
            :disabled="sendingSuggestionId === item.id"
            @keyup.enter="submitReply(item.id, null)"
          />
          <n-button
            type="primary"
            size="small"
            :loading="sendingSuggestionId === item.id"
            :disabled="!(rootDrafts[item.id] || '').trim() || sendingSuggestionId === item.id"
            @click="submitReply(item.id, null)"
          >
            发送
          </n-button>
        </div>
      </section>
    </article>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { NButton, NInput } from 'naive-ui'
import { suggestionApi, type SuggestionItem, type SuggestionReply, type SuggestionStatus } from '../../api'
import { useAuthStore } from '../../stores/auth'

const props = withDefaults(defineProps<{
  items: SuggestionItem[]
  isAdminMode?: boolean
  highlightId?: number | null
}>(), {
  isAdminMode: false,
  highlightId: null,
})

const emit = defineEmits<{
  (e: 'updated', item: SuggestionItem): void
}>()

const auth = useAuthStore()

const rootDrafts = reactive<Record<number, string>>({})
const errorBySuggestion = reactive<Record<number, string>>({})
const openComposers = reactive<Record<number, boolean>>({})

const activeReplyTarget = ref<{
  suggestionId: number
  parentReplyId: number
  threadId: number
  targetName: string
} | null>(null)
const nestedDraft = ref('')
const sendingSuggestionId = ref<number | null>(null)
const deletingReplyId = ref<number | null>(null)
const statusLoadingId = ref<number | null>(null)

function isComposerOpen(suggestionId: number) {
  if (openComposers[suggestionId] !== undefined) {
    return openComposers[suggestionId]
  }
  return props.highlightId === suggestionId || props.items.length <= 3
}

function toggleComposer(suggestionId: number) {
  const next = !isComposerOpen(suggestionId)
  openComposers[suggestionId] = next
  if (!next && activeReplyTarget.value?.suggestionId === suggestionId) {
    cancelNestedReply()
  }
}

function startNestedReply(suggestionId: number, parentReplyId: number, threadId: number, targetName: string) {
  if (
    activeReplyTarget.value?.suggestionId === suggestionId
    && activeReplyTarget.value?.parentReplyId === parentReplyId
  ) {
    cancelNestedReply()
    return
  }
  activeReplyTarget.value = { suggestionId, parentReplyId, threadId, targetName }
  nestedDraft.value = ''
  errorBySuggestion[suggestionId] = ''
}

function cancelNestedReply() {
  activeReplyTarget.value = null
  nestedDraft.value = ''
}

function canDeleteReply(reply: SuggestionReply) {
  if (auth.isAdmin) return true
  return auth.userId != null && reply.authorUserId === auth.userId
}

async function submitReply(suggestionId: number, parentReplyId: number | null) {
  const content = (parentReplyId != null ? nestedDraft.value : (rootDrafts[suggestionId] || '')).trim()
  if (!content || sendingSuggestionId.value === suggestionId) return

  sendingSuggestionId.value = suggestionId
  errorBySuggestion[suggestionId] = ''
  try {
    const res = await suggestionApi.addReply(suggestionId, content, parentReplyId)
    const updated: SuggestionItem = res.data.data
    emit('updated', updated)
    if (parentReplyId != null) {
      cancelNestedReply()
    } else {
      rootDrafts[suggestionId] = ''
    }
    window.$message?.success('回复已发送')
  } catch (err: any) {
    errorBySuggestion[suggestionId] = err?.response?.data?.message || '发送回复失败，请稍后重试'
  } finally {
    sendingSuggestionId.value = null
  }
}

async function handleDeleteReply(suggestionId: number, replyId: number) {
  if (deletingReplyId.value != null) return
  if (!window.confirm('确定删除这条回复吗？')) return

  deletingReplyId.value = replyId
  errorBySuggestion[suggestionId] = ''
  try {
    const res = await suggestionApi.deleteReply(suggestionId, replyId)
    const updated: SuggestionItem = res.data.data
    emit('updated', updated)
    if (activeReplyTarget.value?.parentReplyId === replyId || activeReplyTarget.value?.threadId === replyId) {
      cancelNestedReply()
    }
    window.$message?.success('回复已删除')
  } catch (err: any) {
    errorBySuggestion[suggestionId] = err?.response?.data?.message || '删除回复失败，请稍后重试'
  } finally {
    deletingReplyId.value = null
  }
}

async function changeStatus(item: SuggestionItem, status: SuggestionStatus) {
  if (statusLoadingId.value === item.id) return
  statusLoadingId.value = item.id
  errorBySuggestion[item.id] = ''
  try {
    const res = await suggestionApi.updateStatus(item.id, status)
    emit('updated', res.data.data)
    window.$message?.success(`已更新为「${statusLabel(status)}」`)
  } catch (err: any) {
    errorBySuggestion[item.id] = err?.response?.data?.message || '更新状态失败'
  } finally {
    statusLoadingId.value = null
  }
}

function statusLabel(status?: string) {
  switch ((status || '').toUpperCase()) {
    case 'RESOLVED':
      return '已解决'
    case 'READ':
      return '跟进中'
    default:
      return '待回复'
  }
}

function countReplies(list?: SuggestionReply[]) {
  if (!list?.length) return 0
  let total = 0
  for (const r of list) {
    total += 1 + (r.replies?.length || 0)
  }
  return total
}

function formatTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<style scoped>
.feedback-thread-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.feedback-entry {
  padding: 20px 0;
  border-bottom: 1px solid var(--color-border);
  transition: background-color 0.2s ease;
}

.feedback-entry:last-child {
  border-bottom: none;
}

.feedback-entry.highlighted {
  padding: 18px 16px;
  border-radius: 8px;
  background: color-mix(in oklab, var(--color-primary) 6%, var(--color-surface));
  border-bottom-color: color-mix(in oklab, var(--color-primary) 24%, var(--color-border));
}

.feedback-entry-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.feedback-author {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.feedback-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  border: 1px solid var(--color-border);
}

.feedback-avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-surface-soft);
  color: var(--color-text-secondary);
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 600;
}

.feedback-author-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.feedback-author-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.feedback-author-name {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.feedback-status-pill {
  font-size: 11px;
  line-height: 1.3;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  background: var(--color-surface-soft);
}

.feedback-status-pill.status-pending {
  color: var(--color-warning);
  border-color: color-mix(in oklab, var(--color-warning) 35%, transparent);
  background: color-mix(in oklab, var(--color-warning) 10%, transparent);
}

.feedback-status-pill.status-read {
  color: var(--color-primary);
  border-color: color-mix(in oklab, var(--color-primary) 35%, transparent);
  background: color-mix(in oklab, var(--color-primary) 10%, transparent);
}

.feedback-status-pill.status-resolved {
  color: var(--color-success);
  border-color: color-mix(in oklab, var(--color-success) 35%, transparent);
  background: color-mix(in oklab, var(--color-success) 10%, transparent);
}

.feedback-privacy-hint {
  font-size: 11px;
  color: var(--color-text-muted);
}

.feedback-time {
  font-size: 12px;
  color: var(--color-text-light);
}

.feedback-admin-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.feedback-act-btn {
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease, color 0.18s ease;
}

.feedback-act-btn:hover:not(:disabled) {
  background: var(--color-surface-hover);
  color: var(--color-text);
}

.feedback-act-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.feedback-act-primary {
  border-color: color-mix(in oklab, var(--color-primary) 35%, transparent);
  color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 8%, transparent);
}

.feedback-act-primary:hover:not(:disabled) {
  border-color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 14%, transparent);
  color: var(--color-primary);
}

.feedback-text-btn {
  padding: 2px 8px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.5;
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.feedback-text-btn:hover:not(:disabled) {
  background: color-mix(in oklab, var(--color-primary) 10%, transparent);
  color: var(--color-text);
}

.feedback-text-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.feedback-text-danger {
  color: var(--color-error);
}

.feedback-text-danger:hover:not(:disabled) {
  background: color-mix(in oklab, var(--color-error) 10%, transparent);
  color: var(--color-error);
}

.feedback-content {
  margin-top: 12px;
  color: var(--color-text);
  font-size: 15px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.feedback-replies-section {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid color-mix(in oklab, var(--color-border) 55%, transparent);
}

.feedback-replies-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.feedback-replies-title {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
}

.feedback-error {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-error);
}

.feedback-comments {
  padding-top: 6px;
}

.feedback-reply-body {
  white-space: pre-wrap;
  word-break: break-word;
}

.role-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 1.4;
  font-weight: 500;
}

.role-badge-admin {
  color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 12%, transparent);
  border: 1px solid color-mix(in oklab, var(--color-primary) 28%, transparent);
}

.role-badge-owner {
  color: var(--color-text-secondary);
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
}

.feedback-no-replies {
  padding: 10px 0 4px;
  font-size: 13px;
  color: var(--color-text-muted);
}

.feedback-root-composer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.feedback-inline-reply-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
  margin-left: 24px;
  padding-left: 16px;
  border-left: 1px solid color-mix(in oklab, var(--color-primary) 24%, transparent);
}

.reply-target-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.feedback-reply-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 640px) {
  .feedback-entry.highlighted {
    padding: 14px 12px;
  }

  .feedback-inline-reply-box {
    margin-left: 12px;
    padding-left: 12px;
  }
}
</style>
