# 真实客户端验收原始证据

源提交：[`814a6a21c22d9980cdc606640af2f2535b83401b`](https://github.com/ZeroPointSix/sound-isolating-eraser/commit/814a6a21c22d9980cdc606640af2f2535b83401b)。

[GitHub Actions 运行](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38159095483) / [客户端任务](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38159095483/job/114528062139) / [构建与全量 GameTest](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38159095483/job/114528062544)。

下列文件从该运行的官方 GitHub Actions 制品原样提取，未修改截图或日志内容。`provenance.json` 记录原始制品 ID、ZIP 校验和及每个文件的 SHA-256。README 是说明文字，不属于运行时产生的原始日志。

## 逐怪索引

| 实体 | 真实截图 | 渲染/动画 | 真实玩家 AI/碰撞 | 死亡音频引擎确认 | 详细移动/技能声音 |
| --- | --- | --- | --- | --- | --- |
| 愚痴 | [截图](yuchi.png) | [报告](client-yuchi.pass) | [报告](server-ai-yuchi.pass) | [报告](client-audio-yuchi.pass) | [日志](audio-events-yuchi.log) |
| 杀生 | [截图](shasheng.png) | [报告](client-shasheng.pass) | [报告](server-ai-shasheng.pass) | [报告](client-audio-shasheng.pass) | [日志](audio-events-shasheng.log) |
| 偷盗 | [截图](toudao.png) | [报告](client-toudao.pass) | [报告](server-ai-toudao.pass) | [报告](client-audio-toudao.pass) | [日志](audio-events-toudao.log) |
| 恶口 | [截图](ekou.png) | [报告](client-ekou.pass) | [报告](server-ai-ekou.pass) | [报告](client-audio-ekou.pass) | [日志](audio-events-ekou.log) |
| 妄语 | [截图](wangyu.png) | [报告](client-wangyu.pass) | [报告](server-ai-wangyu.pass) | [报告](client-audio-wangyu.pass) | [日志](audio-events-wangyu.log) |
| 两舌 | [截图](liangshe.png) | [报告](client-liangshe.pass) | [报告](server-ai-liangshe.pass) | [报告](client-audio-liangshe.pass) | [日志](audio-events-liangshe.log) |
| 邪淫 | [截图](xieyin.png) | [报告](client-xieyin.pass) | [报告](server-ai-xieyin.pass) | [报告](client-audio-xieyin.pass) | [日志](audio-events-xieyin.log) |
| 绮语 | [截图](qiyu.png) | [报告](client-qiyu.pass) | [报告](server-ai-qiyu.pass) | [报告](client-audio-qiyu.pass) | [日志](audio-events-qiyu.log) |
| 贪欲 | [截图](tanyu.png) | [报告](client-tanyu.pass) | [报告](server-ai-tanyu.pass) | [报告](client-audio-tanyu.pass) | [日志](audio-events-tanyu.log) |
| 嗔怒 | [截图](chennu.png) | [报告](client-chennu.pass) | [报告](server-ai-chennu.pass) | [报告](client-audio-chennu.pass) | [日志](audio-events-chennu.log) |
| 十佛头 | [截图](shifotou.png) | [报告](client-shifotou.pass) | [报告](server-ai-shifotou.pass) | [报告](client-audio-shifotou.pass) | [日志](audio-events-shifotou.log) |
| 骨刺 | [截图](bone_spike.png) | [报告](client-bone_spike.pass) | [撞墙与消失](server-bone_spike.pass) | 附件无独立音效，N/A | 发射声记录于恶口日志 |

每张截图都有同名 `*-control.png` 对照图。对照图只在同一客户端、同一相机把目标移出视野后截取，用来确认前景确实是目标实体，不用空白世界冒充渲染成功。

## 汇总与复现

- [客户端汇总](client-gallery.pass)、[服务器汇总](server-gallery.pass)。
- [客户端完整日志](client.log)、[服务器完整日志](server.log)。
- [全量 GameTest 日志](gametest.log)：182 required tests passed，25 个 Yuji 用例被发现且完成。
- [来源及文件校验和](provenance.json)。
- [需求对照与具体未覆盖项](../../../yuji-acceptance.md)。

真实 Forge 专用服务器、完整 Minecraft 客户端、自动化驱动的实际连接玩家；没有以假玩家或 Mock 客户端替代 E2E。使用 Xvfb/软件 OpenGL，OpenAL null 输出设备。日志证明实际声音引擎播放，不代表人工听音或扬声器录音。

从上述源提交，按仓库既有 Gradle 环境执行：

```bash
python3 tools/yuji/validate_assets.py
bash ./gradlew -PyujiE2E writeYujiTestClasspath --no-daemon
python3 tools/yuji/e2e.py
bash ./gradlew build runGameTestServer --no-daemon --stacktrace 2>&1 | tee build/yuji-gametest.log
python3 tools/yuji/verify_gametest_log.py build/yuji-gametest.log
```

请将结果与源提交对应。之后的证据整理提交只增加文档/原始制品和字幕校验；新 HEAD 的 CI 入口在 PR checks 中，不能把历史 run 当作新 HEAD 的运行。
