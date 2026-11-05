# vgstation13's sounds, and which ZAS³ uses

Source: `vgstation-coders/vgstation13`, branch `Bleeding-Edge`, commit
`23f25f386f7a` (2026-10-02). Its code is AGPL/GPL, so this note describes
behaviour in prose and no code enters the repository (NFR-040). The oggs are
assets under CC BY-SA 3.0 unless marked otherwise, which ZAS³ can adapt under
CC BY-SA 4.0 (D-56, D-62, NFR-041).

Every other track in the sound round works from this note, not from vg's code.
Nobody can hear anything where this was written: everything below about a
sound is what vg does with it or what was measured from the file, never how it
sounds. The owner judges the sounds in the round's combined test world.

## What vg does with sound

- **Two systems.** Most sounds are one-shots played at a point, with a volume, a
  range and usually a random change of frequency. A newer system (2025) lets an
  object hold named sounds and start or stop them, looped or not, for whoever is
  in range.
- **Very few loops.** Only the canister's release hiss and the cryo tube's
  bubbling loop on a machine. A weather effect loops wind, rain or snow for
  everyone in the weather. The power storage unit's hum is written but switched
  off in the code, and only its contactor switch still plays. The PR that added
  these loops noted that many loud repeating sounds close together were a
  cacophony, and that it would avoid them on principle.
- **Most atmos devices are silent.** Pumps, vents, scrubbers, filters, mixers,
  valves, freezers and heaters, connector ports and burst pipes play nothing in
  vg, beyond the wrench sound when one is built or moved. So most device rows
  below are vg's nearest sound, not vg's sound for that device, and they share
  files at different pitches, as the owner approved.
- **Airflow used to have a sound.** The wind files were added in 2012 "for
  airflow": every few atmos ticks a connection played one wind one-shot at a
  random border tile, picked from the size of its pressure difference in four
  bands (2 up to the weakest band, then 3, 4, and 5 for the strongest). That code
  is gone, and airflow is silent in vg now. `wind_4_1` and `wind_5_1` survive in
  weather (dust storms and sand storms) and in two cult events, and nothing plays
  the others.
- **Thin air.** vg's newer system scales a sound by the pressure at the sound's
  source, and again at the listener, as a fraction of one atmosphere, capped
  at 1. ZAS³ fades only its own new sounds, by the listener's air alone (the
  owner's decision; P2's work).
- **The canister's hiss** gets louder with the release pressure's excess over
  the air around the canister, as a ratio to that air's pressure, and it stops
  when the valve closes.
- **Explosions** are chosen per listener. Inside the blast radius plus about a
  screen's range, a listener hears a near sound, one of six if the blast has a
  devastation range and one of three small ones if it doesn't. Beyond that, out
  to a distance that grows with the blast's size (20 tiles per tile of
  devastation range plus 5 per tile of heavy range), the far sound plays, at a
  volume of 30 to 50 out of 100, plus 50 in the nearer half. Everyone in one blast
  gets the same random frequency. Players on the level outside even that hear
  the far sound unless they're in space, or deaf.
- **Gas fire is silent.** vg's fire itself plays nothing, and neither does gas
  catching fire. The closest are a campfire's crackle (three parts, one picked
  at random each tick while it burns) and the flare-up when a clumsy player sets
  themself alight.

## Provenance

vg's README puts its assets under CC BY-SA 3.0 unless specified otherwise, but
its `sound/README.md` says most of the record of who made them is lost. Each
file picked here was checked by its git history, its pull request where it has
one, and the tags inside the file. "No sign" means none of those points
elsewhere. It doesn't prove where a file came from.

**Not used, because something points elsewhere:**

| File | What points elsewhere |
|---|---|
| `machines/effects/air_alarm_warning`, `air_alarm_danger` | vg #37730 says they are Airbus and MD-11 autopilot disconnect sounds. The tags say Sound Forge 4.0, 1999. |
| `machines/looping/gas_hiss`, `machines/looping/smes_hum`, `machines/effects/smes_switch`, `machines/looping/bubbles` | The same PR, which names no source. All carry the same tag left by an MP4 container (`TXXX=isommp42` or `iso6mp41`) as the two aircraft files. `bubbles` also says Sound Forge 4.0, 1999. |
| `effects/Explosion_Small1` to `3`, `effects/explosionsmallfar` | Added with `weapons/stickybomb_det` in a "Stickybomb Launcher" commit, a Team Fortress 2 weapon, with no source. `Explosion_Small1` says Sound Forge 5.0, 2003, twelve years before the commit. |
| `effects/Explosion3` to `6`, `effects/bamfgas` | One 2014 sound batch with no sources. `Explosion5` is tagged `TITLE=Track 81`, `ALBUM=Album`, `ARTIST=Artist`, as a track of an effects disc would be. The others have no sign themselves and are left out only as its siblings. |
| `misc/click`, `weapons/flamethrower`, `machines/compactor` | Tags from a DASH stream (`major_brand=dash`), as a video download leaves. |
| `effects/vacuum` | Tags from an M4A made by Google's muxer. |
| `effects/blow` | Tagged `TITLE=Age of empires 2 villager spawn sound`. |
| `misc/TestLoop1` | FL Studio music from a roundstart-music commit. |

**Used, with a gap:** `misc/fire_alarm`, for the fire alarm. It came in vg #33473
(`bc8e6c73f7`, 2022), whose author is this project's owner. The PR names no
source, and the file's tags are the emptied keys of an MP4 file. The owner says
(2026-11-05) that it came from Freesound under a suitable license. The original
sound and its author aren't recorded. See [For review](#for-review).

**Used, with a gap:** `machines/charge_finish`, for the recharger filling. It came in
vg #25079 (`83fc373f7`, 2019), which links a Discord-hosted `alert12.mp3` and names
no source. It was left out until the owner asked for it after their playtest, and
decided (2026-11-05) to credit it to vgstation13 contributors: it's likely a sound a
vg player made, or one of SS13's old assets, shared and changed for so long that its
author is unknown.

**Used, no sign:** everything else in the tables. Most came in vg's first commit
(`662c08272a`, 2010), an import with no record of each file. The wind files came
in `4053e33a3f` (2012), and `misc/comfyfire` in `484b82f074` (2015), whose
message credits sprites but no sounds. Of the rest, `effects/extinguish` and
`refill` came in `456a9d6da6` (2010), `effects/alert` in `7a4b9e09bc` (2011),
`machines/pressurehiss` in `ad47dc7a62` (2016), `machines/door_bolt` and
`door_unbolt` in `c5c5fe9ca0` (2019), and `items/assemblytick2` in `1c313df696`
(2025).

## Airflow

The owner's decision: loudness follows moles per second through the opening,
pressure difference sets pitch, and a listener in a vacuum hears none. `Class`
is FLOW-011's. All are in `assets/zas/sounds/airflow/`.

| Event | Use | vg file | vg's use | Source | Shipped loop |
|---|---|---|---|---|---|
| `zas:airflow.hiss` | Any opening with flow: the bright cue that says where the leak is | `effects/smoke` | Smoke grenades and smoke reactions: gas pouring out, 2010. No match for a leak as such; the closest steady hiss with a clean history | 3.56 s, 2 ch | 2.77 s |
| `zas:airflow.light` | Light class, from 20 kPa | `effects/wind/wind_2_1` | Early ZAS's weakest band of airflow wind | 5.46 s, 2 ch | 2.50 s |
| `zas:airflow.mob` | Mob class, from 65 kPa | `effects/wind/wind_4_1` | Early ZAS's third band; dust storm weather | 7.66 s, 2 ch | 5.15 s |
| `zas:airflow.heavy` | Heavy class, from 85 kPa | `effects/wind/wind_5_1` | Early ZAS's strongest band; sand storm weather and cult events | 10.37 s, 2 ch | 6.90 s |

A suggestion for P3: play the hiss at every active opening, and add the wind of
the opening's class. The wind files carry almost nothing above about 400 Hz
(measured brightness 170 to 220 Hz), so alone they may not carry on small
speakers or say where the leak is, while the hiss (about 3.2 kHz) has no body.
Pitch from the pressure difference multiplies whatever `sounds.json` sets.

## Fire

All are in `assets/zas/sounds/fire/`.

| Event | vg file | vg's use | Source | Shipped |
|---|---|---|---|---|
| `zas:fire.loop` | `misc/comfyfire` | **No match: vg's gas fire is silent.** The closest is the campfire's crackle. This long file holds the same material as the three parts vg plays. It is a crackle, not a roar | 15.88 s, 2 ch | 15.48 s loop |
| `zas:fire.ignite` | `effects/bamf` | **No match: gas catching fire is silent in vg.** The closest is the flare-up when a player catches fire by accident, also a reagent splash exploding. The right channel only, as averaging the two partly cancels them | 2.21 s, 2 ch | one-shot |

## Gas blasts

All are in `assets/zas_atmos/sounds/blast/`, not `zas`: only `zas_atmos` has
gas containers that explode (CONT-107).

| Event | vg file | vg's use | Source | Pitch, volume |
|---|---|---|---|---|
| `zas_atmos:blast.near` | `effects/Explosion1`, `effects/Explosion2` | Two of the six near sounds of a blast with a devastation range | 3.19 s and 3.94 s, 2 ch | 1, 1 |
| `zas_atmos:blast.small` | The same two | **vg's small sounds aren't usable** (see above), so these are its big ones, pitched up | | 1.5, 0.6 |
| `zas_atmos:blast.far` | `effects/explosionfar` | The far sound of a big blast, out to the far distance | 7.56 s, 2 ch | 1, 1; range 256 |
| `zas_atmos:blast.small_far` | The same | **vg's small far sound isn't usable**, so this is its big one, pitched up | | 1.5, 0.5; range 128 |

Pass volume 4 for a near blast, as vanilla's explosion does: that gives 64 blocks
of range. The far events carry their range themselves, so pass volume 1. A
`level.playSound` reaches everyone in range, near listeners included, so to play
one or the other per listener, as vg does, send each player only theirs.

## Devices

All are in `assets/zas_atmos/sounds/device/`, and each event's pitch and volume
are in `sounds.json`, so a track plays the event at volume 1 and pitch 1. **Bold**
marks a device where vg has nothing, so this is the closest sound and not a
match.

Shared running loops:

| File | vg file | vg's use | Source | Shipped loop |
|---|---|---|---|---|
| `device/hum` | `ambience/ambiatm1` | Ambience from the atmospherics room, not played by any code now. Steady, within about 2 dB | 14.30 s, 2 ch | 13.50 s |
| `device/rush` | `machines/hiss` | Disposal chutes flushing and blowing out, inflatables deflating, vending machines | 2.64 s, 1 ch | 1.65 s |
| `device/hiss` | `effects/smoke` | As `airflow.hiss` | 3.56 s, 2 ch | 2.77 s |
| `device/engine` | `ambience/ambieng1` | Engine room ambience, not played by any code now. A steady pulse every 0.410 s, and the loop is cut to 15 of them | 38.82 s, 2 ch | 6.15 s |

Running loops. `sounds.json` sets each loop's file, pitch and volume, but **not its
range**. A loop plays through the client's loop manager with no attenuation of its
own, so its range comes from the `LoopRequest` that starts it, about 8 blocks by the
owner's decision.

| Event | File | Pitch | Volume | vg |
|---|---|---|---|---|
| `zas_atmos:device.pump.run` | hum | 1 | 0.35 | **Silent** |
| `zas_atmos:device.volume_pump.run` | hum | 0.85 | 0.35 | **Silent** |
| `zas_atmos:device.filter.run` | hum | 1.2 | 0.3 | **Silent** |
| `zas_atmos:device.mixer.run` | hum | 1.35 | 0.3 | **Silent** |
| `zas_atmos:device.vent.run` | rush | 1 | 0.35 | **Silent** |
| `zas_atmos:device.passive_vent.run` | rush | 0.75 | 0.2 | **Silent** |
| `zas_atmos:device.scrubber.run` | hiss | 1 | 0.25 | **Silent** |
| `zas_atmos:device.canister.run` | hiss | 1.25 | 0.4 | vg's canister hisses while its valve is open, with a file this round can't use, so this is the same idea with `smoke` |
| `zas_atmos:device.pipe.leak` | hiss | 1.4 | 0.3 | **Silent** |
| `zas_atmos:device.heater.run` | hum | 0.7 | 0.3 | **Silent**, unless the heater is a campfire |
| `zas_atmos:device.air_conditioner.run` | rush | 1.15 | 0.3 | **Silent** |
| `zas_atmos:device.portable_pump.run` | hum | 0.92 | 0.4 | **Silent** |
| `zas_atmos:device.portable_scrubber.run` | hiss | 0.85 | 0.3 | **Silent** |
| `zas_atmos:device.recharger.run` | hum | 1.8 | 0.2 | **Silent** while charging |
| `zas_atmos:device.generator.run` | engine | 1 | 0.45 | **Silent**; no generator of vg's has a sound |

On and off, all `machines/click` (0.37 s, 2 ch), shipped as `device/click`. **No
device-specific match:** vg's space heater clicks when it shuts itself off, and many
machines' buttons use it. Each device has a pitch of its own, lower for off.

| Events | On pitch | Off pitch | Volume |
|---|---|---|---|
| `zas_atmos:device.pump.on`, `.off` | 1.1 | 0.9 | 0.35 |
| `zas_atmos:device.volume_pump.on`, `.off` | 1 | 0.8 | 0.35 |
| `zas_atmos:device.vent.on`, `.off` | 1.2 | 1 | 0.35 |
| `zas_atmos:device.scrubber.on`, `.off` | 1.3 | 1.1 | 0.35 |
| `zas_atmos:device.filter.on`, `.off` | 1.4 | 1.2 | 0.35 |
| `zas_atmos:device.mixer.on`, `.off` | 1.5 | 1.3 | 0.35 |
| `zas_atmos:device.heater.on`, `.off` | 0.9 | 0.75 | 0.35 |
| `zas_atmos:device.air_conditioner.on`, `.off` | 1 | 0.85 | 0.35 |
| `zas_atmos:device.portable_pump.on`, `.off` | 1.15 | 0.95 | 0.35 |
| `zas_atmos:device.portable_scrubber.on`, `.off` | 1.25 | 1.05 | 0.35 |
| `zas_atmos:device.generator.on`, `.off` | 0.8 | 0.7 | 0.35 |
| `zas_atmos:device.controller.on`, `.off` | 1.6 | 1.4 | 0.35 |
| `zas_atmos:device.recharger.on`, `.off` | 1.7 | 1.5 | 0.3 |

The passive vent has no switch, so it has only its loop.

Event sounds. Every event below is in `zas_atmos`:

| Event | Pitch | Volume | vg file | vg's use | Source |
|---|---|---|---|---|---|
| `device.valve.open`, `.close` | 1.4 | 0.4 | `machines/door_unbolt`, `machines/door_bolt` | **Valves are silent.** The airlock's bolts lifting and dropping | 1.02 s, 2 ch |
| `device.redstone_valve.open`, `.close` | 1.7 | 0.4 | The same | As above | |
| `device.canister.open`, `.close` | 0.8, 0.7 | 0.35 | `machines/click` | **Silent**, though vg's hiss starts and stops | 0.37 s, 2 ch |
| `device.port.connect`, `.disconnect` | 1, 0.9 | 0.4 | `items/Ratchet` | vg's wrench, which is what connects a port | 1.41 s, 2 ch |
| `device.pipe.burst` | 0.85 | 0.8 | `effects/bamf` | **Pipes burst silently.** vg's flare-up and reagent splash exploding, the same file as `fire.ignite`, as the closer `effects/bamfgas` is [left out](#provenance) | 2.21 s, 2 ch |
| `device.container.burst` | 1 | 0.9 | `effects/bamf` | **No match.** As above; vg's canisters and tanks give only the spray below | |
| `device.container.leak` | 1.3 | 0.35 | `effects/spray` | A canister destroyed; a tank rupturing, both very quiet | 1.46 s, 2 ch |
| `device.tank.open`, `.close` | 1.3, 1.1 | 0.3 | `machines/click` | **Silent.** A tank has no valve, only a distribution pressure, so these are for its supply starting and stopping | 0.37 s, 2 ch |
| `device.tank.low` | 1 | 0.35 | `effects/alert` | A tank almost out of gas, on inspecting it | 2.38 s, 1 ch |
| `device.igniter.click` | 1.5 | 0.35 | `machines/click` | The ignition switch clicks, with a file [left out](#provenance) | 0.37 s, 2 ch |
| `device.igniter.spark` | 1 | 0.7 | `effects/sparks1` to `4` | Any spark effect | 0.73 to 1.15 s, 2 ch |
| `device.fire_alarm.alarm` | 1 | 0.75; range 32 | `misc/fire_alarm` | A fire alarm going off, at most once per its delay | 4.01 s, 2 ch |
| `device.fire_alarm.reset` | 1.2 | 0.35 | `machines/click` | **Silent** | 0.37 s, 2 ch |
| `device.controller.warning` | 1 | 0.25 | `machines/twobeep` | **Weak.** vg's air alarm has its own warning sound, which is [left out](#provenance). Pinpointers, cargo and consoles | 0.29 s, 1 ch |
| `device.controller.danger` | 1 | 0.5; range 24 | `machines/warning-buzzer` | **Weak**, as above. Gas extractor warnings, consoles | 3.51 s, 1 ch |
| `device.firelock.open`, `.close` | 0.9, 0.8 | 0.5, 0.6 | `machines/airlock` | vg's firelock plays the default door sound | 1.28 s, 2 ch |
| `device.firelock.force` | 0.8 | 0.4 | `effects/bang` | **Weak: silent** apart from opening. A flashbang, or headbutting a door | 0.51 s, 2 ch |
| `device.recharger.done` | 1 | 0.3 | `machines/charge_finish` | vg's rechargers and cell chargers, as they fill. [It has a gap in its record](#provenance) | 0.34 s, 2 ch |
| `device.ttv.arm` | 1 | 0.7 | `weapons/armbomb` | A grenade being armed | 1.97 s, 2 ch |
| `device.ttv.tick` | 1 | 0.3 | `items/assemblytick2` | An assembly timer's tick. vg's valve itself is silent | 0.20 s, 2 ch |
| `device.ttv.open` | 1 | 0.6 | `machines/pressurehiss` | **Silent.** Cryo tubes, sleepers and suit machines opening | 1.69 s, 2 ch |
| `device.extinguisher.spray` | 1 | 0.5 | `effects/extinguish` | The fire extinguisher spraying | 1.62 s, 2 ch |
| `device.extinguisher.refill` | 1 | 0.5 | `effects/refill` | The fire extinguisher refilling | 1.27 s, 2 ch |
| `device.extinguisher.empty` | 1.7 | 0.3 | `machines/click` | **Silent** | 0.37 s, 2 ch |

## How the files are made and used

- **Mono.** Minecraft only attenuates mono sounds with distance, so a stereo file
  plays equally loud everywhere (CLIENT-021). Channels are averaged, or the
  louder one kept where averaging would cancel: only `effects/bamf`, whose average
  loses 4 dB against its louder channel.
- **Levels.** Loops are set to -20 dBFS RMS, or as near as a -1 dBFS peak allows,
  and one-shots to -1 dBFS peak, so relative levels live in `sounds.json`. For
  scale, vanilla's sounds measure: explosion -13 and -15 dBFS RMS, fire -34,
  click -27, fizz -24, conduit -22, beacon -29, portal -17. `fire.loop` comes out
  at -34, because the crackle's peaks limit it, the same as vanilla's fire. The
  volumes in `sounds.json` are starting points from these figures, not from
  listening: the click at 0.35 comes out about 3 dB above vanilla's lever.
- **Loops** are cut to a region and their ends joined with an equal-power
  crossfade of 0.4 to 1.5 s, which also removes the near-silence at both ends of
  the wind files. Measured after encoding, the step across each join is at most
  1.3 times the file's typical sample step. **None of the joins has been heard.**
  The shortest loops, `device/rush` (1.65 s) and `airflow.light` (2.50 s), are the
  likeliest to sound repetitive.
- **Static, not streamed.** No file sets `stream`. A streamed sound takes a
  channel from a pool of at most 8 shared with music and records, which fires and
  device loops would use up, while the static pool has the rest, usually over 200,
  and loops exactly. The longest loop is 15.5 s, under 1.4 MB when decoded.
- **Pitch** multiplies: what `sounds.json` sets, what the code passes, then the
  game clamps the result to 0.5 to 2.
- **A one-shot's range** on a client is its `attenuation_distance` (16 by default)
  times the volume passed, if that is above 1. A server sends a sound only out to
  the event's own range: 16 blocks, or 16 times a volume above 1, unless the event
  is registered with a fixed range. So `blast.far`, `blast.small_far`,
  `device.controller.danger` and `device.fire_alarm.alarm` are registered with the
  range their `sounds.json` sets, and a GameTest checks the two agree.
- **A loop's range** isn't in `sounds.json`: see [Devices](#devices).
- **Sliders.** Blocks on Blocks, airflow on Ambient/Environment.
- **Size.** 35 files, 842 KB in all.
- **The game's decoder reads them.** The test `client_021_…decodes…` runs every
  file through Minecraft's own decoder, as nothing here can play one.

## Swapping a sound

Replace the ogg under the same name, or change the `name`, `pitch` or `volume` in
`sounds.json`. No code changes, as events are named for what happens, not for the
file. A new file also needs its `CREDITS` entry and must be mono. A one-shot's range
past 16 blocks also needs the same number in `AtmosSounds`.

## For review

*2026-11-05: the owner accepted this list as it stands. The files left out over
provenance stay out, and `misc/fire_alarm` stays in with its gap, as there's no link to
add. The notes below are kept as the record, and a swap stays open if that changes.*

For the owner, when judging the combined world:

- **The most noticeable files:** `effects/smoke` and the wind files for airflow,
  `misc/comfyfire` for fire, `effects/bamf` for ignition and bursts,
  `effects/Explosion1`, `Explosion2` and `explosionfar` for blasts.
- **`machines/looping/gas_hiss`** is vg's own gas-release loop and the natural
  choice for the hiss, but it came in the same PR as two aircraft recordings,
  with the same MP4 tag. If you know it's fine, it's one file to swap, for the
  airflow hiss and the canister. The same goes for `smes_hum` and `smes_switch`.
- **`misc/fire_alarm`** has a gap in its record. You cleared it on 2026-11-05: from
  Freesound under a suitable license. The original sound and its author aren't
  recorded, and `CREDITS` says so. Some Freesound licenses need the author named,
  so if you find the original, its entry should gain its link and author.
- **Bursts use `effects/bamf`,** the same file as ignition. `effects/bamfgas` would
  have been closer, but it's left out with its 2014 batch. Pipe bursts are pitched
  lower, but a burst and an ignition may be hard to tell apart.
- **Weak matches:** the controller's alarms, the firelock being forced, the pumps'
  and the generator's loops, and the fire loop, which is a crackle and not a roar.
- **Small blasts** are vg's big explosion pitched up, not vg's small files.
