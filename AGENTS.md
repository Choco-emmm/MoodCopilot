# MoodCopilot Agent Guide

## 产品边界

- `frontend/` 是网页端，包含完整社区能力。
- `frontend-uniapp/` 是微信小程序，定位为私密的日记、AI 陪伴与个人成长工具。
- 小程序**不提供**广场、关注、用户主页、评论、点赞/共鸣、举报或其他公开社交入口。
- 不要因为网页端存在这些页面或后端已有相应接口，就在小程序添加路由、入口、互动按钮或推送跳转。

## 功能对齐原则

- 小程序应与网页端保持一致的是非社交的个人能力：日记创建、编辑、删除、图片与音乐、AI 分析、聊天、记忆与图谱、报告、合集、成长、通知、设置和个人资料。
- 功能对齐不等于照搬网页布局。小程序优先保证单手操作、列表滚动、低首屏开销和明确的页面层级。
- 全局视觉方向为“带有纸张温度的克制版杂志风”：以编辑部式的信息编排、清晰的标题层级、克制的细边框和有节奏的留白表达内容，不使用软萌、糖果化、过度玻璃拟物或堆叠卡片制造氛围。
- Web 与小程序应共享同一视觉语气：纸张感来自主题背景、表面层级、衬线标题和细节排版，而不是额外硬编码颜色、纹理或装饰图形。小程序可以压缩间距和信息密度，但不得改变标题、正文、辅助信息、状态和主操作的层级关系。
- 页面优先采用“标题区 - 内容区 - 操作区”的编辑式结构。重复内容使用轻量分隔线和列表节奏承载；只有需要聚焦或确认的内容才使用边界明确的面板，避免卡片套卡片。
- 文字摘录、证据和来源必须以次级信息呈现：使用较小字号、较弱对比度、引用缩进或细分隔线表达“这是原始材料”，不得与用户记忆正文争夺视觉主层级。
- 操作控件遵循明确的主次关系：每个区域最多一个主操作，次要操作使用透明背景和边框；按钮、标签和状态不得仅依靠颜色表达含义，必须同时有可读文字或结构提示。
- Web 与小程序必须遵守“视觉语言一致，布局方式平台适配”的原则，不得分别设计成两套产品。两端必须共享主题颜色、字体层级、信息优先级、分组/分隔语义、状态文案和主操作顺序；只有屏幕尺寸、触摸/鼠标反馈、弹窗/底部面板、网页组件/小程序原生组件等平台行为可以不同。
- 视觉适配不得削弱信息层级：Web 可以使用宽屏网格或卡片，小程序可以使用单列列表，但正式记忆、候选记忆和关系图谱的标题、正文、辅助信息、依据入口和操作层级必须保持一致。不能因为小程序空间有限而删除必要的状态、数量或可追溯入口。
- 记忆中心第一层只展示用户需要判断的内容：属性分组标题、条目数量、记忆正文摘要和一个主要入口。来源、证据、整理状态、更新时间和历史版本放入“查看依据/详情”区域；不要在列表中重复显示分组标题或铺开内部元数据。
- 记忆中心按属性分组并支持折叠；默认只展开少量重点分组，其余保持收起。候选记忆必须在视觉上明确显示待确认总数和分组数量；关系图谱必须提供总数、有限首屏展示、搜索/筛选和展开全部能力，禁止数据量大时一次性铺满页面。
- 分组边界必须可感知。优先使用主题级 `divider`/`border`、留白和稳定的区块节奏建立分隔，不得依赖增加颜色数量制造层级。网页端的卡片、边框和间距与小程序端的列表、分隔线和触摸区域只是同一语义的不同平台呈现。
- 新增或调整小程序页面时，延续现有主题变量（`--theme-*`）、`GlobalUI`、自定义 tabBar 与安全区处理；不要引入另一套颜色、字体或导航体系。
- 合集是私密内容组织能力，不应改造成公开内容或社交收藏功能。
- 重要事件和时光画卷是私密个人能力；事件仅允许 `PENDING` 与 `FOLLOWED_UP`，历史 `ARCHIVED` 只兼容显示为已跟进，不得新增归档状态。聊天入口只注入事件上下文，不自动修改状态。
- 重要事件的日期和时间必须按后端配置的业务时区解释，不能直接依赖开发机、容器或数据库的系统默认时区。当前业务时区默认为 `Asia/Shanghai`，配置项为 `moodcopilot.time-zone`，环境变量为 `MOODCOPILOT_TIME_ZONE`。
- 重要事件回访时间规则必须保持一致：只有开始日期时按开始日 `00:00` 到期；有结束日期但没有结束时间时按结束日 `23:59:59.999999999` 到期；有结束时间时按结束日期的结束时间到期；只有开始时间时按开始日期的开始时间到期。
- 事件表中的 `targetDate`、`endDate`、`startTime`、`endTime` 是业务时区下的本地日期/时间值。后端到期判断必须使用配置时区生成当前时间；前端不得用浏览器时区改写用户选择的值。未来若支持用户级时区，必须显式增加用户时区字段和转换规则，不能隐式改变现有事件语义。
- 长期记忆的正式写入唯一经过 `MemoryOrchestrator`；候选记忆、证据、拒绝指纹和 `previousMemoryId` 版本链必须保留可追溯关系。
- AI 推断记忆的 evidence 必须是用户正文或主动歌词中的短连续原文片段；匹配失败时直接丢弃，不得用整篇日记兜底，也不得保存无关正文作为证据。候选去重只按 `userId + attributeKey + memoryType + normalizedValue` 的确定性指纹执行，不做语义相似或包含关系自动合并。
- 记忆 `attributeKey` 的数据库值保持原始值，不新增前端展示映射；新 AI 提取的属性键必须是简洁、用户可读的中文名称，英文/snake_case/数据库字段名由服务端拒绝。已有正式画像中的旧英文键仅为兼容“原样保留历史属性”而允许继续传递，不得据此放宽新键校验。

## 后端协作规则

- 实现前先检查 `backend/moodcopilot` 的 Controller、DTO/View 和网页端 API 调用，确认接口、权限和返回结构。
- 如果所需的非社交能力没有接口、接口权限不符合小程序场景，或返回契约无法满足页面需求，先向用户说明问题和建议，未经确认不要修改后端以猜测产品需求。
- 小程序请求统一使用 `frontend-uniapp/src/utils/request.ts`，不要在页面中硬编码 API 或 WebSocket 域名。
- API 地址由 `VITE_API_BASE_URL` 控制。微信小程序（包括微信开发者工具的开发构建）默认请求 `https://moodcopilot.top`，通过该站点的反向代理访问后端；不要为小程序调试给 Docker backend 新增宿主机 `ports` 映射。仅 H5 本地开发可默认使用 `http://localhost:18080`。WebSocket 必须从该基地址派生。
- 微信小程序 AppID 必须维护在 `frontend-uniapp/src/manifest.json` 的顶层 `appid` 和 `mp-weixin.appid`，不要只在 `dist/**/project.config.json` 或微信开发者工具中临时修改，否则下次构建会回退为游客项目。
- Pro/Flash 始终由用户手动选择并通过 `useReasoning` 传递：`false` 使用 Flash，`true` 使用 Pro。禁止恢复自动意图判断、复杂度判断、自动模型路由和静默降级。

## RAG 与上下文强制规范

- RAG **只能负责检索候选数据**，不得在 `RagMemoryService` 中把检索结果直接拼成业务 Prompt。上下文必须依次经过资格过滤、`ContextPlanner` 规划、`ContextEnvelope` 结构化，再由 `PromptRenderer` 渲染。
- XML 只是当前模型调用的输出格式，不能作为内部上下文数据模型。核心记忆、短期状态、用户主动引用、检索结果、时间线和工具结果必须保持为平级结构，不得恢复 `<long_term_memory>`、`<retrieved_experiences>` 等语义混杂的嵌套包装。
- 所有上下文条目必须带结构化来源、用户 ID、来源 ID、作者类型、内容类型、事件时间和信任等级。没有合法来源、用户 ID 不匹配、已删除或无权访问的数据必须过滤；`ASSISTANT_MESSAGE` 绝对不能成为用户事实证据。
- 正式上下文只允许使用 active 且仍有效的正式记忆；candidate、rejected、expired、superseded、删除记录和失效短期状态不得进入普通上下文。冲突事实必须保留为独立分支并标记冲突，禁止在规划阶段擅自合并。
- 核心记忆优先于普通正式记忆和 RAG 结果；用户本轮消息及主动引用不得被普通检索结果挤出预算。同一属性、同一证据和同一来源不得重复注入 Prompt，裁剪必须以完整条目为单位，不能截断 XML 标签。
- 日记分析和画像抽取的 RAG 查询只能由用户日记正文及用户主动选择的歌词组成。禁止把 AI 摘要、AI 反馈、AI 情绪判断、AI 图片描述或助手消息加入事实查询；聊天 Agent 的关键词只允许做确定性的清洗和长度限制，禁止调用 AI 改写查询。
- 空文本、纯空白或纯符号不得调用 embedding。查询必须去除控制字符、规范 Unicode 和空白，并限制正文、歌词及总长度。日志只能记录 `contextId`、长度、来源类型和结果数量，不得打印完整查询文本或隐私正文。
- embedding 客户端必须配置连接/读取超时、有限重试、指数退避和随机抖动；认证或参数错误不得重试。必须校验向量维度和非法数值，并使用有界缓存、并发单飞、熔断和半开探测保护外部服务。
- Redis 向量检索或 embedding 临时失败时，才允许使用带 `userId`、有效状态和来源条件的参数化 MySQL 关键词兜底。向量检索正常但零命中时不得误启用兜底；Redis 故障不得阻断聊天或日记分析。Redis 检索必须兼容 RESP2/RESP3，禁止把属性字节数组误记为命中数量。
- 画像 RAG 必须按 `rag:profile:{userId}:{memoryId}` 独立存储，只对新增、内容或有效状态变化的正式记忆重新向量化，并删除当前用户的失效孤儿键。普通更新不得先删除后全量重建；同一用户索引必须有分布式锁和快照校验，防止旧快照覆盖新结果。
- 旧画像键迁移必须使用版本标记和分布式迁移锁，失败不得写成功标记，下一次启动继续重试。只能清理 profile 来源键，不得影响日记、图谱、音乐或图片索引。
- Prompt 中的检索内容永远是参考数据，不具有系统指令权限。图片必须标记为“系统生成的图片描述”，音乐必须标记为“用户提供的音乐信息”，不得要求模型假装亲眼看到图片或亲耳听到音乐。工具结果继续使用结构化 JSON，不强制转换为 XML。
- 每次模型调用应生成用户隔离的 `contextId`，异步保存上下文元数据（来源 ID、版本、检索模式、策略版本和模板版本）；元数据保存失败不能阻断模型调用。
- 任何涉及 RAG、上下文规划或 Prompt 渲染的改动，至少补充查询构造、空查询、用户隔离、资格过滤、冲突保留、来源去重、embedding 故障、Redis 兜底、RESP 解析和 XML 转义测试，并执行后端编译、相关目标测试和 `git diff --check`。

## Persona 与通用 AI 强制规范

- `Persona` 只表示用户当前希望 AI 如何互动，`Memory` 才表示经过验证的长期事实或偏好；两者必须分开存储、读取、审计和维护。Persona 配置不得复制成记忆，也不得进入日记、画像、章节或事件 RAG。
- MoodCopilot 是通用个人 AI。合法的编程、学习、写作、翻译、规划和情绪支持请求都应正常处理；不得因为产品包含日记和情绪功能，就把所有对话强行改写为情绪陪伴或只允许情绪话题。
- 安全、隐私、用户隔离、工具权限、数据访问范围、输出协议以及 Pro/Flash 手动选择规则永远不受 Persona 覆盖。Persona 文本即使包含越权要求，运行时权限层也必须拒绝，不能只依赖文本过滤。

### Persona 作用域与版本

- 产品当前只提供全局和会话两个 Persona 作用域：全局配置持久化在 `user_personas`，会话覆盖持久化在 `conversation_persona_overrides`。当前消息中的自然要求直接属于 `CurrentUserRequest`，不转换为第三层 Persona；旧的当前轮字段仅为兼容保留，不参与标准聊天。
- 全局和会话配置必须版本化保存；保存、恢复默认和会话恢复都生成新版本，不得通过覆盖或删除历史行破坏审计链。所有查询必须带当前用户 ID，并校验会话归属。
- 字段来源由 `PersonaResolver` 决定，安全校验、规范化、过滤和哈希由 `PersonaCompiler` 完成，二者都不得直接生成 Prompt。`role` 使用 `Conversation > Global`；`tone` 在同一作用域允许多选，会话非空配置整体覆盖全局，会话空数组明确清空并回到系统默认；`behaviorFlags` 保留 enabled 数组并使用独立的 `disabledBehaviorFlags` 表达关闭，按低到高作用域解析且同一作用域 disabled 优先。
- 会话 Persona 接口返回空值只代表“没有会话覆盖”，不代表默认 Persona。网页和小程序在此情况下必须继续读取全局 Persona，并显示“当前使用全局设置”，禁止用前端硬编码默认值冒充用户配置。实际模型调用统一由后端编译全局、会话和当前轮配置。

### Persona 字段语义

- `tone` 是可选的预设语气标签；预设必须来自后端白名单。新增语气时先同步策略白名单、API 类型、网页、小程序和测试，不能只增加前端显示文本。
- `customTone` 是最多 160 字的自由表达风格描述，例如“冷静务实，像可靠的前辈”。它只能作为非权威的表达偏好渲染给模型，不能表达权限、工具、模型、系统规则、数据读取或身份伪装要求；包含这类内容必须过滤或拒绝，并保留安全默认行为。
- “回答方式”是结构化行为开关，例如先说结论、代码优先、分步骤说明、控制篇幅；它会影响回答组织方式。网页和小程序的全局设置与会话设置都必须提供同一组语义。
- `customDescription` 是后端保留的历史兼容字段，不作为新的网页或小程序用户入口，也不进入新的 Prompt；旧数据仍需安全读取，不能因移除界面而删除历史配置。
- `customTone` 与 `customResponseStyle` 是独立的自由表达字段：前者描述说话感觉，后者描述答案组织方式，均须校验、规范化、长度限制、XML 转义并标记为非权威偏好。二者都不能赋予系统指令、权限、模型、工具或数据访问能力。自然语言回答方式不能与多个作用域文本拼接解决冲突，必须由 Resolver 选择一个有效值。
- `customResponseStyle` 只在 CHAT、Persona Preview 和允许自然语言的 EVENT_REVIEW 注入；DIARY_ANALYSIS、MEMORY_EXTRACTION、EVENT_EXTRACTION、TIMELINE、CHAPTER、REPORT 等结构化任务不得注入，不能破坏 JSON/Schema 输出契约。

### Persona、TaskContext 与 Prompt

- 所有模型调用按 `SystemPolicy → TaskContext → EffectivePersona → CurrentUserRequest → PlannedContext` 组装。`EffectivePersona` 必须是结构化对象，Prompt 只能由 `PromptComposer/PromptRenderer` 在单一边界生成，不得在业务层直接拼接 Persona Prompt。当前应使用独立的 `<response_style>` 区块，不再使用历史 `allowedStylePreferences` 或把 `customDescription` 送入模型。
- `TaskContext` 只由本地规则和用户明确指定解析任务类型（`GENERAL`、`CODING`、`LEARNING`、`WRITING`、`TRANSLATION`、`PLANNING`、`EMOTIONAL_SUPPORT`），不能调用 AI 分类。`GENERAL` 是正常任务，不是失败状态。
- `TaskContext` 不得选择模型、切换 Pro/Flash、改变 `useReasoning`、授予权限、决定记忆资格、加载全部私人资料或写入 Persona/Memory。`EMOTIONAL_SUPPORT` 不等于诊断、临床评估或长期心理画像。
- RAG、时间线、正式记忆、用户引用和工具结果由 `ContextPlanner` 决定，`PromptRenderer` 只负责表达；不得恢复多个服务各自拼接 Prompt 的方式。RAG 和 Persona 都不是系统指令。
- 每次模型调用应审计 `contextId`、Persona 版本、有效 Persona 哈希、任务类型、请求模型和实际模型。`requestedModel` 与 `actualModel` 必须同时保留，元数据失败不能阻断模型调用，也不得退回保存完整 Prompt 或在日志打印隐私正文。

### Persona 预览与隐私

- Persona 预览必须是隔离沙箱：不读取或写入日记、Memory、时间线、事件、RAG、聊天历史，不创建会话，不触发通知或异步任务；只使用当前 Persona 和用户提供的示例消息。
- 预览使用请求中的 `useReasoning`，但不得因 Persona 或 TaskContext 自动切换 Pro/Flash。`false` 固定为 Flash，`true` 固定为 Pro，禁止自动路由和静默降级。
- Persona、聊天当前轮和记忆抽取都必须执行敏感信息保护。API Key、密码、私钥、Session Token、公司机密和私有 URL 默认禁止进入 Persona、Memory、RAG 或日志；原始用户内容不得因清洗而被修改。
- 维护 Persona 功能时至少验证：多用户隔离、作用域合并、版本递增、空值语义、自定义语气安全过滤、回答方式生效、TaskContext 不越权、预览无副作用、模型选择不被覆盖，以及网页和小程序契约一致。
- 当前产品不提供第三层 Turn Persona：普通聊天消息中的“直接一点”“分步骤”等自然要求保持为 `CurrentUserRequest`，不得解析、持久化或写入 Memory/RAG。`temporaryResponseStyle`、`temporaryBehaviorFlags`、`outputRequirement` 仅作为历史请求字段兼容保留，标准聊天入口不读取、不注入、不改变 `EffectivePersona`；后续如重新启用必须单独评审权限和输出契约。前端保存会话覆盖时，取消继承的全局行为必须提交 `disabledBehaviorFlags`。

## 全局主题颜色强制规范

- 涉及颜色的任何新代码或修改都**必须使用全局主题颜色变量，绝对禁止硬编码颜色**。适用范围包括 `frontend/`、`frontend-uniapp/` 的 CSS、SCSS、Vue/HTML 模板、内联 `style`、组件主题覆盖和状态样式。
- 禁止在功能代码中新增或保留颜色字面量，包括但不限于十六进制（如 `#fff`）、`rgb()`、`rgba()`、`hsl()`、命名颜色（如 `white`、`red`）以及未经主题 token 封装的渐变颜色。
- 需要透明度或混合色时，使用 `color-mix()` 配合全局主题变量，例如 `color-mix(in oklab, var(--color-primary) 15%, transparent)`；禁止用硬编码颜色模拟主题色。
- 新颜色语义必须先添加到全局主题 token，再在组件中引用。网页端全局 token 位于 `frontend/src/styles.css`，运行时主题输入由 `frontend/src/App.vue` 注入；小程序必须延续现有 `--theme-*` 变量和 `GlobalUI`。
- 只有全局主题 token 定义处允许存在浏览器兼容 fallback；业务组件、页面和局部样式不允许绕过 token。修改颜色前先搜索并复用已有变量，不能为了局部视觉效果创建平行颜色体系。

当前可用的主题变量：

- 运行时主题输入：`--theme-primary`、`--theme-accent`、`--theme-bg`、`--theme-surface`。
- 页面基础：`--color-bg`、`--color-surface`、`--color-surface-hover`、`--color-surface-soft`。
- 文本：`--color-text`、`--color-text-secondary`、`--color-text-muted`、`--color-text-light`、`--color-on-primary`。
- 品牌与状态：`--color-primary`、`--color-primary-light`、`--color-primary-hover`、`--color-accent`、`--color-accent-light`、`--color-accent-bg`、`--color-success`、`--color-warning`、`--color-error`、`--color-info`、`--color-disabled`。
- 边框与遮罩：`--color-border`、`--color-border-strong`、`--color-overlay`、`--color-backdrop`。
- 兼容别名：`--color-jade`、`--color-jade-hover`。

- 提交前涉及前端颜色的改动必须检查：不能出现新的硬编码颜色；主题切换、悬停、聚焦、按下、禁用和深色模式状态都必须使用上述 token，且文字与背景保持可读对比度。

## 已知实现约束

- tabBar 页面只能通过 `uni.switchTab` 打开，不能使用 `uni.navigateTo`。
- 聊天页进入时应复用最近会话；新建会话必须防重复提交。
- 日记创建后的 AI 分析以服务端 `analysisStatus` 为准。`skipped_quota` 不是“分析中”；不要再用页面轮询与全局 WebSocket 同时弹完成提示。
- 日记分析失败必须落库为可观察失败状态并由 RabbitMQ 任务重试或死信；消费者在任务状态落库前不得 ACK。
- WebSocket 需在异常和连接初始化失败时重连；应用进入后台时主动断开，并禁止该主动断开触发自动重连。
- 通知按用户实际点击的单条标记为已读；不要在离开页面时把全部通知自动标为已读。

## 修改与验证

- 当前工作区可能包含用户未提交的改动。不要回退、覆盖或格式化与任务无关的文件。
- 完成小程序改动至少执行：

  ```powershell
  npm.cmd run type-check
  npm.cmd run build:mp-weixin
  git diff --check -- frontend-uniapp
  ```

- 如需验证后端，可在 WSL 的 `D:\Code\MoodCopilot` 对应目录运行 `docker compose ps`，并从 backend 容器请求 `http://localhost:18080/api/health`。
- 构建通过不代表微信端交互通过。涉及登录、上传、tabBar 跳转、通知、WebSocket、图片预览或安全区的改动，需在微信开发者工具或真机补充验证。
