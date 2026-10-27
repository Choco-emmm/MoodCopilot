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
  deferEvidence(id) { state.deferred[id] = new Promise(resolve => { state.releases[id] = resolve }) },
  releaseEvidence(id, text) { state.releases[id]([{ id, sourceType: 'chat_candidate', evidenceText: text }]) },
}
api.defaults.adapter = async config => {
  state.requests.push({ url: config.url, method: config.method })
  let data = []
  if (config.url === '/memory') {
    if (state.formalFail) throw new Error('fixture formal refresh offline')
    data = memories
  }
  else if (config.url === '/memory/candidates') data = candidates
  else if (/^\\/memory\\/\\d+\\/evidence$/.test(config.url)) {
    const id = Number(config.url.split('/')[2])
    if (state.deferred[id]) { const pending = state.deferred[id]; delete state.deferred[id]; data = await pending }
    else data = [{ id, sourceType: 'chat_candidate', evidenceText: state.evidenceText[id] || '这是第' + id + '条记忆的原始依据。' }]
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
    try {
      await page.goto(url)
      await page.waitForFunction(() => window.memoryLayoutReady && document.querySelector('.memory-overview')?.textContent.includes('4 条正式记忆'))
      await test(page)
      passed++; console.log(`PASS ${name}`)
    } catch (error) { failed++; console.log(`FAIL ${name}: ${error.message}`) }
    finally { await page.close() }
  }
  try {
    await check('collapsed candidate summary exposes counts and conflicts without opening the queue', async page => {
      const toggle = page.getByRole('button', { name: /待确认的记忆/ })
      const text = await toggle.innerText()
      assert.match(text, /3\s*条.*2\s*个属性/s)
      assert.match(text, /1\s*个属性.*候选/s)
      assert.equal(await toggle.getAttribute('aria-expanded'), 'false')
      assert.equal(await page.locator('.candidate-item').count(), 0)
      assert.equal(await page.locator('.memory-item').count(), 3)
    })
    await check('formal details switch between records and groups without leaving other panels open', async page => {
      const rows = page.locator('.memory-item')
      const toggle = index => rows.nth(index).getByRole('button', { name: /详情/ }).first()
      await toggle(0).click()
      await page.locator('#memory-detail-1').waitFor()
      await toggle(1).click()
      await page.locator('#memory-detail-2').waitFor()
      assert.equal(await page.locator('.memory-details').count(), 1)
      assert.equal(await toggle(0).getAttribute('aria-expanded'), 'false')
      assert.equal(await toggle(1).getAttribute('aria-expanded'), 'true')
      await toggle(2).click()
      await page.locator('#memory-detail-3').waitFor()
      assert.equal(await page.locator('.memory-details').count(), 1)
      assert.equal(await toggle(1).getAttribute('aria-expanded'), 'false')
      await toggle(2).click()
      assert.equal(await page.locator('.memory-details').count(), 0)
      assert.equal(await toggle(2).evaluate(el => el === document.activeElement), true)
    })
    await check('details identify the selected attribute, span the row and contain maintenance actions', async page => {
      const row = page.locator('.memory-item').first()
      await row.getByRole('button', { name: '查看详情与依据', exact: true }).click()
      const panel = page.getByRole('region', { name: '运动 · 详情与依据', exact: true })
      assert.equal(await panel.count(), 1, 'selected attribute has no named detail region')
      const bounds = await panel.boundingBox(), rowBounds = await row.boundingBox()
      assert.ok(bounds.width >= rowBounds.width - 2, 'details are squeezed into a summary column')
      const edit = panel.getByRole('button', { name: '编辑', exact: true })
      assert.equal(await edit.count(), 1, 'edit action is outside details')
      assert.equal(await panel.getByRole('button', { name: '删除', exact: true }).count(), 1)
      await edit.click()
      assert.equal(await panel.getByRole('textbox', { name: '编辑记忆内容' }).count(), 1, 'editor is outside details')
    })
    await check('an unsaved draft remains visible until save or cancel before switching or collapsing', async page => {
      const rows = page.locator('.memory-item')
      await rows.first().getByRole('button', { name: '查看详情与依据', exact: true }).click()
      const panel = page.locator('#memory-detail-1')
      await panel.getByRole('button', { name: '编辑', exact: true }).click()
      const editor = panel.getByRole('textbox', { name: '编辑记忆内容' })
      await editor.fill('还没有保存的草稿')
      const groupToggle = page.getByRole('button', { name: /运动.*2 条/ })
      assert.equal(await groupToggle.isDisabled(), true, 'group can hide an unsaved draft')
      assert.equal(await rows.nth(1).getByRole('button', { name: '查看详情与依据', exact: true }).isDisabled(), true)
      assert.equal(await page.getByRole('button', { name: '刷新', exact: true }).isDisabled(), true, 'refresh can replace an unsaved draft')
      assert.equal(await editor.inputValue(), '还没有保存的草稿')
      await panel.getByRole('button', { name: '取消', exact: true }).click()
      assert.equal(await groupToggle.isDisabled(), false)
      await groupToggle.click()
      await groupToggle.click()
      assert.equal(await page.locator('.memory-details').count(), 0, 'reopening a group unexpectedly reopens old details')
    })
    await check('formal refresh retry cannot hide an unsaved draft after a load failure', async page => {
      await page.evaluate(() => { window.memoryLayout.state.formalFail = true })
      await page.getByRole('button', { name: '刷新', exact: true }).click()
      const retry = page.getByRole('button', { name: '重试正式记忆', exact: true })
      await retry.waitFor()
      await page.locator('.memory-item').first().getByRole('button', { name: '查看详情与依据', exact: true }).click()
      await page.getByRole('button', { name: '编辑', exact: true }).click()
      const editor = page.getByRole('textbox', { name: '编辑记忆内容' })
      await editor.fill('失败后的未保存草稿')
      assert.equal(await retry.isDisabled(), true, 'retry can hide the editor and leave navigation locked')
      assert.equal(await editor.inputValue(), '失败后的未保存草稿')
      await page.getByRole('button', { name: '取消', exact: true }).click()
      assert.equal(await retry.isEnabled(), true)
      await page.evaluate(() => { window.memoryLayout.state.formalFail = false })
      await retry.click()
      await retry.waitFor({ state: 'hidden' })
      assert.equal(await page.locator('.memory-details').count(), 0)
    })
    await check('refresh invalidates pending detail responses without replacing fresh evidence', async page => {
      await page.evaluate(() => window.memoryLayout.deferEvidence(1))
      const toggle = page.locator('.memory-item').first().getByRole('button', { name: /详情/ }).first()
      await toggle.click()
      await page.waitForFunction(() => window.memoryLayout.state.requests.some(r => r.url === '/memory/1/evidence'))
      await page.evaluate(() => { window.memoryLayout.state.evidenceText[1] = '刷新后的原始依据' })
      await page.getByRole('button', { name: '刷新', exact: true }).click()
      await page.locator('.memory-details').waitFor({ state: 'hidden' })
      await toggle.click()
      await page.getByText('刷新后的原始依据', { exact: true }).waitFor()
      await page.evaluate(() => window.memoryLayout.releaseEvidence(1, '过期请求中的旧依据'))
      await page.waitForFunction(() => window.memoryLayout.state.completedEvidence >= 2)
      const text = await page.locator('.memory-details').innerText()
      assert.ok(text.includes('刷新后的原始依据') && !text.includes('过期请求中的旧依据'), 'old response repopulated a refreshed detail cache')
    })
    await check('candidate and formal details share one reading focus', async page => {
      await page.getByRole('button', { name: /待确认的记忆/ }).click()
      const candidate = page.locator('.candidate-item').first().getByRole('button', { name: '查看详情与依据', exact: true })
      const formal = page.locator('.memory-item').first().getByRole('button', { name: /详情/ }).first()
      await candidate.click()
      await page.locator('.candidate-details').waitFor()
      await formal.click()
      assert.equal(await page.locator('.candidate-details').count(), 0)
      assert.equal(await page.locator('.memory-details').count(), 1)
      await candidate.click()
      assert.equal(await page.locator('.memory-details').count(), 0)
      assert.equal(await page.locator('.candidate-details').count(), 1)
    })
    for (const width of [390, 768]) {
      await check(`full-width detail remains readable and keyboard-operable at ${width}px`, async page => {
        await page.evaluate(() => { window.memoryLayout.state.evidenceText[1] = 'LongIdentifier_'.repeat(40) })
        const row = page.locator('.memory-item').first()
        const toggle = row.getByRole('button', { name: /详情/ }).first()
        await toggle.focus(); await toggle.press('Enter')
        const panel = page.getByRole('region', { name: '运动 · 详情与依据', exact: true })
        await panel.locator('.memory-evidence-text').waitFor()
        const bounds = await panel.boundingBox(), rowBounds = await row.boundingBox()
        assert.ok(bounds.width >= rowBounds.width - 2)
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, 'horizontal overflow')
        const tooSmall = await page.locator('.candidate-toggle, .group-toggle, .memory-item button').evaluateAll(els => els.filter(el => {
          const r = el.getBoundingClientRect(); return r.width > 0 && (r.width < 44 || r.height < 44)
        }).map(el => el.textContent))
        assert.deepEqual(tooSmall, [])
        await toggle.press('Enter')
        assert.equal(await page.locator('.memory-details').count(), 0)
        assert.equal(await toggle.evaluate(el => el === document.activeElement), true)
      }, width)
    }
  } finally { await browser.close(); await server.close() }
  console.log(`LAYOUT: ${passed} passed, ${failed} failed`)
  process.exitCode = failed ? 1 : 0
}
