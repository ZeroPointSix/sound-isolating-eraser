# 首个生产专服启动与空载 spark 基线

## 结论与范围

2026-10-16 20:52 UTC，固定 PR1 源码 `bb325dc9ccf3d99565d8a1eedb7efee372fdcd45` 已在独立 GitHub-hosted runner 完成标准构建、生产 Forge 专服启动、空载 300 秒 spark 及正常停服。依赖恢复的有效替代路线是已有 CI 环境，不是删除 foojay、降级插件、改 TLS 校验或扩张网络权限。

这证明此前“所有路径都无法启动专服”的阻塞已解除。它不证明 Daytona 出站网络已修好，也不证明全部性能验收完成。B1 雨寂、B2 永夜、C 真实召唤物、真实 GPU FPS、重复运行方差和优化前后对比仍待单独验收；PR2 尚未开工。

| 证据 | 固定入口 |
| --- | --- |
| spark 火焰图 | [bf8uw90DTk](https://spark.lucko.me/bf8uw90DTk) |
| 原始 metadata | [原始 JSON](https://spark.lucko.me/bf8uw90DTk?raw=1)，[选取字段存档](2026-10-16-idle-metadata.json) |
| 实际执行 | [run 38964330760](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38964330760)，[job 117186919839](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38964330760/job/117186919839)；success |
| 日志、CSV、世界、配置、摘要 | [artifact 12074462792](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38964330760/artifacts/12074462792)，5,621,388 bytes |
| 实际验证脚本版本 | [f2401047](https://github.com/ZeroPointSix/sound-isolating-eraser/tree/f2401047ceeae7ac28bf7397eef12cfab3317b30/tools/perf)，位于独立验证分支，未进入 PR43 |
| 后续计数修正 | [013f9706](https://github.com/ZeroPointSix/sound-isolating-eraser/commit/013f97061b004248a2ca6510536daa35522807e8)，维度限制及物理核心分组；后续四窗口不能冒充本次已完成结果 |

制品 ZIP 已实际下载到 CLI 进程内存并核对 SHA-256：`ae806063759798d9093961327bf208fa516550bdaf6beb524fc984b882c9a91e`。未在 CLI 网关创建源码工作区或保留构建制品。

## 数据与统计口径

| 项目 | 实测值与来源 |
| --- | --- |
| spark 开始/结束 | `1792183664637` / `1792183964674` Unix ms，即 20:47:44.637 至 20:52:44.674 UTC |
| 实际采样长度 | 300.037 秒，`numberOfTicks=6000`；async sampler，4 ms 间隔，未加长 tick 过滤 |
| MSPT 均值 | 0.4114875888333333 ms，`metadata.platformStatistics.mspt.last5m.mean` |
| MSPT 中位数 / P95 / 最大 | 0.358368 / 0.864341 / 23.712475 ms，同一 `last5m` 对象 |
| TPS | 19.99999477926803，`platformStatistics.tps.last5m` |
| 在线玩家 | 0；空载指零玩家，不指世界内零实体 |
| 已加载实体 | 全局 109；300 行每秒记录均为 109。报告结束时主世界 109，雨寂/永夜/下界/末地各 0 |
| 采样期间主机 load1 | 每秒记录范围 0.0205078125 至 0.3955078125；4 vCPU runner，不使用高负载共享 VPS |
| FPS | 不适用：没有客户端；没有 `/sparkc` 或 GPU 帧时数据 |

MSPT 是报告结束时的 **last5m 滚动分布**，与 300.037 秒 profile 接近对齐；不是把控制台最后 10 秒或 1 分钟快照冒充整窗，也没有从两点快照计算分位数。控制器记录 301.111 秒是“开始确认到收到上传链接”的墙钟时间，包含结束/上传等待，不是 profiler 长度。

### 必须保留的计数纠错

首轮采集脚本对三个维度执行 `execute in ... if entity @e`。缺少位置/范围限制的 `@e` 跨维度选择，因此原 CSV 的 `overworld/rain/night` 三列均为同一个全局数 109。**不能称三个维度各有 109，也不能相加成 327。** 原始 CSV 保留，不改写历史文件。

本报告用 spark 的 `world.totalEntities=109` 和各世界 `totalEntities` 交叉验证。CSV 能证明每秒全局已加载数不变；只有报告结束时的逐维度分布来自 spark。后续脚本已加 `@e[distance=0..]`，覆盖五个实际维度，并补两个保护测试。子召唤物按实际 `YujiSummoner` NBT 计数，不用总数减父数推断。

首轮计数命令本身也有开销；后续四窗口改为五维度计数并固定服务端/客户端核心分组，所以不能把两轮数值直接相减称为优化收益。

## 启动、存活与退出

原始日志及 `startup.json` / `A-summary.json`：

```text
20:44:08 UTC MIXIN_TARGETS checked=14 failures=0
20:44:43 UTC BUILD SUCCESSFUL in 34s
20:45:44 UTC Done (32.465s)! For help, type "help"
startup: pid=2994, alive=true, listening=127.0.0.1:25592
20:47:44 UTC Profiler is now running! (async)
20:52:45 UTC https://spark.lucko.me/bf8uw90DTk
sample end: processAlive=true, entityRows=300
20:52:50 UTC shutdown: pid=2994, exitCode=0, graceful=true, aliveAfterStop=false
```

启动确认同时检查真实 Java 子进程未退出、回环端口可连接、Minecraft `Done` 日志。采样结束再次检查该进程仍存活，然后通过控制台 `save-all flush`、`stop` 正常保存退出，没有为交付保留后台专服。

本次源提交 Mixin 14/0 和构建成功是当次执行结果；默认完整 GameTest 仍引用同一源码 `bb325dc` 的 [Gravity run 38958533336](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38958533336)：434/434 required tests passed。既有八项 CI 全部 success 只绑定该 SHA，新增文档 HEAD 的检查须另行记录，不自动继承。

## 环境与可复现恢复命令

- OS：Ubuntu 24.04.5，内核 `6.17.0-1022-azure`；CPU Intel Xeon 6973P-C，4 vCPU / 2 physical cores。
- Java：Temurin 17.0.20.1+1；JVM `-Xms512M -Xmx2G -XX:ActiveProcessorCount=2`。首轮未固定 CPU affinity。
- Minecraft 1.20.1，Forge 47.2.0；模组 0.14.2，Curios 5.14.1+1.20.1，GeckoLib 4.4.9，spark 1.10.53-forge。
- 世界 seed 20261016，normal 难度，view-distance 8，simulation-distance 6，0 玩家，预热 120 秒；不在窗口内运行构建或 GameTest。
- 专服仅 `127.0.0.1:25592`，离线认证仅用于该隔离回环实例，RCON/query 关闭。

在有正常依赖访问的隔离环境，准确检出源码后，实际成功的构建与生产服务入口如下。Forge installer 与依赖下载/拷贝、EULA、回环 server.properties、stdin 转发由[固定脚本](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/f2401047ceeae7ac28bf7397eef12cfab3317b30/tools/perf/production_probe.py)完成；它的旧 CSV 维度标签和 `health show` 兼容性问题按上文处理，不盲目复用错误口径。

```bash
python3 tools/verify_mixin_targets.py
bash ./gradlew --no-daemon --max-workers=2 build
java -Xmx1G -jar forge-installer.jar --installServer
java -Xms512M -Xmx2G -XX:ActiveProcessorCount=2 @libraries/net/minecraftforge/forge/1.20.1-47.2.0/unix_args.txt nogui
```

最后两条在安装目录执行；`mods/` 放入该准确源码构建的 jar 与上述三个依赖 jar。Forge 官方 installer 通过发布的 SHA-1 校验，spark 通过发布的 SHA-512 校验；完整 URL/摘要在制品 `downloads.json`。关键 SHA-256：

```text
sound_isolating_eraser-0.14.2.jar
7c6bb9d54c2b0b3481fed6b62541bd5933f58f93357ecd25764e51efda48f4d9
spark-1.10.53-forge.jar
eba275ba9bb0cb5b542913c3138c6eeff6a752898266d63d125d185f26ac99ed
```

spark 官方下载页的旧 Forge 1.20.1 发布与 Modrinth 版本 `4FXHDE9F` 均对应 1.10.53；未将 profiler 加入正式依赖声明。[官方入口](https://spark.lucko.me/download)，[发布元数据](https://api.modrinth.com/v2/version/4FXHDE9F)。元数据读取依据[官方 raw-data 说明](https://spark.lucko.me/docs/misc/Raw-spark-data)。

首轮 `spark health show` 被此旧版拒绝，但 `spark tps` 和 profile metadata 成功，不把该错误藏作全步骤成功。后续使用兼容命令，并以 raw metadata 为数字来源。

## 本地限制与后续门禁

20:32 UTC 主力机 Java17、Gradle8.1.1 与 foojay 缓存存在，4 CPU、可用内存3907 MiB，但负载8.47/11.14/11.10仍不作基线。原 T2 目录保留 `RedNailsClient.java` 未提交删除；新增 worktree 暴露原 Git 对象库缺失 blob `8016a3a04373fbeec52cb6b91f64aa788584cdd7`，退出128。未重置/修写他人工作树。后续 VPS 调用反复503，备用 SSH 拒绝认证，未读取主机凭证文件或扩大权限。

四窗口脚本位于独立验证分支；`2432c24` 运行因发现维度计数错误由本任务主动取消，不能算通过。修正后重跑结果单独验收。本 PR 仍只更新 docs/perf，`mod_version=0.14.2` 不再无故 bump；不合 main、不 force push、不开始 cap/cache/render 改动。

在四个有效窗口、世界/配置副本及计数口径全部齐备前，PR2 门禁保持关闭。源码候选热点和八个上游项目对照沿用现有审计，不能用空载低 MSPT 推断雨寂、城市或召唤物没有问题。
