<template>
  <view class="composer" :style="globalThemeStyle">
    <GlobalUI />
    <!-- Header -->
    <view class="composer-header">
      <text class="composer-eyebrow">今日日记</text>
      <text class="composer-title">此刻发生了什么</text>
      <text class="composer-subtitle">不需要完美的文字，把此刻的感受放在这里就好。</text>
    </view>

    <!-- Controls -->
    <view class="composer-controls">
      <label class="composer-ai-toggle" @click="analyze = !analyze">
        <checkbox :checked="analyze" color="var(--theme-primary)" style="transform:scale(0.8);" />
        <text class="toggle-text">AI 分析我的情绪</text>
      </label>
      <view class="composer-visibility">
        <text class="composer-vis-opt active">仅自己看</text>
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
      <view class="music-input-row">
        <input class="music-input" v-model="musicUrl" placeholder="粘贴网易云音乐分享链接..." />
        <button class="music-parse-btn" @click="parseMusic" :disabled="!musicUrl || isParsingMusic">解析</button>
      </view>
      <view v-if="musicMeta" class="music-preview">
        <image class="music-cover" :src="musicMeta.coverUrl" mode="aspectFill" />
        <view class="music-info">
          <text class="music-title">{{ musicMeta.title }}</text>
          <text class="music-artist">{{ musicMeta.artist }}</text>
        </view>
        <view class="music-remove" @click="musicMeta = null">×</view>
      </view>
    </view>

    <!-- Footer Actions -->
    <view class="composer-footer">
      <view class="composer-footer-left">
        <text class="composer-privacy-note">私密日记只进入你的个人记录，也会生成 AI 分析。</text>
      </view>
      <button 
        class="composer-submit" 
        :class="{ disabled: !canSubmit }"
        @click="submitDiary"
      >
        {{ analyze ? '保存并分析' : '保存' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { post, upload, get, request } from '@/utils/request';
import { showModal } from '@/stores/globalUI';

import { onLoad } from '@dcloudio/uni-app';

const content = ref('');




const isSubmitting = ref(false);
const analyze = ref(true);
const isPublic = ref(false);
const images = ref<string[]>([]);

const mode = ref('create');
const editId = ref<number | null>(null);

const musicUrl = ref('');
const musicMeta = ref<any>(null);
const isParsingMusic = ref(false);

onLoad((options: any) => {
  if (options.mode === 'edit' && options.id) {
    mode.value = 'edit';
    editId.value = parseInt(options.id);
    uni.setNavigationBarTitle({ title: '编辑日记' });
    fetchDiaryForEdit(editId.value);
  }
});

const fetchDiaryForEdit = async (id: number) => {
  try {
    const res = await get(`/api/diaries/${id}`);
    if (res.code === 200 && res.data) {
      content.value = res.data.content;
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
  try {
    const res = await post('/api/music/parse', { url: musicUrl.value });
    if (res.code === 200 && res.data) {
      musicMeta.value = res.data;
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
      images: images.value,
      musicMeta: musicMeta.value
    };

    let res;
    if (mode.value === 'edit') {
      res = await request('PUT', `/api/diaries/${editId.value}`, payload);
    } else {
      res = await post('/api/diaries', payload);
    }

    if (res.code === 200) {
      uni.showToast({ title: mode.value === 'edit' ? '更新成功' : '发布成功', icon: 'success' });
      uni.$emit('refreshFeed');
      
      const diaryId = mode.value === 'edit' ? editId.value : res.data?.id;
      if (analyze.value && diaryId) {
        let attempts = 0;
        const timer = setInterval(async () => {
          attempts++;
          if (attempts > 30) {
            clearInterval(timer);
            return;
          }
          try {
            const checkRes = await get(`/api/diaries/${diaryId}`);
            if (checkRes.code === 200 && checkRes.data?.analysis) {
               clearInterval(timer);
               showModal('日记分析已完成', checkRes.data.analysis.summary || '你的日记有了新的 AI 解读，快来看看吧！', diaryId);
            }
          } catch (e) {}
        }, 2000);
      }

      setTimeout(() => {
        uni.navigateBack();
      }, 1000);
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
  for (const path of tempFilePaths) {
    try {
      const res: any = await upload('/api/images/upload', path);
      if (res.code === 200 && res.data && res.data.url) {
        images.value.push(res.data.url);
      }
    } catch (e) {
      console.error('上传图片失败', e);
    }
  }
  uni.hideLoading();
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
  border-radius: 12rpx;
  background-color: rgba(0, 0, 0, 0.02);
  border: 1px dashed #ccc;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
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
  transition: all 0.3s ease;
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

.music-input {
  flex: 1;
  color: var(--theme-text-primary);
  background-color: var(--theme-surface);
  border: 1px solid rgba(var(--theme-primary-rgb), 0.1);
  border-radius: 8rpx;
  padding: 16rpx 24rpx;
  font-size: 28rpx;
}
</style>
