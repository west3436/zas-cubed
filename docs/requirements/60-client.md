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

*2026-11-02: The warning part is built
([#38](https://github.com/west3436/zas-cubed/issues/38)): the HUD shows while
any reading is at warning or worse, and hides otherwise and with the rest of
the GUI (F1). Nothing seals breathing or analyzes gas yet, so the sealed
helmet and analyzer parts wait for them, and this stays Accepted.*

#### CLIENT-008 · HUD readings and thresholds
`Must` · `R1` · `Accepted` · Verify: Manual, GameTest · Trace: LIFE-003, LIFE-004, LIFE-010, LIFE-011, LIFE-014, LIFE-041

The HUD shall show pressure, O₂ partial pressure, temperature and toxins, each
at a warning or danger level. Toxin levels are read from the hazard profiles
(LIFE-004), so addon gases get indicators without code. Each level is judged
on what reaches the player through its protection: the effective pressure
(LIFE-020), the O₂ and gases in its breath, and the temperature unless its
thermal protection takes all temperature damage there (LIFE-025). Defaults:

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
- **Changed 2026-11-02 (the owner, in the HUD alerts work):** levels are
  judged on what reaches the player through its protection, as vg's are, so a
  danger is where LIFE harms it and a sealed suit in vacuum shows no pressure
  alert.

*2026-11-02: Built ([#38](https://github.com/west3436/zas-cubed/issues/38)).
Each reading at warning or worse shows vg's screen-alert icon for it, in a
column at the right edge of the screen, with the reading beside it in the
display units (CLIENT-006): yellow for a warning, red for a danger. The toxin
alert names the gas with the worst level. A hazard that is off in the
dimension has no alert (LIFE-031), and a creative or spectator player none
(LIFE-037). The temperature warnings follow the comfort band, 20 K inside it.
`client_008_levels_at_each_threshold`,
`client_008_rooms_sync_their_alerts`,
`client_008_suits_hide_what_does_not_reach_the_player` and
`client_008_hazards_off_and_creative_have_no_alerts` (GameTests) pass. It
stays Accepted until the owner checks the display by hand.*

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

*2026-11-02: Built ([#38](https://github.com/west3436/zas-cubed/issues/38)).
Low and high pressure, and cold and heat, have different icons, and every
danger icon flashes with vg's two-frame animation while warnings stay still.
O₂ and toxins have one icon each, so for them the flashing is the cue. It
stays Accepted until the owner checks it by hand.*

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

*2026-11-05: Built ([#57](https://github.com/west3436/zas-cubed/issues/57)).
Each game tick the client starts vanilla's white smoke sprite, tinted a neutral
pale grey in code, at each opening it was sent (DATA-015), just on the high side
of the face and moving through it towards the low side. Each opening starts
kPa / 200 particles a tick, up to 1, so 0.1 at 20 kPa and 0.5 at 101 kPa. Their
speed is 0.1 + 0.35 × kPa / 101.325 blocks a tick, up to 0.6. One edge starts at
most 8 a tick and a client at most 32, spent on the nearest edge first. The
Particles setting scales the density by vanilla's own shares: All 1, Decreased
2/3, Minimal none. With
airflow switched off (FLOW-014) there are none. `AirflowParticlePlanTest` checks
the curves, the caps, the setting, the direction and the 32 blocks;
`client_003_players_within_32_blocks_are_sent_the_breach` and
`client_003_an_edge_shows_from_the_lowest_class_threshold` (GameTests) check what
the server sends. It stays Accepted until the owner checks how it looks.*

#### CLIENT-010 · Airflow sound
`Should` · `R1` · `Accepted` · Verify: Manual · Trace: FLOW-001, FLOW-011, DATA-015

Such an edge shall play a looping hiss whose volume follows the moles per second
through its openings and whose pitch rises with the pressure difference, audible
within 16 blocks. A listener in a vacuum shall hear none of it.

- **Rationale:** Decision: the owner's sound decisions of 2026-11-05 set the
  loudness and the vacuum. Inferred: a hiss is the clearest cue to a leak, louder
  as it worsens.
- **Verify:** Manual check of the hiss at different pressure differences, room
  sizes and distances.

*2026-11-05: The owner changed the loudness rule. Volume followed the pressure
difference; it now follows the moles per second through the opening, so no flow
is silence and a big room venting is louder than a closet at the same pressure.
The difference still sets the pitch. This stays Accepted, and nothing has been
heard yet, so the Manual check is open.*

*What was built. The engine records each edge's net moles per second, and the
client gets it with the rest of DATA-015's list. Measured by `FlowExportTest`,
through one hole at 1 atm to a vacuum: 11.6 mol/s from a 3-cell room, 23.6 from 27
cells and 27.0 from 675. It levels off with size because venting is limited by the
hole, not the room. `AirflowSoundPlan` holds the rest, with no game classes:*

- *Volume is moles per second / 64, up to 1, so that hole plays at 0.18, 0.37 and
  0.42. `AirflowSoundPlan.volume` is the one place that decides it.*
- *Pitch is 1 at 101 kPa, and moves 0.107 for each doubling of the difference, from
  0.7 to 1.5: 0.75 at 20 kPa and 1.11 at 200.*
- *Openings are grouped by the 8-block cell of the world grid they are in, whichever
  edge they belong to. Each cell is one source at the middle of its flow, with the
  flows added up, so many edges at one breach make one capped sound. A client hears
  the 2 sources loudest where it stands.*
- *Each source plays two loops: `zas:airflow.hiss`, and the wind of the class its
  difference reaches (FLOW-011): `airflow.light`, `.mob` from 65 kPa or `.heavy` from
  85. The wind files carry almost nothing above 400 Hz and the hiss has no body
  (`docs/research/vg-sounds.md`). So airflow takes at most 4 of CLIENT-015's 8 slots.*
- *The vacuum is CLIENT-016's thin air, with no second rule here. It reads the real
  pressure at the eyes, so it holds in a sealed suit and in creative.*

*`AirflowSoundPlanTest` checks the curves, the grouping and the cap;
`AirflowSoundLoopsTest` runs the loops through the real `LoopBook`, for the slots,
the vacuum, a steady breach staying in the same voices, and nothing left once the
flow ends.*

#### CLIENT-004 · Low-pressure sound
`Could` · `R2` · `Deferred` — moved to R2 (lean scope; damping every sound needs sound-engine hooks, and no R1 scenario needs it), 2026-10-31. · Verify: Manual

Sounds heard by a player in low pressure shall be attenuated, falling silent
in vacuum. Sound from the player's own suit is exempt.

*2026-11-05: The owner decided only our new sounds fade in thin air, and that
is CLIENT-016. Damping vanilla's sounds, and the suit's exemption, stay
deferred here.*

#### CLIENT-015 · Looping sounds
`Should` · `R1` · `Draft` · Verify: Unit, Manual · Trace: CLIENT-010, API-061

A client shall play at most 8 looping sounds at once, chosen from those
requested within their range: the ones that reach the player loudest, which
among loops of one volume and range are the nearest. Each shall fade over 0.5 s
as it starts, stops or changes volume, and shall stop within 1 s of its last
request, as soon as its chunk or the player's level is gone, and when the
player leaves its range.

- **Rationale:** Decision: the owner's sound decisions of 2026-11-05 set the
  nearest 8 and the 8-block range of a running device. Loudness decides between
  loops of different volumes and ranges, so a row of quiet machines can't mute
  a breach or a fire. Inferred: a loop that outlives its source is the worst
  bug sound can have, so a request is a lease that lapses, and nothing has to
  turn a loop off.
- **Verify:** `LoopBookTest` (Unit) checks the choice, the fades and the leases,
  through random comings and goings; Manual checks the sound of a fade and a
  swap, and that no loop remains after leaving a world.

*2026-11-05: added for review, with the manager built
(`sound.LoopBook`, `client.ClientLoops`). A swap fades the old voice out before
the new one starts, so there are never more than 8. The sound engine's own
attenuation is off: the gain falls in a straight line to 0 at each loop's range.
Nothing has been heard yet, so the Manual part is open.*

#### CLIENT-016 · Thin air
`Should` · `R1` · `Draft` · Verify: Unit, GameTest, Manual · Trace: CLIENT-004, CLIENT-010, API-060 · Depends: DATA-019

This mod's new sounds shall fade for a listener in thin air, by the real
pressure at the listener's eyes: in a straight line from exact silence at 1 kPa
to full volume at 50 kPa. Vanilla's sounds shall not change. A listener whose
pressure the client doesn't know yet counts as being in normal air.

- **Rationale:** Decision: the owner's sound decisions of 2026-11-05: only our
  new sounds fade, and a listener in a vacuum hears no airflow at all. Inferred:
  50 kPa is the HUD's low-pressure warning (CLIENT-008), so the sound starts to
  fade where the player is first warned, and a straight line in amplitude is
  roughly what falling density does to a sound.
- **Verify:** `ThinAirTest` and `LoopBookTest` (Unit) check the curve and that a
  vacuum leaves no voice playing; `client_016_*` (GameTest) check the factor in
  rooms and in open air; Manual checks how the fade sounds.

*2026-11-05: added for review, with the curve built (`sound.ThinAir`), for the
owner to judge by ear: 0 at 1 kPa or below, 0.08 at 5, 0.18 at 10, 0.39 at 20,
0.59 at 30, 0.80 at 40 and 1 from 50. It reads the air, not what reaches the
player, so a sealed suit in a vacuum hears silence. The open air reads as its
dimension's ambient pressure, so it is normal air in a breathable dimension and
a vacuum in an airless one. Nothing has been heard yet, so the Manual part is
open.*

#### CLIENT-017 · Gas particle limits
`Should` · `R1` · `Draft` · Verify: Unit, Manual · Trace: D-53

Gas particles (CLIENT-018, CLIENT-019) shall be vanilla sprites tinted in
neutral greys in code, follow Minecraft's Particles setting (all at All, two
thirds at Decreased, none at Minimal), and be capped per client at 40 per source
and 120 in total per game tick.

- **Rationale:** Decision: the owner, 2026-11-05: no new art, never gas-coloured
  because gas visibility is R2 (CLIENT-002), and a large station mustn't drown
  a client.
- **Verify:** Unit tests of the caps and the setting; Manual check of the tints
  and of a station under each setting.

*2026-11-05: Built. A client spawns vanilla's poof and sets its grey, speed and
life. `JetEmittersTest` (Unit) passes; when more particles are wanted than the
total allows, they are shared out evenly, nearest source first, and the rest
are dropped. Nobody has seen them yet, so the numbers are for the owner to
judge.*

#### CLIENT-018 · Spray, leak and burst particles
`Should` · `R1` · `Draft` · Verify: Unit, GameTest, Manual · Trace: CONT-037, CONT-038, CONT-085, CONT-108 · Depends: CLIENT-017

A fire extinguisher (CONT-037) shall show a cone of particles along its reach
while it sprays. A tank that leaks (CONT-038) shall show a jet, and a tank or
canister that ruptures, or a pipe that bursts (CONT-108), a burst, each sized by
the moles it lets out.

- **Rationale:** Decision: the owner, 2026-11-05: players should see where gas
  is coming from and how much of it there is.
- **Verify:** Unit tests of the sizes; GameTests of what the server sends; Manual
  check of how they look.

*2026-11-05: The spray is built. The server sends each spray with its nozzle,
look and reach, stopped short by a wall as the spray is, to players within 32
blocks. `JetPlanTest` (Unit) and `client_018_a_spray_sends_a_jet_along_the_look`,
`client_018_a_spray_stopped_by_a_wall_is_shorter` and
`client_018_only_a_spray_sends_a_jet` (GameTests) pass. Leaks and bursts are
next, and nobody has seen any of it yet.*

*2026-11-05: Leaks and bursts are built. A leaking tank sends a level jet in a
random direction each atmos tick, with the moles it lost. A rupturing tank or
canister, or a bursting pipe, sends a burst in every direction, with the moles
it let out: a canister's share of its network, or all of a burst pipe's. Sizes go
by ratio, because gas comes in amounts many powers of ten apart. An exploding
container sends none, as vanilla's explosion has its own particles, and an empty
canister has nothing to show. The `client_018_*` tests in `ContainerFailureTests`
and `PipeBurstTests` (GameTests) and `JetPlanTest` (Unit) pass. Nobody has seen
these either, so this stays Draft.*

#### CLIENT-019 · Flow particles
`Should` · `R1` · `Draft` · Verify: Unit, GameTest, Manual · Trace: CONT-004, CONT-014, CONT-015, [#164](https://github.com/west3436/zas-cubed/issues/164) · Depends: CLIENT-017, DATA-017, DATA-018

An open pipe end (CONT-004) moving at least 0.25 mol/s net shall show particles
scaled by its flow, moving out of its mouth while gas leaves and into it while
gas enters, and none below that. A vent or scrubber at activity level working or
hard (DATA-017) shall show particles sized by that level, blowing out of the
grille of a blowing vent and drawn into the grille of a siphoning vent or of a
scrubber, and none below working.

- **Rationale:** Decision: the owner, 2026-11-02 (#164) and 2026-11-05: players
  should see big changes in the atmosphere, such as a vacuum being filled or
  scrubbers working hard, and tell which way a device is going, with nothing at
  a trickle.
- **Verify:** Unit tests of the threshold and scaling; GameTests of what the
  server sends for each flow and what a client is told of each device; Manual
  check of how it looks.

*2026-11-05: Open ends are built. While it flows, an end sends one event per
atmos tick with its net moles per second, so gases only passing each other show
nothing. A pipe with one connection blows out of the opposite face, and a lone
pipe every way. Each event's particles last 22 game ticks and then stop. Events
go to players within 16 blocks. Together with a tank's leak jets, a player is
sent at most 20 a second, of 41-byte payloads: about 1.2 KiB/s with their headers,
however large the station. Past that cap the same ends lose out each second. `JetPlanTest`,
`JetEmittersTest`, `SendLimiterTest` and `GasJetTest` (Unit) and the `client_019_*`
tests in `PipeTests` (GameTests) pass. Nobody has seen them yet. The art half of
#164 is still to come. Vents and scrubbers wait for the device activity sync.*

*2026-11-05: Vents and scrubbers are built, with nothing new sent. The client
reads how hard a device is working from its activity table (DATA-018), and which
way from its block state: `VentMode.PROPERTY` for a vent, which can do either, and
`FACING` for where its grille looks. A device that is off shows nothing even
while the table still lists it, and one whose block has gone shows nothing at all.
Working shows 0.3 particles a game tick travelling 1.2 blocks, and hard 1 a tick
travelling 2.5, starting across most of the grille rather than from one point.
They stop within a game tick of the device stopping, and count toward CLIENT-017's
limits. `DeviceJetsTest`, `JetPlanTest` and `JetEmittersTest` (Unit) and the
`client_019_*` tests in `DeviceActivityTests` (GameTests) pass. Nobody has seen
these either, so this stays Draft.*

## Sound files

#### CLIENT-020 · Sound events resolve
`Must` · `R1` · `Draft` · Verify: Unit, GameTest · Trace: CLIENT-007, CLIENT-010, NFR-045

Every sound event `zas` and `zas_atmos` register shall be defined in their
`sounds.json`, every file an event names shall exist, every file shipped shall
be named by an event, and every event shall have a subtitle in `en_us`. An event
whose `sounds.json` sets a range past 16 blocks shall be registered with that
range.

- **Rationale:** Inferred: a missing file only logs a warning and plays silence,
  which nobody can catch without hearing it. Subtitles are how a player who can't
  hear a sound still gets it, and they must be translatable (CLIENT-007). Files
  nothing plays would only grow the jar. A server only sends a sound out to its
  event's range, so a longer range in `sounds.json` alone is never heard out there.
- **Verify:** Unit tests check the files and subtitles; a GameTest checks the
  registered events against `sounds.json`.

*2026-11-05: added for review, with the behaviour built.
`client_020_every_event_names_files_that_exist`,
`client_020_every_file_is_named_by_an_event` and
`client_020_every_event_has_a_translated_subtitle` (Unit, one set per mod), and
`client_020_registered_events_are_defined`,
`client_020_atmos_registered_events_are_defined` and
`client_020_atmos_event_ranges_match_sounds_json` (GameTests).*

#### CLIENT-021 · Sound files are mono Vorbis
`Must` · `R1` · `Draft` · Verify: Unit · Trace: CLIENT-010

Every sound file `zas` and `zas_atmos` ship shall be mono Ogg Vorbis, readable
by Minecraft's decoder.

- **Rationale:** Inferred: Minecraft only attenuates mono sounds with distance,
  so a stereo file would be as loud across the base as beside the device, which
  breaks every range this round sets.
- **Verify:** A Unit test reads each file's Vorbis header for the channel count;
  another decodes each file with the game's decoder.

*2026-11-05: added for review, with the behaviour built.
`client_021_every_file_is_mono_vorbis` and
`client_021_every_file_decodes_with_the_games_decoder` (Unit, one set per mod).*

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
`Must` · `R0` · `Implemented` · Verify: Manual · Depends: CLIENT-005

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

*2026-11-01: they can now: an open door's edge is a zone boundary (GEO-007),
and `client_011_door_edge_is_a_zone_boundary` (GameTest) checks the payload
marks it so. It stays Accepted until the owner checks the boundary colour by
hand.*

*2026-11-01: Implemented. The owner checked by hand, in the dev client on main,
that the boundary edges around doors and trapdoors are coloured and labelled:
blue for a zone boundary and orange for an ambient boundary
([#31](https://github.com/west3436/zas-cubed/issues/31#issuecomment-6566381690)).*

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
`Must` · `R0` · `Implemented` · Verify: Manual · Depends: CLIENT-005

The overlay shall mark hotspots.

- **Rationale:** Inferred: hotspots aren't always visible blocks, and fire
  bugs are hard to debug without them.
- **Verify:** Manual check during a gas fire.

*2026-10-31: The overlay has a hotspot layer (#98), but nothing produces
hotspots before FIRE (R1), so this stays Accepted until it can be checked
during a gas fire.*

*2026-11-04: Implemented. The owner checked it during a gas fire in the fire
round's test world.*

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
