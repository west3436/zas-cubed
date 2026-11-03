# 40 · Atmospherics content (`CONT`)

**Status:** Accepted 2026-10-30 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules). CONT-100 to CONT-107 were added and
accepted on 2026-10-31 (container reactions, TTV and igniter, D-57), and
CONT-108 and CONT-109 from the gap-check answers (D-59). On 2026-11-01 the air
alarm became the area air controller and the power cable and multipart block
were dropped (D-68), and portables moved to batteries (D-69): CONT-018,
CONT-042, CONT-062, CONT-068 and CONT-069 changed, CONT-063 and CONT-097 to
CONT-099 were rejected, and CONT-110 to CONT-113 were added as Draft.
CONT-114 (dyed pipes) was added as Draft on 2026-11-02, then changed by the
owner, accepted and implemented the same day.

**Scope.** The SS13-style content layer (D-02): pipe networks, devices,
portable atmospherics, tanks and suits, area air controllers and firelocks,
tools, how gas is obtained, power, and progression. Background:
[vg-atmos-content](../research/vg-atmos-content.md).

R1 ships a playable subset. The rest of vg's device set is R2 (see the table
at the end). vg's per-2 s device rates are converted to per second (D-20).

## Pipe networks

#### CONT-001 · Network model
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: [vg §1](../research/vg-atmos-content.md#1-pipe-networks)

Connected pipes shall form a network. Each continuous pipe run holds one
mixture whose volume is the sum of its segments' volumes.

- **Rationale:** vg: a vg pipeline owns one mixture whose volume is the sum of
  its pipes ([vg §1](../research/vg-atmos-content.md#1-pipe-networks)).
- **Verify:** A Unit test builds runs of several lengths and checks their
  volumes; a GameTest checks connected pipes share one mixture.

*2026-11-02: Implemented ([#47](https://github.com/west3436/zas-cubed/issues/47)).
A network is one `GasStore` whose volume is its members' sum ([architecture
§11.4](../architecture.md#114-pipe-networks)). `PipeNetworkTest.cont_001_*`
(Unit) checks runs of 1 to 50 pipes and a vent buffer;
`PipeTests.cont_001_connected_pipes_share_one_mixture` (GameTest) checks a run,
a branch and a cap share one mixture, and `cont_001_a_pipe_saves_its_share`
that a segment saves its share by volume.*

#### CONT-007 · Pipe and buffer volumes
`Must` · `R1` · `Implemented` · Verify: Unit · Trace: [vg §1](../research/vg-atmos-content.md#1-pipe-networks)

Each pipe block shall hold 70 L, and each device port buffer 200 L (vents
400 L), as in vg.

- **Rationale:** vg: vg's 70 L pipes and 200 L port buffers ([vg
  §1](../research/vg-atmos-content.md#1-pipe-networks)); the 400 L vent buffer
  is inferred, twice a port buffer, so a vent can keep up its rate (CONT-078).
- **Verify:** A Unit test checks each part's volume.

*2026-11-02: Implemented ([#48](https://github.com/west3436/zas-cubed/issues/48)).
Pipes and caps hold 70 L, a connector port 200 L, a vent 400 L and a canister
1,000 L (`PipeVolumes`). `PipeVolumesTest.cont_007_each_part_has_its_volume`
(Unit); `GasDeviceTests` checks the volumes of built networks.*

#### CONT-008 · Perfect mixing
`Must` · `R1` · `Implemented` · Verify: Unit

A pipe run's mixture shall be perfectly mixed after every change.

- **Rationale:** vg: vg merges a network into one mixture whenever it changes
  ([vg §1](../research/vg-atmos-content.md#1-pipe-networks)).
- **Verify:** A Unit test adds gas at one end of a run and checks the whole
  run is uniform after the change.

*2026-11-02: Implemented ([#47](https://github.com/west3436/zas-cubed/issues/47)).
A network's gas is a single mixture, so every change mixes the whole run.
`PipeNetworkTest.cont_008_gas_added_at_one_end_is_uniform_across_the_run`
(Unit). The API gives no heat capacities, so gases at different temperatures
mix at the mole-weighted mean; the Verify doesn't cover temperatures.*

*2026-11-02: Gas at different temperatures now mixes conserving thermal
energy, with each gas's heat capacity from the API (API-051,
[#157](https://github.com/west3436/zas-cubed/issues/157)), as the owner
required before release. Checked by
`PipeNetworkTest.cont_008_gas_at_different_temperatures_mixes_conserving_thermal_energy`
(Unit).*

#### CONT-002 · Separate from zones
`Must` · `R1` · `Implemented` · Verify: Inspect · Trace: API-021

Pipe gas shall never join zone gas directly. Only devices, open ends (CONT-004)
and ruptures move gas between pipes and zones.

- **Rationale:** vg: in vg, pipe gas never joins zone gas; only devices move
  it ([vg §1](../research/vg-atmos-content.md#1-pipe-networks)).
- **Verify:** Inspect the code: only devices, open ends and ruptures call zone
  mutations.

*2026-11-02: Implemented ([#47](https://github.com/west3436/zas-cubed/issues/47)).
Inspect: the content mod calls `GasMutations` only in `ZonePort` (devices and
open ends) and `PipeNodeEntity` (a removed segment's share, CONT-009).*

#### CONT-003 · Gas-conserving rebuilds
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: CORE-036

Splitting or merging networks shall share gas out by volume.

- **Rationale:** vg: vg shares a pipeline's gas out by volume when it is
  rebuilt ([vg §1](../research/vg-atmos-content.md#1-pipe-networks)); also
  CORE-036.
- **Verify:** A Unit test splits and merges networks and checks each part gets
  gas by volume; a GameTest breaks a pipe in a run and checks total moles.

*2026-11-02: Implemented ([#47](https://github.com/west3436/zas-cubed/issues/47)).
A network never changes its members: it dissolves, each member keeping its
share by volume, and the next one built gathers the shares.
`PipeNetworkTest.cont_003_*` (Unit) splits and merges networks;
`PipeTests.cont_003_breaking_a_pipe_conserves_gas` (GameTest) breaks a pipe in
a run and checks each part's share and the total.*

*2026-11-02: Note: `PipeChunkTests.cont_003_*` (GameTest) runs a network across
a chunk border through chunk lifecycles. The total stays the same when the far
chunk drops out of full status, when both chunks unload and load again, and when
the network is rebuilt while the far chunk is away. Writing them found a bug,
now fixed: devices in a chunk that had dropped out of full status still
stepped, and a rebuild that read their blocks loaded the chunk again.*

#### CONT-009 · Removed segments release their gas
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: CORE-036

A removed pipe segment's share of gas shall be released into its cell
(CORE-053 if the cell is open).

- **Rationale:** vg: a removed vg pipe dumps its share on its tile ([vg
  §1](../research/vg-atmos-content.md#1-pipe-networks)).
- **Verify:** A GameTest removes a segment from a filled run and checks its
  cell gains the segment's share.

*2026-11-02: Implemented ([#47](https://github.com/west3436/zas-cubed/issues/47)).
A removed node adds its share at its position through the API.
`PipeTests.cont_003_breaking_a_pipe_conserves_gas` (GameTest) checks the
room gains the segment's share; `PipeNetworkTest.cont_009_*` (Unit) checks the
share.*

#### CONT-004 · Open ends leak
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: vg quirk 4, CORE-031

A pipe end with no connection shall exchange gas with its cell's atmosphere as
a one-connection edge does (sharing ratio 0.163 per second). vg disabled this.

- **Rationale:** Inferred: an open pipe end should leak, as in reality; vg's
  open-end mixing is commented out ([vg
  §1](../research/vg-atmos-content.md#1-pipe-networks)).
- **Verify:** A GameTest checks an open pipe end exchanges gas with its room
  at the one-connection ratio.

*2026-11-02: Implemented ([#47](https://github.com/west3436/zas-cubed/issues/47)).
A pipe with fewer than two connections has an open end; a pipe cap
(`zas_atmos:pipe_cap`) closes one and never leaks. An open end exchanges each
gas by ZAS's one-connection rule with its cell's zone, or with the ambient as
an infinite reservoir, and skips flows under 10⁻⁴ mol. After it loads, it waits
until its cell reads the same twice running ([architecture
§11.4](../architecture.md#114-pipe-networks)). `OpenEndTest` (Unit)
checks the formula; `PipeTests.cont_004_open_end_exchanges_at_the_one_connection_ratio`
and `cont_004_capped_pipe_does_not_leak` (GameTest) check it in a room.*

#### CONT-114 · Dyed pipes
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: [textures](../textures.md#content-mod-zas_atmos-blocks)

Using a dye on a pipe or pipe cap shall colour it, using up one dye outside
creative. The colour is cosmetic: it doesn't change what the pipe connects to.
A dyed pipe or cap, mined, shall drop an item of its colour, and a dyed item
shall place a pipe or cap of that colour.

- **Verify:** A GameTest dyes a pipe and a cap and checks the colour, the dye
  used and the connections. It mines dyed pipes and caps, checks each drops an
  item of its colour, and places those items, checking that the blocks are
  dyed.

*2026-11-02: added as Draft from the owner's choice of 2026-11-01 that players
dye pipes, which had no requirement. Whether differently dyed pipes should
stay apart, so parallel runs can share a wall, is left open. Built in
[#47](https://github.com/west3436/zas-cubed/issues/47) and checked by
`PipeTests.cont_114_a_dye_colours_a_pipe` (GameTest).*

*2026-11-02: owner changed mined dyed pipes to keep their dye, and accepted.
Previously a mined dyed pipe dropped a plain pipe, losing the dye it used.
The item carries the colour in the block state component, as the canister
item does. `PipeTests.cont_114_a_mined_dyed_pipe_keeps_its_dye` (GameTest)
checks the drops, the placing and picking the block. Implemented
([#152](https://github.com/west3436/zas-cubed/pull/152)).*

#### CONT-005 · Overpressure
`Could` · `R2` · `Deferred` — moved to R2 (lean scope; off by default anyway), 2026-10-30. · Verify: GameTest

Pipes may leak or burst above configurable pressure differentials.

*2026-10-31: bursting at a fixed threshold moved to R1 as CONT-108 (D-59).
Leaks and configurable thresholds stay here.*

#### CONT-108 · Pipe burst
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-59, D-57, [vg §1](../research/vg-atmos-content.md#1-pipe-networks) · Depends: CONT-100

When a pipe network's pressure exceeds a segment's cell pressure by more than
10,132.5 kPa (100 atm), one such segment, chosen at random, shall burst that
atmos tick: it is destroyed and the network's whole contents are dumped into
its cell. A burst doesn't explode. (vg's burst threshold is 100 atm, in code
that never runs.)

- **Rationale:** Decision: D-59.
- **Verify:** A GameTest raises a network to just below and just above 100 atm
  over its room and checks only the second bursts one segment and dumps
  everything, with no explosion.

#### CONT-006 · Network cadence
`Must` · `R1` · `Implemented` · Verify: Unit · Trace: D-31, D-20, D-50

Pipe networks and devices shall step once per atmos tick, on the server
thread.

- **Rationale:** Decision: D-31, D-20 and D-50.
- **Verify:** A Unit test checks networks and devices step exactly once per
  atmos tick, on the server thread.

*2026-11-02: Implemented ([#47](https://github.com/west3436/zas-cubed/issues/47)).
Devices and pipe segments are `AtmosDevice` block entities, stepped every
20 game ticks on the server thread, spread across ticks ([architecture
§11.2](../architecture.md#112-device-stepping-d-31-cont-006-cont-072)).
`DeviceScheduleTest.cont_006_*` (Unit) checks the timing;
`DeviceTests.cont_006_devices_step_once_per_atmos_tick` (GameTest) checks it
on the server thread. The API doesn't expose the framework's period, so the
content mod always steps every second; rates are per second, so behaviour
doesn't change with the period.*

*2026-11-02: Note: a device steps only while its chunk is loaded in full. A
chunk next to a loaded one that drops out of full status keeps its block
entities, which wait until it returns.
`PipeChunkTests.cont_006_a_vent_waits_while_its_chunk_is_away` (GameTest)
checks a vent there.*

#### CONT-070 · Client smoothing
`Must` · `R1` · `Accepted` · Verify: Manual

Device animations shall be smoothed on the client between steps.

- **Rationale:** Inferred: devices step once per second, so animations would
  look jerky unless the client interpolates.
- **Verify:** Manual check that pump and vent animations play smoothly.

#### CONT-071 · Zones through the public API
`Must` · `R1` · `Implemented` · Verify: Inspect, GameTest · Trace: D-50, API-010, API-016

Devices shall read zones through atmosphere queries (API-010) and change them
through asynchronous mutations (API-016).

- **Rationale:** Decision: D-50.
- **Verify:** Inspect the content mod's code for non-API engine access;
  GameTests of the devices exercise the calls.

*2026-11-02: Implemented ([#48](https://github.com/west3436/zas-cubed/issues/48)).
Inspect: devices read zones only with `Atmospheres.at` and change them only
through a `ZonePort`. The open pipe end, canister valve and vent GameTests
exercise the calls.*

#### CONT-072 · One outstanding mutation per port
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: D-50, CORE-036

A device port shall issue a new zone mutation only after its previous one has
completed, so decisions made on a stale snapshot never add or remove gas
twice.

- **Rationale:** Decision: D-50. A second mutation decided on the same stale
  snapshot would move gas twice (CORE-036).
- **Verify:** A Unit test checks a port waits for its future; a GameTest runs
  a vent and scrubber with the conservation checker on.

*2026-11-02: the vent, canister valve and open pipe end each issue their
changes through one `ZonePort` and wait while it is busy
([#48](https://github.com/west3436/zas-cubed/issues/48)); the GameTest with a
scrubber and the conservation checker waits for the scrubber.*

#### CONT-073 · No reactions in pipes
`Won't` · `R1` · `Rejected` — superseded by CONT-100 (D-57), 2026-10-31.

#### CONT-100 · Reactions in containers
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-57, D-50 · Depends: API-019, FIRE-031

Each atmos tick, every pipe network, canister and tank shall react its mixture
through the API (API-019), with no ignition source. A pipe network that
overpressures bursts (CONT-108).

- **Rationale:** Decision: D-57.
- **Verify:** A GameTest fills a pipe network, a canister and a tank with a
  mixture above autoignition and checks each reacts.

*2026-10-31: pipes now burst (CONT-108, D-59); they previously never burst in
R1.*

## Devices (R1 set)

All active devices consume FE as described under [Power](#power).

#### CONT-074 · Server-validated device GUIs
`Must` · `R1` · `Implemented` · Verify: GameTest, Inspect

Each device's configuration shall be set through a GUI whose inputs the
server validates.

- **Rationale:** Inferred: clients can send anything, so device settings are
  checked on the server, as with any multiplayer GUI.
- **Verify:** A GameTest sends out-of-range settings and checks they are
  clamped or refused; Inspect the packet handlers.

*2026-11-02: Implemented ([#48](https://github.com/west3436/zas-cubed/issues/48)).
The canister and vent GUIs send only button ids, through the game's own
menu-button packet; the server refuses unknown ids and clamps every setting
(`CanisterMenu`, `VentMenu`).
`GasDeviceTests.cont_074_out_of_range_settings_are_clamped_or_refused`
(GameTest). Later devices' GUIs follow the same pattern.*

*2026-11-02: The controller's and the generator's GUIs are built ([#50](https://github.com/west3436/zas-cubed/issues/50),
[#52](https://github.com/west3436/zas-cubed/issues/52)). The controller's buttons are menu button clicks: unknown ids are
refused and edits are clamped (`cont_074_gui_settings_are_checked_and_an_edited_preset_rates`).
The generator's only setting is its fuel slot, which takes only fuel. This
stays Accepted until every device's GUI is built.*

*2026-11-02: The vent's GUI also refuses every button while an area air
controller controls the vent (CONT-014), on the server, whatever the client
sends (`cont_014_a_controlled_vent_refuses_hand_settings`).*

#### CONT-010 · Valves
`Must` · `R1` · `Accepted` · Verify: GameTest

There shall be a manual valve and a redstone-controlled valve.

- **Rationale:** vg: vg's manual and digital valves ([vg
  §1](../research/vg-atmos-content.md#1-pipe-networks)); redstone is
  Minecraft's control signal.
- **Verify:** A GameTest opens and closes each valve by hand and by redstone.

#### CONT-075 · Open valves join networks
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: [vg §1](../research/vg-atmos-content.md#1-pipe-networks)

An open valve shall join the networks on both sides into one. Closing it
separates them, sharing gas by volume (CONT-003).

- **Rationale:** vg: an open vg valve joins both sides into one network ([vg
  §1](../research/vg-atmos-content.md#1-pipe-networks)).
- **Verify:** A GameTest opens a valve between networks at different
  pressures, checks they equalize as one, closes it and checks the split by
  volume.

#### CONT-011 · Pressure pump
`Must` · `R1` · `Accepted` · Verify: Unit · Trace: [vg §2.1](../research/vg-atmos-content.md#21-pumps-filters-and-mixers)

A pressure pump shall move the moles needed to bring its output to a target
pressure (0–4,500 kPa), counting the output buffer once (vg counts it twice).

- **Rationale:** vg: vg's pump rule and 4,500 kPa limit ([vg
  §2.1](../research/vg-atmos-content.md#21-pumps-filters-and-mixers)), without
  the double-counted buffer ([vg
  §6](../research/vg-atmos-content.md#6-config-knobs-fictional-gases-and-quirks),
  quirk 3).
- **Verify:** A Unit test checks the moles moved for given pressures, volumes
  and targets.

#### CONT-076 · Pressure pump throughput
`Must` · `R1` · `Accepted` · Verify: Unit · Trace: [vg §2.1](../research/vg-atmos-content.md#21-pumps-filters-and-mixers)

A pressure pump shall move at most 100 L/s, at input conditions.

- **Rationale:** vg: vg's pump is limited by one 200 L input buffer per 2 s
  tick ([vg
  §2.1](../research/vg-atmos-content.md#21-pumps-filters-and-mixers)), which
  is 100 L/s.
- **Verify:** A Unit test checks the moles moved per second never exceed 100 L
  at input conditions.

#### CONT-012 · Volume pump
`Should` · `R1` · `Accepted` · Verify: Unit · Trace: vg quirk 1

A volume pump shall move a set volume per second, up to 100 L/s, at the
input's conditions, and never more (vg's max-instead-of-min bug is fixed).

- **Rationale:** vg: vg's volume pump, with its max-instead-of-min bug fixed
  ([vg
  §6](../research/vg-atmos-content.md#6-config-knobs-fictional-gases-and-quirks),
  quirk 1).
- **Verify:** A Unit test checks the volume moved per second for settings
  below and above the limit.

#### CONT-013 · Filter
`Should` · `R1` · `Accepted` · Verify: Unit · Trace: [vg §2.1](../research/vg-atmos-content.md#21-pumps-filters-and-mixers)

A filter shall send selected gases or gas tags to a side outlet, up to a
target pressure of at most 4,500 kPa.

- **Rationale:** vg: vg's filter ([vg
  §2.1](../research/vg-atmos-content.md#21-pumps-filters-and-mixers)); tags
  replace its single gas, so datapack gases can be filtered (CORE-012).
- **Verify:** A Unit test checks selected gases leave by the side outlet up to
  the target.

#### CONT-077 · Mixer
`Should` · `R1` · `Accepted` · Verify: Unit · Trace: [vg §2.1](../research/vg-atmos-content.md#21-pumps-filters-and-mixers)

A mixer shall combine two inputs at set fractions, up to a target pressure of
at most 4,500 kPa.

- **Rationale:** vg: vg's mixer ([vg
  §2.1](../research/vg-atmos-content.md#21-pumps-filters-and-mixers)).
- **Verify:** A Unit test checks the output fractions and that both inputs
  scale together when one runs short.

#### CONT-014 · Vent
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-01 · [vg §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)

A vent shall blow gas into its zone, or siphon gas out of it, within an
external bound (default 101.325 kPa) and an internal bound, both 0–5,066 kPa.
In an unenclosed cell it vents to the ambient (CORE-053, CORE-057). It faces
any of six ways, and a pipe joins it only on its back, the face opposite its
grille.

- **Rationale:** vg: vg's vent pump and its bounds ([vg
  §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)).
- **Verify:** A GameTest checks a vent fills a room to the external bound and
  siphons down to it, and vents to the ambient in an open cell. It also checks
  that a pipe joins the vent's back and no other face, and that a vent an area
  air controller controls refuses a hand setting while one no controller
  controls takes it.
- **Changed 2026-11-01 (the owner, in the batch 1 art review):** the vent
  faces any of six ways and takes a pipe only on its back.
- **Changed 2026-11-02 (the owner, in the S-01 integration review,
  [#160](https://github.com/west3436/zas-cubed/pull/160)):** the controller
  wins, and the GUI says so. A vent an area air controller covers keeps taking
  the controller's orders. Its GUI shows the controller's position and its
  current order, its own settings are disabled, and the server refuses hand
  settings for it (CONT-074). A vent no controller covers is still set by hand.

*2026-11-02: Implemented ([#48](https://github.com/west3436/zas-cubed/issues/48)).
`zas_atmos:vent` blows into, or siphons from, the cell in front of its grille,
within its external and internal bounds (`VentSettings`, clamped to
0–5,066 kPa), and in an open cell into or from the ambient ([architecture
§11.5](../architecture.md#115-devices)). `VentFlowTest` (Unit) checks the
bounds; `GasDeviceTests.cont_014_a_vent_fills_a_room_to_the_external_bound`,
`cont_014_a_vent_siphons_a_room_to_the_external_bound`,
`cont_014_a_vent_vents_to_the_ambient_in_an_open_cell` and
`cont_014_a_pipe_joins_a_vent_only_on_its_back` (GameTest) check the Verify.
The vent runs only while powered (CONT-060).*

*2026-11-02: The owner's change above is built. A controller's order
(`VentBlockEntity.control`) makes the vent controlled for two atmos ticks, and
a controller renews that every atmos tick. While controlled, `setByHand`
refuses settings, so the GUI's buttons do nothing, and the GUI shows the
controller and its order with its buttons off.
`GasDeviceTests.cont_014_a_controlled_vent_refuses_hand_settings` (GameTest)
checks it with `control` called directly; [#160](https://github.com/west3436/zas-cubed/pull/160) checks it with a real
controller.*

*2026-11-02: Note: in the owner's S-01 test, a vent siphoning into a network
with a full canister on its port moved nothing, because the network was above
the internal bound. That is what this requirement asks for, but the GUI didn't
show why. The GUI now shows the network's pressure, and a status line: moving
gas, or idle and why (the network or the room has reached its bound, either is
empty, or the vent is waiting for the air in front to settle).
`VentFlowTest.cont_014_an_idle_vent_says_why` (Unit) and
`GasDeviceTests.cont_014_an_idle_vent_says_why` (GameTest) check each reason.*

#### CONT-078 · Vent rate
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: [vg §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)

A vent shall move at most half its buffer's moles per second, so it can't fill
a room in one tick as vg's does.

- **Rationale:** Inferred: vg's vent can fill a whole room in one tick ([vg
  §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)), which looks
  wrong and makes airflow spike; half the buffer per second spreads it out.
- **Verify:** A Unit test checks the moles moved per second; a GameTest checks
  a room fills over several ticks.

*2026-11-02: Implemented ([#48](https://github.com/west3436/zas-cubed/issues/48)).
A vent moves at most half a 400 L buffer's moles a second: of its network
when blowing, and of its zone when siphoning (`VentFlow`).
`VentFlowTest.cont_078_*` (Unit); the `GasDeviceTests.cont_014_*` fill and
siphon tests (GameTest) check a room takes at least four atmos ticks.*

#### CONT-015 · Scrubber
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-02

A scrubber shall remove selected gases or gas tags from its zone. The default
selection is CO₂ and `zas:toxic`. It faces any of six ways, and a pipe joins
it only on its back, the face opposite its grille.

- **Rationale:** vg: vg's scrubber ([vg
  §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)); CO₂ and
  toxic gases replace vg's CO₂ and plasma.
- **Verify:** A GameTest checks a scrubber removes CO₂ and CO from a room and
  leaves O₂ and N₂. It also checks that a pipe joins the scrubber's back and
  no other face.
- **Changed 2026-11-01 (the owner, in the batch 2 art review):** the scrubber
  is shaped like the vent: it faces any of six ways and takes a pipe only on
  its back.

#### CONT-079 · Scrubber modes
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: [vg §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)

A scrubber shall have three modes: scrubbing (keeps only the selected gases),
siphon (keeps its whole sample) and panic (siphon at the panic rate).

- **Rationale:** vg: vg's scrubbing, siphon and panic modes ([vg
  §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)).
- **Verify:** A GameTest checks each mode's effect on a mixed room.

#### CONT-080 · Scrubber rate
`Must` · `R1` · `Accepted` · Verify: Unit · Trace: [vg §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)

A scrubber shall sample 500 L/s of its zone (1,000 L/s in panic).

- **Rationale:** vg: vg's scrubber sample rates ([vg
  §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)), converted to
  per second.
- **Verify:** A Unit test checks the sampled volume per second in normal and
  panic modes.

#### CONT-081 · Scrubber stall
`Must` · `R1` · `Accepted` · Verify: Unit · Trace: [vg §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)

A scrubber shall stop once its buffer reaches 50 atm.

- **Rationale:** vg: vg's 50 atm scrubber stall ([vg
  §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)).
- **Verify:** A Unit test checks a scrubber stops at 50 atm in its buffer.

#### CONT-016 · Passive vent
`Could` · `R1` · `Accepted` · Verify: GameTest · Trace: [vg §2](../research/vg-atmos-content.md#2-devices)

A passive vent shall equalize its buffer with the room when the pressure
difference exceeds 0.5 kPa, at the one-connection sharing ratio (CORE-031).
It faces any of six ways, and a pipe joins it only on its back, the face
opposite its grille.

- **Rationale:** vg: vg's passive vent, which equalizes above 0.5 kPa ([vg
  §2](../research/vg-atmos-content.md#2-devices)).
- **Verify:** A GameTest checks a passive vent equalizes with a room only
  above the threshold. It also checks that a pipe joins the passive vent's
  back and no other face.
- **Changed 2026-11-02 (the owner, in the batch 5 art review):** the passive
  vent is shaped like the vent: it faces any of six ways and takes a pipe
  only on its back.

#### CONT-082 · Injector
`Could` · `R2` · `Deferred` — moved to R2 (lean scope; the vent covers it), 2026-10-30. · Verify: GameTest

A fixed-rate injector shall push gas into its zone regardless of room pressure.

#### CONT-017 · Canister
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-01 · [vg §2.3](../research/vg-atmos-content.md#23-canisters-and-portables)

A canister shall be a 1,000 L block rated at 9,120 kPa (90 atm) that keeps its
contents when mined.

- **Rationale:** vg: vg's 1,000 L canister with a 90 atm limit ([vg
  §2.3](../research/vg-atmos-content.md#23-canisters-and-portables)); keeping
  contents when mined is the Minecraft equivalent of moving it.
- **Verify:** A GameTest mines a filled canister, places it again and checks
  the contents are unchanged.
- **Changed 2026-11-02 (the owner, in the S-01 integration review,
  [#160](https://github.com/west3436/zas-cubed/pull/160)):** a canister broken
  in creative drops nothing, and its gas is destroyed with it, not released.

*2026-11-02: Implemented ([#48](https://github.com/west3436/zas-cubed/issues/48)).
`zas_atmos:canister` is a 1,000 L network node rated at 9,120 kPa
(`CanisterBlockEntity.RATED_KPA`; rupture is CONT-085, not built). Mined with a
pickaxe it drops an item carrying its gas (`zas_atmos:gas`) and its paint;
removed any other way it releases its gas, as a pipe does (CONT-009).
`GasDeviceTests.cont_017_a_mined_canister_keeps_its_contents` and
`cont_017_a_canister_removed_without_a_drop_releases_its_gas` (GameTest).*

*2026-11-02: the owner's change is built. Broken in creative, the canister's
gas is destroyed. `GasDeviceTests.cont_017_a_canister_broken_in_creative_destroys_its_gas`
(GameTest) checks that the room gains nothing.*

#### CONT-083 · Connector ports
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-01

Placing a canister against a connector port shall attach it, joining the
port's network. The port faces up, so the canister stands on it, and a pipe
joins the port only on its bottom, the face opposite.

- **Rationale:** vg: vg canisters attach to connector ports and join the
  network ([vg
  §2.3](../research/vg-atmos-content.md#23-canisters-and-portables)).
- **Verify:** A GameTest places a canister on a port and checks the network
  includes it. It also checks that a pipe joins the port's bottom and no
  other face.
- **Changed 2026-11-01 (the owner, in the batch 1 art review):** the port
  faces up and takes a pipe only on its bottom.

*2026-11-02: Implemented ([#48](https://github.com/west3436/zas-cubed/issues/48)).
`zas_atmos:connector_port` joins a pipe below it and a canister standing on
it; its `connected` state shows the collar.
`GasDeviceTests.cont_083_a_canister_on_a_port_joins_its_network` (GameTest).
Placing the canister attaches it; the wrench (CONT-035) isn't built.*

#### CONT-084 · Canister release
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: vg quirk 10

A canister shall release through a valve at a set release pressure
(10–1,013 kPa) into the room or a held tank, computed against the receiving
volume: the zone's real volume, not vg's fixed 2,500 L.

- **Rationale:** vg: vg's release valve and its 10–1,013 kPa range ([vg
  §2.3](../research/vg-atmos-content.md#23-canisters-and-portables)); its
  fixed 2,500 L receiving volume is replaced with the real volume.
- **Verify:** A Unit test checks the moles released against the formula; a
  GameTest checks a canister fills a room to the release pressure.

*2026-11-02: Implemented ([#48](https://github.com/west3436/zas-cubed/issues/48)).
An open valve releases the least of what brings the zone to the release
pressure, by the zone's real volume, and what equalises the canister and the
zone (`CanisterRelease`); the release pressure is clamped to 10–1,013 kPa.
`CanisterReleaseTest` (Unit);
`GasDeviceTests.cont_084_a_canister_fills_a_room_to_the_release_pressure`
(GameTest). Releasing into a held tank waits for held tanks, which R1 doesn't
have.*

#### CONT-085 · Canister rupture
`Must` · `R1` · `Accepted` · Verify: GameTest

A canister caught in an explosion, or above 1,273 K, shall rupture and dump
its contents into its cell.

- **Rationale:** vg: vg canisters dump everything when broken, and fire above
  1,273 K damages them ([vg
  §2.3](../research/vg-atmos-content.md#23-canisters-and-portables)).
- **Verify:** GameTests check a canister caught in an explosion and one heated
  above 1,273 K both rupture into their cell.

#### CONT-101 · Canister overpressure rupture
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-57 · Depends: CONT-085

A canister above its 9,120 kPa (90 atm) rating shall rupture as in CONT-085.

- **Rationale:** Decision: D-57.
- **Verify:** A GameTest checks a canister ruptures just above 90 atm and not
  below.

#### CONT-102 · Canister explosion
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-57, CONT-038

A canister above 150 % of its rating (13,680 kPa, 135 atm) shall explode
instead, as a vanilla explosion of power (P − 135 atm) / 10 atm, releasing its
contents into its cell.

- **Rationale:** Decision: D-57 and D-59.
- **Verify:** A GameTest raises a canister from below 90 atm to above 135 atm
  in one tick and checks the explosion power.

*2026-10-31: threshold and power confirmed; the power is capped by CONT-109
(D-59).*

#### CONT-086 · Filled canisters are creative-only
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: CONT-050

Canisters pre-filled with each built-in gas shall exist only in creative.
Survival canisters are crafted empty.

- **Rationale:** Inferred: pre-filled canisters in survival would skip gas
  acquisition (CONT-050); creative players need them for building and tests.
- **Verify:** Inspect the creative tab and recipes.

*2026-11-02: the creative tab has a canister of each of the 8 gases at
45 atm and 293.15 K, in the gas's colour
([#48](https://github.com/west3436/zas-cubed/issues/48)), and no recipe makes a
filled one. Survival recipes, the empty canister's included, aren't written
yet.*

#### CONT-018 · Portable pump and scrubber
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-69

The portables, a portable pump, a portable scrubber, a heater and a freezer
(CONT-019), shall be blocks that run on a battery in a slot (CONT-112) and
work on their own on the zone they stand in. No area air controller shall
power, order or list them. The portable pump and scrubber shall also work on
a held tank.

- **Rationale:** vg: vg's portable pump and scrubber, which act on the room or
  a held tank ([vg
  §2.3](../research/vg-atmos-content.md#23-canisters-and-portables)); the
  battery follows D-69, and the heater and freezer joining them D-73.
- **Verify:** A GameTest checks each works on a room and on a held tank, and
  stops when its battery is empty, and that a controller in its room doesn't
  power or list it.
- **Changed 2026-11-02 (the owner, reviewing
  [#177](https://github.com/west3436/zas-cubed/pull/177)):** "heaters, air conditioners,
  scrubbers, and pumps are all portable devices on battery power which
  shouldn't link with any AACs; they should simply work independently on
  battery power to modify the zone they occupy." The heater and the freezer
  (the air conditioner) are portables too, and no portable is a
  controller's part (D-73). Wall-mounted scrubbers stay controller parts
  (CONT-015).

*2026-11-01: the portables run on a battery instead of their own FE buffer,
whose charging was left open by D-68 (D-69).*

*2026-11-02: Built for the heater only (CONT-019), so this stays Accepted
until the portable pump, portable scrubber and freezer exist.*

#### CONT-019 · Heater and freezer
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-05, D-73, S-05

A heater (293–573 K) and a freezer (73–293 K) shall be portables
(CONT-018), each running on a battery in a slot (CONT-112), that hold the
zone they stand in at a setpoint. A heater shall add heat to that zone, and a
freezer take heat from it, until the zone reaches the setpoint, and neither
shall take it past the setpoint. With no zone where it stands, it shall do
nothing.

- **Rationale:** vg: vg's heater and freezer ranges ([vg
  §2](../research/vg-atmos-content.md#2-devices)). Portables on batteries:
  D-73.
- **Verify:** A GameTest checks a heater on a battery holds a room at its
  setpoint, within 0.5 K and never above it, and stops when its battery is
  empty; and that a freezer holds a room at its setpoint, never below it.
- **Changed 2026-11-02 (the owner, reviewing
  [#177](https://github.com/west3436/zas-cubed/pull/177)):** the heater
  regulates the room. Its setpoint is the temperature of the zone its air
  point is in. Each atmos tick it reads that zone's temperature and heats its
  network gas as hot as needed, up to 573 K, until the room reaches the
  setpoint, and adds nothing at or above it, so it doesn't overshoot or
  oscillate. When its air point isn't zoned (open air, unresolved or
  paused), it moves its own gas toward the setpoint. Its GUI shows the room's
  temperature beside the setpoint. The freezer was not part of this change.
- **Changed 2026-11-02 (the owner, reviewing
  [#177](https://github.com/west3436/zas-cubed/pull/177), again):** the heater and the
  freezer, which is the air conditioner, are portables on battery power that
  link with no area air controller and work on their own on the zone they
  stand in (D-73). The heater has no pipe: it heats its room directly, and
  does nothing with no zone where it stands. This replaces the piped heater
  of the change above, which heated its network's gas for vents to carry in.

*2026-11-02: Built for the heater only, so this stays Accepted; the freezer
is out of this round's scope (S-05 needs only the heater). The heater adds
the heat that brings its room to the setpoint, up to `[power] heater_watts`,
through the heat mutation (API-013), and holds a wool room in vacuum within
about 0.02 K under it (architecture §11.5). Checked by
`cont_087_never_more_than_5_kw` (Unit),
`cont_019_heater_heats_its_room_on_its_battery`,
`cont_019_a_controller_leaves_a_heater_in_its_room_alone` and
`s_05_heater_holds_a_room_at_its_setpoint` (GameTest).*

#### CONT-087 · Thermomachine power
`Should` · `R1` · `Accepted` · Verify: Unit

A heater or freezer shall move up to 5 kW toward its setpoint, drawing
60 FE/t from its battery while it works.

- **Rationale:** Inferred: vg's thermomachines have no real power limit ([vg
  §2](../research/vg-atmos-content.md#2-devices)); 5 kW is a gameplay value
  that warms a 27-block room by about 20 K in under 2 minutes.
- **Verify:** A Unit test checks the heat moved per second never exceeds 5 kW.
- **Changed 2026-11-02 (the owner, reviewing
  [#177](https://github.com/west3436/zas-cubed/pull/177)):** 5 kW stays, as
  the default of a setting, `[power] heater_watts`. For rooms that lose more
  than a heater can make up, such as stone in vacuum, the owner asked for
  insulating walls instead: station walls and reinforced station walls
  ([#179](https://github.com/west3436/zas-cubed/issues/179)).
- **Changed 2026-11-02 (the owner, reviewing
  [#177](https://github.com/west3436/zas-cubed/pull/177), again):** the draw is from the
  heater's or freezer's own battery, not a controller (D-73).

*2026-11-02: Built for the heater only, so this stays Accepted until the
freezer exists. The heater moves at most `[power] heater_watts`, 5,000 W, and
draws from its battery only while it heats: `[power] heater`, 60 FE/t, at
full power, and in proportion below it. With too little charge it adds what
the charge pays for, then stops. Checked by `cont_087_never_more_than_5_kw`,
`cont_087_limited_or_exact` and `cont_087_draw_in_proportion` (Unit) and
`cont_019_heater_heats_its_room_on_its_battery` (GameTest).*

#### CONT-105 · Igniter
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-57, FIRE-014, FIRE-016

A placeable igniter block shall, while it receives a redstone signal and is
powered, be an ignition source (FIRE-014) in its cell. A flammable mixture in
its zone therefore ignites and spreads (FIRE-016). It draws 20 FE/t while
active (CONT-065). (vg's igniter is a floor machine that exposes its tile to a
1,000 K flame while switched on.)

- **Rationale:** Decision: D-57 and D-59.
- **Verify:** A GameTest checks a powered igniter given a signal lights an
  H₂–air zone, and that it doesn't without power or signal.

*2026-10-31: draw raised from 10 to 20 FE/t (D-59).*

## Wearables and tools

#### CONT-030 · Gas tanks
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: LIFE-021

Gas tank items shall hold real mixtures, in these types:

| Tank | Volume | Filled |
|---|---|---|
| O₂ tank | 70 L | about 6 atm (17.5 mol) |
| Emergency O₂ tank | 2 L | 3 atm |
| Air tank | 70 L | about 6 atm |
| Empty tank | 70 L | – |

- **Rationale:** vg: vg's O₂ and emergency tanks ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)); the air and
  empty tanks are inferred.
- **Verify:** A GameTest checks each tank's volume and filled contents.

#### CONT-038 · Tank failure
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: [vg §4](../research/vg-atmos-content.md#4-mob-interaction-humans)

A tank shall leak above 30 atm, rupture above 40 atm, and explode above 50 atm
as a vanilla explosion of power (P − 50 atm) / 10 atm.

- **Rationale:** vg: vg's tank thresholds and explosion range ([vg
  §4](../research/vg-atmos-content.md#4-mob-interaction-humans)).
- **Verify:** GameTests take a tank to each threshold and check it leaks,
  ruptures and explodes with the formula's power.

*2026-10-31: whether the explosion breaks blocks follows the preset
(CONT-107). Added with D-57.*

*2026-10-31: the power is capped by CONT-109 (D-59).*

#### CONT-107 · Container explosions and blocks
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-57, D-28, D-14

Explosions of tanks (CONT-038) and canisters (CONT-102) shall break blocks
only under the Hardcore preset. Under Normal they damage entities but leave
blocks intact. Either preset can override it (OPS-007).

- **Rationale:** Decision: D-57, D-28 and D-14.
- **Verify:** GameTests explode a tank under each preset and check blocks
  break only under Hardcore.

#### CONT-109 · Container explosion power cap
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-59 · Depends: CONT-038, CONT-102

The power of a tank (CONT-038) or canister (CONT-102) explosion shall be capped
at 16, configurable (TNT is 4).

- **Rationale:** Decision: D-59.
- **Verify:** A GameTest explodes a tank at a pressure whose formula power
  exceeds 16 and checks the power used is 16.

#### CONT-103 · Tank transfer valve
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-57, S-13

A handheld tank transfer valve shall hold two gas tanks (CONT-030) joined by a
valve. Opening the valve moves all of the first tank's gas into the second;
closing it shares the gas back by volume, as in vg. The merged mixture reacts
in its tank (CONT-100), so a hot oxidizer mixed with fuel above its
autoignition temperature (FIRE-010) burns, and the tank may fail (CONT-038).
It's an item until its timer starts, and an entity once primed (CONT-104).

- **Rationale:** Decision: D-57 (S-13).
- **Verify:** A GameTest opens and closes a TTV and checks the gas moves and
  shares back by volume, and that a hot mixture burns in the tank.
- **Changed 2026-11-02 (the owner, in the batch 3 art review):** a TTV is an
  entity only once primed, like primed TNT (CONT-104).

#### CONT-104 · TTV timer
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-57, S-13 · Depends: CONT-103

A TTV's valve shall open by hand, or when a timer set from 0 to 600 s
(default 10 s) runs out. Starting the timer primes the TTV: it becomes an
entity in the world, like primed TNT, that no one can pick up or use, and its
valve opens when the timer runs out. At 0 s the valve opens as soon as the
timer starts, as in vg. vg's signaler and proximity triggers are R2.

- **Rationale:** Decision: D-57 and D-59.
- **Verify:** GameTests check the valve opens by hand, at 0 s, and when a
  primed TTV's timer runs out, and that a primed TTV can't be picked up or
  used.
- **Changed 2026-11-02 (the owner, in the batch 3 art review):** a primed TTV
  is an entity, like primed TNT, so the timer no longer counts down in an
  inventory or as a dropped item.

*2026-10-31: the range now starts at 0 s, as in vg (D-59).*

#### CONT-039 · Tank filling
`Must` · `R1` · `Accepted` · Verify: GameTest

A tank shall be filled from a canister holding it (CONT-084).

- **Rationale:** Inferred: tanks need a refill path in survival, and the
  canister is the natural one.
- **Verify:** A GameTest checks a held tank fills from a canister to the
  release pressure.

#### CONT-031 · Breath mask and helmets
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: LIFE-021

A breath mask and suit helmets shall provide sealed breathing for internals.

- **Rationale:** Decision: D-47.
- **Verify:** A GameTest checks internals work with a breath mask and with
  each suit helmet.

#### CONT-088 · Gas mask
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: LIFE-021, LIFE-026

A gas mask shall provide sealed breathing and filter `zas:toxic` gases (CO,
N₂O) out of the breath. It supplies no O₂, and its filter never wears out in
R1.

- **Rationale:** Decision: D-47. A never-wearing filter keeps R1 lean.
- **Verify:** A GameTest checks a gas-masked player in a CO zone takes no CO
  harm but still suffers hypoxia without O₂.

#### CONT-032 · Softsuit
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-01, S-02, D-47

A four-piece softsuit shall provide a full seal (its helmet supports
internals), low-pressure protection down to vacuum, and thermal protection
from 0 to 500 K. It gives no high-pressure protection.

- **Rationale:** Decision: D-47.
- **Verify:** GameTests check a suited player in vacuum, at 450 K and at 600
  kPa: protected, protected, unprotected.

#### CONT-036 · Hardsuit
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-47

A four-piece hardsuit shall provide a full seal and protection against all
pressures and temperatures.

- **Rationale:** Decision: D-47.
- **Verify:** GameTests check a hardsuited player in vacuum, at 2,000 kPa and
  at 1,500 K is protected.

#### CONT-089 · Hardsuit encumbrance
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-47

Each hardsuit piece worn shall reduce movement speed by 10 % (configurable).

- **Rationale:** Decision: D-47.
- **Verify:** A GameTest checks movement speed with one to four pieces.

#### CONT-090 · Suit tank slot
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: LIFE-028

Softsuit and hardsuit chestplates shall each have a tank slot.

- **Rationale:** Inferred: suits need somewhere to carry a tank, standing in
  for vg's suit storage (LIFE-028).
- **Verify:** A GameTest checks internals draw from a tank in each
  chestplate's slot.

#### CONT-033 · Magnetic boots
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: FLOW-012

Magnetic boots shall toggle on and off with a keybind, giving airflow
resistance 1 while on.

- **Rationale:** vg: vg's magboots stop airflow; a keybind toggles them as
  LIFE-029 does internals.
- **Verify:** A GameTest checks magnetic boots stop airflow while on and not
  while off.

#### CONT-034 · Gas analyzer
`Must` · `R1` · `Accepted` · Verify: Manual

A handheld analyzer shall report the composition, pressure and temperature
of:
- a point (API-010)
- a pipe network
- a canister
- a tank

- **Rationale:** vg: vg's handheld gas analyzer; it shows the API query
  (API-010) for each kind of target.
- **Verify:** Manual check of the readout for each target against debug
  values.

#### CONT-035 · Wrench
`Should` · `R1` · `Accepted` · Verify: GameTest

A wrench shall rotate and remove atmos parts.

- **Rationale:** vg: vg's wrench rotates and removes atmos parts.
- **Verify:** A GameTest checks a wrench rotates and removes a pipe and a
  device.

#### CONT-091 · Wrench pressure lock
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: [vg §1](../research/vg-atmos-content.md#1-pipe-networks)

The wrench shall refuse to remove a part holding more than 2 atm above ambient
until it has been vented.

- **Rationale:** vg: vg refuses to unwrench above 2 atm over ambient ([vg
  §1](../research/vg-atmos-content.md#1-pipe-networks)).
- **Verify:** A GameTest checks a part at 3 atm over ambient refuses removal
  and comes off once vented.

#### CONT-037 · Fire extinguisher
`Could` · `R2` · `Deferred` — recorded during the FIRE review; R1 relies on removing O₂ and vanilla water, 2026-10-30. · Verify: GameTest

A handheld extinguisher shall put out hotspots and fire blocks it is aimed at.

## Area air controllers and firelocks

The area air controller takes the place of vg's air alarm (D-68). It also
powers the parts around it (see [Power](#power)).

#### CONT-040 · Area air controller
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: S-02, D-68 · [vg §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)

An area air controller shall sample its zone each atmos tick and rate each
reading against its preset's (min2, min1, max1, max2): at or beyond min2 or
max2 is danger 2, at or beyond min1 or max1 is danger 1. Readings are pressure,
temperature, each listed gas, and all other gases combined.

- **Rationale:** vg: vg's air alarm sampling and thresholds ([vg
  §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)).
- **Verify:** A GameTest steps a zone's readings through each band and checks
  the danger level.

*2026-11-01: the air alarm is renamed the area air controller (D-68).*

*2026-11-02: Implemented ([#50](https://github.com/west3436/zas-cubed/issues/50)). The controller samples the middle of its own
block each atmos tick and rates pressure, temperature, each listed gas and the
other gases summed (architecture §11.6). Nitrogen is listed with no bounds, so
the air's filler gas isn't counted among the other gases. An open cell's
ambient air is rated too. `cont_040_danger_follows_the_readings` steps a
zone's CO₂ through each band; `ControllerLogicTest` checks every reading and
both bounds of each band.*

#### CONT-046 · Danger hysteresis
`Must` · `R1` · `Implemented` · Verify: Unit · Trace: [vg §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors), D-20

A controller's danger level shall rise immediately and fall only after 10 s of
lower readings.

- **Rationale:** vg: vg needs 5 lower readings (10 s) before danger falls ([vg
  §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)).
- **Verify:** A Unit test checks danger rises at once and falls only after 10
  s.

*2026-11-01: the air alarm is renamed the area air controller (D-68).*

*2026-11-02: Implemented ([#50](https://github.com/west3436/zas-cubed/issues/50)). The level falls after 10 readings in a row
below it, one per atmos tick, to the highest of those readings
(`DangerLevel`; `cont_046_danger_rises_at_once_and_falls_after_10_s`).*

#### CONT-047 · Controller presets
`Must` · `R1` · `Implemented` · Verify: Inspect, GameTest · Trace: [vg §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors), LIFE-041, FIRE-003

Area air controllers shall ship an editable Human preset and a Vacuum preset.
Human (min2 / min1 / max1 / max2):

| Reading | Thresholds |
|---|---|
| O₂ | 16 / 18 / 135 / 140 kPa |
| CO₂ | – / – / 5 / 10 kPa |
| N₂O | – / – / 0.5 / 1 kPa |
| CO | – / – / 0.005 / 0.12 kPa |
| H₂ | – / – / 1 / 2 kPa (half the lower flammable limit) |
| CH₄ | – / – / 1 / 2.5 kPa (half the lower flammable limit) |
| Other gases | – / – / 0.5 / 1 kPa |
| Pressure | 81 / 91 / 111 / 122 kPa |
| Temperature | 243 / 273 / 313 / 343 K |

Vacuum sets pressure max1 / max2 to 1 / 5 kPa.

- **Rationale:** vg: vg's Human and Vacuum presets ([vg
  §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors));
  the CO, H₂ and CH₄ rows are inferred from LIFE-041 and half the lower
  flammable limits (FIRE-003).
- **Verify:** Inspect the presets; a GameTest checks an edited preset changes
  the controller's ratings.

*2026-11-01: the air alarm is renamed the area air controller (D-68).*

*2026-11-02: Implemented ([#50](https://github.com/west3436/zas-cubed/issues/50)). Each controller holds its own copy of a
preset, edited from its GUI; loading a shipped preset discards the edits.
Vacuum keeps Human's gas maxima, sets pressure to –/–/1/5 kPa and has no O₂
minimum and no temperature bounds, which would always trip in an empty room.
`cont_047_presets_hold_the_table` checks the table, and
`cont_074_gui_settings_are_checked_and_an_edited_preset_rates` checks an edit
changes the rating.*

#### CONT-041 · Controller modes
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: [vg §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)

The area air controller shall command its vents and scrubbers in these modes:

| Mode | Scrubbers | Vents |
|---|---|---|
| Scrubbing | Scrub the preset's gases | Blow to the middle of the safe band (101 kPa for Human) |
| Replacement | Panic | Blow to target |
| Panic | Panic | Off |
| Cycle | Panic until below 5 % of 1 atm, then switch to Fill | Off |
| Fill | Off | Blow to target; switch to Scrubbing at 95 % of target |
| Off | Off | Off |

- **Rationale:** vg: vg's alarm modes ([vg
  §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)).
- **Verify:** GameTests set each mode and check the vents and scrubbers follow
  it.

*2026-11-01: the air alarm is renamed the area air controller (D-68).*

*2026-11-02: The controller's side is built ([#50](https://github.com/west3436/zas-cubed/issues/50)): each mode's vent and
scrubber orders, and Cycle's and Fill's switches (`ControllerMode`,
`VentControl`, `ScrubberControl`). `cont_041_modes_command_vents_and_scrubbers`
checks them with stand-in parts. This stays Accepted until the vent (#48) and
scrubber follow the orders.*

*2026-11-02: The vent half passes with the real vent ([#160](https://github.com/west3436/zas-cubed/pull/160)):
it implements `VentControl`, and `StationTests.cont_069_a_real_vent_runs_only_on_a_controller` (GameTest) checks that the vent
follows Scrubbing, Off, Panic and Replacement, and moves nothing while ordered
off. This stays Accepted until the scrubber follows its orders.*

#### CONT-048 · Controller redstone
`Must` · `R1` · `Implemented` · Verify: GameTest

An area air controller shall emit a redstone signal of 7 at danger 1 and 15 at
danger 2.

- **Rationale:** Inferred: redstone lets players build their own responses to
  danger, at two strengths for the two danger levels.
- **Verify:** A GameTest checks signal strengths 0, 7 and 15 at each danger
  level.

*2026-11-01: the air alarm is renamed the area air controller (D-68).*

*2026-11-02: Implemented ([#50](https://github.com/west3436/zas-cubed/issues/50)). The signal goes to every side, and strongly
into the wall behind the panel, as a lever's does
(`cont_040_danger_follows_the_readings`).*

#### CONT-042 · Room model
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-32, D-68

By default, an area air controller shall control the devices in its zone. The
multitool can also link parts to it by hand, for example across a firelock.

- **Rationale:** Decision: D-32 and D-68.
- **Verify:** A GameTest checks a controller controls the devices in its zone
  and a device linked with the multitool across a firelock.

*2026-11-01: the air alarm is renamed the area air controller, and the linking
tool is the multitool (D-68). A link also powers the part (CONT-068).*

*2026-11-02: Built ([#50](https://github.com/west3436/zas-cubed/issues/50)): a controller covers the loaded parts whose air
point is in its zone, and the parts linked to it with the multitool, which
`cont_068_controller_powers_its_zone_and_its_links` checks with stand-in parts
across a stone wall. This stays Accepted until it is checked across a
firelock (CONT-043).*

#### CONT-110 · Controller forms its zone
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-68, D-13, S-01 · Depends: API-050, CONT-042

When placed, an area air controller shall pin the zone at the cell it samples
(API-050), forming one there if that cell's region is enclosed and has no zone,
and it shall unpin the zone when removed. In an open region no zone forms.
While its cell has no zone, it shall form one as soon as the region becomes
enclosed, for example when the last wall of its room goes up.

- **Rationale:** Decision: D-68. A sealed room has no zone until its air
  changes (D-13, CORE-051), so without this a vent in a new room would have no
  power to start filling it (CONT-068, S-01). The pin stops a zone that holds
  only ambient air from being dissolved (CORE-052).
- **Verify:** GameTests place a controller in a sealed vacuum room and in a
  sealed Overworld room and check a zone forms and outlasts CORE-052's delay,
  that none forms in the open, that a controller placed in an unfinished room
  forms one once the room is sealed, and that removing the controller unpins
  the zone.

*2026-11-01: Accepted by the owner, with one addition: a controller placed
before its room is sealed forms the zone once the room is sealed, so a player
needn't build the walls first or break and replace the controller. The Verify
gains that case.*

*2026-11-02: Implemented ([#50](https://github.com/west3436/zas-cubed/issues/50)). The controller pins the zone at the middle
of its own block under the owner `zas_atmos:area_air_controller/<x>_<y>_<z>`.
It pins on its first step after loading and again whenever that point's zone
isn't the one it last pinned, so an open room is tried once a second until it
is sealed. Removing the block unpins; a chunk unloading doesn't. The
`cont_110_` GameTests cover a sealed Overworld room, a vacuum room (the End
with the test vacuum ambient), a room sealed after the controller went in, and
removal. CORE-052's timed collection isn't built yet, so "outlasts" is checked
against an opened room, which dissolves an unpinned zone at once.*

#### CONT-049 · Controller device list
`Must` · `R1` · `Accepted` · Verify: Manual

The controller's GUI shall list every device it controls and show whether each
came from the zone or from a link.

- **Rationale:** Inferred: players need to see what a controller controls to
  debug their station.
- **Verify:** Manual check of the list against the devices in the zone and the
  links.

*2026-11-01: the air alarm is renamed the area air controller (D-68).*

*2026-11-02: Built ([#50](https://github.com/west3436/zas-cubed/issues/50)). The GUI lists each part with its position, whether
it is in the zone, linked or both, and whether it has power. This stays
Accepted until the manual check.*

#### CONT-043 · Firelock
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-02, GEO-007

A firelock shall be a zone-boundary door (tagged `zas:zone_boundary`) that
closes when any area air controller covering either side reaches danger 2. A
controller covers a side if its zone or its links include the cell on that
side.

- **Rationale:** vg: vg firedoors close when an alarm on either side reaches
  danger 2 ([vg
  §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)).
- **Verify:** A GameTest raises a controller to danger 2 on each side and
  checks the firelock closes.

*2026-11-01: the air alarm is renamed the area air controller (D-68).*

#### CONT-092 · Firelock reopening
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: [vg §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)

A firelock closed by a controller shall reopen once no controller covering
either side is at danger 2.

- **Rationale:** vg: vg firedoors reopen when the alarm drops ([vg
  §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)).
- **Verify:** A GameTest checks the firelock reopens once both sides fall
  below danger 2.

*2026-11-01: the air alarm is renamed the area air controller (D-68).*

#### CONT-093 · Firelock lockdown
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: [vg §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)

A closed firelock shall lock down while the pressure difference across it is
at least 25 kPa, or either side is at least 323.15 K (50 °C) or at most
273.15 K (0 °C). Locked down, it refuses to open by hand and shows a warning.

- **Rationale:** vg: vg's lockdown at 25 kPa, 50 °C or 0 °C ([vg
  §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)).
- **Verify:** GameTests check lockdown at each threshold and that a locked
  firelock refuses to open by hand.

#### CONT-094 · Forcing firelocks
`Must` · `R1` · `Accepted` · Verify: GameTest

A tool shall force a firelock open, even in lockdown.

- **Rationale:** vg: vg lets crew with access force firelocks; in Minecraft a
  tool stands in for access.
- **Verify:** A GameTest checks the tool opens a locked-down firelock.

#### CONT-044 · Fire alarm
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: FIRE-018, FIRE-019

A fire alarm shall raise danger 2 for its zone when a hotspot is in its zone
or the zone exceeds 473 K.

- **Rationale:** vg: vg's fire alarm triggers on fire or above 473 K ([vg
  §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)).
- **Verify:** GameTests check the alarm raises danger 2 for a hotspot and for
  a zone above 473 K.

#### CONT-045 · Thermostat
`Could` · `R2` · `Deferred` — moved to R2 (lean scope; the heater and freezer cover it), 2026-10-30. · Verify: GameTest

An area air controller may move its zone's temperature toward a target, using
power.

*2026-11-01: the air alarm is renamed the area air controller (D-68).*

## Obtaining gas and progression

#### CONT-050 · Gas acquisition
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-01, D-33

R1 shall provide these ways to obtain gas, with no other mods installed:
- the air compressor (CONT-054)
- the water electrolyzer (CONT-055)
- scrubbing and filtering gases into canisters for reuse (CONT-013, CONT-015,
  CONT-017)

- **Rationale:** Decision: D-33.
- **Verify:** A GameTest obtains O₂ and N₂ by each listed route with only ZAS³
  installed.

#### CONT-054 · Air compressor
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-33

An air compressor shall fill a canister with 2 mol/s of ambient gas, up to
4,500 kPa, drawing 40 FE/t. It works only where the ambient has gas.

- **Rationale:** Decision: D-33; 2 mol/s is a gameplay rate that fills a
  canister to 4,500 kPa in about 15 minutes.
- **Verify:** A GameTest checks the fill rate and the 4,500 kPa limit, and
  that it does nothing in vacuum.

#### CONT-055 · Water electrolyzer
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-33

A water electrolyzer shall produce 1 mol/s of O₂ and 2 mol/s of H₂, drawing
80 FE/t. The rate is gameplay-scaled; real electrolysis energy would make
about 140 times less. Each gas leaves by its own pipe port, on opposite sides:
facing its front, O₂ on the left and H₂ on the right.

- **Rationale:** Decision: D-33; the rate is gameplay-scaled, as the statement
  says.
- **Verify:** A GameTest checks the O₂ and H₂ output per second, and that each
  gas leaves only by its own port.
- **Changed 2026-11-02 (the owner, in the batch 4 art review):** each gas
  leaves by its own pipe port, on opposite sides.

#### CONT-056 · Electrolyzer water supply
`Must` · `R1` · `Accepted` · Verify: GameTest

The electrolyzer shall take water from buckets or a fluid capability into a
4-bucket internal tank. One bucket lasts about 7.7 h of operation. Fluid
pipes join it only at the water inlet on its back.

- **Rationale:** Inferred: a 4-bucket tank lets the electrolyzer run for hours
  unattended; the 7.7 h follows from 2 mol/s of H₂.
- **Verify:** A GameTest checks buckets and a fluid capability fill the tank,
  and the water used per second. It also checks that the fluid capability is
  offered on the back and no other face.
- **Changed 2026-11-02 (the owner, in the batch 4 art review):** fluid pipes
  join only the water inlet on the back.

#### CONT-053 · Photosynthesis
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-33, D-13, S-11

Inside a zone, plants (data-driven by block tag: crops, saplings, leaves,
etc.) shall turn CO₂ into an equal number of moles of O₂ while lit, limited by
the CO₂ available. (Planting as a Hardcore zone trigger is CORE-056; under
Normal, plants only affect existing zones, per CORE-055.)

- **Rationale:** Decision: D-33 and D-13.
- **Verify:** A GameTest checks lit crops in a zone turn CO₂ into equal moles
  of O₂ and stop when CO₂ runs out (S-11).

*2026-11-01: the plants tag is `#zas:plants`, shipped with CORE-056
([#64](https://github.com/west3436/zas-cubed/issues/64)). It holds
`#minecraft:crops`, `#minecraft:saplings` and `#minecraft:leaves`, and under
Hardcore placing one in an enclosed, unzoned area forms a zone. Photosynthesis
reads the same tag. CONT-057's two rates can tell leaves apart by
`#minecraft:leaves`, or split the tag into two that it includes.*

#### CONT-057 · Photosynthesis rates
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: D-33, S-11

Rates shall be configurable per tag and scale linearly with light level, from
0 at light 0. Defaults at light 15 follow the preset (OPS-007), so that a
number of lit crops supplies one breather (about 1 mol of O₂ per hour,
LIFE-042):

| Preset | Crops and saplings | Leaves | Crops per breather |
|---|---|---|---|
| Normal | 0.125 mol/h | 0.0625 mol/h | 8 |
| Hardcore | 0.0625 mol/h | 0.03125 mol/h | 16 |

- **Rationale:** Decision: D-61. Leaves at half the crop rate is inferred,
  kept from the earlier defaults.
- **Verify:** Inspect the config; a GameTest checks the rate at light 15 and
  7 under each preset.

*2026-10-31: defaults lowered from 0.002 and 0.001 mol/s (one crop supplied
about 7 breathers) and made preset-dependent, at the user's request before
the baseline (D-61).*

#### CONT-051 · Craftable from vanilla materials
`Must` · `R1` · `Accepted` · Verify: Inspect

All R1 content shall be craftable from vanilla materials.

- **Rationale:** Decision: D-02 and A-05. The content mod must be playable
  alone.
- **Verify:** Inspect the recipes for non-vanilla ingredients.

*2026-11-02: recipes go through 19 crafting components, each made from
vanilla materials (D-72), so every chain ends in vanilla materials. The
GameTest `cont_051_recipes_use_vanilla_materials` checks it for the recipes
that load.*

#### CONT-058 · Documented progression
`Must` · `R1` · `Accepted` · Verify: Inspect

The content's crafting progression shall be documented.

- **Rationale:** Inferred: SS13 content is unfamiliar to most Minecraft
  players, so the progression needs a guide.
- **Verify:** Inspect the documentation against the recipes.

*2026-11-02: the progression and every recipe are in
[docs/crafting.md](../crafting.md).*

#### CONT-059 · Data-driven recipes
`Must` · `R1` · `Accepted` · Verify: Inspect

Recipes shall be data-driven, so packs can override them.

- **Rationale:** Inferred: packs rebalance recipes, and data-driven recipes
  are the Minecraft norm.
- **Verify:** Inspect the recipe files; a test datapack overrides one.

*2026-11-02: the recipes are in `data/zas_atmos/recipe/`. The GameTest
`cont_059_datapack_overrides_recipe` loads a data pack that replaces the pipe
cap's recipe and checks that its version wins.*

#### CONT-052 · Packaging
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-16, D-38

All SS13 content shall ship as a separate mod from the same repository, with
the ID `zas_atmos` and display name "ZAS³ Atmospherics" (D-38). It depends on
the framework mod `zas`.

- **Rationale:** Decision: D-16 and D-38.
- **Verify:** Inspect the build: two jars, with the content mod's metadata
  depending on `zas`.

#### CONT-095 · Public API only
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-16, D-50, API-001

The content mod shall use only the public API.

- **Rationale:** Decision: D-16 and D-50. Proves the API is enough for other
  tech mods.
- **Verify:** Inspect: a build check fails if the content mod uses framework
  internals.

#### CONT-096 · Framework works alone
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-16

The framework mod shall be fully usable without the content mod.

- **Rationale:** Decision: D-16.
- **Verify:** GameTests for the framework run without the content mod.

## Power

Decided in D-34, D-35, D-37, D-68 and D-69. The area air controller is the
only power source for atmospherics parts, and portables run on batteries. All
FE values below are configurable starting points, tuned in playtesting.

#### CONT-060 · FE-powered devices
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-25, D-37

Active devices shall consume Forge Energy (FE). A device without power stops.

- **Rationale:** Decision: D-25 and D-37.
- **Verify:** A GameTest checks each device stops when its power is cut.

*2026-11-02: the vent runs only while powered;
`GasDeviceTests.cont_014_an_unpowered_vent_does_nothing` (GameTest). The
GameTest that cuts a provider's power waits for the area air controller.*

*2026-11-02: The controller is the first FE source ([#52](https://github.com/west3436/zas-cubed/issues/52)): it stops when it
can't pay its own draw, and parts stop when it stops paying them
(`cont_064_a_starved_controller_powers_life_support_first`). This stays
Accepted until each device exists to check.*

*2026-11-02: The vent stops when its provider's power is cut ([#160](https://github.com/west3436/zas-cubed/pull/160)).
In `StationTests.cont_069_a_real_vent_runs_only_on_a_controller` (GameTest), the controller's generator is removed and its buffer
spent; once the vent's lease runs out, it moves nothing and shows off. This
stays Accepted until each device exists to check.*

#### CONT-065 · Device draws
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: D-37

Devices shall draw these amounts by default:

| Device | FE/t |
|---|---|
| Vent | 10 |
| Scrubber (panic) | 15 (30) |
| Pumps, filter, mixer | 20 |
| Compressor | 40 |
| Heater, freezer | 60 |
| Igniter (while active) | 20 |
| Recharger (while charging) | 40 |
| Electrolyzer | 80 |
| Area air controller (base) | 5 |
| Fire alarm | 1 |
| Firelock | 0 |

- **Rationale:** Decision: D-37 and D-59; starting values for playtesting.
- **Verify:** Inspect the config defaults.

*2026-10-31: added the igniter (CONT-105, D-57).*

*2026-10-31: igniter raised from 10 to 20 FE/t (D-59).*

*2026-11-01: the air alarm is renamed the area air controller (D-68), and the
recharger is added (CONT-113, D-69).*

*2026-11-02: the vent's 10 FE/t is `[power] vent`, drawn while it isn't off
([#48](https://github.com/west3436/zas-cubed/issues/48)); the other devices'
draws come with them.*

*2026-11-02: The controller's 5 FE/t is in the config (`[power]
area_air_controller`, [config.md](../config.md)). This stays Accepted until
every device's draw is.*

*2026-11-02: In `StationTests.cont_069_a_real_vent_runs_only_on_a_controller` ([#160](https://github.com/west3436/zas-cubed/pull/160)),
a controller pays 100 FE per atmos tick for itself and 200 for a blowing
vent, out of a real generator's FE, and an ordered-off vent draws nothing.
This stays Accepted until every device's draw is in the config.*

#### CONT-061 · Built-in generator
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-25, D-35, D-37, A-01

ZAS³ content shall include a portable solid-fuel generator in the style of
SS13's P.A.C.M.A.N., making 40 FE/t, so the mod is playable without any other
power mod.

- **Rationale:** Decision: D-25, D-35 and D-37.
- **Verify:** A GameTest checks the generator makes 40 FE/t while burning.

*2026-11-02: Implemented ([#52](https://github.com/west3436/zas-cubed/issues/52)). The generator is a block with a fuel slot
and an 8,000 FE store that it offers through the FE capability
(`cont_061_generator_makes_40_fe_per_tick_and_a_coal_lasts_80_s`).*

#### CONT-066 · Generator fuel
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-35

The generator shall burn any furnace fuel, one coal per 80 s, and other fuels
for 80 s times their furnace burn time over coal's.

- **Rationale:** Decision: D-35; 80 s per coal matches a vanilla furnace.
- **Verify:** A GameTest checks one coal lasts 80 s, and that other fuels are
  accepted and burn in proportion.

*2026-11-02: Implemented ([#52](https://github.com/west3436/zas-cubed/issues/52)). Coal and charcoal are `#minecraft:coals`. A
generator whose store is full starts no new fuel
(`cont_061_generator_makes_40_fe_per_tick_and_a_coal_lasts_80_s`).*

*2026-11-02: Changed by the owner (d-155-fuel): any furnace fuel, not only
coal and charcoal. Fuel is anything with a furnace burn time, which includes
NeoForge's furnace fuel data map, except fuels that leave an item behind (a lava
bucket), which the one slot has nowhere to put. A coal block burns for 800 s and
a stick for 5 s (`cont_066_generator_burns_any_furnace_fuel_by_its_burn_time`).*

#### CONT-067 · Generator is a lit fuel block
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-35

The generator shall count as a lit fuel-burning block for FIRE-021, FIRE-024,
FIRE-025 and HEAT-007.

- **Rationale:** Decision: D-35.
- **Verify:** A GameTest checks a running generator in a zone uses O₂, emits
  CO₂ and heat, and forms a zone under Hardcore.

*2026-11-01: for CORE-056 and FIRE-024 the generator counts as combustion
through data, with nothing added to the API
([#64](https://github.com/west3436/zas-cubed/issues/64)). `zas_atmos` lists it
in `#zas:combustion` (`data/zas/tags/block/combustion.json`), and the block has
a boolean `lit` property that is true while it burns. The change hook then sees
it being lit as it sees a furnace. Its O₂ and CO₂ exchange is FIRE-021's, from
its FIRE-025 profile. If the generator made those changes itself through
`GasMutations`, it would form zones under Normal too, because API calls are
Normal triggers (CORE-055).*

*2026-11-02: The generator is in `#zas:combustion` with a `lit` property
([#52](https://github.com/west3436/zas-cubed/issues/52)), and `core_056_lit_generator_forms_a_zone_under_hardcore` checks it
forms a zone under Hardcore and not under Normal. This stays Accepted until
FIRE-021 and HEAT-007 make lit fuel blocks use O₂, emit CO₂ and heat.*

#### CONT-062 · Controller power buffer
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: D-25, D-34, D-68

An area air controller shall take FE from any block next to it that exposes
the FE capability, such as the generator (CONT-061) or another mod's cable,
into an internal 10,000 FE buffer.

- **Rationale:** Decision: D-25, D-34 and D-68; the 10,000 FE buffer is an
  inferred starting value for playtesting. Other mods' generators, batteries
  and cables can power ZAS³.
- **Verify:** A GameTest checks the controller fills from an adjacent
  generator and from an adjacent test FE block up to 10,000 FE.

*2026-11-01: the air alarm is renamed the area air controller, and its buffer
fills only from a block next to it, since there is no cable (D-68).*

*2026-11-02: Implemented ([#52](https://github.com/west3436/zas-cubed/issues/52)). Each atmos tick the controller pulls from
the FE capability of each of its six neighbours, and it also takes FE that
cables push. It gives none out
(`cont_062_buffer_fills_from_fe_blocks_next_to_it`).*

#### CONT-068 · Wireless feed to parts
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-32, D-34, D-68

An area air controller shall power, from its buffer, every atmospherics part
that draws FE (CONT-065) in its zone or linked to it with the multitool
(CONT-042). Portables run on their battery instead (CONT-018).

- **Rationale:** Decision: D-32, D-34 and D-68.
- **Verify:** A GameTest checks parts in a controller's zone, and a part
  linked to it across a firelock, run from its buffer with no other power.

*2026-11-01: the air alarm is renamed the area air controller, and it powers
every part in its zone or linked to it, not only the devices it controls
(D-68).*

*2026-11-02: Built ([#52](https://github.com/west3436/zas-cubed/issues/52)): the controller pays each part's draw for one
atmos tick at a time (architecture §11.7).
`cont_068_controller_powers_its_zone_and_its_links` checks it with stand-in
parts in its zone and linked across a wall. This stays Accepted until it is
checked with real parts across a firelock.*

#### CONT-069 · Parts without a controller have no power
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: D-25, D-68

An atmospherics part that no area air controller powers (CONT-068) shall have
no power, even beside an FE source. Portables run on their battery instead
(CONT-018).

- **Rationale:** Decision: D-68.
- **Verify:** A GameTest places a vent beside a generator with no controller
  and checks it stays off, then links it to a controller and checks it runs.

*2026-11-01: a part with no controller now has no power; it used to need its
own cable (D-68).*

*2026-11-02: `cont_068_controller_powers_its_zone_and_its_links` checks a
stand-in part beside a burning generator has no power, then runs once linked.
This stays Accepted until it is checked with the vent (#48).*

*2026-11-02: Implemented ([#160](https://github.com/west3436/zas-cubed/pull/160)). In `StationTests.cont_069_a_real_vent_runs_only_on_a_controller` (GameTest), a vent
beside a burning generator, with no controller, stays unpowered and moves no
gas; once it is linked with the multitool to a generator-powered controller in
another room, it is powered by that controller and blows.*

#### CONT-064 · Controller power priority
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-34, S-02

When a controller's buffer can't meet demand, it shall power devices in
priority order: life support (vents, scrubbers) first, then pumps, filters and
mixers, then igniters, rechargers and gas production. Devices that miss out
stop until power returns.

- **Rationale:** Decision: D-34.
- **Verify:** A GameTest starves a controller's buffer and checks vents and
  scrubbers keep running while lower priorities stop.
- **Changed 2026-11-02 (the owner, reviewing
  [#177](https://github.com/west3436/zas-cubed/pull/177)):** heaters and freezers leave the
  last group: they are portables on batteries, which no controller powers
  (D-73).

*2026-10-31: added igniters to the last group (CONT-105, D-57).*

*2026-11-01: the air alarm is renamed the area air controller (D-68), and
rechargers join the last group (CONT-113, D-69).*

*2026-11-02: Built ([#52](https://github.com/west3436/zas-cubed/issues/52)). Groups are served in order; within a group each
part that fits is paid, and once a part of a group misses out, the later
groups get nothing that atmos tick (`PowerFeedTest`,
`cont_064_a_starved_controller_powers_life_support_first` with stand-in
parts). This stays Accepted until it is checked with the vent and scrubber.*

*2026-11-02: With the real vent ([#160](https://github.com/west3436/zas-cubed/pull/160)): a controller with FE for
itself and the vent but not a pump powers the vent and leaves the pump off
(`StationTests.cont_069_a_real_vent_runs_only_on_a_controller`). This stays Accepted until it is checked with the scrubber.*

#### CONT-111 · Batteries
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: D-69, [vg §2.5](../research/vg-atmos-content.md#25-power-cells-and-chargers)

There shall be three batteries, holding 100,000 FE (battery), 1,000,000 FE
(high-capacity battery) and 2,000,000 FE (super-capacity battery) through the
FE item capability, so other mods' chargers and machines can use them.

- **Rationale:** vg: vg's power cell, high-capacity and super-capacity cells
  hold 1,000, 10,000 and 20,000 units ([vg
  §2.5](../research/vg-atmos-content.md#25-power-cells-and-chargers)). The
  100 FE per vg unit is inferred: the smallest battery runs a 20 FE/t pump for
  about 4 minutes and the largest for about 80, and one generator (CONT-061)
  fills the smallest in about 2 minutes.
- **Verify:** A GameTest fills each battery through the FE item capability and
  checks it stops at its capacity.

*2026-11-01: Accepted by the owner as written.*

*2026-11-02: Implemented ([#177](https://github.com/west3436/zas-cubed/pull/177)). Each battery keeps its
charge in an item component and takes or gives up to its whole capacity at
once through the FE item capability; the creative tab has an empty and a full
copy of each. Checked by `cont_111_batteries_fill_to_their_capacity`
(GameTest).*

#### CONT-112 · Battery tag
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: D-69 · Depends: CONT-111

A portable's battery slot and the recharger (CONT-113) shall accept any item
in the `zas_atmos:batteries` item tag that exposes the FE item capability, and
no other item. The tag holds the three batteries (CONT-111), and packs can add
other mods' batteries and energy tablets to it.

- **Rationale:** Decision: D-69. A tag lets packs choose which of other mods'
  FE items count as batteries, with no compile-time dependency.
- **Verify:** A GameTest adds a test FE item to the tag and checks a portable
  runs from it and the recharger charges it, and that an FE item outside the
  tag is refused.

*2026-11-01: Accepted by the owner as written.*

*2026-11-02: Implemented ([#177](https://github.com/west3436/zas-cubed/pull/177)). A test data pack adds a
test FE item to the tag; the heater runs from it and the recharger charges
it, and both slots refuse an FE item outside the tag and an item with no FE
(`cont_112_the_tag_decides_what_is_a_battery`, GameTest).*

#### CONT-113 · Recharger
`Should` · `R1` · `Implemented` · Verify: GameTest · Trace: D-69, [vg §2.5](../research/vg-atmos-content.md#25-power-cells-and-chargers) · Depends: CONT-068, CONT-112

A recharger shall hold one battery (CONT-112) and charge it at 28 FE/t,
drawing 40 FE/t from its controller (CONT-068): 70 % of what it draws reaches
the battery.

- **Rationale:** vg: vg's cell charger holds one cell and puts 70 % of what it
  draws into it ([vg
  §2.5](../research/vg-atmos-content.md#25-power-cells-and-chargers)). The
  40 FE/t draw is inferred, one generator's output (CONT-061); at that rate the
  smallest battery fills in about 3 minutes and the largest in about an hour.
- **Verify:** A GameTest checks a recharger charges a battery at 28 FE/t while
  drawing 40 FE/t, and stops when the battery is full or its power is cut.

*2026-11-01: Accepted by the owner as written.*

*2026-11-02: Implemented ([#177](https://github.com/west3436/zas-cubed/pull/177)). The recharger is a
controller's part in the last group (CONT-064), through its zone or a link,
and draws only while its battery has room; it charges only on power its
controller paid for. `[power] recharger` (40) and `[power] recharger_rate`
(28) set the rates. Checked by `cont_113_power_supplied_at_no_cost_is_not_paid_for`
(Unit) and `cont_113_a_recharger_charges_at_28_drawing_40` (GameTest), whose
power cut is a room whose controller has no FE, since a GameTest's lease
outlasts its single game tick.*

#### CONT-063 · Cable sharing a block with pipes
`Should` · `R1` · `Rejected` — superseded by D-68: there is no power cable, 2026-11-01.

#### CONT-097 · Multipart block
`Should` · `R1` · `Rejected` — superseded by D-68: there is no multipart block, 2026-11-01.

#### CONT-098 · Cable capacity
`Should` · `R1` · `Rejected` — superseded by D-68: there is no power cable, 2026-11-01.

#### CONT-099 · Cables connect to FE blocks
`Should` · `R1` · `Rejected` — superseded by D-68: there is no power cable; the area air controller takes FE from any block next to it (CONT-062), 2026-11-01.

## R1 / R2 split

| R1 | R2 |
|---|---|
| Pipes, valves, pressure pump, vent, scrubber, connector, canister, tanks, masks, softsuit, hardsuit, area air controller, firelock, analyzer, gas acquisition (compressor, electrolyzer, scrubbing), photosynthesis, power (FE devices, generator, area air controllers powering their zones and links), reactions in pipes and containers, pipe burst, tank transfer valve with timer, igniter | Heat-exchange pipes and radiators, thermoelectric cooler, TEG and turbine, cryo cell, dual-port vent, injector, thermostat, pipe leaks and configurable overpressure, meters and air sensors, pipe layers, automation (device redstone and comparator I/O, computer-mod peripherals), gas miner (creative), fire extinguisher, TTV signaler and proximity triggers |
| *Should/Could in R1:* volume pump, filter, mixer, portable pump and scrubber, batteries and recharger, heater and freezer, magnetic boots, fire alarm, passive vent | |

*2026-11-01: the cable sharing a block with pipes is gone (D-68). D-36 had R2's
pipe layers reuse the multipart block, so they need another design. Batteries
and the recharger are added (D-69).*
