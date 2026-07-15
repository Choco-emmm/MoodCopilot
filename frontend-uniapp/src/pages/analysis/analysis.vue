<template>
  <scroll-view scroll-y class="analysis-page" :style="localThemeStyle">
    <GlobalUI :tabIndex="1" />
    <view class="header">
      <text class="title">洞察 Insights</text>
      <text class="subtitle">✨ AI 为你整理记忆，发现那些被遗忘的情绪角落。</text>
    </view>

    <!-- 情绪周报 -->
    <view class="section">
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
    <view class="section">
      <text class="section-title">往期月度总结</text>
      
      <view v-if="loadingSummaries" class="loading-state">
        <text>正在查阅历史...</text>
      </view>
      
      <view v-else-if="monthlySummaries.length > 0" class="summary-list">
        <view class="card summary-card" v-for="summary in monthlySummaries" :key="summary.id">
          <text class="summary-title">{{ summary.title }}</text>
          <text class="summary-date">{{ summary.startDate }} 至 {{ summary.endDate }}</text>
          <text class="block-content">{{ summary.aiSummary }}</text>
        </view>
      </view>
      
      <view v-else class="card empty-card">
        <text class="empty-text">暂时没有月度总结，坚持记录日记，系统会按月自动生成。</text>
      </view>
    </view>

    <!-- 个人记忆 -->
    <view class="section">
      <text class="section-title">你的专属记忆</text>
      <text class="section-desc">AI 悄悄为你记录下的点点滴滴。</text>
      
      <view v-if="loadingMemory" class="loading-state">
        <text>正在提取记忆...</text>
      </view>
      
      <view v-else-if="memories.length > 0">
        <view class="memory-waterfall">
          <view v-for="m in memories" :key="m.id" class="memory-polaroid" @click="openEditMemory(m)">
            <view class="pin"></view>
            <view class="item-actions">
              <text class="del-btn" @click.stop="deleteMemory(m.id)">×</text>
            </view>
            <text class="memory-key">{{ m.attributeKey }}</text>
            <view class="memory-divider"></view>
            <text class="memory-value">{{ m.attributeValue }}</text>
            <text v-if="m.isCore" class="core-badge">★ 核心记忆</text>
          </view>
        </view>
        <button class="consolidate-btn" @click="previewConsolidate" :loading="isConsolidating">整理与巩固记忆</button>
      </view>
      
      <view v-else class="card empty-card">
        <text class="empty-text">AI 正在努力了解你，多写点日记给它线索吧。</text>
      </view>
    </view>

    <!-- 关系图谱 -->
    <view class="section">
      <text class="section-title">羁绊图谱</text>
      <text class="section-desc">在你的世界里，谁是常客？</text>
      
      <view v-if="loadingGraph" class="loading-state">
        <text>正在连接图谱...</text>
      </view>
      
      <view v-else-if="triples.length > 0" class="graph-container">
        <view v-for="t in triples" :key="t.id" class="graph-link" @click="openEditGraph(t)">
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
          <textarea class="edit-textarea" v-model="editMemoryForm.attributeValue" auto-height></textarea>
          
          <view class="edit-switch-row">
            <text class="edit-label">设为核心记忆</text>
            <switch :checked="editMemoryForm.isCore" @change="editMemoryForm.isCore = $event.detail.value" color="#d4a373" style="transform: scale(0.8);" />
          </view>
        </view>
        <view class="modal-actions">
          <button class="cancel-btn" @click="showEditMemoryModal = false">取消</button>
          <button class="confirm-btn" @click="saveMemory">保存</button>
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
import { themeStyle, syncNavigationBarColor } from '@/stores/theme';
import { ref, onMounted } from 'vue';
import { onLoad, onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import { get, post, put, del } from '@/utils/request';
import { parseMarkdown } from '@/utils/markdown';

const localThemeStyle = ref(themeStyle.value);

onShow(() => {
  localThemeStyle.value = themeStyle.value;
  syncNavigationBarColor();
});

uni.$on('themeChanged', () => {
  localThemeStyle.value = themeStyle.value;
});

const loadingReport = ref(false);
const weeklyReport = ref<any>(null);
const isGenerating = ref(false);

const loadingMemory = ref(false);
const memories = ref<any[]>([]);

const loadingGraph = ref(false);
const triples = ref<any[]>([]);

const loadingSummaries = ref(false);
const monthlySummaries = ref<any[]>([]);

const isConsolidating = ref(false);
const showConsolidateModal = ref(false);
const previewMemories = ref<any[]>([]);

const loadAllData = async () => {
  fetchReport();
  fetchMemory();
  fetchGraph();
  fetchSummaries();
};

onMounted(() => {
  loadAllData();
});

onPullDownRefresh(async () => {
  await Promise.all([
    fetchReport(),
    fetchMemory(),
    fetchGraph(),
    fetchSummaries()
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
  return get('/api/memory')
    .then((res: any) => {
      loadingMemory.value = false;
      if (res.code === 200) {
        memories.value = res.data || [];
      }
    })
    .catch(() => loadingMemory.value = false);
};

const fetchGraph = () => {
  loadingGraph.value = true;
  return get('/api/graph/triples')
    .then((res: any) => {
      loadingGraph.value = false;
      if (res.code === 200) {
        triples.value = res.data || [];
      }
    })
    .catch(() => loadingGraph.value = false);
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

const showEditMemoryModal = ref(false);
const editMemoryForm = ref<any>({});

const openEditMemory = (m: any) => {
  editMemoryForm.value = { ...m };
  showEditMemoryModal.value = true;
};

const saveMemory = async () => {
  try {
    const res = await put(`/api/memory/${editMemoryForm.value.id}`, {
      attributeValue: editMemoryForm.value.attributeValue,
      isCore: editMemoryForm.value.isCore
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
  box-shadow: 0 8rpx 32rpx rgba(0,0,0,0.1);
  border: 1px solid rgba(var(--theme-primary-rgb), 0.1);
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
  border-bottom: 2px solid rgba(var(--theme-primary-rgb), 0.1);
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
  background-color: rgba(var(--theme-primary-rgb), 0.04);
  border-radius: 16rpx;
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

.progress-fill.positive {
  background-color: #d4a373;
}

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
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.memory-polaroid {
  width: 48%;
  background-color: var(--theme-surface);
  padding: 32rpx 24rpx;
  border-radius: 8rpx;
  box-shadow: 0 8rpx 20rpx rgba(32, 32, 29, 0.06);
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
  background-color: #e55353;
  border-radius: 50%;
  position: absolute;
  top: 12rpx;
  left: 50%;
  transform: translateX(-50%);
  box-shadow: inset -2rpx -2rpx 4rpx rgba(0,0,0,0.2), 2rpx 2rpx 4rpx rgba(0,0,0,0.1);
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
  background-color: #d4a373;
  margin: 0 auto 16rpx auto;
}

.memory-value {
  font-family: "Noto Serif SC", serif;
  font-size: 28rpx;
  color: var(--theme-text-primary);
  line-height: 1.6;
  text-align: center;
  display: block;
}

.core-badge {
  position: absolute;
  bottom: -20rpx;
  left: 50%;
  transform: translateX(-50%);
  background-color: #f9ca24;
  color: #fff;
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  border-radius: 20rpx;
  white-space: nowrap;
  box-shadow: 0 4rpx 10rpx rgba(249, 202, 36, 0.3);
}

/* Graph Styles */
.graph-container {
  background-color: var(--theme-surface);
  border-radius: 20rpx;
  padding: 40rpx;
  box-shadow: 0 8rpx 32rpx rgba(0,0,0,0.1);
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
  border: 1px solid rgba(var(--theme-primary-rgb), 0.3);
  color: var(--theme-primary);
  background: rgba(var(--theme-primary-rgb), 0.05);
}

/* Edit Styles */
.item-actions {
  position: absolute;
  top: 10rpx;
  right: 16rpx;
  z-index: 2;
}

.del-btn {
  font-size: 36rpx;
  color: #c4c0b8;
  padding: 10rpx;
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
  border-top: 1px solid rgba(0,0,0,0.05);
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
  background: #f8f8f7;
  border-radius: 12rpx;
  padding: 20rpx;
  font-size: 28rpx;
  margin-bottom: 24rpx;
  border: 1px solid #e0ddd6;
}

.edit-textarea {
  background: #f8f8f7;
  border-radius: 12rpx;
  padding: 20rpx;
  font-size: 28rpx;
  width: 100%;
  box-sizing: border-box;
  min-height: 120rpx;
  border: 1px solid #e0ddd6;
  margin-bottom: 24rpx;
}

.graph-node.subject text {
  color: var(--theme-primary);
}

.graph-node.object {
  background-color: rgba(212, 163, 115, 0.15);
  border: 1px solid rgba(212, 163, 115, 0.3);
}

.graph-node.object text {
  color: #a86c32;
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
  border-bottom: 2px dotted #ccc;
  z-index: 1;
}

/* Modal Styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.4);
  z-index: 1000;
  display: flex;
  justify-content: center;
  align-items: center;
}

.modal-content {
  width: 85%;
  background-color: var(--theme-surface);
  border-radius: 24rpx;
  padding: 40rpx;
  box-shadow: 0 16rpx 40rpx rgba(0, 0, 0, 0.1);
  box-sizing: border-box;
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
  background-color: rgba(0,0,0,0.02);
  border-radius: 12rpx;
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
  background-color: #f0f0f0;
  color: var(--theme-text-secondary);
  font-size: 30rpx;
  border-radius: 40rpx;
  margin: 0;
}
.cancel-btn::after { border: none; }

.confirm-btn {
  flex: 1;
  background-color: var(--theme-primary);
  color: #fff;
  font-size: 30rpx;
  border-radius: 40rpx;
  margin: 0;
}
.confirm-btn::after { border: none; }
</style>

