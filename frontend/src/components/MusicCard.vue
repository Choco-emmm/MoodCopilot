<script setup lang="ts">
import type { MusicMeta } from '../stores/diary'

const props = withDefaults(defineProps<{
  musicMeta: MusicMeta
  lyric?: string
  showLyric?: boolean
}>(), {
  showLyric: false,
})

const emit = defineEmits<{
  'update:lyric': [value: string]
}>()
</script>

<template>
  <div class="music-card">
    <div class="music-card-body">
      <img
        v-if="musicMeta.coverUrl"
        :src="musicMeta.coverUrl"
        :alt="musicMeta.title"
        class="music-cover"
        referrerpolicy="no-referrer"
        loading="lazy"
        decoding="async"
        @error="($event.target as HTMLImageElement).style.display = 'none'"
      />
      <div v-else class="music-cover-fallback">
        <span class="music-icon">🎵</span>
      </div>
      <div class="music-info">
        <span class="music-title">{{ musicMeta.title }}</span>
        <span class="music-artist">{{ musicMeta.artist }}</span>
      </div>
    </div>
    <slot name="lyric-input">
      <div v-if="showLyric" class="music-lyric-row">
        <input
          class="music-lyric-input"
          :value="lyric"
          @input="emit('update:lyric', ($event.target as HTMLInputElement).value)"
          placeholder="哪一句歌词最戳中你？"
          maxlength="100"
        />
      </div>
    </slot>
  </div>
</template>

<style scoped>
.music-card {
  margin: 12px 0;
  border-radius: var(--radius-md, 10px);
  background: linear-gradient(135deg, #fdf6f0 0%, #faf3e8 100%);
  border: 1px solid rgba(180, 150, 120, 0.12);
  overflow: hidden;
}

.music-card-body {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
}

.music-cover {
  width: 52px;
  height: 52px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
  border: 1px solid rgba(0, 0, 0, 0.06);
  background: #f0ebe3;
}

.music-cover-fallback {
  width: 52px;
  height: 52px;
  border-radius: 6px;
  flex-shrink: 0;
  background: linear-gradient(135deg, #e8e0d5, #f0ebe3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.music-icon {
  font-size: 22px;
}

.music-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.music-title {
  font-size: 14px;
  font-weight: 600;
  color: #3d3d3d;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.music-artist {
  font-size: 12px;
  color: #8a7a6a;
}

.music-lyric-row {
  padding: 8px 12px 12px;
  border-top: 1px dashed rgba(180, 150, 120, 0.1);
}

.music-lyric-input {
  width: 100%;
  border: none;
  background: transparent;
  font-size: 13px;
  color: #6a5a4a;
  outline: none;
  padding: 4px 0;
  font-family: inherit;
}

.music-lyric-input::placeholder {
  color: #b0a090;
  font-style: italic;
}
</style>
