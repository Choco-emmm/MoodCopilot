<template>
  <section class="composer">
    <!-- ★ 杂志风标题区 -->
    <div class="composer-header">
      <span class="composer-eyebrow">{{ isEditMode ? '编辑日记' : '今日日记' }}</span>
      <h1 class="composer-title">{{ isEditMode ? '修改这篇日记' : '此刻发生了什么' }}</h1>
      <p class="composer-subtitle">不需要完美的文字，把此刻的感受放在这里就好。</p>
    </div>

    <!-- 控制栏：AI分析 + 可见性 -->
    <div class="composer-controls">
      <label class="composer-ai-toggle">
        <input type="checkbox" v-model="analyze" />
        <span>AI 分析我的情绪</span>
      </label>
      <div class="composer-visibility">
        <button
          v-for="opt in visibilityOptions"
          :key="opt.value"
          :class="['composer-vis-opt', { active: visibility === opt.value }]"
          @click="visibility = opt.value as 'PRIVATE' | 'PUBLIC'"
        >{{ opt.label }}</button>
      </div>
    </div>

    <!-- ★ 编辑器：纸质感 -->
    <div class="composer-editor">
      <div ref="vditorContainer"></div>
    </div>

    <!-- 音乐附件 -->
    <div class="composer-music">
      <button
        v-if="!musicMeta && !musicParsing && !showMusicInput"
        class="composer-music-attach"
        type="button"
        @click="showMusicInput = true"
      >
        <span class="composer-music-attach-icon">🎵</span> 分享一首今天的歌...
      </button>

      <div v-if="showMusicInput && !musicMeta" class="composer-music-input">
        <input
          ref="musicUrlInput"
          v-model="musicUrlDraft"
          class="composer-music-url"
          type="url"
          placeholder="粘贴网易云音乐链接..."
          @paste="handleMusicInputPaste"
          @keyup.enter="handleMusicUrlSubmit"
        />
      </div>
    </div>

    <div v-if="musicParsing" class="composer-music-parsing">正在解析音乐链接...</div>
    <div v-else-if="musicMeta" class="composer-music-preview-wrap">
      <MusicCard
        :music-meta="musicMeta"
        :lyric="userLyric"
        :show-lyric="true"
        :song-url="musicSongUrl"
        @update:lyric="userLyric = $event"
      />
      <button class="composer-music-remove" @click="removeMusic">✕ 移除音乐</button>
    </div>

    <!-- 图片上传 -->
    <div class="composer-images">
      <div class="composer-images-grid">
        <div v-for="(img, i) in imageList" :key="i" class="composer-image-thumb">
          <img :src="img" alt="" loading="lazy" decoding="async" @click="previewSrc = img" />
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
          <template v-if="uploadingImage">
            <span class="composer-upload-text">上传中...</span>
          </template>
          <template v-else>
            <span class="composer-image-add-plus">+</span>
            <span>添加图片</span>
          </template>
        </label>
      </div>
    </div>

    <!-- 提示文字 -->
    <p class="composer-hint">
      写得越具体，MoodCopilot 越能理解你在意的人和事。持续记录比一次写满更重要。
    </p>

    <!-- 底部操作栏 -->
    <div class="composer-footer">
      <div class="composer-footer-left">
        <p class="composer-privacy-note">{{ visibilityCopy }}</p>
        <span v-if="draftNotice" class="composer-draft-note">
          <span class="composer-draft-dot" />
          {{ draftNotice }}<template v-if="draftSavedAt"> · {{ draftSavedAt }}</template>
        </span>
      </div>
      <button
        class="composer-submit"
        :disabled="!draft.trim() || isOverLimit"
        @click="handleSave"
      >
        {{ isEditMode ? '保存修改' : (analyze ? '保存并分析' : '保存') }}
      </button>
    </div>

    <!-- 分析状态 -->
    <div v-if="store.analysisStatus !== 'idle'" class="composer-status">
      <template v-if="store.analysisStatus === 'analyzing'">已保存，MoodCopilot 正在分析中...</template>
      <template v-else-if="store.analysisStatus === 'complete'">分析完成</template>
      <template v-else-if="store.analysisStatus === 'failed'">
        分析结果暂时没有更新。
        <button v-if="store.activeDiary" class="composer-inline-link" @click="store.refreshAnalysis(store.activeDiary.id)">重新获取分析结果</button>
      </template>
    </div>

    <div v-if="store.errorMessage" class="composer-error">{{ store.errorMessage }}</div>

  </section>

  <!-- 图片预览 Lightbox -->
  <Teleport to="body">
    <div v-if="previewSrc" class="composer-lightbox" @click="previewSrc = ''">
      <img :src="previewSrc" alt="" @click.stop />
      <button class="composer-lightbox-close" @click="previewSrc = ''">&times;</button>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import type Vditor from 'vditor'
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

const vditorContainer = ref<HTMLElement | null>(null)
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
const previewSrc = ref('')

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

const isOverLimit = computed(() => draft.value.length > 3000)

onMounted(async () => {
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

  const VditorModule = await import('vditor')
  const Vditor = VditorModule.default

  if (!vditorContainer.value) return

  vditorInst.value = new Vditor(vditorContainer.value, {
    mode: 'ir',
    height: 'auto',
    minHeight: 260,
    cdn: 'https://cdn.jsdelivr.net/npm/vditor@3.11.2',
    placeholder: '今天发生了什么？可以只写一句，也可以把说不清的感觉先放在这里。',
    cache: { enable: false },
    counter: { enable: true, max: 3000, type: 'text' },
    outline: { enable: false, position: 'right' },
    toolbar: window.innerWidth <= 780
      ? ['bold', 'italic', 'strike', 'line', 'list', 'ordered-list', 'undo', 'redo']
      : ['headings', 'bold', 'italic', 'strike', 'line', 'quote', 'list', 'ordered-list', 'check', 'outdent', 'indent', 'code', 'inline-code', 'undo', 'redo'],
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
  try {
    vditorInst.value?.destroy()
  } catch (e) {
    console.warn('Vditor destory error ignored:', e)
  }
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
  } catch (e: any) {
    const msg = e?.response?.data?.message || '解析失败，请检查链接'
    window.$message?.error(msg)
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
  } catch (e: any) {
    const msg = e?.response?.data?.message || '图片上传失败，请稍后重试'
    window.$message?.error(msg)
  }
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
      await store.updateDiary(props.editId!, draft.value.trim(), visibility.value, musicPayload, imagesPayload, analyze.value)
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
/* ═══════════════════════════════════════════
   写日记 · 杂志编辑风
   纸质感编辑器 · 温柔引导 · 去框架
   ═══════════════════════════════════════════ */

.composer {
  display: grid;
  gap: 0;
}

/* ── 标题区 ── */
.composer-header {
  margin-bottom: 28px;
  margin-top: -16px;
}

.composer-eyebrow {
  font-size: 10px;
  font-weight: 700;
  color: var(--color-accent);
  text-transform: uppercase;
  letter-spacing: 0.12em;
  display: block;
  margin-bottom: 6px;
}

.composer-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 600;
  color: var(--color-text);
  letter-spacing: 0.02em;
  line-height: 1.2;
}

.composer-subtitle {
  margin: 6px 0 0;
  font-size: 0.9rem;
  color: var(--color-text-muted);
  font-style: italic;
}

/* ── 控制栏 ── */
.composer-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid color-mix(in oklab, var(--color-primary) 12%, transparent);
}

.composer-ai-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  cursor: pointer;
  user-select: none;
}

.composer-ai-toggle input[type="checkbox"] {
  accent-color: var(--color-primary);
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.composer-visibility {
  display: flex;
  gap: 0;
  border: 1.5px solid var(--color-border);
  border-radius: 10px;
  overflow: hidden;
}

.composer-vis-opt {
  padding: 7px 18px;
  border: none;
  background: transparent;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.composer-vis-opt:first-child {
  border-right: 1px solid var(--color-border);
}

.composer-vis-opt:hover {
  background: color-mix(in oklab, var(--color-primary) 8%, transparent);
  color: var(--color-primary);
}

.composer-vis-opt.active {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

/* ── 编辑器：纸质感 ── */
.composer-editor {
  margin-bottom: 20px;
  width: 100%;
  min-width: 0;
}

.composer-editor :deep(.vditor) {
  position: relative !important;
  border: none !important;
  border-radius: 14px !important;
  background: var(--color-surface) !important;
  background-image:
    linear-gradient(135deg, var(--color-surface) 0%, color-mix(in oklab, var(--color-primary) 1.5%, var(--color-surface)) 100%) !important;
  box-shadow:
    0 1px 2px rgba(32,32,29,0.03),
    0 6px 20px color-mix(in oklab, var(--color-primary) 6%, transparent) !important;
  width: 100% !important;
  max-width: 100% !important;
  box-sizing: border-box !important;
  transition: box-shadow 0.3s var(--ease-out) !important;
  overflow: hidden !important;
  --vditor-toolbar-background-color: transparent;
  --vditor-toolbar-border-color: transparent;
}

.composer-editor:focus-within :deep(.vditor) {
  box-shadow:
    0 1px 2px rgba(32,32,29,0.04),
    0 10px 32px color-mix(in oklab, var(--color-primary) 12%, transparent),
    0 0 0 3px color-mix(in oklab, var(--color-primary) 10%, transparent) !important;
}

.composer-editor :deep(.vditor::before) {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 14px;
  background:
    radial-gradient(ellipse at 20% 80%, color-mix(in oklab, var(--color-primary) 2%, transparent) 0%, transparent 40%),
    radial-gradient(ellipse at 85% 15%, color-mix(in oklab, var(--color-accent) 1.5%, transparent) 0%, transparent 30%);
  pointer-events: none;
  z-index: 0;
}

.composer-editor :deep(.vditor-toolbar) {
  padding: 10px 20px !important;
  border-bottom: 1px solid color-mix(in oklab, var(--color-primary) 8%, transparent) !important;
  background: transparent !important;
}

.composer-editor :deep(.vditor-toolbar__item > button) {
  color: var(--color-text-muted);
  border-radius: 8px;
  transition: all 0.15s;
}

.composer-editor :deep(.vditor-toolbar__item > button:hover) {
  background: color-mix(in oklab, var(--color-primary) 8%, transparent);
  color: var(--color-primary);
}

.composer-editor :deep(.vditor-content) {
  width: 100% !important;
  max-width: 100% !important;
  box-sizing: border-box !important;
  background: transparent !important;
}

.composer-editor :deep(.vditor-ir) {
  padding: 20px 24px 40px 24px !important;
  background: transparent !important;
  color: var(--color-text) !important;
  min-height: 260px;
  position: relative;
  z-index: 1;
}

.composer-editor :deep(.vditor-reset) {
  max-width: none !important;
  padding: 0 !important;
  margin: 0 !important;
  color: var(--color-text) !important;
}

.composer-editor :deep(.vditor-ir pre.vditor-reset) {
  color: var(--color-text-muted) !important;
}

.composer-editor :deep(.vditor-counter) {
  position: absolute !important;
  left: 24px !important;
  right: auto !important;
  bottom: 14px !important;
  top: auto !important;
  background: transparent !important;
  color: var(--color-text-light) !important;
  font-size: 12px;
  pointer-events: none;
  z-index: 10;
}

/* ── 音乐附件 ── */
.composer-music {
  margin-bottom: 4px;
}

.composer-music-attach {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 18px;
  border: 1.5px dashed color-mix(in oklab, var(--color-primary) 25%, transparent);
  border-radius: 12px;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.composer-music-attach:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 3%, transparent);
}

.composer-music-attach-icon { font-size: 15px; }

.composer-music-input {
  display: flex;
  gap: 6px;
  align-items: center;
}

.composer-music-url {
  flex: 1;
  padding: 9px 14px;
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  font-size: 0.85rem;
  outline: none;
  background: var(--color-surface);
  color: var(--color-text);
  font-family: inherit;
  transition: border-color 0.2s;
}

.composer-music-url::placeholder { color: var(--color-text-light); }
.composer-music-url:focus { border-color: var(--color-primary); }

.composer-music-parsing {
  margin: 8px 0;
  padding: 12px 16px;
  border-radius: 10px;
  background: color-mix(in oklab, var(--color-primary) 4%, var(--color-surface));
  border: 1px solid color-mix(in oklab, var(--color-primary) 10%, transparent);
  font-size: 0.82rem;
  color: var(--color-text-secondary);
  text-align: center;
}

.composer-music-preview-wrap {
  position: relative;
  margin-top: 8px;
}

.composer-music-remove {
  margin-top: 6px;
  background: none;
  border: none;
  color: var(--color-text-light);
  font-size: 0.78rem;
  cursor: pointer;
  padding: 2px 0;
  font-family: inherit;
}

.composer-music-remove:hover { color: var(--color-accent); }

/* ── 图片上传 ── */
.composer-images {
  margin-top: 16px;
}

.composer-images-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: flex-start;
}

.composer-image-thumb {
  position: relative;
  width: 76px;
  height: 76px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid color-mix(in oklab, var(--color-primary) 10%, transparent);
  background: var(--color-surface-hover);
  animation: composer-slide-in 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
}

.composer-image-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: zoom-in;
}

.composer-image-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(0,0,0,0.5);
  color: #fff;
  font-size: 11px;
  line-height: 20px;
  cursor: pointer;
  text-align: center;
  opacity: 0;
  transition: opacity 0.15s;
}

.composer-image-thumb:hover .composer-image-remove { opacity: 1; }

.composer-image-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 76px;
  height: 76px;
  border-radius: 10px;
  border: 1.5px dashed color-mix(in oklab, var(--color-primary) 20%, transparent);
  background: transparent;
  cursor: pointer;
  color: var(--color-text-muted);
  font-size: 0.7rem;
  transition: all 0.2s;
  gap: 2px;
}

.composer-image-add:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
