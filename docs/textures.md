# Blocks and items that need textures

**Status:** list, 2026-11-01. No block or item is registered yet, so the ids
below are proposals.

This lists every block and item ZAS³ will add that needs a texture, taken from
the requirements, with one row for each. Art follows D-67: vanilla JSON models
on the 1/16-block grid, textures at 16 pixels per block, flat sprites for
handheld items, and the standard armor texture layout for wearables. Sources
and licensing follow D-26 and D-39, with each asset's provenance in `CREDITS`
(NFR-045).

In the block tables, the **Item** column says what the block's item looks
like: the block's own model (no extra texture), a flat sprite (one more
texture), or none (the block has no item).

## R1

### Framework mod (`zas`)

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas:gas_fire` | Gas fire | Must | FIRE-018 | None | Animated, like vanilla fire. It burns in mid air, so it must look right with nothing under it. |

### Content mod (`zas_atmos`): blocks

**Pipes, cables and valves**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:pipe` | Pipe | Must | CONT-001, CONT-007 | Flat sprite | A part of the multipart block: a core plus an arm for each connected side. An open end leaks (CONT-004), so it could look open. |
| `zas_atmos:cable` | Power cable | Should | CONT-063, CONT-098 | Flat sprite | A part of the multipart block. It must read clearly next to a pipe in the same block. |
| `zas_atmos:multipart` | Multipart block | Should | CONT-097 | None | It draws only its parts, but needs a particle texture for breaking, which can be the pipe's. |
| `zas_atmos:manual_valve` | Manual valve | Must | CONT-010 | Block model | Open and closed. |
| `zas_atmos:redstone_valve` | Redstone valve | Must | CONT-010 | Block model | Open and closed. It should look different from the manual valve. |

**Devices**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:pressure_pump` | Pressure pump | Must | CONT-011 | Block model | On and off. The input and output faces must be easy to tell apart. |
| `zas_atmos:volume_pump` | Volume pump | Should | CONT-012 | Block model | On and off. As the pressure pump, but distinct from it. |
| `zas_atmos:filter` | Filter | Should | CONT-013 | Block model | On and off. Three ports: input, output and side outlet. |
| `zas_atmos:mixer` | Mixer | Should | CONT-077 | Block model | On and off. Three ports: two inputs and an output. |
| `zas_atmos:vent` | Vent | Must | CONT-014 | Block model | Off, blowing and siphoning. Animated (CONT-070). |
| `zas_atmos:scrubber` | Scrubber | Must | CONT-015, CONT-079 | Block model | Off, scrubbing, siphon and panic. Animated (CONT-070). |
| `zas_atmos:passive_vent` | Passive vent | Could | CONT-016 | Block model | Unpowered; one state. |
| `zas_atmos:connector_port` | Connector port | Must | CONT-083 | Block model | Free, and with a canister attached. |
| `zas_atmos:heater` | Heater | Should | CONT-019 | Block model | On and off. |
| `zas_atmos:freezer` | Freezer | Should | CONT-019 | Block model | On and off. It should look different from the heater. |
| `zas_atmos:igniter` | Igniter | Must | CONT-105 | Block model | Idle and active. |

**Portable atmospherics**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:canister` | Canister | Must | CONT-017 | Block model | Valve open and closed. Survival canisters are crafted empty (CONT-086); the filled ones are below. |
| `zas_atmos:portable_pump` | Portable pump | Should | CONT-018 | Block model | On and off. |
| `zas_atmos:portable_scrubber` | Portable scrubber | Should | CONT-018 | Block model | On and off. |

**Gas sources and power**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:air_compressor` | Air compressor | Must | CONT-054 | Block model | On and off. |
| `zas_atmos:electrolyzer` | Water electrolyzer | Must | CONT-055, CONT-056 | Block model | On and off. It could also show its water level. |
| `zas_atmos:generator` | Generator | Must | CONT-061, CONT-067 | Block model | Lit and unlit. In the style of SS13's P.A.C.M.A.N. |

**Alarms and firelocks**

| Proposed id | Name | Priority | Requirement | Item | States and notes |
|---|---|---|---|---|---|
| `zas_atmos:air_alarm` | Air alarm | Must | CONT-040, CONT-048 | Flat sprite | Wall-mounted. Normal, danger 1, danger 2 and unpowered. |
| `zas_atmos:fire_alarm` | Fire alarm | Should | CONT-044 | Flat sprite | Wall-mounted. Normal and alarm. |
| `zas_atmos:firelock` | Firelock | Must | CONT-043, CONT-093 | Flat sprite | Open, closed and locked down, which shows a warning. Its size isn't decided: one block, or two tall like a door. |

The wall-mounted alarms and the firelock get flat sprites, as vanilla's
torches and doors do. Either could use its block model instead.

### Content mod (`zas_atmos`): creative canisters

Canisters pre-filled with each built-in gas (the 8 of D-42) exist only in
creative (CONT-086). Each is a canister holding one gas, not a new block. vg
colours canisters by gas. Each canister below needs its own texture if each
gas gets its own colour. One texture with a tinted band would cover them all,
and datapack gases too.

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
| `zas_atmos:air_tank` | Air tank | Must | CONT-030 | Flat sprite. |
| `zas_atmos:empty_tank` | Empty tank | Must | CONT-030 | Flat sprite. |
| `zas_atmos:tank_transfer_valve` | Tank transfer valve | Must | CONT-103, CONT-104 | Flat sprite, with variants for no tanks, one tank and two tanks, and for the timer running. |

**Wearables.** Each piece needs a flat sprite and a worn texture. The
**Worn texture** column gives the vanilla armor layer it's drawn on. Pieces of
one suit share their layer files.

| Proposed id | Name | Priority | Requirement | Worn texture | Notes |
|---|---|---|---|---|---|
| `zas_atmos:breath_mask` | Breath mask | Must | CONT-031 | Layer 1, head only | |
| `zas_atmos:gas_mask` | Gas mask | Must | CONT-088 | Layer 1, head only | |
| `zas_atmos:softsuit_helmet` | Softsuit helmet | Must | CONT-031, CONT-032 | Softsuit layer 1 | |
| `zas_atmos:softsuit_chestplate` | Softsuit chestplate | Must | CONT-032, CONT-090 | Softsuit layer 1 | Holds a tank (CONT-090); showing it on the back is optional. |
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
| `zas_atmos:gas_analyzer` | Gas analyzer | Must | CONT-034 | Flat sprite. |
| `zas_atmos:wrench` | Wrench | Should | CONT-035 | Flat sprite. |
| *not decided* | Tool that forces firelocks | Must | CONT-094 | The requirement says only "a tool". SS13 uses a crowbar. It could also be the wrench, which would need no new art. |
| *not decided* | Tool that links devices to an alarm | Must | CONT-042 | The requirement says only "a tool". vg edits devices with a multitool ([vg §2.2](research/vg-atmos-content.md#22-vents-and-scrubbers)). It could also be the wrench or the analyzer. |

### Totals for R1

| | Must | Should | Could | Total |
|---|---|---|---|---|
| Blocks (`zas`) | 1 | – | – | 1 |
| Blocks (`zas_atmos`) | 14 | 10 | 1 | 25 |
| Creative canisters (`zas_atmos`) | 8 | – | – | 8 |
| Items (`zas_atmos`) | 16 to 18 | 2 | – | 18 to 20 |

The item range depends on whether the two undecided tools are new items.

R1 needs no crafting ingredients of its own: all its recipes use vanilla
materials (CONT-051).

## Art that isn't a block or item

These need textures too, but aren't in the tables above:
- device GUIs (CONT-074), the air alarm's GUI and device list (CONT-049), and
  the TTV's timer
- the HUD: readings and the internals indicator (CLIENT-001, CLIENT-008,
  CLIENT-009). Each warning needs a shape or flashing cue as well as a colour
  (CLIENT-014).
- airflow particles (CLIENT-003)

## Later releases (R2)

Not needed for R1, and no ids are proposed yet. Most come from CONT's R2
column, which gives them no requirement of their own; the rest are deferred
Mekanism requirements.

| Name | Kind | Requirement | Notes |
|---|---|---|---|
| Heat-exchange pipe | Block | CONT R2 column | Likely a multipart part, like the pipe. |
| Heat-exchange junction | Block | CONT R2 column | vg's heat-exchange pipes meet ordinary pipes only at junctions ([vg §1](research/vg-atmos-content.md#1-pipe-networks)). Needed only if R2 does the same. |
| Radiator | Block | CONT R2 column | |
| Thermoelectric cooler | Block | CONT R2 column | |
| Thermoelectric generator | Block | CONT R2 column | |
| Circulator | Block | CONT R2 column | vg's generator takes two, one per stream. |
| Gas turbine | Block | CONT R2 column | vg's is a compressor and a turbine, so it may be two blocks. |
| Cryo cell | Block | CONT R2 column | |
| Dual-port vent | Block | CONT R2 column | |
| Injector | Block | CONT-082 | |
| Meter | Block | CONT R2 column | |
| Air sensor | Block | CONT R2 column | |
| Pipe layer manifold | Block | CONT R2 column, CONT-097 | vg joins pipe layers with manifolds and adapters ([vg §1](research/vg-atmos-content.md#1-pipe-networks)). R2 may need one or both. |
| Pipe layer adapter | Block | CONT R2 column, CONT-097 | See the manifold. |
| Gas miner | Block | CONT R2 column | Creative only. |
| Fire extinguisher | Item | CONT-037 | |
| TTV signaler | Item | CONT-104 | Attaches to the tank transfer valve. |
| TTV proximity trigger | Item | CONT-104 | Attaches to the tank transfer valve. |
| Mekanism bridge port | Block | MEK-012 | |
| Mekanism exhaust port | Block | MEK-022 | |

The thermostat (CONT-045) is a feature of the air alarm, not a block. The Ad
Astra adapter adds no blocks or items.
