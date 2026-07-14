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
