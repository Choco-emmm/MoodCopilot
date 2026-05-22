# MoodCopilot v1.6.0 Release Notes

## 🚀 DeepSeek 思考模型全面升级

### 纯流式 Agent Loop
- **自写递归 Agent Loop**：基于 `Flux.defer` + `concatWith` 实现真正的边生成边推流，告别阻塞式 `collectList()`
- 支持 DeepSeek-v4-pro 推理模型的 **原生 Function Calling（Tool Calls）**
- 5 个内置工具 Strict Mode JSON Schema：日记检索、情绪统计、报告快照、长期画像、知识图谱
- 工具调用发生在后端拦截执行，用户无感知，模型拿到数据后继续推理

### 多轮思考渲染
- 前端 `parseThink` 升级为正则引擎，支持 **多轮 `<think>` 块的合并提取**
- 流式输出中的未闭合 `<think>` 标签不再泄露到正文
- 思考过程中显示 "✨ MoodCopilot 正在深度思考" 动画占位

### 工具引用卡片
- LLM 调用工具查询到的日记/画像记录，通过 SSE 实时推送前端 **引用卡片**
- 引用卡片位于气泡上方，位置固定不受正文流式增长影响
- 工具名汉化（diarySearch → 日记，memoryQuery → 画像 等）

---

## 🧠 日记知识图谱

- AI 自动从日记中提取 **因果/情绪三元组**（如"失眠 → 导致 → 焦虑"）
- 双存储：MySQL 结构化 + Redis 向量索引
- 聊天中通过 `graphSearch` 工具按关键词查询图谱，回答因果追溯问题
- 日记编辑/删除时自动更新图谱三元组

---

## 🤖 AI 分析体验优化

- AI 分析链路重构为 **Redis Stream + MQ 异步队列**，后台逐步消费
- 编辑日记时自动清除旧分析结果，前端重新轮询弹窗
- 管理员 AI 额度不限（角色感知 RateLimitService）
- 分析提示语精简，状态自动清理

---

## 🔐 安全与基础设施

- **天爱滑块验证码**替换 Cloudflare Turnstile，更好适配国内网络环境
- 验证码错误信息正常透传（Security 允许 ERROR dispatch）
- DeepSeek API 请求体精准注入拦截器（Jackson 树模型修复 400 问题）
- AI 异步线程池升级为虚拟线程模式，大并发 I/O 密集型请求吞吐量大幅提升

---

## 📊 配额系统重构

- 配额表扁平化，等级增长更线性
- PRO 用户框架预留
- 管理员（ADMIN）无额度限制
- 前端同步展示完整配额信息（聊天 / 分析 / 图片 / 报告）

---

## 📱 移动端体验

- 修复移动端水平溢出（`min-width: 0` + overflow 约束）
- 移动端键盘弹起 / 关闭时输入框自适应定位
- 对话页面移动端新建 / 删除 / 切换会话 UI 优化
- 弹窗 `max-width` 适配小屏

---

## 🎨 UI / UX 优化

- 编辑器发布按钮文案优化
- 日记字数上限 1000 → 3000
- Vditor 编辑器 destroy 崩溃修复
- Vditor 工具提示裁剪修复
- 日记详情页水平居中
- 聊天消息间距收紧，底部导航阴影移除
- 大厅管理员标识显示

---

## 🐛 Bug 修复

- `graphSearch` Bean 缺失导致聊天抛出 `IllegalArgumentException`
- 工具引用 JSON（`[[TOOL_EVENT]]`）泄露到聊天正文流
- `JsonUtils` 对 `deepseek-reasoner` 输出 `<think>` 标签的兼容
- `MemoryConsolidationService` JSON 解析异常处理
- DeepSeek `max-tokens` 提升至 8192 防止长回复截断

---

## 🏗️ 架构变更

| 模块 | 变更 |
|------|------|
| AI 分析管道 | 同步调用 → Redis Stream MQ 异步队列 |
| 思考模型接入 | Spring AI 黑盒 → 自写原生 WebClient 纯流式管线 |
| 函数调用 | Spring AI `FunctionCallback` → 自研 Strict Mode Tool Schema + Agent Loop |
| 对话记忆 | Advisor 自动维护 → 推理分支手动 ChatMemory 注入 + 前端富文本持久化 |
| 验证码 | Cloudflare Turnstile → 天爱滑块 |
