# 风雷翅接力记录

本记录对应 2026-10-06 原线程的 PR 与工作区交接请求。继续现有成果，不重新搭建模组，不覆盖接手者自己的未提交修改。

## 权威入口

- 仓库：[ZeroPointSix/fenglei-wings](https://github.com/ZeroPointSix/fenglei-wings)，私有。
- 已有 [Draft PR #1](https://github.com/ZeroPointSix/fenglei-wings/pull/1)，分支 `feat/art-first-core`，目标 `main`，未合并。
- 已通过完整构建的代码提交：`538ccc18a079fc90917d0177d4c876a43d01ee42`。核心代码和资源在父提交 `bf34bef454ae196c6a29273b67a1c45a7e66a820`。
- [成功的 PR CI](https://github.com/ZeroPointSix/fenglei-wings/actions/runs/37478305294)；产物名称 `fanren-wings-build`，含重混淆 JAR。此记录之后的交接提交不改 Java 或运行时资源。
- 唯一需求源：[Notion 正文](https://app.notion.com/p/Minecraft-3f1463aed7e681e8a6e1cc9842ba7dc9)。文末饥饿值补充覆盖早先灵力方案，不新增灵力条。
- [原 Slack 线程](https://app.slack.com/client/T0B9J0W01F0/C0BDF94JZEE/thread/C0BDF94JZEE-1791289448.268879)。后续结果仍回此线程。
- 接力者当前 [Devin 会话](https://app.devin.ai/sessions/a01eb80bfde04b809c7219bc6bf20811)。他报告的工作区为 `/home/ubuntu/repos/fenglei-wings`，分支为 `devin/20261006-fenglei-wings-core`；这是对方报告的位置，不是本 agent 的执行环境。

## 本次实际工作流程与环境

| 阶段 | 实际位置 | 做过什么 / 边界 |
| --- | --- | --- |
| 需求、美术脚本、Java 和资源整理 | 原生容器 `/workspace` | 先读 Notion，制作原创方向图、像素素材和 Blender 脚本，再把导出的网格和素材接入 Forge。不是 Devin 沙箱。 |
| 原生代码目录 | `/workspace/fenglei-wings` | 本地目录保留文件及分支名，但没有本地 Git 提交历史；真实提交通过 GitHub Git Data API 写入远端。恢复请以远端 branch/commit 为准。 |
| 美术工作目录 | `/workspace/art-src`、`/workspace/art-build/fenglei_art_v01` | 脚本、像素资源、离线预览与本地首版模型包。此容器不保证跨会话存活。 |
| 真实 Blender 渲染 | Daytona `fenglei-art-20261006` | sandbox ID `1901fc26-5b52-4ce7-9d0e-a77ee377de31`，US，`daytona-medium`，Blender 4.3.2 CPU Cycles。交接时查询为 STARTED。不是 Minecraft 服务器。 |
| Daytona 脚本与输出 | `/tmp/fenglei_scene.py`、`/tmp/fenglei-art/model` | 现成模型、帧、渲染图；`/tmp/fenglei_postprocess.py` 编码验证；`/tmp/B_model_preview.zip` 是首版包。 |
| 编译和自动检查 | GitHub Actions `ubuntu-22.04` | Temurin Java 17、Gradle 8.8、Forge 47.4.0 / MC 1.20.1；原生容器没有完整本地 javac 工具链，不能声称本地完整构建。 |

未部署游戏测试服务器，未合并 main，未生产发布，未发生资金支出。

## A / B / C 成果边界

| 类别 | 可复用位置 | 验收事实 |
| --- | --- | --- |
| A 概念 / 方向 | [银白金纹方向稿](https://chatgpt-a1a4278.slack.com/files/U0BD8D3JULB/F0C729ZLCR4/fenglei-direction-v1.png) | AI 方向图，不是模型渲染或游戏截图。 |
| A 像素与 HUD 设计样张 | [图标可读性](art/previews/inventory_readability.png)、[四档 HUD](art/previews/inventory_hud_mock.png)、[粒子帧](art/previews/particle_sheet.png)、[粒子节奏 GIF](art/previews/particle_motion.gif) | 离线排版 / 素材预览；不能充当实机 GUI 遮挡验收。 |
| B 模型 / 动画首版 | [B_model_preview.zip](https://chatgpt-a1a4278.slack.com/files/U0B9MTQHQUR/F0C7YH5R5NU/b_model_preview.zip) | 可编辑 .blend、8 动作 GLB、7 PNG、8 MP4。视频能解码，但首版过曝，视觉验收未通过。 |
| C 已接入工程的资源 | [assets/fanren_wings](src/main/resources/assets/fanren_wings)、[资源清单](art/metadata/game_resources.json) | 网格、16/32 图标、HUD 图集、24 粒子帧、3 段原创声音等已经打入 Forge 构建；真实游戏内表现待验收。 |

B 首版 ZIP：6,615,321 bytes，SHA-256 `87eb6177a674acb5ed9838a427157786577b932fa38abc3f93a3ca99abfbc1c9`。

模型为 2,340 三角面、42 层叠羽片、52 骨骼；翼展约 6.4 格，收起宽度约 0.4 格。七个相机通过顶点在画幅内检查。GLB 是编辑交换件；游戏读取导出的 `wind_thunder_wings.mesh.json`，Java 渲染器使用程序化动作，不直接加载 GLB 或 Blender 骨骼动作。

### 不能误判的渲染状态

首版使用 8 samples，无降噪。八段 768x432 / 24 fps 视频共 215 帧，均完整解码且源帧不同；见 [首版解码记录](art/evidence/first-pass/playback_verification.json) 和 [首版模型清单](art/evidence/first-pass/asset_manifest.json)。这是文件完整性证据，不是视觉通过证据。

首版画面过曝，银白羽片辨识度不足，掉落图也未通过非空白阈值。`art/source/validate_assets.py` 对首版会失败，应保留此质量门，不得放宽阈值来制造通过结果。

后续脚本已改 AgX、exposure -0.7、较暗中性背景、48 samples。在移交前七张静态图已生成，动画尚未完成；约 14:27 UTC 停止了本 agent 的第二轮渲染进程，以交接现有任务。输出目录含新旧两轮文件，旧 ZIP、GLB 和 manifest 不代表 48 samples 的完整新版本。新静态图尚未完成视觉复核。先核对文件时间及画面、保留已合格文件，再补缺失帧；不得混装后声称整套第二版通过。

## 本次额外保全的源文件

| 文件 | 用途 / 依赖 |
| --- | --- |
| [blender_scene.py](art/source/blender_scene.py) | 原始几何、8 动作、7 相机、MC 网格导出。支持 `--out` 和按动作 `--render-clips`。 |
| [build_assets.py](art/source/build_assets.py) | Pillow 像素图标、HUD、粒子帧、离线样张生成。 |
| [integrate_assets.py](art/source/integrate_assets.py) | 拆分粒子帧、生成 Forge 资源引用和三段原创音效；需要 ffmpeg。 |
| [validate_assets.py](art/source/validate_assets.py) | 图片尺寸与曝光、图集、GLB 动作、镜头、视频记录和哈希的失败即停检查。 |
| [remote_postprocess.py](art/source/remote_postprocess.py) | ffmpeg 编码、ffprobe 帧数、完整解码和 ZIP。必须先处理新旧帧混合问题。 |
| [build_review.py](art/source/build_review.py) | ReportLab 中文评审 PDF 的草稿生成器，保留供接续；尚未完成最终逐页视觉检查，不是可交付成品。 |

这些是现有脚本的原样快照，不是重新生成的替代资产。除 Blender 外，原脚本固定使用 `/workspace/art-build/fenglei_art_v01`；整合脚本目标为 `/workspace/fenglei-wings`；编码脚本固定使用 Daytona `/tmp/fenglei-art/model`。换工作区时集中改这些根路径再运行，不要在其他目录盲跑。需要 Pillow / ReportLab；样张字体原位置 `/workspace/art-build/ZCOOLXiaoWei-Regular.ttf`，PDF 另需 DejaVuSans。字体不影响已经提交的运行时 PNG 或模组构建。

## 已验证与尚未验证

| Notion / 用户要求 | 当前状态 | 继续验证 |
| --- | --- | --- |
| 先做美术、精致四档 HUD、原版饥饿消耗 | 素材与 HUD 代码已接入；无独立灵力条 | Minecraft 多 GUI 缩放、冷热背景、快捷栏与饥饿条不重叠。 |
| 物品栏、穿戴、收展、掉落、相关粒子 | 16/32 PNG、胸甲槽、3D 网格、程序化动画、风带/电弧/冲击环已实现 | 实机展开/收起/第一人称/掉落，纹理正常、透明度、帧率。 |
| 四档飞行、平滑加速、雷遁 | 服务器所有权、碰撞扫掠、冷却和位置同步已实现 | 双人专服 100-200ms RTT；速度误差不超过 10%、悬停漂移小于 0.1 格/秒、无穿墙/回弹/失同步。 |
| 饥饿与退出状态 | 消耗、最低饥饿、冷却和小数消耗持久化已实现 | 低饥饿、重连、死亡/重生、换维度、乘坐、睡眠、卸下装备恢复重力。 |
| 可构建模组 | 资源检查、FlightMath 11 项、reobf Forge 构建通过 | 成功 JAR 不等于真实客户端/多人 E2E 通过。 |
| 可选速度 FOV +15 / 鼠标灵敏度 0.6 | 未实现 | 后续按 Notion 可选项范围决定，不能称已完成。 |
| 生存获取、Curios / Trinkets | 未实现 | Notion 未定配方还是掉落，不编造；当前可用创造栏或测试命令取得。 |

饥饿数值 `0.02 / 0.05 / 0.15 / 0.4` 点每秒、雷遁 `1` 点、最低 `2` 点是暂定可配置平衡值，不是 Notion 明确指定的换算率。

## 接手步骤

1. 阅读此文件和最新 Notion。保护自己的工作区修改后，fetch 远端 `feat/art-first-core`，比较 HEAD 与已通过 CI 的 `538ccc18`。Devin 的分支若落后，不要再写一份已有核心逻辑；只接续或挑选增量，保留双方已有成果。
2. 从 Actions 下载 `fanren-wings-build`，或在 Java 17 / Gradle 8.8 环境运行下方检查。复用现成 PNG、HUD、网格和声音，无需先重建资产才能编译。
3. 在已有 Daytona 沙箱核对第二轮静态输出与动画进度，修复未通过视觉质量的部分；沿用几何、动作、镜头和设计，不全部重做。
4. 用真实客户端和双人专服补全上表验收。截图/视频必须标明游戏实机，不把 Blender 或 HUD 排版当作实机证据。
5. 更新同一 PR，或在自己的现有分支提交清楚标注依赖 #1 的增量 PR；记录所测 commit、环境、命令与失败日志。不要自动合并 main、生产发布或付款。
6. 在原 Slack 线程回报 PR、CI、A/B/C 证据和未完成项；没有通过实测前保持 Draft / 未完成状态。

```bash
python3 tests/validate_resources.py
mkdir -p build/math-test
javac --release 17 -d build/math-test src/main/java/dev/zerodotsix/fanrenwings/FlightMath.java tests/FlightMathTest.java
java -ea -cp build/math-test FlightMathTest
gradle --no-daemon --console=plain build
```

以下仅为修复后按需补动作的命令模板，不要对合格动作整套重跑。使用独立输出目录，人工核验后再合并证据，避免污染首版：

```bash
blender -b --python art/source/blender_scene.py -- --out /tmp/fenglei-art/review-pass --engine cycles --samples 48 --no-denoise --render-clips deploy --export-minecraft
```

渲染下载限速已经绕过并获得真实输出；现在的剩余工作是视觉质量和真实游戏验证，不是继续等 Blender 下载。当前没有已运行的 Minecraft 测试环境可以交接。
