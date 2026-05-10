# MoodCopilot

Spring Boot 3.5.14、Java 21、MySQL 8、Redis (Lettuce)、MyBatis-Plus 3.5.10.1、Flyway / Vue 3、Vite 5、TypeScript、Naive UI / DeepSeek API

## 命令

```bash
# 后端（需先加载 .env）
eval $(cat /d/Code/MoodCopilot/.env | tr -d '\r' | grep -v '^#' | grep -v '^$' | sed 's/^/export /')
cd backend/moodcopilot && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # 18080
./mvnw compile           # 仅编译
./mvnw test              # 单元测试

# 前端
cd frontend && npx vite --host                    # dev → 5173
npx vue-tsc --noEmit                               # 类型检查
npm run build                                       # 生产构建 → dist/
npx vite preview --host --port 4173                # 生产预览 → 4173
node e2e/smoke-test.mjs                            # E2E 测试（22 项）

# 对外分享
cloudflared tunnel --config ~/.cloudflared/moodcopilot-config.yaml run moodcopilot
```

测试账号：`test@test.com` / `123456`

## 架构

```
backend/.../com/moodcopilot/
├── ai/        ChatService, AiAnalysisService, DailyFollowUpScheduler
├── auth/      AuthService, AuthController（注册登录、改 profile、上传头像）
├── common/    ApiResponse<T>, RateLimitException, GlobalExceptionHandler
├── diary/     DiaryService（日记 CRUD、报告、同频、鼓励）
├── entity/    UserEntity（含 avatar、dailyNotifyEnabled）
├── security/  JwtTokenProvider, JwtAuthenticationFilter, RateLimitService

frontend/src/
├── api/       Axios 实例，/api/auth/** 白名单外的接口自动带 JWT，401/403 跳 /login
├── pages/     10 个页面，含 /settings 个人中心（launch 加载）
├── router/    9 条路由，beforeEnter 守卫检查 localStorage token
└── styles.css 全局 CSS：Warm Precision（#F8F6F2 底 / #5B7C6B 主色 / 无情绪标签展示）
```

## 规则

- **所有文本用中文**：commit message、注释、PR、文档。代码标识符用英文。
- **提交格式**：`<type>: <中文描述>`。type = feat / fix / refactor / design / perf / docs / test / chore。
- **完成功能后**：重构去重 → 编译 + 类型检查 → E2E → commit + push → 更新 AGENTS.md/CLAUDE.md。
- **公开区域禁止显示情绪/主题标签**。`DiaryFeedItem`、`DiaryDetailPage`、广场均不渲染 moodLabel 和 topicLabels。
- **AI 回复 ≤ 2-3 句**。system prompt 已限定，不要改长。
- **新增 DB 列用 Flyway**。迁移文件命名 `V1_X__description.sql`，当前最新 V1_10。

## 陷阱

1. **PUT 请求被 Spring Security 拦截返回 403**。即使 `.permitAll()` 也拦。新接口改用 POST。
2. **axios 上传文件不要设 `Content-Type`**。手动设 `multipart/form-data` 会丢失 boundary。让浏览器自动带。
3. **`.desktop-only` 不要设 `display: revert`**，会覆盖 `display: flex`。只在 `@media (max-width: 768px)` 里设 `display: none`。
4. **Filter 类不加 `@Component`**，否则 Spring 重复注册。用 `SecurityConfig` 的 `@Bean` + `FilterRegistrationBean.setEnabled(false)`。
5. **`@Cacheable` + Redis 与 Security AOP 冲突** → 403。改用手动 `StringRedisTemplate`。
6. **SSE 直连后端**：Vite 代理缓冲 SSE 流。`ChatPage` 用 `XMLHttpRequest` 直连 `localhost:18080`，`onloadend` 补调 `processSSE()`。
7. **头像存 `uploads/avatars/`**，`WebMvcConfig` 映射 `/uploads/**`，`SecurityConfig` 中 permitAll。
8. **`.env` 在工作树中路径不同**：`/d/Code/MoodCopilot/.env`（不是相对路径）。
