<template>
  <view v-if="loginSheetVisible" class="login-mask" @click="dismissLogin">
    <view class="login-sheet" @click.stop>
      <view class="sheet-handle" />
      <view class="login-mark">M</view>
      <text class="login-title">登录后继续</text>
      <text class="login-desc">用微信一键登录，保留你的私密记录与成长轨迹。</text>
      <button class="wx-login-button" :loading="loginSubmitting" :disabled="loginSubmitting" @click="loginWithWechat">微信一键登录</button>
      <text class="login-cancel" @click="dismissLogin">暂不登录</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { dismissLogin, loginSheetVisible, loginSubmitting, loginWithWechat } from '@/stores/login'
</script>

<style scoped>
.login-mask { position: fixed; inset: 0; z-index: 10020; display: flex; flex-direction: column; justify-content: flex-end; background: rgba(25, 30, 27, .42); }
.login-sheet { position: relative; padding: 26rpx 48rpx calc(52rpx + env(safe-area-inset-bottom)); background: var(--theme-surface); border-radius: 28rpx 28rpx 0 0; text-align: center; }
.sheet-handle { width: 64rpx; height: 7rpx; margin: 0 auto 34rpx; border-radius: 6rpx; background: var(--theme-border); }
.login-mark { width: 70rpx; height: 70rpx; margin: 0 auto 22rpx; border-radius: 50%; background: var(--theme-primary); color: #fff; font-family: Georgia, serif; font-size: 39rpx; line-height: 70rpx; }
.login-title { display: block; color: var(--theme-text-primary); font-size: 40rpx; font-weight: 700; }
.login-desc { display: block; margin: 16rpx auto 42rpx; max-width: 510rpx; color: var(--theme-text-secondary); font-size: 27rpx; line-height: 1.65; }
.wx-login-button { width: 100%; margin: 0; border-radius: 10rpx; background: #07c160; color: #fff; font-size: 31rpx; font-weight: 600; }
.wx-login-button::after { border: 0; }
.login-cancel { display: block; padding: 27rpx 16rpx 0; color: var(--theme-text-placeholder); font-size: 27rpx; }
</style>
