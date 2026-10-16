# 2026-10-16：既有缓存与离线专服启动复核

关联 [PR #43](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/43)。本轮复用原分支、主机工作区和已存在的任务隔离沙箱，没有新建或删除沙箱，没有改运行时、依赖声明、网络权限或版本号。

**结果：未采样、未宣称全绿性能基线。** 四个正式 300 秒窗口均未开始，无 spark viewer URL、MSPT、实体数或 FPS 数据。Gradle 恢复不是专服恢复，Mixin 通过不是性能验收。

## 并行成果与提交归属

- 19:43:40 UTC，在既有 `/data2/test-agent/eraser-t2-20261016` 执行 `git fetch origin docs/zer-1542-t2-pr1-perf-baseline`，FETCH_HEAD 为 `3a213afeb5ef71412560b5a90259f2b620cd1326`。
- 该工作区仍在 `e170331...`，已有 `D src/main/java/com/zeropointsix/eraser/client/RedNailsClient.java` 未提交改动。本轮没有切换、恢复、提交或删除它。
- 19:48:53 UTC，并行续办提交 [da45d57](https://github.com/ZeroPointSix/sound-isolating-eraser/commit/da45d57c34a788bf898563949ecbee523ff75b35) 已恢复官方 Gradle 分发并补齐在线构建/网络分层证据。本轮保留并复用其全部文档，不重复认领这项恢复。
- 19:52:02 UTC，隔离沙箱再 `git fetch`，本地 HEAD 和 FETCH_HEAD 均为 `da45d57c34a788bf898563949ecbee523ff75b35`。下面的离线启动与 Mixin 结果绑定此 SHA。

## 既有主机通道与缓存

目标为已授权主力机 `kuoya-sgp-002`。以下是本轮实时观察，不把历史 503 或旧负载当成当前测量：

| UTC | 探针 | 结果 |
| --- | --- | --- |
| 19:40:36 | `uptime`，执行作业 `job-yu8QGnwKlFECUEAI` | 退出 0，负载 16.45 / 11.52 / 8.31 |
| 19:42:17 | CPU、内存、任务路径，执行作业 `job-k4k11Dfatk3jlTtu` | 4 CPU；可用内存 2534 MiB、无 swap；8 个 Java 进程，多项 CPU 超过 100%；未停止任何其他任务 |
| 19:42:17 | `df -h /data /data2` | `/data` 可用 61 GiB，`/data2` 可用 41 GiB；有磁盘空间不等于有空闲计算窗口 |
| 19:43:40 | 既有 Gradle home 目录 | `/root/.gradle/caches/{8.1.1,8.8,forge_gradle,modules-2,...}` 与 wrapper distributions 存在；没有把目录存在当成完整离线依赖证明 |
| 后续两次只读调用 | 继续读取缓存/源码 | 连接器返回 `MCP error 503: Upstream MCP is temporarily unavailable`，没有远端输出，不能宣称这两次命令已执行 |

Java 实测为 OpenJDK `17.0.20.1`。未在这台高负载共享机上启动采样或新增构建负载。既有官方依赖 URL 在主力机与沙箱的不同结果见 [并行续办诊断](2026-10-16-network-diagnosis.md#现有环境与缓存替代路径)，不能把沙箱失败扩大为上游全局故障。

## 复用隔离环境

已有任务沙箱 `t2-connectivity-20261016c`，2 CPU / 4 GiB / 8 GiB，`public=false`。本轮只复用任务工具链和源码，不删除或改动其他任务资源。19:50:35 UTC 读取结果：

```text
repo=/home/daytona/t2-connectivity/repo
HEAD=da45d57c34a788bf898563949ecbee523ff75b35
git status --short: empty
JAVA_HOME=/home/daytona/t2-connectivity/jdk-17.0.20.1+1
openjdk version "17.0.20.1" 2026-08-18
OpenJDK Runtime Environment Temurin-17.0.20.1+1
/home/daytona/.gradle total: 1.7M
caches: 8.1.1, jars-9, journal-1, modules-2, transforms-3
```

官方 Gradle/JDK 资产、SHA-256、在线 `build runGameTestServer` 退出 1 的完整证据已在 [网络诊断](2026-10-16-network-diagnosis.md#已实际恢复的官方工具链)。本轮不重新下载同一资产，不修改 wrapper，也不通过镜像、代理或权限修改绕过已观察到的出站限制。

## 新增验证：缓存能否离线启动 runServer

19:52:02 UTC 开始，先检查 Mixin，再用已有 Gradle/JDK 尝试真正的 `runServer` 任务。`--offline` 与前次在线构建不同，用来验证已有缓存是否足够；不是把离线模式当作性能优化。

```bash
export JAVA_HOME=/home/daytona/t2-connectivity/jdk-17.0.20.1+1
export PATH="$JAVA_HOME/bin:$PATH"
cd /home/daytona/t2-connectivity/repo
git fetch origin docs/zer-1542-t2-pr1-perf-baseline
git rev-parse HEAD FETCH_HEAD
python3 tools/verify_mixin_targets.py
/home/daytona/t2-connectivity/gradle-8.1.1/bin/gradle --version
timeout 60 /home/daytona/t2-connectivity/gradle-8.1.1/bin/gradle \
  --offline --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs=-Xmx768m --console=plain runServer
```

终端原始输出摘录：

```text
da45d57c34a788bf898563949ecbee523ff75b35
da45d57c34a788bf898563949ecbee523ff75b35
MIXIN_TARGETS checked=14 failures=0
mixin_exit=0
Gradle 8.1.1
Revision: 1cf537a851c635c364a4214885f8b9798051175b
JVM: 17.0.20.1 (Eclipse Adoptium 17.0.20.1+1)
OS: Linux 6.8.0-146-generic amd64

Settings file '/home/daytona/t2-connectivity/repo/settings.gradle' line: 12
Plugin [id: 'org.gradle.toolchains.foojay-resolver-convention', version: '0.5.0']
was not found in any of the following sources:
- Gradle Core Plugins
- Plugin Repositories (could not resolve plugin artifact
  'org.gradle.toolchains.foojay-resolver-convention:org.gradle.toolchains.foojay-resolver-convention.gradle.plugin:0.5.0')
  Gradle Central Plugin Repository
  MinecraftForge(https://maven.minecraftforge.net/)
BUILD FAILED in 4s
```

执行通道最终返回 `exit_code=1`、`state=idle`。失败发生于配置/插件解析阶段，不是超时 124、不是编译或测试用例失败；未启动 Minecraft 专服。外层执行在失败后停止，后置时间戳和 status 命令未输出，不伪造结束时刻或清洁状态。19:50 的干净工作区检查不能冒充失败后的检查。

结论：现有隔离缓存不足以离线解析 foojay 插件及其 marker，不能通过 `--offline` 恢复专服。这里没有断言每个相关 JAR 都不存在，没有声称 Maven 上该插件不存在，也没有移除插件来放宽门禁。

## Endpoint 与证据关联

本轮直接复用同一沙箱 19:37-19:44 的在线 curl/Java/Gradle 证据，不在无环境变化时重复同一失败请求。以下是已归档端点，不是本次离线命令发出的网络请求：

- Gradle：`https://services.gradle.org/distributions/gradle-8.1.1-bin.zip`；沙箱 curl 35 / HTTP 000，官方 GitHub 同版本资产已恢复。
- Forge：`https://maven.minecraftforge.net/net/minecraftforge/gradle/ForgeGradle/maven-metadata.xml`；沙箱 curl 35 / HTTP 000，TLS 握手重置。
- Cloudsmith：`https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/software/bernie/geckolib/geckolib-forge-1.20.1/4.4.9/geckolib-forge-1.20.1-4.4.9.pom`；沙箱 curl 35 / HTTP 000。
- 插件：`https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver-convention/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/0.5.0/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin-0.5.0.pom`；沙箱 curl 35 / HTTP 000。
- `https://spark.lucko.me/` 首页探针失败；无 profile 可上传，不能称为已尝试上传失败。

精确时间、命令、HTTP/TLS 字段、GitHub/Maven Central 对照和网络策略假设见 [在线分层证据](2026-10-16-network-diagnosis.md)。组织 tier 与具体阻断规则仍未核实，本轮没有扩权或更改网络策略。

## 仍未完成与下一步

四场景采样均未完成，PR2-4 运行时代码未开始。新的 [PR2 文件/API/cap/config 清单](../pr2-spawn-cap-plan.md) 已把实现和回归条件写清，但它不是开工批准。

下一动作是恢复已授权隔离环境的完整插件/Forge/GeckoLib/spark 访问，或取得已有授权主机的低负载独占窗口。不能使用旧 JAR、其他 SHA、共享高负载测量或删除/暂停他人任务来填基线。当前 HEAD CI 以 PR 上绑定 SHA 的 Actions 原始日志为准；不把 `3a213af` 的历史 8/8、434/434 自动继承给新的文档提交。
