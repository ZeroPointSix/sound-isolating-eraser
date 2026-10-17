# 热点候选与源码证据

基线固定为 `e1703317583858b73fb1cd29c3db3f0a177c068b`。本页的“频率”来自控制流，不是采样计数；“候选”不表示已证实的主要瓶颈。源码路径均相对于 `src/main/java/com/zeropointsix/eraser/`。

## 本轮实测调用路径

2026-10-16 的四窗口使用未改变上述运行时逻辑的 `bb325dc`。完整环境、限制与实体数见[诊断基线](evidence/2026-10-16-four-window-baseline.md)。以下数值取对应 viewer 的 `?raw=1&full=true` 数据，对 `Server thread` 中同类/方法的节点选择**最大的单条调用路径**并累计该节点的 `times`。它是包含子调用的采样墙钟毫秒，不是方法自身 CPU 耗时，也不是该方法在所有路径的总和；不得将祖先与后代相加。采样粒度 4ms，线程约300秒包含等待，不能把占总墙钟的比例当CPU占用或MSPT降幅。

| 场景 | 已观测路径及最大单路径包含时间 | 可支持与不能支持的结论 |
| --- | --- | --- |
| [B1 雨寂](https://spark.lucko.me/BQ1iJ9qI3b) | `RecliningBuddha.m_8119_` 7188ms；`RainCityEvents.tick` 292ms；`RainCitySpawns.tick` 216ms；`YujiMonster.nearbyPlayers` 156ms | 确认实体 AI 和刷怪/查询路径被采到；tick override 包含原版父类逻辑，7188ms 不能全部归因于自定义代码。不能认定 Ambient 重复计数就是最大热点 |
| [B2 永夜](https://spark.lucko.me/Scz8kd12Ic) | `ShadowTanglerEntity.m_8119_` 6664ms；`LightAversionNodeEvaluator.m_6065_` 172ms；`refreshLightTier` 144ms；`FleeLightGoal.m_8037_` 64ms | 确认缠影 AI/光照路径存在。单玩家早期城市只有8至11个区域实体，不代表多人/高密度城市瓶颈；不应重做已存在的分片 |
| [C 召唤物](https://spark.lucko.me/aCKjSDJu2S) | `YujiMonster.pursue` 1940ms；`m_8024_` 940ms；`resolveAction` 552ms；`YujiAfflictions.mobTick` 408ms；`strike` 352ms | 16父/64子下追逐与战斗链值得复测；这些节点可能相互嵌套，不能相加。C 站位存在碰撞偏移，尚非严格固定站位的前后验收 |

C 的计数探针也被采到：`EntityDataAccessor.m_6184_` 单条包含时间 528ms，说明 NBT 计数并非零开销。优先在下一次固定快照重复运行中量化探针与场景方差，再决定 cap 与扫描缓存的优先级。爬行动画数组、`PoseStack`、SavedData 序列化和账本增长仍只是下文静态证据，服务端这四份 profile 没有补上客户端分配或长期存档测量。

原始数据结构以 [spark 官方 Raw data 文档](https://spark.lucko.me/docs/misc/Raw-spark-data) 为准；仓库记录筛选口径，原始二进制和完整 metadata 在 Actions 制品中保留，不用截图读数替代原数据。

## 刷怪与实体查询

| 位置 / 方法 | 已核实触发频率与工作量 | 采样重点和后续风险 |
| --- | --- | --- |
| [`eternalnight/CitySpawns.java:39`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/eternalnight/CitySpawns.java#L39)，`tick` | 内部 `gameTime % 100` 门控。每名有效玩家一次 `Mob` 的 `inflate(64)` 查询；每人至多 16 次位置尝试。已有 shadow/neon 局部 cap 和成功生成后的增量计数 | 大量玩家区域重叠；不能描述为每 tick 不限量刷怪。全局与每区块硬 cap 尚需明确计数范围 |
| [`CitySpawns.java:75`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/eternalnight/CitySpawns.java#L75)，Neon 分支 | 仅在局部未满、商业区、满足光照/墙条件时，尝试循环内重复查询 64x全高度x64 的大型霓虹 | 同区域可以复用计数，但新生成、死亡、移动和同 tick 的后续尝试必须同步更新 |
| [`yuji/city/RainCitySpawns.java:35`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/yuji/city/RainCitySpawns.java#L35)，`tick` | 由雨寂 END tick 调用；已有 `SPAWN_BUDGET` 和玩家/队列轮转。每 20 tick 才复制全实体列表做成长/远距清理 | 全实体快照是每秒维护，不能误写每 tick。实时查询应与世界生成、AI 成本分开采样 |
| [`RainCitySpawns.java:65`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/yuji/city/RainCitySpawns.java#L65)，Ambient 队列 | 每个命中 Ambient 的预算步查询玩家周围 `DISTANCE` AABB；同一 tick 可多次查询。循环内还创建队列字符串数组。非 Ambient 波次不经过此 Ambient cap 分支 | 次数预算不等于存活总量上限。缓存不能使同 tick 连续生成超限，也不能饿死波次队列 |
| [`RainCitySpawns.java:100`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/yuji/city/RainCitySpawns.java#L100)，`regionalChennu` | 创建 Ambient 组、day>0 且随机分支命中时，查 256x384x256 区域的存活嗔怒 | 不是无条件每 tick；可按区域复用，但必须保留类型与存活过滤 |
| [`yuji/YujiMonster.java:202`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/yuji/YujiMonster.java#L202)，`customServerAiStep` | 石佛头目标为空/失效/超距时查 64 格玩家，未找到再查铁傀儡；其他分支查 24/32 格玩家。分支中无独立目标重搜冷却，持续无目标时每次 AI step 可重走 | 重点测“很多无目标实体”和“刚出现目标”。不要将 AI step 次数凭空写成实测 20 次/秒；缓存需立即失效且有可接受的捕获延迟 |
| [`YujiMonster.java:380`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/yuji/YujiMonster.java#L380)，`livingSummons/canSummon/summonAllies` | 已有 `SUMMON_INTERVAL=200`、失败后 20 tick 重试、`SUMMON_CAP=4`。计数只覆盖 32 格 AABB 内同 summoner 的存活 YujiMonster | 这是局部上限，离开范围的活体不在计数中；全局 cap 测试必须覆盖离区、跨区块、死亡/卸载和重复加载 |

## 路径、光照和方块搜索

- [`shadow/FleeLightGoal.java:16`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/shadow/FleeLightGoal.java#L16)：已有预计算偏移、游标续扫、目标缓存，以及每批最多 1024 位置、64 寻路候选。缓存目标检查和搜索结束后的间隔也已存在。应测多实体汇总成本，而非重做已存在的分片。
- [`neon/SeekLightGoal.java:20`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/neon/SeekLightGoal.java#L20)：`nextCheck=now+200`；单次用 `BlockPos.betweenClosed` 遍历 17x5x17 候选长方体，共 `17*5*17=1445` 个位置，再按半径和已加载区块过滤，并可能多次 `createPath`。候选风险是大量实体同步批次峰值，不是无间隔每 tick 全扫描。
- [`YujiMonster.java:183`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/yuji/YujiMonster.java#L183)：追逐/逃离 `moveTo` 已按 `tickCount % 10` 限频。嗔怒破坏邻域也有 10 tick 门控；不能把所有寻路与方块检查归类为每 tick 重算。
- `monster/LightTier.of` 是读取已有亮度，不是重算光照；Neon 的服务端 `aiStep`、Shadow 的存活服务端 `tick` 会读取亮度。Brickrot 的 `findQuarry` 是状态门控的 `inflate(40)` 查询；移动时 `crush` 按身体部件查询，冲锋期间逐 AI step 检查前方方块。具体调用节奏、源码行号、已有清理和渲染器补充见[范围审计](evidence/2026-10-17-pr1-scope-audit.md)。这些仍是静态候选，不能为了统一间隔而延迟战斗伤害，也不声称已穷尽所有调用路径。

## 客户端分配

| 位置 | 已证实的分配来源 | 验证要求 |
| --- | --- | --- |
| [`client/SuxiaModel.java:141`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/client/SuxiaModel.java#L141)，`setupAnim` 与 IK helper | `crawl>0` 分支的 `solveLimb`、矩阵/向量计算创建多组 `float[]`，辅助方法在 250 行之后 | 单独客户端 allocation profile；测试站立、爬行、混合姿态和多个实体，工作数组不得跨实体错误共享 |
| [`client/WaterGhostModel.java:157`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/client/WaterGhostModel.java#L157)，`setupAnim` | 当前版本没有上述爬行短数组，主要为标量动画及部件重置遍历 | 不把用户给出的排查入口当成缺陷已定位；若要改，先提供客户端采样 |
| [`client/GravityClient.java:48`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/client/GravityClient.java#L48)，render 阶段回调 | AFTER_PARTICLES 新建 `PoseStack` 复制 pose/normal，AFTER_LEVEL 消费后清空 | 阶段间复制有正确性用途。不能直接替换成后一个事件的栈；需验证轮廓、矩阵归位、资源重载与每帧复用边界 |

模型初始化期数组不等于逐帧分配。远距剔除只应影响渲染；巨型实体、伸出包围盒的特效、透视轮廓和第三方兼容需独立测试，不能假定原版没有任何距离/视锥剔除。

补充发现：`NeonTumorRenderer.preRender` 的三元素 `String[]`、`GravityWorldRenderer.render` 的 shader color 克隆、圆柱线段向量，以及 `YujiRenderer.preRender` 的骨骼递归/逐层名称构造均位于渲染路径。已有 fieldBounds 视锥剔除、静态纹理及发光层条件不能当成本轮新增。见[逐项证据与边界](evidence/2026-10-17-pr1-scope-audit.md#渲染路径补充)；实际分配率、JIT 消除情况及 FPS 收益仍未测量。

## SavedData 与账本

| 位置 | 源码事实 | 不能采取的捷径 |
| --- | --- | --- |
| [`RainCityEvents.java:68`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/yuji/city/RainCityEvents.java#L68) | 活跃城市的 `cityTime` 变化时 `setDirty`，可随推进每 tick 发生；波次预算分支也标脏 | 标脏不是每 tick 写盘。先区分标志更新、序列化和实际 I/O；不得通过少存进度引入重启回退 |
| [`RainCityData.java`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/yuji/city/RainCityData.java) | UUID 到玩家 CompoundTag 的映射，保存全部条目；类内未见容量/保留期清理 | 玩家离开不代表进度可删除，需要明确长期存档语义 |
| [`rednails/RedNailsState.java`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/rednails/RedNailsState.java) 与 [`suxia/SuxiaSummons.java`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/suxia/SuxiaSummons.java) | 物品 UUID 账本类内未见淘汰；`owned`/`active` 扫描历史条目，save 遍历账本 | 掉落物、容器、离线玩家和卸载实体仍可能有效；简单 TTL 会丢归属或绕过死亡冷却。应先测基数，再设计索引和可证明安全的生命周期回收 |
| [`eternalnight/CitySavedData.java`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/eternalnight/CitySavedData.java) | 出口已有到期删除，boss 有死亡释放调用；待关闭门覆盖卸载区块 | 不能笼统写成所有 SavedData 永久增长，不能任意过期丢弃待关闭操作 |

账本增长目前是生命周期与复杂度风险，尚非已测量的泄漏。后续报告至少记录活跃条目/总条目、序列化大小、保存耗时和重启后行为。
