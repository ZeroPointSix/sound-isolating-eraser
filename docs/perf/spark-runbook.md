# spark 三类场景、四个正式窗口采集手册

本手册针对 `e1703317583858b73fb1cd29c3db3f0a177c068b`。命令及场景定义可供下一轮复现；没有实际运行日志的步骤不标作已执行。本文只提供隔离测试步骤，不连接或更改生产专服。

## 回源发现

| 来源 | 已确认的入口 | 对性能采样的意义 |
| --- | --- | --- |
| [`build.gradle:60`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/build.gradle#L60) | `server` run 使用项目 `run/`，添加 `--nogui`；标准任务 `runServer` | 可在独立检出中启动持续运行的 Forge 专服，不必等待现有生产入口 |
| [`README.md:107`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/README.md#L107) 与 `build.gradle:103` | 默认 `runGameTestServer` 运行注册测试后退出 | 用于功能验证，不能当持续 300 秒的场景；不得用 `performanceOnly` 子集冒充全部默认测试 |
| [`tools/yuji/city_e2e.py`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/tools/yuji/city_e2e.py) | `-PcityE2E writeCityTestClasspath`；脚本默认回环端口 25591，可用 `--port`、`--timeout`、`--skip-build` | 真服务端/客户端功能流程；脚本管理生命周期并结束测试，不能不加区分地用它采五分钟 |
| [`tools/night-e2e.py`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/tools/night-e2e.py) | `-PnightE2E writeNightTestClasspath`；默认 `NIGHT_QA_PORT=25587`；server 的 stdin PIPE 支持 `save-all flush` 与 `stop` | 可参考已验证的 Forge 启动元数据和控制台生命周期；不改该脚本，不把其自动场景冒充本次压力场景 |
| [`RainCityEvents.java:35`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/yuji/city/RainCityEvents.java#L35) | 玩家命令 `/yuji enter`、`/yuji leave`、`/yuji status`；enter/leave 需权限 2 | 雨寂必须通过正常入口登记玩家状态，不只用 `/execute in ... tp` 绕过入口 |
| [`RainCityContent.java:29`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/yuji/city/RainCityContent.java#L29) 与 [`EternalNightContent.java:27`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/eternalnight/EternalNightContent.java#L27) | 雨寂 `qihai:yuji_fodu`，永夜 `sound_isolating_eraser:eternal_night` | 两个不同维度，不能在同一 300 秒窗口中混采后归因 |

对 README、build.gradle、gradle.properties、tools、CI、src/main 和主要验收文档的固定版本文本检索未发现 spark 配置或依赖。现有目录列表未提供已登记的目标场景性能存档/专服控制台。本次选择新建隔离实例；历史 `docs/evidence`/`docs/validation` 日志不当作当前活跃环境或性能数据。

## 建立独立实例

使用空目录，不能复用或清理他人工作区。以下为源码运行路径，保留日志及命令退出码：

```bash
git clone https://github.com/ZeroPointSix/sound-isolating-eraser.git t2-perf
cd t2-perf
git switch --detach e1703317583858b73fb1cd29c3db3f0a177c068b
java -version
python3 tools/verify_mixin_targets.py
bash ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx2G build
bash ./gradlew --no-daemon --console=plain runServer
```

Java 必须为 17。第一次启动按终端日志生成 `run/`；仅在同意 Minecraft EULA 后设置 `run/eula.txt`。在服务器关闭时编辑隔离实例的 `run/server.properties`，至少固定以下值，再重新启动：

```properties
server-ip=127.0.0.1
server-port=25592
level-name=world
level-seed=20261016
max-players=2
view-distance=8
simulation-distance=6
difficulty=normal
spawn-protection=0
enable-rcon=false
```

端口使用前检查占用；若换端口，写入记录而不是抢占已有服务。专服进程与真实客户端尽量放在不同的受控 CPU/GPU 环境。回环隔离功能测试可以按现有 E2E 使用离线模式，但不得把未认证的离线专服开放到公网。不要为采样开启公网 RCON；需要远程连接时使用已授权的安全隧道。

安装独立 **Forge 1.20.1** 兼容 spark jar 到 `run/mods/`，记录下载来源、版本和 SHA-256。不要误装 Bukkit 插件、Fabric 或其他 Minecraft 版本。依赖从本仓库 Gradle 定义解析；不修改 build.gradle 或把 profiler 写入正式模组包。spark 需要重启后加载，使用 `/spark`；客户端安装的是对应版本的客户端包，使用 `/sparkc`。[spark 官方安装说明](https://spark.lucko.me/docs/Installation)

如果 Gradle 终端未转发 stdin，用已授权 OP 玩家执行下述带 `/` 命令；不要把“进程启动”当成控制台可用。进入采样前必须确认日志中 Forge、模组、spark 加载成功，客户端确实登录，并能执行 `/spark profiler info`。只创建 GameTest 假玩家不等于复现 `ServerLevel.players()` 驱动的雨寂刷怪。

开发客户端可在另一个终端用 `bash ./gradlew --no-daemon --console=plain runClient` 启动，再连接 `127.0.0.1:25592`。下文 `PerfUser` 为玩家名占位符，须替换为该客户端的实际名字并在前后运行保持同一身份。仅有无 GPU 的软件渲染环境时，可以验证流程，但 FPS 验收仍标“待真实客户端采集”。

## 三类场景

原任务的三类场景是空载、城市/雨寂、大量召唤物。城市/雨寂按两个独立区域处理：A、B1、B2、C 都必须正式采样 300 秒，合计四个窗口；B2 不是后续可选项。这是合成、可重复的测试定义，不代表已取得用户生产存档。各场景先准备并正常 `save-all flush`、`stop`，复制关闭后的整个世界和配置为只读基准副本。每次基线/候选运行从副本新建工作目录，保留原副本；不能在同一世界连续升级后直接比较。

### A：空载专服

1. 固定世界 seed、难度、视距、模拟距离与完整模组清单，正常启动后等待区块与资源加载稳定。
2. 采样窗口内保持 0 个在线玩家，不运行 GameTest、不预生成区块、不混入其他构建。
3. 预热至少 120 秒，记录实际时刻、游戏时间和是否仍有明显首次加载；再采样 300 秒。
4. FPS 填“不适用”。如果需要玩家驻足空载对照，另开 A1（1 玩家），不能把 A 和 A1 混为同一基线。

### B1：雨寂区域，正式 300 秒

1. 使用相同用户名/UUID 的真实 Forge 客户端进入隔离专服，权限 2，执行 `/yuji enter`，等待正常入口和区块加载完成。
2. 执行 `/yuji status` 并记录输出；固定位置、相机方向、玩家模式、装备与防护、难度、白天/夜晚、进入后的日数和波次状态。不要用创造/旁观者代表生存玩家，刷怪代码会过滤它们。
3. 首轮完成准备后保存世界副本。每次从同一副本启动、同样的登录/预热流程，窗口内固定维度 `qihai:yuji_fodu`，不传送、不重设时间、不更换装备。
4. 采样 300 秒。记录是否有死亡、断线、波次切换、卡加载等事件；偏离定义的运行作废，不挑选其中较好的片段。

### B2：永夜城市，正式 300 秒

永夜与雨寂是不同维度。正常入口是主世界深睡后按配置概率进入，不存在本文可用的 `/night enter` 命令；不能只传送坐标绕过 Visiting 状态和入城队列。入口控制流见 [`NightEvents.java:52`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/eternalnight/NightEvents.java#L52)，概率配置见 [`NightConfig.java:17`](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/e1703317583858b73fb1cd29c3db3f0a177c068b/src/main/java/com/zeropointsix/eraser/eternalnight/NightConfig.java#L17)。以下为待执行的隔离存档准备流程，不是已运行证据：

1. 使用独立世界副本，正常停服后备份 `run/world/serverconfig/eternal-night-server.toml`。仅为准备入口，在 `[eternalNight]` 下临时设置 `entryBase=1.0`、`entryCap=1.0`；这两个值在现有配置合法范围内，不改运行时代码或刷怪配置。
2. 启动后让固定身份的真实生存玩家在主世界夜间安全床位正常深睡，等待概率判定、入城队列、到达睡眠及唤醒完成。确认玩家确实位于 `sound_isolating_eraser:eternal_night`，记录区域、坐标、装备、防护和模式。
3. 正常 `save-all flush`、`stop`，恢复备份中的原始概率配置，不能把临时 100% 入口概率带进正式窗口。默认值为 `entryBase=0.02`、`entryCap=0.50`；若原值不同，恢复原值而不是覆盖成默认值。
4. 将入城后的整个世界和恢复后的配置保存为 B2 只读基准副本。每次从副本启动，以同一用户名/UUID 登录，固定城市区域、相机和装备；记录配置与副本标识。不要在采样窗口内再次入城、传送或生成新区块。
5. 完成至少 120 秒稳定预热后采样 300 秒，记录死亡、退出、重生、区块生成等异常；偏离场景定义的运行作废。B2 的 spark、MSPT、实体数和客户端 FPS 独立列项，不与 B1 平均。

### C：大量真实召唤物

采用绮语的实际召唤路径，不用 `/summon suxia_summon` 等无归属实体替代完整生命周期。只在本场景的新世界副本执行以下布置，固定一个生存玩家 PerfUser；不得在生产世界执行填充命令。防护、禁自然生成和场地均属于合成测试条件，必须随结果记录。实体 AI 必须开启。

```text
/difficulty normal
/gamerule doMobSpawning false
/gamemode survival PerfUser
/effect give PerfUser minecraft:resistance 1000000 4 true
/execute at PerfUser run fill ~-24 ~-1 ~-24 ~24 ~-1 ~24 minecraft:stone
/execute at PerfUser run fill ~-24 ~ ~-24 ~24 ~11 ~24 minecraft:air
```

两个 fill 分别为 2401 和 28812 个方块，低于原版单次填充上限。先完成区块加载和上述布置，再备份世界；不要把生成平台的时间计入正式窗口。

在客户端所在普通测试维度准备 4x4 的绮语网格：x/z 相对玩家偏移分别为 -12、-4、4、12，共 16 个父实体。每个点执行以下命令并替换偏移；`Tags` 只用于计数，不改变 AI：

```text
/execute at PerfUser run summon qihai:qiyu ~-12 ~ ~-12 {Tags:["t2_parent"],PersistenceRequired:1b}
```

先观测 16 个父实体自然施放和子实体生成，逐级另建 32/64 父实体副本，不在正式采样中追加实体。选定最终规模后锁定副本及配置，并记录实际存活父/子实体数量与类型；不能把“16x4”当成已测实体数，因为范围、生成失败、战斗、移动和死亡都会改变数量。该合成场景的持久化标签与生产情况不同，结果必须注明。

计数可用原版命令做每秒快照，命令由控制台或 OP 执行。示例中的维度应替换成已选场景的固定维度：

```text
/scoreboard objectives add t2_perf dummy
/scoreboard players set all t2_perf 0
/execute in minecraft:overworld as @e[type=!minecraft:player] run scoreboard players add all t2_perf 1
/scoreboard players get all t2_perf
/scoreboard players set parents t2_perf 0
/execute in minecraft:overworld as @e[type=qihai:qiyu,tag=t2_parent] run scoreboard players add parents t2_perf 1
/scoreboard players get parents t2_perf
```

计数脚本本身会遍历实体，采样前后使用相同频率，并记录这部分测量开销。总数包含非怪物实体；按类型补充计数，不能直接用总数减父数声称得到召唤子实体数。卸载实体不在 `@e` 可见集合内，计数口径写明“当前已加载实体”。不要用大范围 `/kill @e` 清理复用世界，应正常停服后使用新的副本。

## 300 秒采样与产物

A、B1、B2、C 四个正式窗口分别执行，不能用一个维度的结果代替另一个：

```text
/spark profiler info
/spark health show
/spark profiler start --timeout 300
```

确认前一个 profiler 已结束后再开始。结束时保存 spark 实际返回的 viewer 链接、服务器日志中的开始/结束时刻及完整窗口信息。网络上传失败时按安装版本的帮助使用本地保存，保存原始 profile 文件和失败信息；没有可访问 URL 就明确写“本地数据，未上传”，不要猜造链接。命令语义以 [spark Command Usage](https://spark.lucko.me/docs/Command-Usage) 为准。

基线不能使用 `--only-ticks-over` 过滤长 tick 后又声称代表所有 tick。CPU 采样与 `--alloc` 分配采样分开运行，且前后使用同一采样配置。服务器 profile 和客户端 `/sparkc` profile 分开记录；服务器报告不能生成客户端 FPS。

结束后再保存一次 `/spark health show` 或对应版本支持的 health 上传结果，作为环境与滚动指标快照。它们不自动等于完整 300 秒的均值/分位数；如果当前 spark 版本没有导出对应窗口指标，完整窗口字段仍记“未取得”，补充受控指标采集后再验收，不能用两张快照拼造分布。

建议记录项：

| 字段 | 口径 |
| --- | --- |
| 构建 | 完整 commit、mod_version、jar SHA-256、模组列表与 spark 版本 |
| 环境 | OS、CPU、实际容器 CPU/内存限制、JDK/JVM 参数、GC、世界/配置副本标识；客户端另记 GPU/驱动/分辨率/画质/VSync/帧率上限 |
| 窗口 | 场景名、UTC 开始/结束、实际秒数、玩家/维度/坐标/模式、游戏时间、区块数 |
| tick | 完整窗口 MSPT 均值及来源实际提供的分位数、TPS；若只有滚动快照，明确标注，不称完整窗口分布 |
| 实体 | 每秒已加载实体计数、类型组成、最小/平均/最大/结束值，召唤父子口径，是否含 Horde |
| 客户端 | 帧时间记录及换算方法、平均 FPS/低帧指标；只有 F3 瞬时值时不得当成 300 秒均值 |
| 证据 | spark 链接/本地 profile、原始指标文件、日志、异常事件、是否有效 |

同一机器至少做重复运行检查方差。比较 cap 前后时分别报告“实体数下降的策略收益”和“相同实体数量下的执行效率”；profile 的 CPU 百分比不是 MSPT 改善百分比。

## 验证记录

- 基线 `e1703317583858b73fb1cd29c3db3f0a177c068b`：2026-10-16，独立源码快照，`python3 tools/verify_mixin_targets.py` 输出 `MIXIN_TARGETS checked=14 failures=0`，退出码 0；本轮隔离沙箱也复现该基线结果。这是原有代码校验，不是优化收益。
- 本轮隔离环境：任务专用沙箱，2 CPU / 4 GiB / 8 GiB。Temurin 17.0.20.1+1 已安装并执行 `java -version`，官方 JDK 归档 SHA-256 已验证；摘要见 [证据日志](evidence/2026-10-16-isolated-validation.log)。`gradle.properties` 的 mappings 配置已核对为 `official` / `1.20.1`；这不等于 mappings 文件已经下载或其摘要已核验。宿主机 `free`/`cpu_count` 不代表容器配额。
- PR 提交 `509d3e5b0f1f00a4f3741a100b42e9a6746f60f7` 与 `fef5dd4e724ef039010c0e7ebfa2837922de7ded`：在独立沙箱分别执行 Mixin 检查 `14/0`、退出码 0；各自 `git diff --check` 无输出、退出码 0。文档修正后的新提交须再跑相同检查，结果写入 [PR #43](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/43) 描述并绑定完整 SHA，不将这两个历史提交的结果当作新 HEAD 验证。
- 基线完整命令 `build runGameTestServer` 实际失败在 Gradle 8.1.1 分发下载阶段，Java 报 `java.net.SocketException: Connection reset`、退出码 1。**编译和 GameTest 都未开始，不是用例失败，更不是通过。**
- 对同一 Gradle URL 改用 curl，仍报退出码 35、`Connection reset by peer`；ForgeGradle 元数据与 GeckoLib POM 端点也同样失败，HTTP 状态为 `000`。2026-10-16 16:39:27 UTC 对下列三个 URL 的 curl 复测仍全部为退出码 35 / HTTP `000`。Gradle 有 Java 与 curl 两种客户端证据，Forge/Cloudsmith 此处记录的是 curl 探针，不把它们冒充成已执行的 Java 构建解析。GitHub 仓库与官方 JDK Release 下载成功，因此只能确认这些端点的连接问题，尚不能认定具体防火墙、代理或服务端根因。
- [脱敏终端证据](evidence/2026-10-16-isolated-validation.log) 保存命令、退出码、错误栈、域名、复测时间和两个 PR 提交的 Mixin 结果。没有关闭 TLS 校验，没有改项目下载源，没有借用高负载共享机生成基线。
- 本地专服/客户端尚未启动，A、B1、B2、C 均未采样，spark 链接、MSPT、实体数和 FPS 仍缺失。CI 在独立的 GitHub runner 上运行，结果不能反推本地构建或性能采样已完成。

### GitHub Actions 按提交记录

2026-10-16 16:42 UTC 回源查询的工作流级快照如下。首提交 `509d3e5` 为 **2 项成功、6 项 cancelled**；补证据提交 `fef5dd4` 当时为 **5 项成功、3 项运行中**。这是两个固定 SHA 的记录，不是后续新 HEAD 的结论。取消和运行中均不等于通过，不能汇总为 CI 全绿或全量 GameTest 已验收；单个 job 的绿色也不能替代所属工作流整体状态。

| 工作流 | `509d3e5` 结果 | `fef5dd4` 在 16:42 UTC 的状态 |
| --- | --- | --- |
| Brickrot Validation | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38931734661) | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38932666107) |
| Yuji City validation | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38931734792) | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38932666139) |
| Enhancement Pill Validation | [cancelled](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38931734775) | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38932666443) |
| Yuji Fodu validation | [cancelled](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38931734791) | [in_progress](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38932666068) |
| Neon Tumor validation | [cancelled](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38931734748) | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38932666149) |
| Shadow Tangler Validation | [cancelled](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38931734818) | [success](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38932666147) |
| Gravity Jade Validation | [cancelled](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38931734711) | [in_progress](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38932666078) |
| Shaxiadao Validation | [cancelled](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38931734698) | [in_progress](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38932666110) |

修正提交后的工作流链接和逐项状态保留在 PR 描述中，并标明查询时间与新 HEAD；不要为了更新 CI 状态反复制造文档提交，从而取消仍在运行的检查。

## 阻塞后的下一动作

19:42 UTC 的[后续分层诊断](evidence/2026-10-16-network-diagnosis.md)已从官方 GitHub 分发恢复 Gradle 8.1.1 / Java 17 并核对摘要，项目实际执行到 foojay 插件解析后失败。原 Gradle 服务入口仍重置，官方资产替代仅解决工具链启动，不代表 Forge、GeckoLib 或插件依赖已恢复。

当前缺的是隔离环境到公开依赖服务的正常出站连接，**不是待索取的密钥、生产控制台或已有存档**：`services.gradle.org`、`plugins.gradle.org`、`maven.minecraftforge.net`、`dl.cloudsmith.io`；`spark.lucko.me` 也需单独恢复/验证。DNS/TCP 可达而 TLS 被重置，不能把问题简化为缺 Java、缺 CA 或上游全面宕机。先核实组织级出站限制，或使用已有授权开发机的低负载独占窗口，不扩大网络权限、不用当前高负载窗口凑基线。修复后先运行下列探针，再运行完整构建命令。成功后按本文创建合成世界；默认目录为独立检出的 `run/world`，没有尚待人工提供的生产存档路径。

```bash
curl -fIL --connect-timeout 10 --max-time 30 https://services.gradle.org/distributions/gradle-8.1.1-bin.zip
curl -fL --connect-timeout 10 --max-time 30 -o /dev/null https://maven.minecraftforge.net/net/minecraftforge/gradle/ForgeGradle/maven-metadata.xml
curl -fL --connect-timeout 10 --max-time 30 -o /dev/null https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/software/bernie/geckolib/geckolib-forge-1.20.1/4.4.9/geckolib-forge-1.20.1-4.4.9.pom
curl -fL --connect-timeout 10 --max-time 30 -o /dev/null https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver-convention/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/0.5.0/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin-0.5.0.pom
curl -fL --connect-timeout 10 --max-time 30 -o /dev/null https://spark.lucko.me/
```

不要以关闭证书校验、修改 CI、公开离线专服、购买资源或无变化地反复重试同一失败请求来绕过边界。只有真实完成 A、B1、B2、C 四个正式窗口后才能填实测表并开始 PR2 的运行时优化。任务专用临时沙箱用后清理，不删除其他任务资源；持久证据保留在 PR。

无选择性子集的功能验证命令：

```bash
python3 tools/verify_mixin_targets.py
bash ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx2G build runGameTestServer
```

相关原有流程（功能验证，不是 spark 场景）：

```bash
bash ./gradlew --no-daemon -PcityE2E writeCityTestClasspath
python3 tools/yuji/city_e2e.py --skip-build --port 25591 --timeout 900
bash ./gradlew --no-daemon -PnightE2E writeNightTestClasspath
python3 tools/night-e2e.py
```

这些客户端流程需要仓库原有 Xvfb、xdotool 等依赖。对应 CI 为 Yuji City、Eternal Night 和 Gravity 等 validation 工作流；`gradle.properties` 的版本更新会触发已有相关 PR 检查。检查必须绑定当前 PR HEAD，不修改工作流来绕过失败，不触发主分支 Release。
