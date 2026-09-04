<template>
  <div class="tab-content">
    <div class="section-head" style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 16px;">
      <div style="display: flex; align-items: center; gap: 8px; flex-shrink: 0;">
        <p class="settings-label" style="margin: 0; font-weight: bold; white-space: nowrap;">我的记忆</p>
      </div>
      <n-button size="small" secondary type="primary" :loading="store.consolidatingMemory" @click="store.consolidateMemories()">
        ✨ 智能整理记忆
      </n-button>
      <n-button size="small" secondary :loading="memoriesLoading" @click="loadMemories">
         刷新
      </n-button>
    </div>
    <p class="memory-desc">这里保存 MoodCopilot 从日记和聊天中整理出的个人记忆。近期状态仅用于当前关怀参考，不会作为核心长期画像。</p>
    <section v-if="candidates.length" class="candidate-section" aria-label="待确认记忆">
      <div class="candidate-head">
        <div class="candidate-head-main">
          <div class="candidate-title-row">
            <span class="candidate-title">待确认的记忆</span>
            <span class="candidate-count">{{ candidates.length }} 条</span>
          </div>
          <small>这些内容还没有进入正式画像，请确认哪些值得长期保留。</small>
        </div>
        <span class="candidate-summary">
          {{ candidateGroups.length }} 个属性<template v-if="candidateConflictGroupCount"> · {{ candidateConflictGroupCount }} 个属性有不同候选</template>
        </span>
      </div>
      <div v-for="group in candidateGroups" :key="group.key" class="candidate-group">
      <div v-if="group.hasConflict" class="candidate-conflict-note">同一属性存在不同候选，请分别确认；系统不会替你合并冲突内容。</div>
      <div v-for="candidate in group.items" :key="candidate.id" class="candidate-item">
        <div class="candidate-copy">
          <div class="candidate-label-row">
            <strong>{{ candidate.attributeKey }}</strong>
            <n-tag v-if="isSafetyState(candidate)" size="small" type="warning">近期状态</n-tag>
          </div>
          <span class="candidate-value">{{ candidate.attributeValue }}</span>
          <small>{{ isSafetyState(candidate) ? '这是需要关注的近期状态，不属于核心长期画像。' : '确认后，这条内容才会进入正式画像。' }}</small>
          <span class="candidate-evidence-count">已有 {{ candidate.evidenceCount || 0 }} 条依据</span>
        </div>
        <div class="candidate-actions">
          <n-button size="small" secondary @click="toggleCandidateDetails(candidate.id)">{{ candidateDetailsId === candidate.id ? '收起依据' : '查看依据' }}</n-button>
          <n-button size="small" secondary type="primary" @click="approveCandidate(candidate.id)">确认</n-button>
          <n-button size="small" secondary :loading="rejectingCandidateId === candidate.id" :disabled="rejectingCandidateId !== null" @click="confirmRejectCandidate(candidate)">拒绝</n-button>
        </div>
        <div v-if="candidateDetailsId === candidate.id" class="candidate-details">
          <span v-if="candidateDetailsLoading">正在加载依据...</span>
          <template v-else>
            <section class="memory-evidence-section">
              <div class="memory-detail-heading">
                <div>
                  <div class="memory-detail-title">形成依据</div>
                  <p class="memory-detail-hint">这些是帮助 AI 形成这条候选记忆的原始记录，可打开来源进行核对。</p>
                </div>
                <span v-if="candidateEvidence.length" class="memory-detail-count">{{ candidateEvidence.length }} 条记录</span>
              </div>
              <div v-if="candidateEvidence.length" class="memory-evidence-list">
                <article v-for="item in candidateEvidence" :key="item.id" class="memory-evidence-item">
                  <div class="memory-evidence-meta">
                    <span>{{ evidenceDateLabel(item) }}</span>
                    <span>{{ sourceTypeLabel(item) }}</span>
                  </div>
                  <p class="memory-evidence-label">{{ isDiaryEvidence(item) ? '日记摘录' : '聊天内容' }}</p>
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
          </template>
        </div>
      </div>
      </div>
    </section>
    <div v-if="memoriesLoading" class="memory-loading" style="text-align: center; padding: 40px 0;">
      <n-spin size="small" />
    </div>
    <div v-else-if="store.memories.length === 0" class="memory-empty" style="text-align: center; padding: 40px 0; color: var(--color-text-light);">
      MoodCopilot 正在默默观察你，多写点日记或和 MoodCopilot 聊天吧。
    </div>
    <div v-else class="memory-list">
      <div
        v-for="(m, index) in store.memories"
        :key="m.id"
        class="memory-item"
        v-motion
        :initial="{ opacity: 0, y: 30 }"
        :enter="{ opacity: 1, y: 0, transition: { type: 'spring', stiffness: 250, damping: 25, delay: index * 50 } }"
      >
        <div class="memory-content">
          <span class="memory-key">{{ m.attributeKey }}</span>
          <n-tag v-if="isRecentlyUpdated(m)" size="small" type="success" style="margin-left: 8px; vertical-align: text-bottom;">✨ 近期变动</n-tag>
          <template v-if="editingMemoryId === m.id">
            <div style="display: flex; flex-direction: column; gap: 8px;">
              <n-input
                v-model:value="editingMemoryValue"
                size="small"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 6 }"
                class="memory-edit-input"
                :maxlength="500"
              />
              <n-checkbox v-if="!isSafetyStateByValue(editingMemoryValue, editingMemoryId)" v-model:checked="editingMemoryIsCore">设为核心属性</n-checkbox>
              <small v-else class="memory-safety-note">近期状态不可设为核心长期画像。</small>
            </div>
          </template>
          <div v-else class="memory-value">
            <n-tag v-if="isSafetyState(m)" size="small" type="warning" style="margin-right: 6px; vertical-align: top;">近期状态</n-tag>
            <n-tag v-else-if="m.isCore" size="small" type="warning" style="margin-right: 6px; vertical-align: top;">
              核心
              <n-popover trigger="hover" placement="top" style="max-width: 280px; font-size: 13px;">
                <template #trigger>
                  <span style="display: inline-block; width: 14px; text-align: center; cursor: pointer; color: var(--color-text-secondary); font-weight: bold; margin-left: 2px;">ⓘ</span>
                </template>
                <strong>核心属性</strong><br/>决定了 MoodCopilot 了解你底层性格、长期偏好和沟通基调的关键。这些信息会被常驻注入到与你的每一次对话中，让 MoodCopilot 能够始终保持对你最深刻的理解。而“非核心属性”则是辅助背景，在提到相关话题时才会被回忆起来。
              </n-popover>
            </n-tag>
            {{ m.attributeValue }}
            <small v-if="isSafetyState(m)" class="memory-safety-note">仅作为近期关怀参考，不是诊断，也不会作为核心画像长期注入。</small>
          </div>
          <small v-if="m.updatedAt || m.updateTime" class="memory-updated">最近更新 {{ formatMemoryTime(m.updatedAt || m.updateTime) }}</small>
        </div>
        <div class="memory-actions">
          <template v-if="editingMemoryId === m.id">
            <n-button size="small" secondary type="primary" :disabled="savingMemoryId === m.id" @click="saveMemory(m.id)">
              {{ savingMemoryId === m.id ? '...' : '保存' }}
            </n-button>
            <n-button size="small" secondary @click="cancelEditMemory">取消</n-button>
          </template>
          <template v-else>
            <n-button size="small" secondary @click="toggleDetails(m.id)">{{ expandedMemoryIds.has(m.id) ? '收起形成依据' : '查看形成依据' }}</n-button>
            <n-button size="small" secondary @click="startEditMemory(m)">编辑</n-button>
            <n-button size="small" secondary type="error" :disabled="deletingMemoryId === m.id" @click="forgetMemory(m.id)">
              {{ deletingMemoryId === m.id ? '...' : '删除' }}
            </n-button>
          </template>
        </div>
        <div v-if="expandedMemoryIds.has(m.id)" class="memory-details">
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
              <span v-if="memoryEvidenceById[m.id]?.length" class="memory-detail-count">{{ memoryEvidenceById[m.id].length }} 条记录</span>
            </div>
            <div v-if="memoryEvidenceById[m.id]?.length" class="memory-evidence-list">
              <article v-for="item in memoryEvidenceById[m.id]" :key="`e-${item.id}`" class="memory-evidence-item">
                <div class="memory-evidence-meta">
                  <span>{{ evidenceDateLabel(item) }}</span>
                  <span>{{ sourceTypeLabel(item) }}</span>
                </div>
                <p class="memory-evidence-label">{{ isDiaryEvidence(item) ? '日记摘录' : '聊天内容' }}</p>
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NSpin, NInput, NTag, NCheckbox, NPopover, useDialog } from 'naive-ui'
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
const savingMemoryId = ref<number | null>(null)
const candidates = ref<any[]>([])
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
  memoriesLoading.value = true
  await Promise.all([store.loadMemories(), loadCandidates()])
  memoriesLoading.value = false
}

async function loadCandidates() {
  try {
    const response = await memoryApi.getCandidates()
    candidates.value = response.data.data || []
  } catch (e) {
    logWarn('memory', '加载候选记忆失败', e)
    candidates.value = []
  }
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

function evidenceDisplayText(item: any): string {
  if (isDiaryEvidence(item)) {
    return item.sourceDiaryPreview
      ? diarySourceExcerpt(item.sourceDiaryPreview)
      : '原始日记内容暂不可查看'
  }
  return item.evidenceText?.trim() || '暂无可展示的原始记录'
}

function conversationIdsFor(item: any): number[] {
  return [...new Set([...(item.sourceConversationIds || []), item.sourceConversationId].filter(Boolean).map(Number))]
}

function sourceTypeLabel(item: any): string {
  if (isDiaryEvidence(item)) return '来自日记'
  if (conversationIdsFor(item).length) return '来自聊天'
  if (item.sourceType === 'USER_ACTION') return '用户整理'
  if (item.sourceType === 'explicit') return '用户确认'
  if (item.sourceType === 'system') return '系统整理'
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
  await memoryApi.approveCandidate(id)
  await loadMemories()
  resetMemoryDetails()
  window.$message?.success('记忆已确认')
}

function confirmRejectCandidate(candidate: any) {
  dialog.warning({
    title: '拒绝这条候选记忆？',
    content: `拒绝后，这条内容不会进入正式画像，但相关依据和历史记录仍会保留。系统会暂时记住你不希望 AI 自动添加这条内容，避免近期再次提出；之后你明确表达新的事实时，仍可以重新建立。${candidate?.attributeKey ? `\n\n当前候选：${candidate.attributeKey}` : ''}`,
    positiveText: '确认拒绝',
    negativeText: '取消',
    positiveButtonProps: { type: 'warning' },
    onPositiveClick: () => rejectCandidate(candidate.id),
  })
}

async function rejectCandidate(id: number) {
  if (rejectingCandidateId.value !== null) return
  rejectingCandidateId.value = id
  try {
    await memoryApi.rejectCandidate(id)
    candidates.value = candidates.value.filter(candidate => candidate.id !== id)
    if (candidateDetailsId.value === id) {
      candidateDetailsId.value = null
      candidateEvidence.value = []
    }
    window.$message?.success('已拒绝，这条内容近期不会再被自动加入')
  } catch (e) {
    logWarn('memory', '拒绝候选记忆失败', id, e)
  } finally {
    rejectingCandidateId.value = null
  }
}

async function toggleCandidateDetails(id: number) {
  if (candidateDetailsId.value === id) {
    candidateDetailsId.value = null
    candidateEvidence.value = []
    return
  }
  candidateDetailsId.value = id
  candidateDetailsLoading.value = true
  candidateEvidence.value = []
  try {
    const response = await memoryApi.getCandidateEvidence(id)
    candidateEvidence.value = response.data.data || []
  } catch (e) {
    logWarn('memory', '加载候选依据失败', id, e)
  } finally {
    candidateDetailsLoading.value = false
  }
}

async function toggleDetails(id: number) {
  if (expandedMemoryIds.value.has(id)) {
    const next = new Set(expandedMemoryIds.value)
    next.delete(id)
    expandedMemoryIds.value = next
    return
  }

  expandedMemoryIds.value = new Set(expandedMemoryIds.value).add(id)
  if (memoryDetailsLoadedIds.value.has(id)) return
  await loadMemoryDetails(id)
}

async function loadMemoryDetails(id: number) {
  detailsLoadingIds.value = new Set(detailsLoadingIds.value).add(id)
  const nextErrors = new Set(memoryDetailsErrorIds.value)
  nextErrors.delete(id)
  memoryDetailsErrorIds.value = nextErrors
  try {
    const [evidence, history] = await Promise.all([memoryApi.getEvidence(id), memoryApi.getHistory(id)])
    memoryEvidenceById.value = { ...memoryEvidenceById.value, [id]: evidence.data.data || [] }
    memoryHistoryById.value = { ...memoryHistoryById.value, [id]: history.data.data || [] }
    memoryDetailsLoadedIds.value = new Set(memoryDetailsLoadedIds.value).add(id)
  } catch (e) {
    memoryDetailsErrorIds.value = new Set(memoryDetailsErrorIds.value).add(id)
    logWarn('memory', '加载记忆依据失败', id, e)
  } finally {
    const nextLoading = new Set(detailsLoadingIds.value)
    nextLoading.delete(id)
    detailsLoadingIds.value = nextLoading
  }
}

function toggleHistory(id: number) {
  const next = new Set(expandedHistoryIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedHistoryIds.value = next
}

function resetMemoryDetails() {
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

function isRecentlyUpdated(item: any) {
  if (!item.updateTime) return false
  const ut = new Date(item.updateTime).getTime()
  // within 5 minutes
  return Date.now() - ut < 5 * 60 * 1000
}

function forgetMemory(id: number) {
  const memory = store.memories.find((item: any) => item.id === id)
  dialog.warning({
    title: '删除这条记忆？',
    content: `删除后，这条记忆会从当前画像中移除，但历史记录仍会保留。系统会暂时记住你不想保留这条内容，避免 AI 很快又自动加回来；你之后明确表达新的事实时，仍可重新建立。${memory?.attributeKey ? `\n\n当前记忆：${memory.attributeKey}` : ''}`,
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
  if (!value) return

  savingMemoryId.value = id
  try {
    await memoryApi.update(id, { 
      attributeValue: value,
      isCore: isSafetyStateByValue(value, id) ? false : editingMemoryIsCore.value
    })
    const idx = store.memories.findIndex((m: any) => m.id === id)
    if (idx !== -1) {
      store.memories[idx] = { 
        ...store.memories[idx], 
        attributeValue: value,
        isCore: isSafetyStateByValue(value, id) ? false : editingMemoryIsCore.value
      }
    }
    editingMemoryId.value = null
    editingMemoryValue.value = ''
  } catch (e) {
    logWarn('memory', '保存记忆失败', id, e)
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
.memory-details { width: 100%; margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--color-border); color: var(--color-text-secondary); font-size: 12px; line-height: 1.5; }
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
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-4);
  padding: 10px 0;
  align-items: start;
}
.memory-item {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 16px;
  padding: 24px;
  background: var(--color-surface);
  border: none;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
  transition: all var(--duration-normal) var(--ease-out);
  align-self: start;
  width: 100%;
  box-sizing: border-box;
}
.memory-item:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}
@media (prefers-color-scheme: dark) {
  .memory-item:hover {
    box-shadow: var(--shadow-xl);
  }
}
.memory-content {
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
  border-top: 1px dashed color-mix(in oklab, var(--color-border) 40%, transparent);
  padding-top: 12px;
}
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

@media (max-width: 640px) {
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
