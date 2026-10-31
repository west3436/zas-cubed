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
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-44

Blocks whose shape changes because they are moving or animating, such as
moving pistons and opening shulker boxes, shall be ignored while in motion.
The engine uses the geometry of the state they settle into (for a shulker box,
closed) and doesn't track the animation.

- **Rationale:** Decision: D-44.
- **Verify:** A GameTest moves a piston and opens a shulker box beside a zone
  and checks the cells don't change mid-motion and the open box counts as
  closed.

#### GEO-043 · Position-dependent static shapes
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-44

Block states whose collision shape depends on position or block-entity data,
but doesn't change on its own (bamboo's offset, data-driven modded shapes),
shall be evaluated per position and re-evaluated whenever the block changes
(GEO-010, GEO-014).

- **Rationale:** Decision: D-44.
- **Verify:** A GameTest places bamboo at two positions and checks their cells
  differ, and that a change re-evaluates them.

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
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Depends: GEO-005

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

#### GEO-006 · Fluids
`Should` · `R1` · `Accepted` · Verify: GameTest

A position holding a fluid (a source, flowing fluid, or waterlogged block)
shall hold no gas and be gas-tight on all faces, unless overridden.

- **Rationale:** Inferred: a fluid-filled position has no room for gas, and
  treating it as a wall is simpler than modelling the fluid's volume.
- **Verify:** A GameTest checks a water column seals a room and an overridden
  fluid doesn't.

#### GEO-007 · Zone-boundary blocks
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: [zas-core §2.3](../research/zas-core.md#23-cells-turfdm-atomdm)

When open, these blocks shall pass gas but keep the zones on either side
separate:
- doors
- trapdoors
- blocks tagged `zas:zone_boundary`

- **Rationale:** vg: ZAS's zone-blocked faces for doors ([zas-core
  §2.3](../research/zas-core.md#23-cells-turfdm-atomdm)).
- **Verify:** A GameTest opens a door and a trapdoor between rooms and checks
  gas passes while the zones stay separate.

#### GEO-044 · Cheap boundary toggles
`Must` · `R1` · `Accepted` · Verify: Unit, Bench · Depends: GEO-007

Opening or closing a zone-boundary block shall never trigger a zone split or
rebuild.

- **Rationale:** Inferred: doors toggle constantly, and ZAS already handles
  door cells cheaply ([zas-core
  §3.1](../research/zas-core.md#31-cell-update-algorithm-turfdm59-206)).
- **Verify:** A Unit test checks a toggle causes no split or rebuild; Bench
  checks the cost per toggle is constant.

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
`Must` · `R0` · `Accepted` · Verify: Bench · Trace: CORE-051, CORE-054

A block change that isn't in or next to a zone, and doesn't touch a pending
operation, shall cost a constant amount of work: no flood fill, no graph
update.

- **Rationale:** Decision: D-13. Most block changes are nowhere near a zone,
  so they must cost almost nothing ([neoforge
  §3](../research/neoforge-1.21.1.md#3-block-change-detection)).
- **Verify:** Bench makes mass block changes away from any zone and checks the
  cost per change is constant.

#### GEO-012 · Coalescing
`Must` · `R0` · `Implemented` · Verify: Unit

Multiple changes during one atmos tick shall be coalesced and processed once
in the next geometry phase.

- **Rationale:** vg: ZAS queues changed cells and processes them on the next
  tick ([zas-core §3](../research/zas-core.md#3-control-flow)).
- **Verify:** A Unit test makes 100 changes in one tick and checks one
  geometry pass handles them.

#### GEO-013 · Revalidation on chunk load
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: [neoforge §3](../research/neoforge-1.21.1.md#3-block-change-detection)

Zones touching a chunk shall be revalidated when that chunk loads, to catch
changes that couldn't be observed at write time (offline or worldgen edits).

- **Rationale:** Inferred: offline editors and worldgen change blocks without
  any observable event ([neoforge
  §3](../research/neoforge-1.21.1.md#3-block-change-detection)).
- **Verify:** A GameTest changes a zone's wall while its chunk is unloaded and
  checks the zone is revalidated on load.

#### GEO-014 · Mark-dirty API
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: [neoforge §3](../research/neoforge-1.21.1.md#3-block-change-detection)

The API shall expose a `markDirty(level, pos)` call that makes the engine
re-evaluate that position's geometry, for changes it can't observe, such as
shapes driven by block-entity data.

- **Rationale:** Inferred: block-entity data can change a shape without a
  block-state change ([neoforge
  §3](../research/neoforge-1.21.1.md#3-block-change-detection)).
- **Verify:** A GameTest changes a test block's data-driven shape, calls
  markDirty, and checks the geometry is re-evaluated.

## Enclosure detection

#### GEO-020 · Bounded enclosure test
`Must` · `R0` · `Accepted` · Verify: Unit, GameTest, Bench · Trace: D-23

Whether a seed cell is enclosed shall be decided by a fill through connected
cells, bounded by a configurable maximum volume (default 65,536 cells).
Exceeding the bound means "open to ambient".

- **Rationale:** Decision: D-23.
- **Verify:** Unit tests check enclosures of 65,535 and 65,537 cells; a
  GameTest checks a large hall reports open; Bench measures fill cost.

#### GEO-024 · Fills that reach unloaded chunks
`Must` · `R0` · `Accepted` · Verify: GameTest · Trace: D-24, D-43

A fill that reaches an unloaded chunk shall leave the region *unresolved*, not
open. No zone forms for it until its chunks are loaded and it can be
classified.

- **Rationale:** Decision: D-24 and D-43.
- **Verify:** A GameTest checks a room reaching an unloaded chunk stays
  unresolved, and forms a zone once the chunk loads.

#### GEO-021 · Open-column classification
`Should` · `R1` · `Accepted` · Verify: Bench, GameTest · Trace: S-11

A cell with no gas-tight face above it in its column, up to the build height,
shall be classified as open without a fill. Sky light is never used, because
it passes through glass.

- **Rationale:** Inferred: most surface cells are open to the sky, and a
  column check is far cheaper than a fill. Sky light passes glass, so it would
  wrongly open greenhouses (S-11).
- **Verify:** A GameTest checks an open-sky cell classifies without a fill and
  a glass-roofed greenhouse is enclosed; Bench measures the saving.

#### GEO-027 · Cached classification
`Should` · `R1` · `Accepted` · Verify: Bench, GameTest

A cell already classified as open shall be classified from the cache, without
a fill, until a block change invalidates that classification.

- **Rationale:** Inferred: open-air devices and leaks act on the same cells
  every tick, and refilling each time wastes work.
- **Verify:** A GameTest checks a second classification of an open cell does
  no fill and a block change invalidates it; Bench measures the saving.

#### GEO-022 · Off-thread classification
`Must` · `R0` · `Accepted` · Verify: Bench, GameTest · Trace: D-30, CORE-045

Enclosure classification shall run off the server thread, on world snapshots.

- **Rationale:** Decision: D-30.
- **Verify:** Bench checks a 65,536-cell fill doesn't stall the server thread;
  GameTests check the results.

#### GEO-028 · Operations wait for classification
`Must` · `R0` · `Accepted` · Verify: GameTest · Trace: CORE-036

Gas operations waiting on classification shall be buffered and applied once
it completes, without any loss of gas.

- **Rationale:** Inferred: classification runs off-thread (GEO-022), so
  operations arrive before the answer and must wait without losing gas
  (CORE-036).
- **Verify:** A GameTest adds gas in a new sealed room and checks the zone
  that forms holds all of it.

#### GEO-023 · Breach into an enclosed pocket
`Must` · `R0` · `Accepted` · Verify: GameTest · Trace: S-02

When a zone's face opens onto unzoned cells that classify as an enclosed
pocket, the pocket shall join the zone, bringing ambient gas (CORE-028).

- **Rationale:** vg: newly opened cells join the ZAS zone; an enclosed unzoned
  pocket does the same, bringing ambient air ([zas-core
  §10](../research/zas-core.md#10-mapping-zas-concepts-to-minecraft-draft-for-requirements-discussion)).
- **Verify:** A GameTest breaks the wall between a zone and a sealed unzoned
  room and checks one zone results with the zone's moles plus the room's
  ambient share.

#### GEO-025 · Breach to the open
`Must` · `R0` · `Accepted` · Verify: GameTest · Trace: S-02

When a zone's face opens onto unzoned cells that classify as open, the opening
shall become an ambient edge.

- **Rationale:** vg: an opening to space becomes a ZAS reservoir edge
  ([zas-core
  §10](../research/zas-core.md#10-mapping-zas-concepts-to-minecraft-draft-for-requirements-discussion)).
- **Verify:** A GameTest breaks a window to open air and checks an ambient
  edge forms and the room vents (S-02).

#### GEO-026 · Breach into an unresolved region
`Must` · `R0` · `Accepted` · Verify: GameTest · Trace: D-43

When a zone's face opens onto an unresolved region (GEO-024), the zone shall
pause (GEO-030) until the region can be classified.

- **Rationale:** Decision: D-43.
- **Verify:** A GameTest opens a zone into a region that reaches an unloaded
  chunk and checks the zone pauses, then resumes once the chunk loads.

## Boundaries and lifecycle

#### GEO-030 · Paused zones
`Must` · `R0` · `Accepted` · Verify: GameTest · Trace: S-09, D-24, D-43

A zone with any cell in an unloaded chunk shall pause as a whole: no exchange,
reactions, heat transfer or device action inside it.

- **Rationale:** Decision: D-24 and D-43.
- **Verify:** A GameTest unloads part of a zone and checks nothing in it
  changes over 10 s.

#### GEO-033 · Resuming unchanged
`Must` · `R0` · `Accepted` · Verify: GameTest · Trace: S-09, D-24

A paused zone shall resume with its mixture unchanged once all of its chunks
are loaded.

- **Rationale:** Decision: D-24 (S-09).
- **Verify:** A GameTest pauses a zone, reloads its chunk and checks the
  mixture is identical.

#### GEO-034 · No force-loading
`Must` · `R0` · `Accepted` · Verify: Inspect, GameTest · Trace: D-24

The engine shall never force chunks to stay loaded.

- **Rationale:** Decision: D-24.
- **Verify:** Inspect the code for chunk tickets; a GameTest checks the ticket
  count doesn't change.

#### GEO-035 · Last-known state at paused interfaces
`Must` · `R0` · `Accepted` · Verify: GameTest · Trace: D-43

Anything that interacts with a paused zone shall see the zone's last-known
state (composition, pressure, temperature) at that interface. This includes
devices, pipes, active zones across an edge, breathing entities and API
queries. An unresolved region's last-known state is the ambient.

- **Rationale:** Decision: D-43.
- **Verify:** A GameTest checks a breathing player, a device and an API query
  at a paused zone all see its last-known state.

#### GEO-036 · Gas into paused zones is voided
`Must` · `R0` · `Accepted` · Verify: GameTest · Trace: D-43 · Depends: CORE-036

Gas sent into a paused zone or an unresolved region shall be voided. Rates are
computed against the last-known state (GEO-035), so pipe networks keep running
and no pressure builds up against a zone that can't absorb gas.

- **Rationale:** Decision: D-43.
- **Verify:** A GameTest vents into a paused zone and checks the zone is
  unchanged and the checker records the gas as voided.

#### GEO-038 · Paused zones supply without depleting
`Must` · `R0` · `Accepted` · Verify: GameTest · Trace: D-43 · Depends: CORE-036

Gas drawn out of a paused zone or an unresolved region shall be supplied at
its last-known state (GEO-035) without depleting it, as the ambient is.

- **Rationale:** Decision: D-43.
- **Verify:** A GameTest draws from a paused zone and checks it is unchanged
  when it resumes.

#### GEO-037 · Changes to paused zones are held
`Must` · `R0` · `Accepted` · Verify: GameTest · Trace: D-43

Block changes touching a paused zone shall be held and processed when the zone
resumes.

- **Rationale:** Decision: D-43.
- **Verify:** A GameTest breaks a wall of a paused zone in its loaded part and
  checks the change applies when the zone resumes.

#### GEO-031 · World limits
`Should` · `R1` · `Accepted` · Verify: GameTest

Faces that lead outside the world's build height or world border shall count
as open to ambient. Each dimension may override this.

- **Rationale:** Inferred: nothing beyond the build height or world border can
  hold gas, but some dimensions, such as a station with a ceiling, want it
  sealed.
- **Verify:** A GameTest checks a room touching the build limit vents, and
  that a dimension override seals it.

#### GEO-032 · Geometry audit
`Should` · `R0` · `Accepted` · Verify: GameTest

A debug command shall check, for a given region, that zone membership and
connections match the actual blocks, and report any differences.

- **Rationale:** Inferred: zones out of step with the blocks are the hardest
  bugs to see in play, and an audit makes them reportable.
- **Verify:** A GameTest corrupts a zone's membership and checks the audit
  reports the difference.
