# 20 · Life support and hazards (`LIFE`)

**Status:** Accepted 2026-10-30 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules). LIFE-048 was added as Draft on
2026-11-02 and accepted by the owner the same day.

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
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-05, D-27

Players, and the entity types in the `zas:breathers` tag, shall breathe.

- **Rationale:** Decision: D-27.
- **Verify:** A GameTest checks a player and a tagged mob lose air in vacuum
  and an untagged mob doesn't.

*2026-11-02: Implemented
([#34](https://github.com/west3436/zas-cubed/issues/34)). Players and the types
in `#zas:breathers` breathe; anything else is `DOES_NOT_BREATHE` and keeps a
full meter. The `life_001_player_and_breather_lose_air_in_vacuum` GameTest
checks a player and a pig lose air in a vacuum zone and a zombie doesn't.*

#### LIFE-006 · Default breather tag
`Must` · `R1` · `Implemented` · Verify: Inspect, GameTest · Trace: D-27, ENV-012

The default `zas:breathers` tag shall include air-breathing mobs. It excludes:
- undead, golems, aquatic and other non-living entities
- natives of the Nether and the End (piglins, hoglins, striders, ghasts,
  blazes, magma cubes, endermen, shulkers, endermites), so the Hardcore
  ambients don't kill them in their own dimensions

- **Rationale:** Decision: D-27. Nether and End natives are left out so the
  Hardcore ambients (ENV-012) don't kill them at home.
- **Verify:** Inspect the tag; a GameTest checks a piglin survives in the
  Hardcore Nether.

*2026-11-02: Implemented. `data/zas/tags/entity_type/breathers.json` lists the
air-breathing vanilla mobs ([datapacks.md](../datapacks.md#breathers)). Besides
the groups above it leaves out the allay and the vex (spirits), the slime (no
lungs, like the magma cube), the breeze (wind, like the blaze), the tadpole and
the turtle, which is in `#minecraft:aquatic`. `life_006_default_breathers`
checks members of each group, and
`life_006_piglin_survives_the_hardcore_nether` that a piglin takes no effects
or damage from the Hardcore Nether's CO₂ while a pig beside it is incapacitated
and then hurt.*

#### LIFE-002 · Breath source
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: API-015

A breathing entity shall breathe from its internals (LIFE-021) when they are on
and supplied, and otherwise from the atmosphere at its eye position.

- **Rationale:** vg: mobs breathe from internals when available, else from the
  room ([vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)).
- **Verify:** A GameTest checks a player in vacuum breathes from a supplied
  tank, and from the atmosphere without one.

*2026-11-02: The atmosphere half is in
([#34](https://github.com/west3436/zas-cubed/issues/34)): a breather breathes
the cell at its eyes (API-015), checked by
`life_002_breathes_the_atmosphere_at_its_eyes`. Internals come with LIFE-021
([#36](https://github.com/west3436/zas-cubed/issues/36)), so this stays
Accepted.*

*2026-11-02: Implemented ([#36](https://github.com/west3436/zas-cubed/issues/36)). With its internals on, its breathing sealed
and a tank that delivers (LIFE-021, LIFE-046), a breather breathes the tank's
gas: the verdict's source is `INTERNALS` and its breath the tank's mixture at
the distribution pressure. `s_08_breathing_gear` checks a suited player in
vacuum breathes its tank with internals on, and the vacuum with them off.*

#### LIFE-048 · Unresolved atmosphere
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: API-010, LIFE-002

While the atmosphere at a breathing entity's eyes is unresolved (API-010), its
breath checks shall keep its last verdict, air drain and gas exposures and
charge no hazard; with no verdict yet, they shall wait for the atmosphere to
resolve.

*2026-11-02: added as Draft with
[#34](https://github.com/west3436/zas-cubed/issues/34). An unresolved sample's
mixture is only a last-known guess, the ambient for a loading room, so a player
logging in to a pressurised room in a vacuum dimension would otherwise breathe
the vacuum for a moment. Built and checked by
`life_048_unresolved_atmosphere_holds_the_breath_state`.*

*2026-11-02: accepted by the owner after the S-01 round test, and
Implemented ([#151](https://github.com/west3436/zas-cubed/pull/151)).*

#### LIFE-007 · Breath check cadence
`Must` · `R1` · `Implemented` · Verify: GameTest

Breathing and its hazards shall be evaluated every 2 s by default
(configurable). Rates are per second, so the cadence doesn't change how fast
anything happens.

- **Rationale:** vg: vg evaluates life every 2 s ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)).
- **Verify:** A GameTest checks hazards apply every 2 s by default and that a
  1 s cadence gives the same damage per second.

*2026-11-02: Implemented. `[breathing] check_interval_ticks`, 40 by default
([config.md](../config.md)); checks are staggered by entity id, and run again
at once when the eyes enter or leave a fluid. A check charges the seconds since
the last, capped at one interval.
`life_007_checks_every_2_s_and_damage_doesnt_depend_on_it` checks a test gas
harms every 40 ticks by default and that a 20-tick interval deals the same
damage over the same time;
`life_007_check_runs_again_when_the_eyes_leave_water` checks the fluid
trigger.*

#### LIFE-003 · Hypoxia
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-02 · [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

If the breath's O₂ partial pressure is below a configurable minimum (default
16 kPa, as in vg), the entity's air meter (the vanilla bubble meter) shall
drain at the vanilla underwater rate × (1 − pO₂ / 16 kPa). In vacuum it
empties in 15 s.

- **Rationale:** vg: the 16 kPa O₂ minimum ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)); the vanilla
  air meter makes it readable.
- **Verify:** A GameTest checks the meter empties in 15 s in vacuum and in 30
  s at 8 kPa O₂.

*2026-11-02: Implemented. `[breathing] oxygen_minimum_kpa`, 16 by default. The
meter drains through NeoForge's `LivingBreatheEvent` at the rate above,
carrying fractions of air between ticks.
`life_003_meter_empties_in_15_s_in_vacuum` and
`life_003_meter_empties_in_30_s_at_8_kpa` (to within a tick) check it.*

#### LIFE-008 · Suffocation
`Must` · `R1` · `Implemented` · Verify: GameTest · Depends: LIFE-003

An entity whose air meter is empty shall take hypoxia damage as vanilla
drowning does (2 HP per second).

- **Rationale:** Inferred: matching vanilla drowning makes hypoxia familiar.
- **Verify:** A GameTest checks an empty meter deals 2 HP/s.

*2026-11-02: Implemented. While hypoxic, `LivingDrownEvent` is cancelled and
the same 2 HP a second is dealt as `zas:hypoxia`.
`life_008_empty_meter_deals_2_hp_per_second` checks the amount, the timing and
the damage type.*

#### LIFE-009 · Air recovery
`Must` · `R1` · `Implemented` · Verify: GameTest · Depends: LIFE-003

When the breath's O₂ partial pressure is at or above the minimum, the air
meter shall refill at the vanilla rate.

- **Rationale:** Inferred: the vanilla refill rate, consistent with LIFE-008.
- **Verify:** A GameTest checks the meter refills at the vanilla rate in
  breathable air.

*2026-11-02: Implemented. In breathable air the event is left as vanilla starts
it, so the meter refills 4 a tick. `life_009_meter_refills_at_the_vanilla_rate`
checks a pig in air refills at that rate while one in a vacuum zone doesn't.*

#### LIFE-004 · Gas hazard profiles
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-08

Each gas may have a data-driven hazard profile that maps partial-pressure
thresholds, optionally sustained for a duration, to effects: an effect level
(LIFE-040), status effects, or damage.

- **Rationale:** vg: vg's lungs apply per-gas thresholds with effects ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)); data-driven
  so datapack gases can be hazards (D-08).
- **Verify:** A GameTest checks a test profile applies at its threshold and
  only after its duration.

*2026-11-02: Implemented. Profiles are data at
`data/<namespace>/zas/hazard_profile/<path>.json` for the gas
`<namespace>:<path>` ([datapacks.md](../datapacks.md#hazard-profiles)). A
threshold applies at or above its partial pressure, or strictly above it with
`"above": true`, once sustained for its duration; per gas the highest damage applies, and gases add up.
`life_004_profile_applies_at_its_threshold_after_its_duration` checks a test
profile applies at its threshold, not below it, and only after its duration,
and that invalid files are skipped (OPS-008).*

#### LIFE-040 · Effect levels
`Must` · `R1` · `Implemented` · Verify: GameTest

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

*2026-11-02: Implemented as `effect_level` in the profile format, with the
effects above. `life_040_effect_levels_apply_their_effects` checks each level's
effects and amplifiers.*

#### LIFE-041 · Default hazard profiles
`Must` · `R1` · `Implemented` · Verify: Inspect, GameTest · Trace: D-42 · [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

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

*2026-11-02: Implemented in `data/zas/zas/hazard_profile/`
([datapacks.md](../datapacks.md#shipped-profiles)). The rows that say "above"
apply strictly above their partial pressure (`"above": true` in the profile),
and the others at or above it. `life_041_shipped_profiles_hold_the_table` reads
the loaded profiles against the table, including that CO₂ isn't harmful at
exactly 10 kPa and N₂O and CO are at exactly 0.15 and 0.12 kPa, and `life_041_carbon_dioxide_rows`,
`life_041_nitrous_oxide_rows` and `life_041_carbon_monoxide_rows` check each
row's effect on a pig.*

#### LIFE-005 · Respiration changes zone air
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: D-27

Inside an existing zone, breathing shall take O₂ from the breath source and
return the same number of moles of CO₂ to the surrounding zone. (Breathing
never creates a zone, per CORE-051, and never changes the ambient, per
CORE-038.)

- **Rationale:** vg: vg exhales consumed O₂ as CO₂, 1:1 ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)); D-27.
- **Verify:** A GameTest checks a breather in a zone lowers O₂ and raises CO₂
  by the same moles.

*2026-11-02: Implemented for the atmosphere; breathing from internals
(LIFE-021) is #36's. Each check adds the breather's O₂ to its zone's draw, and
once per atmos period each zone's draw is removed and the moles actually
removed come back as CO₂, under the ledger's `LIFE` category and never forming
a zone. `life_005_respiration_turns_o2_into_co2` checks O₂ falls and CO₂ rises
by the same moles, and that a breather in the open air draws nothing.*

#### LIFE-042 · Respiration rate
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: D-27

Respiration shall run at the realistic rate, about 1 mol of O₂ per breather
per hour, in both presets, with a configurable multiplier.

- **Rationale:** Decision: D-27.
- **Verify:** A GameTest with a high multiplier measures O₂ use per breather
  against the rate.

*2026-11-02: Implemented. `[breathing] respiration_multiplier`, 1 by default;
the rate doesn't depend on the preset. `life_042_respiration_rate` measures a
breather's O₂ use with a multiplier of 360 (0.1 mol a second) against the
rate.*

## Environmental hazards

#### LIFE-015 · Who takes environmental hazards
`Must` · `R1` · `Implemented` · Verify: GameTest

Pressure and temperature hazards shall apply to every living entity except
those in the `zas:atmosphere_immune` tag.

- **Rationale:** Inferred: pressure and heat hurt every living thing, not only
  breathers; the tag exempts those that shouldn't care.
- **Verify:** A GameTest checks a zombie takes vacuum damage and a tagged mob
  doesn't.

*2026-11-02: Implemented
([#35](https://github.com/west3436/zas-cubed/issues/35)). Every living entity
not in the tag is checked on the breath check's cadence, breathing or not, and
judged on the atmosphere at its eyes; an immune entity's verdict lists
`Protection.IMMUNE`, and an immune entity that doesn't breathe isn't checked at
all. `life_015_zombie_takes_vacuum_damage_and_an_immune_mob_doesnt` checks a
zombie and an iron golem in a vacuum zone.*

#### LIFE-016 · Default immune tag
`Must` · `R1` · `Implemented` · Verify: Inspect

The default `zas:atmosphere_immune` tag shall contain the bosses (wither,
ender dragon) and golems (iron, snow).

- **Rationale:** Inferred: atmospheric damage would trivialise boss fights and
  break golem builds.
- **Verify:** Inspect the shipped tag.

*2026-11-02: Implemented in `data/zas/tags/entity_type/atmosphere_immune.json`
([datapacks.md](../datapacks.md#atmosphere-immune-entities)), with those four
and nothing else; `life_016_default_immune_tag` reads it.*

#### LIFE-010 · Low-pressure hazard
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-02 · [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

An effective pressure (LIFE-020) below 20 kPa shall deal 1 HP/s of
decompression damage. (The HUD warnings at 50 and 325 kPa are CLIENT-008.)

- **Rationale:** vg: the 20 kPa threshold ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)), rescaled to
  20 HP and per second.
- **Verify:** A GameTest checks an unprotected entity at 15 kPa takes 1 HP/s.

*2026-11-02: Implemented as `zas:decompression`
([#35](https://github.com/west3436/zas-cubed/issues/35)). The effective
pressure is 101.325 kPa + (P − 101.325 kPa) × the fraction of the difference
the entity's protection lets through, which is the whole of it until LIFE-020
([#36](https://github.com/west3436/zas-cubed/issues/36)).
`life_010_low_pressure_deals_1_hp_per_second` checks 15 kPa and 25 kPa.*

#### LIFE-014 · High-pressure hazard
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

An effective pressure at or above 550 kPa shall deal
min((P / 550 kPa − 1) × 2, 2) HP/s of barotrauma damage.

- **Rationale:** vg: the 550 kPa threshold and formula ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)), rescaled.
- **Verify:** GameTests check 1 HP/s at 825 kPa and the 2 HP/s cap at 1,650
  kPa.

*2026-11-02: Implemented as `zas:barotrauma`
([#35](https://github.com/west3436/zas-cubed/issues/35)), with LIFE-010's
effective pressure. `life_014_high_pressure` checks 825 kPa, 1,650 kPa and
545 kPa.*

#### LIFE-011 · Temperature hazard
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-05 · [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

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

*2026-11-02: Implemented as `zas:frostbite` and `zas:burns`
([#35](https://github.com/west3436/zas-cubed/issues/35)). The exposure
temperature is the temperature of the atmosphere at the eyes. Each tier is
reached as the temperature passes it, and the highest reached applies, so the
2.7 K vacuum gives 2 HP/s of frostbite and 3 HP/s with decompression, as
stated; adding the tiers would give 4.5. The band's edges are
`[hazards] comfort_min_k` and `comfort_max_k` ([config.md](../config.md));
the other tiers are fixed. `life_011_temperature_tiers` checks either side of
each tier, `life_011_a_hot_room_burns` a zone at 450 K, and
`life_011_unprotected_in_vacuum_takes_3_hp_per_second` the vacuum.*

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
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: D-47 · [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

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

*2026-11-02: Implemented
([#36](https://github.com/west3436/zas-cubed/issues/36)). `WornProtection` is
an entity's shielding. A piece is rated for P below 1 atm if its lowest
pressure is at most P, and above 1 atm if its highest is at least P; at exactly
1 atm nothing is. The shares of the rated pieces give c, and a sum within 1e-9
of 1 counts as 1, so a full suit lets nothing through. The 83 % follows from
the formula: c is at most 0.7 without a helmet. A verdict lists `PRESSURE`
wherever a piece is rated for the pressure. zas has no unit-test source set, so
the formula's checks are a GameTest that runs no world:
`life_020_pressure_formula`. `life_020_suited_and_helmetless_players_in_vacuum`
checks the players.*

#### LIFE-024 · Coverage weights
`Must` · `R1` · `Implemented` · Verify: Unit · Trace: [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

Armor slots shall have these coverage weights by default (configurable):
helmet 0.3, chestplate 0.4 (torso and arms), leggings 0.2, boots 0.1.

- **Rationale:** vg: vg's weights (head 0.3, torso 0.3, arms 0.2, legs and
  feet 0.2; [vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)), regrouped
  into Minecraft's four slots.
- **Verify:** Unit tests check the weights and that they sum to 1.

*2026-11-02: Implemented
([#36](https://github.com/west3436/zas-cubed/issues/36)). The weights are
`[protection] head_coverage`, `chest_coverage`, `legs_coverage` and
`feet_coverage` in the server config, each 0 to 1 (`ProtectionConfig`).
`life_024_coverage_weights` checks them and their sum, as a GameTest that runs
no world.*

#### LIFE-025 · Thermal protection
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: D-47

Temperature damage shall be multiplied by (1 − c), where c is the coverage of
worn pieces whose thermal range includes the exposure temperature.

- **Rationale:** Decision: D-47. vg models body temperature; R1 uses exposure
  with coverage instead (LIFE-013).
- **Verify:** Unit tests check the multiplier; a GameTest checks partial suits
  at 450 K.

*2026-11-02: Implemented
([#36](https://github.com/west3436/zas-cubed/issues/36)). A piece's thermal
range is its lowest to highest temperature, and at exactly 293.15 K no piece
counts. A verdict lists `THERMAL` wherever a piece's range holds the
temperature. `life_025_partial_suits_at_450_k` checks a full suit, chest and
legs, a helmet alone, and no suit. The multiplier is checked there too, not in
a separate unit test.*

#### LIFE-026 · Protection sources
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: API-022

An item's protection shall come from its life-support capability (API-022),
or else from data by item or tag: low- and high-pressure ratings, thermal
range, sealed breathing, gas filtering and airflow resistance.

- **Rationale:** Decision: D-12. The capability serves mods; data serves items
  that can't implement it.
- **Verify:** A GameTest checks a capability item and a data-defined item both
  protect.

*2026-11-02: Implemented
([#36](https://github.com/west3436/zas-cubed/issues/36)). The capability is
`ILifeSupport.ITEM`, and `ILifeSupport.ENTITY` for an entity's own, which
covers its whole body. The data is `data/<namespace>/zas/protection/*.json`
([datapacks.md](../datapacks.md#protection)). Precedence: an item's own
capability, then a file naming the item, then one naming a tag it is in. Files
of the same kind go in file id order, and a second claim on an item is logged.
Gas filtering is carried but not applied until the gas mask.
`life_026_capability_and_data_items_protect` checks the capability, data by
item and by tag, the precedence, an entity's own, and the invalid and duplicate
files.*

*2026-11-05: Gas filtering is now applied (API-022), from the capability or data
alike. `life_026_a_data_item_filters_the_breath` checks a data item.*

#### LIFE-027 · Vanilla armor
`Must` · `R1` · `Implemented` · Verify: Inspect, GameTest · Trace: D-47

Vanilla armor shall protect against heat only, up to 400 K. It has no cold
protection, no pressure rating and no seal, so it can't be used with
internals.

- **Rationale:** Decision: D-47.
- **Verify:** Inspect the data; a GameTest checks diamond armor reduces burns
  at 380 K, not at 450 K or in the cold, and can't be used with internals.

*2026-11-02: Implemented
([#36](https://github.com/west3436/zas-cubed/issues/36)).
`data/zas/zas/protection/vanilla_armor.json` lists the 24 vanilla armor pieces
and the turtle shell with `max_temperature_k` 400, and nothing else.
`life_027_vanilla_armor_protects_against_heat_only` checks full diamond armor:
no burns at 380 K, full burns at 450 K, full frostbite at 150 K, no pressure
protection and no seal. Internals themselves come later in #36.*

#### LIFE-021 · Internals need a seal
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-01, S-08, D-47

Internals shall work only with a worn head-slot item that provides sealed
breathing: a breath mask, gas mask or suit helmet (CONT-031, CONT-088, CONT-032,
CONT-036).

- **Rationale:** Decision: D-47.
- **Verify:** A GameTest checks internals work with each sealed head item and
  not without one.

*2026-11-02: Implemented ([#36](https://github.com/west3436/zas-cubed/issues/36)). `Internals.active` needs
`WornProtection.sealedBreathing`: the head slot's item, or the entity itself,
seals the breathing. A verdict on internals lists `SEALED_BREATHING`.
`life_021_internals_need_a_seal` checks the test item, and
`cont_031_internals_with_each_sealed_head` the breath mask and the softsuit
helmet, and not the rest of the suit. The gas mask and the hardsuit helmet will
pass the same way once they are built (CONT-088, CONT-036).*

#### LIFE-028 · Tank location
`Must` · `R1` · `Implemented` · Verify: GameTest

Internals shall draw from the first tank found in: the suit's tank slot
(CONT-032, CONT-036), the offhand, then the hotbar.

- **Rationale:** Inferred: Minecraft has no belt or suit-storage slots like
  vg's ([vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)), so
  the suit slot, offhand and hotbar stand in.
- **Verify:** A GameTest places tanks in each location and checks the draw
  order.

*2026-11-02: Implemented ([#36](https://github.com/west3436/zas-cubed/issues/36)). `Internals.find` looks in the armor slots,
chest first, so a suit's tank slot comes first, then the offhand, then the
hotbar from left to right, and last the entity's own life support. A mob has no
hotbar, so its main hand stands in. The first tank found is used even when it
can't deliver, and then the entity breathes the air (LIFE-046): it doesn't pass
over an empty tank to the next. `life_028_the_first_tank_found` checks the
order with test tanks, and `cont_090_suit_tank_slot` with the softsuit's slot.*

#### LIFE-029 · Internals toggle
`Must` · `R1` · `Implemented` · Verify: GameTest, Manual

Internals shall be switched on and off with a keybind, and stay in that state
until toggled.

- **Rationale:** Inferred: a keybind is the simplest control for vg's
  internals switch, and it stays on so players needn't keep toggling.
- **Verify:** A GameTest toggles the state and checks it persists; Manual
  checks the keybind.

*2026-11-02: Implemented ([#36](https://github.com/west3436/zas-cubed/issues/36)). Internals are a saved entity attachment,
kept through death, off until toggled. The keybind, I by default under
"Atmospherics", sends a toggle to the server, which answers in the action bar:
on, or on but with no seal, no tank or a tank that can't deliver, or off.
`life_029_internals_toggle_and_persist` checks the toggle and that the state is
saved. Manual: the keybind.*

#### LIFE-043 · Internals draw rate
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: D-20 · [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

Each second, internals shall draw n = P_dist × 0.0625 L / (R · T_tank) from
the tank (vg's 0.5 L every 8 s), whatever the breath check cadence.

- **Rationale:** vg: vg draws 0.5 L at distribution pressure per 8 s breath
  ([vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)),
  converted to per second.
- **Verify:** A Unit test checks the formula; a GameTest checks an O₂ tank
  lasts about 9 hours at the default, as in vg.

*2026-11-02: Implemented ([#36](https://github.com/west3436/zas-cubed/issues/36)). Each check draws `Internals.drawRate` times
the seconds it charges, the same seconds as respiration's. zas has no
unit-test source set, so `life_043_draw_rate` checks the formula, an hour's
draw at once against three checks' 4 s, and that a 17.5 mol O₂ tank lasts
between 8.5 and 9.5 hours (about 8.6) before it falls below 21.3 kPa.*

#### LIFE-044 · Distribution pressure
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

The distribution pressure shall be adjustable from 0 to 304 kPa, with a
default per tank type: 21.3 kPa for O₂ tanks, 101.3 kPa for air tanks.

- **Rationale:** vg: the 0–304 kPa range and 21.3 kPa O₂ default ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)). The air-tank
  default is inferred, so that it delivers about 21 kPa of O₂.
- **Verify:** A GameTest checks the defaults and the adjustable range.

*2026-11-02: Implemented ([#49](https://github.com/west3436/zas-cubed/issues/49)). A tank's distribution pressure is the
`zas_atmos:distribution_pressure` component, or its kind's default. Using a
held tank opens its settings screen, whose buttons (−10, −1, +1, +10, default,
0) send only the hand and the button: the server refuses an unknown hand or
button or a hand without a tank, and clamps to 0–304 kPa. The breath is the
tank's mixture scaled to the distribution pressure.
`life_044_distribution_pressure` checks the defaults, the range and the
refusals.*

#### LIFE-045 · Exhaled gas
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: CORE-036

Gas drawn by internals shall be exhaled into the surrounding atmosphere, after
respiration (LIFE-005).

- **Rationale:** vg: vg returns breaths to the room even when they came from a
  tank ([vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)).
- **Verify:** A GameTest checks tank gas moves into the zone and the
  conservation ledger balances.

*2026-11-02: Implemented ([#36](https://github.com/west3436/zas-cubed/issues/36)). The gas drawn goes into the cell at the
eyes, with up to LIFE-042's O₂ for the seconds charged turned into CO₂, through
the same LIFE-ledger addition as respiration's (`addForLife`), which never
forms a zone. A breather on internals draws no O₂ from its zone.
`life_045_exhaled_gas` checks the O₂ and CO₂ reach the zone and that it gains
exactly what the tank lost; it doesn't run the invariant checker.*

#### LIFE-046 · Empty tank fallback
`Must` · `R1` · `Implemented` · Verify: GameTest

When the tank can't supply the distribution pressure, the wearer shall
breathe the environment.

- **Rationale:** Inferred: a player whose tank runs dry should breathe the air
  around them rather than suffocate in breathable air.
- **Verify:** A GameTest empties a tank in breathable air and checks the
  player keeps breathing.

*2026-11-02: Implemented ([#36](https://github.com/west3436/zas-cubed/issues/36)). A tank delivers while its distribution
pressure is above 0 and its own pressure is at least that.
`life_046_empty_tank_fallback` checks a tank set above its pressure, a nearly
empty, an empty and a closed tank each leave a sealed player breathing the air,
and that a CO room's gas reaches it only once its tank is empty.
`s_08_breathing_gear` checks a tank running dry in vacuum.*

#### LIFE-023 · Client breathing sync
`Must` · `R1` · `Implemented` · Verify: Manual, GameTest · Trace: [neoforge §9](../research/neoforge-1.21.1.md#9-entities)

NeoForge's breathing event also fires on the client. The local player's
breathing state shall therefore be synced, so the client-side air bar matches
the server without jitter.

- **Rationale:** Inferred: the breathing event fires on the client too, so
  without sync the client refills the bar and it jitters ([neoforge
  §9](../research/neoforge-1.21.1.md#9-entities)).
- **Verify:** Manual checks the air bar is steady in a low-O₂ zone; a GameTest
  checks the synced state.

*2026-11-02: Implemented
([#36](https://github.com/west3436/zas-cubed/issues/36)). The server sends each
player, and only that player, its breathing state (`BreathSyncPayload`): the
last check's verdict without the breath gas, and the air drain while it is
short of O₂. It goes at the first check, then whenever it changes, at most
once every 5 ticks. On the client, `ClientBreath` runs NeoForge's breathing
hook for the local player as `Breathers` does on the server: while the server
finds it short of O₂ and its eyes aren't in a fluid, it drains the bar at the
synced rate and holds an empty bar without drowning bubbles; otherwise vanilla
runs, so the bar refills in breathable air and drowns under water as before.
The server's air value still arrives with the entity data and corrects any
drift. The `life_023_` GameTests check the codec, the sync's triggers and rate,
that the client's bar matches the server's tick for tick in a vacuum and at
8 kPa of O₂, and that air and fluids are left to vanilla. The manual check of
the bar in game is still to do.*

#### LIFE-022 · Underwater
`Should` · `R1` · `Accepted` · Verify: GameTest

While the eyes are in water, vanilla drowning shall apply instead of
atmospheric breathing.

- **Rationale:** Inferred: water is vanilla's domain, and drowning already
  handles it.
- **Verify:** A GameTest checks a submerged player in a zone drowns as in
  vanilla.

*2026-11-02: The verdict is vanilla's while the eyes are in a fluid
([#34](https://github.com/west3436/zas-cubed/issues/34)), checked by
`api_014_keeps_vanilla_underwater`. The drowning GameTest this asks for is left
to [#36](https://github.com/west3436/zas-cubed/issues/36), with LIFE-047.*

#### LIFE-047 · Internals underwater
`Should` · `R1` · `Accepted` · Verify: GameTest · Depends: LIFE-021

Internals that are on and supplied shall give water breathing.

- **Rationale:** Inferred: a sealed mask on a supplied tank would work
  underwater, and it rewards the gear.
- **Verify:** A GameTest checks a player with internals on doesn't drown.

## Damage, configuration and cost

#### LIFE-030 · Damage types
`Must` · `R1` · `Implemented` · Verify: Inspect, GameTest

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

*2026-11-02: `zas:hypoxia` and `zas:toxic_gas` are in, with death messages
([#34](https://github.com/west3436/zas-cubed/issues/34),
[datapacks.md](../datapacks.md#damage-types));
`life_008_empty_meter_deals_2_hp_per_second` and the LIFE-041 GameTests check
they are used. The others come with their hazards
([#35](https://github.com/west3436/zas-cubed/issues/35)), so this stays
Accepted.*

*2026-11-02: Implemented ([#35](https://github.com/west3436/zas-cubed/issues/35)).
`zas:decompression`, `zas:barotrauma`, `zas:frostbite`, `zas:burns` and
`zas:airflow_impact` join them, each with a death message;
`life_030_damage_types` checks all seven exist with their messages, and the
LIFE-010, LIFE-011 and LIFE-014 GameTests check each hazard uses its type.
Airflow impact is dealt by FLOW's work, which checks its own use of it.*

#### LIFE-035 · Armor bypass
`Must` · `R1` · `Implemented` · Verify: GameTest

Every atmospheric damage type except airflow impact shall bypass armor.
Protection comes from LIFE-020 and LIFE-025 instead, so armor doesn't count
twice.

- **Rationale:** Inferred: protection already comes from LIFE-020 and
  LIFE-025, so armor reduction would count twice; impacts are physical, so
  armor applies to them.
- **Verify:** A GameTest compares damage with and without armor for each type.

*2026-11-02: `zas:hypoxia` and `zas:toxic_gas` are in
`#minecraft:bypasses_armor`
([#34](https://github.com/west3436/zas-cubed/issues/34)); the armor GameTest
comes with the other types
([#35](https://github.com/west3436/zas-cubed/issues/35)).*

*2026-11-02: Implemented ([#35](https://github.com/west3436/zas-cubed/issues/35)).
The four new atmospheric types are in `#minecraft:bypasses_armor`, and
`zas:airflow_impact` isn't. `life_035_armor_bypass` hurts a bare zombie and
one in diamond armor with each type: the same damage for each atmospheric
type, less for airflow impact.*

#### LIFE-031 · Hazard toggles and scaling
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-04

Every hazard shall be individually toggleable and scalable.

- **Rationale:** Decision: D-04. Servers and packs tune hazards.
- **Verify:** GameTests turn off and scale each hazard.

*2026-11-02: Implemented ([#35](https://github.com/west3436/zas-cubed/issues/35)).
Each of the seven hazards, one per LIFE-030 damage type, has
`[hazards.<hazard>] enabled` and `scale` ([config.md](../config.md#hazards)).
A hazard that is off harms nothing and isn't listed in a verdict; with
hypoxia off, a breath short of O₂ is breathable. A scale multiplies damage,
not effects or the air meter's drain. FLOW reads airflow impact's.
`life_031_pressure_and_temperature_hazards_turn_off_and_scale`,
`life_031_toxic_gas_turns_off_and_scales` and
`life_031_hypoxia_turns_off_and_scales` check them.*

#### LIFE-036 · Per-dimension hazard overrides
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-04

Hazard toggles and scales shall be overridable per dimension.

- **Rationale:** Decision: D-04.
- **Verify:** A GameTest overrides a hazard in one dimension and checks
  another is unaffected.

*2026-11-02: Implemented ([#35](https://github.com/west3436/zas-cubed/issues/35)).
`[hazards] dimension_overrides` takes entries
`"<dimension> <hazard> <on|off|scale>"`; an entry that doesn't parse is
dropped. `life_036_per_dimension_overrides` turns decompression off and halves
burns in the Overworld and checks the Nether keeps both.*

#### LIFE-037 · Creative and spectator exemption
`Must` · `R1` · `Implemented` · Verify: GameTest

Creative and spectator players shall be exempt from all hazards.

- **Rationale:** Inferred: building and testing in creative mustn't be
  interrupted, as vanilla spares creative players.
- **Verify:** A GameTest checks creative and spectator players in vacuum take
  no harm.

*2026-11-02: For breathing, an invulnerable player (creative or spectator) is
`EXEMPT`, keeps its air and takes no gas hazards
([#34](https://github.com/west3436/zas-cubed/issues/34),
`api_014_invulnerable_player_is_exempt`). The other hazards are
[#35](https://github.com/west3436/zas-cubed/issues/35)'s, so this stays
Accepted.*

*2026-11-02: Implemented ([#35](https://github.com/west3436/zas-cubed/issues/35)).
An exempt player is never judged on pressure or temperature either.
`life_037_creative_and_spectator_are_exempt` checks creative and spectator
players in a vacuum have no hazards, nothing to be charged and a full meter,
while a survival player there has the hazards. Fake players can't be hurt at
all, so it checks what a check would charge rather than their health.*

#### LIFE-032 · Hazard extension API
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-04, D-12

Consumer mods shall be able to register additional hazard handlers per gas or
per tag, for example radiation from a radioactive gas they define. (API-033
intercepts existing hazard ticks; this adds new hazards.)

- **Rationale:** Decision: D-04 and D-12.
- **Verify:** A GameTest registers a handler for a test gas and checks it
  applies.

#### LIFE-033 · Staggered evaluation
`Must` · `R1` · `Implemented` · Verify: Bench · Trace: NFR-001

Hazard evaluation shall run at a per-entity cadence, staggered across
entities.

- **Rationale:** Inferred: evaluating every entity on the same tick causes
  spikes (NFR-001).
- **Verify:** Bench checks hazard work is spread evenly across ticks with 100
  breathers (S-10).

*2026-11-02: Breath checks are staggered by entity id (LIFE-007,
[#34](https://github.com/west3436/zas-cubed/issues/34)); the Bench is left to
the hazards work ([#35](https://github.com/west3436/zas-cubed/issues/35)).*

*2026-11-02: Implemented ([#35](https://github.com/west3436/zas-cubed/issues/35)).
The pressure and temperature hazards run in the same staggered check as
breathing, one check per entity per interval.
`life_033_checks_spread_across_the_interval` checks 100 breathers are each
checked once in a 2 s interval and no tick runs more than 3 checks. The
engine's Bench is headless, so S-10's Bench with a running server is left to
S-10.*

#### LIFE-038 · Per-tick entity cap
`Must` · `R1` · `Implemented` · Verify: Bench · Trace: NFR-001

The number of entities evaluated per server tick shall be capped
(configurable).

- **Rationale:** Inferred: a large mob farm in a zone mustn't break the tick
  budget (NFR-001).
- **Verify:** Bench checks the per-tick count never exceeds the cap.

*2026-11-02: Implemented ([#35](https://github.com/west3436/zas-cubed/issues/35)).
`[breathing] max_checks_per_tick`, 100 by default, caps breath and hazard
checks per server tick over every dimension; a check due beyond it waits for
the next tick. `life_038_checks_per_tick_are_capped` checks 20 breathers due
at once under a cap of 5 are checked 5 a tick, none lost. As with LIFE-033,
the Bench is S-10's.*

#### LIFE-034 · Spawning in lethal atmospheres
`Could` · `R1` · `Accepted` · Verify: GameTest

Breathing mobs should not spawn naturally where the atmosphere is immediately
lethal, such as vacuum.

- **Rationale:** Inferred: mobs spawning into vacuum would die at once,
  wasting work and littering drops.
- **Verify:** A GameTest checks breathing mobs don't spawn naturally in a
  vacuum test dimension.
