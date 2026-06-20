import { marked } from 'marked'
import DOMPurify from 'dompurify'

// 单换行识别为 <br>，聊天应用中 AI 输出多行文本时确保换行生效
marked.use({ breaks: true })

const DEFAULT_ALLOWED_TAGS = [
    'p', 'br', 'strong', 'em', 'b', 'i', 'u',
    'ul', 'ol', 'li', 'blockquote', 'code', 'pre',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'a',
    'details', 'summary', 'div', 'span',
    'table', 'thead', 'tbody', 'tr', 'th', 'td'
]

const DEFAULT_ALLOWED_ATTR = ['href', 'target', 'rel', 'class', 'open']

/**
 * 移除常见 Markdown 语法标记，用于安全截断预览文本。
 * 不会处理 HTML 标签 — 输入应为纯 Markdown 文本。
 */
export function stripMarkdown(text: string): string {
    if (!text) return ''
    return text
        .replace(/<\/(p|div|h[1-6]|li|blockquote)>/gi, '\n')
        .replace(/<br\s*\/?>/gi, '\n')
        .replace(/<[^>]+>/g, '')                     // HTML 鏍囩
        .replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1')  // 图片 → alt 文本
        .replace(/\[([^\]]*)\]\([^)]+\)/g, '$1')    // 链接 → 文本
        .replace(/[*_~`]{1,3}/g, '')                 // 粗体/斜体/删除线/代码
        .replace(/^#{1,6}\s+/gm, '')                 // 标题
        .replace(/^>\s?/gm, '')                      // 引用
        .replace(/^[-*+]\s+/gm, '')                  // 无序列表
        .replace(/^\d+\.\s+/gm, '')                  // 有序列表
        .replace(/^---+/gm, '')                      // 分隔线
        .replace(/\n{3,}/g, '\n\n')                  // 预览保留空行断句（最多连续两个换行）
        .trim()
}

export function renderSafeMarkdown(
    text: string,
    options?: {
        allowedTags?: string[]
        allowedAttr?: string[]
    },
) {
    if (!text) return ''

    // ── 第一道：转义复杂 Markdown，防止 DOMPurify 事后撕碎 ──
    const escaped = preprocessComplexMarkdown(text)

    // ── 第二道：保留用户主动按下的多次回车（Vditor 存储为多个空行） ──
    // preprocessComplexMarkdown 已转义用户输入的 HTML，这里注入的 <br> 是安全的
    const withLineBreaks = escaped.replace(/\n{3,}/g, (match) => {
        // 正常段落分隔需要 2 个 \n，超出的每 2 个 \n 对应一个可见空行
        const extra = Math.max(0, Math.floor((match.length - 2) / 2))
        return '\n\n' + '<br>\n\n'.repeat(extra)
    })

    // ── 第三道：修复 AI 常见笔误与 CommonMark 兼容性问题 ──
    const processedText = withLineBreaks
        .replace(/\\\*/g, '*')                   // \*\* → **
        .replace(/^ {0,3}-(?=[^\s])/gm, '$& ')   // -X → - X
        .replace(/([。！？])(不过|但是|其实|所以|然而|总之)/g, '$1\n$2') // 句子+连词 → 换行
        .replace(/([^\s\dA-Za-z])(-)(\*\*)/gu, '$1\n- $3') // ** 粗体列表
        .replace(/([^\s\dA-Za-z])(-)([^\s\d])/gu, '$1\n- $3') // 普通列表
        .replace(/\*\*(["\u201c\u201d\u201e])/g, '**\u200b$1')   // **" → 零宽空格
        .replace(/(["\u201c\u201d])\*\*/g, '$1\u200b**')   // "** → 零宽空格

    const html = marked.parse(processedText, { async: false }) as string
    return DOMPurify.sanitize(html, {
        USE_PROFILES: { html: true },
        ALLOWED_TAGS: options?.allowedTags ?? DEFAULT_ALLOWED_TAGS,
        ALLOWED_ATTR: options?.allowedAttr ?? DEFAULT_ALLOWED_ATTR,
    })
}

/**
 * 在 marked 解析之前，将不被白名单支持的 Markdown 语法转义为纯文本，
 * 避免 DOMPurify 事后删除标签后残留乱码。
 *
 * 处理：图片、原始 HTML 标签。
 * 保留：粗体、斜体、列表、链接、引用、标题等简单 Markdown。
 */
function preprocessComplexMarkdown(text: string): string {
    let result = text

    // ── 1. 表格：已移除转义，由 marked 原生解析并被 DOMPurify 允许 ──

    // ── 2. 图片：![alt](url) → [图片: alt] ──
    result = result.replace(/!\[([^\]]*)\]\([^)]+\)/g, (_m, alt) => {
        return alt ? `[图片: ${alt}]` : '[图片]'
    })

    // ── 3. 原始 HTML 标签：<tag> → &lt;tag&gt; ──
    result = result.replace(/<(\/?)(\w+)([^>]*)>/g, (_m, slash, tag, attrs) => {
        return `&lt;${slash}${tag}${attrs}&gt;`
    })

    return result
}

/**
 * 解码 HTML 实体（如 &amp; → &），用于处理后端返回的可能已被转义的文本。
 */
export function decodeHtmlEntities(text: string): string {
    if (!text) return ''
    const textarea = document.createElement('textarea')
    textarea.innerHTML = text
    return textarea.value
}

/**
 * 格式化老数据：判断文本中是否包含 <p>、<br> 或 <h1-6> 等典型 HTML 标签，
 * 如果包含，说明是新版富文本，直接返回（使用 DOMPurify 净化）；
 * 否则说明是旧版 Markdown，调用 renderSafeMarkdown 进行转换。
 */
export function formatLegacyContent(text: string): string {
    if (!text) return ''
    const isHtml = /<p>|<br>|<br\s*\/>|<h[1-6]>/i.test(text)
    if (isHtml) {
        return DOMPurify.sanitize(text, { USE_PROFILES: { html: true } })
    }
    return renderSafeMarkdown(text)
}
