import os

p = "frontend/src/components/chat/ChatSidebar.vue"
with open(p, "r", encoding="utf-8") as f:
    content = f.read()

target = '''        <button
          class="conv-delete"
          @click.stop="$emit('delete', conv.id)"
        >&times;</button>'''

replacement = '''        <button
          class="conv-edit"
          title="重命名"
          @click.stop="$emit('rename', conv.id, conv.title)"
        >✎</button>
        <button
          class="conv-delete"
          @click.stop="$emit('delete', conv.id)"
        >&times;</button>'''

target2 = "    (e: 'delete', id: number): void"
replacement2 = '''    (e: 'delete', id: number): void
  (e: 'rename', id: number, oldTitle: string): void'''

target3 = ".conv-delete {"
replacement3 = '''.conv-edit {
  opacity: 0;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: opacity 0.15s;
  font-family: inherit;
}
.conv-item:hover .conv-edit {
  opacity: 1;
}
.conv-delete {'''

content = content.replace('\r\n', '\n')
target = target.replace('\r\n', '\n')

if target in content:
    content = content.replace(target, replacement).replace(target2, replacement2).replace(target3, replacement3)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("Patched ChatSidebar")
else:
    print("ChatSidebar target not found")

