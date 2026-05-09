# 匿名鼓励 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将"共鸣"按钮扩展为「AI 生成 3 句鼓励话 → 用户选一句匿名发送」，提升陌生人互动的温度和深度。

**Architecture:** 复用现有 `diary_resonances` 表加 `message` 列；新增两个 REST 端点（生成候选 + 发送）；前端 DiaryFeedItem 中点击「鼓励」弹出 3 个候选，选中后 POST 发送。AI 用现有的 `analysisChatClient` Bean 生成候选。

**Tech Stack:** Spring Boot 3.5 + MyBatis-Plus + Spring AI ChatClient + Vue 3 + Naive UI + Flyway

---

## File Structure

| 文件 | 职责 | 操作 |
|------|------|------|
| `db/migration/V1_9__add_encouragement_message.sql` | 给 `diary_resonances` 加 `message VARCHAR(200)` | 新建 |
| `entity/DiaryResonanceEntity.java` | 加 `message` 字段 | 修改 |
| `diary/DiaryService.java` | 新增 `generateEncouragements()` 和 `sendEncouragement()` | 修改 |
| `diary/DiaryController.java` | 新增两个端点 | 修改 |
| `ai/AiAnalysisService.java` | 新增 `generateEncouragements()` 方法 | 修改 |
| `notification/NotificationService.java` | 新增 `notifyEncouragement()` 匿名通知 | 修改 |
| `api/index.ts` | 新增 `encourageCandidates()` 和 `sendEncouragement()` | 修改 |
| `stores/diary.ts` | 新增 `sendEncouragement()` action | 修改 |
| `components/DiaryFeedItem.vue` | 「鼓励」按钮 + 候选面板 + 发送逻辑 | 修改 |
| `styles.css` | 候选面板样式 | 修改 |

---

### Task 1: 数据库迁移 —— diary_resonances 加 message 列

**Files:**
- Create: `backend/moodcopilot/src/main/resources/db/migration/V1_9__add_encouragement_message.sql`
- Modify: `backend/moodcopilot/src/main/java/com/moodcopilot/entity/DiaryResonanceEntity.java`

- [ ] **Step 1: 写迁移 SQL**

```sql
ALTER TABLE diary_resonances ADD COLUMN message VARCHAR(200) NULL AFTER created_at;
```

- [ ] **Step 2: 运行迁移验证**

Run: `cd backend/moodcopilot && eval $(cat ../../.env | tr -d '\r' | grep -v '^#' | grep -v '^$' | sed 's/^/export /') && ./mvnw flyway:migrate -Dflyway.url=jdbc:mysql://localhost:3306/mood_copilot_db -Dflyway.user=$DB_USERNAME -Dflyway.password=$DB_PASSWORD`
Expected: "Successfully applied 1 migration"

- [ ] **Step 3: 更新 Entity**

`DiaryResonanceEntity.java` 加字段：

```java
private String message;

public String getMessage() { return message; }
public void setMessage(String message) { this.message = message; }
```

- [ ] **Step 4: 编译检查**

Run: `cd backend/moodcopilot && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/moodcopilot/src/main/resources/db/migration/V1_9__add_encouragement_message.sql backend/moodcopilot/src/main/java/com/moodcopilot/entity/DiaryResonanceEntity.java
git commit -m "feat: diary_resonances 加 message 列，支持匿名鼓励"
```

---

### Task 2: AI 鼓励候选生成

**Files:**
- Modify: `backend/moodcopilot/src/main/java/com/moodcopilot/ai/AiAnalysisService.java`

- [ ] **Step 1: 写 AI 生成方法**

在 `AiAnalysisService.java` 末尾（`fallbackMonthlySummary` 之后）添加：

```java
private static final String ENCOURAGEMENT_SYSTEM_PROMPT = """
        You are a warm, compassionate stranger. Below is a diary entry. Generate exactly 3 short, anonymous encouragement messages in Chinese, each under 60 characters. They should be gentle, specific (reference the diary content), and feel like a real person wrote them, not a therapist. Format your response as a JSON array of 3 strings, nothing else.
        Example: ["抱抱你，摔倒了没关系，明天又是新的一天","减肥真的好难，但你已经在努力了，这本身就很了不起","我也有过类似的委屈，想说你不是一个人"]""";

public List<String> generateEncouragements(String diaryContent) {
    try {
        String response = analysisChatClient.prompt()
                .system(ENCOURAGEMENT_SYSTEM_PROMPT)
                .user(diaryContent)
                .call()
                .content();
        return objectMapper.readValue(response, new TypeReference<List<String>>() {});
    } catch (Exception e) {
        log.warn("AI encouragement generation failed: {}", e.getMessage());
        return fallbackEncouragements();
    }
}

private List<String> fallbackEncouragements() {
    return List.of(
            "看到你了，今天辛苦了",
            "你的感受很重要，谢谢你的分享",
            "你不是一个人，有我在听"
    );
}
```

- [ ] **Step 2: 编译检查**

Run: `cd backend/moodcopilot && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/moodcopilot/src/main/java/com/moodcopilot/ai/AiAnalysisService.java
git commit -m "feat: AI 生成匿名鼓励候选（3 句中文短句）"
```

---

### Task 3: 后端 API —— 生成候选 + 发送鼓励

**Files:**
- Modify: `backend/moodcopilot/src/main/java/com/moodcopilot/diary/DiaryService.java`
- Modify: `backend/moodcopilot/src/main/java/com/moodcopilot/diary/DiaryController.java`
- Modify: `backend/moodcopilot/src/main/java/com/moodcopilot/notification/NotificationService.java`

- [ ] **Step 1: DiaryService 新增两个方法**

在 `DiaryService.java` 的 `resonate()` 方法之后添加：

```java
public List<String> generateEncouragements(long diaryId) {
    DiaryEntity diary = findPublicDiary(diaryId);
    return aiAnalysisService.generateEncouragements(diary.getContent());
}

@Transactional
public DiaryView sendEncouragement(long diaryId, String message) {
    DiaryEntity diary = findPublicDiary(diaryId);
    UserEntity actor = currentUser();

    DiaryResonanceEntity r = new DiaryResonanceEntity();
    r.setDiaryId(diaryId);
    r.setUserId(actor.getId());
    r.setMessage(message != null && message.length() > 200
            ? message.substring(0, 200) : message);
    diaryResonanceMapper.insert(r);

    diary.setResonanceCount(diary.getResonanceCount() + 1);
    diaryMapper.updateById(diary);

    if (!diary.getAuthorUserId().equals(actor.getId())) {
        notificationService.notifyEncouragement(diaryId, diary.getAuthorUserId(), message);
    }

    return loadDiaryView(diary);
}
```

- [ ] **Step 2: NotificationService 新增匿名通知**

```java
public void notifyEncouragement(Long diaryId, Long recipientUserId, String message) {
    try {
        NotificationEntity n = new NotificationEntity();
        n.setRecipientUserId(recipientUserId);
        n.setDiaryId(diaryId);
        n.setType("ENCOURAGEMENT");
        String preview = message != null && message.length() > 30
                ? message.substring(0, 30) + "..." : message;
        n.setMessage("有人给你的日记送来了鼓励：" + preview);
        notificationMapper.insert(n);
    } catch (Exception e) {
        log.warn("Failed to create encouragement notification: {}", e.getMessage());
    }
}
```

注意：通知 ENUM 需要扩展。如果 MySQL 的 `notifications.type` 列是 `ENUM('COMMENT','REPLY','RESONANCE','SYSTEM')`，需要先 ALTER：

```sql
ALTER TABLE notifications MODIFY COLUMN type ENUM('COMMENT','REPLY','RESONANCE','SYSTEM','ENCOURAGEMENT') NOT NULL;
```

- [ ] **Step 3: Controller 新增两个端点**

```java
@GetMapping("/{id}/encourage-candidates")
public ApiResponse<List<String>> encourageCandidates(@PathVariable long id) {
    return ApiResponse.ok(diaryService.generateEncouragements(id));
}

@PostMapping("/{id}/encourage")
public ApiResponse<DiaryView> sendEncouragement(
        @PathVariable long id,
        @RequestBody Map<String, String> body) {
    return ApiResponse.ok(diaryService.sendEncouragement(id, body.get("message")));
}
```

- [ ] **Step 4: 把通知 ALTER 加到 V1_9 迁移中**

```sql
ALTER TABLE notifications MODIFY COLUMN type ENUM('COMMENT','REPLY','RESONANCE','SYSTEM','ENCOURAGEMENT') NOT NULL;
```

- [ ] **Step 5: 编译检查**

Run: `cd backend/moodcopilot && ./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/moodcopilot/src/main/java/com/moodcopilot/diary/DiaryService.java backend/moodcopilot/src/main/java/com/moodcopilot/diary/DiaryController.java backend/moodcopilot/src/main/java/com/moodcopilot/notification/NotificationService.java backend/moodcopilot/src/main/resources/db/migration/V1_9__add_encouragement_message.sql
git commit -m "feat: 匿名鼓励 API — 生成候选 + 发送鼓励 + 匿名通知"
```

---

### Task 4: 前端 API + Store

**Files:**
- Modify: `frontend/src/api/index.ts`
- Modify: `frontend/src/stores/diary.ts`

- [ ] **Step 1: API 层加两个方法**

在 `diaryApi` 对象中添加：

```typescript
encourageCandidates: (id: number) => api.get(`/diaries/${id}/encourage-candidates`),
sendEncouragement: (id: number, message: string) => api.post(`/diaries/${id}/encourage`, { message }),
```

- [ ] **Step 2: Store 加 action**

在 `useDiaryStore` 中，`resonate()` 函数之后添加：

```typescript
async function sendEncouragement(diaryId: number, message: string) {
    const res = await diaryApi.sendEncouragement(diaryId, message)
    const updated = normalize(res.data.data)
    mergeDiary(updated)
    if (activeDiary.value?.id === diaryId) {
        activeDiary.value = updated
    }
}
```

并在 return 块中导出 `sendEncouragement`。

- [ ] **Step 3: TypeScript 检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无错误输出

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/index.ts frontend/src/stores/diary.ts
git commit -m "feat: 前端鼓励 API + Store action"
```

---

### Task 5: 前端 UI —— 鼓励按钮 + 候选面板

**Files:**
- Modify: `frontend/src/components/DiaryFeedItem.vue`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 替换共鸣按钮行**

将原有单按钮：
```html
<n-button size="small" tertiary @click="$emit('resonate', diary)">
    共鸣 {{ diary.resonanceCount }}
</n-button>
```

替换为：
```html
<div class="encourage-row">
    <n-button size="small" tertiary @click="$emit('resonate', diary)">
        共鸣 {{ diary.resonanceCount }}
    </n-button>
    <n-button size="small" tertiary type="primary" @click="openEncourage">
        鼓励
    </n-button>
</div>
<div v-if="showEncourage" class="encourage-panel">
    <n-spin v-if="encouraging" size="small" />
    <template v-else-if="encourageCandidates.length">
        <p class="encourage-prompt">选一句匿名发送：</p>
        <button
            v-for="(msg, i) in encourageCandidates"
            :key="i"
            class="encourage-option"
            @click="sendEncourage(msg)"
        >{{ msg }}</button>
    </template>
    <p v-if="encourageSent" class="encourage-sent">已匿名发送 🩵</p>
</div>
```

- [ ] **Step 2: Script 加逻辑**

```typescript
const showEncourage = ref(false)
const encouraging = ref(false)
const encourageCandidates = ref<string[]>([])
const encourageSent = ref(false)

async function openEncourage() {
    if (encourageSent.value) return
    showEncourage.value = !showEncourage.value
    if (showEncourage.value && encourageCandidates.value.length === 0) {
        encouraging.value = true
        try {
            const res = await diaryApi.encourageCandidates(props.diary.id)
            encourageCandidates.value = res.data.data ?? []
        } catch { /* ignore */ }
        encouraging.value = false
    }
}

async function sendEncourage(message: string) {
    await store.sendEncouragement(props.diary.id, message)
    encourageSent.value = true
    showEncourage.value = false
}
```

注意：需要在导入中添加 `diaryApi`，以及 `useDiaryStore`（如果还没导入 store）。当前 DiaryFeedItem 已经在用 `useFollowStore` 和 `useAuthStore`。

- [ ] **Step 3: CSS**

```css
.encourage-row {
    display: flex;
    gap: 8px;
    align-items: center;
}

.encourage-panel {
    display: grid;
    gap: 6px;
    padding: 10px;
    background: var(--color-jade-light);
    border-radius: var(--radius-md);
}

.encourage-prompt {
    margin: 0;
    font-size: 12px;
    color: var(--color-text-muted);
}

.encourage-option {
    padding: 8px 12px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    background: var(--color-surface);
    font-size: 14px;
    text-align: left;
    cursor: pointer;
    transition: border-color 0.15s, background 0.15s;
    line-height: 1.5;
}
.encourage-option:hover {
    border-color: var(--color-jade);
    background: #fff;
}

.encourage-sent {
    margin: 0;
    font-size: 13px;
    color: var(--color-jade);
    text-align: center;
}
```

- [ ] **Step 4: TypeScript 检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无错误输出

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/DiaryFeedItem.vue frontend/src/styles.css
git commit -m "feat: 匿名鼓励 UI — 候选面板 + 发送按钮"
```

---

### Task 6: 联调测试

- [ ] **Step 1: 重启后端**

```bash
kill $(netstat -ano | grep ":18080" | awk '{print $5}' | head -1) 2>/dev/null
sleep 2
cd backend/moodcopilot && eval $(cat ../../.env | tr -d '\r' | grep -v '^#' | grep -v '^$' | sed 's/^/export /') && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -q &
sleep 15
```

- [ ] **Step 2: 测试生成候选 API**

```bash
TOKEN=$(curl -s -X POST http://localhost:18080/api/auth/login -H 'Content-Type: application/json' -d '{"email":"test@test.com","password":"123456"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -s "http://localhost:18080/api/diaries/2006/encourage-candidates" -H "Authorization: Bearer $TOKEN"
```

Expected: `{"code":0,"message":"ok","data":["候选1...","候选2...","候选3..."]}`

- [ ] **Step 3: 测试发送鼓励 API**

```bash
curl -s -X POST "http://localhost:18080/api/diaries/2006/encourage" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"message":"测试鼓励消息"}'
```

Expected: `{"code":0,"data":{"resonanceCount":N+1,...}}`

- [ ] **Step 4: 验证通知**

```bash
curl -s "http://localhost:18080/api/notifications" -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; ns=json.load(sys.stdin)['data']['items']; [print(n['message']) for n in ns[:3]]"
```

Expected: 最上面一条是 "有人给你的日记送来了鼓励：测试鼓励消息"

- [ ] **Step 5: 浏览器验证**

用 `/browse` 打开 `http://localhost:5173`，在广场中找到一篇日记，点击「鼓励」，确认弹出 3 个候选，点击一个，确认显示「已匿名发送」。

- [ ] **Step 6: Commit**

```bash
git commit -m "test: 匿名鼓励 API 联调验证通过" --allow-empty
```

---

## 验证清单

- [ ] `GET /api/diaries/{id}/encourage-candidates` 返回 3 个 AI 生成的中文短句
- [ ] `POST /api/diaries/{id}/encourage` 存储 message、增加 resonanceCount、发送匿名通知
- [ ] 通知中不暴露发送者身份（"有人给你..."）
- [ ] 前端候选面板展示 3 个选项，点击后发送并显示成功状态
- [ ] 编译：`./mvnw compile` 和 `npx vue-tsc --noEmit` 均通过
- [ ] 已有共鸣按钮不受影响
