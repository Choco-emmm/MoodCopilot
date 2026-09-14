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

const HEADING_RE = /^\s*#{1,6}\s+/;
const UNORDERED_ITEM_RE = /^\s*[-*+]\s+(.*)$/;
const ORDERED_ITEM_RE = /^\s*\d{1,9}[.)]\s+(.*)$/;

// 与 marked 保持一致：分隔行允许 1 个及以上的 `-`，两端 `|` 可有可无（如 |--|--|、| :--- | ---: |）
const TABLE_DIVIDER_RE = /^ {0,3}(?:\| *)?:?-+:? *(?:\| *:?-+:? *)*\|? *$/;

export const parseMarkdown = (text: string) => {
  if (!text) return '';

  const normalized = mergeLineLeadingStrong(
    text
      .replace(/<think>[\s\S]*?<\/think>/gi, '')
      // 只处理 \r\n 不够：模型偶尔只输出 \r（或 U+2028），此时 split('\n') 会把整段当成一行，
      // 表格/列表/标题全部失效并原样吐出 | 和 #。这里统一归一化所有换行符。
      .replace(/\r\n?|[\u2028\u2029]/g, '\n')
      // -X → - X（排除 `---` 分隔线与 `-- | ---` 这类分隔行）
      .replace(/^ {0,3}-(?=[^\s-])/gm, '$& '),
  );

  if (!normalized.trim()) return '';

  const lines = normalized.split('\n');
  const blocks: string[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    if (isTableStart(lines, i)) {
      const headerCells = splitTableRow(line);
      const rows: string[] = [];
      let j = i + 2;

      while (j < lines.length) {
        const row = lines[j].trim();
        if (!row.includes('|')) break;
        const cells = splitTableRow(row);
        rows.push(cells.map(cell => `<td>${formatInlineMarkdown(cell)}</td>`).join(''));
        j += 1;
      }

      const head = headerCells
        .map(cell => `<th>${formatInlineMarkdown(cell)}</th>`)
        .join('');
      const body = rows.length ? `<tbody>${rows.map(row => `<tr>${row}</tr>`).join('')}</tbody>` : '';
      blocks.push(`<table><thead><tr>${head}</tr></thead>${body}</table>`);
      i = j;
      continue;
    }

    if (HEADING_RE.test(line)) {
      const level = Math.min(6, Math.max(1, (line.match(/^\s*#+/) || [''])[0].trim().length));
      const headingText = line.replace(/^\s*#{1,6}\s+/, '').trim();
      blocks.push(`<h${level}>${formatInlineMarkdown(headingText)}</h${level}>`);
      i += 1;
      continue;
    }

    const listType = listTypeOf(line);
    if (listType) {
      const itemRe = listType === 'ol' ? ORDERED_ITEM_RE : UNORDERED_ITEM_RE;
      const items: string[] = [];

      while (i < lines.length) {
        const itemMatch = lines[i].match(itemRe);
        if (itemMatch) {
          items.push(itemMatch[1].trim());
          i += 1;
          continue;
        }
        // 列表项续行：非空且不是新的块级语法
        if (lines[i].trim() && !listTypeOf(lines[i]) && !HEADING_RE.test(lines[i]) && !isTableStart(lines, i)) {
          items[items.length - 1] += '\n' + lines[i].trim();
          i += 1;
          continue;
        }
        break;
      }

      const rendered = items
        .map(item => item.split('\n').map(seg => formatInlineMarkdown(seg)).join('<br/>'))
        .map(item => `<li>${item}</li>`)
        .join('');
      blocks.push(`<${listType}>${rendered}</${listType}>`);
      continue;
    }

    if (!line.trim()) {
      i += 1;
      continue;
    }

    const paragraphLines: string[] = [];
    while (
      i < lines.length
      && lines[i].trim()
      && !HEADING_RE.test(lines[i])
      && !listTypeOf(lines[i])
      && !isTableStart(lines, i)
    ) {
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

function listTypeOf(line: string): 'ol' | 'ul' | null {
  if (UNORDERED_ITEM_RE.test(line)) return 'ul';
  if (ORDERED_ITEM_RE.test(line)) return 'ol';
  return null;
}

function splitTableRow(row: string): string[] {
  return row.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(cell => cell.trim());
}

function isTableStart(lines: string[], index: number): boolean {
  const header = (lines[index] || '').trim();
  const next = (lines[index + 1] || '').trim();
  return header.includes('|') && next.includes('|') && TABLE_DIVIDER_RE.test(next);
}

/**
 * AI 常把闭合的 `**` 写到下一行行首（`**前一句。\n**后一句`），
 * 而 CommonMark 要求闭合定界符前不能是空白，跨行 `**` 会被原样输出成字面量。
 * 当上一行存在未闭合的 `**` 时，把行首的 `**` 移回上一行末尾补上闭合。
 */
function mergeLineLeadingStrong(text: string): string {
  const lines = text.split('\n');
  for (let i = 1; i < lines.length; i++) {
    if (!/^\s*\*\*(?=\S)/.test(lines[i])) continue;
    const unclosed = (lines[i - 1].match(/\*\*/g) || []).length % 2 === 1;
    if (!unclosed) continue;
    lines[i] = lines[i].replace(/^(\s*)\*\*/, '$1');
    lines[i - 1] = `${lines[i - 1].replace(/\s+$/, '')}**`;
  }
  return lines.join('\n');
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
