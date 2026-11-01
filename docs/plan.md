# ZAS³ (zas-cubed): plan to begin work

**Status:** proposed, 2026-10-30. Updated after the open-questions
walkthroughs (decisions D-11 to D-40), and on 2026-11-01 for the end of R0,
the move of Ad Astra to R2 with Mekanism (D-66), Blockbench for art (D-67),
and the area air controller and batteries in place of the cable and the
multipart block (D-68, D-69).

**Inputs:**
- decisions in [requirements/00-overview](requirements/00-overview.md#decision-log)
- research notes in [`research/`](research/)

## Where things stand

- **Repository:** public at `github.com/west3436/zas-cubed`. Code is MIT and
  clean-room (D-01). Assets are derived from vgstation13's CC BY-SA sprites
  (D-26) and made in Blockbench as Minecraft's own JSON models (D-67).
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
    D-66 then moved Ad Astra to R2 alongside Mekanism, so R1 ships no
    mod-compat adapter.
  - Added to R1: power, with FE, a generator and alarm-powered zones (D-25);
    photosynthesis (D-33); and the Normal/Hardcore preset (D-14). D-68 later
    dropped the cable that shared a block with pipes and made the air alarm
    the area air controller, the only power source for atmospherics parts;
    portables run on batteries (D-69).
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
Still open: accepting the architecture doc
([#2](https://github.com/west3436/zas-cubed/issues/2)).

- *2026-11-01:* S6 (Ad Astra, [#9](https://github.com/west3436/zas-cubed/issues/9))
  moved out of Phase B to R2 with the Ad Astra adapter (D-66). Nothing in
  R0 or R1 depends on it.

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
  | S6 | *Moved to R2 with the Ad Astra adapter (D-66).* End-to-end proof of the Ad Astra adapter (`EntityOxygenEvent`, the oxygen and temperature tick vetoes, `EnvironmentTickEvent`) with no mixins. Also confirm: suit item ids and temperature-immunity tags, whether suit oxygen is reachable through the fluid item capability, whether Ad Astra asks `EntityOxygenEvent` client-side, and the 1.16.26 floor | SPACE-010, SPACE-013, SPACE-016, SPACE-022, SPACE-023, D-29, D-51 |
- **Exit:** the architecture doc is accepted, the NFR numbers are set, and the
  mixin list is known.

### Phase C: scaffold (done 2026-10-31)

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

#### Phase D exit status (2026-10-31)

Every exit item passes in CI ([#33](https://github.com/west3436/zas-cubed/issues/33)).
R0 has no devices, pipes or hazards, so the scenarios run as adapted below;
the full plans in [Scenario verification](requirements/00-overview.md#scenario-verification)
are the Phase E exit. The scenario GameTests are in
[`ScenarioTests`](../zas/src/gametest/java/io/github/west3436/zas/gametest/ScenarioTests.java). They run in
the End, which the GameTest server has, with the vacuum ambient of the test
datapack's `zas_gametest:vacuum` for the length of each test, in a batch of
their own and with the invariant checker on (CORE-036).

| Item | Result | Tests |
|---|---|---|
| S-01 by commands | Pass. A sealed 27-cell room in vacuum is filled with `/zas gas add` and `/zas gas fill` to 101.325 kPa at 21 % O₂ and 293.15 K, forms one zone and holds it for 60 s. No area air controller, canisters, vent or helmet check: those are R1 (CONT, LIFE). | `s_01_pressurise_sealed_room_by_commands` |
| S-02 without hazards | Pass. Breaking a window gives the zone an ambient edge; the 27-cell room keeps 97.9 % of its pressure per second, the 1,000-cell room 99.93 % (D-21), and the small room dissolves into the vacuum after 325 s while the large one still holds 82 kPa. On a two-room station, closing the door splits the zone, a breach vents only its room, the resealed room is pressurised again by command, and opening the door merges the rooms. The "door" is a wall block: doors as zone boundaries are R1 (GEO-007). No items, players, damage or firelock: those are R1 (FLOW, LIFE, CONT). | `s_02_breach_vents_small_room_faster_than_large`, `s_02_station_breach_split_and_merge` |
| S-09 | Pass, without pipes and canisters (CONT, R1). A restart keeps two zones' compositions and temperatures to 1e-9; a zone paused by an unloaded chunk at the restart stays paused until the chunk loads, then resumes unchanged. | `s_09_restart_keeps_zones_and_paused_zone_resumes`, and the [`PersistenceTests`](../zas/src/gametest/java/io/github/west3436/zas/gametest/PersistenceTests.java) and [`PausedZoneTests`](../zas/src/gametest/java/io/github/west3436/zas/gametest/PausedZoneTests.java) |
| S-12 | Pass ([#100](https://github.com/west3436/zas-cubed/pull/100)). | CI's "S-12 API without ZAS³" step |
| Benchmarks | Recorded ([R0 engine benchmarks](research/benchmarks.md)). The engine's share passes NFR-001, NFR-002, NFR-003 and NFR-005; NFR-004's per-cell memory fails ([#78](https://github.com/west3436/zas-cubed/issues/78)). *2026-11-01: re-measured by retained size in #117 (283 B per zoned cell, 10.85 MiB for S-10); NFR-004's per-cell budget is revised to 256 B, which the owner accepted on 2026-11-01.* | `./gradlew :engine:bench` |

The scenarios also complete CORE-036, CORE-075, GEO-020 and GEO-022, now
*Implemented*. CORE-025 (R1) stays *Accepted*: its split search is local, but
the worker's tick still grows with zone size
([benchmarks](research/benchmarks.md#merge-and-split-core-023-core-024-and-core-025)).
*2026-11-01: CORE-025 is now Implemented. Per-section membership palettes
(#117) make a local edit cost about 0.05 ms at 1,000, 8,000 and 50,000 cells
([check](research/benchmarks.md#membership-palette-check-2026-11-01)).*

**R0 issues still open** (2026-11-01; #20 to #23, #25, #26, #28 and #32
are closed, and so are #106, #108 and #109 from the R0 benchmarks and tests).
Each remaining one waits on R1 work:

| Issue | Why |
|---|---|
| [#27](https://github.com/west3436/zas-cubed/issues/27) boundaries and paused zones | GEO-035 stays *Accepted*: its Verify needs a breathing player (LIFE) and a device (CONT), both R1. The API query part passes (`geo_035_queries_see_last_known_state`). |
| [#29](https://github.com/west3436/zas-cubed/issues/29) persistence | DATA-001 also lists zone pins (API-018) and dimension pauses (OPS-005), and DATA-009 lists hotspots (FIRE): none exist yet. Membership, mixtures, temperatures, held changes and queued additions persist. |
| [#31](https://github.com/west3436/zas-cubed/issues/31) debug rendering | The overlay merged in [#98](https://github.com/west3436/zas-cubed/pull/98) and passed the owner's manual test; CLIENT-005 and CLIENT-012 are Implemented. CLIENT-011's boundary edges need GEO-007 and CLIENT-013 needs hotspots from FIRE, both R1. |

### Phase E: R1 feature tracks (in parallel once R0 is done)

E7 (Ad Astra) moved to R2 with Mekanism (D-66).

**Order (2026-11-01).** The tracks share a foundation that has to come first,
because nearly every one of them depends on it:

| Foundation | Issue | Needed by |
|---|---|---|
| The real gas set and gas classification tags (CORE-011, CORE-012) | [#64](https://github.com/west3436/zas-cubed/issues/64) | LIFE's toxicity and hazards, FIRE's fuels and oxidisers, CONT's canisters and filters |
| Doors and other zone-boundary blocks (GEO-007, GEO-044, CORE-034, CORE-067) | [#64](https://github.com/west3436/zas-cubed/issues/64), [#65](https://github.com/west3436/zas-cubed/issues/65) | Firelocks and airlocks (CONT), S-02, the debug overlay's boundary edges (#31). It also fixes [#107](https://github.com/west3436/zas-cubed/issues/107) and brings the S-10 bench station from 278 zones down to 100 |
| The Normal/Hardcore preset (OPS-007) | [#37](https://github.com/west3436/zas-cubed/issues/37) | Hardcore zone triggers (CORE-056), FIRE, FLOW's block damage, planting (CONT) |
| The breathability helper (API-014) | [#59](https://github.com/west3436/zas-cubed/issues/59) | LIFE, the HUD, and anything else that asks whether a point is breathable |

*2026-11-01: the multipart pipe-and-cable spike
([#46](https://github.com/west3436/zas-cubed/issues/46)) that was to run
alongside is dropped. D-68 removed the multipart block and the cable.*

Then the first target is **S-01 end to end**, a vertical slice through most
tracks: breathing ([#34](https://github.com/west3436/zas-cubed/issues/34)),
pipe networks ([#47](https://github.com/west3436/zas-cubed/issues/47)), the
canister, connector and vent from the device set
([#48](https://github.com/west3436/zas-cubed/issues/48)), the area air
controller, which forms the room's zone
([#50](https://github.com/west3436/zas-cubed/issues/50)), and the power it
gives the vent ([#52](https://github.com/west3436/zas-cubed/issues/52)). After that
the tracks widen out in parallel, one scenario at a time (S-02 to S-05, S-08,
S-11, S-13).

| Track | Area | Notes |
|---|---|---|
| E1 | LIFE plus HUD, Normal/Hardcore preset (OPS-007) | Breathing, hazards, life-support capability, client breathing sync. Internals need E5 tanks and masks. |
| E2 | HEAT | Original design. vg has no precedent. |
| E3 | FIRE | Data-driven reactions, gas fire, vanilla fire needing O₂, combustion as a Hardcore zone trigger |
| E4 | FLOW | Volume-aware airflow, player-motion sync, Hardcore glass shattering |
| E5 | CONT (R1 subset) | Pipe networks and devices; canisters, tanks, suit; area air controller, firelock, analyzer; gas sources and photosynthesis; **power**: FE on an early-game scale (D-37), a P.A.C.M.A.N.-style fuel generator (D-35), the area air controller as an APC-style hub that powers every part in its zone or linked to it, with priority (D-34, D-68), and batteries and a recharger for portables (D-69) |
| E6 | Art pipeline | Block and item models made in Blockbench from vg sprites, as vanilla JSON models with textures at 16 pixels per block (D-67); `CREDITS` and provenance for each asset (NFR-045). Sources and order in the [texture procurement plan](texture-procurement.md) |
| ~~E7~~ | ~~SPACE~~ | **Moved to R2 (D-66).** R1 keeps the generic pieces space mods build on: ambient providers (ENV-004), the breathability helper (API-014), the life-support capability (API-022), and the NeoForge breathing event (SPACE-002, now with E1) |
| E8 | CLIENT, DATA, docs | Sync, visuals, player and developer docs, example addon, API 1.0.0 on Maven Central |

- **Exit:** S-01 to S-13 pass (except S-06 and S-07, deferred to R2), the NFR budgets are
  met, and the API is frozen at 1.0.0.

### Phase F: release

- Public betas through GitHub Releases, then R1 on Modrinth and CurseForge,
  with the API on Maven Central (D-18).

### After R1 (already queued)

1. **Mekanism reference integration** (D-11). It uses only the public API and
   starts from the [MEK candidates](requirements/50-compat-mekanism.md) and
   the research note.
2. **Ad Astra reference integration** (D-66). Spike S6
   ([#9](https://github.com/west3436/zas-cubed/issues/9)), then the adapter
   and planet ambients ([SPACE](requirements/51-compat-space.md), #54 to
   #56). Like Mekanism, it uses only the public API.
3. **Northstar Redux and Create Cosmonautics adapters** (SPACE-011/012).
4. **The rest of the SS13 device set** (CONT R2 column), and biome/altitude
   ambients.

## Proposed module layout (to be validated in Phase B)

| Module | Depends on | Contents |
|---|---|---|
| `engine` | nothing Minecraft-specific | Gas math, zone graph, edges, scheduler, reactions runtime. Thread-safe, snapshot-based (CORE-043/045). |
| `api` (standalone module jar, published to Maven Central) | Minecraft and NeoForge types only | Public interfaces, records, events, capability definitions, and the fallback when ZAS³ is absent (API-001/006) |
| `zas` (framework mod) | api (jar-in-jar), engine | Geometry, hooks and mixins, persistence, sync, environments, life support, heat, fire, airflow, presets, commands, client |
| `zas_atmos` (content mod "ZAS³ Atmospherics", D-38) | api, `zas` | SS13 atmospherics blocks and items, power (D-68, D-69), GUIs, CC BY-SA 4.0 assets (D-39) |
| `compat-adastra` | api, Ad Astra (compile-only) | The Ad Astra adapter (SPACE-010 to SPACE-023), R2 (D-66) |
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
| R6 | ~~Ad Astra's API changes, or Ad Astra stops updating on 1.21.1~~ | — | **Retired for R1:** Ad Astra moved to R2 (D-66). When it resumes: public `api` package; pinned version range; adapter isolated in its own module (SPACE-003). |
| R7 | Scope creep. Full SS13 content now also includes power, a generator and power distribution by the area air controller. | R1 slips | A strict R1 content subset; power scoped by D-34, D-35, D-37 and D-68 (one generator, no cable: the area air controller powers its parts). **Reduced by D-68,** which dropped the multipart pipe-and-cable block. |
| R8 | Clean-room drift: vg code leaks in | License violation | NFR-040; review checklist; research notes in prose only |
| R9 | Hazard checks on every entity cost too much | Tick cost | Per-entity cadence, cached zone lookups, spatial bucketing |
| R10 | Gameplay feel at Minecraft scale | Balance | Volume-aware venting (D-21); per-second rates (D-20); the preset (D-14); playtest scenarios |
| R11 | **Off-thread engine complexity:** races with block changes, stale results, nondeterminism | Subtle desync bugs; slower development | Snapshot-only inputs, generation counters, apply on the server thread, single-thread equivalence tests (CORE-071); spike S5 first. **S5:** the fixed-latency apply plus reject-on-unload protocol gave bit-identical and conserving results headless ([architecture §3](architecture.md#3-the-off-thread-engine)). |
| R12 | **Asset provenance:** a vg sprite with unclear or non-free origin | License violation in shipped assets | CC BY-SA sources only, never `goon/`; per-asset provenance in `CREDITS`; leave out anything unclear (NFR-041) |

## Immediate next steps

_Updated 2026-11-01._

1. **Owner decisions:**
   - ~~NFR-004: accept or reject the per-cell memory budget revised to 256 B in
     #117~~ *Accepted 2026-11-01.*
   - review the [architecture](architecture.md)
     ([#2](https://github.com/west3436/zas-cubed/issues/2)) and accept it or
     send changes. That closes Phase B.
2. **The R1 foundation** (see [Phase E](#phase-e-r1-feature-tracks-in-parallel-once-r0-is-done)):
   the gas set and zone-boundary blocks
   ([#64](https://github.com/west3436/zas-cubed/issues/64),
   [#65](https://github.com/west3436/zas-cubed/issues/65)), the preset
   ([#37](https://github.com/west3436/zas-cubed/issues/37)) and the
   breathability helper ([#59](https://github.com/west3436/zas-cubed/issues/59)).
3. **S-01 end to end:** breathing, pipe networks, canister, connector and
   vent, the area air controller and the power it gives the vent (#34, #47,
   #48, #50, #52). Then widen out scenario by scenario.
   - Follow-ups from R0 to fold in along the way: zone-creation speed
     ([#120](https://github.com/west3436/zas-cubed/issues/120)), idle-zone cost
     ([#121](https://github.com/west3436/zas-cubed/issues/121)), saved
     operations keeping their sub-cell
     ([#122](https://github.com/west3436/zas-cubed/issues/122)), and sub-cells
     of position-dependent shapes, with GEO-043
     ([#123](https://github.com/west3436/zas-cubed/issues/123)).
4. **After R1:** the reference integrations, Mekanism and Ad Astra
   (D-11, D-66; see [After R1](#after-r1-already-queued)).
