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
- Ad Astra event answers (SPACE-018)
- client sync (DATA-005)

Worker-thread time is NFR-005. This is measured on reference hardware before
each release; CI tracks regressions (NFR-034).

- **Rationale:** Decision: D-40 (reference hardware). The 2 ms budget is
  inferred: 4 % of a 50 ms tick, leaving room for other mods.
- **Verify:** Bench runs S-10 on reference hardware before each release; CI
  tracks regressions (NFR-034).

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
`Must` · `R0` · `Accepted` · Verify: Bench · Trace: CORE-051, CORE-054, GEO-011

Simulation cost shall scale with active zones, edges and changed cells, not
with world size, loaded chunks or idle zones. Adding 1,000 idle zones (all
edges asleep) adds at most 0.1 ms per server tick on average, and doubling the
loaded chunks that hold no zones changes atmos cost by less than 5 %.

- **Rationale:** Decision: D-13. Cost must follow activity, not world size.
- **Verify:** Bench adds 1,000 idle zones and doubles empty loaded chunks and
  compares costs.

#### NFR-004 · Memory
`Should` · `R1` · `Accepted` · Verify: Bench · Trace: S-10

Atmos heap use shall average at most 64 bytes per zoned cell and 4 KiB per zone
(excluding its cells), and at most 64 MiB in total in S-10. Spikes S3 and S5
may revise these by dated note.

- **Rationale:** Inferred: starting budgets that fit a 4 GB heap (D-40);
  spikes S3 and S5 may revise them.
- **Verify:** Bench measures heap use in S-10.

## Reliability

#### NFR-010 · Integrity under fuzzing
`Must` · `R1` · `Accepted` · Verify: Unit (fuzz), GameTest · Trace: CORE-036, CORE-063, CORE-073, GEO-032

A fuzz run of 10,000 random operations (block edits, gas operations, chunk
unloads and reloads, save/load cycles) shall end with no unexplained change
reported by CORE-073, a clean GEO-032 audit, and no non-finite values
(CORE-063). CI runs 20 seeds nightly and 1 per PR.

- **Rationale:** Inferred: random sequences find conservation and geometry
  bugs that hand-written tests miss.
- **Verify:** The fuzz suite runs in CI as stated.

#### NFR-011 · Server authority
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: A-03, CONT-074, LIFE-029

No client packet shall change atmos state, except server-validated device GUI
inputs (CONT-074) and the internals toggle (LIFE-029).

- **Rationale:** Inferred: clients can't be trusted on a multiplayer server
  (A-03).
- **Verify:** Inspect every client-to-server packet handler.

#### NFR-012 · Exploit checklist
`Must` · `R1` · `Accepted` · Verify: Manual · Trace: CORE-036, CORE-073

Before each release, a manual exploit checklist shall pass with CORE-073
reporting no unexplained change: pipes across chunk borders, chunk unloads and
reloads during transfers, canister swapping, door cycling, and saving and
quitting mid-transfer. (Ambient harvesting and supply from paused zones are
sources by design, per CORE-036.)

- **Rationale:** Inferred: these are the classic duplication paths in
  Minecraft tech mods.
- **Verify:** Manual checklist run before each release.

#### NFR-013 · Third-party fault isolation
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-55, D-04

An exception thrown by third-party code (ambient providers, seal providers,
code-defined reactions, hazard handlers, event listeners) shall be caught,
logged at most once per source per minute, and that source's contribution
skipped for that tick.

- **Rationale:** Decision: D-55.
- **Verify:** GameTests make each kind of third-party code throw and check it
  is skipped and logged at most once a minute.

#### NFR-014 · Engine fault isolation
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-55, OPS-005

An exception inside the engine shall pause that dimension's simulation, as
OPS-005 does, with a logged error. The server keeps running.

- **Rationale:** Decision: D-55.
- **Verify:** A GameTest injects an engine exception and checks the dimension
  pauses and the server keeps running.

## Compatibility

#### NFR-020 · Platform
`Must` · `R0` · `Accepted` · Verify: Inspect · Trace: D-09

zas-cubed shall run on NeoForge `[21.1.264,)` for Minecraft 1.21.1 under Java
21, on both dedicated servers and singleplayer. 21.1.264 is the floor because
it contains the attachment-sync privacy fix and the network hardening
([neoforge §1](../research/neoforge-1.21.1.md#1-toolchain)).

- **Rationale:** Decision: D-09; the 21.1.264 floor is from research
  ([neoforge §1](../research/neoforge-1.21.1.md#1-toolchain)).
- **Verify:** Inspect the mod metadata; CI builds and tests against the floor.

#### NFR-023 · Dedicated server safety
`Must` · `R0` · `Accepted` · Verify: GameTest · Depends: NFR-020

A dedicated server shall start with no client classes loaded. CI boots one as
a GameTest.

- **Rationale:** Inferred: client classes on a dedicated server crash it;
  booting one in CI catches it ([neoforge
  §13](../research/neoforge-1.21.1.md#13-testing--ci)).
- **Verify:** A CI GameTest boots a dedicated server.

#### NFR-021 · Injection-only mixins
`Must` · `R1` · `Accepted` · Verify: Inspect

Mixins shall be minimal and injection-based, with no `@Overwrite`.

- **Rationale:** Inferred: overwrites break other mods; injections coexist
  ([neoforge §14](../research/neoforge-1.21.1.md#14-mixins--ats)).
- **Verify:** Inspect the mixin configuration.

#### NFR-024 · Documented mixins
`Must` · `R1` · `Accepted` · Verify: Inspect

Each mixin shall be documented with its purpose and the requirement it serves.

- **Rationale:** Inferred: mixins are the most fragile code across updates, so
  each needs a recorded reason.
- **Verify:** Inspect each mixin's documentation.

#### NFR-025 · Compatibility matrix
`Must` · `R1` · `Accepted` · Verify: Manual, GameTest

Before each release, with ModernFix, FerriteCore, Lithium and Sodium (NeoForge
builds) and Ad Astra installed, the game shall launch and the S-01 to S-04
smoke tests shall pass.

- **Rationale:** Inferred: these performance mods are in most modpacks, and Ad
  Astra is the R1 compat target (D-29).
- **Verify:** Manual and GameTest smoke tests with the matrix installed before
  each release.

#### NFR-022 · Optional compat isolation
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: A-01

Compat code shall load only when its target mod is present. zas-cubed shall
run fully without any optional mod installed.

- **Rationale:** Decision: A-01 and D-11.
- **Verify:** A GameTest runs without any optional mod; Inspect the compat
  entry points.

## Quality and process

#### NFR-030 · Engine coverage
`Should` · `R1` · `Accepted` · Verify: Inspect

The `:core` engine module shall have at least 80 % line coverage from unit
tests.

- **Rationale:** Inferred: the engine carries the conservation and physics
  guarantees, so it needs the most test coverage.
- **Verify:** Inspect the coverage report in CI.

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
`Must` · `R0` · `Accepted` · Verify: Inspect · Trace: D-01

`CONTRIBUTING.md` shall state the clean-room rule, and the PR template shall
carry a checkbox confirming it.

- **Rationale:** Decision: D-01.
- **Verify:** Inspect `CONTRIBUTING.md` and the PR template.

#### NFR-041 · Asset sources
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-26

Assets derived from vgstation13 sprites shall come only from vg's CC BY-SA
3.0 US assets, never from the CC BY-NC-SA `goon/` folder. Any sprite whose
origin is unclear is left out.

- **Rationale:** Decision: D-26.
- **Verify:** Inspect the CREDITS entries for source paths.

#### NFR-044 · Asset license
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-39, D-56

All assets, derived or original, shall be licensed CC BY-SA 4.0 International
in a separate `LICENSE-ASSETS` file. Code stays MIT.

- **Rationale:** Decision: D-39 and D-56.
- **Verify:** Inspect `LICENSE-ASSETS`.

#### NFR-045 · Asset provenance
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-26, D-56, D-62

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

#### NFR-046 · No NonCommercial assets
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-26

No NonCommercial asset shall ship in any jar. RE:SS3D assets (CC BY-NC-SA 4.0)
and Stationeers (proprietary) are reference and inspiration only.

- **Rationale:** Decision: D-26.
- **Verify:** Inspect the jar contents against CREDITS.

#### NFR-042 · Distribution
`Should` · `R1` · `Accepted` · Verify: Inspect · Trace: D-18

Mod jars (`zas` and `zas_atmos`) shall be published to Modrinth and
CurseForge. Publishing the API to Maven Central is API-008.

- **Rationale:** Decision: D-18.
- **Verify:** Inspect the release pages.
