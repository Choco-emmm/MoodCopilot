import { marked } from 'marked'
import DOMPurify from 'dompurify'

// 单换行识别为 <br>，聊天应用中 AI 输出多行文本时确保换行生效
marked.use({ breaks: true })

const DEFAULT_ALLOWED_TAGS = [
    'p', 'br', 'strong', 'em', 'b', 'i', 'u',
    'ul', 'ol', 'li', 'blockquote', 'code', 'pre',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'a',
]

const DEFAULT_ALLOWED_ATTR = ['href', 'target', 'rel']

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
        .replace(/([^\s\dA-Za-z])(-)([^\s\d])/g, '$1\n- $3') // AI 行内列表拆行：中文/emoji后- → 换行- 空格
        .replace(/\*\*([""“])/g, '**​$1')   // **" → 零宽空格绕过左边界定界符限制
        .replace(/([""”])\*\*/g, '$1​**')   // "** → 零宽空格绕过右边界定界符限制

    const html = marked.parse(processedText, { async: false }) as string
    return DOMPurify.sanitize(html, {
        USE_PROFILES: { html: true },
        ALLOWED_TAGS: options?.allowedTags ?? DEFAULT_ALLOWED_TAGS,
        ALLOWED_ATTR: options?.allowedAttr ?? DEFAULT_ALLOWED_ATTR,
    })
}
