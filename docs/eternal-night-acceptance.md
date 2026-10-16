# 永夜都市 v0.1 功能验收清单

## 2026-10-15 续作当前状态（未完成独立验收）

**真实交付位置是 [Draft PR #34](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/34)，不是已合并的 #31。**

- 分支 `agent/eternal-night-entry-release-20261015`，代码 HEAD `32d83a5a15f6eee60ac2a79de38ac9973a0731a0`。只把主线已有修复同步到该分支，没有向 main 合并。
- GitHub 回源确认：#20 于 2026-10-11 15:05:55 UTC 合入 main；题为「fix(city): 共享白门走进去稳定回床，隔离 Eternal Night GameTest」的通知属于 **#31**，它于 2026-10-15 01:59:37 UTC 合并，早于本任务 02:37 UTC 的接管。最终 source 为 `56f2a07`，合并提交 `9def214`。无法仅凭 merge 元数据判断此前授权，未擅自回滚。
- 主线随后通过 #35 独立合入同一睡眠注入修复。`32d83a5` 保留主线更精确的 `stopSleeping()V` / `m_5796_()V` 签名，消除续作冲突，并调整对应静态回归。原主线和 #18 未改动。
- Notion v0.1 已重新回源：REST 连接返回 404，已授权 MCP 连接成功读取原页，原页最后编辑时间 2026-10-11 07:38:00 UTC。以下不把转述当成新产品决定。
- [永久原始证据与命令](evidence/eternal-night-entry-release-20261015/README.md)包含通过结果、截图、JAR 哈希和失败记录。以下是实现方验证，不是独立 Review 或 Notion 功能验收通过。

### 需求与缺口

| Notion 条目 / 任务项 | 状态 | 代码与真实证据 / 边界 |
| --- | --- | --- |
| §4.1 / §4.2 深睡只判定一次、失眠概率 | 已实现并回归 | `NightRules`、`NightEvents.considerSleepEntry`；666397 规则断言、Forge 深睡用例、双原生床交互。原生场景强制 100% 入城，不是默认概率的统计试验。 |
| §4.3 传送前独立黑场文案 | 本轮补齐，实机通过 | `CityEntryOverlay` / `CityClientRuntime` / `CityEntryQueue`：20 tick 渐黑，黑底显示既有文案，至少 60 tick 后才能传送；覆盖睡眠界面和 HUD；失败/中断清除黑场。两客户端仍在主世界时，整帧 517882 黑像素、518 文字像素；原始 PNG 已目视检查。 |
| §4.3 / §4.4 躺姿、物品与床、同夜同区域 | 已实现并回归 | 实际床交互、深睡和同夜入城；保留物品与各自床位。安装版 Forge 曾暴露 `stopSleeping` 注入启动失败，MCP/SRG 双签名已修复并实际启动通过。 |
| §5.1 门去重与持久化 | 已实现并回归 | `NightRules.Doors` / 玩家 NBT；原生第 100 门交互，正常停服、新 JVM、同世界读回 100 个坐标及两玩家 compound。预置前 99 个历史坐标，不冒充手点 100 门。 |
| §5.2 100 门保底 | 第 100 门已实现；101 门后待产品明确 | 保留 `count >= guarantee`，新增第 101 门断言记录当前行为。原文写「第 100 扇新门必定是出口」，未明确以后仅 1% 还是继续保底；未擅自改成 `==`。 |
| §5.3 / §5.5 共享出口、回各自床、计数清除 | 已实现并回归 | 同夜两原生客户端、白面正/负渲染对照、两人走入出口返回各自床；实际重启读回共享出口相同到期时间、两份玩家状态、背包和历史。 |
| §5.3 有效期不可关、到期恢复普通门 | 正式 SRG 进程已补证 | 独立 QA mod 使用**成品 JAR + 安装版 Forge 47.2.0**，确认 `production=true` 及 `m_153165_` 存在且 MCP `setOpen` 不存在。实际调用拒绝窗口内关门，真实 tick 0→20 到期后上下门叶均关闭，普通开关控制组正常。不是字节字符串证据。卸载区块 PendingClose 仍由既有 Forge NBT/重载用例覆盖，不冒充原生客户端卸载 E2E。 |
| §6 城市、地铁、建筑可达 | **部分；楼层抽样仍有失败** | 最新 32d83a5 原生走入入口 12/12，碰撞 BFS 楼层 11/12：工业区 `res_tenement` 原点 `(-752,88,-752)`，门 `(-752,89,-744)`，只到 1 个高度层，预期至少 4 层。7e7ff72 样本曾 12/12，不覆盖此反例。这不是原生爬楼验收，也不证明所有种子/建筑可达；地铁网络、站台与竖井沿用既有回归。 |
| §6.6 / §6.8 32 字形、医院/警局/消防 | **缺授权原资源，未完成** | 原包 4 字形、35 结构（12 建筑+4 广场+12 道路+7 地铁）；87 运行时文件逐项 SHA 通过。没有挪用雨寂佛都医院，也没有自造替代资源；原包允许通用建筑临时代用不等于专用建筑需求完成。 |
| §7 怪物与原版禁刷 | 规则已实现，完整生态未验 | 既有栖息地/禁刷/上限回归；PR #18 三怪联调本轮跳过；怪物密度和附着观感不宣称全部验收。 |
| §8 容器首次打开生成、不补货 | 已实现并回归 | 既有容器懒加载、真实 largeCivic 计数与不 refill 用例；原包 6 个战利品表门禁通过。 |
| §9.2 死亡回床、掉落、清史 | 已实现并实测 | 真正 DeathScreen、原生 Respawn 按钮；7 个可见掉落钻石、身上 0 个、原床复活、历史清零。原文该规则为建议方案，沿用既有配置。 |
| R-1 同夜重复睡 / R-2 旁观者 | 未擅自新增限制 | R-1 原文 §4.1 是「每次入睡只判定一次」，不是每夜一次；现实现按该规则。R-2 是否排除旁观者原文无明确规定，保留当前行为，等待产品裁决。 |

### 本轮测试状态

- 最新代码 `32d83a5` 的 [Eternal Night CI 38694613593](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38694613593/job/116288157052)已完成并通过：**666397 规则断言、87 原包文件 / 6 战利品表、389/389 Forge GameTest**；双客户端、实际停服重启/死亡、正式 SRG 门锁三个通过标记均存在。原始日志与截图在证据目录 `ci-32d83a5/`，不是从旧 SHA 借用结果。
- `32d83a5` 开发机 JDK17：`bash ./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1G -PnightOnly checkNightRules verifyCityAssets build runGameTestServer`，**22/22、666397、87 文件、BUILD SUCCESSFUL in 5m 38s**；原始记录 `host/after-upstream-sync.log`。
- `32d83a5` 仓库 Checks 最终 **11/12，仍有 1 个失败**：[validate 116288158001](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38694613741/job/116288158001) 第二轮全量的 `flareredtransfersvanillapursuitandreacquiresplayer` 在准备后 900 tick 超时。原始完整日志压缩保存在 `failures/116288158001.log.gz`。未放宽断言、未改照明弹实现、未把专项全量通过当作消除红灯的证明。
- 最新 CI 的成品 `sound_isolating_eraser-0.12.1.jar` SHA-256：`bdc3ff9eb8cbd089bfc8f49a2f00b9e89b6f357472b6ed6fec9c9e98175215c6`。安装版测试加载此 JAR 和独立 QA JAR；正式成品内无 QA 类。实际新服务端进程 PID `3210 → 3512`，保存的共享出口到期 `6966`、开门计数 `100` 及两玩家数据精确读回。冷入城最大 tick 间隔 1293 ms，不宣称恒定 20 TPS。
- `7e7ff72` 的 [Eternal Night CI 38680611668](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38680611668/job/116244229161)通过：**666397 规则断言、87 原包文件 / 6 战利品表、388/388 Forge GameTest**，并有 `NIGHT_TWO_CLIENT_E2E_PASS`、`NIGHT_RESTART_AND_REAL_CLIENT_DEATH_PASS`、`NIGHT_RELEASE_DOOR_LOCK_PASS`。
- 上述 CI 实际 checkout 合并测试引用 `3771361ec40492bf469a373cec01fedb9822a666`（`7e7ff72` + 当时主线 `c2981a0`），不能说只测试了旧 342 用例版本。
- `7e7ff72` 的仓库总 Checks 是 **9/12，不是全绿**。三个失败分别为重力落地夹具进流水、信号枪追击准备超时、信号枪降落容器断言；具体测试名和原始日志见证据 README。永夜专项同一 SHA 全量通过不能抵消这些红灯，也不能单凭此判定故障归属。
- 开发机 JDK17 修复后 `-PnightOnly` 已跑 **22/22、666397、87 文件**；正式安装版 Forge 独立进程也通过。原始失败与修复后日志均保留。
- 结论：黑场与正式门锁验证已推进到可复核里程碑；**整个 GOAL 未完成，也不建议跳过红灯和楼层缺口直接合并**。先复现上述楼层反例和照明弹超时，再补授权原资源及产品裁决；#18 跳过。后续归档提交只改文档/原始证据，不与经过测试的代码 SHA 混写。

## 2026-10-15 03:37 中间记录（历史，已被上节更新）

本节保留早期失败和恢复过程，不是最新状态；旧待重跑项以上节最终证据为准。

- GitHub 回源：PR #31 已于 **2026-10-15 01:59:37 UTC** 合入 `main`，合并提交 `9def214f874a9ce07cf0abb36c4c07afe019d67a`。接管任务在 02:37 UTC 才发出，本轮没有执行合并。
- PR 最终 HEAD：`56f2a071090488556a2ed345c0865244cb64b4b7`，与合并提交的文件树一致，不是派单中的 `9c825f2`。
- 已回源确认 [CI 38665002356](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38665002356)：测试的正是 `56f2a07`；Checks 12/12；日志 342/342 GameTest、`NIGHT_TWO_CLIENT_E2E_PASS`、`NIGHT_RESTART_AND_REAL_CLIENT_DEATH_PASS`。这些是**基线**证据，不是本轮未提交修改的 CI。
- Notion v0.1 本轮通过已授权 MCP 连接成功读取。原文 §4.1 为每次睡眠只判定一次；§4.3 要求传送前黑场文字「你醒了，但天没有亮。」；§5.2 要求第 100 扇新门保底；§6.6/§6.8 列出专用建筑与 32 字形。
- 工作区：`kuoya-sgp-002:/data/eternal-night-city-20261011/takeover-20261015`，从现有仓库恢复的隔离 worktree；原工作区及其未跟踪 `.vscode/` 未改动。
- 续作 [Draft PR #34](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/34)，分支 `agent/eternal-night-entry-release-20261015`，代码提交 `697236d16b09c7c3edc430c78eb685e91359200a`。已在 #31 评论交接；没有试图把已合并的 #31 恢复为 Draft。

| 本轮项目 | 实际结果 | 证据 / 尚缺内容 |
| --- | --- | --- |
| 传送前独立黑场文案 | 已实现，客户端实测未通过 | `CityEntryOverlay`、`CityClientRuntime`：20 tick 渐黑，黑底居中文字；服务器至少等待 60 tick 才传送。`NightRulesCheck` 覆盖渐黑、文案、淡出、取消。双客户端增加主世界黑底文字像素断言与截图；首次运行在登录阶段超时，尚未验证文案。 |
| 入城失败黑屏恢复 | 已实现，规则回归通过 | `CityEntryQueue.failed/cancel` 及中断路径发送取消消息；`CityExitNetwork.cancelFade`；清除 overlay 不再永久 hold。 |
| 正式 remap 门锁验证 | **未通过：启动阻断** | 新增独立 `nightReleaseQA` 探针和 `tools/night-release-door-lock.py`，使用安装版 Forge 47.2.0、成品 JAR，`--launchTarget forgeserver`。进程在门锁探针前因 `CityArrivalSleepMixin` 找不到 `stopSleeping` 崩溃。不能记为门锁已通过。 |
| 启动阻断的最小修复 | 已提交，正式进程复跑中 | `CityArrivalSleepMixin` 添加 `m_5796_` 与 `remap=false`，沿用其他 City mixin 的 MCP/SRG 双名模式。映射由 `build/createSrgToMcp/output.srg:76858` 核实。修复后规则 666397、资源校验、JAR 构建通过；GameTest / 正式探针结果待回收。 |
| 双客户端就绪门禁 | 已修测试脚本，待复跑 | 首次日志为客户端 `Timed out`，服务器登录阶段滞后 919 tick。旧脚本仅等待 TCP 端口，新脚本等待本轮服务端 `Done (...)! For help, type "help"` 后才启动两个客户端。初始及重启日志分别判定，黑场截图新增为必须证据。 |
| 保底 `>=` / `==` | 保留 `>=`，待产品明确 | 新增第 101 门回归，明确当前行为。§5.2 未明确 101 门之后行为，不能擅自切换为 `==`。 |
| R-1 / R-2 | 未改变产品语义 | R-1 当前每次有效睡眠只抽一次，同夜重新入睡可以形成新尝试，与 §4.1 的每次睡眠一致；未新增每夜冷却。R-2 旁观者是否应被共享出口排除，原文未明确，保持原行为。 |
| 32 字形及医院 / 警局 / 消防 | 缺授权原包，未实现 | 原包仍只有 4 字形，35 结构。禁止以雨寂佛都或自造替代贴图补数。87 文件校验及原 ZIP SHA 本轮再通过。 |
| PR #18 | 本轮跳过 | 没有改动、合并或移植。 |

### 已执行的命令与结果

```bash
bash ./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1G -PnightOnly checkNightRules verifyCityAssets build runGameTestServer
# NightRulesCheck PASS assertions=666395
# CITY_ART_VERIFIED files=87 loot_tables=6
# All 22 required tests passed :)
# BUILD SUCCESSFUL in 13m 53s

bash ./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1G -PnightReleaseQA -PnightE2E nightReleaseQAJar writeNightTestClasspath
# BUILD SUCCESSFUL in 5m 1s

python3 tools/night-release-door-lock.py --server-dir ../remapped-runtime-20261015 --eula ../repo/build/night-e2e/server/eula.txt
# FAIL exit 1: CityArrivalSleepMixin stopSleeping target missing in installed forgeserver

NIGHT_QA_PORT=25588 NIGHT_QA_TIMEOUT_SECONDS=1200 python3 tools/night-e2e.py
# First attempt FAIL: native client disconnected before scenario completion.
# Preserved before rerun in ../takeover-evidence-20261015/native-first-run/.
```

上述通过项发生在 `CityArrivalSleepMixin` 候选修复之前，不能代替修复后的重跑。完整原始日志保存在工作区的 `../takeover-evidence-20261015/`：`night-only.log`、`probe-build.log`、`release-runtime.log`、`release-runtime-before-fix.log`；双客户端日志位于 `build/night-e2e/`。这些目录是恢复位置，不是假定已公开的下载 URL。

开发机连接曾连续返回 503，备用 SSH 不可用；现已通过恢复的 VPS 入口同步和提交。连接仍间歇失败，本轮不修改基础设施认证配置。新的 [CI 38677204861](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38677204861) 针对续作提交运行，尚未作为通过证据。

剩余顺序：回收修复后 GameTest 和正式服务端探针结果；重跑真实双客户端；将原始日志和最终 CI 结果写回本表及 #34；授权资源和产品待决项继续独立记录，独立验收另行进行。

## 2026-10-14 历史记录

对照 Notion v0-1（<https://app.notion.com/p/v0-1-3f6463aed7e6818db813c2b77fbdc21a>）与仓库实现。本文件是 2026-10-14 续做（白门回程 + GameTest 隔离 + 审查修复）的验收记录，不是独立 Review。

- 实现基线：main 已含已合并的 [PR #20](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/20)（merge HEAD `ac2c157`）。
- 续做分支：`cursor/eternal-night-white-door-d4dc`（PR #20 已关闭，不能再更新 Draft #20）。Draft [PR #31](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/31)。
- 原包：`eternal_night_city_kit.zip` 2,759,557 字节，SHA-256 `9dab6be385742ebc8a1e7c2d371db5f8b5ef6b40a1062ee376ae99f915455d24`。
- Notion 原文：本轮 Maton 对该 v0.1 页仍返回 `object_not_found`（未共享给集成）。栖息地按独立验收评论已回源的 Notion §6.7/§7 原文「地铁隧道、工业区地下」修正，并与砌腐墙设计页「常驻地下」一致。

判定：PASS = 代码路径 + 本轮或既有可复现测试；FAIL = 实现不符且本轮可修却未修；blocked = 缺授权资源或产品原文含糊；partial = 代码有路径但证据不覆盖正式包或完整联调。

| 需求 | 判定 | 证据 |
| --- | --- | --- |
| 入城公式 `min(0.02+0.08*floor(t/24000), 0.5)`，合法深睡只抽一次 | PASS | `NightRules.entryProbability`；`cityDeepSleepAndReturnPreservePlayer`；本轮双客户端深睡入城 |
| 同夜同一区域、安全街道 | PASS | `CityEntryQueue` 用主世界日数哈希中心；`safeStreet` 31×31 / 无怪 / 固体地面 |
| 木门计数、上下半归一、重复不重抽；铁门仅按钮/拉杆 | PASS | `cityRealPlayerDoorInteractions` |
| 每新门 1% 出口 | PASS | `NightRules.Doors.open` 的 `lucky` 分支；`cityDoorHistoryNbtAndPity` 失败骰不提前出门 |
| 100 门保底 | blocked（产品语义） | Notion/台账原文是「100门保底」，没有写「仅第 100 扇」或「第 101 扇起仍保底」。实现是 `count >= guarantee`。本轮**不改代码**。建议后续若产品定为 pity，则改 `==` 并加第 101 门断言 |
| 共享出口 300s SavedData，穿过回床 | PASS（核心）+ 卸载/重启补关 | `cityWalkingThroughSharedExitReturnsHome`；窗口内不可关见 `cityExitDoorStaysOpenUntilExpiry`。PendingClose：强制加载已开门 → 卸载区块 → 过期保留记录 → **NBT `overwriteFrom` 模拟进程重启** → 重载关叶。日志 `NIGHT_PENDING_CLOSE_RESTART` / `NIGHT_PENDING_CLOSE_RELOAD`。真实双客户端 JVM 重启仍不是这条 GameTest |
| 死亡清史、不改原版掉落 | PASS | `cityDeathDropsAndRespawnsAtOwnBed`；本轮 `NIGHT_RESTART_AND_REAL_CLIENT_DEATH_PASS` |
| 分区 55/15/30、地铁、无洞穴 | PASS | `CityLayout` / `cityGeneratedSubwayAndShaftConnect` |
| 城区专属建筑池（mall/市政不进住宅） | PASS（本轮加锁） | 大建筑是街区内部 3×3 地块中的 2×2 区块（32×32）模板。现 `CityTemplates.largeCivic` 仅商业区；`cityDistrictPoolsKeepCivicBuildingsInCommercial` |
| 原版生物禁刷 | PASS | `CitySpawns.naturalSpawn` 取消非缠影子/霓虹瘤/砌腐墙的 NATURAL/CHUNK/PATROL/EVENT；`cityEventInterceptionCoversSpec` 僵尸禁刷 |
| 砌腐墙栖息地 | 规则已改；密度/三怪仍未验 | Notion §6.7/§7「地铁隧道、工业区地下」。`brickrotHabitat` = 各区地铁隧道（`SUBWAY_Y`..`+6` 且生成器走廊）**或**工业区 `y<56`。工业地表和非隧道住宅/商业地下不刷。`cityBrickrotHabitatIsSubwayAndIndustrialUnderground`。禁止把「仅工业区」或旧 `INDUSTRIAL \|\| y<56` 标 PASS。密度/挂壁/三怪联调仍未验，PR #18 未操作 |
| 白面 / 粒子 / 低沉音效 | PASS（代码+本轮 e2e） | `CityClientRuntime` 白面；`END_ROD` + `BEACON_AMBIENT`；本轮双客户端白像素对照 `NIGHT_TWO_CLIENT_E2E_PASS` |
| 事件拦截（床/锚/生长/传送门/原版怪） | PASS | `cityEventInterceptionCoversSpec` |
| 窗口门锁（木门点击 / 铁门红石 / `setOpen(false)`） | partial | 开发 classpath 三入口：`cityExitDoorStaysOpenUntilExpiry`（日志 `NIGHT_DOOR_LOCK_RUNTIME` oakClick / ironRedstone / setOpen blocked）。`@Group min=3`，SRG `m_153165_`。`verifyRemappedDoorLock` 检查成品 remap JAR 字节含 `m_6227_` / `m_6861_` / `m_153165_` 以及 `setOpen` 的 `Level.setBlock` SRG `m_7731_`。night-e2e 仍是 userdev classpath，**不能把 JAR 字符串或开发态三入口写成正式 remap 进程注入 PASS** |
| 美术授权范围 | blocked（缺包） | 原包仅 `curse_glyph_0..3`，无 32 字形；无医院/警局/消防专用模板（`yuji/buildings/hospital.nbt` 属于雨寂佛都，禁止挪用）。`CityTemplates.available` 现校验 **35** 个已授权结构（12 建筑 + 4 广场 + 12 街道 + 7 地铁） |
| R-1 同夜刷床再抽 | blocked（无明文） | 未改行为 |
| R-2 旁观者穿门 | blocked（无明文） | 未改行为 |
| 入城演出（传送前渐黑、躺姿醒来） | partial | 判定成功后 `CityExitNetwork.fadeIn` 在传送前把客户端拉黑并保持；到达后维度切换渐隐。`finishEntry` 以 `startSleeping` 躺在街道上约 40 tick（`CityArrivalSleepMixin` 避免无床立刻醒来）。`cityArrivalLiesDownOnStreet`。黑屏中文文案仍是到达标题「你醒了，但天没有亮。」，传送前只有动作栏 `city.preparing`，**不是独立黑场文案**。引擎不能在区块未就绪时先播原版起床 GUI。部分实现 |
| 12 类建筑入口与楼层 | 原生走路 12/12；楼层 BFS 11/12 | 见下表。探测器：最近 path-clear、街道优先、避开玻璃/混凝土邻地门、距门 2 格、AABB 按原生 1.8 高。街道生成剥掉贴地块 1–3 格底板台阶，避免站在门高台阶上把头撞进门楣。**不得把 GameTest 12/12 写成原生 PASS**；也不得把原生走路 12/12 写成原生爬楼 12/12 |

## 本轮代码改动

1. **回程传送**：不再要求门叶 OPEN，也不再丢弃位移 ≥4 格的包。占用门洞体积、线段 clip、原 sign-flip 任一成立即排队 `returnHome`；服务端 tick 补扫访客，避免嵌入连接漏 `PlayerTickEvent`。
2. **窗口门锁**：`CityDoorMixin` 拦截 `use` / `neighborChanged` / `setOpen`（SRG `m_6227_` / `m_6861_` / `m_153165_`）的关门；过期 `activeExits(level, now)` 关叶反馈。开发 classpath 已测三入口；成品 JAR 有字节门禁（含 `m_7731_`）。正式 remap 进程注入仍缺。
3. **隔离 / AfterBatch**：`isolate()` 只在第一个 sandbox 用户上写默认 NightConfig 并 `clearForTesting`；后续并行 batch 只加引用计数，避免把 `ENTRY_BASE=1` 打回 `0.02`。`restoreConfig` 在 `configUsers==0` 时不再二次 `clearTransientState`。深睡入城与白门回程一样有 **ServerTick 兜底**（`considerSleepEntry`），`cityDeepSleepAndReturnPreservePlayer` 不再用 tick-105 一次性硬断言（全量套件 `gameTime` 追赶时会在 doTick 之前触发）。`@AfterBatch` 失败注入 + 超时路径都走同一 `restoreConfig`，日志 `NIGHT_AFTERBATCH_FAIL_RESTORE` / `NIGHT_AFTERBATCH_TIMEOUT_RESTORE`。真实 Forge 超时失败用例不能留在绿套件里。
4. **砌腐墙**：栖息地为地铁隧道（全区）+ 工业区地下，不是「只有工业区」，也不是旧 `INDUSTRIAL \|\| y<56`。
5. **过期关门**：已存在开叶门：强制加载 → 卸载 → 过期 PendingClose → NBT 模拟重启 `overwriteFrom` → 重载关叶。
6. **入城演出**：传送前渐黑包 + 到达躺姿；见上表 partial。
7. **12 入口**：街道 path-clear；生成时剥掉贴地块底板台阶；`findApproach` 同样清台阶；碰撞盒 1.8 高。距门 2 格。GameTest 与原生 survey 都打印 `approachDist` / `street` / `stepUp`。
8. **文档**：结构模板 35 项；大建筑是 3×3 内部地块中的 2×2 区块。

## 命令

```bash
JAVA_HOME 指向 JDK17 后
bash ./gradlew --no-daemon --max-workers=2 -PnightE2E checkNightRules verifyCityAssets build writeNightTestClasspath runGameTestServer
python3 tools/night-e2e.py   # 有显示/客户端 classpath 时
```

## 本轮验证（修复 Request Changes 后，SHA 以 git / CI 为准）

本地 JDK 17（本推送；原生调查跑在含街道台阶剥离的 `f4c1007`，isolate 不再踩并行 ENTRY_BASE 的修复叠在其后）：

- `NightRulesCheck PASS assertions=666329`
- `CITY_ART_VERIFIED files=87 loot_tables=6`
- `-PnightOnly runGameTestServer`：**22/22**，`CITY_ENTRANCE_SUMMARY walked=12/12 floorsReached=12/12`（12 栋 `street=true approachDist=2 stepUp=0.0`）
- 成品 JAR `sound_isolating_eraser-0.11.0.jar` SHA-256 `6aff705538ec9a9a36c13beee1c3cf67ddf14e7d1312ff9b6253428df0b3d4d8`：`NIGHT_REMAPPED_DOOR_MIXIN_PASS`（`m_6227_` / `m_6861_` / `m_153165_` / `m_7731_`）。开发态不能冒充正式 remap 进程注入，门锁 runtime 保持 partial
- 栖息地正反例：`cityBrickrotHabitatIsSubwayAndIndustrialUnderground`
- AfterBatch：`NIGHT_AFTERBATCH_FAIL_RESTORE` + `NIGHT_AFTERBATCH_TIMEOUT_RESTORE` 均 `ENABLED=true EXIT_SECONDS=300`
- PendingClose：`NIGHT_PENDING_CLOSE_RESTART waitingToClose=true after NBT reload`；`NIGHT_PENDING_CLOSE_RELOAD doorClosed=true after chunk reload`
- 入城演出：`cityArrivalLiesDownOnStreet`；传送前独立黑场文案仍缺，partial
- 本轮双客户端：`NIGHT_TWO_CLIENT_E2E_PASS` + `NIGHT_RESTART_AND_REAL_CLIENT_DEATH_PASS`；survey `12/12 entrances crossed, floors 11/12`

GameTest 12 入口表（碰撞 path-clear，`stepUp=0`，非原生客户端）：

| 模板 | 区 | dist | street | 门 | BFS 楼层 / 门高簇 |
| --- | --- | --- | --- | --- | --- |
| warehouse | INDUSTRIAL | 2 | true | -752,89,-744 | 4 / 2 |
| office_dark | INDUSTRIAL | 2 | true | -752,89,-729 | 14 / 7 |
| res_tenement | INDUSTRIAL | 2 | true | -744,89,-705 | 10 / 4 |
| office_tower | COMMERCIAL | 2 | true | -728,83,-368 | 22 / 10 |
| shop_neon | COMMERCIAL | 2 | true | -712,83,-321 | 4 / 1 |
| mall | COMMERCIAL | 2 | true | -752,86,-288 | 9 / 1 |
| res_apartment_mid | RESIDENTIAL | 2 | true | -752,89,-232 | 12 / 6 |
| res_apartment_high | RESIDENTIAL | 2 | true | -752,89,-216 | 18 / 7 |
| res_apartment_low | RESIDENTIAL | 2 | true | -728,89,-193 | 8 / 4 |
| townhall | COMMERCIAL | 2 | true | -671,80,-368 | 6 / 3 |
| library | COMMERCIAL | 2 | true | -560,71,608 | 6 / 3 |
| civic_center | COMMERCIAL | 2 | true | -496,68,-410 | 8 / 4 |

原生 12 入口调查（`build/night-e2e/city-survey.json`，真实客户端按住 W，`nativeEntrancesPassed=12`，`floorsReached=11`）：

| 模板 | 区 | dist | street | stepUp | walkDepth | 门 | BFS 楼层 / 门高簇 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| office_dark | INDUSTRIAL | 2 | true | 0 | 0.762 | -745,89,-752 | 14 / 7 |
| warehouse | INDUSTRIAL | 2 | true | 0 | 0.762 | -752,89,-728 | 4 / 2 |
| res_tenement | INDUSTRIAL | 2 | true | 0 | 0.762 | -712,89,-705 | 10 / 4 |
| office_tower | COMMERCIAL | 2 | true | 0 | 0.762 | -752,83,-328 | 22 / 10 |
| shop_neon | COMMERCIAL | 2 | true | 0 | 0.762 | -728,83,-321 | 4 / 1 |
| res_apartment_mid | RESIDENTIAL | 2 | true | 0 | 0.762 | -752,89,-200 | 12 / 6 |
| res_apartment_low | RESIDENTIAL | 2 | true | 0 | 0.762 | -728,86,-176 | **1 / 4** |
| res_apartment_high | RESIDENTIAL | 2 | true | 0 | 0.762 | -705,86,-152 | 18 / 7 |
| library | COMMERCIAL | 2 | true | 0 | 0.762 | -688,83,-288 | 6 / 3 |
| mall | COMMERCIAL | 2 | true | 0 | 0.762 | -688,86,800 | 9 / 1 |
| townhall | COMMERCIAL | 2 | true | 0 | 0.762 | -607,71,528 | 6 / 3 |
| civic_center | COMMERCIAL | 2 | true | 0 | 0.762 | -496,65,550 | 8 / 4 |

`res_apartment_low` 原生从该扇已穿过的门做碰撞 BFS 只到 1 层（期望 4 个门高簇）。入口已走进去（walkDepth 0.762），**不是**进不去。GameTest 在另一世界种子上从该模板的门 BFS 到 8/4。原生爬楼不得标 12/12。三怪联调仍 `threeMonsterIntegrationVerified=false`。

CI 以 GitHub Checks 为准。`f4c1007` Eternal Night 原生步在 observer `Native phase timed out: READY`：双方都拍了 `original-art`，但 `/nightqa ready` 只发一次、CI 丢包后 `READY.size()` 达不到 2。`52e05dc` 起客户端周期重发 ready/captured，服务端 6 秒后用城内在场兜底。`38656882761`（`52e05dc`）在 gradle 套件就失败：`cityDeepSleepAndReturnPreservePlayer` 仍睡着，Native READY 没跑到。现 ServerTick 深睡兜底 + 去掉 tick-105 硬断言。雨寂/重力若仍偶发失败，与街道台阶剥离无直接调用关系。本轮**不合 main**，也不自称 Notion 功能验收通过。
