# a55 肥料夹具实体隔离回归

## 复用与范围

基线为 main `64d53c12dce56c9f16db79304d37c32ab3f18c5c`，包含 #25 的 `22ea4a50b8d70000f577b2c96cbe45ea20379e91` 及外部合并 `a9d905cd9031a3fd939b0fc07ff8dc96f2a5ddad`。#21 和 #25 均不再追加提交。本轮只修改肥料 GameTest 夹具、增加两项正式回归及更新开发版本号；不修改肥料或十怪生产行为。

## 原始失败与证据界限

[CI 首次执行](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38510873989/job/115678837206) 为 312/313、exit 1。失败用例是 `fifteenTreeGroveIndependentDosesPersistenceAndThreeTiers`，断言为 `tier three expands the trunk and canopy`，并非旧的施肥阈值配置断言。

同一 CI 后续无补丁重跑两轮均 313/313。此次未修复基线本地完整运行也为 321/321、exit 0；原用例在种子 0-71 下均通过。这是偶发性诊断证据，不能据此声称原失败已经解决。种子 0-7 是 8 项包装 GameTest，种子 8-71 是 1 项 GameTest 内的 64 个参数场景，不虚报为 72 项正式测试。

历史失败日志没有记录拒绝原因、碰撞对象或对应世界，不能唯一反推出当次 CI 中的具体干扰对象。本轮确证并修复的是下面这个可重复触发同一断言的夹具缺陷，仍保留该历史归因限制。

## 可重复的缺陷与最小修复

GameTest 放置结构与执行测试方法不是同一步。林地夹具清理了旧 SavedData 和土壤，但没有在场景开始时排除进入场地的非玩家实体。三阶新增的 5 x 5 树干若占据实体位置，`GrowthPlan.commit` 会按原有安全规则拒绝整个事务。

负对照在相对原树根 `(2, 1, 2)` 的未来三阶树干区域放入真实实体，关闭其 AI 和重力以固定位置。旧夹具下，一阶、二阶及原有全部先行断言通过，三阶稳定失败，诊断记录 `FERTILIZER_ENTITY_COLLISION` 与实际位置。两项探针中 1 通过、1 失败，exit 1。

修复仅在 `prepareGrove` 开始时，对树根周围 30 格测试区域的非玩家实体调用 `discard`，不碰其他测试场地或玩家，不改变产品代码的碰撞、建筑、领地与树干所有权保护。第一轮尝试使用原版 `killAllEntities`，原三阶断言通过，但实体仍处于死亡过程，新增“已经移除”断言失败；该失败日志保留，最终使用立即移除而非放宽断言。

两项正式回归：

- `groveFixtureIsolatesStrayEntities`：插入干扰实体后完整重用原用例，全部成长、15 棵独立身份、剂量、NBT、建筑保护及上限断言保留，并检查干扰实体确实被移除。
- `tierThreeGrowthStillProtectsEntitiesInsideNewTrunk`：夹具完成后再放入占位实体，验证三阶生长仍必须失败，实体保持存活，所有方块和 SavedData 不变；实体离开后，同一棵树才可升阶。

最终 `discard` 夹具修复后，同一两项探针 **2/2、exit 0**；原夹具为 **1/2、exit 1**，中间 `killAllEntities` 方案也为 **1/2、exit 1**。三份未经改写的运行日志以 `.log.gz` 保留在本目录，校验值见 `SHA256SUMS`。日志时间均为 UTC。

临时 `GrowthPlan` 日志及独立探针未纳入最终产品代码。正式构建前清理探针编译产物，并检查产物不含 `fertilizer_diagnostics` 或 GameTest 类。

## 复跑入口

使用 Java 17 和仓库既有 Forge/Gradle 配置。基础命令如下；低内存环境可用 `_JAVA_OPTIONS=-Xmx3G` 和 `--max-workers=1`。不要复用其他人的生产世界。

```bash
set -o pipefail
mkdir -p build
python3 tools/yuji/validate_assets.py
bash ./gradlew -PyujiOnly build runGameTestServer --no-daemon --max-workers=1 --stacktrace 2>&1 | tee build/a55-yuji.log
python3 tools/yuji/verify_gametest_log.py build/a55-yuji.log
bash ./gradlew runGameTestServer --no-daemon --max-workers=1 --stacktrace 2>&1 | tee build/a55-full.log
python3 tools/yuji/verify_gametest_log.py build/a55-full.log
```

分别将 Gradle 标准输出与错误输出记录到对应日志，并保留实际退出码；两轮之间使用独立新测试世界。此次 VPS 复跑使用同一 Gradle 缓存及 `--offline --daemon`，命令和退出码随完整证据保留。修复后专项、全量与 CI 的最终计数、SHA、run URL 以本 PR 的证据回报为准，不套用旧提交计数。

本地完整基线曾在约 201 项处长时间无新输出。线程栈显示处于原版 `StructureUtils.clearSpaceForStructure` 的定时任务清理及下一批区块载入，继续运行后完成，未删测试、未增大断言时限，也未归咎于贪欲 AI。

## 十怪与下一棒

十怪对照沿用 [现有验收矩阵](../../../yuji-acceptance.md) 及其 F1-F8 变更：愚痴、杀生、偷盗、恶口、妄语、两舌、邪淫、绮语、贪欲、嗔怒。十佛头与骨刺是额外配套，不计入十怪。

`YujiMonster.java` 与 `YujiGameTests.java` SHA-256 保持为：

```text
1b432149c37ff22cdd62e73f8e86725458dd39cd3cf8c5ece5780824779a6267
1bc6f02820b9335c95f481c12e9b106aeac24088dad7976de7f5a30e9150335b
```

贪欲近路径点、成长上限读档同步与服务器咬击声音修复全部复用。第一轮独立 Review 已在 #25 以 COMMENTED 完成，不等于批准。第二轮不同 Agent Review、人工听音、真人多人、动作观感、任意复杂地形/第三方模组组合、关闭视角转向后的双端切换、全部中立物种及跨 8 层速度/外部属性组合仍未签收。本轮不代签，不合并、不发布、不部署。
