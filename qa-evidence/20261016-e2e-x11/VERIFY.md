# Independent re-verify 2026-10-16 13:05 UTC

Recomputed on-host with ImageMagick `convert` center crop 320x180 (not trusting prior flash.pass alone).

| File | Center mean RGB | Center white80 | Full white80 |
| --- | --- | --- | --- |
| baseline.png | 169.6, 198.9, 249.8 (blue world) | 0 | 356 |
| flash.png | 167.8, 164.0, 160.8 (grey-white overlay) | 11567 | 34172 |
| photo.png | 128.3, 104.0, 55.7 (sepia R>G>B) | 0 | 87023 (polaroid border, not center) |
| hud.png | same as photo.png | 0 | 87023 |

flash-grab.txt: frames 0-3 all `phase=shoot` (not IN_PHOTO). Frame 3 white 4092 -> 60283, grey mean 154,158,166, sepiaLike=false.
flash-called.txt: 1792154277396 (KeeperVisuals.flash() QA marker).
This is a white shutter overlay during shoot, not polaroid cream border (border only appears in photo/hud full-frame white80, center white80=0).
