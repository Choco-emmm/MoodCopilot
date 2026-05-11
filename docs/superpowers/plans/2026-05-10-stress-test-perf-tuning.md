# 压测与性能调优 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对 MoodCopilot 进行全链路压测，修复发现的性能瓶颈，确保系统在合理并发下稳定运行。

**Architecture:** 分三步走 — 先修复已知的 N+1 查询和无分页问题（不改这些压测没意义），再用 k6 做压力测试找到真正的瓶颈，最后根据压测结果做资源调优（连接池、JVM、Docker 资源限制）。

**Tech Stack:** k6 (压测工具), Spring Boot 3.5, MySQL 8, Redis 7, MyBatis-Plus 3.5.10.1, Docker Compose

---

## 问题总览

代码审计发现 23 个性能/安全问题。本计划按优先级分组处理：

**阻断级（压测前必做）：** N+1 查询、`/api/diaries/mine` 无分页、`similar()` 无 LIMIT
**高危（压测后优先修）：** 缺限流、AI 同步阻塞、Redis KEYS 扫描
**中危（资源调优）：** 连接池、JVM 参数、Docker 资源限制、前端代码分割

---

### Task 1: 修复 N+1 查询 —— DiaryService 批量分析加载

**Files:**
- Modify: `backend/moodcopilot/src/main/java/com/moodcopilot/diary/DiaryService.java`

**问题:** `buildDiaryView()` 对每篇日记单独查 `diaryAnalysisMapper.selectById()` 和 `diaryCommentMapper.selectList()`。20 篇公开日记 = 40 次额外查询。

**方案:** 在 `publicDiaries()`、`followingDiaries()`、`myDiaries()` 中，先用 `selectBatchIds()` 批量加载所有日记的分析和评论，再传入 `buildDiaryView()`。

- [ ] **Step 1: 批量查询分析 + 评论的辅助方法**

在 `DiaryService.java` 中新增两个私有方法：

```java
private Map<Long, DiaryAnalysisEntity> batchLoadAnalyses(List<Long> diaryIds) {
    if (diaryIds.isEmpty()) return Map.of();
    return diaryAnalysisMapper.selectBatchIds(diaryIds).stream()
            .collect(Collectors.toMap(DiaryAnalysisEntity::getDiaryId, a -> a));
}

private Map<Long, List<DiaryCommentEntity>> batchLoadComments(List<Long> diaryIds) {
    if (diaryIds.isEmpty()) return Map.of();
    List<DiaryCommentEntity> all = diaryCommentMapper.selectList(
            new LambdaQueryWrapper<DiaryCommentEntity>()
                    .in(DiaryCommentEntity::getDiaryId, diaryIds)
                    .orderByAsc(DiaryCommentEntity::getCreatedAt));
    return all.stream().collect(Collectors.groupingBy(DiaryCommentEntity::getDiaryId));
}
```

- [ ] **Step 2: 重构 buildDiaryView 接受预加载的 Map**

```java
private DiaryView buildDiaryView(DiaryEntity diary, boolean isPublic,
        Map<Long, DiaryAnalysisEntity> analysisMap,
        Map<Long, List<DiaryCommentEntity>> commentMap) {
    DiaryAnalysisEntity analysis = analysisMap.get(diary.getId());
    List<DiaryCommentEntity> comments = commentMap.getOrDefault(diary.getId(), List.of());
    return isPublic ? DiaryView.fromPublic(diary, analysis, comments)
                    : DiaryView.from(diary, analysis, comments);
}

// 保留旧签名兼容单条查询场景（todayStatus、get 等）
private DiaryView buildDiaryView(DiaryEntity diary, boolean isPublic) {
    DiaryAnalysisEntity analysis = diaryAnalysisMapper.selectById(diary.getId());
    List<DiaryCommentEntity> comments = diaryCommentMapper.selectList(
            new LambdaQueryWrapper<DiaryCommentEntity>()
                    .eq(DiaryCommentEntity::getDiaryId, diary.getId())
                    .orderByAsc(DiaryCommentEntity::getCreatedAt));
    return buildDiaryView(diary, isPublic, 
            analysis != null ? Map.of(analysis.getDiaryId(), analysis) : Map.of(),
            Map.of(diary.getId(), comments));
}
```

- [ ] **Step 3: 修改 publicDiaries 使用批量加载**

```java
public List<DiaryView> publicDiaries(int page, int size) {
    size = Math.min(size, 50);
    String cacheKey = "public:diaries:" + page + ":" + size;
    String cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        try { return objectMapper.readValue(cached, listDiaryViewType); } catch (Exception e) { /* fall through */ }
    }
    Page<DiaryEntity> result = diaryMapper.selectPage(
            Page.of(page, size),
            new LambdaQueryWrapper<DiaryEntity>()
                    .eq(DiaryEntity::getVisibility, "PUBLIC")
                    .orderByDesc(DiaryEntity::getCreatedAt));
    List<DiaryEntity> diaries = result.getRecords();
    // 批量加载
    List<Long> ids = diaries.stream().map(DiaryEntity::getId).toList();
    Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(ids);
    Map<Long, List<DiaryCommentEntity>> commentMap = batchLoadComments(ids);
    List<DiaryView> views = diaries.stream()
            .map(d -> buildDiaryView(d, true, analysisMap, commentMap))
            .toList();
    redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(views), Duration.ofMinutes(5));
    return views;
}
```

- [ ] **Step 4: 同样修改 followingDiaries、myDiaries**

`followingDiaries()` 和 `myDiaries()` 同样改为先收集 ID → 批量加载分析+评论 → 构建视图。

- [ ] **Step 5: 编译验证**

```bash
cd backend/moodcopilot && ./mvnw compile -q
```

---

### Task 2: /api/diaries/mine 加分页

**Files:**
- Modify: `backend/moodcopilot/src/main/java/com/moodcopilot/diary/DiaryController.java`
- Modify: `backend/moodcopilot/src/main/java/com/moodcopilot/diary/DiaryService.java`

**问题:** `myDiaries()` 返回用户全部日记，无分页。

- [ ] **Step 1: DiaryService.myDiaries 加分页参数**

```java
public List<DiaryView> myDiaries(int page, int size) {
    size = Math.min(size, 50);
    Page<DiaryEntity> result = diaryMapper.selectPage(
            Page.of(page, size),
            new LambdaQueryWrapper<DiaryEntity>()
                    .eq(DiaryEntity::getAuthorUserId, currentUserId())
                    .orderByDesc(DiaryEntity::getCreatedAt));
    List<DiaryEntity> diaries = result.getRecords();
    List<Long> ids = diaries.stream().map(DiaryEntity::getId).toList();
    Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(ids);
    Map<Long, List<DiaryCommentEntity>> commentMap = batchLoadComments(ids);
    return diaries.stream()
            .map(d -> buildDiaryView(d, d.getAuthorUserId().equals(currentUserId()), analysisMap, commentMap))
            .toList();
}
```

- [ ] **Step 2: DiaryController 接收分页参数**

```java
@GetMapping("/mine")
public ApiResponse<List<DiaryView>> myDiaries(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(diaryService.myDiaries(page, size));
}
```

- [ ] **Step 3: 前端 api/index.ts 更新调用**

在 `diaryApi.mine()` 方法中传分页参数：

```typescript
mine: (page = 1, size = 20) => api.get('/diaries/mine', { params: { page, size } }),
```

- [ ] **Step 4: 编译验证**

```bash
cd backend/moodcopilot && ./mvnw compile -q
```

---

### Task 3: similar() 加 LIMIT 并修复 N+1

**Files:**
- Modify: `backend/moodcopilot/src/main/java/com/moodcopilot/diary/DiaryService.java`

**问题:** `similar()` 加载全部公开日记到内存，且对每篇调 `findAnalysis()`。

- [ ] **Step 1: 添加 LIMIT 并批量加载分析**

```java
public List<DiaryView> similar(long diaryId, int limit) {
    DiaryEntity target = diaryMapper.selectById(diaryId);
    if (target == null) return List.of();
    DiaryAnalysisEntity targetAnalysis = diaryAnalysisMapper.selectById(diaryId);

    // 限定候选池大小：取最近 200 篇公开日记
    Page<DiaryEntity> candidates = diaryMapper.selectPage(
            Page.of(1, 200),
            new LambdaQueryWrapper<DiaryEntity>()
                    .eq(DiaryEntity::getVisibility, "PUBLIC")
                    .ne(DiaryEntity::getId, diaryId)
                    .orderByDesc(DiaryEntity::getCreatedAt));

    List<DiaryEntity> diaries = candidates.getRecords();
    // 批量加载分析
    List<Long> ids = diaries.stream().map(DiaryEntity::getId).toList();
    Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(ids);

    // 相似度排序
    List<DiaryEntity> sorted = diaries.stream()
            .sorted(Comparator.comparingDouble(d ->
                    similarityScore(targetAnalysis, analysisMap.get(d.getId()))))
            .toList();

    // 去重：每人最多 1 篇
    Set<Long> seenUsers = new HashSet<>();
    List<DiaryView> result = new ArrayList<>();
    for (DiaryEntity d : sorted) {
        if (seenUsers.add(d.getAuthorUserId())) {
            result.add(buildDiaryView(d, true, analysisMap, Map.of()));
            if (result.size() >= limit) break;
        }
    }
    return result;
}
```

- [ ] **Step 2: 编译验证**

```bash
cd backend/moodcopilot && ./mvnw compile -q
```

---

### Task 4: 修复 todayMatch N+1

**Files:**
- Modify: `backend/moodcopilot/src/main/java/com/moodcopilot/diary/DiaryService.java`

**问题:** `todayMatch()` 加载最多 53 篇日记的分析，逐条查询。

- [ ] **Step 1: 批量加载分析**

在 `todayMatch()` 方法中，将候选池查询后 bulk load 分析：

```java
// 批量加载候选分析
List<Long> candidateIds = candidates.stream().map(DiaryEntity::getId).toList();
Map<Long, DiaryAnalysisEntity> candidateAnalysisMap = batchLoadAnalyses(candidateIds);

// 相似度排序
DiaryEntity best = candidates.stream()
        .filter(d -> !d.getAuthorUserId().equals(currentUserId()))
        .max(Comparator.comparingDouble(d ->
                similarityScore(targetAnalysis, candidateAnalysisMap.get(d.getId()))))
        .orElse(null);
```

- [ ] **Step 2: 编译验证**

```bash
cd backend/moodcopilot && ./mvnw compile -q
```

---

### Task 5: 修复周报/月报 N+1

**Files:**
- Modify: `backend/moodcopilot/src/main/java/com/moodcopilot/diary/DiaryService.java`

**问题:** `computeWeeklyReport()` 和 `computeMonthlyReport()` 对每篇日记逐条查分析。

- [ ] **Step 1: 批量加载**

```java
// computeWeeklyReport() 和 computeMonthlyReport() 中，获取 diaries 后：
List<Long> ids = diaries.stream().map(DiaryEntity::getId).toList();
Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(ids);

// 后续用 analysisMap.get(d.getId()) 代替 findAnalysis(d.getId())
```

- [ ] **Step 2: 编译验证**

```bash
cd backend/moodcopilot && ./mvnw compile -q
```

---

### Task 6: 安装 k6 并编写压测脚本

**Files:**
- Create: `backend/moodcopilot/stress-test/k6-smoke.js`
- Create: `backend/moodcopilot/stress-test/k6-load.js`
- Create: `backend/moodcopilot/stress-test/k6-stress.js`

- [ ] **Step 1: 安装 k6**

```bash
# macOS
brew install k6
# Linux
sudo apt-get install k6
# Windows
choco install k6
```

- [ ] **Step 2: 准备测试数据脚本**

写一个辅助脚本 `stress-test/setup.js`，用 k6 的 `setup()` 阶段注册测试用户并创建日记：

```javascript
// setup.js —— 在压测前创建测试数据
import http from 'k6/http';
import { check } from 'k6';

const BASE = 'http://localhost:18080/api';

export function setup() {
  // 注册 10 个测试用户并各自写 5 篇日记
  const users = [];
  for (let i = 0; i < 10; i++) {
    const email = `loadtest${i}@test.com`;
    const res = http.post(`${BASE}/auth/login`, JSON.stringify({
      email: email,
      password: '123456',
    }), { headers: { 'Content-Type': 'application/json' } });
    let token = null;
    if (res.status === 200) {
      token = res.json().data.token;
    } else {
      // 注册
      const reg = http.post(`${BASE}/auth/register`, JSON.stringify({
        displayName: `Tester${i}`,
        email: email,
        password: '123456',
      }), { headers: { 'Content-Type': 'application/json' } });
      token = reg.json().data.token;
    }
    if (token) {
      // 写 5 篇日记
      const moods = ['今天很开心', '有点累，但还好', '遇到了一些挫折', '平静的一天', '期待明天'];
      for (let j = 0; j < 5; j++) {
        http.post(`${BASE}/diaries`, JSON.stringify({
          content: moods[j] + ` - 来自压测用户${i}的第${j + 1}篇日记`,
          visibility: j % 2 === 0 ? 'PUBLIC' : 'PRIVATE',
        }), { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } });
      }
    }
    users.push({ email, token });
  }
  return { users };
}

export default function() {}
```

- [ ] **Step 3: 冒烟测试脚本**

```javascript
// k6-smoke.js —— 低负载验证所有接口正常
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1,
  iterations: 1,
};

const BASE = 'http://localhost:18080/api';

export default function () {
  // 1. 健康检查（公开接口）
  let res = http.get(`${BASE}/health`);
  check(res, { 'health OK': (r) => r.status === 200 });

  // 2. 登录
  res = http.post(`${BASE}/auth/login`, JSON.stringify({
    email: 'test@test.com', password: '123456',
  }), { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'login OK': (r) => r.status === 200 });
  const token = res.json().data.token;

  const auth = { headers: { 'Authorization': `Bearer ${token}` } };

  // 3. 公开日记流
  res = http.get(`${BASE}/diaries/public?page=1&size=20`, auth);
  check(res, { 'public OK': (r) => r.status === 200 });

  // 4. 我的日记
  res = http.get(`${BASE}/diaries/mine?page=1&size=20`, auth);
  check(res, { 'mine OK': (r) => r.status === 200 });

  // 5. 每日状态
  res = http.get(`${BASE}/diaries/today-status`, auth);
  check(res, { 'today-status OK': (r) => r.status === 200 });

  // 6. 今日同频
  res = http.get(`${BASE}/diaries/today-match`, auth);
  check(res, { 'today-match OK': (r) => r.status === 200 });

  // 7. 社区情绪
  res = http.get(`${BASE}/diaries/community-mood`, auth);
  check(res, { 'community-mood OK': (r) => r.status === 200 });

  // 8. 通知
  res = http.get(`${BASE}/notifications`, auth);
  check(res, { 'notifications OK': (r) => r.status === 200 });

  console.log('Smoke test passed!');
}
```

- [ ] **Step 4: 负载测试脚本**

```javascript
// k6-load.js —— 模拟正常用户流量
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // 爬升到 10 VU
    { duration: '2m',  target: 20 },   // 爬升到 20 VU
    { duration: '2m',  target: 20 },   // 保持 20 VU
    { duration: '30s', target: 0 },    // 降到 0
  ],
  thresholds: {
    'http_req_duration': ['p(95)<2000'],  // 95% 请求 < 2s
    'http_req_failed': ['rate<0.05'],      // 失败率 < 5%
  },
};

const BASE = 'http://localhost:18080/api';

export default function () {
  // 每个 VU 模拟注册用户
  const vuId = __VU;
  const email = `loadtest${vuId % 10}@test.com`;

  // 登录
  const login = http.post(`${BASE}/auth/login`, JSON.stringify({
    email: email, password: '123456',
  }), { headers: { 'Content-Type': 'application/json' } });
  if (login.status !== 200) { sleep(1); return; }
  const token = login.json().data.token;
  const auth = { headers: { 'Authorization': `Bearer ${token}` } };

  // 模拟用户行为循环
  const actions = [
    () => http.get(`${BASE}/diaries/public?page=1&size=10`, auth),
    () => http.get(`${BASE}/diaries/mine?page=1&size=10`, auth),
    () => http.get(`${BASE}/diaries/today-status`, auth),
    () => http.get(`${BASE}/diaries/today-match`, auth),
    () => http.get(`${BASE}/notifications`, auth),
  ];

  for (let i = 0; i < 10; i++) {
    const action = actions[Math.floor(Math.random() * actions.length)];
    const res = action();
    check(res, { 'status 200': (r) => r.status === 200 });
    sleep(Math.random() * 2 + 1); // 1-3 秒间隔
  }
}
```

- [ ] **Step 5: 压力测试脚本**

```javascript
// k6-stress.js —— 高负载找瓶颈
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 30 },
    { duration: '3m', target: 50 },
    { duration: '3m', target: 50 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    'http_req_duration': ['p(95)<3000'],
    'http_req_failed': ['rate<0.10'],
  },
};

const BASE = 'http://localhost:18080/api';

export default function () {
  const vuId = __VU % 10;
  const email = `loadtest${vuId}@test.com`;

  const login = http.post(`${BASE}/auth/login`, JSON.stringify({
    email: email, password: '123456',
  }), { headers: { 'Content-Type': 'application/json' } });
  if (login.status !== 200) { sleep(1); return; }
  const token = login.json().data.token;
  const auth = { headers: { 'Authorization': `Bearer ${token}` } };

  // 密集请求序列
  http.get(`${BASE}/diaries/public?page=1&size=20`, auth);
  http.get(`${BASE}/diaries/public?page=2&size=20`, auth);
  http.get(`${BASE}/diaries/mine?page=1&size=20`, auth);
  http.get(`${BASE}/diaries/today-status`, auth);
  http.get(`${BASE}/diaries/today-match`, auth);
  http.get(`${BASE}/notifications`, auth);
  http.get(`${BASE}/notifications/unread-count`, auth);

  sleep(1);
}
```

---

### Task 7: 运行冒烟测试

**Files:**
- Run: `backend/moodcopilot/stress-test/k6-smoke.js`

- [ ] **Step 1: 确保前后端都在运行**

```bash
# 终端1：后端
cd backend/moodcopilot && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# 终端2：前端（可选）
cd frontend && npx vite --host
```

- [ ] **Step 2: 运行冒烟测试**

```bash
k6 run backend/moodcopilot/stress-test/k6-smoke.js
```

预期：所有 check 通过，无失败。

- [ ] **Step 3: 如果冒烟测试失败，修复后重新跑通再继续**

---

### Task 8: 运行负载测试并记录基线

**Files:**
- Run: `backend/moodcopilot/stress-test/k6-load.js`

- [ ] **Step 1: 先准备测试数据**

```bash
k6 run backend/moodcopilot/stress-test/setup.js
```

- [ ] **Step 2: 运行负载测试**

```bash
k6 run backend/moodcopilot/stress-test/k6-load.js
```

- [ ] **Step 3: 记录基线指标**

将结果输出（avg/p95 响应时间、失败率、吞吐量）记录为基线，用于后续对比。

---

### Task 9: 运行压力测试，记录瓶颈

**Files:**
- Run: `backend/moodcopilot/stress-test/k6-stress.js`

- [ ] **Step 1: 运行压力测试**

```bash
k6 run backend/moodcopilot/stress-test/k6-stress.js
```

- [ ] **Step 2: 观察并记录**

记录 p95 延迟、失败率、吞吐量峰值。观察后端日志是否有异常（OOM、连接超时、Redis 错误）。

---

### Task 10: HikariCP 连接池调优

**Files:**
- Modify: `backend/moodcopilot/src/main/resources/application.yaml`

- [ ] **Step 1: 添加 HikariCP 配置**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30
      minimum-idle: 5
      connection-timeout: 5000
      idle-timeout: 300000
      max-lifetime: 600000
      leak-detection-threshold: 10000
```

- [ ] **Step 2: 重启后端，重新运行压力测试对比**

---

### Task 11: Redis Lettuce 连接池调优

**Files:**
- Modify: `backend/moodcopilot/src/main/resources/application.yaml`

- [ ] **Step 1: 增大 Redis 连接池**

```yaml
redis:
  lettuce:
    pool:
      max-active: 30
      max-idle: 20
      min-idle: 5
      time-between-eviction-runs: 30s
```

---

### Task 12: JVM 与 Docker 资源调优

**Files:**
- Modify: `backend/moodcopilot/Dockerfile`
- Modify: `docker-compose.yml`

- [ ] **Step 1: 后端 Dockerfile 加 JVM 参数**

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 18080
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
```

- [ ] **Step 2: docker-compose.yml 加资源限制**

```yaml
services:
  backend:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 768M
        reservations:
          cpus: '0.5'
          memory: 256M
    restart: unless-stopped

  mysql:
    deploy:
      resources:
        limits:
          memory: 512M
    restart: unless-stopped

  redis:
    deploy:
      resources:
        limits:
          memory: 128M
    restart: unless-stopped
```

- [ ] **Step 3: MySQL 性能配置**

在 `docker-compose.yml` 的 mysql 服务中添加：

```yaml
mysql:
  command: >
    --innodb-buffer-pool-size=128M
    --max-connections=50
    --character-set-server=utf8mb4
    --collation-server=utf8mb4_unicode_ci
```

---

### Task 13: 前端 Nginx gzip + 缓存头

**Files:**
- Modify: `frontend/nginx.conf`

- [ ] **Step 1: 更新 nginx.conf**

```nginx
server {
    listen 80;
    server_name localhost;

    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;
    gzip_min_length 256;
    gzip_vary on;

    # 静态资源缓存
    location /assets/ {
        root /usr/share/nginx/html;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:18080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 120s;
        proxy_buffering off; # SSE 支持
    }
}
```

---

### Task 14: 前端 Vite 代码分割

**Files:**
- Modify: `frontend/vite.config.ts`

- [ ] **Step 1: 添加 manualChunks 配置**

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const apiTarget = mode === 'production'
    ? 'http://backend:18080'
    : 'http://localhost:18080'

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      chunkSizeWarningLimit: 500,
      rollupOptions: {
        output: {
          manualChunks: {
            'naive-ui': ['naive-ui'],
            'vue-vendor': ['vue', 'vue-router', 'pinia'],
          },
        },
      },
    },
  }
})
```

---

### Task 15: Redis KEYS 改为 SCAN 或 Set 维护

**Files:**
- Modify: `backend/moodcopilot/src/main/java/com/moodcopilot/diary/DiaryService.java`

**问题:** `evictUserCache()` 用 `redisTemplate.keys("report:*:0")` 扫描全部 key，O(N) 阻塞操作。

- [ ] **Step 1: 改用精确 key 删除**

```java
private void evictUserCache() {
    Long userId = currentUserId();
    // 精确删除已知 key pattern，避免 KEYS 扫描
    redisTemplate.delete("coaching:" + userId);
    // 报告缓存：最多删除近 4 周的周报 + 近 3 个月的月报
    for (int i = 0; i < 4; i++) {
        redisTemplate.delete("report:" + userId + ":" + i);
    }
    for (int i = 0; i < 3; i++) {
        redisTemplate.delete("report:monthly:" + userId + ":" + i);
    }
    // 公开流缓存：pattern 匹配无法避免，但可以限制范围（最多 5 页）
    for (int page = 1; page <= 5; page++) {
        for (int size : new int[]{10, 20, 50}) {
            redisTemplate.delete("public:diaries:" + page + ":" + size);
        }
    }
    // 关注流缓存
    for (int page = 1; page <= 5; page++) {
        for (int size : new int[]{10, 20, 50}) {
            redisTemplate.delete("following:" + userId + ":" + page + ":" + size);
        }
    }
}
```

---

### Task 16: Docker 部署后最终压测验证

**Files:**
- Run: `backend/moodcopilot/stress-test/k6-stress.js`

- [ ] **Step 1: Docker 启动**

```bash
docker compose up -d
```

- [ ] **Step 2: 等所有服务健康后，运行数据初始化**

```bash
k6 run backend/moodcopilot/stress-test/setup.js
```

- [ ] **Step 3: 运行压力测试**

```bash
k6 run backend/moodcopilot/stress-test/k6-stress.js
```

- [ ] **Step 4: 记录最终结果**

确认 p95 < 3s、失败率 < 10% 的阈值达标。不达标则分析 k6 输出的慢接口，深入排查。

- [ ] **Step 5: Commit**

```bash
git add backend/moodcopilot/stress-test/ backend/moodcopilot/src/ backend/moodcopilot/Dockerfile frontend/vite.config.ts frontend/nginx.conf docker-compose.yml
git commit -m "perf: N+1 批量查询 + 分页 + 连接池/JVM/Docker 资源调优 + 压测脚本"
```

---

## 验证清单

- [ ] N+1 查询：`publicDiaries(1, 20)` 仅执行 3 条 SQL（diaries + batchAnalyses + batchComments）
- [ ] `/api/diaries/mine` 支持分页，默认 page=1 size=20
- [ ] `similar()` 候选池上限 200 篇
- [ ] k6 冒烟测试 100% 通过
- [ ] k6 负载测试 p95 < 2s，失败率 < 5%
- [ ] k6 压力测试 p95 < 3s，失败率 < 10%
- [ ] Docker 部署后压测结果达标
