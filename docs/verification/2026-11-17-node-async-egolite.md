# 节点改异步：egolite 实机回归

2026-11-17，验证 `ChatAgentLoop` 两个节点从同步 `NodeActionWithConfig` 改为直接实现 `AsyncNodeActionWithConfig` 后，工具审批的暂停与恢复链路是否仍然成立。使用 egolite 操作真实网页，连接本次工作区启动的本地后端，未模拟模型回复、未拦截业务接口返回值。

## 环境与方法

- 后端：`mvn spring-boot:run -Dspring-boot.run.profiles=dev`，加载根目录 `.env`，`http://127.0.0.1:18080`，使用本次改动后的 `target/classes`。
- 前端：Vite，`http://localhost:5173`，`/api` 代理到本地 18080；沿用浏览器已有的本应用登录会话（用户 9201）。
- 浏览器：egolite，任务空间 2，页面 `p1`。
- 观测：页面 DOM 文本与截图；后端 stdout 日志中的中断、续跑、工具执行、递归深度记录。

## 结果

| 场景 | 实测证据 | 结果 |
|---|---|---|
| 写操作触发中断 | 发送“请记住我的一个长期偏好：我特别讨厌番茄”后，页面出现“要记进你的长期记忆 / 讨厌的食物 / 番茄（闻到味道就反胃，很排斥）”与“记下这条 / 先不记”；SSE 未结束 | 通过 |
| 批准后执行并续跑 | 日志 `工具执行前等待用户批准 items=1` → `用户已对工具调用表态，从检查点续跑` → `Agent Loop 执行工具调用: saveMemoryFunction` → `depth=1`；页面出现完整回复并自动把会话改名为“讨厌番茄的偏好” | 通过 |
| 记忆落库 | 记忆中心由 8 条正式记忆变为 9 条，新增“讨厌的食物 / 番茄（闻到味道就反胃，很排斥）” | 通过 |
| 批量审批分页 | 第二次触发时出现“上一页 / 1/2 / 下一页”，处理后变为“2/2 · 已定 1” | 通过 |
| 重复属性提示 | 对已存在的“讨厌的食物”提出修改时，界面显示“已有这项记忆，请重新提出修改建议。”，未重复写入 | 通过 |
| 拒绝后继续 | 点“先不记”后同一条 SSE 继续输出，模型回复“这次没有存上——你在确认那一步点了拒绝……番茄那条我也没存成”；审批组件消失，整轮正常结束 | 通过 |
| 只读工具不中断 | 发送“我最近的日记里都提到些什么？”后直接出现“已检索 5 条记录”，全程未出现审批组件 | 通过 |

关键标识：

- 批准路径 run `c4c17a0a-19df-4aad-97c8-7e0857ebb9e2`，会话 45。
- 拒绝路径与只读路径同属会话 45，分别见 `b3540525…`、`89f7a660…` 的画像异步更新记录。

中断时间线（批准路径）：

```
19:25:04 工具执行前等待用户批准，items=1 model=FLASH
19:25:04 聊天停在工具执行前等待批准，conversationId=45
19:25:17 用户已对工具调用表态，从检查点续跑 decisions=1
19:25:17 恢复工具审批后的续跑：FLASH
19:25:17 Agent Loop 执行工具调用: saveMemoryFunction
19:25:17 Agent Loop 递归 depth=1
```

## 补充：概览类问题耗尽深度的定位与修复

同一轮测试里，问“我最近的日记里都提到些什么？”命中了深度守卫：

```
WARN Agent Loop 递归深度达到上限 depth=6 model=FLASH，终止递归
```

还原那 6 轮：`userStats`+语义搜索 → 语义搜索 → 关键词+`userStats` → 2×语义+`readDiary` → 4×关键词 → 8×`readDiary`（被守卫拦下，一个都没执行）。最终只输出半句“我翻一下你最近这段时间的记录”。

三个成因：

1. `diarySemanticSearchFunction` 连续 5 次 `totalHits=0`，负反馈让模型在语义与关键词之间反复换策略。
2. 工具集缺少概览能力：关键词搜索必须给具体名词，`readDiary` 一次一篇且要 id，没有“按时间倒序列出最近 N 篇”的入口。
3. 两个搜索工具的描述各自写“绝对禁止使用此工具”，一旦某一路返回空，模型就按描述跳到另一路，来回摇摆。

修复：

- 新增 `listRecentDiariesFunction`（`ListRecentDiariesTool`）：按时间倒序列出最近日记摘要，`limit`/`startDate`/`endDate` 全部可选，内部复用 `DiaryService.searchOwnDiarySummaries` 在 keyword 为空时的倒序全量能力；只读，`requiresApproval=false`。
- 两个检索工具的描述由“绝对禁止”改为职责说明，并明确指向新工具处理“没有检索词”的场景。
- 新工具注册在工具列表首位。

验证（同一环境、同一问题、会话 46）：

```
19:35:28 reasoning model emitted 1 tool call(s)
19:35:28 Agent Loop 执行工具调用: listRecentDiariesFunction argsLen=13
19:35:28 Agent Loop 递归 depth=1
```

| 指标 | 修复前 | 修复后 |
|---|---|---|
| 工具调用轮次 | 5 轮（语义/关键词反复试） | 1 轮 |
| 模型调用次数 | 6（触顶终止） | 2 |
| 最终输出 | 半句“我翻一下你最近这段时间的记录” | 覆盖 9 篇日记的结构化回顾，含日期与具体事件 |
| 是否弹审批 | 否 | 否 |

页面同时出现“🔍 已检索 9 条记录”，会话自动命名为“日记内容回顾”。

注：语义检索 `totalHits=0` 本身未处理，属于独立问题（本机无 `redis-cli`，未能直接核对索引内容）。新工具绕开了这条路径，但语义查询质量仍需单独排查。

## 补充二：语义检索 0 命中的根因（短日记 distance 超阈）

写入一篇 542 字的长日记（id=9010）后对照测试，结论与最初猜测不同：

| 查询 | 索引情况 | 检索结果 |
|---|---|---|
| “我最近加班之后总是睡不好” | 新日记 `chunks=2` 已索引 | `totalHits=2 qualityHits=2` 命中 |
| “读完一本书被某个角色的自尊戳到” | 同上 | 语义检索 `totalHits=2 qualityHits=0`，被过滤 |

关键区别在 `qualityHits`，不在 `totalHits`：**向量检索查得到，是相关性分没过阈值**。阈值在 `RagMemoryService.filterQualityHits`：

```java
.filter(h -> h.score() != null && h.score() >= 0.0 && h.score() <= 0.55)   // COSINE distance 硬上限
double threshold = Math.min(0.55, Math.max(topDistance * 3.0, topDistance + 0.10));
```

COISINE 的 `distance` 越小越相似。旧日记那两条 distance 都大于 0.55，被硬上限整批丢弃。

篇幅对照（同一账号 10 篇日记）：

| 日记 | 字数 |
|---|---|
| 9010（本次新写） | 542 |
| 旧日记 9 篇 | 74 / 76 / 85 / 85 / 86 / 92 / 98 / 104 / 119 |

旧日记平均约 91 字，是长文的六分之一。短文本 embedding 的信息量少、语义聚焦弱，与查询的 distance 普遍偏大，于是全部卡在 0.55 之外。**不是索引缺失，也不是工具故障。**

这条链上暴露出两个可改进点（尚未实施）：

1. `filterQualityHits` 在 `validHits` 为空或全部被滤掉时不输出 distance，日志只有 `totalHits=N qualityHits=0`，无法从日志判断是「查不到」还是「分不够」。
2. 索引只用正文，短日记表征天然弱。可考虑把 AI 分析的 summary / 情绪标签一并纳入 embedding，或对短日记用 summary 代替正文。

第三点观察：模型在被语义检索空手返回后，靠 `listRecentDiariesFunction`（9 条）+ `diaryKeywordSearchFunction` + `readDiaryFunction` 在 3 轮内拿到了《山月记》那篇的准确内容。新工具在这里同时起到了兜底作用。

## 补充三：摘要列表的截断与「不做 offset 分页」的取舍

`listRecentDiariesFunction` 首版有两个缺陷：单次最多 20 条（`DiaryService` 里写死 `Page.of(1, 20)`），且 `total` 填的是**返回条数**而非匹配总数。后者是正确性问题 —— 模型看到 `total=20` 会认为区间内只有 20 篇，静默漏掉更早的记录。

改动：

1. `DiaryService.searchOwnDiarySummaries` 增加带 `limit` 的重载（默认 20，上限 100），`total` 改用 `page.getTotal()` 的真实匹配数。
2. `listRecentDiariesFunction` 的 `limit` 上限 20 → 50，参数直接透传给服务层。
3. 截断时由 `DiaryService.summaryNote` 生成明确提示：「匹配 30 篇，本次返回最近 20 篇（按时间倒序）。还有更早的记录未返回，如需完整回顾请缩小时间范围再查。」
4. `DiarySearchResult` 补充字段语义说明，避免后续把 `total` 当成返回条数使用。

**没有加 `offset` 分页**，这是有意的取舍：

| 方案 | 对 Agent 的影响 |
|---|---|
| `offset` 翻页 | 每翻一页要一次完整模型调用，直接吃 `maxDepth`；模型容易丢失游标、重复请求或误以为一页就是全部；翻页期间新增日记会导致错位 |
| 缩小时间范围 | 语义明确、幂等、不会错位；模型对日期切片的直觉远强于字符串偏移 |

所以提示文案写的是「缩小时间范围」而不是「翻到下一页」。

测试：`ListRecentDiariesToolTest`（8 个，覆盖 keyword 为空、limit 默认/上限/下限、日期透传、total 与 note 原样返回）+ `DiarySummaryNoteTest`（4 个，覆盖空结果/截断/完整三种文案）。相关回归 81 个用例全过。

本次改动完成时后端服务已被停止，未做浏览器端实机验证；验证方式是编译 + 单元测试。

（后补：服务重启后的浏览器实测见下一节。）

## 补充四：摘要列表改动的浏览器实测

重启后端加载新代码后，在会话 49 连续三个提问，日志与界面一致：

| 提问 | 工具入参 | 服务层日志 | 界面结果 |
|---|---|---|---|
| “我最近都写了什么？简单列三篇就行。” | `limit=3` | `resultCount=3，matched=10` | 只列 3 篇，未声称是全部 |
| “我一共写了多少篇日记？最早和最晚分别是哪天？” | 无日期、`limit` 默认 | `resultCount=10，matched=10` | 答“一共 10 篇”，给出 10/8 与 11/17 |
| “10 月份我写了哪些日记？” | `startDate=2026-10-01, endDate=2026-10-31` | `resultCount=8，matched=8` | 列出 10/8–10/29 共 8 篇，11 月的两篇被正确排除，并给出“前半段松、评审前夜起变紧”的趋势归纳 |

关键一条是第一行：`matched=10` 而 `resultCount=3`。改动前 `total` 会等于 3，模型无从得知还有 7 篇；现在两者分离，截断事实可被模型读到。

时间范围由模型自行推断并填入（`2026-10-01` 到 `2026-10-31`），不需要用户显式说日期边界，说明「时间区间作为切分手段」这个设计与模型的行为习惯吻合。

未覆盖到的场景：当前账号只有 10 篇日记，三次提问中模型都拿到了完整结果，因此没有观察到「截断提示 → 模型主动缩小时间范围重查」这一跳。该路径的正确性目前由 `DiarySummaryNoteTest` 的文案断言与 `ListRecentDiariesToolTest` 的 total 透传断言保障；要实机验证需要账号内有超过 50 篇日记。

## 副作用与保留状态

- 会话 45“讨厌番茄的偏好”与一条正式记忆“讨厌的食物 / 番茄（闻到味道就反胃，很排斥）”由本次测试产生，属于测试数据，尚未清理。
- 会话 46“日记内容回顾”为该问题修复后的对照，同样属于测试数据。
- 日记 9010（542 字，2026-11-17）与随之生成的 2 个向量分块为语义检索对照实验产生，尚未清理。
- 会话 47“加班后睡不好”、会话 48（《山月记》对照）为本次对照实验产生。
- 会话 49“最近写作内容查询”为摘要列表改动的实机验证，同样属于测试数据。
- 被拒绝的“每天早上七点去跑步”没有写入任何记忆。
- 未修改任何已有记忆，未删除任何数据。
- 后端以后台任务方式运行（加载 `.env` 的 `mvn spring-boot:run`），前端 5173 沿用原进程。
- 截图保存在 `backend/moodcopilot/target/egolite-1117/`：`before-send.png`、`after-send.png`、`approval-dialog.png`、`after-approve.png`、`approval-2.png`、`after-decline.png`、`readonly-no-approval.png`、`recent-diaries-list.png`、`memory.png`、`home.png`。
