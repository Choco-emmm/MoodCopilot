<template>
  <main class="app-shell">
    <AppHeader />

    <div class="summary-page">
      <h2>总结库</h2>

      <div v-if="summaries.length === 0 && !loading" class="empty-state">
        <p>还没有保存的总结，去周报页面生成吧～</p>
        <n-button type="primary" @click="router.push('/weekly-report')">去周报</n-button>
      </div>

      <div v-else class="summary-list">
        <article v-for="s in summaries" :key="s.id" class="summary-card">
          <div class="summary-head">
            <h3>{{ s.title }}</h3>
            <n-button size="tiny" text type="error" @click="remove(s.id)">删除</n-button>
          </div>
          <p class="summary-body">{{ s.aiSummary }}</p>
        </article>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { summaryApi } from '../api'

const router = useRouter()
const summaries = ref<any[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const res = await summaryApi.list()
    summaries.value = res.data.data ?? []
  } finally {
    loading.value = false
  }
})

async function remove(id: number) {
  await summaryApi.delete(id)
  summaries.value = summaries.value.filter(s => s.id !== id)
}
</script>
