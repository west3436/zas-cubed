# Datapack formats

ZAS³ reads these files from datapacks. Mods ship them in their own
`data/` folder; servers can add them with an ordinary datapack.

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
  }
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `name` | string | yes | Translation key of the display name |
| `formula` | string | yes | Formula label shown in the HUD and analyzer, such as `O₂` |
| `molar_mass` | number | yes | Molar mass in kg/mol; greater than 0 |
| `molar_heat_capacity` | number | yes | Molar heat capacity in J/(mol·K); greater than 0 |
| `visibility` | object | no | Leave out for an invisible gas |
| `visibility.color` | string | yes, in `visibility` | Colour as `"#RRGGBB"` |
| `visibility.density_threshold` | number | yes, in `visibility` | Molar density in mol/L above which the gas shows; greater than 0 |

- **Invalid files** are skipped with an error in the server log. The other
  gases still load.
- **When it applies:** gas definitions load when the server starts. A
  `/reload` that changes them is logged and takes effect at the next start,
  because zones store their gas by the loaded set.
- **Clients** receive the server's definitions when they join and after each
  `/reload` (CORE-014), so a server-only datapack is enough.
- **Built-in gases:** `zas` ships `zas:nitrogen` and `zas:oxygen`, the gases
  of standard air, which the API's `StandardAir` names, and
  `zas:carbon_dioxide`, which the Hardcore Nether ambient needs (ENV-012).
  The rest of the built-in set (CORE-011) arrives in R1.

### Built-in gas constants

Molar heat capacity is at constant volume near 298 K: C_v = C_p − R, with
C_p from the NIST Chemistry WebBook and R = 8.314 J/(mol·K). Molar mass is
from the IUPAC standard atomic weights (N 14.007, O 15.999).

| Gas | Molar mass (kg/mol) | C_p (J/(mol·K)) | C_v (J/(mol·K)) | Source |
|---|---|---|---|---|
| `zas:nitrogen` (N₂) | 0.028014 | 29.12 | 20.81 | [NIST WebBook, N₂](https://webbook.nist.gov/cgi/cbook.cgi?ID=C7727379&Mask=1) |
| `zas:oxygen` (O₂) | 0.031998 | 29.38 | 21.06 | [NIST WebBook, O₂](https://webbook.nist.gov/cgi/cbook.cgi?ID=C7782447&Mask=1) |
| `zas:carbon_dioxide` (CO₂) | 0.044009 | 37.12 | 28.81 | [NIST WebBook, CO₂](https://webbook.nist.gov/cgi/cbook.cgi?ID=C124389&Mask=1) |

CO₂'s molar mass uses C 12.011.

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

- **Which ambient applies:** the variant for the active preset (see [Server
  config](#server-config)), else the file's top-level atmosphere. A
  dimension with no ambient for the active preset, or no file at all, gets
  standard Overworld air: 101.325 kPa, 293.15 K, 79 % N₂, 21 % O₂ (ENV-011).
- **Gases** must exist in the running server's gas definitions; a file
  naming an unknown gas is skipped with an error.
- **Invalid files** are skipped with an error in the server log naming the
  file and field (OPS-008); the dimension falls back as above. Unknown
  fields are ignored with a warning.
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

Two settings in the per-world server config,
`<world>/serverconfig/zas-server.toml` (OPS-001), go with ambients:

| Key | Default | Meaning |
|---|---|---|
| `preset` | `NORMAL` | The atmosphere difficulty preset, `NORMAL` or `HARDCORE` (D-14). For now it selects only the ambient variant; the full preset (OPS-007) arrives in R1 |
| `excluded_dimensions` | `[]` | Dimension ids excluded from simulation, in addition to files with `"excluded": true` (ENV-007) |

A change applies from the next lookup, without a restart.
