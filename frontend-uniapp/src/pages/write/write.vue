<template>
  <view class="composer" :style="globalThemeStyle">
    <GlobalUI />
    <scroll-view scroll-y class="composer-scroll" :show-scrollbar="false">
    <view class="composer-content">
    <!-- Header -->
    <view class="composer-header">
      <text class="composer-eyebrow">今日日记</text>
      <text class="composer-title">此刻发生了什么</text>
      <text class="composer-subtitle">不需要完美的文字，把此刻的感受放在这里就好。</text>
    </view>

    <!-- Controls -->
    <view v-if="mode === 'create'" class="composer-controls">
      <label class="composer-ai-toggle" @click="analyze = !analyze">
        <checkbox :checked="analyze" color="var(--theme-primary)" style="transform:scale(0.8);" />
        <text class="toggle-text">AI 分析我的情绪</text>
      </label>
      <view v-if="analyze" class="analysis-model-choice">
        <text class="model-choice-label">分析模式</text>
        <picker :range="analysisModelOptions" :value="useReasoning ? 1 : 0" @change="onAnalysisModelChange">
          <view class="model-choice-picker">{{ useReasoning ? '深度思考' : '普通分析' }} <text class="model-choice-arrow">⌄</text></view>
        </picker>
      </view>
    </view>

    <!-- Editor -->
    <view class="composer-editor">
      <textarea
        class="composer-textarea"
        v-model="content"
        placeholder="今天发生了什么？可以只写一句，也可以把说不清的感觉先放在这里。"
        :maxlength="3000"
        auto-height
      />
      <!-- Image Upload Area -->
      <view class="image-uploader">
        <view class="image-list">
          <view v-for="(img, idx) in images" :key="idx" class="image-item">
            <image :src="img" mode="aspectFill" class="uploaded-img" @click="previewImage(img)"/>
            <view class="delete-btn" @click.stop="removeImage(idx)">×</view>
          </view>
          <view v-if="images.length < 9" class="upload-btn" @click="chooseImage">
            <text class="upload-icon">+</text>
          </view>
        </view>
      </view>

      <view class="composer-word-count" :class="{ 'text-error': content.length >= 3000 }">
        {{ content.length }} / 3000
      </view>
    </view>

    <!-- Hint -->
    <text class="composer-hint">
      写得越具体，MoodCopilot 越能理解你在意的人和事。持续记录比一次写满更重要。    </text>

    <!-- Music Uploader -->
    <view class="music-uploader">
      <view v-if="!showMusicComposer && !musicMeta" class="add-music-row" @click="showMusicComposer = true">
        <text class="add-music-note">♪</text>
        <text>添加一首歌</text>
        <text class="add-music-arrow">›</text>
      </view>
      <view v-if="showMusicComposer && !musicMeta" class="music-input-row">
        <input class="music-input" v-model="musicUrl" placeholder="粘贴网易云音乐分享链接..." />
        <button class="music-parse-btn" @click="parseMusic" :disabled="!musicUrl || isParsingMusic">解析</button>
      </view>
      <view v-if="musicMeta" class="music-preview">
        <image class="music-cover" :src="musicMeta.coverUrl" mode="aspectFill" />
        <view class="music-info">
          <text class="music-title">{{ musicMeta.title }}</text>
          <text class="music-artist">{{ musicMeta.artist }}</text>
        </view>
        <view class="music-remove" @click="removeMusic">×</view>
      </view>
      <view v-if="musicMeta" class="lyrics-selector">
        <view class="lyrics-toolbar" @click="fetchLyrics(musicMeta)">
          <text class="lyrics-toolbar-title">{{ lyricsLoading ? '正在加载歌词...' : (showLyricsPanel ? '收起歌词' : '选择歌词') }}</text>
          <text class="lyrics-toolbar-arrow">{{ showLyricsPanel ? '⌃' : '⌄' }}</text>
        </view>
        <view v-if="selectedLyricIndices.length" class="selected-lyrics">
          <view v-for="index in selectedLyricIndices" :key="index" class="selected-lyric" @click="toggleLyric(index)">
            <text>{{ lyricsList[index] }}</text>
            <text class="selected-lyric-remove">×</text>
          </view>
        </view>
        <text v-else-if="!lyricsLoading && !showLyricsPanel" class="lyrics-hint">选择一段喜欢的歌词，保存到这篇日记里</text>
        <text v-if="lyricsError" class="lyrics-error">歌词加载失败，请稍后重试</text>
        <scroll-view v-if="showLyricsPanel && lyricsList.length" scroll-y class="lyrics-list" :show-scrollbar="false">
          <view
            v-for="(line, index) in lyricsList"
            :key="index"
            class="lyric-item"
            :class="{ selected: selectedLyricIndices.includes(index) }"
            @click="toggleLyric(index)"
          >
            <text class="lyric-check">{{ selectedLyricIndices.includes(index) ? '✓' : '' }}</text>
            <text>{{ line }}</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- Footer Actions -->
    <view class="composer-footer">
      <text class="composer-privacy-note">仅你可见。{{ analyze ? '保存后会生成 AI 分析。' : '这篇日记不会生成 AI 分析。' }}</text>
      <button 
        class="composer-submit" 
        :class="{ disabled: !canSubmit || isSubmitting }"
        :loading="isSubmitting"
        :disabled="!canSubmit || isSubmitting"
        @click="submitDiary"
      >
        {{ analyze ? '保存并分析' : '保存' }}
      </button>
    </view>
    </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { post, upload, get, request } from '@/utils/request';

import { onLoad } from '@dcloudio/uni-app';

const content = ref('');




const isSubmitting = ref(false);
const analyze = ref(true);
const useReasoning = ref(false);
const analysisModelOptions = ['普通分析', '深度思考'];
const isPublic = ref(false);
const images = ref<string[]>([]);

const mode = ref('create');
const editId = ref<number | null>(null);

const musicUrl = ref('');
const musicMeta = ref<any>(null);
const showMusicComposer = ref(false);
const isParsingMusic = ref(false);
const lyricsList = ref<string[]>([]);
const lyricsLoading = ref(false);
const lyricsError = ref(false);
const showLyricsPanel = ref(false);
const selectedLyricIndices = ref<number[]>([]);
let lyricsRequestId = 0;

const onAnalysisModelChange = (event: any) => {
  useReasoning.value = Number(event.detail.value) === 1;
};

const removeMusic = () => {
  lyricsRequestId++;
  musicMeta.value = null;
  lyricsList.value = [];
  selectedLyricIndices.value = [];
  showLyricsPanel.value = false;
  lyricsError.value = false;
  showMusicComposer.value = false;
};

const fetchLyrics = async (meta: any) => {
  if (lyricsList.value.length > 0) {
    showLyricsPanel.value = !showLyricsPanel.value;
    return;
  }
  const requestId = ++lyricsRequestId;
  lyricsLoading.value = true;
  lyricsError.value = false;
  try {
    const res = await post('/api/music/lyrics', {
      title: meta.title,
      artist: meta.artist,
      url: meta.songUrl || meta.url
    });
    const lines = Array.isArray(res.data) ? res.data : res.data?.data;
    if (requestId === lyricsRequestId && musicMeta.value === meta && res.code === 200 && Array.isArray(lines)) {
      lyricsList.value = lines.filter((line: string) => line.trim().length > 0);
      showLyricsPanel.value = lyricsList.value.length > 0;
      lyricsError.value = lyricsList.value.length === 0;
    } else if (requestId === lyricsRequestId) {
      lyricsError.value = true;
    }
  } catch (e) {
    console.error('Failed to fetch lyrics', e);
    if (requestId === lyricsRequestId) lyricsError.value = true;
  } finally {
    if (requestId === lyricsRequestId) lyricsLoading.value = false;
  }
};

const toggleLyric = (index: number) => {
  if (!musicMeta.value) {
    return;
  }
  const next = new Set(selectedLyricIndices.value);
  if (next.has(index)) next.delete(index);
  else next.add(index);
  selectedLyricIndices.value = [...next].sort((a, b) => a - b);
  musicMeta.value.userLyric = selectedLyricIndices.value.map(item => lyricsList.value[item]).join('\n');
};

onLoad((options: any) => {
  if (options.mode === 'edit' && options.id) {
    mode.value = 'edit';
    editId.value = parseInt(options.id);
    uni.setNavigationBarTitle({ title: '编辑日记' });
    fetchDiaryForEdit(editId.value);
  }
});

/** 将网页端保存的富文本 HTML 转成小程序 textarea 可编辑的纯文本。 */
const htmlToPlainText = (value: unknown): string => {
  if (typeof value !== 'string' || !value) return '';
  return value
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&#x([0-9a-f]+);/gi, (_match, hex: string) => String.fromCodePoint(parseInt(hex, 16)))
    .replace(/&#(\d+);/g, (_match, code: string) => String.fromCodePoint(parseInt(code, 10)))
    .replace(/<br\s*\/?\s*>/gi, '\n')
    .replace(/<li\b[^>]*>/gi, '- ')
    .replace(/<\/(?:p|div|li|h[1-6]|blockquote|pre)\s*>/gi, '\n')
    .replace(/<[^>]*>/g, '')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n[ \t]+/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
};

const fetchDiaryForEdit = async (id: number) => {
  try {
    const res = await get(`/api/diaries/${id}`);
    if (res.code === 200 && res.data) {
      content.value = htmlToPlainText(res.data.content);
      images.value = res.data.images || [];
      isPublic.value = res.data.visibility === 'PUBLIC';
      musicMeta.value = res.data.musicMeta;
    }
  } catch (e) {
    console.error('Failed to fetch diary for edit', e);
  }
};

const parseMusic = async () => {
  if (!musicUrl.value) return;
  isParsingMusic.value = true;
  lyricsRequestId++;
  lyricsList.value = [];

  const urlRegex = /(https?:\/\/[^\s]+)/;
  const match = musicUrl.value.match(urlRegex);
  const parsedUrl = match ? match[0] : musicUrl.value;
  const originalText = musicUrl.value;

  try {
    const res = await post('/api/music/parse', { url: parsedUrl, text: originalText });
    if (res.code === 200 && res.data) {
      musicMeta.value = res.data;
      showMusicComposer.value = true;
      musicUrl.value = '';
      uni.showToast({ title: '解析成功', icon: 'success' });
    } else {
      uni.showToast({ title: res.message || '解析失败', icon: 'none' });
    }
  } catch (e) {
    uni.showToast({ title: '解析失败', icon: 'none' });
  } finally {
    isParsingMusic.value = false;
  }
};

const canSubmit = computed(() => {
  return (content.value.trim().length > 0 || images.value.length > 0 || musicMeta.value) && content.value.length <= 3000;
});

const submitDiary = async () => {
  if (!canSubmit.value || isSubmitting.value) return;

  isSubmitting.value = true;
  uni.showLoading({ title: '保存中...' });

  try {
    const payload = {
      content: content.value.trim(),
      isPublic: isPublic.value,
      visibility: isPublic.value ? 'PUBLIC' : 'PRIVATE',
      analyze: analyze.value,
      useReasoning: analyze.value && useReasoning.value,
      images: images.value,
      musicMeta: musicMeta.value
    };

    let res;
    if (mode.value === 'edit') {
      res = await request(`/api/diaries/${editId.value}`, 'PUT', payload);
    } else {
      res = await post('/api/diaries', payload);
    }

    if (res.code === 200) {
      uni.$emit('refreshFeed');
      
      if (analyze.value && mode.value === 'create') {
        const analysisStatus = res.data?.analysisStatus;
        const analysisQueued = analysisStatus === 'analyzing';
        uni.showModal({
          title: '发布成功',
          content: analysisQueued
            ? '日记已保存。AI 正在分析，完成后会通知你。'
            : analysisStatus === 'failed_limit'
              ? '日记已保存。深度思考额度已用完，可改用普通分析或稍后重试。'
              : '日记已保存。今日 AI 分析次数已用完，你仍可正常查看和编辑日记。',
          showCancel: false,
          success: () => {
            uni.navigateBack();
          }
        });
      } else {
        uni.showToast({ title: mode.value === 'edit' ? '更新成功' : '发布成功', icon: 'success' });
        setTimeout(() => {
          uni.navigateBack();
        }, 1000);
      }
    }
  } catch (e) {
    console.error(e);
  } finally {
    isSubmitting.value = false;
    uni.hideLoading();
  }
};

const chooseImage = () => {
  uni.chooseImage({
    count: 9 - images.value.length,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      const tempFilePaths = res.tempFilePaths as string[];
      uploadImages(tempFilePaths);
    }
  });
};

const uploadImages = async (tempFilePaths: string[]) => {
  uni.showLoading({ title: '上传中...' });
  let failedCount = 0;
  for (const path of tempFilePaths) {
    try {
      const res: any = await upload('/api/images/upload', path);
      if (res.code === 200 && res.data && res.data.url) {
        images.value.push(res.data.url);
      } else {
        failedCount++;
      }
    } catch (e) {
      console.error('上传图片失败', e);
      failedCount++;
    }
  }
  uni.hideLoading();
  if (failedCount > 0) {
    uni.showToast({
      title: failedCount === tempFilePaths.length ? '图片上传失败，请重试' : `${failedCount} 张图片上传失败`,
      icon: 'none'
    });
  }
};

const removeImage = (index: number) => {
  images.value.splice(index, 1);
};

const previewImage = (current: string) => {
  uni.previewImage({
    current,
    urls: images.value
  });
};
</script>

<style scoped>
.composer {
  min-height: 100vh;
  background-color: var(--theme-bg);
  padding: 40rpx;
  display: flex;
  flex-direction: column;
}

/* Header */
.composer-header {
  margin-top: 40rpx;
  display: flex;
  flex-direction: column;
  margin-bottom: 48rpx;
}
.composer-eyebrow {
  font-size: 24rpx;
  color: var(--theme-primary);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 2rpx;
  margin-bottom: 16rpx;
}
.composer-title {
  font-family: "Noto Serif SC", "Songti SC", "STSong", "KaiTi", serif;
  font-size: 64rpx;
  color: var(--theme-text-primary);
  font-weight: 700;
  letter-spacing: 2rpx;
  margin-bottom: 16rpx;
}
.composer-subtitle {
  font-size: 26rpx;
  color: var(--theme-text-secondary);
  line-height: 1.6;
}

/* Controls */
.composer-controls {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 40rpx;
  padding-bottom: 32rpx;
  border-bottom: 1px solid rgba(var(--theme-primary-rgb), 0.15);
}
.composer-ai-toggle {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.toggle-text {
  font-size: 28rpx;
  color: var(--theme-text-primary);
  font-weight: 500;
  margin-left: 8rpx;
}
.composer-visibility {
  display: flex;
  flex-direction: row;
  background-color: rgba(var(--theme-primary-rgb), 0.08);
  border-radius: 8rpx;
  padding: 8rpx;
}
.composer-vis-opt {
  font-size: 24rpx;
  padding: 12rpx 32rpx;
  border-radius: 8rpx;
  color: var(--theme-primary);
  font-weight: 500;
}
.composer-vis-opt.active {
  background-color: var(--theme-primary);
  color: #fff;
}

/* Editor */
.composer-editor {
  flex: 1;
  background-color: var(--theme-surface);
  border-radius: 24rpx;
  padding: 40rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.04);
  margin-bottom: 32rpx;
  position: relative;
}
.composer-textarea {
  width: 100%;
  min-height: 400rpx;
  font-size: 32rpx;
  line-height: 1.8;
  color: var(--theme-text-primary);
  background-color: transparent;
  padding: 0;
}

.composer-upload {
  margin-top: 32rpx;
}
.image-list {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 16rpx;
}
.image-item, .upload-btn {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  position: relative;
}
.uploaded-img {
  width: 100%;
  height: 100%;
  border-radius: 12rpx;
}
.delete-btn {
  position: absolute;
  top: -12rpx;
  right: -12rpx;
  width: 40rpx;
  height: 40rpx;
  background-color: rgba(0, 0, 0, 0.5);
  color: #fff;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
}
.upload-btn {
  width: 160rpx;
  height: 160rpx;
  border-radius: 20rpx;
  background-color: var(--theme-surface);
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s ease, background-color 0.2s ease, border-color 0.2s ease, opacity 0.2s ease, transform 0.2s ease;
}

.upload-btn:active {
  background-color: rgba(0,0,0, 0.05);
}
.upload-icon {
  font-size: 64rpx;
  color: #ccc;
  font-weight: 300;
}
.composer-word-count {
  position: absolute;
  bottom: 32rpx;
  right: 40rpx;
  font-size: 24rpx;
  color: #a09c94;
}
.text-error {
  color: #d9534f;
}

/* Hint */
.composer-hint {
  font-size: 24rpx;
  color: var(--theme-primary);
  opacity: 0.6;
  line-height: 1.5;
  margin-bottom: 48rpx;
  text-align: center;
}

/* Footer */
.composer-footer {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;
  padding-bottom: 40rpx;
}
.composer-footer-left {
  flex: 1;
  padding-right: 32rpx;
}
.composer-privacy-note {
  font-size: 24rpx;
  color: var(--theme-primary);
  opacity: 0.7;
  line-height: 1.4;
}
.composer-submit {
  flex: 1;
  background: var(--theme-primary);
  color: #fff;
  border-radius: 12rpx;
  font-size: 32rpx;
  font-weight: 700;
  text-align: center;
  padding: 24rpx 0;
  transition: color 0.3s ease, background-color 0.3s ease, border-color 0.3s ease, opacity 0.3s ease, transform 0.3s ease;
}

.composer-submit:active:not(.disabled) {
  transform: scale(0.97);
  box-shadow: 0 4rpx 12rpx rgba(var(--theme-primary-rgb), 0.2);
}

.composer-submit.disabled {
  background-color: #ccc;
  box-shadow: none;
  opacity: 0.6;
}

.music-input-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.music-input {
  flex: 1;
  color: var(--theme-text-primary);
  background-color: var(--theme-surface);
  border: 1px solid rgba(var(--theme-primary-rgb), 0.1);
  border-radius: 16rpx;
  padding: 24rpx;
  font-size: 28rpx;
  box-shadow: inset 0 2rpx 8rpx rgba(0,0,0,0.02);
}

.music-parse-btn {
  background-color: var(--theme-primary);
  color: #fff;
  border-radius: 16rpx;
  font-size: 28rpx;
  padding: 0 32rpx;
  height: 84rpx;
  line-height: 84rpx;
  margin: 0;
  box-shadow: 0 4rpx 12rpx rgba(var(--theme-primary-rgb), 0.2);
}
.music-parse-btn::after {
  border: none;
}
.music-parse-btn[disabled] {
  background-color: rgba(var(--theme-primary-rgb), 0.5);
  color: rgba(255,255,255,0.8);
  box-shadow: none;
}

.lyrics-selector {
  margin-top: 20rpx;
  overflow: hidden;
  border: 1rpx solid rgba(var(--theme-primary-rgb), 0.13);
  border-radius: 8rpx;
  background-color: rgba(var(--theme-primary-rgb), 0.025);
}

.lyrics-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 22rpx;
  color: var(--theme-primary);
}

.lyrics-toolbar-title {
  font-size: 25rpx;
  font-weight: 600;
}

.lyrics-toolbar-arrow {
  font-size: 29rpx;
  line-height: 1;
}

.lyrics-hint, .lyrics-error {
  display: block;
  padding: 0 22rpx 19rpx;
  color: var(--theme-text-placeholder);
  font-size: 22rpx;
}

.lyrics-error { color: #c84d4d; }

.selected-lyrics {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 0 18rpx 18rpx;
}

.selected-lyric {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  padding: 12rpx 14rpx;
  border-left: 4rpx solid var(--theme-primary);
  border-radius: 4rpx;
  background: rgba(var(--theme-primary-rgb), .08);
  color: var(--theme-primary);
  font-size: 23rpx;
  line-height: 1.55;
}

.selected-lyric-remove {
  margin-left: auto;
  color: var(--theme-primary);
  font-size: 28rpx;
  line-height: 1;
}

.lyrics-list {
  max-height: 430rpx;
  border-top: 1rpx solid rgba(var(--theme-primary-rgb), .1);
}

.lyric-item {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  min-height: 40rpx;
  padding: 18rpx 20rpx;
  color: var(--theme-text-primary);
  border-bottom: 1rpx solid rgba(var(--theme-primary-rgb), .07);
  font-size: 25rpx;
  line-height: 1.55;
}

.lyric-check {
  width: 28rpx;
  min-width: 28rpx;
  height: 28rpx;
  margin-top: 4rpx;
  border: 1rpx solid rgba(var(--theme-primary-rgb), .35);
  border-radius: 50%;
  color: #fff;
  font-size: 19rpx;
  line-height: 28rpx;
  text-align: center;
}

.lyric-item.selected { background-color: rgba(var(--theme-primary-rgb), .07); color: var(--theme-primary); }
.lyric-item.selected .lyric-check { border-color: var(--theme-primary); background: var(--theme-primary); }
.lyric-item:active { background-color: rgba(var(--theme-primary-rgb), .1); }

/* A compact writing flow: write first, attach media deliberately, then save. */
.composer { height: 100vh; min-height: 100vh; padding: 0; overflow: hidden; box-sizing: border-box; }
.composer-scroll { height: 100%; }
.composer-content { padding: 30rpx 32rpx calc(130rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.composer-header { margin-top: 0; margin-bottom: 24rpx; }
.composer-eyebrow { display: none; }
.composer-title { margin-bottom: 8rpx; font-family: inherit; font-size: 38rpx; letter-spacing: 0; }
.composer-subtitle { font-size: 23rpx; line-height: 1.55; }
.composer-controls { margin-bottom: 18rpx; padding: 15rpx 0; border-top: 1rpx solid var(--theme-border); border-bottom: 1rpx solid var(--theme-border); }
.toggle-text { font-size: 23rpx; }
.analysis-model-choice { display: flex; align-items: center; gap: 14rpx; margin-top: 14rpx; }
.model-choice-label { color: var(--theme-text-secondary); font-size: 22rpx; }
.model-choice-picker { min-width: 180rpx; padding: 9rpx 16rpx; border: 1rpx solid var(--theme-border); border-radius: 6rpx; color: var(--theme-primary); font-size: 22rpx; }
.model-choice-arrow { margin-left: 6rpx; color: var(--theme-text-placeholder); }
.composer-editor { min-height: 0; margin-bottom: 16rpx; padding: 26rpx; border: 1rpx solid var(--theme-border); border-radius: 8rpx; box-shadow: none; }
.composer-textarea { min-height: 230rpx; font-size: 29rpx; line-height: 1.72; }
.image-uploader { margin-top: 20rpx; padding-top: 18rpx; border-top: 1rpx solid var(--theme-border); }
.image-list { gap: 12rpx; }
.image-item, .upload-btn { width: 118rpx; height: 118rpx; border-radius: 6rpx; }
.uploaded-img { border-radius: 6rpx; }
.upload-btn { border: 1rpx dashed rgba(var(--theme-primary-rgb), .28); background: rgba(var(--theme-primary-rgb), .025); box-shadow: none; }
.upload-icon { color: var(--theme-primary); font-size: 48rpx; }
.delete-btn { top: -8rpx; right: -8rpx; width: 34rpx; height: 34rpx; border: 2rpx solid var(--theme-surface); font-size: 21rpx; line-height: 34rpx; }
.composer-word-count { right: 25rpx; bottom: 18rpx; font-size: 20rpx; }
.composer-hint { display: none; }
.music-uploader { margin-top: 16rpx; }
.add-music-row { display: flex; height: 78rpx; align-items: center; gap: 13rpx; padding: 0 22rpx; border: 1rpx solid var(--theme-border); border-radius: 7rpx; background: var(--theme-surface); color: var(--theme-text-secondary); font-size: 25rpx; }
.add-music-note { color: var(--theme-primary); font-size: 31rpx; }
.add-music-arrow { margin-left: auto; color: var(--theme-text-placeholder); font-size: 31rpx; font-weight: 300; }
.music-input-row { margin-bottom: 14rpx; gap: 12rpx; }
.music-input { height: 72rpx; padding: 0 18rpx; border-radius: 7rpx; font-size: 24rpx; box-sizing: border-box; box-shadow: none; }
.music-parse-btn { height: 72rpx; padding: 0 23rpx; border-radius: 7rpx; font-size: 24rpx; line-height: 72rpx; box-shadow: none; }
.music-preview { display: flex; align-items: center; gap: 16rpx; padding: 16rpx; border: 1rpx solid var(--theme-border); border-radius: 7rpx; background: var(--theme-surface); }
.music-cover { width: 92rpx; height: 92rpx; flex: 0 0 92rpx; border-radius: 5rpx; background: rgba(var(--theme-primary-rgb), .08); }
.music-info { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.music-title { overflow: hidden; color: var(--theme-text-primary); font-size: 25rpx; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.music-artist { overflow: hidden; margin-top: 6rpx; color: var(--theme-text-secondary); font-size: 21rpx; text-overflow: ellipsis; white-space: nowrap; }
.music-remove { display: flex; width: 42rpx; height: 42rpx; align-items: center; justify-content: center; color: var(--theme-text-placeholder); font-size: 34rpx; font-weight: 300; }
.lyrics-selector { margin-top: 12rpx; }
.composer-footer { display: block; margin-top: 24rpx; padding: 0; }
.composer-privacy-note { display: block; margin-bottom: 14rpx; color: var(--theme-text-secondary); font-size: 21rpx; line-height: 1.5; }
.composer-submit { width: 100%; padding: 0; height: 82rpx; border-radius: 7rpx; font-size: 28rpx; line-height: 82rpx; }
</style>

