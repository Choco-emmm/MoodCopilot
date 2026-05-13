# 后端性能与安全优化（2026-05-13）

## 背景

对现有后端代码进行专项审查，发现并修复 6 处性能瓶颈、安全漏洞与错误处理缺陷。

---

## 变更清单

### 1. ChatService — 历史接口越权修复

**文件：** `ai/ChatService.java`

**问题：** `loadHistory` / `saveHistory` 未校验会话所有权，任意登录用户可读写他人聊天记录。

**修复：** 提取 `requireOwnedConversation(conversationId)` 辅助方法，在两处接口入口处强制校验 `conv.getUserId == currentUserId`，不匹配时抛出 `ResponseStatusException(BAD_REQUEST)`。

---

### 2. ChatService — buildContext N+1 优化

**文件：** `ai/ChatService.java`

**问题：** `buildContext` 方法在循环内逐条调用 `diaryAnalysisMapper.selectById(diary.getId())`，10 篇日记产生 10 条独立 SQL。

**修复：** 改为 `selectBatchIds` 一次批量加载，结果转 `Map<Long, DiaryAnalysisEntity>` 后按 diaryId 查找，SQL 从 N+1 降至 2 条。

---

### 3. SummaryService — create N+1 优化

**文件：** `summary/SummaryService.java`

**问题：** `create` 方法循环内对每篇日记单独调用 `diaryAnalysisMapper.selectById`。

**修复：** 循环前一次性 `selectBatchIds` 全量加载，构建 Map，循环内直接 `get(diary.getId())`，SQL 从 N+1 降至 2 条。

---

### 4. DiaryService — 写操作缓存失效补全

**文件：** `diary/DiaryService.java`

**问题：** `addComment`、`resonate`、`deleteComment`、`sendEncouragement`、`runAiAnalysis` 完成后未调用 `evictUserCache`，导致公开流 / 关注流 / 陪跑建议缓存陈旧。

**修复：**
- 在上述 5 处写操作完成后补充 `evictUserCache` 调用。
- 提取 `evictRelatedUsersCache(Long actorId, Long ownerId)` 辅助方法，统一处理评论/共鸣等跨用户场景下双方缓存失效逻辑，避免重复代码。

---

### 5. DailyFollowUpScheduler — 连接数优化

**文件：** `ai/DailyFollowUpScheduler.java`

**问题一：** `calcStreak` 按天循环查询最近 30 天是否有日记，最坏情况 30 条 SQL/用户。

**修复：** 改为一次查询用户最近 30 天有日记的日期集合，计算连续天数后将结果写入 Redis（key: `streak:{userId}:{date}`，TTL 6h），同日再触发直接命中缓存。

**问题二：** 通知发送循环内逐条调用 `diaryAnalysisMapper.selectById`。

**修复：** 循环前批量加载分析记录，构建 `Map<Long, DiaryAnalysisEntity>`，循环内直接查 Map。

---

### 6. GlobalExceptionHandler — 统一错误格式

**文件：** `common/GlobalExceptionHandler.java`

**问题：** 仅处理 `RateLimitException`，其余异常（`ResponseStatusException`、JSON 解析错误、参数校验失败、未知异常）返回 Spring 默认格式，与 `ApiResponse<T>` 不一致。

**新增处理器：**

| 异常类型 | HTTP 状态码 | 说明 |
|---|---|---|
| `ResponseStatusException` | 异常自带 status | 业务逻辑抛出的状态异常 |
| `HttpMessageNotReadableException` | 400 | 请求体 JSON 格式错误 |
| `MethodArgumentNotValidException` | 400 | `@Valid` 参数校验失败，返回首条字段错误 |
| `Exception`（兜底） | 500 | 未预期异常，记录 log.error 后返回通用提示 |

所有处理器均返回 `ApiResponse<Void>` 统一格式。

---

## 验证结果

| 验证项 | 结果 |
|---|---|
| Maven `clean compile` | BUILD SUCCESS |
| 健康检查 `/api/health` | 200 OK |
| 登录 + 鉴权 | 200，token 正常 |
| 跨用户访问聊天历史 | 400 `{"code":400,"message":"会话不存在"}` |
| 不存在会话 ID | 400 统一格式 |
| 创建日记 + 异步 AI 分析 | code:0，diary ID 正常返回 |
| 添加评论 | code:0 |

---

## 影响范围

- **安全**：修复聊天历史越权读写漏洞。
- **性能**：消除 ChatService / SummaryService / DailyFollowUpScheduler 三处 N+1 查询。
- **一致性**：补全写操作后缓存失效，避免用户看到陈旧数据。
- **可观测性**：统一错误响应格式，前端可统一处理 `code != 0` 的情况。
