<template>
  <!-- AI 分析 -->
  <template v-if="diary.analysis?.summary">
    <div class="section-title compact">
      <div>
        <p class="eyebrow">AI 分析</p>
        <h2>
          {{ diary.analysis.moodLabel }} · 强度 {{ diary.analysis.moodIntensity }}/5
          <n-popover trigger="click" placement="bottom-start" :width="370" :delay="0">
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

    <p class="summary">{{ diary.analysis.summary }}</p>
    <p class="feedback">{{ diary.analysis.feedback }}</p>
  </template>

  <template v-else>
    <div class="section-title compact">
      <div>
        <p class="eyebrow">AI 分析</p>
        <h2>分析中...</h2>
      </div>
    </div>
    <n-progress
      :percentage="fakeProgress"
      :indicator-placement="'inside'"
      :processing="true"
      :border-radius="6"
      :height="20"
      style="width: 100%"
    />
    <p class="feedback" :style="{ opacity: fakeProgress > 0 ? 0.7 : 1 }">
      {{ progressHint }}
    </p>
  </template>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { NProgress, NPopover } from 'naive-ui'
import type { Diary } from '../stores/diary'
import { moodColor } from '../utils/mood'

const props = defineProps<{ diary: Diary }>()

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

// ── 虚拟进度条 ──
const fakeProgress = ref(0)
let progressTimer: ReturnType<typeof setInterval> | null = null
let finishTimer: ReturnType<typeof setTimeout> | null = null

const progressHint = computed(() => {
  const p = fakeProgress.value
  if (p < 30) return '正在读取你的日记...'
  if (p < 60) return '正在拆解情绪线索...'
  if (p < 85) return '正在进行深层分析...'
  return '正在生成温柔回应...'
})

function startFakeProgress() {
  stopTimers()
  fakeProgress.value = 0
  progressTimer = setInterval(() => {
    const p = fakeProgress.value
    let add: number
    if (p < 60) {
      add = 8 + Math.random() * 7 // 8–15%
    } else if (p < 85) {
      add = 2 + Math.random() * 3 // 2–5%
    } else if (p < 98) {
      add = 0.5 + Math.random() * 0.5 // 0.5–1%
    } else {
      add = 0
    }
    fakeProgress.value = Math.min(98, p + add)
  }, 200)
}

function finishFakeProgress() {
  stopTimers()
  fakeProgress.value = 100
  finishTimer = setTimeout(() => {
    fakeProgress.value = 0
  }, 350)
}

function stopTimers() {
  if (progressTimer) { clearInterval(progressTimer); progressTimer = null }
  if (finishTimer) { clearTimeout(finishTimer); finishTimer = null }
}

onMounted(() => {
  if (!props.diary.analysis?.summary) {
    startFakeProgress()
  }
})

watch(() => props.diary.analysis?.summary, (summary) => {
  if (summary) {
    finishFakeProgress()
  }
})

onUnmounted(() => {
  stopTimers()
})
</script>

<style scoped>
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
  white-space: nowrap;
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
</style>
