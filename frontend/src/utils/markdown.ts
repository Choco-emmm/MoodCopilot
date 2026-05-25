import { marked } from 'marked'
import DOMPurify from 'dompurify'

// 单换行识别为 <br>，聊天应用中 AI 输出多行文本时确保换行生效
marked.use({ breaks: true })

const DEFAULT_ALLOWED_TAGS = [
    'p', 'br', 'strong', 'em', 'b', 'i', 'u',
    'ul', 'ol', 'li', 'blockquote', 'code', 'pre',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'a',
    'details', 'summary', 'div', 'span'
]

const DEFAULT_ALLOWED_ATTR = ['href', 'target', 'rel', 'class', 'open']

/**
 * 移除常见 Markdown 语法标记，用于安全截断预览文本。
 * 不会处理 HTML 标签 — 输入应为纯 Markdown 文本。
 */
export function stripMarkdown(text: string): string {
    if (!text) return ''
    return text
        .replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1')  // 图片 → alt 文本
        .replace(/\[([^\]]*)\]\([^)]+\)/g, '$1')    // 链接 → 文本
        .replace(/[*_~`]{1,3}/g, '')                 // 粗体/斜体/删除线/代码
        .replace(/^#{1,6}\s+/gm, '')                 // 标题
        .replace(/^>\s?/gm, '')                      // 引用
        .replace(/^[-*+]\s+/gm, '')                  // 无序列表
        .replace(/^\d+\.\s+/gm, '')                  // 有序列表
        .replace(/^---+/gm, '')                      // 分隔线
        .replace(/\n{2,}/g, '\n')                    // 多余空行压缩
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

    // 预处理：修复 AI 常见笔误与 CommonMark 兼容性问题
    const processedText = text
        .replace(/\\\*/g, '*')                   // \*\* → **
        .replace(/^ {0,3}-(?=[^\s])/gm, '$& ')   // -X → - X
        .replace(/([。！？])(不过|但是|其实|所以|然而|总之)/g, '$1\n$2') // 句子+连词 → 换行
        .replace(/([^\s\dA-Za-z])(-)(\*\*)/gu, '$1\n- $3') // ** 粗体列表：😄-** → 😄\n- **
        .replace(/([^\s\dA-Za-z])(-)([^\s\d])/gu, '$1\n- $3') // 普通列表：水-🍫 → 水\n- 🍫
        .replace(/\*\*([""“])/g, '**​$1')   // **" → 零宽空格绕过左边界定界符限制
        .replace(/([""”])\*\*/g, '$1​**')   // "** → 零宽空格绕过右边界定界符限制

    const html = marked.parse(processedText, { async: false }) as string
    return DOMPurify.sanitize(html, {
        USE_PROFILES: { html: true },
        ALLOWED_TAGS: options?.allowedTags ?? DEFAULT_ALLOWED_TAGS,
        ALLOWED_ATTR: options?.allowedAttr ?? DEFAULT_ALLOWED_ATTR,
    })
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
