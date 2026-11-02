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
pixels per block (the wrench's 32×32 sprite is the one exception, D-70), flat
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
| `zas:gas_fire` | Gas fire | Must | FIRE-018 | None | Animated, like vanilla fire. It burns in mid air, so it must look right with nothing under it. |

### Content mod (`zas_atmos`): blocks

**Pipes and valves**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:pipe` | Pipe | Must | CONT-001, CONT-007 | Block model | A core plus an arm for each connected side. An unconnected end leaks (CONT-004) unless it's capped. Players dye pipes (the owner's choice, 2026-11-01; no requirement yet). |
| `zas_atmos:pipe_cap` | Pipe end cap | Not set | None yet | Block model | Closes a pipe end so it doesn't leak (CONT-004). Dyed like the pipe. |
| `zas_atmos:radiator_pipe` | Radiator pipe | Not set | None yet | Flat sprite | Exchanges heat with its surroundings, like vg's heat-exchange pipe. It joins ordinary pipe only through the adaptor, so it must look clearly different from it. |
| `zas_atmos:radiator_adaptor` | Radiator adaptor pipe | Not set | None yet | Block model | Joins an ordinary pipe to a radiator pipe. One end of each kind. |
| `zas_atmos:manual_valve` | Manual valve | Must | CONT-010 | Block model | Open and closed. |
| `zas_atmos:redstone_valve` | Redstone valve | Must | CONT-010 | Block model | Open and closed. It should look different from the manual valve. |

**Devices**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:pressure_pump` | Pressure pump | Must | CONT-011 | Block model | On and off. The input and output faces must be easy to tell apart. |
| `zas_atmos:volume_pump` | Volume pump | Should | CONT-012 | Block model | On and off. As the pressure pump, but distinct from it. |
| `zas_atmos:filter` | Filter | Should | CONT-013 | Block model | On and off. Three ports: input, output and side outlet. |
| `zas_atmos:mixer` | Mixer | Should | CONT-077 | Block model | On and off. Three ports: two inputs and an output. |
| `zas_atmos:vent` | Vent | Must | CONT-014 | Block model | Off, blowing and siphoning. Animated (CONT-070). Faces any of six ways; a pipe joins only its back. |
| `zas_atmos:scrubber` | Scrubber | Must | CONT-015, CONT-079 | Block model | Off, scrubbing, siphon and panic. Animated (CONT-070). Faces any of six ways, like the vent; a pipe joins only its back. |
| `zas_atmos:passive_vent` | Passive vent | Could | CONT-016 | Block model | Unpowered; one state. |
| `zas_atmos:connector_port` | Connector port | Must | CONT-083 | Block model | Free, and with a portable connected. Set in the floor, facing up; a pipe joins only its bottom. |
| `zas_atmos:igniter` | Igniter | Must | CONT-105 | Block model | Idle and active. |
| `zas_atmos:heat_exchanger` | Heat exchanger | Not set | None yet | Block model | vg's works in linked pairs that share one temperature ([vg §2](research/vg-atmos-content.md#2-devices)). |
| `zas_atmos:airlock_controller` | Airlock controller | Not set | None yet | Flat sprite | Wall-mounted. Cycles an airlock with its vents and an air sensor. Idle and cycling. |
| `zas_atmos:air_sensor` | Air sensor | Not set | None yet | Block model | Reads the gas where it's placed, for an airlock controller. |

The heater and freezer are now portables (below).

**Portable atmospherics**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:canister` | Canister | Must | CONT-017 | Block model | Valve open and closed. Coloured as vg's canisters, and players dye them, so it needs a texture for each colour ([below](#content-mod-zas_atmos-creative-canisters)). Survival canisters are crafted empty (CONT-086); the filled ones are below. |
| `zas_atmos:portable_pump` | Portable pump | Should | CONT-018 | Block model | On and off. |
| `zas_atmos:portable_scrubber` | Portable scrubber | Should | CONT-018 | Block model | On and off. |
| `zas_atmos:heater` | Heater | Should | CONT-019 | Block model | On and off. Now a portable that heats the room, not piped gas, so CONT-019 needs rewording. |
| `zas_atmos:air_conditioner` | Air conditioner | Should | CONT-019 | Block model | On and off. Replaces the freezer: a portable that cools the room. It should look different from the heater. |
| `zas_atmos:tank_holder` | Tank holder | Not set | None yet | Block model | Holds up to 10 personal tanks and keeps their contents. It could show how many it holds. |

Portables connect to and disconnect from connector ports with the wrench.

**Gas sources and power**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:electrolyzer` | Water electrolyzer | Must | CONT-055, CONT-056 | Block model | On and off. It could also show its water level. |
| `zas_atmos:generator` | Generator | Must | CONT-061, CONT-067 | Block model | Lit and unlit. In the style of SS13's P.A.C.M.A.N. |
| `zas_atmos:recharger` | Recharger | Should | CONT-113 | Block model | Empty, charging and full. Charges one battery (below) for the portables. It could show the charge in steps, as vg's cell charger does. |

The air compressor is gone, and so are the power cable and the multipart block
(D-68). Without the compressor, R1 gets gas from the electrolyzer and from
scrubbing and filtering, so CONT-050 and CONT-054 need updating.

**Area air controllers, airlocks and firelocks**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:area_air_controller` | Area air controller | Must | CONT-040, CONT-048, CONT-068 | Flat sprite | Wall-mounted. Normal, danger 1, danger 2 and unpowered. Was the air alarm. It also powers every atmospherics part in its zone or linked to it (D-68). |
| `zas_atmos:fire_alarm` | Fire alarm | Should | CONT-044 | Flat sprite | Wall-mounted. Normal and alarm. |
| `zas_atmos:airlock` | Airlock | Not set | None yet | Flat sprite | A zone-boundary door, worked by an airlock controller. Open and closed. Two sizes: 1 wide and 2 tall like a standard door, and 2 by 2. |
| `zas_atmos:firelock` | Firelock | Must | CONT-043, CONT-093 | Flat sprite | Open, closed and locked down, which shows a warning. Modelled on vg's old full-tile firelock. It can be placed on a door, a vanilla door or an airlock, and is then drawn as an overlay on it, so it comes in the doors' sizes: 1 by 2 and 2 by 2. On its own it's 1 by 2, a frame the block's full depth with its panel in the middle (the owner's choice of 2026-11-01). |

Both door sizes and firelocks on doors are the owner's choices of 2026-11-01.
The requirements don't cover them yet.

The wall-mounted blocks and the doors get flat sprites, as vanilla's torches
and doors do. Any of them could use its block model instead.

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
| `zas_atmos:tank_transfer_valve` | Tank transfer valve | Must | CONT-103, CONT-104 | Flat sprite, with variants for no tanks, one tank and two tanks, and for the timer running. |

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
| `zas_atmos:hardsuit_helmet` | Hardsuit helmet | Must | CONT-031, CONT-036 | Hardsuit layer 1 | The hardsuit should look heavier than the softsuit (CONT-089). |
| `zas_atmos:hardsuit_chestplate` | Hardsuit chestplate | Must | CONT-036, CONT-090 | Hardsuit layer 1 | Holds a tank (CONT-090). |
| `zas_atmos:hardsuit_leggings` | Hardsuit leggings | Must | CONT-036 | Hardsuit layer 2 | |
| `zas_atmos:hardsuit_boots` | Hardsuit boots | Must | CONT-036 | Hardsuit layer 1 | |
| `zas_atmos:magnetic_boots` | Magnetic boots | Should | CONT-033 | Layer 1, feet only | On and off, both as a sprite and worn. |

**Tools**

| Proposed id | Name | Priority | Requirement | States and notes |
|---|---|---|---|---|
| `zas_atmos:atmos_analyzer` | Atmos analyzer | Must | CONT-034 | Flat sprite. Was the gas analyzer. |
| `zas_atmos:wrench` | Wrench | Should | CONT-035 | Flat sprite, vg's own at 32×32 (D-70). Quickly takes pipes apart, and connects portables to connector ports and disconnects them. CONT-035 and CONT-083 need updating to match. |
| `zas_atmos:crowbar` | Crowbar | Must | CONT-094 | Flat sprite. Forces firelocks open, even in lockdown. A crowbar, not a halligan bar (the owner's choice of 2026-11-01). |
| `zas_atmos:multitool` | Multitool | Must | CONT-042 | Flat sprite. Links parts to an area air controller (D-68). |

**Batteries.** Portables run on these (D-69), and so can other mods' batteries
in the `zas_atmos:batteries` tag (CONT-112). The three tiers follow vg's power
cell, high-capacity and super-capacity cells.

| Proposed id | Name | Priority | Requirement | States and notes |
|---|---|---|---|---|
| `zas_atmos:battery` | Battery | Should | CONT-111 | Flat sprite. It could show its charge, as vg's cells do. |
| `zas_atmos:high_capacity_battery` | High-capacity battery | Should | CONT-111 | Flat sprite. The three tiers must be easy to tell apart. |
| `zas_atmos:super_capacity_battery` | Super-capacity battery | Should | CONT-111 | Flat sprite. |

### Totals for R1

| | Must | Should | Could | Not set | Total |
|---|---|---|---|---|---|
| Blocks (`zas`) | 1 | – | – | – | 1 |
| Blocks (`zas_atmos`) | 13 | 9 | 1 | 8 | 31 |
| Creative canisters (`zas_atmos`) | 8 | – | – | – | 8 |
| Items (`zas_atmos`) | 18 | 5 | – | – | 23 |

R1 needs no crafting ingredients of its own: all its recipes use vanilla
materials (CONT-051).

## Art that isn't a block or item

These need textures too, but aren't in the tables above:
- device GUIs (CONT-074), the area air controller's GUI and device list
  (CONT-049), and the TTV's timer
- the HUD: readings and the internals indicator (CLIENT-001, CLIENT-008,
  CLIENT-009). Each warning needs a shape or flashing cue as well as a colour
  (CLIENT-014).
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
| Fire extinguisher | Item | CONT-037 | |
| TTV signaler | Item | CONT-104 | Attaches to the tank transfer valve. |
| TTV proximity trigger | Item | CONT-104 | Attaches to the tank transfer valve. |
| Mekanism bridge port | Block | MEK-012 | |
| Mekanism exhaust port | Block | MEK-022 | |

The thermostat (CONT-045) is a feature of the area air controller, not a
block. The Ad Astra adapter adds no blocks or items.
