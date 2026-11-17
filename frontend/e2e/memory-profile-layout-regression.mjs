import { createServer } from 'vite'
import { chromium } from 'playwright'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

// Real Vue component and theme CSS; only HTTP responses use synthetic records.
const root = fileURLToPath(new URL('..', import.meta.url)).replace(/\\/g, '/').replace(/\/$/, '')
const fixture = `
import { createApp, h } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { NDialogProvider } from 'naive-ui'
import MemoryProfileView from '/src/components/memory/MemoryProfileView.vue'
import { api } from '/src/api/core.ts'
import '/src/styles.css'
const memories = [
  { id: 1, attributeKey: '运动', attributeValue: '喜欢晨跑，周末会选择公园路线。'.repeat(15), isCore: true },
  { id: 2, attributeKey: '运动', attributeValue: '周三晚上喜欢游泳。' },
  { id: 3, attributeKey: '沟通', attributeValue: '喜欢先说结论，再给解释。' },
  { id: 4, attributeKey: '阅读', attributeValue: '喜欢阅读科普书。' },
]
const candidates = [
  { id: 101, attributeKey: '饮食', attributeValue: '喜欢清淡口味。', hasConflict: true },
  { id: 102, attributeKey: '饮食', attributeValue: '喜欢辛辣口味。', hasConflict: true },
  { id: 103, attributeKey: '作息', attributeValue: '最近尝试早睡。' },
]
const state = { requests: [], evidenceText: {}, deferred: {}, releases: {}, completedEvidence: 0 }
window.memoryLayout = {
  state,
  replaceRecords(formal, pending) { memories.splice(0, memories.length, ...formal); candidates.splice(0, candidates.length, ...pending) },
  deferEvidence(id) { state.deferred[id] = new Promise(resolve => { state.releases[id] = resolve }) },
  releaseEvidence(id, text) { state.releases[id]([{ id, sourceType: 'chat_candidate', evidenceText: text }]) },
}
api.defaults.adapter = async config => {
  state.requests.push({ url: config.url, method: config.method })
  let data = []
  if (config.url === '/memory') {
    if (state.formalFail) throw new Error('fixture formal refresh offline')
    data = memories.map(item => ({ ...item }))
  }
  else if (config.url === '/memory/candidates') data = candidates.map(item => ({ ...item }))
  else if (config.method === 'delete' && /^\\/memory\\/\\d+$/.test(config.url)) {
    const id = Number(config.url.split('/')[2])
    const index = memories.findIndex(item => item.id === id)
    if (index >= 0) memories.splice(index, 1)
  }
  else if (/^\\/memory\\/\\d+\\/evidence$/.test(config.url)) {
    const id = Number(config.url.split('/')[2])
    if (state.deferred[id]) { const pending = state.deferred[id]; delete state.deferred[id]; data = await pending }
    else data = state.evidenceRecords || [{ id, sourceType: 'chat_candidate', evidenceText: state.evidenceText[id] || '这是第' + id + '条记忆的原始依据。' }]
    state.completedEvidence++
  } else if (/^\\/memory\\/candidates\\/\\d+\\/evidence$/.test(config.url)) {
    data = [{ id: 101, sourceType: 'chat_candidate', evidenceText: '用户提供的候选原文。' }]
  }
  return { data: { data }, status: 200, statusText: 'OK', headers: {}, config }
}
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
await router.push('/')
createApp({ render: () => h(NDialogProvider, null, { default: () => h(MemoryProfileView) }) })
  .use(createPinia()).use(router).mount('#app')
window.memoryLayoutReady = true
`
const server = await createServer({
  configFile: false, root, server: { host: '127.0.0.1', port: 0 },
  plugins: [vue(), {
    name: 'memory-layout-fixture',
    resolveId(id) { if (id === '/__memory-layout-fixture.js') return '\0memory-layout-fixture' },
    load(id) { if (id === '\0memory-layout-fixture') return fixture },
    configureServer(s) {
      s.middlewares.use('/__memory-layout', (_req, res) => {
        res.setHeader('Content-Type', 'text/html')
        res.end('<meta name="viewport" content="width=device-width, initial-scale=1"><div id="app" style="max-width:900px;margin:24px auto;padding:0 20px"></div><script type="module" src="/__memory-layout-fixture.js"></script>')
      })
    },
  }],
})
await server.listen()
const url = `http://127.0.0.1:${server.httpServer.address().port}/__memory-layout`
if (process.argv.includes('--serve')) {
  console.log(`Memory layout fixture: ${url}`)
  await new Promise(resolve => { process.once('SIGINT', resolve); process.once('SIGTERM', resolve) })
  await server.close()
} else {
  const browser = await chromium.launch({ headless: true })
  let failed = 0, passed = 0
  async function check(name, test, width = 1200) {
    const page = await browser.newPage({ viewport: { width, height: 844 } })
    page.setDefaultTimeout(5000)
    try {
      await page.goto(url)
      await page.waitForFunction(() => window.memoryLayoutReady && document.querySelector('.memory-overview')?.textContent.includes('4 条正式记忆'))
      await test(page)
      passed++; console.log(`PASS ${name}`)
    } catch (error) { failed++; console.log(`FAIL ${name}: ${error.message}`) }
    finally { await page.close() }
  }
  const details = page => page.locator('.memory-details, .candidate-details')
  const formalButton = (page, index = 0) => page.locator('.memory-item').nth(index).getByRole('button', { name: '查看全文与依据', exact: true })
  async function closeDetails(page) {
    await page.locator('#memory-details-return').click()
    await details(page).waitFor({ state: 'hidden' })
    await page.locator('.n-drawer-mask').waitFor({ state: 'hidden' })
  }
  try {
    await check('the list bounds initial groups and exposes candidate counts without nested detail expanders', async page => {
      const toggle = page.getByRole('button', { name: /待确认的记忆/ })
      const text = await toggle.innerText()
      assert.match(text, /3\s*条.*2\s*个属性/s)
      assert.match(text, /1\s*个属性.*候选/s)
      assert.equal(await toggle.getAttribute('aria-expanded'), 'false')
      assert.equal(await page.locator('.candidate-item').count(), 0)
      assert.equal(await page.locator('.memory-item').count(), 2)
      assert.equal(await page.locator('.memory-list .memory-details, .candidate-section .candidate-details').count(), 0)
      const button = formalButton(page)
      assert.equal(await button.getAttribute('aria-haspopup'), 'dialog')
      assert.equal(await button.getAttribute('aria-expanded'), null, 'evidence is still a second inline expander')
      assert.equal(await page.evaluate(() => window.memoryLayout.state.requests.some(r => /evidence/.test(r.url))), false, 'list eagerly loads evidence')
    })
    await check('returning from details restores list focus before opening another record', async page => {
      for (const [index, id] of [[0, 1], [1, 2], [2, 3]]) {
        if (id === 3) await page.locator('.formal-group').nth(1).locator('.group-toggle').click()
        const button = formalButton(page, index)
        await button.click()
        await page.locator(`#memory-detail-${id}`).waitFor()
        assert.equal(await details(page).count(), 1, 'more than one reading panel is open')
        await closeDetails(page)
        assert.equal(await button.evaluate(el => el === document.activeElement), true, 'focus did not return to the record')
      }
    })
    await check('the separate detail panel identifies its attribute and shows one complete memory with maintenance actions', async page => {
      await formalButton(page).click()
      const panel = page.getByRole('region', { name: '运动 · 详情与依据', exact: true })
      await panel.waitFor()
      assert.equal(await panel.evaluate(el => !!el.closest('.memory-item')), false, 'details remain nested in the list')
      assert.equal(await panel.locator('.memory-detail-value').count(), 1, 'memory is repeated in details')
      assert.equal(await panel.locator('.memory-detail-value').innerText(), '喜欢晨跑，周末会选择公园路线。'.repeat(15))
      assert.equal(await page.locator('.memory-detail-actions').getByRole('button', { name: '删除', exact: true }).count(), 1)
      await page.locator('.memory-detail-actions').getByRole('button', { name: '编辑记忆', exact: true }).click()
      assert.equal(await panel.getByRole('textbox', { name: '编辑记忆内容' }).count(), 1, 'editor is outside details')
    })
    await check('confirmed deletion closes the detail drawer and returns focus to the list heading', async page => {
      await formalButton(page).click()
      await page.locator('.memory-detail-actions').getByRole('button', { name: '删除', exact: true }).click()
      const confirm = page.getByRole('button', { name: '确认删除', exact: true })
      await confirm.waitFor()
      assert.equal(await page.evaluate(() => window.memoryLayout.state.requests.some(r => r.method === 'delete')), false, 'memory was deleted before confirmation')
      await confirm.click()
      await page.waitForFunction(() => document.querySelector('.memory-overview')?.textContent.includes('3 条正式记忆'))
      await page.locator('.n-drawer').waitFor({ state: 'hidden' })
      assert.equal(await page.locator('.memory-details, .candidate-details').count(), 0, 'deleted memory left an empty detail panel')
      assert.equal(await page.locator('.formal-heading').evaluate(el => el === document.activeElement), true, 'focus did not return to the heading after its opener was deleted')
      assert.equal(await page.evaluate(() => window.memoryLayout.state.requests.filter(r => r.method === 'delete' && r.url === '/memory/1').length), 1)
      await page.getByRole('button', { name: '刷新', exact: true }).click()
      assert.equal(await page.locator('.memory-overview').innerText(), '3 条正式记忆 · 3 条待确认', 'refresh resurrected the deleted fixture memory')
    })
    await check('unsaved edits keep the panel open until save or cancel', async page => {
      const button = formalButton(page)
      await button.click()
      const panel = page.locator('#memory-detail-1')
      await page.locator('.memory-detail-actions').getByRole('button', { name: '编辑记忆', exact: true }).click()
      const editor = panel.getByRole('textbox', { name: '编辑记忆内容' })
      await editor.fill('还没有保存的草稿')
      const groupToggle = page.locator('.formal-group .group-toggle').first()
      assert.equal(await groupToggle.isDisabled(), true, 'group can hide an unsaved draft')
      assert.equal(await formalButton(page, 1).isDisabled(), true, 'another record can replace a draft')
      assert.equal(await page.locator('.section-head button').filter({ hasText: '刷新' }).isDisabled(), true, 'refresh can replace a draft')
      assert.equal(await page.locator('#memory-details-return').isDisabled(), true, 'return can discard a draft')
      await editor.press('Escape')
      assert.equal(await editor.inputValue(), '还没有保存的草稿')
      assert.equal(await editor.isVisible(), true, 'Escape closed an unsaved draft')
      await page.locator('.memory-detail-actions').getByRole('button', { name: '取消', exact: true }).click()
      await closeDetails(page)
      assert.equal(await button.evaluate(el => el === document.activeElement), true)
      await groupToggle.click()
      await groupToggle.click()
      assert.equal(await details(page).count(), 0, 'reopening a group unexpectedly reopens old details')
    })
    await check('a failed formal refresh cannot discard a later unsaved draft', async page => {
      await page.evaluate(() => { window.memoryLayout.state.formalFail = true })
      await page.getByRole('button', { name: '刷新', exact: true }).click()
      const retry = page.locator('button').filter({ hasText: '重试正式记忆' })
      await retry.waitFor()
      await formalButton(page).click()
      const panel = page.locator('#memory-detail-1')
      await page.locator('.memory-detail-actions').getByRole('button', { name: '编辑记忆', exact: true }).click()
      const editor = panel.getByRole('textbox', { name: '编辑记忆内容' })
      await editor.fill('失败后的未保存草稿')
      assert.equal(await retry.isDisabled(), true, 'retry can discard an unsaved draft')
      assert.equal(await editor.inputValue(), '失败后的未保存草稿')
      await page.locator('.memory-detail-actions').getByRole('button', { name: '取消', exact: true }).click()
      await closeDetails(page)
      assert.equal(await retry.isEnabled(), true)
      await page.evaluate(() => { window.memoryLayout.state.formalFail = false })
      await retry.click()
      await retry.waitFor({ state: 'hidden' })
      assert.equal(await details(page).count(), 0)
    })
    await check('refresh invalidates a closed pending detail response before opening fresh evidence', async page => {
      await page.evaluate(() => window.memoryLayout.deferEvidence(1))
      const button = formalButton(page)
      await button.click()
      await page.waitForFunction(() => window.memoryLayout.state.requests.some(r => r.url === '/memory/1/evidence'))
      await closeDetails(page)
      await page.evaluate(() => { window.memoryLayout.state.evidenceText[1] = '刷新后的原始依据' })
      await page.getByRole('button', { name: '刷新', exact: true }).click()
      await button.click()
      await page.getByRole('tab', { name: /形成依据/ }).click()
      await page.getByText('刷新后的原始依据', { exact: true }).waitFor()
      await page.evaluate(() => window.memoryLayout.releaseEvidence(1, '过期请求中的旧依据'))
      await page.waitForFunction(() => window.memoryLayout.state.completedEvidence >= 2)
      const text = await page.locator('.memory-details').innerText()
      assert.ok(text.includes('刷新后的原始依据') && !text.includes('过期请求中的旧依据'), 'old response repopulated a refreshed detail cache')
    })
    await check('candidate and formal evidence use the same independent reading panel', async page => {
      await page.getByRole('button', { name: /待确认的记忆/ }).click()
      const candidate = page.locator('.candidate-item').first().getByRole('button', { name: '查看全文与依据', exact: true })
      await candidate.click()
      await page.locator('.candidate-details').waitFor()
      assert.equal(await page.locator('.candidate-section .candidate-details').count(), 0)
      assert.equal(await page.locator('.candidate-decisions').getByRole('button', { name: '确认', exact: true }).count(), 1)
      await closeDetails(page)
      assert.equal(await candidate.evaluate(el => el === document.activeElement), true)
      await formalButton(page).click()
      assert.equal(await page.locator('.candidate-details').count(), 0)
      assert.equal(await page.locator('.memory-details').count(), 1)
      await closeDetails(page)
      await candidate.click()
      assert.equal(await page.locator('.memory-details').count(), 0)
      assert.equal(await page.locator('.candidate-details').count(), 1)
    })
    await check('long groups and evidence are paged without losing the original text or reading position', async page => {
      const text = '喜欢先独立整理问题，再和信任的人讨论；愿意根据新的依据调整计划。'.repeat(12).slice(0, 480)
      await page.evaluate(text => {
        window.memoryLayout.replaceRecords(
          Array.from({ length: 15 }, (_, i) => ({ id: i + 1, attributeKey: '学习与沟通', attributeValue: text, isCore: i === 0 })),
          Array.from({ length: 8 }, (_, i) => ({ id: i + 101, attributeKey: '沟通偏好', attributeValue: text })),
        )
        window.memoryLayout.state.evidenceRecords = Array.from({ length: 20 }, (_, i) => ({ id: i + 1, sourceType: 'chat_candidate', sourceConversationId: i + 1000, evidenceText: '用户提供的连续原文。'.repeat(12).slice(0, 160) }))
      }, text)
      await page.getByRole('button', { name: '刷新', exact: true }).click()
      await page.waitForFunction(() => document.querySelector('.memory-overview')?.textContent.includes('15 条正式记忆'))
      assert.equal(await page.locator('.memory-item').count(), 3)
      const row = page.locator('.memory-item').first()
      assert.equal((await row.locator('.memory-main-phrase, .memory-preview-summary').allTextContents()).join(''), text)
      assert.ok((await row.boundingBox()).height < 230, 'a long preview forms a text wall')
      await page.getByRole('navigation', { name: '学习与沟通记忆分页', exact: true }).getByRole('button', { name: '下一页', exact: true }).click()
      assert.equal(await formalButton(page).getAttribute('data-memory-id'), '4')
      await formalButton(page).click()
      assert.match(await page.locator('.memory-position').innerText(), /第 4 条.*15 条/)
      assert.equal(await page.locator('.memory-detail-value').innerText(), text)
      assert.equal(await page.locator('.memory-evidence-item:visible').count(), 0)
      const footer = await page.locator('.memory-detail-actions').boundingBox()
      assert.ok(footer.y + footer.height <= 844, 'actions disappear below the long body')
      await page.getByRole('tab', { name: /形成依据/ }).click()
      assert.equal(await page.locator('.memory-evidence-item:visible').count(), 3)
      const pages = page.getByRole('navigation', { name: '形成依据分页', exact: true })
      for (let i = 0; i < 6; i++) await pages.getByRole('button', { name: '下一页', exact: true }).click()
      assert.match(await pages.innerText(), /19–20.*20/)
      assert.equal(await page.locator('.memory-evidence-item:visible').count(), 2)
      await page.getByRole('button', { name: '下一条', exact: true }).click()
      assert.match(await page.locator('.memory-position').innerText(), /第 5 条/)
      assert.equal(await page.getByRole('tab', { name: '记忆内容', exact: true }).getAttribute('aria-selected'), 'true')
      await page.getByRole('tab', { name: /形成依据/ }).click()
      assert.match(await pages.innerText(), /1–3.*20/)
      await closeDetails(page)
      assert.equal(await page.locator('[data-memory-id="5"][data-memory-kind="formal"]').evaluate(el => el === document.activeElement), true)
      await page.getByRole('button', { name: /待确认的记忆/ }).click()
      assert.equal(await page.locator('.candidate-item').count(), 3)
      assert.ok((await page.locator('.candidate-item').first().boundingBox()).height < 230)
      await page.getByRole('navigation', { name: '沟通偏好候选分页', exact: true }).getByRole('button', { name: '下一页', exact: true }).click()
      assert.equal(await page.locator('.candidate-item button').first().getAttribute('data-memory-id'), '104')
    }, 390)
    for (const width of [320, 390, 768, 1200]) {
      await check(`separate details remain readable and keyboard-operable at ${width}px`, async page => {
        await page.evaluate(() => { window.memoryLayout.state.evidenceText[1] = 'LongIdentifier_'.repeat(40) })
        const button = formalButton(page)
        await button.focus(); await button.press('Enter')
        const panel = page.getByRole('region', { name: '运动 · 详情与依据', exact: true })
        await page.getByRole('tab', { name: /形成依据/ }).click()
        await panel.locator('.memory-evidence-text').waitFor()
        const drawerBounds = await page.locator('.n-drawer').boundingBox()
        assert.ok(drawerBounds.width <= width && drawerBounds.width >= Math.min(width, 640) - 2, 'drawer width does not fit the viewport')
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, 'horizontal overflow')
        assert.equal(await panel.evaluate(el => el.scrollWidth <= el.clientWidth), true, 'long evidence overflows the reading panel')
        const tooSmall = await page.locator('.candidate-toggle, .group-toggle, .memory-item button, #memory-details-return').evaluateAll(els => els.filter(el => {
          const r = el.getBoundingClientRect(); return r.width > 0 && (r.width < 44 || r.height < 44)
        }).map(el => el.textContent))
        assert.deepEqual(tooSmall, [])
        await page.locator('#memory-details-return').focus()
        await page.locator('#memory-details-return').press('Enter')
        await details(page).waitFor({ state: 'hidden' })
        await page.locator('.n-drawer-mask').waitFor({ state: 'hidden' })
        assert.equal(await button.evaluate(el => el === document.activeElement), true)
      }, width)
    }
  } finally { await browser.close(); await server.close() }
  console.log(`LAYOUT: ${passed} passed, ${failed} failed`)
  process.exitCode = failed ? 1 : 0
}
