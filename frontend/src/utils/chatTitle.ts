const REF_REMINDER = '请优先结合我引用的日记内容来回应，不要忽略日记中的具体细节和情绪'
const DEFAULT_CHAT_TITLE = '新聊天'

export function isPlaceholderConversationTitle(title: string | undefined): boolean {
  if (!title || !title.trim()) return true
  const value = title.trim()
  return value === '新聊天'
    || value === '新对话'
    || !cleanConversationTitle(value)
}

export function displayConversationTitle(title: string | undefined, _id?: number): string {
  return cleanConversationTitle(title) || DEFAULT_CHAT_TITLE
}

export function cleanConversationTitle(title: string | undefined): string {
  if (!title) return ''
  let value = title.trim()

  for (const prefix of [`（${REF_REMINDER}）`, `(${REF_REMINDER})`]) {
    if (value.startsWith(prefix)) {
      value = value.slice(prefix.length).trim()
      break
    }
  }
  if (value.startsWith('（请优先结合') || value.startsWith('(请优先结合')) return ''

  if (value.startsWith('[重点跟进事件]')) {
    const separator = value.indexOf('\n\n')
    value = separator >= 0 ? value.slice(separator + 2).trim() : ''
  }

  if (value.startsWith('[用户引用了之前的发言：')) {
    const marker = ']\n\n用户的回复是：'
    const markerIndex = value.indexOf(marker)
    value = markerIndex >= 0 ? value.slice(markerIndex + marker.length).trim() : ''
  }

  return value.replace(/\s+/g, ' ').trim()
}
