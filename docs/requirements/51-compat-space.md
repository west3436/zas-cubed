# 51 · Space-mod compat (`SPACE`)

**Status:** Accepted 2026-10-31 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules).

**Scope.** Making ZAS³ the atmosphere authority alongside existing space
mods on NeoForge 1.21.1 (D-07, D-51). This covers:
- breathing, oxygen and temperature answers
- seeding ambients from those mods' planet data
- tier-1 adapters

With an adapter active, ZAS³ is the only source of atmospheric harm. The other
mod's own oxygen and temperature effects are suppressed, its queries are
answered from ZAS³, and its world (planet data, breathable areas, suits, mob
tags) feeds into ZAS³ as inputs (D-51).

Background:
[space-mods-and-prior-art](../research/space-mods-and-prior-art.md).

**Key research facts (2026-10-30):**
- No library owns the shared oxygen or atmosphere niche on 1.21.1.
- The tier-1 targets all integrate without mixins:
  - **Ad Astra 1.16.26.** Public `AdAstraEvents` and `OxygenApi`; confirmed on
    the `1.21.1` branch.
  - **Create: Northstar – Redux 0.6.6.** Register a `NorthstarOxygen.Provider`.
  - **Create Cosmonautics.** A `LivingBreatheEvent` listener.
- **Decided (D-29): R1 ships the Ad Astra adapter only.** Northstar Redux and
  Create Cosmonautics are deferred to R2.

**For spike S6 to confirm:** Ad Astra's suit item ids and temperature-immunity
tags; whether suit oxygen is reachable through a capability (SPACE-023);
whether Ad Astra asks `EntityOxygenEvent` on the client for its HUD; and the
1.16.26 version floor (SPACE-003).

## Interop model

#### SPACE-001 · Compat modes
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-07, D-51, S-06

Each adapter shall have two modes, set per mod:
- **Cooperative:** the other mod's own breathable areas also count as
  breathable for ZAS³ (for Ad Astra, SPACE-017).
- **Authoritative:** only ZAS³'s atmosphere counts.

In both modes ZAS³ is the only source of atmospheric harm (SPACE-013).

- **Rationale:** Decision: D-51.
- **Verify:** GameTests in each mode check a distributor area counts as
  breathable only in Cooperative, and that ZAS³ alone deals harm in both.

#### SPACE-004 · Default mode follows the preset
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-14, D-51 · Depends: SPACE-001, OPS-007

The default mode shall be Cooperative under the Normal preset and
Authoritative under the Hardcore preset (OPS-007). A per-mod setting overrides
it.

- **Rationale:** Decision: D-51 and D-14.
- **Verify:** GameTests check the default mode under each preset and that a
  per-mod setting overrides it.

#### SPACE-002 · NeoForge breathing event
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: API-014, LIFE-003, LIFE-023

ZAS³ shall express its breathing verdict (API-014) through NeoForge's
`LivingBreatheEvent`. Its listener runs at `NORMAL` priority, sets
`canBreathe` to false when the verdict is "can't breathe", and otherwise leaves
the event unchanged, so it never overrides another mod's "can't breathe". A
listener at `LOW` priority therefore sees ZAS³'s answer. (On the client, the
local player's verdict comes from the synced state in LIFE-023.)

- **Rationale:** Inferred: other mods read breathing through NeoForge's event
  ([neoforge §9](../research/neoforge-1.21.1.md#9-entities)); setting only
  "can't breathe" at normal priority never overrides another mod's verdict.
- **Verify:** GameTests check a LOW-priority listener sees ZAS³'s verdict and
  that another mod's "can't breathe" survives.

#### SPACE-003 · Adapter version check
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: NFR-022, D-29

Each adapter shall check its target mod's version at runtime against a
supported range; for Ad Astra, `[1.16.26,1.17)`. Out of range, the adapter
disables itself and logs one warning, and the game still launches. Adapters
therefore declare no version range in `neoforge.mods.toml`, where a mismatch
would stop the game from launching. Loading only when the target mod is present
is NFR-022.

- **Rationale:** Inferred: Ad Astra's API can change between versions, and a
  version range in the mod metadata would stop the game launching instead of
  disabling one adapter.
- **Verify:** A GameTest with a mocked out-of-range version checks the adapter
  disables itself, logs once, and the game runs.

## Tier-1 adapters

#### SPACE-010 · Ad Astra oxygen checks agree
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-06, D-51 · Depends: API-014

Ad Astra's entity oxygen check (`OxygenApi.hasOxygen(Entity)`) shall return
ZAS³'s breathability verdict (API-014) for every entity in every dimension.

*Note for S6: implemented by answering `EntityOxygenEvent`.*

- **Rationale:** Decision: D-51 (S-06).
- **Verify:** GameTests with Ad Astra check `hasOxygen` matches API-014 in a
  sealed zone, in vacuum and on the Overworld.

#### SPACE-013 · Ad Astra applies no atmospheric harm
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-51, S-06

While the adapter is active, Ad Astra shall apply no oxygen damage, temperature
damage or suit oxygen drain to any entity. ZAS³'s hazards (LIFE) apply instead.

*Note for S6: veto `OxygenTickEvent` and `TemperatureTickEvent`, and answer
`HotTemperatureTickEvent` and `ColdTemperatureTickEvent` with false.*

- **Rationale:** Decision: D-51. Two damage sources would hurt players twice.
- **Verify:** A GameTest on an Ad Astra planet checks only ZAS³'s damage types
  apply and suit oxygen doesn't drain.

#### SPACE-014 · Ad Astra environment effects inside zones
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-51, D-49

Ad Astra's per-block environment effects (putting out fire, freezing water,
killing plants) shall not run at positions inside zones. ZAS³'s fire rules
(D-49) govern there.

*Note for S6: implemented by answering `EnvironmentTickEvent`.*

- **Rationale:** Decision: D-51 and D-49. Inside a zone ZAS³ knows the real
  atmosphere.
- **Verify:** A GameTest checks fire, water and plants inside a pressurized
  zone on the Moon behave per ZAS³, and outside per Ad Astra.

#### SPACE-015 · Torches inside zones
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-49, S-06

Blocks in Ad Astra's `#ad_astra:destroyed_in_space` tag, such as torches,
shall be placeable inside zones whose atmosphere meets D-49's fire thresholds:
an O₂ fraction of at least 0.14 and an O₂ partial pressure of at least 10 kPa.
(Once placed they stay, per SPACE-014.)

*Note for S6: Ad Astra cancels `EntityPlaceEvent` for these blocks where it
sees no oxygen; a later listener that receives cancelled events can re-allow
it.*

- **Rationale:** Decision: D-49. Ad Astra cancels placing these blocks where
  it sees no oxygen ([space research
  §A1](../research/space-mods-and-prior-art.md#a1-available-on-neoforge-1211)),
  which would wrongly forbid torches in a pressurized station.
- **Verify:** A GameTest checks a torch places in a breathable zone on the
  Moon and not in vacuum.

#### SPACE-017 · Distributor areas in Cooperative mode
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-51, API-014 · Depends: SPACE-001

In Cooperative mode, at positions outside zones that an Ad Astra oxygen
distributor oxygenates, ZAS³'s hazards and breathability helper (API-014)
shall treat the atmosphere as standard air (101.325 kPa, 79 % N₂, 21 % O₂) at
295.15 K (Ad Astra's 22 °C). Inside zones, the zone governs. Atmosphere
queries (API-010) still report ZAS³'s own state.

*Note: a distributor area is where `hasOxygen(level, pos)` holds and
`hasOxygen(level)` doesn't.*

- **Rationale:** Decision: D-51; 22 °C is Ad Astra's distributor temperature
  ([space research
  §A1](../research/space-mods-and-prior-art.md#a1-available-on-neoforge-1211)).
- **Verify:** GameTests in Cooperative mode check a player in a distributor
  area breathes and takes no harm, while queries report ZAS³'s own state.

#### SPACE-019 · Ad Astra's space-native mobs
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: D-51, LIFE-015, LIFE-016

The adapter shall add Ad Astra's `#ad_astra:lives_without_oxygen` and
`#ad_astra:can_survive_in_space` entity tags to `zas:atmosphere_immune` as
optional entries, so Ad Astra's mobs survive on their own planets.

- **Rationale:** Decision: D-51. Ad Astra's mobs must survive on their own
  planets.
- **Verify:** Inspect the tag entries; a GameTest checks an Ad Astra space mob
  takes no harm in vacuum.

#### SPACE-016 · Public API only
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-29, plan R6

The Ad Astra adapter shall use only Ad Astra's public API package
(`earth.terrarium.adastra.api`), with no mixins.

- **Rationale:** Decision: D-29. The public API is stable, and mixins into Ad
  Astra break on its updates ([space research
  §A1](../research/space-mods-and-prior-art.md#a1-available-on-neoforge-1211)).
- **Verify:** Inspect the adapter's imports and mixin configuration.

#### SPACE-018 · Constant-time event answers
`Must` · `R1` · `Accepted` · Verify: Bench · Trace: NFR-001, API-046

Each answer to an Ad Astra event shall be an O(1) lookup in the last applied
snapshot (API-046). Ad Astra's environment event fires per block, so it is a
hot path.

- **Rationale:** Inferred: Ad Astra asks about the environment once per block,
  so anything slower than a lookup would cost too much (NFR-001).
- **Verify:** Bench measures answer time on a planet with many blocks against
  NFR-001.

#### SPACE-011 · Northstar Redux
`Should` · `R2` · `Deferred` · Verify: GameTest · Trace: S-06, D-29

The adapter shall register one `NorthstarOxygen.Provider` per level:
- `isSealed` is an O(1) zone lookup
- `drainOxygen` takes from the zone's O₂
- zone changes are reported through `enqueueUpdates`

Risk: medium. Northstar is beta, and these classes sit outside its `api/`
package.

#### SPACE-012 · Create Cosmonautics
`Should` · `R2` · `Deferred` · Verify: GameTest · Trace: D-29

Inside breathable zones, a `LivingBreatheEvent` listener at `HIGH` priority
shall grant breathing. This needs no compile-time dependency.

## Data interop

#### SPACE-020 · Ambient seeding from Ad Astra planets
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: ENV-014, D-45, D-52, S-06

When neither a datapack nor a provider defines the ambient of a dimension
listed in Ad Astra's `planets` data (ENV-014), ZAS³ shall seed it:

| Ad Astra `oxygen` | Seeded ambient |
|---|---|
| true | Standard air (101.325 kPa, 79 % N₂, 21 % O₂) at the planet's temperature |
| false | Vacuum (ENV-003) at the planet's temperature |

The planet's temperature is converted from °C to K; for example, a −160 °C
moon seeds at 113.15 K. Seeded ambients are the same in both presets. Seeding
from Northstar `planet_dimension` and Sable `dimension_physics` comes with those
adapters in R2.

- **Rationale:** Decision: D-45 and D-52.
- **Verify:** GameTests check an Ad Astra oxygen planet seeds standard air and
  an airless one vacuum, each at its converted temperature.

#### SPACE-022 · Ad Astra suit protection
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: S-06, D-51, LIFE-026, D-47

The adapter shall ship protection data (LIFE-026) for Ad Astra's suits:

| Suit | Seal | Pressure | Thermal |
|---|---|---|---|
| Space suit | Full; the helmet supports internals | Low pressure down to vacuum; no high-pressure rating | 0–500 K |
| Netherite space suit, jet suit | Full; the helmet supports internals | All pressures | All temperatures |

None of them carries the hardsuit's speed penalty (CONT-089). Item ids are
confirmed in spike S6.

- **Rationale:** Decision: D-51 and D-47. Ad Astra suits map onto ZAS³'s
  softsuit and hardsuit ratings.
- **Verify:** Inspect the data; GameTests check each suit's protection in
  vacuum, at 600 kPa and at 737 K (Venus).

#### SPACE-023 · Ad Astra suit oxygen as internals
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-51, LIFE-028, LIFE-043, CONT-030 · Depends: SPACE-022

If spike S6 shows Ad Astra's suit oxygen store is reachable through NeoForge's
fluid item capability, internals shall draw from it as the suit's tank
(LIFE-028). It behaves as an O₂ tank at 293.15 K (LIFE-043, LIFE-044); a full
store supplies 17.5 mol of O₂, as one ZAS³ O₂ tank does (CONT-030), scaled by
its fill fraction. This conversion is local to the adapter and is a gameplay
scale. If the store isn't reachable, Ad Astra suits give protection only.

- **Rationale:** Decision: D-51; the 17.5 mol scale is a gameplay conversion,
  matching one ZAS³ O₂ tank.
- **Verify:** A GameTest, if S6 confirms access, checks internals draw from a
  full Ad Astra suit and that half a store gives 8.75 mol.

#### SPACE-021 · Shared fluid tags
`Should` · `R2` · `Deferred` — moved to R2: ZAS³ stores gas as mixtures in moles, not as fluids, and the mB↔mol rule is parked with Mekanism (Q-19), 2026-10-31. · Verify: GameTest · Trace: D-12

ZAS³ gas containers shall interoperate with the shared `#c:oxygen`,
`#c:hydrogen` and `#c:nitrogen` fluid tags. This needs a registered fluid type
per gas (no buckets or blocks) and a fluid-handler capability on containers
that moves one pure gas at the Q-19 rate. Mekanism's chemical-handler
capability is deferred with MEK (D-11).

## Tier 2 and later

#### SPACE-030 · Stellaris and Creating Space
`Could` · `R2` · `Deferred` — tier-2 targets after R1 (D-29), 2026-10-31. · Verify: GameTest

These two mods have no API, so adapters would need mixins:
- Stellaris: `DimensionOxygenManager#breath` and `#hasOxygenAt`
- Creating Space: `CSEventHandler#inO2`

Stellaris 2.x renames its packages.

#### SPACE-031 · Temperature mods
`Could` · `R2` · `Deferred` — deferred with the body-temperature model (LIFE-013), 2026-10-31. · Verify: GameTest · Trace: LIFE-013

Zone temperature shall be fed to Cold Sweat (`TempModifier`), Tough As Nails
(positional modifier), Legendary Survival Overhaul and Thermoo, through their
APIs.

#### SPACE-032 · Moving structures
`Won't` · `R1` · `Accepted` · Trace: D-07

R1 shall not support atmospheres on Sable sub-levels, Valkyrien Skies, Create
contraptions or Create: Deep Seas.

- **Rationale:** Decision: D-07.
- **Verify:** Inspect: no adapters for these mods exist.
