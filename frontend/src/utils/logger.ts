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
