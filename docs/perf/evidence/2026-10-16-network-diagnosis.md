# 2026-10-16：依赖连接分层诊断与 Gradle 恢复

关联 [PR #43](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/43) / [ZER-1542](https://linear.app/zerodotsix/issue/ZER-1542)。所有时间为 UTC。被测源码是 `3a213afeb5ef71412560b5a90259f2b620cd1326`，`mod_version=0.14.2`。本次只补证据，不改运行时、Mixin、依赖声明或 CI。

> 历史记录：下文描述 19:37 至 19:44 UTC 的 Daytona 连接与启动失败，不代表当前仍无专服或采样。随后已在隔离 GitHub runner 正常构建生产模组、启动专服并完成 A/B1/B2/C 各 300 秒采样，见[四窗口实测报告](2026-10-16-four-window-baseline.md)。原 Daytona TLS 问题没有被声称已经修复；成功的是不改项目依赖的替代执行路径。

## 结论与证据边界

- 官方 GitHub 分发的 Gradle 8.1.1 和 Temurin 17 已下载、校验并运行。原来的 Gradle 分发下载问题已有可复现的官方替代入口，未修改仓库 wrapper URL。
- 真实 `build runGameTestServer` 已推进到 `settings.gradle:12` 的插件解析，随后退出 1；编译、GameTest、专服和 spark 仍未开始。不能将 Gradle 能运行写成项目构建成功。
- 隔离环境能解析依赖域名并建立 TCP，但多个站点在 TLS 握手阶段重置。IPv4、HTTP/1.1、TLS 1.2 兼容性尝试没有修复；GitHub 与 Maven Central 对照成功。
- 已有主力开发机的执行通道和三个原依赖入口本轮恢复可用，但实时负载不符合可靠基线条件。另一台既有测试机也处于高负载；没有在任一共享机采集性能数据。
- A 空载、B1 雨寂、B2 永夜城市、C 大量召唤物四个正式 300 秒窗口均未采集。无 spark 链接、MSPT、实体计数或客户端 FPS，也没有优化前后对比。

## 隔离环境与连接测试

任务专用 Daytona `daytona-medium`：2 CPU / 4 GiB / 8 GiB，默认网络设置，未增加 allow list、代理或公开端口。CLI 0.190.0 / API 0.225.1。curl 8.14.1、OpenSSL 3.5.6。没有因版本提示升级 CLI。

19:37:44，以下探针在隔离环境执行：

```bash
curl -fLsS --range 0-1023 --connect-timeout 4 --max-time 10 \
  -o /dev/null \
  -w 'HTTP=%{http_code} TLS=%{ssl_verify_result} IP=%{remote_ip} CONNECT=%{time_connect} HANDSHAKE=%{time_appconnect}' \
  '<URL>'
```

| URL | curl 退出码 / HTTP | 连接与握手结果 |
| --- | --- | --- |
| `https://services.gradle.org/distributions/gradle-8.1.1-bin.zip` | 35 / 000 | DNS 有结果；CONNECT 0.004062s；HANDSHAKE 0；connection reset |
| `https://downloads.gradle.org/distributions/gradle-8.1.1-bin.zip` | 35 / 000 | DNS 有结果；CONNECT 0.002637s；HANDSHAKE 0；connection reset |
| `https://maven.minecraftforge.net/net/minecraftforge/gradle/ForgeGradle/maven-metadata.xml` | 35 / 000 | DNS 有结果；CONNECT 0.006825s；HANDSHAKE 0；connection reset |
| `https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/software/bernie/geckolib/geckolib-forge-1.20.1/4.4.9/geckolib-forge-1.20.1-4.4.9.pom` | 35 / 000 | DNS 有结果；CONNECT 0.024140s；HANDSHAKE 0；connection reset |
| `https://repo.maven.apache.org/maven2/` | 0 / 206 | TLS 校验 0；HANDSHAKE 0.016828s |
| `https://api.github.com/repos/gradle/gradle-distributions/releases/tags/v8.1.1` | 0 / 206 | TLS 校验 0；HANDSHAKE 0.015102s |

失败探针的 `ssl_verify_result=1` 不能单独解释为缺 CA 或证书错误：握手根本没有完成，实际错误是 `curl: (35) Recv failure: Connection reset by peer`。没有使用 `-k` 或关闭 Java 证书检查。

19:39:15，对同一 Forge URL 分别添加 `-4 --http1.1` 和 `-4 --http1.1 --tlsv1.2 --tls-max 1.2`，均为退出 35 / HTTP 000 / HANDSHAKE 0。独立 Python 标准库 `socket.create_connection((host,443))` 成功后，以默认验证开启的 `ssl.create_default_context().wrap_socket(..., server_hostname=host)` 复验：

```text
TCP_OK maven.minecraftforge.net ('104.21.58.163', 443)
TLS_ERROR maven.minecraftforge.net ConnectionResetError [Errno 104] Connection reset by peer
TCP_OK dl.cloudsmith.io ('13.226.238.20', 443)
TLS_ERROR dl.cloudsmith.io ConnectionResetError [Errno 104] Connection reset by peer
TCP_OK api.github.com ('140.82.114.6', 443)
TLS_OK api.github.com TLSv1.3
```

19:44:16，新出现的插件依赖与 spark 站点也分别为 curl 35 / HTTP 000 / HANDSHAKE 0：

```text
https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver-convention/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/0.5.0/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin-0.5.0.pom
https://spark.lucko.me/
```

这说明只恢复 Gradle ZIP 仍不足以启动项目；spark 站点可达性也需要单独预检。站点首页失败不是已尝试上传 profile 的证据，本轮没有 profile 可上传。

## 已实际恢复的官方工具链

19:40:21 起，从官方 GitHub Release 下载以下资产。Gradle 摘要与主力开发机通过正常 TLS 取得的[官方 SHA-256 文件](https://services.gradle.org/distributions/gradle-8.1.1-bin.zip.sha256)一致；JDK 摘要与前次已核验的官方摘要一致。

| 资产 | 字节数 | SHA-256 |
| --- | --- | --- |
| [Gradle 8.1.1 bin](https://github.com/gradle/gradle-distributions/releases/download/v8.1.1/gradle-8.1.1-bin.zip) | 124643981 | `e111cb9948407e26351227dabce49822fb88c37ee72f1d1582a69c68af2e702f` |
| [Temurin 17.0.20.1+1](https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz) | 193252603 | `3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e` |

没有使用第三方镜像或把下载包转存为绕过限制的代理。Gradle [官方发行仓库](https://github.com/gradle/gradle-distributions)提供的是同版本资产；使用其二进制不更改项目版本和依赖坐标。

复现时将两个归档放在任务独立目录，先计算摘要并与上表严格比对，再解压。以下是本轮实际执行命令，目录属于一次性沙箱，不是待提供的外部入口：

```bash
env JAVA_HOME=/home/daytona/t2-connectivity/jdk-17.0.20.1+1 \
  /home/daytona/t2-connectivity/gradle-8.1.1/bin/gradle --version

timeout --kill-after=10 180 \
  env JAVA_HOME=/home/daytona/t2-connectivity/jdk-17.0.20.1+1 \
  /home/daytona/t2-connectivity/gradle-8.1.1/bin/gradle \
  --project-dir /home/daytona/t2-connectivity/repo \
  --no-daemon --console=plain --max-workers=1 \
  -Dorg.gradle.jvmargs=-Xmx2G \
  -Dorg.gradle.internal.http.connectionTimeout=10000 \
  -Dorg.gradle.internal.http.socketTimeout=10000 \
  build runGameTestServer
```

19:42:15 至 19:42:27 原始输出摘要：

```text
Gradle 8.1.1
Revision: 1cf537a851c635c364a4214885f8b9798051175b
JVM: 17.0.20.1 (Eclipse Adoptium 17.0.20.1+1)
--version exit_code=0

Settings file '/home/daytona/t2-connectivity/repo/settings.gradle' line: 12
Plugin [id: 'org.gradle.toolchains.foojay-resolver-convention', version: '0.5.0']
was not found in any of the following sources:
  Gradle Central Plugin Repository
  MinecraftForge(https://maven.minecraftforge.net/)
BUILD FAILED in 11s
build runGameTestServer exit_code=1
```

该构建错误与插件站点 TLS 失败相符，但错误本身不是插件发布不存在的证明。没有移除 foojay 插件、放宽失败门禁或把命令失败当作 GameTest 用例失败。

## 网络策略假设与授权边界

[Daytona 官方网络限制说明](https://www.daytona.io/docs/en/network-limits/)与[排障文档](https://www.daytona.io/docs/en/troubleshooting/)说明：Tier 1/2 的组织级出站限制不能由沙箱 allow list 覆盖，连接重置是列出的症状；GitHub 和 Maven Central 在基本可达服务表内，而本任务失败的 Gradle/Forge/Cloudsmith 域名不在该表内。

**推断，不是已核实根因：** 当前已知配额与低等级组织相符，选择性 TLS 重置也与上述策略相符，因此组织级出站策略是优先核查项。本轮尝试只读 `daytona organization list --format json`，CLI 返回：

```text
organization commands are not available when using API key authentication
run `daytona login` to reauthenticate with browser
exit_code=1
```

没有进行浏览器重新登录，也没有读取登录配置或令牌来绕过这一入口限制。因此本报告未确认账户当前 tier 或防火墙审计命中；不能直接断言是哪条规则阻断。`networkBlockAll=false` 也不等于组织没有限制。

不通过临时代理、改 SNI、关闭 TLS、换区域碰运气、修改组织网络或付费升级来绕过限制。需要账户管理员核实组织 tier/出站策略；涉及付费仍必须等用户批准，不把升级套餐当成默认修复。

## 现有环境与缓存替代路径

| 入口 | 本轮实测 | 判断 |
| --- | --- | --- |
| 已授权主力开发机 `kuoya-sgp-002` | 19:35:42 负载 10.81/9.52/6.94；19:37 确认 4 CPU；19:40 负载 13.94/10.68/7.93 | 执行通道恢复，但不作为基线；没有停止其他任务 |
| 同一主力开发机依赖探针 | 19:37:07，原 Gradle / Forge / Cloudsmith URL 分别 HTTP 206/200/206，curl 均退出 0、TLS 校验 0 | 不能把隔离环境失败扩大为上游服务全局故障 |
| 已授权测试机 `kuoya-sgp-001` | 19:38:40 负载 10.94/10.60/9.41 | 未采样；后续补充只读调用返回连接器 503，不把该次调用当新的主机状态证据 |
| 主力机已有 Gradle 缓存 | `/root/.gradle/caches` 存在 8.1.1、modules-2、forge_gradle 等目录 | 仅确认缓存存在，未验证完整性、未复制/修改共享缓存 |
| 主力机已有项目产物 | 已知工作区有 `sound_isolating_eraser-0.14.1.jar`，HEAD 为 `a3f7b4c...` 且存在其他任务文件 | 不匹配本 PR 的版本/提交，不拿来冒充 HEAD3a213af 的采样产物；工作区保持不动 |
| [HEAD3a213af 的 CI 制品](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38934356990/artifacts/12061187450) | 已有工作流声明上传模组 JAR、测试报告、日志和映射，不含完整 Forge 专服依赖包 | 不是已验收的离线专服；没有伪称已解包/校验全部依赖 |

## 验证与下一动作

19:41:20 在本次独立沙箱检出完整 HEAD `3a213afeb5ef71412560b5a90259f2b620cd1326`：

```text
git status --porcelain
(empty; exit_code=0)
python3 tools/verify_mixin_targets.py
MIXIN_TARGETS checked=14 failures=0
exit_code=0
```

本轮回读同一 SHA 的八项 CI 仍全部 success；此前原始日志已确认默认 `runGameTestServer` 的 434 个必需 GameTest 全部通过，证据见[固定提交验证记录](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/43#issuecomment-6221225271)。这些历史 CI 结果不是新文档提交自动通过的证明；新提交的 Mixin 与 Actions 结果另在 PR 按 SHA 记录。

下一步优先核实组织出站限制，或安排已有授权开发/测试机的低负载独占窗口，使用该 PR 的准确 SHA 和独立目录，不能使用其旧 JAR。采样前须复核完整依赖、spark 下载/报告上传入口与主机稳定性，再按 [runbook](../spark-runbook.md)执行 A/B1/B2/C，各 300 秒；客户端 FPS 单独测量。当前不缺生产密钥或已有存档，不扩大 PR1 到运行时优化或工作流改动。

任务临时沙箱用后清理；可复用的是本文的官方资产摘要、复现命令、固定 SHA 与 PR 证据，不依赖临时机器持续存在。
