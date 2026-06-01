<template>
  <main class="app-shell">
    <AppHeader />

    <section class="panel memory-center-panel">
      <div class="panel-header">
        <h2>我的记忆中心</h2>
        <p class="panel-desc">这是 MoodCopilot 从你的日常记录中提取的长期画像。不再是冰冷的表格，而是由点滴细节拼凑出的、一个更懂你的数字记忆库。</p>
      </div>

      <n-tabs v-model:value="activeTab" type="line" justify-content="center" size="large" style="margin-top: 20px;" display-directive="show">
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
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NTabs, NTabPane } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import MemoryProfileView from '../components/memory/MemoryProfileView.vue'
import MemoryGraphView from '../components/memory/MemoryGraphView.vue'

type MemoryTab = 'profile' | 'graph'

const route = useRoute()
const router = useRouter()

function toTab(value: unknown): MemoryTab {
  return value === 'graph' ? 'graph' : 'profile'
}

const activeTab = ref<MemoryTab>(toTab(route.query.tab))

watch(
  () => route.query.tab,
  (tab) => {
    const next = toTab(tab)
    if (next !== activeTab.value) {
      activeTab.value = next
    }
  },
)

watch(activeTab, (tab) => {
  const nextQuery = { ...route.query, tab }
  if (route.query.tab !== tab) {
    router.replace({ query: nextQuery })
  }
})
</script>

<style scoped>
.memory-center-panel {
  max-width: 900px;
  margin: 40px auto;
  padding: 0 20px;
  border: none;
  background: transparent;
  box-shadow: none;
}
.panel-header {
  margin-bottom: 40px;
  text-align: center;
}
.panel-header h2 {
  margin: 0 0 12px;
  color: var(--color-primary);
  font-family: var(--font-serif);
  font-size: 2.5rem;
  font-weight: 600;
}
.panel-desc {
  color: var(--color-text-secondary);
  font-size: 1.1rem;
  margin: 0;
}
</style>
