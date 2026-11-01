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
| NFR-004: ≤ 64 B per zoned cell and ≤ 64 MiB in S-10 | Rough: 13.6 MiB in all, 356 B per zoned cell. *2026-11-01, retained size: 10.85 MiB; 222 B per zoned cell in parts that scale with cells, about 1 KiB per zone* | Total and per zone pass; per cell fails, and is revised to 256 B by dated note ([check](#membership-palette-check-2026-11-01)) |
| GEO-011: constant cost per change away from zones | 5.3 to 5.8 ns, from 16 k to 1 M changes and with 278 or 1,278 zones | Pass |
| CORE-054: world generation creates no zone and no fill | 0 captures, 0 fills, 0 new zones over 320 generated chunks | Pass |
| GEO-022: a 65,536-cell fill doesn't stall the server thread | Server ≤ 0.71 ms per round; the fill runs on the worker | Pass |
| GEO-020: fill cost (no number set) | 7.5 ms median at the 65,536-cell cap | Recorded |
| CORE-025: a connected edit costs the same at any zone size | Split search: 24 visits at 1,000, 8,000 and 50,000 cells. Worker tick: 0.06, 0.23 and 1.4 ms. *2026-11-01: 0.046, 0.041 and 0.054 ms* | Pass since [#106](https://github.com/west3436/zas-cubed/issues/106) ([check](#membership-palette-check-2026-11-01)) |
| CORE-045: server-thread versus worker time | 31 µs versus 0.94 ms per atmos tick in S-10 | Recorded |
| CORE-046: cost at each period | 0.5 s: 1.29 ms of worker time per game second; 1 s: 0.94 ms; 2 s: 0.67 ms | Recorded |

*2026-11-01: since GEO-007 ([#107](https://github.com/west3436/zas-cubed/issues/107)),
the station's door cells join a room each, so it has 100 zones and 178 edges
rather than 278 and 356 ([S-10](#s-10-engine-load)). The figures above that
use the station (S-10, NFR-001 to NFR-005, GEO-011, CORE-045 and CORE-046)
were measured with 278 zones and are pending re-measurement. GEO-044's door
toggle case is new and not yet run ([door toggles](#geo-044-door-toggles)).*

**Requirements moved to `Implemented`:** GEO-011, CORE-054, CORE-047 and
NFR-003 (each with a dated note), and API-048, since S-12 now runs in CI (see
[S-12](#s-12-api-fallback-check)). CORE-045 and CORE-046 were already
`Implemented`; their Bench parts are recorded here. *2026-11-01: CORE-025
followed, with a dated note, once its Bench passed
([membership palette check](#membership-palette-check-2026-11-01)).*

**Left as they are:**

- **GEO-020 and GEO-022** meet their Bench plans, but their GameTest plans
  (a large hall reporting open; checks of off-thread results) aren't written
  yet. *2026-10-31: written with the R0 scenario tests; both are now
  `Implemented`.*
- **CORE-025** (R1): the split search is local, but the tick that holds the
  edit rebuilds the zone-membership table over every zoned cell, so the
  worker's cost still grows with zone size. See [Findings](#findings).
  *2026-11-01: fixed by
  [#106](https://github.com/west3436/zas-cubed/issues/106); CORE-025 is now
  `Implemented` ([check](#membership-palette-check-2026-11-01)).*
- **NFR-001, NFR-002, NFR-004 and NFR-005** are R1. They need the full S-10
  with devices, players, mobs and hotspots on reference hardware. The engine's
  share is recorded here as a baseline.
- **NFR-034** (R1, CI regression check against this baseline) isn't wired up.

## Method

`./gradlew :engine:bench` runs a plain Java program in the engine's `bench`
source set: no Minecraft, no JMH. It isn't part of `build`, but `check`
compiles it. `-Pbench.args="s10 fill"` picks cases (`fill`, `zones`, `s10`,
`scaling`, `changes`, `toggle`); `-Pbench.args=--quick` makes every run short,
to check that the harness works.

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

Each door's far face is a zone boundary, so its cell joins the room west or
north of it, as an open door's cell joins the room it faces in game (GEO-007,
[architecture §4.7](../architecture.md#47-zone-boundary-blocks-geo-007-geo-044-core-034-core-067)):
100 zones and 178 door edges, S-10's 100 zones. Rooms start between 85 % and
110 % of standard pressure, so the doors are busy at first and then fall
asleep.

*2026-11-01: the numbers in this section were measured before GEO-007, when
the engine made each open door's cell a zone of its own: 278 zones and 356
edges, more than S-10's 100 zones and spike S5's 180 edges
([#107](https://github.com/west3436/zas-cubed/issues/107)). They are pending
re-measurement with `-Pbench.args=s10`.*

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
*2026-11-01: measured by retained size, part by part, in the
[membership palette check](#membership-palette-check-2026-11-01).*

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
cell on any tick with a geometry change. *2026-11-01: it is now, after #106
(see the [membership palette check](#membership-palette-check-2026-11-01)).*

## GEO-044: door toggles

*Added 2026-11-01; not yet run.* Two rooms side by side, joined by one door in
their shared wall, each of 1,000, 8,000 or 50,000 cells; the door is opened
and closed again 50 times after 10 warm-up times, with one atmos tick after
each change (`-Pbench.args=toggle`). Each pair is alone in its engine, so the
worker tick shows everything a toggle costs. The case checks every toggle
leaves the zones, their ids and their cells as they were, and reports the
split checks the toggles caused, which should be none. GEO-044 passes if the
worker tick per toggle is about the same at every size and close to the idle
tick, unlike the placed block of [local edits](#merge-and-split-core-023-core-024-and-core-025).

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

## Cell-id layout check (2026-10-31)

_[#105](https://github.com/west3436/zas-cubed/issues/105) widened cell ids to
the world border: 26 bits of x and z, 10 of y and 2 of sub-cell index instead
of 24, 24, 12 and 4 (architecture §4.3). Same machine and settings;
`./gradlew :engine:bench -Pbench.args="fill zones s10"` on `main` just before
the change and on the branch after it._ Medians:

| Measure | Before | After |
|---|---|---|
| Fill: sealed hall, 65,536 cells | 7.79 ms | 7.69 ms |
| Fill: hall of 65,537 cells, open at the cap | 5.22 ms | 5.22 ms |
| Fill: S-10's largest zone, 8,000 cells | 0.827 ms | 0.813 ms |
| Fill: 3 × 3 × 3 room | 2.2 µs | 2.1 µs |
| Pipeline: worker tick zoning the 65,536-cell hall | 81.08 ms | 83.76 ms |
| Pipeline: server close plus apply | 0.572 ms | 0.528 ms |
| Split tick, 49,001 cells | 14.45 ms | 15.01 ms |
| Merge tick | 15.09 ms | 15.36 ms |
| Local edit, 1,000 / 8,000 / 50,000-cell zone | 0.116 / 0.529 / 3.57 ms | 0.112 / 0.553 / 3.60 ms |
| S-10 with ops: worker tick, mean / worst | 0.823 / 13.34 ms | 0.839 / 11.97 ms |
| S-10 heap per zoned cell | 373 B | 373 B |

The fill, which decodes ids on every step, is unchanged: decoding is still a
shift per field. The one steady difference is the pipeline's worker tick for
the 65,536-cell hall, about 2.5 % slower (83.2 ms in a second run after the
change); the extra time is outside the fill, most likely in how the new bit
layout spreads in the graph's hash maps. It is well inside NFR-005. The
other rows differ by run-to-run noise. The split, merge and edit rows run
slower than the table above in both columns; they are the same code in both,
so that is this run's machine state, not the change.

## Membership palette check (2026-11-01)

_[#106](https://github.com/west3436/zas-cubed/issues/106) replaced the
membership table the engine rebuilt over every zoned cell on any tick with a
geometry change by the per-section palettes of architecture §4.4, kept up to
date change by change and published copy-on-write;
[#78](https://github.com/west3436/zas-cubed/issues/78) replaced the rough heap
figure with retained-size accounting. Same machine and settings;
`./gradlew :engine:bench` on `main` just before the change (with the new heap
accounting, which reads the engine's fields by name and runs on both) and on
the branch after it. Both columns come from one session; this session ran
slower than the tables above, so compare a row across, not with the tables
above._ Medians unless noted:

| Measure | Before | After |
|---|---|---|
| Local edit, worker tick: 1,000 / 8,000 / 50,000-cell zone | 0.109 / 0.529 / 3.55 ms | 0.046 / 0.041 / 0.054 ms |
| Local edit, split-search visits | 24 / 24 / 24 | 24 / 24 / 24 |
| Idle tick, 49,001-cell zone | 0.151 ms | 0.070 ms |
| Split tick, 49,001 cells into 25,000 + 24,000 | 14.36 ms | 8.52 ms |
| Merge tick, the same halves | 14.92 ms | 0.551 ms |
| Pipeline: worker tick zoning the 65,536-cell hall | 84.04 ms | 80.43 ms |
| Pipeline: server close plus apply for the hall | 0.544 ms | 0.566 ms |
| Fill alone: sealed hall, 65,536 cells | 7.58 ms | 7.71 ms |
| S-10 with ops: worker tick, mean / worst | 0.837 / 17.08 ms | 0.594 / 9.29 ms |
| S-10 without ops: worker tick, mean / worst | 0.494 / 12.36 ms | 0.412 / 11.93 ms |
| S-10 with ops: explosion tick, worker | 11.45 ms | 7.77 ms |
| S-10 with ops: server per server tick, mean / worst server tick | 4.7 µs / 0.346 ms | 4.8 µs / 0.363 ms |
| NFR-003: 1,000 idle zones, Δ worker per server tick | +0.027 ms | +0.018 ms |
| Burst of 1,000 zone-creating adds: worst worker tick | 46.74 ms | 42.52 ms |
| S-10 atmos heap, retained (see below) | 12.27 MiB | 10.85 MiB |
| S-10 atmos heap per zoned cell, all parts | 320 B | 283 B |
| Of which zone membership and position lookups | 48.4 B | 10.7 B |
| GC'd `Runtime` difference per zoned cell (the old method) | 371 B | 334 B |

- **CORE-025's Bench passes.** A one-block edit now costs the same at every
  zone size (0.04 to 0.05 ms, inside run-to-run noise of each other), where it
  grew 33-fold from 1,000 to 50,000 cells before. The split search was already
  local; the rest of the tick now is too.
- **Merges** rename palette entries in the absorbed zone's sections, and the
  graph moves the absorbed zone's edges as wholes instead of recounting every
  stored pair of its cells: 27 times faster. 0.55 ms is above spike S4's
  0.15 ms because the graph still moves each cell's zone reference (24,000 of
  them) and the tick runs exchange and publishes; the membership part is
  O(sections).
- **Splits** must move every cell of the split-off part, so the tick follows
  the remnant (24,000 cells here): 8.5 ms against spike S4's 6.7 ms. That is
  proportional to the change, as CORE-024 and CORE-025 allow.
- **Publishing** is O(changed sections): a table shares every unchanged
  section and zone record with the one before, so a tick with no zone change
  publishes the same table again.
- **Zone creation (Finding 1) didn't change:** the 65,536-cell hall still
  takes 80 ms. A profile puts the time in building the zone (the connectivity
  check in `ZoneGraph.createZone`, `EnclosureZones` splitting the region by
  direct openings, both binary searches per neighbour, and linking each cell's
  stored pairs); the palettes are about 1.5 % of it. Not in scope for #106.
- **Idle zones (Finding 3)** cost a third less: membership no longer adds
  anything per zone per tick, but the undo copies and zone views remain.

**Heap (NFR-004), method.** `HeapAccounting` (in the bench) walks the object
graph from the engine and the pipeline, after the warm-up, with the worker
idle, and adds up each object's shallow size, counting every object once. Sizes
are HotSpot's own layout read through `sun.misc.Unsafe` (field offsets, array
base offsets and element sizes, rounded to 8 bytes; compressed oops, as with
any heap under 32 GB); records and lambdas, which Unsafe won't give offsets
for, are estimated from their fields. Each object is charged to the first part
that reaches it, walked in a fixed order: section mirror, zone membership, zone
graph, published snapshots, undo point, then the rest. Within a part, the
graph's cells (with their stored connections and the cell map) are split from
its zones, mixtures and edges, and each zone's section list from the
palettes. Left out: the bench's stand-in world, threads and executors, and what
every dimension shares (gas registry, mixture settings, geometry palette).
"Sparse" adds 1,000 sealed 27-cell rooms (about 10 to a section) after the
timed run, and reports what they add.

**Heap after the change** (278 zones, 40,178 zoned cells at 40,178 positions):

| Part | Scales with | S-10 | Per zoned cell | Per zone | Sparse: per added cell | Sparse: per added zone |
|---|---|---|---|---|---|---|
| Section mirror (geometry ids of mirrored sections) | cells | 0.84 MiB | 21.9 B | | 68.8 B | |
| Zone membership: per-section palettes and lookups | cells | 0.41 MiB | 10.7 B | | 36.9 B | |
| Zone graph: cells, their stored connections, the cell map | cells | 7.25 MiB | 189.3 B | | 207.6 B | |
| Zone graph: zones, mixtures and edges | zones | 0.10 MiB | | 384 B | | 344 B |
| Zone membership: each zone's sections and chunks | zones | 0.02 MiB | | 79 B | | 76 B |
| Published snapshots: zone views | zones | 0.03 MiB | | 117 B | | 116 B |
| Undo point: mixture copies, components | zones | 0.13 MiB | | 483 B | | 59 B |
| Classifier and fill scratch, queues, outcomes | neither | 2.06 MiB | | | | |
| **Total** | | **10.85 MiB** | **221.9 B** | **1,063 B** | **313.4 B** | **596 B** |

- **The graph's cells are four fifths of it,** 189 B per zoned cell: a 56-byte
  `Cell`, a `long[6]` and a `boolean[6]` of stored connections (88 B), about
  39 B of open-addressing cell map at its 0.5 load factor, and the zone's cell
  array. That is the R0 graph's design (every open pair stored on both
  cells, §4.4), not the membership.
- **The section mirror's share** is 22 B per zoned cell in S-10, where rooms
  fill their sections well, and 69 B in the sparse rooms, since a mirrored
  section costs 8 KiB whatever it holds (uniform sections cost almost nothing).
- **The palettes** cost 11 B per zoned cell in S-10 and 37 B in the sparse
  rooms, against 48 to 50 B for the old table, which kept every cell in sorted
  arrays twice over (the table and the snapshot's copy of its positions).
- **The classifier's scratch** (2 MiB, its fill's tables sized for the
  65,536-cell cap) is fixed per dimension.

**Against NFR-004:** the total, 10.85 MiB, passes 64 MiB with a sixfold
margin, and zones cost about 1 KiB each against 4 KiB. The parts that scale
with cells come to 222 B per zoned cell, over the 64 B budget; without the
graph's cells they would be 33 B. NFR-004 is revised by a dated note (see the
requirement).

## Findings

1. **Zone creation is the worker's biggest cost.** Zoning a 65,536-cell hall
   takes 82 ms on the worker, but only 7.5 ms of that is the fill. Spike S3
   measured 5.6 ms for fill plus zone creation. The rest is building the zone's
   cells and edges in the graph and rebuilding the membership table and
   snapshot. It's inside NFR-005 (250 ms), but it's the largest single cost
   seen, and an R1 station that zones several large rooms in one tick could
   approach the budget. *2026-11-01: still 80 ms after #106; the membership
   rebuild was a small part of it. The time is in building the zone (see the
   [membership palette check](#membership-palette-check-2026-11-01)).*
2. ~~**Any geometry change rebuilds the membership table over every zoned
   cell.**~~ That made a one-block edit in a 50,000-cell zone cost 1.4 ms
   instead of 0.06 ms, and a merge cost 10 ms instead of spike S4's 0.15 ms.
   Merges also still relabelled cells rather than section palettes
   (architecture §4). *2026-11-01: fixed by
   [#106](https://github.com/west3436/zas-cubed/issues/106): a local edit
   costs the same at any zone size and a merge renames palette entries
   ([check](#membership-palette-check-2026-11-01)).*
3. **Idle zones cost about 0.4 µs each per atmos tick** (undo copies and the
   snapshot). NFR-003's number holds with a wide margin. *2026-11-01: a third
   less after #106; the undo copies and zone views remain.*
4. ~~**Rough heap use is about 356 B per zoned cell,** against NFR-004's
   64 B.~~ *2026-11-01: measured by retained size
   ([#78](https://github.com/west3436/zas-cubed/issues/78)): 283 B per zoned
   cell in all, 222 B of it in parts that scale with cells, four fifths of
   that the zone graph's stored connections; 10.85 MiB for S-10. NFR-004's
   per-cell budget is revised
   ([check](#membership-palette-check-2026-11-01)).*

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
