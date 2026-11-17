# DeepSeekClient 薄适配层评估

日期：2026-11-17。本文核对 Spring AI **已发布 tag** 的官方源码；不把 `main` 或 2.1 预览文档当作当前可直接使用的能力。只进行依赖与协议评估，没有改生产配置、读取凭据或调用真实模型。

## 结论

可以减少自研 HTTP/SSE 解析，但不能把“新版支持 reasoning_content”直接等同于“可以无差别替换”。**本次隔离编译和本地 HTTP/SSE 探针已确认：暂不建议用 Spring AI 1.1.8 直接替换。** 当前实现能正确处理的多工具续片和 index 交错参数，在该官方版本出现异常或错误聚合。应保留 AgentLoop、Registry、事件协议及现有客户端，待框架升级时再重新评估。

在当前 Spring Boot 3.5.14 / Java 21 约束下，优先评估 Spring AI 1.1.8。其低层 `OpenAiApi.chatCompletionStream()` 比 `ChatModel` 更贴合项目的手动工具循环，但官方工具分片合并器的限制已被探针复现，构成本次直接迁移的阻断项。仅为本次瘦身升级到 Spring AI 2.0.2 / Boot 4 的成本较高。

## 已发布版本能力

| 候选 | reasoning_content | reasoning_effort 与工具 | 适用判断 |
|---|---|---|---|
| Spring AI 1.0.9 OpenAI | 固定 tag 的 OpenAI 消息 DTO 没有 `reasoning_content` 字段 | 请求支持 `reasoning_effort` | 不满足本次聊天推理分片需求 |
| Spring AI 1.0.9 / 1.1.8 DeepSeek 专用 | 消息 DTO 支持；1.1.8 `DeepSeekChatModel` 可构建带推理的 `DeepSeekAssistantMessage` | 1.1.8 请求/options 没有 `reasoning_effort`；工具合并仍有限制 | 无法原样覆盖当前 Pro 请求契约 |
| Spring AI 1.1.8 OpenAI | 低层消息 DTO 支持；高层 `internalStream()` 把推理放到 `reasoningContent` 元数据 | 支持 `reasoning_effort`、直接传工具 schema、关闭高层内部工具执行 | 最接近当前 Boot 代际，需先验证分片边界 |
| Spring AI 2.0.2 | OpenAI 与 DeepSeek 都支持；OpenAI 支持历史推理回放，DeepSeek 新增 thinking/effort 选项 | OpenAI 适配器改用官方 Java SDK，工具聚合按 index 归并 | 能力更完整，但涉及 Boot 4 代际迁移 |

来源：[1.0.9 OpenAI API](https://github.com/spring-projects/spring-ai/blob/v1.0.9/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/api/OpenAiApi.java)、[1.1.8 OpenAI model](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java)、[1.1.8 DeepSeek options](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-deepseek/src/main/java/org/springframework/ai/deepseek/DeepSeekChatOptions.java)、[2.0.2 OpenAI model](https://github.com/spring-projects/spring-ai/blob/v2.0.2/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java)、[2.0.2 DeepSeek options](https://github.com/spring-projects/spring-ai/blob/v2.0.2/models/spring-ai-deepseek/src/main/java/org/springframework/ai/deepseek/DeepSeekChatOptions.java)。

1.1.8 的官方 parent POM 使用 Java 17 与 Boot 3.5.15，说明其与本项目属于同一 Boot 代际；这不代替在项目 Boot 3.5.14 上编译、启动与契约验证。2.0.2 parent 使用 Boot 4.1.2；官方支持说明列出 Boot 4.0.x / 4.1.x。[1.1.8 parent POM](https://github.com/spring-projects/spring-ai/blob/v1.1.8/pom.xml)、[2.0.2 parent POM](https://github.com/spring-projects/spring-ai/blob/v2.0.2/pom.xml)、[官方兼容说明](https://docs.spring.io/spring-ai/reference/getting-started.html)。

## 1.1.8 两条适配路径的差别

### 高层 ChatModel

高层方便接入观测与统一消息模型，但当前协议需要做更多类型转换。必须设置 `internalToolExecutionEnabled(false)`，并防止默认 tools/callbacks 混入；工具声明、审批与执行仍唯一来自 `ChatToolRegistry`。

两个容易被文档概述掩盖的差异：

- `OpenAiChatModel.internalStream()` 会保存推理元数据，但同 tag 非流式 `internalCall()` 的 metadata 构造未包含 `reasoningContent`，不能据此假设流式与非流式表现相同。
- 1.1.8 OpenAI `createRequest()` 为 assistant 消息构造 DTO 时未从 metadata 回填推理；DeepSeek model 则把 assistant 的 reasoning 参数固定传 null。若当前工具循环需要回传 `reasoning_content`，不能直接依赖这两个高层映射。

来源：[1.1.8 OpenAI model](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java)、[1.1.8 DeepSeek model](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-deepseek/src/main/java/org/springframework/ai/deepseek/DeepSeekChatModel.java)。

此外，model builder 默认 options 含 `temperature(0.7)`；运行时传 null 会被非空默认值覆盖。应使用项目独立的、无温度默认值的 options，而不是复用全局 `ChatClient` 的默认配置。低层 DTO 的 NON_NULL 序列化更容易保留“null 就不下发”的现有语义。[OpenAI model builder](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java#L768)、[DeepSeek model builder](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-deepseek/src/main/java/org/springframework/ai/deepseek/DeepSeekChatModel.java)。

### 低层 OpenAiApi

低层可以直接构造官方的请求/响应 DTO，不经过 Prompt、Advisor 或自动工具循环，适配工作收敛为：

```text
项目 messages / Registry schema / AgentLoopOptions
                     ↓ 转成官方 DTO
OpenAiApi.chatCompletionStream()
                     ↓ 转成项目事件
TextChunk / ToolCallReady → 原 ChatAgentLoop
```

低层支持显式 `baseUrl(...)` 和 `completionsPath("/chat/completions")`。默认 path 是 `/v1/chat/completions`，迁移时必须显式覆盖，避免改变现有请求地址。`reasoning_effort` 与 assistant 的 `reasoning_content` 可以通过 DTO 显式传递，不受高层历史映射影响。[1.1.8 OpenAI API](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/api/OpenAiApi.java)。

这不是一个可以和 `1.0.0-M6` 随意混用的独立 SDK：官方模型模块依赖同版本 `spring-ai-model`、`spring-ai-retry`。因此即使用低层 API，也应统一 Spring AI 版本，而不能只给一个模型 jar 指定新版本。[1.1.8 DeepSeek 模块 POM](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-deepseek/pom.xml)、[1.1.8 OpenAI 模块 POM](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-openai/pom.xml)。

## 工具分片合并：当前最重要的阻断项

1.1.8 `OpenAiApi.chatCompletionStream()` 不是逐帧原样透传：它进入工具调用后会把分片收集到窗口，通常等 `finish_reason=tool_calls` 再调用官方 helper 归并输出。

固定 tag 的 `OpenAiStreamFunctionCallingHelper`：

- 会拼接一个工具调用的 arguments；可以在一次窗口中输出已聚合的工具调用列表。
- 首个工具帧若包含多个完整调用，可以通过；但后续需要合并的 delta 同时包含多个工具续片时会抛异常。它没有像项目当前实现那样按 index map 归并，而是依赖最后一个调用与新 id 判断。
- 同一工具窗口内的 `content`、`reasoningContent` 取当前非 null 值覆盖旧值，不是字符串拼接。因此单个混合帧可以保留文本/推理，但多个混合帧可能丢失前面的片段。

上述多续片异常和交错参数错误已经由人工 SSE 固件复现；这不代表已证明实际 DeepSeek 服务一定产生相应 delta，也不能据此假设未来服务不会产生。首帧完整多工具可通过，不能将限制泛化为“所有同帧多工具都失败”。[1.1.8 OpenAI helper](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/api/OpenAiStreamFunctionCallingHelper.java)、[1.1.8 API 窗口处理](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/api/OpenAiApi.java#L235)。

2.0.2 OpenAI 适配器的 merger 改为按 index 合并工具参数，并显式拼接 reasoning 扩展字段；这消除了上述部分限制，但正文混合帧及取消、异常、配置语义仍需独立契约测试。[2.0.2 ChunkMerger](https://github.com/spring-projects/spring-ai/blob/v2.0.2/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java)。

## 建议的迁移门槛

1. 在隔离评估工程升级为统一 Spring AI 1.1.8，新 starter 名称改为 `spring-ai-starter-model-openai`，先编译现有生产源码及测试，检查 M6 到正式版的 API 变化。
2. 用本地假 HTTP/SSE 服务验证：正文与推理交错、单/多工具同帧、按 index 交错、空 id continuation、参数分片、推理/正文与工具混帧、`[DONE]`、空 choices、错误和取消。
3. 精确比较请求 JSON：Flash 的 null 温度与 effort 不下发；Pro 参数原样保留；模型必须由 `useReasoning` 决定；tools 与历史 tool_call_id 不丢失；路径不额外增加 `/v1`。
4. 若官方 1.1.8 聚合限制触发当前契约失败，不应为“瘦身”降低工具能力或改变模型协议。此时继续保留现有实现，或另评估独立官方 SDK；不要通过重新引入第二份工具声明或自动工具执行绕过问题。
5. 真正迁移后再跑现有 Agent、审批、恢复、历史和 SSE 回归，最后用本地后端做 Flash/Pro 实测。此次源码评估不构成真实服务兼容性验收。

## 本项目实际边界与改动范围

当前 `DeepSeekClient` 约 150 行，职责是构造请求、使用注入的 WebClient 发送 SSE、提取正文/推理、按 index 累积工具参数、转换成 `TextChunk` / `ToolCallReady`，以及记录调用计时。它本身没有 Agent 决策、工具执行或审批逻辑。

- Flash/Pro 均由 `ChatModelProfiles` 提供参数；null 温度、null effort 表示不发送，Pro 使用显式 `reasoning_effort`。不能让官方模型默认值改变这些语义。
- `ChatAgentLoop` 通过 `timeout`、取消信号和关闭 `toStream()` 订阅停止模型请求；适配器取消时必须能终止实际 HTTP 消费。
- `ModelRequestSanitizer` 和 `AIConfiguration` 的 WebClient 过滤器负责出站脱敏及补齐 assistant 工具消息的空 content/reasoning。低层官方 API 默认新建的 builder 不会自动继承这些项目规则；若以后迁移，必须显式接入现有清洗与超时配置，不能顺手删除全局过滤器，其他分析调用仍在使用。
- 非流式聊天 `/reply` 实际也消费同一个 Agent 流，并非直接调用模型的非流式接口。另一个 `DeepSeekReasoningClient` 用于分析/Persona 等路径，应单独评估，不与本次瘦身捆绑。
- 当前有 10 个生产 Java 文件直接依赖 Spring AI，涉及日记分析、记忆、标题、章节、事件和 Persona；统一升级依赖的验证范围远大于这个客户端。项目工具声明仍只有 `ChatToolRegistry` 一份，不恢复 FunctionCallback Bean 或自动工具执行。

## 隔离编译结果

在 `backend/moodcopilot/target/spring-ai-adapter-evaluation/` 生成独立 POM，保留 Boot 3.5.14 / Java 21，统一 Spring AI BOM 为 1.1.8，并把 OpenAI starter 改为新名称。sourceDirectory 指向现有生产源码，所有输出写入隔离目录，没有改生产 POM 或运行中的 class 文件。

执行独立 POM 的 `mvn.cmd --batch-mode --no-transfer-progress test-compile`：

- 现有生产源码全部编译通过。
- 现有测试编译有一处失败：`ReasoningTest.java:41` 调用的 `.functions(String)` 在新 API 中不存在。
- 该历史测试应在真正迁移时调整为当前 Registry/Agent 契约，不能为了兼容测试恢复第二套工具 Bean。
- 没有据此宣称应用上下文启动通过，也没有运行升级后的完整业务测试或真实模型请求。

原依赖树保存在 `target/spring-ai-current-dependencies.txt`；编译日志为 `target/spring-ai-adapter-evaluation/compile-1.1.8.log`。

## 本地 HTTP/SSE 对照探针

隔离工程随后只编译自己的探针测试，使用本机随机端口的 JDK HttpServer 返回人工 SSE，分别调用现有 `DeepSeekClient` 和真实 Spring AI 1.1.8 `OpenAiApi`。没有读取 API Key；请求使用固定无效凭据 `probe-only`，没有访问模型服务。

| 固件 | 当前实现 | 官方 1.1.8 低层 API | 判断 |
|---|---|---|---|
| 独立 reasoning 与正文帧 | 分别得到推理标记分片与正文 | 能读取 delta.reasoningContent 与 content；请求未注入温度/effort | 可映射 |
| 单工具参数分两帧 | 正确拼为 `{"x":1}` | 正确拼为相同参数 | 可映射 |
| 首帧两个完整工具调用 | 得到两个完整调用 | 得到两个完整调用 | 可映射，不能笼统说多工具必失败 |
| 后续同一帧两个工具 continuation | 两个调用分别正确完成 | 抛出 `Currently only one tool call is supported per message!` | 不兼容 |
| 工具参数按 0/1/0/1 交错 | 得到 A=`{"x":1}`、B=`{"y":2}` | 得到 A=`{"x":`、B=`{"y":1}2}` | 不兼容，有错误工具参数风险 |
| effort 与工具调用历史请求 | 当前原始 Map 路径保留字段 | 官方 DTO 实际出站保留 `reasoning_effort=high`、assistant.reasoning_content 和 tool_call_id | 低层可映射，高层不能据此推定 |

探针最终 6 项测试通过，含“预期复现不兼容”的断言；**不代表官方候选通过了迁移验收**。来源为实际执行日志与请求捕获：`target/spring-ai-adapter-evaluation/probe-1.1.8.log`，探针源码在该目录的 `probe-src/AdapterCompatibilityProbeTest.java`。使用隔离 POM 执行 `mvn.cmd --batch-mode --no-transfer-progress -Dtest=AdapterCompatibilityProbeTest test` 可重跑；这些临时文件会被主工程 clean 删除。

## 投入收益与下一步

- **现在：保留当前客户端，不进行生产依赖升级。** 为减少约 150 行代码引入全项目 Spring AI 升级，却又需要规避/重写官方工具聚合，收益不足。
- **维护说明：**“自研是因为 Spring AI 无法读取 reasoning_content”只能作为历史背景，不应作为永久技术结论。后续修改注释时，应准确说明当前依赖、项目事件契约和手动工具控制需求。
- **未来框架升级时：**优先考虑保留 `DeepSeekClient.stream(...)` 外部契约，内部改成经过验证的官方客户端 + DTO/事件转换；同时保留参数映射、出站清洗、计时、取消和完整工具 ready 事件生成。Agent 图、Registry、审批、历史和 Redis/SSE 不随之迁移。
- **Spring AI 2.0.2：**固定源码已修复按 index 合并的部分问题，但要迁移 Boot 4；本次只做源码评估，没有用该版本执行相同 HTTP 契约探针，不能宣称已可替换。
- **尚未验证：**模型服务实际分片形态、2.0.2 全量契约、升级后的应用启动、真实 Flash/Pro 及审批恢复。若后续确定迁移，应先把上述边界固件转成长期契约测试，再处理全项目依赖升级。
