import { createServer } from 'vite'
import { chromium } from 'playwright'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'
import assert from 'node:assert/strict'

// Real Vue/Pinia/router/ECharts/dialogs in Chromium; only the HTTP boundary is controlled.
// --baseline reads the original component from HEAD without changing workspace files.
const root = fileURLToPath(new URL('..', import.meta.url)).replace(/\\/g, '/').replace(/\/$/, '')
const baseline = process.argv.includes('--baseline')
const fixture = `
import { createApp, h, nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { NDialogProvider } from 'naive-ui'
import { getInstanceByDom } from 'echarts/core'
import MemoryGraphView from '/src/components/memory/MemoryGraphView.vue'
import { useConsolidationStore } from '/src/stores/consolidation.ts'
import { api } from '/src/api/core.ts'
import '/src/styles.css'
window.mount = async (options = {}) => {
  const state = window.state = { requests: [], getFail: false, deleteFail: false, updateFail: false, ...options }
  state.triples ??= Array.from({ length: 418 }, (_, i) => ({ id: i + 1,
    headEntity: i < 3 ? '共同起点' : '事件' + (i + 1), relation: i === 417 ? '独特关系' : '影响',
    tailEntity: '感受' + (i + 1), tailPolarity: i % 3 - 1,
    diaryId: i === 0 ? 7 : null, sourceDiaryIds: i === 0 ? '[7,8,"9",0,-1,1.5,null,true,"2e2","0x10",{},"abc"]' : '[]' }))
  api.defaults.adapter = async config => {
    state.requests.push({ url: config.url, method: config.method, data: config.data && JSON.parse(config.data) })
    let data = null
    if (config.url === '/graph/triples' && config.method === 'get') {
      if (state.getFail) throw new Error('fixture offline')
      data = structuredClone(state.triples)
    } else if (config.url === '/graph/user-graph') {
      data = { nodes: [{ name: '旧图谱', value: 1 }], edges: [] }
    } else if (/^\\/graph\\/triples\\/\\d+$/.test(config.url)) {
      if (config.method === 'delete') {
        if (state.holdDelete) await new Promise(resolve => { state.releaseDelete = resolve })
        if (state.deleteFail) throw new Error('fixture delete offline')
        state.triples = state.triples.filter(t => t.id !== Number(config.url.split('/').pop()))
      } else if (config.method === 'put') {
        if (state.holdUpdate) await new Promise(resolve => { state.releaseUpdate = resolve })
        if (state.updateFail) throw new Error('fixture update offline')
        const triple = state.triples.find(t => t.id === Number(config.url.split('/').pop()))
        Object.assign(triple, JSON.parse(config.data))
      }
    } else throw new Error('Unexpected request: ' + config.url)
    return { data: { code: 200, data }, status: 200, statusText: 'OK', headers: {}, config }
  }
  const pinia = createPinia(); setActivePinia(pinia)
  window.store = useConsolidationStore()
  window.router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', component: { render: () => null } },
    { path: '/diary/:id', name: 'diary-detail', component: { render: () => null } },
  ] })
  await router.push('/')
  window.app = createApp({ render: () => h(NDialogProvider, null, { default: () => h(MemoryGraphView) }) })
  app.use(pinia).use(router).mount('#app')
  await nextTick()
}
window.chart = () => getInstanceByDom(document.querySelector('.chart'))
window.fixtureReady = true
`
const server = await createServer({
  configFile: false, root, server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'memory-graph-fixture', enforce: 'pre',
    resolveId(id) { if (id === '/e2e/memory-graph-fixture.js') return id },
    load(id) {
      if (id === '/e2e/memory-graph-fixture.js') return fixture
      if (baseline && id.replace(/\\/g, '/') === `${root}/src/components/memory/MemoryGraphView.vue`) {
        return execFileSync('git', ['show', 'HEAD:frontend/src/components/memory/MemoryGraphView.vue'], { cwd: root, encoding: 'utf8' })
      }
    },
    configureServer(s) {
      s.middlewares.use('/__memory_graph', (_req, res) => {
        res.setHeader('Content-Type', 'text/html')
        res.end('<meta name="viewport" content="width=device-width, initial-scale=1"><div id="app"></div><script type="module" src="/e2e/memory-graph-fixture.js"></script>')
      })
    },
  }, vue()],
})
let browser, failed = 0, passed = 0
const count = (page, method) => page.evaluate(method => state.requests.filter(r => r.method === method).length, method)
async function check(name, run, options = {}) {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true })
  page.setDefaultTimeout(6000)
  const errors = []
  page.on('pageerror', e => { if (!e.message.startsWith('ResizeObserver loop')) errors.push(e.message) })
  try {
    await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/__memory_graph`)
    await page.waitForFunction(() => window.fixtureReady)
    await page.evaluate(options => mount(options), options)
    await run(page)
    assert.deepEqual(errors, [], 'uncaught browser errors')
    passed++; console.log(`PASS ${name}`)
  } catch (error) {
    failed++; console.log(`FAIL ${name}: ${error.message.split('\n')[0]}`)
  } finally { await page.close() }
}
const button = (page, name) => page.getByRole('button', { name, exact: true })
const list = async page => { await button(page, '切换列表视图').click(); await page.locator('.memory-item').first().waitFor() }
const stats = async (page, total, matched, shown) => {
  await page.waitForFunction(({ total, matched, shown }) => {
    const text = document.querySelector('.graph-counts')?.textContent || ''
    return text.includes('关系总数 ' + total) && text.includes('筛选匹配 ' + matched) && text.includes('当前显示 ' + shown)
  }, { total, matched, shown }, { timeout: 4000 })
}
try {
  await server.listen()
  browser = await chromium.launch({ headless: true })
  await check('418 relationships render only 20, with shared counts and explicit expansion', async page => {
    await stats(page, 418, 418, 20)
    await page.waitForFunction(() => window.chart()?.getOption().series[0].links.length === 20)
    assert.equal(await page.evaluate(() => state.requests.filter(r => r.url === '/graph/user-graph').length), 0)
    await list(page)
    assert.equal(await page.locator('.memory-item').count(), 20)
    assert.equal(await page.locator('.memory-item').first().getByRole('button').count(), 1, 'summary should have one detail entry')
    await button(page, '显示更多').click(); await stats(page, 418, 418, 40)
    assert.equal(await page.locator('.memory-item').count(), 40)
    await button(page, '展开全部').click(); await stats(page, 418, 418, 418)
    assert.equal(await page.locator('.memory-item').count(), 418)
    await button(page, '恢复有限显示').click(); await stats(page, 418, 418, 20)
    assert.equal(await page.locator('.memory-item').count(), 20)
  })
  await check('head/relation/tail search, polarity, and view switching share conditions', async page => {
    await stats(page, 418, 418, 20); await list(page)
    const search = page.getByRole('searchbox', { name: '搜索关系' })
    await search.fill('独特关系'); await stats(page, 418, 1, 1)
    assert.match(await page.locator('.memory-item').innerText(), /感受418/)
    await button(page, '切换图谱视图').click()
    await page.waitForFunction(() => window.chart()?.getOption().series[0].links.length === 1)
    await button(page, '切换列表视图').click()
    assert.equal(await search.inputValue(), '独特关系')
    await search.fill('共同起点'); await stats(page, 418, 3, 3)
    await page.getByLabel('感受极性', { exact: true }).selectOption('1'); await stats(page, 418, 1, 1)
    await page.getByLabel('感受极性', { exact: true }).selectOption('all')
    await search.fill('感受418'); await stats(page, 418, 1, 1)
    await search.fill('不存在'); await stats(page, 418, 0, 0)
    assert.match(await page.locator('.graph-empty').innerText(), /没有匹配/)
  })
  await check('real canvas node click selects its neighborhood and can be cleared in list view', async page => {
    await stats(page, 418, 418, 20)
    await page.waitForFunction(() => {
      const chart = window.chart(); if (!chart) return false
      const el = chart.getModel().getSeriesByIndex(0).getData().getItemGraphicEl(0)
      return el && el.getBoundingRect().width > 0
    })
    // Click the actual rendered symbol after the chart is visible in the mobile viewport.
    await page.locator('.chart').scrollIntoViewIfNeeded()
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
    const point = await page.evaluate(() => {
      const el = chart().getModel().getSeriesByIndex(0).getData().getItemGraphicEl(0)
      const [x, y] = el.transformCoordToGlobal(0, 0)
      const rect = document.querySelector('.chart').getBoundingClientRect()
      return { x: rect.left + x, y: rect.top + y }
    })
    await page.mouse.click(point.x, point.y)
    await stats(page, 418, 3, 3)
    assert.match(await page.locator('.graph-selection').innerText(), /共同起点/)
    await list(page); assert.equal(await page.locator('.memory-item').count(), 3)
    await button(page, '清除节点').click(); await stats(page, 418, 418, 20)
  })
  await check('details parse JSON diary sources, reject invalid IDs, and navigate via named route', async page => {
    await list(page)
    await page.locator('.memory-item').first().getByRole('button', { name: '查看详情', exact: true }).click()
    const sources = page.locator('.triple-sources a')
    assert.equal(await sources.count(), 3)
    assert.deepEqual(await sources.evaluateAll(els => els.map(el => el.getAttribute('href'))), ['/diary/7', '/diary/8', '/diary/9'])
    await sources.nth(1).click()
    assert.equal(await page.evaluate(() => router.currentRoute.value.name), 'diary-detail')
    assert.equal(await page.evaluate(() => router.currentRoute.value.params.id), '8')
  })
  await check('delete cancellation sends nothing; confirmation is explicit and single-flight', async page => {
    await list(page); await page.locator('.memory-item').first().getByRole('button', { name: '查看详情', exact: true }).click()
    await button(page, '删除此关系').click()
    const dialog = page.locator('.n-dialog')
    assert.match(await dialog.innerText(), /共同起点.*影响.*感受1/s)
    assert.match(await dialog.innerText(), /仅删除此关系/)
    assert.match(await dialog.innerText(), /不.*原日记/)
    assert.match(await dialog.innerText(), /无法撤销/)
    await page.waitForFunction(() => [...document.querySelectorAll('.n-dialog button')].every(el => {
      const rect = el.getBoundingClientRect(); return rect.width >= 44 && rect.height >= 44
    }), null, { timeout: 2000 })
    await dialog.getByRole('button', { name: '取消', exact: true }).click()
    assert.equal(await count(page, 'delete'), 0)
    await button(page, '删除此关系').click()
    await page.evaluate(() => { state.holdDelete = true })
    const confirmButton = await dialog.getByRole('button', { name: '确认删除', exact: true }).elementHandle()
    await confirmButton.click()
    await page.waitForFunction(() => state.releaseDelete)
    await confirmButton.evaluate(el => { el.click(); el.click() })
    assert.equal(await count(page, 'delete'), 1)
    await page.evaluate(() => state.releaseDelete())
    await stats(page, 417, 417, 20)
    await dialog.waitFor({ state: 'hidden' })
  })
  await check('delete failure stays in confirmation with local error and permits retry', async page => {
    await list(page); await page.locator('.memory-item').first().getByRole('button', { name: '查看详情', exact: true }).click()
    await button(page, '删除此关系').click()
    await page.evaluate(() => { state.deleteFail = true })
    const dialog = page.locator('.n-dialog')
    await dialog.getByRole('button', { name: '确认删除', exact: true }).click()
    await dialog.getByRole('alert').waitFor()
    assert.match(await dialog.getByRole('alert').innerText(), /删除失败/)
    await stats(page, 418, 418, 20)
    await page.evaluate(() => { state.deleteFail = false })
    await dialog.getByRole('button', { name: '确认删除', exact: true }).click()
    await stats(page, 417, 417, 20)
    assert.equal(await count(page, 'delete'), 2)
  })
  await check('edit fields are labeled, failed drafts stay editable, and save updates both views once', async page => {
    await list(page); await page.locator('.memory-item').first().getByRole('button', { name: '查看详情', exact: true }).click()
    await button(page, '编辑关系').click()
    await page.getByLabel('起点事件', { exact: true }).fill('修改后的起点')
    await page.getByLabel('关系描述', { exact: true }).fill('带来')
    await page.getByLabel('终点感受', { exact: true }).fill('新的感受')
    await page.getByLabel('编辑感受极性', { exact: true }).selectOption('1')
    await page.evaluate(() => { state.updateFail = true })
    await button(page, '保存关系').click()
    await page.locator('.triple-details [role="alert"]').waitFor()
    assert.equal(await page.getByLabel('起点事件', { exact: true }).inputValue(), '修改后的起点')
    await page.evaluate(() => { state.updateFail = false; state.holdUpdate = true })
    const saveButton = await button(page, '保存关系').elementHandle()
    await saveButton.click()
    await page.waitForFunction(() => state.releaseUpdate)
    await saveButton.evaluate(el => { el.click(); el.click() })
    assert.equal(await count(page, 'put'), 2)
    await page.evaluate(() => state.releaseUpdate())
    await page.waitForFunction(() => store.triples[0].headEntity === '修改后的起点')
    await button(page, '切换图谱视图').click()
    await page.waitForFunction(() => window.chart()?.getOption().series[0].data.some(n => n.name === '修改后的起点'))
    assert.equal(await page.evaluate(() => state.requests.filter(r => r.method === 'get').length), 1, 'edit should not fetch a separate graph')
  })
  await check('unsaved relationship drafts keep navigation locked until save or cancel', async page => {
    await list(page)
    const first = page.locator('.memory-item').first()
    await first.getByRole('button', { name: '查看详情', exact: true }).click()
    await button(page, '编辑关系').click()
    const editor = page.getByLabel('起点事件', { exact: true })
    await editor.fill('尚未保存的关系草稿')
    for (const control of [
      button(page, '收起详情'), button(page, '查看详情').first(),
      button(page, '刷新'), button(page, '智能整理图谱'), button(page, '切换图谱视图'),
      button(page, '显示更多'), button(page, '展开全部'),
      page.getByLabel('搜索关系', { exact: true }), page.getByLabel('感受极性', { exact: true }),
    ]) assert.equal(await control.isDisabled(), true, 'navigation can discard an unsaved draft')
    assert.equal(await editor.inputValue(), '尚未保存的关系草稿')
    assert.equal(await button(page, '保存关系').isEnabled(), true)
    await button(page, '取消编辑').click()
    assert.equal(await button(page, '收起详情').isEnabled(), true)
    assert.equal(await page.getByLabel('搜索关系', { exact: true }).isEnabled(), true)
    await button(page, '查看详情').first().click()
    assert.equal(await page.locator('.triple-editor').count(), 0)
  })
  await check('failed refresh preserves existing content and local retry recovers', async page => {
    await list(page)
    await page.evaluate(() => { state.getFail = true })
    await button(page, '刷新').click()
    await page.locator('.graph-load-error').waitFor()
    assert.match(await page.locator('.graph-load-error').innerText(), /保留/)
    await stats(page, 418, 418, 20)
    assert.equal(await page.locator('.memory-item').count(), 20)
    await page.evaluate(() => { state.getFail = false })
    await button(page, '重试加载').click()
    await page.locator('.graph-load-error').waitFor({ state: 'hidden' })
    await stats(page, 418, 418, 20)
  })
  await check('first load failure is distinct from a successful empty result', async page => {
    await page.locator('.graph-load-error').waitFor()
    assert.match(await page.locator('.graph-load-error').innerText(), /加载失败/)
    assert.equal(await page.locator('.graph-empty').count(), 0)
    await page.evaluate(() => { state.getFail = false; state.triples = [] })
    await button(page, '重试加载').click()
    await page.locator('.graph-empty').waitFor()
    assert.match(await page.locator('.graph-empty').innerText(), /暂无关系/)
    await stats(page, 0, 0, 0)
  }, { getFail: true })
  await check('store consolidation changes update graph and list; safe tooltip and mobile keyboard controls', async page => {
    await stats(page, 418, 418, 20)
    await page.evaluate(() => { store.triples = [{ id: 900, headEntity: '<img src=x onerror=alert(1)>', relation: '导致', tailEntity: '整理结果', tailPolarity: 0, diaryId: -1, sourceDiaryIds: 'bad JSON' }] })
    await stats(page, 1, 1, 1)
    await page.waitForFunction(() => window.chart()?.getOption().series[0].links.length === 1)
    assert.equal(await page.evaluate(() => chart().getOption().tooltip[0].renderMode), 'richText')
    assert.equal(await page.evaluate(() => chart().getOption().series[0].labelLayout.hideOverlap), true)
    await button(page, '切换列表视图').focus(); await page.keyboard.press('Enter')
    await page.locator('.memory-item').waitFor()
    const detail = page.locator('.memory-item').getByRole('button', { name: '查看详情', exact: true })
    await detail.focus(); await page.keyboard.press('Enter')
    await page.locator('.triple-details').waitFor()
    assert.equal(await page.locator('.memory-item img').count(), 0)
    assert.equal(await page.locator('.triple-sources a').count(), 0)
    assert.match(await page.locator('.triple-sources').innerText(), /暂无可用/)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, 'mobile horizontal overflow')
    const tooSmall = await page.locator('.memory-graph button, .memory-graph input, .memory-graph select').evaluateAll(els => els.filter(el => {
      const r = el.getBoundingClientRect(); return r.width > 0 && (r.width < 44 || r.height < 44)
    }).map(el => el.textContent || el.getAttribute('aria-label')))
    assert.deepEqual(tooSmall, [], 'touch targets smaller than 44px')
  })
} finally { await browser?.close(); await server.close() }
console.log(`${baseline ? 'BASELINE' : 'CURRENT'}: ${passed} passed, ${failed} failed`)
process.exitCode = failed ? 1 : 0
