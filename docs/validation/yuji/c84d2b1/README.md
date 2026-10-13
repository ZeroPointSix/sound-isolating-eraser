# c84d2b1 实现侧固定证据

## 版本与结论

- PR：[唯一 Draft PR #21](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/21)。分支 `feat/yuji-fodu-monsters-20261011`，未合并、未发布、未请求 Review。
- 受测代码：`c84d2b11aad4e18b3266c92814dcbf8409434b00`。本目录所在的后续归档提交只增加文档/证据，不把归档提交冒充当时 CI 的受测 SHA。
- [CI 38235118401](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38235118401)：11/11 同 SHA 检查通过；216/216 全仓库 GameTests，其中雨寂 44/44。
- 原始逐怪、双客户端边界、双客户端隔离/门/攀墙/Panda 三阶段通过。下列通过结论属于实现侧自动化，不是人工玩法、音质或需求总体验收。

## 原始入口

| 证据 | 入口 |
| --- | --- |
| GameTest 和构建原始日志 | [114763779213.log](ci/jobs/114763779213.log) |
| 三阶段客户端原始日志 | [114763779332.log](ci/jobs/114763779332.log) |
| 逐怪图像/同机位对照、AI/碰撞/声音 | [gallery](ci/yuji-client-evidence/yuji-e2e-1791800185594212680/) |
| 记仇、双斩增益、Boss 身体接触 | [boundaries](ci/yuji-client-evidence/yuji-boundaries-1791800455319781117/) |
| 声音/视角隔离、门、地形、Panda | [isolation](ci/yuji-client-evidence/yuji-isolation-1791800549015453886/) |
| 逐文件链接和 SHA-256 | [INDEX.md](INDEX.md) |
| 来源与所选文件哈希 | [provenance.json](provenance.json) |
| 全部原制品清单，包括未纳入 git 的 WAV/Jar | [CI source-provenance.json](ci/source-provenance.json) |
| 修复前内拐角失败 | [bd104e1 原始 client-smoke 日志](before-bd104e1/jobs/114732796096.log) |

CI 归档保留 218 份原始文件：56 张 PNG、3 份实际混音 Ogg、128 个通过标记、31 份日志；没有失败标记。通过标记数量不是 GameTest 数量。另保留 4 份修复前失败相关文件，合计 222 份原始文件；本地运行记录另列，不混算 CI 数量。文件不修图、不删改日志、不重新生成通过标记。

原始 WAV 和构建 Jar 为控制仓库体积没有重复提交，仍在已下载并校验的官方制品内：[客户端制品](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38235118401/artifacts/11741236429)、[构建/回归制品](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38235118401/artifacts/11740293735)。三个 Ogg 文件是 CI 原有转码结果，并非本轮重编码。

## 修复前后

| 项目 | bd104e1 | c84d2b1 |
| --- | --- | --- |
| 全仓库 / 雨寂 GameTests | 216 / 44 通过 | 216 / 44 通过 |
| PR 同 SHA checks | 10/11 | 11/11 |
| 内拐角 | 超时，Boss 位于约 (33.69,94,65.79) | 通过，最高 y=81.154448，80 次墙面接触，整身跨墙 |
| 直墙 | 已通过 | 通过，最高 y=86.154448，105 次墙面接触 |
| 实心顶檐 | 被前置失败阻断 | 通过，最高 y=68，140 次顶碰撞，不穿块 |
| Panda 真实攻击 / 清理 | 被前置失败阻断 | 自然众叛后实际伤害 6；移除源后 60 tick 无残留攻击 |

本轮修复移除越墙后的观察台，停止在巨大身体内部持续创建额外高台；真实玩家在已存在的地面引导，并扩大已加载区域。保留正常重力、完整身体越墙、墙面接触、顶檐阻挡和时限，新增逐 tick 无方块重叠断言。没有传送 Boss 或降低碰撞要求。

## 隔离与资源

- 两客户端互换角色的两轮假声：目标均收到并实际激活 2 个声音，旁观者均为 0。
- 两轮迷魂：受影响者转向 90 度且达 II 级，旁观者无效果、无转向；移除源后两端 100 tick 无残留。
- 愚痴站立/残躯遇关闭门均自然阻挡；同一怪物在打开后自然跨过门板，无开门或伤人行为。
- gallery 对全部 11 个生物与骨刺使用实体截图及同机位移除对照；骨刺另有独立插墙截图。模型、动画、真实地板/投射物碰撞、实际声音播放由原日志给出断言。
- 地形截图是当时真实客户端帧，部分相机只拍到 Boss 局部或旁观者视野，不当作独立的越墙视觉证明；越障结论依据完整 AABB、位置、正常重力和无方块重叠的服务端逐 tick 断言。

## 本地复跑

`kuoya-sgp-002` 上同一代码的 QA 编译和资源校验通过。完整双客户端复跑通过两轮声音、视角及清除，以及站立/残躯门场景，随后报 `连接已丢失. 连接超时` 并退出，未进入完整地形/Panda 闭环。服务端同时记录明显 tick 延迟；不能把本地部分通过写成整套成功，也不把独立 CI 成功当作本地成功。

本地隔离复跑命令：

```bash
YUJI_QA_ISOLATION=1 YUJI_QA_RECORD_AUDIO=1 YUJI_QA_CLIENT_FPS=10 python3 tools/yuji/e2e.py
```

## 仍未覆盖

人工听音、动作审美和实际玩法体验未签收；全原版中立物种、任意复杂地形和第三方模组交互没有穷举。OpenAL 实际混音/非静音/活跃播放不等于人工听觉验收。绮语配额是同一召唤者附近 32 格区域内存活上限 4，不是全世界持久配额。自然刷新、世界生成及 Boss 召唤条件按既定范围未实现。

完整范围对照见 [yuji-acceptance.md](../../../yuji-acceptance.md)。保持 Draft，不发起 Review，不作需求总体验收结论。
