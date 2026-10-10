# 风雷翅美术资源包清单（A / B / C）

三类资源严格分开，目录与文件名不混用。

## A — 概念 / 方向稿 `art/A_concept/`
| 文件 | 说明 |
|---|---|
| `fenglei-direction-v1.png` | 银白金纹方向稿（gpt 阶段产物，已复用；含掉落形态与备用靛青配色） |
| `hud_tiers_mockup.png` | 四档 HUD 视觉规范样张（悬停/御风/疾风/神霄，标注"消耗=饥饿值，无灵力条"） |

## B — 模型渲染 / 预览 `art/B_model_preview/model/`
由 `art/source/blender_scene.py`（Blender 4.3.2 CYCLES）生成；153 meshes / 1476 verts / 52 bones / 双侧 21 羽 / 翼展 6.4 格。
| 路径 | 内容 |
|---|---|
| `renders/orthographic_{front,side,top}.png` | 正交三视图 |
| `renders/hero_45.png` | 45° 展示图 |
| `renders/worn_{idle,flight}.png` | 穿戴收起 / 展开飞行 |
| `renders/dropped.png` | 地面掉落（0.4 格收起形态） |
| `videos/*.mp4` | 8 个动作预览（deploy / retract / hover / glide / boost / thunder_boost / blink / drop_spin_bob），768x432@24fps，见 `playback_verification.json` |
| `wind_thunder_wings.blend` / `.glb` / `.mesh.json` | 源模型、glTF 导出与 MC 网格导出 |
| `asset_manifest.json` | 资产清单与渲染参数 |

## C — 待接入游戏资源 `art/C_game_assets/textures/`
已按 MC 1.20.1 资源路径镜像到（迁入本仓后命名空间 sound_isolating_eraser） `src/main/resources/assets/sound_isolating_eraser/textures/`（同源文件，勿手工改副本——改 `tools/generate_wings_textures.py` 重新生成）。
| 相对路径 | 尺寸 | 用途 |
|---|---|---|
| `item/wind_thunder_wings.png` | 16x16 | 物品栏图标（像素风：银白羽扇+金雷纹+青羽尖） |
| `item/wind_thunder_wings_32.png` | 32x32 | 高清版图标（合成表/展示用） |
| `item/thunder_feather.png` | 16x16 | 雷鹏骨羽材料图标 |
| `entity/wings.png` | 64x64 | 穿戴模型贴图图集（覆羽带/飞羽/背带/收翼/白闪） |
| `gui/wings_hud.png` | 64x64 | HUD 图集（四档图标+翼徽+冷却环+充盈珠+铭牌底板） |
| `particle/{wind_ribbon,thunder_arc,impact_ring,trail_dot}.png` | 16x16 | 风带/电弧/冲击环/尾迹粒子 |

## 生成脚本 `art/source/`
- `blender_scene.py` — 建模+三视图+8 动作渲染（复用 gpt 成果）
- `gen_textures.py` — 全部 C 包贴图的可复现生成：`python3 tools/generate_wings_textures.py --out src/main/resources --art art`
