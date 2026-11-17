# 聊天事件与 SSE

聊天生成任务独立于浏览器连接运行。文字、推理分片和工具事件经过现有 `merge` 合并后，由 `ChatGenerationService.consume()` 交给 `ChatRunEvents` 保存。

```text
文字 / 工具事件 → merge → Lua 保存事件并发布通知
                                      ↓
                    SSE 发送端按游标读取新增事件 → 浏览器
```

## 保存与发送

- `chat:run:{runId}:events` 继续保存带 `seq` 的 JSON 事件，元数据保存在对应 `:meta`，TTL 为一天。
- Lua 分配序号、追加事件、刷新 TTL，然后向 `chat:run:events:available` 发布 `{runId, seq}`。频道不携带正文、图片地址或工具结果。
- Redis 账号需要允许脚本内的 `TYPE/HGET/HINCRBY/HSET/RPUSH/EXPIRE/PUBLISH`，以及订阅频道。当前脚本面向项目现有的单节点 Redis；不承诺 Redis Cluster 跨槽执行。
- 各后端实例共享各自的一份频道订阅，通知按 run 分发给本实例的 SSE 连接。通知不作为内容来源，实际发送的数据始终读取自事件表。
- 每个 SSE 连接先注册通知，等 Redis 确认订阅后补发历史，再按通知读取新增尾部。初始扫描每批最多 256 条，List 下标与事件序号分别维护，兼容旧序号空洞。
- 重复或密集通知可以合并，不丢弃持久化事件。没有通知时不定时查询事件表。

## 恢复与审批

`GET /api/chat/conversations/{id}/runs/{runId}/stream?after={seq}`、JSON 事件类型及前端处理方式不变，浏览器不直接访问 Redis。

- SSE 断开只清理该连接，不取消生成。前端用最后收到的 `seq` 重连，后端补发其后的事件。
- Redis 通知连接重新订阅成功时，唤醒活跃连接补齐中断期间的事件；通知订阅报错时结束相关 SSE，让客户端重连。监听器按 1–10 秒指数退避重试，不回退到定期查询事件表。
- 初始订阅就绪最多等待 5 秒；SSE 维持原有 30 分钟空闲超时。
- 审批事件先落盘、再更新为 `AWAITING_APPROVAL`、最后发布通知，三步处于同一个 Lua 脚本内。前端收到事件后可以立即核验状态。
- `approval_required` 不结束 SSE；批准或拒绝后，同一个 run 从图检查点续跑，并向原事件表追加输出。只有 `done` 或 `error` 事件结束发送。
- 图检查点用于恢复 Agent，事件表用于客户端重放，两者不互相替代。浏览器断线继续生成不等于后端进程退出后自动恢复正在生成的模型请求。

## 验证

普通后端测试包含通知就绪、重复通知、历史补发交界、用户隔离和虚拟时间内无空闲轮询验证：

```powershell
cd backend/moodcopilot
mvn.cmd --batch-mode --no-transfer-progress clean verify
```

真实 Redis 测试显式启用，使用一次性的独立 Redis；测试会中断该 Redis 的 Pub/Sub 连接，不能指向应用 Redis。以下操作在仓库根目录运行：

```powershell
docker run --detach --rm --name moodcopilot-chat-events-test --publish 127.0.0.1::6379 redis:7-alpine
try {
    $env:MOODCOPILOT_TEST_REDIS_PORT = (docker port moodcopilot-chat-events-test 6379/tcp).Split(':')[-1]
    Push-Location backend/moodcopilot
    try {
        mvn.cmd --batch-mode --no-transfer-progress '-Dtest=ChatRunEventsRedisTest' test
        if ($LASTEXITCODE -ne 0) { throw 'Redis 集成测试失败' }
    } finally { Pop-Location }
} finally {
    Remove-Item Env:MOODCOPILOT_TEST_REDIS_PORT -ErrorAction SilentlyContinue
    docker stop moodcopilot-chat-events-test
}
```

测试覆盖独立实例间通知、文字/工具/审批/终态事件、通知断连恢复、分页与旧序号空洞、输入失败不推进序号，以及现有 Controller 的 SSE JSON 协议。

## 发布

统一更新生成端与 SSE 服务端后再接入流量。旧实例不会发布通知，不支持新旧后端实例混合承载聊天任务。事件存储格式保持兼容，无需清空 Redis 或迁移已有任务。
