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
const selectedIndices = ref<Set<number>>(new Set())

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

function toggleLine(index: number) {
  const next = new Set(selectedIndices.value)
  if (next.has(index)) {
    next.delete(index)
  } else {
    next.add(index)
  }
  selectedIndices.value = next
  // Emit concatenated selected lines
  const lines = [...next].sort((a, b) => a - b).map(i => lyricsList.value[i])
  emit('update:lyric', lines.join('\n'))
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
      <button
        v-if="songUrl"
        class="lyrics-fetch-btn"
        type="button"
        :disabled="lyricsLoading"
        @click="fetchLyrics"
      >
        {{ lyricsLoading ? '加载中...' : lyricsList.length ? (showLyricsPanel ? '收起歌词 ▲' : '查看歌词 ▼') : '查看歌词' }}
      </button>
      <div v-if="selectedIndices.size > 0" class="music-lyric-selected">
        <div
          v-for="idx in [...selectedIndices].sort((a, b) => a - b)"
          :key="idx"
          class="selected-line"
        >
          <span class="selected-line-text">{{ lyricsList[idx] }}</span>
          <button class="selected-line-x" type="button" @click="toggleLine(idx)">×</button>
        </div>
      </div>
      <p v-else-if="!lyricsList.length && !lyricsLoading" class="lyrics-hint">点击上方「查看歌词」选择你喜欢的歌词</p>
      <p v-if="lyricsError" class="lyrics-error">歌词加载失败</p>
      <p v-if="showLyricsPanel && lyricsList.length" class="lyrics-hint">点击歌词多选</p>
      <div v-if="showLyricsPanel && lyricsList.length" class="lyrics-chips">
        <button
          v-for="(line, i) in lyricsList"
          :key="i"
          class="lyric-chip"
          type="button"
          :class="{ selected: selectedIndices.has(i) }"
          @click="toggleLine(i)"
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

.music-icon { font-size: 22px; }

.music-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.music-title {
  font-size: 14px;
  font-weight: 600;
  color: #3d3d3d;
}

.music-title-zh {
  font-size: 12px;
  font-weight: 400;
  color: #8a7a6a;
  margin-left: 6px;
}

.music-artist {
  font-size: 12px;
  color: #8a7a6a;
}

.music-artist-zh {
  font-size: 11px;
  color: #b0a090;
  margin-left: 4px;
}

.music-lyric-section { border-top: 1px dashed rgba(180, 150, 120, 0.1); }

.music-lyric-selected { padding: 8px 12px 0; }

.selected-line {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0 0 4px;
  padding: 4px 10px;
  border-radius: 8px;
  background: #f0f7f2;
  border-left: 3px solid var(--color-primary, #4a7c62);
}

.selected-line-text {
  flex: 1;
  color: var(--color-primary, #4a7c62);
  font-size: 13px;
  line-height: 1.6;
}

.selected-line-x {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--color-primary, #4a7c62);
  font-size: 14px;
  line-height: 20px;
  cursor: pointer;
  text-align: center;
  opacity: 0.6;
  transition: opacity 0.15s;
  font-family: inherit;
}

.selected-line-x:hover {
  opacity: 1;
  background: rgba(74, 124, 98, 0.1);
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

.lyrics-fetch-btn:hover { color: var(--color-primary, #4a7c62); }
.lyrics-fetch-btn:disabled { opacity: 0.6; cursor: wait; }

.lyrics-error {
  margin: 0 12px 6px;
  font-size: 11px;
  color: #b0a090;
}

.lyrics-hint {
  margin: 0 12px 4px;
  font-size: 11px;
  color: #b0a090;
}

.lyrics-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 12px 10px;
  max-height: 200px;
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

.lyric-chip:hover { border-color: var(--color-primary, #4a7c62); }

.lyric-chip.selected {
  border-color: var(--color-primary, #4a7c62);
  background: #f0f7f2;
  color: var(--color-primary, #4a7c62);
}
</style>
