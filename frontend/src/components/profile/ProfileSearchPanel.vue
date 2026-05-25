<template>
  <transition name="fade-slide">
    <div v-if="show" class="search-panel-card">
      <div class="search-grid">
        <div class="search-field keyword-field">
          <label class="field-label">关键词</label>
          <n-input
            :value="keyword"
            @update:value="$emit('update:keyword', $event)"
            placeholder="搜索日记内容…"
            clearable
            @keyup.enter="$emit('search')"
          />
        </div>
        <div class="search-field">
          <label class="field-label">起始日期</label>
          <n-date-picker
            :value="startDate"
            @update:value="$emit('update:startDate', $event)"
            type="date"
            clearable
            placeholder="不限"
            :is-date-disabled="dateDisabled"
            style="width: 100%;"
          />
        </div>
        <div class="search-field">
          <label class="field-label">结束日期</label>
          <n-date-picker
            :value="endDate"
            @update:value="$emit('update:endDate', $event)"
            type="date"
            clearable
            placeholder="不限"
            style="width: 100%;"
          />
        </div>
        <div class="search-field">
          <label class="field-label">公开范围</label>
          <n-select
            :value="visibility"
            @update:value="$emit('update:visibility', $event)"
            :options="visibilityOpts"
            placeholder="不限"
            clearable
            style="width: 100%;"
          />
        </div>
      </div>
      <div class="search-actions">
        <n-button text class="clear-filters-btn" @click="$emit('clear')">清除筛选</n-button>
        <div class="search-buttons-group">
          <n-button type="primary" :loading="loading" @click="$emit('search')">搜索</n-button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { NInput, NDatePicker, NSelect, NButton } from 'naive-ui'

defineProps<{
  show: boolean
  keyword: string
  startDate: number | null
  endDate: number | null
  visibility: string | null
  visibilityOpts: any[]
  dateDisabled: (ts: number) => boolean
  loading: boolean
}>()

defineEmits<{
  (e: 'update:keyword', val: string): void
  (e: 'update:startDate', val: number | null): void
  (e: 'update:endDate', val: number | null): void
  (e: 'update:visibility', val: string | null): void
  (e: 'search'): void
  (e: 'clear'): void
}>()
</script>

<style scoped>
/* Scoped styles will be inherited from parent or we can move them here later if needed */
</style>
