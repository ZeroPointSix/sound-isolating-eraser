# a44-a51: 合并后的贪欲修复

## 范围与恢复来源

- 原实现 [PR #21](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/21) 已于 2026-10-13 02:55:46 UTC 由维护者合并；最终 HEAD `f8af45cbba594760776cdab10de32875fed285af`，合并提交 `cbdb7ccaaaccbcf4292b0da5849cbd6d453d1ca6`。
- 本轮基线为 main `2f30359d6e71332ca3c258f42ba55c195a0d2c4a`，包含 PR #22 城市流程。分支 `fix/yuji-navigation-reload-followup-20261013`；按用户明确授权新建后续 Draft PR，不合并 main、不发布、不部署。
- a51 于 2026-10-13 09:48 UTC 重新 fetch 核验 main 仍为上述 SHA，直接复用 a44 工作树和下面两笔提交。未重建十怪、未覆盖原 a23 工作树、未执行 main 合并。
- 贪欲代码提交为 `c17aea16449955df81891b5e704729941a66143a`；城市测试夹具修复提交为 `f00f2698ca6a632dae4598469aec00e3f1d60b57`。最终受测源码校验值见 [source.sha256](local/source.sha256)，修复前校验值见 [before-city-source.sha256](local/before-city-source.sha256)，精确代码增量见 [tested-code.patch](local/tested-code.patch)。
- 直接恢复已验证的 a42 补丁，不重新实现十怪。原工作树及归档保留；恢复包 SHA-256 为 `4fba2c17dfe16654036b52a1695be241ff24e6be9b7367761fe125efb5e44125`。
- [原始补丁](history/uncommitted-fixes.patch)、[旧基线 SHA](history/baseline-commit.txt)、[旧源码校验值](history/source-before.sha256) 仅用于溯源，不应整体重放到新 main：旧版本号 0.9.1 已过期，当前构建版本为 0.10.2。

## 增量

1. 贪欲导航使用原生 `Path.getNextEntityPos` 的实际移动目标判断到达，沿用原版水平容差及垂直阈值。解决精确相等补丁遗漏微小偏移的问题；保留卡住检测、碰撞、游泳，不传送或跳过远节点。
2. 读取贪欲 NBT 后重新应用已限制的层数属性，避免原版加载 `Attributes` 恢复旧的最大生命/攻击值。不恢复满血，保留已受伤生命，兼容缺少 `Attributes` 的旧存档。
3. 保留新 main 的白袍 NBT 和城市怪群逻辑，仅解决存档附近的文本冲突。未修改 Shadow、施肥、床位、战斗伤害时点或客户端截图阈值。
4. 恢复五项正式 GameTests：`yujiConfiguredGrowthCapSurvivesReload`、`yujiConfiguredPullStrengthControlsVelocity`、`yujiConfiguredBetrayalTargetsAndBuff`、`yujiConfiguredLegacyCurseDamageCap`、`yujiGreedNearWaypointAdvancesWithoutSkippingDistantNodes`。配置使用现有 sandbox，退出恢复；不修改真实 TOML。
5. 修城市 `cityBuddhaRespawnsAfterRemoval` 的测试前提：单次 `getChunk` 不能保证空维度中的实体区块在后续重试仍保持载入。测试重试时刷新专属、40 tick 过期的区块票据，成功后释放；保留原 1200 tick 时限、生成/替换/唯一性/不重复生成断言。生产 `ensureBuddha` 的载入保护和游戏机制不改。

## 证据分层

| 受测版本 | 结果 | 原件 |
| --- | --- | --- |
| a39 未修复生产代码 + 新回归 | 50/52，退出 1；近节点停滞、降上限后最大生命 42 而非 34 | [失败日志](../a39-recovery/local/a39-near-waypoint-before.log)、[退出码](../a39-recovery/local/a39-near-waypoint-before.exit) |
| f8af45c + a42 补丁 | 专项 52/52、构建成功、退出 0 | [专项](../a39-recovery/local/a42-focused-after.log)、[退出码](../a39-recovery/local/a42-focused-after.exit) |
| f8af45c + a42 补丁 | 全量 252/252、构建成功、退出 0 | [全量](../a39-recovery/local/a42-full-after.log)、[退出码](../a39-recovery/local/a42-full-after.exit) |
| f8af45c + a42 补丁 | 愚痴/三层贪欲/十佛头真实客户端通过、退出 0；10 FPS、700 ms 帧延迟、1600 ms 咬击停顿、实际 OpenAL 混音 | [客户端](../a39-recovery/local/a42-client-after.log)、[退出码](../a39-recovery/local/a42-client-after.exit) |
| main 2f30359 + 本轮增量 | 专项 74/74（雨寂 52 + 城市 22）、构建成功、退出 0 | [专项](local/a44-focused.log)、[退出码](local/a44-focused.exit) |
| main 2f30359 + 贪欲增量，城市夹具修复前 | 全量 273/274，退出 1；仅 `cityBuddhaRespawnsAfterRemoval` 失败，完整运行 35 分 55 秒 | [失败原件](local/a44-full-before-city.log)、[退出码](local/a44-full-before-city.exit) |
| main 2f30359 + 贪欲增量及城市夹具修复 | 专项 74/74、构建成功、退出 0 | [专项复测](local/a44-focused-after-city.log)、[退出码](local/a44-focused-after-city.exit) |
| main 2f30359 + 贪欲增量及城市夹具修复 | 全量复测因同机内存争用中止，Java 退出 137、Gradle 退出 1；内核确认 OOM，未计通过 | [中止原件](local/a44-full-after-city-oom.log)、[退出码](local/a44-full-after-city-oom.exit)、[内核记录](local/a44-oom-kernel.log) |
| 相同源码，等待内存回落后新世界重跑 | **274/274，退出 0，构建成功，31 分 44 秒**；测试完成 2026-10-13 07:03:20 UTC，a51 回收核验原件 | [完整日志](local/a44-full-final.log)、[退出码](local/a44-full-final.exit) |

a51 核对现存四个受测文件与运行时保存的校验清单完全一致，没有把旧 252 项结果外推到含城市的新 main。完整 274 项日志 SHA-256 为 `0c2e047821b7bf621b15f780033e3908a896dceccfc1dcf6cbd07d93eb7d3427`。新增专项复跑于 09:49 UTC 开始；在取得退出文件前不计通过，其终态和同 HEAD CI 链接追加到后续 Draft PR。

历史客户端记录：三层贪欲自然移动 8.8228595643 格，对真实玩家造成 5 次伤害；十佛头实际 11 帧、前景 49156 像素、边界 297,125,666,419，铁傀儡自然受伤两次共 120。仅三个场景，不是十怪全量客户端复测，更不是人工听音或真人多人签收。

前序失败、退出 137 的集成尝试也保留在 [a39 恢复目录](../a39-recovery/README.md)。本轮使用独立工作树和新世界，保存每轮世界而非复用或销毁用户存档；专项、全量串行执行，避免内存争用。校验值见 `SHA256SUMS`。

2026-10-13 06:33:07 UTC，宿主机内核杀死本轮测试 JVM PID 2690955；当时同机另有重型 Java 任务，7.9 GiB 主机无 swap，终止前可用内存约 217 MiB。记录原始线程栈及内核证据，不把进程被杀解释成断言失败，也不省略这次中止。下一轮等待可用内存至少 5,500,000 KiB 持续 15 秒后启动，源码、测试范围、断言和时限均不改变。

## 十怪对照

需求来源：[图鉴 v0.2](../../../../art/yuji_fodu/monster_cards.md) 及 [F1-F8 已确认调整与完整矩阵](../../../yuji-acceptance.md)。以下描述现有实现与本轮变化，不代表全部需求已签收。核心入口为 [YujiMonster](../../../../src/main/java/com/zeropointsix/eraser/yuji/YujiMonster.java)、[YujiGameTests](../../../../src/main/java/com/zeropointsix/eraser/gametest/YujiGameTests.java)。

| 十怪 | 已有机制 | 本轮变化 / 证据边界 |
| --- | --- | --- |
| 愚痴 | 不主动攻击、跟随、残躯爬行、门碰撞 | 未改；保留城市白袍状态读取 |
| 杀生 | 冲锋与第 6/12 tick 双斩 | 未改时点、基础每击 3 或众叛每击 3.9 |
| 偷盗 | 盗取、逃跑、携物 NBT、一次归还 | 未改；保留真实库存及护甲保护测试 |
| 恶口 | F5 远程念咒、受伤爆刺 | 仅补旧咒缚配置兼容回归，不混作新念咒覆盖 |
| 妄语 | 目标独占假声、距离控制、虚弱清理 | 未改；历史双端隔离不冒充本轮重测 |
| 两舌 | 众叛、宠物/村民/Panda、增益和恢复 | 补开关及倍率组合、交易取消、不叠加和状态恢复测试 |
| 邪淫 | 巨型、迷魂、拉拽、转向及挤压 | 补拉拽倍率 0/0.5/1/3；关闭转向配置的双端切换仍未验证 |
| 绮语 | 四增益、200 tick 召唤、32 格同 owner 上限 4、箫声衰减 | 未改当前参数；不回滚历史派单数值 |
| 贪欲 | 成长、减伤、剥皮、吞噬、追击及携物存档 | 修近路径点和降上限读档；0-12 层、近/远对照、不传送及生命保留测试 |
| 嗔怒 | 首攻击者记仇、死亡/超过 128 格清除 | 未改；城市怪群额外索敌逻辑原样保留 |

配套十佛头与骨刺保留，不计入十怪数量：30 x 40 Boss 身体、12000/260/60 属性、五倍模型；骨刺真实墙碰撞和 200 tick 到期。此次未改模型或伤害。资源校验覆盖 12 套模型/动画、11 枚蛋/掉落表、3 效果、19 粒子、47 声音及 47 Ogg。

## 复现与签收边界

```bash
python3 tools/yuji/validate_assets.py
_JAVA_OPTIONS=-Xmx3G GRADLE_USER_HOME=/data/minecraft-models/gradle-cache \
  bash ./gradlew -PyujiOnly runGameTestServer build --offline --no-daemon --max-workers=1 --stacktrace
# 下一轮在独立新世界执行，保留上一轮的 run/world。
_JAVA_OPTIONS=-Xmx3G GRADLE_USER_HOME=/data/minecraft-models/gradle-cache \
  bash ./gradlew runGameTestServer build --offline --no-daemon --max-workers=1 --stacktrace
python3 tools/yuji/verify_gametest_log.py build/a44-full-final.log
```

CI 链接、a51 专项复跑及最终 HEAD 由后续 Draft PR 正文和评论记录。原 #21 的 11/11 成功只验证旧提交，不替代本分支 checks；运行中、失败、超时均不写成通过。GameTest 不是客户端 E2E，自动操作的真实客户端不是人工游玩。

未签收：新 HEAD 客户端复测、人工听音、真人多人、任意复杂地形/全部中立物种、两名不同 Agent 的独立 Review，以及相对原十怪需求的最终功能性验收。本执行者参与实现，不提供独立 Review 结论。后续审查应固定本 PR HEAD/Jar，逐项对照完整矩阵和原始日志。
