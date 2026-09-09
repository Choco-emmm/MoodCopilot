import re

with open('frontend/src/components/chat/ChatMessageItem.vue', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update parsedContent
parsed_old = '''const parsedContent = computed(() => {
  const content = props.msg.content
  if (!content) return { think: '', text: '' }

  let think = ''
  let text = content.replace(/<think>([\s\S]*?)<\/think>/g, (match, innerThink) => {
    think += (think ? '\\n\\n' : '') + innerThink.trim()
    return ''
  })

  const unclosedMatch = text.match(/<think>([\s\S]*)$/)
  if (unclosedMatch) {
    think += (think ? '\\n\\n' : '') + unclosedMatch[1].trim()
    text = text.substring(0, unclosedMatch.index)
  }

  return {
    think: think.trim(),
    text: text.trimStart()
  }
})'''

parsed_new = '''const parsedContent = computed(() => {
  const content = props.msg.content || ''
  let think = props.msg.reasoningContent || ''
  
  if (!content && !think) return { think: '', text: '' }

  let text = content
  if (!think) {
    text = content.replace(/<think>([\s\S]*?)<\/think>/g, (match, innerThink) => {
      think += (think ? '\\n\\n' : '') + innerThink.trim()
      return ''
    })

    const unclosedMatch = text.match(/<think>([\s\S]*)$/)
    if (unclosedMatch) {
      think += (think ? '\\n\\n' : '') + unclosedMatch[1].trim()
      text = text.substring(0, unclosedMatch.index)
    }
  }

  return {
    think: think.trim(),
    text: text.trimStart()
  }
})'''

content = content.replace(parsed_old, parsed_new)

# 2. Add isReasoningExpanded state and watch
script_old = 'const isRefsExpanded = ref(false)'
script_new = '''const isRefsExpanded = ref(false)
const isReasoningExpanded = ref(props.msg.status === 'streaming' || props.msg.status === 'pending')

import { watch } from 'vue'
watch(() => props.msg.status, (newStatus) => {
  if (newStatus === 'success' || newStatus === 'error') {
    isReasoningExpanded.value = false
  }
})

function toggleReasoning() {
  isReasoningExpanded.value = !isReasoningExpanded.value
}
'''
content = content.replace(script_old, script_new)

# 3. Add UI elements for reasoning content
template_old = '''        <template v-if="msg.role === 'ai'">
          <!-- think 块内容不对用户展示，只显示正文 -->
          <div v-if="parsedContent.text" class="md-content" v-html="renderMd(parsedContent.text)" />
          <!-- 如果只有 think 没有正文（消息异常时的兜底） -->
          <span v-else class="ai-think-placeholder">...</span>'''

template_new = '''        <template v-if="msg.role === 'ai'">
          <!-- Reasoning Block -->
          <div v-if="parsedContent.think" class="reasoning-panel">
            <button class="reasoning-toggle" @click="toggleReasoning">
              <span class="reasoning-icon">💭</span>
              <span>{{ msg.status === 'streaming' ? '正在深度思考...' : '已深度思考' }}</span>
              <span class="reasoning-arrow">{{ isReasoningExpanded ? '▾' : '▸' }}</span>
            </button>
            <div v-if="isReasoningExpanded" class="reasoning-content md-content" v-html="renderMd(parsedContent.think)"></div>
          </div>
          
          <div v-if="parsedContent.text" class="md-content" v-html="renderMd(parsedContent.text)" />
          <span v-else-if="!parsedContent.think" class="ai-think-placeholder">...</span>'''

content = content.replace(template_old, template_new)

with open('frontend/src/components/chat/ChatMessageItem.vue', 'w', encoding='utf-8') as f:
    f.write(content)
