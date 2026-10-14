# PR29 第二轮审查与功能性证据范围

## 基线、身份与边界

复用 Draft [PR29](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/29) 和 `fix/fertilizer-grove-growth-20261014`。本轮从 `9c1887b0fa7b89497b8d4e8316c31a4e64574b5d` 继续，不重做十怪。PR25 的第一轮 Review 对应 `22ea4a5`，不冒充第二轮。

本轮独立 reviewer 未编写原十怪或 PR29 肥料夹具；执行者曾编写夹具，仅负责代发独立结论和落实修复。正式 [Request Changes](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/29#pullrequestreview-5519745647) 固定到 `9c1887b`。最终复核与运行结果必须绑定 PR 评论中的精确 HEAD，不使用本文件存在本身证明通过。

PR 继续保持 Draft。没有合并 main、发布、生产部署或资金操作。自动化通过不等于人工听音、真人多人或动作审美签收。

## 发现与修复

已核验 `64d53c1...9c1887b` 的准确差异：仅肥料 GameTest、版本与证据文件改变，未修改十怪生产代码或资源。以下 P2/P3 是扩大逐怪审查发现的既存问题，不是肥料夹具引入的回归。

### P2：妄语反应串到未收声玩家

`YujiMonster.mimic()` 只给实际听者 A 发声音包，但旧反应阶段读取可变化的 AI 目标；改成 B 后，B 看向 A 的旧假声源也会获得虚弱 II。漏看计数同样跨人继承，且没有目标时 40 tick 窗口停止倒计时。

测试提交 `01ae50409595ce2343819bdc3c6a7ce221f3e9f0` 保留旧产品逻辑。[原始 CI](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38583089264/job/115917830137) 实际完成 326 项，323 通过、以下 3 项失败；Java exit 3、Gradle exit 1。[完整工件](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38583089264/artifacts/11904353116) 保留，不用重跑抹除。

- `yujiFakeVoiceReactionStaysWithRecipient`：B 不能因 A 的旧声音升级，同时 A 在换 AI 目标后仍能正确反应。
- `yujiFakeVoiceMissesDoNotCrossRecipients`：B 第一声不继承 A 的两次漏看，但 B 自己第三声仍达到虚弱 II / 400 tick。
- `yujiFakeVoiceWindowExpiresWithoutTarget`：实际发声打开 40 tick 窗口；听者离开索敌范围后 AI 目标始终为空，窗口仍到期，返回再看不会升级。

修复 `0dc92f11a9e1e4f6b4f2eaeccce3681100805990` 将反应绑定实际声音收件人 UUID；仅当下一次实际发声的收件人变化时重置漏看计数；窗口不依赖 AI 目标倒计时，听者失效时关闭。正常窗口结束不清除听者历史，以保留同人连续三次升级。

夹具使用真实注册到 ServerLevel 的 Forge FakePlayer，反射驱动实际生产 AI/发声方法。它是组件 GameTest，不是可听网络交付或真人多人证明。围墙使用独立高度，保存并在 finally 恢复全部原方块，避免污染恶口和贪欲同批用例。没有删除、放宽或跳过原断言。

### P3：恶口念咒回正关键帧越界

念咒长度为 2.0 秒，五个回正关键帧却在 2.333 秒。仅把这五个键移到 2.0 秒，保持原姿态、40 tick 施法与第 15/32 tick 的伤害不变。资源校验新增所有骨骼时间轴边界检查，4 个标准库测试覆盖负时间、超时、准确结束点、实际仓库动画。

同一静态测试在旧动画为 3/4、exit 1，错误明确为 `animation.ekou.chant/head/rotation@2.333 outside [0, 2.0]`；修正后为 4/4、exit 0。这证明资源时序一致性，不代表人工观感已签收。

### 保留的修复

肥料夹具的局部实体隔离与两项回归继续保留；生产 `GrowthPlan` 碰撞保护未修改。贪欲近路径点导航、降低层数上限后的读档属性同步、服务器咬击漏音修复均保留。除妄语收件人状态和恶口五个动画时间键外，不重写十怪机制。

## 验证记录与复跑入口

`9c1887b` 的两次本地全量均已回收退出码；原 a55 全量 323/323、Gradle exit 0、日志校验 exit 0，原始全量日志 SHA-256 为 `b6a7589adde5bf66d827b7672182388a45293de37635d328ab13237790d75575`。a61 专项 75/75、全量 323/323，Gradle 与完整性校验均 exit 0；日志 SHA-256 分别为 `955199620f0bfe4ed4cf457a4ce950d31650826fcf95b53ea276414e9ded21f6`、`9e9318e0e494a0bc11182cd59dfa6fc9b47d8306a4b0d39d98d1973d3a15a448`。连接器的 SSH 超时不是 GameTest 退出码；最初未取得结果时没有宣称通过，恢复后才补记。

这些旧 HEAD 结果不覆盖新修复。新 HEAD 的精确计数、exit code、全量两轮和客户端 CI 链接，统一以 PR 的最终证据评论为准。中间被新提交取代的 CI 不视为通过。

```bash
set -euo pipefail
evidence="$(mktemp -d "${TMPDIR:-/tmp}/yuji-a66.XXXXXXXX")"
printf 'Evidence directory: %s\n' "$evidence"
python -m unittest discover -s tools/yuji -p 'test_*.py' -v
python tools/yuji/validate_assets.py
# Run only in a disposable QA checkout with no game/test process using run/.
if test -d run/world; then mv run/world "$evidence/world-before"; fi
bash ./gradlew clean build -PyujiOnly runGameTestServer --no-daemon --max-workers=1 --stacktrace 2>&1 | tee "$evidence/focused.log"
python tools/yuji/verify_gametest_log.py "$evidence/focused.log"
if test -d run/world; then mv run/world "$evidence/world-focused"; fi
bash ./gradlew runGameTestServer --no-daemon --max-workers=1 --stacktrace 2>&1 | tee "$evidence/full.log"
python tools/yuji/verify_gametest_log.py "$evidence/full.log"
```

运行前确认没有同工作区的旧测试进程，分别留 stdout/stderr、实际退出码、HEAD 和日志哈希；专项与全量使用独立测试世界，不删除真实用户世界。既有雨寂 CI 对全量连续跑两轮，并验证全部 `yuji` 批次被发现，没有改为允许失败。

## 十怪功能性对照

原始 [图鉴](../../../../art/yuji_fodu/monster_cards.md) 与 [F1-F8 最新需求](../../../yuji-acceptance.md) 为依据。下列“自动化覆盖”指可执行断言及已具备的真实客户端探针；当前 HEAD 是否通过须结合最终 CI。Boss/骨刺不计十怪。

| 怪物 | 实现及应对/边界 | 自动化覆盖 | 未签收缺口 |
| --- | --- | --- | --- |
| 愚痴 | 跟随不近战、低血爬行、关闭门阻挡 | `yujiPassiveAndGliderSafety`、门导航配置；双客户端站立/爬行关门与开门后自然通过 | 所有门型、任意地形、真人操作 |
| 杀生 | 双斩第 6/12 tick；众叛增益作用于两击 | `yujiDoubleSlashDealsTwoTimedHits`、`yujiBetrayalBuffAffectsBothTimedSlashes`；双客户端伤害时点 | 动作审美、真人躲避手感 |
| 偷盗 | 整组物品优先级、护甲不偷、逃离；死后归还一次 | `yujiTheftPriorityAndArmor`、`yujiStolenStackReloadAndNoLootRule`，真实背包交互探针 | 真人多人归属组合、复杂逃跑地形 |
| 恶口 | F5 远程念咒替代旧自然咒缚；视线/距离复核，15/32 tick 各 6 点 | `yujiEkouChantHitsFromRange`、`yujiEkouChantSurvivesExpiredCooldown`；动画边界检查 | 任意复杂遮挡、人工念咒观感/听音 |
| 妄语 | 定向拟声、弱化来源清除；本轮补收件人隔离和到期 | 三项 `yujiFakeVoice*`、旧虚弱来源/牛奶回归；完整双客户端声音收发隔离探针 | 新增切换窗口目前为组件证据；真人听感、多人实战 |
| 两舌 | 众叛、敌怪增益不叠加、宠物/村民限制及恢复、Panda | 配置/增益/主人与坐下恢复/Ghast/Panda 回归；真实 Panda 攻击及清除 | 所有中立物种实战未穷举；没有专门“寻找室内”目标，不声称主动择屋已实现 |
| 邪淫 | F6 巨型化，分级拉拽/转向、潜行降级、受伤/离开解除 | 配置、范围/伤害清除、挤压回归；双客户端转向隔离/清除 | `cameraTurn=false` 双端重测、真人多人；无专门狭窄地形搜寻目标 |
| 绮语 | F1 四种增益，F7 战斗召唤其他种类、区域上限/补位；无摔伤 | `yujiQiyu*`、自然增益与补召、箫声衰减和退出探针 | 区域上限不等于跨区块全局配额；真人长期多人、听音 |
| 贪欲 | 成长/剥皮/存物；导航、读档、漏音修复保留 | `yujiGreed*`、降上限读档、实际咬击时点；慢渲染/停帧客户端两轮 | 跨 8 层外部属性组合、任意地形、人工听音 |
| 嗔怒 | 首攻击者持久记仇，死亡或严格超过 128 格解除；中立近距践踏符合图鉴 | 原目标/第二攻击者/重载/128 边界/死亡回归；双真实客户端边界探针 | 跨维度/掉线复杂战斗、真人多人 |

注册、物种属性、服务器配置、双语言、十怪骨骼/动画引用已有代码和 JSON 复核；运行时贴图、音频、渲染、自然 AI 仍由资源检查及真实客户端 CI 提供证据，不能用 GameTest 假玩家替代。

## 最小待执行人工清单

1. 在仅用于 QA 的新世界启动两份完整客户端，执行 `YUJI_QA_ISOLATION=1 YUJI_QA_RECORD_AUDIO=1 python tools/yuji/e2e.py`；保留两玩家 UUID、`*.pass/*.failed`、服务器/客户端日志和各自混音。由两人互换听者/旁观者，人工听辨方向及漏音，不把自动非静音检测当听感签收。
2. 使用 `YUJI_QA_BOUNDARIES=1 python tools/yuji/e2e.py` 复现记仇、增益双斩和接触边界；在 QA 世界以 `/gamemode survival`、`/summon qihai:<id> ~ ~ ~` 补真人发现/应对。各怪分场景进行，避免混场干扰；嗔怒应先无挑衅观察，再由两位玩家先后攻击。
3. 单独切换 `qihai-yuji-server.toml` 的视角开关并重启测试服务器，两端互换邪淫受影响者，确认开关关闭时不强转向，伤害/离开后不残留；记录配置和 HEAD。
4. 在门、转角、楼梯、水边及封闭窄巷逐项观察愚痴/贪欲实际移动、偷盗逃离、恶口遮挡。保留固定世界种子、坐标和失败录像；不把一张截图当越障证明。
5. 人工逐怪检查动作可读性，特别是杀生左右斩及恶口回正；将完成的场景与未做场景分别签字。机器能完成的既有回放和日志检查继续执行，但真人内容本轮不代签。

当前没有承诺可交付的真人验收存档；既有 E2E 命令会生成独立 QA 世界和证据工件。最终功能性结论为自动化覆盖范围内可验证、真人及上述边界待签收，不是“十怪全部游戏场景 100% 通过”。两舌主动择屋、邪淫主动寻找窄处目前没有专门目标实现，属于机制实现边界，不能归为仅等待真人签收；若按原图鉴把这些描述作为强制主动行为，两项应标为部分实现并另行落实。
