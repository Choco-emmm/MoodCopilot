import os

# Fix ChatSidebar
p1 = "frontend/src/components/chat/ChatSidebar.vue"
with open(p1, "r", encoding="utf-8") as f:
    c1 = f.read()

t1 = "    (e: 'delete', id: number): void\n  }>()"
r1 = "    (e: 'delete', id: number): void\n    (e: 'rename', id: number, oldTitle: string): void\n  }>()"
if t1 in c1:
    with open(p1, "w", encoding="utf-8", newline="\n") as f:
        f.write(c1.replace(t1, r1))
    print("Fixed ChatSidebar")

# Fix ChatPage
p2 = "frontend/src/pages/ChatPage.vue"
with open(p2, "r", encoding="utf-8") as f:
    c2 = f.read()

t2 = "  } = useChat()"
r2 = '''  } = useChat()

  const activeConvTitle = computed(() => {
    const conv = conversations.value.find((c: any) => c.id === activeConvId.value)
    return conv ? displayConversationTitle(conv.title, conv.id) : '选择对话'
  })

  const mobileConvOptions = computed(() => {
    return conversations.value.map((c: any) => ({
      label: displayConversationTitle(c.title, c.id),
      key: c.id
    }))
  })'''

if t2 in c2:
    with open(p2, "w", encoding="utf-8", newline="\n") as f:
        f.write(c2.replace(t2, r2))
    print("Fixed ChatPage")

