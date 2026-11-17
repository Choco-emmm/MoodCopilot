# 记忆生命周期与新鲜度

> 状态：**已实现第一步**（开关默认 `off`，未启用）；前端展示与 `enforce` 灰度未做
> 日期：2026-11-17
> 参考：OpenBiliClaw `soul/topic_lifecycle.py`、`soul/pipeline.py`（分层节流）
> 关联：`docs/MEMORY-WRITES.md`、`AGENTS.md`「记忆写入」章节

## 0. 一句话

给 `user_profile_memory` 增加一个**与 `status` 正交的生命周期维度**，用每日任务把长期未被强化、低置信度、AI 推断来源的持续型记忆逐步标记为「衰减」和「归档」，归档后退出上下文与 RAG，但保留完整数据和可复活能力。衰减的计时单位不是日历天数，而是**该用户产生新内容的次数**，因此对高频与低频用户自动等价。

## 1. 先纠正一个前提

参考实现用 `weight × 0.9^(沉默周数)` 做兴趣衰减，因为兴趣权重服务于推荐排序——不活跃就该降权。**这个判据不能直接搬到 MoodCopilot。**

MoodCopilot 的记忆是**事实断言**，不是排序权重。一条「用户不喜欢香菜」在两年后没有被再次提及，不构成它变假。把「未被强化」当成「已失效」，对情绪陪伴产品是信任损失：用户会说"这我早就告诉过你了"。

所以本方案拆成两个互不混用的轴：

| 轴 | 含义 | 变化方向 | 用途 |
|---|---|---|---|
| `confidence` | 断言有多可信 | 只随证据上升（现有 `Math.max` 逻辑） | 决定是否采信 |
| `freshness` | 多久没被重新强化 | 只随时间下降 | 决定展示优先级、是否降噪 |

**两者绝不合并成一个数**，也不允许用 `freshness` 反过来压低 `confidence`。

在此基础上，归档只作为**降噪手段**存在，且必须同时满足「低置信 + 久未强化 + 非用户来源 + 非核心 + 持续型」五重条件。任意一条不满足就保留——宁可留僵尸条目，不可丢用户说过的话。

## 2. 目标与非目标

### 目标

1. 长期画像里不再堆积大批半年没被触发的 AI 推断条目，减少上下文预算浪费。
2. 归档可逆：任何一次同值证据命中都能自动复活，不需要用户操作。
3. 全链路可观测：每条记忆为什么被归档有明确记录。
4. 回滚成本接近零：不改 `status`、不删数据。

### 非目标

- 不自动删除任何记忆（删除只由用户显式触发，走 `purgeMemory`）。
- 不改 `confidence` 语义，不改候选审批流程。
- 不在本期改变上下文排序算法（`freshness` 先只观测，不参与 `ContextPlanner` 打分）。
- 不处理 `relationship`、`event`、`short_term_state` 三类（理由见 §4.2）。

## 3. 数据模型

### 3.1 为什么必须新增列，不能复用 `status`

`V1_47__add_ai_task_and_memory_governance.sql` 建立了这个约束：

```sql
ADD COLUMN active_attribute_key VARCHAR(64)
  GENERATED ALWAYS AS (IF(status = 'active', attribute_key, NULL)) STORED,
ADD UNIQUE KEY uk_memory_one_active_key (user_id, active_attribute_key);
```

即**每个用户每个属性最多一条 `status='active'` 记录**。这带来两个决定性后果：

- 若用 `status='archived'` 表达归档，生成列变 NULL，唯一槽位被释放，同一属性可能出现并存的 `archived` 行和新 `active` 行；同时复活时必须把 `status` 改回 `active`，这会与 `superseded`（不可复活）的语义混在一起。
- 若用独立列，`status` 恒为 `active`，唯一槽位占住不被复用，**记忆抽取按属性查找时能直接命中归档行**，复活就是一次字段回写，不产生新版本行，`previous_memory_id` 版本链不被"假变更"污染。

结论：用独立列。复活免费是这个选择的直接收益。

### 3.2 迁移 `V1_64__add_memory_lifecycle.sql`

```sql
ALTER TABLE user_profile_memory ADD COLUMN lifecycle_state VARCHAR(16) NOT NULL DEFAULT 'active';
ALTER TABLE user_profile_memory ADD COLUMN freshness DECIMAL(5,4) NOT NULL DEFAULT 1.0000;
ALTER TABLE user_profile_memory ADD COLUMN lifecycle_reason VARCHAR(64) NULL;
ALTER TABLE user_profile_memory ADD COLUMN lifecycle_updated_at DATETIME(3) NULL;

CREATE INDEX idx_memory_lifecycle_scan ON user_profile_memory (lifecycle_state, last_evidence_at);
CREATE INDEX idx_memory_user_lifecycle ON user_profile_memory (user_id, lifecycle_state);

UPDATE user_profile_memory
   SET lifecycle_updated_at = COALESCE(last_evidence_at, updated_at)
 WHERE lifecycle_updated_at IS NULL;
```

**不需要给 `diaries` 建索引**：`V1__init_schema.sql` 第 29 行已经建过 `idx_diaries_author_created (author_user_id, created_at)`，曝光统计直接复用它。

> 这一条是本方案踩过的坑：初稿在这里又写了一次 `CREATE INDEX idx_diaries_author_created`，MySQL 报 `Duplicate key name`。因为 MySQL 的 DDL 不能回滚，前面四条 `ALTER` 已经生效、迁移却被标记为失败，Flyway 会拒绝启动——生产上表现为发布直接失败。**新增迁移前必须先 grep `V1__init_schema.sql` 确认索引是否已经存在。**

`lifecycle_reason` 取值（枚举，写入后不改）：

| 值 | 含义 |
|---|---|
| `STALE_INFERRED` | 低置信推断型记忆长期未被强化 |
| `REVIVED_BY_EVIDENCE` | 新证据命中已归档条目，自动复活 |
| `RESTORED_BY_USER` | 用户在记忆中心手动恢复 |
| `USER_CONFIRMED_PROTECTED` | 用户确认过，被规则保护（仅审计用） |

`freshness` 是每日任务刷新的**快照**，读侧只能把它当作展示/排序提示，**不得作为有效性判据**——有效性只由 `lifecycle_state` 和 `status` 决定。

### 3.3 实体改动

`UserProfileMemoryEntity` 增加四个字段与 getter/setter，风格与现有 `status` 一致：

```java
private String lifecycleState = "active";
private Double freshness = 1.0;
private String lifecycleReason;
private LocalDateTime lifecycleUpdatedAt;
```

## 4. 判定算法

### 4.1 计时单位：用曝光次数，不用日历天数

原始草案用「沉默天数」做衰减，问题在于它对不同活跃度的用户完全不等价：对每天写日记的人，107 天意味着 107 次重新提及的机会；对每月写 4 篇的人，107 天只有 14 次机会。同一个阈值，前者合理，后者等于随机清零。

**按活跃度调整窗口长度**是一种解法，但它引进了分档表和边界跳变：用户在档位边界附近晃动时，窗口会突变，记忆会反复「归档→复活」，制造抖动。

更稳的做法是**替换计时单位**——衰减衡量的本来就不是时间，而是「这条记忆错过了多少次被重新提及的机会」。于是：

```
参照时间 ref = lastEvidenceAt ?? updatedAt ?? 首次写入时间
等效活跃周数 weeks = 归一化后的双来源活跃度      // 见 §4.2
exposure = weeks × STEPS_PER_WEEK
raw  = 0.9 ^ weeks
freshness = clamp(raw, 0.05, 1.0)
```

`exposure` 只是 `weeks` 的整数刻度表示（落库与展示用），衰减本身由 `weeks` 驱动。

- `STEPS_PER_WEEK = 7` 的语义锚点：**对一个每天写日记的用户，衰减速度与日历完全一致**（对齐原方案的 107 天）。
- 低频用户自动放宽：每月 4 篇的用户，走完同样曲线需要约 26 个月，不需要任何档位配置。
- `exposure` 单调递增，不会因为滚动窗口回退而抖动，这是它相对「按活跃度分档」的决定性优势。

下限取 `0.05` 而不是 0：避免展示层出现「新鲜度 0%」，也避免排序时把大量行挤成同一档。

阈值换算：

| freshness | 每天写日记的用户 | 每月写 4 篇的用户 | 状态 |
|---|---|---|---|
| ≥ 0.45 | 约 53 天 | 约 13 个月 | `active` |
| 0.20 ~ 0.45 | 53 ~ 107 天 | 13 ~ 26 个月 | `decaying` |
| < 0.20 | 超过 107 天 | 超过 26 个月 | 进入归档判定 |

一个刻意保留的缺口：**完全停更的用户 `exposure = 0`，记忆永不衰减**。这不是漏洞——停更用户没有上下文成本，而如果一年后回来写第一篇日记，旧记忆全量进入上下文恰恰是正确行为，AI 需要知道他之前是谁。不设日历硬上限。

### 4.2 双来源的量纲对齐：速率归一化 + 相对上限

日记与聊天的产出频率差几个数量级（每天 1 篇 vs 每天 10 个会话），直接加权（1.0 / 0.5）等于把「谁的计数大」错当成「谁更能代表活跃」。两个方向都会失真：日记型用户的聊天项恒为 0，聊天型用户的日记项恒为 0。

**给每个来源设绝对上限（`min(count, CAP)`) 不能解决这个问题**：一条沉默三年的记忆，日记数远超任何固定 `CAP`，上限在长时间尺度上会被一直顶满而失去区分度，等于没有上限。

正确的做法是先把两个来源折算到同一个无量纲单位——**「等效活跃周数」**，再用相对形式限制主导权。

#### 第一步：折算成等效活跃周数

```
// 每个来源各自的"典型速率"，用该用户自己的历史，不用全局常数
spanWeeks  = clamp( (now - 最早内容时间) / 1周, 1, 52 )
rateD      = 近 spanWeeks 内日记数 / spanWeeks      // 篇/周
rateC      = 近 spanWeeks 内会话数 / spanWeeks      // 会话/周

weeksD = countAfter(diaries, ref) / max(rateD, MIN_RATE)
weeksC = countAfter(conversations, ref) / max(rateC, MIN_RATE)
```

除以自身速率后，`weeksD` 的含义是「这条记忆错过了该用户多少周的**日记产出**」，`weeksC` 同理。两个数天然同量纲，可以直接比较。

`spanWeeks` 必须用**真实内容跨度**而不是固定 52 周。新用户注册一个月写了 30 篇，若用固定 52 周做分母，`rateD = 0.58`，`weeksD = 30 / 0.58 ≈ 52` 周——把一个月误算成一年，直接过度归档。用真实跨度 `spanWeeks = 4`，`rateD = 7.5`，`weeksD = 4` 周，正确。

#### 第二步：相对上限（融合）

```
weeks = max(weeksD, weeksC) + FUSION_WEIGHT × min(weeksD, weeksC)
exposure = weeks × STEPS_PER_WEEK
```

`FUSION_WEIGHT = 0.5` 的直接效果：

```
max(a,b) ≤ weeks ≤ 1.5 × max(a,b)
```

即**任一来源最多把总曝光主导到 1.5 倍**，不可能出现「聊天量 20 倍于日记，曝光就 20 倍」的失控。这是"上限"的正确形式——它相对于双方的实际规模，所以在任何时间尺度上都成立，不会像绝对 `CAP` 那样被顶满失效。

同时保留加法的语义：两个来源都活跃时确实提供了更多重新提及的机会，`1.5` 倍是对"机会可叠加但不能简单相加"的折中。（纯取 `max` 会让双高用户和单高用户完全等价，丢失信息。）

#### 第三步：参数

| 常量 | 默认值 | 含义 | 调整影响 |
|---|---|---|---|
| `STEPS_PER_WEEK` | 7 | 曝光单位/活跃周 | 全局快慢，锚定"每天一篇日记 = 日历速度" |
| `FUSION_WEIGHT` | 0.5 | 弱来源补充系数 | 越大，双来源用户衰减越快；上限 = `1 + FUSION_WEIGHT` |
| `MIN_RATE` | 1.0 | 速率下限（单位/周） | 防止低频来源的小样本被放大成大量"周" |
| `RATE_WINDOW_WEEKS` | 52 | 速率统计窗口上限 | 越长越平滑，越短越贴近当前习惯 |

`MIN_RATE` 不可省。假设某用户 `rateC = 0.2 会话/周`（一年聊 10 次），沉默 60 天里聊了 2 次，若不做下限约束 `weeksC = 2 / 0.2 = 10` 周，反而超过了日记项的 8.57 周——低频来源的偶发事件会主导衰减。加上 `MIN_RATE = 1.0` 后 `weeksC = 2`，符合直觉。

#### 三种典型用户的验算

沉默 60 天，`STEPS_PER_WEEK = 7`：

| 用户类型 | rateD / rateC | 60 天内 | weeksD / weeksC | weeks | freshness | 状态 |
|---|---|---|---|---|---|---|
| 日记型 | 7 / 0.2 | 日记 60、会话 2 | 8.57 / 2.00 | 9.57 | 0.36 | `decaying` |
| 聊天型 | 0.5 / 14 | 日记 1、会话 120 | 1.00 / 8.57 | 9.07 | 0.38 | `decaying` |
| 双高型 | 7 / 14 | 日记 60、会话 120 | 8.57 / 8.57 | 12.86 | 0.26 | `decaying` |

三种用户的量级落在同一区间（0.26 ~ 0.38），没有哪一类被系统性偏袒——这就是量纲对齐要达到的效果。双高型略快（1.5 倍），对应"机会确实更多"。

#### 为什么不用「每次抽取 +1」的增量计数

需要改 `MemoryExtractionService` 主链路（`docs/MEMORY-WRITES.md` 划定的写入边界不应混入衰减逻辑），抽取重试会漂移，历史数据无法回填，且无法做上述速率归一化（拿不到历史速率）。

> **标定声明**：以上四个常量均为首轮默认值。参考实现在 `topic_lifecycle.py` 里对同类阈值的处理值得照搬——明确标注"first-round default，首个生产月后重开标定，且任何模型/供应商切换后重标"。`shadow` 期应按三个用户画像分桶统计归档率，用真实分布回校 `STEPS_PER_WEEK` 与 `FUSION_WEIGHT`。

### 4.3 成员资格：哪些记忆参与衰减

| memory_type | 参与 | 理由 |
|---|---|---|
| `preference` | 是 | 偏好会迁移，长期无证据说明可能已变化 |
| `habit` | 是 | 习惯会中断，长期无证据说明可能已停止 |
| `pattern` | 是 | 行为模式有阶段性 |
| `relationship` | **否** | 关系不因沉默而消失，人物关系失效必须有否定证据 |
| `event` | **否** | 事件有 `validUntil` 或天然一次性，与新鲜度无关 |
| `short_term_state` | **否** | 已有 `validUntil` TTL 机制，重复判定会互相干扰 |

### 4.4 归档条件

必须**全部**满足才归档，任一不满足即保留：

| # | 条件 | 说明 |
|---|---|---|
| 1 | `status = 'active'` | 已经 superseded / expired / rejected 的不动 |
| 2 | `lifecycle_state <> 'archived'` | 幂等保护 |
| 3 | `memory_type IN ('preference','habit','pattern')` | §4.3 |
| 4 | `is_core = 0` | 用户标记的核心属性永不自动归档 |
| 5 | `source_type IN ('diary_inferred','chat_candidate','system')` | **用户 `explicit` 来源永久豁免** |
| 6 | `confidence < 0.65` | 用置信度作为证据强度代理；多次同值强化会经 `touchFormalMemory` 的 `Math.max` 抬过这条线 |
| 7 | `valid_until IS NULL` | 有显式有效期的交给 `validUntil` 机制，不重复判 |
| 8 | `freshness < 0.20` | 曝光不足以维持，含义随用户活跃度自适应（§4.1） |
| 9 | `calendarDays >= 90` | **护栏**：防止一次批量补录（例如导入 300 篇历史日记）当天把记忆全部曝光清零 |

条件 6 是本方案依赖的关键既有能力：`MemoryOrchestrator.touchFormalMemory()` 在每次同值证据追加时执行 `old.setConfidence(Math.max(old.getConfidence(), confidence))`。因此"被反复提到"的条目会自然逃离归档，不需要额外统计证据条数。

条件 9 与条件 8 是**同时满足**（AND）关系，不是取宽：曝光可以在一天内暴涨，但日历必须先走过 90 天，两者都不达标才归档。这让补录、导入、批量迁移这类非自然行为无法推动状态机。

### 4.5 状态机

```
                 freshness<0.45
      active ──────────────────▶ decaying
        ▲  ◀──────────────────      │
        │      freshness≥0.45      │ freshness<0.20 且 §4.4 全满足
        │                          ▼
        └────────────────────── archived
             新证据 / 用户恢复
```

- `decaying` **不改变任何现有行为**，只在记忆中心显示"较久未提及"，属纯观测态。这是刻意设计：让入库后先跑一段肉眼可检验的中间态，再谈是否参与排序。
- `archived` 退出上下文与 RAG，但保留在库中并可在记忆中心查看。

## 5. 代码改造清单

### 5.1 新增 `MemoryLifecyclePolicy`

`src/main/java/com/moodcopilot/ai/MemoryLifecyclePolicy.java`，纯静态、无依赖、可单测：

```java
public final class MemoryLifecyclePolicy {

    public static final String STATE_ACTIVE = "active";
    public static final String STATE_DECAYING = "decaying";
    public static final String STATE_ARCHIVED = "archived";

    /** 一个"活跃周"对应的曝光单位数。7 = 每天写一篇日记，用于对齐日历语义。 */
    public static final int STEPS_PER_WEEK = 7;
    /** 弱来源的补充系数；主导上限 = 1 + FUSION_WEIGHT。 */
    public static final double FUSION_WEIGHT = 0.5;
    /** 来源速率下限（单位/周），防止低频来源的小样本被放大。 */
    public static final double MIN_RATE_PER_WEEK = 1.0;
    /** 速率统计窗口上限。 */
    public static final int RATE_WINDOW_WEEKS = 52;
    public static final double DECAYING_BELOW = 0.45;
    public static final double ARCHIVE_BELOW = 0.20;
    public static final int MIN_CALENDAR_DAYS = 90;
    public static final double MAX_ARCHIVE_CONFIDENCE = 0.65;

    /** 上下文 / 索引的准入谓词：只判归档，不判有效期，其余条件由调用方保留。 */
    public static boolean passLifecycle(UserProfileMemoryEntity memory) {
        return memory != null
                && memory.getLifecycleState() != null
                && !STATE_ARCHIVED.equalsIgnoreCase(memory.getLifecycleState().trim());
    }

    /** 等效活跃周数 → 新鲜度。纯函数，无时间依赖，便于单测。 */
    public static double freshness(double weeks) {
        double raw = Math.pow(0.9, weeks);
        return Math.max(0.05, Math.min(1.0, raw));
    }

    /** 判定目标状态；返回 null 表示维持现状（不变更，避免无谓 UPDATE）。 */
    public static String evaluate(UserProfileMemoryEntity memory, double exposure, long calendarDays) { ... }
}
```

`evaluate()` 内部顺序固定为：已归档 → 返回 `null`；不具备衰减资格（§4.3 类型、`is_core`、`source_type`、`valid_until`）→ 返回 `null`；`freshness < ARCHIVE_BELOW` 且 `calendarDays >= MIN_CALENDAR_DAYS` 且 `confidence < MAX_ARCHIVE_CONFIDENCE` 且当前非 `archived` → `archived`；否则 `freshness < DECAYING_BELOW` → `decaying`；否则 `active`。

命名刻意与 `MemoryWritePolicy`、`MemorySafetyPolicy` 对齐。

### 5.2 资格判定收敛（4 处）

**不要重构现有逻辑，只插入一个新谓词**，把爆炸半径压到最小：

| 文件 | 位置 | 改动 |
|---|---|---|
| `ContextPlanner.java` | `isEligibleFormal`（约 346 行） | 在 `status` 判断后加 `if (!MemoryLifecyclePolicy.passLifecycle(memory)) return false;` |
| `ContextPlanner.java` | `isEligibleShortTerm`（约 358 行） | 同上（短期状态不会归档，此处是防御性一致） |
| `RagMemoryService.java` | `isEligibleProfileMemory`（约 657 行） | 同上 |
| `RagMemoryService.java` | 关键词兜底查询（约 1095 行 wrapper） | 增加 `.ne(UserProfileMemoryEntity::getLifecycleState, "archived")` |

`ContextPlanner.isAllowedSource` 是基于 `sourceType` 的子串过滤，与生命周期无关，**不改**。

`MemoryOrchestrator.current()` **不改**：它要继续返回归档行，因为记忆中心需要展示，且抽取匹配需要命中归档行才能复活。归档行的排除全部发生在读取侧谓词上。

### 5.3 复活钩子

在 `MemoryOrchestrator.touchFormalMemory(long memoryId, Double confidence)` 内，更新置信度的同时回写生命周期：

```java
// 同值证据命中即视为"再次被用户提到"，无条件复活并刷新新鲜度
memory.setLifecycleState(MemoryLifecyclePolicy.STATE_ACTIVE);
memory.setFreshness(1.0);
memory.setLifecycleReason("REVIVED_BY_EVIDENCE");
memory.setLifecycleUpdatedAt(businessNow());
```

该方法是 `selectById` → 改字段 → `updateById`，且已显式 `setUpdatedAt(businessNow())`，插入这三行不需要调整事务或锁。

因为 §3.1 的选型，归档行的 `status` 仍是 `active`，抽取流程的按属性查找链路天然命中它，复活不需要任何额外查询或分支。

### 5.4 每日任务 `MemoryLifecycleScheduler`

```java
@Scheduled(cron = "${moodcopilot.memory-lifecycle.cron:0 30 4 * * *}")
```

- 按 `user_id` 分批，批大小 500，参照 `ReportGenerationScheduler` 的分批写法。
- 每批用一条只读查询捞出候选（`status='active' AND is_core=0 AND memory_type IN (...) AND source_type <> 'explicit'`）。
- **每用户只建一次曝光索引**，供该用户全部候选复用（见 §5.5）。
- 在内存里算 `evaluate()`，只对有变化的行发 UPDATE。
- 单用户失败只记日志不中断整批，与 `ReportGenerationScheduler.generateForUsers` 一致。
- 全局开关读 `SystemConfigService.getConfig("memory.lifecycle.mode", ...)`。

### 5.5 曝光的采集与查询成本

新增 `MemoryExposureIndex`，每用户构建一次、只读、可缓存：

```java
/** 该用户全部内容的升序时间戳；每用户构建一次，供该用户所有候选记忆复用。 */
public record MemoryExposureIndex(long[] diaryMillis, long[] conversationMillis, long nowMillis) {

    private static final long WEEK_MILLIS = 7L * 86_400_000L;

    /** 返回等效活跃周数（§4.2）。 */
    public double weeksSince(long refMillis) {
        long contentStart = earliest();
        double spanWeeks = clamp((nowMillis - contentStart) / (double) WEEK_MILLIS,
                                 1.0, MemoryLifecyclePolicy.RATE_WINDOW_WEEKS);

        double rateD = Math.max(countBetween(diaryMillis, contentStart, nowMillis) / spanWeeks,
                                MemoryLifecyclePolicy.MIN_RATE_PER_WEEK);
        double rateC = Math.max(countBetween(conversationMillis, contentStart, nowMillis) / spanWeeks,
                                MemoryLifecyclePolicy.MIN_RATE_PER_WEEK);

        double weeksD = countAfter(diaryMillis, refMillis) / rateD;
        double weeksC = countAfter(conversationMillis, refMillis) / rateC;

        double strong = Math.max(weeksD, weeksC);
        double weak = Math.min(weeksD, weeksC);
        return strong + MemoryLifecyclePolicy.FUSION_WEIGHT * weak;
    }
}
```

`countAfter` / `countBetween` 用 `Arrays.binarySearch` 定位下标后相减，复杂度 `O(log n)`；整个用户一轮是 `O((D + C) + M log D)`，`M` 为该用户候选记忆数，实际都是几十条量级。

构建时的两条查询，都只需要时间戳列：

```sql
SELECT created_at FROM diaries
 WHERE author_user_id = ? AND is_deleted = 0
 ORDER BY created_at;

SELECT created_at FROM chat_conversations
 WHERE user_id = ?
 ORDER BY created_at;
```

**不要加 `created_at >= ?` 的时间下界**：速率归一化需要该用户的完整历史来算 `contentStart` 与 `rateD / rateC`，只取窗口内的数据会让速率失真。单用户内容量级在千条以内，全量拉取的时间戳内存开销可忽略。

三个实现约束：

1. **每用户构建一次索引**，供该用户全部候选记忆复用，不要按记忆逐条查询。
2. **`diaries` 的 `is_deleted = 0` 必须显式写**。这里走的是自定义列查询而非实体查询，`@TableLogic` 不会自动生效，漏掉会把用户删掉的日记算成曝光和速率。
3. **`nowMillis` 由任务统一传入一次**，同一用户全部记忆用同一个 `now`，否则跨午夜执行时同一批的记忆会拿到不同的时间基准。

补录/导入场景（一次性写入大量历史日记）仍由 §4.4 条件 9 的 `calendarDays >= 90` 护栏兜底：归一化会摊薄速率、`contentStart` 前移，但当天归档依然被日历条件拦住。

曝光查询走 `diaries(author_user_id, created_at)`，该索引由 `V1__init_schema.sql` 建立、`V1_64` 不重复创建（见 §3.2）。`chat_conversations` 已有 `idx_conv_user (user_id)`，会话量级小，不必新增。

### 5.6 并发与幂等

写入用条件更新做 CAS，**不新增 version 字段**——`updated_at` 已有 `ON UPDATE CURRENT_TIMESTAMP(3)`，任何写操作都会刷新它：

```sql
UPDATE user_profile_memory
   SET lifecycle_state = ?, freshness = ?, lifecycle_reason = ?, lifecycle_updated_at = ?
 WHERE id = ?
   AND status = 'active'
   AND lifecycle_state <> ?
   AND updated_at = ?
```

影响行数为 0 说明期间被并发写过（抽取追加了证据），本次跳过，下次任务重算。这样即使任务与 `touchFormalMemory` 同时发生，也不会把刚复活的条目重新归档。

任务本身不需要分布式锁：多实例并发跑同一批，CAS 会让重复更新变成零影响，任务天然幂等。曝光索引是纯读，天然可并发。

## 6. 接口与前端契约

> 已实现。实现时把草案的 `includeArchived` 参数换成了独立的 `?lifecycle=archived` 查询，
> 理由见下方「与草案的偏差」。

`GET /api/memory` 的默认行为不变（只返回生效记忆），列表项与详情增加四个字段（只增不改，向后兼容）：

```java
map.put("lifecycleState", m.getLifecycleState());
map.put("freshness", m.getFreshness());
map.put("lifecycleReason", m.getLifecycleReason());
map.put("lifecycleUpdatedAt", m.getLifecycleUpdatedAt());
```

已归档列表走独立查询，避免把归档行混进默认列表：

```
GET /api/memory?lifecycle=archived
```

恢复接口：

```
PATCH /api/memory/{id}/lifecycle   body: {"state":"active"}
```

只接受 `active`，其它值返回 400。服务端只允许 `archived → active`，写 `lifecycle_reason = 'RESTORED_BY_USER'`，不新建版本行。

> **发布顺序硬约束**：迁移 + 后端（shadow）→ 前端展示「已归档」分组与恢复按钮 → 才允许把开关切到 `enforce`。三者不可拆开发布。

前端契约（本次只改 `frontend/`；`frontend-uniapp/` 无记忆中心页面，无需同步）：

- 记忆中心默认列表只显示 `active` + `decaying`。
- `decaying` 行加一行次级说明「较久没有再次提到，参考优先级已降低」，不使用警告色，不改变主层级（遵循 `AGENTS.md` 视觉规范：状态不得仅靠颜色表达）。
- 正式记忆列表下方提供「已归档的记忆 · N 条」折叠入口，**收起时不发请求，展开才懒加载**；展开后可单条恢复，恢复成功后从归档列表移除并刷新正式列表。

**与草案的偏差**：草案写的是 `includeArchived` 布尔参数。实现改成独立 `lifecycle` 取值，原因是前端要懒加载归档列表——用布尔参数的话，一次请求会同时带回生效和归档两组数据，归档行的证据关联（`sourceSummariesForMemories`）也会被一并查询，对低频视图是不必要的开销。独立查询让两者互不干扰，且默认路径的 SQL 与响应结构完全不变。

## 7. 灰度

`system_config` 增加 `memory.lifecycle.mode`，三档，直接借鉴 OpenBiliClaw `posture_gate` 的 shadow 设计：

| 模式 | 行为 |
|---|---|
| `off` | 任务完全不运行，零副作用（默认值） |
| `shadow` | 完整计算 `evaluate()` 与 `freshness`，**只写 `freshness` 字段和 INFO 日志，不改 `lifecycle_state`**；用于观察"如果生效会归档多少条" |
| `enforce` | 按 §4.4 实际改状态 |

`shadow` 至少跑满 7 天，且统计口径满足以下条件才切 `enforce`：

- 拟归档条目占该用户 `active` 总量 < 40%（超过说明阈值定激进或抽取噪声大）；
- 抽样 20 条人工确认「确实可以不再注入上下文」；
- 复活率（归档后 7 天内被新证据复活）< 10%（超过说明条件 6 的 0.65 定低了）。

**统计必须按活跃度分桶**：`exposure` 均值分三档（低 / 中 / 高）分别算归档率。不分桶的话，少数高频用户会主导全局比例，把低频用户被过度归档的问题掩盖掉——而这正是自适应阈值要解决的核心场景，也是唯一需要盯的回归点。

## 8. 测试清单

| 测试 | 覆盖点 |
|---|---|
| `MemoryLifecyclePolicyTest` | §4.4 九条条件的组合表驱动；`relationship` / `event` / `short_term_state` 不参与；`explicit` 与 `is_core` 豁免；边界 freshness 0.45 / 0.20 |
| `MemoryLifecycleExposureTest` | 等效活跃周数换算：`spanWeeks` 用真实跨度而非固定 52（新用户注册 1 个月写 30 篇 → `weeksD ≈ 4` 而非 52）；`MIN_RATE` 使低频来源的偶发事件不被放大；`is_deleted=1` 的日记不计入曝光与速率 |
| `MemoryLifecycleFusionTest` | 主导上限：`weeks` 恒在 `[max(a,b), 1.5×max(a,b)]` 区间内；§4.2 三种典型用户（日记型 / 聊天型 / 双高型）在同样 60 天沉默下 freshness 落在 0.26~0.38，量级一致 |
| `MemoryLifecycleAdaptiveTest` | **同样的 6 个月沉默**：每天写日记的用户判定 `archived`，每月写 4 篇的用户保持 `active`——这是自适应阈值的核心断言 |
| `MemoryLifecycleGuardTest` | 补录 300 篇同日历史日记时，`calendarDays < 90` 使归档不生效（条件 9 护栏） |
| `MemoryLifecycleEligibilityTest` | 归档行不进 `ContextPlanner` 正式记忆与短期状态；不进 RAG 画像索引；不进关键词兜底结果 |
| `MemoryLifecycleReviveTest` | 归档行被同值证据命中 → 回 `active`、`freshness=1.0`、**`id` 不变且不新增版本行**、`previous_memory_id` 链不受影响 |
| `MemoryLifecycleCasTest` | 任务计算期间 `touchFormalMemory` 更新 `updated_at`，任务 UPDATE 影响行数为 0，不覆盖复活结果 |
| `MemoryLifecycleIdempotentTest` | 同一数据连跑两次任务，第二次零变更 |
| `MemoryLifecycleShadowTest` | `shadow` 模式只写 `freshness`，`lifecycle_state` 恒为 `active` |
| `UserProfileMemoryControllerLifecycleTest` | `includeArchived` 默认 false；恢复接口只接受 `archived → active` |
| 迁移测试 | 在 H2 上执行 `V1_64`，验证默认值、两个新索引与回填 `lifecycle_updated_at` |

按 `docs/MEMORY-WRITES.md`「验证与发布边界」，后端执行 `mvn --batch-mode --no-transfer-progress clean verify`，最后 `git diff --check`。

## 9. 回滚

因为归档不改 `status`、不删数据、不写新版本行，回滚是一次 UPDATE：

```sql
UPDATE user_profile_memory
   SET lifecycle_state = 'active', lifecycle_reason = NULL
 WHERE lifecycle_state IN ('decaying', 'archived');
```

配合把 `memory.lifecycle.mode` 置回 `off`。无数据损失，无需回滚 Flyway 迁移（新增列可空或不影响旧代码）。

## 10. 明确不做

- **不做自动删除**。归档永远不等于删除，`purgeMemory` 是唯一删除路径。
- **不把 `freshness` 合并进 `confidence`**。事实可信度不随时间下降，只随证据上升。
- **不让 `archived` 释放唯一槽位**。归档行必须继续占住 `uk_memory_one_active_key`，否则同一属性会出现并存行，破坏版本链。
- **不在本期参与排序**。`freshness` 先只观测，等 `shadow` 数据证明它有区分度，再讨论是否进入 `ContextPlanner` 的 priority 计算。
- **不动 `short_term_state` 与 `event`**。它们已有 `validUntil` 机制，重复语义会互相干扰。

## 11. 待确认

1. `STEPS_PER_WEEK = 7` 的标定依据是「每天一篇日记」，但真实用户的日记频率分布可能集中在每周 1~3 篇。这个常数一旦定下就影响所有人的归档节奏，`shadow` 期应顺便拉一次真实分布，再决定是否调整到 4 或 5。
2. `FUSION_WEIGHT = 0.5` 与 `MIN_RATE_PER_WEEK = 1.0` 是首轮默认值，`shadow` 期按日记型 / 聊天型 / 双高型三桶分别看归档率再回校。若某一桶归档率显著偏离另两桶，优先调整 `MIN_RATE`（它控制低频来源的放大倍数），而不是 `FUSION_WEIGHT`（它只影响双高用户）。
3. `decaying` 是否需要在聊天侧体现（例如「这条信息比较旧了，要不要确认一下」），还是只做静默标记？
4. 记忆中心「已归档」分组是否要计入 `AGENTS.md` 提到的候选记忆待确认总数口径？

## 12. 实现记录（2026-11-17）

### 交付物

| 文件 | 内容 |
|---|---|
| `db/migration/V1_64__add_memory_lifecycle.sql` | 四个新列、两个索引、`diaries(author_user_id, created_at)` 索引、`lifecycle_updated_at` 回填 |
| `ai/MemoryLifecyclePolicy.java` | `passLifecycle` / `freshness(weeks)` / `evaluate` / `eligibleForDecay` 与全部常量 |
| `ai/MemoryExposureIndex.java` | 速率归一化 + `max + 0.5·min` 融合，纯计算无 IO |
| `ai/MemoryLifecycleScheduler.java` | 每日任务、三档开关、按用户分批、CAS 写入 |
| `ContextPlanner` / `RagMemoryService` | 4 处资格谓词接入 |
| `MemoryOrchestrator` | `touchFormalMemory` 与 `saveFormal` 同值分支的复活回写 |
| 4 个测试类 | 24 个用例，含 H2 真实迁移执行 |

### 与草案的三处偏差

1. **`V1_64` 拆成多条 `ALTER TABLE`**。MySQL 支持单条 `ALTER` 加多列，但 H2（MySQL 模式，测试用）不支持，会报语法错误。索引也改用独立 `CREATE INDEX` 而非 `ALTER TABLE ... ADD KEY`，两端都通。

2. **复活钩子多了一处**。草案只写了 `touchFormalMemory`，实现时发现 `saveFormal` 的「同值分支」也会强化记忆但不经过 `touchFormalMemory`——用户手动编辑、审批通过走的是这条路。漏掉会导致「用户又确认了一遍，归档状态却没复活」，已一并回写。

3. **CAS 显式写回 `updated_at`**。`updated_at` 带 `ON UPDATE CURRENT_TIMESTAMP(3)`，每天刷 `freshness` 会把所有记忆的「更新时间」推成当天，污染记忆中心的展示。写入时显式 `SET updated_at = 旧值` 覆盖该行为，同时保留它作为乐观锁的语义。

### 测试中暴露的一个事实

`max(a,b) ≤ weeks ≤ 1.5·max(a,b)` 这个上限只在**两个来源共用同一速率基准**时严格成立。合并索引会让 `contentStart` 前移（取两者最早），使 span 变长、速率变小、两个 `weeks` 同时略微变大，于是合并值可能比「单来源索引算出的最大值 × 1.5」高出一丝。

这不是实现缺陷，而是对照方式的问题：`singleSourceDominanceIsCappedAtOnePointFive` 因此把日记与会话对齐到同一起点构造，断言才成立。真实场景里不存在「同一份数据分别按两种索引用」的情形，上限本身有效。

### 第二批（同日完成）：接口与网页端

| 文件 | 内容 |
|---|---|
| `UserProfileMemoryController` | `GET /api/memory?lifecycle=archived`、列表与详情新增 4 个生命周期字段、`PATCH /{id}/lifecycle` 恢复接口 |
| `MemoryOrchestrator` | `archived(userId)`、`restoreLifecycle(userId, memoryId)`（恢复即用户确认，重置衰减时钟 + 写 USER_ACTION 证据 + 重建索引） |
| `MemoryExtractionService` | `listUserMemories` 过滤归档行（同时挡住 `buildUserMemoryPrompt` 与抽取上下文的注入路径）、`listArchivedUserMemories`、`restoreLifecycle` |
| `frontend/src/api/diary.ts` | `memoryApi.getArchived()` / `memoryApi.restore()` |
| `frontend/src/stores/consolidation.ts` | `archivedMemories` + 懒加载 + 恢复后刷新 |
| `frontend/src/components/memory/MemoryProfileView.vue` | 「已归档的记忆」折叠区（懒加载 + 单条恢复）、`decaying` 次级说明 |

### 实现中发现的一个关键点

`listUserMemories` 不只服务于记忆中心列表，它还被 `buildUserMemoryPrompt`（日记分析、聊天注入）使用。这意味着**归档过滤必须放在这一层**，否则归档条目会绕过 `ContextPlanner` 直接从提示词路径进入模型上下文。这是本次改动里最容易漏、后果最直接的一处。

### 尚未完成

- `enforce` 灰度未开启，当前 `memory.lifecycle.mode` 缺省为 `off`。
- `frontend-uniapp/` 未改：该仓库目前没有记忆中心页面（只有 `analysis.vue` 提及记忆），等小程序补齐记忆页时按 §6 契约实现。
- `AiTaskMetricsController` 的 `activeMemoryCount` 仍按 `status='active'` 统计，会把归档行计入。这是既有口径，未在本期调整。
