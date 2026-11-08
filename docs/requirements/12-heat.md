# 12 · Heat (`HEAT`)

**Status:** Accepted 2026-10-30 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules). HEAT-020 to HEAT-022, heat between pipe gas and
what's around it, were added as Draft on 2026-11-07 (D-84).

**Scope.** How zone temperatures change other than through gas mixing:
- conduction through boundary blocks
- heat sources and sinks
- radiative loss to space
- heat between pipe gas and its surroundings, or another network's gas

The heat API is API-013. vgstation13 has **no** working heat conduction
([zas-core §6](../research/zas-core.md#6-heat)), so this area is original
design (D-05, D-48).

## Conduction

#### HEAT-001 · Boundary conduction
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: D-05, D-48, S-05

Across each gas-tight face of a zone, heat shall flow between the zone and the
face's far side (HEAT-011).

- **Rationale:** Decision: D-05 and D-48.
- **Verify:** Unit tests check face heat flow; a GameTest checks a room beside
  lava warms and a room in a cold vacuum cools (S-05).

*2026-11-02: Implemented ([#39](https://github.com/west3436/zas-cubed/issues/39)).
Phase 5 of the atmos tick (`Conduction`, architecture §4.8). Checked by the
`heat_011_` and `heat_012_` Unit tests and the `heat_011_` GameTests; the S-05
GameTests follow with the heater.*

#### HEAT-011 · Far side of a face
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: D-48

The far side of a zone's gas-tight face shall be found by looking along the
face's normal, through at most a configurable wall thickness (default 3
blocks):
- the first cell found: its zone, or the ambient if it is unzoned
- a heat-source block (HEAT-003) in the path
- the ambient, if nothing is found within the maximum thickness (ground
  temperature)

- **Rationale:** Decision: D-48.
- **Verify:** Unit tests and a GameTest check each far-side case: a cell, a
  heat source, and nothing within 3 blocks.

*2026-11-02: Implemented ([#39](https://github.com/west3436/zas-cubed/issues/39)).
Lines of the 1/16 grid are followed from each sealed part of a face, so a
slab or pane is a partial layer. An unzoned cell is the ambient: a reservoir
at its temperature, or a radiating face if the ambient is a vacuum (HEAT-004).
`[heat] wall_thickness` sets the thickness. Checked by
`heat_011_far_side_is_a_cell`, `heat_011_far_side_is_a_heat_source` and
`heat_011_nothing_within_three_blocks_is_the_ground` (Unit), and the four
`heat_011_` GameTests, which use the test datapack's heat source.*

#### HEAT-012 · Conduction rate
`Must` · `R1` · `Implemented` · Verify: Unit · Trace: D-48

Heat flow through a face shall be U × A × ΔT, where A is the face's gas-tight
area (1 m² for a full face) and U combines the blocks in the path in series:
1/U = Σ 1/Uᵢ.

- **Rationale:** Decision: D-48.
- **Verify:** Unit tests check the flow through single and layered walls
  against hand-computed values.

*2026-11-02: Implemented ([#39](https://github.com/west3436/zas-cubed/issues/39)).
Each line of the grid is 1/256 m². Checked by `heat_012_single_wall`,
`heat_012_layered_wall` and `heat_012_series` (Unit).*

#### HEAT-013 · Conduction across partitions
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: D-44, D-48

Sub-cells on either side of a partition (GEO-004) shall exchange heat through
it, using the partition block's U.

- **Rationale:** Decision: D-44 and D-48.
- **Verify:** A Unit test and a GameTest check two sub-cells divided by a pane
  exchange heat at the pane's U.

*2026-11-02: Implemented ([#39](https://github.com/west3436/zas-cubed/issues/39)).
A pane's two halves face each other across 1 m². Checked by
`heat_013_pane_partition` (Unit), `heat_013_pane_partition_and_post` (Unit,
the derived geometry) and `heat_013_pane_between_rooms` (GameTest).*

#### HEAT-002 · Transmittance data
`Must` · `R1` · `Implemented` · Verify: Inspect · Trace: D-48

Thermal transmittance U, in W/(m²·K) per block-thick layer, shall be defined
per block or tag in datapacks.

- **Rationale:** Decision: D-48.
- **Verify:** Inspect the data format and a test datapack.

*2026-11-02: Implemented ([#39](https://github.com/west3436/zas-cubed/issues/39)).
`data/<ns>/zas/thermal/*.json` with `blocks`, `transmittance` and
`temperature` ([datapacks](../datapacks.md#heat-data)). The test datapack's
files are checked by `heat_002_datapack_heat_data` (GameTest).*

#### HEAT-014 · Default transmittances
`Must` · `R1` · `Implemented` · Verify: Inspect · Trace: D-48

ZAS³ shall ship these defaults:

| Material | U (W/(m²·K)) |
|---|---|
| Wool and carpet | 0.04 |
| Wood | 0.15 |
| Stone | 1.7 |
| Ice | 2.2 |
| Glass | 5.8 |
| Metal blocks | 50 |

- **Rationale:** Decision: D-48 (realistic U values); these are typical values
  for each material.
- **Verify:** Inspect the shipped data.

*2026-11-02: Implemented ([#39](https://github.com/west3436/zas-cubed/issues/39)).
`data/zas/zas/thermal/`, one file per material
([datapacks](../datapacks.md#heat-data)); `heat_014_default_transmittances`
(GameTest) checks every state of sample blocks.*

#### HEAT-015 · Fallback transmittance
`Must` · `R1` · `Implemented` · Verify: Unit · Trace: D-48

A block with no transmittance defined shall use a configurable fallback
(default 1.7, as stone).

- **Rationale:** Inferred: stone is the most common building material, so an
  unknown block behaves like it.
- **Verify:** A Unit test checks an undefined block uses the fallback.

*2026-11-02: Implemented ([#39](https://github.com/west3436/zas-cubed/issues/39)).
`[heat] fallback_transmittance`. Checked by `heat_015_fallback_transmittance`
(Unit).*

#### HEAT-008 · No conduction in near-vacuum
`Must` · `R1` · `Implemented` · Verify: Unit · Trace: D-48

A zone below a configurable molar density (default 1 % of standard air) shall
neither conduct nor radiate heat, so a near-empty zone beside lava doesn't
reach lava temperature and burn entities (LIFE-011).

- **Rationale:** Decision: D-48.
- **Verify:** A Unit test checks a zone at 0.5 % of standard density beside
  lava doesn't change temperature.

*2026-11-02: Implemented ([#39](https://github.com/west3436/zas-cubed/issues/39)).
`[heat] near_vacuum_percent`. Checked by `heat_008_near_vacuum_does_not_conduct`
(Unit).*

#### HEAT-006 · Stability
`Must` · `R1` · `Implemented` · Verify: Unit (property tests)

Heat transfer in one atmos tick shall never overshoot equilibrium, whatever
the tick period, heat capacities or transmittances.

- **Rationale:** Inferred: an explicit heat step can overshoot and oscillate
  with large U or long ticks, which would break period independence (D-20).
- **Verify:** Unit property tests check random capacities, U values and
  periods never cross equilibrium.

*2026-11-02: Implemented ([#39](https://github.com/west3436/zas-cubed/issues/39)).
Each step is the exact solution over the tick, not an explicit step. Checked
by `heat_006_never_crosses_equilibrium` and
`heat_006_never_overshoots_in_the_engine` (Unit).*

#### HEAT-019 · A block that doesn't conduct
`Should` · `R1` · `Implemented` · Verify: Unit · Trace: D-80, HEAT-002, HEAT-004, HEAT-012

A block's transmittance may be 0. No heat shall cross a path through such a block, by
conduction or by radiating to a vacuum.

- **Rationale:** Decision: D-80.
- **Verify:** Unit tests check that a path with a layer of U 0 carries no heat, next to a
  path that does, and that it radiates nothing to a vacuum.

*2026-11-06: Added for review. `ThermalData` and `Geometry` reject a U of 0 today, so
this needs a change in the heat code, which
[#179](https://github.com/west3436/zas-cubed/issues/179) didn't expect. `HEAT-002`'s data
format gains the value, and `docs/datapacks.md` changes with the code.*

*2026-11-07: built, and left Draft for the owner to accept. A heat file's `transmittance` may be 0
([datapacks](../datapacks.md#heat-data)). In the engine a layer of U 0 is an infinite
resistance: a line of the heat grid that crosses one ends there and makes no link, so no
conductance, pair or radiating face is ever worked out from it, and NaN still means the
fallback. Checked by `heat_019_a_path_with_a_layer_of_0_carries_no_heat`,
`heat_019_rooms_either_side_of_a_layer_of_0_exchange_nothing`,
`heat_019_a_wall_of_0_radiates_nothing_to_a_vacuum`, `heat_019_a_layer_of_0_in_series`,
`heat_019_a_wall_of_0_radiates_nothing` and `heat_019_a_transmittance_of_0` (engine Unit),
and `heat_019_a_transmittance_of_0_loads` (the data format, Unit).*

*2026-11-07: Accepted by the owner, and Implemented: its Verify plan's unit tests pass (see
the note above).*

## Sources and sinks

#### HEAT-003 · Heat-source blocks
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-05

Data-driven heat-source blocks shall act as a far side (HEAT-011) at a fixed
temperature, conducting with their own U.

- **Rationale:** Decision: D-48 (heat-source blocks on the far side), for
  S-05.
- **Verify:** A GameTest checks a room beside a test heat source moves toward
  its temperature.

*2026-11-02: Implemented ([#40](https://github.com/west3436/zas-cubed/issues/40)).
A heat file's `temperature` makes its blocks sources
([datapacks](../datapacks.md#heat-data)), and conduction ends a heat path at
one ([#39](https://github.com/west3436/zas-cubed/issues/39)). Checked by
`heat_003_room_moves_toward_a_heat_source` (GameTest), which runs twenty atmos
ticks beside the test datapack's 400 K source, and by the `heat_011_` source
tests.*

#### HEAT-016 · Default heat sources
`Must` · `R1` · `Implemented` · Verify: Inspect · Trace: S-05

ZAS³ shall ship these heat sources:

| Block | Temperature |
|---|---|
| Lava | 1,450 K |
| Magma block | 1,000 K |
| Ice | 273 K |
| Packed ice | 263 K |
| Blue ice | 253 K |
| Snow block | 268 K |

Fire and lit fuel blocks are heat sources through FIRE-025 instead.

- **Rationale:** Inferred: approximate real temperatures of these materials,
  so S-05 works.
- **Verify:** Inspect the shipped data.

*2026-11-02: Implemented ([#40](https://github.com/west3436/zas-cubed/issues/40)).
`data/zas/zas/thermal/sources/`, one file per block
([datapacks](../datapacks.md#heat-data)). They set no U, so lava, magma and
snow use the fallback and the ice keeps 2.2.
`heat_016_default_heat_sources` (GameTest) checks every state.*

*2026-11-05: Fluids seal now (GEO-006), so lava also warms a room it touches with
no wall between. `geo_006_lava_beside_a_room_warms_it` checks it.*

#### HEAT-007 · Lit fuel blocks
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: D-35 · Depends: FIRE-025

While lit, a fuel-burning block in a zone shall add its profile's power
(FIRE-025) to the zone as heat.

- **Rationale:** Decision: D-35 and D-49.
- **Verify:** A GameTest checks a lit furnace raises a zone's thermal energy
  by its profile power.

*2026-11-03: Implemented ([#43](https://github.com/west3436/zas-cubed/issues/43)). The heat is added through API-013's heat
operation, without forming a zone, once the O₂ it burned is removed (FIRE-021).
Checked by `fire_021_lit_fuel_blocks_use_o2_at_their_profile` and
`cont_067_running_generator_uses_o2_and_makes_co2_and_heat`.*

*2026-11-03: the ZAS³ fuel generator adds no heat: it doesn't use the air
(D-35, CONT-067, changed by the owner). `fire_021_lit_fuel_blocks_use_o2_at_their_profile`
checks the vanilla blocks.*

#### HEAT-004 · Radiative loss to vacuum
`Should` · `R1` · `Implemented` · Verify: Unit · Trace: D-07, D-48

A face whose far side is a vacuum ambient shall radiate from the wall's outer
surface, in series with conduction through the wall: the surface settles where
U · (T_zone − T_s) = εσ (T_s⁴ − T_bg⁴). Emissivity ε is configurable (default
0.9).

- **Rationale:** Decision: D-48.
- **Verify:** Unit tests check the surface temperature and flux against a
  numerical solution.

*2026-11-02: Implemented ([#39](https://github.com/west3436/zas-cubed/issues/39)),
with conduction, since a face's far side in a vacuum is found the same way.
`[heat] emissivity`. Checked by `heat_004_surface_temperature_against_bisection`
and `heat_004_radiates_to_vacuum` (Unit).*

#### HEAT-010 · Heat at open air
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: CORE-053

Heat added to or removed from an unzoned point shall go to the ambient, with no
effect.

- **Rationale:** Inferred: the ambient is an infinite reservoir (CORE-038), so
  heat behaves as gas does at open air (CORE-053).
- **Verify:** A GameTest adds heat at an open point and checks nothing changes
  and the result reports the ambient.

*2026-11-02: Implemented ([#40](https://github.com/west3436/zas-cubed/issues/40)),
with API-013. Removal at open air is supplied in full, as gas is (CORE-057).
Checked by `heat_010_heat_at_open_air` (Unit and GameTest).*

#### HEAT-005 · Heat API
`Must` · `R1` · `Rejected` — duplicate of API-013 and API-011; the open-air rule moved to HEAT-010, 2026-10-30.

## Pipe gas

Radiator pipe and heat exchangers (D-84) are the only ways heat leaves or enters a pipe
network other than with gas. A radiator pipe exchanges heat only with the gas of its own
cell: a solid block beside it takes none, and heat reaches a room's walls through the
room's own conduction (HEAT-001).

#### HEAT-020 · Radiator pipe and the gas around it
`Must` · `R1` · `Draft` · Verify: Unit, GameTest · Trace: D-84, HEAT-006, HEAT-008, HEAT-010, [vg §2.4](../research/vg-atmos-content.md#24-heat-exchange-and-generators) · Depends: CONT-143, API-064

Each atmos tick, heat shall flow between each radiator pipe segment's network and the gas of
the cell the segment is in, its zone's or the open air's, at 20 W/K (configurable) per
segment times their difference in temperature. A segment shall move no more heat in an atmos
tick than its cell's share of that gas can take, and neither side shall pass the other's
temperature. A cell thinner than HEAT-008's near-vacuum density, 1 % of standard air's by
default, takes no part.

- **Rationale:** Decision: D-84. vg's heat-exchange pipe evens out its gas with a quarter of
  its tile's air each tick ([vg §2.4](../research/vg-atmos-content.md#24-heat-exchange-and-generators));
  a conductance per segment is the same idea in HEAT-012's terms, and the cap keeps a thin
  cell's gas from being driven past the pipe. 20 W/K is a finned tube in still air.
- **Verify:** Unit tests check the flow against 20 W/K × ΔT in dense gas, the cap in thin
  gas, and that random capacities and runs never cross. GameTests check a hot run in a sealed
  room warms it by the heat its network loses, a run in the open air cools to the ambient's
  temperature with nothing else changing, and a run in a near-vacuum room changes nothing.

*2026-11-07: Added for review.*

*2026-11-07: Built, and its Verify plan passes; it stays Draft for the owner. The line is the
one zones use, which the API now gives (API-064), so a server's `near_vacuum_percent` moves
both. The network's new temperature is the implicit step over all its segments at once, so
the flow is 20 W/K × ΔT where both capacities are large and less in a step where they're
small (architecture §11.17). Checked by `RadiatorMathTest` (Unit:
`heat_020_dense_gas_takes_20_w_per_k_times_the_difference`, `heat_020_a_thin_cell_caps_the_heat`,
`heat_020_random_runs_never_cross` and two more) and the GameTests
`heat_020_a_hot_run_warms_a_sealed_room_by_what_it_loses`,
`heat_020_a_cold_run_cools_a_sealed_room_by_what_it_gains`,
`heat_020_a_run_in_the_open_air_cools_to_the_ambient` and
`heat_020_a_run_in_a_near_vacuum_room_changes_nothing`. A 512-segment run costs about
0.2 ms an atmos tick (architecture §11.17).*

#### HEAT-021 · Radiator pipe in a vacuum
`Must` · `R1` · `Draft` · Verify: Unit, GameTest · Trace: D-84, HEAT-004 · Depends: CONT-143, API-064

A radiator pipe segment in the open air of an ambient thinner than HEAT-008's near-vacuum
density shall radiate εσA(T⁴ − T_bg⁴) from its network, where T_bg is the ambient's
temperature, ε is 0.9 (configurable) and A is 1 m², never taking its network below T_bg.

- **Rationale:** Decision: D-84. vg's heat-exchange pipe radiates when there is almost no gas
  around it ([vg §2.4](../research/vg-atmos-content.md#24-heat-exchange-and-generators)),
  which is what makes it a radiator in space; this uses HEAT-004's law and emissivity. In a
  zone that thin, an evacuated room, it does nothing, as such a zone doesn't conduct
  (HEAT-008).
- **Verify:** A Unit test checks the heat against the formula and that a network never drops
  below T_bg. A GameTest checks a hot run in a vacuum's open air cools at that rate.

*2026-11-07: Added for review.*

*2026-11-07: Built, and its Verify plan passes; it stays Draft for the owner. Checked by
`heat_021_radiates_epsilon_sigma_a_t4` and `heat_021_never_below_the_background` (Unit) and
`heat_021_a_hot_run_in_a_vacuum_radiates_at_the_law` (GameTest, in the test datapack's
2.7 K vacuum): each atmos tick is the implicit step's, within 2 % of the law for its gas.*

#### HEAT-022 · Heat exchanger pair
`Must` · `R1` · `Draft` · Verify: Unit, GameTest · Trace: D-84, [vg §2](../research/vg-atmos-content.md#2-devices) · Depends: CONT-147

Each atmos tick, two heat exchangers facing each other from neighbouring blocks shall bring
their shares of their networks' gas to one temperature, conserving thermal energy: ΔT ×
C₁C₂ / (C₁ + C₂) moves from the hotter, where Cᵢ is the heat capacity of exchanger i's share
of its network's gas by volume.

- **Rationale:** vg: vg's heat exchangers work in pairs that set both buffers to their combined
  temperature each tick ([vg §2](../research/vg-atmos-content.md#2-devices)). An exchanger's
  buffer is its share of its network here (CONT-001), so a large network changes more slowly
  than a small one.
- **Verify:** A Unit test checks the heat moved and that energy is conserved. A GameTest
  joins a hot network and a cold one by a pair and checks the heat each gains or loses after
  one atmos tick, and that a lone exchanger, or two not facing each other, move none.

*2026-11-07: Added for review.*

*2026-11-07: Built, and its Verify plan passes; it stays Draft for the owner. Of a pair, the
exchanger facing south, east or up runs it, once per atmos tick. Checked by
`heat_022_a_pair_shares_one_temperature` (Unit and GameTest).*

## Not in R1

#### HEAT-009 · Wall thermal mass
`Won't` · `R1` · `Accepted` · Trace: performance

Boundary blocks shall not store their own temperature in R1. Conduction is
steady-state. This may be revisited in R2.

- **Rationale:** Inferred: storing wall temperatures would multiply state and
  cost, and steady-state conduction is enough for S-05.
- **Verify:** Inspect: no per-block temperature is stored.

#### HEAT-017 · No phase changes
`Won't` · `R1` · `Accepted` · Trace: D-48, D-58

Temperature in zones, pipes, canisters and tanks shall not melt ice, freeze
water or condense gas in R1.

- **Rationale:** Decision: D-58.
- **Verify:** Inspect: no phase-change code; ice beside a hot zone stays ice
  in a GameTest.

*2026-10-31: extended from zones to pipes, canisters and tanks (D-58).*

#### HEAT-018 · Phase changes
`Could` · `Later` · `Deferred` — recorded future scope (D-58), 2026-10-31. · Verify: GameTest · Depends: CORE-017

A later update may add phase changes in zones and pipes: condensation,
boiling, melting and freezing, with latent heat, using the phase data of
CORE-017.
