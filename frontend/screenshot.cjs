const { chromium } = require('playwright');
const fs = require('fs');

(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 }, // Mobile viewport
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1'
  });
  const page = await context.newPage();

  try {
    console.log('Logging in via API...');
    const loginResp = await page.request.post('http://localhost:5173/api/auth/login', {
      data: {
        email: 'test@test.com',
        password: '123456',
        turnstileToken: ''
      }
    });

    const loginData = await loginResp.json();
    if (!loginData.data || !loginData.data.token) {
      console.log('Login failed', loginData);
      await browser.close();
      return;
    }

    console.log('Login success via API');
    const token = loginData.data.token;
    
    // Go to a simple page first to set localStorage
    await page.goto('http://localhost:5173/login');
    await page.evaluate((t) => {
      localStorage.setItem('token', t);
    }, token);

    console.log('Navigating to /write...');
    await page.goto('http://localhost:5173/write');
    await page.waitForTimeout(3000);
    
    const isWrite = await page.locator('.vditor-ir').isVisible();
    if (!isWrite) {
        console.log('Not on write page');
        await page.screenshot({ path: 'error_screenshot.png', fullPage: true });
        await browser.close();
        return;
    }

    console.log('Writing diary...');
    await page.locator('.vditor-ir').click();
    await page.waitForTimeout(500);
    
    const emoText = "凌晨三点了，还是睡不着。\n最近感觉很焦虑，什么事都做不好。室友都已经熟睡了，只有我一个人对着天花板发呆。感觉自己一无是处，是不是我真的不适合现在的生活？好累。";
    await page.keyboard.type(emoText, { delay: 50 });
    
    await page.waitForTimeout(1000);
    console.log('Clicking save...');
    await page.locator('button:has-text("保存并分析")').click();
    
    console.log('Waiting for analysis...');
    await page.waitForTimeout(10000); 
    
    await page.screenshot({ path: 'diary_screenshot.png', fullPage: true });
    console.log('Saved diary_screenshot.png');

    console.log('Navigating to /chat...');
    await page.goto('http://localhost:5173/chat');
    await page.waitForTimeout(3000);
    
    console.log('Chatting...');
    await page.locator('textarea').fill('我感觉真的很迷茫，每天都在自我怀疑和内耗。');
    await page.waitForTimeout(500);
    // Send button
    await page.locator('.chat-input-area .n-button').click();
    
    console.log('Waiting for AI response...');
    await page.waitForTimeout(12000);
    await page.screenshot({ path: 'chat_screenshot.png' });
    console.log('Saved chat_screenshot.png');

    console.log('Navigating to /report...');
    await page.goto('http://localhost:5173/report');
    await page.waitForTimeout(4000);
    
    await page.screenshot({ path: 'report_screenshot.png', fullPage: true });
    console.log('Saved report_screenshot.png');

  } catch (err) {
    console.error('Error during execution:', err);
  }

  await browser.close();
})();
