<template>
  <main class="app-shell chat-shell">
    <AppHeader />

    <div class="chat-layout">
      <ChatSidebar
        :conversations="conversations"
        :active-conv-id="activeConvId"
        :creating-conversation="creatingConversation"
        @create="createConversation"
        @select="selectConversation"
        @delete="deleteConversation"
        @rename="renameConversation"
      />

      <!-- 聊天区域 -->
      <div class="chat-window">
        <!-- Magazine Style Features Index — admin only -->
        <div v-if="authStore.isAdmin" class="chat-features-index">
          <span class="index-label">INDEX //</span>
          <router-link to="/report" class="index-link">
            情绪报告 <span class="en-sub">Report</span> <span class="link-arrow">↗</span>
          </router-link>
          <span class="index-separator">·</span>
          <router-link to="/ai-memory" class="index-link">
            记忆中心 <span class="en-sub">Memory</span> <span class="link-arrow">↗</span>
          </router-link>
          <span class="index-separator">·</span>
          <router-link to="/life-events" class="index-link">
            重要事件 <span class="en-sub">Threads</span> <span class="link-arrow">↗</span>
          </router-link>
          <span class="index-separator">·</span>
          <router-link to="/life-chapters" class="index-link">
            时光画卷 <span class="en-sub">Chapters</span> <span class="link-arrow">↗</span>
          </router-link>
        </div>

        <div class="chat-mobile-conv">
          <button class="ds-mobile-icon-btn" @click="mobileDrawerOpen = true" aria-label="打开历史记录">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>
          </button>
          <div class="chat-mobile-conv-title">{{ activeConvTitle }}</div>
          <button class="ds-mobile-icon-btn" @click="createConversation" :disabled="creatingConversation" aria-label="新建对话">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
          </button>
        </div>

        <!-- 移动端历史对话抽屉 -->
        <n-drawer v-model:show="mobileDrawerOpen" width="280" placement="left" :auto-focus="false">
          <n-drawer-content title="历史对话" class="ds-mobile-drawer-content">
            <div class="ds-mobile-drawer-search">
              <n-input v-model:value="searchConvText" placeholder="搜索对话" clearable>
                <template #prefix>
                  <span style="opacity: 0.5;">🔍</span>
                </template>
              </n-input>
            </div>
            <div class="ds-mobile-conv-list">
              <template v-for="group in filteredConversationGroups" :key="group.label">
                <div class="ds-mobile-conv-group-label">{{ group.label }}</div>
                <div
                  v-for="conv in group.items"
                  :key="conv.id"
                  class="ds-mobile-conv-item"
                  :class="{ active: conv.id === activeConvId }"
                  @click="selectConversation(conv.id); mobileDrawerOpen = false;"
                >
                  <span class="ds-mobile-conv-title-text">{{ conv.title || '新对话' }}</span>
                  <n-dropdown
                    trigger="click"
                    :options="conversationOptions"
                    @select="(key: string) => { if (key === 'rename') renameConversation(conv.id, conv.title || ''); else deleteConversation(conv.id) }"
                    @click.stop
                  >
                    <button class="ds-mobile-conv-more-btn" @click.stop title="更多操作">
                      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/></svg>
                    </button>
                  </n-dropdown>
                </div>
              </template>
              <div v-if="filteredConversations.length === 0" class="ds-mobile-conv-empty">
                没有找到匹配的对话
              </div>
            </div>
          </n-drawer-content>
        </n-drawer>

        <Teleport to="body">
          <div v-if="personaOpen" class="chat-persona-modal" @click.self="personaOpen = false">
            <section class="chat-persona-panel" role="dialog" aria-modal="true" aria-labelledby="chat-persona-title">
              <button type="button" class="chat-persona-close" aria-label="关闭本会话风格设置" @click="personaOpen = false">×</button>
            <div class="chat-persona-heading">
              <strong id="chat-persona-title">本会话风格</strong>
              <strong>{{ activeConvId ? '只影响这一场对话' : '新对话风格' }}</strong>
              <span>{{ activeConvId
                ? (conversationPersonaUsesGlobal ? '当前正在使用全局设置' : '当前会话正在使用独立设置')
                : '当前正在使用全局设置，应用后会保存为本会话设置' }}</span>
            </div>
            <label class="chat-persona-label" for="chat-persona-role">互动身份</label>
            <select id="chat-persona-role" v-model="conversationPersona.role" class="chat-persona-select">
              <option v-for="option in personaRoleOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <span class="chat-persona-label">预设语气</span>
            <div class="chat-persona-options">
              <button v-for="option in personaToneOptions" :key="option.value" type="button"
                :class="['chat-persona-option', { active: conversationPersona.tone.includes(option.value) }]"
                @click="toggleConversationTone(option.value)">{{ option.label }}</button>
            </div>
            <label class="chat-persona-label" for="chat-persona-custom-tone">自定义语气</label>
            <input id="chat-persona-custom-tone" v-model="conversationPersona.customTone" class="chat-persona-input"
              maxlength="160" placeholder="例如：冷静务实，像可靠的前辈" />
            <span class="chat-persona-help">用一句话描述希望听起来怎样，只影响表达风格。</span>
            <span class="chat-persona-label">回答方式</span>
            <div class="chat-persona-options">
              <button v-for="option in personaBehaviorOptions" :key="option.value" type="button"
                :class="['chat-persona-option', { active: conversationPersona.behaviorFlags.includes(option.value) }]"
                @click="toggleConversationBehavior(option.value)">{{ option.label }}</button>
            </div>
            <label class="chat-persona-label" for="chat-persona-response-style">自定义回答方式</label>
            <textarea id="chat-persona-response-style" v-model="conversationPersona.customResponseStyle" class="chat-persona-input chat-persona-textarea"
              maxlength="800" placeholder="例如：按“事实、判断、建议”分开说明，并明确标注不确定信息"></textarea>
            <span class="chat-persona-help">只影响回答组织方式。</span>
            <div class="chat-persona-actions">
              <button type="button" class="chat-persona-save" :disabled="personaSaving" @click="saveConversationPersona">
                {{ personaSaving ? '保存中' : '应用到本会话' }}
              </button>
              <button type="button" class="chat-persona-reset" :disabled="personaSaving" @click="resetConversationPersona">恢复全局设置</button>
            </div>
            <p v-if="personaMessage" class="chat-persona-message">{{ personaMessage }}</p>
            </section>
          </div>
        </Teleport>

        <div class="chat-messages" ref="msgBox" @scroll="handleScroll">
          <div v-if="messages.length === 0" class="chat-empty">
            <h2 class="chat-header-title">MoodCopilot</h2>
            <p class="chat-subtitle">可以聊聊最近的心情，分享你的故事和想法</p>
            <div v-if="quickStarters[0]?.eventId" class="event-checkin-note">
              <span class="event-checkin-kicker">今天想起一件事</span>
              <span>{{ quickStarters[0].greeting || quickStarters[0].text }}</span>
            </div>
            
            <div v-if="quickStartersLoading" class="chat-quick-starters skeleton-starters">
              <div v-for="i in 4" :key="i" class="quick-starter-card skeleton-card">
                <div class="skeleton-icon"></div>
                <div class="skeleton-text"></div>
              </div>
            </div>
            <div v-else class="chat-quick-starters">
              <button 
                v-for="(item, idx) in quickStarters" 
                :key="idx" 
                class="quick-starter-card"
                type="button"
                @click="useQuickStarter(item.text, item.eventId)"
              >
                <span class="starter-icon">{{ item.icon }}</span>
                <span class="starter-text">{{ item.text }}</span>
              </button>
            </div>
          </div>

          <ChatMessageItem
            v-for="msg in messages"
            :key="msg.id"
            :msg="msg"
            :user-avatar="authStore.avatar"
            :user-initial="userInitial"
            :user-name="authStore.displayName || '我'"
            @go-diary="goToDiary"
            @quote="handleQuote"
          />

          <div v-if="isCompressing" class="msg-item ai animate-fade-in">
            <div class="msg-avatar ai-avatar">
              <svg class="ai-avatar-icon" xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64" fill="none">
                <rect x="14" y="11" width="36" height="42" rx="8" stroke="currentColor" stroke-width="4"/>
                <path d="M24 11V53" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
                <path d="M32 38C26.6 33.8 24 31.1 24 27.5C24 24.95 26 23 28.6 23C30.1 23 31.55 23.68 32.5 24.76C33.45 23.68 34.9 23 36.4 23C39 23 41 24.95 41 27.5C41 31.1 38.4 33.8 33 38L32.5 38.4L32 38Z" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
            <div class="msg-wrapper">
              <div class="chat-bubble chat-ai thinking-bubble">
                <div class="thinking-header">
                  <span class="sparkle-icon">✨</span>
                  <span class="thinking-text">{{ compressingMessage || '正在优化对话上下文...' }}</span>
                </div>
                <div class="thinking-dots-loader">
                  <span class="dot animate-bounce" style="animation-delay: 0ms"></span>
                  <span class="dot animate-bounce" style="animation-delay: 150ms"></span>
                  <span class="dot animate-bounce" style="animation-delay: 300ms"></span>
                </div>
                <div v-if="compressingSubtip" class="compressing-subtip">{{ compressingSubtip }}</div>
              </div>
            </div>
          </div>

          <div v-else-if="isThinking" class="msg-item ai animate-fade-in">
            <div class="msg-avatar ai-avatar">
              <svg class="ai-avatar-icon" xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64" fill="none">
                <rect x="14" y="11" width="36" height="42" rx="8" stroke="currentColor" stroke-width="4"/>
                <path d="M24 11V53" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
                <path d="M32 38C26.6 33.8 24 31.1 24 27.5C24 24.95 26 23 28.6 23C30.1 23 31.55 23.68 32.5 24.76C33.45 23.68 34.9 23 36.4 23C39 23 41 24.95 41 27.5C41 31.1 38.4 33.8 33 38L32.5 38.4L32 38Z" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
            <div class="msg-wrapper">
              <div class="chat-bubble chat-ai thinking-bubble">
                <div class="thinking-header">
                  <span class="sparkle-icon">✨</span>
                  <span class="thinking-text">MoodCopilot 正在沉思</span>
                </div>
                <div class="thinking-dots-loader">
                  <span class="dot animate-bounce" style="animation-delay: 0ms"></span>
                  <span class="dot animate-bounce" style="animation-delay: 150ms"></span>
                  <span class="dot animate-bounce" style="animation-delay: 300ms"></span>
                </div>
              </div>
            </div>
          </div>

          <ChatStreamingItem
            :streaming="streaming && !isCompressing"
            :streaming-text="streamingText"
            :streaming-reasoning="streamingReasoning"
            :streaming-refs="streamingRefs"
            :is-compressing="isCompressing"
            :compressing-message="compressingMessage"
            @go-diary="goToDiary"
          />
        </div>

        <!-- 工具执行前的人工批准。面板**就地占住输入框的位置**，不做全屏浮层：
             浮层会把消息区一并盖住，用户既看不到 AI 刚说了什么，也切不了别的页面。
             也刻意不给「点背景关闭」—— 这一轮正停在半路，必须有一个明确的结论（记或不记）。 -->
        <section v-if="awaitingApproval && approvalItem" class="chat-approval-panel" role="dialog"
          aria-labelledby="chat-approval-title">
          <div class="chat-approval-heading">
            <strong id="chat-approval-title">{{ approvalTitleText }}</strong>
            <span>{{ approvalHintText }}</span>
          </div>

          <ul class="chat-approval-list">
            <li class="chat-approval-item">
              <span class="chat-approval-key">{{ approvalTitle(approvalItem) }}</span>
              <!-- 合并：一条条列出要被吃掉的源，比拼成一个字符串看得清 -->
              <span v-if="approvalItem.sources?.length" class="chat-approval-change">
                <span v-for="source in approvalItem.sources" :key="source.attributeKey" class="chat-approval-old">
                  <span class="chat-approval-sign" aria-hidden="true">−</span>
                  <span class="chat-approval-text">{{ source.attributeKey }}：{{ source.value || '（已不存在）' }}</span>
                </span>
              </span>
              <span v-else-if="approvalItem.oldValue" class="chat-approval-change">
                <span class="chat-approval-old">
                  <span class="chat-approval-sign" aria-hidden="true">−</span>
                  <span class="chat-approval-text">{{ approvalItem.oldValue }}</span>
                </span>
              </span>
              <span v-if="approvalItem.newValue" class="chat-approval-change">
                <span class="chat-approval-new">
                  <span class="chat-approval-sign" aria-hidden="true">+</span>
                  <span class="chat-approval-text">{{ approvalItem.newValue }}</span>
                </span>
              </span>
            </li>
          </ul>

          <div v-if="approvalPageCount > 1" class="chat-approval-pager">
            <button type="button" class="chat-approval-pager-btn" :disabled="approvalPageIndex === 0"
              @click="goToApprovalPage(approvalPageIndex - 1)">上一页</button>
            <span class="chat-approval-pager-indicator">
              {{ approvalPageIndex + 1 }}/{{ approvalPageCount }}
              <template v-if="approvalDecidedCount"> · 已定 {{ approvalDecidedCount }}</template>
            </span>
            <button type="button" class="chat-approval-pager-btn"
              :disabled="approvalPageIndex >= approvalPageCount - 1"
              @click="goToApprovalPage(approvalPageIndex + 1)">下一页</button>
          </div>

          <!-- 标签折进 placeholder：面板高一块就少看一块对话，而「这是拒绝原因」由 placeholder 同样说得清。
               读屏器仍从 aria-label 拿到完整说明。 -->
          <input id="chat-approval-reason" v-model="approvalReason" class="chat-approval-input" maxlength="200"
            :aria-label="approvalReasonLabel" :placeholder="approvalReasonPlaceholder" />

          <p v-if="approvalError" class="chat-approval-error">{{ approvalError }}</p>

          <div class="chat-approval-actions">
            <button type="button" class="chat-approval-confirm" :disabled="approvalSubmitting"
              @click="decideApproval(true)">
              {{ approvalSubmitting ? '提交中' : approvalConfirmText }}
            </button>
            <button type="button" class="chat-approval-reject" :disabled="approvalSubmitting"
              @click="decideApproval(false)">{{ approvalRejectText }}</button>
          </div>
        </section>

        <div v-else ref="chatInputArea" class="chat-input-wrapper">
          <Transition name="fade-bounce">
            <button
              v-if="showScrollToBottom"
              class="scroll-to-bottom-btn"
              @click="scrollToBottomAction"
              title="回到底部"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="7 13 12 18 17 13"></polyline><polyline points="7 6 12 11 17 6"></polyline></svg>
            </button>
          </Transition>
          <ChatInputBox
            v-model:draft="draft"
            :streaming="streaming"
            :disabled="creatingConversation"
            :is-compressing="isCompressing"
            :compressing-message="compressingMessage"
            :use-reasoning="useReasoning"
            :last-reply-error="lastReplyError"
            :can-retry="!!lastReplyRequest"
            :references="references"
            :recent-diaries="recentDiaryOptions"
            :recent-events="recentEventOptions"
            :recent-diaries-loading="recentDiariesLoading"
            :recent-events-loading="recentEventsLoading"
            :recent-diaries-error="recentDiariesError"
            :recent-events-error="recentEventsError"
            @send="send"
            @send-enter="handleDraftEnter"
            @update:use-reasoning="useReasoning = $event"
            @retry="retryLastReply"
            @remove-ref="removeRef"
            @add-diary-ref="addDiaryRef"
            @add-event-ref="addEventRef"
            @add-image-reference="addImageRef"
            @load-recent-diaries="loadRecentDiaryOptions"
            @load-recent-events="loadRecentEventOptions"
            @focus="handleDraftFocus"
            @open-persona="openPersonaPanel"
          />
        </div>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { NButton, NDropdown, NDrawer, NDrawerContent, NInput, NBadge } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import ChatSidebar from '../components/chat/ChatSidebar.vue'
import ChatMessageItem from '../components/chat/ChatMessageItem.vue'
import ChatStreamingItem from '../components/chat/ChatStreamingItem.vue'
import ChatInputBox from '../components/chat/ChatInputBox.vue'
import { useChat } from '../composables/useChat'
import { displayConversationTitle } from '../utils/chatTitle'
import { chatApi, type PendingApprovalItem } from '../api/social'
import { authApi } from '../api/auth'
import { computed, ref, watch } from 'vue'

const {
  authStore, userInitial,
  conversations, activeConvId, creatingConversation,
  createConversation, selectConversation, deleteConversation, renameConversation,
  ensureConversation,
  handleMobileConversationChange, deleteActiveConversation,
  messages,
  draft, streaming, streamingText, streamingReasoning, isThinking, isCompressing, compressingMessage, compressingSubtip, useReasoning, streamingRefs,
  lastReplyError, lastReplyRequest, references,
  pendingApprovals, awaitingApproval, approvalSubmitting, approvalError, resolveApproval,
  send, retryLastReply, removeRef, addImageRef,
  recentDiaryOptions, recentDiariesLoading, recentDiariesError,
  addDiaryRef, loadRecentDiaryOptions,
  recentEventOptions, recentEventsLoading, recentEventsError,
  addEventRef, loadRecentEventOptions,
  quickStarters, quickStartersLoading, useQuickStarter,
  msgBox, chatInputArea,
  handleDraftFocus, handleDraftEnter, goToDiary,
} = useChat()

// ── 工具执行前的人工批准 ──

/**
 * 一个审批项 = 一页。一次工具调用对应一项，所以「一次合并一组」天然就是一页一组 ——
 * 合并好几组就翻几页，每页各自表态。
 */
const approvalPageIndex = ref(0)
const approvalReason = ref('')
/** 已经表过态的项，键是 toolCallId。没表态的不在里面。 */
const approvalChoices = ref<Record<string, { approved: boolean; reason: string }>>({})

const approvalItem = computed(() => pendingApprovals.value[approvalPageIndex.value] ?? null)
const approvalPageCount = computed(() => pendingApprovals.value.length)
const approvalDecidedCount = computed(
  () => pendingApprovals.value.filter((item) => item.toolCallId in approvalChoices.value).length)

// 新一批到达（或旧的被收掉）时重置向导：上一批的页号和选择不该留到这一批。
watch(pendingApprovals, () => {
  approvalPageIndex.value = 0
  approvalReason.value = ''
  approvalChoices.value = {}
})

/** 只显示用户看得懂的属性名；工具叫什么对用户没有意义，不往外露。 */
const approvalTitle = (item: PendingApprovalItem) => item.attributeKey?.trim() || '这条记忆'

/**
 * 弹框文案按「这一页要做什么」分派。
 *
 * 写入、删除、合并是三种方向不同的操作，共用「记下这条 / 先不记」会让人点错 —— 而删除和合并
 * 都不可逆，点错没有回头路。所以宁可多写三套文案，也不硬套一种措辞。
 */
const APPROVAL_COPY: Record<string, {
  title: string; hint: string; confirm: string; reject: string
  reasonLabel: string; reasonPlaceholder: string
}> = {
  // hint 一律压到一行：面板每高一行，用户就少看一行对话，而「不可逆」这层意思不能省
  saveMemory: {
    title: '要记进你的长期记忆',
    hint: '确认之后才会写入，以后可以再改或删。',
    confirm: '记下这条',
    reject: '先不记',
    reasonLabel: '不想记的话，可以说一句原因',
    reasonPlaceholder: '拒绝的话说一句原因，例如：这条不重要',
  },
  deleteMemory: {
    title: '要删掉这条记忆',
    hint: '连同全部历史版本一起删除，找不回来。',
    confirm: '确认删除',
    reject: '先不删',
    reasonLabel: '不想删的话，可以说一句原因',
    reasonPlaceholder: '拒绝的话说一句原因，例如：这条还想留着',
  },
  mergeMemory: {
    title: '要合并这几条记忆',
    hint: '只保留合并后的那一条，被合掉的会彻底删除。',
    confirm: '确认合并',
    reject: '先不合并',
    reasonLabel: '不想合并的话，可以说一句原因',
    reasonPlaceholder: '拒绝的话说一句原因，例如：这几条不该合并',
  },
}

/** 工具没给预览、或者出现了新工具时的兜底：不猜语义，只请用户核对。 */
const FALLBACK_APPROVAL_COPY = {
  title: '要变动你的长期记忆',
  hint: '确认之后才会执行，请核对这一条。',
  confirm: '确认执行',
  reject: '先不执行',
  reasonLabel: '不想执行的话，可以说一句原因',
  reasonPlaceholder: '拒绝的话说一句原因，例如：弄错了',
}

const approvalCopy = computed(
  () => (approvalItem.value && APPROVAL_COPY[approvalItem.value.toolName]) || FALLBACK_APPROVAL_COPY)

const approvalTitleText = computed(() => approvalCopy.value.title)
const approvalHintText = computed(() => approvalCopy.value.hint)
const approvalConfirmText = computed(() => approvalCopy.value.confirm)
const approvalRejectText = computed(() => approvalCopy.value.reject)
const approvalReasonLabel = computed(() => approvalCopy.value.reasonLabel)
const approvalReasonPlaceholder = computed(() => approvalCopy.value.reasonPlaceholder)

function goToApprovalPage(index: number) {
  if (index < 0 || index >= approvalPageCount.value) return
  approvalPageIndex.value = index
  const target = pendingApprovals.value[index]
  approvalReason.value = approvalChoices.value[target.toolCallId]?.reason ?? ''
}

/**
 * 记下这一页的选择，然后翻到下一条还没表态的；都表完就直接提交。
 * <p>
 * 单条待批准时这一步就是「点完即提交」，与改造前的手感一致；多条才显出翻页。
 */
async function decideApproval(approved: boolean) {
  const item = approvalItem.value
  if (!item) return
  const next = { ...approvalChoices.value, [item.toolCallId]: { approved, reason: approvalReason.value } }
  approvalChoices.value = next

  const undecided = pendingApprovals.value.findIndex((candidate) => !(candidate.toolCallId in next))
  if (undecided >= 0) {
    goToApprovalPage(undecided)
    return
  }
  // 只发真的表过态的。没表态的后端按拒绝处理，漏发不会变成误放行。
  await resolveApproval(pendingApprovals.value
    .filter((candidate) => candidate.toolCallId in next)
    .map((candidate) => ({
      toolCallId: candidate.toolCallId,
      approved: next[candidate.toolCallId].approved,
      reason: next[candidate.toolCallId].reason,
    })))
  approvalReason.value = ''
}

const showScrollToBottom = ref(false)
const handleScroll = (e: Event) => {
  const target = e.target as HTMLElement
  const distanceToBottom = target.scrollHeight - target.scrollTop - target.clientHeight
  showScrollToBottom.value = distanceToBottom > 150
}
const scrollToBottomAction = () => {
  if (msgBox.value) {
    msgBox.value.scrollTo({ top: msgBox.value.scrollHeight, behavior: 'smooth' })
  }
}

const activeConvTitle = computed(() => {
  const conv = conversations.value.find((c: any) => c.id === activeConvId.value)
  return conv ? displayConversationTitle(conv.title, conv.id) : '选择对话'
})

const mobileConvOptions = computed(() => {
  return conversations.value.map((c: any) => ({
    label: displayConversationTitle(c.title, c.id),
    key: c.id
  }))
})

const mobileDrawerOpen = ref(false)
const searchConvText = ref('')
const filteredConversations = computed(() => {
  if (!searchConvText.value) return conversations.value
  const lowSearch = searchConvText.value.toLowerCase()
  return conversations.value.filter((c: any) => 
    displayConversationTitle(c.title, c.id).toLowerCase().includes(lowSearch)
  )
})

const conversationOptions = [
  { label: '重命名', key: 'rename' },
  { label: '删除', key: 'delete', props: { style: 'color: var(--color-error)' } },
]

const filteredConversationGroups = computed(() => {
  const groups = new Map<string, any[]>()
  for (const conversation of filteredConversations.value) {
    const timestamp = conversation.updatedAt || conversation.createdAt
    const date = timestamp ? new Date(timestamp) : null
    const now = new Date()
    const dayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
    const conversationTime = date && !Number.isNaN(date.getTime()) ? date.getTime() : 0
    const label = conversationTime >= dayStart
      ? '今天'
      : conversationTime >= dayStart - 24 * 60 * 60 * 1000
        ? '昨天'
        : '更早'
    if (!groups.has(label)) groups.set(label, [])
    groups.get(label)!.push(conversation)
  }
  return ['今天', '昨天', '更早'].filter(label => groups.has(label)).map(label => ({ label, items: groups.get(label)! }))
})

const personaRoleOptions = [
  { value: 'personal_assistant', label: '通用个人助手' },
  { value: 'study_partner', label: '学习伙伴' },
  { value: 'coding_partner', label: '编程协作伙伴' },
  { value: 'writing_partner', label: '写作伙伴' },
  { value: 'life_companion', label: '生活陪伴者' },
]
const personaToneOptions = [
  { value: 'natural', label: '自然' }, { value: 'warm', label: '温和' },
  { value: 'direct', label: '直接' }, { value: 'clear', label: '清晰' },
  { value: 'concise', label: '简洁' }, { value: 'precise', label: '严谨' },
  { value: 'formal', label: '正式' }, { value: 'playful', label: '轻松' },
  { value: 'empathetic', label: '共情' }, { value: 'calm', label: '沉静' },
  { value: 'analytical', label: '分析型' }, { value: 'encouraging', label: '鼓励' },
  { value: 'humorous', label: '幽默' }, { value: 'critical', label: '批判思考' },
]
const personaBehaviorOptions = [
  { value: 'CONCLUSION_FIRST', label: '先说结论' },
  { value: 'CODE_FIRST', label: '代码优先' }, { value: 'LESS_REASSURANCE', label: '少一些安慰' },
  { value: 'DIRECT_FEEDBACK', label: '直接反馈' }, { value: 'STEP_BY_STEP', label: '分步骤说明' },
  { value: 'CONCISE', label: '控制篇幅' },
]
const personaOpen = ref(false)
const personaSaving = ref(false)
const personaMessage = ref('')
const personaLoading = ref(false)
const conversationPersonaUsesGlobal = ref(true)
const defaultPersona = () => ({ role: 'personal_assistant', tone: ['natural', 'clear'], behaviorFlags: [] as string[], disabledBehaviorFlags: [] as string[], customTone: '', customResponseStyle: '' })
const conversationPersona = ref(defaultPersona())
const globalPersonaSnapshot = ref(defaultPersona())

function openPersonaPanel() {
  personaMessage.value = ''
  personaOpen.value = true
  if (personaLoading.value) return
  const id = activeConvId.value
  const token = ++personaLoadToken
  if (id) void loadConversationPersona(id, token)
  else void loadGlobalPersona(token)
}

let personaLoadToken = 0

watch(activeConvId, (id) => {
  if (personaSaving.value) return
  personaOpen.value = false
  personaMessage.value = ''
  const token = ++personaLoadToken
  if (id) void loadConversationPersona(id, token)
  else void loadGlobalPersona(token)
}, { immediate: true })

function unwrapPersonaPayload(payload: any) {
  return payload && Object.prototype.hasOwnProperty.call(payload, 'data') ? payload.data : payload
}

function applyGlobalPersona(global: any) {
  const fallback = defaultPersona()
  globalPersonaSnapshot.value = {
    role: global?.role || fallback.role,
    tone: Array.isArray(global?.tone) && global.tone.length ? global.tone : fallback.tone,
    behaviorFlags: Array.isArray(global?.behaviorFlags) ? global.behaviorFlags : fallback.behaviorFlags,
    disabledBehaviorFlags: Array.isArray(global?.disabledBehaviorFlags) ? global.disabledBehaviorFlags : [],
    customTone: global?.customTone || '',
    customResponseStyle: global?.customResponseStyle || '',
  }
}

function copyPersona(persona: ReturnType<typeof defaultPersona>) {
  return {
    role: persona.role,
    tone: [...persona.tone],
    behaviorFlags: [...persona.behaviorFlags],
    disabledBehaviorFlags: [...persona.disabledBehaviorFlags],
    customTone: persona.customTone,
    customResponseStyle: persona.customResponseStyle,
  }
}

async function loadGlobalPersona(token = ++personaLoadToken) {
  personaLoading.value = true
  try {
    const global = unwrapPersonaPayload((await authApi.getAiPersona()).data)
    if (token !== personaLoadToken) return
    applyGlobalPersona(global)
    conversationPersonaUsesGlobal.value = true
    conversationPersona.value = copyPersona(globalPersonaSnapshot.value)
  } catch {
    if (token !== personaLoadToken) return
    applyGlobalPersona(null)
    conversationPersonaUsesGlobal.value = true
    conversationPersona.value = defaultPersona()
    personaMessage.value = '全局设置暂时无法加载，当前显示默认设置'
  } finally {
    if (token === personaLoadToken) personaLoading.value = false
  }
}

async function loadConversationPersona(id: number, token = ++personaLoadToken) {
  personaLoading.value = true
  try {
    const [overrideResponse, globalResponse] = await Promise.all([chatApi.getPersona(id), authApi.getAiPersona()])
    if (token !== personaLoadToken) return
    const override = unwrapPersonaPayload(overrideResponse.data)
    const global = unwrapPersonaPayload(globalResponse.data)
    applyGlobalPersona(global)
    const fallback = defaultPersona()
    const data = override || global
    conversationPersonaUsesGlobal.value = !override
    conversationPersona.value = {
      role: data?.role || fallback.role,
      tone: Array.isArray(data?.tone) && data.tone.length ? data.tone : fallback.tone,
      behaviorFlags: Array.isArray(data?.behaviorFlags) ? data.behaviorFlags : fallback.behaviorFlags,
      disabledBehaviorFlags: Array.isArray(data?.disabledBehaviorFlags) ? data.disabledBehaviorFlags : [],
      customTone: data?.customTone || '',
      customResponseStyle: data?.customResponseStyle || '',
    }
  } catch {
    if (token !== personaLoadToken) return
    conversationPersonaUsesGlobal.value = true
    conversationPersona.value = copyPersona(globalPersonaSnapshot.value)
    personaMessage.value = '会话设置暂时无法加载'
  } finally {
    if (token === personaLoadToken) personaLoading.value = false
  }
}

function toggleConversationTone(value: string) {
  const tone = new Set(conversationPersona.value.tone)
  tone.has(value) ? tone.delete(value) : tone.add(value)
  conversationPersona.value.tone = [...tone]
}

function toggleConversationBehavior(value: string) {
  const behaviorFlags = new Set(conversationPersona.value.behaviorFlags)
  behaviorFlags.has(value) ? behaviorFlags.delete(value) : behaviorFlags.add(value)
  conversationPersona.value.behaviorFlags = [...behaviorFlags]
}

async function saveConversationPersona() {
  if (personaSaving.value) return
  personaSaving.value = true
  personaMessage.value = ''
  try {
    const conversationId = await ensureConversation()
    if (!conversationId) {
      personaMessage.value = '创建会话失败，请稍后重试'
      return
    }
    await chatApi.updatePersona(conversationId, {
      ...conversationPersona.value,
      customTone: conversationPersona.value.customTone.trim(),
      customResponseStyle: conversationPersona.value.customResponseStyle.trim(),
      disabledBehaviorFlags: [],
    })
    await loadConversationPersona(conversationId)
    conversationPersonaUsesGlobal.value = false
    personaMessage.value = '本会话风格已应用'
  } catch (error: any) {
    personaMessage.value = error?.response?.data?.message || '保存失败，请稍后重试'
  } finally {
    personaSaving.value = false
  }
}

async function resetConversationPersona() {
  if (personaSaving.value) return
  if (!activeConvId.value) {
    conversationPersonaUsesGlobal.value = true
    conversationPersona.value = copyPersona(globalPersonaSnapshot.value)
    personaMessage.value = '新对话当前使用全局设置'
    return
  }
  personaSaving.value = true
  try {
    await chatApi.resetPersona(activeConvId.value)
    await loadConversationPersona(activeConvId.value)
    personaMessage.value = '已恢复全局设置'
  } catch (error: any) {
    personaMessage.value = error?.response?.data?.message || '恢复失败，请稍后重试'
  } finally {
    personaSaving.value = false
  }
}

function handleQuote(data: { text: string; role: 'user' | 'ai' }) {
  references.value = references.value.filter((r: any) => r.type !== 'quote')
  const author = data.role === 'ai' ? 'AI' : '我'
  references.value.push({
    label: '引用',
    content: data.text,
    fullContent: data.text,
    type: 'quote',
    quoteAuthor: author,
    displayContent: (data.role === 'ai' ? 'MoodCopilot' : (authStore.displayName || '我')) + '：' + data.text,
  } as any)
  handleDraftFocus()
}
</script>
<style scoped>
.event-checkin-note {
  display: flex;
  flex-direction: column;
  gap: 5px;
  max-width: 520px;
  margin: 22px auto 4px;
  padding: 12px 15px;
  border-left: 3px solid var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 7%, transparent);
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.55;
  text-align: left;
}

.event-checkin-kicker {
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .06em;
}

.chat-task-context {
  display: flex;
  align-items: baseline;
  gap: 8px;
  max-width: 720px;
  margin: 0 auto 6px;
  padding: 0 20px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.chat-task-context span {
  color: var(--color-text-muted);
}
.chat-task-context strong {
  color: var(--color-primary);
  font-weight: 650;
}
.chat-task-context em {
  overflow: hidden;
  color: var(--color-text-muted);
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-persona-modal {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: var(--color-backdrop);
}
.chat-persona-panel {
  position: relative;
  width: min(440px, 100%);
  max-height: min(760px, calc(100vh - 32px));
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  box-shadow: var(--shadow-lg);
}
.chat-persona-close {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--color-text-muted);
  font: inherit;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}
.chat-persona-close:hover,
.chat-persona-close:focus-visible {
  background: var(--color-surface-hover);
  color: var(--color-text);
}
.chat-persona-heading {
  display: flex;
  flex-direction: column;
  gap: 3px;
  color: var(--color-text);
  font-size: 13px;
  padding-right: 32px;
}
.chat-persona-heading span {
  color: var(--color-text-muted);
  font-size: 11px;
}
.chat-persona-label {
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 600;
}
.chat-persona-select,
.chat-persona-input,
.chat-persona-textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-bg);
  color: var(--color-text);
  font: inherit;
}
.chat-persona-select {
  min-height: 34px;
  padding: 0 8px;
}
.chat-persona-input {
  min-height: 34px;
  padding: 0 8px;
}
.chat-persona-textarea {
  min-height: 58px;
  padding: 8px;
  resize: vertical;
  font-size: 12px;
}
.chat-persona-select:focus,
.chat-persona-input:focus,
.chat-persona-textarea:focus {
  outline: 2px solid color-mix(in oklab, var(--color-primary) 24%, transparent);
  border-color: var(--color-primary);
}
.chat-persona-help {
  color: var(--color-text-muted);
  font-size: 11px;
  line-height: 1.45;
}
.chat-persona-options {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.chat-persona-option,
.chat-persona-reset,
.chat-persona-save {
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  padding: 6px 9px;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.chat-persona-option.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
  color: var(--color-primary-hover);
}
.chat-persona-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}
.chat-persona-save {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}
.chat-persona-reset:disabled,
.chat-persona-save:disabled {
  cursor: not-allowed;
  opacity: .6;
}
.chat-persona-message {
  margin: 0;
  color: var(--color-success);
  font-size: 12px;
}

/* ── 工具执行前的人工批准 ──
   旧值/新值不能只靠颜色区分（部分主题里 success 与 primary 同色），
   所以同时保留 +/− 字形和删除线。 */
/* 面板就占住输入框的位置（`.chat-window` 里的普通 flex 子项），不做固定定位浮层。
   `.chat-messages` 是 flex:1，所以面板变高只会压缩消息区的可视高度，不会盖住文字。
   max-height 兜住「一次合并好几组」的长列表，超出部分面板自己滚。 */
.chat-approval-panel {
  flex: 0 0 auto;
  max-height: min(60vh, 460px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}
/* 输入框被面板顶掉了，那截留给输入框的底部空白就没用了 —— 还给消息区，
   否则弹框一出现，能看到的对话反而比平时更少。
   移动端那两条规则带 !important，这里不动用同样的手段压不过它们。 */
.chat-window:has(.chat-approval-panel) .chat-messages {
  padding-bottom: 24px !important;
}
.chat-approval-heading {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--color-text);
}
.chat-approval-heading strong {
  font-size: 14px;
}
.chat-approval-heading span {
  color: var(--color-text-muted);
  font-size: 11px;
  line-height: 1.5;
}
.chat-approval-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.chat-approval-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 7px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
}
.chat-approval-key {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 600;
}
.chat-approval-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.chat-approval-pager-btn {
  padding: 3px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
}
.chat-approval-pager-btn:disabled {
  opacity: .45;
  cursor: default;
}
.chat-approval-pager-indicator {
  font-variant-numeric: tabular-nums;
}
.chat-approval-change {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 13px;
}
.chat-approval-old,
.chat-approval-new {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.chat-approval-old {
  color: var(--color-error);
}
.chat-approval-old .chat-approval-text {
  text-decoration: line-through;
}
.chat-approval-new {
  color: var(--color-success);
}
.chat-approval-sign {
  flex: 0 0 auto;
  font-weight: 700;
}
.chat-approval-text {
  word-break: break-word;
}
.chat-approval-input {
  width: 100%;
  box-sizing: border-box;
  min-height: 32px;
  padding: 0 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-bg);
  color: var(--color-text);
  font: inherit;
}
.chat-approval-input:focus {
  outline: 2px solid color-mix(in oklab, var(--color-primary) 24%, transparent);
  border-color: var(--color-primary);
}
.chat-approval-error {
  margin: 0;
  color: var(--color-error);
  font-size: 12px;
}
.chat-approval-actions {
  display: flex;
  gap: 8px;
  margin-top: 2px;
}
.chat-approval-confirm,
.chat-approval-reject {
  flex: 1 1 0;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 7px 10px;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.chat-approval-confirm {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}
.chat-approval-reject {
  background: var(--color-surface);
  color: var(--color-text-secondary);
}
.chat-approval-confirm:disabled,
.chat-approval-reject:disabled {
  cursor: not-allowed;
  opacity: .6;
}

@media (max-width: 640px) {
  .chat-persona-modal {
    align-items: center;
    padding: 8px;
  }

  .chat-persona-panel {
    max-height: calc(100vh - 16px);
    border-radius: 10px 10px 8px 8px;
  }

  .chat-persona-actions {
    flex-wrap: wrap;
  }

  .chat-persona-save,
  .chat-persona-reset {
    flex: 1 1 150px;
  }
}

@keyframes bounce-subtle {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-4px); }
}

.animate-bounce {
  animation: bounce-subtle 1.2s infinite ease-in-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-fade-in {
  animation: fadeIn 0.25s ease-out forwards;
}

/* 动态省略号打字效果 */
@keyframes typing-dots {
  0% { content: ''; }
  25% { content: '.'; }
  50% { content: '..'; }
  75% { content: '...'; }
  100% { content: ''; }
}

.typing-dots::after {
  content: '';
  animation: typing-dots 1.5s infinite;
  display: inline-block;
  width: 1em;
  text-align: left;
}

:deep(.chat-reply-error-bar) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  margin-bottom: 8px;
  border-radius: 10px;
  background: var(--color-accent-bg);
  border: 1px solid var(--color-border);
  color: var(--color-error);
  font-size: 13px;
}

.chat-user-refs {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.5;
  opacity: 0.9;
}

/* ── Quote Reference Bar ── */
:deep(.quote-ref-bar) {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 10px;
  padding: 8px 12px;
  background: color-mix(in oklab, var(--color-primary) 6%, transparent);
  border-left: 3px solid var(--color-primary);
  border-radius: 0 8px 8px 0;
  font-size: 12.5px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

:deep(.quote-ref-label) {
  font-weight: 600;
  color: var(--color-primary);
  flex-shrink: 0;
}

:deep(.quote-ref-content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-secondary);
}



/* 流式输出光标 */
:deep(.streaming-cursor) {
  animation: cursor-pulse 1.2s ease-in-out infinite;
  color: var(--color-primary);
  margin-left: 2px;
  vertical-align: baseline;
  display: inline-block;
}
@keyframes cursor-pulse {
  0%, 100% { opacity: 0.3; transform: scaleY(0.9); }
  50% { opacity: 1; transform: scaleY(1.1); }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ── RAG 引用折叠面板 ── */
:deep(.rag-refs-panel) {
  margin: 6px 0;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-surface-soft, color-mix(in oklab, var(--color-primary) 3%, transparent));
  overflow: hidden;
  animation: refsIn 0.2s var(--ease-out);
}

/* 引用面板放在气泡上方 */
:deep(.rag-refs-above) {
  margin: 0 0 8px 0;
  max-width: 85%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* 固定引用面板：脱离正文流式溢出，始终保持置顶 */
:deep(.rag-references-fixed) {
  margin-bottom: 8px;
  padding: 8px 10px;
  background: var(--color-surface-soft, color-mix(in oklab, var(--color-primary) 3%, transparent));
  border-radius: 8px;
  border: none;
  width: fit-content;
  max-width: 100%;
}

/* 引用面板内的子项增加呼吸感 */
:deep(.rag-refs-list) {
  gap: 2px;
}
:deep(.rag-ref-item) {
  padding: 6px 12px;
}
:deep(.rag-ref-snippet) {
  margin-right: 8px;
  max-width: 260px;
}

/* 展开面板中的画像项用 chip 风格横向排列 */
:deep(.rag-refs-list .rag-ref-item) {
  max-width: 100%;
}
:deep(.rag-refs-list .rag-ref-snippet) {
  max-width: 260px;
}

/* 消息内容容器：垂直堆叠，引用在上、气泡在下，shrink-wrap 到内容宽度 */
.msg-wrapper {
  display: flex;
  flex-direction: column;
  max-width: 88%;
}

@keyframes refsIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}
:deep(.rag-refs-toggle) {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 4px 8px;
  border: none;
  background: none;
  color: var(--color-text-muted);
  font-size: 10px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}
:deep(.rag-refs-toggle:hover) {
  background: var(--color-surface-hover);
}
:deep(.rag-refs-icon) {
  font-size: 13px;
}
:deep(.rag-refs-arrow) {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-text-muted);
}
:deep(.rag-refs-list) {
  display: flex;
  flex-direction: column;
  gap: 0;
  border-top: 1px solid color-mix(in oklab, var(--color-border) 30%, transparent 70%);
  padding: 2px 0;
}
:deep(.rag-refs-section-label) {
  padding: 2px 10px 0;
  font-size: 9px;
  font-weight: 600;
  color: var(--color-text-muted);
  letter-spacing: 0.03em;
}

/* 日记引用行：日期标签 | 摘要 | 跳转箭头 */
:deep(.rag-ref-item) {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  font-size: 11px;
}
:deep(.rag-ref-item:not(:last-child)) {
  border-bottom: 1px solid color-mix(in oklab, var(--color-border) 25%, transparent 75%);
}
:deep(.rag-ref-clickable) {
  cursor: pointer;
  transition: background 0.12s;
}
:deep(.rag-ref-clickable:hover) {
  background: var(--color-surface-hover);
}

/* 画像条目：简单横排，无额外列 */
:deep(.rag-ref-item-profile) {
  display: flex;
  align-items: center;
  padding: 5px 10px;
  font-size: 11px;
  cursor: pointer;
  transition: background 0.12s;
  position: relative;
}
:deep(.rag-ref-item-profile:not(:last-child)) {
  border-bottom: 1px solid color-mix(in oklab, var(--color-border) 25%, transparent 75%);
}
:deep(.rag-ref-item-profile:hover) {
  background: var(--color-surface-hover);
}
:deep(.rag-ref-meta) {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  min-width: 56px;
}
:deep(.rag-ref-date) {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  font-size: 10px;
}
:deep(.rag-ref-tool-badge) {
  background: color-mix(in oklab, var(--color-primary) 10%, transparent);
  color: var(--color-primary);
  border-radius: 3px;
  padding: 0 3px;
  font-size: 8px;
  font-weight: 600;
  white-space: nowrap;
}

:deep(.think-block) {
  margin: 10px 0;
  padding: 8px 10px;
  background-color: transparent;
  border-radius: 6px;
  border-left: 2px solid color-mix(in oklab, var(--color-text-muted) 30%, transparent);
  font-size: var(--text-xs);
  color: color-mix(in oklab, var(--color-text-secondary) 80%, transparent);
}

:deep(.think-block summary) {
  cursor: pointer;
  font-weight: 500;
  font-size: var(--text-xs);
  color: color-mix(in oklab, var(--color-text-muted) 80%, transparent);
  user-select: none;
  outline: 2px solid transparent; outline-offset: 2px;
}

.streaming-md :deep(.think-block:last-of-type summary::after) {
  content: '';
  animation: typing-dots 1.5s infinite;
  display: inline-block;
  width: 1em;
  text-align: left;
}

:deep(.think-content) {
  margin-top: 6px;
  padding-left: 2px;
  border-top: 1px dashed color-mix(in oklab, var(--color-border) 40%, transparent);
  padding-top: 6px;
}

:deep(.think-content p) {
  font-size: var(--text-xs);
  color: color-mix(in oklab, var(--color-text-secondary) 80%, transparent);
  margin-bottom: 0.5em;
  line-height: 1.6;
}
:deep(.rag-ref-snippet) {
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  transition: color 0.2s ease, background-color 0.2s ease, border-color 0.2s ease, opacity 0.2s ease, transform 0.2s ease;
}
:deep(.rag-ref-key) {
  font-weight: 600;
  margin-right: 2px;
}
:deep(.rag-ref-snippet.expanded) {
  white-space: normal;
  display: block;
  max-width: 360px;
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  z-index: 10;
  position: relative;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--color-border);
}

/* ── 消息对齐：AI 靠左，用户靠右 ── */
:deep(.msg-item) {
  display: flex;
  width: 100%;
  margin-bottom: 20px !important;
  gap: 12px;
}

:deep(.msg-item.ai) {
  justify-content: flex-start !important;
}

:deep(.msg-item.user) {
  justify-content: flex-end !important;
}

:deep(.msg-wrapper) {
  display: flex;
  flex-direction: column;
  max-width: 85% !important;
}

:deep(.msg-item.ai .msg-wrapper) {
  max-width: 92% !important;
}

:deep(.msg-item.ai .msg-wrapper) {
  align-items: flex-start !important;
}

:deep(.msg-item.user .msg-wrapper) {
  align-items: flex-end !important;
}

/* ── 引用卡片悬浮展开 (chip 风格) ── */
:deep(.rag-ref-chip) {
  cursor: pointer;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
  transition: color 0.2s ease, background-color 0.2s ease, border-color 0.2s ease, opacity 0.2s ease, transform 0.2s ease;
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
:deep(.rag-ref-chip.expanded) {
  max-width: 400px;
  white-space: normal;
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  z-index: 10;
  position: relative;
}
:deep(.rag-ref-go) {
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
:deep(.thinking-status) {
  display: flex;
  align-items: center;
  gap: 6px;
  color: color-mix(in oklab, var(--color-text-secondary) 80%, transparent);
  font-size: var(--text-sm);
  padding: 8px 12px;
  background-color: transparent;
  border-radius: 8px;
  margin-bottom: 8px;
}

/* 深度思考动效中的行内跳点容器 */
:deep(.thinking-dots-inline) {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: 2px;
}

/* AI 消息只有 think 无正文时的兜底占位 */
.ai-think-placeholder {
  color: var(--color-text-muted);
  font-size: 13px;
  opacity: 0.5;
  letter-spacing: 0.1em;
}

.sparkle-icon {
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% { opacity: 0.4; }
  50% { opacity: 1; }
  100% { opacity: 0.4; }
}

/* 沉思加载动画样式 */:deep(.thinking-bubble) {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--color-surface);
  border: none !important;
  box-shadow: 0 4px 12px color-mix(in oklab, var(--color-primary) 8%, transparent) !important;
}:deep(.thinking-header) {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  color: var(--color-primary);
  font-weight: 500;
}:deep(.thinking-dots-loader) {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 8px;
  padding-left: 2px;
}:deep(.thinking-dots-loader .dot) {
  width: 6px;
  height: 6px;
  background-color: var(--color-primary);
  border-radius: 50%;
}
:deep(.compressing-subtip) {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 2px;
}

/* ── 快捷对话建议卡片 ── */
.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  flex: 1;
  text-align: center;
  align-self: center;
  width: 100%;
  max-width: 560px;
  box-sizing: border-box;
}

.chat-header-title {
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0 0 6px;
}

.chat-subtitle {
  font-size: 0.9rem;
  color: var(--color-text-muted);
  margin: 0 0 28px;
  font-style: italic;
}

.chat-quick-starters {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-width: 480px;
  width: 100%;
  margin-top: 4px;
}

.quick-starter-card {
  display: flex;
  align-items: flex-start;
  text-align: left;
  gap: 10px;
  padding: 14px 16px;
  background: transparent;
  border: 1.5px solid color-mix(in oklab, var(--color-primary) 10%, transparent);
  border-radius: 12px;
  cursor: pointer;
  transition: color 0.25s var(--ease-out), background-color 0.25s var(--ease-out), border-color 0.25s var(--ease-out), opacity 0.25s var(--ease-out), transform 0.25s var(--ease-out);
  font-family: inherit;
}

.quick-starter-card:hover {
  transform: translateY(-1px);
  border-color: var(--color-primary);
  background: color-mix(in oklab, var(--color-primary) 4%, transparent);
  box-shadow: 0 4px 12px color-mix(in oklab, var(--color-primary) 8%, transparent);
}

.quick-starter-card:active { transform: translateY(0); }

.starter-icon {
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 1px;
}

.starter-text {
  font-size: 0.82rem;
  color: var(--color-text-secondary);
  line-height: 1.4;
  font-weight: 500;
}

/* Skeleton Loading Styles */
.skeleton-card {
  cursor: default;
  opacity: 0.6;
}
.skeleton-card:hover {
  transform: none;
  box-shadow: none;
  background: transparent;
  border-color: color-mix(in oklab, var(--color-primary) 10%, transparent);
}

.skeleton-icon {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  margin-bottom: 8px;
  background: linear-gradient(90deg, var(--color-surface-hover) 25%, var(--color-bg) 50%, var(--color-surface-hover) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite linear;
}

.skeleton-text {
  width: 80%;
  height: 14px;
  border-radius: 4px;
  margin-top: 4px;
  background: linear-gradient(90deg, var(--color-surface-hover) 25%, var(--color-bg) 50%, var(--color-surface-hover) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite linear;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@media (max-width: 768px) {
  .chat-messages {
    padding: 18px 14px 160px !important;
    border-radius: 12px !important;
    min-height: 340px;
  }

  .chat-window {
    padding-bottom: calc(58px + env(safe-area-inset-bottom)) !important;
  }

  .chat-quick-starters {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .quick-starter-card {
    padding: 12px 14px;
    flex-direction: row;
    align-items: center;
    gap: 10px;
  }
  .starter-icon { margin-top: 0; }
  .chat-input-area {
    border-radius: 6px !important;
    padding: 6px 6px 6px 14px !important;
  }

  .chat-input-wrapper {
    bottom: calc(58px + env(safe-area-inset-bottom)) !important;
    padding-bottom: 12px;
  }

  .chat-messages {
    padding-bottom: calc(180px + env(safe-area-inset-bottom)) !important;
  }
}

/* --- Premium Custom Styles --- */

/* --- DeepSeek Float Style --- */

.chat-input-wrapper {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 10;
  padding: 0 10px 10px;
  background: linear-gradient(to bottom, transparent, var(--color-bg) 20%);
  pointer-events: none; /* Let clicks pass through gradient */
}

.chat-input-wrapper > * {
  pointer-events: auto; /* Enable clicks on the actual input box */
}

.scroll-to-bottom-btn {
  position: absolute;
  top: -40px;
  right: 16px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  cursor: pointer;
  z-index: 11;
  pointer-events: auto;
}

.scroll-to-bottom-btn:hover {
  background: var(--color-surface-hover);
}

:deep(.chat-input-row) {
  gap: 12px !important;
}

:deep(.chat-input-row .n-input) {
  --n-border-radius: 6px !important;
  --n-border: none !important;
  --n-border-hover: none !important;
  --n-border-focus: none !important;
  --n-box-shadow-focus: 0 0 0 2px var(--color-primary-light) !important;
  background: var(--color-bg) !important;
}

:deep(.chat-input-row .n-button) {
  --n-border-radius: 6px !important;
  height: 40px !important;
  padding: 0 20px !important;
  font-weight: 600 !important;
  transition: all 0.2s ease !important;
}

:deep(.chat-input-row .n-button:not([disabled]):hover) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px var(--color-primary-light);
}:deep(.rag-references-fixed) {
  background: color-mix(in oklab, var(--color-primary) 3%, var(--color-surface)) !important;
  backdrop-filter: blur(4px);
  border: 1px solid color-mix(in oklab, var(--color-primary) 15%, transparent) !important;
  border-radius: 12px !important;
  padding: 6px 10px !important;
}

:deep(.msg-avatar) {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
  font-family: inherit;
  box-sizing: border-box;
}

:deep(.msg-avatar.user-avatar) {
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-hover));
  color: var(--color-on-primary);
  font-weight: 600;
  font-size: 13px;
  box-shadow: 0 4px 10px color-mix(in oklab, var(--color-primary) 15%, transparent);
  border: 1.5px solid var(--color-surface);
  overflow: hidden;
}

:deep(.msg-avatar.user-avatar img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

:deep(.msg-avatar.ai-avatar) {
  background: var(--color-surface);
  border: 1.5px solid var(--color-primary-light);
  box-shadow: 0 4px 10px var(--color-primary-light);
  padding: 4px;
  overflow: hidden;
}

:deep(.msg-avatar.ai-avatar .ai-avatar-icon) {
  width: 100%;
  height: 100%;
  display: block;
  color: var(--color-primary);
}

:deep(.chat-bubble) {
  max-width: 100% !important;
  padding: 14px 18px !important;
  font-size: 14px !important;
  line-height: 1.8 !important;
  letter-spacing: 0.01em;
  box-sizing: border-box;
}

:deep(.msg-item.user) {
  transform: rotate(-0.3deg);
}

:deep(.chat-bubble.chat-user) {
  background: var(--color-surface) !important;
  background-image: repeating-linear-gradient(
    transparent,
    transparent 27px,
    color-mix(in oklab, var(--color-primary) 15%, transparent) 27px,
    color-mix(in oklab, var(--color-primary) 15%, transparent) 28px
  ) !important;
  background-attachment: local !important;
  border: 1px solid color-mix(in oklab, var(--color-primary) 20%, transparent) !important;
  border-radius: 2px 15px 3px 18px / 15px 3px 18px 2px !important;
  box-shadow: 2px 4px 12px color-mix(in oklab, var(--color-primary) 5%, transparent) !important;
  color: var(--color-text) !important;
  line-height: 28px !important;
  position: relative;
  font-family: var(--font-body) !important;
}

:deep(.chat-bubble.chat-user p) {
  margin: 0;
  color: var(--color-text) !important;
  line-height: 28px !important;
}

:deep(.msg-item.ai) {
  transform: rotate(0.2deg);
}

:deep(.chat-bubble.chat-ai) {
  background: var(--color-surface) !important;
  border: 1px solid var(--color-border) !important;
  border-radius: 4px !important;
  padding: 16px 20px !important;
  box-shadow: var(--shadow-md) !important;
  font-family: var(--font-display) !important;
  color: var(--color-text) !important;
  font-size: 15.5px !important;
  line-height: 1.6 !important;
  position: relative;
  background-image: radial-gradient(circle at 100% 0%, color-mix(in oklab, var(--color-primary) 3%, transparent) 0%, transparent 40%) !important;
}

:deep(.chat-bubble.chat-ai::after) {
  content: '';
  position: absolute;
  top: -5px;
  left: 15px;
  width: 12px;
  height: 25px;
  border: 2px solid color-mix(in oklab, var(--color-text) 15%, transparent);
  border-radius: 10px;
  border-bottom: none;
  transform: rotate(-15deg);
}

:deep(.chat-user-refs) {
  margin: 8px 0 0;
  padding: 6px 12px;
  background: color-mix(in oklab, var(--color-text) 5%, transparent);
  border-radius: 4px;
  font-size: 11.5px;
  line-height: 1.5;
  border-left: 2px solid color-mix(in oklab, var(--color-text) 30%, transparent);
  color: var(--color-text-secondary);
  opacity: 1;
  list-style-type: none;
}

:deep(.chat-user-refs li) {
  margin-bottom: 2px;
}

:deep(.think-block) {
  margin: 4px 0 12px 0;
  padding: 10px 12px;
  background-color: var(--color-primary-light);
  border-radius: 8px;
  border-left: 3px solid var(--color-primary-light);
  font-family: Consolas, Monaco, "Andale Mono", monospace;
  font-size: 12.5px;
  color: var(--color-text-secondary);
}

:deep(.think-block summary) {
  cursor: pointer;
  font-weight: 600;
  font-size: 12px;
  color: var(--color-primary);
  user-select: none;
  outline: 2px solid transparent; outline-offset: 2px;
  margin-bottom: 4px;
}

:deep(.think-content) {
  margin-top: 8px;
  border-top: 1px dashed var(--color-primary-light);
  padding-top: 8px;
  line-height: 1.6;
  white-space: pre-wrap;
}



/* ── Magazine Style Features Index ── */
.chat-features-index {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 28px 0;
  font-family: var(--font-display);
  font-size: 13px;
  letter-spacing: 0.05em;
  color: var(--color-text-muted);
}

.chat-features-index .index-label {
  font-style: italic;
  opacity: 0.6;
}

.chat-features-index .index-link {
  color: var(--color-text-secondary);
  text-decoration: none;
  transition: color 0.25s var(--ease-out, ease), background-color 0.25s var(--ease-out, ease), border-color 0.25s var(--ease-out, ease), opacity 0.25s var(--ease-out, ease), transform 0.25s var(--ease-out, ease);
  font-weight: 500;
  text-transform: uppercase;
  font-size: 11px;
  letter-spacing: 0.1em;
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
}

.chat-features-index .index-link:hover {
  color: var(--color-primary);
}

.chat-features-index .link-arrow {
  font-family: var(--font-sans);
  font-size: 10px;
  opacity: 0.4;
  transition: color 0.25s var(--ease-out, ease), background-color 0.25s var(--ease-out, ease), border-color 0.25s var(--ease-out, ease), opacity 0.25s var(--ease-out, ease), transform 0.25s var(--ease-out, ease);
}

.chat-features-index .index-link:hover .link-arrow {
  opacity: 1;
  transform: translate(2px, -2px);
}

.chat-features-index .index-separator {
  opacity: 0.4;
}

.chat-features-index .en-sub {
  font-size: 9.5px;
  opacity: 0.6;
  font-weight: 600;
  font-family: var(--font-sans);
}

/* ── Mobile drawer conversation more button ── */
.ds-mobile-conv-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  color: var(--color-text);
}

.ds-mobile-conv-item:hover,
.ds-mobile-conv-item.active {
  background: color-mix(in oklab, var(--color-primary) 8%, transparent);
}

.ds-mobile-conv-group-label {
  padding: 14px 14px 5px;
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.ds-mobile-conv-title-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.ds-mobile-conv-more-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s;
}

.ds-mobile-conv-item:hover .ds-mobile-conv-more-btn,
.ds-mobile-conv-item.active .ds-mobile-conv-more-btn {
  opacity: 1;
}

.ds-mobile-conv-more-btn:hover {
  background: color-mix(in oklab, var(--color-primary) 15%, transparent);
  color: var(--color-primary);
}

@media (max-width: 600px) {
  .chat-features-index {
    justify-content: flex-start;
    gap: 8px;
    padding: 10px 14px 0;
    overflow-x: auto;
    overscroll-behavior-x: contain;
    scrollbar-width: none;
    white-space: nowrap;
  }

  .chat-features-index::-webkit-scrollbar {
    display: none;
  }

  .chat-shell {
    width: 100%;
    max-width: 100vw;
    min-width: 0;
    overflow-x: hidden;
  }

  .chat-window,
  .chat-messages,
  .chat-input-area {
    width: 100%;
    min-width: 0;
    box-sizing: border-box;
  }

  .chat-mobile-conv {
    min-width: 0;
    padding: 2px 0;
  }

  .chat-mobile-conv-select {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .chat-messages {
    overflow-x: hidden;
    padding: 14px 10px 160px !important;
  }

  :deep(.msg-item) {
    min-width: 0;
    gap: 8px;
  }

  :deep(.msg-wrapper),
  :deep(.msg-item.ai .msg-wrapper) {
    min-width: 0;
    max-width: calc(100% - 44px) !important;
  }

  :deep(.chat-bubble) {
    min-width: 0;
    max-width: 100% !important;
    padding: 12px 14px !important;
    overflow-wrap: anywhere;
  }

  :deep(.md-content) {
    min-width: 0;
    overflow-wrap: anywhere;
  }

}
</style>

<style>
body.chat-keyboard-open .chat-input-wrapper {
  bottom: 0 !important;
}
</style>

<style scoped>
@media (max-width: 768px) {
  .chat-empty {
    justify-content: flex-start !important;
    padding-top: 100px !important;
  }
}
</style>
