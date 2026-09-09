import sys, codecs; sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer)
with open('D:/Code/MoodCopilot/frontend/src/stores/notification.ts', 'r', encoding='utf-8') as f:
    content = f.read()

target = '''import { logWarn } from '../utils/logger'
import { useReportStore } from './report'

export interface Notification {'''

replacement = '''import { logWarn } from '../utils/logger'
import { useReportStore } from './report'
import { renderSafeMarkdown } from '../utils/markdown'

export interface Notification {'''

content = content.replace(target, replacement)

target2 = '''    window..create({
      title,
      content: () => h('div', null, [
        h('div', { style: 'font-weight: bold; margin-bottom: 8px;' }, message || fallbackMessage),
        ...nodes
      ]),
      meta: new Date().toLocaleTimeString(),
      duration: 12000,
      keepAliveOnHover: true
    })'''

replacement2 = '''    window..create({
      title,
      content: () => h('div', null, [
        h('div', { 
          style: 'font-weight: normal; margin-bottom: 8px;', 
          innerHTML: renderSafeMarkdown(message || fallbackMessage) 
        }),
        ...nodes
      ]),
      meta: new Date().toLocaleTimeString(),
      duration: 5000,
      keepAliveOnHover: true,
      closable: true
    })'''

content = content.replace(target2, replacement2)

with open('D:/Code/MoodCopilot/frontend/src/stores/notification.ts', 'w', encoding='utf-8') as f:
    f.write(content)
print('Done')
