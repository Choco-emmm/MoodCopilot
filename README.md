# MoodCopilot

MoodCopilot 是一个 AI 情绪日记 + 同频陪伴社区。
用户可以记录日记、获得 AI 情绪分析、选择公开内容并获得他人温和回应。

## 当前版本重点

- 长期用户画像：日记分析后异步抽取长期属性并注入聊天上下文。
- 聊天驱动画像增量更新：用户与 AI 对话完成后也会触发画像提取（流式/非流式都覆盖）。
- 聊天画像阈值策略：硬门槛 + 打分 + 去重 + 冷却，减少噪声与抖动。
- 删除后画像重建：删除日记后按分层证据重建画像，避免旧证据污染。
- 举报审核后台：管理员可按状态分页处理举报。
- 管理员“隐藏并处理”已改为软删：
  - 举报目标是日记：将日记 `is_deleted=1`
  - 举报目标是评论：将评论 `is_deleted=1`
  - 不再物理删除。
- 用户侧“隐藏他人日记”功能已下线。

## 本次更新（2026-05-15）

- 聊天完成后触发画像更新：
  - 流式接口 `POST /api/chat/conversations/{id}` 在流结束后更新画像。
  - 非流式接口 `POST /api/chat/conversations/{id}/reply` 在返回后更新画像。
- 新增聊天阈值策略：
  - 硬门槛：过滤短消息、寒暄短句、短回复。
  - 打分阈值：消息长度、长期关键词、引用、回复长度综合判断。
  - 去重：同一用户相似对话哈希去重（2 小时窗口）。
  - 冷却：同一用户 10 分钟内最多触发一次聊天画像更新。
- 新增链路日志：统一前缀 `memory-chat | skip/pass`，便于线上排查。

## 技术栈

- 后端：Spring Boot 3.5、Java 21、MySQL 8、Redis、MyBatis-Plus、Flyway
- 前端：Vue 3、Vite 5、TypeScript、Naive UI、Pinia、Vue Router
- AI：Spring AI + DeepSeek（OpenAI 兼容）

## 目录结构

```text
backend/moodcopilot/    Spring Boot 后端
frontend/               Vue 前端
docs/                   设计与计划文档
```

## 本地启动（Windows）

### 一键启动（推荐）

在仓库根目录执行：

```powershell
cd D:\Code\MoodCopilot
npm.cmd run app:start
```

常用变体：

- 重启：`npm.cmd run app:restart`
- 诊断：`npm.cmd run app:doctor`
- 含公网链路：`npm.cmd run public:start`

### 1. 后端

```powershell
cd D:\Code\MoodCopilot\backend\moodcopilot
cmd /c mvn.cmd -Dmaven.test.skip=true spring-boot:run
```

默认端口：`18080`

### 2. 前端

```powershell
cd D:\Code\MoodCopilot\frontend
npm.cmd install
npm.cmd run dev
```

## 数据库迁移说明

Flyway 迁移位于：

- `backend/moodcopilot/src/main/resources/db/migration`

本次与审核相关的关键迁移：

- `V1_15__refresh_seed_meaningful_users.sql`：更新高质量种子用户和日记数据
- `V1_16__ensure_admin_account.sql`：确保可用管理员账号存在

## 管理员审核接口

后端路由前缀：`/api/admin/reports`

- `GET /api/admin/reports?status=PENDING&page=1&size=20`
- `POST /api/admin/reports/{id}/resolve`
- `POST /api/admin/reports/{id}/reject`
- `POST /api/admin/reports/{id}/hide-target`

状态流转：

- 待处理：`PENDING`
- 已处理：`RESOLVED`
- 已驳回：`REJECTED`

处理记录会写入：

- `handled_by_user_id`
- `handled_at`
- `handle_note`

## 默认管理员账号（开发环境）

迁移会保证以下至少一个账号可用：

1. 若存在 `test@test.com`，该账号将被提升为 `ADMIN`
2. 若存在 `testuser2@test.com`，该账号将被提升为 `ADMIN`
3. 若以上都不存在，会创建兜底管理员账号

兜底管理员账号：

- 邮箱：`admin@moodcopilot.local`
- 密码：`123456`

## 权限与安全约束

- 举报审核接口仅 `ADMIN` 角色可访问。
- 用户端不再提供“隐藏他人日记”入口。
- 举报“隐藏并处理”使用软删，便于审计与回溯。

## 常见问题

### 1) 启动报 Flyway checksum mismatch

原因：修改了已执行过的历史迁移文件（如 `V1_1__...`）。

建议：

- 不要改动已上线历史迁移内容；新增更高版本迁移。
- 若本地已污染，先恢复历史迁移文件，再新增增量脚本。

### 2) 启动报数据库认证失败

检查 `application.yaml` 中数据库配置，或设置环境变量：

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`

## 维护建议

- 业务变更优先追加新迁移，不要覆盖旧迁移。
- 管理端动作优先可审计（软删 + 处理备注）。
- 新增管理功能时同步更新本 README 的“接口”和“权限约束”。
