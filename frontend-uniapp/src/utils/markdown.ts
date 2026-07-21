export const parseMarkdown = (text: string) => {
  if (!text) return '';
  let html = text
    // Remove <think> tags (already done in chat.vue, but safe here)
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    // Bold
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    // Italic
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    // Line breaks to <br/>
    .replace(/\n/g, '<br/>');
  return html;
};

export const unescapeHtml = (text: string) => {
  if (!text) return '';
  return text
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&nbsp;/g, ' ');
};

export const formatDiaryContent = (text: string) => {
  if (!text) return '';
  let unescaped = unescapeHtml(text);
  if (unescaped.includes('<p>') || unescaped.includes('<div') || unescaped.includes('<br') || unescaped.includes('<h')) {
    unescaped = unescaped.replace(/<p>/gi, '<p style="margin: 0 0 16px 0; line-height: 1.8;">');
    unescaped = unescaped.replace(/<img /gi, '<img style="max-width: 100%; border-radius: 8px;" ');
    return unescaped;
  }
  return unescaped.replace(/\n/g, '<br/>');
};

export const extractPlainText = (text: string) => {
  if (!text) return '';
  let unescaped = unescapeHtml(text);
  return unescaped.replace(/<[^>]+>/g, '').replace(/\n/g, ' ').trim();
};
