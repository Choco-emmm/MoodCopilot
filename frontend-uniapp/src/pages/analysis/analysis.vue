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
      <text class="section-desc">AI 悄悄为你记录下的点点滴滴。</text>
      
      <view v-if="loadingMemory" class="loading-state">
        <text>正在提取记忆...</text>
      </view>
      
      <view v-else-if="memories.length > 0">
        <view class="memory-list">
          <view v-for="m in memories" :key="m.id" class="memory-row" @click="openEditMemory(m)">
            <view class="memory-row-main">
              <view class="memory-row-head">
                <text class="memory-key">{{ m.attributeKey }}</text>
                <text v-if="m.isCore" class="core-badge">核心</text>
              </view>
              <text class="memory-value">{{ memoryPreview(m.attributeValue) }}</text>
            </view>
            <view class="memory-row-actions">
              <text class="memory-edit">编辑</text>
              <text class="del-btn" @click.stop="deleteMemory(m.id)">×</text>
            </view>
          </view>
        </view>
      </view>
      
      <view v-else class="card empty-card">
        <text class="empty-text">AI 正在努力了解你，多写点日记给它线索吧。</text>
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
            <switch :checked="editMemoryForm.isCore" @change="handleCoreMemoryChange" color="#d4a373" style="transform: scale(0.8);" />
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
            <text class="preview-key">{{ t.headEntity }} -- {{ t.relation }}</text>
            <text class="preview-value">{{ t.tailEntity }}</text>
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

import { ref, onMounted } from 'vue';
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app';
import { get, post, put, del } from '@/utils/request';
import { parseMarkdown } from '@/utils/markdown';
import { hasLoginToken, requireLogin } from '@/stores/login';

const loadingReport = ref(false);
const weeklyReport = ref<any>(null);
const isGenerating = ref(false);
const showLegacyReports = false;

const loadingMemory = ref(false);
const memories = ref<any[]>([]);

const loadingGraph = ref(false);
const triples = ref<any[]>([]);

const loadingSummaries = ref(false);
const monthlySummaries = ref<any[]>([]);

const isConsolidating = ref(false);
const showConsolidateModal = ref(false);
const previewMemories = ref<any[]>([]);

const isGraphConsolidating = ref(false);
const isGraphApplying = ref(false);
const showGraphConsolidateModal = ref(false);
const previewGraphTriples = ref<any[]>([]);
const isLoggedIn = ref(hasLoginToken());

const loadAllData = async () => {
  fetchMemory();
  fetchGraph();
};

onMounted(() => {
  if (isLoggedIn.value) loadAllData();
  uni.$on('login-success', loadAfterLogin);
});

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
  border-color: rgba(var(--theme-primary-rgb), .32);
  background: rgba(var(--theme-primary-rgb), .055);
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
.memory-row:active { background: rgba(var(--theme-primary-rgb), .045); }
.memory-row-main { min-width: 0; flex: 1; }
.memory-row-head { display: flex; align-items: center; gap: 9rpx; }
.memory-key { overflow: hidden; color: var(--theme-text-primary); font-size: 25rpx; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.memory-value { display: -webkit-box; overflow: hidden; margin-top: 9rpx; color: var(--theme-text-secondary); font-size: 22rpx; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.core-badge { padding: 3rpx 9rpx; border-radius: 4rpx; background: rgba(var(--theme-primary-rgb), .1); color: var(--theme-primary); font-size: 18rpx; line-height: 1.4; white-space: nowrap; }
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
  column-count: 2;
  column-gap: 24rpx;
}

.memory-polaroid {
  break-inside: avoid;
  width: 100%;
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
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.15);
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
  border: 1rpx solid rgba(var(--theme-primary-rgb), .3);
  border-radius: 6rpx;
  background: rgba(var(--theme-primary-rgb), .06);
  box-sizing: border-box;
  color: var(--theme-primary);
  font-size: 22rpx;
  font-weight: 600;
  line-height: 54rpx;
}

.section-action::after { border: none; }
.section-action:active { background: rgba(var(--theme-primary-rgb), .13); }

/* Keep memory rows compact even though legacy card styles above share class names. */
.memory-list { display: flex; flex-direction: column; gap: 12rpx; }
.memory-row { display: flex; min-height: 114rpx; align-items: center; gap: 18rpx; padding: 21rpx 20rpx; border: 1rpx solid var(--theme-border); border-radius: 7rpx; background: var(--theme-surface); box-sizing: border-box; }
.memory-row:active { background: rgba(var(--theme-primary-rgb), .045); }
.memory-row-main { min-width: 0; flex: 1; }
.memory-row-head { display: flex; align-items: center; gap: 9rpx; }
.memory-key { overflow: hidden; color: var(--theme-text-primary); font-size: 25rpx; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.memory-value { display: -webkit-box; overflow: hidden; margin-top: 9rpx; color: var(--theme-text-secondary); font-size: 22rpx; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.core-badge { padding: 3rpx 9rpx; border-radius: 4rpx; background: rgba(var(--theme-primary-rgb), .1); color: var(--theme-primary); font-size: 18rpx; line-height: 1.4; white-space: nowrap; }
.memory-row-actions { display: flex; align-items: center; gap: 12rpx; }
.memory-edit { color: var(--theme-primary); font-size: 21rpx; }
.memory-row-actions .del-btn { position: static; display: flex; width: 38rpx; height: 38rpx; align-items: center; justify-content: center; margin: 0; border: 1rpx solid var(--theme-border); border-radius: 50%; background: transparent; color: var(--theme-text-placeholder); font-size: 27rpx; line-height: 1; box-shadow: none; }
</style>
