<template>
  <div v-if="show" class="composer-collection-modal" @click.self="handleClose">
    <div class="composer-collection-modal-content" @click.stop>
      <div class="composer-collection-modal-head">
        <h3 class="composer-collection-modal-title">{{ isEdit ? '编辑合集' : '新建合集' }}</h3>
        <button class="composer-collection-modal-close" @click="handleClose">&times;</button>
      </div>

      <div class="composer-collection-create-form" style="padding-bottom: 20px;">
        <input
          ref="nameInput"
          v-model="formData.name"
          class="composer-create-input"
          type="text"
          placeholder="合集名称"
        />
        <textarea
          v-model="formData.description"
          class="composer-create-textarea"
          placeholder="描述（可选）"
          rows="2"
        />

        <div class="cover-upload-section">
          <label class="cover-label">封面图片 (可选)</label>
          <div class="cover-uploader">
            <div
              v-if="formData.coverUrl"
              class="cover-preview"
              :style="{ backgroundImage: `url(${formData.coverUrl})` }"
            >
              <button class="cover-remove-btn" @click.prevent="formData.coverUrl = ''">&times;</button>
            </div>
            <label v-else class="cover-add-btn" :class="{ uploading: uploadingCover }">
              <input
                ref="fileInput"
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                :disabled="uploadingCover"
                hidden
                @change="handleFileSelect"
              />
              <template v-if="uploadingCover">
                <span class="upload-text">上传中...</span>
              </template>
              <template v-else>
                <span class="plus-icon">+</span>
                <span>添加封面</span>
              </template>
            </label>
          </div>
        </div>

        <div class="composer-create-visibility">
          <button
            v-for="opt in visibilityOpts"
            :key="opt.value"
            :class="['composer-vis-opt-small', { active: formData.visibility === opt.value }]"
            @click="formData.visibility = opt.value"
          >{{ opt.label }}</button>
        </div>
        <div class="composer-create-actions mt-2">
          <button class="composer-create-cancel" @click="handleClose">取消</button>
          <button
            class="composer-create-submit"
            :disabled="!formData.name.trim() || loading"
            @click="handleSubmit"
          >
            {{ loading ? '保存中...' : (isEdit ? '保存' : '创建') }}
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- 裁剪弹窗 -->
  <Teleport to="body">
    <div v-if="showCropper" class="cropper-overlay" @click.self="cancelCrop">
      <div class="cropper-dialog" @click.stop>
        <div class="cropper-dialog-head">
          <h3 class="cropper-dialog-title">裁剪封面</h3>
          <button class="cropper-dialog-close" @click="cancelCrop">&times;</button>
        </div>
        <div class="cropper-container">
          <img ref="cropImageEl" :src="cropImageSrc" alt="" />
        </div>
        <div class="cropper-dialog-foot">
          <button class="composer-create-cancel" @click="cancelCrop">取消</button>
          <button class="composer-create-submit" :disabled="uploadingCover" @click="confirmCrop">
            {{ uploadingCover ? '上传中...' : '确认裁剪' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed, onBeforeUnmount } from 'vue'
import { collectionApi, imageApi } from '../../api'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'

const props = defineProps<{
  show: boolean
  editData?: any
}>()

const emit = defineEmits(['update:show', 'success'])

const isEdit = computed(() => !!props.editData && !!props.editData.id)

const loading = ref(false)
const uploadingCover = ref(false)
const nameInput = ref<HTMLInputElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

// Cropper state
const showCropper = ref(false)
const cropImageSrc = ref('')
const cropImageEl = ref<HTMLImageElement | null>(null)
let cropperInstance: Cropper | null = null

const visibilityOpts: { label: string; value: 'PRIVATE' | 'PUBLIC' }[] = [
  { label: '私密', value: 'PRIVATE' },
  { label: '公开', value: 'PUBLIC' },
]

const formData = ref({
  name: '',
  description: '',
  visibility: 'PRIVATE' as 'PRIVATE' | 'PUBLIC',
  coverUrl: ''
})

watch(() => props.show, (newVal) => {
  if (newVal) {
    if (props.editData) {
      formData.value = {
        name: props.editData.name || '',
        description: props.editData.description || '',
        visibility: props.editData.visibility || 'PRIVATE',
        coverUrl: props.editData.coverUrl || ''
      }
    } else {
      formData.value = {
        name: '',
        description: '',
        visibility: 'PRIVATE',
        coverUrl: ''
      }
    }
    nextTick(() => {
      nameInput.value?.focus()
    })
  } else {
    destroyCropper()
  }
})

onBeforeUnmount(() => {
  destroyCropper()
})

function handleClose() {
  emit('update:show', false)
}

function destroyCropper() {
  if (cropperInstance) {
    cropperInstance.destroy()
    cropperInstance = null
  }
  showCropper.value = false
  cropImageSrc.value = ''
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // Read file as data URL for cropper preview
  const reader = new FileReader()
  reader.onload = (ev) => {
    cropImageSrc.value = ev.target?.result as string
    showCropper.value = true
    nextTick(() => {
      initCropper()
    })
  }
  reader.readAsDataURL(file)
  // Reset so the same file can be re-selected
  input.value = ''
}

function initCropper() {
  if (!cropImageEl.value) return
  destroyCropperInstance()

  cropperInstance = new Cropper(cropImageEl.value, {
    aspectRatio: 4 / 3,
    viewMode: 1,
    autoCropArea: 0.9,
    responsive: true,
    restore: false,
    guides: true,
    center: true,
    highlight: true,
    cropBoxMovable: true,
    cropBoxResizable: true,
    toggleDragModeOnDblclick: false,
    background: true,
  })
}

function destroyCropperInstance() {
  if (cropperInstance) {
    cropperInstance.destroy()
    cropperInstance = null
  }
}

function cancelCrop() {
  destroyCropper()
}

async function confirmCrop() {
  if (!cropperInstance) return
  uploadingCover.value = true
  try {
    const canvas = cropperInstance.getCroppedCanvas({
      width: 800,
      height: 600,
      imageSmoothingEnabled: true,
      imageSmoothingQuality: 'high',
    })
    const blob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob(resolve, 'image/jpeg', 0.9)
    })
    if (!blob) throw new Error('裁剪失败')

    const file = new File([blob], 'cover.jpg', { type: 'image/jpeg' })
    const url = await imageApi.uploadDirect(file)
    if (url) formData.value.coverUrl = url
    destroyCropper()
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '图片上传失败，请稍后重试'
    window.$message?.error(msg)
  } finally {
    uploadingCover.value = false
  }
}

async function handleSubmit() {
  if (!formData.value.name.trim()) return
  loading.value = true
  try {
    let res;
    if (isEdit.value) {
      res = await collectionApi.update(props.editData.id, formData.value)
      window.$message?.success('合集更新成功')
    } else {
      res = await collectionApi.create(formData.value)
      window.$message?.success('合集创建成功')
    }
    emit('success', res.data.data || res.data)
    handleClose()
  } catch (e: any) {
    const msg = e?.response?.data?.message || (isEdit.value ? '更新合集失败' : '创建合集失败')
    window.$message?.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.composer-collection-modal {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  backdrop-filter: blur(3px);
}

.composer-collection-modal-content {
  background: var(--color-surface);
  border-radius: 14px;
  width: 100%;
  max-width: 380px;
  max-height: 90vh;
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

.composer-collection-create-form {
  padding: 8px 20px;
  display: flex;
  flex-direction: column;
  gap: 0;
  overflow-y: auto;
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
  outline: none;
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
  outline: none;
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
  transition: all 0.2s;
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

.cover-upload-section {
  margin-bottom: 14px;
}

.cover-label {
  display: block;
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}

.cover-uploader {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cover-preview {
  position: relative;
  width: 120px;
  height: 80px;
  border-radius: 8px;
  background-size: cover;
  background-position: center;
  border: 1px solid var(--color-border);
}

.cover-remove-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 50%;
  background: rgba(0,0,0,0.5);
  color: white;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.cover-remove-btn:hover {
  background: rgba(0,0,0,0.7);
}

.cover-add-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 80px;
  border-radius: 8px;
  border: 1.5px dashed color-mix(in oklab, var(--color-primary) 20%, transparent);
  background: transparent;
  cursor: pointer;
  color: var(--color-text-muted);
  font-size: 12px;
  transition: all 0.2s;
  gap: 4px;
}

.cover-add-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.cover-add-btn.uploading {
  cursor: wait;
  opacity: 0.6;
}

.plus-icon {
  font-size: 20px;
  font-weight: 300;
  line-height: 1;
}

.upload-text {
  color: var(--color-primary);
  font-weight: 500;
}

/* ── 裁剪弹窗 ── */
.cropper-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3000;
  backdrop-filter: blur(4px);
}

.cropper-dialog {
  background: var(--color-surface);
  border-radius: 14px;
  width: 92%;
  max-width: 500px;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 12px 48px rgba(0,0,0,0.20);
  border: 1px solid var(--color-border);
  overflow: hidden;
}

.cropper-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 12px;
}

.cropper-dialog-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  font-family: var(--font-display);
}

.cropper-dialog-close {
  background: none;
  border: none;
  font-size: 22px;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 0;
  line-height: 1;
  transition: color 0.15s;
}

.cropper-dialog-close:hover {
  color: var(--color-text);
}

.cropper-container {
  width: 100%;
  max-height: 400px;
  overflow: hidden;
  background: #1a1a1a;
}

.cropper-container img {
  display: block;
  max-width: 100%;
  max-height: 400px;
}

.cropper-dialog-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 12px 20px 16px;
  gap: 10px;
}
</style>
