# 90 · Non-functional requirements (`NFR`)

**Status:** Accepted 2026-10-31 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules).

**Scope.** Performance, scalability, reliability, compatibility, quality,
documentation and licensing. The S-10 scale scenario is defined in the
[overview](00-overview.md#reference-scenarios); reference hardware is D-40.

## Performance and scalability

#### NFR-001 · Tick budget
`Must` · `R1` · `Accepted` · Verify: Bench · Trace: S-10, D-40

In the S-10 scenario on reference hardware (D-40), atmos work on the server
thread shall average at most 2 ms per server tick, with a 99th percentile of at
most 5 ms. Atmos work is all server-thread time spent by `zas` and `zas_atmos`
together:
- snapshotting and applying results (CORE-047)
- devices (D-50) and capability transfers (API-026)
- ambient providers (ENV-015) and seal providers (API-028)
- hazard evaluation (LIFE-033) and airflow (FLOW-013)
- Ad Astra event answers (SPACE-018, from R2 with D-66)
- client sync (DATA-005)

Worker-thread time is NFR-005. This is measured on reference hardware before
each release; CI tracks regressions (NFR-034).

- **Rationale:** Decision: D-40 (reference hardware). The 2 ms budget is
  inferred: 4 % of a 50 ms tick, leaving room for other mods.
- **Verify:** Bench runs S-10 on reference hardware before each release; CI
  tracks regressions (NFR-034).

*2026-11-01: SPACE-018 moved to R2 with the Ad Astra adapter (D-66); its line applies from R2.*

#### NFR-005 · Worker keeps up
`Must` · `R1` · `Accepted` · Verify: Bench · Trace: S-10, D-30, CORE-045

In S-10, the off-thread simulation shall finish each atmos tick within 250 ms
on one worker thread (25 % of the default 1 s period).

- **Rationale:** Decision: D-30. A quarter of the period leaves headroom for
  bursts.
- **Verify:** Bench times worker ticks in S-10.

#### NFR-002 · No spikes
`Must` · `R1` · `Accepted` · Verify: Bench · Trace: CORE-047, GEO-022

No server tick shall spend more than the CORE-047 budget (default 10 ms) on
atmos server-thread work. Work beyond it is deferred to the next tick. Verified
by an explosion breaching S-10's 8,000-cell zone: no tick exceeds the budget,
and the zone still vents correctly.

- **Rationale:** Decision: D-30. Spikes are more noticeable to players than
  averages.
- **Verify:** Bench runs the explosion breach in S-10 and checks every tick's
  atmos time.

#### NFR-003 · Cost scales with activity
`Must` · `R0` · `Implemented` · Verify: Bench · Trace: CORE-051, CORE-054, GEO-011

Simulation cost shall scale with active zones, edges and changed cells, not
with world size, loaded chunks or idle zones. Adding 1,000 idle zones (all
edges asleep) adds at most 0.1 ms per server tick on average, and doubling the
loaded chunks that hold no zones changes atmos cost by less than 5 %.

- **Rationale:** Decision: D-13. Cost must follow activity, not world size.
- **Verify:** Bench adds 1,000 idle zones and doubles empty loaded chunks and
  compares costs.

*2026-10-31: Implemented. In the R0 bench 1,000 idle zones add 0.022 ms per
server tick on average (0.8 µs on the server thread, 0.021 ms of worker time
spread over the tick's 20 game ticks), and doubling the loaded chunks with no
zones changes atmos cost by +0.2 %, within noise ([benchmarks ·
scaling](../research/benchmarks.md#nfr-003-and-core-054-cost-follows-activity)). Idle
zones aren't quite free: each atmos tick still copies every zone's mixture and
rebuilds the published table, about 0.4 µs per zone.*

#### NFR-004 · Memory
`Should` · `R1` · `Accepted` · Verify: Bench · Trace: S-10

Atmos heap use shall average at most 256 bytes per zoned cell and 4 KiB per
zone (excluding its cells), and at most 64 MiB in total in S-10. Spikes S3 and
S5 may revise these by dated note.

- **Rationale:** Inferred: starting budgets that fit a 4 GB heap (D-40);
  spikes S3 and S5 may revise them.
- **Verify:** Bench measures heap use in S-10.

*2026-11-01: per-cell budget revised from 64 to 256 bytes after measuring it
([#78](https://github.com/west3436/zas-cubed/issues/78)); back to Draft for
review, as a material change. The R0 bench now counts the retained size of
every atmos structure in the headless S-10 station, part by part
([benchmarks](../research/benchmarks.md#membership-palette-check-2026-11-01)):
10.85 MiB in all, about 1 KiB per zone, and 222 bytes per zoned cell in the
parts that scale with cells. Of those 222 bytes, the zone graph's cells take
189 (each cell's object, its stored connections and the cell map), the section
mirror 22 and the membership palettes 11. The 64-byte figure assumed
membership was the per-cell cost; the palette layout meets it with room to
spare, but the graph's per-cell connections, which the R0 design keeps so
edge counts follow every change without rescanning (architecture §4.4), can't
fit it. 256 bytes keeps a margin over S-10's 222. The binding limit is the
total: at 256 bytes per cell, 64 MiB holds about 260,000 zoned cells, six
times S-10. Sparse zones cost more per cell (313 bytes in 27-cell rooms about
ten to a section), mostly the mirror's 8 KiB per mirrored section; S-10 is
what this budget is set against.*

*2026-11-01: Accepted by the owner with the 256-byte per-cell budget. The
per-zone and total limits are unchanged.*

## Reliability

#### NFR-010 · Integrity under fuzzing
`Must` · `R1` · `Implemented` · Verify: Unit (fuzz), GameTest · Trace: CORE-036, CORE-063, CORE-073, GEO-032

A fuzz run of 10,000 random operations (block edits, gas operations, chunk
unloads and reloads, save/load cycles) shall end with no unexplained change
reported by CORE-073, a clean GEO-032 audit, and no non-finite values
(CORE-063). CI runs 20 seeds nightly and 1 per PR.

- **Rationale:** Inferred: random sequences find conservation and geometry
  bugs that hand-written tests miss.
- **Verify:** The fuzz suite runs in CI as stated.

*2026-11-05: Still `Accepted`. The engine half is in:
`nfr_010_integrity_under_fuzzing` runs 10,000 operations with CORE-073 on, the
audit every 1,000 operations, checks for non-finite values, and its own total of
the gas, because CORE-073 explains whatever moves through storage and so can't
see gas that a reload makes or loses. `./gradlew build` runs it on seed 3, and
the "Integrity fuzz" workflow runs 20 seeds from the Actions tab. A seed takes
18 to 24 s. Of seeds 1 to 20, seventeen pass. Seed 2 fails the total of the gas
at operation 6238, and seeds 4 and 8 fail the audit at operations 6999 and 9999.
None of those three is read yet. Some seeds hit the limit on DATA-008, which the
fuzz excuses for exactly that event.

The fuzz's resets leave out a zone with another zone beside it, because OPS-013
and GEO-032 disagree there. The neighbour of a reset zone gains an ambient
connection even when the room left behind is sealed, as beside a dissolved zone
(CORE-076). `ops_013_a_neighbour_across_an_edge_now_opens_onto_the_ambient` pins
that and the audit reports it. Resets of other zones still run, 49 to 75 a seed,
with 26 to 43 left out. Whether the rule should change is the owner's call.

Left to do: those three seeds, a nightly schedule once all 20 pass, and the
GameTest half, which totals the gas in pipes.*

*2026-11-06: Implemented. Seeds 1 to 40 of both halves pass, with no seed left
out. `nfr_010_integrity_under_fuzzing` is the engine half, at 18 to 28 s a seed
locally and 24 to 44 s on CI. `nfr_010_integrity_under_fuzzing_with_pipes` is the
GameTest half, at about 3 s a seed locally and 3 to 16 s on CI: it runs in a real
level with pipes, vents and canisters, and its own total
covers zones, networks and devices, checks what a real save writes, and is
itself tested by `nfr_010_gas_made_in_a_pipe_fails_the_total`. Every PR runs seed
3 of each, in `./gradlew build` and `runGameTestServer`. The "Integrity fuzz"
workflow runs 20 seeds of each nightly, and any seeds from the Actions tab.

Three things are outside what it shows:

- **Resets.** They still leave out a zone with another zone beside it, as the
  note above says. Whether that rule should change is the owner's call.
- **The limit on DATA-008.** The engine half excuses the difference that limit
  makes, as DATA-008's notes describe, and across seeds 1 to 40 that was 78
  differences on 16 seeds. So it can't catch a real bug that makes or loses no
  more than the excuse's bound, in a zone that has just divided after an unload.
  Reverting each of #311's two fixes, #312, #314 and #315 still failed on the
  gas total, on the seed and at the operation that first caught it. With #314
  reverted, 2 of the 6 seeds run still passed, one of them with 3 more differences
  excused, so a seed passing doesn't rule such a bug out.
- **Pipes.** The GameTest half unloads a chunk with no block change queued or
  held and ticks straight after a reload, so neither that limit nor a dropped
  share comes up there. Its unloads leave block entities in place and its restarts
  are of the atmosphere only: `PipeSaveTests` covers really unloading and saving
  pipes. A save/load cycle in either half is a clean stop, not a crash (#304).*

#### NFR-011 · Server authority
`Must` · `R1` · `Implemented` · Verify: Inspect · Trace: A-03, CONT-074, LIFE-029

No client packet shall change atmos state, except server-validated device GUI
inputs (CONT-074) and the internals toggle (LIFE-029).

- **Rationale:** Inferred: clients can't be trusted on a multiplayer server
  (A-03).
- **Verify:** Inspect every client-to-server packet handler.

*2026-11-05: Implemented. [Server authority](../research/server-authority.md) has the
inspection, handler by handler: two ZAS³ payloads, the internals toggle and the tank
settings, and the device GUIs' clicks on the game's own menu packets. No client packet
carries a value: each sends an id the server checks, and the server works out and
clamps the result. The inspection found no way to change atmos state. It did find both
payloads taking presses from spectators, which the game's menu packets refuse, and
they now refuse them too (`nfr_011_only_a_living_player_toggles_their_own_internals`,
`nfr_011_only_a_living_player_sets_the_tank_they_hold`).*

#### NFR-012 · Exploit checklist
`Must` · `R1` · `Implemented` · Verify: Manual · Trace: CORE-036, CORE-073

Before each release, a manual exploit checklist shall pass with CORE-073
reporting no unexplained change: pipes across chunk borders, chunk unloads and
reloads during transfers, canister swapping, door cycling, and saving and
quitting mid-transfer. (Ambient harvesting and supply from paused zones are
sources by design, per CORE-036.)

- **Rationale:** Inferred: these are the classic duplication paths in
  Minecraft tech mods.
- **Verify:** Manual checklist run before each release.

*2026-11-05: Implemented. The checklist is in
[release-checklist.md](../release-checklist.md#exploit-checklist-nfr-012), and it
repeats on each release's commit. Four of its five items are GameTests
(`ExploitChecklistTests`, `PipeSaveTests`), and saving and quitting mid-transfer
has steps for a dedicated server's console. All of it passed on one commit, as the
checklist's results record: the gas came to 2023.420 mol before and 2023.417 and
2023.423 after two stops, with the checker and the audit clean. Writing it found
gas coming back from old saves, fixed in
[#304](https://github.com/west3436/zas-cubed/pull/304). CORE-073 doesn't see gas in
pipes, so these checks total the gas themselves, with `GasCensus`. A crash isn't on
the list, and can still bring back more gas than went in: the checklist has the
numbers.*

#### NFR-013 · Third-party fault isolation
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-55, D-04

An exception thrown by third-party code (ambient providers, seal providers,
code-defined reactions, hazard handlers, event listeners) shall be caught,
logged at most once per source per minute, and that source's contribution
skipped for that tick.

- **Rationale:** Decision: D-55.
- **Verify:** GameTests make each kind of third-party code throw and check it
  is skipped and logged at most once a minute.

*2026-11-05: Stays `Accepted`, because hazard handlers don't exist yet (LIFE-032,
API-033, [#35](https://github.com/west3436/zas-cubed/issues/35)), so the plan can't
make that kind throw. They're all that's left, and they must go through the same
guard when they're built.*

*Every other kind is guarded, with a GameTest that makes it throw over several atmos
ticks and counts the errors in the log, then moves the limit's clock on a minute and
counts one more. A kind's source is what the log names. An ambient provider is skipped
as if it had answered null, so the next source applies for that atmos tick
(`nfr_013_an_ambient_provider_that_throws_is_skipped_and_logged_once_a_minute`). A seal
provider's block has its own shape for that atmos tick, and its section goes back to
the worker so that it's asked again the next
(`nfr_013_a_seal_provider_that_throws_is_skipped_and_logged_once_a_minute`). A code
reaction changes nothing that step, and its source is its definition
(`nfr_013_a_code_reaction_that_throws_is_skipped_and_logged_once_a_minute`). An event
listener misses only that event, the others still hear it, and the bus logs nothing
(`nfr_013_an_event_listener_that_throws_is_skipped_and_logged_once_a_minute`).*

*The statement doesn't name gas handlers (API-021), but they're third-party code too, so
they're treated as included: a handler is skipped for the atmos tick whichever of its
calls throws, and no gas is lost
(`nfr_013_a_gas_handler_that_throws_is_skipped_and_logged_once_a_minute`). Its source is
its block. The limit is `ThirdPartyFaults` (API-062), whose `ThirdPartyFaultsTest` checks
the minute and that its record stays bounded. An item's or entity's `ILifeSupport` and
`IInternalSupply` (API-022) aren't named either, and aren't guarded yet.*

#### NFR-014 · Engine fault isolation
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-55, OPS-005

An exception inside the engine shall pause that dimension's simulation, as
OPS-005 does, with a logged error. The server keeps running.

- **Rationale:** Decision: D-55.
- **Verify:** A GameTest injects an engine exception and checks the dimension
  pauses and the server keeps running.

*2026-11-05: Implemented. The exception goes through
`DimensionAtmos.pauseSimulation`, by `zas:engine`, with an error and stack trace in
the log. Nothing of the failed tick is published, and a new, paused engine is built
at once from the last published table, so the dimension then behaves exactly as an
operator's pause leaves it and `/zas resume` ends it. The pause is saved as OPS-005's
is, so it lasts through a restart. An engine that throws while the dimension is
already paused stays stopped until `/zas resume`, which builds a new one. GameTests:
`EngineFaultTests.nfr_014_an_engine_exception_pauses_the_dimension`,
`nfr_014_the_pause_survives_a_restart` and
`nfr_014_a_fault_while_paused_waits_for_a_resume`, which inject the exception
through the engine's phase listener, so production code has no hook for it. Engine
side: `SchedulerTest.nfr_014_a_failed_tick_leaves_the_last_result_and_its_operations`.
The guards around the pipeline's work on the server thread, as opposed to the
worker's tick, have no test: nothing reaches them without a hook.*

#### NFR-015 · A server stop finishes
`Must` · `R1` · `Implemented` · Verify: Unit · Trace: DATA-011

A server stop shall finish, or say why it hasn't: while one is still running 2
minutes after it began, ZAS³ shall log each level's remaining chunk work and a
dump of every thread, every 2 minutes, at most 3 times. A stop that takes less
than 2 minutes logs nothing, and the reports can't keep the JVM alive.

- **Rationale:** Inferred: the game's final chunk loop logs nothing while it
  runs, so a stop stuck there leaves no trace of why
  ([#232](https://github.com/west3436/zas-cubed/issues/232)).
- **Verify:** Unit tests of `StopWatchdog`: a stop that finishes in time logs
  nothing, and a stuck one is reported with its threads, a limited number of
  times, from a daemon thread.

*2026-11-05: accepted by the owner, and Implemented: its tests pass. The hang it would
report, [#242](https://github.com/west3436/zas-cubed/issues/242), is left as it is by the owner's decision, so this
watchdog logs the cause if it comes back.*

## Compatibility

#### NFR-020 · Platform
`Must` · `R0` · `Implemented` · Verify: Inspect · Trace: D-09

zas-cubed shall run on NeoForge `[21.1.264,)` for Minecraft 1.21.1 under Java
21, on both dedicated servers and singleplayer. 21.1.264 is the floor because
it contains the attachment-sync privacy fix and the network hardening
([neoforge §1](../research/neoforge-1.21.1.md#1-toolchain)).

- **Rationale:** Decision: D-09; the 21.1.264 floor is from research
  ([neoforge §1](../research/neoforge-1.21.1.md#1-toolchain)).
- **Verify:** Inspect the mod metadata; CI builds and tests against the floor.

*2026-11-03: Still Accepted. The mod metadata gives `[21.1.264,)` and CI uses
Java 21, but CI builds and tests against 21.1.265 (`neo_version`), not the
floor the Verify plan asks for ([#69](https://github.com/west3436/zas-cubed/issues/69)).*

*2026-11-05: Implemented. `neo_version` in `gradle.properties` is now 21.1.264, and it
is also each mod's minimum in `neoforge.mods.toml`, so CI's build, unit tests and
GameTests all run on the floor, and the two can't drift apart. This replaces
[neoforge §1](../research/neoforge-1.21.1.md#1-toolchain)'s "build against 21.1.265".
21.1.265 added only context to enchanted loot events
([NeoForge #3690](https://github.com/neoforged/NeoForge/pull/3690)), which ZAS³ doesn't
use.*

#### NFR-023 · Dedicated server safety
`Must` · `R0` · `Implemented` · Verify: GameTest · Depends: NFR-020

A dedicated server shall start with no client classes loaded. CI boots one as
a GameTest.

- **Rationale:** Inferred: client classes on a dedicated server crash it;
  booting one in CI catches it ([neoforge
  §13](../research/neoforge-1.21.1.md#13-testing--ci)).
- **Verify:** A CI GameTest boots a dedicated server.

*2026-11-03: Implemented. `nfr_023_zas_loads_on_dedicated_server` and
`nfr_023_zas_atmos_loads_on_dedicated_server` check they run on a dedicated
server, in CI's "GameTests (dedicated server)" step.*

#### NFR-021 · Injection-only mixins
`Must` · `R1` · `Implemented` · Verify: Inspect

Mixins shall be minimal and injection-based, with no `@Overwrite`.

- **Rationale:** Inferred: overwrites break other mods; injections coexist
  ([neoforge §14](../research/neoforge-1.21.1.md#14-mixins--ats)).
- **Verify:** Inspect the mixin configuration.

*2026-11-05: Implemented. `zas.mixins.json` lists five mixins with six hooks: three
`@Inject`, one `@ModifyReturnValue`, one `@WrapMethod` and one `@WrapOperation`. None
uses `@Overwrite`.*

#### NFR-024 · Documented mixins
`Must` · `R1` · `Implemented` · Verify: Inspect

Each mixin shall be documented with its purpose and the requirement it serves.

- **Rationale:** Inferred: mixins are the most fragile code across updates, so
  each needs a recorded reason.
- **Verify:** Inspect each mixin's documentation.

*2026-11-05: Implemented. Each of the five classes in `zas.mixins.json`, the only
mixin config in the repository, has a class javadoc giving its purpose and
requirements, and each of its six hooks has a row in [architecture
§7](../architecture.md#7-mixin-inventory) naming the same requirements. The
inspection found the table and the javadocs disagreeing on M1, M3, M4, M5 and M6, and
a row for M7, which was never built; all are corrected. A new mixin adds a javadoc
and a row.*

#### NFR-025 · Compatibility matrix
`Must` · `R1` · `Accepted` · Verify: Manual, GameTest

Before each release, with ModernFix, FerriteCore, Lithium and Sodium (NeoForge
builds) installed, the game shall launch and the S-01 to S-04
smoke tests shall pass.

- **Rationale:** Inferred: these performance mods are in most modpacks.
- **Verify:** Manual and GameTest smoke tests with the matrix installed before
  each release.

*2026-11-01: Ad Astra left the R1 matrix with the adapter (D-66). It joins the matrix again when the adapter ships.*

*2026-11-05: Stays Accepted. `-Pcompat.matrix=true` runs the GameTests with the matrix's
server mods, by hand or from the nightly Compatibility matrix workflow, and never on a
PR. Run once headless: all 692 GameTests pass on a dedicated server, S-01 to S-04 among
them, with ModernFix 5.27.27, FerriteCore 7.0.3 and Lithium 0.15.4. Sodium 0.8.13 can't be
tested there: it isn't loaded on a server, and all its mixins are client mixins. Left:
the client launch with all four mods, which has to be by hand and hasn't been run. It
repeats with the headless run before each release:
[release checklist](../release-checklist.md#compatibility-matrix-nfr-025).*

#### NFR-022 · Optional compat isolation
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: A-01

Compat code shall load only when its target mod is present. zas-cubed shall
run fully without any optional mod installed.

- **Rationale:** Decision: A-01 and D-11.
- **Verify:** A GameTest runs without any optional mod; Inspect the compat
  entry points.

*2026-11-05: Implemented. `nfr_022_runs_with_no_optional_mod` checks that a GameTest
server loads only the game, NeoForge and our own mods, so every GameTest in CI runs
with no optional mod. The inspection found no compat entry point yet: `compat-adastra`
holds only a `package-info` until the adapter comes in R2 (D-66), and nothing refers
to it. No main source asks `ModList` or calls `Class.forName`. The mods' metadata lists
only required dependencies, and `zas.mixins.json` is the one mixin config, with no
`requiredMods`, and targets only game classes. The adapter, when it comes, needs this
inspected again.*

## Quality and process

#### NFR-030 · Engine coverage
`Should` · `R1` · `Implemented` · Verify: Inspect

The `:core` engine module shall have at least 80 % line coverage from unit
tests.

- **Rationale:** Inferred: the engine carries the conservation and physics
  guarantees, so it needs the most test coverage.
- **Verify:** Inspect the coverage report in CI.

*2026-11-05: Implemented, at 91.5 % (6,760 of 7,389 lines). `:core` here is the
`engine` project today. CI's Engine coverage step puts each run's figure, by package,
on the run's summary page, with the full JaCoCo report as an artifact, and warns under
80 %. `./gradlew :engine:jacocoTestReport` writes the same report locally.*

#### NFR-032 · Scenario tests
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: README rule 5

Every R1 scenario (S-xx) shall have a GameTest or a scripted manual test.

- **Rationale:** Inferred: README rule 5; scenarios are how the requirements
  are proven end to end.
- **Verify:** Inspect the test list against S-01 to S-13.

#### NFR-033 · CI on every PR
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: API-043, API-048

CI shall run the build, unit tests, GameTests and the API diff (API-043) on
every PR.

- **Rationale:** Decision: D-04 (library standards).
- **Verify:** Inspect the CI configuration.

*2026-11-03: Still Accepted. CI runs the build, unit tests and GameTests on
every PR. Its API diff runs too, but compares nothing until API-043's baseline
is set ([#61](https://github.com/west3436/zas-cubed/issues/61)).*

#### NFR-034 · Benchmark regression check
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-40, NFR-001

CI shall run a headless S-10 engine benchmark and fail on a regression of more
than 10 % against the recorded baseline.

- **Rationale:** Decision: D-40. CI compares against a recorded baseline, not
  absolute numbers; the 10 % threshold is inferred.
- **Verify:** Inspect the CI configuration; a deliberate slowdown fails the
  check.

#### NFR-031 · Documentation
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-04, D-10

Releases shall ship with the following, as Markdown in `docs/` linked from each
release:
- player docs (mechanics and content)
- pack and datapack docs (formats, defaults)
- developer docs (API guide, javadoc)
- a changelog

- **Rationale:** Decision: D-04 and D-10.
- **Verify:** Inspect the release checklist and docs folder.

## Licensing and provenance

#### NFR-040 · Clean-room code
`Must` · `R0` · `Accepted` · Verify: Inspect · Trace: D-01

No vgstation13 *code* shall enter the repository, whether copied or
translated. Research notes describe vg behaviour in prose only. Assets are
governed by NFR-041.

- **Rationale:** Decision: D-01.
- **Verify:** Inspect: PR reviews against the clean-room rule (NFR-043).

#### NFR-043 · Clean-room rule in contributions
`Must` · `R0` · `Implemented` · Verify: Inspect · Trace: D-01

`CONTRIBUTING.md` shall state the clean-room rule, and the PR template shall
carry a checkbox confirming it.

- **Rationale:** Decision: D-01.
- **Verify:** Inspect `CONTRIBUTING.md` and the PR template.

*2026-11-03: Implemented. `CONTRIBUTING.md` has "The clean-room rule", and the
first item of the PR template's checklist confirms it.*

#### NFR-041 · Asset sources
`Must` · `R1` · `Implemented` · Verify: Inspect · Trace: D-26

Assets derived from vgstation13 sprites shall come only from vg's CC BY-SA
3.0 US assets, never from the CC BY-NC-SA `goon/` folder. Any sprite whose
origin is unclear is left out.

- **Rationale:** Decision: D-26.
- **Verify:** Inspect the CREDITS entries for source paths.

*2026-11-05: This holds for vg's sounds as well as its sprites, and
`scripts/check_credits.py` refuses a `goon/` source for either. Which sounds were
left out for an unclear origin, and why, is in [vg
sounds](../research/vg-sounds.md#provenance).*

*2026-11-05: Implemented. All 214 vg entries in `CREDITS` have sources under vg's
`icons/` or `sound/`, none under `goon/`. One gap is open: the owner says
[`misc/fire_alarm`](../research/vg-sounds.md#provenance) came from Freesound under a
suitable license, but the original sound and its author aren't recorded. If that
license asks for attribution, the entry needs the name before release
([#72](https://github.com/west3436/zas-cubed/issues/72)).*

#### NFR-044 · Asset license
`Must` · `R1` · `Implemented` · Verify: Inspect · Trace: D-39, D-56

All assets, derived or original, shall be licensed CC BY-SA 4.0 International
in a separate `LICENSE-ASSETS` file. Code stays MIT.

- **Rationale:** Decision: D-39 and D-56.
- **Verify:** Inspect `LICENSE-ASSETS`.

*2026-11-05: Implemented. `LICENSE-ASSETS` puts every file under
`src/*/resources/assets/` under CC BY-SA 4.0, with its full text, and `LICENSE` keeps
the code MIT.*

#### NFR-045 · Asset provenance
`Must` · `R1` · `Implemented` · Verify: Inspect · Trace: D-26, D-56, D-62

Every file under `assets/` shall have a `CREDITS` entry giving its origin:
"original"; its vg source file, icon state and commit, with attribution to
the vgstation13 contributors; or, for any other source, where it came from,
its license and its attribution. A CI script checks this.

- **Rationale:** Decision: D-26, D-56 and D-62.
- **Verify:** The CI script fails the build on an asset without a CREDITS
  entry.
- **Changed 2026-10-31 (D-62):** assets may also come from sources other
  than vgstation13, under a license that allows adapting them under CC BY-SA
  4.0. The entry records the source, license and attribution.

*2026-11-05: A vg sound's entry gives its path under vg's `sound/` and the commit,
with no icon state, and says how the file was adapted. The script checks that a
sound has no state, that an icon has one, and that a file under `sounds/` draws on
vg's `sound/`.*

*2026-11-05: Implemented. CI's build job runs `scripts/check_credits.py`. All 555
asset files have an entry, and an uncredited probe file made the script exit 1.*

#### NFR-046 · No NonCommercial assets
`Must` · `R1` · `Implemented` · Verify: Inspect · Trace: D-26

No NonCommercial asset shall ship in any jar. RE:SS3D assets (CC BY-NC-SA 4.0)
and Stationeers (proprietary) are reference and inspiration only.

- **Rationale:** Decision: D-26.
- **Verify:** Inspect the jar contents against CREDITS.

*2026-11-05: Implemented. Inspected the `zas` and `zas_atmos` jars, nested API jar
included. All 549 asset files have a `CREDITS` entry, each `original` or
`vgstation13`, with no `goon/` source, and no image or sound sits outside `assets/`.
No entry uses another source, and the script refuses a NonCommercial license on any
that does.*

#### NFR-042 · Distribution
`Should` · `R1` · `Accepted` · Verify: Inspect · Trace: D-18

Mod jars (`zas` and `zas_atmos`) shall be published to Modrinth and
CurseForge. Publishing the API to Maven Central is API-008.

- **Rationale:** Decision: D-18.
- **Verify:** Inspect the release pages.
