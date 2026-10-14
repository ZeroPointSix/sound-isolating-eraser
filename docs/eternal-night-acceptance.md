# 永夜都市 v0.1 功能验收清单

对照 Notion v0-1（<https://app.notion.com/p/v0-1-3f6463aed7e6818db813c2b77fbdc21a>）与仓库实现。本文件是 2026-10-14 续做（白门回程 + GameTest 隔离 + 审查修复）的验收记录，不是独立 Review。

- 实现基线：main 已含已合并的 [PR #20](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/20)（merge HEAD `ac2c157`）。
- 续做分支：`cursor/eternal-night-white-door-d4dc`（PR #20 已关闭，不能再更新 Draft #20）。Draft [PR #31](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/31)。
- 原包：`eternal_night_city_kit.zip` 2,759,557 字节，SHA-256 `9dab6be385742ebc8a1e7c2d371db5f8b5ef6b40a1062ee376ae99f915455d24`。
- Notion 原文：本轮 Maton 对该 v0.1 页仍返回 `object_not_found`（未共享给集成）。栖息地按独立验收评论已回源的 Notion §6.7/§7 原文「地铁隧道、工业区地下」修正，并与砌腐墙设计页「常驻地下」一致。

判定：PASS = 代码路径 + 本轮或既有可复现测试；FAIL = 实现不符且本轮可修却未修；blocked = 缺授权资源或产品原文含糊；partial = 代码有路径但证据不覆盖正式包或完整联调。

| 需求 | 判定 | 证据 |
| --- | --- | --- |
| 入城公式 `min(0.02+0.08*floor(t/24000), 0.5)`，合法深睡只抽一次 | PASS | `NightRules.entryProbability`；`cityDeepSleepAndReturnPreservePlayer`；既有双客户端深睡入城 |
| 同夜同一区域、安全街道 | PASS | `CityEntryQueue` 用主世界日数哈希中心；`safeStreet` 31×31 / 无怪 / 固体地面 |
| 木门计数、上下半归一、重复不重抽；铁门仅按钮/拉杆 | PASS | `cityRealPlayerDoorInteractions` |
| 每新门 1% 出口 | PASS | `NightRules.Doors.open` 的 `lucky` 分支；`cityDoorHistoryNbtAndPity` 失败骰不提前出门 |
| 100 门保底 | blocked（产品语义） | Notion/台账原文是「100门保底」，没有写「仅第 100 扇」或「第 101 扇起仍保底」。实现是 `count >= guarantee`。本轮**不改代码**。建议后续若产品定为 pity，则改 `==` 并加第 101 门断言 |
| 共享出口 300s SavedData，穿过回床 | PASS（核心）+ 卸载补关已加真实路径 | `cityWalkingThroughSharedExitReturnsHome`；窗口内不可关见 `cityExitDoorStaysOpenUntilExpiry`。PendingClose：先强制加载并装上已开门、卸载该区块、过期后仍保留记录、重载后关叶，见 `cityExpiredExitClosesWhenUnloadedChunkLoads`。真实双客户端卸载→过期→重启仍不是这条 GameTest |
| 死亡清史、不改原版掉落 | PASS | `cityDeathDropsAndRespawnsAtOwnBed` |
| 分区 55/15/30、地铁、无洞穴 | PASS | `CityLayout` / `cityGeneratedSubwayAndShaftConnect` |
| 城区专属建筑池（mall/市政不进住宅） | PASS（本轮加锁） | 大建筑是街区内部 3×3 地块中的 2×2 区块（32×32）模板。现 `CityTemplates.largeCivic` 仅商业区；`cityDistrictPoolsKeepCivicBuildingsInCommercial` |
| 原版生物禁刷 | PASS | `CitySpawns.naturalSpawn` 取消非缠影子/霓虹瘤/砌腐墙的 NATURAL/CHUNK/PATROL/EVENT；`cityEventInterceptionCoversSpec` 僵尸禁刷 |
| 砌腐墙栖息地 | 规则已改；密度/三怪仍未验 | Notion §6.7/§7「地铁隧道、工业区地下」。`brickrotHabitat` = 各区地铁隧道（`SUBWAY_Y`..`+6` 且生成器走廊）**或**工业区 `y<56`。工业地表和非隧道住宅/商业地下不刷。`cityBrickrotHabitatIsSubwayAndIndustrialUnderground`。禁止把「仅工业区」或旧 `INDUSTRIAL \|\| y<56` 标 PASS。密度/挂壁/三怪联调仍未验，PR #18 未操作 |
| 白面 / 粒子 / 低沉音效 | PASS（代码+既有 e2e） | `CityClientRuntime` 白面；`END_ROD` + `BEACON_AMBIENT`；既有 native 白像素对照。本轮未重跑双客户端 e2e 则不把听觉当新证据 |
| 事件拦截（床/锚/生长/传送门/原版怪） | PASS | `cityEventInterceptionCoversSpec` |
| 窗口门锁（木门点击 / 铁门红石 / `setOpen(false)`） | partial | 开发 classpath 三入口：`cityExitDoorStaysOpenUntilExpiry`（日志 `NIGHT_DOOR_LOCK_RUNTIME`）。`@Group min=3`，SRG `m_153165_`。`verifyRemappedDoorLock` 检查**成品 remap JAR 字节**含 `m_6227_`/`m_6861_`/`m_153165_`。night-e2e 仍是 userdev classpath，**不能把 JAR 字符串或开发态三入口写成正式 remap 进程注入 PASS** |
| 美术授权范围 | blocked（缺包） | 原包仅 `curse_glyph_0..3`，无 32 字形；无医院/警局/消防专用模板（`yuji/buildings/hospital.nbt` 属于雨寂佛都，禁止挪用）。`CityTemplates.available` 现校验 **35** 个已授权结构（12 建筑 + 4 广场 + 12 街道 + 7 地铁） |
| R-1 同夜刷床再抽 | blocked（无明文） | 未改行为 |
| R-2 旁观者穿门 | blocked（无明文） | 未改行为 |
| 入城演出（传送前渐黑、躺姿醒来） | partial | 判定成功后 `CityExitNetwork.fadeIn` 在传送前把客户端拉黑；到达后维度切换渐隐。`finishEntry` 以 `startSleeping` 躺在街道上约 40 tick（`CityArrivalSleepMixin` 避免无床立刻醒来）。`cityArrivalLiesDownOnStreet`。黑屏中文文案仍是到达标题，不是传送前独立黑场文案；原生视觉未在本轮重验 |
| 12 类建筑入口与楼层 | GameTest 入口回归；原生 survey 仍以新 CI 为准 | CI 15c1fc5 原生调查是 **6/12**。失败的 6 处全是商业/市政：4 处准星打在玻璃/砖上且门未开，2 处（mall/library）用模板边缘+门高度当站立点，街道更低所以判定“无接近点”。不是缺模板。`CityBuildingAccess` 按门朝向落到实地面、服务端先开门，GameTest `cityTwelveBuildingEntrancesWalkable` 要求 12/12 入口可站入并用碰撞 BFS 记楼层。原生 12/12 与 `allFloorsVerified` 仍看出 CI survey JSON，不能预标 PASS |

## 本轮代码改动

1. **回程传送**：不再要求门叶 OPEN，也不再丢弃位移 ≥4 格的包。占用门洞体积、线段 clip、原 sign-flip 任一成立即排队 `returnHome`；服务端 tick 补扫访客，避免嵌入连接漏 `PlayerTickEvent`。
2. **窗口门锁**：`CityDoorMixin` 拦截 `use` / `neighborChanged` / `setOpen`（SRG `m_6227_` / `m_6861_` / `m_153165_`）的关门；过期 `activeExits(level, now)` 关叶反馈。开发 classpath 已测三入口；成品 JAR 有字节门禁。正式 remap 进程注入仍缺。
3. **隔离**：每个 eternal_night 用例开头 `isolate()`。`@AfterBatch` → `liveConfig.close()`。成功路径 `cityConfigSandboxRestoresHostValuesAcrossReruns`；失败路径用同一 `restoreConfig` 做注入异常，见 `cityConfigSandboxRestoresAfterSimulatedFailure`。CI **不能**用一个会失败的 GameTest 去证明 Forge 超时 AfterBatch，不把超时注入标 PASS。
4. **砌腐墙**：栖息地为地铁隧道（全区）+ 工业区地下，不是「只有工业区」，也不是旧 `INDUSTRIAL \|\| y<56`。
5. **过期关门**：对已存在的开叶门做强制加载→卸载→过期保留 PendingClose→重载关叶。
6. **入城演出**：传送前渐黑包 + 到达躺姿；见上表 partial。
7. **12 入口**：接近点按实际地面与门朝向；GameTest 12/12 入口回归。
8. **文档**：结构模板 35 项；大建筑是 3×3 内部地块中的 2×2 区块。

## 命令

```bash
JAVA_HOME 指向 JDK17 后
bash ./gradlew --no-daemon --max-workers=2 -PnightE2E checkNightRules verifyCityAssets build writeNightTestClasspath runGameTestServer
python3 tools/night-e2e.py   # 有显示/客户端 classpath 时
```

## 本轮验证（修复 Request Changes 后，SHA 以 git / CI 为准）

本地 JDK 17：

- NightRules 以本次 `checkNightRules` 打印为准（源码仍门禁 `setOpen` / `m_153165_` / `min = 3`）
- CITY_ART_VERIFIED 87 files / 6 loot tables
- Eternal Night GameTest 新增 config-fail / 栖息地 / 到达躺姿 / 12 入口；计数以本次 `runGameTestServer` 为准
- 成品 JAR：`verifyRemappedDoorLock` 必须打印 `NIGHT_REMAPPED_DOOR_MIXIN_PASS`
- 双原生 e2e 与 12 入口调查以新 CI artifact 的 `city-survey.json` 为准，不沿用 15c1fc5 的 6/12

CI 以 GitHub Checks 为准。雨寂都市客户端窗口超时、重力 `hostileHoversThenFallsOutsideCylinder` 悬停高度若偶发失败，与本轮白门/栖息地/PendingClose 路径无直接调用关系，以重跑后的 HEAD 检查为准。
