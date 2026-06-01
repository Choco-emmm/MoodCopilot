<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'

const props = defineProps<{
  images: string[]
  thumbnail?: boolean
}>()

function ossUrl(url: string, width: number) {
  return `${url}?x-oss-process=image/resize,w_${width}/format,webp`
}

const lightboxIndex = ref<number | null>(null)
const lightboxSrc = computed(() => {
  if (lightboxIndex.value === null) return null
  return props.images[lightboxIndex.value]
})
const total = computed(() => props.images.length)

function open(idx: number) {
  lightboxIndex.value = idx
}

function close() {
  lightboxIndex.value = null
}

function prev() {
  if (lightboxIndex.value === null) return
  lightboxIndex.value = (lightboxIndex.value - 1 + total.value) % total.value
}

function next() {
  if (lightboxIndex.value === null) return
  lightboxIndex.value = (lightboxIndex.value + 1) % total.value
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') close()
  else if (e.key === 'ArrowLeft') prev()
  else if (e.key === 'ArrowRight') next()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <!-- 列表流：首图大幅封面 + 余数标记 -->
  <div v-if="thumbnail" class="feed-gallery">
    <div class="feed-gallery-hero" @click="open(0)">
      <img
        :src="ossUrl(images[0], 800)"
        alt=""
        loading="lazy"
        decoding="async"
        referrerpolicy="no-referrer"
        @error="($event.target as HTMLImageElement).style.display = 'none'"
      />
      <span v-if="total > 1" class="feed-gallery-badge">+{{ total - 1 }}</span>
    </div>
  </div>

  <!-- 详情页：全宽出血杂志排版 -->
  <div v-else class="detail-gallery">
    <div
      v-for="(img, i) in images"
      :key="i"
      class="detail-gallery-item"
      @click="open(i)"
    >
      <img
        :src="ossUrl(img, 1600)"
        :alt="'图片 ' + (i + 1)"
        loading="lazy"
        decoding="async"
        referrerpolicy="no-referrer"
        @error="($event.target as HTMLImageElement).style.display = 'none'"
      />
      <span class="detail-gallery-num">{{ i + 1 }}/{{ total }}</span>
    </div>
  </div>

  <!-- 灯箱 -->
  <Teleport to="body">
    <div v-if="lightboxIndex !== null" class="image-lightbox" @click="close">
      <img :src="lightboxSrc!" alt="" @click.stop />

      <button v-if="total > 1" class="lightbox-arrow left" @click.stop="prev">
        <svg width="28" height="28" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 6l-6 6 6 6" />
        </svg>
      </button>
      <button v-if="total > 1" class="lightbox-arrow right" @click.stop="next">
        <svg width="28" height="28" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 6l6 6-6 6" />
        </svg>
      </button>

      <div v-if="total > 1" class="lightbox-dots">
        <span
          v-for="(_, i) in images"
          :key="i"
          :class="['lightbox-dot', { active: i === lightboxIndex }]"
        ></span>
      </div>

      <button class="image-lightbox-close" @click="close">&times;</button>
    </div>
  </Teleport>
</template>

<style scoped>
/* ── 列表流：首图宽幅封面 ── */
.feed-gallery {
  margin: 10px 0;
}

.feed-gallery-hero {
  position: relative;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  aspect-ratio: 16 / 9;
  background: var(--color-surface-hover);
  border: 1px solid color-mix(in oklab, var(--color-primary) 8%, transparent);
}

.feed-gallery-hero img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s var(--ease-out);
}

.feed-gallery-hero:hover img {
  transform: scale(1.02);
}

.feed-gallery-badge {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background: rgba(32, 32, 29, 0.6);
  backdrop-filter: blur(4px);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
  font-family: var(--font-body);
}

/* ── 详情页：全宽出血 ── */
.detail-gallery {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin: 28px calc(-1 * (100vw - 100%) / 2 - 16px);
  margin-left: max(-24px, calc(-1 * (100vw - 680px) / 2 - 16px));
  margin-right: max(-24px, calc(-1 * (100vw - 680px) / 2 - 16px));
}

.detail-gallery-item {
  position: relative;
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  background: var(--color-surface-hover);
  border: 1px solid color-mix(in oklab, var(--color-primary) 6%, transparent);
}

.detail-gallery-item img {
  width: 100%;
  display: block;
  object-fit: contain;
  max-height: 75vh;
}

.detail-gallery-num {
  position: absolute;
  bottom: 10px;
  right: 10px;
  background: rgba(32, 32, 29, 0.5);
  backdrop-filter: blur(4px);
  color: #fff;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 10px;
  font-family: monospace;
}

/* ── 灯箱 ── */
.image-lightbox {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.88);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  cursor: zoom-out;
}

.image-lightbox img {
  max-width: 85vw;
  max-height: 85vh;
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
  color: rgba(255,255,255,0.7);
  font-size: 36px;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.15s;
  line-height: 1;
  font-family: var(--font-body);
}

.image-lightbox-close:hover {
  opacity: 1;
}

.lightbox-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  background: rgba(255,255,255,0.08);
  backdrop-filter: blur(6px);
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 50%;
  color: rgba(255,255,255,0.7);
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
  padding: 0;
}

.lightbox-arrow:hover {
  background: rgba(255,255,255,0.15);
  color: #fff;
}

.lightbox-arrow.left { left: 20px; }
.lightbox-arrow.right { right: 20px; }

.lightbox-dots {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 6px;
}

.lightbox-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255,255,255,0.3);
  transition: all 0.15s;
}

.lightbox-dot.active {
  background: #fff;
  transform: scale(1.4);
}

@media (max-width: 640px) {
  .detail-gallery {
    margin-left: -16px;
    margin-right: -16px;
    gap: 10px;
    margin-top: 20px;
    margin-bottom: 20px;
  }

  .detail-gallery-item {
    border-radius: 0;
    border-left: none;
    border-right: none;
  }

  .image-lightbox {
    padding: 14px;
    align-items: center;
  }

  .image-lightbox img {
    max-width: 92vw;
    max-height: 88vh;
    border-radius: 6px;
  }

  .image-lightbox-close {
    top: 10px;
    right: 12px;
    font-size: 30px;
  }

  .lightbox-arrow {
    width: 36px;
    height: 36px;
  }

  .lightbox-arrow.left { left: 8px; }
  .lightbox-arrow.right { right: 8px; }
}
</style>
