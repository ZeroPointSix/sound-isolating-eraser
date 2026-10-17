# T2：性能排查证据与分 PR 关口

> PR1 保留源码证据、上游对照、采样手册、四个真实 300 秒服务端 spark 窗口，以及四个约 300 秒的客户端逐帧 FPS 窗口（llvmpipe 软件渲染诊断基线，不是硬件 GPU 性能或改善报告；C 存在推位和遮挡）。独立 PR2 的实现、测试与限制见[刷怪准入说明](pr2-spawn-caps.md)。PR1 基线与 PR2 结果分开记录，不把基线数字冒充优化收益。

- 任务：[ZER-1542](https://linear.app/zerodotsix/issue/ZER-1542)。
- 审计基线：`e1703317583858b73fb1cd29c3db3f0a177c068b`，原 `mod_version=0.14.1`。
- 环境：Minecraft `1.20.1`、Forge `47.2.0`、Java `17`、official mappings `1.20.1`。
- 证据日期：2026-10-16 至 2026-10-17 UTC。PR1 仅新增本目录并已将 `mod_version` 提升至 0.14.2，不修改运行时、Mixin 或 CI；本次文档续补不重复 bump。
- 最新审查缺口是客户端 FPS。本次复用成功 run38992181049 补入原始数据及限制，不自称独立审批或启动 PR2/3/4。

## 阅读入口

| 文档 | 用途 |
| --- | --- |
| [hotspots.md](hotspots.md) | 文件、方法、触发条件、调用频率与风险；区分已存在的优化和待测候选 |
| [PR1 恢复与补充审计](evidence/2026-10-17-pr1-scope-audit.md) | 固定提交恢复证据；补齐砖蚀、光照、逐帧分配与 State 分类；记录仍未覆盖的验收 |
| [upstream-comparison.md](upstream-comparison.md) | 八个指定项目的一手资料、可迁移方法、版本及语义限制 |
| [spark-runbook.md](spark-runbook.md) | 标准启动、三类场景的四个正式窗口、300 秒采样、数据口径、环境证据与复测步骤 |
| [四窗口诊断基线](evidence/2026-10-16-four-window-baseline.md) | A/B1/B2/C viewer、MSPT/实体数、场景与站位限制、固定世界快照、恢复命令及逐项 CI |
| [客户端 FPS 基线](evidence/2026-10-17-client-fps-baseline.md) | A1/B1/B2/C 实测均值/1% low、帧时间方法、环境/位置/实体数、原始 CSV 与软件渲染限制 |
| [首轮生产专服与空载基线](evidence/2026-10-16-production-idle-baseline.md) | 固定 bb325dc 构建、生产 Forge 启动/存活/停服、真实 300 秒 spark、MSPT 与计数纠错 |
| [pr2-spawn-caps.md](pr2-spawn-caps.md) | 独立 PR53 的共享配额、按区块缩放、SERVER config、默认策略、14项新测试及已知边界 |
| [PR2固定站位对照](evidence/2026-10-16-spawn-cap-comparison.md) | 同机原版/cap32/默认/原版四个300秒窗口；cap32策略收益与默认配置未证实提速分开报告 |
| [pr2-spawn-cap-plan.md](pr2-spawn-cap-plan.md) | PR1 阶段历史候选方案；不代表 PR2 当前实现状态或默认配置 |
| [缓存与离线启动复核](evidence/2026-10-16-codexweb-cache-check.md) | 19:52 UTC 离线 runServer 的插件缺失历史证据；该次未启动/采样，不代表后续 CI 替代路线仍失败 |
| [网络分层诊断与 Gradle 恢复](evidence/2026-10-16-network-diagnosis.md) | 19:37 后新增的 DNS/TCP/TLS 证据、官方 Gradle 替代入口、插件解析失败与现有环境负载 |

## PR1 基线状态

本表仅保留PR1当时的四场景记录；下表“优化后未实施”指该次运行，不能据此推断PR2尚未开工。PR2另建严格站位C场景：[实测报告](evidence/2026-10-16-spawn-cap-comparison.md)中cap32平均MSPT为8.237ms，原版重复12.420–13.846ms；默认13.208ms仍落在基线波动范围。两组fixture/机器不同，不直接相减。A/B1/B2优化后对照和FPS仍未补齐。

| 场景 | 基线 spark | 基线 MSPT / 实体数 | 基线客户端 FPS | 优化后 |
| --- | --- | --- | --- | --- |
| A：空载专服，0 玩家 | [300.081 秒](https://spark.lucko.me/QMZXUwQZ26) | 均值 0.887 / P95 1.410 ms；全局 110 | 不适用 | 未实施 |
| B1：雨寂，1 玩家 | [300.072 秒](https://spark.lucko.me/BQ1iJ9qI3b) | 均值 5.000 / P95 6.135 ms；雨寂 37 至 42，全局 272 至 277 | 软件渲染 9.725 / 1% low 7.243 | 未实施 |
| B2：永夜城市，1 玩家 | [300.089 秒](https://spark.lucko.me/Scz8kd12Ic) | 均值 3.785 / P95 4.799 ms；永夜 8 至 11，全局 252 至 255 | 软件渲染 8.464 / 1% low 5.708 | 未实施 |
| C：真实召唤物，1 玩家 | [300.088 秒](https://spark.lucko.me/aCKjSDJu2S) | 均值 7.230 / P95 12.921 ms；全局 170 至 204，父 16 / 归属子 64 | 软件渲染 7.525 / 1% low 5.016；有推位 | 未实施 |

客户端另有 A1 驻足 1 玩家窗口：平均 11.046 FPS / 1% low 7.930，不能替换 A 的 0 玩家定义。FPS 和服务端 spark 是不同时间窗口、不同实体数，表中不能理解为同步测量；FPS 原始计数及完整元数据见[客户端报告](evidence/2026-10-17-client-fps-baseline.md)。新四窗实测时 maxFps=260、VSync=false；不把旧 20 限帧或新 260 上限当 FPS。客户端计数聊天叠层和 C 位移均已披露，硬件 GPU 与严格配对前后比较尚未完成。

原任务的三类场景是空载、城市/雨寂、大量召唤物；城市与雨寂必须分开采集，所以共四个窗口，B2 不是可选扩展。每个窗口为 6000 ticks、300 行实体快照；MSPT 是 spark 结束时 last5m 滚动摘要。四窗口均有真实数据，但 C 在采样前被实体推离初始站位，未连续记录坐标，不能当作严格固定站位的前后验收。单玩家、早期城市与 16 父实体也不足以决定有限 cap 默认值。详见[完整口径和限制](evidence/2026-10-16-four-window-baseline.md)。没有可验证的改善百分比，软件渲染上限 20 不是实测 FPS。

首轮空载 [bf8uw90DTk](https://spark.lucko.me/bf8uw90DTk) 保留为启动恢复证据；CPU、亲和性和构建摘要不同，不与本表相减。后续 PR2 可以基于这些硬数据继续设计，但仍须落实统计/玩法范围、默认策略和可比较的复测；PR43 不混入运行时代码。

## 已核实与未核实

已核实：雨寂 Ambient 计数在预算循环内重复执行；无有效目标时的 Yuji 搜索分支缺少独立冷却；爬行 IK 短数组位于 `SuxiaModel`；`GravityClient` 在渲染阶段复制 `PoseStack`；雨寂推进时间会反复标脏；多个持久化账本类内未见容量清理。详见固定 SHA 的源码链接。

不能据此认定哪个候选占用最多 CPU、造成多少 GC 或能降低多少 MSPT。也不能把以下原有优化记到本轮：城市每 100 tick 刷怪、雨寂预算轮转及每 20 tick 维护、避光搜索分片/缓存、霓虹寻光 200 tick 间隔、绮语局部召唤上限。

## PR1 验证记录

PR2 开工前回源核验 `9955ecd3b96bdfa9a58f8586a18851319fdc8c24` 的八项 Actions 均成功；Gravity 原始日志明确记录默认434项必需 GameTest 全通过及 Mixin14/0。以下更早失败仍作为历史保留，不覆盖该准确提交的成功结果，也不自动继承到 PR2。PR2 的失败、修正及准确 HEAD 门禁单独见[实施说明](pr2-spawn-caps.md#验证门禁)和[PR53](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/53)。

2026-10-17 00:05 UTC 回源：恢复时 HEAD `9955ecd3b96bdfa9a58f8586a18851319fdc8c24` 的八项 Actions 均为 success；同 SHA 的原始日志确认默认 434/434 必需 GameTest 和 Mixin 14/0。四份专服采样仍绑定 `bb325dc` 运行时，不是此次文档补充新测的数据。逐项链接及证据范围见[恢复记录](evidence/2026-10-17-pr1-scope-audit.md)。本段只证明指定 SHA；本文档补充提交后的结果写到原 PR 检查/评论，不继承旧 HEAD 的成功，也不为刷新状态反复制造新提交。

21:43 UTC 固定记录：准确源码 `bb325dc` 在独立 GitHub runner 正常构建，四个生产专服 spark 窗口完成、全部正常停服；无需修改 foojay、项目依赖或网络权限。验证脚本仅在独立分支，未修改本 PR 或既有 CI 门禁。`bb7d241` 已核实默认完整 GameTest 434/434、Mixin14/0，但八项 CI 为 7 success / 1 failure：Gravity 真实双客户端入水断言两次均 `water=false`。完整证据和逐项链接见[四窗口记录](evidence/2026-10-16-four-window-baseline.md#pr-提交级验证)。这些结果绑定指定 SHA，不自动继承到下一文档提交；当前 HEAD 状态以原 PR 的逐项检查记录为准。

2026-10-16 15:52:58 至 15:53:00 UTC，基线独立源码快照执行：

```text
python3 tools/verify_mixin_targets.py
MIXIN_TARGETS checked=14 failures=0
exit_code=0
```

这是 **基线** 结果，不能冒充 PR HEAD 的验证。本轮另外在新建隔离沙箱对 PR 提交 `509d3e5`、`fef5dd4` 分别验证 Mixin `14/0` 和差异空白检查。Java 17 及官方 JDK 归档 SHA-256 已核验；`official 1.20.1` 是已核对的 mappings 配置，不是已下载并校验 mappings 文件的结论。完整构建卡在 Gradle 下载，Java 与 curl 均复现连接重置；2026-10-16 16:39 UTC 的 curl 复测中 Gradle、Forge、Cloudsmith 三端点仍失败，本地专服与 GameTest 尚未启动。详见 [原始证据](evidence/2026-10-16-isolated-validation.log)。

PR1 的全量 GameTest、构建及 CI 结果应以 [PR #43](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/43) 当前 HEAD 检查和 [采样手册的验证记录](spark-runbook.md#验证记录) 为准。未运行、失败或排队必须单独写明；已有 CI 绿色结果不能追认尚未采集的 spark 数据。PR2结果单独见PR53，不混用提交身份。

19:42 UTC 历史记录：当时已从官方 GitHub 发行资产恢复并校验 Gradle 8.1.1 / Java 17，实际构建推进到 `settings.gradle:12` 的 foojay 插件解析后退出 1；该次本地专服和采样未开始。主力开发机依赖访问恢复，但负载不符合可靠基线条件。详见[当时诊断](evidence/2026-10-16-network-diagnosis.md)，其中明确区分已证实的握手重置与尚未核实的组织级策略假设；不覆盖后续四窗口采样成功的事实。

## 四个 PR 的边界

| PR | 允许改动 | 开始条件与回归重点 |
| --- | --- | --- |
| 1，基线 PR43 | `docs/perf/` 和版本号 | 审计证据、来源、复现命令可核查；数据缺失明确标注；不含运行时优化 |
| 2，刷怪 cap/config | 全局/区块预算与配置 | 先有有效基线；明确全局是跨维度还是单维度、计入哪些自定义实体、已存在实体处理方式；验证重叠玩家、同 tick 连续生成、失败回滚、跨区块/维度和重启 |
| 3，tick 扫描缓存 | 经采样证实的高频读取 | 使用 PR2 的已验收版本作为对照；验证目标及时失效、移动/传送/卸载、缓存有界、同 tick 计数一致性；保留原有战斗和寻路行为 |
| 4，客户端渲染 | 远距剔除、分配削减 | 真实客户端 CPU/分配与帧时证据；验证距离边界、巨型实体包围盒、轮廓、发光/粒子/名字、相机内外切换；不以剔除替代服务器 tick |

每个 PR 都独立 bump `mod_version`，跑 `tools/verify_mixin_targets.py`、全部默认 GameTest 和对应 CI。若增加 Mixin，遵守 MCP+SRG 双名及 `remap=false`；本文档 PR 不增加 Mixin。不 force push，不自动合并 `main`，不触发正式发布。

cap 导致实体总量减少的收益与同等实体数量下的效率收益要分开报告。每一轮用同一世界副本、配置、玩家路径和硬件复测，不能拿不同机器或不同场景相减。
