import { marked } from 'marked'
import DOMPurify from 'dompurify'

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
    const html = marked.parse(text, { async: false }) as string
    return DOMPurify.sanitize(html, {
        USE_PROFILES: { html: true },
        ALLOWED_TAGS: options?.allowedTags ?? DEFAULT_ALLOWED_TAGS,
        ALLOWED_ATTR: options?.allowedAttr ?? DEFAULT_ALLOWED_ATTR,
    })
}
