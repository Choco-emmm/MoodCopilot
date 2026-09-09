import os

p = "frontend/src/pages/ChatPage.vue"
with open(p, "r", encoding="utf-8") as f:
    content = f.read()

target = "@delete=\"deleteConversation\""
replacement = '''@delete="deleteConversation"
        @rename="renameConversation"'''

target2 = "createConversation, selectConversation, deleteConversation,"
replacement2 = "createConversation, selectConversation, deleteConversation, renameConversation,"

content = content.replace('\r\n', '\n')

if target in content and target2 in content:
    content = content.replace(target, replacement).replace(target2, replacement2)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("Patched ChatPage")
else:
    print("ChatPage target not found")

