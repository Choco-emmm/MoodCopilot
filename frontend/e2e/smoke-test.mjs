import { chromium } from 'playwright';

const BASE_URL = process.env.E2E_BASE_URL || 'http://127.0.0.1:4173';
const API_URL = process.env.E2E_API_URL || 'http://127.0.0.1:18080/api';
const EMAIL = process.env.E2E_EMAIL || 'test@test.com';
const PASSWORD = process.env.E2E_PASSWORD || '123456';

let passed = 0;
let failed = 0;

async function check(label, fn) {
  try {
    await fn();
    console.log(`  OK ${label}`);
    passed++;
  } catch (error) {
    console.log(`  FAIL ${label}: ${String(error.message || error).slice(0, 180)}`);
    failed++;
  }
}

async function expectHttpOk(url, label) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${label} failed: ${response.status} ${await response.text()}`);
  }
  return response;
}

async function login(page) {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
  await page.locator('input').nth(0).fill(EMAIL);
  await page.locator('input').nth(1).fill(PASSWORD);
  await page.locator('button').first().click();
  await page.waitForURL(`${BASE_URL}/`, { timeout: 15000 });
}

async function firstPublicDiaryId(page) {
  const token = await page.evaluate(() => localStorage.getItem('token'));
  const result = await page.evaluate(async ({ apiUrl, token }) => {
    const response = await fetch(`${apiUrl}/diaries/public?page=1&size=5`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!response.ok) return { status: response.status, id: null };
    const body = await response.json();
    const data = body.data;
    const items = data?.items ?? data?.records ?? data ?? [];
    return { status: response.status, id: items[0]?.id ?? null };
  }, { apiUrl: API_URL, token });
  if (result.status < 200 || result.status >= 300) {
    throw new Error(`public diaries failed: ${result.status}`);
  }
  return result.id;
}

async function main() {
  console.log('\n=== MoodCopilot App smoke ===\n');
  await check('API health 可用', async () => {
    await expectHttpOk(`${API_URL}/health`, 'health');
  });

  const browser = await chromium.launch({ headless: true });
  const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true });
  const consoleErrors = [];
  const requestFailures = [];
  mobile.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  mobile.on('requestfailed', (request) => {
    const errorText = request.failure()?.errorText || '';
    if (errorText.includes('net::ERR_ABORTED')) return;
    requestFailures.push(`${request.url()} ${errorText}`);
  });

  try {
    await check('登录页可打开', async () => {
      await mobile.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
      await mobile.locator('.auth-card').waitFor({ timeout: 8000 });
    });

    await check('测试账号可登录并进入广场', async () => {
      await login(mobile);
      await mobile.locator('.app-shell').waitFor({ timeout: 10000 });
    });

    await check('广场公开流可加载', async () => {
      await mobile.goto(`${BASE_URL}/`, { waitUntil: 'domcontentloaded' });
      await mobile.locator('.feed-item').first().waitFor({ timeout: 12000 });
    });

    await check('写日记页可访问', async () => {
      await mobile.goto(`${BASE_URL}/write`, { waitUntil: 'domcontentloaded' });
      await mobile.locator('textarea').first().waitFor({ timeout: 10000 });
    });

    await check('报告页可访问', async () => {
      await mobile.goto(`${BASE_URL}/report`, { waitUntil: 'domcontentloaded' });
      await mobile.locator('.report-page').waitFor({ timeout: 12000 });
    });

    await check('AI 聊天页可访问', async () => {
      await mobile.goto(`${BASE_URL}/chat`, { waitUntil: 'domcontentloaded' });
      await mobile.locator('.chat-messages').waitFor({ timeout: 10000 });
      await mobile.locator('.chat-input-row').waitFor({ timeout: 10000 });
    });

    await check('设置页可访问', async () => {
      await mobile.goto(`${BASE_URL}/settings`, { waitUntil: 'domcontentloaded' });
      await mobile.locator('.avatar-upload').waitFor({ timeout: 10000 });
    });

    await check('日记详情页可访问', async () => {
      const id = await firstPublicDiaryId(mobile);
      if (!id) {
        console.log('  SKIP 没有公开日记，跳过详情页');
        return;
      }
      await mobile.goto(`${BASE_URL}/diary/${id}`, { waitUntil: 'domcontentloaded' });
      await mobile.locator('.diary-detail-page').waitFor({ timeout: 10000 });
    });

    await check('桌面端顶栏可访问', async () => {
      const desktop = await browser.newPage({ viewport: { width: 1280, height: 800 } });
      await login(desktop);
      await desktop.locator('.masthead').waitFor({ timeout: 10000 });
      await desktop.close();
    });

    await check('页面没有控制台错误或失败请求', async () => {
      if (consoleErrors.length || requestFailures.length) {
        throw new Error(JSON.stringify({ consoleErrors, requestFailures }, null, 2));
      }
    });
  } finally {
    await browser.close();
  }

  console.log(`\n=== Result: ${passed} passed, ${failed} failed ===\n`);
  process.exit(failed > 0 ? 1 : 0);
}

main().catch((error) => {
  console.error('Smoke test crashed:', error);
  process.exit(1);
});
