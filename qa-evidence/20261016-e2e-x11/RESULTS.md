# Keeper camera X11 client E2E

Host: `kuoya-sgp-002:/data2/keeper-camera-20261016`
Branch: `feat/keeper-camera-20261016` (private, not pushed)
Base commit: `c15b083595890b2ca7fb76e8337a418675b028ba`
Jar: `build/libs/sound_isolating_eraser-0.13.0-keeper.2.jar` (userdev classes used for E2E)
Display: Xvfb 1280x720; Minecraft 854x480; `import -window root` + ImageIO RGB
Port: 25578
Harness: `python3 -u tools/keeper-e2e.py`
Passed: True

## PASS/FAIL

| Check | Result | Evidence |
| --- | --- | --- |
| Enter game / shoot / enter photo | PASS | server.log join 12:37:50 at (0.5, -57.0, 0.5); KEEPER_QA_PHASE shoot then photo inPhoto=true; server.pass |
| Shutter flash (X11 GUI, not world FBO) | PASS | flash.pass; flash-called.txt (KeeperVisuals.flash invoked); flash-grab frame=3 white=60283 mean=154,158,166 lum=true; ImageMagick white80 356 -> 34172 |
| Sepia in photo world | PASS | sepia.pass rgb=141,118,71; photo-stats brown=209631/230400; ImageMagick photo meanRGB=67,59,43 (R>B) |
| Polaroid HUD 0x5A4632 | PASS | hud.pass pixels=340; hud.png (=photo.png overlay capture) |
| Server 14/14 GameTest | prior PASS | qa-evidence/20261016-a10/ (not rerun; client-only flash timing change) |

## Reproduce

```
cd /data2/keeper-camera-20261016
./gradlew compileJava compileKeeperTestJava writeKeeperTestClasspath genVSCodeRuns -PkeeperE2E --offline --no-daemon --max-workers=1
KEEPER_QA_TIMEOUT_SECONDS=1200 python3 -u tools/keeper-e2e.py
```

## Notes

- Attempt-4 flash.pass was a false positive (polaroid border jump). Tightened acceptFlash rejects IN_PHOTO and jump-only.
- Tick-based flashTicks=16 expired during client catch-up before import; production overlay is now 800ms wall-clock.
- Do not push / open PR / merge / touch pocket-watch.

## Independent re-verify (2026-10-16 13:05 UTC)

See VERIFY.md. Center crop 320x180: flash grey 167.8,164.0,160.8 white80=11567; photo sepia 128.3,104.0,55.7 white80=0. Polaroid border is full-frame only.
