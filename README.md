<p align="center">
  <img src="./docs/logo.svg" alt="MoodCopilot" width="120" />
</p>

<h1 align="center">MoodCopilot (情绪副驾)</h1>

<p align="center">
  <strong>🫂 温暖、共情、懂你的开源 AI 情绪伙伴</strong><br />
  <em>“先帮你看见情绪，再把你温和地连接给相似心情的人。”</em>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-AGPL--3.0-blue" alt="License" /></a>
  <a href="#"><img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21" /></a>
  <a href="#"><img src="https://img.shields.io/badge/Vue-3.x-4fc08d" alt="Vue 3" /></a>
  <a href="#"><img src="https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen" alt="Spring Boot 3.5" /></a>
  <a href="#"><img src="https://img.shields.io/badge/AI-DeepSeek-blue" alt="DeepSeek" /></a>
</p>

<h3 align="center">
  👉 <a href="https://moodcopilot.dpdns.org/">正式上线，点此开始记录 (Production Ready)</a> 👈
</h3>

---

> **👋 寻找种子用户与贡献者！**
> MoodCopilot 已经正式上线并稳定运行中！**（我本人每天都在上面写日记，所以这是一个绝对不会删档的长期服务，请放心把你的情绪交给我妥善保管。）** 无论你是想寻找一个专属的赛博树洞，还是想探索前沿的 AI 应用开发（RAG、双模型路由、多模态），我都非常欢迎你的加入！去创造专属于你的情绪自留地吧。

**MoodCopilot** 是一款基于大语言模型构建的情绪日记与社交应用。它不仅是一个可以记录纯文本、音乐、图片的树洞，更是一个拥有长期记忆的 AI 伴侣。它能敏锐地捕捉你的情绪波动，为你提供深度共情的回复，甚至通过向量检索，在茫茫人海中帮你找到此时此刻和你有着相似心情的“同频者”。

## ✨ 为什么选择 MoodCopilot？（核心体验）

### 📝 懂你的“多模态”日记本
*   **AI 自动洞察**：随手记录几句碎碎念，AI 会自动为你提取情绪标签、计算情绪强度、生成摘要，并给予最温暖的共情回应。
*   **网易云音乐解析**：粘贴网易云歌曲链接，自动抓取封面与歌词，AI 会结合歌曲的意境深度分析你的心境。
*   **图片直传与视觉理解**：支持一键上传图片，内置 qwen3-vl-flash 视觉大模型自动描述画面细节，将你眼前的风景无缝融入你的情绪画像中。

### 🧠 拥有长期记忆的 AI 伴侣
*   **RAG 向量记忆**：TA 不仅记住你的过去，还能在对话中随时引用你曾经写过的日记、分享的音乐和图片，提供连续不断的关怀。
*   **长期人格画像**：随着互动加深，AI 会在后台异步提炼你的人格特质，变得越来越“懂你”。
*   **双模型智能路由**：底层统一使用 DeepSeek-V4-Flash。日常闲聊时极速响应，涉及复杂情绪分析和深度日记回顾时，自动无缝开启“深度思考”模式。

### 🌌 寻找同频共振的星空
*   **情绪广场**：如果你愿意将日记设为公开，它们将会展示在广场上，与大家分享此时此刻的心情。
*   **灵魂共鸣匹配（规划中）**：我计划在未来加入基于你数据的特殊向量搜索功能，帮你找到在这个世界上与你“灵魂共鸣”的人。不过这需要等社区的人多起来之后才有意义，所以快来成为第一批原住民吧！
*   **实时温暖互动**：支持关注、点赞、评论与独特的“共鸣”反馈，新消息通过 WebSocket 实时推送到你的通知中心。
*   **守护社区氛围**：内置完整的举报机制与管理员后台审核，保障每一份真诚都不被辜负。

### 📈 陪伴式的成长轨迹
*   **记录即成长**：写日记、签到、获取共鸣皆可积累经验值 (EXP)。
*   **动态权益激励**：等级越高，解锁的 AI 深度对话调用额度越高，见证你的心理成长。
*   **数据可视化图表**：提供情绪四象限分布周报/月报，帮你以上帝视角俯瞰近期的内心轨迹。

---

## 🛠 极客之选（技术亮点）

本项目也是一个极其完善的“全栈 AI 落地应用”学习与二次开发模板：

| 模块 | 核心技术选型 |
|---|---|
| **后端架构** | Spring Boot 3.5 + Java 21 + Spring AI + MyBatis-Plus |
| **存储底座** | MySQL 8.0 + Redis Stack (利用 RediSearch 实现原生、高性能的 HNSW 向量检索) |
| **安全体系** | Spring Security + JWT 无状态认证 + Cloudflare Turnstile 防机审人机验证 |
| **前端交互** | Vue 3 + TypeScript + Vite + Naive UI (响应式、现代化的 UI 设计风格) |
| **AI 模型栈** | **DeepSeek** (Reasoning 推理模型 + Chat 对话模型) + **DashScope VLM** (qwen-vl 等) |
| **RAG 引擎** | BAAI/bge-m3 (SiliconFlow API 1024 维密集向量嵌入), 支持多模态独立建库、HyDE 查询重写、长文本滑动窗口分块。 |
| **部署架构** | 阿里云 OSS 浏览器直传（极低服务器带宽压力）、Docker Compose 容器化编排部署、Nginx 反代。 |

---

## 🚀 极速部署指引

只需简单的几步，即可在本地跑起你的专属情绪副驾。

### 前置要求
- Docker & Docker Compose (推荐，最省心)
- 或者手动配置：Java 21+, Node.js 20+, MySQL 8.0, Redis Stack

### 🐳 方式一：Docker 一键启动（推荐）

```bash
# 1. 克隆仓库
git clone https://github.com/Choco-emmm/MoodCopilot.git
cd MoodCopilot

# 2. 配置环境变量
cp .env.example .env
# 🚨 重要：请务必使用文本编辑器打开 .env 文件，填入你的 API Key (DeepSeek, 向量模型等) 和数据库密码。

# 3. 启动所有服务
docker compose up -d
```
启动完成后，访问 `http://localhost` 即可体验。

### 💻 方式二：本地源码开发调试

**1. 启动后端 (端口 18080):**
```bash
cd backend/moodcopilot
# 确保你已在 .env 模板中配置好所需环境变量，或放入系统环境
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**2. 启动前端 (端口 5173):**
```bash
cd frontend
npm install
npm run dev
```

---

## 📂 项目结构概览

```text
MoodCopilot/
├── backend/moodcopilot/         # Spring Boot 后端核心
│   └── src/main/java/com/moodcopilot/
│       ├── ai/                  # 🤖 AI 智能体编排：双模型路由、HyDE 重写、RAG 记忆重组
│       ├── diary/               # 📝 核心业务：日记 CRUD、公开流、相似度同频推荐
│       ├── music/               # 🎵 网易云音乐外链自动解析与意境抓取
│       ├── oss/                 # ☁️ 阿里云 OSS 浏览器直传 STS 鉴权管理
│       ├── admin/               # 🛡️ 社区治理：内容安全与举报审核管理
│       ├── growth/              # 📈 用户激励体系：等级、任务、签到、AI 额度
│       └── notification/        # 🔔 WebSocket 实时消息通知总线
├── frontend/                    # Vue 3 渐进式前端应用
│   └── src/
│       ├── pages/               # 核心页面视图 (聊天室、日记流、广场、报表、后台等)
│       ├── api/                 # Axios 请求拦截与封装
│       └── components/          # 高度可复用的业务组件库
├── docs/                        # 项目文档、产品设计稿与路线图
├── docker-compose.yml           # 容器化编排一键启动脚本
└── .env.example                 # 全局环境变量配置模板 (使用前必读)
```

## 🔒 隐私与安全承诺

我深知情绪与日记是极其私密的数据。
- 本项目完全开源，你可以 100% 掌控自己的数据，将应用部署在自己的私有服务器、甚至本地局域网内。
- 若你使用官方的线上服务，我的**数据伦理底线是**：作为独立开发者，我绝对不查看、不分析任何用户的私密日记与 AI 聊天记录。
- **数据持久化保证**：这已经是一个稳定运行的正式环境，我本人每天都在使用它。我在此承诺绝对不会删档，你的每一份情绪记录都会被永久、安全地保存。
- 架构层面配备了防刷与接口限流机制，从代码层面杜绝恶意的机器人爬取和爆破攻击。

## 🤝 加入社区 & 贡献代码

MoodCopilot 正在快速迭代中，目前只有我（网名：自由基）一个人在“用爱发电”，你的想法对我真的很重要！

- **期待种子用户**：快来体验吧！如果你在使用中觉得“这功能懂我！”，或者是遇到了反人类的 Bug，请毫不犹豫地提 Issue 告诉我！
- **寻找开源贡献者**：我非常渴望收到你的 Pull Request！你想加入语音日记功能？你想优化 RAG 的召回率？你想写个更炫酷的 UI？一起来折腾吧！
    1. Fork 本仓库并 `git clone` 到本地
    2. 创建你的特性分支 (`git checkout -b feat/amazing-feature`)
    3. 提交你的代码 (`git commit -m 'feat: 增加超级酷炫的功能'`)
    4. 推送到你的分支 (`git push origin feat/amazing-feature`)
    5. 发起 Pull Request，我会在看到的第一时间 Review！

## 📄 开源协议

本项目基于 [AGPL-3.0 License](LICENSE) 许可，任何基于此代码提供的网络服务都必须同样开源。

---
<p align="center">
  <sub>Made with ❤️ by 自由基 & MoodCopilot AI</sub>
</p>
