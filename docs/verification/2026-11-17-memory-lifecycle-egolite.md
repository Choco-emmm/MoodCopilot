# 记忆生命周期「已归档」视图：egolite 本地实测

2026-11-17，使用 egolite 操作真实网页，连接本次工作区启动的本地后端，验证记忆生命周期第一批（归档 / 恢复）的真实链路。**没有部署到生产环境**，也没有模拟接口返回或直接构造前端状态。

## 环境与方法

- 后端：`mvn spring-boot:run -Dspring-boot.run.profiles=dev`，`http://127.0.0.1:18080`，使用本次工作区 Java 代码，Flyway 执行到 `V1_64`。
- 前端：`vite preview`，`http://localhost:5173`，使用构建后的 `frontend/dist`（含本次改动）。
- 浏览器：egolite，任务空间 5，登录态为本地用户 `9201`（林小满）。
- 数据库：本地 `moodcopilot-mysql-1`。归档状态不具备任何接口入口（只能由调度器推进），因此用一条 SQL 把 `user_profile_memory.id=9314`（`habit` / `diary_inferred` / `confidence=0.6`，符合归档条件）置为 `archived` 来模拟调度器产物，测试结束后已还原。

## 结果

| 场景 | 实测证据 | 结果 |
|---|---|---|
| 已归档区默认收起且不发请求 | `.archived-section` 存在、`aria-expanded=false`、`#archived-list` 未渲染（`v-if` 未创建 DOM，加载逻辑不可能执行） | 通过 |
| 展开懒加载 | 点击后 `aria-expanded=true`，归档列表出现 1 条：`打算独自去河边散步，不带任何东西` · `情绪调节方式` · `长期未再提及` · 时间 · 「恢复」按钮 | 通过 |
| 单条恢复 | 点击「恢复」后折叠区变为 `0 条`、列表渲染「暂无已归档的记忆」、顶部从 `12 条正式记忆` 变为 `13 条正式记忆`、出现 `已恢复到记忆中` 提示 | 通过 |
| 数据库落库 | `lifecycle_state=active`、`freshness=1.0000`、`lifecycle_reason=RESTORED_BY_USER`、`last_evidence_at` 与 `lifecycle_updated_at` 同为恢复时刻（衰减时钟已重启）、新增 1 条 `USER_ACTION` 证据 | 通过 |
| 重复恢复（幂等） | 同一 id 再次 `PATCH {state:active}` 返回 200，`USER_ACTION` 证据仍为 1 条，未重复写入 | 通过 |
| 非法状态 | `{state:"archived"}` 与 `{}` 均返回 `400 只支持恢复为 active` | 通过 |
| 越权/不存在 | 不存在的 id 返回 `400 记忆记录不存在或无权操作`，不回显记录是否存在 | 通过 |
| 默认列表契约 | `GET /api/memory` 返回 13 条，全部带 `lifecycleState`/`freshness`，不含任何 `archived` | 通过 |
| 归档查询契约 | `GET /api/memory?lifecycle=archived` 返回 200、0 条（恢复后无归档行） | 通过 |

## 发现的问题（已修复）

1. **`V1_64` 重复创建已有索引，会让发布直接失败。** 首次启动后端时 Flyway 报 `Duplicate key name 'idx_diaries_author_created'`：`V1__init_schema.sql` 早已建立该索引。由于 MySQL 的 DDL 不能回滚，前四条 `ALTER` 已生效而迁移被标记失败（`flyway_schema_history.success=0`），后续启动全部被拒。已删除该 `CREATE INDEX`、清理本地半迁移状态后重新应用成功。H2 迁移测试没有覆盖到这个场景，因为测试夹具的 `diaries` 表不含该索引。

2. **本地 dev 环境 `127.0.0.1` 无法访问。** egolite 打开 `http://127.0.0.1:4173/` 返回 `net::ERR_HTTP_RESPONSE_CODE_FAILURE`，同一时刻 curl 该地址返回 200；改用 `http://localhost:4173/` 与 `http://localhost:5173/` 均正常。属于本地浏览器/代理侧问题，未改动业务代码或 CORS 配置。

3. **既有时间不一致（非本次引入）**：`user_memory_evidence.created_at` 由数据库 `CURRENT_TIMESTAMP` 生成，容器时区为 UTC，与业务时区差 8 小时；同一行的 `lifecycle_updated_at` 是应用按 `Asia/Shanghai` 写入的。所以「依据时间」与「生命周期时间」会出现 8 小时偏差。这是既有列行为，本次未调整。

## 验证范围与保留状态

- 只覆盖归档与恢复；调度器本身的阈值判定由 `MemoryLifecyclePolicyTest` / `MemoryExposureIndexTest` 等单元测试覆盖，`memory.lifecycle.mode` 仍为缺省的 `off`，本轮没有让调度器真实运行。
- 测试数据已还原：删除本次新增的 `USER_ACTION` 证据行，把 `last_evidence_at` 恢复为 `2026-11-17 19:38:17.501`，清除 `lifecycle_reason` / `lifecycle_updated_at`，`lifecycle_state` 回到 `active`（即其原始状态）。
- 截图保存在 `backend/moodcopilot/target/egolite-memory-lifecycle-20261117/`：`archived-expanded.png`、`after-restore.png`；脚本 `round-01.mjs` … `round-11-finish.mjs`（`target/` 被 git 忽略，构建清理会删除）。
- 本地后端与前端预览仍在运行，便于继续查看。
