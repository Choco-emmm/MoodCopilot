# AGENTS.md

Codex / Copilot CLI / 其他 AI 编码代理在此仓库工作时的指引。

## 语言规则

- **全部用中文**：commit message、代码注释、PR 描述、与用户交流
- 代码标识符（变量名、函数名、类名）用英文

## 工作流

每完成一个功能阶段：

```
重构（去重复、拆长方法、删未用 import）
→ 后端编译通过（./mvnw compile）
→ 前端类型检查通过（npx vue-tsc --noEmit）
→ 跑 Playwright E2E 测试
→ git commit + push
→ 更新 AGENTS.md / CLAUDE.md
```

## 提交消息格式

```
<type>: <简短中文描述>

type 取值：
  feat     新功能
  fix      Bug 修复
  refactor 重构（无功能变化）
  design   视觉/样式改动
  perf     性能优化
  docs     文档更新
  test     测试相关
  chore    构建/依赖/工具

示例：
  feat: 用户中心 — 头像上传 + 用户名修改
  fix: 头像上传去掉手动 Content-Type，让浏览器自动带 boundary
  design: 全局视觉重设计 — Warm Precision
  perf: N+1 批量查询优化，公开日记流 41 次查询降至 3 次
```

## 技术栈

- 后端：Spring Boot 3.5.14、Java 21、MySQL 8、Redis (Lettuce)、MyBatis-Plus 3.5.10.1、Flyway
- 前端：Vue 3、Vite 5、TypeScript、Naive UI 2.41、Pinia 2.2、Vue Router 4.4
- AI：Spring AI 1.0.0-M6 + DeepSeek API

## 项目结构

```
backend/moodcopilot/src/main/java/com/moodcopilot/
├── ai/        AI 聊天、分析、陪跑、定时通知
├── auth/      注册登录、用户信息修改、头像上传
├── common/    ApiResponse、限流异常、全局异常处理
├── config/    安全、数据库、Redis、AI、定时任务、静态资源
├── diary/     日记 CRUD、分析、报告、同频推荐、鼓励
├── entity/    所有数据库实体（UserEntity 含 avatar + dailyNotifyEnabled）
├── mapper/    MyBatis-Plus BaseMapper
├── notification/  通知系统（含 SYSTEM 类型每日跟进）
└── security/  JWT、限流服务
frontend/src/
├── api/       Axios 实例 + 拦截器
├── components/ 10 个组件
├── pages/     10 个页面（含 /settings 个人中心）
├── router/    9 条路由 + beforeEnter 认证守卫
├── stores/    Pinia（auth 含 avatar、dailyNotifyEnabled）
└── styles.css 全局 CSS（Warm Precision 设计系统）
```

## 关键注意事项

1. **Spring Security 拦截 PUT**：`.permitAll()` 不生效时改用 POST。`/api/auth/profile` (PUT) → 403，改为 `/api/auth/update-profile` (POST)。
2. **multipart 上传不设 Content-Type**：axios 手动设 `Content-Type: multipart/form-data` 会丢失 boundary，让浏览器自动生成。
3. **CSS `.desktop-only` 不要设 `display: revert`**：会覆盖 flex 布局的 `display: flex`。只在 media query 中设 `display: none`。
4. **Filter 类不加 `@Component`**：Spring 会重复注册。用 `SecurityConfig` 中 `@Bean` + `FilterRegistrationBean.setEnabled(false)`。
5. **SSE 直连后端**：Vite 代理会缓冲 SSE 流，ChatPage 用 `XMLHttpRequest` 直连 `localhost:18080`。`onloadend` 补调 `processSSE()` 防数据丢失。
6. **缓存用 `StringRedisTemplate` 手动管理**：`@Cacheable` + Redis 会与 Spring Security AOP 冲突导致 403。
7. **Flyway 迁移命名**：`V1_X__description.sql`，当前最新 V1_10。
8. **头像存储**：`backend/moodcopilot/uploads/avatars/{userId}.jpg`，通过 `/uploads/**` 映射访问。
9. **环境变量**：从仓库根目录 `.env` 加载。工作树中路径为 `/d/Code/MoodCopilot/.env`。
10. **E2E 测试**：`cd frontend && node e2e/smoke-test.mjs`（22 项）。

## 设计系统

Warm Precision — 暖调精炼。
- 背景 `#F8F6F2`、卡片 `#FFFFFF`、主色 `#5B7C6B`、文字 `#1C1C1C`
- 系统无衬线字体，无楷体/衬线
- M 几何 SVG logo（`icon-192.svg`），无印章/圆圈装饰
- 公开区域不显示情绪/主题标签

## 运行

```bash
# 后端
eval $(cat /d/Code/MoodCopilot/.env | tr -d '\r' | grep -v '^#' | grep -v '^$' | sed 's/^/export /')
cd backend/moodcopilot && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 前端 dev
cd frontend && npx vite --host

# 生产 + 分享
cd frontend && npx vite build && npx vite preview --host --port 4173
cloudflared tunnel --config ~/.cloudflared/moodcopilot-config.yaml run moodcopilot
```

测试账号：`test@test.com` / `123456`
