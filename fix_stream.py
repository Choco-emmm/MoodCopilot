with open('frontend/src/composables/useChatStream.ts', 'r', encoding='utf-8') as f:
    content = f.read()

import re

# Add streamingReasoning to refs
content = content.replace("const streamingText = ref('')", "const streamingText = ref('')\n  const streamingReasoning = ref('')")

# Update resumeActiveRun chunk handler
old_handler1 = '''(chunk: string) => {
            fullReply += chunk
            pendingStreamText = fullReply
            if (isThinking.value) isThinking.value = false
            if (streamRafId === null) {
              streamRafId = requestAnimationFrame(() => {
                const keepScroll = scrollManager.isNearBottom()
                streamingText.value = pendingStreamText
                streamRafId = null
                if (keepScroll) scrollManager.scrollBottom()
              })
            }
          }'''

new_handler1 = '''(chunk: string) => {
            if (chunk.startsWith('[[REASONING]]')) {
              fullReasoning += chunk.substring(13)
            } else {
              fullReply += chunk
            }
            pendingStreamText = fullReply
            if (isThinking.value && chunk && !chunk.startsWith('[[REASONING]]')) isThinking.value = false
            if (streamRafId === null) {
              streamRafId = requestAnimationFrame(() => {
                const keepScroll = scrollManager.isNearBottom()
                streamingText.value = pendingStreamText
                streamingReasoning.value = fullReasoning
                streamRafId = null
                if (keepScroll) scrollManager.scrollBottom()
              })
            }
          }'''
content = content.replace(old_handler1, new_handler1)

# Update sendReply chunk handler
old_handler2 = '''(chunk: string) => {
          fullReply += chunk
          pendingStreamText = fullReply
          if (isCompressing.value) isCompressing.value = false
          if (isThinking.value) isThinking.value = false
          if (streamRafId === null) {
            streamRafId = requestAnimationFrame(() => {
              const keepScroll = scrollManager.isNearBottom()
              streamingText.value = pendingStreamText
              streamRafId = null
              if (keepScroll) scrollManager.scrollBottom()
            })
          }
        }'''

new_handler2 = '''(chunk: string) => {
          if (chunk.startsWith('[[REASONING]]')) {
            fullReasoning += chunk.substring(13)
          } else {
            fullReply += chunk
          }
          pendingStreamText = fullReply
          if (isCompressing.value) isCompressing.value = false
          if (isThinking.value && chunk && !chunk.startsWith('[[REASONING]]')) isThinking.value = false
          if (streamRafId === null) {
            streamRafId = requestAnimationFrame(() => {
              const keepScroll = scrollManager.isNearBottom()
              streamingText.value = pendingStreamText
              streamingReasoning.value = fullReasoning
              streamRafId = null
              if (keepScroll) scrollManager.scrollBottom()
            })
          }
        }'''
content = content.replace(old_handler2, new_handler2)

# Reset streamingReasoning on start
content = content.replace("streamingText.value = ''\n    isThinking.value = true", "streamingText.value = ''\n    streamingReasoning.value = ''\n    isThinking.value = true")

# Add let fullReasoning = ''
content = content.replace("let fullReply = ''\n      let currentRefs: RagRef[] = []", "let fullReply = ''\n      let fullReasoning = ''\n      let currentRefs: RagRef[] = []")
content = content.replace("let fullReply = ''\n    let currentRefs: RagRef[] = []", "let fullReply = ''\n    let fullReasoning = ''\n    let currentRefs: RagRef[] = []")


# Push message with reasoningContent
old_push1 = '''messages.value.push({
            id: `${runId}:assistant`,
            role: 'ai',
            content: fullReply || '我刚才没有组织好语言，你可以再说一遍吗？',
            createdAt: new Date().toISOString(),
            ragReferences: currentRefs.length ? currentRefs : undefined,
          })'''
new_push1 = '''messages.value.push({
            id: `${runId}:assistant`,
            role: 'ai',
            content: fullReply || (fullReasoning ? '' : '我刚才没有组织好语言，你可以再说一遍吗？'),
            reasoningContent: fullReasoning || undefined,
            createdAt: new Date().toISOString(),
            ragReferences: currentRefs.length ? currentRefs : undefined,
          })'''
content = content.replace(old_push1, new_push1)

old_push2 = '''messages.value.push({
        id: nextMsgId(), role: 'ai',
        content: fullReply || '我刚才没有组织好语言，你可以再说一遍吗？',
        createdAt: new Date().toISOString(),
        ragReferences: currentRefs.length ? currentRefs : undefined,
      })'''
new_push2 = '''messages.value.push({
        id: nextMsgId(), role: 'ai',
        content: fullReply || (fullReasoning ? '' : '我刚才没有组织好语言，你可以再说一遍吗？'),
        reasoningContent: fullReasoning || undefined,
        createdAt: new Date().toISOString(),
        ragReferences: currentRefs.length ? currentRefs : undefined,
      })'''
content = content.replace(old_push2, new_push2)

# Add streamingReasoning to return
content = content.replace("draft, streaming, streamingText, isThinking", "draft, streaming, streamingText, streamingReasoning, isThinking")

with open('frontend/src/composables/useChatStream.ts', 'w', encoding='utf-8') as f:
    f.write(content)
