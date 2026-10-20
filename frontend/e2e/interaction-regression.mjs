import { createServer } from 'vite'
import { chromium } from 'playwright'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'

const root = fileURLToPath(new URL('..', import.meta.url)).replace(/\\/g, '/').replace(/\/$/, '')
const baseline = process.argv.includes('--baseline')

const server = await createServer({
  configFile: false,
  root,
  server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'baseline-source', enforce: 'pre', load(id) {
    const path = id.replace(/\\/g, '/')
    if (!baseline || !path.startsWith(`${root}/src/`) || path.includes('?')) return
    return execFileSync('git', ['show', `HEAD:frontend/${path.slice(root.length + 1)}`], { cwd: root, encoding: 'utf8' })
  } }, vue(), { name: 'fixture-page', configureServer(s) {
    s.middlewares.use('/__regression', (_req, res) => {
      res.setHeader('Content-Type', 'text/html')
      res.end('<div id="app"></div>')
    })
  } }],
})
await server.listen()
const browser = await chromium.launch({ headless: true })
let failed = 0
async function check(name, test) {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true })
  try {
    await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/__regression`)
    await page.evaluate(async () => {
      window.f = await import('/e2e/interaction-fixture.ts')
      window.assert = (condition, message) => { if (!condition) throw new Error(message) }
      window.deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
      window.flush = async () => { for (let i = 0; i < 20; i++) await Promise.resolve(); await window.f.nextTick() }
      window.ok = data => ({ data: { data } })
      window.scroll = { scrollBottom() {}, isNearBottom: () => true }
      localStorage.setItem('token', 'fixture-token')
    })
    await page.evaluate(test)
    console.log(`PASS ${name}`)
  } catch (e) { failed++; console.log(`FAIL ${name}: ${e.message.split('\n')[0]}`) }
  finally { await page.close() }
}
try {
  await check('diary save settles before slow refresh/similar requests', async () => {
    const { diaryApi, createPinia, setActivePinia, useDiaryStore } = f
    setActivePinia(createPinia())
    const slow = deferred()
    diaryApi.create = async () => ok({ id: 1, analysisStatus: 'analyzing', analysis: null })
    diaryApi.mine = diaryApi.public = diaryApi.similar = () => slow.promise
    const store = useDiaryStore()
    let saved = false
    store.createDiary('fixture journal', 'PRIVATE').then(() => { saved = true })
    await flush()
    assert(saved && !store.saving, 'saved diary is still blocked by background requests')
    assert(store.analysisStatus === 'analyzing', 'analysis status lost')
  })
  await check('late welcome topics cannot reset a sent conversation', async () => {
    const { createApp, h, createPinia, createRouter, createMemoryHistory, useChat, chatApi, diaryApi } = f
    const topics = deferred()
    const topicsStarted = deferred()
    let created = false
    chatApi.listConversations = async () => ok(created ? [{ id: 42, title: '新聊天' }] : [])
    chatApi.getWelcomeTopics = () => { topicsStarted.resolve(); return topics.promise }
    chatApi.createConversation = async () => { created = true; return ok({ id: 42, title: '新聊天' }) }
    chatApi.saveHistory = async () => ok(null)
    chatApi.getHistory = async () => ok([])
    chatApi.getActiveRun = () => null
    chatApi.replyStream = async () => {}
    diaryApi.mine = async () => ok([])
    let chat
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
    const app = createApp({ setup() { chat = useChat(); return () => h('div') } })
    app.use(createPinia()).use(router).mount('#app')
    await topicsStarted.promise
    chat.draft.value = 'hello'
    await chat.send()
    assert(chat.activeConvId.value === 42, 'send did not select its conversation')
    topics.resolve(ok([{ text: 'delayed topic' }]))
    await flush()
    assert(chat.activeConvId.value === 42 && chat.messages.value.some(m => m.content === 'hello'), 'late initialization reset the conversation')
    app.unmount()
  })
  await check('stale conversation list cannot evict a newly created conversation', async () => {
    const { chatApi, useChatConversation } = f
    const stale = deferred()
    chatApi.listConversations = () => stale.promise
    chatApi.createConversation = async () => ok({ id: 42, title: '新聊天' })
    const conv = useChatConversation(scroll)
    const loading = conv.loadConversations()
    await conv.ensureConversation()
    stale.resolve(ok([])); await loading
    assert(conv.activeConvId.value === 42, 'stale list cleared current conversation')
  })
  await check('slow history annotation does not block sending or allow duplicate sends', async () => {
    const { ref, useChatStream, chatApi } = f
    const slow = deferred(); let starts = 0
    chatApi.replyStream = async () => { starts++ }
    const messages = ref([]), id = ref(42)
    const stream = useChatStream(messages, id, () => slow.promise, async () => {}, async () => {}, scroll)
    stream.draft.value = 'hello'
    void stream.send(false, async () => {})
    void stream.send(false, async () => {})
    await flush()
    assert(starts === 1, `reply starts while annotation pending: ${starts}`)
    assert(messages.value.filter(m => m.role === 'user').length === 1, 'duplicate user messages')
    await flush()
    stream.draft.value = 'second'
    void stream.send(false, async () => {})
    await flush()
    assert(starts === 2, 'history annotation kept the next send locked after the reply completed')
  })
  await check('history response cannot overwrite a conversation selected in the meantime', async () => {
    const { chatApi, useChatConversation } = f
    const slow = deferred()
    chatApi.getHistory = id => id === 1 ? slow.promise : Promise.resolve(ok([{ role: 'user', content: 'second' }]))
    const conv = useChatConversation(scroll)
    const first = conv.selectConversation(1)
    await conv.selectConversation(2)
    slow.resolve(ok([{ role: 'user', content: 'first' }])); await first
    assert(conv.activeConvId.value === 2 && conv.messages.value[0].content === 'second', 'old history overwrote selected chat')
  })
  await check('slow conversation creation is single-flight and preserves manual model choice', async () => {
    const { ref, useChatConversation, useChatStream, chatApi } = f
    const creating = deferred(); let creates = 0; let starts = 0
    chatApi.createConversation = () => { creates++; return creating.promise }
    chatApi.replyStream = async (_id, _text, _refs, reasoning) => { starts++; assert(reasoning === true, 'manual Pro choice changed') }
    const conv = useChatConversation(scroll)
    const stream = useChatStream(conv.messages, conv.activeConvId, async () => {}, async () => {}, async () => {}, scroll)
    stream.draft.value = 'hello'; stream.useReasoning.value = true
    const send = () => stream.send(false, () => conv.ensureConversation())
    const first = send(), second = send()
    creating.resolve(ok({ id: 42, title: '新聊天' }))
    await Promise.all([first, second])
    assert(creates === 1 && starts === 1, 'duplicate creation or generation')
    assert(conv.messages.value.filter(m => m.role === 'user').length === 1, 'duplicate user message')
  })
  await check('conversation creation failure preserves draft and unlocks retry', async () => {
    const { ref, useChatStream, chatApi } = f
    const id = ref(null); let starts = 0
    chatApi.replyStream = async () => { starts++ }
    const stream = useChatStream(ref([]), id, async () => {}, async () => {}, async () => {}, scroll)
    stream.draft.value = 'keep this message'
    await stream.send(false, async () => null)
    assert(stream.draft.value === 'keep this message' && stream.lastReplyError.value, 'failure lost draft or feedback')
    await stream.send(false, async () => { id.value = 42; return 42 })
    assert(starts === 1, 'failed creation left send locked')
  })
  await check('in-flight polling cannot overwrite a newly streaming message', async () => {
    const { ref, useChatSync } = f
    const slow = deferred(), messages = ref([{ id: 'old', role: 'ai', content: 'old' }])
    const streaming = ref(false)
    const sync = useChatSync(messages, ref(1), streaming, ref(false), () => slow.promise, async () => {}, scroll)
    const polling = sync.syncFromServer(false)
    streaming.value = true
    messages.value = [{ id: 'new', role: 'user', content: 'new' }]
    slow.resolve([{ id: 'server', role: 'ai', content: 'stale' }]); await polling
    assert(messages.value[0].id === 'new', 'polling overwrote live messages')
    sync.cleanup()
  })
  await check('stopped polling cannot apply an in-flight response or restart its timer', async () => {
    const { ref, useChatSync } = f
    const slow = deferred(), messages = ref([{ id: 'current', role: 'user', content: 'current' }])
    const sync = useChatSync(messages, ref(1), ref(false), ref(false), () => slow.promise, async () => {}, scroll)
    sync.startAutoSync()
    const polling = sync.syncFromServer(false)
    sync.stopAutoSync()
    slow.resolve([{ id: 'stale', role: 'ai', content: 'stale' }]); await polling
    assert(messages.value[0].id === 'current', 'hidden chat applied stale polling response')
    sync.cleanup()
  })
  await check('diary editor clears on save acknowledgement before background analysis', async () => {
    const { createApp, h, createPinia, createRouter, createMemoryHistory, diaryApi, collectionApi } = f
    const { default: DiaryComposer } = await import('/src/components/DiaryComposer.vue')
    const slow = deferred()
    diaryApi.create = async () => ok({ id: 1, analysisStatus: 'analyzing', analysis: null })
    diaryApi.mine = diaryApi.public = diaryApi.similar = () => slow.promise
    collectionApi.mine = async () => ok({ records: [] })
    localStorage.setItem('moodcopilot:draft', '<p>saved fixture diary</p>')
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
    await router.push('/')
    const app = createApp({ render: () => h(DiaryComposer) })
    app.use(createPinia()).use(router).mount('#app')
    await flush()
    document.querySelector('.composer-submit').click()
    await flush()
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    assert(!document.querySelector('[contenteditable]').textContent.includes('saved fixture diary'), `editor retained saved text; word count: ${document.querySelector('.composer-word-count').textContent}; draft: ${localStorage.getItem('moodcopilot:draft')}`)
    assert(!localStorage.getItem('moodcopilot:draft'), 'submitted draft persisted')
    assert(document.querySelector('.composer-status').textContent.includes('分析中'), 'background analysis feedback lost')
    app.unmount()
  })
  await check('save failure preserves diary draft', async () => {
    const { createApp, h, createPinia, createRouter, createMemoryHistory, diaryApi, collectionApi } = f
    const { default: DiaryComposer } = await import('/src/components/DiaryComposer.vue')
    diaryApi.create = async () => { throw new Error('fixture failure') }
    collectionApi.mine = async () => ok({ records: [] })
    localStorage.setItem('moodcopilot:draft', '<p>keep this diary</p>')
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
    await router.push('/')
    const app = createApp({ render: () => h(DiaryComposer) })
    app.use(createPinia()).use(router).mount('#app')
    await flush()
    document.querySelector('.composer-submit').click()
    await flush()
    assert(document.querySelector('[contenteditable]').textContent.includes('keep this diary'), 'failed save cleared editor')
    assert(localStorage.getItem('moodcopilot:draft').includes('keep this diary'), 'failed save cleared local draft')
    assert(!document.querySelector('.composer-submit').disabled, 'save failure left button locked')
    app.unmount()
  })
  await check('streamed Markdown renders headings and tables in the mobile message component', async () => {
    const { createApp, h, ref, nextTick } = f
    const { default: ChatStreamingItem } = await import('/src/components/chat/ChatStreamingItem.vue')
    const text = ref('')
    const app = createApp({ render: () => h(ChatStreamingItem, { streaming: true, streamingText: text.value, streamingRefs: [] }) })
    app.mount('#app')
    for (const chunk of ['##', ' ', '分数分布', '\n\n', '| 分数 | 数量 |', '\n', '|---|---|', '\n', '| 4.5 | 1 |', '\n']) {
      text.value += chunk
      await nextTick()
    }
    assert(document.querySelector('.streaming-md h2')?.textContent === '分数分布', 'heading did not render')
    assert(document.querySelector('.streaming-md tbody td')?.textContent === '4.5', 'table did not render')
    app.unmount()
  })
} finally { await browser.close(); await server.close() }
process.exitCode = failed ? 1 : 0
