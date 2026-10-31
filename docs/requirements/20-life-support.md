# 20 · Life support and hazards (`LIFE`)

**Status:** Accepted 2026-10-30 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules).

**Scope.** This area covers:
- which entities breathe, and what they breathe
- hypoxia, toxic gases, pressure and temperature hazards
- the protection model
- internals (tanks and masks)

Defaults follow vgstation13 where it uses real gases
([vg-atmos-content §4](../research/vg-atmos-content.md#4-mob-interaction-humans)),
rescaled from vg's 100 HP and 2 s life tick to Minecraft's 20 HP and per-second
rates. Plasma's roles are taken over by gas tags (CORE-012).

## Breathing

#### LIFE-001 · Who breathes
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-05, D-27

Players, and the entity types in the `zas:breathers` tag, shall breathe.

- **Rationale:** Decision: D-27.
- **Verify:** A GameTest checks a player and a tagged mob lose air in vacuum
  and an untagged mob doesn't.

#### LIFE-006 · Default breather tag
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: D-27, ENV-012

The default `zas:breathers` tag shall include air-breathing mobs. It excludes:
- undead, golems, aquatic and other non-living entities
- natives of the Nether and the End (piglins, hoglins, striders, ghasts,
  blazes, magma cubes, endermen, shulkers, endermites), so the Hardcore
  ambients don't kill them in their own dimensions

- **Rationale:** Decision: D-27. Nether and End natives are left out so the
  Hardcore ambients (ENV-012) don't kill them at home.
- **Verify:** Inspect the tag; a GameTest checks a piglin survives in the
  Hardcore Nether.

#### LIFE-002 · Breath source
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: API-015

A breathing entity shall breathe from its internals (LIFE-021) when they are on
and supplied, and otherwise from the atmosphere at its eye position.

- **Rationale:** vg: mobs breathe from internals when available, else from the
  room ([vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)).
- **Verify:** A GameTest checks a player in vacuum breathes from a supplied
  tank, and from the atmosphere without one.

#### LIFE-007 · Breath check cadence
`Must` · `R1` · `Accepted` · Verify: GameTest

Breathing and its hazards shall be evaluated every 2 s by default
(configurable). Rates are per second, so the cadence doesn't change how fast
anything happens.

- **Rationale:** vg: vg evaluates life every 2 s ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)).
- **Verify:** A GameTest checks hazards apply every 2 s by default and that a
  1 s cadence gives the same damage per second.

#### LIFE-003 · Hypoxia
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-02 · [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

If the breath's O₂ partial pressure is below a configurable minimum (default
16 kPa, as in vg), the entity's air meter (the vanilla bubble meter) shall
drain at the vanilla underwater rate × (1 − pO₂ / 16 kPa). In vacuum it
empties in 15 s.

- **Rationale:** vg: the 16 kPa O₂ minimum ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)); the vanilla
  air meter makes it readable.
- **Verify:** A GameTest checks the meter empties in 15 s in vacuum and in 30
  s at 8 kPa O₂.

#### LIFE-008 · Suffocation
`Must` · `R1` · `Accepted` · Verify: GameTest · Depends: LIFE-003

An entity whose air meter is empty shall take hypoxia damage as vanilla
drowning does (2 HP per second).

- **Rationale:** Inferred: matching vanilla drowning makes hypoxia familiar.
- **Verify:** A GameTest checks an empty meter deals 2 HP/s.

#### LIFE-009 · Air recovery
`Must` · `R1` · `Accepted` · Verify: GameTest · Depends: LIFE-003

When the breath's O₂ partial pressure is at or above the minimum, the air
meter shall refill at the vanilla rate.

- **Rationale:** Inferred: the vanilla refill rate, consistent with LIFE-008.
- **Verify:** A GameTest checks the meter refills at the vanilla rate in
  breathable air.

#### LIFE-004 · Gas hazard profiles
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-08

Each gas may have a data-driven hazard profile that maps partial-pressure
thresholds, optionally sustained for a duration, to effects: an effect level
(LIFE-040), status effects, or damage.

- **Rationale:** vg: vg's lungs apply per-gas thresholds with effects ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)); data-driven
  so datapack gases can be hazards (D-08).
- **Verify:** A GameTest checks a test profile applies at its threshold and
  only after its duration.

#### LIFE-040 · Effect levels
`Must` · `R1` · `Accepted` · Verify: GameTest

Hazard profiles shall be able to apply these effect levels:

| Level | Effects |
|---|---|
| Impaired | Nausea, Weakness |
| Incapacitated | Slowness IV, Mining Fatigue III, Weakness II |
| Unconscious | Incapacitated, plus Blindness |

- **Rationale:** Inferred: Minecraft has no paralysis or sleep, so vg's states
  ([vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)) map to
  vanilla effects.
- **Verify:** A GameTest checks each level applies its listed effects.

#### LIFE-041 · Default hazard profiles
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: D-42 · [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

ZAS³ shall ship these default profiles (partial pressures in the breath):

| Gas | Threshold | Effect | Source |
|---|---|---|---|
| CO₂ | 5 kPa | HUD warning | vg |
| CO₂ | above 10 kPa for 12 s | Incapacitated | vg |
| CO₂ | above 10 kPa for 60 s | Incapacitated, 1 HP/s toxic gas | vg, rescaled |
| N₂O | 0.15 kPa | Nausea | vg |
| N₂O | 1 kPa | Incapacitated | vg |
| N₂O | 5 kPa | Unconscious (no damage) | vg |
| CO | 0.005 kPa (50 ppm) | HUD warning | OSHA PEL |
| CO | 0.12 kPa (1,200 ppm) | Impaired, 0.5 HP/s toxic gas | NIOSH IDLH |
| CO | 1.28 kPa (12,800 ppm) | Unconscious, 2 HP/s toxic gas | Lethal within minutes |

The other built-in gases are hazardous only by displacing O₂ (LIFE-003).

- **Rationale:** vg: the CO₂ and N₂O thresholds ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)). vg has no
  CO, so its thresholds come from the OSHA and NIOSH limits shown.
- **Verify:** Inspect the shipped profiles; GameTests check each row's effect.

#### LIFE-005 · Respiration changes zone air
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-27

Inside an existing zone, breathing shall take O₂ from the breath source and
return the same number of moles of CO₂ to the surrounding zone. (Breathing
never creates a zone, per CORE-051, and never changes the ambient, per
CORE-038.)

- **Rationale:** vg: vg exhales consumed O₂ as CO₂, 1:1 ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)); D-27.
- **Verify:** A GameTest checks a breather in a zone lowers O₂ and raises CO₂
  by the same moles.

#### LIFE-042 · Respiration rate
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-27

Respiration shall run at the realistic rate, about 1 mol of O₂ per breather
per hour, in both presets, with a configurable multiplier.

- **Rationale:** Decision: D-27.
- **Verify:** A GameTest with a high multiplier measures O₂ use per breather
  against the rate.

## Environmental hazards

#### LIFE-015 · Who takes environmental hazards
`Must` · `R1` · `Accepted` · Verify: GameTest

Pressure and temperature hazards shall apply to every living entity except
those in the `zas:atmosphere_immune` tag.

- **Rationale:** Inferred: pressure and heat hurt every living thing, not only
  breathers; the tag exempts those that shouldn't care.
- **Verify:** A GameTest checks a zombie takes vacuum damage and a tagged mob
  doesn't.

#### LIFE-016 · Default immune tag
`Must` · `R1` · `Accepted` · Verify: Inspect

The default `zas:atmosphere_immune` tag shall contain the bosses (wither,
ender dragon) and golems (iron, snow).

- **Rationale:** Inferred: atmospheric damage would trivialise boss fights and
  break golem builds.
- **Verify:** Inspect the shipped tag.

#### LIFE-010 · Low-pressure hazard
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-02 · [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

An effective pressure (LIFE-020) below 20 kPa shall deal 1 HP/s of
decompression damage. (The HUD warnings at 50 and 325 kPa are CLIENT-008.)

- **Rationale:** vg: the 20 kPa threshold ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)), rescaled to
  20 HP and per second.
- **Verify:** A GameTest checks an unprotected entity at 15 kPa takes 1 HP/s.

#### LIFE-014 · High-pressure hazard
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

An effective pressure at or above 550 kPa shall deal
min((P / 550 kPa − 1) × 2, 2) HP/s of barotrauma damage.

- **Rationale:** vg: the 550 kPa threshold and formula ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)), rescaled.
- **Verify:** GameTests check 1 HP/s at 825 kPa and the 2 HP/s cap at 1,650
  kPa.

#### LIFE-011 · Temperature hazard
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-05 · [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

Exposure temperatures outside the comfort band (default 220–360 K) shall deal
damage by tier. Every tier applies, unlike vg.

| Cold (frostbite) | | Heat (burns) | |
|---|---|---|---|
| below 220 K | 0.5 HP/s | above 360 K | 1 HP/s |
| below 200 K | 1 HP/s | above 400 K | 2 HP/s |
| below 120 K | 2 HP/s | above 1,000 K | 4 HP/s |

An unprotected entity in vacuum takes about 3 HP/s (decompression plus
frostbite), before hypoxia.

- **Rationale:** vg: the 220–360 K band and tiers ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)), rescaled.
  Every tier applies because vg's tiers never escalate, which is a bug ([vg
  §6](../research/vg-atmos-content.md#6-config-knobs-fictional-gases-and-quirks)).
- **Verify:** GameTests check the damage in each tier.

#### LIFE-012 · Breath temperature
`Could` · `R2` · `Deferred` — moved to R2 (lean scope; only matters for very cold tanks), 2026-10-30. · Verify: GameTest

Breathing gas below 220 K or above 360 K may cause damage, unless a mask or
helmet conditions the breath.

#### LIFE-013 · Body temperature model
`Won't` · `R1` · `Accepted` · Trace: R2 candidate

R1 uses exposure-based temperature damage. A body-temperature model, or a
bridge to temperature mods, is deferred (see SPACE and future compat).

- **Rationale:** Inferred: a body-temperature model is a large system, and
  exposure damage covers R1's scenarios (lean scope).
- **Verify:** Inspect: no body temperature is stored.

## Protection and internals

#### LIFE-020 · Pressure protection
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: D-47 · [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

The effective pressure difference shall be |P − 101.325 kPa| × (1 − c⁵), where
c is the coverage (LIFE-024) of worn pieces rated for that difference on that
side: a low-pressure rating below 1 atm, a high-pressure rating above it.
Without a helmet, 83 % of the difference still applies.

- **Rationale:** vg: vg's effective-pressure formula, c⁵ and the 83 %
  helmetless remainder ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)); ratings by
  side from D-47.
- **Verify:** Unit tests check the formula for coverage combinations; a
  GameTest checks a suited and a helmetless player in vacuum.

#### LIFE-024 · Coverage weights
`Must` · `R1` · `Accepted` · Verify: Unit · Trace: [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

Armor slots shall have these coverage weights by default (configurable):
helmet 0.3, chestplate 0.4 (torso and arms), leggings 0.2, boots 0.1.

- **Rationale:** vg: vg's weights (head 0.3, torso 0.3, arms 0.2, legs and
  feet 0.2; [vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)), regrouped
  into Minecraft's four slots.
- **Verify:** Unit tests check the weights and that they sum to 1.

#### LIFE-025 · Thermal protection
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: D-47

Temperature damage shall be multiplied by (1 − c), where c is the coverage of
worn pieces whose thermal range includes the exposure temperature.

- **Rationale:** Decision: D-47. vg models body temperature; R1 uses exposure
  with coverage instead (LIFE-013).
- **Verify:** Unit tests check the multiplier; a GameTest checks partial suits
  at 450 K.

#### LIFE-026 · Protection sources
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: API-022

An item's protection shall come from its life-support capability (API-022),
or else from data by item or tag: low- and high-pressure ratings, thermal
range, sealed breathing, gas filtering and airflow resistance.

- **Rationale:** Decision: D-12. The capability serves mods; data serves items
  that can't implement it.
- **Verify:** A GameTest checks a capability item and a data-defined item both
  protect.

#### LIFE-027 · Vanilla armor
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: D-47

Vanilla armor shall protect against heat only, up to 400 K. It has no cold
protection, no pressure rating and no seal, so it can't be used with
internals.

- **Rationale:** Decision: D-47.
- **Verify:** Inspect the data; a GameTest checks diamond armor reduces burns
  at 380 K, not at 450 K or in the cold, and can't be used with internals.

#### LIFE-021 · Internals need a seal
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-01, S-08, D-47

Internals shall work only with a worn head-slot item that provides sealed
breathing: a breath mask, gas mask or suit helmet (CONT-031, CONT-088, CONT-032,
CONT-036).

- **Rationale:** Decision: D-47.
- **Verify:** A GameTest checks internals work with each sealed head item and
  not without one.

#### LIFE-028 · Tank location
`Must` · `R1` · `Accepted` · Verify: GameTest

Internals shall draw from the first tank found in: the suit's tank slot
(CONT-032, CONT-036), the offhand, then the hotbar.

- **Rationale:** Inferred: Minecraft has no belt or suit-storage slots like
  vg's ([vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)), so
  the suit slot, offhand and hotbar stand in.
- **Verify:** A GameTest places tanks in each location and checks the draw
  order.

#### LIFE-029 · Internals toggle
`Must` · `R1` · `Accepted` · Verify: GameTest, Manual

Internals shall be switched on and off with a keybind, and stay in that state
until toggled.

- **Rationale:** Inferred: a keybind is the simplest control for vg's
  internals switch, and it stays on so players needn't keep toggling.
- **Verify:** A GameTest toggles the state and checks it persists; Manual
  checks the keybind.

#### LIFE-043 · Internals draw rate
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: D-20 · [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

Each second, internals shall draw n = P_dist × 0.0625 L / (R · T_tank) from
the tank (vg's 0.5 L every 8 s), whatever the breath check cadence.

- **Rationale:** vg: vg draws 0.5 L at distribution pressure per 8 s breath
  ([vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)),
  converted to per second.
- **Verify:** A Unit test checks the formula; a GameTest checks an O₂ tank
  lasts about 9 hours at the default, as in vg.

#### LIFE-044 · Distribution pressure
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

The distribution pressure shall be adjustable from 0 to 304 kPa, with a
default per tank type: 21.3 kPa for O₂ tanks, 101.3 kPa for air tanks.

- **Rationale:** vg: the 0–304 kPa range and 21.3 kPa O₂ default ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)). The air-tank
  default is inferred, so that it delivers about 21 kPa of O₂.
- **Verify:** A GameTest checks the defaults and the adjustable range.

#### LIFE-045 · Exhaled gas
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: CORE-036

Gas drawn by internals shall be exhaled into the surrounding atmosphere, after
respiration (LIFE-005).

- **Rationale:** vg: vg returns breaths to the room even when they came from a
  tank ([vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)).
- **Verify:** A GameTest checks tank gas moves into the zone and the
  conservation ledger balances.

#### LIFE-046 · Empty tank fallback
`Must` · `R1` · `Accepted` · Verify: GameTest

When the tank can't supply the distribution pressure, the wearer shall
breathe the environment.

- **Rationale:** Inferred: a player whose tank runs dry should breathe the air
  around them rather than suffocate in breathable air.
- **Verify:** A GameTest empties a tank in breathable air and checks the
  player keeps breathing.

#### LIFE-023 · Client breathing sync
`Must` · `R1` · `Accepted` · Verify: Manual, GameTest · Trace: [neoforge §9](../research/neoforge-1.21.1.md#9-entities)

NeoForge's breathing event also fires on the client. The local player's
breathing state shall therefore be synced, so the client-side air bar matches
the server without jitter.

- **Rationale:** Inferred: the breathing event fires on the client too, so
  without sync the client refills the bar and it jitters ([neoforge
  §9](../research/neoforge-1.21.1.md#9-entities)).
- **Verify:** Manual checks the air bar is steady in a low-O₂ zone; a GameTest
  checks the synced state.

#### LIFE-022 · Underwater
`Should` · `R1` · `Accepted` · Verify: GameTest

While the eyes are in water, vanilla drowning shall apply instead of
atmospheric breathing.

- **Rationale:** Inferred: water is vanilla's domain, and drowning already
  handles it.
- **Verify:** A GameTest checks a submerged player in a zone drowns as in
  vanilla.

#### LIFE-047 · Internals underwater
`Should` · `R1` · `Accepted` · Verify: GameTest · Depends: LIFE-021

Internals that are on and supplied shall give water breathing.

- **Rationale:** Inferred: a sealed mask on a supplied tank would work
  underwater, and it rewards the gear.
- **Verify:** A GameTest checks a player with internals on doesn't drown.

## Damage, configuration and cost

#### LIFE-030 · Damage types
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest

Hazards shall use dedicated data-driven damage types, each with its own death
message:
- hypoxia
- toxic gas
- decompression (low pressure)
- barotrauma (high pressure)
- frostbite
- burns
- airflow impact

- **Rationale:** Inferred: dedicated types give clear death messages and let
  damage tags apply per hazard ([neoforge
  §9](../research/neoforge-1.21.1.md#9-entities)).
- **Verify:** Inspect the damage-type data; a GameTest checks each hazard uses
  its type.

#### LIFE-035 · Armor bypass
`Must` · `R1` · `Accepted` · Verify: GameTest

Every atmospheric damage type except airflow impact shall bypass armor.
Protection comes from LIFE-020 and LIFE-025 instead, so armor doesn't count
twice.

- **Rationale:** Inferred: protection already comes from LIFE-020 and
  LIFE-025, so armor reduction would count twice; impacts are physical, so
  armor applies to them.
- **Verify:** A GameTest compares damage with and without armor for each type.

#### LIFE-031 · Hazard toggles and scaling
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-04

Every hazard shall be individually toggleable and scalable.

- **Rationale:** Decision: D-04. Servers and packs tune hazards.
- **Verify:** GameTests turn off and scale each hazard.

#### LIFE-036 · Per-dimension hazard overrides
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-04

Hazard toggles and scales shall be overridable per dimension.

- **Rationale:** Decision: D-04.
- **Verify:** A GameTest overrides a hazard in one dimension and checks
  another is unaffected.

#### LIFE-037 · Creative and spectator exemption
`Must` · `R1` · `Accepted` · Verify: GameTest

Creative and spectator players shall be exempt from all hazards.

- **Rationale:** Inferred: building and testing in creative mustn't be
  interrupted, as vanilla spares creative players.
- **Verify:** A GameTest checks creative and spectator players in vacuum take
  no harm.

#### LIFE-032 · Hazard extension API
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-04, D-12

Consumer mods shall be able to register additional hazard handlers per gas or
per tag, for example radiation from a radioactive gas they define. (API-033
intercepts existing hazard ticks; this adds new hazards.)

- **Rationale:** Decision: D-04 and D-12.
- **Verify:** A GameTest registers a handler for a test gas and checks it
  applies.

#### LIFE-033 · Staggered evaluation
`Must` · `R1` · `Accepted` · Verify: Bench · Trace: NFR-001

Hazard evaluation shall run at a per-entity cadence, staggered across
entities.

- **Rationale:** Inferred: evaluating every entity on the same tick causes
  spikes (NFR-001).
- **Verify:** Bench checks hazard work is spread evenly across ticks with 100
  breathers (S-10).

#### LIFE-038 · Per-tick entity cap
`Must` · `R1` · `Accepted` · Verify: Bench · Trace: NFR-001

The number of entities evaluated per server tick shall be capped
(configurable).

- **Rationale:** Inferred: a large mob farm in a zone mustn't break the tick
  budget (NFR-001).
- **Verify:** Bench checks the per-tick count never exceeds the cap.

#### LIFE-034 · Spawning in lethal atmospheres
`Could` · `R1` · `Accepted` · Verify: GameTest

Breathing mobs should not spawn naturally where the atmosphere is immediately
lethal, such as vacuum.

- **Rationale:** Inferred: mobs spawning into vacuum would die at once,
  wasting work and littering drops.
- **Verify:** A GameTest checks breathing mobs don't spawn naturally in a
  vacuum test dimension.
