# PR2：固定世界、固定站位的刷怪配额对照

关联 [PR53](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/53) / [ZER-1542](https://linear.app/zerodotsix/issue/ZER-1542)。本页只验证 PR2 刷怪准入，不包含 PR3 扫描缓存或 PR4 渲染优化。PR1 四场景基线保持独立，不能与本页不同 fixture 的绝对值直接相减。

## 准确版本与复现

- 原版生产源码：`bb325dc9ccf3d99565d8a1eedb7efee372fdcd45`，mod `0.14.2`。
- PR2 生产源码：`8c49ff6a1dd98eb97c45e4222b7918f8b0191d69`，mod `0.14.3`。
- 采样工具：`41281d44ece41a70d835ba081c6fcba8139e2b50`，分支 `verify/t2-pr2-caps-20261016`。
- 原始运行：[38980531687](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38980531687)，job `117240086693`。
- 后续 PR2 提交 `0a66b3b` 和 `6e87df4` 仅修改测试，生产运行时与被采样的 `8c49ff6` 一致；不声称采样执行了后来的文档/测试 SHA。

准确命令与依赖安装见[隔离验证工作流](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/41281d44ece41a70d835ba081c6fcba8139e2b50/.github/workflows/t2-cap-comparison.yml)。三个工作树分别检出上述工具、原版、PR2 SHA；正常构建可分发 jar，不用 GameTest 替代专服。

```sh
(cd source && bash ./gradlew --no-daemon --max-workers=2 build)
(cd candidate && bash ./gradlew --no-daemon --max-workers=2 build)
(cd source && bash ./gradlew --no-daemon --max-workers=2 \
  -I ../harness/tools/perf/launch.init.gradle writeT2ClientClasspath)
(cd candidate && python3 tools/verify_mixin_targets.py)
python3 harness/tools/perf/test_probe.py
python3 harness/tools/perf/test_cap_comparison.py
T2_SOURCE_DIR="$PWD/source" T2_CANDIDATE_DIR="$PWD/candidate" \
  T2_HARNESS_SHA=41281d44ece41a70d835ba081c6fcba8139e2b50 \
  python3 harness/tools/perf/cap_comparison.py
```

沿用 Java17 / Forge47.2.0 / Minecraft1.20.1 / spark1.10.53，依赖校验和、硬件和进程参数保存在制品。此隔离 runner 能完成依赖解析和生产构建；没有修改项目 foojay 版本、网络权限或生产服务器，也不声称修好了先前受限容器的 TLS。

## 固定条件与计数口径

创建种子 `20261016`、视距8/模拟距离6的正常生产世界；49×49石平台上放置16个真实绮语父实体。仅在 fixture 准备阶段使用 `NoAI:1b`；每个正式窗口预热前恢复AI，子实体由原生产 `summonAllies` 自然生成，不用手工创建子实体冒充负载。专服只监听 `127.0.0.1:25592`，关闭 RCON/query；离线模式仅用于本机隔离测试。

每个窗口开始前恢复同一份正常停服后的世界文件，逐文件指纹必须完全一致，再写入该窗口的 SERVER 配置并选择生产 jar。客户端固定在 `(0.5,118,0.5)` 的1×2屏障空间内，方向180/8，防止碰撞推位；每秒读取真实 `Pos`，漂移超过0.01格立即失败，每10秒检查健康值，退出或死亡使窗口无效。屏障和高度会改变寻路/视线，本页是固定合成召唤场景，不代表任意实战站位。

顺序为原版 baseline-1、PR2压力配置 cap32、PR2默认配置 default、原版 baseline-2。每次重新启动生产专服及真实客户端，预热120秒，然后 `spark profiler start --timeout 300`。服务端和软件渲染客户端分别绑定不同物理核心；测试不使用已知高负载 VPS。

每秒 CSV 记录本维度 `@e[distance=0..]` 总实体、带父标签的绮语数、含真实 `YujiSummoner` NBT 的子实体数、站位和系统 load。全实体数包含玩家和其他类型，不能当作受控配额计数。cap32 窗口要求每行父+子不超过32且确有子实体；总实体数仍可超过32。原有每父4只、32格归属范围和200tick召唤冷却未改。

压力配置仅把 `dimensionCap` 从默认512改为32；两者均为 `chunkCap=64`、`entitiesPerLoadedChunk=2`、`minimumScaledCap=128`。固定维度上限仍能压过128 floor。32是验证限额的压力策略，不是建议替换默认值。默认512/64策略仍需要多人和晚期波次回归。

## 结果与限制

运行于2026-10-16 23:54:32 UTC完成，结论 `success`。以下数值来自原始报告和每窗口300行CSV，不是预估值。

| 窗口 / spark 火焰图 | 秒 / ticks | MSPT 均值 / P95 / 中位数（ms） | 父实体 / 归属子实体 | 主世界全部实体范围 |
| --- | --- | --- | --- | --- |
| [原版 baseline-1](https://spark.lucko.me/rglU9rv97s) | 300.105 / 5999 | 13.846 / 64.625 / 6.366 | 16 / 64 | 165–216 |
| [PR2 cap32](https://spark.lucko.me/p4Sysfp2WD) | 300.093 / 6000 | 8.237 / 25.720 / 4.218 | 16 / 16 | 117–166 |
| [PR2 默认配置](https://spark.lucko.me/H0VnE5jfJO) | 300.087 / 5999 | 13.208 / 57.194 / 6.296 | 16 / 64 | 164–234 |
| [原版 baseline-2](https://spark.lucko.me/fgw03jBDUN) | 300.112 / 5999 | 12.420 / 49.955 / 6.176 | 16 / 63–64 | 166–216 |

cap32的300行全部保持16父+16子，没有出现配额越界。相对前后两个原版窗口，平均MSPT分别下降 **40.51% / 33.68%**，P95分别下降 **60.20% / 48.51%**，计算为 `100 × (1 - cap32 / 对应基线)`。这是本固定合成场景内通过减少受控实体数量取得的策略收益，不是每实体AI效率提升，也不能直接推广至多人城市。

默认配置仍有16父+64子，其13.208ms均值位于两次原版12.420–13.846ms之间；相对前后基线分别为−4.61%与+6.35%。**没有证据证明默认配置提速，也不能据此证明额外计数开销为零。** 两次原版本身存在波动；不要只选较慢的第一段基线来宣称默认值改善。不同子实体种类、位置和投射物会变化，全部实体数也未逐tick完全匹配。

CSV实际采样时间：baseline-1 23:23:51–23:28:50 UTC；cap32 23:32:18–23:37:16；default 23:40:45–23:45:44；baseline-2 23:49:13–23:54:12。共1200行，所有玩家坐标均为 `(0.5,118,0.5)`；每窗最后一行约为299秒，spark本身均超过300秒。TPS last5m均约20。系统load1范围依次为1.140–2.557、1.176–2.882、1.423–3.017、1.284–2.596，不能把系统load当作MSPT。

MSPT 采用每个 spark 报告 `metadata.platformStatistics.mspt.last5m` 的结束时滚动摘要；采样时长从 `endTime-startTime` 计算，tick数单独记录。没有把单元测试耗时当MSPT。每个策略只有一个窗口，原版前后各一次，用于观察漂移，不构成统计显著性结论；实体自身随机数不是完全回放锁定。

软件渲染客户端设置20帧上限只是固定观察者负载，**不是实测FPS**；本页不提供GPU FPS或客户端帧时改善结论。限额减少实体数量的策略收益，也不能冒充同等实体数下的每实体算法加速。

本页没有补采PR2后的A空载、B1雨寂或B2永夜窗口，没有验证多人重叠范围和晚期波次的玩法平衡。默认值仍是兼容性优先的初始策略，不据此调低正式默认cap。PR3/4没有实施，T2整体目标尚未完成。

## 可审计原始证据

[原始制品12082326412](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38980531687/artifacts/12082326412)为60,002,237字节，下载后计算的SHA-256与GitHub提供值一致：

```text
3e011239ed3b3ec62203746a40deaff6ee0e68bd9b517b651d6ddcf36c6f6efb
```

制品保留30天，内有4份 `.sparkprofile`、完整spark元数据、4份300行CSV、逐命令与控制台日志、准备及各窗口前后世界归档、4张观察端截图。截图存在不等于FPS测量。关键数值、所有profile/世界归档摘要及生命周期另存[机器可读记录](2026-10-16-spawn-cap-metrics.json)。

- 机器：AMD EPYC7763，runner暴露4逻辑CPU/2物理核心；专服亲和性0,1，软件观察端2,3；Java17.0.20.1。不要与PR1使用的另一台机器直接做百分比对照。
- 原版jar SHA-256：`15da3d9575e35d32b2c74489a7a83d0fc0e461a32a25b4e6d49d98388493a28d`。
- PR2 jar SHA-256：`93ee12af73ccda0a579237404cc809888d9d43a854b1e622005c1a8ed5a8eae1`。
- 恢复前世界树指纹：`b74aeae075001e28b1e2a4fa00a44cbce3af5c5622b1031555bee2438d2ca45e`；准备归档SHA-256：`c5cd32fc0d3d51d1dc8e1b2f57614fd31a04ea455c07185a5a67bc55de898e73`。各窗口在指纹核对后写入对应配置，压缩归档摘要不要求相同。
- 固定观察端源码为`bb325dc`，没有更换客户端渲染实现；只切换专服生产jar和显式配额配置。
- 准备进程PID4224、四窗口PID4391/4701/4958/5267均记录 `Done`、存活和正常停止，5次均 `exitCode=0`、`graceful=true`、`aliveAfterStop=false`。末次停服23:54:24 UTC，无遗留采样专服。
- 两个生产源码均构建成功（28秒/27秒），候选提交独立 `verify_mixin_targets.py` 为14/0。这不代替PR HEAD的CI；`6e87df4`的默认448/448 GameTest与真实客户端结果在[PR2实施说明](../pr2-spawn-caps.md#验证门禁)单独绑定。
