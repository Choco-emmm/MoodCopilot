<template>
  <section class="composer panel">
    <div class="section-title">
      <div>
        <p class="eyebrow">{{ isEditMode ? '编辑日记' : '今日日记' }}</p>
        <h2>{{ isEditMode ? '修改这篇日记' : '此刻发生了什么' }}</h2>
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

    <div class="composer-editor">
      <div id="vditor-composer"></div>
    </div>

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

    <!-- 图片上传 -->
    <div class="composer-images-section">
      <div class="composer-images-grid">
        <div v-for="(img, i) in imageList" :key="i" class="composer-image-preview">
          <img :src="img" alt="" />
          <button class="composer-image-remove" @click="removeImage(i)">✕</button>
        </div>
        <label class="composer-image-add" :class="{ uploading: uploadingImage }">
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif,image/heic"
            :disabled="uploadingImage || imageList.length >= 9"
            hidden
            @change="handleImageSelect"
          />
          <span v-if="uploadingImage">上传中...</span>
          <span v-else>+ 添加图片</span>
        </label>
      </div>
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
          {{ isEditMode ? '保存修改' : '保存并分析' }}
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
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import Vditor from 'vditor'
import 'vditor/dist/index.css'
import { useDiaryStore, type MusicMeta } from '../stores/diary'
import { musicApi, imageApi } from '../api'
import MusicCard from './MusicCard.vue'

const props = withDefaults(defineProps<{
  editId?: number
  initialContent?: string
  initialVisibility?: 'PRIVATE' | 'PUBLIC'
  initialMusicMeta?: MusicMeta | null
  initialImages?: string[]
  initialLyric?: string
  initialSongUrl?: string
}>(), {})

const store = useDiaryStore()
const router = useRouter()
const draft = ref('')
const draftNotice = ref('')
const draftSavedAt = ref('')
const isEditMode = computed(() => props.editId != null && props.editId > 0)
const visibility = ref<'PRIVATE' | 'PUBLIC'>('PRIVATE')
const analyze = ref(true)
const DRAFT_KEY = 'moodcopilot:draft'

const vditorInst = ref<Vditor | null>(null)

const musicMeta = ref<MusicMeta | null>(null)
const musicSongUrl = ref('')
const userLyric = ref('')
const musicParsing = ref(false)
const showMusicInput = ref(false)
const musicUrlDraft = ref('')
const musicUrlInput = ref<HTMLInputElement | null>(null)

const imageList = ref<string[]>([])
const uploadingImage = ref(false)

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
  let initialValue = ''
  if (isEditMode.value) {
    initialValue = props.initialContent || ''
    visibility.value = props.initialVisibility || 'PRIVATE'
    if (props.initialMusicMeta) {
      musicMeta.value = props.initialMusicMeta
      userLyric.value = props.initialLyric || props.initialMusicMeta.userLyric || ''
      musicSongUrl.value = props.initialSongUrl || props.initialMusicMeta.songUrl || ''
    }
    if (props.initialImages?.length) {
      imageList.value = [...props.initialImages]
    }
    draftNotice.value = '正在编辑日记'
    updateDraftSavedAt()
  } else {
    const savedDraft = localStorage.getItem(DRAFT_KEY)
    if (savedDraft) {
      draftNotice.value = '已恢复本机草稿'
      initialValue = savedDraft
      updateDraftSavedAt()
    }
  }

  vditorInst.value = new Vditor('vditor-composer', {
    mode: 'ir',
    height: 360,
    cdn: 'https://cdn.jsdelivr.net/npm/vditor@3.11.2',
    placeholder: '今天发生了什么？可以只写一句，也可以把说不清的感觉先放在这里。',
    cache: { enable: false },
    outline: { enable: false, position: 'right' },
    toolbar: [
      'headings', 'bold', 'italic', 'strike', 'line', 'quote', 'list', 'ordered-list', 'check', 'outdent', 'indent', 'code', 'inline-code', 'undo', 'redo'
    ],
    after: () => {
      if (initialValue) {
        vditorInst.value?.setValue(initialValue)
      }
      draft.value = initialValue
    },
    input: (val: string) => {
      draft.value = val
    }
  })
})

onBeforeUnmount(() => {
  vditorInst.value?.destroy()
})

// 粘贴/输入音乐链接后自动解析，无需手动 Enter
watch(musicUrlDraft, (val) => {
  if (!val) return
  const url = detectMusicUrl(val)
  if (url) submitMusicUrl(url, val)
})

watch(draft, (value, oldValue) => {
  // 编辑模式下不写草稿，避免旧日记内容污染新日记草稿
  if (isEditMode.value) return
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

const MUSIC_URL_PATTERN = /https?:\/\/(?:(?:[a-z0-9]+\.)?music\.163\.com|163cn\.tv)\/[^\s]+/

function detectMusicUrl(text: string): string | null {
  const m = text.match(MUSIC_URL_PATTERN)
  return m ? m[0] : null
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
  musicUrlDraft.value = url // watch 自动触发解析
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

async function handleImageSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadingImage.value = true
  try {
    const url = await imageApi.uploadDirect(file)
    if (url) imageList.value.push(url)
  } catch { /* silently ignore */ }
  finally {
    uploadingImage.value = false
    input.value = ''
  }
}

function removeImage(i: number) {
  imageList.value.splice(i, 1)
}


async function handleSave() {
  if (!draft.value.trim()) return
  try {
    const musicPayload = musicMeta.value
      ? { ...musicMeta.value, userLyric: userLyric.value, songUrl: musicSongUrl.value }
      : undefined
    const imagesPayload = imageList.value.length ? imageList.value : undefined

    if (isEditMode.value) {
      await store.updateDiary(props.editId!, draft.value.trim(), visibility.value, musicPayload, imagesPayload)
      router.push(`/diary/${props.editId}`)
    } else {
      await store.createDiary(draft.value.trim(), visibility.value, musicPayload, analyze.value, imagesPayload)
      draft.value = ''
      vditorInst.value?.setValue('')
      musicMeta.value = null
      userLyric.value = ''
      imageList.value = []
      localStorage.removeItem(DRAFT_KEY)
    }
  } catch {
    // error handled by store
  }
}

</script>

<style scoped>
.composer-editor {
  height: 360px;
  margin-bottom: 4px;
}

.composer-editor :deep(.vditor) {
  border: 1px solid rgba(180, 150, 120, 0.25) !important;
  border-radius: var(--radius-sm, 6px);
  overflow: hidden;
  --vditor-toolbar-background-color: #fdfcf8;
  --vditor-toolbar-border-color: rgba(180, 150, 120, 0.15);
}

.composer-editor :deep(.vditor-toolbar) {
  padding: 4px 8px;
}

.composer-editor :deep(.vditor-toolbar__item > button) {
  color: #8a7a6a;
}

.composer-editor :deep(.vditor-toolbar__item > button:hover) {
  background-color: rgba(180, 150, 120, 0.1);
  color: #4a7c62;
}

@media (max-width: 780px) {
  .composer-editor :deep(.vditor-toolbar) {
    flex-wrap: nowrap !important;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
  }
  .composer-editor :deep(.vditor-toolbar::-webkit-scrollbar) {
    display: none;
  }
  .composer-editor :deep(.vditor-toolbar__item) {
    flex-shrink: 0;
  }
}

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

/* ── 图片上传 ── */
.composer-images-section {
  margin-top: 10px;
}

.composer-images-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: flex-start;
}

.composer-image-preview {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: #f5f0e8;
}

.composer-image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.composer-image-remove {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 10px;
  line-height: 18px;
  cursor: pointer;
  text-align: center;
}

.composer-image-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 8px;
  border: 1.5px dashed rgba(180, 150, 120, 0.35);
  background: transparent;
  cursor: pointer;
  color: #b0a090;
  font-size: 11px;
  transition: border-color 0.15s, color 0.15s;
  gap: 2px;
}

.composer-image-add:hover {
  border-color: var(--color-primary, #4a7c62);
  color: var(--color-primary, #4a7c62);
}

.composer-image-add.uploading {
  cursor: wait;
  opacity: 0.6;
}
</style>

