# T2 PR1：性能排查基线与后续关口

> 当前交付包含源码证据、上游对照、采样手册及四个真实 300 秒服务端 spark 窗口，不是性能改善报告。严格站位重复对照、真实客户端 FPS 与优化后数据仍未完成。

- 任务：[ZER-1542](https://linear.app/zerodotsix/issue/ZER-1542)。
- 审计基线：`e1703317583858b73fb1cd29c3db3f0a177c068b`，原 `mod_version=0.14.1`。
- 环境：Minecraft `1.20.1`、Forge `47.2.0`、Java `17`、official mappings `1.20.1`。
- 证据日期：2026-10-16 UTC。PR1 仅新增本目录并更新 `mod_version`，不修改运行时、Mixin 或 CI。
- 最新任务允许未完成 spark 时提交明确阻塞与精确采样手册；这不等于放宽后续性能验收。

## 阅读入口

| 文档 | 用途 |
| --- | --- |
| [hotspots.md](hotspots.md) | 文件、方法、触发条件、调用频率与风险；区分已存在的优化和待测候选 |
| [upstream-comparison.md](upstream-comparison.md) | 八个指定项目的一手资料、可迁移方法、版本及语义限制 |
| [spark-runbook.md](spark-runbook.md) | 标准启动、三类场景的四个正式窗口、300 秒采样、数据口径、环境证据与复测步骤 |
| [四窗口诊断基线](evidence/2026-10-16-four-window-baseline.md) | A/B1/B2/C viewer、MSPT/实体数、场景与站位限制、固定世界快照、恢复命令及逐项 CI |
| [首轮生产专服与空载基线](evidence/2026-10-16-production-idle-baseline.md) | 固定 bb325dc 构建、生产 Forge 启动/存活/停服、真实 300 秒 spark、MSPT 与计数纠错 |
| [pr2-spawn-cap-plan.md](pr2-spawn-cap-plan.md) | 未开工的 PR2 文件/API/cap 算法/config/生命周期与回归清单；有限默认值待实测 |
| [缓存与离线启动复核](evidence/2026-10-16-codexweb-cache-check.md) | 19:52 UTC 离线 runServer 的插件缺失历史证据；该次未启动/采样，不代表后续 CI 替代路线仍失败 |
| [网络分层诊断与 Gradle 恢复](evidence/2026-10-16-network-diagnosis.md) | 19:37 后新增的 DNS/TCP/TLS 证据、官方 Gradle 替代入口、插件解析失败与现有环境负载 |

## 实测状态

| 场景 | 基线 spark | 基线 MSPT / 实体数 | 基线客户端 FPS | 优化后 |
| --- | --- | --- | --- | --- |
| A：空载专服，0 玩家 | [300.081 秒](https://spark.lucko.me/QMZXUwQZ26) | 均值 0.887 / P95 1.410 ms；全局 110 | 不适用 | 未实施 |
| B1：雨寂，1 玩家 | [300.072 秒](https://spark.lucko.me/BQ1iJ9qI3b) | 均值 5.000 / P95 6.135 ms；雨寂 37 至 42，全局 272 至 277 | 未采集 | 未实施 |
| B2：永夜城市，1 玩家 | [300.089 秒](https://spark.lucko.me/Scz8kd12Ic) | 均值 3.785 / P95 4.799 ms；永夜 8 至 11，全局 252 至 255 | 未采集 | 未实施 |
| C：真实召唤物，1 玩家 | [300.088 秒](https://spark.lucko.me/aCKjSDJu2S) | 均值 7.230 / P95 12.921 ms；全局 170 至 204，父 16 / 归属子 64 | 未采集 | 未实施 |

原任务的三类场景是空载、城市/雨寂、大量召唤物；城市与雨寂必须分开采集，所以共四个窗口，B2 不是可选扩展。每个窗口为 6000 ticks、300 行实体快照；MSPT 是 spark 结束时 last5m 滚动摘要。四窗口均有真实数据，但 C 在采样前被实体推离初始站位，未连续记录坐标，不能当作严格固定站位的前后验收。单玩家、早期城市与 16 父实体也不足以决定有限 cap 默认值。详见[完整口径和限制](evidence/2026-10-16-four-window-baseline.md)。没有可验证的改善百分比，软件渲染上限 20 不是实测 FPS。

首轮空载 [bf8uw90DTk](https://spark.lucko.me/bf8uw90DTk) 保留为启动恢复证据；CPU、亲和性和构建摘要不同，不与本表相减。后续 PR2 可以基于这些硬数据继续设计，但仍须落实统计/玩法范围、默认策略和可比较的复测；PR43 不混入运行时代码。

## 已核实与未核实

已核实：雨寂 Ambient 计数在预算循环内重复执行；无有效目标时的 Yuji 搜索分支缺少独立冷却；爬行 IK 短数组位于 `SuxiaModel`；`GravityClient` 在渲染阶段复制 `PoseStack`；雨寂推进时间会反复标脏；多个持久化账本类内未见容量清理。详见固定 SHA 的源码链接。

不能据此认定哪个候选占用最多 CPU、造成多少 GC 或能降低多少 MSPT。也不能把以下原有优化记到本轮：城市每 100 tick 刷怪、雨寂预算轮转及每 20 tick 维护、避光搜索分片/缓存、霓虹寻光 200 tick 间隔、绮语局部召唤上限。

## 验证记录

最新进展（21:43 UTC）：准确源码 `bb325dc` 在独立 GitHub runner 正常构建，四个生产专服 spark 窗口完成、全部正常停服；无需修改 foojay、项目依赖或网络权限。验证脚本仅在独立分支，未修改本 PR 或既有 CI 门禁。`bb7d241` 已核实默认完整 GameTest 434/434、Mixin14/0，但八项 CI 为 7 success / 1 failure：Gravity 真实双客户端入水断言两次均 `water=false`。完整证据和逐项链接见[四窗口记录](evidence/2026-10-16-four-window-baseline.md#pr-提交级验证)。这些结果绑定指定 SHA，不自动继承到下一文档提交。

2026-10-16 15:52:58 至 15:53:00 UTC，基线独立源码快照执行：

```text
python3 tools/verify_mixin_targets.py
MIXIN_TARGETS checked=14 failures=0
exit_code=0
```

这是 **基线** 结果，不能冒充 PR HEAD 的验证。本轮另外在新建隔离沙箱对 PR 提交 `509d3e5`、`fef5dd4` 分别验证 Mixin `14/0` 和差异空白检查。Java 17 及官方 JDK 归档 SHA-256 已核验；`official 1.20.1` 是已核对的 mappings 配置，不是已下载并校验 mappings 文件的结论。完整构建卡在 Gradle 下载，Java 与 curl 均复现连接重置；2026-10-16 16:39 UTC 的 curl 复测中 Gradle、Forge、Cloudsmith 三端点仍失败，本地专服与 GameTest 尚未启动。详见 [原始证据](evidence/2026-10-16-isolated-validation.log)。

当前 PR 的全量 GameTest、构建及 CI 结果应以 [PR #43](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/43) 当前 HEAD 检查和 [采样手册的验证记录](spark-runbook.md#验证记录) 为准。未运行、失败或排队必须单独写明；已有 CI 绿色结果不能追认尚未采集的 spark 数据。

19:42 UTC 更新：已从官方 GitHub 发行资产恢复并校验 Gradle 8.1.1 / Java 17，实际构建推进到 `settings.gradle:12` 的 foojay 插件解析后退出 1；本地专服和采样仍未开始。主力开发机依赖访问恢复，但负载不符合可靠基线条件。详见[本轮诊断](evidence/2026-10-16-network-diagnosis.md)，其中明确区分已证实的握手重置与尚未核实的组织级策略假设。

## 四个 PR 的边界

| PR | 允许改动 | 开始条件与回归重点 |
| --- | --- | --- |
| 1，本文档 | `docs/perf/` 和版本号 | 审计证据、来源、复现命令可核查；数据缺失明确标注；不含运行时优化 |
| 2，刷怪 cap/config | 全局/区块预算与配置 | 先有有效基线；明确全局是跨维度还是单维度、计入哪些自定义实体、已存在实体处理方式；验证重叠玩家、同 tick 连续生成、失败回滚、跨区块/维度和重启 |
| 3，tick 扫描缓存 | 经采样证实的高频读取 | 使用 PR2 的已验收版本作为对照；验证目标及时失效、移动/传送/卸载、缓存有界、同 tick 计数一致性；保留原有战斗和寻路行为 |
| 4，客户端渲染 | 远距剔除、分配削减 | 真实客户端 CPU/分配与帧时证据；验证距离边界、巨型实体包围盒、轮廓、发光/粒子/名字、相机内外切换；不以剔除替代服务器 tick |

每个 PR 都独立 bump `mod_version`，跑 `tools/verify_mixin_targets.py`、全部默认 GameTest 和对应 CI。若增加 Mixin，遵守 MCP+SRG 双名及 `remap=false`；本文档 PR 不增加 Mixin。不 force push，不自动合并 `main`，不触发正式发布。

cap 导致实体总量减少的收益与同等实体数量下的效率收益要分开报告。每一轮用同一世界副本、配置、玩家路径和硬件复测，不能拿不同机器或不同场景相减。
