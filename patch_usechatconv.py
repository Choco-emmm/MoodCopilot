import os

p = "frontend/src/composables/useChatConversation.ts"
with open(p, "r", encoding="utf-8") as f:
    content = f.read()

target = "async function deleteConversation(id: number) {"
replacement = '''async function renameConversation(id: number, oldTitle: string) {
    const newTitle = window.prompt("请输入新会话名称:", oldTitle);
    if (newTitle !== null && newTitle.trim() !== "") {
      try {
        await chatApi.updateConversationTitle(id, newTitle.trim());
        const conv = conversations.value.find(c => c.id === id);
        if (conv) conv.title = newTitle.trim();
      } catch (err: any) {
        window.alert(err.message || "修改失败");
      }
    }
  }

  async function deleteConversation(id: number) {'''

target2 = "deleteConversation,"
replacement2 = '''deleteConversation,
    renameConversation,'''

content = content.replace('\r\n', '\n')

if target in content:
    content = content.replace(target, replacement).replace(target2, replacement2)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("Patched useChatConversation")
else:
    print("useChatConversation target not found")

