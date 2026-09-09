import sys, codecs; sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer)
with open('D:/Code/MoodCopilot/backend/moodcopilot/src/main/java/com/moodcopilot/ai/MemoryExtractionService.java', 'r', encoding='utf-8') as f:
    content = f.read()

target = '''    // 第一层：硬门槛，过滤无信息量噪声。
    // 但如果短消息中包含长期特征关键词（如"总是""习惯""关系"），放行进入后续评分。
    if (isLikelySmallTalk(normalizedUserMessage) && normalizedRefs.isEmpty()) {
        log.info("memory-chat | skip | reason=small_talk | userId={} | userLength={}", userId,
                normalizedUserMessage.length());
        return;
    }'''

content = content.replace(target, '''    // 取消字数与关键词硬拦截，直接进入模型分析
    // if (isLikelySmallTalk(normalizedUserMessage) && normalizedRefs.isEmpty()) { ... }''')

with open('D:/Code/MoodCopilot/backend/moodcopilot/src/main/java/com/moodcopilot/ai/MemoryExtractionService.java', 'w', encoding='utf-8') as f:
    f.write(content)
print('Done')
