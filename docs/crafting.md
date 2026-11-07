# Crafting

**Status:** approved by the owner, 2026-11-02. The fire extinguisher's recipe was
proposed on 2026-11-04 and approved on 2026-11-05. Recipes for blocks and items
that aren't in the mod yet are already shipped, and load once their item exists,
except the central atmospherics controller's: its row below has no recipe file yet.

This page lists how every ZAS³ Atmospherics block and item is crafted, and
the order a survival player gets them in (CONT-058). Every recipe uses
vanilla materials, either directly or through the mod's crafting components,
which are themselves made from vanilla materials (CONT-051).

## How recipes work

- **They're ordinary data-pack recipes** (CONT-059), one file per item in
  `data/zas_atmos/recipe/`, so a data pack can replace or remove any of them.
- **Each one loads only once every `zas_atmos` item it names exists.** Each
  recipe file has a `neoforge:item_exists` condition for its result and for
  each of the mod's items among its ingredients. Recipes for blocks still to
  come are skipped quietly until those blocks are added, with no errors in
  the log.
- **A filter or mixer converts to and from its mirrored version** (D-77), one
  for one.
- **Filled canisters are creative-only** (CONT-086). You craft an empty
  canister, and the same goes for tanks: you fill an empty tank from a
  canister to get an O₂ tank or an air tank (CONT-039). Neither the O₂ tank
  nor the air tank has a recipe.
- **A crafted fire extinguisher comes full** (CONT-037). The water bucket in
  its recipe charges it and the empty bucket comes back. After that it refills
  at a water source block, not in the crafting grid.

## Progression

Each recipe belongs to the stage of the hardest vanilla material it needs
once its components are broken down.

| Stage | Needs | What it unlocks |
|---|---|---|
| Workshop | Iron, copper, redstone, glass, amethyst and other early materials | Nearly everything: pipes, every device, the area air controller, the multitool, the softsuit and the first two batteries |
| Hardened | Obsidian, so a diamond pickaxe | Plasteel, and with it the firelock and the hardsuit |
| Bluespace | Ender pearls | The bluespace crystal, the super capacitor and the super-capacity battery |

A few patterns repeat:

- **Powered devices** (pumps, filter, mixer, vent and scrubber): their own
  parts across the top, the pipe ports and an iron casing in the middle, and
  cable coil, a circuit board and cable coil along the bottom.
- **The bigger machines and the portables** are built on a machine frame:
  the heater, air conditioner, electrolyzer, generator, recharger, portable
  pump and portable scrubber.
- **Wall panels and doors** take electronics instead of a circuit board and
  frame.
- **The hardsuit** is the softsuit, plated piece by piece in plasteel.
- **The capacitors set the battery tiers:** the battery takes a capacitor,
  the high-capacity battery an advanced capacitor, and the super-capacity
  battery a super capacitor.

## Crafting components

The components follow vgstation13's stock parts, and their sprites are vg's:
cropped to 16×16 where they fit, and otherwise unchanged at 32×32 (D-72).

<!-- GENERATED: components -->

| Component | Stage | Goes into |
|---|---|---|
| Cable coil | Workshop | Electronics, Igniter assembly, Timer assembly, Subspace transmitter, Pressure pump, Volume pump, Filter, Mixer, Vent, Scrubber, Igniter, Heater, Air conditioner, Water electrolyzer, Generator, Recharger, Magnetic boots |
| Reinforced glass sheet | Workshop | Water electrolyzer, Large airlock |
| Plasteel sheet | Hardened | Firelock, Hardsuit helmet, Hardsuit chestplate, Hardsuit leggings, Hardsuit boots |
| Machine frame | Workshop | Portable pump, Portable scrubber, Heater, Air conditioner, Water electrolyzer, Generator, Recharger |
| Circuit board | Workshop | Electronics, Subspace ansible, Pressure pump, Volume pump, Filter, Mixer, Vent, Scrubber, Heater, Air conditioner, Water electrolyzer, Generator, Recharger, Multitool |
| Electronics | Workshop | Area air controller, Fire alarm, Airlock controller, Air sensor, Firelock, Airlock, Large airlock |
| Capacitor | Workshop | Advanced capacitor, Igniter assembly, Subspace ansible, Subspace transmitter, Volume pump, Portable pump, Portable scrubber, Generator, Recharger, Battery, Area air controller, Magnetic boots, Atmos analyzer |
| Advanced capacitor | Workshop | Super capacitor, High-capacity battery |
| Super capacitor | Bluespace | Super-capacity battery |
| Scanning module | Workshop | Filter, Scrubber, Recharger, Area air controller, Fire alarm, Air sensor, Atmos analyzer |
| Micro-manipulator | Workshop | Redstone valve, Pressure pump, Volume pump, Filter, Mixer, Vent, Scrubber, Firelock, Airlock, Large airlock |
| Micro-laser | Workshop | Heater, Air conditioner |
| Console screen | Workshop | Heater, Air conditioner, Area air controller, Airlock controller, Atmos analyzer |
| Igniter assembly | Workshop | Igniter |
| Timer assembly | Workshop | Tank transfer valve |
| Ansible crystal | Workshop | Subspace ansible, Subspace transmitter, Air sensor, Multitool |
| Subspace ansible | Workshop | Area air controller, Airlock controller |
| Subspace transmitter | Workshop | Area air controller |
| Bluespace crystal | Bluespace | Super capacitor |

<!-- END GENERATED: components -->

## Recipes

In the grids, `.` is an empty slot. A shapeless recipe takes its ingredients
in any arrangement.

<!-- GENERATED: recipes -->

### Components

| Makes | Stage | Grid | Ingredients |
|---|---|---|---|
| 8 × Cable coil | Workshop | Shapeless | 3 × copper ingot, dried kelp |
| 4 × Reinforced glass sheet | Workshop | `.G.` / `GXG` / `.G.` | `G` glass, `X` iron bars |
| 2 × Plasteel sheet | Hardened | `IOI` | `I` iron ingot, `O` obsidian |
| Machine frame | Workshop | `IXI` / `X.X` / `IXI` | `I` iron ingot, `X` iron bars |
| 2 × Circuit board | Workshop | Shapeless | green dye, 2 × redstone dust, copper ingot, iron nugget |
| 2 × Electronics | Workshop | Shapeless | circuit board, redstone torch, cable coil |
| 2 × Capacitor | Workshop | Shapeless | copper ingot, redstone dust, 2 × iron nugget |
| Advanced capacitor | Workshop | Shapeless | 2 × capacitor, gold ingot, redstone dust |
| Super capacitor | Bluespace | Shapeless | 2 × advanced capacitor, bluespace crystal, gold ingot |
| Scanning module | Workshop | Shapeless | glass pane, redstone dust, copper ingot, iron nugget |
| 2 × Micro-manipulator | Workshop | Shapeless | 3 × iron nugget, redstone dust |
| Micro-laser | Workshop | Shapeless | redstone torch, glass pane, copper ingot |
| Console screen | Workshop | Shapeless | 2 × glass pane, redstone dust |
| Igniter assembly | Workshop | Shapeless | flint, capacitor, cable coil, iron nugget |
| Timer assembly | Workshop | Shapeless | clock, cable coil |
| 8 × Ansible crystal | Workshop | Shapeless | amethyst shard, glass, redstone dust |
| Subspace ansible | Workshop | Shapeless | ansible crystal, circuit board, capacitor, copper ingot |
| Subspace transmitter | Workshop | Shapeless | lightning rod, ansible crystal, capacitor, cable coil |
| Bluespace crystal | Bluespace | `LAL` / `AEA` / `LAL` | `L` lapis lazuli, `A` amethyst shard, `E` ender pearl |

### Pipes and valves

| Makes | Stage | Grid | Ingredients |
|---|---|---|---|
| 8 × Pipe | Workshop | `CCC` | `C` copper ingot |
| Pipe cap | Workshop | Shapeless | pipe, iron nugget |
| Radiator pipe | Workshop | Shapeless | pipe, iron bars |
| Radiator adaptor | Workshop | Shapeless | pipe, radiator pipe, iron ingot |
| Manual valve | Workshop | `.L.` / `PIP` | `L` lever, `P` pipe, `I` iron ingot |
| Redstone valve | Workshop | Shapeless | manual valve, micro-manipulator, redstone dust |
| Connector port | Workshop | `IPI` / `.H.` | `I` iron ingot, `P` pipe, `H` heavy weighted pressure plate |

### Devices

| Makes | Stage | Grid | Ingredients |
|---|---|---|---|
| Pressure pump | Workshop | `.M.` / `PIP` / `WBW` | `I` iron ingot, `W` cable coil, `B` circuit board, `P` pipe, `M` micro-manipulator |
| Volume pump | Workshop | `MKM` / `PIP` / `WBW` | `I` iron ingot, `W` cable coil, `B` circuit board, `P` pipe, `M` micro-manipulator, `K` capacitor |
| Filter | Workshop | `MSM` / `PIP` / `WBW` | `I` iron ingot, `W` cable coil, `B` circuit board, `P` pipe, `M` micro-manipulator, `S` scanning module |
| Mixer | Workshop | `MPM` / `PIP` / `WBW` | `I` iron ingot, `W` cable coil, `B` circuit board, `P` pipe, `M` micro-manipulator |
| Mirrored filter | Workshop | Shapeless | filter |
| Filter | Workshop | Shapeless | mirrored filter |
| Mirrored mixer | Workshop | Shapeless | mixer |
| Mixer | Workshop | Shapeless | mirrored mixer |
| Vent | Workshop | `XXX` / `PIM` / `WBW` | `I` iron ingot, `W` cable coil, `B` circuit board, `P` pipe, `X` iron bars, `M` micro-manipulator |
| Scrubber | Workshop | `XSX` / `PIM` / `WBW` | `I` iron ingot, `W` cable coil, `B` circuit board, `P` pipe, `X` iron bars, `S` scanning module, `M` micro-manipulator |
| Passive vent | Workshop | `XXX` / `IPI` | `X` iron bars, `I` iron ingot, `P` pipe |
| Igniter | Workshop | Shapeless | igniter assembly, heavy weighted pressure plate, cable coil |
| 2 × Heat exchanger | Workshop | `CPC` / `CRC` / `CCC` | `C` copper ingot, `P` pipe, `R` radiator pipe |

### Portables

| Makes | Stage | Grid | Ingredients |
|---|---|---|---|
| Canister | Workshop | `ILI` / `I.I` / `III` | `I` iron ingot, `L` lever |
| Portable pump | Workshop | Shapeless | canister, pressure pump, capacitor, machine frame |
| Portable scrubber | Workshop | Shapeless | canister, scrubber, capacitor, machine frame |
| Heater | Workshop | `LLL` / `CFT` / `WBW` | `F` machine frame, `W` cable coil, `B` circuit board, `L` micro-laser, `C` copper ingot, `T` console screen |
| Air conditioner | Workshop | `LLL` / `SFT` / `WBW` | `F` machine frame, `W` cable coil, `B` circuit board, `L` micro-laser, `S` snow block, `T` console screen |
| Tank holder | Workshop | `III` / `X.X` / `III` | `I` iron ingot, `X` iron bars |

### Gas sources and power

| Makes | Stage | Grid | Ingredients |
|---|---|---|---|
| Water electrolyzer | Workshop | `GCG` / `PFP` / `WBW` | `F` machine frame, `W` cable coil, `B` circuit board, `P` pipe, `G` reinforced glass sheet, `C` copper ingot |
| Generator | Workshop | `IUI` / `KFK` / `WBW` | `F` machine frame, `W` cable coil, `B` circuit board, `I` iron ingot, `U` furnace, `K` capacitor |
| Recharger | Workshop | `KSK` / `IFI` / `WBW` | `F` machine frame, `W` cable coil, `B` circuit board, `K` capacitor, `S` scanning module, `I` iron ingot |
| Battery | Workshop | Shapeless | capacitor, 2 × copper ingot, redstone dust |
| High-capacity battery | Workshop | Shapeless | advanced capacitor, 2 × copper ingot, block of redstone |
| Super-capacity battery | Bluespace | Shapeless | super capacitor, 2 × copper ingot, block of redstone |

### Panels and doors

| Makes | Stage | Grid | Ingredients |
|---|---|---|---|
| Area air controller | Workshop | Shapeless | electronics, console screen, scanning module, subspace ansible, subspace transmitter, capacitor, iron ingot |
| Central atmospherics controller | Workshop | `TTT` / `AFX` / `WBW` | `F` machine frame, `W` cable coil, `B` circuit board, `T` console screen, `A` subspace ansible, `X` subspace transmitter |
| Fire alarm | Workshop | Shapeless | electronics, scanning module, note block, red dye |
| Airlock controller | Workshop | Shapeless | electronics, console screen, subspace ansible, iron ingot |
| Air sensor | Workshop | Shapeless | electronics, scanning module, ansible crystal |
| Firelock | Hardened | `SE` / `SM` / `SS` | `S` plasteel sheet, `E` electronics, `M` micro-manipulator |
| Airlock | Workshop | `IE` / `IM` / `II` | `I` iron ingot, `E` electronics, `M` micro-manipulator |
| Large airlock | Workshop | `IGI` / `EGM` / `IGI` | `I` iron ingot, `G` reinforced glass sheet, `E` electronics, `M` micro-manipulator |

### Wearables and tools

| Makes | Stage | Grid | Ingredients |
|---|---|---|---|
| Empty tank | Workshop | `L` / `I` / `I` | `L` lever, `I` iron ingot |
| Emergency O₂ tank | Workshop | `L` / `I` | `L` lever, `I` iron ingot |
| Tank transfer valve | Workshop | Shapeless | manual valve, timer assembly |
| Breath mask | Workshop | Shapeless | leather, string, iron nugget |
| Gas mask | Workshop | Shapeless | breath mask, charcoal, 2 × glass pane |
| Softsuit helmet | Workshop | `WWW` / `WGW` | `W` white wool, `G` glass pane |
| Softsuit chestplate | Workshop | `W.W` / `WLW` / `WWW` | `W` white wool, `L` leather |
| Softsuit leggings | Workshop | `WWW` / `W.W` / `W.W` | `W` white wool |
| Softsuit boots | Workshop | `W.W` / `L.L` | `W` white wool, `L` leather |
| Hardsuit helmet | Hardened | `SSS` / `SVS` | `S` plasteel sheet, `V` softsuit helmet |
| Hardsuit chestplate | Hardened | `S.S` / `SVS` / `SSS` | `S` plasteel sheet, `V` softsuit chestplate |
| Hardsuit leggings | Hardened | `SVS` / `S.S` / `S.S` | `S` plasteel sheet, `V` softsuit leggings |
| Hardsuit boots | Hardened | `SVS` / `S.S` | `S` plasteel sheet, `V` softsuit boots |
| Magnetic boots | Workshop | Shapeless | iron boots, 2 × capacitor, cable coil |
| Atmos analyzer | Workshop | Shapeless | scanning module, console screen, capacitor, iron ingot |
| Wrench | Workshop | `I.I` / `.I.` / `.I.` | `I` iron ingot |
| Crowbar | Workshop | `.II` / `.I.` / `I..` | `I` iron ingot |
| Multitool | Workshop | Shapeless | ansible crystal, circuit board, iron ingot |
| Fire extinguisher | Workshop | `.L.` / `IRI` / `IWI` | `L` lever, `I` iron ingot, `R` red dye, `W` water bucket |

<!-- END GENERATED: recipes -->
