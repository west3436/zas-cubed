# 30 · Public API (`API`)

**Status:** Accepted 2026-10-30 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules). API-019 was added and accepted on
2026-10-31 (D-57).

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
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: D-17

ZAS³ shall bundle the API module as a jar-in-jar, so that ZAS³ and consumer
mods that bundle it share one copy.

- **Rationale:** Decision: D-17.
- **Verify:** Inspect the jar; a GameTest loads ZAS³ with a consumer that
  bundles the API and checks only one copy is loaded.

#### API-008 · Maven Central publishing
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-18

The API shall be published to Maven Central under `io.github.west3436`, with
sources and javadoc jars.

- **Rationale:** Decision: D-18.
- **Verify:** Inspect the published artifact for sources and javadoc jars.

#### API-006 · Works without ZAS³
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-17, S-12

A consumer mod that bundles the API shall run without ZAS³ installed, and
nothing crashes.

- **Rationale:** Decision: D-17 (S-12).
- **Verify:** A GameTest runs the example addon without ZAS³ and checks the
  S-12 scenario completes without errors.

#### API-009 · Safe defaults without ZAS³
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-17, S-12

Without ZAS³, every API call shall return safe defaults:
- ambient queries return standard air (ENV-011)
- mutations are no-ops reported as "absent"
- the breathability helper (API-014) follows vanilla rules, so being
  underwater still counts

- **Rationale:** Decision: D-17.
- **Verify:** A GameTest without ZAS³ checks each kind of call returns its
  listed default.

#### API-040 · Presence check
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-17

The API shall let consumers check whether a real implementation is present.

- **Rationale:** Inferred: consumers need to turn atmosphere features (HUD,
  gear) on or off depending on whether the atmosphere is real.
- **Verify:** GameTests check the answer with and without ZAS³ installed.

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

#### API-042 · Deprecation before removal
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-04

Anything removed from the API shall first be deprecated for at least one minor
version.

- **Rationale:** Inferred: the usual library convention under D-04, giving
  consumers one minor version to migrate.
- **Verify:** Inspect: the CI API diff (API-043) flags any removal that wasn't
  deprecated in an earlier minor version.

#### API-043 · API-diff check in CI
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-04

CI shall check API compatibility automatically against the last release.

- **Rationale:** Inferred: manual review misses accidental breaks, and D-04
  promises stability.
- **Verify:** Inspect: the CI job runs on every build and fails on a
  deliberate test break.

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
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-04

Every public API type and member shall have javadoc.

- **Rationale:** Decision: D-04.
- **Verify:** Inspect: CI's javadoc lint treats a missing comment as an error.

#### API-047 · Example addon
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-04

The repository shall include an example addon that uses each extension point.

- **Rationale:** Decision: D-04. Working examples are documentation consumers
  can copy.
- **Verify:** Inspect the example addon against the list of extension points.

#### API-048 · Example addon without ZAS³ in CI
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-12, D-17

CI shall run the example addon without ZAS³ installed, as the S-12 test.

- **Rationale:** Decision: D-17 (S-12).
- **Verify:** Inspect the CI configuration; the job itself is the S-12
  GameTest.

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

#### API-015 · Point resolution
`Must` · `R0` · `Accepted` · Verify: Unit, GameTest · Trace: D-44

A call at an exact point (`Vec3`) shall use the cell that contains it, or the
nearest cell if the point is inside the block's collision shape. A call at a
block position uses that position's largest cell. Calls about an entity use
its eye position.

- **Rationale:** Decision: D-44. A position can hold several cells, so a point
  must resolve to one.
- **Verify:** Unit tests and a GameTest resolve points in each sub-cell of a
  pane, inside a block's shape, at a block position and at an entity's eyes.

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
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: API-016, HEAT-010

The API shall allow adding or removing heat at a point. The energy actually
applied is delivered as in API-016.

- **Rationale:** Inferred: heaters and other mods' machines need to heat
  zones, and heat follows API-016 for the same reasons as gas (D-46).
- **Verify:** A GameTest adds and removes heat in a zone and in open air and
  checks the energy applied.

#### API-014 · Breathability helper
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-06

The API shall answer "can this entity breathe here, and is it protected?",
taking into account LIFE rules and every life-support provider. This is the
single call that space-mod compat layers route their own checks through.

- **Rationale:** Decision: D-12 and D-51. Space-mod compat routes its checks
  through this one call (S-06).
- **Verify:** GameTests check the answer in air, in vacuum, with internals and
  with a suit.

#### API-019 · Standalone mixture reactions
`Must` · `R1` · `Accepted` · Verify: Unit · Trace: D-57, D-50, FIRE-001, API-020

The API shall offer a call that applies reactions (FIRE-001, API-020) and
combustion (FIRE-031) to a standalone mixture of a given volume over a given
time, with or without an ignition source, and returns the result. It touches no
world state and may be called from any thread.

- **Rationale:** Decision: D-57 and D-50.
- **Verify:** Unit tests react a test mixture at several volumes and times,
  compare it with the same reaction in a zone, and call it from a worker
  thread.

#### API-017 · Zone pins
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: CORE-052

The API shall let a mod pin and unpin a zone under an owner id. A pinned zone
is never dissolved (CORE-052).

- **Rationale:** Inferred: devices and other mods' structures need zones that
  survive equalizing with the ambient (CORE-052).
- **Verify:** A GameTest checks a pinned zone isn't dissolved and dissolves
  once unpinned.

#### API-018 · Pins persist
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: CORE-052, S-09

Zone pins shall be saved and restored with their zone.

- **Rationale:** Inferred: a pin lost on reload would dissolve zones their
  owner still needs (S-09).
- **Verify:** A GameTest saves and reloads a pinned zone and checks the pin
  and owner remain.

## Extension points

#### API-020 · Code-defined reactions
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: FIRE-001

The API shall let mods register reaction types implemented in code, which
datapack reaction definitions can reference. Datapack gases and reactions
themselves are CORE-010 and FIRE-001.

- **Rationale:** Inferred: datapack definitions can't express every reaction,
  so code can register types; ZAS's own reactions are code ([zas-core
  §10](../research/zas-core.md#10-mapping-zas-concepts-to-minecraft-draft-for-requirements-discussion)).
- **Verify:** A GameTest registers a test reaction type, references it from a
  datapack, and checks it runs in a zone.

#### API-021 · Gas handler capability
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-12

Blocks shall be able to expose a gas-handler capability that fills and drains
mixtures, with simulate and execute modes.

- **Rationale:** Decision: D-12. A shared gas capability lets tech mods move
  gas between each other's blocks.
- **Verify:** A GameTest fills and drains a test block in simulate and execute
  modes.

#### API-025 · Pipes connect to gas handlers
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-12 · Depends: API-021

ZAS³ pipes and devices shall connect to any block that provides the
gas-handler capability.

- **Rationale:** Decision: D-12.
- **Verify:** A GameTest connects a pipe to a test gas handler and checks gas
  moves.

#### API-026 · Capability transfers on the server thread
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: D-30, CORE-049, CORE-069

Transfers through the gas-handler capability shall run on the server thread
when results are applied, exchanging gas with buffers inside the pipe network.

- **Rationale:** Decision: D-30.
- **Verify:** Inspect the code; a GameTest asserts the thread during a
  transfer.

#### API-022 · Life-support capability
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-07, S-06, S-08

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

#### API-027 · Accessory slots
`Could` · `R2` · `Deferred` — moved to R2: an accessory mod is an optional dependency outside the API (API-003), 2026-10-30. · Verify: GameTest

Items in accessory slots shall be able to provide life support, through an
optional compat module.

#### API-023 · Seal provider
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-04, GEO-003, GEO-007, GEO-043

Blocks shall be able to supply their geometry dynamically: gas-tight faces,
zone-boundary faces and gas volume. Supplied geometry is a single cell, as with
GEO-005 overrides. Changes are signalled with `markDirty` (GEO-014).

- **Rationale:** Decision: D-04. Other mods' dynamic blocks, such as airlocks,
  must report their geometry.
- **Verify:** A GameTest switches a test block between sealed and open through
  its provider and markDirty, and checks the zones follow.

#### API-028 · Seal providers read on the server thread
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: D-30, CORE-049 · Depends: API-023

Seal providers shall be called on the server thread when snapshots are taken,
and their result cached until `markDirty` (GEO-014).

- **Rationale:** Decision: D-30.
- **Verify:** Inspect the code; a GameTest checks the provider runs on the
  server thread and isn't called again until markDirty.

#### API-024 · Ambient provider
`Must` · `R1` · `Rejected` — duplicate of ENV-004, 2026-10-30.

## Events

#### API-032 · Events on the server thread
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: D-30, CORE-069

API events shall fire on the server thread, when the results that cause them
are applied.

- **Rationale:** Decision: D-30.
- **Verify:** Inspect the code; GameTest listeners assert the thread.

#### API-030 · Zone lifecycle events
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-43

The API shall raise events when a zone is created, merged, split, breached
(gains an ambient edge), sealed, dissolved, paused or resumed.

- **Rationale:** Inferred: other mods (alarms, quests, compat) need to react
  to zones changing; D-43 added pause and resume.
- **Verify:** GameTests trigger each lifecycle change and check the event
  fires once.

#### API-031 · Fire events
`Should` · `R1` · `Accepted` · Verify: GameTest

The API shall raise events on fire ignition and extinguishing.

- **Rationale:** Inferred: mods such as alarms need to react to fires starting
  and stopping.
- **Verify:** A GameTest lights and starves a fire and checks both events
  fire.

#### API-033 · Hazard tick events
`Should` · `R1` · `Accepted` · Verify: GameTest

The API shall raise an event on each entity hazard tick, which can be
cancelled or modified.

- **Rationale:** Inferred: compat mods need to adjust or cancel ZAS³'s
  hazards, for example for their own suits (D-12).
- **Verify:** GameTests check a cancelled hazard tick deals no damage and a
  modified one deals the changed amount.
