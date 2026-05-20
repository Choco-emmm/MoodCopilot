<script setup lang="ts">
defineProps<{
  images: string[]
  thumbnail?: boolean
}>()

function thumbUrl(url: string): string {
  return url + '?x-oss-process=image/resize,w_400'
}
</script>

<template>
  <div :class="['image-gallery', { thumbnail }]">
    <div v-for="(img, i) in images" :key="i" class="image-gallery-item">
      <img
        :src="thumbnail ? thumbUrl(img) : img"
        :alt="'图片 ' + (i + 1)"
        loading="lazy"
        decoding="async"
        referrerpolicy="no-referrer"
        @error="($event.target as HTMLImageElement).style.display = 'none'"
      />
    </div>
  </div>
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
  background: #f5f0e8;
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
</style>
