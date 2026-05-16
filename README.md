# MoodCopilot

温暖、共情的情绪伙伴 —— 先帮你看见情绪，再把你温和地连接给相似心情的人。

## 功能

### 情绪日记
- 创建、查看、删除日记，支持公开/私密切换
- AI 自动分析：情绪标签、强度、话题、关键词、摘要、共情回应
- 发布后推荐 3 篇情绪相近的公开日记

### AI 对话
- 针对单篇日记或近期情绪状态深度聊天
- 双模型路由：工具调用走常规模型，日记引用走推理模型
- 思考中动画气泡、Markdown 富文本渲染

### 社区
- 公开日记广场，按情绪/话题筛选
- 关注/取关用户，关注流
- 评论、回复、共鸣反馈
- 消息通知

### 其他
- 周报、月报
- 管理员面板与内容审核
- 头像上传与裁剪

## 技术栈

| 层 | 技术 |
|---|------|
| 后端 | Spring Boot 3.5 + Java 21 + Spring AI + MyBatis-Plus |
| 数据库 | MySQL 8.0 + Redis 7 |
| 安全 | Spring Security + JWT + 邮箱验证码 + 接口速率限制 |
| 前端 | Vue 3 + TypeScript + Vite + Naive UI |
| AI | DeepSeek 双模型路由（Reasoning + Chat） |
| 部署 | Docker Compose + Nginx + Cloudflare Tunnel |
| 测试 | Playwright E2E |

## 项目结构

```
MoodCopilot/
├── backend/moodcopilot/         # Spring Boot 后端
│   └── src/main/java/com/moodcopilot/
│       ├── ai/                  # AI 聊天、意图路由、推理模型调度
│       ├── auth/                # JWT 认证、登录注册
│       ├── diary/               # 日记 CRUD、公开流、相似推荐
│       ├── follow/              # 关注系统
│       ├── notification/        # 推送通知
│       ├── report/              # 周报
│       ├── summary/             # 摘要
│       ├── admin/               # 管理员接口
│       ├── security/            # Spring Security 配置
│       └── mapper/              # MyBatis 映射
├── frontend/                    # Vue 3 前端
│   └── src/
│       ├── pages/               # 页面组件（10 个）
│       ├── components/          # 可复用组件（11 个）
│       ├── api/                 # Axios 请求层
│       ├── stores/              # Pinia 状态管理
│       └── router/              # 路由配置
├── docs/roadmap.md              # 产品路线图
├── scripts/                     # 运维脚本
└── docker-compose.yml           # Docker 编排
```

## 快速开始

### 环境要求

- Java 21
- Maven 3.9+
- Node.js 20+
- MySQL 8.0
- Redis 7

### 本地开发

```bash
# 一键启动（后端 + 前端 + 健康检查）
npm run app:start

# 带 Cloudflare Tunnel 公网访问
npm run public:start

# 重启
npm run app:restart

# 诊断
npm run app:doctor
```

或手动分步启动：

```bash
# 启动 MySQL / Redis 后

# 后端（端口 18080）
cd backend/moodcopilot
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 前端（端口 5173）
cd frontend
npm install
npm run dev
```

### Docker 部署

```bash
# 创建 .env 文件，填入所需环境变量
cp .env.example .env

# 启动全部服务
docker compose up -d
```

服务端口：

| 服务 | 端口 |
|------|------|
| 前端 (Nginx) | 80 |
| 后端 (Spring Boot) | 18080（内网） |
| MySQL | 3306（内网） |
| Redis | 6379（内网） |

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_HOST` | MySQL 地址 | `localhost` |
| `DB_PORT` | MySQL 端口 | `3306` |
| `DB_NAME` | 数据库名 | `mood_copilot_db` |
| `DB_USERNAME` | 数据库用户 | `root` |
| `DB_PASSWORD` | 数据库密码 | — |
| `REDIS_HOST` | Redis 地址 | — |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | — |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | — |
| `DEEPSEEK_BASE_URL` | DeepSeek API 地址 | `https://api.deepseek.com` |
| `DEEPSEEK_MODEL` | 常规对话模型 | `deepseek-chat` |
| `DEEPSEEK_REASONING_MODEL` | 推理模型 | `deepseek-reasoner` |
| `JWT_SECRET` | JWT 签名密钥 | 内置开发默认值 |
| `MAIL_HOST` | SMTP 服务器 | `smtp.qq.com` |
| `MAIL_PORT` | SMTP 端口 | `465` |
| `MAIL_USERNAME` | 发件邮箱 | — |
| `MAIL_PASSWORD` | SMTP 授权码 | — |

## AI 架构

### 双模型路由

- **LLM 语义分类器**（2s 超时 → 规则降级）
- **功能调用意图**（报告/总结/回顾/查询等关键词）→ 常规模型，挂载 Function Calling
- **日记引用意图** → 推理模型，深度分析
- **5 分钟惯性锁定**防模型横跳

### System Prompt 分层

```
<long_term_memory>          ← 长期画像（JSON 事实列表，反注入保护）
  ...
</long_term_memory>

【绝对核心聚焦指令】          ← 引用日记时注入
<user_diary>
  ...日记切片...
</user_diary>

【核心行为准则】              ← 始终注入（推理模型 + 常规模型共用）
  1. 日常闲聊简短温暖（2-3句）；用户引用日记或要求分析时自然展开
  2. 禁止 emoji / 角色扮演 / 轻浮口语
  3. 禁止自称"心理咨询师/AI助手/经历了日记事件"
  4. 支持 Markdown 格式

【Agent Tools】               ← 仅常规模型路径
  工具检索结果的措辞规范，区分"用户主动提及"与"系统检索查到"
```

### 长期画像（Memory Extraction）

- **双源提取**：日记分析完成后异步提取 + 聊天完成后经 4 层门控增量提取
- **幂等同步**：MySQL UNIQUE KEY (user_id, attribute_key)，LLM 输出完整替换旧属性列表
- **安全防护**：空属性列表不执行同步；用户手动删除的属性 key 进入 Redis 黑名单，防止被重新生成
- **提取 Prompt** 包含 3 个 few-shot 示例（稳定特征提取 / 一次性状态不提取 / 新证据更新旧属性）
- **门控优化**：短消息包含长期关键词（"总是""一直""失眠"等）时跳过长度门槛

### 推理模型独立策略

- 历史记忆以 `<chat_history>` XML 标签注入 User Message，保持 System Context 纯洁
- Agent Tools 提示词仅注入常规模型路径

## 路线图

详见 [docs/roadmap.md](docs/roadmap.md)，当前处于 Phase 1–2 阶段。
