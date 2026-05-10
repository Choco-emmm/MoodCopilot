# MoodCopilot

MoodCopilot 是一个用 AI 帮你理解情绪，并连接同频陌生人陪伴的情绪日记社区。

它不是一个用来刷内容的广场，而是一个更克制的情绪副驾：我写下今天的情绪，AI 帮我理解自己；如果我愿意，陌生人可以温柔回应我；我也能看到和我相似处境的人。

## 产品定位

MoodCopilot 是一个 AI 情绪副驾，在你愿意的时候，把你连接给相似心情的人。

核心路径：

```text
写日记
-> AI 分析
-> 用户选择：仅自己看 / 分享到社区
-> 系统推荐同频日记
-> 用户可以留言或共鸣
-> 后续 AI 生成趋势和陪跑建议
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
- 情绪小卡片

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
- 报告页（周报/月报切换、情绪趋势、话题云、AI 总结、日记溯源）
- 自定义时期总结库
- Spring AI 框架（ChatClient + ChatMemory）
- Redis 缓存层
- 日记/评论删除
- 「纸墨之间」设计系统

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

#### ✅ 性能优化

- **N+1 批量查询** ✅ — 批量加载分析+评论，公开日记流从 41 次查询降至 3 次
- **连接池调优** ✅ — HikariCP max 30 + Redis Lettuce max 30
- **JVM 参数** ✅ — G1GC、Xms256m/Xmx512m、MaxRAMPercentage=75%
- **Docker 资源限制** ✅ — MySQL 512MB、Redis 128MB、Backend 768MB/1CPU
- **Redis 缓存优化** ✅ — 精确 key 删除代替 KEYS 扫描、coaching/月报缓存、evict 范围限定
- **前端优化** ✅ — Vite 代码分割（naive-ui/vue-vendor 独立 chunk）+ Nginx gzip + 静态资源缓存
- **k6 压测脚本** ✅ — 冒烟/负载/压力三套脚本，50 VU 压力测试

#### ⏳ 待做

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
| Agent 工具调用 | 用户场景不明确 | 暂无 |
| 运动手表数据导入 | 投入产出比极低 | 不做 |
| 向量检索 | 有场景但优先级低 | 等数据量大再做 |

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
