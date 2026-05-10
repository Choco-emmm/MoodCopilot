# MoodCopilot

MoodCopilot 是一个用 AI 帮你理解情绪，并连接同频陌生人陪伴的情绪日记社区。

它不是一个用来刷内容的广场，而是一个更克制的情绪副驾：我写下今天的情绪，AI 帮我理解自己；如果我愿意，陌生人可以温柔回应我；我也能看到和我相似处境的人。

## 产品定位

MoodCopilot 是一个 AI 情绪副驾，在你愿意的时候，把你连接给相似心情的人。

核心路径：

```text
写日记
→ AI 分析
→ 用户选择：仅自己看 / 分享到社区
→ 系统推荐同频日记
→ 用户可以留言或共鸣
→ 后续 AI 生成趋势和陪跑建议
```

## 核心价值

- 对自己：AI 帮我看见情绪、触发因素和长期变化。
- 对他人：我可以选择把某些日记分享出去，获得陌生人的回应。
- 对社区：所有日记平等对待——不显示情绪/主题标签，让每个人被看见的是内容而非分类。

## 核心功能

### 一级核心功能

1. AI 情绪日记
   - 写日记 → AI 自动分析（情绪、主题、强度、摘要、反馈）
   - 支持公开/私密两种可见性
   - 日记和评论可删除（仅本人）

2. 陌生人陪伴
   - 公开日记可被留言、共鸣、匿名鼓励
   - 互动围绕具体情绪和处境展开
   - 不暴露情绪标签给其他用户

3. 同频推荐
   - 发完日记后，系统推荐情绪相似的公开日记
   - 推荐展示作者头像 + 用户名 + 内容摘要

4. AI 陪跑 + 报告
   - 每日 6:00 系统通知推送 AI 陪跑建议（可开关）
   - 周报/月报：情绪趋势、AI 总结、日记溯源
   - AI 对话（多会话 SSE 流式、Markdown 渲染、引用栏）
   - AI 调用每日限流（聊天 30 次、分析 10 次等）

### 二级功能

- 广场（公开日记瀑布流 + 今日状态）
- 关注 / 取消关注 + 关注动态流
- 个人中心（头像上传、用户名修改、通知开关）
- 通知系统（评论/回复/共鸣/关注/鼓励/系统）
- 自定义时期总结库

### 玩法功能

- 匿名鼓励（AI 生成 3 句候选 + 匿名发送）
- 今日同频（推荐情绪相似日记）
- 漂流瓶（暂不做）
- 情绪小卡片（暂不做）

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
- 报告页（周报/月报切换、情绪趋势、话题云、AI 总结、日记溯源）
- 自定义时期总结库
- Spring AI 框架（ChatClient + ChatMemory）
- Redis 缓存层
- 日记/评论删除
- 匿名鼓励

**用户留存**
- 匿名鼓励 — AI 生成 3 句候选 + 匿名发送
- 每日跟进 — 每日 6:00 系统通知 AI 陪跑建议（可开关）+ 广场状态条

**AI 差异化**
- 情绪陪跑计划 — AI 个性化陪跑建议，15min Redis 缓存

**社区体验**
- 今日同频卡片 — 广场推荐情绪相似日记（显示作者头像+用户名+摘要）
- 去标签化 — 公开日记流和日记详情不显示情绪/主题标签

**工程化**
- 内容审核 — 敏感词过滤
- Docker 部署 — `docker compose up` 全栈启动

**性能优化**
- N+1 批量查询 — 批量加载分析+评论，公开日记流从 41 次查询降至 3 次
- 连接池调优 — HikariCP max 30 + Redis Lettuce max 30
- JVM 参数 — G1GC、Xms256m/Xmx512m、MaxRAMPercentage=75%
- Docker 资源限制 — MySQL 512MB、Redis 128MB、Backend 768MB/1CPU
- Redis 缓存优化 — 精确 key 删除代替 KEYS 扫描
- 前端优化 — Vite 代码分割（naive-ui/vue-vendor 独立 chunk）+ Nginx gzip + 静态资源缓存
- k6 压测 — 冒烟/负载/压力三套脚本，20 VU 负载测试 p95 54ms

**PWA + 响应式**
- PWA 可安装 — manifest.json + Service Worker + 全屏模式
- 响应式双 UI — 桌面端顶栏导航 / 移动端底部 6 Tab 栏（广场/写日记/AI/关注/报告/我的）
- 对外分享 — Cloudflare Tunnel 免费 HTTPS 域名（moodcopilot.dpdns.org），/api 直连后端避免 SSE 缓冲

**用户系统 + AI 治理**
- 个人中心 — 头像上传（512KB）、用户名修改、每日通知开关
- AI 调用限流 — Redis 按用户+日期+类型计数，超限 429
- 每日跟进定时任务 — 6:00 为活跃用户推送 AI 陪跑系统通知
- AI 回复简短化 — system prompt 限制 2-3 句

**设计刷新 — Warm Precision**
- 去掉纸质纹理、印章标识、楷体标题
- 暖石色背景 + 纯白卡片 + 鼠尾草绿强调
- 几何 M SVG logo（替换旧「印」字图标和 favicon）
- 登录/注册页简化（无圆圈装饰）

### ⏳ 待做

- 报告定时预生成（当前为请求时实时计算 + 缓存）
- 向量检索实现同频推荐聚类
- 限时匿名贴（匿名鼓励已覆盖此场景）

---

### ❌ 评估后暂不做的功能

| 功能 | 原因 | 替代方案 |
|------|------|---------|
| 私信 | 陌生人私信风险大（骚扰），与情绪社区定位不符 | 留言板更轻量安全 |
| 附近 | 位置功能与情绪日记关联弱，隐私风险高 | 匿名城市共鸣 |
| 漂流瓶 | 容易变成垃圾信息容器 | 限时匿名贴 |
| 睡眠状态识别 | 需要手环/手表硬件数据 | 日记中加「精力状态」标签 |
| 发现模式 | 独立页面非刚需 | 广场加情绪/话题筛选 |
| Agent 工具调用 | 用户场景不明确 | 暂无 |
| 运动手表数据导入 | 投入产出比极低 | 不做 |
| 向量检索 | 有场景但优先级低 | 等数据量大再做 |

## 技术栈

- 后端：Spring Boot 3.5.14、Java 21、MySQL 8、Redis (Lettuce)、MyBatis-Plus 3.5.10.1、Flyway
- 前端：Vue 3、Vite 5、TypeScript、Naive UI 2.41、Pinia 2.2、Vue Router 4.4
- AI：Spring AI 1.0.0-M6 + DeepSeek API（OpenAI 兼容），失败回退关键词分析
- 设计：Warm Precision（暖调精炼）— `#F8F6F2` / `#5B7C6B` / 系统无衬线字体 / M 几何 logo
- 测试：Playwright E2E + k6 压测

## 项目结构

```text
backend/moodcopilot/  Spring Boot API 服务
frontend/             Vue 移动端 Web 应用
docs/                 产品和工程文档
```

## 本地开发

后端配置文件位于 `backend/moodcopilot/src/main/resources/application.yaml`。

环境变量从仓库根目录 `.env` 加载：

```bash
eval $(cat /d/Code/MoodCopilot/.env | tr -d '\r' | grep -v '^#' | grep -v '^$' | sed 's/^/export /')
```

后端启动：

```bash
cd backend/moodcopilot
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

前端启动：

```bash
cd frontend
npm install
npm run dev          # dev server → localhost:5173（/api 代理到 :18080）
npm run build        # 生产构建 → dist/
npx vite preview --host --port 4173  # 生产预览
```

### E2E 测试

```bash
cd frontend
npm install --save-dev playwright
node e2e/smoke-test.mjs
```

### 对外分享（给朋友用）

```bash
# 1. 构建
cd frontend && npx vite build

# 2. 启动预览（4173 端口）
npx vite preview --host --port 4173

# 3. 启动后端（18080 端口）
cd backend/moodcopilot && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 4. 启动隧道
cloudflared tunnel --config ~/.cloudflared/moodcopilot-config.yaml run moodcopilot

# 朋友访问：https://moodcopilot.dpdns.org
```

隧道配置将 `/api` 请求直连后端 `:18080`（避免 SSE 缓冲），其余请求走预览服务器 `:4173`。
