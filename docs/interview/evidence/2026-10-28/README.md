# 面试材料证据记录

日期：2026-10-28（Asia/Shanghai）。源码：`933ff4a1bed8ef0cad1056f9d8a576742ba4eb27`。

## 本轮实际执行

- 本地 Maven 3.9.12，Oracle JDK 25.0.1；项目 target release 为 Java 21。本轮不是 CI 的 Temurin 21 环境。
- 明确关闭需要真实模型的 opt-in 开关，执行下面 13 个类，共 119 项：0 失败、0 错误、0 跳过。Maven 报告 Total time 为 14.077 秒；这只是测试命令耗时，不是模型响应速度。
- [test-run.json](./test-run.json) 记录实际开始／结束时间和退出码；[targeted-tests.txt](./targeted-tests.txt) 保存控制台日志；[evidence-summary.json](./evidence-summary.json) 保存每项用例名称、耗时、原 XML SHA-256 和总数，没有复制报告里的环境属性或完整业务内容。

| 测试类 | 用例数 |
| --- | ---: |
| ChatAgentResilienceTest | 16 |
| ChatAgentLoopTest | 7 |
| ChatAgentApprovalTest | 13 |
| ChatGenerationServiceTest | 18 |
| ChatHistoryMergeTest | 12 |
| ChatHistoryStoreTest | 4 |
| ChatModelProfilesTest | 5 |
| ChatToolRegistryTest | 14 |
| ContextPlannerTest | 11 |
| PromptComposerTest | 2 |
| XmlPromptRendererTest | 3 |
| AiTaskProducerTest | 2 |
| MemoryWriteContractTest | 12 |

这些是受控测试：外部模型流、工具执行边界、Redis 操作等在相应测试中使用替身，状态图测试使用内存检查点。AiTaskProducerTest 的两项实际覆盖章节刷新 operationKey，不是投递失败／ACK／MQ 故障集成测试。

复现命令（仓库根目录，重新运行会覆盖本目录同名日志和摘要；需要保留本轮时先复制整个材料目录）：

```powershell
$ErrorActionPreference = 'Stop'
$evidenceDir = Join-Path (Get-Location) 'docs/interview/evidence/2026-10-28'
$env:MOODCOPILOT_RUN_REASONING_TESTS = 'false'
$started = [DateTimeOffset]::Now.ToString('o')
Push-Location backend/moodcopilot
try {
    & mvn.cmd --batch-mode --no-transfer-progress '-Dtest=ChatAgentResilienceTest,ChatAgentLoopTest,ChatAgentApprovalTest,ChatGenerationServiceTest,ChatHistoryMergeTest,ChatHistoryStoreTest,ChatModelProfilesTest,ChatToolRegistryTest,ContextPlannerTest,PromptComposerTest,XmlPromptRendererTest,AiTaskProducerTest,MemoryWriteContractTest' test *> (Join-Path $evidenceDir 'targeted-tests.txt')
    $testExit = $LASTEXITCODE
} finally {
    Pop-Location
}
@{startedAt=$started; finishedAt=[DateTimeOffset]::Now.ToString('o'); exitCode=$testExit} |
    ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $evidenceDir 'test-run.json')
if ($testExit -ne 0) { throw '定向测试失败，请查看日志' }
node docs/interview/verify-evidence.mjs
```

## 历史实测数据复核

来源：[2026-10-21 原报告](../../../verification/2026-10-21-agent-performance/README.md)。该轮是本机 HTTP/1.1 keep-alive、闭环固定并发、每组约 20 秒，无 AI 调用，不应外推为生产容量。

本轮解压并重算三份原始采样，总计 575,266 条：样本数、成功数、状态计数、nearest-rank P95、成功响应 P95、吞吐计算和压缩文件 SHA-256 均与存档一致。

| 组 | 样本／成功 | 关键结果 |
| --- | --- | --- |
| health-c8 | 262,875 / 262,875 | 成功 P95 0.951 ms；探针无 AI／数据库业务工作量 |
| health-c32 | 263,196 / 263,196 | 成功 P95 3.506 ms；不能代表业务吞吐 |
| announcements-c8 | 49,195 / 296 | 48,899 次 HTTP 429；成功 P95 6.774 ms；主要反映单 IP 限流 |

还重新解析历史故障测试 XML：修复前 6 项、4 失败；修复后扩展至 16 项、0 失败。本轮同名 Agent 故障类也实际再次通过 16 项。历史测试与本轮测试的条件不同于真实模型服务，不能计算生产故障率。

## 本地可用性及未验证项

证据脚本实际 GET 探测：本机前端 `http://127.0.0.1:5173` 返回 200；后端 `http://127.0.0.1:18080/api/health` 为 `ECONNREFUSED`。前端 200 仅说明开发服务器可达，不证明登录或接口可用。

本轮没有启动／重启服务、部署或操作应用用户数据，没有真实 AI 延迟、token／成本、RAG 召回率、生产容量、浏览器刷新恢复或微信真机交互的实测。演示录屏和 300 秒彩排仍需后端可用后完成。

交付检查包含证据脚本语法、原始数据复核、代码链接位置核对和 `git diff --check`；没有因整理文档重新运行完整后端测试或小程序构建。
