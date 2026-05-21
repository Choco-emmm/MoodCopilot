const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1'
  });
  const page = await context.newPage();
  try {
    const loginResp = await page.request.post('http://localhost:5173/api/auth/login', {
      data: { email: 'test@test.com', password: '123456', turnstileToken: '' }
    });
    const loginData = await loginResp.json();
    const token = loginData.data.token;
    await page.goto('http://localhost:5173/login');
    await page.evaluate((t) => { localStorage.setItem('token', t); }, token);
    await page.goto('http://localhost:5173/report');
    await page.waitForTimeout(6000);
    await page.screenshot({ path: 'report_screenshot.png', fullPage: true });
  } catch (err) {
  }
  await browser.close();
})();
