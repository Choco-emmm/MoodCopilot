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
            <span class="author-name">{{ diary.authorName }}</span>
            <span class="diary-time">{{ formatTime(diary.createdAt) }}</span>
            <n-tag :type="diary.visibility === 'PUBLIC' ? 'success' : 'default'" round size="small">
              {{ diary.visibility === 'PUBLIC' ? '公开' : '私密' }}
            </n-tag>
            <n-button v-if="!isOwner" size="tiny" text @click="reportDiary">举报</n-button>
          </div>
          <p class="diary-content">{{ diary.content }}</p>
          <div class="detail-actions">
            <n-button size="small" tertiary :disabled="resonating" @click="resonateDiary">
              👍 {{ diary.resonanceCount ?? 0 }}
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
      <SimilarDiariesPanel :diaries="store.similarDiaries" @select="selectDiary" />
    </div>
    <n-empty v-else description="日记不存在" />
  </main>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NInput, NTag, NEmpty } from 'naive-ui'
import { diaryApi, reportApi } from '../api'
import { useAuthStore } from '../stores/auth'
import AppHeader from '../components/AppHeader.vue'
import AiAnalysisCard from '../components/AiAnalysisCard.vue'
import SimilarDiariesPanel from '../components/SimilarDiariesPanel.vue'
import { useDiaryStore, type Diary } from '../stores/diary'

const route = useRoute()
const router = useRouter()
const store = useDiaryStore()
const auth = useAuthStore()
const diary = ref<Diary | null>(null)
const commentDraft = ref('')
const replyDraft = ref('')
const commentError = ref('')
const replyParentId = ref<number | null>(null)
const replyThreadId = ref<number | null>(null)
const replyTargetName = ref('')
const sending = ref(false)
const resonating = ref(false)

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
    commentDraft.value = ''
    replyDraft.value = ''
    commentError.value = ''
    cancelReply()
  } catch (e: any) {
    commentError.value = e?.response?.data?.message || '评论发送失败，请稍后重试'
  }
  sending.value = false
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

async function reportComment(commentId: number) {
  const reason = window.prompt('请简单说明举报原因')
  if (!reason?.trim()) return
  await reportApi.create({ targetType: 'COMMENT', targetId: commentId, reason: reason.trim() })
}

async function resonateDiary() {
  if (!diary.value || resonating.value) return
  resonating.value = true
  try {
    const res = await diaryApi.resonate(diary.value.id)
    diary.value = store.normalize(res.data.data)
  } finally {
    resonating.value = false
  }
}

function selectDiary(d: Diary) {
  void router.push(`/diary/${d.id}`)
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
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
</style>
