# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此仓库中工作时提供指导。

## 核心规则

1. **完成一个 plan 阶段后，先重构代码，再前后端联调测试，通过后 commit 并 push。**
   - 检查新增代码是否有重复逻辑、是否可以抽取公共方法
   - 检查方法长度是否合理（超过 40 行考虑拆分）
   - 检查是否有未使用的 import、变量、方法
   - 确保前后端都在运行（后端 18080，前端 5173 / 预览 4173）
   - 跑 Playwright E2E 测试确认全链路正常
   - 测试通过后即刻提交并推送
2. **完成一个 plan 阶段后，自动更新 CLAUDE.md 文件**，保持架构、API 路由、踩坑记录等内容与实际代码一致。
3. **所有命令必须使用中文。** 与用户的交流、commit message、代码注释、plan 文件、CLAUDE.md 等均用中文。

## 项目概述

MoodCopilot 是一个 AI 情绪日记 + 陌生人互助社区。用户写日记，AI 分析情绪，用户可以选择将日记公开，让相似心情的人回应和共鸣。

- 后端：Spring Boot 3.5.14、Java 21、MySQL 8、Redis (Lettuce)、MyBatis-Plus 3.5.10.1
- 前端：Vue 3、Vite 5、TypeScript、Naive UI 2.41、Pinia 2.2、Vue Router 4.4
- 设计：Warm Precision — 暖石色底 + 纯白卡片 + 鼠尾草绿强调 + 几何 M logo + 系统无衬线字体
- AI：Spring AI 1.0.0-M6 + DeepSeek API（OpenAI 兼容），失败时回退关键词分析

## 构建与运行

### 环境变量（仓库根目录的 .env）

```bash
# 启动后端前需要先导出环境变量：
eval $(cat /d/Code/MoodCopilot/.env | tr -d '\r' | grep -v '^#' | grep -v '^$' | sed 's/^/export /')
cd backend/moodcopilot
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

后端运行在 **18080** 端口（dev profile）。健康检查：`GET /api/health`

```bash
# 前端
cd frontend
npm install
npx vite --host         # dev server 运行在 5173 端口，/api 代理到 localhost:18080
npx vue-tsc --noEmit    # 仅类型检查
npm run build           # 生产构建 → dist/
npx vite preview --host --port 4173  # 生产预览
```

### 测试账号

邮箱：`test@test.com`  密码：`123456`

如果页面提示无法登录，先直连后端确认账号链路：

```powershell
$body = @{ email='test@test.com'; password='123456' } | ConvertTo-Json
Invoke-RestMethod -Uri http://127.0.0.1:18080/api/auth/login -Method Post -ContentType 'application/json' -Body $body
```

当前本地开发库中该账号存在且密码哈希非空；2026-05-11 已验证直连登录返回 200。

## 架构

### 后端：按功能分包

```
src/main/java/com/moodcopilot/
├── ai/              ChatService、ChatController、AiAnalysisService、MemoryExtractionService、DailyFollowUpScheduler
├── config/           SecurityConfig、MybatisPlusConfig、RedisConfig、AIConfiguration、
│                    SchedulingConfig（@EnableScheduling）、WebMvcConfig（静态资源映射）
├── auth/            AuthController、AuthService、RegisterRequest/LoginRequest/AuthResponse
├── common/          ApiResponse<T>、RateLimitException、GlobalExceptionHandler（@RestControllerAdvice）
├── follow/          FollowController、FollowService
├── diary/           DiaryController、DiaryService、DiaryView、DiaryComment、WeeklyReportView、CreateDiaryRequest
├── summary/          SummaryController、SummaryService、SummaryView
├── entity/          UserEntity（含 avatar、dailyNotifyEnabled）、DiaryEntity（@TableLogic）、DiaryAnalysisEntity、
│                    DiaryCommentEntity、DiaryResonanceEntity、NotificationEntity、FollowEntity、
│                    DiarySummaryEntity、ChatConversationEntity、UserProfileMemoryEntity
├── health/          HealthController
├── mapper/          MyBatis-Plus BaseMapper 接口（共 9 个）
├── notification/    NotificationService、NotificationController
└── security/        JwtTokenProvider、JwtAuthenticationFilter、RateLimitService（AI 调用限流）
```

### 前端：Vue 3 单页应用

```
public/
│   manifest.json、sw.js、icon-192.svg（M logo）    PWA 支持
e2e/
│   smoke-test.mjs、visual-polish-smoke.mjs    Playwright E2E 冒烟测试
src/
├── api/index.ts         拦截器 + diaryApi/authApi/notificationApi/followApi/summaryApi/chatApi
├── components/          10 个：AppHeader、BottomNav（6 Tab）、DiaryComposer、AiAnalysisCard、
│                        SimilarDiariesPanel、MyDiaryList、PublicFeed、DiaryFeedItem、
│                        ReferenceBar、LoadingSkeleton
├── pages/               10 个：SquarePage、WritePage、LoginPage、RegisterPage、DiaryDetailPage、
│                        ReportPage、FollowingPage、ChatPage、SettingsPage（个人中心）
├── router/index.ts      9 条路由（新增 /settings），beforeEach 守卫
├── stores/              auth.ts（含 avatar、dailyNotifyEnabled）、diary.ts、notification.ts、follow.ts
└── styles.css           全局 CSS：Warm Precision 设计系统
```

### API 路由（除白名单外均需 JWT Bearer token）

| Method | Path | 需要认证 | 说明 |
|--------|------|---------|------|
| ANY | `/api/health`、`/api/auth/**`、`/uploads/**`、`/swagger-ui/**`、`/v3/api-docs/**` | 否 | |
| POST | `/api/auth/register`、`/api/auth/login` | 否 | |
| GET | `/api/auth/me` | 是 | 含 avatar、dailyNotifyEnabled |
| POST | `/api/auth/update-profile` | 是 | 修改用户名/头像 |
| POST | `/api/auth/avatar` | 是 | 上传头像文件（512KB/JPEG/PNG/WebP） |
| PUT | `/api/auth/settings` | 是 | 开关每日通知 |
| GET | `/api/auth/quota` | 是 | 当日 AI 余量 |
| POST | `/api/diaries` | 是 | |
| GET | `/api/diaries/mine`、`/api/diaries/public?page=&size=`、`/api/diaries/following?page=&size=` | 是 | |
| GET | `/api/diaries/weekly-report?weekOffset=`、`/api/diaries/monthly-report?monthOffset=` | 是 | |
| GET | `/api/diaries/{id}`、`/api/diaries/{id}/similar?limit=` | 是 | |
| GET | `/api/diaries/today-status`、`/api/diaries/today-match` | 是 | |
| GET | `/api/diaries/coaching`、`/api/diaries/community-mood` | 是 | |
| POST | `/api/diaries/{id}/comments`（body: `{content, parentCommentId}`） | 是 | |
| POST | `/api/diaries/{id}/resonance` | 是 | |
| GET | `/api/diaries/{id}/encourage-candidates` | 是 | |
| GET | `/api/notifications`、`/api/notifications/unread-count` | 是 | |
| PUT | `/api/notifications/{id}/read` | 是 | |
| POST | `/api/follows/{userId}` | 是 | |
| DELETE | `/api/follows/{userId}` | 是 | |
| GET | `/api/follows/{userId}/status` | 是 | |
| GET | `/api/chat/conversations` | 是 | |
| POST | `/api/chat/conversations` | 是 | |
| DELETE | `/api/chat/conversations/{id}` | 是 | |
| POST | `/api/chat/conversations/{id}`（SSE 流式） | 是 | |
| GET/PUT | `/api/chat/conversations/{id}/history` | 是 | |
| POST | `/api/summaries` | 是 | |
| GET | `/api/summaries` | 是 | |
| DELETE | `/api/summaries/{id}` | 是 | |

### 数据库

MySQL 8，Flyway 迁移脚本位于 `src/main/resources/db/migration/`（当前最新 V1_14）。表：`users`（含 avatar、daily_notify_enabled）、`diaries`、`diary_analysis`、`diary_comments`、`diary_resonances`、`notifications`（message 列 TEXT 类型）、`follows`、`diary_summaries`、`chat_conversations`、`user_profile_memory`。

### AI 分析流程

1. `POST /api/diaries` → 保存日记，返回 `analysis: null`
2. `@Async runAiAnalysis()` 后台调 DeepSeek API，结果写入 `diary_analysis`（消耗 ANALYSIS 额度）
3. 分析成功后继续触发 `MemoryExtractionService`，结合新日记和旧属性刷新 `user_profile_memory`
4. 前端每 2 秒轮询 `GET /api/diaries/{id}`，直到 `analysis != null`
5. DeepSeek 失败 → 回退关键词匹配（6 种情绪、5 个主题）

### AI 调用限流

`RateLimitService` 用 Redis 计数器按用户 + 日期 + 类型限流，超限抛出 `RateLimitException` → 返回 429。

| 类型 | 每日限额 | 触发场景 |
|------|---------|---------|
| `CHAT` | 30 | AI 聊天消息 |
| `ANALYSIS` | 10 | 日记分析 |
| `REPORT` | 5 | 周报/月报 AI 总结 |
| `COACHING` | 10 | 陪跑建议 + 每日通知 |
| `ENCOURAGEMENT` | 15 | 鼓励语生成 |

Key 格式：`ratelimit:{userId}:{yyyy-MM-dd}:{type}`，TTL 到次日凌晨。

### 每日跟进通知

`DailyFollowUpScheduler`（cron: `0 0 6 * * *`）每天早上 6:00：
1. 查询 `daily_notify_enabled=true` 且昨天有日记的用户
2. 拉取最近 7 篇日记 + 分析，调用 AI 生成陪跑建议
3. 消耗一次 `COACHING` 额度
4. 创建 `type=SYSTEM` 通知（无 diaryId，点击跳转广场）
5. 额度不足或失败的跳过

### 评论：两级平铺结构

`diary_comments` 表有 `parent_comment_id` 和 `root_comment_id`。所有回复平铺在根评论下方，最多两级。

### AI 对话架构

- **SSE 流式**：后端 `Flux<String>` → 前端 `XMLHttpRequest` + `onprogress` + `onloadend`
- **ChatMemory**：`ConcurrentHashMap` 按 `userId:conversationId` 隔离
- **历史持久化**：Redis `chat:msgs:{convId}`，TTL 7 天
- **长记忆注入**：`ChatController` 会先读取 `user_profile_memory`，再把“性格 / 长期目标 / 关键人物”等背景知识拼进 system prompt
- **上下文**：引用内容 + 最近 10 篇原始日记（不读总结防止幻觉）
- **AI 回复简短化**：system prompt 限制 2-3 句

### 设计系统

**Warm Precision**：暖调精炼，干净几何，呼吸感白。

- **色彩**：背景 `#F8F6F2`（暖石色）、卡片 `#FFFFFF`、主色 `#5B7C6B`（鼠尾草绿）、文字 `#1C1C1C`
- **字体**：系统无衬线栈 `PingFang SC / Microsoft YaHei / Hiragino Sans GB`
- **Logo**：几何 M SVG 图标（两条斜线交汇）+ "MoodCopilot" 文字
- **图标**：`icon-192.svg` 绿底 M logo（替换旧「印」字图标）
- **卡片**：纯白 + 1px 边框，hover 浮现阴影
- **无情绪标签展示**：公开日记流、日记详情（非本人）、社区共鸣均不显示情绪/主题标签
- **认证页**：纯色背景 + 居中白色卡片，无圆圈装饰

### Redis 缓存

| 缓存 Key | TTL | 失效触发 |
|------|------|------|
| `report:{userId}:{weekOffset}` | 30min | 用户写/删日记 |
| `report:monthly:{userId}:{monthOffset}` | 30min | 用户写/删日记 |
| `public:diaries:{page}:{size}` | 5min | 新公开日记 |
| `following:{userId}:{page}:{size}` | 5min | 新日记/关注变化 |
| `coaching:{userId}` | 15min | 用户写/删日记 |
| `chat:msgs:{conversationId}` | 7d | 用户删除会话 |
| `ratelimit:{userId}:{date}:{type}` | 次日凌晨 | 自动过期 |

缓存失效：`DiaryService.evictUserCache()` 用精确 key 删除，不用 `redisTemplate.keys()`。

### 个人中心

- `/settings` 路由，底部 Tab「👤 我的」
- 头像上传（POST /api/auth/avatar）、用户名编辑（POST /api/auth/update-profile）
- 每日跟进通知开关（PUT /api/auth/settings）
- 退出登录
- 头像文件存 `backend/moodcopilot/uploads/avatars/`，通过 `/uploads/**` 静态映射访问

### PWA + 响应式 UI

- **PWA**：`manifest.json`（`display: standalone`）、`sw.js`（缓存壳）、`icon-192.svg`（M logo）
- **底部 Tab**（`BottomNav.vue`）：6 Tab（广场/写日记/AI/关注/报告/我的），仅移动端显示
- **桌面端**：顶栏导航链接 + 标语 + 用户名→个人中心入口，`≥769px` 显示
- **响应式**：CSS 媒体查询 `.desktop-only`（≥769px 不设 display，≤768px 隐藏）

### 生产构建与对外分享

```bash
cd frontend && npx vite build
npx vite preview --host --port 4173
cloudflared tunnel --config ~/.cloudflared/moodcopilot-config.yaml run moodcopilot
# HTTPS: https://moodcopilot.dpdns.org
```

**cloudflared 配置**：`/api` → `:18080`（避免 SSE 缓冲），其余 → `:4173`。`protocol: http2`。

公网优先使用一键脚本，避免重复排障：

```powershell
cd D:\Code\MoodCopilot
npm.cmd run public:start
npm.cmd run public:restart
```

### 登录和公网可用性排障

登录失败不要先假设是前端表单或密码校验 bug。先按链路排查：

```powershell
Test-NetConnection 127.0.0.1 -Port 18080
Test-NetConnection 127.0.0.1 -Port 4173
Invoke-WebRequest http://127.0.0.1:18080/api/health -UseBasicParsing
Invoke-WebRequest https://moodcopilot.dpdns.org/api/health -UseBasicParsing
Get-Process | Where-Object { $_.ProcessName -like '*cloudflared*' }
```

若公网 `/api/health` 返回 `530`，而本地后端和前端预览可用，通常是 `cloudflared` 没运行。启动：

```powershell
cloudflared tunnel --config C:\Users\renpe\.cloudflared\moodcopilot-config.yaml run moodcopilot
```

2026-05-11 排障结论：当 `18080`、`4173`、`cloudflared` 都在线时，本地和公网登录页均可用，`POST /api/auth/login` 返回 200，随后 `/api/diaries/public`、`/api/diaries/today-match`、`/api/notifications/unread-count` 也返回 200。

## 重要踩坑记录

- **绝不要在 Filter 类上加 `@Component`。** 用 `SecurityConfig` 中 `@Bean` 创建 + `FilterRegistrationBean.setEnabled(false)`。
- **`@Cacheable` + Redis 与 Security 冲突** → 改用 `StringRedisTemplate` 手动缓存。
- **Spring Security 默认拦截 PUT 请求**。即使 `.permitAll()` 也不放行。改为 POST 或显式配置。本次 `/api/auth/profile`（PUT）返回 403，改为 POST `/api/auth/update-profile` 解决。
- **axios 手动设 `Content-Type: multipart/form-data` 会丢失 boundary**。浏览器需要自动生成 `boundary=----WebKitFormBoundary...`。上传文件时不传 headers。
- **CSS 中 `.desktop-only { display: revert }` 会覆盖 flex 布局**。`.nav-links` 的 `display: flex` 被后来的 `display: revert` 覆盖变成 `block`。正确做法：`desktop-only` 不设 display，只在 media query 中 `display: none`。
- **`n-segmented` 在 Naive UI 中不存在。** 用 `<n-radio-group>` + `<n-radio-button>`。
- **不要提交 `dist/` 到 git。** `.gitignore` 已包含 `frontend/dist/`。
- **Windows bash 的 curl 损坏中文 UTF-8**。API 测试用 ASCII 内容或 Playwright。
- **MyBatis-Plus 3.5.10 缺少 `PaginationInnerInterceptor`** → 用 3.5.10.1 + `mybatis-plus-jsqlparser`。
- **Vite 代理缓冲 SSE** → SSE 端点直连后端 `localhost:18080`，不走 Vite。
- **`fetch` + `ReadableStream` 在 Vite 代理下 `reader.read()` 永不 `done`** → 改用 `XMLHttpRequest`。
- **`allowCredentials(true)` 不能用 `allowedOrigins("*")`** → 用 `allowedOriginPatterns("*")`。
- **工作树中 `.env` 路径**：从 `backend/moodcopilot` 到仓库根的 `.env` 是 `/d/Code/MoodCopilot/.env`。
- **公网登录 530 不是登录代码 bug。** 先查 `cloudflared`、`18080` 和 `4173`。只要公网 health 不是 200，登录页通常无法正常调用 `/api/auth/login`。

## E2E 测试

前置条件：后端 `18080` 和前端生产预览 `4173` 均已启动。公网验证还需要 `cloudflared` 隧道在线。

```powershell
cd D:\Code\MoodCopilot
npm.cmd run e2e:smoke
npm.cmd run e2e:visual-polish
```
