# 雨寂佛都实测反馈验收证据

## 固定来源

- 源 HEAD：`ddfa448b02efc4cedd3527e836b4d24f8e9bd061`，沿用 PR #21 原分支。
- [Yuji CI 38206157431](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38206157431)：资源校验、构建、213/213 全仓库 GameTests（41 个 Yuji）、逐怪客户端及双客户端边界场景通过。
- [构建与测试完整日志](validation/validate-job.log)、[GameTest 原始日志](validation/gametest.log)、[客户端执行完整日志](validation/client-smoke-job.log)。
- [客户端制品](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38206157431/artifacts/11726838839)、[构建/测试制品](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38206157431/artifacts/11727840997)。
- [来源与 SHA-256 清单](provenance.json)：117 份原始证据，包含 26 张逐怪/投射物截图及同机位对照、6 张双客户端边界截图。此前 184 份历史证据逐文件哈希复核未变。

本运行通过不等于该 HEAD 的所有工作流都通过。同期其他全量任务出现远程咒击测试的不稳定失败，另有一次 Brickrot 用例失败。后续提交将远程测试的双方约束在同一已加载区块，并显式检查实体注册、视线和区块 tick 状态；40→34→28、施法第 15/32 tick 及中途不能被冷却重启的断言保持不变。PR 最新检查用于确认后续修正，不将本次单个绿 run 冒充全部 CI 绿色。

## 逐怪可复核入口

| 实体 | 真实截图 | AI / 碰撞 | 播放记录 |
| --- | --- | --- | --- |
| 愚痴 | [渲染](gallery/yuchi.png) | [真实玩家交互](gallery/server-ai-yuchi.pass) | [死亡/观察到的声音](gallery/client-audio-yuchi.pass) |
| 杀生 | [渲染](gallery/shasheng.png) | [真实玩家交互](gallery/server-ai-shasheng.pass) | [死亡/观察到的声音](gallery/client-audio-shasheng.pass) |
| 偷盗 | [渲染](gallery/toudao.png) | [实际背包偷取与逃跑](gallery/server-ai-toudao.pass) | [死亡/观察到的声音](gallery/client-audio-toudao.pass) |
| 恶口 | [渲染](gallery/ekou.png) | [自然远程念咒命中](gallery/server-ai-ekou.pass) | [念咒及死亡播放](gallery/client-audio-ekou.pass) |
| 妄语 | [渲染](gallery/wangyu.png) | [真实玩家交互](gallery/server-ai-wangyu.pass) | [假声及死亡播放](gallery/client-audio-wangyu.pass) |
| 两舌 | [渲染](gallery/liangshe.png) | [自然众叛](gallery/server-ai-liangshe.pass) | [死亡/观察到的声音](gallery/client-audio-liangshe.pass) |
| 邪淫 | [巨型渲染](gallery/xieyin.png) | [拉拽及地板碰撞](gallery/server-ai-xieyin.pass) | [死亡/观察到的声音](gallery/client-audio-xieyin.pass) |
| 绮语 | [渲染](gallery/qiyu.png) | [四种增益及自然补召](gallery/server-ai-qiyu.pass) | [箫声/衰减及死亡播放](gallery/client-audio-qiyu.pass) |
| 贪欲 | [渲染](gallery/tanyu.png) | [成长及攻击](gallery/server-ai-tanyu.pass) | [死亡/观察到的声音](gallery/client-audio-tanyu.pass) |
| 嗔怒 | [渲染](gallery/chennu.png) | [中立至报复](gallery/server-ai-chennu.pass) | [死亡/观察到的声音](gallery/client-audio-chennu.pass) |
| 十佛头 | [5 倍线性尺寸渲染](gallery/shifotou.png) | [玩家敌对](gallery/server-ai-shifotou.pass)、[铁傀儡敌对](gallery/server-boss-iron-golem.pass) | [技能/音乐及死亡播放](gallery/client-audio-shifotou.pass) |
| 骨刺 | [飞行](gallery/bone_spike.png)、[插墙](gallery/bone_spike_stuck.png) | [插墙渲染与命中音](gallery/client-bone_spike_stuck.pass) | 飞行无独立循环音；命中原版骷髅受伤音实际 active=true |

每张逐怪图片旁均有同名 `-control.png`，在相同相机位置移走实体后拍摄，用于像素差分；不是合成的游戏画面。骨刺插墙的实际前景为 691 像素，边界为 `(486,242)-(534,262)`；飞行前景 7385 像素。已目视检查原始渲染画面。

### 新增反馈专项

- 绮语：自然施法后的同伴实际同时拥有力量、速度、抗性、再生；观察到 3 个召唤物，移除一个后后续自然召唤补位，`allyFourBuffs=true, replenished=true`。
- 十佛头：未手动指定目标、铁傀儡未挑衅，Boss 自然选择铁傀儡；记录 2 次实际伤害事件，累计血量下降 120。攻击属性 260 不等于所有技能各造成 260。
- 偷盗：真实取得玩家背包的物品后执行原有逃跑。验收要求观察到自然索敌和偷取，不错误要求逃跑结束时还盯着玩家。
- 骨刺：实际投射物撞墙，插墙渲染帧数 135，声音事件 `minecraft:entity.skeleton.hurt` 活跃；200 tick 后消失。
- 旧独立验收边界：[双客户端逐事件日志](boundaries/boundary-events.log)、[记仇保持](boundaries/server-grudge-two-clients.pass)、[增益双斩](boundaries/server-betrayal-double-slash.pass)、[Boss 侧面](boundaries/server-boss-side.pass)、[上部](boundaries/server-boss-upper.pass)、[侧面不接触](boundaries/server-boss-outside-side.pass)、[上方不接触](boundaries/server-boss-above.pass)。

## 念咒失败到通过的回溯

- `df93578` 原有 40 个 Yuji 测试：[40/40 日志](before/df93578-yuji-baseline.log)。这个结果遗漏了冷却到期时仍有有效 AI 目标的条件。
- 同生产版本补上该条件：[39/40，施法序号 2 != 1 的失败日志](before/df93578-chant-regression-before.log)、[原始增强测试 diff](before/deterministic-fixture.patch)。
- [原始 CI 失败日志](before/df93578-ci-validate.log)另记录第二击未生效，血量 34 而不是 28。
- 上游 `028aeba` 保留正在执行的念咒，禁止技能冷却中途重置动作。本目录对应修复后的完整测试运行：41 个 Yuji 均通过，未降低两击伤害或时点要求。后续新增的区块可见性约束专门修正测试环境的间歇失效。

## 覆盖边界

完整 Forge 客户端通过 TCP 连接真实专用服务器，操作由独立 QA 模组执行，不是 Mock 客户端、假截图或人工游玩。GameTest 中的单元边界用例另行分层，不把其中的测试玩家称为真实客户端。

音频经过 Minecraft SoundManager/OpenAL-null，记录解码、音量、声源和实际播放状态；没有扬声器录音，也没有人工听觉验收。双客户端定向声音与转向隔离、复杂攀墙地形、动画美术观感仍未单独验收。两舌的 Panda 目标遗漏是既有需求差异，本轮按要求未改变其机制。五怪只优化骨骼动作，不宣称重新制作全部网格。

完整需求映射见 [需求对照表](../../../yuji-acceptance.md)。未合并、未发布、未新开 PR。
