<template>
  <section class="composer panel">
    <div class="section-title">
      <div>
        <p class="eyebrow">今日日记</p>
        <h2>此刻发生了什么</h2>
      </div>
      <div class="composer-toggles">
        <label class="analyze-toggle">
          <input type="checkbox" v-model="analyze" />
          <span>AI 分析</span>
        </label>
        <n-radio-group v-model:value="visibility" size="small">
          <n-radio-button v-for="opt in visibilityOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
        </n-radio-group>
      </div>
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

    <div class="composer-toolbar">
      <button
        v-if="!musicMeta && !musicParsing && !showMusicInput"
        class="music-attach-btn"
        type="button"
        @click="showMusicInput = true"
      >
        <span class="music-attach-icon">🎵</span> 分享音乐
      </button>

      <div v-if="showMusicInput && !musicMeta" class="music-input-row">
        <input
          ref="musicUrlInput"
          v-model="musicUrlDraft"
          class="music-url-input"
          type="url"
          placeholder="粘贴网易云音乐链接..."
          @paste="handleMusicInputPaste"
          @keyup.enter="handleMusicUrlSubmit"
        />
      </div>
    </div>

    <div v-if="musicParsing" class="music-parsing">
      正在解析音乐链接...
    </div>
    <div v-else-if="musicMeta" class="music-preview-wrap">
      <MusicCard
        :music-meta="musicMeta"
        :lyric="userLyric"
        :show-lyric="true"
        :song-url="musicSongUrl"
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
const analyze = ref(true)
const DRAFT_KEY = 'moodcopilot:draft'

const musicMeta = ref<MusicMeta | null>(null)
const musicSongUrl = ref('')
const userLyric = ref('')
const musicParsing = ref(false)
const showMusicInput = ref(false)
const musicUrlDraft = ref('')
const musicUrlInput = ref<HTMLInputElement | null>(null)

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

const MUSIC_URL_PATTERN = /https?:\/\/(?:music\.163\.com|163cn\.tv)\/[^\s]+/

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

  await submitMusicUrl(url, text)
}

function removeMusic() {
  musicMeta.value = null
  musicSongUrl.value = ''
  userLyric.value = ''
  showMusicInput.value = false
  musicUrlDraft.value = ''
}

async function handleMusicInputPaste(e: ClipboardEvent) {
  const text = e.clipboardData?.getData('text/plain')
  if (!text) return
  const url = detectMusicUrl(text)
  if (!url) return
  e.preventDefault()
  musicUrlDraft.value = url
  await submitMusicUrl(url, text)
}

async function handleMusicUrlSubmit() {
  const url = musicUrlDraft.value.trim()
  if (!url) return
  const detected = detectMusicUrl(url)
  await submitMusicUrl(detected || url, url)
}

async function submitMusicUrl(url: string, fullText?: string) {
  if (musicMeta.value || musicParsing.value) return
  musicParsing.value = true
  try {
    const res = await musicApi.parse(url, fullText)
    if (res.data?.data) {
      musicMeta.value = res.data.data as MusicMeta
      musicSongUrl.value = url
      showMusicInput.value = false
      musicUrlDraft.value = ''
    }
  } catch {
    // if parse fails, ignore silently
  } finally {
    musicParsing.value = false
  }
}

async function handleSave() {
  if (!draft.value.trim()) return
  try {
    const payload = musicMeta.value
      ? { ...musicMeta.value, userLyric: userLyric.value, songUrl: musicSongUrl.value }
      : undefined
    await store.createDiary(draft.value.trim(), visibility.value, payload, analyze.value)
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
.composer-toggles {
  display: flex;
  align-items: center;
  gap: 12px;
}

.analyze-toggle {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: #6a5a4a;
  cursor: pointer;
  user-select: none;
}

.analyze-toggle input[type="checkbox"] {
  accent-color: var(--color-primary, #4a7c62);
  width: 15px;
  height: 15px;
  cursor: pointer;
}

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

.composer-toolbar {
  margin: 8px 0;
}

.music-attach-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px dashed #b0a090;
  border-radius: var(--radius-sm, 6px);
  background: transparent;
  color: #8a7a6a;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
  font-family: inherit;
}

.music-attach-btn:hover {
  border-color: var(--color-primary, #4a7c62);
  color: var(--color-primary, #4a7c62);
}

.music-attach-icon {
  font-size: 15px;
}

.music-input-row {
  display: flex;
  gap: 6px;
  align-items: center;
}

.music-url-input {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid rgba(180, 150, 120, 0.2);
  border-radius: var(--radius-sm, 6px);
  font-size: 13px;
  outline: none;
  background: #fdfcf8;
  color: #5a4a3a;
  font-family: inherit;
}

.music-url-input::placeholder {
  color: #b0a090;
  font-size: 12px;
}

.music-url-input:focus {
  border-color: var(--color-primary, #4a7c62);
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
