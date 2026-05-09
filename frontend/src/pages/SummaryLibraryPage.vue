<template>
  <main class="app-shell">
    <AppHeader />

    <div class="summary-page">
      <div class="summary-page-head">
        <h2>总结库</h2>
        <n-button type="primary" @click="showCreate = !showCreate">
          {{ showCreate ? '取消' : '新建总结' }}
        </n-button>
      </div>

      <div v-if="showCreate" class="create-panel">
        <div class="create-row">
          <n-date-picker v-model:value="startDate" type="date" placeholder="开始日期" />
          <span class="create-sep">至</span>
          <n-date-picker v-model:value="endDate" type="date" placeholder="结束日期" />
          <n-button type="primary" :loading="creating" :disabled="!startDate || !endDate" @click="createSummary">
            生成总结
          </n-button>
        </div>
      </div>

      <div v-if="!loading && summaries.length === 0" class="empty-state">
        <p>还没有保存的总结</p>
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
import { NButton, NDatePicker } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import { summaryApi } from '../api'

const summaries = ref<any[]>([])
const loading = ref(false)
const showCreate = ref(false)
const startDate = ref<number | null>(null)
const endDate = ref<number | null>(null)
const creating = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const res = await summaryApi.list()
    summaries.value = res.data.data ?? []
  } finally {
    loading.value = false
  }
})

async function createSummary() {
  if (!startDate.value || !endDate.value) return
  creating.value = true
  try {
    const iso = (ts: number) => new Date(ts).toISOString().split('T')[0]
    await summaryApi.create({ startDate: iso(startDate.value), endDate: iso(endDate.value) })
    showCreate.value = false
    startDate.value = null
    endDate.value = null
    const res = await summaryApi.list()
    summaries.value = res.data.data ?? []
  } finally {
    creating.value = false
  }
}

async function remove(id: number) {
  await summaryApi.delete(id)
  summaries.value = summaries.value.filter(s => s.id !== id)
}
</script>
