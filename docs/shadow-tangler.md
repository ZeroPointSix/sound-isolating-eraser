# Shadow Tangler / 缠影子

Minecraft 1.20.1, Forge 47.2.0 and GeckoLib 4.4.9 are required on the client and server. Existing Curios requirements still apply. This feature is in mod version 0.8.0.

## Spawn and assets

- Entity: `sound_isolating_eraser:shadow_tangler`.
- Egg: `sound_isolating_eraser:shadow_tangler_spawn_egg`, in the Spawn Eggs creative tab.
- Command: `/summon sound_isolating_eraser:shadow_tangler ~ ~ ~`.
- No biome spawn rules are registered. The mob is available through its egg, commands and normal spawner configuration only.
- The supplied geometry, two 64x64 textures, 16x16 egg and seven animations are retained. Resource paths use the repository namespace rather than the source pack's `qihai` namespace.

## Behavior

Base attributes: 20 health, 3 attack damage, 0 armor, 0.40 movement speed, 35 follow range and 5 XP. The bounding box is 0.6 x 2.0 blocks. New entities cannot move horizontally or attack during their first 24 ticks; saved spawn age prevents the animation replaying after reload.

| Light | Speed multiplier | Incoming damage | Response |
| --- | --- | --- | --- |
| 0-6 | 1.00 | 0.50 | Hunt players, villagers/traders and iron golems |
| 7-10 | 0.60 | 1.00 | Retreat after 60 consecutive ticks |
| 11-15 | 0.25 | 2.00 | Clear combat target, flee immediately and take 2 light damage per 20 ticks |

Brightness is computed on the server from local block and time/weather-adjusted sky light. The visual tier is synchronized to clients. Light damage is not multiplied a second time and does not overwrite another hit's invulnerability bookkeeping. External damage scaling includes the knife's event-added and armor-bypassing segments, while preserving normal armor, resistance and absorption behavior.

Fleeing collects dark standing positions within 16 blocks, validates collision and path reachability, and supports translucent/partial-block flooring. A single bounded vanilla multi-target search chooses a reachable refuge without wasting separate path searches on nearer sealed spaces. Dim paths have a cost penalty. Bright nodes are blocked while hunting, but traversable with a penalty during retreat so a mob can leave a bright area. Searches run at most once per second. Combat and wandering resume only after 40 ticks spent in darkness.

Melee strikes have a 5-tick windup and a minimum 20-tick interval. Successful hits apply Slowness II for 20 ticks. Bright death shatters immediately and drops 0-2 black stained glass panes. Other deaths play the one-second death animation and drop 0-2 ink sacs. Looting may add up to its level. The base XP reward is issued once. There is no equipment pickup, zombie conversion or natural spawning.

## Reproduce validation

```sh
bash ./gradlew --no-daemon --max-workers=2 build runGameTestServer
bash ./gradlew --no-daemon --max-workers=2 -PshadowE2E writeShadowTestClasspath
python3 tools/shadow-e2e.py
```

The native-client run needs Xvfb, xdotool and Mesa software rendering. It binds a localhost-only dedicated server on port 25581 (override with `SHADOW_QA_PORT`), launches two real Forge clients, uses native mouse/keyboard input for an egg and knife, captures both light-state views and performs a real server save/restart. Evidence is placed in `build/shadow-e2e`. Processes are stopped in a `finally` block; older worlds/evidence are archived rather than silently reused. Test-only sources and structures are excluded from the production jar.

The GitHub `Shadow Tangler Validation` workflow runs the same checks and uploads evidence for seven days. A successful compile alone is not proof that GameTests or native-client checks passed; use their explicit completion results.
