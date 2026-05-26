export function normalizeResourceUrl(value?: string | null): string | null {
    if (!value) return null

    const raw = String(value).trim()
    if (!raw) return null

    if (raw.startsWith('data:') || raw.startsWith('blob:')) {
        return raw
    }

    if (typeof window === 'undefined') {
        return raw
    }

    const isLocalHost = (hostname: string) => hostname === 'localhost' || hostname === '127.0.0.1'

    try {
        const url = new URL(raw, window.location.origin)

        // 手机公网访问时，后端若返回 localhost 头像地址会导致不可达；这里自动回落到当前域名。
        if (isLocalHost(url.hostname) && !isLocalHost(window.location.hostname)) {
            return `${window.location.origin}${url.pathname}${url.search}${url.hash}`
        }

        if (url.origin === window.location.origin) {
            return `${url.pathname}${url.search}${url.hash}`
        }

        return url.toString()
    } catch {
        const normalizedPath = raw.startsWith('/') ? raw : `/${raw}`
        return normalizedPath.replace(/\/\/{2,}/g, '/')
    }
}

/** 将 http:// URL 升级为 https://，避免 Mixed Content 警告 */
export function ensureHttps(url?: string | null): string | undefined {
    if (!url) return undefined
    if (url.startsWith('http://')) return url.replace('http://', 'https://')
    return url
}
