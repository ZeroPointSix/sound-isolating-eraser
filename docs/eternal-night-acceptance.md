# 永夜都市 v0.1 功能验收清单

对照 Notion v0-1（<https://app.notion.com/p/v0-1-3f6463aed7e6818db813c2b77fbdc21a>）与仓库实现。本文件是 2026-10-14 续做（白门回程 + GameTest 隔离 + 审查修复）的验收记录，不是独立 Review。

- 实现基线：main 已含已合并的 [PR #20](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/20)（merge HEAD `ac2c157`）。
- 续做分支：`cursor/eternal-night-white-door-d4dc`（PR #20 已关闭，不能再更新 Draft #20）。Draft [PR #31](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/31)。
- 原包：`eternal_night_city_kit.zip` 2,759,557 字节，SHA-256 `9dab6be385742ebc8a1e7c2d371db5f8b5ef6b40a1062ee376ae99f915455d24`。
- Notion 原文：本轮 Maton 集成对该页返回 `object_not_found`（页面未共享给 Maton）。验收句来自仓库 `docs/eternal-night-v0.1.md` 中已回源摘录的对照表，独立审查已读 Notion §7「工业区地下 / 常驻地下」，以及原 Slack 线程里多次核对过的同一套条款。

判定：PASS = 代码路径 + 本轮或既有可复现测试；FAIL = 实现不符且本轮可修却未修；blocked = 缺授权资源或产品原文含糊；partial = 代码有路径但证据不覆盖正式包或完整联调。

| 需求 | 判定 | 证据 |
| --- | --- | --- |
| 入城公式 `min(0.02+0.08*floor(t/24000), 0.5)`，合法深睡只抽一次 | PASS | `NightRules.entryProbability`；`cityDeepSleepAndReturnPreservePlayer`；既有双客户端深睡入城 |
| 同夜同一区域、安全街道 | PASS | `CityEntryQueue` 用主世界日数哈希中心；`safeStreet` 31×31 / 无怪 / 固体地面 |
| 木门计数、上下半归一、重复不重抽；铁门仅按钮/拉杆 | PASS | `cityRealPlayerDoorInteractions` |
| 每新门 1% 出口 | PASS | `NightRules.Doors.open` 的 `lucky` 分支；`cityDoorHistoryNbtAndPity` 失败骰不提前出门 |
| 100 门保底 | blocked（产品语义） | Notion/台账原文是「100门保底」，没有写「仅第 100 扇」或「第 101 扇起仍保底」。实现是 `count >= guarantee`。本轮**不改代码**。建议后续若产品定为 pity，则改 `==` 并加第 101 门断言 |
| 共享出口 300s SavedData，穿过回床 | PASS（本轮加锁） | `CitySavedData` Until；`cityWalkingThroughSharedExitReturnsHome`：开叶穿越、关叶仍传、>4 格滞后跳跃仍传、过期不传；窗口内不可关 + 过期自动关见 `cityExitDoorStaysOpenUntilExpiry`；卸载区块过期后门稍后补关见 `cityExpiredExitClosesWhenUnloadedChunkLoads` |
| 死亡清史、不改原版掉落 | PASS | `cityDeathDropsAndRespawnsAtOwnBed` |
| 分区 55/15/30、地铁、无洞穴 | PASS | `CityLayout` / `cityGeneratedSubwayAndShaftConnect` |
| 城区专属建筑池（mall/市政不进住宅） | PASS（本轮加锁） | 大建筑是街区内部 3×3 地块中的 2×2 区块（32×32）模板。现 `CityTemplates.largeCivic` 仅商业区；`cityDistrictPoolsKeepCivicBuildingsInCommercial` |
| 原版生物禁刷 | PASS | `CitySpawns.naturalSpawn` 取消非缠影子/霓虹瘤/砌腐墙的 NATURAL/CHUNK/PATROL/EVENT；`cityEventInterceptionCoversSpec` 僵尸禁刷 |
| 砌腐墙栖息地 | PASS（本轮按 Notion §7 收口） | 工业区且 `y < 56`。`CitySpawns.brickrotHabitat`；`cityBrickrotHabitatIsIndustrialUnderground`。禁止把工业区地表刷墙标 PASS。密度/挂壁/三怪联调仍未验，PR #18 未操作 |
| 白面 / 粒子 / 低沉音效 | PASS（代码+既有 e2e） | `CityClientRuntime` 白面；`END_ROD` + `BEACON_AMBIENT`；既有 native 白像素对照。本轮未重跑双客户端 e2e 则不把听觉当新证据 |
| 事件拦截（床/锚/生长/传送门/原版怪） | PASS | `cityEventInterceptionCoversSpec` |
| 窗口门锁（木门点击 / 铁门红石 / `setOpen(false)`） | partial | GameTest 开发 classpath 已覆盖三入口：`cityExitDoorStaysOpenUntilExpiry`。Mixin `@Group min=3`，正式 SRG 目标已改为 `m_153165_`（`setOpen`）。**正式 remap JAR 门锁注入未用原生客户端/成品 JAR 单测证明**，不能标 PASS |
| 美术授权范围 | blocked（缺包） | 原包仅 `curse_glyph_0..3`，无 32 字形；无医院/警局/消防专用模板（`yuji/buildings/hospital.nbt` 属于雨寂佛都，禁止挪用）。`CityTemplates.available` 现校验 **35** 个已授权结构（12 建筑 + 4 广场 + 12 街道 + 7 地铁） |
| R-1 同夜刷床再抽 | blocked（无明文） | 未改行为 |
| R-2 旁观者穿门 | blocked（无明文） | 未改行为 |

## 本轮代码改动

1. **回程传送**：不再要求门叶 OPEN，也不再丢弃位移 ≥4 格的包。占用门洞体积、线段 clip、原 sign-flip 任一成立即排队 `returnHome`；服务端 tick 补扫访客，避免嵌入连接漏 `PlayerTickEvent`。
2. **窗口门锁**：`CityDoorMixin` 拦截 `use` / `neighborChanged` / `setOpen`（SRG `m_6227_` / `m_6861_` / `m_153165_`）的关门；过期 `activeExits(level, now)` 关叶反馈。开发 classpath 已测三入口；正式 remap JAR 未证。
3. **隔离**：每个 eternal_night 用例开头 `isolate()`：配置默认值、清空 `CitySavedData`、入口队列和 NightEvents 瞬态。每个独立 batch 有 `@AfterBatch` 调用 `liveConfig.close()`，成功/失败/超时都恢复宿主配置；`cityConfigSandboxRestoresHostValuesAcrossReruns` 同 JVM 跑两次证明不泄漏。
4. **砌腐墙**：栖息地改为工业区地下（`district == INDUSTRIAL && y < 56`），不再用 `INDUSTRIAL \|\| y < 56`。
5. **过期关门**：区块未加载时保留 `PendingClose`，加载后再关；NBT 可读回。
6. **文档**：结构模板 35 项；大建筑是 3×3 内部地块中的 2×2 区块。

## 命令

```bash
JAVA_HOME 指向 JDK17 后
bash ./gradlew --no-daemon --max-workers=2 -PnightE2E checkNightRules verifyCityAssets build writeNightTestClasspath runGameTestServer
python3 tools/night-e2e.py   # 有显示/客户端 classpath 时
```

## 本轮验证（HEAD `c77f765`）

本地 JDK 17：

- NightRules **666329**（含 CityDoorMixin `setOpen` / `m_153165_` 源码门禁 4 项）
- CITY_ART_VERIFIED 87 files / 6 loot tables
- `-PnightOnly runGameTestServer`：**19/19**
- `-PnightE2E ... runGameTestServer`：**328/328**
- `python3 tools/night-e2e.py`：`NIGHT_TWO_CLIENT_E2E_PASS` 与 `NIGHT_RESTART_AND_REAL_CLIENT_DEATH_PASS`
- 成品 JAR `build/libs/sound_isolating_eraser-0.11.0.jar` 字节含 `m_153165_`，不含错误 SRG

CI 以 GitHub Checks 为准。雨寂都市客户端窗口超时、重力 `hostileHoversThenFallsOutsideCylinder` 悬停高度若偶发失败，与本轮白门/栖息地/PendingClose 路径无直接调用关系，以重跑后的 HEAD 检查为准。
