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

*2026-11-06: This replaces "Nothing seals breathing" above: the breath mask and the
softsuit helmet have sealed it since
[#182](https://github.com/west3436/zas-cubed/pull/182) (LIFE-021). The HUD doesn't
show for them yet and there's still no analyzer (CONT-034), so this stays Accepted.*

*2026-11-06: This replaces "The HUD doesn't show for them yet" above. The sealed-head part
is built: the server sends whether the player's breathing is sealed (LIFE-021), by a
capability or by protection data, so a breath mask or softsuit helmet on the head shows the
HUD and taking it off hides it. The analyzer has its way in: an item in `#zas:shows_hud`
([datapacks.md](../datapacks.md#item-tags)) shows the HUD while held in either hand, and the
analyzer joins the tag when it's built. A creative player gets both triggers and still no
alerts, and a spectator neither. With no alert, the HUD shows only the internals indicator
(CLIENT-009), since a reading with no alert isn't shown (CLIENT-008).
`client_001_a_sealed_head_shows_the_hud`, `client_001_a_held_tag_item_shows_the_hud`,
`client_001_creative_is_shown_the_hud_and_a_spectator_is_not` and
`client_001_the_mask_and_helmet_show_the_hud` (GameTests) pass. Left: the Manual check with
a sealed helmet and an analyzer, and with F1, so this stays Accepted.*

#### CLIENT-008 · HUD readings and thresholds
`Must` · `R1` · `Implemented` · Verify: Manual, GameTest · Trace: LIFE-003, LIFE-004, LIFE-010, LIFE-011, LIFE-014, LIFE-041

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

*2026-11-06: Implemented. The owner checked the display in a test world with a
room at each reading's warning and danger levels: each showed the icon its sign
named, still at a warning and flashing at a danger.*

*2026-11-06: The 2026-11-02 note's list leaves out a fifth GameTest,
`client_008_a_filter_hides_the_gas_it_stops`: a filter's wearer is shown no toxin
alert for the gas it keeps out of its breath (API-022).*

#### CLIENT-009 · Internals indicator
`Must` · `R1` · `Accepted` · Verify: Manual · Trace: LIFE-029, LIFE-028

While the HUD is shown, it shall show whether internals are on (LIFE-029) and
the pressure of the tank they draw from (LIFE-028).

- **Rationale:** Inferred: a player on internals needs to know how much air is
  left.
- **Verify:** Manual check of the indicator while a tank drains.

*2026-11-06: Built ([#38](https://github.com/west3436/zas-cubed/issues/38)). A row above
the alert column, where a full column doesn't reach it and it doesn't move the alerts, has
vg's internals icon and the pressure of the first tank found (LIFE-028) in the display units
(CLIENT-006), or no value without a tank. The icon has three states: off, with no hose or
tile; on with the tank delivering, on a green tile; and on but failing with no seal, no tank
or a tank that can't deliver, flashing between a red tile and the off icon (CLIENT-014). The
pressure shows with internals off too, so a tank can be checked before switching on. It
reaches the client at 1 kPa (DATA-014).
`client_009_internals_and_the_tanks_pressure`, `client_009_an_alert_shows_the_indicator`,
`client_009_internals_update_while_the_atmosphere_is_unresolved`,
`client_009_payload_round_trips` and `client_001_the_mask_and_helmet_show_the_hud`
(GameTests) pass. Left: the Manual check while a tank drains, so this stays Accepted.*

#### CLIENT-014 · Not colour alone
`Should` · `R1` · `Implemented` · Verify: Manual

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

*2026-11-06: Implemented. The owner went round the same rooms as CLIENT-008's
check under Windows' greyscale filter, and could tell warning from danger, and low
from high, with no colour to help.*

## Effects

#### CLIENT-002 · Gas visibility
`Should` · `R2` · `Deferred` — moved to R2 (lean scope; only H₂O vapor is plausibly visible among the built-in gases, D-42, and it would need per-cell zone membership synced to clients), 2026-10-31. · Verify: Manual · Trace: CORE-010

Gases with visibility data shall tint or fog the cells of zones where they
exceed their threshold. Example: water vapor as mist. Addon gases such as
Cl₂ (yellow-green) define their own colour.

#### CLIENT-003 · Airflow particles
`Should` · `R1` · `Implemented` · Verify: Manual · Trace: FLOW-001, FLOW-011, DATA-015

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

*2026-11-05: Implemented, on the owner's check in the sound and particle world:
the smoke at a breach, and fewer then none under the Decreased and Minimal
settings. Nobody walked past 32 blocks by hand, so
`client_003_players_within_32_blocks_are_sent_the_breach` stands for that part.*

#### CLIENT-010 · Airflow sound
`Should` · `R1` · `Implemented` · Verify: Manual · Trace: FLOW-001, FLOW-011, DATA-015

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

*2026-11-05: Implemented, on the owner's check in the sound and particle world: the
hiss at a cupboard, a big room and a weak leak, fading by 18 blocks, stopping when
sealed, and fading away as the listener's air thins. The owner passed the loudness
as built, so the rule stays moles per second with no room-size term, and a weak leak
stays as quiet as it is.*

#### CLIENT-004 · Low-pressure sound
`Could` · `R2` · `Deferred` — moved to R2 (lean scope; damping every sound needs sound-engine hooks, and no R1 scenario needs it), 2026-10-31. · Verify: Manual

Sounds heard by a player in low pressure shall be attenuated, falling silent
in vacuum. Sound from the player's own suit is exempt.

*2026-11-05: The owner decided only our new sounds fade in thin air, and that
is CLIENT-016. Damping vanilla's sounds, and the suit's exemption, stay
deferred here.*

#### CLIENT-015 · Looping sounds
`Should` · `R1` · `Implemented` · Verify: Unit, Manual · Trace: CLIENT-010, API-061

A client shall play at most 8 looping sounds at once, chosen from those
requested within their range: the ones that will play loudest at the player,
counting the volume in `sounds.json` and the player's sliders, which among loops
of one volume and range are the nearest. Each shall fade over 0.5 s
as it starts, stops or changes volume, and shall stop within 0.7 s of its last
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

*2026-11-05: Choosing now counts what a loop will play at. It compared requested
volumes, but the engine then multiplies by the event's volume in `sounds.json`,
which sets the device loops' levels, so a pump at 0.35 there ranked about 3 times
louder than it plays. By calculation, 8 pumps working hard within 5 blocks took
every slot from a breach louder than any of them 2 blocks away. `ClientLoops` now
gives the book that volume times the slider as a weight, 1 for an event with no
sound data. `LoopBookTest` and `ClientLoopsTest` (Unit) check it. The client side
hasn't run, so what plays is still unverified.*

*2026-11-05: The owner, after playing it: "The sound persists longer than the
animation does; they should be more in sync". A request now lasts 4 game ticks
rather than 10, so a loop is silent 14 ticks after its last request, down from 20:
the lease, then the fade. Airflow, device loops and the generator ask every game
tick, and the fire alarm while it sounds, so a shorter lease doesn't touch them
while they play. The gas fire asked every 5, which would have dipped each loop
between looks, so it now looks every 4 (FIRE-036). `LoopBookTest` and
`FireLoopGroupsTest` (Unit) check the 14 ticks and that a steady fire doesn't dip.
Nobody has heard this, so the Manual part stays open.*

*2026-11-05: accepted by the owner. It stays Accepted until a client shows no loop
remains after leaving a world: the world was never left and reopened in the owner's
playthrough.*

*2026-11-06: Implemented, on the owner's check in the R1 round's test world:
every loop stopped at Save and Quit, the title screen was silent, and the loops
came back when the world reopened. The log shows the world left and reopened
this time.*

#### CLIENT-016 · Thin air
`Should` · `R1` · `Implemented` · Verify: Unit, GameTest, Manual · Trace: CLIENT-004, CLIENT-010, API-060 · Depends: DATA-019

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
  vacuum leaves no voice playing; `ThinAirOneShotsTest` (Unit) checks which
  one-shots fade and by how much; `client_016_*` (GameTest) check the factor in
  rooms and in open air; Manual checks how the fade sounds, and that a real
  one-shot from a server fades.

*2026-11-05: added for review, with the curve built (`sound.ThinAir`), for the
owner to judge by ear: 0 at 1 kPa or below, 0.08 at 5, 0.18 at 10, 0.39 at 20,
0.59 at 30, 0.80 at 40 and 1 from 50. It reads the air, not what reaches the
player, so a sealed suit in a vacuum hears silence. The open air reads as its
dimension's ambient pressure, so it is normal air in a breathable dimension and
a vacuum in an airless one. Nothing has been heard yet, so the Manual part is
open.*

*2026-11-05: one-shots fade too, on the client as each sound starts
(`client.ThinAirOneShots`, on NeoForge's `PlaySoundEvent`), for every event in a
namespace a mod gives `ClientSounds.fadeInThinAir` (API-060). A server plays a
sound for everyone in range and only each client knows its own player's air, so
nothing that plays a sound has to do anything, and it must not multiply by the
factor as well. In a vacuum the sound is dropped, and its subtitle with it. A
sound above volume 1 keeps full gain and loses range until the factor brings it
below 1, since the engine works out both from the volume, so a near blast at
volume 4 starts to lose gain at a factor of 0.25.*

*2026-11-05: accepted by the owner, and Implemented: its tests pass, and the owner
passed it in the sound and particle world.*

#### CLIENT-017 · Gas particle limits
`Should` · `R1` · `Implemented` · Verify: Unit, Manual · Trace: D-53

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

*2026-11-05: accepted by the owner, and Implemented: its tests pass, and the owner
passed it in the sound and particle world.*

#### CLIENT-018 · Spray, leak and burst particles
`Should` · `R1` · `Implemented` · Verify: Unit, GameTest, Manual · Trace: CONT-037, CONT-038, CONT-085, CONT-108 · Depends: CLIENT-017

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

*2026-11-05: from the owner's playtest of the sound and particle world, on a leak's
jet picking a new direction each atmos tick: "Just have it choose one direction." A
leak now chooses one as it starts and keeps it until it stops. The tank's
`zas_atmos:leaking` mark holds it, so a tank leaking again chooses again.
`client_018_a_leak_keeps_one_direction` (GameTest) passes. The owner chose to skip a
retest, so nobody has seen the result.*

*2026-11-05: accepted by the owner. It stays Accepted until someone sees a leak keep
one direction.*

*2026-11-06: Implemented, on the owner's check in the R1 round's test world: a
leak's jet kept one direction for the whole leak, with no twitching.*

#### CLIENT-019 · Flow particles
`Should` · `R1` · `Implemented` · Verify: Unit, GameTest, Manual · Trace: CONT-004, CONT-014, CONT-015, [#164](https://github.com/west3436/zas-cubed/issues/164) · Depends: CLIENT-017, DATA-017, DATA-018

An open pipe end (CONT-004) or passive vent (CONT-016) moving at least 0.25 mol/s
net shall show particles scaled by its flow, moving out of its mouth while gas
leaves and into it while gas enters, and none below that. A vent or scrubber at
activity level working or
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

*2026-11-05: A passive vent now shows as an open end does, so that its loop has
particles to go with (CONT-125). The jet starts 0.55 blocks from the block's middle,
just in front of the middle of its grille, and looks the way the grille does.
Being an open end's jet, it comes from one point rather than across the grille, as
a vent's does: for the owner to judge by eye. It counts toward the same 20 flowing
jets a second, so it adds nothing to the cap on bytes.
`client_019_a_passive_vent_sends_a_jet_at_its_grille` (GameTest) passes. Nobody has
seen it.*

*2026-11-05: A pump has no particles of its own, by the owner's decision: what shows
is the flow out of the open ends of the pipes on it. In the open air, a running
pressure pump with a pipe on each side, at its default 101.325 kPa, reads
`TARGET_REACHED` and sends no jets over 10 atmos ticks, because the air already
holds its output there. Set to 300 kPa it sends one out of its output's end and
one in at its input's. A volume pump at its default 100 L/s sends both at once,
since it moves its rate regardless. `client_019_a_pressure_pump_shows_flow_only_past_its_target`
and `client_019_a_volume_pump_shows_flow_at_its_default_rate` (GameTests) pass.*

*2026-11-05: accepted by the owner, and Implemented: its tests pass, and the owner
passed it in the sound and particle world.*

## Sound files

#### CLIENT-020 · Sound events resolve
`Must` · `R1` · `Implemented` · Verify: Unit, GameTest · Trace: CLIENT-007, CLIENT-010, NFR-045

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

*2026-11-05: accepted by the owner, and Implemented: its tests pass.*

#### CLIENT-021 · Sound files are mono Vorbis
`Must` · `R1` · `Implemented` · Verify: Unit · Trace: CLIENT-010

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

*2026-11-05: accepted by the owner, and Implemented: its tests pass.*

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
- pressure: kPa (default), atm, bar or psi
- temperature: °C (default), K or °F

They apply to the HUD, the analyzer and device GUIs. Commands always print kPa
and K.

- **Rationale:** Inferred: players think in different units; commands stay in
  kPa and K so logs and bug reports are comparable (A-04).
- **Verify:** Manual check of each unit in the HUD, analyzer and a GUI, and
  that commands print kPa and K.

*2026-11-06: the config's temperature default was K, and is now °C as this says, so
the HUD and debug overlay change with it. Other mods' GUIs read the units through
API-063. The area air controller's window is the first device GUI to use them.*

*2026-11-06: The owner's decision: bar joins the list, as the config already offers it.*

#### CLIENT-022 · TGUI look for device GUIs
`Should` · `R1` · `Implemented` · Verify: Manual · Trace: D-76, CONT-074

A device's window shall look like SS13's TGUI: a title bar with a status eye and a
close button, titled sections, labelled lists, tabs, and TGUI's colours and proportions,
with text in DejaVu Sans that stays sharp at every GUI scale. A window shall fit a
320 × 240 GUI-pixel screen, the least any screen has at Auto GUI scale.

- **Rationale:** Decision: D-76.
- **Verify:** Manual check of each window at GUI scales 2, 3 and 4 and at Auto.

*2026-11-06: added for review. Every device window built so far uses the kit in
`client.tgui`: the area air controller, vent, scrubber, heater, air conditioner, both
pumps, filter, mixer, canister, both portables, generator, gas tank and TTV. Their text
is kept short, with graphics where they say as much. The central controller, electrolyzer
and analyzer have no window yet because they don't exist yet.*

*2026-11-06: every one of those windows passed the owner's check in the game, with the
filter and a TTV checked at GUI scales 2, 3, 4 and Auto. Accepted by the owner, and
Implemented.*

#### CLIENT-007 · Localization
`Must` · `R1` · `Implemented` · Verify: Inspect

All player-facing text shall be translatable, with en_us as the baseline.

- **Rationale:** Inferred: standard practice for Minecraft mods, so the
  community can translate.
- **Verify:** Inspect: a check fails the build on hard-coded player-facing
  strings.

*2026-11-06: Implemented. `PlayerTextTest`, among `zas-atmos`'s unit tests in `build`, reads
both mods' compiled classes and follows each string that becomes text back to where it's
written. `client_007_no_hard_coded_player_text` fails on one with a letter in it, and
`client_007_every_key_the_code_spells_out_is_in_en_us` on a key en_us lacks. The
`client_007_the_scan_*` tests hold what it catches and allows. Lists at the top of the test
allow unit symbols, who paused a dimension and why, which are logged and saved in the world,
and a datapack problem's line, which is also the log's. `/zas` output is otherwise keyed, so
the command package has no exemption. The mods' descriptions in the mods list have keys too;
NeoForge shows a mod's display name as written, so that can't. The example addon is never
published, so it isn't scanned.*
