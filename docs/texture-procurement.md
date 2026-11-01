# Texture procurement plan

**Status:** proposed, 2026-11-01.

This plan says where each texture in [textures.md](textures.md) comes from,
how it's made and in what order. It covers R1's blocks and items. GUIs, the
HUD, particles and R2 come later ([§8](#8-not-covered)).

## 1. Summary

- **vg covers nearly everything.** vgstation13 has a sprite to start from for
  every R1 row except the gas fire and the electrolyzer. Every vg sprite and
  state named here exists at the pinned commit ([§3](#3-sources)).
- **Block faces are all redrawn.** vg draws machines in a three-quarter view
  at 32 px, so each face is redrawn at 16 px per block (D-67).
- **11 of the 23 items' sprites can be cropped.** vg draws many items at
  16 px or less inside a 32 px frame. Those are cut out and touched up. Larger
  ones are redrawn at half size.
- **Worn textures are redrawn** onto Minecraft's armor layout from vg's
  four-direction worn sprites.
- **Canisters come in vg's colours,** and players dye them. All 16 dyes give
  a colour, so with vg's red-and-white there are 17
  ([§6.4](#64-canister-colours)).
- **AI tools make the textures** ([§7](#7-order-of-work)), and a person
  checks each one in game before it merges.
- **A set-up batch, then five batches** that follow the build order, starting
  with the S-01 slice ([§7](#7-order-of-work)).

| Route | Blocks | Canister colours | Item sprites | Worn textures |
|---|---|---|---|---|
| Crop | – | – | 12 | – |
| Redraw | 30 | 17 | 18 | 8 files |
| Original | 2 | – | – | – |

The item sprites include the 7 blocks whose item is a flat sprite. The
canister counts once under Blocks, and its 17 colours separately.

## 2. Routes

| Route | When | `CREDITS` origin |
|---|---|---|
| **Crop** | A vg item sprite fits in 16×16 px, or is within 2 px of it. It's cut out of its 32 px frame, trimmed to fit, and touched up. | `vgstation13` |
| **Redraw** | Any other texture with a vg sprite: every block face, and item sprites larger than 16 px. It's drawn at 16 px per block from the sprite's shapes, markings and colours. | `vgstation13` |
| **Original** | No usable vg sprite. | `original` |
| **Other** | A fallback only, for a texture that's hard to draw from scratch (D-62). | `other` |

- **Credit the reference.** A texture drawn with a vg sprite open as a
  reference is credited to vgstation13, naming the file and states it
  followed. `original` is only for textures drawn without one.
- **New states of a vg design** are credited to vg too: a state vg doesn't
  have, such as the scrubber's panic mode, still follows its vg sprite.
- **Models and animation files are assets too.** Each JSON model and each
  `.png.mcmeta` file needs its own `CREDITS` entry (NFR-045). Models are
  built from boxes in Blockbench, so they are `original`.

## 3. Sources

**vgstation13.** Every vg-derived texture comes from one commit, upstream
`Bleeding-Edge` at
[`23f25f3`](https://github.com/vgstation-coders/vgstation13/commit/23f25f386f7a6c75b1a604029848d798e8b7ec06)
(2026-10-02), unless a sprite needs a later one. The `commit` field of each
`CREDITS` entry is that hash. vg licenses its icons CC BY-SA 3.0 US "unless
specified otherwise", so before using a file:

1. Check it isn't under `goon/` (NFR-041). No file named in this plan is.
2. Read the file's history in vg for the commit that added each state used.
   Leave out a state that was ported from Goonstation or came in under any
   other NonCommercial license (NFR-046).

**Other sources** are allowed only under CC0, CC BY or CC BY-SA (D-62).
Minecraft's own textures are never used, not even as a base. The only one this
plan suggests is Pixel Perfection by XSSheep, a 16 px resource pack, as a base
for the gas fire's animation. It is CC BY-SA, but sources disagree on whether
it's 3.0 or 4.0. Both are allowed; confirm the version from the author's own
statement before using it.

## 4. Tools

- **Blockbench** for models and textures (D-67).
- **A DMI extractor.** vg's `.dmi` files are PNG sprite sheets with the list
  of states in a text chunk. A small script in `scripts/` would cut a state's
  frames out as PNGs and print the file, state and commit for its `CREDITS`
  entry. It reads the PNG format, so it's our own code and holds no vg code.
- **One palette** for the whole set, taken from vg's atmospherics sprites:
  pipe greys, canister colours, warning yellow and red, screen colours. Every
  texture uses it, so textures by different people match.
- **Animation** uses vanilla `.png.mcmeta` files, with frames stacked
  vertically. vg's frame counts, given in [§6](#6-sources-for-each-row), are
  the starting point.

## 5. Style

- **16 px per block,** on the 1/16-block grid (D-67). No higher resolutions.
- **Light from the top left,** as in vanilla.
- **vg's colour coding stays:** grey pipe, near-black radiator pipe, vg's
  canister colours, and the controller's green, yellow and red lights.
- **One port design.** Every device port uses the same flange. Output ports
  carry an arrow, so a pump's input and output are easy to tell apart.
- **States** are separate textures, or overlays where only a light changes.

## 6. Sources for each row

Paths are in the vg repository. A state with frames is animated in vg.

### 6.1 Framework mod (`zas`)

| Proposed id | vg source | States | Route | Notes |
|---|---|---|---|---|
| `zas:gas_fire` | `icons/effects/fire.dmi` | `1`, `2`, `3` (6 to 14 frames) | Original | vg's fire lies flat on a floor tile, so it can't become Minecraft's crossed planes of flame. Drawn from scratch, or from a CC BY-SA base ([§3](#3-sources)). |

### 6.2 Content mod (`zas_atmos`): blocks

Every row is a Redraw unless its notes say otherwise. Each block's item
uses its block model, or for the flat-sprite rows, a sprite redrawn from its
face.

**Pipes and valves**

| Proposed id | vg source | States | Notes |
|---|---|---|---|
| `zas_atmos:pipe` | `icons/obj/pipes/regular.dmi` | `12`, `3` | A tube face, an end face and a flange, for a small model. |
| `zas_atmos:pipe_cap` | `icons/obj/pipes.dmi` | `cap` | |
| `zas_atmos:radiator_pipe` | `icons/obj/pipes/heat.dmi` | `intact` | vg's is near-black and ribbed, already unlike grey pipe. |
| `zas_atmos:radiator_adaptor` | `icons/obj/pipes/junction.dmi` | `intact` | One grey end and one black end. |
| `zas_atmos:manual_valve` | `icons/obj/atmospherics/valve.dmi` | `valve0`, `valve1` | |
| `zas_atmos:redstone_valve` | `icons/obj/atmospherics/digital_valve.dmi` | `valve0`, `valve1` | Add redstone detail to set it apart from the manual valve. |

**Devices**

| Proposed id | vg source | States | Notes |
|---|---|---|---|
| `zas_atmos:pressure_pump` | `icons/obj/atmospherics/pump.dmi` | `intact_off`, `intact_on` (5 frames) | vg's pump is a small part on a pipe, 7×16 px. The block is mostly new, with its colours and markings. |
| `zas_atmos:volume_pump` | `icons/obj/atmospherics/volume_pump.dmi` | `intact_off`, `intact_on` (11 frames) | |
| `zas_atmos:filter` | `icons/obj/atmospherics/filter.dmi` | `intact_off`, `intact_on` (5 frames) | |
| `zas_atmos:mixer` | `icons/obj/atmospherics/mixer.dmi` | `intact_off`, `intact_on` (5 frames) | |
| `zas_atmos:vent` | `icons/obj/atmospherics/vent_pump.dmi` | `base`, `out`, `in` (8 frames each) | Animated (CONT-070). vg's grille becomes the top face. |
| `zas_atmos:scrubber` | `icons/obj/atmospherics/vent_scrubber.dmi` | `off`, `on`, `in` (5 frames) | vg has no panic state. Panic is new: faster frames and a red light. |
| `zas_atmos:passive_vent` | `icons/obj/atmospherics/pipe_vent.dmi` | `base` | |
| `zas_atmos:connector_port` | `icons/obj/atmospherics/portables_connector.dmi` | `intact` | The connected state is new. |
| `zas_atmos:igniter` | `icons/obj/stationobjs.dmi` | `igniter0`, `igniter1` (3 frames) | |
| `zas_atmos:heat_exchanger` | `icons/obj/atmospherics/heat_exchanger.dmi` | `intact` | |
| `zas_atmos:airlock_controller` | `icons/obj/airlock_machines.dmi` | `airlock_control_standby`, `airlock_control_process` (2 frames) | The panel is 11×12 px, so its item sprite is a Crop. |
| `zas_atmos:air_sensor` | `icons/obj/airlock_machines.dmi` | `airlock_sensor_standby`, `airlock_sensor_alert` (3 frames) | A small wall sensor in vg, 10×6 px. |

**Portable atmospherics**

| Proposed id | vg source | States | Notes |
|---|---|---|---|
| `zas_atmos:canister` | `icons/obj/atmos.dmi` | `yellow`, `can-open`, `can-connector`, `can-o0` to `can-o3` | vg's empty canister is yellow, so that's an undyed canister. Its other colours are in [§6.4](#64-canister-colours). The `can-o` lights show pressure; they're optional. |
| `zas_atmos:portable_pump` | `icons/obj/atmos.dmi` | `psiphon:0`, `psiphon:1` (2 frames) | |
| `zas_atmos:portable_scrubber` | `icons/obj/atmos.dmi` | `pscrubber:0`, `pscrubber:1` (2 frames) | |
| `zas_atmos:heater` | `icons/obj/atmos.dmi` | `sheater0`, `sheater1` (7 frames) | vg's space heater. |
| `zas_atmos:air_conditioner` | `icons/obj/atmos.dmi` | `aircond0`, `aircond1` (9 frames) | vg's chiller. It's white where the heater is black, so the two already differ. |
| `zas_atmos:tank_holder` | `icons/obj/objects.dmi` | `dispenser`, `oxygen-1` to `oxygen-4` | vg's tank storage unit. Its overlays show how many tanks it holds. |

**Gas sources and power**

| Proposed id | vg source | States | Notes |
|---|---|---|---|
| `zas_atmos:electrolyzer` | None | | **Original.** vg's electrolyzer is a chemistry machine, not a water splitter. Its oxygen generator (`icons/obj/atmospherics/oxygen_generator.dmi`) is the nearest atmospherics device; if it's used as a reference, the texture becomes a Redraw. |
| `zas_atmos:generator` | `icons/obj/power.dmi` | `portgen0`, `portgen1` | vg's P.A.C.M.A.N. |
| `zas_atmos:recharger` | `icons/obj/power.dmi` | `ccharger0`, `ccharger1`, `ccharger-o0` to `ccharger-o4` | vg's cell charger. The overlays give the charge steps. |

**Area air controllers, airlocks and firelocks**

| Proposed id | vg source | States | Notes |
|---|---|---|---|
| `zas_atmos:area_air_controller` | `icons/obj/monitors.dmi` | `alarm0`, `alarm2`, `alarm1`, `alarmp` | vg's air alarm: normal, danger 1, danger 2 and unpowered, in that order. vg's `alarm2` is yellow and `alarm1` red. |
| `zas_atmos:fire_alarm` | `icons/obj/monitors.dmi` | `fire0e`, `fire1e`, `firep` | Normal, alarm and unpowered. |
| `zas_atmos:airlock` | `icons/obj/doors/Doorext.dmi`; for the 2×2 layout, `icons/obj/doors/Door2x1glassfull.dmi` | `door_closed`, `door_open` | vg's external airlock, in two sizes: 1 wide and 2 tall like a standard door, and 2 by 2. vg's doors are one tile, so both are redrawn taller. The 2×2 follows how vg's two-tile glass airlock splits into two leaves. |
| `zas_atmos:firelock` | `icons/obj/doors/Doorfire.dmi` | `door_open`, `door_closed`, `door_locked`, `palert` (2 frames), `alert_hot`, `alert_cold` | vg's old full-tile firelock, red with "FIRE" and hazard stripes, from vg's first commit in 2010. It was replaced in 2012 by today's yellow `DoorHazard.dmi`, ported from Baystation 12. `palert` flashes for lockdown. The temperature lights `alert_hot` and `alert_cold` are optional. See [Firelocks on doors](#firelocks-on-doors). |

#### Firelocks on doors

A firelock can be placed on a door, either a vanilla door or an airlock (the
owner's choice, 2026-11-01). The door is then drawn with the firelock as an
overlay, so the firelock's textures must work over any door:

- **Open,** it's vg's empty frame around the doorway, transparent in the
  middle so the door shows through.
- **Closed and locked down,** its panel covers the door.
- **It comes in the doors' sizes:** 1 by 2 for vanilla doors and the small
  airlock, and 2 by 2 for the large airlock. On its own, a firelock is 1 by 2
  (proposed).
- **Its model sits just outside the door's faces,** so the two don't flicker
  where they meet.

### 6.3 Content mod (`zas_atmos`): items

The vg size is the sprite's opaque area inside its 32 px frame.

| Proposed id | vg source | States | vg size | Route | Notes |
|---|---|---|---|---|---|
| `zas_atmos:oxygen_tank` | `icons/obj/tank.dmi` | `oxygen` | 22×21 | Redraw | |
| `zas_atmos:emergency_oxygen_tank` | `icons/obj/tank.dmi` | `emergency` | 8×18 | Crop | Already looks smaller than the others. |
| `zas_atmos:air_tank` | `icons/obj/tank.dmi` | `oxygen` | 22×21 | Redraw | vg reuses the O₂ tank's sprite. Recolour it so the two differ. |
| `zas_atmos:empty_tank` | `icons/obj/tank.dmi` | `oxygen` | 22×21 | Redraw | No vg sprite; a grey recolour of the O₂ tank. |
| `zas_atmos:tank_transfer_valve` | `icons/obj/assemblies.dmi` | `valve`, `valve_1`, `oxygen` | 8×10 | Crop | vg draws each attached tank as an overlay, such as `oxygen`. The timer state is new. |
| `zas_atmos:breath_mask` | `icons/obj/clothing/masks.dmi` | `breath` | 18×13 | Crop | |
| `zas_atmos:gas_mask` | `icons/obj/clothing/masks.dmi` | `gas_mask` | 16×14 | Crop | |
| `zas_atmos:softsuit_helmet` | `icons/obj/clothing/hats.dmi` | `space` | 16×16 | Crop | |
| `zas_atmos:softsuit_chestplate` | `icons/obj/clothing/suits.dmi` | `space` | 23×22 | Redraw | |
| `zas_atmos:softsuit_leggings` | `icons/obj/clothing/suits.dmi` | `space` | | Redraw | vg's suit is one piece. The legs come from its lower half. |
| `zas_atmos:softsuit_boots` | `icons/obj/clothing/suits.dmi` | `space` | | Redraw | From the suit's feet. |
| `zas_atmos:hardsuit_helmet` | `icons/obj/clothing/hats.dmi` | `rig0-engineering` | 15×16 | Crop | vg's base hardsuit, in brown and gold with a blue visor. vg's civilian and engineering hardsuits both use it. |
| `zas_atmos:hardsuit_chestplate` | `icons/obj/clothing/suits.dmi` | `rig-engineering` | 21×25 | Redraw | |
| `zas_atmos:hardsuit_leggings` | `icons/obj/clothing/suits.dmi` | `rig-engineering` | | Redraw | As the softsuit's. |
| `zas_atmos:hardsuit_boots` | `icons/obj/clothing/suits.dmi` | `rig-engineering` | | Redraw | As the softsuit's. |
| `zas_atmos:magnetic_boots` | `icons/obj/clothing/shoes.dmi` | `magboots0`, `magboots1` | 18×12 | Crop | Off and on. |
| `zas_atmos:atmos_analyzer` | `icons/obj/device.dmi` | `atmos` | 14×22 | Redraw | |
| `zas_atmos:wrench` | `icons/obj/items.dmi` | `wrench` | 23×22 | Redraw | |
| `zas_atmos:crowbar` | `icons/obj/items.dmi` | `crowbar` | 27×21 | Redraw | |
| `zas_atmos:multitool` | `icons/obj/device.dmi` | `multitool` | 9×17 | Crop | |
| `zas_atmos:battery` | `icons/obj/power.dmi` | `cell`, `cell-o1`, `cell-o2` | 11×8 | Crop | The overlays show the charge. |
| `zas_atmos:high_capacity_battery` | `icons/obj/power.dmi` | `hcell` | 11×8 | Crop | vg's high- and super-capacity cells look much alike. Recolour them so the tiers are easy to tell apart. |
| `zas_atmos:super_capacity_battery` | `icons/obj/power.dmi` | `scell` | 11×8 | Crop | |

**Worn textures** are redrawn onto Minecraft's armor layout from vg's worn
sprites. Those have four directions, which give the front, back and sides;
the top and bottom are new. The textures must match their item sprites.

| Worn texture | vg source | States |
|---|---|---|
| Breath mask, layer 1 | `icons/mob/mask.dmi` | `breath` |
| Gas mask, layer 1 | `icons/mob/mask.dmi` | `gas_mask` |
| Softsuit, layers 1 and 2 | `icons/mob/head.dmi`, `icons/mob/suit.dmi` | `space` |
| Hardsuit, layers 1 and 2 | `icons/mob/head.dmi`, `icons/mob/suit.dmi` | `rig0-engineering`, `rig-engineering` |
| Magnetic boots, layer 1, off and on | `icons/mob/feet.dmi` | `magboots0`, `magboots1` |

### 6.4 Canister colours

Canisters are coloured as vg's, and a player dyes one by using a dye on it
(the owner's choice, 2026-11-01; dyeing has no requirement yet). vg paints a
canister one of nine colours, all in `icons/obj/atmos.dmi`. Each colour is a
texture of its own, not a tint, because vg's red-and-white has a stripe.

| Dye | vg state | Creative canister |
|---|---|---|
| Yellow, or none | `yellow` | |
| Blue | `blue` | O₂ |
| Red | `red` | N₂ |
| Black | `black` | CO₂ |
| No dye | `redws`, red with a white stripe | N₂O |
| Light gray | `grey` | |
| Orange | `orange` | H₂ (proposed) |
| Green | `green` | |
| Cyan | `cyan` | |
| White | None | H₂O vapor (proposed) |
| Gray | None | |
| Brown | None | CH₄ (proposed) |
| Lime | None | |
| Light blue | None | |
| Purple | None | CO (proposed) |
| Magenta | None | |
| Pink | None | |

- **All 17 are Redraws.** vg's nine are redrawn from their own states. The
  eight dyes vg has no colour for are recolours of the same design, credited
  to the vg state they follow.
- **vg's gas symbols are left out.** vg marks some colours with a symbol for
  the gas it held, such as a "P" for plasma on orange and a radiation mark on
  green. A dyed canister can hold any gas, so a symbol would mislead. The
  yellow canister keeps its hazard stripes.
- **Red-and-white has no dye.** In R1 only the creative N₂O canister has it,
  and dyeing that canister changes it to the dye's colour.
- **The creative canisters** use vg's colour for the gases vg has: O₂, N₂, CO₂
  and N₂O. vg has no canister for the other four, so their colours are
  proposals. H₂ is orange because it takes the place of vg's plasma, the main
  fuel, whose canister is orange. Water vapor is white, like steam. CH₄ is
  brown and CO purple, chosen only to stand apart from the rest.

## 7. Order of work

Art follows the build order in the [plan](plan.md#phase-e-r1-feature-tracks-in-parallel-once-r0-is-done):
first the S-01 slice, then scenario by scenario. A block's art ships in the
PR that registers it, or right after, so it can be checked in game. Until
then a block may use a plain placeholder texture, credited `original`.

**AI tools make the textures,** crops and redraws alike (the owner's choice,
2026-11-01). They write each PNG at 16 px from the vg sprite, the palette
and the textures already approved, and Blockbench puts the textures on the
models. The pilot in batch 0 sets the style the rest follow. A person checks
every texture in game before it merges, as CONTRIBUTING asks of AI-made
assets.

| Batch | Scope | Rows |
|---|---|---|
| 0. Set-up | The DMI extractor, the palette, the history check of each vg file ([§3](#3-sources)), and a pilot to settle the style: the pipe, the canister and the wrench, one of each kind of texture, reviewed in game | – |
| 1. S-01 | Pipe, pipe cap, connector port, vent, the canister in yellow, blue and red (the O₂ and N₂ creative canisters), area air controller, generator, softsuit helmet, wrench, atmos analyzer | 12 |
| 2. S-02 and S-08 | Firelock at 1 by 2, on its own and over a vanilla door, crowbar, scrubber, the rest of the softsuit, the four tanks, breath mask, gas mask, and the worn textures of the masks and softsuit | 12 |
| 3. S-03 to S-05 and S-13 | Gas fire, igniter, heater, air conditioner, pressure pump, manual and redstone valves, tank transfer valve, the other 14 canister colours, which cover the six other creative canisters | 14 |
| 4. The rest of R1's Must and Should | Hardsuit, magnetic boots, multitool, fire alarm, filter, mixer, volume pump, electrolyzer, recharger, the three batteries, portable pump and scrubber | 17 |
| 5. Could, and rows with no priority yet | Radiator pipe and adaptor, heat exchanger, airlock in both sizes with the 2-by-2 firelock, airlock controller, air sensor, tank holder, passive vent | 8 |

The batches cover all 63 rows. The pilot's three textures count in batch 1.
The pipe cap has no priority yet, but it's in batch 1 because an open pipe end
leaks (CONT-004). Batches 4 and 5 can run alongside 2 and 3.

**Each art PR:**
- adds a `CREDITS` entry for every texture, model and `.mcmeta` file, and
  passes `python scripts/check_credits.py`
- shows each texture in game, in every state, with screenshots in "Human
  testing" (player-facing, per [CONTRIBUTING](../CONTRIBUTING.md#using-ai))

## 8. Not covered

- **GUIs, the HUD and particles** ([textures.md](textures.md#art-that-isnt-a-block-or-item))
  are mostly original, and come with the client work (CLIENT, CONT-074).
- **R2.** vg has sprites for many of R2's blocks, such as the injector,
  dual-port vent, meter and gas miner. They're mapped when R2 is planned.

## 9. Decisions

The owner settled these on 2026-11-01:

1. **AI tools make the textures** ([§7](#7-order-of-work)).
2. **Canisters are coloured as vg's, and players dye them**
   ([§6.4](#64-canister-colours)).
3. **The hardsuit is vg's,** from its base hardsuit sprite
   (`rig-engineering`).
4. **The firelock tool is a crowbar,** not a halligan bar.
5. **Airlocks come in two sizes:** 1 by 2 like a standard door, and 2 by 2.
6. **Firelocks follow vg's old full-tile firelock,** and can be placed on
   doors, where they're drawn as an overlay
   ([Firelocks on doors](#firelocks-on-doors)).

Still open:

- **The colours proposed in [§6.4](#64-canister-colours):** which dye gives
  which colour, and the creative canisters for H₂, water vapor, CO and CH₄.
- **A firelock on its own** is proposed at 1 by 2, like a door.
