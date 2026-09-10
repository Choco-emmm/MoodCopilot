import codecs

with open('D:/Code/MoodCopilot/backend/moodcopilot/src/main/java/com/moodcopilot/diary/DiaryService.java', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'String imageDescriptions = visionService.describeImages(images, imageMeta);',
    'String imageDescriptions = visionService.describeImages(images, imageMeta, content);'
)

with open('D:/Code/MoodCopilot/backend/moodcopilot/src/main/java/com/moodcopilot/diary/DiaryService.java', 'w', encoding='utf-8') as f:
    f.write(content)
print("DiaryService patched successfully.")
