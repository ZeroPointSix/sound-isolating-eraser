# PR #21 接管验证记录

本页区分固定源版本、实际运行结果与待人工验收项。保留同一 Draft PR，不合并、不发布。

## 固定来源

| 源版本 | 运行 | 实际结果 |
| --- | --- | --- |
| `88a211447f01de7f7ffef39fcef61d3036b71e30` 加 Panda 回归用例，生产代码未修复 | [Panda 失败日志](failures/panda-before.log) | 原 42 个雨寂用例通过，新 Panda 用例失败，证实遗漏 |
| `20c9113882c7c986a74e8512e7978fffd34793e7` | [CI 38219652461](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38219652461) | 215/216 GameTests；新增导航 getter 断言失败。逐怪主场景通过后，录音归档因 CI 缺少 ffmpeg 失败；整体不通过 |
| `c282be45af10af237e83e55884256062c7d47b87` | [CI 38220879245](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38220879245) | 逐怪、声音录制和旧双客户端边界通过；新增两角色声音/视角/清除通过，关门场景错误地按方块起点而非门板碰撞面判断越门，整体失败 |
| `c282be45af10af237e83e55884256062c7d47b87` | [低配 VPS 双客户端失败日志](failures/isolation-third.log) | 第一角色声音隔离通过；第二角色收到真实声音，但固定第 240 tick 的虚弱截点可能落在两次随机施法之间，整体失败 |
| `e9615eb364eb3e27b8daf49b3703b6082d99ca81` | [CI 38222636169](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38222636169) | 216/216 GameTests、44/44 Yuji 通过；逐怪、旧边界、声音/视角隔离通过。门夹具两端敞开，怪物绕到外侧后触发越门断言，整体失败；不作为真实穿门的确证 |
| `bd104e18da840e4a3cb9caa24e4d3d0530aef64e` | [CI 38225775339](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38225775339) | 封闭门走廊两端、保留整身碰撞和开门通过断言。216/216 GameTests、44/44 Yuji 通过；门与直墙通过，内拐角因引导高台形成额外障碍而超时，整体失败；[原始失败记录](../c84d2b1/before-bd104e1/jobs/114732796096.log) |
| `c84d2b11aad4e18b3266c92814dcbf8409434b00` | [CI 38235118401](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38235118401) | 216/216 GameTests、44/44 Yuji、逐怪、旧双客户端边界、新隔离/门/直墙/内拐角/顶檐/Panda 全部通过。移除观察台障碍，新增逐 tick 无方块重叠断言；[固定证据](../c84d2b1/README.md) |

原始 `c282be4` [CI 制品](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38220879245/artifacts/11734641097) 包含原始 WAV。本目录保留其截图、原始日志、通过/失败标记及直接转码的 Ogg，不把失败场景的局部通过写成整体通过。

## 已保存的真实客户端证据

| 场景 | 原始记录 |
| --- | --- |
| 全部 11 个生物、飞行及插墙骨刺 | [逐怪证据目录](c282be4/yuji-e2e-1791792042646245090/)；包含 26 张实体/同机位对照截图、自然 AI、碰撞及音频报告 |
| 记仇、增益双斩和 Boss 身体接触边界 | [双客户端证据目录](c282be4/yuji-boundaries-1791792311100406940/) |
| 妄语与邪淫互换目标/旁观者，以及移除源后清除 | [服务端阶段日志](c282be4/yuji-isolation-1791792403821489443/isolation-events.log)、[客户端 A](c282be4/yuji-isolation-1791792403821489443/isolation-YujiTester.log)、[客户端 B](c282be4/yuji-isolation-1791792403821489443/isolation-YujiWitness.log) |
| 实际混音 | [逐怪 Ogg](c282be4/yuji-e2e-1791792042646245090/client-mix.ogg)、[隔离客户端 A Ogg](c282be4/yuji-isolation-1791792403821489443/client-mix.ogg)、[隔离客户端 B Ogg](c282be4/yuji-isolation-1791792403821489443/witness-mix.ogg) |

声音隔离是在两个完整 Minecraft 客户端的实际 SoundManager 上检查定向原版 HOSTILE 声音事件、资源解析及活跃实例。角色交换后，目标有对应活跃声音，旁观者收到 0 个对应事件。邪淫场景记录实际 yaw/pitch 和效果等级，旁观者不转向，源移除后额外观察清除状态。Ogg 是各客户端完整混音，含正常环境声；不能把旁观者混音非静音误判为定向声音泄漏。

录音文件从各自 OpenAL 设备启动时开始，两个客户端的零点不同。用客户端日志的 `epochMs` 和 `START` 阶段对齐事件；不能把两个 Ogg 的同一秒直接当作同一服务器时刻。进程退出留下的 WAV 长度头可能未最终写回，转码器告警保留在 `*-mix-audio.log`；Ogg 解码及非静音检查独立执行。

## 测试修复与验收约束

- Panda 生产修复显式包含不实现 `NeutralMob` 的 Panda；GameTest 测试玩家只作为回归测试，不冒充双客户端 E2E。
- Minecraft 1.20.1 的 `GroundPathNavigation.canOpenDoors()` 实际转调 `canPassDoors()`；改为分别检查 NodeEvaluator 的开门、通门标志，没有禁止通过已打开的门来凑绿。
- 门测试按关闭门的真实碰撞形状检查整个实体碰撞箱，仍要求正常追击、不开门、不穿块、不伤人及同一实体开门后自然穿过。
- 妄语效果按整个观察窗口采样，旁观者每 tick 都不得获得虚弱；避免将短效果的随机间歇误判为未施加。
- 攀墙使用正常重力、原版碰撞、自然索敌和实际移动；移动连接中的玩家引导整个 30 格宽身体越障，不直接搬动 Boss。直墙、拐角和实心顶檐均须独立留下结果。
- CI 的 gallery、旧双客户端边界、新隔离场景分开执行；聚合器遇到失败标记即失败，截图必须由客户端完成并确认。

## 人工听音与独立复验

自动播放、音量和非静音检查不等于人工听音。本轮尚无真人听感签收，不能宣称全功能验收完成。独立审阅者需记录姓名、测试版本、设备和结果：

- 逐怪对照声音字幕和音频日志，确认呼吸、移动、受伤、死亡及技能声音可辨认，无异常爆音或截断。
- 检查绮语箫声及 Boss 循环的开始、衰减和退出，核对自然增益/召唤阶段。
- 分别听取两个客户端的角色交换片段，确认定向假声只在目标端出现；正常环境声不算泄漏。
- 在真实双人游戏中复验站立/残躯关门、直墙、拐角和顶檐，记录是否出现卡死、穿模或摄像机残留影响。

全部原版中立物种逐一客户端穷举、所有复杂地形及人工动作审美仍不在自动覆盖声明内。历史证据目录不改写；本轮文件哈希与运行来源随最终结果一同归档。
