<template>
  <div class="tab-content">
    <div class="section-head" style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 16px;">
      <div style="display: flex; align-items: center; gap: 8px; flex-shrink: 0;">
        <p class="settings-label" style="margin: 0; font-weight: bold; white-space: nowrap;">我的记忆</p>
      </div>
      <n-button size="small" secondary type="primary" :loading="store.consolidatingMemory" :disabled="editingMemoryId !== null || savingMemoryId !== null" @click="store.consolidateMemories()">
        智能整理记忆
      </n-button>
      <n-button size="small" secondary :loading="memoriesLoading" :disabled="savingMemoryId !== null || editingMemoryId !== null" @click="loadMemories">
         刷新
      </n-button>
    </div>
    <p class="memory-desc">查看已经记住的内容，核对依据，或纠正不准确的记忆。</p>
    <p class="memory-overview" role="status">{{ formalLoaded ? `${store.memories.length} 条正式记忆` : '正式记忆数量待加载' }} · {{ candidatesLoaded ? `${candidates.length} 条待确认` : '待确认数量待加载' }}</p>
    <p v-if="memoryError" class="memory-action-error" role="alert">{{ memoryError }}</p>
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
      <div class="candidate-head">
        <div class="candidate-head-main">
          <div class="candidate-title-row">
            <span class="candidate-title">待确认的记忆</span>
            <span class="candidate-count">{{ candidates.length }} 条</span>
          </div>
          <small>候选尚未进入正式画像。新属性在多日原始依据充分、满足类型规则时可能自动保存；已有正式记忆的改写须由你确认。近期状态不会设为核心长期画像。</small>
        </div>
        <span class="candidate-summary">
          {{ candidateGroups.length }} 个属性<template v-if="candidateConflictGroupCount"> · {{ candidateConflictGroupCount }} 个属性有不同候选</template>
        </span>
      </div>
      <div v-for="(group, groupIndex) in candidateGroups" :key="group.key" class="candidate-group">
      <h3><button type="button" class="group-toggle" :aria-expanded="isCandidateGroupExpanded(group.key, groupIndex)" :aria-controls="`candidate-group-${groupIndex}`" @click="toggleCandidateGroup(group.key, groupIndex)"><span>{{ group.items[0].attributeKey }} · {{ group.items.length }} 条</span><span>{{ isCandidateGroupExpanded(group.key, groupIndex) ? '收起' : '展开' }}</span></button></h3>
      <p v-if="!isCandidateGroupExpanded(group.key, groupIndex)" class="group-summary">{{ group.items[0].attributeValue }}</p>
      <div v-if="isCandidateGroupExpanded(group.key, groupIndex)" :id="`candidate-group-${groupIndex}`">
      <div v-if="group.hasConflict" class="candidate-conflict-note">同一属性存在不同候选，请分别确认；系统不会替你合并冲突内容。</div>
      <div v-for="candidate in group.items" :key="candidate.id" class="candidate-item">
        <div class="candidate-copy">
          <span class="candidate-value">{{ candidate.attributeValue }}</span>
        </div>
        <div class="candidate-actions">
          <n-button size="small" secondary :aria-expanded="candidateDetailsId === candidate.id" :aria-controls="`candidate-detail-${candidate.id}`" :disabled="editingMemoryId !== null || savingMemoryId !== null" @click="toggleCandidateDetails(candidate.id)">{{ candidateDetailsId === candidate.id ? '收起详情' : '查看详情与依据' }}</n-button>
        </div>
        <div v-if="candidateDetailsId === candidate.id" :id="`candidate-detail-${candidate.id}`" class="candidate-details">
          <div class="candidate-decisions">
            <n-button secondary type="primary" @click="approveCandidate(candidate.id)">确认</n-button>
            <n-button secondary :loading="rejectingCandidateId === candidate.id" :disabled="rejectingCandidateId !== null" @click="confirmRejectCandidate(candidate)">拒绝</n-button>
          </div>
          <p class="memory-desc">{{ isSafetyState(candidate) ? '近期状态，仅供当前关怀参考。' : '确认后保存为正式记忆。' }} 已有 {{ candidate.evidenceCount || 0 }} 条依据。</p>
          <span v-if="candidateDetailsLoading" role="status">正在加载依据...</span>
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
                <article v-for="item in originalEvidence(candidateEvidence)" :key="item.id" class="memory-evidence-item">
                  <div class="memory-evidence-meta">
                    <span>{{ evidenceDateLabel(item) }}</span>
                    <span>{{ sourceTypeLabel(item) }}</span>
                  </div>
                  <p class="memory-evidence-label">{{ evidenceLabel(item) }}</p>
                  <blockquote v-if="isDiaryEvidence(item)" class="memory-evidence-text memory-evidence-quote">{{ evidenceDisplayText(item) }}</blockquote>
                  <p v-else class="memory-evidence-text">{{ evidenceDisplayText(item) }}</p>
                  <div v-if="item.sourceDiaryId || item.sourceConversationId" class="evidence-source-link">
                    <span>{{ item.sourceDiaryId ? '来自这篇日记' : '来自这段聊天' }}</span>
                    <button v-if="item.sourceDiaryId" type="button" class="evidence-diary-open" @click="openDiary(item.sourceDiaryId)">打开原日记 <span aria-hidden="true">→</span></button>
                    <button v-else type="button" class="evidence-diary-open" @click="openConversation(item.sourceConversationId)">打开聊天 <span aria-hidden="true">→</span></button>
                  </div>
                </article>
              </div>
              <div v-else class="memory-detail-muted">暂时没有可展示的原始记录。</div>
            </section>
            <section v-if="activityEvidence(candidateEvidence).length" class="memory-activity" aria-label="整理记录">
              <h4>整理记录</h4><p v-for="item in activityEvidence(candidateEvidence)" :key="item.id">{{ evidenceDateLabel(item) }} · {{ activityLabel(item) }}</p>
            </section>
          </template>
        </div>
      </div>
      </div>
      </div>
      </div>
    </section>
    <h3 class="formal-heading">已记住的内容 <small>{{ formalGroups.length }} 个属性</small></h3>
    <div v-if="memoriesLoading && !formalLoaded" class="memory-loading" style="text-align: center; padding: 40px 0;">
      <n-spin size="small" />
    </div>
    <div v-else-if="formalLoaded && store.memories.length === 0" class="memory-empty" style="text-align: center; padding: 40px 0; color: var(--color-text-light);">
      MoodCopilot 正在默默观察你，多写点日记或和 MoodCopilot 聊天吧。
    </div>
    <div v-else class="memory-list">
      <section v-for="(group, groupIndex) in formalGroups" :key="group.key" class="formal-group">
      <h3><button type="button" class="group-toggle" :aria-expanded="isFormalGroupExpanded(group.key, groupIndex)" :aria-controls="`formal-group-${groupIndex}`" :disabled="editingMemoryId !== null || savingMemoryId !== null" @click="toggleFormalGroup(group.key, groupIndex)"><span>{{ group.key }} · {{ group.items.length }} 条</span><span>{{ isFormalGroupExpanded(group.key, groupIndex) ? '收起' : '展开' }}</span></button></h3>
      <p v-if="!isFormalGroupExpanded(group.key, groupIndex)" class="group-summary">{{ group.items[0].attributeValue }}</p>
      <div v-if="isFormalGroupExpanded(group.key, groupIndex)" :id="`formal-group-${groupIndex}`">
      <article v-for="m in group.items" :key="m.id" class="memory-item" :class="{ 'memory-item-expanded': expandedMemoryIds.has(m.id) }">
        <div class="memory-content">
          <span v-if="expandedMemoryIds.has(m.id)" class="memory-current-label">正在查看</span>
          <div class="memory-value">
            <n-tag v-if="isSafetyState(m)" size="small" type="warning" style="margin-right: 6px; vertical-align: top;">近期状态</n-tag>
            <n-tag v-else-if="m.isCore" size="small" type="warning" style="margin-right: 6px;">核心</n-tag>
            {{ m.attributeValue }}
            <small v-if="isSafetyState(m)" class="memory-safety-note">仅作为你近期状态的背景参考，一段时间后会自然淡出，不会被固化为长期的核心特征。</small>
          </div>
        </div>
        <div class="memory-actions memory-summary-actions">
          <n-button size="small" secondary :aria-expanded="expandedMemoryIds.has(m.id)" :aria-controls="`memory-detail-${m.id}`" :disabled="memoryRefreshRequired || savingMemoryId !== null || editingMemoryId !== null" @click="toggleDetails(m.id)">{{ expandedMemoryIds.has(m.id) ? '收起详情' : '查看详情与依据' }}</n-button>
        </div>
        <div v-if="expandedMemoryIds.has(m.id)" :id="`memory-detail-${m.id}`" class="memory-details" role="region" :aria-labelledby="`memory-detail-heading-${m.id}`">
        <h4 :id="`memory-detail-heading-${m.id}`" class="memory-detail-heading-title">{{ m.attributeKey }} · 详情与依据</h4>
        <div v-if="editingMemoryId === m.id" class="memory-editor">
          <textarea v-model="editingMemoryValue" class="memory-textarea" rows="3" maxlength="500"
            aria-label="编辑记忆内容" :disabled="savingMemoryId !== null" />
          <n-checkbox v-if="!isSafetyStateByValue(editingMemoryValue, editingMemoryId)" v-model:checked="editingMemoryIsCore">设为核心属性</n-checkbox>
          <small v-else class="memory-safety-note">近期状态不可设为核心长期画像。</small>
        </div>
        <p v-else class="memory-detail-value">{{ m.attributeValue }}</p>
        <div class="memory-actions memory-detail-actions">
          <template v-if="editingMemoryId === m.id">
            <n-button size="small" secondary type="primary" :disabled="savingMemoryId === m.id" @click="saveMemory(m.id)">
              {{ savingMemoryId === m.id ? '...' : '保存' }}
            </n-button>
            <n-button size="small" secondary @click="cancelEditMemory">取消</n-button>
          </template>
          <template v-else>
            <n-button size="small" secondary :disabled="memoryRefreshRequired || savingMemoryId !== null" @click="startEditMemory(m)">编辑</n-button>
            <n-button size="small" secondary type="error" :disabled="memoryRefreshRequired || savingMemoryId !== null || deletingMemoryId === m.id" @click="forgetMemory(m.id)">
              {{ deletingMemoryId === m.id ? '...' : '删除' }}
            </n-button>
          </template>
        </div>
        <p v-if="m.updatedAt || m.updateTime" class="memory-updated">最近更新 {{ formatMemoryTime(m.updatedAt || m.updateTime) }}</p>
        <p v-if="m.isCore && !isSafetyState(m)" class="memory-desc">核心属性用于长期了解你；其他属性在相关话题出现时作为背景参考。</p>
        <div v-if="detailsLoadingIds.has(m.id)" class="memory-detail-muted">正在加载...</div>
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
              <article v-for="item in memoryOriginalEvidence(m.id)" :key="`e-${item.id}`" class="memory-evidence-item">
                <div class="memory-evidence-meta">
                  <span>{{ evidenceDateLabel(item) }}</span>
                  <span>{{ sourceTypeLabel(item) }}</span>
                </div>
                <p class="memory-evidence-label">{{ evidenceLabel(item) }}</p>
                <blockquote v-if="isDiaryEvidence(item)" class="memory-evidence-text memory-evidence-quote">{{ evidenceDisplayText(item) }}</blockquote>
                <p v-else class="memory-evidence-text">{{ evidenceDisplayText(item) }}</p>
                <div v-if="item.sourceDiaryId || item.sourceConversationId" class="evidence-source-link">
                  <span>{{ item.sourceDiaryId ? '来自这篇日记' : '来自这段聊天' }}</span>
                  <button v-if="item.sourceDiaryId" type="button" class="evidence-diary-open" @click="openDiary(item.sourceDiaryId)">打开原日记 <span aria-hidden="true">→</span></button>
                  <button v-else type="button" class="evidence-diary-open" @click="openConversation(item.sourceConversationId)">打开聊天 <span aria-hidden="true">→</span></button>
                </div>
              </article>
            </div>
            <div v-else class="memory-detail-muted">暂时没有可展示的原始记录。</div>
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
      </article>
      </div>
      </section>
    </div>
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
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NSpin, NTag, NCheckbox, useDialog, NModal } from 'naive-ui'
import { memoryApi } from '../../api'
import { logWarn } from '../../utils/logger'
import { useConsolidationStore } from '../../stores/consolidation'

interface MemoryItem {
  id: number
  attributeKey: string
  attributeValue: string
  isCore?: boolean
  sourceDiaryId?: number | null
  sourceConversationId?: number | null
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
function isFormalGroupExpanded(key: string, index: number): boolean { return formalGroupState.value[key] ?? index < 2 }
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


async function toggleCandidateDetails(id: number) {
  if (editingMemoryId.value !== null || savingMemoryId.value !== null) return
  candidateDetailsRequest++
  if (candidateDetailsId.value === id) {
    candidateDetailsId.value = null
    candidateEvidence.value = []
    return
  }
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

async function toggleDetails(id: number) {
  if (editingMemoryId.value !== null || savingMemoryId.value !== null) return
  if (expandedMemoryIds.value.has(id)) {
    expandedMemoryIds.value = new Set()
    return
  }

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
  } catch (e) {
    logWarn('memory', '删除记忆失败', id, e)
  } finally {
    deletingMemoryId.value = null
  }
}

function startEditMemory(m: MemoryItem) {
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
.tab-content {
  padding: 16px 0;
  min-height: 400px;
}
.memory-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0 0 16px 0;
  line-height: 1.5;
}
.memory-action-error { color: var(--color-error); font-size: 13px; line-height: 1.6; }
.memory-textarea {
  display: block;
  box-sizing: border-box;
  width: 100%;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  color: var(--color-text);
  background: var(--color-surface);
  font: inherit;
  line-height: 1.6;
  resize: none;
  overflow-y: auto;
}
.memory-textarea:focus { outline: 2px solid var(--color-primary); outline-offset: 1px; }
.memory-textarea:disabled { color: var(--color-text-muted); background: var(--color-surface-soft); }
.candidate-dialog-body { margin-top: 16px; }
.candidate-dialog-body > p { margin: 0 0 8px; color: var(--color-text-secondary); }
.candidate-dialog-body > .memory-action-error { color: var(--color-error); }
.candidate-section { margin: 18px 0 22px; padding: 16px; border: 1px solid var(--color-border); border-left: 3px solid var(--color-primary); background: color-mix(in srgb, var(--color-primary) 4%, transparent); }
.candidate-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; margin-bottom: 14px; color: var(--color-text); }
.candidate-head-main { min-width: 0; }
.candidate-title-row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.candidate-title { font-size: 15px; font-weight: 700; }
.candidate-count { display: inline-flex; align-items: center; min-height: 22px; padding: 0 8px; border: 1px solid color-mix(in srgb, var(--color-primary) 32%, var(--color-border)); border-radius: var(--radius-full); background: color-mix(in srgb, var(--color-primary) 12%, var(--color-surface)); color: var(--color-primary); font-size: 12px; font-weight: 700; line-height: 1; }
.candidate-head small, .candidate-copy small { display: block; color: var(--color-text-secondary); font-size: 12px; font-weight: 400; line-height: 1.5; }
.candidate-head-main > small { margin-top: 5px; }
.candidate-summary { flex: 0 0 auto; color: var(--color-text-muted); font-size: 12px; line-height: 1.5; text-align: right; }
.candidate-group { display: flex; flex-direction: column; gap: 8px; }
.candidate-group + .candidate-group { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--color-border); }
.candidate-conflict-note { padding: 2px 0 1px; color: var(--color-warning); font-size: 12px; line-height: 1.5; }
.candidate-item { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 14px; padding: 13px 14px; border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-surface); }
.candidate-details { flex-basis: 100%; width: 100%; margin-top: 10px; padding: 10px 0 0; border-top: 1px dashed var(--color-border); color: var(--color-text-secondary); font-size: 12px; }
.candidate-copy { display: flex; min-width: 0; flex: 1 1 240px; flex-direction: column; gap: 4px; }
.candidate-label-row { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.candidate-copy strong { color: var(--color-text); font-size: 13px; }
.candidate-value { color: var(--color-text); font-size: 14px; font-weight: 600; line-height: 1.55; }
.candidate-evidence-count { color: var(--color-text-muted); font-size: 12px; line-height: 1.4; }
.candidate-actions { display: flex; flex-shrink: 0; gap: 6px; }
.memory-details { grid-column: 1 / -1; min-width: 0; padding-top: 16px; border-top: 1px solid var(--color-border); color: var(--color-text-secondary); font-size: 12px; line-height: 1.5; }
.memory-detail-heading-title { margin: 0 0 12px; color: var(--color-text); font-family: var(--font-serif); font-size: 16px; font-weight: 600; }
.memory-detail-value { max-width: 72ch; margin: 0 0 16px; color: var(--color-text); font-size: 15px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.memory-editor { display: flex; flex-direction: column; gap: 8px; }
.memory-current-label { color: var(--color-primary); font-size: 12px; font-weight: 600; }
.memory-detail-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.memory-detail-title { color: var(--color-text); font-weight: 700; margin-bottom: 3px; }
.memory-detail-hint { margin: 0; color: var(--color-text-secondary); font-size: 12px; line-height: 1.5; }
.memory-detail-count { flex: 0 0 auto; color: var(--color-text-muted); font-size: 12px; }
.memory-detail-line { display: flex; flex-direction: column; gap: 2px; padding: 5px 0; }
.memory-detail-line small { color: var(--color-text); white-space: pre-wrap; }
.memory-evidence-section { padding-bottom: 12px; }
.memory-evidence-list { display: flex; flex-direction: column; margin-top: 12px; border-top: 1px solid var(--color-border); }
.memory-evidence-item { display: grid; grid-template-columns: minmax(82px, 0.22fr) minmax(0, 1fr); column-gap: 16px; padding: 12px 0; border-bottom: 1px solid var(--color-border); }
.memory-evidence-meta { display: flex; flex-direction: column; align-items: flex-start; gap: 3px; color: var(--color-text-secondary); font-size: 11px; line-height: 1.35; }
.memory-evidence-meta span:first-child { color: var(--color-text); font-variant-numeric: tabular-nums; font-weight: 600; }
.memory-evidence-meta span + span { color: var(--color-text-muted); }
.memory-evidence-label { margin: 0 0 4px; color: var(--color-text-muted); font-size: 11px; font-weight: 600; letter-spacing: 0; line-height: 1.4; }
.memory-evidence-text { margin: 0 0 6px; color: var(--color-text); font-size: 13px; line-height: 1.55; }
.memory-evidence-label, .memory-evidence-text { grid-column: 2; }
.memory-evidence-quote { position: relative; margin-left: 2px; padding: 9px 12px 9px 14px; border-left: 3px solid color-mix(in srgb, var(--color-primary) 55%, var(--color-border)); border-radius: 0 var(--radius-sm) var(--radius-sm) 0; background: color-mix(in srgb, var(--color-primary) 7%, var(--color-surface)); color: var(--color-text-secondary); quotes: '"' '"'; }
.memory-evidence-quote::before { content: open-quote; color: var(--color-primary); font-size: 18px; font-weight: 700; line-height: 0; vertical-align: -3px; }
.memory-evidence-quote::after { content: close-quote; color: var(--color-primary); font-size: 18px; font-weight: 700; line-height: 0; vertical-align: -3px; }
.evidence-source-link { display: flex; grid-column: 2; align-items: center; gap: 8px; min-width: 0; color: var(--color-text-secondary); }
.evidence-source-link > span { min-width: 0; overflow: hidden; color: var(--color-text-secondary); text-overflow: ellipsis; white-space: nowrap; }
.evidence-diary-open, .memory-history-toggle { display: inline-flex; align-items: center; gap: 4px; border: 0; background: transparent; color: var(--color-primary); cursor: pointer; font: inherit; font-size: 12px; font-weight: 600; line-height: 1.4; }
.evidence-diary-open:hover, .memory-history-toggle:hover { color: var(--color-primary-hover); text-decoration: underline; text-underline-offset: 3px; }
.evidence-diary-open:focus-visible, .memory-history-toggle:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 3px; }
.memory-history-section { padding-top: 10px; border-top: 1px solid var(--color-border); }
.memory-history-toggle { padding: 0; }
.memory-history-list { display: flex; flex-direction: column; margin-top: 9px; border-top: 1px solid var(--color-border); }
.memory-history-item { padding: 9px 0; border-bottom: 1px solid var(--color-border); }
.memory-history-value { margin: 0 0 3px; color: var(--color-text); font-size: 13px; line-height: 1.5; }
.memory-history-meta { color: var(--color-text-secondary); font-size: 11px; }
.memory-detail-muted { color: var(--color-text-light); }
.memory-detail-error { display: flex; align-items: center; gap: 8px; color: var(--color-text-secondary); }
.memory-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 10px 0;
}
.memory-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 16px;
  padding: 16px 0;
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--color-border);
  border-radius: 0;
  align-self: start;
  width: 100%;
  box-sizing: border-box;
}
.memory-content {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.memory-key {
  font-family: var(--font-serif);
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--color-primary);
}
.memory-value {
  font-size: 0.95rem;
  color: var(--color-text);
  line-height: 1.6;
  white-space: pre-wrap;
}
.memory-updated { color: var(--color-text-light); font-size: 12px; }
.memory-safety-note { display: block; margin-top: 8px; color: var(--color-text-secondary); font-size: 12px; font-weight: 400; line-height: 1.5; }
.memory-edit-input {
  margin-top: 8px;
}
.memory-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
  justify-content: flex-end;
}
.memory-detail-actions { justify-content: flex-start; margin: 16px 0; }
.memory-item-expanded { border-bottom-color: var(--color-primary); }
.memory-actions .n-button {
  font-size: 12px;
  padding: 0 12px;
  border-radius: var(--radius-full);
}

.profile-header-controls { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.profile-header-title { margin: 0; font-weight: bold; }
.mb-12 { margin-bottom: 12px; }
.profile-filter-row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.w-140 { width: 140px; }
.profile-tab-content { min-height: 400px; display: flex; flex-direction: column; }
.flex-center-full { flex: 1; display: flex; justify-content: center; align-items: center; }
.flex-wrap-gap-8 { display: flex; flex-wrap: wrap; gap: 8px; }
.ml-6 { margin-left: 6px; }
.empty-profile-state { text-align: center; color: var(--color-text-light); padding: 40px 20px; }
.profile-details-section { margin-top: 24px; padding-top: 16px; border-top: 1px dashed var(--color-border); }
.details-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.details-title { font-size: 16px; font-weight: bold; margin: 0; color: var(--color-text); }
.profile-detail-card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: 8px; padding: 12px; }
.details-content-text { font-size: 14px; color: var(--color-text-secondary); line-height: 1.6; white-space: pre-wrap; }
.details-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 12px; font-size: 12px; color: var(--color-text-light); }
.graph-preview-modal { width: 600px; max-width: 90vw; }
.preview-desc { margin-top: 0; color: var(--color-text-secondary); font-size: 13px; }
.preview-list-panel { max-height: 35vh; overflow-y: auto; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: 6px; padding: 12px; margin-bottom: 16px; }
.preview-item { margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--color-border); font-size: 13px; color: var(--color-text); }
.preview-item-key { font-weight: bold; color: var(--color-primary); margin-bottom: 4px; }
.preview-item-value { color: var(--color-text-secondary); line-height: 1.5; }
.flex-end-gap-12 { display: flex; gap: 12px; justify-content: flex-end; }

.memory-overview { color: var(--color-text); font-weight: 600; font-variant-numeric: tabular-nums; }
.formal-heading { font-family: var(--font-serif); color: var(--color-text); margin: 24px 0 8px; }
.formal-heading small { color: var(--color-text-secondary); font: 400 13px var(--font-sans); }
.formal-group { width: 100%; min-width: 0; padding: 12px 0; border-top: 1px solid var(--color-border-strong); }
.formal-group h3, .candidate-group h3 { margin: 0; font: inherit; }
.group-toggle, .candidate-toggle { display: flex; justify-content: space-between; align-items: center; gap: 16px; width: 100%; min-height: 44px; padding: 8px 0; border: none; background: transparent; color: var(--color-text); text-align: left; font: inherit; font-weight: 600; cursor: pointer; }
.group-toggle > span:last-child, .candidate-toggle > span:last-child { color: var(--color-primary); white-space: nowrap; font-size: 13px; }
.candidate-toggle-copy { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.candidate-toggle-copy small { color: var(--color-text-secondary); font-size: 12px; font-weight: 400; line-height: 1.5; }
.group-toggle:focus-visible, .candidate-toggle:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 3px; }
.group-toggle:disabled { cursor: not-allowed; color: var(--color-disabled); }
.group-toggle:disabled > span:last-child { color: var(--color-disabled); }
.group-summary { margin: 4px 0 8px; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; color: var(--color-text-secondary); font-size: 14px; line-height: 1.6; }
.memory-value { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; }
.memory-item, .candidate-value, .group-toggle, .candidate-toggle, .group-summary, .memory-evidence-text { overflow-wrap: anywhere; }
.candidate-item + .candidate-item { margin-top: 8px; }
.memory-activity { border-top: 1px solid var(--color-border); padding: 12px 0; color: var(--color-text-secondary); }
.memory-activity h4 { margin: 0 0 8px; color: var(--color-text); }
.memory-activity p { margin: 6px 0; }
:deep(.n-button) { min-height: 44px; }
.memory-actions { flex-wrap: wrap; }
.evidence-diary-open, .memory-history-toggle { min-height: 44px; }

@media (max-width: 640px) {
  .memory-item { grid-template-columns: minmax(0, 1fr); }
  .memory-summary-actions { justify-content: flex-start; }
  .candidate-head { align-items: flex-start; flex-direction: column; gap: 6px; }
  .candidate-summary { text-align: left; }
  .candidate-actions { width: 100%; flex-wrap: wrap; }
  .memory-evidence-item { grid-template-columns: 1fr; row-gap: 6px; }
  .memory-evidence-meta { flex-direction: row; align-items: center; gap: 8px; }
  .memory-evidence-meta span + span { padding-left: 8px; border-left: 1px solid var(--color-border-strong); }
  .memory-evidence-label, .memory-evidence-text { grid-column: 1; }
  .evidence-source-link { grid-column: 1; }
  .memory-detail-heading { flex-direction: column; gap: 4px; }
}

</style>

