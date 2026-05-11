# App 化基础准备 E2E 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐关键 Web/API 冒烟测试，保证 App 化前路由、登录态和核心 API 路径稳定。

**Architecture:** 不新增产品功能，先建立一条可重复执行的 Playwright 冒烟链路。测试从真实前端入口进入，覆盖登录、主要路由、核心接口健康、写日记入口、报告页和聊天页可访问性。

**Tech Stack:** Vue 3、Vite 5、Playwright、Spring Boot API、本机 `18080` 后端和 `4173` 前端预览。

---

## 文件结构

- Create: `frontend/e2e/smoke-test.mjs`：Playwright 冒烟测试脚本。
- Modify: `package.json`：增加根目录 E2E 脚本，复用根目录已有 Playwright 依赖。
- Modify: `README.md`：补充 App 化基础准备的本地验证命令。
- Modify: `AGENTS.md`：补充 E2E 命令和前置服务要求。

---

### Task 1: 添加根目录 E2E 命令

**Files:**
- Modify: `package.json`

- [ ] **Step 1: 修改 package.json**

将根目录 `package.json` 改为：

```json
{
  "scripts": {
    "e2e:smoke": "node frontend/e2e/smoke-test.mjs"
  },
  "dependencies": {
    "playwright": "^1.59.1"
  }
}
```

- [ ] **Step 2: 验证脚本可被 npm 识别**

Run:

```powershell
cd D:\Code\MoodCopilot
npm.cmd run
```

Expected: 输出包含 `e2e:smoke`。

---

### Task 2: 编写 Playwright 冒烟测试

**Files:**
- Create: `frontend/e2e/smoke-test.mjs`

- [ ] **Step 1: 创建测试脚本**

写入以下内容：

```javascript
import { chromium } from 'playwright';

const BASE_URL = process.env.E2E_BASE_URL || 'http://127.0.0.1:4173';
const API_URL = process.env.E2E_API_URL || 'http://127.0.0.1:18080/api';
const EMAIL = process.env.E2E_EMAIL || 'test@test.com';
const PASSWORD = process.env.E2E_PASSWORD || '123456';

async function expectOk(response, label) {
  if (!response.ok()) {
    throw new Error(`${label} failed: ${response.status()} ${await response.text()}`);
  }
  return response;
}

async function main() {
  const health = await fetch(`${API_URL}/health`);
  await expectOk(health, 'health');

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });

  try {
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
    await page.getByPlaceholder('输入邮箱').fill(EMAIL);
    await page.getByPlaceholder('输入密码').fill(PASSWORD);
    await page.getByRole('button', { name: '登录' }).click();
    await page.waitForURL(`${BASE_URL}/`, { timeout: 15000 });

    await page.getByText('写下今天，慢慢理解自己。').waitFor({ timeout: 10000 });
    await page.goto(`${BASE_URL}/write`, { waitUntil: 'networkidle' });
    await page.getByText('此刻发生了什么').waitFor({ timeout: 10000 });

    await page.goto(`${BASE_URL}/report`, { waitUntil: 'networkidle' });
    await page.getByText(/周报|月报|报告/).first().waitFor({ timeout: 10000 });

    await page.goto(`${BASE_URL}/chat`, { waitUntil: 'networkidle' });
    await page.getByText(/MoodCopilot|AI/).first().waitFor({ timeout: 10000 });

    await page.goto(`${BASE_URL}/following`, { waitUntil: 'networkidle' });
    await page.waitForLoadState('networkidle');

    const desktop = await browser.newPage({ viewport: { width: 1280, height: 800 } });
    await desktop.goto(`${BASE_URL}/`, { waitUntil: 'networkidle' });
    await desktop.getByText('广场').first().waitFor({ timeout: 10000 });
    await desktop.close();
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
```

- [ ] **Step 2: 运行前先启动服务**

Run:

```powershell
cd D:\Code\MoodCopilot\backend\moodcopilot
cmd /c mvn.cmd -o spring-boot:run -Dspring-boot.run.profiles=dev
```

另一个终端：

```powershell
cd D:\Code\MoodCopilot\frontend
npm.cmd run build
npx.cmd vite preview --host 127.0.0.1 --port 4173
```

- [ ] **Step 3: 执行冒烟测试**

Run:

```powershell
cd D:\Code\MoodCopilot
npm.cmd run e2e:smoke
```

Expected: 命令退出码为 0。

---

### Task 3: 文档化 App 化验证入口

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: 更新 README.md**

在“本地开发”或“下一步”附近补充：

````markdown
## App 化前冒烟验证

前置条件：后端运行在 `18080`，前端生产预览运行在 `4173`。

```powershell
cd D:\Code\MoodCopilot\frontend
npm.cmd run build
npx.cmd vite preview --host 127.0.0.1 --port 4173

cd D:\Code\MoodCopilot
npm.cmd run e2e:smoke
```
````

- [ ] **Step 2: 更新 AGENTS.md**

在“常用验证命令”中追加：

````markdown
### E2E

```powershell
cd D:\Code\MoodCopilot
npm.cmd run e2e:smoke
```

前置条件：后端 `18080` 和前端预览 `4173` 均已启动。
````

---

### Task 4: 验证与收尾

**Files:**
- Run: frontend build、backend compile、E2E smoke。

- [ ] **Step 1: 后端编译**

Run:

```powershell
cd D:\Code\MoodCopilot\backend\moodcopilot
cmd /c mvn.cmd -o compile
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 2: 前端构建**

Run:

```powershell
cd D:\Code\MoodCopilot\frontend
npm.cmd run build
```

Expected: Vite build 成功。

- [ ] **Step 3: E2E 冒烟**

Run:

```powershell
cd D:\Code\MoodCopilot
npm.cmd run e2e:smoke
```

Expected: 退出码 0。若失败，先记录失败页面和错误，再按系统化调试流程定位。

- [ ] **Step 4: 检查 git 变更**

Run:

```powershell
cd D:\Code\MoodCopilot
git status --short
```

Expected: 只包含计划内文件变更。
