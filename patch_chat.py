import os
import re

p = "frontend/src/pages/ChatPage.vue"
with open(p, "r", encoding="utf-8") as f:
    content = f.read()

target = '''        <div class="chat-mobile-conv">
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
          </select>'''
        
replacement = '''        <div class="chat-mobile-conv">
          <n-dropdown 
            trigger="click" 
            :options="mobileConvOptions"
            @select="selectConversation"
            style="max-height: 400px; overflow-y: auto;"
          >
            <n-button text class="chat-mobile-conv-select" style="font-size: 16px; font-weight: 600;">
              {{ activeConvTitle }} ▾
            </n-button>
          </n-dropdown>'''

target = target.replace('\r\n', '\n')
content = content.replace('\r\n', '\n')

if target in content:
    content = content.replace(target, replacement)
    # Add computed imports
    if "mobileConvOptions" not in content:
        content = content.replace("function handleMobileConversationChange(e: Event) {", '''
const activeConvTitle = computed(() => {
  const conv = conversations.value.find(c => c.id === activeConvId.value)
  return conv ? displayConversationTitle(conv.title, conv.id) : '选择对话'
})

const mobileConvOptions = computed(() => {
  return conversations.value.map(c => ({
    label: displayConversationTitle(c.title, c.id),
    key: c.id
  }))
})

function handleMobileConversationChange(e: Event) {''')
        content = content.replace("import { NButton } from 'naive-ui'", "import { NButton, NDropdown } from 'naive-ui'")
    
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("Patched " + p)
else:
    print("Target not found in " + p)

