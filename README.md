# ZAS³

Zone-based atmospherics for Minecraft (NeoForge 1.21.1), inspired by the ZAS
(Zone Atmospherics System) of the Space Station 13 codebase
[vgstation13](https://github.com/vgstation-coders/vgstation13).

> **Status: planning.** There is no playable build yet. Research, the plan,
> and requirements live in [`docs/`](docs/).

## Goals

- **Faithful ZAS-style simulation.** Sealed spaces become *zones* holding real
  gas mixtures (composition, pressure, temperature) that equalize across
  openings, vent to vacuum or the ambient atmosphere, burn, and conduct heat.
  Ordinary open air and caves are never simulated. A zone only forms once an
  enclosed space's air is actually modified.
- **The shared atmosphere API for NeoForge.** A stable, documented public API
  (mod ID `zas`) that space, tech and survival mods can build on, with
  first-class support for space-like environments: vacuum and exotic
  per-dimension atmospheres, and life support. Mods can bundle the API and
  still run when ZAS³ isn't installed.
- **SS13-style atmospherics content,** shipped as a separate companion mod:
  pipes, pumps, vents, scrubbers, canisters, air alarms, firelocks, suits and
  power.
- **Integrations.** Ad Astra first. Mekanism will follow as a reference
  integration built only on the public API.

## Licensing

- **Code** is released under the [MIT License](LICENSE) and is
  **clean-room**. vgstation13's code (GPLv3/AGPLv3) is used strictly as a
  design reference: its documented behaviour, algorithms and physical
  constants inform this project, but no vgstation13 code is copied or
  translated into this repository. Contributions must follow the same rule.
- **Art assets** are planned to be derived from vgstation13's sprites, which
  are licensed CC BY-SA 3.0. Derived assets will be licensed CC BY-SA 4.0, with
  per-asset attribution in `CREDITS` and the asset license in
  `LICENSE-ASSETS`. No NonCommercial-licensed assets are used.
