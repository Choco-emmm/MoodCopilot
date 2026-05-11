<template>
  <main class="app-shell">
    <AppHeader />
    <div v-if="diary" class="diary-detail-page">
      <!-- 日记正文 -->
      <article class="panel analysis-panel">
        <div class="diary-content-section">
          <div class="diary-author-row">
            <span class="avatar">{{ diary.authorName.charAt(0) }}</span>
            <span class="author-name">{{ diary.authorName }}</span>
            <span class="diary-time">{{ formatTime(diary.createdAt) }}</span>
            <n-tag :type="diary.visibility === 'PUBLIC' ? 'success' : 'default'" round size="small">
              {{ diary.visibility === 'PUBLIC' ? '公开' : '私密' }}
            </n-tag>
            <n-button v-if="!isOwner" size="tiny" text @click="reportDiary">举报</n-button>
            <n-button v-if="!isOwner" size="tiny" text @click="hideDiary">隐藏</n-button>
          </div>
          <p class="diary-content">{{ diary.content }}</p>
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
        <div class="comment-box">
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
                <n-button size="tiny" text @click="replyTo = replyTo === c.id ? null : c.id">回复</n-button>
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
                <n-button size="tiny" text @click="reportComment(r.id)">举报</n-button>
              </div>
            </div>

            <!-- 回复输入框 -->
            <div v-if="replyTo === c.id" class="comment-reply-box">
              <n-input
                v-model:value="replyDraft"
                placeholder="回复 {{ c.authorName }}..."
                size="small"
                @keyup.enter="submitComment(c.id)"
              />
              <n-button size="tiny" type="primary" :disabled="!replyDraft.trim()" @click="submitComment(c.id)">
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
import { ref, computed, onMounted } from 'vue'
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
const replyTo = ref<number | null>(null)
const sending = ref(false)

const isOwner = computed(() => auth.userId != null && diary.value != null && auth.userId === diary.value.authorUserId)

onMounted(async () => {
  const id = Number(route.params.id)
  try {
    const res = await diaryApi.get(id)
    diary.value = store.normalize(res.data.data)
    await store.loadSimilar(id)
  } catch {
    diary.value = null
  }
})

async function submitComment(parentId: number | null) {
  const content = parentId ? replyDraft.value.trim() : commentDraft.value.trim()
  if (!content || !diary.value || sending.value) return
  sending.value = true
  try {
    const res = await diaryApi.addComment(diary.value.id, content, parentId ?? undefined)
    diary.value = store.normalize(res.data.data)
    commentDraft.value = ''
    replyDraft.value = ''
    replyTo.value = null
  } catch { /* ignore */ }
  sending.value = false
}

async function hideDiary() {
  if (!diary.value) return
  await store.hideDiary(diary.value.id)
  router.push('/')
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

function selectDiary(d: Diary) {
  diary.value = store.normalize(d)
  store.loadSimilar(d.id)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>
