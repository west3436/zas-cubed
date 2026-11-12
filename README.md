# ZAS³
ZAS³ adds fluid mechanics to your world inspired by Space Station 13's early atmospheric system. Make use of pumps, vents, scrubbers, heaters, and more to keep your space station pressurized or your greenhouse conditioned properly.

## The Atmosphere
 - Open air is unsimulated while areas which have been deliberately sealed and pressurized form zones. Ais is unsimulated outdoors, in caves, and in rooms you haven't sealed; this air has the same properties throughout the dimension and can never run out.
 - A sealed room gets its own air once something changes it; a device adds or removes gas, a leak, or an air controller on its wall. From then on it has a pressure, a temperature and a mix of gases that equalise through openings, vent to vacuum, burn and conduct heat.
 - Too little oxygen, toxic gases, pressure too low or too high, heat/cold or rushing air throwing you into things can hurt you. The HUD indicates whether you are standing in unsafe air prior to taking damage.
 - Combustion needs oxygen; a furnace in a sealed room uses the oxygen up and stops while  hydrogen, methane and other fuels may violently combust if placed under the proper conditions.
 - Normal and Hardcore presets are present to adjust the difficulty; see [the wiki page](https://github.com/west3436/zas-cubed/wiki/Presets) for a specific list of differences. 
 - Gases, each dimension's open air, fire and reactions, hazards, suit protection and heat are all highly configurable via datapacks.

## Mod Contents

This mod contains two modules; **ZAS³** (`zas`) is the air itself and the public API while **ZAS³ Atmospherics** (`zas_atmos`) is the SS13-style content. A pack can turn the content off and keep the simulation alone.

 - **Pipe network:** pipes, end caps, manual and redstone valves, connector ports, pressure and volume pumps, filters, mixers, radiator pipes and heat exchangers.
 - **Room air:** vents, scrubbers, passive vents, area air controllers, a central atmospherics controller, air sensors, heaters and air conditioners.
 - **Fire and doors:** fire alarms, firelocks, igniters, airlocks and airlock controllers.
 - **Gas handling:** canisters, portable pumps and scrubbers, tank holders, tank transfer valves and a water electrolyzer.
 - **Staying alive:** oxygen and air tanks, breath masks and gas masks, softsuits, hardsuits and magnetic boots.
 - **Tools:** atmos analyzer, multitool, wrench, crowbar and fire extinguisher. Also a bike horn.
 - **Power:** devices run on FE from a generator or any other mod's power source, handed out through the controllers. There are no cables. Portables and heaters run on rechargeable batteries.
 - **Building:** station walls, reinforced station walls and reinforced glass.

Animated guides to every device are built in as Ponder scenes. They show when Create is installed. Without Create, the separate [ZAS³ Ponder](https://modrinth.com/mod/zas-ponders) addon brings Ponder.

## Mod Support
**Mekanism** (10.7.15+)
 - Oxygen, hydrogen, water vapour, steam, chlorine, hydrogen chloride, sulfur dioxide, ethene, deuterium, tritium and fusion fuel are ZAS³ gases, at 1 mB to 1 L. 
 - Nitrogen, CO₂, CO, N₂O and methane are added as Mekanism chemicals.
 - Pressurized tubes, chemical tanks and machines connect to ZAS pipes.
 - Chemicals Mekanism would delete, such as an electrolytic separator's dumped output, are released into the room instead.
 - Scuba gear, the MekaSuit and the Inhalation Purification Unit work as ZAS³ life support.
 - Heaters, thermodynamic conductors, boilers, reactor ports and other heat blocks warm and cool the rooms they stand in. A running fusion reactor makes a sealed room deadly in minutes unless it is cooled.

**Ad Astra** (1.16.26+)
 - ZAS³ decides who can breathe and what the air does to them. Ad Astra's own oxygen, freezing and burning damage is disabled, and its HUD reflects ZAS³'s atmospherics.
 - Each planet's open air comes from Ad Astra's planet data: vacuum at the planet's temperature, or standard air where the planet has oxygen. Planets from datapacks get theirs the same way.
 - Ad Astra's space suits protect like ZAS³'s, and their oxygen feeds your internals.
 - Torches and lanterns can be placed in a room whose air can keep a flame.

**Jade**: looking at a device shows its pressure, temperature and gas shares, port by port on pumps, filters and mixers.

**JEI** and **EMI**: gas properties and reactions are detailed.

**Fusion**: connected textures for the station walls and reinforced glass blocks.

## For mod developers

The public API (`zas-api`) is what space, tech and survival mods can build on: read and change a zone's gas and heat, give a dimension its open air, offer a block's gas store to pipes, and make items that protect or supply breath. A mod can bundle it and still run when ZAS³ isn't installed. The Mekanism and Ad Astra adapters use the public API alone, so they double as worked examples. The guide and Javadoc are on the [wiki](https://github.com/west3436/zas-cubed/wiki).

## Licensing

- **Code** is released under the [MIT License](LICENSE) and is
  **clean-room**. vgstation13's code (GPLv3/AGPLv3) is used strictly as a
  design reference: its documented behaviour, algorithms and physical
  constants inform this project, but no vgstation13 code is copied or
  translated into this repository. Contributions must follow the same rule;
  see [CONTRIBUTING.md](CONTRIBUTING.md).
- **Art assets** are licensed CC BY-SA 4.0 International under
  [LICENSE-ASSETS](LICENSE-ASSETS), whether drawn from scratch, derived
  from vgstation13's sprites (CC BY-SA 3.0 US) or adapted from other sources
  under compatible licenses. Each asset file's source, license and
  attribution are listed in [CREDITS](CREDITS). No NonCommercial-licensed
  assets are used.
- **Bundled libraries:** the Ponder addon (`zas_ponder`) nests
  [Ponder](https://github.com/Creators-of-Create/Ponder) and
  [Flywheel](https://github.com/Engine-Room/Flywheel), both under the MIT
  License, and holds nothing else. Their licences ship in its jar. The ZAS³
  jar, which holds `zas`, `zas_atmos` and the Ponder scenes, bundles neither.
