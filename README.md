<p align="center">
  <img src="./docs/logo.svg" alt="MoodCopilot" width="120" />
</p>

<h1 align="center">MoodCopilot</h1>

<p align="center">
  <strong>🫂 温暖、共情、懂你的开源 AI 情绪伙伴</strong><br />
  <em>“先帮你看见情绪，再把你温和地连接给相似心情的人。”</em>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-AGPL--3.0-blue" alt="License" /></a>
  <a href="#"><img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21" /></a>
  <a href="#"><img src="https://img.shields.io/badge/Vue-3.x-4fc08d" alt="Vue 3" /></a>
  <a href="#"><img src="https://img.shields.io/badge/Tailwind_CSS-38B2AC?logo=tailwind-css&logoColor=white" alt="Tailwind CSS" /></a>
  <a href="#"><img src="https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen" alt="Spring Boot 3.5" /></a>
  <a href="#"><img src="https://img.shields.io/badge/AI-DeepSeek-blue" alt="DeepSeek" /></a>
</p>

<p align="center">
<img src="./docs/images/cover.png" alt="MoodCopilot 封面" width="100%" style="max-width: 800px; border-radius: 8px;" />
</p>

<h3 align="center">
  👉 <a href="https://moodcopilot.dpdns.org/">正式上线，点此开始记录 </a> 👈
</h3>

---

> **👋 寻找种子用户与贡献者！**
> MoodCopilot 已经正式上线并稳定运行中！**（我本人每天都在上面写日记，所以这是一个绝对不会删档的长期服务，请放心把你的情绪交给MoodCopilot妥善保管。）** 无论你是想寻找一个专属的赛博树洞，还是想探索前沿的 AI 应用开发（RAG、双模型路由、多模态），都非常欢迎你的加入！去创造专属于你的情绪自留地吧。

**MoodCopilot** 是一款基于大语言模型构建的情绪日记与社交应用。它不仅是一个可以记录纯文本、音乐、图片的树洞，更是一个拥有长期记忆的 AI 伴侣。它能敏锐地捕捉你的情绪波动，为你提供深度共情的回复，甚至通过向量检索，在茫茫人海中帮你找到此时此刻和你有着相似心情的“同频者”。

## ✨ 为什么选择 MoodCopilot？（核心体验）

### 📝 懂你的”多模态”日记本
*   **富文本与 Markdown 双引擎**：全面升级为 WangEditor 富文本编辑器，提供丝滑的沉浸式排版体验，同时向下兼容旧版 Markdown 数据的无缝渲染与编辑。
*   **突破束缚的动态情绪词典**：彻底解开传统情绪下拉框的标签束缚，AI 会结合日记的时间上下文，自由于广袤的心理学词汇中提取出最精准的细腻心境（如：怅然若失、久违的轻盈），并给予最温暖的共情回应。
*   **网易云音乐解析**：粘贴网易云歌曲链接，自动抓取封面与歌词，AI 对歌曲进行深层分析，提取情绪标签（如”病态、绝望、压抑”）与主题摘要，结合整首歌的氛围语境共同参与日记的情绪分析。
*   **图片深度参与分析**：支持一键上传图片（上限 20MB），内置 OCR + VLM 双路由视觉管线——qwen-vl-ocr 精确提取图中文字后注入日记正文参与分析，qwen3-vl-flash 独立描述画面氛围与情感色调。图片不再是附件，而是与文字、音乐同等权重的情感表达。

<p align="center">
  <img src="./docs/images/after_nav_write.png" alt="写日记界面" width="300" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="./docs/images/diary_screenshot.png" alt="AI分析完成" width="300" />
</p>

### 🎨 沉浸式情绪美学与隐私保护
*   **「纸张温度的克制版杂志风」设计语言**：全局采用暖黑色调 + 水墨呼吸线分隔 + 纸张纹理渐变，去卡片化排版。详情页图片全宽出血、列表页首图宽幅封面，打造沉静的杂志阅读体验。
*   **28 款疗愈系全屏主题**：内置 28 套经过精心调色的全屏无边框 UI 主题。通过全局 CSS 变量深度定制，让弹窗、按钮甚至底层网格都能完美融入主题氛围。
*   **自研动态情绪色彩引擎**：AI 提取效价 (Valence) 与唤醒度 (Arousal) 双维指标，通过双线性插值算法，在「暖黄、草木绿、焦土红褐、暮霭蓝灰」四大基底色间进行无缝动态调色。
*   **高级主题色彩融合**：计算出的情绪原色会按照 15% 的黄金比例，与你当前所选系统主题的强调色进行调和。你的每一抹情绪都会被铺在高级感滤镜之下，以微光的形式在纸张边缘如呼吸般温柔晕染。
*   **交互去繁就简**：摒弃传统社交软件臃肿的气泡对话框，采用无界排版与水墨晕染风的通知点缀，还原沉静的阅读与交流体验。
*   **绝对的隐私边界**：日记的公开私密状态一目了然，且情绪光晕与 AI 深度分析卡片仅对作者本人可见。即使日记公开到广场，其他用户也无法逆向推测你潜意识里的情绪侧写，为您守住最深处的情绪自留地。

### 📚 日记合集：为情绪建立专辑
*   **创建与管理合集**：将相关日记归入同一合集（如"考研日记"、"旅行回忆"），支持封面图片上传与裁剪、公开/私密切换。
*   **合集内日记排序**：拖拽调整日记在合集内的排列顺序，编排属于你的情绪叙事。
*   **从合集快捷写作**：在合集详情页一键跳转写日记，自动关联当前合集。
*   **添加已有日记**：将过往日记批量加入合集，支持分页加载与公开/私密过滤。

### 🧠 拥有长期记忆的 AI 伙伴
*   **RAG 向量记忆与透明回溯**：TA 不仅记住你的过去，还能在对话中随时引用你曾经写过的日记、分享的音乐和图片。**聊天界面会实时、透明地展示 AI 当下正在引用的日记记忆**，提供连续不断的关怀。
*   **记忆引用与跨时空对话 (Quote)**：在聊天中，你可以直接“引用 (Quote)”自己过往的某篇日记，或者引用 AI 曾经给出的回复卡片，发起连续、深度的心理探讨。
*   **知识图谱可视化 (Knowledge Graph)**：通过直观的网状拓扑图，展示你的情绪状态与记忆节点在时间维度上的关联脉络，支持移动端丝滑手势缩放探索。
*   **长期人格画像**：随着互动加深，AI 会在后台异步提炼你的人格特质，变得越来越“懂你”。
*   **双模型智能路由与深度思考**：底层全面接入 **deepseek-v4-flash** 与 **deepseek-v4-pro**。日常闲聊时使用 flash 模型极速响应，涉及复杂情绪分析和深度日记回顾时，自动无缝切换并开启 pro 模型的**深度思考模式（Reasoning）**。AI 在给出最终回复前会先进行一段心理学角度的沉思，为你提供更有深度、更共情的解答。

<p align="center">
  &nbsp;&nbsp;
  <img src="./docs/images/chat_snippet_screenshot.png" alt="记忆引用对话" width="260" />
  &nbsp;&nbsp;
  <img src="./docs/images/setting_memory_screenshot.png" alt="记忆管理设置" width="260" />
</p>

### 🌌 寻找同频共振的星空
*   **情绪广场**：如果你愿意将日记设为公开，它们将会展示在广场上，与大家分享此时此刻的心情。
*   **灵魂共鸣匹配（规划中）**：我计划在未来加入基于你数据的特殊向量搜索功能，帮你找到在这个世界上与你“灵魂共鸣”的人。不过这需要等社区的人多起来之后才有意义，所以快来成为第一批原住民吧！
*   **双通道实时温暖互动**：支持关注、点赞、评论，新消息通过 WebSocket 与 HTTP Fallback 的双通道架构实时推送到通知中心，保证跨页漫游不丢通知。
*   **守护社区氛围**：内置完整的举报机制与管理员后台审核，保障每一份真诚都不被辜负。
<p align="center">
  <img src="./docs/images/homepage_screenshot.png" alt="情绪广场" width="300" />
</p>

### 📈 陪伴式的成长轨迹
*   **记录即成长**：写日记、签到、获取共鸣皆可积累经验值 (EXP)。
*   **动态权益激励**：等级越高，解锁的 AI 深度对话调用额度越高，见证你的心理成长。
*   **数据可视化图表**：提供情绪四象限分布周报/月报，帮你以上帝视角俯瞰近期的内心轨迹。
<p align="center">
  <img src="./docs/images/report_screenshot.png" alt="月度情绪报告" width="300" />
</p>
---

## 🛠 技术亮点

本项目也是一个极其完善的“全栈 AI 落地应用”学习与二次开发模板：

| 模块 | 核心技术选型 |
|---|---|
| **后端架构** | Spring Boot 3.5 + Java 21 + Spring AI + MyBatis-Plus |
| **存储底座** | MySQL 8.0 + Redis Stack (利用 RediSearch 实现原生、高性能的 HNSW 向量检索) |
| **安全体系** | Spring Security + JWT 无状态认证 + TIANAI-CAPTCHA |
| **前端交互** | Vue 3 + TypeScript + Vite + Naive UI + Tailwind CSS |
| **实时通信引擎** | Server-Sent Events (SSE) 构建高可用流式打字机效果，跨页面路由漫游时自动保持连接不中断 |
| **AI 模型栈** | **deepseek-v4-flash / pro** (纯手写 WebClient 实现流式思考过程解析) + **DashScope VLM** (qwen-vl-ocr 文字提取 + qwen3-vl-flash 视觉描述双路由) |
| **RAG 引擎** | BAAI/bge-m3 (SiliconFlow API 1024 维密集向量嵌入), 支持多模态独立建库、HyDE 查询重写、长文本滑动窗口分块。 |
| **部署架构** | 阿里云 OSS 浏览器直传（极低服务器带宽压力）、Docker Compose 容器化编排部署、Nginx 反代。 |

---

## 🚀 极速部署指引

只需简单的几步，即可在本地跑起你的专属MoodCopilot。

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
│       ├── ai/                  # 🤖 AI 编排：双模型路由、VLM 视觉、知识图谱、RAG 记忆重组
│       ├── diary/               # 📝 日记 CRUD、合集管理、公开流、同频推荐
│       ├── music/               # 🎵 网易云音乐解析、情绪标签、主题摘要
│       ├── oss/                 # ☁️ 阿里云 OSS 浏览器直传鉴权
│       ├── graph/               # 🕸️ 知识图谱：事件因果提取与情感脉络可视化
│       ├── admin/               # 🛡️ 社区治理：举报审核
│       ├── growth/              # 📈 用户成长：等级、签到、AI 额度
│       └── notification/        # 🔔 WebSocket 实时通知
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

MoodCopilot 正在快速迭代中，目前只有我一个人在用爱发电，你的想法对我真的很重要！

- **加入 QQ 交流群**：欢迎加入QQ 群探讨需求和技术，群号：**1094722984**。
  <br><img src="./docs/qq_group.jpg" alt="QQ群二维码" width="200" />
- **期待种子用户**：快来体验吧！如果你在使用中产生了新奇的想法，或者是遇到了反人类的 Bug，请毫不犹豫地提 Issue 告诉我！
- **寻找开源贡献者**：我非常渴望收到你的 PR！你想加入语音日记功能？你想优化 RAG 的召回率？你想写个更炫酷的 UI？一起来折腾吧！
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
