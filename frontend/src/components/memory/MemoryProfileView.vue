<template>
  <div ref="rootElement" class="tab-content">
    <div class="section-head">
      <h2 class="memory-section-title sr-only">记忆管理</h2>
      <div class="memory-toolbar">
      <n-button size="small" secondary type="primary" :loading="store.consolidatingMemory" :disabled="editingMemoryId !== null || savingMemoryId !== null" @click="store.consolidateMemories()">
        智能整理记忆
      </n-button>
      <n-button size="small" secondary :loading="memoriesLoading" :disabled="savingMemoryId !== null || editingMemoryId !== null" @click="loadMemories">
         刷新
      </n-button>
      </div>
    </div>
    <p class="memory-overview" role="status">{{ formalLoaded ? `${store.memories.length} 条正式记忆` : '正式记忆数量待加载' }} · {{ candidatesLoaded ? `${candidates.length} 条待确认` : '待确认数量待加载' }}</p>
    <p v-if="memoryError && !detailsOpen" class="memory-action-error" role="alert">{{ memoryError }}</p>
    <div v-if="formalLoadError" class="memory-detail-error" role="alert">{{ formalLoadError }} <n-button secondary :loading="memoriesLoading" :disabled="editingMemoryId !== null || savingMemoryId !== null" @click="loadMemories">重试正式记忆</n-button></div>
    <section class="candidate-section" aria-label="待确认记忆">
      <button type="button" class="candidate-toggle" :aria-expanded="candidatesExpanded" aria-controls="candidate-queue" @click="candidatesExpanded = !candidatesExpanded">
        <span class="candidate-toggle-copy">
          <span>待确认的记忆 · {{ candidatesLoaded ? `${candidates.length} 条` : '待加载' }}</span>
          <small v-if="candidatesLoaded">{{ candidateGroups.length }} 个属性<template v-if="candidateConflictGroupCount"> · {{ candidateConflictGroupCount }} 个属性有不同候选</template></small>
        </span>
        <span>{{ candidatesExpanded ? '收起' : '展开核对' }}</span>
      </button>
      <div v-if="candidatesError" class="candidate-load-error memory-detail-error" role="alert">{{ candidatesError }} <n-button secondary :loading="candidatesLoading" @click="loadCandidates">重试候选</n-button></div>
      <p v-if="candidatesLoading && !candidatesLoaded" role="status">正在加载候选…</p>
      <div v-if="candidatesExpanded" id="candidate-queue">
      <p v-if="candidatesLoaded && !candidates.length" class="memory-desc">暂无待确认的记忆。</p>
      <p class="candidate-queue-note">候选尚未成为正式记忆。已有记忆的改写需要你确认；请核对原始依据后再决定。</p>
      <div v-for="(group, groupIndex) in candidateGroups" :key="group.key" class="candidate-group">
      <h3><button type="button" class="group-toggle" :aria-expanded="isCandidateGroupExpanded(group.key, groupIndex)" :aria-controls="`candidate-group-${groupIndex}`" @click="toggleCandidateGroup(group.key, groupIndex)"><span class="group-title-row"><span class="group-title">{{ group.items[0].attributeKey }}</span><small class="group-count">{{ group.items.length }} 条</small></span><span>{{ isCandidateGroupExpanded(group.key, groupIndex) ? '收起' : '展开' }}</span></button></h3>
      <p v-if="!isCandidateGroupExpanded(group.key, groupIndex)" class="group-summary">{{ group.items[0].attributeValue }}</p>
      <div v-if="isCandidateGroupExpanded(group.key, groupIndex)" :id="`candidate-group-${groupIndex}`">
      <div v-if="group.hasConflict" class="candidate-conflict-note">同一属性存在不同候选，请分别确认；系统不会替你合并冲突内容。</div>
      <div v-for="candidate in groupItems(group.items, 'candidate', group.key)" :key="candidate.id" class="candidate-item">
        <div class="candidate-copy">
          <MemoryPreview :value="candidate.attributeValue" :status="isSafetyState(candidate) ? '近期状态' : undefined" />
        </div>
        <div class="candidate-actions">
          <button type="button" class="memory-evidence-open" aria-haspopup="dialog" :data-memory-id="candidate.id" data-memory-kind="candidate" :disabled="detailsBusy" @click="toggleCandidateDetails(candidate.id, $event)">查看全文与依据 <span aria-hidden="true">→</span></button>
        </div>

      </div>
      <MemoryPagination :total="group.items.length" :page="groupPage(group.items, 'candidate', group.key)" :label="`${group.items[0].attributeKey}候选分页`" :disabled="detailsBusy" @update:page="setGroupPage('candidate', group.key, $event)" />
      </div>
      </div>
      </div>
    </section>
    <h2 class="formal-heading" tabindex="-1">已记住的内容 <small>{{ formalGroups.length }} 个属性</small></h2>
    <div v-if="memoriesLoading && !formalLoaded" class="memory-loading" style="text-align: center; padding: 40px 0;">
      <n-spin size="small" />
    </div>
    <div v-else-if="formalLoaded && store.memories.length === 0" class="memory-empty" style="text-align: center; padding: 40px 0; color: var(--color-text-light);">
      MoodCopilot 正在默默观察你，多写点日记或和 MoodCopilot 聊天吧。
    </div>
    <div v-else class="memory-list">
      <section v-for="(group, groupIndex) in formalGroups" :key="group.key" class="formal-group">
      <h3><button type="button" class="group-toggle" :aria-expanded="isFormalGroupExpanded(group.key, groupIndex)" :aria-controls="`formal-group-${groupIndex}`" :disabled="editingMemoryId !== null || savingMemoryId !== null" @click="toggleFormalGroup(group.key, groupIndex)"><span class="group-title-row"><span class="group-title">{{ group.key }}</span><small class="group-count">{{ group.items.length }} 条</small></span><span>{{ isFormalGroupExpanded(group.key, groupIndex) ? '收起' : '展开' }}</span></button></h3>
      <p v-if="!isFormalGroupExpanded(group.key, groupIndex)" class="group-summary">{{ group.items[0].attributeValue }}</p>
      <div v-if="isFormalGroupExpanded(group.key, groupIndex)" :id="`formal-group-${groupIndex}`">
      <article v-for="m in groupItems(group.items, 'formal', group.key)" :key="m.id" class="memory-item">
        <div class="memory-content">
          <MemoryPreview :value="m.attributeValue" :status="isSafetyState(m) ? '近期状态' : m.isCore ? '核心' : undefined" />
          <p v-if="m.lifecycleState === 'decaying'" class="memory-lifecycle-hint">较久没有再次提到，参考优先级已降低</p>
        </div>
        <div class="memory-actions memory-summary-actions">
          <button type="button" class="memory-evidence-open" aria-haspopup="dialog" :data-memory-id="m.id" data-memory-kind="formal" :disabled="memoryRefreshRequired || detailsBusy" @click="toggleDetails(m.id, $event)">查看全文与依据 <span aria-hidden="true">→</span></button>
        </div>

      </article>
      <MemoryPagination :total="group.items.length" :page="groupPage(group.items, 'formal', group.key)" :label="`${group.key}记忆分页`" :disabled="detailsBusy" @update:page="setGroupPage('formal', group.key, $event)" />
      </div>
      </section>
    </div>
    <section v-if="formalLoaded" class="archived-section" aria-label="已归档的记忆">
      <button type="button" class="candidate-toggle" :aria-expanded="archivedExpanded" aria-controls="archived-list" @click="toggleArchived">
        <span class="candidate-toggle-copy">
          <span>已归档的记忆 · {{ archivedLoaded ? `${store.archivedMemories.length} 条` : '待加载' }}</span>
          <small>长时间没有被再次提到的推断内容，已退出对话参考，但记录仍然保留。</small>
        </span>
        <span>{{ archivedExpanded ? '收起' : '查看' }}</span>
      </button>
      <div v-if="archivedExpanded" id="archived-list">
        <p v-if="archivedLoading && !archivedLoaded" role="status">正在加载已归档的记忆…</p>
        <p v-else-if="archivedLoaded && store.archivedMemories.length === 0" class="memory-desc">暂无已归档的记忆。</p>
        <article v-for="m in store.archivedMemories" :key="`archived-${m.id}`" class="memory-item archived-item">
          <div class="memory-content">
            <MemoryPreview :value="m.attributeValue" />
            <p class="memory-lifecycle-hint">
              {{ m.attributeKey }} · {{ archivedReasonLabel(m.lifecycleReason) }}<template v-if="m.lifecycleUpdatedAt"> · {{ formatMemoryTime(m.lifecycleUpdatedAt) }}</template>
            </p>
          </div>
          <div class="memory-actions">
            <n-button size="small" secondary :loading="restoringMemoryId === m.id" :disabled="restoringMemoryId !== null" @click="restoreArchived(m.id)">恢复</n-button>
          </div>
        </article>
      </div>
    </section>
    <n-drawer :show="detailsOpen" width="min(640px, 100vw)" :mask-closable="!detailsBusy" :close-on-esc="!detailsBusy"
      content-style="font-family: var(--font-body)" :theme-overrides="drawerTheme" @update:show="updateDetailsOpen" @after-leave="restoreDetailsFocus">
      <n-drawer-content body-content-style="padding: 24px; padding-bottom: max(24px, env(safe-area-inset-bottom));">
        <template #header>
          <div class="memory-drawer-header">
            <button id="memory-details-return" type="button" class="memory-evidence-open" :disabled="detailsBusy" @click="closeDetails">返回记忆列表</button>
            <div class="memory-current-heading">
              <h2>{{ activeDetail?.attributeKey }}</h2>
              <div class="memory-detail-navigation">
                <button type="button" class="memory-evidence-open" :disabled="detailsBusy || detailIndex <= 0" @click="navigateDetail(-1)">上一条</button>
                <button type="button" class="memory-evidence-open" :disabled="detailsBusy || detailIndex >= detailGroup.length - 1" @click="navigateDetail(1)">下一条</button>
              </div>
            </div>
            <p class="memory-position">{{ candidateDetailsId !== null ? '待确认 · ' : '' }}第 {{ detailIndex + 1 }} 条 / 共 {{ detailGroup.length }} 条</p>
            <div class="memory-detail-tabs" role="tablist" aria-label="记忆详情分区" @keydown="onDetailTabKey">
              <button id="memory-content-tab" type="button" role="tab" :aria-selected="detailTab === 'content'" :tabindex="detailTab === 'content' ? 0 : -1" aria-controls="memory-content-panel" :disabled="detailsBusy" @click="selectDetailTab('content')">记忆内容</button>
              <button id="memory-evidence-tab" type="button" role="tab" :aria-selected="detailTab === 'evidence'" :tabindex="detailTab === 'evidence' ? 0 : -1" aria-controls="memory-evidence-panel" :disabled="detailsBusy" @click="selectDetailTab('evidence')">形成依据 <span>{{ activeEvidenceLoading ? '…' : activeEvidence.length }}</span></button>
            </div>
          </div>
        </template>
        <p v-if="detailsBusy" class="memory-desc" role="status">请先保存或取消编辑，再返回列表。</p>
        <p v-if="memoryError" class="memory-action-error" role="alert">{{ memoryError }}</p>
        <template v-for="m in activeFormalMemories" :key="`formal-${m.id}`">
        <div v-if="expandedMemoryIds.has(m.id)" :id="`memory-detail-${m.id}`" class="memory-details" role="region" :aria-labelledby="`memory-detail-heading-${m.id}`">
        <h4 :id="`memory-detail-heading-${m.id}`" class="memory-detail-heading-title sr-only">{{ m.attributeKey }} · 详情与依据</h4>
        <div v-show="detailTab === 'content'" id="memory-content-panel" role="tabpanel" aria-labelledby="memory-content-tab">
        <p v-if="isSafetyState(m) || m.isCore" class="memory-status">
          <span class="memory-status-tag" :class="{ 'memory-status-recent': isSafetyState(m) }">{{ isSafetyState(m) ? '近期状态' : '核心记忆' }}</span>
        </p>
        <div v-if="editingMemoryId === m.id" class="memory-editor">
          <textarea v-model="editingMemoryValue" class="memory-textarea" rows="3" maxlength="500"
            aria-label="编辑记忆内容" :disabled="savingMemoryId !== null" />
          <n-checkbox v-if="!isSafetyStateByValue(editingMemoryValue, editingMemoryId)" v-model:checked="editingMemoryIsCore">设为核心属性</n-checkbox>
          <small v-else class="memory-safety-note">近期状态不可设为核心长期画像。</small>
        </div>
        <p v-else class="memory-detail-value">{{ m.attributeValue }}</p>
        <p v-if="isSafetyState(m)" class="memory-safety-note">仅作为你近期状态的背景参考，一段时间后会自然淡出，不会被固化为长期的核心特征。</p>

        <p v-if="m.updatedAt || m.updateTime" class="memory-updated">最近更新 {{ formatMemoryTime(m.updatedAt || m.updateTime) }}</p>
        <p v-if="m.isCore && !isSafetyState(m)" class="memory-desc">核心属性用于长期了解你；其他属性在相关话题出现时作为背景参考。</p>
        </div>
        <div v-show="detailTab === 'evidence'" id="memory-evidence-panel" role="tabpanel" aria-labelledby="memory-evidence-tab">        <div v-if="detailsLoadingIds.has(m.id)" class="memory-detail-muted">正在加载...</div>
        <div v-else-if="memoryDetailsErrorIds.has(m.id)" class="memory-detail-error">
          <span>依据暂时无法加载。</span>
          <n-button size="tiny" secondary @click="loadMemoryDetails(m.id)">重新加载</n-button>
        </div>
        <template v-else>
          <section class="memory-evidence-section">
            <div class="memory-detail-heading">
              <div>
                <div class="memory-detail-title">形成依据</div>
                <p class="memory-detail-hint">这些是帮助 AI 形成这条记忆的原始记录，可打开来源进行核对。</p>
              </div>
              <span v-if="memoryOriginalEvidence(m.id)?.length" class="memory-detail-count">{{ memoryOriginalEvidence(m.id).length }} 条记录</span>
            </div>
            <div v-if="memoryOriginalEvidence(m.id)?.length" class="memory-evidence-list">
              <article v-for="item in pageEvidence(memoryOriginalEvidence(m.id))" :key="`e-${item.id}`" class="memory-evidence-item">
                <div class="memory-evidence-meta">
                  <span>{{ evidenceDateLabel(item) }}</span>
                  <span>{{ sourceTypeLabel(item) }}</span>
                </div>
                <p class="memory-evidence-label">{{ evidenceLabel(item) }}</p>
                <blockquote v-if="isDiaryEvidence(item)" class="memory-evidence-text memory-evidence-quote">{{ evidenceDisplayText(item) }}</blockquote>
                <blockquote v-else class="memory-evidence-text memory-evidence-quote">{{ evidenceDisplayText(item) }}</blockquote>
                <div v-if="item.sourceDiaryId || item.sourceConversationId" class="evidence-source-link">
                  <span>{{ item.sourceDiaryId ? '来自这篇日记' : '来自这段聊天' }}</span>
                  <button v-if="item.sourceDiaryId" type="button" class="evidence-diary-open" @click="openDiary(item.sourceDiaryId)">打开原日记 <span aria-hidden="true">→</span></button>
                  <button v-else type="button" class="evidence-diary-open" @click="openConversation(item.sourceConversationId)">打开聊天 <span aria-hidden="true">→</span></button>
                </div>
              </article>
            </div>
            <div v-else class="memory-detail-muted">暂时没有可展示的原始记录。</div>
            <MemoryPagination :total="memoryOriginalEvidence(m.id).length" :page="evidencePage" label="形成依据分页" @update:page="setEvidencePage" />
          </section>
          <section v-if="activityEvidence(memoryEvidenceById[m.id] || []).length" class="memory-activity" aria-label="整理记录">
            <h4>整理记录</h4><p v-for="item in activityEvidence(memoryEvidenceById[m.id] || [])" :key="item.id">{{ evidenceDateLabel(item) }} · {{ activityLabel(item) }}</p>
          </section>
          <section v-if="historicalVersions(m.id).length" class="memory-history-section">
            <button type="button" class="memory-history-toggle" @click="toggleHistory(m.id)">
              {{ expandedHistoryIds.has(m.id) ? '收起版本变化' : `查看版本变化（${historicalVersions(m.id).length}）` }}
            </button>
            <div v-if="expandedHistoryIds.has(m.id)" class="memory-history-list">
              <article v-for="item in historicalVersions(m.id)" :key="`h-${item.id}`" class="memory-history-item">
                <p class="memory-history-value">{{ item.attributeValue }}</p>
                <span class="memory-history-meta">{{ memoryStatusLabel(item.status) }}<template v-if="item.updatedAt || item.updateTime || item.supersededAt"> · {{ formatMemoryTime(item.updatedAt || item.updateTime || item.supersededAt) }}</template></span>
              </article>
            </div>
          </section>
        </template>
        </div>
        </div>
        </template>
        <template v-for="candidate in activeCandidates" :key="`candidate-${candidate.id}`">
        <div v-if="candidateDetailsId === candidate.id" :id="`candidate-detail-${candidate.id}`" class="candidate-details" role="region" :aria-labelledby="`candidate-detail-heading-${candidate.id}`">
          <h4 :id="`candidate-detail-heading-${candidate.id}`" class="memory-detail-heading-title sr-only">{{ candidate.attributeKey }} · 详情与依据</h4>
          <div v-show="detailTab === 'content'" id="memory-content-panel" role="tabpanel" aria-labelledby="memory-content-tab">
          <p class="memory-detail-value">{{ candidate.attributeValue }}</p>

          <p class="memory-desc">{{ isSafetyState(candidate) ? '近期状态，仅供当前关怀参考。' : '确认后保存为正式记忆。' }} 已有 {{ candidate.evidenceCount || 0 }} 条依据。</p>
          </div>
          <div v-show="detailTab === 'evidence'" id="memory-evidence-panel" role="tabpanel" aria-labelledby="memory-evidence-tab">          <span v-if="candidateDetailsLoading" role="status">正在加载依据...</span>
          <div v-else-if="candidateDetailsError" class="memory-detail-error" role="alert">依据暂时无法加载。<n-button secondary @click="loadCandidateDetails(candidate.id)">重新加载</n-button></div>
          <template v-else>
            <section class="memory-evidence-section">
              <div class="memory-detail-heading">
                <div>
                  <div class="memory-detail-title">形成依据</div>
                  <p class="memory-detail-hint">这些是帮助 AI 形成这条候选记忆的原始记录，可打开来源进行核对。</p>
                </div>
                <span v-if="originalEvidence(candidateEvidence).length" class="memory-detail-count">{{ originalEvidence(candidateEvidence).length }} 条记录</span>
              </div>
              <div v-if="originalEvidence(candidateEvidence).length" class="memory-evidence-list">
                <article v-for="item in pageEvidence(originalEvidence(candidateEvidence))" :key="item.id" class="memory-evidence-item">
                  <div class="memory-evidence-meta">
                    <span>{{ evidenceDateLabel(item) }}</span>
                    <span>{{ sourceTypeLabel(item) }}</span>
                  </div>
                  <p class="memory-evidence-label">{{ evidenceLabel(item) }}</p>
                  <blockquote v-if="isDiaryEvidence(item)" class="memory-evidence-text memory-evidence-quote">{{ evidenceDisplayText(item) }}</blockquote>
                  <blockquote v-else class="memory-evidence-text memory-evidence-quote">{{ evidenceDisplayText(item) }}</blockquote>
                  <div v-if="item.sourceDiaryId || item.sourceConversationId" class="evidence-source-link">
                    <span>{{ item.sourceDiaryId ? '来自这篇日记' : '来自这段聊天' }}</span>
                    <button v-if="item.sourceDiaryId" type="button" class="evidence-diary-open" @click="openDiary(item.sourceDiaryId)">打开原日记 <span aria-hidden="true">→</span></button>
                    <button v-else type="button" class="evidence-diary-open" @click="openConversation(item.sourceConversationId)">打开聊天 <span aria-hidden="true">→</span></button>
                  </div>
                </article>
              </div>
              <div v-else class="memory-detail-muted">暂时没有可展示的原始记录。</div>
              <MemoryPagination :total="originalEvidence(candidateEvidence).length" :page="evidencePage" label="形成依据分页" @update:page="setEvidencePage" />
            </section>
            <section v-if="activityEvidence(candidateEvidence).length" class="memory-activity" aria-label="整理记录">
              <h4>整理记录</h4><p v-for="item in activityEvidence(candidateEvidence)" :key="item.id">{{ evidenceDateLabel(item) }} · {{ activityLabel(item) }}</p>
            </section>
          </template>
          </div>
        </div>
        </template>
        <template #footer>
          <template v-for="m in activeFormalMemories" :key="m.id">
        <div class="memory-actions memory-detail-actions">
          <template v-if="editingMemoryId === m.id">
            <n-button size="small" secondary type="primary" :disabled="savingMemoryId === m.id" @click="saveMemory(m.id)">
              {{ savingMemoryId === m.id ? '...' : '保存' }}
            </n-button>
            <n-button size="small" secondary @click="cancelEditMemory">取消</n-button>
          </template>
          <template v-else>
            <n-button size="small" class="memory-edit-action" :disabled="memoryRefreshRequired || savingMemoryId !== null" @click="startEditMemory(m)">编辑记忆</n-button>
            <n-button size="small" quaternary type="error" :disabled="memoryRefreshRequired || savingMemoryId !== null || deletingMemoryId === m.id" @click="forgetMemory(m.id)">
              {{ deletingMemoryId === m.id ? '...' : '删除' }}
            </n-button>
          </template>
        </div>
          </template>
          <template v-for="candidate in activeCandidates" :key="candidate.id">
          <div class="candidate-decisions">
            <n-button secondary type="primary" @click="approveCandidate(candidate.id)">确认</n-button>
            <n-button secondary :loading="rejectingCandidateId === candidate.id" :disabled="rejectingCandidateId !== null" @click="confirmRejectCandidate(candidate)">拒绝</n-button>
          </div>
          </template>
        </template>
      </n-drawer-content>
    </n-drawer>
    <n-modal v-model:show="showApproveModal" preset="dialog" title="确认候选记忆" :show-icon="false"
      positive-text="确认并保存" negative-text="取消" @positive-click="doApproveCandidate" @negative-click="showApproveModal = false">
      <div class="candidate-dialog-body">
        <p>你可以直接确认，也可以在这里修改后再确认：</p>
        <textarea v-model="approveEditedValue" class="memory-textarea" rows="3" maxlength="500"
          aria-label="确认后的候选记忆" placeholder="请输入最终的记忆内容" />
        <p v-if="memoryError" class="memory-action-error" role="alert">{{ memoryError }}</p>
      </div>
    </n-modal>
    <n-modal v-model:show="showRejectModal" preset="dialog" title="拒绝候选记忆" :show-icon="false"
      positive-text="确认拒绝" negative-text="取消" :positive-button-props="{ type: 'warning' }" @positive-click="doRejectCandidate" @negative-click="showRejectModal = false">
      <div class="candidate-dialog-body">
        <p>拒绝后，这条内容不会进入正式画像。你可以写下拒绝原因，帮助 AI 核对原始记录：</p>
        <textarea v-model="rejectReason" class="memory-textarea" rows="3" aria-label="拒绝候选的原因"
          placeholder="（可选）例如：这只是一时冲动，并不是我的习惯..." />
        <p v-if="memoryError" class="memory-action-error" role="alert">{{ memoryError }}</p>
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NSpin, NCheckbox, useDialog, NModal, NDrawer, NDrawerContent } from 'naive-ui'
import { memoryApi } from '../../api'
import MemoryPreview from './MemoryPreview.vue'
import MemoryPagination from './MemoryPagination.vue'
import { logWarn } from '../../utils/logger'
import { useConsolidationStore } from '../../stores/consolidation'

interface MemoryItem {
  id: number
  attributeKey: string
  attributeValue: string
  isCore?: boolean
  sourceDiaryId?: number | null
  sourceConversationId?: number | null
  lifecycleState?: string | null
  freshness?: number | null
  lifecycleReason?: string | null
  lifecycleUpdatedAt?: string | null
}

const store = useConsolidationStore()
const router = useRouter()
const dialog = useDialog()
const memoriesLoading = ref(false)
const deletingMemoryId = ref<number | null>(null)
const editingMemoryId = ref<number | null>(null)
const editingMemoryValue = ref('')
const editingMemoryIsCore = ref(false)
const showApproveModal = ref(false)
const showRejectModal = ref(false)
const activeCandidateId = ref<number | null>(null)
const rejectReason = ref('')
const approveEditedValue = ref('')
const savingMemoryId = ref<number | null>(null)
const memoryError = ref('')
const memoryRefreshRequired = ref(false)
const candidates = ref<any[]>([])
const formalLoaded = ref(store.memories.length > 0)
const formalLoadError = ref('')
const candidatesLoaded = ref(false)
const candidatesLoading = ref(false)
const candidatesError = ref('')
const candidatesExpanded = ref(false)
const formalGroupState = ref<Record<string, boolean>>({})
const candidateGroupState = ref<Record<string, boolean>>({})
const candidateDetailsError = ref(false)
let candidateDetailsRequest = 0
const formalGroups = computed(() => {
  const groups = new Map<string, { key: string; items: any[] }>()
  for (const item of store.memories) {
    const group = groups.get(item.attributeKey) || { key: item.attributeKey, items: [] as any[] }
    group.items.push(item)
    groups.set(group.key, group)
  }
  return [...groups.values()]
})
function isFormalGroupExpanded(key: string, index: number): boolean { return formalGroupState.value[key] ?? index === 0 }
function toggleFormalGroup(key: string, index: number) {
  if (editingMemoryId.value !== null || savingMemoryId.value !== null) return
  const expanded = isFormalGroupExpanded(key, index)
  formalGroupState.value = { ...formalGroupState.value, [key]: !expanded }
  if (expanded && formalGroups.value.find(group => group.key === key)?.items.some(item => expandedMemoryIds.value.has(item.id))) {
    expandedMemoryIds.value = new Set()
  }
}
function isCandidateGroupExpanded(key: string, index: number): boolean { return candidateGroupState.value[key] ?? index === 0 }
function toggleCandidateGroup(key: string, index: number) { candidateGroupState.value = { ...candidateGroupState.value, [key]: !isCandidateGroupExpanded(key, index) } }
const candidateDetailsId = ref<number | null>(null)
const candidateDetailsLoading = ref(false)
const candidateEvidence = ref<any[]>([])
const rejectingCandidateId = ref<number | null>(null)
const expandedMemoryIds = ref<Set<number>>(new Set())
const detailsLoadingIds = ref<Set<number>>(new Set())
const memoryDetailsErrorIds = ref<Set<number>>(new Set())
const memoryDetailsLoadedIds = ref<Set<number>>(new Set())
const expandedHistoryIds = ref<Set<number>>(new Set())
const memoryEvidenceById = ref<Record<number, any[]>>({})
const memoryHistoryById = ref<Record<number, any[]>>({})
let memoryDetailsGeneration = 0

const detailsOpen = computed(() => expandedMemoryIds.value.size > 0 || candidateDetailsId.value !== null)
const detailsBusy = computed(() => editingMemoryId.value !== null || savingMemoryId.value !== null)
const activeFormalMemories = computed(() => store.memories.filter(item => expandedMemoryIds.value.has(item.id)))
const activeCandidates = computed(() => candidates.value.filter(item => item.id === candidateDetailsId.value))
const drawerTheme = {
  color: 'var(--color-surface)', textColor: 'var(--color-text)', titleTextColor: 'var(--color-text)',
  headerBorderBottom: '1px solid var(--color-border)', footerBorderTop: '1px solid var(--color-border)', boxShadow: 'var(--shadow-lg)',
}
const rootElement = ref<HTMLElement | null>(null)
const detailTab = ref<'content' | 'evidence'>('content')
const evidencePage = ref(0)
const groupPages = ref<Record<string, number>>({})
const activeDetail = computed(() => activeCandidates.value[0] || activeFormalMemories.value[0])
const detailGroup = computed<any[]>(() => {
  const groups = candidateDetailsId.value !== null ? candidateGroups.value : formalGroups.value
  return groups.find(group => group.items.some(item => item.id === activeDetail.value?.id))?.items || []
})
const detailIndex = computed(() => detailGroup.value.findIndex(item => item.id === activeDetail.value?.id))
const activeEvidence = computed(() => candidateDetailsId.value !== null ? originalEvidence(candidateEvidence.value) : memoryOriginalEvidence(activeDetail.value?.id))
const activeEvidenceLoading = computed(() => candidateDetailsId.value !== null ? candidateDetailsLoading.value : detailsLoadingIds.value.has(activeDetail.value?.id))
function groupPage(items: any[], kind: string, key: string) { return Math.min(groupPages.value[`${kind}:${key}`] || 0, Math.max(0, Math.ceil(items.length / 3) - 1)) }
function groupItems(items: any[], kind: string, key: string) { return items.slice(groupPage(items, kind, key) * 3, groupPage(items, kind, key) * 3 + 3) }
function setGroupPage(kind: string, key: string, page: number) { if (!detailsBusy.value) groupPages.value = { ...groupPages.value, [`${kind}:${key}`]: page } }
function pageEvidence(items: any[]) { const page = Math.min(evidencePage.value, Math.max(0, Math.ceil(items.length / 3) - 1)); return items.slice(page * 3, page * 3 + 3) }
function setEvidencePage(page: number) { evidencePage.value = page; resetDetailScroll() }
function resetDetailScroll() {
  void nextTick(() => {
    let element = document.querySelector('.memory-details, .candidate-details')?.parentElement
    while (element) {
      if (/(auto|scroll)/.test(getComputedStyle(element).overflowY)) { element.scrollTop = 0; break }
      element = element.parentElement
    }
  })
}
function selectDetailTab(tab: 'content' | 'evidence') { if (detailsBusy.value) return; detailTab.value = tab; resetDetailScroll() }
function onDetailTabKey(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key) || detailsBusy.value) return
  event.preventDefault()
  selectDetailTab(event.key === 'Home' ? 'content' : event.key === 'End' ? 'evidence' : detailTab.value === 'content' ? 'evidence' : 'content')
  document.getElementById(`memory-${detailTab.value}-tab`)?.focus()
}
function navigateDetail(direction: number) {
  if (detailsBusy.value) return
  const item = detailGroup.value[detailIndex.value + direction]
  if (!item) return
  const candidate = candidateDetailsId.value !== null
  const groups = candidate ? candidateGroups.value : formalGroups.value
  const group = groups.find(group => group.items.some(record => record.id === item.id))
  if (group) setGroupPage(candidate ? 'candidate' : 'formal', group.key, Math.floor(group.items.indexOf(item) / 3))
  if (candidate) void toggleCandidateDetails(item.id)
  else void toggleDetails(item.id)
}
let returnTarget: { id: number; kind: string } | null = null
function prepareDetails(id: number, kind: string) {
  returnTarget = { id, kind }; detailTab.value = 'content'; evidencePage.value = 0; resetDetailScroll()
}

let detailsOpener: HTMLElement | null = null
function rememberDetailsFocus(event?: Event) {
  if (event?.currentTarget instanceof HTMLElement) detailsOpener = event.currentTarget
}
function restoreDetailsFocus() {
  // Wait for the drawer's focus trap to restore its original trigger first.
  void nextTick(() => {
    const target = returnTarget && rootElement.value?.querySelector<HTMLElement>(`[data-memory-kind="${returnTarget.kind}"][data-memory-id="${returnTarget.id}"]`)
    if (target) target.focus()
    else if (detailsOpener?.isConnected) detailsOpener.focus()
    else rootElement.value?.querySelector<HTMLElement>('.formal-heading')?.focus()
  })
}
function closeDetails() {
  if (detailsBusy.value) return
  expandedMemoryIds.value = new Set()
  candidateDetailsId.value = null
  candidateDetailsRequest++
  candidateEvidence.value = []
}
function updateDetailsOpen(show: boolean) { if (!show) closeDetails() }

const candidateGroups = computed(() => {
  const groups = new Map<string, { key: string; items: any[]; hasConflict: boolean }>()
  for (const candidate of candidates.value) {
    const key = candidate.candidateGroupKey || `${candidate.memoryType || 'memory'}:${candidate.attributeKey}`
    const group = groups.get(key) || { key, items: [] as any[], hasConflict: false }
    group.items.push(candidate)
    group.hasConflict = group.hasConflict || Boolean(candidate.hasConflict)
    groups.set(key, group)
  }
  return [...groups.values()]
})
const candidateConflictGroupCount = computed(() => candidateGroups.value.filter(group => group.hasConflict).length)

onMounted(() => {
  loadMemories()
})

// 已归档是低频查看的降噪视图：收起时不发请求，展开时才懒加载。
const archivedExpanded = ref(false)
const archivedLoaded = ref(false)
const archivedLoading = ref(false)
const restoringMemoryId = ref<number | null>(null)

async function toggleArchived() {
  archivedExpanded.value = !archivedExpanded.value
  if (archivedExpanded.value && !archivedLoaded.value) {
    archivedLoading.value = true
    try {
      await store.loadArchivedMemories()
      archivedLoaded.value = true
    } finally {
      archivedLoading.value = false
    }
  }
}

async function restoreArchived(id: number) {
  if (restoringMemoryId.value !== null) return
  restoringMemoryId.value = id
  try {
    await store.restoreArchivedMemory(id)
  } finally {
    restoringMemoryId.value = null
  }
}

function archivedReasonLabel(reason?: string | null) {
  const labels: Record<string, string> = {
    STALE_INFERRED: '长期未再提及',
    REVIVED_BY_EVIDENCE: '已由新内容恢复',
    RESTORED_BY_USER: '你已手动恢复',
  }
  return labels[reason || ''] || '已归档'
}

async function loadMemories() {
  if (memoriesLoading.value || editingMemoryId.value !== null || savingMemoryId.value !== null) return false
  memoriesLoading.value = true
  formalLoadError.value = ''
  const candidateRequest = loadCandidates()
  let loaded = false
  try {
    const response = await memoryApi.getAll()
    store.memories = response.data.data || []
    formalLoaded.value = true
    memoryRefreshRequired.value = false
    memoryError.value = ''
    resetMemoryDetails()
    loaded = true
  } catch {
    formalLoadError.value = '正式记忆暂时无法刷新，已保留之前的内容。'
  } finally {
    memoriesLoading.value = false
  }
  await candidateRequest
  return loaded
}

async function loadCandidates() {
  if (candidatesLoading.value) return
  candidatesLoading.value = true
  candidatesError.value = ''
  try {
    const response = await memoryApi.getCandidates()
    candidates.value = response.data.data || []
    candidatesLoaded.value = true
    if (!candidates.value.some(item => item.id === candidateDetailsId.value)) {
      candidateDetailsId.value = null
      candidateDetailsRequest++
    }
  } catch (e) {
    logWarn('memory', '加载候选记忆失败', e)
    candidatesError.value = '候选记忆加载失败，已保留之前的内容。'
  } finally { candidatesLoading.value = false }
}

function diaryIdsFor(item: any): number[] {
  return [...new Set([...(item.sourceDiaryIds || []), item.sourceDiaryId].filter(Boolean).map(Number))]
}

type DiarySourcePreview = { id: number; createdAt?: string | null; excerpt?: string | null }

function diarySourceExcerpt(source: DiarySourcePreview): string {
  return source.excerpt?.trim() || '打开查看日记内容'
}

function isDiaryEvidence(item: any): boolean {
  const sourceType = String(item?.sourceType || '').toLowerCase()
  return diaryIdsFor(item).length > 0 || sourceType === 'diary_inferred' || sourceType === 'diary'
}

function evidenceDateLabel(item: any): string {
  const diaryCreatedAt = item?.sourceDiaryPreview?.createdAt
  if (diaryCreatedAt) return String(diaryCreatedAt).replace('T', ' ').slice(0, 10)
  if (item?.evidenceDate) return String(item.evidenceDate).slice(0, 10)
  return isDiaryEvidence(item) ? '日记日期未记录' : '日期未记录'
}

const activityActions: Record<string, string> = {
  USER_EDITED: '用户编辑记忆', USER_CONFIRMED_CHAT_EDIT: '用户在聊天中确认修改记忆',
  USER_CONFIRMED_REPLACEMENT: '用户确认重写记忆', USER_CONFIRMED_CHAT: '用户在聊天中确认记忆',
  USER_CONFIRMED: '用户确认记忆', AUTO_UPGRADED: '依据充分后自动保存',
  USER_MERGED: '用户确认合并记忆', USER_CONFIRMED_MERGE: '用户确认合并记忆',
}
function isActivity(item: any): boolean {
  const type = String(item?.sourceType || '').toLowerCase()
  return type === 'user_action' || type === 'system' || Object.prototype.hasOwnProperty.call(activityActions, item?.evidenceText?.trim() || '')
}
function activityEvidence(items: any[]): any[] { return items.filter(isActivity) }
function originalEvidence(items: any[]): any[] { return items.filter(item => !isActivity(item)) }
function memoryOriginalEvidence(id: number): any[] { return originalEvidence(memoryEvidenceById.value[id] || []) }
function activityLabel(item: any): string {
  const text = String(item?.evidenceText || '').trim()
  if (Object.prototype.hasOwnProperty.call(activityActions, text)) return activityActions[text]
  if (text.includes('合并')) return '用户确认合并记忆'
  if (text.includes('重写')) return '用户确认重写记忆'
  if (text.includes('编辑') || text.includes('修改')) return '用户编辑记忆'
  return String(item?.sourceType || '').toLowerCase() === 'system' ? '系统整理记忆' : '用户确认整理记忆'
}
function evidenceLabel(item: any): string {
  if (isDiaryEvidence(item)) return '日记摘录'
  if (conversationIdsFor(item).length || String(item?.sourceType).toLowerCase() === 'chat_candidate') return '用户聊天原文'
  return '用户提供的依据'
}
function evidenceDisplayText(item: any): string {
  if (isActivity(item)) return activityLabel(item)
  if (item.evidenceText?.trim()) return item.evidenceText.trim()
  if (isDiaryEvidence(item)) return item.sourceDiaryPreview ? diarySourceExcerpt(item.sourceDiaryPreview) : '原始日记内容暂不可查看'
  return '暂无可展示的原始记录'
}

function conversationIdsFor(item: any): number[] {
  return [...new Set([...(item.sourceConversationIds || []), item.sourceConversationId].filter(Boolean).map(Number))]
}

function sourceTypeLabel(item: any): string {
  if (isActivity(item)) return '整理记录'
  if (isDiaryEvidence(item)) return '来自日记'
  if (conversationIdsFor(item).length || String(item.sourceType).toLowerCase() === 'chat_candidate') return '来自聊天'
  if (item.sourceType === 'explicit') return '用户确认'
  return '暂无原始来源'
}

function formatMemoryTime(value: string | null | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 16)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}


async function approveCandidate(id: number) {
  const candidate = candidates.value.find(c => c.id === id)
  if (candidate) {
    memoryError.value = ''
    approveEditedValue.value = candidate.attributeValue
    activeCandidateId.value = id
    showApproveModal.value = true
  }
}

async function doApproveCandidate() {
  if (activeCandidateId.value === null) return
  const id = activeCandidateId.value
  try {
    await memoryApi.approveCandidate(id, { editedValue: approveEditedValue.value })
    await loadMemories()
    window.$message?.success('记忆已确认')
    showApproveModal.value = false
  } catch (e) {
    return candidateDecisionFailed(e)
  }
}

function confirmRejectCandidate(candidate: any) {
  memoryError.value = ''
  activeCandidateId.value = candidate.id
  rejectReason.value = ''
  showRejectModal.value = true
}

async function doRejectCandidate() {
  if (activeCandidateId.value === null) return
  const id = activeCandidateId.value
  if (rejectingCandidateId.value !== null) return
  rejectingCandidateId.value = id
  try {
    await memoryApi.rejectCandidate(id, { reason: rejectReason.value })
    candidates.value = candidates.value.filter(candidate => candidate.id !== id)
    if (candidateDetailsId.value === id) {
      candidateDetailsId.value = null
      candidateEvidence.value = []
    }
    window.$message?.success('已拒绝，这条内容近期不会再被自动加入')
    showRejectModal.value = false
  } catch (e) {
    return candidateDecisionFailed(e)
  } finally {
    rejectingCandidateId.value = null
  }
}

async function candidateDecisionFailed(error: any) {
  if (error?.response?.status === 409) {
    showApproveModal.value = false
    showRejectModal.value = false
    const refreshed = await loadMemories()
    memoryError.value = refreshed
      ? '这条候选记忆已经处理，已刷新当前记录。'
      : '这条候选记忆已经处理，但当前记录暂时无法刷新，请重试。'
  } else {
    memoryError.value = String(error?.response?.data?.message || '').includes('触发受限资料规则')
      ? '触发受限资料规则，请移除受限资料。'
      : '操作未完成，请保留内容并稍后重试。'
  }
  logWarn('memory', '候选决定未完成', { status: error?.response?.status })
  return false
}


async function toggleCandidateDetails(id: number, event?: Event) {
  rememberDetailsFocus(event)
  if (editingMemoryId.value !== null || savingMemoryId.value !== null) return
  candidateDetailsRequest++
  if (candidateDetailsId.value === id) {
    candidateDetailsId.value = null
    candidateEvidence.value = []
    return
  }
  prepareDetails(id, 'candidate')
  candidateDetailsId.value = id
  expandedMemoryIds.value = new Set()
  candidateEvidence.value = []
  await loadCandidateDetails(id)
}
async function loadCandidateDetails(id: number) {
  const request = ++candidateDetailsRequest
  candidateDetailsLoading.value = true
  candidateDetailsError.value = false
  try {
    const response = await memoryApi.getCandidateEvidence(id)
    if (request === candidateDetailsRequest && candidateDetailsId.value === id) candidateEvidence.value = response.data.data || []
  } catch (e) {
    if (request === candidateDetailsRequest && candidateDetailsId.value === id) candidateDetailsError.value = true
    logWarn('memory', '加载候选依据失败', id, e)
  } finally {
    if (request === candidateDetailsRequest) candidateDetailsLoading.value = false
  }
}

async function toggleDetails(id: number, event?: Event) {
  rememberDetailsFocus(event)
  if (editingMemoryId.value !== null || savingMemoryId.value !== null) return
  if (expandedMemoryIds.value.has(id)) {
    expandedMemoryIds.value = new Set()
    return
  }

  prepareDetails(id, 'formal')
  expandedMemoryIds.value = new Set([id])
  candidateDetailsId.value = null
  candidateDetailsRequest++
  candidateEvidence.value = []
  if (memoryDetailsLoadedIds.value.has(id)) return
  await loadMemoryDetails(id)
}

async function loadMemoryDetails(id: number) {
  if (detailsLoadingIds.value.has(id)) return
  const generation = memoryDetailsGeneration
  detailsLoadingIds.value = new Set(detailsLoadingIds.value).add(id)
  const nextErrors = new Set(memoryDetailsErrorIds.value)
  nextErrors.delete(id)
  memoryDetailsErrorIds.value = nextErrors
  try {
    const [evidence, history] = await Promise.all([memoryApi.getEvidence(id), memoryApi.getHistory(id)])
    if (generation !== memoryDetailsGeneration) return
    memoryEvidenceById.value = { ...memoryEvidenceById.value, [id]: evidence.data.data || [] }
    memoryHistoryById.value = { ...memoryHistoryById.value, [id]: history.data.data || [] }
    memoryDetailsLoadedIds.value = new Set(memoryDetailsLoadedIds.value).add(id)
  } catch (e) {
    if (generation === memoryDetailsGeneration) memoryDetailsErrorIds.value = new Set(memoryDetailsErrorIds.value).add(id)
    logWarn('memory', '加载记忆依据失败', id, e)
  } finally {
    if (generation === memoryDetailsGeneration) {
      const nextLoading = new Set(detailsLoadingIds.value)
      nextLoading.delete(id)
      detailsLoadingIds.value = nextLoading
    }
  }
}

function toggleHistory(id: number) {
  const next = new Set(expandedHistoryIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedHistoryIds.value = next
}

function resetMemoryDetails() {
  memoryDetailsGeneration++
  expandedMemoryIds.value = new Set()
  detailsLoadingIds.value = new Set()
  memoryDetailsErrorIds.value = new Set()
  memoryDetailsLoadedIds.value = new Set()
  expandedHistoryIds.value = new Set()
  memoryEvidenceById.value = {}
  memoryHistoryById.value = {}
}

function historicalVersions(id: number) {
  return (memoryHistoryById.value[id] || []).filter(item => item.id !== id)
}

function openDiary(id: number) {
  router.push({ name: 'diary-detail', params: { id } })
}

function openConversation(id: number) {
  sessionStorage.setItem('pendingChatConversationId', String(id))
  router.push({ name: 'chat' })
}

function forgetMemory(id: number) {
  const memory = store.memories.find((item: any) => item.id === id)
  dialog.warning({
    title: '删除这条记忆？',
    content: `删除后，这条记忆连同它的全部历史版本一起清除，找不回来（想保留历史请用「编辑」）。系统会暂时记住你不想保留这条内容，避免 AI 很快又自动加回来；你之后明确表达新的事实时，仍可重新建立。${memory?.attributeKey ? `\n\n当前记忆：${memory.attributeKey}` : ''}`,
    positiveText: '确认删除',
    negativeText: '取消',
    positiveButtonProps: { type: 'error' },
    onPositiveClick: () => deleteMemory(id),
  })
}

async function deleteMemory(id: number) {
  deletingMemoryId.value = id
  try {
    await memoryApi.forget(id)
    store.memories = store.memories.filter((m: any) => m.id !== id)
    if (expandedMemoryIds.value.has(id)) closeDetails()
  } catch (e) {
    memoryError.value = '删除未完成，请稍后重试。'
    logWarn('memory', '删除记忆失败', id, e)
  } finally {
    deletingMemoryId.value = null
  }
}

function startEditMemory(m: MemoryItem) {
  detailTab.value = 'content'
  resetDetailScroll()
  editingMemoryId.value = m.id
  editingMemoryValue.value = m.attributeValue
  editingMemoryIsCore.value = isSafetyState(m) ? false : !!m.isCore
}

function isSafetyState(item: any) {
  if (String(item?.memoryType || '').toLowerCase() === 'short_term_state') return true
  const text = `${item?.attributeKey || ''} ${item?.attributeValue || ''}`
  return /自杀|自残|轻生|想死|不想活|结束生命|伤害自己|割腕|跳楼|心理危机|危机干预/.test(text)
}

function isSafetyStateByValue(value: string, id: number | null) {
  const item = store.memories.find((memory: any) => memory.id === id)
  return isSafetyState(item || { attributeValue: value })
}

function memoryStatusLabel(status: string) {
  return ({ active: '当前内容', superseded: '已被新版本替代', expired: '已过期', rejected: '已移除' } as Record<string, string>)[status] || '历史内容'
}

async function saveMemory(id: number) {
  const value = editingMemoryValue.value.trim()
  if (!value || savingMemoryId.value !== null || memoryRefreshRequired.value) return

  savingMemoryId.value = id
  memoryError.value = ''
  let saved = false
  try {
    await memoryApi.update(id, { 
      attributeValue: value,
      isCore: isSafetyStateByValue(value, id) ? false : editingMemoryIsCore.value
    })
    saved = true
    // Editing creates a new formal version. Reload its ID and metadata before
    // allowing another edit, delete or detail request against this row.
    const response = await memoryApi.getAll()
    store.memories = response.data.data || []
    resetMemoryDetails()
    editingMemoryId.value = null
    editingMemoryValue.value = ''
  } catch (e: any) {
    if (saved) {
      memoryRefreshRequired.value = true
      editingMemoryId.value = null
      memoryError.value = '修改已经保存，但最新记录暂时无法加载。请刷新后再编辑。'
    } else {
      memoryError.value = String(e?.response?.data?.message || '').includes('触发受限资料规则')
        ? '触发受限资料规则，请移除受限资料。'
        : '保存未完成，记忆可能已经变化。请保留草稿并刷新后重新核对。'
    }
    logWarn('memory', '保存记忆未完成', id, { saved, status: e?.response?.status })
  } finally {
    savingMemoryId.value = null
  }
}

function cancelEditMemory() {
  editingMemoryId.value = null
  editingMemoryValue.value = ''
}
</script>

<style scoped>
.tab-content { padding: var(--space-4) 0; min-height: 400px; font-family: var(--font-body); }
.section-head, .memory-toolbar, .memory-drawer-header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); }
.section-head { flex-wrap: wrap; margin-bottom: var(--space-2); }
.memory-section-title { margin: 0; color: var(--color-text); font: 600 22px/1.4 var(--font-serif); }
.memory-toolbar { flex-wrap: wrap; justify-content: flex-start; gap: var(--space-2); }
.memory-desc { margin: 0 0 var(--space-4); color: var(--color-text-secondary); font-size: 13px; line-height: 1.6; }
.memory-overview { margin: var(--space-3) 0 var(--space-5); color: var(--color-text-secondary); font-size: 13px; font-variant-numeric: tabular-nums; }
.memory-action-error { color: var(--color-error); font-size: 13px; line-height: 1.6; }
.memory-textarea { display: block; box-sizing: border-box; width: 100%; padding: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); color: var(--color-text); background: var(--color-surface); font: inherit; line-height: 1.7; resize: vertical; }
.memory-textarea:focus { outline: 2px solid var(--color-primary); outline-offset: 2px; }
.memory-textarea:disabled { color: var(--color-text-muted); background: var(--color-surface-soft); }
.candidate-dialog-body { margin-top: var(--space-4); }
.candidate-dialog-body > p { margin: 0 0 var(--space-2); color: var(--color-text-secondary); }
.candidate-dialog-body > .memory-action-error { color: var(--color-error); }
.candidate-section { margin: var(--space-5) 0 var(--space-8); padding: var(--space-3) var(--space-4); border: 1px solid var(--color-border-strong); background: var(--color-surface-soft); }
.candidate-toggle-copy { display: flex; min-width: 0; flex-direction: column; gap: var(--space-1); }
.candidate-toggle-copy > span { font-size: 16px; }
.candidate-toggle-copy small { color: var(--color-text-secondary); font-size: 12px; font-weight: 400; line-height: 1.5; }
.candidate-queue-note { margin: var(--space-3) 0 var(--space-5); color: var(--color-text-secondary); font-size: 12px; line-height: 1.6; }
.candidate-group { border-top: 1px solid var(--color-border); padding: var(--space-2) 0; }
.candidate-conflict-note { margin: 0 0 var(--space-3); color: var(--color-text-secondary); font-size: 12px; line-height: 1.5; }
.candidate-item { min-width: 0; padding: var(--space-3) 0; }
.candidate-item + .candidate-item { border-top: 1px solid var(--color-border); }
.candidate-copy { min-width: 0; }
.candidate-value { color: var(--color-text); font-size: 14px; font-weight: 400; line-height: 1.75; white-space: pre-wrap; overflow-wrap: anywhere; }
.candidate-actions { display: flex; justify-content: flex-start; margin-top: var(--space-1); }
.candidate-decisions { display: flex; flex-wrap: wrap; gap: var(--space-2); margin: var(--space-4) 0; }
.formal-heading { display: flex; align-items: baseline; flex-wrap: wrap; gap: var(--space-3); margin: var(--space-8) 0 var(--space-4); color: var(--color-text); font: 600 21px/1.4 var(--font-serif); }
.formal-heading small { color: var(--color-text-secondary); font-family: var(--font-body); font-size: 12px; font-weight: 400; }
.memory-list { display: flex; flex-direction: column; min-width: 0; }
.formal-group { min-width: 0; padding: var(--space-3) 0 var(--space-5); border-top: 1px solid var(--color-border-strong); }
.formal-group h3, .candidate-group h3 { margin: 0; font: inherit; }
.group-toggle, .candidate-toggle { display: flex; align-items: center; justify-content: space-between; gap: var(--space-4); width: 100%; min-height: 44px; padding: var(--space-2) 0; border: 0; background: transparent; color: var(--color-text); text-align: left; font: inherit; cursor: pointer; }
.group-title-row { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--space-2); min-width: 0; }
.group-title { font-size: 16px; font-weight: 600; line-height: 1.5; overflow-wrap: anywhere; }
.group-count { flex-shrink: 0; color: var(--color-text-secondary); font-size: 12px; font-weight: 400; white-space: nowrap; }
.group-toggle > span:last-child, .candidate-toggle > span:last-child { flex-shrink: 0; color: var(--color-primary); font-size: 12px; font-weight: 500; white-space: nowrap; }
.group-toggle:hover .group-title, .candidate-toggle:hover > span:last-child { color: var(--color-primary-hover); }
.group-toggle:active, .candidate-toggle:active { background: var(--color-surface-hover); }
.group-toggle:focus-visible, .candidate-toggle:focus-visible, .formal-heading:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 3px; }
.group-toggle:disabled, .group-toggle:disabled > span:last-child { color: var(--color-disabled); cursor: not-allowed; }
.group-summary { margin: var(--space-1) 0 0; overflow: hidden; color: var(--color-text-secondary); font-size: 13px; line-height: 1.6; text-overflow: ellipsis; white-space: nowrap; }
.formal-group > div, .candidate-group > div { margin: var(--space-2) 0 0; padding-left: var(--space-4); border-left: 1px solid var(--color-border); }
.memory-item { min-width: 0; padding: var(--space-2) 0 var(--space-3); }
.memory-item + .memory-item { padding-top: var(--space-4); border-top: 1px solid var(--color-border); }
.memory-content { min-width: 0; }
.memory-value { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; color: var(--color-text); font-size: 14px; font-weight: 400; line-height: 1.75; white-space: pre-wrap; overflow-wrap: anywhere; }
.memory-actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
.memory-summary-actions { margin-top: var(--space-1); }
.memory-evidence-open { display: inline-flex; align-items: center; justify-content: center; gap: var(--space-2); min-height: 44px; min-width: 44px; padding: 0; border: none; background: transparent; color: var(--color-primary); font: 500 13px/1.5 var(--font-body); cursor: pointer; }
.memory-evidence-open:hover { color: var(--color-primary-hover); text-decoration: underline; text-underline-offset: 4px; }
.memory-evidence-open:active { color: var(--color-primary); background: var(--color-surface-hover); }
.memory-evidence-open:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 3px; }
.memory-evidence-open:disabled { color: var(--color-disabled); cursor: not-allowed; text-decoration: none; }
.memory-drawer-header { width: 100%; flex-wrap: wrap; }
.memory-details, .candidate-details { min-width: 0; color: var(--color-text-secondary); font-size: 13px; line-height: 1.6; }
.memory-detail-heading-title { margin: 0 0 var(--space-4); color: var(--color-text); font: 600 21px/1.5 var(--font-serif); }
.memory-status { margin: 0 0 var(--space-3); }
.memory-detail-value { margin: 0 0 var(--space-5); color: var(--color-text); font-size: 16px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.memory-editor { display: flex; flex-direction: column; gap: var(--space-2); }
.memory-detail-actions { margin: var(--space-4) 0; }
.memory-updated, .memory-safety-note { color: var(--color-text-secondary); font-size: 12px; line-height: 1.6; }
.memory-safety-note { display: block; margin-top: var(--space-2); font-weight: 400; }
.memory-detail-heading { display: flex; align-items: baseline; justify-content: space-between; flex-wrap: wrap; gap: var(--space-2); }
.memory-detail-title { color: var(--color-text); font-size: 14px; font-weight: 600; }
.memory-detail-hint { margin: var(--space-1) 0 0; color: var(--color-text-secondary); font-size: 12px; line-height: 1.6; }
.memory-detail-count { flex-shrink: 0; color: var(--color-text-secondary); font-size: 12px; }
.memory-evidence-section { margin-top: var(--space-6); padding: var(--space-5) 0; border-top: 1px solid var(--color-border-strong); }
.memory-evidence-item { display: grid; grid-template-columns: 100px minmax(0, 1fr); column-gap: var(--space-4); padding: var(--space-4) 0; }
.memory-evidence-item + .memory-evidence-item { border-top: 1px solid var(--color-border); }
.memory-evidence-meta { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-1); color: var(--color-text-secondary); font-size: 11px; line-height: 1.5; font-variant-numeric: tabular-nums; }
.memory-evidence-label { margin: 0 0 var(--space-2); color: var(--color-text-secondary); font-size: 12px; }
.memory-evidence-text { margin: 0 0 var(--space-2); color: var(--color-text-secondary); font-size: 13px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.memory-evidence-label, .memory-evidence-text, .evidence-source-link { grid-column: 2; }
.memory-evidence-quote { padding: 0 0 0 var(--space-3); border-left: 1px solid var(--color-border-strong); }
.evidence-source-link { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); color: var(--color-text-secondary); font-size: 12px; }
.evidence-diary-open, .memory-history-toggle { display: inline-flex; align-items: center; gap: var(--space-1); min-height: 44px; padding: 0; border: 0; background: transparent; color: var(--color-primary); font: inherit; font-size: 12px; cursor: pointer; }
.evidence-diary-open:hover, .memory-history-toggle:hover { color: var(--color-primary-hover); text-decoration: underline; text-underline-offset: 3px; }
.evidence-diary-open:focus-visible, .memory-history-toggle:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 3px; }
.memory-history-section, .memory-activity { padding: var(--space-4) 0; border-top: 1px solid var(--color-border); }
.memory-activity h4 { margin: 0 0 var(--space-2); font-size: 13px; font-weight: 500; }
.memory-activity p { margin: var(--space-2) 0; }
.memory-history-item { padding: var(--space-3) 0; border-top: 1px solid var(--color-border); }
.memory-history-value { margin: 0 0 var(--space-1); color: var(--color-text-secondary); font-size: 13px; line-height: 1.6; }
.memory-history-meta { color: var(--color-text-secondary); font-size: 11px; }
.memory-detail-muted { color: var(--color-text-secondary); }
.memory-detail-error { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); color: var(--color-text-secondary); }
:deep(.n-button) { min-height: 44px; }
@media (max-width: 640px) {
  .memory-toolbar { width: 100%; }
  .section-head { margin-bottom: var(--space-3); }
  .memory-drawer-header > span { font-size: 16px; }
  .memory-evidence-item { grid-template-columns: minmax(0, 1fr); row-gap: var(--space-2); }
  .memory-evidence-meta { flex-direction: row; flex-wrap: wrap; gap: var(--space-2); }
  .memory-evidence-label, .memory-evidence-text, .evidence-source-link { grid-column: 1; }
}

.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
.section-head { justify-content: flex-end; }
.memory-overview { margin-top: 0; }
.candidate-section { border-color: color-mix(in oklab, var(--color-warning) 50%, var(--color-border)); background: color-mix(in oklab, var(--color-warning) 10%, var(--color-surface)); }
.candidate-toggle-copy > span { color: var(--color-text); font-weight: 600; }
.candidate-toggle-copy > span::before { content: ''; display: inline-block; width: 6px; height: 6px; margin-right: var(--space-2); border-radius: 50%; background: var(--color-warning); vertical-align: middle; }
/* 展开的内容挂在标题下面：细竖线 + 缩进表达从属关系，替代原来整行铺底的色块。 */
.formal-group > div, .candidate-group > div { border-left: 1px solid var(--color-border-strong); padding-left: var(--space-4); padding-right: 0; }
.group-toggle { padding: var(--space-2) 0; border-radius: 0; }
/* 展开态靠箭头方向 + 下方缩进表达，不再整行铺底色 —— 色块会让标题变成与内容脱节的一张卡片。
   箭头用实心三角而不是旋转的折角，方向确定、不依赖 transform 的直觉。 */
.group-toggle > span:last-child::after,
.candidate-toggle > span:last-child::after {
  content: '';
  display: inline-block;
  width: 0;
  height: 0;
  margin-left: 6px;
  border-left: 4px solid currentColor;
  border-top: 3.5px solid transparent;
  border-bottom: 3.5px solid transparent;
  vertical-align: middle;
}
.group-toggle[aria-expanded='true'] > span:last-child::after,
.candidate-toggle[aria-expanded='true'] > span:last-child::after {
  border-left: 3.5px solid transparent;
  border-right: 3.5px solid transparent;
  border-top: 4px solid currentColor;
  border-bottom: 0;
}
.group-title { color: var(--color-primary); font-family: var(--font-serif); font-size: 18px; }
.group-title-row { flex: 0 1 auto; display: flex; align-items: baseline; gap: var(--space-2); min-width: 0; }
.memory-item, .candidate-item { padding: var(--space-2) 0 var(--space-3); }
.memory-summary-actions { justify-content: flex-start; margin-top: 0; }
.candidate-actions { justify-content: flex-end; margin-top: var(--space-1); }
.memory-evidence-open { font-size: 12px; }
.memory-status-tag { display: inline-block; padding: 2px 8px; border: 1px solid color-mix(in oklab, var(--color-accent) 30%, var(--color-border)); border-radius: var(--radius-sm); color: color-mix(in oklab, var(--color-accent) 55%, var(--color-text)); background: var(--color-accent-bg); font-size: 12px; font-weight: 500; }
.memory-status-recent { color: var(--color-text); border-color: color-mix(in oklab, var(--color-warning) 45%, var(--color-border)); background: color-mix(in oklab, var(--color-warning) 12%, var(--color-surface)); }
.memory-lifecycle-hint { margin: var(--space-2) 0 0; color: var(--color-text-secondary); font-size: 12px; line-height: 1.6; }
.archived-section { margin-top: var(--space-5); border-top: 1px solid var(--color-border); padding-top: var(--space-3); }
.archived-item { border-bottom: 1px solid var(--color-border); }
.archived-item:last-child { border-bottom: 0; }
.archived-item .memory-actions { justify-content: flex-end; }
.memory-drawer-header { display: block; }
.memory-current-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-3); }
.memory-current-heading h2 { min-width: 0; margin: 0; color: var(--color-text); font: 600 22px/1.5 var(--font-serif); overflow-wrap: anywhere; }
.memory-detail-navigation { display: flex; flex-shrink: 0; gap: var(--space-3); }
.memory-position { margin: var(--space-1) 0 var(--space-3); color: var(--color-text-secondary); font: 12px/1.6 var(--font-body); }
.memory-detail-tabs { display: flex; gap: var(--space-3); border-bottom: 1px solid var(--color-border); }
.memory-detail-tabs button { min-height: 44px; padding: var(--space-2) var(--space-3); border: none; border-bottom: 2px solid transparent; background: transparent; color: var(--color-text-secondary); font: 500 15px/1.5 var(--font-body); cursor: pointer; }
.memory-detail-tabs button[aria-selected='true'] { border-bottom-color: var(--color-primary); background: var(--color-primary-light); color: var(--color-primary); }
.memory-detail-tabs button:hover:not(:disabled) { background: var(--color-surface-hover); }
.memory-detail-tabs button:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }
.memory-detail-tabs button:disabled { cursor: not-allowed; }
.memory-detail-tabs button span { margin-left: var(--space-1); font-size: 12px; }
.memory-detail-value { font-family: var(--font-body); font-weight: 400; }
.memory-evidence-section { margin-top: 0; padding-top: 0; border-top: 0; }
.memory-evidence-meta span + span { color: color-mix(in oklab, var(--color-info) 70%, var(--color-text)); }
.memory-evidence-quote { padding: var(--space-2) var(--space-3); border-left-color: var(--color-info); background: color-mix(in oklab, var(--color-info) 5%, var(--color-surface)); }
.memory-detail-actions, .candidate-decisions { width: 100%; justify-content: space-between; margin: 0; padding-bottom: env(safe-area-inset-bottom); }
.memory-detail-actions .memory-edit-action { flex: 1; margin-right: var(--space-4); color: var(--color-on-primary); background: var(--color-primary); }
.memory-detail-actions .memory-edit-action:not(.n-button--disabled):hover { background: var(--color-primary-hover); }
.memory-detail-actions .memory-edit-action.n-button--disabled { color: var(--color-text-secondary); background: var(--color-surface-soft); }
@media (max-width: 420px) {
  .memory-current-heading { flex-wrap: wrap; }
  .memory-detail-navigation { margin-left: auto; }
  .memory-current-heading h2 { font-size: 20px; }
}

</style>
