import { readFileSync, writeFileSync, statSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'
import { gunzipSync } from 'node:zlib'
import { execFileSync } from 'node:child_process'
import { request } from 'node:http'
import os from 'node:os'
import assert from 'node:assert/strict'

// Run from any directory: node docs/interview/verify-evidence.mjs
// Reads local reports and historical samples; only sends two local GET probes.
// Does not call AI, authenticate, or create/modify application data.
const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '../..')
const output = resolve(here, 'evidence/2026-10-28')
const historical = resolve(root, 'docs/verification/2026-10-21-agent-performance')
const readJson = path => JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, ''))
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex')
const suiteNames = [
  'ChatAgentResilienceTest', 'ChatAgentLoopTest', 'ChatAgentApprovalTest',
  'ChatGenerationServiceTest', 'ChatHistoryMergeTest', 'ChatHistoryStoreTest',
  'ChatModelProfilesTest', 'ChatToolRegistryTest', 'ContextPlannerTest',
  'PromptComposerTest', 'XmlPromptRendererTest', 'AiTaskProducerTest',
  'MemoryWriteContractTest',
]
const attrs = tag => Object.fromEntries([...tag.matchAll(/([\w:.-]+)="([^"]*)"/g)]
  .map(match => [match[1], match[2]]))
function parseSuites(path) {
  return [...readFileSync(path, 'utf8').matchAll(/<testsuite\b[^>]*>/g)].map(match => {
    const a = attrs(match[0])
    return { name: a.name, tests: Number(a.tests), failures: Number(a.failures),
      errors: Number(a.errors), skipped: Number(a.skipped), seconds: Number(a.time) }
  })
}
const run = readJson(resolve(output, 'test-run.json'))
assert.equal(run.exitCode, 0, 'The targeted Maven run must succeed')
const reports = suiteNames.map(name => {
  const pkg = name === 'ChatToolRegistryTest' ? 'ai.tool'
    : name === 'AiTaskProducerTest' ? 'ai.mq' : 'ai'
  const path = resolve(root, `backend/moodcopilot/target/surefire-reports/TEST-com.moodcopilot.${pkg}.${name}.xml`)
  const bytes = readFileSync(path)
  const [suite] = parseSuites(path)
  assert.ok(statSync(path).mtimeMs >= Date.parse(run.startedAt) - 2000, `${name}: stale report`)
  assert.ok(statSync(path).mtimeMs <= Date.parse(run.finishedAt) + 2000, `${name}: later unrelated report`)
  assert.equal(suite.failures + suite.errors + suite.skipped, 0, `${name}: did not fully pass`)
  const cases = [...bytes.toString('utf8').matchAll(/<testcase\b[^>]*>/g)].map(match => {
    const a = attrs(match[0])
    return { name: a.name, classname: a.classname, seconds: Number(a.time) }
  })
  assert.equal(cases.length, suite.tests)
  return { ...suite, sha256: sha256(bytes), cases }
})
const historicalSummary = readJson(resolve(historical, 'load-summary.json'))
const previousIntegrity = readJson(resolve(historical, 'integrity.json'))
const loads = historicalSummary.results.map(expected => {
  const file = `${expected.name}.ndjson.gz`
  const compressed = readFileSync(resolve(historical, file))
  const samples = gunzipSync(compressed).toString('utf8').trim().split(/\r?\n/).map(JSON.parse)
  const hash = sha256(compressed)
  assert.equal(hash, previousIntegrity.find(item => item.file === file).sha256)
  const successful = samples.filter(sample => sample.ok)
  const counts = {}
  for (const sample of samples) counts[String(sample.status)] = (counts[String(sample.status)] ?? 0) + 1
  const p95 = values => values.sort((a, b) => a - b)[Math.ceil(values.length * 0.95) - 1]
  const measured = { file, sha256: hash, samples: samples.length, successes: successful.length,
    statusCounts: counts, p95Ms: p95(samples.map(sample => sample.latencyMs)),
    successP95Ms: p95(successful.map(sample => sample.latencyMs)) }
  assert.equal(measured.samples, expected.requests)
  assert.equal(measured.successes, expected.successes)
  assert.deepEqual(measured.statusCounts, expected.statusCounts)
  assert.equal(measured.p95Ms, expected.p95Ms)
  assert.equal(measured.successP95Ms, expected.successP95Ms)
  assert.ok(Math.abs(expected.throughputRps - samples.length / expected.elapsedSeconds) < 1e-8)
  return { ...measured, historicalDate: '2026-10-21', concurrency: expected.concurrency,
    elapsedSeconds: expected.elapsedSeconds, successfulRps: expected.successfulRps,
    verifiedAgainstSummary: true }
})
const oldTests = ['before-fix.xml', 'after-fix.xml'].map(file => ({
  file, suites: parseSuites(resolve(historical, file)),
}))
function probe(url) {
  return new Promise(resolveProbe => {
    const req = request(url, { method: 'GET' }, res => {
      res.resume()
      res.on('end', () => resolveProbe({ url, reachable: true, status: res.statusCode }))
    })
    req.setTimeout(3000, () => req.destroy(Object.assign(new Error('timeout'), { code: 'TIMEOUT' })))
    req.on('error', error => resolveProbe({ url, reachable: false, errorCode: error.code ?? 'ERROR' }))
    req.end()
  })
}
const localProbes = []
for (const url of ['http://127.0.0.1:18080/api/health', 'http://127.0.0.1:5173']) {
  localProbes.push(await probe(url))
}
const totals = reports.reduce((sum, suite) => {
  for (const key of ['tests', 'failures', 'errors', 'skipped']) sum[key] += suite[key]
  return sum
}, { tests: 0, failures: 0, errors: 0, skipped: 0 })
const result = {
  generatedAt: new Date().toISOString(), reportingTimeZone: 'Asia/Shanghai',
  sourceCommit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(),
  environment: { platform: `${os.platform()} ${os.release()}`, cpu: os.cpus()[0].model,
    logicalCpus: os.cpus().length, node: process.version,
    java: 'Oracle JDK 25.0.1; project target release 21', maven: '3.9.12' },
  currentRegression: { ...run, totals, suites: reports },
  historicalLoadRecalculation: loads, historicalFaultTests: oldTests, localProbes,
  limits: [
    'Current regression uses mock external model/tool/Redis boundaries; not a live provider benchmark.',
    'Historical load was measured on 2026-10-21, not rerun today; raw samples recalculated today.',
    'Health endpoint throughput is not AI or database business throughput.',
    'UI demo script has not been rehearsed against a running backend in this session.',
  ],
}
writeFileSync(resolve(output, 'evidence-summary.json'), JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify({ sourceCommit: result.sourceCommit, totals,
  verifiedHistoricalSamples: loads.reduce((sum, group) => sum + group.samples, 0), localProbes }, null, 2))
