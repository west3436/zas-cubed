# Texture procurement plan

**Status:** proposed, 2026-11-01.

This plan says where each texture in [textures.md](textures.md) comes from,
how it's made and in what order. It covers R1's blocks and items. GUIs, the
HUD, particles and R2 come later ([§8](#8-not-covered)).

## 1. Summary

- **vg covers nearly everything.** vgstation13 has a sprite to start from for
  every R1 row except the gas fire and the electrolyzer. Every vg sprite and
  state named here exists at the pinned commit ([§3](#3-sources)).
- **Block faces are redrawn.** vg draws machines in a three-quarter view
  at 32 px, so each face is redrawn at 16 px per block (D-67). The filter's
  and mixer's tops are the exception: vg's own boxes, cut out at 32×32
  (D-71).
- **10 of the 24 items' sprites can be cropped.** vg draws many items at
  16 px or less inside a 32 px frame. Those are cut out and touched up. Larger
  ones are redrawn to fill the 16 px slot, not at half size (the owner's pick
  for the atmos analyzer). The wrench and the fire extinguisher are the
  exceptions: they're vg's own sprites at 32×32 (D-70, D-74).
- **Worn textures are redrawn** onto Minecraft's armor layout from vg's
  four-direction worn sprites.
- **The 19 crafting components are vg's own sprites,** cropped to 16×16
  where they fit and otherwise as they are at 32×32 (D-72,
  [§6.5](#65-crafting-components)). The table below doesn't count them.
- **Canisters come in vg's colours,** and players dye them. All 16 dyes give
  a colour, so with vg's red-and-white there are 17
  ([§6.4](#64-canister-colours)).
- **AI tools make the textures,** the owner reviews each one before it
  merges, and a person checks it in game when its block or item is
  registered ([§7](#7-order-of-work)).
- **A set-up batch, then five batches** that follow the build order, starting
  with the S-01 slice ([§7](#7-order-of-work)).

| Route | Blocks | Canister colours | Item sprites | Worn textures |
|---|---|---|---|---|
| Crop | 2 | – | 12 | – |
| Redraw | 28 | 17 | 15 | 8 files |
| As is | – | – | 2 | – |
| Original | 2 | – | – | – |

The item sprites include the 5 blocks whose item is a flat sprite. The
canister counts once under Blocks, and its 17 colours separately.

## 2. Routes

| Route | When | `CREDITS` origin |
|---|---|---|
| **Crop** | A vg item sprite fits in 16×16 px, or is within 2 px of it. It's cut out of its 32 px frame, trimmed to fit, and touched up. Also the filter's and mixer's tops, vg's boxes cut out as they are (D-71). | `vgstation13` |
| **Redraw** | Any other texture with a vg sprite: every block face, and item sprites larger than 16 px. It's drawn at 16 px per block from the sprite's shapes, markings and colours. | `vgstation13` |
| **As is** | vg's sprite unchanged, at its own size. The wrench (D-70), the fire extinguisher (D-74) and the larger crafting components (D-72, [§6.5](#65-crafting-components)). | `vgstation13` |
| **Original** | No usable vg sprite. | `original` |
| **Other** | A fallback only, for a texture that's hard to draw from scratch (D-62). | `other` |

- **Credit the reference.** A texture drawn with a vg sprite open as a
  reference is credited to vgstation13, naming the file and states it
  followed. `original` is only for textures drawn without one.
- **New states of a vg design** are credited to vg too: a state vg doesn't
  have, such as the scrubber's panic mode, still follows its vg sprite.
- **Models and animation files are assets too.** Each JSON model and each
  `.png.mcmeta` file needs its own `CREDITS` entry (NFR-045). Models are
  built from boxes, in Blockbench or by hand, so they are `original`.

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
statement before using it. The gas fire was drawn from scratch in batch 3, so
it isn't used.

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

- **16 px per block,** on the 1/16-block grid (D-67). No higher resolutions,
  except vg's wrench and fire extinguisher sprites at 32×32 (D-70, D-74), and
  the filter's and mixer's boxes, vg's own at 2 texels a pixel (D-71). A block taller than a block,
  like the 26 px canister, uses a 32×32 texture, still at 16 px per block.
- **vg's colours with a vanilla finish** (style A, the owner's pick in the
  pilot): no black outlines on block faces, flat colour with one light and
  one dark edge per face, and no wear. Item sprites keep vg's outlines.
- **Round where vg is round.** Round parts are discs drawn in pixels, built
  as stacks of crossed boxes: the pipe is a 4 px disc, the canister's body a
  12 px disc.
- **Light from the top left,** as in vanilla.
- **vg's colour coding stays:** grey pipe, near-black radiator pipe, vg's
  canister colours, and the controller's green, yellow and red lights.
- **Dyed blocks are tinted.** A block players dye, such as the pipe, is drawn
  in light greys and tinted in code. Parts that should stand out, like the
  pipe's flanges, are drawn darker, so they come out a darker shade of the
  dye.
- **Tints show state too.** A part that code colours by what it holds is
  drawn in light greys with a tint index: the tanks in the tank transfer
  valve and the tank holder, by kind, and the heat exchanger's coil and pipe,
  by the temperature of its gas (batch 5).
- **Glass is partly transparent,** as vg's is. The 2 by 2 airlock's panes
  show on both faces of its leaf, so each face carries 1 − √(1 − a) of vg's
  alpha a, and the two together look like vg's one pane. Its models use the
  translucent render type.
- **One port design.** Every device port uses the pipe's 6 px round flange,
  painted flush on the face in an undyed pipe's colours. Output ports carry an
  arrow, so a device's input and output are easy to tell apart. A device with
  one pipe takes it on the face opposite its output: the vent's, scrubber's
  and passive vent's back, the connector port's bottom, and the heat
  exchanger's face away from its partner.
- **Ports for one gas or for water are coloured.** The electrolyzer's gas
  outlets are the pipe's flange in each gas's canister colour, and its water
  inlet is square and water blue, so it can't be taken for a gas port.
- **Inline devices sit on the pipe.** The pumps and the valves have the
  pipe's flange at each block edge and a short tube into a body in the
  middle, in an undyed pipe's greys. The pressure pump shows its direction
  with arrows along its body, not on a port (the owner's pick in batch 3),
  and the volume pump with a lamp sweeping toward the output. The filter and
  mixer are boxes with a short third pipe to one side, which a mirrored item
  puts on the left, and vg's arrow diagram on top (batch 4). The radiator adaptor is
  inline too, with the radiator pipe out of its other end (batch 5).
- **Hand valves turn a straight lever,** like the canister's: the manual
  valve's lies across the pipe when shut and in line when open, and the tank
  transfer valve has the same one. Its grip is vg's handwheel red.
- **Worn helmets look round.** Their faces darken toward the edges, past the
  one light and one dark edge. Raised details, like the softsuit's antenna
  and ear plates, go on the armor's outer layer, which Minecraft draws 0.5 px
  further out.
- **States** are separate textures, or overlays where only a light changes.

## 6. Sources for each row

Paths are in the vg repository. A state with frames is animated in vg.

### 6.1 Framework mod (`zas`)

| Proposed id | vg source | States | Route | Notes |
|---|---|---|---|---|
| `zas:gas_fire` | `icons/effects/fire.dmi` | `1`, `2`, `3` (6 to 14 frames) | Original | vg's fire lies flat on a floor tile, so it can't become Minecraft's crossed planes of flame. Drawn from scratch, or from a CC BY-SA base ([§3](#3-sources)). Done in batch 3, from scratch in vg's fire colours: crossed planes of rising flame, two textures picked at random so cells don't flicker in step, and a see-through haze on every face. Faces between two fire blocks aren't culled, so a burning room fills with a glow that thickens with depth. |

### 6.2 Content mod (`zas_atmos`): blocks

Every row is a Redraw unless its notes say otherwise. Each block's item
uses its block model, or for the flat-sprite rows, a sprite redrawn from its
face.

**Pipes and valves**

| Proposed id | vg source | States | Notes |
|---|---|---|---|
| `zas_atmos:pipe` | `icons/obj/pipes/regular.dmi` | `12`, `3` | Done in the pilot: a round 4 px tube with 6 px flanges at the block edges, and a rounded joint. Players dye it ([§5](#5-style)). Its item is the 3D straight pipe. |
| `zas_atmos:pipe_cap` | `icons/obj/pipes.dmi` | `cap` | Done in batch 1: the pipe's flange and tube to the block's middle, closed by a rounded end. Dyed like the pipe. |
| `zas_atmos:radiator_pipe` | `icons/obj/pipes/heat.dmi` | `intact` | vg's is near-black and ribbed, already unlike grey pipe. Done in batch 5: a finned tube, the pipe's 4 px round tube with 8 px round fins every 2 px, in vg's near-black, not dyed, and with no flanges, since it joins only radiator pipe and the adaptor. The owner picked it over vg's four thin tubes. Its item is the 3D straight piece, like the pipe's. |
| `zas_atmos:radiator_adaptor` | `icons/obj/pipes/junction.dmi` | `intact` | One grey end and one black end. Done in batch 5: inline, an undyed pipe's flange and tube into a steel collar, then the finned radiator pipe. It has no texture of its own. |
| `zas_atmos:manual_valve` | `icons/obj/atmospherics/valve.dmi` | `valve0`, `valve1` | Done in batch 3: inline on the pipe, an 8 px box with vg's lamp each side, red shut and green open. vg's handwheel is a straight lever with a red grip (the owner's pick), across the pipe when shut and in line when open. |
| `zas_atmos:redstone_valve` | `icons/obj/atmospherics/digital_valve.dmi` | `valve0`, `valve1` | Add redstone detail to set it apart from the manual valve. Done in batch 3: the manual valve's shape in vg's dark steel with vg's panel on top, and a redstone trace round the body, lit while powered. |

**Devices**

| Proposed id | vg source | States | Notes |
|---|---|---|---|
| `zas_atmos:pressure_pump` | `icons/obj/atmospherics/pump.dmi` | `intact_off`, `intact_on` (5 frames) | vg's pump is a small part on a pipe, 7×16 px. The block is mostly new, with its colours and markings. Done in batch 3: inline on the pipe, an 8 px round body whose lamp bars are two chevrons pointing the way the gas goes, lit in vg's lamp colours when on. vg's red band at the output is left out. |
| `zas_atmos:volume_pump` | `icons/obj/atmospherics/volume_pump.dmi` | `intact_off`, `intact_on` (11 frames) | Done in batch 4: the pressure pump's shape, with vg's dark slot along the body. On, one lamp sweeps toward the output in vg's colours for each place, and an arrowhead at the slot's output end shows the way when off. |
| `zas_atmos:filter` | `icons/obj/atmospherics/filter.dmi` | `intact_off`, `intact_on` (5 frames) | Done in batch 4: inline, a box 10.5 by 11 px with a short third pipe to one side, the side its item sets (vg's mirrored filter). Its top is vg's box cut out as it is, at 32×32 (D-71), with vg's 5 frames: the input glows, then the path, then the outputs. vg leaves the filtered outlet's arrow dark; here it lights with the output's (the owner's ask). |
| `zas_atmos:mixer` | `icons/obj/atmospherics/mixer.dmi` | `intact_off`, `intact_on` (5 frames) | Done in batch 4: the filter's shape, with vg's box and its 5 frames as they are. |
| `zas_atmos:vent` | `icons/obj/atmospherics/vent_pump.dmi` | `base`, `out`, `in` (8 frames each) | Done in batch 1: a cube that faces any of six ways, like a dispenser, with vg's grille on the front. A light runs round the grille, blue when blowing and red the other way round when siphoning (CONT-070). A pipe joins only the back (CONT-014). |
| `zas_atmos:scrubber` | `icons/obj/atmospherics/vent_scrubber.dmi` | `off`, `on`, `in` (5 frames) | Done in batch 2: the vent's cube, facing any of six ways with a pipe only on its back, with vg's round grille. vg's rings show the mode: a steady blue ring when scrubbing, a shrinking green one when siphoning. vg has no panic state. Panic is new: the siphon's ring in red, twice as fast. vg's own red rings are its stalled states, which ZAS has no texture for. |
| `zas_atmos:passive_vent` | `icons/obj/atmospherics/pipe_vent.dmi` | `base`, `bezel` | Done in batch 5: the vent's cube, facing any of six ways with a pipe only on its back (CONT-016), with vg's square grille in its bezel. |
| `zas_atmos:connector_port` | `icons/obj/atmospherics/portables_connector.dmi` | `intact` | Done in batch 1: a cube set in the floor, socket up, that a canister stands on. Connected, a collar rises round the canister's foot, a new state. A pipe joins only the bottom (CONT-083). |
| `zas_atmos:igniter` | `icons/obj/stationobjs.dmi` | `igniter0`, `igniter1` (3 frames) | Done in batch 3: a 14×14 floor plate 2 px thick, with nine lights that flicker orange when active. |
| `zas_atmos:heat_exchanger` | `icons/obj/atmospherics/heat_exchanger.dmi` | `main` | Done in batch 5: vg's box half a block deep, with vg's three panels on the face its pipe joins, facing any of six ways. A pair stands back to back 2 px apart. Its coil, seen through a window in each side, and its pipe are tinted by the gas temperature in that half: frost blue when cold, copper at room temperature, then red, orange and yellow-white, as vg's heat-exchange pipes glow (the owner's ask). |
| `zas_atmos:airlock_controller` | `icons/obj/airlock_machines.dmi` | `airlock_control_standby`, `airlock_control_process` (2 frames) | The panel is 11×12 px, so its item sprite is a Crop. Done in batch 5: a 10×11 wall panel 2 deep, vg's face at 1 texel a pixel. Cycling, its light blinks red. |
| `zas_atmos:air_sensor` | `icons/obj/airlock_machines.dmi` | `airlock_sensor_standby`, `airlock_sensor_cycle` (2 frames), `airlock_sensor_alert` (3 frames) | A small wall sensor in vg, 10×6 px. Done in batch 5: a 10×6 wall panel 2 deep, in standby, cycling and alert. Its item is its block model. |

**Portable atmospherics**

| Proposed id | vg source | States | Notes |
|---|---|---|---|
| `zas_atmos:canister` | `icons/obj/atmos.dmi` | `yellow`, `can-open`, `can-connector`, `can-o0` to `can-o3` | vg's empty canister is yellow, so that's an undyed canister. Its other colours are in [§6.4](#64-canister-colours). The `can-o` lights show pressure; they're optional. |
| `zas_atmos:portable_pump` | `icons/obj/atmos.dmi` | `psiphon:0`, `psiphon:1` (2 frames) | Done in batch 4: 12×14×12 in vg's blues, with its carry handle and a fan that turns when on. The top has the battery's hatch (CONT-018). |
| `zas_atmos:portable_scrubber` | `icons/obj/atmos.dmi` | `pscrubber:0`, `pscrubber:1` (2 frames) | Done in batch 4: 14 wide, 9 tall and 12 deep, a low white body with its corners cut back under vg's grey housing. The light between vg's green stripes blinks when on. |
| `zas_atmos:heater` | `icons/obj/atmos.dmi` | `sheater0`, `sheater1` (7 frames) | vg's space heater. Done in batch 3: a box 14 px wide on four feet, with vg's own front. |
| `zas_atmos:air_conditioner` | `icons/obj/atmos.dmi` | `aircond0`, `aircond1` (9 frames) | vg's chiller. It's white where the heater is black, so the two already differ. Done in batch 3, at the heater's size. |
| `zas_atmos:tank_holder` | `icons/obj/objects.dmi` | `dispenser`, `oxygen-1` to `oxygen-4` | vg's tank storage unit. Its overlays show how many tanks it holds. Done in batch 5: a full block with an open front, vg's blue panel and lights along the top, and two shelves of five upright tanks, with a model for each count. vg's tanks lie down in two stacks; the owner picked upright ones. Each tank is light grey with its own tint index, so code colours it by kind. |

**Gas sources and power**

| Proposed id | vg source | States | Notes |
|---|---|---|---|
| `zas_atmos:electrolyzer` | None | | **Original.** vg's electrolyzer is a chemistry machine, not a water splitter. Its oxygen generator (`icons/obj/atmospherics/oxygen_generator.dmi`) is the nearest atmospherics device; if it's used as a reference, the texture becomes a Redraw. Done in batch 4, from scratch: the owner found the oxygen generator a poor base. Two round glass columns, after a lab's Hofmann apparatus, stand on a plinth that is the water tank, its front window showing the water. On, bubbles rise and gas gathers under the caps, twice as much H₂ as O₂. Each gas leaves by a pipe flange in its canister colour on its own side (CONT-055), and fluid pipes join only the square inlet on the back (CONT-056). The flanges are the pipe's fitting recoloured, so they're credited to vg's pipe. |
| `zas_atmos:generator` | `icons/obj/power.dmi` | `portgen0`, `portgen1` | Done in batch 1: vg's P.A.C.M.A.N., an engine in a tube frame. The firebox, which glows when lit, is new. |
| `zas_atmos:recharger` | `icons/obj/power.dmi` | `ccharger0`, `ccharger1`, `ccharger-o0` to `ccharger-o4` | vg's cell charger. The overlays give the charge steps. Done in batch 4: vg's plate and tower, with the 4-step gauge on the tower's front. Six states: no battery, empty (its red step blinks) and steps 1 to 4. The battery on it is vg's plain cell whatever its tier. |

**Area air controllers, airlocks and firelocks**

| Proposed id | vg source | States | Notes |
|---|---|---|---|
| `zas_atmos:area_air_controller` | `icons/obj/monitors.dmi` | `alarm0`, `alarm2`, `alarm1`, `alarmp` | vg's air alarm: normal, danger 1, danger 2 and unpowered, in that order. vg's `alarm2` is yellow and `alarm1` red. Done in batch 1: a 12×7 panel on a wall, without vg's "AIR". The danger lights flash with vg's "!". |
| `zas_atmos:fire_alarm` | `icons/obj/monitors.dmi` | `fire0e`, `fire1e`, `firep` | Normal, alarm and unpowered. Done in batch 4: vg's red box as a 10×8 wall panel, 2 deep, its lamp flashing red in an alarm. Its item sprite is a Crop, vg's own with two repeated columns dropped. |
| `zas_atmos:airlock` | `icons/obj/doors/Doorext.dmi`; for the 2×2 layout, `icons/obj/doors/Door2x1glassfull.dmi` | `door_closed`, `door_open`, `door_locked` | vg's external airlock, in two sizes: 1 wide and 2 tall like a standard door, and 2 by 2. vg's doors are one tile, so both are redrawn taller. The 2×2 follows how vg's two-tile glass airlock splits into two leaves. Done in batch 5, in the firelock's form: a frame the block's full depth with the leaf in the middle, closed, open and bolted (vg's `door_locked`, which no requirement lists yet). The 1 by 2 is vg's red airlock, its leaf sliding up into the header; vg's lettering doesn't fit it. The 2 by 2 is vg's glass door itself (the owner's pick): grey leaves with see-through panes, parting sideways into the posts. It's proposed as a block of its own, `zas_atmos:large_airlock`. vg's glass door sprites came from Baystation 12, which licenses its icons CC BY-SA 3.0. |
| `zas_atmos:firelock` | `icons/obj/doors/Doorfire.dmi` | `door_open`, `door_closed`, `door_locked`, `palert` (2 frames), `alert_hot`, `alert_cold` | vg's old full-tile firelock, red with "FIRE" and hazard stripes, from vg's first commit in 2010. It was replaced in 2012 by today's yellow `DoorHazard.dmi`, ported from Baystation 12. `palert` flashes for lockdown. The temperature lights `alert_hot` and `alert_cold` are optional. See [Firelocks on doors](#firelocks-on-doors). Done in batch 2: 1 by 2, a gold frame the block's full depth with a 4 px panel in the middle, closed, open and locked down, on its own and over a vanilla door. The temperature lights aren't drawn. Batch 5 put it on airlocks: its panel just off each face of the leaf, the 1 by 2's own, and for the 2 by 2 one panel the width of both leaves with FIRE at twice the size. |

#### Firelocks on doors

A firelock can be placed on a door, either a vanilla door or an airlock (the
owner's choice, 2026-11-01). The door is then drawn with the firelock as an
overlay, so the firelock's textures must work over any door:

- **Open,** it's vg's empty frame around the doorway, transparent in the
  middle so the door shows through.
- **Closed and locked down,** its panel covers the door.
- **It comes in the doors' sizes:** 1 by 2 for vanilla doors and the small
  airlock, and 2 by 2 for the large airlock. On its own, a firelock is 1 by 2,
  a frame the block's full depth with its panel in the middle (the owner's
  choice, 2026-11-01).
- **Its model sits just outside the door's faces,** so the two don't flicker
  where they meet.

### 6.3 Content mod (`zas_atmos`): items

The vg size is the sprite's opaque area inside its 32 px frame.

| Proposed id | vg source | States | vg size | Route | Notes |
|---|---|---|---|---|---|
| `zas_atmos:oxygen_tank` | `icons/obj/tank.dmi` | `oxygen` | 22×21 | Redraw | Done in batch 2: redrawn at 45° to fill the slot. |
| `zas_atmos:emergency_oxygen_tank` | `icons/obj/tank.dmi` | `emergency` | 8×18 | Crop | Already looks smaller than the others. Done in batch 2: cut to 16 px tall. |
| `zas_atmos:air_tank` | `icons/obj/tank.dmi` | `oxygen_f` | 22×21 | Redraw | vg reuses the O₂ tank's sprite for air. Done in batch 2: the O₂ tank in the colours of vg's yellow variant, `oxygen_f` (the owner's choice, 2026-11-01). |
| `zas_atmos:empty_tank` | `icons/obj/tank.dmi` | `oxygen` | 22×21 | Redraw | No vg sprite; a grey recolour of the O₂ tank. Done in batch 2. |
| `zas_atmos:tank_transfer_valve` | `icons/obj/assemblies.dmi` | `valve`, `valve_1`, `oxygen` | 8×10 | Redraw | vg draws each attached tank as an overlay, such as `oxygen`. The timer is new. Done in batch 3: vg's sprites with the manual valve's lever for vg's handwheel, and upright tanks in greys that code tints by kind. Once primed it's an entity (CONT-104), with block models: an H from above, the valve between two tanks lying on the floor, its timer running or the valve open. vg's transfer valve sprites came from Baystation 12, which licenses its icons CC BY-SA 3.0. |
| `zas_atmos:breath_mask` | `icons/obj/clothing/masks.dmi` | `breath` | 18×13 | Crop | Done in batch 2: cut to 16 px wide, with its worn mask. |
| `zas_atmos:gas_mask` | `icons/obj/clothing/masks.dmi` | `gas_alt` | 21×17 | Crop | vg's standard gas mask (the owner's choice in the batch 2 review). vg's older `gas_mask` is only its plague doctor's. Done in batch 2: cut to 15×16, with its worn mask. |
| `zas_atmos:softsuit_helmet` | `icons/obj/clothing/hats.dmi` | `space` | 16×16 | Crop | Done in batch 1, with its worn helmet. |
| `zas_atmos:softsuit_chestplate` | `icons/obj/clothing/suits.dmi` | `space` | 23×22 | Redraw | Done in batch 2: vg's worn front, waist up, narrowed to 16 px. |
| `zas_atmos:softsuit_leggings` | `icons/obj/clothing/suits.dmi` | `space` | | Redraw | vg's suit is one piece. The legs come from its lower half. Done in batch 2. |
| `zas_atmos:softsuit_boots` | `icons/obj/clothing/suits.dmi` | `space` | | Redraw | From the suit's feet. Done in batch 2. |
| `zas_atmos:hardsuit_helmet` | `icons/obj/clothing/hats.dmi` | `rig0-engineering` | 15×16 | Crop | vg's base hardsuit, in brown and gold with a blue visor. vg's civilian and engineering hardsuits both use it. Done in batch 4: vg's sprite as it is, with its worn helmet. vg's rig sprites came from Baystation 12 (Flashkirby99's), which licenses its icons CC BY-SA 3.0. |
| `zas_atmos:hardsuit_chestplate` | `icons/obj/clothing/suits.dmi` | `rig-engineering` | 21×25 | Redraw | Done in batch 4: vg's worn front (`icons/mob/suit.dmi`), waist up, narrowed to 16 px. |
| `zas_atmos:hardsuit_leggings` | `icons/obj/clothing/suits.dmi` | `rig-engineering` | | Redraw | As the softsuit's. Done in batch 4, from the worn suit's legs. |
| `zas_atmos:hardsuit_boots` | `icons/obj/clothing/suits.dmi` | `rig-engineering` | | Redraw | As the softsuit's. Done in batch 4, from the worn suit's feet. |
| `zas_atmos:magnetic_boots` | `icons/obj/clothing/shoes.dmi` | `magboots0`, `magboots1` | 18×12 | Crop | Off and on. Done in batch 4: cut to 16 px wide, with vg's ankle light red off and green on, on the item and worn (the owner's choice). |
| `zas_atmos:atmos_analyzer` | `icons/obj/device.dmi` | `atmos` | 14×22 | Redraw | Done in batch 1: redrawn at 10×16 to fill the slot. |
| `zas_atmos:wrench` | `icons/obj/items.dmi` | `wrench` | 23×22 | As is | Done in the pilot: vg's sprite unchanged, at 32×32 and not mirrored (D-70). |
| `zas_atmos:crowbar` | `icons/obj/items.dmi` | `crowbar` | 27×21 | Redraw | Done in batch 2: redrawn at 45° to fill the slot's width. |
| `zas_atmos:multitool` | `icons/obj/device.dmi` | `multitool` | 9×17 | Crop | Done in batch 4: cut to 16 px tall. |
| `zas_atmos:fire_extinguisher` | `icons/obj/items.dmi` | `fire_extinguisher0` | 14×22 | As is | Added 2026-11-03 when the owner moved CONT-037 to R1. Done in wave 6: vg's sprite unchanged, centred in a 32×32 texture (D-74). vg's `fire_extinguisher1` (safety off) differs only in the hose's angle and isn't used. The sprite is unchanged since vg's first commit of items.dmi (662c082, 2010), so it has no Goon or NC source. |
| `zas_atmos:battery` | `icons/obj/power.dmi` | `cell`, `cell-o1`, `cell-o2` | 11×8 | Crop | The overlays show the charge. Done in batch 4: grey, as it is. The overlays, yellow part charged and green full, are shared by all three tiers. |
| `zas_atmos:high_capacity_battery` | `icons/obj/power.dmi` | `hcell` | 11×8 | Crop | vg's high- and super-capacity cells look much alike. Recolour them so the tiers are easy to tell apart. Done in batch 4: red, as it is. |
| `zas_atmos:super_capacity_battery` | `icons/obj/power.dmi` | `scell` | 11×8 | Crop | Done in batch 4: in the blues of vg's ultra cell, `ucell`, keeping `scell`'s two stripes. |

**Worn textures** are redrawn onto Minecraft's armor layout from vg's worn
sprites. Those have four directions, which give the front, back and sides;
the top and bottom are new. The textures must match their item sprites.

| Worn texture | vg source | States |
|---|---|---|
| Breath mask, layer 1 | `icons/mob/mask.dmi` | `breath` |
| Gas mask, layer 1 | `icons/mob/mask.dmi` | `gas_alt` |
| Softsuit, layers 1 and 2 | `icons/mob/head.dmi`, `icons/mob/suit.dmi` | `space` |
| Hardsuit, layers 1 and 2 | `icons/mob/head.dmi`, `icons/mob/suit.dmi` | `rig0-engineering`, `rig-engineering` |
| Magnetic boots, layer 1, off and on | `icons/mob/feet.dmi` | `magboots0`, `magboots1` |

The softsuit's helmet was drawn in batch 1, as the head part of layer 1. Its
antenna and ear plates are on the outer layer. Batch 2 drew the rest of the
suit, with vg's 13 on the back (the owner's choice). It doesn't show the tank
it holds. The masks' raised parts, the breath mask's cup and the gas mask's
filter, are on the outer layer too. Batch 4 drew the hardsuit the same
way, its crest ridges and headlamp on the outer layer and its ribs
alternating gold and brown so it looks heavier (CONT-089). Minecraft mirrors
the right arm onto the left, so vg's arm light is on both arms' outer sides.
The magnetic boots cover the legs' lower seven rows.

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
| Orange | `orange` | H₂ |
| Green | `green` | |
| Cyan | `cyan` | |
| White | None | H₂O vapor |
| Gray | None | |
| Brown | None | CH₄ |
| Lime | None | |
| Light blue | None | |
| Purple | None | CO |
| Magenta | None | |
| Pink | None | |

- **All 17 are Redraws.** vg's nine are redrawn from their own states. The
  eight dyes vg has no colour for are recolours of the same design, credited
  to the vg state they follow. Done in batch 3: the eight recolours follow
  Minecraft's dye colours, shaded in the steps of vg's red.
- **Only blue keeps vg's pale band,** as in vg. Red is plain, so it stands
  apart from red-and-white, which has vg's white rings at the shoulder and
  the foot (the owner's pick in batch 3).
- **vg's gas symbols are left out.** vg marks some colours with a symbol for
  the gas it held, such as a "P" for plasma on orange and a radiation mark on
  green. A dyed canister can hold any gas, so a symbol would mislead. The
  yellow canister keeps its hazard stripes.
- **Red-and-white has no dye.** In R1 only the creative N₂O canister has it,
  and dyeing that canister changes it to the dye's colour.
- **The creative canisters** use vg's colour for the gases vg has: O₂, N₂, CO₂
  and N₂O. vg has no canister for the other four, so their colours are new,
  as the owner accepted them in batch 3. H₂ is orange because it takes the
  place of vg's plasma, the main fuel, whose canister is orange. Water vapor is
  white, like steam. CH₄ is brown and CO purple, chosen only to stand apart
  from the rest.

### 6.5 Crafting components

Added 2026-11-02 with the recipes (D-72), after the five batches. Every
component's sprite is vg's own: a **Crop** into 16×16 where vg's sprite fits,
and otherwise **As is**, vg's sprite unchanged in its 32×32 frame. They're
crafting items only, so none is redrawn to fill the slot. The vg size is the
sprite's opaque area.

| Id | vg source | State | vg size | Route | Notes |
|---|---|---|---|---|---|
| `zas_atmos:cable_coil` | `icons/obj/power.dmi` | `coil` | 26×12 | As is | vg's greyscale coil, tinted vg's default cable red (#FF0000) by multiplying, as vg tints it in game. |
| `zas_atmos:circuit_board` | `icons/obj/module.dmi` | `std_mod` | 19×13 | As is | vg's standard board. |
| `zas_atmos:electronics` | `icons/obj/doors/door_assembly.dmi` | `door_electronics` | 14×10 | Crop | vg's airlock electronics. |
| `zas_atmos:machine_frame` | `icons/obj/machines/constructable.dmi` | `box_0` | 32×26 | As is | vg's empty machine frame. |
| `zas_atmos:capacitor` | `icons/obj/stock_parts.dmi` | `capacitor2_basic` | 20×6 | As is | |
| `zas_atmos:advanced_capacitor` | `icons/obj/stock_parts.dmi` | `capacitor2_adv` | 20×8 | As is | |
| `zas_atmos:super_capacitor` | `icons/obj/stock_parts.dmi` | `capacitor2_super` | 20×8 | As is | |
| `zas_atmos:scanning_module` | `icons/obj/stock_parts.dmi` | `scan_module` (5 frames) | 8×11 | Crop | Animated, at vg's frame delay. |
| `zas_atmos:micro_manipulator` | `icons/obj/stock_parts.dmi` | `micro_mani` | 11×10 | Crop | |
| `zas_atmos:micro_laser` | `icons/obj/stock_parts.dmi` | `micro_laser` | 5×13 | Crop | |
| `zas_atmos:console_screen` | `icons/obj/stock_parts.dmi` | `screen` | 22×11 | As is | |
| `zas_atmos:igniter_assembly` | `icons/obj/assemblies/new_assemblies.dmi` | `igniter` | 13×14 | Crop | |
| `zas_atmos:timer_assembly` | `icons/obj/assemblies/new_assemblies.dmi` | `timer` | 14×10 | Crop | |
| `zas_atmos:ansible_crystal` | `icons/obj/stock_parts.dmi` | `ansible_crystal` | 7×13 | Crop | |
| `zas_atmos:subspace_ansible` | `icons/obj/stock_parts.dmi` | `subspace_ansible` | 24×13 | As is | |
| `zas_atmos:subspace_transmitter` | `icons/obj/stock_parts.dmi` | `subspace_transmitter` | 20×17 | As is | |
| `zas_atmos:bluespace_crystal` | `icons/obj/telescience.dmi` | `bluespace_crystal` | 6×8 | Crop | |
| `zas_atmos:plasteel_sheet` | `icons/obj/stacks_sheets.dmi` | `sheet-plasteel` | 16×20 | As is | |
| `zas_atmos:reinforced_glass` | `icons/obj/stacks_sheets.dmi` | `sheet-rglass` | 16×20 | As is | |

**History check** ([§3](#3-sources)), 2026-11-02, over the eight files' 133
commits: no Goon or NonCommercial source.

- **Older files.** vg moved its sheets out of `icons/obj/items.dmi` in 2023,
  and its machine frame out of `icons/obj/stock_parts.dmi` in 2025. Both
  sprites are unchanged since, so those files' histories were checked too.
  The one Goon hit, `items.dmi`'s "Goon-style Ores" (`cda6160`), leaves both
  sheets' visible pixels as they were.
- **The other hits** name an artist, or a port from Baystation 12 or
  /tg/station, both CC BY-SA 3.0. The airlock electronics are unchanged by
  `door_assembly.dmi`'s Baystation 12 port (`a2d11cf`).

## 7. Order of work

Art follows the build order in the [plan](plan.md#phase-e-r1-feature-tracks-in-parallel-once-r0-is-done):
first the S-01 slice, then scenario by scenario. Art can ship ahead of its
blocks (the owner's change, 2026-11-01). Each batch is made in waves that
the owner reviews on a page that renders the JSON models in 3D, in every
state and dye colour, and an approved wave goes in one art PR. Its files sit
unused until their block or item is registered, and the PR that registers
it checks them in game. A block registered before its art may use a plain
placeholder texture, credited `original`.

**AI tools make the textures,** crops and redraws alike (the owner's choice,
2026-11-01). They write each PNG at 16 px from the vg sprite, the palette
and the textures already approved, and the models are built in Blockbench
or written as JSON. The pilot in batch 0 set the style the rest follow
([§5](#5-style)). A person checks every texture in game in the PR that
registers its block or item, as CONTRIBUTING asks of AI-made player-facing
changes.

| Batch | Scope | Rows |
|---|---|---|
| 0. Set-up | The DMI extractor, the palette, the history check of each vg file ([§3](#3-sources)), and a pilot to settle the style: the pipe, the canister and the wrench, one of each kind of texture. The pilot is done (2026-11-01) | – |
| 1. S-01 | Pipe, pipe cap, connector port, vent, the canister in yellow, blue and red (the O₂ and N₂ creative canisters), area air controller, generator, softsuit helmet, wrench, atmos analyzer. Done (2026-11-01) | 12 |
| 2. S-02 and S-08 | Firelock at 1 by 2, on its own and over a vanilla door, crowbar, scrubber, the rest of the softsuit, the four tanks, breath mask, gas mask, and the worn textures of the masks and softsuit. Done (2026-11-02) | 12 |
| 3. S-03 to S-05 and S-13 | Gas fire, igniter, heater, air conditioner, pressure pump, manual and redstone valves, tank transfer valve, the other 14 canister colours, which cover the six other creative canisters. Done (2026-11-02) | 14 |
| 4. The rest of R1's Must and Should | Hardsuit, magnetic boots, multitool, fire alarm, filter, mixer, volume pump, electrolyzer, recharger, the three batteries, portable pump and scrubber. Done (2026-11-02) | 17 |
| 5. Could, and rows with no priority yet | Radiator pipe and adaptor, heat exchanger, airlock in both sizes with the 2-by-2 firelock, airlock controller, air sensor, tank holder, passive vent. Done (2026-11-02) | 8 |

The batches cover all 63 rows. The pilot's three textures count in batch 1.
The pipe cap has no priority yet, but it's in batch 1 because an open pipe end
leaks (CONT-004). Batches 4 and 5 can run alongside 2 and 3.

**Each art PR:**
- adds a `CREDITS` entry for every texture, model and `.mcmeta` file, and
  passes `python scripts/check_credits.py`
- shows each texture in game, in every state, with screenshots in "Human
  testing" (player-facing, per [CONTRIBUTING](../CONTRIBUTING.md#using-ai)),
  if its block or item is registered. Otherwise the PR says the owner
  approved it on the review page, and the in-game check comes with the PR
  that registers it.

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
7. **Art can ship ahead of its blocks,** reviewed on a review page, with the
   in-game check in the PR that registers each block or item
   ([§7](#7-order-of-work)).
8. **The pilot settled the style** ([§5](#5-style)): vg's colours with a
   vanilla finish; a round 4 px pipe with 6 px flanges, dyed by players, its
   item the 3D model; a round canister 26 px tall with a ball valve on a front
   port; and vg's own wrench sprite (D-70).
9. **Players dye pipes,** as they dye canisters. Dyeing pipes has no
   requirement yet.
10. **Batch 1 settled** ([§5](#5-style), [§6](#6-sources-for-each-row)):
    - The connector port is set in the floor, facing up, and the vent faces
      any of six ways. Each takes a pipe only on the face opposite its output
      (CONT-014, CONT-083).
    - The area air controller is a small wall panel without "AIR".
    - Item sprites larger than 16 px are redrawn to fill the slot.
    - Worn helmets are shaded to look round, with raised details on the
      armor's outer layer.
11. **Batch 2 settled** ([§6](#6-sources-for-each-row)):
    - A firelock on its own is 1 by 2: a gold frame the block's full depth,
      with its panel in the middle.
    - The scrubber is shaped like the vent (CONT-015). Panic is the siphon's
      ring in red, twice as fast.
    - The air tank is vg's yellow tank, and the empty tank grey.
    - The gas mask is vg's standard one, `gas_alt`.
    - The softsuit keeps vg's 13 on its back.
12. **Batch 3 settled** ([§5](#5-style), [§6](#6-sources-for-each-row)):
    - The canister colours are as [§6.4](#64-canister-colours) proposed,
      with the pale band on blue only.
    - The pump and valves sit inline on the pipe. The pump shows its
      direction with arrows along its body, and the manual valve turns a
      straight lever.
    - The gas fire is crossed planes of flame with a see-through haze.
    - The igniter is a floor plate, and the heater and air conditioner are
      14 px wide.
    - The redstone valve has a redstone trace.
    - A primed tank transfer valve is an entity (CONT-104), with tanks lying
      on the floor either side of the valve.
13. **Batch 4 settled** ([§5](#5-style), [§6](#6-sources-for-each-row)):
    - The filter and mixer sit inline, with the side port on the side its
      item sets. Their tops are vg's own boxes at 32×32 (D-71).
    - The volume pump is the pressure pump's shape, with vg's slot and a
      sweeping lamp.
    - The electrolyzer is drawn from scratch as glass columns on a water
      tank. Each gas leaves by its own port (CONT-055), and fluid pipes join
      only the inlet on the back (CONT-056).
    - The recharger shows the charge in four steps, and the batteries are
      grey, red and blue.
    - The portable pump and scrubber each fit in one block.
    - The hardsuit's crest and headlamp stand out on the outer layer, and
      the magnetic boots show vg's ankle light.
14. **Batch 5 settled** ([§5](#5-style), [§6](#6-sources-for-each-row)):
    - The radiator pipe is a finned tube in vg's near-black, not dyed, and
      the adaptor sits inline.
    - The heat exchanger is vg's half-block pair, 2 px apart, its coil and
      pipe coloured by the gas temperature.
    - The passive vent is the vent's cube with a pipe only on its back
      (CONT-016).
    - Airlocks take the firelock's form and have a bolted state. The 1 by 2
      is vg's red airlock; the 2 by 2 is vg's glass door, see-through.
    - The airlock controller and air sensor are small wall panels.
    - The tank holder shows its tanks upright on two shelves, each tinted by
      its kind.
