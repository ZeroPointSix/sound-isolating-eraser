# a70: reachable habitats and live behavior evidence

## Scope and provenance

- Continue PR #29, branch `fix/fertilizer-grove-growth-20261014`, from `8fc710052e4cba60c0ef3e6aa85891ee23387656`.
- Requirements: [monster cards](../../../../art/yuji_fodu/monster_cards.md), sections 3-12, plus the superseding feedback recorded in [acceptance matrix](../../../yuji-acceptance.md).
- First implementation commit: `e8fc6456ff20f9e5ab4f7f4fe6b65f26b493657d`. Later fixes and final SHA/results are recorded in the PR evidence comment. Do not transfer an older green result to a newer SHA.
- No merge, release, production deployment, payment, or replacement of the existing ten monsters. Development version is 0.11.3.

## Production behavior

`YujiHabitatGoal` gives Liangshe and Xieyin a bounded search for reachable hiding places. Liangshe requires a solid non-leaf roof and walls on at least three sides. Xieyin requires opposite walls across a short corridor while retaining its 1.8 x 6.3 body. Both reject fluids, unsupported body corners, unloaded search regions, world-border violations and full-body collisions. The search uses real path reachability, a bounded path budget and rotating candidates so an unreachable nearby room cannot permanently starve reachable alternatives.

The goal takes movement priority over open-ground approach/retreat while a usable habitat exists; aura and melee target handling continue. A live target limits selection to the existing aura radius. Without suitable reachable terrain, the previous pursuit goal remains available. Roof removal invalidates the held destination. No navigation teleport, reduced collision body, disabled production gravity or fertilizer protection change is introduced.

## Executable verification

```bash
GRADLE_USER_HOME=/data/minecraft-models/gradle-cache bash ./gradlew -PyujiOnly runGameTestServer --offline --no-daemon --console=plain
python3 tools/yuji/verify_gametest_log.py <captured-log>
GRADLE_USER_HOME=/data/minecraft-models/gradle-cache bash ./gradlew build runGameTestServer --offline --no-daemon --console=plain
GRADLE_USER_HOME=/data/minecraft-models/gradle-cache bash ./gradlew -PyujiE2E writeYujiTestClasspath --offline --no-daemon --console=plain
YUJI_QA_ISOLATION=1 YUJI_QA_RECORD_AUDIO=1 YUJI_QA_PORT=25670 python3 tools/yuji/e2e.py
YUJI_QA_BOUNDARIES=1 python3 tools/yuji/e2e.py
```

The existing `Yuji Fodu validation` workflow runs two full GameTest rounds and the real-client gallery, slow-render regressions, two-client boundaries and isolation stages. `verify_gametest_log.py` now independently requires both the original `yuji` batch and all five `yuji_habitat` tests; empty or partial discovery is not success.

The five habitat GameTests use real spawned entities, ordinary AI, navigation, gravity and block collision: entry/holding under a roof; rejecting a sealed room; entering a body-width corridor; rejecting a low ceiling; and finding replacement shelter after the first roof is removed. Entity position is set only at initial spawn. The replacement test creates its alternative only after first arrival, avoiding an unspecified preference between two valid rooms. Whole-body bounds and height prevent an outside-wall or on-roof false positive.

The dedicated-server/two-full-client probe strengthens door closure to wait for natural arrival within 0.35 block of the actual door collision plane. It then observes 180 blocked ticks and requires the same crawling entity to cross after reopening. New habitat stages require natural movement, whole-body bounds, 80 ticks of holding, continued aura on a TCP player, both clients' synchronized entity checks and framebuffer evidence. Physical glass observation enclosures separate Xieyin habitat navigation from player pushing; the earlier camera/pull stages remain unchanged and the original full-body assertions stay strict.

The crouch probe starts with enthralled III and no prior cooldown, sends real client input, requires no early reduction and checks the first III-to-II reduction at 40 consecutive server-observed crouch ticks. The theft probe naturally steals a 37-item tagged stack from a connected player, checks both clients' synchronized loot, performs a real entity NBT round trip and kills the restored thief with a client attack. It checks exactly 37 tagged items across ground/player inventories for 40 ticks with `doMobLoot=false`. This is not a disk/server restart, manual play, or proof of client-side pickup synchronization.

## Ten-monster evidence boundaries

| Monster | Implemented behavior / executable coverage | Remaining acceptance boundary |
| --- | --- | --- |
| Yuchi / 愚痴 | Harmless pursuit, stand/crawl door obstruction; strengthened arrived-at-door, closed, reopened sequence | Human motion quality and arbitrary doors/terrain |
| Shasheng / 杀生 | Two natural slashes; gallery and two-client buffed damage/timing probes retained | Human animation judgment |
| Toudao / 偷盗 | Natural theft; added real-client count/tag synchronization, NBT restoration and death restitution chain | Full disk/server restart and client pickup synchronization |
| Ekou / 恶口 | Natural ranged chant, two damage times and animation bounds retained | Human listening and motion judgment |
| Wangyu / 妄语 | Recipient-specific mimic/weakness; player-switch and reaction-window fixes retained; two audio engines exercised | Human listening |
| Liangshe / 两舌 | Betrayal/Panda behavior retained; new active indoor navigation, inaccessible-room and roof-removal tests | Arbitrary building layouts and human multiplayer |
| Xieyin / 邪淫 | Pull, camera, source clearing retained; new active narrow-space navigation, low-ceiling rejection and timed crouch input | Arbitrary terrain, cameraTurn=false client rerun, human comfort |
| Qiyu / 绮语 | Four ally buffs, natural summon/replacement and cap probes retained | Human balancing/play experience |
| Tanyu / 贪欲 | Growth, navigation/reload/audio fixes retained; existing layer/peel threshold GameTests | Peel threshold remains component-level, not newly upgraded to client E2E |
| Chennu / 嗔怒 | First grudge, NBT restoration, second-player and 128-block boundaries retained in real two-client probe | Full dedicated-server restart with reconnect |

Boss and bone spikes do not count toward these ten. Existing tests remain enabled. Runtime pass/fail must be read from the evidence for the exact submitted SHA, not inferred from this implementation table.

## Evidence and follow-up

Local recovery: `kuoya-sgp-002:/data/yuji-fertilizer-a55`; raw a70 logs and exits: `/data/yuji-a70-evidence`. The first targeted run is retained as 82/83, exit 1; it failed the first-room assertion in the original two-valid-room replacement fixture. The corrected second local run completed 83/83, exit 0. These are targeted results, not a local full-suite pass.

The first submitted SHA `e8fc645` had a real CI failure, preserved in [run 38645057226](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38645057226): first full round 331/331, second 330/331, with `xieyinRejectsTooLowCorridorAndChoosesTallOne` stationary near `(1.908,1,1.933)`. A diagnostic-only follow-up `a2e3eba` records native path, entity tick count, chunk activation and target rather than guessing a root cause or weakening the assertion.

That first CI also produced genuine standing/crawling closed-door and reopening markers, both habitat server markers, and both Liangshe client markers, but the Xieyin witness-client body assertion failed. Crouch and theft stages were not reached and must not be counted as passed. [Official failed client artifact](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38645057226/artifacts/11932608668) retains the logs, screenshots and audio (ZIP SHA-256 `16a8cd8dd4e8311381f71b59e23576718eee286c84cadee15260e59037e4b4cf`). Subsequent results and the final submitted SHA are separately recorded in the PR evidence comment.

Next independent acceptance should inspect these source assertions and raw results, then run the commands above on the final HEAD. Use two actual human clients for gameplay isolation, listen to the separately recorded mixes, inspect crawl/slash/peel motion, and try closed doors, roof destruction and giant-body corridors in a representative save. None of those human tasks is signed off by automated GameTests or by a prior engineering approval.
