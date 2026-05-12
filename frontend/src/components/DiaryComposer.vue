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
      <template v-else-if="store.analysisStatus === 'complete'">分析完成，可以在上方查看结果。</template>
      <template v-else-if="store.analysisStatus === 'failed'">
        分析结果暂时没有更新。
        <button v-if="store.activeDiary" class="inline-link" @click="store.refreshAnalysis(store.activeDiary.id)">重新获取分析结果</button>
      </template>
    </div>

    <n-alert v-if="store.errorMessage" type="error" :show-icon="false">
      {{ store.errorMessage }}
    </n-alert>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useDiaryStore } from '../stores/diary'

const store = useDiaryStore()
const draft = ref('')
const draftNotice = ref('')
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
</script>
