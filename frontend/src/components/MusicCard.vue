<script setup lang="ts">
import { ref } from 'vue'
import type { MusicMeta } from '../stores/diary'
import { musicApi } from '../api'

const props = withDefaults(defineProps<{
  musicMeta: MusicMeta
  lyric?: string
  showLyric?: boolean
  songUrl?: string
}>(), {
  showLyric: false,
})

const emit = defineEmits<{
  'update:lyric': [value: string]
}>()

const lyricsLoading = ref(false)
const lyricsList = ref<string[]>([])
const lyricsError = ref(false)
const showLyricsPanel = ref(false)

async function fetchLyrics() {
  if (!props.songUrl) return
  if (lyricsList.value.length > 0) {
    showLyricsPanel.value = !showLyricsPanel.value
    return
  }
  lyricsLoading.value = true
  lyricsError.value = false
  try {
    const res = await musicApi.lyrics(props.songUrl, props.musicMeta.title, props.musicMeta.artist)
    if (res.data?.data?.length) {
      lyricsList.value = res.data.data
      showLyricsPanel.value = true
    } else {
      lyricsError.value = true
    }
  } catch {
    lyricsError.value = true
  } finally {
    lyricsLoading.value = false
  }
}

function selectLyric(line: string) {
  emit('update:lyric', line)
  showLyricsPanel.value = false
}
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

    <!-- 歌词选择区 -->
    <div v-if="showLyric" class="music-lyric-section">
      <div class="music-lyric-row">
        <input
          class="music-lyric-input"
          :value="lyric"
          @input="emit('update:lyric', ($event.target as HTMLInputElement).value)"
          :placeholder="lyricsList.length ? '点击下方歌词快速填写，或手动输入' : '哪一句歌词最戳中你？'"
          maxlength="100"
        />
      </div>
      <button
        v-if="songUrl"
        class="lyrics-fetch-btn"
        type="button"
        :disabled="lyricsLoading"
        @click="fetchLyrics"
      >
        {{ lyricsLoading ? '加载中...' : lyricsList.length ? (showLyricsPanel ? '收起歌词 ▲' : '查看歌词 ▼') : '查看歌词' }}
      </button>
      <p v-if="lyricsError" class="lyrics-error">歌词加载失败，请手动输入</p>
      <div v-if="showLyricsPanel && lyricsList.length" class="lyrics-chips">
        <button
          v-for="(line, i) in lyricsList"
          :key="i"
          class="lyric-chip"
          type="button"
          :class="{ selected: lyric === line }"
          @click="selectLyric(line)"
        >
          {{ line }}
        </button>
      </div>
    </div>
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

.music-lyric-section {
  border-top: 1px dashed rgba(180, 150, 120, 0.1);
}

.music-lyric-row {
  padding: 8px 12px 4px;
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

.lyrics-fetch-btn {
  display: block;
  margin: 0 12px 4px;
  padding: 3px 0;
  border: none;
  background: none;
  color: #8a7a6a;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
}

.lyrics-fetch-btn:hover {
  color: var(--color-primary, #4a7c62);
}

.lyrics-fetch-btn:disabled {
  opacity: 0.6;
  cursor: wait;
}

.lyrics-error {
  margin: 0 12px 6px;
  font-size: 11px;
  color: #b0a090;
}

.lyrics-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 12px 10px;
  max-height: 160px;
  overflow-y: auto;
}

.lyric-chip {
  padding: 4px 10px;
  border: 1px solid rgba(180, 150, 120, 0.25);
  border-radius: 12px;
  background: #fdfcf8;
  color: #5a4a3a;
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
  font-family: inherit;
  line-height: 1.5;
  text-align: left;
}

.lyric-chip:hover {
  border-color: var(--color-primary, #4a7c62);
}

.lyric-chip.selected {
  border-color: var(--color-primary, #4a7c62);
  background: #f0f7f2;
  color: var(--color-primary, #4a7c62);
}
</style>
