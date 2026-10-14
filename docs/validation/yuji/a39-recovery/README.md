# a39-a42 接续修复与验证

> 历史恢复记录：正文保留前序调查时的状态，不作为当前 PR/CI 结论。a44 已核实 a42 专项 52/52、全量 252/252、三场景真实客户端均退出 0，并获准创建后续 Draft PR。完整新增日志见本目录 `local/a42-*.log`；当前 main 上的迁移及复测见 [a44 记录](../a44-followup/README.md)。

## 固定来源

- 恢复来源：[PR #21](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/21)，分支 `feat/yuji-fodu-monsters-20261011`。GitHub API 在 a42 核验到该 PR 已由 `ZeroPointSix` 于 **2026-10-13 02:55:46 UTC** 合并，合并提交 `cbdb7ccaaaccbcf4292b0da5849cbd6d453d1ca6`。它已不是 Draft，不能将新修复描述为更新了仍开放的 PR；本轮没有执行合并或发布，后续 PR 的交付方式待用户确认。
- a39 恢复源为 `b42552aeee374b704ec2b30500cc619bd59dc05a`。a42 已保留未提交回归和证据，快进到原分支最新 `f8af45cbba594760776cdab10de32875fed285af`，不是派单中的旧 `e69e6a0b2512b09b9638a989029a263c5fe548b1`。两份相关 Java 源文件在 b42552a 与 f8af45c 间没有变化；没有重新实现十怪或创建 main 合并提交。
- `e69e6a0` 已修复连接 tick 返回床位坐标被覆盖、施肥配置文件监听竞争、十佛头展示相机状态丢失。
- `b42552a` 修复了三层贪欲精确到达路径点后停滞。原生导航的两种中心偏移计算在宽度 `0.953999936580658` 时相差浮点边界：距离 `0.47699999809265137` 大于容差 `0.476999968290329`。旧修复只消费精确到达的原生目标点；本轮另加近节点和远节点对照，不能据旧精确坐标测试宣称附近坐标也已覆盖。
- 卡顿时贪欲咬击声音仍由服务端第 12 action tick 发出；本轮不撤销该修复，也不改伤害、截图、时间或数量门槛。
- 新增四个正式配置 GameTests，覆盖六个既有配置项的非默认行为及恢复。临时修改使用 `GameTestConfigs.sandbox`，不写真实 TOML；`cameraTurn=false` 的真实双端切换仍列为未覆盖，不拿静态代码当实测。

## 本地结果与监控故障分开计数

| 固定受测代码 | 原始结果 | 证据 |
| --- | --- | --- |
| e69e6a0 加路径点回归，未修导航 | 46/47，三层路径点断言失败 | [原始失败](local/a32-waypoint-before.log) |
| b42552a 的导航主体，尚未追加游泳断言 | 47/47 | [导航复测](local/a32-waypoint-after.log) |
| b42552a，原工作树全量 | 204/204，构建成功，30 分 21 秒 | [完整日志](local/a32-full-serial-after.log) |
| b42552a，a36 独立新世界全量 | 204/204，构建成功，37 分 41 秒；真实退出码 0 | [完整日志](local/a36-full-fresh.log)、[退出码](local/a36-full-fresh.exit) |
| b42552a，原生客户端专项 | 愚痴、三层贪欲、十佛头三个场景通过；10 FPS、700 ms 帧延迟、1600 ms 咬击停顿；实际混音 | [完整日志](local/a32-waypoint-client-after.log) |
| b42552a 加四项配置回归，未改实现 | 50/51；降低成长上限后重载，最大生命 42 不等于 34；真实退出码 1 | [原始日志](local/a39-config-focused.log)、[退出码](local/a39-config-focused.exit) |
| b42552a 加五项回归，未改实现 | 50/52；同一重载失败，另有近路径点偏移 -0.0001 时节点不前进；真实退出码 1 | [原始日志](local/a39-near-waypoint-before.log)、[退出码](local/a39-near-waypoint-before.exit) |
| GitHub 旧集成快照加 PR23 暗影夹具候选 | Java 进程被信号终止，退出 137，Gradle 退出 1；没有全量通过结论 | `build/a39-integrated-candidate.log` 与 `.exit`，待归档 |
| f8af45c 加 a42 实现修复及五项回归 | 专项与构建执行中，不计为通过 | `build/a42-focused-after.log`，结束后归档 |

204 项包含 47 项雨寂；GitHub 集成快照包含额外上游模块，共 219 项。这两个分母不可互换。204 项通过不表示 219 项通过。

两次远程监控在测试结束后报 `SSH connection error: read ETIMEDOUT`，不能冒充 Gradle 非零退出。a36 有独立 `EXIT_CODE=0` 文件；a32 仅有完整 `BUILD SUCCESSFUL`，不为它编造退出码。原始日志全部保留，不删除失败或只摘最后一行。

配置回归定位到真实缺陷：原版 `readAdditionalSaveData` 会在层数已按新上限设置后恢复旧的属性基值，导致层数与最大生命、攻击属性不一致。修复在原版加载后重新应用层数属性，不恢复满血，并检查旧格式缺少 Attributes 的存档。最终复测结果将在结束后补入本页。待执行或仍在运行的用例不计通过。

近路径点回归定位到第二个实现缺陷：移动目标使用 `Path.getNextEntityPos` 的宽度取整中心，到达判断却使用另一套未取整中心；精确相等补丁不能覆盖真实移动的微小偏移。a42 改为在原生水平半径与垂直阈值内判断实际移动目标，不扩大半径、不传送、不跳过远节点，并保留原版卡住检测、转角回退和游泳支持。回归覆盖 0-12 层、X/Z 轴、正负近偏移和正负 2 格远偏移，每次最多推进一个节点，实体坐标必须不变。

a42 受测源码 SHA-256：`YujiMonster.java` 为 `8c331d902edbce74a73f36a25b3110cf5c400a47e83be6b0ece6b85d66bef749`；`YujiGameTests.java` 为 `1bc6f02820b9335c95f481c12e9b106aeac24088dad7976de7f5a30e9150335b`。版本按仓库规范由 0.9.0 增加至 0.9.1，仅构建验证，不发布。

## 十怪六维需求对照

依据：[原始图鉴 v0.2](../../../../art/yuji_fodu/monster_cards.md) 与 [已确认 F1-F8 调整](../../../yuji-acceptance.md)。以下列的是已落地实现；未承诺的逐怪配置不凭空新增。所有实体注册于 `qihai`，共同由 `YujiContent`、`YujiKind`、`YujiMonster` 实现。声音、网络与效果分别见 `YujiClient`、`YujiNetwork`、`YujiAfflictions`。

源码入口：[实体/AI](../../../../src/main/java/com/zeropointsix/eraser/yuji/YujiMonster.java)、[七项配置](../../../../src/main/java/com/zeropointsix/eraser/yuji/YujiConfig.java)、[效果与交互](../../../../src/main/java/com/zeropointsix/eraser/yuji/YujiAfflictions.java)、[GameTests](../../../../src/main/java/com/zeropointsix/eraser/gametest/YujiGameTests.java)、[真实客户端场景](../../../../src/yujiTest/java/com/zeropointsix/yujitest/YujiTestServer.java)。

| 怪物 / 实体 | AI | 音效与隔离 | 交互 | 配置 | 测试证据 |
| --- | --- | --- | --- | --- | --- |
| 愚痴 `yuchi` | 无主动伤害、跟随/招引、低血爬行 | 普通位置声源，非定向；步声/呼吸/死亡 | 站立与残躯均被关门阻挡，开门后自然通过 | 无独立配置要求 | `yujiPassiveAndGliderSafety`；逐怪 AI/音频；双端关门/开门场景 |
| 杀生 `shasheng` | 冲锋、左右连斩第 6/12 tick | 普通位置声源；连斩/死亡 | 基础每击 3；众叛实际每击 3.9 | 受 `betrayed.hostileBuff` 影响 | `yujiDoubleSlashDealsTwoTimedHits`、`yujiBetrayalBuffAffectsBothTimedSlashes`；双端真实伤害 |
| 偷盗 `toudao` | 盗取后逃跑 10 秒 | 无脚步；偷盗/尖叫/死亡为位置声源 | 附魔/稀有优先；不偷护甲；携物 NBT 保存并只归还一次 | 无独立配置要求 | `yujiTheftPriorityAndArmor`、`yujiStolenStackReloadAndNoLootRule`；真实背包盗取 |
| 恶口 `ekou` | F5 远程念咒第 15/32 tick 各 6；受伤爆刺 | 念咒/爆刺/死亡为位置声源 | 24 格起手；26 格与视线复查；骨刺实际碰撞 | `cursed.maxDamage` 仅控制保留的旧咒缚兼容，不控制新念咒 | `yujiEkouChantHitsFromRange`、`yujiEkouChantSurvivesExpiredCooldown`、新增旧咒缚配置回归；真实远程命中 |
| 妄语 `wangyu` | 墙后拟声、保持距离、虚弱升级 | 假声只送目标玩家；两客户端互换目标，旁观者为 0 | 来源清理、多来源与外部虚弱保留；牛奶可解 | 无独立配置要求 | 三项 `yujiWeakness*`；两客户端实际声音收到及激活证据 |
| 两舌 `liangshe` | 众叛、临时目标及加成 | 低语等普通位置声源，非定向假声 | 宠物/村民/敌怪/Panda；增益不叠加；归还原目标/驯服状态 | `betrayed.affectPets`、`affectVillagers`、`hostileBuff` | `yujiBetrayal*`、新增配置组合；Panda 对真实玩家造成 6 点伤害；未穷举全部物种 |
| 邪淫 `xieyin` | 三倍模型、1.8 x 6.3 身体；迷魂拉拽，不直接近战 | 诱导声为空间声源；转向包只发受影响玩家 | I-III、潜行降级、挨打/离源清除、双侧高墙挤压 | `enthralled.pullStrength`、`cameraTurn` | `yujiEnthrall*`、挤压与新增倍率测试；双端转向隔离/清理已测；关闭转向配置的双端切换未测 |
| 绮语 `qiyu` | 吹箫四增益；200 tick 尝试召唤 1-2 只，32 格同 owner 存活上限 4 | 箫声跟随/退出；32 格其他敌声衰减 70% | 召唤种类限制、空位补充、owner UUID 存档；高处偏好/滑降 | 无独立召唤频率/上限配置要求；当前值为实现选择 | 四项 `yujiQiyu*`；真实自然增益、首波/补位、音量 0.3 和循环退出 |
| 贪欲 `tanyu` | 进食成长、吞噬最近同伴、原生追击；精确路径点已修，近点边界正在复测 | 咬击服务端定时，停顿后实际播放；普通位置声源 | 最多 12 层、减伤、重击剥皮、物品与生命存档；降低配置上限后读档属性修复正在复测 | `tanyu.maxLayers` | `yujiGreed*`、新增配置重载；0-12 层精确/近/远路径点；此前三层真实追击/5 次伤害/卡顿混音 |
| 嗔怒 `chennu` | 中立；首名玩家仇恨不被后来者覆盖 | 踩踏/吼叫/死亡等位置声源 | 原目标死亡或严格超过 128 格才清除；存档后保持 | 无独立配置要求 | 记仇/重载/第二攻击者/128 格边界 GameTests 与两真实客户端 |

### 范围内配套能力

| 对象 | 已落地与证据 | 尚未覆盖 |
| --- | --- | --- |
| 十佛头 `shifotou` | 5 倍模型、30 x 40 真实身体、12000 HP/攻击属性 260/护甲 60/韧性 20；自然攻击玩家和铁傀儡；接触每秒 10；大体型边界 GameTests；完整展示截图；正常重力/碰撞下直墙和内拐角整身越障、顶檐阻挡 | 任意复杂地形组合、人工动作观感；属性 260 不等于每种技能都造成 260 |
| 骨刺 `bone_spike` | 飞行 1.4 倍、插墙 1.7 倍、轨迹/命中特效；真实墙碰撞、200 tick 消失；飞行/插墙独立图和同机位对照 | 人工视觉评价；没有虚构独立飞行循环声 |
| 共同资源 | 12 套模型/动画、11 枚蛋及明确空掉落表、3 效果、19 粒子、47 声音/Ogg；双语键和真实地板碰撞 | 不新增自然刷新、世界生成、黑雨或 Boss 召唤条件 |

现有核心实现已可逐项定位，但这不等于全部需求已人工验收。静态存在、GameTest、自动操作的真实客户端和真人游玩是不同证据层级。

## 当前 CI 与历史失败边界

- a42 核验原 PR 最终 HEAD `f8af45c` 的 **11/11 checks 成功**：[雨寂验证及客户端](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38337709089)、[夜世界](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38337709086)。这是修复前的既有 CI，不包含新增的近路径点与配置回归，不能据此宣称 a42 新修复已获 CI 验证。

- b42552a 的历史 11 checks 为 **9 成功、2 失败**。两个失败各为 **218/219**：`shadowescapesbrightarea`。
- [雨寂全量失败原作业](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38311803514/job/115019051274)；[药丸全量失败原作业](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38311803516/job/115019050575)。原件保存在 `ci/`，没有重跑刷绿或改用仅测 PR 分支掩盖失败。
- [此前真实客户端成功](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38311803514/job/115019050980)；[原始制品](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38311803514/artifacts/11775476949)；[夜世界回床/重启/死亡重生](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38311803472)。
- GitHub 受测合成快照为 `086b34b22b321746176b299d3ebfbfd1cc279d3e`，父版本为 main `193523fc5c317225dee4cbf98d0c0886fdb4aba0` 与 PR b42552a。本轮只在隔离目录检出该已存在快照，不执行 main 合并。
- 暗影源码不在旧的纯 PR b42552a 分支，在 f8af45c 中已经包含。PR #23 的既有夹具修复记录真实抵达暗处的时刻，而非要求冷却后随机巡游的怪物在第 300 tick 仍处于暗处；近战夹具固定第 26 tick 选择自己的活目标，保留出生锁、5 tick 前摇、20 tick 间隔及第 130 tick 至少三次命中。该 PR 已由其他执行者于 2026-10-13 02:33:32 UTC 合并，合并提交为 `a4ff5ca1466475d087294b70900326ff2aad0fd7`。a42 直接复用现有最新分支，不重复搬入模块或执行 main 合并。

## 可复现入口

恢复后的原分支在专用测试工作树执行，保留旧世界与失败文件，不对用户存档使用清理命令。以下是专项和全量入口；只有完整日志与退出记录已确认的运行才计入结果：

```bash
python3 tools/yuji/validate_assets.py
_JAVA_OPTIONS=-Xmx3G GRADLE_USER_HOME=/data/minecraft-models/gradle-cache \
  bash ./gradlew -PyujiOnly runGameTestServer build --offline --no-daemon --max-workers=1 --stacktrace \
  > build/a42-focused-after.log 2>&1
_JAVA_OPTIONS=-Xmx3G GRADLE_USER_HOME=/data/minecraft-models/gradle-cache \
  bash ./gradlew runGameTestServer build --offline --no-daemon --max-workers=1 --stacktrace \
  > build/a42-full-after.log 2>&1
python3 tools/yuji/verify_gametest_log.py build/a42-full-after.log
bash ./gradlew -PyujiE2E writeYujiTestClasspath --no-daemon
YUJI_QA_PROFILES=yuchi,tanyu,shifotou YUJI_QA_CLIENT_FPS=10 \
  YUJI_QA_FRAME_DELAY_MS=700 YUJI_QA_STALL_BITE=1 YUJI_QA_RECORD_AUDIO=1 \
  python3 tools/yuji/e2e.py
YUJI_QA_ISOLATION=1 YUJI_QA_RECORD_AUDIO=1 python3 tools/yuji/e2e.py
```

环境不共享测试进程；先结束一轮服务器/客户端再开始另一轮。用完整日志及独立退出文件判断完成，不根据 SSH 状态、`.pass` 文件数量或进度截图推算 GameTest 数量。

## 后续验收入口

1. 同一最终 HEAD 的本地全量和全部 CI 通过后，再启动不同执行者的独立 Review；本轮不代做。
2. 人工听音使用官方制品中的各客户端 `*-mix.ogg`，逐项确认声源、响度、循环退出和咬击卡顿后的可听性；自动 OpenAL 播放不替代听感签收。
3. 真人双端测试从 `/summon qihai:<实体 ID>` 与既有隔离场景布置开始，交换目标/旁观者，检查妄语声音、邪淫转向、关门以及复杂攀墙组合；结果绑定同一 Jar/HEAD。
4. 继续补 `cameraTurn=false` 的实际双端切换和全物种/任意复杂地形边界。当前不宣称完整 GOAL、全部场景或真人 E2E 已通过。
