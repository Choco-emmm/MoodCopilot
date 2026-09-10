import codecs

with open('D:/Code/MoodCopilot/backend/moodcopilot/src/main/java/com/moodcopilot/ai/VisionService.java', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. public String describeImages(List<String> imageUrls) { return describeImages(imageUrls, null); }
content = content.replace(
    'public String describeImages(List<String> imageUrls) {\n        return describeImages(imageUrls, null);\n    }',
    'public String describeImages(List<String> imageUrls) {\n        return describeImages(imageUrls, null, null);\n    }'
)

# 2. public String describeImages(List<String> imageUrls, List<DiaryImageMeta> imageMeta) {
content = content.replace(
    'public String describeImages(List<String> imageUrls, List<DiaryImageMeta> imageMeta) {',
    'public String describeImages(List<String> imageUrls, List<DiaryImageMeta> imageMeta, String diaryContent) {'
)

# 3. String desc = describeWithOcrRouting(accessibleUrl, task.index(), task.channel());
content = content.replace(
    'String desc = describeWithOcrRouting(accessibleUrl, task.index(), task.channel());',
    'String desc = describeWithOcrRouting(accessibleUrl, task.index(), task.channel(), diaryContent);'
)

# 4. private String describeWithOcrRouting(String imageUrl, int index, String channel) {
content = content.replace(
    'private String describeWithOcrRouting(String imageUrl, int index, String channel) {',
    'private String describeWithOcrRouting(String imageUrl, int index, String channel, String diaryContent) {'
)

# 5. Modify visualPrompt
old_prompt = '''String visualPrompt = "请用一句话描述这张图片的画面内容、拍摄类型（如自拍/风景/美食/截图/手写等）与情感氛围（40字以内）。不要评价图片质量。" +
                "注意：只需要描述你看到的画面本身，不要提及画面中的文字内容。";'''
new_prompt = '''String visualPrompt = "请用一句话描述这张图片的画面内容、拍摄类型（如自拍/风景/美食/截图/手写等）与情感氛围（40字以内）。不要评价图片质量。" +
                "注意：只需要描述你看到的画面本身，不要提及画面中的文字内容。";
        if (diaryContent != null && !diaryContent.isBlank()) {
            visualPrompt += "\\n这可能与用户的日记内容有关，请推测图片和日记的联系，重点关注与日记相关的画面细节。日记内容参考：\\n" +
                    (diaryContent.length() > 500 ? diaryContent.substring(0, 500) + "..." : diaryContent);
        }'''
content = content.replace(old_prompt, new_prompt)

with open('D:/Code/MoodCopilot/backend/moodcopilot/src/main/java/com/moodcopilot/ai/VisionService.java', 'w', encoding='utf-8') as f:
    f.write(content)
print("VisionService patched successfully.")
