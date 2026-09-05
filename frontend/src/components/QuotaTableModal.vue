<template>
  <Teleport to="body">
    <div v-if="show" class="quota-overlay" @click.self="$emit('close')">
      <div class="quota-modal">
        <div class="quota-modal-header">
          <h3>配额表</h3>
          <button class="quota-modal-close" @click="$emit('close')">&times;</button>
        </div>
        <p class="quota-modal-desc">
          当前：<strong>Lv.{{ level }}</strong>
        </p>
        <div class="quota-table-wrap">
          <table class="quota-table">
            <thead>
              <tr>
                <th>身份 / 等级</th>
                <th>日记分析 Flash <span class="quota-unit">/天</span></th>
                <th>日记分析 Pro <span class="quota-unit">/天</span></th>
                <th>聊天 Flash <span class="quota-unit">/天</span></th>
                <th>聊天 Pro <span class="quota-unit">/天</span></th>
                <th>章节整理 <span class="quota-unit">/天</span></th>
                <th>共鸣检索 <span class="quota-unit">/天</span></th>
                <th>图片上传 <span class="quota-unit">/天</span></th>
                <th>图片分析 <span class="quota-unit">/天</span></th>
                <th>报告 <span class="quota-unit">/月</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in quotaTable" :key="row.label" :class="{ 'quota-row-active': row.isCurrent }">
                <td :class="{ 'quota-row-active': row.isCurrent }">{{ row.label }}</td>
                <td>{{ row.diaryFlash }}</td>
                <td>{{ row.diaryPro }}</td>
                <td>{{ row.chatFlash }}</td>
                <td>{{ row.chatPro }}</td>
                <td>{{ row.chapter }}</td>
                <td>{{ row.resonance }}</td>
                <td>{{ row.imageUpload }}</td>
                <td>{{ row.imageAnalysis }}</td>
                <td>{{ row.report }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="quota-rules-desc">
          <div class="rule-item"><strong>💡 日记分析：</strong>发布或修改日记时按你选择的 Flash 或 Pro 模型触发。</div>
          <div class="rule-item"><strong>💡 图片分析：</strong>聊天时向 AI 追问图片内的具体文字、细节（基础提炼未涵盖的内容）时才触发。</div>
          <div class="rule-item"><strong>💡 聊天：</strong>普通聊天和 Pro 聊天分别计算，每日额度互不占用。</div>
          <div class="rule-item"><strong>💡 章节整理：</strong>重新整理时光画卷中的章节，每天最多 2 次。</div>
          <div class="rule-item" style="opacity: 0.7"><strong>💡 共鸣检索：</strong>功能加紧开发中，敬请期待...</div>
        </div>

        <p class="quota-modal-footer">聊天、日记分析、章节整理、检索和传图每日 0 点重置 · 报告每月 1 日重置</p>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  show: boolean
  level: number
}>()

defineEmits<{
  (e: 'close'): void
}>()

const LEVEL_LABELS = ['Lv.1', 'Lv.2', 'Lv.3', 'Lv.4', 'Lv.5', 'Lv.6']
const QUOTA_DATA: Array<Record<string, number>> = [
  { diaryFlash: 5, diaryPro: 1, chatFlash: 15, chatPro: 1, chapter: 2, resonance: 0, report: 0, imageUpload: 3, imageAnalysis: 2 },
  { diaryFlash: 8, diaryPro: 2, chatFlash: 25, chatPro: 2, chapter: 2, resonance: 3, report: 2, imageUpload: 5, imageAnalysis: 3 },
  { diaryFlash: 12, diaryPro: 3, chatFlash: 35, chatPro: 3, chapter: 2, resonance: 5, report: 4, imageUpload: 8, imageAnalysis: 5 },
  { diaryFlash: 16, diaryPro: 4, chatFlash: 45, chatPro: 4, chapter: 2, resonance: 8, report: 7, imageUpload: 12, imageAnalysis: 8 },
  { diaryFlash: 20, diaryPro: 5, chatFlash: 55, chatPro: 5, chapter: 2, resonance: 10, report: 11, imageUpload: 16, imageAnalysis: 12 },
  { diaryFlash: 25, diaryPro: 6, chatFlash: 65, chatPro: 6, chapter: 2, resonance: 12, report: 16, imageUpload: 20, imageAnalysis: 15 },
  { chat: 35,  analysis: 12, reasoning: 6,  resonance: 5,  report: 4,  imageUpload: 8,  imageAnalysis: 5 },
  { chat: 45,  analysis: 16, reasoning: 8,  resonance: 8,  report: 7,  imageUpload: 12, imageAnalysis: 8 },
  { chat: 55,  analysis: 20, reasoning: 10, resonance: 10, report: 11, imageUpload: 16, imageAnalysis: 12 },
  { chat: 65,  analysis: 25, reasoning: 12, resonance: 12, report: 16, imageUpload: 20, imageAnalysis: 15 },
]

const quotaTable = computed(() => {
  return LEVEL_LABELS.map((label, i) => {
    const d = QUOTA_DATA[i]
    const isCurrent = label === `Lv.${props.level}`
    return {
      label,
      diaryFlash: d.diaryFlash + '次',
      diaryPro: d.diaryPro + '次',
      chatFlash: d.chatFlash > 900 ? '不限' : d.chatFlash + '次',
      chatPro: d.chatPro + '次',
      chapter: d.chapter + '次',
      resonance: d.resonance === 0 ? '—' : d.resonance + '次',
      report: d.report === 0 ? '—' : d.report > 900 ? '不限' : d.report + '次',
      imageUpload: d.imageUpload + '次',
      imageAnalysis: d.imageAnalysis + '次',
      isCurrent,
    }
  })
})
</script>

<style scoped>
.quota-overlay {
  position: fixed;
  inset: 0;
  background: var(--color-overlay);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.quota-modal {
  background: var(--color-surface);
  border-radius: 14px;
  max-width: 640px;
  width: 100%;
  max-height: 85vh;
  overflow-y: auto;
  padding: 24px;
  box-shadow: var(--shadow-lg);
}

.quota-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.quota-modal-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--color-text);
}

.quota-modal-close {
  background: none;
  border: none;
  font-size: 24px;
  color: var(--color-text-light);
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.quota-modal-desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.quota-table-wrap {
  overflow-x: auto;
}

.quota-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.quota-table th,
.quota-table td {
  padding: 8px 10px;
  text-align: center;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

.quota-table th {
  font-weight: 600;
  color: var(--color-text-secondary);
  background: var(--color-surface-soft);
  position: sticky;
  top: 0;
}

.quota-table th:first-child,
.quota-table td:first-child {
  text-align: left;
  font-weight: 600;
}

.quota-unit {
  font-weight: 400;
  font-size: 11px;
  color: var(--color-text-light);
}

.quota-row-active {
  background: color-mix(in oklab, var(--color-primary) 8%, transparent);
}

.quota-row-active td:first-child {
  color: var(--color-primary);
}

.quota-modal-footer {
  margin: 10px 0 0;
  font-size: 11px;
  color: var(--color-text-light);
  text-align: center;
}

.quota-rules-desc {
  margin-top: 16px;
  padding: 12px;
  background: color-mix(in oklab, var(--color-primary) 4%, transparent);
  border-radius: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.rule-item {
  margin-bottom: 6px;
}
.rule-item:last-child {
  margin-bottom: 0;
}
.rule-item strong {
  color: var(--color-primary);
}
</style>
