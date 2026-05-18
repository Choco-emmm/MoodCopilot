<template>
  <section class="composer panel">
    <div class="section-title">
      <div>
        <p class="eyebrow">今日日记</p>
        <h2>此刻发生了什么</h2>
      </div>
      <n-radio-group v-model:value="visibility" size="small">
        <n-radio-button v-for="opt in visibilityOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
      </n-radio-group>
    </div>

    <n-input
      v-model:value="draft"
      type="textarea"
      size="large"
      placeholder="今天发生了什么？可以只写一句，也可以把说不清的感觉先放在这里。"
      :autosize="{ minRows: 8, maxRows: 15 }"
      :status="isOverLimit ? 'error' : undefined"
    />

    <p class="composer-hint">
      写得越具体，MoodCopilot 越能理解你在意的人和事。持续记录比一次写满更重要。
    </p>

    <div class="composer-actions">
      <div class="composer-side-copy">
        <span class="privacy-copy">{{ visibilityCopy }}</span>
        <span v-if="draftNotice" class="draft-notice">
          <span class="draft-dot" />
          {{ draftNotice }}<template v-if="draftSavedAt"> · {{ draftSavedAt }}</template>
        </span>
      </div>
      <div class="composer-submit-row">
        <span :class="['composer-count', { over: isOverLimit }]">{{ draft.length }}/1000</span>
        <n-button
          type="primary"
          size="large"
          :loading="store.saving"
          :disabled="!draft.trim() || isOverLimit"
          @click="handleSave"
        >
          保存并分析
        </n-button>
      </div>
    </div>

    <div v-if="store.analysisStatus !== 'idle'" class="composer-status">
      <template v-if="store.analysisStatus === 'analyzing'">已保存，MoodCopilot 正在分析中...</template>
      <template v-else-if="store.analysisStatus === 'complete'">分析完成，结果已经更新在上方。</template>
      <template v-else-if="store.analysisStatus === 'failed'">
        分析结果暂时没有更新。
        <button v-if="store.activeDiary" class="inline-link" @click="store.refreshAnalysis(store.activeDiary.id)">重新获取分析结果</button>
      </template>
    </div>

    <n-alert v-if="store.errorMessage" type="error" :show-icon="false">
      {{ store.errorMessage }}
    </n-alert>

    <!-- 分析完成弹窗 -->
    <n-modal :show="showAnalysisModal" :mask-closable="false" @update:show="onModalUpdate">
      <div class="analysis-modal">
        <div class="modal-header">
          <h3>分析完成</h3>
          <button class="modal-close" @click="closeModal">&times;</button>
        </div>
        <template v-if="store.activeDiary?.analysis">
          <div class="modal-mood">
            <n-tag :type="moodTagType(store.activeDiary.analysis.moodLabel)" size="medium">
              {{ store.activeDiary.analysis.moodLabel }}
            </n-tag>
            <span class="mood-intensity">强度 {{ '★'.repeat(store.activeDiary.analysis.moodIntensity) }}{{ '☆'.repeat(5 - store.activeDiary.analysis.moodIntensity) }}</span>
          </div>
          <template v-if="store.activeDiary.analysis.secondaryMoods?.length">
            <div class="modal-secondary">
              <n-tag v-for="m in store.activeDiary.analysis.secondaryMoods" :key="m" size="small" :bordered="true">
                {{ m }}
              </n-tag>
            </div>
          </template>
          <p class="modal-summary">{{ store.activeDiary.analysis.summary }}</p>
          <p class="modal-feedback">{{ truncatedFeedback }}</p>
        </template>
        <div class="modal-actions">
          <n-button @click="closeModal">关闭</n-button>
          <n-button type="primary" @click="goToDetail">查看完整分析</n-button>
        </div>
      </div>
    </n-modal>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useDiaryStore } from '../stores/diary'

const router = useRouter()
const store = useDiaryStore()
const draft = ref('')
const draftNotice = ref('')
const showAnalysisModal = ref(false)
const shownAnalysisDiaryId = ref<number | null>(null)
const draftSavedAt = ref('')
const visibility = ref<'PRIVATE' | 'PUBLIC'>('PRIVATE')
const DRAFT_KEY = 'moodcopilot:draft'

function updateDraftSavedAt() {
  draftSavedAt.value = new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date())
}

const visibilityOptions = [
  { label: '仅自己看', value: 'PRIVATE' },
  { label: '分享到社区', value: 'PUBLIC' },
]

const visibilityCopy = computed(() =>
  visibility.value === 'PUBLIC'
    ? '公开后，别人能看到正文、作者名和互动入口，但不会看到你的完整 AI 分析。'
    : '私密日记只进入你的个人记录，也会生成 AI 分析。',
)

const isOverLimit = computed(() => draft.value.length > 1000)

onMounted(() => {
  const savedDraft = localStorage.getItem(DRAFT_KEY)
  if (savedDraft) {
    draftNotice.value = '已恢复本机草稿'
    draft.value = savedDraft
    updateDraftSavedAt()
  }
})

watch(draft, (value, oldValue) => {
  if (value) {
    localStorage.setItem(DRAFT_KEY, value)
    updateDraftSavedAt()
    if (draftNotice.value !== '已恢复本机草稿' || oldValue) {
      draftNotice.value = '草稿已自动保存到本机'
    }
  } else {
    localStorage.removeItem(DRAFT_KEY)
    draftNotice.value = ''
    draftSavedAt.value = ''
  }
})

async function handleSave() {
  if (!draft.value.trim()) return
  try {
    await store.createDiary(draft.value.trim(), visibility.value)
    draft.value = ''
    localStorage.removeItem(DRAFT_KEY)
  } catch {
    // error handled by store
  }
}

// 分析完成时弹出弹窗（每个分析只弹一次）
watch(() => store.analysisStatus, (status) => {
  if (status === 'complete' && store.activeDiary?.analysis && store.activeDiary.id !== shownAnalysisDiaryId.value) {
    showAnalysisModal.value = true
  }
})

function onModalUpdate(show: boolean) {
  if (!show) closeModal()
}

function closeModal() {
  showAnalysisModal.value = false
  if (store.activeDiary) {
    shownAnalysisDiaryId.value = store.activeDiary.id
  }
}

function goToDetail() {
  closeModal()
  router.push('/diary/' + store.activeDiary!.id)
}

const truncatedFeedback = computed(() => {
  const fb = store.activeDiary?.analysis?.feedback
  if (!fb) return ''
  return fb.length > 120 ? fb.slice(0, 120) + '...' : fb
})

function moodTagType(mood: string) {
  const positive = ['喜悦', '期待', '兴奋', '自豪', '轻松', '平静', '感恩', '满足']
  return positive.includes(mood) ? 'success' as const : 'warning' as const
}
</script>

<style scoped>
.composer-hint {
  margin: 0;
  padding: 10px 12px;
  border-left: 3px solid #7aa68f;
  border-radius: 8px;
  background: #f4f8f5;
  color: #4d5f54;
  font-size: 12px;
  line-height: 1.7;
}

.analysis-modal {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  max-width: 420px;
  margin: 0 auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  color: #333;
}

.modal-close {
  background: none;
  border: none;
  font-size: 22px;
  color: #999;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.modal-close:hover {
  color: #333;
}

.modal-mood {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.mood-intensity {
  font-size: 13px;
  color: #666;
}

.modal-secondary {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.modal-summary {
  margin: 0 0 8px;
  font-size: 14px;
  color: #555;
  line-height: 1.6;
}

.modal-feedback {
  margin: 0 0 20px;
  font-size: 13px;
  color: #777;
  line-height: 1.6;
  padding: 10px 12px;
  background: #f8f8f8;
  border-radius: 8px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
