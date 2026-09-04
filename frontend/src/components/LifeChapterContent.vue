<template>
  <div>
    <div class="chapter-period">{{ chapter.startDate }}{{ chapter.endDate ? ` - ${chapter.endDate}` : ' - 至今' }} · {{ sourceCount }} 条记录</div>
    <h3>{{ chapter.title }}</h3>
    <div class="chapter-meta">
      <span v-if="chapter.segmentType === 'LEGACY_MONTH'">历史月度章节</span>
      <span v-else>动态阶段</span>
      <span v-if="chapter.currentVersion">第 {{ chapter.currentVersion }} 版</span>
      <span v-if="chapter.lastGeneratedAt || chapter.updatedAt">最近更新 {{ chapter.lastGeneratedAt || chapter.updatedAt }}</span>
      <span v-if="chapter.isOpen || chapter.generationStatus === 'COLLECTING'">正在积累</span>
      <span v-else-if="chapter.generationStatus === 'GENERATING'" class="status updating">正在整理</span>
      <span v-else-if="chapter.generationStatus === 'DIRTY'" class="status updating">待整理</span>
      <span v-else-if="chapter.generationStatus === 'FAILED'" class="status failed">更新失败，已保留上一版</span>
    </div>
    <p v-if="chapter.isOpen || chapter.generationStatus === 'COLLECTING'" class="collecting-note">这一阶段的记录还在积累，内容更完整后会生成总结。</p>
    <p v-else class="chapter-summary">{{ chapter.themeSummary }}</p>
    <p v-if="chapter.growthReflection && !chapter.isOpen && chapter.generationStatus !== 'COLLECTING'" class="chapter-reflection">{{ chapter.growthReflection }}</p>
    <div v-if="chapter.dominantMoods?.length" class="mood-row"><span v-for="mood in chapter.dominantMoods" :key="mood">{{ mood }}</span></div>
    <p v-if="chapter.generationStatus === 'FAILED' && chapter.lastGenerationError" class="chapter-error">{{ chapter.lastGenerationError }}</p>
    <div class="chapter-actions">
      <button type="button" class="text-button" @click="$emit('toggle-sources', chapter.id)">{{ expandedId === chapter.id ? '收起来源' : `查看来源（${sourceCount}）` }}</button>
      <button v-if="!chapter.isOpen && chapter.generationStatus !== 'COLLECTING'" type="button" class="text-button" :disabled="refreshingId === chapter.id" aria-haspopup="dialog" @click="confirmRefresh">{{ refreshingId === chapter.id ? '已提交整理' : '重新整理这一章' }}</button>
      <button v-if="(chapter.currentVersion || 0) > 0" type="button" class="text-button" @click="$emit('toggle-versions', chapter.id)">{{ versionsId === chapter.id ? '收起历史' : '查看历史版本' }}</button>
    </div>
    <div v-if="expandedId === chapter.id" class="source-list">
      <div v-for="source in chapter.diarySources" :key="source.id" class="source-item"><div><span class="source-date">{{ source.date }}</span><span class="source-excerpt">{{ source.excerpt || source.summary || '这篇日记暂无摘要' }}</span></div><button type="button" class="source-link" @click="$emit('open-diary', source.id)">查看日记 →</button></div>
      <div v-for="source in chapter.eventSources" :key="`event-${source.id}`" class="source-item"><div><span class="source-date">{{ source.startDate }}</span><span class="source-excerpt">重要事件：{{ source.title }}</span></div><button type="button" class="source-link" @click="$emit('open-events')">查看事件 →</button></div>
      <span v-if="!sourceCount" class="empty-source">暂无来源</span>
    </div>
    <div v-if="versionsId === chapter.id" class="version-list"><div v-for="version in versions[chapter.id] || []" :key="version.version" class="version-item"><strong>第 {{ version.version }} 版 · {{ version.createdAt }}</strong><span>{{ version.title }}</span></div></div>
  </div>
</template>

<script setup lang="ts">
import type { LifeChapter, LifeChapterVersion } from '../api/life'
import { useDialog } from 'naive-ui'
const props = defineProps<{ chapter: LifeChapter; expandedId: number | null; versionsId: number | null; versions: Record<number, LifeChapterVersion[]>; refreshingId: number | null }>()
const emit = defineEmits<{ (event: 'toggle-sources', id: number): void; (event: 'toggle-versions', id: number): void; (event: 'refresh', chapter: LifeChapter): void; (event: 'open-diary', id: number): void; (event: 'open-events'): void }>()
const sourceCount = (props.chapter.diarySources?.length || 0) + (props.chapter.eventSources?.length || 0)
const dialog = useDialog()

function confirmRefresh() {
  dialog.info({
    title: '重新整理这一章？',
    content: `系统会根据当前关联的 ${sourceCount} 条记录，重新生成这一章的标题、主题摘要和成长回顾。之前的版本会保留在历史记录中；你的日记和重要事件不会被修改或删除。`,
    positiveText: '开始整理',
    negativeText: '取消',
    onPositiveClick: () => emit('refresh', props.chapter),
  })
}
</script>

<style scoped>
.chapter-period { color: var(--color-text-muted); font-size: 12px; letter-spacing: .03em; }
h3 { margin: 9px 0 10px; color: var(--color-text); font-family: var(--font-display); font-size: 1.55rem; }
.chapter-meta { display: flex; flex-wrap: wrap; gap: 10px; color: var(--color-text-muted); font-size: 11px; }
.status.updating { color: var(--color-primary); }.status.failed, .chapter-error { color: var(--color-error); }
.chapter-error { margin: 8px 0; font-size: 12px; }
.chapter-summary, .chapter-reflection { max-width: 650px; margin: 0 0 10px; color: var(--color-text-secondary); line-height: 1.75; }
.collecting-note { max-width: 650px; margin: 12px 0; color: var(--color-text-secondary); line-height: 1.7; }
.chapter-reflection { padding-left: 14px; border-left: 2px solid var(--color-primary); }
.mood-row { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 15px; }.mood-row span { padding: 4px 9px; border: 1px solid var(--color-border); color: var(--color-text-muted); font-size: 11px; }
.chapter-actions { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 16px; }
.text-button, .source-link { padding: 0; border: 0; background: transparent; color: var(--color-primary); cursor: pointer; font: inherit; font-size: 12px; }.text-button:disabled { cursor: wait; opacity: .55; }
.source-list, .version-list { margin-top: 14px; border-left: 2px solid var(--color-border); padding-left: 14px; }.source-item, .version-item { display: flex; align-items: baseline; justify-content: space-between; gap: 14px; padding: 9px 0; border-bottom: 1px solid var(--color-border); font-size: 12px; }.source-date { display: inline-block; min-width: 90px; color: var(--color-text-muted); }.source-excerpt, .version-item span { color: var(--color-text-secondary); }.empty-source { color: var(--color-text-muted); font-size: 12px; }
@media (max-width: 620px) { .source-item { align-items: flex-start; flex-direction: column; gap: 5px; } .source-date { min-width: auto; margin-right: 8px; } }
</style>
