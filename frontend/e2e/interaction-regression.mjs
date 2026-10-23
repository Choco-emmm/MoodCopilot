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
  await check('memory center uses the current version for consecutive edits and preserves failed drafts', async () => {
    const { createApp, h, createPinia, setActivePinia, createRouter, createMemoryHistory } = f
    const { NDialogProvider } = await import('/node_modules/.vite/deps/naive-ui.js')
    const { memoryApi } = await import('/src/api/index.ts')
    const { useConsolidationStore } = await import('/src/stores/consolidation.ts')
    const { default: MemoryProfileView } = await import('/src/components/memory/MemoryProfileView.vue')
    const pinia = createPinia(); setActivePinia(pinia)
    let current = { id: 11, attributeKey: '饮食偏好', attributeValue: '喜欢苹果', memoryType: 'preference', isCore: false }, fail = false, refreshFail = false
    const updates = []
    memoryApi.getAll = async () => { if (refreshFail) throw new Error('fixture refresh offline'); return ok([{ ...current }]) }
    memoryApi.getCandidates = async () => ok([])
    memoryApi.update = async (id, data) => {
      updates.push(id)
      if (fail) throw { response: { status: 400, data: { message: '触发受限资料规则，请移除受限资料。' } } }
      assert(id === current.id, 'second save targeted a superseded version')
      current = { ...current, id: id + 1, attributeValue: data.attributeValue }
      return ok(null)
    }
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
    await router.push('/')
    const app = createApp({ render: () => h(NDialogProvider, null, { default: () => h(MemoryProfileView) }) })
    app.use(pinia).use(router).mount('#app'); await flush()
    const button = text => [...document.querySelectorAll('.memory-actions button')].find(el => el.textContent.trim() === text)
    const edit = async value => {
      button('编辑').click(); await flush()
      const input = document.querySelector('.memory-item textarea'); input.value = value
      input.dispatchEvent(new Event('input', { bubbles: true })); await flush()
      button('保存').click(); await flush()
    }
    await edit('喜欢梨')
    assert(useConsolidationStore().memories[0].id === 12, 'saved page kept the old memory ID')
    await edit('喜欢香蕉')
    assert(JSON.stringify(updates) === '[11,12]' && document.body.textContent.includes('喜欢香蕉'), 'consecutive edits did not use latest version')
    fail = true; await edit('未保存的草稿')
    assert(document.querySelector('.memory-item textarea').value === '未保存的草稿', 'failed save discarded draft')
    assert(document.body.textContent.includes('触发受限资料规则'), 'failed save has no visible error')
    fail = false; refreshFail = true; button('保存').click(); await flush()
    assert(document.body.textContent.includes('修改已经保存') && button('编辑').disabled, 'post-save refresh failure permits a stale second edit')
    refreshFail = false
    ;[...document.querySelectorAll('.section-head button')].find(el => el.textContent.trim() === '刷新').click(); await flush()
    assert(useConsolidationStore().memories[0].id === 14 && !button('编辑').disabled, 'manual refresh did not restore latest-version actions')
    app.unmount()
  })
  await check('ResizeObserver diagnostics are counted and limited without hiding other errors or source positions', async () => {
    const { reportWindowError } = await import('/src/utils/logger.ts')
    const warn = console.warn, error = console.error, warnings = [], errors = []
    console.warn = (...args) => warnings.push(args); console.error = (...args) => errors.push(args)
    try {
      for (let i = 0; i < 4; i++) reportWindowError('ResizeObserver loop completed with undelivered notifications.', '/src/example.ts?token=fixture-secret', 12, 3)
      reportWindowError('password=fixture-secret', '/src/example.ts?token=fixture-secret', 14, 2, new Error('fixture-secret'))
      assert(warnings.length === 1 && errors.length === 1, 'exact resize classification swallowed or flooded other errors')
      const meta = errors[0].find(value => value && typeof value === 'object')
      assert(meta?.sourcePath === '/src/example.ts' && meta.line === 14 && meta.column === 2, 'safe source positions missing')
      assert(!JSON.stringify([warnings, errors]).includes('fixture-secret'), 'diagnostic leaked private message or query')
    } finally { console.warn = warn; console.error = error }
  })
  await check('memory center editors keep stable heights and a single candidate can show a stale-decision error', async () => {
    const { createApp, h, createPinia, setActivePinia, createRouter, createMemoryHistory } = f
    const { NDialogProvider } = await import('/node_modules/.vite/deps/naive-ui.js')
    const { memoryApi } = await import('/src/api/index.ts')
    const { default: MemoryProfileView } = await import('/src/components/memory/MemoryProfileView.vue')
    const frame = async () => { await flush(); await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))) }
    let resizeErrors = 0, pending = true
    const onError = event => { if (event.message === 'ResizeObserver loop completed with undelivered notifications.') resizeErrors++ }
    window.addEventListener('error', onError)
    const pinia = createPinia(); setActivePinia(pinia)
    memoryApi.getAll = async () => ok([{ id: 11, attributeKey: '饮食偏好', attributeValue: '喜欢苹果' }])
    memoryApi.getCandidates = async () => ok(pending ? [{ id: 21, attributeKey: '运动偏好', attributeValue: '喜欢跑步', memoryType: 'preference', hasConflict: false }] : [])
    memoryApi.rejectCandidate = async () => { pending = false; throw { response: { status: 409 } } }
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
    await router.push('/')
    const app = createApp({ render: () => h(NDialogProvider, null, { default: () => h(MemoryProfileView) }) })
    app.use(pinia).use(router).mount('#app'); await frame()
    const button = (scope, text) => [...document.querySelectorAll(`${scope} button`)].find(el => el.textContent.trim() === text)
    button('.memory-actions', '编辑').click(); await frame()
    const editor = document.querySelector('.memory-item textarea'), initial = editor.getBoundingClientRect().height
    editor.value = '长文本\n'.repeat(20); editor.dispatchEvent(new Event('input', { bubbles: true })); await frame()
    assert(editor.getBoundingClientRect().height === initial && editor.scrollHeight > editor.clientHeight, 'memory editor height still depends on observed content size')
    button('.memory-actions', '取消').click(); await frame()
    button('.candidate-actions', '拒绝').click(); await frame()
    assert(document.body.textContent.includes('确认拒绝'), 'a single nonconflicting candidate cannot open its dialog')
    button('.n-dialog', '确认拒绝').click(); await frame()
    assert(document.body.textContent.includes('已经处理') && !document.querySelector('.candidate-item'), 'stale candidate decision did not refresh or explain conflict')
    app.unmount(); await frame(); window.removeEventListener('error', onError)
    assert(resizeErrors === 0, 'memory editor emitted a ResizeObserver error')
  })
  await check('memory approval pages preserve editable drafts and reset them when the page is recreated', async () => {
    const { createApp, h, createPinia, setActivePinia, createRouter, createMemoryHistory, chatApi, diaryApi } = f
    const { NMessageProvider } = await import('/node_modules/.vite/deps/naive-ui.js')
    const { useAuthStore } = await import('/src/stores/auth.ts')
    const { default: ChatPage } = await import('/src/pages/ChatPage.vue')
    sessionStorage.setItem('chatSessionInitialized', 'true'); sessionStorage.setItem('currentChatId', '42')
    chatApi.listConversations = async () => ok([{ id: 42, title: '验收会话' }])
    chatApi.getHistory = async () => ok([])
    chatApi.getWelcomeTopics = async () => ok([{ text: '测试话题' }])
    chatApi.getActiveRun = () => 'run-ui-edit'
    chatApi.getRunStatus = async () => ok({ status: 'AWAITING_APPROVAL', lastSequence: 3 })
    diaryApi.mine = async () => ok([])
    const submissions = []
    chatApi.approveRun = async (_conv, _run, decisions) => {
      submissions.push(decisions)
      throw { response: { status: 400, data: { message: '触发受限资料规则' } } }
    }
    let app, waiting, started, deliver
    async function mount() {
      waiting = deferred(); started = deferred()
      chatApi.resumeReplyStream = async (...args) => { deliver = args[7]; started.resolve(); await waiting.promise }
      const pinia = createPinia(); setActivePinia(pinia); useAuthStore().userId = 7
      const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
      await router.push('/')
      app = createApp({ render: () => h(NMessageProvider, null, { default: () => h(ChatPage) }) })
      app.use(pinia).use(router).mount('#app')
      await started.promise
      deliver(['a', 'b'].map(id => ({ toolCallId: id, toolName: 'updateMemory', attributeKey: `测试属性${id}`,
        oldValue: `旧值${id}`, newValue: `原建议${id}`, editableNewValue: true, canApprove: true, evidence: '本轮用户原文' })), 'run-ui-edit', 3)
      await flush()
    }
    const edit = async text => { const el = document.querySelector('[aria-label="修改后的记忆内容"]'); el.value = text; el.dispatchEvent(new Event('input', { bubbles: true })); await flush() }
    await mount()
    const editor = document.querySelector('[aria-label="修改后的记忆内容"]')
    assert(editor.rows === 3 && getComputedStyle(editor).resize === 'none', 'approval editor does not keep fixed rows')
    assert(document.querySelector('.chat-approval-old').textContent.includes('−'), 'old content lacks diff sign')
    await edit(''); assert(document.querySelector('.chat-approval-confirm').disabled, 'empty edit can be approved')
    await edit('第一项用户稿'); document.querySelector('.chat-approval-confirm').click(); await flush()
    assert(document.querySelector('[aria-label="修改后的记忆内容"]').value === '原建议b', 'second draft lost')
    await edit('第二项未批准稿'); document.querySelector('.chat-approval-reject').click(); await flush()
    assert(document.querySelector('.chat-approval-error').textContent.includes('受限资料'), 'server validation error hidden')
    assert(document.querySelector('[aria-label="修改后的记忆内容"]').value === '第二项未批准稿', 'failed request cleared draft')
    assert(submissions[0][0].editedValue === '第一项用户稿' && !('editedValue' in submissions[0][1]), 'partial decisions changed approval scope')
    document.querySelector('.chat-approval-pager-btn').click(); await flush()
    assert(document.querySelector('[aria-label="修改后的记忆内容"]').value === '第一项用户稿', 'page navigation discarded draft')
    await edit('第一项再次修正'); document.querySelector('.chat-approval-confirm').click(); await flush()
    assert(submissions[1][0].editedValue === '第一项再次修正', 'retry reused stale approved content')
    waiting.resolve(); app.unmount(); await flush()
    await mount()
    assert(document.querySelector('[aria-label="修改后的记忆内容"]').value === '原建议a', 'unsubmitted edit survived recreation')
    waiting.resolve(); app.unmount()
  })
  await check('native chat input grows to six rows and never sends during IME composition', async () => {
    const { createApp, h, ref, nextTick } = f
    const { NMessageProvider } = await import('/node_modules/.vite/deps/naive-ui.js')
    const { default: ChatInputBox } = await import('/src/components/chat/ChatInputBox.vue')
    let resizeErrors = 0, sends = 0
    const onError = event => { if (event.message === 'ResizeObserver loop completed with undelivered notifications.') resizeErrors++ }
    window.addEventListener('error', onError)
    const draft = ref(''), disabled = ref(false)
    const app = createApp({ render: () => h(NMessageProvider, null, { default: () => h(ChatInputBox, {
      draft: draft.value, 'onUpdate:draft': value => { draft.value = value }, streaming: false,
      disabled: disabled.value, useReasoning: false, lastReplyError: null, canRetry: false,
      references: [], recentDiaries: [], recentEvents: [], recentDiariesLoading: false,
      recentEventsLoading: false, recentDiariesError: null, recentEventsError: null,
      'onSend-enter': () => sends++,
    }) }) })
    app.mount('#app')
    const frame = async () => { await nextTick(); await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))) }
    await frame()
    const input = document.querySelector('textarea[aria-label="聊天消息"]')
    const initial = input.getBoundingClientRect().height
    draft.value = '第一行\n'; await frame()
    assert(input.getBoundingClientRect().height > initial, 'trailing newline does not grow input')
    draft.value = Array(15).fill('长文本').join('\n'); await frame()
    assert(input.getBoundingClientRect().height <= 161 && input.scrollHeight > input.clientHeight, 'six row scrolling limit broken')
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', isComposing: true, bubbles: true, cancelable: true }))
    assert(sends === 0, 'IME Enter sent the message')
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }))
    assert(sends === 1, 'normal Enter does not send')
    disabled.value = true; await frame(); assert(input.disabled, 'disabled state lost')
    disabled.value = false; draft.value = ''; await frame()
    assert(input.getBoundingClientRect().height === initial && input.maxLength === 1000, 'clear/length behavior changed')
    app.unmount(); await frame()
    assert(resizeErrors === 0, 'input emitted ResizeObserver loop error')
    window.removeEventListener('error', onError)
  })
  await check('edited memory approval keeps pending items on validation failure and sends only approved edits', async () => {
    const { ref, useChatStream, chatApi } = f
    const waiting = deferred(), started = deferred()
    let approve, calls = [], fail = true
    chatApi.getActiveRun = () => 'run-edit'
    chatApi.getRunStatus = async () => ok({ status: 'AWAITING_APPROVAL', lastSequence: 3 })
    chatApi.resumeReplyStream = async (...args) => { approve = args[7]; started.resolve(); await waiting.promise }
    chatApi.approveRun = async (_conv, _run, decisions) => {
      calls.push(decisions)
      if (fail) throw { response: { status: 400, data: { message: '触发受限资料规则' } } }
      return ok({})
    }
    const stream = useChatStream(ref([]), ref(42), async () => {}, async () => {}, async () => {}, scroll)
    const recovery = stream.resumeActiveRun(42); await started.promise
    approve([{ toolCallId: 'edit-a', toolName: 'updateMemory', editableNewValue: true, newValue: '原建议', canApprove: true }], 'run-edit', 3)
    await flush()
    await stream.resolveApproval([{ toolCallId: 'edit-a', approved: true, editedValue: '用户编辑稿' }])
    assert(stream.awaitingApproval.value && stream.approvalError.value === '触发受限资料规则', 'validation failure consumed approval')
    assert(calls[0][0].editedValue === '用户编辑稿' && !('memoryId' in calls[0][0]), 'edit transport expanded write scope')
    fail = false
    await stream.resolveApproval([{ toolCallId: 'edit-a', approved: true, editedValue: '修正后的用户稿' }])
    assert(!stream.awaitingApproval.value && calls[1][0].editedValue === '修正后的用户稿', 'retry did not save latest edit')
    waiting.resolve(); await recovery
  })
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
  await check('retry preserves images, references and the original model choice', async () => {
    const { ref, useChatStream, chatApi } = f
    const calls = []
    chatApi.replyStream = async (...args) => {
      calls.push({ content: args[1], references: args[2], useReasoning: args[3], eventId: args[4], imageUrls: args[5], referenceItems: args[12] })
      if (calls.length === 1) throw new Error('synthetic failure')
    }
    const stream = useChatStream(ref([]), ref(42), async () => {}, async () => {}, async () => {}, scroll)
    stream.draft.value = 'explain this image with my diary'
    stream.useReasoning.value = true
    stream.addImageRef('https://synthetic.invalid/image.png')
    stream.references.value.push({ label: '日记', content: 'synthetic diary', fullContent: 'synthetic diary', diaryId: 7, sourceType: 'diary' })
    await stream.send(false, async () => {}, 9)
    stream.useReasoning.value = false
    await stream.retryLastReply()
    assert(calls.length === 2, 'retry did not start')
    assert(calls[1].imageUrls.length === 1, 'retry discarded original image')
    assert(JSON.stringify(calls[0]) === JSON.stringify(calls[1]), 'retry changed the original request')
  })
  await check('recovery accepts a new approval after initially RUNNING', async () => {
    const { ref, useChatStream, chatApi } = f
    const waiting = deferred(), started = deferred()
    let approve, status = { status: 'RUNNING', lastSequence: 2 }
    chatApi.getActiveRun = () => 'run-new-approval'
    chatApi.getRunStatus = async () => ok(status)
    chatApi.resumeReplyStream = async (...args) => { approve = args[7]; started.resolve(); await waiting.promise }
    const stream = useChatStream(ref([]), ref(42), async () => {}, async () => {}, async () => {}, scroll)
    const recovery = stream.resumeActiveRun(42)
    await started.promise
    // The frame can arrive before the server's status transition is observable.
    status = { status: 'RUNNING', lastSequence: 3 }
    approve([{ toolCallId: 'new-write', toolName: 'saveMemory' }], 'run-new-approval', 3)
    await flush()
    status = { status: 'AWAITING_APPROVAL', lastSequence: 3 }
    for (let i = 0; i < 30 && !stream.awaitingApproval.value; i++) await new Promise(resolve => setTimeout(resolve, 25))
    assert(stream.pendingApprovals.value[0]?.toolCallId === 'new-write', 'new approval was lost after recovery')
    waiting.resolve(); await recovery
  })
  await check('recovery shows only the current approval batch and retries status failures', async () => {
    const { ref, useChatStream, chatApi } = f
    const waiting = deferred(), started = deferred()
    let approve, failures = 1
    chatApi.getActiveRun = () => 'run-current-batch'
    chatApi.getRunStatus = async () => {
      if (failures-- > 0) throw new Error('temporary status failure')
      return ok({ status: 'AWAITING_APPROVAL', lastSequence: 8 })
    }
    chatApi.resumeReplyStream = async (...args) => { approve = args[7]; started.resolve(); await waiting.promise }
    const stream = useChatStream(ref([]), ref(42), async () => {}, async () => {}, async () => {}, scroll)
    const recovery = stream.resumeActiveRun(42)
    await started.promise
    approve([{ toolCallId: 'old-write', toolName: 'saveMemory' }], 'run-current-batch', 3)
    await flush()
    assert(!stream.awaitingApproval.value, 'resolved historical approval appeared')
    failures = 1
    approve([{ toolCallId: 'current-write', toolName: 'saveMemory' }], 'run-current-batch', 8)
    for (let i = 0; i < 30 && !stream.awaitingApproval.value; i++) await new Promise(resolve => setTimeout(resolve, 25))
    assert(stream.pendingApprovals.value[0]?.toolCallId === 'current-write', 'current batch lost after status failure')
    waiting.resolve(); await recovery
  })
  await check('switching conversations invalidates a pending approval status response', async () => {
    const { ref, useChatStream, chatApi } = f
    const waiting = deferred(), started = deferred(), statusResponse = deferred()
    let approve, queries = 0
    chatApi.getActiveRun = () => 'run-stale-response'
    chatApi.getRunStatus = async () => ++queries === 1 ? ok({ status: 'RUNNING', lastSequence: 1 }) : statusResponse.promise
    chatApi.resumeReplyStream = async (...args) => { approve = args[7]; started.resolve(); await waiting.promise }
    const id = ref(42)
    const stream = useChatStream(ref([]), id, async () => {}, async () => {}, async () => {}, scroll)
    const recovery = stream.resumeActiveRun(42)
    await started.promise
    approve([{ toolCallId: 'stale-write', toolName: 'saveMemory' }], 'run-stale-response', 2)
    await flush()
    id.value = 43
    await flush()
    statusResponse.resolve(ok({ status: 'AWAITING_APPROVAL', lastSequence: 2 }))
    await flush()
    assert(!stream.awaitingApproval.value, 'previous conversation opened an approval dialog')
    waiting.resolve(); await recovery
  })
  await check('a late approval response cannot discard the next approval batch', async () => {
    const { ref, useChatStream, chatApi } = f
    const waiting = deferred(), started = deferred(), submission = deferred(), nextStatus = deferred()
    let approve, holdStatus = false
    chatApi.getActiveRun = () => 'run-next-batch'
    chatApi.getRunStatus = async () => holdStatus ? nextStatus.promise : ok({ status: 'AWAITING_APPROVAL', lastSequence: 2 })
    chatApi.approveRun = async () => submission.promise
    chatApi.resumeReplyStream = async (...args) => { approve = args[7]; started.resolve(); await waiting.promise }
    const stream = useChatStream(ref([]), ref(42), async () => {}, async () => {}, async () => {}, scroll)
    const recovery = stream.resumeActiveRun(42)
    await started.promise
    approve([{ toolCallId: 'first-write', toolName: 'saveMemory' }], 'run-next-batch', 2)
    await flush()
    assert(stream.pendingApprovals.value[0]?.toolCallId === 'first-write', 'first approval missing')
    const resolving = stream.resolveApproval([{ toolCallId: 'first-write', approved: true }])
    holdStatus = true
    approve([{ toolCallId: 'second-write', toolName: 'saveMemory' }], 'run-next-batch', 4)
    await flush()
    submission.resolve(ok({})); await resolving
    nextStatus.resolve(ok({ status: 'AWAITING_APPROVAL', lastSequence: 4 }))
    await flush()
    assert(stream.pendingApprovals.value[0]?.toolCallId === 'second-write', 'late first response discarded the next batch')
    waiting.resolve(); await recovery
  })
  await check('the real SSE transport forwards the approval sequence', async () => {
    const { chatApi } = f
    const originalFetch = window.fetch
    sessionStorage.setItem('chat:active-run:42', 'run-sequence')
    let received
    window.fetch = async () => new Response(
      'data: {"type":"approval_required","seq":3,"items":[{"toolCallId":"write-a","toolName":"saveMemory"}]}\n\n'
      + 'data: {"type":"done","seq":4}\n\n',
      { headers: { 'Content-Type': 'text/event-stream' } },
    )
    try {
      await chatApi.resumeReplyStream(42, 'run-sequence', () => {}, new AbortController(), undefined, undefined, undefined,
        (items, runId, sequence) => { received = { items, runId, sequence } })
      assert(received?.sequence === 3 && received?.runId === 'run-sequence', 'transport discarded approval identity')
    } finally { window.fetch = originalFetch }
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
