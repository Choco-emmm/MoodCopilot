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
cmd /c mvn.cmd -o compile
cmd /c mvn.cmd -o test-compile
```

本次验证结果：`cmd /c mvn.cmd -o compile` 可重新编译后端源码；`cmd /c mvn.cmd -o test-compile` 可通过。

TODO：`cmd /c mvn.cmd test` 目前需要下载 Surefire 3.5.5 依赖，但全局 Maven 本地仓库指向 `D:\coding\apache-maven-3.9.12\mvn_repo`，当前沙箱不可写，导致测试阶段无法下载缺失依赖。需要在普通终端补齐依赖或把 Maven localRepository 指到可写目录后再跑完整测试。

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
- 本地库当前存在 Flyway 历史迁移不一致：数据库记录的 `V1.10 add user profile` checksum 与当前本地 `V1_10__add_reports_and_hides.sql` 不一致。常规后端启动会被 Flyway validate 拦住。
- 为了临时预览，可用 `--spring.flyway.enabled=false` 启动后端，但这只是本地预览绕过；正式修复应整理迁移版本，不要长期依赖关闭 Flyway。
- 本地开发库曾缺少 `diary_hides` / `user_reports`，导致登录后 `/api/diaries/public`、`today-match`、`coaching`、`community-mood` 返回 403 并被前端拦截器踢回登录页。按当前库的 `BIGINT UNSIGNED` 主键类型补齐表后，公网登录冒烟可通过。
- 当前已验证：`http://127.0.0.1:4173/`、`http://127.0.0.1:18080/api/health`、`https://moodcopilot.dpdns.org/`、`https://moodcopilot.dpdns.org/api/health` 均返回 200；in-app browser 登录后首页公开流有 20 条 `.feed-item`。

## 当前开发方向

`README.md` 已把大并发压测降级为暂不做，当前更适合继续做：

- App 化基础准备：保持 Web/API 路径稳定，补齐关键 E2E 冒烟测试。
- 审核后台：基于现有举报数据，补处理状态、隐藏范围和管理视图。
- 推荐质量优化：曝光去重、同一作者去重、推荐原因说明。

## 已知踩坑

- 不要用 PowerShell 直接运行 `npm`，用 `npm.cmd`。
- 当前环境存在重复 `PATH` / `Path`，会影响 PowerShell 环境枚举、`Start-Process` 重定向和 Maven Wrapper。
- `cloudflared` 隧道配置为 `/api -> localhost:18080`，其他路径 `-> localhost:4173`；如果 4173 不起，公网首页不可访问。
- Vite preview 通过 Cloudflare Tunnel 暴露时会校验 Host。`moodcopilot.dpdns.org` 必须在 `frontend/vite.config.ts` 的 `preview.allowedHosts` 中，否则公网首页返回 403，错误为 `Blocked request. This host (...) is not allowed`。
- 旧版本曾按 PWA/Service Worker 方式缓存页面壳时，访问过旧版的浏览器可能继续被旧 SW 控制，新版本即使移除了注册代码也不会自动卸载旧 SW。当前仓库通过 `frontend/public/sw.js` 提供同路径清退脚本，并在 `frontend/src/main.ts` 启动时注销同源旧 SW、清理 Cache Storage。验证 `/sw.js` 必须返回 `text/javascript`，不能回退成 `index.html`。
- 公开流缓存不要按用户缓存完整结果。应缓存全局公开页，再按当前用户过滤隐藏日记，避免用户发布新公开日记后其他用户缓存不刷新。
