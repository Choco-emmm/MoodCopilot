import { chromium } from 'playwright';

const BASE_URL = process.env.E2E_BASE_URL || 'http://127.0.0.1:4173';
const API_BASE = process.env.E2E_API_BASE || '/api';
const stamp = Date.now();
const account = {
  displayName: `visual${stamp}`,
  email: `visual-${stamp}@example.com`,
  password: 'codex123456',
};

async function main() {
  const browser = await chromium.launch({ headless: true });
  const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true });
  const consoleErrors = [];
  const pageErrors = [];

  mobile.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  mobile.on('pageerror', (error) => pageErrors.push(error.message));

  try {
    await register(mobile);
    await verifyDraftRestore(mobile);
    await createPublicDiary(mobile, `visual public diary ${stamp}`);
    await verifySquareClean(mobile, `visual public diary ${stamp}`);
    await verifyChatMobile(mobile);
    await verifyDesktopSquare(browser, `visual public diary ${stamp}`);

    if (consoleErrors.length || pageErrors.length) {
      throw new Error(JSON.stringify({ consoleErrors, pageErrors }, null, 2));
    }
    console.log("Visual Polish E2E smoke tests passed!");
  } finally {
    await browser.close();
  }
}

async function register(page) {
  await page.goto(`${BASE_URL}/register`, { waitUntil: 'networkidle' });
  await page.locator('input').nth(0).fill(account.displayName);
  await page.locator('input').nth(1).fill(account.email);
  await page.locator('input').nth(2).fill(account.password);
  await page.locator('button').first().click();
  await page.waitForURL(`${BASE_URL}/`, { timeout: 10000 });
}

async function verifyDraftRestore(page) {
  const draft = `visual draft ${stamp}`;
  await page.goto(`${BASE_URL}/write`, { waitUntil: 'networkidle' });
  await page.locator('textarea').first().fill(draft);
  await page.locator('.draft-notice').waitFor({ state: 'visible', timeout: 5000 });
  await page.reload({ waitUntil: 'networkidle' });
  const restored = await page.locator('textarea').first().inputValue();
  if (restored !== draft) throw new Error(`draft restore mismatch: ${restored}`);
  await page.screenshot({ path: 'frontend/test-results/visual-mobile-write.png', fullPage: true });
}

async function createPublicDiary(page, content) {
  const token = await page.evaluate(() => localStorage.getItem('token'));
  const status = await page.evaluate(async ({ token, content, apiBase }) => {
    const response = await fetch(`${apiBase}/diaries`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ content, visibility: 'PUBLIC' }),
    });
    return response.status;
  }, { token, content, apiBase: API_BASE });
  if (status < 200 || status >= 300) throw new Error(`create diary failed: ${status}`);
}

async function verifySquareClean(page, content) {
  await page.goto(`${BASE_URL}/`, { waitUntil: 'networkidle' });
  await page.getByText(content).waitFor({ timeout: 10000 });
  const body = await page.locator('body').innerText();
  const forbidden = ['鼓励', '看分析', '本周话题', '本月话题', '主题标签', '分类标签'];
  const found = forbidden.filter((text) => body.includes(text));
  if (found.length) throw new Error(`forbidden square text: ${found.join(', ')}`);
  await page.screenshot({ path: 'frontend/test-results/visual-mobile-square.png', fullPage: true });
}

async function verifyChatMobile(page) {
  await page.goto(`${BASE_URL}/chat`, { waitUntil: 'networkidle' });
  await page.getByText('MoodCopilot').first().waitFor({ timeout: 10000 });
  const inputVisible = await page.locator('.chat-input-row').isVisible();
  if (!inputVisible) throw new Error('chat input row is not visible on mobile');
  await page.screenshot({ path: 'frontend/test-results/visual-mobile-chat.png', fullPage: true });
}

async function verifyDesktopSquare(browser, content) {
  const desktop = await browser.newPage({ viewport: { width: 1280, height: 820 } });
  await desktop.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
  await desktop.locator('input').nth(0).fill(account.email);
  await desktop.locator('input').nth(1).fill(account.password);
  await desktop.locator('button').first().click();
  await desktop.waitForURL(`${BASE_URL}/`, { timeout: 10000 });
  await desktop.getByText(content).waitFor({ timeout: 10000 });
  await desktop.screenshot({ path: 'frontend/test-results/visual-desktop-square.png', fullPage: true });
  await desktop.close();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
