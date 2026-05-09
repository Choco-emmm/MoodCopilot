<template>
  <header class="masthead">
    <div class="masthead-top">
      <p class="eyebrow">MoodCopilot</p>
      <nav class="masthead-nav">
        <template v-if="auth.isAuthenticated">
          <span class="masthead-user">{{ auth.displayName }}</span>
          <n-button text type="primary" @click="handleLogout">退出</n-button>
        </template>
        <template v-else>
          <n-button text type="primary" @click="router.push('/login')">登录</n-button>
          <n-button text type="primary" @click="router.push('/register')">注册</n-button>
        </template>
      </nav>
    </div>
    <h1>写下今天，慢慢理解自己。</h1>
    <p class="subtitle">AI 先帮你看见情绪；当你愿意时，再把你温和地连接给相似心情的人。</p>
  </header>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NButton } from 'naive-ui'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>
