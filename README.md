# Sound-Isolating Eraser · 隔音橡皮

《畸海浮城》× Minecraft 1.20.1 Forge 一次性小道具 mod。手持隔音橡皮点击方块表面，
用可切换形状画出多列 5 格高的透明虚拟墙；底部连续白痕是墙的锚点。

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

- 运行依赖：Minecraft 1.20.1、Forge 47.x、Curios API 5.14.1+1.20.1。客户端与服务端均需安装本 mod 和 Curios。
- 物品：`sound_isolating_eraser:gravity_jade_pendant`，在工具创造物品栏中获取，也可使用 `/give`。设计未定义合成配方，因此不添加配方。
- 只有 Curios 的实际 `necklace` 槽启用能力；手持、背包和饰品外观槽不会启用。复用 Curios 的项链槽预设，不增加同名槽数量。
- 被动感知：佩戴者本地每 2 tick 检测附近 16 格 AABB 内实体的位置变化；阈值 0.02 格/tick，停下约 4 tick 消失，最多显示最近 64 个。生物显示白色模型轮廓，其他实体显示白色边框。
- 按住 `V` 预览 5×5 的范围，滚轮每格调整 1 方块距离（默认 8，范围 3–20），松开施放。`Esc`、右键、打开界面、死亡、摘下玉佩和切换维度取消；按键可在原版设置中修改。
- 重力场默认高 5 格、持续 15 秒：范围内所有生物包含施法者受缓慢 III，每秒承受 1 点自定义 `gravity_crush` 压伤。效果每 tick 刷新为 10 tick，离场约 0.5 秒内消失。护甲、无敌帧及创造免疫仍按原版规则处理。成功施放后冷却 30 秒，拒绝施放不消耗冷却。
- **压伤无击退**：伤害类型加入 `minecraft:no_impact`，并由 `GravityDamage.hurt` 在真实 `LivingEntity.hurt` 调用范围内取消该受害者的 `LivingKnockBackEvent`，在 `finally` 恢复伤害前速度、`hurtMarked`、`hasImpulse`。不取消伤害、不持续锁位置、不清除玩家本来的运动；普通攻击仍可击退。缓慢 amplifier 默认/最小值为 2，旧值 1 经 Forge 配置校正为 III。
- 创建时仅压毁一次标签内的脆弱方块；每列只考虑最高的暴露土层，以 25% 概率破坏，不递归挖掘。方块实体、不可破坏方块与取消 Forge 方块破坏事件的领地保护会保留。
- 参数位于世界目录 `serverconfig/sound_isolating_eraser-server.toml`；`gravity_fragile` 和 `gravity_surface_fragile` 方块标签可由数据包扩展。
- 服务端接收客户端预览中心 `BlockPos` 与维度，不再按当前朝向重算选区；校验装备、存活、冷却、距离、边界和区块加载状态，客户端不执行伤害或破坏。

玉佩的 32×32 像素纹理由 `gradle/gravity-texture.gradle` 在资源处理前自动生成，不需要额外图片工具。
功能分支的 `Gravity Jade Validation` 工作流构建 jar 并运行原有及新增的 Forge GameTest，不发布 Release。
工作流还会启动两个隔离的实际 Forge 客户端，以 X11 原生按键、滚轮和鼠标输入验证穿墙模型轮廓、预览与取消、可重绑定按键、私有感知隔离及公共重力场同步，并保存截图与日志证据。

## 第二轮验收

```bash
bash ./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1G compileJava runGameTestServer
bash ./gradlew --no-daemon -PgravityE2E writeGravityTestClasspath
python3 tools/gravity-e2e.py
```

- GameTest 覆盖五种形状/四朝向/负坐标、逐列清理、最后一格障碍或液体整次失败、缺支撑、保护取消恢复原植物、真实 `ItemStack.useOn` 包装路径与恰好一次事件、主副手模式及非法数据、仇恨失去/绕行/拆墙恢复及高级怪排除。
- 压伤测试保留真实伤害源和攻击者，连续 300 tick 检查 Cow/Zombie/Player 的 15 次伤害、无位移、缓慢 III、到期清理；另检查原有速度不被抹掉和普通击退仍生效。测试将受试者血量设 100、防御设 0 以准确数伤害，关闭测试玩家自然回血，但不关闭伤害或强制锁定位置。
- 双客户端测试继续运行玉佩全部已有白描框/瞄准/同步/像素回归，再用真实联网生存玩家和两种生物检查 15 次压伤。随后通过原生 `R`/重绑定 `Y` 和右键完成五种预览与放置，另一客户端验证全部形状同步。
- 日志、通过标记与截图位于 `build/gravity-e2e/`；运行会清理旧标记，必须重新获得证据。仅本机回环测试服，不部署生产。共享机器较慢时可显式设置 `GRAVITY_QA_STARTUP_SECONDS` 和 `GRAVITY_QA_TEST_SECONDS` 扩大启动/测试等待上限，不放宽断言。
