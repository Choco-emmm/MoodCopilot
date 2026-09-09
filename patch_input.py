import os

p = "frontend/src/components/chat/ChatInputBox.vue"
with open(p, "r", encoding="utf-8") as f:
    content = f.read()

target = '''      <n-input
        :value="draft"
        @update:value="$emit('update:draft', $event)"
        size="large"
        :placeholder="isCompressing ? '正在优化对话上下文，请稍候...' : '聊聊你今天的心情...'"
        :disabled="streaming || isCompressing || disabled"
        :maxlength="500"
        @focus="$emit('focus')"
        @keydown.enter.prevent="!isCompressing && !streaming && $emit('send-enter', $event)"
      />'''

replacement = '''      <n-input
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 5 }"
        :value="draft"
        @update:value="$emit('update:draft', $event)"
        size="large"
        :placeholder="isCompressing ? '正在优化对话上下文，请稍候...' : '聊聊你今天的心情...'"
        :disabled="streaming || isCompressing || disabled"
        :maxlength="500"
        @focus="$emit('focus')"
        @keydown.enter.prevent="!isCompressing && !streaming && $emit('send-enter', $event)"
      />'''

content = content.replace('\r\n', '\n')
target = target.replace('\r\n', '\n')

if target in content:
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(content.replace(target, replacement))
    print("Patched ChatInputBox")
else:
    print("ChatInputBox target not found")

