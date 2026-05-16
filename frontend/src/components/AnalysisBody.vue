<template>
  <!-- AI 分析 -->
  <template v-if="diary.analysis?.summary">
    <div class="section-title compact">
      <div>
        <p class="eyebrow">AI 分析</p>
        <h2>
          {{ diary.analysis.moodLabel }} · 强度 {{ diary.analysis.moodIntensity }}/5
          <n-popover trigger="click" placement="bottom-start" :width="340" :delay="0">
            <template #trigger>
              <span class="mood-guide-trigger" title="情绪与强度评级指南">ⓘ</span>
            </template>
            <div class="mood-guide">
              <div class="guide-section">
                <p class="guide-section-title">情绪分类</p>
                <div v-for="group in moodGroups" :key="group.label" class="mood-group">
                  <p class="mood-group-label">{{ group.label }}</p>
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
      :percentage="0"
      :indicator-placement="'inside'"
      :processing="true"
      :border-radius="6"
      style="width: 100%"
    />
    <p class="feedback">AI 正在解读你的情绪，稍等片刻...</p>
  </template>
</template>

<script setup lang="ts">
import { NProgress, NPopover } from 'naive-ui'
import type { Diary } from '../stores/diary'
import { moodColor } from '../utils/mood'

defineProps<{ diary: Diary }>()

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

/* ── 情绪分组 ── */
.mood-group + .mood-group {
  margin-top: 10px;
}
.mood-group-label {
  margin: 0 0 4px;
  font-size: 0.72rem;
  color: var(--color-text-tertiary, #999);
}
.mood-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
.mood-chip {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 9px;
  font-size: 0.7rem;
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
  background: var(--color-bg-secondary, #f0f0f0);
  color: var(--color-text-secondary, #888);
  border: 1px solid var(--color-border, #e0e0e0);
}
</style>
