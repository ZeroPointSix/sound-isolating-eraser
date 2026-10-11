# 永夜都市 PR #20 独立审查报告（HEAD `6637565`）

审查人：Devin（独立审查，非实现作者）。范围：Notion v0-1 后端逻辑 + MC 模组设计对照；不重新开发，仅审查与验收。
方法：通读 `eternalnight/` 全部 15 个源文件 + 4 个 mixin + GameTest/harness，对照 docs/eternal-night-v0.1.md 逐条核对；CI 7/7 绿（156/156 GameTest）已复核。

## 一、偏离需求 / 潜在逻辑漏洞（按严重度）

| # | 级别 | 位置 | 发现 |
|---|------|------|------|
| 1 | 偏差候选 | `NightRules.Doors.open` | `exit() = opened.size() >= guarantee \|\| lucky`：`count` 单调不减，第 100 门之后**每扇新门都强制成为共享出口**，"100 门保底"字面语义更可能意图为恰好第 100 门必出、其后回归 1%。建议改为 `count == guarantee \|\| lucky`，需产品确认语义。 |
| 2 | 观感偏差 | `NightEvents.playerTick` | 共享出口门是普通 `DoorBlock`，其他玩家可点击关闭；`OPEN` 检查使关闭后穿越无效果——"不稳定"观感来源之一。物理上可重开恢复，但建议拦截激活出口的关门交互，或打日志提示。 |
| 3 | 观感偏差 | `CitySavedData` | 出口 5 分钟过期后玩家穿过无任何反馈（白面消失是唯一信号）——spec 窗口行为正确，但过期瞬间无任何状态变化可感知；建议过期时自动关闭门或本地提示。协作方报告的"回主世界不稳定"很可能部分源于过期后穿过。 |
| 4 | 轻微 | `NightRules.Doors.open` | `lucky = roll(chance, sample)` 在 dedupe 之前执行：重复开门消耗随机样本但不计入——结果正确但污染 RNG 流，建议先判重再 roll。 |
| 5 | 轻微 | `CitySavedData.activeExits` | `removeIf(until <= now)` 语义下 `Long.MAX_VALUE` 传参会清空整张表（e2e fixture 已踩中）——API 易误用，建议注释或改名 `pruneAndSnapshot`。 |
| 6 | 信息 | `NightEvents` | `Visiting` 标记写入后无读取方（仅审计/e2e 用）；保留无害。 |
| 7 | 信息 | `NightEvents.clonePlayer` | 对所有 `PlayerEvent.Clone` 复制 tag（含 End 返回非死亡克隆）——当前路径无害，可在注释中说明。 |

## 二、代码层复核通过项（与 spec 逐条吻合）

- **入城**：`min(.02+.08*floor(t/24000),.5)` 公式逐字吻合；深睡 100t 单次抽取（`attempted` 标记）；睡前快照 `TIME_SINCE_REST`；同夜 `dayTime/24000` hash 定中心；`CityEntryQueue` 异步备块（4 张票据 chunk、模板可用性后台校验）不阻塞 tick；超时/断线/死亡/维度切换释放票据。
- **保持原状**：`finishEntry` 仅传送，`Visiting`、背包、床位、fallDistance 均符合"不改"。
- **城内规则**：床返回 OTHER_PROBLEM 不爆炸；`PlayerSetSpawnEvent` 取消禁重生点；`PortalSpawnEvent` 取消；Crop/Sapling/Bonemeal 全禁；原版自然生物 FinalizeSpawn 白名单拦截。
- **门计数**：`lowerDoor` 上下半归一；`LinkedHashSet` 去重（重复不重抽）；`before.OPEN==false→after==true` 才计；mixin 链 `useItemOn→BlockState.use→DoorBlock.setBlock` 因果归因；按钮/拉杆经同步红石+计划 tick 传递身份（200t 上限、重启保守不计）；提示 25/50/75/90。
- **共享出口**：`Until = gameTime + EXIT_SECONDS*20`（默认 300s）；`SavedData` NBT 落盘+load 恢复已实测；`crossedDoor` 线段投影 + 门洞相交兜底；`returnHome` 床位优先→spawn 环形搜索→共享出生点保底，绝不滞留。
- **死亡**：城内死亡 `ReturnAfterDeath` + 清 Doors + Clone 复制 + respawn 回床清 tag；不改原版掉落。
- **城市结构**：`district` 55/15/30 逐字吻合；16t/64b/512r 网格；`applyCarvers/spawnOriginalMobs/applyBiomeDecoration` 全空覆写（无洞穴/原生怪/矿物植被）；地铁 SUBWAY_Y=40。
- **怪物**：shadow registry-safe null 跳过（PR #18 未合并安全）；霓虹瘤 COMMERCIAL+attachmentLight≥12+64 街区内大体型≤2；砌腐墙 512 区 `reserveBoss` 持久+死亡释放。
- **表现**：`CityExitNetwork` 每秒服务端快照（64 格内、≤256），白面纯客户端渲染；END_ROD 粒子 + BEACON_AMBIENT 每 80t 低沉音效；黑场渐隐+中文到达标题。

## 三、功能性验收状态（证据已在 PR/台账）

已实测通过：双客户端同夜深睡入城、共享白门穿越回各自床、同世界进程重启读回（seed/exit Until/床/背包/门历史）、真实死亡 7 钻掉落+床重生+清史、三区 25 栋建筑门位 BFS 全达街道（CITY_REACH）、怪物分区/亮度/墙面规则实机断言（CITY_MONSTER）、156/156 Forge GameTest、87 文件原包 SHA 门禁。

未覆盖（如实列出，不冒充通过）：5 分钟共享窗实时时长、音效听觉验收、默认 2%/1% 概率统计分布、时钟/指南针原版行为实测、挂壁附着视觉统计密度、"不稳定"报告的根因归因（已加固检测+保底回城，但未能复现原失败日志确认根因）。

## 四、阻塞（非实现问题）

- 32 字形：原包仅提供 glyph 0–3 × 5 色（`NeonSignBlock.GLYPH 0..3` 亦证实），缺 28 字形授权资源。
- 医院/警局/消防专用外观：87 文件原包内无对应模板。
- PR #18（缠影子）：Draft + main 冲突 + verify FAILED，依赖 #20 先合并。

## 五、结论

实现与 Notion v0-1 高度吻合，工程质量良好（因果归因、SavedData、异步入城、registry-safe 依赖）。**发现 1 项需确认的语义偏差（#1 保底语义）、2 项观感偏差（#2/#3 出口交互）、3 项轻微问题**。建议：确认 #1 语义后小改；#2/#3 可选增强。不主张阻塞合并评审，但应在合并前对 #1 取得产品裁决。
