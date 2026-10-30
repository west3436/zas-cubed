# ZAS Cubed

Zone-based atmospherics for Minecraft (NeoForge 1.21.1), inspired by the ZAS
(Zone Atmospherics System) of the Space Station 13 codebase
[vgstation13](https://github.com/vgstation-coders/vgstation13).

> **Status: planning.** There is no playable build yet.

## Goals

- **Faithful ZAS-style simulation.** Sealed spaces become *zones* holding real
  gas mixtures (composition, pressure, temperature) that equalize across
  openings, vent to vacuum or the ambient atmosphere, burn, and conduct heat.
- **An atmosphere provider for other mods.** A stable public API that space,
  tech, and survival mods can build on, with first-class support for
  space-like environments: vacuum and exotic per-dimension atmospheres, and
  life support.
- **SS13-style atmospherics content.** Pipes, pumps, vents, scrubbers,
  canisters, air alarms, firelocks, and suits.
- **Optional Mekanism integration.** Chemical/gas bridging, venting into rooms,
  breathing gear, and heat exchange with room air.

## Licensing and clean-room policy

ZAS Cubed is released under the [MIT License](LICENSE).

vgstation13 is licensed under GPLv3/AGPLv3, so it is used strictly as a
**design reference**: its documented behavior, algorithms, and physical
constants inform this project, but no vgstation13 code or assets are copied or
translated into this repository. Contributions must follow the same rule.
