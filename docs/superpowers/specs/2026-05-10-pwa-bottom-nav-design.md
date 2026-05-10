# MoodCopilot PWA + 底部 Tab 栏 设计

> 目标：让 MoodCopilot 能安装到手机桌面，像原生 App 一样使用，方便发给朋友玩。

## 架构

```
frontend/
├── index.html              # 加 manifest 链接 + Apple meta 标签
├── public/
│   ├── manifest.json       # PWA 清单（名称、图标、全屏模式）
│   ├── sw.js               # Service Worker（静态资源缓存 + 离线壳）
│   ├── icon-192.png        # App 图标（192x192）
│   └── icon-512.png        # App 图标（512x512，用于 splash）
├── src/
│   ├── main.ts             # 注册 Service Worker
│   ├── components/
│   │   └── BottomNav.vue   # 新增：底部 Tab 栏组件
│   │   └── AppHeader.vue   # 修改：去掉导航链接，精简为品牌+通知+用户
│   └── pages/              # 不变
```

## 改动

### 1. PWA 安装能力

**manifest.json** — `name: "MoodCopilot"`, `display: "standalone"`（全屏无浏览器壳），`theme_color: "#f7f3eb"`（纸色背景）

**Service Worker** — 安装时预缓存 CSS/JS/HTML，后续请求走缓存优先（Cache First），确保离线不白屏。API 请求不缓存（始终走网络）。

**图标** — 用现有的印章红 + 纸色生成简单的 SVG → PNG 图标，192px 和 512px 两种尺寸。

### 2. 底部 Tab 栏（BottomNav.vue）

5 个 Tab：
- 🔍 广场 `/`
- ✏️ 写日记 `/write`
- 💬 AI `/chat`
- 👥 关注 `/following`
- 📊 报告 `/report`

用 `router-link` 实现，`active` 状态高亮（玉绿色）。固定在页面底部，iOS 安全区适配。

### 3. AppHeader 精简

保留：品牌名、通知铃铛、用户头像/退出
去掉：5 个导航链接、"写下今天，慢慢理解自己" 标语行

### 4. 不被改动的

所有页面内容、路由结构、API、后端 — 完全不变。

## 验证

- Chrome DevTools → Application → Manifest 检查可安装性
- 手机浏览器打开 → "添加到主屏幕" → 桌面出现图标 → 点击全屏打开
- 断网后刷新 → 不白屏（显示缓存的壳）
