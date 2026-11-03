# 13 · Reactions and fire (`FIRE`)

**Status:** Accepted 2026-10-30 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules). FIRE-006 and FIRE-031 were added and
accepted on 2026-10-31 (D-57).

**Scope.** Covers:
- gas reactions
- gas combustion and hotspots
- vanilla fire, furnaces and solid fuels needing oxygen

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

#### FIRE-014 · Ignition sources
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-03, D-49

A mixture within a fuel's flammability limits shall ignite when an ignition
source is in its cell or a neighbouring cell of its zone. The default sources
are fire, lava, lit campfires, flint and steel, lightning, explosions and
torches.

- **Rationale:** Decision: D-49 (torches ignite), for S-03.
- **Verify:** GameTests check each default source ignites a flammable zone.

#### FIRE-015 · Configurable ignition sources
`Should` · `R1` · `Accepted` · Verify: GameTest

Ignition sources shall be data-driven. Redstone sparks are available but off
by default.

- **Rationale:** Inferred: packs may want different ignition rules; redstone
  sparks would make every redstone build a hazard, so they're off by default.
- **Verify:** A GameTest checks a datapack can add and remove sources and that
  redstone doesn't ignite by default.

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

#### FIRE-023 · Extinguishing
`Should` · `R1` · `Rejected` — covered by FIRE-017, FIRE-020 and FIRE-030 plus vanilla water; the extinguisher is CONT-037 (R2), 2026-10-30.
