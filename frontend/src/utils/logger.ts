const isDev = import.meta.env.DEV

function ts() {
  return new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

export function log(tag: string, ...args: unknown[]) {
  console.log(`[${ts()}][${tag}]`, ...args)
}

export function logWarn(tag: string, ...args: unknown[]) {
  console.warn(`[${ts()}][${tag}]`, ...args)
}

export function logError(tag: string, ...args: unknown[]) {
  const err = args.find(a => a instanceof Error)
  console.error(`[${ts()}][${tag}]`, ...args)
  if (isDev && err?.stack) {
    console.error(err.stack)
  }
}

let resizeCount = 0
let resizeLoggedAt = 0
export function reportWindowError(message: string, source = '', line = 0, column = 0, error?: unknown) {
  let sourcePath = ''
  try { sourcePath = source ? new URL(source, window.location.origin).pathname : '' } catch { /* unavailable */ }
  if (message === 'ResizeObserver loop completed with undelivered notifications.') {
    resizeCount++
    const now = Date.now()
    if (resizeCount === 1 || now - resizeLoggedAt >= 60_000) {
      logWarn('resize-observer', '尺寸通知延后', { count: resizeCount, sourcePath, line, column })
      resizeLoggedAt = now
    }
    return
  }
  logError('window', '未处理的页面异常', { sourcePath, line, column, errorType: error instanceof Error ? error.name : 'Error' })
}
