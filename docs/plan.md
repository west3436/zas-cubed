# ZAS³ (zas-cubed): plan to begin work

**Status:** proposed, 2026-10-30. Updated after the open-questions
walkthroughs (decisions D-11 to D-40), and on 2026-11-01 for the end of R0,
the move of Ad Astra to R2 with Mekanism (D-66), Blockbench for art (D-67),
the area air controller and batteries in place of the cable and the
multipart block (D-68, D-69), and the R1 foundation.

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

### Phase B: architecture and spikes (done 2026-11-05)

**Status:** the owner accepted the [architecture](architecture.md) on 2026-11-05
([#2](https://github.com/west3436/zas-cubed/issues/2)).
Spikes S0–S5 are done ([results](research/spikes.md)), including S0's check on
a real dedicated server ([#77](https://github.com/west3436/zas-cubed/issues/77),
[follow-up](research/spikes.md#follow-up-real-dedicated-server-2026-10-31)).
Two of the architecture's open items are still to measure: a large modpack
([#79](https://github.com/west3436/zas-cubed/issues/79)) and the enclosure
prefetch ([#80](https://github.com/west3436/zas-cubed/issues/80)).

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
| Benchmarks | Recorded ([R0 engine benchmarks](research/benchmarks.md)). The engine's share passes NFR-001, NFR-002, NFR-003 and NFR-005; NFR-004's per-cell memory fails ([#78](https://github.com/west3436/zas-cubed/issues/78)). *2026-11-01: re-measured by retained size in #117 (283 B per zoned cell, 10.85 MiB for S-10); NFR-004's per-cell budget is revised to 256 B, which the owner accepted on 2026-11-01. Re-measured after GEO-007, with S-10's 100 zones and 178 door edges ([#133](https://github.com/west3436/zas-cubed/pull/133)): every verdict holds, NFR-004's included ([S-10 after GEO-007](research/benchmarks.md#s-10-after-geo-007-2026-11-01)).* | `./gradlew :engine:bench` |

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
| [#31](https://github.com/west3436/zas-cubed/issues/31) debug rendering | The overlay merged in [#98](https://github.com/west3436/zas-cubed/pull/98) and passed the owner's manual test; CLIENT-005 and CLIENT-012 are Implemented. CLIENT-011's boundary edges need GEO-007 and CLIENT-013 needs hotspots from FIRE, both R1. *2026-11-01: GEO-007 is in ([#133](https://github.com/west3436/zas-cubed/pull/133)), and the overlay needed no change. CLIENT-011 is Implemented after the owner's check of the boundary colours; #31 waits only on CLIENT-013.* *2026-11-04: CLIENT-013 is Implemented after the owner's look at a gas fire, which closes #31.* |

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

*2026-11-01: the foundation is in, with two more items from #64 that were
ready: Hardcore zone triggers and volume-aware venting.*

| Item | PR | Requirements | Left |
|---|---|---|---|
| Gas set and gas tags | [#130](https://github.com/west3436/zas-cubed/pull/130) | CORE-011, CORE-013 and CORE-015 to CORE-017 *Implemented*; CORE-012 back to *Accepted* by the owner, until LIFE, FIRE or CONT reads the tags; *Implemented* 2026-11-05, now that the scrubber and filter read them | Heats of fusion for O₂, N₂, H₂O, H₂ and CO, which no checked reference gives: left out until a track needs them (owner). `gasesInTag` got a default, like `breathability` ([#138](https://github.com/west3436/zas-cubed/pull/138)) |
| Zone-boundary blocks | [#133](https://github.com/west3436/zas-cubed/pull/133) | GEO-007, GEO-044, CORE-034 and CORE-067 *Implemented*; [#107](https://github.com/west3436/zas-cubed/issues/107) fixed | None for CLIENT-011, which the owner's overlay check made *Implemented* (#31). A door opening onto open air still costs a bounded breach fill each time, until GEO-021 and GEO-027 |
| The preset | [#128](https://github.com/west3436/zas-cubed/pull/128) | OPS-015 *Implemented*; OPS-007 stays *Accepted* until the options it lists have users | Per-world presets: NeoForge shares `config/zas-server.toml` between singleplayer worlds that have no `serverconfig` copy. The owner chose to have `/zas preset` write the world's own copy ([#139](https://github.com/west3436/zas-cubed/issues/139)) |
| Breathability helper | [#131](https://github.com/west3436/zas-cubed/pull/131) | API-014 stayed *Accepted*; *Implemented* 2026-11-05 | The API and the no-ZAS³ fallback only. The verdict needs LIFE and API-022 (#34 to #36); until then `zas` answers with vanilla's verdict, marked interim. The owner kept `@LevelThread` and the `Hazard` and `Protection` values; LIFE confirms or trims the values before 1.0.0 |
| Hardcore zone triggers | [#136](https://github.com/west3436/zas-cubed/pull/136) | CORE-056 stays *Accepted* | The fuel generator (CONT) joins `#zas:combustion` when it exists. Fire spreading in the open under Hardcore runs a bounded fill per new fire block, a cost like a door opening to air, with the same mitigation |
| Volume-aware venting | [#134](https://github.com/west3436/zas-cubed/pull/134) | CORE-035 and CORE-068 *Implemented* | Zone-to-zone sharing is now volume-aware by default too: two 27-cell rooms halve their difference in about 18 s rather than 4 s, two 1,000-cell rooms in about 9 minutes. `[atmos] venting = FAITHFUL` gives ZAS's rates, about 4 s at any size |

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

*2026-11-02: S-01 passes end to end.* A sealed glass room in a vacuum dimension
forms its zone from its area air controller. A coal generator powers the
controller, which orders its vent to fill the room from N₂ and O₂ canisters on
connector ports. The room settles at 101 kPa and 21 % O₂, and a survival
player breathes in it for 60 s unharmed. The owner played the whole round in
one combined test world, including building a second room from a chest. Then
it merged as [#160](https://github.com/west3436/zas-cubed/pull/160).

| Item | PR | Requirements | Left |
|---|---|---|---|
| Breathing | [#151](https://github.com/west3436/zas-cubed/pull/151) | The #34 breathing subset; LIFE-048 added and *Implemented* | ~~The air bar flickers while the server drains it, and the owner saw it "always": LIFE-023, client breathing sync ([#36](https://github.com/west3436/zas-cubed/issues/36))~~ *Fixed by [#170](https://github.com/west3436/zas-cubed/pull/170) (2026-11-03).* |
| Pipe networks | [#152](https://github.com/west3436/zas-cubed/pull/152) | The #47 subset; CONT-114 (dyed pipes) added, changed by the owner so mined pipes keep their dye, and *Implemented* | ~~Mole-weighted mixing until the API has heat capacities, which the owner requires before release ([#157](https://github.com/west3436/zas-cubed/issues/157)).~~ *Energy-conserving since [#167](https://github.com/west3436/zas-cubed/pull/167).* Open ends need art that reads as open, then particles ([#164](https://github.com/west3436/zas-cubed/issues/164)) |
| Canister, connector port, vent | [#156](https://github.com/west3436/zas-cubed/pull/156) | The #48 subset | The owner changed it so a canister broken in creative destroys its gas. The vent GUI now shows its network's pressure and why it is idle. A controlled vent's GUI is locked (owner) |
| Area air controller and generator | [#155](https://github.com/west3436/zas-cubed/pull/155) | The #50 and #52 subset | The generator burns any furnace fuel (owner), scaled to coal. ~~A vent's first order doesn't yet name its controller (the sender position on `VentOrder`).~~ *Fixed by [#166](https://github.com/west3436/zas-cubed/pull/166).* GUI textures for the controller and generator screens are still to draw ([#185](https://github.com/west3436/zas-cubed/issues/185)). The power-lease GameTest clock gap ([#162](https://github.com/west3436/zas-cubed/issues/162)) |
| Zone pins | [#149](https://github.com/west3436/zas-cubed/pull/149) | API-050; OPS-017 added and *Implemented* | None |
| Per-world preset copy | [#161](https://github.com/west3436/zas-cubed/pull/161) | Closes [#139](https://github.com/west3436/zas-cubed/issues/139) | None |
| Loading rooms read as unresolved | [#154](https://github.com/west3436/zas-cubed/pull/154), [#163](https://github.com/west3436/zas-cubed/pull/163) | API-010 | Flaky `geo_010_piston` and `geo_010_fluid` ([#159](https://github.com/west3436/zas-cubed/issues/159)). Open-air additions under test-driven ticks ([#158](https://github.com/west3436/zas-cubed/issues/158)) |

The S-01 GameTest is `StationTests.s_01_pressurise_station`.

*2026-11-03: S-02, S-05 and S-08 pass end to end.* The owner's scope was each
scenario's subset of its issues: a standalone 1 × 2 firelock and the crowbar,
no door or airlock variants; the heater but no freezer; the softsuit, four
tanks and the breath mask, but no gas mask, hardsuit, magnetic boots or tank
failure. During the round's review the owner made heaters, air conditioners,
portable scrubbers and pumps battery portables that never link to an area air
controller (CONT-018, CONT-019, CONT-064 and CONT-087 stay *Accepted* with
dated notes). So the round also brought the batteries and the recharger. The
owner played the round in one combined test world over two sessions, then it
merged as [#183](https://github.com/west3436/zas-cubed/pull/183).

| Item | PR | Requirements | Left |
|---|---|---|---|
| Heat capacities and energy-conserving pipe mixing | [#167](https://github.com/west3436/zas-cubed/pull/167) | API-051 and API-052 added, accepted by the owner and *Implemented* | `zas_atmos`'s devices still step every 20 game ticks, not every atmos period |
| Hazards | [#169](https://github.com/west3436/zas-cubed/pull/169) | The #35 subset | Each temperature hazard deals only its highest tier, not the sum (as built) |
| Conduction and radiation | [#172](https://github.com/west3436/zas-cubed/pull/172) | #39; HEAT-004 | Insulating station walls ([#179](https://github.com/west3436/zas-cubed/issues/179)) |
| Heat sources | [#175](https://github.com/west3436/zas-cubed/pull/175) | #40 without HEAT-007; API-013 | Paused regions void or supply heat; ice never warms (as built) |
| Life support and worn protection | [#176](https://github.com/west3436/zas-cubed/pull/176) | API-022 in part; LIFE-020, LIFE-024 to LIFE-027 | API-022's gas filtering |
| Firelock and crowbar | [#168](https://github.com/west3436/zas-cubed/pull/168) | CONT-043 (changed by the owner: a breach alarms the controller at once, so firelocks shut within about 2 s, and a link covers only the device linked), CONT-092 to CONT-094; CONT-042 and CONT-068 | Firelocks over doors and airlocks |
| Airflow | [#171](https://github.com/west3436/zas-cubed/pull/171) | The #44 subset; FLOW-018 (changed by the owner: creative players and spectators are exempt) and FLOW-019 (crawling through low holes, owner) added and *Implemented* | Particles and sound (CLIENT-003 and CLIENT-010, [#57](https://github.com/west3436/zas-cubed/issues/57)). Magnetic boots for FLOW-012 ([#49](https://github.com/west3436/zas-cubed/issues/49)) |
| S-02 GameTest | [#173](https://github.com/west3436/zas-cubed/pull/173) | S-02 | None |
| Client breathing sync | [#170](https://github.com/west3436/zas-cubed/pull/170) | LIFE-023 | None |
| Battery heater, batteries, recharger | [#177](https://github.com/west3436/zas-cubed/pull/177) | CONT-111 to CONT-113 *Implemented*; CONT-018, CONT-019, CONT-064, CONT-087 changed by the owner | The air conditioner, portable scrubber and portable pump are battery portables in the text only. The air conditioner will hold its room the heater's way, with `HeatMutations.remove` (owner, 2026-11-03) |
| Internals, tanks, breath mask, softsuit | [#182](https://github.com/west3436/zas-cubed/pull/182) | LIFE-002, LIFE-021, LIFE-028, LIFE-029, LIFE-043 to LIFE-046, CONT-030, CONT-032, CONT-039 *Implemented*; CONT-031 and CONT-090 in part | The hardsuit halves (CONT-036) |
| Shared test player | [#174](https://github.com/west3436/zas-cubed/pull/174), [#180](https://github.com/west3436/zas-cubed/pull/180) | None | None |

The GameTests are `StationTests.s_02_hull_breach`,
`HeatStationTests.s_05_heater_holds_a_room_at_its_setpoint` (with
`s_05_room_in_vacuum_cools` and `s_05_room_beside_lava_warms`) and
`StationTests.s_08_breathing_gear`. GUI textures for the heater, recharger and
tank screens are still to draw ([#185](https://github.com/west3436/zas-cubed/issues/185)).

*2026-11-03: S-03 and S-04 pass end to end.* The scope was all of
[#41](https://github.com/west3436/zas-cubed/issues/41), [#42](https://github.com/west3436/zas-cubed/issues/42) and [#43](https://github.com/west3436/zas-cubed/issues/43), plus API-019, API-020 and API-031.
The owner added the igniter (CONT-105), the fire alarm (CONT-044) and the fire
extinguisher, which moved from R2 to R1 as a `Could` (CONT-037). The owner also
decided that the fuel generator never touches the air: it uses no O₂, makes no
CO₂ or heat, and never starts a Hardcore zone (D-35, with dated notes on
FIRE-021, FIRE-024, FIRE-025, FIRE-027, HEAT-007, CONT-067 and CORE-056). Every
PR merged once CI passed. The owner's in-game checks come afterwards, in one
condensed combined test world.

| Item | PR | Requirements | Left |
|---|---|---|---|
| Shared fire chemistry | [#188](https://github.com/west3436/zas-cubed/pull/188) | FIRE-030; FIRE-005's CO share | None |
| Reactions | [#190](https://github.com/west3436/zas-cubed/pull/190), [#193](https://github.com/west3436/zas-cubed/pull/193), [#196](https://github.com/west3436/zas-cubed/pull/196) | FIRE-001 to FIRE-006, FIRE-031; API-019, API-020 | None |
| Vanilla fire and fuels | [#194](https://github.com/west3436/zas-cubed/pull/194), [#197](https://github.com/west3436/zas-cubed/pull/197) | FIRE-020 to FIRE-022, FIRE-024, FIRE-025, FIRE-027; HEAT-007; CONT-067 | ~~Fire can still burn away a block whose cell lacks O₂ at a zone's edge (M4 skipped).~~ *`FireBlockMixin` has had M4 since #194; it has no GameTest of its own.* Vanilla's fire-spread check and campfire lighting aren't hooked |
| Hotspots and the gas fire | [#191](https://github.com/west3436/zas-cubed/pull/191), [#202](https://github.com/west3436/zas-cubed/pull/202), [#206](https://github.com/west3436/zas-cubed/pull/206) | FIRE-010, FIRE-011, FIRE-013, FIRE-016 to FIRE-019; DATA-009's hotspot part; CLIENT-013, after the owner's look at the gas fire (2026-11-04) | None |
| Fire API | [#203](https://github.com/west3436/zas-cubed/pull/203) | API-031; API-055 to API-058 added, accepted by the owner and *Implemented* | None |
| Ignition sources | [#189](https://github.com/west3436/zas-cubed/pull/189) | FIRE-014, FIRE-015; API-053 added, accepted by the owner and *Implemented* | None |
| Igniter | [#192](https://github.com/west3436/zas-cubed/pull/192) | CONT-105, FIRE-006's GameTest | None |
| Fire alarm | [#199](https://github.com/west3436/zas-cubed/pull/199) | CONT-044 | None |
| Fire extinguisher | [#195](https://github.com/west3436/zas-cubed/pull/195), [#198](https://github.com/west3436/zas-cubed/pull/198), [#204](https://github.com/west3436/zas-cubed/pull/204) | CONT-037; D-74 (its sprite stays at vg's 32 × 32) | ~~No recipe yet, so creative only.~~ *Recipe added in [#221](https://github.com/west3436/zas-cubed/pull/221), for the owner to approve.* No spray particle or in-use pose |
| S-03 and S-04 GameTests | [#205](https://github.com/west3436/zas-cubed/pull/205) | S-03, S-04 | None |
| `geo_010` flake | [#207](https://github.com/west3436/zas-cubed/pull/207) | None | Closes [#159](https://github.com/west3436/zas-cubed/issues/159) |

The GameTests are `FireStationTests.s_03_gas_leak_in_a_sealed_workshop`,
`FireScenarioTests.s_04_room_fire_suffocates_and_rekindles_under_hardcore` and
`s_04_room_fire_stays_vanilla_under_normal`. FIRE-012 stays in R2.

*2026-11-04: S-11 and S-13 pass on main.* The scope was photosynthesis, reactions
and failure in containers, pipe bursts, valves, pumps, the filter and mixer, the
scrubber, the passive vent, the portables, the tank transfer valve, two fire
follow-ups and the extinguisher's recipe. The compressor and electrolyzer, the
rest of [#49](https://github.com/west3436/zas-cubed/issues/49), open pipe ends and GUI textures stayed out. The owner left the
question from [#211](https://github.com/west3436/zas-cubed/issues/211) of how far hot fires burn to the coordinator's judgement,
which gave FIRE-035: a gas fire at or above its fuel's autoignition point skips
FIRE-030's 14 % O₂ limit and keeps the 10 kPa floor. It makes S-03's fire much
hotter, so it stays *Draft* until the owner has seen it. Every PR merged once CI
passed. The owner's in-game checks come afterwards, in one combined test world.

| Item | PR | Requirements | Left |
|---|---|---|---|
| Zone rebuild flake | [#214](https://github.com/west3436/zas-cubed/pull/214) | None | Closes [#209](https://github.com/west3436/zas-cubed/issues/209) |
| Gas fire's look | [#215](https://github.com/west3436/zas-cubed/pull/215) | CLIENT-013 *Implemented* after the owner's check | Closes [#31](https://github.com/west3436/zas-cubed/issues/31) |
| Photosynthesis and S-11 | [#229](https://github.com/west3436/zas-cubed/pull/229) | CONT-053, CONT-057, S-11 | A greenhouse's empty start and the night rate are for review |
| Container reactions | [#220](https://github.com/west3436/zas-cubed/pull/220) | CONT-100 | Tanks in chests, on mobs and in item frames don't tick, so they don't react or fail until carried |
| Tank failure | [#222](https://github.com/west3436/zas-cubed/pull/222) | CONT-038, CONT-107, CONT-109; API-059 added as *Draft* | A blast from a carried tank gives no kill credit. Block pressure damage is all that OPS-007 still waits on |
| Canister failure | [#233](https://github.com/west3436/zas-cubed/pull/233) | CONT-085, CONT-101, CONT-102 | CONT-085's 1,273 K means the air around a canister, not the gas inside (dated note). No GameTest covers a canister in a paused or unresolved cell |
| Pipe bursts | [#223](https://github.com/west3436/zas-cubed/pull/223) | CONT-108 | A burst comes one tick late |
| Valves | [#225](https://github.com/west3436/zas-cubed/pull/225); a fix in [#231](https://github.com/west3436/zas-cubed/pull/231) | CONT-010, CONT-075 | A redstone valve at a chunk's edge can still bring the far chunk back, through the game's own neighbour updates. An unreproduced pipe report at a chunk's edge ([#226](https://github.com/west3436/zas-cubed/issues/226)) |
| Pumps | [#231](https://github.com/west3436/zas-cubed/pull/231), [#240](https://github.com/west3436/zas-cubed/pull/240) | CONT-011, CONT-076, CONT-012 | CONT-070 *Implemented* after the owner's check, with no code. A volume pump has no stall, so it can drive a line until it bursts |
| Filter and mixer | [#217](https://github.com/west3436/zas-cubed/pull/217), [#237](https://github.com/west3436/zas-cubed/pull/237) | CONT-013, CONT-077 | None. Mirrored versions came later, as items of their own (D-77) |
| Scrubber and the controller's parts | [#216](https://github.com/west3436/zas-cubed/pull/216), [#227](https://github.com/west3436/zas-cubed/pull/227), [#234](https://github.com/west3436/zas-cubed/pull/234) | CONT-015, CONT-079 to CONT-081, CONT-041, CONT-064, CONT-072 | CONT-049 *Implemented* after the owner's check. A lone scrubber can't empty a room, and stalls a controller in Cycle. A vent loses gas if stepped on a busy port, which the schedule never does |
| Passive vent | [#236](https://github.com/west3436/zas-cubed/pull/236) | CONT-016 | None |
| Portables | [#218](https://github.com/west3436/zas-cubed/pull/218), [#230](https://github.com/west3436/zas-cubed/pull/230), [#235](https://github.com/west3436/zas-cubed/pull/235) | CONT-019, CONT-087, CONT-018 | The portable scrubber has no outlet. The portable pump's siphon has no rate limit. A failing tank in a portable isn't tested |
| Tank transfer valve and S-13 | [#224](https://github.com/west3436/zas-cubed/pull/224), [#238](https://github.com/west3436/zas-cubed/pull/238) | CONT-103, CONT-104, S-13's GameTest | Nothing tells the player the chamber must lose its air first |
| Fire follow-ups | [#219](https://github.com/west3436/zas-cubed/pull/219), [#228](https://github.com/west3436/zas-cubed/pull/228), [#241](https://github.com/west3436/zas-cubed/pull/241) | FIRE-032, FIRE-033, FIRE-034 and FIRE-035, all *Draft*, closing [#211](https://github.com/west3436/zas-cubed/issues/211) and [#212](https://github.com/west3436/zas-cubed/issues/212); dated notes on FIRE-030 and D-49 | Large hot zones aren't benchmarked, so check them at S-10. A very hot sealed room can relight as a gas fire burning its CO |
| Extinguisher recipe and filled canisters | [#221](https://github.com/west3436/zas-cubed/pull/221) | CONT-086 | The owner's approval of the recipe |
| Server stops | [#239](https://github.com/west3436/zas-cubed/pull/239), [#243](https://github.com/west3436/zas-cubed/pull/243) | NFR-015 added as *Draft* | Fires no longer pull chunks back up. The stop hang stays open ([#232](https://github.com/west3436/zas-cubed/issues/232), [#242](https://github.com/west3436/zas-cubed/issues/242)), with a watchdog to report it |

The GameTests are `PhotosynthesisTests.s_11_hardcore_greenhouse_forms_a_zone_and_lit_crops_make_o2`,
`s_11_normal_greenhouse_stays_unzoned` and `ToxinsLabTests.s_13_toxins_lab`.

*2026-11-05: the owner played this round's world.* 24 of its 28 checks passed,
which settles CONT-049, CONT-070, S-13's manual playtest and the O₂ pace. The four
it flagged were changed the same day. None of those changes has run on a client
yet, so each goes in the owner's second combined world. The owner also asked why a
running pressure pump showed no flow where a volume pump did: a new one's target
is one atmosphere, so in 1 atm air it has nothing to do. A GameTest now covers
this, and pumps didn't change.

| Flagged | PR | Change |
|---|---|---|
| Air sounds outlasted their particles | [#269](https://github.com/west3436/zas-cubed/pull/269) | Air loops follow the particles' thresholds, and clients hear of changes within 4 ticks. A passive vent gains particles |
| Portables lagged when switched | [#268](https://github.com/west3436/zas-cubed/pull/268) | The model and running sound change at the press. Gas and heat still move only in the normal step |
| A tank's leak hissed each second | [#272](https://github.com/west3436/zas-cubed/pull/272) | One hiss as a leak starts |
| Tanks went on a TTV only by right-click | [#273](https://github.com/west3436/zas-cubed/pull/273) | Two tank slots on its screen. Start in creative now uses the TTV up |

*2026-11-05: sound and particles on main.* The scope was airflow particles and
sound, sounds for every device, gas fire and gas blast sounds, and particles for
vents, scrubbers, leaks, bursts, open pipe ends and the extinguisher. The sounds
are vgstation13's oggs, chosen from how vg uses them
([vg-sounds](research/vg-sounds.md)), and the particles are vanilla sprites tinted
in neutral colours. GUI textures, display units, language, the rest of CLIENT-004
and gas-coloured particles stayed out. The owner set the main rules: airflow's
loudness follows moles per second and its pitch the pressure difference, a
listener in a vacuum hears no airflow, and only this round's sounds fade in thin
air. Every PR merged once CI passed. CI has no client, so nothing here has been
heard or seen, and every *Draft* ID above waits on the owner's combined test
world.

*2026-11-05: the owner played that world.* 39 of its 42 checks passed, on main at
666e793, which also tried the S-11/S-13 fixes above on a client for the first time.
That settles CLIENT-003 and CLIENT-010, keeps airflow's loudness rule and weak leaks
as built, and keeps a scrubber on 1 kPa silent. The rest of the table's checks passed,
but its *Draft* IDs still wait for the owner to accept them. The three it flagged were
changed the same day, and the owner chose to skip a retest, so nobody has seen or
heard those. Save and Quit was marked as passing, but the log shows the world was
never left and reopened, so that one is untested on a client.

| Flagged | PR | Change |
|---|---|---|
| The recharger's screen and its sound | [#277](https://github.com/west3436/zas-cubed/pull/277) | No screen: right-click a battery in or out. Plays vg's own `machines/charge_finish`, credited to vgstation13 contributors by the owner's decision |
| A leak's jet looked twitchy | [#276](https://github.com/west3436/zas-cubed/pull/276) | A leak chooses one direction as it starts, and keeps it |
| No whoomp heard from two rooms lit in turn at a distance | [#275](https://github.com/west3436/zas-cubed/pull/275) | A whoomp at least 0.3 louder plays through the 5 s limit. The small room was out of range by design, and likely silenced the large one. Unproven |

*2026-11-05: the owner accepted every decision left open by this round and by S-11/S-13.*
Each went as recommended or as built:

| Decision | Result |
|---|---|
| The *Draft* IDs | FIRE-032 to FIRE-037, API-059 to API-061, CONT-125 to CONT-128, CLIENT-015 to CLIENT-021, DATA-017 to DATA-019 and NFR-015 are accepted. All are Implemented except four: FIRE-036, CLIENT-018 and CONT-127 wait for someone to hear or see the changes above, and CLIENT-015 for a Save and Quit on a client |
| The extinguisher's recipe | Approved as shipped |
| S-13's strength | Stays at power 1.9 |
| Siphoning on real power ([#245](https://github.com/west3436/zas-cubed/issues/245)) | A Siphon mode on the controller, [#279](https://github.com/west3436/zas-cubed/pull/279). Nobody has seen its button yet |
| A stop hanging on a server running behind ([#242](https://github.com/west3436/zas-cubed/issues/242)) | Left as it is. NFR-015's watchdog logs the cause if it comes back, and the issue stays open |
| The portable pump's siphon | Capped at 150 L of the air a second, as a release is, [#280](https://github.com/west3436/zas-cubed/pull/280) |
| A lone scrubber stalling (CONT-081) | Left as written, to revisit if players run into it |
| Sound files left out over provenance | They stay out |
| A link for `misc/fire_alarm` | None to add, so its credit keeps its gap |

| Item | PR | Requirements | Left |
|---|---|---|---|
| Sound files | [#251](https://github.com/west3436/zas-cubed/pull/251), [#254](https://github.com/west3436/zas-cubed/pull/254) | CLIENT-020, CLIENT-021 added as *Draft*; notes on NFR-041, NFR-045 | The owner's ear on each choice. No loop's join has been checked. The origin of `misc/fire_alarm` is recorded only as Freesound |
| Looping sounds and thin air | [#252](https://github.com/west3436/zas-cubed/pull/252), [#257](https://github.com/west3436/zas-cubed/pull/257), [#258](https://github.com/west3436/zas-cubed/pull/258) | CLIENT-015, CLIENT-016, DATA-019, API-060, API-061 added as *Draft* | None of it has run on a client |
| Device activity | [#255](https://github.com/west3436/zas-cubed/pull/255) | DATA-017, DATA-018 added as *Draft* | DATA-006 has to be measured with this included |
| Airflow | [#248](https://github.com/west3436/zas-cubed/pull/248), [#250](https://github.com/west3436/zas-cubed/pull/250), [#256](https://github.com/west3436/zas-cubed/pull/256) | CLIENT-003, CLIENT-010 stay *Accepted*; dated notes on CLIENT-010, DATA-015 | The owner's check, and whether a big room should be louder than moles per second makes it. DATA-015's bench |
| Particles | [#247](https://github.com/west3436/zas-cubed/pull/247), [#249](https://github.com/west3436/zas-cubed/pull/249), [#253](https://github.com/west3436/zas-cubed/pull/253), [#260](https://github.com/west3436/zas-cubed/pull/260) | CLIENT-017 to CLIENT-019 added as *Draft*; note on DATA-006 | The art half of [#164](https://github.com/west3436/zas-cubed/issues/164). No bench of the jet cap |
| Pipe device sounds | [#263](https://github.com/west3436/zas-cubed/pull/263) | CONT-125, CONT-126 added as *Draft* | The owner's check |
| Other device sounds | [#262](https://github.com/west3436/zas-cubed/pull/262), [#264](https://github.com/west3436/zas-cubed/pull/264), [#265](https://github.com/west3436/zas-cubed/pull/265) | CONT-127, CONT-128 added as *Draft* | The owner's check. The generator's loop has never run |
| Fire and blast sounds | [#259](https://github.com/west3436/zas-cubed/pull/259), [#261](https://github.com/west3436/zas-cubed/pull/261) | FIRE-036, FIRE-037 added as *Draft* | The owner's check. `fire.loop` is a quiet crackle, as vg's gas fire is silent |
| Four fire test flakes | [#266](https://github.com/west3436/zas-cubed/pull/266) | None | None |

*2026-11-06: the R1 parallel round on main.* Six tracks ran in parallel, followed by
fault isolation and the integrity fuzz. The scope was ambient providers (#66), the
gas-handler capability and seal providers (#60), dimension pause and region reset
(#67), default block overrides and moving-block geometry (#65), block damage (#45)
and most of #69, plus GEO-035 and DATA-001. S-09, S-10, the rest of #49 and #51,
#200 and #179 stayed out. Every PR merged once CI passed. CI has no client, so
nothing here has been seen in play, and the round's decisions and its combined test
world are with the owner. That world also carries FIRE-036, CLIENT-015, CLIENT-018
and CONT-127, which stay *Accepted* until it passes.

NFR-010's fuzz found five gas bugs and a room that kept venting after its way out
closed, and the exploit checklist for NFR-012 found one more gas bug. All are fixed.
Seeds 1 to 40 pass locally in both halves, and 20 seeds of each passed on CI by hand.
With each of the five gas fixes reverted in turn, the fuzz fails on the gas total at
the operation that first caught it. One limit stays, in DATA-008's note: a block
change that divides a zone between a chunk's last published tick and the next can
make or lose gas. The fuzz excuses exactly that event, up to the larger of the zone's
loss and the share written, so a real bug smaller than that, in a zone that has just
divided after an unload, would go unseen. The fuzz resets only zones with no
neighbour, because OPS-013's rule and GEO-032's audit disagree beside one.

| Item | PR | Requirements | Left |
|---|---|---|---|
| Dimension pause and region reset | [#292](https://github.com/west3436/zas-cubed/pull/292), [#300](https://github.com/west3436/zas-cubed/pull/300) | OPS-005, OPS-013 | The owner's decision on what a reset leaves beside a neighbouring zone |
| Devices beside a paused zone | [#283](https://github.com/west3436/zas-cubed/pull/283) | GEO-035 | None |
| Saved worlds and live config | [#295](https://github.com/west3436/zas-cubed/pull/295), [#302](https://github.com/west3436/zas-cubed/pull/302) | DATA-001, OPS-016 | None |
| Ambient providers | [#287](https://github.com/west3436/zas-cubed/pull/287), [#291](https://github.com/west3436/zas-cubed/pull/291), [#293](https://github.com/west3436/zas-cubed/pull/293) | ENV-004, ENV-006, ENV-009, ENV-013, ENV-014, ENV-015, ENV-017 to ENV-020 | ENV-014's seeded source is tested through a test-only ambient, as real seeding is R2 |
| Gas handlers and worn filters | [#285](https://github.com/west3436/zas-cubed/pull/285), [#296](https://github.com/west3436/zas-cubed/pull/296) | API-021, API-022, API-025, API-026 | None |
| What seals | [#290](https://github.com/west3436/zas-cubed/pull/290), [#298](https://github.com/west3436/zas-cubed/pull/298), [#301](https://github.com/west3436/zas-cubed/pull/301), [#303](https://github.com/west3436/zas-cubed/pull/303), [#307](https://github.com/west3436/zas-cubed/pull/307), [#308](https://github.com/west3436/zas-cubed/pull/308) | API-023, API-028, GEO-006, GEO-009, GEO-013, GEO-014, GEO-021, GEO-027, GEO-031, GEO-042, GEO-043 | None |
| Block damage | [#299](https://github.com/west3436/zas-cubed/pull/299) | FLOW-007, FLOW-015, FLOW-016, OPS-002 | None |
| Fault isolation | [#309](https://github.com/west3436/zas-cubed/pull/309), [#310](https://github.com/west3436/zas-cubed/pull/310) | NFR-014. API-062 added as *Draft*. NFR-013 stays *Accepted* | Hazard handlers ([#35](https://github.com/west3436/zas-cubed/issues/35)), which NFR-013 also guards |
| Compatibility and checks | [#282](https://github.com/west3436/zas-cubed/pull/282), [#284](https://github.com/west3436/zas-cubed/pull/284), [#286](https://github.com/west3436/zas-cubed/pull/286), [#288](https://github.com/west3436/zas-cubed/pull/288), [#289](https://github.com/west3436/zas-cubed/pull/289), [#294](https://github.com/west3436/zas-cubed/pull/294), [#306](https://github.com/west3436/zas-cubed/pull/306) | NFR-011, NFR-012, NFR-020, NFR-022, NFR-024, NFR-030. NFR-025 stays *Accepted* | One client launch with Sodium for NFR-025. NFR-012 repeats before each release |
| Integrity fuzz | [#313](https://github.com/west3436/zas-cubed/pull/313), [#317](https://github.com/west3436/zas-cubed/pull/317) | NFR-010 | The nightly schedule has not been seen firing yet |
| Bugs the checks found | [#304](https://github.com/west3436/zas-cubed/pull/304), [#311](https://github.com/west3436/zas-cubed/pull/311), [#312](https://github.com/west3436/zas-cubed/pull/312), [#314](https://github.com/west3436/zas-cubed/pull/314), [#315](https://github.com/west3436/zas-cubed/pull/315), [#316](https://github.com/west3436/zas-cubed/pull/316) | None | DATA-008's limit, below |
| CI and test upkeep | [#297](https://github.com/west3436/zas-cubed/pull/297), [#305](https://github.com/west3436/zas-cubed/pull/305) | None | None |

*2026-11-06: the owner played that world.* All 13 steps passed, with no notes, on main
at 9b2920b, whose code is the 54271bb the world was built on. It took about 3 minutes,
not the plan's 9. That moves FIRE-036, CLIENT-015, CLIENT-018 and CONT-127 to
*Implemented*, and the log shows the world left by Save and Quit and reopened. Windows
blowing out and the controller's seven mode buttons passed too. The round's decisions,
including accepting API-062, are still with the owner.

*2026-11-06: the owner settled the round's decisions.*

| Decision | Result |
|---|---|
| A crash restoring more gas than went in ([#304](https://github.com/west3436/zas-cubed/pull/304)) | A known limit |
| A block change dividing a zone just before its chunk saves (DATA-008) | A known limit, with no follow-up. The fuzz keeps excusing exactly that event |
| Gas in flight when an engine faults (NFR-014) | Voided and recorded |
| An ambient provider that throws (NFR-013) | It keeps its last good answer, [#321](https://github.com/west3436/zas-cubed/pull/321). With none yet, as after a restart, the next source applies |
| `/zas reset` on a pinned zone (OPS-013) | It resets and loses its pins |
| `/zas reset` beside a neighbouring zone (OPS-013) | The rule stays, so the neighbour can drain into a sealed room until something changes beside it. The fuzz resets only zones with no neighbour |
| A waterlogged block with a shipped override (GEO-006) | Water wins, [#323](https://github.com/west3436/zas-cubed/pull/323). A pack's own override keeps the last word. Checked by GameTests; not tried in game |
| Two layers of glass holding at any pressure (FLOW-007) | They stay as they are. A reinforced glass block is [#320](https://github.com/west3436/zas-cubed/issues/320) |
| A border moving in two or more blocks in one atmos tick (GEO-031) | As built: the far part of a zone and its gas leave |
| `ILifeSupport` and `IInternalSupply` (NFR-013) | They join NFR-013's list, guarded with the hazard handlers, [#35](https://github.com/west3436/zas-cubed/issues/35) |
| Gas handlers (NFR-013) | They count as third-party code |
| API-062 | Accepted, and *Implemented* |
| A sealed room beside a moving ambient (ENV-017) | Counted as walls conducting, so ENV-017 stays *Implemented* |
| ENV-014's seeded source | Stays *Implemented*, tested through a test-only ambient until real seeding in R2 |
| NFR-012 after a pass not on a release commit | Stays *Implemented*. The pass repeats before each release |
| NFR-025's client launch with Sodium | Before release. NFR-025 stays *Accepted* until then |
| OPS-007 ([#37](https://github.com/west3436/zas-cubed/issues/37)) | Finished, [#324](https://github.com/west3436/zas-cubed/pull/324), and *Implemented* |
| `zas-atmos` re-running the `zas` GameTests | Nightly only, [#325](https://github.com/west3436/zas-cubed/pull/325). The per-PR step went from about 7 m 18 s to about 5 m 15 s |
| How often a zone's way out is checked again (GEO-023) | At most every 2 atmos ticks, not 8, [#326](https://github.com/west3436/zas-cubed/pull/326). A PR's fuzz comes to 60 to 67 s, and the cost in game isn't measured |
| The ledger's unused `DEVICE` category | Removed, [#322](https://github.com/west3436/zas-cubed/pull/322): devices record as `API` |

*2026-11-06: a status audit.* Each of the 70 requirements still *Accepted* for R0 or R1
was compared with main at 61c29d8: its notes and Verify plan against the code and tests,
which were read, not rerun. Only API-032 moved to *Implemented*. Finding a requirement's ID
in the code and tests says little: many of the others have both, and still lack a part or
the check their Verify plan asks for.

| Result | Requirements |
|---|---|
| *Implemented* now | API-032 |
| Waiting for the owner to check them in game | CLIENT-008, CLIENT-014, DATA-007 |
| Can only be true at release | API-002, API-008, API-041, API-043, NFR-025, NFR-033, NFR-040, NFR-042 |
| Not built | CORE-029, FLOW-005, LIFE-032, LIFE-034, LIFE-047, API-030, API-033, NFR-034, CLIENT-009, CONT-033 to CONT-036, CONT-055, CONT-056, CONT-088, CONT-089, CONT-091, CONT-115 to CONT-124 |

| Partly built | What's missing |
|---|---|
| CORE-052 | The timed collection for sealed zones, its config key and its GameTest |
| LIFE-022 | The GameTest with a submerged player drowning in a zone |
| DATA-005 | The GameTest recording what a player is sent |
| DATA-006, DATA-014, DATA-015 | The Bench figures |
| DATA-012 | Saved fixtures and the old-world GameTest, which need a release to exist. `DimensionData` also drops its contents on a version it doesn't know, and `zas_atmos` block entity data has no version |
| DATA-016 | The GameTest saving and reloading one whole station, which goes with S-09 |
| API-005 | A build that fails on a missing javadoc comment |
| API-007 | The GameTest with a consumer that bundles the API |
| API-042 | Any check or written policy for deprecation. `apiCompat` doesn't look at it |
| API-047 | The example addon's main code uses none of the extension points |
| CONT-031, CONT-090 | The hardsuit's helmet and chestplate |
| CONT-050 | The electrolyzer route, and a test that obtains gas by each route |
| CONT-051, CONT-058 | 17 recipes wait for items that aren't built, and the central controller's row has no recipe |
| CONT-060, CONT-065 | The electrolyzer and central controller. CONT-060 also lacks a test that cuts the volume pump's power |
| CLIENT-001 | The sealed-head and analyzer triggers |
| CLIENT-006 | °C as the default, and units in device GUIs, tooltips and the analyzer |
| CLIENT-007 | The build check, and hard-coded text left in some screens |
| NFR-001, NFR-002, NFR-004, NFR-005 | The full S-10, on D-40 hardware |
| NFR-013 | Hazard handlers, and guards on `ILifeSupport` and `IInternalSupply` |
| NFR-031 | A player mechanics doc and a changelog |
| NFR-032 | S-09 with pipes and canisters, and S-10 |

Later that day the owner narrowed CORE-052 to sealed zones and removed the air compressor
(CONT-054, now *Rejected*). These tables already show both.

*2026-11-06: the audit round.* Seven tracks built what the audit listed that needs no new
content, alongside the GUI work ([#349](https://github.com/west3436/zas-cubed/pull/349)).

| Item | PR | Requirements | Left |
|---|---|---|---|
| Sealed zones dissolving, and zone events | [#346](https://github.com/west3436/zas-cubed/pull/346), [#351](https://github.com/west3436/zas-cubed/pull/351) | CORE-052, API-030 | None |
| Displaced gas | [#333](https://github.com/west3436/zas-cubed/pull/333) | CORE-029 | None |
| Saved operations keep their sub-cell | [#332](https://github.com/west3436/zas-cubed/pull/332) | None | None |
| Open-air gas under test-driven atmos ticks | [#338](https://github.com/west3436/zas-cubed/pull/338) | None | Two older tests, `cont_014_a_vent_vents_to_the_ambient_in_an_open_cell` and `cont_015_a_scrubber_in_an_open_cell_takes_from_the_ambient`, still run in real time, with comments that are now wrong |
| Hazard handlers, hazard events and guards | [#344](https://github.com/west3436/zas-cubed/pull/344), [#337](https://github.com/west3436/zas-cubed/pull/337), [#334](https://github.com/west3436/zas-cubed/pull/334) | LIFE-032, API-033, NFR-013 | The owner's ruling on a suit whose code throws |
| Drowning, and internals under water | [#336](https://github.com/west3436/zas-cubed/pull/336) | LIFE-022, LIFE-047 | A look in game, in the world below |
| Airflow impact damage | [#348](https://github.com/west3436/zas-cubed/pull/348) | FLOW-005 | A look in game, in the world below |
| Spawning in lethal air | [#339](https://github.com/west3436/zas-cubed/pull/339) | LIFE-034 | The natural spawner hasn't run end to end, as a GameTest has no player in the level |
| Forward migration | [#335](https://github.com/west3436/zas-cubed/pull/335) | DATA-012 stays *Accepted* | Saved fixtures and the old-world GameTest, which need a release. Item data, the player's internals switch and the primed transfer valve save no version |
| What a player is sent | [#340](https://github.com/west3436/zas-cubed/pull/340) | DATA-005 | None |
| Repeat power charges | [#345](https://github.com/west3436/zas-cubed/pull/345) | None | None |
| Pipes across a chunk border | [#341](https://github.com/west3436/zas-cubed/pull/341) | None | None |
| Javadoc, nested API jars and deprecation | [#342](https://github.com/west3436/zas-cubed/pull/342), [#347](https://github.com/west3436/zas-cubed/pull/347), [#343](https://github.com/west3436/zas-cubed/pull/343) | API-005, API-007. API-042 stays *Accepted* | API-042's check compares nothing until `api_baseline_version` names a release |
| S-10 regression check | [#350](https://github.com/west3436/zas-cubed/pull/350) | NFR-034 | None |
| A volume pump losing power | [#331](https://github.com/west3436/zas-cubed/pull/331) | CONT-060 stays *Accepted* | The electrolyzer and central controller |
| Example addon | [#354](https://github.com/west3436/zas-cubed/pull/354) | API-047 | No test has ZAS³ call `OutpostAir`, fill the tank by pipe or give a player air from the oxygen tank, as a GameTest server has only vanilla's dimensions, no content mod and no player. `OutpostAir` was checked once on a dedicated server |

Every requirement above moved to *Implemented* unless the table says otherwise, and
none of them needed a new ID. Of the audit's tables, *Not built* now holds only
CLIENT-009 and the CONT items. From *Partly built*, this round finished CORE-052,
LIFE-022, DATA-005, API-005, API-007, API-047 and NFR-013.

The combined test world, **ZAS R1 Audit Round Test**, was built on main at b90f959e, whose
mod code #354 doesn't change. It covers the HUD alerts, a dive on internals, the airflow
fling and removing the mods. CLIENT-008, CLIENT-014 and DATA-007 stay *Accepted* until it
passes. The round's decisions are with the owner.

*2026-11-06: the owner played that world.* Every section passed, with no notes, on main at
88cbdf6, whose mod code is the b90f959 the world was built on. The logs show about 4
minutes in the world, not the plan's 8, and under a minute in the copy with no mods. That
moves CLIENT-008, CLIENT-014 and DATA-007 to *Implemented*. The dive and the fling looked
right too.

*2026-11-06: the owner settled the round's decisions.*

| Decision | Result |
|---|---|
| A suit whose code throws (NFR-013) | No protection while it throws, as built, unlike an ambient provider |
| What zone events carry (API-030) | A `ZoneId`, as built. The architecture is reworded, and a snapshot can be added later |
| Saved data with no version (DATA-012) | Item data, the player's internals switch and the primed transfer valve get a version before the first release |
| A newer `DimensionData` (DATA-012) | Read by name with a warning, as built |
| The air meter on internals under water (LIFE-047) | It refills, as built |
| Airflow impact's size and knockback (FLOW-005) | As built |
| Dissolving sealed zones (CORE-052) | As built: 60 s, joined zones going together, now in the statement, and CORE-006's tolerances. A fire that outlasts its zone is a known limit |
| When zone events fire (API-030) | As built, with the statement now saying `breached` waits for the opening's classification |
| Natural spawning in bad air (LIFE-034) | As built, including mobs that don't breathe spawning in a vacuum and dying there. The natural spawner still hasn't run end to end |
| Hazard handlers (LIFE-032) | As built. A check holding on an unresolved atmosphere (LIFE-048) stays untested with a handler registered |
| Hazard events (API-033) | As built |
| Data formats in the example addon (API-047) | They don't count, as `docs/datapacks.md` has an example of each |
| A window's sync (DATA-005) | `ControllerSync` and `CanisterSync` are named on its list |
| API-042's status | Stays *Accepted* until a release exists for the check to compare, with the policy as built |
| S-10's regression check (NFR-034) | As built, comparing means only |
| The nested-jar test's CI step (API-007) | It keeps its own step |
| Displaced gas (CORE-029) | As built |
| A valve at a chunk's edge rebuilding twice per atmos tick ([#341](https://github.com/west3436/zas-cubed/pull/341)) | As built, and measured with S-09 |
| The audit's stale comments, the two real-time tests left from [#338](https://github.com/west3436/zas-cubed/pull/338), and CONTRIBUTING's checks | Next, as a small PR |

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
| `example-addon` | api | Usage examples for consumers (API-047) |

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

_Updated 2026-11-06._

1. **Owner decisions:**
   - ~~NFR-004: accept or reject the per-cell memory budget revised to 256 B in
     #117~~ *Accepted 2026-11-01.*
   - ~~review the [architecture](architecture.md)
     ([#2](https://github.com/west3436/zas-cubed/issues/2)) and accept it or
     send changes~~ *Accepted 2026-11-05, which closes Phase B.*
   - ~~from the R1 foundation: look at the boundary colours in the debug
     overlay (CLIENT-011)~~ *Checked by the owner 2026-11-01. The other
     foundation questions were decided the same day: see the table in
     [Phase E](#phase-e-r1-feature-tracks-in-parallel-once-r0-is-done).*
2. ~~**The R1 foundation**~~ *Done 2026-11-01* (#128, #130, #131, #133, plus
   #134 and #136 from #64; see [Phase E](#phase-e-r1-feature-tracks-in-parallel-once-r0-is-done)).
3. ~~**S-01 end to end**~~ *Done 2026-11-02* (#149, #151, #152, #154 to #156,
   #160, #161, #163; see [Phase E](#phase-e-r1-feature-tracks-in-parallel-once-r0-is-done)).
   Per-world preset copies (#139) went in with it.
4. **Widen out scenario by scenario:** ~~S-02, S-05 and S-08~~ *done
   2026-11-03 (#167 to #177, #180, #182, #183)*. ~~S-03 and S-04 (fire)~~
   *done 2026-11-03 (#188 to #199, #202 to #207; see
   [Phase E](#phase-e-r1-feature-tracks-in-parallel-once-r0-is-done))*.
   ~~S-11 and S-13~~ *pass on main 2026-11-04 (#214 to #243; see
   [Phase E](#phase-e-r1-feature-tracks-in-parallel-once-r0-is-done))*. The owner
   played its combined test world the next day.
   - **Last, together (owner, 2026-11-05):** S-09 and S-10, and the rest of #49
     and #51 (gas mask, hardsuit, boots, analyzer, wrench, electrolyzer).
   - **A later track of its own (owner, 2026-11-06):** the items not built yet,
     among them the analyzer, the central atmospherics controller
     ([#200](https://github.com/west3436/zas-cubed/issues/200)), station walls
     ([#179](https://github.com/west3436/zas-cubed/issues/179)) and reinforced glass
     ([#320](https://github.com/west3436/zas-cubed/issues/320)).
   - **What each open requirement still needs:** the 2026-11-06 status audit in
     [Phase E](#phase-e-r1-feature-tracks-in-parallel-once-r0-is-done), less what the
     audit round after it built.
   - ~~**The audit's items that need no new content**~~ *on main 2026-11-06 (#331 to
     #348, #350, #351, #354; see
     [Phase E](#phase-e-r1-feature-tracks-in-parallel-once-r0-is-done))*. The owner
     played its combined test world and settled its decisions the same day.
   - **Before the first release:** a version on item data, the player's internals
     switch and the primed transfer valve (DATA-012,
     [#58](https://github.com/west3436/zas-cubed/issues/58)).
   - ~~**Before them,** the gas-handler capability and seal providers (#60),
     ambient providers (#66), dimension pause and region reset (#67), default
     block overrides and moving-block geometry (#65), fuzzing and fault isolation
     (#69), and block damage (#45)~~ *on main 2026-11-06 (#282 to #317; see
     [Phase E](#phase-e-r1-feature-tracks-in-parallel-once-r0-is-done))*. The
     owner played its combined test world and settled its decisions the same day
     (#319 to #326). #69 stays open for NFR-025 and NFR-040.
   - ~~Sound and particles~~ *on main 2026-11-05 (#247 to #266; see
     [Phase E](#phase-e-r1-feature-tracks-in-parallel-once-r0-is-done))*. The owner
     played its combined test world the same day, and accepted its *Draft* IDs.
   - ~~From this round, for the owner: the *Draft* IDs, the extinguisher's recipe and
     a stop hanging on a server running behind (#242)~~ *accepted 2026-11-05*.
   - Carried over: art for open pipe ends (#164), GUI textures (#185),
     display units and language (#57), magnetic boots (#49) and insulating
     station walls (#179).
   - Follow-ups from R0: ~~saved operations keeping their sub-cell
     ([#122](https://github.com/west3436/zas-cubed/issues/122))~~ *fixed 2026-11-06
     (#332)*. Zone-creation
     speed and idle-zone cost, both inside their budgets, are now part of S-10's
     work ([#68](https://github.com/west3436/zas-cubed/issues/68)), and sub-cells
     of position-dependent shapes go with GEO-043
     ([#65](https://github.com/west3436/zas-cubed/issues/65)).
5. **After R1:** the reference integrations, Mekanism and Ad Astra
   (D-11, D-66; see [After R1](#after-r1-already-queued)).
