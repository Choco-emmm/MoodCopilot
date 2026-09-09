import sys, codecs; sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer)
with open('D:/Code/MoodCopilot/frontend/src/components/profile/ProfileSettingsModal.vue', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(\"personaMsg.value = 'AI 个性已更新'\", \"window..success('AI 个性已更新')\\n    emit('update:show', false)\")
content = content.replace(\"nameMsg.value = '用户名已更新'\", \"window..success('用户名已更新')\\n    emit('update:show', false)\")
content = content.replace(\"signatureMsg.value = '个性签名已更新'\", \"window..success('个性签名已更新')\\n    emit('update:show', false)\")

with open('D:/Code/MoodCopilot/frontend/src/components/profile/ProfileSettingsModal.vue', 'w', encoding='utf-8') as f:
    f.write(content)
print('Done')
