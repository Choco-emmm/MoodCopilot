<template>
  <view class="collection-detail-page" :style="globalThemeStyle">
    <GlobalUI />
    <view v-if="loading" class="state">加载中...</view>
    <scroll-view v-else-if="collection" class="content" scroll-y :show-scrollbar="false" @scrolltolower="loadMore">
      <view class="collection-header">
        <text class="title">{{ collection.name }}</text>
        <text v-if="collection.description" class="description">{{ collection.description }}</text>
      </view>
      <view v-if="diaries.length === 0" class="state">这个合集还没有日记</view>
      <view v-else class="diary-list">
        <view v-for="diary in diaries" :key="diary.id" class="diary-item" @click="openDiary(diary.id)">
          <text class="diary-date">{{ formatDate(diary.createdAt) }}</text>
          <text class="diary-content">{{ extractPlainText(diary.content) || '一段没有文字的记录' }}</text>
        </view>
        <text v-if="loadingMore" class="list-status">正在加载...</text>
        <text v-else-if="!hasMore" class="list-status">已经到底了</text>
      </view>
    </scroll-view>
    <view v-else class="state">合集不存在或无权查看</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { get } from '@/utils/request';
import { onLoad } from '@dcloudio/uni-app';
import GlobalUI from '@/components/GlobalUI.vue';
import { extractPlainText } from '@/utils/markdown';

const collection = ref<any>(null);
const diaries = ref<any[]>([]);
const loading = ref(true);
const loadingMore = ref(false);
const hasMore = ref(true);
const currentPage = ref(1);
const pageSize = 20;
const collectionId = ref<number | null>(null);

onLoad(async (options: any) => {
  const id = Number(options.id);
  if (!id) {
    loading.value = false;
    return;
  }
  collectionId.value = id;

  try {
    const collectionRes = await get(`/api/collections/${id}`);
    if (collectionRes.code === 200) collection.value = collectionRes.data;
    await fetchDiaries();
  } catch (error) {
    console.error('Failed to load collection', error);
  } finally {
    loading.value = false;
  }
});

const fetchDiaries = async (isLoadMore = false) => {
  if (!collectionId.value || loadingMore.value || (isLoadMore && !hasMore.value)) return;
  if (isLoadMore) loadingMore.value = true;
  else {
    currentPage.value = 1;
    hasMore.value = true;
  }

  try {
    const res = await get(`/api/collections/${collectionId.value}/diaries?page=${currentPage.value}&size=${pageSize}`);
    if (res.code !== 200) return;
    const items = res.data?.records || res.data?.items || res.data?.content || [];
    diaries.value = isLoadMore ? [...diaries.value, ...items] : items;
    hasMore.value = items.length === pageSize;
    if (hasMore.value) currentPage.value += 1;
  } catch (error) {
    console.error('Failed to load collection diaries', error);
  } finally {
    loadingMore.value = false;
  }
};

const loadMore = () => {
  void fetchDiaries(true);
};

const openDiary = (id: number) => uni.navigateTo({ url: `/pages/detail/detail?id=${id}` });

const formatDate = (value: string) => {
  const date = new Date(value);
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`;
};
</script>

<style scoped>
.collection-detail-page { display: flex; height: 100vh; min-height: 100vh; flex-direction: column; background-color: var(--theme-bg); }
.content { min-height: 0; flex: 1; padding: 32rpx; box-sizing: border-box; }
.collection-header { padding-bottom: 32rpx; border-bottom: 1px solid rgba(var(--theme-primary-rgb), 0.12); }
.title { display: block; font-size: 40rpx; font-weight: 600; color: var(--theme-text-primary); }
.description { display: block; margin-top: 12rpx; font-size: 26rpx; color: var(--theme-text-secondary); }
.state { padding: 120rpx 32rpx; text-align: center; color: var(--theme-text-secondary); }
.diary-list { padding-top: 24rpx; }
.diary-item { padding: 28rpx 0; border-bottom: 1px solid rgba(var(--theme-primary-rgb), 0.1); }
.diary-date { display: block; font-size: 24rpx; color: var(--theme-text-secondary); }
.diary-content { display: -webkit-box; margin-top: 10rpx; overflow: hidden; color: var(--theme-text-primary); font-size: 30rpx; line-height: 1.6; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.list-status { display: block; padding: 28rpx 0 48rpx; color: var(--theme-text-placeholder); font-size: 22rpx; text-align: center; }
</style>
