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
  of standard air, which the API's `StandardAir` names. The rest of the
  built-in set (CORE-011) arrives in R1.

### Built-in gas constants

Molar heat capacity is at constant volume near 298 K: C_v = C_p − R, with
C_p from the NIST Chemistry WebBook and R = 8.314 J/(mol·K). Molar mass is
from the IUPAC standard atomic weights (N 14.007, O 15.999).

| Gas | Molar mass (kg/mol) | C_p (J/(mol·K)) | C_v (J/(mol·K)) | Source |
|---|---|---|---|---|
| `zas:nitrogen` (N₂) | 0.028014 | 29.12 | 20.81 | [NIST WebBook, N₂](https://webbook.nist.gov/cgi/cbook.cgi?ID=C7727379&Mask=1) |
| `zas:oxygen` (O₂) | 0.031998 | 29.38 | 21.06 | [NIST WebBook, O₂](https://webbook.nist.gov/cgi/cbook.cgi?ID=C7782447&Mask=1) |
