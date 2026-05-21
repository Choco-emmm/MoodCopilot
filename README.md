<p align="center">
  <img src="docs/logo.svg" alt="MoodCopilot" width="120" />
</p>

<h1 align="center">MoodCopilot</h1>

<p align="center">
  <strong>温暖、共情的 AI 情绪伙伴</strong><br />
  先帮你看见情绪，再把你温和地连接给相似心情的人。
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green" alt="License" /></a>
  <a href="#"><img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21" /></a>
  <a href="#"><img src="https://img.shields.io/badge/Vue-3.x-4fc08d" alt="Vue 3" /></a>
  <a href="#"><img src="https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen" alt="Spring Boot 3.5" /></a>
</p>

---

**English**: MoodCopilot is an AI-powered emotional journaling app. Write diaries, share music, upload images — an AI companion analyzes your moods, retrieves relevant memories via vector search, and gently connects you with people feeling the same way.

## ✨ 功能

### 📝 情绪日记
- 纯文本编辑，支持公开/私密切换
- AI 自动分析：情绪标签、强度、话题、摘要、共情回应
- **音乐分享**：粘贴网易云链接自动解析歌名/歌手/封面/歌词，AI 结合歌曲意境分析
- **图片上传**：浏览器直传 OSS，VLM（qwen3-vl-flash）自动描述画面，融入 AI 分析

### 💬 AI 对话
- 双模型路由：工具调用走常规模型，深度分析走推理模型
- RAG 向量记忆：语义搜索历史日记、音乐、图片描述和长期画像
- HyDE 查询重写：结合对话上下文理解追问的隐含意图

### 🌐 社区
- 公开日记广场，按情绪/话题发现同频的人
- 关注/取关、评论、回复、共鸣反馈
- WebSocket 实时推送通知

### 📈 成长系统
- 写日记、签到、获赞等行为获取 EXP
- 等级越高 AI 调用额度越多
- 每日任务中心，签到+领取奖励

### 📊 数据报告
- 周报/月报，情绪四象限分布可视化
- AI 陪跑建议与每日关怀推送

## 🛠 技术栈

| 层 | 技术 |
|---|------|
| 后端 | Spring Boot 3.5 + Java 21 + Spring AI + MyBatis-Plus |
| 数据库 | MySQL 8.0 + Redis Stack (RediSearch 向量引擎) |
| 安全 | Spring Security + JWT + 邮箱验证码 + Cloudflare Turnstile |
| 前端 | Vue 3 + TypeScript + Vite + Naive UI |
| AI | DeepSeek 双模型 (Reasoning + Chat) + DashScope VLM (qwen3-vl-flash) |
| 向量模型 | BAAI/bge-m3 (SiliconFlow API, 1024 维) |
| 存储 | 阿里云 OSS（浏览器直传） |
| 部署 | Docker Compose + Nginx |

## 🚀 快速开始

### 环境要求

- Java 21+ & Maven 3.9+
- Node.js 20+
- MySQL 8.0
- Redis Stack（含 RediSearch 模块）

### 1. 克隆并配置

```bash
git clone https://github.com/Choco-emmm/MoodCopilot.git
cd MoodCopilot
cp .env.example .env
# 编辑 .env 填入你的 API Key 和数据库密码
```

### 2. 启动后端（端口 18080）

```bash
cd backend/moodcopilot
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. 启动前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

### Docker 部署

```bash
cp .env.example .env
docker compose up -d    # 启动全部服务
```

| 服务 | 端口 |
|------|------|
| 前端 (Nginx) | 80 |
| 后端 (Spring Boot) | 18080（内网） |
| MySQL | 3306（内网） |
| Redis Stack | 6379（内网） |

## 📁 项目结构

```
MoodCopilot/
├── backend/moodcopilot/         # Spring Boot 后端
│   └── src/main/java/com/moodcopilot/
│       ├── ai/                  # AI 聊天、RAG 记忆、VLM、意图路由
│       ├── auth/                # JWT 认证、登录注册
│       ├── diary/               # 日记 CRUD、公开流、相似推荐
│       ├── music/               # 网易云音乐解析
│       ├── oss/                 # OSS 上传/转正/删除
│       ├── growth/              # 等级经验系统
│       ├── notification/        # WebSocket 推送
│       ├── report/              # 周报/月报
│       └── security/            # 安全、限流
├── frontend/                    # Vue 3 前端
│   └── src/
│       ├── pages/               # 页面组件
│       ├── components/          # 可复用组件
│       ├── api/                 # Axios 请求层
│       ├── stores/              # Pinia 状态管理
│       └── router/              # 路由配置
├── docs/                        # 文档和路线图
├── scripts/                     # 运维脚本
├── docker-compose.yml
├── .env.example                 # 环境变量模板
└── LICENSE
```

## 🧠 AI 架构

### 双模型路由
- **LLM 语义分类器** → 意图识别（2s 超时规则降级）
- **功能调用意图** → 常规模型 + 4 个 Function Calling 工具
- **日记引用意图** → 推理模型深度分析
- 5 分钟惯性锁定防模型横跳

### RAG 向量记忆
- Redis Stack HNSW 索引 + BAAI/bge-m3（1024 维）
- **多模态独立索引**：正文 / 音乐 / 图片描述三种 source_type 独立建向量
- **回表组装**：命中后从 MySQL 查完整 DiaryEntity，拼装结构化 XML 上下文
- 长日记分块（400 字/块 + 50 重叠）、HyDE 查询重写、质量过滤

### 长期画像
- 日记分析 + 多轮对话后异步提取人格特质
- MySQL UNIQUE KEY 幂等同步，自动索引到 RAG

## 🔒 隐私安全

- **基础设施保障**：服务器部署在腾讯云，享受云平台层面的安全防护
- **访问控制**：生产环境登录需二次验证，严格限制操作权限
- **接口防护**：完善的防刷与限流机制，从接口层面杜绝恶意攻击和数据爬取
- **数据伦理底线**：开发者承诺不查看任何用户的私密日记和 AI 对话记录，这是产品最基本的原则

如有任何隐私安全方面的顾虑，欢迎随时通过 Issue 提出。

## 🌍 环境变量

完整列表见 [.env.example](.env.example)，关键变量：

| 变量 | 说明 |
|------|------|
| `DB_HOST/NAME/USERNAME/PASSWORD` | MySQL 连接 |
| `REDIS_HOST/PORT/PASSWORD` | Redis Stack 连接 |
| `DEEPSEEK_API_KEY` | DeepSeek API Key |
| `SILICONFLOW_API_KEY` | 向量模型 API Key |
| `OSS_ACCESS_KEY/SECRET_KEY` | 阿里云 OSS |
| `VISION_API_KEY` | DashScope VLM |
| `JWT_SECRET` | JWT 签名密钥 |
| `MAIL_*` | 邮箱验证码 SMTP |

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feat/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feat/amazing-feature`)
5. 创建 Pull Request

## 📄 许可

[MIT License](LICENSE)

---

<p align="center">
  <sub>Made with ❤️ by Choco &amp; MoodCopilot AI</sub>
</p>
