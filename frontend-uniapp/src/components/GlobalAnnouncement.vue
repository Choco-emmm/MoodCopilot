<template>
  <view v-if="announcementVisible && activeAnnouncement" class="announcement-mask">
    <view class="announcement-card">
      <view class="announcement-topline">
        <text>MOODCOPILOT</text>
        <text>公告</text>
      </view>
      <view class="announcement-close" @click="closeAnnouncement">×</view>
      <text class="announcement-title">{{ activeAnnouncement.title }}</text>
      <scroll-view scroll-y class="announcement-content-wrap">
        <text class="announcement-content">{{ activeAnnouncement.content }}</text>
      </scroll-view>
      <view class="announcement-footer">
        <text>{{ formatDate(activeAnnouncement.publishedAt) }}</text>
        <view class="announcement-confirm" @click="closeAnnouncement">我知道了</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { activeAnnouncement, announcementVisible, closeAnnouncement } from '@/stores/announcement'

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.announcement-mask { position: fixed; inset: 0; z-index: 10030; display: flex; align-items: center; justify-content: center; padding: 42rpx; box-sizing: border-box; background: rgba(25, 30, 27, .4); }
.announcement-card { position: relative; width: 100%; max-width: 620rpx; max-height: 78vh; padding: 42rpx; box-sizing: border-box; background: var(--theme-surface); border-top: 8rpx solid var(--theme-primary); border-radius: 12rpx; box-shadow: 0 28rpx 72rpx rgba(0, 0, 0, .18); }
.announcement-topline { display: flex; justify-content: space-between; padding-right: 48rpx; color: var(--theme-primary); font-size: 21rpx; font-weight: 700; letter-spacing: 2rpx; }
.announcement-close { position: absolute; top: 26rpx; right: 28rpx; width: 48rpx; height: 48rpx; color: var(--theme-text-secondary); font-size: 48rpx; font-weight: 300; line-height: 42rpx; text-align: center; }
.announcement-title { display: block; margin: 28rpx 0 24rpx; padding-right: 30rpx; color: var(--theme-text-primary); font-family: "Noto Serif SC", "Songti SC", serif; font-size: 42rpx; font-weight: 700; line-height: 1.35; }
.announcement-content-wrap { max-height: 560rpx; }
.announcement-content { display: block; color: var(--theme-text-secondary); font-size: 28rpx; line-height: 1.8; white-space: pre-wrap; word-break: break-word; }
.announcement-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 34rpx; padding-top: 24rpx; border-top: 1rpx solid var(--theme-border); color: var(--theme-text-placeholder); font-size: 22rpx; }
.announcement-confirm { padding: 13rpx 24rpx; border-radius: 7rpx; background: var(--theme-primary); color: #fff; font-size: 25rpx; }
</style>
