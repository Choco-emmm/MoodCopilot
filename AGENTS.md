# MoodCopilot Agent Guide

## 产品边界

- `frontend/` 是网页端，包含完整社区能力。
- `frontend-uniapp/` 是微信小程序，定位为私密的日记、AI 陪伴与个人成长工具。
- 小程序**不提供**广场、关注、用户主页、评论、点赞/共鸣、举报或其他公开社交入口。
- 不要因为网页端存在这些页面或后端已有相应接口，就在小程序添加路由、入口、互动按钮或推送跳转。

## 功能对齐原则

- 小程序应与网页端保持一致的是非社交的个人能力：日记创建、编辑、删除、图片与音乐、AI 分析、聊天、记忆与图谱、报告、合集、成长、通知、设置和个人资料。
- 功能对齐不等于照搬网页布局。小程序优先保证单手操作、列表滚动、低首屏开销和明确的页面层级。
- 新增或调整小程序页面时，延续现有主题变量（`--theme-*`）、`GlobalUI`、自定义 tabBar 与安全区处理；不要引入另一套颜色、字体或导航体系。
- 合集是私密内容组织能力，不应改造成公开内容或社交收藏功能。
- 重要事件和时光画卷是私密个人能力；事件仅允许 `PENDING` 与 `FOLLOWED_UP`，历史 `ARCHIVED` 只兼容显示为已跟进，不得新增归档状态。聊天入口只注入事件上下文，不自动修改状态。
- 重要事件的日期和时间必须按后端配置的业务时区解释，不能直接依赖开发机、容器或数据库的系统默认时区。当前业务时区默认为 `Asia/Shanghai`，配置项为 `moodcopilot.time-zone`，环境变量为 `MOODCOPILOT_TIME_ZONE`。
- 重要事件回访时间规则必须保持一致：只有开始日期时按开始日 `00:00` 到期；有结束日期但没有结束时间时按结束日 `23:59:59.999999999` 到期；有结束时间时按结束日期的结束时间到期；只有开始时间时按开始日期的开始时间到期。
- 事件表中的 `targetDate`、`endDate`、`startTime`、`endTime` 是业务时区下的本地日期/时间值。后端到期判断必须使用配置时区生成当前时间；前端不得用浏览器时区改写用户选择的值。未来若支持用户级时区，必须显式增加用户时区字段和转换规则，不能隐式改变现有事件语义。
- 长期记忆的正式写入唯一经过 `MemoryOrchestrator`；候选记忆、证据、拒绝指纹和 `previousMemoryId` 版本链必须保留可追溯关系。

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
