# 视觉与核心体验打磨 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 MoodCopilot 的朋友内测版从“功能可用”打磨到“第一眼舒服、写日记安心、手机端常用路径顺手”。

**Architecture:** 本轮只改 Web 体验和验证脚本，不新增后端表结构，不改变现有 API 契约。先建立 Playwright 视觉/交互冒烟，再按页面边界打磨：全局设计系统、广场、写日记、AI 聊天，最后更新文档和验证命令。

**Tech Stack:** Vue 3、Vite 5、TypeScript、Naive UI、Pinia、Vue Router、Playwright、现有 Spring Boot API。

---

## Scope

本计划聚焦朋友内测最容易被感知的体验质量：

- 视觉气质从“纸墨/印章/楷体感”转向更现代、克制、温暖的 `Warm Precision`。
- 广场继续保持轻量正文流，不展示 AI 主题/分类标签，不展示“看分析”和“鼓励”。
- 写日记页强调本机草稿、保存状态、公开范围和手机端输入舒适度。
- 聊天页重点修手机端布局和输入体验，不改现有 SSE/JSON 兜底架构。
- 补一条可重复执行的视觉冒烟脚本，防止以后又把公开帖子做重。

Non-goals:

- 不做原生 App 工程。
- 不做完整审核后台。
- 不做大并发压测。
- 不恢复公开帖子上的 AI 分析入口、主题标签或分类标签。
- 不新增 AI 自主工具调用。

## File Structure

- Create: `frontend/e2e/visual-polish-smoke.mjs`
  - 注册临时账号，覆盖移动端写日记草稿恢复、公开日记流清爽度、聊天页基本可用性，并保存截图。
- Modify: `package.json`
  - 增加根目录 `e2e:visual-polish` 脚本。
- Modify: `frontend/src/styles.css`
  - 刷新全局设计 token、移动端 feed/chat/write 关键布局、按钮和卡片细节。
- Modify: `frontend/src/pages/SquarePage.vue`
  - 简化顶部概览，把社区入口从“统计/分类感”改为“今日状态 + 继续写/继续聊”的轻量入口。
- Modify: `frontend/src/components/PublicFeed.vue`
  - 调整标题、空状态和加载状态文案，继续保留滚动分页和“加载更多”兜底。
- Modify: `frontend/src/components/DiaryFeedItem.vue`
  - 做卡片视觉微调，保证只呈现作者、时间、正文、共鸣、留言、举报/隐藏。
- Modify: `frontend/src/components/DiaryComposer.vue`
  - 增加草稿保存时间、手机端保存区布局优化、公开/私密说明更清楚。
- Modify: `frontend/src/pages/WritePage.vue`
  - 调整写作页结构，让历史列表和 AI 分析不抢写作输入的注意力。
- Modify: `frontend/src/pages/ChatPage.vue`
  - 优化手机端会话列表、消息区高度、输入栏 sticky 行为和发送状态。
- Modify: `frontend/src/components/ReferenceBar.vue`
  - 让引用 chips 在手机端不挤压聊天输入。
- Modify: `README.md`
  - 更新下一步规划和视觉冒烟命令。
- Modify: `AGENTS.md`
  - 记录视觉冒烟命令和公开社区卡片约束。

---

### Task 1: 增加视觉与核心路径冒烟脚本

**Files:**
- Create: `frontend/e2e/visual-polish-smoke.mjs`
- Modify: `package.json`

- [ ] **Step 1: 创建视觉冒烟脚本**

Create `frontend/e2e/visual-polish-smoke.mjs`:

```javascript
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
  await page.screenshot({ path: 'test-results/visual-mobile-write.png', fullPage: true });
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
  await page.screenshot({ path: 'test-results/visual-mobile-square.png', fullPage: true });
}

async function verifyChatMobile(page) {
  await page.goto(`${BASE_URL}/chat`, { waitUntil: 'networkidle' });
  await page.getByText('MoodCopilot').first().waitFor({ timeout: 10000 });
  const inputVisible = await page.locator('.chat-input-row').isVisible();
  if (!inputVisible) throw new Error('chat input row is not visible on mobile');
  await page.screenshot({ path: 'test-results/visual-mobile-chat.png', fullPage: true });
}

async function verifyDesktopSquare(browser, content) {
  const desktop = await browser.newPage({ viewport: { width: 1280, height: 820 } });
  await desktop.goto(`${BASE_URL}/`, { waitUntil: 'networkidle' });
  await desktop.getByText(content).waitFor({ timeout: 10000 });
  await desktop.screenshot({ path: 'test-results/visual-desktop-square.png', fullPage: true });
  await desktop.close();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
```

- [ ] **Step 2: 给根目录 package.json 增加脚本**

Modify `package.json`:

```json
{
  "scripts": {
    "e2e:visual-polish": "node frontend/e2e/visual-polish-smoke.mjs"
  },
  "dependencies": {
    "playwright": "^1.59.1"
  }
}
```

- [ ] **Step 3: 验证脚本注册**

Run:

```powershell
cd D:\Code\MoodCopilot
npm.cmd run
```

Expected: 输出包含 `e2e:visual-polish`。

- [ ] **Step 4: Commit**

```powershell
git add package.json frontend/e2e/visual-polish-smoke.mjs
git commit -m "test: 增加视觉与核心路径冒烟脚本"
```

---

### Task 2: 刷新全局视觉系统为 Warm Precision

**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 替换顶部设计说明和核心 token**

In `frontend/src/styles.css`, replace the opening comment and `:root` block with:

```css
/* ═══════════════════════════════════════════
   MoodCopilot — Warm Precision
   设计方向：温暖、中性、克制、移动端优先
   ═══════════════════════════════════════════ */

:root {
  --color-bg: #f8f6f1;
  --color-surface: #fffdf8;
  --color-surface-hover: #f1eee6;
  --color-surface-soft: #f6f2ea;

  --color-text: #20201d;
  --color-text-secondary: #67645d;
  --color-text-muted: #98938a;

  --color-accent: #a94b45;
  --color-accent-light: #d9827a;
  --color-accent-bg: #fff1ef;

  --color-jade: #3f7a63;
  --color-jade-light: #e7f0eb;
  --color-jade-hover: #326a54;

  --color-border: #e3ded3;
  --color-border-strong: #cdc5b6;

  --font-display: "Inter", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  --font-body: "Inter", "PingFang SC", "Microsoft YaHei", "Hiragino Sans GB", system-ui, sans-serif;

  --shadow-sm: 0 1px 3px rgba(32, 32, 29, 0.05);
  --shadow-md: 0 8px 24px rgba(32, 32, 29, 0.07);
  --shadow-lg: 0 14px 40px rgba(32, 32, 29, 0.09);

  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;

  color: var(--color-text);
  background: var(--color-bg);
  font-family: var(--font-body);
  font-synthesis: none;
  text-rendering: optimizeLegibility;
}
```

- [ ] **Step 2: 移除纸张噪点覆盖层**

Delete the `body::after` paper grain block. Expected result: app no longer has a global fixed pseudo-element above all content.

- [ ] **Step 3: 调整标题风格**

Replace the `h1` and `h2` rules with:

```css
h1 {
  font-family: var(--font-display);
  font-size: clamp(32px, 5vw, 46px);
  line-height: 1.12;
  font-weight: 760;
  color: var(--color-text);
}

h2 {
  font-family: var(--font-display);
  font-size: 20px;
  line-height: 1.3;
  font-weight: 720;
  color: var(--color-text);
}
```

- [ ] **Step 4: 前端构建验证**

Run:

```powershell
cd D:\Code\MoodCopilot\frontend
npm.cmd run build
```

Expected: `✓ built`，允许 Vite chunk size warning。

- [ ] **Step 5: Commit**

```powershell
git add frontend/src/styles.css
git commit -m "style: 刷新 MoodCopilot 全局视觉系统"
```

---

### Task 3: 打磨广场首屏和公开流

**Files:**
- Modify: `frontend/src/pages/SquarePage.vue`
- Modify: `frontend/src/components/PublicFeed.vue`
- Modify: `frontend/src/components/DiaryFeedItem.vue`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 简化 SquarePage 顶部数据依赖**

In `frontend/src/pages/SquarePage.vue`, remove the `moods` ref and remove this request from `onMounted`:

```ts
try { const res = await diaryApi.communityMood(); moods.value = res.data.data } catch { /* ignore */ }
```

Expected: `SquarePage.vue` no longer imports or renders community mood distribution.

- [ ] **Step 2: 替换右侧概览模板**

Replace the `.today-side` template block with:

```vue
<div class="today-side">
  <router-link v-if="matchDiary" :to="'/diary/' + matchDiary.id" class="today-match-mini">
    <span class="today-side-label">今日同频</span>
    <span class="today-side-snippet">「{{ matchDiary.content?.length > 42 ? matchDiary.content.slice(0, 42) + '...' : matchDiary.content }}」</span>
  </router-link>

  <router-link v-else to="/write" class="today-match-mini">
    <span class="today-side-label">今日同频</span>
    <span class="today-side-snippet">写下今天后，MoodCopilot 会帮你找相似处境的人。</span>
  </router-link>
</div>
```

- [ ] **Step 3: 调整 PublicFeed 标题**

In `frontend/src/components/PublicFeed.vue`, replace title text:

```vue
<p class="eyebrow">公开日记</p>
<h2>最近的心情</h2>
```

Expected: 广场语气更像正文流，不像功能模块。

- [ ] **Step 4: 给 FeedItem 正文加长度约束和展开按钮**

In `frontend/src/components/DiaryFeedItem.vue`, add state:

```ts
const expanded = ref(false)
const isLongContent = computed(() => props.diary.content.length > 180)
const visibleContent = computed(() => {
  if (expanded.value || !isLongContent.value) return props.diary.content
  return props.diary.content.slice(0, 180) + '...'
})
```

Update imports:

```ts
import { computed, ref, onMounted } from 'vue'
```

Replace the content paragraph:

```vue
<p class="feed-content">{{ visibleContent }}</p>
<button v-if="isLongContent" class="feed-expand" type="button" @click="expanded = !expanded">
  {{ expanded ? '收起' : '展开' }}
</button>
```

- [ ] **Step 5: 添加 Feed 微调样式**

Add to `frontend/src/styles.css`:

```css
.feed-expand {
  justify-self: start;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-jade);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.feed-expand:hover {
  color: var(--color-jade-hover);
}
```

- [ ] **Step 6: 验证公开流不出现禁用文案**

Run:

```powershell
cd D:\Code\MoodCopilot
npm.cmd run e2e:visual-polish
```

Expected: 退出码 0，生成 `test-results/visual-mobile-square.png` 和 `test-results/visual-desktop-square.png`。

- [ ] **Step 7: Commit**

```powershell
git add frontend/src/pages/SquarePage.vue frontend/src/components/PublicFeed.vue frontend/src/components/DiaryFeedItem.vue frontend/src/styles.css
git commit -m "style: 打磨广场首屏和公开日记流"
```

---

### Task 4: 优化写日记体验

**Files:**
- Modify: `frontend/src/components/DiaryComposer.vue`
- Modify: `frontend/src/pages/WritePage.vue`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 增加草稿保存时间**

In `frontend/src/components/DiaryComposer.vue`, add refs and formatter:

```ts
const draftNotice = ref('')
const draftSavedAt = ref('')

function updateDraftSavedAt() {
  draftSavedAt.value = new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date())
}
```

In the `watch(draft, ...)` branch where `value` is truthy, call:

```ts
updateDraftSavedAt()
```

- [ ] **Step 2: 展示保存时间**

Replace the draft notice span with:

```vue
<span v-if="draftNotice" class="draft-notice">
  <span class="draft-dot" />
  {{ draftNotice }}<template v-if="draftSavedAt"> · {{ draftSavedAt }}</template>
</span>
```

- [ ] **Step 3: 写作页隐藏空占位列**

In `frontend/src/pages/WritePage.vue`, replace:

```vue
<section class="content-grid write-history-grid">
  <MyDiaryList :diaries="store.myDiaries" @select="selectDiary" @delete="handleDelete" />
  <div />
</section>
```

with:

```vue
<section class="write-history-section">
  <MyDiaryList :diaries="store.myDiaries" @select="selectDiary" @delete="handleDelete" />
</section>
```

- [ ] **Step 4: 写作页样式**

Add to `frontend/src/styles.css`:

```css
.write-history-section {
  width: min(100%, 520px);
  margin-top: 16px;
}

@media (max-width: 780px) {
  .write-history-section {
    width: 100%;
  }
}
```

- [ ] **Step 5: 验证草稿恢复**

Run:

```powershell
cd D:\Code\MoodCopilot
npm.cmd run e2e:visual-polish
```

Expected: 退出码 0；`visual-mobile-write.png` 中能看到草稿提示。

- [ ] **Step 6: Commit**

```powershell
git add frontend/src/components/DiaryComposer.vue frontend/src/pages/WritePage.vue frontend/src/styles.css
git commit -m "style: 优化写日记页草稿与历史布局"
```

---

### Task 5: 优化手机端 AI 聊天布局

**Files:**
- Modify: `frontend/src/pages/ChatPage.vue`
- Modify: `frontend/src/components/ReferenceBar.vue`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 给聊天发送按钮增加短文案保护**

In `frontend/src/pages/ChatPage.vue`, replace send button text:

```vue
<n-button type="primary" :disabled="!draft.trim() || streaming || !activeConvId" @click="send">
  {{ streaming ? '发送中' : '发送' }}
</n-button>
```

- [ ] **Step 2: ReferenceBar 手机端不挤压输入区**

In `frontend/src/components/ReferenceBar.vue`, ensure root has class `ref-bar`. If it already exists, no template change is needed. Add this CSS in `frontend/src/styles.css`:

```css
@media (max-width: 780px) {
  .ref-bar {
    max-height: 68px;
    overflow-y: auto;
  }

  .ref-chip-label {
    max-width: 180px;
  }
}
```

- [ ] **Step 3: 手机端聊天输入 sticky**

Add to the existing mobile media block in `frontend/src/styles.css`:

```css
@media (max-width: 780px) {
  .chat-window {
    min-height: calc(100vh - 150px);
    padding-bottom: 0;
  }

  .chat-messages {
    min-height: 42vh;
    max-height: calc(100vh - 330px);
    padding: 16px;
    border-radius: var(--radius-md);
  }

  .chat-input-area {
    position: sticky;
    bottom: calc(72px + env(safe-area-inset-bottom));
    z-index: 20;
    padding: 10px 0 0;
    background: linear-gradient(180deg, rgba(248, 246, 241, 0), var(--color-bg) 28%);
  }

  .chat-input-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
  }
}
```

- [ ] **Step 4: 手机端会话列表改横向**

Add to mobile media block:

```css
@media (max-width: 780px) {
  .chat-sidebar {
    overflow-x: auto;
  }

  .conv-list {
    display: flex;
    max-height: none;
    overflow-x: auto;
    overflow-y: hidden;
  }

  .conv-item {
    min-width: 148px;
    border-right: 1px solid var(--color-border);
    border-bottom: none;
  }
}
```

- [ ] **Step 5: 验证手机聊天页**

Run:

```powershell
cd D:\Code\MoodCopilot
npm.cmd run e2e:visual-polish
```

Expected: 退出码 0；`visual-mobile-chat.png` 中输入栏可见，引用栏不遮挡消息。

- [ ] **Step 6: Commit**

```powershell
git add frontend/src/pages/ChatPage.vue frontend/src/components/ReferenceBar.vue frontend/src/styles.css
git commit -m "style: 优化手机端 AI 聊天布局"
```

---

### Task 6: 文档、验证和收尾

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: README 增加下一步说明**

In `README.md`, under `待做`, replace the first item with:

```markdown
- **视觉与核心体验打磨** — Warm Precision 视觉刷新、广场轻量正文流、写日记页和手机聊天页体验优化、视觉冒烟脚本。
```

- [ ] **Step 2: AGENTS 增加视觉冒烟命令**

In `AGENTS.md`, under `常用验证命令`, add:

````markdown
### 视觉冒烟

前置条件：后端 `18080` 和前端预览 `4173` 已启动。

```powershell
cd D:\Code\MoodCopilot
npm.cmd run e2e:visual-polish
```

验证范围：移动端写日记草稿恢复、公开流禁用文案检查、移动端聊天输入可见、桌面广场截图。
````

- [ ] **Step 3: 全量验证**

Run:

```powershell
cd D:\Code\MoodCopilot\frontend
npm.cmd run build

cd D:\Code\MoodCopilot
npm.cmd run e2e:visual-polish
```

Expected:

```text
frontend build: ✓ built
visual polish smoke: exit code 0
```

- [ ] **Step 4: 检查用户可见禁用词**

Run:

```powershell
cd D:\Code\MoodCopilot
rg -n "鼓励|看分析|主题标签|分类标签|topic-cloud|tag-row" frontend/src
```

Expected: 只允许 TypeScript 类型或后端兼容字段命中；不允许 Vue template 中出现公开帖子标签渲染。

- [ ] **Step 5: 最终提交**

```powershell
git add README.md AGENTS.md package.json frontend/e2e/visual-polish-smoke.mjs frontend/src
git commit -m "style: 打磨 MoodCopilot 视觉与核心体验"
```

## Self-Review

Spec coverage:

- 美观度：Task 2、Task 3、Task 4、Task 5 覆盖。
- 草稿可见与写作安心：Task 4 覆盖。
- 广场轻量正文流：Task 3 覆盖，并在 Task 6 用 `rg` 防回归。
- 手机端体验：Task 1、Task 5 覆盖。
- 可验证性：Task 1 和 Task 6 覆盖。

Placeholder scan:

- 占位词扫描通过。
- 每个代码变更步骤都给出具体文件和代码片段。

Type consistency:

- 新脚本使用根目录现有 `playwright` 依赖。
- `DiaryFeedItem.vue` 新增 `computed` 后同步更新 Vue import。
- `package.json` 新脚本名和文档命令均为 `e2e:visual-polish`。
