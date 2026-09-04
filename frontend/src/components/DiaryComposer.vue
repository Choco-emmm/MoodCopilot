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
      <div v-if="analyze" class="composer-model-choice" role="radiogroup" aria-label="选择分析模型">
        <label v-for="option in analysisModelOptions" :key="option.label" class="composer-model-option">
          <input type="radio" v-model="useReasoning" :value="option.value" />
          <span>{{ option.label }}</span>
        </label>
      </div>
      <div class="composer-visibility">
        <button
          v-for="opt in visibilityOptions"
          :key="opt.value"
          :class="['composer-vis-opt', { active: visibility === opt.value }]"
          @click="visibility = opt.value as 'PRIVATE' | 'PUBLIC'"
        >{{ opt.label }}</button>
      </div>
    </div>

    <!-- 合集选择 -->
    <div class="composer-collections">
      <div class="composer-collections-trigger" @click="showCollectionModal = true; loadCollections()">
        <span v-if="selectedCollections.length === 0" class="composer-collections-placeholder">
          <span class="composer-collections-icon">📚</span>
          添加到合集...
        </span>
        <span v-else class="composer-collections-selected">
          已选 {{ selectedCollections.length }} 个合集
        </span>
      </div>
    </div>

    <!-- 合集选择弹窗 -->
    <div v-if="showCollectionModal" class="composer-collection-modal" @click.self="showCollectionModal = false">
      <div class="composer-collection-modal-content" @click.stop>
        <div class="composer-collection-modal-head">
          <h3 class="composer-collection-modal-title">选择合集</h3>
          <button class="composer-collection-modal-close" @click="showCollectionModal = false">&times;</button>
        </div>

        <div v-if="loadingCollections" style="display: flex; justify-content: center; padding: 40px 0;">
          <n-spin size="small" />
        </div>

        <div v-else-if="collections.length === 0 && !showCreateForm" style="text-align: center; padding: 24px 0; color: var(--color-text-muted); font-size: 13px;">
          暂无合集，点击左下角创建你的第一个合集
        </div>

        <div v-else class="composer-collection-modal-list">
          <div
            v-for="collection in collections"
            :key="collection.id"
            :class="['composer-collection-modal-item', {
              selected: selectedCollections.includes(collection.id),
              disabled: visibility === 'PRIVATE' && collection.visibility === 'PUBLIC'
            }]"
            @click="visibility !== 'PRIVATE' || collection.visibility !== 'PUBLIC' ? toggleCollection(collection.id) : null"
          >
            <span class="composer-collection-checkbox">
              <span v-if="selectedCollections.includes(collection.id)" class="composer-collection-checked">✓</span>
            </span>
            <div class="composer-collection-modal-info">
              <span class="composer-collection-modal-name">{{ collection.name }}</span>
              <span v-if="visibility === 'PRIVATE' && collection.visibility === 'PUBLIC'" class="composer-collection-hint">需公开日记</span>
              <span class="composer-collection-modal-vis">{{ collection.visibility === 'PUBLIC' ? '公开' : '私密' }}</span>
            </div>
          </div>
        </div>

        <!-- 创建新合集表单 -->
        <div v-if="showCreateForm" class="composer-collection-create-form">
          <div class="composer-collection-create-divider" />
          <input
            ref="newCollectionNameInput"
            v-model="newCollectionName"
            class="composer-create-input"
            type="text"
            placeholder="合集名称"
            @keyup.enter="submitCreateCollection"
          />
          <textarea
            v-model="newCollectionDesc"
            class="composer-create-textarea"
            placeholder="描述（可选）"
            rows="2"
          />
          <div class="composer-create-visibility">
            <button
              v-for="opt in visibilityOpts"
              :key="opt.value"
              :class="['composer-vis-opt-small', { active: newCollectionVisibility === opt.value }]"
              @click="newCollectionVisibility = opt.value"
            >{{ opt.label }}</button>
          </div>
          <div class="composer-create-actions">
            <button class="composer-create-cancel" @click="showCreateForm = false">取消</button>
            <button class="composer-create-submit" :disabled="!newCollectionName.trim()" @click="submitCreateCollection">创建</button>
          </div>
        </div>

        <div class="composer-collection-modal-foot">
          <button
            v-if="!showCreateForm"
            class="composer-collection-foot-btn"
            @click="openCreateForm"
          >+ 新建合集</button>
          <button class="composer-collection-foot-btn primary" @click="showCollectionModal = false">完成</button>
        </div>
      </div>
    </div>

    <!-- ★ 编辑器：纸质感 -->
    <div class="composer-editor" style="position: relative;">
      <Toolbar
        v-if="editorRef"
        :editor="editorRef"
        :defaultConfig="toolbarConfig"
      />
      <Editor
        v-model="htmlContent"
        :defaultConfig="editorConfig"
        @onCreated="handleEditorCreated"
      />
      <div class="composer-word-count" :class="{ 'text-error': isOverLimit }">
        {{ plainText.length }} / 3000
      </div>
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
        :disabled="!plainText.trim() || isOverLimit"
        @click="handleSave"
      >
        {{ isEditMode ? '保存修改' : (analyze ? '保存并分析' : '保存') }}
      </button>
    </div>

    <!-- 分析状态 -->
    <div v-if="store.analysisStatus !== 'idle'" class="composer-status">
      <template v-if="store.analysisStatus === 'analyzing'">已保存，MoodCopilot 正在分析中...</template>
      <template v-else-if="store.analysisStatus === 'complete'">分析完成</template>
      <template v-else-if="store.analysisStatus === 'skipped_quota'">日记已保存，今日分析次数已用完。</template>
      <template v-else-if="store.analysisStatus === 'failed_limit'">日记已保存，深度思考额度已用完。</template>
      <template v-else-if="store.analysisStatus === 'skipped_user'">日记已保存，AI 分析已关闭。</template>
      <template v-else-if="store.analysisStatus === 'failed'">
        分析结果暂时没有更新。
        <template v-if="store.activeDiary">
          <button class="composer-inline-link" @click="store.refreshAnalysis(store.activeDiary.id, false)">普通分析</button>
          <button class="composer-inline-link" @click="store.refreshAnalysis(store.activeDiary.id, true)">深度思考</button>
        </template>
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
import { ref, computed, onMounted, onBeforeUnmount, watch, shallowRef, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import type { IDomEditor, IToolbarConfig, IEditorConfig } from '@wangeditor/editor'
import { useDiaryStore, type MusicMeta } from '../stores/diary'
import { musicApi, imageApi, collectionApi } from '../api'
import type { DiaryImageMetaPayload } from '../api/diary'
import MusicCard from './MusicCard.vue'
import { formatLegacyContent } from '../utils/markdown'
import { NButton, NSpin, NEmpty } from 'naive-ui'

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
const route = useRoute()
const isEditMode = computed(() => props.editId != null && props.editId > 0)

const DRAFT_KEY = 'moodcopilot:draft'

let initialHtml = ''
let initialDraftNotice = ''
if (isEditMode.value) {
  initialHtml = formatLegacyContent(props.initialContent || '')
  initialDraftNotice = '正在编辑日记'
} else {
  const savedDraft = localStorage.getItem(DRAFT_KEY)
  if (savedDraft) {
    initialHtml = savedDraft
    initialDraftNotice = '已恢复本机草稿'
  }
}

const htmlContent = ref(initialHtml)
const draft = ref(initialHtml)
const draftNotice = ref(initialDraftNotice)
const draftSavedAt = ref('')
const visibility = ref<'PRIVATE' | 'PUBLIC'>(props.initialVisibility || 'PRIVATE')
const analyze = ref(true)
const useReasoning = ref(false)
const analysisModelOptions = [
  { value: false, label: '极速分析' },
  { value: true, label: '深度思考' },
]
watch(analyze, (enabled) => {
  if (!enabled) useReasoning.value = false
})

const collections = ref<any[]>([])
const selectedCollections = ref<number[]>([])
const loadingCollections = ref(false)
const showCollectionModal = ref(false)
const showCreateForm = ref(false)
const newCollectionName = ref('')
const newCollectionDesc = ref('')
const newCollectionVisibility = ref<'PRIVATE' | 'PUBLIC'>('PRIVATE')
const newCollectionNameInput = ref<HTMLInputElement | null>(null)

const visibilityOpts: { label: string; value: 'PRIVATE' | 'PUBLIC' }[] = [
  { label: '私密', value: 'PRIVATE' },
  { label: '公开', value: 'PUBLIC' },
]

const editorRef = shallowRef<IDomEditor | null>(null)

const toolbarConfig: Partial<IToolbarConfig> = {
  toolbarKeys: [
    'bold',
    'italic',
    'underline',
    'through',
    '|',
    'bulletedList',
    'numberedList',
    '|',
    'divider',
    '|',
    'undo',
    'redo',
  ],
}

const editorConfig: Partial<IEditorConfig> = {
  placeholder: '今天发生了什么？可以只写一句，也可以把说不清的感觉先放在这里。',
  autoFocus: false,
  scroll: false,
}

const musicMeta = ref<MusicMeta | null>(props.initialMusicMeta || null)
const musicSongUrl = ref(props.initialSongUrl || props.initialMusicMeta?.songUrl || '')
const userLyric = ref(props.initialLyric || props.initialMusicMeta?.userLyric || '')
const musicParsing = ref(false)
const showMusicInput = ref(false)
const musicUrlDraft = ref('')
const musicUrlInput = ref<HTMLInputElement | null>(null)

const imageList = ref<string[]>(props.initialImages ? [...props.initialImages] : [])
const imageMetaList = ref<DiaryImageMetaPayload[]>([])
const uploadingImage = ref(false)
const previewSrc = ref('')

watch(previewSrc, (value) => {
  document.body.style.overflow = value ? 'hidden' : ''
})

onBeforeUnmount(() => {
  document.body.style.overflow = ''
})

type UploadChannel = 'normal' | 'text'

interface UploadImageAnalysis {
  width: number
  height: number
  edgeDensity: number
  grayRatio: number
}

interface PreparedImageUpload {
  file: File
  channel: UploadChannel
  origWidth: number
  origHeight: number
  compressedWidth: number
  compressedHeight: number
  origSize: number
  compressedSize: number
  quality?: number
  mime: string
}

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

const plainText = computed(() => htmlContent.value.replace(/<[^>]*>/g, '').trim())
const isOverLimit = computed(() => plainText.value.length > 3000)

// 编辑模式下，内容未变化时不触发 AI 重新分析
const contentChanged = computed(() => !isEditMode.value || htmlContent.value !== initialHtml)

function handleEditorCreated(editor: IDomEditor) {
  editorRef.value = editor
}

onMounted(() => {
  if (draftNotice.value) {
    updateDraftSavedAt()
  }
  
  if (route.query.collectionId) {
    const id = Number(route.query.collectionId)
    if (!isNaN(id)) {
      selectedCollections.value.push(id)
    }
  }
  
  void loadCollections()
})

onBeforeUnmount(() => {
  const editor = editorRef.value
  if (editor) {
    try { editor.destroy() } catch (e) { /* ignore */ }
    editorRef.value = null
  }
})

// 同步编辑器 HTML 到 draft，保持现有的 localStorage 草稿逻辑
watch(htmlContent, (val) => {
  draft.value = val
})

// 粘贴/输入音乐链接后自动解析，无需手动 Enter
watch(musicUrlDraft, (val) => {
  if (!val) return
  const url = detectMusicUrl(val)
  if (url) submitMusicUrl(url, val)
})

watch(draft, (value, oldValue) => {
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
  musicUrlDraft.value = url
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
    const prepared = await prepareImageUpload(file)
    const url = await imageApi.uploadDirect(prepared.file)
    if (url) {
      imageList.value.push(url)
      imageMetaList.value.push({
        url,
        channel: prepared.channel,
        origWidth: prepared.origWidth,
        origHeight: prepared.origHeight,
        compressedWidth: prepared.compressedWidth,
        compressedHeight: prepared.compressedHeight,
        origSize: prepared.origSize,
        compressedSize: prepared.compressedSize,
        quality: prepared.quality,
        mime: prepared.mime,
      })
    }
  } catch (e: any) {
    console.error('[ImageUpload] 上传失败', e)
    let msg = '图片上传失败，请稍后重试'
    if (e?.response?.data?.message) {
      msg = e.response.data.message
    } else if (e?.message) {
      msg = e.message
    }
    window.$message?.error(msg)
  }
  finally {
    uploadingImage.value = false
    input.value = ''
  }
}

function removeImage(i: number) {
  const removedUrl = imageList.value[i]
  imageList.value.splice(i, 1)
  const metaIndex = imageMetaList.value.findIndex((m) => m.url === removedUrl)
  if (metaIndex >= 0) {
    imageMetaList.value.splice(metaIndex, 1)
  }
}

function sameImageOrder(a: string[], b: string[]) {
  if (a.length !== b.length) return false
  for (let i = 0; i < a.length; i++) {
    if ((a[i] || '').trim() !== (b[i] || '').trim()) return false
  }
  return true
}

async function prepareImageUpload(file: File): Promise<PreparedImageUpload> {
  const unsupportedCompressTypes = ['image/gif', 'image/heic', 'image/heif']
  if (unsupportedCompressTypes.includes(file.type)) {
    const dim = await readImageDimensions(file)
    return {
      file,
      channel: file.type === 'image/png' ? 'text' : 'normal',
      origWidth: dim.width,
      origHeight: dim.height,
      compressedWidth: dim.width,
      compressedHeight: dim.height,
      origSize: file.size,
      compressedSize: file.size,
      mime: file.type || 'image/jpeg',
    }
  }

  const analysis = await analyzeImage(file)
  const channel = classifyChannel(file, analysis)
  const policy = getCompressionPolicy(channel, file.type)
  const compressed = await compressImage(file, analysis.width, analysis.height, policy.targetLongEdge, policy.mime, policy.quality)

  const shouldFallback = !compressed || compressed.blob.size >= file.size * 0.98
  if (shouldFallback) {
    return {
      file,
      channel,
      origWidth: analysis.width,
      origHeight: analysis.height,
      compressedWidth: analysis.width,
      compressedHeight: analysis.height,
      origSize: file.size,
      compressedSize: file.size,
      mime: file.type || 'image/jpeg',
    }
  }

  const uploadFile = blobToFile(compressed.blob, file.name, compressed.mime)
  return {
    file: uploadFile,
    channel,
    origWidth: analysis.width,
    origHeight: analysis.height,
    compressedWidth: compressed.width,
    compressedHeight: compressed.height,
    origSize: file.size,
    compressedSize: uploadFile.size,
    quality: compressed.quality,
    mime: compressed.mime,
  }
}

function classifyChannel(file: File, analysis: UploadImageAnalysis): UploadChannel {
  const ratio = analysis.width / Math.max(1, analysis.height)
  const isPng = file.type === 'image/png'
  const screenshotAspect = (ratio > 0.45 && ratio < 0.62) || (ratio > 1.6 && ratio < 2.3)
  const denseEdges = analysis.edgeDensity > 0.14
  const veryDenseEdges = analysis.edgeDensity > 0.18
  const highGray = analysis.grayRatio > 0.5
  const mediumGray = analysis.grayRatio > 0.38
  const compactImage = Math.min(analysis.width, analysis.height) < 1500

  if (isPng) {
    return denseEdges || mediumGray || screenshotAspect ? 'text' : 'normal'
  }

  if (screenshotAspect && (denseEdges || mediumGray)) {
    return 'text'
  }

  if (compactImage && veryDenseEdges && highGray) {
    return 'text'
  }

  return 'normal'
}

function getCompressionPolicy(channel: UploadChannel, sourceMime: string) {
  if (channel === 'text') {
    return {
      targetLongEdge: 2048,
      quality: sourceMime === 'image/png' ? undefined : 0.9,
      mime: sourceMime === 'image/png' ? 'image/png' : 'image/webp',
    }
  }
  return {
    targetLongEdge: 1800,
    quality: 0.84,
    mime: 'image/webp',
  }
}

async function analyzeImage(file: File): Promise<UploadImageAnalysis> {
  const { img, revoke } = await loadImage(file)
  try {
    const sample = 96
    const canvas = document.createElement('canvas')
    canvas.width = sample
    canvas.height = sample
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (!ctx) {
      return { width: img.naturalWidth, height: img.naturalHeight, edgeDensity: 0, grayRatio: 0 }
    }

    ctx.drawImage(img, 0, 0, sample, sample)
    const data = ctx.getImageData(0, 0, sample, sample).data
    let grayPixels = 0
    let edgePixels = 0
    const luminance = new Float32Array(sample * sample)

    for (let i = 0, p = 0; i < data.length; i += 4, p++) {
      const r = data[i]
      const g = data[i + 1]
      const b = data[i + 2]
      luminance[p] = 0.299 * r + 0.587 * g + 0.114 * b
      if (Math.abs(r - g) < 12 && Math.abs(g - b) < 12) grayPixels++
    }

    for (let y = 1; y < sample - 1; y++) {
      for (let x = 1; x < sample - 1; x++) {
        const idx = y * sample + x
        const dx = Math.abs(luminance[idx + 1] - luminance[idx - 1])
        const dy = Math.abs(luminance[idx + sample] - luminance[idx - sample])
        if (dx + dy > 35) edgePixels++
      }
    }

    const total = sample * sample
    return {
      width: img.naturalWidth,
      height: img.naturalHeight,
      edgeDensity: edgePixels / total,
      grayRatio: grayPixels / total,
    }
  } finally {
    revoke()
  }
}

async function readImageDimensions(file: File): Promise<{ width: number; height: number }> {
  const { img, revoke } = await loadImage(file)
  try {
    return { width: img.naturalWidth, height: img.naturalHeight }
  } finally {
    revoke()
  }
}

async function loadImage(file: File): Promise<{ img: HTMLImageElement; revoke: () => void }> {
  const objectUrl = URL.createObjectURL(file)
  const img = new Image()
  img.decoding = 'async'
  await new Promise<void>((resolve, reject) => {
    img.onload = () => resolve()
    img.onerror = () => reject(new Error('读取图片失败'))
    img.src = objectUrl
  })
  return {
    img,
    revoke: () => URL.revokeObjectURL(objectUrl),
  }
}

async function compressImage(
  file: File,
  sourceWidth: number,
  sourceHeight: number,
  targetLongEdge: number,
  outputMime: string,
  quality?: number,
): Promise<{ blob: Blob; width: number; height: number; mime: string; quality?: number } | null> {
  const { img, revoke } = await loadImage(file)
  try {
    const longEdge = Math.max(sourceWidth, sourceHeight)
    const scale = Math.min(1, targetLongEdge / Math.max(1, longEdge))
    const width = Math.max(1, Math.round(sourceWidth * scale))
    const height = Math.max(1, Math.round(sourceHeight * scale))

    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    if (!ctx) return null

    ctx.drawImage(img, 0, 0, width, height)
    const blob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob((b) => resolve(b), outputMime, quality)
    })
    if (!blob) return null
    return { blob, width, height, mime: outputMime, quality }
  } finally {
    revoke()
  }
}

function blobToFile(blob: Blob, originalName: string, mime: string): File {
  const base = originalName.includes('.') ? originalName.slice(0, originalName.lastIndexOf('.')) : originalName
  const ext = mime === 'image/png' ? '.png' : mime === 'image/webp' ? '.webp' : '.jpg'
  return new File([blob], `${base}${ext}`, { type: mime, lastModified: Date.now() })
}

async function loadCollections() {
  loadingCollections.value = true
  try {
    const res = await collectionApi.mine(1, 100)
    const data = res.data.data
    collections.value = data.records ?? []
  } catch (e) {
    console.error('加载合集失败', e)
  } finally {
    loadingCollections.value = false
  }
}

function toggleCollection(collectionId: number) {
  const index = selectedCollections.value.indexOf(collectionId)
  if (index === -1) {
    selectedCollections.value.push(collectionId)
  } else {
    selectedCollections.value.splice(index, 1)
  }
}

function openCreateForm() {
  showCreateForm.value = true
  nextTick(() => {
    newCollectionNameInput.value?.focus()
  })
}

async function submitCreateCollection() {
  const name = newCollectionName.value.trim()
  if (!name) return

  try {
    const res = await collectionApi.create({
      name,
      description: newCollectionDesc.value.trim() || undefined,
      visibility: newCollectionVisibility.value,
    })
    const newCollection = res.data.data
    collections.value.push(newCollection)
    selectedCollections.value.push(newCollection.id)

    newCollectionName.value = ''
    newCollectionDesc.value = ''
    newCollectionVisibility.value = 'PRIVATE'
    showCreateForm.value = false
    window.$message?.success('合集创建成功')
  } catch (e: any) {
    const msg = e?.response?.data?.message || '创建合集失败'
    window.$message?.error(msg)
  }
}

async function handleSave() {
  const content = htmlContent.value.trim()
  if (!content) return
  try {
    const musicPayload = musicMeta.value
      ? { ...musicMeta.value, userLyric: userLyric.value, songUrl: musicSongUrl.value }
      : undefined

    const isImageChangedInEdit = isEditMode.value
      ? !sameImageOrder(imageList.value, props.initialImages ? [...props.initialImages] : [])
      : true

    const imagesPayload = isEditMode.value
      ? (isImageChangedInEdit ? [...imageList.value] : undefined)
      : (imageList.value.length ? imageList.value : undefined)

    const imageMetaPayload = isEditMode.value
      ? (isImageChangedInEdit && imageMetaList.value.length ? imageMetaList.value : undefined)
      : (imageMetaList.value.length ? imageMetaList.value : undefined)

    let diaryId: number

    if (isEditMode.value) {
      diaryId = props.editId!
      await store.updateDiary(diaryId, content, visibility.value, musicPayload, imagesPayload, analyze.value && contentChanged.value, imageMetaPayload, useReasoning.value)
      router.push(`/diary/${diaryId}`)
    } else {
      await store.createDiary(content, visibility.value, musicPayload, analyze.value, imagesPayload, imageMetaPayload, useReasoning.value)
      diaryId = store.activeDiary?.id!
      htmlContent.value = ''
      draft.value = ''
      musicMeta.value = null
      userLyric.value = ''
      imageList.value = []
      imageMetaList.value = []
      localStorage.removeItem(DRAFT_KEY)
    }

    if (selectedCollections.value.length > 0 && diaryId) {
      for (const collectionId of selectedCollections.value) {
        try {
          await collectionApi.addDiaries(collectionId, [diaryId])
        } catch (e) {
          console.error(`添加日记到合集 ${collectionId} 失败`, e)
        }
      }
    }

    selectedCollections.value = []
  } catch (e) {
    console.error('[DiarySave] 保存失败', e)
    // error message is displayed by store.errorMessage
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
  transition: color 0.2s, background-color 0.2s, border-color 0.2s, opacity 0.2s, transform 0.2s;
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

/* ── 合集选择 ── */
.composer-collections {
  margin-bottom: 16px;
  position: relative;
}

.composer-collections-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1.5px dashed color-mix(in oklab, var(--color-primary) 20%, transparent);
  border-radius: 10px;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 0.82rem;
  cursor: pointer;
  transition: color 0.2s, background-color 0.2s, border-color 0.2s, opacity 0.2s, transform 0.2s;
  font-family: inherit;
}

.composer-collections-trigger:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 2%, transparent);
}

.composer-collections-icon { font-size: 14px; }

.composer-collections-placeholder {
  display: flex;
  align-items: center;
  gap: 6px;
}

.composer-collections-selected {
  font-weight: 600;
  color: var(--color-primary);
}

/* ── 合集选择弹窗 ── */
.composer-collection-modal {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(3px);
}

.composer-collection-modal-content {
  background: var(--color-surface);
  border-radius: 14px;
  width: 100%;
  max-width: 380px;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 40px rgba(0,0,0,0.10);
  border: 1px solid var(--color-border);
  overflow: hidden;
}

.composer-collection-modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 12px;
}

.composer-collection-modal-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  font-family: var(--font-display);
}

.composer-collection-modal-close {
  background: none;
  border: none;
  font-size: 22px;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 0;
  line-height: 1;
  transition: color 0.15s;
}

.composer-collection-modal-close:hover {
  color: var(--color-text);
}

.composer-collection-modal-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 20px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 60px;
  max-height: 280px;
}

.composer-collection-modal-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: color 0.15s, background-color 0.15s, border-color 0.15s, opacity 0.15s, transform 0.15s;
  border: 1px solid transparent;
}

.composer-collection-modal-item:hover {
  background: color-mix(in oklab, var(--color-primary) 3%, transparent);
}

.composer-collection-modal-item.selected {
  background: color-mix(in oklab, var(--color-primary) 6%, transparent);
  border-color: color-mix(in oklab, var(--color-primary) 15%, transparent);
}

.composer-collection-modal-item.disabled {
  opacity: 0.45;
  cursor: not-allowed;
  pointer-events: none;
}

.composer-collection-checkbox {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 1.5px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 11px;
  color: var(--color-primary);
  transition: color 0.15s, background-color 0.15s, border-color 0.15s, opacity 0.15s, transform 0.15s;
}

.composer-collection-modal-item.selected .composer-collection-checkbox {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.composer-collection-modal-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  min-width: 0;
  gap: 8px;
}

.composer-collection-modal-name {
  font-size: 14px;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer-collection-modal-vis {
  font-size: 11px;
  color: var(--color-text-muted);
  background: color-mix(in oklab, var(--color-surface-soft) 80%, transparent);
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}

.composer-collection-hint {
  font-size: 10px;
  color: var(--color-text-light);
  margin-left: 4px;
  flex-shrink: 0;
}

.composer-collection-modal-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px 18px;
  gap: 10px;
}

.composer-collection-foot-btn {
  padding: 8px 16px;
  border: 1.5px solid var(--color-border);
  border-radius: 10px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.15s, background-color 0.15s, border-color 0.15s, opacity 0.15s, transform 0.15s;
  font-family: inherit;
}

.composer-collection-foot-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.composer-collection-foot-btn.primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.composer-collection-foot-btn.primary:hover {
  background: var(--color-primary-hover);
}

/* ── 创建合集内联表单 ── */
.composer-collection-create-form {
  padding: 8px 20px 0;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.composer-collection-create-divider {
  border-top: 1px solid color-mix(in oklab, var(--color-border) 40%, transparent);
  margin-bottom: 12px;
}

.composer-create-input {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  font-size: 14px;
  font-family: inherit;
  outline: 2px solid transparent; outline-offset: 2px;
  margin-bottom: 10px;
  transition: border-color 0.2s;
}

.composer-create-input::placeholder {
  color: var(--color-text-light);
}

.composer-create-input:focus {
  border-color: var(--color-primary);
}

.composer-create-textarea {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  font-size: 13px;
  font-family: inherit;
  outline: 2px solid transparent; outline-offset: 2px;
  resize: none;
  margin-bottom: 14px;
  transition: border-color 0.2s;
}

.composer-create-textarea::placeholder {
  color: var(--color-text-light);
}

.composer-create-textarea:focus {
  border-color: var(--color-primary);
}

.composer-create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.composer-create-cancel {
  padding: 8px 16px;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  border-radius: 6px;
  font-family: inherit;
}

.composer-create-cancel:hover {
  background: color-mix(in oklab, var(--color-border) 50%, transparent);
}

.composer-create-submit {
  padding: 8px 16px;
  border: none;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border-radius: 6px;
  font-family: inherit;
}

.composer-create-submit:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.composer-create-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.composer-create-visibility {
  display: flex;
  gap: 0;
  margin-bottom: 14px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  overflow: hidden;
}

.composer-vis-opt-small {
  flex: 1;
  padding: 6px;
  border: none;
  background: transparent;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: color 0.2s, background-color 0.2s, border-color 0.2s, opacity 0.2s, transform 0.2s;
  font-family: inherit;
}

.composer-vis-opt-small:first-child {
  border-right: 1px solid var(--color-border);
}

.composer-vis-opt-small:hover {
  background: color-mix(in oklab, var(--color-primary) 8%, transparent);
  color: var(--color-primary);
}

.composer-vis-opt-small.active {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

/* ── 编辑器：纸质感 ── */
.composer-editor {
  margin-bottom: 20px;
  width: 100%;
  min-width: 0;
  border: none !important;
  border-radius: 14px !important;
  background: var(--color-surface) !important;
  background-image:
    linear-gradient(135deg, var(--color-surface) 0%, color-mix(in oklab, var(--color-primary) 1.5%, var(--color-surface)) 100%) !important;
  box-shadow:
    0 1px 2px rgba(32,32,29,0.03),
    0 6px 20px color-mix(in oklab, var(--color-primary) 6%, transparent) !important;
  overflow: hidden !important;
  transition: box-shadow 0.3s var(--ease-out) !important;

  /* 覆盖 wangEditor 默认 CSS 变量，使其完美兼容明暗主题 */
  --w-e-textarea-bg-color: transparent;
  --w-e-textarea-color: var(--color-text);
  --w-e-textarea-border-color: transparent;
  --w-e-toolbar-color: var(--color-text-secondary);
  --w-e-toolbar-bg-color: transparent;
  --w-e-toolbar-active-color: var(--color-primary);
  --w-e-toolbar-active-bg-color: color-mix(in oklab, var(--color-primary) 10%, transparent);
  --w-e-toolbar-disabled-color: var(--color-text-muted);
  --w-e-toolbar-border-color: color-mix(in oklab, var(--color-primary) 12%, transparent);
}

.composer-editor:focus-within {
  box-shadow:
    0 1px 2px rgba(32,32,29,0.04),
    0 10px 32px color-mix(in oklab, var(--color-primary) 12%, transparent),
    0 0 0 3px color-mix(in oklab, var(--color-primary) 10%, transparent) !important;
}

/* wangEditor 工具栏容器 */
.composer-editor :deep(.w-e-toolbar) {
  padding: 10px 20px !important;
  border-bottom: 1px solid color-mix(in oklab, var(--color-primary) 8%, transparent) !important;
  background: transparent !important;
}

.composer-editor :deep(.w-e-bar-item button) {
  color: var(--color-text-muted);
  border-radius: 8px;
  transition: color 0.15s, background-color 0.15s, border-color 0.15s, opacity 0.15s, transform 0.15s;
}

.composer-editor :deep(.w-e-bar-item button:hover) {
  background: color-mix(in oklab, var(--color-primary) 8%, transparent);
  color: var(--color-primary);
}

/* 编辑区容器 */
.composer-editor :deep(.w-e-text-container) {
  min-height: 260px;
  padding: 20px 24px 40px 24px !important;
  background: transparent !important;
}

/* 占位符 */
.composer-editor :deep(.w-e-text-placeholder) {
  color: var(--color-text-light) !important;
  font-style: normal !important;
}

/* 编辑区域文字 */
.composer-editor :deep(.w-e-text-container [data-slate-editor]) {
  color: var(--color-text) !important;
  line-height: 1.55 !important;
}

.composer-editor :deep(.w-e-text-container [data-slate-editor] *) {
  color: inherit !important;
  background-color: transparent !important;
}

.composer-editor :deep(.w-e-text-container [data-slate-editor] p) {
  margin: 0.1em 0 !important;
}

.composer-editor :deep(.w-e-text-container [data-slate-editor] h1),
.composer-editor :deep(.w-e-text-container [data-slate-editor] h2),
.composer-editor :deep(.w-e-text-container [data-slate-editor] h3),
.composer-editor :deep(.w-e-text-container [data-slate-editor] h4) {
  margin: 0.5em 0 0.15em !important;
}

/* 下拉菜单面板主题适配 */
.composer-editor :deep(.w-e-panel-container) {
  background: var(--color-surface) !important;
  border: 1px solid var(--color-border) !important;
}

.composer-editor :deep(.w-e-panel-container .w-e-panel-content) {
  color: var(--color-text-secondary) !important;
}

.composer-editor :deep(.w-e-dropdown-content) {
  background: var(--color-surface) !important;
  border: 1px solid var(--color-border) !important;
}

.composer-word-count {
  position: absolute;
  left: 24px;
  bottom: 14px;
  font-size: 12px;
  color: var(--color-text-light);
  pointer-events: none;
  z-index: 10;
}

.composer-word-count.text-error {
  color: var(--color-error);
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
  transition: color 0.2s, background-color 0.2s, border-color 0.2s, opacity 0.2s, transform 0.2s;
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
  outline: 2px solid transparent; outline-offset: 2px;
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
.composer-lightbox {
  position: fixed;
  inset: 0;
  z-index: 2400;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(11, 14, 18, 0.86);
  backdrop-filter: blur(2px);
}
.composer-lightbox img {
  max-width: min(94vw, 1200px);
  max-height: 90vh;
  width: auto;
  height: auto;
  border-radius: 10px;
  object-fit: contain;
  box-shadow: 0 18px 60px rgba(0, 0, 0, 0.45);
}
.composer-lightbox-close {
  position: absolute;
  top: 14px;
  right: 16px;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  font-size: 28px;
  line-height: 44px;
  text-align: center;
  cursor: pointer;
}
.composer-lightbox-close:hover {
  background: rgba(255, 255, 255, 0.28);
}

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
  transition: color 0.2s, background-color 0.2s, border-color 0.2s, opacity 0.2s, transform 0.2s;
  gap: 2px;
}

.composer-image-add:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.composer-image-add.uploading {
  cursor: wait;
  opacity: 0.6;
  border-color: var(--color-primary);
}

.composer-image-add-plus {
  font-size: 22px;
  font-weight: 300;
  line-height: 1;
}

.composer-upload-text {
  color: var(--color-primary);
  font-weight: 500;
  font-size: 0.7rem;
}

@keyframes composer-slide-in {
  from { opacity: 0; transform: translateY(8px) scale(0.95); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

/* ── 提示文字 ── */
.composer-hint {
  margin: 24px 0;
  padding: 14px 18px;
  border-left: 3px solid var(--color-primary);
  border-radius: 0 8px 8px 0;
  background: color-mix(in oklab, var(--color-primary) 3%, var(--color-surface));
  color: var(--color-text-muted);
  font-size: 0.82rem;
  line-height: 1.7;
  font-style: italic;
}

/* ── 底部操作栏 ── */
.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.composer-footer-left {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.composer-privacy-note {
  margin: 0;
  font-size: 0.78rem;
  color: var(--color-text-light);
  line-height: 1.5;
  max-width: 380px;
}

.composer-draft-note {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: var(--color-primary);
  font-weight: 600;
}

.composer-draft-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 0 0 3px color-mix(in oklab, var(--color-primary) 15%, transparent);
}

.composer-submit {
  padding: 13px 36px;
  border: none;
  border-radius: 14px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: 1rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s var(--ease-out);
  letter-spacing: 0.03em;
  box-shadow: 0 2px 8px color-mix(in oklab, var(--color-primary) 30%, transparent);
  font-family: inherit;
  flex-shrink: 0;
}

.composer-submit:hover:not(:disabled) {
  background: var(--color-primary-hover);
  box-shadow: 0 4px 16px color-mix(in oklab, var(--color-primary) 40%, transparent);
  transform: translateY(-1px);
}

.composer-submit:active:not(:disabled) { transform: scale(0.97); }

.composer-submit:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

/* ── 分析状态 ── */
.composer-status {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 10px;
  background: color-mix(in oklab, var(--color-primary) 5%, var(--color-surface));
  border: 1px solid color-mix(in oklab, var(--color-primary) 12%, transparent);
  color: var(--color-text-secondary);
  font-size: 0.85rem;
  line-height: 1.6;
}

.composer-inline-link {
  border: none;
  background: none;
  color: var(--color-primary);
  cursor: pointer;
  font-weight: 700;
  font-family: inherit;
  font-size: inherit;
}

.composer-error {
  margin-top: 12px;
  padding: 12px 16px;
  border-radius: 10px;
  background: color-mix(in oklab, var(--color-error) 8%, transparent);
  color: var(--color-error);
  font-size: 0.85rem;
  border: 1px solid color-mix(in oklab, var(--color-error) 15%, transparent);
}

/* ═══ Mobile ═══ */
@media (max-width: 768px) {
  .composer-title { font-size: 1.5rem; }

  .composer-controls {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .composer-editor {
    border-radius: 12px !important;
  }

  .composer-editor :deep(.w-e-toolbar) {
    padding: 6px 12px !important;
  }

  .composer-editor :deep(.w-e-toolbar) {
    display: flex !important;
    flex-wrap: wrap !important;
  }

  .composer-editor :deep(.w-e-text-container) {
    padding: 16px 16px 36px 16px !important;
  }

  .composer-hint { display: none; }

  .composer-footer {
    flex-direction: column;
    gap: 14px;
    align-items: stretch;
  }

  .composer-submit {
    width: 100%;
    text-align: center;
    padding: 14px 0;
  }
  
  .composer-lightbox {
    padding: 12px;
  }
  
  .composer-lightbox img {
    max-width: 96vw;
    max-height: 86vh;
    border-radius: 8px;
  }
  
  .composer-lightbox-close {
    top: 8px;
    right: 8px;
    width: 40px;
    height: 40px;
    line-height: 40px;
    font-size: 24px;
  }
}

@media (max-width: 420px) {
  .composer-title { font-size: 1.35rem; }

  .composer-editor :deep(.w-e-text-container) {
    padding: 14px 12px 32px 12px !important;
  }

  .composer-vis-opt { padding: 6px 14px; font-size: 0.75rem; }

  .composer-image-thumb,
  .composer-image-add {
    width: 66px;
    height: 66px;
  }
}
</style>

