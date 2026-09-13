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

  let normalized = text
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    .replace(/\r\n/g, '\n');

  if (!normalized.trim()) return '';

  const lines = normalized.split('\n');
  const blocks: string[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    const tableHeaderMatch = line.match(/^\s*\|?(.*\|.*)\|?\s*$/);
    const nextLine = lines[i + 1] || '';
    const isDividerRow = /^\s*\|?(?:\s*:?-{3,}:?\s*\|)+\s*$/.test(nextLine.trim());

    if (tableHeaderMatch && isDividerRow) {
      const headerCells = tableHeaderMatch[1].split('|').map(cell => cell.trim());
      const rows: string[] = [];
      let j = i + 2;

      while (j < lines.length) {
        const row = lines[j].trim();
        if (!row || !row.includes('|')) break;
        const cells = row.replace(/^\|/, '').replace(/\|$/, '').split('|').map(cell => cell.trim());
        rows.push(cells.map(cell => `<td>${escapeHtml(cell)}</td>`).join(''));
        j += 1;
      }

      const head = headerCells
        .map(cell => `<th>${escapeHtml(cell)}</th>`)
        .join('');
      const body = rows.length ? `<tbody>${rows.map(row => `<tr>${row}</tr>`).join('')}</tbody>` : '';
      blocks.push(`<table><thead><tr>${head}</tr></thead>${body}</table>`);
      i = j;
      continue;
    }

    if (/^\s*#{1,6}\s+/.test(line)) {
      const level = Math.min(6, Math.max(1, (line.match(/^\s*#+/) || [''])[0].trim().length));
      const text = line.replace(/^\s*#{1,6}\s+/, '').trim();
      blocks.push(`<h${level}>${formatInlineMarkdown(text)}</h${level}>`);
      i += 1;
      continue;
    }

    if (!line.trim()) {
      i += 1;
      continue;
    }

    const paragraphLines: string[] = [];
    while (i < lines.length && lines[i].trim() && !/^\s*#{1,6}\s+/.test(lines[i]) && !isTableStart(lines, i)) {
      paragraphLines.push(lines[i].trim());
      i += 1;
    }

    if (paragraphLines.length) {
      blocks.push(`<p>${formatInlineMarkdown(paragraphLines.join(' '))}</p>`);
      continue;
    }

    i += 1;
  }

  return blocks.join('');
};

function isTableStart(lines: string[], index: number): boolean {
  const header = lines[index] || '';
  const next = lines[index + 1] || '';
  return /^\s*\|?(.*\|.*)\|?\s*$/.test(header)
    && /^\s*\|?(?:\s*:?-{3,}:?\s*\|)+\s*$/.test(next.trim());
}

function formatInlineMarkdown(text: string): string {
  const escaped = escapeHtml(text);
  return escaped
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/__(.+?)__/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/_(.+?)_/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
}

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
  if (!/<\/?(p|div|br|h[1-6]|ul|ol|li|blockquote|strong|em|u|table|thead|tbody|tr|th|td|code|pre|a)\b/i.test(unescaped)) {
    return escapeHtml(unescaped).replace(/\n/g, '<br/>');
  }

  // rich-text receives user and AI content. Keep only structural tags and drop
  // every attribute so inline event handlers, styles and unsafe URLs cannot pass through.
  return unescaped
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<\/?(script|style|iframe|object|embed|form|input|button|img)\b[^>]*>/gi, '')
    .replace(/<\/?([a-z][\w-]*)(?:\s[^>]*)?>/gi, (_match, tag: string) => {
      const allowed = ['p', 'br', 'strong', 'em', 'u', 'ul', 'ol', 'li', 'blockquote', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'div', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'code', 'pre', 'a'];
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
