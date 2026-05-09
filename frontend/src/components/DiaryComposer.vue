<template>
  <section class="composer panel">
    <div class="section-title">
      <div>
        <p class="eyebrow">今日日记</p>
        <h2>此刻发生了什么</h2>
      </div>
      <n-segmented v-model:value="visibility" :options="visibilityOptions" />
    </div>

    <n-input
      v-model:value="draft"
      type="textarea"
      size="large"
      placeholder="今天发生了什么？可以只写一句，也可以把说不清的感觉先放在这里。"
      :autosize="{ minRows: 8, maxRows: 15 }"
    />

    <div class="composer-actions">
      <span class="privacy-copy">{{ visibilityCopy }}</span>
      <n-button
        type="primary"
        size="large"
        :loading="store.saving"
        :disabled="!draft.trim()"
        @click="handleSave"
      >
        保存并分析
      </n-button>
    </div>

    <n-alert v-if="store.errorMessage" type="error" :show-icon="false">
      {{ store.errorMessage }}
    </n-alert>
  </section>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDiaryStore } from '../stores/diary'

const store = useDiaryStore()
const draft = ref('')
const visibility = ref<'PRIVATE' | 'PUBLIC'>('PRIVATE')

const visibilityOptions = [
  { label: '仅自己看', value: 'PRIVATE' },
  { label: '分享到社区', value: 'PUBLIC' },
]

const visibilityCopy = computed(() =>
  visibility.value === 'PUBLIC'
    ? '公开后，相似心情的人可以看到并回应这篇日记。'
    : '私密日记只进入你的个人记录，也会生成 AI 分析。',
)

async function handleSave() {
  if (!draft.value.trim()) return
  try {
    await store.createDiary(draft.value.trim(), visibility.value)
    draft.value = ''
  } catch {
    // error handled by store
  }
}
</script>
