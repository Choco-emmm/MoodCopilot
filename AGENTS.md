# AGENTS.md

本文件给 Codex / 自动化代理使用。详细架构、API 路由和产品说明以 `CLAUDE.md`、`README.md` 为准；这里记录实际协作约束和本仓库中已经验证过的工作流。

## 基本规则

- 所有说明、提交信息、计划文档和代码注释优先使用中文。
- 保持改动最小、准确，以仓库现有代码和运行结果为依据。
- 不要提交 `frontend/dist/`、临时日志、`.maven-*` 下载内容或其他自动生成文件。
- 工作树可能已有用户或其他代理改动；不要回滚无关改动。
- 如果不确定，添加带简短原因的 TODO，不要编造接口、命令或结论。

## 常用验证命令

### 前端

PowerShell 会拦截 `npm.ps1`，在 Windows 下优先使用 `npm.cmd` / `npx.cmd`。

```powershell
cd D:\Code\MoodCopilot\frontend
npm.cmd install
npm.cmd run build
npx.cmd vite preview --host 127.0.0.1 --port 4173
```

本次验证结果：`npm.cmd run build` 可通过，包含 `vue-tsc -b && vite build`。

### 后端

当前环境下 `mvnw.cmd` 会通过 PowerShell 片段启动，可能因重复的 `PATH/Path` 环境变量报 `Cannot index into a null array`。优先使用本机 Maven：

```powershell
cd D:\Code\MoodCopilot\backend\moodcopilot
cmd /c mvn.cmd compile
cmd /c mvn.cmd test
```

本次验证结果：`cmd /c mvn.cmd compile`、`cmd /c mvn.cmd test` 均可通过。

### E2E

前置条件：后端 `18080` 和前端预览 `4173` 均已启动。公网验证还需要 `cloudflared` 隧道在线。

```powershell
cd D:\Code\MoodCopilot
npm.cmd run e2e:smoke
npm.cmd run e2e:visual-polish
```

## 公开访问恢复流程

`https://moodcopilot.dpdns.org/` 依赖本机三件事同时可用：

1. 后端监听 `18080`。
2. 前端生产预览监听 `4173`。
3. `cloudflared` 隧道使用 `C:\Users\renpe\.cloudflared\moodcopilot-config.yaml` 运行。

本地检查：

```powershell
Invoke-WebRequest http://127.0.0.1:18080/api/health -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:4173/ -UseBasicParsing
```

启动前端预览：

```powershell
cd D:\Code\MoodCopilot\frontend
npm.cmd run build
npx.cmd vite preview --host 127.0.0.1 --port 4173
```

启动隧道：

```powershell
cloudflared tunnel --config C:\Users\renpe\.cloudflared\moodcopilot-config.yaml run moodcopilot
```

如果 `cloudflared` 不在 PATH，WinGet 安装位置通常是：

```powershell
C:\Users\renpe\AppData\Local\Microsoft\WinGet\Packages\Cloudflare.cloudflared_Microsoft.Winget.Source_8wekyb3d8bbwe\cloudflared.exe tunnel --config C:\Users\renpe\.cloudflared\moodcopilot-config.yaml run moodcopilot
```

近期排查结论：

- 如果公网打不开但本地能打开，先检查三段链路：`18080`、`4173`、`cloudflared`。任意一段掉线都会导致 `https://moodcopilot.dpdns.org/` 不可用。
- MySQL 是 Windows 服务，普通 Codex shell 不能启动；需要用户用管理员权限或服务管理器先启动 `MySQL`。确认命令：`Test-NetConnection localhost -Port 3306`。
- Flyway `V1_10` 版本冲突已整理：`V1_10__add_user_profile.sql` 保留头像/通知列迁移，举报/隐藏表放到 `V1_11__add_reports_and_hides.sql`。如果启动时仍提示两个 `V1_10`，通常是 `target/classes` 残留旧迁移，先跑 `cmd /c mvn.cmd clean compile`。
- 本地开发库曾缺少 `diary_hides` / `user_reports`，导致登录后 `/api/diaries/public`、`today-match`、`coaching`、`community-mood` 返回 403 并被前端拦截器踢回登录页。当前迁移已纳入 `V1_11`。
- 当前已验证：`http://127.0.0.1:4173/`、`http://127.0.0.1:18080/api/health`、`https://moodcopilot.dpdns.org/`、`https://moodcopilot.dpdns.org/api/health` 均返回 200。

## 手机端 AI 聊天排障结论

2026-05-11 已修复手机端无法和 AI 对话、电脑和手机聊天内容不同步的问题。关键结论：

- 不要在前端业务代码里写死 `http://localhost:18080/api`。手机访问公网时，`localhost` 指向手机自身，必然无法访问本机后端；聊天历史也会因为请求失败而不同步。
- 聊天历史读写必须统一走 `chatApi.getHistory()` / `chatApi.saveHistory()`，它们基于 axios 的同源 `/api` baseURL，会经 Cloudflare Tunnel 转发到后端。
- SSE 流式接口 `POST /api/chat/conversations/{id}` 在本地 localhost 可继续使用；公网手机端不要依赖它。Cloudflare Tunnel + HTTP/2 + 移动浏览器曾出现 `ERR_HTTP2_PROTOCOL_ERROR`，`cloudflared` 日志表现为 `unexpected EOF` / `context canceled`。
- 公网和手机端应走普通 JSON 兜底接口：`POST /api/chat/conversations/{id}/reply`。前端 `ChatPage.vue` 当前逻辑是 localhost 开发环境使用 SSE，其余环境使用 `/reply`。
- 验证手机端聊天时，抓网络请求应看到 `POST https://moodcopilot.dpdns.org/api/chat/conversations/{id}/reply` 返回 200，且不应出现 `localhost:18080` 或对 `/api/chat/conversations/{id}` 的流式 POST。
- 已验证手机视口登录、进入 `/chat`、发送消息成功；`localhostRequestCount=0`、`streamRequestCount=0`、`replyStatus=200`、控制台错误为 0。随后读取 `/api/chat/conversations/{id}/history` 能看到最后一条 user/ai 消息，说明跨端同步已恢复。

## 当前开发方向

`README.md` 已把大并发压测降级为暂不做，当前更适合继续做：

- App 化基础准备：保持 Web/API 路径稳定，补齐关键 E2E 冒烟测试。
- 审核后台：基于现有举报数据，补处理状态、隐藏范围和管理视图。
- 推荐质量优化：曝光去重、同一作者去重；不做推荐理由展示。
- 公开社区卡片保持轻量正文流：不要展示 AI 主题/分类标签，不提供“看分析”入口；鼓励功能暂不作为广场主操作展示。

## 已知踩坑

- 不要用 PowerShell 直接运行 `npm`，用 `npm.cmd`。
- 当前环境存在重复 `PATH` / `Path`，会影响 PowerShell 环境枚举、`Start-Process` 重定向和 Maven Wrapper。
- `cloudflared` 隧道配置为 `/api -> localhost:18080`，其他路径 `-> localhost:4173`；如果 4173 不起，公网首页不可访问。
- Vite preview 通过 Cloudflare Tunnel 暴露时会校验 Host。`moodcopilot.dpdns.org` 必须在 `frontend/vite.config.ts` 的 `preview.allowedHosts` 中，否则公网首页返回 403，错误为 `Blocked request. This host (...) is not allowed`。
- 旧版本曾按 PWA/Service Worker 方式缓存页面壳时，访问过旧版的浏览器可能继续被旧 SW 控制，新版本即使移除了注册代码也不会自动卸载旧 SW。当前仓库通过 `frontend/public/sw.js` 提供同路径清退脚本，并在 `frontend/src/main.ts` 启动时注销同源旧 SW、清理 Cache Storage。验证 `/sw.js` 必须返回 `text/javascript`，不能回退成 `index.html`。
- 公开流缓存不要按用户缓存完整结果。应缓存全局公开页，再按当前用户过滤隐藏日记，避免用户发布新公开日记后其他用户缓存不刷新。
- 报告页会消耗 `REPORT` 限额。前端不要一进页面同时拉周报和月报；当前月报采用切换到月报时懒加载，429 时展示可重试提示，避免未处理 Promise 错误。
