# a26: Yuchi / Tanyu timing stability

This is an implementation-and-test handoff, not independent Review or human acceptance.
The only delivery remains [Draft PR #21](https://github.com/ZeroPointSix/sound-isolating-eraser/pull/21).
All older evidence directories are retained unchanged.

## Reproduced Failure

Production baseline: `0b628a8aef81077636ec641e6ff9929048f54191`.
The inherited diagnostic QA mod was extended to run only Yuchi and Tanyu and
pause the real render thread for 1600 ms when a new bite action serial arrived.
The Forge server, TCP connection, survival client, damage and sound pipeline were real.

- [Complete client log](baseline/client.log): bite serials 2 through 6 were observed, but no `tanyu.bite` sound was requested.
- [Complete server log](baseline/server.log): Tanyu reached 3 layers, acquired the connected player, and caused 5 damage events.
- [Failure marker](baseline/client.failed): actual skill playback was missing, with only ambient/death in both requested and active sets.
- [Original sound observations](baseline/audio-events-tanyu.log) and [actual mixed audio](baseline/client-mix.ogg).
- [Yuchi screenshot](baseline/yuchi.png) and [Tanyu screenshot](baseline/tanyu.png) are original client captures, not synthesized images.

The first command requested `YUJI_QA_CLIENT_FPS=5`. Minecraft rejected 5 as outside
its 10..260 option range and used its default; this run proves the injected stall
failure, **not** 5 FPS behavior. The harness now rejects invalid settings before launch.

## Cause and Change

Tanyu's bite was driven only by a client-local cue clock at age 12. During a stall,
incoming action serial changes reset that clock before age 12, while the server
still resolved the real hit. The bite is now broadcast from the production server
on action tick 12. Client-side duplicate sound is suppressed; damage, animation,
particles and the sound-on-miss behavior are retained.

The inherited Yuchi fix waits for actual rendering within a bounded window instead
of assuming 110 client ticks imply more than 10 rendered frames. Its strict frame,
bone-motion, foreground-pixel and framing assertions remain unchanged.

Short sounds also need observation on the audio thread: the new callback checks
the real OpenAL channel immediately after `Channel.play()`, then queues evidence
for the client thread. This records short playback even when a render stall lasts
longer than the 0.6-second bite clip. It does not emit a sound or substitute a fake
sound engine. Existing request/active logs and actual mixer recordings are retained.

## Repeatable Gates

```bash
export GRADLE_USER_HOME=/data/minecraft-models/gradle-cache
python3 tools/yuji/validate_assets.py
bash ./gradlew -PyujiE2E writeYujiTestClasspath build --offline --no-daemon --max-workers=1
YUJI_QA_PORT=25593 YUJI_QA_PROFILES=yuchi,tanyu YUJI_QA_CLIENT_FPS=10 \
  YUJI_QA_FRAME_DELAY_MS=700 YUJI_QA_STALL_BITE=1 YUJI_QA_RECORD_AUDIO=1 \
  python3 tools/yuji/e2e.py
```

Run the focused command twice in new worlds. It must report actual rendering,
unchanged AI/damage assertions and decoded, playing bite audio. `YUJI_RENDER_WAIT`
shows when the old frame deadline would have been premature. This focused pass
is never a substitute for the default eleven-profile/projectile run.

Two new `yuji` GameTests check bite sound exactly once at tick 12, damage on the
same tick when in reach, and no damage on a miss. They use the actual level sound
event; they do not constitute real-player E2E evidence. The existing full suite is
retained. CI repeats the full GameTests in a new world and the focused client
stress twice, alongside the original gallery, two-client boundaries and isolation
/ doors / climb / Panda scenarios. Exact current-HEAD results and run/artifact
URLs are posted in the PR evidence comment, not inferred from the workflow design.

## Independent Review / Human Acceptance Packet

- [Requirement matrix](../../../yuji-acceptance.md): all ten monsters, Shifotou and BoneSpike, with implementation choices distinguished from user-specified values.
- Compare the production change against `0b628a8`; all other monster mechanics and Boss statistics are unchanged in this stability patch.
- Check failure-to-pass playback logs, the two new tick-boundary tests, current-HEAD CI, repeated stress runs, and duplicate-audio suppression.
- Reuse preserved two-client target/bystander and terrain evidence, but use the new CI when asserting compatibility with this patch.
- Human listening is **pending**: review each actual mixer recording for clarity, timing, volume and unintended overlap. Non-silent audio and active channels alone cannot sign off perceived quality.
- Human multiplayer playtesting is **pending**: automated native clients verify concrete effects, not fairness, comfort, pacing or cooperation between people.
- Arbitrary terrain / all neutral species / all latency patterns are not exhaustively covered. The historical combined Tanyu food/damage failure is not assigned a root cause without its discriminating measurements; this baseline ate successfully.

No independent reviewer has signed off this patch here. No merge, release or production deployment is authorized.
