with open('frontend/src/components/chat/ChatStreamingItem.vue', 'r', encoding='utf-8') as f:
    content = f.read()

# Add prop
content = content.replace("streamingText: { type: String, default: '' },", "streamingText: { type: String, default: '' },\n  streamingReasoning: { type: String, default: '' },")

# Add isReasoningExpanded
content = content.replace('const showStreamingRefs = ref(false)', 'const showStreamingRefs = ref(false)\nconst isReasoningExpanded = ref(true)\n\nfunction toggleReasoning() {\n  isReasoningExpanded.value = !isReasoningExpanded.value\n}')

# Remove old parsedStreaming that parsed <think>
parsed_old = '''const parsedStreaming = computed(() => {
  const content = props.streamingText
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

parsed_new = '''const parsedStreaming = computed(() => {
  return {
    think: props.streamingReasoning || '',
    text: props.streamingText || ''
  }
})'''
content = content.replace(parsed_old, parsed_new)

# Add reasoning panel UI
template_old = '''        <div v-else-if="parsedStreaming.think && !parsedStreaming.text" class="thinking-status">
          <span class="sparkle-icon">✨</span>
          <span class="thinking-text">深度思考中</span>
          <span class="thinking-dots-inline">
            <span class="dot animate-bounce" style="animation-delay: 0ms"></span>
            <span class="dot animate-bounce" style="animation-delay: 150ms"></span>
            <span class="dot animate-bounce" style="animation-delay: 300ms"></span>
          </span>
        </div>

        <div v-else-if="!parsedStreaming.text && !parsedStreaming.think" class="thinking-status">
          <span class="sparkle-icon">✨</span>
          <span class="thinking-text">MoodCopilot 正在思考</span>
          <span class="typing-dots"></span>
        </div>

        <template v-if="parsedStreaming.text">
          <div class="md-content" v-html="renderStreamingMd(parsedStreaming.text, true)"></div>
        </template>'''

template_new = '''        <div v-else-if="!parsedStreaming.text && !parsedStreaming.think" class="thinking-status">
          <span class="sparkle-icon">✨</span>
          <span class="thinking-text">MoodCopilot 正在思考</span>
          <span class="typing-dots"></span>
        </div>
        
        <template v-if="parsedStreaming.think">
          <div class="reasoning-panel">
            <button class="reasoning-toggle" @click="toggleReasoning">
              <span class="reasoning-icon">💭</span>
              <span>正在深度思考...</span>
              <span class="reasoning-arrow">{{ isReasoningExpanded ? '▾' : '▸' }}</span>
            </button>
            <div v-if="isReasoningExpanded" class="reasoning-content md-content" v-html="renderStreamingMd(parsedStreaming.think, !parsedStreaming.text)"></div>
          </div>
        </template>

        <template v-if="parsedStreaming.text">
          <div class="md-content" v-html="renderStreamingMd(parsedStreaming.text, true)"></div>
        </template>'''
content = content.replace(template_old, template_new)

with open('frontend/src/components/chat/ChatStreamingItem.vue', 'w', encoding='utf-8') as f:
    f.write(content)
