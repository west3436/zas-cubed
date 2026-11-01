# 60 · Client (`CLIENT`)

**Status:** Accepted 2026-10-31 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules).

**Scope.** What players see and hear: the HUD, airflow effects, sound, and
debug rendering. The client never simulates (A-03), and receives only the data
in DATA-005 (D-53).

## HUD

#### CLIENT-001 · HUD visibility
`Must` · `R1` · `Accepted` · Verify: Manual · Trace: S-02, S-08

The atmosphere HUD shall be shown while any reading is at warning level or
worse (CLIENT-008), while the player wears a head item that provides sealed
breathing (LIFE-021), or while they hold an analyzer (CONT-034).

- **Rationale:** Inferred: the HUD should appear only when it matters, so it
  stays out of the way in normal play.
- **Verify:** Manual check that the HUD appears at a warning, with a sealed
  helmet and with an analyzer, and hides otherwise.

#### CLIENT-008 · HUD readings and thresholds
`Must` · `R1` · `Accepted` · Verify: Manual, GameTest · Trace: LIFE-003, LIFE-004, LIFE-010, LIFE-011, LIFE-014, LIFE-041

The HUD shall show pressure, O₂ partial pressure, temperature and toxins, each
at a warning or danger level. Toxin levels are read from the hazard profiles
(LIFE-004), so addon gases get indicators without code. Defaults:

| Reading | Warning | Danger |
|---|---|---|
| Pressure | below 50 kPa or above 325 kPa | below 20 kPa, or 550 kPa and above (LIFE-010, LIFE-014) |
| O₂ partial pressure | below 19.8 kPa (19.5 % of 1 atm, the OSHA level) | below 16 kPa (LIFE-003) |
| Temperature | below 240 K or above 340 K | outside 220–360 K (LIFE-011) |
| Each toxic gas | its first LIFE-041 level | its first level that incapacitates or damages |

- **Rationale:** vg: vg's HUD warnings at 50 and 325 kPa ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)); the O₂
  warning is OSHA's 19.5 % level, the temperature warnings are inferred 20 K
  inside the damage band, and dangers match LIFE.
- **Verify:** A GameTest checks the synced warning level at each threshold;
  Manual checks the display.

#### CLIENT-009 · Internals indicator
`Must` · `R1` · `Accepted` · Verify: Manual · Trace: LIFE-029, LIFE-028

While the HUD is shown, it shall show whether internals are on (LIFE-029) and
the pressure of the tank they draw from (LIFE-028).

- **Rationale:** Inferred: a player on internals needs to know how much air is
  left.
- **Verify:** Manual check of the indicator while a tank drains.

#### CLIENT-014 · Not colour alone
`Should` · `R1` · `Accepted` · Verify: Manual

Every HUD warning and danger cue shall have a shape or flashing cue as well as
its colour.

- **Rationale:** Inferred: colour-blind players must be able to tell warning
  from danger.
- **Verify:** Manual check with a colour-blindness filter.

## Effects

#### CLIENT-002 · Gas visibility
`Should` · `R2` · `Deferred` — moved to R2 (lean scope; only H₂O vapor is plausibly visible among the built-in gases, D-42, and it would need per-cell zone membership synced to clients), 2026-10-31. · Verify: Manual · Trace: CORE-010

Gases with visibility data shall tint or fog the cells of zones where they
exceed their threshold. Example: water vapor as mist. Addon gases such as
Cl₂ (yellow-green) define their own colour.

#### CLIENT-003 · Airflow particles
`Should` · `R1` · `Accepted` · Verify: Manual · Trace: FLOW-001, FLOW-011, DATA-015

An active edge whose pressure difference is at or above FLOW-011's lowest class
threshold shall show particles at its openings, moving toward the
lower-pressure side, to players within 32 blocks.

- **Rationale:** Inferred: players need to see where air is escaping; the
  32-block range matches the airflow sync range (DATA-015).
- **Verify:** Manual check of particles at a breach, and none beyond 32
  blocks.

#### CLIENT-010 · Airflow sound
`Should` · `R1` · `Accepted` · Verify: Manual · Trace: FLOW-001, FLOW-011, DATA-015

Such an edge shall play a looping hiss whose volume and pitch rise with the
pressure difference, audible within 16 blocks.

- **Rationale:** Inferred: a hiss is the clearest cue to a leak, louder as it
  worsens.
- **Verify:** Manual check of the hiss at different pressure differences and
  distances.

#### CLIENT-004 · Low-pressure sound
`Could` · `R2` · `Deferred` — moved to R2 (lean scope; damping every sound needs sound-engine hooks, and no R1 scenario needs it), 2026-10-31. · Verify: Manual

Sounds heard by a player in low pressure shall be attenuated, falling silent
in vacuum. Sound from the player's own suit is exempt.

## Debug rendering

#### CLIENT-005 · Debug zone overlay
`Must` · `R0` · `Implemented` · Verify: Manual · Trace: D-43, GEO-030, GEO-024

An overlay, for players with permission level 2, shall colour each zone's
cells within 2 chunks of the player. Paused zones and unresolved regions are
shown distinctly.

- **Rationale:** vg: ZAS has debug zone overlays ([zas-core
  §8](../research/zas-core.md#8-debug-tooling)); geometry is otherwise
  invisible, so developers and operators need to see it.
- **Verify:** Manual check of the overlay on a station with active, paused and
  unresolved regions.

*2026-10-31: Implemented. The owner ran the #98 test plan in a dev client and
it passed: zones tinted per zone with labels, a paused zone (a tunnel past a
chunk border at render distance 2) in grey, and a held breach in magenta.*

#### CLIENT-011 · Debug edges
`Must` · `R0` · `Accepted` · Verify: Manual · Depends: CLIENT-005

The overlay shall show edges, coloured by type (zone or ambient, direct or
boundary), each labelled with its pressure difference.

- **Rationale:** vg: ZAS's debug tools show connections ([zas-core
  §8](../research/zas-core.md#8-debug-tooling)).
- **Verify:** Manual check of edge colours and labels around a door and a
  breach.

*2026-10-31: The overlay draws all four edge types and their labels (#98). The
owner's test passed for the zone/direct and ambient/direct edges at a door and
a breach. Boundary edges can't occur until a block carries the zone-boundary
flag (GEO-007, R1), so this stays Accepted until they can be checked.*

#### CLIENT-012 · Debug seal masks and sub-cells
`Must` · `R0` · `Implemented` · Verify: Manual · Trace: D-44 · Depends: CLIENT-005

The overlay shall show the seal mask and sub-cell boundaries of the block the
player is looking at.

- **Rationale:** Decision: D-44. Sub-cells are invisible otherwise and hard to
  debug.
- **Verify:** Manual check on a pane, a slab and a chest.

*2026-10-31: Implemented. The owner's test of #98 passed on a pane, a slab and
a chest, plus stairs, a wall, a door, a trapdoor and scaffolding.*

#### CLIENT-013 · Debug hotspots
`Must` · `R0` · `Accepted` · Verify: Manual · Depends: CLIENT-005

The overlay shall mark hotspots.

- **Rationale:** Inferred: hotspots aren't always visible blocks, and fire
  bugs are hard to debug without them.
- **Verify:** Manual check during a gas fire.

*2026-10-31: The overlay has a hotspot layer (#98), but nothing produces
hotspots before FIRE (R1), so this stays Accepted until it can be checked
during a gas fire.*

## Units and language

#### CLIENT-006 · Display units
`Should` · `R1` · `Accepted` · Verify: Manual · Trace: A-04

Display units shall be a client setting:
- pressure: kPa (default), atm or psi
- temperature: °C (default), K or °F

They apply to the HUD, the analyzer and device GUIs. Commands always print kPa
and K.

- **Rationale:** Inferred: players think in different units; commands stay in
  kPa and K so logs and bug reports are comparable (A-04).
- **Verify:** Manual check of each unit in the HUD, analyzer and a GUI, and
  that commands print kPa and K.

#### CLIENT-007 · Localization
`Must` · `R1` · `Accepted` · Verify: Inspect

All player-facing text shall be translatable, with en_us as the baseline.

- **Rationale:** Inferred: standard practice for Minecraft mods, so the
  community can translate.
- **Verify:** Inspect: a check fails the build on hard-coded player-facing
  strings.
