# 11 · World geometry (`GEO`)

**Status:** Accepted 2026-10-30 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules).

**Scope.** This file covers:
- which block positions hold gas, how much, and in how many cells
- which faces are gas-tight
- how block changes reach the engine
- how enclosures are detected
- how zones behave at chunk and world boundaries

**Background:**
- [zas-core](../research/zas-core.md) §2.3 and §10
- [neoforge-1.21.1](../research/neoforge-1.21.1.md) (block-change hooks, shapes)
- [space-mods-and-prior-art](../research/space-mods-and-prior-art.md) (seal detection lessons)

## Cells and sealing

#### GEO-001 · Cell definition
`Must` · `R0` · `Implemented` · Verify: Unit, GameTest · Trace: D-44

A cell shall be a connected region of a block position's free space (space
outside its collision shape) that reaches at least one of the position's
faces. An air position is one cell. A partial block has one cell for each
such region (GEO-004). Free space that reaches no face is ignored.

- **Rationale:** Decision: D-44.
- **Verify:** Unit tests count the cells of air, a slab, a pane and a block
  with a sealed internal void; a GameTest checks the same blocks in-world.

#### GEO-002 · Cell volume
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: A-02, D-22

A cell's gas volume shall be its share of the free space: 1,000 L for an air
position, and proportionally less for a partial block (a bottom slab holds
500 L).

- **Rationale:** Decision: D-22 and A-02.
- **Verify:** Unit tests check the volumes of air, a slab and stairs against
  their free space.

#### GEO-003 · Derived seal mask
`Must` · `R0` · `Implemented` · Verify: Unit, GameTest · Trace: [neoforge §4](../research/neoforge-1.21.1.md#4-block-geometry)

By default, the face between two adjacent positions shall be gas-tight wherever
the two blocks' collision shapes *together* cover it, so complementary
half-slab faces seal. Two cells in those positions are connected if both reach
a part of the face that isn't covered.

Blocks whose collision shape is inset from the face, such as chests and cactus,
therefore leak around their edges. This is intended.

- **Rationale:** Inferred: ZAS walls fill whole tiles ([zas-core
  §2.3](../research/zas-core.md#23-cells-turfdm-atomdm)), but Minecraft blocks
  are partial, so a face seals where both shapes together cover it ([neoforge
  §4](../research/neoforge-1.21.1.md#4-block-geometry)).
- **Verify:** Unit tests check face masks for slab pairs and slab against air;
  a GameTest checks complementary slabs seal a room and a chest leaks.

#### GEO-041 · Collision shapes only
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: [neoforge §4](../research/neoforge-1.21.1.md#4-block-geometry)

Cells, volumes and seal masks shall be derived from collision shapes in an
empty collision context, never from render or light occlusion. (Glass and
leaves don't occlude for rendering, and shapes such as scaffolding and powder
snow change with the entity.)

- **Rationale:** Inferred: render and occlusion shapes misreport gas-tightness
  (glass, leaves), and some collision shapes depend on the entity ([neoforge
  §4](../research/neoforge-1.21.1.md#4-block-geometry)).
- **Verify:** Unit tests check glass is gas-tight and scaffolding and powder
  snow use their empty-context shapes.

#### GEO-040 · Geometry cached per block state
`Must` · `R0` · `Implemented` · Verify: Bench, Unit

Cells, volumes and seal masks shall be computed once per block state and
cached, except for the blocks in GEO-043.

- **Rationale:** Inferred: computing shapes on every change is too slow for
  large builds, and block states are finite and shared.
- **Verify:** A Unit test checks repeated queries reuse the cached result;
  Bench measures the lookup cost.

#### GEO-042 · Moving and animated blocks
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-44

Blocks whose shape changes because they are moving or animating, such as
moving pistons and opening shulker boxes, shall be ignored while in motion.
The engine uses the geometry of the state they settle into (for a shulker box,
closed) and doesn't track the animation.

- **Rationale:** Decision: D-44.
- **Verify:** A GameTest moves a piston and opens a shulker box beside a zone
  and checks the cells don't change mid-motion and the open box counts as
  closed.

*2026-11-05: Implemented. `geo_042_cells_do_not_change_while_a_piston_moves`
extends and retracts a sticky piston beside a zone, and
`geo_042_an_open_shulker_box_counts_as_closed` opens a box in a room's wall.*

#### GEO-043 · Position-dependent static shapes
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-44

Block states whose collision shape depends on position or block-entity data,
but doesn't change on its own (bamboo's offset, data-driven modded shapes),
shall be evaluated per position and re-evaluated whenever the block changes
(GEO-010, GEO-014).

- **Rationale:** Decision: D-44.
- **Verify:** A GameTest places bamboo at two positions and checks their cells
  differ, and that a change re-evaluates them.

*2026-11-05: Implemented. `geo_043_bamboo_cells_differ_by_position` does that, and
`geo_043_points_in_each_cell_resolve_to_that_cell` covers points in a shape of
several cells ([#123](https://github.com/west3436/zas-cubed/issues/123)).*

#### GEO-004 · Sub-cells
`Must` · `R0` · `Implemented` · Verify: Unit, GameTest · Trace: D-44

When a block's shape divides its free space into separate regions (glass
panes, walls), each region shall be an independent cell. Gas doesn't pass
between them inside the block, and they may belong to different zones, so a
line of panes seals like a wall.

- **Rationale:** Decision: D-44.
- **Verify:** A Unit test checks a pane has two cells; a GameTest checks a
  line of panes keeps two zones at different pressures apart.

#### GEO-005 · Geometry overrides
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-04

A datapack override, by block or block tag, shall replace a block's derived
geometry with a declared one: which faces are gas-tight, and its gas volume.
An overridden block is a single cell.

- **Rationale:** Decision: D-04. Pack authors and mods must be able to fix a
  block's geometry.
- **Verify:** A GameTest overrides a block with a test datapack and checks its
  tightness and volume follow the override.

#### GEO-009 · Default overrides
`Must` · `R1` · `Implemented` · Verify: Inspect, GameTest · Depends: GEO-005

ZAS³ shall ship overrides for blocks whose collision shape misleads:

| Block or tag | Default | Why |
|---|---|---|
| `#minecraft:leaves` | Permeable, 1,000 L | A full collision cube, but visibly porous |
| Scaffolding | Permeable, 1,000 L | Its collision depends on the entity |
| `#minecraft:fences`, `#minecraft:fence_gates`, iron bars | Permeable, 1,000 L | A full-height collision plate, but visibly open |
| Honey block | Gas-tight, 0 L | Its collision is inset by 1/16, so the derived rule would leak |

- **Rationale:** Inferred: these blocks' collision shapes contradict how they
  look, for the reasons in the table.
- **Verify:** Inspect the shipped overrides; a GameTest checks leaves and
  fences pass gas and a honey block seals.

*2026-11-05: Implemented. Inspected: the four files in
`data/zas/zas/geometry_overrides/` match the table
([datapacks](../datapacks.md#geometry-overrides)). `geo_009_shipped_overrides`
checks every state of those blocks, and
`geo_009_leaves_and_fences_pass_gas_and_honey_seals` passes in a real level.*

*2026-11-06: the table's permeable defaults are for dry blocks. The owner chose
that water wins in a waterlogged fence, leaf, iron bar or scaffolding (GEO-006),
so those files now say `fluid_seals`.*

#### GEO-006 · Fluids
`Should` · `R1` · `Implemented` · Verify: GameTest

A position holding a fluid (a source, flowing fluid, or waterlogged block)
shall hold no gas and be gas-tight on all faces, unless overridden.

- **Rationale:** Inferred: a fluid-filled position has no room for gas, and
  treating it as a wall is simpler than modelling the fluid's volume.
- **Verify:** A GameTest checks a water column seals a room and an overridden
  fluid doesn't.

*2026-11-05: Implemented. `geo_006_water_column_seals_a_room`,
`geo_006_waterlogged_block_seals_a_room` and
`geo_006_overridden_fluid_does_not_seal` pass. An override also beats a waterlogged
state, so a waterlogged fence passes gas under GEO-009's override.*

*2026-11-06: the owner decided that water wins in a waterlogged block: it seals,
matching what the player sees. So "unless overridden" no longer covers GEO-009's
shipped overrides, which replaces the reading in the note above: a waterlogged
fence, leaf, iron bar or scaffolding is gas-tight and holds no gas, and only the
dry states take the override. A pack's own override still has the last word
(D-45), for a fluid or a waterlogged state, unless it says `fluid_seals` as the
shipped files do ([datapacks](../datapacks.md#geometry-overrides)).
`geo_006_water_wins_over_the_shipped_overrides`,
`geo_006_waterlogged_fence_leaf_and_bars_seal_a_room`,
`geo_006_waterlogging_a_fence_seals_it_and_draining_opens_it` and
`geo_006_pack_override_beats_water_and_zones_rebuild_without_it` pass. A zone
saved through such a block comes back without its cell when an existing world
loads, keeping its gas (DATA-009).*

#### GEO-007 · Zone-boundary blocks
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: [zas-core §2.3](../research/zas-core.md#23-cells-turfdm-atomdm)

When open, these blocks shall pass gas but keep the zones on either side
separate:
- doors
- trapdoors
- blocks tagged `zas:zone_boundary`

- **Rationale:** vg: ZAS's zone-blocked faces for doors ([zas-core
  §2.3](../research/zas-core.md#23-cells-turfdm-atomdm)).
- **Verify:** A GameTest opens a door and a trapdoor between rooms and checks
  gas passes while the zones stay separate.

*2026-11-01: Implemented ([#64](https://github.com/west3436/zas-cubed/issues/64),
fixing [#107](https://github.com/west3436/zas-cubed/issues/107)). The tag
`zas:zone_boundary` holds every door and trapdoor by default. Each tagged
state keeps zones apart only across the faces its panel lies against, closed
and open, so the door's own cell joins the room on the side away from its panel
rather than becoming a zone of its own ([architecture
§4.7](../architecture.md#47-zone-boundary-blocks-geo-007-geo-044-core-034-core-067)).
`geo_007_open_door_passes_gas_and_keeps_zones_apart` and
`geo_007_open_trapdoor_passes_gas_and_keeps_zones_apart` (GameTest) open each
between two zoned rooms; `geo_007_doors_and_trapdoors_are_boundaries_on_their_panel_faces`
checks the faces of every vanilla door and trapdoor state.*

#### GEO-044 · Cheap boundary toggles
`Must` · `R1` · `Implemented` · Verify: Unit, Bench · Depends: GEO-007

Opening or closing a zone-boundary block shall never trigger a zone split or
rebuild.

- **Rationale:** Inferred: doors toggle constantly, and ZAS already handles
  door cells cheaply ([zas-core
  §3.1](../research/zas-core.md#31-cell-update-algorithm-turfdm59-206)).
- **Verify:** A Unit test checks a toggle causes no split or rebuild; Bench
  checks the cost per toggle is constant.

*2026-11-01: the Unit part passes. A toggle opens or closes only a door's
boundary faces, so it changes an edge's connection count and nothing else:
`geo_044_toggling_a_door_never_splits` (Unit) and
`geo_044_toggling_a_door_never_splits_or_rebuilds` (Unit, whole pipeline) count
no split check, split, merge or new zone over 20 toggles. The Bench case
(`-Pbench.args=toggle`) is written but not yet run, so this stays Accepted.*

*2026-11-01: Implemented. The Bench case ran: a toggle costs 4 to 5 µs of
worker time above the idle tick with rooms of 1,000, 8,000 and 50,000 cells,
and causes no split check ([benchmarks · door
toggles](../research/benchmarks.md#geo-044-door-toggles)).*

*2026-11-08: a correction to the 2026-11-01 note. A toggle also changed a zone's
volume where a block's closed shape takes room its open shape doesn't. The
airlock's and the firelock's two cells held 527.3 L closed and 1,406.3 L open,
and the large airlock's four held 1,230.5 L and 3,281.3 L. So closing one shrank
the zone holding its cells, which kept its gas (CORE-022) and rose in pressure:
by 3.2 % in a 27 m³ room, and by up to 22 % in a 4 m³ airlock chamber. It still
caused no split check, split, merge or new zone. Since GEO-045 every state of a
tagged block holds its open state's volume, so a toggle now changes only
boundary faces and an edge's connection count, as the note said. Vanilla doors
and trapdoors were never affected: their cells hold 812.5 L open or closed.*

#### GEO-045 · Boundary toggles keep a zone's volume
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: D-41, CORE-022 · Depends: GEO-007, GEO-044

Each state of a block tagged `zas:zone_boundary` that has an `open` property
shall hold the gas volume of the same state with `open=true`, so opening or
closing it changes only its boundary faces and edges, never a zone's volume.
The state's cells keep their shares of that volume. A tagged block with no
`open` property keeps each state's own volume, and so do a state with no cells,
such as a waterlogged one (GEO-006), and a state whose open state holds no gas.

- **Rationale:** Inferred: displaced gas stays in its zone (D-41, CORE-022). So
  a door whose cells hold less gas closed than open compresses the zone they
  belong to as it closes and dilutes it as it opens, and the gas that crossed
  while it was open stays where it went. Each cycle then moves gas toward the
  door's side, with no power. The airlock's closed leaf fills the passage from
  the face it seals, so it held 879 L less closed: closing an airlock's outer
  door raised a 4 m³ chamber from 200 to 204.1 kPa after one exchange step, and
  to up to 22 % above the room beyond, where an airlock cycle finishes within
  5.07 kPa (CONT-139). A leaf that slides out of its frame takes no room from
  either side.
- **Verify:** A Unit test checks that resizing a geometry's cells keeps their
  faces, heat data and shares. A GameTest checks every tagged state holds its
  open state's volume and every vanilla door and trapdoor state the volume its
  shape gives. GameTests open and close the airlock, the large airlock, the
  firelock and a door with a firelock four times between two rooms at 200 kPa
  and check both rooms stay at 200 kPa within 1e-6.

*2026-11-08: implemented, and still Draft for the owner. Once tags are bound,
`Geometries` resizes each tagged state's cells, after overrides, to
`boundaryVolume`, each cell keeping its share (`Geometry.withVolume`). The
airlock's and the firelock's lower block now holds 750 L in every state and the
upper 656.25 L; the large airlock's hold 875 L and 765.625 L. Vanilla doors,
trapdoors and the door with a firelock hold 812.5 L, as before. A seal
provider's answer (API-023) and a shape worked out per position (GEO-043) are
the block's own and aren't resized
([architecture §4.7](../architecture.md#47-zone-boundary-blocks-geo-007-geo-044-core-034-core-067)).
`geo_045_with_volume_resizes_the_cells_and_nothing_else` (Unit),
`geo_045_every_boundary_state_holds_its_open_volume` and the four
`geo_045_toggling_…_keeps_both_rooms_at_200_kpa` (GameTests) pass.*

*2026-11-08: Accepted by the owner, and Implemented: its Verify plan's unit tests and
GameTests pass (see the notes above).*

#### GEO-008 · Dynamic sealing API
`Must` · `R1` · `Rejected` — duplicate of API-023 (Seal provider), 2026-10-30.

## Change tracking

#### GEO-010 · Complete change detection
`Must` · `R0` · `Implemented` · Verify: GameTest

Every server-side block state change in a loaded chunk shall reach the engine,
whatever its cause:
- players
- pistons
- explosions
- fluids
- commands
- structure placement
- other mods

- **Rationale:** Inferred: a missed block change leaves zones out of step with
  the world, and changes arrive by many paths ([neoforge
  §3](../research/neoforge-1.21.1.md#3-block-change-detection)).
- **Verify:** GameTests change a zone's wall by each listed cause and check
  the engine sees it.

#### GEO-011 · Locality filter
`Must` · `R0` · `Implemented` · Verify: Bench · Trace: CORE-051, CORE-054

A block change that isn't in or next to a zone, and doesn't touch a pending
operation, shall cost a constant amount of work: no flood fill, no graph
update.

- **Rationale:** Decision: D-13. Most block changes are nowhere near a zone,
  so they must cost almost nothing ([neoforge
  §3](../research/neoforge-1.21.1.md#3-block-change-detection)).
- **Verify:** Bench makes mass block changes away from any zone and checks the
  cost per change is constant.

*2026-10-31: Implemented. The R0 bench measures 5.3 to 5.8 ns per change, hook
and filtered drain together, from 16,384 to 1,048,576 changes and with 278 or
1,278 zones ([benchmarks · GEO-011](../research/benchmarks.md#geo-011-change-tracking-and-the-locality-filter)).*

#### GEO-012 · Coalescing
`Must` · `R0` · `Implemented` · Verify: Unit

Multiple changes during one atmos tick shall be coalesced and processed once
in the next geometry phase.

- **Rationale:** vg: ZAS queues changed cells and processes them on the next
  tick ([zas-core §3](../research/zas-core.md#3-control-flow)).
- **Verify:** A Unit test makes 100 changes in one tick and checks one
  geometry pass handles them.

#### GEO-013 · Revalidation on chunk load
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: [neoforge §3](../research/neoforge-1.21.1.md#3-block-change-detection)

Zones touching a chunk shall be revalidated when that chunk loads, to catch
changes that couldn't be observed at write time (offline or worldgen edits).

- **Rationale:** Inferred: offline editors and worldgen change blocks without
  any observable event ([neoforge
  §3](../research/neoforge-1.21.1.md#3-block-change-detection)).
- **Verify:** A GameTest changes a zone's wall while its chunk is unloaded and
  checks the zone is revalidated on load.

*2026-11-05: Implemented. `geo_013_a_wall_opened_while_unloaded_breaches_the_paused_zone`
and `geo_013_a_wall_opened_while_unloaded_breaches_the_rebuilt_zone` are the
plan's tests, for a zone waiting in memory and one rebuilt from its chunks. The
other `geo_013_` tests in `ChunkRevalidationTests` check a block placed, a wall
built across the zone and a hole mended, each under the chunk hook.*

#### GEO-014 · Mark-dirty API
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: [neoforge §3](../research/neoforge-1.21.1.md#3-block-change-detection)

The API shall expose a `markDirty(level, pos)` call that makes the engine
re-evaluate that position's geometry, for changes it can't observe, such as
shapes driven by block-entity data.

- **Rationale:** Inferred: block-entity data can change a shape without a
  block-state change ([neoforge
  §3](../research/neoforge-1.21.1.md#3-block-change-detection)).
- **Verify:** A GameTest changes a test block's data-driven shape, calls
  markDirty, and checks the geometry is re-evaluated.

*2026-11-05: Implemented. `geo_014_mark_dirty_reevaluates_a_data_driven_shape`
does that, in a real level: the zones change only once markDirty is called.*

## Enclosure detection

#### GEO-020 · Bounded enclosure test
`Must` · `R0` · `Implemented` · Verify: Unit, GameTest, Bench · Trace: D-23

Whether a seed cell is enclosed shall be decided by a fill through connected
cells, bounded by a configurable maximum volume (default 102,400 cells).
Exceeding the bound means "open to ambient".

- **Rationale:** Decision: D-23.
- **Verify:** Unit tests check enclosures of 102,399 and 102,401 cells; a
  GameTest checks a large hall reports open; Bench measures fill cost.

*2026-10-31: Implemented. `geo_020_bounded_enclosure_test` (Unit) checks
65,535, 65,536 and 65,537 cells; `geo_020_hall_too_big_to_enclose_reports_open`
(GameTest) checks a sealed hall of 67,240 cells classifies as open, and that
its halves zone once a wall parts them; the R0 bench records 7.5 ms per fill
at the cap ([benchmarks](../research/benchmarks.md)).*

*2026-11-08: The bound is set by `[atmos] enclosure_max_cells` in the server
config, 1 to 102,400 cells, default 65,536 ([config](../config.md)). A change
applies from the next atmos tick without a restart (OPS-016): a higher bound
puts the cells found open in doubt, so they are classified again, and zones that
formed under another bound stay as they are. `geo_020_the_bound_comes_from_the_tick_settings`
(Unit) and `geo_020_the_enclosure_limit_applies_on_the_next_atmos_tick`
(GameTest) check a 27-cell room is open at a bound of 26 and zones once it is
27. The S-10 bench measures halls of 65,536 to 102,400 cells for the owner to
set the final range from (owner, 2026-11-08). The engine's two listings that
must hold a whole enclosure, the cells it exports for the debug overlay and the
cells held for a paused breach (GEO-026), are sized from the range's top, so a
zone of any size the key allows is listed whole;
`client_005_a_hall_at_the_largest_cap_is_listed_whole` (Unit) checks it. The
overlay's packet still carries at most 32,768 cells, as before.*

*The default is 102,400 cells, up from 65,536, and the range stays 1 to
102,400 (owner, 2026-11-08). S-10's cap step found an 80 × 80 × 16 hall of
102,400 cells within every budget
([benchmarks](../research/benchmarks.md#the-final-run-2026-11-08)). The
engine's own default follows, so headless runs and the game agree.
`geo_020_bounded_enclosure_test` now checks 102,399, 102,400 and 102,401 cells
at the default; `geo_020_hall_too_big_to_enclose_reports_open` checks a hall
of 104,040 cells classifies as open, and that its halves of 51,000 zone. A
server whose config file already holds 65,536 keeps it until that line is
changed or removed. A fill must now find more cells before it calls a region
open, so where few chunks are loaded it can take more atmos ticks to fetch
sections (GEO-028), or reach unloaded chunks first, which pauses a breached
zone (GEO-024, GEO-026). Three GameTests whose rooms open onto the air of the few
chunks they load saw this: two now wait an atmos tick or two longer, and
`data_001_a_saved_world_keeps_every_kind_of_state` runs at a bound of 65,536.*

#### GEO-024 · Fills that reach unloaded chunks
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-24, D-43

A fill that reaches an unloaded chunk shall leave the region *unresolved*, not
open. No zone forms for it until its chunks are loaded and it can be
classified.

- **Rationale:** Decision: D-24 and D-43.
- **Verify:** A GameTest checks a room reaching an unloaded chunk stays
  unresolved, and forms a zone once the chunk loads.

#### GEO-021 · Open-column classification
`Should` · `R1` · `Implemented` · Verify: Bench, GameTest · Trace: S-11

A cell with no gas-tight face above it in its column, up to the build height,
shall be classified as open without a fill. Sky light is never used, because
it passes through glass.

- **Rationale:** Inferred: most surface cells are open to the sky, and a
  column check is far cheaper than a fill. Sky light passes glass, so it would
  wrongly open greenhouses (S-11).
- **Verify:** A GameTest checks an open-sky cell classifies without a fill and
  a glass-roofed greenhouse is enclosed; Bench measures the saving.

*2026-11-05: Implemented. `geo_021_open_sky_classifies_without_a_fill` checks a
cell under open sky takes no fill while one under a lid takes one, and
`geo_021_glass_roofed_greenhouse_is_enclosed` checks a room under glass, which
sky light passes, is zoned. The column check takes 0.8 µs where a fill takes
5.6 ms ([bench](../research/benchmarks.md#open-classification-geo-021-and-geo-027)).
Where the mirror doesn't reach the top of the column, the classification waits
a tick for those sections rather than start a fill, since a fill couldn't find
the region enclosed (`geo_021_beside_a_zone_the_add_waits_for_the_column`,
`geo_021_a_breach_onto_open_sky_takes_no_fill`).*

#### GEO-027 · Cached classification
`Should` · `R1` · `Implemented` · Verify: Bench, GameTest

A cell already classified as open shall be classified from the cache, without
a fill, until a block change invalidates that classification.

- **Rationale:** Inferred: open-air devices and leaks act on the same cells
  every tick, and refilling each time wastes work.
- **Verify:** A GameTest checks a second classification of an open cell does
  no fill and a block change invalidates it; Bench measures the saving.

*2026-11-05: Implemented.
`geo_027_open_cell_takes_no_second_fill_until_a_block_changes` checks both. The
cache takes 20 ns where a fill takes 5.4 ms
([bench](../research/benchmarks.md#open-classification-geo-021-and-geo-027)). A
classification rests on the sections it looked at, so a block change in any of
them invalidates it, even one that leaves the way out open; a chunk unloading
does too. Sections the worker doesn't mirror count:
`geo_027_block_change_in_a_section_not_mirrored_invalidates_it`.*

#### GEO-022 · Off-thread classification
`Must` · `R0` · `Implemented` · Verify: Bench, GameTest · Trace: D-30, CORE-045

Enclosure classification shall run off the server thread, on world snapshots.

- **Rationale:** Decision: D-30.
- **Verify:** Bench checks a 65,536-cell fill doesn't stall the server thread;
  GameTests check the results.

*2026-10-31: Implemented. The R0 bench shows a 65,536-cell fill runs on the
worker with at most 0.71 ms of server-thread time per round
([benchmarks](../research/benchmarks.md)). `geo_022_classification_results_from_worker`
(GameTest) checks classification happens on the atmos worker, never at batch
close on the server thread, and that a sealed room zones and a room with a
hole goes to the ambient; `geo_024_room_reaching_unloaded_chunk_is_unresolved`
covers the unresolved result.*

#### GEO-028 · Operations wait for classification
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: CORE-036

Gas operations waiting on classification shall be buffered and applied once
it completes, without any loss of gas.

- **Rationale:** Inferred: classification runs off-thread (GEO-022), so
  operations arrive before the answer and must wait without losing gas
  (CORE-036).
- **Verify:** A GameTest adds gas in a new sealed room and checks the zone
  that forms holds all of it.

#### GEO-023 · Breach into an enclosed pocket
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: S-02

When a zone's face opens onto unzoned cells that classify as an enclosed
pocket, the pocket shall join the zone, bringing ambient gas (CORE-028).

- **Rationale:** vg: newly opened cells join the ZAS zone; an enclosed unzoned
  pocket does the same, bringing ambient air ([zas-core
  §10](../research/zas-core.md#10-mapping-zas-concepts-to-minecraft-draft-for-requirements-discussion)).
- **Verify:** A GameTest breaks the wall between a zone and a sealed unzoned
  room and checks one zone results with the zone's moles plus the room's
  ambient share.

*2026-11-05: A zone can also come to open onto a pocket with nothing changing
beside it, when it vents through a corridor and the corridor's far end closes.
A block change now puts the cells found open in its section in doubt, and those
a zone opens onto are classified again, at most once every 8 atmos ticks. The
zone goes on venting until that's known, and doesn't pause
(`geo_023_closing_the_far_end_of_what_a_zone_vents_through_makes_it_a_pocket`,
`geo_023_a_way_out_closing_just_after_a_check_is_found_at_the_next`,
`geo_023_a_chunk_unloading_on_the_way_out_doesnt_stop_a_zone_venting`).*

*2026-11-06: The owner chose to check more often. Cells in doubt are now checked at
most once every 2 atmos ticks, not 8, so a room whose way out closes vents at most
about 2 s too long at the default atmos tick, not about 8 s. A check that has to
fetch sections fills again the next tick whatever the gap, which can find a closing
sooner. `geo_023_a_way_out_closing_just_after_a_check_is_found_at_the_next` now uses
a way out that needs no fetching, so that it sees the gap itself.*

#### GEO-025 · Breach to the open
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: S-02

When a zone's face opens onto unzoned cells that classify as open, the opening
shall become an ambient edge.

- **Rationale:** vg: an opening to space becomes a ZAS reservoir edge
  ([zas-core
  §10](../research/zas-core.md#10-mapping-zas-concepts-to-minecraft-draft-for-requirements-discussion)).
- **Verify:** A GameTest breaks a window to open air and checks an ambient
  edge forms and the room vents (S-02).

#### GEO-026 · Breach into an unresolved region
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-43

When a zone's face opens onto an unresolved region (GEO-024), the zone shall
pause (GEO-030) until the region can be classified.

- **Rationale:** Decision: D-43.
- **Verify:** A GameTest opens a zone into a region that reaches an unloaded
  chunk and checks the zone pauses, then resumes once the chunk loads.

## Boundaries and lifecycle

#### GEO-030 · Paused zones
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: S-09, D-24, D-43

A zone with any cell in an unloaded chunk shall pause as a whole: no exchange,
reactions, heat transfer or device action inside it.

- **Rationale:** Decision: D-24 and D-43.
- **Verify:** A GameTest unloads part of a zone and checks nothing in it
  changes over 10 s.

#### GEO-033 · Resuming unchanged
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: S-09, D-24

A paused zone shall resume with its mixture unchanged once all of its chunks
are loaded.

- **Rationale:** Decision: D-24 (S-09).
- **Verify:** A GameTest pauses a zone, reloads its chunk and checks the
  mixture is identical.

#### GEO-034 · No force-loading
`Must` · `R0` · `Implemented` · Verify: Inspect, GameTest · Trace: D-24

The engine shall never force chunks to stay loaded.

- **Rationale:** Decision: D-24.
- **Verify:** Inspect the code for chunk tickets; a GameTest checks the ticket
  count doesn't change.

*2026-11-04: Still Implemented, with a bug fixed. Reading a block through the
level adds a ticket, so `Burners` and `FireIntents` pulled chunks that had
left full status back up. They now read such chunks only through
`getChunkNow`, and a fire waits while its chunk isn't full. The GameTest
`geo_034_a_fire_waits_while_its_chunk_isnt_full` checks the chunks' ticket
levels don't change.*

#### GEO-035 · Last-known state at paused interfaces
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-43

Anything that interacts with a paused zone shall see the zone's last-known
state (composition, pressure, temperature) at that interface. This includes
devices, pipes, active zones across an edge, breathing entities and API
queries. An unresolved region's last-known state is the ambient.

- **Rationale:** Decision: D-43.
- **Verify:** A GameTest checks a breathing player, a device and an API query
  at a paused zone all see its last-known state.

*2026-11-05: Implemented. Devices and open pipe ends used to wait at a paused
cell, so a network stopped rather than running against the last-known state as
D-43 and GEO-036 ask. They now act on it, and the zone doesn't change. A device
still waits at an unresolved cell, as a breather does (LIFE-048), because the
ambient there is only a guess; queries report it as the ambient. The heater,
air conditioner, pipe bursts and container failure keep their own stated rules
for a paused cell. GameTests: `geo_035_queries_see_last_known_state`,
`geo_035_a_breathing_player_sees_last_known_state` and
`geo_035_an_active_zone_across_an_edge_sees_last_known_state` in
`PausedZoneTests`; `PausedZoneDeviceTests.geo_035_*` for the passive vent, open
pipe end, vent, scrubber, canister and portable scrubber, and the wait at an
unresolved cell.*

#### GEO-036 · Gas into paused zones is voided
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-43 · Depends: CORE-036

Gas sent into a paused zone or an unresolved region shall be voided. Rates are
computed against the last-known state (GEO-035), so pipe networks keep running
and no pressure builds up against a zone that can't absorb gas.

- **Rationale:** Decision: D-43.
- **Verify:** A GameTest vents into a paused zone and checks the zone is
  unchanged and the checker records the gas as voided.

#### GEO-038 · Paused zones supply without depleting
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-43 · Depends: CORE-036

Gas drawn out of a paused zone or an unresolved region shall be supplied at
its last-known state (GEO-035) without depleting it, as the ambient is.

- **Rationale:** Decision: D-43.
- **Verify:** A GameTest draws from a paused zone and checks it is unchanged
  when it resumes.

#### GEO-037 · Changes to paused zones are held
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-43

Block changes touching a paused zone shall be held and processed when the zone
resumes.

- **Rationale:** Decision: D-43.
- **Verify:** A GameTest breaks a wall of a paused zone in its loaded part and
  checks the change applies when the zone resumes.

#### GEO-031 · World limits
`Should` · `R1` · `Implemented` · Verify: GameTest

Faces that lead outside the world's build height or world border shall count
as open to ambient. Each dimension may override this.

- **Rationale:** Inferred: nothing beyond the build height or world border can
  hold gas, but some dimensions, such as a station with a ceiling, want it
  sealed.
- **Verify:** A GameTest checks a room touching the build limit vents, and
  that a dimension override seals it.

*2026-11-05: Implemented. A dimension overrides it with `sealed_world_limits`
in its ambient file (docs/datapacks.md). `geo_031_a_room_touching_the_build_limit_vents`
and `geo_031_a_dimension_override_seals_the_build_limit` are the plan's tests;
`geo_031_the_default_world_border_is_open` and
`geo_031_a_world_border_moved_in_counts_at_once` check the border, and
`WorldLimitsTest` checks limits that change.*

#### GEO-032 · Geometry audit
`Should` · `R0` · `Implemented` · Verify: GameTest

A debug command shall check, for a given region, that zone membership and
connections match the actual blocks, and report any differences.

- **Rationale:** Inferred: zones out of step with the blocks are the hardest
  bugs to see in play, and an audit makes them reportable.
- **Verify:** A GameTest corrupts a zone's membership and checks the audit
  reports the difference.
