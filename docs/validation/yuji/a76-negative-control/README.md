# PR29 a76 Habitat Red/Green Control

## Scope

Continue Draft PR #29 on `fix/fertilizer-grove-growth-20261014`, preserving the fetched `1eb386111dfd71067e67914291288cf5bfed3d95` and all earlier commits. This increment changes validation only, not production AI, collision shapes, fertilizer safeguards or existing assertions. It does not merge main, release, deploy or spend money.

The existing production `YujiHabitatGoal` already actively finds and navigates to reachable indoor/narrow places. The new control proves that the original movement tests reject removing that goal, instead of accepting a build failure, partial test execution or broken test arena as behavioral evidence.

## Reproduce

Use Java 17 and Python 3 in a clean checkout of the exact reviewed commit:

```bash
python3 -m unittest discover -s tools/yuji -p 'test_*.py' -v
bash ./gradlew -PyujiOnly runGameTestServer --no-daemon --max-workers=1 --console=plain
python3 tools/yuji/habitat_negative_control.py
```

The dedicated `habitat-negative-control` CI job first runs unmodified production code and verifies the successful GameTest log. It then creates a detached, disposable worktree at the same commit and removes exactly one production goal-registration statement. The primary checkout and all test assertions stay unchanged.

The negative verifier requires all original 83 cases: 55 `yuji`, 22 `yuji_city`, one `yuji_city_iter`, and five `yuji_habitat`. Gradle must exit 1 with a complete test summary. Both `liangshewalksinsideandstaysunderroof` and `xieyinwalksintobodywidthcorridor` must fail with their movement/retention assertion diagnostics. Additional failures are allowed only among the remaining three habitat cases and only with the corresponding behavior diagnostics. Compiler errors, partial runs, generic timeouts, collision/fixture corruption and unrelated failures cannot count as successful proof.

The runner's exit 0 means the expected behavioral red control was observed; it does not mean the mutant passed. Raw Gradle exit code, source SHA, named failures, original diagnostics and unchanged-source hash are saved in `build/yuji-habitat-negative-*/result.json`; the complete red log is saved beside it. CI uploads these and the green baseline log even on failure. On cancellation or timeout the process group is stopped before worktree cleanup.

The five regular habitat tests also retain sealed-room, too-low-corridor and destroyed-roof controls. The inherited `1eb3861` isolates their geometry and checks every placed air/stone block for unexpected writes; no previous collision or body-clearance assertion is weakened.

## Ten-Monster Evidence Boundary

Refer to [monster_cards.md](../../../../art/yuji_fodu/monster_cards.md), [the acceptance matrix](../../../yuji-acceptance.md) and [the detailed a74 comparison](../a74-takeover/README.md). The table describes implemented probes, not a claim that an unfinished current CI run passed.

| Monster | Implementation and executable evidence | Boundary |
| --- | --- | --- |
| Yuchi | Natural crawling contact with the closed door, then 180 additional consecutive ticks of contact-plane/no-overlap/no-crossing checks; same entity passes the open door. Standing checks remain. | Fixed door geometry, not all terrain/door combinations. |
| Shasheng | Existing natural double-hit timing and actual two-client betrayal-buffed damage. | Automated clients, not human avoidance/animation judgment. |
| Toudao | Natural theft of exactly 37 tagged items, carried stack sync, NBT round-trip, real-client kill and exact death return with doMobLoot=false. | Forty-tick conservation, not disk/server restart persistence. |
| Ekou | Natural chant hits at action ticks 15 and 32, exactly six damage each, total 12 and actual client health synchronization. | Controlled stationary source; human sound/animation unsigned. |
| Wangyu | Listener-specific reaction window, role-swapped audio/camera isolation and original regression assertions preserved. | Human hearing and arbitrary multiplayer interactions unsigned. |
| Liangshe | Active reachable indoor search/navigation, full body below roof, 80-tick stay, alternate-room and roof-loss cases. New removed-goal red control must fail real movement. | Fixed arenas; no claim of arbitrary terrain coverage or all neutral species. |
| Xieyin | Active narrow-space search/navigation with unchanged 1.8 x 6.3 body; 80-tick stay and too-low rejection. New removed-goal red control must fail movement. Real TCP crouch reduces III to II after 40 ticks. | Fixed corridors; cross-dimension/disconnect unsigned. |
| Qiyu | Existing ally buffs, regional summon limits and real-client sound probes preserved. | Not a global unlimited summon guarantee or long-duration human play. |
| Tanyu | Real-client attacks at raw damage 5.9 and 6 check accepted health loss, unchanged/exactly-one-removed layer and both clients' sync. Growth/load/navigation guards remain. | Controlled stationary monster; arbitrary combat combinations unsigned. |
| Chennu | Existing first-attacker grudge, two-client attacks, NBT and exactly-128/beyond-128 boundaries. | Cross-dimension and disconnect/reconnect unsigned. |

## Results And Remaining Acceptance

Fixed-SHA results, commands, exit codes and artifact URLs are recorded in the [PR evidence thread](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/29). A prior commit's green run is not current-HEAD proof. PR integration runs may include main's test modules; direct `workflow_dispatch` runs test the branch itself. Keep those results separate.

Human listening, human multiplayer, arbitrary complex terrain, cross-dimension/disconnect/reconnect, full disk/server restart, all neutral species and final original-requirement acceptance remain unsigned. Engineering review and automated clients do not replace those checks. Keep PR #29 Draft.
