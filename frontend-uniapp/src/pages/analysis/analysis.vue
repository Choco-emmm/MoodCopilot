<template>
  <scroll-view scroll-y class="analysis-page" :style="globalThemeStyle">
    <GlobalUI :tabIndex="1" />
    <view class="header">
      <text class="title">洞察</text>
      <text class="subtitle">把一段时间的报告和重要记忆放在同一个入口。</text>
      <view class="insight-entry-switch">
        <view class="insight-entry active">
          <text class="entry-kicker">当前</text>
          <text>记忆中心</text>
        </view>
        <view class="insight-entry" @click="goToReports">
          <text class="entry-kicker">按时间回顾</text>
          <text>情绪报告</text>
          <text class="entry-arrow">›</text>
        </view>
      </view>
    </view>

    <!-- 情绪周报 -->
    <view v-if="showLegacyReports" class="section">
      <text class="section-title">本周情绪报告</text>
      
      <view v-if="loadingReport" class="loading-state">
        <text>正在查阅记录...</text>
      </view>
      
      <view v-else-if="weeklyReport" class="card report-card">
        <view class="report-header">
          <text class="report-week">{{ weeklyReport.weekLabel || '本周' }}</text>
          <text class="report-count">共记录 {{ weeklyReport.diaryCount }} 篇日记。</text>
        </view>

        <!-- Generate Button -->
        <view v-if="weeklyReport.needsRegenerate" class="generate-box">
          <text class="generate-desc">你的记录有了新变化，快让 AI 为你生成最新的情绪洞察吧！</text>
          <button class="generate-btn" :loading="isGenerating" @click="generateReport">
            ✨ 立即生成专属报告
          </button>
        </view>

        <!-- Stats Board -->
        <view v-if="weeklyReport.aiSummary" class="stats-board">
          <view class="stat-item" v-if="weeklyReport.positiveRatioPercent !== null">
            <text class="stat-label">积极占比</text>
            <view class="progress-bar">
              <view class="progress-fill positive" :style="{ width: weeklyReport.positiveRatioPercent + '%' }"></view>
            </view>
            <text class="stat-val">{{ weeklyReport.positiveRatioPercent }}%</text>
          </view>
          <view class="stat-item" v-if="weeklyReport.highEnergyRatioPercent !== null">
            <text class="stat-label">高能占比</text>
            <view class="progress-bar">
              <view class="progress-fill energy" :style="{ width: weeklyReport.highEnergyRatioPercent + '%' }"></view>
            </view>
            <text class="stat-val">{{ weeklyReport.highEnergyRatioPercent }}%</text>
          </view>
          <view class="stat-item" v-if="weeklyReport.moodDominantQuadrant">
            <text class="stat-label">主导情绪</text>
            <text class="stat-quadrant">{{ weeklyReport.moodDominantQuadrant }}</text>
          </view>
        </view>

        <!-- AI Summary -->
        <view v-if="weeklyReport.aiSummary" class="text-block">
          <text class="block-title">💡 AI 综合总结</text>
          <rich-text class="block-content" :nodes="parseMarkdown(weeklyReport.aiSummary)"></rich-text>
        </view>

        <!-- Insights -->
        <view v-if="weeklyReport.insights && weeklyReport.insights.length > 0" class="text-block">
          <text class="block-title">🔍 深度洞察</text>
          <view class="list-item" v-for="(insight, idx) in weeklyReport.insights" :key="'i'+idx">
            <text class="bullet">。</text>
            <rich-text class="block-content" :nodes="parseMarkdown(insight)"></rich-text>
          </view>
        </view>

        <!-- Suggestions -->
        <view v-if="weeklyReport.suggestions && weeklyReport.suggestions.length > 0" class="text-block">
          <text class="block-title">🌱 行动建议</text>
          <view class="list-item" v-for="(sugg, idx) in weeklyReport.suggestions" :key="'s'+idx">
            <text class="bullet">。</text>
            <rich-text class="block-content" :nodes="parseMarkdown(sugg)"></rich-text>
          </view>
        </view>
        
        <view v-if="!weeklyReport.needsRegenerate && !weeklyReport.aiSummary" class="empty-text">
          <text>暂无报告数据，多写几篇日记再来看看吧。</text>
        </view>
      </view>
      
      <view v-else class="card empty-card">
        <text class="empty-text">本周还没有写日记哦。</text>
      </view>
    </view>

    <!-- 往期月度总结 -->
    <view v-if="showLegacyReports" class="section">
      <text class="section-title">往期月度总结</text>
      
      <view v-if="loadingSummaries" class="loading-state">
        <text>正在查阅历史...</text>
      </view>
      
      <view v-else-if="monthlySummaries.length > 0" class="summary-list">
        <view class="card summary-card" v-for="summary in monthlySummaries" :key="summary.id">
          <text class="summary-title">{{ summary.title }}</text>
          <text class="summary-date">{{ summary.startDate }} 至 {{ summary.endDate }}</text>
          <rich-text class="block-content" :nodes="parseMarkdown(summary.aiSummary)"></rich-text>
        </view>
      </view>
      
      <view v-else class="card empty-card">
        <text class="empty-text">暂时没有月度总结，坚持记录日记，系统会按月自动生成。</text>
      </view>
    </view>

    <!-- 个人记忆 -->
    <view class="section">
      <view class="section-title-row">
        <text class="section-title">你的专属记忆</text>
        <button class="section-action" @click="previewConsolidate" :loading="isConsolidating">整理记忆</button>
      </view>
      <text class="section-desc">这里保存你的个人记忆；近期状态只用于当前关怀参考，不会作为核心长期画像。</text>
      
      <view v-if="loadingMemory" class="loading-state">
        <text>正在提取记忆...</text>
      </view>
      <view v-else-if="memoryError" class="card inline-error-card">
        <text class="empty-text">正式记忆暂时无法加载</text>
        <text class="inline-error-detail">请稍后重试</text>
        <button class="inline-retry-btn" @click="fetchMemory">重新加载</button>
      </view>
      
      <view v-else-if="memories.length > 0">
        <view class="memory-overview"><text>{{ memoryGroups.length }} 个属性 · {{ memories.length }} 条记忆</text><text>点击属性查看详情</text></view>
        <view class="memory-groups">
          <view v-for="(group, groupIndex) in memoryGroups" :key="group.key" :class="['memory-group', { 'memory-group-expanded': isMemoryGroupExpanded(group, groupIndex) }]">
            <view class="memory-group-header" @click="toggleMemoryGroup(group.key, groupIndex)">
              <view class="memory-group-heading"><text class="memory-group-title">{{ group.label }}</text><text class="memory-group-count">{{ group.items.length }} 条</text></view>
              <text class="memory-group-toggle">{{ isMemoryGroupExpanded(group, groupIndex) ? '收起' : '展开' }} {{ isMemoryGroupExpanded(group, groupIndex) ? '⌃' : '⌄' }}</text>
            </view>
            <view v-if="isMemoryGroupExpanded(group, groupIndex)" class="memory-list">
              <view v-for="m in group.items" :key="m.id" class="memory-row" @click="openEditMemory(m)">
                <view class="memory-row-main">
                  <view v-if="isSafetyState(m) || m.isCore" class="memory-row-head">
                    <text v-if="isSafetyState(m)" class="safety-badge">近期状态</text>
                    <text v-else-if="m.isCore" class="core-badge">核心</text>
                  </view>
                  <text class="memory-value">{{ memoryPreview(m.attributeValue) }}</text>
                  <view class="memory-source-row">
                    <text class="memory-source-label">{{ sourceTypeLabel(m) }}</text>
                    <text v-if="diarySourcesFor(m).length" class="memory-source-preview">{{ diarySourceLabel(diarySourcesFor(m)[0]) }}</text><text v-if="diarySourcesFor(m).length" class="memory-source-link" @click.stop="openMemoryDiarySources(m)">查看关联日记{{ diarySourcesFor(m).length > 1 ? `（${diarySourcesFor(m).length}）` : '' }} →</text>
                    <text v-if="conversationIdsFor(m).length" class="memory-source-link" @click.stop="openMemorySource(null, conversationIdsFor(m)[0])">查看关联会话 →</text>
                    <text v-if="!diaryIdsFor(m).length && !conversationIdsFor(m).length" class="memory-source-empty">暂无原始来源</text>
                  </view>
                  <text v-if="m.updatedAt || m.updateTime" class="memory-updated">最近更新 {{ formatMemoryTime(m.updatedAt || m.updateTime) }}</text>
                </view>
                <view class="memory-row-actions">
                  <text class="memory-edit" @click.stop="openMemoryDetails(m)">依据</text>
                  <text class="memory-edit">编辑</text>
                  <text class="del-btn" @click.stop="deleteMemory(m.id)">×</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
      
      <view v-else class="card empty-card">
        <text class="empty-text">AI 正在努力了解你，多写点日记给它线索吧。</text>
      </view>
      <view class="candidate-section">
        <view class="candidate-heading">
          <view class="candidate-heading-main">
            <text class="candidate-title">待确认的记忆</text>
            <text class="candidate-desc">AI 的推断需要你确认后才会进入正式画像。</text>
          </view>
          <text class="candidate-count">{{ candidates.length }} 条</text>
        </view>
        <text v-if="candidates.length" class="candidate-summary">{{ candidateGroups.length }} 个属性需要你的确认</text>
        <view v-if="loadingCandidates" class="loading-state candidate-loading-state"><text>正在加载待确认记忆...</text></view>
        <view v-else-if="candidateError" class="inline-error-card candidate-error-card">
          <text class="empty-text">待确认记忆暂时无法加载</text>
          <text class="inline-error-detail">请稍后重试，正式记忆不受影响</text>
          <button class="inline-retry-btn" @click="fetchCandidates">重新加载</button>
        </view>
        <view v-else-if="candidates.length === 0" class="candidate-empty">暂无待确认记忆</view>
        <view v-else v-for="(group, groupIndex) in candidateGroups" :key="group.key" class="candidate-group">
          <view class="candidate-group-header" @click="toggleCandidateGroup(group.key, groupIndex)">
            <view class="candidate-group-heading"><text class="candidate-group-title">{{ group.label }}</text><text class="candidate-group-count">{{ group.items.length }} 条候选</text></view>
            <text class="candidate-group-toggle">{{ isCandidateGroupExpanded(group, groupIndex) ? '收起' : '展开' }} {{ isCandidateGroupExpanded(group, groupIndex) ? '⌃' : '⌄' }}</text>
          </view>
          <view v-if="isCandidateGroupExpanded(group, groupIndex)">
            <text v-if="group.hasConflict" class="candidate-conflict-note">同一属性存在不同候选，请分别确认。</text>
            <view v-for="candidate in group.items" :key="candidate.id" class="candidate-row">
              <view class="candidate-copy"><text class="candidate-value">{{ candidate.attributeValue }}</text><text class="candidate-evidence">{{ isSafetyState(candidate) ? '这是需要关注的近期状态，不属于核心长期画像。' : (candidate.evidenceSummary || '暂无证据摘要') }}</text><text class="candidate-evidence">已有 {{ candidate.evidenceCount || 0 }} 条依据 · {{ sourceTypeLabel(candidate) }}</text><text v-if="diarySourcesFor(candidate).length" class="candidate-source-preview">{{ diarySourceLabel(diarySourcesFor(candidate)[0]) }}</text><text v-if="diarySourcesFor(candidate).length" class="candidate-source-link" @click.stop="openMemoryDiarySources(candidate)">查看关联日记{{ diarySourcesFor(candidate).length > 1 ? `（${diarySourcesFor(candidate).length}）` : '' }} →</text><text v-else-if="conversationIdsFor(candidate).length" class="candidate-source-link" @click.stop="openMemorySource(null, conversationIdsFor(candidate)[0])">查看关联会话 →</text><text v-else class="candidate-evidence">暂无原始来源</text></view>
              <view class="candidate-actions"><text :class="['candidate-approve', { 'candidate-action-disabled': candidateActionId === candidate.id }]" @click="approveCandidate(candidate.id)">确认</text><text :class="['candidate-reject', { 'candidate-action-disabled': candidateActionId === candidate.id }]" @click="rejectCandidate(candidate.id)">拒绝</text></view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="modal-overlay" v-if="showMemoryDetailsModal" @click="showMemoryDetailsModal = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">记忆依据与历史</text>
        <scroll-view scroll-y class="preview-list">
          <view v-if="memoryDetailsLoading" class="empty-text">正在加载...</view>
          <template v-else>
            <view v-for="item in memoryEvidence" :key="`e-${item.id}`" class="preview-item">
              <text class="preview-key">{{ item.evidenceDate || '未标日期' }}</text>
              <text class="preview-value">{{ item.evidenceText }}</text>
              <text v-if="item.sourceDiaryId" class="memory-source-link" @click="openMemorySource(item.sourceDiaryId, null)">查看关联日记 →</text>
            </view>
            <view v-for="item in historicalMemoryVersions" :key="`h-${item.id}`" class="preview-item">
              <text class="preview-key">历史版本 · {{ memoryStatusLabel(item.status) }}</text>
              <text class="preview-value">{{ item.attributeValue }}</text>
            </view>
            <text v-if="!memoryEvidence.length && !historicalMemoryVersions.length" class="empty-text">这条记忆没有保存可展示的原始依据。</text>
          </template>
        </scroll-view>
        <view class="modal-actions"><button class="cancel-btn" @click="showMemoryDetailsModal = false">关闭</button></view>
      </view>
    </view>

    <!-- 关系图谱 -->
    <view class="section">
      <view class="section-title-row">
        <text class="section-title">羁绊图谱</text>
        <button class="section-action" @click="previewGraphConsolidate" :loading="isGraphConsolidating">整理图谱</button>
      </view>
      <text class="section-desc">在你的世界里，谁是常客？</text>
      
      <view v-if="loadingGraph" class="loading-state">
        <text>正在连接图谱...</text>
      </view>
      <view v-else-if="graphError" class="card inline-error-card">
        <text class="empty-text">关系图谱暂时无法加载</text>
        <text class="inline-error-detail">请稍后重试</text>
        <button class="inline-retry-btn" @click="fetchGraph">重新加载</button>
      </view>
      
      <view v-else-if="triples.length > 0" class="graph-container">
        <view class="graph-toolbar">
          <input v-model="graphKeyword" class="graph-search" placeholder="搜索人物、关系或事物" confirm-type="search" />
          <text v-if="graphKeyword" class="graph-search-clear" @click="graphKeyword = ''">清除</text>
        </view>
        <view class="graph-summary"><text>共 {{ filteredTriples.length }} 条关联</text><text v-if="filteredTriples.length > graphDisplayLimit && !graphExpanded">，先展示 {{ graphDisplayLimit }} 条</text></view>
        <view v-if="filteredTriples.length > 0">
          <view v-for="t in visibleTriples" :key="t.id" class="graph-link" @click="openEditGraph(t)">
          <view class="item-actions">
            <text class="del-btn" @click.stop="deleteTriple(t.id)">×</text>
          </view>
          <view class="graph-node subject">
            <text>{{ t.headEntity }}</text>
          </view>
          <view class="graph-edge">
            <text class="edge-text">{{ t.relation }}</text>
            <view class="edge-line"></view>
          </view>
          <view class="graph-node object">
            <text>{{ t.tailEntity }}</text>
          </view>
          </view>
          <view v-if="filteredTriples.length > graphDisplayLimit" class="graph-expand-row" @click="graphExpanded = !graphExpanded"><text>{{ graphExpanded ? '收起关联' : `展开全部 ${filteredTriples.length} 条关联` }}</text><text>{{ graphExpanded ? '⌃' : '⌄' }}</text></view>
        </view>
        <view v-else class="graph-filter-empty"><text>没有匹配的关联</text><text @click="graphKeyword = ''">清除筛选</text></view>
      </view>
      
      <view v-else class="card empty-card">
        <text class="empty-text">暂无关系数据，下次日记记得提到你的好朋友哦。</text>
      </view>
    </view>

    <!-- Consolidation Modal -->
    <view class="modal-overlay" v-if="showConsolidateModal" @click="showConsolidateModal = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">记忆整理预览</text>
        <scroll-view scroll-y class="preview-list">
          <view v-if="previewMemories.length === 0" class="empty-text">
            没有需要整理的新记忆。
          </view>
          <view v-else v-for="(m, idx) in previewMemories" :key="idx" class="preview-item">
            <text class="preview-key">{{ m.attributeKey }}</text>
            <text class="preview-value">{{ m.attributeValue }}</text>
            <text class="preview-source">合并 {{ (m.sourceMemoryIds || []).length }} 条相同记忆 · 保留 {{ (m.evidenceIds || []).length }} 条证据</text>
          </view>
        </scroll-view>
        <view class="modal-actions">
          <button class="cancel-btn" @click="showConsolidateModal = false">取消</button>
          <button class="confirm-btn" @click="applyConsolidate" :disabled="previewMemories.length === 0">确认应用</button>
        </view>
      </view>
    </view>

    <!-- Edit Memory Modal -->
    <view class="modal-overlay" v-if="showEditMemoryModal" @click="showEditMemoryModal = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">编辑专属记忆</text>
        <view class="edit-form">
          <text class="edit-label">记忆类型</text>
          <input class="edit-input" v-model="editMemoryForm.attributeKey" disabled />
          
          <text class="edit-label">记忆详情</text>
          <textarea class="edit-textarea" v-model="editMemoryForm.attributeValue"></textarea>
          
          <view class="edit-switch-row">
            <text class="edit-label">设为核心记忆</text>
            <switch v-if="!isSafetyState(editMemoryForm)" :checked="editMemoryForm.isCore" @change="handleCoreMemoryChange" :color="currentTheme.primary" style="transform: scale(0.8);" />
            <text v-else class="safety-edit-note">近期状态不可设为核心长期画像</text>
          </view>
        </view>
        <view class="modal-actions">
          <button class="cancel-btn" @click="showEditMemoryModal = false">取消</button>
          <button class="confirm-btn" @click="saveMemory">保存</button>
        </view>
      </view>
    </view>

    <!-- Graph Consolidation Modal -->
    <view class="modal-overlay" v-if="showGraphConsolidateModal" @click="closeGraphConsolidateModal">
      <view class="modal-content" @click.stop>
        <text class="modal-title">图谱整理预览</text>
        <scroll-view scroll-y class="preview-list">
          <view v-if="previewGraphTriples.length === 0" class="empty-text">
            没有需要整理的关联。
          </view>
          <view v-else v-for="(t, idx) in previewGraphTriples" :key="idx" class="preview-item">
            <text class="preview-key">{{ t.headEntity }} → {{ t.relation }} → {{ t.tailEntity }}</text>
            <text class="preview-value">{{ graphOperationLabel(t.operation) }}</text>
            <text class="preview-source">来自 {{ (t.sourceTripleIds || []).length }} 条关系 · 关联 {{ (t.sourceDiaryIds || []).length }} 篇日记</text>
          </view>
        </scroll-view>
        <view class="modal-actions">
          <button class="cancel-btn" @click="closeGraphConsolidateModal" :disabled="isGraphApplying">取消</button>
          <button class="confirm-btn" @click="applyGraphConsolidate" :loading="isGraphApplying" :disabled="previewGraphTriples.length === 0 || isGraphApplying">确认应用</button>
        </view>
      </view>
    </view>

    <!-- Edit Graph Modal -->
    <view class="modal-overlay" v-if="showEditGraphModal" @click="showEditGraphModal = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">编辑羁绊</text>
        <view class="edit-form">
          <text class="edit-label">主体 (谁)</text>
          <input class="edit-input" v-model="editGraphForm.headEntity" />
          
          <text class="edit-label">关系 (做了什么 / 是什么)</text>
          <input class="edit-input" v-model="editGraphForm.relation" />
          
          <text class="edit-label">客体 (对谁 / 对什么)</text>
          <input class="edit-input" v-model="editGraphForm.tailEntity" />
        </view>
        <view class="modal-actions">
          <button class="cancel-btn" @click="showEditGraphModal = false">取消</button>
          <button class="confirm-btn" @click="saveGraph">保存</button>
        </view>
      </view>
    </view>
  </scroll-view>
</template>

<script setup lang="ts">
import GlobalUI from '@/components/GlobalUI.vue';

import { ref, computed, onMounted, onUnmounted } from 'vue';
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app';
import { get, post, put, del } from '@/utils/request';
import { parseMarkdown } from '@/utils/markdown';
import { hasLoginToken, requireLogin } from '@/stores/login';
import { currentTheme } from '@/stores/theme';

const loadingReport = ref(false);
const weeklyReport = ref<any>(null);
const isGenerating = ref(false);
const showLegacyReports = false;

const loadingMemory = ref(false);
const memories = ref<any[]>([]);
const memoryError = ref(false);
const expandedMemoryGroups = ref<string[]>([]);
const collapsedMemoryGroups = ref<string[]>([]);
const candidates = ref<any[]>([]);
const loadingCandidates = ref(false);
const candidateError = ref(false);
const candidateActionId = ref<number | null>(null);
const expandedCandidateGroups = ref<string[]>([]);
const collapsedCandidateGroups = ref<string[]>([]);
const showMemoryDetailsModal = ref(false);
const memoryDetailsLoading = ref(false);
const memoryEvidence = ref<any[]>([]);
const memoryHistory = ref<any[]>([]);
const activeMemoryDetailsId = ref<number | null>(null);
const historicalMemoryVersions = computed(() => memoryHistory.value.filter(item => item.id !== activeMemoryDetailsId.value));
const memoryGroups = computed(() => {
  const groups = new Map<string, { key: string; label: string; items: any[] }>();
  for (const memory of memories.value) {
    const key = `${memory.memoryType || 'memory'}:${memory.attributeKey || '未命名属性'}`;
    const group = groups.get(key) || { key, label: memory.attributeKey || '未命名属性', items: [] as any[] };
    group.items.push(memory);
    groups.set(key, group);
  }
  return Array.from(groups.values());
});
const candidateGroups = computed(() => {
  const groups = new Map<string, { key: string; label: string; items: any[]; hasConflict: boolean }>();
  for (const candidate of candidates.value) {
    const key = candidate.candidateGroupKey || `${candidate.memoryType || 'memory'}:${candidate.attributeKey}`;
    const group = groups.get(key) || { key, label: candidate.attributeKey || '未命名属性', items: [] as any[], hasConflict: false };
    group.items.push(candidate);
    group.hasConflict = group.hasConflict || Boolean(candidate.hasConflict);
    groups.set(key, group);
  }
  return Array.from(groups.values());
});

const isMemoryGroupExpanded = (group: { key: string }, index: number) => {
  return expandedMemoryGroups.value.includes(group.key)
    || (!collapsedMemoryGroups.value.includes(group.key) && index < 1);
};

const toggleMemoryGroup = (key: string, index: number) => {
  const expanded = isMemoryGroupExpanded({ key }, index);
  if (expanded) {
    expandedMemoryGroups.value = expandedMemoryGroups.value.filter(item => item !== key);
    if (!collapsedMemoryGroups.value.includes(key)) collapsedMemoryGroups.value = [...collapsedMemoryGroups.value, key];
  } else {
    collapsedMemoryGroups.value = collapsedMemoryGroups.value.filter(item => item !== key);
    if (!expandedMemoryGroups.value.includes(key)) expandedMemoryGroups.value = [...expandedMemoryGroups.value, key];
  }
};

const isCandidateGroupExpanded = (group: { key: string }, index: number) => {
  return expandedCandidateGroups.value.includes(group.key)
    || (!collapsedCandidateGroups.value.includes(group.key) && index < 1);
};

const toggleCandidateGroup = (key: string, index: number) => {
  const expanded = isCandidateGroupExpanded({ key }, index);
  if (expanded) {
    expandedCandidateGroups.value = expandedCandidateGroups.value.filter(item => item !== key);
    if (!collapsedCandidateGroups.value.includes(key)) collapsedCandidateGroups.value = [...collapsedCandidateGroups.value, key];
  } else {
    collapsedCandidateGroups.value = collapsedCandidateGroups.value.filter(item => item !== key);
    if (!expandedCandidateGroups.value.includes(key)) expandedCandidateGroups.value = [...expandedCandidateGroups.value, key];
  }
};

const loadingGraph = ref(false);
const triples = ref<any[]>([]);
const graphError = ref(false);
const graphKeyword = ref('');
const graphExpanded = ref(false);
const graphDisplayLimit = 12;
const filteredTriples = computed(() => {
  const keyword = graphKeyword.value.trim().toLocaleLowerCase();
  if (!keyword) return triples.value;
  return triples.value.filter((triple) => [triple.headEntity, triple.relation, triple.tailEntity]
    .some((value) => String(value || '').toLocaleLowerCase().includes(keyword)));
});
const visibleTriples = computed(() => graphExpanded.value ? filteredTriples.value : filteredTriples.value.slice(0, graphDisplayLimit));

const loadingSummaries = ref(false);
const monthlySummaries = ref<any[]>([]);

const isConsolidating = ref(false);
const showConsolidateModal = ref(false);
const previewMemories = ref<any[]>([]);

const isGraphConsolidating = ref(false);
const isGraphApplying = ref(false);
const showGraphConsolidateModal = ref(false);
const previewGraphTriples = ref<any[]>([]);

const graphOperationLabel = (operation?: string) => ({
  MERGE: '合并重复关系', DEDUP: '去除重复', NORMALIZE: '统一表达',
}[operation || ''] || '整理建议');
const isLoggedIn = ref(hasLoginToken());

const loadAllData = async () => {
  fetchMemory();
  fetchCandidates();
  fetchGraph();
};

onMounted(() => {
  if (isLoggedIn.value) loadAllData();
  uni.$on('login-success', loadAfterLogin);
  uni.$on('refreshMemory', fetchMemory);
  uni.$on('refreshGraph', fetchGraph);
  uni.$on('refreshAnalysis', refreshAnalysisData);
});

onUnmounted(() => {
  uni.$off('login-success', loadAfterLogin);
  uni.$off('refreshMemory', fetchMemory);
  uni.$off('refreshGraph', fetchGraph);
  uni.$off('refreshAnalysis', refreshAnalysisData);
});

function refreshAnalysisData() {
  if (isLoggedIn.value) loadAllData();
}

function loadAfterLogin() {
  isLoggedIn.value = true;
  loadAllData();
}

onPullDownRefresh(async () => {
  if (!isLoggedIn.value) {
    uni.stopPullDownRefresh();
    return;
  }
  await Promise.all([
    fetchMemory(),
    fetchCandidates(),
    fetchGraph()
  ]);
  uni.stopPullDownRefresh();
});

const fetchReport = () => {
  loadingReport.value = true;
  return get('/api/diaries/weekly-report')
    .then((res: any) => {
      loadingReport.value = false;
      if (res.code === 200) {
        weeklyReport.value = res.data;
      }
    })
    .catch(() => loadingReport.value = false);
};

const generateReport = async () => {
  if (isGenerating.value) return;
  isGenerating.value = true;
  try {
    const res = await post('/api/diaries/weekly-report/generate');
      if (res.code === 200) {
        uni.showToast({ title: '报告已更新', icon: 'success' });
        weeklyReport.value = res.data;
    }
  } catch (e) {
    console.error(e);
  } finally {
    isGenerating.value = false;
  }
};

const fetchMemory = () => {
  loadingMemory.value = true;
  memoryError.value = false;
  return get('/api/memory')
    .then((res: any) => {
      loadingMemory.value = false;
      if (res.code === 200) {
        memories.value = res.data || [];
      } else {
        memoryError.value = true;
      }
    })
    .catch(() => {
      loadingMemory.value = false;
      memoryError.value = true;
    });
};

const fetchCandidates = () => {
  loadingCandidates.value = true;
  candidateError.value = false;
  return get('/api/memory/candidates?status=PENDING&page=1&size=20&sort=updatedAt')
    .then((res: any) => {
      if (res.code === 200) {
        candidates.value = Array.isArray(res.data) ? res.data : (res.data?.content || []);
      } else {
        candidateError.value = true;
      }
    })
    .catch(() => {
      candidateError.value = true;
    })
    .finally(() => {
      loadingCandidates.value = false;
    });
};

const diaryIdsFor = (item: any): number[] => Array.from(new Set([...(item.sourceDiaryIds || []), item.sourceDiaryId].filter(Boolean).map(Number)));
type DiarySourcePreview = { id: number; createdAt?: string | null; excerpt?: string | null };
const diarySourcesFor = (item: any): DiarySourcePreview[] => {
  const previews = Array.isArray(item.sourceDiaryPreviews) ? item.sourceDiaryPreviews.filter((source: any) => source?.id) : [];
  return previews.length ? previews : diaryIdsFor(item).map(id => ({ id }));
};
const diarySourceLabel = (source: DiarySourcePreview): string => {
  const date = source.createdAt ? formatDiarySourceDate(source.createdAt) : '日期未知';
  return `${date} · “${source.excerpt?.trim() || '打开查看日记内容'}”`;
};
const formatDiarySourceDate = (value: string): string => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 10);
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
};
const conversationIdsFor = (item: any): number[] => Array.from(new Set([...(item.sourceConversationIds || []), item.sourceConversationId].filter(Boolean).map(Number)));
const sourceTypeLabel = (item: any): string => {
  if (diaryIdsFor(item).length) return '来自日记';
  if (conversationIdsFor(item).length) return '来自聊天';
  if (item.sourceType === 'USER_ACTION') return '用户整理';
  if (item.sourceType === 'explicit') return '用户确认';
  if (item.sourceType === 'system') return '系统整理';
  return '暂无原始来源';
};
const formatMemoryTime = (value: string | null | undefined): string => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 16);
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

const openMemoryDetails = async (memory: any) => {
  activeMemoryDetailsId.value = memory.id;
  showMemoryDetailsModal.value = true;
  memoryDetailsLoading.value = true;
  memoryEvidence.value = [];
  memoryHistory.value = [];
  try {
    const [evidence, history] = await Promise.all([
      get(`/api/memory/${memory.id}/evidence`),
      get(`/api/memory/${memory.id}/history`)
    ]);
    memoryEvidence.value = evidence.code === 200 ? evidence.data || [] : [];
    memoryHistory.value = history.code === 200 ? history.data || [] : [];
  } catch (e) {
    uni.showToast({ title: '依据加载失败', icon: 'none' });
  } finally {
    memoryDetailsLoading.value = false;
  }
};

const approveCandidate = async (id: number) => {
  if (candidateActionId.value !== null) return;
  candidateActionId.value = id;
  try {
    const res = await post(`/api/memory/candidates/${id}/approve`);
    if (res.code === 200) {
      candidates.value = candidates.value.filter(candidate => candidate.id !== id);
      await Promise.all([fetchMemory(), fetchCandidates()]);
      showMemoryDetailsModal.value = false;
      activeMemoryDetailsId.value = null;
      memoryEvidence.value = [];
      memoryHistory.value = [];
      uni.showToast({ title: '记忆已确认', icon: 'success' });
    }
  } catch (e) {
    uni.showToast({ title: '确认失败，请稍后再试', icon: 'none' });
  } finally {
    candidateActionId.value = null;
  }
};

const openMemorySource = (diaryId: number | null, conversationId: number | null) => {
  if (diaryId) {
    uni.navigateTo({ url: `/pages/detail/detail?id=${diaryId}` });
    return;
  }
  if (conversationId) {
    uni.setStorageSync('pendingChatConversationId', conversationId);
    uni.switchTab({ url: '/pages/chat/chat' });
  }
};

const openMemoryDiarySources = (item: any) => {
  const sources = diarySourcesFor(item);
  if (sources.length === 1) {
    openMemorySource(sources[0].id, null);
    return;
  }
  uni.showActionSheet({
    itemList: sources.map(source => diarySourceLabel(source)),
    success: (result) => openMemorySource(sources[result.tapIndex].id, null)
  });
};

const rejectCandidate = async (id: number) => {
  if (candidateActionId.value !== null) return;
  candidateActionId.value = id;
  try {
    const res = await post(`/api/memory/candidates/${id}/reject`);
    if (res.code === 200) {
      candidates.value = candidates.value.filter(candidate => candidate.id !== id);
      uni.showToast({ title: '已拒绝', icon: 'none' });
    }
  } catch (e) {
    uni.showToast({ title: '拒绝失败，请稍后再试', icon: 'none' });
  } finally {
    candidateActionId.value = null;
  }
};

const fetchGraph = () => {
  loadingGraph.value = true;
  graphError.value = false;
  return get('/api/graph/triples')
    .then((res: any) => {
      loadingGraph.value = false;
      if (res.code === 200) {
        triples.value = res.data || [];
      } else {
        graphError.value = true;
      }
    })
    .catch(() => {
      loadingGraph.value = false;
      graphError.value = true;
    });
};

const fetchSummaries = () => {
  loadingSummaries.value = true;
  return get('/api/summaries?type=MONTHLY')
    .then((res: any) => {
      loadingSummaries.value = false;
      if (res.code === 200) {
        monthlySummaries.value = res.data || [];
      }
    })
    .catch(() => loadingSummaries.value = false);
};

const previewConsolidate = async () => {
  if (!isLoggedIn.value) {
    requireLogin();
    return;
  }
  if (isConsolidating.value) return;
  isConsolidating.value = true;
  try {
    const res = await post('/api/memory/consolidate/preview');
    if (res.code === 200) {
      previewMemories.value = res.data || [];
      showConsolidateModal.value = true;
    }
  } catch (e: any) {
    uni.showToast({ title: e.message || '预览失败', icon: 'none' });
  } finally {
    isConsolidating.value = false;
  }
};

const applyConsolidate = async () => {
  try {
    const res = await post('/api/memory/consolidate/apply', previewMemories.value);
    if (res.code === 200) {
      uni.showToast({ title: '记忆已巩固', icon: 'success' });
      showConsolidateModal.value = false;
      fetchMemory();
    }
  } catch (e) {
    uni.showToast({ title: '应用失败', icon: 'none' });
  }
};

const previewGraphConsolidate = async () => {
  if (!isLoggedIn.value) {
    requireLogin();
    return;
  }
  if (isGraphConsolidating.value) return;
  isGraphConsolidating.value = true;
  try {
    const res = await post('/api/graph/consolidate/preview');
    if (res.code === 200) {
      previewGraphTriples.value = res.data || [];
      showGraphConsolidateModal.value = true;
    }
  } catch (e: any) {
    uni.showToast({ title: e.message || '预览失败', icon: 'none' });
  } finally {
    isGraphConsolidating.value = false;
  }
};

const memoryPreview = (value: string) => {
  const compact = String(value || '').replace(/\s+/g, ' ').trim();
  return compact.length > 76 ? `${compact.slice(0, 76)}...` : compact;
};

const goToReports = () => {
  requireLogin(() => uni.navigateTo({ url: '/pages/summaries/summaries' }));
};

const closeGraphConsolidateModal = () => {
  if (!isGraphApplying.value) {
    showGraphConsolidateModal.value = false;
  }
};

const applyGraphConsolidate = async () => {
  if (isGraphApplying.value || previewGraphTriples.value.length === 0) return;
  isGraphApplying.value = true;
  try {
    const triples = previewGraphTriples.value.map((triple) => ({ ...triple }));
    const res = await post('/api/graph/consolidate/apply', triples);
    if (res.code === 200) {
      uni.showToast({ title: '图谱已整理', icon: 'success' });
      showGraphConsolidateModal.value = false;
      fetchGraph();
    }
  } catch (e) {
    uni.showToast({ title: '应用失败', icon: 'none' });
  } finally {
    isGraphApplying.value = false;
  }
};

const showEditMemoryModal = ref(false);
const editMemoryForm = ref<any>({});

const handleCoreMemoryChange = (event: any) => {
  editMemoryForm.value.isCore = event.detail.value;
};

const isSafetyState = (item: any) => String(item?.memoryType || '').toLowerCase() === 'short_term_state'
  || /自杀|自残|轻生|想死|不想活|结束生命|伤害自己|割腕|跳楼|心理危机|危机干预/.test(`${item?.attributeKey || ''} ${item?.attributeValue || ''}`);
const memoryStatusLabel = (status: string) => ({ active: '当前有效', superseded: '历史版本', expired: '已过期', rejected: '已拒绝' } as Record<string, string>)[status] || '历史记录';

const openEditMemory = (m: any) => {
  editMemoryForm.value = { ...m };
  if (isSafetyState(m)) editMemoryForm.value.isCore = false;
  showEditMemoryModal.value = true;
};

const saveMemory = async () => {
  try {
    const res = await put(`/api/memory/${editMemoryForm.value.id}`, {
      attributeValue: editMemoryForm.value.attributeValue,
      isCore: isSafetyState(editMemoryForm.value) ? false : editMemoryForm.value.isCore
    });
    if (res.code === 200) {
      uni.showToast({ title: '修改成功', icon: 'success' });
      showEditMemoryModal.value = false;
      fetchMemory();
    }
  } catch (e) {
    uni.showToast({ title: '修改失败', icon: 'none' });
  }
};

const deleteMemory = (id: number) => {
  uni.showModal({
    title: '删除记忆',
    content: '确定要删除这条记忆吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          const r = await del(`/api/memory/${id}`);
          if (r.code === 200) {
            uni.showToast({ title: '删除成功', icon: 'success' });
            fetchMemory();
          }
        } catch (e) {
          uni.showToast({ title: '删除失败', icon: 'none' });
        }
      }
    }
  });
};

const showEditGraphModal = ref(false);
const editGraphForm = ref<any>({});

const openEditGraph = (t: any) => {
  editGraphForm.value = { ...t };
  showEditGraphModal.value = true;
};

const saveGraph = async () => {
  try {
    const res = await put(`/api/graph/triples/${editGraphForm.value.id}`, editGraphForm.value);
    if (res.code === 200) {
      uni.showToast({ title: '修改成功', icon: 'success' });
      showEditGraphModal.value = false;
      fetchGraph();
    }
  } catch (e) {
    uni.showToast({ title: '修改失败', icon: 'none' });
  }
};

const deleteTriple = (id: number) => {
  uni.showModal({
    title: '删除羁绊',
    content: '确定要删除这条关系吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          const r = await del(`/api/graph/triples/${id}`);
          if (r.code === 200) {
            uni.showToast({ title: '删除成功', icon: 'success' });
            fetchGraph();
          }
        } catch (e) {
          uni.showToast({ title: '删除失败', icon: 'none' });
        }
      }
    }
  });
};
</script>

<style scoped>
.analysis-page {
  min-height: 100vh;
  background-color: var(--theme-bg);
  padding: 40rpx;
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.header {
  margin-top: 40rpx;
  margin-bottom: 60rpx;
}

.title {
  font-family: "Noto Serif SC", "Songti SC", "STSong", "KaiTi", serif;
  font-size: 64rpx;
  color: var(--theme-text-primary);
  font-weight: 700;
  display: block;
  margin-bottom: 16rpx;
  letter-spacing: 2rpx;
}

.subtitle {
  font-size: 26rpx;
  color: var(--theme-text-secondary);
  line-height: 1.6;
}

.insight-entry-switch {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12rpx;
  margin-top: 30rpx;
}

.insight-entry {
  position: relative;
  min-height: 106rpx;
  padding: 19rpx 18rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: 8rpx;
  background: var(--theme-surface);
  color: var(--theme-text-primary);
  font-size: 27rpx;
  font-weight: 650;
  box-sizing: border-box;
}

.insight-entry.active {
  border-color: var(--theme-primary);
  background: color-mix(in oklab, var(--theme-primary) 6%, var(--theme-surface));
  color: var(--theme-primary);
}

.entry-kicker {
  display: block;
  margin-bottom: 7rpx;
  color: var(--theme-text-placeholder);
  font-size: 19rpx;
  font-weight: 400;
}

.entry-arrow {
  position: absolute;
  right: 18rpx;
  bottom: 18rpx;
  color: var(--theme-primary);
  font-size: 33rpx;
  font-weight: 300;
  line-height: .7;
}

/* Memory center: a compact list keeps long memories readable without exposing full text. */
.memory-waterfall { display: none; }
.memory-list { display: flex; flex-direction: column; gap: 12rpx; }
.memory-row { display: flex; min-height: 114rpx; align-items: center; gap: 18rpx; padding: 21rpx 20rpx; border: 1rpx solid var(--theme-border); border-radius: 7rpx; background: var(--theme-surface); box-sizing: border-box; }
.memory-row:active { background: var(--theme-surface-hover); }
.memory-row-main { min-width: 0; flex: 1; }
.memory-row-head { display: flex; align-items: center; gap: 9rpx; }
.memory-key { overflow: hidden; color: var(--theme-text-primary); font-size: 25rpx; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.memory-value { display: -webkit-box; overflow: hidden; margin-top: 9rpx; color: var(--theme-text-secondary); font-size: 22rpx; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.memory-source-row { display: flex; align-items: center; flex-wrap: wrap; gap: 10rpx; margin-top: 8rpx; font-size: 20rpx; }
.memory-source-label, .memory-source-empty { color: var(--theme-text-placeholder); }
.memory-source-preview, .candidate-source-preview { display: block; overflow: hidden; margin-top: 7rpx; color: var(--theme-text-secondary); font-size: 19rpx; text-overflow: ellipsis; white-space: nowrap; }
.memory-source-link { color: var(--theme-primary); }
.memory-updated { display: block; margin-top: 6rpx; color: var(--theme-text-placeholder); font-size: 19rpx; }
.candidate-group + .candidate-group { margin-top: 12rpx; }
.candidate-conflict-note { display: block; margin: 8rpx 0; color: var(--theme-accent); font-size: 20rpx; }
.candidate-source-link { display: block; margin-top: 6rpx; color: var(--theme-primary); font-size: 20rpx; }
.core-badge { padding: 3rpx 9rpx; border-radius: 4rpx; background: color-mix(in oklab, var(--theme-primary) 10%, var(--theme-surface)); color: var(--theme-primary); font-size: 18rpx; line-height: 1.4; white-space: nowrap; }
.safety-badge { padding: 3rpx 9rpx; border-radius: 4rpx; background: color-mix(in oklab, var(--theme-accent) 12%, var(--theme-surface)); color: var(--theme-accent); font-size: 18rpx; line-height: 1.4; white-space: nowrap; }
.safety-edit-note { color: var(--theme-text-secondary); font-size: 21rpx; }
.memory-row-actions { display: flex; align-items: center; gap: 12rpx; }
.memory-edit { color: var(--theme-primary); font-size: 21rpx; }
.memory-row-actions .del-btn { position: static; display: flex; width: 38rpx; height: 38rpx; align-items: center; justify-content: center; margin: 0; border: 1rpx solid var(--theme-border); border-radius: 50%; background: transparent; color: var(--theme-text-placeholder); font-size: 27rpx; line-height: 1; box-shadow: none; }

.section {
  margin-bottom: 64rpx;
}

.section-title {
  font-family: "Noto Serif SC", "Songti SC", "STSong", "KaiTi", serif;
  font-size: 40rpx;
  color: var(--theme-text-primary);
  font-weight: 700;
  margin-bottom: 12rpx;
  display: block;
}

.section-desc {
  font-size: 24rpx;
  color: var(--theme-text-secondary);
  margin-bottom: 32rpx;
  display: block;
}

.card {
  background-color: var(--theme-surface);
  border-radius: 20rpx;
  padding: 40rpx;
  box-shadow: var(--theme-shadow-panel);
  border: 1rpx solid var(--theme-border);
}

.empty-card {
  text-align: center;
  padding: 60rpx 40rpx;
}

.empty-text {
  color: var(--theme-text-secondary);
  font-size: 28rpx;
}

.loading-state {
  text-align: center;
  padding: 60rpx;
  color: var(--theme-text-secondary);
  font-size: 28rpx;
}

/* Report Styles */
.report-card {
  position: relative;
  overflow: hidden;
}

.report-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  border-bottom: 1rpx solid var(--theme-border);
  padding-bottom: 24rpx;
  margin-bottom: 32rpx;
}

.report-week {
  font-family: "Noto Serif SC", serif;
  font-size: 44rpx;
  font-weight: 700;
  color: var(--theme-primary);
}

.report-count {
  font-size: 26rpx;
  color: var(--theme-text-secondary);
}

.generate-box {
  background-color: color-mix(in oklab, var(--theme-primary) 4%, var(--theme-surface));
  border-radius: var(--theme-radius-sm);
  padding: 32rpx;
  margin-bottom: 32rpx;
  text-align: center;
}

.generate-desc {
  font-size: 26rpx;
  color: var(--theme-text-secondary);
  margin-bottom: 24rpx;
  display: block;
}

.generate-btn {
  background-color: var(--theme-primary);
  color: var(--theme-surface);
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 600;
  width: 80%;
}

.generate-btn::after {
  border: none;
}

.stats-board {
  margin-bottom: 40rpx;
}

.stat-item {
  display: flex;
  align-items: center;
  margin-bottom: 20rpx;
}

.stat-label {
  width: 120rpx;
  font-size: 26rpx;
  color: var(--theme-text-secondary);
}

.progress-bar {
  flex: 1;
  height: 12rpx;
  background-color: var(--theme-border);
  border-radius: 6rpx;
  margin: 0 24rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 6rpx;
}

.progress-fill.positive { background-color: var(--theme-accent); }

.progress-fill.energy {
  background-color: var(--theme-primary);
}

.stat-val {
  width: 80rpx;
  text-align: right;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.stat-quadrant {
  flex: 1;
  font-size: 28rpx;
  color: var(--theme-primary);
  font-weight: 600;
  text-align: right;
}

.text-block {
  margin-bottom: 32rpx;
}

.block-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: 16rpx;
  display: block;
}

.block-content {
  font-size: 28rpx;
  color: var(--theme-text-secondary);
  line-height: 1.8;
  text-align: justify;
}

.list-item {
  display: flex;
  margin-bottom: 12rpx;
  align-items: flex-start;
}

.bullet {
  color: var(--theme-primary);
  margin-right: 12rpx;
  font-size: 28rpx;
}

/* Memory Styles */
.memory-waterfall {
  column-count: 2;
  column-gap: 24rpx;
}

.memory-polaroid {
  break-inside: avoid;
  width: 100%;
  background-color: var(--theme-surface);
  padding: 32rpx 24rpx;
  border-radius: 8rpx;
  box-shadow: var(--theme-shadow-panel);
  margin-bottom: 24rpx;
  position: relative;
  box-sizing: border-box;
  transform: rotate(-2deg);
}

.memory-polaroid:nth-child(even) {
  transform: rotate(2deg);
  margin-top: 24rpx;
}

.pin {
  width: 20rpx;
  height: 20rpx;
  background-color: var(--theme-accent);
  border-radius: 50%;
  position: absolute;
  top: 12rpx;
  left: 50%;
  transform: translateX(-50%);
  box-shadow: var(--theme-shadow-panel);
}

.memory-key {
  font-size: 22rpx;
  color: var(--theme-text-secondary);
  display: block;
  text-align: center;
  margin-top: 12rpx;
  margin-bottom: 12rpx;
  text-transform: uppercase;
  letter-spacing: 2rpx;
}

.memory-divider {
  width: 40rpx;
  height: 2px;
  background-color: var(--theme-accent);
  margin: 0 auto 16rpx auto;
}

.memory-value {
  font-family: "Noto Serif SC", serif;
  font-size: 28rpx;
  color: var(--theme-text-primary);
  line-height: 1.6;
  text-align: justify;
  text-align-last: center;
  word-break: break-all;
  display: block;
}


/* Graph Styles */
.graph-container {
  background-color: var(--theme-surface);
  border-radius: 20rpx;
  padding: 40rpx;
  box-shadow: var(--theme-shadow-panel);
}

.graph-link {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32rpx;
}

.graph-link:last-child {
  margin-bottom: 0;
}

.graph-node {
  padding: 16rpx 24rpx;
  border-radius: 12rpx;
  font-size: 26rpx;
  font-weight: 600;
  max-width: 180rpx;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.graph-node text {
  font-size: 28rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.graph-node.subject {
  border: 1rpx solid var(--theme-primary);
  color: var(--theme-primary);
  background: color-mix(in oklab, var(--theme-primary) 5%, var(--theme-surface));
}

/* Edit Styles */
.item-actions {
  position: absolute;
  top: -20rpx;
  right: -16rpx;
  z-index: 2;
  background-color: var(--theme-surface);
  border-radius: 50%;
  width: 44rpx;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--theme-shadow-panel);
  border: 1px solid var(--theme-border);
}

.del-btn {
  font-size: 32rpx;
  color: var(--theme-text-placeholder);
  padding: 0;
  line-height: 1;
  margin-top: -4rpx;
}

.edit-form {
  margin-top: 24rpx;
}

.edit-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 32rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid var(--theme-border);
}

.edit-switch-row .edit-label {
  margin-bottom: 0;
}

.edit-label {
  font-size: 26rpx;
  color: var(--theme-text-secondary);
  margin-bottom: 12rpx;
  display: block;
}

.edit-input {
  background: var(--theme-bg);
  border-radius: 12rpx;
  padding: 20rpx;
  font-size: 28rpx;
  margin-bottom: 24rpx;
  border: 1rpx solid var(--theme-border);
}

.edit-textarea {
  background: var(--theme-bg);
  border-radius: 12rpx;
  padding: 20rpx;
  font-size: 28rpx;
  width: 100%;
  box-sizing: border-box;
  height: 280rpx;
  min-height: 0;
  max-height: 42vh;
  overflow-y: auto;
  border: 1rpx solid var(--theme-border);
  margin-bottom: 24rpx;
}

.graph-node.subject text {
  color: var(--theme-primary);
}

.graph-node.object {
  background-color: color-mix(in oklab, var(--theme-accent) 15%, var(--theme-surface));
  border: 1rpx solid var(--theme-accent);
}

.graph-node.object text {
  color: var(--theme-accent);
}

.graph-edge {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 0 16rpx;
  position: relative;
}

.edge-text {
  font-size: 22rpx;
  color: var(--theme-text-secondary);
  background-color: var(--theme-surface);
  padding: 0 8rpx;
  z-index: 2;
  margin-bottom: -10rpx;
}

.edge-line {
  width: 100%;
  height: 1px;
  border-bottom: 2rpx dotted var(--theme-border);
  z-index: 1;
}

/* Modal Styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: var(--theme-overlay);
  z-index: 1000;
  display: flex;
  justify-content: center;
  align-items: center;
}

.modal-content {
  width: 85%;
  max-height: calc(100vh - 96rpx);
  background-color: var(--theme-surface);
  border-radius: 24rpx;
  padding: 40rpx;
  box-shadow: var(--theme-shadow-dialog);
  box-sizing: border-box;
  overflow: hidden;
}

.modal-title {
  font-size: 34rpx;
  font-weight: bold;
  color: var(--theme-text-primary);
  margin-bottom: 24rpx;
  text-align: center;
  display: block;
}

.preview-list {
  max-height: 400rpx;
  margin-bottom: 32rpx;
}

.preview-item {
  display: flex;
  flex-direction: column;
  margin-bottom: 16rpx;
  padding: 16rpx;
  background-color: var(--theme-surface-hover);
  border-radius: var(--theme-radius-sm);
}

.preview-key {
  font-size: 24rpx;
  color: var(--theme-primary);
  margin-bottom: 8rpx;
}

.preview-value {
  font-size: 28rpx;
  color: var(--theme-text-secondary);
}

.modal-actions {
  display: flex;
  justify-content: space-between;
  gap: 24rpx;
  margin-top: 32rpx;
}

.cancel-btn {
  flex: 1;
  background-color: var(--theme-surface-hover);
  color: var(--theme-text-secondary);
  font-size: 30rpx;
  border-radius: 40rpx;
  margin: 0;
}
.cancel-btn::after { border: none; }

.confirm-btn {
  flex: 1;
  background-color: var(--theme-primary);
  color: var(--theme-text-on-primary);
  font-size: 30rpx;
  border-radius: 40rpx;
  margin: 0;
}
.confirm-btn::after { border: none; }

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.section-title-row .section-title {
  min-width: 0;
  margin-bottom: 0;
}

.section-action {
  flex: 0 0 auto;
  min-width: 136rpx;
  height: 56rpx;
  margin: 0;
  padding: 0 16rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: 6rpx;
  background: transparent;
  box-sizing: border-box;
  color: var(--theme-primary);
  font-size: 22rpx;
  font-weight: 600;
  line-height: 54rpx;
}

.section-action::after { border: none; }
.section-action:active { background: var(--theme-surface-hover); }

/* Keep memory rows compact even though legacy card styles above share class names. */
.memory-list { display: flex; flex-direction: column; gap: 12rpx; }
.memory-row { display: flex; min-height: 114rpx; align-items: center; gap: 18rpx; padding: 21rpx 20rpx; border: 1rpx solid var(--theme-border); border-radius: 7rpx; background: var(--theme-surface); box-sizing: border-box; }
.memory-row:active { background: var(--theme-surface-hover); }
.memory-row-main { min-width: 0; flex: 1; }
.memory-row-head { display: flex; align-items: center; gap: 9rpx; }
.memory-key { overflow: hidden; color: var(--theme-text-primary); font-size: 25rpx; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.memory-value { display: -webkit-box; overflow: hidden; margin-top: 9rpx; color: var(--theme-text-secondary); font-size: 22rpx; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.core-badge { padding: 3rpx 9rpx; border-radius: 4rpx; background: color-mix(in oklab, var(--theme-primary) 10%, var(--theme-surface)); color: var(--theme-primary); font-size: 18rpx; line-height: 1.4; white-space: nowrap; }
.memory-row-actions { display: flex; align-items: center; gap: 12rpx; }
.memory-edit { color: var(--theme-primary); font-size: 21rpx; }
.memory-row-actions .del-btn { position: static; display: flex; width: 38rpx; height: 38rpx; align-items: center; justify-content: center; margin: 0; border: 1rpx solid var(--theme-border); border-radius: 50%; background: transparent; color: var(--theme-text-placeholder); font-size: 27rpx; line-height: 1; box-shadow: none; }
</style>

<style scoped>
.memory-overview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 8rpx;
  color: var(--theme-text-placeholder);
  font-size: 19rpx;
  line-height: 1.5;
}

.memory-groups {
  border-top-color: var(--theme-divider);
}

.memory-group {
  border-bottom-color: var(--theme-divider);
}

.memory-group-header {
  border-bottom: 1rpx solid var(--theme-divider);
}

.memory-group-header:active,
.candidate-group-header:active {
  background: color-mix(in oklab, var(--theme-primary) 5%, var(--theme-surface));
}

.memory-row-actions .memory-edit {
  color: var(--theme-text-secondary);
  font-weight: 500;
}

.memory-row-actions .memory-edit:first-child {
  color: var(--theme-primary);
  font-weight: 650;
}

.candidate-group {
  border-top-color: var(--theme-divider);
}

.memory-groups {
  border-top: 1rpx solid var(--theme-divider);
}

.memory-group {
  border-bottom: 0;
}

.memory-group + .memory-group {
  margin-top: 14rpx;
}

.memory-group-expanded .memory-group-header {
  border-bottom: 0;
}

.memory-group-header,
.candidate-group-header {
  display: flex;
  min-height: 82rpx;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 16rpx 0;
  box-sizing: border-box;
}

.memory-group-heading,
.candidate-group-heading {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 12rpx;
}

.memory-group-title,
.candidate-group-title {
  overflow: hidden;
  color: var(--theme-text-primary);
  font-size: 25rpx;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.memory-group-count,
.candidate-group-count {
  flex-shrink: 0;
  color: var(--theme-text-placeholder);
  font-size: 19rpx;
}

.memory-group-toggle,
.candidate-group-toggle {
  flex-shrink: 0;
  color: var(--theme-primary);
  font-size: 20rpx;
}

.memory-group-header:active,
.candidate-group-header:active {
  background: var(--theme-surface-hover);
}

.memory-group .memory-list {
  border-top: 0;
}

.candidate-group-header {
  min-height: 68rpx;
  padding: 10rpx 0;
}

.candidate-group-title {
  font-size: 23rpx;
}

.candidate-group-count {
  font-size: 18rpx;
}

.graph-toolbar {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 14rpx;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid var(--theme-border);
}

.graph-search {
  min-width: 0;
  flex: 1;
  height: 62rpx;
  padding: 0 18rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
  background: var(--theme-bg);
  color: var(--theme-text-primary);
  font-size: 23rpx;
  box-sizing: border-box;
}

.graph-search-clear {
  flex-shrink: 0;
  color: var(--theme-primary);
  font-size: 21rpx;
}

.graph-summary {
  display: block;
  margin-bottom: 10rpx;
  color: var(--theme-text-placeholder);
  font-size: 19rpx;
}

.graph-expand-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  margin-top: 8rpx;
  padding: 18rpx 0 4rpx;
  border-top: 1rpx solid var(--theme-border);
  color: var(--theme-primary);
  font-size: 21rpx;
}

.graph-filter-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18rpx;
  padding: 40rpx 0 24rpx;
  color: var(--theme-text-secondary);
  font-size: 22rpx;
}

.graph-filter-empty text:last-child {
  color: var(--theme-primary);
}
</style>

<style scoped>
/* Editorial memory center layer. The page keeps the existing content and actions,
   but gives conclusions, evidence, and controls distinct visual weight. */
.analysis-page {
  padding: var(--theme-page-padding);
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
}

.header {
  margin-top: 22rpx;
  margin-bottom: 48rpx;
}

.title {
  font-size: 54rpx;
  letter-spacing: 0;
  line-height: 1.15;
}

.subtitle {
  max-width: 600rpx;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.65;
}

.insight-entry-switch {
  gap: 12rpx;
  margin-top: 28rpx;
}

.insight-entry {
  min-height: 104rpx;
  padding: 20rpx;
  border-radius: var(--theme-radius-md);
  background: var(--theme-surface);
  font-size: 26rpx;
}

.insight-entry.active {
  border-color: var(--theme-primary);
  background: color-mix(in oklab, var(--theme-primary) 7%, var(--theme-surface));
}

.entry-kicker {
  margin-bottom: 8rpx;
  font-size: 18rpx;
  letter-spacing: 1rpx;
}

.section {
  margin-bottom: var(--theme-section-gap);
}

.section-title {
  margin-bottom: 10rpx;
  font-size: 36rpx;
  line-height: 1.25;
}

.section-desc {
  margin-bottom: 22rpx;
  font-size: 23rpx;
  line-height: 1.6;
}

.section-title-row {
  align-items: baseline;
  padding-bottom: 14rpx;
  border-bottom: 1rpx solid var(--theme-border);
}

.section-action {
  min-width: 128rpx;
  height: 52rpx;
  padding: 0 14rpx;
  border-color: var(--theme-border);
  border-radius: var(--theme-radius-sm);
  background: transparent;
  color: var(--theme-primary);
  font-size: 21rpx;
  line-height: 50rpx;
}

.section-action:active {
  background: var(--theme-surface-hover);
}

.card,
.graph-container {
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-md);
  background: var(--theme-surface);
  box-shadow: var(--theme-shadow-panel);
}

.card {
  padding: 28rpx;
}

.empty-card {
  padding: 46rpx 28rpx;
  box-shadow: none;
}

.report-header {
  padding-bottom: 18rpx;
  margin-bottom: 24rpx;
  border-bottom: 1rpx solid var(--theme-border);
}

.report-week {
  font-size: 36rpx;
  color: var(--theme-text-primary);
}

.report-count,
.stat-label,
.empty-text {
  font-size: 23rpx;
}

.generate-box {
  padding: 24rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
  background: color-mix(in oklab, var(--theme-primary) 4%, var(--theme-surface));
}

.generate-btn,
.confirm-btn {
  border-radius: var(--theme-radius-sm);
  background: var(--theme-primary);
  color: var(--theme-text-on-primary);
}

.stats-board {
  padding: 18rpx 0;
  margin-bottom: 24rpx;
  border-bottom: 1rpx solid var(--theme-border);
}

.progress-bar {
  height: 8rpx;
  border-radius: var(--theme-radius-sm);
  background: var(--theme-border);
}

.progress-fill {
  border-radius: var(--theme-radius-sm);
  background: var(--theme-primary);
}

.progress-fill.positive {
  background: var(--theme-accent);
}

.block-title {
  margin-bottom: 10rpx;
  font-size: 27rpx;
}

.block-content {
  font-size: 25rpx;
  line-height: 1.75;
}

.memory-list {
  gap: 0;
  border-top: 1rpx solid var(--theme-border);
}

.memory-row {
  min-height: 0;
  align-items: flex-start;
  gap: 14rpx;
  padding: 22rpx 0;
  border: 0;
  border-bottom: 1rpx solid var(--theme-border);
  border-radius: 0;
  background: transparent;
}

.memory-row:active {
  background: var(--theme-surface-hover);
}

.memory-row-head {
  gap: 8rpx;
}

.memory-key {
  font-size: 24rpx;
}

.memory-value {
  margin-top: 7rpx;
  color: var(--theme-text-primary);
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 25rpx;
  line-height: 1.6;
}

.memory-source-row {
  gap: 8rpx;
  margin-top: 10rpx;
  font-size: 19rpx;
}

.memory-source-preview,
.candidate-source-preview {
  width: 100%;
  margin-top: 4rpx;
  font-size: 19rpx;
  color: var(--theme-text-placeholder);
}

.memory-updated {
  margin-top: 8rpx;
  font-size: 18rpx;
}

.memory-row-actions {
  flex-shrink: 0;
  gap: 10rpx;
  padding-top: 2rpx;
}

.memory-edit {
  font-size: 20rpx;
}

.memory-row-actions .del-btn {
  width: 42rpx;
  height: 42rpx;
  border-radius: var(--theme-radius-sm);
  color: var(--theme-text-placeholder);
}

.core-badge,
.safety-badge {
  border-radius: var(--theme-radius-sm);
}

.safety-badge {
  background: color-mix(in oklab, var(--theme-accent) 12%, var(--theme-surface));
  color: var(--theme-accent);
}

.candidate-section {
  margin-top: 34rpx;
  padding-top: 28rpx;
  border-top: 2rpx solid var(--theme-primary);
}

.candidate-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.candidate-heading-main {
  min-width: 0;
}

.candidate-title {
  display: block;
  color: var(--theme-text-primary);
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1.3;
}

.candidate-desc {
  display: block;
  margin-top: 8rpx;
  color: var(--theme-text-secondary);
  font-size: 21rpx;
  line-height: 1.55;
}

.candidate-count {
  flex-shrink: 0;
  padding: 6rpx 12rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
  color: var(--theme-primary);
  font-size: 20rpx;
  font-weight: 650;
}

.candidate-summary {
  display: block;
  margin-top: 18rpx;
  color: var(--theme-text-placeholder);
  font-size: 19rpx;
}

.candidate-group {
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--theme-divider);
}

.candidate-group + .candidate-group {
  margin-top: 20rpx;
}

.candidate-conflict-note {
  margin: 0 0 10rpx;
  color: var(--theme-accent);
  font-size: 19rpx;
  line-height: 1.5;
}

.candidate-row {
  display: flex;
  align-items: flex-end;
  gap: 18rpx;
  padding: 18rpx 0;
  border-bottom: 1rpx solid var(--theme-divider);
}

.candidate-copy {
  min-width: 0;
  flex: 1;
}

.candidate-key {
  display: block;
  color: var(--theme-text-secondary);
  font-size: 20rpx;
  font-weight: 650;
}

.candidate-value {
  display: block;
  margin-top: 7rpx;
  color: var(--theme-text-primary);
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 25rpx;
  line-height: 1.55;
}

.candidate-evidence {
  display: block;
  margin-top: 8rpx;
  color: var(--theme-text-placeholder);
  font-size: 19rpx;
  line-height: 1.45;
}

.candidate-source-link {
  display: block;
  margin-top: 7rpx;
  color: var(--theme-primary);
  font-size: 20rpx;
}

.candidate-actions {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: 10rpx;
  min-width: 86rpx;
}

.candidate-approve,
.candidate-reject {
  display: block;
  padding: 8rpx 12rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
  font-size: 20rpx;
  line-height: 1.3;
  text-align: center;
}

.candidate-approve {
  border-color: var(--theme-primary);
  background: var(--theme-primary);
  color: var(--theme-text-on-primary);
}

.candidate-reject {
  color: var(--theme-text-secondary);
}

.candidate-approve:active,
.candidate-reject:active {
  opacity: .72;
}

.graph-container {
  padding: 26rpx 22rpx;
}

.graph-node {
  padding: 12rpx 16rpx;
  border-radius: var(--theme-radius-sm);
  font-size: 23rpx;
}

.graph-node.subject {
  border-color: var(--theme-primary);
  background: color-mix(in oklab, var(--theme-primary) 7%, var(--theme-surface));
}

.graph-node.object {
  border-color: var(--theme-border);
  background: var(--theme-surface-hover);
}

.graph-node.object text {
  color: var(--theme-text-primary);
}

.edge-line {
  border-bottom-color: var(--theme-border);
}

.modal-content {
  border-radius: var(--theme-radius-lg);
  box-shadow: var(--theme-shadow-dialog);
}

.preview-item {
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
  background: var(--theme-surface-hover);
}

.cancel-btn {
  border-radius: var(--theme-radius-sm);
  background: var(--theme-surface-hover);
  color: var(--theme-text-secondary);
}

@media (max-width: 360px) {
  .analysis-page { padding-right: 28rpx; padding-left: 28rpx; }
  .candidate-row { align-items: stretch; flex-direction: column; gap: 14rpx; }
  .candidate-actions { flex-direction: row; }
  .candidate-approve, .candidate-reject { flex: 1; }
}
</style>

<style scoped>
.memory-overview {
  margin: 2rpx 0 10rpx;
  padding: 0 0 16rpx;
  border-bottom: 0;
}

.memory-group-header {
  min-height: 94rpx;
  padding: 22rpx 0;
}

.memory-group-title {
  font-size: 27rpx;
}

.memory-group-count {
  font-size: 20rpx;
}

.memory-group-toggle,
.candidate-group-toggle {
  min-width: 76rpx;
  text-align: right;
}

.memory-row {
  padding: 24rpx 0 28rpx;
  border-bottom-color: var(--theme-divider);
}

.memory-list {
  border-top: 0;
}

.memory-row-head {
  margin-bottom: 8rpx;
}

.memory-value {
  margin-top: 0;
  font-size: 27rpx;
  line-height: 1.72;
}

.memory-list .memory-source-row {
  display: none;
}

.memory-updated {
  margin-top: 13rpx;
  font-size: 18rpx;
}

.memory-row-actions {
  gap: 8rpx;
}

.memory-row-actions .memory-edit {
  padding: 6rpx 4rpx;
}

.candidate-section {
  margin-top: 46rpx;
  padding-top: 34rpx;
}

.candidate-group-header {
  min-height: 76rpx;
  padding: 14rpx 0;
}

.candidate-group-title {
  font-size: 25rpx;
}

.candidate-value {
  font-size: 27rpx;
  line-height: 1.65;
}

.candidate-evidence {
  margin-top: 10rpx;
}
</style>
