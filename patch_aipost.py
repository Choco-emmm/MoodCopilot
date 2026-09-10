import codecs

with open('D:/Code/MoodCopilot/backend/moodcopilot/src/main/java/com/moodcopilot/ai/AiPostProcessService.java', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'return visionService.describeImages(diary.getImages(), diary.getImageMeta());',
    'return visionService.describeImages(diary.getImages(), diary.getImageMeta(), diary.getContent());'
)

with open('D:/Code/MoodCopilot/backend/moodcopilot/src/main/java/com/moodcopilot/ai/AiPostProcessService.java', 'w', encoding='utf-8') as f:
    f.write(content)
print("AiPostProcessService patched successfully.")
