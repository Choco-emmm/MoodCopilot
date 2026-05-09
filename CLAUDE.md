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
├── config/           SecurityConfig、MybatisPlusConfig、AIConfiguration（ChatClient Bean、ChatMemory、aiExecutor）
├── auth/            AuthController、AuthService、RegisterRequest/LoginRequest/AuthResponse
├── common/          ApiResponse<T> 统一响应包装 { code, message, data }
├── config/          SecurityConfig、MybatisPlusConfig
├── follow/          FollowController、FollowService
├── diary/           DiaryController、DiaryService、DiaryView、DiaryComment、WeeklyReportView、CreateDiaryRequest
├── entity/          MyBatis-Plus 实体：UserEntity、DiaryEntity（@TableLogic）、DiaryAnalysisEntity、
│                    DiaryCommentEntity、DiaryResonanceEntity、NotificationEntity
├── health/          HealthController
├── mapper/          MyBatis-Plus BaseMapper 接口（共 6 个）
├── notification/    NotificationService、NotificationController
└── security/        JwtTokenProvider、JwtAuthenticationFilter
```

### 前端：Vue 3 单页应用

```
src/
├── api/index.ts         Axios 实例，拦截器（JWT 附加、401/403→跳到/login）、diaryApi、authApi、
│                        notificationApi、followApi、summaryApi、chatApi
├── components/          7 个组件：AppHeader、DiaryComposer、AiAnalysisCard、
│                        SimilarDiariesPanel、MyDiaryList、PublicFeed、DiaryFeedItem
├── pages/               HomePage、LoginPage、RegisterPage、DiaryDetailPage、ReportPage、
│                        FollowingPage、ChatPage
├── router/index.ts      7 条路由，beforeEach 守卫（requiresAuth→跳转/login）
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
| POST | `/api/chat`（SSE 流式） | 是 |
| DELETE | `/api/chat/memory` | 是 |
| POST | `/api/summaries` | 是 |
| GET | `/api/summaries` | 是 |
| DELETE | `/api/summaries/{id}` | 是 |

### 数据库

MySQL 8，Flyway 迁移脚本位于 `src/main/resources/db/migration/`。表：`users`、`diaries`、`diary_analysis`、`diary_comments`、`diary_resonances`、`notifications`。

MyBatis-Plus 配置：`is_deleted` 字段使用 `@TableLogic`，主键使用 `@TableId(type=IdType.AUTO)`，JSON 列使用 `JacksonTypeHandler`。分页需要 `PaginationInnerInterceptor`（在 `MybatisPlusConfig` 中配置）。

### AI 分析流程

1. `POST /api/diaries` → 立即保存日记，返回 `analysis: null`
2. `@Async runAiAnalysis()` 在后台调用 DeepSeek API，结果写入 `diary_analysis`
3. 前端每 2 秒轮询 `GET /api/diaries/{id}`，直到 `analysis != null`
4. DeepSeek 失败 → 回退到中文关键词匹配（6 种情绪、5 个主题）

### 评论：两级平铺结构

`diary_comments` 表有 `parent_comment_id`（回复了谁）和 `root_comment_id`（锚定顶级评论）。所有回复平铺在根评论下方，最多两级，不会无限嵌套。

### AI 对话架构

- **SSE 流式**：ChatController 返回 `Flux<String>`（`text/event-stream`），前端用 `XMLHttpRequest` 直连 `localhost:18080` 消费
- **ChatMemory**：`Map<Long, ChatMemory>` 按用户隔离，`MessageChatMemoryAdvisor` 自动管理对话历史
- **历史持久化**：`PUT/GET /api/chat/history` 存 Redis，TTL 7 天，跨设备同步
- **上下文**：只注入原始日记（最近 10 篇），不读总结防止幻觉传递
- **Markdown**：前端用 `marked` 渲染 AI 回复，AI prompt 指引使用基本 Markdown 格式

### Redis 缓存

用 `StringRedisTemplate` 手动缓存（不用 `@Cacheable` 避免 AOP 与 Security 冲突）。

| 缓存 Key | TTL | 失效触发 |
|------|------|------|
| `report:{userId}:{weekOffset}` | 30min | 用户写新日记 |
| `public:diaries:{page}:{size}` | 5min | 新公开日记 |
| `following:{userId}:{page}:{size}` | 5min | 新日记/关注变化 |
| `chat:history:{userId}` | 7d | 用户清空对话 |

### CORS

SecurityConfig 已配置 CORS（`allowedOriginPatterns("*")` + `allowCredentials(true)`），ChatPage 直连后端绕过 Vite 代理的 SSE 缓冲问题。

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
