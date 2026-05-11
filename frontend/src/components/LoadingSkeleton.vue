<template>
  <div class="loading-skeleton">
    <n-spin size="medium" />
    <p v-if="text" class="loading-text">{{ text }}</p>
  </div>
</template>

<script setup lang="ts">
import { NSpin } from 'naive-ui'
defineProps<{ text?: string }>()
</script>
