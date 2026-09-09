with open('frontend/src/composables/useChat.ts', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace("draft: stream.draft,\n    streaming: stream.streaming,\n    streamingText", "draft: stream.draft,\n    streaming: stream.streaming,\n    streamingReasoning: stream.streamingReasoning,\n    streamingText")

with open('frontend/src/composables/useChat.ts', 'w', encoding='utf-8') as f:
    f.write(content)
