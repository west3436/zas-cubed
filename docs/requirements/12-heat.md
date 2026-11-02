# 12 · Heat (`HEAT`)

**Status:** Accepted 2026-10-30 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules).

**Scope.** How zone temperatures change other than through gas mixing:
- conduction through boundary blocks
- heat sources and sinks
- radiative loss to space

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

## Sources and sinks

#### HEAT-003 · Heat-source blocks
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-05

Data-driven heat-source blocks shall act as a far side (HEAT-011) at a fixed
temperature, conducting with their own U.

- **Rationale:** Decision: D-48 (heat-source blocks on the far side), for
  S-05.
- **Verify:** A GameTest checks a room beside a test heat source moves toward
  its temperature.

#### HEAT-016 · Default heat sources
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: S-05

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

#### HEAT-007 · Lit fuel blocks
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-35 · Depends: FIRE-025

While lit, a fuel-burning block in a zone shall add its profile's power
(FIRE-025) to the zone as heat.

- **Rationale:** Decision: D-35 and D-49.
- **Verify:** A GameTest checks a lit furnace raises a zone's thermal energy
  by its profile power.

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
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: CORE-053

Heat added to or removed from an unzoned point shall go to the ambient, with no
effect.

- **Rationale:** Inferred: the ambient is an infinite reservoir (CORE-038), so
  heat behaves as gas does at open air (CORE-053).
- **Verify:** A GameTest adds heat at an open point and checks nothing changes
  and the result reports the ambient.

#### HEAT-005 · Heat API
`Must` · `R1` · `Rejected` — duplicate of API-013 and API-011; the open-air rule moved to HEAT-010, 2026-10-30.

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
