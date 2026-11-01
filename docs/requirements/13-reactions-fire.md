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
`Must` · `R1` · `Accepted` · Verify: Unit, Inspect · Trace: D-05, D-08

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

#### FIRE-006 · Reactions requiring ignition
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: D-57 · Depends: FIRE-001, FIRE-014

A reaction definition may require an ignition source (FIRE-014, FIRE-015) in
or next to the reacting cell, in addition to its other activation conditions.

- **Rationale:** Decision: D-57.
- **Verify:** A Unit test and a GameTest check a reaction requiring ignition
  waits until an igniter fires.

#### FIRE-002 · Fair allocation of reactants
`Should` · `R1` · `Accepted` · Verify: Unit · Trace: [zas-core §9](../research/zas-core.md#9-quirks-dont-replicate-blindly)

When reactions compete for the same reactant, only the competing reactions
shall be scaled back. This fixes ZAS's global scaling.

- **Rationale:** Inferred: ZAS scales every reaction by one worst ratio
  ([zas-core §9](../research/zas-core.md#9-quirks-dont-replicate-blindly),
  quirk 7), which slows unrelated reactions.
- **Verify:** Unit tests run competing and independent reactions and check
  only the competing ones are scaled.

#### FIRE-003 · Built-in combustion
`Must` · `R1` · `Accepted` · Verify: Unit · Trace: S-03, D-49

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

#### FIRE-005 · Incomplete combustion
`Should` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: D-49, LIFE-041

When CH₄ or a solid fuel burns at an O₂ fraction below 0.18, a share of its
carbon shall form CO instead of CO₂, rising linearly from 0 at 0.18 to all of
it at the fire threshold (0.14), with the heat release reduced to match.

- **Rationale:** Decision: D-49.
- **Verify:** A Unit test checks half the carbon forms CO at an O₂ fraction of
  0.16; a GameTest checks a sealed room with a furnace builds up CO.

#### FIRE-004 · Non-combustion examples
`Could` · `R1` · `Accepted` · Verify: Unit

Ship at least one non-combustion reaction to exercise the framework, such as
N₂O thermal decomposition.

- **Rationale:** Inferred: a non-combustion reaction proves the framework
  isn't fire-only.
- **Verify:** A Unit test runs N₂O decomposition above its threshold.

## Oxygen

#### FIRE-030 · Fire oxygen threshold
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: D-49 · [zas-core §9](../research/zas-core.md#9-quirks-dont-replicate-blindly)

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

#### FIRE-031 · Combustion in containers
`Must` · `R1` · `Accepted` · Verify: Unit · Trace: D-57, FIRE-011 · Depends: API-019

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

## Gas fire

#### FIRE-010 · Autoignition
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-03

A mixture within a fuel's flammability limits shall ignite when it is above
that fuel's autoignition temperature.

- **Rationale:** vg: ZAS autoignites above a temperature ([zas-core
  §4](../research/zas-core.md#4-fire-firedm)), here with real autoignition
  points (FIRE-003).
- **Verify:** A GameTest checks an H₂–air zone ignites at 900 K and not at 800
  K.

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
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: D-03, D-20

A hotspot shall burn a parcel of its zone's gas of 0.375 cell volumes per
second (ZAS's 0.75 per 2 s).

- **Rationale:** vg: ZAS's hotspot parcel ([zas-core
  §4](../research/zas-core.md#4-fire-firedm)), per second (D-20).
- **Verify:** A Unit test checks the parcel size; a GameTest checks a
  hotspot's fuel use per second.

#### FIRE-016 · Hotspot spread
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-03

A hotspot shall spread to adjacent combustible cells with a configurable
per-second probability.

- **Rationale:** vg: ZAS hotspots spread to neighbours with a probability
  ([zas-core §4](../research/zas-core.md#4-fire-firedm)).
- **Verify:** A GameTest runs many trials and checks the spread rate matches
  the configured probability.

#### FIRE-017 · Hotspots go out
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-04

A hotspot shall go out when its cell fails the fire oxygen threshold (FIRE-030)
or has no fuel left.

- **Rationale:** vg: ZAS hotspots go out without enough O₂ ([zas-core
  §4](../research/zas-core.md#4-fire-firedm)).
- **Verify:** A GameTest checks a sealed fire goes out once O₂ falls below the
  threshold (S-04), and when the fuel is used up.

#### FIRE-018 · Gas-fire block
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-49

A hotspot with no vanilla fire block shall be shown as a `zas:gas_fire` block:
no collision, emits light, and damages entities as fire does. It burns in mid
air, where vanilla fire can't survive.

- **Rationale:** Decision: D-49.
- **Verify:** A GameTest checks a mid-air hotspot shows the block, which gives
  light, has no collision and damages entities.

#### FIRE-019 · Vanilla fire in zones is a hotspot
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-49

A vanilla fire block in a zone shall count as a hotspot.

- **Rationale:** Decision: D-49.
- **Verify:** A GameTest checks a vanilla fire in a zone burns zone gas as a
  hotspot.

#### FIRE-013 · Fire damage
`Must` · `R1` · `Accepted` · Verify: GameTest

Entities in a hotspot shall be damaged and set alight as vanilla fire does.

- **Rationale:** vg: ZAS hotspots expose mobs to fire ([zas-core
  §4](../research/zas-core.md#4-fire-firedm)); vanilla fire damage is what
  players expect.
- **Verify:** A GameTest checks an entity in a hotspot is damaged and set
  alight.

#### FIRE-012 · Rapid combustion
`Could` · `R2` · `Deferred` — moved to R2 (lean scope; S-03 only needs the burn to raise pressure), 2026-10-30. · Verify: GameTest

A large premixed fuel and oxidizer volume above a configurable burn rate may
deflagrate. This produces a pressure spike and optionally a vanilla explosion.

## Vanilla fire and fuels

#### FIRE-020 · Fire needs oxygen
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-05, S-04

Vanilla fire shall fail to ignite, and shall go out, where the fire oxygen
threshold (FIRE-030) isn't met. Outside zones it checks the ambient
(CORE-050), so a vacuum dimension can't sustain fire.

- **Rationale:** Decision: D-05 (S-04).
- **Verify:** GameTests check flint and steel fails in a vacuum dimension and
  a fire goes out in a sealed room.

#### FIRE-025 · Fuel profiles
`Must` · `R1` · `Accepted` · Verify: Inspect, Unit · Trace: D-35, D-49

Fuel-burning blocks shall have data-driven profiles giving their power, burned
as carbon (C + O₂ → CO₂, 394 kJ/mol), so O₂ use and CO₂ output follow from the
power. Defaults:

| Block | Power |
|---|---|
| Furnace, smoker, campfire | 5 kW |
| Blast furnace, ZAS³ fuel generator | 10 kW |
| Fire block | 10 kW |

- **Rationale:** Decision: D-35 and D-49.
- **Verify:** Inspect the profiles; a Unit test checks 5 kW consumes about
  0.0127 mol of O₂ per second.

#### FIRE-021 · Lit fuel blocks consume O₂
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-35 · Depends: FIRE-025

In a zone, lit fuel-burning blocks (furnaces, smokers, blast furnaces,
campfires, the ZAS³ fuel generator) shall consume O₂ and emit CO₂ according to
their profile.

- **Rationale:** Decision: D-35.
- **Verify:** A GameTest checks a lit furnace in a zone lowers O₂ and raises
  CO₂ at its profile's rate.

#### FIRE-027 · Fuel blocks pause without oxygen
`Should` · `R1` · `Accepted` · Verify: GameTest · Depends: FIRE-030

A lit fuel-burning block shall pause, without using up its fuel, while its
cell fails the fire oxygen threshold.

- **Rationale:** Inferred: a furnace that burned fuel without oxygen would
  waste the player's fuel; pausing lets it resume when air returns.
- **Verify:** A GameTest checks a furnace in a low-O₂ zone keeps its fuel and
  resumes when O₂ returns.

#### FIRE-022 · Burning blocks
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: S-04, D-49 · Depends: FIRE-025

In a zone, each fire block shall burn as its fuel profile (default 10 kW of
carbon), overridable per tag of the block it burns on.

- **Rationale:** Decision: D-49.
- **Verify:** A GameTest checks a fire block in a zone burns at its profile,
  and that a tag override changes it.

#### FIRE-024 · Combustion as a zone trigger (Hardcore)
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-13, D-14, S-04

With the Hardcore preset, fire or a lit furnace, campfire or fuel generator
(D-35) in an enclosed, unzoned area shall create a zone (CORE-051, CORE-056),
so a sealed room's fire can suffocate itself.

- **Rationale:** Decision: D-13 and D-14.
- **Verify:** A GameTest under Hardcore checks a fire in a sealed room forms a
  zone and goes out once O₂ is low.

*2026-11-01: The trigger is built (CORE-056,
[#64](https://github.com/west3436/zas-cubed/issues/64)). Under Hardcore, a fire
starting or a furnace, smoker, blast furnace, campfire or soul campfire being
lit in an enclosed, unzoned area forms a zone. These are the blocks in
`#zas:combustion`, fire and soul fire included, and the generator joins the tag
(CONT-067). The `core_056_` GameTests check it. This stays Accepted because its
Verify also needs the fire to go out once O₂ is low (FIRE-017, FIRE-020).*

#### FIRE-023 · Extinguishing
`Should` · `R1` · `Rejected` — covered by FIRE-017, FIRE-020 and FIRE-030 plus vanilla water; the extinguisher is CONT-037 (R2), 2026-10-30.
