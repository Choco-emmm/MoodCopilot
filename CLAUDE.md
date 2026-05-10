# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此仓库中工作时提供指导。

## 核心规则

1. **完成一个 plan 阶段后，自动执行前后端联调测试，通过后 commit 并 push。**
   - 确保前后端都在运行（后端 18080，前端 5173）
   - 跑 Playwright E2E 测试确认全链路正常
   - 测试通过后即刻提交并推送
2. **完成一个 plan 阶段后，自动更新 CLAUDE.md 文件**，保持架构、API 路由、踩坑记录等内容与实际代码一致。
3. **所有命令必须使用中文。** 与用户的交流、commit message、代码注释、plan 文件、CLAUDE.md 等均用中文。

## 项目概述

MoodCopilot 是一个 AI 情绪日记 + 陌生人互助社区。用户写日记，AI 分析情绪，用户可以选择将日记公开，让相似心情的人回应和共鸣。

- 后端：Spring Boot 3.5.14、Java 21、MySQL 8、Redis (Lettuce)、MyBatis-Plus 3.5.10.1
- 前端：Vue 3、Vite 5、TypeScript、Naive UI 2.41、Pinia 2.2、Vue Router 4.4
- 设计：纸墨之间 (Between Ink & Paper) — 纸质纹理 + 墨色层次 + 楷体标题 + 印章红点缀 + 玉绿行动色
- AI：Spring AI 1.0.0-M6 + DeepSeek API（OpenAI 兼容），失败时回退关键词分析

## 构建与运行

### 环境变量（仓库根目录的 .env）

```bash
# 启动后端前需要先导出环境变量：
eval $(cat .env | tr -d '\r' | grep -v '^#' | grep -v '^$' | sed 's/^/export /')
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
```

### 测试账号

邮箱：`test@test.com`  密码：`123456`

## 架构

### 后端：按功能分包

```
src/main/java/com/moodcopilot/
├── ai/              ChatService、ChatController、AiAnalysisService（Spring AI ChatClient + 关键词回退）
├── config/           SecurityConfig、MybatisPlusConfig、RedisConfig、AIConfiguration（ChatClient Bean、ChatMemory、aiExecutor）
├── auth/            AuthController、AuthService、RegisterRequest/LoginRequest/AuthResponse
├── common/          ApiResponse<T> 统一响应包装 { code, message, data }
├── follow/          FollowController、FollowService
├── diary/           DiaryController、DiaryService、DiaryView、DiaryComment、WeeklyReportView（含 diaryIds 溯源）、CreateDiaryRequest
├── summary/          SummaryController、SummaryService、SummaryView（含 diaryIds）
├── entity/          MyBatis-Plus 实体：UserEntity、DiaryEntity（@TableLogic）、DiaryAnalysisEntity、
│                    DiaryCommentEntity、DiaryResonanceEntity、NotificationEntity、FollowEntity、
│                    DiarySummaryEntity（含 diaryIds JSON）、ChatConversationEntity
├── health/          HealthController
├── mapper/          MyBatis-Plus BaseMapper 接口（共 8 个）
├── notification/    NotificationService、NotificationController
└── security/        JwtTokenProvider、JwtAuthenticationFilter
```

### 前端：Vue 3 单页应用

```
public/
│   manifest.json、sw.js、icon-192.svg    PWA 支持
src/
├── api/index.ts         Axios 实例，拦截器（JWT 附加、401/403→跳到/login）、diaryApi、authApi、
│                        notificationApi、followApi、summaryApi、chatApi
├── components/          9 个组件：AppHeader、BottomNav（底部 Tab 栏）、DiaryComposer、AiAnalysisCard、
│                        SimilarDiariesPanel、MyDiaryList、PublicFeed（瀑布流）、DiaryFeedItem、ReferenceBar（聊天引用栏）
├── pages/               SquarePage（`/` 广场瀑布流）、WritePage（`/write` 写日记+我的日记）、LoginPage、
│                        RegisterPage、DiaryDetailPage、ReportPage（周报+自定义总结，情绪趋势可溯源）、
│                        FollowingPage、ChatPage（多对话）
├── router/index.ts      8 条路由，beforeEach 守卫（requiresAuth→跳转/login）
├── stores/              auth.ts、diary.ts、notification.ts、follow.ts
└── styles.css           全局 CSS（无 scoped 样式）
```

### API 路由（除白名单外均需 JWT Bearer token）

| Method | Path | 需要认证 |
|--------|------|---------|
| ANY | `/api/health`、`/api/auth/**`、`/swagger-ui/**`、`/v3/api-docs/**` | 否 |
| POST | `/api/auth/register`、`/api/auth/login` | 否 |
| GET | `/api/auth/me` | 是 |
| POST | `/api/diaries` | 是 |
| GET | `/api/diaries/mine`、`/api/diaries/public?page=&size=`、`/api/diaries/following?page=&size=`、`/api/diaries/weekly-report?weekOffset=` | 是 |
| GET | `/api/diaries/{id}`、`/api/diaries/{id}/similar?limit=` | 是 |
| POST | `/api/diaries/{id}/comments`（body: `{content, parentCommentId}`） | 是 |
| POST | `/api/diaries/{id}/resonance` | 是 |
| GET | `/api/notifications`、`/api/notifications/unread-count` | 是 |
| PUT | `/api/notifications/{id}/read` | 是 |
| POST | `/api/follows/{userId}` | 是 |
| DELETE | `/api/follows/{userId}` | 是 |
| GET | `/api/follows/{userId}/status` | 是 |
| GET | `/api/chat/conversations` | 是 |
| POST | `/api/chat/conversations` | 是 |
| DELETE | `/api/chat/conversations/{id}` | 是 |
| POST | `/api/chat/conversations/{id}`（SSE 流式） | 是 |
| GET | `/api/chat/conversations/{id}/history` | 是 |
| PUT | `/api/chat/conversations/{id}/history` | 是 |
| POST | `/api/summaries` | 是 |
| GET | `/api/summaries` | 是 |
| DELETE | `/api/summaries/{id}` | 是 |

### 数据库

MySQL 8，Flyway 迁移脚本位于 `src/main/resources/db/migration/`（当前最新 V1_8）。表：`users`、`diaries`、`diary_analysis`、`diary_comments`、`diary_resonances`、`notifications`、`follows`、`diary_summaries`、`chat_conversations`。

MyBatis-Plus 配置：`is_deleted` 字段使用 `@TableLogic`，主键使用 `@TableId(type=IdType.AUTO)`，JSON 列使用 `JacksonTypeHandler`。分页需要 `PaginationInnerInterceptor`（在 `MybatisPlusConfig` 中配置）。

### AI 分析流程

1. `POST /api/diaries` → 立即保存日记，返回 `analysis: null`
2. `@Async runAiAnalysis()` 在后台调用 DeepSeek API，结果写入 `diary_analysis`
3. 前端每 2 秒轮询 `GET /api/diaries/{id}`，直到 `analysis != null`
4. DeepSeek 失败 → 回退到中文关键词匹配（6 种情绪、5 个主题）

### 评论：两级平铺结构

`diary_comments` 表有 `parent_comment_id`（回复了谁）和 `root_comment_id`（锚定顶级评论）。所有回复平铺在根评论下方，最多两级，不会无限嵌套。

### AI 对话架构

- **多对话管理**：MySQL `chat_conversations` 表存会话元数据（id, user_id, title），支持新建/切换/删除
- **SSE 流式**：`POST /api/chat/conversations/{id}` 返回 `Flux<String>`（`text/event-stream`），前端用 `XMLHttpRequest` 直连 `localhost:18080` 消费
- **ChatMemory**：`Map<String, ChatMemory>` 按 `userId:conversationId` 隔离，每个会话独立记忆
- **历史持久化**：`PUT/GET /api/chat/conversations/{id}/history` 存 Redis（key=`chat:msgs:{convId}`），TTL 7 天
- **自动标题**：首条用户消息前 20 字自动设为会话标题
- **上下文**：只注入原始日记（最近 10 篇），不读总结防止幻觉传递
- **Markdown**：前端用 `marked` 渲染 AI 回复，AI prompt 指引使用基本 Markdown 格式

### 日记溯源

- 周报 `WeeklyReportView.DailyMood` 包含 `diaryIds`，前端情绪趋势行可点击跳转日记详情
- 总结库 `diary_summaries` 的 `diary_ids` JSON 列存关联日记 ID，`SummaryView.diaryIds` 返回到前端
- 自定义总结卡片底部显示可点击的日记链接

### Docker 部署

```bash
docker compose up -d    # 启动全栈（MySQL + Redis + 后端 + 前端）
# 前端 http://localhost:80
# 后端 http://localhost:18080
```

Docker 资源限制：MySQL 512MB（InnoDB buffer pool 128MB / max_connections 50）、Redis 128MB、Backend 768MB / 1 CPU。JVM 参数：`-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxRAMPercentage=75.0`。

### 性能优化

**批量查询：** `DiaryService.batchLoadAnalyses()` 和 `batchLoadComments()` 用 `selectBatchIds` / `IN` 批量加载，消除 N+1。`buildDiaryView(diary, isPublic, analysisMap, commentMap)` 接受预加载的 Map。公开日记 20 篇从 41 次查询降至 3 次。

**相似日记：** `similar()` 候选池上限 200 篇，批量加载分析，每人最多 1 篇去重。

**分页：** `myDiaries()` 使用 MyBatis-Plus `Page` 分页（上限 50 条），`/api/diaries/mine` 接受 `page`/`size` 参数。

**连接池：** HikariCP `maximum-pool-size=30`，Redis Lettuce `max-active=30`。

**压测：** k6 脚本位于 `backend/moodcopilot/stress-test/`，包含 setup（数据准备）、smoke（冒烟）、load（20 VU 负载）、stress（50 VU 压力）。

```bash
"/c/Program Files/k6/k6.exe" run backend/moodcopilot/stress-test/k6-smoke.js
```

### 匿名鼓励

- `GET /api/diaries/{id}/encourage-candidates` — AI 生成 3 句匿名鼓励候选
- `POST /api/diaries/{id}/resonance` — 发送鼓励 (body: `{message}`)，共鸣 (无 body 或不含 message)
- 通知类型 `ENCOURAGEMENT`，不暴露发送者身份

### 每日跟进 + 今日同频

- `GET /api/diaries/today-status` — 返回今日记录状态、连续天数、昨天情绪
- `GET /api/diaries/today-match` — 推荐一篇情绪相似的公开日记
- 广场顶部显示状态卡片和同频推荐（显示日记内容摘要，不直接显示情绪标签）

### 陪跑 + 聊天引用栏

- `GET /api/diaries/coaching` — AI 陪跑建议，Redis 缓存（key=`coaching:{userId}`，TTL 15min），写日记时 evict
- 广场陪跑面板新增「和 AI 聊聊这个话题」入口，点击跳转聊天页并传递陪跑建议作为引用
- 聊天输入框上方新增引用栏（`ReferenceBar.vue` 组件），支持：
  - 自动接收广场传过来的陪跑引用
  - 「+ 引用日记」从最近 7 篇日记中添加引用
  - 每个引用以可移除卡片展示
- `POST /api/chat/conversations/{id}` SSE body 新增 `references` 字段（`List<String>`），拼入 AI system prompt

### 设计系统

- **纸墨之间 (Between Ink & Paper)**：纸质纹理（SVG 噪点）+ 墨色层次 + 楷体标题 + 印章红点缀 + 玉绿行动色
- Naive UI 主题覆盖：主色玉绿 `#4a7c62`，辅色印章红 `#b5343a`
- 页面过渡：`fade+slide` 动画 (Transition mode="out-in")
- 认证页：径向渐变背景 + 圆形「印」字装饰（倾斜 8° 仿真印章）

### Redis 缓存

用 `StringRedisTemplate` 手动缓存（不用 `@Cacheable` 避免 AOP 与 Security 冲突）。

| 缓存 Key | TTL | 失效触发 |
|------|------|------|
| `report:{userId}:{weekOffset}` | 30min | 用户写/删日记 |
| `report:monthly:{userId}:{monthOffset}` | 30min | 用户写/删日记 |
| `public:diaries:{page}:{size}` | 5min | 新公开日记 |
| `following:{userId}:{page}:{size}` | 5min | 新日记/关注变化 |
| `coaching:{userId}` | 15min | 用户写/删日记 |
| `chat:msgs:{conversationId}` | 7d | 用户删除会话 |

缓存失效：`DiaryService.evictUserCache()` 用精确 key 删除（遍历已知分页组合），不用 `redisTemplate.keys()` 避免 O(N) SCAN 阻塞。

### CORS

SecurityConfig 已配置 CORS（`allowedOriginPatterns("*")` + `allowCredentials(true)`），ChatPage 直连后端绕过 Vite 代理的 SSE 缓冲问题。

### PWA + 响应式 UI

- **PWA 可安装**：`public/manifest.json`（`display: standalone` 全屏模式）、`public/sw.js`（Service Worker 缓存壳）、`public/icon-192.svg`（玉绿方底「印」字图标）
- **底部 Tab 栏**（`BottomNav.vue`）：5 个 Tab（广场/写日记/AI/关注/报告），仅在移动端（≤768px）显示
- **桌面端**：保留原版顶栏导航链接 + 标语「写下今天，慢慢理解自己。」（≥769px 显示）
- **响应式**：CSS 媒体查询 `.desktop-only` / 移动端 `.app-shell` 底部 padding 适配安全区
- Apple 兼容：`apple-mobile-web-app-capable` + `apple-touch-icon`

### 生产构建与对外分享

**Cloudflare Tunnel**：用 `cloudflared` 把本地服务暴露到 HTTPS 域名，分享给朋友。

```bash
# 生产构建（压缩 + 代码分割）
cd frontend && npx vite build

# 启动生产预览（端口 4173）
npx vite preview --host --port 4173

# Cloudflare 隧道（配置见 ~/.cloudflared/moodcopilot-config.yaml）
cloudflared tunnel --config moodcopilot-config.yaml run moodcopilot
```

**cloudflared 配置关键**：`/api` 路由直连后端 `:18080`，避免 SSE 被代理缓冲截断。其余请求走 Vite preview `:4173`。

**SSE 直连策略**：`ChatPage.vue` 检测 `window.location.hostname`，本地（`localhost`）用 `http://localhost:18080/api`，远程走同源 `/api`。`onloadend` 补调 `processSSE()` 防止最后一段数据丢失。

## 重要踩坑记录

- **绝不要在 Filter 类上加 `@Component`。** Spring Boot 会自动将其注册为全局 servlet 过滤器，绕过 Spring Security 过滤器链。正确做法是在 `SecurityConfig` 中用 `@Bean` 创建，并用 `FilterRegistrationBean.setEnabled(false)` 禁用自动注册。
- **`@Cacheable` + Redis 与 Spring Security 冲突**，会导致受保护接口返回 403。**已解决**：改用 `StringRedisTemplate` 手动缓存，AOP 代理不会干扰 Security 过滤器链。
- **`n-segmented` 在 Naive UI 中不存在。** 用 `<n-radio-group>` + `<n-radio-button>` 实现分段控件效果。
- **不要提交 `dist/` 或 `src/` 中的 `*.js` 文件。** `dist/` 中的旧构建产物会使 Vite dev server 失效（浏览器加载带 hash 的过期 JS 文件）。`.gitignore` 已包含 `frontend/dist/` 和 `frontend/src/**/*.vue.js`。
- **Windows bash 下的 curl 会损坏中文 UTF-8**。用 ASCII 内容测试 API，或使用 Playwright E2E 脚本。
- **MyBatis-Plus 3.5.10 缺少 `PaginationInnerInterceptor`** — 必须用 3.5.10.1，并添加 `mybatis-plus-jsqlparser` 依赖。
- **Vite 代理会缓冲 SSE 流**，导致连接不关闭、`reader.read()` 永不返回 `done: true`。解决方案：SSE 端点直连后端（`localhost:18080`），不走 Vite 代理，需配合 CORS。
- **`fetch` + `ReadableStream` 的 `reader.read()` 在 Vite 代理下可能永不返回 `done`**。改用 `XMLHttpRequest`，其 `onloadend` 更可靠地触发。
- **`allowCredentials(true)` 不能用 `allowedOrigins("*")`**，必须用 `allowedOriginPatterns("*")`。Spring Security 的 `UrlBasedCorsConfigurationSource` 支持此模式。

## E2E 测试

```bash
npm install playwright
npx playwright install chromium
node -e "..."    # Playwright 脚本测试登录→聊天流式→历史持久化
```

## gstack

使用 gstack 中的 `/browse` 技能进行所有网页浏览，切勿使用 `mcp__claude-in-chrome__*` 工具。

### 可用技能

| 技能 | 用途 |
|------|------|
| `/office-hours` | 办公时间咨询 |
| `/plan-ceo-review` | CEO 审查计划 |
| `/plan-eng-review` | 工程审查计划 |
| `/plan-design-review` | 设计审查计划 |
| `/plan-devex-review` | DevEx 审查计划 |
| `/design-consultation` | 设计咨询 |
| `/design-shotgun` | 设计发散 |
| `/design-html` | HTML 设计输出 |
| `/design-review` | 设计审查 |
| `/review` | 代码审查 |
| `/code-review` | PR 审查 |
| `/ship` | 发布上线 |
| `/land-and-deploy` | 合并并部署 |
| `/canary` | 金丝雀发布 |
| `/benchmark` | 性能基准测试 |
| `/browse` | 无头浏览器网页浏览 |
| `/connect-chrome` | 连接 Chrome |
| `/qa` | QA 测试 |
| `/qa-only` | 仅 QA |
| `/setup-browser-cookies` | 设置浏览器 cookies |
| `/setup-deploy` | 设置部署 |
| `/setup-gbrain` | 设置 gbrain |
| `/retro` | 回顾总结 |
| `/investigate` | 调查问题 |
| `/document-release` | 发布文档 |
| `/codex` | Codex 模式 |
| `/cso` | CSO 模式 |
| `/autoplan` | 自动规划 |
| `/devex-review` | DevEx 审查 |
| `/careful` | 谨慎模式 |
| `/freeze` | 冻结部署 |
| `/guard` | 守卫模式 |
| `/unfreeze` | 解冻 |
| `/gstack-upgrade` | 升级 gstack |
| `/learn` | 学习记录 |
