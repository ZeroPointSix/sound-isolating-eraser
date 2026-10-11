# 雨寂佛都需求与验收对照表

## 来源与边界

- 原始需求：[附件图鉴 v0.2](../art/yuji_fodu/monster_cards.md)，包括第 3-12 节的十恶业、第 13 节十佛头及第 14 节骨刺。
- 附件 SHA-256：`c034d4c9aa7685255fb182fa3ff07b12af5c52b1c813438ebd6843ee73a8bc1f`。
- 唯一实现 PR：[sound-isolating-eraser #21](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/21)，分支 `feat/yuji-fodu-monsters-20261011`，保留 Draft，不合并、不发布。
- 数量按附件原文计算：10 种普通怪物 + 1 个十佛头 Boss + 1 种骨刺投射物，不把投射物虚算成第十二种生物。
- 沿用仓库 Forge 1.20.1 注册事件、GeckoLib 4 渲染、独立 QA source set 与专用服务器/原生客户端验收方式。生产 Jar 不包含 `yuji_qa` 探针模组。
- 原需求明确排除自然刷新和 Boss 召唤条件。本 PR 只提供 11 枚刷怪蛋及 `/summon`；未增加黑雨、世界生成或无来源的普通战利品。

## 公共接入检查

| 检查项 | 实现与检查方式 |
| --- | --- |
| 实体注册、属性、刷怪蛋 | [YujiContent](../src/main/java/com/zeropointsix/eraser/yuji/YujiContent.java)、[YujiKind](../src/main/java/com/zeropointsix/eraser/yuji/YujiKind.java)；`yujiAllProfilesRegistered` 检查全部 11 个生命值及实体/蛋，Boss 保留 2000 HP |
| 模型/贴图/动画 | 每种 `id` 对应 `assets/qihai/geo/id.geo.json`、`textures/entity/id.png`、`animations/id.animation.json`；嗔怒/十佛头额外发光层；校验器检查全部资源、动画、骨骼和特效引用 |
| 真实可见、动画 | 客户端渲染回调计数、实际动画骨骼变化、截图与同一相机移走实体后的对照图差分；不能以“世界画面非空”代替实体渲染通过 |
| 碰撞 | 每个活体用原版移动/方块碰撞实际撞地板，要求停在表面且 `verticalCollision=true`；尺寸来自附件，贪欲随层数缩放、愚痴残血改变尺寸；骨刺用真实弹道撞墙 |
| 音效 | 47 个声音事件及 Ogg 引用校验；逐怪自然移动/技能声和实际死亡事件经过 Minecraft SoundManager/OpenAL 后记录 `active=true`、解码资源、音量、音调与声源位置；另检查箫声衰减 70%、箫声/Boss 循环退出 |
| 掉落 | 11 个明确的空实体掉落表；附件未规定一般战利品。偷盗/贪欲携带的原物品用代码完整归还，即使 `doMobLoot=false`，并验证只归还一次 |
| 语言 | 附件 `zh_cn`、`en_us` 的实体/蛋/效果/声音字幕键导入并交叉校验；骨刺也保留实体语言键 |

## Requirement Traceability Matrix

以下“实现”列描述代码对照，不等同于全部场景都经过端到端验证。真实客户端每行的截图、AI 和音频文件使用对应实体 ID；每轮通过/失败结果必须参照末尾证据索引。

| 需求 | 模型/动画与声音 | 行为实现、尺寸 | 自动化证据与边界 |
| --- | --- | --- | --- |
| R03 愚痴 `yuchi` | 原附件模型、皮肤；idle/walk/stare/crawl/death；呼吸/拖步/受伤/死亡 | 30 HP，不主动攻击，6 格逐步贴近、招引、30% 以下爬行；0.6 x 1.95 | 真实玩家：自然追踪、位移、无伤害、地板碰撞；GameTest：无攻击、爬行属性与尺寸；关闭的门专项不在本轮客户端场景 |
| R04 杀生 `shasheng` | 原附件模型；idle/run/chop/death；喘息/连斩/受伤/死亡 | 20 HP，0.30 速度，0.3/0.6 秒各结算 3 点；击退抗性 0.3；0.6 x 1.95 | 真实玩家：追击及至少两次伤害、地板碰撞；`yujiDoubleSlashDealsTwoTimedHits` 检查两次时点和数值 |
| R05 偷盗 `toudao` | 原附件模型及 loot 骨骼；idle/scuttle/snatch/scream/death；偷盗/尖叫/死亡，无脚步 | 12 HP，从背包优先取附魔/稀有整组，不偷穿戴护甲；逃跑 10 秒；伤害尖叫反胃；0.5 x 0.7 | 真实玩家：17 枚钻石从真实背包进入携物数据、地板碰撞；GameTest：优先级、NBT、护甲保护、重载及关掉落规则下归还 |
| R06 恶口 `ekou` | 原附件模型；idle/walk/curse/spike_volley/claw/burst/death；诅咒/齐射/爆刺/受伤/死亡 | 24 HP；12 格隔墙咒缚、1.5 秒间隔递增封顶 3；招怒；5 根扇射/受伤 8 根爆刺；0.6 x 1.95 | 真实玩家：自然诅咒、地板碰撞；GameTest：多来源、3 秒离开宽限、伤害递增、骨刺数量/速度/持久化；骨刺另有真实撞墙验收 |
| R07 妄语 `wangyu` | 原附件模型；idle/walk/mimic/claw/death；拟声/环境/受伤/死亡，定向原版假声 | 20 HP；6-10 秒随机墙后声源，仅发目标；视线与漏找次数升级虚弱；低光偏好、保持距离；0.6 x 2.1 | 真实玩家：墙后拟声造成虚弱、地板碰撞；GameTest：虚弱来源、短刷新、外部效果保留、牛奶清除；双客户端隔离未在本轮执行 |
| R08 两舌 `liangshe` | 原附件模型；低语/抓挠等全部动画与声音 | 16 HP；20 格、15 秒刷新 20 秒众叛；敌怪 +30%、宠物临时反叛、村民交易限制和状态恢复；0.6 x 1.6 | 真实玩家：自然施加众叛、地板碰撞；GameTest：增益不叠加、主人 UUID、坐下恢复、Ghast 保留原生远程 AI；全部原版中立物种的逐一客户端表现未穷举 |
| R09 邪淫 `xieyin` | 原附件模型；idle/walk/lure/curl/death；诱导/受伤/死亡 | 20 HP，无直接攻击；I-III 拉拽、II 起转向、夹墙挤压、潜行降级、伤害/离开解除；0.6 x 2.1 | 真实客户端玩家：自然迷魂并产生移动、地板碰撞；GameTest：长期暴露后潜行、伤害/范围清除、双侧两格墙挤压；双客户端转向隔离未执行 |
| R10 绮语 `qiyu` | 原附件镂空贴图；滑行/攻击等动画；箫声循环及受伤/死亡 | 20 HP、伤害 4；32 格内其他敌怪声音衰减 70%；偏好高处、滑降、无摔伤；0.6 x 2.3 | 真实玩家：追击攻击、地板碰撞；真实声音链：敌怪音量 0.3、箫声退出停止；GameTest：无摔伤 |
| R11 贪欲 `tanyu` | 原附件模型；吞噬/冲刺/攻击等动画；1-6 层可见脸皮及随层缩放；吞噬/受伤/死亡等 | 30+4n HP、4+0.5n 攻击、最多 12 层，原始大伤害剥皮；最近同伴吞噬、腐肉进食/物品存储；基础 0.9 x 2.7 | 真实玩家：实际掉落腐肉被吃后成长并攻击、地板碰撞；GameTest：层数/减伤/剥皮边界、最近吞噬、蛋 1-3 层、一次只吃一份、存档 |
| R12 嗔怒 `chennu` | 原附件六足模型、佛眼发光层；践踏/跑动/死亡等动画与声音 | 500 HP，中立，被打后保存玩家 UUID；普通 20/践踏 30、抗击退、投射物减半/爆炸加倍、只破玻璃树叶；4.5 x 6 | 真实客户端发送近战攻击，服务器确认记仇保存并反击、地板碰撞；GameTest：中立、重载记仇、抗性；不把八足蜘蛛当原模型 |
| R13 十佛头 `shifotou` | 原附件模型、发光层；砸击/横扫/凝视/拖行/死亡；战斗音乐和技能声 | 2000 HP、护甲 30；50+20 砸击、40 横扫、10/s 接触，Darkness/缓慢、攀墙、血条；6 x 8 | 真实玩家：凝视状态和实际伤害、地板碰撞；客户端：模型动画/音乐及退出；GameTest：2000 HP、减伤及火/摔落/窒息免疫；攀墙具体地形未单独验收 |
| R14 骨刺 `bone_spike` | 原附件独立模型/贴图/动画；附件无独立声音 | 原版投射物碰撞；伤害 3、速度 1.6；插墙 200 tick 消失；0.25 x 0.25 | 真实客户端独立截图及帧数，服务器实际发射撞墙并等待超时；GameTest：5/8 发数量、速度及插墙重载；音频标记 N/A 而非伪造播放 |

## 真实客户端方法

`tools/yuji/e2e.py` 启动真实 Forge 专用服务器和完整 Minecraft 客户端，以 `YujiTester` 通过回环 TCP 连接。客户端使用 Xvfb/软件 OpenGL 与 OpenAL null 输出设备；不是截图合成、Mock 客户端或假玩家。操作由独立 QA 模组自动执行，不是人工手动游玩。

每个生物先固定相机采集可复核渲染截图，再启用真实 AI 与生存玩家互动，不手工指定 AI 目标、不直接调用技能来代替自然触发。探针记录玩家 UUID、实际位移、伤害事件、状态和物理碰撞。音频阶段经真实伤害/死亡流程触发声音，记录声音事件、解码后的资源、播放活跃状态和音量。

OpenAL null 不向扬声器输出可听录音。本轮证据证明游戏音频引擎确实接收、解析和播放声音，不能宣称经过人的听觉音质验收。GameTest 中的边界单元用例与真实客户端验收分层报告，不把 GameTest 的测试玩家当作真实玩家证据。

```bash
python3 tools/yuji/validate_assets.py
bash ./gradlew build runGameTestServer --no-daemon --stacktrace
python3 tools/yuji/verify_gametest_log.py build/yuji-gametest.log
bash ./gradlew -PyujiE2E writeYujiTestClasspath --no-daemon
python3 tools/yuji/e2e.py
```

## 证据索引

- 已核验基线 `a80926069cb15b396a2a1975c9dd13607bf758bd`：[CI run 38156839592](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38156839592)，182/182 required GameTests（含 25 个 Yuji）通过。
- 基线渲染/动画/循环音频：[客户端日志](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38156839592/job/114521453228)、[11 怪截图制品](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38156839592/artifacts/11708766148)、[构建及全量回归制品](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38156839592/artifacts/11709570140)。基线不包含新增逐怪玩家 AI/死亡播放验收。
- 新增真实玩家验收版本 `814a6a21c22d9980cdc606640af2f2535b83401b`：[CI run 38159095483](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38159095483)，资源/构建/全量 GameTest 和真实客户端两项任务均通过；[客户端完整日志](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38159095483/job/114528062139)。
- [固定版本原始证据及逐怪索引](validation/yuji/814a6a2/README.md)：12 张真实截图 + 12 张同相机对照图、11 份 AI/碰撞报告、11 份死亡播放报告、11 份详细技能音频日志、骨刺碰撞/消失报告、客户端/服务器完整日志、全量 GameTest 日志及 SHA-256 清单。
- 对应官方制品：[客户端证据](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38159095483/artifacts/11709818266)、[构建/回归日志](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38159095483/artifacts/11709833127)。固定证据未经修图、拼造日志或修改结果。
- 失败回溯：`c1bf645` 已通过 11 怪的交互/死亡音频，但骨刺在截图中太小，整体运行失败。`814a6a2` 只调整测试相机，使其前景面积达到 899 像素、包围框 100 x 43，并补充真实技能声音断言；没有降低原截图门槛。

本轮要求的逐怪真实客户端验收已通过；表中明确列出的双玩家隔离、人工听音、特定地形和全部原版中立物种穷举仍未执行，不将此报告表述为全部游戏场景 100% 覆盖。

CI 截图和日志会被保留到 PR 内的证据目录；GitHub Actions 制品链接可能到期，固定提交内的证据才是长期复核入口。
