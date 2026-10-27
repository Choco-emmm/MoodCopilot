<template>
  <main class="app-shell">
    <AppHeader />

    <section class="panel memory-center-panel">
      <button class="page-back-link" aria-label="返回上一页" @click="router.back()">←</button>
      <div class="panel-header">
        <h1>我的记忆中心</h1>
        <p class="panel-desc">记住了什么，依据是什么，都可以在这里核对。</p>
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
.page-back-link { display: inline-flex; align-items: center; justify-content: center; min-width: 44px; min-height: 44px; }
.page-back-link:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 3px; }
.panel-header {
  margin-bottom: 20px;
  text-align: center;
}
.panel-header h1 {
  margin: 0 0 12px;
  color: var(--color-primary);
  font-family: var(--font-serif);
  font-size: clamp(1.7rem, 4vw, 2rem);
  font-weight: 600;
}
.panel-desc {
  color: var(--color-text-secondary);
  font-size: 1.1rem;
  margin: 0;
}
</style>
