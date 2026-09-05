<template>
  <view class="music-card">
    <view class="music-card-body">
      <image
        v-if="coverSource && !coverFailed"
        :src="coverSource"
        class="music-cover"
        mode="aspectFill"
        @error="coverFailed = true"
      />
      <view v-else class="music-cover-fallback">♪</view>

      <view class="music-info">
        <text v-if="label" class="music-label">{{ label }}</text>
        <text class="music-title">{{ displayTitle }}</text>
        <text class="music-artist">{{ displayArtist }}</text>
        <view v-if="tags.length" class="music-tags">
          <text v-for="tag in tags" :key="tag" class="music-tag">{{ tag }}</text>
        </view>
        <text v-if="musicMeta.themeSummary" class="music-summary">{{ musicMeta.themeSummary }}</text>
      </view>

      <view v-if="musicMeta.songUrl" class="music-play" @click.stop="copySongUrl">
        <text>▶</text>
      </view>
    </view>

    <text v-if="musicMeta.userLyric" class="music-lyric">“{{ musicMeta.userLyric }}”</text>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  musicMeta: Record<string, any>
  label?: string
}>(), {
  label: '',
})

const coverFailed = ref(false)

const coverSource = computed(() => {
  const coverUrl = String(props.musicMeta?.coverUrl || '').trim()
  if (!coverUrl) return ''
  return coverUrl.startsWith('http://') ? coverUrl.replace('http://', 'https://') : coverUrl
})

const displayTitle = computed(() => decodeEntities(props.musicMeta?.title || '未命名歌曲'))
const displayArtist = computed(() => decodeEntities(props.musicMeta?.artist || '未知歌手'))
const tags = computed(() => String(props.musicMeta?.moodTags || '').split(',').map(tag => tag.trim()).filter(Boolean))

watch(coverSource, () => {
  coverFailed.value = false
})

function decodeEntities(value: string) {
  return String(value)
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
}

function copySongUrl() {
  uni.setClipboardData({
    data: props.musicMeta.songUrl,
    success: () => uni.showToast({ title: '歌曲链接已复制', icon: 'none' }),
  })
}
</script>

<style scoped>
.music-card { overflow: hidden; border: 1rpx solid var(--theme-border); border-radius: 7rpx; background: var(--theme-surface); }
.music-card-body { display: flex; align-items: flex-start; gap: 16rpx; padding: 18rpx; }
.music-cover, .music-cover-fallback { width: 96rpx; height: 96rpx; flex: 0 0 96rpx; border-radius: 5rpx; background: color-mix(in oklab, var(--theme-primary) 12%, var(--theme-surface)); }
.music-cover-fallback { display: flex; align-items: center; justify-content: center; color: var(--theme-primary); font-size: 42rpx; }
.music-info { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.music-label { margin-bottom: 2rpx; color: var(--theme-text-placeholder); font-size: 19rpx; }
.music-title { overflow: hidden; color: var(--theme-text-primary); font-size: 25rpx; font-weight: 650; line-height: 1.42; text-overflow: ellipsis; white-space: nowrap; }
.music-artist { overflow: hidden; margin-top: 3rpx; color: var(--theme-text-secondary); font-size: 21rpx; text-overflow: ellipsis; white-space: nowrap; }
.music-tags { display: flex; flex-wrap: wrap; gap: 6rpx; margin-top: 8rpx; }
.music-tag { padding: 3rpx 9rpx; border-radius: var(--theme-radius-sm); background: color-mix(in oklab, var(--theme-primary) 9%, var(--theme-surface)); color: var(--theme-primary); font-size: 18rpx; line-height: 1.45; }
.music-summary { display: -webkit-box; overflow: hidden; margin-top: 7rpx; color: var(--theme-text-secondary); font-size: 20rpx; line-height: 1.52; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.music-play { display: flex; width: 54rpx; height: 54rpx; flex: 0 0 54rpx; align-items: center; justify-content: center; align-self: center; border-radius: 50%; background: var(--theme-primary); color: var(--theme-text-on-primary); font-size: 20rpx; }
.music-lyric { display: -webkit-box; overflow: hidden; margin: 0 18rpx 18rpx; color: var(--theme-text-secondary); font-size: 22rpx; font-style: italic; line-height: 1.62; white-space: pre-line; -webkit-box-orient: vertical; -webkit-line-clamp: 4; }
</style>
