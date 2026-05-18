# MoodCopilot

温暖、共情的情绪伙伴 —— 先帮你看见情绪，再把你温和地连接给相似心情的人。

## 功能

### 情绪日记
- 创建、查看、删除日记，支持公开/私密切换
- AI 自动分析：情绪标签、强度、话题、摘要、共情回应
- 分析完成后弹窗展示摘要，一键跳转日记详情
- 发布后推荐 3 篇情绪相近的公开日记

### AI 对话
- 针对单篇日记或近期情绪状态深度聊天
- 双模型路由：工具调用走常规模型，日记引用走推理模型
- RAG 向量记忆：语义搜索历史日记、聊天记录和长期画像，理解用户过往状态
- HyDE 多轮感知重写：结合最近对话上下文理解追问的隐含意图
- 思考中动画气泡、Markdown 富文本渲染

### 社区
- 公开日记广场，按情绪/话题筛选
- 关注/取关用户，关注流
- 评论、回复、共鸣反馈
- 消息通知

### 其他
- 周报、月报（情绪四象限分布）
- AI 陪跑建议
- 管理员面板与内容审核
- 头像上传、裁剪与长期缓存

## 技术栈

| 层 | 技术 |
|---|------|
| 后端 | Spring Boot 3.5 + Java 21 + Spring AI + MyBatis-Plus |
| 数据库 | MySQL 8.0 + Redis Stack (含 RediSearch 向量引擎) |
| 安全 | Spring Security + JWT + 邮箱验证码 + 接口速率限制 |
| 前端 | Vue 3 + TypeScript + Vite + Naive UI + marked |
| AI | DeepSeek 双模型路由 (Reasoning + Chat) |
| 向量模型 | BAAI/bge-m3 (SiliconFlow API, 1024 维) |
| 部署 | Docker Compose + Nginx + Cloudflare Tunnel |
| 测试 | Playwright E2E |

## 项目结构

```
MoodCopilot/
├── backend/moodcopilot/         # Spring Boot 后端
│   └── src/main/java/com/moodcopilot/
│       ├── ai/                  # AI 聊天、意图路由、推理模型调度、RAG、画像提取
│       ├── auth/                # JWT 认证、登录注册
│       ├── diary/               # 日记 CRUD、公开流、相似推荐
│       ├── follow/              # 关注系统
│       ├── notification/        # 推送通知
│       ├── report/              # 周报/月报
│       ├── admin/               # 管理员接口、RAG 回填
│       ├── security/            # Spring Security 配置
│       ├── config/              # AI/Web/Cache 配置
│       └── mapper/              # MyBatis 映射
├── frontend/                    # Vue 3 前端
│   └── src/
│       ├── pages/               # 页面组件
│       ├── components/          # 可复用组件
│       ├── api/                 # Axios 请求层
│       ├── stores/              # Pinia 状态管理
│       ├── utils/               # Markdown 渲染等工具
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
- Redis Stack (redis-stack-server, 含 RediSearch 模块)

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
# 启动 MySQL / Redis Stack 后

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
cp .env.example .env   # 填入所需环境变量
docker compose up -d    # 启动全部服务（含 redis-stack-server）
```

服务端口：

| 服务 | 端口 |
|------|------|
| 前端 (Nginx) | 80 |
| 后端 (Spring Boot) | 18080（内网） |
| MySQL | 3306（内网） |
| Redis Stack | 6379（内网） |

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
| `SILICONFLOW_API_KEY` | 向量模型 API Key | — |
| `SILICONFLOW_EMBEDDING_URL` | 向量模型地址 | — |
| `SILICONFLOW_EMBEDDING_MODEL` | 向量模型名称 | `BAAI/bge-m3` |
| `JWT_SECRET` | JWT 签名密钥 | 内置开发默认值 |
| `MAIL_HOST` | SMTP 服务器 | `smtp.qq.com` |
| `MAIL_PORT` | SMTP 端口 | `465` |
| `MAIL_USERNAME` | 发件邮箱 | — |
| `MAIL_PASSWORD` | SMTP 授权码 | — |

## AI 架构

### 双模型路由

- **LLM 语义分类器**（2s 超时 → 规则降级）
- **功能调用意图**（报告/总结/回顾/查询等关键词）→ 常规模型，挂载 4 个 Function Calling 工具 (diarySearch / userStats / reportSnapshot / memoryQuery)
- **日记引用意图** → 推理模型，深度分析
- **5 分钟惯性锁定**防模型横跳

### RAG 向量记忆

- **向量引擎**: Redis Stack + HNSW 索引，BAAI/bge-m3 1024 维余弦距离
- **数据源分类**: diary（日记）、chat（聊天）、profile（长期画像）三类统一索引，按 source_type 过滤
- **长日记分块**: >500 字按 400 字/块 + 50 字重叠切分，每块独立索引
- **HyDE 查询重写**: 将用户口语化输入改写为第一人称日记风格陈述句（30-80 字），多轮对话中结合最近聊天历史理解隐含意图
- **安全阈值**: 剔除 cosine distance ≈0 的自我重复记录和 >1.0 的无关噪音
- **回填与管理**: 管理员可通过 `/api/admin/reports/rag/reindex` 批量重建索引

### System Prompt 分层

```
<long_term_memory>               ← 长期画像
<system_metadata>                ← 当前系统时间（供工具计算绝对日期）
<chat_history>                   ← 最近对话历史（3000 字预算，新消息优先）
<rag_retrieved_context>          ← RAG 向量检索到的相关历史片段
<user_data_context>              ← 推理模型专属：最近 14 天情绪统计 + 长期画像

【记忆与当前上下文的优先级铁律】
  绝对优先结合 <chat_history> 回答追问；RAG 旧记录与当前话题脱节时果断忽略

【长期画像与聊天风格规范】
  禁止角色扮演、禁止轻浮口语、禁止称"心理咨询师/AI助手"

【工具检索结果的话术规范】
  严格区分用户主动分享 vs 系统后台检索，禁止使用"你分享的""正如你提到的"
  必须使用"我帮你查了一下""根据你的历史记录显示"等系统检索措辞

【Agent Tools】
  并行工具调用指引，4 个 Function Calling 工具注册说明
```

### 长期画像提取

- **双源触发**: 日记分析完成后异步提取 + 多轮对话后经 4 层门控增量提取
- **幂等同步**: MySQL UNIQUE KEY (user_id, attribute_key)
- **安全防护**: 空属性列表不执行同步；用户手动删除的属性进入黑名单防回写
- **向量同步**: 提取后自动索引到 RAG (profile source_type)，编辑/删除时同步更新

### 推理模型独立策略

- 历史记忆以 `<chat_history>` 标签注入 User Message，保持 System Context 纯洁
- Agent Tools / RAG / Function Calling 仅注入常规模型路径
- 推理模型路径通过 `buildReasoningDataContext` 预取数据（情绪统计 + 画像）

## 路线图

详见 [docs/roadmap.md](docs/roadmap.md)，当前处于 Phase 2 阶段。
