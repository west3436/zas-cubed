# 30 · Public API (`API`)

**Status:** Accepted 2026-10-30 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules). API-019 was added and accepted on
2026-10-31 (D-57). API-050 was added as Draft on 2026-11-01 (D-68), and API-051 and API-052
on 2026-11-02 ([#157](https://github.com/west3436/zas-cubed/issues/157)); the
owner accepted both on 2026-11-03. API-053, the ignition source tag, and
API-055 to API-058, the fire API, were added as Draft on 2026-11-03
([#42](https://github.com/west3436/zas-cubed/issues/42)); the owner accepted all
five the same day. API-059, whether container explosions break blocks, was added
as Draft on 2026-11-04 for CONT-107. API-060 and API-061, the client's sound API,
were added as Draft on 2026-11-05 for CLIENT-015 and CLIENT-016. The owner
accepted all three on 2026-11-05. API-063 is Draft until the owner's review.

**Scope.** The contract that consumer mods depend on: its packaging,
versioning, threading, queries and mutations, extension points and events.
D-04 requires that it meet public-library standards from the start, and D-12
makes it the intended shared atmosphere API for NeoForge 1.21.1.

## Packaging and stability

#### API-001 · Standalone API module
`Must` · `R0` · `Implemented` · Verify: Inspect · Trace: D-04, D-12, D-15, D-17

The public API shall be its own module jar whose packages all sit under
`io.github.west3436.zas.api`, with none of the framework's packages. It holds
interfaces, records, events and capability definitions.

- **Rationale:** Decision: D-04, D-12, D-15 and D-17.
- **Verify:** Inspect: a build check fails if the API jar holds any package
  outside `io.github.west3436.zas.api`.

#### API-007 · Jar-in-jar bundling
`Must` · `R1` · `Implemented` · Verify: Inspect, GameTest · Trace: D-17

ZAS³ shall bundle the API module as a jar-in-jar, so that ZAS³ and consumer
mods that bundle it share one copy.

- **Rationale:** Decision: D-17.
- **Verify:** Inspect the jar; a GameTest loads ZAS³ with a consumer that
  bundles the API and checks only one copy is loaded.

*2026-11-06: Implemented. The `zas` jar holds the API only as
`META-INF/jarjar/io.github.west3436.zas-api-0.1.0.jar`, listed in its `metadata.json`.
`:example-addon:runOneCopyGameTestServer`, its own step in CI, loads the built `zas` jar and
a built copy of the example addon with no other API on the classpath.
`api_007_zas_and_a_consumer_both_nest_the_api` checks that both jars nest it, and
`api_007_only_one_copy_of_the_api_is_loaded` that the game's class loader finds one
`ZasApi`, in one module, and that the addon's copy sees `zas` as the real provider.*

#### API-008 · Maven Central publishing
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-18

The API shall be published to Maven Central under `io.github.west3436`, with
sources and javadoc jars.

- **Rationale:** Decision: D-18.
- **Verify:** Inspect the published artifact for sources and javadoc jars.

#### API-006 · Works without ZAS³
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-17, S-12

A consumer mod that bundles the API shall run without ZAS³ installed, and
nothing crashes.

- **Rationale:** Decision: D-17 (S-12).
- **Verify:** A GameTest runs the example addon without ZAS³ and checks the
  S-12 scenario completes without errors.

*2026-11-05: Implemented. CI's "S-12 API without ZAS³" step runs the example addon
alone, and the `s_12_` GameTests in `WithoutZasTests` pass with no errors (API-048).*

#### API-009 · Safe defaults without ZAS³
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-17, S-12

Without ZAS³, every API call shall return safe defaults:
- ambient queries return standard air (ENV-011)
- mutations are no-ops reported as "absent"
- the breathability helper (API-014) follows vanilla rules, so being
  underwater still counts

- **Rationale:** Decision: D-17.
- **Verify:** A GameTest without ZAS³ checks each kind of call returns its
  listed default.

*2026-11-05: Implemented. On the example addon's GameTest server,
`s_12_queries_return_standard_air` checks the ambient queries,
`s_12_mutations_complete_absent` the gas and heat mutations, and the
`api_014_fallback_` tests vanilla's breathing verdict, underwater included.*

#### API-040 · Presence check
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-17

The API shall let consumers check whether a real implementation is present.

- **Rationale:** Inferred: consumers need to turn atmosphere features (HUD,
  gear) on or off depending on whether the atmosphere is real.
- **Verify:** GameTests check the answer with and without ZAS³ installed.

*2026-11-05: Implemented. `api_040_present_with_zas` checks the answer with ZAS³,
and `s_12_api_absent_without_zas` without it.*

#### API-002 · Semantic versioning
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-04

The API shall be versioned with semver, independently of the mod version.
Breaking changes happen only in a major version.

- **Rationale:** Decision: D-04 (public-library standards).
- **Verify:** Inspect the build and release notes for a semver API version
  separate from the mod version.

#### API-041 · 1.0 at the first public release
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-04

R0 builds of the API shall be versioned `0.x`, with no compatibility
guarantee. The first R1 release is 1.0.0.

- **Rationale:** Inferred: the R0 API is still changing, and freezing it at
  1.0 for the first release gives consumers a stable starting point (D-04).
- **Verify:** Inspect the version numbers of R0 and R1 builds.

*2026-11-06: The move to 1.0.0 has to change the nested API's range as well.
`zas/build.gradle` and `example-addon/build.gradle` ask for `[${api_version},1.0)`,
which is empty at 1.0.0. Found by reading the build files; a build at 1.0.0 hasn't
been tried.*

#### API-042 · Deprecation before removal
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-04

Anything removed from the API shall first be deprecated for at least one minor
version.

- **Rationale:** Inferred: the usual library convention under D-04, giving
  consumers one minor version to migrate.
- **Verify:** Inspect: the CI API diff (API-043) flags any removal that wasn't
  deprecated in an earlier minor version.

*2026-11-06: Still Accepted. `:zas-api:apiCompat` now fails on a removal its baseline
didn't mark `@Deprecated`, even in a new major, where it used to report only. Against a
scratch baseline of 1.0.0, a 2.0.0 failed on a field, a method and a class with no mark,
and on members of a class marked only in 2.0.0, and passed with only marked ones removed.
The policy is in [docs/api-consumers.md](../api-consumers.md#versioning-and-the-compatibility-check).
CI compares nothing until `api_baseline_version` names a release (API-043). The check sees
only that baseline, so it can't tell which release added a mark, and it exempts a `0.x`
baseline, for which API-041 promises nothing.*

#### API-043 · API-diff check in CI
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-04

CI shall check API compatibility automatically against the last release.

- **Rationale:** Inferred: manual review misses accidental breaks, and D-04
  promises stability.
- **Verify:** Inspect: the CI job runs on every build and fails on a
  deliberate test break.

*2026-11-03: Still Accepted. `:zas-api:apiCompat` runs in CI's `check`, and it
failed on a deliberate break against a scratch baseline ([#87](https://github.com/west3436/zas-cubed/pull/87)). Until
`api_baseline_version` names a release it skips with a notice, so CI compares
nothing yet. Set it after the first release ([#61](https://github.com/west3436/zas-cubed/issues/61)).*

#### API-003 · No optional-mod references
`Must` · `R0` · `Implemented` · Verify: Inspect

The API shall not reference Mekanism or any other optional mod.

- **Rationale:** Decision: D-11 and A-01. Optional mods stay optional, and the
  API must load anywhere (D-17).
- **Verify:** Inspect: a build check fails if the API depends on anything but
  Minecraft and NeoForge.

#### API-044 · No internal references
`Must` · `R0` · `Implemented` · Verify: Inspect

The API shall not reference ZAS³ internals.

- **Rationale:** Decision: D-17. The API must work without ZAS³ installed.
- **Verify:** Inspect: a build check fails if the API module depends on the
  framework.

#### API-004 · Documented threads
`Must` · `R0` · `Implemented` · Verify: Inspect

Every API method shall document which thread it may be called from.

- **Rationale:** Decision: D-30. The threading rules are part of the contract.
- **Verify:** Inspect the javadoc of every API method.

#### API-045 · Server-thread mutations
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-30

Mutations shall be callable only from the server thread.

- **Rationale:** Decision: D-30.
- **Verify:** A Unit test calls a mutation from another thread and checks it
  is refused.

#### API-046 · Snapshot reads
`Must` · `R0` · `Implemented` · Verify: Unit · Trace: D-30, CORE-069

Read queries shall return immutable snapshots of the last applied atmos tick.

- **Rationale:** Decision: D-30.
- **Verify:** Unit tests check a returned snapshot can't be changed and
  doesn't change after the next tick.

#### API-005 · Javadoc
`Must` · `R1` · `Implemented` · Verify: Inspect · Trace: D-04

Every public API type and member shall have javadoc.

- **Rationale:** Decision: D-04.
- **Verify:** Inspect: CI's javadoc lint treats a missing comment as an error.

*2026-11-06: Implemented. `:zas-api:javadoc`, which `check` runs and so CI's build, runs
with `-Xdoclint:all -Werror`. A probe class failed the build on a type, a nested type, an
enum constant, a constant, a public method and a protected method with no comment, on a
default constructor, and on a method's missing `@param` and `@return`. The API as it stands
passes with no warning.*

#### API-047 · Example addon
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-04

The repository shall include an example addon that uses each extension point.

- **Rationale:** Decision: D-04. Working examples are documentation consumers
  can copy.
- **Verify:** Inspect the example addon against the list of extension points.

#### API-048 · Example addon without ZAS³ in CI
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-12, D-17

CI shall run the example addon without ZAS³ installed, as the S-12 test.

- **Rationale:** Decision: D-17 (S-12).
- **Verify:** Inspect the CI configuration; the job itself is the S-12
  GameTest.

*2026-10-31: Implemented. CI's "S-12 API without ZAS³" step runs
`:example-addon:runGameTestServer`, a GameTest server with the example addon
and its nested API but no `zas`; `WithoutZasTests` checks the API reports
absent, every query reads ambient standard air with no zone, and mutations
complete at once as `ABSENT`.*

## Queries and mutations

#### API-010 · Atmosphere query
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: S-06, D-43

Given a level and a point, the API shall return:
- composition
- partial pressures
- total pressure
- temperature
- the state: zoned, ambient, paused or unresolved
- the zone id, if zoned

- **Rationale:** Decision: D-12 and D-43. This is the query every consumer
  needs, including the paused and unresolved states.
- **Verify:** A GameTest queries a zoned, an ambient, a paused and an
  unresolved point and checks every field.

*2026-11-01: A loaded, unzoned cell of a region left unresolved because it
reaches an unloaded chunk, where a breach of zones is held (GEO-026), now reads
as unresolved; it read as ambient before
([#109](https://github.com/west3436/zas-cubed/issues/109)). The published table
lists the cells of held regions. Checked by
`api_010_held_breach_region_is_unresolved` (Unit and GameTest).*

*2026-11-02: A loaded chunk's cells now read as unresolved, not ambient, until
the zones it saved are back in the published table
([#153](https://github.com/west3436/zas-cubed/issues/153)). Before, a point in
a saved room read as the dimension's ambient for up to an atmos tick after its
chunk loaded or the server started. Checked by the GameTests
`api_010_reloading_room_is_unresolved_until_its_zone_returns`,
`api_010_reloading_room_never_reads_ambient`, which samples the room at every
game tick from its load until its zone is back, and
`api_010_restarted_room_is_unresolved_until_its_zone_returns`.*

#### API-015 · Point resolution
`Must` · `R0` · `Implemented` · Verify: Unit, GameTest · Trace: D-44

A call at an exact point (`Vec3`) shall use the cell that contains it, or the
nearest cell if the point is inside the block's collision shape. A call at a
block position uses that position's largest cell. Calls about an entity use
its eye position.

- **Rationale:** Decision: D-44. A position can hold several cells, so a point
  must resolve to one.
- **Verify:** Unit tests and a GameTest resolve points in each sub-cell of a
  pane, inside a block's shape, at a block position and at an entity's eyes.

*2026-11-01: Implemented
([#108](https://github.com/west3436/zas-cubed/issues/108)). Each voxel of a
block's 16³ rasterization maps to its cell, or to the nearest cell if the voxel
is inside the shape, so a point resolves to its block's sub-cell. A point inside
a block with no cells (a full block) uses the nearest cell in an adjacent
block, by the distance to their shared face; with none, it reads as the
ambient, since it holds no gas. The published table answers per cell from
its membership palettes, and mutations at a point act on the cell it resolves
to. Checked by the `api_015_` Unit tests and
GameTests.*

#### API-011 · Zone query
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-43

Given a zone id or a point, the API shall return the zone's:
- volume and cell count
- mixture snapshot
- edge summary
- whether it is enclosed or breached
- whether it is paused

- **Rationale:** Decision: D-12 and D-43.
- **Verify:** A GameTest queries a zone by id and by point, including a
  breached and a paused zone, and checks every field.

#### API-012 · Gas mutation
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: CORE-036, CORE-053, CORE-057

The API shall allow adding or removing a gas mixture at a point, with a source
attribution.

- **Rationale:** Inferred: this is the basic write operation mods need, and
  the attribution feeds the invariant checker (CORE-073) and logs.
- **Verify:** A GameTest adds and removes gas at zoned and open points and
  checks the amounts and attribution.

#### API-016 · Asynchronous mutation results
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-46, D-30, CORE-036, CORE-040

A mutation shall return a future that completes on the server thread once the
atmos tick that applies it has been applied. Its result gives the amounts
actually moved and the outcome: zone, ambient, or paused (voided, or supplied
without depletion, per GEO-036 and GEO-038). Added gas leaves the caller at
call time; removed gas is delivered when the future completes.

- **Rationale:** Decision: D-46.
- **Verify:** A GameTest checks the future completes after the applying tick,
  with the right amounts and outcome for a zone, the ambient and a paused
  zone.

#### API-013 · Heat mutation
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: API-016, HEAT-010

The API shall allow adding or removing heat at a point. The energy actually
applied is delivered as in API-016.

- **Rationale:** Inferred: heaters and other mods' machines need to heat
  zones, and heat follows API-016 for the same reasons as gas (D-46).
- **Verify:** A GameTest adds and removes heat in a zone and in open air and
  checks the energy applied.

*2026-11-02: Implemented ([#40](https://github.com/west3436/zas-cubed/issues/40)).
`HeatMutations.add` and `remove(level, point, joules, source)` return a future
of `HeatResult`: the outcome (`ZONE`, `AMBIENT`, `PAUSED`, `ABSENT`), the
joules moved and the temperature after. Added heat leaves the caller at the
call; a removal stops at the temperature floor and delivers what it got. A
call at an enclosed, unzoned point forms a zone (CORE-055). Heat isn't saved:
a call still queued at shutdown completes `PAUSED` with nothing moved. The
provider's new methods have defaults that give `ABSENT`. Checked by
`api_013_heat_mutation` and `heat_010_heat_at_open_air` (GameTest), the
`api_013_` and `heat_010_` Unit tests, and the example addon's
`s_12_mutations_complete_absent` without ZAS³.*

#### API-014 · Breathability helper
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-06

The API shall answer "can this entity breathe here, and is it protected?",
taking into account LIFE rules and every life-support provider. This is the
single call that space-mod compat layers route their own checks through.

- **Rationale:** Decision: D-12 and D-51. Space-mod compat routes its checks
  through this one call (S-06).
- **Verify:** GameTests check the answer in air, in vacuum, with internals and
  with a suit.

*2026-11-01: The API and its fallback shipped
([#59](https://github.com/west3436/zas-cubed/issues/59)); the verdict itself
waits for LIFE ([#34](https://github.com/west3436/zas-cubed/issues/34) to
[#36](https://github.com/west3436/zas-cubed/issues/36)) and API-022
([#60](https://github.com/west3436/zas-cubed/issues/60)), so this stays
Accepted. `Breathing.of(entity)` returns an immutable `Breathability`: whether
the entity can breathe and why, the breath source and gas, the hazards its
protection doesn't cover, and what protects it. It is called on the main thread
of the entity's logical side. Without ZAS³ the answer is vanilla's verdict
(API-009): in air the entity breathes, and with its eyes in water it drowns
unless vanilla lets it breathe there. Until LIFE lands, ZAS³ gives the same
vanilla verdict, even in a vacuum zone. Checked by the `api_014_` Unit tests
and GameTests, with and without ZAS³.*

*2026-11-01: the owner kept both open points as shipped. The call stays
`@LevelThread`, since it reads live entity state. The `Hazard` values
(`TOXIC_GAS`, `LOW_PRESSURE`, `HIGH_PRESSURE`, `COLD`, `HEAT`) and the
`Protection` values (`PRESSURE`, `THERMAL`, `SEALED_BREATHING`,
`GAS_FILTERING`, `IMMUNE`, `EXEMPT`) stay as they are. LIFE's work
([#34](https://github.com/west3436/zas-cubed/issues/34) to
[#36](https://github.com/west3436/zas-cubed/issues/36)) confirms or trims each
before the API freezes at 1.0.0. After that, values can be added but not
removed.*

*2026-11-02: On the server, ZAS³ now answers with LIFE's verdict
([#34](https://github.com/west3436/zas-cubed/issues/34)): who breathes, the
breath at the eyes, the O₂ minimum and gas hazards, vanilla's while the eyes
are in a fluid, and `EXEMPT` for an invulnerable player. The client still gets
vanilla's verdict until LIFE-023. Internals and suits wait for
[#36](https://github.com/west3436/zas-cubed/issues/36) and API-022, so this
stays Accepted. `Breathing.oxygenMinimum()` is added, any thread: the
configured O₂ minimum, 16 kPa without ZAS³, so a consumer can judge a point's
air from `Atmospheres.at`. The `api_014_` GameTests check air, vacuum,
underwater, a harmful gas, an exempt player, an untagged mob and the minimum.*

*2026-11-02: On the client, ZAS³ now answers for the local player with the
verdict the server last synced (LIFE-023), without the breath gas, while its
eyes are where the server last checked them; for other entities, and before
the first sync, with vanilla's. Still Accepted, for internals and suits.*

*2026-11-05: Implemented. Internals and suits exist now, and the GameTests the
Verify names pass: `api_014_breathes_the_open_air` in air,
`api_014_vacuum_zone_is_low_oxygen` in a vacuum, `s_08_breathing_gear` on internals,
and `life_020_suited_and_helmetless_players_in_vacuum` and
`life_025_partial_suits_at_450_k` in a suit. Nothing reports `GAS_FILTERING` until
the gas mask (CONT-088, under API-022). The `Hazard` and `Protection` values still
get confirmed or trimmed before the API freezes at 1.0.0.*

*2026-11-05: A breather with a filter (API-022) now reports `GAS_FILTERING`, and
its verdict's breath is without the `#zas:toxic` gases. The gas mask isn't built
yet, so only a mod's item, an entity or a data file gives one.*

#### API-019 · Standalone mixture reactions
`Must` · `R1` · `Implemented` · Verify: Unit · Trace: D-57, D-50, FIRE-001, API-020

The API shall offer a call that applies reactions (FIRE-001, API-020) and
combustion (FIRE-031) to a standalone mixture of a given volume over a given
time, with or without an ignition source, and returns the result. It touches no
world state and may be called from any thread.

- **Rationale:** Decision: D-57 and D-50.
- **Verify:** Unit tests react a test mixture at several volumes and times,
  compare it with the same reaction in a zone, and call it from a worker
  thread.

*2026-11-03: Implemented ([#41](https://github.com/west3436/zas-cubed/issues/41)).
`Reactions.applyStandalone(mixture, seconds, ignition)` burns the mixture's
ignition reactions in a 375 L-per-second parcel (FIRE-031), then runs its zone
reactions for the time, and returns a new mixture; a gas the server doesn't
define is carried through and only absorbs heat. Without ZAS³ it returns the
mixture unchanged. Checked by the `api_019_` tests in `StandaloneReactionsTest`
(zas), `ZoneReactionsTest` (engine, against a zone) and `ReactionsTest`
(zas-api).*

#### API-017 · Zone pins
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: CORE-052

The API shall let a mod pin and unpin a zone under an owner id. A pinned zone
is never dissolved (CORE-052).

- **Rationale:** Inferred: devices and other mods' structures need zones that
  survive equalizing with the ambient (CORE-052).
- **Verify:** A GameTest checks a pinned zone isn't dissolved and dissolves
  once unpinned.

*2026-11-02: Implemented ([#59](https://github.com/west3436/zas-cubed/issues/59)).
`ZonePins.pin(level, point, owner)` pins the zone at a point (API-050) and
`ZonePins.unpin(level, owner)` takes that owner's pins off every zone it pins;
owners are `SourceId`s, so a mod gives each thing that holds a pin its own id.
A zone stays pinned while any owner pins it, and pinning again under the same
owner changes nothing. Pins follow D-64: a merged zone takes both zones'
owners and every part of a split keeps them, which is why unpinning goes by
owner rather than by zone. Zones stored in unloaded chunks lose an unpinned
owner's pin when they load (API-018's note). Both calls are server-thread
only, queue for the next atmos tick and complete when it is applied. They are
provider methods with defaults, and the fallback pins nothing (`ABSENT`). The
`api_017_` Unit tests and the `PinTests` GameTest check that a pinned room
opened to the open air isn't dissolved while its unpinned twin is, that it
dissolves once its last owner unpins, and per-owner pins, merges and splits.*

#### API-018 · Pins persist
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: CORE-052, S-09

Zone pins shall be saved and restored with their zone.

- **Rationale:** Inferred: a pin lost on reload would dissolve zones their
  owner still needs (S-09).
- **Verify:** A GameTest saves and reloads a pinned zone and checks the pin
  and owner remain.

*2026-10-31: D-64: a pin also keeps its zone from dissolving into the ambient
(CORE-076), so losing it on reload would dissolve a zone with an ambient edge.
The R0 chunk record (format version 2) already saves whether each zone is
pinned; the owner ids remain to be added with API-017.*

*2026-11-02: Implemented. The chunk record's format version 4 saves each
zone's pin owners with its share; a pin saved by versions 2 and 3, which had
no owners, loads under the owner `zas:pinned`. An owner that unpins is kept in
the dimension's `zas_atmos` saved data until it pins again, and its pins are
taken off every chunk record that loads, so a zone that was out of memory at
the unpin, or saved at shutdown before a queued unpin was applied, comes back
without it. `api_018_pins_survive_a_restart` restarts the Nether's atmosphere
with two pinned rooms, one owner's unpin still queued, and checks the zones,
their gas and the remaining owners. Unit tests cover the byte format and
unloading and loading a pinned zone.*

#### API-050 · Pinning a point forms a zone
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-68, D-13 · Depends: API-016, API-017

The API shall let a mod pin the zone at a point under an owner id. If the
point's region is enclosed and has no zone, a zone forms there holding its
cells' ambient gas, with no gas added or removed. The result, delivered as in
API-016, gives the zone, or says the region is open or unresolved.

- **Rationale:** Decision: D-68. The area air controller needs a zone for its
  room before any gas is added (CONT-110), and a zone that holds only ambient
  air must be pinned to last (CORE-052).
- **Verify:** A GameTest pins a point in a sealed, unzoned room and checks a
  pinned zone forms holding the ambient's gas, and that pinning a point in the
  open forms nothing and says so.

*2026-11-01: Accepted by the owner as written.*

*2026-11-02: Implemented ([#59](https://github.com/west3436/zas-cubed/issues/59)).
`ZonePins.pin` returns a `PinResult`: `ZONE` with the zone, `OPEN` (the point
is in the open, or inside a block with no cell next to it), `UNRESOLVED` (the
region reaches an unloaded chunk, or the server stopped first) or `ABSENT`
(no ZAS³). The point resolves as for mutations (API-015). A pin forms a zone
under either preset and whatever the API-call trigger option says (D-68). It
is applied before the atmos tick's undo point, like the zones classification
forms, so a stale component (CORE-070) never undoes it. The
`api_050_pin_forms_a_zone_and_open_air_forms_none` GameTest pins a sealed,
unzoned Overworld room and checks a pinned 27-cell zone forms holding exactly
the ambient's gas, and that a room with a hole in its roof forms nothing and
reports `OPEN`. The `api_050_` Unit tests also cover a vacuum room, a pin that
waits for its section and a rejected tick.*

#### API-051 · Gas heat capacities
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: CORE-003, CONT-008, CONT-095 · Depends: API-009

The API shall give each gas's molar heat capacity from the server's gas
definitions, and a documented default for a gas the server doesn't define.

- **Rationale:** Inferred: the content mod may use only the API (CONT-095),
  and it can't mix pipe gas at different temperatures conserving energy
  (CONT-008, CORE-003) without heat capacities
  ([#157](https://github.com/west3436/zas-cubed/issues/157)).
- **Verify:** Unit tests check the fallback's values and that a parcel's heat
  capacity and an energy-conserving mix follow from them; a GameTest checks
  the API's value for every loaded gas against its definition.

*2026-11-02: Implemented ([#157](https://github.com/west3436/zas-cubed/issues/157)).
`GasProperties.molarHeatCapacity(gas)` gives C<sub>v</sub> in J/(mol·K), or
20.8 (about (5/2)R) for an undefined gas; `heatCapacity`, `mix` and
`mixedTemperature` build on it. Without ZAS³, N₂ and O₂ have their shipped
values (API-009). Checked by `GasPropertiesTest` (Unit) and
`api_051_heat_capacities_come_from_the_gas_definitions` (GameTest).*

*2026-11-03: accepted by the owner in the S-02/S-05/S-08 round's review,
20.8 J/(mol·K) default included.*

#### API-052 · Atmos period
`Should` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: CORE-041 · Depends: API-009

The API shall give the atmos tick period in game ticks, 20 without ZAS³.

- **Rationale:** Inferred: the period is configurable (CORE-041), and a mod
  that steps devices in time with the atmosphere needs to know it
  ([#157](https://github.com/west3436/zas-cubed/issues/157)).
- **Verify:** A Unit test checks the fallback's 20, and a GameTest checks the
  API gives the server's configured period.

*2026-11-02: Implemented ([#157](https://github.com/west3436/zas-cubed/issues/157)).
`Atmospheres.periodTicks()`. Checked by
`api_052_fallback_period_is_20_ticks` (Unit) and
`api_052_period_is_the_servers` (GameTest).*

*2026-11-03: accepted by the owner in the S-02/S-05/S-08 round's review.
`zas_atmos`'s devices don't read it yet: they still step every 20 game
ticks.*

#### API-059 · Whether container explosions break blocks
`Should` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: CONT-107, OPS-007, CONT-095 · Depends: API-009

The API shall say whether explosions of gas containers break blocks: the
preset's `container_explosions_break_blocks`, with any override, or false
without ZAS³.

- **Rationale:** Inferred: the content mod may use only the API (CONT-095), and
  it can't follow the preset (CONT-107) without reading this option.
- **Verify:** A Unit test checks the fallback's false; a GameTest checks the API
  follows each preset and an override under either.

*2026-11-04: added for review, with the behaviour built.
`Atmospheres.containerExplosionsBreakBlocks()`. Checked by
`api_059_container_explosions_break_no_blocks_without_zas` (Unit),
`api_059_container_explosions_follow_the_preset` (GameTest) and
`s_12_container_explosions_break_no_blocks` (GameTest, without ZAS³).*

*2026-11-05: accepted by the owner, and Implemented: its tests pass.*

#### API-060 · Thin-air factor
`Should` · `R1` · `Implemented` · Verify: Unit, Inspect · Trace: CLIENT-016, CONT-095 · Depends: API-009

The API shall give the volume factor for the local player's air, 0 to 1, from any
thread: 1 without ZAS³ and on a dedicated server.

- **Rationale:** Inferred: the content mod may use only the API (CONT-095), and
  its one-shot sounds must fade in thin air as the framework's do.
- **Verify:** A Unit test checks the fallback's 1; Inspect checks the API refers
  to no client-only class, so a server can load it.

*2026-11-05: added for review, with the behaviour built.
`ClientSounds.thinAir()`, with `api_060_a_listener_hears_normally_without_zas`
(Unit).*

*2026-11-05: for review, the API also lets a mod have ZAS³ fade its one-shot
sounds: one call, `ClientSounds.fadeInThinAir(modId)`, from its client entry
point, and `fadesInThinAir` to ask. A sound a server plays can only be faded on
the client that hears it, and `zas` knows nothing of the mods built on it, so
each names its own namespace. With `api_060_a_mod_gives_its_own_namespace`
(Unit).*

*2026-11-05: accepted by the owner, and Implemented: its test passes, and `zas-api`
imports no client-only class.*

#### API-061 · Looping sounds through the API
`Should` · `R1` · `Implemented` · Verify: Unit, Inspect, Manual · Trace: CLIENT-015, CONT-095 · Depends: API-009

The API shall let a mod's client code ask for a looping sound: a source, a sound,
its channel, a position, a volume, a pitch and a range. A request lasts 4 game
ticks unless renewed. Without ZAS³, and on a dedicated server, a request does
nothing.

- **Rationale:** Inferred: the content mod may use only the API (CONT-095), and
  its running devices need loops; one manager keeps the limit of CLIENT-015 for
  every mod.
- **Verify:** Unit tests check the fallback and the request's checks; Inspect
  checks the API refers to no client-only class; Manual checks a loop through
  the API in the combined test world.

*2026-11-05: added for review, with the behaviour built. `ClientSounds.loop`
and `LoopRequest`, with `ClientSounds.install` for `zas`'s client entry point.
`ClientSoundProvider` has a default for each method, as architecture §6.2 asks
of a provider.*

*2026-11-05: A request lasts 4 game ticks rather than 10, so a sound stops sooner
after what it goes with (CLIENT-015). A mod has to ask at least that often, and
every tick is the simple way. `api_061_the_api_states_the_books_lease` (Unit) checks
that `ClientSounds.LEASE_TICKS` is what the manager does.*

*2026-11-05: accepted by the owner, and Implemented: its tests pass, `zas-api` imports
no client-only class, and the loops the owner heard in the sound and particle world
come through it (`DeviceLoops`, `FireAlarmSounds`, `GeneratorBlockEntity`).*

#### API-062 · Limit on logging third-party faults
`Should` · `R1` · `Implemented` · Verify: Unit · Trace: NFR-013, CONT-095

The API shall say whether to log a fault from a named source, answering yes at most
once per source per minute, from any thread, with or without ZAS³.

*2026-11-05: added for review, with the behaviour built: `ThirdPartyFaults.shouldLog`,
tested by `ThirdPartyFaultsTest`. It's in the API because the content mod may use only
the API (CONT-095) and NFR-013 needs one limit for both mods. It only answers yes or no,
and the caller logs, because the API may not refer to a logging library (API-044). Its
`advanceClockForTest` lets a GameTest see the next minute without waiting for it.*

*2026-11-06: accepted by the owner, and Implemented: `ThirdPartyFaultsTest`
(Unit) passes.*

#### API-063 · Display units through the API
`Should` · `R1` · `Draft` · Verify: Unit, Manual · Trace: CLIENT-006, CONT-095

The API shall convert a pressure in kPa and a temperature in K to the units the player
chose, and name those units. Without ZAS³, and on a dedicated server, the answers are in
kPa and °C.

*2026-11-06: added for review, with the behaviour built: `DisplayUnits`, tested by
`DisplayUnitsTest`. It's in the API because the content mod may use only the API
(CONT-095), and CLIENT-006 has its device GUIs follow the player's units.*

#### API-055 · Hotspot queries
`Should` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: FIRE-011, FIRE-019, API-046 · Depends: API-009

The API shall give a zone's hotspots, whether a zone is burning and whether a
block position is a hotspot, from any thread, as of the last applied atmos
tick. Without ZAS³ there are none.

- **Rationale:** Inferred: alarms, sensors and extinguishers need to find
  fires without reading blocks, and a hotspot is the atmosphere's fire, which
  a vanilla fire outside any zone isn't (FIRE-019).
- **Verify:** A Unit test checks the fallback's answers; a GameTest lights two
  fires in one of two rooms and checks the queries for both.

*2026-11-03: implemented for review ([#42](https://github.com/west3436/zas-cubed/issues/42)).
`Fires.hotspots(level, zone)` (positions in ascending `BlockPos.asLong` order,
immutable), `Fires.isBurning(level, zone)` and `Fires.isHotspot(level, pos)`.
Provider methods with defaults; the fallback finds none. Checked by
`api_055_fallback_has_no_fire` (Unit) and `api_055_hotspot_queries` (GameTest).*

*2026-11-03: a new gas fire counts from the atmos tick after the one that
placed it, the first to see its block: when an ignition's future completes,
`isHotspot` is still false at its block, and the `FireIgnitedEvent` comes with
the next tick. The javadoc of `Fires.ignite` and `Fires.isHotspot` says so.*

#### API-056 · Ignition through the API
`Should` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: FIRE-014, API-016, API-045 · Depends: API-009

The API shall let a mod ignite the gas at a block position from the server
thread. The ignition is applied in the next atmos tick and its result,
delivered as in API-016, says whether a fire started or already burned there
(with its hotspot and zone), the zone's gas won't burn, there is no space for
a fire (API-058), the position is in the open, or the region is paused.
Without ZAS³ the result is at once that ZAS³ is absent.

- **Rationale:** Inferred: ignition sources in other mods (FIRE-014), and
  ZAS³'s own content, need one way in that says what happened.
- **Verify:** A Unit test checks the fallback and the result's rules; a
  GameTest ignites in open air, in air, in hydrogen and next to the fire, and
  checks each result.

*2026-11-03: implemented for review ([#42](https://github.com/west3436/zas-cubed/issues/42)).
`Fires.ignite(level, pos, source)` returns a `CompletableFuture<Ignition>`:
an `IgnitionOutcome` (`IGNITED`, `BURNING`, `NOT_FLAMMABLE`, `NO_SPACE`, `OPEN`,
`PAUSED` or `ABSENT`), an `Optional` hotspot present exactly for `IGNITED` and
`BURNING`, and an `Optional` zone. The future completes once the tick's gas
fire block is placed. It is FIRE-014's entry point, the same one that
`AtmosService.ignite` gives ZAS³'s own code. Checked by
`api_056_provider_fire_methods_have_defaults` (Unit) and
`api_056_ignite_through_the_api` (GameTest).*

#### API-057 · Extinguishing
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: CONT-037, FIRE-018, FIRE-019, API-045 · Depends: API-009

The API shall let a mod put out every fire in a box of at most 4,096 blocks,
from the server thread: the gas fires and vanilla fire blocks in it go at once,
and their hotspots with the next atmos tick. The zone's gas is unchanged.

- **Rationale:** Inferred: the fire extinguisher (CONT-037) and other mods'
  suppression need to put fires out, and a fire block removed by hand could
  be placed again by the atmos tick already running.
- **Verify:** A GameTest puts out a gas fire and a vanilla fire in a burning
  room and checks the zone stops burning with its gas kept and an oversized
  box is refused; another sprays a box while the tick that ignites it is in
  flight and checks no gas fire is placed there.

*2026-11-03: implemented for review ([#42](https://github.com/west3436/zas-cubed/issues/42)).
`Fires.extinguish(level, from, to)` returns how many fire blocks it removed,
in loaded chunks; a box over `Fires.MAX_EXTINGUISH_BLOCKS` throws. A gas fire
that the batch in flight when it is called asks for inside the box isn't
placed; the next batch has seen the spray. A zone hot enough lights again by
itself (FIRE-010). The fire extinguisher (CONT-037) calls it. Checked by
`api_057_extinguish_puts_out_fires` and
`api_057_extinguish_stops_a_fire_in_flight`.*

#### API-058 · No space for a fire
`Should` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: FIRE-014, FIRE-018 · Depends: API-056

An ignition in a flammable zone shall report that there is no space when
neither the position nor any neighbour in the zone could hold a fire block,
naming the zone and no hotspot, and change nothing. A position can hold a fire
block when it is plain air with no fire and no gas fire refused there in the
last 30 atmos ticks.

- **Rationale:** Inferred: every hotspot is a block (FIRE-018), so a zone
  whose space is all partial blocks, such as the space over a slab, can't
  catch fire from an ignition, and the caller needs to know why.
- **Verify:** A Unit test checks an ignition names a hotspot only when a fire
  burns; a GameTest ignites a hydrogen-filled space over a bottom slab and
  checks the result and that no block changed.

*2026-11-03: implemented for review ([#42](https://github.com/west3436/zas-cubed/issues/42)).
`IgnitionOutcome.NO_SPACE`. A block that isn't air but has air's
geometry, such as a lever or a flower, isn't plain air: its geometry carries
`FLAG_OCCUPIED` (FIRE-018), so an ignition there lights an air neighbour.
Checked by `api_058_ignition_names_a_hotspot_only_when_burning` (Unit) and
`api_058_no_space` (GameTest).*

## Extension points

#### API-020 · Code-defined reactions
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: FIRE-001

The API shall let mods register reaction types implemented in code, which
datapack reaction definitions can reference. Datapack gases and reactions
themselves are CORE-010 and FIRE-001.

- **Rationale:** Inferred: datapack definitions can't express every reaction,
  so code can register types; ZAS's own reactions are code ([zas-core
  §10](../research/zas-core.md#10-mapping-zas-concepts-to-minecraft-draft-for-requirements-discussion)).
- **Verify:** A GameTest registers a test reaction type, references it from a
  datapack, and checks it runs in a zone.

*2026-11-03: Implemented ([#41](https://github.com/west3436/zas-cubed/issues/41)).
`Reactions.registerType(id, type)` registers a `ReactionType`; a definition
names it in `type` and passes it `parameters` ([datapacks.md](../datapacks.md#reactions)).
The definition's conditions still apply, and the type's change is scaled to
what the zone has (FIRE-002). A type that throws or asks for a gas the server
doesn't define changes nothing and is logged at most once a minute (§3.6).
Checked by `api_020_a_code_reaction_type_runs_in_a_zone` (GameTest) and the
`api_020_` tests in `CodeTypesTest` (zas) and `ReactionSetTest` (engine).*

#### API-021 · Gas handler capability
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-12

Blocks shall be able to expose a gas-handler capability that fills and drains
mixtures, with simulate and execute modes.

- **Rationale:** Decision: D-12. A shared gas capability lets tech mods move
  gas between each other's blocks.
- **Verify:** A GameTest fills and drains a test block in simulate and execute
  modes.

*2026-11-05: Implemented ([#60](https://github.com/west3436/zas-cubed/issues/60)).
`IGasHandler`, next to `ILifeSupport`, with its sided `BLOCK` capability:
`contents`, and `fill` and `drain` that simulate or execute.
`api_021_a_block_fills_and_drains_in_both_modes` runs the plan on a test block, and
`s_12_the_gas_handler_capability_exists_without_zas` checks that asking for it is
safe without ZAS³.*

#### API-025 · Pipes connect to gas handlers
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-12 · Depends: API-021

ZAS³ pipes and devices shall connect to any block that provides the
gas-handler capability.

- **Rationale:** Decision: D-12.
- **Verify:** A GameTest connects a pipe to a test gas handler and checks gas
  moves.

*2026-11-05: Implemented ([#60](https://github.com/west3436/zas-cubed/issues/60)).
A node connects to a neighbour that provides the capability on any face where it
offers a connection, and a pipe grows an arm that counts against an open end.
`api_025_a_pipe_connects_to_a_handler_and_gas_moves` runs the plan,
`api_025_every_pipe_device_connects_to_a_handler` covers caps, connector ports,
vents, scrubbers, passive vents, open valves, pumps, filters and mixers,
`api_025_a_sided_handler_connects_on_its_side` a capability on one face only,
`api_025_an_arm_follows_the_capability` a capability or block that goes and returns,
and `api_025_a_placed_device_lines_up_with_a_handler` placement. A canister connects
only to a connector port (CONT-083), so it reaches a handler through a port, and a
shut valve stays connected and moves no gas:
`api_025_a_canister_and_a_shut_valve_move_no_gas`.*

#### API-026 · Capability transfers on the server thread
`Must` · `R1` · `Implemented` · Verify: Inspect, GameTest · Trace: D-30, CORE-049, CORE-069

Transfers through the gas-handler capability shall run on the server thread
when results are applied, exchanging gas with buffers inside the pipe network.

- **Rationale:** Decision: D-30.
- **Verify:** Inspect the code; a GameTest asserts the thread during a
  transfer.

*2026-11-05: Implemented ([#60](https://github.com/west3436/zas-cubed/issues/60)).
Inspected: `GasHandlers` makes every call into a handler. Its transfers run only from
a node's step, which `DeviceTicker` runs on the server thread, and it refuses any
other thread. They move gas between the handler and the network's store, with no
zone involved. `api_026_transfers_run_on_the_server_thread` asserts the thread of
every call under the game's own ticks, and the refusal.
`api_026_gas_is_conserved_both_ways_with_the_checker_on` holds each gas's total over
a room, a pipe and a handler with CORE-073's checker on. The `api_026_` tests also
cover a handler that refuses, takes part of a transfer, overstates what it took,
throws, removes the pipe or a pump from inside a fill, or is in a chunk that's away.
Nothing catches a handler's exception yet: that's for NFR-013, in `GasHandlers`.*

*2026-11-05: `GasHandlers` now catches it (NFR-013).*

#### API-022 · Life-support capability
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-07, S-06, S-08

Items in armor slots, and entities, shall be able to provide:
- breathable gas from an internal supply
- pressure protection
- thermal protection
- airflow resistance
- sealed breathing (needed for internals)
- gas filtering

- **Rationale:** Decision: D-07 and D-47 (S-06, S-08).
- **Verify:** GameTests give a test item and a test entity each property and
  check LIFE honours it.

*2026-10-30: added sealed breathing and gas filtering during the LIFE review
(LIFE-021, LIFE-026, D-47).*

*2026-11-02: Built in part ([#36](https://github.com/west3436/zas-cubed/issues/36)).
The capability is `ILifeSupport` in `io.github.west3436.zas.api.capability`,
with `ILifeSupport.ITEM` for worn items and `ILifeSupport.ENTITY` for
entities. Every part defaults to giving nothing. The parts are `minPressure`,
`maxPressure`, `minTemperature`, `maxTemperature`, `sealedBreathing`,
`gasFiltering` and `airflowResistance`. Pressure and thermal protection are
honoured (LIFE-020, LIFE-025). Sealed breathing is read
(`WornProtection.sealedBreathing`) for the internals still to come. An
entity's airflow resistance, the highest of its pieces' and its own, can be
read (`WornProtection.airflowResistance`), and airflow applies it (FLOW-012).
Gas filtering is carried for the gas mask but not applied. It stays Accepted
until those are honoured. `life_020_suited_and_helmetless_players_in_vacuum`,
`life_025_partial_suits_at_450_k` and
`life_026_capability_and_data_items_protect` check the test item and test
entity, and `api_022_airflow_resistance_is_the_highest_worn` checks the
resistance.*

*2026-11-02: Breathable gas from an internal supply is honoured
([#36](https://github.com/west3436/zas-cubed/issues/36)): `internalSupply()`
returns an `IInternalSupply` (its contents, its distribution pressure and a
draw), which internals breathe from (LIFE-021, LIFE-028). Sealed breathing now
gates internals. `life_028_the_first_tank_found` checks a test tank item and a
test entity's own tank. Gas filtering (CONT-088) and airflow resistance
(FLOW-012) are still to be applied, so it stays Accepted.*

*2026-11-05: Implemented ([#60](https://github.com/west3436/zas-cubed/issues/60)).
FLOW-012 had already applied airflow resistance, so what was left was gas
filtering, which LIFE now honours: a filter in the head slot, or an entity's own,
takes the `#zas:toxic` gases out of the breath, from the atmosphere or from
internals, and the verdict lists `GAS_FILTERING`. It supplies no O₂, and nothing
leaves the zone or stays in the filter, so it never wears out. The gas mask itself
(CONT-088) is still to come. The plan's test item and test entity, part by part:*

| Part | Test item | Test entity |
|---|---|---|
| Internal supply | `life_028_the_first_tank_found` | `api_022_a_test_entity_gets_every_part` |
| Pressure | `life_020_suited_and_helmetless_players_in_vacuum` | `api_022_a_test_entity_gets_every_part` |
| Thermal | `life_025_partial_suits_at_450_k` | `api_022_a_test_entity_gets_every_part` |
| Airflow resistance | `flow_012_resistance_scales_the_push` | `flow_012_resistance_scales_the_push` |
| Sealed breathing | `life_021_internals_need_a_seal` | `api_022_a_test_entity_gets_every_part` |
| Gas filtering | `api_022_a_filter_keeps_toxic_gases_out_of_the_breath` | `api_022_a_test_entity_gets_every_part` |

*`api_022_a_filter_supplies_no_oxygen` checks a filter leaves its wearer short of
O₂ without O₂ around, `api_022_a_filter_works_on_a_tanks_breath` that it cleans a
tank's breath while the tank's gas goes on into the room, and
`client_008_a_filter_hides_the_gas_it_stops` that the HUD shows no alert for a
gas it stops.*

#### API-027 · Accessory slots
`Could` · `R2` · `Deferred` — moved to R2: an accessory mod is an optional dependency outside the API (API-003), 2026-10-30. · Verify: GameTest

Items in accessory slots shall be able to provide life support, through an
optional compat module.

#### API-023 · Seal provider
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-04, GEO-003, GEO-007, GEO-043

Blocks shall be able to supply their geometry dynamically: gas-tight faces,
zone-boundary faces and gas volume. Supplied geometry is a single cell, as with
GEO-005 overrides. Changes are signalled with `markDirty` (GEO-014).

- **Rationale:** Decision: D-04. Other mods' dynamic blocks, such as airlocks,
  must report their geometry.
- **Verify:** A GameTest switches a test block between sealed and open through
  its provider and markDirty, and checks the zones follow.

*2026-11-05: Implemented. `api_023_zones_follow_the_provider` passes, with the
other `api_023_` GameTests in `SealProviderTests` for faces, volume, zone-boundary
faces and an override of the block, and `PositionGeometryTest` takes a position's
own geometry through every engine phase.*

#### API-028 · Seal providers read on the server thread
`Must` · `R1` · `Implemented` · Verify: Inspect, GameTest · Trace: D-30, CORE-049 · Depends: API-023

Seal providers shall be called on the server thread when snapshots are taken,
and their result cached until `markDirty` (GEO-014).

- **Rationale:** Decision: D-30.
- **Verify:** Inspect the code; a GameTest checks the provider runs on the
  server thread and isn't called again until markDirty.

*2026-11-05: Implemented. Inspected: `SealProviders.ask` is the only call of a
provider, reached only from `SectionCapture.capture`, which runs on the server
thread. `api_028_provider_is_kept_until_mark_dirty` passes: the provider runs on
the server thread and isn't asked again while its section is copied again, until
markDirty or a write of the block.*

#### API-024 · Ambient provider
`Must` · `R1` · `Rejected` — duplicate of ENV-004, 2026-10-30.

#### API-053 · Ignition source tag
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: FIRE-014, FIRE-015, CONT-105

Blocks in the block tag `#zas:ignition_sources` shall be ignition sources
(FIRE-014) while they are there. A block with a boolean `lit` property shall
count only while it is lit.

- **Rationale:** Inferred: a mod's block, such as the igniter (CONT-105), can
  then ignite gas without calling ZAS³ at all, which also works without ZAS³
  installed.
- **Verify:** A GameTest checks a tagged block ignites a flammable zone, and
  one with a `lit` property only while lit.

## Events

#### API-032 · Events on the server thread
`Must` · `R1` · `Implemented` · Verify: Inspect, GameTest · Trace: D-30, CORE-069

API events shall fire on the server thread, when the results that cause them
are applied.

- **Rationale:** Decision: D-30.
- **Verify:** Inspect the code; GameTest listeners assert the thread.

*2026-11-06: Implemented, for the three events that exist. Read the code:
`ApiEvents` is the only place ZAS³ posts an event, and its only callers are
`DimensionAtmos.applyBatch` and `completeInOpenAir`, which only `applyBatch` calls,
while a batch's results are applied. Listeners assert the thread in
`api_031_fire_events_fire_on_ignition_and_going_out` (`FireIgnitedEvent` and
`FireExtinguishedEvent`) and in `core_053_open_air_add_goes_to_ambient` and
`core_057_open_air_remove_yields_ambient_gas` (`OpenAirGasEvent`). API-030's and
API-033's events, when they're built, must post through `ApiEvents` and have
listeners that assert the thread too.*

*2026-11-06: `HazardTickEvent` (API-033) posts through `ApiEvents` too, from `LifeDamage`,
on the server thread just before the hazard it names is dealt: that's when its result is
applied, and it isn't tied to an atmos tick. A listener asserts the thread in
`api_033_a_cancelled_pressure_or_temperature_tick_deals_no_damage`.*

#### API-030 · Zone lifecycle events
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-43

The API shall raise events when a zone is created, merged, split, breached
(gains an ambient edge), sealed, dissolved, paused or resumed.

- **Rationale:** Inferred: other mods (alarms, quests, compat) need to react
  to zones changing; D-43 added pause and resume.
- **Verify:** GameTests trigger each lifecycle change and check the event
  fires once.

#### API-031 · Fire events
`Should` · `R1` · `Implemented` · Verify: GameTest

The API shall raise events on fire ignition and extinguishing.

- **Rationale:** Inferred: mods such as alarms need to react to fires starting
  and stopping.
- **Verify:** A GameTest lights and starves a fire and checks both events
  fire.

*2026-11-03: Implemented ([#42](https://github.com/west3436/zas-cubed/issues/42)).
`FireIgnitedEvent(level, zone, hotspot)` when a zone's first hotspot appears,
and `FireExtinguishedEvent(level, zone, lastHotspot)` when its last one goes,
each once per zone, on `NeoForge.EVENT_BUS` on the server thread when the atmos
tick is applied, after its gas fire blocks are placed or removed. A new gas
fire's zone catches fire the tick after the ignition, when the tick sees the
block. Events go by zone id, so a fire that a merge or split carries into
another zone goes out in the old one and catches in the new one. The
`api_031_fire_events_fire_on_ignition_and_going_out` GameTest lights a
hydrogen room, empties it, and checks each event fires once, on the server
thread, with its block already changed.*

#### API-033 · Hazard tick events
`Should` · `R1` · `Implemented` · Verify: GameTest

The API shall raise an event on each entity hazard tick, which can be
cancelled or modified.

- **Rationale:** Inferred: compat mods need to adjust or cancel ZAS³'s
  hazards, for example for their own suits (D-12).
- **Verify:** GameTests check a cancelled hazard tick deals no damage and a
  modified one deals the changed amount.

*2026-11-05: When this is built, post the event through `ApiEvents`, so a listener
that throws is isolated (NFR-013).*

*2026-11-06: Implemented ([#59](https://github.com/west3436/zas-cubed/issues/59)).
`HazardTickEvent(entity, hazard, damage)` fires through `ApiEvents` just before each
hazard harms an entity, one event per hazard each time: at each check for toxic gas,
pressure and temperature, each second for hypoxia, and each blow for airflow impact,
which FLOW-005 deals through the same call (`LifeDamage.deal`). `Hazard` gains `HYPOXIA`
and `AIRFLOW_IMPACT` to name them; a verdict never lists either. The damage is after
protection and scale, and `setDamage` changes it. Cancelling deals none and, for toxic
gas, applies no status effects, but exposures go on counting, the verdict still lists the
hazard and a hypoxic meter stays empty. Tests:
`api_033_a_cancelled_pressure_or_temperature_tick_deals_no_damage`,
`api_033_a_modified_pressure_or_temperature_tick_deals_the_changed_amount`,
`api_033_toxic_gas_ticks_cancel_and_modify`, `api_033_hypoxia_ticks_cancel_and_modify`,
`api_033_airflow_impact_ticks_cancel_and_modify`,
`api_033_listeners_share_the_tick_in_priority_order` and
`nfr_013_a_hazard_tick_listener_that_throws_is_skipped_and_logged_once_a_minute`. The
airflow test calls `LifeDamage.deal` itself, so FLOW-005 checks its own use of it.*
