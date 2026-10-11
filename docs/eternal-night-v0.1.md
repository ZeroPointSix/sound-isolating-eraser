# 永夜都市 v0.1 实现与验收台账

状态：Draft，可审查的服务端实现，不是已验收可发布成品。禁止合并 main、生产发布或资金支出。

- 需求：[Notion v0.1](https://app.notion.com/p/3f6463aed7e6818db813c2b77fbdc21a)
- 原始美术接手资料：[Notion](https://app.notion.com/p/3f6463aed7e68112af9cf05db856abad)
- 任务：[ZER-1398](https://linear.app/zerodotsix/issue/ZER-1398)
- 实现：[PR #20](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/20)
- 分支：`feature/eternal-night-city-20261011`
- 基线：`daef9433175320b67e49f8f0237e96065cbe060e`，MC 1.20.1 / Forge 47.2.0 / Java 17。

## 已确认决策

| 冲突或未决项 | 影响与选择 | 当前处理 |
| --- | --- | --- |
| 需求仅依赖 GeckoLib，旧工程依赖 Curios | 移除会破坏重力玉佩；拆包会扩大范围 | 按原线程明确授权保留旧 Curios，新模块不新增模组依赖 |
| 美术仍使用 qihai 命名空间 | 重写 NBT 或保留原资源键 | 注册 qihai 两个方块，保留原 NBT/贴图/JSON 字节，避免替换资产 |
| 原文 32 字形，原包仅 glyph 0..3、5 色 | 无法以已有原包实现 32 字形 | 仅实现原包属性，不自造缺失字形 |
| 医院/警局/消防站的专用美术缺失 | 使用现有功能建筑或补充原资源 | 仅选择原包 civic_center/townhall 等，不能宣称专用外观已提供 |
| 原文死亡规则待定 | 保留原版死亡或按建议返回 | 可配置默认返回主世界床/出生点并清空计数；不改掉落/keepInventory |
| 缠影子尚未合并 | 直接引入 PR #18 或解耦 | 不合并、不复制；注册 ID 缺失时跳过，禁止落入默认实体 pig |
| 地铁楼梯原包不连续 | 原样会存在不可通行风险 | 按原包 README 允许方案补连续梯子；采用原包四向 split 和连入站台的支线，不改原 NBT |
| 招牌发光12，邻接空气衰减至11 | 按脚下空气阈值会使该招牌无法产生附着霓虹瘤 | 附着点校验同时读取相邻实体支撑面的发光强度；独立验收时确认这一设计解释 |
| 冷世界同步入城阻塞主线程 | 两个真实客户端在生成期间超时，服务端落后约66秒 | 深睡100 tick时唤醒并排队；后台准备原模板与四个落点区块，完成后传送；断线/死亡/超时/停服释放票据 |

## Notion 逐项对照

“已实现”表示代码路径已存在；只有下面验证记录覆盖的项目才算实测通过。

| 需求 | 实现位置与当前情况 | 未完成的验收 |
| --- | --- | --- |
| 固定 18000、无天光/天气/自然光、无日月星云 | dimension_type、三个 biome JSON、CityClient | 真实客户端天空/雾截图 |
| 黑场与到达文案 | CityClientRuntime 黑场渐隐、服务器中文标题 | 客户端视觉测试 |
| 合法深睡只抽一次，失眠天数公式 | NightEvents 睡前快照；NightRules `min(.02+.08*floor(t/24000),.5)`；CityEntryQueue 后台准备不阻塞 tick | Forge 深睡、夜晚不跳过、原床位及背包测试通过；原生双客户端重跑中 |
| 背包、原床位不改 | 只传送；禁止都市设置重生点 | 双客户端往返实测 |
| 同夜同一区域，安全街道，附近无怪 | 同主世界日数确定中心；31x31安全落点区域在24格半径内；碰撞/液体/16格怪物检查；拒绝挖穿街道下方 | Forge 实际落点测试通过；原生测试增加同夜48格范围与服务器响应间隔断言 |
| 三分区 55/15/30、20~30格高差、平地建筑街区 | CityBiomeSource/CityLayout；24格高差；楼块平面与街道坡度 | 原 NBT 接缝、楼梯、跨区块生成实测 |
| 无限人工地表、无自然洞穴/矿/液体/植被 | 专用 ChunkGenerator，不调用原版地形特征/洞穴；模板处理清除自然地表/液体/植物 | 原包内全部方块实际扫描 |
| 固定地下地铁，无动态列车 | Y=40 模板接入；竖井按表面高度堆叠；原包允许的梯子连接 | Forge 跨72格东西线、65格南北线、四向路口、站台和连续梯子断言通过 |
| 每栋1~4容器，第一次开启生成装备战利品 | 按世界种子选择原模板候选箱；清空原 Items 并写 LootTable/LootTableSeed；六表无食物/水/药水 | Forge 实际生成32x32建筑容器数、开箱装备、清空后不补货及NBT恢复通过 |
| 木门手动打开、上下半块归一、重复不重抽 | 实际 BlockState.use 作用域及 Level.setBlock 前后迁移；LongArray 玩家 NBT | Forge 上半门、下半门、重复打开及拉杆铁门通过；修复 use 参数被原版覆盖造成漏计的问题 |
| 铁门仅归因按钮/拉杆 | 同步红石和由该动作安排的计划 tick 传递身份；没有近邻猜测 | 延迟器交互测试；超过200 tick或重启中断的线路保守不计数 |
| 每新门1%、100门保底、计数与提示 | NightRules/NightEvents；25/50/75/90提示；NBT跨登录/克隆；死亡标记持久化 | 重启前后实机存档复测 |
| 出口共享5分钟、穿过返回个人床/出生点 | CitySavedData 到期gameTime；线段过门；服务端主动同步共享白色门面，辅以原版白色粒子和低沉音效 | NBT保存/到期边界及穿越几何通过；原生双客户端白面负对照与共享返床重跑中 |
| 出去清空计数，死亡规则 | returnHome/Clone/Respawn；不改原版掉落规则 | Forge 真实伤害死亡、7颗钻石一次性掉落、个人床重生及计数清空已通过一轮；随机床位地形干扰修复后重跑 |
| 缠影子、霓虹瘤独立密度 | CitySpawns 自有生成与配置；亮度/墙面/分区条件；霓虹瘤大体型整街区上限 | 实际怪物分布与挂壁视觉；缠影子依赖 PR #18 |
| 砌腐墙每512x512区最多一只、持久 | 世界 SavedData UUID 预留；实体持久；死亡释放，卸载不释放 | 跨区、重启、自然生成实测 |
| 禁原版自然生物、袭击、地狱门；床不爆炸但不能睡；锚无效 | 无生物群系生成表；拦截原版自然/事件生成；has_raids=false；PortalSpawn/睡床/重生点事件 | 原版特殊生成器兼容回归 |
| 时钟/指南针旋转、鞘翅/珍珠不改 | 非 natural 非主世界维度沿用原版物品行为 | 客户端实测 |
| 不长植物 | CropGrow/SaplingGrow/BoneMeal及方块随机tick拦截 | 所有可种植物及骨粉路径回归 |

## 美术原包与来源

指定原包 `eternal_night_city_kit.zip`，大小 **2,759,557 字节**。

SHA256：`9dab6be385742ebc8a1e7c2d371db5f8b5ef6b40a1062ee376ae99f915455d24`。

2026-10-11 用户在原 Slack 线程上传附件 `F0C8CDACQ4S`；实际下载的大小与 SHA256 均匹配。提交 `7c639c794ddd2fa11d0db78139c1dea9da900c2c` 导入35个NBT、22张贴图、29个模型/方块状态JSON及MIT声明，共87个原始运行资源。原包另含结构统计JSON和预览，未冒充游戏截图。

历史下载阻塞已由用户上传解除。原始ZIP保留在开发机 `/data/eternal-night-city-20261011/assets/eternal_night_city_kit.zip`，运行资源的87项SHA校验已通过。

可复现的导入与校验命令：

```bash
python3 tools/import_city_art.py /authorized/path/eternal_night_city_kit.zip
python3 tools/verify_city_art.py
```

导入脚本先核对ZIP整体大小/SHA、路径安全、35个NBT、22张PNG和MIT文件，再复制原始字节；遇到已有不同文件直接失败。清单写入 `src/main/resources/data/sound_isolating_eraser/eternal_night_art_manifest.json`，记录每个资源的SHA。

`check` 强制依赖 `verifyCityAssets`，缺失原资源时不能声称 build 通过。自然睡眠入城也要求12栋原建筑模板存在，避免把玩家送进未接入的地图。不能绕过门禁来发布。

## 验证记录

执行环境：用户既有开发机 kuoya-sgp-002；独立目录 `/data/eternal-night-city-20261011/repo`；无生产部署。

| 提交 | 命令 | 实际结果 |
| --- | --- | --- |
| a2c352d | `./gradlew --offline --no-daemon compileJava checkNightRules` | 失败：旧依赖 mclib:20 未在所选缓存中；未改依赖，在线补齐 |
| a2c352d | `./gradlew --no-daemon checkNightRules compileJava` | BUILD SUCCESSFUL，NightRulesCheck 666323项断言 |
| 7e54914 | `./gradlew --no-daemon checkNightRules compileJava runGameTestServer` | 规则666325项断言和编译通过；GameTest结果待写入证据提交 |
| 8917fe7 | `./gradlew --offline --no-daemon -PnightOnly -PnightE2E checkNightRules verifyCityAssets build writeNightTestClasspath runGameTestServer` | 规则666325、原包87文件/6表、Wings1470及JAR构建通过；Forge 7/8，嵌入连接未驱动玩家tick导致睡眠失败，后续已修复 |
| 5c353ab | GitHub Actions `server-regression` | [155/155通过](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38132743582/job/114450268393)，含10个城市测试；另一流水线两项既有砌腐墙场景失败，不隐去失败记录 |
| 8917fe7 | `python3 tools/night-e2e.py` | 两个真实客户端首次进入均超时；服务端落后66888ms。原始失败日志已保留，促成非阻塞入城修复 |
| 3858fa5 | GitHub Actions `server-regression` | [155/155通过](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38134243150/job/114454791007)，含异步入城；另一流水线出现随机地形遮挡测试床位，已补清理与前置断言 |
| 3858fa5 | 开发机同上构建/聚焦测试命令 | 规则、美术、编译及构建通过；9/10城市测试通过；复用世界中的旧红石块影响物品放置fixture，已补重置；深睡异步入城通过 |

运行修复与证据边界：`CityEntryQueue` 显式绕过 Forge `getChunkFuture` 的主线程 `managedBlock` 分支。验证代码属于 `-PnightE2E` 的独立 source set，不进入交付JAR。测试通过原生 `xdotool` 点击床/开门、按W过门；仅将入城概率设为100%、预置99条门历史以确定性覆盖边界，不替代实际交互。白面截图同时渲染“只有粒子、隐藏白面”的负对照，不能用粒子白点冒充完整出口。无声设备使用空音频后端，不声称听觉验收完成。

独立审查已覆盖并修复：未注册shadow回退猪、10001门写入后无法读档、近邻按钮错误归因、同tick开门覆盖、死亡重启丢失标记、远处出口强制加载、大型霓虹瘤漏计、同夜范围超64格，以及计划tick提前消费身份。追加实际玩家交互回归，不把静态审查当作运行通过。

## 完整验收仍需

1. 原生双客户端修复后重跑结果与截图写回本页；完整Forge复测确认重复世界/随机地形fixture稳定。
2. 实际进程重启恢复、全建筑可达性、所有特殊原版交互及怪物分布/附着视觉仍需专门验收；NBT读写测试不冒充进程重启证据。
3. PR #18 经独立流程准备就绪后的三怪联调；本PR不合并它。
4. 32字形与专用医院/警局/消防外观需要补齐经授权原资源；现有原包仅4字形及README允许的通用建筑，不自造补齐。
