/**
 * Escape HTML special characters to prevent XSS.
 * Must be called BEFORE applying markdown transformations,
 * so that user-supplied HTML becomes inert entities while
 * the markdown-generated tags (<strong>, <em>, <br/>) remain active.
 */
const escapeHtml = (text: string): string => {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
};

export const parseMarkdown = (text: string) => {
  if (!text) return '';
  let html = text
    // Remove <think> tags (AI thinking blocks, safe to strip before escaping)
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    // Escape HTML to prevent XSS — any user/AI-supplied HTML becomes inert
    ;
  html = escapeHtml(html);
  // Now apply markdown transformations (these add safe, known tags)
  html = html
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
  const unescaped = unescapeHtml(text);
  if (!/<\/?(p|div|br|h[1-6]|ul|ol|li|blockquote|strong|em|u)\b/i.test(unescaped)) {
    return escapeHtml(unescaped).replace(/\n/g, '<br/>');
  }

  // rich-text receives user and AI content. Keep only structural tags and drop
  // every attribute so inline event handlers, styles and unsafe URLs cannot pass through.
  return unescaped
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<\/?(script|style|iframe|object|embed|form|input|button|img)\b[^>]*>/gi, '')
    .replace(/<\/?([a-z][\w-]*)(?:\s[^>]*)?>/gi, (_match, tag: string) => {
      const allowed = ['p', 'br', 'strong', 'em', 'u', 'ul', 'ol', 'li', 'blockquote', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'div'];
      return allowed.includes(tag.toLowerCase()) ? `<${_match.startsWith('</') ? '/' : ''}${tag.toLowerCase()}>` : '';
    })
    .replace(/\n/g, '<br/>');
};

export const extractPlainText = (text: string) => {
  if (!text) return '';
  let str = text;
  for (let i = 0; i < 3; i++) {
    if (str.includes('&lt;') || str.includes('&gt;') || str.includes('&amp;') || str.includes('&quot;') || str.includes('&#39;') || str.includes('&nbsp;')) {
      str = unescapeHtml(str);
    } else {
      break;
    }
  }
  str = str.replace(/<[^>]+>/g, '');
  str = str.replace(/&[a-zA-Z0-9#]+;/g, ' ');
  return str.replace(/\r?\n/g, ' ').replace(/\s+/g, ' ').trim();
};
