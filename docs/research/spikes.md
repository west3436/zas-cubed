# Phase B spikes: results

_Run 2026-10-31. The code is throwaway and is not merged ([#3](https://github.com/west3436/zas-cubed/issues/3) to [#8](https://github.com/west3436/zas-cubed/issues/8)). It lives on
the [`spikes/phase-b`](https://github.com/west3436/zas-cubed/tree/spikes/phase-b/spikes/)
branch, whose README explains how to rerun each spike._

**Machine:** Intel i7-13700K (24 hardware threads), 32 GB RAM, Windows 11,
Temurin/Oracle JDK 21.0.6. That machine is faster than the D-40 reference
(i5-12400 class). As a rule of thumb, multiply single-thread times by about 1.3
to 1.5 for the reference machine. In-game spikes ran in NeoForge 21.1.265
GameTest servers started by ModDevGradle 2.0.148.

## Summary

| # | Question | Answer | Status |
|---|---|---|---|
| S0 | Packaging | **Yes,** with three rules: name the API project `zas-api`, declare its jar-in-jar range through module coordinates, and keep it out of the mod's `mods {}` source sets. FML keeps one copy (the newest) and the fallback works. | Done in dev, in a jar-in-jar harness and on a real dedicated server ([#77](https://github.com/west3436/zas-cubed/issues/77)). |
| S1 | Change hook | `LevelChunk#setBlockState` sees everything `onBlockStateChange` sees, **plus** direct chunk writes. The enqueue cost is below measurement noise. | Done. The piston case was not exercised. |
| S2 | Seal masks for every state | 26,684 vanilla states in 0.62 s, or **0.12 s** when cached per `VoxelShape` instance. Only **287 unique geometries**, about 180 KiB. Panes and walls come out as sub-cells. | Done (vanilla only, no large pack) |
| S3 | Bounded fills | A 65,536-cell fill takes **5.5 ms**, about 12 M cells/s. With zone creation, 5.6 ms. | Done (headless) |
| S4 | Merge, split, local split | A connected edit costs **2 µs** (6 visits). A real 50k split takes 6.7 ms and a merge 0.15 ms. The worst-case detour is 4.9 ms. | Done (headless) |
| S5 | Off-thread pipeline | The protocol works. Threaded and inline runs are **bit-identical**, and conservation is exact (3e-14), but **only with** reject-on-unload (1.7e-3 drift without it). Server-thread cost is µs per atmos tick. | Done (headless, plus the in-game snapshot cost) |
| S6 | Ad Astra adapter | Not started | Blocked: it needs Ad Astra jars downloaded and run ([#9](https://github.com/west3436/zas-cubed/issues/9)) |

## S0 · Packaging

**Setup.** [`spikes/s0-packaging`](https://github.com/west3436/zas-cubed/tree/spikes/phase-b/spikes/s0-packaging) is a 4-project
build:

- `engine`: plain Java.
- `zas-api`: ModDevGradle, with a manifest of `FMLModType: GAMELIBRARY` and
  `Automatic-Module-Name: io.github.west3436.zas.api`.
- `zas`: a mod that bundles the engine classes in its jar and nests the API.
- `consumer`: a third-party-style mod that nests a published API at a
  *newer* version.

The API is a tiny service holder with a fallback (`ZasApi.get()`,
`isPresent()`, `install()`). Each mod has a GameTest that checks which API copy
and which implementation it sees.

**Runs.**

| Run | Result |
|---|---|
| `:zas:runGameTestServer` (dev, from source) | Pass. The engine classes load inside the `zas` module; the API loads as its own module. |
| `:consumer:runGameTestServer` (dev, without zas) | Pass. `isPresent=false` and the fallback answers 101.325 kPa (the S-12 shape). |
| Harness with **both built jars** loaded as mod files | Pass. **One** API copy, 0.2.0, taken from the consumer's jar; both mods see `impl=zas`. |
| Harness with only the consumer jar | Pass. Fallback, API 0.2.0. |
| Harness with only the zas jar | Pass. API 0.1.0 from zas's jar. |

The harness (`:harness`) puts the built jars on the dev runtime classpath as
mod files, not source sets. FML's jar-in-jar locator and `JarSelector` then
choose between the nested copies, which is the code path production uses.

**Pitfalls found** (each one is a rule in the [architecture](../architecture.md#2-modules-and-packaging)):

1. **Artifact identity comes from the Gradle project name.** For a project
   dependency, ModDevGradle writes the nested jar's identifier as
   `group:<project name>`. A project named `api` produced
   `io.github.west3436:api`, while the published artifact is
   `io.github.west3436:zas-api`
   ([evidence](https://github.com/west3436/zas-cubed/tree/spikes/phase-b/spikes/s0-packaging/evidence/zas-jarjar-metadata-before-fix.json)).
2. **A mismatched identity silently downgrades consumers.** With the two
   identities, FML's `JarSelector` logged *"Attempted to select two dependency
   jars from JarJar which have the same identification … Using [zas's]"* and
   loaded **0.1.0** in place of the consumer's 0.2.0, chosen by mod-file order
   and not by version. A consumer compiled against a newer API would hit
   `NoSuchMethodError`.
3. **Version ranges only come from module selectors.** ModDevGradle reads a
   jar-in-jar range from a `ModuleComponentSelector`'s version constraint. For
   a project dependency it writes `[version,)`. On Gradle 9, a `version {}`
   block inside `jarJar(project(...)) { }` resolves to *the project's*
   `version` setter, and produced a jar named after a closure. The fix is to
   request `io.github.west3436:zas-api` by coordinates with
   `strictly '[0.1,1.0)'`, then substitute the local project in the `jarJar`
   configuration.
4. **The dev classpath splits packages.** Listing the API source set in the
   mod's `mods {}` block, while the API jar is also on the classpath, crashed
   the loader with `ResolutionException: Modules io.github.west3436.zas.api and
   zas export package io.github.west3436.zas.api`
   ([log](https://github.com/west3436/zas-cubed/tree/spikes/phase-b/spikes/s0-packaging/evidence/gt-zas-split-package.log)). The
   engine is fine in `mods {}`: its jar has no module identity of its own on
   the dev classpath.

**Also confirmed:**

- A `GAMELIBRARY` jar can use Minecraft and NeoForge types (`Level`,
  `BlockPos`).
- Declaring `ordering = "AFTER"` on an optional `zas` dependency makes
  `isPresent()` true already during the consumer's mod construction.
- A GameTest server needs no `eula.txt`, so CI can run these as is.

**Not verified in the spike:** a real dedicated server built with the NeoForge
installer. Running the installer was blocked in that session. See the
follow-up below.

### Follow-up: real dedicated server (2026-10-31)

[#77](https://github.com/west3436/zas-cubed/issues/77), run on the Phase C
build from `main`, not the spike code.

**Method.**

- Server: `neoforge-21.1.265-installer.jar --installServer` (SHA-256 checked
  against the Maven `.sha256`), started with
  `@libraries/net/neoforged/neoforge/21.1.265/win_args.txt nogui` on JDK
  21.0.6, `view-distance=3`. Each run got a fresh `mods/` and world, then
  `stop` on stdin after `Done`.
- Jars: `zas`, `zas_atmos` and `zas_example` from `./gradlew build`, each
  nesting `zas-api` 0.1.0 (`zas_atmos` nests nothing). For "newest wins", a
  second `zas_example` was built in a scratch copy with
  `-Papi_version=0.1.1`, so it nests 0.1.1 with range `[0.1.1,1.0)`.
- Evidence: FML's `debug.log`, and `-Xlog:class+load` for the classes loaded.
  No probe code. The API is still empty, so these runs show which copy is
  selected, not the fallback or the provider (those need Phase D's API; the
  harness above covered them).

**Runs.** All passed: `Done (~3 s)`, a clean stop, no `ERROR` and no crash
report.

| Run | `mods/` | API copy selected |
|---|---|---|
| a | `zas`, `zas_atmos`, `zas_example` (API 0.1.1) | **One:** 0.1.1, from `zas_example`. Both copies were read; the newer won though `zas` was scanned first. |
| a0 | `zas`, `zas_atmos`, `zas_example` (both API 0.1.0) | One: 0.1.0, from `zas`. |
| b | `zas_example` alone (API 0.1.0) | 0.1.0, from `zas_example`. Starts without `zas`. |
| d | `zas`, `zas_atmos` | 0.1.0, from `zas`. |

Key lines from run a (timestamps cut):

```text
JarInJarDependencyLocator: Failed to load resource META-INF\jarjar\metadata.json from io.github.west3436.zas-api-0.1.1.jar, it does not contain dependency information.
JarInJarDependencyLocator: Failed to load resource META-INF\jarjar\metadata.json from io.github.west3436.zas-api-0.1.0.jar, it does not contain dependency information.
JarInJarDependencyLocator: Found 3 dependencies adding them to mods collection
ModDiscoverer/SCAN: Found gamelibrary file "io.github.west3436.zas-api-0.1.1.jar" [parent: zas_example-0.0.1.jar, locator: jarinjar, reader: mod manifest]
```

The two `Failed to load` lines are FML reading each nested copy (they nest
nothing further). Only one `zas-api` gamelibrary is then added; the other two
dependencies are NeoForge's own (`mixinextras`, `coremods`). There was no
`Attempted to select two dependency jars` warning, so the identity rule
(pitfall 1) holds in production. Runs a0, b and d show the same single line
with the expected version and parent.

**Client classes (NFR-023).** The class-load log has no `com.mojang.blaze3d`
class and one `net.minecraft.client` class, `LanServerPinger`, which is part
of the vanilla server jar and loads on a server with an empty `mods/` too. The
only `WARN` lines (*Assets URL … uses unexpected schema*) also appear on the
empty server.

## S1 · Change hook

**Setup.** [`spikes/s1s2-ingame`](https://github.com/west3436/zas-cubed/tree/spikes/phase-b/spikes/s1s2-ingame) has two mixins
that each enqueue the changed position into a primitive `long[]` queue:

- **A:** `LevelChunk#setBlockState(BlockPos, BlockState, boolean)` at RETURN,
  when the return value is non-null.
- **B:** `ServerLevel#onBlockStateChange` at HEAD.

A mixin config plugin switches each hook on or off for a run
(`-Phooks=none|chunk|chunk,poi`).

**Coverage.** Changes seen per source:

| Source | Hook A (chunk) | Hook B (POI) |
|---|---|---|
| `Level#setBlock`, flags 3 | 1 | 1 |
| `Level#setBlock`, flags 2\|16 | 1 | 1 |
| Direct `LevelChunk#setBlockState` (WorldEdit style) | **1** | **0** |
| `/fill` 3×3×3 | 27 | 27 |
| `/setblock` | 1 | 1 |
| `/place template minecraft:igloo/top` | 117 | 117 |
| Explosion (power 3, glass cube) | 117 | 117 |
| Door via `DoorBlock#setOpen` | 2 | 2 |
| Water spreading (40 ticks) | 25–37 | 25–37 |
| Piston | not exercised¹ | not exercised¹ |

¹ The piston setup never fired in the GameTest structure. Pistons move blocks
through `Level#setBlock` (moving-piston placement), so both hooks would see
them; a GameTest still needs to confirm this.

**Cost.** Best of 10 rounds of 16,384 writes, in ns per change:

| Hooks | `setBlock` flags 3 | flags 18 | Direct chunk write |
|---|---|---|---|
| none | 644 | 295 | 276 |
| A | 599 | 272 | 218 |
| A + B | 655 | 293 | 278 |

The differences are within run-to-run noise (about ±60 ns, in no consistent
direction). The enqueue-only hook adds no measurable cost.

**Coalescing.** Sorting and deduplicating 100,000 queued positions took 20 ms.
That is fine for normal edit rates, but too slow for an explosion burst within
the NFR-002 budget. **Decision for the architecture:** deduplicate at enqueue
time with per-section dirty bitsets (64 longs per section), so coalescing is
O(changes) with no sort.

**Conclusion.** Use hook A only (GEO-010). Hook B adds nothing. Reconciliation
on chunk load (GEO-013) uses the snapshot path measured in S5.

## S2 · Seal masks and volumes for every block state

**Method.**

- Each state's collision shape comes from
  `getCollisionShape(EmptyBlockGetter.INSTANCE, ZERO, CollisionContext.empty())`
  (GEO-041).
- The shape is rasterized to 16³ voxels.
- Each face's coverage is its 16×16 boundary layer.
- Free space reaching a face is split into 6-connected components, which are
  the sub-cells (GEO-004).
- A state counts as **positional** if its shape at (7, 0, 13) differs from its
  shape at the origin.

**Results (vanilla 1.21.1):**

| Measure | Value |
|---|---|
| States | 26,684 |
| Derive every state | 623 ms (23.4 µs per state) |
| Fetch shapes only | 3.3 ms |
| **Derive once per distinct `VoxelShape` instance** | **120–135 ms** (4,811 distinct instances) |
| Unique geometries | **287** |
| Memory | about 176 KiB (a 4-byte id per state plus the unique palette) |
| Empty / full / more than one sub-cell | 5,011 / 2,990 / 8,342 (walls dominate the last) |
| Positional (GEO-043) | 32 (bamboo and other offset plants) |
| Declaring `dynamicShape` | 179 |
| Failures | 0 |

**Shapes that check out:**

| Block | Result |
|---|---|
| Slab | 500 L. Bottom face sealed, top open, sides half covered. Double slab: full. |
| Stairs | 250 L (straight), 125 L (inner), 375 L (outer). *Corrected 2026-10-31: the labels were swapped; the production GameTest `geo_002_volumes` confirms these.* |
| Glass pane | 1 sub-cell (post only, 984 L) up to 4 (cross, 191 L each). A straight pane is 2 × 438 L, so a line of panes seals like a wall. |
| Iron bars | Same as panes (they need the GEO-009 permeable override) |
| Fence | 1 to 4 sub-cells. Its 1.5-block collision is clipped to the block, and it needs the GEO-009 override as well. |
| Walls | Up to 4 sub-cells, 94–313 L each |
| Snow layers | 875 L down to 125 L in eighths |
| Chest | 282 L with every face open: inset, so it leaks by design (D-44) |
| Door / trapdoor | 813 L; the closed face is sealed |
| Composter | Sealed on the sides and bottom, 492 L |
| Cauldron and hopper | 2 sub-cells: the inner basin reaches the top only |
| Leaves | Full collision, so sealed unless overridden. Glass and shulker boxes are full. |
| Moving piston and powder snow | Empty in an empty context. Both are GEO-042 cases (powder snow is entity-dependent). |
| Fluids | Empty collision, so they need the GEO-006 policy |

**Conclusions.**

- Derive geometry **once per distinct `VoxelShape` instance**, then map every
  state to an index into a small palette of unique geometries. A pack with 10×
  vanilla's states would take about 1 s. That is acceptable at server start,
  and it can run on a background thread because non-dynamic shapes are
  immutable.
- Masks at 16×16 resolution are exact for shapes on the 1/16 grid. A sub-cell
  needs its own per-face footprint when a geometry has more than one sub-cell,
  so stacked panes connect sub-cell to sub-cell.
- Not measured: a large modpack ([#79](https://github.com/west3436/zas-cubed/issues/79)). The per-shape cost suggests it scales
  with distinct shapes, not with states.

## S3 · Bounded fills

**Setup.** [`spikes/engine-bench`](https://github.com/west3436/zas-cubed/tree/spikes/phase-b/spikes/engine-bench) (`GeoBench`) runs
a breadth-first fill over decoded section arrays (`short[4096]` geometry ids per
section). It applies the union-coverage seal test with 8×8 masks, uses an
open-addressing `long` visited set, and caches the last section looked up.

| Case | Outcome | Median | p99 |
|---|---|---|---|
| Sealed room, 65,536 cells | ENCLOSED | 5.5 ms | 6.6 ms |
| Room breached to open air | OPEN at the cap | 6.6 ms | 8.3 ms |
| Sealed room holding 20k mixed slabs | ENCLOSED | 5.3 ms | 6.1 ms |
| 3×3×3 room (27 cells) | ENCLOSED | 2 µs | 5 µs |
| S-10's largest zone (8,000 cells) | ENCLOSED | 0.57 ms | 0.71 ms |
| Fill plus zone creation, 65,536 cells | | 5.6 ms | 6.2 ms |
| GEO-021 open-column scan (260 positions) | open sky | 0.7 µs | 1.2 µs |

**Throughput** is 10–17 M cells per second on one thread.

**Latency from trigger to zone** is dominated by the pipeline: the trigger
waits for the batch to close (up to 1 period), the worker fills (≤ 6 ms), and
the result is applied 10 game ticks later. That is 0.5–1.5 s with the defaults,
and the fill itself is under 1 % of it.

**Conclusion.** The D-23 cap of 65,536 cells is affordable on the worker;
R2 in the plan's risk register is retired. GEO-021's column scan is effectively
free and should run before any fill. GEO-027's open-classification cache is an
optimisation, not a necessity.

## S4 · Merge, split, local split detection

**Setup.** A 50×20×50 room (50,000 cells). Membership is stored per section;
each zone keeps a list of its packed cells, which makes a merge relabel only
the smaller zone. Split detection runs **interleaved breadth-first searches**
from the changed cell's neighbours in the same zone: searches that meet are
unioned, and a group whose searches all run out is a complete remnant and
becomes a new zone.

| Case | Median | p99 / max | Work |
|---|---|---|---|
| A. Random interior block, no split | 2.3 µs | 3.7 µs | 6 visits (p99 6) |
| A2. Removing it (the cell rejoins) | 0.1 µs | 0.2 µs | |
| B. Building a 999-block wall | 3 µs each | worst 42 µs | ≤ 4 visits |
| B2. Worst case: the last gap is 70 blocks from the only hole | 4.9 ms | 5.2 ms | 43,563 visits |
| C/E. Split into 24.5k + 25k | 6.7 ms | 8.5 ms | 48,000 visits, 24,000 relabels |
| D/E. Merge of 24.5k + 25k | 0.15 ms | 0.22 ms | 24,000 relabels |
| F. Cutting a 36-cell alcove off a 50k zone | 5 µs | 11 µs | |

**Conclusions.**

- CORE-025 holds for ordinary edits. The work depends on how far apart the
  changed cell's neighbours are inside the zone, not on the zone's size. The
  bad case is B2, a change whose only reconnection is a long detour. It costs
  O(the detour's search ball), which here is most of the zone. That still takes
  only 5 ms on the worker, so it needs no further optimisation for R1.
- A true split costs O(the smaller remnant) twice, because the interleaved
  searches advance equally. A size-balanced variant could halve that if it
  matters.
- Merges should become **O(sections), not O(cells)**, by giving each section a
  small palette from local index to zone id. A merge then rewrites palette
  entries. The [architecture](../architecture.md#4-geometry-storage-and-persistence)
  adopts this. It also lowers memory (see the NFR-004 row below).

## S5 · Off-thread engine pipeline

**Setup.** `Pipeline` in [`spikes/engine-bench`](https://github.com/west3436/zas-cubed/tree/spikes/phase-b/spikes/engine-bench) is
an S-10-shaped station:

- 100 zones and 40,000 cells, the largest zone 8,000 cells, spread over
  1–4 chunks each
- about 180 zone edges (doors) with 8-gas mixtures and energy-conserving merges
- 408 gas operations per second (300 devices, 108 breathers)
- a breach and reseal of the big zone every 60 s
- a 3×3-chunk corner unloading for 30 s every 2 minutes

It runs 10 minutes of game time, 12,000 game ticks.

**The protocol under test** (adopted in the
[architecture](../architecture.md#3-the-off-thread-engine)):

1. **Batches.** Every period the server thread closes a batch: section
   snapshots, lifecycle events, operations, and the previous apply's
   rejections. It records each chunk's lifecycle generation.
2. **Worker ownership.** The worker owns the zone graph. It builds the
   immutable snapshot itself, and keeps an undo copy of each zone's mixture
   **after the geometry phase and before gas operations**.
3. **Fixed-latency apply.** The result is applied **at a fixed game tick**
   (10 ticks after submission), not when the worker happens to finish.
4. **Rejection.** An exchange component (zones linked by edges that exchanged
   this tick) is rejected if any of its chunks unloaded in between. The worker
   restores it from undo and re-queues its operations first, in sequence
   order.
5. **Persistence ownership.** Persisted shares are owned by the server thread.
   They are written at unload from the published snapshot, and copies travel
   to the worker inside batches.

**Results:**

| Measure | Inline (single-threaded) | Threaded (1 worker) |
|---|---|---|
| Worker tick (exchange + ops) | 0.047 ms mean, 0.29 ms max | 0.048 ms mean, 0.37 ms max |
| Server thread per atmos tick: snapshot | 4.4 µs | 3.7 µs |
| Server thread per atmos tick: apply | 1.2 µs | 1.9 µs |
| Rejected components / re-queued ops | 51 / 1,475 | 51 / 1,475 |
| Final state hash | `cc1d518f2622c538` | `cc1d518f2622c538` |
| Conservation (worst relative unexplained) | 3.2e-14 | 3.2e-14 |
| **Control: rejection disabled** | | **1.7e-3 (violation)** |

**In-game snapshot cost**, from `s5_section_snapshot_cost` in the S1 mod over
about 60 non-empty sections:

- `PalettedContainer#copy()`: **0.30 µs per section** on the server thread
- decoding to geometry ids through `PalettedContainer#get`: 21 µs per section
  on the worker

A decoder that maps palette entries once and then unpacks the storage directly
would be about 10× faster. That is optional.

**Findings.**

- **CORE-071 holds when results are applied at a fixed game tick.** Inputs
  are fixed per batch and the apply point is fixed in game ticks, so thread
  timing cannot change the outcome. If the worker is late, the server thread
  waits. That costs a little server time but keeps determinism, and it should
  never happen while NFR-005 holds (250 ms against a 500 ms apply delay).
- **CORE-070 and CORE-074 are needed.** Without rejection, a chunk that
  unloads mid-tick saves its share from the committed state while the
  in-memory zone moves on, and the gas totals drift. Rejection, restore from
  undo, and re-queue in sequence order make conservation exact.
- **Only lifecycle changes reject mixture results.** If ordinary block edits
  bumped the same counter, a busy station would be rejected every tick. Block
  edits instead reach the worker through the next batch in order, so the
  worker's graph is always consistent with the world at batch close. Only
  results that write to the world (fire blocks, shattering) need a per-chunk
  **block** generation check.
- **Persistence state must be owned by the server thread.** The first version
  of the spike let the worker clear saved shares the server thread had just
  written, and crashed with a data race (an NPE). Persisted shares are
  authoritative only for zones not in memory; the worker gets copies.
- **Exchange and operations are cheap.** About 0.05 ms per atmos tick for
  S-10's 100 zones. The worker budget (NFR-005, 250 ms) is dominated by
  geometry events: fills and splits of 5–10 ms (S3, S4).
- **Loads need not reject.** In the spike a chunk load also bumped the
  generation and rejected about half the components. A paused zone doesn't
  change, so only unloads have to reject.

## S6 · Ad Astra adapter

**Not started.** It needs Ad Astra (and its Botarium/Resourceful Lib
dependencies) downloaded from a Maven repository and run in a GameTest server.
This session's permission rules blocked running downloaded third-party
installers, and running third-party mod jars falls in the same category. The
questions in [#9](https://github.com/west3436/zas-cubed/issues/9) stand unchanged.

## Proposed NFR numbers

Phase B is meant to set the NFR numbers. The spikes support keeping the
baselined budgets: there is ample headroom.

| NFR | Budget | Spike evidence |
|---|---|---|
| NFR-001 server thread | 2 ms mean / 5 ms p99 | Snapshot plus apply is about 6 µs per atmos tick for S-10 without devices, hazards or sync. The budget goes to those. |
| NFR-002 spikes | 10 ms per tick | An explosion of 100–200 changes costs about 0.03 ms to enqueue and 0.3 µs per dirty section to snapshot. Splitting the 8,000-cell zone runs on the worker (about 2 ms, extrapolated from S4). |
| NFR-005 worker | 250 ms per atmos tick | 0.05 ms of exchange plus 5–10 ms per large fill or split. Even 10 large geometry events in one tick fit. |
| NFR-004 memory | 64 B per cell, 4 KiB per zone | The S4 layout costs 8 B per position of every touched section plus 8 B per cell in lists. That is 16 B per cell where zones fill their sections, but over 64 B where a zone fills less than about 15 % of a section. The palette layout in the architecture costs 2 B per position and no per-cell list. Measure in R0 ([#78](https://github.com/west3436/zas-cubed/issues/78)). |
