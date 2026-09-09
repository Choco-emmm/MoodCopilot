import sys, codecs; sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer)
content = open('D:/Code/MoodCopilot/backend/moodcopilot/src/main/java/com/moodcopilot/auth/AuthService.java', 'r', encoding='utf-8').read()
content = content.replace('!newName.matches(\"^[a-zA-Z0-9_-]{4,20}$\")', '!newName.matches(\"^[a-zA-Z0-9\\\\u4e00-\\\\u9fa5_-]{2,20}$\")')
content = content.replace('账号ID需为 4-20 位英文字母、数字、下划线或横线', '账号ID需为 2-20 位中英文字母、数字、下划线或横线')
with open('D:/Code/MoodCopilot/backend/moodcopilot/src/main/java/com/moodcopilot/auth/AuthService.java', 'w', encoding='utf-8') as f:
    f.write(content)
print('Done')
