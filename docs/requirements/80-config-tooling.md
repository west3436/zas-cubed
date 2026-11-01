# 80 · Configuration and tooling (`OPS`)

**Status:** Accepted 2026-10-31 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules).

**Scope.** Config files, datapack formats, commands, profiling and admin
controls. Background:
[neoforge §12](../research/neoforge-1.21.1.md#12-config).

## Config and data

#### OPS-001 · Config tiers
`Must` · `R0` · `Implemented` · Verify: Inspect

There shall be three config tiers:
- **Server (per world):** simulation tuning (tick period, budgets, sharing
  table, thresholds), the preset (OPS-007) and hazard toggles.
- **Common:** shared settings, such as thread counts.
- **Client:** visuals and units.

- **Rationale:** Inferred: NeoForge's server, common and client configs
  ([neoforge §12](../research/neoforge-1.21.1.md#12-config)) match who owns
  each setting.
- **Verify:** Inspect the config files.

#### OPS-016 · Live config changes
`Should` · `R1` · `Accepted` · Verify: GameTest

Server config changes shall apply from the next atmos tick without a restart,
except settings marked restart-only, such as the thread count.

- **Rationale:** Inferred: tuning a running server shouldn't need a restart.
- **Verify:** A GameTest changes a server setting and checks it applies on the
  next atmos tick.

#### OPS-002 · Datapack formats
`Must` · `R0` · `Accepted` · Verify: Inspect · Trace: D-04

The following shall be data-driven, with documented JSON formats:
- gases
- reactions and reaction types
- ambients, with their preset variants (ENV-010)
- block seal and volume overrides
- thermal properties: transmittances (HEAT-002) and heat sources (HEAT-016)
- fuel profiles (FIRE-025)
- protection data (LIFE-026)
- hazard profiles
- entity tags: breathers, atmosphere-immune and airflow
- the pressure-fragile block tag

- **Rationale:** Decision: D-04. Pack makers can tune everything without code.
- **Verify:** Inspect the documented formats against the loaders.

#### OPS-008 · Datapack validation
`Must` · `R0` · `Implemented` · Verify: GameTest · Depends: OPS-002

An invalid datapack entry shall log an error naming the file and field, and be
skipped. It never crashes the game.

- **Rationale:** Inferred: one broken datapack file mustn't crash a server.
- **Verify:** A GameTest loads a datapack with an invalid entry and checks the
  error names file and field and the rest loads.

## Commands

#### OPS-011 · Command permission
`Must` · `R0` · `Implemented` · Verify: GameTest

Every `/zas` command shall need permission level 2 by default
(configurable).

- **Rationale:** Inferred: level 2 is vanilla's operator level for
  game-changing commands.
- **Verify:** A GameTest checks a non-operator is refused and that the level
  is configurable.

#### OPS-003 · Inspection commands
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: API-010, API-011

Commands shall report the atmosphere at a point (API-010) and a zone's details
(API-011).

- **Rationale:** vg: ZAS has admin commands to inspect zones ([zas-core
  §8](../research/zas-core.md#8-debug-tooling)).
- **Verify:** A GameTest runs each command and checks the output.

#### OPS-009 · Gas editing commands
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: API-012, CORE-036

Commands shall add, remove or set gas at a point, and fill or vent a region.
They act through API-012, attributed to `command:<player>`.

- **Rationale:** Inferred: admins and testers need to set up and fix
  atmospheres by hand; going through API-012 keeps conservation accounting.
- **Verify:** A GameTest runs each command and checks the gas and its
  attribution.

#### OPS-010 · Audit and invariant commands
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: GEO-032, CORE-073

Commands shall run the geometry audit (GEO-032) on a region, and switch the
invariant checker (CORE-073) on or off and print its report.

- **Rationale:** Inferred: the audit and checker are only useful if operators
  can run them in a live world.
- **Verify:** A GameTest runs the audit and toggles the checker by command.

## Profiling

#### OPS-004 · Profiling command
`Must` · `R0` · `Implemented` · Verify: Manual · Trace: NFR-001 · [zas-core §8](../research/zas-core.md#8-debug-tooling)

A command shall report:
- timings for each phase: geometry, exchange, reactions, heat, apply
- server-thread milliseconds per tick, average and p99 (NFR-001)
- counts of zones, edges, active edges, hotspots, pending classifications and
  paused zones

- **Rationale:** vg: ZAS has profiling tools ([zas-core
  §8](../research/zas-core.md#8-debug-tooling)); server owners need to see the
  cost against NFR-001.
- **Verify:** Manual check of the report during S-10.

#### OPS-012 · Periodic profiling log
`Should` · `R0` · `Implemented` · Verify: Manual · Depends: OPS-004

The OPS-004 report may be logged periodically. This is off by default, with a
configurable interval.

- **Rationale:** Inferred: periodic logs catch problems on servers nobody is
  watching.
- **Verify:** Manual check that the log appears at the set interval when
  enabled.

## Admin controls

#### OPS-005 · Dimension pause
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: GEO-030, GEO-035, GEO-036, GEO-037, GEO-038, CORE-036

An operator shall be able to pause the simulation in a dimension without
restarting the server. Every zone there then behaves as a paused zone
(GEO-030, GEO-035 to GEO-038). The pause survives a restart until it is
resumed.

- **Rationale:** Inferred: an operator needs a way to stop a misbehaving
  dimension without a restart; it reuses the paused-zone rules (D-43).
- **Verify:** A GameTest pauses a dimension, checks its zones are paused,
  restarts, and checks the pause remains.

#### OPS-013 · Region reset
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: CORE-036, CORE-073

An operator shall be able to reset a region to ambient: its zoned cells revert
to unzoned. The gas removed is recorded as an operator-command sink
(CORE-036), attributed to the player.

- **Rationale:** Inferred: operators need to undo griefing or broken
  atmospheres.
- **Verify:** A GameTest resets a region and checks the cells are unzoned and
  the checker records the sink.

#### OPS-006 · Admin logging
`Could` · `R2` · `Deferred` — moved to R2 (lean scope; API-012's source attribution makes it easy to add later), 2026-10-31. · Verify: Inspect

Large releases of hazardous gas and fire ignitions caused by players should be
logged with the player's name and the location, for griefing investigations.

## Preset

#### OPS-007 · Atmosphere difficulty preset
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-14, D-13, D-19, D-28, D-51

A per-world server setting shall select the **Normal** (default) or
**Hardcore** preset. The preset sets defaults for:
- zone triggers (CORE-055, CORE-056), including combustion (FIRE-024)
- vanilla-dimension ambients (ENV-002, ENV-012)
- block pressure damage (FLOW-007, FLOW-016)
- planting as a zone trigger (CONT-053)
- photosynthesis rates (CONT-057)
- space-mod compat modes (SPACE-004, from R2 with D-66)
- container explosions breaking blocks (CONT-107)

- **Rationale:** Decision: D-14.
- **Verify:** GameTests check each listed option's default under each preset.

*2026-10-31: added container explosions (CONT-107, D-57).*

*2026-10-31: added photosynthesis rates (CONT-057, D-61).*

*2026-11-01: SPACE-004 moved to R2 with the Ad Astra adapter (D-66); the preset covers it from R2.*

#### OPS-014 · Overrides survive a preset change
`Must` · `R1` · `Accepted` · Verify: GameTest · Depends: OPS-007

Each preset-controlled option shall be overridable individually, and an
override survives a change of preset.

- **Rationale:** Decision: D-14.
- **Verify:** A GameTest overrides an option, changes the preset, and checks
  the override remains.

#### OPS-015 · Value sources shown
`Should` · `R1` · `Accepted` · Verify: Manual · Depends: OPS-007

Commands and config shall show which values come from the preset and which are
overridden.

- **Rationale:** Inferred: with preset defaults and overrides mixed, operators
  need to see where each value comes from.
- **Verify:** Manual check of the command and config output.
