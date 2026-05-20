<template>
  <!-- AI 分析 -->
  <template v-if="diary.analysis?.summary">
    <div class="section-title compact">
      <div>
        <p class="eyebrow">AI 分析</p>
        <h2>
          {{ diary.analysis.moodLabel }} · 强度 {{ diary.analysis.moodIntensity }}/5
          <n-popover trigger="click" placement="bottom-start" :width="popoverWidth" :delay="0" :scrollable="false">
            <template #trigger>
              <span class="mood-guide-trigger" title="情绪与强度评级指南">ⓘ</span>
            </template>
            <div class="mood-guide">
              <div class="guide-section">
                <p class="guide-section-title">情绪分类</p>
                <div class="quadrant-grid">
                  <div
                    v-for="group in moodGroups"
                    :key="group.label"
                    class="quadrant-cell"
                  >
                    <p class="quadrant-label">{{ group.label }}</p>
                    <div class="mood-chips">
                      <span
                        v-for="m in group.moods"
                        :key="m"
                        class="mood-chip"
                        :style="{ background: moodColor(m) }"
                      >{{ m }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <div class="guide-section">
                <p class="guide-section-title">强度标准</p>
                <div class="intensity-list">
                  <div v-for="lv in intensityLevels" :key="lv.value" class="intensity-item">
                    <span class="intensity-dot" />
                    <span class="intensity-label">{{ lv.value }} · {{ lv.name }}</span>
                    <span class="intensity-desc">{{ lv.desc }}</span>
                  </div>
                </div>
              </div>
            </div>
          </n-popover>
          <span v-if="diary.analysis.secondaryMoods?.length" class="secondary-moods">
            <span
              v-for="m in diary.analysis.secondaryMoods"
              :key="m"
              class="secondary-mood-tag"
              :style="{
                color: moodColor(m),
                borderColor: moodColor(m),
              }"
            >{{ m }}</span>
          </span>
        </h2>
      </div>
    </div>

    <div class="mood-meter" aria-label="情绪强度">
      <span v-for="step in 5" :key="step" :class="{ filled: step <= diary.analysis.moodIntensity }" />
    </div>

    <div class="md-content" v-html="renderMd(diary.analysis.summary)" />
    <div class="md-content" v-html="renderMd(diary.analysis.feedback)" />
  </template>

  <template v-else-if="diary.analysisStatus === 'skipped_quota'">
    <div class="section-title compact">
      <div>
        <p class="eyebrow">AI 分析</p>
        <h2>今日次数已用完</h2>
      </div>
    </div>
    <p class="feedback" style="color: #b0a090;">
      今天的 AI 分析次数已用完，日记已保存。明天再来看看吧~
    </p>
  </template>

  <template v-else-if="diary.analysisStatus === 'skipped_user'">
    <div class="section-title compact">
      <div>
        <p class="eyebrow">AI 分析</p>
        <h2>分析已关闭</h2>
      </div>
    </div>
  </template>

  <template v-else>
    <div class="section-title compact">
      <div>
        <p class="eyebrow">AI 分析</p>
        <h2>分析中...</h2>
      </div>
    </div>
    <p class="feedback">
      {{ progressHint }}
    </p>
  </template>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { NPopover } from 'naive-ui'
import type { Diary } from '../stores/diary'
import { moodColor } from '../utils/mood'
import { renderSafeMarkdown } from '../utils/markdown'

const props = defineProps<{ diary: Diary }>()

function renderMd(text: string) {
  return renderSafeMarkdown(text)
}

const moodGroups = [
  {
    label: '积极 · 高能量',
    moods: ['喜悦', '期待', '兴奋', '自豪'],
  },
  {
    label: '积极 · 低能量',
    moods: ['轻松', '平静', '感恩', '满足'],
  },
  {
    label: '消极 · 高能量',
    moods: ['烦躁', '愤怒', '焦虑', '害怕'],
  },
  {
    label: '消极 · 低能量',
    moods: ['疲惫', '委屈', '难过', '孤独', '迷茫', '内疚'],
  },
]

const intensityLevels = [
  { value: 1, name: '转瞬即逝', desc: '极其轻微，一闪而过' },
  { value: 2, name: '隐约察觉', desc: '背景情绪，细细感知才注意' },
  { value: 3, name: '明显体验', desc: '清晰的情感，影响当前注意力' },
  { value: 4, name: '强烈情感', desc: '驱动身体或行为上的反应' },
  { value: 5, name: '压倒性的', desc: '几乎失控，难以独自承受' },
]

// ── 弹窗宽度自适应 ──
const popoverWidth = computed(() => {
  if (typeof window === 'undefined') return 370
  return window.innerWidth <= 600 ? undefined : 370
})

const progressHint = ref('正在读取你的日记...')
let hintTimer: ReturnType<typeof setInterval> | null = null

function startHintRotation() {
  stopHintTimer()
  const hints = ['正在读取你的日记...', '正在拆解情绪线索...', '正在进行深层分析...', '正在生成温柔回应...']
  let i = 0
  hintTimer = setInterval(() => {
    i = (i + 1) % hints.length
    progressHint.value = hints[i]
  }, 4000)
}

function stopHintTimer() {
  if (hintTimer) { clearInterval(hintTimer); hintTimer = null }
}

onMounted(() => {
  if (!props.diary.analysis?.summary && props.diary.analysisStatus !== 'skipped_quota' && props.diary.analysisStatus !== 'skipped_user') {
    startHintRotation()
  }
})

watch(() => props.diary.analysis?.summary, (summary) => {
  if (summary) {
    stopHintTimer()
  }
})

onUnmounted(() => {
  stopHintTimer()
})
</script>

<style scoped>
/* ── Markdown prose 排版 ── */
.prose {
  max-width: none;
  line-height: 1.75;
  color: var(--color-text, #444);
  white-space: normal;
}

.prose :deep(p) {
  margin: 0 0 0.75em;
}

.prose :deep(p:last-child) {
  margin-bottom: 0;
}

.prose :deep(h1),
.prose :deep(h2),
.prose :deep(h3),
.prose :deep(h4) {
  margin: 1.25em 0 0.5em;
  font-weight: 600;
  line-height: 1.35;
  color: var(--color-text, #333);
}

.prose :deep(h1) { font-size: 1.25rem; }
.prose :deep(h2) { font-size: 1.1rem; }
.prose :deep(h3) { font-size: 1rem; }

.prose :deep(ul),
.prose :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.5em;
}

.prose :deep(li) {
  margin-bottom: 0.3em;
}

.prose :deep(blockquote) {
  margin: 0.75em 0;
  padding: 0.5em 1em;
  border-left: 3px solid var(--color-accent-light, #d9827a);
  background: var(--color-accent-bg, #fff1ef);
  border-radius: 0 8px 8px 0;
  color: var(--color-text-secondary, #67645d);
}

.prose :deep(code) {
  padding: 0.15em 0.4em;
  border-radius: 4px;
  background: var(--color-surface-soft, #f6f2ea);
  font-size: 0.9em;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}

.prose :deep(pre) {
  margin: 0.75em 0;
  padding: 0.75em 1em;
  border-radius: 8px;
  background: var(--color-surface-soft, #f6f2ea);
  overflow-x: auto;
  font-size: 0.85em;
  line-height: 1.55;
}

.prose :deep(pre code) {
  padding: 0;
  background: none;
  font-size: inherit;
}

.prose :deep(a) {
  color: var(--color-accent, #a94b45);
  text-decoration: underline;
}

.prose :deep(strong) {
  font-weight: 600;
  color: var(--color-text, #333);
}

.prose :deep(em) {
  font-style: italic;
}

/* Markdown 动态内容样式穿透（ChatPage 同款） */
.md-content :deep(p) {
  margin: 0 0 0.5em 0;
  line-height: 1.6;
}
.md-content :deep(p:last-child) {
  margin-bottom: 0;
}
.md-content :deep(strong), .md-content :deep(b) {
  font-weight: 600;
  color: inherit;
}
.md-content :deep(ul) {
  list-style-type: disc;
  padding-left: 1.5em;
  margin: 0.5em 0;
}
.md-content :deep(ol) {
  list-style-type: decimal;
  padding-left: 1.5em;
  margin: 0.5em 0;
}
.md-content :deep(li) {
  margin-bottom: 0.25em;
}
.md-content :deep(blockquote) {
  border-left: 3px solid #cbd5e1;
  padding-left: 0.75em;
  color: #64748b;
  margin: 0.5em 0;
}
.md-content :deep(code) {
  background-color: rgba(0, 0, 0, 0.05);
  padding: 0.2em 0.4em;
  border-radius: 4px;
  font-family: monospace;
  font-size: 0.9em;
}

/* ── 指南触发图标 ── */
.mood-guide-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  margin-left: 6px;
  font-size: 0.75rem;
  font-style: normal;
  color: var(--color-text-tertiary, #aaa);
  border: 1px solid var(--color-border, #ddd);
  border-radius: 50%;
  cursor: pointer;
  vertical-align: middle;
  user-select: none;
  transition: color 0.2s, border-color 0.2s;
}
.mood-guide-trigger:hover {
  color: var(--color-accent, #5a8f7a);
  border-color: var(--color-accent, #5a8f7a);
}

/* ── 弹窗内容 ── */
.mood-guide {
  font-size: 0.82rem;
  line-height: 1.6;
  color: var(--color-text, #444);
}
.guide-section + .guide-section {
  margin-top: 16px;
}
.guide-section-title {
  font-weight: 600;
  font-size: 0.85rem;
  margin: 0 0 10px;
  color: var(--color-text, #333);
}

/* ── 四象限网格 ── */
.quadrant-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.quadrant-cell {
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--color-bg-elevated, #fafafa);
}
.quadrant-label {
  margin: 0 0 5px;
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--color-text-tertiary, #999);
  letter-spacing: 0.02em;
}
.mood-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.mood-chip {
  display: inline-block;
  padding: 2px 7px;
  border-radius: 8px;
  font-size: 0.68rem;
  color: #fff;
  white-space: nowrap;
  opacity: 0.88;
}

/* ── 强度列表 ── */
.intensity-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.intensity-item {
  display: grid;
  grid-template-columns: 8px auto 1fr;
  align-items: baseline;
  gap: 6px;
  font-size: 0.78rem;
}
.intensity-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-accent, #5a8f7a);
  margin-top: 5px;
}
.intensity-label {
  font-weight: 500;
  color: var(--color-text, #333);
}
.intensity-desc {
  color: var(--color-text-tertiary, #999);
  font-size: 0.74rem;
}

/* ── 次要情绪标签 ── */
.secondary-moods {
  display: inline-flex;
  gap: 6px;
  margin-left: 8px;
  vertical-align: middle;
}
.secondary-mood-tag {
  font-size: 0.72rem;
  font-weight: 400;
  padding: 1px 8px;
  border-radius: 10px;
  background: transparent;
  border: 1px solid;
}

/* ── 移动端适配 ── */
@media (max-width: 600px) {
  .mood-guide {
    font-size: 0.76rem;
  }
  .guide-section + .guide-section {
    margin-top: 12px;
  }
  .guide-section-title {
    font-size: 0.78rem;
    margin-bottom: 8px;
  }
  .quadrant-grid {
    grid-template-columns: 1fr;
    gap: 6px;
  }
  .quadrant-cell {
    padding: 6px 8px;
  }
  .quadrant-label {
    font-size: 0.68rem;
    margin-bottom: 4px;
  }
  .mood-chip {
    padding: 2px 6px;
    font-size: 0.65rem;
    white-space: normal;
  }
  .intensity-item {
    font-size: 0.74rem;
    gap: 4px;
  }
  .intensity-label {
    white-space: normal;
  }
  .intensity-desc {
    font-size: 0.7rem;
  }
}
</style>
