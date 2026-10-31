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
- **Built-in gases** arrive in R1 (CORE-011). Until then `zas` ships none.
