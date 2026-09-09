import os

p = "frontend/src/composables/useChat.ts"
with open(p, "r", encoding="utf-8") as f:
    content = f.read()

target = "deleteConversation: conv.deleteConversation,"
replacement = '''deleteConversation: conv.deleteConversation,
    renameConversation: conv.renameConversation,'''

content = content.replace('\r\n', '\n')

if target in content:
    content = content.replace(target, replacement)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("Patched useChat")
else:
    print("useChat target not found")

