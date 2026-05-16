# MoodCopilot

温暖、共情的情绪伙伴 —— 先帮你看见情绪，再把你温和地连接给相似心情的人。

## 技术栈

| 层 | 技术 |
|---|------|
| 后端 | Spring Boot 3 + Spring AI + MyBatis-Plus + MySQL + Redis |
| 前端 | Vue 3 + TypeScript + Vite + Naive UI |
| AI | DeepSeek (Reasoning + Chat) 双模型路由 |
| 部署 | Docker Compose + Nginx |

## AI 架构（最近重构）

### 双模型路由 (`ChatIntentRouter`)
- LLM 语义分类器（2s 超时 → 规则降级）
- 工具调用意图优先检测：检测到"报告/总结/回顾/查询"等关键词时强制走常规模型（挂载 Function Calling）
- 引用日记时强制走推理模型（深度分析）
- 5 分钟惯性锁定防模型横跳

### System Prompt 架构 (`ChatService.buildContext`)

```
<long_term_memory>                  ← 长期画像
  ...
</long_term_memory>

【绝对核心聚焦指令】                   ← 引用日记时注入
<user_diary>
  ...日记切片...
</user_diary>

【绝对系统指令】                       ← 末尾兜底，反角色扮演
  身份：MoodCopilot（倾听者/情绪伙伴）
  禁止：自称"心理咨询师/AI助手/经历了日记事件"
```

### 推理模型 (`callReasoningModel`)
- 历史记忆以 `<chat_history>` XML 标签注入 User Message（非 System Prompt），保持 System Context 纯洁
- Agent Tools 提示词仅注入常规模型路径（挂载 `.functions()` 时）

### 前端
- 思考中动画气泡（星芒 emoji + 打字机省略号动画）
- Markdown 富文本渲染（marked + DOMPurify）
- 底部导航栏 Flex 均分自适应
- 引用日记完整上下文传输（fullContent 不截断）
