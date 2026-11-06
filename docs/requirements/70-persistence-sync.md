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
`Must` · `R0` · `Implemented` · Verify: GameTest · Trace: S-09, D-54

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

*2026-11-02: zone pins, with their owners, are saved and restored now
(API-018, `api_018_pins_survive_a_restart`). Dimension pauses (OPS-005) don't
exist yet, so this stays Accepted.*

*2026-11-05: Implemented, with OPS-005 in place.
`data_001_a_saved_world_keeps_every_kind_of_state` saves and reloads one world
of two dimensions holding every kind of state listed and compares each within
1e-9. The stand-in for a restart is `AtmosService.stopForTest` and
`startForTest`, through the real save code and byte format; a GameTest can't
restart the server. A queued addition in a paused dimension comes back and is
then voided, with the checker recording it, as it would have been a tick later.*

#### DATA-009 · Derived state is rebuilt
`Must` · `R0` · `Implemented` · Verify: Inspect, GameTest · Trace: D-54

Edges, seal masks, sub-cell shapes and hotspots shall not be saved; they are
rebuilt on load. Edges start awake and sleep again once equivalent (CORE-066).
Hotspots are rebuilt from fire blocks, since every hotspot is a vanilla fire or
`zas:gas_fire` block (FIRE-018, FIRE-019).

- **Rationale:** Decision: D-54. Rebuilding avoids saving state that can go
  stale.
- **Verify:** Inspect the saved format; a GameTest checks edges and hotspots
  return after a reload.

*2026-11-03: Implemented. Hotspots are rebuilt from their blocks as the
sections are mirrored again ([#42](https://github.com/west3436/zas-cubed/issues/42)); `data_009_edges_rebuilt` and
`data_009_hotspots_rebuilt_after_reload` check edges and hotspots.*

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

*2026-11-05: A known limit, found by the integrity fuzz (NFR-010). A rebuilt
zone does hold the sum of its shares, but the shares can fail to add up to what
the zone held. A chunk writes its share once, when it unloads, and the zone can
change after that. Later shares now make up the difference: each is what the
earlier ones left of the zone's gas, by volume among the chunks that haven't
saved. So a cell filled in the tick a chunk saves during, a hole opened in that
tick, and a paused zone taking in a share all still add up
(`data_008_shares_add_up_when_a_cell_fills_in_the_tick_a_chunk_saves`,
`data_008_shares_add_up_when_a_hole_opens_in_the_tick_a_chunk_saves`,
`data_008_shares_add_up_when_a_paused_zone_takes_in_a_share`). While the zone
hasn't changed, that is its plain share by volume, so what a crash can restore
(#304) is no worse for it. The limit left is a zone that divides in that same
tick, because the share already written holds gas that's now in the other parts
too. The difference can go either way. In a room of 720 mol, half of it in the
saving chunk, a wall across that half leaves 514.3 mol in the shares
(`data_008_known_limit_a_zone_dividing_in_the_tick_a_chunk_saves_can_lose_gas`),
where plain shares by volume would come to 771.4 mol, so in this one case the
remainder is further off than plain shares were. Seed 4 of the fuzz has 2.49e247
mol too much in one zone's shares at operation 3582, about 0.7% of the gas in
that run. It needs a zone across chunks, and a block change that divides it in
the tick one of its chunks unloads in. The fuzz excuses exactly that event, only
for those zones, and by no more than the gas the zone no longer holds. The
alternatives are for the owner to decide at review: an unload during a tick
could wait for the worker, which removes the window but changes a core design
rule, or the pipeline could track divisions and correct the share when the
chunk reloads, which is a larger change.*

*2026-11-06: The window is wider than that note says, as fresh seeds of the fuzz
showed. A chunk saves from the published table, so the tick that divides the
zone can be the one in flight as it unloads, or the next one if the block change
was queued before it unloaded. An unload waiting for the worker would close only
the first. The share can also come back on top of what the zone's other parts
hold: on seed 29 a share of 3.79e247 mol returns at a reload where the zone had
lost 2.80e247, about 0.8% of that gas in the run. And gas can go: on seed 30 a
part holding 3,229 mol leaves memory against a share of 2,191 mol, so 1,038 mol
goes. The fuzz now excuses no more than the larger of the zone's loss and the
share written, for that zone and those it divided into, and reports how many
differences it excused for each seed.

So there is something the fuzz can no longer catch: a real bug that makes or
loses no more than that bound, in a zone that has just divided after an unload.*

*2026-11-06: the owner accepted this as a known limit, with no follow-up, so the
alternatives above aren't planned. The fuzz keeps excusing exactly that event.*

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

*2026-11-06: A saved addition now keeps the sub-cell it targets (API-015,
[#122](https://github.com/west3436/zas-cubed/issues/122)), so one aimed at a pane's
smaller half no longer lands in the block's largest cell after a restart. That is
chunk record format 5. Formats 1 to 4 still read, and an addition from one of them
acts on the largest cell. `data_011_saved_addition_lands_in_its_sub_cell` (Unit) and
`data_011_queued_addition_keeps_its_sub_cell_across_a_restart` (GameTest) check it.
Operations carried over to a new engine after a geometry palette change had the same
fault and keep their sub-cell now too.*

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

*2026-11-05: The owner's sound decisions add two things to this list. One is a
player's own pressure for thin air (DATA-019). The other is how hard each device
nearby is working (DATA-017), which comes with its sync (DATA-018) in the same
round. All three are Draft until the owner's review, and none carries zone
geometry or per-cell data.*

*2026-11-05: Gas particles (CLIENT-018) are sent as well, in place of vanilla's
particle packets, which a client can't cap. Each is one event: what happened, a
place, a direction and one number. Like vanilla's, they are sent to nearby
players only. They carry nothing about a zone, so this keeps to D-53.*

#### DATA-014 · Own-state sync rate
`Must` · `R1` · `Accepted` · Verify: GameTest, Bench · Trace: LIFE-023, CLIENT-008 · Depends: DATA-005

A player's own atmosphere state shall be sent to that player only, at most 4
times a second, and only when a CLIENT-008 threshold is crossed or a displayed
value changes by its display resolution (1 kPa, 1 K, 0.1 kPa partial
pressure).

- **Rationale:** Decision: D-53.
- **Verify:** A GameTest records state packets during a slow pressure change
  and checks rate and triggers; Bench measures bytes.

*2026-11-02: The HUD state is built this way
([#38](https://github.com/west3436/zas-cubed/issues/38)). Every 5 game ticks a
player's state is worked out and sent to it alone if a level, the worst toxic
gas, or a shown value at its resolution changed. A reading with no alert
carries no value, so normal air sends nothing; a state is 21 to about 40
bytes. `data_014_hud_syncs_on_change_at_most_4_a_second`,
`data_014_values_resend_at_their_resolution` and
`data_014_payload_round_trips` (GameTests) pass. There's no bench yet, and
LIFE-023's breathing sync is separate, so this stays Accepted.*

#### DATA-015 · Airflow sync
`Must` · `R1` · `Accepted` · Verify: GameTest, Bench · Trace: FLOW-017, CLIENT-003, CLIENT-010 · Depends: DATA-005

Airflow data for active edges within 32 blocks of a player shall be sent to
that player at most once per atmos tick, and only when an edge's pressure
difference changes.

- **Rationale:** Decision: D-53.
- **Verify:** A GameTest checks airflow packets reach only players within 32
  blocks, at most once per tick; Bench measures bytes.

*2026-11-05: Checked for CLIENT-003 and CLIENT-010. What the client had wasn't
enough, and the rule above wasn't what the code did. FLOW-017's
`AirflowSyncPayload` goes out every atmos tick while a player is near airflow,
changed or not, and lapses without one. It holds only what pushes that player:
within the push range plus 8 blocks, on edges beside its zone, at or above its
class's threshold, and nothing for creative players or spectators. So it stays
as it is, and is a known exception to "only when it changes": the push must not
outlive the flow by more than a moment.*

*Showing airflow has its own payload, `AirflowEffectsPayload`: every exported
edge's openings within 32 blocks, whatever the player's zone or game mode, each
with its block and the face the flow leaves by, and each edge with its difference
in whole kPa and the moles per second through those openings (CLIENT-010), as one
byte: steps of an eighth of a doubling, about 9%. No zone ids. At an atmos tick the
server sends a player its whole list if it differs from the last it sent, so a
difference that moved by a whole kPa, a flow that moved by a step, or an opening
coming into range or leaving. A steady breach sends nothing, and the
client holds the list. A newer list replaces it, so an edge missing from one has
ended, and an empty list ends them all. As a failsafe against a client showing a
flow that ended, a list that isn't empty is sent again every 10 s, and the client
drops it after 30 s with no packet, on leaving the dimension or on logging out.*

*Measured bytes, before about 21 of packet id and channel name: 1 for an empty
list; 16 for one hole; 48 for a 3 × 3 hole (4 an opening); and at most 1,161, for
the largest list, 32 edges and 256 openings. An edge with over 64 openings in
range is sampled evenly. So a steady one-hole breach costs about 4 B/s, and one
whose difference or flow is moving at most about 37 B/s. FLOW-017's payload, for
comparison, is 89 bytes every second for one hole. `AirflowEffectsPayloadTest`, `HeldFlowsTest` and
`VisibleFlowsTest` check the format, the holding and the selection;
`data_015_a_steady_breach_is_held_until_it_ends`,
`data_015_at_most_once_per_atmos_tick`,
`data_015_airflow_off_ends_what_clients_show` and
`data_015_a_new_player_object_is_sent_it_again` (GameTests) check the sending.
There's no bench yet, and FLOW-017's payload is the exception above, so this stays
Accepted.*

#### DATA-019 · Listener pressure sync
`Should` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: CLIENT-016, D-53 · Depends: DATA-005

The real pressure at a player's eyes, in whole kPa up to 50, shall be sent to
that player only, at most 4 times a second, and only when it changes. It shall
be the same whatever the player wears and whatever its game mode.

- **Rationale:** Decision: D-53 allows a player's own atmosphere state. Inferred:
  the HUD's state (DATA-014) can't serve, because its pressure is what reaches
  the player through its protection and it has none without an alert, so a
  sealed suit in a vacuum, or anyone in creative, would read as normal air.
- **Verify:** `ThinAirTest` (Unit) checks a new client counts as normal air;
  `data_019_*` (GameTests) check the triggers, the rate, the reading under each
  game mode and the payload.

*2026-11-05: added for review, with the behaviour built (`hud.ListenerAir`). A
message is 1 byte of payload and 19 bytes as a packet, before the connection's
framing. Normal air and anything above it read the same, so they send nothing
after the first reading.*

*2026-11-05: accepted by the owner, and Implemented: its tests pass.*

#### DATA-017 · Device activity
`Should` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: CLIENT-015, D-53

A device that moves gas shall have an activity level, read from the moles it
moved in its last atmos tick: idle below 0.05 mol, trickle from 0.05, working
from 1 and hard from 5. A level shall rise at once, and fall only after 2 atmos
ticks running below half of where it starts. A device that is off or has no
power shall read as idle from that atmos tick, as shall an open pipe end or
passive vent moving less than 0.25 mol/s net. A portable (CONT-018), pump, filter
or mixer switched off shall read as idle from that moment, and a portable switched
on shall read at once as what its next atmos tick would move. A device that does
other work, such as a heater, shall read as working while it runs.

- **Rationale:** Decision: the owner's sound decisions of 2026-11-05: a device's
  loop plays only while it moves gas, and particles show gas moving. Inferred:
  moles are what every gas device has in common. The scale's ends are set by
  what devices do at 1 atm and 293 K: filling a vacuum from a full line moves
  8 mol/s and a siphoning scrubber 21, and a scrubber on trace CO₂ moves under
  0.01.
- **Verify:** `DeviceActivityTest` (Unit) checks the thresholds and the
  hysteresis; `data_017_*` (GameTests) check the vent and the scrubber.

*2026-11-05: added for review, with the behaviour built
(`device.DeviceActivity`, `AtmosDevice.reportMoved`, one line in a device's
step). The scale's values are for the owner to judge by ear and eye. A device
reports what it has decided to move, as its status does, so one blowing against
a refused change still reads as moving. The vent and the scrubber report so far.*

*2026-11-05: the pumps, filter, mixer, passive vent, canister and open pipe ends
report too, and `cont_125_*` check them. A passive vent or open pipe end reports
its net flow rather than all the gas it moves, so gases only passing each other
at one pressure read as idle: for the owner's review, with CONT-125.*

*2026-11-05: the portable pump and scrubber report what they move, and the heater,
air conditioner and recharger report running, and `cont_127_*` check them. The
generator isn't an atmospherics device, so it doesn't report: its loop follows the
lit state it already syncs (CONT-127).*

*2026-11-05: An open end or passive vent now reads only the flow that shows
(CLIENT-019), with no hold below it, so its loop stops with its particles
(CONT-125).*

*2026-11-05: from the owner's playtest of the S-11/S-13 world, on the heater, air
conditioner, portable scrubber and portable pump: "Noticeable delay in the animation
and sounds when toggling power". A switch now passes its level on when it moves, not
at the device's next step (`AtmosDevice.finishActivity`), and
`DeviceSoundTests.cont_018_*`, `cont_019_*` and
`data_017_an_inline_device_switched_off_reads_idle_at_once` check it. A pump, filter
or mixer switched on still waits for its step, which is what finds out if it has
power and gas to move. A client hears of any of this at the player's next sync,
within 4 game ticks (DATA-018).*

*2026-11-05: accepted by the owner, and Implemented: its tests pass.*

#### DATA-018 · Device activity sync
`Should` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: CLIENT-015, D-53 · Depends: DATA-005, DATA-017

The activity level of each device that isn't idle within 32 blocks of a player
shall be sent to that player only, at most once every 4 game ticks, and only what
has changed. A player shall be told to forget a device that goes idle, is
removed, has not stepped for 3 s, or is over 40 blocks away.

- **Rationale:** Decision: D-53 allows this, as it carries nothing of a zone, and
  the owner's sound decisions of 2026-11-05 need it. Inferred: 32 blocks matches
  airflow's range (DATA-015) and is further than a loop is heard, so a client
  knows of a device before the player can hear or see it. The gap up to 40 blocks
  stops a player on the edge from being told again and again.
- **Verify:** `ClientDeviceActivityTest` (Unit) checks the client's table;
  `data_018_*` (GameTests) check who is told, the rate, that nothing changed
  means nothing sent, each way of forgetting, and the bytes.

*2026-11-05: added for review, with the behaviour built
(`device.DeviceActivitySync`, `device.ClientDeviceActivity`). It is its own
payload rather than a block state, which would rebuild the block's model and
relight it, or a block entity update, which by the packet formats comes to about
30 bytes a device, unbatched, for everyone tracking the chunk.*

*Measured bytes, before the connection's framing: 9 for each change, and 21 for
a message with its packet id and channel name, so 30 for one device. A message
holds at most 25 changes and the rest wait for the next, so one player can never
cost more than 1,230 B/s.*

*Estimated for a large station, with 200 devices within 32 blocks of a player. A
settled station sends nothing. All 200 starting at once costs 1,968 bytes across
8 messages, over 1.4 s. All of them filling rooms over a minute, each going through
hard, working, trickle and idle, is about 13 changes a second, or at most 225 B/s
if they land in all 5 of a second's messages. There's no bench yet, so DATA-006 is
still to be measured with this included.*

*2026-11-05: A player is now sent what has changed every 4 game ticks rather than
every 20, so a loop starts and stops within 4 ticks of its device's step rather
than up to a second later, for the owner's wish that sound and particles go
together (CONT-125). A level still changes only at a step, at most once a second a
device, so there are no more changes than before. What can rise is the messages
they come in: up to 4 more a second at 21 bytes each, at most 84 B/s more for a
player. A message also holds at most 25 changes rather than 127: at 5 messages a
second 127 would have let the limit rise to 5,820 B/s, past DATA-006's 2 KiB/s. It
is now 1,230 B/s rather than 1,164, at 125 changes a second rather than 127.
`data_018_what_a_message_costs` measures the bytes and checks that figure.*

*2026-11-05: accepted by the owner, and Implemented: its tests pass.*

#### DATA-006 · Bandwidth budget
`Should` · `R1` · `Accepted` · Verify: Bench · Trace: S-10

In the S-10 scale scenario, atmos sync shall average at most 2 KiB/s per
player, excluding the debug overlay.

- **Rationale:** Inferred: 2 KiB/s per player keeps atmos traffic small next
  to vanilla's own, even on busy servers.
- **Verify:** Bench measures average atmos bytes per player in S-10.

*2026-11-05: Gas particle events a block repeats while gas flows (CLIENT-019)
count here. A player is sent at most 20 a second, about 1.2 KiB/s, however large
the station. No Bench has measured this yet.*
