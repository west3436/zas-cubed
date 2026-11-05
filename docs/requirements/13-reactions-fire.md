# 13 · Reactions and fire (`FIRE`)

**Status:** Accepted 2026-10-30 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules). FIRE-006 and FIRE-031 were added and
accepted on 2026-10-31 (D-57). FIRE-032, FIRE-033, FIRE-034 and FIRE-035 were
added as Drafts on 2026-11-04. FIRE-037 was added as a Draft on 2026-11-05.

**Scope.** Covers:
- gas reactions
- gas combustion and hotspots
- vanilla fire, furnaces and solid fuels needing oxygen
- the sound of a gas blast

vgstation13's gas fire only burns fictional fuels
([zas-core §4](../research/zas-core.md#4-fire-firedm)). zas-cubed keeps the ZAS
*structure* (hotspots, parcel burning, spread odds, O₂ cut-offs) but uses real
chemistry (D-03, D-08, D-49).

## Reactions

#### FIRE-001 · Data-driven reactions
`Must` · `R1` · `Implemented` · Verify: Unit, Inspect · Trace: D-05, D-08

Reactions shall be defined in datapacks. Each definition specifies:
- reactant and product stoichiometry
- enthalpy (J/mol)
- activation conditions: temperature, concentration or flammability limits,
  and optionally catalysts
- rate parameters, per second (CORE-046)

Reaction types implemented in code are API-020.

- **Rationale:** Decision: D-05 and D-08. Real, data-driven reactions replace
  vg's hard-coded fire, which only burns fictional fuels ([zas-core
  §4](../research/zas-core.md#4-fire-firedm)).
- **Verify:** Unit tests load test definitions with every field; Inspect the
  documented format.

*2026-11-03: Implemented ([#41](https://github.com/west3436/zas-cubed/issues/41)).
Definitions are read from `data/<ns>/zas/reaction/*.json`, documented in
[datapacks.md](../datapacks.md#reactions), and compiled against the server's
gases at start and on `/reload` (architecture §5, Reactions). Checked by the
`fire_001_` tests in `ReactionFormatTest` (zas) and `ReactionSetTest` (engine).
Zones run their reactions that don't need ignition in phase 7 (ZONE_UPDATES),
checked by `ZoneReactionsTest`.*

#### FIRE-006 · Reactions requiring ignition
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: D-57 · Depends: FIRE-001, FIRE-014

A reaction definition may require an ignition source (FIRE-014, FIRE-015) in
or next to the reacting cell, in addition to its other activation conditions.

- **Rationale:** Decision: D-57.
- **Verify:** A Unit test and a GameTest check a reaction requiring ignition
  waits until an igniter fires.

*2026-11-03: Implemented ([#192](https://github.com/west3436/zas-cubed/pull/192)).
`fire_006_a_reaction_requiring_ignition_waits_for_a_source` (Unit) and
`fire_006_reaction_requiring_ignition_waits_for_the_igniter` (GameTest) check
it.*

#### FIRE-002 · Fair allocation of reactants
`Should` · `R1` · `Implemented` · Verify: Unit · Trace: [zas-core §9](../research/zas-core.md#9-quirks-dont-replicate-blindly)

When reactions compete for the same reactant, only the competing reactions
shall be scaled back. This fixes ZAS's global scaling.

- **Rationale:** Inferred: ZAS scales every reaction by one worst ratio
  ([zas-core §9](../research/zas-core.md#9-quirks-dont-replicate-blindly),
  quirk 7), which slows unrelated reactions.
- **Verify:** Unit tests run competing and independent reactions and check
  only the competing ones are scaled.

*2026-11-03: Implemented ([#41](https://github.com/west3436/zas-cubed/issues/41)).
Each reaction is scaled by the worst shortfall among its own reactants. Checked
by the `fire_002_` tests in `ReactionSetTest`.*

#### FIRE-003 · Built-in combustion
`Must` · `R1` · `Implemented` · Verify: Unit · Trace: S-03, D-49

ZAS³ shall ship these combustion reactions with O₂, using real stoichiometry
and producing H₂O and CO₂:

| Fuel | Heat of combustion (vapor) | Flammability limits in air | Autoignition |
|---|---|---|---|
| H₂ | 242 kJ/mol | 4–75 % | 858 K |
| CH₄ | 802 kJ/mol | 5–15 % | 810 K |
| CO | 283 kJ/mol | 12.5–74 % | 882 K |

- **Rationale:** Decision: D-49 (real chemistry); the values are standard
  reference data.
- **Verify:** Unit tests burn each fuel and check products and heat released.

*2026-11-03: Implemented ([#41](https://github.com/west3436/zas-cubed/issues/41)).
Shipped in `data/zas/zas/reaction/` as reactions that need ignition, with
these limits and autoignition points. Checked by the `fire_003_` tests in
`ReactionFormatTest`, which burn the shipped files with the shipped gases, and
`ReactionSetTest`. In game they burn once hotspots do (FIRE-011).*

*2026-11-04: the limits in this table are the ones at 298 K and below. Above
298 K they widen with the gas's temperature (FIRE-032,
[#211](https://github.com/west3436/zas-cubed/issues/211)).*

#### FIRE-032 · Flammability limits widen with temperature
`Should` · `R1` · `Draft` · Verify: Unit, GameTest · Trace: S-03, S-13, FIRE-003, FIRE-010, [#211](https://github.com/west3436/zas-cubed/issues/211)

Above 298 K, a fuel's lower flammability limit shall fall and its upper limit
rise, each by 0.75 · (T − 298) / ΔH꜀ percentage points, with T in K and ΔH꜀ the
reaction's heat per mole of fuel in kcal/mol. The lower limit shall stop at a
mole fraction of 0.1 %, or where it was declared if that is lower, and the
upper at 100 %. At and below 298 K the declared limits shall apply.

| Fuel | Lower limit at 298 K | Falls per kelvin | Reaches 0.1 % at |
|---|---|---|---|
| H₂ | 4 % | 0.0130 points | 599 K |
| CH₄ | 5 % | 0.0039 points | 1,550 K |
| CO | 12.5 % | 0.0111 points | 1,416 K |

- **Rationale:** Decision: the owner's, 2026-11-03, on the fire round's test
  feedback ([#211](https://github.com/west3436/zas-cubed/issues/211)): a hot
  room stopped burning at H₂'s 4 % with O₂ left. Real limits widen as the gas
  warms, and the modified Burgess–Wheeler rule gives how far.
- **Verify:** Unit tests check 3.9 % H₂ doesn't burn at 298 K and does at
  700 K, and that a fire at 2,700 K burns H₂ down to the floor or to the fire
  oxygen threshold. A GameTest checks a sealed room at 9 % H₂ burns to about 4 %
  when kept at room temperature and well below it once its own heat takes it
  past 600 K.

*2026-11-04: Built, and waiting for review. `FlammabilityLimits.lowerAt` and
`upperAt`, used by every check of the limits in `ReactionSet`. Checked by the
`fire_032_` tests in `ReactionSetTest` and
`fire_032_sealed_room_burns_below_the_declared_limit_once_hot` (GameTest).*

*2026-11-04: FIRE-035 (Draft) changes one case in the Verify above. A fire at
2,700 K short of O₂ no longer stops at the fire oxygen threshold: it burns on to
the floor, or to O₂ at 10 kPa. `fire_035_the_211_room_burns_past_14_percent_to_the_fuel_floor`
replaces the test that said it stopped.*

#### FIRE-005 · Incomplete combustion
`Should` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: D-49, LIFE-041

When CH₄ or a solid fuel burns at an O₂ fraction below 0.18, a share of its
carbon shall form CO instead of CO₂, rising linearly from 0 at 0.18 to all of
it at the fire threshold (0.14), with the heat release reduced to match.

- **Rationale:** Decision: D-49.
- **Verify:** A Unit test checks half the carbon forms CO at an O₂ fraction of
  0.16; a GameTest checks a sealed room with a furnace builds up CO.

*2026-11-03: The solid-fuel half is built ([#43](https://github.com/west3436/zas-cubed/issues/43)): `engine.fire.CarbonCombustion`
gives the CO share and the reduced O₂ and heat, and furnaces, campfires, fires
and the generator burn with it (FIRE-025). The share starts at
`[fire] incomplete_below_fraction` (0.18). Checked by
`fire_005_half_the_carbon_forms_co_at_0_16` (Unit) and
`fire_005_furnace_in_low_oxygen_makes_co` (GameTest).*

*2026-11-03: CH₄'s part is implemented ([#41](https://github.com/west3436/zas-cubed/issues/41)):
the shipped methane combustion has an incomplete form, CH₄ + 1.5 O₂ → CO +
2 H₂O at 519 kJ/mol, blended in by the CO share. Checked by
`fire_005_half_the_methane_carbon_forms_co_at_an_oxygen_fraction_of_016`. With
the solid-fuel half above, FIRE-005 is Implemented.*

#### FIRE-004 · Non-combustion examples
`Could` · `R1` · `Implemented` · Verify: Unit

Ship at least one non-combustion reaction to exercise the framework, such as
N₂O thermal decomposition.

- **Rationale:** Inferred: a non-combustion reaction proves the framework
  isn't fire-only.
- **Verify:** A Unit test runs N₂O decomposition above its threshold.

*2026-11-03: Implemented ([#41](https://github.com/west3436/zas-cubed/issues/41)).
`data/zas/zas/reaction/nitrous_oxide_decomposition.json`: N₂O → N₂ + ½ O₂,
82.05 kJ/mol released, above 850 K, at an Arrhenius rate (about 1 % a second
at 1,000 K, 250 kJ/mol). The rate is chosen for play, not fitted. Checked by
`fire_004_nitrous_oxide_decomposes_above_its_threshold`.*

## Oxygen

#### FIRE-030 · Fire oxygen threshold
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: D-49 · [zas-core §9](../research/zas-core.md#9-quirks-dont-replicate-blindly)

Any fire (hotspots, vanilla fire, lit fuel blocks) shall burn only where the
O₂ fraction is at least 0.14 *and* the O₂ partial pressure is at least 10 kPa
(both configurable). This replaces ZAS's "1 mol per tile" rule; nothing burns
in the Hardcore End.

- **Rationale:** vg: the 0.14 O₂ fraction from ZAS ([zas-core
  §4](../research/zas-core.md#4-fire-firedm)); the 10 kPa floor replaces its 1
  mol per tile rule ([zas-core
  §9](../research/zas-core.md#9-quirks-dont-replicate-blindly), quirk 8;
  D-49).
- **Verify:** Unit tests check both limits; a GameTest checks a fire goes out
  in each case.

*2026-11-03: Implemented ([#43](https://github.com/west3436/zas-cubed/issues/43)). `engine.fire.FireSettings`, the `[fire]`
section of the server config (`oxygen_minimum_fraction`, `oxygen_minimum_kpa`),
carried to the worker in `EngineSettings.fire`. Vanilla fire, fuel blocks
(FIRE-020, FIRE-027) test it, and the hotspots (FIRE-017) will too. Checked by the
`fire_030_` Unit tests and `fire_030_fire_goes_out_below_each_limit`
(GameTest: 13 % O₂ at 1 atm, and pure O₂ at 9 kPa).*

*2026-11-04: FIRE-035 (Draft) lets a gas fire at or above its fuel's
autoignition temperature burn below the 0.14 fraction, keeping the 10 kPa floor.
Every other fire keeps this requirement whole.*

#### FIRE-035 · A gas fire at autoignition needs only the O₂ floor
`Should` · `R1` · `Draft` · Verify: Unit, GameTest · Trace: S-03, S-13, FIRE-031, FIRE-032, [#211](https://github.com/west3436/zas-cubed/issues/211) · Depends: FIRE-010, FIRE-030

A gas fire whose fuel is at or above that fuel's autoignition temperature
(FIRE-010) shall not need FIRE-030's O₂ fraction, and shall still need its O₂
partial pressure. In a mixture of fuels, each fuel shall go by its own
autoignition temperature. A standalone mixture (FIRE-031) shall follow the same
rule. Vanilla fire, lit fuel blocks, blocks catching fire (FIRE-033, FIRE-034)
and gas below its fuel's autoignition temperature shall keep FIRE-030 whole.

- **Rationale:** Decision: the coordinator's, at the owner's request, 2026-11-04,
  for the owner's goal in [#211](https://github.com/west3436/zas-cubed/issues/211)
  that hot rooms burn their fuel out. With FIRE-030 whole, a room at 2,749 K and
  14.2 % O₂ stopped with about 300 of its 340 mol of H₂ left.
- **Verify:** Unit tests check that room burns past 14 % to the fuel's 0.1 % floor
  with its elements and energy conserved, that the same gas below autoignition
  under 14 % O₂ doesn't burn with a source or without, that O₂ below 10 kPa stops
  a fire at any temperature, that at 850 K below 14 % O₂ methane burns and hydrogen
  doesn't, and that a container goes by the same rule. GameTests check a sealed
  room at 1,000 K and 12 % O₂ lights itself and burns to the floor, one at 800 K
  doesn't, one cooled to 800 K goes out with fuel left, a vanilla fire in the hot
  gas goes out, and a pipe network follows the same rule.

*2026-11-04: Built, and waiting for review. `FireSettings.sustainsAutoignited`, the
floor alone, and `ReactionSet`, which tests each reaction's own autoignition point
in `flammability` and in every step, so hotspots and containers (`burn`,
`applyStandalone`) share the rule. Below 0.14 all of a fuel's carbon forms CO
(FIRE-005). No new config key: `[fire] oxygen_minimum_kpa` is the floor. The #211
room burns 332 of its 340 mol of H₂, in 1,443 parcels with one hotspot (52 of them
to reach 14 %), and stops at 8.3 mol, the 0.1 % floor, with O₂ at 12.5 % and
192 kPa, at 3,237 K. A vanilla fire that goes out below 0.14 in gas hot enough can
come back as a gas fire burning its CO: see the note on FIRE-024. Checked by the
`fire_035_` tests in `FireSettingsTest`,
`ReactionSetTest`, `BlockIgniterTest` and `StandaloneReactionsTest`, and
`fire_035_a_hot_room_burns_below_14_percent_and_a_cooler_one_does_not`,
`fire_035_a_vanilla_fire_in_hot_gas_keeps_the_oxygen_fraction` and
`fire_035_a_hot_network_below_14_percent_burns` (GameTests).*

#### FIRE-031 · Combustion in containers
`Must` · `R1` · `Implemented` · Verify: Unit · Trace: D-57, FIRE-011 · Depends: API-019

A standalone mixture (API-019) that ignites, by autoignition (FIRE-010) or a
given ignition source, shall burn as one hotspot whose parcel is 375 L per
second (0.375 cell volumes, FIRE-011), or the whole mixture if it is smaller,
subject to the fire oxygen threshold (FIRE-030). A 70 L tank therefore burns
completely in one atmos tick.

- **Rationale:** Decision: D-57 and D-59.
- **Verify:** Unit tests check a 70 L tank burns completely in one tick and a
  1,000 L canister burns 375 L.

*2026-10-31: the parcel changed from 0.375 of the container's volume to an
absolute 375 L, so a TTV detonates before its tank ruptures (S-13, D-59).*

*2026-11-03: Implemented ([#41](https://github.com/west3436/zas-cubed/issues/41)).
`Reactions.applyStandalone` (API-019) burns 375 L per second of the mixture,
or all of it if smaller, when given an ignition source or above a fuel's
autoignition temperature, and not below the fire oxygen threshold. Checked by
the `fire_031_` tests in `ReactionSetTest`.*

*2026-11-04: FIRE-035 (Draft) applies to a container as it does to a zone: at or
above a fuel's autoignition temperature, that fuel needs only the threshold's
10 kPa floor.*

## Gas fire

#### FIRE-010 · Autoignition
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-03

A mixture within a fuel's flammability limits shall ignite when it is above
that fuel's autoignition temperature.

- **Rationale:** vg: ZAS autoignites above a temperature ([zas-core
  §4](../research/zas-core.md#4-fire-firedm)), here with real autoignition
  points (FIRE-003).
- **Verify:** A GameTest checks an H₂–air zone ignites at 900 K and not at 800
  K.

*2026-11-03: Implemented ([#42](https://github.com/west3436/zas-cubed/issues/42)). A zone with no hotspot whose gas is
flammable at or above a fuel's autoignition point starts a gas fire at its lowest
free position. `fire_010_hydrogen_autoignites_at_900_k_not_800_k` checks it.*

*2026-11-04: the limits this tests are the fuel's at the mixture's temperature
(FIRE-032, [#211](https://github.com/west3436/zas-cubed/issues/211)), not
FIRE-003's table, so a mixture leaner than that table can autoignite. At its
autoignition point H₂ is flammable down to 0.1 %, CH₄ to about 3 % and CO to
about 6 %.*

#### FIRE-014 · Ignition sources
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-03, D-49

A mixture within a fuel's flammability limits shall ignite when an ignition
source is in its cell or a neighbouring cell of its zone. The default sources
are fire, lava, lit campfires, flint and steel, lightning, explosions and
torches.

- **Rationale:** Decision: D-49 (torches ignite), for S-03.
- **Verify:** GameTests check each default source ignites a flammable zone.

*2026-11-03: Implemented ([#189](https://github.com/west3436/zas-cubed/pull/189)).
Standing sources (fire, soul fire, lava, lit campfires, torches) are flagged
in the geometry table and found by the engine. One-off sources (flint and
steel, lightning, explosions) call `Fires.ignite` when they happen. `zas:gas_fire`
is not a source. `IgnitionZoneTests` lights a 10 % H₂ room with each.*

#### FIRE-015 · Configurable ignition sources
`Should` · `R1` · `Implemented` · Verify: GameTest

Ignition sources shall be data-driven. Redstone sparks are available but off
by default.

- **Rationale:** Inferred: packs may want different ignition rules; redstone
  sparks would make every redstone build a hazard, so they're off by default.
- **Verify:** A GameTest checks a datapack can add and remove sources and that
  redstone doesn't ignite by default.

*2026-11-03: Implemented ([#189](https://github.com/west3436/zas-cubed/pull/189)).
Sources are `data/<ns>/zas/ignition_source/*.json`, documented in
[datapacks.md](../datapacks.md#ignition-sources). The redstone spark ships
disabled. `fire_015_datapack_adds_and_removes_sources_in_a_zone` and
`fire_015_redstone_ignites_only_when_enabled` check it.*

#### FIRE-011 · Hotspot burn rate
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: D-03, D-20

A hotspot shall burn a parcel of its zone's gas of 0.375 cell volumes per
second (ZAS's 0.75 per 2 s).

- **Rationale:** vg: ZAS's hotspot parcel ([zas-core
  §4](../research/zas-core.md#4-fire-firedm)), per second (D-20).
- **Verify:** A Unit test checks the parcel size; a GameTest checks a
  hotspot's fuel use per second.

*2026-11-03: Implemented ([#42](https://github.com/west3436/zas-cubed/issues/42)). Each hotspot burns
`ReactionSet.PARCEL_LITRES_PER_SECOND` (375 L) of its zone per second with the
server's reactions. `HotspotTest.fire_011_*` checks the parcel and
`fire_011_hotspot_burns_375_litres_a_second` the fuel used.*

#### FIRE-016 · Hotspot spread
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-03

A hotspot shall spread to adjacent combustible cells with a configurable
per-second probability.

- **Rationale:** vg: ZAS hotspots spread to neighbours with a probability
  ([zas-core §4](../research/zas-core.md#4-fire-firedm)).
- **Verify:** A GameTest runs many trials and checks the spread rate matches
  the configured probability.

*2026-11-03: Implemented ([#42](https://github.com/west3436/zas-cubed/issues/42)). `[fire] spread_chance` (default 0.3)
is the chance per second for each open neighbouring cell that is air and whose gas
can burn; draws are seeded per dimension (§3.5).
`fire_016_spread_matches_the_configured_chance` checks 400 trials.*

#### FIRE-017 · Hotspots go out
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-04

A hotspot shall go out when its cell fails the fire oxygen threshold (FIRE-030)
or has no fuel left.

- **Rationale:** vg: ZAS hotspots go out without enough O₂ ([zas-core
  §4](../research/zas-core.md#4-fire-firedm)).
- **Verify:** A GameTest checks a sealed fire goes out once O₂ falls below the
  threshold (S-04), and when the fuel is used up.

*2026-11-03: Implemented ([#42](https://github.com/west3436/zas-cubed/issues/42)). A gas fire whose zone's gas can no
longer burn, for want of O₂ (FIRE-030) or fuel, goes out and its block is
removed; a vanilla fire is left to FIRE-020. `fire_017_gas_fire_goes_out_without_oxygen`
and `fire_017_gas_fire_goes_out_without_fuel` check both.*

*2026-11-04: with FIRE-035 (Draft), a gas fire at or above its fuel's autoignition
temperature goes out for want of O₂ at the threshold's 10 kPa floor, not at its
0.14 fraction. Below 0.14 it also goes out if the gas cools below that temperature.
`fire_017_gas_fire_goes_out_without_oxygen` still goes out at 14 %, because a fire
lit from 20 °C at 25 % H₂ reaches 14 % before it reaches 858 K.*

#### FIRE-018 · Gas-fire block
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-49

A hotspot with no vanilla fire block shall be shown as a `zas:gas_fire` block:
no collision, emits light, and damages entities as fire does. It burns in mid
air, where vanilla fire can't survive.

- **Rationale:** Decision: D-49.
- **Verify:** A GameTest checks a mid-air hotspot shows the block, which gives
  light, has no collision and damages entities.

*2026-11-03: Implemented ([#42](https://github.com/west3436/zas-cubed/issues/42)). The engine asks for the block with
fire intents, which the server applies only to air (architecture §5).
`fire_018_gas_fire_burns_in_mid_air` checks it.*

*2026-11-03: a block that isn't air but that gas passes as it passes air (a
lever, redstone wire, a rail, a flower, grass, a fluid) has its own geometry,
air's with `FLAG_OCCUPIED`, so the engine never asks for a gas fire there. An
ignition at one lights an air neighbour, and spread passes it by. Before, the
engine asked for the block, the server refused it and the ignition reported a
fire that never burned. `fire_018_ignition_at_a_lever_lights_the_air_beside_it`
and `fire_018_spread_skips_flowers` check it, and
`fire_018_passable_blocks_leave_the_zone_as_air_does` checks such blocks leave
a zone's gas and volume as air does.*

#### FIRE-019 · Vanilla fire in zones is a hotspot
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-49

A vanilla fire block in a zone shall count as a hotspot.

- **Rationale:** Decision: D-49.
- **Verify:** A GameTest checks a vanilla fire in a zone burns zone gas as a
  hotspot.

*2026-11-03: Implemented ([#42](https://github.com/west3436/zas-cubed/issues/42)). Fire blocks carry a geometry flag, so
the engine sees them through the change hook, with no FireBlock mixin. A vanilla
fire burns the zone's flammable gas as a hotspot; the carbon of what it burns on
is FIRE-022's (`Burners`). `fire_019_vanilla_fire_burns_zone_gas` checks it.*

#### FIRE-013 · Fire damage
`Must` · `R1` · `Implemented` · Verify: GameTest

Entities in a hotspot shall be damaged and set alight as vanilla fire does.

- **Rationale:** vg: ZAS hotspots expose mobs to fire ([zas-core
  §4](../research/zas-core.md#4-fire-firedm)); vanilla fire damage is what
  players expect.
- **Verify:** A GameTest checks an entity in a hotspot is damaged and set
  alight.

*2026-11-03: Implemented ([#42](https://github.com/west3436/zas-cubed/issues/42)). `zas:gas_fire` hurts and sets alight
as vanilla fire does, and is in `#minecraft:fire`; a vanilla fire is vanilla's
own. `fire_013_entity_in_gas_fire_is_hurt_and_set_alight` checks it.*

#### FIRE-012 · Rapid combustion
`Could` · `R2` · `Deferred` — moved to R2 (lean scope; S-03 only needs the burn to raise pressure), 2026-10-30. · Verify: GameTest

A large premixed fuel and oxidizer volume above a configurable burn rate may
deflagrate. This produces a pressure spike and optionally a vanilla explosion.

## Vanilla fire and fuels

#### FIRE-020 · Fire needs oxygen
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-05, S-04

Vanilla fire shall fail to ignite, and shall go out, where the fire oxygen
threshold (FIRE-030) isn't met. Outside zones it checks the ambient
(CORE-050), so a vacuum dimension can't sustain fire.

- **Rationale:** Decision: D-05 (S-04).
- **Verify:** GameTests check flint and steel fails in a vacuum dimension and
  a fire goes out in a sealed room.

*2026-11-03: Implemented ([#43](https://github.com/west3436/zas-cubed/issues/43)). Mixins M2 (`FireBlock#tick`: a fire whose
cell fails FIRE-030 goes out), M3 (`BaseFireBlock#canBePlacedAt`: flint and
steel, fire charges and dispensers can't light one) and M4 (`checkBurnOut`: a
fire doesn't burn away a neighbouring block whose own cell fails it). Outside
zones the cell is the dimension's ambient. Soul fire never ticks, so the
burning step (FIRE-022) puts it out. Checked by
`fire_020_flint_and_steel_fails_in_a_vacuum_dimension` and
`fire_020_fire_goes_out_in_a_sealed_room`.*

#### FIRE-025 · Fuel profiles
`Must` · `R1` · `Implemented` · Verify: Inspect, Unit · Trace: D-35, D-49

Fuel-burning blocks shall have data-driven profiles giving their power, burned
as carbon (C + O₂ → CO₂, 394 kJ/mol), so O₂ use and CO₂ output follow from the
power. Defaults:

| Block | Power |
|---|---|
| Furnace, smoker, campfire | 5 kW |
| Blast furnace | 10 kW |
| Fire block | 10 kW |

- **Rationale:** Decision: D-35 and D-49.
- **Verify:** Inspect the profiles; a Unit test checks 5 kW consumes about
  0.0127 mol of O₂ per second.
- **Changed 2026-11-03 (the owner, D-35):** the ZAS³ fuel generator has no
  profile; it doesn't use the air (CONT-067).

*2026-11-03: Implemented ([#43](https://github.com/west3436/zas-cubed/issues/43)). The profiles are data,
`data/<namespace>/zas/fuel_profile/` ([datapacks.md](../datapacks.md#fuel-profiles)),
and ship with the defaults above; `zas_atmos` ships the generator's. Checked by
`fire_025_five_kilowatts_uses_0_0127_mol_o2_per_second` (Unit).*

*2026-11-03: `zas_atmos` no longer ships the generator's profile (D-35,
CONT-067).*

#### FIRE-021 · Lit fuel blocks consume O₂
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: D-35 · Depends: FIRE-025

In a zone, lit fuel-burning blocks (furnaces, smokers, blast furnaces,
campfires) shall consume O₂ and emit CO₂ according to their profile.

- **Rationale:** Decision: D-35.
- **Verify:** A GameTest checks a lit furnace in a zone lowers O₂ and raises
  CO₂ at its profile's rate.
- **Changed 2026-11-03 (the owner, D-35):** the ZAS³ fuel generator is no
  longer listed; it doesn't use the air (CONT-067).

*2026-11-03: Implemented ([#43](https://github.com/west3436/zas-cubed/issues/43)). Every burning state of `#zas:combustion` is
tracked through the change hook and a scan of each loaded chunk, and burns its
profile once per atmos period in its zone (`Burners`, architecture §5). The O₂
is removed, then CO₂, CO (FIRE-005) and heat (HEAT-007) come back in
proportion to the O₂ actually removed. Nothing forms a zone. Checked by
`fire_021_lit_fuel_blocks_use_o2_at_their_profile` (furnace, blast furnace,
smoker, campfire) and `cont_067_running_generator_uses_o2_and_makes_co2_and_heat`.*

*2026-11-03: the generator is no longer a lit fuel block (D-35, CONT-067);
`fire_021_lit_fuel_blocks_use_o2_at_their_profile` checks the rest.*

#### FIRE-027 · Fuel blocks pause without oxygen
`Should` · `R1` · `Implemented` · Verify: GameTest · Depends: FIRE-030

A lit vanilla fuel-burning block (a furnace, smoker or blast furnace) shall
pause, without using up its fuel, while its cell fails the fire oxygen
threshold.

- **Rationale:** Inferred: a furnace that burned fuel without oxygen would
  waste the player's fuel; pausing lets it resume when air returns.
- **Verify:** A GameTest checks a furnace in a low-O₂ zone keeps its fuel and
  resumes when O₂ returns.
- **Changed 2026-11-03 (the owner, D-35):** it covers the vanilla fuel blocks
  only. The ZAS³ fuel generator doesn't use the air, so it runs without O₂
  (CONT-067).

*2026-11-03: Built for the vanilla blocks ([#43](https://github.com/west3436/zas-cubed/issues/43)). M5 skips the tick of a furnace, smoker or
blast furnace that burns or has fuel while the cell in front of it fails
FIRE-030. Its burn time, fuel and cooking progress stay, and it uses no O₂.
M6 puts a lit campfire out, as it has no fuel to keep. Checked by
`fire_027_furnace_pauses_without_oxygen` and
`fire_027_campfire_goes_out_without_oxygen`. The fuel generator lives in
`zas_atmos`, which sees only the API, so it pauses through API-054 in its own
PR; this stays Accepted until then.*

*2026-11-03: Implemented. The owner took the generator out (D-35), so the
vanilla blocks above are all of it, and API-054 isn't needed.*

#### FIRE-022 · Burning blocks
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: S-04, D-49 · Depends: FIRE-025

In a zone, each fire block shall burn as its fuel profile (default 10 kW of
carbon), overridable per tag of the block it burns on.

- **Rationale:** Decision: D-49.
- **Verify:** A GameTest checks a fire block in a zone burns at its profile,
  and that a tag override changes it.

*2026-11-03: Implemented ([#43](https://github.com/west3436/zas-cubed/issues/43)). A fire block burns the profile of
`#minecraft:fire` (10 kW), or one whose `burning_on` matches the block it burns
on: the block below it, or else the first flammable block next to it. Checked
by `fire_022_fire_burns_its_profile_and_a_tag_overrides_it`.*

#### FIRE-033 · Hot gas ignites blocks
`Should` · `R1` · `Draft` · Verify: Unit, GameTest · Trace: S-04, [#212](https://github.com/west3436/zas-cubed/issues/212) · Depends: FIRE-020, FIRE-030

A flammable block next to an air cell of a zone whose temperature is at or above
the block's ignition temperature shall catch fire, with a chance per second for
each such face of `odds / 100 × heat`. `odds` is the block's vanilla ignite odds,
counting at most 100. `heat` rises linearly from 0.1 at the ignition temperature
to 1 at 500 K above it. The fire is a vanilla fire in that air cell, as flint and
steel would place one. The ignition temperature is `[fire]
block_ignition_temperature` (default 573 K), which datapacks may override per
block or block tag.

- **Rationale:** The owner's choice of 2026-11-03: a wooden room at 2,700 K
  should catch fire from the heat. The default is about wood's autoignition
  point. Open air is ambient, not a zone, so it never lights a block.
- **Verify:** Unit tests check the chance, the rate over many seeds, and that a
  cool zone never looks for flammable blocks. GameTests check a sealed planks
  room at 293 K stays unlit and at 700 K gets fires on its inside faces, that a
  tag override changes the temperature, that the rate matches the chance, and
  that nothing lights with `doFireTick` false or below 14 % O₂.

*2026-11-04: Built for review ([#212](https://github.com/west3436/zas-cubed/issues/212)). `engine.scheduler.BlockIgniter`, in
phase 6 (architecture §5, Hotspots). The same rules apply under Normal and
Hardcore. Under Normal, a sealed room is only a zone once something makes one.
Once lit, the fire burns as FIRE-020 and FIRE-022 say. Checked by the `fire_033_`
tests in `BlockIgnitionSettingsTest`, `BlockIgniterTest` (Unit) and
`BlockIgnitionTests` (GameTest), among them
`fire_033_a_planks_room_lights_only_when_hot` and
`fire_033_rate_matches_the_odds_and_the_heat`.*

#### FIRE-034 · Gas fires light the blocks next to them
`Should` · `R1` · `Draft` · Verify: Unit, GameTest · Trace: S-04, [#212](https://github.com/west3436/zas-cubed/issues/212) · Depends: FIRE-018, FIRE-030

A cell with a gas fire beside a flammable block shall light it, with a chance per
second for each such block of `[fire] hotspot_block_ignite_chance × odds / 100`
(default 1), where `odds` is the block's vanilla ignite odds, counting at most
100. A vanilla fire takes the gas fire's cell, which may be the only open cell
beside the block. A vanilla fire has vanilla's own spread instead.

- **Rationale:** The owner's choice of 2026-11-03. FIRE-016's spread only reaches
  air cells whose gas can burn, so a gas fire would never light the wood or wool
  beside it.
- **Verify:** Unit tests check the chance and the rate over many seeds. GameTests
  check a gas fire beside wool lights it and one beside stone doesn't, that the
  rate matches the configured chance, and that nothing lights with `doFireTick`
  false or below 14 % O₂.

*2026-11-04: Built for review ([#212](https://github.com/west3436/zas-cubed/issues/212)). `BlockIgniter`, for each gas fire that
burned this tick. Checked by the `fire_034_` tests in `BlockIgnitionSettingsTest`,
`BlockIgniterTest` (Unit) and `BlockIgnitionTests` (GameTest), among them
`fire_034_a_gas_fire_lights_wool_not_stone` and
`fire_034_rate_matches_the_configured_chance`.*

#### FIRE-024 · Combustion as a zone trigger (Hardcore)
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-13, D-14, S-04

With the Hardcore preset, fire or a lit furnace or campfire in an enclosed,
unzoned area shall create a zone (CORE-051, CORE-056), so a sealed room's fire
can suffocate itself.

- **Rationale:** Decision: D-13 and D-14.
- **Verify:** A GameTest under Hardcore checks a fire in a sealed room forms a
  zone and goes out once O₂ is low.
- **Changed 2026-11-03 (the owner, D-35):** the ZAS³ fuel generator is no
  longer listed; it doesn't use the air, so it is no trigger (CONT-067).

*2026-11-01: The trigger is built (CORE-056,
[#64](https://github.com/west3436/zas-cubed/issues/64)). Under Hardcore, a fire
starting or a furnace, smoker, blast furnace, campfire or soul campfire being
lit in an enclosed, unzoned area forms a zone. These are the blocks in
`#zas:combustion`, fire and soul fire included, and the generator joins the tag
(CONT-067). The `core_056_` GameTests check it. This stays Accepted because its
Verify also needs the fire to go out once O₂ is low (FIRE-017, FIRE-020).*

*2026-11-03: Implemented ([#43](https://github.com/west3436/zas-cubed/issues/43)).
`fire_024_sealed_fire_forms_a_zone_and_goes_out_under_hardcore` lights a fire
in a sealed two-cell room under Hardcore. It forms a zone, burns the O₂ down
with CO forming below 18 %, and goes out between 12 % and 14 %.*

*2026-11-03: the generator left `#zas:combustion` (D-35, CONT-067);
`core_056_lit_generator_forms_no_zone_under_hardcore` checks it forms no
zone.*

*2026-11-04: with FIRE-035 (Draft), a fire that goes out in gas hot enough can
come back as a gas fire burning its CO, and that goes out too. The test allows at
most one such lighting and checks the room ends with no fire of either kind. In
`fire_024_sealed_fire_forms_a_zone_and_goes_out_under_hardcore`, one logged run
had the fire out at 1,572 K with 0.79 mol of CO and 13.2 % O₂. A gas fire then
burned the CO to 0.08 mol, the 0.1 % floor, and was out after 8 ticks at 12.8 %
O₂. It stayed out for the 20 ticks the test then waits, and wool in the walls lit
no vanilla fire. The test's 30 s burn steps against 1 s atmos ticks make that room
hotter than real play; how much hotter isn't measured.*

#### FIRE-023 · Extinguishing
`Should` · `R1` · `Rejected` — covered by FIRE-017, FIRE-020 and FIRE-030 plus vanilla water; the extinguisher is CONT-037 (R2), 2026-10-30.

*Note 2026-11-03:* the owner moved the extinguisher, CONT-037, into R1, and it is
built on API-057. This requirement stays rejected.

## Sound

#### FIRE-037 · A gas blast has its own sound, near or far
`Should` · `R1` · `Draft` · Verify: Unit, GameTest, Manual · Trace: CLIENT-016, CLIENT-020, CONT-038, CONT-102

A tank's or canister's explosion shall play vg's explosion sounds in place of
vanilla's: the big sounds at a power of 2 or more, the small ones below it. Each
listener shall hear one sound, chosen by distance: for a big blast, the near sound
out to 32 blocks and the far sound out to 256; for a small one, 8 and 128. Beyond
that nothing shall be sent. Any other explosion, such as TNT's, shall keep
vanilla's sound.

- **Rationale:** Decision: the owner's sound decisions of 2026-11-05: vg's
  explosions, with their far-away versions, for gas blasts, and vanilla's TNT stays
  vanilla's. Inferred: a power of 2 is where vanilla itself changes to its large
  explosion particles. The near sound stops at half its range because its gain is
  almost nothing near the end of that range, when the far sound's has hardly fallen.
- **Verify:** `BlastSoundTest` (Unit) checks the choice and that near and far meet
  within 3 dB. `fire_037_` (GameTest) checks what the server sends to players at
  several distances, for a blast of each size, an exploding tank and a primed TTV,
  and that TNT sends vanilla's explosion and none of these. Manual checks how each
  one sounds, and the step between near and far.

*2026-11-05: added for review, with the behaviour built. Three things decide the
distances, and none of them has been heard:*

- *Sending one sound per player. `level.playSound` reaches everyone in the event's
  range, so a player beside a blast would get the far sound too. So `ContainerBlast`
  sends each player a `ClientboundSoundPacket` of its own, and gives the vanilla
  explosion `minecraft:intentionally_empty`, the sound a client skips, because a client
  plays an explosion's own sound at volume 4 out to 64 blocks.*
- *Volumes. `blast.near` at 4, for 64 blocks; `blast.small` at 1, for 16, with its
  0.6 from `sounds.json`; the far ones at 1. Above a volume of 1 the engine clamps
  gain at 1 and only the range grows, so a higher volume would lose that 0.6.*
- *The step. The shipped files, decoded with the game's decoder: `near1` -7.7 and
  `near2` -6.9 dBFS in their loudest 0.4 s, `far` -9.5, so the far file is about
  2.2 dB below the near ones. With each sound's straight line to 0, a big blast at
  32 blocks steps from 0.50 to 0.68 (+2.7 dB) and a small one at 8 blocks from 0.30
  to 0.37 (+1.7 dB). Had near run to the end of its range, the step at 64 would be
  from nothing to 0.58. So a listener just past the line gets a slightly louder
  sound than one just inside it. How that sounds is for the owner.*

*A thin-air fade applies to these like any of our one-shots. The packet reaches
the client's `ClientLevel.playSeededSound`, which plays a `SimpleSoundInstance`
through `SoundEngine.play`, where the `PlaySoundEvent` that CLIENT-016 hooks fires.
That is from reading the game's code; no client runs here. Because the engine clamps
gain at 1, a near blast at volume 4 in thin air loses range before it loses gain,
until the factor falls below 0.25: quieter at every distance, but not at its
centre.*
