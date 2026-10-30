import { beforeEach, expect, it, vi } from 'vitest'
vi.mock('../markdown', () => ({ extractPlainText: (s: string) => s }))
vi.mock('../request', () => ({ post: vi.fn().mockResolvedValue({}), get: vi.fn() }))
vi.mock('../socket', () => ({ disconnectWebSocket: vi.fn() }))
vi.mock('../../stores/announcement', () => ({ setAnnouncementUserId: vi.fn() }))
let storage: Map<string, any>
beforeEach(() => {
  vi.resetModules()
  storage = new Map([['loginUserId', 7], ['token', 'fixture']])
  vi.stubGlobal('uni', {
    getStorageSync: (k: string) => storage.get(k),
    setStorageSync: (k: string, v: any) => storage.set(k, v),
    removeStorageSync: (k: string) => storage.delete(k),
  })
})
it('logout clears a pending private quote in memory and storage', async () => {
  const quote = await import('../../stores/quote')
  quote.setQuote('private fixture')
  const user = await import('../../stores/user')
  await user.logout()
  expect(quote.activeQuote.value).toBeNull()
  expect(storage.has('activeQuote')).toBe(false)
})
it('legacy unowned quotes cannot be restored into a new session', async () => {
  storage.set('activeQuote', 'previous account fixture')
  const quote = await import('../../stores/quote')
  expect(quote.activeQuote.value).toBeNull()
})
it('a quote is rejected at read/send after identity changes even without logout', async () => {
  const quote = await import('../../stores/quote')
  quote.setQuote('account A fixture')
  storage.set('loginUserId', 8)
  expect(quote.activeQuote.value).toBeNull()
  quote.setQuote('account B fixture')
  expect(quote.activeQuote.value).toBe('account B fixture')
  storage.delete('token')
  expect(quote.activeQuote.value).toBeNull()
})
it('owned drafts survive reload only for the same signed-in user', async () => {
  storage.set('activeQuote', 'owned fixture')
  storage.set('activeQuoteOwner', '7')
  const quote = await import('../../stores/quote')
  expect(quote.activeQuote.value).toBe('owned fixture')
})
