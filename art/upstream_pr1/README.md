# upstream_pr1 — fenglei-wings 仓 PR #1 保留材料

来源：`ZeroPointSix/fenglei-wings` 分支 `feat/art-first-core`（maton-app 实现，modid `fanren_wings`），commit `283b4df`。
风雷翅功能已按用户指示迁入本仓（`sound_isolating_eraser` modid）。本目录仅保全其
可复用脚本/清单/预览/交接文档，命名空间与贴图为上游 fanren_wings 版本，
**不接入本仓游戏资源**——本仓接入的是 `art/C_game_assets/` → `assets/sound_isolating_eraser/`。

- `source/`：资产构建/整合/校验/编码流水线脚本（引用 fanren_wings 路径，复用需改命名空间）
- `metadata/`：游戏资源清单、HUD 图集与粒子序列规范、素材来源登记
- `previews/`：HUD/图标离线样张与粒子动图
- `sounds`（三个 .ogg 已接入本仓 `assets/sound_isolating_eraser/sounds/`，经 `ModSounds` 注册）
- `HANDOFF.md`：上游工作区/沙箱说明
