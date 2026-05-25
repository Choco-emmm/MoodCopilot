<template>
  <main class="app-shell">
    <AppHeader />

    <section class="panel memory-center-panel">
      <div class="panel-header">
        <h2>🏛️ MoodCopilot 记忆中心</h2>
        <p class="panel-desc">查看和管理 MoodCopilot 为你整理的长期记忆与事件图谱。</p>
      </div>

      <n-tabs type="segment" justify-content="space-evenly" style="margin-top: 20px;">
        <n-tab-pane name="profile" tab="长期画像">
          <MemoryProfileView />
        </n-tab-pane>
        
        <n-tab-pane name="graph" tab="关系图谱">
          <MemoryGraphView />
        </n-tab-pane>
      </n-tabs>
    </section>
  </main>
</template>

<script setup lang="ts">
import { NTabs, NTabPane } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import MemoryProfileView from '../components/memory/MemoryProfileView.vue'
import MemoryGraphView from '../components/memory/MemoryGraphView.vue'
</script>

<style scoped>
.memory-center-panel {
  max-width: 900px;
  margin: 20px auto;
  padding: 24px;
}
.panel-header h2 {
  margin: 0 0 8px;
  color: var(--color-text);
}
.panel-desc {
  color: var(--color-text-secondary);
  font-size: 14px;
  margin: 0;
}
</style>
