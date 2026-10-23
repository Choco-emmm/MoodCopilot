// Node built-ins only. Closed-loop, fixed concurrency, no auth, writes or AI calls.
import http from 'node:http';
import { performance } from 'node:perf_hooks';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import os from 'node:os';
import { gzipSync } from 'node:zlib';

const base = new URL(process.env.PERF_BASE_URL || 'http://127.0.0.1:18080');
if (!['localhost', '127.0.0.1', '[::1]'].includes(base.hostname) || base.protocol !== 'http:') {
  throw new Error('This harness only permits local HTTP services.');
}
const output = resolve(process.env.PERF_OUTPUT || 'docs/verification/2026-10-21-agent-performance');
await mkdir(output, { recursive: true });
const durationMs = 20_000;
// A real loopback source address, not a forged proxy header. It isolates the limiter
// from browser requests; wait 70s before repeating the complete experiment.
const localAddress = '127.0.0.2';
const phases = [
  { name: 'health-c8', path: '/api/health', concurrency: 8 },
  { name: 'health-c32', path: '/api/health', concurrency: 32 },
  { name: 'announcements-c8', path: '/api/announcements/active', concurrency: 8 },
];
const quantile = values => values.length ? [...values].sort((a, b) => a - b)[Math.ceil(values.length * .95) - 1] : null;
async function run(phase, measured = true) {
  const agent = new http.Agent({ keepAlive: true, maxSockets: phase.concurrency });
  const samples = [];
  const begin = performance.now();
  const deadline = begin + (measured ? durationMs : 5000);
  async function worker() {
    while (performance.now() < deadline) {
      const start = performance.now();
      const result = await new Promise(resolve => {
        const req = http.get(new URL(phase.path, base), { agent, localAddress, family: 4 }, res => {
          let body = '';
          res.setEncoding('utf8');
          res.on('data', chunk => { if (body.length < 65536) body += chunk; });
          res.on('end', () => {
            let valid = false;
            try {
              const value = JSON.parse(body);
              valid = value.code === 0 && (phase.path !== '/api/health' || value.data?.status === 'UP');
            } catch {}
            resolve({ status: res.statusCode, ok: res.statusCode === 200 && valid });
          });
          res.on('error', () => resolve({ status: 0, ok: false }));
        });
        req.setTimeout(5000, () => req.destroy(new Error('timeout')));
        req.on('error', () => resolve({ status: 0, ok: false }));
      });
      samples.push({ offsetMs: +(start - begin).toFixed(3), latencyMs: +(performance.now() - start).toFixed(3), ...result });
    }
  }
  await Promise.all(Array.from({ length: phase.concurrency }, worker));
  const elapsedSeconds = (performance.now() - begin) / 1000;
  agent.destroy();
  const successes = samples.filter(s => s.ok);
  const statusCounts = {};
  for (const sample of samples) statusCounts[sample.status] = (statusCounts[sample.status] || 0) + 1;
  const summary = {
    ...phase, scheduledSeconds: measured ? 20 : 5, elapsedSeconds, requests: samples.length,
    successes: successes.length, throughputRps: samples.length / elapsedSeconds,
    successfulRps: successes.length / elapsedSeconds, p95Ms: quantile(samples.map(s => s.latencyMs)),
    successP95Ms: quantile(successes.map(s => s.latencyMs)), errorRate: 1 - successes.length / samples.length,
    statusCounts,
  };
  if (measured) {
    await writeFile(resolve(output, `${phase.name}.ndjson.gz`), gzipSync(samples.map(s => JSON.stringify(s)).join('\n') + '\n'));
    await writeFile(resolve(output, `${phase.name}.json`), JSON.stringify(summary, null, 2) + '\n');
    console.log(JSON.stringify(summary));
  }
  return summary;
}
// Warm only the exempt health endpoint, preserving the business endpoint's rate-limit window.
await run(phases[0], false);
const metadata = {
  startedAt: new Date().toISOString(), base: base.origin, localAddress, node: process.version,
  platform: `${os.platform()} ${os.release()} ${os.arch()}`, cpu: os.cpus()[0].model,
  logicalCpus: os.cpus().length, totalMemoryBytes: os.totalmem(),
  workload: 'GET only; HTTP/1.1 keep-alive; closed loop; 5s socket inactivity timeout; no retries or think time',
};
const results = [];
for (const phase of phases) results.push(await run(phase));
await writeFile(resolve(output, 'load-summary.json'), JSON.stringify({ ...metadata, results }, null, 2) + '\n');
// Health after the load; fail visibly if the service no longer responds normally.
const postcheck = await run({ name: 'postcheck', path: '/api/health', concurrency: 1 }, false);
if (postcheck.errorRate !== 0) process.exitCode = 1;
