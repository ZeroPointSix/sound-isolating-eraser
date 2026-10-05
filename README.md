# Sound-Isolating Eraser · 隔音橡皮

《畸海浮城》× Minecraft 1.20.1 Forge 一次性小道具 mod。手持隔音橡皮点击方块表面，
一次画出 1 列 5 格高的透明虚拟墙；底部的白色擦痕是整列墙的锚点。

## 玩法规则（与设计稿一致）

- **点地面（上表面）**：点击位置上方立起 1×5 半透明墙，底格锚点把白色擦痕作为**平面贴花**贴在脚下那个方块的上表面（非立体凸起）。
- **点墙面（侧表面）**：同样 5 格一列，白痕同样以平面贴花贴在被点击的那一面上。
- **点方块下表面**：拒绝，什么都不发生。
- **原子放置**：5 格任一被实心方块、液体、已有墙体或实体占用 → 整列不生成、不耗耐久，
  ActionBar 提示「空间不足」。
- **联动销毁**：拆任意一格墙或底部白痕 → 整列立即一起消失；TNT/苦力怕爆炸同样整列消失；
  相邻列互不影响。孤儿屏障（锚点被 /setblock 拆掉）1 tick 后自毁。
- **墙体性质**：半透明薄膜（Mob 视线可穿透）、完整实体碰撞（挡玩家/怪物/弹射物/寻路/水岩浆）、
  泥土级硬度 0.5 / 爆炸抗性 0.5、无掉落物、非红石导体、活塞不可推动、不可生成怪物。
- **耐久**：每成功一列耗 1 点（创造不消耗）。配置 `sound_isolating_eraser-common.toml`：
  `barrierHeight`（默认 5）、`eraserDurability`（默认 64）。墙硬度/抗性按设计稿固定 0.5，
  烘焙在方块属性里，不走配置。

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
└── gametest/EraserGameTests   14 个 GameTest 用例
```

## 构建与验证

需要 JDK 17：

```bash
./gradlew build              # 产物 build/libs/sound_isolating_eraser-<ver>.jar
./gradlew runGameTestServer  # 真服务端跑全部 GameTest（含 mob 视线/联动销毁/爆炸等）
```

纹理由 `tools/generate_textures.py` 生成（PIL，可复现）。

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
- 重力场默认高 5 格、持续 15 秒：范围内所有生物包含施法者受缓慢 II，每秒承受 1 点普通伤害。护甲、无敌帧及创造免疫仍按原版规则处理。成功施放后冷却 30 秒，拒绝施放不消耗冷却。
- 创建时仅压毁一次标签内的脆弱方块；每列只考虑最高的暴露土层，以 25% 概率破坏，不递归挖掘。方块实体、不可破坏方块与取消 Forge 方块破坏事件的领地保护会保留。
- 参数位于世界目录 `serverconfig/sound_isolating_eraser-server.toml`；`gravity_fragile` 和 `gravity_surface_fragile` 方块标签可由数据包扩展。
- 服务端接收客户端预览中心 `BlockPos` 与维度，不再按当前朝向重算选区；校验装备、存活、冷却、距离、边界和区块加载状态，客户端不执行伤害或破坏。

玉佩的 32×32 像素纹理由 `gradle/gravity-texture.gradle` 在资源处理前自动生成，不需要额外图片工具。
功能分支的 `Gravity Jade Validation` 工作流构建 jar 并运行原有及新增的 Forge GameTest，不发布 Release。
工作流还会启动两个隔离的实际 Forge 客户端，以 X11 原生按键、滚轮和鼠标输入验证穿墙模型轮廓、预览与取消、可重绑定按键、私有感知隔离及公共重力场同步，并保存截图与日志证据。
