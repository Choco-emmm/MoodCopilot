import codecs
content = open('D:/Code/MoodCopilot/frontend/src/components/ReferenceBar.vue', 'r', encoding='utf-8').read()

target1 = '''      <button class="ref-add-btn" @click.stop="'''
content = content.replace(target1, '''      <button class="ref-add-btn" style="display: none;" @click.stop="''')

target2 = '''const showEventPopover = ref(false)

function closePopovers() {'''

content = content.replace(target2, '''const showEventPopover = ref(false)

defineExpose({
  openDiaryPopover: () => {
    showDiaryPopover.value = true;
    showEventPopover.value = false;
  },
  openEventPopover: () => {
    showEventPopover.value = true;
    showDiaryPopover.value = false;
  }
})

function closePopovers() {''')

open('D:/Code/MoodCopilot/frontend/src/components/ReferenceBar.vue', 'w', encoding='utf-8').write(content)
print('Done')
