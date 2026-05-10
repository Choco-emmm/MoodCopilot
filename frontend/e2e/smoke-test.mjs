import { chromium } from 'playwright';

const BASE = 'http://localhost:4173';

async function main() {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 } }); // iPhone 14 size
  const page = await ctx.newPage();

  let passed = 0;
  let failed = 0;
  const check = async (label, fn) => {
    try {
      await fn(page);
      console.log(`  ✅ ${label}`);
      passed++;
    } catch (e) {
      console.log(`  ❌ ${label}: ${e.message?.slice(0, 120)}`);
      failed++;
    }
  };

  console.log('\n═══ MoodCopilot E2E 冒烟测试 ═══\n');

  // ── 1. 登录页 ──
  console.log('1. 登录页');
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await check('显示登录表单', async (p) => {
    await p.waitForSelector('.auth-card', { timeout: 5000 });
  });
  await check('M logo 存在（无旧印章）', async (p) => {
    await p.waitForSelector('.auth-logo svg', { timeout: 3000 });
    const seal = await p.$('.auth-seal');
    if (seal) throw new Error('旧印章仍存在');
  });
  await check('登录标题可见', async (p) => {
    await p.waitForSelector('.auth-title', { timeout: 3000 });
  });

  // ── 2. 登录 ──
  console.log('2. 执行登录');
  await page.fill('input[placeholder="输入邮箱"]', 'test@test.com');
  await page.fill('input[placeholder="输入密码"]', '123456');
  await page.click('button:has-text("登录")');
  await page.waitForTimeout(2000);

  await check('跳转到广场页', async (p) => {
    const url = p.url();
    // 登录成功后应离开 /login，到 / 或其他认证页
    if (url.includes('/login')) {
      const alert = await p.$('.n-alert');
      if (alert) {
        const text = await alert.textContent();
        throw new Error(`登录失败: ${text}`);
      }
      throw new Error('仍停留在登录页');
    }
  });

  // ── 3. 广场页 ──
  console.log('3. 广场页');
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' });
  await check('M logo 显示在顶栏', async (p) => {
    await p.waitForSelector('.brand-mark svg', { timeout: 5000 });
  });
  await check('今日状态条可见', async (p) => {
    await p.waitForSelector('.today-status-row', { timeout: 5000 });
  });
  await check('无情绪标签', async (p) => {
    const moodChips = await p.$$('.mood-chip');
    if (moodChips.length > 0) throw new Error('社区共鸣情绪标签仍存在');
  });
  await check('公开日记流加载', async (p) => {
    await p.waitForSelector('.feed-item', { timeout: 8000 });
  });
  await check('日记卡片无情绪标签', async (p) => {
    const tags = await p.$$('.feed-item .n-tag');
    // 可能还有公开/私密标签，但不应该有情绪标签
    const moodTag = await p.$('.feed-head .n-tag');
    if (moodTag) throw new Error('feed 项仍显示情绪标签');
  });

  // ── 4. 个人中心 ──
  console.log('4. 个人中心');
  await check('点击用户名进入设置', async (p) => {
    await p.click('.masthead-user-link');
    await p.waitForTimeout(1000);
    const url = p.url();
    if (!url.includes('/settings')) throw new Error(`未跳转到设置页: ${url}`);
  });
  await check('设置页显示头像上传', async (p) => {
    await p.waitForSelector('.avatar-upload', { timeout: 5000 });
  });
  await check('设置页显示用户名编辑', async (p) => {
    await p.waitForSelector('input[placeholder="输入新用户名"]', { timeout: 3000 });
  });
  await check('设置页显示通知开关', async (p) => {
    await p.waitForSelector('.n-switch', { timeout: 3000 });
  });
  await check('设置页显示退出按钮', async (p) => {
    await p.waitForSelector('button:has-text("退出登录")', { timeout: 3000 });
  });

  // ── 5. 日记详情 ──
  console.log('5. 日记详情');
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.feed-item', { timeout: 8000 });
  const firstDiary = await page.$('.feed-item .feed-content');
  if (firstDiary) {
    await page.click('.feed-item:first-child .n-button:has-text("看分析")');
    await page.waitForTimeout(1500);
    await check('日记详情加载', async (p) => {
      await p.waitForSelector('.diary-detail-page', { timeout: 5000 });
    });
    await check('加载中不显示"日记不存在"', async (p) => {
      // 页面已加载完成，确认不是显示"日记不存在"
      const empty = await p.$('.n-empty');
      if (empty) {
        const text = await empty.textContent();
        if (text?.includes('不存在')) throw new Error('显示了日记不存在');
      }
    });
  } else {
    console.log('  ⏭️ 跳过（无公开日记）');
  }

  // ── 6. AI 聊天 ──
  console.log('6. AI 聊天页');
  await page.goto(`${BASE}/chat`, { waitUntil: 'networkidle' });
  await check('聊天页加载', async (p) => {
    await p.waitForSelector('.chat-messages', { timeout: 5000 });
  });
  await check('输入框可见', async (p) => {
    await p.waitForSelector('.chat-input-row input', { timeout: 3000 });
  });

  // ── 7. 报告页 ──
  console.log('7. 报告页');
  await page.goto(`${BASE}/report`, { waitUntil: 'networkidle' });
  await check('报告页加载', async (p) => {
    await p.waitForSelector('.report-page', { timeout: 8000 });
  });

  // ── 8. 桌面端顶栏 ──
  console.log('8. 桌面端布局');
  const desktopCtx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const desktopPage = await desktopCtx.newPage();
  await desktopPage.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await desktopPage.fill('input[placeholder="输入邮箱"]', 'test@test.com');
  await desktopPage.fill('input[placeholder="输入密码"]', '123456');
  await desktopPage.click('button:has-text("登录")');
  await desktopPage.waitForTimeout(2000);
  // 确认登录成功
  const desktopUrl = desktopPage.url();
  console.log('  桌面端登录后URL:', desktopUrl);
  await check('桌面端已登录', async (p) => {
    if (p.url().includes('/login')) {
      const alert = await p.$('.n-alert');
      throw new Error('桌面端登录失败');
    }
  });
  await desktopPage.goto(`${BASE}/`, { waitUntil: 'networkidle' });
  await check('桌面端M logo可见', async (p) => {
    await p.waitForSelector('.brand-mark', { timeout: 5000 });
  });
  await check('桌面端顶栏可见', async (p) => {
    await p.waitForSelector('.masthead', { timeout: 5000 });
  });
  await desktopCtx.close();

  // ── 结果 ──
  console.log(`\n═══ 结果: ${passed} 通过, ${failed} 失败 ═══\n`);

  await browser.close();
  process.exit(failed > 0 ? 1 : 0);
}

main().catch(e => {
  console.error('测试异常:', e.message);
  process.exit(1);
});
