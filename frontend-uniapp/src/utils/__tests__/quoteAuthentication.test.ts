import { beforeEach, expect, it, vi } from 'vitest'
let storage: Map<string, any>
beforeEach(() => {
  vi.resetModules()
  vi.stubGlobal('wx', undefined)
  Reflect.deleteProperty(globalThis, 'wx')
  storage = new Map([['loginUserId', 7], ['token', 'fixture']])
  vi.stubGlobal('uni', {
    getStorageSync: (k: string) => storage.get(k),
    setStorageSync: (k: string, v: any) => storage.set(k, v),
    removeStorageSync: (k: string) => storage.delete(k),
    showToast: vi.fn(), $emit: vi.fn(),
    request: (options: any) => options.success({ statusCode: 401, data: {} }),
  })
})
it('does not replay private request data when silent login changes accounts', async () => {
  vi.stubGlobal('wx', {})
  let privateRequests = 0
  Object.assign(uni, {
    login: (options: any) => options.success({ code: 'fixture-code' }),
    request: (options: any) => {
      if (options.url.endsWith('/api/auth/wx-login')) {
        options.success({ statusCode: 200, data: { code: 200, data: { token: 'account-b', userId: 8 } } })
      } else {
        privateRequests++
        options.success({ statusCode: 401, data: {} })
      }
    },
  })
  const quote = await import('../../stores/quote')
  const { post } = await import('../request')
  quote.setQuote('synthetic account-a quote')
  await expect(post('/api/diaries', { content: quote.activeQuote.value })).rejects.toMatchObject({ statusCode: 401 })
  expect(privateRequests).toBe(1)
  expect(storage.has('token')).toBe(false)
  expect(quote.activeQuote.value).toBeNull()
})
it('same-account renewal retries once and preserves the pending quote', async () => {
  vi.stubGlobal('wx', {})
  let privateRequests = 0
  Object.assign(uni, {
    login: (options: any) => options.success({ code: 'fixture-code' }),
    request: (options: any) => {
      if (options.url.endsWith('/api/auth/wx-login')) {
        options.success({ statusCode: 200, data: { code: 200, data: { token: 'renewed-a', userId: 7 } } })
      } else {
        privateRequests++
        options.success(privateRequests === 1 ? { statusCode: 401, data: {} }
          : { statusCode: 200, data: { code: 200, data: 'ok' } })
      }
    },
  })
  const quote = await import('../../stores/quote')
  const { get } = await import('../request')
  quote.setQuote('owned fixture')
  await expect(get('/api/auth/me')).resolves.toMatchObject({ data: 'ok' })
  expect(privateRequests).toBe(2)
  expect(storage.get('token')).toBe('renewed-a')
  expect(quote.activeQuote.value).toBe('owned fixture')
})
it('the real unauthorized response clears pending quotes without requiring logout', async () => {
  const quote = await import('../../stores/quote')
  const { get } = await import('../request')
  quote.setQuote('synthetic private text')
  await expect(get('/api/auth/me')).rejects.toMatchObject({ statusCode: 401 })
  expect(quote.activeQuote.value).toBeNull()
  expect(storage.has('activeQuote')).toBe(false)
  expect(storage.has('activeQuoteOwner')).toBe(false)
})
