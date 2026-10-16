# T2 PR2：共享刷怪准入配额

关联 [ZER-1542](https://linear.app/zerodotsix/issue/ZER-1542) 与 [PR1 #43](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/43)。PR2 从已验证的 PR1 提交 `9955ecd3b96bdfa9a58f8586a18851319fdc8c24` 继续，版本由 `0.14.2` 增至 `0.14.3`。运行时变更只在独立 PR2 分支；没有改 PR1、Mixin、路径搜索、扫描调度或客户端渲染。

## 数据与适用范围

[四窗口基线](evidence/2026-10-16-four-window-baseline.md) 已包含空载、雨寂、永夜城市、大量召唤物各 300 秒真实 spark，MSPT 均值依次为 0.887 / 5.000 / 3.785 / 7.230 ms。PR1 当前 HEAD 的八项 Actions 成功，默认 434 项 GameTest 全通过，Mixin 14/0。这些是 **PR1 证据，不是 PR2 通过结果或性能提升**。

本实现的“全局”是单个 `ServerLevel` 的共享配额，不是所有维度共用一个额度。计入已跟踪、未移除的 shadow、neon 和非剧情 Yuji，含 Horde 与绮语子召唤物。死亡动画中的实体直到移除仍占额，避免过早回填；这比候选计划中的“存活”口径更保守。管理员手动生成、磁盘载入、移动和传送不被拒绝，受控类别仍计入后续自动准入。超过配置时只暂停新增，不清理已有实体。

| 源码路径 | PR2 覆盖与保留行为 |
| --- | --- |
| `eternalnight/CitySpawns.spawn` | shadow/neon 每个组成员逐一预留；保留 100 tick 频率、16 次位置尝试、局部 cap、地区/光照/碰撞条件 |
| `yuji/city/RainCitySpawns.attempt` | Ambient/Horde/Wave 共用配额；区分 `SPAWNED`、位置失败 `FAILED`、满额 `DEFERRED_CAP`；满额不扣已有波次或 AmbientGroup，仍消耗尝试预算并轮转 |
| `yuji/YujiMonster.summonAllies` | 与雨寂共享同维度配额；保留每父 4 只、32 格、冷却和归属；void 入世 API 后核对真实 UUID 注册，再增加成功数/发粒子 |
| `monster/`、`shadow/`、`neon/` | 目录审计无第二条受控怪物自动创建路径；shadow/neon 由 CitySpawns 统一覆盖。neon 的效果云不是怪物配额对象 |
| `brickrot/` | 地区砌腐墙 boss 保留原地区唯一性；刷怪蛋属于手动入口，不增加另一套 cap |
| 例外 | `YujiGuardian`、`SHIFOTOU`、砌腐墙 boss、BoneSpike/flare 投射物、诱饵/补给箱以及其他无关类型不计入，不改剧情生成 |

## 配置

按仓库现有 SERVER 配置注册 `sound_isolating_eraser-spawn-caps-server.toml`，位于世界的 `serverconfig/`。原 NightConfig、RainCityConfig、YujiConfig 保持不变。

```toml
[spawnCaps]
dimensionCap = 512
chunkCap = 64
entitiesPerLoadedChunk = 2
minimumScaledCap = 128
```

维度有效上限为 `min(dimensionCap, max(minimumScaledCap, loadedChunks * entitiesPerLoadedChunk))`，忽略被禁用的边界。`loadedChunks` 取服务端 chunk cache 的已加载区块数，不相加重叠的玩家范围；也不声称等同于原版“可刷怪区块”数。加载区块增加允许预算增加，但不突破固定 512 上限。所有计数在服务端主线程读取，不为了统计而加载新区块。

`dimensionCap`、`chunkCap`、`entitiesPerLoadedChunk` 的 `-1` 表示分别禁用，`0` 禁止新的受控准入，正数启用；`entitiesPerLoadedChunk=0` 不受 minimum floor 抵消。`minimumScaledCap` 非负，只影响正比例缩放。固定参数最大 1,000,000，乘法用 long 并饱和到 int，负区块计数输入按 0 处理。全部三个限制设为 `-1` 可恢复不限额准入，既有局部 cap 仍生效。

512/64/2/128 是兼容性优先的初始策略，**不是单玩家基线推导出的最优值**：512 高于已采窗口的总实体数，64 的每区块共享上限仍可能改变局部聚集玩法。基线没有逐区块受控人口分布，因而不能保证默认值在所有早期或晚期场景均不触发。正式合入前需要多人/晚期波次回归，以及默认配置与限额压力配置的独立对照。配置降低不会删除现有实体；文件重载由 Forge SERVER config 原机制处理。

## 一致性与开销

`SpawnCaps.tryReserve` 同时检查 `dimensionLive + pending` 和目标物理 16×16 区块的 `chunkLive + chunkPending`。任何一项不足都不占额；每个成员独立预留，`try-with-resources` 覆盖创建/碰撞/异常/入世取消失败。成功必须满足类型、世界、目标区块、未移除以及 `level.getEntity(uuid) == entity`。加入事件可取消，事件本身不算成功。显式 commit 和加入回调按 UUID 幂等去重。

配额索引在有准入需求的 tick 懒建一次，只读遍历 `getAllEntities`，不边遍历边删实体；该 tick 的实际加入、移除、跨区块移动更新索引，区块载入使快照失效。tick 结束且没有预留时清空实体索引；维度卸载/服务端停止清空状态。只保留有界的统计字段，不新增永久 UUID 账本、SavedData 或脏标记。

这仍有 **每个有准入需求 tick 的全实体快照成本**，不是零开销，不可用算法描述冒充性能收益。它避免同一 tick 每个候选重复全量计数；现有 RainCity AABB、每秒安全快照、寻路与渲染分配未调整，留给 PR3/4。`snapshot` 提供总/区块实体、pending、有效 cap、预留允许/拒绝/未兑现次数与扫描次数；允许次数是预留成功而非最终出生数，无逐实体日志。

临时 reservation 必须在同一调用/tick 内关闭；生产入口均以同步 try-with-resources 使用。不为跨 tick 第三方持有 reservation 提供保证，也不把手动加入、传送、加载超过 cap 误报为自动生成越界。

## 验证门禁

新增 `SpawnCapGameTests`，独立 `spawn_caps` batch，使用既有 `GameTestConfigs.sandbox` 隔离并同步恢复配置，默认完整 GameTest 集包含该类。覆盖公式、cap-1/cap/cap+1、联合预留、失败/异常/重复关闭、实际入世/重复 commit、取消 join、移除与移动、负区块、零值/禁用/再启用、剧情例外/手动生成、维度隔离、真实 NBT 保存重载、多个召唤者共享最后一格、雨寂四队列轮转保余额、同 tick 只建一次快照。NBT 重载测试不冒充真实服务端进程重启测试。

```sh
bash ./gradlew --no-daemon build runGameTestServer
python3 tools/verify_mixin_targets.py
```

提交时状态：新代码尚待远端编译和完整门禁，不继承 PR1 的 434/434 或 14/0。独立 PR 的 Actions、实际 GameTest 数和 Mixin 输出按准确 HEAD 附在 PR 验证记录；分支门禁未自动触发时，对同一 HEAD 手动执行原 workflow，不修改或放宽门禁。

性能验收尚未完成：必须在同一隔离 runner、固定世界副本、玩家轨迹、配置和采样时长下比较原 jar 与 PR2 jar，区分限额改变实体数量的策略收益和相同实体数下的计数成本。现有 C 基线碰撞推位且无完整轨迹，不能直接与本轮新 runner 数字相减宣称优化。FPS 及 sparkc 继续单列，软件渲染限帧值不当作实测 FPS。
