# Commands

ZAS³'s operator commands live under `/zas`. They inspect the atmosphere,
edit gas, audit the simulation against the world and profile it. They are for
operators and testers, not players.

## Permission

Every `/zas` command needs permission level 2, vanilla's level for
game-changing commands, by default (OPS-011). Change it with
`[commands] permission_level` in the server config ([config.md](config.md)):
0 lets everyone use them, 4 only the server owner and console. A source below
the level doesn't see `/zas` at all.

## Where commands act

A command acts in the dimension it runs in. Use `execute in` to target another:
`/execute in minecraft:the_nether run zas zones`. Positions take the usual
coordinates, `~ ~ ~` included; a position left out is the source's own.

Commands read the zone table the simulation last published, the same one the
API reads (API-046). Commands that change gas go through the API (API-012), so
the conservation checker accounts for them (CORE-036), and are attributed to
`command:<player>`, or `command:server` from the console or a command block
(OPS-009). Gas changes queue for the next atmos tick, like every API call: the
command says it queued them, then reports the outcome when that tick is
applied, within about a second. Commands that look inside the simulation run
on its worker between atmos ticks and report back when they finish. No command
loads a chunk.

## Inspection (OPS-003)

| Command | What it reports |
|---|---|
| `/zas at [<pos>]` | The atmosphere at a block position's largest cell (API-010, API-015): zoned, ambient, paused or unresolved, the zone id, pressure, temperature, and each gas's moles and partial pressure. With no position, at your eyes, as an exact point. At a block with several cells (a glass pane), it names the sub-cell |
| `/zas at point <x> <y> <z>` | The same at an exact point: the cell holding it, or the nearest cell if the point is inside a block (API-015) |
| `/zas zone <id>` | A zone's details (API-011): cells, volume, breached or enclosed, paused, pressure, temperature, total moles, edges to zones and to the ambient, open faces, and each gas |
| `/zas zone at [<pos>]` | The details of the zone at a block position, or with no position at your eyes |
| `/zas zones` | The dimension's zone and cell counts, the atmos tick of the table, and the first 10 zones by id |

At a glass pane in the wall of a zoned room, each half of the pane is its own
cell:

```
> /zas at point 3.2 65.5 1.5
Atmosphere at 3.2 65.5 1.5 (3 65 1, sub-cell 0 of 2) in minecraft:overworld: zoned, zone 8
> /zas at point 3.8 65.5 1.5
Atmosphere at 3.8 65.5 1.5 (3 65 1, sub-cell 1 of 2) in minecraft:overworld: ambient, zone -
```

Example (`zas:oxygen` added to a sealed 3 × 3 × 3 room):

```
> /zas zone at 2 64 2
Zone 8: 27 cells, 27000 L, enclosed, active
  102.228 kPa, 293.15 K, 1132.42 mol
  Edges: 0 to zones, 0 to the ambient, 0 open faces
  Nitrogen (zas:nitrogen): 886.714 mol, 80.0467 kPa
  Oxygen (zas:oxygen): 245.709 mol, 22.181 kPa
```

## Gas editing (OPS-009)

| Command | What it does |
|---|---|
| `/zas gas add <pos> <gas> <moles> [<temperature>]` | Adds gas at a point, at the given temperature in K or the point's own. In a sealed space this forms a zone (CORE-055); in the open air the gas is lost to the ambient |
| `/zas gas remove <pos> <gas> <moles>` | Removes gas at a point. The open air supplies gases its ambient holds |
| `/zas gas set <pos> <gas> <moles>` | Sets the moles of a gas in the zone at a point, by adding or removing the difference from the published table. The point must be zoned; use `add` to form a zone |
| `/zas gas fill <from> <to> <gas> <moles>` | Adds `<moles>` of a gas at every block position in the region that holds gas (not solid blocks) |
| `/zas gas vent <from> <to> [<gas>]` | Removes all gas, or all of one gas, from every zone with a cell in the region |

`fill` and `vent` cover at most 32,768 blocks, as vanilla's `/fill`, and need
loaded positions. Gas ids complete with Tab.

```
> /zas gas add 0 64 0 zas:oxygen 10
Queued for the next atmos tick, as command:alex
Added 10 mol of Oxygen at 0 64 0, into a zone (by command:alex)
```

## Audit and the invariant checker (OPS-010)

### `/zas audit <from> <to> [repair]`

The geometry audit (GEO-032) checks that the simulation's view of a region
matches the blocks there. It captures the region's loaded sections fresh and
compares them on the worker with the engine's section mirror and zones:

| Finding | Meaning |
|---|---|
| Stale geometry | The mirror holds a different shape than the block there now: a block change the simulation never saw |
| Zoned cell the blocks don't have | A zone holds a cell the blocks no longer have, such as one now filled by stone |
| Opening to unzoned air without an ambient edge | A zone is open to unzoned air but doesn't vent |
| Opening between zones without an edge | Two zones are open to each other but don't exchange |
| Sealed zone that isn't enclosed | A fresh enclosure fill from a zone without an ambient edge reaches the open air |
| Unzoned cell enclosed with a zone | A fresh fill from a zone encloses unzoned cells too |
| Zone cell cut off from its zone | A fresh fill from a zone can't reach one of its cells |
| Ambient edge with no opening | A zone vents, but a fresh fill finds it enclosed by its own cells |

It lists the count of each kind and the first 10 findings. Sections whose
chunks aren't loaded are skipped, never loaded; so are sections with block
changes still queued for the next atmos tick. Paused zones (GEO-030) aren't
checked, and positions whose block changes are held until a paused zone
resumes (GEO-037) aren't stale geometry: the audit counts both under "Not
checked". An audit covers at most 512 sections.

With `repair`, sections with stale geometry are sent to the simulation again
with the next atmos tick, whose geometry phase brings the mirror and zones back
in step. Other findings are reported only.

```
> /zas audit -1 63 -1 5 69 5
Auditing 4 sections; the result follows from the atmos worker
Geometry audit: 2 differences in 1 sections and 1 zones
  Not checked: 3 unloaded sections, 0 sections with changes still queued, 0 zones reaching unknown sections, 0 paused zones, 0 positions with changes held for paused zones
  Stale geometry: 1
  Zoned cell the blocks don't have: 1
  Stale geometry at 2 64 2 (zone -)
  Zoned cell the blocks don't have at 2 64 2 (zone 9)
  1 stale sections can be sent to the engine again with "repair"
```

### `/zas checker [on|off|config|report|run]`

The invariant checker (CORE-073) compares each gas's zoned moles before and
after every atmos tick with what the ledger's sources and sinks explain.

| Command | What it does |
|---|---|
| `/zas checker` | Says whether the checker is on, and whether by command or config |
| `/zas checker on` / `off` | Switches it on or off in every dimension until the server stops, whatever `[atmos] conservation_checker` says |
| `/zas checker config` | Returns to the config's setting |
| `/zas checker report` | Prints this dimension's last report: each gas's ledger by category, and any unexplained change |
| `/zas checker run` | Runs the checker on the next atmos tick of this dimension, on or off, and prints its report |

## Profiling (OPS-004, OPS-012)

### `/zas profile [reset]`

Reports, for the dimension:

- the server thread's time on atmos work per game tick, average, p99 and
  largest, over the last 1,200 game ticks (one minute; NFR-001's budget);
- closing batches and applying results on the server thread, and the whole
  atmos tick on the worker, average and largest over the last 100 atmos ticks;
- each phase of the atmos tick on the worker: restore, geometry, boundaries,
  gas operations, exchange, heat, hotspots, zone updates (reactions), and
  garbage collection with the invariant checker and building the result;
- counts of zones, edges, active (not sleeping) edges, hotspots, operations
  waiting for classification, paused zones and mirrored sections;
- the queues: operations for the next batch, mutations waiting, sections in
  the last batch, applies that waited for the worker and components rejected
  as stale.

`reset` clears the dimension's window of atmos ticks.

Phases that arrive in R1 (heat, hotspots, reactions) report 0 until then.

### Periodic log

Set `[profiling] log_interval_seconds` in the server config to log every
dimension's report at that interval (OPS-012). It is 0, off, by default.

## Debug overlay (CLIENT-005, CLIENT-011 to CLIENT-013)

| Command | What it does |
|---|---|
| `/zas debug` | Lists the overlay layers you have on |
| `/zas debug <zones\|edges\|cells\|hotspots>` | Switches one layer on or off for you |
| `/zas debug all` | Switches every layer on |
| `/zas debug off` | Switches the overlay off |

The layers draw within 2 chunks of you, refreshed once per atmos tick:

- `zones`: each zone's cells, tinted by zone id, with its pressure and temperature. Paused zones are grey, unresolved regions magenta.
- `edges`: each open face out of a zone, coloured by kind (zone direct cyan, zone boundary blue, ambient direct red, ambient boundary orange), with each edge's pressure difference.
- `cells`: the seal mask (red) and sub-cells (one colour each) of the block under your crosshair.
- `hotspots`: hotspots. There are none until fire arrives in R1.

Labels use the units in `zas-client.toml` ([config.md](config.md)). Only players who switched the overlay on are sent its data.

## Datapacks (OPS-008)

`/zas datapacks` lists the problems of the last datapack load: every entry
skipped, with its file and the field at fault. See
[datapacks.md](datapacks.md#invalid-files).
