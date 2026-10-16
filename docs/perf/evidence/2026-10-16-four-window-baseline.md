# 四窗口生产专服诊断基线

2026-10-16 UTC；[ZER-1542](https://linear.app/zerodotsix/issue/ZER-1542)、[PR43](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/43)。这是未优化源码的真实服务端采样，不是性能改善报告，也不是客户端 FPS 验收。

## 结果与证据

- 准确源码：`bb325dc9ccf3d99565d8a1eedb7efee372fdcd45`，模组 `0.14.2`；PR43 后续提交只改文档。
- 独立验证脚本：[`013f97061b004248a2ca6510536daa35522807e8`](https://github.com/ZeroPointSix/sound-isolating-eraser/commit/013f97061b004248a2ca6510536daa35522807e8)，分支 `verify/zer-1542-t2-spark-bb325dc`。没有进入 PR43，没有修改已有 CI 门禁或生产模组逻辑。
- [完整运行 38966307507](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966307507) / [job 117193568838](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966307507/job/117193568838)：success；准确源码正常 Gradle build、源代码与验证分支 Mixin 各 `14/0`、采集器 7 项保护测试通过。
- [原始制品 12077285827](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966307507/artifacts/12077285827)：68,723,631 bytes，ZIP SHA-256 `e0a5505f00f9fd0aeccf85dedf7c4c9990a0a201c2f33bf14bb6755ad718de88`。独立读取 ZIP 后重算与 GitHub digest 一致；保留期为 30 天，长期对照前必须保全世界副本与二进制 profile。
- 制品包含四份 `.sparkprofile`、四份完整 spark metadata、四份每秒实体 CSV、命令/客户端/服务端日志、生命周期记录、配置、八份前后世界快照、三个原始客户端截图。
- [可机器读取的摘要](2026-10-16-four-window-metrics.json) 保留完整精度和来源字段；这里为便于阅读进行舍入。原始 metadata 中的空值或默认值不替代实测字段。

每个 profiler 均记录 **6000 ticks**、采样间隔 **4000 微秒**；每个实体 CSV 均为 **300 行，elapsedSeconds 0..299**。时间来自 spark 的 `startTime/endTime`，不是包括报告上传等待的 Python 外层用时。MSPT 取报告结束时 `platformStatistics.mspt.last5m`，是该滚动窗口摘要，不声称拥有逐 tick 原始分布。

| 窗口 / viewer | UTC 开始至结束 | 实际秒数 | MSPT 均值 / 中位 / P95 / 最大，ms | 玩家 | 已加载实体 |
| --- | --- | --- | --- | --- | --- |
| [A 空载](https://spark.lucko.me/QMZXUwQZ26) | 21:06:32.391 至 21:11:32.472 | 300.081 | 0.887 / 0.763 / 1.410 / 52.605 | 0 | 主世界及全局均 110 |
| [B1 雨寂](https://spark.lucko.me/BQ1iJ9qI3b) | 21:16:37.641 至 21:21:37.713 | 300.072 | 5.000 / 4.793 / 6.135 / 73.899 | 1 | 雨寂 37 至 42；主世界 235；全局 272 至 277 |
| [B2 永夜城市](https://spark.lucko.me/Scz8kd12Ic) | 21:26:43.632 至 21:31:43.721 | 300.089 | 3.785 / 3.585 / 4.799 / 52.070 | 1 | 永夜 8 至 11；主世界 244；全局 252 至 255 |
| [C 真实召唤物](https://spark.lucko.me/aCKjSDJu2S) | 21:36:54.677 至 21:41:54.765 | 300.088 | 7.230 / 5.808 / 12.921 / 104.043 | 1 | 主世界及全局 170 至 204；其中父实体 16、真实归属子实体 64 |

四窗口 last5m TPS 均约 20。实体总数包含玩家、非怪物等当前已加载实体，不代表怪物总数；每维度使用 `@e[distance=0..]`，报告结束值分别为 110、277、255、170，与 spark 世界汇总一致。C 的父数来自 `type=qihai:qiyu,tag=t2_parent`，子数来自实际 `YujiSummoner` NBT；不是总数减父数，也不是把理论 `16*4` 当观测。300 行内父/子数量均稳定为 16/64。

**FPS：A 不适用；B1/B2/C 未测。** 客户端是软件渲染、配置上限 20 FPS，该值不是测量结果。没有客户端 `/sparkc` 或 allocation profile，不能用服务端报告推导帧率、分配量或 GC 改善。

## 环境与启动恢复

| 项目 | 本次实际值 |
| --- | --- |
| 宿主与配额 | GitHub Ubuntu 24.04；Linux 6.17.0-1022-azure / glibc 2.39；CPU 型号 AMD EPYC 9V74，但实例仅分配 4 个逻辑 CPU、2 个物理核心，不能写成使用了 80 核 |
| CPU 分隔 | 服务端 CPU 0,1，同一个物理核心；客户端 CPU 2,3，另一个物理核心；固定亲和性，不在采样时并行构建 |
| Java / 服务端 | Temurin 17.0.20.1+1；Minecraft 1.20.1 / Forge 47.2.0 / spark 1.10.53-forge；生产 reobf jar，无 QA 运行时模组 |
| jar 摘要 | `a13db04b82dec3bac59ca7d974895d0c51924511076f4d38220655820c1caff0` |
| JVM | 服务端 `-Xms512M -Xmx2G -XX:ActiveProcessorCount=2`；客户端 `-Xms256M -Xmx1536M -XX:ActiveProcessorCount=1` |
| 世界 | seed 20261016、normal、view-distance 8、simulation-distance 6；每窗口正式启动后预热 120 秒 |
| 连接 | 仅回环 `127.0.0.1:25592`；隔离测试离线认证；RCON 关闭，未开放公网专服 |
| 客户端 | 真实 Forge 开发客户端；T2Perf，survival；llvmpipe / 1280x720 / maxFps 20 / VSync 关闭；无 GPU 帧率结论 |

原 Daytona 的 Gradle/Forge/Cloudsmith TLS 重置没有被声称“已修复”。成功替代路径是独立 GitHub runner 的正常依赖解析和官方 Forge installer；没有删除 foojay、改变插件版本、关闭证书校验、扩大网络权限或使用高负载 VPS。准确源码可正常构建，随后生产专服持续运行，证明不是仅修复了 `gradlew --version`。

在依赖可达的隔离工作区，可复用以下固定版本流程。`source/` 与 `harness/` 是两个独立检出目录；先检查端口，并仅在接受 Minecraft EULA 的测试环境执行。完整依赖下载、校验、server.properties 与 JVM 参数以固定脚本为准。

```bash
git clone https://github.com/ZeroPointSix/sound-isolating-eraser.git source
git -C source switch --detach bb325dc9ccf3d99565d8a1eedb7efee372fdcd45
git clone https://github.com/ZeroPointSix/sound-isolating-eraser.git harness
git -C harness switch --detach 013f97061b004248a2ca6510536daa35522807e8
cd source
python3 tools/verify_mixin_targets.py
bash ./gradlew --no-daemon --max-workers=2 build
bash ./gradlew --no-daemon --max-workers=2 -I ../harness/tools/perf/launch.init.gradle writeT2ClientClasspath
python3 ../harness/tools/perf/test_probe.py
cd ..
T2_SOURCE_DIR="$PWD/source" T2_HARNESS_SHA=013f97061b004248a2ca6510536daa35522807e8 python3 harness/tools/perf/full_suite.py
```

需要 Java 17、至少两个可分隔的物理核心、Xvfb/xdotool 及可用 OpenGL 软件渲染。脚本会创建 `source/build/t2-perf/server/world`，通过标准输入控制真实专服，按 `spark profiler start --timeout 300` 采样。它会重新制作场景；**严格前后比较须改为恢复下面的固定 before 快照**，不能把重新生成的世界当同一副本。

8 次启动均有 `Done (...)!`、PID 存活记录；8 次关闭均为正常 `stop`、exit 0。最后专服 PID 5149 在 21:42:04.250 UTC 正常结束；客户端/Xvfb 也由采集器回收。没有遗留常驻专服。

## 场景真实性与限制

- B1 通过正常 `/yuji enter` 登记进入，开始/结束维度均为 `qihai:yuji_fodu`；采样前位置 `[82.5,129,82.5]`、方向 `[-180,8]`。
- B2 通过主世界真实床位深睡及正常入城流程进入；开始/结束均为 `sound_isolating_eraser:eternal_night`，采样前位置 `[4.079615,79.5,12600.217451]`、方向 `[89.99999,0]`。21:26:39 原始截图已人工目视检查：正常第一人称城市画面及 HUD，不是菜单、加载画面或睡眠界面。
- 准备 B2 时才临时使用 `entryBase=1.0/entryCap=1.0`；正式采样前停服恢复。八个前后快照均重读到 `entryBase=0.02/entryCap=0.5`，对应 TOML SHA-256 均为 `22c66c8fbc8e8e597bc869cc186a30e0b8dbbb3a6c2b7cd9c824b49266724b59`。
- C 使用 49x49 平台、禁自然刷怪、4x4 的 16 个持久化绮语父实体，由真实 AI 生成带归属的子实体。没有 `NoAI`；没有用直接召唤无归属子实体冒充真实召唤链。21:36:50 截图已目视检查，显示玩家被实体包围；不是用于衡量远距渲染的画面。
- 三个玩家场景均开启 resistance 4、saturation 1、knockback resistance 1；每 10 秒健康检查均为 20，窗口内未发现死亡或断线，维度起止一致。防护与持久化均属合成条件，不外推为生产玩法。
- **站位限制：C 初始布置为 `[0.5,100,0.5]`，采样前已被挤到 `[-14.937141,100,5.822418]`。** 无主动移动输入不等于固定坐标；抗击退也不保证阻止实体碰撞。此次未逐秒记录位置、未在结束时读取 Pos，不能声称整窗严格固定站位。数据可用于真实工作负载诊断，尚不足以验收严格固定站位的优化前后收益。
- 本次为新世界、单玩家、早期城市和 16 父实体规模。尚未测试多人重叠区域、高日数波次、32/64 父实体、跨区块游离召唤者、长期账本规模；不能从区域总实体数直接决定有限 cap 默认值。
- load1 范围：A 0.132 至 0.994；B1 1.432 至 2.083；B2 1.477 至 2.170；C 1.642 至 3.692。包含软件客户端负载，不能只凭 load1 把本环境称为绝对无噪声。正式窗口外 B1/B2 准备/预热出现过加载落后提示，不混入本表 300 秒；需要重复运行量化方差。
- 每秒五维度计数与 C 的 NBT 计数本身有开销，后者能在火焰图看到；未来对照必须同频率执行，并另测无探针开销。没有扣掉或假定为零。

首轮 [bf8uw90DTk](https://spark.lucko.me/bf8uw90DTk) 空载保留作启动恢复证据；它使用不同 CPU、亲和性和 jar 构建摘要。不得把其 0.411 ms 与本次 A 的 0.887 ms 相减，称为改善或回退。

## 可复用世界快照

所有路径相对于上面的 Actions 制品。快照在正常停服后生成；前快照是对应场景准备完成、正式重启之前，后快照是采样完成并正常停止之后。以下为压缩文件原字节 SHA-256，不是树哈希。

| 场景 | before 快照与 SHA-256 | after SHA-256 |
| --- | --- | --- |
| A | `fixtures/A-before.tar.gz`；`5fbe667e1ce72d9d18651a925233e6fb1a7e7405e3551d1e1c1a619f29e84633` | `c2cec0548c343f4562fc0a6c110bb86a79cac9d38afd246e2b51c887be09fb46` |
| B1 | `fixtures/B1-before.tar.gz`；`b11993c7a340b2c1e5677549fe0fdba61b4a8e512122a11556d90f62a8aabe62` | `b9fa45580be30b9fa8c916870490bb9b0b8a021e9644aceb8c1e5b637ebfea0a` |
| B2 | `fixtures/B2-before.tar.gz`；`14614856dd4c60c63290114261f448d0834ca29e9a324f696f7a74315ece03d6` | `47db722103fc309d331f94a1f2018e1e4635b5d31349ce6fe1d33dd2fa803eb2` |
| C | `fixtures/C-before.tar.gz`；`e69395a2ab8be8ac35ae23a96e53ca02afa5587fc4ae45d8a6425b60b552fe2f` | `0a0b6da72b88aae87ba3ddb13cb6f301441cf6e61f01ae5febd30a6df29f7e5c` |

## 实测调用路径与后续动作

查看完整火焰图后，已见雨寂实体 AI、永夜缠影 AI/光照和召唤场景追逐/战斗调用。详细口径见 [hotspots.md](../hotspots.md#本轮实测调用路径)。最大单条包含耗时不是方法自身 CPU，也不能把父子节点相加。没有证据证明先前静态候选“雨寂重复计数”就是全局第一热点。

1. 保留这批可核查的服务端硬数据；先从固定快照恢复，在同一 runner 内重复基线并记录逐秒 Pos/方向、区块及波次。解决 C 碰撞推位与严格站位定义的冲突，再做同条件候选对照。
2. PR2 继续沿已写的全局/区块配额清单设计；有限默认 cap 尚缺多人/高日数/32 与 64 父实体证据及玩法范围确认。不在文档 PR 中混入运行时实现。
3. PR3 用具体调用路径与探针开销来决定缓存优先级；PR4 必须另取真实客户端 FPS、帧时和分配证据。当前没有优化后数字。
4. 修复/定位既有 Gravity 真实客户端入水验证的失败，保持所有门禁；不能因为 434 个 GameTest 通过就宣称 8 项 CI 全绿。

## PR 提交级验证

这批性能数据绑定 `bb325dc` 的运行时，不能替代后续文档 HEAD 的 Mixin/CI 检查。`bb7d2411ec1581a70d5f678292aac61ef40a364b` 提交后的真实记录如下；下一文档提交重新触发检查，其结果写到 PR 评论，避免为刷新状态不断制造新 HEAD。

| 工作流 | bb7d241 结果 |
| --- | --- |
| Brickrot Validation | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966801557)；原始日志为默认 `runGameTestServer`，434/434 required passed |
| Yuji City validation | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966801592)；Mixin 14/0；子集不冒充全量 |
| Enhancement Pill Validation | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966801560) |
| Yuji Fodu validation | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966801618) |
| Neon Tumor validation | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966801626) |
| Shadow Tangler Validation | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966801613) |
| Shaxiadao Validation | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966801581) |
| Gravity Jade Validation | [failure，attempt 1 与唯一一次复验 attempt 2](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966801616)；两次均 Mixin 14/0、默认434/434 passed，但真实双客户端入水步骤失败 |

Gravity [attempt 1 job 117195165038](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966801616/job/117195165038) 于 21:19:14 报 `AssertionError: entering water while deployed keeps wings open`，`water=false`，位置 `(173.6559,95.9367,0.5)`。不改代码/门禁，只进行一次同 HEAD 复验；[attempt 2 job 117203973825](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38966801616/job/117203973825) 于 21:43:06 同断言失败，`water=false`，位置 `(163.5497,96.6537,0.5)`。日志里的 wings deployed=true 不能解释为已经进水，也不能无证据归因为 flaky。保留失败，不无限重试。

**结论：构建/专服启动和四个真实 profile 已完成；当前 CI 为 7 success / 1 failure，严格固定站位重复对照、真实客户端 FPS、有限 cap 决策与优化后验证未完成。T2 GOAL 不完成、不合 main、不 force push。**
