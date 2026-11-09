# Datapack formats

ZAS³ reads these files from datapacks. Mods ship them in their own
`data/` folder; servers can add them with an ordinary datapack.

R0 reads three formats: [gases](#gases), [ambients](#ambients) and
[geometry overrides](#geometry-overrides), and R1 adds [block tags](#block-tags),
[item tags](#item-tags), [hazard profiles](#hazard-profiles), the [breathers tag](#breathers), the
[airflow class tags](#airflow-classes), [reactions](#reactions) and
[ignition sources](#ignition-sources), [fuel profiles](#fuel-profiles) and
[block ignition](#block-ignition). Every format
reports invalid files the same way ([Invalid files](#invalid-files)).

## Gases

Gas types are data (CORE-010, D-08). Each file defines one gas:

```
data/<namespace>/zas/gas/<path>.json   →   gas id <namespace>:<path>
```

```json
{
  "name": "gas.example.argon",
  "formula": "Ar",
  "molar_mass": 0.039948,
  "molar_heat_capacity": 12.47,
  "visibility": {
    "color": "#B080FF",
    "density_threshold": 0.01
  },
  "phase": {
    "melting_point": 83.8,
    "boiling_point": 87.3,
    "latent_heat_fusion": 1180,
    "latent_heat_vaporization": 6430
  },
  "sources": {
    "molar_mass": "CIAAW standard atomic weight of Ar",
    "molar_heat_capacity": "C_v = 3/2 R for a monatomic ideal gas",
    "phase": "Where the phase values come from"
  }
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `name` | string | yes | Translation key of the display name |
| `formula` | string | yes | Formula label shown in the HUD and analyzer, such as `O₂` |
| `molar_mass` | number | yes | Molar mass in kg/mol; greater than 0 |
| `molar_heat_capacity` | number | yes | Molar heat capacity at constant volume, C_v, in J/(mol·K); greater than 0. The engine holds a gas's energy as C_v·T at fixed volume, so give C_v, not C_p |
| `visibility` | object | no | Leave out for an invisible gas |
| `visibility.color` | string | yes, in `visibility` | Colour as `"#RRGGBB"` |
| `visibility.density_threshold` | number | yes, in `visibility` | Molar density in mol/L above which the gas shows; greater than 0 |
| `phase` | object | no | Phase data (CORE-017): when the substance melts, boils or sublimes. Leave out if unknown; give at least one value if present |
| `phase.melting_point` | number | no | Melting point at 101.325 kPa, in K |
| `phase.boiling_point` | number | no | Boiling point at 101.325 kPa, in K; above `melting_point` |
| `phase.latent_heat_fusion` | number | no | Latent heat of fusion, in J/mol |
| `phase.latent_heat_vaporization` | number | no | Latent heat of vaporization, in J/mol |
| `phase.sublimation_point` | number | no | For a substance with no liquid at 101.325 kPa, such as CO₂: the temperature at which the solid sublimes there, in K. Excludes `melting_point` and `boiling_point` |
| `phase.latent_heat_sublimation` | number | no | Latent heat of sublimation, in J/mol |
| `sources` | object | no | Citations, by the name of the field they back, such as `"molar_mass"`. Not read by the game; they let a reader check the values. A key that names no field is warned about |

All phase values are finite and greater than 0. R1 stores phase data but
doesn't use it; it is there so that phase changes (HEAT-018) can come later
without a format change.

- **Invalid files** are skipped with an error naming the file and field
  ([Invalid files](#invalid-files)). The other gases still load.
- **When it applies:** gas definitions load when the server starts. A
  `/reload` that changes them is logged and takes effect at the next start,
  because zones store their gas by the loaded set.
- **Clients** receive the server's definitions when they join and after each
  `/reload` (CORE-014), so a server-only datapack is enough.
- **Built-in gases:** `zas` ships the eight gases of CORE-011:
  `zas:oxygen`, `zas:nitrogen`, `zas:carbon_dioxide`, `zas:nitrous_oxide`,
  `zas:water_vapor`, `zas:hydrogen`, `zas:carbon_monoxide` and
  `zas:methane`. Their constants and sources are in [Built-in gas
  constants](#built-in-gas-constants).
- **Removing a gas:** if a datapack or mod that defined a gas is removed,
  worlds that hold some of it still load (CORE-013). When a chunk loads, its
  moles of that gas are dropped, and a warning names the gas, the amount, the
  chunk and the zones that held it (CORE-016), for example:

  ```
  [minecraft:overworld] Dropped 40 mol of mypack:argon saved in chunk [3, -2], which the gas registry no longer defines: 40 mol in zone 17 at 52 64 -30
  ```

  The zone keeps its other gases, at the temperature it had. The chunk is
  saved without the gas, so the warning appears once per chunk.

### Gas tags

Gas tags group gases for hazard, fire and device logic (CORE-012), so that
logic handles gases that datapacks and addons add, as long as they are
tagged. They use Minecraft's tag format:

```
data/<namespace>/tags/zas/gas/<path>.json   →   gas tag #<namespace>:<path>
```

```json
{
  "values": [
    "mypack:chlorine",
    "#zas:flammable",
    { "id": "othermod:phosgene", "required": false }
  ]
}
```

Tags behave as Minecraft's do: files for the same tag in several packs add
up, `"replace": true` drops what lower packs added, `#tag` includes another
gas tag, an entry with `"required": false` may name a gas that doesn't exist,
and NeoForge's `"remove"` list takes gases out. A tag that names a required
gas or tag that doesn't exist fails as a whole and is reported as a skipped
`gas tag` ([Invalid files](#invalid-files)). Gas tags load with the gas
definitions and follow the same rules: fixed when the server starts, a
`/reload` that changes them applies at the next start, and clients receive
them with the definitions.

Addons read a tag through the API's `GasTags` (architecture §6.1).

| Tag | Gases | Read by |
|---|---|---|
| `#zas:oxidizer` | `zas:oxygen`, `zas:nitrous_oxide` | Fire logic (FIRE-003) and warnings about a fuel mixed with an oxidizer (CONT-103) |
| `#zas:flammable` | `zas:hydrogen`, `zas:methane`, `zas:carbon_monoxide` | Fire logic: the fuels (FIRE-003); the air alarm and devices that warn of flammable mixtures |
| `#zas:toxic` | `zas:carbon_monoxide`, `zas:nitrous_oxide` | Gas filtering, which keeps them out of the breath (API-022, CONT-088), and the scrubber, which removes CO₂ and these by default (CONT-015) |

CO₂ isn't in `#zas:toxic`: it harms only at high partial pressure, which its
hazard profile covers. There is no breathable tag, because breathing reads
O₂ by id (LIFE), no inert tag, because nothing would read it, and no visible
tag, because visibility is a field of the gas definition.

### Built-in gas constants

Each built-in definition cites its sources in its `sources` field
(CORE-015); this table collects them.

- **Molar mass** is the sum of the CIAAW abridged standard atomic weights
  (2024): H 1.0080, C 12.011, N 14.007, O 15.999 g/mol
  ([CIAAW](https://www.ciaaw.org/abridged-atomic-weights.htm)).
- **Molar heat capacity** is C_v at 298.15 K, C_p − R, with C_p of the
  ideal gas at 298.15 K from the NIST-JANAF Thermochemical Tables (Chase,
  1998) and R = 8.314462618 J/(mol·K) (CODATA 2018). The engine treats it as
  constant at every temperature.

| Gas | Molar mass (kg/mol) | C_p at 298.15 K (J/(mol·K)) | C_v (J/(mol·K)) | JANAF table |
|---|---|---|---|---|
| `zas:oxygen` (O₂) | 0.031998 | 29.376 | 21.06 | [O-029](https://janaf.nist.gov/tables/O-029.html) |
| `zas:nitrogen` (N₂) | 0.028014 | 29.124 | 20.81 | [N-023](https://janaf.nist.gov/tables/N-023.html) |
| `zas:carbon_dioxide` (CO₂) | 0.044009 | 37.129 | 28.81 | [C-095](https://janaf.nist.gov/tables/C-095.html) |
| `zas:nitrous_oxide` (N₂O) | 0.044013 | 38.617 | 30.30 | [N-026](https://janaf.nist.gov/tables/N-026.html) |
| `zas:water_vapor` (H₂O) | 0.018015 | 33.590 | 25.28 | [H-064](https://janaf.nist.gov/tables/H-064.html) |
| `zas:hydrogen` (H₂) | 0.002016 | 28.836 | 20.52 | [H-050](https://janaf.nist.gov/tables/H-050.html) |
| `zas:carbon_monoxide` (CO) | 0.028010 | 29.142 | 20.83 | [C-093](https://janaf.nist.gov/tables/C-093.html) |
| `zas:methane` (CH₄) | 0.016043 | 35.639 | 27.32 | [C-067](https://janaf.nist.gov/tables/C-067.html) |

**Phase data** (CORE-017), at 101.325 kPa:

| Gas | Melting point (K) | Boiling point (K) | Latent heat of fusion (J/mol) | Latent heat of vaporization (J/mol) |
|---|---|---|---|---|
| O₂ | 54.361 | 90.188 | — | 6818 |
| N₂ | 63.151 | 77.355 | — | 5580 |
| N₂O | 182.33 | 184.684 | 6500 | 16473 |
| H₂O | 273.16 | 373.124 | — | 40651 |
| H₂ | 13.957 | 20.369 | — | 904.5 |
| CO | 68.16 | 81.638 | — | 6013 |
| CH₄ | 90.694 | 111.667 | 939.2 | 8195 |

- Boiling points and latent heats of vaporization are from the NIST
  Chemistry WebBook's fluid properties (SRD 69): the saturation temperature
  at 101.325 kPa, and the vapour enthalpy less the liquid enthalpy there.
  H₂ is normal hydrogen.
- Melting points are the triple-point temperatures of the same data: the
  melting point at 101.325 kPa differs from the triple point by under 0.1 K
  for these substances (for water, ice melts at 273.15 K).
- Latent heats of fusion: N₂O 6.5 kJ/mol at 182.4 K (Atake and Chihara,
  1974) and CH₄ 0.9392 kJ/mol at 90.67 K (Vogt and Pitzer, 1976), both from
  the WebBook's phase change data. The others are left out until a checked
  reference value is found; the field is optional.
- **CO₂ has no liquid at 101.325 kPa**: its triple point is 216.58 K at
  518.5 kPa. It ships a sublimation point of 194.686 K (−78.464 °C, CRC
  Handbook, 95th ed., p. 4-56) and a latent heat of sublimation of
  25,200 J/mol at 195 K (Giauque and Egan, 1937, in the WebBook), and no
  melting or boiling point.

## Ambients

Each dimension's ambient atmosphere, the open air outside every zone, is
data (ENV-001). Each file defines one dimension's ambient, keyed by the
dimension id:

```
data/<namespace>/zas/ambient/<path>.json   →   dimension <namespace>:<path>
```

So `data/minecraft/zas/ambient/the_nether.json` defines the Nether. A
datapack overrides a shipped ambient by providing the same file.

```json
{
  "partial_pressures": {
    "zas:nitrogen": 80.04675,
    "zas:oxygen": 21.27825
  },
  "temperature": 293.15,
  "presets": {
    "hardcore": {
      "moles": { "zas:nitrogen": 13.0, "zas:oxygen": 3.5 },
      "temperature": 263.15
    }
  },
  "excluded": false
}
```

An **atmosphere** is an object with exactly one of `partial_pressures`,
`moles` or `vacuum`, and a `temperature`:

| Field | Type | Meaning |
|---|---|---|
| `partial_pressures` | object | Gas id → partial pressure in kPa, ≥ 0 |
| `moles` | object | Gas id → moles per 1,000 L (one full cell), ≥ 0; converted at the given temperature |
| `vacuum` | `true` | No gas at all (ENV-003) |
| `temperature` | number | Kelvin, ≥ 0 (above 0 when there is gas). Required, except for a vacuum, which defaults to the cosmic background, 2.7 K |

A **file** has these fields, all optional, but it must define an
atmosphere for at least one preset, exclude the dimension or seal its limits:

| Field | Type | Meaning |
|---|---|---|
| the atmosphere fields | | The ambient under every preset, unless `presets` overrides it |
| `presets` | object | `"normal"` and/or `"hardcore"` → an atmosphere for that preset only (ENV-010) |
| `excluded` | boolean | `true` excludes the dimension from simulation: no zones form there, but it keeps its ambient for queries and hazards (ENV-007, ENV-018). Default `false` |
| `sealed_world_limits` | boolean | `true` makes the dimension's build height and world border gas-tight, as for a station with a ceiling. Otherwise a face leading past them counts as open to the ambient (GEO-031). Default `false` |

- **Which ambient applies:** the variant for the active preset, or the one
  `ambient_variant` overrides it with (see [Server config](#server-config)),
  else the file's top-level atmosphere. A file beats anything a mod supplies
  through the API (ENV-014, D-45), so a pack can always have the final say. A
  dimension with no ambient for the active preset, or no file at all, takes
  what a [mod's provider](api-consumers.md#supplying-a-dimensions-ambient)
  gives, or else standard Overworld air: 101.325 kPa, 293.15 K, 79 % N₂, 21 %
  O₂ (ENV-011).
- **Gases** must exist in the running server's gas definitions; a file
  naming an unknown gas is skipped with an error.
- **Invalid files** are skipped with an error naming the file and field
  ([Invalid files](#invalid-files)); the dimension falls back as above.
  Unknown fields are ignored with a warning.
- **When it applies:** ambients load when the server starts and reload with
  `/reload`.

### Shipped ambients

`zas` ships these, in `data/minecraft/zas/ambient/` (ENV-002, ENV-012):

| Dimension | Normal | Hardcore |
|---|---|---|
| `minecraft:overworld` | Standard air, 293.15 K | Standard air, 293.15 K |
| `minecraft:the_nether` | Standard air, 313.15 K | CO₂ 12 kPa, O₂ 18 kPa, N₂ 71.325 kPa, 340 K |
| `minecraft:the_end` | Standard air, 293.15 K | O₂ 8.4 kPa, N₂ 31.6 kPa, 263.15 K |

Standard air is N₂ 80.04675 kPa and O₂ 21.27825 kPa.

### Server config

Three settings in the server config, `zas-server.toml` (OPS-001, see
[config.md](config.md)), go with ambients:

| Key | Default | Meaning |
|---|---|---|
| `preset` | `NORMAL` | The atmosphere difficulty preset, `NORMAL` or `HARDCORE` (D-14, OPS-007) |
| `[preset_options] ambient_variant` | `"preset"` | Which variant applies: the active preset's, or `NORMAL` or `HARDCORE` to override it under either preset ([config.md](config.md#the-preset-and-its-options)) |
| `excluded_dimensions` | `[]` | Dimension ids excluded from simulation, in addition to files with `"excluded": true` (ENV-007) |

A change applies from the next lookup, without a restart.

## Geometry overrides

A block's gas geometry, which faces seal and how much gas it holds, is derived
from its collision shape (GEO-001 to GEO-004). An override replaces it for
chosen blocks (GEO-005):

```
data/<namespace>/zas/geometry_overrides/<path>.json
```

```json
{
  "blocks": ["minecraft:sponge", "#mymod:gratings"],
  "sealed_faces": ["down", "up"],
  "volume": 600
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `blocks` | list of strings | yes | Block ids, or block tags prefixed with `#`. Unknown ids and tags are skipped, so a pack can name blocks of mods that aren't installed |
| `sealed_faces` | list of strings | no | The gas-tight faces: `down`, `up`, `north`, `south`, `west`, `east`. The others are fully open. Default none |
| `volume` | number | yes | Gas volume in L, 0 to 1,000 |
| `fluid_seals` | boolean | no | Whether a state of these blocks holding a fluid stays gas-tight (GEO-006), so that only its dry states take this override. Default `false`: the override has the last word over every state |

- An overridden block is a single cell.
- When several overrides match a block, one naming the block directly beats
  one matching it through a tag; between equals, the file whose id sorts last
  wins.
- **Shipped overrides** (GEO-009), in `data/zas/zas/geometry_overrides/`, are for
  blocks whose collision shape misleads. To change one, ship a file at the same
  path, which replaces it. A file naming a block directly also beats `leaves.json`
  and the tags in `fences.json`:

  | File | Blocks | Geometry |
  |---|---|---|
  | `leaves.json` | `#minecraft:leaves` | Permeable, 1,000 L, unless waterlogged |
  | `scaffolding.json` | scaffolding | Permeable, 1,000 L, unless waterlogged |
  | `fences.json` | `#minecraft:fences`, `#minecraft:fence_gates`, iron bars | Permeable, 1,000 L, unless waterlogged |
  | `honey_block.json` | honey block | Gas-tight, 0 L |

- **Fluids** (GEO-006): a position holding a fluid, whether a source, flowing or
  a waterlogged block, holds no gas and is gas-tight on every face. The shipped
  overrides say `fluid_seals`, so a waterlogged fence, leaf or iron bar seals. An
  override without it has the last word, for a fluid block or a waterlogged state:
  a pack's file naming `minecraft:oak_fence`, or replacing `fences.json`, makes a
  wet oak fence pass gas unless it says `fluid_seals` too.
- **Invalid files** are skipped with an error naming the file and field
  ([Invalid files](#invalid-files)).
- **When it applies:** overrides load when the server starts and reload with
  `/reload`, which rebuilds the geometry table; zones are rebuilt if the
  geometry changed.

## Heat data

How well a block conducts heat, and whether it is a heat source, are set per
block or tag (HEAT-002, HEAT-003):

```
data/<namespace>/zas/thermal/<path>.json
```

```json
{
  "blocks": ["#minecraft:wool", "#minecraft:wool_carpets"],
  "transmittance": 0.04
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `blocks` | list of strings | yes | Block ids, or block tags prefixed with `#`. Unknown ids and tags are skipped |
| `transmittance` | number | no | U in W/(m²·K) per block-thick layer, 0 to 1,000,000. Heat crossing a wall passes each block in turn: 1/U = Σ 1/Uᵢ. A block of 0 lets no heat through, by conduction or by radiating to a vacuum, so no path through it carries any (HEAT-019) |
| `temperature` | number | no | Makes the blocks heat sources at this temperature in K, 0 to 1,000,000. A source ends any heat path that reaches it and conducts with its own U |

- A file needs `transmittance`, `temperature` or both.
- **Each field is resolved on its own.** For each block, a file naming it
  directly beats one matching it through a tag; between equals, the file whose
  id sorts last wins. So a tag's file can set a U and another file can make
  one of its blocks a source.
- A block with no `transmittance` uses the server's fallback,
  `[heat] fallback_transmittance` (1.7, as stone; [config](config.md#server-zas-servertoml)).
- **Shipped data** (HEAT-014), in `data/zas/zas/thermal/`:

  | File | Blocks | U |
  |---|---|---|
  | `wool.json` | `#minecraft:wool`, `#minecraft:wool_carpets` | 0.04 |
  | `wood.json` | `#minecraft:planks`, `#minecraft:logs`, and the wooden slabs, stairs, doors, trapdoors, fences and fence gates | 0.15 |
  | `stone.json` | `#minecraft:base_stone_overworld`, `#minecraft:base_stone_nether`, `#minecraft:stone_bricks`, `#c:stones`, `#c:cobblestones`, smooth stone | 1.7 |
  | `ice.json` | `#minecraft:ice` | 2.2 |
  | `glass.json` | `#c:glass_blocks`, `#c:glass_panes` | 5.8 |
  | `metal.json` | iron, gold, copper and netherite storage blocks (`#c:storage_blocks/…`), iron bars, iron door, iron trapdoor | 50 |

  The content mod ships its own, in `data/zas_atmos/zas/thermal/`:

  | File | Blocks | U |
  |---|---|---|
  | `station_blocks.json` | `zas_atmos:station_wall`, `zas_atmos:reinforced_station_wall`, `zas_atmos:reinforced_glass_block` | 0 (CONT-129 to CONT-131) |

  And the heat sources (HEAT-016), one file per block in
  `data/zas/zas/thermal/sources/`:

  | File | Block | Temperature |
  |---|---|---|
  | `lava.json` | lava | 1,450 K |
  | `magma_block.json` | magma block | 1,000 K |
  | `ice.json` | ice | 273 K |
  | `packed_ice.json` | packed ice | 263 K |
  | `blue_ice.json` | blue ice | 253 K |
  | `snow_block.json` | snow block | 268 K |

  These set no U, so lava, magma and snow conduct at the fallback, and the ice
  at `ice.json`'s 2.2. Lava seals (GEO-006), so it warms a room it touches
  directly, through its own layer, as well as through any wall between.

- **Invalid files** are skipped with an error naming the file and field
  ([Invalid files](#invalid-files)).
- **When it applies:** with geometry overrides, at server start and on
  `/reload`. Heat data is part of a block's geometry, so a change rebuilds the
  zones as a geometry change does.

## Fuel profiles

How hard each fuel-burning block burns (FIRE-025). The blocks that burn are
those in `#zas:combustion` ([Block tags](#block-tags)), and a profile gives
their power:

```
data/<namespace>/zas/fuel_profile/<path>.json
```

```json
{
  "blocks": ["minecraft:furnace", "minecraft:smoker", "#minecraft:campfires"],
  "watts": 5000
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `blocks` | list of strings | yes | Block ids, or block tags prefixed with `#`. Unknown ids and tags are skipped |
| `watts` | number | yes | The power, in W, 0 to 10,000,000 |
| `burning_on` | list of strings | no | For fire: block ids or `#` tags of the block the fire burns on. The profile applies only to a fire burning on one of them (FIRE-022) |

- **Burned as carbon.** A block burns `watts` as carbon, C + O₂ → CO₂ at 394
  kJ/mol, so 5 kW uses about 0.0127 mol of O₂ a second and makes as much
  CO₂. It also adds its power to its zone as heat (HEAT-007). Below an O₂
  fraction of 0.18 part of the carbon forms CO instead, all of it at the fire
  threshold, with less heat (FIRE-005,
  [config](config.md#server-zas-servertoml)).
- **Where.** Only in a zone. A burning block outside zones changes nothing. A
  block below the fire oxygen threshold (FIRE-030) doesn't burn: a fire goes
  out, a furnace pauses and a campfire goes out.
- **Which profile.** A profile whose `burning_on` matches beats one without.
  Then a profile naming the block directly beats one matching it through a
  tag, and between equals the file whose id sorts last wins. A fire burns on
  the block below it, or, with air below, the first flammable block next to
  it.
- **Shipped profiles,** in `data/zas/zas/fuel_profile/`:

  | File | Blocks | Power |
  |---|---|---|
  | `furnace.json` | furnace, smoker, `#minecraft:campfires` | 5 kW |
  | `blast_furnace.json` | blast furnace | 10 kW |
  | `fire.json` | `#minecraft:fire` | 10 kW |

  `zas_atmos`'s fuel generator has none: it doesn't use the air (D-35,
  CONT-067).
- **A modded fuel block** needs both: a place in `#zas:combustion` with a
  boolean `lit` property, and a profile. A block in the tag without a profile
  forms zones under Hardcore but uses no O₂.
- **Invalid files** are skipped with an error naming the file and field
  ([Invalid files](#invalid-files)).
- **When it applies:** profiles load when the server starts and reload with
  `/reload`.

## Block ignition

The gas temperature at which a flammable block catches fire from a zone beside
it (FIRE-033), in place of `[fire] block_ignition_temperature`
([config](config.md#server-zas-servertoml)):

```
data/<namespace>/zas/block_ignition/<path>.json
```

```json
{
  "blocks": ["#minecraft:wool", "#minecraft:leaves"],
  "temperature": 500
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `blocks` | list of strings | yes | Block ids, or block tags prefixed with `#`. Unknown ids and tags are skipped |
| `temperature` | number | yes | The ignition temperature, in K, above 0, up to 100,000 |

- **Which blocks can burn** stays vanilla's: this file only says when. A block
  catches only if it is flammable (`isFlammable`), with vanilla ignite odds
  above 0, which is what `FireBlock.setFlammable` or NeoForge's flammability
  hooks give it. Naming stone does nothing.
- **Which file.** A file naming the block directly beats one matching it through
  a tag. Then the file whose id sorts last wins.
- **Shipped,** in `data/zas/zas/block_ignition/`: `wool_and_leaves.json`, 500 K
  for `#minecraft:wool`, `#minecraft:wool_carpets` and `#minecraft:leaves`.
- **Invalid files** are skipped with an error naming the file and field
  ([Invalid files](#invalid-files)).
- **When it applies:** files load when the server starts and reload with
  `/reload`. A change gives the blocks a new geometry, which replaces each
  dimension's simulation, as a change to a geometry override does.

## Reactions

Gas reactions are data (FIRE-001, D-05, D-08). Each file defines one reaction:

```
data/<namespace>/zas/reaction/<path>.json   →   reaction id <namespace>:<path>
```

```json
{
  "reactants": { "zas:methane": 1, "zas:oxygen": 2 },
  "products": { "zas:carbon_dioxide": 1, "zas:water_vapor": 2 },
  "enthalpy": -802000,
  "requires_ignition": true,
  "flammability": {
    "fuel": "zas:methane",
    "lower": 0.05,
    "upper": 0.15,
    "autoignition": 810
  },
  "sources": {
    "enthalpy": "where the value comes from"
  }
}
```

The reaction as written happens some number of times per step, its *extent*
in mol. Each mol of extent consumes the reactants' coefficients in mol,
produces the products' and releases −`enthalpy` J of heat into the gas.

| Field | Type | Required | Meaning |
|---|---|---|---|
| `type` | string | no | `zas:standard` (the default), or the id of a reaction type a mod implements in code (API-020) |
| `reactants` | object | yes, for `zas:standard` | Gas id → mol per mol of extent, each above 0. At least one |
| `products` | object | no | Gas id → mol per mol of extent, each above 0 |
| `enthalpy` | number | yes, for `zas:standard` | ΔH in J per mol of extent: negative releases heat, as in chemistry |
| `min_temperature` | number | no | The least temperature it happens at, K. Default 0 |
| `max_temperature` | number | no | The greatest, K, above `min_temperature`. Default none |
| `concentrations` | list | no | Objects `{"gas", "min", "max"}`: the gas's mole fraction of the mixture must be from `min` (default 0) to `max` (default 1) |
| `catalysts` | list | no | Objects `{"gas", "min"}`: the gas must be present, at a mole fraction of at least `min` (default 0), and isn't consumed |
| `requires_ignition` | boolean | no | Whether it needs an ignition source in or next to the cell (FIRE-006). Default false |
| `flammability` | object | no | The fuel's flammability limits: `fuel` (a gas id), `lower` and `upper` (its mole fraction of the mixture at 298 K, 0 to 1; they widen above it, FIRE-032), and `autoignition` (K, optional): at or above it no ignition source is needed. A reaction with flammability limits is a fire, so it also needs the fire oxygen threshold (FIRE-030), or only the threshold's 10 kPa floor at or above `autoignition` (FIRE-035) |
| `rate` | object | no | How fast it goes (CORE-046): `per_second` (k, above 0), `activation_energy` (Ea in J/mol, default 0) and `reference_temperature` (K, default 298.15). Without one it goes as far as its reactants allow in one step. Not allowed with `requires_ignition` |
| `incomplete_combustion` | object | no | Another form, with its own `reactants`, `products` and `enthalpy`, blended in by the share of carbon that forms CO when the O₂ fraction is low (FIRE-005) |
| `parameters` | any | no | Only for a code type: passed to it as it loads, and checked by it |
| `sources` | object | no | Citations by field name, as for gases; the game doesn't read them |

**Reaction types in code** (API-020). A mod registers a type with
`Reactions.registerType(id, type)` during setup; a definition names it in
`type` and gives it `parameters`:

```json
{
  "type": "examplemod:catalytic_converter",
  "parameters": { "efficiency": 0.8 },
  "min_temperature": 500,
  "catalysts": [{ "gas": "zas:oxygen" }]
}
```

- The definition's conditions (temperatures, concentrations, catalysts,
  ignition, flammability) still decide whether it runs; the type works out its
  own amounts, so `reactants`, `products`, `enthalpy`, `rate` and
  `incomplete_combustion` aren't allowed with it.
- Its change is scaled back like any other reaction's when it asks for more
  than there is.
- A `type` no mod registers, or `parameters` the type rejects, skips the file
  with an error naming the field.
- A type that throws, or that consumes or produces a gas the server doesn't
  define, changes nothing that step and is logged at most once a minute; the
  simulation carries on.

**How reactions run.**

- **Every condition is read at the start of a step,** from the whole mixture:
  temperatures, mole fractions, catalysts, flammability limits and, for a
  fire, the oxygen threshold.
- **Flammability limits widen with temperature** (FIRE-032). What you declare
  holds at 298 K and below. Above it, `lower` falls and `upper` rises by 0.75 ·
  (T − 298) / ΔH꜀ percentage points, the modified Burgess–Wheeler rule, where
  ΔH꜀ is the heat the reaction releases per mole of its `fuel`, in kcal/mol
  (`-enthalpy` / 4,184 / the fuel's coefficient). `lower` stops at 0.1 %, or at
  what you declared if that is lower, and `upper` at 100 %. A reaction that
  releases no heat, such as a code type, whose `enthalpy` isn't allowed, keeps
  its limits at any temperature.
- **A fire at or above its `autoignition` needs only the 10 kPa floor**
  (FIRE-035), not the 0.14 fraction. Each reaction goes by its own
  `autoignition`, so at 850 K below 14 % O₂ the shipped methane burns and
  hydrogen doesn't. A reaction with no `autoignition` always needs the whole
  threshold.
- **A reaction without `requires_ignition`** runs in every zone each atmos
  tick. With a `rate`, a step of dt seconds goes 1 − e^(−k·dt) of the way to
  using up its limiting reactant, with k = `per_second` ·
  e^(−Ea/R·(1/T − 1/Tref)) at the zone's temperature. Without one it uses up
  its limiting reactant at once.
- **A reaction with `requires_ignition`** never runs zone-wide. It burns in a
  hotspot's or a container's parcel, 375 L per second of the gas (FIRE-011,
  FIRE-031), and uses up its limiting reactant in that parcel. It burns where
  an ignition source is (a hotspot is one), or where the gas is at or above its
  `autoignition`.
- **Competing reactions share** (FIRE-002). When reactions together want more
  of a gas than there is, each one that wants it is scaled back by the worst
  ratio among its own reactants. A reaction competing with no other runs in
  full. Products are added after every reaction has consumed.
- **The heat goes into the gas.** The thermal energy, Σ cᵢnᵢ·T, plus the heat
  released gives the new temperature at the products' heat capacities (CORE-003).
  An endothermic reaction never cools the gas below the temperature floor.

**Shipped reactions**, in `data/zas/zas/reaction/`. The combustion
(FIRE-003) needs ignition and burns with O₂ to H₂O vapour and CO₂:

| File | Reaction | Enthalpy | Flammability limits | Autoignition |
|---|---|---|---|---|
| `hydrogen_combustion.json` | H₂ + ½ O₂ → H₂O | −242 kJ/mol | 4–75 % H₂ | 858 K |
| `methane_combustion.json` | CH₄ + 2 O₂ → CO₂ + 2 H₂O | −802 kJ/mol | 5–15 % CH₄ | 810 K |
| `carbon_monoxide_combustion.json` | CO + ½ O₂ → CO₂ | −283 kJ/mol | 12.5–74 % CO | 882 K |

- **The limits above are at 298 K** (FIRE-032). Hot gas burns leaner: H₂'s
  lower limit reaches the 0.1 % floor near 600 K, CH₄'s near 1,550 K and CO's
  near 1,420 K, so a hot room burns its fuel out unless it runs short of O₂
  first: at the fire oxygen threshold below the fuel's autoignition temperature,
  and at O₂'s 10 kPa floor at or above it (FIRE-035).

- **Methane's incomplete form** (FIRE-005) is CH₄ + 1.5 O₂ → CO + 2 H₂O,
  −519 kJ/mol. Below an O₂ fraction of 0.18 (`[fire]` in the server config) a
  share of the carbon burns this way, rising linearly to all of it at the fire
  threshold, 0.14: half at 0.16. Methane burning below the threshold, which it
  does only at or above its autoignition temperature (FIRE-035), forms only CO.
- **`nitrous_oxide_decomposition.json`** (FIRE-004) runs in zones without
  ignition: N₂O → N₂ + ½ O₂, −82.05 kJ/mol, above 850 K, at `per_second`
  0.012 at 1,000 K with an activation energy of 250 kJ/mol. That is about 1 %
  a second at 1,000 K and 1.8 per second at 1,200 K.

- A pack replaces a shipped reaction with a file of the same id, and can't
  remove one except by replacing it with one whose conditions never hold.
- A definition naming a gas the server doesn't define is skipped with an error
  when the server starts (or on `/reload`).
- **Invalid files** are skipped with an error naming the file and field
  ([Invalid files](#invalid-files)).
- **When it applies:** at server start and on `/reload`; the next atmos tick
  uses the new reactions.

## Zone boundaries

Blocks in the block tag `zas:zone_boundary` pass gas but keep the zones on
either side apart, like doors (GEO-007):

```
data/zas/tags/block/zone_boundary.json
```

```json
{
  "values": ["#minecraft:doors", "#minecraft:trapdoors"]
}
```

That is the mod's own tag. A pack adds blocks to it the usual way, with a file
at the same path listing them; to take blocks out, it replaces the tag with
`"replace": true` and lists what should stay.

- **Which faces.** A block that opens and closes keeps zones apart across the
  faces it seals in one state but not the other: a door across the face its
  panel closes against and the face it swings to, a trapdoor across its bottom
  or top face and the side it swings to. Its own cells join the space on its
  other sides, so a door belongs to the room it faces. Opening and closing it
  never splits or rebuilds a zone (GEO-044).
- **One volume.** A tagged block with an `open` property holds, in every state,
  the gas volume of the same state open (GEO-045). So a door whose closed panel
  takes more room than its open one, as the airlock's leaf does, never changes
  the volume of the zone its cells are in. A vanilla door's or trapdoor's
  panel is the same size either way, so its volume doesn't change. A tagged
  block with no `open` property keeps each state's own volume.
- **Other blocks.** A tagged block that doesn't open, or seals the same faces
  either way, keeps zones apart across every face: each of its cells is a zone
  of its own.
- **With overrides.** The faces come from the block's geometry after any
  override (above).
- **When it applies:** the tag is read when the server starts and on
  `/reload`, with the overrides.

## Block tags

ZAS³ reads these Minecraft block tags, at
`data/zas/tags/block/<name>.json`. Packs and mods add to them as to any tag:
a file at the same path adds its entries, unless it says `"replace": true`.

| Tag | Holds by default | Used for |
|---|---|---|
| `#zas:combustion` | `#minecraft:fire` (fire, soul fire; `zas:gas_fire` is in `#minecraft:fire` but never counts here, since it burns zone gas, not a fuel), furnace, blast furnace, smoker, `#minecraft:campfires` | Under Hardcore, a block here starting to burn in an enclosed, unzoned area forms a zone (CORE-056, FIRE-024). A block with a boolean `lit` property burns while it is `true`; one without, such as fire, always burns. In a zone, a burning block uses O₂ at its [fuel profile](#fuel-profiles)'s power (FIRE-021) |
| `#zas:plants` | `#zas:crop_plants`, `#zas:leaf_plants` | Under Hardcore, placing one in an enclosed, unzoned area forms a zone (CORE-056). In a zone, one in the light turns CO₂ into O₂ (CONT-053) |
| `#zas:crop_plants` | `#minecraft:crops`, `#minecraft:saplings` | Plants that make O₂ at `crop_photosynthesis_rate` (CONT-057), as does a block added straight to `#zas:plants` |
| `#zas:leaf_plants` | `#minecraft:leaves` | Plants that make O₂ at `leaf_photosynthesis_rate` (CONT-057) |
| `#zas:ignition_sources` | nothing | Blocks that ignite a flammable mixture while they are there (API-053, FIRE-014); one with a boolean `lit` property only while lit. `zas_atmos`'s igniter is in it. See [Ignition sources](#ignition-sources) |
| `#zas:zone_boundary` | `#minecraft:doors`, `#minecraft:trapdoors` | Open, they pass gas but keep the zones on either side apart (GEO-007); see [Zone boundaries](#zone-boundaries) |
| `#zas:pressure_fragile` | `#c:glass_blocks`, `#c:glass_panes` (glass and panes of every colour, tinted glass, and any mod's glass in those tags) | Under Hardcore, a pressure difference across one above the threshold breaks it (FLOW-007); see [Pressure-fragile blocks](#pressure-fragile-blocks) |

A trigger counts when it starts: a fire appears, a block is lit or a plant is
placed. A crop growing or a lit furnace turning doesn't count. The tags apply
from the next block change after `/reload`, and photosynthesis reads the plants
in its zones again at its next pass. Whether each trigger is on is the
preset's (`combustion_forms_zones`, `planting_forms_zones`,
[config.md](config.md#the-preset-and-its-options)). A mod's fuel block joins
`#zas:combustion` with a `lit` property, and `zas_atmos`'s firelock joins
`#zas:zone_boundary`. `zas_atmos`'s generator isn't in `#zas:combustion`: it
doesn't use the air (D-35, CONT-067).

### Pressure-fragile blocks

A block in `#zas:pressure_fragile` breaks when the pressure difference across it
exceeds the threshold (FLOW-007), so at the default of 150 kPa a window holds
1 atm against a vacuum and blows out on overpressure (FLOW-015). Whether blocks
break at all is the preset's: `block_pressure_damage` is on under Hardcore, off
under Normal, and either can override it (FLOW-016). The tag only chooses the
blocks. A tag can't hold a number, so the threshold is one value for the whole
tag, `[airflow] pressure_fragile_threshold_kpa`
([config.md](config.md#server-zas-servertoml)).

- **Across a block.** A block's sides are its own cells, as with a pane's two
  halves, and the open cells beside it. A cell in a zone reads the zone's
  pressure, and any other cell the dimension's ambient. The difference is between
  the highest and lowest of them. A solid block beside it isn't a side, so of two
  layers of glass neither sees a difference, and both hold.
- **What holds for now.** A block holds while one of its sides is a paused zone
  (in a paused dimension every zone is) or an unresolved region, or is in an
  unloaded chunk, or in a chunk whose saved zones aren't back yet, however large
  the difference looks. Those numbers are a last-known state or a guess. It is
  judged again each atmos tick, so it breaks when the far side comes back, if the
  difference is still there. Nothing is loaded to find out.
- **The break** is vanilla's, as if with no tool, with the block's own sound and
  particles. It drops what the block's loot table gives without a tool: nothing
  for glass and panes, which need silk touch, and itself for a plain block added
  to the tag. The `doTileDrops` game rule applies, and a waterlogged block leaves
  its water. To the atmosphere it is a block change like any other.
- **How many.** A dimension breaks at most `[airflow]
  pressure_fragile_breaks_per_tick` blocks per atmos tick. The rest break in later
  ticks, if the difference is still there, so a chain of windows goes over several
  ticks rather than in one.
- **Reinforced glass** (`zas_atmos:reinforced_glass_block`, CONT-131) is in neither
  `#c:glass_blocks` nor `#c:glass_panes`, so it isn't in the tag and no pressure
  difference breaks it. A pack that adds it to the tag makes it fragile.

A change to the tag applies after `/reload`, from the dimension's next atmos tick.

## Item tags

ZAS³ reads these Minecraft item tags, at `data/zas/tags/item/<name>.json`. Packs
and mods add to them as to any tag.

| Tag | Holds by default | Used for |
|---|---|---|
| `#zas:shows_hud` | nothing | An item here shows the atmosphere HUD while held in either hand, with or without an alert (CLIENT-001). `zas_atmos` adds its analyzer (CONT-034) |
| `#zas:shows_readings` | nothing | An item here, held in either hand, makes the atmosphere HUD show all four readings, safe ones too, not only those at warning or worse (CLIENT-008). `zas_atmos` adds its analyzer (CONT-034) |

The server checks the tags, and a change applies after `/reload`, from the
player's next HUD update, within a quarter of a second.

## Ignition sources

What ignites a flammable mixture (FIRE-014, FIRE-015), one source per file:

```
data/<namespace>/zas/ignition_source/<path>.json
```

```json
{
  "type": "block",
  "blocks": ["#minecraft:campfires"],
  "state": {"lit": "true"}
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `type` | string | yes, unless `enabled` is false | What ignites: `block`, `spark`, `item_use`, `entity` or `explosion` (below) |
| `enabled` | boolean | no | Whether the source is on; default true |
| `blocks` | list of strings | for `block` and `spark` | Block ids, or block tags prefixed with `#`. Unknown ids and tags are skipped |
| `state` | object | no | For `block` and `spark`: the block state properties that must match, as in vanilla's block state predicates, such as `{"lit": "true"}` or `{"power": {"min": "1"}}`. Without it, a `block` source's block with a boolean `lit` property counts only while lit |
| `items` | list of strings | for `item_use` | Item ids, or item tags prefixed with `#` |
| `entities` | list of strings | for `entity` | Entity type ids, or entity type tags prefixed with `#` |

The types:

- **`block`:** a block that ignites while it is there. The gas engine flags
  these states in its geometry, so it finds them in and next to zones without
  scanning the world.
- **`spark`:** a block that ignites once, when a block is changed to one of
  its states: a lever thrown, a redstone wire's power changing.
- **`item_use`:** an item used on a block, once the block hasn't taken the
  click itself, ignites the cell on the side used.
- **`entity`:** an entity of the type appearing in the world ignites its cell.
  An entity loaded with its chunk doesn't.
- **`explosion`:** an explosion ignites its centre. A burst that only
  triggers blocks, such as a wind charge's, doesn't.

A source only ever ignites a mixture the engine finds flammable, in the
source's cell or a neighbouring cell of its zone; outside zones it does
nothing.

- **Shipped sources**, in `data/zas/zas/ignition_source/`:

  | File | Type | What |
  |---|---|---|
  | `fire.json` | `block` | fire and soul fire. Not all of `#minecraft:fire`, which holds `zas:gas_fire`: a gas fire is a hotspot already, and spreads by FIRE-016's odds |
  | `lava.json` | `block` | lava and lava cauldrons |
  | `campfire.json` | `block` | `#minecraft:campfires` with `lit` true |
  | `torch.json` | `block` | torches and soul torches, standing or on a wall |
  | `flint_and_steel.json` | `item_use` | flint and steel |
  | `lightning.json` | `entity` | lightning bolts |
  | `explosion.json` | `explosion` | explosions |
  | `tagged.json` | `block` | `#zas:ignition_sources`, empty unless a mod adds to it ([Block tags](#block-tags)) |
  | `redstone_spark.json` | `spark` | **off**: redstone wire, redstone torches, repeaters, comparators, levers, `#minecraft:buttons` and `#minecraft:pressure_plates` |

- **Adding and removing.** A pack adds a source with a file of its own. It
  replaces a shipped one with a file at the same path: to turn torches off,
  `data/zas/zas/ignition_source/torch.json` holding `{"enabled": false}`; to
  turn the redstone spark on, a copy of `redstone_spark.json` with `"enabled":
  true`. Where two files claim the same block state, item or entity type, the
  file whose id sorts last names it.
- **Invalid files** are skipped with an error naming the file and field
  ([Invalid files](#invalid-files)).
- **When it applies:** at server start and on `/reload`, once tags are bound.

## Hazard profiles

A gas's hazard profile says how it harms a breathing entity that breathes it
(LIFE-004). Each file holds one gas's profile, keyed by the gas id:

```
data/<namespace>/zas/hazard_profile/<path>.json   →   gas <namespace>:<path>
```

So `data/zas/zas/hazard_profile/carbon_dioxide.json` is CO₂'s. A datapack
replaces a shipped profile by providing the same file; `"thresholds": []`
makes the gas harmless.

```json
{
  "thresholds": [
    { "partial_pressure": 5 },
    { "partial_pressure": 10, "above": true, "duration": 12, "effect_level": "incapacitated" },
    { "partial_pressure": 10, "above": true, "duration": 60, "effect_level": "incapacitated", "damage": 1 },
    { "partial_pressure": 20, "effects": [{ "id": "minecraft:poison", "amplifier": 1 }] }
  ]
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `thresholds` | list | yes | The thresholds, in any order |
| `thresholds[].partial_pressure` | number | yes | The gas's partial pressure in the breath at or above which the threshold applies, in kPa; greater than 0 |
| `thresholds[].above` | boolean | no | `true`: the threshold applies only strictly above `partial_pressure`, for a limit stated as "above"; default `false`, at or above |
| `thresholds[].duration` | number | no | Seconds the partial pressure must stay at the threshold before it applies; default 0, at once |
| `thresholds[].effect_level` | string | no | `impaired`, `incapacitated` or `unconscious` ([below](#effect-levels)) |
| `thresholds[].effects` | list | no | Other status effects: objects with an `id`, such as `"minecraft:poison"`, and an `amplifier`, 0 (level I, the default) to 255 |
| `thresholds[].damage` | number | no | Toxic gas damage while it applies, in HP per second; default 0 |

- **How it applies.** At each breath check (every 2 s by default,
  [config.md](config.md)), every threshold the breath's partial pressure
  reaches (at or above it, or strictly above with `above`), and has reached
  for its duration, applies. The duration counts
  from check to check and starts again once the partial pressure no longer reaches it.
  The effects of every threshold that applies add up; an effect named by
  several takes the highest amplifier. Effects last a second past the next
  check, so they hold while the exposure does and wear off soon after.
- **Damage.** A gas deals the highest `damage` among its thresholds that
  apply, and the damage of different gases adds up. It is the `zas:toxic_gas`
  damage type ([below](#damage-types)).
- **Warning levels.** A threshold with no effect level, effects or damage does
  nothing to the entity. The HUD warns at it (CLIENT-008).
- **Gases that aren't defined.** A profile for a gas the server doesn't have
  loads and never applies, so a pack can ship profiles for another mod's gases.
- **Invalid files** are skipped with an error naming the file and field
  ([Invalid files](#invalid-files)).
- **When it applies:** profiles load when the server starts and reload with
  `/reload`.

### Effect levels

Minecraft has no paralysis or sleep, so each level is a set of vanilla effects
(LIFE-040):

| Level | Effects |
|---|---|
| `impaired` | Nausea, Weakness |
| `incapacitated` | Slowness IV, Mining Fatigue III, Weakness II |
| `unconscious` | Incapacitated, plus Blindness |

### Shipped profiles

`zas` ships these, in `data/zas/zas/hazard_profile/` (LIFE-041). Partial
pressures are in the breath. A row that says "above" uses `"above": true`;
the others apply at or above their partial pressure.

| Gas | Threshold | Effect |
|---|---|---|
| CO₂ | 5 kPa | Warning |
| CO₂ | above 10 kPa for 12 s | Incapacitated |
| CO₂ | above 10 kPa for 60 s | Incapacitated, 1 HP/s |
| N₂O | 0.15 kPa | Nausea |
| N₂O | 1 kPa | Incapacitated |
| N₂O | 5 kPa | Unconscious, no damage |
| CO | 0.005 kPa (50 ppm) | Warning |
| CO | 0.12 kPa (1,200 ppm) | Impaired, 0.5 HP/s |
| CO | 1.28 kPa (12,800 ppm) | Unconscious, 2 HP/s |

The other built-in gases have no profile: they harm only by leaving too little
O₂ in the breath (LIFE-003).

## Breathers

Players breathe, and so do the entity types in the entity type tag
`zas:breathers` (LIFE-001):

```
data/zas/tags/entity_type/breathers.json
```

A breather's air meter drains while the O₂ partial pressure at its eyes is
below the minimum, and the gases' hazard profiles apply to it. Inside a zone it
also turns O₂ into CO₂ (LIFE-005). The shipped tag holds the air-breathing
vanilla mobs (LIFE-006):

- **In:** armadillo, bat, bee, camel, cat, cave spider, chicken, cow, creeper,
  donkey, evoker, fox, frog, goat, horse, illusioner, llama, mooshroom, mule,
  ocelot, panda, parrot, pig, pillager, polar bear, rabbit, ravager, sheep,
  silverfish, sniffer, spider, trader llama, villager, vindicator, wandering
  trader, warden, witch, wolf.
- **Out:** the undead (`#minecraft:undead`); golems; the aquatic mobs
  (`#minecraft:aquatic`, with the turtle and axolotl, and the tadpole); the
  Nether's and the End's natives (piglins, piglin brutes, hoglins, striders,
  ghasts, blazes, magma cubes, endermen, shulkers, endermites, the ender
  dragon), so that Hardcore's ambients don't kill them at home; the allay and
  the vex, which are spirits; the slime, which has no lungs, like the magma
  cube; the breeze, a creature of wind, like the blaze; and non-living
  entities such as armor stands.

Packs add mobs, their own or other mods', the usual way, with a file at the
same path; to take one out, they replace the tag with `"replace": true` and
list what should stay. The tag applies from the next breath check after
`/reload`.

A mob in the tag doesn't spawn naturally where the atmosphere would kill it, such
as a vacuum (LIFE-034). Spawners and spawn eggs still place it there.

## Atmosphere-immune entities

Pressure and temperature (LIFE-010, LIFE-011, LIFE-014) harm every living
entity, breathing or not, except the entity types in `zas:atmosphere_immune`
(LIFE-015):

```
data/zas/tags/entity_type/atmosphere_immune.json
```

The shipped tag holds the bosses (the wither and the ender dragon) and the
golems (the iron golem and the snow golem) (LIFE-016). An immune entity can
still breathe if it is in `zas:breathers`: the tag spares it pressure and
temperature only. Creative and spectator players are spared every hazard
without a tag (LIFE-037). Packs change the tag as they do `zas:breathers`.

## Protection

Worn items protect against pressure and temperature (LIFE-020, LIFE-025), and
can seal the breathing for internals or resist airflow. An item gets this from
its own life-support capability (`ILifeSupport`, API-022) or, if it has none,
from protection data (LIFE-026):

```
data/<namespace>/zas/protection/<name>.json
```

```json
{
  "items": ["mymod:pressure_helmet", "#mymod:pressure_suit"],
  "min_pressure_kpa": 0,
  "max_pressure_kpa": 101.325,
  "min_temperature_k": 0,
  "max_temperature_k": 500,
  "sealed_breathing": true,
  "gas_filtering": false,
  "airflow_resistance": 0
}
```

| Field | Default | Meaning |
|---|---|---|
| `items` | required | The items it applies to, by id, and item tags, with a leading `#` |
| `min_pressure_kpa` | 101.325 | The lowest pressure it protects against, 0 to 101.325 kPa; the default protects against no low pressure |
| `max_pressure_kpa` | 101.325 | The highest pressure it protects against, at least 101.325 kPa; the default protects against no high pressure |
| `min_temperature_k` | 293.15 | The lowest temperature it protects against, 0 to 293.15 K; the default protects against no cold |
| `max_temperature_k` | 293.15 | The highest temperature it protects against, at least 293.15 K; the default protects against no heat |
| `sealed_breathing` | `false` | Whether it seals the breathing, which internals need, in the head slot (LIFE-021). It also shows the atmosphere HUD there (CLIENT-001) |
| `gas_filtering` | `false` | Whether it filters the `#zas:toxic` gases out of the breath, in the head slot (API-022, CONT-088) |
| `airflow_resistance` | 0 | How much it resists being pushed by airflow, 0 to 1 (FLOW-012) |

An item counts in the head, chest, legs and feet slots, each covering its
share of the body (`[protection]` in the server config, LIFE-024: by default
0.3, 0.4, 0.2 and 0.1). The pieces rated for the pressure or temperature
around the wearer add up their shares to the coverage c. Then (1 − c⁵) of the
pressure difference from 1 atm still reaches the wearer, and (1 − c) of
temperature damage. Without a rated helmet, c is at most 0.7, so 83 % of the
difference still gets through.

**Which file wins:**
- An item's own capability wins over data.
- A file naming the item wins over one naming a tag it is in.
- Between files of the same kind, the first in file id order wins (by path,
  then namespace). The later file's claim on that item is logged as a warning.

A file may name items of a mod that isn't installed. A datapack replaces a
shipped file by providing the same path.

**Shipped:** `data/zas/zas/protection/vanilla_armor.json` gives every vanilla
armor piece, and the turtle shell, heat protection up to 400 K. They get no
cold or pressure protection and no seal, so they can't be used with internals
(LIFE-027).

## Airflow classes

Airflow moves an entity once the pressure difference at an opening near it
reaches its class's threshold (FLOW-003, FLOW-011), set in the server config's
`[airflow]` section. The class comes from four entity type tags, looked at in
this order:

```
data/zas/tags/entity_type/airflow/immune.json   never moved
data/zas/tags/entity_type/airflow/heavy.json    85 kPa
data/zas/tags/entity_type/airflow/light.json    20 kPa
data/zas/tags/entity_type/airflow/mob.json      65 kPa
```

A living entity in none of them is a mob; any other entity in none of them,
such as a painting or a display entity, is never moved. The shipped tags:

- **light:** items, XP orbs, `#minecraft:arrows`, `#minecraft:impact_projectiles`,
  thrown potions, bottles o' enchanting, ender pearls, eyes of ender, snowballs,
  eggs, tridents, fishing bobbers, llama spit and wind charges.
- **mob:** players.
- **heavy:** every minecart, boats, chest boats and armor stands.
- **immune:** the ender dragon.

Packs add to them the usual way; the tags apply from the next game tick after
`/reload`.

A worn item's `airflow_resistance` r ([Protection](#protection)) scales the
push on its wearer by (1 − r), whatever the class: at 1 the wearer isn't moved
(FLOW-012).

## Damage types

Each hazard deals its own damage type, data in `data/zas/damage_type/`
(LIFE-030):

| Damage type | When | Death message |
|---|---|---|
| `zas:hypoxia` | An empty air meter in a breath with too little O₂, 2 HP/s (LIFE-008) | *Steve ran out of oxygen* |
| `zas:toxic_gas` | A hazard profile's damage (LIFE-004) | *Steve breathed toxic gas* |
| `zas:decompression` | An effective pressure below 20 kPa, 1 HP/s (LIFE-010) | *Steve was exposed to vacuum* |
| `zas:barotrauma` | An effective pressure at or above 550 kPa, up to 2 HP/s (LIFE-014) | *Steve was crushed by the pressure* |
| `zas:frostbite` | Below the comfort band, 0.5 to 2 HP/s (LIFE-011) | *Steve froze in the cold air* |
| `zas:burns` | Above the comfort band, 1 to 4 HP/s (LIFE-011) | *Steve was burned by the hot air* |
| `zas:airflow_impact` | Thrown against a block by airflow (FLOW) | *Steve was thrown by rushing air* |

All but `zas:airflow_impact` are in `#minecraft:bypasses_armor` (LIFE-035),
since worn protection (LIFE-020, LIFE-025) stands in for armor;
`#minecraft:bypasses_cooldown`, so a hazard isn't lost to another's
invulnerability ticks; and `#minecraft:no_knockback`. Airflow impact is a blow,
so armor and the damage cooldown apply to it as to any other.

## Recipes

The content mod's recipes are ordinary vanilla crafting recipes in
`data/zas_atmos/recipe/` (CONT-059). A pack replaces one by shipping a file
with the same name, such as `data/zas_atmos/recipe/pipe_cap.json`, and removes
one with a file whose only entry is a failed condition,
`{"neoforge:conditions": [{"type": "neoforge:false"}]}`. Every recipe uses
vanilla materials and the mod's own crafting components (CONT-051); the
components, the recipes and their progression are in
[crafting.md](crafting.md).

The content mod's recipes, loot tables and tags ship in a built-in data pack,
"ZAS³ Atmospherics", not in the mod's own `data/` (CONT-155). It's always on
and starts at the bottom of the pack list, so any data pack overrides it. With
the content turned off in
[`zas_atmos-startup.toml`](config.md#content-mod-zas_atmos-startuptoml), the
pack isn't loaded.

## Invalid files

An invalid entry in any format is logged as an error naming the file and the
field at fault, and skipped; the rest of the pack loads, and the game never
crashes over it (OPS-008). For example:

```
Skipping gas definition data/mypack/zas/gas/argon.json: molar_mass: Not a number: "heavy"
Skipping gas tag data/mypack/tags/zas/gas/toxic.json: values: no such gas or gas tag: mypack:chlorine; an entry that may be missing needs "required": false
Skipping geometry override data/mypack/zas/geometry_overrides/sponge.json: sealed_faces: Unknown face 'sideways'; expected down, up, north, south, west or east
Skipping ambient definition data/mypack/zas/ambient/overworld.json: temperature is required
```

Nested fields are named by their path, such as `visibility: color: ...` or
`presets.hardcore.partial_pressures."zas:oxygen" ...`. Content that is ignored
while the rest of a file loads, such as an unknown field of an ambient, is
logged as a warning in the same form. Operators can list the last load's
problems in game with `/zas datapacks` ([commands.md](commands.md)).
