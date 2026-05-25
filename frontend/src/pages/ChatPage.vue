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
              {{ conv.title || `对话 ${conv.id}` }}
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
                @click="useQuickStarter(item.text)"
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
            @go-diary="goToDiary"
          />

          <div v-if="isThinking" class="msg-item ai animate-fade-in">
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
            :streaming="streaming"
            :streaming-text="streamingText"
            :streaming-refs="streamingRefs"
            @go-diary="goToDiary"
          />
        </div>

        <div ref="chatInputArea">
          <ChatInputBox
            v-model:draft="draft"
            :streaming="streaming"
            :disabled="creatingConversation"
            :last-reply-error="lastReplyError"
            :can-retry="!!lastReplyRequest"
            :references="references"
            :recent-diaries="recentDiaryOptions"
            :recent-diaries-loading="recentDiariesLoading"
            :recent-diaries-error="recentDiariesError"
            @send="send"
            @send-enter="handleDraftEnter"
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

const {
  authStore, userInitial,
  conversations, activeConvId, creatingConversation,
  createConversation, selectConversation, deleteConversation,
  handleMobileConversationChange, deleteActiveConversation,
  messages,
  draft, streaming, streamingText, isThinking, streamingRefs,
  lastReplyError, lastReplyRequest, references,
  send, retryLastReply, removeRef,
  recentDiaryOptions, recentDiariesLoading, recentDiariesError,
  addDiaryRef, loadRecentDiaryOptions,
  quickStarters, quickStartersLoading, useQuickStarter,
  msgBox, chatInputArea,
  handleDraftFocus, handleDraftEnter, goToDiary,
} = useChat()
</script>
<style scoped>
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
  background: #fff4f4;
  border: 1px solid #ffd7d7;
  color: #9a3030;
  font-size: 13px;
}

.chat-user-refs {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.5;
  opacity: 0.9;
}

/* Markdown 动态内容样式穿透 */
.md-content :deep(p) {
  margin: 0 0 0.5em 0;
  line-height: 1.6;
}
.md-content :deep(p:last-child) {
  margin-bottom: 0;
}
.md-content :deep(strong), .md-content :deep(b) {
  font-weight: 600;
  color: inherit;
}
.md-content :deep(ul) {
  list-style-type: disc;
  padding-left: 1.5em;
  margin: 0.5em 0;
}
.md-content :deep(ol) {
  list-style-type: decimal;
  padding-left: 1.5em;
  margin: 0.5em 0;
}
.md-content :deep(li) {
  margin-bottom: 0.25em;
}
.md-content :deep(blockquote) {
  border-left: 3px solid #cbd5e1;
  padding-left: 0.75em;
  color: #64748b;
  margin: 0.5em 0;
}
.md-content :deep(code) {
  background-color: rgba(0, 0, 0, 0.05);
  padding: 0.2em 0.4em;
  border-radius: 4px;
  font-family: monospace;
  font-size: 0.9em;
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

/* ── RAG 引用折叠面板 ── */:deep(.rag-refs-panel) {
  margin: 6px 0;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-surface-soft, color-mix(in oklab, var(--color-primary) 3%, transparent));
  overflow: hidden;
  animation: refsIn 0.2s var(--ease-out);
}

/* 引用面板放在气泡上方 */:deep(.rag-refs-above) {
  margin: 0 0 8px 0;
  max-width: 85%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* 固定引用面板：脱离正文流式溢出，始终保持置顶 */:deep(.rag-references-fixed) {
  margin-bottom: 8px;
  padding: 8px 10px;
  background: var(--color-surface-soft, color-mix(in oklab, var(--color-primary) 3%, transparent));
  border-radius: 8px;
  border: none;
  width: fit-content;
  max-width: 100%;
}

/* 引用面板内的子项增加呼吸感 */:deep(.rag-refs-list) {
  gap: 2px;
}:deep(.rag-ref-item) {
  padding: 6px 12px;
}:deep(.rag-ref-snippet) {
  margin-right: 8px;
  max-width: 260px;
}

/* 展开面板中的画像项用 chip 风格横向排列 */:deep(.rag-refs-list .rag-ref-item) {
  max-width: 100%;
}:deep(.rag-refs-list .rag-ref-snippet) {
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
}:deep(.rag-refs-toggle) {
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
}:deep(.rag-refs-toggle:hover) {
  background: var(--color-surface-hover);
}:deep(.rag-refs-icon) {
  font-size: 13px;
}:deep(.rag-refs-arrow) {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-text-muted);
}:deep(.rag-refs-list) {
  display: flex;
  flex-direction: column;
  gap: 0;
  border-top: 1px solid color-mix(in oklab, var(--color-border) 30%, transparent 70%);
  padding: 2px 0;
}:deep(.rag-refs-section-label) {
  padding: 2px 10px 0;
  font-size: 9px;
  font-weight: 600;
  color: var(--color-text-muted);
  letter-spacing: 0.03em;
}

/* 日记引用行：日期标签 | 摘要 | 跳转箭头 */:deep(.rag-ref-item) {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  font-size: 11px;
}:deep(.rag-ref-item:not(:last-child)) {
  border-bottom: 1px solid color-mix(in oklab, var(--color-border) 25%, transparent 75%);
}:deep(.rag-ref-clickable) {
  cursor: pointer;
  transition: background 0.12s;
}:deep(.rag-ref-clickable:hover) {
  background: var(--color-surface-hover);
}

/* 画像条目：简单横排，无额外列 */:deep(.rag-ref-item-profile) {
  display: flex;
  align-items: center;
  padding: 5px 10px;
  font-size: 11px;
  cursor: pointer;
  transition: background 0.12s;
  position: relative;
}:deep(.rag-ref-item-profile:not(:last-child)) {
  border-bottom: 1px solid color-mix(in oklab, var(--color-border) 25%, transparent 75%);
}:deep(.rag-ref-item-profile:hover) {
  background: var(--color-surface-hover);
}:deep(.rag-ref-meta) {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  min-width: 56px;
}:deep(.rag-ref-date) {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  font-size: 10px;
}:deep(.rag-ref-tool-badge) {
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
}:deep(.rag-ref-snippet) {
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  transition: all 0.2s ease;
}:deep(.rag-ref-key) {
  font-weight: 600;
  margin-right: 2px;
}:deep(.rag-ref-snippet.expanded) {
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

/* ── 引用卡片悬浮展开 (chip 风格) ── */:deep(.rag-ref-chip) {
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
}:deep(.rag-ref-chip.expanded) {
  max-width: 400px;
  white-space: normal;
  background: var(--color-surface);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  z-index: 10;
  position: relative;
}:deep(.rag-ref-go) {
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}:deep(.thinking-status) {
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

/* 深度思考动效中的行内跳点容器 */:deep(.thinking-dots-inline) {
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

/* 快捷对话建议卡片 */
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
  font-size: 28px;
  color: var(--color-primary);
  margin: 0;
  font-weight: 700;
}

.chat-subtitle {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 8px 0 24px;
}

.chat-quick-starters {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  max-width: 560px;
  width: 100%;
  margin-top: 10px;
}

.quick-starter-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
  padding: 16px;
  background: rgba(255, 255, 255, 0.45);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(180, 150, 120, 0.15);
  border-radius: var(--radius-lg, 16px);
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  font-family: inherit;
}

.quick-starter-card:hover {
  transform: translateY(-2px);
  border-color: var(--color-primary);
  background: rgba(255, 255, 255, 0.7);
  box-shadow: 0 6px 20px var(--color-primary-light);
}

.quick-starter-card:active {
  transform: translateY(0);
}

.starter-icon {
  font-size: 20px;
  margin-bottom: 8px;
}

.starter-text {
  font-size: 13.5px;
  color: #4a5a4e;
  line-height: 1.4;
  font-weight: 500;
}

/* Skeleton Loading Styles */
.skeleton-card {
  cursor: default;
}
.skeleton-card:hover {
  transform: none;
  box-shadow: none;
  background: rgba(255, 255, 255, 0.45);
  border-color: rgba(180, 150, 120, 0.15);
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
  .chat-quick-starters {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .quick-starter-card {
    padding: 12px 14px;
    flex-direction: row;
    align-items: center;
    gap: 12px;
  }
  .starter-icon {
    margin-bottom: 0;
  }
}

/* --- Premium Custom Styles --- */

.chat-messages {
  padding: 24px !important;
  background: var(--color-bg) !important;
  border: none !important;
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.01);
}

.chat-messages::after {
  content: '';
  display: block;
  min-height: 24px;
  flex-shrink: 0;
}

.chat-input-area {
  background: var(--color-surface) !important;
  border: none !important;
  border-radius: 16px !important;
  padding: 12px 16px !important;
  box-shadow: 0 8px 30px color-mix(in oklab, var(--color-primary) 8%, transparent) !important;
  transition: all 0.3s ease;
}

.chat-input-area:focus-within {
  border-color: var(--color-primary) !important;
  box-shadow: 0 8px 30px var(--color-primary-light), 0 0 0 2px var(--color-primary-light) !important;
}

:deep(.chat-input-row) {
  gap: 12px !important;
}

:deep(.chat-input-row .n-input) {
  --n-border-radius: 12px !important;
  --n-border: none !important;
  --n-border-hover: none !important;
  --n-border-focus: none !important;
  --n-box-shadow-focus: 0 0 0 2px var(--color-primary-light) !important;
  background: var(--color-bg) !important;
}

:deep(.chat-input-row .n-button) {
  --n-border-radius: 12px !important;
  height: 40px !important;
  padding: 0 20px !important;
  font-weight: 600 !important;
  transition: all 0.2s ease !important;
}

:deep(.chat-input-row .n-button:not([disabled]):hover) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px var(--color-primary-light);
}:deep(.rag-references-fixed) {
  background: rgba(244, 247, 245, 0.8) !important;
  backdrop-filter: blur(4px);
  border: 1px solid var(--color-primary-light) !important;
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
  background: linear-gradient(135deg, #8ba897, #5f836f);
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
  padding: 12px 18px !important;
  font-size: 14.5px !important;
  line-height: 1.6 !important;
  letter-spacing: 0.01em;
  box-sizing: border-box;
}

:deep(.chat-bubble.chat-user) {
  background: var(--color-primary) !important;
  color: var(--color-on-primary) !important;
  border: none !important;
  border-radius: 18px 18px 4px 18px !important;
  box-shadow: 0 4px 14px var(--color-primary-light) !important;
}

:deep(.chat-bubble.chat-user p) {
  margin: 0;
  color: var(--color-on-primary) !important;
}

:deep(.chat-bubble.chat-ai) {
  background: var(--color-surface) !important;
  color: var(--color-text) !important;
  border: none !important;
  border-radius: 18px 18px 18px 4px !important;
  box-shadow: 0 4px 12px color-mix(in oklab, var(--color-primary) 8%, transparent) !important;
}

:deep(.chat-user-refs) {
  margin: 8px 0 0;
  padding-left: 12px;
  font-size: 11.5px;
  line-height: 1.5;
  border-left: 2px solid rgba(255, 255, 255, 0.4);
  color: rgba(255, 255, 255, 0.85);
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

.md-content :deep(p) {
  margin-bottom: 8px !important;
}

.md-content :deep(p:last-child) {
  margin-bottom: 0 !important;
}

.md-content :deep(pre) {
  background: #f7f6f2 !important;
  border: 1px solid rgba(180, 150, 120, 0.12) !important;
}
</style>
