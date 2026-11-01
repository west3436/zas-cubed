# Datapack formats

ZAS³ reads these files from datapacks. Mods ship them in their own
`data/` folder; servers can add them with an ordinary datapack.

R0 reads three formats: [gases](#gases), [ambients](#ambients) and
[geometry overrides](#geometry-overrides). The rest of OPS-002's list
(reactions, thermal properties, fuel, protection and hazard profiles, entity
and block tags) arrives with its features in R1. Every format reports invalid
files the same way ([Invalid files](#invalid-files)).

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
| `#zas:toxic` | `zas:carbon_monoxide`, `zas:nitrous_oxide` | The gas mask, which filters them out of the breath (CONT-088), and the scrubber, which removes CO₂ and these by default (CONT-015) |

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
atmosphere for at least one preset or exclude the dimension:

| Field | Type | Meaning |
|---|---|---|
| the atmosphere fields | | The ambient under every preset, unless `presets` overrides it |
| `presets` | object | `"normal"` and/or `"hardcore"` → an atmosphere for that preset only (ENV-010) |
| `excluded` | boolean | `true` excludes the dimension from simulation: no zones form there, but it keeps its ambient for queries and hazards (ENV-007, ENV-018). Default `false` |

- **Which ambient applies:** the variant for the active preset, or the one
  `ambient_variant` overrides it with (see [Server config](#server-config)),
  else the file's top-level atmosphere. A
  dimension with no ambient for the active preset, or no file at all, gets
  standard Overworld air: 101.325 kPa, 293.15 K, 79 % N₂, 21 % O₂ (ENV-011).
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
  "blocks": ["minecraft:honey_block", "#minecraft:leaves"],
  "sealed_faces": ["down", "up", "north", "south", "west", "east"],
  "volume": 0
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `blocks` | list of strings | yes | Block ids, or block tags prefixed with `#`. Unknown ids and tags are skipped, so a pack can name blocks of mods that aren't installed |
| `sealed_faces` | list of strings | no | The gas-tight faces: `down`, `up`, `north`, `south`, `west`, `east`. The others are fully open. Default none |
| `volume` | number | yes | Gas volume in L, 0 to 1,000 |

- An overridden block is a single cell.
- When several overrides match a block, one naming the block directly beats
  one matching it through a tag; between equals, the file whose id sorts last
  wins.
- **Invalid files** are skipped with an error naming the file and field
  ([Invalid files](#invalid-files)).
- **When it applies:** overrides load when the server starts and reload with
  `/reload`, which rebuilds the geometry table; zones are rebuilt if the
  geometry changed.

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
