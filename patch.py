
import os

paths = ["frontend/src/api/social.ts", "frontend-uniapp/src/utils/request.ts", "frontend-uniapp/src/api/social.ts"]

for p in paths:
    if not os.path.exists(p):
        continue
    with open(p, "r", encoding="utf-8") as f:
        content = f.read()
    
    target = "createConversation: (title?: string) => api.post('" + "/chat/conversations" + "', { title: title || '' }),"
    replacement = target + "\n    updateConversationTitle: (id: number, title: string) => api.put(`/chat/conversations/${id}/title`, { title }),"
    
    if target in content:
        with open(p, "w", encoding="utf-8") as f:
            f.write(content.replace(target, replacement))
        print("Patched " + p)
    else:
        print("Target not found in " + p)


