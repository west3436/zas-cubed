# Blocks and items that need textures

**Status:** list, 2026-11-01. No block or item is registered yet, so the ids
below are proposals.

This lists every block and item ZAS³ will add that needs a texture, with one
row for each. It comes from the requirements and from the owner's changes of
2026-11-01 in the review of #125, which the requirements don't have yet. Rows
from those changes have no requirement of their own, and their priority isn't
set. The changes made in #127, the area air controller and batteries, are in
the requirements (D-68, D-69).

Art follows D-67: vanilla JSON models on the 1/16-block grid, textures at 16
pixels per block (the exceptions are the wrench's 32×32 sprite, D-70,
the filter's and mixer's tops, D-71, the larger crafting components,
D-72, and the fire extinguisher's 32×32 sprite, D-74), flat
sprites for handheld items, and the standard armor
texture layout for wearables. Sources and licensing follow D-26 and D-39, with
each asset's provenance in `CREDITS` (NFR-045). Where each texture comes from,
and the order they're made in, is in the
[texture procurement plan](texture-procurement.md).

In the block tables, the **Item** column says what the block's item looks
like: the block's own model (no extra texture), a flat sprite (one more
texture), or none (the block has no item).

## R1

### Framework mod (`zas`)

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas:gas_fire` | Gas fire | Must | FIRE-018 | None | Animated, like vanilla fire. It burns in mid air, so it must look right with nothing under it. Crossed planes of flame and a see-through haze on every face, so a burning room fills with a glow (the owner's choice of 2026-11-02). |

### Content mod (`zas_atmos`): blocks

**Pipes and valves**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:pipe` | Pipe | Must | CONT-001, CONT-007 | Block model | A core plus an arm for each connected side. An unconnected end leaks (CONT-004) unless it's capped. Players dye pipes (the owner's choice, 2026-11-01; no requirement yet). Each face it leaks from shows a mouth: the tube flaring to an 8 px lip at the block edge, hollow and dark inside (the owner's choice of 2026-11-07, [#164](https://github.com/west3436/zas-cubed/issues/164)). That's the face opposite a pipe's one connection, or every face of a pipe with none. Runs, bends and tees have no mouth. The lip is a darker shade of the dye, like the flanges, and the inside isn't tinted. |
| `zas_atmos:pipe_cap` | Pipe end cap | Not set | None yet | Block model | Closes a pipe end so it doesn't leak (CONT-004). Dyed like the pipe. |
| `zas_atmos:radiator_pipe` | Radiator pipe | Must | CONT-143, HEAT-020 | Block model | Exchanges heat with its surroundings, like vg's heat-exchange pipe. It joins ordinary pipe only through the adaptor, so it must look clearly different from it: a finned tube in vg's near-black, not dyed, with no flanges (the owner's choice of 2026-11-02). Its item is the 3D straight piece, like the pipe's. |
| `zas_atmos:radiator_adaptor` | Radiator adaptor pipe | Must | CONT-145 | Block model | Joins an ordinary pipe to a radiator pipe. One end of each kind: inline, an undyed pipe's flange and tube at one end and the finned tube at the other. |
| `zas_atmos:manual_valve` | Manual valve | Must | CONT-010 | Block model | Open and closed. Inline on the pipe, with a straight lever across the pipe when closed and in line when open (the owner's choice of 2026-11-02). |
| `zas_atmos:redstone_valve` | Redstone valve | Must | CONT-010 | Block model | Open and closed. It should look different from the manual valve: a redstone trace round its body, lit while powered. |

**Devices**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:pressure_pump` | Pressure pump | Must | CONT-011 | Block model | On and off. The way it pumps must be easy to tell. Inline on the pipe, it shows it with arrows along its body (the owner's choice of 2026-11-02). |
| `zas_atmos:volume_pump` | Volume pump | Should | CONT-012 | Block model | On and off. As the pressure pump, but distinct from it: inline the same way, with a dark slot along its body where a lamp sweeps toward the output (the owner's choice of 2026-11-02). |
| `zas_atmos:filter` | Filter | Should | CONT-013 | Block model | On and off. Three ports: input, output and side outlet. Inline on the pipe, the side outlet right of the flow, or left when `zas_atmos:filter_mirrored` places it (D-77). |
| `zas_atmos:mixer` | Mixer | Should | CONT-077 | Block model | On and off. Three ports: two inputs and an output. Inline on the pipe like the filter, the side input right of the flow, or left when `zas_atmos:mixer_mirrored` places it (D-77). |
| `zas_atmos:vent` | Vent | Must | CONT-014 | Block model | Off, blowing and siphoning. Animated (CONT-070). Faces any of six ways; a pipe joins only its back. |
| `zas_atmos:scrubber` | Scrubber | Must | CONT-015, CONT-079 | Block model | Off, scrubbing, siphon and panic. Animated (CONT-070). Faces any of six ways, like the vent; a pipe joins only its back. |
| `zas_atmos:passive_vent` | Passive vent | Could | CONT-016 | Block model | Unpowered; one state. Faces any of six ways, like the vent; a pipe joins only its back. |
| `zas_atmos:connector_port` | Connector port | Must | CONT-083 | Block model | Free, and with a portable connected. Set in the floor, facing up; a pipe joins only its bottom. |
| `zas_atmos:igniter` | Igniter | Must | CONT-105 | Block model | Idle and active. A floor plate, like vg's. |
| `zas_atmos:heat_exchanger` | Heat exchanger | Must | CONT-147, CONT-148, HEAT-022 | Block model | vg's works in linked pairs that share one temperature ([vg §2](research/vg-atmos-content.md#2-devices)). Half a block deep against one face, with the pipe on the opposite face; it faces any of six ways, and a pair stands back to back 2 px apart. Its coil and pipe change colour with the temperature of the gas in that half: blue when cold, copper at room temperature, and red to yellow-white as it heats (the owner's choices of 2026-11-02). |
| `zas_atmos:airlock_controller` | Airlock controller | Not set | None yet | Flat sprite | Wall-mounted. Cycles an airlock with its vents and an air sensor. Idle and cycling. A small wall panel, like the fire alarm. |
| `zas_atmos:air_sensor` | Air sensor | Not set | None yet | Block model | Reads the gas where it's placed, for an airlock controller. A small wall panel: standby, cycling and alert. |

The heater and air conditioner are now portables (below).

**Portable atmospherics**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:canister` | Canister | Must | CONT-017 | Block model | Valve open and closed. Coloured as vg's canisters, and players dye them, so it needs a texture for each colour ([below](#content-mod-zas_atmos-creative-canisters)). Survival canisters are crafted empty (CONT-086); the filled ones are below. |
| `zas_atmos:portable_pump` | Portable pump | Should | CONT-018 | Block model | On and off. In one block, shorter than the canister (the owner's choice of 2026-11-02). |
| `zas_atmos:portable_scrubber` | Portable scrubber | Should | CONT-018 | Block model | On and off. In one block, shorter than the canister (the owner's choice of 2026-11-02). |
| `zas_atmos:heater` | Heater | Should | CONT-019 | Block model | On and off. A portable on a battery that heats the room, not piped gas (D-73). 14 px wide, the generator's size (the owner's choice of 2026-11-02). |
| `zas_atmos:air_conditioner` | Air conditioner | Should | CONT-019 | Block model | On and off. A portable that cools the room. It should look different from the heater. The heater's size, in white. |
| `zas_atmos:tank_holder` | Tank holder | Must | CONT-149 to CONT-151 | Block model | Holds up to 10 personal tanks and keeps their contents. It shows the tanks it holds, standing on two shelves of five, each tinted by its kind (the owner's choices of 2026-11-02). |

Portables connect to and disconnect from connector ports with the wrench.

**Gas sources and power**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:electrolyzer` | Water electrolyzer | Must | CONT-055, CONT-056 | Block model | On and off, and its water level, 0 to 4 buckets. Two glass gas columns on a water tank (the owner's choice of 2026-11-02). Each gas leaves by its own pipe port, on opposite sides (CONT-055), and fluid pipes join only the water inlet on its back (CONT-056). |
| `zas_atmos:generator` | Generator | Must | CONT-061, CONT-067 | Block model | Lit and unlit. In the style of SS13's P.A.C.M.A.N. |
| `zas_atmos:recharger` | Recharger | Should | CONT-113 | Block model | No battery, empty, and four charge steps, as vg's cell charger shows them (the owner's choice of 2026-11-02). Charges one battery (below) for the portables. |

The air compressor is gone (CONT-054), and so are the power cable and the
multipart block (D-68). Without the compressor, R1 gets gas from the electrolyzer
and from scrubbing and filtering (CONT-050).

**Area air controllers, airlocks and firelocks**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:area_air_controller` | Area air controller | Must | CONT-040, CONT-048, CONT-068 | Flat sprite | Wall-mounted. Normal, danger 1, danger 2 and unpowered. Was the air alarm. It also powers every atmospherics part in its zone or linked to it (D-68). |
| `zas_atmos:central_atmos_controller` | Central atmospherics controller | Should | CONT-115, CONT-123 | Block model | A standing console 2 blocks wide, 2 tall and 1 deep, after vg's atmospheric alert computer (`icons/obj/computer.dmi` `alert:0`, `alert:1`, `alert:2`, `alert:00`): a monitor, a keyboard shelf at desk height and a pedestal (the owner's choice of 2026-11-07). Four block models, lower and upper, left and right. Its screen shows normal, danger 1 somewhere, danger 2 somewhere, and unpowered, with a light for each of four zones. Its item is the whole console. Powers and runs the area air controllers around it (D-75). |
| `zas_atmos:fire_alarm` | Fire alarm | Should | CONT-044 | Flat sprite | Wall-mounted. Normal, alarm and unpowered. vg's small red box, not the controller's panel (the owner's choice of 2026-11-02). |
| `zas_atmos:airlock` | Airlock | Not set | None yet | Flat sprite | A zone-boundary door, worked by an airlock controller. Open, closed and bolted. Two sizes: 1 wide and 2 tall like a standard door, and 2 by 2. The 1 by 2 is vg's red external airlock, its leaf sliding up; the 2 by 2 is vg's glass two-tile door, which you can see through, its leaves parting sideways (the owner's choices of 2026-11-02). The 2 by 2 is proposed as a block of its own, `zas_atmos:large_airlock`, with its own item. |
| `zas_atmos:firelock` | Firelock | Must | CONT-043, CONT-093 | Flat sprite | Open, closed and locked down, which shows a warning. Modelled on vg's old full-tile firelock. It can be placed on a door, a vanilla door or an airlock, and is then drawn as an overlay on it, so it comes in the doors' sizes: 1 by 2 and 2 by 2. On its own it's 1 by 2, a frame the block's full depth with its panel in the middle (the owner's choice of 2026-11-01). |

Both door sizes and firelocks on doors are the owner's choices of 2026-11-01.
The requirements don't cover them yet, nor the airlock's bolted state, which
the controller would set while it cycles.

The wall-mounted blocks and the doors get flat sprites, as vanilla's torches
and doors do. Any of them could use its block model instead.

**Station walls and glass**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:station_wall` | Station wall | Should | CONT-129 | Block model | A full block, the same on every face. vg's plain wall (`icons/turf/walls.dmi` `metal0`) in vg's dark greys: a plate with a recessed rim, a bolt in each corner and a raised middle panel. |
| `zas_atmos:reinforced_station_wall` | Reinforced station wall | Should | CONT-130 | Block model | The station wall with vg's reinforcing rods (`rwall0`) crossing its middle and a boss where they meet. |
| `zas_atmos:reinforced_glass_block` | Reinforced glass | Should | CONT-131 | Block model | A full see-through block, after vg's full reinforced window (`icons/obj/structures/window.dmi` `frwindow0`): a blue-steel frame with white rods along it, and wired glass, a square wire mesh through a grey-blue pane (the owner's choice of 2026-11-07). Translucent. |

With Fusion installed, each of the three joins with blocks of its own kind (CONT-132). Each
has one more texture, a strip of five tiles in Fusion's pieced layout, in a built-in resource
pack: the rim or frame only on outside edges, as vg's joined walls and windows have it
(`metal15`, `rwall15`, `frwindow15`), and the reinforced wall's cross of rods on every
block. The owner approved them as drawn on 2026-11-07.

### Content mod (`zas_atmos`): creative canisters

Canisters pre-filled with each built-in gas (the 8 of D-42) exist only in
creative (CONT-086). Each is a canister holding one gas, not a new block.
Canisters are coloured as vg's, and players dye them by using a dye on one
(the owner's choice of 2026-11-01, which has no requirement yet). Each
creative canister comes in its gas's colour. The colours, and which dye
gives which, are in the
[texture procurement plan](texture-procurement.md#64-canister-colours).

| Proposed id | Gas | Priority | Requirement | Notes |
|---|---|---|---|---|
| `zas_atmos:canister` with `zas:oxygen` | O₂ | Must | CONT-086 | |
| `zas_atmos:canister` with `zas:nitrogen` | N₂ | Must | CONT-086 | |
| `zas_atmos:canister` with `zas:carbon_dioxide` | CO₂ | Must | CONT-086 | |
| `zas_atmos:canister` with `zas:nitrous_oxide` | N₂O | Must | CONT-086 | The gas id is proposed; `zas` ships only O₂, N₂ and CO₂ so far. |
| `zas_atmos:canister` with `zas:water_vapor` | H₂O vapor | Must | CONT-086 | The gas id is proposed. |
| `zas_atmos:canister` with `zas:hydrogen` | H₂ | Must | CONT-086 | The gas id is proposed. |
| `zas_atmos:canister` with `zas:carbon_monoxide` | CO | Must | CONT-086 | The gas id is proposed. |
| `zas_atmos:canister` with `zas:methane` | CH₄ | Must | CONT-086 | The gas id is proposed. |

### Content mod (`zas_atmos`): items

**Tanks**

| Proposed id | Name | Priority | Requirement | States and notes |
|---|---|---|---|---|
| `zas_atmos:oxygen_tank` | O₂ tank | Must | CONT-030 | Flat sprite. |
| `zas_atmos:emergency_oxygen_tank` | Emergency O₂ tank | Must | CONT-030 | Flat sprite. It holds 2 L against the others' 70 L, so it should look smaller. |
| `zas_atmos:air_tank` | Air tank | Must | CONT-030 | Flat sprite. Yellow, as vg's yellow tank (the owner's choice of 2026-11-01). |
| `zas_atmos:empty_tank` | Empty tank | Must | CONT-030 | Flat sprite. Grey. |
| `zas_atmos:tank_transfer_valve` | Tank transfer valve | Must | CONT-103, CONT-104 | Flat sprite, with variants for no tanks, one tank and two tanks, each tank tinted in code by its kind. Once primed it's an entity, like primed TNT (CONT-104), drawn with block models: no tanks, one or two, with the timer running or the valve open. |

**Wearables.** Each piece needs a flat sprite and a worn texture. The
**Worn texture** column gives the vanilla armor layer it's drawn on. Pieces of
one suit share their layer files.

| Proposed id | Name | Priority | Requirement | Worn texture | Notes |
|---|---|---|---|---|---|
| `zas_atmos:breath_mask` | Breath mask | Must | CONT-031 | Layer 1, head only | |
| `zas_atmos:gas_mask` | Gas mask | Must | CONT-088 | Layer 1, head only | |
| `zas_atmos:softsuit_helmet` | Softsuit helmet | Must | CONT-031, CONT-032 | Softsuit layer 1 | |
| `zas_atmos:softsuit_chestplate` | Softsuit chestplate | Must | CONT-032, CONT-090 | Softsuit layer 1 | Holds a tank (CONT-090); showing it on the back is optional. vg's 13 is on the back (the owner's choice of 2026-11-02). |
| `zas_atmos:softsuit_leggings` | Softsuit leggings | Must | CONT-032 | Softsuit layer 2 | |
| `zas_atmos:softsuit_boots` | Softsuit boots | Must | CONT-032 | Softsuit layer 1 | |
| `zas_atmos:hardsuit_helmet` | Hardsuit helmet | Must | CONT-031, CONT-036 | Hardsuit layer 1 | The hardsuit should look heavier than the softsuit (CONT-089). Its crest and headlamp stand out on the outer layer (the owner's choice of 2026-11-02). |
| `zas_atmos:hardsuit_chestplate` | Hardsuit chestplate | Must | CONT-036, CONT-090 | Hardsuit layer 1 | Holds a tank (CONT-090). |
| `zas_atmos:hardsuit_leggings` | Hardsuit leggings | Must | CONT-036 | Hardsuit layer 2 | |
| `zas_atmos:hardsuit_boots` | Hardsuit boots | Must | CONT-036 | Hardsuit layer 1 | |
| `zas_atmos:magnetic_boots` | Magnetic boots | Should | CONT-033 | Layer 1, feet only | On and off, both as a sprite and worn, shown by vg's ankle light, red off and green on (the owner's choice of 2026-11-02). |

**Tools**

| Proposed id | Name | Priority | Requirement | States and notes |
|---|---|---|---|---|
| `zas_atmos:atmos_analyzer` | Atmos analyzer | Must | CONT-034 | Flat sprite. Was the gas analyzer. |
| `zas_atmos:wrench` | Wrench | Should | CONT-035 | Flat sprite, vg's own at 32×32 (D-70). Quickly takes pipes apart, and connects portables to connector ports and disconnects them. CONT-035 and CONT-083 need updating to match. |
| `zas_atmos:crowbar` | Crowbar | Must | CONT-094 | Flat sprite. Forces firelocks open, even in lockdown. A crowbar, not a halligan bar (the owner's choice of 2026-11-01). |
| `zas_atmos:multitool` | Multitool | Must | CONT-042 | Flat sprite. Links parts to an area air controller (D-68). |
| `zas_atmos:fire_extinguisher` | Fire extinguisher | Could | CONT-037 | Flat sprite, vg's own at 32×32, centred (D-74). Puts out hotspots and fire blocks. Moved from R2 by the owner, 2026-11-03. |
| `zas_atmos:bike_horn` | Bike horn | Could | CONT-154 | Flat sprite, vg's own cropped to 16×16. An easter egg found in dungeon and stronghold chests (D-87). Added 2026-11-09. |

**Batteries.** Portables run on these (D-69), and so can other mods' batteries
in the `zas_atmos:batteries` tag (CONT-112). The three tiers follow vg's power
cell, high-capacity and super-capacity cells.

| Proposed id | Name | Priority | Requirement | States and notes |
|---|---|---|---|---|
| `zas_atmos:battery` | Battery | Should | CONT-111 | Flat sprite. Grey (the owner's choice of 2026-11-02). It shows its charge, as vg's cells do: part charged and full. |
| `zas_atmos:high_capacity_battery` | High-capacity battery | Should | CONT-111 | Flat sprite. The three tiers must be easy to tell apart. Red, with one stripe. |
| `zas_atmos:super_capacity_battery` | Super-capacity battery | Should | CONT-111 | Flat sprite. Blue, with two stripes. |

**Crafting components.** Recipes go through these (D-72), and each is made
from vanilla materials, so CONT-051 holds. All are flat sprites taken from
vg: cropped to 16×16 where the sprite fits, and otherwise vg's sprite as it
is at 32×32. Where they come from is in the
[texture procurement plan](texture-procurement.md#65-crafting-components),
and what each goes into is in [Crafting](crafting.md#crafting-components).
All are done (2026-11-02).

| Id | Name | Priority | Requirement | Size and notes |
|---|---|---|---|---|
| `zas_atmos:cable_coil` | Cable coil | Must | CONT-051, D-72 | 32×32. vg's coil in vg's default red. An item only: there's no cable block (D-68). |
| `zas_atmos:circuit_board` | Circuit board | Must | CONT-051, D-72 | 32×32. One board for every machine. |
| `zas_atmos:electronics` | Electronics | Must | CONT-051, D-72 | 16×16. For wall panels and doors. |
| `zas_atmos:machine_frame` | Machine frame | Must | CONT-051, D-72 | 32×32. An item, not a block. |
| `zas_atmos:capacitor` | Capacitor | Must | CONT-051, D-72 | 32×32. |
| `zas_atmos:advanced_capacitor` | Advanced capacitor | Should | CONT-111, D-72 | 32×32. |
| `zas_atmos:super_capacitor` | Super capacitor | Should | CONT-111, D-72 | 32×32. |
| `zas_atmos:scanning_module` | Scanning module | Must | CONT-051, D-72 | 16×16, animated in vg's 5 frames. |
| `zas_atmos:micro_manipulator` | Micro-manipulator | Must | CONT-051, D-72 | 16×16. |
| `zas_atmos:micro_laser` | Micro-laser | Should | CONT-019, D-72 | 16×16. |
| `zas_atmos:console_screen` | Console screen | Must | CONT-051, D-72 | 32×32. |
| `zas_atmos:igniter_assembly` | Igniter assembly | Must | CONT-105, D-72 | 16×16. |
| `zas_atmos:timer_assembly` | Timer assembly | Must | CONT-104, D-72 | 16×16. |
| `zas_atmos:ansible_crystal` | Ansible crystal | Must | CONT-042, D-72 | 16×16. |
| `zas_atmos:subspace_ansible` | Subspace ansible | Must | CONT-042, D-72 | 32×32. |
| `zas_atmos:subspace_transmitter` | Subspace transmitter | Must | CONT-068, D-72 | 32×32. |
| `zas_atmos:bluespace_crystal` | Bluespace crystal | Should | CONT-111, D-72 | 16×16. |
| `zas_atmos:plasteel_sheet` | Plasteel sheet | Must | CONT-036, D-72 | 32×32. |
| `zas_atmos:reinforced_glass` | Reinforced glass sheet | Must | CONT-055, D-72 | 32×32. |

### Totals for R1

| | Must | Should | Could | Not set | Total |
|---|---|---|---|---|---|
| Blocks (`zas`) | 1 | – | – | – | 1 |
| Blocks (`zas_atmos`) | 13 | 13 | 1 | 8 | 35 |
| Creative canisters (`zas_atmos`) | 8 | – | – | – | 8 |
| Items (`zas_atmos`) | 18 | 5 | 2 | – | 25 |
| Crafting components (`zas_atmos`) | 15 | 4 | – | – | 19 |

*2026-11-02: R1 now has crafting components of its own (D-72). Its recipes
still come down to vanilla materials (CONT-051).*

*2026-11-07: the station walls and reinforced glass are added (D-80, D-81).
The Should count for blocks was one short before them and is corrected.*

*2026-11-09: the bike horn is added (D-87).*

## Art that isn't a block or item

These need textures too, but aren't in the tables above:
- any icon a device GUI can't draw in code or with a DejaVu Sans glyph (D-76). The
  windows built so far need none: slot frames, gauges, arrows and locks are drawn in code.
- the HUD: readings and the internals indicator (CLIENT-001, CLIENT-008,
  CLIENT-009). Each warning needs a shape or flashing cue as well as a colour
  (CLIENT-014). The alerts are done: they're vg's screen-alert icons from
  `icons/mob/screen_alarms.dmi` (`carbon_oxy`, `carbon_tox`,
  `carbon_pressure±1/±2`, `carbon_temp±2/±4`), Crops at 32×32 in
  `zas/textures/gui/hud/`, with animated states' frames stacked downward. vg
  took its screen-alert framework from /tg/station (CC BY-SA 3.0). The
  internals indicator is vg's internals button from `icons/mob/screen1_old.dmi`
  (`internal0`, `internal1_old`, `internal2_old`), as `internals_off`,
  `internals_on` and `internals_failing`, which flashes between `internal2_old`
  and `internal0`.
- airflow particles (CLIENT-003)

## Later releases (R2)

Not needed for R1, and no ids are proposed yet. Most come from CONT's R2
column, which gives them no requirement of their own; the rest are deferred
Mekanism requirements. The radiator pipes, heat exchanger and air sensor have
moved to R1 (above).

| Name | Kind | Requirement | Notes |
|---|---|---|---|
| Thermoelectric cooler | Block | CONT R2 column | |
| Thermoelectric generator | Block | CONT R2 column | |
| Circulator | Block | CONT R2 column | vg's generator takes two, one per stream. |
| Gas turbine | Block | CONT R2 column | vg's is a compressor and a turbine, so it may be two blocks. |
| Cryo cell | Block | CONT R2 column | |
| Dual-port vent | Block | CONT R2 column | |
| Injector | Block | CONT-082 | |
| Meter | Block | CONT R2 column | |
| Pipe layer manifold | Block | CONT R2 column | vg joins pipe layers with manifolds and adapters ([vg §1](research/vg-atmos-content.md#1-pipe-networks)). R2 may need one or both. Pipe layers were to reuse the multipart block, so they need another design (D-68). |
| Pipe layer adapter | Block | CONT R2 column | See the manifold. |
| Gas miner | Block | CONT R2 column | Creative only. |
| TTV signaler | Item | CONT-104 | Attaches to the tank transfer valve. |
| TTV proximity trigger | Item | CONT-104 | Attaches to the tank transfer valve. |
| Mekanism bridge port | Block | MEK-012 | |
| Mekanism exhaust port | Block | MEK-022 | |

The thermostat (CONT-045) is a feature of the area air controller, not a
block. The Ad Astra adapter adds no blocks or items.
