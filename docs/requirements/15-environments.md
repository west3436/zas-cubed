# 15 · Environments (`ENV`)

**Status:** Accepted 2026-10-30 (full form 2026-10-31). ENV-019
(added during the API review) and ENV-020 (split from it) were accepted with
SPACE on 2026-10-31. Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules).

**Scope.** Ambient atmospheres, which act as the "unsimulated reservoir":
- per-dimension definitions
- vacuum and space semantics
- the provider API

Per D-07, biome- and altitude-varying ambients are **not** shipped in R1.

## Definitions and defaults

#### ENV-001 · Per-dimension ambient
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-07, S-06

Each dimension shall have an ambient atmosphere defined in datapacks, keyed by
dimension id. The definition gives a composition (moles per 1,000 L, or
partial pressures) and a temperature.

- **Rationale:** vg: ZAS planets and space use fixed reservoir gases per cell
  ([zas-core
  §10](../research/zas-core.md#10-mapping-zas-concepts-to-minecraft-draft-for-requirements-discussion));
  here they are per dimension and data-driven (D-07).
- **Verify:** A GameTest gives a test dimension an ambient by datapack and
  checks queries return it.

#### ENV-010 · Per-preset variants
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-14, D-19

An ambient definition may give separate variants for the Normal and Hardcore
presets. The variant for the active preset applies.

- **Rationale:** Decision: D-14 and D-19.
- **Verify:** A GameTest switches the preset and checks the ambient changes to
  the matching variant.

*2026-11-01: the variant is the preset option `ambient_variant` (OPS-007),
which follows the active preset unless an operator overrides it (OPS-014,
D-14), #37.*

#### ENV-011 · Fallback for undefined dimensions
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-45

A dimension with no ambient from any source (ENV-014) shall get standard
Overworld air: 101.325 kPa, 293.15 K, 79 % N₂, 21 % O₂.

- **Rationale:** Decision: D-45.
- **Verify:** A GameTest checks an undefined test dimension returns standard
  air.

#### ENV-002 · Normal vanilla defaults
`Must` · `R0` · `Implemented` · Verify: Inspect · Trace: D-19

ZAS³ shall ship these Normal-preset ambients, all breathable:

| Dimension | Composition | Temperature |
|---|---|---|
| Overworld | Standard air: 101.325 kPa, 79 % N₂, 21 % O₂ | 293.15 K |
| Nether | Standard air | 313.15 K (40 °C) |
| End | Standard air | 293.15 K |

- **Rationale:** Decision: D-19.
- **Verify:** Inspect the shipped ambient data.

#### ENV-012 · Hardcore vanilla defaults
`Must` · `R0` · `Implemented` · Verify: Inspect · Trace: D-19, D-14

ZAS³ shall ship these Hardcore-preset ambients:

| Dimension | Partial pressures | Temperature | Hazard |
|---|---|---|---|
| Overworld | Standard air | 293.15 K | None |
| Nether | CO₂ 12 kPa, O₂ 18 kPa, N₂ 71.325 kPa (101.325 kPa total) | 340 K | CO₂ above the 10 kPa incapacitating level: needs a mask and internals |
| End | O₂ 8.4 kPa, N₂ 31.6 kPa (40 kPa total) | 263.15 K | O₂ below the 16 kPa hypoxia minimum: needs a mask and internals, not a suit |

The values were chosen against the LIFE-003, LIFE-004, LIFE-010 and LIFE-011
defaults and are retuned if those change.

- **Rationale:** Decision: D-19 (a hot, CO₂-rich Nether and a thin End). The
  values were chosen against the LIFE thresholds so each needs the gear
  listed.
- **Verify:** Inspect the shipped data; LIFE's GameTests check the hazards
  occur.

#### ENV-003 · Vacuum
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-07, S-01

A dimension shall be able to declare a vacuum ambient: no gas, at the cosmic
background temperature (2.7 K) unless the definition gives another
temperature. In such a dimension every non-enclosed region is vacuum.

- **Rationale:** vg: ZAS space is an empty reservoir at the cosmic background
  temperature ([zas-core
  §2.1](../research/zas-core.md#21-gas-mixture-_gas_mixturedm)); D-07, and
  D-52 for the settable temperature.
- **Verify:** A GameTest checks open cells in a vacuum test dimension report 0
  kPa at 2.7 K, or at the declared temperature.

*2026-10-31: added the settable vacuum temperature during the SPACE review
(SPACE-020, D-52). It is the radiative background T_bg (HEAT-004) and the
exposure temperature (LIFE-011).*

## Normal-preset survival

#### ENV-009 · No hazards in Normal
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-13, D-19

With the Normal preset and no atmos devices in use, ZAS³ shall cause no
atmospheric hazards in the vanilla dimensions. (No zones form there either,
per CORE-055.)

- **Rationale:** Decision: D-13 and D-19.
- **Verify:** A GameTest under Normal checks a player in each vanilla
  dimension takes no atmospheric harm.

*2026-11-05: `env_009_no_atmospheric_harm_in_any_vanilla_dimension` puts a player
that can be hurt in the open air of each dimension for 20 s of checks and game
ticks (#66). The test's coverage is open air: it doesn't try every biome or a
player in a cave.*

#### ENV-013 · Vanilla fire and furnaces in Normal
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-13, S-04

With the Normal preset, fire and furnaces outside zones shall behave as in
vanilla.

- **Rationale:** Decision: D-13 (S-04).
- **Verify:** A GameTest under Normal checks fire spreads and a furnace smelts
  outside zones as in vanilla.

*2026-11-05: `env_013_fire_spreads_outside_zones_in_every_vanilla_dimension` and
`env_013_furnace_smelts_at_vanillas_rate_outside_zones` pass in each dimension
(#66). The furnace is checked against vanilla's exact rate. Fire is random, so it's
checked to spread and stay alight on netherrack, not against vanilla's odds.*

## Providers

#### ENV-004 · Ambient providers
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-04, S-06

Mods shall be able to register a provider that supplies a dimension's ambient
programmatically.

- **Rationale:** Decision: D-04 and D-12. Space mods supply their planets'
  ambients (S-06).
- **Verify:** A GameTest registers a test provider and checks it sets the
  dimension's ambient.

*2026-11-05: `AmbientProviders` in the API (#66).
`env_004_provider_sets_the_dimensions_ambient` checks queries, the lookup, and a
zone pinned in the dimension, which fills with the provider's gas;
`s_12_ambient_providers_are_kept_but_never_called` checks a game without `zas`.*

#### ENV-014 · Ambient source precedence
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-45

A dimension's ambient shall come from the first of these sources that defines
it:
1. a datapack definition (ENV-001)
2. a registered provider (ENV-004)
3. an ambient seeded from another mod's data (SPACE-020)
4. the fallback (ENV-011)

- **Rationale:** Decision: D-45.
- **Verify:** GameTests define a dimension's ambient from each pair of sources
  and check the higher one wins.

*2026-11-05: the six `env_014_` pair tests pass (#66). The third source's data
comes with the Ad Astra adapter in R2 (SPACE-020, D-66), so until then the
tests fill its slot in the chain with a test-only ambient
(`Ambients.seedForTest`).*

*2026-11-06: a provider that throws still defines the dimension, with its last good
answer. The owner's decision of 2026-11-06, recorded in full at NFR-013: "keep the
provider's last good answer, using the next source only when there's no good answer
yet". A provider that answers `null` still defines nothing.*

#### ENV-019 · Provider conflicts
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-45 · Depends: ENV-004

If more than one provider is registered for a dimension, the one with the
highest declared priority (default 0) shall apply. A tie goes to the
lexicographically smallest mod id.

- **Rationale:** Inferred: two mods may claim one dimension, and the winner
  must be predictable; mod id is a stable tie-break.
- **Verify:** A GameTest registers providers with different and equal
  priorities and checks which applies.

*2026-11-05: `env_019_highest_priority_applies` and
`env_019_tie_goes_to_the_smallest_mod_id` pass (#66). The mod id is the namespace
of the id a provider registers with.*

#### ENV-020 · Provider conflict warning
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-45 · Depends: ENV-019

When more than one provider is registered for a dimension, ZAS³ shall log one
warning naming the providers and the one that applies.

- **Rationale:** Inferred: a silent conflict would be hard for pack makers to
  diagnose.
- **Verify:** The ENV-019 GameTest checks one warning is logged with both
  providers and the winner.

*2026-11-05: `env_020_one_warning_names_the_providers_and_the_winner` reads the
log (#66). It's one warning per set of providers for a dimension: a change in who
is registered warns again.*

#### ENV-015 · Providers run on the server thread
`Must` · `R1` · `Implemented` · Verify: Inspect, GameTest · Trace: D-30, CORE-045, CORE-049

Providers shall be called on the server thread, and their results snapshotted
for the simulation once per atmos tick.

- **Rationale:** Decision: D-30.
- **Verify:** Inspect the code; a GameTest checks the provider runs on the
  server thread once per atmos tick.

*2026-11-05: Inspected: `DimensionAtmos.close` calls
`Ambients.takeProviderAnswer` as a batch closes, and `ProvidedAmbients.call` is
the only caller of a provider (#66).
`env_015_provider_runs_on_the_server_thread_once_per_atmos_tick` and
`env_015_provider_runs_once_per_real_atmos_tick` pass. The answer is also taken
once at server start, so a dimension doesn't read as the fallback until its first
batch.*

#### ENV-005 · Position-dependent providers
`Could` · `R2` · `Deferred` — moved to R2 with biome and altitude ambients (D-07), 2026-10-30. · Verify: GameTest

The provider API may accept a position, so that third parties can vary the
ambient by altitude or region. ZAS³ itself ships no biome or altitude rules.

#### ENV-016 · Edges keyed by the full ambient
`Must` · `R2` · `Deferred` — moved to R2 with ENV-005, 2026-10-30. · Verify: Unit · Trace: [zas-core §2.5](../research/zas-core.md#25-connections-and-edges-connectiondm-connectiongroupdm) · Depends: ENV-005

When ambients vary by position, ambient edges shall be keyed by the full
ambient composition and temperature, never by a subset of gases.

## Changes and exclusion

#### ENV-006 · Live ambient changes
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: [zas-core §9](../research/zas-core.md#9-quirks-dont-replicate-blindly)

Existing ambient edges shall pick up a changed ambient within one atmos tick.
The change may come from a datapack reload or from a provider, for example a
planetary day/night temperature.

- **Rationale:** Inferred: ZAS snapshots reservoir air when an edge forms
  ([zas-core §9](../research/zas-core.md#9-quirks-dont-replicate-blindly),
  quirk 4), which would freeze day/night ambients.
- **Verify:** A GameTest reloads a datapack that changes an ambient's
  temperature and checks a zone beside it moves toward the new value from the
  next tick.

*2026-11-05: `env_006_a_reloaded_datapack_moves_a_zone_from_the_next_atmos_tick`
really reloads a pack, and
`env_006_a_providers_changing_answer_moves_a_zone_every_atmos_tick` does the same
through a provider (#66). The zone in both is a pinned room with a hole, the only
zone that can stay beside an ambient that matches it (D-63, D-64).*

#### ENV-017 · Ambient changes add no extra work
`Must` · `R1` · `Implemented` · Verify: Unit, Bench · Trace: D-63, D-64 · Depends: CORE-006, CORE-066

A change to the ambient shall add no extra work: it wakes no edge and adds no
exchange.

- **Rationale:** Inferred: providers may change an ambient slightly every tick
  (day/night), which mustn't cost much.
- **Verify:** Unit tests check a sleeping edge stays asleep and exchange calls
  don't change when the ambient does; Bench measures a provider changing every
  tick.

*2026-11-05: reworded; the owner chose this over revisiting D-64. It used to say
that a change wakes a sleeping ambient edge only once it breaks equivalence with
the mixture the zone snapped to. No such edge exists: a zone that matches its
ambient dissolves (D-63) unless pinned, and a pinned one keeps exchanging (D-64),
so ambient edges never sleep and nothing snaps (#66).*

*2026-11-05: `AmbientChangeTest`'s `env_017_` tests pass. The ambient bench has
the same exchange calls per tick with a still ambient and a changing one in every
world ([benchmarks](../research/benchmarks.md#ambient-changing-every-tick-env-017)).
Walls still conduct toward the new temperature (HEAT-001), since that's the change
doing its job, which costs each zone a heat step: within noise for S-10, and about
0.25 ms of worker time per atmos tick with 1,000 more rooms. The bench found one
real cost, resolving every zone's heat links on each change, which is gone
(`env_017_a_changing_ambient_resolves_no_links_unless_it_turns_to_vacuum`).*

#### ENV-007 · Excluded dimensions
`Must` · `R0` · `Implemented` · Verify: GameTest

Config or datapacks shall be able to exclude a dimension from simulation, so
that no zones exist there.

- **Rationale:** Inferred: some dimensions, such as minigame or void
  dimensions, shouldn't be simulated at all.
- **Verify:** A GameTest vents gas in a sealed room in an excluded dimension
  and checks no zone forms.

*2026-10-31: D-65: when a dimension becomes excluded, its existing zones are
purged and its chunks save without them. An unloaded dimension is different:
its zones persist and pause.*

#### ENV-018 · Excluded dimensions keep their ambient
`Must` · `R1` · `Implemented` · Verify: GameTest

An excluded dimension shall keep its ambient for queries (CORE-050) and
hazards (LIFE). Gas operations there behave as in open air (CORE-053,
CORE-057).

- **Rationale:** Inferred: excluding a dimension mustn't make it breathable;
  it only stops zones forming.
- **Verify:** A GameTest checks an excluded vacuum dimension still harms an
  unsuited player and that added gas goes to open air.

*2026-11-05: `env_018_an_excluded_vacuum_harms_an_unsuited_player` and
`env_018_gas_operations_in_an_excluded_vacuum_act_on_the_open_air` pass (#66), in
the End, borrowed with a test definition that excludes it. Also
`env_018_an_excluded_ambient_with_gas_supplies_what_is_taken`, and
`env_018_a_provider_still_sets_an_excluded_dimensions_ambient`.*

#### ENV-008 · Not atmospheric
`Won't` · `R1` · `Accepted`

ZAS³ shall not model gravity, radiation, weather or seasons. Those belong
to consumer mods, which may feed effects in through the API.

- **Rationale:** Inferred: these are outside an atmosphere framework's scope
  and belong to dedicated mods; keeps R1 lean.
- **Verify:** Inspect: design review confirms none of these systems is added.
