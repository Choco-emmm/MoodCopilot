import re

# 1. Update formatMessage in chat.vue
with open('frontend-uniapp/src/pages/chat/chat.vue', 'r', encoding='utf-8') as f:
    chat_vue = f.read()

chat_vue = chat_vue.replace(
    "return content.replace(/<think>[\\s\\S]*?<\\/think>/gi, '').trim();",
    "return content;"
)
chat_vue = chat_vue.replace(
    ":nodes=\"parseMarkdown(formatMessage(msg.content))\"",
    ":nodes=\"parseMarkdown(formatMessage(msg.content || ''))\""
)

# 1.5 Add reasoning panel to chat.vue
template_old = '''          <view class="bubble" :class="msg.role === 'user' ? 'user-bubble' : 'ai-bubble'" @longpress="handleLongPress(msg)">
            <rich-text class="message-text" :nodes="parseMarkdown(formatMessage(msg.content))"></rich-text>
          </view>'''
template_new = '''          <view class="bubble" :class="msg.role === 'user' ? 'user-bubble' : 'ai-bubble'" @longpress="handleLongPress(msg)">
            <view v-if="msg.reasoningContent" class="reasoning-panel">
              <view class="reasoning-toggle" @click="msg._reasoningExpanded = !msg._reasoningExpanded">
                <text class="reasoning-icon">💭</text>
                <text class="reasoning-text">{{ msg.content ? '已深度思考' : '正在深度思考...' }}</text>
                <text class="reasoning-arrow">{{ msg._reasoningExpanded ? '▾' : '▸' }}</text>
              </view>
              <view v-if="msg._reasoningExpanded" class="reasoning-content">
                <rich-text :nodes="parseMarkdown(msg.reasoningContent)"></rich-text>
              </view>
            </view>
            <rich-text class="message-text" :nodes="parseMarkdown(formatMessage(msg.content || ''))"></rich-text>
          </view>'''
chat_vue = chat_vue.replace(template_old, template_new)

style_add = '''
.reasoning-panel {
  margin-bottom: 12rpx;
  border: 1px solid var(--theme-border);
  border-radius: 12rpx;
  background-color: var(--theme-surface);
  overflow: hidden;
}
.reasoning-toggle {
  display: flex;
  align-items: center;
  padding: 12rpx 16rpx;
}
.reasoning-text {
  font-size: 24rpx;
  color: var(--theme-text-secondary);
  margin-left: 8rpx;
}
.reasoning-arrow {
  margin-left: auto;
  font-size: 24rpx;
  color: var(--theme-text-muted);
}
.reasoning-content {
  padding: 16rpx;
  border-top: 1px solid var(--theme-border);
  font-size: 24rpx;
  color: var(--theme-text-secondary);
  background-color: var(--theme-bg);
}
'''
chat_vue = chat_vue.replace("</style>", style_add + "</style>")

with open('frontend-uniapp/src/pages/chat/chat.vue', 'w', encoding='utf-8') as f:
    f.write(chat_vue)

# 2. Add reasoningContent to useChatConversation.ts
with open('frontend-uniapp/src/composables/useChatConversation.ts', 'r', encoding='utf-8') as f:
    use_chat = f.read()

use_chat = use_chat.replace(
    "content: string\n  createdAt?: string",
    "content: string\n  reasoningContent?: string\n  _reasoningExpanded?: boolean\n  createdAt?: string"
)

# 3. Update replyStream chunk parsing
old_chunk = '''(chunk: string) => {
          fullReply += chunk;
          const currentLength = extractPlainText(parseMarkdown(fullReply)).length;
          if (currentLength - lastLength > 5) {
            messages.value[targetIndex].content = fullReply;
            lastLength = currentLength;
          }
        }'''

new_chunk = '''(chunk: string) => {
          if (chunk.startsWith('[[REASONING]]')) {
            const r = chunk.substring(13);
            messages.value[targetIndex].reasoningContent = (messages.value[targetIndex].reasoningContent || '') + r;
          } else {
            fullReply += chunk;
            const currentLength = extractPlainText(parseMarkdown(fullReply)).length;
            if (currentLength - lastLength > 5) {
              messages.value[targetIndex].content = fullReply;
              lastLength = currentLength;
            }
          }
        }'''

use_chat = use_chat.replace(old_chunk, new_chunk)
use_chat = use_chat.replace(
    "messages.value[targetIndex].content = fullReply || '我刚才没有组织好语言，你可以再说一遍吗？';",
    "messages.value[targetIndex].content = fullReply || (messages.value[targetIndex].reasoningContent ? '' : '我刚才没有组织好语言，你可以再说一遍吗？');\n        messages.value[targetIndex]._reasoningExpanded = false;"
)
use_chat = use_chat.replace(
    "content: msg.content,",
    "content: msg.content,\n        reasoningContent: msg.reasoningContent,\n        _reasoningExpanded: !msg.content,"
)
use_chat = use_chat.replace(
    "content: '',",
    "content: '', reasoningContent: '', _reasoningExpanded: true,"
)

with open('frontend-uniapp/src/composables/useChatConversation.ts', 'w', encoding='utf-8') as f:
    f.write(use_chat)
