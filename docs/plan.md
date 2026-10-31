# ZAS³ (zas-cubed): plan to begin work

**Status:** proposed, 2026-10-30. Updated after the open-questions
walkthroughs (decisions D-11 to D-40).

**Inputs:**
- decisions in [requirements/00-overview](requirements/00-overview.md#decision-log)
- research notes in [`research/`](research/)

## Where things stand

- **Repository:** public at `github.com/west3436/zas-cubed`. Code is MIT and
  clean-room (D-01). Assets are derived from vgstation13's CC BY-SA sprites
  (D-26).
- **Research:**
  - vgstation13 ZAS core: [zas-core](research/zas-core.md)
  - vg atmospherics content and mob effects: [vg-atmos-content](research/vg-atmos-content.md)
  - NeoForge 1.21.1 platform: [neoforge-1.21.1](research/neoforge-1.21.1.md)
  - space mods and prior art: [space-mods-and-prior-art](research/space-mods-and-prior-art.md)
  - Mekanism: [mekanism-1.21.1](research/mekanism-1.21.1.md), deferred by D-11
- **Requirements:** the structure is proposed and every area has draft
  candidates ([requirements/README](requirements/README.md)). Q-01 to Q-18
  and Q-21 to Q-23 are resolved into decisions D-11 to D-39, and the NFR
  reference hardware into D-40.
  - **Still open:** nothing outside Mekanism.
  - **Parked with Mekanism:** Q-19 and Q-20.
- **Scope changes from the walkthrough:**
  - Mekanism is cut from R1 (D-11), and space compat is Ad Astra only (D-29).
  - Added to R1: power, with FE, a generator, alarm-powered zones and a cable
    that shares a block with pipes (D-25); photosynthesis (D-33); and the
    Normal/Hardcore preset (D-14).
  - The engine runs **off the main thread from day one** (D-30).

## Approach

Requirements first, then risk-driven work:

1. **Agree what we're building.** Review the requirements area by area until
   every R1 `Must` is accepted.
2. **Decide how to build it.** Write the architecture doc, and in parallel run
   small, measured spikes on the riskiest unknowns. The off-thread engine (S5)
   is now the biggest one.
3. **Build R0 (Foundation):** the engine, world geometry, ambients,
   persistence and tooling, with no survival content.
4. **Build R1 in parallel tracks** on that foundation, then release.

## Phases

### Phase A: requirements baseline (done 2026-10-31, tag `req-r1-baseline`)

- Review in the order given in the [requirements workflow](requirements/README.md#review-workflow):
  `00-overview` → CORE → GEO → ENV → API → LIFE → HEAT/FIRE/FLOW → CONT → SPACE
  → CLIENT/DATA/OPS → NFR. MEK is deferred.
- Write acceptance criteria for scenarios S-01 to S-12 (S-07 is deferred).
  They become the R1 test plan.
- **Exit:** every R1 `Must` is *Accepted*. Tag `req-r1-baseline`.

### Phase B: architecture and spikes (in progress since 2026-10-31)

**Status:** the [architecture](architecture.md) draft is ready for review.
Spikes S0–S5 are done ([results](research/spikes.md)), including S0's check on
a real dedicated server ([#77](https://github.com/west3436/zas-cubed/issues/77),
[follow-up](research/spikes.md#follow-up-real-dedicated-server-2026-10-31)).
Still open:

- S6 (Ad Astra) is not started ([#9](https://github.com/west3436/zas-cubed/issues/9)).

- **Architecture doc** (`docs/architecture.md`):
  - module layout and the engine/platform boundary
  - the **off-thread engine**: snapshots, worker threads, applying results,
    generation counters, determinism
  - storage: chunk-aligned cell data plus an in-memory zone graph
  - API module layout, fallback behaviour without ZAS³, and the event model
  - the mixin inventory
- **Spikes.** Throwaway code. Each one produces numbers that are written up in
  `docs/research/spikes.md`.

  | # | Question | Feeds |
  |---|---|---|
  | S0 | Packaging. Can ModDevGradle bundle a plain-Java `engine` source set into the mod jar? Can the standalone `api` module be bundled (jar-in-jar) by both ZAS³ and a consumer, with one copy kept and the fallback working when ZAS³ is absent? | CORE-043, API-001/006, D-17 |
  | S1 | Compare hooking `LevelChunk#setBlockState` (catches direct chunk writers such as WorldEdit) with `Level#onBlockStateChange` (misses them). Check enqueue-only cost and reconciliation on chunk load. | GEO-010/011/013, NFR-003 |
  | S2 | How long does deriving seal masks and free volumes for every block state in a large pack take, and how much memory? Do panes and walls come out right? | GEO-002/003/004 |
  | S3 | What is the throughput of bounded fills at 65,536 cells on snapshot data, and what is the latency from trigger to zone? | GEO-020/022, D-23 |
  | S4 | How do merge, split and local-split detection perform on 50k-cell zones? | CORE-024/025 |
  | S5 | **Off-thread engine pipeline.** Cost of snapshotting sections on the server thread; handing off to a worker; applying results with generation checks under concurrent block changes; whether results stay deterministic. | CORE-044/045/047/070/071, D-30 |
  | S6 | End-to-end proof of the Ad Astra adapter (`EntityOxygenEvent`, the oxygen and temperature tick vetoes, `EnvironmentTickEvent`) with no mixins. Also confirm: suit item ids and temperature-immunity tags, whether suit oxygen is reachable through the fluid item capability, whether Ad Astra asks `EntityOxygenEvent` client-side, and the 1.16.26 floor | SPACE-010, SPACE-013, SPACE-016, SPACE-022, SPACE-023, D-29, D-51 |
- **Exit:** the architecture doc is accepted, the NFR numbers are set, and the
  mixin list is known.

### Phase C: scaffold (unblocked: content mod ID decided in D-38)

- **Build:** a ModDevGradle multi-project. Versions, all verified on
  2026-10-30 ([neoforge-1.21.1 §1](research/neoforge-1.21.1.md#1-toolchain)):

  | Component | Version |
  |---|---|
  | ModDevGradle | 2.0.148 |
  | Gradle | 9.2.1 |
  | NeoForge (build) | 21.1.265 |
  | NeoForge (minimum) | 21.1.264 |
  | Parchment | 1.21.1 / 2024.11.17 |
  | Java | 21 |
  | MixinExtras | 0.5.5, bundled by NeoForge |

- **Tooling:**
  - JUnit 5 for the engine
  - a NeoForge GameTest server run
  - Spotless formatting
  - GitHub Actions running build, unit tests and GameTests on every PR
  - snapshot publishing of the API to Maven Central (D-18)
  - an API binary-compatibility check
- **Repo hygiene:**
  - `CONTRIBUTING.md` with the clean-room code rule (NFR-040, NFR-043)
  - `LICENSE-ASSETS` and `CREDITS` with asset provenance (NFR-044, NFR-045)
  - issue and PR templates
- **Exit:** CI is green. Empty `zas` and `zas_atmos` mods load in the dev
  client and on a dedicated server.

### Phase D: R0 Foundation

- **Engine (CORE R0):**
  - gas mixture and gas registry interface
  - zones and edges on an abstract topology
  - ambient reservoirs
  - the **off-thread scheduler** (1 s tick, per-second rates)
  - conservation checker
- **Platform:**
  - seal masks, free volumes and overrides (GEO R0)
  - the complete change hook and locality filter
  - bounded enclosure detection (65,536 cells)
  - zones on demand with Normal/Hardcore triggers
  - per-dimension ambients (ENV R0)
  - persistence (DATA R0)
- **Tooling:** commands, profiler, debug renderer, geometry audit (OPS and
  CLIENT R0).
- **API:** standalone module skeleton with queries, mutations and the fallback
  (API R0).
- **Exit:**
  - S-01 can be done with commands: fill a sealed room in a vacuum dimension.
  - S-02 works without hazards: breach, vent, the zone splits and merges
    correctly.
  - S-09 and S-12 pass.
  - Benchmarks are recorded against the NFR targets
    ([R0 engine benchmarks](research/benchmarks.md)).

### Phase E: R1 feature tracks (in parallel once R0 is done)

| Track | Area | Notes |
|---|---|---|
| E1 | LIFE plus HUD, Normal/Hardcore preset (OPS-007) | Breathing, hazards, life-support capability, client breathing sync. Internals need E5 tanks and masks. |
| E2 | HEAT | Original design. vg has no precedent. |
| E3 | FIRE | Data-driven reactions, gas fire, vanilla fire needing O₂, combustion as a Hardcore zone trigger |
| E4 | FLOW | Volume-aware airflow, player-motion sync, Hardcore glass shattering |
| E5 | CONT (R1 subset) | Pipe networks and devices; canisters, tanks, suit; air alarm, firelock, analyzer; gas sources and photosynthesis; **power**: FE on an early-game scale (D-37), a P.A.C.M.A.N.-style fuel generator (D-35), the alarm as an APC-style hub with priority (D-34), and our own multipart block for cable plus pipe (D-36, spike it early) |
| E6 | Art pipeline | 3D models from vg sprites with the Blender MCP; `CREDITS` and provenance for each asset (NFR-045) |
| E7 | SPACE | Ad Astra adapter, plus seeding ambients from Ad Astra planet data |
| E8 | CLIENT, DATA, docs | Sync, visuals, player and developer docs, example addon, API 1.0.0 on Maven Central |

- **Exit:** S-01 to S-12 pass (except the deferred S-07), the NFR budgets are
  met, and the API is frozen at 1.0.0.

### Phase F: release

- Public betas through GitHub Releases, then R1 on Modrinth and CurseForge,
  with the API on Maven Central (D-18).

### After R1 (already queued)

1. **Mekanism reference integration** (D-11). It uses only the public API and
   starts from the [MEK candidates](requirements/50-compat-mekanism.md) and
   the research note.
2. **Northstar Redux and Create Cosmonautics adapters** (SPACE-011/012).
3. **The rest of the SS13 device set** (CONT R2 column), and biome/altitude
   ambients.

## Proposed module layout (to be validated in Phase B)

| Module | Depends on | Contents |
|---|---|---|
| `engine` | nothing Minecraft-specific | Gas math, zone graph, edges, scheduler, reactions runtime. Thread-safe, snapshot-based (CORE-043/045). |
| `api` (standalone module jar, published to Maven Central) | Minecraft and NeoForge types only | Public interfaces, records, events, capability definitions, and the fallback when ZAS³ is absent (API-001/006) |
| `zas` (framework mod) | api (jar-in-jar), engine | Geometry, hooks and mixins, persistence, sync, environments, life support, heat, fire, airflow, presets, commands, client |
| `zas_atmos` (content mod "ZAS³ Atmospherics", D-38) | api, `zas` | SS13 atmospherics blocks and items, power and cables, the multipart block (D-36), GUIs, CC BY-SA 4.0 assets (D-39) |
| `compat-adastra` | api, Ad Astra (compile-only) | The Ad Astra adapter (SPACE-010 to SPACE-023) |
| `example-addon` | api | Usage examples for consumers (API-005) |

**Constraints from the platform research:**
- **No split packages.** FML loads JPMS modules, and the same Java package in
  two jars crashes the loader. Every module has its own package root under
  `io.github.west3436.zas` (D-15).
- **Capability definitions live in `api`.** NeoForge resolves capabilities by
  name.
- **Storage:** gas contents and zone fragments go in a serialized chunk
  attachment. The live zone graph stays in memory and is rebuilt from loaded
  chunks.
- **Conditional mixins** use `[[mixins]]` with `requiredMods`, which was
  verified in FML 1.21.1.
- **Expected mixin inventory:**
  - the block-change hook
  - fire: `FireBlock#tick`, `BaseFireBlock#canBePlacedAt`, NeoForge's
    `checkBurnOut` and `canCatchFire`
  - furnace and campfire ticking

## Risk register

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | Block changes that bypass our hook (other mods, world editors) leave zones out of sync with the world | Ghost zones, air leaking through walls | Hook at `LevelChunk#setBlockState` (spike S1); revalidate on chunk load and offer an API `markDirty` (GEO-013); geometry audit command. **S1 (2026-10-31):** the hook sees direct chunk writes, and its cost is below noise. |
| R2 | Enclosure detection is too slow for large stations | Slow zone formation | Bounded fills at 65,536 cells on snapshots; the trigger-only model (D-13) keeps fills rare; caching (spike S3). **Retired by S3:** 5.5 ms per 65,536-cell fill on the worker. |
| R3 | Zone rebuilds of O(zone size) on edits inside large zones | Latency when building inside stations | Local split detection (CORE-025); spike S4. **Mostly retired by S4:** 2 µs per ordinary edit; 5 ms worst-case detour; 6.7 ms for a real 50k split, all on the worker. |
| R4 | Zones that cross unloaded chunks | Inconsistent state | Freeze, never force-load (D-24) |
| R5 | ~~Mekanism venting needs fragile mixins~~ | — | **Retired for R1:** Mekanism deferred (D-11). It comes back as an API-only reference integration. |
| R6 | Ad Astra's API changes, or Ad Astra stops updating on 1.21.1 | The adapter breaks | Public `api` package; pinned version range; adapter isolated in its own module (SPACE-003) |
| R7 | Scope creep. Full SS13 content now also includes power, a generator, alarm power distribution and a multipart pipe-and-cable block. | R1 slips | A strict R1 content subset; power scoped by D-34 to D-37 (one generator, one cable tier); spike the multipart block early in E5 |
| R8 | Clean-room drift: vg code leaks in | License violation | NFR-040; review checklist; research notes in prose only |
| R9 | Hazard checks on every entity cost too much | Tick cost | Per-entity cadence, cached zone lookups, spatial bucketing |
| R10 | Gameplay feel at Minecraft scale | Balance | Volume-aware venting (D-21); per-second rates (D-20); the preset (D-14); playtest scenarios |
| R11 | **Off-thread engine complexity:** races with block changes, stale results, nondeterminism | Subtle desync bugs; slower development | Snapshot-only inputs, generation counters, apply on the server thread, single-thread equivalence tests (CORE-071); spike S5 first. **S5:** the fixed-latency apply plus reject-on-unload protocol gave bit-identical and conserving results headless ([architecture §3](architecture.md#3-the-off-thread-engine)). |
| R12 | **Asset provenance:** a vg sprite with unclear or non-free origin | License violation in shipped assets | CC BY-SA sources only, never `goon/`; per-asset provenance in `CREDITS`; leave out anything unclear (NFR-041) |

## Immediate next steps

1. **Review the [architecture](architecture.md)** and the
   [spike results](research/spikes.md), then accept the doc or send changes.
2. **Close the Phase B gap:** run S6 (Ad Astra, [#9](https://github.com/west3436/zas-cubed/issues/9)).
3. **Then Phase C:** scaffold the multi-project build, following the
   packaging rules in [architecture §2](architecture.md#2-modules-and-packaging).
