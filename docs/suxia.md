# Suxia and the pocket watch

## Content

- `sound_isolating_eraser:suxia_pocket_watch`: non-stackable, epic, fireproof owner-bound watch.
- `sound_isolating_eraser:suxia_summon`: temporary owner-protecting tame entity, never naturally spawned.
- `sound_isolating_eraser:suxia`: blind hostile entity, spawn egg/command/structure only.
- Watch and hostile spawn egg appear together in the existing Qijie tab. The watch's second tooltip line is the gold precious-memory category.

Original artwork and the supplied 31-cuboid Java model/IK animations are retained. Both entities use the same two-sided renderer and aggressive emissive eyes. The animation interface lives in common code to keep dedicated servers free of client-only class loading.

## Lifecycle

The watch binds on first server-side use. A successful summon lasts 1,200 ticks. Recall, expiry, owner logout/death/dimension change, or failed long-distance recovery starts a 3,600-tick cooldown. Summon death instead starts 12,000 ticks and gives its owner nausea for 100 ticks and weakness for 200 ticks. Cooldowns use saved overworld game time, not client time or dimension-local clocks.

The world owns a `SavedData` ledger keyed by watch UUID. Item NBT mirrors it for rendering and tooltips; a dropped or container-stored watch cannot lose its death penalty. A player may have only one active summon across watches. Stale/unloaded entities are reconciled with a 40-tick load grace, and orphaned entities discard themselves. Spawn attempts require loaded, supported, dry, collision-free positions, first standing and then crawling; failed placement does not start cooldown.

## Perception

Hostile Suxia uses a registered vanilla vibration listener with the Warden event tag, 16-block hearing, native sneaking/wool filtering, and the existing eraser-wall acoustic ray. Blood scent separately detects eligible prey within 24 blocks if below half health or damaged within the last 200 ticks. Eraser walls do not block scent. Vision alone never acquires or refreshes a target.

Projectile impacts lure Suxia to the landing location rather than revealing a silent shooter's position. Unidentified sound is investigated and searched for 100 ticks on arrival; unreachable sound investigation is bounded. A target is forgotten after 160 ticks without renewed perception. After 600 quiet ticks Suxia plays dead; three-block proximity wakes it and arms one 1.5x strike for 60 ticks.

## Validation

Run on the development host with Java 17 and Forge 47.2.0:

```sh
./gradlew check build
./gradlew runGameTestServer -PsuxiaOnly
./gradlew -PsuxiaE2E writeSuxiaTestClasspath
python3 tools/suxia/e2e.py
```

The focused server tests cover properties, poses, placement, binding, recall, duplicate/foreign watch rejection, saved state, death while the watch is absent, login/logout/dimension events, friendly-target safety, live owner protection, scent boundaries, actual vibration delivery, sneaking/wool, eraser-versus-scent, sleep/wake, blindness, and hostile alliances. Real-client validation uses an isolated loopback-only dedicated server, live item-use packets, synchronized models/poses, framebuffer screenshots, and actual disconnect/rejoin. Its QA mod is opt-in and excluded from the production jar.

No natural spawn rules, existing eraser isolated-mob tags, production world, release workflow, or main-branch merge are changed by this feature.
