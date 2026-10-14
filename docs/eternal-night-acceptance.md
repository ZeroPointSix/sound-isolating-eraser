# 永夜都市 v0.1 功能验收清单

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
3. **隔离 / AfterBatch**：`isolate()` 只在第一个 sandbox 用户上写默认 NightConfig 并 `clearForTesting`；后续并行 batch 只加引用计数，避免把 `ENTRY_BASE=1` 打回 `0.02`（CI 全量套件曾因此让 `cityDeepSleepAndReturnPreservePlayer` 在 105 tick 仍睡着）。`@AfterBatch` 失败注入 + 超时路径都走同一 `restoreConfig`，日志 `NIGHT_AFTERBATCH_FAIL_RESTORE` / `NIGHT_AFTERBATCH_TIMEOUT_RESTORE`。真实 Forge 超时失败用例不能留在绿套件里。
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

CI 以 GitHub Checks 为准。`f4c1007` Eternal Night 原生步在 observer `Native phase timed out: READY`：双方都拍了 `original-art`，但 `/nightqa ready` 只发一次、CI 丢包后 `READY.size()` 达不到 2。现客户端周期重发 ready/captured，服务端 6 秒后用城内在场兜底。全量 GameTest 并行 isolate 踩 `ENTRY_BASE` 已修。雨寂/重力若仍偶发失败，与街道台阶剥离无直接调用关系。本轮**不合 main**，也不自称 Notion 功能验收通过。
