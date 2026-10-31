# 70 · Persistence and sync (`DATA`)

**Status:** Accepted 2026-10-31 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules).

**Scope.** Saving and loading simulation state, migrating it between
versions, and syncing what clients need. The persistence model is D-54; the
sync model is D-53. Background:
[neoforge §2](../research/neoforge-1.21.1.md#2-data-storage) and
[§8](../research/neoforge-1.21.1.md#8-networking).

## Persistence

#### DATA-001 · Persistent state
`Must` · `R0` · `Accepted` · Verify: GameTest · Trace: S-09, D-54

Saving and reloading shall preserve, to a relative error of 1e-9 per gas:
- each cell's zone membership, sub-cells included (D-44)
- each zone's mixture and temperature
- zone pins (API-018)
- held block changes (DATA-010)
- queued gas additions (DATA-011)
- dimension pauses (OPS-005)

- **Rationale:** Decision: D-54 (S-09).
- **Verify:** A GameTest saves and reloads a world with every listed kind of
  state and compares each within 1e-9.

#### DATA-009 · Derived state is rebuilt
`Must` · `R0` · `Accepted` · Verify: Inspect, GameTest · Trace: D-54

Edges, seal masks, sub-cell shapes and hotspots shall not be saved; they are
rebuilt on load. Edges start awake and sleep again once equivalent (CORE-066).
Hotspots are rebuilt from fire blocks, since every hotspot is a vanilla fire or
`zas:gas_fire` block (FIRE-018, FIRE-019).

- **Rationale:** Decision: D-54. Rebuilding avoids saving state that can go
  stale.
- **Verify:** Inspect the saved format; a GameTest checks edges and hotspots
  return after a reload.

#### DATA-002 · Chunk-aligned storage
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: GEO-030, D-43, D-54

Each chunk shall save the zone membership and gas of its own cells: its share
of each zone's mixture, by volume. Zones are rebuilt from their chunks as they
load. Nothing saves zone geometry for a whole level. (A paused zone doesn't
change, and its pressure and composition are the same in every share, so its
last-known state (GEO-035) needs no extra storage.)

- **Rationale:** Decision: D-54. Chunk attachments load and save with their
  chunks ([neoforge §2](../research/neoforge-1.21.1.md#2-data-storage)).
- **Verify:** A GameTest reloads a zone spanning several chunks one chunk at a
  time and checks it reassembles.

#### DATA-008 · Reassembly conserves gas
`Must` · `R0` · `Implemented` · Verify: Unit, GameTest · Trace: CORE-036 · Depends: DATA-002

A zone rebuilt from its chunks shall hold the sum of their shares' moles, to a
relative error of 1e-9 per gas.

- **Rationale:** Decision: D-54, with conservation (CORE-036).
- **Verify:** A Unit test splits and reassembles random zones; a GameTest
  checks a reloaded zone's moles.

#### DATA-010 · Held changes persist
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: GEO-037, D-43

Block changes held for a paused zone (GEO-037) shall be saved in their chunk
and processed when the zone resumes, even across a restart.

- **Rationale:** Decision: D-43 and D-54.
- **Verify:** A GameTest holds a change for a paused zone, restarts, resumes
  the zone and checks the change applies.

#### DATA-011 · Queued operations at save and shutdown
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: D-46, CORE-036, CORE-074, API-016

Queued gas additions not yet applied, including those re-queued under
CORE-074, shall be saved and applied after load, because their gas has already
left the caller (D-46). Queued removals are not saved: at shutdown their
futures complete with nothing moved.

- **Rationale:** Decision: D-46 and D-54. Added gas has already left the
  caller, so dropping it would lose gas.
- **Verify:** A GameTest saves with queued additions and removals and checks
  additions apply after load and removals complete empty.

#### DATA-016 · Content-mod persistence
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-09, D-50, CONT-095

`zas_atmos` shall save, to a relative error of 1e-9 per gas:
- pipe network contents, stored in the pipe parts that hold them and split by
  volume
- device settings and internal buffers
- canister contents
- tank contents, as item data

- **Rationale:** Decision: D-54 (S-09).
- **Verify:** A GameTest saves and reloads a station with pipes, devices,
  canisters and tanks and compares their contents within 1e-9.

#### DATA-003 · Versioned format
`Must` · `R0` · `Implemented` · Verify: Unit

Saved data shall carry a format version.

- **Rationale:** Inferred: a format version is needed to migrate saves safely
  (DATA-012).
- **Verify:** A Unit test checks the version is written and read.

#### DATA-012 · Forward migration
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Depends: DATA-003

Saves from every earlier release shall load and be migrated forward. Unknown
gas ids are handled by CORE-013 and CORE-016.

- **Rationale:** Inferred: players upgrade mid-world, and losing a station to
  an update is unacceptable.
- **Verify:** Unit tests migrate saved fixtures from each release; a GameTest
  loads an old world.

#### DATA-004 · Corruption tolerance
`Must` · `R0` · `Implemented` · Verify: GameTest

Corrupt or missing atmos data shall never prevent a world from loading. The
affected chunk's cells revert to unzoned.

- **Rationale:** Inferred: a mod must never cost players their world; losing
  one chunk's atmosphere is the lesser harm.
- **Verify:** A GameTest corrupts a chunk's atmos data and checks the world
  loads with that chunk unzoned.

#### DATA-013 · Corruption warning
`Must` · `R0` · `Implemented` · Verify: GameTest · Depends: DATA-004

Each chunk reset under DATA-004 shall log one warning naming the dimension and
chunk.

- **Rationale:** Inferred: silent resets would be impossible to diagnose.
- **Verify:** The DATA-004 GameTest checks one warning names the dimension and
  chunk.

#### DATA-007 · Clean uninstall
`Should` · `R1` · `Accepted` · Verify: Manual

Removing zas-cubed from a world shall leave vanilla data intact. Only
zas-cubed's own blocks (including `zas:gas_fire`, which becomes air), items
and attachments are lost.

- **Rationale:** Inferred: players must be able to try the mod without risking
  their world; attachments are dropped cleanly when the mod is absent
  ([neoforge §2](../research/neoforge-1.21.1.md#2-data-storage)).
- **Verify:** Manual: remove the mods from a test world and check it loads
  with vanilla blocks intact.

## Client sync

#### DATA-005 · Client sync scope
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: A-03, D-53

Clients shall receive only:
- their own player's atmosphere state (DATA-014)
- airflow data for nearby active edges (DATA-015)
- debug data, while an operator has the overlay on (CLIENT-005)

No zone geometry or per-cell data is sent otherwise. Gas definitions
(CORE-014) and breathing state (LIFE-023) are synced as those require.

- **Rationale:** Decision: D-53. Syncing geometry floods clients, as Ad Astra
  issue #434 shows ([space research
  §B](../research/space-mods-and-prior-art.md#b-prior-art-rooms-and-gas-in-voxel-worlds)).
- **Verify:** Inspect the packet types; a GameTest records the packets sent to
  a player.

#### DATA-014 · Own-state sync rate
`Must` · `R1` · `Accepted` · Verify: GameTest, Bench · Trace: LIFE-023, CLIENT-008 · Depends: DATA-005

A player's own atmosphere state shall be sent to that player only, at most 4
times a second, and only when a CLIENT-008 threshold is crossed or a displayed
value changes by its display resolution (1 kPa, 1 K, 0.1 kPa partial
pressure).

- **Rationale:** Decision: D-53.
- **Verify:** A GameTest records state packets during a slow pressure change
  and checks rate and triggers; Bench measures bytes.

#### DATA-015 · Airflow sync
`Must` · `R1` · `Accepted` · Verify: GameTest, Bench · Trace: FLOW-017, CLIENT-003, CLIENT-010 · Depends: DATA-005

Airflow data for active edges within 32 blocks of a player shall be sent to
that player at most once per atmos tick, and only when an edge's pressure
difference changes.

- **Rationale:** Decision: D-53.
- **Verify:** A GameTest checks airflow packets reach only players within 32
  blocks, at most once per tick; Bench measures bytes.

#### DATA-006 · Bandwidth budget
`Should` · `R1` · `Accepted` · Verify: Bench · Trace: S-10

In the S-10 scale scenario, atmos sync shall average at most 2 KiB/s per
player, excluding the debug overlay.

- **Rationale:** Inferred: 2 KiB/s per player keeps atmos traffic small next
  to vanilla's own, even on busy servers.
- **Verify:** Bench measures average atmos bytes per player in S-10.
