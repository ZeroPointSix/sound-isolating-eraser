# a21 接续核验记录

本记录是实现侧证据核对，不是独立 Review 或需求总体验收。继续使用 PR #21 和 `feat/yuji-fodu-monsters-20261011`，不合并、不发布。

## 固定受测版本

- 受测 SHA：`c84d2b11aad4e18b3266c92814dcbf8409434b00`，包含 `bd104e1` 的门夹具修补；`ff42621`、`88a2114` 均为祖先，不是需要恢复的当前版本。
- [Yuji CI 38235118401](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38235118401)：资源校验、构建、216/216 全量 GameTests、44/44 Yuji 通过。
- [实际客户端日志](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38235118401/job/114763779332)：逐怪、旧边界、两客户端隔离/门/地形/Panda 三阶段均通过。
- 2026-10-12 11:02 UTC 经 GitHub API 重新读取该 SHA 的 11 个 check-runs，均为 `completed/success`。这是同一 SHA 的结果，不拿历史 88a2114 的绿检查替代。
- 后续归档提交只增加文档和原始证据；受测 SHA 与归档 SHA 分别记录，归档 HEAD 的检查仍以 PR checks 为准。
- 已接续核对 a20 在原工作机启动的 `GRADLE_USER_HOME=/data/minecraft-models/gradle-cache bash ./gradlew -PyujiOnly runGameTestServer --no-daemon --stacktrace`：[本地原始日志](local/gametest-a20-focused.log)于 11:13 UTC 记录 44/44 required tests passed，构建成功（13 分 11 秒）。这是 a20 启动、本轮读取并保全的真实执行，不伪称本轮重新启动。

## 原始证据核对

- 重新计算 [provenance.json](provenance.json) 全部 222 个文件的字节数和 SHA-256，均一致。其中本次成功 CI 原件 218 份，修复前失败原件 4 份，来源不混合。
- 历史 `814a6a2` 的 76 份、`6e5eea4` 的 108 份、`ddfa448` 的 117 份原件共 301 份，全部哈希保持一致。
- 本次文档和证据索引 257 个相对链接无缺失；历史接管目录 205 条校验记录全部检查，原始日志/图像哈希不变，仅按更新后的说明文档刷新该文档自身哈希。
- 官方客户端 ZIP SHA-256：`c685c843a24d0c94d5c0a57d4519ebae75bb9e3b787dbbe42d367575e46da80c`；构建 ZIP：`3e4c739c53aa8c0bcf58dcb10b1ef639f6ced39f1262ec5c70384d7d768b4abd`。与 GitHub artifact 元数据及收集清单一致。
- 查看了全部 13 张主体画面：10 怪、十佛头、飞行骨刺、插墙骨刺。原截图未修图或覆盖；同机位对照和客户端渲染断言仍在归档中。
- 两轮定向声音的目标端均收到并激活 2 个事件，旁观端均为 0；两轮迷魂目标端转向 90 度、达到 II 级，旁观端不转向且无效果，移除来源后两端继续 100 tick 无残留。
- 站立与残躯愚痴关闭门受阻、开门后同一实体自然跨过；直墙最高 y=86.154448、内拐角 y=81.154448，整个身体越墙；顶檐最高 y=68，实际顶碰撞 140 次且不穿块。
- Panda 自然众叛后对真实连接玩家造成 6 点伤害；移除来源后清理目标与影响，并观察 60 tick 无残留攻击。

## 失败不抹除

- [bd104e1 原始失败日志](before-bd104e1/jobs/114732796096.log) 的内拐角超时保留。c84d2b1 移除测试引导高台这个额外障碍，没有放宽整身越墙、真实碰撞、正常重力或时限断言。
- a18 在 VPS 上另起的全量回归达到 30 分钟执行上限被终止，未产生完整通过结论。[原始日志](local/full-a18-door-timeout.log)与[任务终态](local/full-a18-door-status.json)保留。不可把 CI 的成功说成本地这次成功。
- e9615eb 的 Enhancement 首次失败、bd104e1 的 Eternal Night 首次失败均保留；它们分别在同一 SHA 原样重跑后通过。未修改无关模块或降低断言。

## 范围与后续

完整十怪/十佛头/骨刺对照见 [需求矩阵](../../../yuji-acceptance.md)。绮语当前召唤周期为 200 tick、同一召唤者附近 32 格存活上限 4，吹奏增益周期为 240 tick；不是旧交接中的召唤 240 tick/上限 6。Boss 当前为 12000 HP/攻击属性 260/护甲 60，未退回旧数值 8000/200/50。

人工听音、动作审美、实际多人玩法体验、全物种和任意地形穷举仍未签收。真实客户端由 QA 模组自动操作，不冒充人工游玩；实际 OpenAL 混音不冒充人工听感。实现侧自动化通过后可交给两名不同 Agent 独立 Review，再进行专门需求功能验收；本轮不代做或宣称这些阶段已经完成。
