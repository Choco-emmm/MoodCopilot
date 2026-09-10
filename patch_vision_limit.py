import codecs

with open('D:/Code/MoodCopilot/backend/moodcopilot/src/main/java/com/moodcopilot/ai/VisionService.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the 500 truncation logic with full diaryContent
old_line = '                    (diaryContent.length() > 500 ? diaryContent.substring(0, 500) + "..." : diaryContent);'
new_line = '                    diaryContent;'

content = content.replace(old_line, new_line)

with open('D:/Code/MoodCopilot/backend/moodcopilot/src/main/java/com/moodcopilot/ai/VisionService.java', 'w', encoding='utf-8') as f:
    f.write(content)
print("VisionService limit patched successfully.")
