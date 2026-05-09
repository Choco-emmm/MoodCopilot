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
- AI：DeepSeek Chat API（兼容），失败时回退关键词分析

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
├── ai/              DeepSeekClient（RestClient）、AiAnalysisService（DeepSeek + 关键词回退）
├── auth/            AuthController、AuthService、RegisterRequest/LoginRequest/AuthResponse
├── common/          ApiResponse<T> 统一响应包装 { code, message, data }
├── config/          SecurityConfig、MybatisPlusConfig
├── diary/           DiaryController、DiaryService、DiaryView、DiaryComment、CreateDiaryRequest
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
├── api/index.ts         Axios 实例，拦截器（JWT 附加、401/403→跳到/login）、diaryApi、authApi、notificationApi
├── components/          7 个组件：AppHeader、DiaryComposer、AiAnalysisCard、
│                        SimilarDiariesPanel、MyDiaryList、PublicFeed、DiaryFeedItem
├── pages/               HomePage、LoginPage、RegisterPage、DiaryDetailPage
├── router/index.ts      4 条路由，beforeEach 守卫（requiresAuth→跳转/login）
├── stores/              auth.ts（JWT token+用户）、diary.ts（增删改+分页+轮询）、notification.ts
└── styles.css           全局 CSS（无 scoped 样式）
```

### API 路由（除白名单外均需 JWT Bearer token）

| Method | Path | 需要认证 |
|--------|------|---------|
| ANY | `/api/health`、`/api/auth/**`、`/swagger-ui/**`、`/v3/api-docs/**` | 否 |
| POST | `/api/auth/register`、`/api/auth/login` | 否 |
| GET | `/api/auth/me` | 是 |
| POST | `/api/diaries` | 是 |
| GET | `/api/diaries/mine`、`/api/diaries/public?page=&size=` | 是 |
| GET | `/api/diaries/{id}`、`/api/diaries/{id}/similar?limit=` | 是 |
| POST | `/api/diaries/{id}/comments`（body: `{content, parentCommentId}`） | 是 |
| POST | `/api/diaries/{id}/resonance` | 是 |
| GET | `/api/notifications`、`/api/notifications/unread-count` | 是 |
| PUT | `/api/notifications/{id}/read` | 是 |

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

## 重要踩坑记录

- **绝不要在 Filter 类上加 `@Component`。** Spring Boot 会自动将其注册为全局 servlet 过滤器，绕过 Spring Security 过滤器链。正确做法是在 `SecurityConfig` 中用 `@Bean` 创建，并用 `FilterRegistrationBean.setEnabled(false)` 禁用自动注册。
- **`@Cacheable` + Redis 与 Spring Security 冲突**，会导致受保护接口返回 403。已移除缓存注解。如需缓存，用手动缓存或 Caffeine 本地缓存代替。
- **`n-segmented` 在 Naive UI 中不存在。** 用 `<n-radio-group>` + `<n-radio-button>` 实现分段控件效果。
- **不要提交 `dist/` 或 `src/` 中的 `*.js` 文件。** `dist/` 中的旧构建产物会使 Vite dev server 失效（浏览器加载带 hash 的过期 JS 文件）。`.gitignore` 已包含 `frontend/dist/` 和 `frontend/src/**/*.vue.js`。
- **Windows bash 下的 curl 会损坏中文 UTF-8**。用 ASCII 内容测试 API，或使用 Playwright E2E 脚本。
- **MyBatis-Plus 3.5.10 缺少 `PaginationInnerInterceptor`** — 必须用 3.5.10.1，并添加 `mybatis-plus-jsqlparser` 依赖。

## E2E 测试

```bash
npm install playwright
node test-e2e.js      # 16 项检查：登录→主页→创建日记→评论→回复→通知→分析
```
