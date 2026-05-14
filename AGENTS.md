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

## 代理执行优先级与适用边界（Rules Manifest）

- P0（必须遵守）：直接改代码交付、禁止假设推演、禁止调查报告、禁止测试脚本、多步自我验证禁用、仅允许老板明确要求时做极简 HTTP 验证。
- P1（默认遵守）：最小改动、中文说明/注释优先、不提交自动生成产物、不回滚无关改动。
- P2（条件遵守）：当规则冲突时，优先 P0，再 P1，再 P2；无法同时满足时优先保证可运行与可回溯日志。

### 适用边界

- 允许：接口联调改造、前后端同仓修复、缓存/限流/观测性增强、Function Calling 扩展。
- 不允许：在未获指令时运行 E2E、修改系统环境、重启底层中间件、输出纯分析不落地。
- 例外：老板明确要求验证时，仅允许最小 HTTP 请求验证并附关键响应日志。

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

### 后端核心包

ai（聊天、分析、长期画像）、config（配置）、auth（鉴权）、diary（日记）、summary（报告）、entity（实体）、mapper（持久化）、security（JWT、限流）、follow、notification、common（异常、响应）。

### 前端

Vue 3 SPA，api（拦截器+请求）、components（10+UI组件）、pages（10+业务页）、router、stores、styles.css。

## 近期核心变更（2026-05）

- 鉴权与登录跳转：前端全局拦截器仅在 `401` 时清 token 并跳登录；`403`（如报告访问拒绝）不再强制跳登录，由页面自行提示错误。
- 额度体系收敛：`ENCOURAGEMENT` 下线，`COACHING` 并入 `ANALYSIS`，当前仅保留 `CHAT / ANALYSIS / REPORT`。
- 额度接口稳定化：补齐 `GET /api/user/quota`，并将该接口从“失败即登出”逻辑中排除。
- Redis 计数修复：额度计数键改为字符串序列化并加解析兼容，修复“额度显示不扣减”。
- 头像链路修复：上传后头像地址标准化，导航栏优先展示图片头像。
- 报告页策略调整：取消“继续聊”入口；相关日记缺失片段支持按 `diaryId` 回填；月报趋势图增加情绪颜色点位与强度标注。
- 聊天引用策略：保留引用能力（最多 2 条，单条限长），用于“围绕具体内容继续聊”。
- **长期用户画像系统**（2026-05-13）：
  - 新表 `user_profile_memory`（V1_14 Flyway），按 `(user_id, attribute_key)` 唯一存储属性（性格、目标、关键人物、压力源等）。
  - `MemoryExtractionService`：日记分析完成后异步提取属性、幂等 upsert 落库；支持批量初始化。
  - `ChatController` 聊天请求时读取画像，注入 system prompt 作为背景知识。
  - `diarySearchFunction`（Function Calling）：支持按关键词/日期检索历史日记摘要。
  - `POST /api/chat/admin/init-memory`：批量初始化接口（已完成 25/25 用户覆盖）。
  - `@Lazy DiaryService` 断开 Spring AI Bean 循环依赖。
  - `DiaryService` 补齐 `generateWeeklyAiSummary/generateMonthlyAiSummary` 方法。
- **删除场景的画像重建**（2026-05-14）：
  - 删除日记后不再只做增量更新，改为基于"剩余日记证据"全量重建画像。
  - 异步入口：`MemoryExtractionService.rebuildUserMemoryAfterDiaryDeletion(userId, deletedDiaryId)`，由 `DiaryService.deleteDiary()` 触发。
  - 四层证据分层避免长期历史截断：
    * 近期层（最近 15 篇）：原文，保留高粒度细节和最新状态。
    * 中期层（最多 120 篇）：读取单篇分析结果（情绪/主题/摘要），覆盖较早历史而不爆炸 token。
    * 长期层（最多 10 个周期）：复用 `diary_summaries` 表周期摘要，自动过滤掉包含被删日记的失效摘要。
    * 更老层：对周期摘要未覆盖的更老历史做聚合统计（高频情绪/主题）。
  - 幂等同步确保数据库最终与重建结果一致；用户无剩余日记时清空画像。
  - 关键日志记录：重建开始、各层证据规模、同步结果，便于排错和监控。
- **高可用性设计**（2026-05-13）：
  - 标准键映射：Redis key 遵循 `module:key:identifier` 格式，避免冲突。
  - 幂等 upsert：`user_profile_memory` 按 `(user_id, attribute_key)` UNIQUE，避免重复。
  - 读降级：历史检索失败自动回退关键词匹配；AI 分析失败用简单情绪推测。
  - 重试补偿：异步任务失败记入日志，支持手动重跑 init-memory。
  - 缓存失效：`evictUserCache()` 精确删除，不扫描全量 key。
- **后端性能与安全专项优化**（2026-05-13）：
  - `ChatService.loadHistory/saveHistory` 补全会话所有权校验，跨用户访问返回 400，修复数据越权漏洞。
  - `ChatService.buildContext` 从 N+1 改为 `selectBatchIds` 批量加载分析，SQL 从 10 条降至 2 条。
  - `SummaryService.create` 同步改为批量加载分析，消除 N+1。
  - `DiaryService` 在 `addComment`、`resonate`、`deleteComment`、`sendEncouragement`、`runAiAnalysis` 后补全 `evictUserCache`；提取 `evictRelatedUsersCache(actorId, ownerId)` 辅助方法统一跨用户缓存失效。
  - `DailyFollowUpScheduler.calcStreak` 改为一次查询 + Redis 缓存（key: `streak:{userId}:{date}`，TTL 6h），分析加载改为批量 Map，消除每用户 O(N) SQL。
  - `GlobalExceptionHandler` 补全 `ResponseStatusException`、`HttpMessageNotReadableException`、`MethodArgumentNotValidException`、兜底 `Exception` 处理器，所有异常均返回 `ApiResponse<Void>` 统一格式。
- **聊天触发画像增量更新**（2026-05-15）：
  - `ChatController` 在流式 `POST /api/chat/conversations/{id}` 完成后，基于拼接后的完整 AI 回复触发画像更新。
  - `ChatController` 在非流式 `POST /api/chat/conversations/{id}/reply` 成功返回后触发画像更新。
  - 新入口：`MemoryExtractionService.extractAndSyncMemoryFromChat(userMessage, refs, aiReply)`，复用既有异步提取链路，避免阻塞聊天响应。
- **聊天画像阈值策略**（2026-05-15）：
  - 四层阈值顺序：硬门槛 -> 信息量打分 -> 近似去重 -> 冷却窗口。
  - 硬门槛：短消息/寒暄短句/短回复直接跳过，降低噪声写入。
  - 打分阈值：长度、长期关键词、引用、回复长度综合打分，`score < 2` 跳过。
  - 去重：`memory:chat:last-hash:{userId}`（TTL 2h）避免重复对话反复抽取。
  - 冷却：`memory:chat:update:{userId}`（10 分钟）限制高频更新抖动。
  - 可观测性：新增统一前缀日志 `memory-chat | skip/pass | reason=...`，便于检索。

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
- Spring AI `FunctionCallback` 若直接注入 `DiaryService`，会与 `AiAnalysisService -> analysisChatClient` 形成循环依赖；在 `AIConfiguration.diarySearchFunction` 使用 `@Lazy DiaryService` 可断环。
- 合并外部 PR 后务必检查 `target/classes` 是否有旧字节码残留；启动时出现 `NoClassDefFoundError` 优先执行 `cmd /c mvn.cmd -DskipTests clean compile`。
