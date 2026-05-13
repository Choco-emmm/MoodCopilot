# MoodCopilot

MoodCopilot 是一个用 AI 帮你理解情绪，并连接同频陌生人陪伴的情绪日记社区。

## 最近变更（2026-05）

- 登录态处理修复：前端全局拦截器改为仅 `401` 才跳转登录，`403` 不再误踢用户出页面（报告页场景已覆盖）。
- 新增额度接口：`GET /api/user/quota`，用于统一展示剩余额度。
- 额度策略调整：保留 `CHAT / ANALYSIS / REPORT` 三类额度；陪跑并入分析，关怀额度下线。
- 额度计数修复：Redis 计数序列化与解析兼容修正，避免“页面始终显示满额”。
- 头像链路修复：上传头像后可稳定显示在导航栏，错误提示更可见。
- 报告页体验优化：移除“继续聊”入口；相关日记片段支持回填；月度趋势图增加情绪颜色和强度标注，阅读成本更低。
- 聊天引用能力恢复：聊天可携带引用内容（受数量和长度限制），用于围绕具体日记继续讨论。
- 聊天上下文机制升级：
   - 不再每次把最近多篇日记直接塞给 AI。
   - 在日记分析完成后，增量合成“用户专属背景上下文”并缓存。
   - 聊天时自动携带该背景上下文；引用内容继续作为当前会话重点素材。- 长期用户画像系统上线（2026-05-13）：
   - 每次写日记、AI 分析完成后，自动抽取用户长期属性（性格、长期目标、关键人物等）并落库（`user_profile_memory` 表）。
   - 聊天时自动将该画像注入 system prompt，使 AI 能结合长期背景回应。
   - 支持按关键词 / 日期检索历史日记摘要的 Function Calling（`diarySearchFunction`），帮助模型在需要更早历史时主动查询。
   - 提供一次性数据迁移接口 `POST /api/chat/admin/init-memory`，可为所有已有日记用户批量生成初始画像。
- 删除日记后的画像动态重建（2026-05-14）：
   - 当用户删除日记时，后端异步触发该用户的画像重建任务，基于"剩余日记"重新推理用户特征。
   - 采用分层证据策略确保不丢失长期历史：先用最近 15 篇原文（细粒度），再用最多 120 篇日记的分析摘要（覆盖中期），然后复用周期摘要（成本低），最后做聚合统计（覆盖更老历史）。
   - 被删日记不会通过旧周期摘要"混回来"，因为周期摘要已过滤失效内容（检测到包含被删日记的摘要自动排除）。
   - 如用户删掉了最后一篇日记，画像自动清空，保持数据一致性。
它不是一个用来刷内容的广场，而是一个更克制的 MoodCopilot：我写下今天的情绪，AI 帮我理解自己；如果我愿意，陌生人可以温柔回应我；我也能看到和我相似处境的人。

## 产品定位

MoodCopilot 是一个 AI 情绪日记和同频陪伴社区，在你愿意的时候，把你连接给相似心情的人。

核心路径：

```text
写日记
-> AI 分析
-> 用户选择：仅自己看 / 分享到社区
-> 系统推荐同频日记
-> 用户可以留言或共鸣
-> 后续 AI 生成趋势、洞察和调整建议
```

## 核心价值

- 对自己：AI 帮我看见情绪、触发因素和长期变化。
- 对他人：我可以选择把某些日记分享出去，获得陌生人的回应。
- 对社区：系统把相似情绪的人温和连接起来，而不是刷流量。

## 核心功能

### 一级核心功能

1. AI 情绪日记
   - 写日记
   - AI 自动分析
   - 心情标签
   - 主题标签
   - 摘要
   - 情绪反馈
   - 趋势报告

2. 陌生人陪伴
   - 用户可以公开部分日记
   - 其他有权限的人可以留言、共鸣、鼓励
   - 互动围绕具体情绪和处境展开

3. 同频推荐
   - 发完日记后，系统推荐和你此刻情绪相似的日记或用户
   - 推荐目标是陪伴和理解，不是停留时长最大化

### 二级功能

- 广场
- 关注
- 私信
- 周报 / 月报
- AI 聊天
- 发现模式
- 附近
- 睡眠关联

### 玩法功能

- 漂流瓶
- 匿名鼓励
- 今日同频

## 同频陪伴体验

发完一篇日记后，AI 和系统可以这样回应：

```text
AI：今天你的主要情绪是“疲惫 + 委屈”，主题集中在“人际关系”。
系统：有 5 篇相似心情的公开日记，你可以看看别人是怎么走过这一天的。
```

用户点进去看到的不是泛泛的信息流，而是更接近这些处境的人：

- 同样感到委屈的人
- 同样因为人际关系内耗的人
- 同样今天很累但撑过去的人

## 阶段规划

### ✅ 已完成

**AI 日记 + 社区 MVP**
- 注册登录、发布日记、日记权限（私密/公开）
- AI 自动分析：心情标签、主题标签、摘要、反馈
- 社区公开日记流（瀑布流分页 + Redis 缓存）
- 发完日记推荐 3 篇同频日记
- 留言 / 回复（两级平铺）
- 共鸣按钮、通知系统
- 关注 / 取消关注 + 关注动态流

**AI 陪跑 + 报告**
- AI 对话（多会话管理、SSE 流式、Markdown 渲染、日记上下文）
- 报告页（周报/月报切换、情绪趋势、话题云、AI 总结、洞察与建议、日记溯源）
- 自定义时期总结库
- Spring AI 框架（ChatClient + ChatMemory）
- Redis 缓存层
- 日记/评论删除
- 「纸墨之间」设计系统

**朋友内测基础打磨**
- 广场公开日记流支持滚动分页，并保留加载更多兜底按钮
- 写日记支持本地草稿、字数提示、公开范围说明和 AI 分析状态反馈
- 报告页新增“MoodCopilot 看见了”和“可以试试”，可带着洞察继续聊天
- 轻量社区安全：隐藏公开日记、举报日记/评论，后续再补审核后台
- 单机稳定性：分页、批量查询、缓存精确清理和关键路径冒烟验证

---

### 下一步（按优先级）

#### ✅ P0 — 用户留存核心

- **匿名鼓励** ✅ — AI 生成 3 句候选 + 匿名发送 + 通知 `/api/diaries/encourage/{id}`
- **每日跟进** ✅ — 广场顶部状态条 + 连续天数 `/api/diaries/today-status`

#### ✅ P1 — AI 差异化价值

- **情绪陪跑计划** ✅ — AI 个性化陪跑建议 `/api/diaries/coaching`

#### ✅ P2 — 社区体验增强

- **今日同频卡片** ✅ — 广场推荐情绪相似日记 `/api/diaries/today-match`
- **匿名社区共鸣** ✅ — 今日社区情绪分布 `/api/diaries/community-mood`
- **限时匿名贴** ⏭️ — 匿名鼓励已覆盖此场景，不做独立功能

#### ✅ P3 — 工程化

- **内容审核** ✅ — 敏感词过滤 `ContentFilter`
- **Docker 部署** ✅ — `docker compose up` 全栈启动

#### ⏳ 待做

- **视觉与核心体验打磨** — Warm Precision 视觉刷新、广场轻量正文流、写日记页和手机聊天页体验优化、视觉冒烟脚本。
- **App 化基础准备** — 保持 Web/API 路径稳定，补齐关键 E2E 冒烟测试
- **审核后台** — 在举报数据积累后补处理状态、隐藏范围和管理视图
- **推荐质量优化** — 曝光去重、同一作者去重；不做推荐理由展示
- **限时匿名贴** — 匿名鼓励已满足匿名互动需求，暂不做

---

### ❌ 评估后暂不做的功能

| 功能 | 原因 | 替代方案 |
|------|------|---------|
| 私信 | 陌生人私信风险大（骚扰），与情绪社区定位不符 | 留言板更轻量安全 |
| 附近 | 位置功能与情绪日记关联弱，隐私风险高 | 匿名城市共鸣 |
| 漂流瓶 | 容易变成垃圾信息容器 | 限时匿名贴 |
| 睡眠状态识别 | 需要手环/手表硬件数据 | 日记中加「精力状态」标签 |
| 发现模式 | 独立页面非刚需 | 广场加情绪/话题筛选 |
| 自主 Agent 工具调用 | 内测阶段安全边界和用户场景还不清楚 | 先做报告洞察与调整建议 |
| 运动手表数据导入 | 投入产出比极低 | 不做 |
| 向量检索 | 有场景但优先级低 | 等数据量大再做 |
| 大并发压测 | 当前只有一台主机，主要发给朋友试玩 | 先做单机稳定性和冒烟验证 |

## 技术栈

- 后端：Spring Boot 3.5、Java 21、MySQL 8、Redis (Lettuce)、MyBatis-Plus 3.5.10.1、Flyway
- 前端：Vue 3、Vite 5、TypeScript、Naive UI 2.41、Pinia 2.2、Vue Router 4.4
- AI：Spring AI 1.0.0-M6 + DeepSeek API（OpenAI 兼容），失败回退关键词分析

## 项目结构

```text
backend/moodcopilot/  Spring Boot API 服务
frontend/             Vue 移动端 Web 应用
docs/                 产品和工程文档
```

## 本地开发

后端配置文件位于：

```text
backend/moodcopilot/src/main/resources/application.yaml
```

数据库初始化脚本：

```text
backend/moodcopilot/db/mysql/V1__init_schema.sql
```

可使用 MySQL 客户端执行：

```bash
mysql -uroot -p < backend/moodcopilot/db/mysql/V1__init_schema.sql
```

本地开发时请使用自己的 MySQL、Redis 和 AI 服务配置，不要提交真实密钥。

后端启动：

```bash
cd backend/moodcopilot
./mvnw spring-boot:run
```

推荐本地开发使用 dev profile（默认端口 18080）：

```bash
cd backend/moodcopilot
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

前端启动：

```bash
cd frontend
npm install
npm run dev
```

前端构建：

```bash
cd frontend
npm run build
```

## App 化前冒烟验证

前置条件：后端运行在 `18080`，前端生产预览运行在 `4173`。公网验证还需要 `cloudflared` 隧道在线。

```powershell
cd D:\Code\MoodCopilot\frontend
npm.cmd run build
npx.cmd vite preview --host 127.0.0.1 --port 4173

cd D:\Code\MoodCopilot
npm.cmd run e2e:smoke
```

## 登录与公网排障

登录失败时先判断是账号问题还是链路问题。`test@test.com / 123456` 在当前本地开发库中可用；如果后端接口直连可登录，但公网登录失败，优先检查本机服务和 Cloudflare Tunnel。

推荐先跑统一诊断（固定输出本地端口、本地健康、公网健康、隧道状态）：

```powershell
cd D:\Code\MoodCopilot

# 本地链路诊断（不检查隧道/公网）
npm.cmd run app:doctor

# 公网链路诊断（含 cloudflared + 公网 health）
npm.cmd run public:doctor
```

直接验证登录接口：

```powershell
$body = @{ email='test@test.com'; password='123456' } | ConvertTo-Json
Invoke-RestMethod -Uri http://127.0.0.1:18080/api/auth/login -Method Post -ContentType 'application/json' -Body $body
```

公网依赖三段链路同时可用：

1. 后端监听 `18080`。
2. 前端生产预览监听 `4173`。
3. `cloudflared` 使用 `C:\Users\renpe\.cloudflared\moodcopilot-config.yaml` 运行。

推荐直接一键启动：

```powershell
cd D:\Code\MoodCopilot
npm.cmd run app:start

# 强制重启本地后端+前端预览（不启动隧道）：
npm.cmd run app:restart

# 本地链路诊断：
npm.cmd run app:doctor

# 需要公网访问时再启动隧道链路：
npm.cmd run public:start

# 强制重启后端、前端预览和隧道：
npm.cmd run public:restart

# 公网链路诊断：
npm.cmd run public:doctor
```

说明：不要长期使用前台 `cmd /c mvn.cmd spring-boot:run -Dspring-boot.run.profiles=dev` 作为常驻启动方式；该方式在终端关闭或被中断时容易出现 `exit code -1`，造成“启动失败”的误判。`app:start/app:restart` 与 `public:start/public:restart` 都使用后台常驻进程启动。

启动前端预览：

```powershell
cd D:\Code\MoodCopilot\frontend
npm.cmd run build
npx.cmd vite preview --host 127.0.0.1 --port 4173
```

启动隧道：

```powershell
cloudflared tunnel --config C:\Users\renpe\.cloudflared\moodcopilot-config.yaml run moodcopilot
```

如果公网 `https://moodcopilot.dpdns.org/api/health` 返回 `530`，通常是 `cloudflared` 未运行或 4173/18080 没有监听，不要先改登录代码。2026-05-11 已验证：本地 `/api/auth/login`、公网 `/api/auth/login` 均返回 200，并能跳转到广场。
