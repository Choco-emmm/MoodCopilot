<template>
  <div class="chapter">
    <p class="chapter-period">{{ chapter.startDate }}{{ chapter.endDate ? ` — ${chapter.endDate}` : ' — 至今' }}</p>
    <h3 class="chapter-title">{{ chapter.title }}</h3>
    <p class="chapter-meta">
      <span v-if="chapter.segmentType === 'LEGACY_MONTH'">历史月度章节</span>
      <span v-else>动态阶段</span>
      <span v-if="chapter.currentVersion">第 {{ chapter.currentVersion }} 版</span>
      <span>{{ sourceCount }} 条记录</span>
      <span v-if="chapter.lastGeneratedAt || chapter.updatedAt">最近更新 {{ chapter.lastGeneratedAt || chapter.updatedAt }}</span>
      <span v-if="chapter.isOpen || chapter.generationStatus === 'COLLECTING'" class="status">正在积累</span>
      <span v-else-if="chapter.generationStatus === 'GENERATING'" class="status updating">正在整理</span>
      <span v-else-if="chapter.generationStatus === 'DIRTY'" class="status updating">待整理</span>
      <span v-else-if="chapter.generationStatus === 'FAILED'" class="status failed">更新失败，已保留上一版</span>
    </p>
    <p v-if="chapter.isOpen || chapter.generationStatus === 'COLLECTING'" class="chapter-prose">这一阶段的记录还在积累，内容更完整后会生成总结。</p>
    <p v-else class="chapter-prose">{{ chapter.themeSummary }}</p>
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
    <div v-if="versionsId === chapter.id" class="version-list">
      <div v-for="version in historyVersions" :key="version.version" class="version-item">
        <button type="button" class="version-head" @click="toggleVersion(version.version)">
          <strong>第 {{ version.version }} 版 · {{ version.createdAt }}</strong>
          <span class="version-toggle">{{ expandedVersion === version.version ? '收起' : '展开' }}</span>
          <span class="version-title">{{ version.title }}</span>
        </button>
        <div v-if="expandedVersion === version.version" class="version-body">
          <p class="version-summary">{{ version.themeSummary }}</p>
          <p v-if="version.growthReflection" class="version-reflection">{{ version.growthReflection }}</p>
          <div v-if="version.dominantMoods?.length" class="mood-row"><span v-for="mood in version.dominantMoods" :key="mood">{{ mood }}</span></div>
          <p class="version-meta">这一版基于 {{ version.diaryIds?.length || 0 }} 篇日记、{{ version.eventIds?.length || 0 }} 个重要事件</p>
        </div>
      </div>
      <span v-if="!historyVersions.length" class="empty-source">暂无历史版本</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { LifeChapter, LifeChapterVersion } from '../api/life'
import { useDialog } from 'naive-ui'
const props = defineProps<{ chapter: LifeChapter; expandedId: number | null; versionsId: number | null; versions: Record<number, LifeChapterVersion[]>; refreshingId: number | null }>()
const emit = defineEmits<{ (event: 'toggle-sources', id: number): void; (event: 'toggle-versions', id: number): void; (event: 'refresh', chapter: LifeChapter): void; (event: 'open-diary', id: number): void; (event: 'open-events'): void }>()
const sourceCount = (props.chapter.diarySources?.length || 0) + (props.chapter.eventSources?.length || 0)
// 章节正文展示的就是当前这一版，历史列表再列一遍是重复。当前版本号在章节头部已有标注。
const historyVersions = computed(() => (props.versions[props.chapter.id] || [])
  .filter((version) => version.version !== props.chapter.currentVersion))

// 版本号在同一个章节里是稳定的，所以用版本号当展开键即可；收起整块历史时复位。
const expandedVersion = ref<number | null>(null)

watch(() => props.versionsId, (id) => {
  if (id !== props.chapter.id) expandedVersion.value = null
})

function toggleVersion(version: number) {
  expandedVersion.value = expandedVersion.value === version ? null : version
}
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
/* 层级只用字号/字重/字体对比和背景表达，不用横向缩进：
   正文、列表、展开的版本内容全部落在同一条左边线上。 */
.chapter-period { margin: 0; color: var(--color-text-muted); font-family: var(--font-display); font-size: 13px; letter-spacing: .04em; }
.chapter-title { margin: 6px 0 10px; color: var(--color-text); font-family: var(--font-display); font-size: 1.5rem; font-weight: 600; line-height: 1.35; }
.chapter-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 12px; margin: 0; color: var(--color-text-muted); font-size: 11.5px; }
.status { padding: 2px 8px; border: 1px solid var(--color-border-strong); border-radius: 999px; }
.status.updating { border-color: var(--color-primary); color: var(--color-primary); }
.status.failed { border-color: var(--color-error); color: var(--color-error); }
.chapter-prose { max-width: 38em; margin: 16px 0 0; color: var(--color-text-secondary); font-size: 15px; line-height: 1.85; }
.chapter-reflection { max-width: 38em; margin: 18px 0 0; color: var(--color-text); font-family: var(--font-display); font-size: 15.5px; line-height: 1.9; }
.chapter-error { margin: 10px 0 0; color: var(--color-error); font-size: 12px; }
.mood-row { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 16px; }
.mood-row span { padding: 3px 9px; border: 1px solid var(--color-border); color: var(--color-text-muted); font-size: 11px; }
.chapter-actions { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 18px; }
.text-button, .source-link { padding: 0; border: 0; background: transparent; color: var(--color-primary); cursor: pointer; font: inherit; font-size: 12px; }
.text-button:disabled { cursor: wait; opacity: .55; }

.source-list, .version-list { margin-top: 18px; border-top: 1px solid var(--color-border); }
.source-item { display: flex; align-items: baseline; justify-content: space-between; gap: 14px; padding: 11px 0; border-bottom: 1px solid var(--color-border); font-size: 12.5px; }
.source-date { color: var(--color-text-muted); }
.source-excerpt { color: var(--color-text-secondary); }
.empty-source { display: block; padding: 12px 0; color: var(--color-text-muted); font-size: 12.5px; }

.version-item { border-bottom: 1px solid var(--color-border); }
.version-head { display: flex; width: 100%; flex-wrap: wrap; align-items: baseline; gap: 4px 14px; padding: 11px 0; border: 0; background: transparent; color: inherit; cursor: pointer; font: inherit; text-align: left; }
.version-head strong { color: var(--color-text); font-size: 12.5px; font-weight: 600; }
.version-toggle { margin-left: auto; color: var(--color-primary); font-size: 12px; white-space: nowrap; }
.version-title { flex-basis: 100%; color: var(--color-text-secondary); font-size: 12.5px; }
/* 展开的旧版内容用一块浅底表示「这是更内一层」，而不是再往右缩进 */
.version-body { margin: 0 0 12px; padding: 14px; background: var(--color-surface-soft); }
.version-summary, .version-reflection { margin: 0 0 8px; color: var(--color-text-secondary); font-size: 13.5px; line-height: 1.8; }
.version-reflection { color: var(--color-text-muted); }
.version-body .mood-row { margin-top: 10px; }
.version-meta { margin: 10px 0 0; color: var(--color-text-muted); font-size: 11px; }

@media (max-width: 620px) {
  .chapter-title { font-size: 1.35rem; }
  .chapter-prose, .chapter-reflection { max-width: none; font-size: 14.5px; }
  .chapter-reflection { font-size: 15.5px; }
  .source-item { align-items: flex-start; flex-direction: column; gap: 4px; }
  .version-body { padding: 12px; }
}
</style>
