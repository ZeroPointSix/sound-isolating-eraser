# PR1 客户端 FPS：优化前软件渲染诊断基线

## 结论与范围

2026-10-17 03:20 UTC 接管复核：前一执行已完成四个约 300 秒的真实客户端帧时间窗口，本次下载原始制品、重算统计并补入同一 PR43，**没有重新采样，也没有运行时优化**。此前“FPS 未测”的历史记录描述的是旧窗口，不再代表本次交付。

此数据回应 [PR1 审查缺口](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/43#issuecomment-6227868213)，但不冒充独立批准。它是 GitHub runner 上 llvmpipe 的真实游戏渲染循环基线，不是硬件 GPU 性能、显示器呈现延迟或优化收益。是否接受此环境作为 PR1 的阶段性 FPS 基线，仍由独立审查者判断。

## 数据来源

- [成功采集 run 38992181049](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38992181049)，[原始 job 日志](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38992181049/job/117277172853)。采样在本次 03:12 UTC 接管前完成。
- [原始制品 12086881569](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38992181049/artifacts/12086881569)：33,355,071 bytes，SHA-256 `fea86cd8c6c0e314ea0cfc263bc4544d5511664a14ec0e741a141ebd6e21e4b0`；GitHub 标示到期时间 2026-11-16 02:20:39 UTC。
- 运行时代码固定为 [`bb325dc9ccf3d99565d8a1eedb7efee372fdcd45`](https://github.com/ZeroPointSix/sound-isolating-eraser/commit/bb325dc9ccf3d99565d8a1eedb7efee372fdcd45)，`mod_version=0.14.2`。PR43 后续提交仅文档；不是 PR53 的 cap 版本。
- [独立采集器 `37fdce8d31f099c60d2c9a2c547db1439f1c3c92`](https://github.com/ZeroPointSix/sound-isolating-eraser/tree/37fdce8d31f099c60d2c9a2c547db1439f1c3c92/tools/perf)。只读探针独立 source set，不打进生产 mod jar，不在 PR43 添加 Mixin/采样逻辑或改 CI。
- 服务端 jar SHA-256 `add2ec1037ec9706930d590b8daa9d5c4077261aa4b49dc844a6dc538d45a451`。客户端是相同源码的 Forge 开发态 classpath，加独立只读探针，不冒充该 reobf jar 的客户端发行态测试。
- [永久保留的原始 CSV、全精度摘要、环境及生命周期](client-fps-38992181049/) 来自上述 ZIP，字节不改写。制品另含 8 张起止截图、完整控制台/客户端日志和世界快照；临时制品到期不影响仓内 CSV 重算。

## FPS 结果

每场景 1 个生存客户端 PerfUser，预热至少 120 秒后录制。均值与 1% low 均来自单调时钟逐帧间隔，不取配置值或 F3 瞬时值。以下均为优化前；无前后改善百分比。

| 场景 | 单调时钟秒数 | 帧端点 / 间隔数 | 平均 FPS | 1% low FPS | P95 / P99 帧时 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| A1：主世界驻足 | 300.005781 | 3315 / 3314 | 11.046 | 7.930 | 96.672 / 114.542 |
| B1：雨寂 | 300.039039 | 2919 / 2918 | 9.725 | 7.243 | 108.752 / 129.556 |
| B2：永夜城市 | 300.092965 | 2541 / 2540 | 8.464 | 5.708 | 124.061 / 161.367 |
| C：真实召唤物 | 300.077009 | 2259 / 2258 | 7.525 | 5.016 | 172.127 / 184.343 |

| 场景 | UTC 元数据开始至完成（2026-10-17） | 全局已加载实体 | 场景维度实体 | 召唤父 / 归属子 |
| --- | --- | ---: | ---: | --- |
| A1 | 01:51:31.365649034 至 01:56:31.380976495 | 216 至 257 | 主世界 216 至 257 | 0 / 0 |
| B1 | 01:59:32.209582468 至 02:04:32.262282863 | 273 至 277 | 雨寂 38 至 42 | 0 / 0 |
| B2 | 02:07:32.561084340 至 02:12:32.665769098 | 253 至 256 | 永夜 9 至 12 | 0 / 0 |
| C | 02:15:32.039997500 至 02:20:32.130051568 | 170 至 208 | 主世界 170 至 208 | 16 / 60 至 69 |

每场景实体 CSV 为 301 行，约每秒一次，末行在帧窗口完成检测边界，不能称为逐帧同步实体数。计数是各维度 `@e[distance=0..]` 的已加载实体（含玩家和非怪物），不是屏幕可见/实际绘制实体数。父实体使用 `type=qihai:qiyu,tag=t2_parent`，子实体按真实 `YujiSummoner` NBT 计数，不用乘法推算。各维度查询串行，跨维度总数是近似同时快照。

A1 明确是 **1 玩家驻足**，不是既有服务端 A 的 0 玩家空载；玩家加载区块后实体数不同。各 FPS 窗口和原 spark 窗口也不是同时采样，不能配对成同一时刻的 FPS/MSPT。

## 环境与站位

Minecraft 1.20.1、Forge 47.2.0、Temurin 17.0.20.1+1、spark 1.10.53-forge。Linux 6.17.0-1022-azure x86_64；CPU 型号字符串 AMD EPYC 9V45 96-Core Processor，runner 实际可见 **4 逻辑核 / 2 物理核**，不是可用 96 核。服务端绑 CPU 0,1，客户端绑 2,3；内存/宿主机噪声未做独立实验。窗口内 load1 全部约 1.27 至 2.18。

实际 OpenGL：`llvmpipe (LLVM 20.1.2, 256 bits)` / Mesa / `4.5 (Core Profile) Mesa 25.2.8-0ubuntu0.24.04.4`，Xvfb 软件渲染。1280x720、Fancy、粒子 ALL、视距 8、模拟距离 6、FOV 70、第一人称、云关闭；VSync false，maxFps 260。260 只是上限，旧服务端采样客户端的 20 上限同样不是 FPS。四场景 options 和运行时元数据相符。

| 场景 | 首帧位置 x,y,z；yaw,pitch | 末帧位置 | 最大相对首帧位移 |
| --- | --- | --- | ---: |
| A1 | -9.5,64,-22.5；0,0 | 同首帧 | 0 |
| B1 | 82.5,129,82.5；-180,8 | 同首帧 | 0 |
| B2 | 3.4144285484,79.5,12601.5；89.99999237,0 | 同首帧 | 0 |
| C | -15.0770393274,66,-6.6080709613；-180,8 | -16.3000000119,62,-11.3000000119 | 10.0161677961 |

所有帧保留位置与朝向，yaw/pitch 范围均为 0。C 碰撞推位导致场景内容改变，末图局部被实体遮挡，**不能标作固定站位前后比较**。A1/B1/B2 的坐标不漂移也不等于已完成重复方差或优化后配对实验。

场景来自原制品 12077285827 的 before 世界，种子 20261016，原场景构建/装备/防护及 B2 正常入城和配置恢复见[服务端报告](2026-10-16-four-window-baseline.md)。本次逐字节核验四份复用快照与原 ZIP 一致：

| FPS 标签 | 原始 ZIP 成员 | SHA-256 |
| --- | --- | --- |
| A1 | fixtures/A-before.tar.gz | `5fbe667e1ce72d9d18651a925233e6fb1a7e7405e3551d1e1c1a619f29e84633` |
| B1 | fixtures/B1-before.tar.gz | `b11993c7a340b2c1e5677549fe0fdba61b4a8e512122a11556d90f62a8aabe62` |
| B2 | fixtures/B2-before.tar.gz | `14614856dd4c60c63290114261f448d0834ca29e9a324f696f7a74315ece03d6` |
| C | fixtures/C-before.tar.gz | `e69395a2ab8be8ac35ae23a96e53ca02afa5587fc4ae45d8a6425b60b552fe2f` |

## 方法、重算与观测开销

[FpsProbe.java](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/37fdce8d31f099c60d2c9a2c547db1439f1c3c92/tools/perf/clientprobe/java/com/zeropointsix/t2probe/FpsProbe.java) 在 Forge `RenderTickEvent.END` 记录 `System.nanoTime()` 和玩家姿态。要求对应维度、存活玩家、世界已完成绘制、无菜单/加载叠层且未暂停；开始后遇无效帧则整窗失败，不筛掉慢帧。预分配缓冲区，结束后写 CSV；每场景 `validWorldFrames == frames`。

- 平均 FPS = `(端点数 - 1) / ((末时间戳 - 首时间戳) / 1e9)`，不是瞬时 FPS 的算术平均。
- 1% low = `1000 / 最慢 ceil(间隔数 * 0.01) 个帧间隔毫秒值的均值`；不是 `1000 / P99`，也不是最小帧率。
- P95/P99 使用排序后 `(N-1)*q` 的相邻值线性插值。
- 正式时长由单调时钟定义。UTC start/end 包含开始元数据和结束落盘附近的时间差，不能用 UTC 相减替换原始帧时长。
- 这是渲染循环节奏，包括调度、逻辑、软件渲染和等待；不是 GPU-only、显示器呈现时间，也没有客户端 sparkc/allocation 火焰图。
- 探针自计 callback 累计约 A1 7.579ms / B1 6.418ms / B2 5.502ms / C 4.757ms，包含首次元数据准备但不覆盖所有事件分发/落盘/CPU竞争开销；没有无探针对照，不能声称零开销。
- 实体计数控制台命令会产生客户端聊天叠层，8 张截图可见；C 的计数信息更密集。该 UI/日志开销包含在测量中，未扣除，不能据此单独归因到某个实体渲染器。后续严格比较需统一观测输出策略。

复算不需要启动游戏。取得固定 harness 中 `fps_stats.py` 后，在其目录执行：

```bash
python3 test_fps_stats.py
python3 -c 'from pathlib import Path; from fps_stats import summarize; root=Path("/path/to/docs/perf/evidence/client-fps-38992181049"); [print(p.name, summarize(p)) for p in sorted(root.glob("*-frames.csv"))]'
```

[统计实现](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/37fdce8d31f099c60d2c9a2c547db1439f1c3c92/tools/perf/fps_stats.py)及[6项统计测试](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/37fdce8d31f099c60d2c9a2c547db1439f1c3c92/tools/perf/test_fps_stats.py)固定在不可变 SHA。各 CSV 摘要可直接用 `sha256sum *-frames.csv` 与全精度 JSON 的 `csvSha256` 核对。

若需要重新采集，复用[原采集流程](https://github.com/ZeroPointSix/sound-isolating-eraser/blob/37fdce8d31f099c60d2c9a2c547db1439f1c3c92/tools/perf/fps_suite.py)：源码 checkout bb325dc，harness checkout 37fdce8，按其现有 Actions 安装依赖及生产专服，校验原 ZIP SHA，将 `T2_SNAPSHOT_ZIP` 指向原制品，`T2_FPS_HARNESS` 指向独立 harness。先执行原构建和 `-I ../harness/tools/perf/fps.init.gradle writeT2FpsClasspath`，再执行 `python3 harness/tools/perf/fps_suite.py`。不要在生产服执行，也不要修改 PR43 的运行时来启用探针。

## 复核结果与审查关口

本次离线重算：11,034 个帧端点 / 11,030 个正间隔、4 个完整窗口、1,204 行实体记录、4 份复用快照全部匹配；统计测试 6/6；原 Mixin 校验 `MIXIN_TARGETS checked=14 failures=0`，exit 0。四次专服启动后均正常 stop、exit 0、aliveAfterStop false，最后关闭 02:20:39 UTC；没有遗留采样任务。

既有 MSPT 保持原报告：A [0.887ms](https://spark.lucko.me/QMZXUwQZ26)、B1 [5.000ms](https://spark.lucko.me/BQ1iJ9qI3b)、B2 [3.785ms](https://spark.lucko.me/Scz8kd12Ic)、C [7.230ms](https://spark.lucko.me/aCKjSDJu2S)。本次没有新增服务端 profiler 窗口或拿 FPS 窗口的 health 摘要替换它们。

PR43 文档父提交 df9cb7a 的 8/8 CI、434/434 是历史已核验证据；**本补充提交必须以新 HEAD 的检查/原始日志为准**，结果写入[原 PR](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/43)，不继承旧绿色。版本保持 0.14.2，未改源码、Mixin、CI，不合 main、不 force push。本轮恢复原分支到独立 worktree，旧工作树的预存删除保留；远程稀疏检出通道出现 503 后使用固定 SHA 文档快照和原分支快进更新，不以失败工具作为 FPS 缺失理由。

下一 Review 点：核验真实逐帧数据及上述软件渲染/计数叠层/C漂移限制，明确这是否满足 PR1 阶段性 FPS 要求。硬件 GPU、多人/晚期波次、重复方差、allocation 和严格固定站位的优化前后比较仍未完成。作者不自行批准；PR2/3/4 本轮不推进。
