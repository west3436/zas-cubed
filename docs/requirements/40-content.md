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

**Scope.** The SS13-style content layer (D-02): pipe networks, devices,
portable atmospherics, tanks and suits, area air controllers and firelocks,
tools, how gas is obtained, power, and progression. Background:
[vg-atmos-content](../research/vg-atmos-content.md).

R1 ships a playable subset. The rest of vg's device set is R2 (see the table
at the end). vg's per-2 s device rates are converted to per second (D-20).

## Pipe networks

#### CONT-001 · Network model
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: [vg §1](../research/vg-atmos-content.md#1-pipe-networks)

Connected pipes shall form a network. Each continuous pipe run holds one
mixture whose volume is the sum of its segments' volumes.

- **Rationale:** vg: a vg pipeline owns one mixture whose volume is the sum of
  its pipes ([vg §1](../research/vg-atmos-content.md#1-pipe-networks)).
- **Verify:** A Unit test builds runs of several lengths and checks their
  volumes; a GameTest checks connected pipes share one mixture.

#### CONT-007 · Pipe and buffer volumes
`Must` · `R1` · `Accepted` · Verify: Unit · Trace: [vg §1](../research/vg-atmos-content.md#1-pipe-networks)

Each pipe block shall hold 70 L, and each device port buffer 200 L (vents
400 L), as in vg.

- **Rationale:** vg: vg's 70 L pipes and 200 L port buffers ([vg
  §1](../research/vg-atmos-content.md#1-pipe-networks)); the 400 L vent buffer
  is inferred, twice a port buffer, so a vent can keep up its rate (CONT-078).
- **Verify:** A Unit test checks each part's volume.

#### CONT-008 · Perfect mixing
`Must` · `R1` · `Accepted` · Verify: Unit

A pipe run's mixture shall be perfectly mixed after every change.

- **Rationale:** vg: vg merges a network into one mixture whenever it changes
  ([vg §1](../research/vg-atmos-content.md#1-pipe-networks)).
- **Verify:** A Unit test adds gas at one end of a run and checks the whole
  run is uniform after the change.

#### CONT-002 · Separate from zones
`Must` · `R1` · `Accepted` · Verify: Inspect · Trace: API-021

Pipe gas shall never join zone gas directly. Only devices, open ends (CONT-004)
and ruptures move gas between pipes and zones.

- **Rationale:** vg: in vg, pipe gas never joins zone gas; only devices move
  it ([vg §1](../research/vg-atmos-content.md#1-pipe-networks)).
- **Verify:** Inspect the code: only devices, open ends and ruptures call zone
  mutations.

#### CONT-003 · Gas-conserving rebuilds
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: CORE-036

Splitting or merging networks shall share gas out by volume.

- **Rationale:** vg: vg shares a pipeline's gas out by volume when it is
  rebuilt ([vg §1](../research/vg-atmos-content.md#1-pipe-networks)); also
  CORE-036.
- **Verify:** A Unit test splits and merges networks and checks each part gets
  gas by volume; a GameTest breaks a pipe in a run and checks total moles.

#### CONT-009 · Removed segments release their gas
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: CORE-036

A removed pipe segment's share of gas shall be released into its cell
(CORE-053 if the cell is open).

- **Rationale:** vg: a removed vg pipe dumps its share on its tile ([vg
  §1](../research/vg-atmos-content.md#1-pipe-networks)).
- **Verify:** A GameTest removes a segment from a filled run and checks its
  cell gains the segment's share.

#### CONT-004 · Open ends leak
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: vg quirk 4, CORE-031

A pipe end with no connection shall exchange gas with its cell's atmosphere as
a one-connection edge does (sharing ratio 0.163 per second). vg disabled this.

- **Rationale:** Inferred: an open pipe end should leak, as in reality; vg's
  open-end mixing is commented out ([vg
  §1](../research/vg-atmos-content.md#1-pipe-networks)).
- **Verify:** A GameTest checks an open pipe end exchanges gas with its room
  at the one-connection ratio.

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
`Must` · `R1` · `Accepted` · Verify: Unit · Trace: D-31, D-20, D-50

Pipe networks and devices shall step once per atmos tick, on the server
thread.

- **Rationale:** Decision: D-31, D-20 and D-50.
- **Verify:** A Unit test checks networks and devices step exactly once per
  atmos tick, on the server thread.

#### CONT-070 · Client smoothing
`Must` · `R1` · `Accepted` · Verify: Manual

Device animations shall be smoothed on the client between steps.

- **Rationale:** Inferred: devices step once per second, so animations would
  look jerky unless the client interpolates.
- **Verify:** Manual check that pump and vent animations play smoothly.

#### CONT-071 · Zones through the public API
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: D-50, API-010, API-016

Devices shall read zones through atmosphere queries (API-010) and change them
through asynchronous mutations (API-016).

- **Rationale:** Decision: D-50.
- **Verify:** Inspect the content mod's code for non-API engine access;
  GameTests of the devices exercise the calls.

#### CONT-072 · One outstanding mutation per port
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: D-50, CORE-036

A device port shall issue a new zone mutation only after its previous one has
completed, so decisions made on a stale snapshot never add or remove gas
twice.

- **Rationale:** Decision: D-50. A second mutation decided on the same stale
  snapshot would move gas twice (CORE-036).
- **Verify:** A Unit test checks a port waits for its future; a GameTest runs
  a vent and scrubber with the conservation checker on.

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
`Must` · `R1` · `Accepted` · Verify: GameTest, Inspect

Each device's configuration shall be set through a GUI whose inputs the
server validates.

- **Rationale:** Inferred: clients can send anything, so device settings are
  checked on the server, as with any multiplayer GUI.
- **Verify:** A GameTest sends out-of-range settings and checks they are
  clamped or refused; Inspect the packet handlers.

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
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-01 · [vg §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)

A vent shall blow gas into its zone, or siphon gas out of it, within an
external bound (default 101.325 kPa) and an internal bound, both 0–5,066 kPa.
In an unenclosed cell it vents to the ambient (CORE-053, CORE-057). It faces
any of six ways, and a pipe joins it only on its back, the face opposite its
grille.

- **Rationale:** vg: vg's vent pump and its bounds ([vg
  §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)).
- **Verify:** A GameTest checks a vent fills a room to the external bound and
  siphons down to it, and vents to the ambient in an open cell. It also checks
  that a pipe joins the vent's back and no other face.
- **Changed 2026-11-01 (the owner, in the batch 1 art review):** the vent
  faces any of six ways and takes a pipe only on its back.

#### CONT-078 · Vent rate
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: [vg §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)

A vent shall move at most half its buffer's moles per second, so it can't fill
a room in one tick as vg's does.

- **Rationale:** Inferred: vg's vent can fill a whole room in one tick ([vg
  §2.2](../research/vg-atmos-content.md#22-vents-and-scrubbers)), which looks
  wrong and makes airflow spike; half the buffer per second spreads it out.
- **Verify:** A Unit test checks the moles moved per second; a GameTest checks
  a room fills over several ticks.

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

- **Rationale:** vg: vg's passive vent, which equalizes above 0.5 kPa ([vg
  §2](../research/vg-atmos-content.md#2-devices)).
- **Verify:** A GameTest checks a passive vent equalizes with a room only
  above the threshold.

#### CONT-082 · Injector
`Could` · `R2` · `Deferred` — moved to R2 (lean scope; the vent covers it), 2026-10-30. · Verify: GameTest

A fixed-rate injector shall push gas into its zone regardless of room pressure.

#### CONT-017 · Canister
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-01 · [vg §2.3](../research/vg-atmos-content.md#23-canisters-and-portables)

A canister shall be a 1,000 L block rated at 9,120 kPa (90 atm) that keeps its
contents when mined.

- **Rationale:** vg: vg's 1,000 L canister with a 90 atm limit ([vg
  §2.3](../research/vg-atmos-content.md#23-canisters-and-portables)); keeping
  contents when mined is the Minecraft equivalent of moving it.
- **Verify:** A GameTest mines a filled canister, places it again and checks
  the contents are unchanged.

#### CONT-083 · Connector ports
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-01

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

#### CONT-084 · Canister release
`Must` · `R1` · `Accepted` · Verify: Unit, GameTest · Trace: vg quirk 10

A canister shall release through a valve at a set release pressure
(10–1,013 kPa) into the room or a held tank, computed against the receiving
volume: the zone's real volume, not vg's fixed 2,500 L.

- **Rationale:** vg: vg's release valve and its 10–1,013 kPa range ([vg
  §2.3](../research/vg-atmos-content.md#23-canisters-and-portables)); its
  fixed 2,500 L receiving volume is replaced with the real volume.
- **Verify:** A Unit test checks the moles released against the formula; a
  GameTest checks a canister fills a room to the release pressure.

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

#### CONT-018 · Portable pump and scrubber
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-69

A portable pump and a portable scrubber, as blocks that run on a battery in a
slot (CONT-112), shall work on the room or on a held tank.

- **Rationale:** vg: vg's portable pump and scrubber, which act on the room or
  a held tank ([vg
  §2.3](../research/vg-atmos-content.md#23-canisters-and-portables)); the
  battery follows D-69.
- **Verify:** A GameTest checks each works on a room and on a held tank, and
  stops when its battery is empty.

*2026-11-01: the portables run on a battery instead of their own FE buffer,
whose charging was left open by D-68 (D-69).*

#### CONT-019 · Heater and freezer
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-05, S-05

A heater (293–573 K) and a freezer (73–293 K) shall move their network gas
toward a setpoint.

- **Rationale:** vg: vg's heater and freezer ranges ([vg
  §2](../research/vg-atmos-content.md#2-devices)).
- **Verify:** A GameTest checks a heater and a freezer bring their network gas
  to a setpoint.

#### CONT-087 · Thermomachine power
`Should` · `R1` · `Accepted` · Verify: Unit

A heater or freezer shall move up to 5 kW toward its setpoint, drawing
60 FE/t.

- **Rationale:** Inferred: vg's thermomachines have no real power limit ([vg
  §2](../research/vg-atmos-content.md#2-devices)); 5 kW is a gameplay value
  that warms a 27-block room by about 20 K in under 2 minutes.
- **Verify:** A Unit test checks the heat moved per second never exceeds 5 kW.

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

- **Rationale:** Decision: D-57 (S-13).
- **Verify:** A GameTest opens and closes a TTV and checks the gas moves and
  shares back by volume, and that a hot mixture burns in the tank.

#### CONT-104 · TTV timer
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-57, S-13 · Depends: CONT-103

A TTV's valve shall open by hand, or when a timer set from 0 to 600 s
(default 10 s) runs out. At 0 s the valve opens as soon as the timer starts,
as in vg. The timer counts down in a player's inventory and as
a dropped item. vg's signaler and proximity triggers are R2.

- **Rationale:** Decision: D-57 and D-59.
- **Verify:** GameTests check the valve opens by hand, at 0 s, and when a
  timer runs out in an inventory and as a dropped item.

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
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: S-02, D-68 · [vg §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)

An area air controller shall sample its zone each atmos tick and rate each
reading against its preset's (min2, min1, max1, max2): at or beyond min2 or
max2 is danger 2, at or beyond min1 or max1 is danger 1. Readings are pressure,
temperature, each listed gas, and all other gases combined.

- **Rationale:** vg: vg's air alarm sampling and thresholds ([vg
  §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)).
- **Verify:** A GameTest steps a zone's readings through each band and checks
  the danger level.

*2026-11-01: the air alarm is renamed the area air controller (D-68).*

#### CONT-046 · Danger hysteresis
`Must` · `R1` · `Accepted` · Verify: Unit · Trace: [vg §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors), D-20

A controller's danger level shall rise immediately and fall only after 10 s of
lower readings.

- **Rationale:** vg: vg needs 5 lower readings (10 s) before danger falls ([vg
  §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors)).
- **Verify:** A Unit test checks danger rises at once and falls only after 10
  s.

*2026-11-01: the air alarm is renamed the area air controller (D-68).*

#### CONT-047 · Controller presets
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: [vg §3](../research/vg-atmos-content.md#3-air-alarms-fire-alarms-and-firedoors), LIFE-041, FIRE-003

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

#### CONT-048 · Controller redstone
`Must` · `R1` · `Accepted` · Verify: GameTest

An area air controller shall emit a redstone signal of 7 at danger 1 and 15 at
danger 2.

- **Rationale:** Inferred: redstone lets players build their own responses to
  danger, at two strengths for the two danger levels.
- **Verify:** A GameTest checks signal strengths 0, 7 and 15 at each danger
  level.

*2026-11-01: the air alarm is renamed the area air controller (D-68).*

#### CONT-042 · Room model
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-32, D-68

By default, an area air controller shall control the devices in its zone. The
multitool can also link parts to it by hand, for example across a firelock.

- **Rationale:** Decision: D-32 and D-68.
- **Verify:** A GameTest checks a controller controls the devices in its zone
  and a device linked with the multitool across a firelock.

*2026-11-01: the air alarm is renamed the area air controller, and the linking
tool is the multitool (D-68). A link also powers the part (CONT-068).*

#### CONT-110 · Controller forms its zone
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-68, D-13, S-01 · Depends: API-050, CONT-042

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

#### CONT-049 · Controller device list
`Must` · `R1` · `Accepted` · Verify: Manual

The controller's GUI shall list every device it controls and show whether each
came from the zone or from a link.

- **Rationale:** Inferred: players need to see what a controller controls to
  debug their station.
- **Verify:** Manual check of the list against the devices in the zone and the
  links.

*2026-11-01: the air alarm is renamed the area air controller (D-68).*

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
about 140 times less.

- **Rationale:** Decision: D-33; the rate is gameplay-scaled, as the statement
  says.
- **Verify:** A GameTest checks the O₂ and H₂ output per second.

#### CONT-056 · Electrolyzer water supply
`Must` · `R1` · `Accepted` · Verify: GameTest

The electrolyzer shall take water from buckets or a fluid capability into a
4-bucket internal tank. One bucket lasts about 7.7 h of operation.

- **Rationale:** Inferred: a 4-bucket tank lets the electrolyzer run for hours
  unattended; the 7.7 h follows from 2 mol/s of H₂.
- **Verify:** A GameTest checks buckets and a fluid capability fill the tank,
  and the water used per second.

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

#### CONT-058 · Documented progression
`Must` · `R1` · `Accepted` · Verify: Inspect

The content's crafting progression shall be documented.

- **Rationale:** Inferred: SS13 content is unfamiliar to most Minecraft
  players, so the progression needs a guide.
- **Verify:** Inspect the documentation against the recipes.

#### CONT-059 · Data-driven recipes
`Must` · `R1` · `Accepted` · Verify: Inspect

Recipes shall be data-driven, so packs can override them.

- **Rationale:** Inferred: packs rebalance recipes, and data-driven recipes
  are the Minecraft norm.
- **Verify:** Inspect the recipe files; a test datapack overrides one.

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

#### CONT-061 · Built-in generator
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-25, D-35, D-37, A-01

ZAS³ content shall include a portable solid-fuel generator in the style of
SS13's P.A.C.M.A.N., making 40 FE/t, so the mod is playable without any other
power mod.

- **Rationale:** Decision: D-25, D-35 and D-37.
- **Verify:** A GameTest checks the generator makes 40 FE/t while burning.

#### CONT-066 · Generator fuel
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-35

The generator shall burn one coal or charcoal per 80 s.

- **Rationale:** Decision: D-35; 80 s per coal matches a vanilla furnace.
- **Verify:** A GameTest checks one coal lasts 80 s.

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

#### CONT-062 · Controller power buffer
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-25, D-34, D-68

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

#### CONT-069 · Parts without a controller have no power
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-25, D-68

An atmospherics part that no area air controller powers (CONT-068) shall have
no power, even beside an FE source. Portables run on their battery instead
(CONT-018).

- **Rationale:** Decision: D-68.
- **Verify:** A GameTest places a vent beside a generator with no controller
  and checks it stays off, then links it to a controller and checks it runs.

*2026-11-01: a part with no controller now has no power; it used to need its
own cable (D-68).*

#### CONT-064 · Controller power priority
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-34, S-02

When a controller's buffer can't meet demand, it shall power devices in
priority order: life support (vents, scrubbers) first, then pumps, filters and
mixers, then heaters, freezers, igniters, rechargers and gas production.
Devices that miss out stop until power returns.

- **Rationale:** Decision: D-34.
- **Verify:** A GameTest starves a controller's buffer and checks vents and
  scrubbers keep running while lower priorities stop.

*2026-10-31: added igniters to the last group (CONT-105, D-57).*

*2026-11-01: the air alarm is renamed the area air controller (D-68), and
rechargers join the last group (CONT-113, D-69).*

#### CONT-111 · Batteries
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-69, [vg §2.5](../research/vg-atmos-content.md#25-power-cells-and-chargers)

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

#### CONT-112 · Battery tag
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-69 · Depends: CONT-111

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

#### CONT-113 · Recharger
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-69, [vg §2.5](../research/vg-atmos-content.md#25-power-cells-and-chargers) · Depends: CONT-068, CONT-112

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
