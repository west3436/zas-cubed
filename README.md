# ZAS³

Zone-based atmospherics for Minecraft (NeoForge 1.21.1), inspired by the ZAS
(Zone Atmospherics System) of the Space Station 13 codebase
[vgstation13](https://github.com/vgstation-coders/vgstation13).

> **Status: beta.** 0.1.0 is on [GitHub Releases](https://github.com/west3436/zas-cubed/releases).
> Guides for players, server owners, pack makers and mod developers are on the
> [wiki](https://github.com/west3436/zas-cubed/wiki).

The requirements, plan, design notes and research were retired before the
first release. They're archived in
[`docs/`](https://github.com/west3436/zas-cubed/tree/f6931d5b91f839b3835fc6c81b1e69c9e09070e3/docs),
with the completed requirements in
[`docs/requirements`](https://github.com/west3436/zas-cubed/tree/f6931d5b91f839b3835fc6c81b1e69c9e09070e3/docs/requirements).

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
- **SS13-style atmospherics content,** a companion mod in the same jar:
  pipes, pumps, vents, scrubbers, canisters, area air controllers, firelocks,
  suits and power. A pack can turn it off and keep only the simulation.
- **Integrations, after R1.** Mekanism and Ad Astra, as reference
  integrations built only on the public API.

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
  License, for its scenes. Their licences ship in its jar. The ZAS³ jar,
  which holds `zas` and `zas_atmos`, bundles neither.
