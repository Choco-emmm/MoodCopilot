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
      @paste="handlePaste"
    />

    <div v-if="musicParsing" class="music-parsing">
      正在解析音乐链接...
    </div>
    <div v-else-if="musicMeta" class="music-preview-wrap">
      <MusicCard
        :music-meta="musicMeta"
        :lyric="userLyric"
        :show-lyric="true"
        @update:lyric="userLyric = $event"
      />
      <button class="music-remove-btn" @click="removeMusic">✕ 移除音乐</button>
    </div>

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

  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useDiaryStore, type MusicMeta } from '../stores/diary'
import { musicApi } from '../api'
import MusicCard from './MusicCard.vue'

const store = useDiaryStore()
const draft = ref('')
const draftNotice = ref('')
const draftSavedAt = ref('')
const visibility = ref<'PRIVATE' | 'PUBLIC'>('PRIVATE')
const DRAFT_KEY = 'moodcopilot:draft'

const musicMeta = ref<MusicMeta | null>(null)
const userLyric = ref('')
const musicParsing = ref(false)

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

const MUSIC_URL_PATTERN = /https?:\/\/music\.163\.com\/[^\s]+/

function detectMusicUrl(text: string): string | null {
  const m = text.match(MUSIC_URL_PATTERN)
  return m ? m[0] : null
}

async function handlePaste(e: ClipboardEvent) {
  const text = e.clipboardData?.getData('text/plain')
  if (!text) return
  const url = detectMusicUrl(text)
  if (!url) return
  // Don't parse if already have music attached
  if (musicMeta.value) return

  musicParsing.value = true
  try {
    const res = await musicApi.parse(url)
    if (res.data?.data) {
      musicMeta.value = res.data.data as MusicMeta
    }
  } catch {
    // if parse fails, ignore silently
  } finally {
    musicParsing.value = false
  }
}

function removeMusic() {
  musicMeta.value = null
  userLyric.value = ''
}

async function handleSave() {
  if (!draft.value.trim()) return
  try {
    const payload = musicMeta.value
      ? { ...musicMeta.value, userLyric: userLyric.value }
      : undefined
    await store.createDiary(draft.value.trim(), visibility.value, payload)
    draft.value = ''
    musicMeta.value = null
    userLyric.value = ''
    localStorage.removeItem(DRAFT_KEY)
  } catch {
    // error handled by store
  }
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

.music-parsing {
  margin: 8px 0;
  padding: 10px 12px;
  border-radius: var(--radius-md, 10px);
  background: #fdf6f0;
  border: 1px solid rgba(180, 150, 120, 0.12);
  font-size: 13px;
  color: #8a7a6a;
  text-align: center;
}

.music-preview-wrap {
  position: relative;
}

.music-remove-btn {
  margin-top: 6px;
  background: none;
  border: none;
  color: #b0a090;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 0;
}

.music-remove-btn:hover {
  color: #a94b45;
}

</style>
