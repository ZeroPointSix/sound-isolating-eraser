# Entry Scene and Installed Forge Evidence

This is implementation evidence, not an independent review or full Notion acceptance.
No main merge, production deployment, payment or PR #18 change was performed by this continuation.

## Provenance

- Continuation: [Draft PR #34](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/34), branch `agent/eternal-night-entry-release-20261015`.
- PR #20 merged on 2026-10-11 15:05:55 UTC. PR #31 merged on 2026-10-15 01:59:37 UTC, before this takeover. The latter is the Slack notification titled "fix(city): shared white-door return / GameTest isolation". Neither is an open Draft.
- PR #31 final source: `56f2a071090488556a2ed345c0865244cb64b4b7`; its merge: `9def214f874a9ce07cf0abb36c4c07afe019d67a`.
- Latest tested source: `32d83a5a15f6eee60ac2a79de38ac9973a0731a0`. It additively incorporates the independently merged PR #35 exact sleep-injection descriptors from main `370b4f84f525d176de627db3ba83a73bd9029138`. This continuation never merged into main or rewrote existing history.
- Earlier tested source: `7e7ff72d7559094dc051a90fbcadb1b5e98712b5`; GitHub tested its merge ref `3771361ec40492bf469a373cec01fedb9822a666` with then-main `c2981a0d91f030fb799a347fe06073c3ca6e4fc5`.
- [City CI run 38680611668](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38680611668/job/116244229161) succeeded. [Original artifact 11948182799](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38680611668/artifacts/11948182799) ZIP SHA-256: `17ba06d328bf873937bfeb3dde1016cc0312ec159bbc921563734ad2d27fce86`.

## Final Results at 32d83a5

[Eternal Night CI 38694613593 / job 116288157052](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38694613593/job/116288157052) passed at this source. [Original artifact 11953938505](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38694613593/artifacts/11953938505) is 10,833,738 bytes, ZIP SHA-256 `c08d3a16cb569843b738e4ecf4883b01335b126f722ca956e61e69616a260199`. Selected original logs, screenshots and survey results are retained in `ci-32d83a5/`; JARs and the full artifact remain available from the CI artifact link.

| Gate | Actual result | Raw evidence |
| --- | --- | --- |
| JDK17 development-host run | 666397 assertions; 87 original runtime files / 6 loot tables; 22/22 night-only GameTests; build successful in 5m 38s | `host/after-upstream-sync.log` |
| CI full suite and JAR | 389/389 required tests, 666397 assertions, 87 original files | `ci-32d83a5/night-validation.log`, `night-jar-sha256.txt` |
| Independent black caption | Both actual clients still in overworld; entire 960x540 frame has 517882 dark pixels and 518 caption pixels; PNG visually checked | `ci-32d83a5/owner-entry-caption.png`, `observer-entry-caption.png`, caption `.pass` files |
| Actual two-client interaction | `NIGHT_TWO_CLIENT_E2E_PASS`: deep sleep, 100th unique door, shared white plane, distinct original beds, inventory retained, history cleared | `ci-32d83a5/night-native-validation.log`, role/server logs |
| Actual stop / restart / read-back | New server PID 3210 -> 3512; exact saved exit expiry 6966; owner 100 unique doors; both original player compounds, beds and inventories | `ci-32d83a5/server-checkpoint.pass`, `server-reload.pass`, server logs |
| Actual death / native respawn | `NIGHT_RESTART_AND_REAL_CLIENT_DEATH_PASS`; visible seven-diamond drop, zero diamonds retained, original bed and cleared history | `ci-32d83a5/server-death.pass`, `owner-death.pass`, death-screen/respawn PNGs |
| Actual installed SRG execution | `NIGHT_RELEASE_DOOR_LOCK_PASS production=true SRG=m_153165_ activeCloseBlocked=true expiredClosed=true inactiveCloseAllowed=true now=20 until=20` | `ci-32d83a5/release/server.log`, `result.log`, `jar-sha256.txt` |
| Building sample | Native entrances 12/12, collision-BFS floors **11/12, not accepted** | `ci-32d83a5/city-survey.json` |
| All repository checks | **11/12, not fully green**; one repeated full-suite validation failed | `failures/116288158001.log.gz` and linked job below |

Production JAR: `sound_isolating_eraser-0.12.1.jar`, SHA-256 `bdc3ff9eb8cbd089bfc8f49a2f00b9e89b6f357472b6ed6fec9c9e98175215c6`. Its class listing contains no release QA probe or native QA client. The separately loaded QA JAR hash is `971a2a3b6867ea7e135e030ae397e16df854d98a9d5cb39d2c59f7e0887ecd92`. The run uses existing GeckoLib/Curios only; maximum cold-entry tick gap was 1293 ms, not invariant 20 TPS.

The floor counterexample is `res_tenement` in INDUSTRIAL, origin `(-752,88,-752)`, entrance `(-752,89,-744)`: entrance walking succeeds but BFS reaches only 1 height level versus at least 4 expected. This is an unresolved recorded observation, not proof of native stair climbing or a fully isolated generator cause. Reproduce this coordinate/sample and inspect the BFS traversal versus the actual staircase before changing original art.

The red [validate job 116288158001](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38694613741/job/116288158001) reports `flareredtransfersvanillapursuitandreacquiresplayer failed! acceptance behavior finishes within 900 ticks after preparation` on the second full-suite run. The city workflow passed its full 389-test run at the same source. That contrast does not establish causality or dismiss the failure; neither the flare implementation nor its assertion was changed in this continuation. Preserve the workflow's repeated-run conditions when reproducing, rather than rerunning until green and discarding the failure.

Reproduction entry point: `.github/workflows/yuji-validation.yml`, first `bash ./gradlew build runGameTestServer --no-daemon --stacktrace`, then a new disposable test world and `bash ./gradlew runGameTestServer --no-daemon --stacktrace`, followed by both `tools/yuji/verify_gametest_log.py` and `tools/flare/verify_gametest_log.py` on the second log. Do not delete a shared or user world to reproduce this CI-only fixture. Further diagnosis must distinguish the pursuit timer/setup from a product regression; there is no verified minimal flare fix in this continuation.

The final archival commit changes documentation and evidence only. Source validation belongs to `32d83a5`, not to an unexecuted claim about a later commit.

## Results at 7e7ff72

| Gate | Actual result | Permanent evidence |
| --- | --- | --- |
| Rules and supplied art | 666397 assertions; 87 original runtime files, 6 loot tables | `ci-7e7ff72/night-validation.log` |
| Forge full suite | 388/388 required tests passed in the city workflow | `ci-7e7ff72/night-validation.log` |
| Black caption before teleport | Both actual native clients were still in the overworld; 517882 dark pixels and 518 caption pixels in each 960x540 frame | `ci-7e7ff72/owner-entry-caption.png`, `observer-entry-caption.png`, corresponding `.pass` files |
| Native sleep, unique door and shared return | `NIGHT_TWO_CLIENT_E2E_PASS`; both return to distinct original beds with inventory preserved and history cleared | `ci-7e7ff72/night-native-validation.log`, role and server logs |
| Real save/stop/new process/load | Server PID 3455 -> 3765, same world, exact exit expiry, both player compounds, owner 100 doors, original beds and inventories | `ci-7e7ff72/server-reload.pass`, server logs |
| Real client death | Native DeathScreen and Respawn-button input; seven visible dropped diamonds, zero retained, original bed, cleared history | `ci-7e7ff72/server-death.pass`, `owner-death.pass`, native log |
| Installed Forge SRG door lock | `production=true`, actual `m_153165_`, active close blocked, both leaves close after real ticks 0 -> 20, ordinary closing works after expiry | `ci-7e7ff72/release/server.log`, `result.log`, `jar-sha256.txt` |
| Building observation | 12/12 native entrances; 12/12 collision-BFS floor reachability in this sample | `ci-7e7ff72/city-survey.json`; not native stair-climbing or all-seed coverage |

The native scenario intentionally uses Creative players, 100% entry probability and 99 preloaded historical door coordinates. It does not prove statistical default probability or 100 manual clicks. The death phase uses actual death, dropped items and the client respawn UI. Maximum observed cold-entry tick gap was **3638 ms**, not invariant 20 TPS.

The installed-runtime probe is a separate, opt-in, reobfuscated QA mod. It loads the shipped mod JAR into an installed Forge 47.2.0 `forgeserver`, requires `FMLEnvironment.production`, verifies the SRG method exists and the MCP name does not, then executes actual `DoorBlock.setOpen`. It is not a mock, userdev-only check, byte/string search, production deployment, or a new production dependency.

Production JAR tested in that run: `sound_isolating_eraser-0.12.0.jar`, SHA-256 `c6439ffa0307b919bcd70f0f4a24bdb7867824a26c5afc9b8cfe508dcb431892`.

## Failures Retained

- Initial installed server crashed before the probe because `CityArrivalSleepMixin` only targeted MCP `stopSleeping`. The dual MCP/SRG target fixed startup. See `host/release-runtime-before-fix.log`.
- A probe-only fixture tried to shorten an existing exit with `open(now - 1)`, but production correctly keeps the later expiry. The corrected probe asserts that behavior and waits for real server ticks. See `host/release-runtime-fixture-failure-server.log` and the passing logs; this was not an expiry product fix.
- One loaded development-host run timed out before reaching the probe; its startup log is retained. The corrected real run later completed with `NIGHT_RELEASE_DOOR_LOCK_PASS`.
- The first development-host native attempt disconnected during login, before the scenario. The harness now waits for the actual server `Done` line, not merely an open port. CI real clients supply the passing evidence; the failed host run is not relabeled PASS.
- The first black-caption capture checked only the center and missed the sleep screen/HUD at the bottom. The final renderer covers both GUI and Screen post-render paths; the new assertion covers the whole framebuffer. The PNGs above were visually inspected.
- **All repository checks at 7e7ff72 were not green (9/12)**: [gravity fixture](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38680611725/job/116244188851) `hostilehoversthenfallsoutsidecylinder` was in flowing water; [flare preparation](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38680611700/job/116244058766) `flareredtransfersyujipursuitandreacquiresplayer` exceeded 1200 preparation ticks; [flare descent](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38680611675/job/116244058571) `flaregreenflightrandomlandingandloot` did not create its container. The city workflow passed all 388 tests at the same source. This alone does not establish causality or dismiss the red checks.

All four failed CI job logs are preserved losslessly as `failures/<job-id>.log.gz`. Use `gzip -dc` to inspect them. `SHA256SUMS` covers the committed evidence, including these compressed logs; raw logs retain their original whitespace.

## Commands

```bash
bash ./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1G \
  -PnightOnly checkNightRules verifyCityAssets build runGameTestServer
bash ./gradlew --no-daemon --max-workers=2 -PnightE2E -PnightReleaseQA \
  checkNightRules verifyCityAssets build nightReleaseQAJar writeNightTestClasspath runGameTestServer
python3 tools/night-e2e.py
python3 tools/night-release-door-lock.py \
  --server-dir build/night-release-server \
  --eula build/night-e2e/server/eula.txt \
  --mod-jar build/libs/sound_isolating_eraser-0.12.1.jar
sha256sum -c SHA256SUMS
```

`--server-dir` must be a fresh installed Forge 47.2.0 directory; the wrapper rejects an existing world or nonempty mods directory. `--eula` copies the existing, previously accepted test EULA. The wrapper records all four JAR hashes and uses loopback-only networking.

## Still Open

- Original art kit remains byte-identical: ZIP `9dab6be385742ebc8a1e7c2d371db5f8b5ef6b40a1062ee376ae99f915455d24`; only 4 glyphs and 35 structures (12 buildings, 4 plazas, 12 streets, 7 metro). Notion requests 32 glyphs and dedicated hospital/police/fire buildings. No substitute art was invented or borrowed.
- The 100th-door guarantee is implemented. `>=` versus `==` after that boundary remains a product choice; the new 101st-door assertion explicitly documents current `>=` behavior.
- R-1 remains once per valid sleep as Notion section 4.1 states, not once per night. R-2 spectator exclusion has no explicit source rule; unchanged.
- Sample floor reachability is not complete: the latest 32d83a5 run observed 11/12; 7e7ff72 observed 12/12; 697236d observed 11/12. The precise latest counterexample is recorded above. No universal or native stair-climbing acceptance is claimed.
- PR #18 and three-monster integration are outside this mission; monster density/attachment visuals are not fully accepted.
- Independent review and Notion functional acceptance remain required. Draft status is not approval to merge.
