# Sound-Isolating Eraser · 隔音橡皮

《畸海浮城》× Minecraft 1.20.1 Forge 奇物与道具 mod。手持隔音橡皮点击方块表面，
用可切换形状画出多列 5 格高的透明虚拟墙；底部连续白痕是墙的锚点。

## 奇物与道具

分类由物品数据标签决定，与原版稀有度、附魔和物品名颜色无关。鼠标悬停时，名称下方第一行显示金色“奇物”（英文 `Curio`）或灰蓝色“道具”（英文 `Tool`），原有提示内容与顺序保留；同时属于两个标签时只显示“奇物”。未加入任一标签的物品不显示分类行。

设计规则：**一个世界（畸界）只包含一件奇物**。本次只记录规则，不在代码中限制生成或持有数量；以下四件奇物分别属于哪个世界，仍待用户确认，不预先分配。

| 当前奇物 | 物品 ID |
| --- | --- |
| 超级肥料 | `sound_isolating_eraser:super_fertilizer_bag` |
| 重力玉佩 | `sound_isolating_eraser:gravity_jade_pendant` |
| 杀夏刀 | `sound_isolating_eraser:shaxiadao` |
| 隔音橡皮 | `sound_isolating_eraser:sound_isolating_eraser` |

强化药片、空药板、空肥料袋、肥沃果实、风雷翅、雷鹏骨羽、十枚业纹和所有刷怪蛋均不在默认奇物名单中。雨寂佛都的信号枪与三色信号弹属于道具；当前基线尚未包含这些物品，因此道具标签暂为空，后续信号枪实现合并时补入实际注册 ID。

标签文件位于 `src/main/resources/` 下；数据包使用同样的 `data/` 相对路径即可扩展，无需修改 Java：

- 奇物：`data/sound_isolating_eraser/tags/items/qiwu.json`（`#sound_isolating_eraser:qiwu`）。
- 道具：`data/sound_isolating_eraser/tags/items/daoju.json`（`#sound_isolating_eraser:daoju`）。

数据包使用 `"replace": false` 和 `"values": ["namespace:item_id"]` 追加物品；使用 `"replace": true` 可覆盖默认名单。加载或 `/reload` 后，客户端使用服务端同步的标签显示分类。分类不会改变偷盗怪按“附魔 + 稀有度”选择目标的逻辑，也不调整创造物品栏排序。

## 安装与双端依赖

本分支运行环境为 **Minecraft 1.20.1 + Forge 47.2.0**。客户端与独立服务端都必须安装本 mod 及以下两个前置模组：

| 前置模组 | 版本与加载器 | 安装位置 |
| --- | --- | --- |
| GeckoLib | **Forge / Minecraft 1.20.1** 版，建议使用 **4.4.9**；依赖范围为 `[4.4.9,5)`，即 `>= 4.4.9` 且 `< 5` 的兼容版本 | 每位玩家的客户端与服务端均必需 |
| Curios API | **Forge / Minecraft 1.20.1** 版，沿用 **5.14.1+1.20.1**；依赖范围 `[5.14.1,6)` | 每位玩家的客户端与服务端均必需，重力玉佩的现有依赖保留 |

将本 mod、GeckoLib 和 Curios API 的 jar 分别放入客户端与服务端各自的 `mods/` 目录，双端保持相同的模组及依赖版本。单人游戏也需要在本地客户端安装全部前置；不要使用 Fabric、NeoForge 或其他 Minecraft 版本的依赖包。

公开的 `sound_isolating_eraser-<ver>.jar` **不内置 GeckoLib 或 Curios API**，下载本 mod 后仍需另行安装这两个依赖，不能只安装在客户端或只安装在服务端。

## 第三个小物品：强化药片（0.4.0）

- 物品 ID：`sound_isolating_eraser:enhancement_pill_pack`；空板为 `sound_isolating_eraser:empty_pill_pack`。工具创造物品栏或 `/give` 获取，不添加合成和掉落。
- 默认一板 12 粒，满饥饿也可长按右键 32 tick 吞服。中途松开不消耗，最后一粒替换为空板。不可修复、附魔或通过铁砧合并补药；创造模式默认不消耗。
- 默认药效 48000 tick：力量 II、速度 II、急迫 II、抗性 I；通过 `pill_suppressed` mob_effect 标签拦截十种负面效果。不会免疫伤害，也不会阻止未加入标签的其他模组效果。
- 药效归零同 tick 进入 12000 tick 戒断：缓慢 II、挖掘疲劳 II、虚弱 II、黑暗。牛奶只清除表现，40 tick 内重新施加；戒断期间再吃一粒会立刻恢复完整药效。重复吃药只刷新，不累加时长或倍率。
- 真正计时在可序列化的玩家 Capability 内，死亡、跨维度、保存重进及服务器重启都保留。仅在线且存活时递减，离线和死亡界面不计时。原有外部药水及其隐藏效果链单独保留，切相不会误删其他来源的增益。
- 参数统一位于既有世界配置 `serverconfig/sound_isolating_eraser-eraser-server.toml` 的 `enhancement_pill` 节：`pillPackUses=12`、`pillDurationTicks=48000`、`withdrawalDurationTicks=12000`、`useDurationTicks=32`，以及各增益/反噬等级、`withdrawalDarknessEnabled=true`、`withdrawalNauseaEnabled=false`、`allowMilkCureWithdrawal=false`、`consumeInCreative=false`、`showHudTimer=true`。药片不新增前置依赖，原仓库重力玉佩的 Curios 依赖仍保留。
- 左上角显示仅本人可见的药效/反噬计时、48 像素进度条和药板图标；吞服时准星下方显示细进度条。网络仅服务端向本人发送状态，不新增服药 C2S 消息。
- 原始美术来自设计稿 8.4 的 `D:\mc\preview\pixelart.js`，保留在 `tools/pill/pixelart.js`。12→1 粒及空板按原版 damage 谓词切换，末粒在右下角；专用粒子贴图只产生淡蓝药屑。`python3 tools/pill/export-assets.py` 可无第三方图片依赖重导出相同 RGBA 像素。
- [原始需求](https://app.notion.com/p/3f3463aed7e681be8d51e866a8623dd1)。本功能接续 PR #9，不包含其他待合并的风雷翅分支。

药片验证（JDK 17；真实客户端测试额外需要 Xvfb、xdotool 和软件 OpenGL）：

```bash
bash ./gradlew --no-daemon build runGameTestServer
bash ./gradlew --no-daemon -PpillE2E writePillTestClasspath
python3 tools/pill-e2e.py
```

GameTest 同时回归隔音橡皮、重力玉佩和药片。独立的 `pillE2E` 测试模组不会打入正式 jar；两位真实 Forge 客户端通过原生右键验证取消、吞服、免疫、反噬、喝奶、再次服用与空板同步，再实际关闭和重启测试服核验持久化。证据位于 `build/pill-e2e/`，测试服仅绑定 `127.0.0.1:25576`。CI 的 `Enhancement Pill Validation` 只上传构建与验证工件，不发布 Release。

## 玩法规则（与设计稿一致）

- **点地面（上表面）**：点击位置上方立起 1×5 半透明墙，底格锚点把白色擦痕作为**平面贴花**贴在脚下那个方块的上表面（非立体凸起）。
- **点墙面（侧表面）**：同样 5 格一列，白痕同样以平面贴花贴在被点击的那一面上。
- **点方块下表面**：拒绝，什么都不发生。
- **模式切换**：手持时按 `R` 循环单点、横排、纵排、斜线、圆环；可在 Controls 重绑定，支持副手。模式保存于各物品，服务端验证切换，HUD 显示当前模式。
- **形状**：以点击格为中心、玩家水平朝向为前方，横排沿左右轴 5 格，纵排沿前后轴 5 格，斜线沿前右/后左方向 5 格；圆环在 5×5 外框上取 12 格，中心留空。每个落点都向上生成 5 格墙。
- **预览与白痕**：释放前显示整条白痕和墙高线框；本地可判定的非法位置显示红色。地面横纵线、斜线、圆环白痕相接，成功后保留相同轨迹。服务端保护插件的最终裁决可能晚于预览。
- **原子放置**：整个形状任一格被实心方块、液体、已有墙体或生物占用，越界、区块未加载、无权限、耐久不足，或多点任一落点缺少支撑面，均整次失败，不留下半成品、不耗耐久。Forge 多格放置事件只发一次；被保护插件取消或写入失败时回滚全部原方块。
- **侧墙边界**：保留单点侧面白痕；多点仍在同一水平面绘制，每点须有相同方向的实心支撑面，因此直立墙面通常只支持与墙相切的一排。圆环/斜线完整体验以平地为准，不会自动跳点、爬台阶或转成竖直画布。下表面继续拒绝。
- **联动销毁**：拆任意一格墙或底部白痕 → 整列立即一起消失；TNT/苦力怕爆炸同样整列消失；
  相邻列互不影响。孤儿屏障（锚点被 /setblock 拆掉）1 tick 后自毁。
- **墙体性质**：半透明薄膜、完整实体碰撞（挡玩家/怪物/弹射物/寻路/水岩浆）、
  泥土级硬度 0.5 / 爆炸抗性 0.5、无掉落物、非红石导体、活塞不可推动、不可生成怪物。
- **有限隔绝仇恨**：仅当 tag 内生物与玩家眼部连线确实穿过橡皮墙的碰撞体时，阻止视线感知/新目标获取；已有目标及对应受伤仇恨每 5 tick 清除并停止追踪路径。绕开或拆墙后立即允许原版 AI 重新发现。非玩家目标不受此机制影响；没有全局隐身或永久失忆。
- **默认名单**：`sound_isolating_eraser:eraser_isolated_mobs` 实体类型 tag 含 zombie、zombie_villager、husk、drowned、skeleton、stray、creeper、spider、cave_spider、silverfish、endermite；数据包可覆盖。Boss、高级怪默认不在名单，仍保留先前穿透明墙的视线行为。
- **耐久**：每成功一列耗 1 点，五格线耗 5、圆环耗 12（创造不消耗）。世界目录 `serverconfig/sound_isolating_eraser-eraser-server.toml`：
  `barrierHeight`（默认 5）、`eraserDurability`（默认 64）。墙硬度/抗性按设计稿固定 0.5，
  烘焙在方块属性里，不走配置。
- **配置迁移**：本轮橡皮配置改为 Forge SERVER 配置并同步客户端，避免预览高度、耐久与服务端不一致。旧 `config/sound_isolating_eraser-common.toml` 不再读取；使用过非默认设置的服主应将同名两节复制到上述世界配置，也可放进 `defaultconfigs/` 作为新世界默认值。默认 5/64 不变；玉佩原配置文件不变。

## 结构

```
com.zeropointsix.eraser
├── ModMain                    注册入口 + 创造模式物品栏
├── registry/ModBlocks         eraser_anchor / eraser_barrier
├── registry/ModItems          sound_isolating_eraser
├── item/SoundIsolatingEraserItem  useOn 原子放置
├── block/EraserWallBlock      公共基类（碰撞/寻路/活塞/视线遮挡）
├── block/EraserAnchorBlock    底格锚点：FACING=白痕朝向，onRemove 整列联动
├── block/EraserBarrierBlock   LEVEL=1..4，孤儿自毁，onRemove 拆锚点联动
├── mixin/LivingEntityWallVisibilityMixin  Mob 视线穿透墙（原版按碰撞形状挡视线）
├── config/CommonConfig        Forge 配置
├── client/ClientSetup         半透明渲染层
└── gametest/                  原有用例及第二轮原子回滚、绘制、仇恨、压伤回归测试
```

## 构建与验证

需要 JDK 17：

```bash
./gradlew build              # 产物 build/libs/sound_isolating_eraser-<ver>.jar
./gradlew runGameTestServer  # 真服务端跑全部 GameTest（含 mob 视线/联动销毁/爆炸等）
```

纹理由 `tools/generate_textures.py` 生成（PIL，可复现）。
多格白痕模型与 blockstate 由 `python3 tools/generate_eraser_traces.py` 生成。

## 说明

- `sound_isolating_eraser.mixins.json`：dev 运行经 `--mixin.config` 加载，
  生产 jar 经 manifest `MixinConfigs` 加载；`hasLineOfSight → m_142582_` 的 SRG
  映射在 `mixins.sound_isolating_eraser.refmap.json`。


## CI

Push to `main` runs [`.github/workflows/build-jar.yml`](.github/workflows/build-jar.yml):
Java 17 + Gradle build, uploads `sound_isolating_eraser-*.jar` as a workflow
artifact (same filename), and publishes a GitHub Release with that jar attached.


## Gravity Jade Pendant / 重力玉佩

- 运行依赖见上方“安装与双端依赖”：Minecraft 1.20.1、Forge 47.2.0；客户端与服务端均需安装本 mod、Curios API 和 GeckoLib，Curios 项链槽依赖保持不变。
- 物品：`sound_isolating_eraser:gravity_jade_pendant`，在工具创造物品栏中获取，也可使用 `/give`。设计未定义合成配方，因此不添加配方。
- 只有 Curios 的实际 `necklace` 槽启用能力；手持、背包和饰品外观槽不会启用。复用 Curios 的项链槽预设，不增加同名槽数量。
- 被动感知：佩戴者本地每 2 tick 检测附近 16 格 AABB 内实体的位置变化；阈值 0.02 格/tick，停下约 4 tick 消失，最多显示最近 64 个。生物显示白色模型轮廓，其他实体显示白色边框。
- **0.11.0：按下 `V` 立即施放**，可在原版设置改键。无需瞄准、松键或滚轮调整；服务端以施法瞬间的玩家脚下坐标为固定圆心，移动、转头不移动领域。
- 圆柱领域水平半径 16 格、向下 8 格、向上 16 格，持续 300 tick（15 秒），成功后冷却 1200 tick（60 秒）。世界边界和建筑高度处裁剪；未佩戴、死亡、旁观、冷却中或涉及未加载区块时拒绝，不消耗冷却。
- 敌方为 `Enemy` 或正在攻击友方的 `Mob`，持续缓慢 V，并以受控竖直速度悬在地面上约 3 格。离场后自然下落，缓慢最多残留 10 tick。中立动物、村民和未攻击友方的傀儡不受影响。
- 友方为所有非旁观玩家和已驯服宠物（含马），优先于敌方判定；得到迅捷 III、跳跃提升 III，离场后保留 80 tick。施法者不受压伤、减速或重摔。
- 每 20 tick 对敌方结算一次 `gravity_crush`：设最大生命值为 H，H≤50 时伤害为 `0.2H`，否则为 `10*(50/H)^e`，`e=ln(3/10)/ln(50/300)`。示例 H=20/50/300/12000 时为 4/10/3/约0.25。伤害穿护甲、穿无敌帧，但保留怪物自身减免和原版特殊伤害规则。
- 自然到期时对仍在领域内的敌方结算一次曲线值的 3 倍重摔，并赋予 -2.5 向下速度，保留原版摔落伤害；落地伴随方块碎屑和重落地声音。提前移除、区块卸载或重新载入不会补发重摔。
- `gravity_heavy` 实体标签免疫领域抬升和下砸速度，但仍受缓慢、压伤和即时重摔伤害。默认含末影龙、凋灵、监守者，以及可选的 `#forge:bosses`、`qihai:shifotou`、`qihai:chennu`。其他模组缺席不会导致加载失败。
- **压伤无击退**：保留 `minecraft:no_impact` 及 `GravityDamage.hurt` 的受害者范围击退取消和原运动状态恢复；不锁水平位置、不清除原有运动，普通攻击仍可击退。公共圆柱轮廓与敌人头顶下压气流不依赖佩戴状态可见，被动感知仍仅本人可见。
- 创建时仅压毁一次标签内的脆弱方块；每列只考虑最高的暴露土层，以 25% 概率破坏，不递归挖掘。方块实体、不可破坏方块与取消 Forge 方块破坏事件的领地保护会保留。
- 参数位于世界目录 `serverconfig/sound_isolating_eraser-server.toml` 的 `gravity_jade` 节；`gravity_fragile`、`gravity_surface_fragile` 和 `gravity_heavy` 标签可由数据包扩展。新参数包括 `gravityFieldRadius=16`、`gravityFieldBelow=8`、`gravityFieldAbove=16`、`maxPercent=0.2`、`capDamage=10`、`referenceHealth=300`、`referencePercent=0.01`、`levitationHoverHeight=3`、`slamDamageMultiplier=3`、`allyBuffLingerTicks=80`。无效曲线锚点退回有限的封顶线性曲线，不产生 NaN 或负伤害。
- 旧瞄准距离、场高和固定伤害配置已移除。已有世界需将 `gravitySlownessAmplifier` 改为 `4`、`gravityFieldCooldownTicks` 改为 `1200` 才与新版默认一致；自定义值不被静默覆盖。网络协议升为 `3`，客户端与服务端需同时升级；C2S 只发送施放意图，无坐标、距离或维度字段，客户端不执行伤害或破坏。

玉佩的 32×32 像素纹理由 `gradle/gravity-texture.gradle` 在资源处理前自动生成，不需要额外图片工具。
功能分支的 `Gravity Jade Validation` 工作流构建 jar 并运行原有及新增的 Forge GameTest，不发布 Release。
工作流还会启动两个隔离的实际 Forge 客户端，以 X11 原生输入验证穿墙模型轮廓、按键即施放、正常滚轮、可重绑定按键、私有感知隔离及公共圆柱同步，并保存真实渲染像素、截图与日志证据。


## Wind-Thunder Wings / 风雷翅

银白金纹的飞行法宝，胸甲槽穿戴（同鞘翅惯例），消耗原版饥饿值——**没有也不新增灵力条**。

- 物品：`sound_isolating_eraser:wind_thunder_wings`（EPIC 级，防火）与材料 `sound_isolating_eraser:thunder_feather`（雷鹏骨羽，幻翼被雷击陨落掉落）。
- 展开/收起：空中双击当前跳跃绑定（默认空格），跟随原版控制设置改键，也支持修饰键与鼠标；落地自动收起，水下保持飞行（原规格 §4.6）。
- 四档速度（G 切换）：悬停 ≤5 / 御风 30 / 疾风 60 / 神霄 120 m/s；H 切换悬停锚定。
- 雷遁（R）：向视线方向闪现 24 格，12 tick 内连闪最多 3 次；满 3 次进入 160 tick 冷却，否则 40 tick。
- 饥饿消耗：按档每秒 0.4/0.8/2.0/4.0 点 exhaustion（`wings/` 下 `FlightEvents.onPlayerTick` 调 `FoodData.addExhaustion`）；饥饿 <4 禁止展开，<6 限制为悬停/巡航，=0 强制悬停并附加缓降。
- 被雷劈中：回满饥饿并获得 60 秒「充能」——期间飞行与雷遁不消耗饥饿。
- HUD：右下角 64×16 铭牌（`textures/gui/wings_hud.png` 图集：四档图标 + 雷遁冷却环 + 充能珠 + 铭牌底），雷闪时有短暂白屏闪光；无灵力条。
- 数值全部在 `serverconfig/sound_isolating_eraser-wings-server.toml`（独立文件名，避免与 gravity SERVER 配置冲突）与客户端 config 中可调。
- 音效：`wind_loop`（飞行环境声）、`thunder_boom`（破档音爆）、`thunder_blink`（雷瞬）。
- 合成：下界之星 + 雷鹏骨羽×2 + 鞘翅 + 避雷针×2 + 皮革胸甲（`data/.../recipes/wind_thunder_wings.json`）。

美术与资源分包（`art/`）：A `art/A_concept/` 概念稿与 HUD 样张；B `art/B_model_preview/` Blender 三视图/穿戴/掉落渲染与 8 段动作视频；C `art/C_game_assets/` 已接入游戏的贴图。游戏贴图可由 `tools/generate_wings_textures.py`（PIL + 内嵌 NotoSansSC 字体）复现生成；`tools/blender_wings_scene.py` 为渲染脚本。上游 fenglei-wings 仓 PR #1 的脚本/清单/预览保留在 `art/upstream_pr1/`。
## 第二轮验收

```bash
bash ./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1G compileJava runGameTestServer
bash ./gradlew --no-daemon -PgravityE2E writeGravityTestClasspath
python3 tools/gravity-e2e.py
```

- GameTest 覆盖五种形状/四朝向/负坐标、逐列清理、最后一格障碍或液体整次失败、缺支撑、保护取消恢复原植物、真实 `ItemStack.useOn` 包装路径与恰好一次事件、主副手模式及非法数据、仇恨失去/绕行/拆墙恢复及高级怪排除。
- 玉佩 GameTest 覆盖曲线十个锚点、圆柱边界与世界裁剪、装备和冷却、穿甲/同 tick 伤害、敌我与宠物分类、悬浮/离场下落、自然到期重摔/提前移除、重型目标和重叠领域；另保留原有速度和普通击退回归。
- 双客户端测试使用真实联网生存玩家和两种生物检查敌方 15 次压伤与一次 3 倍重摔、悬浮、缓慢 V、玩家增益和被动动物无伤；测试僵尸设为 300 最大生命值以完整观察生命周期。随后通过原生 `R`/重绑定 `Y` 和右键完成隔音橡皮五种预览与放置，另一客户端验证全部形状同步。
- 日志、通过标记与截图位于 `build/gravity-e2e/`；运行会清理旧标记，必须重新获得证据。仅本机回环测试服，不部署生产。共享机器较慢时可显式设置 `GRAVITY_QA_STARTUP_SECONDS` 和 `GRAVITY_QA_TEST_SECONDS` 扩大启动/测试等待上限，不放宽断言。
