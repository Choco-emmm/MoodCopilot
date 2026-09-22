import re

with open("frontend/src/pages/ChatPage.vue", "r", encoding="utf-8") as f:
    code = f.read()

event_copy = """    createEventFunction: {
      title: '要创建一条新事件',
      hint: '确认之后才会创建，以后可以在事件页面修改。',
      confirm: '确认创建',
      reject: '先不创建',
      reasonLabel: '不想创建的话，可以说一句原因',
      reasonPlaceholder: '拒绝的话说一句原因，例如：时间不对',
    },
"""

if "createEventFunction:" not in code:
    code = code.replace("saveMemory: {", event_copy + "    saveMemory: {")

with open("frontend/src/pages/ChatPage.vue", "w", encoding="utf-8") as f:
    f.write(code)

print("Updated ChatPage.vue")
