<template>
  <main class="shell">
    <section class="today">
      <p class="eyebrow">MoodCopilot</p>
      <h1>写下今天，慢慢理解自己。</h1>
      <textarea
        v-model="draft"
        class="diary-input"
        placeholder="今天发生了什么？你可以只写一句。"
      />
      <div class="actions">
        <button class="primary" type="button">保存并分析</button>
        <button class="secondary" type="button">仅自己可见</button>
      </div>
    </section>

    <section class="preview">
      <h2>第一阶段核心闭环</h2>
      <div class="item">AI 自动识别心情、主题、摘要和一句反馈</div>
      <div class="item">用户选择私密或公开</div>
      <div class="item">发完后推荐同频日记，支持留言和共鸣</div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const draft = ref('')
</script>

