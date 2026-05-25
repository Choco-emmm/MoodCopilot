<template>
  <n-modal
    :show="show"
    @update:show="$emit('update:show', $event)"
    preset="card"
    title="用户建议列表"
    style="width: 90%; max-width: 600px; max-height: 80vh; overflow-y: auto;"
  >
    <div v-if="loading" style="text-align: center; padding: 20px;">
      <n-spin size="small" />
    </div>
    <div v-else-if="!suggestions.length" style="text-align: center; padding: 20px; color: var(--color-text-light);">
      暂无用户建议
    </div>
    <div v-else class="admin-suggestions-list">
      <div v-for="s in suggestions" :key="s.id" class="suggestion-item" style="border: 1px solid var(--color-border); border-radius: 8px; padding: 12px; margin-bottom: 12px;">
        <div style="display: flex; justify-content: space-between; margin-bottom: 8px; align-items: center;">
          <div style="display: flex; align-items: center; gap: 8px;">
            <img v-if="s.userAvatar" :src="s.userAvatar" style="width: 24px; height: 24px; border-radius: 50%;" />
            <span v-else style="width: 24px; height: 24px; border-radius: 50%; background: var(--color-border); display: inline-flex; align-items: center; justify-content: center; font-size: 12px;">{{ s.userName.charAt(0) }}</span>
            <strong>{{ s.userName }}</strong>
          </div>
          <span style="font-size: 12px; color: var(--color-text-light);">{{ new Date(s.createdAt).toLocaleString() }}</span>
        </div>
        <p style="white-space: pre-wrap; font-size: 14px; margin: 0;">{{ s.content }}</p>
      </div>
      <div v-if="hasMore" style="text-align: center; margin-top: 12px;">
        <n-button size="small" :loading="loadingMore" @click="loadMore">加载更多</n-button>
      </div>
    </div>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { NModal, NSpin, NButton } from 'naive-ui'
import { suggestionApi } from '../../api'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  (e: 'update:show', val: boolean): void
}>()

const suggestions = ref<any[]>([])
const page = ref(1)
const hasMore = ref(false)
const loading = ref(false)
const loadingMore = ref(false)

async function loadData() {
  loading.value = true
  page.value = 1
  try {
    const res = await suggestionApi.adminList(1, 20)
    suggestions.value = res.data.data.items || []
    hasMore.value = suggestions.value.length < (res.data.data.total || 0)
  } catch (e) {
    console.error('加载建议失败', e)
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const nextPage = page.value + 1
    const res = await suggestionApi.adminList(nextPage, 20)
    const items = res.data.data.items || []
    suggestions.value.push(...items)
    page.value = nextPage
    hasMore.value = suggestions.value.length < (res.data.data.total || 0)
  } catch (e) {
    console.error('加载更多建议失败', e)
  } finally {
    loadingMore.value = false
  }
}

watch(() => props.show, (val) => {
  if (val) {
    loadData()
  }
})
</script>

<style scoped>
/* Extracted from global if necessary */
</style>
