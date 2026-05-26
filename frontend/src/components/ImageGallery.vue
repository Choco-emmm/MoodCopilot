<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

defineProps<{
  images: string[]
  thumbnail?: boolean
}>()

function ossUrl(url: string, width: number) {
  return `${url}?x-oss-process=image/resize,w_${width}/format,webp`
}

const lightboxSrc = ref<string | null>(null)

function open(src: string) {
  lightboxSrc.value = src
}

function close() {
  lightboxSrc.value = null
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') close()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div :class="['image-gallery', { thumbnail }]">
    <div v-for="(img, i) in images" :key="i" class="image-gallery-item" @click="open(img)">
      <img
        :src="thumbnail ? ossUrl(img, 400) : ossUrl(img, 1200)"
        :alt="'图片 ' + (i + 1)"
        loading="lazy"
        decoding="async"
        referrerpolicy="no-referrer"
        @error="($event.target as HTMLImageElement).style.display = 'none'"
      />
    </div>
  </div>

  <Teleport to="body">
    <div v-if="lightboxSrc" class="image-lightbox" @click="close">
      <img :src="lightboxSrc" alt="" @click.stop />
      <button class="image-lightbox-close" @click="close">&times;</button>
    </div>
  </Teleport>
</template>

<style scoped>
.image-gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 10px 0;
}

.image-gallery-item {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.06);
  background: var(--color-surface-hover);
  cursor: pointer;
  transition: opacity 0.15s;
}

.image-gallery-item:hover {
  opacity: 0.85;
}

.image-gallery.thumbnail .image-gallery-item {
  width: calc(33.33% - 4px);
  max-width: 160px;
  aspect-ratio: 1;
}

.image-gallery:not(.thumbnail) .image-gallery-item {
  width: 100%;
  max-width: 400px;
}

.image-gallery-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* ── Lightbox ── */
.image-lightbox {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  cursor: zoom-out;
}

.image-lightbox img {
  max-width: 90vw;
  max-height: 90vh;
  object-fit: contain;
  border-radius: 8px;
  cursor: default;
}

.image-lightbox-close {
  position: absolute;
  top: 16px;
  right: 24px;
  background: none;
  border: none;
  color: var(--color-on-primary);
  font-size: 36px;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.15s;
  line-height: 1;
}

.image-lightbox-close:hover {
  opacity: 1;
}
</style>
