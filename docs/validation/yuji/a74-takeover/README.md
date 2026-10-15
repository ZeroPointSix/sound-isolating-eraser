# PR29 a74 takeover evidence

## Scope

Continue `fix/fertilizer-grove-growth-20261014` and Draft PR #29 from fetched remote HEAD `5ac2daa2b9643d95bd0ff219aea053e6abdb1a3e`. The supplied `8fc7100` is an ancestor, not the latest source. All intervening commits are retained. This takeover does not merge main, deploy, publish or perform financial operations.

The requirements remain [monster_cards.md](../../../../art/yuji_fodu/monster_cards.md), with the explicitly recorded F1-F8 amendments in [the acceptance matrix](../../../yuji-acceptance.md). An engineering review or green CI is not human acceptance of all ten monsters.

## Changes And Reused Implementations

| Monster | Requirement difference addressed or preserved | Current verification boundary |
| --- | --- | --- |
| Yuchi / 愚痴 | Preserve harmless pursuit and standing/crawling navigation. Strengthen crawling closed-door proof: remain crawling, no overlap/crossing, final front within 0.001 of the door collision plane, and at least one joint contact/collision observation. | Same naturally moving entity is blocked for 180 ticks, then crosses after the door opens. Standing assertions are retained. Not arbitrary-door/terrain coverage. |
| Shasheng / 杀生 | No production change; preserve natural two-hit attack and betrayal multiplier. | Existing actual-player 3.9 + 3.9 damage, six-tick spacing, total 7.8 remains in the expanded boundary sequence. |
| Toudao / 偷盗 | Reuse the inherited natural inventory theft, exact 37-item tagged stack, carried-item synchronization, NBT round-trip and death return. | Actual connected client kills the restored thief with doMobLoot=false; exact tagged quantity conserved for 40 ticks. NBT round-trip is not a disk/server restart. |
| Ekou / 恶口 | Preserve F5 natural ranged chant and its animation fix. Add complete-action timing and actual damage/client health checks. | Natural AI starts chant; two and only two accepted six-damage hits at zero-based action ticks 15/32; server health loss 12; real client health 388. No direct begin/resolve call. |
| Wangyu / 妄语 | Preserve target-specific listening/reaction-window fixes and leakage protection. | Existing two-client role swaps and actual sound-engine checks remain. Human listening is unsigned. |
| Liangshe / 两舌 | Reuse the already-added production YujiHabitatGoal: search reachable indoor candidates, navigate with normal AI/gravity, hold while suitable, invalidate lost shelter. | Five habitat GameTests include controls; real-client room approach, full-body clearance and 80-tick stay remain. Not only a roof predicate or manually placed final entity. |
| Xieyin / 邪淫 | Reuse active reachable narrow-space search without shrinking the 1.8 x 6.3 body; preserve partial crouch release. | Natural corridor approach/full-body clearance/80-tick stay; real TCP crouch reduces III to II at tick 40 with cooldown 120. Too-low/blocked controls remain. |
| Qiyu / 绮语 | No production change; preserve amended ally buffs, bounded repeat summons and sound attenuation. | Existing GameTests and full-client gallery remain; no claim of global unlimited summon behavior. |
| Tanyu / 贪欲 | Preserve growth, navigation, load and item protections. Add real-client raw-damage threshold probes. | Accepted 5.9 hit must retain layers; accepted 6 hit must remove exactly one layer and start peel. Match actual damage/health loss and require both clients' layer synchronization. Controlled stationary monster, not field combat. |
| Chennu / 嗔怒 | No production change; preserve first-attacker grudge and original boundaries. | Existing actual two-client attacks, NBT round-trip, exactly 128 / beyond 128 controls remain. Not disconnect or cross-dimension coverage. |

Boss contact, climbing, Panda recruitment, audio/camera isolation, fertilizer production and collision safeguards are not relaxed or removed.

## Reproduction

```bash
bash ./gradlew -PyujiOnly -PyujiE2E compileYujiTestJava writeYujiTestClasspath runGameTestServer --no-daemon --max-workers=1 --console=plain
python3 tools/yuji/verify_gametest_log.py <targeted-log>
bash ./gradlew build runGameTestServer --no-daemon --max-workers=1 --console=plain
python3 tools/yuji/verify_gametest_log.py <full-log>
YUJI_QA_BOUNDARIES=1 python3 tools/yuji/e2e.py
YUJI_QA_ISOLATION=1 YUJI_QA_RECORD_AUDIO=1 python3 tools/yuji/e2e.py
```

The E2E harness creates a fresh world, dedicated Forge server and two full Minecraft clients over loopback TCP. It records actual events, screenshots and synchronization. Test setup uses fixed geometry, controlled player attributes and stationary entities in boundary probes; habitat and door probes retain natural monster movement. It is automated real-client evidence, not human multiplayer play.

Action timing anchor: natural `begin("chant", 40, ...)` runs after that entity tick's action resolution. Consequently the next entity tick is action tick 0, and `hitEntityTick - observedStartEntityTick - 1` must equal 15 and 32. The test observes the entire 40-tick action and verifies no third hit.

## Evidence Status

The [PR #29 evidence thread](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/29) records current-run commands, exit codes, fixed source revision and CI/artifact links. This document records the verification design and inherited baseline only; do not treat it as a claim that uncompleted checks passed. Read the result for the actual tested HEAD rather than applying an older green run to a newer commit.

Inherited-source evidence, not a substitute for the new source's run:

- At `5ac2daa`, [Yuji validation run 38671488543](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38671488543) passed server and client jobs. The server log contains two complete 352/352 passes.
- The same inherited head was not all-green: [gravity run 38671488487](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38671488487) failed `hostilehoversthenfallsoutsidecylinder`. Preserve this red log and evaluate the current head separately; do not waive or weaken its assertion.

## Unsigned Acceptance

Human listening, human multiplayer gameplay, unrestricted complex terrain, cross-dimension and disconnect/reconnect behavior, complete disk/server restart persistence, all neutral mob species, visual/artistic judgment and final original-requirement sign-off remain unsigned. Existing approvals cover their reviewed increments only. PR #29 stays Draft.
