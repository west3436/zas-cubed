# R0 engine benchmarks

_Run 2026-10-31 for the Phase D exit ([#33](https://github.com/west3436/zas-cubed/issues/33)).
The harness is `engine/src/bench`; rerun it with `./gradlew :engine:bench`._

**Machine:** the spikes' machine ([spikes](spikes.md)): Intel i7-13700K
(24 hardware threads), 32 GB RAM, Windows 11, Oracle JDK 21.0.6, with a 4 GB
heap (`-Xms4g -Xmx4g`, the D-40 server heap). It is faster than the D-40
reference (i5-12400 class): multiply single-thread times by about 1.3 to 1.5
for the reference machine. Every verdict below holds with that factor applied.

## Summary

Engine share, headless. "Pass" means the engine's part of the budget fits with
room to spare; the R1 work the NFR budgets also cover (devices, hazards, sync,
airflow) doesn't exist yet, and the platform's own server-thread costs aren't
measured here (see [Not measured](#not-measured)).

| Target | Measured | Result |
|---|---|---|
| NFR-001: atmos server-thread time ≤ 2 ms per server tick on average, S-10 | 1.6 µs (close plus apply, spread over the 20 game ticks of a 1 s period) | Pass (engine share) |
| NFR-001: 99th percentile ≤ 5 ms | 0.023 ms | Pass (engine share) |
| NFR-002 and CORE-047: no server tick over 10 ms | Worst close or apply: 0.097 ms in S-10, 0.026 ms on the explosion tick, 0.93 ms for a burst of 1,000 zone-creating adds | Pass |
| NFR-005: worker tick ≤ 250 ms, S-10 | Worst 10.8 ms. The slowest tick anywhere: 88 ms, zoning a 65,536-cell hall | Pass |
| NFR-003: 1,000 idle zones add ≤ 0.1 ms per server tick | +0.022 ms (0.8 µs server, 0.021 ms worker) | Pass |
| NFR-003: doubling loaded chunks without zones changes cost < 5 % | +0.2 % | Pass |
| NFR-004: ≤ 64 B per zoned cell and ≤ 64 MiB in S-10 | Rough: 13.6 MiB in all, 356 B per zoned cell | Total passes; per cell fails ([#78](https://github.com/west3436/zas-cubed/issues/78)) |
| GEO-011: constant cost per change away from zones | 5.3 to 5.8 ns, from 16 k to 1 M changes and with 278 or 1,278 zones | Pass |
| CORE-054: world generation creates no zone and no fill | 0 captures, 0 fills, 0 new zones over 320 generated chunks | Pass |
| GEO-022: a 65,536-cell fill doesn't stall the server thread | Server ≤ 0.71 ms per round; the fill runs on the worker | Pass |
| GEO-020: fill cost (no number set) | 7.5 ms median at the 65,536-cell cap | Recorded |
| CORE-025: a connected edit costs the same at any zone size | Split search: 24 visits at 1,000, 8,000 and 50,000 cells. Worker tick: 0.06, 0.23 and 1.4 ms | Partial: the search is local, the tick isn't |
| CORE-045: server-thread versus worker time | 31 µs versus 0.94 ms per atmos tick in S-10 | Recorded |
| CORE-046: cost at each period | 0.5 s: 1.29 ms of worker time per game second; 1 s: 0.94 ms; 2 s: 0.67 ms | Recorded |

**Requirements moved to `Implemented`:** GEO-011, CORE-054, CORE-047 and
NFR-003 (each with a dated note), and API-048, since S-12 now runs in CI (see
[S-12](#s-12-api-fallback-check)). CORE-045 and CORE-046 were already
`Implemented`; their Bench parts are recorded here.

**Left as they are:**

- **GEO-020 and GEO-022** meet their Bench plans, but their GameTest plans
  (a large hall reporting open; checks of off-thread results) aren't written
  yet.
- **CORE-025** (R1): the split search is local, but the tick that holds the
  edit rebuilds the zone-membership table over every zoned cell, so the
  worker's cost still grows with zone size. See [Findings](#findings).
- **NFR-001, NFR-002, NFR-004 and NFR-005** are R1. They need the full S-10
  with devices, players, mobs and hotspots on reference hardware. The engine's
  share is recorded here as a baseline.
- **NFR-034** (R1, CI regression check against this baseline) isn't wired up.

## Method

`./gradlew :engine:bench` runs a plain Java program in the engine's `bench`
source set: no Minecraft, no JMH. It isn't part of `build`, but `check`
compiles it. `-Pbench.args="s10 fill"` picks cases (`fill`, `zones`, `s10`,
`scaling`, `changes`); `-Pbench.args=--quick` makes every run short, to check
that the harness works.

- **The world** is a `BenchWorld`: geometry ids per section, a square of
  loaded chunks, and the platform's real change tracking (`DirtySections` and
  `ChunkGenerations`, as `ChangeTracker` uses them). A capture copies the
  section's ids on the calling thread, standing in for the platform's palette
  copy.
- **The pipeline** is the production one: `DimensionPipeline`,
  `DimensionScheduler` and `DimensionEngine`, with the production
  `EnclosureClassifier` and one worker thread from `AtmosWorkers`. Each atmos
  tick closes a batch on the main thread (the "server thread"), waits for the
  worker, then applies. Close and apply are timed on the main thread, and the
  engine tick on the worker. Apply runs only once the worker is done, so it
  measures applying, not waiting, as in game, where the result is applied a
  fixed 10 game ticks later.
- **Server-thread time per server tick** (NFR-001's measure) counts close and
  apply as two game ticks of a period and every other game tick as zero.
- **Timing** is `System.nanoTime()` after warm-up, on one JVM. Treat
  differences under about 10 % as noise. With 20 to 50 samples, p99 is the
  maximum.
- **Gases:** N₂, O₂ and CO₂. **Ambient:** vacuum. The conservation checker is
  off, as in game.

## S-10 engine load

**The station** is the engine side of S-10:

- a 20 × 20 × 20 room (8,000 cells), on its own
- 99 small rooms in a 10 × 10 grid sharing walls: 98 of 8 × 5 × 8 (320 cells)
  and one of 8 × 10 × 8 (640), so 40,000 zoned cells in 100 rooms
- an open door in each of the 178 shared walls

The engine makes each open door's cell a zone of its own (GEO-007), so there
are 278 zones and 356 edges. That is more than S-10's 100 zones and spike S5's
180 edges, which makes the load heavier, not lighter. Rooms start between 85 %
and 110 % of standard pressure, so the doors are busy at first and then fall
asleep.

**The script:** 2 minutes of warm-up, then 10 minutes of game time.

- Every 60 s the big room is breached. It is resealed and topped back up to
  standard pressure 10 s later; when it has vented away, the top-up zones it
  again with a fresh fill.
- Halfway through, an explosion breaches the big room: a ball of radius 3
  centred on its wall, which opens a disc of 29 wall blocks.
- "Ops" runs add spike S5's 408 engine gas operations per second: 300 device
  stand-ins (adds and removes of air in balance) and 108 breathers (O₂ out,
  CO₂ in). The devices themselves are R1 platform work and not here.

Per atmos tick at the default 1 s period:

| Run | Thread | Measure | Mean | Median | p99 | Max |
|---|---|---|---|---|---|---|
| No ops | Server | Batch close | 0.015 ms | 0.015 ms | 0.054 ms | 0.087 ms |
| No ops | Server | Apply | 6.8 µs | 5.5 µs | 0.023 ms | 0.030 ms |
| No ops | Worker | Engine tick | 0.535 ms | 0.311 ms | 10.15 ms | 10.69 ms |
| Ops | Server | Batch close | 0.015 ms | 0.013 ms | 0.036 ms | 0.097 ms |
| Ops | Server | Apply | 0.016 ms | 0.015 ms | 0.027 ms | 0.035 ms |
| Ops | Worker | Engine tick | 0.939 ms | 0.728 ms | 10.17 ms | 10.80 ms |

Against the budgets:

| Run | Server per server tick: mean (NFR-001 ≤ 2 ms) | p99 (≤ 5 ms) | Worst server tick (CORE-047 ≤ 10 ms) | Worst worker tick (NFR-005 ≤ 250 ms) | Explosion tick: close / apply / worker |
|---|---|---|---|---|---|
| No ops | 1.1 µs | 0.019 ms | 0.087 ms | 10.69 ms | 0.017 / 0.018 / 10.53 ms |
| Ops | 1.6 µs | 0.023 ms | 0.097 ms | 10.80 ms | 0.016 / 0.026 / 10.63 ms |

The worker's p99 and max are the breach, reseal and explosion ticks: each
rebuilds the membership table and the reseal re-zones the 8,000-cell room. On
other ticks the worker spends about 0.3 ms (no ops) or 0.7 ms (ops).

**Against spike S5** (same machine, 100 zones and 180 edges, 408 ops per
second): S5's worker tick was 0.047 ms on average and 0.29 ms at most, and its
server thread about 6 µs per atmos tick. Here it's 0.94 ms, 10.8 ms and 31 µs.
The engine is real now: 278 zones, edges with door connections, geometry
updates through the section mirror, a published snapshot with a position
table, undo copies for rejection, and full refills when the big room re-zones.
All of it is still far inside the budgets.

**Cost by period (CORE-046),** with ops, over the same 10 minutes of game time:

| Period | Atmos ticks | Worker per tick: mean | max | Worker per game second | Server per server tick: mean | Worst server tick |
|---|---|---|---|---|---|---|
| 0.5 s | 1,200 | 0.647 ms | 10.46 ms | 1.29 ms | 2.4 µs | 0.127 ms |
| 1 s | 600 | 0.939 ms | 10.80 ms | 0.939 ms | 1.6 µs | 0.097 ms |
| 2 s | 300 | 1.35 ms | 11.21 ms | 0.674 ms | 0.9 µs | 0.048 ms |

A shorter period costs more per game second, but less than in proportion: the
per-tick work that doesn't depend on how much time passed (snapshot, undo
copies) dominates.

**Heap (NFR-004, rough):** the world alone takes 1.1 MiB. With the station
zoned and running, the heap holds 13.6 MiB more: about 356 bytes per zoned
cell, counting every atmos structure (zone graph and cells, section mirror,
published snapshot, membership and undo copies). That is well inside the
64 MiB total but over the 64 B per cell budget. This figure is a rough
difference of `Runtime` readings after `System.gc()`, so measure it properly
in [#78](https://github.com/west3436/zas-cubed/issues/78) before acting on it.

## Bounded fill: GEO-020 and GEO-022

**The fill alone** (`EnclosureFill` on a `SectionTopology`, the worker's view
of the sections), 50 runs after 30 warm-up runs:

| Case | Outcome | Median | p99 | Cells/s | Spike S3 median |
|---|---|---|---|---|---|
| Sealed hall, 65,536 cells (the cap) | enclosed | 7.51 ms | 10.47 ms | 8.7 M | 5.5 ms |
| Hall of 65,537 cells: open at the cap | open | 5.25 ms | 5.75 ms | 12.5 M | 6.6 ms |
| S-10's largest zone, 8,000 cells | enclosed | 0.853 ms | 1.02 ms | 9.4 M | 0.57 ms |
| 3 × 3 × 3 room, 27 cells | enclosed | 2.1 µs | 6.6 µs | 12.9 M | 2 µs |

The production fill runs at 9 to 13 M cells per second, close to S3's 10 to
17 M. It does more than S3's: sub-cells, door crossings, and section and
outside-world checks.

**Through the pipeline (GEO-022):** one add zones a fresh 65,536-cell sealed
hall, in a fresh engine each round, 20 rounds after 5 warm-up rounds. Every
add finished in its first atmos tick.

| Thread | Measure | Median | Max |
|---|---|---|---|
| Server | Batch close (captures the prefetch around the add) | 0.488 ms | 0.689 ms |
| Server | Apply (publishes the zone) | 0.025 ms | 0.049 ms |
| Worker | Engine tick: fill, zone creation, snapshot | 82.1 ms | 88.4 ms |

The server thread never sees the fill: it spends half a millisecond capturing
sections while the worker spends 82 ms. That worker tick is within NFR-005,
but it's 15 times spike S3's 5.6 ms for a fill plus zone creation (see
[Findings](#findings)).

## Merge and split: CORE-023, CORE-024 and CORE-025

**Split and merge:** a 50 × 20 × 50 room with a wall at x = 25 and one hole in
it, 49,001 cells. Filling the hole splits the zone into 25,000 + 24,000
(CORE-024); opening it joins the halves by a direct opening, and they merge in
the same tick, since a split shares gas by volume (CORE-023, CORE-064). 30
rounds after 5 warm-up rounds:

| Measure | Median | p99 | Spike S4 |
|---|---|---|---|
| Worker: idle tick with the same zone, for reference | 0.121 ms | 0.227 ms | |
| Worker: tick holding the split | 9.74 ms | 18.22 ms | 6.7 ms (24.5 k + 25 k split) |
| Worker: tick holding the merge | 10.38 ms | 18.83 ms | 0.15 ms |
| Server: close plus apply, split tick | 0.036 ms | 0.134 ms | |
| Server: close plus apply, merge tick | 0.067 ms | 0.291 ms | |

**Local edits (CORE-025):** a block placed at the centre of a zone and removed
again, 50 times. Each zone is alone in its engine:

| Zone | Split-search visits | Worker tick: median | p99 |
|---|---|---|---|
| 1,000 cells | 24 | 0.060 ms | 0.469 ms |
| 8,000 cells | 24 | 0.225 ms | 0.285 ms |
| 50,000 cells | 24 | 1.42 ms | 2.81 ms |

The split search is local, as CORE-025 requires: 24 visits at every size
(spike S4 had 6, with a simpler neighbourhood). The tick isn't: it grows with
the zone, because the engine rebuilds the membership table over every zoned
cell on any tick with a geometry change.

## NFR-003 and CORE-054: cost follows activity

Two S-10 stations with ops run the same script, warmed up for 2 minutes, then
in 20 interleaved blocks of 60 atmos ticks each, so drift and JIT noise fall
on both. Station B has something extra that should cost nothing:

| Extra in B | Server per atmos tick: A / B | Worker per atmos tick: A / B | Δ per server tick: server / worker | Δ total |
|---|---|---|---|---|
| 1,000 idle zones (sealed 27-cell rooms with no edges) | 0.029 / 0.044 ms | 0.934 / 1.35 ms | +0.8 µs / +0.021 ms | +44.5 % |
| Twice the loaded chunks with no zones (255 → 510, terrain in each) | 0.029 / 0.030 ms | 0.949 / 0.950 ms | +0.0 µs / +0.1 µs | +0.2 % |

1,000 idle zones add 0.022 ms per server tick, inside NFR-003's 0.1 ms, but
they aren't free: about 0.4 µs per idle zone per atmos tick. Each tick still
copies every zone's mixture for undo and lists every zone in the published
snapshot. Loaded chunks with no zones cost nothing at all: the engine never
sees a chunk until something near a zone changes in it.

**Burst (CORE-047):** 1,000 rooms zoned by 1,000 adds queued in one atmos tick:

| Prefetch budget | Atmos ticks until all zoned | Worst close | Worst apply | Worst worker tick |
|---|---|---|---|---|
| 10 ms (the default) | 1 | 0.773 ms | 0.133 ms | 38.8 ms |
| 0 (always over budget, so every prefetch is deferred) | 2 | 0.930 ms | 0.146 ms | 38.7 ms |

Even 1,000 prefetches fit in 1 ms, far inside the budget. With no budget at
all, the prefetch is skipped and the adds wait one tick for the sections their
classification asks for, so deferral works without losing anything.

**World generation and exploration (CORE-054):** with the station zoned but
quiet, each of 40 atmos ticks loads 8 new chunks of terrain (stone with a
sealed 5 × 5 × 5 cave in each chunk) and makes 500 block edits in them: 4
million block writes in all. **No section was captured, no fill ran, and no
zone formed** (278 zones before and after). Close plus apply went from
0.019 ms (median, quiet) to 0.031 ms while generating.

## GEO-011: change tracking and the locality filter

Changes to distinct positions far from the station, in rows of 16 along x as
a fill writes them, then one batch close. Each change does the change hook's
work (a block generation and a dirty bit), and the close drains the dirty
sections through the pipeline's filter, which drops them all. Best of 7
rounds; "per change" is (hook + close − a quiet close) ÷ changes.

| Zones | Changes | Dirty sections | Hook per change | Close | Per change, all in |
|---|---|---|---|---|---|
| 278 | 16,384 | 64 | 5.7 ns | 0.016 ms | 5.8 ns |
| 278 | 262,144 | 256 | 5.8 ns | 0.020 ms | 5.8 ns |
| 278 | 1,048,576 | 256 | 5.8 ns | 0.030 ms | 5.8 ns |
| 1,278 | 16,384 | 64 | 5.6 ns | 0.039 ms | 5.3 ns |
| 1,278 | 262,144 | 256 | 5.6 ns | 0.022 ms | 5.7 ns |
| 1,278 | 1,048,576 | 256 | 5.6 ns | 0.023 ms | 5.6 ns |

The cost per change is constant, whatever the number of changes or zones.
Spike S1 found the in-game enqueue below measurement noise. The comparison of
geometry ids in front of the hook needs Minecraft and isn't included; GEO-040's
per-state cache makes it an array lookup.

## S-12: API fallback check

Not a benchmark, but the other half of this exit. The example addon has a
`gametest` source set and its own `gameTestServer` run. The run's classpath
holds the addon and the API it nests, and never `zas`. `WithoutZasTests`
checks that:

- `zas` isn't loaded, and `ZasApi.isPresent()` is false
- every query (block position, exact point, entity, another dimension) reads
  ambient standard air at 101.325 kPa and 293.15 K, 21 % O₂ and no zone
- `zoneAt` and `zone` find nothing
- `GasMutations.add` and `remove` complete at once as `ABSENT`, having moved
  nothing

CI runs it as its own step, "S-12 API without ZAS³ (example addon alone)"
(`./gradlew :example-addon:runGameTestServer`, about 35 s, 5-minute timeout).
Locally, `./gradlew runGameTestServer` runs it with the others.

## Findings

1. **Zone creation is the worker's biggest cost.** Zoning a 65,536-cell hall
   takes 82 ms on the worker, but only 7.5 ms of that is the fill. Spike S3
   measured 5.6 ms for fill plus zone creation. The rest is building the zone's
   cells and edges in the graph and rebuilding the membership table and
   snapshot. It's inside NFR-005 (250 ms), but it's the largest single cost
   seen, and an R1 station that zones several large rooms in one tick could
   approach the budget.
2. **Any geometry change rebuilds the membership table over every zoned
   cell.** That makes a one-block edit in a 50,000-cell zone cost 1.4 ms
   instead of 0.06 ms, and a merge cost 10 ms instead of spike S4's 0.15 ms.
   Merges also still relabel cells rather than section palettes (architecture
   §4). Fixing this is what CORE-025 needs.
3. **Idle zones cost about 0.4 µs each per atmos tick** (undo copies and the
   snapshot). NFR-003's number holds with a wide margin.
4. **Rough heap use is about 356 B per zoned cell,** against NFR-004's 64 B.
   [#78](https://github.com/west3436/zas-cubed/issues/78) should measure the
   parts properly.

## Not measured

- **The platform's server-thread costs**, which need Minecraft: copying
  sections from the chunk's palette containers (spike S5: 0.30 µs per section
  in game), the change hook's mixin and geometry-id lookup (spike S1: below
  noise), API calls queuing operations, and the server tick loop around close
  and apply. A GameTest measurement of an S-10-sized station wasn't cheap
  enough for this round.
- **The rest of S-10:** devices, pipe networks, players, breathing mobs and
  hotspots (R1), and the player who leaves and re-enters view distance so part
  of the station pauses and resumes. Paused zones are
  [#27](https://github.com/west3436/zas-cubed/issues/27).
- **Reference hardware (D-40):** these numbers are from a faster machine.
- **A CI regression check** against this baseline (NFR-034, R1).
