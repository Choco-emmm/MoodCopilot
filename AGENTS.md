# AGENTS.md

本文件已合并原 AGENTS.md 与 CLAUDE.md，作为 MoodCopilot 仓库的唯一代理执行规范与项目事实来源。

## 强制执行规则（最高优先级）

1. 遇到任何需求或 Bug，直接修改文件或给出最终可运行代码。
2. 绝对禁止进行假设推演。
3. 绝对禁止输出调查报告。
4. 绝对禁止跑测试脚本。
5. 绝对禁止多步自我验证。
42. **极简测试原则：** 当老板明确要求你测试时，只允许生成并执行基础的 HTTP 请求命令（如 `curl` 或 PowerShell 的 `Invoke-RestMethod`）来验证接口是否返回 200 及预期数据。
	- 绝对禁止运行前端 E2E 测试脚本。
	- 绝对禁止检查、修改或尝试重启本机的 MySQL/Redis 等底层服务进程。
	- 绝对禁止修改系统环境变量或启动脚本。
	- 如果你的 HTTP 测试请求失败，最多自行修改代码并重试 2 次，如果仍失败，请立即停止动作，直接向老板输出报错日志。

## 基本协作规则

- 所有说明、提交信息、计划文档和代码注释优先使用中文。
- 保持改动最小、准确，以仓库现有代码和已知事实为依据。
- 不要提交 `frontend/dist/`、临时日志、`.maven-*` 下载内容或其他自动生成文件。
- 工作树可能已有用户或其他代理改动；不要回滚无关改动。
- 如果不确定，添加带简短原因的 TODO，不要编造接口、命令或结论。

## 项目概述

MoodCopilot 是一个 AI 情绪日记 + 陌生人互助社区。用户写日记，AI 分析情绪，用户可选择公开，让相似心情的人回应和共鸣。

- 后端：Spring Boot 3.5.14、Java 21、MySQL 8、Redis（Lettuce）、MyBatis-Plus 3.5.10.1
- 前端：Vue 3、Vite 5、TypeScript、Naive UI 2.41、Pinia 2.2、Vue Router 4.4
- 设计：Warm Precision（暖石色底 + 纯白卡片 + 鼠尾草绿强调）
- AI：Spring AI 1.0.0-M6 + DeepSeek API（OpenAI 兼容），失败时回退关键词分析

## 架构

### 后端分包

```
src/main/java/com/moodcopilot/
├── ai/              ChatService、ChatController、AiAnalysisService、DailyFollowUpScheduler
├── config/          SecurityConfig、MybatisPlusConfig、RedisConfig、AIConfiguration、SchedulingConfig、WebMvcConfig
├── auth/            AuthController、AuthService、RegisterRequest/LoginRequest/AuthResponse
├── common/          ApiResponse<T>、RateLimitException、GlobalExceptionHandler
├── follow/          FollowController、FollowService
├── diary/           DiaryController、DiaryService、DiaryView、DiaryComment、WeeklyReportView、CreateDiaryRequest
├── summary/         SummaryController、SummaryService、SummaryView
├── entity/          UserEntity、DiaryEntity、DiaryAnalysisEntity、DiaryCommentEntity、DiaryResonanceEntity 等
├── health/          HealthController
├── mapper/          MyBatis-Plus BaseMapper 接口
├── notification/    NotificationService、NotificationController
└── security/        JwtTokenProvider、JwtAuthenticationFilter、RateLimitService
```

### 前端结构

```
frontend/src/
├── api/index.ts
├── components/      AppHeader、BottomNav、DiaryComposer、PublicFeed 等
├── pages/           SquarePage、WritePage、ChatPage、AdminReportsPage、SettingsPage 等
├── router/index.ts
├── stores/          auth.ts、diary.ts、notification.ts、follow.ts
└── styles.css
```

## API 要点

- 白名单：`/api/health`、`/api/auth/**`、`/uploads/**`、`/swagger-ui/**`、`/v3/api-docs/**`
- 日记：`/api/diaries`、`/api/diaries/public`、`/api/diaries/following`、`/api/diaries/{id}`
- 聊天：`/api/chat/conversations`、`/api/chat/conversations/{id}`（SSE）、`/api/chat/conversations/{id}/reply`（公网兜底）
- 额度：`/api/user/quota`
- 审核后台：`/api/admin/reports/**`，前端页面 `/admin/reports`

## 近期核心变更（2026-05）

- 鉴权与登录跳转：前端全局拦截器仅在 `401` 时清 token 并跳登录；`403`（如报告访问拒绝）不再强制跳登录，由页面自行提示错误。
- 额度体系收敛：`ENCOURAGEMENT` 下线，`COACHING` 并入 `ANALYSIS`，当前仅保留 `CHAT / ANALYSIS / REPORT`。
- 额度接口稳定化：补齐 `GET /api/user/quota`，并将该接口从“失败即登出”逻辑中排除。
- Redis 计数修复：额度计数键改为字符串序列化并加解析兼容，修复“额度显示不扣减”。
- 头像链路修复：上传后头像地址标准化，导航栏优先展示图片头像。
- 报告页策略调整：取消“继续聊”入口；相关日记缺失片段支持按 `diaryId` 回填；月报趋势图增加情绪颜色点位与强度标注。
- 聊天引用策略：保留引用能力（最多 2 条，单条限长），用于“围绕具体内容继续聊”。
- 聊天上下文重构：不再在每次聊天请求中拼接最近 10 篇日记；改为日记分析完成后增量生成并更新用户专属上下文（Redis key: `chat:user-context:{userId}`），聊天时自动注入该背景。

## 运行与链路事实（Windows）

- PowerShell 下使用 `npm.cmd` / `npx.cmd`，不要直接用 `npm`。
- 当前环境 `mvnw.cmd` 可能受重复 `PATH/Path` 影响，优先 `cmd /c mvn.cmd ...`。
- 公网 `https://moodcopilot.dpdns.org/` 依赖三段链路同时在线：
	1. 后端 `18080`
	2. 前端预览 `4173`
	3. `cloudflared` 隧道（配置：`C:\Users\renpe\.cloudflared\moodcopilot-config.yaml`）

### 视觉冒烟验证

前置条件：后端 `18080` 和前端预览 `4173` 已启动。

```powershell
cd D:\Code\MoodCopilot
npm.cmd run e2e:visual-polish
```

验证范围：移动端写日记草稿恢复、公开流禁用文案检查、移动端聊天输入可见、桌面广场截图。

## 手机端聊天已验证结论

- 前端业务代码不得写死 `http://localhost:18080/api`。
- 历史读写统一走 `chatApi.getHistory()` / `chatApi.saveHistory()`（同源 `/api`）。
- 公网/手机端使用 `POST /api/chat/conversations/{id}/reply`，不要依赖 SSE 流式接口。
- 已验证手机端聊天链路：`reply` 请求 200，且无 `localhost:18080` 请求。

## 当前开发方向

- App 化基础准备：保持 Web/API 路径稳定，补齐关键冒烟覆盖。
- 审核后台增量完善：状态筛选、隐藏范围细化、管理视图可读性。
- 推荐策略微调：继续参数优化，不做推荐理由展示。
- 社区卡片保持轻量正文流：不展示 AI 主题/分类标签，不提供“看分析”入口。

## 已知踩坑

- 不要在 Filter 类上加 `@Component`，改用 `SecurityConfig` 的 `@Bean` 管理。
- `@Cacheable` + Redis 与 Security 冲突时，改用 `StringRedisTemplate` 手动缓存。
- 上传文件时不要手动设置 `multipart/form-data` 的 `Content-Type`。
- `preview.allowedHosts` 必须包含 `moodcopilot.dpdns.org`，否则公网首页会 403。
- 旧版 Service Worker 可能残留，需通过 `frontend/public/sw.js` 和 `frontend/src/main.ts` 清退。
- 报告页不要在首屏同时拉周报和月报，避免触发 `REPORT` 限额与未处理异常。
