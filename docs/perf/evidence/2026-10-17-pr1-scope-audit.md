# PR1 恢复核验与范围补充

2026-10-17 UTC；[ZER-1542](https://linear.app/zerodotsix/issue/ZER-1542)、[PR43](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/43)。本次仅补充报告，不改刷怪 cap、AI 缓存、渲染或 Mixin。所有下述代码链接固定到恢复时 PR1 的 `9955ecd3b96bdfa9a58f8586a18851319fdc8c24`；运行时与既有四窗口来源 `bb325dc` 一致。调用节奏来自控制流，不是新的性能测量。

## 恢复结果

- 同一分支 `docs/zer-1542-t2-pr1-perf-baseline`；PR43 仍 open、draft、未合并，基于 main `e1703317583858b73fb1cd29c3db3f0a177c068b`。没有另建文档 PR，也没有 force push。
- `mod_version=0.14.2` 已在 PR1 从 main 的 `0.14.1` 提升；同一 PR 的文档续补不重复 bump。
- [四窗口原始报告](2026-10-16-four-window-baseline.md)和[完整精度 JSON](2026-10-16-four-window-metrics.json)可复用。A 空载、B1 雨寂、B2 永夜城市、C 真实召唤物均为约 300 秒、6000 ticks；城市和雨寂不是二选一。
- [采集 run38966307507](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966307507) 已成功；[制品12077285827](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966307507/artifacts/12077285827) 含真实 profile、实体 CSV、世界快照及启动/停止日志。SHA-256 为 `e0a5505f00f9fd0aeccf85dedf7c4c9990a0a201c2f33bf14bb6755ad718de88`，不是截图估数。
- 成功路线是独立 GitHub runner 正常解析原 Gradle 依赖，构建 reobf jar 后安装官方 Forge 专服。旧环境 TLS/foojay 失败不代表这条路线仍失败；未关闭证书校验、修改依赖版本或使用高负载测试机。
- 这是隔离专服的真实诊断基线，不是线上生产服测量，也不是优化后报告。FPS 未测；20 是软件渲染限帧。C 原采样站位被碰撞推移，不能作为严格固定站位前后比较。保留这些限制，不补造数值。

## 服务端补充

源码路径均相对于 `src/main/java/com/zeropointsix/eraser/`。下表补齐原热点清单中仅概括提及的部分；原有 Yuji、雨寂、CitySpawns、FleeLight 和 SeekLight 证据仍见 [hotspots.md](../hotspots.md)。

| 文件 / 方法 | 真实调用节奏与工作量 | 候选成本与正确性边界 |
| --- | --- | --- |
| [monster/LightTier.java:13](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/monster/LightTier.java#L13)，`of` | 单次调用 `getMaxLocalRawBrightness` 并按阈值分类；没有实体扫描、寻路或光照传播重算 | 区分高频亮度读取与昂贵光照重建；不能仅凭方法名声称每 tick 重算光照 |
| [neon/NeonTumorEntity.java:248](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/neon/NeonTumorEntity.java#L248)，`aiStep` / `tickSteam` | 每次服务端 `aiStep` 读取 LightTier 并调用 `tickSteam`。仅大型且 BRIGHT 时查询 `inflate(5)` 内玩家，再按距离平方 25 过滤，创建 `HashSet` 并保留仍在区域内的 UUID | 小范围但可高频，非大范围全世界扫描。连续暴露 60 tick 生效、短暂离开即重置的语义要求即时退出检测，不能直接改为长间隔轮询 |
| [shadow/ShadowTanglerEntity.java:175](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/shadow/ShadowTanglerEntity.java#L175)，`refreshLightTier` / `tick` / `hurt` / `die` | 存活服务端每 tick 刷新亮度档位；hurt 和 die 另外刷新，单 tick 可多次进入 | 多实体和受击路径可能重复读取；亮度影响逃离、伤害和死亡掉落，任何缓存必须保留这些即时语义。已有 B2 采样包含该路径，不能把包含时间当自身 CPU |
| [brickrot/BrickrotWallEntity.java:188](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/brickrot/BrickrotWallEntity.java#L188)，`customServerAiStep` / `track` / `findQuarry` | `findQuarry` 在第 440 行查询 `inflate(40)`，对候选做类型、亮度和视线判断。SCAN 在 30/50 action ticks 后查找并转状态；IDLE 每 20 action ticks 查；TRACK 每 10 entity ticks 查；WINDUP 达 10 action ticks 时重查 | 状态门控已存在，不是无条件每 tick 大范围扫描。密集候选时实体遍历及射线成本需专门 Brickrot 场景采样，当前四窗口不能证明其占比 |
| [BrickrotWallEntity.java:328](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/brickrot/BrickrotWallEntity.java#L328)，`charge` | CHARGE 状态每次服务端 AI step 遍历前方 swept AABB 内方块；已先检查区块，随后取碰撞形状、`toAabbs().stream()` 和破坏事件 | 方块密度、碰撞形状和事件回调共同决定成本；减少检查不能允许穿墙或绕过 mobGriefing / 销毁事件 |
| [BrickrotWallEntity.java:469](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/brickrot/BrickrotWallEntity.java#L469)，`victims` / `crush` | 存活且本 tick 水平移动后进入 crush，非潜地/硬直时逐身体部件 `getEntitiesOfClass`；每次创建命中集合。BITE 只在 action tick6 伤害，SWEEP 在 12..15，不能一概算每 tick 攻击 | 多部件重复邻域查询和命中射线是候选；需要保留同次攻击去重、20 tick 碾压冷却和瞬时碰撞，不能以 TTL 缓存过期碰撞结果 |
| [brickrot/BrickrotTrail.java:18](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/brickrot/BrickrotTrail.java#L18)，`record` / `sample` | 实体 tick 记录/采样身体轨迹；队列最多 192 点。Brickrot 的 `crushedUntil` 每 100 ticks 清理到期值 | 这两者不是已证实的永久增长账本；轨迹重复线性采样仍可测量，但不能误删已有有界/过期保护 |

## 渲染路径补充

频率以“每次渲染回调”描述：同一帧可能存在重绘/额外通道，未运行客户端 allocation profiler，不能把源码中的 `new` 直接换算为每秒堆分配或断言 JIT 无法消除。

| 文件 / 方法 | 已确认的路径与已有保护 | PR4 需要验证的内容 |
| --- | --- | --- |
| [client/NeonTumorRenderer.java:37](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/client/NeonTumorRenderer.java#L37)，`preRender` | 每回调创建三元素 `String[]`，逐骨骼查询并设置 slam 可见性；GlowLayer 非 invisible 时做 emissive reRender | 多实体时短数组与额外渲染通道；静态名称可考虑复用，但骨骼状态仍必须按当前实体更新，不能让不同实体共享错误姿态 |
| [client/GravityWorldRenderer.java:18](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/client/GravityWorldRenderer.java#L18)，`render` / `cylinder` / `line` | 无玩家/世界或无相关场/目标时返回；实际绘制先克隆 shader color。每个可见 field 遍历 96 圆段，最多调用 300 次 line；line 创建 Vec3，非退化线再归一化。已有按真实 `fieldBounds()` 的视锥判断及 finally 状态恢复 | 不把现有视锥剔除当新增；距离判断不能使用逻辑实体的 0.1 格物理包围盒而漏掉大场。向量/颜色缓存必须保留矩阵和渲染状态生命周期 |
| [client/GravityFieldRenderer.java:15](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/client/GravityFieldRenderer.java#L15)，`render` | render 本身为空，公共边界由 GravityWorldRenderer 绘制；texture getter 新建 ResourceLocation，但此处没有实际调用频率证据 | 不能把 getter 中 new 写成已发生的逐帧分配，也不能只优化这个空 render 就声称解决公共边界开销 |
| [yuji/client/YujiRenderer.java:55](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/yuji/client/YujiRenderer.java#L55)，`preRender` / `track` / `Glow.render` | 每回调构造六个 layer 名称并查询骨骼，再递归所有顶层骨骼及子骨骼启用矩阵追踪；仅 giant 且非 invisible 时构造 glow 资源名并重绘 | 骨骼数、重绘次数与对象分配应分开采样；资源名缓存可候选，但隐藏层、手持物骨骼和矩阵追踪不能跨实体串扰 |
| [client/SuxiaRenderer.java:22](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/client/SuxiaRenderer.java#L22) / [WaterGhostRenderer.java:24](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/client/WaterGhostRenderer.java#L24) | 纹理/eyes RenderType 已静态复用；eyes layer 仅 aggressive 时绘制。模型烘焙位于构造器，不是每帧创建模型 | 原 hotspots 指出的爬行 float[] 在 SuxiaModel，不在 WaterGhostModel；保留已有条件并先测 setupAnim / allocation |
| [client/ShadowTanglerRenderer.java:18](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/client/ShadowTanglerRenderer.java#L18) / [WingsItemRenderer.java:27](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/client/WingsItemRenderer.java#L27) | 前者模型在构造时创建、使用 translucent；后者每物品渲染按显示上下文绘制 WingsMesh，纹理静态复用 | 本次未取得它们的帧时/分配证据；保留为已查入口，不制造新“热点”或将注册期对象计入每帧 |

## State 与账本分类

原报告已有 RainCityEvents 活跃时间推进标脏、RainCityData、RedNailsState、SuxiaSummons 和 CitySavedData 的证据。本次另核对按类名命中的 State，避免把所有状态对象都误称为 SavedData：

- [pill/PillState.java:17](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/pill/PillState.java#L17) 是 capability 状态，不继承 SavedData。`tick` 处理剩余时间；`presentations` 按 MobEffect 去重，存在 remove/clear。`save()` 生成 NBT、遍历当前效果，但调用该方法不等于每 tick 磁盘写入。
- [wings/WingsState.java:9](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/wings/WingsState.java#L9) 使用每玩家 persistent NBT，不继承 SavedData。数值键及 `blinkTimes` 数组读写不能仅凭名字归类为永久增长的全局 UUID 账本；数组长度与调用频率还需从调用者验证。
- [rednails/RedNailsState.java:39](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/rednails/RedNailsState.java#L39) 的 `owned(owner)` 每调用扫描全部 `entries.values()` 并生成结果列表；`save` 遍历全部 entries。`read` 对新 item UUID 插入、`begin/finish` 更新并标脏，类内未发现删除整个 entry 的路径。此为基数增长风险；没有长期存档曲线，不声称已测出泄漏，不能用随意 TTL 丢弃离线/卸载归属。
- [eternalnight/CitySavedData.java:68](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/9955ecd3b96bdfa9a58f8586a18851319fdc8c24/src/main/java/com/zeropointsix/eraser/eternalnight/CitySavedData.java#L68) 的 `activeExits` 每调用扫描出口和待关闭集合，仅 dirty 时标脏，返回 `Map.copyOf(exits)`；存在过期出口删除、成功关门移除、boss 释放。调用者频率与地图大小需分别测，不能写为每 tick 全盘保存或所有表永久增长。

## 恢复时 HEAD 的检查

2026-10-17 00:05 UTC 从 GitHub Actions API 回源，以下均属于 `9955ecd3b96bdfa9a58f8586a18851319fdc8c24` 的 attempt1，全部 completed/success。此表不是本次文档补充提交的检查结果；新提交的检查必须另行核对并写入 PR 评论。历史失败保留在原报告中，不由此宣称其根因已修复。

| 工作流 | 恢复时结果 |
| --- | --- |
| Neon Tumor | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38974969202) |
| Brickrot | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38974969170) |
| Yuji City | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38974969558) |
| Enhancement Pill | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38974969370) |
| Shaxiadao | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38974969296) |
| Shadow Tangler | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38974969410) |
| Gravity Jade | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38974969393) |
| Yuji Fodu | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38974969159) |

[Gravity 原始 job117222090880](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38974969393/job/117222090880) 记录 `MIXIN_TARGETS checked=14 failures=0`、不带子集开关的 `runGameTestServer`、`434 GAME TESTS COMPLETE`、`All 434 required tests passed` 和 `BUILD SUCCESSFUL`。这证明完整默认套件，不把 YujiOnly120项当全量，也不把源码基线 e170331 的旧 Mixin 记录当本次结果。

## 下一关口

PR1 可交付的是可追溯基线、静态候选及上游方法对照，不是整个 T2 已完成。后续 cap/config 必须保持独立 PR，并用固定世界、同机重复基线和逐秒站位记录做比较；减少实体总量与相同实体数量下的效率收益分开报告。PR3 依据调用路径选择缓存并定义失效，PR4 补真实客户端帧时/FPS/allocation，保留巨型包围盒、发光和公共重力场语义。本轮不推进上述运行时修改、不合 main、不正式发布。
