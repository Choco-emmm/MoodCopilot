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
      />

      <!-- 聊天区域 -->
      <div class="chat-window">
        <!-- Magazine Style Features Index -->
        <div class="chat-features-index">
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
          <select
            class="chat-mobile-conv-select"
            :value="activeConvId ?? ''"
            @change="handleMobileConversationChange"
          >
            <option
              v-for="conv in conversations"
              :key="conv.id"
              :value="conv.id"
            >
              {{ displayConversationTitle(conv.title, conv.id) }}
            </option>
          </select>
          <n-button
            size="small"
            tertiary
            type="error"
            :disabled="!activeConvId"
            @click="deleteActiveConversation"
          >删除</n-button>
          <n-button size="small" type="primary" :disabled="creatingConversation" @click="createConversation">新建</n-button>
        </div>

        <div class="chat-messages" ref="msgBox">
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
                <div class="compressing-subtip">正在精炼长对话记忆，优化后将继续回复</div>
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
            :streaming-refs="streamingRefs"
            :is-compressing="isCompressing"
            :compressing-message="compressingMessage"
            @go-diary="goToDiary"
          />
        </div>

        <div ref="chatInputArea">
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
            :recent-diaries-loading="recentDiariesLoading"
            :recent-diaries-error="recentDiariesError"
            @send="send"
            @send-enter="handleDraftEnter"
            @update:use-reasoning="useReasoning = $event"
            @retry="retryLastReply"
            @remove-ref="removeRef"
            @add-diary-ref="addDiaryRef"
            @load-recent-diaries="loadRecentDiaryOptions"
            @focus="handleDraftFocus"
          />
        </div>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { NButton } from 'naive-ui'
import AppHeader from '../components/AppHeader.vue'
import ChatSidebar from '../components/chat/ChatSidebar.vue'
import ChatMessageItem from '../components/chat/ChatMessageItem.vue'
import ChatStreamingItem from '../components/chat/ChatStreamingItem.vue'
import ChatInputBox from '../components/chat/ChatInputBox.vue'
import { useChat } from '../composables/useChat'
import { displayConversationTitle } from '../utils/chatTitle'

const {
  authStore, userInitial,
  conversations, activeConvId, creatingConversation,
  createConversation, selectConversation, deleteConversation,
  handleMobileConversationChange, deleteActiveConversation,
  messages,
  draft, streaming, streamingText, isThinking, isCompressing, compressingMessage, useReasoning, streamingRefs,
  lastReplyError, lastReplyRequest, references,
  send, retryLastReply, removeRef,
  recentDiaryOptions, recentDiariesLoading, recentDiariesError,
  addDiaryRef, loadRecentDiaryOptions,
  quickStarters, quickStartersLoading, useQuickStarter,
  msgBox, chatInputArea,
  handleDraftFocus, handleDraftEnter, goToDiary,
} = useChat()

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
  outline: none;
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
  transition: all 0.2s ease;
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
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
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
  transition: all 0.2s ease;
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
:deep(.rag-ref-chip.expanded) {
  max-width: 400px;
  white-space: normal;
  background: var(--color-surface);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
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
  color: var(--color-text-muted, #8a919f);
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
  transition: all 0.25s var(--ease-out);
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

@media (max-width: 600px) {
  .chat-messages {
    padding: 18px 14px !important;
    border-radius: 12px !important;
    min-height: 340px;
  }

  .chat-window {
    padding-bottom: calc(76px + env(safe-area-inset-bottom));
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
}

/* --- Premium Custom Styles --- */

.chat-messages {
  padding: 28px 28px 20px !important;
  background: var(--color-surface) !important;
  background-image: linear-gradient(135deg, var(--color-surface) 0%, color-mix(in oklab, var(--color-primary) 1%, var(--color-surface)) 100%) !important;
  border: none !important;
  border-radius: 16px !important;
  box-shadow: 0 1px 2px rgba(32,32,29,0.03), 0 4px 16px color-mix(in oklab, var(--color-primary) 4%, transparent) !important;
}

.chat-messages::after {
  content: '';
  display: block;
  min-height: 24px;
  flex-shrink: 0;
}

.chat-input-area {
  background: color-mix(in oklab, var(--color-surface) 80%, transparent) !important;
  backdrop-filter: blur(24px) !important;
  -webkit-backdrop-filter: blur(24px) !important;
  border: 1.5px solid color-mix(in oklab, var(--color-primary) 12%, transparent) !important;
  border-radius: 8px !important;
  padding: 8px 8px 8px 20px !important;
  box-shadow: 0 8px 32px color-mix(in oklab, var(--color-primary) 6%, transparent) !important;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.chat-input-area:focus-within {
  border-color: var(--color-primary) !important;
  box-shadow: 0 12px 40px color-mix(in oklab, var(--color-primary) 10%, transparent) !important;
  transform: translateY(-1px);
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
  box-shadow: 0 4px 10px rgba(95, 131, 111, 0.15);
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
    rgba(139, 115, 85, 0.15) 27px,
    rgba(139, 115, 85, 0.15) 28px
  ) !important;
  background-attachment: local !important;
  border: 1px solid color-mix(in oklab, rgba(139, 115, 85, 0.2) 100%, transparent) !important;
  border-radius: 2px 15px 3px 18px / 15px 3px 18px 2px !important;
  box-shadow: 2px 4px 12px rgba(139, 115, 85, 0.05) !important;
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
  box-shadow: 0 4px 16px rgba(32, 32, 29, 0.06) !important;
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
  outline: none;
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
  transition: all 0.25s var(--ease-out, ease);
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
  transition: all 0.25s var(--ease-out, ease);
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

@media (max-width: 600px) {
  .chat-features-index {
    padding: 10px 14px 0;
  }
}
</style>
