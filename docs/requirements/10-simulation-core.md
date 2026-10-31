# 10 · Simulation core (`CORE`)

**Status:** Accepted 2026-10-30 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules).

**Scope.** The engine's gas model, gas registry, zones, edges, the ambient
reservoir, and the scheduler. Geometry (which cells exist and how they connect)
is in [GEO](11-world-geometry.md). Where ambients come from is in
[ENV](15-environments.md).

**Background:** [zas-core](../research/zas-core.md) §2–§3 and §10.

## Gas mixture model

#### CORE-001 · Gas mixture state
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03

The engine shall represent a gas mixture as moles per gas type, a temperature
(K) and a volume (L). It shall derive total moles and pressure (P = nRT/V) from
that state.

- **Rationale:** vg: ZAS stores a mixture as moles per gas, a temperature and
  a volume, and derives pressure from the ideal gas law ([zas-core
  §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)).
- **Verify:** Unit tests build mixtures and check total moles and P = nRT/V
  against hand-computed values.

#### CORE-002 · Energy-conserving merge
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03

Merging two mixtures shall conserve total thermal energy, so the resulting
temperature is the heat-capacity-weighted mean.

- **Rationale:** vg: ZAS merges at the heat-capacity-weighted temperature
  ([zas-core §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)).
- **Verify:** Unit tests merge mixtures of different temperatures and
  compositions and check that total thermal energy is unchanged.

#### CORE-008 · Mole-conserving merge
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03

Merging two mixtures shall conserve the moles of each gas.

- **Rationale:** vg: ZAS adds each gas's moles on merge ([zas-core
  §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)).
- **Verify:** Unit tests merge mixtures and check each gas's moles equal the
  sum of the inputs.

#### CORE-003 · Heat capacity
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03

A mixture's heat capacity shall be the sum, over gases, of moles times that
gas's molar heat capacity.

- **Rationale:** vg: ZAS computes C = Σ cᵢnᵢ ([zas-core
  §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)).
- **Verify:** Unit tests compare the heat capacity of known mixtures with
  hand-computed values.

#### CORE-009 · Heat capacity floor
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: [zas-core §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)

A mixture's heat capacity shall never be less than a small positive floor
(default 0.0003 J/K, as in ZAS), so an empty mixture never divides by zero.

- **Rationale:** vg: ZAS floors heat capacity at 0.0003 J/K ([zas-core
  §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)).
- **Verify:** Unit tests check an empty mixture reports the floor and that
  adding energy to it gives a finite temperature.

#### CORE-004 · Removal operations
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03

The engine shall support removing gas by moles, by fraction, and by volume.

- **Rationale:** vg: ZAS removes gas by moles, by ratio and by volume, and
  devices, breathing and fire each need one of them ([zas-core
  §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)).
- **Verify:** Unit tests run each removal mode on a known mixture and check
  the removed and remaining amounts.

#### CORE-060 · Removed parcel temperature
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03

A parcel removed from a mixture shall have the source mixture's temperature.

- **Rationale:** vg: ZAS's removed parcel keeps the source temperature
  ([zas-core §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)).
- **Verify:** Unit tests check a removed parcel's temperature equals the
  source's.

#### CORE-061 · Removal never goes negative
`Must` · `R0` · `Implemented` · Verify: Unit (fuzz) · Trace: [zas-core §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)

A removal that asks for more than is present shall remove only what is
present, never leaving any gas with a negative amount.

- **Rationale:** vg: ZAS clamps a removal to what is available ([zas-core
  §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)).
- **Verify:** A Unit fuzz test runs random removals on random mixtures and
  asserts no gas ever goes negative.

#### CORE-005 · Thermal energy changes
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03, D-05

The engine shall support adding and removing thermal energy, changing the
mixture's temperature by ΔE/C.

- **Rationale:** vg: ZAS changes temperature by ΔE/C ([zas-core
  §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)); heat
  conduction (D-05) builds on it.
- **Verify:** Unit tests add and remove energy and check ΔT = ΔE/C.

#### CORE-062 · Temperature floor
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03

Removing thermal energy shall never take a mixture below a configurable
temperature floor (default 2.7 K). The operation returns the energy actually
removed.

- **Rationale:** vg: ZAS never cools a mixture below the cosmic background
  temperature ([zas-core
  §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)).
- **Verify:** Unit tests remove more energy than a mixture has and check it
  stops at the floor, reports the energy actually removed, and honours a
  configured floor.

#### CORE-006 · Equivalence test
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03 · [zas-core §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)

Two mixtures shall count as equivalent unless their temperature, pressure, or
any single gas's molar density differs by more than *both* an absolute and a
relative tolerance. The tolerances are configurable, with ZAS's values as
defaults:

| Quantity | Absolute | Relative |
|---|---|---|
| Temperature | 4 K | 1.2 % |
| Pressure | 0.1 kPa | 5 % |
| Each gas's molar density | 0.0021 mol/L (5 % of standard air) | 5 % |

- **Rationale:** vg: ZAS's compare test, with these tolerances, drives zone
  merging and edge sleeping ([zas-core
  §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)).
- **Verify:** Table-driven Unit tests check pairs just inside and just outside
  each absolute and relative tolerance, and a configured override.

#### CORE-007 · Pruning
`Must` · `R0` · `Implemented` · Verify: Unit

Amounts below a configurable epsilon shall be pruned.

- **Rationale:** vg: ZAS prunes gases that reach zero ([zas-core
  §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)); an epsilon
  replaces exact zero because floating-point removals rarely land on it.
- **Verify:** Unit tests check that amounts below the epsilon are removed and
  amounts above it kept.

#### CORE-063 · No non-finite values
`Must` · `R0` · `Implemented` · Verify: Unit (fuzz)

NaN or infinite values shall never be stored. An operation that would produce
one is rejected and logged.

- **Rationale:** Inferred: one NaN would spread through merges to every
  connected zone and into saves, so it is stopped where it is made.
- **Verify:** A Unit fuzz test feeds extreme and invalid inputs (zero volume,
  huge energies) and asserts stored values stay finite and each rejection is
  logged.

## Gas registry

#### CORE-010 · Data-driven gases
`Must` · `R0` · `Implemented` · Verify: Unit, Inspect · Trace: D-08

Gas types shall be defined in datapacks. Each definition has: an id, a
translatable name, a formula label, molar mass (kg/mol), molar heat capacity
(J/(mol·K)), and optional visibility (colour, density threshold).

- **Rationale:** Decision: D-08 and D-04: gases are real, and addons and
  datapacks can add more. vg defines gases in code ([zas-core
  §2.2](../research/zas-core.md#22-gas-registry-xgmdm-xgm_gasesdm)).
- **Verify:** A Unit test loads a test datapack gas and checks every field;
  Inspect the documented format.

#### CORE-014 · Gas definitions sync to clients
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-08

Gas definitions shall be synced to clients.

- **Rationale:** Inferred: the client needs gas names, formulas and colours
  for the HUD and analyzer, and datapack gases exist only on the server until
  synced.
- **Verify:** A GameTest adds a gas by datapack and checks the client-side
  registry holds it with the same fields.

#### CORE-011 · Built-in real gases
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-08, D-42

ZAS³ shall ship built-in definitions for exactly these gases: O₂, N₂, CO₂,
N₂O, H₂O (vapor), H₂, CO and CH₄. Compat modules, addons and datapacks may add
more.

- **Rationale:** Decision: D-08 and D-42.
- **Verify:** Inspect the shipped data: exactly these eight gas definitions.

#### CORE-015 · Cited gas constants
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-08 · [zas-core §2.2](../research/zas-core.md#22-gas-registry-xgmdm-xgm_gasesdm)

Each built-in gas's molar mass and molar heat capacity shall cite a reference
source, not vgstation13's values.

- **Rationale:** Decision: D-08 (real gases). vg's constants are not accurate
  enough to reuse; its N₂O is 40 J/(mol·K) against about 30.4 ([zas-core
  §2.2](../research/zas-core.md#22-gas-registry-xgmdm-xgm_gasesdm)).
- **Verify:** Inspect each built-in gas definition for a cited source whose
  values match.

#### CORE-012 · Gas classification tags
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-04

Gases shall be groupable by tags (for example breathable/oxidizer, toxic,
flammable, inert, visible) that hazard, fire, and device logic consume.

- **Rationale:** vg: ZAS gives each gas flags and a safety check ([zas-core
  §2.2](../research/zas-core.md#22-gas-registry-xgmdm-xgm_gasesdm)). Tags are
  the Minecraft equivalent, and let hazard, fire and device logic handle gases
  added by others (D-04).
- **Verify:** Inspect the shipped tags; the LIFE, FIRE and CONT tests exercise
  the logic that reads them.

#### CORE-013 · Safe gas set changes
`Should` · `R1` · `Accepted` · Verify: GameTest

Removing a gas definition that saved zones still contain shall not corrupt the
world. That gas's moles are dropped.

- **Rationale:** Inferred: removing a datapack or addon is a normal server
  operation and must not break a world.
- **Verify:** A GameTest saves a zone holding a datapack gas, removes the
  datapack and reloads, then checks the world loads and the zone keeps its
  other gases.

#### CORE-017 · Optional phase data
`Should` · `R1` · `Accepted` · Verify: Inspect, Unit · Trace: D-58, CORE-015

Gas definitions shall accept optional phase data: melting and boiling points
at 101.325 kPa (K), and latent heats of fusion and vaporization (J/mol). The
built-in gases ship it with cited values (CORE-015). R1 doesn't use it, so a
later update can add phase changes (HEAT-018) without a format break.

- **Rationale:** Decision: D-58.
- **Verify:** Inspect the built-in definitions for cited phase data; a Unit
  test loads definitions with and without it.

#### CORE-016 · Dropped-gas warning
`Should` · `R1` · `Accepted` · Verify: GameTest

When gas is dropped under CORE-013, a warning shall name the gas, the amount
and the location.

- **Rationale:** Inferred: gas vanishing silently would look like a
  conservation bug, so admins are told.
- **Verify:** The CORE-013 GameTest checks the log names the gas, the amount
  and the location.

## Zones

#### CORE-020 · Zone definition
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03

A zone shall be a connected set of cells that share one mixture.

- **Rationale:** vg: a ZAS zone is a set of cells with one mixture ([zas-core
  §2.4](../research/zas-core.md#24-zones-zonedm)).
- **Verify:** Unit tests build a cell topology and check connected zoned cells
  share one mixture.

#### CORE-027 · Zone volume
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03

A zone's mixture volume shall be the sum of its cells' gas volumes (GEO-002).

- **Rationale:** vg: a ZAS zone's volume is the sum of its cells' volumes
  ([zas-core §2.4](../research/zas-core.md#24-zones-zonedm)).
- **Verify:** Unit tests check a zone of full and partial cells has the summed
  volume.

#### CORE-021 · Joining cells
`Must` · `R0` · `Implemented` · Verify: Unit

When a cell joins a zone, the cell's gas shall be merged into the zone's
mixture.

- **Rationale:** vg: adding a cell to a ZAS zone merges the cell's air into
  the zone ([zas-core §2.4](../research/zas-core.md#24-zones-zonedm)).
- **Verify:** Unit tests join a cell holding known gas and check the zone's
  moles and temperature.

#### CORE-028 · Unzoned cells bring ambient gas
`Must` · `R0` · `Implemented` · Verify: Unit

A previously unzoned cell that joins a zone shall contribute the ambient gas
for its volume.

- **Rationale:** Decision: D-13. Unzoned cells hold the ambient implicitly
  ([zas-core
  §10](../research/zas-core.md#10-mapping-zas-concepts-to-minecraft-draft-for-requirements-discussion)),
  so a joining cell brings ambient gas for its volume.
- **Verify:** Unit tests check an unzoned cell that joins adds exactly the
  ambient moles for its volume.

#### CORE-022 · Displaced gas is conserved
`Must` · `R0` · `Implemented` · Verify: Unit, GameTest · Trace: D-41

When a cell stops being a cell (for example a solid block is placed in it),
its share of the gas shall stay in the zone, so the zone's pressure rises.

- **Rationale:** Decision: D-41.
- **Verify:** A Unit test fills one cell of a zone and checks total moles are
  unchanged and pressure rises by V/(V − v); a GameTest places a block in a
  sealed room and checks the pressure rises.

#### CORE-029 · ZAS displaced-gas option
`Should` · `R1` · `Accepted` · Verify: Unit · Trace: D-41

A config option shall instead delete a displaced cell's gas, as ZAS does.

- **Rationale:** Decision: D-41 keeps ZAS's behaviour, deleting the gas, as an
  option ([zas-core §2.4](../research/zas-core.md#24-zones-zonedm)).
- **Verify:** A Unit test with the option on checks filling a cell removes its
  volume share of the moles.

#### CORE-075 · Displaced gas from a zone's last cell
`Must` · `R0` · `Accepted` · Verify: Unit, GameTest · Trace: D-41 · Depends: CORE-022, CORE-065

If the filled cell was its zone's last cell, its gas shall move into the zones
that cell was connected to, shared in proportion to their volumes. If there
were none, the gas is released to the ambient.

- **Rationale:** Inferred: D-41 conserves displaced gas, but when a zone's
  last cell fills there is no zone left to hold it, so it goes to the
  neighbouring zones or the ambient.
- **Verify:** Unit tests and a GameTest fill a one-cell zone between two zones
  and check the moles split by volume, and with no neighbours that they go to
  the ambient.

#### CORE-023 · Zone merge
`Must` · `R0` · `Implemented` · Verify: Unit, GameTest · Trace: [zas-core §3.2](../research/zas-core.md#32-edge-exchange-math)

Two zones joined by at least one direct opening shall merge into one zone once
their mixtures are equivalent (CORE-006).

- **Rationale:** vg: ZAS merges directly connected zones once their mixtures
  are equivalent ([zas-core
  §3.2](../research/zas-core.md#32-edge-exchange-math)).
- **Verify:** A Unit test and a GameTest join rooms at 100 and 50 kPa by one
  opening and check they become one zone with total moles conserved.

#### CORE-024 · Zone split
`Must` · `R0` · `Implemented` · Verify: Unit, GameTest

When a geometry change disconnects a zone, each connected remnant shall become
its own zone.

- **Rationale:** vg: ZAS rebuilds a split zone, and disconnected remnants
  become separate zones ([zas-core
  §2.4](../research/zas-core.md#24-zones-zonedm)).
- **Verify:** A Unit test and a GameTest wall a room in two and check two
  zones result.

#### CORE-064 · Gas shared on split
`Must` · `R0` · `Implemented` · Verify: Unit

Each remnant of a split shall keep the original mixture's composition and
temperature, with moles in proportion to its volume.

- **Rationale:** vg: ZAS gives each cell a per-volume copy of the zone air
  when it rebuilds ([zas-core §2.4](../research/zas-core.md#24-zones-zonedm)).
- **Verify:** A Unit test splits a zone 1:3 by volume and checks equal
  composition and temperature and moles in a 1:3 ratio.

#### CORE-025 · Local split detection
`Must` · `R1` · `Accepted` · Verify: Unit, Bench · Trace: [zas-core §9](../research/zas-core.md#9-quirks-dont-replicate-blindly)

A change inside a zone that leaves the zone connected shall be resolved
without visiting every cell of the zone. The bound depends on the size of the
neighbourhood around the change, not on the zone's size.

- **Rationale:** Inferred: ZAS rebuilds a whole zone on any interior blockage
  ([zas-core §9](../research/zas-core.md#9-quirks-dont-replicate-blindly),
  quirk 1), which is too slow for zones of 8,000 cells (S-10).
- **Verify:** A Unit test counts the cells visited when a block is placed
  inside zones of growing size and checks the count doesn't grow with the
  zone; Bench covers it in S-10.

#### CORE-026 · Zone identity
`Must` · `R0` · `Implemented` · Verify: Unit

Every zone shall have an id that is unique within its dimension and stable for
the zone's lifetime.

- **Rationale:** Inferred: the API, events, debug tools and persistence all
  refer to zones across ticks.
- **Verify:** Unit tests check ids are unique across many creations and
  destructions and unchanged while a zone lives.

#### CORE-065 · Empty zones are destroyed
`Must` · `R0` · `Implemented` · Verify: Unit

A zone with no cells shall be destroyed.

- **Rationale:** Inferred: a zone with no cells holds no gas and would only
  leak memory and stale edges.
- **Verify:** A Unit test removes every cell of a zone and checks the zone is
  gone from the graph.

## Edges and exchange

#### CORE-030 · Edges
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03

All connections between the same pair of zones, or between a zone and the
same ambient, shall be aggregated into one edge.

- **Rationale:** vg: ZAS aggregates connections into edges ([zas-core
  §2.5](../research/zas-core.md#25-connections-and-edges-connectiondm-connectiongroupdm)).
- **Verify:** A Unit test gives two zones three openings and checks they share
  one edge with three connections.

#### CORE-037 · Edge connection counts
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03

Each edge shall track how many connections it has and how many of them are
direct.

- **Rationale:** vg: ZAS edges count their connections and direct connections
  ([zas-core
  §2.5](../research/zas-core.md#25-connections-and-edges-connectiondm-connectiongroupdm)).
- **Verify:** Unit tests add direct and door connections, toggle a door, and
  check both counts.

#### CORE-031 · Exchange rule
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03 · [zas-core §3.2](../research/zas-core.md#32-edge-exchange-math)

Each atmos tick, an active zone-to-zone edge shall exchange gas using a
sharing ratio looked up from its connection count and weighted by the two
volumes. The lookup table is configurable. Its default is derived from ZAS's
2 s values (0.30–0.66 for 1 to 6 or more connections) so that sharing
proceeds at the same real-time rate at any period T (CORE-046):
r_T = 1 − (1 − r_ZAS)^(T / 2 s).

| Connections | 1 | 2 | 3 | 4 | 5 | ≥6 |
|---|---|---|---|---|---|---|
| ZAS, per 2 s | 0.30 | 0.40 | 0.48 | 0.54 | 0.60 | 0.66 |
| Default, per 1 s | 0.163 | 0.225 | 0.279 | 0.322 | 0.368 | 0.417 |

- **Rationale:** vg: ZAS's sharing ratios ([zas-core
  §3.2](../research/zas-core.md#32-edge-exchange-math)), converted to
  per-second rates (D-20).
- **Verify:** Unit tests check one tick's exchange against the formula for
  each connection count, and that two 1 s ticks match one ZAS 2 s tick.

#### CORE-032 · Ambient edges
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03, D-07

A zone shall exchange gas with its ambient across ambient edges.

- **Rationale:** vg: ZAS zones share with reservoir cells ([zas-core
  §3.2](../research/zas-core.md#32-edge-exchange-math)); D-07.
- **Verify:** A Unit test checks a zone beside an ambient moves toward it each
  tick.

#### CORE-038 · Ambient is never changed
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03

Exchange with a zone shall never change the ambient.

- **Rationale:** vg: ZAS reservoirs never change ([zas-core
  §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)).
- **Verify:** A Unit test runs many exchanges and checks the ambient is
  unchanged.

#### CORE-039 · Snap to ambient
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-03 · [zas-core §3.2](../research/zas-core.md#32-edge-exchange-math)

Once a zone is equivalent to its ambient, it shall take the ambient's
composition and temperature exactly.

- **Rationale:** vg: ZAS snaps a zone to the reservoir once they are
  equivalent ([zas-core §3.2](../research/zas-core.md#32-edge-exchange-math)).
- **Verify:** A Unit test checks a zone inside tolerance takes the ambient's
  composition and temperature exactly.

*2026-10-31: CORE-076 (D-63) now governs zones with an ambient edge: one that
reaches the ambient dissolves into it instead of snapping, so its cells hold
the ambient exactly.*

#### CORE-076 · Zones dissolve into the ambient
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-63

A zone with at least one ambient edge shall be destroyed once, after an atmos
tick's exchange, it is nearly equivalent to its ambient: within CORE-006's
tolerances on pressure and on each gas's molar density, and also on
temperature unless it is near-empty (its pressure within the absolute pressure
tolerance, default 0.1 kPa, of both the ambient's and vacuum). Its cells
become unzoned and hold the ambient (CORE-050), and its gas goes to the
ambient, recorded in the conservation ledger as exchange with the ambient
(CORE-036). Zones are dissolved in id order, and a later trigger can zone the
cells again.

- **Rationale:** Decision: D-63. A zone venting to a vacuum keeps its own
  temperature, so under CORE-006 it never matched the ambient and its ambient
  edge exchanged trivial amounts forever.
- **Verify:** Unit tests vent a room into a 2.7 K vacuum and dissolve a room
  near standard air, with the ledger balanced, and check a sealed room equal
  to the ambient and a room 10 K warmer at ambient pressure are kept.

#### CORE-033 · Sleeping edges
`Must` · `R0` · `Implemented` · Verify: Unit

Edges whose two sides are equivalent shall sleep and cost no exchange work.

- **Rationale:** vg: ZAS edges sleep when their sides are equivalent
  ([zas-core §3.2](../research/zas-core.md#32-edge-exchange-math)).
- **Verify:** A Unit test counts exchange calls and checks sleeping edges get
  none.

#### CORE-066 · Waking edges
`Must` · `R0` · `Implemented` · Verify: Unit

Any change to either side of a sleeping edge shall wake it within one atmos
tick.

- **Rationale:** vg: ZAS wakes an edge when a side stops comparing equal
  ([zas-core §3.2](../research/zas-core.md#32-edge-exchange-math)).
- **Verify:** Unit tests change one side of a sleeping edge and check it
  exchanges on the next tick.

*2026-10-30: for ambient edges, a change to the ambient wakes the edge only
once it breaks equivalence (ENV-017), added during the ENV review.*

#### CORE-034 · Door-separated zones never merge
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: [zas-core §3.2](../research/zas-core.md#32-edge-exchange-math)

Zones connected only through zone-boundary openings shall never merge.

- **Rationale:** vg: ZAS never merges across doors (zone-blocked faces,
  [zas-core §2.3](../research/zas-core.md#23-cells-turfdm-atomdm)).
- **Verify:** A Unit test and a GameTest open a door between two rooms and
  check gas passes but they stay two zones.

#### CORE-067 · Door-separated group equalization
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: [zas-core §3.2](../research/zas-core.md#32-edge-exchange-math)

Once zones connected only through zone-boundary openings are equivalent, every
zone reachable through those sleeping edges shall be equalized to one combined
mixture.

- **Rationale:** vg: ZAS equalizes a door-separated group at once ([zas-core
  §3.2](../research/zas-core.md#32-edge-exchange-math)); kept because it is
  cheap ([zas-core
  §9](../research/zas-core.md#9-quirks-dont-replicate-blindly), quirk 6).
- **Verify:** A Unit test and a GameTest link three rooms by open doors and
  check that once equivalent they hold one combined mixture.

#### CORE-035 · Volume-aware venting
`Must` · `R1` · `Accepted` · Verify: Unit · Trace: D-21

By default, the rate at which a zone exchanges gas with the ambient or with a
much larger zone shall be volume-aware: larger zones drain proportionally
slower through the same openings.

- **Rationale:** Decision: D-21.
- **Verify:** A Unit test vents rooms of 10 and 1,000 cells through one
  opening and checks the larger loses a proportionally smaller fraction per
  tick.

#### CORE-068 · Faithful venting option
`Must` · `R1` · `Accepted` · Verify: Unit · Trace: D-21

The faithful ZAS venting model, where the fraction lost per tick doesn't
depend on volume, shall be selectable in config.

- **Rationale:** Decision: D-21 keeps ZAS's venting model ([zas-core
  §3.2](../research/zas-core.md#32-edge-exchange-math)) as an option.
- **Verify:** A Unit test with the option on checks both rooms lose the same
  fraction per tick.

#### CORE-036 · Conservation invariant
`Must` · `R0` · `Accepted` · Verify: Unit (fuzz), GameTest

The moles of each gas shall change only through these sources and sinks:
- API calls
- devices, including leaks from pipes, tanks and canisters
- reactions
- respiration (LIFE-005) and photosynthesis (CONT-053)
- exchange with the ambient: ambient edges (CORE-032), snapping to the
  ambient (CORE-039), open-air operations (CORE-053, CORE-057) and zone
  dissolution (CORE-052, CORE-076)
- pruning below epsilon (CORE-007)
- removed gas definitions (CORE-013)
- the ZAS displaced-gas option (CORE-029)
- gas voided at, or supplied by, paused zones and unresolved regions (GEO-036,
  GEO-038)
- operator commands that reset a region to ambient (OPS-013)

- **Rationale:** Inferred: gas must appear or vanish only through a listed
  source or sink, otherwise players can duplicate or lose gas and every other
  requirement's numbers drift. D-41 and D-43 extended the list.
- **Verify:** A Unit fuzz test runs random operation sequences against a
  per-gas ledger; GameTests run the S-01 and S-02 scenarios with the invariant
  checker (CORE-073) on.

*2026-10-30: added the paused-zone sink and source (GEO-036, GEO-038, D-43)
during the GEO review.*

*2026-10-31: added the operator-command sink (OPS-013) during the OPS review.
Gas-editing commands (OPS-009) count as API calls.*

*2026-10-31: zone dissolution includes zones dissolving into the ambient
(CORE-076, D-63).*

#### CORE-073 · Invariant checker
`Must` · `R0` · `Implemented` · Verify: Unit, GameTest · Depends: CORE-036

A debug checker shall record every change in moles by source or sink category
and report any change larger than the pruning epsilon that no category
explains.

- **Rationale:** Inferred: conservation bugs are silent in play, so a checker
  attributes every change to a category and reports the rest.
- **Verify:** A Unit test injects an unattributed change and checks it is
  reported; a GameTest runs a scenario and checks nothing unexplained is
  reported.

*2026-10-31: operator commands (OPS-013) are a category, attributed to the
player; the checker is switched and read by command (OPS-010). Added during the
OPS review.*

## Ambient and sparse zoning

These requirements define how a world with no "unsimulated" marking still
behaves like ZAS ([zas-core §10](../research/zas-core.md#10-mapping-zas-concepts-to-minecraft-draft-for-requirements-discussion)).

#### CORE-050 · Unzoned cells are ambient
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-07

A query for a cell that belongs to no zone shall return the ambient for that
cell's dimension.

- **Rationale:** vg: ZAS's unsimulated cells report the reservoir's gas
  ([zas-core
  §10](../research/zas-core.md#10-mapping-zas-concepts-to-minecraft-draft-for-requirements-discussion)).
- **Verify:** A Unit test queries an unzoned cell and checks it returns the
  ambient.

#### CORE-051 · Zones on demand
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-13, D-14

A zone shall be created only when a region is enclosed *and* a triggering
operation (CORE-055, CORE-056) modifies its air. Ambient air, natural caves
included, never forms a zone by itself.

Breathing never triggers a zone (LIFE-005). Once a zone exists, every process
affects it.

- **Rationale:** Decision: D-13 and D-14.
- **Verify:** A GameTest checks a sealed Overworld room and a natural cave
  stay unzoned until a vent adds gas to the room, which then forms a zone.

#### CORE-054 · No world scanning
`Must` · `R0` · `Implemented` · Verify: Inspect, Bench · Trace: D-13

Zones shall never be created by scanning the world. Only a triggering
operation starts enclosure detection.

- **Rationale:** Decision: D-13.
- **Verify:** Inspect the code for world scans; Bench checks world generation
  and exploration create no zones and no fills.

*2026-10-31: Implemented. The R0 bench generates 320 chunks of terrain with
sealed caves and makes 20,000 block edits in them next to a zoned station:
no section captured, no fill, no zone ([benchmarks · scaling](../research/benchmarks.md#nfr-003-and-core-054-cost-follows-activity)).
The platform has no world scans: `LevelWorld` only drains dirty sections,
captures sections the engine names and reads chunk generations.*

#### CORE-055 · Normal zone triggers
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-13, D-14

With the Normal preset, these operations shall trigger a zone:
- devices adding, removing or heating gas
- leaks from pipes, tanks and canisters
- API calls
- breaches of an existing zone

- **Rationale:** Decision: D-13 and D-14.
- **Verify:** GameTests run each listed operation in a sealed room under
  Normal and check a zone forms, and that a lit furnace doesn't form one.

#### CORE-056 · Hardcore zone triggers
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-13, D-14, D-33, D-35 · Depends: FIRE-024, CONT-053

With the Hardcore preset, these operations shall trigger a zone, in addition
to the Normal triggers (CORE-055):
- combustion: fires, lit furnaces and campfires, and the ZAS³ fuel generator
  (FIRE-024)
- planting in an enclosed area (CONT-053)

- **Rationale:** Decision: D-13, D-14, D-33 and D-35.
- **Verify:** GameTests under Hardcore check a lit furnace, the generator and
  planting in a sealed room each form a zone.

#### CORE-052 · Zone garbage collection
`Should` · `R1` · `Accepted` · Verify: GameTest

A zone shall be dissolved back to ambient when both hold:
- its mixture has been equivalent to its ambient for a configurable time
- nothing pins it (attached devices, API pins)

- **Rationale:** Inferred: zones exist only where air differs from ambient
  (D-13), so one that has returned to ambient and holds nothing is removed to
  keep the zone count low ([zas-core
  §10](../research/zas-core.md#10-mapping-zas-concepts-to-minecraft-draft-for-requirements-discussion)).
- **Verify:** A GameTest vents a room, opens it to ambient and waits the
  configured time, then checks the zone dissolves and a pinned zone doesn't.

#### CORE-053 · Adding gas to open air
`Must` · `R0` · `Implemented` · Verify: Unit, GameTest

Adding gas at a cell that isn't enclosed shall release the gas into the
ambient. The caller is told where the gas went (API-016).

- **Rationale:** Decision: D-13. Gas added where there is no enclosure goes to
  the ambient reservoir ([zas-core
  §10](../research/zas-core.md#10-mapping-zas-concepts-to-minecraft-draft-for-requirements-discussion)).
- **Verify:** A Unit test and a GameTest add gas at an open cell and check no
  zone forms and the result reports the ambient.

*2026-10-30: reference changed from API-012 to API-016 after the API split.*

#### CORE-057 · Removing gas from open air
`Must` · `R0` · `Implemented` · Verify: Unit, GameTest

Removing gas from a cell that isn't enclosed shall yield gas of the ambient's
composition.

- **Rationale:** Decision: D-13. The ambient is an infinite reservoir, as
  ZAS's unsimulated mixtures are ([zas-core
  §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)).
- **Verify:** A Unit test and a GameTest remove gas at an open cell and check
  it has the ambient's composition.

#### CORE-058 · Open-air events
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: API-012, API-032

Adding gas to or removing gas from open air (CORE-053, CORE-057) shall raise
an API event.

- **Rationale:** Inferred: gas entering or leaving through open air leaves the
  simulation, so listeners and the invariant checker need to see it.
- **Verify:** A GameTest registers a listener and checks it receives an event
  with amount and position for an add and a remove.

## Scheduler

#### CORE-040 · Phase order
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: [zas-core §3](../research/zas-core.md#3-control-flow) · D-31, D-50 · Depends: GEO-012, HEAT-001, FLOW-001, CORE-052

Each atmos tick shall run these phases in order:
1. geometry updates (coalesced block changes, GEO-012)
2. deferred zone-boundary updates
3. queued gas operations: API calls (including devices, D-50), respiration,
   photosynthesis, and operations buffered during classification (GEO-028).
   Zone creation (CORE-051) happens here.
4. edge exchange, recording airflow (FLOW-001)
5. heat conduction (HEAT-001)
6. hotspots
7. zone updates (reactions, events)
8. zone garbage collection (CORE-052)

The results, such as airflow impulses, block changes and events, are then
applied on the server thread (CORE-069). Phases 3, 5 and 8 are additions to
ZAS's order.

- **Rationale:** vg: ZAS's tick order ([zas-core
  §3](../research/zas-core.md#3-control-flow)), plus phases for gas
  operations, heat and garbage collection (D-31, D-50).
- **Verify:** A Unit test instruments one atmos tick and checks the phases run
  in this order.

*2026-10-30: removed the "pipe networks and devices" phase during the CONT
review. Devices run in the content mod on the server thread and reach zones
through API mutations, which arrive in phase 3 (D-50).*

#### CORE-041 · Atmos tick period
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-20

The atmos tick period shall be configurable, with a default of 1 s.

- **Rationale:** Decision: D-20.
- **Verify:** Unit tests check the default is 1 s and that configured periods
  are honoured.

#### CORE-046 · Per-second rates
`Must` · `R0` · `Implemented` · Verify: Unit, Bench · Trace: D-20

Every rate (sharing, venting, reactions, heat) shall be defined per second, so
changing the period changes smoothness and cost but not real-time behaviour.

- **Rationale:** Decision: D-20.
- **Verify:** A Unit test runs one scenario at 0.5, 1 and 2 s periods and
  checks the state after 10 s agrees; Bench records the cost at each period.

#### CORE-047 · Server-thread budget
`Must` · `R0` · `Implemented` · Verify: Bench · Trace: D-30 · Depends: NFR-001

Work that touches the server thread, such as snapshotting and applying
results, shall stay within a per-server-tick budget.

- **Rationale:** Decision: D-30; the 10 ms budget is NFR-002.
- **Verify:** Bench runs S-10 and checks server-thread atmos work stays within
  budget, with the excess deferred.

*2026-10-31: the budget defaults to 10 ms (configurable), and work beyond it
is deferred to the next tick. Added during the NFR review (NFR-002).*

*2026-10-31: Implemented for R0. The headless S-10 engine bench keeps every
batch close and apply under 0.3 ms, an explosion breach included, and a burst
of 1,000 zone-creating adds closes in under 1 ms; with no budget left the
prefetch is deferred and the rooms zone a tick later
([benchmarks · S-10](../research/benchmarks.md#s-10-engine-load)). R0 has no devices,
hazards or sync, so the server thread's atmos work is close and apply plus the
change hook; the full S-10 with R1's work is NFR-001 and NFR-002's bench.*

#### CORE-048 · Exactly-once processing
`Must` · `R0` · `Implemented` · Verify: Unit

No item shall be skipped or processed twice in one atmos tick.

- **Rationale:** Inferred: skipping or repeating an item breaks conservation
  and determinism.
- **Verify:** A Unit test counts how often each zone, edge and operation is
  processed in one tick, including items added mid-tick.

#### CORE-042 · Per-dimension engines
`Must` · `R0` · `Implemented` · Verify: GameTest

Each dimension shall have its own zone graph and scheduler state. Excluding a
dimension from simulation is ENV-007.

- **Rationale:** Inferred: dimensions are separate worlds with their own
  ambients (ENV-001), and a shared graph would couple their ticks.
- **Verify:** A GameTest changes zones in one dimension and pauses another,
  and checks neither affects the other.

#### CORE-043 · Engine independent of Minecraft
`Must` · `R0` · `Implemented` · Verify: Inspect · Trace: D-04, D-30

The gas math, zone graph and scheduler shall depend only on an abstract voxel
topology, not on Minecraft classes. This allows headless unit tests and
off-thread execution.

- **Rationale:** Decision: D-30 and D-04. Off-thread execution and headless
  tests both need an engine free of Minecraft classes.
- **Verify:** Inspect: the build fails if the engine module imports Minecraft
  classes.

#### CORE-045 · Off-thread execution
`Must` · `R0` · `Implemented` · Verify: Unit, GameTest, Bench · Trace: D-30 · [neoforge §5](../research/neoforge-1.21.1.md#5-ticking--threading)

The simulation shall run on worker thread(s), using immutable snapshots of the
world data it needs: chunk-section block data or derived face masks, plus
queued gas operations.

- **Rationale:** Decision: D-30.
- **Verify:** Unit tests and a GameTest check the simulation runs on worker
  threads and gives correct results; Bench measures server-thread versus
  worker time.

#### CORE-049 · No live-world access off-thread
`Must` · `R0` · `Implemented` · Verify: Inspect · Trace: D-30 · [neoforge §5](../research/neoforge-1.21.1.md#5-ticking--threading)

Simulation code shall never read or write the live world, chunks,
capabilities or attachments off the server thread.

- **Rationale:** Decision: D-30. The live world isn't thread-safe ([neoforge
  §5](../research/neoforge-1.21.1.md#5-ticking--threading)).
- **Verify:** Inspect the code; development builds assert the thread on every
  world access.

#### CORE-069 · Results applied on the server thread
`Must` · `R0` · `Implemented` · Verify: Inspect, GameTest · Trace: D-30

Simulation results shall be applied to the world on the server thread.

- **Rationale:** Decision: D-30.
- **Verify:** Inspect the code; a GameTest asserts the thread when results are
  applied.

#### CORE-070 · Stale results discarded
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-30

The server thread shall discard a result if the world changed under it,
detected with a per-chunk generation counter.

- **Rationale:** Decision: D-30. A result computed from a snapshot is wrong if
  the world changed since.
- **Verify:** A GameTest changes a block between snapshot and apply and checks
  the result is discarded.

#### CORE-074 · Discarded results don't lose gas
`Must` · `R0` · `Implemented` · Verify: GameTest, Unit (fuzz) · Trace: D-30 · Depends: CORE-036, CORE-070

When a result is discarded as stale (CORE-070), its queued gas operations
shall be run again in a later atmos tick, not dropped.

- **Rationale:** Decision: D-30, with conservation (CORE-036).
- **Verify:** A GameTest forces a discard and checks the queued gas is applied
  later; a Unit fuzz test with random discards checks the ledger.

#### CORE-071 · Single-threaded equivalence
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-30 · Depends: CORE-044

Running the simulation off-thread shall give the same outcome as running it
single-threaded on the same inputs.

- **Rationale:** Decision: D-30. Threading must not change behaviour.
- **Verify:** A Unit test runs the same inputs threaded and single-threaded
  and compares the states exactly.

#### CORE-044 · Deterministic results
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-30

Given the same world state and inputs, the engine shall produce the same
results.

- **Rationale:** Inferred: deterministic results make tests and bug reports
  reproducible, and CORE-071 depends on them.
- **Verify:** A Unit test runs a scenario twice and compares the results
  exactly.

#### CORE-072 · Hash-independent iteration
`Must` · `R0` · `Implemented` · Verify: Inspect · Trace: D-30

Iteration order shall never depend on hash order or identity hash codes.

- **Rationale:** Inferred: hash and identity order varies between runs, which
  would break determinism (CORE-044).
- **Verify:** Inspect the code for hash-ordered iteration; the CORE-044 test
  runs in two separate JVMs.
