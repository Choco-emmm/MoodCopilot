with open('frontend/src/pages/ChatPage.vue', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('draft, streaming, streamingText, isThinking', 'draft, streaming, streamingText, streamingReasoning, isThinking')
content = content.replace(':streaming-text="streamingText"', ':streaming-text="streamingText"\n            :streaming-reasoning="streamingReasoning"')

with open('frontend/src/pages/ChatPage.vue', 'w', encoding='utf-8') as f:
    f.write(content)
