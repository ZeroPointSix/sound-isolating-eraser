# 永夜都市 PR #20 第二份独立审查报告（HEAD `ac2c157`）

审查人：Devin（第二名独立审查 Agent；未参考第一份审查的会话过程，结论为独立复核所得）。
范围：`src/main/java/com/zeropointsix/eraser/eternalnight/` 全部 16 个源文件 + 4 个 City mixin（CityPlayerInteractionMixin / CityDoorMixin / CityScheduledTickMixin / CityBlockTickMixin）+ `EternalNightGameTests` + `nightTest/` e2e harness + 资源 JSON + `tools/` 校验脚本。
方法：逐文件通读源码并对照 Notion v0-1 逐条核对；自行扫描 35 个 NBT 模板字节（门/容器/禁用方块）；核对 manifest 87 文件计数、维度 JSON、lang 键；复核 CI 检查状态与 e2e `.pass` 标记语义；不重新实现、不修改代码、不重跑全量 e2e。

## 一、逐项判定表

| Notion 需求 | 判定 | 独立核对依据 |
|---|---|---|
| 深睡入城 `.02+.08*失眠夜数 cap .5` | 实现吻合 | `NightRules.entryProbability` = `min(cap, base+daily*floor(restTicks/24000))`，`restTicks` 取睡前 `TIME_SINCE_REST` 快照（NightEvents.sleep LOWEST）；`isSleepingLongEnough()`（100t）单抽，`attempted` 去重；离线 NightRulesCheck 边界 2/10/26/50% 逐值断言。 |
| 合法深睡只抽一次 | 实现吻合（附偏差候选 R-1） | `attempted` 标记先于 roll 写入；但退床重睡=新会话可再抽，见 R-1。 |
| 同夜同一区域 | 实现吻合 | `CityEntryQueue.request`：`night=floorDiv(overworld.dayTime,24000)` → `CityLayout.hash` 得中心 x/z（均 64 倍数），同夜必同区域；e2e 双客户端同夜 48 格内实测。 |
| 安全街道落点（无怪、可站立） | 实现吻合 | `safeStreet` 31×31 内 128 次抽样：street+heightmap∈[surface,surface+2]+实心脚下方块+noCollision+无液体+16 格内无 Monster+worldborder。 |
| 入城不改背包/床位 | 实现吻合 | `finishEntry` 仅 `stopSleepInBed`+teleport+fade/title；`PlayerSetSpawnEvent` 在城市维度一律取消（禁设重生点）；e2e 实测 7 钻保留、各回原床。 |
| 持久化开门计数（上下半归一、重复不重抽、归因链） | 实现吻合 | `lowerDoor` 归一 + `opened.add(canonical)` dedupe；因果链 `useItemOn→BlockState.use→DoorBlock.setBlock`（mixin 链在 `before.OPEN=false→after=true` 才计）；按钮/拉杆经 `schedule` HEAD 捕获 + `tickBlock` 恢复身份（200t 上限、`hasScheduledTick`/`willTickThisTick` 防抢占、重启/超时保守不计）；铁门经拉杆计入、放红石块不计入——均有 GameTest 断言。 |
| 每新门 1% 出共享出口 | 实现吻合 | `roll(EXIT_CHANCE=0.01, player.getRandom())`；抽样 63.7% 前 99 门出（理论 63.0%）由验收报告记录在案。 |
| 100 门保底 | **语义偏差候选（P-1，待裁决）** | `opened.size() >= guarantee || lucky`：第 100 门必出（==与>=在 100 处等价），但第 101+ 门起每扇新门必成出口——改变了"每新门 1%"的稳态语义。两处测试均只断言到 100 门，未锁定 101+ 行为。详见三节复核。 |
| 计数提示 25/50/75/90 | 实现吻合 | `milestone()` + `city.milestone.*` zh/en lang 键齐备；`city.doors` actionbar 计数。 |
| 共享白色出口 5 分钟 | 实现吻合 | `Until=gameTime+EXIT_SECONDS*20`（默认 300s=6000t）；`CitySavedData` NBT Exits 列表落盘；`active()` 严格 `until>now`；`activeExits` 惰性清理+快照；重启读回由 e2e `server-reload.pass` 实测（含精确 Until 比对）。 |
| 穿门回个人床/出生点 | 实现吻合 | `crossedDoor` 线段-平面交叉+门洞 AABB 兜底→`returnHome`：床（findRespawnPositionAndUseSpawnBlock）→出生点环搜→共享出生柱保底（`NIGHT_UNSAFE_HOME` 日志+提示，绝不滞留）；e2e 双人各回自床实测。 |
| 出去清空计数 | 实现吻合 | `returnHome` 移除整个 `EternalNightCity` NBT（Doors/Visiting/ReturnAfterDeath 一并清）。 |
| 死亡回床清历史、不改掉落 | 实现吻合 | `death`（城内）→置 `ReturnAfterDeath`+删 `Doors`→Clone 复制→`respawn`→`returnHome`；未触碰 keepInventory/掉落规则；e2e 实测 7 钻可见掉落+点复活按钮回床+清史。 |
| 三分区 55/15/30 | 实现吻合 | `CityLayout.district`：128 格块 hash mod 100，`<55 RES/<70 COM/else IND` 逐字吻合（55/15/30）；NightRulesCheck 65536 样本比例断言 ±2%。 |
| 20~30 格高差、平地街区 | 实现吻合 | `plateau`∈[64,88]（波函数 0-8 三角波），建筑块内平地、街道 `slope` 逐块跟随 surface；高差包络 24 格在 spec 20~30 区间内。 |
| 无限人工地表、无洞穴/矿/液体/植被 | 实现吻合 | `CityChunkGenerator`：`applyCarvers`/`spawnOriginalMobs`/`applyBiomeDecoration` 全空覆写；`fillFromNoise` 床岩-石砖-灰混凝土柱；PlacementProcessor 去水logged/灌木/树叶、土砂砾→灰混凝土。 |
| 地铁固定线路+站台 | 实现吻合 | SUBWAY_Y=40；lx==0 NS 线与 lz∈{0,3} EW 线+split 四通；站块 1/6 概率放 platform_stairs+井道堆叠+连续梯子（README 许可）；GameTest 跨 72/65 格+梯子+支撑断言。 |
| 每栋 1~4 容器、首开懒生成装备 | 实现吻合 | `chooseContainers` chest/trapped_chest/barrel 候选按 seed 排序取 1-4；非保留容器置 AIR、保留者重写 tag 仅 `LootTable+LootTableSeed`（清原 Items）；6 表白名单装备/工具/箭，verifyCityAssets 断言无食物/水/药水；GameTest 实测 unpack→装备+不补货+NBT 往返。 |
| 木门手动开、铁门仅按钮/拉杆 | 实现吻合 | 见开门计数行；红石块放置不计入有断言。 |
| 缠影子/霓虹瘤/砌腐墙密度规则 | 实现吻合（shadow 挂起） | `CitySpawns.tick` 每 100t×每玩家 16 试：shadow 非商业区或 y<56+light≤4+贴墙+cap10（registry ID 缺失即跳过，绝不回退默认实体——PR #18 未合并安全）；neon 商业区+attachmentLight≥12+贴墙+cap12+大体型每 64 块≤2；brickrot 工业区或地下+512 区 `reserveBoss` 持久+`releaseBoss` 死亡释放。 |
| 禁原版自然生物/袭击/传送门 | 实现吻合（附观察 R-4） | biome `spawners:{}`+`FinalizeSpawn` 白名单（NATURAL/CHUNK_GENERATION/PATROL/EVENT 仅放行 shadow/neon/brickrot）；`has_raids:false`+EVENT 双保险；`PortalSpawnEvent` 取消。35 NBT 全扫无 portal/spawner/bed/anchor/植物方块（本审查自验）。 |
| 床不爆炸但不能睡、锚无效 | 实现吻合 | `bed_works:true`+`RightClickBlock`（LOWEST）对 BedBlock/RespawnAnchorBlock 一律取消→不炸不睡；city sleep 再返回 OTHER_PROBLEM 双保险；`respawn_anchor_works:false`。 |
| 不长植物 | 实现吻合 | `CropGrowEvent.Pre`+`SaplingGrowTreeEvent` DENY+`BonemealEvent` 取消（`DISABLE_GROWTH` 门控）。 |
| 固定 18000/无天光/无日月星云 | 实现吻合 | `fixed_time:18000`、`has_skylight:false`、`ambient_light:0`、biome 无降水；`CityClient` SkyType.NONE+雾色+常雾。 |
| 时钟/指南针/鞘翅/珍珠不改 | 实现吻合（附文档勘误 R-5） | 模组不触碰三者；`natural:false` 使指南针随机旋转、fixed_time 使时钟定格——皆为原版语义（v0.1.md 终验清单"指南针指 spawn"表述不准，见 R-5）。 |
| 黑场渐隐+到达文案 | 实现吻合 | `CityClientRuntime` 维度切换 40t 渐隐+服务端 `ClientboundSetTitleTextPacket` 中文标题。 |
| 白面共享出口视觉 | 实现吻合 | `CityExitNetwork.sync` 每秒≤256 近邻快照（64 格）→客户端 `AFTER_TRANSLUCENT_BLOCKS` 无深度写白四边形（仅在门 OPEN 时渲染）；END_ROD 粒子+BEACON_AMBIENT 每 80t 音效（服务端）。 |
| 原包美术 87 文件 | 实现吻合 | manifest 实测 87 项（35 NBT+22 PNG+29 JSON+MIT）；`verifyCityAssets` 每文件 SHA256 门禁+ZIP 整体 SHA `9dab…5d24`；`check` 依赖该任务。 |
| 32 字形 | 资源缺口（非代码缺陷） | `NeonSignBlock.GLYPH=0..3`、COLOR 5 色，与原包 README 一致；87 文件内无其余 28 字形授权资源。 |
| 医院/警局/消防专用外观 | 资源缺口（非代码缺陷） | 原包仅有通用 civic_center/townhall 等；87 文件终扫无专用模板。 |
| 缠影子三怪联调 | 依赖阻塞 | PR #18 仍 Draft+冲突；本 PR registry-safe 降级，不阻塞本 PR 合并判定。 |

## 二、发现的问题（按严重度）

| # | 级别 | 位置 | 独立复核发现 |
|---|---|---|---|
| P-1 | 语义偏差候选（待产品裁决） | `NightRules.Doors.open` | `count>=guarantee` 使第 101+ 扇新门全部强制成出口，与"每新门 1%"稳态语义不符；"保底"主流解读为恰第 100 门必出、其后回归 1%（`==`）或触发后重置计数。合并前需裁决。 |
| P-2 | 观感偏差 | `NightEvents.playerTick` + `CityClientRuntime` | 共享出口是普通 `DoorBlock`，任何玩家可点击关闭；穿越与白面渲染都以 `OPEN==true` 为前置 → 被关后穿门无效果、白面消失。建议：激活期间拦截关门或至少提示。 |
| P-3 | 观感偏差 | `CitySavedData.activeExits` | 出口过期静默清理：过期瞬间无任何反馈（白面消失是唯一信号），过期后穿门无任何效果。可能是"回城不稳定"观感来源。建议过期自动关门或提示。 |
| R-1 | 偏差候选（本次新发现，中低） | `NightEvents.sleep`/`playerTick` | 退床重进床=新 `SleepAttempt(attempted=false)`→同夜可无限重抽（每次 100t≈5s）。字面"合法深睡只抽一次"仍成立（每次深睡一抽），但与"每夜一次"的隐含语义存在解释空间：有意刷床可把当夜入城概率推向近 1。另：成功入队时 `stopSleepInBed` 会重置 `TIME_SINCE_REST`（原版语义），入城失败/超时同样清零——失眠加成不跨尝试保留。建议同 P-1 一并裁决语义。 |
| R-2 | 低 | `CitySpawns.tick`/`NightEvents.playerTick` | 旁观者（spectator）在城内穿门同样被 `returnHome` 送回主世界——`playerTick` 出口扫描未豁免 `isSpectator`（spawner 豁免了）。管理员/观察者旁观城市时可能意外被传送。 |
| R-3 | 低 | `CityTemplates.available` | 入城门禁只校验 12 栋建筑模板，街道/地铁/plaza 模板缺失不拦截入城；`placeOne` 对缺失模板静默跳过 → 街道缺口会留下实心填充（`fillFromNoise` 先填实）。`verifyCityAssets` 构建门禁已覆盖 87 文件，属运行时健壮性加固空间，非阻断项。 |
| R-4 | 低/潜伏 | `CitySpawns.naturalSpawn` | 白名单只拦 NATURAL/CHUNK_GENERATION/PATROL/EVENT；`SPAWNER`（刷怪笼）、`SPAWN_EGG`、命令 `/summon` 不拦。已对 35 NBT 自验：原包无 spawner 方块，故仅为潜伏边界；蛋/命令属玩家主动行为，可接受。 |
| R-5 | 文档勘误 | `docs/eternal-night-v0.1.md` 终验清单 | "指南针指 spawn"表述与 `natural:false` 矛盾：非 natural 维度指南针按原版随机旋转（这正是 spec"时钟/指南针旋转"的本意）。实现正确，文档措辞建议修正。 |
| M-1 | 轻微 | `NightRules.Doors.open` | `lucky=roll` 在 dedupe 前消耗样本：重复开门不计入但耗一次 `nextDouble()`。结果正确，建议先判重再 roll。 |
| M-2 | 轻微 | `CitySavedData.activeExits` | `removeIf(until<=now)` 清理副作用语义易误用（传 `Long.MAX_VALUE` 即清空全表）。建议改名 `pruneExpiredAndSnapshot` 或注释。 |
| M-3 | 信息 | `NightEvents` | `Visiting` 只写不读（审计/e2e 用途）；保留无害。 |
| M-4 | 信息 | `NightEvents.clonePlayer` | 对一切 `PlayerEvent.Clone` 复制 tag（含 End 返回非死亡克隆），当前路径无害。 |
| M-5 | 信息 | 原包素材 | `mall.nbt` 无 `DoorBlock`（ barrel-only）；`station_entrance`/`tunnel_bend`/`station_platform` 三个模板未被引用——均为原包事实/冗余素材，非缺陷。 |

## 三、对第一份审查已知 3 项的独立复核

| 项 | 独立复核结论 |
|---|---|
| 保底语义 `>=` vs `==` | **确认成立，且补充稳态分析**：在 `count` 单调不减前提下，`>=` 使保底"锁死"——第 100 门后城市失去对玩家的约束力（每扇新门必出口），与"每新门 1%"需求描述在 101+ 区间直接冲突。两份测试（NightRulesCheck `exit()==(i==100)`、`cityDoorHistoryNbtAndPity`）均只覆盖 ≤100，未锁定边界外行为，即该偏差不是测试回归能发现的——必须由产品裁决后小改+补 101 断言。**建议 `==` 或"触发保底后重置计数"**。 |
| 出口门可关 | **确认成立**：服务器端穿越判定（`NightEvents.playerTick`）与客户端白面渲染（`CityClientRuntime`）都以 `DoorBlock.OPEN` 为前置；e2e 中 fixture 门被刻意 `setBlock(OPEN,false)` 后 owner 直接死亡流程，也侧面证实关闭态无穿越。属观感层非逻辑错误。 |
| 过期无反馈 | **确认成立**：`activeExits` 惰性删除过期项，过期瞬间服务器无动作、客户端仅下秒快照少一个白面；玩家对"为何没回去"无感知。属观感层。 |

独立复核另有 3 项轻微结论（M-1/M-2 与第一份 #4/#5 重合：RNG 样本消耗、`removeIf` 语义易误用）与 `Visiting`/`clonePlayer` 信息项——方向一致、独立验证通过。

## 四、既有证据复核（是否支撑结论）

- **CI 7/7**：本审查时点对 ac2c157 复核 GitHub 检查 `verify/server-regression/supplied-art/native-client/build-and-game-tests/night-native-and-regression/validate` 全绿（7 passed/0 failed），"CI 7/7"表述属实。
- **e2e `.pass` 语义**：`.pass` 文件由 `nightTest` 内嵌 harness 在断言逐条成立后才写盘（如 `server-reload.pass` 前置校验 seed/Until/双人 compound/床/背包逐值相等），驱动方要求全部必需标记存在且无 `.failed`、进程存活——非自证式标记，语义可信。边界如实：入城概率被置 1.0、Owner 预置 99 门后点第 100 门（覆盖 `==` 边界，不覆盖 101+）、`server-survey.complete` 只表示采集结束（0/12 原生走通如实记录为未通过）。
- **GameTest 与 spec 的贴合度**：11 项城市测试是真断言（真实 `startSleepInBed`/真伤害死亡/真 `useItemOn`/真容器 unpack/真 BFS/真 NBT 往返），非空转。诚实缺口（测试-需求脱节面，按缺口大小列）：
  1. **事件拦截层无专项测试**：床/锚 `RightClickBlock` 取消、`CropGrow/SaplingGrow/Bonemeal` DENY、`PortalSpawnEvent` 取消、`PlayerSetSpawnEvent` 取消、`FinalizeSpawn` 白名单（原版怪被拦）均无 GameTest/native 断言，仅有代码路径与维度 JSON 核对——建议补 1-2 个拦截测试；
  2. 保底 101+ 语义未锁定（见 P-1）；
  3. 同床重睡再抽未覆盖（见 R-1）；
  4. `NightConfig.ENABLED=false` 关断路径未覆盖；
  5. 5 分钟实时窗、音效听觉、默认概率分布、挂壁视觉密度为代码级/非统计证据（已如实记录于台账）。

## 五、结论

**CONDITIONAL（条件合并）**——与独立复核结果一致：代码与 Notion v0-1 高度吻合、工程质量良好（因果归因链、SavedData、异步入城、registry-safe、测试隔离），但合并前需满足：

1. **产品裁决 P-1 保底语义**：若裁定为"恰第 100 门必出"，改 `opened.size() == guarantee || lucky`（一行 + 一处 101+ 断言）；若接受现状，把 `>=` 语义在需求文档中定稿。一并裁决 R-1 同夜重抽语义。
2. **资源缺口决策**：32 字形与医院/警局/消防外观属授权资源缺失，非实现缺陷——补充授权原包或在 Notion 降级需求，均不阻塞代码合并本身。
3. P-2/P-3 观感增强（拦截出口关门/过期反馈）建议合并后跟进，不阻断。
4. PR #18 按独立流程合并后再做三怪联调验收。

PR 保持 Draft、不合并 main；本审查未修改任何实现代码。
