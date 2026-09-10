import codecs

with open('D:/Code/MoodCopilot/frontend/src/components/memory/MemoryProfileView.vue', 'r', encoding='utf-8') as f:
    content = f.read()

# find <small>这些内容还没有正式 or similar
old_line = '<small>这些内容还没有正式，确认后将成为长期记忆。</small>'
# Need to know exact text
import re
match = re.search(r'<small>(.*?)</small>', content)
if match:
    original_text = match.group(1)
    new_text = original_text + " 如果 AI 再次观察到相同的记忆规律，也会自动转正。"
    content = content.replace(f'<small>{original_text}</small>', f'<small>{new_text}</small>')
    with open('D:/Code/MoodCopilot/frontend/src/components/memory/MemoryProfileView.vue', 'w', encoding='utf-8') as f:
        f.write(content)
    print("Replaced with:", new_text)
else:
    print("Not found")

