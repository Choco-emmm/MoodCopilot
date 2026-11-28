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
      window.paint = async () => { await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))); await window.flush() }
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
  await check('memory groups bound the first view and separate original evidence from activity', async () => {
    const { createApp, h, createPinia, setActivePinia, createRouter, createMemoryHistory } = f
    const { NDialogProvider } = await import('/node_modules/.vite/deps/naive-ui.js')
    const { memoryApi } = await import('/src/api/index.ts')
    const { default: MemoryProfileView } = await import('/src/components/memory/MemoryProfileView.vue')
    const pinia = createPinia(); setActivePinia(pinia)
    memoryApi.getAll = async () => ok(Array.from({ length: 20 }, (_, i) => ({ id: i + 1, attributeKey: `属性${i}`, attributeValue: `记忆${i}` })))
    memoryApi.getCandidates = async () => ok(Array.from({ length: 20 }, (_, i) => ({ id: i + 101, attributeKey: `候选属性${i}`, attributeValue: `候选内容${i}` })))
    memoryApi.getEvidence = async () => ok([
      { id: 1, sourceType: 'chat_candidate', sourceConversationId: 5, evidenceText: '我喜欢晨跑' },
      { id: 2, sourceType: 'USER_ACTION', sourceConversationId: 5, evidenceText: 'USER_CONFIRMED_CHAT_EDIT' },
    ])
    memoryApi.getHistory = async () => ok([])
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] }); await router.push('/')
    const app = createApp({ render: () => h(NDialogProvider, null, { default: () => h(MemoryProfileView) }) }); app.use(pinia).use(router).mount('#app'); await flush()
    assert(document.querySelector('.memory-overview').textContent.includes('20 条正式记忆'), 'formal memory count is missing')
    assert(!document.querySelector('.candidate-item'), 'candidate queue is not initially collapsed')
    assert(document.querySelectorAll('.memory-item').length === 1, 'formal groups do not bound initial content')
    document.querySelector('.memory-actions button').click(); await flush()
    const evidence = document.querySelector('.memory-evidence-section').textContent
    assert(evidence.includes('我喜欢晨跑') && !evidence.includes('USER_CONFIRMED_CHAT_EDIT'), 'activity is mixed with original evidence')
    assert(document.querySelector('.memory-activity').textContent.includes('确认修改记忆'), 'activity does not have a readable label')
    document.querySelector('#memory-details-return').click(); await flush()
    document.querySelector('.candidate-toggle').click(); await flush()
    assert(document.querySelectorAll('.candidate-item').length === 1, 'candidate group defaults are unbounded')
    assert(document.documentElement.scrollWidth <= innerWidth, 'mobile memory groups overflow')
    app.unmount()
  })
  await check('candidate refresh failure preserves content and evidence retry ignores stale responses', async () => {
    const { createApp, h, createPinia, setActivePinia, createRouter, createMemoryHistory } = f
    const { NDialogProvider } = await import('/node_modules/.vite/deps/naive-ui.js')
    const { memoryApi } = await import('/src/api/index.ts')
    const { default: MemoryProfileView } = await import('/src/components/memory/MemoryProfileView.vue')
    const pinia = createPinia(); setActivePinia(pinia)
    let fail = false, evidenceFail = true
    const slow = deferred()
    memoryApi.getAll = async () => ok([{ id: 1, attributeKey: '运动', attributeValue: '喜欢跑步' }])
    memoryApi.getCandidates = async () => { if (fail) throw new Error('offline'); return ok([101, 102].map(id => ({ id, attributeKey: '运动', attributeValue: `候选${id}` }))) }
    memoryApi.getCandidateEvidence = async id => { if (evidenceFail) throw new Error('offline'); return id === 101 ? slow.promise : ok([{ id: 9, sourceType: 'chat_candidate', evidenceText: '新的依据' }]) }
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] }); await router.push('/')
    const app = createApp({ render: () => h(NDialogProvider, null, { default: () => h(MemoryProfileView) }) }); app.use(pinia).use(router).mount('#app'); await flush()
    document.querySelector('.candidate-toggle').click(); await flush()
    fail = true; [...document.querySelectorAll('.section-head button')].find(b => b.textContent.trim() === '刷新').click(); await flush()
    assert(document.querySelectorAll('.candidate-item').length === 2 && document.querySelector('.candidate-load-error'), 'failed candidate refresh cleared content or has no error')
    document.querySelector('.candidate-actions button').click(); await flush()
    assert(document.querySelector('.candidate-details [role="alert"]'), 'evidence failure is shown as empty')
    evidenceFail = false; document.querySelector('.candidate-details [role="alert"] button').click(); await flush()
    document.querySelector('#memory-details-return').click(); await flush()
    document.querySelectorAll('.candidate-actions')[1].querySelector('button').click(); await flush()
    slow.resolve(ok([{ id: 8, sourceType: 'chat_candidate', evidenceText: '过时的依据' }])); await flush()
    assert(document.querySelector('.candidate-details').textContent.includes('新的依据') && !document.querySelector('.candidate-details').textContent.includes('过时的依据'), 'stale candidate response replaced current evidence')
    app.unmount()
  })
  await check('a single memory candidate opens confirmation and submits the edited value through the real API', async () => {
    const { createApp, h, createPinia, setActivePinia, createRouter, createMemoryHistory } = f
    const { NDialogProvider } = await import('/node_modules/.vite/deps/naive-ui.js')
    const { api } = await import('/src/api/core.ts')
    const { default: MemoryProfileView } = await import('/src/components/memory/MemoryProfileView.vue')
    const pinia = createPinia(); setActivePinia(pinia)
    let pending = true
    const approvals = []
    api.defaults.adapter = async config => {
      let data = []
      if (config.url === '/memory/candidates' && pending) data = [{ id: 21, attributeKey: '运动偏好', attributeValue: '喜欢跑步', memoryType: 'preference', hasConflict: false }]
      if (config.url === '/memory/candidates/21/approve') {
        approvals.push({ method: config.method, data: JSON.parse(config.data) })
        pending = false
      }
      return { data: { code: 200, data }, status: 200, statusText: 'OK', headers: {}, config }
    }
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
    await router.push('/')
    const app = createApp({ render: () => h(NDialogProvider, null, { default: () => h(MemoryProfileView) }) })
    app.use(pinia).use(router).mount('#app'); await flush()
    const button = (scope, label) => [...document.querySelectorAll(`${scope} button`)].find(el => el.textContent.trim() === label)
    document.querySelector('.candidate-toggle')?.click(); await flush()
    document.querySelector('.candidate-actions button').click(); await flush()
    button('.candidate-decisions', '确认').click(); await flush()
    assert(document.querySelector('[aria-label="确认后的候选记忆"]'), 'list confirmation did not open the edit dialog')
    assert(approvals.length === 0, 'candidate was saved before dialog confirmation')
    const editor = document.querySelector('[aria-label="确认后的候选记忆"]')
    editor.value = '喜欢晨跑'; editor.dispatchEvent(new Event('input', { bubbles: true })); await flush()
    button('.n-dialog', '确认并保存').click(); await flush()
    assert(approvals.length === 1 && approvals[0].method === 'post' && approvals[0].data.editedValue === '喜欢晨跑', 'confirmation did not submit the edited candidate once')
    assert(!document.querySelector('.candidate-item'), 'confirmed candidate remained in pending list')
    app.unmount()
  })
  await check('accepting a new life stage keeps source counts aligned with the replaced current chapter', async () => {
    const { createApp, h, createPinia, setActivePinia, createRouter, createMemoryHistory } = f
    const { NDialogProvider } = await import('/node_modules/.vite/deps/naive-ui.js')
    const { lifeChapterApi } = await import('/src/api/life.ts')
    const { useNotificationStore } = await import('/src/stores/notification.ts')
    const { default: LifeChaptersPage } = await import('/src/pages/LifeChaptersPage.vue')
    const pinia = createPinia(); setActivePinia(pinia)
    const notifications = useNotificationStore()
    notifications.connectRealtime = () => {}
    notifications.disconnectRealtime = () => {}
    notifications.fetchUnreadCount = async () => {}
    const diaries = Array.from({ length: 89 }, (_, i) => ({ id: i + 1, date: '2026-10-01', excerpt: `记录 ${i + 1}` }))
    const events = [{ id: 101, title: '阶段转折', startDate: '2026-10-20' }]
    const chapter = { id: 1, title: '原阶段', startDate: '2026-01-01', isOpen: true, diarySources: diaries, eventSources: events }
    let accepted = false
    lifeChapterApi.timeline = async () => ok({ stages: accepted ? [
      { ...chapter, id: 2, title: '新阶段', startDate: '2026-10-20', diarySources: diaries.slice(-2) },
      { ...chapter, isOpen: false, endDate: '2026-10-19', diarySources: diaries.slice(0, -2), eventSources: [] },
    ] : [chapter], gaps: [], nextCursor: null })
    lifeChapterApi.candidates = async () => ok(accepted ? [] : [{ id: 7, suggestedStartDate: '2026-10-20', reason: '阶段变化', sourceDiaryIds: [88, 89], sourceEventIds: [101] }])
    lifeChapterApi.acceptCandidate = async id => { assert(id === 7, 'wrong candidate accepted'); accepted = true; return ok(null) }
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
    await router.push('/')
    const app = createApp({ render: () => h(NDialogProvider, null, { default: () => h(LifeChaptersPage) }) })
    app.use(pinia).use(router).mount('#app'); await flush()
    const current = () => document.querySelector('.chapter-group .chapter')
    const sourceButton = () => current().querySelector('.chapter-actions button')
    assert(sourceButton().textContent === '查看来源（90）', 'initial source count is wrong')
    document.querySelector('.candidate-actions .primary-button').click(); await flush()
    assert(current().querySelector('.chapter-title').textContent === '新阶段', 'new current stage did not load')
    sourceButton().click(); await flush()
    assert(current().querySelectorAll('.source-item').length === 3, 'new stage should contain two diaries and one event')
    sourceButton().click(); await flush()
    assert(sourceButton().textContent === '查看来源（3）', `expected 3 sources after accepting, got ${sourceButton().textContent}`)
    assert(current().querySelector('.chapter-meta').textContent.includes('3 条记录'), 'chapter metadata retained the old count')
    const previous = document.querySelectorAll('.chapter-group .chapter')[1]
    assert(previous.querySelector('.chapter-actions button').textContent === '查看来源（87）', 'previous stage count is wrong')
    app.unmount()
  })
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
    memoryApi.getEvidence = async () => ok([])
    memoryApi.getHistory = async () => ok([])
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
    const button = text => [...document.querySelectorAll('.memory-actions button')].find(el => el.textContent.replace('→', '').trim() === text)
    const edit = async value => {
      if (!button('编辑记忆')) { button('查看全文与依据').click(); await flush() }
      button('编辑记忆').click(); await flush()
      const input = document.querySelector('.memory-details textarea'); input.value = value
      input.dispatchEvent(new Event('input', { bubbles: true })); await flush()
      button('保存').click(); await flush()
    }
    await edit('喜欢梨')
    assert(useConsolidationStore().memories[0].id === 12, 'saved page kept the old memory ID')
    await edit('喜欢香蕉')
    assert(JSON.stringify(updates) === '[11,12]' && document.body.textContent.includes('喜欢香蕉'), 'consecutive edits did not use latest version')
    fail = true; await edit('未保存的草稿')
    assert(document.querySelector('.memory-details textarea').value === '未保存的草稿', 'failed save discarded draft')
    assert(document.body.textContent.includes('触发受限资料规则'), 'failed save has no visible error')
    fail = false; refreshFail = true; button('保存').click(); await flush()
    assert(document.body.textContent.includes('修改已经保存') && button('编辑记忆').disabled, 'post-save refresh failure permits a stale second edit')
    refreshFail = false
    document.querySelector('#memory-details-return').click(); await flush()
    ;[...document.querySelectorAll('.section-head button')].find(el => el.textContent.trim() === '刷新').click(); await flush()
    assert(useConsolidationStore().memories[0].id === 14 && !button('查看全文与依据').disabled, 'manual refresh did not restore latest-version actions')
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
    const button = (scope, text) => [...document.querySelectorAll(`${scope} button`)].find(el => el.textContent.replace('→', '').trim() === text)
    button('.memory-actions', '查看全文与依据')?.click(); await frame()
    button('.memory-actions', '编辑记忆').click(); await frame()
    const editor = document.querySelector('.memory-details textarea'), initial = editor.getBoundingClientRect().height
    editor.value = '长文本\n'.repeat(20); editor.dispatchEvent(new Event('input', { bubbles: true })); await frame()
    assert(editor.getBoundingClientRect().height === initial && editor.scrollHeight > editor.clientHeight, 'memory editor height still depends on observed content size')
    button('.memory-actions', '取消').click(); await frame()
    document.querySelector('#memory-details-return').click(); await frame()
    document.querySelector('.candidate-toggle')?.click(); await frame()
    document.querySelector('.candidate-actions button').click(); await frame()
    button('.candidate-decisions', '拒绝').click(); await frame()
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
  await check('community sharing preserves the explicit analysis opt-out through the real editor and API', async () => {
    const { createApp, h, createPinia, createRouter, createMemoryHistory, diaryApi, collectionApi, useDiaryStore } = f
    const { api } = await import('/src/api/core.ts')
    const { default: DiaryComposer } = await import('/src/components/DiaryComposer.vue')
    const submissions = []
    api.defaults.adapter = async config => {
      assert(config.url === '/diaries' && config.method === 'post', 'unexpected API call')
      const payload = JSON.parse(config.data)
      submissions.push(payload)
      return { data: { data: { id: submissions.length, ...payload, analysis: null, analysisStatus: payload.analyze ? 'analyzing' : 'skipped_user' } }, status: 200, statusText: 'OK', headers: {}, config }
    }
    diaryApi.mine = diaryApi.public = async () => ok({ items: [] })
    collectionApi.mine = async () => ok({ records: [] })
    localStorage.setItem('moodcopilot:draft', '<p>explicitly disabled analysis</p>')
    const pinia = createPinia()
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
    await router.push('/')
    const app = createApp({ render: () => h(DiaryComposer) })
    app.use(pinia).use(router).mount('#app'); await flush()
    document.querySelectorAll('.composer-vis-opt')[1].click(); await flush()
    document.querySelector('.composer-ai-toggle input').click(); await flush()
    assert(!document.querySelector('.composer-ai-toggle input').checked, 'analysis toggle is still checked')
    document.querySelector('.composer-submit').click(); await flush()
    assert(submissions.length === 1 && submissions[0].visibility === 'PUBLIC' && submissions[0].analyze === false, 'sharing replaced the explicit false in the submitted JSON')
    assert(useDiaryStore(pinia).analysisStatus === 'skipped_user', 'disabled analysis is presented as running')
    assert(document.querySelector('.composer-status').textContent.includes('AI 分析已关闭'), 'opt-out confirmation is missing')
    app.unmount()
  })
  await check('sharing an existing diary sends the actual analysis switch even when its content is unchanged', async () => {
    const { createApp, h, createPinia, createRouter, createMemoryHistory, diaryApi, collectionApi } = f
    const { api } = await import('/src/api/core.ts')
    const { default: DiaryComposer } = await import('/src/components/DiaryComposer.vue')
    const submissions = []
    api.defaults.adapter = async config => {
      assert(config.url === '/diaries/42' && config.method === 'put', 'unexpected edit API call')
      const payload = JSON.parse(config.data); submissions.push(payload)
      return { data: { data: { id: 42, ...payload, analysis: null, analysisStatus: 'skipped_user' } }, status: 200, statusText: 'OK', headers: {}, config }
    }
    diaryApi.mine = diaryApi.public = async () => ok({ items: [] })
    collectionApi.mine = async () => ok({ records: [] })
    for (const enabled of [true, false]) {
      const pinia = createPinia()
      const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }, { path: '/diary/42', component: { render: () => null } }] })
      await router.push('/')
      const app = createApp({ render: () => h(DiaryComposer, { editId: 42, initialContent: '<p>unchanged original</p>', initialVisibility: 'PRIVATE' }) })
      app.use(pinia).use(router).mount('#app'); await flush()
      document.querySelectorAll('.composer-vis-opt')[1].click(); await flush()
      if (!enabled) document.querySelector('.composer-ai-toggle input').click()
      await flush(); document.querySelector('.composer-submit').click(); await flush()
      assert(submissions.at(-1)?.analyze === enabled && submissions.at(-1)?.visibility === 'PUBLIC', 'unchanged content replaced the actual switch value')
      app.unmount(); await flush()
    }
    assert(submissions.length === 2, 'edit was not submitted once per switch state')
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
  await check('onboarding store enforces account isolation, monotonic merge, silent degradation, and retry cleanup', async () => {
    const { createPinia, setActivePinia } = f
    const { api } = await import('/src/api/core.ts')
    const { useOnboardingStore } = await import('/src/stores/onboarding.ts')
    const {
      computeCoreTaskProgress,
      createDefaultOnboardingState,
      isChecklistActive,
      mergeOnboardingStateMonotonic,
      shouldAutoShowWelcome,
    } = await import('/src/constants/onboarding.ts')

    // Pure helpers sanity
    const oldUser = createDefaultOnboardingState()
    assert(!shouldAutoShowWelcome(oldUser), 'old user should not auto-show welcome')
    assert(!isChecklistActive(oldUser), 'old user should not have active checklist')

    const newUser = createDefaultOnboardingState({ enrolledVersion: 1 })
    assert(shouldAutoShowWelcome(newUser), 'new enrolled user should auto-show welcome')
    assert(isChecklistActive(newUser), 'new enrolled user with NOT_STARTED checklist should be active')

    const merged = mergeOnboardingStateMonotonic(
      createDefaultOnboardingState({ enrolledVersion: 1, tasks: { ...oldUser.tasks, FIRST_DIARY: 'COMPLETED' } }),
      createDefaultOnboardingState({ enrolledVersion: 1, welcomeStatus: 'SKIPPED', tasks: { ...oldUser.tasks, FIRST_DIARY: 'NOT_STARTED', THEME_EXPLORED: 'COMPLETED' } }),
    )
    assert(merged.tasks.FIRST_DIARY === 'COMPLETED', 'monotonic merge downgraded COMPLETED task')
    assert(merged.welcomeStatus === 'SKIPPED', 'monotonic merge did not advance welcomeStatus')
    assert(computeCoreTaskProgress(merged).completed === 1 && computeCoreTaskProgress(merged).total === 4, 'THEME_EXPLORED should not count in core 4 denominator')

    // Store race, failure degradation, retry queue cleanup on account switch
    const pinia = createPinia(); setActivePinia(pinia)
    const store = useOnboardingStore()
    const slowUser1 = deferred()
    let failEvent = false
    const eventCalls = []
    api.defaults.adapter = async (config) => {
      if (config.url === '/onboarding') {
        if (config.headers?.Authorization === 'Bearer token-u1') return slowUser1.promise
        if (config.headers?.Authorization === 'Bearer token-fail') throw new Error('network down')
        return { data: { code: 0, message: 'OK', data: { enrolledVersion: 0, guideVersion: 1, welcomeStatus: 'NOT_STARTED', checklistStatus: 'NOT_STARTED', tasks: {}, hints: {} } }, status: 200, statusText: 'OK', headers: {}, config }
      }
      if (config.url === '/onboarding/events') {
        eventCalls.push(JSON.parse(config.data))
        if (failEvent) throw new Error('503')
        return { data: { code: 0, message: 'OK', data: { enrolledVersion: 1, guideVersion: 1, welcomeStatus: 'IN_PROGRESS', checklistStatus: 'NOT_STARTED', tasks: {}, hints: {} } }, status: 200, statusText: 'OK', headers: {}, config }
      }
      return { data: { code: 0, message: 'OK', data: {} }, status: 200, statusText: 'OK', headers: {}, config }
    }

    // API failure degrades silently without treating user as new
    localStorage.setItem('token', 'token-fail')
    await store.loadForUser(99, { force: true })
    assert(store.degraded && store.state.enrolledVersion === 0 && !store.canAutoShowWelcome, 'API failure falsely enrolled user')

    // Account switch discards stale response from previous account and clears retry queue
    localStorage.setItem('token', 'token-u1')
    const p1 = store.loadForUser(101, { force: true })
    failEvent = true
    await store.recordEvent({ eventType: 'THEME_EXPLORED' })
    assert(store.getPendingRetryCount() === 1, 'retryable UI event was not queued')

    localStorage.setItem('token', 'token-u2')
    await store.loadForUser(202, { force: true })
    assert(store.getPendingRetryCount() === 0, 'retry queue leaked across account switch')

    slowUser1.resolve({ data: { code: 0, message: 'OK', data: { enrolledVersion: 1, guideVersion: 1, welcomeStatus: 'NOT_STARTED', checklistStatus: 'NOT_STARTED', tasks: {}, hints: {} } }, status: 200, statusText: 'OK', headers: {}, config: {} })
    await p1
    assert(store.boundUserId === 202 && store.state.enrolledVersion === 0, 'stale user 101 response polluted user 202')
  })
  await check('OnboardingHost respects mutual exclusion, Esc/focus restore, welcome actions, and local-only replay', async () => {
    const { createApp, h, createPinia, setActivePinia, createRouter, createMemoryHistory } = f
    const { api } = await import('/src/api/core.ts')
    const { useAuthStore } = await import('/src/stores/auth.ts')
    const { useAiPreviewStore } = await import('/src/stores/aiPreview.ts')
    const { useOnboardingStore } = await import('/src/stores/onboarding.ts')
    const { default: OnboardingHost } = await import('/src/components/onboarding/OnboardingHost.vue')

    localStorage.clear()
    localStorage.setItem('token', 'onboarding-host-token')
    const postedEvents = []
    const postedDismisses = []
    api.defaults.adapter = async (config) => {
      if (config.url === '/onboarding') {
        return { data: { code: 0, message: 'OK', data: { enrolledVersion: 1, guideVersion: 1, welcomeStatus: 'NOT_STARTED', checklistStatus: 'NOT_STARTED', tasks: {}, hints: {} } }, status: 200, statusText: 'OK', headers: {}, config }
      }
      if (config.url === '/onboarding/events') {
        const body = JSON.parse(config.data)
        postedEvents.push(body)
        const nextWelcome = body.eventType === 'WELCOME_COMPLETED' ? 'COMPLETED' : 'IN_PROGRESS'
        return { data: { code: 0, message: 'OK', data: { enrolledVersion: 1, guideVersion: 1, welcomeStatus: nextWelcome, checklistStatus: 'NOT_STARTED', tasks: {}, hints: {} } }, status: 200, statusText: 'OK', headers: {}, config }
      }
      if (config.url === '/onboarding/dismiss') {
        const body = JSON.parse(config.data)
        postedDismisses.push(body)
        return { data: { code: 0, message: 'OK', data: { enrolledVersion: 1, guideVersion: 1, welcomeStatus: 'SKIPPED', checklistStatus: 'NOT_STARTED', tasks: {}, hints: {} } }, status: 200, statusText: 'OK', headers: {}, config }
      }
      return { data: { code: 0, message: 'OK', data: {} }, status: 200, statusText: 'OK', headers: {}, config }
    }

    const pinia = createPinia(); setActivePinia(pinia)
    const auth = useAuthStore()
    auth.token = 'onboarding-host-token'
    auth.userId = 777
    const aiPreview = useAiPreviewStore()
    const onboarding = useOnboardingStore()
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { render: () => null } },
        { path: '/login', component: { render: () => null } },
        { path: '/write', component: { render: () => null } },
        { path: '/chat', component: { render: () => null } },
        { path: '/admin/users', meta: { requiresAdmin: true }, component: { render: () => null } },
      ],
    })
    await router.push('/admin/users')
    const app = createApp({ render: () => h(OnboardingHost) })
    app.use(pinia).use(router).mount('#app')

    // Load enrolled new user while blocked by /admin/users + aiPreview modal
    aiPreview.enqueue({ kind: 'DIARY_ANALYSIS', diary: { id: 1 } })
    await onboarding.loadForUser(777, { force: true })
    await new Promise((r) => setTimeout(r, 180)); await flush()
    assert(!document.querySelector('[data-testid="onboarding-welcome-dialog"]'), 'welcome opened on /admin route with aiPreview active')

    // Move to / while aiPreview still blocks
    await router.push('/')
    await new Promise((r) => setTimeout(r, 180)); await flush()
    assert(!document.querySelector('[data-testid="onboarding-welcome-dialog"]'), 'welcome opened while aiPreview modal active')

    // Release aiPreview -> auto-welcome opens and stays open after WELCOME_STARTED
    aiPreview.pop()
    await new Promise((r) => setTimeout(r, 180)); await flush()
    assert(document.querySelector('[data-testid="onboarding-welcome-dialog"]'), 'welcome did not auto-open for enrolled user')
    assert(postedEvents.some((e) => e.eventType === 'WELCOME_STARTED' && e.scope === 'WEB'), 'WELCOME_STARTED was not sent')

    // Mount a live nav anchor and step through 4 real-UI spotlight tour steps to Step 4 actions
    const navWriteAnchor = document.createElement('a')
    navWriteAnchor.setAttribute('data-guide-id', 'nav-write')
    navWriteAnchor.textContent = '记录'
    navWriteAnchor.style.cssText = 'position:fixed;top:16px;left:120px;width:64px;height:32px;'
    document.body.appendChild(navWriteAnchor)
    window.dispatchEvent(new Event('resize'))
    await window.paint()
    assert(document.querySelector('[data-testid="onboarding-welcome-dialog"]')?.getAttribute('data-tour-step') === 'write', 'step 1 spotlight did not render')
    assert(document.querySelector('[data-testid="onboarding-spotlight-ring"]'), 'spotlight ring did not anchor to live nav-write element')
    document.querySelector('[data-testid="onboarding-next-btn"]').click(); await window.paint()
    assert(document.querySelector('[data-testid="onboarding-welcome-dialog"]')?.getAttribute('data-tour-step') === 'chat', 'step 2 spotlight did not render')
    document.querySelector('[data-testid="onboarding-next-btn"]').click(); await window.paint()
    assert(document.querySelector('[data-testid="onboarding-welcome-dialog"]')?.getAttribute('data-tour-step') === 'profile', 'step 3 spotlight did not render')
    document.querySelector('[data-testid="onboarding-next-btn"]').click(); await window.paint()
    assert(document.querySelector('[data-testid="onboarding-welcome-dialog"]')?.getAttribute('data-tour-step') === 'square', 'step 4 spotlight did not render')
    const dialogText = document.querySelector('[data-testid="onboarding-welcome-dialog"]').textContent
    assert(dialogText.includes('记录今天') && dialogText.includes('找 AI 聊聊') && dialogText.includes('稍后探索'), 'post-welcome actions missing')
    navWriteAnchor.remove()

    // Click 记录今天 -> completes welcome and routes to /write
    document.querySelector('[data-testid="onboarding-action-WRITE_TODAY"]').click(); await flush()
    assert(!onboarding.isWelcomeOpen, 'welcome store stayed open after action')
    await new Promise((r) => setTimeout(r, 260)); await flush()
    assert(!document.querySelector('[data-testid="onboarding-welcome-dialog"]'), 'welcome stayed open after action')
    assert(postedEvents.some((e) => e.eventType === 'WELCOME_COMPLETED'), 'WELCOME_COMPLETED was not sent')
    assert(router.currentRoute.value.path === '/write', 'WRITE_TODAY did not navigate to /write')

    // Local-only replay: focus restore on Esc & zero remote API calls
    const triggerBtn = document.createElement('button')
    triggerBtn.textContent = '使用指南'
    document.body.appendChild(triggerBtn)
    triggerBtn.focus()
    const eventCountBeforeReplay = postedEvents.length
    const dismissCountBeforeReplay = postedDismisses.length

    onboarding.openWelcomeReplay(); await flush()
    assert(document.querySelector('[data-testid="onboarding-welcome-dialog"]'), 'replay dialog did not open')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flush()
    assert(!onboarding.isWelcomeOpen, 'replay store stayed open on Esc')
    await new Promise((r) => setTimeout(r, 260)); await flush()
    assert(!document.querySelector('[data-testid="onboarding-welcome-dialog"]'), 'Esc did not close replay dialog')
    assert(document.activeElement === triggerBtn, 'focus was not restored to trigger element on close')
    assert(postedEvents.length === eventCountBeforeReplay && postedDismisses.length === dismissCountBeforeReplay, 'replay sent remote onboarding requests')

    triggerBtn.remove()
    app.unmount()
  })
  await check('StarterChecklist renders 4 core tasks + optional theme, supports task skip/dismiss, and splits fetchSeq from mutations', async () => {
    const { createApp, h, createPinia, setActivePinia, createRouter, createMemoryHistory } = f
    const { api } = await import('/src/api/core.ts')
    const { useAuthStore } = await import('/src/stores/auth.ts')
    const { useOnboardingStore } = await import('/src/stores/onboarding.ts')
    const { default: StarterChecklist } = await import('/src/components/onboarding/StarterChecklist.vue')

    localStorage.clear()
    localStorage.setItem('token', 'checklist-token')
    const dismissCalls = []
    const slowRefresh = deferred()
    let holdGet = false
    let serverState = {
      enrolledVersion: 1,
      guideVersion: 1,
      welcomeStatus: 'COMPLETED',
      checklistStatus: 'IN_PROGRESS',
      tasks: {
        FIRST_DIARY: 'COMPLETED',
        FIRST_ANALYSIS_VIEWED: 'NOT_STARTED',
        FIRST_CHAT_REPLY: 'NOT_STARTED',
        MEMORY_CENTER_VISITED: 'NOT_STARTED',
        THEME_EXPLORED: 'NOT_STARTED',
      },
      hints: {},
    }
    api.defaults.adapter = async (config) => {
      if (config.url === '/onboarding') {
        if (holdGet) return slowRefresh.promise
        return { data: { code: 0, message: 'OK', data: structuredClone(serverState) }, status: 200, statusText: 'OK', headers: {}, config }
      }
      if (config.url === '/onboarding/events') {
        const body = JSON.parse(config.data)
        if (body.eventType === 'THEME_EXPLORED') serverState.tasks.THEME_EXPLORED = 'COMPLETED'
        return { data: { code: 0, message: 'OK', data: structuredClone(serverState) }, status: 200, statusText: 'OK', headers: {}, config }
      }
      if (config.url === '/onboarding/dismiss') {
        const body = JSON.parse(config.data)
        dismissCalls.push(body)
        if (body.target === 'TASK' && body.key) serverState.tasks[body.key] = 'SKIPPED'
        if (body.target === 'CHECKLIST') serverState.checklistStatus = 'SKIPPED'
        return { data: { code: 0, message: 'OK', data: structuredClone(serverState) }, status: 200, statusText: 'OK', headers: {}, config }
      }
      return { data: { code: 0, message: 'OK', data: {} }, status: 200, statusText: 'OK', headers: {}, config }
    }

    const pinia = createPinia(); setActivePinia(pinia)
    const auth = useAuthStore(); auth.token = 'checklist-token'; auth.userId = 808
    const onboarding = useOnboardingStore()
    await onboarding.loadForUser(808, { force: true })

    // Overlapping refreshIfActive + mutation must not leave loading stuck true
    holdGet = true
    const refreshPromise = onboarding.refreshAfterBusinessAction()
    assert(onboarding.loading === true, 'loading should be true while refresh is in flight')
    await onboarding.recordThemeExplored()
    slowRefresh.resolve({ data: { code: 0, message: 'OK', data: structuredClone(serverState) }, status: 200, statusText: 'OK', headers: {}, config: {} })
    await refreshPromise
    assert(onboarding.loading === false, 'loading remained stuck true after overlapping mutation')

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { render: () => null } },
        { path: '/write', component: { render: () => null } },
        { path: '/chat', component: { render: () => null } },
        { path: '/ai-memory', component: { render: () => null } },
        { path: '/profile/:userId', component: { render: () => null } },
      ],
    })
    await router.push('/')
    const app = createApp({ render: () => h(StarterChecklist) })
    app.use(pinia).use(router).mount('#app'); await flush()

    assert(document.querySelector('[data-testid="starter-checklist-progress"]').textContent.includes('1 / 4'), 'core progress denominator should be 1 / 4 even with THEME_EXPLORED completed')
    document.querySelector('[data-testid="starter-skip-FIRST_ANALYSIS_VIEWED"]').click(); await flush()
    assert(dismissCalls.some((d) => d.target === 'TASK' && d.key === 'FIRST_ANALYSIS_VIEWED'), 'task skip did not call dismiss API')
    assert(document.querySelector('[data-testid="starter-task-FIRST_ANALYSIS_VIEWED"]').textContent.includes('已跳过'), 'skipped task missing readable status text')
    assert(document.querySelector('[data-testid="starter-checklist-progress"]').textContent.includes('1 / 4'), 'skipped task should not count as completed')

    document.querySelector('[data-testid="starter-checklist-dismiss"]').click(); await flush()
    assert(dismissCalls.some((d) => d.target === 'CHECKLIST'), 'checklist dismiss did not call dismiss API')
    assert(!document.querySelector('[data-testid="starter-checklist"]'), 'checklist stayed visible after dismiss')
    assert(onboarding.state.tasks.FIRST_CHAT_REPLY === 'NOT_STARTED', 'dismissing checklist must not forge task completion')
    app.unmount()
  })
  await check('P0-C MemoryProfileView reports visit on empty success only, and Chat starters replace templates while preserving hand-written drafts', async () => {
    const { createApp, h, createPinia, setActivePinia, createRouter, createMemoryHistory } = f
    const { NDialogProvider } = await import('/node_modules/.vite/deps/naive-ui.js')
    const { api } = await import('/src/api/core.ts')
    const { memoryApi, diaryApi } = await import('/src/api/index.ts')
    const { useAuthStore } = await import('/src/stores/auth.ts')
    const { useOnboardingStore } = await import('/src/stores/onboarding.ts')
    const { default: MemoryProfileView } = await import('/src/components/memory/MemoryProfileView.vue')
    const { useChat } = await import('/src/composables/useChat.ts')

    localStorage.clear()
    localStorage.setItem('token', 'p0c-hooks-token')
    const postedEvents = []
    api.defaults.adapter = async (config) => {
      if (config.url === '/onboarding') {
        return { data: { code: 0, message: 'OK', data: { enrolledVersion: 1, guideVersion: 1, welcomeStatus: 'COMPLETED', checklistStatus: 'IN_PROGRESS', tasks: {}, hints: {} } }, status: 200, statusText: 'OK', headers: {}, config }
      }
      if (config.url === '/onboarding/events') {
        const body = JSON.parse(config.data)
        postedEvents.push(body)
        return { data: { code: 0, message: 'OK', data: { enrolledVersion: 1, guideVersion: 1, welcomeStatus: 'COMPLETED', checklistStatus: 'IN_PROGRESS', tasks: { MEMORY_CENTER_VISITED: 'COMPLETED' }, hints: {} } }, status: 200, statusText: 'OK', headers: {}, config }
      }
      return { data: { code: 0, message: 'OK', data: [] }, status: 200, statusText: 'OK', headers: {}, config }
    }

    const pinia = createPinia(); setActivePinia(pinia)
    const auth = useAuthStore(); auth.token = 'p0c-hooks-token'; auth.userId = 909
    const onboarding = useOnboardingStore()
    await onboarding.loadForUser(909, { force: true })

    // Error state must NOT report MEMORY_CENTER_VISITED
    let failMemories = true
    memoryApi.getAll = async () => { if (failMemories) throw new Error('offline'); return ok([]) }
    memoryApi.getCandidates = async () => ok([])
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
    await router.push('/')
    const app1 = createApp({ render: () => h(NDialogProvider, null, { default: () => h(MemoryProfileView) }) })
    app1.use(pinia).use(router).mount('#app'); await flush()
    assert(!postedEvents.some((e) => e.eventType === 'MEMORY_CENTER_VISITED'), 'failed memory load falsely reported MEMORY_CENTER_VISITED')
    app1.unmount()

    // Genuine empty memory list MUST report MEMORY_CENTER_VISITED once
    failMemories = false
    const app2 = createApp({ render: () => h(NDialogProvider, null, { default: () => h(MemoryProfileView) }) })
    app2.use(pinia).use(router).mount('#app'); await flush()
    assert(postedEvents.filter((e) => e.eventType === 'MEMORY_CENTER_VISITED').length === 1, 'empty memory center did not report MEMORY_CENTER_VISITED')
    app2.unmount()

    // Chat starter fills empty draft without forcing Pro or overwriting existing draft, and syncs awaitingApproval
    diaryApi.mine = async () => ok({ items: [] })
    let chatApiHandle = null
    const ChatProbe = {
      setup() {
        chatApiHandle = useChat()
        return () => null
      },
    }
    const app3 = createApp(ChatProbe)
    app3.use(pinia).use(router).mount('#app'); await flush()
    chatApiHandle.fillStarterPrompt('帮我梳理今天要做的三件事')
    assert(chatApiHandle.draft.value === '帮我梳理今天要做的三件事', 'starter did not fill empty draft')
    assert(chatApiHandle.useReasoning.value === false, 'starter must not force Pro')
    chatApiHandle.fillStarterPrompt('另一段提示')
    assert(chatApiHandle.draft.value === '另一段提示', 'starter did not replace app-owned template')
    chatApiHandle.draft.value = '我自己写的内容，不要覆盖'
    chatApiHandle.fillStarterPrompt('帮我梳理今天要做的三件事')
    assert(chatApiHandle.draft.value === '我自己写的内容，不要覆盖', 'starter overwrote hand-written user draft')
    assert(chatApiHandle.pendingStarterReplaceText.value === '帮我梳理今天要做的三件事', 'hand-written draft did not request confirmation')

    chatApiHandle.pendingApprovals.value = [{ toolCallId: 't1', toolName: 'saveMemory' }]
    await flush()
    assert(onboarding.externalModalBlocking === true, 'awaitingApproval did not block onboarding host')
    app3.unmount(); await flush()
    assert(onboarding.externalModalBlocking === false, 'unmounting Chat did not clear externalModalBlocking')
  })
  await check('P1 SpotlightGuide positions on visible anchor, falls back cleanly when anchor missing, cleans up on route change, persists seen/dismiss, and keeps TOOL_APPROVAL_WEB non-blocking', async () => {
    const { createApp, h, ref, createPinia, setActivePinia, createRouter, createMemoryHistory } = f
    const { api } = await import('/src/api/core.ts')
    const { useAuthStore } = await import('/src/stores/auth.ts')
    const { useOnboardingStore } = await import('/src/stores/onboarding.ts')
    const { ONBOARDING_HINT_MAP } = await import('/src/constants/onboarding.ts')
    const { default: SpotlightGuide } = await import('/src/components/onboarding/SpotlightGuide.vue')

    localStorage.clear()
    localStorage.setItem('token', 'p1-spotlight-token')
    const postedEvents = []
    const postedDismisses = []
    const serverHints = {}
    api.defaults.adapter = async (config) => {
      if (config.url === '/onboarding') {
        return { data: { code: 0, message: 'OK', data: { enrolledVersion: 1, guideVersion: 1, welcomeStatus: 'COMPLETED', checklistStatus: 'COMPLETED', tasks: {}, hints: { ...serverHints } } }, status: 200, statusText: 'OK', headers: {}, config }
      }
      if (config.url === '/onboarding/events') {
        const body = JSON.parse(config.data)
        postedEvents.push(body)
        if (body.eventType === 'HINT_SEEN' && body.hintKey) serverHints[body.hintKey] = 'COMPLETED'
        return { data: { code: 0, message: 'OK', data: { enrolledVersion: 1, guideVersion: 1, welcomeStatus: 'COMPLETED', checklistStatus: 'COMPLETED', tasks: {}, hints: { ...serverHints } } }, status: 200, statusText: 'OK', headers: {}, config }
      }
      if (config.url === '/onboarding/dismiss') {
        const body = JSON.parse(config.data)
        postedDismisses.push(body)
        if (body.target === 'HINT' && body.key) serverHints[body.key] = 'SKIPPED'
        return { data: { code: 0, message: 'OK', data: { enrolledVersion: 1, guideVersion: 1, welcomeStatus: 'COMPLETED', checklistStatus: 'COMPLETED', tasks: {}, hints: { ...serverHints } } }, status: 200, statusText: 'OK', headers: {}, config }
      }
      return { data: { code: 0, message: 'OK', data: {} }, status: 200, statusText: 'OK', headers: {}, config }
    }

    const pinia = createPinia(); setActivePinia(pinia)
    const auth = useAuthStore(); auth.token = 'p1-spotlight-token'; auth.userId = 616
    const onboarding = useOnboardingStore()
    await onboarding.loadForUser(616, { force: true })

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/diary/1', component: { render: () => null } },
        { path: '/chat', component: { render: () => null } },
      ],
    })
    await router.push('/diary/1')

    // 1. Anchor present -> anchored spotlight + seen persistence
    const anchorEl = document.createElement('div')
    anchorEl.setAttribute('data-guide-id', 'diary-analysis-card')
    anchorEl.style.cssText = 'position:fixed;top:120px;left:24px;width:280px;height:80px;'
    document.body.appendChild(anchorEl)

    const currentHint = ref(onboarding.pickEligibleHint(['ANALYSIS_READY']))
    const nonBlocking = ref(false)
    const app = createApp({
      render: () => h(SpotlightGuide, { hint: currentHint.value, nonBlocking: nonBlocking.value }),
    })
    app.use(pinia).use(router).mount('#app'); await flush()
    const layer1 = document.querySelector('[data-testid="spotlight-guide"]')
    assert(layer1 && layer1.getAttribute('data-fallback') === 'false', 'visible anchor should render anchored spotlight')
    assert(getComputedStyle(layer1).pointerEvents === 'none', 'spotlight layer must not block page clicks')

    document.querySelector('[data-testid="spotlight-seen-btn"]').click(); await flush()
    assert(postedEvents.some((e) => e.eventType === 'HINT_SEEN' && e.hintKey === 'ANALYSIS_READY' && e.scope === 'WEB'), 'HINT_SEEN was not persisted')
    assert(onboarding.isHintEligible('ANALYSIS_READY') === false, 'seen hint should no longer be eligible')

    // 2. Missing anchor -> graceful corner fallback without blocking mask, dismissed via Esc
    currentHint.value = onboarding.pickEligibleHint(['REPORT_AVAILABLE'])
    await flush()
    const layer2 = document.querySelector('[data-testid="spotlight-guide"]')
    assert(layer2 && layer2.getAttribute('data-fallback') === 'true', 'missing anchor must fall back to non-blocking card')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); await flush()
    assert(postedDismisses.some((d) => d.target === 'HINT' && d.key === 'REPORT_AVAILABLE' && d.scope === 'WEB'), 'Esc did not persist hint dismiss')

    // 3. TOOL_APPROVAL_WEB remains eligible while externalModalBlocking is true & renders non-blocking, then cleans up on route change
    onboarding.setExternalModalBlocking(true)
    assert(onboarding.isHintEligible('CHAT_CITATION') === false, 'non-approval hint must be blocked while approval modal active')
    assert(onboarding.isHintEligible('TOOL_APPROVAL_WEB') === true, 'TOOL_APPROVAL_WEB must remain eligible alongside approval')
    currentHint.value = ONBOARDING_HINT_MAP.TOOL_APPROVAL_WEB
    nonBlocking.value = true
    await flush()
    assert(document.querySelector('[data-testid="spotlight-guide"]')?.classList.contains('is-non-blocking'), 'TOOL_APPROVAL_WEB must use non-blocking mode')

    await router.push('/chat'); await flush()
    assert(!document.querySelector('[data-testid="spotlight-guide"]'), 'route change must dismiss active spotlight guide')

    anchorEl.remove()
    app.unmount()

    // 4. KeepAlive lifecycle: deactivated view must NOT teleport hint when hint changes in background, and must restore on re-activation
    const { KeepAlive } = await import('/node_modules/.vite/deps/vue.js')
    const showKeepAlivePage = ref(true)
    const keepAliveHint = ref(null)
    const KeepAliveChild = {
      name: 'KeepAliveChild',
      setup() {
        return () => h('div', [
          h('div', { 'data-guide-id': 'chat-citation-panel', style: 'position:fixed;top:120px;left:24px;width:240px;height:60px;' }),
          h(SpotlightGuide, { hint: keepAliveHint.value }),
        ])
      },
    }
    const KeepAliveOther = {
      name: 'KeepAliveOther',
      render: () => h('div', 'other-page'),
    }
    const appKeepAlive = createApp({
      render: () => h(KeepAlive, null, {
        default: () => (showKeepAlivePage.value ? h(KeepAliveChild, { key: 'chat' }) : h(KeepAliveOther, { key: 'other' })),
      }),
    })
    appKeepAlive.use(pinia).use(router).mount('#app'); await flush()

    // Navigate away (deactivates KeepAliveChild), then a background hint becomes eligible
    showKeepAlivePage.value = false; await flush()
    keepAliveHint.value = ONBOARDING_HINT_MAP.CHAT_CITATION; await flush()
    assert(!document.querySelector('[data-testid="spotlight-guide"]'), 'deactivated KeepAlive SpotlightGuide leaked onto another route')

    // Return to KeepAliveChild -> hint should now render cleanly
    showKeepAlivePage.value = true; await flush()
    assert(document.querySelector('[data-testid="spotlight-guide"]')?.getAttribute('data-hint-key') === 'CHAT_CITATION', 'reactivated KeepAlive SpotlightGuide stayed stuck hidden')

    appKeepAlive.unmount()
  })
} finally { await browser.close(); await server.close() }
process.exitCode = failed ? 1 : 0
