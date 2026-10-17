# PR2 刷怪 cap：待实施清单与开工门禁

> 历史计划：本页保留 PR1 当时的候选方案与门禁，不代表 PR2 当前实现状态。PR2 的实际口径、有限默认值、测试与风险见[实施说明](pr2-spawn-caps.md)及[独立 PR53](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/53)。PR1 分支保持不变。

关联 [PR #43](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/43) / [ZER-1542](https://linear.app/zerodotsix/issue/ZER-1542)。本文件只做 PR1 的续办准备，不包含运行时代码。源码核对基准为 `3a213afeb5ef71412560b5a90259f2b620cd1326`；后续 `da45d57` 仅修改文档，未改变下述源码。

## 开工门禁：已有硬数据，默认策略尚未冻结

**已取得 A/B1/B2/C 四个真实 300 秒服务端窗口。** 准确源码 `bb325dc`、同一次独立 runner 运行的 MSPT 均值分别为 0.887 / 5.000 / 3.785 / 7.230 ms，C 实测16父/64归属子。报告、快照、机器可读数据及限制见[四窗口基线](evidence/2026-10-16-four-window-baseline.md)。不再以“无 spark”阻止设计工作，但单玩家早期城市不能决定有限 cap 默认值；C 碰撞推位且无完整位置轨迹，也不满足严格固定站位前后验收。客户端 FPS / sparkc 仍独立待采。PR43 继续只提交证据，不混入运行时实现。

以下 API、配置和统计范围是源码约束下的候选实现方案，不是已冻结的玩法变更，也不是已证明的性能收益。实际有限 cap 数值须由基线实体数量、生成吞吐、MSPT 和玩法回归共同确定。先完成 [采样手册](spark-runbook.md)，再确定默认值、范围与 PR2 验收。

## 必须保留的现有行为

| 入口 | 已核实的行为 | PR2 不得破坏 |
| --- | --- | --- |
| [CitySpawns.java](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/3a213afeb5ef71412560b5a90259f2b620cd1326/src/main/java/com/zeropointsix/eraser/eternalnight/CitySpawns.java) `tick` / `spawn` | 每 100 tick、每有效玩家最多 16 次位置尝试；已有 64 格范围的 shadow/neon 独立计数；私有 `spawn` 用 `addFreshEntity` 返回值判断成功 | 保留局部 cap、光照、碰撞、距离和地区条件。每个组成员单独预留，不能只在循环前检查一次 |
| `CitySpawns` 砌腐墙 boss 分支 | 使用 `CitySavedData.hasBoss/reserveBoss`；预留地区失败后 `discard` | 不用通用 cap 替代地区 boss 唯一性。本候选范围排除该剧情 boss |
| [NightConfig.java](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/3a213afeb5ef71412560b5a90259f2b620cd1326/src/main/java/com/zeropointsix/eraser/eternalnight/NightConfig.java) | `nearbyShadowCap=10`、`nearbyNeonCap=12` | 现有默认值不变，不把已存在的局部 cap 作为本轮新增成果 |
| [RainCityConfig.java](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/3a213afeb5ef71412560b5a90259f2b620cd1326/src/main/java/com/zeropointsix/eraser/yuji/city/RainCityConfig.java) / [RainCityRules.java](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/3a213afeb5ef71412560b5a90259f2b620cd1326/src/main/java/com/zeropointsix/eraser/yuji/city/RainCityRules.java) | `ambientCap=300`、`spawnDistance=96`、`spawnBudgetPerTick=4`、`hordeMultiplier=1`；实际 Ambient 上限还随天数和昼夜计算 | 300 不是所有场景的即时总实体 cap；生成速率预算与存量准入 cap 分开处理 |
| [RainCitySpawns.java](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/3a213afeb5ef71412560b5a90259f2b620cd1326/src/main/java/com/zeropointsix/eraser/yuji/city/RainCitySpawns.java) `tick` / `attempt` | 玩家和四队列轮转；Horde/WaveChennu/WaveGreed 仅在 `attempt` 成功后扣余额；AmbientGroup 当前无论位置尝试成败都扣一 | `DEFERRED_CAP` 必须区别于原有位置失败。cap 拒绝时保留波次和 AmbientGroup 余额，仍推进轮转与尝试预算，防止丢波次和饥饿 |
| [YujiMonster.java](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/3a213afeb5ef71412560b5a90259f2b620cd1326/src/main/java/com/zeropointsix/eraser/yuji/YujiMonster.java) `livingSummons` / `summonAllies` | 32 格内同 summoner 的存活召唤物局部计数；`SUMMON_CAP=4`、`SUMMON_INTERVAL=200`；施法前检查未通过时 20 tick 重试，已开始施法后沿用 200 tick 冷却；召唤可发生于雨寂以外 | 新配额仅为额外准入条件；保留局部上限、冷却、归属、持久化和成功粒子语义；BoneSpike 投射物不纳入怪物 cap |
| [RainCityEvents.java](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/3a213afeb5ef71412560b5a90259f2b620cd1326/src/main/java/com/zeropointsix/eraser/yuji/city/RainCityEvents.java) `spawning` | 敲钟时取消非磁盘载入的 Yuji 入世；继承 Horde 父召唤者标记；磁盘加载豁免 | `addFreshEntityWithPassengers` 是 void，调用后 `made++` 不能证明入世成功。reservation 必须在真实成功后兑现，事件取消不得泄漏额度或产生假成功 |

## 文件与 API 清单

下面的相对 Java 路径均以 `src/main/java/com/zeropointsix/eraser/` 为根。新增路径和 API 名称是候选，现有类名和方法已核实。

| 文件 | 具体改动 | 验证点 |
| --- | --- | --- |
| 新增 `spawn/SpawnCaps.java` | 单一服务端配额服务；候选 `tryReserve(ServerLevel, EntityType<?>, BlockPos)` 返回可关闭 reservation，`Reservation.commit(Entity)`、`close()`；提供只读诊断快照 | 不为每个玩家或刷怪入口创建独立余额；同一 `ServerLevel` 内所有入口共享 |
| 新增 `config/SpawnCapConfig.java`；修改 `ModMain.java` | 沿现有 `ModConfig.Type.SERVER` 注册方式接入共享配置，不复制 RainCity/Qiyu 两套互相绕过的额度 | 按世界 SERVER config 生效；保留既有配置文件和默认值 |
| `eternalnight/CitySpawns.java` | shadow/neon 的私有 `spawn` 路径逐个预留、入世后提交；失败自动释放 | 现有局部 cap 与新维度/区块 cap 同时成立 |
| `yuji/city/RainCitySpawns.java` | 将私有 boolean `attempt` 改为 `SPAWNED / FAILED / DEFERRED_CAP` 三态；调用方按结果更新队列 | cap 延迟不扣余额；位置失败继续维持原队列各自语义，不顺手重构刷怪调度 |
| `yuji/YujiMonster.java` | `summonAllies` 逐个预留，核实真实入世再提交；失败释放、不增加成功数、不发成功粒子 | 敲钟取消、归属标记与 Horde 继承完整；局部 4 只限制继续有效 |
| 新增 `spawn/SpawnCapEvents.java` 或现有事件注册位置 | 接入计数生命周期和诊断；无需新增 Mixin | Forge 1.20.1 对应生命周期 API 必须通过实际编译和 GameTest 核验；不能只凭事件名称假定顺序 |
| 新增 `gametest/SpawnCapGameTests.java` | 配额、原子预留、失败回滚、生命周期、配置测试 | 参考现有 `GameTestConfigs` 的配置设置/恢复方式，并验证与后续测试串行运行无污染 |

不在本 PR2 扩大到 tick 扫描缓存、路径搜索、渲染、账本 TTL 或 SavedData 保存策略；它们分别留给后续已测热点对应的 PR。

## 候选计数口径

“全局”明确为 **单个 ServerLevel 内的已加载、存活、符合类别口径的实体总数**，不是所有维度共享一个额度。各维度使用同一服务实现但独立状态；RainCity 生成和 Qiyu 召唤在同一维度内消费同一余额。

候选类别为 shadow、neon、非剧情 Yuji，包含 Horde 及其召唤物；排除 `YujiGuardian`、`SHIFOTOU`、地区砌腐墙 boss、投射物和展示实体。候选类型白名单及剧情例外需要采样和玩法验收后冻结，不能未经验证扩大为所有 `Entity` 或所有 `Monster`。

区块 cap 按实体生成脚点 `floorDiv(x,16)` / `floorDiv(z,16)` 所在区块计算，负坐标同样成立。它与现有 64 格霓虹地区、256 格嗔怒地区不同，不复用这些区域键来冒充区块。

只限制受控自动生成入口，不取消磁盘实体加载，不删除存量、不强制加载区块。移动、传送、存档重载、管理员命令可能让已有数量超过 cap，此时仅暂停新的自动准入。这是 **自动生成准入上限**，不是无例外的实体存量硬上限。

## Cap 算法与一致性

对启用的上限，单个候选必须同时满足：

```text
dimensionLive + dimensionPending < dimensionCap
chunkLive[targetChunk] + chunkPending[targetChunk] < chunkCap
```

1. 在服务端主线程完成类别判断和两个额度的联合预留。任意一项不足，两个额度都不增加；不得先占维度额度再遗留失败的区块预留。
2. 已确定生成位置后，在昂贵创建、初始化和入世前尽早预留。不能跳过维护玩法状态所需的步骤，也不能因 cap 拒绝改变已有随机数、冷却、波次语义而不测试。
3. 所有玩家、队列、组成员、同 tick 召唤共用 `live + pending`。预算循环逐个预留，不使用循环前一次性快照保证硬准入。
4. 用确定性的 `try/finally` 或 `AutoCloseable` 生命周期释放 reservation。创建返回 null、碰撞/生成条件失败、事件取消、入世返回 false 或抛出异常均释放。
5. 真实入世成功后，按 UUID 幂等地把 pending 转成 live；join 回调和显式 commit 不得对同一实体各加一次。可取消的 join 回调及其中对 Horde 标记的改写，不能直接视为最终成功。
6. 记录类别和当前区块的实体索引；死亡、discard、区块卸载、跨区块移动、跨维度、世界卸载和服务器停止均需更新或清理。离开旧维度与进入新维度要分别结算，计数不得为负。
7. 临时 reservation 不落盘，不用静态强引用永久保留卸载世界。重建计数时允许合法存档载入；初始化未完成时不得把未知数量当零放行。
8. 不在每个生成候选上重新全实体扫描。初始化/校正必须有界且不修改正遍历的 `getAllEntities` 活视图，保留 RainCity 已有安全快照维护。具体计数维护开销也必须纳入前后 spark 比较。

## Config 与默认值策略

候选字段为 `spawnCaps.dimensionCap`、`spawnCaps.chunkCap`，通过一份新的 SERVER 配置注册。兼容阶段默认 `-1` 表示该项不限制，`0` 表示禁止该范围新增准入，正数表示上限；不再另加含义重复的 enable 开关。合法最大值和正式启用的有限默认值，在四窗口基线后确定。

默认关闭只用于保持兼容，不得宣称它已经降低实体数量或改善 MSPT。现有 NightConfig、RainCityConfig、YujiConfig 数值不改。降低配置只阻止新生成，不清怪；禁用、重载、再启用与世界重启都要验证。

诊断快照至少返回 live、pending、允许、cap 拒绝、入世失败计数及统计维度/区块。按场景窗口读取，避免每次拒绝都写日志，避免诊断自身成为新热点。

## 回归与验收

以下测试类路径已核对，现有具体测试函数未逐一审查；实施时先读对应测试，不按类名假定已覆盖。

- `SpawnCapGameTests`：cap-1/cap/cap+1、-1/0/正值；同 tick 多玩家、多入口和批量请求；维度与区块额度共同成功或共同失败。
- 创建/碰撞/入世失败、事件取消与异常释放；重复 join/commit、死亡/discard 不重计、不负数。
- 区块边界与负坐标、传送、跨维度、卸载重载、世界关闭、服务器重启；超限存量可以合法加载，不丢实体。
- `RainCityGameTests`：cap 延迟不扣波次、不清 AmbientGroup；轮转继续、释放额度后恢复；敲钟取消不产生假成功。
- `YujiGameTests`：多个召唤者共享额度，仍保留各召唤者局部 4 只上限、冷却、归属保存和 Horde 继承。
- `EternalNightGameTests`、`ShadowTanglerGameTests`、`NeonTumorGameTests`：原有局部 cap、光照/地区、大型霓虹约束和剧情 boss 唯一性不变。
- `RainCityEntitySweepGameTests`、`PerformanceGameTests`：计数维护不破坏快照安全；测试耗时不替代正式性能采样。
- 配置测试结束时恢复原值；新增测试后串行执行相关旧测试，确认没有共享配置污染。

PR2 独立 bump 版本，提交前运行 `python3 tools/verify_mixin_targets.py`、默认完整 `build runGameTestServer` 和对应 CI。不得用子集测试替代全套；若确需触及 Mixin，仍须 MCP+SRG 双名与 `remap=false`，本候选方案不需要 Mixin。

性能验收使用相同机器、世界副本、玩家身份、视距和配置，分别报告“实体数量减少的策略收益”和“同等实体数量下的执行效率”。必须附真实 viewer URL、300 秒窗口与原始指标，不能以 cap 算法或单元测试通过推定性能改善。

开工与验收分开：四窗口服务端硬数据已经存在，下一项是冻结统计/玩法范围与默认策略，并补固定快照下的轨迹和重复基线；PR2 合入验收才要求优化前后对照。当前没有足够数据为有限 cap 默认值背书，PR2-4 运行时代码尚未开始；PR3/4 仍按 README 的先后依赖推进，不要求在 PR2 实施前取得不可能已有的优化后数据。既有 Gravity 真实客户端门禁连续失败须独立定位，不能以默认434个GameTest已通过冒充CI全绿。
