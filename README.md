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

### ✅ 第一阶段：AI 日记 + 同频陪伴 MVP（已完成）

- 注册登录
- 发布日记
- 日记权限：私密 / 公开
- AI 自动分析：心情标签、主题标签、摘要、一句反馈
- 日记详情
- 我的日记列表（分页）
- 社区公开日记流（分页 + Redis 缓存）
- 发完日记推荐 3 篇同频日记
- 留言 / 回复（两级平铺，回复显示时间和对象）
- 共鸣按钮
- 基础通知（评论/回复/共鸣/关注）

### ✅ 第二阶段：社区互动和推荐增强（已完成）

- 关注 / 取消关注 + 关注动态流
- 回复留言（两级平铺 root_comment_id）
- 报告页（周报 + 自定义时期 AI 总结 + 情绪趋势 + 话题分布）
- 总结库（保存/查看/删除，含情绪趋势和话题数据）
- ~~屏蔽 / 拉黑~~（暂不做）
- ~~举报~~（暂不做）
- ~~情绪筛选 / 主题筛选~~（用户确认不需要）

### ✅ 第三阶段：AI 陪跑（部分完成）

- AI 对话窗口（/chat，SSE 流式逐字输出，按用户隔离记忆）
- AI 引用日记作为上下文（最多 10 篇，不读总结防止幻觉）
- 长期报告页（/report，合并周报+自定义总结，VIP 预留）
- Redis 缓存（周报 30min/流 5min，不等 AI 响应）
- Spring AI 框架重构（ChatClient + ChatMemory + aiExecutor）
- 待做：情绪陪跑计划
- 待做：每日跟进
- 待做：Agent 工具调用
- 待做：睡眠状态识别
- 待做：月报

### 第四阶段：探索和匿名陪伴

- 发现模式
- 漂流瓶
- 匿名陪伴
- 附近
- 位置隐私设置
- 私信

### 第五阶段：工程化和数据能力

- 向量检索
- 运动手表数据导入
- 后台管理
- 内容审核
- 推荐效果统计
- Docker 部署
- 压测和调优

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
