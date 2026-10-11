# 三项独立验收偏差：修复回归证据

## 版本与结论

- 被验证的源提交：`6e5eea409c8ffae913134b99bf94800cd470176c`，仍为 PR #21 原分支。
- [Yuji CI 38169852787](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38169852787)：资源、构建、192/192 全仓库 GameTests（其中 35 个 Yuji）及两个真实客户端测试阶段通过。
- [构建/正式回归任务](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38169852787/job/114559986560)；[真实客户端任务](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38169852787/job/114559986320)。
- 本目录的 108 份原始文件来自该运行，未修改日志、截图或通过标记。来源、字节数、SHA-256 见 [provenance.json](provenance.json)。证据归档提交不改变被测游戏逻辑或测试代码。
- [原 76 份历史证据](../814a6a2/README.md)全部保留并重新核验哈希。历史通过不能替代本次新增边界验证。
- 三项修复已通过实现侧自动回归，等待独立复验；不表示全功能或全部游戏场景通过。其他模块和最终归档 HEAD 的 CI 状态以 [PR checks](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/21/checks)及 PR 最新评论为准。

## 问题与断言对照

[独立验收原报告和可复现源码](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/21#issuecomment-6112713109)在 `b3667ec` 上运行 29 项测试，26 通过、3 失败。原 25 项全部通过，新增测试证明旧覆盖存在缺口。

| 原问题 | 正式回归，保留原预期 | 实际客户端观察 | 原始证据 |
| --- | --- | --- | --- |
| R12 第二名攻击者覆盖嗔怒原仇恨 | 首名 UUID 和目标保持；NBT 重载后第二人仍不能覆盖；恰好 128 保持，128.01 释放；死亡允许新目标 | 两个完整客户端分别发出实际攻击包；第二次受击及重载后保持首名玩家；128 保持、129 释放 | [事件全日志](boundaries/boundary-events.log)、[两人攻击](boundaries/server-grudge-two-clients.pass)、[重载](boundaries/server-grudge-reload.pass)、[128](boundaries/server-grudge-128.pass)、[释放](boundaries/server-grudge-release.pass)、[截图](boundaries/grudge-two-clients-YujiTester.png) |
| R04/R08 双斩绕过众叛 +30% 攻击属性 | 实际招募产生 3.9 属性；第 6/12 action tick 累计伤害 3.9/7.8；其他 tick 不额外伤害；原基础 3+3 用例保留 | 两舌自然施加众叛，杀生自然启动双斩；真实玩家两次受伤各 3.9，间隔 6 tick，实际掉血约 7.8 | [报告](boundaries/server-betrayal-double-slash.pass)、[事件全日志](boundaries/boundary-events.log)、[截图](boundaries/betrayal-double-slash-YujiTester.png) |
| R13 侧面中心 3.2 格实际接触却不掉血 | 侧面 3.2、上部 7.8 有相交；侧面 3.31、上方 8.01 无相交；第 20/40 tick 各 10，间隔内无额外伤害 | 生存玩家真实包围盒相交判定；两个接触位置均掉 20，两个对照位置均掉 0 | [侧面](boundaries/server-boss-side.pass)、[上部](boundaries/server-boss-upper.pass)、[侧面外](boundaries/server-boss-outside-side.pass)、[上方外](boundaries/server-boss-above.pass) |
| 死亡动画正向对照 | 第 55 tick 尚未移除，第 85 tick 已移除 | 本项为正式 GameTest，不冒充客户端死亡时序专项 | [完整 GameTest 日志](validation/gametest.log) |

### Boss 边界截图

| 场景 | 真实截图 | 45 tick 内预期及实测 |
| --- | --- | --- |
| 侧面中心 x=3.2 | [boss-side](boundaries/boss-side-YujiWitness.png) | 相交，tick 20/40 各 10，总计 20 |
| 上部 y=7.8 | [boss-upper](boundaries/boss-upper-YujiWitness.png) | 相交，tick 20/40 各 10，总计 20 |
| 侧面中心 x=3.31 | [boss-outside-side](boundaries/boss-outside-side-YujiWitness.png) | 不相交，0 次、0 伤害 |
| 上方 y=8.01 | [boss-above](boundaries/boss-above-YujiWitness.png) | 不相交，0 次、0 伤害 |

## 真实客户端方法与限制

完整 Forge 服务器、两个原生 Minecraft 客户端 `YujiTester` / `YujiWitness` 经回环 TCP 连接。QA 模组自动操作客户端游戏接口并发送攻击包，服务端记录实际接受的伤害和玩家 UUID；没有用 Mock 或 Stub 替代这些客户端。正式 GameTests 单独使用测试玩家，不作为真实客户端证据。

为精确控制距离和几何，测试场地固定位置，设置抗击退并给生存玩家飞行能力；未设为无敌。Boss 接触测试只移除无关近战移动 goal，保留正常实体 tick 和接触伤害执行路径。这是伤害边界专项，不证明自由移动时的全部导航行为。截图由实际 framebuffer 直接保存，画面中的玩家和怪物均来自同一真实游戏进程。

[服务端完整日志](boundaries/server.log)、[第一客户端日志](boundaries/client.log)、[第二客户端日志](boundaries/witness.log)、[综合通过标记](boundaries/server-boundaries.pass)。

仍未覆盖双客户端假声/视角隔离、Boss 特定攀墙地形、关闭门专项、人工听音和全部原版中立生物逐一穷举。双客户端存在不等于已经执行声音或视角隔离测试。

## 本版本逐怪回归

本轮同时重新执行原有逐怪渲染、动画、真实 AI、地板碰撞和音频流程。音频为 Minecraft SoundManager/OpenAL null 输出链上的真实播放活跃日志，不是人工听音录音。骨刺没有独立声音事件，不能伪造音频通过。

| 怪物 | 截图 | 真实 AI/碰撞 | 声音播放记录 |
| --- | --- | --- | --- |
| 愚痴 | [yuchi](gallery/yuchi.png) | [报告](gallery/server-ai-yuchi.pass) | [日志](gallery/audio-events-yuchi.log) |
| 杀生 | [shasheng](gallery/shasheng.png) | [报告](gallery/server-ai-shasheng.pass) | [日志](gallery/audio-events-shasheng.log) |
| 偷盗 | [toudao](gallery/toudao.png) | [报告](gallery/server-ai-toudao.pass) | [日志](gallery/audio-events-toudao.log) |
| 恶口 | [ekou](gallery/ekou.png) | [报告](gallery/server-ai-ekou.pass) | [日志](gallery/audio-events-ekou.log) |
| 妄语 | [wangyu](gallery/wangyu.png) | [报告](gallery/server-ai-wangyu.pass) | [日志](gallery/audio-events-wangyu.log) |
| 两舌 | [liangshe](gallery/liangshe.png) | [报告](gallery/server-ai-liangshe.pass) | [日志](gallery/audio-events-liangshe.log) |
| 邪淫 | [xieyin](gallery/xieyin.png) | [报告](gallery/server-ai-xieyin.pass) | [日志](gallery/audio-events-xieyin.log) |
| 绮语 | [qiyu](gallery/qiyu.png) | [报告](gallery/server-ai-qiyu.pass) | [日志](gallery/audio-events-qiyu.log) |
| 贪欲 | [tanyu](gallery/tanyu.png) | [报告](gallery/server-ai-tanyu.pass) | [日志](gallery/audio-events-tanyu.log) |
| 嗔怒 | [chennu](gallery/chennu.png) | [报告](gallery/server-ai-chennu.pass) | [日志](gallery/audio-events-chennu.log) |
| 十佛头 Boss | [shifotou](gallery/shifotou.png) | [报告](gallery/server-ai-shifotou.pass) | [日志](gallery/audio-events-shifotou.log) |
| 骨刺 | [bone_spike](gallery/bone_spike.png) | [实际撞墙及超时](gallery/server-bone_spike.pass) | N/A，发射音效见恶口 |

## 复现与来源

```bash
set -o pipefail
mkdir -p build
python3 tools/yuji/validate_assets.py
bash ./gradlew build runGameTestServer --no-daemon --stacktrace 2>&1 | tee build/yuji-gametest.log
python3 tools/yuji/verify_gametest_log.py build/yuji-gametest.log
bash ./gradlew -PyujiE2E writeYujiTestClasspath --no-daemon
python3 tools/yuji/e2e.py
YUJI_QA_BOUNDARIES=1 python3 tools/yuji/e2e.py
```

验证环境使用 Java 17、Forge 1.20.1、Xvfb 和软件 OpenGL，QA 不进入生产 Jar。

- [官方客户端制品](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38169852787/artifacts/11713887490)、[构建及正式回归制品](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38169852787/artifacts/11713662521)。
- [客户端 job 原始日志](validation/client-smoke-job.log)、[构建 job 原始日志](validation/validate-job.log)、[GameTest 原始日志](validation/gametest.log)。
- 调试历史：`230187d` 的正式重载用例未注册在线玩家，客户端 128 格用例受无地板和击退影响；`fb802d0` 的真实客户端全部通过，但原版测试登录助手缺少 Forge 所需 Netty channel。`6e5eea4` 改用 Forge 测试玩家注册正式用例，伤害、距离和时点断言未降低。
- 本目录来源为 PR 测试合并提交对应的运行，`provenance.json` 记录源 HEAD、事件类型、job、制品、原始归档路径与文件哈希。后续仅归档证据的提交应保持相同游戏/测试源码，并另行核验新 HEAD 的 CI。
