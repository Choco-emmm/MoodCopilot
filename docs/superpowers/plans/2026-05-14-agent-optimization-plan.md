# Agent 功能优化实施记录（2026-05-14）

## 目标

在不改动整体架构的前提下，提升 MoodCopilot 的 agent 功能稳定性、可观测性与可维护性，优先落地低风险高收益项。

## 已实施范围

### Phase 1：前后端稳定性与体验断点

- 前端聊天页：
  - 引用日记增加 loading / error / retry 反馈。
  - 引用去重策略由内容去重改为 diaryId 去重。
  - 历史读写失败增加前端警告日志，避免静默失败。
  - 回复解析增加空内容回退。
  - 日记分析轮询增加超时上限（30 次）。
- 后端聊天历史：
  - `ChatService.saveHistory/loadHistory` 异常分支增加告警日志。

### Phase 2：agent 能力增强

- Function Calling 扩展：新增 `userStatsFunction`（最近 N 天情绪与主题统计）。
- 聊天函数链：`diarySearchFunction + userStatsFunction` 并行可用。
- 删除后画像重建：新增用户维度互斥锁，避免并发重建冲突。
- `aiExecutor`：新增任务耗时告警与队列拥塞告警。

### Phase 3：工程治理

- AGENTS 规则清单化：新增优先级与适用边界说明。
- 新增 `agent:health-check` 入口与诊断脚本。

## 验收口径

- 代码层验收：
  - 目标文件无静态错误。
  - 新增函数可被 Spring AI FunctionCallback 注册。
- 运行层验收（按仓库规则，最小化）：
  - 仅在老板要求时使用 HTTP 请求检查 200 与关键字段。
  - 不运行 E2E，不改环境变量，不重启底层服务。

## 回滚点

- Function Calling 扩展回滚：
  - 移除 `userStatsFunction` 注册与 `ChatService` 绑定。
- 画像重建互斥回滚：
  - 去除 `memory:rebuild:{userId}` 锁逻辑，恢复原异步重建流程。
- 线程池观测回滚：
  - 恢复 `CallerRunsPolicy` 默认实现与无 task decorator。

## 后续建议

1. 将用户统计函数结果接入前端透明化提示（可选）。
2. 增加 agent 执行审计日志（taskId、改动文件、耗时、结果）。
3. 将健康检查纳入日常开发启动脚本（仅检测，不自动修复）。
